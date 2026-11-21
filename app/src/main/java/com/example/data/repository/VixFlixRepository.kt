package com.example.data.repository

import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.example.BuildConfig
import com.example.data.local.CacheStatsSummary
import com.example.data.local.CatalogSnapshotEntity
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.MediaDetailsCacheEntity
import com.example.data.local.ResolvedStreamCacheEntity
import com.example.data.local.SeasonEpisodesCacheEntity
import com.example.data.local.VixFlixDao
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.GitHubUpdateService
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.ResolvedTrailerStream
import com.example.data.remote.SubtitleTrack
import com.example.data.remote.TmdbApiConfig
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbGenreDto
import com.example.data.remote.TmdbGenreMap
import com.example.data.remote.TmdbSeasonDetailsDto
import com.example.data.remote.TmdbTrailerResolver
import com.example.data.remote.TmdbVideoDto
import com.example.data.remote.TmdbWatchProviderDto
import com.example.data.remote.TmdbWatchProviderMap
import com.example.data.remote.VixSrcStreamResolver
import com.example.data.remote.YouTubeTrailerCandidate
import com.example.data.remote.toDomain
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Immutable
data class CachedValue<T>(
    val value: T,
    val updatedAtMs: Long,
    val isStale: Boolean
)

@Immutable
data class CachedCatalogResult(
    val feed: CatalogFeed,
    val genres: List<TmdbGenreDto>,
    val updatedAtMs: Long,
    val isStale: Boolean
)

@Immutable
data class PlatformCatalogContent(
    val provider: TmdbWatchProviderDto,
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
    val allCombined: List<MediaItem>
)

@Immutable
@JsonClass(generateAdapter = true)
data class CatalogFeed(
    val heroBanner: MediaItem,
    val top10Today: List<MediaItem>,
    val trendingMovies: List<MediaItem>,
    val trendingTv: List<MediaItem>,
    val popularMovies: List<MediaItem>,
    val popularTv: List<MediaItem>,
    val nowPlayingMovies: List<MediaItem>,
    val topRatedMovies: List<MediaItem>,
    val actionMovies: List<MediaItem>,
    val sciFiMovies: List<MediaItem>
)

