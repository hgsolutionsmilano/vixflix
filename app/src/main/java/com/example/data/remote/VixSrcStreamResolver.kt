package com.example.data.remote

import androidx.compose.runtime.Immutable
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@Immutable
data class ResolvedStream(
    val streamUrl: String,
    val embedUrl: String,
    val referer: String = "https://vixsrc.to/",
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
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
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
 * Interceptor OkHttp dedicato per VixSrc e Media3 ExoPlayer:
 * 1. Instrada in modo trasparente tutte le richieste verso `vixsrc.to` (API `/api/movie/...`, `/embed/...`,
 *    `/playlist/...` e chiave AES-128 `/storage/enc.key`) attraverso il percorso normalizzato `/api/list/..%2f..%2f...`
 *    così da evitare blocchi Cloudflare 403 senza alcun WebView.
 * 2. Imposta `Content-Type: video/mp2t` sui segmenti HLS cifrati (`.html`, `.m4s`) serviti dai CDN VixSrc
 *    affinché `DefaultHlsExtractorFactory` di ExoPlayer utilizzi immediatamente `TsExtractor`.
 */
class VixSrcOkHttpInterceptor : Interceptor {
    companion object {
        private val ENC_KEY_REGEX = Regex("""URI=(["'])/storage/enc\.key([^"']*)\1""")
        private val HLS_MEDIA_TYPE = "application/vnd.apple.mpegurl".toMediaType()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url
        val host = originalUrl.host.lowercase()

        val requestBuilder = originalRequest.newBuilder()
        if (originalRequest.header("User-Agent") == null) {
            requestBuilder.header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
        }
        if (originalRequest.header("Referer") == null) {
            requestBuilder.header("Referer", "https://vixsrc.to/")
        }
        if (originalRequest.header("Origin") == null) {
            requestBuilder.header("Origin", "https://vixsrc.to")
        }

        if (host == "vixsrc.to" || host.endsWith(".vixsrc.to")) {
            val encodedPath = originalUrl.encodedPath
            if (!encodedPath.startsWith("/api/list/")) {
                val cleanSubPath = encodedPath.removePrefix("/")
                val escapedSubPath = cleanSubPath.replace("/", "%2f")
                val rewrittenUrl = originalUrl.newBuilder()
                    .encodedPath("/api/list/..%2f..%2f$escapedSubPath")
                    .build()
                requestBuilder.url(rewrittenUrl)
            }
        }

        val response = chain.proceed(requestBuilder.build())
        val path = originalUrl.encodedPath

        if (originalRequest.method.equals("HEAD", ignoreCase = true)) {
            return response
        }

        // Fast-path per segmenti binari HLS dai CDN VixSrc: evita qualsiasi controllo/buffer stringa sul body.
        if (path.contains("/hls/") && (path.endsWith(".html") || path.endsWith(".m4s") || path.endsWith(".ts"))) {
            return response.newBuilder()
                .header("Content-Type", "video/mp2t")
                .build()
        }

        // Se è una playlist HLS (.m3u8) di VixSrc, assicura URI assoluta per la chiave AES-128 /storage/enc.key
        val contentType = response.header("Content-Type").orEmpty()
        val isM3u8Candidate = (host.contains("vixsrc.to") && path.contains("/playlist/")) ||
            path.endsWith(".m3u8") ||
            contentType.contains("mpegurl", ignoreCase = true)
        if (response.isSuccessful && isM3u8Candidate) {
            val rawBody = response.body?.string().orEmpty()
            val cleanedManifest = rawBody.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            if (cleanedManifest.startsWith("#EXTM3U")) {
                val isDefaultPort = (originalUrl.scheme == "https" && originalUrl.port == 443) ||
                    (originalUrl.scheme == "http" && originalUrl.port == 80)
                val keyBaseUrl = buildString {
                    append(originalUrl.scheme).append("://").append(originalUrl.host)
                    if (!isDefaultPort) {
                        append(":").append(originalUrl.port)
                    }
                }
                val fixedManifest = ENC_KEY_REGEX.replace(cleanedManifest) { match ->
                    val quote = match.groupValues[1]
                    val suffix = match.groupValues[2]
                    "URI=${quote}$keyBaseUrl/storage/enc.key$suffix$quote"
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
    baseOkHttpClient: OkHttpClient,
    rawBaseUrl: String = BuildConfig.VIXSRC_BASE_URL
) {
    companion object {
        private val TMDB_ID_REGEX = Regex(""""tmdb_id"\s*:\s*(\d+)""")
        private val SRC_MATCH_REGEX = Regex(""""src"\s*:\s*"([^"]+)"""")
        private val EXPIRES_QUERY_REGEX = Regex("""[?&]expires=(\d+)""")
        private val MASTER_BLOCK_REGEX = Regex(
            """window\.masterPlaylist\s*=\s*\{(.*?)\}\s*;?""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val PLAYLIST_URL_REGEX = Regex("""['"]?url['"]?\s*:\s*['"]([^'"]+playlist[^'"]+|[^'"]+\.m3u8[^'"]*)['"]""")
        private val PARAMS_BLOCK_REGEX = Regex(
            """params\s*:\s*\{(.*?)\}""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val KV_REGEX = Regex("""['"]([^'"]+)['"]\s*:\s*['"]([^'"]*)['"]""")
        private val TOKEN_FALLBACK_REGEX = Regex("""['"]token['"]\s*:\s*['"]([^'"]+)['"]""")
        private val EXPIRES_FALLBACK_REGEX = Regex("""['"]expires['"]\s*:\s*['"]([^'"]+)['"]""")
        private val STREAMS_BLOCK_REGEX = Regex(
            """window\.streams\s*=\s*(\[.*?\]);""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        private val STREAM_URL_REGEX = Regex("""["'](?:url|file)["']\s*:\s*["']([^"']+)["']""")
        private val DIRECT_M3U8_REGEX = Regex("""https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""")
        private val SUBTITLE_REGEX = Regex("""https?://[^\s"'<>]+?\.(?:vtt|srt)[^\s"'<>]*""")
    }

    private val baseUrl = rawBaseUrl.trimEnd('/')
    private val catalogMutex = Mutex()
    private val cachedMovieIds = mutableSetOf<Int>()
    private val cachedTvIds = mutableSetOf<Int>()
    private val prewarmInFlight = AtomicBoolean(false)
    private val lastPrewarmMs = AtomicLong(0L)

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
     */
    val exoOkHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .dispatcher(streamingDispatcher)
        .connectionPool(streamingConnectionPool)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(VixSrcOkHttpInterceptor())
        .build()

    private val resolverApiClient: OkHttpClient = exoOkHttpClient.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val prewarmApiClient: OkHttpClient = exoOkHttpClient.newBuilder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

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
     * Costruisce l'URL API/embed ufficiale di vixsrc.to passando sia i secondi (startAt)
     * sia i minuti (startMinutes) da cui far partire lo streaming.
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
        val startMinutes = (safeSeconds / 60L).toInt()
        val path = if (mediaType == "tv") {
            val s = season ?: 1
            val e = episode ?: 1
            "$baseUrl/tv/$tmdbId/$s/$e"
        } else {
            "$baseUrl/movie/$tmdbId"
        }
        return buildString {
            append(path)
            append("?lang=").append(lang)
            append("&autoplay=true")
            append("&primaryColor=E50914")
            append("&secondaryColor=B20710")
            if (safeSeconds > 0L) {
                append("&startAt=").append(safeSeconds)
                append("&startMinutes=").append(startMinutes)
            }
        }
    }

    /**
     * Verifica la presenza del contenuto nell'endpoint JSON live `https://vixsrc.to/api/list/{type}/?lang=it`.
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

        runCatching {
            val endpointType = if (mediaType == "tv") "tv" else "movie"
            val listUrl = "$baseUrl/api/list/$endpointType/?lang=$lang"
            val request = Request.Builder()
                .url(listUrl)
                .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                .header("Accept", "application/json")
                .build()

            exoOkHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val ids = TMDB_ID_REGEX.findAll(body).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
                    if (ids.isNotEmpty()) {
                        catalogMutex.withLock {
                            targetSet.addAll(ids)
                        }
                        return@withContext ids.contains(tmdbId)
                    }
                }
            }
            true
        }.getOrDefault(true)
    }

    /**
     * Risolve il flusso HLS reale (.m3u8 / masterPlaylist) da vixsrc.to in 2 passaggi:
     * 1. Chiama `/api/movie/{tmdbId}` o `/api/tv/{tmdbId}/{s}/{e}` per ottenere `{ "src": "/embed/..." }`
     * 2. Scarica la pagina `/embed/...` con `Referer` appropriato ed estrae `window.masterPlaylist` (`url`, `token`, `expires`).
     * Nessun video di fallback viene mai restituito.
     */
    suspend fun resolveStreamForExoPlayer(
        tmdbId: Int,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
        startSeconds: Long = 0L,
        lang: String = "it"
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        val safeSeconds = startSeconds.coerceAtLeast(0L)
        val startMinutes = (safeSeconds / 60L).toInt()
        val embedPageReferer = buildVixSrcApiUrl(
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            startSeconds = safeSeconds,
            lang = lang
        )

        val apiEndpointUrl = if (mediaType == "tv") {
            val s = season ?: 1
            val e = episode ?: 1
            "$baseUrl/api/tv/$tmdbId/$s/$e?lang=$lang&startAt=$safeSeconds"
        } else {
            "$baseUrl/api/movie/$tmdbId?lang=$lang&startAt=$safeSeconds"
        }

        runCatching {
            // Step 1: Recupera il JSON {"src": "/embed/..."} dall'API VixSrc con retry su errori transitori
            val apiRequest = Request.Builder()
                .url(apiEndpointUrl)
                .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                .header("Referer", embedPageReferer)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()

            val embedSrcPath = executeWithTransientRetry(apiRequest) { apiResponse ->
                if (!apiResponse.isSuccessful) {
                    throw IllegalStateException("Titolo non trovato su VixSrc API (HTTP ${apiResponse.code})")
                }
                val jsonBody = apiResponse.body?.string().orEmpty()
                val srcMatch = SRC_MATCH_REGEX.find(jsonBody)?.groupValues?.getOrNull(1)
                srcMatch?.replace("\\/", "/")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Nessun flusso disponibile su VixSrc per questo titolo.")
            }

            val fullEmbedUrl = resolveRelativeUrl(embedSrcPath)

            // Step 2: Scarica la pagina /embed/... ed estrai window.masterPlaylist con retry su errori transitori
            val embedRequest = Request.Builder()
                .url(fullEmbedUrl)
                .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                .header("Referer", embedPageReferer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            executeWithTransientRetry(embedRequest) { embedResponse ->
                if (!embedResponse.isSuccessful) {
                    throw IllegalStateException("Errore caricamento player VixSrc (HTTP ${embedResponse.code})")
                }
                val html = embedResponse.body?.string().orEmpty()
                val extractedHls = parseMasterPlaylistFromHtml(html, fullEmbedUrl, lang, safeSeconds)
                    ?: throw IllegalStateException("Impossibile estrarre la masterPlaylist HLS da VixSrc.")
                val subtitles = parseSubtitlesFromHtml(html)
                val expiresAtMs = computeSafeExpiresAtMs(extractedHls)

                ResolvedStream(
                    streamUrl = extractedHls,
                    embedUrl = embedPageReferer,
                    referer = embedPageReferer,
                    startSeconds = safeSeconds,
                    startMinutes = startMinutes,
                    subtitles = subtitles,
                    isDirectHls = true,
                    vixCatalogVerified = true,
                    expiresAtMs = expiresAtMs,
                    fromCache = false
                )
            }
        }
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

    private suspend fun <T> executeWithTransientRetry(
        request: Request,
        maxAttempts: Int = 2,
        parser: (Response) -> T
    ): T {
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                resolverApiClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val isTransientHttp = code == 403 || code == 429 || code in 500..599
                    if (!response.isSuccessful && isTransientHttp && attempt < maxAttempts) {
                        lastError = IllegalStateException("HTTP transitorio $code da VixSrc")
                    } else {
                        return parser(response)
                    }
                }
            } catch (io: java.io.IOException) {
                lastError = io
                if (attempt >= maxAttempts) throw io
            }
            kotlinx.coroutines.delay(250L)
        }
        throw lastError ?: IllegalStateException("Connessione VixSrc non riuscita.")
    }

    /**
     * Estrae l'URL HLS (masterPlaylist o window.streams) dal codice sorgente HTML/JS di vixsrc.to.
     */
    fun parseMasterPlaylistFromHtml(
        html: String,
        pageUrl: String,
        lang: String = "it",
        startSeconds: Long = 0L
    ): String? {
        val masterMatch = MASTER_BLOCK_REGEX.find(html)?.groupValues?.getOrNull(1) ?: html

        val rawPlaylistUrl = PLAYLIST_URL_REGEX.find(masterMatch)?.groupValues?.getOrNull(1)
            ?: PLAYLIST_URL_REGEX.find(html)?.groupValues?.getOrNull(1)

        if (!rawPlaylistUrl.isNullOrBlank()) {
            val resolvedBase = resolveRelativeUrl(rawPlaylistUrl)
            val paramsMap = linkedMapOf<String, String>()

            val paramsContent = PARAMS_BLOCK_REGEX.find(masterMatch)?.groupValues?.getOrNull(1)
                ?: PARAMS_BLOCK_REGEX.find(html)?.groupValues?.getOrNull(1)

            if (!paramsContent.isNullOrBlank()) {
                KV_REGEX.findAll(paramsContent).forEach { matchResult ->
                    val k = matchResult.groupValues[1]
                    val v = matchResult.groupValues[2]
                    if (k.isNotBlank() && v.isNotBlank()) {
                        paramsMap[k] = v
                    }
                }
            } else {
                val tokenMatch = TOKEN_FALLBACK_REGEX.find(html)?.groupValues?.getOrNull(1)
                val expiresMatch = EXPIRES_FALLBACK_REGEX.find(html)?.groupValues?.getOrNull(1)
                if (!tokenMatch.isNullOrBlank()) paramsMap["token"] = tokenMatch
                if (!expiresMatch.isNullOrBlank()) paramsMap["expires"] = expiresMatch
            }

            paramsMap.putIfAbsent("h", "1")
            if (lang.isNotBlank()) {
                paramsMap.putIfAbsent("lang", lang)
            }
            if (startSeconds > 0L) {
                paramsMap.putIfAbsent("startAt", startSeconds.toString())
            }

            val separator = if (resolvedBase.contains("?")) "&" else "?"
            val queryString = paramsMap.entries.joinToString("&") { (k, v) -> "$k=$v" }
            return if (queryString.isNotBlank()) "$resolvedBase$separator$queryString" else resolvedBase
        }

        val streamsJson = STREAMS_BLOCK_REGEX.find(html)?.groupValues?.getOrNull(1)
        if (!streamsJson.isNullOrBlank()) {
            val streamUrlMatch = STREAM_URL_REGEX
                .find(streamsJson)?.groupValues?.getOrNull(1)
            if (!streamUrlMatch.isNullOrBlank()) {
                return resolveRelativeUrl(streamUrlMatch)
            }
        }

        val directMatch = DIRECT_M3U8_REGEX.find(html)?.value
        if (!directMatch.isNullOrBlank()) {
            return directMatch.replace("\\/", "/")
        }

        return null
    }

    private fun parseSubtitlesFromHtml(html: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        SUBTITLE_REGEX.findAll(html).forEachIndexed { idx, match ->
            val url = match.value.replace("\\/", "/")
            val lang = if (url.contains("it", ignoreCase = true)) "it" else "en"
            val label = if (lang == "it") "Italiano #${idx + 1}" else "Sottotitoli #${idx + 1}"
            tracks.add(SubtitleTrack(label = label, language = lang, url = url))
        }
        return tracks.distinctBy { it.url }
    }

    private fun resolveRelativeUrl(rawUrl: String): String {
        val cleaned = rawUrl.replace("\\/", "/").trim()
        return when {
            cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("/") -> "$baseUrl$cleaned"
            else -> "$baseUrl/$cleaned"
        }
    }
}
