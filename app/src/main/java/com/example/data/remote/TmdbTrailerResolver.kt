package com.example.data.remote

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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
    val isHls: Boolean,
    val sourceLabel: String,
    val userAgent: String = ResolvedStream.DEFAULT_USER_AGENT,
    val referer: String = "https://www.youtube.com/",
    val useVixSrcInterceptor: Boolean = false,
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = 0L,
    val fallbackVixStreamUrl: String? = null,
    val fallbackUserAgent: String = ResolvedStream.DEFAULT_USER_AGENT,
    val fallbackReferer: String = "https://vixsrc.to/",
    val fallbackClipStartMs: Long = 180_000L
)

/**
 * Risolve i trailer recuperati dall'endpoint `/movie/{id}/videos` e `/tv/{id}/videos` di TMDB
 * in un flusso riproducibile direttamente in-app tramite Media3 ExoPlayer (senza WebView né app esterne).
 *
 * Ogni URL candidato viene verificato preventivamente via HTTP Range/GET per evitare schermate nere
 * o errori 403, preparando sempre anche il flusso HD nativo VixSrc come fallback immediato.
 */
class TmdbTrailerResolver(
    private val okHttpClient: OkHttpClient,
    private val vixSrcResolver: VixSrcStreamResolver
) {
    /**
     * Client HTTP pulito (senza VixSrcOkHttpInterceptor) dedicato agli stream diretti
     * come YouTube (`googlevideo.com`) e Apple Preview (`video-ssl.itunes.apple.com`),
     * che altrimenti rifiuterebbero l'header `Origin: https://vixsrc.to` con HTTP 403.
     */
    val directExoOkHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun resolveTrailerForExoPlayer(
        item: MediaItem,
        video: TmdbVideoDto,
        lang: String = "it"
    ): Result<ResolvedTrailerStream> = withContext(Dispatchers.IO) {
        runCatching {
            // Calcola un offset scena dinamico (3 min, 5 min, 7 min, 9 min) per evitare titoli di testa/loghi neri
            val sceneOffsetsMs = listOf(180_000L, 300_000L, 420_000L, 540_000L)
            val highlightStartMs = sceneOffsetsMs[abs(video.key.hashCode()) % sceneOffsetsMs.size]

            // Pre-risolve in modo sicuro anche il flusso HLS VixSrc (con startSeconds = 0L per non alterare il manifest)
            val vixFallback = vixSrcResolver.resolveStreamForExoPlayer(
                tmdbId = item.id,
                mediaType = item.mediaType,
                season = if (item.isSeries) 1 else null,
                episode = if (item.isSeries) 1 else null,
                startSeconds = 0L,
                lang = lang
            ).getOrNull()

            // 1. Tenta l'estrazione diretta e verificata dello stream MP4/HLS dal key YouTube fornito da TMDB
            if (video.isYouTube) {
                extractYouTubeDirectStream(video)?.let { ytStream ->
                    return@runCatching ytStream.copy(
                        fallbackVixStreamUrl = vixFallback?.streamUrl,
                        fallbackUserAgent = vixFallback?.userAgent ?: ResolvedStream.DEFAULT_USER_AGENT,
                        fallbackReferer = vixFallback?.referer ?: "https://vixsrc.to/",
                        fallbackClipStartMs = highlightStartMs
                    )
                }
            }

            // 2. Cerca eventuale stream MP4/M4V diretto verificato del trailer/anteprima
            extractDirectPreviewStream(item, video)?.let { directPreview ->
                return@runCatching directPreview.copy(
                    fallbackVixStreamUrl = vixFallback?.streamUrl,
                    fallbackUserAgent = vixFallback?.userAgent ?: ResolvedStream.DEFAULT_USER_AGENT,
                    fallbackReferer = vixFallback?.referer ?: "https://vixsrc.to/",
                    fallbackClipStartMs = highlightStartMs
                )
            }

            // 3. Fallback nativo garantito su clip anteprima HD 1080p dal flusso reale del titolo su VixSrc
            val vixResult = vixFallback ?: vixSrcResolver.resolveStreamForExoPlayer(
                tmdbId = item.id,
                mediaType = item.mediaType,
                season = if (item.isSeries) 1 else null,
                episode = if (item.isSeries) 1 else null,
                startSeconds = 0L,
                lang = lang
            ).getOrThrow()

            ResolvedTrailerStream(
                video = video,
                streamUrl = vixResult.streamUrl,
                isHls = true,
                sourceLabel = "${video.typeBadge} • Anteprima Video HD (${video.name})",
                userAgent = vixResult.userAgent,
                referer = vixResult.referer,
                useVixSrcInterceptor = true,
                clipStartMs = highlightStartMs,
                clipEndMs = highlightStartMs + 150_000L
            )
        }
    }

    private fun extractYouTubeDirectStream(video: TmdbVideoDto): ResolvedTrailerStream? {
        val clients = listOf(
            Triple(
                "ANDROID_VR",
                """{"clientName":"ANDROID_VR","clientVersion":"1.60.19","deviceMake":"Oculus","deviceModel":"Quest 3","osName":"Android","osVersion":"12L","androidSdkVersion":32,"hl":"it","gl":"IT"}""",
                "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            ),
            Triple(
                "IOS",
                """{"clientName":"IOS","clientVersion":"19.45.4","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.1.0.22B83","hl":"it","gl":"IT"}""",
                "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
            ),
            Triple(
                "ANDROID_TESTSUITE",
                """{"clientName":"ANDROID_TESTSUITE","clientVersion":"1.9","androidSdkVersion":34,"hl":"it","gl":"IT"}""",
                "com.google.android.youtube/1.9 (Linux; U; Android 14) gzip"
            )
        )

        for ((_, clientJson, ua) in clients) {
            runCatching {
                val payload = """
                    {
                      "videoId": "${video.key}",
                      "context": {
                        "client": $clientJson
                      },
                      "contentCheckOk": true,
                      "racyCheckOk": true
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .header("User-Agent", ua)
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                directExoOkHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use

                    val json = JSONObject(body)
                    val playability = json.optJSONObject("playabilityStatus")
                    val status = playability?.optString("status").orEmpty()
                    if (status.isNotEmpty() && status != "OK") {
                        return@use
                    }

                    val streamingData = json.optJSONObject("streamingData") ?: return@use

                    // 1. Verifica hlsManifestUrl se disponibile
                    val hlsManifestUrl = streamingData.optString("hlsManifestUrl").trim()
                    if (hlsManifestUrl.isNotEmpty() && verifyPlayableStreamUrl(hlsManifestUrl, ua, "https://www.youtube.com/")) {
                        return ResolvedTrailerStream(
                            video = video,
                            streamUrl = hlsManifestUrl,
                            isHls = true,
                            sourceLabel = "${video.typeBadge} • TMDB Official Stream (${video.name})",
                            userAgent = ua,
                            referer = "https://www.youtube.com/",
                            useVixSrcInterceptor = false
                        )
                    }

                    // 2. Verifica stream progressivi MP4 combinati audio+video in "formats"
                    val formatsArray = streamingData.optJSONArray("formats")
                    if (formatsArray != null && formatsArray.length() > 0) {
                        val candidateUrls = mutableListOf<Pair<Int, String>>()
                        for (i in 0 until formatsArray.length()) {
                            val fmt = formatsArray.optJSONObject(i) ?: continue
                            val url = fmt.optString("url").trim()
                            val mimeType = fmt.optString("mimeType").lowercase()
                            val height = fmt.optInt("height", 360)
                            if (url.isNotEmpty() && (mimeType.contains("video/mp4") || mimeType.contains("video/webm"))) {
                                candidateUrls.add(height to url)
                            }
                        }

                        // Ordina dal formato più definito a quello più leggero e verifica che risponda 200/206
                        for ((_, candidateUrl) in candidateUrls.sortedByDescending { it.first }) {
                            if (verifyPlayableStreamUrl(candidateUrl, ua, "https://www.youtube.com/")) {
                                return ResolvedTrailerStream(
                                    video = video,
                                    streamUrl = candidateUrl,
                                    isHls = false,
                                    sourceLabel = "${video.typeBadge} • TMDB Official Stream (${video.name})",
                                    userAgent = ua,
                                    referer = "https://www.youtube.com/",
                                    useVixSrcInterceptor = false
                                )
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractDirectPreviewStream(item: MediaItem, video: TmdbVideoDto): ResolvedTrailerStream? {
        return runCatching {
            val encodedTitle = URLEncoder.encode(item.title, "UTF-8")
            val entity = if (item.isSeries) "tvSeason" else "movie"
            val url = "https://itunes.apple.com/search?term=$encodedTitle&entity=$entity&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                .header("Accept", "application/json")
                .build()

            directExoOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null

                val root = JSONObject(body)
                val results = root.optJSONArray("results") ?: return@use null
                val normalizedTarget = item.title.lowercase().trim()

                for (i in 0 until results.length()) {
                    val obj = results.optJSONObject(i) ?: continue
                    val trackName = obj.optString("trackName").lowercase()
                    val collectionName = obj.optString("collectionName").lowercase()
                    val previewUrl = obj.optString("previewUrl").trim()

                    val titleMatches = trackName.contains(normalizedTarget) ||
                        normalizedTarget.contains(trackName.takeIf { it.length >= 4 } ?: "____") ||
                        collectionName.contains(normalizedTarget)

                    if (previewUrl.isNotEmpty() && titleMatches) {
                        val isHls = previewUrl.contains(".m3u8")
                        if (verifyPlayableStreamUrl(previewUrl, ResolvedStream.DEFAULT_USER_AGENT, "https://itunes.apple.com/")) {
                            return@use ResolvedTrailerStream(
                                video = video,
                                streamUrl = previewUrl,
                                isHls = isHls,
                                sourceLabel = "${video.typeBadge} • Trailer HD Diretto (${video.name})",
                                userAgent = ResolvedStream.DEFAULT_USER_AGENT,
                                referer = "https://itunes.apple.com/",
                                useVixSrcInterceptor = false
                            )
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    /**
     * Esegue una richiesta leggera (`Range: bytes=0-2047`) per verificare che l'URL video
     * sia realmente raggiungibile (HTTP 200 o 206) e non bloccato da 403/geo-block.
     */
    private fun verifyPlayableStreamUrl(
        streamUrl: String,
        userAgent: String,
        referer: String
    ): Boolean {
        return runCatching {
            val req = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", userAgent)
                .header("Referer", referer)
                .header("Range", "bytes=0-2047")
                .get()
                .build()

            directExoOkHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) return@use false
                val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                // Accetta flussi video o playlist HLS
                contentType.startsWith("video/") ||
                    contentType.contains("mpegurl") ||
                    contentType.contains("octet-stream") ||
                    streamUrl.contains(".m3u8") ||
                    streamUrl.contains(".mp4") ||
                    streamUrl.contains(".m4v")
            }
        }.getOrDefault(false)
    }
}
