package com.example.data.cache

import android.content.Context
import coil.Coil
import coil.request.CachePolicy
import com.example.VixFlixApplication
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbWatchProviderDto
import com.example.data.remote.TmdbWatchProviderMap
import com.example.data.repository.CatalogFeed
import com.example.ui.components.ImageTargetSizes
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinatore leggero per il precaricamento in background di locandine, backdrop e miniature
 * nella cache RAM + disco di Coil, ottimizzato per Fire TV Stick e Android TV entry-level:
 * - Usa le dimensioni esatte condivise in `ImageTargetSizes` per 100% MemoryCache hit.
 * - Su dispositivi low-RAM esegue prefetch solo su disco per le righe fuori schermo,
 *   evitando di scacciare dalla RAM le bitmap visibili.
 * - Usa un pool fisso di 2 worker invece di creare decine di coroutine in coda.
 */
class BackgroundCachePrefetcher(private val context: Context) {

    private data class PrefetchSpec(
        val url: String,
        val widthPx: Int,
        val heightPx: Int,
        val allowRgb565: Boolean = true,
        val cacheInMemory: Boolean = true
    )

    private val density: Float = context.resources.displayMetrics.density.coerceAtLeast(1f)
    private val isLowRam: Boolean = VixFlixApplication.isLowRamDevice(context)

    private fun dpToPx(dp: Float): Int = ImageTargetSizes.dpToPx(dp, density)