class VixFlixRepository(
    private val dao: VixFlixDao,
    baseHttpClient: OkHttpClient = defaultRawOkHttpClient(),
    private val tmdbBaseUrl: String = BuildConfig.TMDB_BASE_URL,
    vixBaseUrl: String = BuildConfig.VIXSRC_BASE_URL,
    httpCacheDir: File? = null
) {
    companion object {
        private const val CATALOG_SNAPSHOT_ID = "home_feed_v1"
        private const val CATALOG_FRESH_MS = 15L * 60L * 1000L
        private const val CATALOG_MAX_RETENTION_MS = 7L * 24L * 3600L * 1000L

        private const val DETAIL_FRESH_MS = 6L * 3600L * 1000L
        private const val DETAIL_MAX_RETENTION_MS = 14L * 24L * 3600L * 1000L

        private const val MAX_DETAILS_DISK_ENTRIES = 60
        private const val MAX_SEASONS_DISK_ENTRIES = 40
        private const val MAX_STREAMS_DISK_ENTRIES = 40

        private fun defaultRawOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val cacheScopePrefix: String =
        if (tmdbBaseUrl == BuildConfig.TMDB_BASE_URL) "" else "u${tmdbBaseUrl.hashCode()}_"

    private val activeCatalogSnapshotId: String = "${cacheScopePrefix}$CATALOG_SNAPSHOT_ID"
    private fun scopedDetailsKey(uniqueKey: String): String = "${cacheScopePrefix}$uniqueKey"
    private fun scopedSeasonKey(tvId: Int, seasonNumber: Int): String = "${cacheScopePrefix}tv_${tvId}_s${seasonNumber}"

    private val rawOkHttpClient: OkHttpClient = baseHttpClient

    private val tmdbHttpCache: Cache? = httpCacheDir?.let { dir ->
        runCatching { Cache(File(dir, "tmdb_http_cache"), 10L * 1024L * 1024L) }.getOrNull()
    }

    /**
     * Deriva dal `baseHttpClient` condiviso riutilizzando ConnectionPool e Dispatcher
     * senza moltiplicare thread pool su Fire TV Stick e dispositivi Android TV low-RAM.
     */
    private val tmdbOkHttpClient: OkHttpClient = rawOkHttpClient.newBuilder()
        .apply { tmdbHttpCache?.let { cache(it) } }
        .addInterceptor { chain ->
            val original = chain.request()
            val apiKey = TmdbApiConfig.currentApiKey.trim().ifBlank { TmdbApiConfig.INTEGRATED_TMDB_API_KEY }
            val newUrl = original.url.newBuilder()
                .addQueryParameter("api_key", apiKey)
                .build()
            val request = original.newBuilder()
                .url(newUrl)
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val gitHubOkHttpClient: OkHttpClient = rawOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Zero-reflection Moshi: tutti i DTO/modelli usano adapter generati da KSP (@JsonClass(generateAdapter = true))
    internal val moshi: Moshi = Moshi.Builder().build()

    private val catalogFeedAdapter = moshi.adapter(CatalogFeed::class.java)
    private val mediaDetailsAdapter = moshi.adapter(MediaDetails::class.java)
    private val genreListAdapter = moshi.adapter<List<TmdbGenreDto>>(
        Types.newParameterizedType(List::class.java, TmdbGenreDto::class.java)
    )
    private val episodeListAdapter = moshi.adapter<List<TmdbEpisodeDto>>(
        Types.newParameterizedType(List::class.java, TmdbEpisodeDto::class.java)
    )
    private val subtitleListAdapter = moshi.adapter<List<SubtitleTrack>>(
        Types.newParameterizedType(List::class.java, SubtitleTrack::class.java)
    )

    private data class TimedMemoryEntry<T>(
        val value: T,
        val updatedAtMs: Long
    )

    private val detailsMemoryCache = LruCache<String, TimedMemoryEntry<MediaDetails>>(24)
    private val episodesMemoryCache = LruCache<String, TimedMemoryEntry<List<TmdbEpisodeDto>>>(20)
    private val platformCatalogMemoryCache = LruCache<Int, TimedMemoryEntry<PlatformCatalogContent>>(12)
    private val movieGenreMemoryCache = LruCache<Int, TimedMemoryEntry<List<MediaItem>>>(20)
    private val streamMemoryCache = ConcurrentHashMap<String, ResolvedStream>()
    @Volatile
    private var cachedGenresInMemory: TimedMemoryEntry<List<TmdbGenreDto>>? = null
    @Volatile
    private var cachedProvidersInMemory: TimedMemoryEntry<List<TmdbWatchProviderDto>>? = null

    private val tmdbApi: TmdbApiService = Retrofit.Builder()
        .baseUrl(tmdbBaseUrl)
        .client(tmdbOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TmdbApiService::class.java)

    val vixSrcResolver = VixSrcStreamResolver(rawOkHttpClient, vixBaseUrl)
    val trailerResolver = TmdbTrailerResolver(rawOkHttpClient, vixSrcResolver)
    val gitHubUpdateService = GitHubUpdateService(gitHubOkHttpClient)

    val continueWatchingFlow: Flow<List<WatchProgressEntity>> = dao.observeContinueWatching()
    val watchlistFlow: Flow<List<WatchlistEntity>> = dao.observeWatchlist()

    fun observeSeasonProgress(tmdbId: Int, seasonNumber: Int): Flow<List<EpisodeProgressEntity>> =
        dao.observeSeasonProgress(tmdbId, seasonNumber)

    /**
     * Legge immediatamente lo snapshot più recente del catalogo Home da Room per apertura istantanea.
     */
    suspend fun getCachedCatalogFeed(): CachedCatalogResult? = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = dao.getCatalogSnapshot(activeCatalogSnapshotId) ?: return@withContext null
            val now = System.currentTimeMillis()
            if (now - snapshot.updatedAtMs > CATALOG_MAX_RETENTION_MS) {
                return@withContext null
            }
            val feed = catalogFeedAdapter.fromJson(snapshot.feedJson) ?: return@withContext null
            val genres = runCatching {
                genreListAdapter.fromJson(snapshot.movieGenresJson)
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: TmdbGenreMap.defaultMovieGenres
            cachedGenresInMemory = TimedMemoryEntry(genres, snapshot.updatedAtMs)
            CachedCatalogResult(
                feed = feed,
                genres = genres,
                updatedAtMs = snapshot.updatedAtMs,
                isStale = (now - snapshot.updatedAtMs) > CATALOG_FRESH_MS
            )
        }.getOrNull()
    }

    /**
     * Carica il catalogo dalle API live di TMDB con priorità in due stadi:
     * - Stadio 1 (above-the-fold): Hero, Top 10, Trending Film e Trending Serie TV -> emesso subito tramite `onPrimaryFeedReady`.
     * - Stadio 2 (below-the-fold): Popolari, Al Cinema, Più Votati, Azione e Sci-Fi -> completa il feed e salva lo snapshot su Room.
     */
    suspend fun fetchLiveCatalogFeed(
        genresToPersist: List<TmdbGenreDto>? = null,
        onPrimaryFeedReady: ((CatalogFeed) -> Unit)? = null
    ): Result<CatalogFeed> = runCatching {
        val apiKey = TmdbApiConfig.currentApiKey.trim().ifBlank { TmdbApiConfig.INTEGRATED_TMDB_API_KEY }
        if (apiKey.isBlank()) {
            throw IllegalStateException("Chiave API TMDB mancante.")
        }

        coroutineScope {
            val trendingAllDef = async { tmdbApi.getTrendingAllDay() }
            val trendingMoviesDef = async { tmdbApi.getTrendingMoviesWeek() }
            val trendingTvDef = async { tmdbApi.getTrendingTvWeek() }
            val popularMoviesDef = async { tmdbApi.getPopularMovies() }
            val popularTvDef = async { tmdbApi.getPopularTv() }
            val nowPlayingDef = async { tmdbApi.getNowPlayingMovies() }
            val topRatedDef = async { tmdbApi.getTopRatedMovies() }
            val actionDef = async { tmdbApi.discoverMoviesByGenre("28") }
            val sciFiDef = async { tmdbApi.discoverMoviesByGenre("878") }

            val trendingAll = trendingAllDef.await().results.mapNotNull { it.toDomain("movie") }.distinctBy { it.uniqueKey }
            val trendingMovies = trendingMoviesDef.await().results.mapNotNull { it.toDomain("movie") }.distinctBy { it.uniqueKey }
            val trendingTv = trendingTvDef.await().results.mapNotNull { it.toDomain("tv") }.distinctBy { it.uniqueKey }

            val primaryHero = trendingAll.firstOrNull { it.backdropPath != null }
                ?: trendingMovies.firstOrNull()
                ?: trendingTv.firstOrNull()

            if (onPrimaryFeedReady != null && primaryHero != null) {
                onPrimaryFeedReady(
                    CatalogFeed(
                        heroBanner = primaryHero,
                        top10Today = trendingAll.take(10),
                        trendingMovies = trendingMovies,
                        trendingTv = trendingTv,
                        popularMovies = emptyList(),
                        popularTv = emptyList(),
                        nowPlayingMovies = emptyList(),
                        topRatedMovies = emptyList(),
                        actionMovies = emptyList(),
                        sciFiMovies = emptyList()
                    )
                )
            }

            val popularMovies = popularMoviesDef.await().results.mapNotNull { it.toDomain("movie") }.distinctBy { it.uniqueKey }
            val popularTv = popularTvDef.await().results.mapNotNull { it.toDomain("tv") }.distinctBy { it.uniqueKey }
            val nowPlaying = nowPlayingDef.await().results.mapNotNull { it.toDomain("movie") }.distinctBy { it.uniqueKey }
            val topRated = topRatedDef.await().results.mapNotNull { it.toDomain("movie") }.distinctBy { it.uniqueKey }
            val action = actionDef.await().results.mapNotNull { it.toDomain("movie") }.distinctBy { it.uniqueKey }
            val sciFi = sciFiDef.await().results.mapNotNull { it.toDomain("movie") }.distinctBy { it.uniqueKey }

            val hero = primaryHero
                ?: popularMovies.firstOrNull()
                ?: throw IllegalStateException("TMDB non ha restituito risultati per il catalogo.")

            val feed = CatalogFeed(
                heroBanner = hero,
                top10Today = trendingAll.take(10),
                trendingMovies = trendingMovies,
                trendingTv = trendingTv,
                popularMovies = popularMovies,
                popularTv = popularTv,
                nowPlayingMovies = nowPlaying,
                topRatedMovies = topRated,
                actionMovies = action,
                sciFiMovies = sciFi
            )

            withContext(Dispatchers.IO) {
                runCatching {
                    val genres = genresToPersist
                        ?: cachedGenresInMemory?.value
                        ?: TmdbGenreMap.defaultMovieGenres
                    dao.upsertCatalogSnapshot(
                        CatalogSnapshotEntity(
                            id = activeCatalogSnapshotId,
                            feedJson = catalogFeedAdapter.toJson(feed),
                            movieGenresJson = genreListAdapter.toJson(genres),
                            updatedAtMs = System.currentTimeMillis()
                        )
                    )
                }
            }

            feed
        }
    }

    suspend fun getCachedMediaDetails(mediaItem: MediaItem): CachedValue<MediaDetails>? = withContext(Dispatchers.IO) {
        val key = scopedDetailsKey(mediaItem.uniqueKey)
        val now = System.currentTimeMillis()
        detailsMemoryCache.get(key)?.let { mem ->
            if (now - mem.updatedAtMs <= DETAIL_MAX_RETENTION_MS) {
                return@withContext CachedValue(
                    value = mem.value,
                    updatedAtMs = mem.updatedAtMs,
                    isStale = (now - mem.updatedAtMs) > DETAIL_FRESH_MS
                )
            }
        }
        runCatching {
            val entity = dao.getMediaDetailsCache(key) ?: return@withContext null
            if (now - entity.updatedAtMs > DETAIL_MAX_RETENTION_MS) {
                return@withContext null
            }
            val parsed = mediaDetailsAdapter.fromJson(entity.detailsJson) ?: return@withContext null
            detailsMemoryCache.put(key, TimedMemoryEntry(parsed, entity.updatedAtMs))
            dao.touchMediaDetailsCache(key, now)
            CachedValue(
                value = parsed,
                updatedAtMs = entity.updatedAtMs,
                isStale = (now - entity.updatedAtMs) > DETAIL_FRESH_MS
            )
        }.getOrNull()
    }

    suspend fun fetchMediaDetails(mediaItem: MediaItem): Result<MediaDetails> = runCatching {
        val details = if (mediaItem.isSeries) {
            val dto = tmdbApi.getTvDetails(mediaItem.id)
            val updatedItem = mediaItem.copy(
                title = dto.name ?: mediaItem.title,
                overview = dto.overview?.takeIf { it.isNotBlank() } ?: mediaItem.overview,
                posterPath = dto.posterPath ?: mediaItem.posterPath,
                backdropPath = dto.backdropPath ?: mediaItem.backdropPath,
                voteAverage = dto.voteAverage ?: mediaItem.voteAverage,
                releaseYear = dto.firstAirDate?.take(4) ?: mediaItem.releaseYear
            )
            val rawVideos = dto.videos?.results.orEmpty().ifEmpty {
                runCatching { tmdbApi.getTvVideos(mediaItem.id).results }.getOrDefault(emptyList())
            }
            val validSeasons = dto.seasons
                .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                .distinctBy { it.seasonNumber }
            MediaDetails(
                item = updatedItem,
                tagline = dto.tagline.orEmpty(),
                runtimeMinutes = null,
                genres = dto.genres.map { it.name }.distinct(),
                cast = dto.credits?.cast?.distinctBy { it.id }?.take(12).orEmpty(),
                similar = dto.similar?.results?.mapNotNull { it.toDomain("tv") }?.distinctBy { it.uniqueKey }?.take(12).orEmpty(),
                seasons = validSeasons,
                numberOfSeasons = dto.numberOfSeasons ?: validSeasons.size,
                trailers = prioritizeTrailers(rawVideos)
            )
        } else {
            val dto = tmdbApi.getMovieDetails(mediaItem.id)
            val updatedItem = mediaItem.copy(
                title = dto.title ?: mediaItem.title,
                overview = dto.overview?.takeIf { it.isNotBlank() } ?: mediaItem.overview,
                posterPath = dto.posterPath ?: mediaItem.posterPath,
                backdropPath = dto.backdropPath ?: mediaItem.backdropPath,
                voteAverage = dto.voteAverage ?: mediaItem.voteAverage,
                releaseYear = dto.releaseDate?.take(4) ?: mediaItem.releaseYear
            )
            val rawVideos = dto.videos?.results.orEmpty().ifEmpty {
                runCatching { tmdbApi.getMovieVideos(mediaItem.id).results }.getOrDefault(emptyList())
            }
            MediaDetails(
                item = updatedItem,
                tagline = dto.tagline.orEmpty(),
                runtimeMinutes = dto.runtime,
                genres = dto.genres.map { it.name }.distinct(),
                cast = dto.credits?.cast?.distinctBy { it.id }?.take(12).orEmpty(),
                similar = dto.similar?.results?.mapNotNull { it.toDomain("movie") }?.distinctBy { it.uniqueKey }?.take(12).orEmpty(),
                trailers = prioritizeTrailers(rawVideos)
            )
        }

        val now = System.currentTimeMillis()
        val key = scopedDetailsKey(mediaItem.uniqueKey)
        detailsMemoryCache.put(key, TimedMemoryEntry(details, now))
        withContext(Dispatchers.IO) {
            runCatching {
                dao.upsertMediaDetailsCache(
                    MediaDetailsCacheEntity(
                        cacheKey = key,
                        mediaType = mediaItem.mediaType,
                        tmdbId = mediaItem.id,
                        detailsJson = mediaDetailsAdapter.toJson(details),
                        updatedAtMs = now,
                        lastAccessedAtMs = now
                    )
                )
                dao.trimMediaDetailsCache(MAX_DETAILS_DISK_ENTRIES)
            }
        }
        details
    }

    private fun prioritizeTrailers(videos: List<TmdbVideoDto>): List<TmdbVideoDto> {
        return videos
            .filter { it.key.isNotBlank() }
            .distinctBy { it.id.ifBlank { it.key } }
            .sortedWith(
                compareByDescending<TmdbVideoDto> { it.type.equals("Trailer", ignoreCase = true) }
                    .thenByDescending { it.languageCode.equals("it", ignoreCase = true) }
                    .thenByDescending { it.official }
                    .thenByDescending { it.type.equals("Teaser", ignoreCase = true) }
            )
    }

    suspend fun searchYouTubeTrailers(item: MediaItem): Result<List<YouTubeTrailerCandidate>> {
        return trailerResolver.searchYouTubeTrailers(item)
    }

    suspend fun resolveYouTubeCandidateForExoPlayer(
        candidate: YouTubeTrailerCandidate
    ): Result<ResolvedTrailerStream> {
        return trailerResolver.resolveYouTubeCandidateForExoPlayer(candidate)
    }

    suspend fun resolveTrailerForExoPlayer(
        item: MediaItem,
        video: TmdbVideoDto,
        lang: String
    ): Result<ResolvedTrailerStream> {
        return trailerResolver.resolveTrailerForExoPlayer(item = item, video = video, lang = lang)
    }

    suspend fun fetchMovieGenres(): List<TmdbGenreDto> {
        val now = System.currentTimeMillis()
        return runCatching {
            tmdbApi.getMovieGenres().genres
                .filter { it.id > 0 && it.name.isNotBlank() }
                .distinctBy { it.id }
                .ifEmpty { TmdbGenreMap.defaultMovieGenres }
                .also { cachedGenresInMemory = TimedMemoryEntry(it, now) }
        }.getOrDefault(TmdbGenreMap.defaultMovieGenres)
    }

    fun getCachedMoviesByGenre(genreId: Int): List<MediaItem>? {
        val now = System.currentTimeMillis()
        return movieGenreMemoryCache.get(genreId)
            ?.takeIf { now - it.updatedAtMs <= CATALOG_FRESH_MS && it.value.isNotEmpty() }
            ?.value
    }

    fun invalidateMovieGenreCache(genreId: Int) {
        movieGenreMemoryCache.remove(genreId)
    }

    fun getCachedPlatformCatalog(providerId: Int): PlatformCatalogContent? {
        val now = System.currentTimeMillis()
        return platformCatalogMemoryCache.get(providerId)
            ?.takeIf { now - it.updatedAtMs <= CATALOG_FRESH_MS && it.value.allCombined.isNotEmpty() }
            ?.value
    }

    fun invalidatePlatformCatalogCache(providerId: Int) {
        platformCatalogMemoryCache.remove(providerId)
    }

    suspend fun fetchMoviesByGenre(
        genreId: Int,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Result<List<MediaItem>> = runCatching {
        val now = System.currentTimeMillis()
        if (!forceRefresh && page == 1) {
            getCachedMoviesByGenre(genreId)?.let { return@runCatching it }
        }
        val response = tmdbApi.discoverMoviesByGenre(genreId = genreId.toString(), page = page)
        response.results.mapNotNull { it.toDomain("movie") }.distinctBy { it.uniqueKey }.also { items ->
            if (page == 1 && items.isNotEmpty()) {
                movieGenreMemoryCache.put(genreId, TimedMemoryEntry(items, now))
            }
        }
    }

    /**
     * Recupera da TMDB la lista delle piattaforme streaming con i relativi loghi ufficiali
     * (`watch/providers/movie` + `watch/providers/tv`), mettendo in primo piano
     * Disney+, Paramount+, Prime Video e Netflix.
     */
    suspend fun fetchWatchProviders(): List<TmdbWatchProviderDto> {
        val now = System.currentTimeMillis()
        cachedProvidersInMemory?.let { mem ->
            if (now - mem.updatedAtMs < DETAIL_FRESH_MS && mem.value.isNotEmpty()) {
                return mem.value
            }
        }
        return runCatching {
            coroutineScope {
                val movieProvidersDef = async {
                    runCatching { tmdbApi.getMovieWatchProviders().results }.getOrDefault(emptyList())
                }
                val tvProvidersDef = async {
                    runCatching { tmdbApi.getTvWatchProviders().results }.getOrDefault(emptyList())
                }
                val combined = movieProvidersDef.await() + tvProvidersDef.await()
                TmdbWatchProviderMap.mergeAndSortProviders(combined).also { merged ->
                    cachedProvidersInMemory = TimedMemoryEntry(merged, now)
                }
            }
        }.getOrDefault(TmdbWatchProviderMap.defaultProviders)
    }

    /**
     * Carica da TMDB tutti i titoli (Film e Serie TV) disponibili sulla piattaforma selezionata
     * (es. Disney+, Paramount+, Prime Video, Netflix) tramite `/discover/movie` e `/discover/tv`.
     */
    suspend fun fetchTitlesByWatchProvider(
        provider: TmdbWatchProviderDto,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Result<PlatformCatalogContent> = runCatching {
        val now = System.currentTimeMillis()
        if (!forceRefresh && page == 1) {
            platformCatalogMemoryCache.get(provider.providerId)?.let { mem ->
                if (now - mem.updatedAtMs <= CATALOG_FRESH_MS) {
                    return@runCatching mem.value
                }
            }
        }

        val queryIds = TmdbWatchProviderMap.providerQueryExpression(provider.providerId)
        coroutineScope {
            val moviesDef = async {
                runCatching {
                    tmdbApi.discoverMoviesByProvider(providerIds = queryIds, page = page).results
                        .mapNotNull { it.toDomain("movie") }
                        .distinctBy { it.uniqueKey }
                }
            }
            val tvDef = async {
                runCatching {
                    tmdbApi.discoverTvByProvider(providerIds = queryIds, page = page).results
                        .mapNotNull { it.toDomain("tv") }
                        .distinctBy { it.uniqueKey }
                }
            }

            val moviesResult = moviesDef.await()
            val tvResult = tvDef.await()
            if (moviesResult.isFailure && tvResult.isFailure) {
                throw (moviesResult.exceptionOrNull() ?: tvResult.exceptionOrNull()
                    ?: IllegalStateException("Impossibile caricare i titoli per ${provider.displayName}."))
            }

            val movies = moviesResult.getOrDefault(emptyList())
            val series = tvResult.getOrDefault(emptyList())

            // Intercala Film e Serie TV per offrire una vista "Tutti" bilanciata e immediata
            val combined = buildList {
                val maxLen = maxOf(movies.size, series.size)
                for (i in 0 until maxLen) {
                    if (i < movies.size) add(movies[i])
                    if (i < series.size) add(series[i])
                }
            }.distinctBy { it.uniqueKey }

            PlatformCatalogContent(
                provider = provider,
                movies = movies,
                series = series,
                allCombined = combined
            ).also { content ->
                if (page == 1) {
                    platformCatalogMemoryCache.put(provider.providerId, TimedMemoryEntry(content, now))
                }
            }
        }
    }

    suspend fun getCachedSeasonEpisodes(tvId: Int, seasonNumber: Int): CachedValue<List<TmdbEpisodeDto>>? =
        withContext(Dispatchers.IO) {
            val key = scopedSeasonKey(tvId, seasonNumber)
            val now = System.currentTimeMillis()
            episodesMemoryCache.get(key)?.let { mem ->
                if (now - mem.updatedAtMs <= DETAIL_MAX_RETENTION_MS) {
                    return@withContext CachedValue(
                        value = mem.value,
                        updatedAtMs = mem.updatedAtMs,
                        isStale = (now - mem.updatedAtMs) > DETAIL_FRESH_MS
                    )
                }
            }
            runCatching {
                val entity = dao.getSeasonEpisodesCache(key) ?: return@withContext null
                if (now - entity.updatedAtMs > DETAIL_MAX_RETENTION_MS) {
                    return@withContext null
                }
                val parsed = episodeListAdapter.fromJson(entity.episodesJson) ?: return@withContext null
                episodesMemoryCache.put(key, TimedMemoryEntry(parsed, entity.updatedAtMs))
                dao.touchSeasonEpisodesCache(key, now)
                CachedValue(
                    value = parsed,
                    updatedAtMs = entity.updatedAtMs,
                    isStale = (now - entity.updatedAtMs) > DETAIL_FRESH_MS
                )
            }.getOrNull()
        }

    suspend fun fetchSeasonEpisodes(tvId: Int, seasonNumber: Int): Result<List<TmdbEpisodeDto>> = runCatching {
        val season: TmdbSeasonDetailsDto = tmdbApi.getTvSeasonDetails(tvId = tvId, seasonNumber = seasonNumber)
        val episodes = season.episodes.distinctBy { it.seasonNumber to it.episodeNumber }
        val now = System.currentTimeMillis()
        val key = scopedSeasonKey(tvId, seasonNumber)
        episodesMemoryCache.put(key, TimedMemoryEntry(episodes, now))
        withContext(Dispatchers.IO) {
            runCatching {
                dao.upsertSeasonEpisodesCache(
                    SeasonEpisodesCacheEntity(
                        cacheKey = key,
                        tvId = tvId,
                        seasonNumber = seasonNumber,
                        episodesJson = episodeListAdapter.toJson(episodes),
                        updatedAtMs = now,
                        lastAccessedAtMs = now
                    )
                )
                dao.trimSeasonEpisodesCache(MAX_SEASONS_DISK_ENTRIES)
            }
        }
        episodes
    }

    suspend fun searchTmdb(query: String): Result<List<MediaItem>> = runCatching {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@runCatching emptyList()
        val response = tmdbApi.searchMulti(query = trimmed)
        response.results.mapNotNull { it.toDomain("movie") }.distinctBy { it.uniqueKey }
    }

    /**
     * Recupera il timestamp salvato su Room per un contenuto (o episodio specifico)
     * e risolve lo stream su vixsrc.to passando `startAt` (secondi) e `startMinutes` (minuti).
     */
    suspend fun getSavedProgress(
        tmdbId: Int,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null
    ): Long {
        if (mediaType == "tv" && season != null && episode != null) {
            val epKey = "tv_${tmdbId}_s${season}_e${episode}"
            val epSaved = dao.getEpisodeProgress(epKey)
            if (epSaved != null && epSaved.positionMs > 0L) {
                return epSaved.positionMs
            }
        }
        val mainKey = "${mediaType}_${tmdbId}"
        val saved = dao.getWatchProgress(mainKey) ?: return 0L
        if (mediaType == "tv" && (saved.seasonNumber != season || saved.episodeNumber != episode)) {
            return 0L
        }
        return saved.positionMs
    }

    suspend fun saveWatchProgress(
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeTitle: String?,
        positionMs: Long,
        durationMs: Long
    ) {
        if (positionMs < 3_000L) return
        val safeDuration = if (durationMs > 0L) durationMs else 5_400_000L // 90m stima se live HLS non espone durata iniziale
        val mainId = "${item.mediaType}_${item.id}"

        // Se l'utente ha completato oltre il 96% di un film, rimuovilo da "Continua a guardare"
        if (item.mediaType == "movie" && durationMs > 60_000L && positionMs >= (durationMs * 0.96).toLong()) {
            dao.deleteWatchProgress(mainId)
            return
        }

        val entity = WatchProgressEntity(
            id = mainId,
            tmdbId = item.id,
            mediaType = item.mediaType,
            title = item.title,
            overview = item.overview,
            posterPath = item.posterPath,
            backdropPath = item.backdropPath,
            voteAverage = item.voteAverage,
            releaseYear = item.releaseYear,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            positionMs = positionMs,
            durationMs = safeDuration,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertWatchProgress(entity)

        if (item.mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
            dao.upsertEpisodeProgress(
                EpisodeProgressEntity(
                    episodeKey = "tv_${item.id}_s${seasonNumber}_e${episodeNumber}",
                    tmdbId = item.id,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    positionMs = positionMs,
                    durationMs = safeDuration,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeContinueWatching(id: String) {
        dao.deleteWatchProgress(id)
    }

    suspend fun clearAllContinueWatching() {
        dao.clearAllWatchProgress()
    }

    suspend fun toggleWatchlist(item: MediaItem, currentlyInList: Boolean) {
        val key = item.uniqueKey
        if (currentlyInList) {
            dao.removeFromWatchlist(key)
        } else {
            dao.addToWatchlist(
                WatchlistEntity(
                    id = key,
                    tmdbId = item.id,
                    mediaType = item.mediaType,
                    title = item.title,
                    overview = item.overview,
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    voteAverage = item.voteAverage,
                    releaseYear = item.releaseYear
                )
            )
        }
    }

    private fun streamCacheKey(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        lang: String
    ): String = "${mediaType}_${tmdbId}_${season ?: 0}_${episode ?: 0}_${lang.lowercase()}"

    suspend fun resolveVixSrcStream(
        item: MediaItem,
        season: Int?,
        episode: Int?,
        startPositionMs: Long,
        lang: String,
        forceRefresh: Boolean = false
    ): Result<ResolvedStream> {
        val startSeconds = (startPositionMs / 1000L).coerceAtLeast(0L)
        val startMinutes = (startSeconds / 60L).toInt()
        val key = streamCacheKey(item.id, item.mediaType, season, episode, lang)
        val now = System.currentTimeMillis()

        if (!forceRefresh) {
            val mem = streamMemoryCache[key]
            if (mem != null && mem.isDirectHls && mem.expiresAtMs > now) {
                withContext(Dispatchers.IO) {
                    runCatching { dao.touchStreamCache(key, now) }
                }
                return Result.success(
                    mem.copy(
                        startSeconds = startSeconds,
                        startMinutes = startMinutes,
                        fromCache = true
                    )
                )
            }

            val cachedEntity = withContext(Dispatchers.IO) {
                runCatching { dao.getStreamCache(key) }.getOrNull()
            }
            if (cachedEntity != null && cachedEntity.expiresAtMs > now && cachedEntity.streamUrl.isNotBlank()) {
                val subtitles = runCatching {
                    subtitleListAdapter.fromJson(cachedEntity.subtitlesJson).orEmpty()
                }.getOrDefault(emptyList())
                val restored = ResolvedStream(
                    streamUrl = cachedEntity.streamUrl,
                    embedUrl = cachedEntity.embedUrl,
                    referer = cachedEntity.referer,
                    userAgent = cachedEntity.userAgent,
                    startSeconds = startSeconds,
                    startMinutes = startMinutes,
                    subtitles = subtitles,
                    isDirectHls = true,
                    vixCatalogVerified = true,
                    expiresAtMs = cachedEntity.expiresAtMs,
                    fromCache = true
                )
                streamMemoryCache[key] = restored
                withContext(Dispatchers.IO) {
                    runCatching { dao.touchStreamCache(key, now) }
                }
                return Result.success(restored)
            }
        } else {
            invalidateCachedStream(item, season, episode, lang)
        }

        val liveResult = vixSrcResolver.resolveStreamForExoPlayer(
            tmdbId = item.id,
            mediaType = item.mediaType,
            season = season,
            episode = episode,
            startSeconds = startSeconds,
            lang = lang
        )

        liveResult.onSuccess { resolved ->
            val resolvedNow = System.currentTimeMillis()
            val safeExpiresAt = if (resolved.expiresAtMs > resolvedNow) {
                resolved.expiresAtMs
            } else {
                resolvedNow + 20L * 60L * 1000L
            }
            val cleanForCache = resolved.copy(expiresAtMs = safeExpiresAt, fromCache = false)
            streamMemoryCache[key] = cleanForCache
            withContext(Dispatchers.IO) {
                runCatching {
                    dao.upsertStreamCache(
                        ResolvedStreamCacheEntity(
                            cacheKey = key,
                            tmdbId = item.id,
                            mediaType = item.mediaType,
                            seasonNumber = season,
                            episodeNumber = episode,
                            lang = lang.lowercase(),
                            streamUrl = cleanForCache.streamUrl,
                            embedUrl = cleanForCache.embedUrl,
                            referer = cleanForCache.referer,
                            userAgent = cleanForCache.userAgent,
                            startSeconds = cleanForCache.startSeconds,
                            startMinutes = cleanForCache.startMinutes,
                            subtitlesJson = subtitleListAdapter.toJson(cleanForCache.subtitles),
                            expiresAtMs = safeExpiresAt,
                            verifiedAtMs = resolvedNow,
                            lastUsedAtMs = resolvedNow
                        )
                    )
                    dao.deleteExpiredStreamCaches(resolvedNow)
                    dao.trimStreamCaches(MAX_STREAMS_DISK_ENTRIES)
                }
            }
        }
        return liveResult
    }

    suspend fun cacheInterceptedHlsStream(
        item: MediaItem,
        season: Int?,
        episode: Int?,
        lang: String,
        stream: ResolvedStream
    ) {
        val key = streamCacheKey(item.id, item.mediaType, season, episode, lang)
        val now = System.currentTimeMillis()
        val safeExpires = vixSrcResolver.computeSafeExpiresAtMs(stream.streamUrl, now)
        val clean = stream.copy(expiresAtMs = safeExpires, fromCache = false)
        streamMemoryCache[key] = clean
        withContext(Dispatchers.IO) {
            runCatching {
                dao.upsertStreamCache(
                    ResolvedStreamCacheEntity(
                        cacheKey = key,
                        tmdbId = item.id,
                        mediaType = item.mediaType,
                        seasonNumber = season,
                        episodeNumber = episode,
                        lang = lang.lowercase(),
                        streamUrl = clean.streamUrl,
                        embedUrl = clean.embedUrl,
                        referer = clean.referer,
                        userAgent = clean.userAgent,
                        startSeconds = clean.startSeconds,
                        startMinutes = clean.startMinutes,
                        subtitlesJson = subtitleListAdapter.toJson(clean.subtitles),
                        expiresAtMs = safeExpires,
                        verifiedAtMs = now,
                        lastUsedAtMs = now
                    )
                )
                dao.trimStreamCaches(MAX_STREAMS_DISK_ENTRIES)
            }
        }
    }

    suspend fun invalidateCachedStream(
        item: MediaItem,
        season: Int?,
        episode: Int?,
        lang: String
    ) {
        val key = streamCacheKey(item.id, item.mediaType, season, episode, lang)
        streamMemoryCache.remove(key)
        withContext(Dispatchers.IO) {
            runCatching { dao.deleteStreamCache(key) }
        }
    }

    suspend fun getCacheStatsSummary(imageCacheBytes: Long): CacheStatsSummary = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        runCatching { dao.deleteExpiredStreamCaches(now) }
        CacheStatsSummary(
            catalogUpdatedAtMs = runCatching { dao.getCatalogSnapshot(activeCatalogSnapshotId)?.updatedAtMs }.getOrNull(),
            cachedDetailsCount = runCatching { dao.countMediaDetailsCache() }.getOrDefault(0),
            cachedSeasonsCount = runCatching { dao.countSeasonEpisodesCache() }.getOrDefault(0),
            validStreamLinksCount = runCatching { dao.countValidStreamCaches(now) }.getOrDefault(0),
            imageCacheSizeBytes = imageCacheBytes
        )
    }

    /**
     * Riduce l'impronta RAM delle cache volatili in memoria su pressione di sistema (`onTrimMemory`),
     * lasciando intatta la persistenza su disco Room/HTTP.
     */
    fun trimMemoryCaches(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE
        ) {
            detailsMemoryCache.evictAll()
            episodesMemoryCache.evictAll()
            platformCatalogMemoryCache.evictAll()
            movieGenreMemoryCache.evictAll()
        } else {
            detailsMemoryCache.trimToSize(8)
            episodesMemoryCache.trimToSize(6)
            platformCatalogMemoryCache.trimToSize(4)
            movieGenreMemoryCache.trimToSize(6)
        }
    }

    suspend fun clearAllLocalCaches(clearImageCaches: () -> Unit) {
        detailsMemoryCache.evictAll()
        episodesMemoryCache.evictAll()
        platformCatalogMemoryCache.evictAll()
        movieGenreMemoryCache.evictAll()
        streamMemoryCache.clear()
        cachedGenresInMemory = null
        cachedProvidersInMemory = null
        withContext(Dispatchers.IO) {
            runCatching { tmdbHttpCache?.evictAll() }
            runCatching { dao.clearCatalogSnapshot() }
            runCatching { dao.clearMediaDetailsCache() }
            runCatching { dao.clearSeasonEpisodesCache() }
            runCatching { dao.clearStreamCaches() }
            runCatching { clearImageCaches() }
        }
    }

    suspend fun checkGitHubRelease(repo: String): Result<GitHubReleaseInfo> {
        return gitHubUpdateService.checkForUpdates(repo)
    }
}
