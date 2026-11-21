package com.example

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbGenreDto
import com.example.data.remote.TmdbGenreMap
import com.example.data.remote.TmdbTrailerResolver
import com.example.data.repository.VixFlixRepository
import com.example.ui.VixFlixViewModel
import com.example.ui.screens.VixFlixAppScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-television-notouch-dpad")
class TvNavigationAndViewsCrashTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: FullMockTmdbServer
    private lateinit var app: Application
    private lateinit var dao: com.example.data.local.VixFlixDao
    private lateinit var repository: VixFlixRepository
    private lateinit var viewModel: VixFlixViewModel

    @Before
    fun setUp() {
        TmdbTrailerResolver.forceSoftwareEmulatorMode = true
        server = FullMockTmdbServer()
        server.start()
        app = ApplicationProvider.getApplicationContext()
        val uiModeManager = app.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        shadowOf(uiModeManager).currentModeType = Configuration.UI_MODE_TYPE_TELEVISION
        shadowOf(app.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, true)
        shadowOf(app.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION, true)

        val db = VixFlixDatabase.getInstance(app)
        dao = db.vixFlixDao()
        runBlocking {
            dao.clearAllWatchProgress()
            dao.upsertWatchProgress(
                WatchProgressEntity(
                    id = "movie_101",
                    tmdbId = 101,
                    mediaType = "movie",
                    title = "Film In Corso TV",
                    overview = "Continua a guardare test",
                    posterPath = "/p101.jpg",
                    backdropPath = "/b101.jpg",
                    voteAverage = 8.1,
                    releaseYear = "2024",
                    positionMs = 900_000L,
                    durationMs = 5_400_000L
                )
            )
            dao.addToWatchlist(
                WatchlistEntity(
                    id = "movie_102",
                    tmdbId = 102,
                    mediaType = "movie",
                    title = "Film In Lista TV",
                    overview = "Watchlist test",
                    posterPath = "/p102.jpg",
                    backdropPath = "/b102.jpg",
                    voteAverage = 7.9,
                    releaseYear = "2023"
                )
            )
        }
        repository = VixFlixRepository(
            dao = dao,
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        viewModel = VixFlixViewModel(app, repository)
    }

    @After
    fun tearDown() {
        TmdbTrailerResolver.forceSoftwareEmulatorMode = null
        server.stop()
    }

    private fun launchTvApp() {
        composeRule.setContent {
            MyApplicationTheme {
                VixFlixAppScreen(viewModel = viewModel)
            }
        }
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("hero_banner_card", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `tv movies tab navigating down between movies and back up to categories never crashes`() {
        launchTvApp()

        // Vai su Film tramite la navigation rail TV
        composeRule.onNodeWithTag("tv_rail_movies").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("movie_genre_chip_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Scorri in basso tra le righe film finché la barra categorie e l'hero escono dallo schermo
        composeRule.onNodeWithTag("catalog_lazy_column").performScrollToIndex(5)
        composeRule.waitForIdle()

        // Ora naviga verso i film popolari e premi Su / Sinistra verso la barra categorie e la navigation rail
        composeRule.onNodeWithTag("poster_card_movie_301", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionUp)
            }
        composeRule.waitForIdle()

        // In modalità orizzontale / TV il logo e le icone della barra superiore non devono esistere
        assertTrue(
            "Top bar brand_logo must be removed in landscape/TV view",
            composeRule.onAllNodesWithTag("brand_logo", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Top bar lang_toggle_button must be removed in landscape/TV view",
            composeRule.onAllNodesWithTag("lang_toggle_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Top bar github_update_button must be removed in landscape/TV view",
            composeRule.onAllNodesWithTag("github_update_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun `tv movies genre bar horizontal scroll and category switching never crashes`() {
        launchTvApp()

        composeRule.onNodeWithTag("tv_rail_movies").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("movie_genre_chip_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Scorri orizzontalmente la barra categorie fino in fondo (Western = 37) così "Tutti" esce dallo schermo
        composeRule.onNodeWithTag("movie_genre_chip_bar")
            .performScrollToNode(hasTestTag("genre_chip_37"))
        composeRule.waitForIdle()

        // Premi Giù dalla chip Western mentre "Tutti" è fuori schermo
        composeRule.onNodeWithTag("genre_chip_37", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionDown)
            }
        composeRule.waitForIdle()

        // Seleziona Fantascienza (878), premi Giù durante eventuale caricamento / risultati
        composeRule.onNodeWithTag("movie_genre_chip_bar")
            .performScrollToNode(hasTestTag("genre_chip_878"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_878").performClick()
        composeRule.waitForIdle()

        // Premi Giù subito dalla chip selezionata
        composeRule.onNodeWithTag("genre_chip_878", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionDown)
            }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("filtered_genre_movies_section", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Naviga sulla prima locandina filtrata e premi Su per tornare alle categorie e Sinistra alla rail
        composeRule.onNodeWithTag("poster_card_movie_8780", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionUp)
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("poster_card_movie_8780", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionLeft)
            }
        composeRule.waitForIdle()

        // Seleziona categoria vuota (Documentario = 99) e premi Giù dalla chip
        composeRule.onNodeWithTag("movie_genre_chip_bar")
            .performScrollToNode(hasTestTag("genre_chip_99"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_99").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_99", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionDown)
            }
        composeRule.waitForIdle()

        // Naviga sulla rail TV e premi Destra con lastFocusedContentRequester ormai rimosso
        composeRule.onNodeWithTag("tv_navigation_rail", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionRight)
            }
        composeRule.waitForIdle()
    }

    @Test
    fun `tv navigating across all tabs, search, details, trailer, and settings never crashes`() {
        launchTvApp()

        val allTabs = listOf(
            "tv_rail_home",
            "tv_rail_series",
            "tv_rail_movies",
            "tv_rail_new_popular",
            "tv_rail_my_list",
            "tv_rail_settings",
            "tv_rail_home"
        )
        allTabs.forEach { tag ->
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("tv_navigation_rail", useUnmergedTree = true)
                .performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
        }

        // Test Settings scrolling + navigation back to rail/topbar
        composeRule.onNodeWithTag("tv_rail_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_updates").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_api_sources").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_system").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").performScrollToIndex(2)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_navigation_rail", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        // Test Movie Detail (con overview lungo, trailer, cast con attori duplicati, serie TV + episodi)
        composeRule.onNodeWithTag("tv_rail_movies").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_info_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("detail_play_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Premi Giù dal pulsante Play nei dettagli anche quando le sezioni sotto sono fuori schermo
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        // Apri il popup Trailer YouTube e chiudilo
        composeRule.onNodeWithTag("detail_watch_trailer_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("youtube_trailer_modal_card", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("close_trailer_overlay_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("detail_close_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `tv movies rapid genre switching across loading, empty, error retry and clear never crashes`() {
        launchTvApp()

        composeRule.onNodeWithTag("tv_rail_movies").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("movie_genre_chip_bar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Click Azione (28) while slowing discover slightly, press Down while loading and Left to rail
        server.discoverDelayMs.set(250L)
        composeRule.onNodeWithTag("genre_chip_28").performClick()
        composeRule.onNodeWithTag("genre_chip_28", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionRight)
            }
        composeRule.waitForIdle()

        // 2. Immediately switch to Fantascienza (878) while Azione may still be settling
        server.discoverDelayMs.set(0L)
        composeRule.onNodeWithTag("movie_genre_chip_bar")
            .performScrollToNode(hasTestTag("genre_chip_878"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_878").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("poster_card_movie_8780", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Test clear_genre_filter_button D-pad Up/Left
        composeRule.onNodeWithTag("clear_genre_filter_button", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionLeft)
            }
        composeRule.waitForIdle()

        // 3. Switch to Horror (27) with simulated error -> D-pad around Retry -> Retry -> Verify loaded -> Clear
        server.failDiscover.set(true)
        composeRule.onNodeWithTag("movie_genre_chip_bar")
            .performScrollToNode(hasTestTag("genre_chip_27"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_27").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("retry_genre_movies_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("retry_genre_movies_button", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionLeft)
            }
        composeRule.waitForIdle()

        server.failDiscover.set(false)
        composeRule.onNodeWithTag("retry_genre_movies_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("poster_card_movie_2800", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Premi Rimuovi filtro
        composeRule.onNodeWithTag("clear_genre_filter_button").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `tv movies topbar search grid and my list views handle focus transitions safely`() {
        launchTvApp()

        // Vai su Film, attiva ricerca, digita query, premi Giù su search_results_grid e Sinistra alla rail
        composeRule.onNodeWithTag("tv_rail_movies").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("search_toggle_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("search_input_field", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("search_input_field").performTextInput("Matrix")
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(450))
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("search_results_grid", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("search_input_field", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("poster_card_movie_700", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionLeft)
            }
        composeRule.waitForIdle()

        // Chiudi ricerca e vai su La mia lista
        composeRule.onNodeWithTag("search_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_rail_my_list").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("my_list_screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Naviga tra Continua a guardare e La mia lista con il telecomando, poi svuota lista
        composeRule.onNodeWithTag("tv_navigation_rail", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_102", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionLeft)
            }
        composeRule.waitForIdle()

        runBlocking {
            dao.clearAllWatchProgress()
            dao.removeFromWatchlist("movie_102")
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("browse_catalog_from_empty_list_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("browse_catalog_from_empty_list_button", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionLeft)
            }
        composeRule.waitForIdle()
    }

    @Test
    fun `tv series detail seasons episodes similar stack and player loading never crash`() {
        launchTvApp()

        // Apri Serie TV e apri i dettagli della prima serie (id 500)
        composeRule.onNodeWithTag("tv_rail_series").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_info_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            val ds = viewModel.detailState.value
            ds != null && !ds.isLoading && !ds.isLoadingEpisodes && ds.episodes.isNotEmpty()
        }
        composeRule.waitForIdle()

        // Verifica che appena aperta in TV Mode la scheda dia spazio a locandina, breve testo e bottoni rapidi
        assertTrue(
            composeRule.onAllNodesWithTag("detail_tv_hero_stage", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithTag("detail_tv_poster", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithTag("detail_short_overview", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        // Naviga tra tasti dettagli (Destra/Sinistra rimane sui bottoni rapidi; solo Giù scende sotto)
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionUp)
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("media_detail_lazy_column")
            .performScrollToNode(hasTestTag("episode_card_1_1"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("episode_card_1_1", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
        composeRule.waitForIdle()

        // Apri un titolo simile (900) per testare detail_back_button nello stack di navigazione
        composeRule.onNodeWithTag("media_detail_lazy_column")
            .performScrollToNode(hasTestTag("poster_card_tv_900"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_900").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("detail_back_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("detail_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_close_button").performClick()
        composeRule.waitForIdle()

        // Avvia player dalla Hero e testa tasti telecomando in schermata di caricamento + Annulla
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("vix_player_screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vix_player_screen", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionRight)
            }
        composeRule.waitForIdle()

        if (
            composeRule.onAllNodesWithTag("loading_cancel_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            composeRule.onNodeWithTag("loading_cancel_button").performClick()
        } else if (
            composeRule.onAllNodesWithTag("player_back_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            composeRule.onNodeWithTag("player_back_button").performClick()
        }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("hero_banner_card", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun `tv dpad down never blocks on platform or film banners and detail hero stays centered across button icons`() {
        launchTvApp()

        // 1. In Home (con Continua a guardare attivo), premi D-Pad Giù dall'Hero -> Continua a guardare -> Banner Piattaforma -> Riga sotto
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        // Ora siamo sui banner piattaforma: premendo Giù il focus deve proseguire sotto su Top 10 senza bloccarsi
        composeRule.onNodeWithTag("platform_card_337", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("top10_card_1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        // 2. Nella sezione Film (NavTab.MOVIES), sali ai banner generi e scendi fino a Film di Tendenza
        composeRule.onNodeWithTag("tv_rail_movies").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("genre_chip_all", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("poster_card_movie_200", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        // 3. Apri la scheda di dettaglio del film e verifica che sia centrata e NON si muova spostandosi tra le icone pulsanti
        composeRule.onNodeWithTag("poster_card_movie_200", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("detail_tv_poster", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("detail_watch_trailer_button", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val initialHeroBounds = composeRule.onNodeWithTag("detail_tv_hero_stage", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val initialPosterBounds = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        // Verifica che lo stage non sia traslato verso l'alto (top == 0f) e che la locandina sia centrata verticalmente
        assertTrue("Hero stage top should be 0f, was ${initialHeroBounds.top}", kotlin.math.abs(initialHeroBounds.top) < 1f)
        val stageCenterY = (initialHeroBounds.top + initialHeroBounds.bottom) / 2f
        val posterCenterY = (initialPosterBounds.top + initialPosterBounds.bottom) / 2f
        assertTrue(
            "Poster should be vertically centered (stageCenterY=$stageCenterY, posterCenterY=$posterCenterY)",
            kotlin.math.abs(stageCenterY - posterCenterY) < 25f
        )

        // Spostati tra tutte le icone pulsanti con D-Pad Destra e Sinistra: la scheda non deve muoversi di 1 pixel
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        val afterRight1PosterTop = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Detail view must not move when navigating to Trailer button (initial=${initialPosterBounds.top}, after=$afterRight1PosterTop)",
            kotlin.math.abs(initialPosterBounds.top - afterRight1PosterTop) < 1f
        )

        composeRule.onNodeWithTag("detail_watch_trailer_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        val afterRight2PosterTop = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Detail view must not move when navigating to Watchlist button (initial=${initialPosterBounds.top}, after=$afterRight2PosterTop)",
            kotlin.math.abs(initialPosterBounds.top - afterRight2PosterTop) < 1f
        )

        composeRule.onNodeWithTag("detail_watchlist_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()

        val afterLeftPosterTop = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Detail view must not move when navigating Left back to Trailer button (initial=${initialPosterBounds.top}, after=$afterLeftPosterTop)",
            kotlin.math.abs(initialPosterBounds.top - afterLeftPosterTop) < 1f
        )

        // 4. La vecchia striscia orizzontale trailer NON deve esistere, mentre il bottone Trailer sull'Hero resta attivo
        assertTrue(
            "Horizontal TMDB trailers strip must be completely removed from detail screen",
            composeRule.onAllNodesWithTag("tmdb_trailers_section", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "No trailer card should exist in detail screen",
            composeRule.onAllNodesWithTag("trailer_card_TcMBFSGVi1c", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // Premendo 1 sola volta D-Pad Giù dai bottoni rapidi, si passa direttamente a Cast senza alcun doppio click
        composeRule.onNodeWithTag("detail_watch_trailer_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        assertTrue(
            "After single D-Pad Down from action buttons, Cast section must be visible and focused",
            composeRule.onAllNodesWithTag("cast_card_1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithTag("cast_card_1", useUnmergedTree = true).assertIsFocused()
        val scrolledHeroTop = composeRule.onNodeWithTag("detail_tv_hero_stage", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Hero stage must have scrolled up to reveal below-the-fold content without snapping back to 0 (top=$scrolledHeroTop)",
            scrolledHeroTop < -50f
        )

        // Premendo ancora 1 sola volta D-Pad Giù da Cast si passa direttamente a Titoli Simili
        composeRule.onNodeWithTag("cast_card_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue(
            "After single D-Pad Down from Cast, Similar titles must be visible",
            composeRule.onAllNodesWithTag("poster_card_movie_900", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithTag("poster_card_movie_900", useUnmergedTree = true).assertIsFocused()

        // Premendo D-Pad Su da Titoli Simili -> Cast -> Hero Stage, la scheda torna centrata a top == 0f
        composeRule.onNodeWithTag("poster_card_movie_900", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cast_card_1", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("cast_card_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()

        val restoredHeroTop = composeRule.onNodeWithTag("detail_tv_hero_stage", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Hero stage top must return to 0f when pressing Up back to Hero stage, was $restoredHeroTop",
            kotlin.math.abs(restoredHeroTop) < 1f
        )
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1024dp-h768dp-land")
    fun `landscape tablet renders exact same unified widescreen layout and detail view as Android TV and Fire Stick`() {
        val uiModeManager = app.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        shadowOf(uiModeManager).currentModeType = Configuration.UI_MODE_TYPE_NORMAL

        launchTvApp()

        // 1. Verifica che su Tablet in orizzontale (UI_MODE_TYPE_NORMAL) sia attiva la Navigation Rail TV,
        // che il logo e le icone della barra superiore siano eliminati e che la ricerca sia nella barra laterale
        assertTrue(
            "Landscape tablet must display the unified TV navigation rail",
            composeRule.onAllNodesWithTag("tv_navigation_rail", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "Landscape tablet must not display brand_logo in the top bar",
            composeRule.onAllNodesWithTag("brand_logo", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Landscape tablet must not display lang_toggle_button in the top bar",
            composeRule.onAllNodesWithTag("lang_toggle_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Landscape tablet must not display github_update_button in the top bar",
            composeRule.onAllNodesWithTag("github_update_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Landscape tablet must include search_toggle_button in the vertical navigation rail",
            composeRule.onAllNodesWithTag("search_toggle_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        // 2. Naviga nella sezione Film con Touch o D-Pad e apri la scheda dettaglio widescreen
        composeRule.onNodeWithTag("tv_rail_movies").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hero_info_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("detail_tv_hero_stage", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("detail_tv_poster", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // 3. Verifica che la scheda dettaglio su Tablet usi esattamente detail_tv_hero_stage centrato
        // e rimanga immobile muovendosi tra i bottoni
        val initialPosterTop = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top

        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        val afterRightPosterTop = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Tablet landscape detail view must not shift when moving across buttons (initial=$initialPosterTop, after=$afterRightPosterTop)",
            kotlin.math.abs(initialPosterTop - afterRightPosterTop) < 1f
        )
    }

    @Test
    fun `opening and closing trailer popup from detail screen restores D-Pad focus to Trailer button`() {
        com.example.data.remote.TmdbTrailerResolver.forceSoftwareEmulatorMode = true
        try {
            launchTvApp()

            // 1. Apri la scheda dettaglio di un film
            composeRule.onNodeWithTag("tv_rail_movies").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("hero_info_button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag("detail_play_button", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            // 2. Spostati con D-Pad Destra da Riproduci a Trailer (imposta userMovedFocus = true)
            composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
                .performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()

            assertTrue(
                "Trailer button must have D-Pad focus after pressing Right from Play",
                composeRule.onNodeWithTag("detail_watch_trailer_button", useUnmergedTree = true)
                    .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
            )

            // 3. Apri il popup dei trailer cliccando sul pulsante Trailer
            composeRule.onNodeWithTag("detail_watch_trailer_button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag("in_app_trailer_overlay", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            // Verifica che il pulsante Chiudi nel popup abbia il focus D-Pad
            assertTrue(
                "Close button inside trailer popup must receive D-Pad focus",
                composeRule.onNodeWithTag("close_trailer_overlay_button", useUnmergedTree = true)
                    .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
            )

            // 4. Chiudi il popup dei trailer e verifica che il focus D-Pad torni immediatamente sul bottone Trailer
            composeRule.onNodeWithTag("close_trailer_overlay_button", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag("in_app_trailer_overlay", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()
            }
            composeRule.waitForIdle()

            assertTrue(
                "Detail Trailer button must immediately regain D-Pad focus after closing trailer popup",
                composeRule.onNodeWithTag("detail_watch_trailer_button", useUnmergedTree = true)
                    .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
            )

            // 5. Premendo D-Pad Destra o Sinistra dopo la chiusura del popup, il D-Pad funziona subito
            composeRule.onNodeWithTag("detail_watch_trailer_button", useUnmergedTree = true)
                .performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()

            assertTrue(
                "Watchlist button must receive D-Pad focus when pressing Right after closing trailer popup",
                composeRule.onNodeWithTag("detail_watchlist_button", useUnmergedTree = true)
                    .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
            )
        } finally {
            com.example.data.remote.TmdbTrailerResolver.forceSoftwareEmulatorMode = null
        }
    }

    @Test
    fun `landscape dpad vertical navigation scrolls down and returns to initial top across all tabs including series`() {
        launchTvApp()

        fun isFocused(tag: String): Boolean {
            val nodes = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            return nodes.any {
                it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
            }
        }

        // 1. HOME: verifica top iniziale Hero Banner, scendi con D-Pad Giù fino alle righe inferiori e risali fino in cima
        val homeInitialHeroTop = composeRule.onNodeWithTag("hero_banner_card", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("Home initial hero top should be near 0f, was $homeInitialHeroTop", kotlin.math.abs(homeInitialHeroTop) < 5f)

        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Focus must move to continue_watching_card_movie_101", isFocused("continue_watching_card_movie_101"))

        composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Focus must move to platform_card_337", isFocused("platform_card_337"))

        composeRule.onNodeWithTag("platform_card_337", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Focus must move to top10_card_1", isFocused("top10_card_1"))

        composeRule.onNodeWithTag("top10_card_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Focus must move to poster_card_movie_200", isFocused("poster_card_movie_200"))

        // Risali con D-Pad Su fino all'Hero Banner e verifica che torni esattamente al punto iniziale (top == homeInitialHeroTop)
        composeRule.onNodeWithTag("poster_card_movie_200", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Focus must return to top10_card_1", isFocused("top10_card_1"))

        composeRule.onNodeWithTag("top10_card_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Focus must return to platform_card_337", isFocused("platform_card_337"))

        composeRule.onNodeWithTag("platform_card_337", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Focus must return to continue_watching_card_movie_101", isFocused("continue_watching_card_movie_101"))

        composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Focus must return to hero_play_button", isFocused("hero_play_button"))

        val homeRestoredHeroTop = composeRule.onNodeWithTag("hero_banner_card", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Home hero banner must return to exact initial top ($homeInitialHeroTop), was $homeRestoredHeroTop",
            kotlin.math.abs(homeRestoredHeroTop - homeInitialHeroTop) < 2f
        )

        // 2. SERIE TV: verifica che scorra verso il basso attraverso tutte le righe e torni al punto iniziale con D-Pad Su
        composeRule.onNodeWithTag("tv_rail_series").performClick()
        composeRule.waitForIdle()

        val seriesInitialHeroTop = composeRule.onNodeWithTag("hero_banner_card", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("Series initial hero top should be near 0f, was $seriesInitialHeroTop", kotlin.math.abs(seriesInitialHeroTop) < 5f)
        assertTrue("Series hero_play_button must be focused on tab entry", isFocused("hero_play_button"))

        // Scendi da Hero -> Serie TV in Tendenza (500) -> Serie TV Popolari (600) -> Serie TV Più Votate (604) -> Maratone (503)
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Series must scroll down to Trending TV row (poster_card_tv_500)", isFocused("poster_card_tv_500"))

        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Series must scroll down to Popular TV row (poster_card_tv_600)", isFocused("poster_card_tv_600"))

        composeRule.onNodeWithTag("poster_card_tv_600", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Series must scroll down to Top Rated TV row (poster_card_tv_604)", isFocused("poster_card_tv_604"))

        composeRule.onNodeWithTag("poster_card_tv_604", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Series must scroll down to Binge TV row (poster_card_tv_504)", isFocused("poster_card_tv_504"))

        // Risali con D-Pad Su fino all'Hero Banner di Serie TV
        composeRule.onNodeWithTag("poster_card_tv_504", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Series must move up to Top Rated TV row (poster_card_tv_604)", isFocused("poster_card_tv_604"))

        composeRule.onNodeWithTag("poster_card_tv_604", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Series must move up to Popular TV row (poster_card_tv_600)", isFocused("poster_card_tv_600"))

        composeRule.onNodeWithTag("poster_card_tv_600", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Series must move up to Trending TV row (poster_card_tv_500)", isFocused("poster_card_tv_500"))

        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Series must return focus to hero_play_button", isFocused("hero_play_button"))

        val seriesRestoredHeroTop = composeRule.onNodeWithTag("hero_banner_card", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Series hero banner must return to exact initial top ($seriesInitialHeroTop), was $seriesRestoredHeroTop",
            kotlin.math.abs(seriesRestoredHeroTop - seriesInitialHeroTop) < 2f
        )

        // 3. FILM: verifica discesa e risalita fino a Hero Banner e barra Categorie al punto iniziale
        composeRule.onNodeWithTag("tv_rail_movies").performClick()
        composeRule.waitForIdle()

        val moviesInitialGenreBarTop = composeRule.onNodeWithTag("movie_genre_chip_bar", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val moviesInitialHeroTop = composeRule.onNodeWithTag("hero_banner_card", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y

        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Movies must scroll down to continue_watching_card_movie_101", isFocused("continue_watching_card_movie_101"))

        composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Movies must scroll down to poster_card_movie_200", isFocused("poster_card_movie_200"))

        composeRule.onNodeWithTag("poster_card_movie_200", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Movies must scroll down to poster_card_movie_400", isFocused("poster_card_movie_400"))

        // Risali fino a hero_play_button e verifica che sia l'Hero Banner sia la barra Categorie tornino in cima
        composeRule.onNodeWithTag("poster_card_movie_400", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_200", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Movies must return focus to hero_play_button", isFocused("hero_play_button"))

        val moviesRestoredHeroTop = composeRule.onNodeWithTag("hero_banner_card", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val moviesRestoredGenreBarTop = composeRule.onNodeWithTag("movie_genre_chip_bar", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Movies hero banner must return to initial top ($moviesInitialHeroTop), was $moviesRestoredHeroTop",
            kotlin.math.abs(moviesRestoredHeroTop - moviesInitialHeroTop) < 2f
        )
        assertTrue(
            "Movies genre chip bar must return to initial top ($moviesInitialGenreBarTop), was $moviesRestoredGenreBarTop",
            kotlin.math.abs(moviesRestoredGenreBarTop - moviesInitialGenreBarTop) < 2f
        )

        // 4. NUOVI E POPOLARI: verifica discesa tra le righe e ritorno in cima
        composeRule.onNodeWithTag("tv_rail_new_popular").performClick()
        composeRule.waitForIdle()
        assertTrue("New & Popular first card (top10_card_1) must be focused on tab entry", isFocused("top10_card_1"))
        val newPopularInitialTop = composeRule.onNodeWithTag("top10_card_1", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y

        composeRule.onNodeWithTag("top10_card_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("New & Popular must scroll down to poster_card_movie_400", isFocused("poster_card_movie_400"))

        composeRule.onNodeWithTag("poster_card_movie_400", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("New & Popular must scroll down to poster_card_tv_500", isFocused("poster_card_tv_500"))

        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_400", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("New & Popular must return focus to top10_card_1", isFocused("top10_card_1"))

        val newPopularRestoredTop = composeRule.onNodeWithTag("top10_card_1", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "New & Popular top10_card_1 must return to initial top ($newPopularInitialTop), was $newPopularRestoredTop",
            kotlin.math.abs(newPopularRestoredTop - newPopularInitialTop) < 2f
        )

        // 5. LA MIA LISTA: verifica discesa da Continua a guardare a Salvati nella Mia Lista e ritorno in cima
        composeRule.onNodeWithTag("tv_rail_my_list").performClick()
        composeRule.waitForIdle()
        assertTrue("My List first card must be focused on tab entry", isFocused("continue_watching_card_movie_101"))
        val myListInitialTop = composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y

        composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("My List must scroll down to poster_card_movie_102", isFocused("poster_card_movie_102"))

        composeRule.onNodeWithTag("poster_card_movie_102", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("My List must return focus to continue_watching_card_movie_101", isFocused("continue_watching_card_movie_101"))

        val myListRestoredTop = composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "My List top item must return to initial top ($myListInitialTop), was $myListRestoredTop",
            kotlin.math.abs(myListRestoredTop - myListInitialTop) < 2f
        )

        // 6. IMPOSTAZIONI: verifica discesa nei controlli del pannello e ritorno alla barra categorie con header visibile in cima
        composeRule.onNodeWithTag("tv_rail_settings").performClick()
        composeRule.waitForIdle()
        assertTrue("Settings playback tab must be focused on tab entry", isFocused("settings_tab_playback"))
        val settingsInitialTabTop = composeRule.onNodeWithTag("settings_tab_playback", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y

        composeRule.onNodeWithTag("settings_tab_playback", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertTrue("Settings must move focus down to first control (settings_lang_it)", isFocused("settings_lang_it"))

        composeRule.onNodeWithTag("settings_lang_it", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        assertTrue("Settings must return focus up to settings_tab_playback", isFocused("settings_tab_playback"))

        val settingsRestoredTabTop = composeRule.onNodeWithTag("settings_tab_playback", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Settings category tab bar must return to initial top ($settingsInitialTabTop), was $settingsRestoredTabTop",
            kotlin.math.abs(settingsRestoredTabTop - settingsInitialTabTop) < 2f
        )
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
    fun `firestick leanback movie detail moving across play trailer and watchlist buttons never moves detail card`() {
        launchTvApp()

        // Apri la scheda dettaglio di un film come su Fire TV Stick (960x540dp con FEATURE_LEANBACK attivo)
        composeRule.onNodeWithTag("hero_info_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("detail_play_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val initialHeroTop = composeRule.onNodeWithTag("detail_tv_hero_stage", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val initialPosterTop = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y

        // Sposta il focus su Trailer (D-Pad Right)
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        val afterTrailerHeroTop = composeRule.onNodeWithTag("detail_tv_hero_stage", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val afterTrailerPosterTop = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Fire TV Stick hero stage must not move when focusing Trailer (initial=$initialHeroTop, after=$afterTrailerHeroTop)",
            kotlin.math.abs(afterTrailerHeroTop - initialHeroTop) < 0.5f
        )
        assertTrue(
            "Fire TV Stick poster must not move when focusing Trailer (initial=$initialPosterTop, after=$afterTrailerPosterTop)",
            kotlin.math.abs(afterTrailerPosterTop - initialPosterTop) < 0.5f
        )

        // Sposta il focus su La mia lista (D-Pad Right)
        composeRule.onNodeWithTag("detail_watch_trailer_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        val afterWatchlistHeroTop = composeRule.onNodeWithTag("detail_tv_hero_stage", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val afterWatchlistPosterTop = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Fire TV Stick hero stage must not move when focusing Watchlist (initial=$initialHeroTop, after=$afterWatchlistHeroTop)",
            kotlin.math.abs(afterWatchlistHeroTop - initialHeroTop) < 0.5f
        )
        assertTrue(
            "Fire TV Stick poster must not move when focusing Watchlist (initial=$initialPosterTop, after=$afterWatchlistPosterTop)",
            kotlin.math.abs(afterWatchlistPosterTop - initialPosterTop) < 0.5f
        )

        // Torna indietro su Trailer e poi su Play (D-Pad Left x2)
        composeRule.onNodeWithTag("detail_watchlist_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_watch_trailer_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()

        val afterReturnPlayHeroTop = composeRule.onNodeWithTag("detail_tv_hero_stage", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val afterReturnPlayPosterTop = composeRule.onNodeWithTag("detail_tv_poster", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Fire TV Stick hero stage must not move when returning to Play (initial=$initialHeroTop, after=$afterReturnPlayHeroTop)",
            kotlin.math.abs(afterReturnPlayHeroTop - initialHeroTop) < 0.5f
        )
        assertTrue(
            "Fire TV Stick poster must not move when returning to Play (initial=$initialPosterTop, after=$afterReturnPlayPosterTop)",
            kotlin.math.abs(afterReturnPlayPosterTop - initialPosterTop) < 0.5f
        )
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
    fun `tv dpad remembers row position preserves grid columns switches settings tabs instantly and uses two step detail back`() {
        launchTvApp()

        // 1. Memoria orizzontale per riga: nella sezione Serie TV scorri a destra nella prima riga, scendi alla seconda e risali
        composeRule.onNodeWithTag("tv_rail_series").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        // Spostati a destra nella riga 0 (Serie TV di Tendenza: 500 -> 501)
        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_501", useUnmergedTree = true).assertIsFocused()

        // Scendi alla riga successiva (Serie TV Popolari: 600) e poi risali: deve ricordare poster_card_tv_501!
        composeRule.onNodeWithTag("poster_card_tv_501", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_600", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("poster_card_tv_600", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_501", useUnmergedTree = true).assertIsFocused()

        // 2. Scheda Dettaglio Serie: navigazione Episodi + Doppio Step sul tasto Indietro (1° torna in cima su Riproduci, 2° chiude)
        composeRule.onNodeWithTag("poster_card_tv_501", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            val ds = viewModel.detailState.value
            ds != null && !ds.isLoading && !ds.isLoadingEpisodes && ds.episodes.size >= 2
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()

        // Scendi sotto la piega con singola pressione per sezione: Cast -> Stagione -> Episodio 1 -> Episodio 2
        assertTrue(
            "Horizontal trailer strip must not exist on TV series detail screen",
            composeRule.onAllNodesWithTag("tmdb_trailers_section", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cast_card_11", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("cast_card_11", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_season_chip_1", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("detail_season_chip_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("episode_card_1_1", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("episode_card_1_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("episode_card_1_2", useUnmergedTree = true).assertIsFocused()

        // 1° Indietro mentre siamo sotto la piega su Episodio 2: NON chiude la scheda, ma torna in cima su detail_play_button
        composeRule.onNodeWithTag("episode_card_1_2", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()
        assertTrue("Detail state must still be open after first Back from below fold", viewModel.detailState.value != null)
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()

        // 2° Indietro mentre siamo sul palco Hero in cima: chiude la scheda dettaglio e ripristina il focus su poster_card_tv_501
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()
        assertTrue("Detail state must close on second Back from hero stage", viewModel.detailState.value == null)
        composeRule.onNodeWithTag("poster_card_tv_501", useUnmergedTree = true).assertIsFocused()

        // 3. Griglia Multi-Colonna Genere Film: preserva la colonna quando si scende/sale tra le righe della griglia
        composeRule.onNodeWithTag("tv_rail_movies").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_28", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("poster_card_movie_2800", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Scendi con il D-Pad dal chip genere selezionato direttamente alla prima locandina della griglia genere (2800)
        composeRule.onNodeWithTag("genre_chip_28", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("genre_chip_28", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_2800", useUnmergedTree = true).assertIsFocused()

        // Spostati sulla 2ª colonna della 1ª riga (2801) e premi Giù: su 4 colonne (888dp disponibili) atterra sulla 2ª colonna della 2ª riga (2805)
        composeRule.onNodeWithTag("poster_card_movie_2800", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_2801", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("poster_card_movie_2801", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_2805", useUnmergedTree = true).assertIsFocused()

        // 4. Ricerca: da search_input_field Destra raggiunge top_bar_search_clear e Sinistra torna a search_input_field
        composeRule.onNodeWithTag("search_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_input_field", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_input_field", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top_bar_search_clear", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("top_bar_search_clear", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_input_field", useUnmergedTree = true).assertIsFocused()

        // 5. Impostazioni: spostarsi con Destra sui tab superiori cambia subito scheda e Giù entra nei controlli della scheda attiva
        composeRule.onNodeWithTag("tv_rail_settings").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("settings_tab_playback", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_playback", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("settings_tab_playback", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_updates", useUnmergedTree = true).assertIsFocused()
        assertTrue(
            "Updates panel should become active immediately when focusing UPDATES tab",
            composeRule.onAllNodesWithTag("settings_auto_update_switch", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithTag("settings_tab_updates", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_auto_update_switch", useUnmergedTree = true).assertIsFocused()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
    fun `horizontal navigation rail dpad enters on active tab navigates up down and restores exact content focus on right`() {
        launchTvApp()

        // 1. All'avvio su Home il focus è su hero_play_button (al centro); Sinistra va su hero_watchlist_button e ancora Sinistra apre il menu laterale su tv_rail_home
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_watchlist_button", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("hero_watchlist_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_rail_home", useUnmergedTree = true).assertIsFocused()

        // 2. Su nel menu laterale va su search_toggle_button e si blocca in cima
        composeRule.onNodeWithTag("tv_rail_home", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_toggle_button", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("search_toggle_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_toggle_button", useUnmergedTree = true).assertIsFocused()

        // 3. Destra dal menu laterale collassa la barra e riporta il focus sull'ultimo bottone Hero focalizzato (hero_watchlist_button)
        composeRule.onNodeWithTag("search_toggle_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_watchlist_button", useUnmergedTree = true).assertIsFocused()

        // 4. Selezioniamo Serie TV dal menu laterale con Enter: il focus passa subito al contenuto di Serie TV
        composeRule.onNodeWithTag("hero_watchlist_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_rail_home", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("tv_rail_home", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_rail_series", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("tv_rail_series", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("poster_card_tv_500", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // 5. In Serie TV, scendiamo sulla prima card della riga (poster_card_tv_500) e premiamo Sinistra:
        // deve atterrare su tv_rail_series (NON su tv_rail_home!)
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_rail_series", useUnmergedTree = true).assertIsFocused()

        // 6. Scendiamo su Impostazioni nel menu laterale e attiviamola con Enter
        repeat(4) {
            val focusedRailTag = listOf(
                "tv_rail_series",
                "tv_rail_movies",
                "tv_rail_new_popular",
                "tv_rail_my_list"
            )[it]
            composeRule.onNodeWithTag(focusedRailTag, useUnmergedTree = true)
                .performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("tv_rail_settings", useUnmergedTree = true).assertIsFocused()
        // Giù sull'ultimo elemento resta bloccato su tv_rail_settings
        composeRule.onNodeWithTag("tv_rail_settings", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_rail_settings", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("tv_rail_settings", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("settings_tab_playback", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_playback", useUnmergedTree = true).assertIsFocused()

        // 7. In Impostazioni ci spostiamo su Aggiornamenti (settings_tab_updates), poi torniamo a Sinistra fino al menu laterale:
        // deve atterrare su tv_rail_settings, e premendo Destra deve tornare esattamente su settings_tab_updates
        composeRule.onNodeWithTag("settings_tab_playback", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_updates", useUnmergedTree = true).assertIsFocused()

        // Scendiamo sul controllo nel pannello Aggiornamenti e premiamo Sinistra per aprire direttamente il menu laterale
        composeRule.onNodeWithTag("settings_tab_updates", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_auto_update_switch", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("settings_auto_update_switch", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_rail_settings", useUnmergedTree = true).assertIsFocused()

        // Premendo Destra dal menu laterale torna esattamente a settings_auto_update_switch
        composeRule.onNodeWithTag("tv_rail_settings", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_auto_update_switch", useUnmergedTree = true).assertIsFocused()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
    fun `remote menu key opens poster quick action dialog navigates vertically escapes cleanly and opens rail from hero`() {
        launchTvApp()

        // 1. Su Hero Banner il tasto MENU del telecomando apre il menu laterale sul tab attivo
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Menu) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_rail_home", useUnmergedTree = true).assertIsFocused()

        // Torna su Serie TV e sposta il focus sulla prima locandina della riga (poster_card_tv_500)
        composeRule.onNodeWithTag("tv_rail_series").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("poster_card_tv_500", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true).assertIsFocused()

        // 2. Premendo MENU sulla locandina si apre il popup rapido opzioni TV con focus immediato su Riproduci
        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Menu) }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("quick_poster_play_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_poster_play_button", useUnmergedTree = true).assertIsFocused()

        // 3. Navigazione verticale D-Pad Giù attraverso tutte le voci del modal + edge lock sul fondo
        composeRule.onNodeWithTag("quick_poster_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_poster_details_button", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("quick_poster_details_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_poster_watchlist_button", useUnmergedTree = true).assertIsFocused()

        // Edge lock sul fondo (Giù / Destra / Sinistra non fanno fuggire il focus)
        composeRule.onNodeWithTag("quick_poster_watchlist_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_poster_watchlist_button", useUnmergedTree = true).assertIsFocused()

        // 4. ESC chiude il modal e ripristina il focus D-Pad sulla locandina di origine
        composeRule.onNodeWithTag("quick_poster_watchlist_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Escape) }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("quick_poster_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true).assertIsFocused()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
    fun `github update dialog focuses confirm button navigates horizontally and dismisses with escape`() {
        var dismissed = false
        val mockState = com.example.ui.GitHubUpdateUiState(
            showUpdateDialog = true,
            releaseInfo = com.example.data.remote.GitHubReleaseInfo(
                tagName = "v9.9.9",
                releaseTitle = "Test Release",
                changelog = "Ottimizzazione D-Pad globale",
                publishedAt = "2026-11-20",
                htmlUrl = "https://github.com/test/repo/releases/tag/v9.9.9",
                apkDownloadUrl = "https://example.com/app.apk",
                apkFileName = "app-release.apk",
                apkSizeBytes = 12_000_000L,
                isUpdateAvailable = true
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.GitHubUpdateDialog(
                    state = mockState,
                    onDismiss = { dismissed = true },
                    onConfirmUpdate = {},
                    onOpenGitHubTab = {}
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("github_update_confirm_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("github_update_confirm_button", useUnmergedTree = true).assertIsFocused()

        // Edge lock a destra sul bottone Installa Ora
        composeRule.onNodeWithTag("github_update_confirm_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("github_update_confirm_button", useUnmergedTree = true).assertIsFocused()

        // Sinistra -> Più tardi -> Sinistra -> Dettagli CI
        composeRule.onNodeWithTag("github_update_confirm_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("github_update_dismiss_button", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("github_update_dismiss_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("github_update_details_button", useUnmergedTree = true).assertIsFocused()

        // Destra torna a Più tardi e poi Installa Ora
        composeRule.onNodeWithTag("github_update_details_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("github_update_dismiss_button", useUnmergedTree = true).assertIsFocused()

        // ESC chiude il dialog
        composeRule.onNodeWithTag("github_update_dismiss_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()
        assertTrue("GitHubUpdateDialog must dismiss on Key.Escape", dismissed)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
    fun `settings all four panels support complete dpad navigation including text fields and horizontal button rows`() {
        launchTvApp()

        composeRule.onNodeWithTag("tv_rail_settings").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("settings_tab_playback", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // 1. Tab AGGIORNAMENTI: Switch -> OutlinedTextField (github_repo_input) -> Verifica Nuova Versione -> Rail
        composeRule.onNodeWithTag("settings_tab_playback", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_updates", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("settings_tab_updates", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_auto_update_switch", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("settings_auto_update_switch", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("github_repo_input", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("github_repo_input", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("check_github_updates_button", useUnmergedTree = true).assertIsFocused()

        // Risalita da check_github_updates_button -> github_repo_input -> settings_auto_update_switch -> settings_tab_updates
        composeRule.onNodeWithTag("check_github_updates_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("github_repo_input", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("github_repo_input", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_auto_update_switch", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("settings_auto_update_switch", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_updates", useUnmergedTree = true).assertIsFocused()

        // 2. Tab API & SORGENTI: tmdb_api_key_input -> Applica Chiave <-> Copia -> Dettagli tecnici
        composeRule.onNodeWithTag("settings_tab_updates", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_api_sources", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("settings_tab_api_sources", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tmdb_api_key_input", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("tmdb_api_key_input", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_tmdb_key_button", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("save_tmdb_key_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("copy_tmdb_key_button", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("copy_tmdb_key_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("toggle_technical_details_button", useUnmergedTree = true).assertIsFocused()

        // Sinistra da toggle_technical_details_button apre il menu laterale su tv_rail_settings
        composeRule.onNodeWithTag("toggle_technical_details_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_rail_settings", useUnmergedTree = true).assertIsFocused()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
    fun `detail screen supports media play remote key and top bar edge locks without losing focus`() {
        launchTvApp()

        composeRule.onNodeWithTag("hero_info_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("detail_play_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()

        // Su da Play porta il focus sul tasto Chiudi in alto; Su / Destra / Sinistra restano bloccati lì
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_close_button", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("detail_close_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_close_button", useUnmergedTree = true).assertIsFocused()

        // Giù da Chiudi torna al pulsante Play
        composeRule.onNodeWithTag("detail_close_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()

        // Il tasto multimediale MediaPlay sul telecomando avvia direttamente lo streaming
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.MediaPlay) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.playbackSession.value != null
        }
        assertTrue("Pressing Key.MediaPlay on detail screen must start playback session", viewModel.playbackSession.value != null)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
    fun `detail modal single-press D-Pad scroll and exact poster focus restoration work across repeated open-close cycles for movies and series`() {
        launchTvApp()

        // 1. HOME (Film): naviga solo con D-Pad fino alla 3ª locandina della riga Film di Tendenza (poster_card_movie_202)
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true).assertIsFocused()
        // Giù -> Continua a guardare -> Piattaforme streaming -> Top 10 -> Film di Tendenza (200)
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("continue_watching_card_movie_101", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("platform_card_337", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top10_card_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_200", useUnmergedTree = true).assertIsFocused()

        // Destra x2 -> poster_card_movie_202
        composeRule.onNodeWithTag("poster_card_movie_200", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_201", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("poster_card_movie_201", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_202", useUnmergedTree = true).assertIsFocused()

        // Ciclo 1: Apri modale Film solo con D-Pad OK, verifica assenza striscia trailer, scorri con 1 sola pressione GIÙ, chiudi con bottone Chiudi
        composeRule.onNodeWithTag("poster_card_movie_202", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            val ds = viewModel.detailState.value
            ds != null && !ds.isLoading && ds.details != null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()
        assertTrue(
            "Trailer strip must not be rendered in movie detail",
            composeRule.onAllNodesWithTag("tmdb_trailers_section", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Hero Trailer action button must remain available",
            composeRule.onAllNodesWithTag("detail_watch_trailer_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        // 1 sola pressione GIÙ -> Cast, 1 sola pressione GIÙ -> Titoli Simili
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cast_card_1", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("cast_card_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_900", useUnmergedTree = true).assertIsFocused()

        // Risali con SU -> Cast -> Play -> Su -> Chiudi -> OK per chiudere la modale
        composeRule.onNodeWithTag("poster_card_movie_900", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cast_card_1", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("cast_card_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_close_button", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("detail_close_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value == null
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(150L)
        composeRule.waitForIdle()

        // Il focus DEVE tornare esattamente su poster_card_movie_202 (NON su hero_play_button o sulla rail)
        composeRule.onNodeWithTag("poster_card_movie_202", useUnmergedTree = true).assertIsFocused()

        // Ciclo 2: Riapri la STESSA locandina con OK e chiudi subito con BACK
        composeRule.onNodeWithTag("poster_card_movie_202", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value != null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value == null
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(150L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_202", useUnmergedTree = true).assertIsFocused()

        // Ciclo 3: Spostati a destra su poster_card_movie_203, apri e chiudi con BACK -> focus esatto su 203
        composeRule.onNodeWithTag("poster_card_movie_202", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_203", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("poster_card_movie_203", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value != null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value == null
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(150L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_movie_203", useUnmergedTree = true).assertIsFocused()

        // 2. SERIE TV: passa alla scheda Serie TV, naviga su poster_card_tv_502, verifica singolo GIÙ fino a Titoli Simili e ripristino focus alla chiusura
        composeRule.onNodeWithTag("tv_rail_series").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("poster_card_tv_500", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag("poster_card_tv_500", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_501", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_502", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("poster_card_tv_502", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            val ds = viewModel.detailState.value
            ds != null && !ds.isLoading && !ds.isLoadingEpisodes && ds.episodes.size >= 2
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()
        assertTrue(
            "Trailer strip must not be rendered in TV series detail",
            composeRule.onAllNodesWithTag("tmdb_trailers_section", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // Percorso completo verticale a singola pressione GIÙ: Play -> Cast -> Stagione -> Ep1 -> Ep2 -> Titoli Simili
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cast_card_11", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("cast_card_11", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_season_chip_1", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("detail_season_chip_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("episode_card_1_1", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("episode_card_1_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("episode_card_1_2", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("episode_card_1_2", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_900", useUnmergedTree = true).assertIsFocused()

        // 1° Back torna su detail_play_button, 2° Back chiude e ripristina focus su poster_card_tv_502
        composeRule.onNodeWithTag("poster_card_tv_900", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true).assertIsFocused()

        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value == null
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(150L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster_card_tv_502", useUnmergedTree = true).assertIsFocused()
    }

    private class FullMockTmdbServer {
        private var socket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()
        val failDiscover = AtomicBoolean(false)
        val discoverDelayMs = java.util.concurrent.atomic.AtomicLong(0L)

        val port: Int
            get() = socket?.localPort ?: error("Server not started")

        fun start() {
            socket = ServerSocket(0, 32, InetAddress.getLoopbackAddress())
            val serverSocket = socket!!
            executor.submit {
                while (!serverSocket.isClosed) {
                    try {
                        val client = serverSocket.accept()
                        executor.submit { handleClient(client) }
                    } catch (_: SocketException) {
                        break
                    }
                }
            }
        }

        fun stop() {
            runCatching { socket?.close() }
            executor.shutdownNow()
        }

        private fun movieListJson(startId: Int, count: Int, withDuplicateAtIndex1: Boolean = false): String {
            val items = (0 until count).map { i ->
                val id = startId + i
                """{"id":$id,"media_type":"movie","title":"Film $id","overview":"Trama molto lunga del film $id per testare lo scrolling verticale nella scheda dettagli Android TV e verificare i target di focus offscreen.","poster_path":"/p$id.jpg","backdrop_path":"/b$id.jpg","vote_average":8.2,"release_date":"2024-05-01","genre_ids":[28,878]}"""
            }.toMutableList()
            if (withDuplicateAtIndex1 && items.isNotEmpty()) {
                // Posiziona il duplicato a indice 1 così entra immediatamente nel viewport 1280dp
                items.add(1.coerceAtMost(items.size), items.first())
            }
            return """{"page":1,"total_pages":1,"results":[${items.joinToString(",")}]}"""
        }

        private fun tvListJson(startId: Int, count: Int, withDuplicateAtIndex1: Boolean = false): String {
            val items = (0 until count).map { i ->
                val id = startId + i
                """{"id":$id,"media_type":"tv","name":"Serie $id","overview":"Trama serie $id","poster_path":"/p$id.jpg","backdrop_path":"/b$id.jpg","vote_average":8.5,"first_air_date":"2024-01-10","genre_ids":[10765]}"""
            }.toMutableList()
            if (withDuplicateAtIndex1 && items.isNotEmpty()) {
                items.add(1.coerceAtMost(items.size), items.first())
            }
            return """{"page":1,"total_pages":1,"results":[${items.joinToString(",")}]}"""
        }

        private fun handleClient(client: Socket) {
            client.use { s ->
                val input = BufferedInputStream(s.getInputStream())
                val requestLine = readLine(input) ?: return
                while (true) {
                    val h = readLine(input) ?: break
                    if (h.isEmpty()) break
                }
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val path = parts[1]

                val genresJson = TmdbGenreMap.defaultMovieGenres.joinToString(",") {
                    """{"id":${it.id},"name":"${it.name}"}"""
                }

                when {
                    path.startsWith("/3/genre/movie/list") -> {
                        writeJson(s, 200, """{"genres":[$genresJson]}""")
                    }
                    path.startsWith("/3/trending/all/day") -> {
                        writeJson(s, 200, movieListJson(100, 10, withDuplicateAtIndex1 = true))
                    }
                    path.startsWith("/3/trending/movie/week") -> {
                        writeJson(s, 200, movieListJson(200, 8, withDuplicateAtIndex1 = true))
                    }
                    path.startsWith("/3/trending/tv/week") -> {
                        writeJson(s, 200, tvListJson(500, 8, withDuplicateAtIndex1 = true))
                    }
                    path.startsWith("/3/movie/popular") -> {
                        writeJson(s, 200, movieListJson(300, 8))
                    }
                    path.startsWith("/3/tv/popular") -> {
                        writeJson(s, 200, tvListJson(600, 8))
                    }
                    path.startsWith("/3/movie/now_playing") -> {
                        writeJson(s, 200, movieListJson(400, 8))
                    }
                    path.startsWith("/3/movie/top_rated") -> {
                        writeJson(s, 200, movieListJson(450, 8))
                    }
                    path.startsWith("/3/search/multi") -> {
                        writeJson(s, 200, movieListJson(700, 6, withDuplicateAtIndex1 = true))
                    }
                    path.startsWith("/3/discover/movie") -> {
                        val delayMs = discoverDelayMs.get()
                        if (delayMs > 0L) Thread.sleep(delayMs)
                        when {
                            failDiscover.get() -> writeJson(s, 503, """{"status_message":"Errore"}""")
                            path.contains("with_genres=99") -> writeJson(s, 200, """{"page":1,"total_pages":1,"results":[]}""")
                            path.contains("with_genres=878") -> writeJson(s, 200, movieListJson(8780, 10, withDuplicateAtIndex1 = true))
                            else -> writeJson(s, 200, movieListJson(2800, 10))
                        }
                    }
                    path.contains("/season/") -> {
                        writeJson(
                            s,
                            200,
                            """
                            {
                              "id":5001,
                              "season_number":1,
                              "episodes":[
                                {"id":1001,"name":"Episodio Pilota","overview":"Primo episodio","episode_number":1,"season_number":1,"runtime":48,"still_path":"/ep1.jpg"},
                                {"id":1001,"name":"Episodio Pilota Dup","overview":"Duplicato","episode_number":1,"season_number":1,"runtime":48,"still_path":"/ep1.jpg"},
                                {"id":1002,"name":"Secondo Episodio","overview":"Secondo episodio","episode_number":2,"season_number":1,"runtime":50,"still_path":"/ep2.jpg"}
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    path.startsWith("/3/tv/") -> {
                        val longOverview = "Questa è una serie TV con trama dettagliata e tante stagioni disponibili in catalogo. ".repeat(8)
                        writeJson(
                            s,
                            200,
                            """
                            {
                              "id":500,
                              "name":"Serie Dettaglio Test",
                              "overview":"$longOverview",
                              "poster_path":"/p500.jpg",
                              "backdrop_path":"/b500.jpg",
                              "vote_average":8.8,
                              "first_air_date":"2024-02-01",
                              "genres":[{"id":10765,"name":"Sci-Fi & Fantasy"}],
                              "seasons":[{"id":1,"season_number":1,"episode_count":2,"name":"Stagione 1"}],
                              "credits":{"cast":[
                                {"id":11,"name":"Attore Serie 1","character":"Protagonista"},
                                {"id":11,"name":"Attore Serie 1","character":"Duplicato"}
                              ]},
                              "similar":${tvListJson(900, 4, withDuplicateAtIndex1 = true)},
                              "videos":{"results":[
                                {"id":"v1","key":"TcMBFSGVi1c","name":"Trailer Serie","site":"YouTube","type":"Trailer","official":true,"iso_639_1":"it"}
                              ]}
                            }
                            """.trimIndent()
                        )
                    }
                    path.startsWith("/3/movie/") -> {
                        val longOverview = "Questa è una trama cinematografica estremamente lunga e dettagliata. ".repeat(14)
                        writeJson(
                            s,
                            200,
                            """
                            {
                              "id":200,
                              "title":"Film Dettaglio Test",
                              "overview":"$longOverview",
                              "poster_path":"/p200.jpg",
                              "backdrop_path":"/b200.jpg",
                              "vote_average":8.6,
                              "release_date":"2024-06-01",
                              "runtime":142,
                              "genres":[{"id":28,"name":"Azione"},{"id":878,"name":"Fantascienza"}],
                              "credits":{"cast":[
                                {"id":1,"name":"Attore Uno","character":"Ruolo A"},
                                {"id":1,"name":"Attore Uno","character":"Ruolo B"},
                                {"id":2,"name":"Attore Due","character":"Ruolo C"}
                              ]},
                              "similar":${movieListJson(900, 5, withDuplicateAtIndex1 = true)},
                              "videos":{"results":[
                                {"id":"v1","key":"TcMBFSGVi1c","name":"Trailer Ufficiale","site":"YouTube","type":"Trailer","official":true,"iso_639_1":"it"}
                              ]}
                            }
                            """.trimIndent()
                        )
                    }
                    else -> writeJson(s, 200, """{"page":1,"total_pages":1,"results":[]}""")
                }
            }
        }

        private fun readLine(input: BufferedInputStream): String? {
            val buffer = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
                if (b == '\n'.code) return buffer.toString("UTF-8").trimEnd('\r')
                buffer.write(b)
            }
        }

        private fun writeJson(socket: Socket, code: Int, json: String) {
            val bytes = json.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 $code OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            val out = socket.getOutputStream()
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }
}
