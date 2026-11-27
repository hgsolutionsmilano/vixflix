package com.example

import android.app.Application
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import com.example.data.local.CompletedMediaEntity
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.PlayerSegmentKeys
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.simkl.SimklPendingScrobbleEntity
import com.example.data.local.simkl.SimklProfileSyncMetaEntity
import com.example.data.local.simkl.SimklSecureTokenStore
import com.example.data.local.simkl.SimklTokenBundle
import com.example.data.local.simkl.SoftwareAesGcmSimklCipherEngine
import com.example.data.remote.MediaItem
import com.example.data.remote.simkl.SimklConfig
import com.example.data.remote.simkl.SimklConnectionState
import com.example.data.remote.simkl.SimklSyncManager
import com.example.data.remote.simkl.SimklSyncTrigger
import com.example.data.repository.VixFlixRepository
import com.example.ui.VixFlixViewModel
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Suite di verifica end-to-end per la correzione del bug:
 * "Continua a guardare bloccato su episodio precedente dopo avanzamento episodio su altro dispositivo / Simkl".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimklContinueWatchingSyncFixTest {

    private lateinit var app: Application
    private lateinit var server: ContinueWatchingSyncFakeServer
    private val clockMs = AtomicLong(1_765_000_000_000L)
    private val openedResources = mutableListOf<() -> Unit>()

    private data class DeviceHarness(
        val db: VixFlixDatabase,
        val vaultDir: File,
        val tokenStore: SimklSecureTokenStore,
        val repo: VixFlixRepository,
        val scope: CoroutineScope,
        val manager: SimklSyncManager
    ) {
        fun close() {
            runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
            db.close()
        }
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ContinueWatchingSyncFakeServer().apply { start() }
    }

    @After
    fun tearDown() {
        openedResources.forEach { runCatching { it() } }
        openedResources.clear()
        server.stop()
    }

    private fun createDevice(
        name: String,
        dbFile: File? = null,
        token: String = "tok_shared_account"
    ): DeviceHarness {
        val db = if (dbFile != null) {
            Room.databaseBuilder(app, VixFlixDatabase::class.java, dbFile.absolutePath)
                .allowMainThreadQueries()
                .build()
        } else {
            Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
        val vaultDir = File(app.cacheDir, "cw_sync_fix_${name}_${System.nanoTime()}").apply { mkdirs() }
        val tokenStore = SimklSecureTokenStore(vaultDir, SoftwareAesGcmSimklCipherEngine())
        val repo = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = server.baseUrl,
            vixBaseUrl = server.baseUrl,
            httpCacheDir = File(vaultDir, "http_cache").apply { mkdirs() }
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runBlocking {
            repo.ensureDefaultPrimaryProfile()
            tokenStore.saveTokens(
                DEFAULT_PRIMARY_PROFILE_ID,
                SimklTokenBundle(
                    accessToken = token,
                    refreshToken = "ref_$token",
                    expiresAtMs = clockMs.get() + 86_400_000L
                )
            )
            if (db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID) == null) {
                db.vixFlixDao().upsertSimklProfileMeta(
                    SimklProfileSyncMetaEntity(
                        profileId = DEFAULT_PRIMARY_PROFILE_ID,
                        connectionState = SimklConnectionState.CONNECTED_SYNCED.name,
                        accountDisplayName = "User"
                    )
                )
            }
        }
        val manager = SimklSyncManager(
            repository = repo,
            tokenStore = tokenStore,
            configProvider = {
                SimklConfig(
                    apiBaseUrl = server.baseUrl,
                    authBaseUrl = server.baseUrl.trimEnd('/'),
                    overrideClientId = "cw_fix_client_id"
                )
            },
            minPostIntervalMs = 5L,
            authPollMinIntervalMs = 20L,
            syncCooldownMs = 0L,
            clockMs = { clockMs.get() },
            scope = scope
        )
        val harness = DeviceHarness(db, vaultDir, tokenStore, repo, scope, manager)
        openedResources.add {
            harness.close()
            if (dbFile == null) vaultDir.deleteRecursively()
        }
        return harness
    }

    private fun seedStandardThreeSeasonSeries(tmdbId: Int = 55001, title: String = "Test Series"): MediaItem {
        server.tmdbTvJson[tmdbId] = """
            {
              "id":$tmdbId,
              "name":"$title",
              "overview":"Serie di test multi-dispositivo",
              "poster_path":"/series.jpg",
              "backdrop_path":"/series_bg.jpg",
              "vote_average":8.7,
              "first_air_date":"2022-03-10",
              "seasons":[
                {"season_number":1,"episode_count":10,"name":"Stagione 1"},
                {"season_number":2,"episode_count":10,"name":"Stagione 2"},
                {"season_number":3,"episode_count":5,"name":"Stagione 3"}
              ]
            }
        """.trimIndent()
        server.tmdbSeasonJson["${tmdbId}_1"] = """
            {"season_number":1,"episodes":[
              ${(1..10).joinToString(",") { """{"episode_number":$it,"season_number":1,"name":"S1 Ep $it","runtime":45,"air_date":"2022-03-10"}""" }}
            ]}
        """.trimIndent()
        server.tmdbSeasonJson["${tmdbId}_2"] = """
            {"season_number":2,"episodes":[
              ${(1..10).joinToString(",") { """{"episode_number":$it,"season_number":2,"name":"S2 Ep $it","runtime":48,"air_date":"2023-03-10"}""" }}
            ]}
        """.trimIndent()
        server.tmdbSeasonJson["${tmdbId}_3"] = """
            {"season_number":3,"episodes":[
              ${(1..5).joinToString(",") { """{"episode_number":$it,"season_number":3,"name":"S3 Ep $it","runtime":50,"air_date":"2024-03-10"}""" }}
            ]}
        """.trimIndent()
        return MediaItem(
            id = tmdbId,
            mediaType = "tv",
            title = title,
            overview = "Serie di test multi-dispositivo",
            posterPath = "/series.jpg",
            backdropPath = "/series_bg.jpg",
            voteAverage = 8.7,
            releaseYear = "2022"
        )
    }

    private suspend fun waitUntil(timeoutMs: Long = 4_000L, predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            delay(20L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timeout nell'attesa della condizione prevista", predicate())
    }

    // -------------------------------------------------------------------------
    // SCENARIO 1A: Caso esatto utente (A fermo su S2E8 min 8 -> B guarda fino a S2E9 -> A sincronizza -> S2E10 0m)
    // -------------------------------------------------------------------------
    @Test
    fun `01A device A paused S2E8 at 8m advances to S2E10 at 0m after device B finishes S2E9 on Simkl`() = runBlocking {
        val series = seedStandardThreeSeasonSeries(tmdbId = 55001)
        val tsInitial = "2026-11-20T10:00:00Z"
        val tsPauseA = "2026-11-20T10:08:00Z"

        server.activitiesJson = """
            {"all":"$tsPauseA","shows":{"all":"$tsInitial","watching":"$tsInitial","watched_at":"$tsInitial","playback":"$tsPauseA"},"movies":{},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {
              "shows":[{
                "status":"watching",
                "last_watched_at":"$tsInitial",
                "watched_episodes_count":17,
                "total_episodes_count":25,
                "next_to_watch":"S02E08",
                "show":{"title":"${series.title}","year":2022,"ids":{"simkl":88001,"tmdb":${series.id}}},
                "seasons":[
                  {"number":1,"episodes":[${(1..10).joinToString(",") { """{"number":$it,"watched_at":"$tsInitial"}""" }}]},
                  {"number":2,"episodes":[${(1..7).joinToString(",") { """{"number":$it,"watched_at":"$tsInitial"}""" }}]}
                ]
              }]
            }
        """.trimIndent()
        server.playbackEpisodesJson = """
            [{"id":9001,"progress":16.7,"paused_at":"$tsPauseA","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88001,"tmdb":${series.id}}},"episode":{"season":2,"number":8,"title":"S2 Ep 8"}}]
        """.trimIndent()

        val deviceA = createDevice("DeviceA_1A")
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        val initialCw = deviceA.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(initialCw)
        assertEquals(2, initialCw!!.seasonNumber)
        assertEquals(8, initialCw.episodeNumber)
        assertTrue("Device A deve avere il punto di ripresa circa al minuto 8", initialCw.positionMs in 450_000L..510_000L)
        assertNotNull(deviceA.db.vixFlixDao().getEpisodeProgress("tv_${series.id}_s2_e8", DEFAULT_PRIMARY_PROFILE_ID))

        // Device B guarda fino alla fine di S2E9 e invia aggiornamenti a Simkl
        // Nota: lasciamo anche un residuo S2E8 in /sync/playback/episodes con clock leggermente sporco per simulare il caso peggiore
        val tsWatchedB = "2026-11-20T11:30:00Z"
        server.activitiesJson = """
            {"all":"$tsWatchedB","shows":{"all":"$tsWatchedB","watching":"$tsWatchedB","watched_at":"$tsWatchedB","playback":"$tsWatchedB"},"movies":{},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = """
            {
              "shows":[{
                "status":"watching",
                "last_watched_at":"$tsWatchedB",
                "watched_episodes_count":19,
                "total_episodes_count":25,
                "next_to_watch":"S02E10",
                "show":{"title":"${series.title}","year":2022,"ids":{"simkl":88001,"tmdb":${series.id}}},
                "seasons":[
                  {"number":1,"episodes":[${(1..10).joinToString(",") { """{"number":$it,"watched_at":"$tsInitial"}""" }}]},
                  {"number":2,"episodes":[${(1..9).joinToString(",") { """{"number":$it,"watched_at":"$tsWatchedB"}""" }}]}
                ]
              }]
            }
        """.trimIndent()
        server.playbackEpisodesJson = "[]"

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        // S2E8 e S2E9 risultano VISTI
        val watchedS2 = deviceA.db.vixFlixDao().getWatchedEpisodeNumbers(series.id, 2, DEFAULT_PRIMARY_PROFILE_ID).toSet()
        assertEquals((1..9).toSet(), watchedS2)

        // Progresso obsoleto di S2E8 rimosso
        assertNull(
            "Il progresso locale obsoleto di S2E8 (~8m) deve essere invalidato",
            deviceA.db.vixFlixDao().getEpisodeProgress("tv_${series.id}_s2_e8", DEFAULT_PRIMARY_PROFILE_ID)
        )

        // Continua a guardare deve puntare a S2E10 con posizione 0ms!
        val updatedCw = deviceA.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull("La serie deve restare in Continua a guardare con il prossimo episodio", updatedCw)
        assertEquals(2, updatedCw!!.seasonNumber)
        assertEquals(10, updatedCw.episodeNumber)
        assertEquals("S2 Ep 10", updatedCw.episodeTitle)
        assertEquals("S2E10 non deve ereditare gli 8 minuti di S2E8", 0L, updatedCw.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 1B: Simkl restituisce ancora S2E8 in /sync/playback/episodes + next_to_watch omesso o obsoleto
    //              + coda offline Device A con partial stop su S2E8
    // -------------------------------------------------------------------------
    @Test
    fun `01B stale remote playback S2E8 stale next_to_watch and local partial stop S2E8 never block advancement to S2E10`() = runBlocking {
        val series = seedStandardThreeSeasonSeries(tmdbId = 55002, title = "Resilient Series")
        val tsInitial = "2026-11-20T10:00:00Z"
        server.activitiesJson = """
            {"all":"$tsInitial","shows":{"all":"$tsInitial","watching":"$tsInitial","watched_at":"$tsInitial","playback":"$tsInitial"},"movies":{},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {
              "shows":[{
                "status":"watching",
                "last_watched_at":"$tsInitial",
                "watched_episodes_count":7,
                "total_episodes_count":10,
                "next_to_watch":"S02E08",
                "show":{"title":"${series.title}","year":2022,"ids":{"simkl":88002,"tmdb":${series.id}}},
                "seasons":[{"number":2,"episodes":[${(1..7).joinToString(",") { """{"number":$it,"watched_at":"$tsInitial"}""" }}]}]
              }]
            }
        """.trimIndent()

        val deviceA = createDevice("DeviceA_1B")
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        // Device A salva localmente S2E8 a 8 minuti + accoda uno stop parziale (17% < 96%) in coda offline
        deviceA.repo.saveWatchProgress(series, 2, 8, "S2 Ep 8", 480_000L, 2_880_000L, DEFAULT_PRIMARY_PROFILE_ID)
        deviceA.db.vixFlixDao().upsertSimklPendingScrobble(
            SimklPendingScrobbleEntity(
                profileId = DEFAULT_PRIMARY_PROFILE_ID,
                contentKey = "tv_${series.id}_s2_e8",
                tmdbId = series.id,
                mediaType = "tv",
                title = series.title,
                seasonNumber = 2,
                episodeNumber = 8,
                action = "stop",
                progressPercent = 16.7,
                minProgress = 96,
                createdAtMs = clockMs.get() - 60_000L,
                updatedAtMs = clockMs.get() - 60_000L,
                triggerEvent = "player_exit_partial"
            )
        )

        // Simkl viene aggiornato da Device B (S2E8 e S2E9 visti), MA:
        // - `next_to_watch` è rimasto sporco/obsoleto su "S02E09" (già completato!)
        // - `/sync/playback/episodes` contiene ancora un vecchio S2E8 in pausa
        val tsUpdateB = "2026-11-20T12:00:00Z"
        server.activitiesJson = """
            {"all":"$tsUpdateB","shows":{"all":"$tsUpdateB","watching":"$tsUpdateB","watched_at":"$tsUpdateB","playback":"$tsUpdateB"},"movies":{},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = """
            {
              "shows":[{
                "status":"watching",
                "last_watched_at":"$tsUpdateB",
                "watched_episodes_count":9,
                "total_episodes_count":10,
                "next_to_watch":"S02E09",
                "show":{"title":"${series.title}","year":2022,"ids":{"simkl":88002,"tmdb":${series.id}}},
                "seasons":[{"number":2,"episodes":[${(1..9).joinToString(",") { """{"number":$it,"watched_at":"2026-11-20T11:45:00Z"}""" }}]}]
              }]
            }
        """.trimIndent()
        // Timestamp di playback S2E8 leggermente successivo (skew 3s) rispetto a watched_at di ep 8, ma inferiore/entro finestra rispetto alla serie!
        server.playbackEpisodesJson = """
            [{"id":9002,"progress":16.7,"paused_at":"2026-11-20T11:45:03Z","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88002,"tmdb":${series.id}}},"episode":{"season":2,"number":8,"title":"S2 Ep 8"}}]
        """.trimIndent()

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL))

        // La scrittura parziale offline di S2E8 deve essere stata scartata e S2E8/S2E9 visti
        assertTrue(deviceA.db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).isEmpty())
        assertEquals((1..9).toSet(), deviceA.db.vixFlixDao().getWatchedEpisodeNumbers(series.id, 2, DEFAULT_PRIMARY_PROFILE_ID).toSet())
        assertNull(deviceA.db.vixFlixDao().getEpisodeProgress("tv_${series.id}_s2_e8", DEFAULT_PRIMARY_PROFILE_ID))

        val cw = deviceA.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(cw)
        assertEquals(2, cw!!.seasonNumber)
        assertEquals(10, cw.episodeNumber)
        assertEquals(0L, cw.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 1C: Fast-path playback (>= 96% su S2E9) avanza subito Continua a guardare da S2E8 a S2E10
    // -------------------------------------------------------------------------
    @Test
    fun `01C pre-playback fast path completing S2E9 advances stuck S2E8 Continue Watching directly to S2E10`() = runBlocking {
        val series = seedStandardThreeSeasonSeries(tmdbId = 55003, title = "FastPath Series")
        val ts1 = "2026-11-20T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","shows":{"all":"$ts1","watching":"$ts1","playback":"$ts1"},"movies":{},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","watched_episodes_count":7,"next_to_watch":"S02E08","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88003,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..7).joinToString(",") { """{"number":$it}""" }}]}]}]}
        """.trimIndent()

        val deviceA = createDevice("DeviceA_1C")
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        deviceA.repo.saveWatchProgress(series, 2, 8, "S2 Ep 8", 480_000L, 2_880_000L, DEFAULT_PRIMARY_PROFILE_ID)

        // Playback fast-path vede S2E9 completato al 98%
        val ts2 = "2026-11-20T11:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","shows":{"all":"$ts1","watching":"$ts1","playback":"$ts2"},"movies":{},"anime":{}}
        """.trimIndent()
        server.playbackEpisodesJson = """
            [{"id":9003,"progress":98.0,"paused_at":"$ts2","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88003,"tmdb":${series.id}}},"episode":{"season":2,"number":9,"title":"S2 Ep 9"}}]
        """.trimIndent()

        assertTrue(deviceA.manager.syncPlaybackFastPathNow(DEFAULT_PRIMARY_PROFILE_ID))

        assertNotNull(
            deviceA.db.vixFlixDao().getCompletedMedia(
                PlayerSegmentKeys.completedMediaKey(series.id, "tv", 2, 9),
                DEFAULT_PRIMARY_PROFILE_ID
            )
        )
        val cw = deviceA.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(cw)
        assertEquals(2, cw!!.seasonNumber)
        assertEquals(10, cw.episodeNumber)
        assertEquals(0L, cw.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 2: Prossimo episodio (S2E10) possiede già un proprio progresso incompleto (5m)
    // -------------------------------------------------------------------------
    @Test
    fun `02 preserves legitimate incomplete progress on next episode S2E10 without inheriting S2E8 minutes`() = runBlocking {
        val series = seedStandardThreeSeasonSeries(tmdbId = 55004, title = "OwnProgress Series")
        val ts1 = "2026-11-20T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","shows":{"all":"$ts1","watching":"$ts1"},"movies":{},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","watched_episodes_count":7,"next_to_watch":"S02E08","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88004,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..7).joinToString(",") { """{"number":$it}""" }}]}]}]}
        """.trimIndent()

        val deviceA = createDevice("DeviceA_2")
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        // S2E8 salvato a 8 minuti; nel frattempo esiste un progresso parziale legittimo di 5 minuti proprio su S2E10
        deviceA.repo.saveWatchProgress(series, 2, 8, "S2 Ep 8", 480_000L, 2_880_000L, DEFAULT_PRIMARY_PROFILE_ID)
        deviceA.db.vixFlixDao().upsertEpisodeProgress(
            EpisodeProgressEntity(
                episodeKey = "tv_${series.id}_s2_e10",
                tmdbId = series.id,
                seasonNumber = 2,
                episodeNumber = 10,
                positionMs = 300_000L,
                durationMs = 2_880_000L,
                updatedAt = clockMs.get(),
                profileId = DEFAULT_PRIMARY_PROFILE_ID
            )
        )

        val ts2 = "2026-11-20T11:30:00Z"
        server.activitiesJson = """
            {"all":"$ts2","shows":{"all":"$ts2","watching":"$ts2","watched_at":"$ts2"},"movies":{},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = """
            {"shows":[{"status":"watching","last_watched_at":"$ts2","watched_episodes_count":9,"next_to_watch":"S02E10","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88004,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..9).joinToString(",") { """{"number":$it,"watched_at":"$ts2"}""" }}]}]}]}
        """.trimIndent()

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val cw = deviceA.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(cw)
        assertEquals(2, cw!!.seasonNumber)
        assertEquals(10, cw.episodeNumber)
        assertEquals("Deve usare i 5 minuti propri di S2E10, mai gli 8 minuti di S2E8", 300_000L, cw.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 3: Visione non lineare (E8 in pausa, E9 marcato visto, E10 non visto) -> avanza a E10 a 0m
    // -------------------------------------------------------------------------
    @Test
    fun `03 non-linear watching with E8 paused and E9 watched on Simkl advances Continue Watching to E10 at 0m`() = runBlocking {
        val series = seedStandardThreeSeasonSeries(tmdbId = 55005, title = "NonLinear Series")
        val ts1 = "2026-11-20T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","shows":{"all":"$ts1","watching":"$ts1"},"movies":{},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","watched_episodes_count":7,"next_to_watch":"S02E08","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88005,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..7).joinToString(",") { """{"number":$it}""" }}]}]}]}
        """.trimIndent()

        val deviceA = createDevice("DeviceA_3")
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        deviceA.repo.saveWatchProgress(series, 2, 8, "S2 Ep 8", 480_000L, 2_880_000L, DEFAULT_PRIMARY_PROFILE_ID)

        // Device B salta E8 e guarda E9 (S2E9 visto, E10 non visto)
        val ts2 = "2026-11-20T12:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","shows":{"all":"$ts2","watching":"$ts2","watched_at":"$ts2"},"movies":{},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = """
            {"shows":[{"status":"watching","last_watched_at":"$ts2","watched_episodes_count":8,"show":{"title":"${series.title}","year":2022,"ids":{"simkl":88005,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..7).joinToString(",") { """{"number":$it}""" }},{"number":9,"watched_at":"$ts2"}]}]}]}
        """.trimIndent()

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val cw = deviceA.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(cw)
        assertEquals(2, cw!!.seasonNumber)
        assertEquals(10, cw.episodeNumber)
        assertEquals(0L, cw.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 4: Cambio stagione (ultimo episodio Stagione 2 -> S3E1 a 0m)
    // -------------------------------------------------------------------------
    @Test
    fun `04 finishing final episode S2E10 advances Continue Watching across season boundary to S3E1 at 0m`() = runBlocking {
        val series = seedStandardThreeSeasonSeries(tmdbId = 55006, title = "SeasonBoundary Series")
        val ts1 = "2026-11-20T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","shows":{"all":"$ts1","watching":"$ts1"},"movies":{},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","watched_episodes_count":8,"next_to_watch":"S02E09","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88006,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..8).joinToString(",") { """{"number":$it}""" }}]}]}]}
        """.trimIndent()

        val deviceA = createDevice("DeviceA_4")
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        deviceA.repo.saveWatchProgress(series, 2, 9, "S2 Ep 9", 540_000L, 2_880_000L, DEFAULT_PRIMARY_PROFILE_ID)

        // Device B completa S2E9 e S2E10 (fine Stagione 2) -> prossimo deve essere S3E1 (anche senza next_to_watch)
        val ts2 = "2026-11-20T13:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","shows":{"all":"$ts2","watching":"$ts2","watched_at":"$ts2"},"movies":{},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = """
            {"shows":[{"status":"watching","last_watched_at":"$ts2","watched_episodes_count":10,"show":{"title":"${series.title}","year":2022,"ids":{"simkl":88006,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..10).joinToString(",") { """{"number":$it,"watched_at":"$ts2"}""" }}]}]}]}
        """.trimIndent()

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val cw = deviceA.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(cw)
        assertEquals(3, cw!!.seasonNumber)
        assertEquals(1, cw.episodeNumber)
        assertEquals("S3 Ep 1", cw.episodeTitle)
        assertEquals(0L, cw.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 5: Tutti gli episodi visti -> serie rimossa da Continua a guardare senza proporre episodi visti
    // -------------------------------------------------------------------------
    @Test
    fun `05 when all episodes of series become watched Continue Watching removes the card cleanly`() = runBlocking {
        val tmdbId = 55007
        server.tmdbTvJson[tmdbId] = """
            {"id":$tmdbId,"name":"Mini Series End","poster_path":"/m.jpg","seasons":[{"season_number":1,"episode_count":2}]}
        """.trimIndent()
        server.tmdbSeasonJson["${tmdbId}_1"] = """
            {"season_number":1,"episodes":[
              {"episode_number":1,"season_number":1,"name":"Ep 1","runtime":45},
              {"episode_number":2,"season_number":1,"name":"Ep 2","runtime":45}
            ]}
        """.trimIndent()
        val series = MediaItem(tmdbId, "tv", "Mini Series End", "", "/m.jpg", null, 8.0, "2024")

        val ts1 = "2026-11-20T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","shows":{"all":"$ts1","watching":"$ts1"},"movies":{},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","watched_episodes_count":0,"show":{"title":"Mini Series End","year":2024,"ids":{"simkl":88007,"tmdb":$tmdbId}}}]}
        """.trimIndent()

        val deviceA = createDevice("DeviceA_5")
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        deviceA.repo.saveWatchProgress(series, 1, 1, "Ep 1", 480_000L, 2_700_000L, DEFAULT_PRIMARY_PROFILE_ID)

        val ts2 = "2026-11-20T12:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","shows":{"all":"$ts2","watching":"$ts2","completed":"$ts2","watched_at":"$ts2"},"movies":{},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = """
            {"shows":[{"status":"watching","last_watched_at":"$ts2","watched_episodes_count":2,"total_episodes_count":2,"show":{"title":"Mini Series End","year":2024,"ids":{"simkl":88007,"tmdb":$tmdbId}},"seasons":[{"number":1,"episodes":[{"number":1,"watched_at":"$ts2"},{"number":2,"watched_at":"$ts2"}]}]}]}
        """.trimIndent()

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        assertEquals(setOf(1, 2), deviceA.db.vixFlixDao().getWatchedEpisodeNumbers(tmdbId, 1, DEFAULT_PRIMARY_PROFILE_ID).toSet())
        assertNull(
            "Quando tutti gli episodi risultano visti, la scheda non deve restare su un episodio già visto",
            deviceA.db.vixFlixDao().getWatchProgress("tv_$tmdbId", DEFAULT_PRIMARY_PROFILE_ID)
        )
        assertNull(deviceA.db.vixFlixDao().getEpisodeProgress("tv_${tmdbId}_s1_e1", DEFAULT_PRIMARY_PROFILE_ID))
    }

    // -------------------------------------------------------------------------
    // SCENARIO 6: Episodio da VISTO -> NON VISTO su Simkl aggiorna completed_media e Continua a guardare
    // -------------------------------------------------------------------------
    @Test
    fun `06 episode marked unwatched on Simkl clears completed_media and updates Continue Watching cleanly`() = runBlocking {
        val series = seedStandardThreeSeasonSeries(tmdbId = 55008, title = "Unwatch Sync Series")
        val ts1 = "2026-11-20T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","shows":{"all":"$ts1","watching":"$ts1","watched_at":"$ts1"},"movies":{},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","last_watched_at":"$ts1","watched_episodes_count":9,"next_to_watch":"S02E10","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88008,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..9).joinToString(",") { """{"number":$it,"watched_at":"$ts1"}""" }}]}]}]}
        """.trimIndent()

        val deviceA = createDevice("DeviceA_6")
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertEquals(10, deviceA.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)?.episodeNumber)

        // Utente rimuove stato visto da S2E9 su Simkl
        val ts2 = "2026-11-20T12:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","shows":{"all":"$ts2","watching":"$ts2","watched_at":"$ts2"},"movies":{},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = """
            {"shows":[{"status":"watching","last_watched_at":"$ts1","watched_episodes_count":8,"next_to_watch":"S02E09","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88008,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..8).joinToString(",") { """{"number":$it,"watched_at":"$ts1"}""" }}]}]}]}
        """.trimIndent()

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        assertEquals((1..8).toSet(), deviceA.db.vixFlixDao().getWatchedEpisodeNumbers(series.id, 2, DEFAULT_PRIMARY_PROFILE_ID).toSet())
        val cw = deviceA.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(cw)
        assertEquals(2, cw!!.seasonNumber)
        assertEquals(9, cw.episodeNumber)
        assertEquals(0L, cw.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 7 & 8: Persistenza dopo riavvio DB + aggiornamento reattivo UI StateFlow + playback attivo differito
    // -------------------------------------------------------------------------
    @Test
    fun `07_08 ViewModel continueWatching updates reactively after sync handles deferred active playback and persists across restart`() = runBlocking<Unit> {
        val dbDir = File(app.cacheDir, "cw_persist_db_${System.nanoTime()}").apply { mkdirs() }
        val dbFile = File(dbDir, "vixflix_persist.db")
        val series = seedStandardThreeSeasonSeries(tmdbId = 55009, title = "Reactive Persist Series")

        val ts1 = "2026-11-20T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","shows":{"all":"$ts1","watching":"$ts1"},"movies":{},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","watched_episodes_count":7,"next_to_watch":"S02E08","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88009,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..7).joinToString(",") { """{"number":$it}""" }}]}]}]}
        """.trimIndent()

        // Prima sessione app (con VixFlixViewModel reattivo)
        val dev1 = createDevice("PersistSession1", dbFile = dbFile)
        val viewModel = VixFlixViewModel(app, dev1.repo, dev1.manager)
        assertTrue(dev1.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        dev1.repo.saveWatchProgress(series, 2, 8, "S2 Ep 8", 480_000L, 2_880_000L, DEFAULT_PRIMARY_PROFILE_ID)
        waitUntil {
            val item = viewModel.continueWatching.value.firstOrNull { it.tmdbId == series.id }
            item?.seasonNumber == 2 && item.episodeNumber == 8
        }

        // Simula anche protezione playback attivo durante sync -> alla chiusura player Continua a guardare si aggiorna subito
        dev1.manager.setActivePlaybackTarget(DEFAULT_PRIMARY_PROFILE_ID, "tv", series.id, 2, 8)
        val ts2 = "2026-11-20T12:30:00Z"
        server.activitiesJson = """
            {"all":"$ts2","shows":{"all":"$ts2","watching":"$ts2","watched_at":"$ts2"},"movies":{},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = """
            {"shows":[{"status":"watching","last_watched_at":"$ts2","watched_episodes_count":9,"next_to_watch":"S02E10","show":{"title":"${series.title}","year":2022,"ids":{"simkl":88009,"tmdb":${series.id}}},"seasons":[{"number":2,"episodes":[${(1..9).joinToString(",") { """{"number":$it,"watched_at":"$ts2"}""" }}]}]}]}
        """.trimIndent()

        assertTrue(dev1.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        // Chiudi player (sblocca riconciliazione differita della serie)
        dev1.manager.clearActivePlaybackTarget()

        // Il flusso reattivo UI di Continua a guardare deve emettere S2E10 a 0m senza riavvii
        waitUntil {
            val item = viewModel.continueWatching.value.firstOrNull { it.tmdbId == series.id }
            item?.seasonNumber == 2 && item.episodeNumber == 10 && item.positionMs == 0L
        }
        dev1.close()

        // Riapertura dopo "riavvio app": verifica che Room su disco conservi S2E10 (0m) senza regressioni su S2E8
        val dev2 = createDevice("PersistSession2", dbFile = dbFile)
        val persistedCw = dev2.db.vixFlixDao().getWatchProgress("tv_${series.id}", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(persistedCw)
        assertEquals(2, persistedCw!!.seasonNumber)
        assertEquals(10, persistedCw.episodeNumber)
        assertEquals(0L, persistedCw.positionMs)
        assertNull(dev2.db.vixFlixDao().getEpisodeProgress("tv_${series.id}_s2_e8", DEFAULT_PRIMARY_PROFILE_ID))
        dbDir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // Fake Simkl + TMDB HTTP Server locale
    // -------------------------------------------------------------------------
    private class ContinueWatchingSyncFakeServer {
        private val executor = Executors.newCachedThreadPool()
        private lateinit var socket: ServerSocket
        private val running = AtomicBoolean(false)

        val baseUrl: String get() = "http://127.0.0.1:${socket.localPort}/"
        val recordedCalls = CopyOnWriteArrayList<String>()

        @Volatile var activitiesJson: String = """{"all":"2026-11-20T10:00:00Z","shows":{},"movies":{},"anime":{}}"""
        @Volatile var showsFullJson: String = """{"shows":[]}"""
        @Volatile var moviesFullJson: String = """{"movies":[]}"""
        @Volatile var animeFullJson: String = """{"anime":[]}"""
        @Volatile var deltaAllItemsJson: String = """{}"""
        @Volatile var playbackMoviesJson: String = """[]"""
        @Volatile var playbackEpisodesJson: String = """[]"""

        val tmdbTvJson = ConcurrentHashMap<Int, String>()
        val tmdbSeasonJson = ConcurrentHashMap<String, String>()

        fun start() {
            socket = ServerSocket(0)
            running.set(true)
            executor.submit {
                while (running.get()) {
                    try {
                        val client = socket.accept()
                        executor.submit { handleClient(client) }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }
        }

        fun stop() {
            running.set(false)
            runCatching { socket.close() }
            executor.shutdownNow()
        }

        private fun handleClient(client: Socket) {
            client.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val target = parts.getOrNull(1) ?: "/"
                val qIdx = target.indexOf('?')
                val path = if (qIdx >= 0) target.substring(0, qIdx) else target
                val query = if (qIdx >= 0) target.substring(qIdx + 1) else ""

                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    }
                }
                if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = reader.read(buf, read, contentLength - read)
                        if (n <= 0) break
                        read += n
                    }
                }
                recordedCalls.add("$method $target")

                val (code, body) = route(method, path, query)
                val out = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                val statusText = if (code == 200) "OK" else "Not Found"
                val bytes = body.toByteArray(Charsets.UTF_8)
                out.write("HTTP/1.1 $code $statusText\r\n")
                out.write("Content-Type: application/json; charset=utf-8\r\n")
                out.write("Content-Length: ${bytes.size}\r\n")
                out.write("Connection: close\r\n\r\n")
                out.flush()
                s.getOutputStream().write(bytes)
                s.getOutputStream().flush()
            }
        }

        private fun route(method: String, path: String, query: String): Pair<Int, String> {
            return when {
                method == "POST" && path == "/sync/activities" -> 200 to activitiesJson
                method == "GET" && path == "/sync/activities" -> 200 to activitiesJson
                method == "GET" && path == "/sync/all-items" -> 200 to deltaAllItemsJson
                method == "GET" && path == "/sync/all-items/shows" -> 200 to if (query.contains("date_from=")) deltaAllItemsJson else showsFullJson
                method == "GET" && path == "/sync/all-items/movies" -> 200 to moviesFullJson
                method == "GET" && path == "/sync/all-items/anime" -> 200 to animeFullJson
                method == "GET" && path == "/sync/playback/movies" -> 200 to playbackMoviesJson
                method == "GET" && path == "/sync/playback/episodes" -> 200 to playbackEpisodesJson
                method == "POST" && (path.startsWith("/scrobble/") || path.startsWith("/sync/history")) -> 200 to """{"added":{"episodes":1}}"""
                path.startsWith("/tv/") && path.contains("/season/") -> {
                    val tvSeg = path.removePrefix("/tv/").substringBefore("/season/").toIntOrNull()
                    val seasonSeg = path.substringAfter("/season/").substringBefore("/").toIntOrNull()
                    val json = if (tvSeg != null && seasonSeg != null) tmdbSeasonJson["${tvSeg}_${seasonSeg}"] else null
                    if (json != null) 200 to json else 404 to "{}"
                }
                path.startsWith("/tv/") -> {
                    val tvSeg = path.removePrefix("/tv/").substringBefore("/").toIntOrNull()
                    val json = tvSeg?.let { tmdbTvJson[it] }
                    if (json != null) 200 to json else 404 to "{}"
                }
                else -> 404 to "{}"
            }
        }
    }
}
