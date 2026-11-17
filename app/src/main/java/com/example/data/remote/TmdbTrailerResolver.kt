package com.example.data.remote

import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

data class ResolvedTrailerStream(
    val video: TmdbVideoDto,
    val streamUrl: String,
    val isHls: Boolean,
    val sourceLabel: String,
    val userAgent: String = ResolvedStream.DEFAULT_USER_AGENT,
    val referer: String = "https://vixsrc.to/",
    val useVixSrcInterceptor: Boolean = true,
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = 0L,
    val fallbackVixStreamUrl: String? = null,
    val fallbackUserAgent: String = ResolvedStream.DEFAULT_USER_AGENT,
    val fallbackReferer: String = "https://vixsrc.to/",
    val fallbackClipStartMs: Long = 90_000L
)

/**
 * Risolve i trailer recuperati dall'endpoint `/movie/{id}/videos` e `/tv/{id}/videos` di TMDB
 * in un flusso HLS diretto riproducibile istantaneamente in-app tramite Media3 ExoPlayer.
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
            // Offset scena dinamico (90s, 150s, 210s, 270s) per partire subito nel vivo dell'azione
            val sceneOffsetsMs = listOf(90_000L, 150_000L, 210_000L, 270_000L)
            val highlightStartMs = sceneOffsetsMs[abs(video.key.hashCode()) % sceneOffsetsMs.size]
            val startSeconds = highlightStartMs / 1000L

            val vixResult = vixSrcResolver.resolveStreamForExoPlayer(
                tmdbId = item.id,
                mediaType = item.mediaType,
                season = if (item.isSeries) 1 else null,
                episode = if (item.isSeries) 1 else null,
                startSeconds = startSeconds,
                lang = lang
            ).getOrThrow()

            ResolvedTrailerStream(
                video = video,
                streamUrl = vixResult.streamUrl,
                isHls = true,
                sourceLabel = "${video.typeBadge} • ${video.name}",
                userAgent = vixResult.userAgent,
                referer = vixResult.referer,
                useVixSrcInterceptor = true,
                clipStartMs = highlightStartMs,
                clipEndMs = highlightStartMs + 120_000L
            )
        }
    }
}
