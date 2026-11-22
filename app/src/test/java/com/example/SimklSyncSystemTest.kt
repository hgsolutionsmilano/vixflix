package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.MAX_USER_PROFILES
import com.example.data.local.UserProfileUiState
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.local.simkl.SimklPlaybackBaselineEntity
import com.example.data.local.simkl.SimklProfileSyncMetaEntity
import com.example.data.local.simkl.SimklSecureTokenStore
import com.example.data.local.simkl.SimklTokenBundle
import com.example.data.local.simkl.SoftwareAesGcmSimklCipherEngine
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbApiService
import com.example.data.remote.simkl.SimklAuthFlowMode
import com.example.data.remote.simkl.SimklConfig
import com.example.data.remote.simkl.SimklConnectionState
import com.example.data.remote.simkl.SimklProfileUiState
import com.example.data.remote.simkl.SimklQrDialogState
import com.example.data.remote.simkl.SimklQrMatrix
import com.example.data.remote.simkl.SimklQrResolutionPrompt
import com.example.data.remote.simkl.SimklSyncManager
import com.example.data.remote.simkl.SimklSyncTrigger
import com.example.data.repository.VixFlixRepository
import com.example.ui.VixFlixViewModel
import com.example.ui.components.SimklQrConnectDialog
import com.example.ui.components.SimklQrGenerator
import com.example.ui.screens.ProfileSelectionScreen
import com.example.ui.screens.SettingsCategory
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.squareup.moshi.Moshi
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-notouch-dpad")
class SimklSyncSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var server: FakeSimklAndTmdbServer
    private lateinit var db: VixFlixDatabase
    private lateinit var vaultDir: File
    private lateinit var tokenStore: SimklSecureTokenStore
    private lateinit var repo: VixFlixRepository
    private lateinit var testScope: CoroutineScope
    private fun nowMs(): Long = System.currentTimeMillis()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = FakeSimklAndTmdbServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vaultDir = File(app.cacheDir, "simkl_test_vault_${System.nanoTime()}").apply { mkdirs() }
        tokenStore = SimklSecureTokenStore(vaultDir, SoftwareAesGcmSimklCipherEngine())

        repo = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = server.baseUrl,
            vixBaseUrl = server.baseUrl,
            httpCacheDir = app.cacheDir
        )
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runBlocking { repo.ensureDefaultPrimaryProfile() }
    }

    @After
    fun tearDown() {
        testScope.cancel()
        db.close()
        server.stop()
        vaultDir.deleteRecursively()
    }

    private fun buildManager(
        minPostIntervalMs: Long = 20L,
        authPollMinIntervalMs: Long = 30L,
        syncCooldownMs: Long = 0L,
        clientId: String = "test_simkl_client_id"
    ): SimklSyncManager {
        return SimklSyncManager(
            repository = repo,
            tokenStore = tokenStore,
            configProvider = {
                SimklConfig(
                    apiBaseUrl = server.baseUrl,
                    authBaseUrl = server.baseUrl.trimEnd('/'),
                    overrideClientId = clientId
                )
            },
            minPostIntervalMs = minPostIntervalMs,
            authPollMinIntervalMs = authPollMinIntervalMs,
            syncCooldownMs = syncCooldownMs,
            clockMs = { nowMs() },
            scope = testScope
        )
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(25L)
        }
        assertTrue("Timeout waiting for condition", predicate())
    }

    // 1. Device flow QR & code generation
    @Test
    fun `01 device flow requests code and builds trusted QR matrix and countdown`() = runBlocking {
        server.deviceResponseJson = """
            {
              "device_code": "dev_code_001",
              "user_code": "VIX-7742",
              "verification_uri": "${server.baseUrl.trimEnd('/')}/pin",
              "verification_uri_complete": "${server.baseUrl.trimEnd('/')}/pin/VIX-7742",
              "expires_in": 600,
              "interval": 1
            }
        """.trimIndent()
        server.tokenResponses.add(400 to """{"error":"authorization_pending"}""")

        val manager = buildManager()
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)

        waitUntil {
            manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().qrDialogState?.userCode == "VIX-7742"
        }
        val state = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first()
        assertEquals("VIX-7742", state.qrDialogState?.userCode)
        assertNotNull(state.qrDialogState?.qrMatrix)
        assertTrue((state.qrDialogState?.remainingSeconds ?: 0) > 500)
        assertEquals(SimklConnectionState.AUTHORIZING_POLLING, state.connectionState)
        manager.dismissDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
    }

    // 2. Device polling pending -> success -> user settings + post auth sync
    @Test
    fun `02 device polling completes auth fetches user settings and updates connected state`() = runBlocking {
        server.deviceResponseJson = """
            {"device_code":"dev_ok","user_code":"ABCD-1234","verification_uri":"${server.baseUrl.trimEnd('/')}/pin","expires_in":300,"interval":1}
        """.trimIndent()
        server.tokenResponses.addAll(
            listOf(
                400 to """{"error":"authorization_pending"}""",
                200 to """{"access_token":"tok_access_alice","refresh_token":"tok_refresh_alice","expires_in":86400,"token_type":"Bearer"}"""
            )
        )
        server.userSettingsJson = """{"user":{"name":"AliceTV"},"account":{"id":987654}}"""

        val manager = buildManager()
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)

        waitUntil {
            manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().connectionState == SimklConnectionState.CONNECTED_SYNCED
        }
        val ui = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first()
        assertEquals("AliceTV", ui.accountDisplayName)
        assertEquals("987654", ui.accountId)
        assertNotNull(ui.lastSyncAtMs)
        assertEquals("tok_access_alice", tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID)?.accessToken)
    }

    // 3. Device polling slow_down increases interval
    @Test
    fun `03 device polling handles slow_down by increasing poll interval before succeeding`() = runBlocking {
        server.deviceResponseJson = """
            {"device_code":"dev_slow","user_code":"SLOW-1111","verification_uri":"${server.baseUrl.trimEnd('/')}/pin","expires_in":300,"interval":1}
        """.trimIndent()
        server.tokenResponses.addAll(
            listOf(
                400 to """{"error":"slow_down"}""",
                200 to """{"access_token":"tok_after_slow","refresh_token":"ref_slow","expires_in":86400}"""
            )
        )
        val manager = buildManager(authPollMinIntervalMs = 20L)
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)

        waitUntil {
            val d = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().qrDialogState
            d?.isSlowDownActive == true
        }
        val slowDialog = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().qrDialogState!!
        assertTrue(slowDialog.pollIntervalSeconds >= 6)
        manager.dismissDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
    }

    // 4. Device polling access_denied
    @Test
    fun `04 device polling access_denied stops polling and reports clear error`() = runBlocking {
        server.deviceResponseJson = """
            {"device_code":"dev_deny","user_code":"DENY-0001","verification_uri":"${server.baseUrl.trimEnd('/')}/pin","expires_in":300,"interval":1}
        """.trimIndent()
        server.tokenResponses.add(400 to """{"error":"access_denied"}""")

        val manager = buildManager()
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)

        waitUntil {
            manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().qrDialogState?.isDenied == true
        }
        val dialog = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().qrDialogState!!
        assertTrue(dialog.isDenied)
        assertTrue(dialog.errorMessage?.contains("rifiutata", ignoreCase = true) == true)
        assertNull(tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID))
    }

    // 5. Device polling expired_token & regeneration
    @Test
    fun `05 device polling expired_token marks dialog expired and supports regeneration`() = runBlocking {
        server.deviceResponseJson = """
            {"device_code":"dev_exp","user_code":"EXP-1111","verification_uri":"${server.baseUrl.trimEnd('/')}/pin","expires_in":300,"interval":1}
        """.trimIndent()
        server.tokenResponses.add(400 to """{"error":"expired_token"}""")

        val manager = buildManager()
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)

        waitUntil {
            manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().qrDialogState?.isExpired == true
        }
        val expiredDialog = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().qrDialogState!!
        assertTrue(expiredDialog.canRegenerate)

        server.deviceResponseJson = """
            {"device_code":"dev_new","user_code":"NEW-2222","verification_uri":"${server.baseUrl.trimEnd('/')}/pin","expires_in":300,"interval":1}
        """.trimIndent()
        server.tokenResponses.add(400 to """{"error":"authorization_pending"}""")
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)

        waitUntil {
            manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().qrDialogState?.userCode == "NEW-2222"
        }
        manager.dismissDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
    }

    // 6. Untrusted verification URL rejected
    @Test
    fun `06 rejects untrusted external phishing verification uri`() = runBlocking {
        server.deviceResponseJson = """
            {"device_code":"dev_phish","user_code":"BAD-9999","verification_uri":"https://evil.phishing.example/pin","expires_in":300,"interval":1}
        """.trimIndent()
        val manager = buildManager()
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)

        waitUntil {
            manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first().qrDialogState?.errorMessage != null
        }
        val state = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first()
        assertEquals(SimklConnectionState.ERROR, state.connectionState)
        assertNull(state.qrDialogState?.qrMatrix)
    }

    // 7. Encrypted vault never writes plaintext tokens
    @Test
    fun `07 secure token store encrypts vault file without leaking plaintext secrets`() = runBlocking {
        val secretAccess = "super_secret_simkl_access_token_XYZ"
        val secretRefresh = "super_secret_simkl_refresh_token_ABC"
        tokenStore.saveTokens(
            DEFAULT_PRIMARY_PROFILE_ID,
            SimklTokenBundle(
                accessToken = secretAccess,
                refreshToken = secretRefresh,
                expiresAtMs = nowMs() + 100_000L
            )
        )
        val rawFile = tokenStore.inspectRawVaultFile(DEFAULT_PRIMARY_PROFILE_ID)
        assertTrue(rawFile.exists())
        val rawString = String(rawFile.readBytes(), Charsets.UTF_8)
        assertFalse(rawString.contains(secretAccess))
        assertFalse(rawString.contains(secretRefresh))

        val decrypted = tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID)
        assertEquals(secretAccess, decrypted?.accessToken)
        assertEquals(secretRefresh, decrypted?.refreshToken)
    }

    // 8. Non-destructive Room migration v5 -> v6
    @Test
    fun `08 room migration 5 to 6 preserves existing user progress and creates simkl tables`() = runBlocking {
        val dbName = "simkl_migration_v5_v6_${System.nanoTime()}.db"
        val v5Callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val t0 = 1_000L
                db.execSQL(
                    """
                    CREATE TABLE `user_profiles` (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `avatarId` TEXT NOT NULL, `accentColorHex` INTEGER NOT NULL,
                        `isPrimary` INTEGER NOT NULL, `preferredLanguage` TEXT NOT NULL, `preferredAudioLanguage` TEXT NOT NULL,
                        `subtitlesEnabled` INTEGER NOT NULL, `preferredSubtitleLanguage` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_user_profiles_createdAtMs` ON `user_profiles` (`createdAtMs`)")
                db.execSQL("INSERT INTO `user_profiles` VALUES ('profile_primary', 'Marco', 'red_classic', 15009812, 1, 'it', 'it', 0, 'it', $t0, $t0)")
                db.execSQL(
                    """
                    CREATE TABLE `watch_progress` (
                        `id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `overview` TEXT NOT NULL, `posterPath` TEXT, `backdropPath` TEXT,
                        `voteAverage` REAL NOT NULL, `releaseYear` TEXT NOT NULL,
                        `seasonNumber` INTEGER, `episodeNumber` INTEGER, `episodeTitle` TEXT,
                        `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                        `profileId` TEXT NOT NULL, PRIMARY KEY(`profileId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_watch_progress_profileId_updatedAt` ON `watch_progress` (`profileId`, `updatedAt`)")
                db.execSQL("CREATE INDEX `index_watch_progress_updatedAt` ON `watch_progress` (`updatedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE `episode_progress` (
                        `episodeKey` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL,
                        `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                        `profileId` TEXT NOT NULL, PRIMARY KEY(`profileId`, `episodeKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_episode_progress_profileId_tmdbId_seasonNumber` ON `episode_progress` (`profileId`, `tmdbId`, `seasonNumber`)")
                db.execSQL("CREATE INDEX `index_episode_progress_tmdbId_seasonNumber` ON `episode_progress` (`tmdbId`, `seasonNumber`)")
                db.execSQL(
                    """
                    CREATE TABLE `my_list` (
                        `id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `overview` TEXT NOT NULL, `posterPath` TEXT, `backdropPath` TEXT,
                        `voteAverage` REAL NOT NULL, `releaseYear` TEXT NOT NULL, `addedAt` INTEGER NOT NULL,
                        `profileId` TEXT NOT NULL, PRIMARY KEY(`profileId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_my_list_profileId_addedAt` ON `my_list` (`profileId`, `addedAt`)")
                db.execSQL("CREATE INDEX `index_my_list_addedAt` ON `my_list` (`addedAt`)")
                db.execSQL("CREATE TABLE `catalog_snapshot_cache` (`id` TEXT NOT NULL, `feedJson` TEXT NOT NULL, `movieGenresJson` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE `media_details_cache` (`cacheKey` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `detailsJson` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `lastAccessedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))")
                db.execSQL("CREATE INDEX `index_media_details_cache_lastAccessedAtMs` ON `media_details_cache` (`lastAccessedAtMs`)")
                db.execSQL("CREATE TABLE `season_episodes_cache` (`cacheKey` TEXT NOT NULL, `tvId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL, `episodesJson` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `lastAccessedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))")
                db.execSQL("CREATE INDEX `index_season_episodes_cache_lastAccessedAtMs` ON `season_episodes_cache` (`lastAccessedAtMs`)")
                db.execSQL("CREATE TABLE `stream_link_cache` (`cacheKey` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, `seasonNumber` INTEGER, `episodeNumber` INTEGER, `lang` TEXT NOT NULL, `streamUrl` TEXT NOT NULL, `embedUrl` TEXT NOT NULL, `referer` TEXT NOT NULL, `userAgent` TEXT NOT NULL, `startSeconds` INTEGER NOT NULL, `startMinutes` INTEGER NOT NULL, `subtitlesJson` TEXT NOT NULL, `expiresAtMs` INTEGER NOT NULL, `verifiedAtMs` INTEGER NOT NULL, `lastUsedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))")
                db.execSQL("CREATE INDEX `index_stream_link_cache_expiresAtMs` ON `stream_link_cache` (`expiresAtMs`)")
                db.execSQL("CREATE INDEX `index_stream_link_cache_lastUsedAtMs` ON `stream_link_cache` (`lastUsedAtMs`)")
                VixFlixDatabase.MIGRATION_4_5.migrate(db)
                db.execSQL("INSERT INTO `watch_progress` VALUES ('movie_550', 550, 'movie', 'Fight Club', 'Overview', '/p.jpg', '/b.jpg', 8.4, '1999', NULL, NULL, NULL, 1800000, 7200000, 123456, 'profile_primary')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(dbName)
                .callback(v5Callback)
                .build()
        )
        helper.writableDatabase.close()

        val migrated = Room.databaseBuilder(app, VixFlixDatabase::class.java, dbName)
            .addMigrations(
                VixFlixDatabase.MIGRATION_1_2,
                VixFlixDatabase.MIGRATION_2_3,
                VixFlixDatabase.MIGRATION_3_4,
                VixFlixDatabase.MIGRATION_4_5,
                VixFlixDatabase.MIGRATION_5_6
            )
            .allowMainThreadQueries()
            .build()
        try {
            val mDao = migrated.vixFlixDao()
            val saved = mDao.getWatchProgress("movie_550", "profile_primary")
            assertNotNull(saved)
            assertEquals(1800000L, saved!!.positionMs)
            mDao.upsertSimklProfileMeta(SimklProfileSyncMetaEntity(profileId = "profile_primary", connectionState = "NOT_CONNECTED"))
            assertNotNull(mDao.getSimklProfileMeta("profile_primary"))
        } finally {
            migrated.close()
            app.deleteDatabase(dbName)
        }
    }

    // 9. Token refresh & automatic 401 retry
    @Test
    fun `09 refreshes expiring token and transparently retries 401 calls`() = runBlocking {
        tokenStore.saveTokens(
            DEFAULT_PRIMARY_PROFILE_ID,
            SimklTokenBundle(
                accessToken = "old_access_tok",
                refreshToken = "valid_refresh_tok",
                expiresAtMs = nowMs() + 60_000L // entro la soglia 24h
            )
        )
        db.vixFlixDao().upsertSimklProfileMeta(
            SimklProfileSyncMetaEntity(
                profileId = DEFAULT_PRIMARY_PROFILE_ID,
                connectionState = SimklConnectionState.CONNECTED_SYNCED.name,
                accountDisplayName = "Marco"
            )
        )
        server.tokenResponses.add(
            200 to """{"access_token":"refreshed_access_tok","refresh_token":"rotated_refresh_tok","expires_in":86400}"""
        )

        val manager = buildManager()
        val ok = manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL)
        assertTrue(ok)
        val stored = tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID)
        assertEquals("refreshed_access_tok", stored?.accessToken)
        assertEquals("rotated_refresh_tok", stored?.refreshToken)
        assertTrue(server.recordedRequests.any { it.path == "/sync/activities" && it.headers["authorization"] == "Bearer refreshed_access_tok" })
    }

    // 10. Revoked refresh token -> AUTH_EXPIRED preserving local progress
    @Test
    fun `10 revoked refresh token marks AUTH_EXPIRED and preserves all local watch progress`() = runBlocking {
        val item = MediaItem(550, "movie", "Fight Club", "desc", "/p.jpg", "/b.jpg", 8.4, "1999")
        repo.saveWatchProgress(item, null, null, null, 2_000_000L, 7_200_000L, DEFAULT_PRIMARY_PROFILE_ID)

        tokenStore.saveTokens(
            DEFAULT_PRIMARY_PROFILE_ID,
            SimklTokenBundle(
                accessToken = "expired_tok",
                refreshToken = "revoked_refresh_tok",
                expiresAtMs = nowMs() - 5_000L
            )
        )
        db.vixFlixDao().upsertSimklProfileMeta(
            SimklProfileSyncMetaEntity(DEFAULT_PRIMARY_PROFILE_ID, SimklConnectionState.CONNECTED_SYNCED.name)
        )
        server.tokenResponses.add(401 to """{"error":"invalid_grant"}""")

        val manager = buildManager()
        val ok = manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL)
        assertFalse(ok)

        val ui = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first()
        assertEquals(SimklConnectionState.AUTH_EXPIRED, ui.connectionState)
        assertNull(tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID))
        // Progressi locali assolutamente intatti
        val local = repo.getWatchProgress("movie_550", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(local)
        assertEquals(2_000_000L, local!!.positionMs)
    }

    // 11. Unlink account calls /oauth2/revoke and preserves local data
    @Test
    fun `11 unlinkAccount revokes tokens resets state to NOT_CONNECTED and keeps local progress`() = runBlocking {
        val item = MediaItem(603, "movie", "Matrix", "desc", "/p.jpg", "/b.jpg", 8.7, "1999")
        repo.saveWatchProgress(item, null, null, null, 1_500_000L, 6_000_000L, DEFAULT_PRIMARY_PROFILE_ID)

        tokenStore.saveTokens(
            DEFAULT_PRIMARY_PROFILE_ID,
            SimklTokenBundle("tok_unlink_acc", "tok_unlink_ref", nowMs() + 86_400_000L)
        )
        db.vixFlixDao().upsertSimklProfileMeta(
            SimklProfileSyncMetaEntity(DEFAULT_PRIMARY_PROFILE_ID, SimklConnectionState.CONNECTED_SYNCED.name, accountDisplayName = "Marco")
        )

        val manager = buildManager()
        manager.unlinkAccount(DEFAULT_PRIMARY_PROFILE_ID)

        val ui = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first()
        assertEquals(SimklConnectionState.NOT_CONNECTED, ui.connectionState)
        assertNull(ui.accountDisplayName)
        assertNull(tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID))
        assertTrue(server.recordedRequests.any { it.path == "/oauth2/revoke" })
        assertNotNull(repo.getWatchProgress("movie_603", DEFAULT_PRIMARY_PROFILE_ID))
    }

    // 12 & 13. Multi-profile isolation & deletion cleanup
    @Test
    fun `12_13 strict multi-profile isolation prevents cross-account bleed and cleans up on profile delete`() = runBlocking {
        val profileB = repo.createProfile("Giulia", "blue_wave", 0xFF2196F3L).getOrThrow()
        tokenStore.saveTokens(DEFAULT_PRIMARY_PROFILE_ID, SimklTokenBundle("tok_profile_a", "ref_a", nowMs() + 86_400_000L))
        db.vixFlixDao().upsertSimklProfileMeta(
            SimklProfileSyncMetaEntity(DEFAULT_PRIMARY_PROFILE_ID, SimklConnectionState.CONNECTED_SYNCED.name, accountDisplayName = "MarcoSimkl")
        )

        server.playbackMoviesJson = """
            [{"id":701,"progress":50.0,"paused_at":"2026-10-01T10:00:00Z","movie":{"title":"Inception","year":2010,"ids":{"tmdb":27205}}}]
        """.trimIndent()
        server.tmdbMovieJson[27205] = """{"id":27205,"title":"Inception","overview":"Dreams","poster_path":"/inc.jpg","backdrop_path":"/inc_b.jpg","vote_average":8.8,"release_date":"2010-07-16","runtime":148}"""

        val manager = buildManager()
        assertTrue(manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL))

        // Profilo A ha ricevuto Inception; Profilo B (non collegato) resta isolato e vuoto
        assertNotNull(repo.getWatchProgress("movie_27205", DEFAULT_PRIMARY_PROFILE_ID))
        assertNull(repo.getWatchProgress("movie_27205", profileB.id))
        assertEquals(SimklConnectionState.NOT_CONNECTED, manager.observeProfileUiState(profileB.id).first().connectionState)

        // Eliminazione profilo B con proprio token -> rimuove token + DB Simkl
        tokenStore.saveTokens(profileB.id, SimklTokenBundle("tok_b", "ref_b", nowMs() + 86_400_000L))
        repo.deleteProfile(profileB.id)
        manager.onProfileDeleted(profileB.id)
        assertNull(tokenStore.readTokens(profileB.id))
        assertNull(db.vixFlixDao().getSimklProfileMeta(profileB.id))
    }

    // 14, 15, 16, 17, 18. Scrobble start / pause / stop partial / completion 100 / 409 conflict
    @Test
    fun `14_18 scrobble lifecycle start pause stop completion and 409 soft success`() = runBlocking {
        tokenStore.saveTokens(DEFAULT_PRIMARY_PROFILE_ID, SimklTokenBundle("tok_scrob", "ref_scrob", nowMs() + 86_400_000L))
        db.vixFlixDao().upsertSimklProfileMeta(SimklProfileSyncMetaEntity(DEFAULT_PRIMARY_PROFILE_ID, SimklConnectionState.CONNECTED_SYNCED.name))
        val movie = MediaItem(27205, "movie", "Inception", "desc", "/p.jpg", "/b.jpg", 8.8, "2010")
        val manager = buildManager(minPostIntervalMs = 10L)

        // Start
        manager.enqueueScrobbleStart(DEFAULT_PRIMARY_PROFILE_ID, movie, null, null, 60_000L, 6_000_000L)
        waitUntil { server.recordedRequests.any { it.path == "/scrobble/start" } }
        val startReq = server.recordedRequests.last { it.path == "/scrobble/start" }
        assertEquals("test_simkl_client_id", startReq.headers["simkl-api-key"])
        assertTrue(startReq.body.contains("27205"))

        // Pause
        manager.enqueueScrobblePause(DEFAULT_PRIMARY_PROFILE_ID, movie, null, null, 1_500_000L, 6_000_000L)
        waitUntil { server.recordedRequests.any { it.path == "/scrobble/pause" } }
        val pauseReq = server.recordedRequests.last { it.path == "/scrobble/pause" }
        assertTrue(pauseReq.body.contains("25.0"))

        // Stop partial (< 96%)
        manager.enqueueScrobbleStop(DEFAULT_PRIMARY_PROFILE_ID, movie, null, null, 2_400_000L, 6_000_000L, isExplicitCompletion = false)
        waitUntil { server.recordedRequests.any { it.path.startsWith("/scrobble/stop") && it.query.contains("min_progress=96") } }

        // Explicit completion (100% + min_progress=100) + HTTP 409 treated as soft success
        server.scrobbleStopStatusCode = 409
        manager.enqueueScrobbleStop(DEFAULT_PRIMARY_PROFILE_ID, movie, null, null, 6_000_000L, 6_000_000L, isExplicitCompletion = true)
        waitUntil { server.recordedRequests.any { it.path.startsWith("/scrobble/stop") && it.query.contains("min_progress=100") } }
        waitUntil { db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).isEmpty() }
    }

    // 19 & 20 & 21. Bidirectional Device B -> Simkl -> VixFlix (Movie + Series S02E05) + incremental activities skip
    @Test
    fun `19_21 bidirectional pull populates movie and tv S02E05 in continue watching and skips unchanged activities`() = runBlocking {
        tokenStore.saveTokens(DEFAULT_PRIMARY_PROFILE_ID, SimklTokenBundle("tok_bidir", "ref_bidir", nowMs() + 86_400_000L))
        db.vixFlixDao().upsertSimklProfileMeta(SimklProfileSyncMetaEntity(DEFAULT_PRIMARY_PROFILE_ID, SimklConnectionState.CONNECTED_SYNCED.name))

        server.activitiesJson = """{"all":"2026-10-05T12:00:00Z","movies":{"playback":"2026-10-05T11:00:00Z"},"shows":{"playback":"2026-10-05T12:00:00Z"}}"""
        server.playbackMoviesJson = """
            [{"id":8801,"progress":40.0,"paused_at":"2026-10-05T11:00:00Z","movie":{"title":"Interstellar","year":2014,"ids":{"tmdb":157336}}}]
        """.trimIndent()
        server.playbackEpisodesJson = """
            [{"id":8802,"progress":60.0,"paused_at":"2026-10-05T12:00:00Z","show":{"title":"Breaking Bad","year":2008,"ids":{"tmdb":1396}},"episode":{"season":2,"number":5,"title":"Breakage"}}]
        """.trimIndent()
        server.tmdbMovieJson[157336] = """{"id":157336,"title":"Interstellar","overview":"Space","poster_path":"/int.jpg","backdrop_path":"/int_b.jpg","vote_average":8.6,"release_date":"2014-11-07","runtime":100}"""
        server.tmdbTvJson[1396] = """{"id":1396,"name":"Breaking Bad","overview":"Meth","poster_path":"/bb.jpg","backdrop_path":"/bb_b.jpg","vote_average":9.5,"first_air_date":"2008-01-20"}"""
        server.tmdbSeasonJson["1396_2"] = """{"id":2002,"season_number":2,"episodes":[{"id":205,"name":"Breakage","episode_number":5,"season_number":2,"runtime":50}]}"""

        val manager = buildManager(syncCooldownMs = 0L)
        assertTrue(manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL))

        val movieProg = repo.getWatchProgress("movie_157336", DEFAULT_PRIMARY_PROFILE_ID)!!
        assertEquals("Interstellar", movieProg.title)
        assertEquals(6_000_000L, movieProg.durationMs)
        assertEquals(2_400_000L, movieProg.positionMs)

        val tvProg = repo.getWatchProgress("tv_1396", DEFAULT_PRIMARY_PROFILE_ID)!!
        assertEquals(2, tvProg.seasonNumber)
        assertEquals(5, tvProg.episodeNumber)
        assertEquals(3_000_000L, tvProg.durationMs)
        assertEquals(1_800_000L, tvProg.positionMs)

        val playbackCallsBefore = server.recordedRequests.count { it.path.startsWith("/sync/playback") }
        // Trigger FOREGROUND con timestamps invariati -> salta /sync/playback/*
        assertTrue(manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.FOREGROUND))
        val playbackCallsAfter = server.recordedRequests.count { it.path.startsWith("/sync/playback") }
        assertEquals(playbackCallsBefore, playbackCallsAfter)
    }

    // 22, 23, 24. Conflict resolution (remote wins, local wins, clock-skew tie-breaker)
    @Test
    fun `22_24 conflict resolution picks newer side and resolves clock skew using furthest progress`() = runBlocking {
        val manager = buildManager()
        val olderMs = 1_700_000_000_000L
        val newerMs = olderMs + 60_000L

        // Remote newer -> remote wins
        assertTrue(
            manager.shouldRemoteWin(
                remotePct = 40.0,
                remotePausedAtMs = newerMs,
                localExists = true,
                localPct = 20.0,
                localUpdatedAtMs = olderMs,
                baseline = SimklPlaybackBaselineEntity(DEFAULT_PRIMARY_PROFILE_ID, "movie_1", 1, "movie", localDirty = true)
            )
        )
        // Local significantly newer -> local wins
        assertFalse(
            manager.shouldRemoteWin(
                remotePct = 55.0,
                remotePausedAtMs = olderMs,
                localExists = true,
                localPct = 35.0,
                localUpdatedAtMs = newerMs,
                baseline = SimklPlaybackBaselineEntity(DEFAULT_PRIMARY_PROFILE_ID, "movie_1", 1, "movie", localDirty = true)
            )
        )
        // Clock skew within 5s -> higher % wins
        assertTrue(
            manager.shouldRemoteWin(
                remotePct = 70.0,
                remotePausedAtMs = olderMs + 2_000L,
                localExists = true,
                localPct = 30.0,
                localUpdatedAtMs = olderMs,
                baseline = SimklPlaybackBaselineEntity(DEFAULT_PRIMARY_PROFILE_ID, "movie_1", 1, "movie", localDirty = true)
            )
        )
    }

    // 25 & 26. Remote removal cleanup & local explicit remove sends DELETE /sync/playback/{id}
    @Test
    fun `25_26 remote completion cleans stale card and local deletion triggers DELETE playback`() = runBlocking {
        tokenStore.saveTokens(DEFAULT_PRIMARY_PROFILE_ID, SimklTokenBundle("tok_del", "ref_del", nowMs() + 86_400_000L))
        db.vixFlixDao().upsertSimklProfileMeta(SimklProfileSyncMetaEntity(DEFAULT_PRIMARY_PROFILE_ID, SimklConnectionState.CONNECTED_SYNCED.name))
        server.playbackMoviesJson = """
            [{"id":9901,"progress":50.0,"paused_at":"2026-10-05T10:00:00Z","movie":{"title":"Dune","year":2021,"ids":{"tmdb":438631}}}]
        """.trimIndent()
        server.tmdbMovieJson[438631] = """{"id":438631,"title":"Dune","overview":"Spice","poster_path":"/d.jpg","backdrop_path":"/db.jpg","vote_average":8.0,"release_date":"2021-10-22","runtime":155}"""

        val manager = buildManager()
        manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL)
        assertNotNull(repo.getWatchProgress("movie_438631", DEFAULT_PRIMARY_PROFILE_ID))

        // Rimozione manuale locale -> invia DELETE /sync/playback/9901
        manager.onContinueWatchingRemoved(DEFAULT_PRIMARY_PROFILE_ID, 438631, "movie", null, null)
        waitUntil { server.recordedRequests.any { it.method == "DELETE" && it.path == "/sync/playback/9901" } }

        // Test rimozione remota per un altro titolo già baselineato
        server.playbackMoviesJson = """
            [{"id":9902,"progress":30.0,"paused_at":"2026-10-05T10:00:00Z","movie":{"title":"Arrival","year":2016,"ids":{"tmdb":329865}}}]
        """.trimIndent()
        server.tmdbMovieJson[329865] = """{"id":329865,"title":"Arrival","overview":"Aliens","poster_path":"/a.jpg","backdrop_path":"/ab.jpg","vote_average":8.0,"release_date":"2016-11-11","runtime":116}"""
        manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL)
        assertNotNull(repo.getWatchProgress("movie_329865", DEFAULT_PRIMARY_PROFILE_ID))

        server.playbackMoviesJson = "[]"
        manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL)
        assertNull(repo.getWatchProgress("movie_329865", DEFAULT_PRIMARY_PROFILE_ID))
    }

    // 27. TMDB fallback via /find/{imdb_id} when tmdb id omitted
    @Test
    fun `27 resolves media via tmdb find endpoint when simkl omits tmdb id`() = runBlocking {
        tokenStore.saveTokens(DEFAULT_PRIMARY_PROFILE_ID, SimklTokenBundle("tok_find", "ref_find", nowMs() + 86_400_000L))
        db.vixFlixDao().upsertSimklProfileMeta(SimklProfileSyncMetaEntity(DEFAULT_PRIMARY_PROFILE_ID, SimklConnectionState.CONNECTED_SYNCED.name))
        server.playbackMoviesJson = """
            [{"id":7701,"progress":25.0,"paused_at":"2026-10-05T10:00:00Z","movie":{"title":"The Dark Knight","year":2008,"ids":{"imdb":"tt0468569"}}}]
        """.trimIndent()
        server.tmdbFindJson["tt0468569"] = """
            {"movie_results":[{"id":155,"title":"The Dark Knight","overview":"Batman","poster_path":"/tdk.jpg","backdrop_path":"/tdk_b.jpg","vote_average":8.5,"release_date":"2008-07-18"}]}
        """.trimIndent()
        server.tmdbMovieJson[155] = """{"id":155,"title":"The Dark Knight","overview":"Batman","poster_path":"/tdk.jpg","backdrop_path":"/tdk_b.jpg","vote_average":8.5,"release_date":"2008-07-18","runtime":152}"""

        val manager = buildManager()
        assertTrue(manager.syncProfileNow(DEFAULT_PRIMARY_PROFILE_ID, SimklSyncTrigger.MANUAL))
        val prog = repo.getWatchProgress("movie_155", DEFAULT_PRIMARY_PROFILE_ID)
        assertNotNull(prog)
        assertEquals("The Dark Knight", prog!!.title)
    }

    // 28 & 29. Offline queue coalesce, flush on reconnect, and HTTP 429 Retry-After backoff
    @Test
    fun `28_29 offline queue coalesces duplicates and retries cleanly after HTTP 429`() = runBlocking {
        tokenStore.saveTokens(DEFAULT_PRIMARY_PROFILE_ID, SimklTokenBundle("tok_q", "ref_q", nowMs() + 86_400_000L))
        db.vixFlixDao().upsertSimklProfileMeta(SimklProfileSyncMetaEntity(DEFAULT_PRIMARY_PROFILE_ID, SimklConnectionState.CONNECTED_SYNCED.name))

        val manager = buildManager(minPostIntervalMs = 15L)
        val movie = MediaItem(550, "movie", "Fight Club", "desc", "/p.jpg", "/b.jpg", 8.4, "1999")

        // Prima chiamata restituisce HTTP 429 con Retry-After: 1
        server.scrobblePauseOverrideStatuses.add(429)
        manager.enqueueScrobblePause(DEFAULT_PRIMARY_PROFILE_ID, movie, null, null, 1_000_000L, 4_000_000L)
        waitUntil { db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).isNotEmpty() }

        val uiOffline = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first()
        assertEquals(SimklConnectionState.OFFLINE_QUEUE_PENDING, uiOffline.connectionState)
        assertEquals(1, uiOffline.pendingQueueCount)

        // Coalesce con nuovo aggiornamento per lo stesso contenuto
        manager.enqueueScrobblePause(DEFAULT_PRIMARY_PROFILE_ID, movie, null, null, 2_000_000L, 4_000_000L)
        waitUntil { db.vixFlixDao().getSimklPendingScrobbles(DEFAULT_PRIMARY_PROFILE_ID).isEmpty() }

        val uiSynced = manager.observeProfileUiState(DEFAULT_PRIMARY_PROFILE_ID).first()
        assertEquals(0, uiSynced.pendingQueueCount)
    }

    // 30. Compose UI (Settings card + QR Code Dialog + QR Matrix checks)
    @Test
    fun `30 settings card and qr dialog render all simkl states and unlink keeps local data`() {
        val qrMatrix = SimklQrGenerator.generate("https://simkl.com/pin/VIX-4242")
        assertTrue(qrMatrix.size >= 21)
        // Finder pattern top-left (7x7 bordo scuro + centro 3x3 scuro)
        assertTrue(qrMatrix[0, 0])
        assertTrue(qrMatrix[3, 3])
        assertFalse(qrMatrix[1, 1])

        var connectClicked = false
        var syncClicked = false
        var unlinkedConfirmed = false
        var simklState by mutableStateOf(
            SimklProfileUiState(
                profileId = DEFAULT_PRIMARY_PROFILE_ID,
                connectionState = SimklConnectionState.NOT_CONNECTED,
                isClientIdConfigured = true
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    gitHubState = com.example.ui.GitHubUpdateUiState(),
                    streamLang = "it",
                    preferWebPlayer = false,
                    bufferPreset = com.example.data.local.StreamingBufferPreset.DEFAULT,
                    autoCheckUpdates = true,
                    continueWatchingCount = 2,
                    watchlistCount = 1,
                    isTvLayout = true,
                    requestedCategory = SettingsCategory.SYSTEM,
                    requestedCategoryNonce = 1,
                    onSelectStreamLang = {},
                    onSetPreferWebPlayer = {},
                    onSelectBufferPreset = {},
                    onSetAutoCheckUpdates = {},
                    onRefreshCatalog = {},
                    onClearContinueWatching = {},
                    onRepoSlugChange = {},
                    onCheckUpdates = {},
                    onTriggerUpdate = {},
                    onUpdateTmdbKey = {},
                    onResetTmdbKey = {},
                    profileUiState = UserProfileUiState(),
                    simklUiState = simklState,
                    onConnectSimkl = { connectClicked = true },
                    onSyncSimklNow = { syncClicked = true },
                    onUnlinkSimkl = { unlinkedConfirmed = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_settings_card", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("simkl_connect_button", useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(connectClicked)

        // Stato collegato + dialogo conferma scollegamento
        simklState = SimklProfileUiState(
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            connectionState = SimklConnectionState.CONNECTED_SYNCED,
            accountDisplayName = "MarcoTV",
            accountId = "445566",
            lastSyncAtMs = System.currentTimeMillis(),
            isClientIdConfigured = true
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_account_label", useUnmergedTree = true).performScrollTo().assertTextContains("MarcoTV", substring = true)
        composeRule.onNodeWithTag("simkl_sync_now_button", useUnmergedTree = true).performScrollTo().performClick()
        assertTrue(syncClicked)
        composeRule.onNodeWithTag("simkl_unlink_button", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("simkl_unlink_confirm_dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("simkl_confirm_unlink_button", useUnmergedTree = true).performClick()
        assertTrue(unlinkedConfirmed)
    }

    @Test
    fun `30b qr connect dialog displays code countdown instructions and handles actions`() {
        val qrMatrix = SimklQrGenerator.generate("https://simkl.com/pin/QR-8899")
        var regenClicked = false
        var closeClicked = false

        composeRule.setContent {
            MyApplicationTheme {
                SimklQrConnectDialog(
                    state = SimklQrDialogState(
                        profileId = DEFAULT_PRIMARY_PROFILE_ID,
                        isLoading = false,
                        verificationUrl = "https://simkl.com/pin",
                        userCode = "QR-8899",
                        remainingSeconds = 540,
                        statusMessage = "In attesa di conferma dallo smartphone…",
                        qrMatrix = qrMatrix
                    ),
                    profileName = "Marco",
                    onRegenerate = { regenClicked = true },
                    onDismiss = { closeClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("COLLEGA IL TUO ACCOUNT SIMKL", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Scansiona il QR Code con il tuo smartphone per collegare il tuo account Simkl.",
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("simkl_qr_code", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("simkl_user_code", useUnmergedTree = true).assertTextContains("QR-8899")
        composeRule.onNodeWithTag("simkl_qr_countdown", useUnmergedTree = true).assertTextContains("09:00")
        composeRule.onNodeWithTag("simkl_copy_code_button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("GENERA NUOVO CODICE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("simkl_regenerate_code_button", useUnmergedTree = true).performClick()
        assertTrue(regenClicked)
        composeRule.onNodeWithText("ANNULLA", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("simkl_close_qr_dialog_button", useUnmergedTree = true).performClick()
        assertTrue(closeClicked)
    }

    // 31. Device authorization sends required media scopes & client_id in form body
    @Test
    fun `31 device authorization sends media_read media_write scope and internal client_id`() = runBlocking {
        server.tokenResponses.add(400 to """{"error":"authorization_pending"}""")
        val manager = buildManager(clientId = "vixflix_public_client_123")
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)

        waitUntil { server.recordedRequests.any { it.path == "/oauth2/device" } }
        val req = server.recordedRequests.first { it.path == "/oauth2/device" }
        val formParams = req.body.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                    java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            } else {
                null
            }
        }.toMap()
        assertEquals("vixflix_public_client_123", formParams["client_id"])
        assertEquals(SimklConfig.DEFAULT_SCOPES, formParams["scope"])
        manager.dismissDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
    }

    // 32. Long verification_uri_complete (>= 183 bytes) generates valid V10+ QR matrix
    @Test
    fun `32 qr generator encodes long simkl verification_uri_complete urls cleanly via V10+ 16-bit header`() = runBlocking {
        val longCompleteUrl = "${server.baseUrl.trimEnd('/')}/pin?user_code=LONG-9988&device_session=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        assertTrue(longCompleteUrl.length > 185)
        val matrix = SimklQrGenerator.generate(longCompleteUrl)
        // V10+ has module size >= 57
        assertTrue(matrix.size >= 57)
        assertTrue(matrix[0, 0])
        assertTrue(matrix[3, 3])
        assertFalse(matrix[1, 1])

        server.deviceResponseJson = """
            {
              "device_code": "dev_long_url",
              "user_code": "LONG-9988",
              "verification_uri": "${server.baseUrl.trimEnd('/')}/pin",
              "verification_uri_complete": "$longCompleteUrl",
              "expires_in": 600,
              "interval": 1
            }
        """.trimIndent()
        server.tokenResponses.add(400 to """{"error":"authorization_pending"}""")

        val manager = buildManager()
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
        waitUntil { manager.globalQrDialogState.value?.qrMatrix != null }
        val dialog = manager.globalQrDialogState.value!!
        assertTrue(dialog.hasCompleteVerificationUri)
        assertTrue(dialog.qrMatrix!!.size >= 57)
        manager.dismissDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
    }

    // 33. verification_uri without verification_uri_complete preserves verification_uri and shows manual PIN guidance
    @Test
    fun `33 verification_uri without verification_uri_complete is preserved without fabricating AUTH V1 path`() = runBlocking {
        server.deviceResponseJson = """
            {
              "device_code": "dev_manual_pin",
              "user_code": "PIN-4455",
              "verification_uri": "${server.baseUrl.trimEnd('/')}/oauth2/device/verify",
              "expires_in": 600,
              "interval": 1
            }
        """.trimIndent()
        server.tokenResponses.add(400 to """{"error":"authorization_pending"}""")

        val manager = buildManager()
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
        waitUntil { manager.globalQrDialogState.value?.userCode == "PIN-4455" }

        val dialog = manager.globalQrDialogState.value!!
        assertFalse(dialog.hasCompleteVerificationUri)
        assertEquals("${server.baseUrl.trimEnd('/')}/oauth2/device/verify", dialog.verificationUrl)
        assertNotNull(dialog.qrMatrix)
        manager.dismissDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
    }

    // 34. Cross-profile duplicate guard prevents linking same Simkl account to two VixFlix profiles
    @Test
    fun `34 cross-profile duplicate simkl account prompts switching instead of duplicating link`() = runBlocking {
        val profileB = repo.createProfile("Giulia", "avatar_2", 0xFF2196F3L).getOrThrow()
        tokenStore.saveTokens(DEFAULT_PRIMARY_PROFILE_ID, SimklTokenBundle("tok_a", "ref_a", nowMs() + 86_400_000L))
        db.vixFlixDao().upsertSimklProfileMeta(
            SimklProfileSyncMetaEntity(
                profileId = DEFAULT_PRIMARY_PROFILE_ID,
                connectionState = SimklConnectionState.CONNECTED_SYNCED.name,
                accountDisplayName = "MarcoSimkl",
                accountId = "777888"
            )
        )

        server.deviceResponseJson = """
            {"device_code":"dev_dup","user_code":"DUP-1122","verification_uri":"${server.baseUrl.trimEnd('/')}/pin","expires_in":600,"interval":1}
        """.trimIndent()
        server.tokenResponses.add(200 to """{"access_token":"tok_dup_new","refresh_token":"ref_dup","expires_in":86400}""")
        server.userSettingsJson = """{"user":{"name":"MarcoSimkl"},"account":{"id":777888}}"""

        val manager = buildManager()
        manager.startDeviceAuthorization(profileB.id, SimklAuthFlowMode.LINK_ACTIVE_PROFILE)

        waitUntil {
            manager.globalQrDialogState.value?.resolutionPrompt is SimklQrResolutionPrompt.AlreadyLinkedToOtherProfile
        }
        val prompt = manager.globalQrDialogState.value?.resolutionPrompt as SimklQrResolutionPrompt.AlreadyLinkedToOtherProfile
        assertEquals(DEFAULT_PRIMARY_PROFILE_ID, prompt.existingProfileId)
        assertNull(tokenStore.readTokens(profileB.id))

        val switchedProfile = manager.confirmSwitchToExistingSimklProfile()
        assertEquals(DEFAULT_PRIMARY_PROFILE_ID, switchedProfile)
        assertEquals("tok_dup_new", tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID)?.accessToken)
        assertNull(tokenStore.readTokens(profileB.id))
    }

    // 35. Existing different Simkl account on profile requires confirmation before overwrite and preserves local data
    @Test
    fun `35 replacing existing different simkl account asks confirmation and preserves local watchlist and progress`() = runBlocking {
        val movie = MediaItem(27205, "movie", "Inception", "desc", "/p.jpg", "/b.jpg", 8.8, "2010")
        repo.toggleWatchlist(movie, currentlyInList = false, profileId = DEFAULT_PRIMARY_PROFILE_ID)
        repo.saveWatchProgress(movie, null, null, null, 1_500_000L, 6_000_000L, DEFAULT_PRIMARY_PROFILE_ID)
        tokenStore.saveTokens(DEFAULT_PRIMARY_PROFILE_ID, SimklTokenBundle("tok_old", "ref_old", nowMs() + 86_400_000L))
        db.vixFlixDao().upsertSimklProfileMeta(
            SimklProfileSyncMetaEntity(
                profileId = DEFAULT_PRIMARY_PROFILE_ID,
                connectionState = SimklConnectionState.CONNECTED_SYNCED.name,
                accountDisplayName = "OldUser",
                accountId = "1001"
            )
        )

        server.deviceResponseJson = """
            {"device_code":"dev_rep","user_code":"REP-3344","verification_uri":"${server.baseUrl.trimEnd('/')}/pin","expires_in":600,"interval":1}
        """.trimIndent()
        server.tokenResponses.add(200 to """{"access_token":"tok_new_2002","refresh_token":"ref_new","expires_in":86400}""")
        server.userSettingsJson = """{"user":{"name":"NewUser"},"account":{"id":2002}}"""

        val manager = buildManager()
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID, SimklAuthFlowMode.LINK_ACTIVE_PROFILE)

        waitUntil {
            manager.globalQrDialogState.value?.resolutionPrompt is SimklQrResolutionPrompt.ConfirmReplaceExistingSimkl
        }
        // Token precedente intatto finché l'utente non conferma esplicitamente
        assertEquals("tok_old", tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID)?.accessToken)

        assertTrue(manager.confirmReplaceExistingSimklAccount())
        assertEquals("tok_new_2002", tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID)?.accessToken)
        assertTrue(server.recordedRequests.any { it.path == "/oauth2/revoke" })
        // Preferiti e progressi locali devono rimanere intatti
        assertTrue(db.vixFlixDao().observeWatchlist(DEFAULT_PRIMARY_PROFILE_ID).first().any { it.id == "movie_27205" })
        assertNotNull(repo.getWatchProgress("movie_27205", DEFAULT_PRIMARY_PROFILE_ID))
    }

    // 36. AUTHENTICATE_OR_CREATE_PROFILE recognizes existing profile or creates a new one up to MAX_USER_PROFILES
    @Test
    fun `36 authenticate_or_create_profile creates new profile once and recognizes it on subsequent login`() = runBlocking {
        server.deviceResponseJson = """
            {"device_code":"dev_prof_new","user_code":"PROF-001","verification_uri":"${server.baseUrl.trimEnd('/')}/pin","expires_in":600,"interval":1}
        """.trimIndent()
        server.tokenResponses.add(200 to """{"access_token":"tok_elena","refresh_token":"ref_elena","expires_in":86400}""")
        server.userSettingsJson = """{"user":{"name":"Elena"},"account":{"id":551234}}"""

        val manager = buildManager()
        manager.startDeviceAuthorization(SimklConfig.PROFILE_CREATION_PENDING_ID, SimklAuthFlowMode.AUTHENTICATE_OR_CREATE_PROFILE)

        waitUntil {
            repo.getAllProfiles().any { it.name == "Elena" }
        }
        val elena = repo.getAllProfiles().first { it.name == "Elena" }
        assertEquals("tok_elena", tokenStore.readTokens(elena.id)?.accessToken)

        // Seconda autenticazione con lo stesso account Simkl -> riconosce Elena senza creare duplicati
        server.tokenResponses.add(200 to """{"access_token":"tok_elena_2","refresh_token":"ref_elena_2","expires_in":86400}""")
        manager.startDeviceAuthorization(SimklConfig.PROFILE_CREATION_PENDING_ID, SimklAuthFlowMode.AUTHENTICATE_OR_CREATE_PROFILE)
        waitUntil { tokenStore.readTokens(elena.id)?.accessToken == "tok_elena_2" }
        assertEquals(1, repo.getAllProfiles().count { it.name.startsWith("Elena") })

        // Riempie fino al limite MAX_USER_PROFILES e verifica blocco pulito per nuovo account sconosciuto
        var idx = 1
        while (repo.getAllProfiles().size < MAX_USER_PROFILES) {
            repo.createProfile("Extra$idx", "initial_letter", 0xFF4CAF50L)
            idx++
        }
        server.tokenResponses.add(200 to """{"access_token":"tok_full","refresh_token":"ref_full","expires_in":86400}""")
        server.userSettingsJson = """{"user":{"name":"Overflow"},"account":{"id":999999}}"""
        manager.startDeviceAuthorization(SimklConfig.PROFILE_CREATION_PENDING_ID, SimklAuthFlowMode.AUTHENTICATE_OR_CREATE_PROFILE)
        waitUntil {
            manager.globalQrDialogState.value?.errorMessage?.contains("Limite", ignoreCase = true) == true
        }
        assertEquals(MAX_USER_PROFILES, repo.getAllProfiles().size)
    }

    // 37. Unconfigured SIMKL_CLIENT_ID & invalid / 401 user token responses fail safely
    @Test
    fun `37 missing client_id and HTTP 401 or blank tokens are handled cleanly without saving broken state`() = runBlocking {
        val managerNoKey = buildManager(clientId = "")
        managerNoKey.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
        val noKeyState = managerNoKey.globalQrDialogState.value!!
        assertTrue(noKeyState.isClientIdMissing)
        assertFalse(noKeyState.canRegenerate)
        assertTrue(noKeyState.errorMessage?.contains("SIMKL_CLIENT_ID") == true)

        // Blank access token rejected
        server.deviceResponseJson = """
            {"device_code":"dev_blank","user_code":"BLK-111","verification_uri":"${server.baseUrl.trimEnd('/')}/pin","expires_in":600,"interval":1}
        """.trimIndent()
        server.tokenResponses.add(200 to """{"access_token":"   ","expires_in":86400}""")
        val manager = buildManager()
        manager.startDeviceAuthorization(DEFAULT_PRIMARY_PROFILE_ID)
        waitUntil { manager.globalQrDialogState.value?.errorMessage?.contains("token", ignoreCase = true) == true }
        assertNull(tokenStore.readTokens(DEFAULT_PRIMARY_PROFILE_ID))
    }

    // 38. Conflict resolution UI renders switch & replace confirmation actions cleanly
    @Test
    fun `38 qr dialog renders resolution prompt buttons and executes switch or replace callbacks`() {
        var switchClicked = false
        var replaceClicked = false
        var prompt by mutableStateOf<SimklQrResolutionPrompt>(
            SimklQrResolutionPrompt.AlreadyLinkedToOtherProfile(
                existingProfileId = "p2",
                existingProfileName = "Giulia",
                simklAccountId = "777",
                simklDisplayName = "MarcoSimkl"
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                SimklQrConnectDialog(
                    state = SimklQrDialogState(
                        profileId = DEFAULT_PRIMARY_PROFILE_ID,
                        isLoading = false,
                        resolutionPrompt = prompt
                    ),
                    profileName = "Principale",
                    onRegenerate = {},
                    onDismiss = {},
                    onConfirmSwitchProfile = { switchClicked = true },
                    onConfirmReplaceAccount = { replaceClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_resolution_prompt", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("simkl_switch_existing_profile_button", useUnmergedTree = true).performClick()
        assertTrue(switchClicked)

        prompt = SimklQrResolutionPrompt.ConfirmReplaceExistingSimkl(
            targetProfileId = DEFAULT_PRIMARY_PROFILE_ID,
            targetProfileName = "Principale",
            currentAccountName = "OldUser",
            newAccountId = "2002",
            newAccountName = "NewUser"
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_confirm_replace_account_button", useUnmergedTree = true).performClick()
        assertTrue(replaceClicked)
    }

    // 39. ProfileSelectionScreen exposes Simkl QR auth button cleanly
    @Test
    fun `39 profile selection screen exposes QR auth entry point`() {
        var simklAuthClicked = false
        val profiles = runBlocking { repo.getAllProfiles() }
        composeRule.setContent {
            MyApplicationTheme {
                ProfileSelectionScreen(
                    state = UserProfileUiState(
                        profiles = profiles,
                        activeProfile = profiles.firstOrNull(),
                        showProfileSelector = true,
                        isInitialized = true
                    ),
                    isTvLayout = true,
                    onSelectProfile = {},
                    onToggleManageMode = {},
                    onCreateProfile = { _, _, _, _, _, _, _, done -> done(true) },
                    onUpdateProfile = {},
                    onDeleteProfile = { _, done -> done(true) },
                    onSetAutoLogin = {},
                    onSetDefaultProfile = {},
                    onClearError = {},
                    onClose = {},
                    onAuthenticateWithSimkl = { simklAuthClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_simkl_auth_button", useUnmergedTree = true).performScrollTo().performClick()
        assertTrue(simklAuthClicked)
    }

    // 40. Full ISO/IEC 18004 decoder verification (Finder, Dark Module, Format BCH both copies, Version BCH, RS syndromes, UTF-8 round-trip)
    @Test
    fun `40 iso18004 full decode verifies format bits dark module reed solomon and exact urls across versions`() {
        val testPayloads = listOf(
            "https://simkl.com", // V2
            "https://simkl.com/pin", // V2
            "https://simkl.com/pin?user_code=ABCD-1234", // V3
            "https://simkl.com/oauth2/device/verify?user_code=VIXF-9988&utm_source=android_tv&client=vixflix", // V6/V7 (triggers Version Info)
            "https://simkl.com/pin?user_code=TV-7744&verification=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", // V7+
            "https://simkl.com/pin?user_code=LONG-9988&device_session=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789" // V10+ (16-bit byte count)
        )

        for (payload in testPayloads) {
            val matrix = SimklQrGenerator.generate(payload)
            val decoded = decodeAndVerifyIso18004Qr(matrix)
            assertEquals("QR decode mismatch per payload: $payload", payload, decoded)
        }
    }

    private fun decodeAndVerifyIso18004Qr(matrix: SimklQrMatrix): String {
        val size = matrix.size
        assertTrue("Dimensione QR non valida: $size", size >= 21 && (size - 17) % 4 == 0)
        val version = (size - 17) / 4

        // 1. Verifica Dark Module fisso (4 * V + 9, 8) = (size - 8, 8)
        assertTrue("Dark module fisso mancante a (${size - 8}, 8) per V$version", matrix[size - 8, 8])

        // 2. Verifica Finder Patterns (7x7) e separatori (1 modulo bianco)
        val finderOrigins = listOf(0 to 0, 0 to (size - 7), (size - 7) to 0)
        for ((fr, fc) in finderOrigins) {
            for (dr in 0 until 7) {
                for (dc in 0 until 7) {
                    val expected = dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                    assertEquals("Finder errato in (${fr + dr}, ${fc + dc})", expected, matrix[fr + dr, fc + dc])
                }
            }
        }
        for (i in 0 until 8) {
            assertFalse("Separatore top-left H ($i)", matrix[7, i])
            assertFalse("Separatore top-left V ($i)", matrix[i, 7])
            assertFalse("Separatore top-right H ($i)", matrix[7, size - 8 + i])
            assertFalse("Separatore top-right V ($i)", matrix[i, size - 8])
            assertFalse("Separatore bottom-left H ($i)", matrix[size - 8, i])
            assertFalse("Separatore bottom-left V ($i)", matrix[size - 8 + i, 7])
        }

        // 3. Verifica Timing Patterns
        for (i in 8 until size - 8) {
            val expected = i % 2 == 0
            assertEquals("Timing H errato a (6, $i)", expected, matrix[6, i])
            assertEquals("Timing V errato a ($i, 6)", expected, matrix[i, 6])
        }

        // 4. Leggi e decodifica entrambe le copie di Format Information secondo ISO/IEC 18004
        var formatCopy1 = 0
        for (i in 0..5) if (matrix[i, 8]) formatCopy1 = formatCopy1 or (1 shl i)
        if (matrix[7, 8]) formatCopy1 = formatCopy1 or (1 shl 6)
        if (matrix[8, 8]) formatCopy1 = formatCopy1 or (1 shl 7)
        if (matrix[8, 7]) formatCopy1 = formatCopy1 or (1 shl 8)
        for (i in 9..14) if (matrix[8, 14 - i]) formatCopy1 = formatCopy1 or (1 shl i)

        var formatCopy2 = 0
        for (i in 0..7) if (matrix[8, size - 1 - i]) formatCopy2 = formatCopy2 or (1 shl i)
        for (i in 8..14) if (matrix[size - 15 + i, 8]) formatCopy2 = formatCopy2 or (1 shl i)

        assertEquals("Le due copie del Format Info non coincidono per V$version", formatCopy1, formatCopy2)
        val unmaskedFormat = formatCopy1 xor 0x5412
        // Verifica BCH(15, 5) resto == 0 modulo 0x537
        var formatRem = unmaskedFormat
        for (bit in 14 downTo 10) {
            if (((formatRem ushr bit) and 1) == 1) {
                formatRem = formatRem xor (0x537 shl (bit - 10))
            }
        }
        assertEquals("BCH Format Information non valido per V$version", 0, formatRem and 0x3FF)

        val eccBits = (unmaskedFormat ushr 13) and 0x3
        assertEquals("ECC atteso M (0b00)", 0b00, eccBits)
        val maskPattern = (unmaskedFormat ushr 10) and 0x7

        // 5. Se V >= 7, verifica entrambe le copie del Version Info
        if (version >= 7) {
            var vCopyTr = 0
            var vCopyBl = 0
            for (i in 0 until 18) {
                val a = size - 11 + (i % 3)
                val b = i / 3
                if (matrix[b, a]) vCopyTr = vCopyTr or (1 shl i)
                if (matrix[a, b]) vCopyBl = vCopyBl or (1 shl i)
            }
            assertEquals("Version Info TR/BL non coincidono per V$version", vCopyTr, vCopyBl)
            var vRem = vCopyTr
            for (bit in 17 downTo 12) {
                if (((vRem ushr bit) and 1) == 1) {
                    vRem = vRem xor (0x1F25 shl (bit - 12))
                }
            }
            assertEquals("BCH Version Info non valido per V$version", 0, vRem and 0xFFF)
            assertEquals(version, (vCopyTr ushr 12) and 0x3F)
        }

        // 6. Ricostruisci mappa moduli funzione ISO/IEC 18004 e allineamenti
        val alignmentCenters = when (version) {
            1 -> intArrayOf()
            2 -> intArrayOf(6, 18)
            3 -> intArrayOf(6, 22)
            4 -> intArrayOf(6, 26)
            5 -> intArrayOf(6, 30)
            6 -> intArrayOf(6, 34)
            7 -> intArrayOf(6, 22, 38)
            8 -> intArrayOf(6, 24, 42)
            9 -> intArrayOf(6, 26, 46)
            10 -> intArrayOf(6, 28, 50)
            11 -> intArrayOf(6, 30, 54)
            12 -> intArrayOf(6, 32, 58)
            13 -> intArrayOf(6, 34, 62)
            14 -> intArrayOf(6, 26, 46, 66)
            15 -> intArrayOf(6, 26, 48, 70)
            else -> error("Versione $version non coperta dal decoder test")
        }
        val isFunc = Array(size) { BooleanArray(size) }
        for ((fr, fc) in finderOrigins) {
            for (r in fr until fr + 7) for (c in fc until fc + 7) isFunc[r][c] = true
        }
        for (i in 0 until 8) {
            isFunc[7][i] = true; isFunc[i][7] = true
            isFunc[7][size - 8 + i] = true; isFunc[i][size - 8] = true
            isFunc[size - 8][i] = true; isFunc[size - 8 + i][7] = true
        }
        for (rCenter in alignmentCenters) {
            for (cCenter in alignmentCenters) {
                if (!isFunc[rCenter][cCenter]) {
                    for (dr in -2..2) for (dc in -2..2) {
                        val expected = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                        assertEquals("Alignment errato in (${rCenter + dr}, ${cCenter + dc})", expected, matrix[rCenter + dr, cCenter + dc])
                        isFunc[rCenter + dr][cCenter + dc] = true
                    }
                }
            }
        }
        for (i in 8 until size - 8) {
            isFunc[6][i] = true
            isFunc[i][6] = true
        }
        isFunc[size - 8][8] = true
        for (i in 0..8) if (i != 6) { isFunc[8][i] = true; isFunc[i][8] = true }
        for (i in 0 until 8) { isFunc[8][size - 1 - i] = true; isFunc[size - 1 - i][8] = true }
        if (version >= 7) {
            for (i in 0 until 18) {
                val a = size - 11 + (i % 3)
                val b = i / 3
                isFunc[b][a] = true
                isFunc[a][b] = true
            }
        }

        // 7. Estrai i bit dati a zig-zag applicando la maschera inversa
        val specsM: Map<Int, Pair<List<Pair<Int, Int>>, Int>> = mapOf(
            1 to (listOf(1 to 16) to 10),
            2 to (listOf(1 to 28) to 16),
            3 to (listOf(1 to 44) to 26),
            4 to (listOf(2 to 32) to 18),
            5 to (listOf(2 to 43) to 24),
            6 to (listOf(4 to 27) to 16),
            7 to (listOf(4 to 31) to 18),
            8 to (listOf(2 to 38, 2 to 39) to 22),
            9 to (listOf(3 to 36, 2 to 37) to 22),
            10 to (listOf(4 to 43, 1 to 44) to 26),
            11 to (listOf(1 to 50, 4 to 51) to 30),
            12 to (listOf(6 to 36, 2 to 37) to 22),
            13 to (listOf(8 to 37, 1 to 38) to 22),
            14 to (listOf(4 to 40, 5 to 41) to 24),
            15 to (listOf(5 to 41, 5 to 42) to 24)
        )
        val (groups, ecPerBlock) = specsM.getValue(version)
        val blockDataSizes = ArrayList<Int>()
        groups.forEach { (count, dataBytes) -> repeat(count) { blockDataSizes.add(dataBytes) } }
        val totalCodewords = blockDataSizes.sum() + blockDataSizes.size * ecPerBlock
        val extractedBits = ArrayList<Int>(totalCodewords * 8)

        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val c = right - j
                    val upward = ((right + 1) and 2) == 0
                    val r = if (upward) size - 1 - vert else vert
                    if (!isFunc[r][c] && extractedBits.size < totalCodewords * 8) {
                        val invert = when (maskPattern) {
                            0 -> (r + c) % 2 == 0
                            1 -> r % 2 == 0
                            2 -> c % 3 == 0
                            3 -> (r + c) % 3 == 0
                            4 -> ((r / 2) + (c / 3)) % 2 == 0
                            5 -> (r * c) % 2 + (r * c) % 3 == 0
                            6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
                            7 -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
                            else -> false
                        }
                        val bit = if (matrix[r, c] xor invert) 1 else 0
                        extractedBits.add(bit)
                    }
                }
            }
            right -= 2
        }
        assertEquals(totalCodewords * 8, extractedBits.size)

        val stream = IntArray(totalCodewords) { cw ->
            var v = 0
            for (k in 0 until 8) v = (v shl 1) or extractedBits[cw * 8 + k]
            v
        }

        // 8. De-interleave e verifica sindrome Reed-Solomon == 0 su tutti i blocchi
        val dataBlocks = blockDataSizes.map { IntArray(it) }
        val ecBlocks = blockDataSizes.map { IntArray(ecPerBlock) }
        var pos = 0
        val maxData = blockDataSizes.max()
        for (i in 0 until maxData) {
            for (b in blockDataSizes.indices) {
                if (i < dataBlocks[b].size) dataBlocks[b][i] = stream[pos++]
            }
        }
        for (i in 0 until ecPerBlock) {
            for (b in blockDataSizes.indices) {
                ecBlocks[b][i] = stream[pos++]
            }
        }
        assertEquals(totalCodewords, pos)

        // GF(256) modulo 0x11D per calcolo sindromi RS
        val gfExp = IntArray(512)
        val gfLog = IntArray(256)
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) gfExp[i] = gfExp[i - 255]
        fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else gfExp[gfLog[a] + gfLog[b]]

        for (b in blockDataSizes.indices) {
            val codeword = dataBlocks[b] + ecBlocks[b]
            for (rootExp in 0 until ecPerBlock) {
                val alpha = gfExp[rootExp]
                var eval = 0
                for (coeff in codeword) {
                    eval = mul(eval, alpha) xor coeff
                }
                assertEquals("Sindrome Reed-Solomon diversa da 0 per V$version blocco $b radice $rootExp", 0, eval)
            }
        }

        // 9. Decodifica Byte Mode (0100) -> stringa UTF-8 originale
        val allDataBits = ArrayList<Int>(blockDataSizes.sum() * 8)
        for (block in dataBlocks) {
            for (byteVal in block) {
                for (bit in 7 downTo 0) allDataBits.add((byteVal ushr bit) and 1)
            }
        }
        var ptr = 0
        fun readBits(n: Int): Int {
            var v = 0
            repeat(n) { v = (v shl 1) or allDataBits[ptr++] }
            return v
        }
        val mode = readBits(4)
        assertEquals("Mode Atteso Byte (0100)", 0b0100, mode)
        val countBits = if (version >= 10) 16 else 8
        val byteCount = readBits(countBits)
        val outBytes = ByteArray(byteCount) { readBits(8).toByte() }
        return String(outBytes, Charsets.UTF_8)
    }

    data class RecordedHttpRequest(
        val method: String,
        val path: String,
        val query: String,
        val headers: Map<String, String>,
        val body: String
    )

    private class FakeSimklAndTmdbServer {
        private var socket: ServerSocket? = null
        private val running = AtomicBoolean(false)
        private val pool = Executors.newCachedThreadPool()

        val recordedRequests = CopyOnWriteArrayList<RecordedHttpRequest>()
        val tokenResponses = java.util.concurrent.ConcurrentLinkedQueue<Pair<Int, String>>()
        val scrobblePauseOverrideStatuses = java.util.concurrent.ConcurrentLinkedQueue<Int>()

        @Volatile var deviceResponseJson: String = """{"device_code":"d1","user_code":"C1","verification_uri":"http://127.0.0.1/pin","expires_in":600,"interval":1}"""
        @Volatile var userSettingsJson: String = """{"user":{"name":"TestUser"},"account":{"id":1001}}"""
        @Volatile var activitiesJson: String = """{"all":"2026-10-01T00:00:00Z","movies":{"playback":"2026-10-01T00:00:00Z"},"shows":{"playback":"2026-10-01T00:00:00Z"}}"""
        @Volatile var playbackMoviesJson: String = "[]"
        @Volatile var playbackEpisodesJson: String = "[]"
        @Volatile var scrobbleStopStatusCode: Int = 200
        val tmdbMovieJson = java.util.concurrent.ConcurrentHashMap<Int, String>()
        val tmdbTvJson = java.util.concurrent.ConcurrentHashMap<Int, String>()
        val tmdbSeasonJson = java.util.concurrent.ConcurrentHashMap<String, String>()
        val tmdbFindJson = java.util.concurrent.ConcurrentHashMap<String, String>()

        val baseUrl: String
            get() = "http://127.0.0.1:${socket!!.localPort}/"

        fun start() {
            socket = ServerSocket(0)
            running.set(true)
            pool.execute {
                while (running.get()) {
                    try {
                        val client = socket?.accept() ?: break
                        pool.execute { handleClient(client) }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        fun stop() {
            running.set(false)
            runCatching { socket?.close() }
            pool.shutdownNow()
        }

        private fun handleClient(client: Socket) {
            client.use { s ->
                val input = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val rawTarget = parts.getOrNull(1) ?: "/"
                val qIdx = rawTarget.indexOf('?')
                val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
                val query = if (qIdx >= 0) rawTarget.substring(qIdx + 1) else ""

                val headers = LinkedHashMap<String, String>()
                var contentLength = 0
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val name = line.substring(0, colon).trim().lowercase()
                        val value = line.substring(colon + 1).trim()
                        headers[name] = value
                        if (name == "content-length") {
                            contentLength = value.toIntOrNull() ?: 0
                        }
                    }
                }
                val bodyChars = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val n = input.read(bodyChars, readTotal, contentLength - readTotal)
                    if (n <= 0) break
                    readTotal += n
                }
                val bodyStr = String(bodyChars, 0, readTotal)
                recordedRequests.add(RecordedHttpRequest(method, path, query, headers, bodyStr))

                val (statusCode, respBody) = routeRequest(method, path)
                val out = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                val extraHeader = if (statusCode == 429) "Retry-After: 0\r\n" else ""
                out.write("HTTP/1.1 $statusCode OK\r\nContent-Type: application/json\r\n${extraHeader}Content-Length: ${respBody.toByteArray(Charsets.UTF_8).size}\r\nConnection: close\r\n\r\n$respBody")
                out.flush()
            }
        }

        private fun routeRequest(method: String, path: String): Pair<Int, String> {
            return when {
                path == "/oauth2/device" -> 200 to deviceResponseJson
                path == "/oauth2/token" -> tokenResponses.poll() ?: (200 to """{"access_token":"tok_def","refresh_token":"ref_def","expires_in":86400}""")
                path == "/oauth2/revoke" -> 200 to "{}"
                path == "/users/settings" -> 200 to userSettingsJson
                path == "/sync/activities" -> 200 to activitiesJson
                path == "/sync/playback/movies" -> 200 to playbackMoviesJson
                path == "/sync/playback/episodes" -> 200 to playbackEpisodesJson
                method == "DELETE" && path.startsWith("/sync/playback/") -> 204 to ""
                path == "/scrobble/start" -> 200 to """{"id":5001,"action":"start"}"""
                path == "/scrobble/pause" -> {
                    val forced = scrobblePauseOverrideStatuses.poll()
                    if (forced != null) forced to """{"error":"rate_limited"}"""
                    else 200 to """{"id":5001,"action":"pause"}"""
                }
                path.startsWith("/scrobble/stop") -> scrobbleStopStatusCode to """{"id":5001,"action":"stop"}"""
                path.startsWith("/find/") -> {
                    val ext = path.removePrefix("/find/")
                    200 to (tmdbFindJson[ext] ?: """{"movie_results":[],"tv_results":[]}""")
                }
                Regex("^/movie/(\\d+)$").matches(path) -> {
                    val id = path.removePrefix("/movie/").toInt()
                    200 to (tmdbMovieJson[id] ?: """{"id":$id,"title":"Movie $id","runtime":120}""")
                }
                Regex("^/tv/(\\d+)$").matches(path) -> {
                    val id = path.removePrefix("/tv/").toInt()
                    200 to (tmdbTvJson[id] ?: """{"id":$id,"name":"Series $id"}""")
                }
                Regex("^/tv/(\\d+)/season/(\\d+)$").matches(path) -> {
                    val m = Regex("^/tv/(\\d+)/season/(\\d+)$").find(path)!!
                    val key = "${m.groupValues[1]}_${m.groupValues[2]}"
                    200 to (tmdbSeasonJson[key] ?: """{"season_number":${m.groupValues[2]},"episodes":[]}""")
                }
                else -> 200 to "{}"
            }
        }
    }
}
