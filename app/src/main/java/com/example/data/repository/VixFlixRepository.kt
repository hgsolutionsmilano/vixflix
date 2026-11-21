package com.example.data.repository

import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.example.BuildConfig
import com.example.data.local.CacheStatsSummary
import com.example.data.local.CatalogSnapshotEntity
import com.example.data.local.CompletedMediaEntity
import com.example.data.local.CreditsDetectionState
import com.example.data.local.CreditsSegmentEntity
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.IntroSegmentEntity
import com.example.data.local.IntroSegmentSource
import com.example.data.local.MAX_USER_PROFILES
import com.example.data.local.MediaDetailsCacheEntity
import com.example.data.local.PlayerSegmentKeys
import com.example.data.local.ProfileAvatarPreset
import com.example.data.local.ProfileCatalogDefaults
import com.example.data.local.ResolvedStreamCacheEntity
import com.example.data.local.SeasonEpisodesCacheEntity
import com.example.data.local.UserProfileEntity
import com.example.data.local.VerifiedNextEpisode
import com.example.data.local.VixFlixDao
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.ExtendedSearchResult
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
import com.example.data.remote.TmdbPersonSearchDto
import com.example.data.remote.TmdbSeasonDetailsDto
import com.example.data.remote.TmdbSeasonSummaryDto
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
import java.util.UUID
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

    val continueWatchingFlow: Flow<List<WatchProgressEntity>> = dao.observeContinueWatching(DEFAULT_PRIMARY_PROFILE_ID)
    val watchlistFlow: Flow<List<WatchlistEntity>> = dao.observeWatchlist(DEFAULT_PRIMARY_PROFILE_ID)

    // --- User Profiles (Offline Local Persistence) ---
    fun observeProfiles(): Flow<List<UserProfileEntity>> = dao.observeAllProfiles()

    suspend fun getAllProfiles(): List<UserProfileEntity> = withContext(Dispatchers.IO) {
        dao.getAllProfiles()
    }

    suspend fun getProfileById(profileId: String): UserProfileEntity? = withContext(Dispatchers.IO) {
        dao.getProfileById(profileId)
    }

    suspend fun ensureDefaultPrimaryProfile(): UserProfileEntity = withContext(Dispatchers.IO) {
        val existing = dao.getAllProfiles()
        val primary = existing.firstOrNull { it.isPrimary }
            ?: existing.firstOrNull { it.id == DEFAULT_PRIMARY_PROFILE_ID }
        if (primary != null) {
            if (!primary.isPrimary) {
                val fixed = primary.copy(isPrimary = true)
                dao.upsertProfile(fixed)
                return@withContext fixed
            }
            return@withContext primary
        }
        if (existing.isNotEmpty()) {
            val promoted = existing.first().copy(isPrimary = true)
            dao.upsertProfile(promoted)
            return@withContext promoted
        }
        val created = ProfileCatalogDefaults.defaultPrimaryProfile()
        dao.upsertProfile(created)
        created
    }

    suspend fun createProfile(
        name: String,
        avatarId: String,
        accentColorHex: Long,
        preferredLanguage: String = "it",
        preferredAudioLanguage: String = preferredLanguage,
        subtitlesEnabled: Boolean = false,
        preferredSubtitleLanguage: String = preferredLanguage,
        maxProfiles: Int = MAX_USER_PROFILES
    ): Result<UserProfileEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedName = name.trim()
            require(cleanedName.isNotEmpty()) { "Inserisci un nome valido per il profilo." }
            val currentCount = dao.countProfiles()
            check(currentCount < maxProfiles) {
                "Hai raggiunto il limite massimo di $maxProfiles profili."
            }
            val now = System.currentTimeMillis()
            val normalizedLang = if (preferredLanguage.equals("en", ignoreCase = true)) "en" else "it"
            val normalizedAudio = when (preferredAudioLanguage.lowercase()) {
                "en", "original" -> preferredAudioLanguage.lowercase()
                else -> "it"
            }
            val normalizedSub = if (preferredSubtitleLanguage.equals("en", ignoreCase = true)) "en" else "it"
            val profile = UserProfileEntity(
                id = "profile_${UUID.randomUUID()}",
                name = cleanedName.take(24),
                avatarId = ProfileAvatarPreset.fromId(avatarId).id,
                accentColorHex = accentColorHex,
                isPrimary = currentCount == 0,
                preferredLanguage = normalizedLang,
                preferredAudioLanguage = normalizedAudio,
                subtitlesEnabled = subtitlesEnabled,
                preferredSubtitleLanguage = normalizedSub,
                createdAtMs = now,
                updatedAtMs = now
            )
            dao.upsertProfile(profile)
            profile
        }
    }

    suspend fun updateProfile(profile: UserProfileEntity): Result<UserProfileEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedName = profile.name.trim()
            require(cleanedName.isNotEmpty()) { "Il nome del profilo non può essere vuoto." }
            val existing = dao.getProfileById(profile.id)
                ?: throw IllegalArgumentException("Profilo non trovato.")
            val updated = existing.copy(
                name = cleanedName.take(24),
                avatarId = ProfileAvatarPreset.fromId(profile.avatarId).id,
                accentColorHex = profile.accentColorHex,
                preferredLanguage = if (profile.preferredLanguage.equals("en", ignoreCase = true)) "en" else "it",
                preferredAudioLanguage = when (profile.preferredAudioLanguage.lowercase()) {
                    "en", "original" -> profile.preferredAudioLanguage.lowercase()
                    else -> "it"
                },
                subtitlesEnabled = profile.subtitlesEnabled,
                preferredSubtitleLanguage = if (profile.preferredSubtitleLanguage.equals("en", ignoreCase = true)) "en" else "it",
                updatedAtMs = System.currentTimeMillis()
            )
            dao.upsertProfile(updated)
            updated
        }
    }

    suspend fun deleteProfile(profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = dao.getProfileById(profileId)
                ?: throw IllegalArgumentException("Profilo non trovato.")
            check(!target.isPrimary) {
                "Il profilo principale è protetto e non può essere eliminato."
            }
            val total = dao.countProfiles()
            check(total > 1) {
                "Deve rimanere almeno un profilo attivo."
            }
            dao.deleteProfileAndPersonalData(profileId)
        }
    }

    // --- Profile-Scoped Personal Data ---
    fun observeContinueWatching(profileId: String = DEFAULT_PRIMARY_PROFILE_ID): Flow<List<WatchProgressEntity>> =
        dao.observeContinueWatching(profileId)

    fun observeWatchlist(profileId: String = DEFAULT_PRIMARY_PROFILE_ID): Flow<List<WatchlistEntity>> =
        dao.observeWatchlist(profileId)

    fun observeSeasonProgress(
        tmdbId: Int,
        seasonNumber: Int,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): Flow<List<EpisodeProgressEntity>> =
        dao.observeSeasonProgress(tmdbId, seasonNumber, profileId)

    suspend fun getWatchProgress(
        id: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): WatchProgressEntity? = dao.getWatchProgress(id, profileId)

    suspend fun getEpisodeProgress(
        episodeKey: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): EpisodeProgressEntity? = dao.getEpisodeProgress(episodeKey, profileId)

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

    suspend fun searchTmdb(query: String): Result<List<MediaItem>> =
        searchTmdbExtended(query).map { it.items }

    /**
     * Ricerca estesa parallela su TMDB per:
     * - Titoli di film e serie TV (`search/multi`)
     * - Attori, registi, sceneggiatori e creatori (`search/person` + `person/{id}/combined_credits` + `known_for`)
     * - Generi cinematografici/TV in italiano, inglese e sinonimi (`discover/movie` + `discover/tv`)
     * - Anno di uscita (`primary_release_year` / `first_air_date_year`, anche combinato es. "azione 2024")
     * - Parole chiave/temi e case di produzione/studi (`search/keyword`, `search/company`)
     */
    suspend fun searchTmdbExtended(query: String): Result<ExtendedSearchResult> = runCatching {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@runCatching ExtendedSearchResult()

        val normalizedQuery = trimmed.lowercase()
        val matchedGenres = TmdbGenreMap.resolveSearchGenres(trimmed)
        val matchedYear = Regex("\\b(19[5-9]\\d|20[0-3]\\d)\\b")
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        // Rimuove eventuali generi e anno riconosciuti per capire se la query è puramente filtro (es. "horror" o "azione 2024")
        val residualTokens = normalizedQuery
            .split(Regex("[\\s,;:/]+"))
            .filter { token ->
                token.isNotBlank() &&
                    token != matchedYear?.toString() &&
                    matchedGenres.none { it.matchedAlias == token } &&
                    token !in setOf("film", "movie", "movies", "serie", "series", "tv", "genere", "del", "di", "da", "con", "su")
            }
        val isPureFilterQuery = (matchedGenres.isNotEmpty() || matchedYear != null) && residualTokens.isEmpty()
        val shouldSearchPeopleAndThemes = !isPureFilterQuery && trimmed.length >= 2

        val importantCrewJobs = setOf(
            "Director",
            "Creator",
            "Screenplay",
            "Writer",
            "Executive Producer",
            "Producer",
            "Original Music Composer"
        )

        coroutineScope {
            val multiDeferred = async {
                runCatching { tmdbApi.searchMulti(query = trimmed) }.getOrNull()
            }

            val personDeferred = async {
                if (!shouldSearchPeopleAndThemes) {
                    null
                } else {
                    runCatching { tmdbApi.searchPerson(query = trimmed) }.getOrNull()
                }
            }

            val genreYearMoviesDeferred = async {
                if (matchedGenres.isEmpty() && matchedYear == null) {
                    emptyList()
                } else {
                    val movieGenreExpr = matchedGenres
                        .mapNotNull { it.movieGenreId }
                        .distinct()
                        .joinToString(",")
                        .takeIf { it.isNotBlank() }
                    if (movieGenreExpr == null && matchedYear == null) {
                        emptyList()
                    } else {
                        runCatching {
                            tmdbApi.discoverMoviesExtended(
                                genreIds = movieGenreExpr,
                                releaseYear = matchedYear
                            ).results.mapNotNull { it.toDomain("movie") }
                        }.getOrDefault(emptyList())
                    }
                }
            }

            val genreYearTvDeferred = async {
                if (matchedGenres.isEmpty() && matchedYear == null) {
                    emptyList()
                } else {
                    val tvGenreExpr = matchedGenres
                        .mapNotNull { it.tvGenreId }
                        .distinct()
                        .joinToString(",")
                        .takeIf { it.isNotBlank() }
                    if (tvGenreExpr == null && matchedYear == null) {
                        emptyList()
                    } else {
                        runCatching {
                            tmdbApi.discoverTvExtended(
                                genreIds = tvGenreExpr,
                                firstAirDateYear = matchedYear
                            ).results.mapNotNull { it.toDomain("tv") }
                        }.getOrDefault(emptyList())
                    }
                }
            }

            val keywordDeferred = async {
                if (!shouldSearchPeopleAndThemes || trimmed.length < 3) {
                    null
                } else {
                    runCatching { tmdbApi.searchKeywords(query = trimmed) }.getOrNull()
                }
            }

            val companyDeferred = async {
                if (!shouldSearchPeopleAndThemes || trimmed.length < 3) {
                    null
                } else {
                    runCatching { tmdbApi.searchCompanies(query = trimmed) }.getOrNull()
                }
            }

            val multiResponse = multiDeferred.await()
            val titleResults = multiResponse?.results
                ?.mapNotNull { it.toDomain("movie") }
                .orEmpty()

            // Estrae eventuali persone già restituite da search/multi e le unisce a search/person
            val multiPersons = multiResponse?.results
                ?.filter { it.mediaType == "person" && !(it.name.isNullOrBlank()) }
                ?.map {
                    TmdbPersonSearchDto(
                        id = it.id,
                        name = it.name.orEmpty(),
                        knownForDepartment = it.knownForDepartment,
                        popularity = it.popularity,
                        knownFor = it.knownFor.orEmpty()
                    )
                }
                .orEmpty()

            val searchedPersons = personDeferred.await()?.results.orEmpty()
            val candidatePersons = (searchedPersons + multiPersons)
                .filter { it.id > 0 && it.name.isNotBlank() }
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<TmdbPersonSearchDto> { person ->
                        val pName = person.name.lowercase()
                        when {
                            pName == normalizedQuery -> 3
                            pName.startsWith(normalizedQuery) || pName.contains(normalizedQuery) -> 2
                            normalizedQuery.split(" ").filter { it.length >= 3 }.all { pName.contains(it) } -> 1
                            else -> 0
                        }
                    }.thenByDescending { it.popularity ?: 0.0 }
                )
                .take(2)

            val strongPersonMatch = candidatePersons.firstOrNull()?.let { top ->
                val pName = top.name.lowercase()
                pName == normalizedQuery ||
                    pName.contains(normalizedQuery) ||
                    (normalizedQuery.length >= 4 && normalizedQuery.split(" ").filter { it.length >= 3 }.let { parts ->
                        parts.isNotEmpty() && parts.all { pName.contains(it) }
                    })
            } == true

            // Carica la filmografia completa dei top attori/registi trovati in parallelo
            val personCreditsDeferred = candidatePersons.map { person ->
                async {
                    val knownForItems = person.knownFor.mapNotNull { it.toDomain("movie") }
                    val combined = runCatching {
                        tmdbApi.getPersonCombinedCredits(personId = person.id)
                    }.getOrNull()
                    val castItems = combined?.cast.orEmpty()
                        .sortedByDescending { (it.popularity ?: 0.0) + ((it.voteAverage ?: 0.0) * 2.0) }
                        .mapNotNull { it.toDomain("movie") }
                    val crewItems = combined?.crew.orEmpty()
                        .filter { crew ->
                            crew.job == null || crew.job in importantCrewJobs || crew.department == "Directing"
                        }
                        .sortedByDescending { (it.popularity ?: 0.0) + ((it.voteAverage ?: 0.0) * 2.0) }
                        .mapNotNull { it.toDomain("movie") }
                    person to (knownForItems + castItems + crewItems).distinctBy { it.uniqueKey }.take(24)
                }
            }

            // Risolve top parole chiave (temi/argomenti)
            val matchedKeywords = keywordDeferred.await()?.results.orEmpty()
                .filter { kw ->
                    val kwLower = kw.name.lowercase()
                    kwLower == normalizedQuery || kwLower.contains(normalizedQuery) || normalizedQuery.contains(kwLower)
                }
                .take(2)

            val keywordItemsDeferred = async {
                if (matchedKeywords.isEmpty()) {
                    emptyList()
                } else {
                    val kwExpr = matchedKeywords.joinToString("|") { it.id.toString() }
                    val movies = async {
                        runCatching {
                            tmdbApi.discoverMoviesExtended(keywordIds = kwExpr).results.mapNotNull { it.toDomain("movie") }
                        }.getOrDefault(emptyList())
                    }
                    val tv = async {
                        runCatching {
                            tmdbApi.discoverTvExtended(keywordIds = kwExpr).results.mapNotNull { it.toDomain("tv") }
                        }.getOrDefault(emptyList())
                    }
                    interleaveMediaLists(movies.await(), tv.await()).take(20)
                }
            }

            // Risolve casa di produzione / studio (es. Pixar, Marvel, Studio Ghibli, A24)
            val matchedCompany = companyDeferred.await()?.results.orEmpty()
                .firstOrNull { company ->
                    val cLower = company.name.lowercase()
                    cLower == normalizedQuery ||
                        cLower.startsWith(normalizedQuery) ||
                        (normalizedQuery.length >= 4 && cLower.contains(normalizedQuery))
                }

            val companyItemsDeferred = async {
                if (matchedCompany == null) {
                    emptyList()
                } else {
                    val cId = matchedCompany.id.toString()
                    val movies = async {
                        runCatching {
                            tmdbApi.discoverMoviesExtended(companyIds = cId).results.mapNotNull { it.toDomain("movie") }
                        }.getOrDefault(emptyList())
                    }
                    val tv = async {
                        runCatching {
                            tmdbApi.discoverTvExtended(companyIds = cId).results.mapNotNull { it.toDomain("tv") }
                        }.getOrDefault(emptyList())
                    }
                    interleaveMediaLists(movies.await(), tv.await()).take(20)
                }
            }

            val personPairs = personCreditsDeferred.map { it.await() }
            val personItems = personPairs.flatMap { it.second }
            val matchedPersonNames = personPairs
                .filter { it.second.isNotEmpty() }
                .map { it.first.name }

            val genreYearItems = interleaveMediaLists(
                genreYearMoviesDeferred.await(),
                genreYearTvDeferred.await()
            )
            val keywordItems = keywordItemsDeferred.await()
            val companyItems = companyItemsDeferred.await()

            val orderedBuckets: List<List<MediaItem>> = when {
                isPureFilterQuery -> listOf(genreYearItems, titleResults)
                strongPersonMatch && personItems.isNotEmpty() -> listOf(
                    titleResults.filter { it.title.lowercase().contains(normalizedQuery) },
                    personItems,
                    titleResults,
                    genreYearItems,
                    companyItems,
                    keywordItems
                )
                else -> listOf(
                    titleResults,
                    personItems,
                    genreYearItems,
                    companyItems,
                    keywordItems
                )
            }

            val mergedItems = orderedBuckets
                .flatten()
                .distinctBy { it.uniqueKey }
                .take(60)

            val categories = buildList {
                if (titleResults.isNotEmpty() && !isPureFilterQuery) add("Titoli")
                matchedPersonNames.forEach { name -> add("Cast/Regia: $name") }
                if (matchedGenres.isNotEmpty() && genreYearItems.isNotEmpty()) {
                    add("Genere: ${matchedGenres.joinToString(", ") { it.displayName }}")
                }
                if (matchedYear != null && genreYearItems.isNotEmpty()) {
                    add("Anno: $matchedYear")
                }
                if (matchedCompany != null && companyItems.isNotEmpty()) {
                    add("Studio: ${matchedCompany.name}")
                }
                if (matchedKeywords.isNotEmpty() && keywordItems.isNotEmpty()) {
                    add("Tema: ${matchedKeywords.first().name}")
                }
            }

            ExtendedSearchResult(
                items = mergedItems,
                matchedCategories = categories
            )
        }
    }

    private fun interleaveMediaLists(first: List<MediaItem>, second: List<MediaItem>): List<MediaItem> {
        if (first.isEmpty()) return second
        if (second.isEmpty()) return first
        val out = ArrayList<MediaItem>(first.size + second.size)
        val maxLen = maxOf(first.size, second.size)
        for (i in 0 until maxLen) {
            if (i < first.size) out.add(first[i])
            if (i < second.size) out.add(second[i])
        }
        return out
    }

    /**
     * Recupera il timestamp salvato su Room per un contenuto (o episodio specifico)
     * e risolve lo stream su vixsrc.to passando `startAt` (secondi) e `startMinutes` (minuti).
     */
    suspend fun getSavedProgress(
        tmdbId: Int,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): Long {
        if (mediaType == "tv" && season != null && episode != null) {
            val epKey = "tv_${tmdbId}_s${season}_e${episode}"
            val epSaved = dao.getEpisodeProgress(epKey, profileId)
            if (epSaved != null && epSaved.positionMs > 0L) {
                return epSaved.positionMs
            }
        }
        val mainKey = "${mediaType}_${tmdbId}"
        val saved = dao.getWatchProgress(mainKey, profileId) ?: return 0L
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
        durationMs: Long,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID,
        allowAutoComplete: Boolean = true
    ) {
        if (positionMs < 3_000L) return
        val safeDuration = if (durationMs > 0L) durationMs else 5_400_000L // 90m stima se live HLS non espone durata iniziale
        val mainId = "${item.mediaType}_${item.id}"
        val isCompleted = allowAutoComplete && durationMs > 60_000L && positionMs >= (durationMs * 0.96).toLong()

        // Quando un contenuto viene completato (>= 96% e auto-completamento consentito), rimuovilo da "Continua a guardare"
        // (Continua a guardare mostra esclusivamente contenuti interrotti da riprendere, mai uno storico).
        if (isCompleted) {
            dao.deleteWatchProgress(mainId, profileId)
            if (item.mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
                dao.deleteEpisodeProgress(
                    episodeKey = "tv_${item.id}_s${seasonNumber}_e${episodeNumber}",
                    profileId = profileId
                )
            }
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
            updatedAt = System.currentTimeMillis(),
            profileId = profileId
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
                    updatedAt = System.currentTimeMillis(),
                    profileId = profileId
                )
            )
        }
    }

    // --- Intro & Credits Segments (Local Room Persistence) ---

    suspend fun getIntroSegment(
        seriesTmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ): IntroSegmentEntity? = withContext(Dispatchers.IO) {
        dao.getIntroSegment(PlayerSegmentKeys.introEpisodeKey(seriesTmdbId, seasonNumber, episodeNumber))
    }

    suspend fun saveIntroSegment(
        seriesTmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        introStartMs: Long,
        introEndMs: Long,
        source: IntroSegmentSource = IntroSegmentSource.LOCAL_DB
    ) = withContext(Dispatchers.IO) {
        if (introStartMs < 0L || introEndMs <= introStartMs + 3_000L) return@withContext
        dao.upsertIntroSegment(
            IntroSegmentEntity(
                episodeKey = PlayerSegmentKeys.introEpisodeKey(seriesTmdbId, seasonNumber, episodeNumber),
                seriesTmdbId = seriesTmdbId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                introStartMs = introStartMs,
                introEndMs = introEndMs,
                source = source.storageKey,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun getCreditsSegment(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): CreditsSegmentEntity? = withContext(Dispatchers.IO) {
        dao.getCreditsSegment(PlayerSegmentKeys.creditsContentKey(tmdbId, mediaType, seasonNumber, episodeNumber))
    }

    suspend fun saveCreditsSegment(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        creditsStartMs: Long,
        durationMs: Long = 0L,
        detectionState: CreditsDetectionState = CreditsDetectionState.LOCAL_DB
    ) = withContext(Dispatchers.IO) {
        if (creditsStartMs <= 0L) return@withContext
        dao.upsertCreditsSegment(
            CreditsSegmentEntity(
                contentKey = PlayerSegmentKeys.creditsContentKey(tmdbId, mediaType, seasonNumber, episodeNumber),
                tmdbId = tmdbId,
                mediaType = mediaType,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                creditsStartMs = creditsStartMs,
                durationMs = durationMs,
                detectionState = detectionState.storageKey,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    // --- Per-Profile Completion State & Continue Watching Management ---

    suspend fun markMovieCompleted(
        item: MediaItem,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ) = withContext(Dispatchers.IO) {
        val contentKey = PlayerSegmentKeys.completedMediaKey(item.id, "movie")
        dao.upsertCompletedMedia(
            CompletedMediaEntity(
                profileId = profileId,
                contentKey = contentKey,
                tmdbId = item.id,
                mediaType = "movie",
                seasonNumber = null,
                episodeNumber = null,
                completedAtMs = System.currentTimeMillis()
            )
        )
        dao.deleteWatchProgress(id = "movie_${item.id}", profileId = profileId)
    }

    suspend fun isMediaCompleted(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): Boolean = withContext(Dispatchers.IO) {
        val contentKey = PlayerSegmentKeys.completedMediaKey(tmdbId, mediaType, seasonNumber, episodeNumber)
        dao.getCompletedMedia(contentKey = contentKey, profileId = profileId) != null
    }

    /**
     * Segna l'episodio corrente come completato per il profilo attivo:
     * - rimuove il progresso parziale dell'episodio concluso;
     * - se esiste un episodio successivo verificato, aggiorna "Continua a guardare" puntando al prossimo episodio;
     * - se era l'ultimo episodio della serie, rimuove la serie da "Continua a guardare" per il solo profilo attivo.
     */
    suspend fun markEpisodeCompletedAndAdvanceSeries(
        item: MediaItem,
        completedSeason: Int,
        completedEpisode: Int,
        nextEpisode: VerifiedNextEpisode?,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ) = withContext(Dispatchers.IO) {
        val contentKey = PlayerSegmentKeys.completedMediaKey(
            tmdbId = item.id,
            mediaType = "tv",
            seasonNumber = completedSeason,
            episodeNumber = completedEpisode
        )
        dao.upsertCompletedMedia(
            CompletedMediaEntity(
                profileId = profileId,
                contentKey = contentKey,
                tmdbId = item.id,
                mediaType = "tv",
                seasonNumber = completedSeason,
                episodeNumber = completedEpisode,
                completedAtMs = System.currentTimeMillis()
            )
        )
        dao.deleteEpisodeProgress(
            episodeKey = "tv_${item.id}_s${completedSeason}_e${completedEpisode}",
            profileId = profileId
        )

        val mainId = "tv_${item.id}"
        if (nextEpisode != null) {
            dao.upsertWatchProgress(
                WatchProgressEntity(
                    id = mainId,
                    tmdbId = item.id,
                    mediaType = "tv",
                    title = item.title,
                    overview = item.overview,
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    voteAverage = item.voteAverage,
                    releaseYear = item.releaseYear,
                    seasonNumber = nextEpisode.seasonNumber,
                    episodeNumber = nextEpisode.episodeNumber,
                    episodeTitle = nextEpisode.title,
                    positionMs = 0L,
                    durationMs = 3_600_000L,
                    updatedAt = System.currentTimeMillis(),
                    profileId = profileId
                )
            )
        } else {
            dao.deleteWatchProgress(id = mainId, profileId = profileId)
        }
    }

    /**
     * Verifica in modo rigoroso se esiste realmente un episodio successivo per la serie TV:
     * 1) Cerca il prossimo episodio nella stagione corrente.
     * 2) Se l'episodio corrente è l'ultimo della stagione, controlla le stagioni reali della serie
     *    (escludendo gli speciali S0 se si sta guardando una stagione regolare) e verifica il primo episodio
     *    della stagione successiva valida.
     * 3) Non inventa mai episodi inesistenti: restituisce `null` se la serie non ha un episodio successivo.
     */
    suspend fun resolveVerifiedNextEpisode(
        item: MediaItem,
        currentSeason: Int,
        currentEpisode: Int,
        currentSeasonEpisodes: List<TmdbEpisodeDto> = emptyList(),
        knownSeasons: List<TmdbSeasonSummaryDto> = emptyList()
    ): VerifiedNextEpisode? = withContext(Dispatchers.IO) {
        if (!item.isSeries || currentSeason < 0 || currentEpisode <= 0) return@withContext null

        val episodesInSeason = currentSeasonEpisodes.ifEmpty {
            fetchSeasonEpisodes(item.id, currentSeason).getOrDefault(emptyList())
        }.sortedBy { it.episodeNumber }

        // 1. Prossimo episodio reale nella stessa stagione
        val sameSeasonNext = episodesInSeason.firstOrNull { it.episodeNumber > currentEpisode }
        if (sameSeasonNext != null) {
            return@withContext VerifiedNextEpisode(
                seasonNumber = currentSeason,
                episodeNumber = sameSeasonNext.episodeNumber,
                title = sameSeasonNext.name?.takeIf { it.isNotBlank() },
                stillUrl = sameSeasonNext.stillUrl,
                overview = sameSeasonNext.overview?.takeIf { it.isNotBlank() },
                runtimeMinutes = sameSeasonNext.runtime?.takeIf { it in 1..600 },
                isCrossSeason = false
            )
        }

        // 2. Se non ci sono episodi della stagione corrente caricabili, non inventare mai un episodio successivo
        if (episodesInSeason.isEmpty()) return@withContext null

        // 3. Ultimo episodio della stagione corrente -> controlla la stagione successiva reale
        val seasonsList = knownSeasons.ifEmpty {
            fetchMediaDetails(item).getOrNull()?.seasons.orEmpty()
        }
        val candidateNextSeasons = seasonsList
            .filter { season ->
                season.seasonNumber > currentSeason &&
                    (currentSeason == 0 || season.seasonNumber > 0) &&
                    season.episodeCount > 0
            }
            .sortedBy { it.seasonNumber }

        for (candidate in candidateNextSeasons) {
            val nextSeasonEpisodes = fetchSeasonEpisodes(item.id, candidate.seasonNumber)
                .getOrDefault(emptyList())
                .sortedBy { it.episodeNumber }
            val firstEp = nextSeasonEpisodes.firstOrNull { it.episodeNumber > 0 }
            if (firstEp != null) {
                return@withContext VerifiedNextEpisode(
                    seasonNumber = candidate.seasonNumber,
                    episodeNumber = firstEp.episodeNumber,
                    title = firstEp.name?.takeIf { it.isNotBlank() },
                    stillUrl = firstEp.stillUrl,
                    overview = firstEp.overview?.takeIf { it.isNotBlank() },
                    runtimeMinutes = firstEp.runtime?.takeIf { it in 1..600 },
                    isCrossSeason = true
                )
            }
        }

        null
    }

    /**
     * Verifica in modo rigoroso se esiste realmente un episodio precedente per la serie TV:
     * 1) Cerca l'episodio precedente nella stagione corrente.
     * 2) Se l'episodio corrente è il primo della stagione, controlla le stagioni regolari precedenti
     *    e seleziona l'ultimo episodio reale della stagione precedente valida.
     * 3) Non inventa mai episodi inesistenti: restituisce `null` per il primo episodio della serie.
     */
    suspend fun resolveVerifiedPreviousEpisode(
        item: MediaItem,
        currentSeason: Int,
        currentEpisode: Int,
        currentSeasonEpisodes: List<TmdbEpisodeDto> = emptyList(),
        knownSeasons: List<TmdbSeasonSummaryDto> = emptyList()
    ): com.example.data.local.VerifiedPreviousEpisode? = withContext(Dispatchers.IO) {
        if (!item.isSeries || currentSeason < 0 || currentEpisode <= 0) return@withContext null

        val episodesInSeason = currentSeasonEpisodes.ifEmpty {
            fetchSeasonEpisodes(item.id, currentSeason).getOrDefault(emptyList())
        }.sortedBy { it.episodeNumber }

        // 1. Episodio precedente reale nella stessa stagione
        val sameSeasonPrev = episodesInSeason.lastOrNull { it.episodeNumber in 1 until currentEpisode }
        if (sameSeasonPrev != null) {
            return@withContext com.example.data.local.VerifiedPreviousEpisode(
                seasonNumber = currentSeason,
                episodeNumber = sameSeasonPrev.episodeNumber,
                title = sameSeasonPrev.name?.takeIf { it.isNotBlank() },
                stillUrl = sameSeasonPrev.stillUrl,
                overview = sameSeasonPrev.overview?.takeIf { it.isNotBlank() },
                runtimeMinutes = sameSeasonPrev.runtime?.takeIf { it in 1..600 },
                isCrossSeason = false
            )
        }

        if (episodesInSeason.isEmpty() || currentSeason <= 1) return@withContext null

        // 2. Primo episodio della stagione corrente -> controlla la stagione regolare precedente
        val seasonsList = knownSeasons.ifEmpty {
            fetchMediaDetails(item).getOrNull()?.seasons.orEmpty()
        }
        val candidatePrevSeasons = seasonsList
            .filter { season ->
                season.seasonNumber in 1 until currentSeason && season.episodeCount > 0
            }
            .sortedByDescending { it.seasonNumber }

        for (candidate in candidatePrevSeasons) {
            val prevSeasonEpisodes = fetchSeasonEpisodes(item.id, candidate.seasonNumber)
                .getOrDefault(emptyList())
                .sortedBy { it.episodeNumber }
            val lastEp = prevSeasonEpisodes.lastOrNull { it.episodeNumber > 0 }
            if (lastEp != null) {
                return@withContext com.example.data.local.VerifiedPreviousEpisode(
                    seasonNumber = candidate.seasonNumber,
                    episodeNumber = lastEp.episodeNumber,
                    title = lastEp.name?.takeIf { it.isNotBlank() },
                    stillUrl = lastEp.stillUrl,
                    overview = lastEp.overview?.takeIf { it.isNotBlank() },
                    runtimeMinutes = lastEp.runtime?.takeIf { it in 1..600 },
                    isCrossSeason = true
                )
            }
        }

        null
    }

    suspend fun removeContinueWatching(
        id: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ) {
        dao.deleteWatchProgress(id, profileId)
    }

    suspend fun clearAllContinueWatching(profileId: String = DEFAULT_PRIMARY_PROFILE_ID) {
        dao.clearAllWatchProgress(profileId)
    }

    suspend fun toggleWatchlist(
        item: MediaItem,
        currentlyInList: Boolean,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ) {
        val key = item.uniqueKey
        if (currentlyInList) {
            dao.removeFromWatchlist(key, profileId)
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
                    releaseYear = item.releaseYear,
                    profileId = profileId
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
