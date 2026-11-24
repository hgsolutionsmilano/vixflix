package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.PlayerSegmentKeys
import com.example.data.local.UserProfileEntity
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.simkl.SimklProfileSyncMetaEntity
import com.example.data.local.simkl.SimklSecureTokenStore
import com.example.data.local.simkl.SimklTokenBundle
import com.example.data.local.simkl.SoftwareAesGcmSimklCipherEngine
import com.example.data.remote.MediaItem
import com.example.data.remote.simkl.SimklConfig
import com.example.data.remote.simkl.SimklConnectionState
import com.example.data.remote.simkl.SimklLibraryJsonParser
import com.example.data.remote.simkl.SimklSyncManager
import com.example.data.remote.simkl.SimklSyncTrigger
import com.example.data.repository.VixFlixRepository
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
import org.json.JSONArray
import org.json.JSONObject
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
 * Audit completo multi-dispositivo della sincronizzazione Simkl in Vixflix.
 * Simula due dispositivi indipendenti (Device A e Device B), ciascuno con il proprio
 * database Room, TokenStore, Repository e SimklSyncManager, connessi a un server HTTP
 * Simkl + TMDB stateful condiviso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimklMultiDeviceSyncAuditTest {

    private lateinit var app: Application
    private lateinit var server: StatefulMultiDeviceSimklServer
    private lateinit var deviceA: SimulatedDevice
    private lateinit var deviceB: SimulatedDevice
    private val virtualClockMs = AtomicLong(1_760_000_000_000L)

    private data class SimulatedDevice(
        val name: String,
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
        server = StatefulMultiDeviceSimklServer().apply { start() }
        deviceA = createDevice("DeviceA")
        deviceB = createDevice("DeviceB")
        connectProfile(deviceA, DEFAULT_PRIMARY_PROFILE_ID, "tok_account_alpha")
        connectProfile(deviceB, DEFAULT_PRIMARY_PROFILE_ID, "tok_account_alpha")
    }

    @After
    fun tearDown() {
        deviceA.close()
        deviceB.close()
        server.stop()
    }

    private fun createDevice(
        name: String,
        fullReconcileIntervalMs: Long = SimklSyncManager.DEFAULT_FULL_RECONCILE_INTERVAL_MS
    ): SimulatedDevice {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val vaultDir = File(app.cacheDir, "simkl_audit_${name}_${System.nanoTime()}").apply { mkdirs() }
        val tokenStore = SimklSecureTokenStore(vaultDir, SoftwareAesGcmSimklCipherEngine())
        val repo = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = server.baseUrl,
            vixBaseUrl = server.baseUrl,
            httpCacheDir = File(vaultDir, "http_cache").apply { mkdirs() }
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runBlocking { repo.ensureDefaultPrimaryProfile() }
        val manager = SimklSyncManager(
            repository = repo,
            tokenStore = tokenStore,
            configProvider = {
                SimklConfig(
                    apiBaseUrl = server.baseUrl,
                    authBaseUrl = server.baseUrl.trimEnd('/'),
                    overrideClientId = "audit_client_id"
                )
            },
            minPostIntervalMs = 5L,
            authPollMinIntervalMs = 20L,
            syncCooldownMs = 0L,
            fullReconcileIntervalMs = fullReconcileIntervalMs,
            clockMs = { virtualClockMs.get() },
            scope = scope
        )
        return SimulatedDevice(name, db, vaultDir, tokenStore, repo, scope, manager)
    }

    private fun connectProfile(
        device: SimulatedDevice,
        profileId: String,
        accessToken: String,
        refreshToken: String = "ref_$accessToken"
    ) {
        runBlocking {
            device.tokenStore.saveTokens(
                profileId,
                SimklTokenBundle(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMs = virtualClockMs.get() + 86_400_000L
                )
            )
            device.db.vixFlixDao().upsertSimklProfileMeta(
                SimklProfileSyncMetaEntity(
                    profileId = profileId,
                    connectionState = SimklConnectionState.CONNECTED_SYNCED.name,
                    accountDisplayName = "tester_$accessToken"
                )
            )
        }
    }

    private fun fightClubMovie(): MediaItem = MediaItem(
        id = 550,
        mediaType = "movie",
        title = "Fight Club",
        overview = "Test movie",
        posterPath = "/fc.jpg",
        backdropPath = "/fc_bg.jpg",
        voteAverage = 8.4,
        releaseYear = "1999"
    )

    private fun breakingBadSeries(): MediaItem = MediaItem(
        id = 1396,
        mediaType = "tv",
        title = "Breaking Bad",
        overview = "Test series",
        posterPath = "/bb.jpg",
        backdropPath = "/bb_bg.jpg",
        voteAverage = 8.9,
        releaseYear = "2008"
    )

    private fun inceptionMovie(): MediaItem = MediaItem(
        id = 27205,
        mediaType = "movie",
        title = "Inception",
        overview = "Dream heist",
        posterPath = "/inc.jpg",
        backdropPath = "/inc_bg.jpg",
        voteAverage = 8.8,
        releaseYear = "2010"
    )

    private suspend fun waitUntilWriteSent(
        device: SimulatedDevice,
        expectedRequestPrefix: String,
        previousMatches: Int,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ) {
        repeat(80) {
            val matched = server.requestLog.count { it.startsWith(expectedRequestPrefix) } > previousMatches
            val queueEmpty = device.db.vixFlixDao().getSimklPendingScrobbles(profileId).isEmpty()
            if (matched && queueEmpty) return
            delay(25L)
        }
    }

    // -------------------------------------------------------------------------
    // SCENARIO 1: Film parziale su Dispositivo A -> ripresa esatta su Dispositivo B
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 01 partial movie playback on Device A resumes accurately on Device B`() = runBlocking {
        // Initial sync on both devices
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        val movie = fightClubMovie()
        val durationMs = 120L * 60_000L // 120 min = 7_200_000 ms
        val pausedPosMs = 54L * 60_000L // 45% = 3_240_000 ms

        val pausesBefore = server.requestLog.count { it.startsWith("POST /scrobble/pause") }
        deviceA.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = movie,
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = pausedPosMs,
            durationMs = durationMs
        )
        waitUntilWriteSent(deviceA, "POST /scrobble/pause", pausesBefore)

        // Device B syncs (e.g. app launch / foreground)
        virtualClockMs.addAndGet(5_000L)
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val progressB = deviceB.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull("Device B must show Fight Club in Continue Watching", progressB)
        assertEquals(durationMs, progressB!!.durationMs)
        assertEquals(pausedPosMs, progressB.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 2: Film completato su Dispositivo A -> sparisce da Continua a guardare su Dispositivo B
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 02 movie completed on Device A disappears from Continue Watching on Device B`() = runBlocking {
        val movie = fightClubMovie()
        val durationMs = 120L * 60_000L

        // Step 1: Device A pauses at 45%, Device B syncs it into Continue Watching
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        val pausesBefore = server.requestLog.count { it.startsWith("POST /scrobble/pause") }
        deviceA.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = movie,
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = 54L * 60_000L,
            durationMs = durationMs
        )
        waitUntilWriteSent(deviceA, "POST /scrobble/pause", pausesBefore)
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        assertNotNull(deviceB.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID))

        // Step 2: Device A completes movie (95% stop)
        virtualClockMs.addAndGet(10_000L)
        val stopsBefore = server.requestLog.count { it.startsWith("POST /scrobble/stop") }
        deviceA.manager.enqueueScrobbleStop(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = movie,
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = (durationMs * 0.95).toLong(),
            durationMs = durationMs,
            isExplicitCompletion = true
        )
        waitUntilWriteSent(deviceA, "POST /scrobble/stop", stopsBefore)

        // Step 3: Device B syncs incrementally
        virtualClockMs.addAndGet(5_000L)
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        assertNull(
            "Completed movie must be removed from Continue Watching on Device B",
            deviceB.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)
        )
        assertNotNull(
            "Completed movie must be persisted in completed_media on Device B",
            deviceB.db.vixFlixDao().getCompletedMedia(
                PlayerSegmentKeys.completedMediaKey(550, "movie"),
                DEFAULT_PRIMARY_PROFILE_ID
            )
        )
        assertNull(
            "Playback baseline must be cleared on completion",
            deviceB.db.vixFlixDao().getSimklBaseline(DEFAULT_PRIMARY_PROFILE_ID, "movie_550")
        )
    }

    // -------------------------------------------------------------------------
    // SCENARIO 3: Episodio parziale su Dispositivo A -> ripresa esatta su Dispositivo B
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 03 partial episode on Device A syncs to Device B with exact episode and position`() = runBlocking {
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        val series = breakingBadSeries()
        val epDurationMs = 50L * 60_000L // 50 min = 3_000_000 ms
        val pausedPosMs = 30L * 60_000L // 60% = 1_800_000 ms

        val pausesBefore = server.requestLog.count { it.startsWith("POST /scrobble/pause") }
        deviceA.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = series,
            seasonNumber = 1,
            episodeNumber = 3,
            episodeTitle = "And the Bag's in the River",
            positionMs = pausedPosMs,
            durationMs = epDurationMs
        )
        waitUntilWriteSent(deviceA, "POST /scrobble/pause", pausesBefore)

        virtualClockMs.addAndGet(5_000L)
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val mainCardB = deviceB.db.vixFlixDao().getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID)
        val epProgressB = deviceB.db.vixFlixDao().getEpisodeProgress("tv_1396_s1_e3", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(mainCardB)
        assertEquals(1, mainCardB!!.seasonNumber)
        assertEquals(3, mainCardB.episodeNumber)
        assertEquals(pausedPosMs, mainCardB.positionMs)
        assertNotNull(epProgressB)
        assertEquals(pausedPosMs, epProgressB!!.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 4: Episodio completato su Dispositivo A -> Dispositivo B avanza al successivo
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 04 episode completed on Device A advances Device B to next episode`() = runBlocking {
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        val series = breakingBadSeries()
        val epDurationMs = 50L * 60_000L

        // Device A pauses S01E03, Device B sees S01E03 paused
        val pausesBefore = server.requestLog.count { it.startsWith("POST /scrobble/pause") }
        deviceA.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = series,
            seasonNumber = 1,
            episodeNumber = 3,
            episodeTitle = "Episode 3",
            positionMs = 20L * 60_000L,
            durationMs = epDurationMs
        )
        waitUntilWriteSent(deviceA, "POST /scrobble/pause", pausesBefore)
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        assertEquals(3, deviceB.db.vixFlixDao().getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID)?.episodeNumber)

        // Device A finishes S01E03
        virtualClockMs.addAndGet(15_000L)
        val stopsBefore = server.requestLog.count { it.startsWith("POST /scrobble/stop") }
        deviceA.manager.enqueueScrobbleStop(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = series,
            seasonNumber = 1,
            episodeNumber = 3,
            episodeTitle = "Episode 3",
            positionMs = 48L * 60_000L,
            durationMs = epDurationMs,
            isExplicitCompletion = true
        )
        waitUntilWriteSent(deviceA, "POST /scrobble/stop", stopsBefore)

        // Device B syncs -> S01E03 is completed and Continue Watching advances to S01E04
        virtualClockMs.addAndGet(5_000L)
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        assertNotNull(
            deviceB.db.vixFlixDao().getCompletedMedia(
                PlayerSegmentKeys.completedMediaKey(1396, "tv", 1, 3),
                DEFAULT_PRIMARY_PROFILE_ID
            )
        )
        assertNull(deviceB.db.vixFlixDao().getEpisodeProgress("tv_1396_s1_e3", DEFAULT_PRIMARY_PROFILE_ID))

        val nextCardB = deviceB.db.vixFlixDao().getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull("Continue Watching must advance to next episode", nextCardB)
        assertEquals(1, nextCardB!!.seasonNumber)
        assertEquals(4, nextCardB.episodeNumber)
        assertEquals(0L, nextCardB.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 5: Modifica diretta su Simkl (visto / non visto) -> recepita da tutti i dispositivi
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 05 direct Simkl edit watched and unwatched propagates to both devices`() = runBlocking {
        // Direct edit on Simkl web: mark S01E01 & S01E02 watched
        server.directEditShowOnSimkl(
            accountToken = "tok_account_alpha",
            simklId = 1001L,
            tmdbId = 1396,
            title = "Breaking Bad",
            year = 2008,
            status = "watching",
            watchedEpisodes = listOf(1 to 1, 1 to 2),
            nextToWatch = "S01E03",
            timestampIso = formatIso(virtualClockMs.addAndGet(5_000L))
        )

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        for (dev in listOf(deviceA, deviceB)) {
            assertNotNull(dev.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(1396, "tv", 1, 1), DEFAULT_PRIMARY_PROFILE_ID))
            assertNotNull(dev.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(1396, "tv", 1, 2), DEFAULT_PRIMARY_PROFILE_ID))
            assertEquals(3, dev.db.vixFlixDao().getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID)?.episodeNumber)
        }

        // Direct edit on Simkl web: UNWATCH S01E02
        server.directEditShowOnSimkl(
            accountToken = "tok_account_alpha",
            simklId = 1001L,
            tmdbId = 1396,
            title = "Breaking Bad",
            year = 2008,
            status = "watching",
            watchedEpisodes = listOf(1 to 1),
            nextToWatch = "S01E02",
            timestampIso = formatIso(virtualClockMs.addAndGet(10_000L))
        )

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        for (dev in listOf(deviceA, deviceB)) {
            assertNotNull("${dev.name} keeps S01E01 watched", dev.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(1396, "tv", 1, 1), DEFAULT_PRIMARY_PROFILE_ID))
            assertNull("${dev.name} unmarks S01E02", dev.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(1396, "tv", 1, 2), DEFAULT_PRIMARY_PROFILE_ID))
            assertEquals("${dev.name} moves Continue Watching back to S01E02", 2, dev.db.vixFlixDao().getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID)?.episodeNumber)
        }
    }

    // -------------------------------------------------------------------------
    // SCENARIO 6: Hold / Dropped su Simkl -> esclusi da Continua a guardare su tutti i dispositivi
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 06 hold and dropped statuses on Simkl exclude items from Continue Watching even with stale playback`() = runBlocking {
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        // Device A pauses both Breaking Bad S01E02 and Fight Club
        val pausesBeforeShow = server.requestLog.count { it.startsWith("POST /scrobble/pause") }
        deviceA.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = breakingBadSeries(),
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "Cat's in the Bag",
            positionMs = 25L * 60_000L,
            durationMs = 50L * 60_000L
        )
        waitUntilWriteSent(deviceA, "POST /scrobble/pause", pausesBeforeShow)
        val pausesBeforeMovie = server.requestLog.count { it.startsWith("POST /scrobble/pause") }
        deviceA.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = fightClubMovie(),
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = 48L * 60_000L,
            durationMs = 120L * 60_000L
        )
        waitUntilWriteSent(deviceA, "POST /scrobble/pause", pausesBeforeMovie)
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        assertNotNull(deviceB.db.vixFlixDao().getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID))
        assertNotNull(deviceB.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID))

        // User moves Breaking Bad -> HOLD and Fight Club -> DROPPED directly on Simkl
        // (intentionally leaving stale paused playback entries in /sync/playback/* on server!)
        val ts = formatIso(virtualClockMs.addAndGet(15_000L))
        server.directEditShowOnSimkl(
            accountToken = "tok_account_alpha",
            simklId = 1001L,
            tmdbId = 1396,
            title = "Breaking Bad",
            year = 2008,
            status = "hold",
            watchedEpisodes = listOf(1 to 1),
            nextToWatch = "S01E02",
            timestampIso = ts
        )
        server.directEditMovieOnSimkl(
            accountToken = "tok_account_alpha",
            simklId = 2001L,
            tmdbId = 550,
            title = "Fight Club",
            year = 1999,
            status = "dropped",
            timestampIso = ts
        )

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        for (dev in listOf(deviceA, deviceB)) {
            assertNull(
                "${dev.name}: Hold series MUST NOT appear in Continue Watching",
                dev.db.vixFlixDao().getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID)
            )
            assertNull(
                "${dev.name}: Dropped movie MUST NOT appear in Continue Watching",
                dev.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)
            )
            val showLib = dev.db.vixFlixDao().getSimklLibraryByTmdb(DEFAULT_PRIMARY_PROFILE_ID, 1396, "tv").firstOrNull()
            val movieLib = dev.db.vixFlixDao().getSimklLibraryByTmdb(DEFAULT_PRIMARY_PROFILE_ID, 550, "movie").firstOrNull()
            assertEquals("hold", showLib?.status)
            assertEquals("dropped", movieLib?.status)
        }
    }

    // -------------------------------------------------------------------------
    // SCENARIO 7: Plan to Watch (aggiunta su A -> visibile su B -> rimozione su B -> rimossa su A)
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 07 plan to watch add on Device A and remove on Device B syncs bidirectionally`() = runBlocking {
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        val inception = inceptionMovie()
        val addBefore = server.requestLog.count { it.startsWith("POST /sync/add-to-list") }
        deviceA.manager.enqueueAddToList(DEFAULT_PRIMARY_PROFILE_ID, inception, "plantowatch")
        waitUntilWriteSent(deviceA, "POST /sync/add-to-list", addBefore)

        virtualClockMs.addAndGet(5_000L)
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))

        val ptwOnB = deviceB.db.vixFlixDao().getSimklLibraryByTmdb(DEFAULT_PRIMARY_PROFILE_ID, 27205, "movie").firstOrNull()
        assertNotNull("Device B must see Inception in Plan to Watch", ptwOnB)
        assertEquals(SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH, ptwOnB!!.status)
        assertNull("Plan to Watch must never enter Continue Watching", deviceB.db.vixFlixDao().getWatchProgress("movie_27205", DEFAULT_PRIMARY_PROFILE_ID))

        // Device B removes Inception from Plan to Watch
        virtualClockMs.addAndGet(5_000L)
        val removeBefore = server.requestLog.count { it.startsWith("POST /sync/history/remove") }
        deviceB.manager.enqueueRemoveFromPlanToWatch(DEFAULT_PRIMARY_PROFILE_ID, inception)
        waitUntilWriteSent(deviceB, "POST /sync/history/remove", removeBefore)

        // Device A syncs and removes Inception from its local Simkl library
        virtualClockMs.addAndGet(5_000L)
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        assertTrue(
            "Device A must prune Inception after Device B removed it from Plan to Watch",
            deviceA.db.vixFlixDao().getSimklLibraryByTmdb(DEFAULT_PRIMARY_PROFILE_ID, 27205, "movie").isEmpty()
        )
    }

    // -------------------------------------------------------------------------
    // SCENARIO 8: Conflitto dispositivo offline vs modifiche su Simkl -> Simkl prevale sempre
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 08 offline conflict on Device B always defers to authoritative Simkl state`() = runBlocking {
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        val movie = fightClubMovie()
        val durationMs = 120L * 60_000L

        // Device A advances Fight Club on Simkl to 80%
        val pausesBefore = server.requestLog.count { it.startsWith("POST /scrobble/pause") }
        deviceA.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = movie,
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = (durationMs * 0.80).toLong(),
            durationMs = durationMs
        )
        waitUntilWriteSent(deviceA, "POST /scrobble/pause", pausesBefore)

        // Simulate Device B being offline (network failure) and pausing locally at 35% with a NEWER local timestamp
        virtualClockMs.addAndGet(30_000L)
        server.simulateOffline.set(true)
        deviceB.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = movie,
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = (durationMs * 0.35).toLong(),
            durationMs = durationMs
        )
        delay(120L)
        assertEquals(
            "Device B must keep offline write queued while offline",
            1,
            deviceB.db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).size
        )

        // Device B reconnects and syncs: Simkl (80%) MUST prevail and Device B's 35% write MUST be discarded!
        server.simulateOffline.set(false)
        val scrobbleCallsBefore = server.requestLog.count { it.startsWith("POST /scrobble/pause") }
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        val scrobbleCallsAfter = server.requestLog.count { it.startsWith("POST /scrobble/pause") }

        assertEquals(
            "Conflicting offline write MUST NOT be pushed to overwrite Simkl",
            scrobbleCallsBefore,
            scrobbleCallsAfter
        )
        assertTrue(
            "Conflicting offline write must be removed from pending queue",
            deviceB.db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).isEmpty()
        )
        val progressB = deviceB.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(progressB)
        assertEquals(
            "Device B must converge to Simkl's authoritative 80%",
            (durationMs * 0.80).toLong(),
            progressB!!.positionMs
        )
        val hasConflictDiagnostic = deviceB.manager.diagnosticLog.value.any {
            it.outcome == "CONFLICT_SIMKL_AUTHORITATIVE_DISCARDED"
        }
        assertTrue("Conflict discard must be traced in diagnostic events", hasConflictDiagnostic)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 9: Protezione sessione attiva su A + fast-path pre-playback su B
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 09 active playback is protected on Device A and fast path pre-playback syncs Device B quickly`() = runBlocking {
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        val movie = fightClubMovie()
        val durationMs = 120L * 60_000L

        // Put Fight Club locally on Device A at 25% and mark as actively playing
        deviceA.db.vixFlixDao().upsertWatchProgress(
            WatchProgressEntity(
                id = "movie_550",
                tmdbId = 550,
                mediaType = "movie",
                title = "Fight Club",
                overview = "Test movie",
                posterPath = "/fc.jpg",
                backdropPath = "/fc_bg.jpg",
                voteAverage = 8.4,
                releaseYear = "1999",
                positionMs = (durationMs * 0.25).toLong(),
                durationMs = durationMs,
                updatedAt = virtualClockMs.get(),
                profileId = DEFAULT_PRIMARY_PROFILE_ID
            )
        )
        deviceA.manager.setActivePlaybackTarget(DEFAULT_PRIMARY_PROFILE_ID, "movie", 550)

        // Meanwhile Device B pushes 72% to Simkl
        val pausesBefore = server.requestLog.count { it.startsWith("POST /scrobble/pause") }
        deviceB.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = movie,
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = (durationMs * 0.72).toLong(),
            durationMs = durationMs
        )
        waitUntilWriteSent(deviceB, "POST /scrobble/pause", pausesBefore)

        // Device A runs sync while actively playing -> local active playback position is NOT clobbered
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.PERIODIC))
        assertEquals(
            (durationMs * 0.25).toLong(),
            deviceA.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)?.positionMs
        )

        // Now clear active playback on Device A and call fast-path syncBeforePlaybackIfConnected!
        deviceA.manager.clearActivePlaybackTarget()
        val allItemsBefore = server.requestLog.count { it.startsWith("GET /sync/all-items") }
        deviceA.manager.syncBeforePlaybackIfConnected(DEFAULT_PRIMARY_PROFILE_ID, timeoutMs = 3_000L)
        val allItemsAfter = server.requestLog.count { it.startsWith("GET /sync/all-items") }

        assertEquals(
            "Fast-path pre-playback sync must NOT call heavy /sync/all-items",
            allItemsBefore,
            allItemsAfter
        )
        assertEquals(
            "Fast-path pre-playback sync must immediately recover 72% from Simkl",
            (durationMs * 0.72).toLong(),
            deviceA.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)?.positionMs
        )
    }

    // -------------------------------------------------------------------------
    // SCENARIO 10: Isolamento rigoroso tra profili Vixflix e account Simkl distinti
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 10 multi-profile isolation prevents cross-account leakage`() = runBlocking {
        val profile2 = "profile_beta"
        deviceA.db.vixFlixDao().upsertProfile(
            UserProfileEntity(
                id = profile2,
                name = "Beta",
                avatarId = "av_beta",
                accentColorHex = 0xFF3366FFL
            )
        )
        connectProfile(deviceA, profile2, "tok_account_beta")

        // Account Alpha watches Fight Club; Account Beta watches Inception
        server.directEditMovieOnSimkl(
            accountToken = "tok_account_alpha",
            simklId = 2001L,
            tmdbId = 550,
            title = "Fight Club",
            year = 1999,
            status = "completed",
            timestampIso = formatIso(virtualClockMs.addAndGet(5_000L))
        )
        server.directEditMovieOnSimkl(
            accountToken = "tok_account_beta",
            simklId = 2002L,
            tmdbId = 27205,
            title = "Inception",
            year = 2010,
            status = "plantowatch",
            timestampIso = formatIso(virtualClockMs.addAndGet(5_000L))
        )

        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceA.manager.syncProfileNow(profile2, SimklSyncTrigger.POST_AUTH))

        assertNotNull(deviceA.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(550, "movie"), DEFAULT_PRIMARY_PROFILE_ID))
        assertNull(deviceA.db.vixFlixDao().getCompletedMedia(PlayerSegmentKeys.completedMediaKey(550, "movie"), profile2))
        assertTrue(deviceA.db.vixFlixDao().getSimklLibraryByTmdb(DEFAULT_PRIMARY_PROFILE_ID, 27205, "movie").isEmpty())
        assertEquals(
            SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH,
            deviceA.db.vixFlixDao().getSimklLibraryByTmdb(profile2, 27205, "movie").firstOrNull()?.status
        )

        // Disconnecting Profile 1 does not affect Profile 2's credentials or library
        deviceA.manager.unlinkAccount(DEFAULT_PRIMARY_PROFILE_ID)
        assertFalse(deviceA.tokenStore.hasTokens(DEFAULT_PRIMARY_PROFILE_ID))
        assertTrue(deviceA.tokenStore.hasTokens(profile2))
        assertEquals(1, deviceA.db.vixFlixDao().getSimklLibraryForProfile(profile2).size)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 11: Token refresh 401 trasparente e gestione errore HTTP 403
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 11 transparent 401 token refresh succeeds and HTTP 403 surfaces permission error without silent drop`() = runBlocking {
        // Part A: expired access token receives 401 once, refreshes transparently, and sync succeeds
        server.rejectTokenOnce.set(true)
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(
            "OAuth token refresh endpoint must have been invoked",
            server.requestLog.any { it.startsWith("POST /oauth2/token") || it.startsWith("POST /oauth/token") }
        )

        // Part B: HTTP 403 on scrobble MUST NOT silently drop the queued event and must set ERROR state
        server.forceForbiddenOnWrites.set(true)
        deviceB.manager.enqueueScrobblePause(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = fightClubMovie(),
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = 30L * 60_000L,
            durationMs = 120L * 60_000L
        )
        delay(200L)
        assertEquals(
            "HTTP 403 must keep entry in queue for retry/re-auth instead of silently dropping",
            1,
            deviceB.db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).size
        )
        val metaB = deviceB.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)
        assertEquals(SimklConnectionState.ERROR.name, metaB?.connectionState)
        assertTrue(metaB?.lastErrorMessage?.contains("403") == true)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 12: Crash checkpoint durante riproduzione -> recupero al riavvio e sync su Device B
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 12 crash checkpoint persists progress and propagates to Device B after relaunch`() = runBlocking {
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))

        val movie = fightClubMovie()
        val durationMs = 120L * 60_000L
        val checkpointPosMs = (durationMs * 0.55).toLong() // 55%

        // Device A records periodic crash checkpoint while playing (then simulates abrupt kill without scrobbleStop)
        deviceA.manager.recordPlaybackCrashCheckpoint(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            item = movie,
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = checkpointPosMs,
            durationMs = durationMs
        )
        delay(120L)
        assertEquals(
            "Checkpoint must be persisted in pending scrobble queue without spamming network immediately",
            1,
            deviceA.db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).size
        )

        // Simulated relaunch on Device A flushes the checkpoint to Simkl
        assertTrue(deviceA.manager.flushPendingQueueNow(DEFAULT_PRIMARY_PROFILE_ID))
        assertTrue(deviceA.db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).isEmpty())

        // Device B syncs and recovers the exact 55% checkpoint position
        virtualClockMs.addAndGet(5_000L)
        assertTrue(deviceB.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        val resumedB = deviceB.db.vixFlixDao().getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(resumedB)
        assertEquals(checkpointPosMs, resumedB!!.positionMs)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 13 (Resilienza): Payload HTTP 200 troncato/corrotto NON distrugge snapshot locale
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 13 truncated HTTP 200 JSON fails cleanly without wiping local snapshot or advancing watermarks`() = runBlocking {
        server.directEditMovieOnSimkl(
            accountToken = "tok_account_alpha",
            simklId = 2001L,
            tmdbId = 550,
            title = "Fight Club",
            year = 1999,
            status = "completed",
            timestampIso = formatIso(virtualClockMs.addAndGet(5_000L))
        )
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        val watermarkBefore = deviceA.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)?.lastActivitiesAll
        assertEquals(1, deviceA.db.vixFlixDao().getSimklLibraryForProfile(DEFAULT_PRIMARY_PROFILE_ID).size)

        // Server returns truncated JSON on next sync with HTTP 200
        server.returnTruncatedAllItemsJson.set(true)
        server.bumpActivitiesTimestamp("tok_account_alpha", formatIso(virtualClockMs.addAndGet(10_000L)))

        val synced = deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL)
        assertFalse("Sync MUST fail when Simkl returns truncated JSON", synced)
        assertEquals(
            "Existing local Simkl library must stay intact on fatal parse error",
            1,
            deviceA.db.vixFlixDao().getSimklLibraryForProfile(DEFAULT_PRIMARY_PROFILE_ID).size
        )
        assertEquals(
            "Watermarks must NOT advance when payload is corrupted",
            watermarkBefore,
            deviceA.db.vixFlixDao().getSimklProfileMeta(DEFAULT_PRIMARY_PROFILE_ID)?.lastActivitiesAll
        )
    }

    // -------------------------------------------------------------------------
    // SCENARIO 14: Riconciliazione completa periodica (pruning) e serie conclusa senza phantom episode
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 14 periodic full reconciliation prunes missing items and concluded series cleans Continue Watching`() = runBlocking {
        val shortReconcileDevice = createDevice("ShortReconcile", fullReconcileIntervalMs = 60_000L)
        try {
            connectProfile(shortReconcileDevice, DEFAULT_PRIMARY_PROFILE_ID, "tok_account_alpha")

            // Add a 2-episode miniseries (TMDB 9901) with S01E01 watched + Fight Club in PTW
            val t1 = formatIso(virtualClockMs.addAndGet(5_000L))
            server.directEditShowOnSimkl(
                accountToken = "tok_account_alpha",
                simklId = 3001L,
                tmdbId = 9901,
                title = "Mini Series",
                year = 2024,
                status = "watching",
                watchedEpisodes = listOf(1 to 1),
                nextToWatch = null,
                timestampIso = t1
            )
            server.directEditMovieOnSimkl(
                accountToken = "tok_account_alpha",
                simklId = 2001L,
                tmdbId = 550,
                title = "Fight Club",
                year = 1999,
                status = "plantowatch",
                timestampIso = t1
            )
            assertTrue(shortReconcileDevice.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
            // S01E02 is inferred from TMDB season 1 (which has 2 episodes)
            assertEquals(2, shortReconcileDevice.db.vixFlixDao().getWatchProgress("tv_9901", DEFAULT_PRIMARY_PROFILE_ID)?.episodeNumber)
            assertEquals(2, shortReconcileDevice.db.vixFlixDao().getSimklLibraryForProfile(DEFAULT_PRIMARY_PROFILE_ID).size)

            // Now mark S01E02 also watched (no S01E03 exists in TMDB!) AND silently remove Fight Club on server WITHOUT bumping removed_from_list
            val t2 = formatIso(virtualClockMs.addAndGet(120_000L)) // exceeds 60s fullReconcileIntervalMs
            server.directEditShowOnSimkl(
                accountToken = "tok_account_alpha",
                simklId = 3001L,
                tmdbId = 9901,
                title = "Mini Series",
                year = 2024,
                status = "watching",
                watchedEpisodes = listOf(1 to 1, 1 to 2),
                nextToWatch = null,
                timestampIso = t2
            )
            server.silentlyDropMovieWithoutRemovedFlag("tok_account_alpha", 2001L)

            assertTrue(shortReconcileDevice.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.PERIODIC))

            // 1) Concluded series MUST NOT fabricate S01E03 and MUST clean stale Continue Watching card
            assertNull(
                "Concluded series with all TMDB episodes watched must NOT fabricate S01E03",
                shortReconcileDevice.db.vixFlixDao().getWatchProgress("tv_9901", DEFAULT_PRIMARY_PROFILE_ID)
            )
            // 2) Silently dropped movie must be pruned by periodic full reconciliation
            assertTrue(
                "Periodic full reconcile must prune items missing from Simkl snapshot",
                shortReconcileDevice.db.vixFlixDao().getSimklLibraryByTmdb(DEFAULT_PRIMARY_PROFILE_ID, 550, "movie").isEmpty()
            )
        } finally {
            shortReconcileDevice.close()
        }
    }

    // -------------------------------------------------------------------------
    // SCENARIO 15: Anime movie vs Anime series, Season 0 Episode 0 & Duration Enrichment Recompute
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 15 anime movie classification and duration enrichment exact resume recomputation`() = runBlocking {
        // Part A: Anime movie (`anime_type = "movie"`) maps to tmdbMediaType = "movie"
        val animePayload = """
            {
              "anime": [
                {
                  "status": "completed",
                  "anime_type": "movie",
                  "last_watched_at": "2026-05-01T12:00:00.123456Z",
                  "show": {
                    "title": "Spirited Away",
                    "year": 2001,
                    "ids": {"simkl": 4001, "tmdb": 129}
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = SimklLibraryJsonParser.parseAllItemsPayload(animePayload, defaultCategory = "anime")
        assertFalse(parsed.isFatalPayloadError)
        assertEquals(1, parsed.anime.size)
        assertEquals("movie", parsed.anime.first().tmdbMediaType)

        // Verify hardened ISO parser handles 6-digit microseconds and timezone offsets
        val parsedMicros = deviceA.manager.parseIsoUtcMillis("2026-05-01T12:00:00.123456Z")
        val parsedMillis = deviceA.manager.parseIsoUtcMillis("2026-05-01T12:00:00.123Z")
        assertTrue(parsedMicros > 0L)
        assertEquals(parsedMillis, parsedMicros)

        // Part B: Duration enrichment recomputes exact resume position from Simkl baseline percentage
        // Remote movie TMDB 77777 has runtime=0 on TMDB during initial sync -> synced at 50% with placeholder 30_000ms
        server.directSetPlaybackMovie(
            accountToken = "tok_account_alpha",
            playbackId = 8801L,
            tmdbId = 77777,
            title = "Unknown Runtime Movie",
            year = 2025,
            progress = 50.0,
            pausedAt = formatIso(virtualClockMs.addAndGet(5_000L))
        )
        assertTrue(deviceA.manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.POST_AUTH))
        val beforeDur = deviceA.db.vixFlixDao().getWatchProgress("movie_77777", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(beforeDur)
        assertEquals(0L, beforeDur!!.durationMs)
        assertEquals(30_000L, beforeDur.positionMs)

        // Player / TMDB resolves real duration = 100 minutes (6_000_000ms) -> position MUST recompute to exact 50% (3_000_000ms)!
        deviceA.repo.updateWatchProgressDuration(
            id = "movie_77777",
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            seasonNumber = null,
            episodeNumber = null,
            resolvedDurationMs = 6_000_000L
        )
        val afterDur = deviceA.db.vixFlixDao().getWatchProgress("movie_77777", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(afterDur)
        assertEquals(6_000_000L, afterDur!!.durationMs)
        assertEquals(3_000_000L, afterDur.positionMs)
    }

    private fun formatIso(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(epochMs))
    }

    // =========================================================================
    // Stateful Multi-Device Simkl + TMDB HTTP Server
    // =========================================================================
    private class StatefulMultiDeviceSimklServer {
        private var serverSocket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()
        private val running = AtomicBoolean(false)

        val requestLog = CopyOnWriteArrayList<String>()
        val simulateOffline = AtomicBoolean(false)
        val rejectTokenOnce = AtomicBoolean(false)
        val forceForbiddenOnWrites = AtomicBoolean(false)
        val returnTruncatedAllItemsJson = AtomicBoolean(false)

        private val nextPlaybackId = AtomicLong(5000L)
        private val nextSimklId = AtomicLong(9000L)

        data class MovieState(
            val simklId: Long,
            val tmdbId: Int,
            val title: String,
            val year: Int,
            var status: String,
            var lastWatchedAt: String
        )

        data class ShowState(
            val simklId: Long,
            val tmdbId: Int,
            val title: String,
            val year: Int,
            var status: String,
            val watchedEpisodes: MutableSet<Pair<Int, Int>>,
            var nextToWatch: String?,
            var lastWatchedAt: String
        )

        data class PlaybackEntry(
            val id: Long,
            val type: String, // "movie" | "episode"
            val tmdbId: Int,
            val title: String,
            val year: Int,
            val season: Int? = null,
            val episode: Int? = null,
            var progress: Double,
            var pausedAt: String
        )

        class AccountData {
            val movies = LinkedHashMap<Int, MovieState>() // by tmdbId
            val shows = LinkedHashMap<Int, ShowState>()   // by tmdbId
            val playbackMovies = LinkedHashMap<Int, PlaybackEntry>() // by tmdbId
            val playbackEpisodes = LinkedHashMap<String, PlaybackEntry>() // "${tmdbId}_${s}_${e}"
            var allTs = "2026-01-01T00:00:00Z"
            var moviesAllTs = "2026-01-01T00:00:00Z"
            var moviesPlaybackTs = "2026-01-01T00:00:00Z"
            var moviesCompletedTs = "2026-01-01T00:00:00Z"
            var moviesRemovedTs: String? = null
            var showsAllTs = "2026-01-01T00:00:00Z"
            var showsPlaybackTs = "2026-01-01T00:00:00Z"
            var showsRemovedTs: String? = null
            var animeAllTs = "2026-01-01T00:00:00Z"
            var animePlaybackTs = "2026-01-01T00:00:00Z"
            var animeRemovedTs: String? = null
        }

        private val accounts = HashMap<String, AccountData>()
        private val lock = Any()
        private var currentServerTimeSec = 1_760_000_000L

        private fun nextIso(): String {
            currentServerTimeSec += 2L
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return fmt.format(Date(currentServerTimeSec * 1000L))
        }

        private fun accountForToken(token: String): AccountData = synchronized(lock) {
            val normalized = if (token.startsWith("refreshed_")) token.removePrefix("refreshed_") else token
            accounts.getOrPut(normalized) { AccountData() }
        }

        fun directEditMovieOnSimkl(
            accountToken: String,
            simklId: Long,
            tmdbId: Int,
            title: String,
            year: Int,
            status: String,
            timestampIso: String
        ) = synchronized(lock) {
            val acc = accountForToken(accountToken)
            acc.movies[tmdbId] = MovieState(simklId, tmdbId, title, year, status, timestampIso)
            acc.allTs = timestampIso
            acc.moviesAllTs = timestampIso
            if (status == "completed") acc.moviesCompletedTs = timestampIso
        }

        fun silentlyDropMovieWithoutRemovedFlag(accountToken: String, simklId: Long) = synchronized(lock) {
            val acc = accountForToken(accountToken)
            val key = acc.movies.entries.firstOrNull { it.value.simklId == simklId }?.key ?: return@synchronized
            acc.movies.remove(key)
        }

        fun directEditShowOnSimkl(
            accountToken: String,
            simklId: Long,
            tmdbId: Int,
            title: String,
            year: Int,
            status: String,
            watchedEpisodes: List<Pair<Int, Int>>,
            nextToWatch: String?,
            timestampIso: String
        ) = synchronized(lock) {
            val acc = accountForToken(accountToken)
            acc.shows[tmdbId] = ShowState(
                simklId = simklId,
                tmdbId = tmdbId,
                title = title,
                year = year,
                status = status,
                watchedEpisodes = watchedEpisodes.toMutableSet(),
                nextToWatch = nextToWatch,
                lastWatchedAt = timestampIso
            )
            acc.allTs = timestampIso
            acc.showsAllTs = timestampIso
        }

        fun directSetPlaybackMovie(
            accountToken: String,
            playbackId: Long,
            tmdbId: Int,
            title: String,
            year: Int,
            progress: Double,
            pausedAt: String
        ) = synchronized(lock) {
            val acc = accountForToken(accountToken)
            acc.playbackMovies[tmdbId] = PlaybackEntry(
                id = playbackId,
                type = "movie",
                tmdbId = tmdbId,
                title = title,
                year = year,
                progress = progress,
                pausedAt = pausedAt
            )
            acc.allTs = pausedAt
            acc.moviesPlaybackTs = pausedAt
        }

        fun bumpActivitiesTimestamp(accountToken: String, iso: String) = synchronized(lock) {
            val acc = accountForToken(accountToken)
            acc.allTs = iso
            acc.moviesAllTs = iso
        }

        val baseUrl: String
            get() = "http://127.0.0.1:${serverSocket!!.localPort}/"

        fun start() {
            val ss = ServerSocket(0)
            serverSocket = ss
            running.set(true)
            executor.execute {
                while (running.get()) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
                    executor.execute { handleClient(client) }
                }
            }
        }

        fun stop() {
            running.set(false)
            runCatching { serverSocket?.close() }
            executor.shutdownNow()
        }

        private fun handleClient(socket: Socket) {
            socket.use { sock ->
                if (simulateOffline.get()) {
                    // Drop connection immediately to simulate offline IOException
                    return
                }
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val rawPath = parts.getOrNull(1) ?: "/"
                val pathOnly = rawPath.substringBefore("?")
                val query = rawPath.substringAfter("?", "")

                var contentLength = 0
                var authHeader: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val lower = line.lowercase(Locale.US)
                    if (lower.startsWith("content-length:")) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    } else if (lower.startsWith("authorization:")) {
                        authHeader = line.substringAfter(":").trim()
                    }
                }
                val bodyChars = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = reader.read(bodyChars, readTotal, contentLength - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                val body = String(bodyChars, 0, readTotal)
                requestLog.add("$method $rawPath")

                val bearerToken = authHeader?.removePrefix("Bearer ")?.trim().orEmpty()
                val (statusCode, responseJson) = routeRequest(method, pathOnly, query, bearerToken, body)

                val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                val bytes = responseJson.toByteArray(Charsets.UTF_8)
                writer.write("HTTP/1.1 $statusCode OK\r\n")
                writer.write("Content-Type: application/json; charset=utf-8\r\n")
                writer.write("Content-Length: ${bytes.size}\r\n")
                writer.write("Connection: close\r\n\r\n")
                writer.write(responseJson)
                writer.flush()
            }
        }

        private fun routeRequest(
            method: String,
            path: String,
            query: String,
            token: String,
            body: String
        ): Pair<Int, String> {
            // TMDB endpoints (no auth needed)
            if (path == "/movie/550") {
                return 200 to """{"id":550,"title":"Fight Club","overview":"Test movie","runtime":120,"release_date":"1999-10-15","vote_average":8.4,"poster_path":"/fc.jpg","backdrop_path":"/fc_bg.jpg"}"""
            }
            if (path == "/movie/27205") {
                return 200 to """{"id":27205,"title":"Inception","overview":"Dream heist","runtime":148,"release_date":"2010-07-16","vote_average":8.8,"poster_path":"/inc.jpg"}"""
            }
            if (path == "/movie/77777") {
                return 200 to """{"id":77777,"title":"Unknown Runtime Movie","overview":"No runtime","runtime":0,"release_date":"2025-01-01","vote_average":7.0}"""
            }
            if (path == "/tv/1396") {
                return 200 to """{"id":1396,"name":"Breaking Bad","overview":"Test series","first_air_date":"2008-01-20","vote_average":8.9,"seasons":[{"season_number":1,"episode_count":5,"name":"Season 1"}]}"""
            }
            if (path == "/tv/1396/season/1") {
                return 200 to """{"season_number":1,"episodes":[
                    {"episode_number":1,"season_number":1,"name":"Pilot","runtime":50,"air_date":"2008-01-20"},
                    {"episode_number":2,"season_number":1,"name":"Cat's in the Bag","runtime":50,"air_date":"2008-01-27"},
                    {"episode_number":3,"season_number":1,"name":"And the Bag's in the River","runtime":50,"air_date":"2008-02-10"},
                    {"episode_number":4,"season_number":1,"name":"Cancer Man","runtime":50,"air_date":"2008-02-17"},
                    {"episode_number":5,"season_number":1,"name":"Gray Matter","runtime":50,"air_date":"2008-02-24"}
                ]}"""
            }
            if (path == "/tv/9901") {
                return 200 to """{"id":9901,"name":"Mini Series","overview":"2-ep miniseries","first_air_date":"2024-01-01","vote_average":8.0,"seasons":[{"season_number":1,"episode_count":2,"name":"Season 1"}]}"""
            }
            if (path == "/tv/9901/season/1") {
                return 200 to """{"season_number":1,"episodes":[
                    {"episode_number":1,"season_number":1,"name":"Part 1","runtime":45,"air_date":"2024-01-01"},
                    {"episode_number":2,"season_number":1,"name":"Part 2","runtime":45,"air_date":"2024-01-08"}
                ]}"""
            }
            if (path.startsWith("/tv/") && path.contains("/season/")) {
                return 404 to """{"status_message":"Not found"}"""
            }

            // OAuth token refresh
            if (method == "POST" && (path == "/oauth2/token" || path == "/oauth/token")) {
                val refreshed = "refreshed_tok_account_alpha"
                return 200 to """{"access_token":"$refreshed","refresh_token":"ref_new","expires_in":86400,"token_type":"Bearer"}"""
            }

            // Simulate single 401 before refresh
            if (token.isNotBlank() && !token.startsWith("refreshed_") && rejectTokenOnce.compareAndSet(true, false)) {
                return 401 to """{"error":"invalid_token"}"""
            }

            val acc = accountForToken(token.ifBlank { "tok_account_alpha" })

            if ((method == "GET" || method == "POST") && path == "/sync/activities") {
                return synchronized(lock) {
                    val json = JSONObject().apply {
                        put("all", acc.allTs)
                        put("movies", JSONObject().apply {
                            put("all", acc.moviesAllTs)
                            put("playback", acc.moviesPlaybackTs)
                            put("completed", acc.moviesCompletedTs)
                            if (acc.moviesRemovedTs != null) put("removed_from_list", acc.moviesRemovedTs)
                        })
                        put("tv_shows", JSONObject().apply {
                            put("all", acc.showsAllTs)
                            put("playback", acc.showsPlaybackTs)
                            if (acc.showsRemovedTs != null) put("removed_from_list", acc.showsRemovedTs)
                        })
                        put("anime", JSONObject().apply {
                            put("all", acc.animeAllTs)
                            put("playback", acc.animePlaybackTs)
                            if (acc.animeRemovedTs != null) put("removed_from_list", acc.animeRemovedTs)
                        })
                    }
                    200 to json.toString()
                }
            }

            if (method == "GET" && path.startsWith("/sync/all-items")) {
                if (returnTruncatedAllItemsJson.get()) {
                    return 200 to """{"shows":[{"status":"watching","show":{"title":"Truncated"""
                }
                val isIdsOnly = query.contains("extended=simkl_ids_only")
                return synchronized(lock) {
                    when {
                        path == "/sync/all-items/movies" -> 200 to buildMoviesPayload(acc, isIdsOnly)
                        path == "/sync/all-items/shows" -> 200 to buildShowsPayload(acc, isIdsOnly)
                        path == "/sync/all-items/anime" -> 200 to """{"anime":[]}"""
                        else -> {
                            val root = JSONObject()
                            root.put("movies", JSONObject(buildMoviesPayload(acc, false)).getJSONArray("movies"))
                            root.put("shows", JSONObject(buildShowsPayload(acc, false)).getJSONArray("shows"))
                            root.put("anime", JSONArray())
                            200 to root.toString()
                        }
                    }
                }
            }

            if (method == "GET" && path == "/sync/playback/movies") {
                return synchronized(lock) {
                    val arr = JSONArray()
                    for (entry in acc.playbackMovies.values) {
                        arr.put(JSONObject().apply {
                            put("id", entry.id)
                            put("progress", entry.progress)
                            put("paused_at", entry.pausedAt)
                            put("type", "movie")
                            put("movie", JSONObject().apply {
                                put("title", entry.title)
                                put("year", entry.year)
                                put("ids", JSONObject().put("tmdb", entry.tmdbId))
                            })
                        })
                    }
                    200 to arr.toString()
                }
            }

            if (method == "GET" && path == "/sync/playback/episodes") {
                return synchronized(lock) {
                    val arr = JSONArray()
                    for (entry in acc.playbackEpisodes.values) {
                        arr.put(JSONObject().apply {
                            put("id", entry.id)
                            put("progress", entry.progress)
                            put("paused_at", entry.pausedAt)
                            put("type", "episode")
                            put("show", JSONObject().apply {
                                put("title", entry.title)
                                put("year", entry.year)
                                put("ids", JSONObject().put("tmdb", entry.tmdbId))
                            })
                            put("episode", JSONObject().apply {
                                put("season", entry.season ?: 1)
                                put("episode", entry.episode ?: 1)
                                put("number", entry.episode ?: 1)
                            })
                        })
                    }
                    200 to arr.toString()
                }
            }

            if (method == "POST" && path.startsWith("/scrobble/")) {
                if (forceForbiddenOnWrites.get()) {
                    return 403 to """{"error":"forbidden"}"""
                }
                val action = path.removePrefix("/scrobble/")
                val req = JSONObject(body)
                val progress = req.optDouble("progress", 0.0)
                val nowIso = nextIso()
                return synchronized(lock) {
                    val movieObj = req.optJSONObject("movie")
                    val showObj = req.optJSONObject("show")
                    val epObj = req.optJSONObject("episode")
                    if (movieObj != null) {
                        val tmdbId = movieObj.getJSONObject("ids").getInt("tmdb")
                        val title = movieObj.optString("title", "Movie $tmdbId")
                        val year = movieObj.optInt("year", 2000)
                        if (action == "stop" && progress >= 90.0) {
                            acc.playbackMovies.remove(tmdbId)
                            val simklId = acc.movies[tmdbId]?.simklId ?: nextSimklId.incrementAndGet()
                            acc.movies[tmdbId] = MovieState(simklId, tmdbId, title, year, "completed", nowIso)
                            acc.allTs = nowIso
                            acc.moviesAllTs = nowIso
                            acc.moviesCompletedTs = nowIso
                            acc.moviesPlaybackTs = nowIso
                            200 to """{"id":$simklId,"action":"scrobble","progress":$progress}"""
                        } else {
                            val pid = acc.playbackMovies[tmdbId]?.id ?: nextPlaybackId.incrementAndGet()
                            acc.playbackMovies[tmdbId] = PlaybackEntry(pid, "movie", tmdbId, title, year, progress = progress, pausedAt = nowIso)
                            acc.allTs = nowIso
                            acc.moviesPlaybackTs = nowIso
                            200 to """{"id":$pid,"action":"$action","progress":$progress}"""
                        }
                    } else if (showObj != null && epObj != null) {
                        val tmdbId = showObj.getJSONObject("ids").getInt("tmdb")
                        val title = showObj.optString("title", "Show $tmdbId")
                        val year = showObj.optInt("year", 2000)
                        val season = epObj.optInt("season", 1)
                        val epNum = epObj.optInt("number", epObj.optInt("episode", 1))
                        val key = "${tmdbId}_${season}_${epNum}"
                        if (action == "stop" && progress >= 90.0) {
                            acc.playbackEpisodes.remove(key)
                            val existingShow = acc.shows[tmdbId] ?: ShowState(
                                simklId = nextSimklId.incrementAndGet(),
                                tmdbId = tmdbId,
                                title = title,
                                year = year,
                                status = "watching",
                                watchedEpisodes = LinkedHashSet(),
                                nextToWatch = null,
                                lastWatchedAt = nowIso
                            )
                            existingShow.watchedEpisodes.add(season to epNum)
                            existingShow.status = "watching"
                            existingShow.nextToWatch = "S${season.toString().padStart(2, '0')}E${(epNum + 1).toString().padStart(2, '0')}"
                            existingShow.lastWatchedAt = nowIso
                            acc.shows[tmdbId] = existingShow
                            acc.allTs = nowIso
                            acc.showsAllTs = nowIso
                            acc.showsPlaybackTs = nowIso
                            200 to """{"id":${existingShow.simklId},"action":"scrobble","progress":$progress}"""
                        } else {
                            val pid = acc.playbackEpisodes[key]?.id ?: nextPlaybackId.incrementAndGet()
                            acc.playbackEpisodes[key] = PlaybackEntry(pid, "episode", tmdbId, title, year, season, epNum, progress, nowIso)
                            acc.allTs = nowIso
                            acc.showsPlaybackTs = nowIso
                            200 to """{"id":$pid,"action":"$action","progress":$progress}"""
                        }
                    } else {
                        400 to """{"error":"bad_payload"}"""
                    }
                }
            }

            if (method == "POST" && path == "/sync/add-to-list") {
                if (forceForbiddenOnWrites.get()) return 403 to """{"error":"forbidden"}"""
                val req = JSONObject(body)
                val nowIso = nextIso()
                return synchronized(lock) {
                    val moviesArr = req.optJSONArray("movies")
                    if (moviesArr != null) {
                        for (i in 0 until moviesArr.length()) {
                            val m = moviesArr.getJSONObject(i)
                            val tmdbId = m.getJSONObject("ids").getInt("tmdb")
                            val simklId = acc.movies[tmdbId]?.simklId ?: nextSimklId.incrementAndGet()
                            acc.movies[tmdbId] = MovieState(
                                simklId = simklId,
                                tmdbId = tmdbId,
                                title = m.optString("title", "Movie $tmdbId"),
                                year = m.optInt("year", 2010),
                                status = m.optString("to", "plantowatch"),
                                lastWatchedAt = nowIso
                            )
                            acc.allTs = nowIso
                            acc.moviesAllTs = nowIso
                        }
                    }
                    200 to """{"added":{"movies":${moviesArr?.length() ?: 0},"shows":0}}"""
                }
            }

            if (method == "POST" && path == "/sync/history/remove") {
                if (forceForbiddenOnWrites.get()) return 403 to """{"error":"forbidden"}"""
                val req = JSONObject(body)
                val nowIso = nextIso()
                return synchronized(lock) {
                    val moviesArr = req.optJSONArray("movies")
                    if (moviesArr != null) {
                        for (i in 0 until moviesArr.length()) {
                            val m = moviesArr.getJSONObject(i)
                            val tmdbId = m.getJSONObject("ids").getInt("tmdb")
                            acc.movies.remove(tmdbId)
                            acc.playbackMovies.remove(tmdbId)
                            acc.allTs = nowIso
                            acc.moviesAllTs = nowIso
                            acc.moviesRemovedTs = nowIso
                        }
                    }
                    200 to """{"deleted":{"movies":${moviesArr?.length() ?: 0},"shows":0,"episodes":0}}"""
                }
            }

            return 200 to "{}"
        }

        private fun buildMoviesPayload(acc: AccountData, idsOnly: Boolean): String {
            val arr = JSONArray()
            for (m in acc.movies.values) {
                if (idsOnly) {
                    arr.put(JSONObject().put("ids", JSONObject().put("simkl", m.simklId).put("tmdb", m.tmdbId)))
                } else {
                    arr.put(JSONObject().apply {
                        put("status", m.status)
                        put("last_watched_at", m.lastWatchedAt)
                        put("movie", JSONObject().apply {
                            put("title", m.title)
                            put("year", m.year)
                            put("ids", JSONObject().put("simkl", m.simklId).put("tmdb", m.tmdbId))
                        })
                    })
                }
            }
            return JSONObject().put("movies", arr).toString()
        }

        private fun buildShowsPayload(acc: AccountData, idsOnly: Boolean): String {
            val arr = JSONArray()
            for (s in acc.shows.values) {
                if (idsOnly) {
                    arr.put(JSONObject().put("ids", JSONObject().put("simkl", s.simklId).put("tmdb", s.tmdbId)))
                } else {
                    val seasonsArr = JSONArray()
                    val grouped = s.watchedEpisodes.groupBy({ it.first }, { it.second }).toSortedMap()
                    for ((seasonNum, eps) in grouped) {
                        val epsArr = JSONArray()
                        for (epNum in eps.sorted()) {
                            epsArr.put(JSONObject().apply {
                                put("number", epNum)
                                put("watched_at", s.lastWatchedAt)
                            })
                        }
                        seasonsArr.put(JSONObject().put("number", seasonNum).put("episodes", epsArr))
                    }
                    arr.put(JSONObject().apply {
                        put("status", s.status)
                        put("last_watched_at", s.lastWatchedAt)
                        put("watched_episodes_count", s.watchedEpisodes.size)
                        if (s.nextToWatch != null) put("next_to_watch", s.nextToWatch) else put("next_to_watch", JSONObject.NULL)
                        put("seasons", seasonsArr)
                        put("show", JSONObject().apply {
                            put("title", s.title)
                            put("year", s.year)
                            put("ids", JSONObject().put("simkl", s.simklId).put("tmdb", s.tmdbId))
                        })
                    })
                }
            }
            return JSONObject().put("shows", arr).toString()
        }
    }
}
