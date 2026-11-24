package com.example

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.data.remote.MediaItem
import com.example.data.remote.VixSrcDiagnostics
import com.example.data.remote.VixSrcErrorCategory
import com.example.data.remote.VixSrcException
import com.example.data.remote.VixSrcResolutionPhase
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.screens.VixPlayerScreen
import com.example.ui.screens.formatVixSrcPlaybackError
import com.example.ui.theme.MyApplicationTheme
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VixSrcApiIntegrationAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: VixSrcAuditServer
    private lateinit var resolver: VixSrcStreamResolver

    @Before
    fun setUp() {
        server = VixSrcAuditServer()
        server.start()

        val loopbackDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return if (
                    hostname.equals("vixsrc.to", ignoreCase = true) ||
                    hostname.endsWith(".vixsrc.to", ignoreCase = true) ||
                    hostname.equals("cdn.vixcloud.co", ignoreCase = true)
                ) {
                    listOf(InetAddress.getLoopbackAddress())
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }

        val baseClient = OkHttpClient.Builder()
            .dns(loopbackDns)
            .build()

        resolver = VixSrcStreamResolver(
            baseOkHttpClient = baseClient,
            rawBaseUrl = "http://vixsrc.to:${server.port}"
        )
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `movie resolution uses clean official movie endpoint, parses unquoted JS keys and numeric expires, respects canPlayFHD, and verifies HLS manifest`() = runBlocking {
        val futureExp = (System.currentTimeMillis() / 1000L) + 7200L
        server.handler = { req ->
            when {
                req.path.startsWith("/movie/550") -> {
                    val html = """
                        <!doctype html>
                        <html><body><script>
                        window.canPlayFHD = true;
                        window.masterPlaylist = {
                            params: {
                                token: 'secret_tok_movie_123',
                                expires: $futureExp,
                                asn: '64512'
                            },
                            url: 'http://vixsrc.to:${server.port}/playlist/550?b=1&amp;existing=ok'
                        };
                        </script></body></html>
                    """.trimIndent()
                    AuditResponse(200, "text/html; charset=utf-8", html.toByteArray())
                }
                req.path.startsWith("/playlist/550") -> {
                    val m3u8 = """
                        #EXTM3U
                        #EXT-X-VERSION:3
                        #EXT-X-STREAM-INF:BANDWIDTH=4800000,RESOLUTION=1920x1080
                        /playlist/550/1080p.m3u8
                    """.trimIndent()
                    AuditResponse(200, "application/vnd.apple.mpegurl", m3u8.toByteArray())
                }
                else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }

        val result = resolver.resolveStreamForExoPlayer(
            tmdbId = 550,
            mediaType = "movie",
            season = null,
            episode = null,
            startSeconds = 420L,
            lang = "it"
        )

        assertTrue("Movie resolution should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val stream = result.getOrThrow()

        // 1. Verifica che nessun path traversal sia mai stato inviato
        assertTrue(
            "Nessuna richiesta deve contenere ..%2f o /api/list/..",
            server.recordedRequests.none { it.path.contains("..") || it.path.contains("%2f", ignoreCase = true) }
        )

        // 2. Verifica che la navigazione HTML GET non invii un header Origin fittizio
        val htmlReq = server.recordedRequests.first { it.path.startsWith("/movie/550") }
        assertTrue("GET /movie/550 non deve inviare Origin", htmlReq.headers["origin"] == null)
        assertTrue("Query string deve includere lang=it", htmlReq.path.contains("lang=it"))

        // 3. Verifica URL finale playlist: token, expires, parametri esistenti, h=1 (da canPlayFHD=true), NO &amp;, NO startAt
        assertTrue(stream.streamUrl.contains("token=secret_tok_movie_123"))
        assertTrue(stream.streamUrl.contains("expires=$futureExp"))
        assertTrue(stream.streamUrl.contains("b=1"))
        assertTrue(stream.streamUrl.contains("existing=ok"))
        assertTrue(stream.streamUrl.contains("h=1"))
        assertTrue(stream.streamUrl.contains("lang=it"))
        assertFalse("URL HLS non deve contenere HTML entity &amp;", stream.streamUrl.contains("&amp;"))
        assertFalse("URL HLS non deve essere inquinato da startAt", stream.streamUrl.contains("startAt="))
        assertEquals(420L, stream.startSeconds)
        assertEquals("http://vixsrc.to:${server.port}", stream.origin)

        // 4. Verifica che la playlist HLS sia stata realmente validata prima del ritorno
        assertTrue(server.recordedRequests.any { it.path.startsWith("/playlist/550") })

        // 5. Verifica diagnostica sicura: fasi registrate e nessun token/asn esposto nei log
        val events = resolver.getRecentDiagnostics()
        assertTrue(events.any { it.phase == VixSrcResolutionPhase.EMBED_PAGE && it.httpStatusCode == 200 })
        assertTrue(events.any { it.phase == VixSrcResolutionPhase.PLAYLIST_VERIFICATION && it.httpStatusCode == 200 })
        val allDiagnosticUrls = events.joinToString("\n") { "${it.sanitizedUrl} ${it.errorDetail.orEmpty()}" }
        assertFalse("Il token non deve mai comparire nella diagnostica", allDiagnosticUrls.contains("secret_tok_movie_123"))
        assertFalse("Il parametro asn sensibile non deve comparire nella diagnostica", allDiagnosticUrls.contains("64512"))
        assertTrue("Il token deve essere oscurato come <redacted>", allDiagnosticUrls.contains("token=%3Credacted%3E") || allDiagnosticUrls.contains("token=<redacted>"))
    }

    @Test
    fun `tv series and episode resolution follows iframe, cross-host redirect, persists cookies, and resolves relative AES-128 enc key against post-redirect host`() = runBlocking {
        val futureExp = (System.currentTimeMillis() / 1000L) + 7200L
        server.handler = { req ->
            when {
                req.path.startsWith("/tv/1399/2/5") -> {
                    val html = """
                        <html><body>
                            <iframe src="/iframe/tv/1399/2/5?lang=it"></iframe>
                        </body></html>
                    """.trimIndent()
                    AuditResponse(
                        code = 200,
                        contentType = "text/html",
                        body = html.toByteArray(),
                        extraHeaders = mapOf("Set-Cookie" to "vix_sess=super_secret_cookie_val; Path=/")
                    )
                }
                req.path.startsWith("/iframe/tv/1399/2/5") -> {
                    AuditResponse(
                        code = 302,
                        contentType = "text/plain",
                        body = ByteArray(0),
                        extraHeaders = mapOf("Location" to "http://cdn.vixcloud.co:${server.port}/embed/1399/s2/e5?lang=it")
                    )
                }
                req.path.startsWith("/embed/1399/s2/e5") -> {
                    val html = """
                        <html><script>
                        window.canPlayFHD = false;
                        window.masterPlaylist = {
                            params: { 'token': 'tv_ep_secret_tok', 'expires': '$futureExp' },
                            url: '/playlist/1399-s2-e5.m3u8'
                        };
                        </script></html>
                    """.trimIndent()
                    AuditResponse(
                        code = 200,
                        contentType = "text/html",
                        body = html.toByteArray(),
                        extraHeaders = mapOf("Set-Cookie" to "cdn_session=cdn_secret_456; Path=/")
                    )
                }
                req.path.startsWith("/playlist/1399-s2-e5.m3u8") -> {
                    val cookie = req.headers["cookie"].orEmpty()
                    if (!cookie.contains("cdn_session=cdn_secret_456")) {
                        AuditResponse(403, "text/plain", "Missing session cookie".toByteArray())
                    } else {
                        val m3u8 = """
                            #EXTM3U
                            #EXT-X-VERSION:3
                            #EXT-X-TARGETDURATION:10
                            #EXT-X-KEY:METHOD=AES-128,URI="/storage/enc.key?key=k1"
                            #EXTINF:10.0,
                            /segments/seg001.ts
                        """.trimIndent()
                        AuditResponse(200, "application/vnd.apple.mpegurl", m3u8.toByteArray())
                    }
                }
                req.path.startsWith("/storage/enc.key") -> {
                    val cookie = req.headers["cookie"].orEmpty()
                    if (!cookie.contains("cdn_session=cdn_secret_456")) {
                        AuditResponse(403, "text/plain", "Missing key cookie".toByteArray())
                    } else {
                        AuditResponse(200, "application/octet-stream", ByteArray(16) { 0x42 })
                    }
                }
                else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }

        val result = resolver.resolveStreamForExoPlayer(
            tmdbId = 1399,
            mediaType = "tv",
            season = 2,
            episode = 5,
            startSeconds = 95L,
            lang = "it"
        )

        assertTrue("TV episode resolution must succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val stream = result.getOrThrow()

        // URL playlist relativo risolto rispetto all'host post-redirect (cdn.vixcloud.co)
        assertTrue(stream.streamUrl.startsWith("http://cdn.vixcloud.co:${server.port}/playlist/1399-s2-e5.m3u8"))
        assertFalse("Con canPlayFHD=false non deve forzare h=1", stream.streamUrl.contains("h=1"))
        assertEquals("http://cdn.vixcloud.co:${server.port}", stream.origin)
        assertTrue(stream.referer.startsWith("http://cdn.vixcloud.co:${server.port}/embed/1399/s2/e5"))

        // Verifica interceptor HLS su exoOkHttpClient: riscrive URI="/storage/enc.key" con l'host post-redirect e mantiene il cookie
        val playlistRequest = Request.Builder()
            .url(stream.streamUrl)
            .header("Referer", stream.referer)
            .header("Origin", stream.origin)
            .build()
        val rewrittenManifest = resolver.exoOkHttpClient.newCall(playlistRequest).execute().use { resp ->
            assertEquals(200, resp.code)
            resp.body?.string().orEmpty()
        }
        assertTrue(
            "URI chiave AES relativa deve risolversi contro host post-redirect cdn.vixcloud.co",
            rewrittenManifest.contains("URI=\"http://cdn.vixcloud.co:${server.port}/storage/enc.key?key=k1\"")
        )

        // Fetch della chiave AES-128 tramite lo stesso client (deve inviare il cookie persistito)
        val keyRequest = Request.Builder()
            .url("http://cdn.vixcloud.co:${server.port}/storage/enc.key?key=k1")
            .header("Referer", stream.referer)
            .header("Origin", stream.origin)
            .build()
        resolver.exoOkHttpClient.newCall(keyRequest).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals(16, resp.body?.bytes()?.size)
        }

        // Verifica sicurezza: nessun cookie o token nei log diagnostici
        val diagnosticsDump = resolver.getRecentDiagnostics().joinToString("\n") { it.toString() }
        assertFalse(diagnosticsDump.contains("super_secret_cookie_val"))
        assertFalse(diagnosticsDump.contains("cdn_secret_456"))
        assertFalse(diagnosticsDump.contains("tv_ep_secret_tok"))
    }

    @Test
    fun `json api fallback resolves movie cleanly when direct html embed returns 404`() = runBlocking {
        val futureExp = (System.currentTimeMillis() / 1000L) + 3600L
        server.handler = { req ->
            when {
                req.path.startsWith("/movie/603") -> AuditResponse(404, "text/html", "Not Found".toByteArray())
                req.path.startsWith("/api/movie/603") -> {
                    AuditResponse(200, "application/json", """{"src":"/embed/603?lang=it"}""".toByteArray())
                }
                req.path.startsWith("/embed/603") -> {
                    val html = """
                        <html><script>
                        window.masterPlaylist = {
                            params: { "token": "api_fallback_tok", "expires": "$futureExp" },
                            url: "http://vixsrc.to:${server.port}/playlist/603"
                        };
                        </script></html>
                    """.trimIndent()
                    AuditResponse(200, "text/html", html.toByteArray())
                }
                req.path.startsWith("/playlist/603") -> {
                    AuditResponse(200, "application/vnd.apple.mpegurl", "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=2000000\n/v.m3u8\n".toByteArray())
                }
                else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }

        val result = resolver.resolveStreamForExoPlayer(
            tmdbId = 603,
            mediaType = "movie",
            season = null,
            episode = null,
            lang = "it"
        )

        assertTrue("Fallback API JSON deve riuscire: ${result.exceptionOrNull()}", result.isSuccess)
        val stream = result.getOrThrow()
        assertTrue(stream.streamUrl.contains("/playlist/603"))
        assertTrue(stream.referer.contains("/embed/603"))
        assertTrue(stream.embedUrl.contains("/movie/603"))
        assertTrue(server.recordedRequests.any { it.path.startsWith("/api/movie/603") })
    }

    @Test
    fun `previously failing traversal server rejects dotdot with HTTP 400 while fixed resolver succeeds with HTTP 200`() = runBlocking {
        val futureExp = (System.currentTimeMillis() / 1000L) + 3600L
        val http400Hits = AtomicInteger(0)

        server.handler = { req ->
            if (req.path.contains("..") || req.path.contains("%2f", ignoreCase = true) || req.path.startsWith("/api/list/..")) {
                http400Hits.incrementAndGet()
                AuditResponse(400, "text/plain", "Bad Request: Path traversal rejected".toByteArray())
            } else {
                when {
                    req.path.startsWith("/movie/27205") -> {
                        val html = "<script>window.masterPlaylist = { params: { token: 'ok_mov', expires: $futureExp }, url: 'http://vixsrc.to:${server.port}/playlist/27205' };</script>"
                        AuditResponse(200, "text/html", html.toByteArray())
                    }
                    req.path.startsWith("/tv/66732/1/1") -> {
                        val html = "<script>window.masterPlaylist = { params: { token: 'ok_tv', expires: $futureExp }, url: 'http://vixsrc.to:${server.port}/playlist/66732' };</script>"
                        AuditResponse(200, "text/html", html.toByteArray())
                    }
                    req.path.startsWith("/playlist/") -> {
                        AuditResponse(200, "application/vnd.apple.mpegurl", "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=1500000\n/stream.m3u8\n".toByteArray())
                    }
                    else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
                }
            }
        }

        val movieRes = resolver.resolveStreamForExoPlayer(27205, "movie", null, null, lang = "it")
        val tvRes = resolver.resolveStreamForExoPlayer(66732, "tv", 1, 1, lang = "it")

        assertTrue("Film deve risolversi con HTTP 200: ${movieRes.exceptionOrNull()}", movieRes.isSuccess)
        assertTrue("Episodio serie TV deve risolversi con HTTP 200: ${tvRes.exceptionOrNull()}", tvRes.isSuccess)
        assertEquals("Nessuna richiesta deve mai causare HTTP 400 per path traversal", 0, http400Hits.get())
    }

    @Test
    fun `http status codes 400 401 403 404 429 and 500 produce distinct error categories, retry only transient errors, and clear user messages`() = runBlocking {
        // 400 Bad Request -> nessun retry, HTTP_BAD_REQUEST
        val hits400 = AtomicInteger(0)
        server.handler = {
            hits400.incrementAndGet()
            AuditResponse(400, "text/plain", "Bad Request".toByteArray())
        }
        val res400 = resolver.resolveStreamForExoPlayer(1001, "movie", null, null)
        assertTrue(res400.isFailure)
        val ex400 = res400.exceptionOrNull() as VixSrcException
        assertEquals(400, ex400.httpStatusCode)
        assertEquals(VixSrcErrorCategory.HTTP_BAD_REQUEST, ex400.category)
        assertTrue(ex400.userMessage.contains("HTTP 400"))

        // 401 Unauthorized
        server.handler = { AuditResponse(401, "text/plain", "Unauthorized".toByteArray()) }
        val ex401 = resolver.resolveStreamForExoPlayer(1002, "movie", null, null).exceptionOrNull() as VixSrcException
        assertEquals(401, ex401.httpStatusCode)
        assertEquals(VixSrcErrorCategory.HTTP_UNAUTHORIZED, ex401.category)
        assertTrue(ex401.userMessage.contains("HTTP 401"))

        // 403 Forbidden
        server.handler = { AuditResponse(403, "text/plain", "Forbidden".toByteArray()) }
        val ex403 = resolver.resolveStreamForExoPlayer(1003, "movie", null, null).exceptionOrNull() as VixSrcException
        assertEquals(403, ex403.httpStatusCode)
        assertEquals(VixSrcErrorCategory.HTTP_FORBIDDEN, ex403.category)
        assertTrue(ex403.userMessage.contains("HTTP 403"))

        // 404 Not Found
        server.handler = { AuditResponse(404, "text/plain", "Not Found".toByteArray()) }
        val ex404 = resolver.resolveStreamForExoPlayer(1004, "movie", null, null).exceptionOrNull() as VixSrcException
        assertEquals(404, ex404.httpStatusCode)
        assertEquals(VixSrcErrorCategory.HTTP_NOT_FOUND, ex404.category)
        assertTrue(ex404.userMessage.contains("HTTP 404"))

        // 429 Too Many Requests -> retry transitorio deve recuperare con successo al 2° tentativo
        val futureExp = (System.currentTimeMillis() / 1000L) + 3600L
        val hits429 = AtomicInteger(0)
        server.handler = { req ->
            when {
                req.path.startsWith("/movie/1005") -> {
                    if (hits429.incrementAndGet() == 1) {
                        AuditResponse(429, "text/plain", "Too Many Requests".toByteArray())
                    } else {
                        val html = "<script>window.masterPlaylist = { params: { token: 'retry_ok', expires: $futureExp }, url: 'http://vixsrc.to:${server.port}/playlist/1005' };</script>"
                        AuditResponse(200, "text/html", html.toByteArray())
                    }
                }
                req.path.startsWith("/playlist/1005") -> {
                    AuditResponse(200, "application/vnd.apple.mpegurl", "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=1800000\n/stream.m3u8\n".toByteArray())
                }
                else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }
        val res429Recovered = resolver.resolveStreamForExoPlayer(1005, "movie", null, null)
        assertTrue("HTTP 429 transitorio deve essere recuperato dal retry: ${res429Recovered.exceptionOrNull()}", res429Recovered.isSuccess)
        assertEquals(2, hits429.get())

        // 500 Internal Server Error -> retry effettuato e poi fallimento classificato HTTP_SERVER_ERROR
        val hits500 = AtomicInteger(0)
        server.handler = { req ->
            if (req.path.startsWith("/movie/1006")) hits500.incrementAndGet()
            AuditResponse(500, "text/plain", "Internal Server Error".toByteArray())
        }
        val ex500 = resolver.resolveStreamForExoPlayer(1006, "movie", null, null).exceptionOrNull() as VixSrcException
        assertEquals(500, ex500.httpStatusCode)
        assertEquals(VixSrcErrorCategory.HTTP_SERVER_ERROR, ex500.category)
        assertTrue(ex500.userMessage.contains("HTTP 500"))
        assertTrue("HTTP 500 deve ritentare almeno una volta", hits500.get() >= 2)
    }

    @Test
    fun `invalid html payload on playlist url and malformed json api response are rejected before reaching player`() = runBlocking {
        val futureExp = (System.currentTimeMillis() / 1000L) + 3600L

        // Caso A: URL playlist restituisce HTML invece di #EXTM3U -> bloccato in PLAYLIST_VERIFICATION
        server.handler = { req ->
            when {
                req.path.startsWith("/movie/901") -> {
                    val html = "<script>window.masterPlaylist = { params: { token: 't', expires: $futureExp }, url: 'http://vixsrc.to:${server.port}/playlist/901' };</script>"
                    AuditResponse(200, "text/html", html.toByteArray())
                }
                req.path.startsWith("/playlist/901") -> {
                    AuditResponse(200, "text/html", "<html><body>Access check</body></html>".toByteArray())
                }
                else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }
        val resHtmlPlaylist = resolver.resolveStreamForExoPlayer(901, "movie", null, null)
        assertTrue("Un payload HTML su URL playlist non deve mai arrivare al player", resHtmlPlaylist.isFailure)
        val exHtml = resHtmlPlaylist.exceptionOrNull() as VixSrcException
        assertEquals(VixSrcResolutionPhase.PLAYLIST_VERIFICATION, exHtml.phase)
        assertEquals(VixSrcErrorCategory.INVALID_CONTENT_TYPE, exHtml.category)

        // Caso B: Content-Type m3u8 ma body corrotto privo di header #EXTM3U -> INVALID_PLAYLIST_MANIFEST
        server.handler = { req ->
            when {
                req.path.startsWith("/movie/903") -> {
                    val html = "<script>window.masterPlaylist = { params: { token: 't', expires: $futureExp }, url: 'http://vixsrc.to:${server.port}/playlist/903' };</script>"
                    AuditResponse(200, "text/html", html.toByteArray())
                }
                req.path.startsWith("/playlist/903") -> {
                    AuditResponse(200, "application/vnd.apple.mpegurl", "CORRUPTED_NON_HLS_BODY".toByteArray())
                }
                else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }
        val resBrokenManifest = resolver.resolveStreamForExoPlayer(903, "movie", null, null)
        assertTrue("Un manifest senza #EXTM3U non deve mai arrivare al player", resBrokenManifest.isFailure)
        val exBroken = resBrokenManifest.exceptionOrNull() as VixSrcException
        assertEquals(VixSrcResolutionPhase.PLAYLIST_VERIFICATION, exBroken.phase)
        assertEquals(VixSrcErrorCategory.INVALID_PLAYLIST_MANIFEST, exBroken.category)

        // Caso C: API JSON malformato -> classificato JSON_PARSE_ERROR
        server.handler = { req ->
            when {
                req.path.startsWith("/movie/902") -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
                req.path.startsWith("/api/movie/902") -> AuditResponse(200, "application/json", "{invalid_json".toByteArray())
                else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }
        val resBadJson = resolver.resolveStreamForExoPlayer(902, "movie", null, null)
        assertTrue(resBadJson.isFailure)
        val exJson = resBadJson.exceptionOrNull() as VixSrcException
        assertEquals(VixSrcResolutionPhase.API_LOOKUP, exJson.phase)
        assertEquals(VixSrcErrorCategory.JSON_PARSE_ERROR, exJson.category)
    }

    @Test
    fun `concurrent duplicate requests for same title are deduplicated into a single network pipeline`() = runBlocking {
        val futureExp = (System.currentTimeMillis() / 1000L) + 3600L
        val moviePageHits = AtomicInteger(0)
        val playlistHits = AtomicInteger(0)

        server.handler = { req ->
            when {
                req.path.startsWith("/movie/777") -> {
                    moviePageHits.incrementAndGet()
                    Thread.sleep(150L)
                    val html = "<script>window.masterPlaylist = { params: { token: 'dedup_tok', expires: $futureExp }, url: 'http://vixsrc.to:${server.port}/playlist/777' };</script>"
                    AuditResponse(200, "text/html", html.toByteArray())
                }
                req.path.startsWith("/playlist/777") -> {
                    playlistHits.incrementAndGet()
                    AuditResponse(200, "application/vnd.apple.mpegurl", "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=3000000\n/stream.m3u8\n".toByteArray())
                }
                else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }

        val results = (1..4).map {
            async {
                resolver.resolveStreamForExoPlayer(777, "movie", null, null, lang = "it")
            }
        }.awaitAll()

        assertTrue(results.all { it.isSuccess })
        assertEquals(1, results.map { it.getOrThrow().streamUrl }.distinct().size)
        assertEquals("Le richieste concorrenti identiche devono essere deduplicate a 1 chiamata pagina", 1, moviePageHits.get())
        assertEquals("La verifica manifest deve avvenire 1 sola volta", 1, playlistHits.get())
    }

    @Test
    fun `end to end handoff to VixPlayerScreen and granular Media3 playback HTTP error classification`() = runBlocking {
        val futureExp = (System.currentTimeMillis() / 1000L) + 3600L
        server.handler = { req ->
            when {
                req.path.startsWith("/movie/808") -> {
                    val html = "<script>window.masterPlaylist = { params: { token: 'player_handoff_tok', expires: $futureExp }, url: 'http://vixsrc.to:${server.port}/playlist/808' };</script>"
                    AuditResponse(200, "text/html", html.toByteArray())
                }
                req.path.startsWith("/playlist/808") -> {
                    AuditResponse(200, "application/vnd.apple.mpegurl", "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=2500000\n/seg.m3u8\n".toByteArray())
                }
                else -> AuditResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }

        val resolved = resolver.resolveStreamForExoPlayer(808, "movie", null, null, lang = "it").getOrThrow()
        assertNotNull(resolved)

        // Verifica handoff reale verso VixPlayerScreen con sorgente VixSrc risolta e verificata
        val movie = MediaItem(
            id = 808,
            mediaType = "movie",
            title = "Shrek Audit Test",
            overview = "Test avvio player con sorgente VixSrc",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.1,
            releaseYear = "2001"
        )
        val session = PlaybackSession(
            mediaItem = movie,
            isResolvingStream = false,
            resolvedStream = resolved,
            metadataDurationMs = 5_400_000L
        )

        composeRule.setContent {
            MyApplicationTheme {
                VixPlayerScreen(
                    session = session,
                    streamLang = "it",
                    vixSrcResolver = resolver,
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    initialDurationMsOverride = 5_400_000L,
                    initialReadyForTest = true
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exo_play_pause_button").assertIsDisplayed()

        // Verifica classificazione granulare errori ExoPlayer (HTTP vs Timeout vs Parser vs Player generico)
        val dataSpec = DataSpec(Uri.parse("https://vixsrc.to/playlist/808?token=secret_val"))
        val http403Cause = HttpDataSource.InvalidResponseCodeException(
            403,
            "Forbidden",
            null,
            emptyMap(),
            dataSpec,
            ByteArray(0)
        )
        val msg403 = formatVixSrcPlaybackError(
            PlaybackException("HTTP 403", http403Cause, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS),
            resolved.streamUrl
        )
        assertTrue("Errore HTTP 403 deve essere segnalato chiaramente", msg403.contains("HTTP 403"))
        assertFalse("Nessun token nei messaggi di errore", msg403.contains("secret_val"))

        val http410Cause = HttpDataSource.InvalidResponseCodeException(
            410,
            "Gone",
            null,
            emptyMap(),
            dataSpec,
            ByteArray(0)
        )
        val msg410 = formatVixSrcPlaybackError(
            PlaybackException("HTTP 410", http410Cause, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS),
            resolved.streamUrl
        )
        assertTrue(msg410.contains("HTTP 410"))

        val timeoutMsg = formatVixSrcPlaybackError(
            PlaybackException("Timeout", SocketTimeoutException("read timed out"), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
            resolved.streamUrl
        )
        assertTrue(timeoutMsg.contains("Timeout"))

        val parserMsg = formatVixSrcPlaybackError(
            PlaybackException("Malformed HLS", ParserException.createForMalformedManifest("bad m3u8", null), PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED),
            resolved.streamUrl
        )
        assertTrue(parserMsg.contains("Manifest HLS"))

        val decoderMsg = formatVixSrcPlaybackError(
            PlaybackException("Decoder init failed", IllegalStateException("codec"), PlaybackException.ERROR_CODE_DECODER_INIT_FAILED),
            resolved.streamUrl
        )
        assertTrue(decoderMsg.contains("Errore player video"))
        assertFalse("Errore decoder non deve essere etichettato come errore HTTP", decoderMsg.contains("HTTP"))

        // Verifica che OkHttpDataSource.Factory costruita con lo stesso client + origin dinamico funzioni senza eccezioni
        val dataSourceFactory = OkHttpDataSource.Factory(resolver.exoOkHttpClient)
            .setUserAgent(resolved.userAgent)
            .setDefaultRequestProperties(mapOf("Referer" to resolved.referer, "Origin" to resolved.origin))
        assertNotNull(dataSourceFactory.createDataSource())
        assertEquals("https://vixsrc.to/playlist/808?token=%3Credacted%3E", VixSrcDiagnostics.sanitizeUrl("https://vixsrc.to/playlist/808?token=my_secret"))
    }

    private data class AuditRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>
    )

    private data class AuditResponse(
        val code: Int,
        val contentType: String,
        val body: ByteArray,
        val extraHeaders: Map<String, String> = emptyMap()
    )

    private class VixSrcAuditServer {
        private var serverSocket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()
        @Volatile private var running = false
        val recordedRequests = CopyOnWriteArrayList<AuditRequest>()
        @Volatile var handler: (AuditRequest) -> AuditResponse = {
            AuditResponse(404, "text/plain", "Not Found".toByteArray())
        }

        val port: Int get() = serverSocket?.localPort ?: 0

        fun start() {
            serverSocket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            running = true
            executor.submit {
                while (running) {
                    val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                    executor.submit { handleClient(socket) }
                }
            }
        }

        fun stop() {
            running = false
            runCatching { serverSocket?.close() }
            executor.shutdownNow()
        }

        private fun handleClient(socket: Socket) {
            socket.use { s ->
                val input = BufferedInputStream(s.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]

                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim().lowercase()
                        val value = line.substring(idx + 1).trim()
                        headers[key] = if (headers.containsKey(key)) "${headers[key]}; $value" else value
                    }
                }

                val auditReq = AuditRequest(method, path, headers)
                recordedRequests.add(auditReq)
                val response = handler(auditReq)
                writeResponse(s, response, headOnly = method == "HEAD")
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

        private fun writeResponse(socket: Socket, response: AuditResponse, headOnly: Boolean) {
            val statusText = when (response.code) {
                200 -> "OK"
                302 -> "Found"
                400 -> "Bad Request"
                401 -> "Unauthorized"
                403 -> "Forbidden"
                404 -> "Not Found"
                410 -> "Gone"
                429 -> "Too Many Requests"
                500 -> "Internal Server Error"
                else -> "Status"
            }
            val headerBuilder = StringBuilder()
            headerBuilder.append("HTTP/1.1 ").append(response.code).append(' ').append(statusText).append("\r\n")
            headerBuilder.append("Content-Type: ").append(response.contentType).append("\r\n")
            headerBuilder.append("Content-Length: ").append(response.body.size).append("\r\n")
            for ((k, v) in response.extraHeaders) {
                headerBuilder.append(k).append(": ").append(v).append("\r\n")
            }
            headerBuilder.append("Connection: close\r\n\r\n")

            val out = socket.getOutputStream()
            out.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
            if (!headOnly && response.body.isNotEmpty()) {
                out.write(response.body)
            }
            out.flush()
        }
    }
}
