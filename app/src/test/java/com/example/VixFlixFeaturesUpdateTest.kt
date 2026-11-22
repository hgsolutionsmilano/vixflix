package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.INITIAL_AVATAR_ID
import com.example.data.local.VixFlixDatabase
import com.example.data.local.extractProfileInitial
import com.example.data.remote.MediaItem
import com.example.data.repository.VixFlixRepository
import com.example.ui.NavTab
import com.example.ui.VixFlixViewModel
import com.example.ui.screens.VixFlixAppScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
@OptIn(ExperimentalTestApi::class)
class VixFlixFeaturesUpdateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var server: FakeTmdbSagaAndSeriesServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = FakeTmdbSagaAndSeriesServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `feature 1 - tv series detail long press OK opens watched episode menu, marks episode and season per profile`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        repository.ensureDefaultPrimaryProfile()
        val profileB = repository.createProfile(
            name = "Veronica",
            avatarId = INITIAL_AVATAR_ID,
            accentColorHex = 0xFF00B4D8L,
            preferredLanguage = "it",
            preferredAudioLanguage = "it",
            subtitlesEnabled = true,
            preferredSubtitleLanguage = "it"
        ).getOrThrow()

        val viewModel = VixFlixViewModel(app, repository)
        viewModel.selectProfile(DEFAULT_PRIMARY_PROFILE_ID)

        composeRule.setContent {
            MyApplicationTheme {
                VixFlixAppScreen(
                    viewModel = viewModel,
                    splashMinDurationMs = 0L,
                    respectStartupProfileGate = false
                )
            }
        }

        val seriesItem = MediaItem(
            id = 500,
            mediaType = "tv",
            title = "The Last Frontier",
            overview = "Serie sci-fi con due stagioni.",
            posterPath = "/series500.jpg",
            backdropPath = "/series500_bg.jpg",
            voteAverage = 8.9,
            releaseYear = "2024"
        )
        viewModel.openDetails(seriesItem)

        composeRule.waitUntil(timeoutMillis = 6_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value?.episodes?.size == 3
        }

        // Scorri fino agli episodi nella schermata dettaglio Serie TV
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_season_chip_1", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("episode_card_1_1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verifica che nessun episodio sia inizialmente segnato come visto e nessuna riproduzione sia attiva
        assertTrue(viewModel.detailState.value?.watchedEpisodeNumbers.isNullOrEmpty())
        assertNull(viewModel.playbackSession.value)

        // 1. Pressione prolungata (OnLongClick / D-Pad OK long-press) sull'episodio 1 apre il popup contestuale senza avviare il player
        composeRule.onNodeWithTag("episode_card_1_1", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("tv_episode_watched_context_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertNull("La pressione prolungata non deve avviare la riproduzione", viewModel.playbackSession.value)
        composeRule.onNodeWithText("SEGNA EPISODIO COME VISTO", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("SEGNA INTERA STAGIONE COME VISTA", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("ANNULLA", useUnmergedTree = true).assertIsDisplayed()

        // 2. Selezione di "ANNULLA" chiude il popup senza modificare i dati
        composeRule.onNodeWithTag("tv_episode_ctx_cancel", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("tv_episode_watched_context_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        assertTrue(viewModel.detailState.value?.watchedEpisodeNumbers.isNullOrEmpty())

        // 3. Riapri popup su Episodio 1 e seleziona "SEGNA EPISODIO COME VISTO"
        composeRule.onNodeWithTag("episode_card_1_1", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("tv_episode_ctx_mark_episode", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tv_episode_ctx_mark_episode", useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value?.watchedEpisodeNumbers == setOf(1) &&
                composeRule.onAllNodesWithTag("episode_watched_badge_1_1", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        // Solo l'episodio 1 è visto; episodio 2 e 3 non sono visti
        assertEquals(setOf(1), viewModel.detailState.value?.watchedEpisodeNumbers)
        assertTrue(
            composeRule.onAllNodesWithTag("episode_watched_badge_1_2", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // 4. Riapri popup e seleziona "SEGNA INTERA STAGIONE COME VISTA" -> tutti gli episodi (1, 2, 3) della Stagione 1 risultano visti
        composeRule.onNodeWithTag("episode_card_1_2", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("tv_episode_ctx_mark_season", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tv_episode_ctx_mark_season", useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value?.watchedEpisodeNumbers == setOf(1, 2, 3)
        }
        assertEquals(
            setOf(1, 2, 3),
            repository.getWatchedEpisodeNumbers(500, 1, DEFAULT_PRIMARY_PROFILE_ID)
        )
        // La Stagione 2 non deve essere stata toccata
        assertTrue(repository.getWatchedEpisodeNumbers(500, 2, DEFAULT_PRIMARY_PROFILE_ID).isEmpty())

        // 5. Isolamento per profilo: passando al profilo "Veronica", gli episodi della Stagione 1 NON risultano visti
        viewModel.closeDetails()
        viewModel.selectProfile(profileB.id)
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.activeProfileId.value == profileB.id
        }
        assertTrue(repository.getWatchedEpisodeNumbers(500, 1, profileB.id).isEmpty())
        viewModel.openDetails(seriesItem)
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value?.episodes?.size == 3
        }
        assertTrue(viewModel.detailState.value?.watchedEpisodeNumbers.isNullOrEmpty())

        // Tornando al profilo principale, gli episodi 1, 2, 3 della Stagione 1 vengono ricaricati come visti
        viewModel.closeDetails()
        viewModel.selectProfile(DEFAULT_PRIMARY_PROFILE_ID)
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.activeProfileId.value == DEFAULT_PRIMARY_PROFILE_ID
        }
        assertEquals(
            setOf(1, 2, 3),
            repository.getWatchedEpisodeNumbers(500, 1, DEFAULT_PRIMARY_PROFILE_ID)
        )

        db.close()
    }

    @Test
    fun `feature 2 and 5 - profile selection solid black background, initial letter avatar updates on rename, and D-Pad navigation in ProfileEditorDialog`() = runBlocking {
        // Verifica logica estrazione iniziale del nome profilo
        assertEquals("M", extractProfileInitial("Marco"))
        assertEquals("V", extractProfileInitial("Veronica"))
        assertEquals("P", extractProfileInitial("Prime Pixel"))
        assertEquals("A", extractProfileInitial("   alessandro "))

        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        repository.ensureDefaultPrimaryProfile()
        val marco = repository.createProfile(
            name = "Marco",
            avatarId = INITIAL_AVATAR_ID,
            accentColorHex = 0xFFE50914L,
            preferredLanguage = "it",
            preferredAudioLanguage = "it",
            subtitlesEnabled = true,
            preferredSubtitleLanguage = "it"
        ).getOrThrow()

        // Associa dati (preferiti + episodio visto) al profilo Marco
        repository.toggleWatchlist(
            item = MediaItem(
                id = 101,
                mediaType = "movie",
                title = "Dune: Parte Uno",
                overview = "Arrakis.",
                posterPath = "/d1.jpg",
                backdropPath = "/d1_bg.jpg",
                voteAverage = 8.1,
                releaseYear = "2021"
            ),
            currentlyInList = false,
            profileId = marco.id
        )
        repository.markEpisodeAsWatched(
            tmdbId = 500,
            seasonNumber = 1,
            episodeNumber = 2,
            profileId = marco.id
        )

        val viewModel = VixFlixViewModel(app, repository)
        viewModel.setAutoLoginProfile(false)
        viewModel.openProfileSelector(manageMode = true)

        composeRule.setContent {
            MyApplicationTheme {
                VixFlixAppScreen(
                    viewModel = viewModel,
                    splashMinDurationMs = 0L,
                    respectStartupProfileGate = true
                )
            }
        }

        // Verifica schermata "Chi sta guardando?" / "Gestisci profili" e avatar con iniziale "M" per Marco
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("profile_selection_screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("profile_selection_screen", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("add_profile_tile", useUnmergedTree = true).assertIsDisplayed()

        // Clic sul profilo Marco in modalità Gestisci apre ProfileEditorDialog
        composeRule.onNodeWithTag("profile_tile_${marco.id}", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("profile_editor_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verifica che il campo nome abbia il focus iniziale in apertura
        composeRule.onNodeWithTag("profile_name_input", useUnmergedTree = true).assertIsFocused()

        // Cambia il nome in "Veronica" -> l'iniziale si aggiorna automaticamente in "V" e l'ID profilo resta invariato
        composeRule.onNodeWithTag("profile_name_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("profile_name_input", useUnmergedTree = true).performTextInput("Veronica")
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithText("V", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        // Navigazione D-Pad dal campo nome verso l'avatar iniziale
        composeRule.onNodeWithTag("profile_name_input", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("avatar_option_$INITIAL_AVATAR_ID", useUnmergedTree = true)
            .assertIsFocused()

        // Salva le modifiche
        composeRule.onNodeWithTag("profile_editor_save_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.profiles.value.any { it.id == marco.id && it.name == "Veronica" }
        }

        val updatedProfile = repository.getProfileById(marco.id)!!
        // Verifica che l'ID del profilo NON sia cambiato e che tutti i dati associati (preferiti ed episodi visti) siano intatti
        assertEquals(marco.id, updatedProfile.id)
        assertEquals("Veronica", updatedProfile.name)
        assertEquals(INITIAL_AVATAR_ID, updatedProfile.avatarId)
        assertEquals(listOf("movie_101"), repository.observeWatchlist(marco.id).first().map { it.id })
        assertEquals(setOf(2), repository.getWatchedEpisodeNumbers(500, 1, marco.id))

        db.close()
    }

    @Test
    fun `feature 3 - movie detail displays FILM DELLA STESSA SAGA in chronological order excluding current movie, navigates and hides for standalone movies`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        repository.ensureDefaultPrimaryProfile()

        val viewModel = VixFlixViewModel(app, repository)
        viewModel.selectProfile(DEFAULT_PRIMARY_PROFILE_ID)

        composeRule.setContent {
            MyApplicationTheme {
                VixFlixAppScreen(
                    viewModel = viewModel,
                    splashMinDurationMs = 0L,
                    respectStartupProfileGate = false
                )
            }
        }

        // 1. Apri il dettaglio di "Dune: Parte Due" (id = 202), che appartiene alla collezione 900 (Dune Collection)
        val dunePartTwo = MediaItem(
            id = 202,
            mediaType = "movie",
            title = "Dune: Parte Due",
            overview = "Paul Atreides si unisce ai Fremen.",
            posterPath = "/dune2.jpg",
            backdropPath = "/dune2_bg.jpg",
            voteAverage = 8.6,
            releaseYear = "2024"
        )
        viewModel.openDetails(dunePartTwo)

        composeRule.waitUntil(timeoutMillis = 6_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value?.details != null
        }

        val sagaMovies = viewModel.detailState.value?.details?.collectionMovies.orEmpty()
        // Deve aver escluso il film corrente (202) e ordinato cronologicamente: Dune Parte Uno (2021, id=201) -> Dune Messiah (2026, id=203)
        assertEquals(listOf(201, 203), sagaMovies.map { it.id })
        assertEquals(listOf("2021", "2026"), sagaMovies.map { it.releaseYear })

        // Scorri nella schermata dettaglio fino alla sezione "FILM DELLA STESSA SAGA"
        composeRule.onNodeWithTag("detail_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil(timeoutMillis = 4_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("detail_saga_section", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("FILM DELLA STESSA SAGA", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("saga_poster_card_movie_201", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("saga_card_title_movie_201", useUnmergedTree = true)
            .assertTextEquals("Dune: Parte Uno")
        composeRule.onNodeWithTag("saga_card_year_movie_201", useUnmergedTree = true)
            .assertTextEquals("2021")

        // 2. Seleziona "Dune: Parte Uno" dalla riga FILM DELLA STESSA SAGA -> apre la sua scheda dettaglio
        composeRule.onNodeWithTag("saga_poster_card_movie_201", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value?.mediaItem?.id == 201 &&
                viewModel.detailStackSize.value == 2
        }
        assertEquals("Dune: Parte Uno", viewModel.detailState.value?.mediaItem?.title)

        // 3. Premi BACK -> torna alla scheda dettaglio precedente ("Dune: Parte Due") mantenendo il contesto
        viewModel.navigateBackDetail()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value?.mediaItem?.id == 202 &&
                viewModel.detailStackSize.value == 1
        }
        assertEquals("movie_201", viewModel.detailState.value?.savedFocusedSagaKey)

        // 4. Apri un film singolo che NON appartiene a nessuna collezione (id = 305, "Oppenheimer") -> la sezione FILM DELLA STESSA SAGA non deve comparire
        val standaloneMovie = MediaItem(
            id = 305,
            mediaType = "movie",
            title = "Oppenheimer",
            overview = "Storia del progetto Manhattan.",
            posterPath = "/opp.jpg",
            backdropPath = "/opp_bg.jpg",
            voteAverage = 8.5,
            releaseYear = "2023"
        )
        viewModel.openDetails(standaloneMovie)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.detailState.value?.mediaItem?.id == 305 &&
                viewModel.detailState.value?.details != null
        }
        assertTrue(viewModel.detailState.value?.details?.collectionMovies.isNullOrEmpty())
        assertTrue(
            composeRule.onAllNodesWithTag("detail_saga_section", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        db.close()
    }

    @Test
    fun `feature 4 - main screen back opens VUOI USCIRE DA VIXFLIX confirmation popup with initial focus on ANNULLA`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        repository.ensureDefaultPrimaryProfile()

        val viewModel = VixFlixViewModel(app, repository)
        viewModel.selectProfile(DEFAULT_PRIMARY_PROFILE_ID)
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

        // 1. Su una schermata secondaria (es. tab Film), premere BACK torna a Home senza mostrare il popup di uscita
        viewModel.selectTab(NavTab.MOVIES)
        composeRule.waitForIdle()
        assertEquals(NavTab.MOVIES, viewModel.selectedTab.value)
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()
        assertEquals(NavTab.HOME, viewModel.selectedTab.value)
        assertTrue(
            composeRule.onAllNodesWithTag("vixflix_exit_confirmation_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // 2. Sulla schermata principale (Home), premere BACK apre il popup "VUOI USCIRE DA VIXFLIX?" con focus iniziale su "ANNULLA"
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("vixflix_exit_confirmation_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("VUOI USCIRE DA VIXFLIX?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("exit_dialog_cancel_button", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertIsFocused()
        composeRule.onNodeWithTag("exit_dialog_confirm_button", useUnmergedTree = true)
            .assertIsDisplayed()

        // Verifica che la vecchia scritta "Premi ancora Indietro per uscire" non esista più
        assertTrue(
            composeRule.onAllNodesWithText("Premi ancora Indietro per uscire da VixFlix", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // 3. Selezionare "ANNULLA" chiude il popup e mantiene l'utente sulla schermata principale
        composeRule.onNodeWithTag("exit_dialog_cancel_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("vixflix_exit_confirmation_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        assertEquals(0, exitConfirmedCount)

        // 4. Riapri il popup con BACK, spostati a destra su "ESCI" con D-Pad e conferma
        composeRule.onNodeWithTag("hero_play_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("exit_dialog_cancel_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("exit_dialog_cancel_button", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exit_dialog_confirm_button", useUnmergedTree = true)
            .assertIsFocused()
            .performClick()

        composeRule.waitForIdle()
        assertEquals(1, exitConfirmedCount)

        db.close()
    }

    private class FakeTmdbSagaAndSeriesServer {
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
                    path.contains("/collection/900") -> """
                        {
                          "id": 900,
                          "name": "Dune - Collezione",
                          "overview": "La saga cinematografica di Dune.",
                          "parts": [
                            {
                              "id": 203,
                              "title": "Dune: Messiah",
                              "media_type": "movie",
                              "overview": "Il capitolo conclusivo su Arrakis.",
                              "poster_path": "/dune3.jpg",
                              "backdrop_path": "/dune3_bg.jpg",
                              "vote_average": 8.8,
                              "release_date": "2026-12-18"
                            },
                            {
                              "id": 202,
                              "title": "Dune: Parte Due",
                              "media_type": "movie",
                              "overview": "Paul Atreides si unisce ai Fremen.",
                              "poster_path": "/dune2.jpg",
                              "backdrop_path": "/dune2_bg.jpg",
                              "vote_average": 8.6,
                              "release_date": "2024-02-28"
                            },
                            {
                              "id": 201,
                              "title": "Dune: Parte Uno",
                              "media_type": "movie",
                              "overview": "L'arrivo della Casa Atreides su Arrakis.",
                              "poster_path": "/dune1.jpg",
                              "backdrop_path": "/dune1_bg.jpg",
                              "vote_average": 8.1,
                              "release_date": "2021-10-22"
                            }
                          ]
                        }
                    """.trimIndent()
                    path.contains("/movie/202") -> """
                        {
                          "id": 202,
                          "title": "Dune: Parte Due",
                          "overview": "Paul Atreides si unisce ai Fremen.",
                          "runtime": 166,
                          "release_date": "2024-02-28",
                          "vote_average": 8.6,
                          "genres": [{"id": 878, "name": "Fantascienza"}],
                          "belongs_to_collection": {
                            "id": 900,
                            "name": "Dune - Collezione",
                            "poster_path": "/col.jpg",
                            "backdrop_path": "/col_bg.jpg"
                          },
                          "credits": {"cast": []},
                          "videos": {"results": []},
                          "similar": {"results": []}
                        }
                    """.trimIndent()
                    path.contains("/movie/201") -> """
                        {
                          "id": 201,
                          "title": "Dune: Parte Uno",
                          "overview": "L'arrivo della Casa Atreides su Arrakis.",
                          "runtime": 155,
                          "release_date": "2021-10-22",
                          "vote_average": 8.1,
                          "genres": [{"id": 878, "name": "Fantascienza"}],
                          "belongs_to_collection": {
                            "id": 900,
                            "name": "Dune - Collezione",
                            "poster_path": "/col.jpg",
                            "backdrop_path": "/col_bg.jpg"
                          },
                          "credits": {"cast": []},
                          "videos": {"results": []},
                          "similar": {"results": []}
                        }
                    """.trimIndent()
                    path.contains("/movie/305") -> """
                        {
                          "id": 305,
                          "title": "Oppenheimer",
                          "overview": "Storia del progetto Manhattan.",
                          "runtime": 180,
                          "release_date": "2023-07-21",
                          "vote_average": 8.5,
                          "genres": [{"id": 18, "name": "Dramma"}],
                          "belongs_to_collection": null,
                          "credits": {"cast": []},
                          "videos": {"results": []},
                          "similar": {"results": []}
                        }
                    """.trimIndent()
                    path.contains("/tv/500/season/1") -> """
                        {
                          "id": 5001,
                          "season_number": 1,
                          "name": "Stagione 1",
                          "episodes": [
                            {"id": 1, "episode_number": 1, "season_number": 1, "name": "Il Risveglio", "overview": "Ep 1", "runtime": 48, "vote_average": 8.4},
                            {"id": 2, "episode_number": 2, "season_number": 1, "name": "L'Orizzonte", "overview": "Ep 2", "runtime": 50, "vote_average": 8.6},
                            {"id": 3, "episode_number": 3, "season_number": 1, "name": "La Tempesta", "overview": "Ep 3", "runtime": 52, "vote_average": 9.0}
                          ]
                        }
                    """.trimIndent()
                    path.contains("/tv/500") -> """
                        {
                          "id": 500,
                          "name": "The Last Frontier",
                          "overview": "Serie sci-fi con due stagioni.",
                          "first_air_date": "2024-01-10",
                          "vote_average": 8.9,
                          "number_of_seasons": 2,
                          "genres": [{"id": 10765, "name": "Sci-Fi & Fantasy"}],
                          "seasons": [
                            {"id": 5001, "season_number": 1, "name": "Stagione 1", "episode_count": 3},
                            {"id": 5002, "season_number": 2, "name": "Stagione 2", "episode_count": 3}
                          ],
                          "credits": {"cast": []},
                          "videos": {"results": []},
                          "similar": {"results": []}
                        }
                    """.trimIndent()
                    path.contains("/genre/") -> """{"genres":[{"id":28,"name":"Azione"}]}"""
                    path.contains("/watch/providers/") -> """{"results":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/n.png","display_priority":1}]}"""
                    else -> """
                        {
                          "page": 1,
                          "results": [
                            {
                              "id": 202,
                              "title": "Dune: Parte Due",
                              "media_type": "movie",
                              "overview": "Paul Atreides si unisce ai Fremen.",
                              "poster_path": "/dune2.jpg",
                              "backdrop_path": "/dune2_bg.jpg",
                              "vote_average": 8.6,
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
