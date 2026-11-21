package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WatchProgressEntity
import com.example.data.local.VixFlixDatabase
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbCastMemberDto
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbSeasonSummaryDto
import com.example.data.remote.formatTmdbDateItalian
import com.example.data.repository.VixFlixRepository
import com.example.ui.DetailUiState
import com.example.ui.VixFlixViewModel
import com.example.ui.components.ContinueWatchingSection
import com.example.ui.components.MediaPosterCard
import com.example.ui.screens.MediaDetailScreen
import com.example.ui.screens.VixFlixAppScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlin.math.abs
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
class ActorAndInterfaceUpdatesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var server: FakeActorTmdbServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = FakeActorTmdbServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `poster card hides green action text globally when unfocused and focused while keeping 130dp size`() {
        val sampleMovie = MediaItem(
            id = 7001,
            mediaType = "movie",
            title = "Interstellar",
            overview = "Viaggio nello spazio",
            posterPath = "/poster_interstellar.jpg",
            backdropPath = "/backdrop_interstellar.jpg",
            voteAverage = 8.6,
            releaseYear = "2014"
        )
        var clicked = false

        composeRule.setContent {
            MyApplicationTheme {
                MediaPosterCard(
                    item = sampleMovie,
                    onClick = { clicked = true }
                )
            }
        }

        val cardNode = composeRule.onNodeWithTag("poster_card_movie_7001")
        cardNode.assertIsDisplayed()
        val bounds = cardNode.getUnclippedBoundsInRoot()
        assertTrue(
            "La locandina deve mantenere la larghezza standard 130dp (trovato ${bounds.width})",
            abs(bounds.width.value - 130f) <= 2f
        )

