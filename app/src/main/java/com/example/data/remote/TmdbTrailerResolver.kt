package com.example.data.remote

import android.os.Build
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class YouTubeTrailerCandidate(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationText: String,
    val durationSeconds: Int,
    val languageLabel: String, // "ITA" oppure "ENG"
    val thumbnailUrl: String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
}

data class ResolvedTrailerStream(
    val video: TmdbVideoDto,
    val streamUrl: String,
    val audioStreamUrl: String? = null,
    val isHls: Boolean,
    val sourceLabel: String,
    val userAgent: String = ResolvedStream.DEFAULT_USER_AGENT,
    val referer: String = "",
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = 0L,
    val isYouTubeEmbedFallback: Boolean = false,
    val embedVideoId: String? = null
)

/**
 * Cerca e risolve i trailer ufficiali direttamente da YouTube (con priorità Italiano, poi Inglese/Originale),
 * mantenendo la compatibilità con gli ID YouTube/Vimeo da TMDB.
 *
 * Non effettua alcun fallback su sorgenti diverse (es. film completo su VixSrc):
 * se il trailer non è disponibile o non è riproducibile, restituisce un errore esplicito.
 */
class TmdbTrailerResolver(
    private val okHttpClient: OkHttpClient,
    @Suppress("UNUSED_PARAMETER") private val vixSrcResolver: VixSrcStreamResolver
) {
    private val cleanTrailerHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val federatedHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    private var cachedPipedInstances: List<String> = emptyList()

    @Volatile
    private var cachedInvidiousInstances: List<String> = emptyList()

    @Volatile
    private var instancesCachedAtMs: Long = 0L

    /**
     * Esegue una ricerca diretta su YouTube per il titolo selezionato:
     * 1) Prima cerca trailer in Italiano (`trailer ufficiale ita`)
     * 2) Integra trailer ufficiali in Inglese / lingua originale (`official trailer`)
     * Restituisce una lista deduplicata ordinata per lingua (ITA prima) e pertinenza.
     */
    suspend fun searchYouTubeTrailers(item: MediaItem): Result<List<YouTubeTrailerCandidate>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseTitle = item.title.trim()
                if (baseTitle.isBlank()) {
                    throw IllegalStateException("Titolo non valido per la ricerca trailer su YouTube.")
                }
                val yearToken = item.releaseYear.takeIf { it.length == 4 }?.let { " $it" }.orEmpty()
                val itaQuery = "$baseTitle$yearToken trailer ufficiale ita"
                val engQuery = "$baseTitle$yearToken official trailer"

                val itaResults = queryYouTubeSearch(
                    query = itaQuery,
                    hl = "it",
                    gl = "IT",
                    defaultLangLabel = "ITA",
                    movieTitle = baseTitle
                )
                val engResults = queryYouTubeSearch(
                    query = engQuery,
                    hl = "en",
                    gl = "US",
                    defaultLangLabel = "ENG",
                    movieTitle = baseTitle
                )

                val mergedById = LinkedHashMap<String, YouTubeTrailerCandidate>()
                // Prima tutti i risultati marcati ITA
                (itaResults + engResults)
                    .filter { it.languageLabel == "ITA" }
                    .forEach { candidate ->
                        mergedById.putIfAbsent(candidate.videoId, candidate)
                    }
                // Poi i risultati originali / ENG non ancora presenti
                (engResults + itaResults)
                    .filter { it.languageLabel != "ITA" }
                    .forEach { candidate ->
                        mergedById.putIfAbsent(candidate.videoId, candidate)
                    }

                val finalResults = mergedById.values.toList().take(12)
                if (finalResults.isEmpty()) {
                    throw IllegalStateException("Nessun trailer trovato su YouTube per \"$baseTitle\".")
                }
                finalResults
            }
        }

    suspend fun resolveYouTubeCandidateForExoPlayer(
        candidate: YouTubeTrailerCandidate
    ): Result<ResolvedTrailerStream> = withContext(Dispatchers.IO) {
        runCatching {
            val syntheticDto = TmdbVideoDto(
                id = "yt_${candidate.videoId}",
                key = candidate.videoId,
                name = candidate.title,
                site = "YouTube",
                type = "Trailer",
                official = true,
                languageCode = if (candidate.languageLabel == "ITA") "it" else "en"
            )
            resolveYouTubeTrailer(syntheticDto)
        }
    }

    suspend fun resolveTrailerForExoPlayer(
        @Suppress("UNUSED_PARAMETER") item: MediaItem,
        video: TmdbVideoDto,
        @Suppress("UNUSED_PARAMETER") lang: String = "it"
    ): Result<ResolvedTrailerStream> = withContext(Dispatchers.IO) {
        runCatching {
            val key = video.key.trim()
            if (key.isBlank()) {
                throw IllegalStateException("Chiave video non valida per questo trailer.")
            }

            when {
                video.site.equals("YouTube", ignoreCase = true) -> {
                    resolveYouTubeTrailer(video)
                }
                video.site.equals("Vimeo", ignoreCase = true) -> {
                    resolveVimeoTrailer(video)
                }
                else -> {
                    throw IllegalStateException("Sorgente trailer '${video.site}' non supportata (solo YouTube/Vimeo).")
                }
            }
        }
    }

    private fun resolveYouTubeTrailer(video: TmdbVideoDto): ResolvedTrailerStream {
        val embedConfig = fetchYouTubeEmbedConfig(video.key)
        val visitorData = embedConfig.visitorData
        var lastReason: String? = null

        val clients = listOf(
            YouTubeClientProfile(
                name = "ANDROID_VR",
                clientId = "28",
                clientVersion = "1.62.27",
                clientJson = buildClientJson(
                    clientName = "ANDROID_VR",
                    clientVersion = "1.62.27",
                    visitorData = visitorData,
                    extraFields = """"deviceMake":"Oculus","deviceModel":"Quest 3","osName":"Android","osVersion":"12L","androidSdkVersion":32,"""
                ),
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            ),
            YouTubeClientProfile(
                name = "IOS",
                clientId = "5",
                clientVersion = "20.10.4",
                clientJson = buildClientJson(
                    clientName = "IOS",
                    clientVersion = "20.10.4",
                    visitorData = visitorData,
                    extraFields = """"deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.3.2.22D82","""
                ),
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
            ),
            YouTubeClientProfile(
                name = "ANDROID",
                clientId = "3",
                clientVersion = "20.10.38",
                clientJson = buildClientJson(
                    clientName = "ANDROID",
                    clientVersion = "20.10.38",
                    visitorData = visitorData,
                    extraFields = """"osName":"Android","osVersion":"14","androidSdkVersion":34,"""
                ),
                userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
            ),
            YouTubeClientProfile(
                name = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientId = "85",
                clientVersion = "2.0",
                clientJson = buildClientJson(
                    clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                    clientVersion = "2.0",
                    visitorData = visitorData,
                    extraFields = ""
                ),
                userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko) Version/7.0 TV Safari/537.36",
                includeEmbedContext = true
            ),
            YouTubeClientProfile(
                name = "MWEB",
                clientId = "2",
                clientVersion = "2.20250310.01.00",
                clientJson = buildClientJson(
                    clientName = "MWEB",
                    clientVersion = "2.20250310.01.00",
                    visitorData = visitorData,
                    extraFields = ""
                ),
                userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
            ),
            YouTubeClientProfile(
                name = "WEB_EMBEDDED_PLAYER",
                clientId = "56",
                clientVersion = "1.20250310.01.00",
                clientJson = buildClientJson(
                    clientName = "WEB_EMBEDDED_PLAYER",
                    clientVersion = "1.20250310.01.00",
                    visitorData = visitorData,
                    extraFields = ""
                ),
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                includeEmbedContext = true
            )
        )

        val apiKeyQuery = embedConfig.apiKey?.takeIf { it.isNotBlank() }?.let { "&key=$it" }.orEmpty()

        for (profile in clients) {
            val thirdPartyBlock = if (profile.includeEmbedContext) {
                ""","thirdParty":{"embedUrl":"https://www.youtube.com/"}"""
            } else ""
            val playbackContextBlock = if (profile.includeEmbedContext && embedConfig.signatureTimestamp != null) {
                ""","playbackContext":{"contentPlaybackContext":{"signatureTimestamp":${embedConfig.signatureTimestamp}}}"""
            } else ""

            val payload = """
                {
                  "videoId": "${video.key}",
                  "context": {
                    "client": ${profile.clientJson}$thirdPartyBlock
                  }$playbackContextBlock,
                  "contentCheckOk": true,
                  "racyCheckOk": true
                }
            """.trimIndent()

            val requestBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false$apiKeyQuery")
                .header("User-Agent", profile.userAgent)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-YouTube-Client-Name", profile.clientId)
                .header("X-YouTube-Client-Version", profile.clientVersion)
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .post(payload.toRequestBody("application/json".toMediaType()))

            if (!visitorData.isNullOrBlank()) {
                requestBuilder.header("X-Goog-Visitor-Id", visitorData)
            }

            runCatching {
                cleanTrailerHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastReason = "YouTube HTTP ${response.code} (${profile.name})"
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use

                    val json = JSONObject(body)
                    extractFromPlayerResponseJson(
                        video = video,
                        json = json,
                        userAgent = profile.userAgent,
                        sourceSuffix = "YouTube ${profile.name}"
                    )?.let { return it }

                    val playability = json.optJSONObject("playabilityStatus")
                    val status = playability?.optString("status").orEmpty()
                    val reason = playability?.optString("reason").orEmpty()
                    if (status.isNotEmpty() && status != "OK") {
                        lastReason = reason.ifBlank { status }
                    }
                }
            }
        }

        // Tentativo pagina watch HTML di YouTube (ytInitialPlayerResponse)
        extractFromYouTubeWatchHtml(video)?.let { return it }

        // Stage 1.5: Estrazione diretta via istanze pubbliche Piped / Invidious per riproduzione nativa ExoPlayer (zero WebView)
        resolveViaPipedAndInvidious(video)?.let { return it }

        // Stage 2: Verifica disponibilità e fallback su YouTube IFrame Embed Player (solo su hardware con GPU WebView stabile)
        val oEmbedCode = checkYouTubeOEmbedStatus(video.key)
        when (oEmbedCode) {
            401, 403 -> {
                throw IllegalStateException(
                    "Riproduzione incorporata disabilitata dall'autore per questo video YouTube. Seleziona un altro trailer dalla lista."
                )
            }
            400, 404 -> {
                throw IllegalStateException(
                    "Trailer YouTube non più disponibile o rimosso."
                )
            }
        }

        val canUseWebViewEmbed = !isSoftwareOnlyEmulator() && !isWebViewRendererUnstable
        if (canUseWebViewEmbed && (oEmbedCode == 200 || embedConfig.isEmbedPageReachable)) {
            return ResolvedTrailerStream(
                video = video,
                streamUrl = "https://www.youtube.com/embed/${video.key}",
                isHls = false,
                sourceLabel = "${video.typeBadge} • YouTube Player (${video.name})",
                isYouTubeEmbedFallback = true,
                embedVideoId = video.key
            )
        }

        val detail = when {
            isSoftwareOnlyEmulator() ->
                "YouTube richiede verifica anti-bot da questo indirizzo e l'emulatore non supporta il player Web senza accelerazione GPU"
            isWebViewRendererUnstable ->
                "Player Web YouTube non stabile su questo dispositivo e nessuno stream diretto disponibile"
            !lastReason.isNullOrBlank() -> lastReason
            oEmbedCode != null -> "HTTP $oEmbedCode"
            else -> null
        }?.let { " ($it)" }.orEmpty()
        throw IllegalStateException("Trailer non disponibile o bloccato su YouTube$detail. Seleziona un altro trailer dalla lista o riprova.")
    }

    private fun extractFromYouTubeWatchHtml(video: TmdbVideoDto): ResolvedTrailerStream? {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        return runCatching {
            val req = Request.Builder()
                .url("https://www.youtube.com/watch?v=${video.key}&hl=it")
                .header("User-Agent", ua)
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .build()

            cleanTrailerHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val html = resp.body?.string().orEmpty()
                val marker = "ytInitialPlayerResponse = "
                val startIdx = html.indexOf(marker)
                if (startIdx == -1) return@use null
                val jsonStart = html.indexOf('{', startIdx + marker.length)
                if (jsonStart == -1) return@use null
                val jsonText = extractBalancedJsonObject(html, jsonStart) ?: return@use null
                val json = JSONObject(jsonText)
                extractFromPlayerResponseJson(
                    video = video,
                    json = json,
                    userAgent = ua,
                    sourceSuffix = "YouTube Web"
                )
            }
        }.getOrNull()
    }

    private fun extractFromPlayerResponseJson(
        video: TmdbVideoDto,
        json: JSONObject,
        userAgent: String,
        sourceSuffix: String
    ): ResolvedTrailerStream? {
        val playability = json.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        if (status.isNotEmpty() && status != "OK") {
            return null
        }

        val streamingData = json.optJSONObject("streamingData") ?: return null

        // 1. HLS manifest (contiene già video + audio)
        val hlsManifestUrl = streamingData.optString("hlsManifestUrl").trim()
        if (hlsManifestUrl.startsWith("http")) {
            return ResolvedTrailerStream(
                video = video,
                streamUrl = hlsManifestUrl,
                isHls = true,
                sourceLabel = "${video.typeBadge} • $sourceSuffix HLS (${video.name})",
                userAgent = userAgent,
                referer = ""
            )
        }

        // 2. Formati MP4 muxed (video + audio insieme)
        val formatsArray = streamingData.optJSONArray("formats")
        if (formatsArray != null && formatsArray.length() > 0) {
            val muxedCandidates = mutableListOf<Pair<Int, String>>()
            for (i in 0 until formatsArray.length()) {
                val fmt = formatsArray.optJSONObject(i) ?: continue
                val url = fmt.optString("url").trim()
                val mimeType = fmt.optString("mimeType").lowercase()
                val height = fmt.optInt("height", 360)
                if (url.startsWith("http") && (mimeType.contains("video/mp4") || mimeType.contains("video/webm"))) {
                    muxedCandidates.add(height to url)
                }
            }
            val bestMuxed = muxedCandidates.maxByOrNull { it.first }
            if (bestMuxed != null) {
                return ResolvedTrailerStream(
                    video = video,
                    streamUrl = bestMuxed.second,
                    isHls = false,
                    sourceLabel = "${video.typeBadge} • $sourceSuffix ${bestMuxed.first}p (${video.name})",
                    userAgent = userAgent,
                    referer = ""
                )
            }
        }

        // 3. Adaptive formats separati (video mp4 <= 1080p + audio m4a) con MergingMediaSource
        val adaptiveArray = streamingData.optJSONArray("adaptiveFormats")
        if (adaptiveArray != null && adaptiveArray.length() > 0) {
            val videoCandidates = mutableListOf<Pair<Int, String>>()
            val audioCandidates = mutableListOf<Pair<Int, String>>()
            for (i in 0 until adaptiveArray.length()) {
                val fmt = adaptiveArray.optJSONObject(i) ?: continue
                val url = fmt.optString("url").trim()
                if (!url.startsWith("http")) continue
                val mimeType = fmt.optString("mimeType").lowercase()
                if (mimeType.startsWith("video/mp4")) {
                    val height = fmt.optInt("height", 0)
                    if (height in 240..1080) {
                        videoCandidates.add(height to url)
                    }
                } else if (mimeType.startsWith("audio/mp4")) {
                    val bitrate = fmt.optInt("bitrate", 0)
                    audioCandidates.add(bitrate to url)
                }
            }
            val bestVideo = videoCandidates.maxByOrNull { it.first }
            val bestAudio = audioCandidates.maxByOrNull { it.first }
            if (bestVideo != null && bestAudio != null) {
                return ResolvedTrailerStream(
                    video = video,
                    streamUrl = bestVideo.second,
                    audioStreamUrl = bestAudio.second,
                    isHls = false,
                    sourceLabel = "${video.typeBadge} • $sourceSuffix ${bestVideo.first}p HD (${video.name})",
                    userAgent = userAgent,
                    referer = ""
                )
            }
        }

        return null
    }

    private fun resolveVimeoTrailer(video: TmdbVideoDto): ResolvedTrailerStream {
        val ua = ResolvedStream.DEFAULT_USER_AGENT
        val req = Request.Builder()
            .url("https://player.vimeo.com/video/${video.key}/config")
            .header("User-Agent", ua)
            .header("Referer", "https://vimeo.com/")
            .build()

        cleanTrailerHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Trailer Vimeo non raggiungibile (HTTP ${resp.code}).")
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val files = json.optJSONObject("request")?.optJSONObject("files")
                ?: throw IllegalStateException("Nessun flusso video trovato nella risposta Vimeo.")

            val hlsObj = files.optJSONObject("hls")
            val defaultCdn = hlsObj?.optString("default_cdn").orEmpty()
            val hlsUrl = hlsObj?.optJSONObject("cdns")?.optJSONObject(defaultCdn)?.optString("url")?.trim()
            if (!hlsUrl.isNullOrBlank() && verifyHlsManifest(hlsUrl, ua, "https://vimeo.com/")) {
                return ResolvedTrailerStream(
                    video = video,
                    streamUrl = hlsUrl,
                    isHls = true,
                    sourceLabel = "${video.typeBadge} • Vimeo HLS (${video.name})",
                    userAgent = ua,
                    referer = "https://vimeo.com/"
                )
            }

            val progressive = files.optJSONArray("progressive")
            if (progressive != null && progressive.length() > 0) {
                val best = (0 until progressive.length())
                    .mapNotNull { progressive.optJSONObject(it) }
                    .sortedByDescending { it.optInt("height", 0) }
                    .firstOrNull { it.optString("url").isNotBlank() }
                val mp4Url = best?.optString("url")?.trim()
                if (!mp4Url.isNullOrBlank() && verifyProgressiveStream(mp4Url, ua, "https://vimeo.com/")) {
                    return ResolvedTrailerStream(
                        video = video,
                        streamUrl = mp4Url,
                        isHls = false,
                        sourceLabel = "${video.typeBadge} • Vimeo MP4 (${video.name})",
                        userAgent = ua,
                        referer = "https://vimeo.com/"
                    )
                }
            }

            throw IllegalStateException("Nessuno stream riproducibile trovato su Vimeo per questo trailer.")
        }
    }

    private fun fetchYouTubeEmbedConfig(videoId: String): YouTubeEmbedConfig {
        return runCatching {
            val req = Request.Builder()
                .url("https://www.youtube.com/embed/$videoId?hl=it")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                )
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .build()
            cleanTrailerHttpClient.newCall(req).execute().use { resp ->
                val html = if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                YouTubeEmbedConfig(
                    apiKey = Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.getOrNull(1),
                    visitorData = Regex(""""visitorData"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.getOrNull(1),
                    signatureTimestamp = Regex(""""STS"\s*:\s*(\d+)""").find(html)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                    embedHttpCode = resp.code,
                    isEmbedPageReachable = resp.isSuccessful && !html.contains("\"UNPLAYABLE\"")
                )
            }
        }.getOrElse {
            YouTubeEmbedConfig(
                apiKey = null,
                visitorData = null,
                signatureTimestamp = null,
                embedHttpCode = 0,
                isEmbedPageReachable = false
            )
        }
    }

    private fun checkYouTubeOEmbedStatus(videoId: String): Int? {
        return runCatching {
            val encodedWatch = URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encodedWatch&format=json")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                )
                .build()
            cleanTrailerHttpClient.newCall(req).execute().use { resp ->
                resp.code
            }
        }.getOrNull()
    }

    private fun verifyHlsManifest(url: String, userAgent: String, referer: String = ""): Boolean {
        return runCatching {
            val builder = Request.Builder().url(url).header("User-Agent", userAgent)
            if (referer.isNotBlank()) builder.header("Referer", referer)
            cleanTrailerHttpClient.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val text = resp.body?.string().orEmpty()
                text.contains("#EXTM3U")
            }
        }.getOrDefault(false)
    }

    private fun verifyProgressiveStream(url: String, userAgent: String, referer: String = ""): Boolean {
        return runCatching {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-1023")
            if (referer.isNotBlank()) builder.header("Referer", referer)
            cleanTrailerHttpClient.newCall(builder.build()).execute().use { resp ->
                resp.isSuccessful || resp.code == 206
            }
        }.getOrDefault(false)
    }

    private fun buildClientJson(
        clientName: String,
        clientVersion: String,
        visitorData: String?,
        extraFields: String
    ): String {
        val visitorField = if (!visitorData.isNullOrBlank()) {
            """"visitorData":"$visitorData","""
        } else ""
        return """{"clientName":"$clientName","clientVersion":"$clientVersion",$extraFields$visitorField"hl":"it","gl":"IT"}"""
    }

    private fun extractBalancedJsonObject(source: String, startIndex: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in startIndex until source.length) {
            val c = source[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        return source.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun queryYouTubeSearch(
        query: String,
        hl: String,
        gl: String,
        defaultLangLabel: String,
        movieTitle: String
    ): List<YouTubeTrailerCandidate> {
        val webUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

        // 1. Tentativo rapido tramite endpoint ufficiale InnerTube /youtubei/v1/search
        val apiPayload = JSONObject().apply {
            put("query", query)
            put("params", "EgIQAQ%3D%3D") // Solo Video
            put(
                "context",
                JSONObject().apply {
                    put(
                        "client",
                        JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", "2.20250310.01.00")
                            put("hl", hl)
                            put("gl", gl)
                        }
                    )
                }
            )
        }.toString()

        runCatching {
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", webUa)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Accept-Language", if (hl == "it") "it-IT,it;q=0.9" else "en-US,en;q=0.9")
                .post(apiPayload.toRequestBody("application/json".toMediaType()))
                .build()

            cleanTrailerHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val parsed = parseYouTubeSearchJson(
                            root = JSONObject(body),
                            defaultLangLabel = defaultLangLabel,
                            movieTitle = movieTitle
                        )
                        if (parsed.isNotEmpty()) return parsed
                    }
                }
            }
        }

        // 2. Fallback sulla pagina HTML risultati YouTube (ytInitialData)
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encoded&sp=EgIQAQ%253D%253D&hl=$hl&gl=$gl"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", webUa)
                .header("Accept-Language", if (hl == "it") "it-IT,it;q=0.9" else "en-US,en;q=0.9")
                .build()

            cleanTrailerHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val html = resp.body?.string().orEmpty()
                val markers = listOf("var ytInitialData = ", "ytInitialData = ")
                var jsonText: String? = null
                for (marker in markers) {
                    val idx = html.indexOf(marker)
                    if (idx != -1) {
                        val startBrace = html.indexOf('{', idx + marker.length)
                        if (startBrace != -1) {
                            jsonText = extractBalancedJsonObject(html, startBrace)
                            if (jsonText != null) break
                        }
                    }
                }
                if (jsonText == null) return@use emptyList()
                parseYouTubeSearchJson(
                    root = JSONObject(jsonText),
                    defaultLangLabel = defaultLangLabel,
                    movieTitle = movieTitle
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun parseYouTubeSearchJson(
        root: JSONObject,
        defaultLangLabel: String,
        movieTitle: String
    ): List<YouTubeTrailerCandidate> {
        val renderers = mutableListOf<JSONObject>()
        collectVideoRenderers(root, renderers)

        val scored = mutableListOf<Pair<Int, YouTubeTrailerCandidate>>()
        val titleWords = movieTitle.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }

        for (renderer in renderers) {
            val videoId = renderer.optString("videoId").trim()
            if (videoId.length < 6) continue

            val title = extractRunsText(renderer.optJSONObject("title")).trim()
            if (title.isBlank()) continue

            val channel = extractRunsText(renderer.optJSONObject("ownerText"))
                .ifBlank { extractRunsText(renderer.optJSONObject("longBylineText")) }
                .ifBlank { "YouTube" }

            val durationText = renderer.optJSONObject("lengthText")
                ?.let { lt ->
                    lt.optString("simpleText").ifBlank { extractRunsText(lt) }
                }
                .orEmpty()
                .trim()
            val durationSeconds = parseDurationSeconds(durationText)
            // Escludi clip < 15s (Shorts) o video lunghi (> 8 min, es. film completi / recensioni lunghe)
            if (durationSeconds in 1..14 || durationSeconds > 480) continue

            val lowerTitle = title.lowercase()
            val lowerChannel = channel.lowercase()
            val isItaMarked = Regex("""\b(ita|italiano|sub ita|ufficiale|doppiato)\b""").containsMatchIn(lowerTitle)
            val isEngMarked = Regex("""\b(official trailer|english|en|original)\b""").containsMatchIn(lowerTitle)
            val langLabel = when {
                isItaMarked -> "ITA"
                defaultLangLabel == "ITA" && !isEngMarked -> "ITA"
                else -> "ENG"
            }

            var score = 0
            if (lowerTitle.contains("trailer")) score += 40
            if (lowerTitle.contains("teaser")) score += 20
            if (lowerTitle.contains("ufficiale") || lowerTitle.contains("official")) score += 25
            if (titleWords.isNotEmpty() && titleWords.any { lowerTitle.contains(it) }) score += 20
            if (lowerChannel.contains("trailer") || lowerChannel.contains("pictures") ||
                lowerChannel.contains("netflix") || lowerChannel.contains("warner") ||
                lowerChannel.contains("universal") || lowerChannel.contains("disney") ||
                lowerChannel.contains("sony") || lowerChannel.contains("paramount") ||
                lowerChannel.contains("eagle") || lowerChannel.contains("01 distribution") ||
                lowerChannel.contains("prime video")
            ) {
                score += 15
            }
            if (lowerTitle.contains("reaction") || lowerTitle.contains("review") ||
                lowerTitle.contains("spiegazione") || lowerTitle.contains("ending explained")
            ) {
                score -= 50
            }

            if (score < 15) continue

            scored.add(
                score to YouTubeTrailerCandidate(
                    videoId = videoId,
                    title = title,
                    channelName = channel,
                    durationText = durationText.ifBlank { "HD" },
                    durationSeconds = durationSeconds,
                    languageLabel = langLabel
                )
            )
        }

        return scored
            .sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { it.videoId }
            .take(8)
    }

    private fun collectVideoRenderers(node: Any?, out: MutableList<JSONObject>) {
        if (out.size >= 30) return
        when (node) {
            is JSONObject -> {
                val vr = node.optJSONObject("videoRenderer")
                if (vr != null) {
                    out.add(vr)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key != "videoRenderer") {
                        collectVideoRenderers(node.opt(key), out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectVideoRenderers(node.opt(i), out)
                }
            }
        }
    }

    private fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText").trim()
        if (simple.isNotEmpty()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }.trim()
    }

    private fun parseDurationSeconds(text: String): Int {
        if (text.isBlank()) return 0
        val parts = text.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private fun resolveViaPipedAndInvidious(video: TmdbVideoDto): ResolvedTrailerStream? {
        refreshFederatedInstancesIfNeeded()
        val ua = ResolvedStream.DEFAULT_USER_AGENT

        for (baseUrl in cachedPipedInstances.take(6)) {
            val resolved = runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/streams/${video.key}")
                    .header("User-Agent", ua)
                    .header("Accept", "application/json")
                    .build()
                federatedHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank() || !body.trimStart().startsWith("{")) return@use null
                    val json = JSONObject(body)
                    if (json.has("error")) return@use null

                    val hlsUrl = json.optString("hls").trim()
                    if (hlsUrl.startsWith("http") && verifyHlsManifest(hlsUrl, ua)) {
                        return@use ResolvedTrailerStream(
                            video = video,
                            streamUrl = hlsUrl,
                            isHls = true,
                            sourceLabel = "${video.typeBadge} • YouTube HLS (${video.name})",
                            userAgent = ua
                        )
                    }

                    val videoStreams = json.optJSONArray("videoStreams")
                    val audioStreams = json.optJSONArray("audioStreams")

                    if (videoStreams != null && videoStreams.length() > 0) {
                        val muxed = mutableListOf<Pair<Int, String>>()
                        val adaptiveVideo = mutableListOf<Pair<Int, String>>()
                        for (i in 0 until videoStreams.length()) {
                            val obj = videoStreams.optJSONObject(i) ?: continue
                            val url = obj.optString("url").trim()
                            if (!url.startsWith("http")) continue
                            val mime = obj.optString("mimeType").lowercase()
                            val format = obj.optString("format").lowercase()
                            if (!mime.contains("mp4") && !format.contains("mpeg_4")) continue
                            val height = obj.optInt("height", parseQualityHeight(obj.optString("quality")))
                            if (!obj.optBoolean("videoOnly", true)) {
                                muxed.add(height to url)
                            } else if (height in 240..1080) {
                                adaptiveVideo.add(height to url)
                            }
                        }

                        val bestMuxed = muxed.maxByOrNull { it.first }
                        if (bestMuxed != null && verifyFederatedStream(bestMuxed.second, ua)) {
                            return@use ResolvedTrailerStream(
                                video = video,
                                streamUrl = bestMuxed.second,
                                isHls = false,
                                sourceLabel = "${video.typeBadge} • YouTube ${bestMuxed.first}p (${video.name})",
                                userAgent = ua
                            )
                        }

                        if (audioStreams != null && audioStreams.length() > 0) {
                            val bestVideo = adaptiveVideo.maxByOrNull { it.first }
                            val bestAudio = (0 until audioStreams.length())
                                .mapNotNull { audioStreams.optJSONObject(it) }
                                .filter { obj ->
                                    val url = obj.optString("url").trim()
                                    val mime = obj.optString("mimeType").lowercase()
                                    val fmt = obj.optString("format").lowercase()
                                    url.startsWith("http") && (mime.contains("mp4") || fmt.contains("m4a"))
                                }
                                .maxByOrNull { it.optInt("bitrate", 0) }
                                ?.optString("url")
                                ?.trim()

                            if (bestVideo != null && !bestAudio.isNullOrBlank() &&
                                verifyFederatedStream(bestVideo.second, ua) &&
                                verifyFederatedStream(bestAudio, ua)
                            ) {
                                return@use ResolvedTrailerStream(
                                    video = video,
                                    streamUrl = bestVideo.second,
                                    audioStreamUrl = bestAudio,
                                    isHls = false,
                                    sourceLabel = "${video.typeBadge} • YouTube ${bestVideo.first}p HD (${video.name})",
                                    userAgent = ua
                                )
                            }
                        }
                    }
                    null
                }
            }.getOrNull()

            if (resolved != null) return resolved
        }

        for (baseUrl in cachedInvidiousInstances.take(6)) {
            val resolved = runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/videos/${video.key}?local=true")
                    .header("User-Agent", ua)
                    .header("Accept", "application/json")
                    .build()
                federatedHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank() || !body.trimStart().startsWith("{")) return@use null
                    val json = JSONObject(body)
                    if (json.has("error")) return@use null

                    val hlsRaw = json.optString("hlsUrl").trim()
                    val hlsUrl = resolveInstanceUrl(baseUrl, hlsRaw)
                    if (hlsUrl.startsWith("http") && verifyHlsManifest(hlsUrl, ua, "$baseUrl/")) {
                        return@use ResolvedTrailerStream(
                            video = video,
                            streamUrl = hlsUrl,
                            isHls = true,
                            sourceLabel = "${video.typeBadge} • YouTube HLS (${video.name})",
                            userAgent = ua,
                            referer = "$baseUrl/"
                        )
                    }

                    val formatStreams = json.optJSONArray("formatStreams")
                    if (formatStreams != null && formatStreams.length() > 0) {
                        val candidates = mutableListOf<Pair<Int, String>>()
                        for (i in 0 until formatStreams.length()) {
                            val fmt = formatStreams.optJSONObject(i) ?: continue
                            val type = fmt.optString("type").lowercase()
                            if (!type.contains("video/mp4")) continue
                            val url = resolveInstanceUrl(baseUrl, fmt.optString("url").trim())
                            if (!url.startsWith("http")) continue
                            val height = parseQualityHeight(fmt.optString("resolution").ifBlank { fmt.optString("qualityLabel") })
                            candidates.add(height to url)
                        }
                        val best = candidates.maxByOrNull { it.first }
                        if (best != null && verifyFederatedStream(best.second, ua, "$baseUrl/")) {
                            return@use ResolvedTrailerStream(
                                video = video,
                                streamUrl = best.second,
                                isHls = false,
                                sourceLabel = "${video.typeBadge} • YouTube ${best.first}p (${video.name})",
                                userAgent = ua,
                                referer = "$baseUrl/"
                            )
                        }
                    }

                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                        val videos = mutableListOf<Pair<Int, String>>()
                        val audios = mutableListOf<Pair<Int, String>>()
                        for (i in 0 until adaptiveFormats.length()) {
                            val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                            val type = fmt.optString("type").lowercase()
                            val url = resolveInstanceUrl(baseUrl, fmt.optString("url").trim())
                            if (!url.startsWith("http")) continue
                            if (type.startsWith("video/mp4")) {
                                val height = parseQualityHeight(fmt.optString("resolution").ifBlank { fmt.optString("qualityLabel") })
                                if (height in 240..1080) videos.add(height to url)
                            } else if (type.startsWith("audio/mp4")) {
                                val bitrate = fmt.optString("bitrate").toIntOrNull() ?: 0
                                audios.add(bitrate to url)
                            }
                        }
                        val bestVideo = videos.maxByOrNull { it.first }
                        val bestAudio = audios.maxByOrNull { it.first }
                        if (bestVideo != null && bestAudio != null &&
                            verifyFederatedStream(bestVideo.second, ua, "$baseUrl/") &&
                            verifyFederatedStream(bestAudio.second, ua, "$baseUrl/")
                        ) {
                            return@use ResolvedTrailerStream(
                                video = video,
                                streamUrl = bestVideo.second,
                                audioStreamUrl = bestAudio.second,
                                isHls = false,
                                sourceLabel = "${video.typeBadge} • YouTube ${bestVideo.first}p HD (${video.name})",
                                userAgent = ua,
                                referer = "$baseUrl/"
                            )
                        }
                    }
                    null
                }
            }.getOrNull()

            if (resolved != null) return resolved
        }

        return null
    }

    private fun refreshFederatedInstancesIfNeeded() {
        val now = System.currentTimeMillis()
        if (cachedPipedInstances.isNotEmpty() &&
            cachedInvidiousInstances.isNotEmpty() &&
            now - instancesCachedAtMs < 10 * 60_000L
        ) {
            return
        }

        val pipedSeed = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://api.piped.yt",
            "https://api.piped.private.coffee",
            "https://pipedapi.drgns.space"
        )
        val dynamicPiped = runCatching {
            val req = Request.Builder()
                .url("https://piped-instances.kavin.rocks/")
                .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                .build()
            federatedHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val arr = JSONArray(resp.body?.string().orEmpty())
                (0 until arr.length())
                    .mapNotNull { arr.optJSONObject(it) }
                    .sortedByDescending { it.optDouble("uptime_30d", 0.0) }
                    .mapNotNull { obj ->
                        obj.optString("api_url").trim().trimEnd('/').takeIf { it.startsWith("https://") }
                    }
            }
        }.getOrDefault(emptyList())

        val invidiousSeed = listOf(
            "https://invidious.nerdvpn.de",
            "https://invidious.tiekoetter.com",
            "https://inv.nadeko.net",
            "https://invidious.jing.rocks",
            "https://yewtu.be"
        )
        val dynamicInvidious = runCatching {
            val req = Request.Builder()
                .url("https://api.invidious.io/instances.json?sort_by=health")
                .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                .build()
            federatedHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val arr = JSONArray(resp.body?.string().orEmpty())
                (0 until arr.length()).mapNotNull { idx ->
                    val entry = arr.optJSONArray(idx) ?: return@mapNotNull null
                    val meta = entry.optJSONObject(1) ?: return@mapNotNull null
                    if (meta.optString("type") != "https" || !meta.optBoolean("api", false)) return@mapNotNull null
                    val statusClass = meta.optJSONObject("monitor")?.optString("statusClass").orEmpty()
                    if (statusClass == "error") return@mapNotNull null
                    meta.optString("uri").trim().trimEnd('/').takeIf { it.startsWith("https://") }
                }
            }
        }.getOrDefault(emptyList())

        cachedPipedInstances = (dynamicPiped + pipedSeed).distinct()
        cachedInvidiousInstances = (dynamicInvidious + invidiousSeed).distinct()
        instancesCachedAtMs = now
    }

    private fun verifyFederatedStream(url: String, userAgent: String, referer: String = ""): Boolean {
        return runCatching {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-255")
            if (referer.isNotBlank()) builder.header("Referer", referer)
            federatedHttpClient.newCall(builder.build()).execute().use { resp ->
                resp.isSuccessful || resp.code == 206
            }
        }.getOrDefault(false)
    }

    private fun resolveInstanceUrl(baseUrl: String, raw: String): String {
        return when {
            raw.startsWith("http") -> raw
            raw.startsWith("/") -> "${baseUrl.trimEnd('/')}$raw"
            else -> ""
        }
    }

    private fun parseQualityHeight(label: String): Int {
        return Regex("""(\d{3,4})""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 360
    }

    private data class YouTubeEmbedConfig(
        val apiKey: String?,
        val visitorData: String?,
        val signatureTimestamp: Int?,
        val embedHttpCode: Int,
        val isEmbedPageReachable: Boolean
    )

    private data class YouTubeClientProfile(
        val name: String,
        val clientId: String,
        val clientVersion: String,
        val clientJson: String,
        val userAgent: String,
        val includeEmbedContext: Boolean = false
    )

    companion object {
        @Volatile
        var isWebViewRendererUnstable: Boolean = false

        fun isSoftwareOnlyEmulator(): Boolean {
            val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
            val model = Build.MODEL.orEmpty().lowercase()
            val product = Build.PRODUCT.orEmpty().lowercase()
            val hardware = Build.HARDWARE.orEmpty().lowercase()
            val brand = Build.BRAND.orEmpty().lowercase()
            val device = Build.DEVICE.orEmpty().lowercase()

            val looksLikeEmulator = fingerprint.startsWith("generic") ||
                fingerprint.contains("emulator") ||
                fingerprint.contains("vsoc") ||
                fingerprint.contains("sdk_gphone") ||
                model.contains("emulator") ||
                model.contains("android sdk built for") ||
                model.contains("cuttlefish") ||
                product.contains("sdk") ||
                product.contains("emulator") ||
                product.contains("vsoc") ||
                hardware.contains("ranchu") ||
                hardware.contains("goldfish") ||
                hardware.contains("cutf") ||
                (brand.startsWith("generic") && device.startsWith("generic"))

            if (!looksLikeEmulator) return false

            val hasHardwareRenderNode = runCatching {
                File("/dev/dri").listFiles()?.any { it.name.startsWith("renderD") } == true
            }.getOrDefault(false)

            return !hasHardwareRenderNode
        }
    }
}