    fun estimateImageDiskCacheBytes(): Long {
        return runCatching {
            val dir = File(context.cacheDir, "vixflix_image_cache")
            if (!dir.exists()) return@runCatching 0L
            dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    fun clearCoilCaches() {
        runCatching {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }

    suspend fun prefetchCatalogVisuals(
        feed: CatalogFeed,
        continueWatching: List<WatchProgressEntity>,
        watchlist: List<WatchlistEntity>,
        providers: List<TmdbWatchProviderDto> = TmdbWatchProviderMap.defaultProviders,
        isTvDevice: Boolean,
        shouldContinue: () -> Boolean
    ) {
        if (!shouldContinue()) return
        val specs = buildCatalogPrefetchSpecs(feed, continueWatching, watchlist, providers, isTvDevice)
        executeSpecs(specs, shouldContinue)
    }

    suspend fun prefetchDetailVisuals(
        details: MediaDetails,
        episodes: List<TmdbEpisodeDto>,
        shouldContinue: () -> Boolean
    ) {
        if (!shouldContinue()) return
        val specs = LinkedHashMap<String, PrefetchSpec>()

        fun addSpec(
            url: String?,
            widthDp: Float,
            heightDp: Float,
            allowRgb565: Boolean = true,
            cacheInMemory: Boolean = true
        ) {
            val clean = url?.trim().takeIf { !it.isNullOrBlank() } ?: return
            val w = dpToPx(widthDp)
            val h = dpToPx(heightDp)
            specs.putIfAbsent("$clean|$w|$h", PrefetchSpec(clean, w, h, allowRgb565, cacheInMemory))
        }

        addSpec(
            details.item.backdropUrl ?: details.item.posterUrl,
            ImageTargetSizes.DETAIL_BACKDROP_WIDTH_DP,
            ImageTargetSizes.DETAIL_BACKDROP_HEIGHT_DP
        )
        details.cast.take(6).forEach { member ->
            addSpec(member.profileUrl, ImageTargetSizes.CAST_AVATAR_DP, ImageTargetSizes.CAST_AVATAR_DP)
        }
        details.similar.take(6).forEach { sim ->
            addSpec(
                sim.posterUrl,
                ImageTargetSizes.POSTER_WIDTH_DP,
                ImageTargetSizes.POSTER_HEIGHT_DP,
                cacheInMemory = !isLowRam
            )
        }
        episodes.take(6).forEach { ep ->
            addSpec(
                ep.stillUrl ?: details.item.backdropUrl,
                ImageTargetSizes.EPISODE_STILL_WIDTH_DP,
                ImageTargetSizes.EPISODE_STILL_HEIGHT_DP
            )
        }

        executeSpecs(specs.values.toList(), shouldContinue)
    }

    private fun buildCatalogPrefetchSpecs(
        feed: CatalogFeed,
        continueWatching: List<WatchProgressEntity>,
        watchlist: List<WatchlistEntity>,
        providers: List<TmdbWatchProviderDto>,
        isTvDevice: Boolean
    ): List<PrefetchSpec> {
        val specs = LinkedHashMap<String, PrefetchSpec>()

        fun addSpec(
            url: String?,
            widthDp: Float,
            heightDp: Float,
            allowRgb565: Boolean = true,
            cacheInMemory: Boolean = true
        ) {
            val clean = url?.trim().takeIf { !it.isNullOrBlank() } ?: return
            val w = dpToPx(widthDp)
            val h = dpToPx(heightDp)
            specs.putIfAbsent("$clean|$w|$h", PrefetchSpec(clean, w, h, allowRgb565, cacheInMemory))
        }

        // 1. Hero Banner (priorità massima all'apertura della Home)
        addSpec(
            url = feed.heroBanner.backdropUrl ?: feed.heroBanner.posterUrl,
            widthDp = ImageTargetSizes.HERO_WIDTH_DP,
            heightDp = if (isTvDevice) ImageTargetSizes.HERO_HEIGHT_TV_DP else ImageTargetSizes.HERO_HEIGHT_MOBILE_DP
        )

        // 2. Loghi piattaforme streaming TMDB (mantengono canale alpha)
        providers.take(10).forEach { provider ->
            addSpec(
                url = provider.logoUrl,
                widthDp = ImageTargetSizes.PROVIDER_LOGO_DP,
                heightDp = ImageTargetSizes.PROVIDER_LOGO_DP,
                allowRgb565 = false
            )
        }

        // 3. Continua a guardare (backdrop 16:9 subito sotto l'Hero)
        continueWatching.take(6).forEach { entry ->
            val cwUrl = entry.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                ?: entry.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            addSpec(
                url = cwUrl,
                widthDp = ImageTargetSizes.CONTINUE_WATCHING_WIDTH_DP,
                heightDp = ImageTargetSizes.CONTINUE_WATCHING_HEIGHT_DP
            )
        }

        // 4. Top 10 di oggi (dimensioni esatte Top10PosterCard)
        feed.top10Today.take(if (isLowRam) 6 else 8).forEach { item ->
            addSpec(
                url = item.posterUrl,
                widthDp = ImageTargetSizes.TOP10_POSTER_WIDTH_DP,
                heightDp = ImageTargetSizes.TOP10_POSTER_HEIGHT_DP
            )
        }

        // 5. Prime locandine delle righe principali (su low-RAM prefetch disco-only per righe sotto la piega)
        val priorityRows: List<List<MediaItem>> = listOf(
            feed.trendingMovies.take(if (isLowRam) 5 else 6),
            feed.trendingTv.take(if (isLowRam) 5 else 6),
            feed.popularMovies.take(if (isLowRam) 4 else 5),
            feed.popularTv.take(if (isLowRam) 4 else 5)
        )
        priorityRows.forEachIndexed { rowIdx, row ->
            val keepInRam = !isLowRam || rowIdx == 0
            row.forEach { item ->
                addSpec(
                    url = item.posterUrl,
                    widthDp = ImageTargetSizes.POSTER_WIDTH_DP,
                    heightDp = ImageTargetSizes.POSTER_HEIGHT_DP,
                    cacheInMemory = keepInRam
                )
            }
        }
        watchlist.take(6).forEach { saved ->
            val wlUrl = saved.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                ?: saved.backdropPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            addSpec(
                url = wlUrl,
                widthDp = ImageTargetSizes.POSTER_WIDTH_DP,
                heightDp = ImageTargetSizes.POSTER_HEIGHT_DP,
                cacheInMemory = !isLowRam
            )
        }

        return specs.values.toList()
    }

    private suspend fun executeSpecs(
        specs: List<PrefetchSpec>,
        shouldContinue: () -> Boolean
    ) = withContext(Dispatchers.IO) {
        if (specs.isEmpty() || !shouldContinue()) return@withContext
        val imageLoader = Coil.imageLoader(context)
        val nextIndex = AtomicInteger(0)
        val workerCount = if (isLowRam) 2 else 2

        try {
            coroutineScope {
                repeat(workerCount) {
                    launch {
                        while (shouldContinue()) {
                            ensureActive()
                            val idx = nextIndex.getAndIncrement()
                            if (idx >= specs.size) break
                            val spec = specs[idx]
                            val request = ImageTargetSizes.buildRequest(
                                context = context,
                                url = spec.url,
                                widthPx = spec.widthPx,
                                heightPx = spec.heightPx,
                                allowRgb565 = spec.allowRgb565,
                                memoryCachePolicy = if (spec.cacheInMemory) CachePolicy.ENABLED else CachePolicy.DISABLED
                            )
                            runCatching { imageLoader.execute(request) }
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            // Interruzione pulita se l'utente avvia un video o cambia schermata
        }
    }
}
