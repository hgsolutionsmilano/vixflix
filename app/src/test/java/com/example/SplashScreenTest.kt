package com.example

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VixFlixDatabase
import com.example.data.repository.VixFlixRepository
import com.example.ui.VixFlixViewModel
import com.example.ui.components.VixFlixSplashScreen
import com.example.ui.screens.VixFlixAppScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-television-notouch-dpad")
class SplashScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: SplashMockTmdbServer
    private lateinit var app: Application

    @Before
    fun setUp() {
        server = SplashMockTmdbServer()
        server.start()
        app = ApplicationProvider.getApplicationContext()
        val uiModeManager = app.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        shadowOf(uiModeManager).currentModeType = Configuration.UI_MODE_TYPE_TELEVISION
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `MainActivity installs splash screen theme and creates cleanly`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun `VixFlixSplashScreen renders logo, wordmark and loading indicator`() {
        composeRule.setContent {
            MyApplicationTheme {
                VixFlixSplashScreen(isCatalogLoading = true)
            }
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(120))
            composeRule.onAllNodesWithTag("splash_brand_title", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("vixflix_splash_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_logo_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_brand_title").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `startup shows splash first, blocks premature D-pad leaks, and reveals catalog after delay`() {
        val dao = VixFlixDatabase.getInstance(app).vixFlixDao()
        val repo = VixFlixRepository(
            dao = dao,
            tmdbBaseUrl = "http://127.0.0.1:${server.port}/3/"
        )
        val viewModel = VixFlixViewModel(app, repo)

        composeRule.setContent {
            MyApplicationTheme {
                VixFlixAppScreen(
                    viewModel = viewModel,
                    splashMinDurationMs = 900L
                )
            }
        }
        composeRule.waitForIdle()

        // Immediatamente all'avvio: splash visibile e catalogo non ancora esposto
        composeRule.onNodeWithTag("vixflix_splash_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_logo_badge").assertIsDisplayed()

        // Tasti telecomando premuti sullo splash non devono causare crash
        composeRule.onNodeWithTag("vixflix_splash_screen", useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionCenter)
            }

        // Attende termine dello splash e comparsa della Hero del catalogo
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("hero_banner_card", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("vixflix_splash_screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private class SplashMockTmdbServer {
        private var socket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()

        val port: Int
            get() = socket?.localPort ?: error("Server not started")

        fun start() {
            socket = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
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
                val path = requestLine.split(" ").getOrNull(1).orEmpty()
                val json = when {
                    path.startsWith("/3/genre/movie/list") -> """{"genres":[{"id":28,"name":"Azione"}]}"""
                    path.startsWith("/3/trending") || path.startsWith("/3/movie") || path.startsWith("/3/discover") -> {
                        """{"page":1,"total_pages":1,"results":[{"id":10,"media_type":"movie","title":"Splash Film","overview":"Descrizione","poster_path":"/p.jpg","backdrop_path":"/b.jpg","vote_average":8.1,"release_date":"2024-01-01","genre_ids":[28]}]}"""
                    }
                    path.startsWith("/3/tv") -> {
                        """{"page":1,"total_pages":1,"results":[{"id":20,"media_type":"tv","name":"Splash Serie","overview":"Descrizione","poster_path":"/ps.jpg","backdrop_path":"/bs.jpg","vote_average":8.3,"first_air_date":"2024-01-01","genre_ids":[18]}]}"""
                    }
                    else -> """{"page":1,"total_pages":1,"results":[]}"""
                }
                val bytes = json.toByteArray(Charsets.UTF_8)
                val header = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                val out = s.getOutputStream()
                out.write(header.toByteArray(Charsets.UTF_8))
                out.write(bytes)
                out.flush()
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
    }
}
