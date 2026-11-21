package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VixFlixDatabase
import com.example.data.remote.TmdbGenreMap
import com.example.data.repository.VixFlixRepository
import com.example.ui.VixFlixViewModel
import com.example.ui.screens.VixFlixAppScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
class ExtendedSearchSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var server: ExtendedSearchFakeServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ExtendedSearchFakeServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `genre resolver matches Italian, English, synonyms, and combined queries`() {
        assertEquals(listOf("Fantascienza"), TmdbGenreMap.resolveSearchGenres("fantascienza").map { it.displayName })
        assertEquals(listOf("Fantascienza"), TmdbGenreMap.resolveSearchGenres("sci-fi").map { it.displayName })
        assertEquals(listOf("Animazione"), TmdbGenreMap.resolveSearchGenres("anime").map { it.displayName })
        assertEquals(listOf("Azione"), TmdbGenreMap.resolveSearchGenres("azione 2024").map { it.displayName })
        assertEquals(listOf("Horror"), TmdbGenreMap.resolveSearchGenres("Horror").map { it.displayName })
    }

    @Test
    fun `extended search supports actor filmography, genres, year, keywords, and studios`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = VixFlixRepository(
                dao = db.vixFlixDao(),
                tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
            )

            // 1. Ricerca per attore ("Leonardo DiCaprio") -> filmografia completa da combined_credits
            val actorResult = repo.searchTmdbExtended("Leonardo DiCaprio").getOrThrow()
            assertTrue(actorResult.items.any { it.title == "Inception" })
            assertTrue(actorResult.items.any { it.title == "The Revenant" })
            assertTrue(actorResult.matchedCategories.any { it.contains("Leonardo DiCaprio") })

            // 2. Ricerca per genere + anno ("azione 2024")
            val genreYearResult = repo.searchTmdbExtended("azione 2024").getOrThrow()
            assertTrue(genreYearResult.items.any { it.title == "Action Hit 2024" })
            assertTrue(genreYearResult.items.any { it.title == "Action Series 2024" })
            assertTrue(genreYearResult.matchedCategories.any { it.contains("Genere: Azione") })
            assertTrue(genreYearResult.matchedCategories.any { it.contains("Anno: 2024") })

            // 3. Ricerca per tema/parola chiave ("zombie")
            val keywordResult = repo.searchTmdbExtended("zombie").getOrThrow()
            assertTrue(keywordResult.items.any { it.title == "World War Z" })
            assertTrue(keywordResult.matchedCategories.any { it.contains("Tema: zombie") })

            // 4. Ricerca per studio/casa di produzione ("Pixar")
            val companyResult = repo.searchTmdbExtended("Pixar").getOrThrow()
            assertTrue(companyResult.items.any { it.title == "Inside Out 2" })
            assertTrue(companyResult.matchedCategories.any { it.contains("Studio: Pixar") })
        } finally {
            db.close()
        }
    }

    @Test
    fun `search results grid renders posters at standard 130dp width and shows extended match badges`() {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        val viewModel = VixFlixViewModel(app, repo)

        composeRule.setContent {
            MyApplicationTheme {
                VixFlixAppScreen(
                    viewModel = viewModel,
                    splashMinDurationMs = 0L,
                    respectStartupProfileGate = false
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("hero_banner_card", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Apre la ricerca dal rail TV e cerca per attore
        composeRule.onNodeWithTag("search_toggle_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_input_field", useUnmergedTree = true).performTextInput("Leonardo DiCaprio")

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("search_results_grid", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("poster_card_movie_27205", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("search_results_summary_header", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cast/Regia: Leonardo DiCaprio", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()

        // Verifica che TUTTE le locandine nei risultati di ricerca abbiano la larghezza standard 130.dp (come il resto dell'app)
        val firstCardWidth = composeRule.onNodeWithTag("poster_card_movie_27205", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().width
        val secondCardWidth = composeRule.onNodeWithTag("poster_card_movie_281957", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().width

        assertTrue(
            "Search poster 1 width must match standard 130.dp (was $firstCardWidth)",
            abs((firstCardWidth - 130.dp).value) <= 2.5f
        )
        assertTrue(
            "Search poster 2 width must match standard 130.dp (was $secondCardWidth)",
            abs((secondCardWidth - 130.dp).value) <= 2.5f
        )

        db.close()
    }

    private class ExtendedSearchFakeServer {
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
                val rawPath = requestLine.split(" ").getOrNull(1).orEmpty()
                val decodedPath = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)

                val body = when {
                    decodedPath.startsWith("/3/search/person") && decodedPath.contains("DiCaprio", ignoreCase = true) -> """
                        {
                          "page": 1,
                          "results": [
                            {
                              "id": 6193,
                              "name": "Leonardo DiCaprio",
                              "known_for_department": "Acting",
                              "popularity": 95.0,
                              "known_for": [
                                {
                                  "id": 27205,
                                  "media_type": "movie",
                                  "title": "Inception",
                                  "overview": "Sogni dentro sogni.",
                                  "poster_path": "/inception.jpg",
                                  "backdrop_path": "/inception_bg.jpg",
                                  "vote_average": 8.4,
                                  "release_date": "2010-07-16"
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent()

                    decodedPath.startsWith("/3/person/6193/combined_credits") -> """
                        {
                          "id": 6193,
                          "cast": [
                            {
                              "id": 27205,
                              "media_type": "movie",
                              "title": "Inception",
                              "overview": "Sogni dentro sogni.",
                              "poster_path": "/inception.jpg",
                              "backdrop_path": "/inception_bg.jpg",
                              "vote_average": 8.4,
                              "popularity": 90.0,
                              "release_date": "2010-07-16"
                            },
                            {
                              "id": 281957,
                              "media_type": "movie",
                              "title": "The Revenant",
                              "overview": "Sopravvivenza estrema.",
                              "poster_path": "/revenant.jpg",
                              "backdrop_path": "/revenant_bg.jpg",
                              "vote_average": 7.6,
                              "popularity": 80.0,
                              "release_date": "2015-12-25"
                            }
                          ],
                          "crew": []
                        }
                    """.trimIndent()

                    decodedPath.startsWith("/3/search/keyword") && decodedPath.contains("zombie", ignoreCase = true) -> """
                        {"page":1,"results":[{"id":12377,"name":"zombie"}]}
                    """.trimIndent()

                    decodedPath.startsWith("/3/search/company") && decodedPath.contains("Pixar", ignoreCase = true) -> """
                        {"page":1,"results":[{"id":3,"name":"Pixar"}]}
                    """.trimIndent()

                    decodedPath.startsWith("/3/discover/movie") && decodedPath.contains("with_genres=28") && decodedPath.contains("primary_release_year=2024") -> """
                        {
                          "page": 1,
                          "results": [
                            {
                              "id": 9101,
                              "title": "Action Hit 2024",
                              "overview": "Azione pura del 2024.",
                              "poster_path": "/act2024.jpg",
                              "vote_average": 7.8,
                              "release_date": "2024-05-10"
                            }
                          ]
                        }
                    """.trimIndent()

                    decodedPath.startsWith("/3/discover/tv") && decodedPath.contains("with_genres=10759") && decodedPath.contains("first_air_date_year=2024") -> """
                        {
                          "page": 1,
                          "results": [
                            {
                              "id": 9102,
                              "name": "Action Series 2024",
                              "overview": "Serie TV azione 2024.",
                              "poster_path": "/acttv2024.jpg",
                              "vote_average": 8.1,
                              "first_air_date": "2024-03-15"
                            }
                          ]
                        }
                    """.trimIndent()

                    decodedPath.startsWith("/3/discover/movie") && decodedPath.contains("with_keywords=12377") -> """
                        {
                          "page": 1,
                          "results": [
                            {
                              "id": 72190,
                              "title": "World War Z",
                              "overview": "Apocalisse zombie.",
                              "poster_path": "/wwz.jpg",
                              "vote_average": 7.0,
                              "release_date": "2013-06-20"
                            }
                          ]
                        }
                    """.trimIndent()

                    decodedPath.startsWith("/3/discover/movie") && decodedPath.contains("with_companies=3") -> """
                        {
                          "page": 1,
                          "results": [
                            {
                              "id": 1022789,
                              "title": "Inside Out 2",
                              "overview": "Nuove emozioni.",
                              "poster_path": "/io2.jpg",
                              "vote_average": 7.7,
                              "release_date": "2024-06-14"
                            }
                          ]
                        }
                    """.trimIndent()

                    decodedPath.startsWith("/3/search/multi") -> """{"page":1,"results":[]}"""
                    decodedPath.startsWith("/3/genre/") -> """{"genres":[{"id":28,"name":"Azione"}]}"""
                    decodedPath.startsWith("/3/watch/providers/") -> """{"results":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/n.png","display_priority":1}]}"""
                    else -> """
                        {
                          "page": 1,
                          "results": [
                            {
                              "id": 100,
                              "title": "Hero Film",
                              "media_type": "movie",
                              "overview": "Catalogo home.",
                              "poster_path": "/hero.jpg",
                              "backdrop_path": "/hero_bg.jpg",
                              "vote_average": 8.5,
                              "release_date": "2024-01-01"
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
