package com.example.ui

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VixFlixApplication
import com.example.data.cache.BackgroundCachePrefetcher
import com.example.data.local.AppPreferences
import com.example.data.local.CacheStatsSummary
import com.example.data.local.CreditsSegmentEntity
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.IntroSegmentEntity
import com.example.data.local.IntroSegmentSource
import com.example.data.local.MAX_USER_PROFILES
import com.example.data.local.PlayerAdvancedSettings
import com.example.data.local.PlayerComfortSettings
import com.example.data.local.PlayerSegmentKeys
import com.example.data.local.ProfileCatalogDefaults
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.SubtitleBackgroundStyle
import com.example.data.local.SubtitleTextColor
import com.example.data.local.SubtitleTextSize
import com.example.data.local.VideoAspectMode
import com.example.data.local.UserProfileEntity
import com.example.data.local.UserProfileUiState
import com.example.data.local.VerifiedNextEpisode
import com.example.data.local.VerifiedPreviousEpisode
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.local.simkl.SimklLibraryItemEntity
import com.example.data.local.simkl.SimklSecureTokenStore
import com.example.data.remote.ActorDetails
import com.example.data.remote.simkl.SimklAuthFlowMode
import com.example.data.remote.simkl.SimklConfig
import com.example.data.remote.simkl.SimklProfileUiState
import com.example.data.remote.simkl.SimklQrDialogState
import com.example.data.remote.simkl.SimklSyncManager
import com.example.data.remote.simkl.SimklSyncTrigger
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.ResolvedTrailerStream
import com.example.data.remote.TmdbApiConfig
import com.example.data.remote.TmdbCastMemberDto
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbGenreDto
import com.example.data.remote.TmdbGenreMap
import com.example.data.remote.TmdbSeasonSummaryDto
import com.example.data.remote.TmdbWatchProviderDto
import com.example.data.remote.TmdbWatchProviderMap
import com.example.data.remote.YouTubeTrailerCandidate
import com.example.data.repository.CatalogFeed
import com.example.data.repository.VixFlixRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NavTab(val label: String) {
    HOME("Home"),
    SERIES("Serie TV"),
    MOVIES("Film"),
    MY_LIST("La Mia Lista"),
    SETTINGS("Impostazioni")
}

@Immutable
sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val feed: CatalogFeed) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}

