package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.VixFlixDao
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.GitHubUpdateService
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.ResolvedTrailerStream
import com.example.data.remote.TmdbApiConfig
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbSeasonDetailsDto
import com.example.data.remote.TmdbTrailerResolver
import com.example.data.remote.TmdbVideoDto
import com.example.data.remote.VixSrcStreamResolver
import com.example.data.remote.YouTubeTrailerCandidate
import com.example.data.remote.toDomain
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

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
    private val dao: VixFlixDao
) {
    private val rawOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val tmdbDispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 6
    }

    private val tmdbOkHttpClient: OkHttpClient = rawOkHttpClient.newBuilder()
        .dispatcher(tmdbDispatcher)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
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

    private val gitHubDispatcher = Dispatcher().apply {
        maxRequests = 6
        maxRequestsPerHost = 2
    }

    private val gitHubOkHttpClient: OkHttpClient = rawOkHttpClient.newBuilder()
        .dispatcher(gitHubDispatcher)
        .connectionPool(ConnectionPool(2, 3, TimeUnit.MINUTES))
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val tmdbApi: TmdbApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.TMDB_BASE_URL)
        .client(tmdbOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TmdbApiService::class.java)

    val vixSrcResolver = VixSrcStreamResolver(rawOkHttpClient)
    val trailerResolver = TmdbTrailerResolver(rawOkHttpClient, vixSrcResolver)
    val gitHubUpdateService = GitHubUpdateService(gitHubOkHttpClient)

    val continueWatchingFlow: Flow<List<WatchProgressEntity>> = dao.observeContinueWatching()
    val watchlistFlow: Flow<List<WatchlistEntity>> = dao.observeWatchlist()

    fun observeSeasonProgress(tmdbId: Int, seasonNumber: Int): Flow<List<EpisodeProgressEntity>> =
        dao.observeSeasonProgress(tmdbId, seasonNumber)

    /**
     * Carica l'intero catalogo iniziale in parallelo direttamente dalle API live di TMDB.
     * Nessun contenuto di fallback statico viene mai restituito.
     */
    suspend fun fetchLiveCatalogFeed(): Result<CatalogFeed> = runCatching {
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

            val trendingAll = trendingAllDef.await().results.mapNotNull { it.toDomain("movie") }
            val trendingMovies = trendingMoviesDef.await().results.mapNotNull { it.toDomain("movie") }
            val trendingTv = trendingTvDef.await().results.mapNotNull { it.toDomain("tv") }
            val popularMovies = popularMoviesDef.await().results.mapNotNull { it.toDomain("movie") }
            val popularTv = popularTvDef.await().results.mapNotNull { it.toDomain("tv") }
            val nowPlaying = nowPlayingDef.await().results.mapNotNull { it.toDomain("movie") }
            val topRated = topRatedDef.await().results.mapNotNull { it.toDomain("movie") }
            val action = actionDef.await().results.mapNotNull { it.toDomain("movie") }
            val sciFi = sciFiDef.await().results.mapNotNull { it.toDomain("movie") }

            val hero = trendingAll.firstOrNull { it.backdropPath != null }
                ?: popularMovies.firstOrNull()
                ?: throw IllegalStateException("TMDB non ha restituito risultati per il catalogo.")

            CatalogFeed(
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
        }
    }

    suspend fun fetchMediaDetails(mediaItem: MediaItem): Result<MediaDetails> = runCatching {
        if (mediaItem.isSeries) {
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
            val validSeasons = dto.seasons.filter { it.seasonNumber > 0 && it.episodeCount > 0 }
            MediaDetails(
                item = updatedItem,
                tagline = dto.tagline.orEmpty(),
                runtimeMinutes = null,
                genres = dto.genres.map { it.name },
                cast = dto.credits?.cast?.take(12).orEmpty(),
                similar = dto.similar?.results?.mapNotNull { it.toDomain("tv") }?.take(12).orEmpty(),
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
                genres = dto.genres.map { it.name },
                cast = dto.credits?.cast?.take(12).orEmpty(),
                similar = dto.similar?.results?.mapNotNull { it.toDomain("movie") }?.take(12).orEmpty(),
                trailers = prioritizeTrailers(rawVideos)
            )
        }
    }

    private fun prioritizeTrailers(videos: List<TmdbVideoDto>): List<TmdbVideoDto> {
        return videos
            .filter { it.key.isNotBlank() }
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

    suspend fun fetchSeasonEpisodes(tvId: Int, seasonNumber: Int): Result<List<TmdbEpisodeDto>> = runCatching {
        val season: TmdbSeasonDetailsDto = tmdbApi.getTvSeasonDetails(tvId = tvId, seasonNumber = seasonNumber)
        season.episodes
    }

    suspend fun searchTmdb(query: String): Result<List<MediaItem>> = runCatching {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@runCatching emptyList()
        val response = tmdbApi.searchMulti(query = trimmed)
        response.results.mapNotNull { it.toDomain("movie") }
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

    suspend fun resolveVixSrcStream(
        item: MediaItem,
        season: Int?,
        episode: Int?,
        startPositionMs: Long,
        lang: String
    ): Result<ResolvedStream> {
        val startSeconds = (startPositionMs / 1000L).coerceAtLeast(0L)
        return vixSrcResolver.resolveStreamForExoPlayer(
            tmdbId = item.id,
            mediaType = item.mediaType,
            season = season,
            episode = episode,
            startSeconds = startSeconds,
            lang = lang
        )
    }

    suspend fun checkGitHubRelease(repo: String): Result<GitHubReleaseInfo> {
        return gitHubUpdateService.checkForUpdates(repo)
    }
}
