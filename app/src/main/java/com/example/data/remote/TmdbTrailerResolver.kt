package com.example.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class ResolvedTrailerStream(
    val video: TmdbVideoDto,
    val streamUrl: String,
    val audioStreamUrl: String? = null,
    val isHls: Boolean,
    val sourceLabel: String,
    val userAgent: String = ResolvedStream.DEFAULT_USER_AGENT,
    val referer: String = "",
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = 0L
)

/**
 * Risolve esclusivamente i trailer ufficiali recuperati dagli endpoint video di TMDB
 * (`/movie/{id}/videos` e `/tv/{id}/videos`) tramite YouTube o Vimeo.
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

    suspend fun resolveTrailerForExoPlayer(
        @Suppress("UNUSED_PARAMETER") item: MediaItem,
        video: TmdbVideoDto,
        @Suppress("UNUSED_PARAMETER") lang: String = "it"
    ): Result<ResolvedTrailerStream> = withContext(Dispatchers.IO) {
        runCatching {
            val key = video.key.trim()
            if (key.isBlank()) {
                throw IllegalStateException("Chiave video TMDB non valida per questo trailer.")
            }

            when {
                video.site.equals("YouTube", ignoreCase = true) -> {
                    resolveYouTubeTrailer(video)
                }
                video.site.equals("Vimeo", ignoreCase = true) -> {
                    resolveVimeoTrailer(video)
                }
                else -> {
                    throw IllegalStateException("Sorgente trailer '${video.site}' non supportata (solo YouTube/Vimeo da TMDB).")
                }
            }
        }
    }

    private fun resolveYouTubeTrailer(video: TmdbVideoDto): ResolvedTrailerStream {
        val visitorData = fetchYouTubeVisitorData(video.key)
        var lastReason: String? = null

        val clients = listOf(
            YouTubeClientProfile(
                name = "ANDROID_VR",
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
                clientJson = buildClientJson(
                    clientName = "ANDROID",
                    clientVersion = "20.10.38",
                    visitorData = visitorData,
                    extraFields = """"osName":"Android","osVersion":"14","androidSdkVersion":34,"""
                ),
                userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
            ),
            YouTubeClientProfile(
                name = "WEB_EMBEDDED_PLAYER",
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

        for (profile in clients) {
            val thirdPartyBlock = if (profile.includeEmbedContext) {
                ""","thirdParty":{"embedUrl":"https://www.themoviedb.org/"}"""
            } else ""

            val payload = """
                {
                  "videoId": "${video.key}",
                  "context": {
                    "client": ${profile.clientJson}$thirdPartyBlock
                  },
                  "contentCheckOk": true,
                  "racyCheckOk": true
                }
            """.trimIndent()

            val requestBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", profile.userAgent)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
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

        // Ultimo tentativo sulla pagina watch HTML di YouTube (ytInitialPlayerResponse)
        extractFromYouTubeWatchHtml(video)?.let { return it }

        val detail = lastReason?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        throw IllegalStateException("Trailer non disponibile o bloccato su YouTube$detail.")
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
        if (hlsManifestUrl.isNotEmpty() && verifyHlsManifest(hlsManifestUrl, userAgent)) {
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
                if (url.isNotEmpty() && (mimeType.contains("video/mp4") || mimeType.contains("video/webm"))) {
                    muxedCandidates.add(height to url)
                }
            }
            for ((height, url) in muxedCandidates.sortedByDescending { it.first }) {
                if (verifyProgressiveStream(url, userAgent)) {
                    return ResolvedTrailerStream(
                        video = video,
                        streamUrl = url,
                        isHls = false,
                        sourceLabel = "${video.typeBadge} • $sourceSuffix ${height}p (${video.name})",
                        userAgent = userAgent,
                        referer = ""
                    )
                }
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
                if (url.isEmpty()) continue
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
            if (bestVideo != null && bestAudio != null &&
                verifyProgressiveStream(bestVideo.second, userAgent) &&
                verifyProgressiveStream(bestAudio.second, userAgent)
            ) {
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

    private fun fetchYouTubeVisitorData(videoId: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url("https://www.youtube.com/embed/$videoId")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                )
                .build()
            cleanTrailerHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val html = resp.body?.string().orEmpty()
                Regex(""""visitorData"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
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

    private data class YouTubeClientProfile(
        val name: String,
        val clientJson: String,
        val userAgent: String,
        val includeEmbedContext: Boolean = false
    )
}
