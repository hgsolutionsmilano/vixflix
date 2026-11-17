package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppPreferences
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.ResolvedTrailerStream
import com.example.data.remote.TmdbApiConfig
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.YouTubeTrailerCandidate
import com.example.data.repository.CatalogFeed
import com.example.data.repository.VixFlixRepository
import java.io.File
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
    SETTINGS("Impostazioni")
}

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val feed: CatalogFeed) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}

data class TrailerPlaybackState(
    val trailers: List<YouTubeTrailerCandidate> = emptyList(),
    val selectedVideo: YouTubeTrailerCandidate? = null,
    val isSearching: Boolean = false,
    val isLoadingStream: Boolean = false,
    val resolvedTrailer: ResolvedTrailerStream? = null,
    val errorMessage: String? = null
)

data class DetailUiState(
    val mediaItem: MediaItem,
    val isLoading: Boolean = true,
    val details: MediaDetails? = null,
    val selectedSeason: Int = 1,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val episodeProgressMap: Map<Int, EpisodeProgressEntity> = emptyMap(),
    val activeTrailer: TrailerPlaybackState? = null,
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

    val preferWebPlayer: StateFlow<Boolean> = preferences.preferWebPlayerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoCheckUpdates: StateFlow<Boolean> = preferences.autoCheckUpdatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val bufferPreset: StateFlow<StreamingBufferPreset> = preferences.bufferPresetFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamingBufferPreset.DEFAULT)

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
    private var trailerJob: Job? = null

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
        trailerJob?.cancel()
        seasonProgressJob?.cancel()
        _detailState.value = null
    }

    /**
     * Apre il popup trailer ed esegue una ricerca diretta su YouTube (prima Italiano, poi Inglese)
     * popolando la lista multipla direttamente nel modale.
     */
    fun openYouTubeTrailerPopup() {
        val current = _detailState.value ?: return
        trailerJob?.cancel()

        _detailState.value = current.copy(
            activeTrailer = TrailerPlaybackState(
                isSearching = true,
                isLoadingStream = false
            )
        )

        trailerJob = viewModelScope.launch {
            repository.searchYouTubeTrailers(current.mediaItem).fold(
                onSuccess = { results ->
                    val latest = _detailState.value ?: return@fold
                    if (latest.mediaItem.id != current.mediaItem.id || latest.activeTrailer == null) return@fold
                    val first = results.firstOrNull()
                    if (first == null) {
                        _detailState.value = latest.copy(
                            activeTrailer = TrailerPlaybackState(
                                trailers = emptyList(),
                                isSearching = false,
                                isLoadingStream = false,
                                errorMessage = "Nessun trailer trovato su YouTube per \"${current.mediaItem.title}\"."
                            )
                        )
                    } else {
                        _detailState.value = latest.copy(
                            activeTrailer = TrailerPlaybackState(
                                trailers = results,
                                selectedVideo = first,
                                isSearching = false,
                                isLoadingStream = true
                            )
                        )
                        resolveSelectedYouTubeCandidate(current.mediaItem.id, first)
                    }
                },
                onFailure = { err ->
                    val latest = _detailState.value ?: return@fold
                    if (latest.mediaItem.id != current.mediaItem.id || latest.activeTrailer == null) return@fold
                    _detailState.value = latest.copy(
                        activeTrailer = TrailerPlaybackState(
                            trailers = emptyList(),
                            isSearching = false,
                            isLoadingStream = false,
                            errorMessage = err.localizedMessage
                                ?: "Impossibile cercare i trailer su YouTube in questo momento."
                        )
                    )
                }
            )
        }
    }

    fun selectYouTubeTrailer(candidate: YouTubeTrailerCandidate) {
        val current = _detailState.value ?: return
        val currentPopup = current.activeTrailer ?: return
        trailerJob?.cancel()

        _detailState.value = current.copy(
            activeTrailer = currentPopup.copy(
                selectedVideo = candidate,
                isSearching = false,
                isLoadingStream = true,
                resolvedTrailer = null,
                errorMessage = null
            )
        )

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
                    _detailState.value = latest.copy(
                        activeTrailer = popup.copy(
                            isSearching = false,
                            isLoadingStream = false,
                            resolvedTrailer = resolved,
                            errorMessage = null
                        )
                    )
                }
            },
            onFailure = { err ->
                val latest = _detailState.value ?: return@fold
                val popup = latest.activeTrailer ?: return@fold
                if (latest.mediaItem.id == mediaId && popup.selectedVideo?.videoId == candidate.videoId) {
                    _detailState.value = latest.copy(
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
        )
    }

    fun closeTrailerPlayer() {
        trailerJob?.cancel()
        val current = _detailState.value ?: return
        _detailState.value = current.copy(activeTrailer = null)
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
                forceWebPlayer = preferWebPlayer.value,
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

    fun clearAllContinueWatching() {
        viewModelScope.launch {
            repository.clearAllContinueWatching()
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
            loadCatalog()
        }
    }

    fun resetTmdbApiKeyToDefault() {
        val defaultKey = TmdbApiConfig.resetToDefault()
        _gitHubState.value = _gitHubState.value.copy(tmdbApiKey = defaultKey)
        viewModelScope.launch {
            preferences.setTmdbApiKey(defaultKey)
            loadCatalog()
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
}
