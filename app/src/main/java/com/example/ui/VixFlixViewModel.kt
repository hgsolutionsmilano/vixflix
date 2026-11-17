package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppPreferences
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.repository.CatalogFeed
import com.example.data.repository.VixFlixRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NavTab(val label: String) {
    HOME("Home"),
    SERIES("Serie TV"),
    MOVIES("Film"),
    NEW_POPULAR("Nuovi & Popolari"),
    MY_LIST("La Mia Lista"),
    GITHUB("GitHub Update")
}

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val feed: CatalogFeed) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}

data class DetailUiState(
    val mediaItem: MediaItem,
    val isLoading: Boolean = true,
    val details: MediaDetails? = null,
    val selectedSeason: Int = 1,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val episodeProgressMap: Map<Int, EpisodeProgressEntity> = emptyMap(),
    val errorMessage: String? = null
)

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
    val errorMessage: String? = null
) {
    val startSeconds: Long get() = (startPositionMs / 1000L).coerceAtLeast(0L)
    val startMinutes: Int get() = (startSeconds / 60L).toInt()
}

data class GitHubUpdateUiState(
    val repoSlug: String = "",
    val isChecking: Boolean = false,
    val releaseInfo: GitHubReleaseInfo? = null,
    val errorMessage: String? = null,
    val showUpdateDialog: Boolean = false
)

class VixFlixViewModel(application: Application) : AndroidViewModel(application) {
    private val database = VixFlixDatabase.getInstance(application)
    val repository = VixFlixRepository(database.vixFlixDao())
    private val preferences = AppPreferences(application)

    private val _selectedTab = MutableStateFlow(NavTab.HOME)
    val selectedTab: StateFlow<NavTab> = _selectedTab.asStateFlow()

    private val _catalogState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val catalogState: StateFlow<CatalogUiState> = _catalogState.asStateFlow()

