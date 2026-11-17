package com.example.data.remote

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ResolvedTrailerStream(
    val video: TmdbVideoDto,
    val streamUrl: String,
    val isHls: Boolean,
    val sourceLabel: String,
    val userAgent: String = ResolvedStream.DEFAULT_USER_AGENT,
    val referer: String = "https://www.youtube.com/",
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = 0L
)

/**
 * Risolve i trailer recuperati dall'endpoint `/movie/{id}/videos` e `/tv/{id}/videos` di TMDB
 * in un flusso riproducibile direttamente in-app tramite Media3 ExoPlayer (senza WebView né app esterne).
 */
class TmdbTrailerResolver(
    private val okHttpClient: OkHttpClient,
    private val vixSrcResolver: VixSrcStreamResolver
) {
    suspend fun resolveTrailerForExoPlayer(
        item: MediaItem,
        video: TmdbVideoDto,
        lang: String = "it"
    ): Result<ResolvedTrailerStream> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Tenta l'estrazione diretta dello stream MP4/HLS dal key YouTube fornito da TMDB
            if (video.isYouTube) {
                extractYouTubeDirectStream(video)?.let { return@runCatching it }
            }

            // 2. Cerca eventuale stream MP4/M4V diretto del trailer/anteprima
            extractDirectPreviewStream(item, video)?.let { return@runCatching it }

            // 3. Fallback nativo su clip anteprima HD 1080p dal flusso reale del titolo su VixSrc
            // (così il trailer parte sempre in-app dentro Media3 ExoPlayer anche se YouTube limita l'IP)
            val vixResult = vixSrcResolver.resolveStreamForExoPlayer(
                tmdbId = item.id,
                mediaType = item.mediaType,
                season = if (item.isSeries) 1 else null,
                episode = if (item.isSeries) 1 else null,
                startSeconds = 420L,
                lang = lang
            ).getOrThrow()

            ResolvedTrailerStream(
                video = video,
                streamUrl = vixResult.streamUrl,
                isHls = true,
                sourceLabel = "${video.typeBadge} • Anteprima In-App HD (${video.name})",
                userAgent = vixResult.userAgent,
                referer = vixResult.referer,
                clipStartMs = 420_000L, // Parte da 07:00 per mostrare una clip trailer di 2 minuti del titolo
                clipEndMs = 540_000L
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

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string().orEmpty()

                    // Controlla se è presente hlsManifestUrl
                    val hlsMatch = Regex(""""hlsManifestUrl"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1)
                    if (!hlsMatch.isNullOrBlank()) {
                        return ResolvedTrailerStream(
                            video = video,
                            streamUrl = hlsMatch.replace("\\/", "/").replace("\\u0026", "&"),
                            isHls = true,
                            sourceLabel = "${video.typeBadge} • TMDB Official Stream (${video.name})",
                            userAgent = ua
                        )
                    }

                    // Cerca stream progressivo MP4 combinato audio+video in "formats"
                    val formatsBlock = Regex(""""formats"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                        .find(body)?.groupValues?.getOrNull(1).orEmpty()
                    val urlMatches = Regex(""""url"\s*:\s*"([^"]+)"""").findAll(formatsBlock)
                        .map { it.groupValues[1].replace("\\/", "/").replace("\\u0026", "&") }
                        .toList()

                    val bestUrl = urlMatches.lastOrNull()
                    if (!bestUrl.isNullOrBlank()) {
                        return ResolvedTrailerStream(
                            video = video,
                            streamUrl = bestUrl,
                            isHls = false,
                            sourceLabel = "${video.typeBadge} • TMDB Official Stream (${video.name})",
                            userAgent = ua
                        )
                    }
                }
            }
        }
        return null
    }

    private fun extractDirectPreviewStream(item: MediaItem, video: TmdbVideoDto): ResolvedTrailerStream? {
        return runCatching {
            val encodedTitle = URLEncoder.encode(item.title, "UTF-8")
            val entity = if (item.isSeries) "tvEpisode" else "movie"
            val url = "https://itunes.apple.com/search?term=$encodedTitle&entity=$entity&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", ResolvedStream.DEFAULT_USER_AGENT)
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val previewUrl = Regex(""""previewUrl"\s*:\s*"([^"]+)"""").find(body)
                    ?.groupValues?.getOrNull(1)
                    ?.replace("\\/", "/")
                if (!previewUrl.isNullOrBlank()) {
                    ResolvedTrailerStream(
                        video = video,
                        streamUrl = previewUrl,
                        isHls = previewUrl.contains(".m3u8"),
                        sourceLabel = "${video.typeBadge} • Direct Preview (${video.name})"
                    )
                } else null
            }
        }.getOrNull()
    }
}