@Immutable
data class MovieGenreFilterUiState(
    val genres: List<TmdbGenreDto> = TmdbGenreMap.defaultMovieGenres,
    val selectedGenre: TmdbGenreDto? = null,
    val movies: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

enum class PlatformFilterMediaType(val label: String) {
    ALL("Tutti"),
    MOVIES("Film"),
    SERIES("Serie TV")
}

@Immutable
data class StreamingPlatformUiState(
    val providers: List<TmdbWatchProviderDto> = TmdbWatchProviderMap.defaultProviders,
    val selectedProvider: TmdbWatchProviderDto? = null,
    val mediaTypeFilter: PlatformFilterMediaType = PlatformFilterMediaType.ALL,
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val allTitles: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val displayedTitles: List<MediaItem>
        get() = when (mediaTypeFilter) {
            PlatformFilterMediaType.ALL -> allTitles
            PlatformFilterMediaType.MOVIES -> movies
            PlatformFilterMediaType.SERIES -> series
        }
}

@Immutable
data class TrailerPlaybackState(
    val trailers: List<YouTubeTrailerCandidate> = emptyList(),
    val selectedVideo: YouTubeTrailerCandidate? = null,
    val isSearching: Boolean = false,
    val isLoadingStream: Boolean = false,
    val resolvedTrailer: ResolvedTrailerStream? = null,
    val errorMessage: String? = null
)

@Immutable
data class DetailUiState(
    val mediaItem: MediaItem,
    val isLoading: Boolean = true,
    val details: MediaDetails? = null,
    val selectedSeason: Int = 1,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val episodeProgressMap: Map<Int, EpisodeProgressEntity> = emptyMap(),
    val watchedEpisodeNumbers: Set<Int> = emptySet(),
    val watchedCountsBySeason: Map<Int, Int> = emptyMap(),
    val completedSeasonNumbers: Set<Int> = emptySet(),
    val activeTrailer: TrailerPlaybackState? = null,
    val errorMessage: String? = null,
    val savedScrollIndex: Int = 0,
    val savedScrollOffset: Int = 0,
    val savedFocusedCastActorId: Int? = null,
    val savedFocusedSimilarKey: String? = null,
    val savedFocusedSagaKey: String? = null
)

@Immutable
data class ActorDetailUiState(
    val actorId: Int,
    val name: String,
    val profilePath: String? = null,
    val characterRole: String? = null,
    val isLoading: Boolean = true,
    val details: ActorDetails? = null,
    val errorMessage: String? = null,
    val savedScrollIndex: Int = 0,
    val savedScrollOffset: Int = 0,
    val savedFocusedMediaKey: String? = null
) {
    val resolvedProfileUrl: String?
        get() = details?.profileUrl ?: profilePath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" }
}

@Immutable
sealed interface DetailStackEntry {
    val stackKey: String
    val displayTitle: String

    @Immutable
    data class Media(val state: DetailUiState) : DetailStackEntry {
        override val stackKey: String get() = "media_${state.mediaItem.uniqueKey}"
        override val displayTitle: String get() = state.mediaItem.title
    }

    @Immutable
    data class Actor(val state: ActorDetailUiState) : DetailStackEntry {
        override val stackKey: String get() = "actor_${state.actorId}"
        override val displayTitle: String get() = state.details?.name?.ifBlank { state.name } ?: state.name
    }
}

@Immutable
data class PlaybackSession(
    val mediaItem: MediaItem,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val seasonEpisodes: List<TmdbEpisodeDto> = emptyList(),
    val startPositionMs: Long = 0L,
    val isResolvingStream: Boolean = true,
    val resolvedStream: ResolvedStream? = null,
    val forceWebPlayer: Boolean = false,
    val errorMessage: String? = null,
    val introSegment: IntroSegmentEntity? = null,
    val creditsSegment: CreditsSegmentEntity? = null,
    val verifiedNextEpisode: VerifiedNextEpisode? = null,
    val hasCheckedNextEpisode: Boolean = false,
    val verifiedPreviousEpisode: VerifiedPreviousEpisode? = null,
    val hasCheckedPreviousEpisode: Boolean = false,
    val metadataDurationMs: Long? = null,
    val sessionToken: Long = 0L
) {
    val startSeconds: Long get() = (startPositionMs / 1000L).coerceAtLeast(0L)
    val startMinutes: Int get() = (startSeconds / 60L).toInt()
}

@Immutable
data class GitHubUpdateUiState(
    val repoSlug: String = "",
    val isChecking: Boolean = false,
    val releaseInfo: GitHubReleaseInfo? = null,
    val isDownloadingApk: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkPath: String? = null,
    val autoInstallTriggered: Boolean = false,
    val errorMessage: String? = null,
    val showUpdateDialog: Boolean = false,
    val tmdbApiKey: String = TmdbApiConfig.INTEGRATED_TMDB_API_KEY,
    val bundledConfigFilePath: String = TmdbApiConfig.BUNDLED_FILE_PATH,
    val kotlinConfigFilePath: String = TmdbApiConfig.KOTLIN_CONFIG_PATH,
    val bundledConfigJson: String = ""
)

class VixFlixViewModel @JvmOverloads constructor(
    application: Application,
    val repository: VixFlixRepository = buildDefaultRepository(application),
    val simklSyncManager: SimklSyncManager = SimklSyncManager(
        repository = repository,
        tokenStore = SimklSecureTokenStore(application)
    )
) : AndroidViewModel(application) {
    companion object {
        private fun buildDefaultRepository(application: Application): VixFlixRepository {
            val vixApp = application as? VixFlixApplication
            val dao = VixFlixDatabase.getInstance(application).vixFlixDao()
            return if (vixApp != null) {
                VixFlixRepository(
                    dao = dao,
                    baseHttpClient = vixApp.sharedBaseOkHttpClient,
                    httpCacheDir = application.cacheDir
                )
            } else {
                VixFlixRepository(dao = dao, httpCacheDir = application.cacheDir)
            }
        }
    }

    private val database = VixFlixDatabase.getInstance(application)
    private val preferences = AppPreferences(application)
    private val imagePrefetcher = BackgroundCachePrefetcher(application)
    private val memoryTrimCallback: (Int) -> Unit = { level -> repository.trimMemoryCaches(level) }

    private val _selectedTab = MutableStateFlow(NavTab.HOME)
    val selectedTab: StateFlow<NavTab> = _selectedTab.asStateFlow()

    private val _catalogState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val catalogState: StateFlow<CatalogUiState> = _catalogState.asStateFlow()

    // --- Sistema Profili Utente ---
    private val _activeProfileId = MutableStateFlow(DEFAULT_PRIMARY_PROFILE_ID)
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _showProfileSelector = MutableStateFlow(false)
    private val _isProfileManageMode = MutableStateFlow(false)
    private val _mustSelectProfile = MutableStateFlow(false)
    private val _profileErrorMessage = MutableStateFlow<String?>(null)
    private val _profilesInitialized = MutableStateFlow(false)

    val profiles: StateFlow<List<UserProfileEntity>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(ProfileCatalogDefaults.defaultPrimaryProfile()))

    val activeProfile: StateFlow<UserProfileEntity?> = combine(profiles, _activeProfileId) { list, activeId ->
        list.firstOrNull { it.id == activeId } ?: list.firstOrNull { it.isPrimary } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileCatalogDefaults.defaultPrimaryProfile())

    val autoLoginProfile: StateFlow<Boolean> = preferences.autoLoginProfileFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val defaultProfileId: StateFlow<String?> = preferences.defaultProfileIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val profileUiState: StateFlow<UserProfileUiState> = combine(
        combine(profiles, activeProfile, defaultProfileId, autoLoginProfile) { list, active, defId, autoLogin ->
            arrayOf<Any?>(list, active, defId, autoLogin)
        },
        combine(_showProfileSelector, _isProfileManageMode, _mustSelectProfile, _profileErrorMessage, _profilesInitialized) { show, manage, mustSelect, err, init ->
            arrayOf<Any?>(show, manage, mustSelect, err, init)
        }
    ) { core, ui ->
        @Suppress("UNCHECKED_CAST")
        UserProfileUiState(
            profiles = core[0] as List<UserProfileEntity>,
            activeProfile = core[1] as UserProfileEntity?,
            defaultProfileId = core[2] as String?,
            autoLoginEnabled = core[3] as Boolean,
            showProfileSelector = ui[0] as Boolean,
            isManageMode = ui[1] as Boolean,
            mustSelectBeforeClosing = ui[2] as Boolean,
            maxProfiles = MAX_USER_PROFILES,
            errorMessage = ui[3] as String?,
            isInitialized = ui[4] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UserProfileUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueWatching: StateFlow<List<WatchProgressEntity>> = _activeProfileId
        .flatMapLatest { profileId -> repository.observeContinueWatching(profileId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlist: StateFlow<List<WatchlistEntity>> = _activeProfileId
        .flatMapLatest { profileId -> repository.observeWatchlist(profileId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val simklPlanToWatch: StateFlow<List<SimklLibraryItemEntity>> = _activeProfileId
        .flatMapLatest { profileId -> repository.observeSimklPlanToWatch(profileId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val simklLibraryForProfile: StateFlow<List<SimklLibraryItemEntity>> = _activeProfileId
        .flatMapLatest { profileId -> repository.localDao.observeSimklLibraryForProfile(profileId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Mappa reattiva `WatchProgressEntity.id -> "GG MMM"` (o `"GG MMM YYYY"`) per i soli episodi
     * futuri (non ancora trasmessi) delle serie TV in "Continua a guardare".
     * Si aggiorna automaticamente al cambio episodio, al completamento di una serie o dopo
     * qualsiasi sincronizzazione automatica/manuale Simkl o aggiornamento cache TMDB.
     */
    private val _continueWatchingUpcomingDateBadges = MutableStateFlow<Map<String, String>>(emptyMap())
    val continueWatchingUpcomingDateBadges: StateFlow<Map<String, String>> =
        _continueWatchingUpcomingDateBadges.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val simklUiState: StateFlow<SimklProfileUiState> = _activeProfileId
        .flatMapLatest { profileId -> simklSyncManager.observeProfileUiState(profileId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SimklProfileUiState())

    val simklQrDialogState: StateFlow<SimklQrDialogState?> = simklSyncManager.globalQrDialogState

    val streamLanguage: StateFlow<String> = combine(activeProfile, preferences.streamLanguageFlow) { profile, prefLang ->
        profile?.preferredLanguage?.takeIf { it.isNotBlank() } ?: prefLang
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "it")

    val preferWebPlayer: StateFlow<Boolean> = preferences.preferWebPlayerFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val autoCheckUpdates: StateFlow<Boolean> = preferences.autoCheckUpdatesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val bufferPreset: StateFlow<StreamingBufferPreset> = preferences.bufferPresetFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StreamingBufferPreset.DEFAULT)

    val backgroundPrefetchEnabled: StateFlow<Boolean> = preferences.backgroundPrefetchFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val playerAdvancedSettings: StateFlow<PlayerAdvancedSettings> = preferences.playerAdvancedSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerAdvancedSettings())

    val playerComfortSettings: StateFlow<PlayerComfortSettings> = preferences.playerComfortSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerComfortSettings())

    private val _cacheStats = MutableStateFlow(CacheStatsSummary())
    val cacheStats: StateFlow<CacheStatsSummary> = _cacheStats.asStateFlow()

    private val _isBackgroundCatalogRefreshing = MutableStateFlow(false)
    val isBackgroundCatalogRefreshing: StateFlow<Boolean> = _isBackgroundCatalogRefreshing.asStateFlow()

    private val _cacheClearedFeedbackNonce = MutableStateFlow(0)
    val cacheClearedFeedbackNonce: StateFlow<Int> = _cacheClearedFeedbackNonce.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _searchMatchedCategories = MutableStateFlow<List<String>>(emptyList())
    val searchMatchedCategories: StateFlow<List<String>> = _searchMatchedCategories.asStateFlow()

    private val _detailBackStack = MutableStateFlow<List<DetailStackEntry>>(emptyList())

    private val _detailState = MutableStateFlow<DetailUiState?>(null)
    val detailState: StateFlow<DetailUiState?> = _detailState.asStateFlow()

    private val _actorDetailState = MutableStateFlow<ActorDetailUiState?>(null)
    val actorDetailState: StateFlow<ActorDetailUiState?> = _actorDetailState.asStateFlow()

    private val _detailStackSize = MutableStateFlow(0)
    val detailStackSize: StateFlow<Int> = _detailStackSize.asStateFlow()

    private val _previousDetailTitle = MutableStateFlow<String?>(null)
    val previousDetailTitle: StateFlow<String?> = _previousDetailTitle.asStateFlow()

    private val _playbackSession = MutableStateFlow<PlaybackSession?>(null)
    val playbackSession: StateFlow<PlaybackSession?> = _playbackSession.asStateFlow()

    private val _gitHubState = MutableStateFlow(GitHubUpdateUiState())
    val gitHubState: StateFlow<GitHubUpdateUiState> = _gitHubState.asStateFlow()

    private val _movieGenreState = MutableStateFlow(MovieGenreFilterUiState())
    val movieGenreState: StateFlow<MovieGenreFilterUiState> = _movieGenreState.asStateFlow()

    private val _platformState = MutableStateFlow(StreamingPlatformUiState())
    val platformState: StateFlow<StreamingPlatformUiState> = _platformState.asStateFlow()

    private var catalogLoadJob: Job? = null
    private var imagePrefetchJob: Job? = null
    private var searchJob: Job? = null
    private var movieGenreJob: Job? = null
    private var platformLoadJob: Job? = null
    private var seasonProgressJob: Job? = null
    private var seasonLoadJob: Job? = null
    private var trailerJob: Job? = null
    private var detailLoadJob: Job? = null
    private var actorLoadJob: Job? = null
    private var playbackResolveJob: Job? = null
    private var nextEpisodePreResolveJob: Job? = null
    private var lastPreResolvedEpisodeKey: String? = null
    private val livePlaybackPositionMs = AtomicLong(0L)
    private val playbackSessionCounter = AtomicLong(0L)

    private fun updateDetailStack(newStack: List<DetailStackEntry>) {
        _detailBackStack.value = newStack
        val top = newStack.lastOrNull()
        _detailState.value = (top as? DetailStackEntry.Media)?.state
        _actorDetailState.value = (top as? DetailStackEntry.Actor)?.state
        _detailStackSize.value = newStack.size
        _previousDetailTitle.value = if (newStack.size > 1) newStack[newStack.size - 2].displayTitle else null
    }

    private fun updateTopDetail(transform: (DetailUiState) -> DetailUiState) {
        val currentStack = _detailBackStack.value
        val top = currentStack.lastOrNull() as? DetailStackEntry.Media ?: return
        val updated = DetailStackEntry.Media(transform(top.state))
        updateDetailStack(currentStack.dropLast(1) + updated)
    }

    private fun updateTopActorDetail(transform: (ActorDetailUiState) -> ActorDetailUiState) {
        val currentStack = _detailBackStack.value
        val top = currentStack.lastOrNull() as? DetailStackEntry.Actor ?: return
        val updated = DetailStackEntry.Actor(transform(top.state))
        updateDetailStack(currentStack.dropLast(1) + updated)
    }

    private val enrichedWatchProgressKeys = ConcurrentHashMap.newKeySet<String>()

    init {
        (application as? VixFlixApplication)?.registerMemoryTrimListener(memoryTrimCallback)
        initUserProfiles()
        loadCatalog(forceRefresh = false)
        initGitHubUpdateChecker()
        initContinueWatchingDurationEnricher()
        initContinueWatchingUpcomingEpisodeBadgeResolver()
        viewModelScope.launch {
            simklSyncManager.resolvedActiveProfileRequests.collect { resolvedProfileId ->
                selectProfile(resolvedProfileId)
            }
        }
    }

    /**
     * Arricchisce automaticamente in background la durata reale (`durationMs`) delle voci
     * "Continua a guardare" che non dispongono ancora della durata del film o dell'episodio corrente,
     * così da mostrare il badge `<X> MIN RIMANENTI` appena il metadato TMDB è disponibile.
     */
    private fun initContinueWatchingDurationEnricher() {
        viewModelScope.launch(Dispatchers.IO) {
            continueWatching.collect { entries ->
                entries.forEach { entry ->
                    if (entry.durationMs <= 0L) {
                        val enrichKey = "${entry.profileId}_${entry.id}_${entry.seasonNumber ?: 0}_${entry.episodeNumber ?: 0}"
                        if (enrichedWatchProgressKeys.add(enrichKey)) {
                            launch(Dispatchers.IO) {
                                val resolvedDurationMs: Long? = if (entry.mediaType == "tv") {
                                    val s = entry.seasonNumber ?: 1
                                    val e = entry.episodeNumber ?: 1
                                    val eps = repository.getCachedSeasonEpisodes(entry.tmdbId, s)?.value
                                        ?: repository.fetchSeasonEpisodes(entry.tmdbId, s).getOrNull()
                                    eps?.firstOrNull { it.episodeNumber == e }
                                        ?.runtime
                                        ?.takeIf { it in 1..600 }
                                        ?.let { it * 60_000L }
                                } else {
                                    val item = MediaItem(
                                        id = entry.tmdbId,
                                        mediaType = entry.mediaType,
                                        title = entry.title,
                                        overview = entry.overview,
                                        posterPath = entry.posterPath,
                                        backdropPath = entry.backdropPath,
                                        voteAverage = entry.voteAverage,
                                        releaseYear = entry.releaseYear
                                    )
                                    val det = repository.getCachedMediaDetails(item)?.value
                                        ?: repository.fetchMediaDetails(item).getOrNull()
                                    det?.runtimeMinutes
                                        ?.takeIf { it in 1..600 }
                                        ?.let { it * 60_000L }
                                }
                                if (resolvedDurationMs != null && resolvedDurationMs > 0L) {
                                    repository.updateWatchProgressDuration(
                                        id = entry.id,
                                        profileId = entry.profileId,
                                        seasonNumber = entry.seasonNumber,
                                        episodeNumber = entry.episodeNumber,
                                        resolvedDurationMs = resolvedDurationMs
                                    )
                                } else {
                                    enrichedWatchProgressKeys.remove(enrichKey)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Risolve in modo reattivo e su `Dispatchers.IO` la data di uscita del prossimo episodio
     * per tutte le serie TV presenti in "Continua a guardare", mostrando il badge SOLO se
     * l'episodio reale in TMDB ha `airDate` strettamente futura (`airDate > today`).
     */
    private fun initContinueWatchingUpcomingEpisodeBadgeResolver() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                continueWatching,
                simklLibraryForProfile,
                simklSyncManager.syncRevision
            ) { entries, libraryItems, _ ->
                entries to libraryItems
            }.collectLatest { (entries, libraryItems) ->
                val tvEntries = entries.filter { entry ->
                    entry.mediaType.equals("tv", ignoreCase = true) &&
                        (entry.seasonNumber ?: 0) > 0 &&
                        (entry.episodeNumber ?: 0) > 0
                }
                if (tvEntries.isEmpty()) {
                    _continueWatchingUpcomingDateBadges.value = emptyMap()
                    return@collectLatest
                }

                val completedShowTmdbIds = libraryItems
                    .asSequence()
                    .filter {
                        it.tmdbMediaType.equals("tv", ignoreCase = true) &&
                            it.status.equals("completed", ignoreCase = true)
                    }
                    .mapNotNull { it.tmdbId }
                    .toSet()

                val today = CalendarDate.today()
                val resolved = LinkedHashMap<String, String>()
                val seasonCache = HashMap<Pair<Int, Int>, List<TmdbEpisodeDto>>()

                for (entry in tvEntries) {
                    val seasonNum = entry.seasonNumber ?: continue
                    val isCompleted = entry.tmdbId in completedShowTmdbIds
                    if (isCompleted) continue

                    val seasonKey = entry.tmdbId to seasonNum
                    val episodes = seasonCache.getOrPut(seasonKey) {
                        repository.getCachedSeasonEpisodes(entry.tmdbId, seasonNum)?.value
                            ?: repository.fetchSeasonEpisodes(entry.tmdbId, seasonNum).getOrNull().orEmpty()
                    }
                    val badgeText = UpcomingEpisodeBadgeFormatter.resolveUpcomingEpisodeBadgeForEntry(
                        entry = entry,
                        seasonEpisodes = episodes,
                        isSeriesCompleted = false,
                        today = today
                    )
                    if (badgeText != null) {
                        resolved[entry.id] = badgeText
                    }
                }
                _continueWatchingUpcomingDateBadges.value = resolved
            }
        }
    }

    @Volatile
    private var explicitProfileSelectorStateSet = false

    @Volatile
    private var explicitActiveProfileSet = false

    private fun initUserProfiles() {
        viewModelScope.launch {
            val primary = repository.ensureDefaultPrimaryProfile()
            val allProfiles = repository.getAllProfiles()
            val savedAutoLogin = preferences.autoLoginProfileFlow.first()
            val savedDefaultId = preferences.defaultProfileIdFlow.first()
            val savedLastActiveId = preferences.activeProfileIdFlow.first()
            val hasSelectedOnce = preferences.hasSelectedProfileOnceFlow.first()

            if (!explicitActiveProfileSet) {
                val resolvedId = when {
                    !savedDefaultId.isNullOrBlank() && allProfiles.any { it.id == savedDefaultId } -> savedDefaultId
                    allProfiles.any { it.id == savedLastActiveId } -> savedLastActiveId
                    else -> primary.id
                }
                _activeProfileId.value = resolvedId
                allProfiles.firstOrNull { it.id == resolvedId }?.let { resolvedProfile ->
                    preferences.setStreamLanguage(resolvedProfile.preferredLanguage)
                }
            }

            if (!explicitProfileSelectorStateSet) {
                val shouldPromptSelection = !hasSelectedOnce || !savedAutoLogin
                _mustSelectProfile.value = shouldPromptSelection
                _showProfileSelector.value = shouldPromptSelection
                _isProfileManageMode.value = false
            }
            _profilesInitialized.value = true
            simklSyncManager.syncIfNeeded(_activeProfileId.value, SimklSyncTrigger.STARTUP)
            simklSyncManager.updatePeriodicSyncProfileIfForeground(_activeProfileId.value)
        }
    }

    fun openProfileSelector(manageMode: Boolean = false) {
        explicitProfileSelectorStateSet = true
        _profileErrorMessage.value = null
        _isProfileManageMode.value = manageMode
        _mustSelectProfile.value = false
        _showProfileSelector.value = true
    }

    fun closeProfileSelector() {
        if (_mustSelectProfile.value) return
        explicitProfileSelectorStateSet = true
        _profileErrorMessage.value = null
        _isProfileManageMode.value = false
        _showProfileSelector.value = false
    }

    fun setProfileManageMode(manage: Boolean) {
        explicitProfileSelectorStateSet = true
        _profileErrorMessage.value = null
        _isProfileManageMode.value = manage
    }

    fun clearProfileError() {
        _profileErrorMessage.value = null
    }

    /**
     * Cambio profilo immediato in 8 passaggi senza riavvio dell'app:
     * 1. Salva stato/progresso pendente del profilo precedente
     * 2. Aggiorna l'identificativo dell'utente attivo su stato e DataStore
     * 3-5. Ricarica automaticamente preferiti, Continua a guardare e progressi via flatMapLatest
     * 6. Applica le preferenze personali (lingua/audio/sottotitoli)
     * 7. Aggiorna la UI chiudendo il selettore
     * 8. Pulisce dalla memoria temporanea i dati personali del precedente profilo (dettagli/ricerca/player)
     */
    fun selectProfile(profileId: String) {
        explicitActiveProfileSet = true
        viewModelScope.launch {
            val targetProfile = repository.getProfileById(profileId) ?: return@launch
            val previousProfileId = _activeProfileId.value

            // 1. Salva eventuale progresso di riproduzione del profilo precedente
            val activeSession = _playbackSession.value
            val livePos = livePlaybackPositionMs.get()
            if (activeSession != null && livePos >= 3_000L && previousProfileId != targetProfile.id) {
                repository.saveWatchProgress(
                    item = activeSession.mediaItem,
                    seasonNumber = activeSession.seasonNumber,
                    episodeNumber = activeSession.episodeNumber,
                    episodeTitle = activeSession.episodeTitle,
                    positionMs = livePos,
                    durationMs = activeSession.metadataDurationMs ?: 0L,
                    profileId = previousProfileId
                )
            }

            // 8. Elimina dalla memoria temporanea lo stato transitorio del precedente profilo
            if (previousProfileId != targetProfile.id) {
                closePlayer()
                closeDetails()
                clearSearch()
                lastPreResolvedEpisodeKey = null
                livePlaybackPositionMs.set(0L)
            }

            // 2. Aggiorna profilo attivo e memorizza scelta
            _activeProfileId.value = targetProfile.id
            preferences.setActiveProfileId(targetProfile.id)
            preferences.markProfileSelectedOnce()

            // 6. Applica le preferenze personali del nuovo profilo
            preferences.setStreamLanguage(targetProfile.preferredLanguage)

            // 7. Aggiorna UI senza riavviare l'applicazione
            _profileErrorMessage.value = null
            _mustSelectProfile.value = false
            _isProfileManageMode.value = false
            _showProfileSelector.value = false

            if (previousProfileId != targetProfile.id) {
                simklSyncManager.onActiveProfileChanged(previousProfileId, targetProfile.id)
            }
        }
    }

    fun createProfile(
        name: String,
        avatarId: String,
        accentColorHex: Long,
        preferredLanguage: String = "it",
        preferredAudioLanguage: String = preferredLanguage,
        subtitlesEnabled: Boolean = false,
        preferredSubtitleLanguage: String = preferredLanguage,
        onResult: ((Result<UserProfileEntity>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.createProfile(
                name = name,
                avatarId = avatarId,
                accentColorHex = accentColorHex,
                preferredLanguage = preferredLanguage,
                preferredAudioLanguage = preferredAudioLanguage,
                subtitlesEnabled = subtitlesEnabled,
                preferredSubtitleLanguage = preferredSubtitleLanguage
            )
            result.onSuccess {
                _profileErrorMessage.value = null
            }.onFailure { err ->
                _profileErrorMessage.value = err.localizedMessage ?: "Impossibile creare il profilo."
            }
            onResult?.invoke(result)
        }
    }

    fun updateProfile(
        profile: UserProfileEntity,
        onResult: ((Result<UserProfileEntity>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.updateProfile(profile)
            result.onSuccess { updated ->
                _profileErrorMessage.value = null
                if (updated.id == _activeProfileId.value) {
                    preferences.setStreamLanguage(updated.preferredLanguage)
                }
            }.onFailure { err ->
                _profileErrorMessage.value = err.localizedMessage ?: "Impossibile aggiornare il profilo."
            }
            onResult?.invoke(result)
        }
    }

    fun deleteProfile(
        profileId: String,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            if (profileId == _activeProfileId.value) {
                val failure = Result.failure<Unit>(
                    IllegalStateException("Questo profilo è attualmente attivo. Seleziona prima un altro profilo per poterlo eliminare.")
                )
                _profileErrorMessage.value = failure.exceptionOrNull()?.message
                onResult?.invoke(failure)
                return@launch
            }
            val result = repository.deleteProfile(profileId)
            result.onSuccess {
                simklSyncManager.onProfileDeleted(profileId)
                _profileErrorMessage.value = null
                if (defaultProfileId.value == profileId) {
                    preferences.setDefaultProfileId(null)
                }
            }.onFailure { err ->
                _profileErrorMessage.value = err.localizedMessage ?: "Impossibile eliminare il profilo."
            }
            onResult?.invoke(result)
        }
    }

    fun setAutoLoginProfile(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoLoginProfile(enabled)
        }
    }

    fun setDefaultProfileId(profileId: String?) {
        viewModelScope.launch {
            preferences.setDefaultProfileId(profileId)
        }
    }

    fun selectTab(tab: NavTab) {
        _selectedTab.value = tab
        when (tab) {
            NavTab.SETTINGS -> refreshCacheStats()
            NavTab.MY_LIST -> simklSyncManager.syncIfNeeded(_activeProfileId.value, SimklSyncTrigger.CONTINUE_WATCHING_OPEN)
            else -> Unit
        }
    }

    fun onAppForeground() {
        simklSyncManager.startForegroundPeriodicSync(_activeProfileId.value)
    }

    fun onAppBackground() {
        simklSyncManager.pauseForegroundPeriodicSync()
    }

    fun openSimklConnectDialog() {
        simklSyncManager.startDeviceAuthorization(
            profileId = _activeProfileId.value,
            flowMode = SimklAuthFlowMode.LINK_ACTIVE_PROFILE
        )
    }

    fun openSimklAuthForProfileSelector() {
        simklSyncManager.startDeviceAuthorization(
            profileId = SimklConfig.PROFILE_CREATION_PENDING_ID,
            flowMode = SimklAuthFlowMode.AUTHENTICATE_OR_CREATE_PROFILE
        )
    }

    fun retrySimklDeviceFlow() {
        val activeQr = simklSyncManager.globalQrDialogState.value
        if (activeQr?.flowMode == SimklAuthFlowMode.AUTHENTICATE_OR_CREATE_PROFILE) {
            openSimklAuthForProfileSelector()
        } else {
            openSimklConnectDialog()
        }
    }

    fun dismissSimklConnectDialog() {
        val targetId = simklSyncManager.globalQrDialogState.value?.profileId ?: _activeProfileId.value
        simklSyncManager.dismissDeviceAuthorization(targetId)
    }

    fun confirmSimklSwitchToExistingProfile() {
        viewModelScope.launch {
            simklSyncManager.confirmSwitchToExistingSimklProfile()
        }
    }

    fun confirmSimklReplaceExistingAccount() {
        viewModelScope.launch {
            simklSyncManager.confirmReplaceExistingSimklAccount()
        }
    }

    fun syncSimklNow() {
        simklSyncManager.launchProfileSync(_activeProfileId.value, SimklSyncTrigger.MANUAL)
    }

    fun unlinkSimklAccount() {
        val targetProfileId = _activeProfileId.value
        viewModelScope.launch {
            simklSyncManager.unlinkAccount(targetProfileId)
        }
    }

    fun onPlayerScrobbleStart(positionMs: Long, durationMs: Long) {
        val session = _playbackSession.value ?: return
        simklSyncManager.enqueueScrobbleStart(
            profileId = _activeProfileId.value,
            item = session.mediaItem,
            seasonNumber = session.seasonNumber,
            episodeNumber = session.episodeNumber,
            episodeTitle = session.episodeTitle,
            positionMs = positionMs,
            durationMs = durationMs.takeIf { it > 0L } ?: (session.metadataDurationMs ?: 0L)
        )
    }

    fun onPlayerScrobblePause(positionMs: Long, durationMs: Long) {
        val session = _playbackSession.value ?: return
        simklSyncManager.enqueueScrobblePause(
            profileId = _activeProfileId.value,
            item = session.mediaItem,
            seasonNumber = session.seasonNumber,
            episodeNumber = session.episodeNumber,
            episodeTitle = session.episodeTitle,
            positionMs = positionMs,
            durationMs = durationMs.takeIf { it > 0L } ?: (session.metadataDurationMs ?: 0L)
        )
    }

    fun onPlayerScrobbleStop(positionMs: Long, durationMs: Long, isCompleted: Boolean) {
        val session = _playbackSession.value ?: return
        simklSyncManager.enqueueScrobbleStop(
            profileId = _activeProfileId.value,
            item = session.mediaItem,
            seasonNumber = session.seasonNumber,
            episodeNumber = session.episodeNumber,
            episodeTitle = session.episodeTitle,
            positionMs = positionMs,
            durationMs = durationMs.takeIf { it > 0L } ?: (session.metadataDurationMs ?: 0L),
            isExplicitCompletion = isCompleted
        )
    }

    private fun isTvEnvironment(): Boolean {
        val uiMode = getApplication<Application>().resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun scheduleCatalogImagePrefetch(feed: CatalogFeed) {
        if (!backgroundPrefetchEnabled.value || _playbackSession.value != null) return
        imagePrefetchJob?.cancel()
        imagePrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            // Attende la fine dell'animazione di ingresso iniziale per non competere con il primo render UI
            delay(350L)
            imagePrefetcher.prefetchCatalogVisuals(
                feed = feed,
                continueWatching = continueWatching.value,
                watchlist = watchlist.value,
                providers = _platformState.value.providers,
                isTvDevice = isTvEnvironment(),
                shouldContinue = {
                    backgroundPrefetchEnabled.value && _playbackSession.value == null
                }
            )
            if (_selectedTab.value == NavTab.SETTINGS) {
                refreshCacheStats()
            }
        }
    }

    private fun scheduleDetailImagePrefetch(details: MediaDetails, episodes: List<TmdbEpisodeDto>) {
        if (!backgroundPrefetchEnabled.value || _playbackSession.value != null) return
        imagePrefetchJob?.cancel()
        imagePrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250L)
            imagePrefetcher.prefetchDetailVisuals(
                details = details,
                episodes = episodes,
                shouldContinue = {
                    backgroundPrefetchEnabled.value && _playbackSession.value == null
                }
            )
        }
    }

    fun loadCatalog(forceRefresh: Boolean = false) {
        catalogLoadJob?.cancel()
        catalogLoadJob = viewModelScope.launch {
            val cached = if (!forceRefresh) repository.getCachedCatalogFeed() else null
            if (cached != null) {
                _movieGenreState.value = _movieGenreState.value.copy(genres = cached.genres)
                _catalogState.value = CatalogUiState.Success(cached.feed)
                launch { repository.vixSrcResolver.prewarmConnection() }
                launch {
                    val providers = repository.fetchWatchProviders()
                    _platformState.value = _platformState.value.copy(providers = providers)
                }
                scheduleCatalogImagePrefetch(cached.feed)

                if (!cached.isStale) {
                    return@launch
                }
                // Stale-While-Revalidate silenzioso in background senza mostrare spinner né bloccare la UI
                _isBackgroundCatalogRefreshing.value = true
                try {
                    val genresDef = async { repository.fetchMovieGenres() }
                    val providersDef = async { repository.fetchWatchProviders() }
                    val liveFeedDef = async { repository.fetchLiveCatalogFeed() }

                    val genres = genresDef.await()
                    val providers = providersDef.await()
                    _movieGenreState.value = _movieGenreState.value.copy(genres = genres)
                    _platformState.value = _platformState.value.copy(providers = providers)
                    liveFeedDef.await().onSuccess { freshFeed ->
                        val currentSuccess = (_catalogState.value as? CatalogUiState.Success)?.feed
                        if (currentSuccess != freshFeed) {
                            _catalogState.value = CatalogUiState.Success(freshFeed)
                        }
                        scheduleCatalogImagePrefetch(freshFeed)
                    }
                } finally {
                    _isBackgroundCatalogRefreshing.value = false
                }
                return@launch
            }

            if (_catalogState.value !is CatalogUiState.Success) {
                _catalogState.value = CatalogUiState.Loading
            } else {
                _isBackgroundCatalogRefreshing.value = true
            }

            try {
                // Avvio a freddo 100% parallelo: generi, piattaforme e catalogo (in due stadi progressivi)
                // partono simultaneamente senza aggiungere un RTT extra prima del primo contenuto.
                val genresDef = async { repository.fetchMovieGenres() }
                val providersDef = async { repository.fetchWatchProviders() }
                val liveFeedDef = async {
                    repository.fetchLiveCatalogFeed(
                        onPrimaryFeedReady = { primaryFeed ->
                            if (_catalogState.value !is CatalogUiState.Success) {
                                _catalogState.value = CatalogUiState.Success(primaryFeed)
                            }
                        }
                    )
                }

                launch {
                    val genres = genresDef.await()
                    _movieGenreState.value = _movieGenreState.value.copy(genres = genres)
                }
                launch {
                    val providers = providersDef.await()
                    _platformState.value = _platformState.value.copy(providers = providers)
                }

                val result = liveFeedDef.await()
                result.fold(
                    onSuccess = { feed ->
                        _catalogState.value = CatalogUiState.Success(feed)
                        launch { repository.vixSrcResolver.prewarmConnection() }
                        scheduleCatalogImagePrefetch(feed)
                    },
                    onFailure = { error ->
                        if (_catalogState.value !is CatalogUiState.Success) {
                            _catalogState.value = CatalogUiState.Error(
                                error.localizedMessage ?: "Impossibile connettersi alle API di TMDB."
                            )
                        }
                    }
                )
            } finally {
                _isBackgroundCatalogRefreshing.value = false
            }
        }
    }

    fun selectMovieGenre(genre: TmdbGenreDto?) {
        movieGenreJob?.cancel()
        if (genre == null) {
            _movieGenreState.value = _movieGenreState.value.copy(
                selectedGenre = null,
                movies = emptyList(),
                isLoading = false,
                errorMessage = null
            )
            return
        }

        val cached = repository.getCachedMoviesByGenre(genre.id)
        if (!cached.isNullOrEmpty()) {
            _movieGenreState.value = _movieGenreState.value.copy(
                selectedGenre = genre,
                movies = cached,
                isLoading = false,
                errorMessage = null
            )
            return
        }

        _movieGenreState.value = _movieGenreState.value.copy(
            selectedGenre = genre,
            movies = emptyList(),
            isLoading = true,
            errorMessage = null
        )

        movieGenreJob = viewModelScope.launch {
            repository.fetchMoviesByGenre(genre.id).fold(
                onSuccess = { items ->
                    if (_movieGenreState.value.selectedGenre?.id == genre.id) {
                        _movieGenreState.value = _movieGenreState.value.copy(
                            movies = items,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    if (_movieGenreState.value.selectedGenre?.id == genre.id) {
                        _movieGenreState.value = _movieGenreState.value.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Impossibile caricare i film per ${genre.name}."
                        )
                    }
                }
            )
        }
    }

    fun retrySelectedMovieGenre() {
        val current = _movieGenreState.value.selectedGenre ?: return
        repository.invalidateMovieGenreCache(current.id)
        selectMovieGenre(current)
    }

    fun selectStreamingPlatform(provider: TmdbWatchProviderDto?) {
        platformLoadJob?.cancel()
        if (provider == null) {
            _platformState.value = _platformState.value.copy(
                selectedProvider = null,
                mediaTypeFilter = PlatformFilterMediaType.ALL,
                movies = emptyList(),
                series = emptyList(),
                allTitles = emptyList(),
                isLoading = false,
                errorMessage = null
            )
            return
        }

        val cached = repository.getCachedPlatformCatalog(provider.providerId)
        if (cached != null && cached.allCombined.isNotEmpty()) {
            _platformState.value = _platformState.value.copy(
                selectedProvider = provider,
                mediaTypeFilter = PlatformFilterMediaType.ALL,
                movies = cached.movies,
                series = cached.series,
                allTitles = cached.allCombined,
                isLoading = false,
                errorMessage = null
            )
            return
        }

        _platformState.value = _platformState.value.copy(
            selectedProvider = provider,
            mediaTypeFilter = PlatformFilterMediaType.ALL,
            movies = emptyList(),
            series = emptyList(),
            allTitles = emptyList(),
            isLoading = true,
            errorMessage = null
        )

        platformLoadJob = viewModelScope.launch {
            repository.fetchTitlesByWatchProvider(provider).fold(
                onSuccess = { content ->
                    if (_platformState.value.selectedProvider?.providerId == provider.providerId) {
                        _platformState.value = _platformState.value.copy(
                            movies = content.movies,
                            series = content.series,
                            allTitles = content.allCombined,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    if (_platformState.value.selectedProvider?.providerId == provider.providerId) {
                        _platformState.value = _platformState.value.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage
                                ?: "Impossibile caricare i titoli per ${provider.displayName}."
                        )
                    }
                }
            )
        }
    }

    fun selectPlatformMediaTypeFilter(filter: PlatformFilterMediaType) {
        _platformState.value = _platformState.value.copy(mediaTypeFilter = filter)
    }

    fun retrySelectedStreamingPlatform() {
        val current = _platformState.value.selectedProvider ?: return
        repository.invalidatePlatformCatalogCache(current.providerId)
        platformLoadJob?.cancel()
        _platformState.value = _platformState.value.copy(
            isLoading = true,
            errorMessage = null
        )
        platformLoadJob = viewModelScope.launch {
            repository.fetchTitlesByWatchProvider(current, forceRefresh = true).fold(
                onSuccess = { content ->
                    if (_platformState.value.selectedProvider?.providerId == current.providerId) {
                        _platformState.value = _platformState.value.copy(
                            movies = content.movies,
                            series = content.series,
                            allTitles = content.allCombined,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    if (_platformState.value.selectedProvider?.providerId == current.providerId) {
                        _platformState.value = _platformState.value.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage
                                ?: "Impossibile caricare i titoli per ${current.displayName}."
                        )
                    }
                }
            )
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _searchResults.value = emptyList()
            _searchMatchedCategories.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(320)
            repository.searchTmdbExtended(newQuery).fold(
                onSuccess = { extended ->
                    _searchResults.value = extended.items
                    _searchMatchedCategories.value = extended.matchedCategories
                    _isSearching.value = false
                },
                onFailure = {
                    _isSearching.value = false
                }
            )
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchMatchedCategories.value = emptyList()
        _isSearching.value = false
    }

    fun openDetails(item: MediaItem) {
        detailLoadJob?.cancel()
        actorLoadJob?.cancel()
        trailerJob?.cancel()
        seasonProgressJob?.cancel()
        seasonLoadJob?.cancel()

        detailLoadJob = viewModelScope.launch {
            launch { repository.vixSrcResolver.prewarmConnection() }
            simklSyncManager.syncIfNeeded(_activeProfileId.value, SimklSyncTrigger.DETAIL_OPEN)
            // Controlla se abbiamo una stagione salvata in "Continua a guardare" per il profilo attivo
            val savedProgress = repository.getWatchProgress(item.uniqueKey, _activeProfileId.value)
            val initialSeason = savedProgress?.seasonNumber ?: 1
            val cachedEntry = repository.getCachedMediaDetails(item)

            val initialEntry = if (cachedEntry != null) {
                val cachedDetails = cachedEntry.value
                val targetSeason = if (cachedDetails.seasons.any { it.seasonNumber == initialSeason }) {
                    initialSeason
                } else {
                    cachedDetails.seasons.firstOrNull()?.seasonNumber ?: 1
                }
                DetailUiState(
                    mediaItem = cachedDetails.item,
                    isLoading = false,
                    details = cachedDetails,
                    selectedSeason = targetSeason,
                    errorMessage = null
                )
            } else {
                DetailUiState(
                    mediaItem = item,
                    isLoading = true,
                    selectedSeason = initialSeason
                )
            }

            val wrappedEntry = DetailStackEntry.Media(initialEntry)
            val currentStack = _detailBackStack.value
            val currentTopMedia = currentStack.lastOrNull() as? DetailStackEntry.Media
            val updatedStack = if (currentTopMedia?.state?.mediaItem?.uniqueKey == item.uniqueKey) {
                currentStack.dropLast(1) + wrappedEntry
            } else {
                (currentStack + wrappedEntry).takeLast(16)
            }
            updateDetailStack(updatedStack)
            if (item.isSeries) {
                observeSeasonProgressForTop(item.id, initialEntry.selectedSeason)
            }

            if (cachedEntry != null) {
                if (cachedEntry.value.item.isSeries) {
                    selectSeason(cachedEntry.value.item.id, initialEntry.selectedSeason)
                } else {
                    scheduleDetailImagePrefetch(cachedEntry.value, emptyList())
                }
                if (!cachedEntry.isStale) {
                    return@launch
                }
            }

            repository.fetchMediaDetails(item).fold(
                onSuccess = { details ->
                    val targetSeason = if (details.seasons.any { it.seasonNumber == initialSeason }) {
                        initialSeason
                    } else {
                        details.seasons.firstOrNull()?.seasonNumber ?: 1
                    }
                    val topMedia = _detailBackStack.value.lastOrNull() as? DetailStackEntry.Media
                    if (topMedia?.state?.mediaItem?.uniqueKey == item.uniqueKey) {
                        val previouslySelectedSeason = topMedia.state.selectedSeason
                        val resolvedSeason = if (details.seasons.any { it.seasonNumber == previouslySelectedSeason }) {
                            previouslySelectedSeason
                        } else {
                            targetSeason
                        }
                        updateTopDetail { top ->
                            top.copy(
                                mediaItem = details.item,
                                isLoading = false,
                                details = details,
                                selectedSeason = resolvedSeason,
                                completedSeasonNumbers = computeCompletedSeasonNumbers(
                                    seasons = details.seasons,
                                    selectedSeason = resolvedSeason,
                                    selectedSeasonEpisodes = top.episodes,
                                    selectedSeasonWatched = top.watchedEpisodeNumbers,
                                    watchedCountsBySeason = top.watchedCountsBySeason
                                ),
                                errorMessage = null
                            )
                        }
                        if (details.item.isSeries && (cachedEntry == null || resolvedSeason != initialEntry.selectedSeason)) {
                            selectSeason(details.item.id, resolvedSeason)
                        } else if (!details.item.isSeries) {
                            scheduleDetailImagePrefetch(details, emptyList())
                        }
                    }
                },
                onFailure = { err ->
                    val topMedia = _detailBackStack.value.lastOrNull() as? DetailStackEntry.Media
                    if (topMedia?.state?.mediaItem?.uniqueKey == item.uniqueKey && cachedEntry == null) {
                        updateTopDetail { top ->
                            top.copy(
                                isLoading = false,
                                errorMessage = err.localizedMessage ?: "Errore caricamento dettagli da TMDB"
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * Apre il dettaglio di un titolo simile preservando lo stato di scorrimento e focus del titolo di origine.
     */
    fun openDetailsFromSimilar(
        item: MediaItem,
        originScrollIndex: Int = 0,
        originScrollOffset: Int = 0
    ) {
        updateTopDetail { top ->
            top.copy(
                savedScrollIndex = originScrollIndex,
                savedScrollOffset = originScrollOffset,
                savedFocusedCastActorId = null,
                savedFocusedSimilarKey = item.uniqueKey,
                savedFocusedSagaKey = null
            )
        }
        openDetails(item)
    }

    /**
     * Apre il dettaglio di un film appartenente alla stessa saga TMDB preservando posizione di scorrimento
     * e focus nella sezione "FILM DELLA STESSA SAGA" per il ritorno con BACK.
     */
    fun openDetailsFromSaga(
        item: MediaItem,
        originScrollIndex: Int = 0,
        originScrollOffset: Int = 0
    ) {
        updateTopDetail { top ->
            top.copy(
                savedScrollIndex = originScrollIndex,
                savedScrollOffset = originScrollOffset,
                savedFocusedCastActorId = null,
                savedFocusedSimilarKey = null,
                savedFocusedSagaKey = item.uniqueKey
            )
        }
        openDetails(item)
    }

    /**
     * Apre la schermata dedicata all'attore selezionato dal Cast preservando posizione di scorrimento
     * e focus esatto sull'attore nello stack del film/serie TV di origine.
     */
    fun openActorDetails(
        actor: TmdbCastMemberDto,
        originScrollIndex: Int = 0,
        originScrollOffset: Int = 0
    ) {
        detailLoadJob?.cancel()
        actorLoadJob?.cancel()
        trailerJob?.cancel()
        seasonProgressJob?.cancel()
        seasonLoadJob?.cancel()

        // Salva posizione di scorrimento e attore selezionato sullo stack Media sottostante per ripristino al BACK
        updateTopDetail { top ->
            top.copy(
                savedScrollIndex = originScrollIndex,
                savedScrollOffset = originScrollOffset,
                savedFocusedCastActorId = actor.id,
                savedFocusedSimilarKey = null,
                savedFocusedSagaKey = null
            )
        }

        val cached = repository.getCachedActorDetails(actor.id)
        val initialActorState = if (cached != null) {
            ActorDetailUiState(
                actorId = actor.id,
                name = cached.value.name.ifBlank { actor.name },
                profilePath = cached.value.profilePath ?: actor.profilePath,
                characterRole = actor.character,
                isLoading = false,
                details = cached.value,
                errorMessage = null
            )
        } else {
            ActorDetailUiState(
                actorId = actor.id,
                name = actor.name,
                profilePath = actor.profilePath,
                characterRole = actor.character,
                isLoading = true,
                details = null,
                errorMessage = null
            )
        }

        val wrappedActor = DetailStackEntry.Actor(initialActorState)
        val currentStack = _detailBackStack.value
        val currentTopActor = currentStack.lastOrNull() as? DetailStackEntry.Actor
        val updatedStack = if (currentTopActor?.state?.actorId == actor.id) {
            currentStack.dropLast(1) + wrappedActor
        } else {
            (currentStack + wrappedActor).takeLast(16)
        }
        updateDetailStack(updatedStack)

        if (cached != null && !cached.isStale) {
            return
        }

        actorLoadJob = viewModelScope.launch {
            repository.fetchActorDetails(
                actorId = actor.id,
                fallbackName = actor.name,
                fallbackProfilePath = actor.profilePath
            ).fold(
                onSuccess = { actorDetails ->
                    val topActor = _detailBackStack.value.lastOrNull() as? DetailStackEntry.Actor
                    if (topActor?.state?.actorId == actor.id) {
                        updateTopActorDetail { top ->
                            top.copy(
                                name = actorDetails.name.ifBlank { top.name },
                                profilePath = actorDetails.profilePath ?: top.profilePath,
                                isLoading = false,
                                details = actorDetails,
                                errorMessage = null
                            )
                        }
                    }
                },
                onFailure = { err ->
                    val topActor = _detailBackStack.value.lastOrNull() as? DetailStackEntry.Actor
                    if (topActor?.state?.actorId == actor.id && cached == null) {
                        updateTopActorDetail { top ->
                            top.copy(
                                isLoading = false,
                                errorMessage = err.localizedMessage ?: "Impossibile caricare il profilo dell'attore da TMDB."
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * Apre un film o una serie TV dalla filmografia di un attore riutilizzando la schermata di dettaglio esistente,
     * salvando posizione di scorrimento e locandina focalizzata nella schermata attore per il ritorno tramite BACK.
     */
    fun openDetailsFromActor(
        item: MediaItem,
        actorScrollIndex: Int = 0,
        actorScrollOffset: Int = 0
    ) {
        updateTopActorDetail { top ->
            top.copy(
                savedScrollIndex = actorScrollIndex,
                savedScrollOffset = actorScrollOffset,
                savedFocusedMediaKey = item.uniqueKey
            )
        }
        openDetails(item)
    }

    fun retryActorDetails() {
        val currentActor = _actorDetailState.value ?: return
        actorLoadJob?.cancel()
        updateTopActorDetail { top ->
            top.copy(isLoading = true, errorMessage = null)
        }
        actorLoadJob = viewModelScope.launch {
            repository.fetchActorDetails(
                actorId = currentActor.actorId,
                fallbackName = currentActor.name,
                fallbackProfilePath = currentActor.profilePath
            ).fold(
                onSuccess = { actorDetails ->
                    val topActor = _detailBackStack.value.lastOrNull() as? DetailStackEntry.Actor
                    if (topActor?.state?.actorId == currentActor.actorId) {
                        updateTopActorDetail { top ->
                            top.copy(
                                name = actorDetails.name.ifBlank { top.name },
                                profilePath = actorDetails.profilePath ?: top.profilePath,
                                isLoading = false,
                                details = actorDetails,
                                errorMessage = null
                            )
                        }
                    }
                },
                onFailure = { err ->
                    val topActor = _detailBackStack.value.lastOrNull() as? DetailStackEntry.Actor
                    if (topActor?.state?.actorId == currentActor.actorId) {
                        updateTopActorDetail { top ->
                            top.copy(
                                isLoading = false,
                                errorMessage = err.localizedMessage ?: "Impossibile caricare il profilo dell'attore da TMDB."
                            )
                        }
                    }
                }
            )
        }
    }

    fun navigateBackDetail() {
        detailLoadJob?.cancel()
        actorLoadJob?.cancel()
        trailerJob?.cancel()
        seasonProgressJob?.cancel()
        seasonLoadJob?.cancel()

        val currentStack = _detailBackStack.value
        if (currentStack.size > 1) {
            val popped = currentStack.dropLast(1)
            updateDetailStack(popped)
            val topMedia = popped.lastOrNull() as? DetailStackEntry.Media
            if (topMedia != null && topMedia.state.mediaItem.isSeries) {
                observeSeasonProgressForTop(topMedia.state.mediaItem.id, topMedia.state.selectedSeason)
            }
        } else {
            closeDetails()
        }
    }

    fun closeDetails() {
        detailLoadJob?.cancel()
        actorLoadJob?.cancel()
        trailerJob?.cancel()
        seasonProgressJob?.cancel()
        seasonLoadJob?.cancel()
        updateDetailStack(emptyList())
    }

    /**
     * Apre il popup trailer ed esegue una ricerca diretta su YouTube (prima Italiano, poi Inglese)
     * popolando la lista multipla direttamente nel modale.
     */
    fun openYouTubeTrailerPopup() {
        val current = _detailState.value ?: return
        trailerJob?.cancel()

        updateTopDetail {
            it.copy(
                activeTrailer = TrailerPlaybackState(
                    isSearching = true,
                    isLoadingStream = false
                )
            )
        }

        trailerJob = viewModelScope.launch {
            repository.searchYouTubeTrailers(current.mediaItem).fold(
                onSuccess = { results ->
                    val latest = _detailState.value ?: return@fold
                    if (latest.mediaItem.id != current.mediaItem.id || latest.activeTrailer == null) return@fold
                    val first = results.firstOrNull()
                    if (first == null) {
                        updateTopDetail { top ->
                            top.copy(
                                activeTrailer = TrailerPlaybackState(
                                    trailers = emptyList(),
                                    isSearching = false,
                                    isLoadingStream = false,
                                    errorMessage = "Nessun trailer trovato su YouTube per \"${current.mediaItem.title}\"."
                                )
                            )
                        }
                    } else {
                        updateTopDetail { top ->
                            top.copy(
                                activeTrailer = TrailerPlaybackState(
                                    trailers = results,
                                    selectedVideo = first,
                                    isSearching = false,
                                    isLoadingStream = true
                                )
                            )
                        }
                        resolveSelectedYouTubeCandidate(current.mediaItem.id, first)
                    }
                },
                onFailure = { err ->
                    val latest = _detailState.value ?: return@fold
                    if (latest.mediaItem.id != current.mediaItem.id || latest.activeTrailer == null) return@fold
                    updateTopDetail { top ->
                        top.copy(
                            activeTrailer = TrailerPlaybackState(
                                trailers = emptyList(),
                                isSearching = false,
                                isLoadingStream = false,
                                errorMessage = err.localizedMessage
                                    ?: "Impossibile cercare i trailer su YouTube in questo momento."
                            )
                        )
                    }
                }
            )
        }
    }

    fun selectYouTubeTrailer(candidate: YouTubeTrailerCandidate) {
        val current = _detailState.value ?: return
        val currentPopup = current.activeTrailer ?: return
        trailerJob?.cancel()

        updateTopDetail { top ->
            top.copy(
                activeTrailer = currentPopup.copy(
                    selectedVideo = candidate,
                    isSearching = false,
                    isLoadingStream = true,
                    resolvedTrailer = null,
                    errorMessage = null
                )
            )
        }

        trailerJob = viewModelScope.launch {
            resolveSelectedYouTubeCandidate(current.mediaItem.id, candidate)
        }
    }

    fun retryCurrentYouTubeTrailer() {
        val current = _detailState.value ?: return
        val currentPopup = current.activeTrailer ?: return
        val selected = currentPopup.selectedVideo
        if (selected != null) {
            selectYouTubeTrailer(selected)
        } else {
            openYouTubeTrailerPopup()
        }
    }

    private suspend fun resolveSelectedYouTubeCandidate(
        mediaId: Int,
        candidate: YouTubeTrailerCandidate
    ) {
        repository.resolveYouTubeCandidateForExoPlayer(candidate).fold(
            onSuccess = { resolved ->
                val latest = _detailState.value ?: return@fold
                val popup = latest.activeTrailer ?: return@fold
                if (latest.mediaItem.id == mediaId && popup.selectedVideo?.videoId == candidate.videoId) {
                    updateTopDetail { top ->
                        top.copy(
                            activeTrailer = popup.copy(
                                isSearching = false,
                                isLoadingStream = false,
                                resolvedTrailer = resolved,
                                errorMessage = null
                            )
                        )
                    }
                }
            },
            onFailure = { err ->
                val latest = _detailState.value ?: return@fold
                val popup = latest.activeTrailer ?: return@fold
                if (latest.mediaItem.id == mediaId && popup.selectedVideo?.videoId == candidate.videoId) {
                    updateTopDetail { top ->
                        top.copy(
                            activeTrailer = popup.copy(
                                isSearching = false,
                                isLoadingStream = false,
                                resolvedTrailer = null,
                                errorMessage = err.localizedMessage
                                    ?: "Impossibile riprodurre questo trailer YouTube."
                            )
                        )
                    }
                }
            }
        )
    }

    fun closeTrailerPlayer() {
        trailerJob?.cancel()
        if (_detailState.value == null) return
        updateTopDetail { top -> top.copy(activeTrailer = null) }
    }

    private fun computeCompletedSeasonNumbers(
        seasons: List<TmdbSeasonSummaryDto>,
        selectedSeason: Int,
        selectedSeasonEpisodes: List<TmdbEpisodeDto>,
        selectedSeasonWatched: Set<Int>,
        watchedCountsBySeason: Map<Int, Int>
    ): Set<Int> {
        if (seasons.isEmpty() && selectedSeasonEpisodes.isEmpty()) return emptySet()
        val completed = linkedSetOf<Int>()
        val validSelectedEpNums = selectedSeasonEpisodes
            .asSequence()
            .filter { it.seasonNumber == selectedSeason && it.episodeNumber > 0 }
            .map { it.episodeNumber }
            .toSet()

        seasons.forEach { season ->
            if (season.seasonNumber == selectedSeason && validSelectedEpNums.isNotEmpty()) {
                if (validSelectedEpNums.all { it in selectedSeasonWatched }) {
                    completed.add(season.seasonNumber)
                }
            } else if (season.episodeCount > 0) {
                val watchedCount = watchedCountsBySeason[season.seasonNumber] ?: 0
                if (watchedCount >= season.episodeCount) {
                    completed.add(season.seasonNumber)
                }
            }
        }
        if (seasons.none { it.seasonNumber == selectedSeason } && validSelectedEpNums.isNotEmpty()) {
            if (validSelectedEpNums.all { it in selectedSeasonWatched }) {
                completed.add(selectedSeason)
            }
        }
        return completed
    }

    private fun observeSeasonProgressForTop(tvId: Int, seasonNumber: Int) {
        seasonProgressJob?.cancel()
        val currentProfileId = _activeProfileId.value
        seasonProgressJob = viewModelScope.launch {
            combine(
                repository.observeSeasonProgress(tvId, seasonNumber, currentProfileId),
                repository.observeWatchedEpisodeNumbers(tvId, seasonNumber, currentProfileId),
                repository.observeWatchedEpisodeCountsBySeason(tvId, currentProfileId)
            ) { progressList, watchedList, countsBySeason ->
                Triple(
                    progressList.associateBy { it.episodeNumber },
                    watchedList.toSet(),
                    countsBySeason
                )
            }.collect { (map, watchedSet, countsBySeason) ->
                if (_detailState.value?.mediaItem?.id == tvId) {
                    updateTopDetail { top ->
                        top.copy(
                            episodeProgressMap = map,
                            watchedEpisodeNumbers = watchedSet,
                            watchedCountsBySeason = countsBySeason,
                            completedSeasonNumbers = computeCompletedSeasonNumbers(
                                seasons = top.details?.seasons.orEmpty(),
                                selectedSeason = top.selectedSeason,
                                selectedSeasonEpisodes = top.episodes,
                                selectedSeasonWatched = watchedSet,
                                watchedCountsBySeason = countsBySeason
                            )
                        )
                    }
                }
            }
        }
    }

    fun markEpisodeAsWatched(seasonNumber: Int, episodeNumber: Int) {
        val currentDetail = _detailState.value ?: return
        val currentMedia = currentDetail.mediaItem
        if (!currentMedia.isSeries) return
        val profileId = _activeProfileId.value
        val currentEpisodes = currentDetail.episodes.filter { it.seasonNumber == seasonNumber }
        val knownSeasons = currentDetail.details?.seasons.orEmpty()
        updateTopDetail { top ->
            val updatedWatched = if (top.selectedSeason == seasonNumber) {
                top.watchedEpisodeNumbers + episodeNumber
            } else {
                top.watchedEpisodeNumbers
            }
            val updatedCounts = top.watchedCountsBySeason.toMutableMap().apply {
                this[seasonNumber] = if (top.selectedSeason == seasonNumber) {
                    updatedWatched.size
                } else {
                    (this[seasonNumber] ?: 0) + 1
                }
            }
            top.copy(
                watchedEpisodeNumbers = updatedWatched,
                watchedCountsBySeason = updatedCounts,
                completedSeasonNumbers = computeCompletedSeasonNumbers(
                    seasons = top.details?.seasons.orEmpty(),
                    selectedSeason = top.selectedSeason,
                    selectedSeasonEpisodes = top.episodes,
                    selectedSeasonWatched = updatedWatched,
                    watchedCountsBySeason = updatedCounts
                )
            )
        }
        viewModelScope.launch {
            repository.markEpisodeAsWatched(
                tmdbId = currentMedia.id,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                profileId = profileId,
                mediaItem = currentMedia,
                currentSeasonEpisodes = currentEpisodes,
                knownSeasons = knownSeasons
            )
            simklSyncManager.enqueueEpisodesWatched(
                profileId = profileId,
                item = currentMedia,
                seasonNumber = seasonNumber,
                episodeNumbers = listOf(episodeNumber),
                triggerEvent = "manual_mark_episode_watched"
            )
        }
    }

    fun unmarkEpisodeAsWatched(seasonNumber: Int, episodeNumber: Int) {
        val currentDetail = _detailState.value ?: return
        val currentMedia = currentDetail.mediaItem
        if (!currentMedia.isSeries) return
        val profileId = _activeProfileId.value
        val currentEpisodes = currentDetail.episodes.filter { it.seasonNumber == seasonNumber }
        updateTopDetail { top ->
            val updatedWatched = if (top.selectedSeason == seasonNumber) {
                top.watchedEpisodeNumbers - episodeNumber
            } else {
                top.watchedEpisodeNumbers
            }
            val updatedCounts = top.watchedCountsBySeason.toMutableMap().apply {
                val nextCount = if (top.selectedSeason == seasonNumber) {
                    updatedWatched.size
                } else {
                    ((this[seasonNumber] ?: 0) - 1).coerceAtLeast(0)
                }
                if (nextCount <= 0) remove(seasonNumber) else this[seasonNumber] = nextCount
            }
            top.copy(
                watchedEpisodeNumbers = updatedWatched,
                watchedCountsBySeason = updatedCounts,
                completedSeasonNumbers = computeCompletedSeasonNumbers(
                    seasons = top.details?.seasons.orEmpty(),
                    selectedSeason = top.selectedSeason,
                    selectedSeasonEpisodes = top.episodes,
                    selectedSeasonWatched = updatedWatched,
                    watchedCountsBySeason = updatedCounts
                )
            )
        }
        viewModelScope.launch {
            repository.unmarkEpisodeAsWatched(
                tmdbId = currentMedia.id,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                profileId = profileId,
                mediaItem = currentMedia,
                currentSeasonEpisodes = currentEpisodes
            )
            simklSyncManager.enqueueEpisodesUnwatched(
                profileId = profileId,
                item = currentMedia,
                seasonNumber = seasonNumber,
                episodeNumbers = listOf(episodeNumber),
                triggerEvent = "manual_unmark_episode_watched"
            )
        }
    }

    fun markSeasonAsWatched(seasonNumber: Int, episodeNumbers: List<Int>) {
        val currentDetail = _detailState.value ?: return
        val currentMedia = currentDetail.mediaItem
        if (!currentMedia.isSeries) return
        val profileId = _activeProfileId.value
        val currentEpisodes = currentDetail.episodes.filter { it.seasonNumber == seasonNumber }
        val knownSeasons = currentDetail.details?.seasons.orEmpty()
        val resolvedNumbers = episodeNumbers.ifEmpty {
            currentEpisodes.map { it.episodeNumber }
        }.filter { it > 0 }.distinct()
        if (resolvedNumbers.isEmpty()) return
        updateTopDetail { top ->
            val updatedWatched = if (top.selectedSeason == seasonNumber) {
                top.watchedEpisodeNumbers + resolvedNumbers
            } else {
                top.watchedEpisodeNumbers
            }
            val updatedCounts = top.watchedCountsBySeason.toMutableMap().apply {
                this[seasonNumber] = if (top.selectedSeason == seasonNumber) {
                    updatedWatched.size
                } else {
                    maxOf(this[seasonNumber] ?: 0, resolvedNumbers.size)
                }
            }
            top.copy(
                watchedEpisodeNumbers = updatedWatched,
                watchedCountsBySeason = updatedCounts,
                completedSeasonNumbers = computeCompletedSeasonNumbers(
                    seasons = top.details?.seasons.orEmpty(),
                    selectedSeason = top.selectedSeason,
                    selectedSeasonEpisodes = top.episodes,
                    selectedSeasonWatched = updatedWatched,
                    watchedCountsBySeason = updatedCounts
                )
            )
        }
        viewModelScope.launch {
            repository.markSeasonAsWatched(
                tmdbId = currentMedia.id,
                seasonNumber = seasonNumber,
                episodeNumbers = resolvedNumbers,
                profileId = profileId,
                mediaItem = currentMedia,
                currentSeasonEpisodes = currentEpisodes,
                knownSeasons = knownSeasons
            )
            simklSyncManager.enqueueEpisodesWatched(
                profileId = profileId,
                item = currentMedia,
                seasonNumber = seasonNumber,
                episodeNumbers = resolvedNumbers,
                triggerEvent = "manual_mark_season_watched"
            )
        }
    }

    fun selectSeason(tvId: Int, seasonNumber: Int) {
        if (_detailState.value == null) return
        seasonLoadJob?.cancel()
        observeSeasonProgressForTop(tvId, seasonNumber)

        seasonLoadJob = viewModelScope.launch {
            val cachedSeason = repository.getCachedSeasonEpisodes(tvId, seasonNumber)
            if (cachedSeason != null) {
                if (_detailState.value?.mediaItem?.id == tvId) {
                    updateTopDetail { top ->
                        top.copy(
                            selectedSeason = seasonNumber,
                            episodes = cachedSeason.value,
                            isLoadingEpisodes = false,
                            completedSeasonNumbers = computeCompletedSeasonNumbers(
                                seasons = top.details?.seasons.orEmpty(),
                                selectedSeason = seasonNumber,
                                selectedSeasonEpisodes = cachedSeason.value,
                                selectedSeasonWatched = top.watchedEpisodeNumbers,
                                watchedCountsBySeason = top.watchedCountsBySeason
                            )
                        )
                    }
                    _detailState.value?.details?.let { d ->
                        scheduleDetailImagePrefetch(d, cachedSeason.value)
                    }
                }
                if (!cachedSeason.isStale) {
                    return@launch
                }
            } else {
                updateTopDetail { top ->
                    top.copy(
                        selectedSeason = seasonNumber,
                        isLoadingEpisodes = true
                    )
                }
            }

            repository.fetchSeasonEpisodes(tvId, seasonNumber).fold(
                onSuccess = { episodes ->
                    val currentTop = _detailState.value
                    if (currentTop?.mediaItem?.id == tvId && currentTop.selectedSeason == seasonNumber) {
                        updateTopDetail { top ->
                            top.copy(
                                episodes = episodes,
                                isLoadingEpisodes = false,
                                completedSeasonNumbers = computeCompletedSeasonNumbers(
                                    seasons = top.details?.seasons.orEmpty(),
                                    selectedSeason = seasonNumber,
                                    selectedSeasonEpisodes = episodes,
                                    selectedSeasonWatched = top.watchedEpisodeNumbers,
                                    watchedCountsBySeason = top.watchedCountsBySeason
                                )
                            )
                        }
                        currentTop.details?.let { d ->
                            scheduleDetailImagePrefetch(d, episodes)
                        }
                    }
                },
                onFailure = {
                    val currentTop = _detailState.value
                    if (currentTop?.mediaItem?.id == tvId && currentTop.selectedSeason == seasonNumber) {
                        updateTopDetail { top ->
                            top.copy(isLoadingEpisodes = false)
                        }
                    }
                }
            )
        }
    }

    private fun initialSeasonEpisodesMatchTitle(
        item: MediaItem,
        cachedDetailEpisodes: List<TmdbEpisodeDto>,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): String? {
        if (!item.isSeries || seasonNumber == null || episodeNumber == null) return null
        if (cachedDetailEpisodes.firstOrNull()?.seasonNumber != seasonNumber) return null
        return cachedDetailEpisodes.firstOrNull { it.episodeNumber == episodeNumber }?.name
    }

    /**
     * Avvia la riproduzione partendo da un elemento del catalogo o dai dettagli.
     * Se esiste un timestamp salvato su Room, riprende automaticamente da quel minuto/secondo
     * passandolo anche alle API di vixsrc.to (`startAt` & `startMinutes`).
     */
    fun startPlayback(
        item: MediaItem,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        episodeTitle: String? = null,
        explicitStartPositionMs: Long? = null,
        forceStreamRefresh: Boolean = false,
        expectedDurationMs: Long? = null
    ) {
        playbackResolveJob?.cancel()
        nextEpisodePreResolveJob?.cancel()
        lastPreResolvedEpisodeKey = null
        // Sospende subito eventuali prefetch locandine per dedicare il 100% della banda allo streaming video
        imagePrefetchJob?.cancel()

        val requestToken = playbackSessionCounter.incrementAndGet()
        val lang = streamLanguage.value
        val useWebPlayer = preferWebPlayer.value
        val cachedDetail = _detailState.value?.takeIf { it.mediaItem.id == item.id }
        val cachedDetailEpisodes = cachedDetail?.episodes.orEmpty()

        val initialSeason = seasonNumber
        val initialEpisode = episodeNumber
        val initialEpTitle = episodeTitle ?: initialSeasonEpisodesMatchTitle(
            item = item,
            cachedDetailEpisodes = cachedDetailEpisodes,
            seasonNumber = initialSeason,
            episodeNumber = initialEpisode
        )
        val matchedInitialEpisode = if (item.isSeries && initialSeason != null && initialEpisode != null) {
            cachedDetailEpisodes.firstOrNull {
                it.seasonNumber == initialSeason && it.episodeNumber == initialEpisode
            }
        } else {
            null
        }
        val initialMetadataDurationMs = expectedDurationMs?.takeIf { it > 0L }
            ?: matchedInitialEpisode?.runtime?.takeIf { it in 1..600 }?.let { it * 60_000L }
            ?: if (!item.isSeries) {
                cachedDetail?.details?.runtimeMinutes?.takeIf { it in 1..600 }?.let { it * 60_000L }
            } else {
                null
            }
        val initialStartPosMs = explicitStartPositionMs ?: 0L
        livePlaybackPositionMs.set(initialStartPosMs)
        val placeholderEmbedUrl = repository.vixSrcResolver.buildVixSrcApiUrl(
            tmdbId = item.id,
            mediaType = item.mediaType,
            season = initialSeason,
            episode = initialEpisode,
            startSeconds = (initialStartPosMs / 1000L).coerceAtLeast(0L),
            lang = lang
        )

        _playbackSession.value = PlaybackSession(
            mediaItem = item,
            seasonNumber = initialSeason,
            episodeNumber = initialEpisode,
            episodeTitle = initialEpTitle,
            seasonEpisodes = if (item.isSeries && initialSeason != null && cachedDetailEpisodes.firstOrNull()?.seasonNumber == initialSeason) {
                cachedDetailEpisodes
            } else {
                emptyList()
            },
            startPositionMs = initialStartPosMs,
            isResolvingStream = true,
            forceWebPlayer = useWebPlayer,
            resolvedStream = ResolvedStream(
                streamUrl = placeholderEmbedUrl,
                embedUrl = placeholderEmbedUrl,
                startSeconds = (initialStartPosMs / 1000L).coerceAtLeast(0L),
                startMinutes = ((initialStartPosMs / 1000L) / 60L).toInt(),
                isDirectHls = false
            ),
            metadataDurationMs = initialMetadataDurationMs,
            sessionToken = requestToken
        )

        playbackResolveJob = viewModelScope.launch {
            launch { repository.vixSrcResolver.prewarmConnection() }
            val resolvedLang = streamLanguage.value
            val resolvedWebPlayer = preferWebPlayer.value
            var targetSeason = seasonNumber
            var targetEpisode = episodeNumber
            var targetEpTitle = episodeTitle

            val playbackProfileId = _activeProfileId.value
            if (explicitStartPositionMs == null) {
                simklSyncManager.syncBeforePlaybackIfConnected(playbackProfileId)
            }
            if (item.isSeries && (targetSeason == null || targetEpisode == null)) {
                val saved = repository.getWatchProgress(item.uniqueKey, playbackProfileId)
                targetSeason = targetSeason ?: saved?.seasonNumber ?: 1
                targetEpisode = targetEpisode ?: saved?.episodeNumber ?: 1
                if (targetEpTitle.isNullOrBlank()) {
                    targetEpTitle = saved?.episodeTitle
                }
            }

            val resolvedSeason = targetSeason ?: 1
            val initialSeasonEpisodes = if (item.isSeries && cachedDetailEpisodes.firstOrNull()?.seasonNumber == resolvedSeason) {
                cachedDetailEpisodes
            } else {
                emptyList()
            }
            if (item.isSeries && targetEpTitle.isNullOrBlank()) {
                targetEpTitle = initialSeasonEpisodes.firstOrNull { it.episodeNumber == targetEpisode }?.name
            }

            val startPosMs = explicitStartPositionMs ?: repository.getSavedProgress(
                tmdbId = item.id,
                mediaType = item.mediaType,
                season = targetSeason,
                episode = targetEpisode,
                profileId = playbackProfileId
            )
            livePlaybackPositionMs.set(startPosMs)

            val initialEmbedUrl = repository.vixSrcResolver.buildVixSrcApiUrl(
                tmdbId = item.id,
                mediaType = item.mediaType,
                season = targetSeason,
                episode = targetEpisode,
                startSeconds = (startPosMs / 1000L).coerceAtLeast(0L),
                lang = resolvedLang
            )

            val currentBeforeResolve = _playbackSession.value
            if (currentBeforeResolve == null || currentBeforeResolve.sessionToken != requestToken) {
                return@launch
            }

            val matchedResolvedEp = if (item.isSeries && targetEpisode != null) {
                initialSeasonEpisodes.firstOrNull { it.episodeNumber == targetEpisode }
            } else {
                null
            }
            val updatedMetaDurationMs = currentBeforeResolve.metadataDurationMs
                ?: matchedResolvedEp?.runtime?.takeIf { it in 1..600 }?.let { it * 60_000L }

            _playbackSession.value = currentBeforeResolve.copy(
                seasonNumber = targetSeason,
                episodeNumber = targetEpisode,
                episodeTitle = targetEpTitle,
                seasonEpisodes = initialSeasonEpisodes,
                startPositionMs = startPosMs,
                isResolvingStream = true,
                forceWebPlayer = resolvedWebPlayer,
                resolvedStream = ResolvedStream(
                    streamUrl = initialEmbedUrl,
                    embedUrl = initialEmbedUrl,
                    startSeconds = (startPosMs / 1000L).coerceAtLeast(0L),
                    startMinutes = ((startPosMs / 1000L) / 60L).toInt(),
                    isDirectHls = false
                ),
                metadataDurationMs = updatedMetaDurationMs
            )
            simklSyncManager.setActivePlaybackTarget(
                profileId = playbackProfileId,
                mediaType = item.mediaType,
                tmdbId = item.id,
                seasonNumber = targetSeason,
                episodeNumber = targetEpisode
            )

            // Salva subito l'ingresso in "Continua a guardare" per il profilo attivo se parte da un punto salvato
            if (startPosMs > 0L) {
                launch {
                    repository.saveWatchProgress(
                        item = item,
                        seasonNumber = targetSeason,
                        episodeNumber = targetEpisode,
                        episodeTitle = targetEpTitle,
                        positionMs = startPosMs,
                        durationMs = updatedMetaDurationMs ?: 0L,
                        profileId = playbackProfileId
                    )
                }
            }

            val cachedDetailSeasons = _detailState.value
                ?.takeIf { it.mediaItem.id == item.id }
                ?.details
                ?.seasons
                .orEmpty()

            // Carica in parallelo eventuali marker affidabili di intro e titoli di coda dal DB locale
            launch {
                val intro = if (item.isSeries && targetSeason != null && targetEpisode != null) {
                    repository.getIntroSegment(item.id, targetSeason, targetEpisode)
                } else {
                    null
                }
                val credits = repository.getCreditsSegment(
                    tmdbId = item.id,
                    mediaType = item.mediaType,
                    seasonNumber = targetSeason,
                    episodeNumber = targetEpisode
                )
                val active = _playbackSession.value
                if (active != null && active.sessionToken == requestToken) {
                    _playbackSession.value = active.copy(
                        introSegment = intro,
                        creditsSegment = credits
                    )
                }
            }

            // Recupera episodi della stagione e verifica sia l'episodio precedente sia quello successivo reale (inclusi cambi stagione)
            if (item.isSeries && targetSeason != null && targetEpisode != null) {
                launch {
                    val seasonEpisodes = initialSeasonEpisodes.ifEmpty {
                        repository.fetchSeasonEpisodes(item.id, resolvedSeason)
                            .getOrDefault(emptyList())
                    }
                    val verifiedNextDeferred = async {
                        repository.resolveVerifiedNextEpisode(
                            item = item,
                            currentSeason = targetSeason,
                            currentEpisode = targetEpisode,
                            currentSeasonEpisodes = seasonEpisodes,
                            knownSeasons = cachedDetailSeasons
                        )
                    }
                    val verifiedPrevDeferred = async {
                        repository.resolveVerifiedPreviousEpisode(
                            item = item,
                            currentSeason = targetSeason,
                            currentEpisode = targetEpisode,
                            currentSeasonEpisodes = seasonEpisodes,
                            knownSeasons = cachedDetailSeasons
                        )
                    }
                    val verifiedNext = verifiedNextDeferred.await()
                    val verifiedPrev = verifiedPrevDeferred.await()
                    val active = _playbackSession.value
                    if (active != null && active.sessionToken == requestToken) {
                        val currentEpDto = seasonEpisodes.firstOrNull { it.episodeNumber == targetEpisode }
                        val resolvedEpName = active.episodeTitle?.takeIf { it.isNotBlank() }
                            ?: currentEpDto?.name
                        val resolvedMetaDurationMs = active.metadataDurationMs
                            ?: currentEpDto?.runtime?.takeIf { it in 1..600 }?.let { it * 60_000L }
                        _playbackSession.value = active.copy(
                            seasonEpisodes = seasonEpisodes.ifEmpty { active.seasonEpisodes },
                            episodeTitle = resolvedEpName,
                            verifiedNextEpisode = verifiedNext,
                            hasCheckedNextEpisode = true,
                            verifiedPreviousEpisode = verifiedPrev,
                            hasCheckedPreviousEpisode = true,
                            metadataDurationMs = resolvedMetaDurationMs
                        )
                        if (resolvedMetaDurationMs != null && resolvedMetaDurationMs > 0L && startPosMs > 0L) {
                            repository.updateWatchProgressDuration(
                                id = item.uniqueKey,
                                profileId = playbackProfileId,
                                seasonNumber = targetSeason,
                                episodeNumber = targetEpisode,
                                resolvedDurationMs = resolvedMetaDurationMs
                            )
                        }
                    }
                }
            }

            val streamDeferred = async {
                repository.resolveVixSrcStream(
                    item = item,
                    season = targetSeason,
                    episode = targetEpisode,
                    startPositionMs = startPosMs,
                    lang = resolvedLang,
                    forceRefresh = forceStreamRefresh
                )
            }

            val resolved = streamDeferred.await()
            val active = _playbackSession.value
            if (active != null && active.sessionToken == requestToken) {
                resolved.fold(
                    onSuccess = { stream ->
                        _playbackSession.value = active.copy(
                            isResolvingStream = false,
                            resolvedStream = stream,
                            errorMessage = null
                        )
                    },
                    onFailure = { err ->
                        _playbackSession.value = active.copy(
                            isResolvingStream = false,
                            errorMessage = err.localizedMessage
                        )
                    }
                )
            }
        }
    }

    /**
     * Avvia la riproduzione direttamente dalla riga "Continua a guardare", passando
     * i minuti/secondi salvati in Room alle API di vixsrc.to e ad ExoPlayer.
     */
    fun resumeFromContinueWatching(entry: WatchProgressEntity) {
        val mediaItem = MediaItem(
            id = entry.tmdbId,
            mediaType = entry.mediaType,
            title = entry.title,
            overview = entry.overview,
            posterPath = entry.posterPath,
            backdropPath = entry.backdropPath,
            voteAverage = entry.voteAverage,
            releaseYear = entry.releaseYear
        )
        startPlayback(
            item = mediaItem,
            seasonNumber = entry.seasonNumber,
            episodeNumber = entry.episodeNumber,
            episodeTitle = entry.episodeTitle,
            explicitStartPositionMs = entry.positionMs
        )
    }

    /**
     * Chiamato quando lo sniffer WebView intercetta dinamicamente il flusso HLS (.m3u8 / masterPlaylist)
     * generato dal runtime JS di vixsrc.to, passandolo al player nativo Media3 ExoPlayer.
     */
    fun onDynamicHlsIntercepted(hlsUrl: String) {
        val current = _playbackSession.value ?: return
        val existingStream = current.resolvedStream
        if (existingStream?.isDirectHls == true && existingStream.streamUrl == hlsUrl) return

        val updatedStream = ResolvedStream(
            streamUrl = hlsUrl,
            embedUrl = existingStream?.embedUrl ?: hlsUrl,
            referer = existingStream?.embedUrl ?: "https://vixsrc.to/",
            startSeconds = current.startSeconds,
            startMinutes = current.startMinutes,
            subtitles = existingStream?.subtitles.orEmpty(),
            isDirectHls = true
        )
        _playbackSession.value = current.copy(
            isResolvingStream = false,
            resolvedStream = updatedStream
        )
        viewModelScope.launch {
            repository.cacheInterceptedHlsStream(
                item = current.mediaItem,
                season = current.seasonNumber,
                episode = current.episodeNumber,
                lang = streamLanguage.value,
                stream = updatedStream
            )
        }
    }

    /**
     * Invalida l'eventuale link stream in cache (ad es. se scaduto anticipatamente con HTTP 403/410)
     * ed esegue immediatamente una nuova risoluzione VixSrc mantenendo la posizione di riproduzione.
     */
    fun invalidateAndRetryCurrentStream() {
        val current = _playbackSession.value ?: return
        val lang = streamLanguage.value
        val resumePos = maxOf(current.startPositionMs, livePlaybackPositionMs.get())
        viewModelScope.launch {
            repository.invalidateCachedStream(
                item = current.mediaItem,
                season = current.seasonNumber,
                episode = current.episodeNumber,
                lang = lang
            )
            startPlayback(
                item = current.mediaItem,
                seasonNumber = current.seasonNumber,
                episodeNumber = current.episodeNumber,
                episodeTitle = current.episodeTitle,
                explicitStartPositionMs = resumePos,
                forceStreamRefresh = true
            )
        }
    }

    fun togglePlayerMode(forceWeb: Boolean) {
        val current = _playbackSession.value ?: return
        _playbackSession.value = current.copy(forceWebPlayer = forceWeb)
    }

    /**
     * Persiste la posizione di visione su Room senza ri-emettere `_playbackSession`
     * ad ogni tick periodico, evitando ricomposizioni della root UI durante lo streaming.
     */
    fun onPlaybackProgressUpdate(
        positionMs: Long,
        durationMs: Long,
        allowAutoComplete: Boolean = true
    ) {
        val session = _playbackSession.value ?: return
        if (positionMs <= 0L) return
        val currentProfileId = _activeProfileId.value
        livePlaybackPositionMs.set(positionMs)
        viewModelScope.launch {
            repository.saveWatchProgress(
                item = session.mediaItem,
                seasonNumber = session.seasonNumber,
                episodeNumber = session.episodeNumber,
                episodeTitle = session.episodeTitle,
                positionMs = positionMs,
                durationMs = durationMs,
                profileId = currentProfileId,
                allowAutoComplete = allowAutoComplete
            )
        }
    }

    /**
     * Pre-risolve silenziosamente lo stream HLS del prossimo episodio reale (anche della stagione successiva)
     * negli ultimi 90 secondi di visione, popolando la cache stream in memoria/Room.
     */
    fun preResolveNextEpisode() {
        val session = _playbackSession.value ?: return
        if (!session.mediaItem.isSeries) return
        val next = session.verifiedNextEpisode ?: return
        val lang = streamLanguage.value
        val key = "${session.mediaItem.id}_${next.seasonNumber}_${next.episodeNumber}_$lang"
        if (lastPreResolvedEpisodeKey == key) return
        lastPreResolvedEpisodeKey = key

        nextEpisodePreResolveJob?.cancel()
        nextEpisodePreResolveJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.resolveVixSrcStream(
                    item = session.mediaItem,
                    season = next.seasonNumber,
                    episode = next.episodeNumber,
                    startPositionMs = 0L,
                    lang = lang,
                    forceRefresh = false
                )
            }
        }
    }

    /**
     * Passa all'episodio successivo reale (stessa stagione o stagione successiva) su conclusione naturale
     * (fine riproduzione o countdown Prossimo Episodio) e aggiorna stato di completamento e Continua a guardare
     * esclusivamente per il profilo attivo.
     */
    fun playNextEpisode() {
        val session = _playbackSession.value ?: return
        if (!session.mediaItem.isSeries) return
        val next = session.verifiedNextEpisode ?: return
        val currentSeason = session.seasonNumber ?: 1
        val currentEp = session.episodeNumber ?: 1
        val profileId = _activeProfileId.value

        viewModelScope.launch {
            repository.markEpisodeCompletedAndAdvanceSeries(
                item = session.mediaItem,
                completedSeason = currentSeason,
                completedEpisode = currentEp,
                nextEpisode = next,
                profileId = profileId
            )
            val dur = session.metadataDurationMs?.takeIf { it > 0L } ?: 60_000L
            simklSyncManager.enqueueScrobbleStop(
                profileId = profileId,
                item = session.mediaItem,
                seasonNumber = currentSeason,
                episodeNumber = currentEp,
                episodeTitle = session.episodeTitle,
                positionMs = dur,
                durationMs = dur,
                isExplicitCompletion = true
            )
        }

        startPlayback(
            item = session.mediaItem,
            seasonNumber = next.seasonNumber,
            episodeNumber = next.episodeNumber,
            episodeTitle = next.title,
            explicitStartPositionMs = 0L,
            expectedDurationMs = next.runtimeMinutes?.takeIf { it in 1..600 }?.let { it * 60_000L }
        )
    }

    /**
     * Cambio manuale all'episodio successivo (pulsante controllo o tasto telecomando MediaNext):
     * segna l'episodio corrente come completato SOLO se ha effettivamente raggiunto la soglia di completamento (>=96%),
     * altrimenti salva il progresso parziale reale e invia uno scrobble stop parziale senza falsi completamenti su Simkl.
     */
    fun playNextEpisodeManual(currentPositionMs: Long, durationMs: Long) {
        val session = _playbackSession.value ?: return
        if (!session.mediaItem.isSeries) return
        val next = session.verifiedNextEpisode ?: return
        val currentSeason = session.seasonNumber ?: 1
        val currentEp = session.episodeNumber ?: 1
        val profileId = _activeProfileId.value
        val resolvedDur = durationMs.takeIf { it > 0L } ?: (session.metadataDurationMs ?: 0L)
        val actuallyCompleted = resolvedDur > 0L && currentPositionMs >= (resolvedDur * 0.96).toLong()

        viewModelScope.launch {
            if (actuallyCompleted) {
                repository.markEpisodeCompletedAndAdvanceSeries(
                    item = session.mediaItem,
                    completedSeason = currentSeason,
                    completedEpisode = currentEp,
                    nextEpisode = next,
                    profileId = profileId
                )
                simklSyncManager.enqueueScrobbleStop(
                    profileId = profileId,
                    item = session.mediaItem,
                    seasonNumber = currentSeason,
                    episodeNumber = currentEp,
                    episodeTitle = session.episodeTitle,
                    positionMs = maxOf(currentPositionMs, resolvedDur),
                    durationMs = resolvedDur,
                    isExplicitCompletion = true
                )
            } else {
                if (currentPositionMs > 0L) {
                    repository.saveWatchProgress(
                        item = session.mediaItem,
                        seasonNumber = currentSeason,
                        episodeNumber = currentEp,
                        episodeTitle = session.episodeTitle,
                        positionMs = currentPositionMs,
                        durationMs = resolvedDur,
                        profileId = profileId,
                        allowAutoComplete = false
                    )
                }
                simklSyncManager.enqueueScrobbleStop(
                    profileId = profileId,
                    item = session.mediaItem,
                    seasonNumber = currentSeason,
                    episodeNumber = currentEp,
                    episodeTitle = session.episodeTitle,
                    positionMs = currentPositionMs,
                    durationMs = resolvedDur,
                    isExplicitCompletion = false
                )
            }
        }

        startPlayback(
            item = session.mediaItem,
            seasonNumber = next.seasonNumber,
            episodeNumber = next.episodeNumber,
            episodeTitle = next.title,
            explicitStartPositionMs = 0L,
            expectedDurationMs = next.runtimeMinutes?.takeIf { it in 1..600 }?.let { it * 60_000L }
        )
    }

    /**
     * Passa all'episodio precedente reale (stessa stagione o ultimo episodio della stagione precedente).
     */
    fun playPreviousEpisode() {
        val session = _playbackSession.value ?: return
        if (!session.mediaItem.isSeries) return
        val prev = session.verifiedPreviousEpisode ?: return

        startPlayback(
            item = session.mediaItem,
            seasonNumber = prev.seasonNumber,
            episodeNumber = prev.episodeNumber,
            episodeTitle = prev.title,
            explicitStartPositionMs = 0L,
            expectedDurationMs = prev.runtimeMinutes?.takeIf { it in 1..600 }?.let { it * 60_000L }
        )
    }

    /**
     * Memorizza localmente nel database Room l'intervallo di Intro (`introStartMs` -> `introEndMs`)
     * per l'episodio della serie TV attualmente in riproduzione e aggiorna immediatamente `playbackSession.introSegment`.
     */
    fun saveCurrentEpisodeIntroSegment(
        introStartMs: Long,
        introEndMs: Long,
        source: IntroSegmentSource = IntroSegmentSource.LOCAL_DB
    ) {
        val session = _playbackSession.value ?: return
        if (!session.mediaItem.isSeries) return
        val season = session.seasonNumber ?: 1
        val episode = session.episodeNumber ?: 1
        if (introStartMs < 0L || introEndMs <= introStartMs + 3_000L) return

        val entity = IntroSegmentEntity(
            episodeKey = PlayerSegmentKeys.introEpisodeKey(session.mediaItem.id, season, episode),
            seriesTmdbId = session.mediaItem.id,
            seasonNumber = season,
            episodeNumber = episode,
            introStartMs = introStartMs,
            introEndMs = introEndMs,
            source = source.storageKey,
            updatedAtMs = System.currentTimeMillis()
        )
        _playbackSession.value = session.copy(introSegment = entity)
        viewModelScope.launch {
            repository.saveIntroSegment(
                seriesTmdbId = session.mediaItem.id,
                seasonNumber = season,
                episodeNumber = episode,
                introStartMs = introStartMs,
                introEndMs = introEndMs,
                source = source
            )
        }
    }

    /**
     * Conferma esplicitamente la conclusione del film in riproduzione:
     * 1) segna il film come completato solo per il profilo attivo;
     * 2) rimuove il film da "Continua a guardare" solo per il profilo attivo;
     * 3) chiude la sessione player tornando alla schermata precedente/dettaglio.
     */
    fun completeCurrentMovieAndClose() {
        val session = _playbackSession.value
        val profileId = _activeProfileId.value
        if (session != null && !session.mediaItem.isSeries) {
            val dur = session.metadataDurationMs ?: 60_000L
            simklSyncManager.enqueueScrobbleStop(
                profileId = profileId,
                item = session.mediaItem,
                seasonNumber = null,
                episodeNumber = null,
                positionMs = dur,
                durationMs = dur,
                isExplicitCompletion = true
            )
            viewModelScope.launch {
                repository.markMovieCompleted(
                    item = session.mediaItem,
                    profileId = profileId
                )
            }
        }
        closePlayer()
    }

    fun closePlayer() {
        playbackResolveJob?.cancel()
        nextEpisodePreResolveJob?.cancel()
        lastPreResolvedEpisodeKey = null
        livePlaybackPositionMs.set(0L)
        simklSyncManager.clearActivePlaybackTarget()
        _playbackSession.value = null
    }

    fun removeContinueWatching(id: String) {
        val currentProfileId = _activeProfileId.value
        viewModelScope.launch {
            val existing = repository.getWatchProgress(id, currentProfileId)
            repository.removeContinueWatching(id, currentProfileId)
            if (existing != null) {
                simklSyncManager.onContinueWatchingRemoved(
                    profileId = currentProfileId,
                    tmdbId = existing.tmdbId,
                    mediaType = existing.mediaType,
                    seasonNumber = existing.seasonNumber,
                    episodeNumber = existing.episodeNumber
                )
            }
        }
    }

    fun clearAllContinueWatching() {
        val currentProfileId = _activeProfileId.value
        viewModelScope.launch {
            repository.clearAllContinueWatching(currentProfileId)
        }
    }

    fun toggleWatchlist(item: MediaItem) {
        val currentProfileId = _activeProfileId.value
        viewModelScope.launch {
            val isInList = watchlist.value.any { it.id == item.uniqueKey }
            repository.toggleWatchlist(item, isInList, currentProfileId)
            if (isInList) {
                simklSyncManager.enqueueRemoveFromPlanToWatch(
                    profileId = currentProfileId,
                    item = item
                )
            } else {
                simklSyncManager.enqueueAddToList(
                    profileId = currentProfileId,
                    item = item,
                    listStatus = "plantowatch"
                )
            }
        }
    }

    fun setStreamLanguage(lang: String) {
        viewModelScope.launch {
            preferences.setStreamLanguage(lang)
            val currentProfile = activeProfile.value
            if (currentProfile != null && currentProfile.preferredLanguage != lang) {
                repository.updateProfile(
                    currentProfile.copy(
                        preferredLanguage = lang,
                        preferredAudioLanguage = if (currentProfile.preferredAudioLanguage == "original") "original" else lang
                    )
                )
            }
            val currentSession = _playbackSession.value
            if (currentSession != null) {
                val resumePos = maxOf(currentSession.startPositionMs, livePlaybackPositionMs.get())
                startPlayback(
                    item = currentSession.mediaItem,
                    seasonNumber = currentSession.seasonNumber,
                    episodeNumber = currentSession.episodeNumber,
                    episodeTitle = currentSession.episodeTitle,
                    explicitStartPositionMs = resumePos
                )
            }
        }
    }

    fun setPreferWebPlayer(preferWeb: Boolean) {
        viewModelScope.launch {
            preferences.setPreferWebPlayer(preferWeb)
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoCheckUpdates(enabled)
        }
    }

    fun setBufferPreset(preset: StreamingBufferPreset) {
        viewModelScope.launch {
            preferences.setBufferPreset(preset)
        }
    }

    fun setSkipIntroEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSkipIntroEnabled(enabled)
        }
    }

    fun setNextEpisodePromptEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNextEpisodePromptEnabled(enabled)
        }
    }

    fun setAutoPlayNextEpisodeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoPlayNextEpisodeEnabled(enabled)
        }
    }

    fun setNextEpisodeCountdownSec(seconds: Int) {
        viewModelScope.launch {
            preferences.setNextEpisodeCountdownSec(seconds)
        }
    }

    fun setCreditsDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setCreditsDetectionEnabled(enabled)
        }
    }

    fun setManualMovieCompletionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setManualMovieCompletionEnabled(enabled)
        }
    }

    fun setVideoAspectMode(mode: VideoAspectMode) {
        viewModelScope.launch {
            preferences.setVideoAspectMode(mode)
        }
    }

    fun setSubtitleTextSize(size: SubtitleTextSize) {
        viewModelScope.launch {
            preferences.setSubtitleTextSize(size)
        }
    }

    fun setSubtitleTextColor(color: SubtitleTextColor) {
        viewModelScope.launch {
            preferences.setSubtitleTextColor(color)
        }
    }

    fun setSubtitleBackgroundStyle(style: SubtitleBackgroundStyle) {
        viewModelScope.launch {
            preferences.setSubtitleBackgroundStyle(style)
        }
    }

    fun setBackgroundPrefetchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBackgroundPrefetch(enabled)
            if (!enabled) {
                imagePrefetchJob?.cancel()
            } else {
                (_catalogState.value as? CatalogUiState.Success)?.feed?.let { feed ->
                    scheduleCatalogImagePrefetch(feed)
                }
            }
        }
    }

    fun refreshCacheStats() {
        viewModelScope.launch {
            val imageBytes = withContext(Dispatchers.IO) {
                imagePrefetcher.estimateImageDiskCacheBytes()
            }
            _cacheStats.value = repository.getCacheStatsSummary(imageBytes)
        }
    }

    fun clearLocalCache() {
        imagePrefetchJob?.cancel()
        viewModelScope.launch {
            repository.clearAllLocalCaches {
                imagePrefetcher.clearCoilCaches()
            }
            _cacheClearedFeedbackNonce.value += 1
            refreshCacheStats()
        }
    }

    private fun initGitHubUpdateChecker() {
        val rawPreview = TmdbApiConfig.readBundledJsonPreview(getApplication())
        _gitHubState.value = _gitHubState.value.copy(
            tmdbApiKey = TmdbApiConfig.currentApiKey,
            bundledConfigJson = rawPreview
        )
        viewModelScope.launch {
            val savedTmdbKey = preferences.tmdbApiKeyFlow.first()
            TmdbApiConfig.updateApiKey(savedTmdbKey)
            val savedRepo = preferences.githubRepoFlow.first()
            val shouldAutoCheck = preferences.autoCheckUpdatesFlow.first()
            _gitHubState.value = _gitHubState.value.copy(
                repoSlug = savedRepo,
                tmdbApiKey = TmdbApiConfig.currentApiKey,
                bundledConfigJson = rawPreview
            )
            if (shouldAutoCheck && savedRepo.isNotBlank()) {
                checkForGitHubUpdates(savedRepo, showPopupIfAvailable = true)
            }
        }
    }

    fun updateTmdbApiKey(newKey: String) {
        val cleaned = newKey.trim().ifBlank { TmdbApiConfig.INTEGRATED_TMDB_API_KEY }
        TmdbApiConfig.updateApiKey(cleaned)
        _gitHubState.value = _gitHubState.value.copy(tmdbApiKey = cleaned)
        viewModelScope.launch {
            preferences.setTmdbApiKey(cleaned)
            loadCatalog(forceRefresh = true)
        }
    }

    fun resetTmdbApiKeyToDefault() {
        val defaultKey = TmdbApiConfig.resetToDefault()
        _gitHubState.value = _gitHubState.value.copy(tmdbApiKey = defaultKey)
        viewModelScope.launch {
            preferences.setTmdbApiKey(defaultKey)
            loadCatalog(forceRefresh = true)
        }
    }

    fun updateGitHubRepoSlug(newSlug: String) {
        val cleaned = newSlug.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        _gitHubState.value = _gitHubState.value.copy(repoSlug = cleaned, errorMessage = null)
        viewModelScope.launch {
            preferences.setGithubRepo(cleaned)
        }
    }

    private var apkDownloadJob: Job? = null

    fun checkForGitHubUpdates(repoSlug: String = _gitHubState.value.repoSlug, showPopupIfAvailable: Boolean = true) {
        val cleaned = repoSlug.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        if (cleaned.isBlank()) {
            _gitHubState.value = _gitHubState.value.copy(
                errorMessage = "Inserisci il repository GitHub nel formato owner/repo (es. tuo-username/vixflix)."
            )
            return
        }
        viewModelScope.launch {
            preferences.setGithubRepo(cleaned)
            _gitHubState.value = _gitHubState.value.copy(
                repoSlug = cleaned,
                isChecking = true,
                errorMessage = null
            )
            val result = repository.checkGitHubRelease(cleaned)
            preferences.recordUpdateCheck()
            result.fold(
                onSuccess = { info ->
                    _gitHubState.value = _gitHubState.value.copy(
                        isChecking = false,
                        releaseInfo = info,
                        errorMessage = null,
                        showUpdateDialog = showPopupIfAvailable && info.isUpdateAvailable
                    )
                    // Se è stata trovata una nuova versione con APK allegato, avvia automaticamente
                    // il download in background e l'installazione appena terminato il download
                    if (info.isUpdateAvailable && !info.apkDownloadUrl.isNullOrBlank()) {
                        startBackgroundApkDownloadAndInstall(info)
                    }
                },
                onFailure = { err ->
                    _gitHubState.value = _gitHubState.value.copy(
                        isChecking = false,
                        errorMessage = err.localizedMessage ?: "Impossibile verificare le release su GitHub."
                    )
                }
            )
        }
    }

    fun startBackgroundApkDownloadAndInstall(release: GitHubReleaseInfo) {
        if (_gitHubState.value.isDownloadingApk) return
        apkDownloadJob?.cancel()
        apkDownloadJob = viewModelScope.launch {
            _gitHubState.value = _gitHubState.value.copy(
                isDownloadingApk = true,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                totalBytes = release.apkSizeBytes,
                autoInstallTriggered = false,
                errorMessage = null
            )

            val downloadResult = repository.gitHubUpdateService.downloadReleaseApkInBackground(
                context = getApplication(),
                release = release,
                onProgress = { downloaded, total, fraction ->
                    _gitHubState.value = _gitHubState.value.copy(
                        isDownloadingApk = true,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        downloadProgress = fraction
                    )
                }
            )

            downloadResult.fold(
                onSuccess = { apkFile ->
                    _gitHubState.value = _gitHubState.value.copy(
                        isDownloadingApk = false,
                        downloadProgress = 1f,
                        downloadedBytes = apkFile.length(),
                        totalBytes = apkFile.length(),
                        downloadedApkPath = apkFile.absolutePath,
                        autoInstallTriggered = true
                    )
                    // Avvia automaticamente il processo di aggiornamento di sistema appena terminato il download
                    repository.gitHubUpdateService.launchApkInstaller(getApplication(), apkFile)
                },
                onFailure = { err ->
                    _gitHubState.value = _gitHubState.value.copy(
                        isDownloadingApk = false,
                        errorMessage = err.localizedMessage ?: "Errore durante il download in background dell'APK."
                    )
                }
            )
        }
    }

    fun dismissUpdateDialog() {
        _gitHubState.value = _gitHubState.value.copy(showUpdateDialog = false)
    }

    fun openUpdateDialog() {
        _gitHubState.value = _gitHubState.value.copy(showUpdateDialog = true)
    }

    fun performInAppUpdate(context: Context) {
        val state = _gitHubState.value
        val release = state.releaseInfo ?: return
        val existingPath = state.downloadedApkPath
        if (!existingPath.isNullOrBlank()) {
            val file = File(existingPath)
            if (file.exists()) {
                repository.gitHubUpdateService.launchApkInstaller(context, file)
                return
            }
        }
        if (!release.apkDownloadUrl.isNullOrBlank()) {
            startBackgroundApkDownloadAndInstall(release)
        } else {
            repository.gitHubUpdateService.triggerInAppUpdate(context, release)
        }
    }

    override fun onCleared() {
        simklSyncManager.stopPeriodicSync()
        super.onCleared()
    }
}