        // Nessuna scritta verde né da non focalizzata né da focalizzata
        assertEquals(0, composeRule.onAllNodesWithText("OK per aprire", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("SELEZIONA", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        cardNode.requestFocus()
        composeRule.waitForIdle()
        cardNode.assertIsFocused()

        composeRule.onNodeWithText("Interstellar").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("OK per aprire", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("SELEZIONA", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        cardNode.performClick()
        assertTrue(clicked)
    }

    @Test
    fun `continue watching card hides info line and green text while preserving title progress and resume action`() {
        val sampleProgress = WatchProgressEntity(
            id = "tv_8801",
            tmdbId = 8801,
            mediaType = "tv",
            title = "Dark",
            posterPath = "/dark_poster.jpg",
            backdropPath = "/dark_backdrop.jpg",
            overview = "Mistero nel tempo",
            voteAverage = 8.4,
            releaseYear = "2017",
            seasonNumber = 2,
            episodeNumber = 4,
            episodeTitle = "I viaggiatori",
            positionMs = 1_260_000L, // 21 min
            durationMs = 3_000_000L  // 50 min -> 42%
        )
        var resumedEntry: WatchProgressEntity? = null

        composeRule.setContent {
            MyApplicationTheme {
                ContinueWatchingSection(
                    items = listOf(sampleProgress),
                    isTvLayout = true,
                    onResume = { resumedEntry = it },
                    onOpenDetails = {},
                    onRemove = {}
                )
            }
        }

        val cwCard = composeRule.onNodeWithTag("cw_card_tv_8801")
        cwCard.assertIsDisplayed()
        composeRule.onNodeWithText("Dark").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()

        // Verifica assenza totale di riga informativa sotto il titolo e scritta verde
        assertEquals(0, composeRule.onAllNodesWithText("OK Riprendi", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Riprendi da", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("I viaggiatori", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        // Anche in stato focalizzato su Android TV non compare alcuna scritta verde
        cwCard.requestFocus()
        composeRule.waitForIdle()
        cwCard.assertIsFocused()
        assertEquals(0, composeRule.onAllNodesWithText("OK Riprendi", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("SELEZIONA", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        // Ripresa riproduzione invariata
        cwCard.performClick()
        assertEquals("tv_8801", resumedEntry?.id)
        assertEquals(1_260_000L, resumedEntry?.positionMs)
    }

    @Test
    fun `repository fetches actor details with English bio fallback and deduplicated Movie plus TV filmography`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = VixFlixRepository(
                dao = db.vixFlixDao(),
                tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
            )

            assertEquals("11 novembre 1974", formatTmdbDateItalian("1974-11-11"))

            // Attore 501 ha biografia italiana vuota -> fallback automatico alla biografia inglese
            val actorDetails = repo.fetchActorDetails(
                actorId = 501,
                fallbackName = "Leonardo DiCaprio",
                fallbackProfilePath = "/leo.jpg"
            ).getOrThrow()

            assertEquals("Leonardo DiCaprio", actorDetails.name)
            assertEquals("1974-11-11", actorDetails.birthday)
            assertEquals("Los Angeles, California, USA", actorDetails.placeOfBirth)
            assertTrue(actorDetails.biography.contains("Academy Award-winning actor"))
            assertTrue(actorDetails.filmography.any { it.uniqueKey == "movie_902" && it.title == "Shutter Island" })
            assertTrue(actorDetails.filmography.any { it.uniqueKey == "tv_903" && it.title == "The Crowded Room" })
            assertEquals(2, actorDetails.filmography.size)

            // Attore inesistente (9999) -> errore gestito senza crash
            val failed = repo.fetchActorDetails(actorId = 9999, fallbackName = "Unknown")
            assertTrue(failed.isFailure)
        } finally {
            db.close()
        }
    }

    @Test
    fun `tv series detail renders enlarged 16to9 episode thumbnails with complete metadata and working playback`() {
        val seriesItem = MediaItem(
            id = 4040,
            mediaType = "tv",
            title = "Severance",
            overview = "Una procedura separa memoria lavorativa e privata.",
            posterPath = "/sev_poster.jpg",
            backdropPath = "/sev_backdrop.jpg",
            voteAverage = 8.7,
            releaseYear = "2022"
        )
        val ep1 = TmdbEpisodeDto(
            id = 1,
            episodeNumber = 1,
            seasonNumber = 1,
            name = "Buone notizie sull'inferno",
            overview = "Mark guida un team nella divisione Macrodata Refinement mentre un nuovo collega mette in discussione le regole.",
            stillPath = "/ep1_still.jpg",
            runtime = 57,
            voteAverage = 8.5
        )
        val detailState = DetailUiState(
            mediaItem = seriesItem,
            isLoading = false,
            details = MediaDetails(
                item = seriesItem,
                genres = listOf("Fantascienza", "Thriller"),
                tagline = "Lavora per vivere",
                runtimeMinutes = null,
                numberOfSeasons = 1,
                seasons = listOf(TmdbSeasonSummaryDto(id = 10, seasonNumber = 1, name = "Stagione 1", episodeCount = 1)),
                cast = emptyList(),
                similar = emptyList(),
                trailerKey = null
            ),
            selectedSeason = 1,
            episodes = listOf(ep1),
            isLoadingEpisodes = false
        )
        var playedEpisode: Triple<Int, Int, String?>? = null

        composeRule.setContent {
            MyApplicationTheme {
                MediaDetailScreen(
                    state = detailState,
                    savedProgress = null,
                    isInWatchlist = false,
                    onClose = {},
                    onPlayMain = {},
                    onPlayEpisode = { s, e, t -> playedEpisode = Triple(s, e, t) },
                    onOpenYouTubeTrailerPopup = {},
                    onSelectYouTubeTrailer = {},
                    onRetryYouTubeTrailer = {},
                    onCloseTrailer = {},
                    onSelectSeason = {},
                    onToggleWatchlist = {},
                    onSelectSimilar = {}
                )
            }
        }

        // Scorri fino alla card dell'episodio nel LazyColumn della scheda serie
        composeRule.onNodeWithTag("media_detail_lazy_column")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("episode_card_1_1"))
        composeRule.waitForIdle()

        val epCard = composeRule.onNodeWithTag("episode_card_1_1")
        epCard.assertIsDisplayed()

        val thumbBounds = composeRule.onNodeWithTag("episode_thumbnail_1_1").getUnclippedBoundsInRoot()
        assertTrue(
            "La miniatura episodio su TV deve essere sensibilmente ingrandita (>= 240dp, trovato ${thumbBounds.width})",
            thumbBounds.width >= 240.dp
        )
        assertTrue(
            "L'altezza miniatura episodio su TV deve rispettare il formato 16:9 (>= 136dp, trovato ${thumbBounds.height})",
            thumbBounds.height >= 136.dp
        )
        val aspectRatio = thumbBounds.width.value / thumbBounds.height.value
        assertTrue(
            "La proporzione miniatura deve restare vicina a 16:9 (trovato $aspectRatio)",
            aspectRatio in 1.68f..1.85f
        )

        composeRule.onNodeWithText("1. Buone notizie sull'inferno").assertIsDisplayed()
        composeRule.onNodeWithText("• 57 min").assertIsDisplayed()

        epCard.performClick()
        assertEquals(Triple(1, 1, "Buone notizie sull'inferno"), playedEpisode)
    }

    @Test
    fun `full cast to actor to filmography to existing detail and BACK stack restores exact scroll and focus`() {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        val viewModel = VixFlixViewModel(app, repo)

        try {
            composeRule.setContent {
                MyApplicationTheme {
                    VixFlixAppScreen(
                        viewModel = viewModel,
                        splashMinDurationMs = 0L,
                        respectStartupProfileGate = false
                    )
                }
            }

            // 1. Apri Film A (Inception, movie_901) nella schermata dettaglio
            val filmA = MediaItem(
                id = 901,
                mediaType = "movie",
                title = "Inception",
                overview = "Un ladro entra nei sogni.",
                posterPath = "/inception.jpg",
                backdropPath = "/inception_bg.jpg",
                voteAverage = 8.8,
                releaseYear = "2010"
            )
            composeRule.runOnIdle { viewModel.openDetails(filmA) }

            composeRule.waitUntil(timeoutMillis = 6_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.detailState.value?.details?.cast?.isNotEmpty() == true
            }
            composeRule.waitForIdle()

            // Scorri al Cast Principale e clicca sull'attore Leonardo DiCaprio (id = 501)
            composeRule.onNodeWithTag("media_detail_lazy_column")
                .performScrollToNode(androidx.compose.ui.test.hasTestTag("cast_card_501"))
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("cast_card_501").assertIsDisplayed().performClick()

            // 2. Verifica apertura schermata dedicata Dettaglio Attore (stack = 2: Film A -> Attore X)
            composeRule.waitUntil(timeoutMillis = 6_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.actorDetailState.value?.details != null
            }
            composeRule.waitForIdle()

            assertEquals(2, viewModel.detailStackSize.value)
            assertEquals("Inception", viewModel.previousDetailTitle.value)
            assertNull(viewModel.detailState.value)
            assertNotNull(viewModel.actorDetailState.value)

            composeRule.onNodeWithTag("actor_detail_screen").assertIsDisplayed()
            composeRule.onNodeWithTag("actor_detail_name").assertIsDisplayed()
            composeRule.onNodeWithText("Leonardo DiCaprio").assertIsDisplayed()
            composeRule.onNodeWithTag("actor_detail_birthday").assertIsDisplayed()
            composeRule.onNodeWithTag("actor_detail_birthplace").assertIsDisplayed()
            composeRule.onNodeWithTag("actor_detail_biography").assertIsDisplayed()

            // Verifica filtri Filmografia (Film + Serie TV)
            composeRule.onNodeWithTag("actor_filter_movies").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("actor_filmography_item_movie_902").assertIsDisplayed()
            assertEquals(0, composeRule.onAllNodesWithTag("actor_filmography_item_tv_903").fetchSemanticsNodes().size)

            composeRule.onNodeWithTag("actor_filter_all").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("actor_filmography_item_tv_903").assertIsDisplayed()

            // 3. Seleziona Film B (Shutter Island, movie_902) dalla filmografia -> apre la STESSA MediaDetailScreen!
            composeRule.onNodeWithTag("poster_card_movie_902").performClick()

            composeRule.waitUntil(timeoutMillis = 6_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.detailState.value?.mediaItem?.uniqueKey == "movie_902" &&
                    viewModel.detailState.value?.isLoading == false
            }
            composeRule.waitForIdle()

            assertEquals(3, viewModel.detailStackSize.value)
            assertEquals("Leonardo DiCaprio", viewModel.previousDetailTitle.value)
            assertNull(viewModel.actorDetailState.value)
            composeRule.onNodeWithTag("media_detail_screen").assertIsDisplayed()
            composeRule.onNodeWithText("Shutter Island").assertIsDisplayed()
            composeRule.onNodeWithTag("detail_back_button").assertIsDisplayed()

            // 4. BACK da Film B -> torna ESATTAMENTE alla schermata Attore X (MAI alla Home!) con focus su Film B
            composeRule.onNodeWithTag("detail_back_button").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.actorDetailState.value?.actorId == 501
            }
            composeRule.waitForIdle()

            assertEquals(2, viewModel.detailStackSize.value)
            assertEquals("movie_902", viewModel.actorDetailState.value?.savedFocusedMediaKey)
            composeRule.onNodeWithTag("actor_detail_screen").assertIsDisplayed()

            composeRule.waitUntil(timeoutMillis = 4_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                runCatching {
                    composeRule.onNodeWithTag("poster_card_movie_902").assertIsFocused()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("poster_card_movie_902").assertIsFocused()

            // 5. BACK da Attore X -> torna ESATTAMENTE a Film A (MAI alla Home!) con focus sull'attore X nel Cast
            composeRule.onNodeWithTag("actor_detail_back_button").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.detailState.value?.mediaItem?.uniqueKey == "movie_901"
            }
            composeRule.waitForIdle()

            assertEquals(1, viewModel.detailStackSize.value)
            assertEquals(501, viewModel.detailState.value?.savedFocusedCastActorId)
            composeRule.onNodeWithTag("media_detail_screen").assertIsDisplayed()

            composeRule.waitUntil(timeoutMillis = 4_000L) {
                shadowOf(Looper.getMainLooper()).idle()
                runCatching {
                    composeRule.onNodeWithTag("cast_card_501").assertIsFocused()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("cast_card_501").assertIsFocused()
        } finally {
            db.close()
        }
    }
}

private class FakeActorTmdbServer {
    private val serverSocket = ServerSocket(0)
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    private var running = false

    val port: Int get() = serverSocket.localPort

    fun start() {
        running = true
        executor.execute {
            while (running && !serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                executor.execute { handleClient(socket) }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    private fun handleClient(socket: java.net.Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
            }
            val rawPath = requestLine.split(" ").getOrNull(1).orEmpty()
            val (status, body) = route(rawPath)
            val bytes = body.toByteArray(Charsets.UTF_8)
            val writer = OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8)
            writer.write("HTTP/1.1 $status\r\n")
            writer.write("Content-Type: application/json; charset=utf-8\r\n")
            writer.write("Content-Length: ${bytes.size}\r\n")
            writer.write("Connection: close\r\n\r\n")
            writer.flush()
            client.getOutputStream().write(bytes)
            client.getOutputStream().flush()
        }
    }

    private fun route(rawPath: String): Pair<String, String> {
        val path = rawPath.substringBefore("?")
        val query = rawPath.substringAfter("?", "")
        return when {
            path == "/3/movie/901" -> "200 OK" to """
                {
                  "id": 901,
                  "title": "Inception",
                  "overview": "Un ladro entra nei sogni per rubare segreti aziendali.",
                  "poster_path": "/inception.jpg",
                  "backdrop_path": "/inception_bg.jpg",
                  "vote_average": 8.8,
                  "release_date": "2010-07-16",
                  "runtime": 148,
                  "tagline": "La tua mente è la scena del crimine",
                  "genres": [{"id": 878, "name": "Fantascienza"}],
                  "credits": {
                    "cast": [
                      {"id": 501, "name": "Leonardo DiCaprio", "character": "Dom Cobb", "profile_path": "/leo.jpg"},
                      {"id": 502, "name": "Joseph Gordon-Levitt", "character": "Arthur", "profile_path": "/jgl.jpg"}
                    ]
                  },
                  "similar": {"results": []},
                  "videos": {"results": []}
                }
            """.trimIndent()

            path == "/3/movie/902" -> "200 OK" to """
                {
                  "id": 902,
                  "title": "Shutter Island",
                  "overview": "Un agente federale indaga sulla scomparsa di una paziente su un'isola remota.",
                  "poster_path": "/shutter.jpg",
                  "backdrop_path": "/shutter_bg.jpg",
                  "vote_average": 8.2,
                  "release_date": "2010-02-18",
                  "runtime": 138,
                  "tagline": "Alcuni luoghi non ti lasciano mai andare",
                  "genres": [{"id": 53, "name": "Thriller"}],
                  "credits": {
                    "cast": [
                      {"id": 501, "name": "Leonardo DiCaprio", "character": "Teddy Daniels", "profile_path": "/leo.jpg"}
                    ]
                  },
                  "similar": {"results": []},
                  "videos": {"results": []}
                }
            """.trimIndent()

            path == "/3/person/501" && query.contains("language=it-IT") -> "200 OK" to """
                {
                  "id": 501,
                  "name": "Leonardo DiCaprio",
                  "biography": "",
                  "birthday": "1974-11-11",
                  "deathday": null,
                  "place_of_birth": "Los Angeles, California, USA",
                  "profile_path": "/leo.jpg",
                  "known_for_department": "Acting"
                }
            """.trimIndent()

            path == "/3/person/501" && query.contains("language=en-US") -> "200 OK" to """
                {
                  "id": 501,
                  "name": "Leonardo DiCaprio",
                  "biography": "Academy Award-winning actor known for Inception, Shutter Island, and The Revenant.",
                  "birthday": "1974-11-11",
                  "deathday": null,
                  "place_of_birth": "Los Angeles, California, USA",
                  "profile_path": "/leo.jpg",
                  "known_for_department": "Acting"
                }
            """.trimIndent()

            path == "/3/person/501/combined_credits" -> "200 OK" to """
                {
                  "cast": [
                    {
                      "id": 902,
                      "media_type": "movie",
                      "title": "Shutter Island",
                      "overview": "Thriller psicologico ambientato nel 1954.",
                      "poster_path": "/shutter.jpg",
                      "backdrop_path": "/shutter_bg.jpg",
                      "vote_average": 8.2,
                      "popularity": 78.5,
                      "release_date": "2010-02-18"
                    },
                    {
                      "id": 902,
                      "media_type": "movie",
                      "title": "Shutter Island",
                      "overview": "Duplicato per verifica deduplicazione.",
                      "poster_path": "/shutter.jpg",
                      "backdrop_path": "/shutter_bg.jpg",
                      "vote_average": 8.2,
                      "popularity": 78.5,
                      "release_date": "2010-02-18"
                    },
                    {
                      "id": 903,
                      "media_type": "tv",
                      "name": "The Crowded Room",
                      "overview": "Serie drammatica psicologica.",
                      "poster_path": "/crowded.jpg",
                      "backdrop_path": "/crowded_bg.jpg",
                      "vote_average": 7.9,
                      "popularity": 64.0,
                      "first_air_date": "2023-06-09"
                    }
                  ],
                  "crew": []
                }
            """.trimIndent()

            path == "/3/person/9999" || path == "/3/person/9999/combined_credits" ->
                "500 Internal Server Error" to """{"status_message":"Not found"}"""

            else -> "200 OK" to """{"results": []}"""
        }
    }
}
