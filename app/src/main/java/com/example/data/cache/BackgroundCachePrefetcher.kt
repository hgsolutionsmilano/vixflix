package com.example.data.cache

import android.content.Context
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbWatchProviderDto
import com.example.data.remote.TmdbWatchProviderMap
import com.example.data.repository.CatalogFeed
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Coordinatore leggero per il precaricamento in background di locandine, backdrop e miniature
 * nella cache RAM + disco di Coil, progettato per non rallentare mai UI, scrolling o playback video.
 *
 * - Usa le stesse dimensioni target dei componenti UI per massimizzare gli hit in MemoryCache.
 * - Limita la concorrenza a massimo 2 download paralleli su Dispatchers.IO.
 * - Si interrompe immediatamente quando l'utente avvia la riproduzione video o disattiva l'opzione.
 */
class BackgroundCachePrefetcher(private val context: Context) {

    private data class PrefetchSpec(
        val url: String,
        val widthPx: Int,
        val heightPx: Int
    )

    private val density: Float = context.resources.displayMetrics.density.coerceAtLeast(1f)
    private val semaphore = Semaphore(permits = 2)

    private fun dpToPx(dp: Float): Int = (dp * density).roundToInt().coerceAtLeast(64)

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

        fun addSpec(url: String?, widthDp: Float, heightDp: Float) {
            val clean = url?.trim().takeIf { !it.isNullOrBlank() } ?: return
            val w = dpToPx(widthDp)
            val h = dpToPx(heightDp)
            specs.putIfAbsent("$clean|$w|$h", PrefetchSpec(clean, w, h))
        }

        addSpec(details.item.backdropUrl ?: details.item.posterUrl, 960f, 360f)
        details.cast.take(6).forEach { member ->
            addSpec(member.profileUrl, 76f, 76f)
        }
        details.similar.take(6).forEach { sim ->
            addSpec(sim.posterUrl, 124f, 190f)
        }
        episodes.take(6).forEach { ep ->
            addSpec(ep.stillUrl ?: details.item.backdropUrl, 124f, 72f)
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

        fun addSpec(url: String?, widthDp: Float, heightDp: Float) {
            val clean = url?.trim().takeIf { !it.isNullOrBlank() } ?: return
            val w = dpToPx(widthDp)
            val h = dpToPx(heightDp)
            specs.putIfAbsent("$clean|$w|$h", PrefetchSpec(clean, w, h))
        }

        // 1. Hero Banner (priorità massima all'apertura della Home)
        addSpec(
            url = feed.heroBanner.backdropUrl ?: feed.heroBanner.posterUrl,
            widthDp = 960f,
            heightDp = if (isTvDevice) 410f else 500f
        )

        // 2. Loghi piattaforme streaming TMDB nella striscia orizzontale Home
        providers.take(10).forEach { provider ->
            addSpec(provider.logoUrl, 44f, 44f)
        }

        // 3. Continua a guardare (backdrop 16:9 usati subito sotto l'Hero)
        continueWatching.take(6).forEach { entry ->
            val cwUrl = entry.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                ?: entry.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            addSpec(cwUrl, 228f, 126f)
        }

        // 4. Top 10 di oggi (prime card visibili)
        feed.top10Today.take(8).forEach { item ->
            addSpec(item.posterUrl, 130f, 194f)
        }

        // 4. Prime locandine delle righe principali Home + Mia Lista
        val priorityRows: List<List<MediaItem>> = listOf(
            feed.trendingMovies.take(6),
            feed.trendingTv.take(6),
            feed.popularMovies.take(5),
            feed.popularTv.take(5)
        )
        priorityRows.forEach { row ->
            row.forEach { item ->
                addSpec(item.posterUrl, 124f, 190f)
            }
        }
        watchlist.take(6).forEach { saved ->
            val wlUrl = saved.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                ?: saved.backdropPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            addSpec(wlUrl, 124f, 190f)
        }

        return specs.values.toList()
    }

    private suspend fun executeSpecs(
        specs: List<PrefetchSpec>,
        shouldContinue: () -> Boolean
    ) = withContext(Dispatchers.IO) {
        if (specs.isEmpty() || !shouldContinue()) return@withContext
        val imageLoader = Coil.imageLoader(context)

        try {
            coroutineScope {
                specs.forEach { spec ->
                    ensureActive()
                    if (!shouldContinue()) return@coroutineScope
                    launch {
                        semaphore.withPermit {
                            if (!shouldContinue()) return@withPermit
                            val request = ImageRequest.Builder(context)
                                .data(spec.url)
                                .size(spec.widthPx, spec.heightPx)
                                .precision(Precision.INEXACT)
                                .scale(Scale.FILL)
                                .allowHardware(true)
                                .allowRgb565(true)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .build()
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