    val continueWatching: StateFlow<List<WatchProgressEntity>> = repository.continueWatchingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val watchlist: StateFlow<List<WatchlistEntity>> = repository.watchlistFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val streamLanguage: StateFlow<String> = preferences.streamLanguageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "it")

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _detailState = MutableStateFlow<DetailUiState?>(null)
    val detailState: StateFlow<DetailUiState?> = _detailState.asStateFlow()

    private val _playbackSession = MutableStateFlow<PlaybackSession?>(null)
    val playbackSession: StateFlow<PlaybackSession?> = _playbackSession.asStateFlow()

    private val _gitHubState = MutableStateFlow(GitHubUpdateUiState())
    val gitHubState: StateFlow<GitHubUpdateUiState> = _gitHubState.asStateFlow()

    private var searchJob: Job? = null
    private var seasonProgressJob: Job? = null

    init {
        loadCatalog()
        initGitHubUpdateChecker()
    }

    fun selectTab(tab: NavTab) {
        _selectedTab.value = tab
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _catalogState.value = CatalogUiState.Loading
            val result = repository.fetchLiveCatalogFeed()
            result.fold(
                onSuccess = { feed ->
                    _catalogState.value = CatalogUiState.Success(feed)
                },
                onFailure = { error ->
                    _catalogState.value = CatalogUiState.Error(
                        error.localizedMessage ?: "Impossibile connettersi alle API di TMDB."
                    )
                }
            )
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(320)
            repository.searchTmdb(newQuery).fold(
                onSuccess = { items ->
                    _searchResults.value = items
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
        _isSearching.value = false
    }

    fun openDetails(item: MediaItem) {
        viewModelScope.launch {
            // Controlla se abbiamo una stagione salvata in "Continua a guardare"
            val savedProgress = database.vixFlixDao().getWatchProgress(item.uniqueKey)
            val initialSeason = savedProgress?.seasonNumber ?: 1

            _detailState.value = DetailUiState(
                mediaItem = item,
                isLoading = true,
                selectedSeason = initialSeason
            )

            repository.fetchMediaDetails(item).fold(
                onSuccess = { details ->
                    val targetSeason = if (details.seasons.any { it.seasonNumber == initialSeason }) {
                        initialSeason
                    } else {
                        details.seasons.firstOrNull()?.seasonNumber ?: 1
                    }
                    _detailState.value = _detailState.value?.copy(
                        mediaItem = details.item,
                        isLoading = false,
                        details = details,
                        selectedSeason = targetSeason,
                        errorMessage = null
                    )
                    if (details.item.isSeries) {
                        selectSeason(details.item.id, targetSeason)
                    }
                },
                onFailure = { err ->
                    _detailState.value = _detailState.value?.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Errore caricamento dettagli da TMDB"
                    )
                }
            )
        }
    }

    fun closeDetails() {
        seasonProgressJob?.cancel()
        _detailState.value = null
    }

    fun selectSeason(tvId: Int, seasonNumber: Int) {
        val current = _detailState.value ?: return
        _detailState.value = current.copy(
            selectedSeason = seasonNumber,
            isLoadingEpisodes = true
        )

        seasonProgressJob?.cancel()
        seasonProgressJob = viewModelScope.launch {
            repository.observeSeasonProgress(tvId, seasonNumber).collect { progressList ->
                val map = progressList.associateBy { it.episodeNumber }
                _detailState.value = _detailState.value?.copy(episodeProgressMap = map)
            }
        }

        viewModelScope.launch {
            repository.fetchSeasonEpisodes(tvId, seasonNumber).fold(
                onSuccess = { episodes ->
                    _detailState.value = _detailState.value?.copy(
                        episodes = episodes,
                        isLoadingEpisodes = false
                    )
                },
                onFailure = {
                    _detailState.value = _detailState.value?.copy(
                        isLoadingEpisodes = false
                    )
                }
            )
        }
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
        explicitStartPositionMs: Long? = null
    ) {
        viewModelScope.launch {
            val lang = streamLanguage.value
            var targetSeason = seasonNumber
            var targetEpisode = episodeNumber
            var targetEpTitle = episodeTitle
            var seasonEpisodes = _detailState.value?.takeIf { it.mediaItem.id == item.id }?.episodes.orEmpty()

            if (item.isSeries && (targetSeason == null || targetEpisode == null)) {
                val saved = database.vixFlixDao().getWatchProgress(item.uniqueKey)
                targetSeason = saved?.seasonNumber ?: 1
                targetEpisode = saved?.episodeNumber ?: 1
                targetEpTitle = saved?.episodeTitle
                if (seasonEpisodes.isEmpty()) {
                    seasonEpisodes = repository.fetchSeasonEpisodes(item.id, targetSeason).getOrDefault(emptyList())
                }
                if (targetEpTitle.isNullOrBlank()) {
                    targetEpTitle = seasonEpisodes.firstOrNull { it.episodeNumber == targetEpisode }?.name
                }
            }

            val startPosMs = explicitStartPositionMs ?: repository.getSavedProgress(
                tmdbId = item.id,
                mediaType = item.mediaType,
                season = targetSeason,
                episode = targetEpisode
            )

            val initialEmbedUrl = repository.vixSrcResolver.buildVixSrcApiUrl(
                tmdbId = item.id,
                mediaType = item.mediaType,
                season = targetSeason,
                episode = targetEpisode,
                startSeconds = (startPosMs / 1000L).coerceAtLeast(0L),
                lang = lang
            )

            _playbackSession.value = PlaybackSession(
                mediaItem = item,
                seasonNumber = targetSeason,
                episodeNumber = targetEpisode,
                episodeTitle = targetEpTitle,
                seasonEpisodes = seasonEpisodes,
                startPositionMs = startPosMs,
                isResolvingStream = true,
                resolvedStream = ResolvedStream(
                    streamUrl = initialEmbedUrl,
                    embedUrl = initialEmbedUrl,
                    startSeconds = (startPosMs / 1000L).coerceAtLeast(0L),
                    startMinutes = ((startPosMs / 1000L) / 60L).toInt(),
                    isDirectHls = false
                )
            )

            // Salva subito l'ingresso in "Continua a guardare" se parte da 0 così compare nella riga
            if (startPosMs > 0L) {
                repository.saveWatchProgress(
                    item = item,
                    seasonNumber = targetSeason,
                    episodeNumber = targetEpisode,
                    episodeTitle = targetEpTitle,
                    positionMs = startPosMs,
                    durationMs = 5_400_000L
                )
            }

            val resolved = repository.resolveVixSrcStream(
                item = item,
                season = targetSeason,
                episode = targetEpisode,
                startPositionMs = startPosMs,
                lang = lang
            )

            resolved.fold(
                onSuccess = { stream ->
                    _playbackSession.value = _playbackSession.value?.copy(
                        isResolvingStream = false,
                        resolvedStream = stream,
                        errorMessage = null
                    )
                },
                onFailure = { err ->
                    _playbackSession.value = _playbackSession.value?.copy(
                        isResolvingStream = false,
                        errorMessage = err.localizedMessage
                    )
                }
            )
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
    }

    fun togglePlayerMode(forceWeb: Boolean) {
        val current = _playbackSession.value ?: return
        _playbackSession.value = current.copy(forceWebPlayer = forceWeb)
    }

    fun onPlaybackProgressUpdate(positionMs: Long, durationMs: Long) {
        val session = _playbackSession.value ?: return
        if (positionMs <= 0L) return
        _playbackSession.value = session.copy(startPositionMs = positionMs)
        viewModelScope.launch {
            repository.saveWatchProgress(
                item = session.mediaItem,
                seasonNumber = session.seasonNumber,
                episodeNumber = session.episodeNumber,
                episodeTitle = session.episodeTitle,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }
    }

    fun playNextEpisode() {
        val session = _playbackSession.value ?: return
        if (!session.mediaItem.isSeries) return
        val currentSeason = session.seasonNumber ?: 1
        val currentEp = session.episodeNumber ?: 1
        val nextEpDto = session.seasonEpisodes.firstOrNull { it.episodeNumber == currentEp + 1 }
        val nextEpisodeNumber = nextEpDto?.episodeNumber ?: (currentEp + 1)
        val nextEpisodeTitle = nextEpDto?.name ?: "Episodio $nextEpisodeNumber"

        startPlayback(
            item = session.mediaItem,
            seasonNumber = currentSeason,
            episodeNumber = nextEpisodeNumber,
            episodeTitle = nextEpisodeTitle,
            explicitStartPositionMs = 0L
        )
    }

    fun closePlayer() {
        _playbackSession.value = null
    }

    fun removeContinueWatching(id: String) {
        viewModelScope.launch {
            repository.removeContinueWatching(id)
        }
    }

    fun toggleWatchlist(item: MediaItem) {
        viewModelScope.launch {
            val isInList = watchlist.value.any { it.id == item.uniqueKey }
            repository.toggleWatchlist(item, isInList)
        }
    }

    fun setStreamLanguage(lang: String) {
        viewModelScope.launch {
            preferences.setStreamLanguage(lang)
            val currentSession = _playbackSession.value
            if (currentSession != null) {
                startPlayback(
                    item = currentSession.mediaItem,
                    seasonNumber = currentSession.seasonNumber,
                    episodeNumber = currentSession.episodeNumber,
                    episodeTitle = currentSession.episodeTitle,
                    explicitStartPositionMs = currentSession.startPositionMs
                )
            }
        }
    }

    private fun initGitHubUpdateChecker() {
        viewModelScope.launch {
            val savedRepo = preferences.githubRepoFlow.first()
            _gitHubState.value = _gitHubState.value.copy(repoSlug = savedRepo)
            if (savedRepo.isNotBlank()) {
                checkForGitHubUpdates(savedRepo, showPopupIfAvailable = true)
            }
        }
    }

    fun updateGitHubRepoSlug(newSlug: String) {
        val cleaned = newSlug.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        _gitHubState.value = _gitHubState.value.copy(repoSlug = cleaned, errorMessage = null)
        viewModelScope.launch {
            preferences.setGithubRepo(cleaned)
        }
    }

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

    fun dismissUpdateDialog() {
        _gitHubState.value = _gitHubState.value.copy(showUpdateDialog = false)
    }

    fun openUpdateDialog() {
        _gitHubState.value = _gitHubState.value.copy(showUpdateDialog = true)
    }

    fun performInAppUpdate(context: Context) {
        val release = _gitHubState.value.releaseInfo ?: return
        repository.gitHubUpdateService.triggerInAppUpdate(context, release)
    }
}
