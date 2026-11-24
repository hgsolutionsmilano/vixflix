package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.PlayerSegmentKeys
import com.example.data.local.VixFlixDatabase
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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Suite di test obbligatoria (Test A - Test H + limiti API / metriche reali)
 * per l'ottimizzazione delle chiamate API Simkl secondo la documentazione ufficiale:
 * https://api.simkl.org/guides/sync
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimklApiOptimizationTest {

    private lateinit var app: Application
    private lateinit var server: OptimizationSimklServer
    private val virtualClockMs = AtomicLong(1_760_000_000_000L)
    private val openedDevices = mutableListOf<TestDevice>()

    private data class RecordedHttpCall(
        val method: String,
        val path: String,
        val queryParams: Map<String, String>,
        val body: String
    ) {
        val isSimklCall: Boolean
            get() = path.startsWith("/sync/") || path.startsWith("/scrobble/") || path.startsWith("/users/") || path.startsWith("/oauth2/")
    }

    private data class TestDevice(
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
            vaultDir.deleteRecursively()
        }
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = OptimizationSimklServer().apply { start() }
    }

    @After
    fun tearDown() {
        openedDevices.forEach { it.close() }
        openedDevices.clear()
        server.stop()
    }

    private fun createDevice(
        name: String,
        token: String = "tok_opt_alpha",
        syncCooldownMs: Long = 0L,
        fullReconcileIntervalMs: Long = SimklSyncManager.DEFAULT_FULL_RECONCILE_INTERVAL_MS
    ): TestDevice {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val vaultDir = File(app.cacheDir, "simkl_opt_${name}_${System.nanoTime()}").apply { mkdirs() }
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
                    expiresAtMs = virtualClockMs.get() + 86_400_000L
                )
            )
            db.vixFlixDao().upsertSimklProfileMeta(
                SimklProfileSyncMetaEntity(
                    profileId = DEFAULT_PRIMARY_PROFILE_ID,
                    connectionState = SimklConnectionState.CONNECTED_SYNCED.name,
                    accountDisplayName = "Tester $name"
                )
            )
        }
        val manager = SimklSyncManager(
            repository = repo,
            tokenStore = tokenStore,
            configProvider = {
                SimklConfig(
                    apiBaseUrl = server.baseUrl,
                    authBaseUrl = server.baseUrl.trimEnd('/'),
                    overrideClientId = "opt_client_id"
                )
            },
            minPostIntervalMs = 5L,
            authPollMinIntervalMs = 20L,
            syncCooldownMs = syncCooldownMs,
            fullReconcileIntervalMs = fullReconcileIntervalMs,
            clockMs = { virtualClockMs.get() },
            scope = scope
        )
        return TestDevice(db, vaultDir, tokenStore, repo, scope, manager).also { openedDevices.add(it) }
    }

    // -------------------------------------------------------------------------
    // TEST A: Nessuna modifica -> eseguita ESCLUSIVAMENTE GET /sync/activities
    // -------------------------------------------------------------------------
    @Test
    fun `test A no changes executes strictly GET sync activities and zero library or playback calls`() = runBlocking {
        val dev = createDevice("TestA")
        server.activitiesJson = """
            {"all":"2026-11-01T10:00:00Z","movies":{"all":"2026-11-01T09:00:00Z","playback":"2026-11-01T09:00:00Z"},"shows":{"all":"2026-11-01T10:00:00Z","playback":"2026-11-01T09:00:00Z"},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","last_watched_at":"2026-11-01T10:00:00Z","watched_episodes_count":1,"total_episodes_count":7,"next_to_watch":"S01E02","show":{"title":"Breaking Bad","year":2008,"ids":{"simkl":1001,"tmdb":1396}},"seasons":[{"number":1,"episodes":[{"number":1,"watched_at":"2026-11-01T10:00:00Z"}]}]}]}
        """.trimIndent()

        // Fase 1 iniziale
        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        val savedMeta = dev.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull("Raw activities snapshot JSON must be persisted after Phase 1", savedMeta?.activitiesSnapshotJson)
        assertEquals(1, dev.manager.apiMetrics.value.fullSyncsCount)

        // Reset chiamate registrate e metriche
        server.recordedCalls.clear()
        dev.manager.resetApiMetrics()

        // Fase 2 senza modifiche: ripeti su FOREGROUND, PERIODIC e MANUAL
        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.PERIODIC))
        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL))
        assertTrue(dev.manager.syncPlaybackFastPathNow(DEFAULT_PRIMARY_PROFILE_ID))

        val simklCalls = server.recordedCalls.filter { it.isSimklCall }
        assertEquals("Exactly 4 calls total (1 GET /sync/activities per check)", 4, simklCalls.size)
        assertTrue("Every single call must be GET /sync/activities", simklCalls.all { it.method == "GET" && it.path == "/sync/activities" })
        assertEquals(4, dev.manager.apiMetrics.value.callsAvoidedUnchangedActivities)
        assertEquals(0, simklCalls.count { it.path.startsWith("/sync/all-items") })
        assertEquals(0, simklCalls.count { it.path.startsWith("/sync/playback") })
    }

    // -------------------------------------------------------------------------
    // TEST B: Episodio modificato su Simkl -> delta incrementale con date_from esatto, NO full re-import
    // -------------------------------------------------------------------------
    @Test
    fun `test B episode modified on Simkl uses exact verbatim date_from delta without full category download`() = runBlocking {
        val dev = createDevice("TestB")
        val initialTs = "2026-11-01T10:00:00Z"
        server.activitiesJson = """
            {"all":"$initialTs","movies":{"all":"2026-11-01T08:00:00Z"},"shows":{"all":"$initialTs","watching":"$initialTs","watched_at":"$initialTs"},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","last_watched_at":"$initialTs","watched_episodes_count":1,"total_episodes_count":7,"next_to_watch":"S01E02","show":{"title":"Breaking Bad","year":2008,"ids":{"simkl":1001,"tmdb":1396}},"seasons":[{"number":1,"episodes":[{"number":1,"watched_at":"$initialTs"}]}]}]}
        """.trimIndent()

        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        server.recordedCalls.clear()
        dev.manager.resetApiMetrics()

        // Episodio S01E02 marcato visto su Simkl (solo shows.watching / shows.all cambiati, playback invariato)
        val updatedTs = "2026-11-01T11:30:00Z"
        server.activitiesJson = """
            {"all":"$updatedTs","movies":{"all":"2026-11-01T08:00:00Z"},"shows":{"all":"$updatedTs","watching":"$updatedTs","watched_at":"$updatedTs"},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = """
            {"shows":[{"status":"watching","last_watched_at":"$updatedTs","watched_episodes_count":2,"total_episodes_count":7,"next_to_watch":"S01E03","show":{"title":"Breaking Bad","year":2008,"ids":{"simkl":1001,"tmdb":1396}},"seasons":[{"number":1,"episodes":[{"number":1,"watched_at":"$initialTs"},{"number":2,"watched_at":"$updatedTs"}]}]}]}
        """.trimIndent()

        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val simklCalls = server.recordedCalls.filter { it.isSimklCall }
        // ESATTAMENTE 2 chiamate: GET /sync/activities + GET /sync/all-items?date_from=...
        assertEquals("Incremental episode update must use strictly 2 Simkl API calls", 2, simklCalls.size)
        assertEquals("/sync/activities", simklCalls[0].path)
        assertEquals("/sync/all-items", simklCalls[1].path)
        assertEquals("date_from must be verbatim previous activities.all", initialTs, simklCalls[1].queryParams["date_from"])
        assertEquals("full", simklCalls[1].queryParams["extended"])
        assertEquals("yes", simklCalls[1].queryParams["episode_watched_at"])
        // completed/dropped non cambiati -> non invia include_all_episodes=yes superfluo
        assertNull("Should omit include_all_episodes when completed/dropped unchanged", simklCalls[1].queryParams["include_all_episodes"])
        // Nessuna chiamata full category e nessuna chiamata playback!
        assertEquals(0, simklCalls.count { it.path == "/sync/all-items/shows" })
        assertEquals(0, simklCalls.count { it.path.startsWith("/sync/playback") })

        // Verifica stato locale aggiornato correttamente
        assertNotNull(dev.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(1396, "tv", 1, 2), DEFAULT_PRIMARY_PROFILE_ID))
        assertEquals(3, dev.db.vixFlixDao().getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID)?.episodeNumber)
    }

    // -------------------------------------------------------------------------
    // TEST C: Episodio segnato come NON visto su Simkl -> rilevato sia da delta che da fallback backward-timestamp
    // -------------------------------------------------------------------------
    @Test
    fun `test C unwatched episode on Simkl is detected and clears stale local watched state`() = runBlocking {
        val dev = createDevice("TestC")
        val ts1 = "2026-11-01T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","movies":{},"shows":{"all":"$ts1","watching":"$ts1","watched_at":"$ts1"},"anime":{}}
        """.trimIndent()
        server.showsFullJson = """
            {"shows":[{"status":"watching","last_watched_at":"$ts1","watched_episodes_count":2,"total_episodes_count":7,"next_to_watch":"S01E03","show":{"title":"Breaking Bad","year":2008,"ids":{"simkl":1001,"tmdb":1396}},"seasons":[{"number":1,"episodes":[{"number":1,"watched_at":"2026-11-01T09:00:00Z"},{"number":2,"watched_at":"$ts1"}]}]}]}
        """.trimIndent()

        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertNotNull(dev.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(1396, "tv", 1, 2), DEFAULT_PRIMARY_PROFILE_ID))

        // Caso C1: Simkl omette la serie dal delta `date_from` perché l'unwatch di S01E02 ha arretrato `last_watched_at`
        server.recordedCalls.clear()
        val ts2 = "2026-11-01T11:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","movies":{},"shows":{"all":"$ts2","watching":"$ts2","watched_at":"$ts2"},"anime":{}}
        """.trimIndent()
        server.deltaAllItemsJson = "{}" // date_from vuoto per arretramento timestamp!
        server.showsFullJson = """
            {"shows":[{"status":"watching","last_watched_at":"2026-11-01T09:00:00Z","watched_episodes_count":1,"total_episodes_count":7,"next_to_watch":"S01E02","show":{"title":"Breaking Bad","year":2008,"ids":{"simkl":1001,"tmdb":1396}},"seasons":[{"number":1,"episodes":[{"number":1,"watched_at":"2026-11-01T09:00:00Z"}]}]}]}
        """.trimIndent()

        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        // S01E02 deve essere stato deselezionato localmente, S01E01 ancora visto, Continua a guardare su S01E02
        assertNull("Unwatched S01E02 must be removed from completed_media", dev.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(1396, "tv", 1, 2), DEFAULT_PRIMARY_PROFILE_ID))
        assertNotNull("S01E01 must remain watched", dev.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(1396, "tv", 1, 1), DEFAULT_PRIMARY_PROFILE_ID))
        assertEquals(2, dev.db.vixFlixDao().getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID)?.episodeNumber)
        val simklCalls = server.recordedCalls.filter { it.isSimklCall }
        // Solo shows fallback eseguito (NO movies, NO anime, NO playback)
        assertTrue(simklCalls.any { it.path == "/sync/all-items/shows" })
        assertEquals(0, simklCalls.count { it.path == "/sync/all-items/movies" })
        assertEquals(0, simklCalls.count { it.path == "/sync/all-items/anime" })
        assertEquals(0, simklCalls.count { it.path.startsWith("/sync/playback") })
    }

    // -------------------------------------------------------------------------
    // TEST D: Contenuto rimosso su Simkl -> removed_from_list attiva simkl_ids_only SOLO in questo caso
    // -------------------------------------------------------------------------
    @Test
    fun `test D removed_from_list calls simkl_ids_only strictly when removal timestamp changes`() = runBlocking {
        val dev = createDevice("TestD")
        val ts1 = "2026-11-01T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","movies":{"all":"$ts1","plantowatch":"$ts1"},"shows":{},"anime":{}}
        """.trimIndent()
        server.moviesFullJson = """
            {"movies":[
              {"status":"plantowatch","movie":{"title":"Fight Club","year":1999,"ids":{"simkl":2001,"tmdb":550}}},
              {"status":"plantowatch","movie":{"title":"Inception","year":2010,"ids":{"simkl":2002,"tmdb":27205}}}
            ]}
        """.trimIndent()

        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertEquals(2, dev.db.vixFlixDao().getSimklLibraryByCategory(DEFAULT_PRIMARY_PROFILE_ID, "movies").size)
        // Verifica che simkl_ids_only NON sia stato chiamato in Fase 1
        assertEquals(0, server.recordedCalls.count { it.queryParams["extended"] == "simkl_ids_only" })

        server.recordedCalls.clear()

        // Rimozione di Inception (simkl 2002) su Simkl -> SOLO movies.removed_from_list cambia!
        val ts2 = "2026-11-01T12:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","movies":{"all":"$ts1","plantowatch":"$ts1","removed_from_list":"$ts2"},"shows":{},"anime":{}}
        """.trimIndent()
        server.simklIdsOnlyMoviesJson = """
            {"movies":[{"movie":{"ids":{"simkl":2001,"tmdb":550}}}]}
        """.trimIndent()

        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val simklCalls = server.recordedCalls.filter { it.isSimklCall }
        assertEquals("Removal-only sync should call strictly activities + movies simkl_ids_only", 2, simklCalls.size)
        assertEquals("/sync/activities", simklCalls[0].path)
        assertEquals("/sync/all-items/movies", simklCalls[1].path)
        assertEquals("simkl_ids_only", simklCalls[1].queryParams["extended"])

        val remainingMovies = dev.db.vixFlixDao().getSimklLibraryByCategory(DEFAULT_PRIMARY_PROFILE_ID, "movies")
        assertEquals(1, remainingMovies.size)
        assertEquals(550, remainingMovies.first().tmdbId)
    }

    // -------------------------------------------------------------------------
    // TEST E: Progresso playback aggiornato su altro dispositivo -> separato rigorosamente dalla watchlist
    // -------------------------------------------------------------------------
    @Test
    fun `test E playback update fetches strictly the changed playback type without touching library or other playback type`() = runBlocking {
        val dev = createDevice("TestE")
        val ts1 = "2026-11-01T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","movies":{"all":"$ts1","playback":"$ts1"},"shows":{"all":"$ts1","playback":"$ts1"},"anime":{}}
        """.trimIndent()

        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        server.recordedCalls.clear()

        // Altro dispositivo mette in pausa SOLO un film (movies.playback cambiato; shows.playback e libreria invariati)
        val ts2 = "2026-11-01T12:30:00Z"
        server.activitiesJson = """
            {"all":"$ts2","movies":{"all":"$ts1","playback":"$ts2"},"shows":{"all":"$ts1","playback":"$ts1"},"anime":{}}
        """.trimIndent()
        server.playbackMoviesJson = """
            [{"id":7701,"progress":40.0,"paused_at":"$ts2","movie":{"title":"Fight Club","year":1999,"ids":{"simkl":2001,"tmdb":550}}}]
        """.trimIndent()

        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val simklCalls = server.recordedCalls.filter { it.isSimklCall }
        assertEquals("Strictly 2 Simkl calls: activities + playback/movies", 2, simklCalls.size)
        assertEquals("/sync/activities", simklCalls[0].path)
        assertEquals("/sync/playback/movies", simklCalls[1].path)
        assertEquals(0, simklCalls.count { it.path == "/sync/playback/episodes" })
        assertEquals(0, simklCalls.count { it.path.startsWith("/sync/all-items") })

        val prog = dev.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull("Movie playback progress must be restored", prog)
        // Fight Club runtime = 120 min -> 40% = 48 min = 2_880_000 ms
        assertEquals(2_880_000L, prog!!.positionMs)
    }

    // -------------------------------------------------------------------------
    // TEST F: Libreria molto grande -> HTTP 400 max_items -> split aggregato -> categoria -> stato -> original
    //         e snapshot NON salvato prematuramente se uno split fallisce
    // -------------------------------------------------------------------------
    @Test
    fun `test F 400 max_items splits without repeating identical request and falls back to include_all_episodes original`() = runBlocking {
        val dev = createDevice("TestF")
        val ts1 = "2026-11-01T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","movies":{"all":"$ts1"},"shows":{"all":"$ts1","completed":"$ts1"},"anime":{}}
        """.trimIndent()
        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        val snapshotBefore = dev.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)?.activitiesSnapshotJson
        assertNotNull(snapshotBefore)

        server.recordedCalls.clear()
        dev.manager.resetApiMetrics()

        // Modifica enorme su shows (completed cambiato) -> aggregato /sync/all-items risponde 400 max_items,
        // categoria /sync/all-items/shows risponde 400 max_items,
        // stato /sync/all-items/shows/completed con include_all_episodes=yes risponde 400 max_items,
        // fallback con include_all_episodes=original risponde 200 OK!
        val ts2 = "2026-11-01T14:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","movies":{"all":"$ts1"},"shows":{"all":"$ts2","completed":"$ts2"},"anime":{}}
        """.trimIndent()
        server.returnMaxItemsOnDelta = true
        server.returnMaxItemsOnShowsCategory = true
        server.returnMaxItemsOnShowsCompletedYes = true
        server.showsCompletedOriginalJson = """
            {"shows":[{"status":"completed","last_watched_at":"$ts2","watched_episodes_count":2,"total_episodes_count":2,"show":{"title":"Breaking Bad","year":2008,"ids":{"simkl":1001,"tmdb":1396}},"seasons":[{"number":1,"episodes":[{"number":1,"watched_at":"$ts1"},{"number":2,"watched_at":"$ts2"}]}]}]}
        """.trimIndent()

        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val simklCalls = server.recordedCalls.filter { it.isSimklCall }
        assertTrue("Aggregated delta was attempted first", simklCalls.any { it.path == "/sync/all-items" && it.queryParams["date_from"] == ts1 })
        assertTrue("Escalated to category /sync/all-items/shows with date_from", simklCalls.any { it.path == "/sync/all-items/shows" && it.queryParams["date_from"] == ts1 })
        assertTrue(
            "Escalated to /sync/all-items/shows/completed with include_all_episodes=yes",
            simklCalls.any { it.path == "/sync/all-items/shows/completed" && it.queryParams["include_all_episodes"] == "yes" }
        )
        assertTrue(
            "Fell back to /sync/all-items/shows/completed with include_all_episodes=original",
            simklCalls.any { it.path == "/sync/all-items/shows/completed" && it.queryParams["include_all_episodes"] == "original" }
        )
        // Nessuna richiesta identica ripetuta
        val completedYesCalls = simklCalls.count { it.path == "/sync/all-items/shows/completed" && it.queryParams["include_all_episodes"] == "yes" }
        assertEquals("Must never repeat identical 400 max_items call", 1, completedYesCalls)
        assertTrue("maxItems400Count metric must be tracked", dev.manager.apiMetrics.value.maxItems400Count >= 3)
        assertEquals(ts2, dev.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)?.lastActivitiesAll)

        // Sub-test F2: se uno degli stati fallisce (HTTP 500), lo snapshot NON viene avanzato prematuramente!
        val ts3 = "2026-11-01T15:00:00Z"
        server.activitiesJson = """
            {"all":"$ts3","movies":{"all":"$ts1"},"shows":{"all":"$ts3","completed":"$ts3"},"anime":{}}
        """.trimIndent()
        server.failShowsHoldStatusWith500 = true
        assertFalse("Partial failure during split must fail sync", dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        assertEquals("Snapshot timestamp must remain at ts2 on partial split failure", ts2, dev.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)?.lastActivitiesAll)
    }

    // -------------------------------------------------------------------------
    // TEST G: Errore durante delta -> vecchio snapshot preservato verbatim, retry successivo con stesso date_from recupera tutto
    // -------------------------------------------------------------------------
    @Test
    fun `test G delta error preserves previous snapshot verbatim and next sync recovers with exact same date_from`() = runBlocking {
        val dev = createDevice("TestG")
        val ts1 = "2026-11-01T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","movies":{"all":"$ts1"},"shows":{},"anime":{}}
        """.trimIndent()
        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        val originalSnapshot = dev.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)?.activitiesSnapshotJson
        assertNotNull(originalSnapshot)

        // Modifica remota, ma GET /sync/all-items fallisce con HTTP 500
        val ts2 = "2026-11-01T16:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","movies":{"all":"$ts2","completed":"$ts2"},"shows":{},"anime":{}}
        """.trimIndent()
        server.failDeltaWith500 = true
        assertFalse(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        // Vecchio snapshot intatto!
        val metaAfterErr = dev.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)
        assertEquals(ts1, metaAfterErr?.lastActivitiesAll)
        assertEquals(originalSnapshot, metaAfterErr?.activitiesSnapshotJson)
        assertTrue(dev.manager.apiMetrics.value.incompleteSyncsCount >= 1)

        // Risoluzione errore -> prossimo sync usa ancora `date_from = ts1` e recupera il film completato!
        server.failDeltaWith500 = false
        server.recordedCalls.clear()
        server.deltaAllItemsJson = """
            {"movies":[{"status":"completed","last_watched_at":"$ts2","movie":{"title":"Fight Club","year":1999,"ids":{"simkl":2001,"tmdb":550}}}]}
        """.trimIndent()
        assertTrue(dev.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val deltaCall = server.recordedCalls.firstOrNull { it.path == "/sync/all-items" }
        assertNotNull(deltaCall)
        assertEquals("Retry must pass exact preserved date_from ts1", ts1, deltaCall!!.queryParams["date_from"])
        // Movie-only delta non deve inviare parametri pesanti per episodi!
        assertNull("Movie-only delta must omit extended=full", deltaCall.queryParams["extended"])
        assertNull("Movie-only delta must omit episode_watched_at", deltaCall.queryParams["episode_watched_at"])
        assertNull("Movie-only delta must omit include_all_episodes", deltaCall.queryParams["include_all_episodes"])

        assertEquals(ts2, dev.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)?.lastActivitiesAll)
        assertNotNull(dev.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(550, "movie"), DEFAULT_PRIMARY_PROFILE_ID))
    }

    // -------------------------------------------------------------------------
    // TEST H: Multidispositivo + protezione HTTP 429 Retry-After + cooldown cambio schermata + snapshot corrotto
    // -------------------------------------------------------------------------
    @Test
    fun `test H multi-device propagation minimizes calls respects 429 Retry-After and recovers corrupted snapshot`() = runBlocking {
        val devA = createDevice("DeviceA", syncCooldownMs = 20_000L)
        val devB = createDevice("DeviceB", syncCooldownMs = 20_000L)

        val ts1 = "2026-11-01T10:00:00Z"
        server.activitiesJson = """
            {"all":"$ts1","movies":{"all":"$ts1","playback":"$ts1"},"shows":{"all":"$ts1","playback":"$ts1"},"anime":{}}
        """.trimIndent()
        assertTrue(devA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(devB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        // Cambio schermata ravvicinato (DETAIL_OPEN / CONTINUE_WATCHING_OPEN) entro 60s -> 0 chiamate!
        server.recordedCalls.clear()
        virtualClockMs.addAndGet(10_000L)
        devB.manager.syncIfNeeded(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.DETAIL_OPEN)
        devB.manager.syncIfNeeded(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.CONTINUE_WATCHING_OPEN)
        delay(80L)
        assertEquals("Screen switches inside 60s cooldown must emit 0 calls", 0, server.recordedCalls.count { it.isSimklCall })

        // Device A invia un pause duplicato (entro 15s, stessa posizione <1%): deve inviare 1 solo POST /scrobble/pause!
        val movie = MediaItem(550, "movie", "Fight Club", "Test", "/fc.jpg", "/fc_bg.jpg", 8.4, "1999")
        devA.manager.enqueueScrobblePause(DEFAULT_PRIMARY_PROFILE_ID, movie, null, null, 3_600_000L, 7_200_000L)
        repeat(40) {
            if (server.recordedCalls.any { it.path == "/scrobble/pause" } &&
                devA.db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).isEmpty()
            ) return@repeat
            delay(25L)
        }
        virtualClockMs.addAndGet(2_000L)
        devA.manager.enqueueScrobblePause(DEFAULT_PRIMARY_PROFILE_ID, movie, null, null, 3_610_000L, 7_200_000L) // +0.14% < 1%
        delay(120L)
        assertEquals("Micro-pause <1% within 15s must be deduplicated", 1, server.recordedCalls.count { it.path == "/scrobble/pause" })

        // Device B sincronizza con fast-path pre-playback: recupera il 50% con SOLE 2 chiamate (activities + playback/movies)
        val ts2 = "2026-11-01T12:00:00Z"
        server.activitiesJson = """
            {"all":"$ts2","movies":{"all":"$ts1","playback":"$ts2"},"shows":{"all":"$ts1","playback":"$ts1"},"anime":{}}
        """.trimIndent()
        server.playbackMoviesJson = """
            [{"id":8801,"progress":50.0,"paused_at":"$ts2","movie":{"title":"Fight Club","year":1999,"ids":{"simkl":2001,"tmdb":550}}}]
        """.trimIndent()
        server.recordedCalls.clear()
        assertTrue(devB.manager.syncPlaybackFastPathNow(DEFAULT_PRIMARY_PROFILE_ID))
        val fastPathCalls = server.recordedCalls.filter { it.isSimklCall }
        assertEquals(2, fastPathCalls.size)
        assertEquals("/sync/activities", fastPathCalls[0].path)
        assertEquals("/sync/playback/movies", fastPathCalls[1].path)
        assertEquals(3_600_000L, devB.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)?.positionMs)

        // HTTP 429 con Retry-After: non deve suddividere in 5 status e deve bloccare trigger automatici finché scade
        server.recordedCalls.clear()
        server.return429OnActivities = true
        assertFalse(devB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.PERIODIC))
        assertEquals(1, devB.manager.apiMetrics.value.rateLimit429Count)
        assertEquals("429 must stop immediately without cascading calls", 1, server.recordedCalls.count { it.isSimklCall })

        server.return429OnActivities = false
        server.recordedCalls.clear()
        // Retry-After = 120s: un trigger automatico a +5s viene saltato senza chiamate!
        virtualClockMs.addAndGet(5_000L)
        devB.manager.syncIfNeeded(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND)
        delay(80L)
        assertEquals("Automatic sync must respect Retry-After window", 0, server.recordedCalls.count { it.isSimklCall })

        // Snapshot corrotto localmente -> forza automaticamente Fase 1 completa
        val metaB = devB.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)!!
        devB.db.vixFlixDao().upsertSimklProfileMeta(metaB.copy(activitiesSnapshotJson = "{corrupted_json"))
        server.recordedCalls.clear()
        assertTrue(devB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL))
        val recoveryCalls = server.recordedCalls.filter { it.isSimklCall }
        assertTrue("Corrupted snapshot must trigger Phase 1 full sync", recoveryCalls.any { it.path == "/sync/all-items/shows" })
        assertTrue(recoveryCalls.any { it.path == "/sync/all-items/movies" })
        assertTrue(recoveryCalls.any { it.path == "/sync/all-items/anime" })
    }

    // -------------------------------------------------------------------------
    // Embedded deterministic Simkl + TMDB HTTP Server
    // -------------------------------------------------------------------------
    private inner class OptimizationSimklServer {
        private val serverSocket = ServerSocket(0)
        private val running = AtomicBoolean(true)
        private val executor = Executors.newCachedThreadPool()

        val baseUrl: String get() = "http://127.0.0.1:${serverSocket.localPort}/"
        val recordedCalls = CopyOnWriteArrayList<RecordedHttpCall>()

        @Volatile var activitiesJson: String = """{"all":"2026-11-01T10:00:00Z","movies":{},"shows":{},"anime":{}}"""
        @Volatile var showsFullJson: String = """{"shows":[]}"""
        @Volatile var moviesFullJson: String = """{"movies":[]}"""
        @Volatile var animeFullJson: String = """{"anime":[]}"""
        @Volatile var deltaAllItemsJson: String = "{}"
        @Volatile var simklIdsOnlyMoviesJson: String = """{"movies":[]}"""
        @Volatile var playbackMoviesJson: String = "[]"
        @Volatile var playbackEpisodesJson: String = "[]"
        @Volatile var showsCompletedOriginalJson: String = """{"shows":[]}"""

        @Volatile var returnMaxItemsOnDelta: Boolean = false
        @Volatile var returnMaxItemsOnShowsCategory: Boolean = false
        @Volatile var returnMaxItemsOnShowsCompletedYes: Boolean = false
        @Volatile var failShowsHoldStatusWith500: Boolean = false
        @Volatile var failDeltaWith500: Boolean = false
        @Volatile var return429OnActivities: Boolean = false

        fun start() {
            executor.execute {
                while (running.get()) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    executor.execute { handle(socket) }
                }
            }
        }

        fun stop() {
            running.set(false)
            runCatching { serverSocket.close() }
            executor.shutdownNow()
        }

        private fun handle(socket: Socket) {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val rawTarget = parts[1]
                val qIdx = rawTarget.indexOf('?')
                val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
                val queryParams = LinkedHashMap<String, String>()
                if (qIdx >= 0 && qIdx + 1 < rawTarget.length) {
                    rawTarget.substring(qIdx + 1).split("&").forEach { pair ->
                        val eq = pair.indexOf('=')
                        if (eq > 0) {
                            val k = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                            val v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                            queryParams[k] = v
                        }
                    }
                }
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                val bodyChars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(bodyChars, read, contentLength - read)
                    if (r <= 0) break
                    read += r
                }
                val body = String(bodyChars, 0, read)
                recordedCalls.add(RecordedHttpCall(method, path, queryParams, body))

                val (statusCode, extraHeaders, responseBody) = route(method, path, queryParams)
                val out = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                val bytes = responseBody.toByteArray(Charsets.UTF_8)
                out.write("HTTP/1.1 $statusCode OK\r\n")
                out.write("Content-Type: application/json\r\n")
                out.write("Content-Length: ${bytes.size}\r\n")
                extraHeaders.forEach { (k, v) -> out.write("$k: $v\r\n") }
                out.write("Connection: close\r\n\r\n")
                out.write(responseBody)
                out.flush()
            }
        }

        private fun route(
            method: String,
            path: String,
            query: Map<String, String>
        ): Triple<Int, Map<String, String>, String> {
            return when {
                path == "/sync/activities" -> {
                    if (return429OnActivities) {
                        Triple(429, mapOf("Retry-After" to "120"), """{"error":"rate_limit_exceeded"}""")
                    } else {
                        Triple(200, emptyMap(), activitiesJson)
                    }
                }
                path == "/sync/all-items" -> {
                    if (failDeltaWith500) {
                        Triple(500, emptyMap(), """{"error":"internal_error"}""")
                    } else if (returnMaxItemsOnDelta) {
                        Triple(400, emptyMap(), """{"error":"max_items","message":"Too many items requested"}""")
                    } else {
                        Triple(200, emptyMap(), deltaAllItemsJson)
                    }
                }
                path == "/sync/all-items/shows" -> {
                    if (returnMaxItemsOnShowsCategory) {
                        Triple(400, emptyMap(), """{"error":"max_items"}""")
                    } else {
                        Triple(200, emptyMap(), showsFullJson)
                    }
                }
                path.startsWith("/sync/all-items/shows/") -> {
                    val status = path.removePrefix("/sync/all-items/shows/")
                    when {
                        status == "hold" && failShowsHoldStatusWith500 ->
                            Triple(500, emptyMap(), """{"error":"server_error"}""")
                        status == "completed" && returnMaxItemsOnShowsCompletedYes && query["include_all_episodes"] == "yes" ->
                            Triple(400, emptyMap(), """{"error":"max_items"}""")
                        status == "completed" && query["include_all_episodes"] == "original" ->
                            Triple(200, emptyMap(), showsCompletedOriginalJson)
                        else -> Triple(200, emptyMap(), """{"shows":[]}""")
                    }
                }
                path == "/sync/all-items/movies" -> {
                    if (query["extended"] == "simkl_ids_only") {
                        Triple(200, emptyMap(), simklIdsOnlyMoviesJson)
                    } else {
                        Triple(200, emptyMap(), moviesFullJson)
                    }
                }
                path == "/sync/all-items/anime" -> Triple(200, emptyMap(), animeFullJson)
                path == "/sync/playback/movies" -> Triple(200, emptyMap(), playbackMoviesJson)
                path == "/sync/playback/episodes" -> Triple(200, emptyMap(), playbackEpisodesJson)
                method == "POST" && path.startsWith("/scrobble/") ->
                    Triple(200, emptyMap(), """{"id":8801,"action":"${path.substringAfterLast('/')}","progress":50.0}""")
                path == "/movie/550" ->
                    Triple(200, emptyMap(), """{"id":550,"title":"Fight Club","overview":"Test","poster_path":"/fc.jpg","backdrop_path":"/fc_bg.jpg","vote_average":8.4,"release_date":"1999-10-15","runtime":120}""")
                path == "/movie/27205" ->
                    Triple(200, emptyMap(), """{"id":27205,"title":"Inception","overview":"Test","poster_path":"/inc.jpg","backdrop_path":"/inc_bg.jpg","vote_average":8.8,"release_date":"2010-07-16","runtime":148}""")
                path == "/tv/1396" ->
                    Triple(200, emptyMap(), """{"id":1396,"name":"Breaking Bad","overview":"Test","poster_path":"/bb.jpg","backdrop_path":"/bb_bg.jpg","vote_average":8.9,"first_air_date":"2008-01-20","seasons":[{"season_number":1,"episode_count":7}]}""")
                path == "/tv/1396/season/1" ->
                    Triple(200, emptyMap(), """{"episodes":[{"episode_number":1,"name":"Pilot","runtime":58,"air_date":"2008-01-20"},{"episode_number":2,"name":"Cat's in the Bag","runtime":48,"air_date":"2008-01-27"},{"episode_number":3,"name":"And the Bag's in the River","runtime":48,"air_date":"2008-02-10"}]}""")
                else -> Triple(200, emptyMap(), "{}")
            }
        }
    }
}
