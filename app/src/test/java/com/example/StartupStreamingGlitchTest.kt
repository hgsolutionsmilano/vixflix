package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppPreferences
import com.example.data.local.StreamingBufferPreset
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.VixFlixViewModel
import com.example.ui.screens.VixPlayerScreen
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupStreamingGlitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: MiniVixHttpServer
    private lateinit var loopbackOkHttp: OkHttpClient
    private lateinit var resolver: VixSrcStreamResolver

    @Before
    fun setUp() {
        server = MiniVixHttpServer()
        server.start()
        val loopbackDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return if (hostname.equals("vixsrc.to", ignoreCase = true) ||
                    hostname.endsWith(".vixsrc.to", ignoreCase = true) ||
                    hostname.endsWith(".greenmeteor53.boats", ignoreCase = true)
                ) {
                    listOf(InetAddress.getLoopbackAddress())
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }
        loopbackOkHttp = OkHttpClient.Builder()
            .dns(loopbackDns)
            .build()
        resolver = VixSrcStreamResolver(
            baseOkHttpClient = loopbackOkHttp,
            rawBaseUrl = "http://vixsrc.to:${server.port}"
        )
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `prewarmConnection uses valid movie list endpoint (200) instead of broken root rewrite (404)`() = runBlocking {
        resolver.prewarmConnection()

        assertTrue(
            "Expected HEAD /api/list/movie/?lang=it, actual requests: ${server.recordedRequests}",
            server.recordedRequests.any { it.startsWith("HEAD /api/list/movie/?lang=it") }
        )
        assertFalse(
            "prewarmConnection must not hit /api/list/..%2f..%2f root 404",
            server.recordedRequests.any { it.startsWith("HEAD /api/list/..%2f..%2f") }
        )
    }

    @Test
    fun `resolveStreamForExoPlayer recovers automatically from cold-start transient 403 glitch`() = runBlocking {
        server.transientApiFailuresRemaining.set(1)

        val result = resolver.resolveStreamForExoPlayer(
            tmdbId = 120,
            mediaType = "movie",
            startSeconds = 180L,
            lang = "it"
        )

        assertTrue("Expected transient retry recovery to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val stream = result.getOrThrow()
        assertTrue(stream.streamUrl.contains("/playlist/170360"))
        assertTrue(stream.streamUrl.contains("token=tok123"))
        assertTrue(stream.streamUrl.contains("startAt=180"))
        val apiCalls = server.recordedRequests.count { it.contains("api%2fmovie%2f120") }
        assertEquals("Expected 1 retry (2 total API calls) after transient 403", 2, apiCalls)
    }

    @Test
    fun `interceptor strips UTF8 BOM from m3u8, rewrites enc key URI, and forces video_mp2t on html segments`() {
        val m3u8Url = "http://vixsrc.to:${server.port}/playlist/170360?token=tok123&expires=9999999&h=1&lang=it"
        resolver.exoOkHttpClient.newCall(Request.Builder().url(m3u8Url).build()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("application/vnd.apple.mpegurl", resp.header("Content-Type"))
            val body = resp.body?.string().orEmpty()
            assertTrue("Expected BOM-free #EXTM3U start", body.startsWith("#EXTM3U"))
            assertTrue(
                "Expected absolute enc.key URL, got:\n$body",
                body.contains("""URI="http://vixsrc.to:${server.port}/storage/enc.key"""")
            )
        }

        val keyUrl = "http://vixsrc.to:${server.port}/storage/enc.key"
        resolver.exoOkHttpClient.newCall(Request.Builder().url(keyUrl).build()).execute().use { keyResp ->
            assertEquals(200, keyResp.code)
            assertEquals(16, keyResp.body?.bytes()?.size)
        }

        val segmentUrl = "http://sc-u12-01.greenmeteor53.boats:${server.port}/hls/seg1.html"
        resolver.exoOkHttpClient.newCall(Request.Builder().url(segmentUrl).build()).execute().use { segResp ->
            assertEquals(200, segResp.code)
            assertEquals("video/mp2t", segResp.header("Content-Type"))
        }
    }

    @Test
    fun `VixPlayerScreen re-prepares ExoPlayer when bufferPreset changes right after cold start`() {
        val playlistUrl = "http://vixsrc.to:${server.port}/playlist/170360?token=tok123&expires=9999999&h=1&lang=it"
        val session = PlaybackSession(
            mediaItem = MediaItem(
                id = 120,
                mediaType = "movie",
                title = "Il Signore degli Anelli - La Compagnia dell'Anello",
                overview = "Test avvio streaming",
                posterPath = null,
                backdropPath = null,
                voteAverage = 8.9,
                releaseYear = "2001"
            ),
            isResolvingStream = false,
            forceWebPlayer = false,
            resolvedStream = ResolvedStream(
                streamUrl = playlistUrl,
                embedUrl = "http://vixsrc.to:${server.port}/movie/120?lang=it",
                referer = "http://vixsrc.to:${server.port}/",
                isDirectHls = true
            )
        )

        var activeBufferPreset by mutableStateOf(StreamingBufferPreset.DEFAULT)

        composeRule.setContent {
            VixPlayerScreen(
                session = session,
                streamLang = "it",
                bufferPreset = activeBufferPreset,
                vixSrcResolver = resolver,
                onDynamicHlsFound = {},
                onTogglePlayerMode = {},
                onToggleLanguage = {},
                onProgressUpdate = { _, _ -> },
                onNextEpisode = {},
                onClose = {}
            )
        }

        // Attende che ExoPlayer #1 (DEFAULT) esegua la prima richiesta playlist HLS verso VixSrc
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            server.playlistRequestCount.get() >= 1
        }
        val countBeforePresetUpdate = server.playlistRequestCount.get()

        // Simula l'emissione asincrona ritardata da DataStore dopo aggiornamento app (DEFAULT -> EXTENDED)
        composeRule.runOnUiThread {
            activeBufferPreset = StreamingBufferPreset.EXTENDED
        }

        // Dopo il fix: ExoPlayer #2 deve riattaccare la MediaSource ed eseguire una nuova prepare()+richiesta playlist
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            server.playlistRequestCount.get() > countBeforePresetUpdate
        }
        assertTrue(
            "Expected recreated ExoPlayer to prepare and request playlist again",
            server.playlistRequestCount.get() > countBeforePresetUpdate
        )
    }

    @Test
    fun `VixFlixViewModel loads DataStore settings eagerly on cold start and cancels stale session on close`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = AppPreferences(app)
        prefs.setBufferPreset(StreamingBufferPreset.BALANCED)
        prefs.setStreamLanguage("en")

        val viewModel = VixFlixViewModel(app)

        // Attende al massimo 3s pompando il Main Looper che Eagerly emetta i valori DataStore
        var loaded = false
        for (i in 0 until 60) {
            shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.bufferPreset.value == StreamingBufferPreset.BALANCED &&
                viewModel.streamLanguage.value == "en"
            ) {
                loaded = true
                break
            }
            delay(50L)
        }
        assertTrue(
            "Expected Eagerly flows to emit saved BALANCED/en settings immediately, got ${viewModel.bufferPreset.value}/${viewModel.streamLanguage.value}",
            loaded
        )

        // Verifica che closePlayer() e cambio rapido titolo annullino eventuali risoluzioni in corso senza glitch
        val lotr = MediaItem(
            id = 120,
            mediaType = "movie",
            title = "Il Signore degli Anelli",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.9,
            releaseYear = "2001"
        )
        val matrix = MediaItem(
            id = 603,
            mediaType = "movie",
            title = "Matrix",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.7,
            releaseYear = "1999"
        )
        viewModel.startPlayback(lotr, explicitStartPositionMs = 60_000L)
        delay(20L)
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.startPlayback(matrix, explicitStartPositionMs = 0L)
        delay(20L)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(603, viewModel.playbackSession.value?.mediaItem?.id)

        viewModel.closePlayer()
        shadowOf(Looper.getMainLooper()).idle()
        delay(150L)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull("Closed playback session must stay null cleanly", viewModel.playbackSession.value)
    }

    private class MiniVixHttpServer {
        private var socket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()
        val recordedRequests = CopyOnWriteArrayList<String>()
        val transientApiFailuresRemaining = AtomicInteger(0)
        val playlistRequestCount = AtomicInteger(0)

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
                // Drain headers
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
                    pathAndQuery.startsWith("/api/list/movie/") -> {
                        writeResponse(s, 200, "application/json", """[{"tmdb_id":120}]""".toByteArray(), method == "HEAD")
                    }
                    pathAndQuery == "/api/list/..%2f..%2f" -> {
                        writeResponse(s, 404, "text/html", "404".toByteArray(), method == "HEAD")
                    }
                    pathAndQuery.contains("api%2fmovie%2f120") -> {
                        if (transientApiFailuresRemaining.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
                            writeResponse(s, 403, "text/html", "Cloudflare challenge".toByteArray(), method == "HEAD")
                        } else {
                            writeResponse(
                                s,
                                200,
                                "application/json",
                                """{"src":"/embed/170360?lang=it"}""".toByteArray(),
                                method == "HEAD"
                            )
                        }
                    }
                    pathAndQuery.contains("embed%2f170360") -> {
                        val html = """
                            <html><script>
                            window.masterPlaylist = {
                                params: { 'token': 'tok123', 'expires': '9999999' },
                                url: 'http://vixsrc.to:$port/playlist/170360'
                            };
                            </script></html>
                        """.trimIndent()
                        writeResponse(s, 200, "text/html", html.toByteArray(), method == "HEAD")
                    }
                    pathAndQuery.contains("playlist%2f170360") -> {
                        playlistRequestCount.incrementAndGet()
                        val m3u8 = "\uFEFF#EXTM3U\n" +
                            "#EXT-X-VERSION:3\n" +
                            "#EXT-X-TARGETDURATION:6\n" +
                            "#EXT-X-MEDIA-SEQUENCE:0\n" +
                            "#EXT-X-KEY:METHOD=AES-128,URI=\"/storage/enc.key\"\n" +
                            "#EXTINF:6.0,\n" +
                            "http://sc-u12-01.greenmeteor53.boats:$port/hls/seg1.html\n" +
                            "#EXT-X-ENDLIST\n"
                        writeResponse(s, 200, "text/plain", m3u8.toByteArray(), method == "HEAD")
                    }
                    pathAndQuery.contains("storage%2fenc.key") -> {
                        val aesKey = ByteArray(16) { (it + 1).toByte() }
                        writeResponse(s, 200, "application/octet-stream", aesKey, method == "HEAD")
                    }
                    pathAndQuery.contains("/hls/seg1.html") -> {
                        val fakeTsPacket = ByteArray(188) { idx -> if (idx == 0) 0x47 else 0x00 }
                        writeResponse(s, 200, "text/html", fakeTsPacket, method == "HEAD")
                    }
                    else -> {
                        writeResponse(s, 404, "text/plain", "Not Found".toByteArray(), method == "HEAD")
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
                    val str = buffer.toString("UTF-8")
                    return str.trimEnd('\r')
                }
                buffer.write(b)
            }
        }

        private fun writeResponse(
            socket: Socket,
            code: Int,
            contentType: String,
            body: ByteArray,
            headOnly: Boolean
        ) {
            val statusText = when (code) {
                200 -> "OK"
                403 -> "Forbidden"
                404 -> "Not Found"
                else -> "Error"
            }
            val header = buildString {
                append("HTTP/1.1 ").append(code).append(' ').append(statusText).append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            val out = socket.getOutputStream()
            out.write(header.toByteArray(Charsets.UTF_8))
            if (!headOnly) {
                out.write(body)
            }
            out.flush()
        }
    }
}
