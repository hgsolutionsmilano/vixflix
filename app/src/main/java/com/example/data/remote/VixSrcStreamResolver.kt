package com.example.data.remote

import androidx.compose.runtime.Immutable
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONException
import org.json.JSONObject

@Immutable
data class ResolvedStream(
    val streamUrl: String,
    val embedUrl: String,
    val referer: String = "https://vixsrc.to/",
    val origin: String = "https://vixsrc.to",
    val userAgent: String = DEFAULT_USER_AGENT,
    val startSeconds: Long = 0L,
    val startMinutes: Int = 0,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val isDirectHls: Boolean = true,
    val vixCatalogVerified: Boolean = true,
    val expiresAtMs: Long = 0L,
    val fromCache: Boolean = false
) {
    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

@Immutable
@JsonClass(generateAdapter = true)
data class SubtitleTrack(
    val label: String,
    val language: String,
    val url: String
)

/**
 * CookieJar in-memory thread-safe per preservare i cookie di sessione/routing tra pagina embed,
 * API, playlist HLS (.m3u8), chiave AES-128 e segmenti video esattamente come fa il browser
 * sul sito ufficiale VixSrc. Non persiste né logga alcun valore di cookie.
 */
internal class InMemoryVixSrcCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, CopyOnWriteArrayList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        cookies.forEach { incoming ->
            val domainKey = incoming.domain.lowercase(Locale.US)
            val list = store.computeIfAbsent(domainKey) { CopyOnWriteArrayList() }
            list.removeAll { existing ->
                existing.name == incoming.name &&
                    existing.domain.equals(incoming.domain, ignoreCase = true) &&
                    existing.path == incoming.path
            }
            if (incoming.expiresAt > now) {
                list.add(incoming)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val matching = mutableListOf<Cookie>()
        store.entries.forEach { (_, list) ->
            list.removeAll { it.expiresAt <= now }
            list.forEach { cookie ->
                if (cookie.matches(url)) {
                    matching.add(cookie)
                }
            }
        }
        return matching
    }

    fun clear() {
        store.clear()
    }
}

/**
 * Interceptor OkHttp standard-compliant per VixSrc e Media3 ExoPlayer:
 * 1. Invia richieste dirette e pulite agli endpoint ufficiali (senza riscritture path-traversal
 *    che causano HTTP 400/403/404 sui server/CDN moderni).
 * 2. Applica `User-Agent` browser coerente senza forzare `Origin` su navigazioni GET HTML o HEAD.
 * 3. Assicura `Content-Type: video/mp2t` esclusivamente sui segmenti binari HLS con risposta 2xx.
 * 4. Risolve eventuali URI relativi nelle chiavi AES-128 (`#EXT-X-KEY` / `/storage/enc.key`)
 *    rispetto all'URL reale finale dopo eventuali redirect (`response.request.url`).
 */
class VixSrcOkHttpInterceptor : Interceptor {
    companion object {
        private val KEY_URI_REGEX = Regex("""(#EXT-X-KEY:[^\r\n]*URI=)(["'])([^"']+)\2""")
        private val HLS_MEDIA_TYPE = "application/vnd.apple.mpegurl".toMediaType()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        if (originalRequest.header("User-Agent") == null) {
            requestBuilder.header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
        }
        if (originalRequest.header("Referer") == null) {
            requestBuilder.header("Referer", "https://vixsrc.to/")
        }

        // Non inviare Origin su navigazioni documento HTML o richieste HEAD (comportamento browser reale)
        val acceptHeader = originalRequest.header("Accept").orEmpty()
        val isDocumentNavigation = acceptHeader.contains("text/html", ignoreCase = true)
        val isHeadRequest = originalRequest.method.equals("HEAD", ignoreCase = true)
        if (isDocumentNavigation || isHeadRequest) {
            requestBuilder.removeHeader("Origin")
        }

        val response = chain.proceed(requestBuilder.build())
        if (isHeadRequest) {
            return response
        }

        val finalUrl = response.request.url
        val finalPath = finalUrl.encodedPath.lowercase(Locale.US)

        // Fast-path per segmenti HLS 2xx serviti con estensione .html/.m4s/.ts dai CDN VixSrc
        if (response.isSuccessful &&
            finalPath.contains("/hls/") &&
            (finalPath.endsWith(".html") || finalPath.endsWith(".m4s") || finalPath.endsWith(".ts"))
        ) {
            return response.newBuilder()
                .header("Content-Type", "video/mp2t")
                .build()
        }

        // Se è una playlist HLS (.m3u8) valida, risolve URI relativi in #EXT-X-KEY rispetto all'host finale post-redirect
        val contentType = response.header("Content-Type").orEmpty()
        val isM3u8Candidate = finalPath.contains("/playlist/") ||
            finalPath.endsWith(".m3u8") ||
            contentType.contains("mpegurl", ignoreCase = true)

        if (response.isSuccessful && isM3u8Candidate) {
            val rawBody = response.body?.string().orEmpty()
            val cleanedManifest = rawBody.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            if (cleanedManifest.startsWith("#EXTM3U")) {
                val fixedManifest = KEY_URI_REGEX.replace(cleanedManifest) { match ->
                    val prefix = match.groupValues[1]
                    val quote = match.groupValues[2]
                    val rawKeyUri = match.groupValues[3].trim()
                    val resolvedKeyUri = finalUrl.resolve(rawKeyUri)?.toString() ?: rawKeyUri
                    "$prefix$quote$resolvedKeyUri$quote"
                }
                val newBody = fixedManifest.toResponseBody(HLS_MEDIA_TYPE)
                return response.newBuilder()
                    .header("Content-Type", "application/vnd.apple.mpegurl")
                    .body(newBody)
                    .build()
            } else {
                val fallbackBody = rawBody.toResponseBody(response.body?.contentType())
                return response.newBuilder().body(fallbackBody).build()
            }
        }

        return response
    }
}

class VixSrcStreamResolver(
    baseOkHttpClient: OkHttpClient = OkHttpClient(),
    rawBaseUrl: String = BuildConfig.VIXSRC_BASE_URL
) {
    companion object {
        private const val MAX_DIAGNOSTICS_HISTORY = 64
        private const val MAX_IFRAME_DEPTH = 2
        private const val MAX_TEXT_BODY_BYTES = 2L * 1024L * 1024L

        private val TMDB_ID_REGEX = Regex(""""tmdb_id"\s*:\s*(\d+)""")
        private val EXPIRES_QUERY_REGEX = Regex("""[?&]expires=(\d+)""")
        private val MASTER_PLAYLIST_START_REGEX = Regex(
            """window\.masterPlaylist\s*=\s*\{""",
            RegexOption.IGNORE_CASE
        )
        private val PARAMS_START_REGEX = Regex(
            """['"]?params['"]?\s*:\s*\{""",
            RegexOption.IGNORE_CASE
        )
        private val URL_PROPERTY_REGEX = Regex(
            """['"]?url['"]?\s*:\s*(['"])([^'"]+)\1""",
            RegexOption.IGNORE_CASE
        )
        private val PLAYLIST_URL_FALLBACK_REGEX = Regex(
            """['"]?url['"]?\s*:\s*(['"])([^'"]*(?:playlist|\.m3u8)[^'"]*)\1""",
            RegexOption.IGNORE_CASE
        )
        // Supporta sia chiavi con apici che senza apici e valori stringa, numerici o booleani
        private val JS_KV_REGEX = Regex(
            """(?:['"]([^'"]+)['"]|([A-Za-z_][A-Za-z0-9_]*))\s*:\s*(?:['"]([^'"]*)['"]|(-?\d+(?:\.\d+)?|true|false))"""
        )
        private val CAN_PLAY_FHD_REGEX = Regex(
            """window\.canPlayFHD\s*=\s*(true|false)""",
            RegexOption.IGNORE_CASE
        )
        private val TOKEN_FALLBACK_REGEX = Regex("""['"]?token['"]?\s*:\s*['"]([^'"]+)['"]""")
        private val EXPIRES_FALLBACK_REGEX = Regex("""['"]?expires['"]?\s*:\s*['"]?(\d+)['"]?""")
        private val IFRAME_SRC_REGEX = Regex(
            """<iframe\b[^>]*\bsrc\s*=\s*(['"])([^'"]+)\1""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val STREAMS_BLOCK_REGEX = Regex(
            """window\.streams\s*=\s*(\[.*?\]);?""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val STREAM_URL_REGEX = Regex("""["']?(?:url|file|src)["']?\s*:\s*["']([^"']+)["']""")
        private val DIRECT_M3U8_REGEX = Regex("""https?:(?:\\/|/)[^\s"'<>]+?\.m3u8[^\s"'<>]*""")
        private val SUBTITLE_REGEX = Regex("""https?:(?:\\/|/)[^\s"'<>]+?\.(?:vtt|srt)[^\s"'<>]*""")

        /**
         * Estrae il blocco bilanciato `{ ... }` a partire dall'indice della parentesi graffa di apertura,
         * gestendo correttamente oggetti annidati (come `params: { ... }` dentro `window.masterPlaylist`)
         * e stringhe con caratteri escapati.
         */
        internal fun extractBalancedBraceContent(source: String, openBraceIndex: Int): String? {
            if (openBraceIndex < 0 || openBraceIndex >= source.length || source[openBraceIndex] != '{') {
                return null
            }
            var depth = 0
            var inQuote: Char? = null
            var escaped = false
            for (i in openBraceIndex until source.length) {
                val ch = source[i]
                if (inQuote != null) {
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == inQuote) {
                        inQuote = null
                    }
                    continue
                }
                when (ch) {
                    '\'', '"' -> inQuote = ch
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return source.substring(openBraceIndex + 1, i)
                        }
                    }
                }
            }
            return null
        }

        /**
         * Pulisce gli escape comuni negli HTML/script JS (`\/`, `\u0026`, `&amp;`).
         */
        internal fun decodeExtractedUrl(rawUrl: String): String = rawUrl
            .trim()
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("&amp;", "&")

        /**
         * Estrae metadati reali di intro (`start` e `end` in secondi -> convertiti in ms) eventualmente
         * dichiarati nella pagina embed/player VixSrc (es. `window.intro = { start: 12, end: 84 }` o `"intro":{"start":12,"end":84}`).
         * Non inventa mai alcun intervallo se il metadato non è presente o non è valido.
         */
        fun parseIntroSegmentFromEmbedHtml(html: String): Pair<Long, Long>? {
            if (html.isBlank()) return null
            val introBlockRegex = Regex(
                """(?:window\.(?:intro|skipIntro)\s*=\s*|["'](?:intro|skipIntro|introSegment)["']\s*:\s*)\{([^}]+)\}""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val block = introBlockRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
            val startRegex = Regex("""["']?(?:start|startTime|introStart)["']?\s*:\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
            val endRegex = Regex("""["']?(?:end|endTime|introEnd)["']?\s*:\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
            val rawStart = startRegex.find(block)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
            val rawEnd = endRegex.find(block)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
            val startMs = if (rawEnd > 10_000.0) rawStart.toLong() else (rawStart * 1000.0).toLong()
            val endMs = if (rawEnd > 10_000.0) rawEnd.toLong() else (rawEnd * 1000.0).toLong()
            if (startMs < 0L || endMs <= startMs + 3_000L) return null
            return startMs to endMs
        }
    }

    private data class HttpFetchResult(
        val finalUrl: String,
        val statusCode: Int,
        val contentType: String?,
        val body: String,
        val responseTimeMs: Long,
        val redirectChain: List<String>
    )

    private val baseUrl = rawBaseUrl.trimEnd('/')
    private val catalogMutex = Mutex()
    private val cachedMovieIds = mutableSetOf<Int>()
    private val cachedTvIds = mutableSetOf<Int>()
    private val prewarmInFlight = AtomicBoolean(false)
    private val lastPrewarmMs = AtomicLong(0L)
    private val cookieJar = InMemoryVixSrcCookieJar()
    private val inFlightResolutions = ConcurrentHashMap<String, CompletableDeferred<Result<ResolvedStream>>>()
    private val diagnosticsHistory = ArrayDeque<VixSrcDiagnosticEvent>(MAX_DIAGNOSTICS_HISTORY)
    private val diagnosticsLock = Any()

    /**
     * Restituisce uno snapshot immutabile degli ultimi eventi diagnostici VixSrc (privi di token/cookie).
     */
    fun getRecentDiagnostics(): List<VixSrcDiagnosticEvent> = synchronized(diagnosticsLock) {
        diagnosticsHistory.toList()
    }

    internal fun clearDiagnosticsForTest() = synchronized(diagnosticsLock) {
        diagnosticsHistory.clear()
    }

    private fun recordDiagnostic(event: VixSrcDiagnosticEvent) {
        synchronized(diagnosticsLock) {
            if (diagnosticsHistory.size >= MAX_DIAGNOSTICS_HISTORY) {
                diagnosticsHistory.removeFirst()
            }
            diagnosticsHistory.addLast(event)
        }
        VixSrcDiagnostics.logEvent(event)
    }

    /**
     * Dispatcher e ConnectionPool dedicati esclusivamente allo streaming video (risoluzione + HLS ExoPlayer),
     * isolati dalle richieste catalogo TMDB e dal download automatico degli aggiornamenti APK.
     */
    private val streamingDispatcher = Dispatcher().apply {
        maxRequests = 48
        maxRequestsPerHost = 10
    }

    private val streamingConnectionPool = ConnectionPool(6, 5, TimeUnit.MINUTES)

    /**
     * Client OkHttp condiviso con `OkHttpDataSource.Factory` di Media3 ExoPlayer.
     * Include gestione CookieJar in-memory per mantenere i cookie di sessione tra risoluzione e streaming.
     */
    val exoOkHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .dispatcher(streamingDispatcher)
        .connectionPool(streamingConnectionPool)
        .cookieJar(cookieJar)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(VixSrcOkHttpInterceptor())
        .build()

    private val resolverApiClient: OkHttpClient = exoOkHttpClient.newBuilder()
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    private val prewarmApiClient: OkHttpClient = exoOkHttpClient.newBuilder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun buildAcceptLanguage(lang: String): String {
        val normalized = lang.trim().lowercase(Locale.US)
        return if (normalized == "en") {
            "en-US,en;q=0.9,it;q=0.7"
        } else {
            "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7"
        }
    }

    /**
     * Pre-riscalda in modo leggero e asincrono DNS, handshake TLS e socket keep-alive verso i server video
     * subito dopo l'apertura dell'app, senza scaricare JSON pesanti né competere con la riproduzione.
     */
    suspend fun prewarmConnection() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastPrewarmMs.get() < 60_000L) return@withContext
        if (!prewarmInFlight.compareAndSet(false, true)) return@withContext
        try {
            val headRequest = Request.Builder()
                .url("$baseUrl/api/list/movie/?lang=it")
                .head()
                .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                .header("Accept", "application/json, */*")
                .build()
            prewarmApiClient
                .newCall(headRequest)
                .execute()
                .close()
            lastPrewarmMs.set(System.currentTimeMillis())
        } catch (_: Throwable) {
            // Solo ottimizzazione preventiva non bloccante
        } finally {
            prewarmInFlight.set(false)
        }
    }

    /**
     * Costruisce l'URL API/embed ufficiale di vixsrc.to passando i parametri supportati dal sito ufficiale
     * (`lang`, `autoplay`, `primaryColor`, `secondaryColor`, `startAt`).
     */
    fun buildVixSrcApiUrl(
        tmdbId: Int,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
        startSeconds: Long = 0L,
        lang: String = "it"
    ): String {
        val safeSeconds = startSeconds.coerceAtLeast(0L)
        val safeLang = lang.trim().ifBlank { "it" }
        val path = if (mediaType == "tv") {
            val s = season ?: 1
            val e = episode ?: 1
            "$baseUrl/tv/$tmdbId/$s/$e"
        } else {
            "$baseUrl/movie/$tmdbId"
        }
        return buildString {
            append(path)
            append("?lang=").append(safeLang)
            append("&autoplay=true")
            append("&primaryColor=E50914")
            append("&secondaryColor=B20710")
            if (safeSeconds > 0L) {
                append("&startAt=").append(safeSeconds)
            }
        }
    }

    /**
     * Costruisce l'URL pagina ufficiale pulita di VixSrc (`https://vixsrc.to/movie/{id}?lang={lang}`
     * o `https://vixsrc.to/tv/{id}/{season}/{episode}?lang={lang}`).
     */
    internal fun buildCleanEmbedPageUrl(
        tmdbId: Int,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
        lang: String = "it"
    ): String {
        val safeLang = lang.trim().ifBlank { "it" }
        return if (mediaType == "tv") {
            val s = season ?: 1
            val e = episode ?: 1
            "$baseUrl/tv/$tmdbId/$s/$e?lang=$safeLang"
        } else {
            "$baseUrl/movie/$tmdbId?lang=$safeLang"
        }
    }

    /**
     * Verifica la presenza del contenuto nell'endpoint JSON live `https://vixsrc.to/api/list/{type}?lang=it`.
     */
    suspend fun checkVixSrcCatalogAvailability(
        tmdbId: Int,
        mediaType: String,
        lang: String = "it"
    ): Boolean = withContext(Dispatchers.IO) {
        val targetSet = if (mediaType == "tv") cachedTvIds else cachedMovieIds
        catalogMutex.withLock {
            if (targetSet.isNotEmpty()) {
                return@withContext targetSet.contains(tmdbId)
            }
        }

        val endpointType = if (mediaType == "tv") "tv" else "movie"
        val listUrl = "$baseUrl/api/list/$endpointType?lang=${lang.trim().ifBlank { "it" }}"
        val request = Request.Builder()
            .url(listUrl)
            .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", buildAcceptLanguage(lang))
            .header("Referer", "$baseUrl/")
            .build()

        runCatching {
            val fetched = executeFetchWithDiagnostics(
                request = request,
                phase = VixSrcResolutionPhase.CATALOG_CHECK,
                maxAttempts = 1
            )
            if (fetched.statusCode in 200..299) {
                val ids = TMDB_ID_REGEX.findAll(fetched.body).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
                if (ids.isNotEmpty()) {
                    catalogMutex.withLock {
                        targetSet.addAll(ids)
                    }
                    return@withContext ids.contains(tmdbId)
                }
            }
            true
        }.getOrDefault(true)
    }

    /**
     * Risolve il flusso HLS reale (.m3u8 / masterPlaylist) da vixsrc.to replicando fedelmente
     * il flusso HTTP del sito ufficiale:
     * 1. Deduplica eventuali richieste concorrenti identiche per evitare chiamate doppie / 429.
     * 2. Richiede la pagina ufficiale `/movie/{tmdbId}?lang=...` o `/tv/{tmdbId}/{s}/{e}?lang=...`
     *    seguendo eventuali redirect, cookie di sessione e `<iframe>` embed intermedi.
     * 3. Se la pagina non espone direttamente la sorgente, interroga l'endpoint JSON `/api/movie/...`
     *    o `/api/tv/...` parsando la risposta con `JSONObject` e seguendo l'embed restituito.
     * 4. Verifica concretamente che l'URL HLS estratto risponda con HTTP 200 e un manifest `#EXTM3U`
     *    valido prima di consegnarlo a ExoPlayer (evitando URL incompleti, HTML di errore o token invalidi).
     */
    suspend fun resolveStreamForExoPlayer(
        tmdbId: Int,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
        startSeconds: Long = 0L,
        lang: String = "it"
    ): Result<ResolvedStream> {
        val safeSeconds = startSeconds.coerceAtLeast(0L)
        val startMinutes = (safeSeconds / 60L).toInt()
        val normalizedLang = lang.trim().ifBlank { "it" }.lowercase(Locale.US)
        val dedupeKey = "${mediaType.lowercase(Locale.US)}_${tmdbId}_${season ?: 0}_${episode ?: 0}_$normalizedLang"

        val existingDeferred = inFlightResolutions[dedupeKey]
        if (existingDeferred != null && existingDeferred.isActive) {
            return existingDeferred.await().map { resolved ->
                resolved.copy(startSeconds = safeSeconds, startMinutes = startMinutes)
            }
        }

        val createdDeferred = CompletableDeferred<Result<ResolvedStream>>()
        val raceWinner = inFlightResolutions.putIfAbsent(dedupeKey, createdDeferred)
        if (raceWinner != null && raceWinner.isActive) {
            return raceWinner.await().map { resolved ->
                resolved.copy(startSeconds = safeSeconds, startMinutes = startMinutes)
            }
        }

        return try {
            val result = withContext(Dispatchers.IO) {
                resolveStreamInternal(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    season = season,
                    episode = episode,
                    startSeconds = safeSeconds,
                    startMinutes = startMinutes,
                    lang = normalizedLang
                )
            }
            createdDeferred.complete(result)
            result
        } catch (t: Throwable) {
            val failure = Result.failure<ResolvedStream>(t)
            createdDeferred.complete(failure)
            failure
        } finally {
            inFlightResolutions.remove(dedupeKey, createdDeferred)
        }
    }

    private suspend fun resolveStreamInternal(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        startSeconds: Long,
        startMinutes: Int,
        lang: String
    ): Result<ResolvedStream> = runCatching {
        val publicEmbedUrl = buildVixSrcApiUrl(
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            startSeconds = startSeconds,
            lang = lang
        )
        val cleanPageUrl = buildCleanEmbedPageUrl(
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            lang = lang
        )
        val acceptLanguage = buildAcceptLanguage(lang)

        var primaryFailure: VixSrcException? = null
        var candidatePlaylistUrl: String? = null
        var candidateSubtitles: List<SubtitleTrack> = emptyList()
        var effectiveReferer = cleanPageUrl

        // STEP 1: Richiesta diretta alla pagina ufficiale VixSrc (/movie/{id} o /tv/{id}/{s}/{e})
        val pageRequest = Request.Builder()
            .url(cleanPageUrl)
            .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", acceptLanguage)
            .header("Referer", "$baseUrl/")
            .build()

        val pageOutcome = runCatching {
            val pageFetch = executeFetchWithDiagnostics(
                request = pageRequest,
                phase = VixSrcResolutionPhase.EMBED_PAGE
            )
            if (pageFetch.statusCode !in 200..299) {
                throw buildHttpException(pageFetch, VixSrcResolutionPhase.EMBED_PAGE, "GET")
            }
            extractFromPageOrEmbed(
                fetch = pageFetch,
                lang = lang,
                acceptLanguage = acceptLanguage,
                remainingIframeDepth = MAX_IFRAME_DEPTH
            )
        }

        pageOutcome.fold(
            onSuccess = { extracted ->
                if (extracted != null) {
                    candidatePlaylistUrl = extracted.playlistUrl
                    candidateSubtitles = extracted.subtitles
                    effectiveReferer = extracted.refererUrl
                }
            },
            onFailure = { err ->
                primaryFailure = err as? VixSrcException ?: VixSrcException(
                    diagnostic = VixSrcDiagnosticEvent(
                        phase = VixSrcResolutionPhase.EMBED_PAGE,
                        method = "GET",
                        sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(cleanPageUrl),
                        category = VixSrcDiagnostics.classifyThrowable(err),
                        errorDetail = err.message
                    ),
                    userMessage = VixSrcDiagnostics.formatNetworkUserMessage(err, VixSrcResolutionPhase.EMBED_PAGE),
                    cause = err
                )
            }
        )

        // STEP 2: Fallback API JSON (/api/movie/{id} o /api/tv/{id}/{s}/{e}) se Step 1 non ha restituito la sorgente
        if (candidatePlaylistUrl.isNullOrBlank()) {
            val apiEndpointUrl = if (mediaType == "tv") {
                val s = season ?: 1
                val e = episode ?: 1
                "$baseUrl/api/tv/$tmdbId/$s/$e?lang=$lang"
            } else {
                "$baseUrl/api/movie/$tmdbId?lang=$lang"
            }
            val apiRequest = Request.Builder()
                .url(apiEndpointUrl)
                .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", acceptLanguage)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", cleanPageUrl)
                .build()

            val apiFetch = try {
                executeFetchWithDiagnostics(
                    request = apiRequest,
                    phase = VixSrcResolutionPhase.API_LOOKUP
                )
            } catch (apiErr: Throwable) {
                // Se anche lo step API fallisce, rilancia l'errore più informativo (preferendo HTTP reale rispetto a 404 secondario)
                throw primaryFailure ?: (apiErr as? VixSrcException ?: VixSrcException(
                    diagnostic = VixSrcDiagnosticEvent(
                        phase = VixSrcResolutionPhase.API_LOOKUP,
                        method = "GET",
                        sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(apiEndpointUrl),
                        category = VixSrcDiagnostics.classifyThrowable(apiErr),
                        errorDetail = apiErr.message
                    ),
                    userMessage = VixSrcDiagnostics.formatNetworkUserMessage(apiErr, VixSrcResolutionPhase.API_LOOKUP),
                    cause = apiErr
                ))
            }

            if (apiFetch.statusCode !in 200..299) {
                val apiHttpError = buildHttpException(apiFetch, VixSrcResolutionPhase.API_LOOKUP, "GET")
                // Preferisce errori HTTP specifici di Step 1 (es. 403/429/5xx) se Step 2 risponde 404
                val preferred = if (primaryFailure != null &&
                    primaryFailure!!.httpStatusCode != null &&
                    primaryFailure!!.httpStatusCode != 404 &&
                    apiFetch.statusCode == 404
                ) {
                    primaryFailure!!
                } else {
                    apiHttpError
                }
                throw preferred
            }

            val embedTargetUrl = parseApiTargetUrl(apiFetch)
            if (isDirectPlaylistUrl(embedTargetUrl)) {
                candidatePlaylistUrl = embedTargetUrl
                effectiveReferer = apiFetch.finalUrl
            } else {
                val embedRequest = Request.Builder()
                    .url(embedTargetUrl)
                    .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", acceptLanguage)
                    .header("Referer", cleanPageUrl)
                    .build()

                val embedFetch = executeFetchWithDiagnostics(
                    request = embedRequest,
                    phase = VixSrcResolutionPhase.IFRAME_EMBED
                )
                if (embedFetch.statusCode !in 200..299) {
                    throw buildHttpException(embedFetch, VixSrcResolutionPhase.IFRAME_EMBED, "GET")
                }

                val extracted = extractFromPageOrEmbed(
                    fetch = embedFetch,
                    lang = lang,
                    acceptLanguage = acceptLanguage,
                    remainingIframeDepth = MAX_IFRAME_DEPTH - 1
                ) ?: run {
                    val event = VixSrcDiagnosticEvent(
                        phase = VixSrcResolutionPhase.PLAYLIST_EXTRACTION,
                        method = "GET",
                        sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(embedFetch.finalUrl),
                        httpStatusCode = embedFetch.statusCode,
                        responseTimeMs = embedFetch.responseTimeMs,
                        redirectCount = embedFetch.redirectChain.size,
                        redirectChain = embedFetch.redirectChain,
                        contentType = embedFetch.contentType,
                        category = VixSrcErrorCategory.PLAYLIST_EXTRACTION_ERROR,
                        errorDetail = "Nessuna masterPlaylist o sorgente HLS trovata nel documento embed"
                    )
                    recordDiagnostic(event)
                    throw VixSrcException(
                        diagnostic = event,
                        userMessage = "Sorgente video HLS non trovata nella pagina player VixSrc."
                    )
                }

                candidatePlaylistUrl = extracted.playlistUrl
                candidateSubtitles = extracted.subtitles
                effectiveReferer = extracted.refererUrl
            }
        }

        val resolvedPlaylistUrl = candidatePlaylistUrl?.takeIf { VixSrcDiagnostics.isSafeHttpUrl(it) }
            ?: run {
                val event = VixSrcDiagnosticEvent(
                    phase = VixSrcResolutionPhase.PLAYLIST_EXTRACTION,
                    method = "GET",
                    sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(effectiveReferer),
                    category = VixSrcErrorCategory.PLAYLIST_EXTRACTION_ERROR,
                    errorDetail = "URL playlist estratto mancante o schema non valido"
                )
                recordDiagnostic(event)
                throw VixSrcException(
                    diagnostic = event,
                    userMessage = "URL streaming VixSrc non valido o incompleto."
                )
            }

        // STEP 3: Verifica reale del manifest HLS (#EXTM3U) prima di passarlo a ExoPlayer
        val effectiveOrigin = VixSrcDiagnostics.extractOrigin(effectiveReferer, fallback = baseUrl)
        val verifiedPlaylistUrl = verifyPlayableHlsManifest(
            playlistUrl = resolvedPlaylistUrl,
            referer = effectiveReferer,
            origin = effectiveOrigin,
            acceptLanguage = acceptLanguage
        )

        val expiresAtMs = computeSafeExpiresAtMs(verifiedPlaylistUrl)

        ResolvedStream(
            streamUrl = verifiedPlaylistUrl,
            embedUrl = publicEmbedUrl,
            referer = effectiveReferer,
            origin = effectiveOrigin,
            startSeconds = startSeconds,
            startMinutes = startMinutes,
            subtitles = candidateSubtitles,
            isDirectHls = true,
            vixCatalogVerified = true,
            expiresAtMs = expiresAtMs,
            fromCache = false
        )
    }

    private data class ExtractedSource(
        val playlistUrl: String,
        val subtitles: List<SubtitleTrack>,
        val refererUrl: String
    )

    private suspend fun extractFromPageOrEmbed(
        fetch: HttpFetchResult,
        lang: String,
        acceptLanguage: String,
        remainingIframeDepth: Int
    ): ExtractedSource? {
        val body = fetch.body
        val trimmed = body.trimStart()

        // Se l'endpoint risponde direttamente con JSON, prova a risolverlo
        if (trimmed.startsWith("{")) {
            val jsonTarget = runCatching { parseApiTargetUrl(fetch) }.getOrNull()
            if (!jsonTarget.isNullOrBlank()) {
                if (isDirectPlaylistUrl(jsonTarget)) {
                    return ExtractedSource(
                        playlistUrl = jsonTarget,
                        subtitles = emptyList(),
                        refererUrl = fetch.finalUrl
                    )
                }
                if (remainingIframeDepth > 0 && !jsonTarget.equals(fetch.finalUrl, ignoreCase = true)) {
                    val nestedReq = Request.Builder()
                        .url(jsonTarget)
                        .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", acceptLanguage)
                        .header("Referer", fetch.finalUrl)
                        .build()
                    val nestedFetch = executeFetchWithDiagnostics(nestedReq, VixSrcResolutionPhase.IFRAME_EMBED)
                    if (nestedFetch.statusCode !in 200..299) {
                        throw buildHttpException(nestedFetch, VixSrcResolutionPhase.IFRAME_EMBED, "GET")
                    }
                    return extractFromPageOrEmbed(nestedFetch, lang, acceptLanguage, remainingIframeDepth - 1)
                }
            }
        }

        // Estrazione diretta masterPlaylist / streams / m3u8 dall'HTML corrente
        val directPlaylist = parseMasterPlaylistFromHtml(
            html = body,
            pageUrl = fetch.finalUrl,
            lang = lang,
            startSeconds = 0L
        )
        if (!directPlaylist.isNullOrBlank()) {
            return ExtractedSource(
                playlistUrl = directPlaylist,
                subtitles = parseSubtitlesFromHtml(body, fetch.finalUrl),
                refererUrl = fetch.finalUrl
            )
        }

        // Segue eventuale <iframe src="..."> intermedio (es. pagina wrapper VixSrc -> iframe embed)
        if (remainingIframeDepth > 0) {
            val iframeUrl = extractCandidateIframeUrl(body, fetch.finalUrl)
            if (!iframeUrl.isNullOrBlank() && !iframeUrl.equals(fetch.finalUrl, ignoreCase = true)) {
                val iframeRequest = Request.Builder()
                    .url(iframeUrl)
                    .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", acceptLanguage)
                    .header("Referer", fetch.finalUrl)
                    .build()

                val iframeFetch = executeFetchWithDiagnostics(
                    request = iframeRequest,
                    phase = VixSrcResolutionPhase.IFRAME_EMBED
                )
                if (iframeFetch.statusCode !in 200..299) {
                    throw buildHttpException(iframeFetch, VixSrcResolutionPhase.IFRAME_EMBED, "GET")
                }
                return extractFromPageOrEmbed(
                    fetch = iframeFetch,
                    lang = lang,
                    acceptLanguage = acceptLanguage,
                    remainingIframeDepth = remainingIframeDepth - 1
                )
            }
        }

        return null
    }

    private fun extractCandidateIframeUrl(html: String, basePageUrl: String): String? {
        for (match in IFRAME_SRC_REGEX.findAll(html)) {
            val rawSrc = decodeExtractedUrl(match.groupValues[2])
            if (rawSrc.isBlank() ||
                rawSrc.startsWith("about:", ignoreCase = true) ||
                rawSrc.startsWith("javascript:", ignoreCase = true) ||
                rawSrc.startsWith("data:", ignoreCase = true)
            ) {
                continue
            }
            val resolved = resolveRelativeUrl(rawSrc, basePageUrl)
            if (VixSrcDiagnostics.isSafeHttpUrl(resolved)) {
                return resolved
            }
        }
        return null
    }

    private fun parseApiTargetUrl(apiFetch: HttpFetchResult): String {
        val rawBody = apiFetch.body.trim()
        val parsedJson = try {
            JSONObject(rawBody)
        } catch (jsonEx: JSONException) {
            val event = VixSrcDiagnosticEvent(
                phase = VixSrcResolutionPhase.API_LOOKUP,
                method = "GET",
                sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(apiFetch.finalUrl),
                httpStatusCode = apiFetch.statusCode,
                responseTimeMs = apiFetch.responseTimeMs,
                redirectCount = apiFetch.redirectChain.size,
                redirectChain = apiFetch.redirectChain,
                contentType = apiFetch.contentType,
                category = VixSrcErrorCategory.JSON_PARSE_ERROR,
                errorDetail = "JSON non valido dalla risposta API VixSrc: ${jsonEx.message}"
            )
            recordDiagnostic(event)
            throw VixSrcException(
                diagnostic = event,
                userMessage = "Risposta non valida dall'API VixSrc (formato JSON non riconosciuto).",
                cause = jsonEx
            )
        }

        val dataObj = parsedJson.optJSONObject("data")
        val rawTarget = listOfNotNull(
            parsedJson.optString("src").takeIf { it.isNotBlank() },
            parsedJson.optString("embed").takeIf { it.isNotBlank() },
            parsedJson.optString("embed_url").takeIf { it.isNotBlank() },
            parsedJson.optString("url").takeIf { it.isNotBlank() },
            parsedJson.optString("playlist").takeIf { it.isNotBlank() },
            parsedJson.optString("stream").takeIf { it.isNotBlank() },
            dataObj?.optString("src")?.takeIf { it.isNotBlank() },
            dataObj?.optString("embed")?.takeIf { it.isNotBlank() },
            dataObj?.optString("url")?.takeIf { it.isNotBlank() }
        ).firstOrNull()

        if (rawTarget.isNullOrBlank()) {
            val event = VixSrcDiagnosticEvent(
                phase = VixSrcResolutionPhase.API_LOOKUP,
                method = "GET",
                sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(apiFetch.finalUrl),
                httpStatusCode = apiFetch.statusCode,
                responseTimeMs = apiFetch.responseTimeMs,
                redirectCount = apiFetch.redirectChain.size,
                redirectChain = apiFetch.redirectChain,
                contentType = apiFetch.contentType,
                category = VixSrcErrorCategory.PLAYLIST_EXTRACTION_ERROR,
                errorDetail = "Campo sorgente (src/url/embed) assente nel JSON API"
            )
            recordDiagnostic(event)
            throw VixSrcException(
                diagnostic = event,
                userMessage = "Nessun flusso disponibile su VixSrc per questo titolo."
            )
        }

        val resolved = resolveRelativeUrl(decodeExtractedUrl(rawTarget), apiFetch.finalUrl)
        if (!VixSrcDiagnostics.isSafeHttpUrl(resolved)) {
            val event = VixSrcDiagnosticEvent(
                phase = VixSrcResolutionPhase.API_LOOKUP,
                method = "GET",
                sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(apiFetch.finalUrl),
                httpStatusCode = apiFetch.statusCode,
                responseTimeMs = apiFetch.responseTimeMs,
                contentType = apiFetch.contentType,
                category = VixSrcErrorCategory.PLAYLIST_EXTRACTION_ERROR,
                errorDetail = "URL sorgente non sicuro o incompleto restituito dall'API"
            )
            recordDiagnostic(event)
            throw VixSrcException(
                diagnostic = event,
                userMessage = "URL sorgente restituito da VixSrc non valido."
            )
        }
        return resolved
    }

    private fun isDirectPlaylistUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains(".m3u8") || lower.contains("/playlist/")
    }

    private suspend fun verifyPlayableHlsManifest(
        playlistUrl: String,
        referer: String,
        origin: String,
        acceptLanguage: String
    ): String {
        val verifyRequest = Request.Builder()
            .url(playlistUrl)
            .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
            .header("Accept", "application/vnd.apple.mpegurl, application/x-mpegurl, */*")
            .header("Accept-Language", acceptLanguage)
            .header("Referer", referer)
            .header("Origin", origin)
            .build()

        val verifyFetch = executeFetchWithDiagnostics(
            request = verifyRequest,
            phase = VixSrcResolutionPhase.PLAYLIST_VERIFICATION
        )

        if (verifyFetch.statusCode !in 200..299) {
            throw buildHttpException(verifyFetch, VixSrcResolutionPhase.PLAYLIST_VERIFICATION, "GET")
        }

        val cleaned = verifyFetch.body.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val isValidM3u8 = cleaned.startsWith("#EXTM3U") && (
            cleaned.contains("#EXT-X-STREAM-INF", ignoreCase = true) ||
                cleaned.contains("#EXTINF", ignoreCase = true) ||
                cleaned.contains("#EXT-X-MEDIA", ignoreCase = true) ||
                cleaned.contains("#EXT-X-TARGETDURATION", ignoreCase = true)
            )

        if (!isValidM3u8) {
            val isHtmlResponse = verifyFetch.contentType.orEmpty().contains("text/html", ignoreCase = true) ||
                cleaned.startsWith("<!DOCTYPE", ignoreCase = true) ||
                cleaned.startsWith("<html", ignoreCase = true)
            val category = if (isHtmlResponse) {
                VixSrcErrorCategory.INVALID_CONTENT_TYPE
            } else {
                VixSrcErrorCategory.INVALID_PLAYLIST_MANIFEST
            }
            val event = VixSrcDiagnosticEvent(
                phase = VixSrcResolutionPhase.PLAYLIST_VERIFICATION,
                method = "GET",
                sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(verifyFetch.finalUrl),
                httpStatusCode = verifyFetch.statusCode,
                responseTimeMs = verifyFetch.responseTimeMs,
                redirectCount = verifyFetch.redirectChain.size,
                redirectChain = verifyFetch.redirectChain,
                contentType = verifyFetch.contentType,
                category = category,
                errorDetail = if (isHtmlResponse) {
                    "Ricevuta pagina HTML invece di un manifest HLS #EXTM3U"
                } else {
                    "Manifest HLS non valido o privo di varianti/segmenti"
                }
            )
            recordDiagnostic(event)
            throw VixSrcException(
                diagnostic = event,
                userMessage = if (isHtmlResponse) {
                    "VixSrc ha restituito una pagina HTML invece del flusso video HLS. Il collegamento potrebbe richiedere verifica o essere scaduto."
                } else {
                    "Manifest video HLS ricevuto da VixSrc non valido o incompleto."
                }
            )
        }

        return verifyFetch.finalUrl
    }

    private suspend fun executeFetchWithDiagnostics(
        request: Request,
        phase: VixSrcResolutionPhase,
        maxAttempts: Int = 2
    ): HttpFetchResult {
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            val startedNs = System.nanoTime()
            try {
                resolverApiClient.newCall(request).execute().use { response ->
                    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
                    val redirectChain = collectSanitizedRedirectChain(response)
                    val statusCode = response.code
                    val contentType = response.header("Content-Type")
                    val finalUrl = response.request.url.toString()

                    // Retry solo su errori effettivamente transitori (429 / 5xx), non su 400/401/403/404 permanenti
                    val isTransientHttp = statusCode == 429 || statusCode in 500..599
                    if (isTransientHttp && attempt < maxAttempts) {
                        recordDiagnostic(
                            VixSrcDiagnosticEvent(
                                phase = phase,
                                method = request.method,
                                sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(finalUrl),
                                httpStatusCode = statusCode,
                                responseTimeMs = elapsedMs,
                                redirectCount = redirectChain.size,
                                redirectChain = redirectChain,
                                contentType = contentType,
                                category = VixSrcDiagnostics.classifyHttpStatus(statusCode),
                                errorDetail = "Tentativo $attempt/$maxAttempts: errore HTTP transitorio $statusCode"
                            )
                        )
                        val retryAfterSec = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 3L)
                        delay(retryAfterSec?.times(1000L) ?: 300L)
                        continue
                    }

                    val bodyText = readBoundedBody(response)
                    recordDiagnostic(
                        VixSrcDiagnosticEvent(
                            phase = phase,
                            method = request.method,
                            sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(finalUrl),
                            httpStatusCode = statusCode,
                            responseTimeMs = elapsedMs,
                            redirectCount = redirectChain.size,
                            redirectChain = redirectChain,
                            contentType = contentType,
                            category = if (statusCode in 200..299) null else VixSrcDiagnostics.classifyHttpStatus(statusCode),
                            errorDetail = if (statusCode in 200..299) null else "HTTP $statusCode ${response.message}"
                        )
                    )
                    return HttpFetchResult(
                        finalUrl = finalUrl,
                        statusCode = statusCode,
                        contentType = contentType,
                        body = bodyText,
                        responseTimeMs = elapsedMs,
                        redirectChain = redirectChain
                    )
                }
            } catch (io: IOException) {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
                val category = VixSrcDiagnostics.classifyThrowable(io)
                recordDiagnostic(
                    VixSrcDiagnosticEvent(
                        phase = phase,
                        method = request.method,
                        sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(request.url.toString()),
                        responseTimeMs = elapsedMs,
                        category = category,
                        errorDetail = "Tentativo $attempt/$maxAttempts: ${io.javaClass.simpleName}: ${io.message.orEmpty()}"
                    )
                )
                lastError = io
                if (attempt >= maxAttempts) {
                    throw VixSrcException(
                        diagnostic = VixSrcDiagnosticEvent(
                            phase = phase,
                            method = request.method,
                            sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(request.url.toString()),
                            responseTimeMs = elapsedMs,
                            category = category,
                            errorDetail = io.message
                        ),
                        userMessage = VixSrcDiagnostics.formatNetworkUserMessage(io, phase),
                        cause = io
                    )
                }
                delay(300L)
            }
        }
        val fallbackErr = lastError ?: IOException("Connessione VixSrc non riuscita")
        throw VixSrcException(
            diagnostic = VixSrcDiagnosticEvent(
                phase = phase,
                method = request.method,
                sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(request.url.toString()),
                category = VixSrcDiagnostics.classifyThrowable(fallbackErr),
                errorDetail = fallbackErr.message
            ),
            userMessage = VixSrcDiagnostics.formatNetworkUserMessage(fallbackErr, phase),
            cause = fallbackErr
        )
    }

    private fun readBoundedBody(response: Response): String {
        val body = response.body ?: return ""
        val source = body.source()
        source.request(MAX_TEXT_BODY_BYTES)
        val buffer = source.buffer
        val bytesToRead = minOf(buffer.size, MAX_TEXT_BODY_BYTES)
        return buffer.clone().readString(bytesToRead, Charsets.UTF_8)
    }

    private fun collectSanitizedRedirectChain(response: Response): List<String> {
        val hops = mutableListOf<String>()
        var prior = response.priorResponse
        while (prior != null) {
            hops.add(VixSrcDiagnostics.sanitizeUrl(prior.request.url.toString()))
            prior = prior.priorResponse
        }
        return hops.reversed()
    }

    private fun buildHttpException(
        fetch: HttpFetchResult,
        phase: VixSrcResolutionPhase,
        method: String
    ): VixSrcException {
        val category = VixSrcDiagnostics.classifyHttpStatus(fetch.statusCode)
        val event = VixSrcDiagnosticEvent(
            phase = phase,
            method = method,
            sanitizedUrl = VixSrcDiagnostics.sanitizeUrl(fetch.finalUrl),
            httpStatusCode = fetch.statusCode,
            responseTimeMs = fetch.responseTimeMs,
            redirectCount = fetch.redirectChain.size,
            redirectChain = fetch.redirectChain,
            contentType = fetch.contentType,
            category = category,
            errorDetail = "HTTP ${fetch.statusCode} durante ${phase.name}"
        )
        return VixSrcException(
            diagnostic = event,
            userMessage = VixSrcDiagnostics.formatHttpUserMessage(fetch.statusCode, phase)
        )
    }

    internal fun computeSafeExpiresAtMs(streamUrl: String, nowMs: Long = System.currentTimeMillis()): Long {
        val defaultFallbackMs = nowMs + 20L * 60L * 1000L
        val safetyMarginMs = 120_000L
        val maxCapMs = nowMs + 6L * 3600L * 1000L

        val rawExpires = EXPIRES_QUERY_REGEX
            .find(streamUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return defaultFallbackMs

        val expiresEpochMs = if (rawExpires < 10_000_000_000L) rawExpires * 1000L else rawExpires
        if (expiresEpochMs <= nowMs + safetyMarginMs + 60_000L) {
            return defaultFallbackMs
        }
        return (expiresEpochMs - safetyMarginMs).coerceAtMost(maxCapMs)
    }

    /**
     * Estrae l'URL HLS (`window.masterPlaylist` o `window.streams` o `.m3u8` diretto)
     * dal codice sorgente HTML/JS di VixSrc usando un parser a parentesi graffe bilanciate:
     * - Supporta oggetti `params: { ... }` annidati con chiavi quotate o non quotate e valori stringa/numerici.
     * - Decodifica escape HTML/JS (`&amp;`, `\u0026`, `\/`).
     * - Rispetta `window.canPlayFHD` per il flag `h=1` senza forzarlo quando `canPlayFHD = false`.
     * - Non aggiunge parametri estranei (es. `startAt`) e non duplica parametri già presenti nell'URL base.
     */
    fun parseMasterPlaylistFromHtml(
        html: String,
        pageUrl: String,
        lang: String = "it",
        startSeconds: Long = 0L
    ): String? {
        if (html.isBlank()) return null

        val masterStart = MASTER_PLAYLIST_START_REGEX.find(html)
        val masterBlock = masterStart?.let { match ->
            extractBalancedBraceContent(html, match.range.last)
        }

        val rawPlaylistUrl = (masterBlock?.let { block ->
            URL_PROPERTY_REGEX.find(block)?.groupValues?.getOrNull(2)
        } ?: PLAYLIST_URL_FALLBACK_REGEX.find(html)?.groupValues?.getOrNull(2))
            ?.let(::decodeExtractedUrl)

        if (!rawPlaylistUrl.isNullOrBlank()) {
            val resolvedBase = resolveRelativeUrl(rawPlaylistUrl, pageUrl)
            if (!VixSrcDiagnostics.isSafeHttpUrl(resolvedBase)) return null

            val paramsMap = linkedMapOf<String, String>()
            val paramsSearchScope = masterBlock ?: html
            val paramsStart = PARAMS_START_REGEX.find(paramsSearchScope)
            val paramsContent = paramsStart?.let { match ->
                extractBalancedBraceContent(paramsSearchScope, match.range.last)
            }

            if (!paramsContent.isNullOrBlank()) {
                JS_KV_REGEX.findAll(paramsContent).forEach { matchResult ->
                    val key = matchResult.groupValues[1].ifBlank { matchResult.groupValues[2] }.trim()
                    val value = matchResult.groupValues[3].ifBlank { matchResult.groupValues[4] }.trim()
                    if (key.isNotBlank() && value.isNotBlank()) {
                        paramsMap[key] = decodeExtractedUrl(value)
                    }
                }
            } else {
                val tokenMatch = TOKEN_FALLBACK_REGEX.find(paramsSearchScope)?.groupValues?.getOrNull(1)
                    ?: TOKEN_FALLBACK_REGEX.find(html)?.groupValues?.getOrNull(1)
                val expiresMatch = EXPIRES_FALLBACK_REGEX.find(paramsSearchScope)?.groupValues?.getOrNull(1)
                    ?: EXPIRES_FALLBACK_REGEX.find(html)?.groupValues?.getOrNull(1)
                if (!tokenMatch.isNullOrBlank()) paramsMap["token"] = decodeExtractedUrl(tokenMatch)
                if (!expiresMatch.isNullOrBlank()) paramsMap["expires"] = expiresMatch
            }

            val canPlayFhdFlag = CAN_PLAY_FHD_REGEX.find(html)?.groupValues?.getOrNull(1)?.lowercase(Locale.US)
            val shouldAppendFhd = when (canPlayFhdFlag) {
                "false" -> false
                "true" -> true
                else -> true
            }

            val parsedBaseUrl = resolvedBase.toHttpUrlOrNull() ?: return resolvedBase
            val existingParamNames = parsedBaseUrl.queryParameterNames
            val urlBuilder = parsedBaseUrl.newBuilder()

            paramsMap.forEach { (k, v) ->
                if (k !in existingParamNames) {
                    urlBuilder.addQueryParameter(k, v)
                }
            }

            val hasHParam = "h" in existingParamNames || paramsMap.containsKey("h")
            if (shouldAppendFhd && !hasHParam) {
                urlBuilder.addQueryParameter("h", "1")
            }

            val cleanLang = lang.trim()
            val hasLangParam = "lang" in existingParamNames || paramsMap.containsKey("lang")
            if (cleanLang.isNotBlank() && !hasLangParam) {
                urlBuilder.addQueryParameter("lang", cleanLang)
            }

            return urlBuilder.build().toString()
        }

        val streamsJson = STREAMS_BLOCK_REGEX.find(html)?.groupValues?.getOrNull(1)
        if (!streamsJson.isNullOrBlank()) {
            val streamUrlMatch = STREAM_URL_REGEX.find(streamsJson)?.groupValues?.getOrNull(1)
            if (!streamUrlMatch.isNullOrBlank()) {
                val resolved = resolveRelativeUrl(decodeExtractedUrl(streamUrlMatch), pageUrl)
                if (VixSrcDiagnostics.isSafeHttpUrl(resolved)) return resolved
            }
        }

        val directMatch = DIRECT_M3U8_REGEX.find(html)?.value
        if (!directMatch.isNullOrBlank()) {
            val cleaned = decodeExtractedUrl(directMatch)
            if (VixSrcDiagnostics.isSafeHttpUrl(cleaned)) return cleaned
        }

        return null
    }

    private fun parseSubtitlesFromHtml(html: String, pageUrl: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        SUBTITLE_REGEX.findAll(html).forEachIndexed { idx, match ->
            val url = resolveRelativeUrl(decodeExtractedUrl(match.value), pageUrl)
            if (!VixSrcDiagnostics.isSafeHttpUrl(url)) return@forEachIndexed
            val lang = if (url.contains("it", ignoreCase = true)) "it" else "en"
            val label = if (lang == "it") "Italiano #${idx + 1}" else "Sottotitoli #${idx + 1}"
            tracks.add(SubtitleTrack(label = label, language = lang, url = url))
        }
        return tracks.distinctBy { it.url }
    }

    private fun resolveRelativeUrl(rawUrl: String, referencePageUrl: String = baseUrl): String {
        val cleaned = decodeExtractedUrl(rawUrl)
        if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
            return cleaned
        }
        if (cleaned.startsWith("//")) {
            return "https:$cleaned"
        }
        val refHttpUrl = referencePageUrl.toHttpUrlOrNull() ?: baseUrl.toHttpUrlOrNull()
        val resolvedByOkHttp = refHttpUrl?.resolve(cleaned)?.toString()
        if (!resolvedByOkHttp.isNullOrBlank()) {
            return resolvedByOkHttp
        }
        return when {
            cleaned.startsWith("/") -> "$baseUrl$cleaned"
            else -> "$baseUrl/$cleaned"
        }
    }

    fun parseIntroSegmentFromEmbedHtml(html: String): Pair<Long, Long>? =
        Companion.parseIntroSegmentFromEmbedHtml(html)
}
