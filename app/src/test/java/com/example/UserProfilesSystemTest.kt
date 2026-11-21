package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.MAX_USER_PROFILES
import com.example.data.local.VixFlixDatabase
import com.example.data.remote.MediaItem
import com.example.data.repository.VixFlixRepository
import com.example.ui.VixFlixViewModel
import com.example.ui.screens.VixFlixAppScreen
import com.example.ui.theme.MyApplicationTheme
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
@OptIn(ExperimentalTestApi::class)
class UserProfilesSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var server: FakeCatalogServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = FakeCatalogServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `room migration 3 to 4 creates primary profile and preserves all existing favorites and watch progress`() = runBlocking {
        val dbName = "vixflix_migration_v3_v4_${System.nanoTime()}.db"
        val v3Callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watch_progress` (
                        `id` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `overview` TEXT NOT NULL,
                        `posterPath` TEXT,
                        `backdropPath` TEXT,
                        `voteAverage` REAL NOT NULL,
                        `releaseYear` TEXT NOT NULL,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `episodeTitle` TEXT,
                        `positionMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_progress_updatedAt` ON `watch_progress` (`updatedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episode_progress` (
                        `episodeKey` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `seasonNumber` INTEGER NOT NULL,
                        `episodeNumber` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`episodeKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_progress_tmdbId_seasonNumber` ON `episode_progress` (`tmdbId`, `seasonNumber`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `my_list` (
                        `id` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `overview` TEXT NOT NULL,
                        `posterPath` TEXT,
                        `backdropPath` TEXT,
                        `voteAverage` REAL NOT NULL,
                        `releaseYear` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_my_list_addedAt` ON `my_list` (`addedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalog_snapshot_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_details_cache` (
                        `mediaKey` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `lastAccessedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`mediaKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_details_cache_lastAccessedAtMs` ON `media_details_cache` (`lastAccessedAtMs`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `season_episodes_cache` (
                        `seasonKey` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `lastAccessedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`seasonKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_season_episodes_cache_lastAccessedAtMs` ON `season_episodes_cache` (`lastAccessedAtMs`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stream_link_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `lang` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `embedUrl` TEXT NOT NULL,
                        `referer` TEXT NOT NULL,
                        `userAgent` TEXT NOT NULL,
                        `startSeconds` INTEGER NOT NULL,
                        `startMinutes` INTEGER NOT NULL,
                        `subtitlesJson` TEXT NOT NULL,
                        `cachedAtMs` INTEGER NOT NULL,
                        `expiresAtMs` INTEGER NOT NULL,
                        `lastUsedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stream_link_cache_expiresAtMs` ON `stream_link_cache` (`expiresAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stream_link_cache_lastUsedAtMs` ON `stream_link_cache` (`lastUsedAtMs`)")

                // Inserisce dati preesistenti v3
                db.execSQL(
                    """
                    INSERT INTO `watch_progress`
                    VALUES ('movie_603', 603, 'movie', 'The Matrix', 'Sci-fi', '/m.jpg', '/mb.jpg', 8.7, '1999', NULL, NULL, NULL, 1800000, 7200000, 5000)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `episode_progress`
                    VALUES ('tv_1399_s1_e2', 1399, 1, 2, 900000, 3600000, 5100)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `my_list`
                    VALUES ('tv_1399', 1399, 'tv', 'Game of Thrones', 'Fantasy', '/g.jpg', '/gb.jpg', 8.4, '2011', 5200)
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(dbName)
                .callback(v3Callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val upgradedDb = Room.databaseBuilder(app, VixFlixDatabase::class.java, dbName)
            .addMigrations(
                VixFlixDatabase.MIGRATION_1_2,
                VixFlixDatabase.MIGRATION_2_3,
                VixFlixDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = upgradedDb.vixFlixDao()
            val profiles = dao.getAllProfiles()
            assertEquals(1, profiles.size)
            assertEquals(DEFAULT_PRIMARY_PROFILE_ID, profiles.first().id)
            assertTrue(profiles.first().isPrimary)

            val cw = dao.observeContinueWatching(DEFAULT_PRIMARY_PROFILE_ID).first()
            assertEquals(1, cw.size)
            assertEquals("The Matrix", cw.first().title)
            assertEquals(25, cw.first().completionPercent)

            val ep = dao.getEpisodeProgress("tv_1399_s1_e2", DEFAULT_PRIMARY_PROFILE_ID)
            assertNotNull(ep)
            assertEquals(900000L, ep?.positionMs)

            val wl = dao.observeWatchlist(DEFAULT_PRIMARY_PROFILE_ID).first()
            assertEquals(1, wl.size)
            assertEquals("Game of Thrones", wl.first().title)
        } finally {
            upgradedDb.close()
            app.deleteDatabase(dbName)
        }
    }

    @Test
    fun `profiles enforce max 8 limit, strict per-profile data isolation, completion removal, and deletion rules`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = VixFlixRepository(
                dao = db.vixFlixDao(),
                tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
            )

            // 1. Seed profilo principale
            val primary = repo.ensureDefaultPrimaryProfile()
            assertEquals(DEFAULT_PRIMARY_PROFILE_ID, primary.id)
            assertTrue(primary.isPrimary)

            // 2. Creazione fino al limite massimo di 8 profili
            val createdIds = mutableListOf(primary.id)
            for (index in 2..MAX_USER_PROFILES) {
                val created = repo.createProfile(
                    name = "Utente $index",
                    avatarId = "indigo_orb",
                    accentColorHex = 0xFF3B82F6L,
                    preferredLanguage = if (index % 2 == 0) "en" else "it",
                    preferredAudioLanguage = "en",
                    subtitlesEnabled = true,
                    preferredSubtitleLanguage = "it"
                )
                assertTrue(created.isSuccess)
                createdIds.add(created.getOrThrow().id)
            }
            assertEquals(MAX_USER_PROFILES, repo.getAllProfiles().size)

            // 9° profilo deve essere rifiutato
            val ninth = repo.createProfile(
                name = "Troppi",
                avatarId = "amber_star",
                accentColorHex = 0xFFF59E0BL
            )
            assertTrue(ninth.isFailure)

            val profileA = createdIds[0]
            val profileB = createdIds[1]

            val movieA = MediaItem(
                id = 101,
                mediaType = "movie",
                title = "Film Profilo A",
                overview = "Desc A",
                posterPath = "/a.jpg",
                backdropPath = "/ab.jpg",
                voteAverage = 8.1,
                releaseYear = "2024"
            )
            val seriesB = MediaItem(
                id = 202,
                mediaType = "tv",
                title = "Serie Profilo B",
                overview = "Desc B",
                posterPath = "/b.jpg",
                backdropPath = "/bb.jpg",
                voteAverage = 8.6,
                releaseYear = "2025"
            )

            // 3. Isolamento completo preferiti e Continua a guardare tra Profilo A e Profilo B
            repo.toggleWatchlist(movieA, profileA)
            repo.toggleWatchlist(seriesB, profileB)

            repo.saveWatchProgress(
                item = movieA,
                positionMs = 1_800_000L, // 30 min su 60 min -> 50%
                durationMs = 3_600_000L,
                profileId = profileA
            )
            repo.saveWatchProgress(
                item = seriesB,
                positionMs = 1_200_000L, // 20 min su 40 min -> 50%
                durationMs = 2_400_000L,
                seasonNumber = 1,
                episodeNumber = 3,
                episodeTitle = "Episodio 3",
                profileId = profileB
            )

            val listA = repo.observeWatchlist(profileA).first()
            val listB = repo.observeWatchlist(profileB).first()
            assertEquals(listOf("movie_101"), listA.map { it.id })
            assertEquals(listOf("tv_202"), listB.map { it.id })

            val cwA = repo.observeContinueWatching(profileA).first()
            val cwB = repo.observeContinueWatching(profileB).first()
            assertEquals(listOf("movie_101"), cwA.map { it.id })
            assertEquals(50, cwA.first().completionPercent)
            assertEquals(listOf("tv_202"), cwB.map { it.id })
            assertEquals(3, cwB.first().episodeNumber)

            // 4. Completamento (>= 96%) rimuove automaticamente da Continua a guardare (NESSUNA cronologia!)
            repo.saveWatchProgress(
                item = movieA,
                positionMs = 3_500_000L, // 97.2%
                durationMs = 3_600_000L,
                profileId = profileA
            )
            assertTrue(repo.observeContinueWatching(profileA).first().isEmpty())
            assertNull(repo.getWatchProgress("movie_101", profileA))
            // Profilo B resta intatto
            assertEquals(1, repo.observeContinueWatching(profileB).first().size)

            // 5. Protezione profilo principale contro l'eliminazione
            val deletePrimary = repo.deleteProfile(profileA)
            assertTrue(deletePrimary.isFailure)

            // 6. Eliminazione profilo secondario rimuove profilo e TUTTI i suoi dati personali
            val deleteB = repo.deleteProfile(profileB)
            assertTrue(deleteB.isSuccess)
            assertNull(repo.getProfileById(profileB))
            assertTrue(repo.observeWatchlist(profileB).first().isEmpty())
            assertTrue(repo.observeContinueWatching(profileB).first().isEmpty())
            assertTrue(repo.observeSeasonProgress(202, 1, profileB).first().isEmpty())
            // Dati del profilo A restano completamente intatti
            assertEquals(listOf("movie_101"), repo.observeWatchlist(profileA).first().map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun `startup selector Chi sta guardando creates profile, edits preferences, switches context without restart, and supports D-pad`() {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        val viewModel = VixFlixViewModel(app, repo)

        // Reset stato avvio per garantire prima apertura pulita
        viewModel.setAutoLoginProfile(false)
        viewModel.openProfileSelector(manageMode = false)

        composeRule.setContent {
            MyApplicationTheme {
                VixFlixAppScreen(
                    viewModel = viewModel,
                    splashMinDurationMs = 0L,
                    respectStartupProfileGate = true
                )
            }
        }

        // 1. Schermata iniziale "Chi sta guardando?" visibile con profilo principale e "+ Aggiungi profilo"
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("profile_selection_screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Chi sta guardando?").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_tile_$DEFAULT_PRIMARY_PROFILE_ID", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("add_profile_tile", useUnmergedTree = true).assertIsDisplayed()

        // Verifica assenza totale di elementi proibiti (cronologia, bambini, PIN)
        assertTrue(composeRule.onAllNodesWithText("Bambini", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("PIN", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Cronologia", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())

        // 2. Creazione nuovo profilo tramite dialog "+ Aggiungi profilo"
        composeRule.onNodeWithTag("add_profile_tile", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_editor_dialog", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("profile_name_input", useUnmergedTree = true).performTextInput("Marco")
        composeRule.onNodeWithTag("avatar_option_emerald_pulse", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("accent_color_option_2", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("profile_lang_en", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("profile_editor_save_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Il nuovo profilo "Marco" appare subito nella griglia
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.profiles.value.any { it.name == "Marco" }
        }
        val marcoProfile = viewModel.profiles.value.first { it.name == "Marco" }
        assertEquals("emerald_pulse", marcoProfile.avatarId)
        assertEquals("en", marcoProfile.preferredLanguage)

        // 3. Modifica profilo in modalità "Gestisci profili" + salvataggio automatico
        composeRule.onNodeWithTag("profile_manage_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Gestisci profili").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_tile_${marcoProfile.id}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_editor_dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("profile_name_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("profile_name_input", useUnmergedTree = true).performTextInput("Marco TV")
        composeRule.waitForIdle()

        // Chiude editor in modalità modifica (auto-save attivo!)
        composeRule.onNodeWithTag("profile_editor_save_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.profiles.value.any { it.id == marcoProfile.id && it.name == "Marco TV" }
        }

        // Esce da modalità gestione
        composeRule.onNodeWithTag("profile_manage_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // 4. Selezione profilo "Marco TV" -> apre subito il catalogo con lingua EN e pulsante profilo attivo sul rail TV
        composeRule.onNodeWithTag("profile_tile_${marcoProfile.id}", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            !viewModel.profileUiState.value.showProfileSelector &&
                composeRule.onAllNodesWithTag("hero_banner_card", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(marcoProfile.id, viewModel.activeProfileId.value)
        assertEquals("en", viewModel.streamLanguage.value)
        composeRule.onNodeWithTag("tv_rail_profile_button", useUnmergedTree = true).assertIsDisplayed()

        // 5. Tentativo di eliminare il profilo attualmente attivo deve essere bloccato finché non si seleziona un altro profilo
        var deletedActive = true
        viewModel.deleteProfile(marcoProfile.id) { deletedActive = it.isSuccess }
        composeRule.waitUntil(timeoutMillis = 2_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.profileUiState.value.errorMessage != null
        }
        assertFalse(deletedActive)
        assertTrue(viewModel.profiles.value.any { it.id == marcoProfile.id })

        // 6. Cambio rapido profilo dal rail TV (senza riavvio app) -> ritorno al profilo principale
        composeRule.onNodeWithTag("tv_rail_profile_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("profile_selection_screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("profile_tile_$DEFAULT_PRIMARY_PROFILE_ID", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.activeProfileId.value == DEFAULT_PRIMARY_PROFILE_ID &&
                !viewModel.profileUiState.value.showProfileSelector
        }
        assertEquals("it", viewModel.streamLanguage.value)

        // 7. Ora che Marco TV non è più attivo, può essere eliminato correttamente
        var deletedInactive = false
        viewModel.deleteProfile(marcoProfile.id) { deletedInactive = it.isSuccess }
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.profiles.value.none { it.id == marcoProfile.id }
        }
        assertTrue(deletedInactive)

        // 8. Verifica preferenza auto-login e profilo predefinito
        viewModel.setDefaultProfileId(DEFAULT_PRIMARY_PROFILE_ID)
        viewModel.setAutoLoginProfile(true)
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.autoLoginProfile.value && viewModel.defaultProfileId.value == DEFAULT_PRIMARY_PROFILE_ID
        }

        // 9. Navigazione D-Pad sul selettore profili riaperto (OK sul profilo seleziona senza mouse/touch)
        viewModel.openProfileSelector(manageMode = false)
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("profile_selection_screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("profile_tile_$DEFAULT_PRIMARY_PROFILE_ID", useUnmergedTree = true)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_profile_tile", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("add_profile_tile", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_tile_$DEFAULT_PRIMARY_PROFILE_ID", useUnmergedTree = true)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            !viewModel.profileUiState.value.showProfileSelector
        }

        db.close()
    }

    private class FakeCatalogServer {
        private var serverSocket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()
        val port: Int get() = serverSocket?.localPort ?: 0

        fun start() {
            serverSocket = ServerSocket(0)
            executor.submit {
                while (serverSocket?.isClosed == false) {
                    try {
                        val socket = serverSocket!!.accept()
                        executor.submit { handleClient(socket) }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        fun stop() {
            runCatching { serverSocket?.close() }
            executor.shutdownNow()
        }

        private fun handleClient(socket: java.net.Socket) {
            socket.use { client ->
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val path = requestLine.split(" ").getOrNull(1).orEmpty()
                val body = when {
                    path.contains("/genre/") -> """{"genres":[{"id":28,"name":"Azione"}]}"""
                    path.contains("/watch/providers/") -> """{"results":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/n.png","display_priority":1}]}"""
                    else -> """
                        {
                          "page": 1,
                          "results": [
                            {
                              "id": 100,
                              "title": "Dune: Parte Due",
                              "media_type": "movie",
                              "overview": "Un viaggio epico su Arrakis.",
                              "poster_path": "/dune.jpg",
                              "backdrop_path": "/dune_bg.jpg",
                              "vote_average": 8.4,
                              "release_date": "2024-02-28"
                            }
                          ]
                        }
                    """.trimIndent()
                }
                val writer = OutputStreamWriter(client.getOutputStream())
                writer.write(
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${body.toByteArray().size}\r\n" +
                        "Connection: close\r\n\r\n" +
                        body
                )
                writer.flush()
            }
        }
    }
}
