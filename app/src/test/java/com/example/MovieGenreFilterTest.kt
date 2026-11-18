package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VixFlixDatabase
import com.example.data.remote.TmdbGenreDto
import com.example.data.remote.TmdbGenreMap
import com.example.data.repository.VixFlixRepository
import com.example.ui.VixFlixViewModel
import com.example.ui.components.MovieGenreChipBar
import com.example.ui.theme.MyApplicationTheme
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
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
@Config(sdk = [36])
class MovieGenreFilterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: MiniTmdbServer
    private lateinit var repository: VixFlixRepository

    @Before
    fun setUp() {
        server = MiniTmdbServer()
        server.start()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = VixFlixDatabase.getInstance(app).vixFlixDao()
        repository = VixFlixRepository(
            dao = dao,
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `repository fetchMovieGenres parses live TMDB list and falls back on error`() = runBlocking {
        val liveGenres = repository.fetchMovieGenres()
        assertEquals(3, liveGenres.size)
        assertEquals(28, liveGenres[0].id)
        assertEquals("Azione", liveGenres[0].name)
        assertEquals(878, liveGenres[2].id)
        assertEquals("Fantascienza", liveGenres[2].name)

        server.failGenreList.set(true)
        val fallbackGenres = repository.fetchMovieGenres()
        assertEquals(TmdbGenreMap.defaultMovieGenres, fallbackGenres)
    }

    @Test
    fun `repository fetchMoviesByGenre hits discover endpoint with genre filter`() = runBlocking {
        val result = repository.fetchMoviesByGenre(genreId = 28)
        assertTrue("Expected discover success: ${result.exceptionOrNull()}", result.isSuccess)
        val movies = result.getOrThrow()
        assertEquals(2, movies.size)
        assertEquals("Mad Max: Fury Road", movies[0].title)
        assertEquals("movie", movies[0].mediaType)
        assertTrue(
            "Expected /3/discover/movie with with_genres=28",
            server.recordedRequests.any { it.contains("/3/discover/movie") && it.contains("with_genres=28") }
        )
    }

    @Test
    fun `viewModel caches filtered movies per genre, retries errors, and resets cleanly`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VixFlixViewModel(app, repository)
        awaitCondition {
            viewModel.movieGenreState.value.genres.size == 3 &&
                viewModel.catalogState.value !is com.example.ui.CatalogUiState.Loading
        }

        val action = TmdbGenreDto(28, "Azione")
        val sciFi = TmdbGenreDto(878, "Fantascienza")
        val baselineActionCalls = server.recordedRequests.count {
            it.contains("/3/discover/movie") && it.contains("with_genres=28")
        }

        // First selection hits TMDB /discover/movie?with_genres=28 once more
        viewModel.selectMovieGenre(action)
        awaitCondition {
            val state = viewModel.movieGenreState.value
            state.selectedGenre == action && !state.isLoading && state.movies.isNotEmpty()
        }
        assertEquals("Mad Max: Fury Road", viewModel.movieGenreState.value.movies.first().title)
        val actionCallsAfterFirst = server.recordedRequests.count {
            it.contains("/3/discover/movie") && it.contains("with_genres=28")
        }
        assertEquals(baselineActionCalls + 1, actionCallsAfterFirst)

        // Error + retry on Sci-Fi
        server.failDiscover.set(true)
        viewModel.selectMovieGenre(sciFi)
        awaitCondition {
            val state = viewModel.movieGenreState.value
            state.selectedGenre == sciFi && !state.isLoading && state.errorMessage != null
        }

        server.failDiscover.set(false)
        viewModel.retrySelectedMovieGenre()
        awaitCondition {
            val state = viewModel.movieGenreState.value
            state.selectedGenre == sciFi && !state.isLoading && state.movies.isNotEmpty()
        }
        assertEquals("Dune: Parte Due", viewModel.movieGenreState.value.movies.first().title)

        // Re-selecting Azione must serve directly from ViewModel cache (no additional HTTP call)
        viewModel.selectMovieGenre(action)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(action, viewModel.movieGenreState.value.selectedGenre)
        assertEquals("Mad Max: Fury Road", viewModel.movieGenreState.value.movies.first().title)
        val actionCallsAfterCacheHit = server.recordedRequests.count {
            it.contains("/3/discover/movie") && it.contains("with_genres=28")
        }
        assertEquals(actionCallsAfterFirst, actionCallsAfterCacheHit)

        // Reset back to Tutti
        viewModel.selectMovieGenre(null)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(viewModel.movieGenreState.value.selectedGenre)
        assertTrue(viewModel.movieGenreState.value.movies.isEmpty())
    }

    @Test
    fun `MovieGenreChipBar scrolls horizontally across genres, selects Fantascienza, and resets on Tutti`() {
        val genres = TmdbGenreMap.defaultMovieGenres
        var selected by mutableStateOf<TmdbGenreDto?>(null)

        composeRule.setContent {
            MyApplicationTheme {
                MovieGenreChipBar(
                    genres = genres,
                    selectedGenre = selected,
                    onSelectGenre = { selected = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("movie_genre_chip_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("genre_chip_all").assertIsDisplayed()
        composeRule.onNodeWithText("Tutti").assertIsDisplayed()

        // Scroll horizontally to Fantascienza (id = 878, far down the genre list) and click it
        composeRule.onNodeWithTag("movie_genre_chip_bar")
            .performScrollToNode(hasTestTag("genre_chip_878"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_878").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(878, selected?.id)
        assertEquals("Fantascienza", selected?.name)

        // Scroll back to Tutti and click to clear filter
        composeRule.onNodeWithTag("movie_genre_chip_bar")
            .performScrollToNode(hasTestTag("genre_chip_all"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_all").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertNull(selected)
    }

    private suspend fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val steps = (timeoutMs / 40L).toInt().coerceAtLeast(1)
        repeat(steps) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            delay(40L)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    private class MiniTmdbServer {
        private var socket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()
        val recordedRequests = CopyOnWriteArrayList<String>()
        val failGenreList = AtomicBoolean(false)
        val failDiscover = AtomicBoolean(false)

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
                val method = parts[0]
                val pathAndQuery = parts[1]
                recordedRequests.add("$method $pathAndQuery")

                when {
                    pathAndQuery.startsWith("/3/genre/movie/list") -> {
                        if (failGenreList.get()) {
                            writeJson(s, 500, """{"status_message":"Internal error"}""")
                        } else {
                            writeJson(
                                s,
                                200,
                                """
                                {"genres":[
                                  {"id":28,"name":"Azione"},
                                  {"id":35,"name":"Commedia"},
                                  {"id":878,"name":"Fantascienza"}
                                ]}
                                """.trimIndent()
                            )
                        }
                    }
                    pathAndQuery.startsWith("/3/discover/movie") -> {
                        if (failDiscover.get()) {
                            writeJson(s, 503, """{"status_message":"Unavailable"}""")
                        } else if (pathAndQuery.contains("with_genres=878")) {
                            writeJson(
                                s,
                                200,
                                """
                                {"page":1,"total_pages":1,"results":[
                                  {"id":693134,"title":"Dune: Parte Due","overview":"Sci-Fi epic","poster_path":"/dune2.jpg","backdrop_path":"/dune2bg.jpg","vote_average":8.3,"release_date":"2024-02-27","genre_ids":[878,12]}
                                ]}
                                """.trimIndent()
                            )
                        } else {
                            writeJson(
                                s,
                                200,
                                """
                                {"page":1,"total_pages":1,"results":[
                                  {"id":76341,"title":"Mad Max: Fury Road","overview":"Action road war","poster_path":"/madmax.jpg","backdrop_path":"/madmaxbg.jpg","vote_average":8.1,"release_date":"2015-05-13","genre_ids":[28,12]},
                                  {"id":245891,"title":"John Wick","overview":"Action thriller","poster_path":"/jw.jpg","backdrop_path":"/jwbg.jpg","vote_average":7.4,"release_date":"2014-10-22","genre_ids":[28,53]}
                                ]}
                                """.trimIndent()
                            )
                        }
                    }
                    else -> {
                        writeJson(s, 200, """{"page":1,"total_pages":1,"results":[]}""")
                    }
                }
            }
        }

        private fun readLine(input: BufferedInputStream): String? {
            val buffer = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) {
                    return if (buffer.size() == 0) null else buffer.toString("UTF-8")
                }
                if (b == '\n'.code) {
                    return buffer.toString("UTF-8").trimEnd('\r')
                }
                buffer.write(b)
            }
        }

        private fun writeJson(socket: Socket, code: Int, json: String) {
            val bytes = json.toByteArray(Charsets.UTF_8)
            val statusText = if (code == 200) "OK" else "Error"
            val header = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            val out = socket.getOutputStream()
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }
}
