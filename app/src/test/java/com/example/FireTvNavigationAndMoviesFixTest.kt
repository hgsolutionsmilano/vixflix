package com.example

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.VixFlixDatabase
import com.example.data.repository.VixFlixRepository
import com.example.ui.NavTab
import com.example.ui.VixFlixViewModel
import com.example.ui.components.DpadFocusObserverRegistry
import com.example.ui.screens.TV_NAV_RAIL_COLLAPSED_WIDTH
import com.example.ui.screens.VixFlixAppScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = VixFlixApplication::class,
    qualifiers = "w960dp-h540dp-television-notouch-dpad"
)
@OptIn(ExperimentalTestApi::class)
class FireTvNavigationAndMoviesFixTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var server: FakeFireTvTmdbServer
    private lateinit var db: VixFlixDatabase

    @Before
    fun setUp() {
        DpadFocusObserverRegistry.clearHistoryForTesting()
        app = ApplicationProvider.getApplicationContext()
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or Configuration.UI_MODE_TYPE_TELEVISION
        }
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(config, app.resources.displayMetrics)
        val shadowPm = shadowOf(app.packageManager)
        shadowPm.setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        @Suppress("DEPRECATION")
        shadowPm.setSystemFeature(PackageManager.FEATURE_TELEVISION, true)

        server = FakeFireTvTmdbServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        server.stop()
    }

    private fun createViewModel(): VixFlixViewModel = runBlocking {
        val repository = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        repository.ensureDefaultPrimaryProfile()
        val vm = VixFlixViewModel(app, repository)
        vm.selectProfile(DEFAULT_PRIMARY_PROFILE_ID)
        vm
    }

    private fun assertNavRailCollapsed(contextMessage: String) {
        val railBounds = composeRule.onNodeWithTag("tv_navigation_rail", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "$contextMessage: il menu laterale deve restare collassato (${TV_NAV_RAIL_COLLAPSED_WIDTH}), effettivo=${railBounds.width}",
            railBounds.width <= TV_NAV_RAIL_COLLAPSED_WIDTH + 6.dp
        )
        assertTrue(
            "$contextMessage: il titolo espanso VIXFLIX TV non deve essere visibile",
            composeRule.onAllNodesWithText("VIXFLIX TV", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
    }

    private fun assertNavRailExpanded(contextMessage: String) {
        val railBounds = composeRule.onNodeWithTag("tv_navigation_rail", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "$contextMessage: il menu laterale deve risultare espanso (> 150.dp), effettivo=${railBounds.width}",
            railBounds.width.value > 150f
        )
    }

    @Test
    fun fireTvExitPopup_opensExclusivelyWithoutSideRail_andHandlesBackAndDpadWithFullPriority() {
        val viewModel = createViewModel()
        var exitConfirmedCount = 0

        composeRule.setContent {
            MyApplicationTheme {
                VixFlixAppScreen(
                    viewModel = viewModel,
                    splashMinDurationMs = 0L,
                    respectStartupProfileGate = false,
                    onRequestExitApp = { exitConfirmedCount++ }
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 6_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("hero_play_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()

        assertNavRailCollapsed("Stato iniziale su Home")

        // 1. Pressione BACK dalla Home: deve aprire SOLO il popup di uscita, MAI il menu laterale
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("vixflix_exit_confirmation_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.advanceTimeBy(220L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("vixflix_exit_confirmation_dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("exit_dialog_cancel_button", useUnmergedTree = true).assertIsFocused()
        assertNavRailCollapsed("All'apertura del popup di uscita")

        // 2. Priorità D-Pad nel popup: DirectionLeft su ANNULLA NON deve aprire o focalizzare il menu laterale
        composeRule.onNodeWithTag("exit_dialog_cancel_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.mainClock.advanceTimeBy(180L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exit_dialog_cancel_button", useUnmergedTree = true).assertIsFocused()
        assertNavRailCollapsed("Dopo DirectionLeft sul popup di uscita")

        // Spostamento a destra su ESCI e ritorno a sinistra su ANNULLA
        composeRule.onNodeWithTag("exit_dialog_cancel_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exit_dialog_confirm_button", useUnmergedTree = true).assertIsFocused()
        assertNavRailCollapsed("Con focus su ESCI nel popup")

        // 3. Tasto BACK mentre il popup è aperto: chiude solo il popup senza aprire il menu laterale e senza uscire
        composeRule.onNodeWithTag("exit_dialog_confirm_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("vixflix_exit_confirmation_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.mainClock.advanceTimeBy(220L)
        composeRule.waitForIdle()

        assertEquals("BACK sul popup non deve chiudere l'app", 0, exitConfirmedCount)
        assertNavRailCollapsed("Dopo chiusura del popup con BACK")
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true).assertIsFocused()

        // 4. Se il menu laterale è aperto manualmente (hero_play -> Left -> hero_watchlist -> Left -> rail), BACK chiude il menu SENZA aprire il popup
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_watchlist_button", useUnmergedTree = true)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.mainClock.advanceTimeBy(220L)
        composeRule.waitForIdle()
        assertNavRailExpanded("Dopo apertura manuale del menu laterale")

        composeRule.onNodeWithTag("tv_rail_home", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.mainClock.advanceTimeBy(220L)
        composeRule.waitForIdle()
        assertNavRailCollapsed("Dopo chiusura del menu laterale con BACK")
        assertTrue(
            "Chiudere il menu laterale con BACK non deve aprire il popup di uscita",
            composeRule.onAllNodesWithTag("vixflix_exit_confirmation_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // 5. Un secondo BACK dalla Home pulita apre SOLO il popup e conferma ESCI con D-Pad Center
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("exit_dialog_cancel_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.advanceTimeBy(180L)
        composeRule.waitForIdle()
        assertNavRailCollapsed("Alla seconda apertura del popup di uscita")

        composeRule.onNodeWithTag("exit_dialog_cancel_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exit_dialog_confirm_button", useUnmergedTree = true)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertEquals("Confermare ESCI deve invocare onRequestExitApp una sola volta", 1, exitConfirmedCount)
    }

    @Test
    fun moviesTab_allMoviesButton_resetsCategoryFilterAndStaysInMoviesWithoutOpeningMainMenu() {
        val viewModel = createViewModel()

        composeRule.setContent {
            MyApplicationTheme {
                VixFlixAppScreen(
                    viewModel = viewModel,
                    splashMinDurationMs = 0L,
                    respectStartupProfileGate = false
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 6_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("hero_play_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Vai alla sezione Film
        viewModel.selectTab(NavTab.MOVIES)
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("genre_chip_28", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        assertEquals(NavTab.MOVIES, viewModel.selectedTab.value)
        assertNull(viewModel.movieGenreState.value.selectedGenre)
        assertNavRailCollapsed("Ingresso nella scheda Film")

        // 1. Seleziona categoria Azione (id 28) tramite D-Pad Center
        composeRule.onNodeWithTag("genre_chip_28", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_28", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("clear_genre_filter_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                !viewModel.movieGenreState.value.isLoading
        }
        composeRule.mainClock.advanceTimeBy(180L)
        composeRule.waitForIdle()

        assertEquals(NavTab.MOVIES, viewModel.selectedTab.value)
        assertEquals(28, viewModel.movieGenreState.value.selectedGenre?.id)
        composeRule.onNodeWithText("Film • Azione", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Tutti i film", useUnmergedTree = true).assertIsDisplayed()

        // 2. Premi "Tutti i film" con telecomando (D-Pad Center): resta nella scheda Film, azzera il filtro, NON apre il menu principale
        composeRule.onNodeWithTag("clear_genre_filter_button", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_genre_filter_button", useUnmergedTree = true)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.movieGenreState.value.selectedGenre == null &&
                composeRule.onAllNodesWithTag("hero_play_button", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.advanceTimeBy(250L)
        composeRule.waitForIdle()

        assertEquals("Premere 'Tutti i film' NON deve tornare alla Home", NavTab.MOVIES, viewModel.selectedTab.value)
        assertNull("Il filtro categoria deve essere azzerato", viewModel.movieGenreState.value.selectedGenre)
        assertTrue(
            "La sezione filtrata per genere non deve più essere visibile",
            composeRule.onAllNodesWithTag("filtered_genre_movies_section", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("genre_chip_all", useUnmergedTree = true).assertIsFocused()
        assertNavRailCollapsed("Dopo aver premuto 'Tutti i film' da Azione")

        // 3. Seleziona Commedia (35) -> cambia direttamente a Fantascienza (878) -> premi "Tutti i film"
        composeRule.onNodeWithTag("genre_chip_35", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_35", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.movieGenreState.value.selectedGenre?.id == 35 &&
                !viewModel.movieGenreState.value.isLoading
        }

        composeRule.onNodeWithTag("genre_chip_878", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_878", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.movieGenreState.value.selectedGenre?.id == 878 &&
                !viewModel.movieGenreState.value.isLoading
        }
        composeRule.onNodeWithText("Film • Fantascienza", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("clear_genre_filter_button", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_genre_filter_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.movieGenreState.value.selectedGenre == null &&
                composeRule.onAllNodesWithTag("hero_play_button", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.advanceTimeBy(250L)
        composeRule.waitForIdle()

        assertEquals("Deve restare su Film anche dopo più cambi categoria", NavTab.MOVIES, viewModel.selectedTab.value)
        assertNull(viewModel.movieGenreState.value.selectedGenre)
        composeRule.onNodeWithTag("genre_chip_all", useUnmergedTree = true).assertIsFocused()
        assertNavRailCollapsed("Dopo aver premuto 'Tutti i film' da Fantascienza")

        // 4. Lo storico di navigazione non è alterato: un singolo BACK dalla scheda Film pulita torna regolarmente a Home
        composeRule.onNodeWithTag("genre_chip_all", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        assertEquals("Un singolo BACK da Film pulita deve tornare a Home", NavTab.HOME, viewModel.selectedTab.value)
        assertNavRailCollapsed("Dopo ritorno regolare a Home da Film")
    }
}

private class FakeFireTvTmdbServer {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    private var running = false

    val port: Int
        get() = serverSocket?.localPort ?: 0

    fun start() {
        serverSocket = ServerSocket(0)
        running = true
        executor.execute {
            while (running) {
                val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                executor.execute {
                    socket.use { s ->
                        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                        val requestLine = reader.readLine() ?: return@use
                        while (true) {
                            val line = reader.readLine()
                            if (line.isNullOrEmpty()) break
                        }
                        val path = requestLine.split(" ").getOrNull(1).orEmpty()
                        val body = routeResponse(path)
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        val writer = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                        writer.write("HTTP/1.1 200 OK\r\n")
                        writer.write("Content-Type: application/json; charset=utf-8\r\n")
                        writer.write("Content-Length: ${bytes.size}\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.write(body)
                        writer.flush()
                    }
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        executor.shutdownNow()
    }

    private fun routeResponse(path: String): String = when {
        path.contains("/genre/movie/list") -> """
            {"genres":[
                {"id":28,"name":"Azione"},
                {"id":35,"name":"Commedia"},
                {"id":878,"name":"Fantascienza"}
            ]}
        """.trimIndent()
        path.contains("/discover/movie") && path.contains("with_genres=28") -> """
            {"page":1,"results":[
                {"id":301,"title":"Azione Uno","overview":"Film azione 1","poster_path":"/a1.jpg","backdrop_path":"/ab1.jpg","vote_average":8.1,"release_date":"2024-02-01"},
                {"id":302,"title":"Azione Due","overview":"Film azione 2","poster_path":"/a2.jpg","backdrop_path":"/ab2.jpg","vote_average":7.8,"release_date":"2024-03-01"}
            ]}
        """.trimIndent()
        path.contains("/discover/movie") && path.contains("with_genres=35") -> """
            {"page":1,"results":[
                {"id":303,"title":"Commedia Uno","overview":"Film commedia 1","poster_path":"/c1.jpg","backdrop_path":"/cb1.jpg","vote_average":7.5,"release_date":"2024-04-01"}
            ]}
        """.trimIndent()
        path.contains("/discover/movie") && path.contains("with_genres=878") -> """
            {"page":1,"results":[
                {"id":304,"title":"SciFi Uno","overview":"Film scifi 1","poster_path":"/s1.jpg","backdrop_path":"/sb1.jpg","vote_average":8.6,"release_date":"2024-05-01"}
            ]}
        """.trimIndent()
        path.contains("/tv/") -> """
            {"page":1,"results":[
                {"id":201,"name":"Serie Demo","overview":"Serie TV demo","poster_path":"/t1.jpg","backdrop_path":"/tb1.jpg","vote_average":8.4,"first_air_date":"2024-01-10"}
            ]}
        """.trimIndent()
        else -> """
            {"page":1,"results":[
                {"id":101,"title":"Film Hero Demo","overview":"Film principale","poster_path":"/m1.jpg","backdrop_path":"/mb1.jpg","vote_average":8.5,"release_date":"2025-01-15"},
                {"id":102,"title":"Film Popolare Demo","overview":"Film popolare","poster_path":"/m2.jpg","backdrop_path":"/mb2.jpg","vote_average":7.9,"release_date":"2024-11-20"}
            ]}
        """.trimIndent()
    }
}
