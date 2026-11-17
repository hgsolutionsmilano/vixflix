package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.MediaItem
import com.example.data.repository.CatalogFeed
import com.example.ui.CatalogUiState
import com.example.ui.NavTab
import com.example.ui.VixFlixViewModel
import com.example.ui.components.ContinueWatchingSection
import com.example.ui.components.MediaCatalogRow
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.NetflixHeroBanner
import com.example.ui.components.NetflixTopBar
import com.example.ui.components.Top10Section
import com.example.ui.components.rememberIsTvDevice
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite

@Composable
fun VixFlixAppScreen(
    viewModel: VixFlixViewModel
) {
    val context = LocalContext.current
    val isTvDevice = rememberIsTvDevice()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val streamLang by viewModel.streamLanguage.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val playbackSession by viewModel.playbackSession.collectAsStateWithLifecycle()
    val gitHubState by viewModel.gitHubState.collectAsStateWithLifecycle()

    var isSearchBarVisible by remember { mutableStateOf(false) }
    val heroPlayFocusRequester = remember { FocusRequester() }
    val searchInputFocusRequester = remember { FocusRequester() }

    // 1. Se c'è una sessione di riproduzione attiva, mostra a tutto schermo il player ExoPlayer + VixSrc
    val activeSession = playbackSession
    if (activeSession != null) {
        VixPlayerScreen(
            session = activeSession,
            streamLang = streamLang,
            vixSrcResolver = viewModel.repository.vixSrcResolver,
            onDynamicHlsFound = viewModel::onDynamicHlsIntercepted,
            onTogglePlayerMode = viewModel::togglePlayerMode,
            onToggleLanguage = viewModel::setStreamLanguage,
            onProgressUpdate = viewModel::onPlaybackProgressUpdate,
            onNextEpisode = viewModel::playNextEpisode,
            onClose = viewModel::closePlayer
        )
        return
    }

    // 2. Se i dettagli di un film/serie sono aperti, mostra la schermata dettagli & episodi
    val currentDetail = detailState
    if (currentDetail != null) {
        val savedEntry = continueWatching.firstOrNull { it.id == currentDetail.mediaItem.uniqueKey }
        val inList = watchlist.any { it.id == currentDetail.mediaItem.uniqueKey }
        MediaDetailScreen(
            state = currentDetail,
            savedProgress = savedEntry,
            isInWatchlist = inList,
            onClose = viewModel::closeDetails,
            onPlayMain = { restartFromZero ->
                viewModel.startPlayback(
                    item = currentDetail.mediaItem,
                    seasonNumber = if (currentDetail.mediaItem.isSeries) (savedEntry?.seasonNumber ?: currentDetail.selectedSeason) else null,
                    episodeNumber = if (currentDetail.mediaItem.isSeries) (savedEntry?.episodeNumber ?: 1) else null,
                    episodeTitle = savedEntry?.episodeTitle,
                    explicitStartPositionMs = if (restartFromZero) 0L else null
                )
            },
            onPlayEpisode = { season, episode, epTitle ->
                viewModel.startPlayback(
                    item = currentDetail.mediaItem,
                    seasonNumber = season,
                    episodeNumber = episode,
                    episodeTitle = epTitle
                )
            },
            onOpenYouTubeTrailerPopup = viewModel::openYouTubeTrailerPopup,
            onSelectYouTubeTrailer = viewModel::selectYouTubeTrailer,
            onRetryYouTubeTrailer = viewModel::retryCurrentYouTubeTrailer,
            onCloseTrailer = viewModel::closeTrailerPlayer,
            onSelectSeason = { seasonNum ->
                viewModel.selectSeason(currentDetail.mediaItem.id, seasonNum)
            },
            onToggleWatchlist = {
                viewModel.toggleWatchlist(currentDetail.mediaItem)
            },
            onSelectSimilar = { similarItem ->
                viewModel.openDetails(similarItem)
            }
        )
        return
    }

    // Gestione tasto INDIETRO del telecomando Android TV / Google TV
    BackHandler(enabled = isSearchBarVisible || selectedTab != NavTab.HOME) {
        if (isSearchBarVisible) {
            isSearchBarVisible = false
            viewModel.clearSearch()
        } else if (selectedTab != NavTab.HOME) {
            viewModel.selectTab(NavTab.HOME)
        }
    }

    LaunchedEffect(isSearchBarVisible) {
        if (isSearchBarVisible) {
            runCatching { searchInputFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(isTvDevice, catalogState is CatalogUiState.Success) {
        if (isTvDevice && catalogState is CatalogUiState.Success) {
            runCatching { heroPlayFocusRequester.requestFocus() }
        }
    }

    // 3. Popup automatico se GitHub Releases segnala una nuova versione
    GitHubUpdateDialog(
        state = gitHubState,
        onDismiss = viewModel::dismissUpdateDialog,
        onConfirmUpdate = { viewModel.performInAppUpdate(context) },
        onOpenGitHubTab = { viewModel.selectTab(NavTab.GITHUB) }
    )

    val navItems = remember {
        listOf(
            Triple(NavTab.HOME, "Home", Icons.Default.Home),
            Triple(NavTab.SERIES, "Serie TV", Icons.Default.Tv),
            Triple(NavTab.MOVIES, "Film", Icons.Default.Movie),
            Triple(NavTab.NEW_POPULAR, "Top & Nuovi", Icons.Default.NewReleases),
            Triple(NavTab.MY_LIST, "La Mia Lista", Icons.Default.BookmarkBorder),
            Triple(NavTab.GITHUB, "GitHub CI", Icons.Default.CloudDownload)
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        val useTvNavigationRail = isTvDevice || maxWidth >= 840.dp

        Scaffold(
            containerColor = NetflixBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useTvNavigationRail) {
                    NavigationBar(
                        containerColor = NetflixDarkSurface,
                        contentColor = NetflixWhite,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { (tab, label, icon) ->
                            var navFocused by remember { mutableStateOf(false) }
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    isSearchBarVisible = false
                                    viewModel.clearSearch()
                                    viewModel.selectTab(tab)
                                },
                                icon = {
                                    Icon(imageVector = icon, contentDescription = label)
                                },
                                label = {
                                    Text(text = label, maxLines = 1)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NetflixRed,
                                    selectedTextColor = NetflixWhite,
                                    indicatorColor = if (navFocused) NetflixRed.copy(alpha = 0.32f) else NetflixElevatedSurface,
                                    unselectedIconColor = if (navFocused) NetflixWhite else NetflixMutedGray,
                                    unselectedTextColor = if (navFocused) NetflixWhite else NetflixMutedGray
                                ),
                                modifier = Modifier
                                    .onFocusChanged { navFocused = it.isFocused }
                                    .testTag("bottom_nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixBlack)
            ) {
                // NavigationRail laterale stile Google TV / Android TV navigabile comodamente con il telecomando
                if (useTvNavigationRail) {
                    NavigationRail(
                        containerColor = NetflixDarkSurface,
                        contentColor = NetflixWhite,
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .padding(top = 12.dp, bottom = 12.dp)
                            .testTag("tv_navigation_rail")
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        navItems.forEach { (tab, label, icon) ->
                            var itemFocused by remember { mutableStateOf(false) }
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    isSearchBarVisible = false
                                    viewModel.clearSearch()
                                    viewModel.selectTab(tab)
                                },
                                icon = {
                                    Icon(imageVector = icon, contentDescription = label)
                                },
                                label = {
                                    Text(text = label, maxLines = 1)
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NetflixRed,
                                    selectedTextColor = NetflixWhite,
                                    indicatorColor = if (itemFocused) NetflixRed.copy(alpha = 0.35f) else NetflixElevatedSurface,
                                    unselectedIconColor = if (itemFocused) NetflixWhite else NetflixMutedGray,
                                    unselectedTextColor = if (itemFocused) NetflixWhite else NetflixMutedGray
                                ),
                                modifier = Modifier
                                    .onFocusChanged { itemFocused = it.isFocused }
                                    .testTag("tv_rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(NetflixBlack)
                ) {
                    val topBarOffset = if (useTvNavigationRail) 78.dp else 122.dp

                    if (isSearchBarVisible && searchQuery.isNotBlank()) {
                        SearchResultsGrid(
                            query = searchQuery,
                            isSearching = isSearching,
                            results = searchResults,
                            isTvLayout = useTvNavigationRail,
                            onItemClick = viewModel::openDetails,
                            contentPadding = PaddingValues(
                                top = topBarOffset + 46.dp,
                                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                                start = 16.dp,
                                end = 16.dp
                            )
                        )
                    } else if (selectedTab == NavTab.GITHUB) {
                        GitHubUpdateSectionScreen(
                            state = gitHubState,
                            onRepoSlugChange = viewModel::updateGitHubRepoSlug,
                            onCheckUpdates = { slug -> viewModel.checkForGitHubUpdates(slug, showPopupIfAvailable = false) },
                            onTriggerUpdate = { viewModel.performInAppUpdate(context) },
                            onUpdateTmdbKey = viewModel::updateTmdbApiKey,
                            onResetTmdbKey = viewModel::resetTmdbApiKeyToDefault,
                            contentPadding = PaddingValues(
                                top = topBarOffset,
                                bottom = innerPadding.calculateBottomPadding()
                            )
                        )
                    } else if (selectedTab == NavTab.MY_LIST) {
                        MyListScreen(
                            watchlist = watchlist,
                            continueWatching = continueWatching,
                            onOpenItem = viewModel::openDetails,
                            onResumeContinueWatching = viewModel::resumeFromContinueWatching,
                            onRemoveContinueWatching = { viewModel.removeContinueWatching(it.id) },
                            contentPadding = PaddingValues(
                                top = topBarOffset,
                                bottom = innerPadding.calculateBottomPadding() + 24.dp
                            )
                        )
                    } else {
                        when (val state = catalogState) {
                            is CatalogUiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = NetflixRed)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Caricamento catalogo live da TMDB…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NetflixLightGray
                                        )
                                    }
                                }
                            }

                            is CatalogUiState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = NetflixRed,
                                            modifier = Modifier.size(52.dp)
                                        )
                                        Text(
                                            text = "Connessione TMDB Richiesta",
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = NetflixWhite,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NetflixLightGray,
                                            textAlign = TextAlign.Center
                                        )
                                        var retryFocused by remember { mutableStateOf(false) }
                                        Button(
                                            onClick = viewModel::loadCatalog,
                                            border = if (retryFocused) BorderStroke(2.5.dp, NetflixWhite) else null,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NetflixRed,
                                                contentColor = NetflixWhite
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .onFocusChanged { retryFocused = it.isFocused }
                                                .testTag("retry_tmdb_button")
                                        ) {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Riprova Connessione TMDB")
                                        }
                                    }
                                }
                            }

                            is CatalogUiState.Success -> {
                                CatalogFeedContent(
                                    feed = state.feed,
                                    selectedTab = selectedTab,
                                    continueWatching = continueWatching,
                                    watchlist = watchlist,
                                    isTvLayout = useTvNavigationRail,
                                    heroPlayFocusRequester = heroPlayFocusRequester,
                                    onPlayItem = { item -> viewModel.startPlayback(item) },
                                    onOpenDetails = viewModel::openDetails,
                                    onToggleWatchlist = viewModel::toggleWatchlist,
                                    onResumeContinueWatching = viewModel::resumeFromContinueWatching,
                                    onInfoContinueWatching = { entry ->
                                        viewModel.openDetails(
                                            MediaItem(
                                                id = entry.tmdbId,
                                                mediaType = entry.mediaType,
                                                title = entry.title,
                                                overview = entry.overview,
                                                posterPath = entry.posterPath,
                                                backdropPath = entry.backdropPath,
                                                voteAverage = entry.voteAverage,
                                                releaseYear = entry.releaseYear
                                            )
                                        )
                                    },
                                    onRemoveContinueWatching = { entry ->
                                        viewModel.removeContinueWatching(entry.id)
                                    },
                                    bottomPadding = innerPadding.calculateBottomPadding()
                                )
                            }
                        }
                    }

                    // Top bar flottante stile Netflix (compatibile D-pad Android TV)
                    NetflixTopBar(
                        selectedTab = selectedTab,
                        onSelectTab = { tab ->
                            isSearchBarVisible = false
                            viewModel.clearSearch()
                            viewModel.selectTab(tab)
                        },
                        searchQuery = searchQuery,
                        isSearchOpen = isSearchBarVisible,
                        onToggleSearch = {
                            isSearchBarVisible = !isSearchBarVisible
                            if (!isSearchBarVisible) {
                                viewModel.clearSearch()
                            }
                        },
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        streamLang = streamLang,
                        onToggleLang = {
                            viewModel.setStreamLanguage(if (streamLang == "it") "en" else "it")
                        },
                        hasUpdateAvailable = gitHubState.releaseInfo?.isUpdateAvailable == true,
                        onClickUpdateIcon = {
                            if (gitHubState.releaseInfo?.isUpdateAvailable == true) {
                                viewModel.openUpdateDialog()
                            } else {
                                viewModel.selectTab(NavTab.GITHUB)
                            }
                        },
                        isTvLayout = useTvNavigationRail,
                        searchFocusRequester = searchInputFocusRequester,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogFeedContent(
    feed: CatalogFeed,
    selectedTab: NavTab,
    continueWatching: List<WatchProgressEntity>,
    watchlist: List<WatchlistEntity>,
    isTvLayout: Boolean,
    heroPlayFocusRequester: FocusRequester,
    onPlayItem: (MediaItem) -> Unit,
    onOpenDetails: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit,
    onResumeContinueWatching: (WatchProgressEntity) -> Unit,
    onInfoContinueWatching: (WatchProgressEntity) -> Unit,
    onRemoveContinueWatching: (WatchProgressEntity) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    val heroItem = when (selectedTab) {
        NavTab.SERIES -> feed.trendingTv.firstOrNull() ?: feed.popularTv.firstOrNull() ?: feed.heroBanner
        NavTab.MOVIES -> feed.trendingMovies.firstOrNull() ?: feed.popularMovies.firstOrNull() ?: feed.heroBanner
        else -> feed.heroBanner
    }

    val filteredContinueWatching = when (selectedTab) {
        NavTab.SERIES -> continueWatching.filter { it.mediaType == "tv" }
        NavTab.MOVIES -> continueWatching.filter { it.mediaType == "movie" }
        else -> continueWatching
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("catalog_lazy_column"),
        contentPadding = PaddingValues(bottom = bottomPadding + 32.dp)
    ) {
        if (selectedTab != NavTab.NEW_POPULAR) {
            item(key = "hero_${heroItem.uniqueKey}") {
                val isInWatchlist = watchlist.any { it.id == heroItem.uniqueKey }
                val savedProgress = continueWatching.firstOrNull { it.id == heroItem.uniqueKey }
                NetflixHeroBanner(
                    item = heroItem,
                    isInWatchlist = isInWatchlist,
                    savedProgress = savedProgress,
                    onPlayClick = { onPlayItem(heroItem) },
                    onToggleWatchlist = { onToggleWatchlist(heroItem) },
                    onInfoClick = { onOpenDetails(heroItem) },
                    playButtonFocusRequester = heroPlayFocusRequester,
                    isTvLayout = isTvLayout
                )
            }
        } else {
            item { Spacer(modifier = Modifier.height(if (isTvLayout) 84.dp else 118.dp)) }
        }

        // Sezione "Continua a guardare" (Room DB + startAt VixSrc API)
        if (filteredContinueWatching.isNotEmpty()) {
            item(key = "row_continue_watching") {
                ContinueWatchingSection(
                    entries = filteredContinueWatching,
                    onResumeClick = onResumeContinueWatching,
                    onInfoClick = onInfoContinueWatching,
                    onRemoveClick = onRemoveContinueWatching
                )
            }
        }

        when (selectedTab) {
            NavTab.HOME -> {
                item(key = "row_top_10") {
                    Top10Section(
                        title = "Top 10 in Italia Oggi",
                        items = feed.top10Today,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "row_trending_movies") {
                    MediaCatalogRow(
                        title = "Film di Tendenza su TMDB",
                        items = feed.trendingMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "row_trending_tv") {
                    MediaCatalogRow(
                        title = "Serie TV del Momento",
                        items = feed.trendingTv,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "row_popular_movies") {
                    MediaCatalogRow(
                        title = "Film Popolari su VixFlix",
                        items = feed.popularMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "row_popular_tv") {
                    MediaCatalogRow(
                        title = "Serie TV Acclamate",
                        items = feed.popularTv,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "row_now_playing") {
                    MediaCatalogRow(
                        title = "Ora al Cinema",
                        items = feed.nowPlayingMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "row_top_rated") {
                    MediaCatalogRow(
                        title = "Più Votati dalla Critica",
                        items = feed.topRatedMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "row_action") {
                    MediaCatalogRow(
                        title = "Azione & Adrenalina",
                        items = feed.actionMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "row_scifi") {
                    MediaCatalogRow(
                        title = "Fantascienza & Mondi Futuri",
                        items = feed.sciFiMovies,
                        onItemClick = onOpenDetails
                    )
                }
            }

            NavTab.SERIES -> {
                item(key = "tv_trending") {
                    MediaCatalogRow(
                        title = "Serie TV in Tendenza",
                        items = feed.trendingTv,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "tv_popular") {
                    MediaCatalogRow(
                        title = "Serie TV Popolari",
                        items = feed.popularTv,
                        onItemClick = onOpenDetails
                    )
                }
            }

            NavTab.MOVIES -> {
                item(key = "movies_trending") {
                    MediaCatalogRow(
                        title = "Film di Tendenza",
                        items = feed.trendingMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "movies_now_playing") {
                    MediaCatalogRow(
                        title = "Ora al Cinema",
                        items = feed.nowPlayingMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "movies_popular") {
                    MediaCatalogRow(
                        title = "Film Popolari",
                        items = feed.popularMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "movies_top_rated") {
                    MediaCatalogRow(
                        title = "Film Più Votati",
                        items = feed.topRatedMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "movies_action") {
                    MediaCatalogRow(
                        title = "Azione & Avventura",
                        items = feed.actionMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "movies_scifi") {
                    MediaCatalogRow(
                        title = "Fantascienza",
                        items = feed.sciFiMovies,
                        onItemClick = onOpenDetails
                    )
                }
            }

            NavTab.NEW_POPULAR -> {
                item(key = "new_top10") {
                    Top10Section(
                        title = "Top 10 in Italia Oggi",
                        items = feed.top10Today,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "new_cinema") {
                    MediaCatalogRow(
                        title = "Nuove Uscite al Cinema",
                        items = feed.nowPlayingMovies,
                        onItemClick = onOpenDetails
                    )
                }
                item(key = "new_tv") {
                    MediaCatalogRow(
                        title = "Serie TV Nuove e Popolari",
                        items = feed.trendingTv,
                        onItemClick = onOpenDetails
                    )
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun SearchResultsGrid(
    query: String,
    isSearching: Boolean,
    results: List<MediaItem>,
    isTvLayout: Boolean,
    onItemClick: (MediaItem) -> Unit,
    contentPadding: PaddingValues
) {
    if (isSearching) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = NetflixRed)
        }
        return
    }

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nessun risultato trovato su TMDB per \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                color = NetflixLightGray,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (isTvLayout) 136.dp else 110.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_results_grid")
    ) {
        itemsIndexed(results, key = { _, it -> "search_${it.uniqueKey}" }) { index, item ->
            MediaPosterCard(
                item = item,
                itemIndex = index,
                onClick = { onItemClick(item) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MyListScreen(
    watchlist: List<WatchlistEntity>,
    continueWatching: List<WatchProgressEntity>,
    onOpenItem: (MediaItem) -> Unit,
    onResumeContinueWatching: (WatchProgressEntity) -> Unit,
    onRemoveContinueWatching: (WatchProgressEntity) -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("my_list_screen"),
        contentPadding = contentPadding
    ) {
        if (continueWatching.isNotEmpty()) {
            item {
                ContinueWatchingSection(
                    entries = continueWatching,
                    onResumeClick = onResumeContinueWatching,
                    onInfoClick = { entry ->
                        onOpenItem(
                            MediaItem(
                                id = entry.tmdbId,
                                mediaType = entry.mediaType,
                                title = entry.title,
                                overview = entry.overview,
                                posterPath = entry.posterPath,
                                backdropPath = entry.backdropPath,
                                voteAverage = entry.voteAverage,
                                releaseYear = entry.releaseYear
                            )
                        )
                    },
                    onRemoveClick = onRemoveContinueWatching
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (watchlist.isEmpty() && continueWatching.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = NetflixMutedGray,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "La tua lista è vuota",
                            style = MaterialTheme.typography.titleLarge,
                            color = NetflixWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Aggiungi film e serie TV dal catalogo live di TMDB o inizia a guardare un titolo per salvarne il minuto in Room.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NetflixLightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (watchlist.isNotEmpty()) {
            val mappedItems = watchlist.map {
                MediaItem(
                    id = it.tmdbId,
                    mediaType = it.mediaType,
                    title = it.title,
                    overview = it.overview,
                    posterPath = it.posterPath,
                    backdropPath = it.backdropPath,
                    voteAverage = it.voteAverage,
                    releaseYear = it.releaseYear
                )
            }
            item {
                MediaCatalogRow(
                    title = "Salvati nella Mia Lista (${mappedItems.size})",
                    items = mappedItems,
                    onItemClick = onOpenItem
                )
            }
        }
    }
}
