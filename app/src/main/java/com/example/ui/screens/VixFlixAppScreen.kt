package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbGenreDto
import com.example.data.remote.TmdbWatchProviderDto
import com.example.data.repository.CatalogFeed
import com.example.ui.CatalogUiState
import com.example.ui.DetailUiState
import com.example.ui.MovieGenreFilterUiState
import com.example.ui.NavTab
import com.example.ui.PlatformFilterMediaType
import com.example.ui.PlaybackSession
import com.example.ui.StreamingPlatformUiState
import com.example.ui.VixFlixViewModel
import com.example.ui.components.ContinueWatchingSection
import com.example.ui.components.DpadFocusObserverRegistry
import com.example.ui.components.DpadFocusScope
import com.example.ui.components.MediaCatalogRow
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.MovieGenreChipBar
import com.example.ui.components.NetflixHeroBanner
import com.example.ui.components.NetflixTopBar
import com.example.ui.components.PlatformLogoBadge
import com.example.ui.components.StreamingPlatformsStrip
import com.example.ui.components.Top10Section
import com.example.ui.components.VixFlixSplashScreen
import com.example.ui.components.formatSecondsToMmSs
import com.example.ui.components.rememberIsTvDevice
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.BebasNeueFontFamily
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VixFlixAppScreen(
    viewModel: VixFlixViewModel,
    splashMinDurationMs: Long = 1_150L
) {
    val context = LocalContext.current
    val isTvDevice = rememberIsTvDevice()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val streamLang by viewModel.streamLanguage.collectAsStateWithLifecycle()
    val preferWebPlayer by viewModel.preferWebPlayer.collectAsStateWithLifecycle()
    val bufferPreset by viewModel.bufferPreset.collectAsStateWithLifecycle()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val backgroundPrefetchEnabled by viewModel.backgroundPrefetchEnabled.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val isBackgroundCatalogRefreshing by viewModel.isBackgroundCatalogRefreshing.collectAsStateWithLifecycle()
    val cacheClearedFeedbackNonce by viewModel.cacheClearedFeedbackNonce.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val detailStackSize by viewModel.detailStackSize.collectAsStateWithLifecycle()
    val previousDetailTitle by viewModel.previousDetailTitle.collectAsStateWithLifecycle()
    val playbackSession by viewModel.playbackSession.collectAsStateWithLifecycle()
    val gitHubState by viewModel.gitHubState.collectAsStateWithLifecycle()
    val movieGenreState by viewModel.movieGenreState.collectAsStateWithLifecycle()
    val platformState by viewModel.platformState.collectAsStateWithLifecycle()

    var isSplashVisible by remember { mutableStateOf(splashMinDurationMs > 0L) }
    var isSearchBarVisible by remember { mutableStateOf(false) }
    var requestedSettingsCategory by remember { mutableStateOf(SettingsCategory.PLAYBACK) }
    var requestedSettingsNonce by remember { mutableIntStateOf(0) }
    var tabScrollResetNonce by remember { mutableIntStateOf(0) }
    var isRailFocused by remember { mutableStateOf(false) }
    var tvQuickActionEntry by remember { mutableStateOf<WatchProgressEntity?>(null) }
    var tvQuickActionItem by remember { mutableStateOf<MediaItem?>(null) }
    var lastBackPressMs by remember { mutableLongStateOf(0L) }
    var showExitHintBanner by remember { mutableStateOf(false) }

    val heroPlayFocusRequesters = remember { NavTab.entries.associateWith { FocusRequester() } }
    val firstRowFocusRequesters = remember { NavTab.entries.associateWith { FocusRequester() } }
    val heroPlayFocusRequester = heroPlayFocusRequesters.getValue(selectedTab)
    val movieGenreBarFocusRequester = remember { FocusRequester() }
    val platformStripFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = firstRowFocusRequesters.getValue(selectedTab)
    val navRailSelectedFocusRequester = remember { FocusRequester() }
    val topBarBrandFocusRequester = remember { FocusRequester() }
    val searchInputFocusRequester = remember { FocusRequester() }
    val searchFirstItemFocusRequester = remember { FocusRequester() }
    val settingsFirstControlFocusRequester = remember { FocusRequester() }
    val retryButtonFocusRequester = remember { FocusRequester() }

    // Memoria del focus TV per evitare di tornare sempre in cima all'Hero chiudendo una locandina
    var lastFocusedContentRequester by remember(
        selectedTab,
        movieGenreState.selectedGenre?.id,
        platformState.selectedProvider?.providerId
    ) {
        mutableStateOf<FocusRequester?>(null)
    }
    var wasOverlayActive by remember { mutableStateOf(false) }

    // Memorizza l'ultimo DetailUiState per animarne l'uscita fluida verso il basso senza scatti
    var lastDetailSnapshot by remember { mutableStateOf<DetailUiState?>(null) }
    if (detailState != null) {
        lastDetailSnapshot = detailState
    }

    LaunchedEffect(splashMinDurationMs) {
        if (splashMinDurationMs <= 0L) {
            isSplashVisible = false
            return@LaunchedEffect
        }
        delay(splashMinDurationMs)
        var waitedExtra = 0L
        while (catalogState is CatalogUiState.Loading && waitedExtra < 700L) {
            delay(100L)
            waitedExtra += 100L
        }
        isSplashVisible = false
    }

    val isDetailVisible = detailState != null
    val isPlayerVisible = playbackSession != null
    val isCatalogInteractive = !isSplashVisible && !isDetailVisible && !isPlayerVisible

    val tabContentEntryFocusRequester: FocusRequester? = when (selectedTab) {
        NavTab.SETTINGS -> settingsFirstControlFocusRequester
        NavTab.MY_LIST -> firstRowFocusRequester
        NavTab.MOVIES -> when (catalogState) {
            is CatalogUiState.Error -> retryButtonFocusRequester
            is CatalogUiState.Success -> {
                if (movieGenreState.selectedGenre != null) movieGenreBarFocusRequester else heroPlayFocusRequester
            }
            is CatalogUiState.Loading -> null
        }
        else -> when (catalogState) {
            is CatalogUiState.Error -> retryButtonFocusRequester
            is CatalogUiState.Success -> {
                when {
                    selectedTab == NavTab.NEW_POPULAR -> firstRowFocusRequester
                    selectedTab == NavTab.HOME && platformState.selectedProvider != null -> platformStripFocusRequester
                    else -> heroPlayFocusRequester
                }
            }
            is CatalogUiState.Loading -> null
        }
    }

    val activeContentEntryFocusRequester: FocusRequester? = when {
        isSearchBarVisible && searchQuery.isNotBlank() -> {
            if (searchResults.isNotEmpty() && !isSearching) searchFirstItemFocusRequester else null
        }
        else -> lastFocusedContentRequester.takeIfAttached() ?: tabContentEntryFocusRequester.takeIfAttached()
    }

    LaunchedEffect(showExitHintBanner) {
        if (showExitHintBanner) {
            delay(2_400L)
            showExitHintBanner = false
        }
    }

    LaunchedEffect(isSearchBarVisible) {
        if (isSearchBarVisible) {
            for (frame in 1..3) {
                androidx.compose.runtime.withFrameNanos { }
                searchInputFocusRequester.safeRequestFocus()
            }
            delay(80L)
            searchInputFocusRequester.safeRequestFocus()
        }
    }

    // Popup automatico se GitHub Releases segnala una nuova versione (dopo lo splash)
    if (!isSplashVisible) {
        GitHubUpdateDialog(
            state = gitHubState,
            onDismiss = viewModel::dismissUpdateDialog,
            onConfirmUpdate = { viewModel.performInAppUpdate(context) },
            onOpenGitHubTab = {
                requestedSettingsCategory = SettingsCategory.UPDATES
                requestedSettingsNonce++
                viewModel.selectTab(NavTab.SETTINGS)
            }
        )
    }

    val navItems = remember {
        listOf(
            Triple(NavTab.HOME, "Home", Icons.Default.Home),
            Triple(NavTab.SERIES, "Serie TV", Icons.Default.Tv),
            Triple(NavTab.MOVIES, "Film", Icons.Default.Movie),
            Triple(NavTab.NEW_POPULAR, "Top & Nuovi", Icons.Default.NewReleases),
            Triple(NavTab.MY_LIST, "La Mia Lista", Icons.Default.BookmarkBorder),
            Triple(NavTab.SETTINGS, "Impostazioni", Icons.Default.Settings)
        )
    }

    val watchlistIds = remember(watchlist) {
        watchlist.asSequence().map { it.id }.toHashSet()
    }
    val progressByKey = remember(continueWatching) {
        continueWatching.associateBy { it.id }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        val useTvNavigationRail = isTvDevice || maxWidth >= 600.dp || maxWidth > maxHeight

        // Focus iniziale per cambio scheda / selezione da menu / caricamento completato / uscita dallo splash
        LaunchedEffect(
            useTvNavigationRail,
            isCatalogInteractive,
            selectedTab,
            tabScrollResetNonce,
            catalogState is CatalogUiState.Success,
            catalogState is CatalogUiState.Error
        ) {
            if (useTvNavigationRail && isCatalogInteractive && !isSearchBarVisible) {
                val target = tabContentEntryFocusRequester ?: return@LaunchedEffect
                for (frame in 1..3) {
                    androidx.compose.runtime.withFrameNanos { }
                    target.safeRequestFocus()
                }
                delay(60L)
                target.safeRequestFocus()
            }
        }

        // Ripristino focus sull'ultima locandina alla chiusura di Dettagli / Player
        LaunchedEffect(isCatalogInteractive, useTvNavigationRail) {
            if (wasOverlayActive && isCatalogInteractive && useTvNavigationRail && !isSearchBarVisible) {
                val target = lastFocusedContentRequester.takeIfAttached() ?: tabContentEntryFocusRequester
                if (target != null) {
                    delay(120L)
                    target.safeRequestFocus()
                }
            }
            wasOverlayActive = !isCatalogInteractive
        }

        // Gestione tasto INDIETRO ottimale per Android TV / Google TV / Layout Orizzontale nel catalogo principale:
        // 1. Chiude menu rapido o ricerca se aperti
        // 2. Sposta prima il fuoco sulla barra laterale compatta (sulla voce attiva) se l'utente è sui contenuti
        // 3. Su schede secondarie torna alla Home; sulla Home chiede doppia pressione entro 2.4s
        BackHandler(enabled = isCatalogInteractive) {
            when {
                tvQuickActionEntry != null -> tvQuickActionEntry = null
                tvQuickActionItem != null -> tvQuickActionItem = null
                isSearchBarVisible -> {
                    isSearchBarVisible = false
                    viewModel.clearSearch()
                    if (!lastFocusedContentRequester.safeRequestFocus()) {
                        tabContentEntryFocusRequester.safeRequestFocus()
                    }
                }
                selectedTab == NavTab.HOME && platformState.selectedProvider != null -> {
                    viewModel.selectStreamingPlatform(null)
                }
                selectedTab == NavTab.MOVIES && movieGenreState.selectedGenre != null -> {
                    viewModel.selectMovieGenre(null)
                }
                useTvNavigationRail && !isRailFocused -> {
                    navRailSelectedFocusRequester.safeRequestFocus()
                }
                selectedTab != NavTab.HOME -> {
                    lastFocusedContentRequester = null
                    tabScrollResetNonce++
                    viewModel.selectTab(NavTab.HOME)
                }
                else -> {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressMs <= 2_400L) {
                        showExitHintBanner = false
                        (context as? Activity)?.finish()
                    } else {
                        lastBackPressMs = now
                        showExitHintBanner = true
                    }
                }
            }
        }

        // 1. LIVELLO BASE: Catalogo Principale (entra con dissolvenza dopo lo splash iniziale)
        AnimatedVisibility(
            visible = !isSplashVisible,
            enter = fadeIn(tween(180)),
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize()
        ) {
        Scaffold(
            containerColor = NetflixBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = isCatalogInteractive },
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
                                    lastFocusedContentRequester = null
                                    tabScrollResetNonce++
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixBlack)
            ) {
                val activeTopBarFocusRequester = if (useTvNavigationRail) {
                    if (isSearchBarVisible) searchInputFocusRequester else null
                } else {
                    topBarBrandFocusRequester
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = if (useTvNavigationRail) 72.dp else 0.dp)
                        .background(NetflixBlack)
                ) {
                    val topBarOffset = when {
                        !useTvNavigationRail -> 122.dp
                        isSearchBarVisible -> 72.dp
                        else -> 16.dp
                    }
                    val sectionKey = MainSectionKey(
                        isSearch = isSearchBarVisible && searchQuery.isNotBlank(),
                        tab = selectedTab,
                        catalogStage = when (catalogState) {
                            is CatalogUiState.Loading -> "loading"
                            is CatalogUiState.Error -> "error"
                            is CatalogUiState.Success -> "success"
                        }
                    )

                    AnimatedContent(
                        targetState = sectionKey,
                        transitionSpec = {
                            fadeIn(tween(120)).togetherWith(fadeOut(tween(90)))
                        },
                        label = "mainContentTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { currentSection ->
                        val isSectionActive = currentSection == sectionKey
                        val sectionSearchFirstFocus = if (isSectionActive) searchFirstItemFocusRequester else remember { FocusRequester() }
                        val sectionSettingsFirstFocus = if (isSectionActive) settingsFirstControlFocusRequester else remember { FocusRequester() }
                        val sectionHeroPlayFocus = if (isSectionActive) heroPlayFocusRequesters.getValue(currentSection.tab) else remember { FocusRequester() }
                        val sectionMovieGenreBarFocus = if (isSectionActive) movieGenreBarFocusRequester else remember { FocusRequester() }
                        val sectionPlatformStripFocus = if (isSectionActive) platformStripFocusRequester else remember { FocusRequester() }
                        val sectionFirstRowFocus = if (isSectionActive) firstRowFocusRequesters.getValue(currentSection.tab) else remember { FocusRequester() }
                        val sectionRetryFocus = if (isSectionActive) retryButtonFocusRequester else remember { FocusRequester() }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusProperties { canFocus = isSectionActive && isCatalogInteractive }
                        ) {
                        if (currentSection.isSearch) {
                            SearchResultsGrid(
                                query = searchQuery,
                                isSearching = isSearching,
                                results = searchResults,
                                isTvLayout = useTvNavigationRail,
                                firstItemFocusRequester = sectionSearchFirstFocus,
                                navRailFocusRequester = navRailSelectedFocusRequester,
                                upFocusRequester = searchInputFocusRequester,
                                onItemFocused = { if (isSectionActive) lastFocusedContentRequester = it },
                                onItemQuickPlay = { item -> viewModel.startPlayback(item) },
                                onItemLongClick = { item -> tvQuickActionItem = item },
                                onItemClick = viewModel::openDetails,
                                contentPadding = PaddingValues(
                                    top = topBarOffset + 46.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                                    start = if (useTvNavigationRail) 24.dp else 16.dp,
                                    end = if (useTvNavigationRail) 24.dp else 16.dp
                                )
                            )
                        } else if (currentSection.tab == NavTab.SETTINGS) {
                            SettingsScreen(
                                gitHubState = gitHubState,
                                streamLang = streamLang,
                                preferWebPlayer = preferWebPlayer,
                                bufferPreset = bufferPreset,
                                autoCheckUpdates = autoCheckUpdates,
                                continueWatchingCount = continueWatching.size,
                                watchlistCount = watchlist.size,
                                isTvLayout = useTvNavigationRail,
                                requestedCategory = requestedSettingsCategory,
                                requestedCategoryNonce = requestedSettingsNonce,
                                onSelectStreamLang = viewModel::setStreamLanguage,
                                onSetPreferWebPlayer = viewModel::setPreferWebPlayer,
                                onSelectBufferPreset = viewModel::setBufferPreset,
                                onSetAutoCheckUpdates = viewModel::setAutoCheckUpdates,
                                onRefreshCatalog = { viewModel.loadCatalog(forceRefresh = true) },
                                onClearContinueWatching = viewModel::clearAllContinueWatching,
                                onRepoSlugChange = viewModel::updateGitHubRepoSlug,
                                onCheckUpdates = { slug -> viewModel.checkForGitHubUpdates(slug, showPopupIfAvailable = false) },
                                onTriggerUpdate = { viewModel.performInAppUpdate(context) },
                                onUpdateTmdbKey = viewModel::updateTmdbApiKey,
                                onResetTmdbKey = viewModel::resetTmdbApiKeyToDefault,
                                backgroundPrefetchEnabled = backgroundPrefetchEnabled,
                                cacheStats = cacheStats,
                                isBackgroundCatalogRefreshing = isBackgroundCatalogRefreshing,
                                cacheClearedFeedbackNonce = cacheClearedFeedbackNonce,
                                onSetBackgroundPrefetch = viewModel::setBackgroundPrefetchEnabled,
                                onClearLocalCache = viewModel::clearLocalCache,
                                firstControlFocusRequester = sectionSettingsFirstFocus,
                                navRailFocusRequester = navRailSelectedFocusRequester,
                                upFocusRequester = activeTopBarFocusRequester,
                                onRememberContentFocus = { requester -> if (isSectionActive) lastFocusedContentRequester = requester },
                                tabScrollResetNonce = tabScrollResetNonce,
                                contentPadding = PaddingValues(
                                    top = topBarOffset,
                                    bottom = innerPadding.calculateBottomPadding()
                                )
                            )
                        } else if (currentSection.tab == NavTab.MY_LIST) {
                            MyListScreen(
                                watchlist = watchlist,
                                continueWatching = continueWatching,
                                isTvLayout = useTvNavigationRail,
                                firstRowFocusRequester = sectionFirstRowFocus,
                                navRailFocusRequester = navRailSelectedFocusRequester,
                                topBarFocusRequester = activeTopBarFocusRequester,
                                tabScrollResetNonce = tabScrollResetNonce,
                                onBrowseCatalog = { viewModel.selectTab(NavTab.HOME) },
                                onOpenItem = viewModel::openDetails,
                                onPlayItem = { viewModel.startPlayback(it) },
                                onResumeContinueWatching = viewModel::resumeFromContinueWatching,
                                onRemoveContinueWatching = { viewModel.removeContinueWatching(it.id) },
                                onTvLongPressContinueWatching = { entry -> tvQuickActionEntry = entry },
                                onTvLongPressPoster = { item -> tvQuickActionItem = item },
                                onRememberContentFocus = { requester -> if (isSectionActive) lastFocusedContentRequester = requester },
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
                                                    .safeFocusRequester(sectionRetryFocus)
                                                    .onPreviewKeyEvent { keyEvent ->
                                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                                            keyEvent.key == Key.DirectionLeft
                                                        ) {
                                                            navRailSelectedFocusRequester.safeRequestFocus()
                                                        } else {
                                                            false
                                                        }
                                                    }
                                                    .focusProperties {
                                                        navRailSelectedFocusRequester.takeIfAttached()?.let { left = it }
                                                        topBarBrandFocusRequester.takeIfAttached()?.let { up = it }
                                                    }
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
                                        selectedTab = currentSection.tab,
                                        continueWatching = continueWatching,
                                        watchlist = watchlist,
                                        movieGenreState = movieGenreState,
                                        platformState = platformState,
                                        isTvLayout = useTvNavigationRail,
                                        heroPlayFocusRequester = sectionHeroPlayFocus,
                                        movieGenreBarFocusRequester = sectionMovieGenreBarFocus,
                                        platformStripFocusRequester = sectionPlatformStripFocus,
                                        firstRowFocusRequester = sectionFirstRowFocus,
                                        navRailFocusRequester = navRailSelectedFocusRequester,
                                        topBarFocusRequester = activeTopBarFocusRequester,
                                        onSelectMovieGenre = viewModel::selectMovieGenre,
                                        onRetryMovieGenre = viewModel::retrySelectedMovieGenre,
                                        onSelectStreamingPlatform = viewModel::selectStreamingPlatform,
                                        onSelectPlatformMediaTypeFilter = viewModel::selectPlatformMediaTypeFilter,
                                        onRetryStreamingPlatform = viewModel::retrySelectedStreamingPlatform,
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
                                        onTvLongPressContinueWatching = { entry ->
                                            tvQuickActionEntry = entry
                                        },
                                        onTvLongPressPoster = { item ->
                                            tvQuickActionItem = item
                                        },
                                        onRememberContentFocus = { requester ->
                                            lastFocusedContentRequester = requester
                                        },
                                        tabScrollResetNonce = tabScrollResetNonce,
                                        bottomPadding = innerPadding.calculateBottomPadding()
                                    )
                                }
                            }
                        }
                        }
                    }

                    // Top bar flottante stile Netflix (compatibile D-pad Android TV / Fire Stick)
                    NetflixTopBar(
                        selectedTab = selectedTab,
                        onSelectTab = { tab ->
                            isSearchBarVisible = false
                            viewModel.clearSearch()
                            lastFocusedContentRequester = null
                            tabScrollResetNonce++
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
                                requestedSettingsCategory = SettingsCategory.UPDATES
                                requestedSettingsNonce++
                                viewModel.selectTab(NavTab.SETTINGS)
                            }
                        },
                        isTvLayout = useTvNavigationRail,
                        searchFocusRequester = searchInputFocusRequester,
                        brandLogoFocusRequester = topBarBrandFocusRequester,
                        navRailFocusRequester = navRailSelectedFocusRequester,
                        downToContentFocusRequester = when {
                            isSearchBarVisible && searchQuery.isNotBlank() -> searchFirstItemFocusRequester
                            selectedTab == NavTab.MOVIES && catalogState is CatalogUiState.Success -> movieGenreBarFocusRequester
                            selectedTab == NavTab.HOME && platformState.selectedProvider != null && catalogState is CatalogUiState.Success -> platformStripFocusRequester
                            else -> activeContentEntryFocusRequester
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                // Scrim morbido quando la barra laterale compatta TV è espansa
                if (useTvNavigationRail) {
                    AnimatedVisibility(
                        visible = isRailFocused,
                        enter = fadeIn(tween(110)),
                        exit = fadeOut(tween(90)),
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(8f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NetflixBlack.copy(alpha = 0.56f))
                        )
                    }

                    TvExpandableNavigationRail(
                        navItems = navItems,
                        selectedTab = selectedTab,
                        isSearchOpen = isSearchBarVisible,
                        isExpanded = isRailFocused,
                        selectedItemFocusRequester = navRailSelectedFocusRequester,
                        rightToContentFocusRequester = if (isSearchBarVisible) {
                            (if (searchQuery.isNotBlank()) lastFocusedContentRequester.takeIfAttached() else null)
                                ?: searchInputFocusRequester
                        } else {
                            activeContentEntryFocusRequester
                        },
                        fallbackRightFocusRequester = activeTopBarFocusRequester,
                        onFocusChanged = { isRailFocused = it },
                        onToggleSearch = {
                            val willOpen = !isSearchBarVisible
                            isSearchBarVisible = willOpen
                            lastFocusedContentRequester = null
                            if (!willOpen) {
                                viewModel.clearSearch()
                                (tabContentEntryFocusRequester.takeIfAttached()
                                    ?: heroPlayFocusRequester.takeIfAttached()
                                    ?: firstRowFocusRequester.takeIfAttached()).safeRequestFocus()
                            }
                        },
                        onSelectTab = { tab ->
                            val isSameTab = (selectedTab == tab && !isSearchBarVisible)
                            isSearchBarVisible = false
                            viewModel.clearSearch()
                            lastFocusedContentRequester = null
                            tabScrollResetNonce++
                            viewModel.selectTab(tab)
                            if (isSameTab) {
                                (tabContentEntryFocusRequester.takeIfAttached()
                                    ?: heroPlayFocusRequester.takeIfAttached()
                                    ?: firstRowFocusRequester.takeIfAttached()).safeRequestFocus()
                            }
                        },
                        onMoveFocusRight = {
                            if (isSearchBarVisible) {
                                (if (searchQuery.isNotBlank()) lastFocusedContentRequester.safeRequestFocus() else false) ||
                                    searchInputFocusRequester.safeRequestFocus() ||
                                    searchFirstItemFocusRequester.safeRequestFocus()
                            } else {
                                lastFocusedContentRequester.safeRequestFocus() ||
                                    tabContentEntryFocusRequester.safeRequestFocus() ||
                                    heroPlayFocusRequester.safeRequestFocus() ||
                                    firstRowFocusRequester.safeRequestFocus()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .zIndex(10f)
                    )
                }

                // Banner discreto "Premi ancora Indietro per uscire" su Android TV / Google TV
                AnimatedVisibility(
                    visible = showExitHintBanner,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 2 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding() + 28.dp)
                        .zIndex(14f)
                ) {
                    Surface(
                        color = NetflixCardSurface.copy(alpha = 0.96f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.8f)),
                        shadowElevation = 12.dp
                    ) {
                        Text(
                            text = "Premi ancora Indietro per uscire da VixFlix",
                            style = MaterialTheme.typography.labelLarge,
                            color = NetflixWhite,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        // Menu Rapido TV (Long-Press OK su "Continua a guardare")
        tvQuickActionEntry?.let { entry ->
            TvContinueWatchingQuickDialog(
                entry = entry,
                onDismiss = { tvQuickActionEntry = null },
                onResume = {
                    tvQuickActionEntry = null
                    viewModel.resumeFromContinueWatching(entry)
                },
                onOpenDetails = {
                    tvQuickActionEntry = null
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
                onRestart = {
                    tvQuickActionEntry = null
                    viewModel.startPlayback(
                        item = MediaItem(
                            id = entry.tmdbId,
                            mediaType = entry.mediaType,
                            title = entry.title,
                            overview = entry.overview,
                            posterPath = entry.posterPath,
                            backdropPath = entry.backdropPath,
                            voteAverage = entry.voteAverage,
                            releaseYear = entry.releaseYear
                        ),
                        seasonNumber = entry.seasonNumber,
                        episodeNumber = entry.episodeNumber,
                        episodeTitle = entry.episodeTitle,
                        explicitStartPositionMs = 0L
                    )
                },
                onRemove = {
                    tvQuickActionEntry = null
                    viewModel.removeContinueWatching(entry.id)
                }
            )
        }

        // Menu Rapido TV (Long-Press OK su qualsiasi locandina del catalogo)
        tvQuickActionItem?.let { item ->
            val inWatchlist = item.uniqueKey in watchlistIds
            TvPosterQuickActionDialog(
                item = item,
                isInWatchlist = inWatchlist,
                onDismiss = { tvQuickActionItem = null },
                onOpenDetails = {
                    tvQuickActionItem = null
                    viewModel.openDetails(item)
                },
                onQuickPlay = {
                    tvQuickActionItem = null
                    viewModel.startPlayback(item)
                },
                onToggleWatchlist = {
                    tvQuickActionItem = null
                    viewModel.toggleWatchlist(item)
                }
            )
        }
        }

        // 2. LIVELLO INTERMEDIO: Schermata Dettagli & Episodi (con stack cronologico tra Titoli Simili)
        androidx.compose.animation.AnimatedVisibility(
            visible = isDetailVisible && !isPlayerVisible,
            enter = fadeIn(tween(130)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = !isPlayerVisible }
        ) {
            val currentDetail = lastDetailSnapshot
            if (currentDetail != null) {
                val savedEntry = progressByKey[currentDetail.mediaItem.uniqueKey]
                val inList = currentDetail.mediaItem.uniqueKey in watchlistIds
                MediaDetailScreen(
                    state = currentDetail,
                    savedProgress = savedEntry,
                    isInWatchlist = inList,
                    bufferPreset = bufferPreset,
                    detailStackDepth = detailStackSize,
                    previousDetailTitle = previousDetailTitle,
                    isInteractive = isDetailVisible && !isPlayerVisible,
                    onBack = viewModel::navigateBackDetail,
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
            }
        }

        // 3. LIVELLO SUPERIORE: Player Video a Tutto Schermo (ExoPlayer + VixSrc) con ingresso fluido e chiusura istantanea
        androidx.compose.animation.AnimatedVisibility(
            visible = isPlayerVisible,
            enter = fadeIn(tween(220)),
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize()
        ) {
            playbackSession?.let { activeSession ->
                VixPlayerScreen(
                    session = activeSession,
                    streamLang = streamLang,
                    bufferPreset = bufferPreset,
                    vixSrcResolver = viewModel.repository.vixSrcResolver,
                    onDynamicHlsFound = viewModel::onDynamicHlsIntercepted,
                    onTogglePlayerMode = viewModel::togglePlayerMode,
                    onToggleLanguage = viewModel::setStreamLanguage,
                    onProgressUpdate = viewModel::onPlaybackProgressUpdate,
                    onNextEpisode = viewModel::playNextEpisode,
                    onClose = viewModel::closePlayer,
                    onInvalidateAndRetryStream = viewModel::invalidateAndRetryCurrentStream
                )
            }
        }

        // 4. SPLASH SCREEN ANIMATO IN FASE DI AVVIO (sincronizzato con Theme.VixFlix.Splash)
        androidx.compose.animation.AnimatedVisibility(
            visible = isSplashVisible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(380)) + scaleOut(
                targetScale = 1.04f,
                animationSpec = tween(380, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(200f)
        ) {
            VixFlixSplashScreen(
                isCatalogLoading = catalogState is CatalogUiState.Loading
            )
        }
    }
}

@Composable
private fun TvExpandableNavigationRail(
    navItems: List<Triple<NavTab, String, ImageVector>>,
    selectedTab: NavTab,
    isSearchOpen: Boolean,
    isExpanded: Boolean,
    selectedItemFocusRequester: FocusRequester,
    rightToContentFocusRequester: FocusRequester?,
    fallbackRightFocusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectTab: (NavTab) -> Unit,
    onMoveFocusRight: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val railWidth by animateDpAsState(
        targetValue = if (isExpanded) 224.dp else 72.dp,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "tvExpandableRailWidth"
    )
    val railScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(if (isExpanded) NetflixCardSurface else NetflixDarkSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 12.dp, start = 10.dp, end = 10.dp)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .focusGroup()
            .focusProperties {
                @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                enter = { selectedItemFocusRequester }
                (rightToContentFocusRequester.takeIfAttached() ?: fallbackRightFocusRequester.takeIfAttached())?.let {
                    right = it
                }
            }
            .testTag("tv_navigation_rail")
    ) {
        // Mini badge brand in cima alla barra laterale: al click quando il menu non è selezionato porta subito sulla voce attiva
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    selectedItemFocusRequester.safeRequestFocus()
                }
                .padding(horizontal = if (isExpanded) 8.dp else 0.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetflixRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V",
                    fontFamily = BebasNeueFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(110)) + expandHorizontally(tween(110)),
                exit = fadeOut(tween(80)) + shrinkHorizontally(tween(90))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "VIXFLIX TV",
                        fontFamily = BebasNeueFontFamily,
                        fontSize = 24.sp,
                        letterSpacing = 1.5.sp,
                        color = NetflixWhite,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(railScrollState)
        ) {
            val searchDedicatedRequester = remember { FocusRequester() }
            val tabDedicatedRequesters = remember(navItems) {
                navItems.associate { (tab, _, _) -> tab to FocusRequester() }
            }
            val resolveRailRequester: (Int) -> FocusRequester = { railIndex ->
                if (railIndex == 0) {
                    if (isSearchOpen) selectedItemFocusRequester else searchDedicatedRequester
                } else {
                    val tab = navItems[railIndex - 1].first
                    if (!isSearchOpen && tab == selectedTab) {
                        selectedItemFocusRequester
                    } else {
                        tabDedicatedRequesters.getValue(tab)
                    }
                }
            }
            val totalRailCount = navItems.size + 1

            // Voce Cerca integrata nella barra laterale (solo visualizzazione orizzontale / TV)
            var searchFocused by remember { mutableStateOf(false) }
            var searchPressed by remember { mutableStateOf(false) }
            val searchScale by animateFloatAsState(
                targetValue = when {
                    searchPressed -> 0.97f
                    searchFocused -> 1.03f
                    else -> 1f
                },
                animationSpec = tween(durationMillis = 100),
                label = "searchRailScale"
            )
            LaunchedEffect(searchFocused) {
                if (searchFocused && railScrollState.value > 0) {
                    runCatching { railScrollState.animateScrollTo(0) }
                }
            }
            val searchBgColor = when {
                searchFocused -> NetflixRed
                isSearchOpen -> NetflixElevatedSurface
                else -> Color.Transparent
            }
            val searchTint = when {
                searchFocused -> Color.White
                isSearchOpen -> NetflixRed
                else -> NetflixMutedGray
            }
            val searchLabel = if (isSearchOpen) "Chiudi Ricerca" else "Cerca"
            val searchRailRequester = resolveRailRequester(0)
            val firstTabRailRequester = if (totalRailCount > 1) resolveRailRequester(1) else null

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .graphicsLayer {
                        scaleX = searchScale
                        scaleY = searchScale
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(searchBgColor)
                    .then(
                        if (searchFocused) {
                            Modifier.border(2.dp, NetflixWhite, RoundedCornerShape(12.dp))
                        } else if (isSearchOpen) {
                            Modifier.border(1.dp, NetflixRed.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        }
                    )
                    .safeFocusRequester(searchRailRequester)
                    .focusProperties {
                        (rightToContentFocusRequester.takeIfAttached() ?: fallbackRightFocusRequester.takeIfAttached())?.let {
                            right = it
                        }
                        firstTabRailRequester.takeIfAttached()?.let { down = it }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        val isCenterKey = keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> {
                                when {
                                    isCenterKey -> {
                                        searchPressed = true
                                        true
                                    }
                                    keyEvent.key == Key.DirectionRight || keyEvent.key == Key.Escape -> onMoveFocusRight()
                                    keyEvent.key == Key.DirectionDown -> firstTabRailRequester.safeRequestFocus()
                                    keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionLeft -> true
                                    else -> false
                                }
                            }
                            KeyEventType.KeyUp -> {
                                if (isCenterKey) {
                                    searchPressed = false
                                    onToggleSearch()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
                    .onFocusChanged { state ->
                        if (!state.isFocused) searchPressed = false
                        if (searchFocused != state.isFocused) {
                            searchFocused = state.isFocused
                            DpadFocusObserverRegistry.onNodeFocusChanged(
                                scope = DpadFocusScope.MAIN_NAVIGATION,
                                tag = "search_toggle_button",
                                isFocused = state.isFocused
                            )
                        }
                    }
                    .focusable()
                    .clickable { onToggleSearch() }
                    .padding(horizontal = 12.dp)
                    .testTag("search_toggle_button"),
                contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
            ) {
                if (isSearchOpen && !searchFocused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(3.5.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(NetflixRed)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.size(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Cerca su TMDB",
                            tint = if (searchFocused) Color.White else searchTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn(tween(100)) + expandHorizontally(tween(110)),
                        exit = fadeOut(tween(80)) + shrinkHorizontally(tween(90))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = searchLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSearchOpen || searchFocused) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSearchOpen || searchFocused) NetflixWhite else NetflixLightGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            navItems.forEachIndexed { navIdx, (tab, label, icon) ->
                val railIdx = navIdx + 1
                val isSelected = selectedTab == tab && !isSearchOpen
                var itemFocused by remember { mutableStateOf(false) }
                var itemPressed by remember { mutableStateOf(false) }
                val itemScale by animateFloatAsState(
                    targetValue = when {
                        itemPressed -> 0.97f
                        itemFocused -> 1.03f
                        else -> 1f
                    },
                    animationSpec = tween(durationMillis = 100),
                    label = "railTabScale_${tab.name}"
                )
                val itemRailRequester = resolveRailRequester(railIdx)
                val upRailRequester = resolveRailRequester(railIdx - 1)
                val downRailRequester = if (railIdx + 1 < totalRailCount) resolveRailRequester(railIdx + 1) else null

                val bgColor = when {
                    itemFocused -> NetflixRed
                    isSelected -> NetflixElevatedSurface
                    else -> Color.Transparent
                }
                val contentTint = when {
                    itemFocused -> Color.White
                    isSelected -> NetflixRed
                    else -> NetflixMutedGray
                }

                val activateAndMoveRight = {
                    onSelectTab(tab)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .graphicsLayer {
                            scaleX = itemScale
                            scaleY = itemScale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .then(
                            if (itemFocused) {
                                Modifier.border(2.dp, NetflixWhite, RoundedCornerShape(12.dp))
                            } else if (isSelected) {
                                Modifier.border(1.dp, NetflixRed.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        )
                        .safeFocusRequester(itemRailRequester)
                        .focusProperties {
                            (rightToContentFocusRequester.takeIfAttached() ?: fallbackRightFocusRequester.takeIfAttached())?.let {
                                right = it
                            }
                            upRailRequester.takeIfAttached()?.let { up = it }
                            downRailRequester.takeIfAttached()?.let { down = it }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            val isCenterKey = keyEvent.key == Key.DirectionCenter ||
                                keyEvent.key == Key.Enter ||
                                keyEvent.key == Key.NumPadEnter
                            when (keyEvent.type) {
                                KeyEventType.KeyDown -> {
                                    when {
                                        isCenterKey -> {
                                            itemPressed = true
                                            true
                                        }
                                        keyEvent.key == Key.DirectionRight || keyEvent.key == Key.Escape -> onMoveFocusRight()
                                        keyEvent.key == Key.DirectionUp -> upRailRequester.safeRequestFocus()
                                        keyEvent.key == Key.DirectionDown -> downRailRequester?.safeRequestFocus() ?: true
                                        keyEvent.key == Key.DirectionLeft -> true
                                        else -> false
                                    }
                                }
                                KeyEventType.KeyUp -> {
                                    if (isCenterKey) {
                                        itemPressed = false
                                        activateAndMoveRight()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        }
                        .onFocusChanged { state ->
                            if (!state.isFocused) itemPressed = false
                            if (itemFocused != state.isFocused) {
                                itemFocused = state.isFocused
                                DpadFocusObserverRegistry.onNodeFocusChanged(
                                    scope = DpadFocusScope.MAIN_NAVIGATION,
                                    tag = "tv_rail_${tab.name.lowercase()}",
                                    isFocused = state.isFocused
                                )
                            }
                        }
                        .focusable()
                        .clickable { activateAndMoveRight() }
                        .padding(horizontal = 12.dp)
                        .testTag("tv_rail_${tab.name.lowercase()}"),
                    contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
                ) {
                    if (isSelected && !itemFocused) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(3.5.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(NetflixRed)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.size(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (itemFocused) Color.White else contentTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn(tween(100)) + expandHorizontally(tween(110)),
                            exit = fadeOut(tween(80)) + shrinkHorizontally(tween(90))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected || itemFocused) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected || itemFocused) NetflixWhite else NetflixLightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvContinueWatchingQuickDialog(
    entry: WatchProgressEntity,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onOpenDetails: () -> Unit,
    onRestart: () -> Unit,
    onRemove: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(entry.id) {
        delay(60L)
        firstFocus.safeRequestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
            border = BorderStroke(1.5.dp, NetflixElevatedSurface),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NetflixWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = if (entry.mediaType == "tv" && entry.seasonNumber != null && entry.episodeNumber != null) {
                    "S${entry.seasonNumber}:E${entry.episodeNumber} • Fermo a ${formatSecondsToMmSs(entry.startSeconds)}"
                } else {
                    "Fermo a ${formatSecondsToMmSs(entry.startSeconds)}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = NetflixMatchGreen
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixWhite, contentColor = NetflixBlack),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .safeFocusRequester(firstFocus)
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedBorderColor = NetflixRed, focusedScale = 1.03f)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Riprendi da ${formatSecondsToMmSs(entry.startSeconds)}", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenDetails,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.03f)
                ) {
                    Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scheda Dettagli & Episodi")
                }

                OutlinedButton(
                    onClick = onRestart,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.03f)
                ) {
                    Icon(imageVector = Icons.Default.Replay, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ricomincia dall'inizio")
                }

                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixRed),
                    border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedBorderColor = NetflixRed, focusedScale = 1.03f)
                ) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rimuovi da Continua a guardare")
                }
            }
        }
    }
}

@Composable
private fun TvPosterQuickActionDialog(
    item: MediaItem,
    isInWatchlist: Boolean,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit,
    onQuickPlay: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(item.uniqueKey) {
        delay(60L)
        firstFocus.safeRequestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
            border = BorderStroke(1.5.dp, NetflixElevatedSurface),
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NetflixWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${if (item.isSeries) "Serie TV" else "Film"} • ${item.releaseYear}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NetflixLightGray
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onQuickPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixWhite, contentColor = NetflixBlack),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .safeFocusRequester(firstFocus)
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedBorderColor = NetflixRed, focusedScale = 1.03f)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Riproduci Subito", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenDetails,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.03f)
                ) {
                    Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apri Scheda & Episodi")
                }

                OutlinedButton(
                    onClick = onToggleWatchlist,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isInWatchlist) NetflixMatchGreen else NetflixWhite
                    ),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.03f)
                ) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isInWatchlist) "Rimuovi da La Mia Lista" else "Aggiungi a La Mia Lista")
                }
            }
        }
    }
}

private data class MainSectionKey(
    val isSearch: Boolean,
    val tab: NavTab,
    val catalogStage: String
)

private data class CatalogNavTarget(
    val key: String,
    val lazyIndex: Int,
    val requester: FocusRequester,
    val resetToTop: Boolean = false
)

private data class CatalogRowSpec(
    val key: String,
    val title: String,
    val items: List<MediaItem>,
    val isTop10: Boolean = false
)

@Composable
private fun CatalogFeedContent(
    feed: CatalogFeed,
    selectedTab: NavTab,
    continueWatching: List<WatchProgressEntity>,
    watchlist: List<WatchlistEntity>,
    movieGenreState: MovieGenreFilterUiState,
    platformState: StreamingPlatformUiState,
    isTvLayout: Boolean,
    heroPlayFocusRequester: FocusRequester,
    movieGenreBarFocusRequester: FocusRequester,
    platformStripFocusRequester: FocusRequester,
    firstRowFocusRequester: FocusRequester,
    navRailFocusRequester: FocusRequester,
    topBarFocusRequester: FocusRequester?,
    onSelectMovieGenre: (TmdbGenreDto?) -> Unit,
    onRetryMovieGenre: () -> Unit,
    onSelectStreamingPlatform: (TmdbWatchProviderDto?) -> Unit,
    onSelectPlatformMediaTypeFilter: (PlatformFilterMediaType) -> Unit,
    onRetryStreamingPlatform: () -> Unit,
    onPlayItem: (MediaItem) -> Unit,
    onOpenDetails: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit,
    onResumeContinueWatching: (WatchProgressEntity) -> Unit,
    onInfoContinueWatching: (WatchProgressEntity) -> Unit,
    onRemoveContinueWatching: (WatchProgressEntity) -> Unit,
    onTvLongPressContinueWatching: (WatchProgressEntity) -> Unit = {},
    onTvLongPressPoster: (MediaItem) -> Unit = {},
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    tabScrollResetNonce: Int = 0,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    val activeMovieGenre = if (selectedTab == NavTab.MOVIES) movieGenreState.selectedGenre else null
    val activePlatform = if (selectedTab == NavTab.HOME) platformState.selectedProvider else null

    val heroItem = remember(selectedTab, feed) {
        when (selectedTab) {
            NavTab.SERIES -> feed.trendingTv.firstOrNull() ?: feed.popularTv.firstOrNull() ?: feed.heroBanner
            NavTab.MOVIES -> feed.trendingMovies.firstOrNull() ?: feed.popularMovies.firstOrNull() ?: feed.heroBanner
            else -> feed.heroBanner
        }
    }

    val filteredContinueWatching = remember(selectedTab, continueWatching) {
        when (selectedTab) {
            NavTab.HOME -> continueWatching
            NavTab.SERIES -> continueWatching.filter { it.mediaType == "tv" }
            NavTab.MOVIES -> continueWatching.filter { it.mediaType == "movie" }
            else -> emptyList()
        }
    }
    val watchlistIds = remember(watchlist) {
        watchlist.asSequence().map { it.id }.toHashSet()
    }
    val progressByKey = remember(continueWatching) {
        continueWatching.associateBy { it.id }
    }

    val showHero = selectedTab != NavTab.NEW_POPULAR && activeMovieGenre == null && activePlatform == null
    val hasContinueWatching = filteredContinueWatching.isNotEmpty() && activeMovieGenre == null && activePlatform == null
    val continueWatchingFocusRequester = remember { FocusRequester() }
    val catalogListState = rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(
        selectedTab,
        activeMovieGenre?.id,
        activePlatform?.providerId,
        platformState.mediaTypeFilter,
        tabScrollResetNonce
    ) {
        runCatching { catalogListState.scrollToItem(0, 0) }
    }

    val dynamicRowRequesters = remember(
        selectedTab,
        activeMovieGenre?.id,
        activePlatform?.providerId,
        platformState.mediaTypeFilter
    ) {
        mutableMapOf<String, FocusRequester>()
    }
    val gridRowLastCol = remember(
        selectedTab,
        activeMovieGenre?.id,
        activePlatform?.providerId,
        platformState.mediaTypeFilter
    ) {
        androidx.compose.runtime.mutableStateMapOf<String, Int>()
    }
    val clearGenreFilterFocusRequester = remember { FocusRequester() }
    fun requesterForKey(key: String, overrideRequester: FocusRequester? = null): FocusRequester {
        if (overrideRequester != null) return overrideRequester
        return dynamicRowRequesters.getOrPut(key) { FocusRequester() }
    }
    fun gridCellRequester(
        rowKey: String,
        colIdx: Int,
        rowZeroColZeroRequester: FocusRequester? = null
    ): FocusRequester {
        if (colIdx == 0 && rowZeroColZeroRequester != null) {
            return rowZeroColZeroRequester
        }
        return dynamicRowRequesters.getOrPut("${rowKey}_col_$colIdx") { FocusRequester() }
    }

    val (seriesTrendingRow, seriesPopularRow, seriesTopRated, seriesBinge) = remember(feed.trendingTv, feed.popularTv) {
        val uniqueTrending = feed.trendingTv.distinctBy { it.uniqueKey }
        val trendingSplit = if (uniqueTrending.size >= 6) (uniqueTrending.size + 1) / 2 else uniqueTrending.size
        val rowTrending = uniqueTrending.take(trendingSplit)
        val rowBinge = uniqueTrending.drop(trendingSplit)

        val trendingKeys = uniqueTrending.mapTo(hashSetOf()) { it.uniqueKey }
        val uniquePopular = feed.popularTv
            .distinctBy { it.uniqueKey }
            .filterNot { it.uniqueKey in trendingKeys }
            .ifEmpty { feed.popularTv.distinctBy { it.uniqueKey } }
        val popularSplit = if (uniquePopular.size >= 6) (uniquePopular.size + 1) / 2 else uniquePopular.size
        val rowPopular = uniquePopular.take(popularSplit)
        val rowTopRated = uniquePopular.drop(popularSplit).sortedByDescending { it.voteAverage }

        listOf(rowTrending, rowPopular, rowTopRated, rowBinge)
    }

    val navigateToTarget: (CatalogNavTarget) -> Boolean = { target ->
        if (target.resetToTop) {
            coroutineScope.launch {
                runCatching { catalogListState.scrollToItem(0, 0) }
                for (attempt in 1..6) {
                    androidx.compose.runtime.withFrameNanos { }
                    if (target.requester.safeRequestFocus()) {
                        break
                    }
                }
                androidx.compose.runtime.withFrameNanos { }
                if (catalogListState.firstVisibleItemIndex != 0 || catalogListState.firstVisibleItemScrollOffset != 0) {
                    runCatching { catalogListState.scrollToItem(0, 0) }
                }
            }
            target.requester.safeRequestFocus()
            true
        } else {
            val isTargetFullyVisible = catalogListState.layoutInfo.visibleItemsInfo.any { info ->
                info.index == target.lazyIndex &&
                    info.offset >= 0 &&
                    (info.offset + info.size) <= catalogListState.layoutInfo.viewportEndOffset
            }
            val focusedImmediately = if (isTargetFullyVisible) {
                target.requester.safeRequestFocus()
            } else {
                false
            }
            if (focusedImmediately) {
                true
            } else {
                coroutineScope.launch {
                    runCatching {
                        catalogListState.scrollToItem(target.lazyIndex, 0)
                    }
                    for (attempt in 1..6) {
                        androidx.compose.runtime.withFrameNanos { }
                        if (target.requester.safeRequestFocus()) {
                            break
                        }
                    }
                }
                true
            }
        }
    }

    val handleTopBoundaryUp: () -> Boolean = {
        if (catalogListState.firstVisibleItemIndex != 0 || catalogListState.firstVisibleItemScrollOffset != 0) {
            coroutineScope.launch {
                runCatching { catalogListState.scrollToItem(0, 0) }
            }
        }
        topBarFocusRequester.safeRequestFocus()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridColumns = when {
            maxWidth >= 1240.dp -> 6
            maxWidth >= 960.dp -> 5
            maxWidth >= 680.dp -> 4
            maxWidth >= 420.dp -> 3
            else -> 2
        }

        val genreRows = remember(movieGenreState.movies, gridColumns) {
            movieGenreState.movies
                .distinctBy { it.uniqueKey }
                .chunked(gridColumns)
        }

        val platformRows = remember(platformState.displayedTitles, gridColumns) {
            platformState.displayedTitles
                .distinctBy { it.uniqueKey }
                .chunked(gridColumns)
        }

        val homeCatalogRows = remember(feed) {
            listOf(
                CatalogRowSpec("row_top_10", "Top 10 in Italia Oggi", feed.top10Today, isTop10 = true),
                CatalogRowSpec("row_trending_movies", "Film di Tendenza su TMDB", feed.trendingMovies),
                CatalogRowSpec("row_trending_tv", "Serie TV del Momento", feed.trendingTv),
                CatalogRowSpec("row_popular_movies", "Film Popolari su VixFlix", feed.popularMovies),
                CatalogRowSpec("row_popular_tv", "Serie TV Acclamate", feed.popularTv),
                CatalogRowSpec("row_now_playing", "Ora al Cinema", feed.nowPlayingMovies),
                CatalogRowSpec("row_top_rated", "Più Votati dalla Critica", feed.topRatedMovies),
                CatalogRowSpec("row_action", "Azione & Adrenalina", feed.actionMovies),
                CatalogRowSpec("row_scifi", "Fantascienza & Mondi Futuri", feed.sciFiMovies)
            ).filter { it.items.isNotEmpty() }
        }

        val seriesCatalogRows = remember(seriesTrendingRow, seriesPopularRow, seriesTopRated, seriesBinge) {
            listOf(
                CatalogRowSpec("tv_trending", "Serie TV in Tendenza", seriesTrendingRow),
                CatalogRowSpec("tv_popular", "Serie TV Popolari", seriesPopularRow),
                CatalogRowSpec("tv_top_rated", "Serie TV Più Votate", seriesTopRated),
                CatalogRowSpec("tv_binge", "Maratone Serie TV Consigliate", seriesBinge)
            ).filter { it.items.isNotEmpty() }
        }

        val moviesCatalogRows = remember(feed) {
            listOf(
                CatalogRowSpec("movies_trending", "Film di Tendenza", feed.trendingMovies),
                CatalogRowSpec("movies_now_playing", "Ora al Cinema", feed.nowPlayingMovies),
                CatalogRowSpec("movies_popular", "Film Popolari", feed.popularMovies),
                CatalogRowSpec("movies_top_rated", "Film Più Votati", feed.topRatedMovies),
                CatalogRowSpec("movies_action", "Azione & Avventura", feed.actionMovies),
                CatalogRowSpec("movies_scifi", "Fantascienza", feed.sciFiMovies)
            ).filter { it.items.isNotEmpty() }
        }

        val newPopularCatalogRows = remember(feed) {
            listOf(
                CatalogRowSpec("new_top10", "Top 10 in Italia Oggi", feed.top10Today, isTop10 = true),
                CatalogRowSpec("new_cinema", "Nuove Uscite al Cinema", feed.nowPlayingMovies),
                CatalogRowSpec("new_tv", "Serie TV Nuove e Popolari", feed.trendingTv),
                CatalogRowSpec("new_top_rated", "Più Votati dalla Critica", feed.topRatedMovies)
            ).filter { it.items.isNotEmpty() }
        }

        // Costruisce la catena ordinata di tutte le sezioni focusabili con il loro indice esatto nella LazyColumn
        val navTargets = mutableListOf<CatalogNavTarget>()
        var nextLazyIdx = 0

        if (selectedTab == NavTab.MOVIES) {
            nextLazyIdx++ // movies_top_spacer
            navTargets.add(
                CatalogNavTarget(
                    key = "movies_genre_chip_bar",
                    lazyIndex = nextLazyIdx++,
                    requester = movieGenreBarFocusRequester,
                    resetToTop = true
                )
            )
        }

        if (selectedTab == NavTab.HOME && activePlatform != null) {
            nextLazyIdx++ // home_platform_top_spacer
        }

        if (showHero) {
            navTargets.add(
                CatalogNavTarget(
                    key = "hero",
                    lazyIndex = nextLazyIdx++,
                    requester = heroPlayFocusRequester,
                    resetToTop = true
                )
            )
        } else if (selectedTab == NavTab.NEW_POPULAR) {
            nextLazyIdx++ // new_popular_top_spacer
        }

        if (hasContinueWatching) {
            navTargets.add(
                CatalogNavTarget(
                    key = "row_continue_watching",
                    lazyIndex = nextLazyIdx++,
                    requester = continueWatchingFocusRequester,
                    resetToTop = (selectedTab == NavTab.NEW_POPULAR)
                )
            )
        }

        when (selectedTab) {
            NavTab.HOME -> {
                val stripLazyIdx = nextLazyIdx++
                if (platformState.providers.isNotEmpty()) {
                    navTargets.add(
                        CatalogNavTarget(
                            key = "row_streaming_platforms_strip",
                            lazyIndex = stripLazyIdx,
                            requester = platformStripFocusRequester,
                            resetToTop = (activePlatform != null)
                        )
                    )
                }
                if (activePlatform != null) {
                    when {
                        platformState.isLoading -> {
                            nextLazyIdx++ // platform_titles_loading
                        }
                        platformState.errorMessage != null -> {
                            navTargets.add(
                                CatalogNavTarget(
                                    key = "platform_titles_error",
                                    lazyIndex = nextLazyIdx++,
                                    requester = firstRowFocusRequester
                                )
                            )
                        }
                        platformState.displayedTitles.isEmpty() -> {
                            nextLazyIdx++ // platform_titles_empty
                        }
                        else -> {
                            val hasBothPlatformRows = platformState.mediaTypeFilter == PlatformFilterMediaType.ALL &&
                                platformState.movies.isNotEmpty() &&
                                platformState.series.isNotEmpty()
                            if (hasBothPlatformRows) {
                                navTargets.add(
                                    CatalogNavTarget(
                                        key = "platform_movies_row",
                                        lazyIndex = nextLazyIdx++,
                                        requester = firstRowFocusRequester
                                    )
                                )
                                navTargets.add(
                                    CatalogNavTarget(
                                        key = "platform_series_row",
                                        lazyIndex = nextLazyIdx++,
                                        requester = requesterForKey("platform_series_row")
                                    )
                                )
                            }
                            nextLazyIdx++ // platform_grid_title
                            for (rowIdx in 0 until platformRows.size) {
                                val rowKey = "platform_grid_row_$rowIdx"
                                val maxCol = (platformRows[rowIdx].size - 1).coerceAtLeast(0)
                                val rememberedCol = (gridRowLastCol[rowKey] ?: 0).coerceIn(0, maxCol)
                                val rowZeroColZero = if (!hasBothPlatformRows && rowIdx == 0) firstRowFocusRequester else null
                                val rowRequester = gridCellRequester(rowKey, rememberedCol, rowZeroColZero)
                                navTargets.add(
                                    CatalogNavTarget(
                                        key = rowKey,
                                        lazyIndex = nextLazyIdx++,
                                        requester = rowRequester
                                    )
                                )
                            }
                        }
                    }
                } else {
                    homeCatalogRows.forEachIndexed { rowIdx, spec ->
                        val rowRequester = if (rowIdx == 0) {
                            firstRowFocusRequester
                        } else {
                            requesterForKey(spec.key)
                        }
                        navTargets.add(
                            CatalogNavTarget(
                                key = spec.key,
                                lazyIndex = nextLazyIdx++,
                                requester = rowRequester
                            )
                        )
                    }
                }
            }

            NavTab.SERIES -> {
                seriesCatalogRows.forEachIndexed { rowIdx, spec ->
                    val rowRequester = if (rowIdx == 0) {
                        firstRowFocusRequester
                    } else {
                        requesterForKey(spec.key)
                    }
                    navTargets.add(
                        CatalogNavTarget(
                            key = spec.key,
                            lazyIndex = nextLazyIdx++,
                            requester = rowRequester
                        )
                    )
                }
            }

            NavTab.MOVIES -> {
                if (activeMovieGenre != null) {
                    nextLazyIdx++ // movies_genre_header
                    when {
                        movieGenreState.isLoading -> {
                            nextLazyIdx++ // movies_genre_loading
                        }
                        movieGenreState.errorMessage != null -> {
                            navTargets.add(
                                CatalogNavTarget(
                                    key = "movies_genre_error",
                                    lazyIndex = nextLazyIdx++,
                                    requester = firstRowFocusRequester
                                )
                            )
                        }
                        movieGenreState.movies.isEmpty() -> {
                            nextLazyIdx++ // movies_genre_empty
                        }
                        else -> {
                            for (rowIdx in 0 until genreRows.size) {
                                val rowKey = "genre_grid_row_$rowIdx"
                                val maxCol = (genreRows[rowIdx].size - 1).coerceAtLeast(0)
                                val rememberedCol = (gridRowLastCol[rowKey] ?: 0).coerceIn(0, maxCol)
                                val rowZeroColZero = if (rowIdx == 0) firstRowFocusRequester else null
                                val rowRequester = gridCellRequester(rowKey, rememberedCol, rowZeroColZero)
                                navTargets.add(
                                    CatalogNavTarget(
                                        key = rowKey,
                                        lazyIndex = nextLazyIdx++,
                                        requester = rowRequester
                                    )
                                )
                            }
                        }
                    }
                } else {
                    moviesCatalogRows.forEachIndexed { rowIdx, spec ->
                        val rowRequester = if (rowIdx == 0) {
                            firstRowFocusRequester
                        } else {
                            requesterForKey(spec.key)
                        }
                        navTargets.add(
                            CatalogNavTarget(
                                key = spec.key,
                                lazyIndex = nextLazyIdx++,
                                requester = rowRequester
                            )
                        )
                    }
                }
            }

            NavTab.NEW_POPULAR -> {
                newPopularCatalogRows.forEachIndexed { rowIdx, spec ->
                    val rowRequester = if (rowIdx == 0) {
                        firstRowFocusRequester
                    } else {
                        requesterForKey(spec.key)
                    }
                    navTargets.add(
                        CatalogNavTarget(
                            key = spec.key,
                            lazyIndex = nextLazyIdx++,
                            requester = rowRequester,
                            resetToTop = (rowIdx == 0 && !hasContinueWatching)
                        )
                    )
                }
            }

            else -> Unit
        }

        fun prevTargetFor(key: String): CatalogNavTarget? {
            val idx = navTargets.indexOfFirst { it.key == key }
            return if (idx > 0) navTargets[idx - 1] else null
        }

        fun nextTargetFor(key: String): CatalogNavTarget? {
            val idx = navTargets.indexOfFirst { it.key == key }
            return if (idx >= 0 && idx < navTargets.lastIndex) navTargets[idx + 1] else null
        }

        fun targetFor(key: String): CatalogNavTarget? = navTargets.firstOrNull { it.key == key }

        LazyColumn(
            state = catalogListState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("catalog_lazy_column"),
            contentPadding = PaddingValues(bottom = bottomPadding + 32.dp)
        ) {
            if (selectedTab == NavTab.MOVIES) {
                item(key = "movies_top_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(if (isTvLayout) 16.dp else 118.dp))
                }
                item(key = "movies_genre_chip_bar", contentType = "genre_bar") {
                    val prev = prevTargetFor("movies_genre_chip_bar")
                    val next = nextTargetFor("movies_genre_chip_bar")
                    MovieGenreChipBar(
                        genres = movieGenreState.genres,
                        selectedGenre = activeMovieGenre,
                        onSelectGenre = onSelectMovieGenre,
                        firstChipFocusRequester = movieGenreBarFocusRequester,
                        upFocusRequester = prev?.requester ?: topBarFocusRequester,
                        downFocusRequester = next?.requester,
                        onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                        onDownKey = { if (next != null) navigateToTarget(next) else false },
                        navRailFocusRequester = navRailFocusRequester,
                        onItemFocused = onRememberContentFocus,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            if (selectedTab == NavTab.HOME && activePlatform != null) {
                item(key = "home_platform_top_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(if (isTvLayout) 16.dp else 118.dp))
                }
            }

            if (showHero) {
                item(key = "hero_${heroItem.uniqueKey}", contentType = "hero") {
                    val isInWatchlist = heroItem.uniqueKey in watchlistIds
                    val savedProgress = progressByKey[heroItem.uniqueKey]
                    val prev = prevTargetFor("hero")
                    val next = nextTargetFor("hero")
                    NetflixHeroBanner(
                        item = heroItem,
                        isInWatchlist = isInWatchlist,
                        savedProgress = savedProgress,
                        onPlayClick = { onPlayItem(heroItem) },
                        onToggleWatchlist = { onToggleWatchlist(heroItem) },
                        onInfoClick = { onOpenDetails(heroItem) },
                        playButtonFocusRequester = heroPlayFocusRequester,
                        navRailFocusRequester = navRailFocusRequester,
                        topBarFocusRequester = prev?.requester ?: topBarFocusRequester,
                        firstRowFocusRequester = next?.requester,
                        onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                        onDownKey = { if (next != null) navigateToTarget(next) else false },
                        onItemFocused = onRememberContentFocus,
                        isTvLayout = isTvLayout
                    )
                }
            } else if (selectedTab == NavTab.NEW_POPULAR) {
                item(key = "new_popular_top_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(if (isTvLayout) 16.dp else 118.dp))
                }
            }

            // Sezione "Continua a guardare" (Room DB + startAt VixSrc API)
            if (hasContinueWatching) {
                item(key = "row_continue_watching", contentType = "continue_watching") {
                    val prev = prevTargetFor("row_continue_watching")
                    val next = nextTargetFor("row_continue_watching")
                    ContinueWatchingSection(
                        entries = filteredContinueWatching,
                        onResumeClick = onResumeContinueWatching,
                        onInfoClick = onInfoContinueWatching,
                        onRemoveClick = onRemoveContinueWatching,
                        onTvLongPress = onTvLongPressContinueWatching,
                        onItemFocused = onRememberContentFocus,
                        isTvLayout = isTvLayout,
                        firstItemFocusRequester = continueWatchingFocusRequester,
                        upFocusRequester = prev?.requester ?: topBarFocusRequester,
                        downFocusRequester = next?.requester,
                        onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                        onDownKey = { if (next != null) navigateToTarget(next) else false },
                        navRailFocusRequester = navRailFocusRequester
                    )
                }
            }

            when (selectedTab) {
                NavTab.HOME -> {
                    item(key = "row_streaming_platforms_strip", contentType = "platform_strip") {
                        val prev = prevTargetFor("row_streaming_platforms_strip")
                        val next = nextTargetFor("row_streaming_platforms_strip")
                        StreamingPlatformsStrip(
                            providers = platformState.providers,
                            selectedProvider = activePlatform,
                            onSelectProvider = onSelectStreamingPlatform,
                            showHeader = true,
                            firstItemFocusRequester = platformStripFocusRequester,
                            upFocusRequester = prev?.requester ?: topBarFocusRequester,
                            downFocusRequester = next?.requester,
                            onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                            onDownKey = { if (next != null) navigateToTarget(next) else false },
                            navRailFocusRequester = navRailFocusRequester,
                            onItemFocused = onRememberContentFocus
                        )
                    }

                    if (activePlatform != null) {
                        when {
                            platformState.isLoading -> {
                                item(key = "platform_titles_loading", contentType = "status") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 56.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = NetflixRed)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Caricamento titoli ${activePlatform.displayName} da TMDB…",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NetflixLightGray
                                            )
                                        }
                                    }
                                }
                            }

                            platformState.errorMessage != null -> {
                                item(key = "platform_titles_error", contentType = "status") {
                                    val prev = prevTargetFor("platform_titles_error")
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 40.dp),
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
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Text(
                                                text = platformState.errorMessage,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NetflixLightGray,
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = onRetryStreamingPlatform,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NetflixRed,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .safeFocusRequester(firstRowFocusRequester)
                                                    .onPreviewKeyEvent { keyEvent ->
                                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                                            when (keyEvent.key) {
                                                                Key.DirectionUp -> if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp()
                                                                Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                                                else -> false
                                                            }
                                                        } else {
                                                            false
                                                        }
                                                    }
                                                    .focusProperties {
                                                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                                                        (prev?.requester ?: platformStripFocusRequester).takeIfAttached()?.let { up = it }
                                                    }
                                                    .tvFocusableControl(
                                                        shape = RoundedCornerShape(8.dp),
                                                        onClick = onRetryStreamingPlatform
                                                    )
                                                    .testTag("retry_platform_titles_button")
                                            ) {
                                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Riprova")
                                            }
                                        }
                                    }
                                }
                            }

                            platformState.displayedTitles.isEmpty() -> {
                                item(key = "platform_titles_empty", contentType = "status") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Nessun titolo trovato su TMDB per ${activePlatform.displayName}.",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = NetflixLightGray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            else -> {
                                val hasBothPlatformRows = platformState.mediaTypeFilter == PlatformFilterMediaType.ALL &&
                                    platformState.movies.isNotEmpty() &&
                                    platformState.series.isNotEmpty()

                                if (hasBothPlatformRows) {
                                    item(key = "platform_${activePlatform.providerId}_movies_row", contentType = "media_row") {
                                        val prev = prevTargetFor("platform_movies_row")
                                        val next = nextTargetFor("platform_movies_row")
                                        MediaCatalogRow(
                                            title = "Film Popolari su ${activePlatform.displayName}",
                                            items = platformState.movies,
                                            onItemClick = onOpenDetails,
                                            onItemQuickPlay = onPlayItem,
                                            onItemLongClick = onTvLongPressPoster,
                                            onItemFocused = onRememberContentFocus,
                                            firstItemFocusRequester = firstRowFocusRequester,
                                            upFocusRequester = prev?.requester ?: platformStripFocusRequester,
                                            downFocusRequester = next?.requester,
                                            onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                                            onDownKey = { if (next != null) navigateToTarget(next) else false },
                                            navRailFocusRequester = navRailFocusRequester
                                        )
                                    }
                                    item(key = "platform_${activePlatform.providerId}_series_row", contentType = "media_row") {
                                        val currentTarget = targetFor("platform_series_row")
                                        val prev = prevTargetFor("platform_series_row")
                                        val next = nextTargetFor("platform_series_row")
                                        MediaCatalogRow(
                                            title = "Serie TV Popolari su ${activePlatform.displayName}",
                                            items = platformState.series,
                                            onItemClick = onOpenDetails,
                                            onItemQuickPlay = onPlayItem,
                                            onItemLongClick = onTvLongPressPoster,
                                            onItemFocused = onRememberContentFocus,
                                            firstItemFocusRequester = currentTarget?.requester,
                                            upFocusRequester = prev?.requester,
                                            downFocusRequester = next?.requester,
                                            onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                                            onDownKey = { if (next != null) navigateToTarget(next) else false },
                                            navRailFocusRequester = navRailFocusRequester
                                        )
                                    }
                                }

                                item(key = "platform_${activePlatform.providerId}_grid_title", contentType = "section_label") {
                                    Text(
                                        text = when (platformState.mediaTypeFilter) {
                                            PlatformFilterMediaType.ALL -> "Tutti i Titoli su ${activePlatform.displayName}"
                                            PlatformFilterMediaType.MOVIES -> "Film su ${activePlatform.displayName}"
                                            PlatformFilterMediaType.SERIES -> "Serie TV su ${activePlatform.displayName}"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = NetflixWhite,
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp, vertical = 6.dp)
                                            .testTag("platform_catalog_section")
                                    )
                                }

                                items(
                                    count = platformRows.size,
                                    key = { rowIdx -> "platform_${activePlatform.providerId}_${platformState.mediaTypeFilter.name}_row_$rowIdx" },
                                    contentType = { "platform_grid_row" }
                                ) { rowIdx ->
                                    val rowKey = "platform_grid_row_$rowIdx"
                                    val prev = prevTargetFor(rowKey)
                                    val next = nextTargetFor(rowKey)
                                    val rowItems = platformRows[rowIdx]
                                    val rowZeroColZero = if (!hasBothPlatformRows && rowIdx == 0) firstRowFocusRequester else null
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                            .focusGroup()
                                    ) {
                                        for (colIdx in 0 until gridColumns) {
                                            if (colIdx < rowItems.size) {
                                                val item = rowItems[colIdx]
                                                val itemFocusRequester = gridCellRequester(
                                                    rowKey = rowKey,
                                                    colIdx = colIdx,
                                                    rowZeroColZeroRequester = rowZeroColZero
                                                )
                                                val leftColRequester = if (colIdx > 0) {
                                                    gridCellRequester(
                                                        rowKey = rowKey,
                                                        colIdx = colIdx - 1,
                                                        rowZeroColZeroRequester = rowZeroColZero
                                                    )
                                                } else {
                                                    navRailFocusRequester
                                                }
                                                val rightColRequester = if (colIdx + 1 < rowItems.size) {
                                                    gridCellRequester(
                                                        rowKey = rowKey,
                                                        colIdx = colIdx + 1,
                                                        rowZeroColZeroRequester = rowZeroColZero
                                                    )
                                                } else {
                                                    null
                                                }
                                                val downCellTarget: CatalogNavTarget? = if (next != null && rowIdx + 1 < platformRows.size) {
                                                    val nextRowKey = "platform_grid_row_${rowIdx + 1}"
                                                    val nextRowCol = colIdx.coerceAtMost(platformRows[rowIdx + 1].lastIndex)
                                                    next.copy(
                                                        requester = gridCellRequester(
                                                            rowKey = nextRowKey,
                                                            colIdx = nextRowCol,
                                                            rowZeroColZeroRequester = null
                                                        )
                                                    )
                                                } else {
                                                    next
                                                }
                                                val upCellTarget: CatalogNavTarget? = if (prev != null && rowIdx > 0) {
                                                    val prevRowKey = "platform_grid_row_${rowIdx - 1}"
                                                    val prevRowCol = colIdx.coerceAtMost(platformRows[rowIdx - 1].lastIndex)
                                                    val prevRowZeroColZero = if (!hasBothPlatformRows && rowIdx - 1 == 0) firstRowFocusRequester else null
                                                    prev.copy(
                                                        requester = gridCellRequester(
                                                            rowKey = prevRowKey,
                                                            colIdx = prevRowCol,
                                                            rowZeroColZeroRequester = prevRowZeroColZero
                                                        )
                                                    )
                                                } else {
                                                    prev
                                                }
                                                MediaPosterCard(
                                                    item = item,
                                                    itemIndex = rowIdx * gridColumns + colIdx,
                                                    onClick = { onOpenDetails(item) },
                                                    onQuickPlay = { onPlayItem(item) },
                                                    onLongClick = { onTvLongPressPoster(item) },
                                                    onFocused = {
                                                        gridRowLastCol[rowKey] = colIdx
                                                        onRememberContentFocus(itemFocusRequester)
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .safeFocusRequester(itemFocusRequester)
                                                        .onPreviewKeyEvent { keyEvent ->
                                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                                when (keyEvent.key) {
                                                                    Key.DirectionDown -> {
                                                                        if (downCellTarget != null) {
                                                                            if (rowIdx + 1 < platformRows.size) {
                                                                                gridRowLastCol["platform_grid_row_${rowIdx + 1}"] =
                                                                                    colIdx.coerceAtMost(platformRows[rowIdx + 1].lastIndex)
                                                                            }
                                                                            navigateToTarget(downCellTarget)
                                                                        } else {
                                                                            false
                                                                        }
                                                                    }
                                                                    Key.DirectionUp -> {
                                                                        if (upCellTarget != null) {
                                                                            if (rowIdx > 0) {
                                                                                gridRowLastCol["platform_grid_row_${rowIdx - 1}"] =
                                                                                    colIdx.coerceAtMost(platformRows[rowIdx - 1].lastIndex)
                                                                            }
                                                                            navigateToTarget(upCellTarget)
                                                                        } else {
                                                                            handleTopBoundaryUp()
                                                                        }
                                                                    }
                                                                    Key.DirectionLeft -> leftColRequester.safeRequestFocus()
                                                                    Key.DirectionRight -> rightColRequester?.safeRequestFocus() ?: true
                                                                    else -> false
                                                                }
                                                            } else {
                                                                false
                                                            }
                                                        }
                                                        .focusProperties {
                                                            leftColRequester.takeIfAttached()?.let { left = it }
                                                            rightColRequester?.takeIfAttached()?.let { right = it }
                                                            upCellTarget?.requester?.takeIfAttached()?.let { up = it }
                                                            downCellTarget?.requester?.takeIfAttached()?.let { down = it }
                                                        }
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        homeCatalogRows.forEach { spec ->
                            item(key = spec.key, contentType = if (spec.isTop10) "top10_row" else "media_row") {
                                val currentTarget = targetFor(spec.key)
                                val prev = prevTargetFor(spec.key)
                                val next = nextTargetFor(spec.key)
                                if (spec.isTop10) {
                                    Top10Section(
                                        title = spec.title,
                                        items = spec.items,
                                        onItemClick = onOpenDetails,
                                        onItemQuickPlay = onPlayItem,
                                        onItemLongClick = onTvLongPressPoster,
                                        onItemFocused = onRememberContentFocus,
                                        firstItemFocusRequester = currentTarget?.requester,
                                        upFocusRequester = prev?.requester,
                                        downFocusRequester = next?.requester,
                                        onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                                        onDownKey = { if (next != null) navigateToTarget(next) else false },
                                        navRailFocusRequester = navRailFocusRequester
                                    )
                                } else {
                                    MediaCatalogRow(
                                        title = spec.title,
                                        items = spec.items,
                                        onItemClick = onOpenDetails,
                                        onItemQuickPlay = onPlayItem,
                                        onItemLongClick = onTvLongPressPoster,
                                        onItemFocused = onRememberContentFocus,
                                        firstItemFocusRequester = currentTarget?.requester,
                                        upFocusRequester = prev?.requester,
                                        downFocusRequester = next?.requester,
                                        onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                                        onDownKey = { if (next != null) navigateToTarget(next) else false },
                                        navRailFocusRequester = navRailFocusRequester
                                    )
                                }
                            }
                        }
                    }
                }

                NavTab.SERIES -> {
                    seriesCatalogRows.forEach { spec ->
                        item(key = spec.key, contentType = "media_row") {
                            val currentTarget = targetFor(spec.key)
                            val prev = prevTargetFor(spec.key)
                            val next = nextTargetFor(spec.key)
                            MediaCatalogRow(
                                title = spec.title,
                                items = spec.items,
                                onItemClick = onOpenDetails,
                                onItemQuickPlay = onPlayItem,
                                onItemLongClick = onTvLongPressPoster,
                                onItemFocused = onRememberContentFocus,
                                firstItemFocusRequester = currentTarget?.requester,
                                upFocusRequester = prev?.requester,
                                downFocusRequester = next?.requester,
                                onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                                onDownKey = { if (next != null) navigateToTarget(next) else false },
                                navRailFocusRequester = navRailFocusRequester
                            )
                        }
                    }
                }

                NavTab.MOVIES -> {
                    if (activeMovieGenre != null) {
                        item(key = "movies_genre_header_${activeMovieGenre.id}", contentType = "genre_header") {
                            val genreBarTarget = targetFor("movies_genre_chip_bar")
                            val firstGridTarget = nextTargetFor("movies_genre_chip_bar")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                                    .testTag("filtered_genre_movies_section")
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Film • ${activeMovieGenre.name}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = NetflixWhite
                                    )
                                    val subtitle = when {
                                        movieGenreState.isLoading -> "Ricerca titoli su TMDB in corso…"
                                        movieGenreState.errorMessage != null -> "Errore di caricamento TMDB"
                                        else -> "${movieGenreState.movies.size} titoli popolari TMDB"
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NetflixLightGray
                                    )
                                }

                                OutlinedButton(
                                    onClick = { onSelectMovieGenre(null) },
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .safeFocusRequester(clearGenreFilterFocusRequester)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.DirectionDown -> if (firstGridTarget != null) navigateToTarget(firstGridTarget) else false
                                                    Key.DirectionUp -> if (genreBarTarget != null) navigateToTarget(genreBarTarget) else handleTopBoundaryUp()
                                                    Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                                    else -> false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                        .focusProperties {
                                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                                            movieGenreBarFocusRequester.takeIfAttached()?.let { up = it }
                                            firstRowFocusRequester.takeIfAttached()?.let { down = it }
                                        }
                                        .tvFocusableControl(
                                            shape = RoundedCornerShape(50),
                                            focusedBorderColor = NetflixWhite,
                                            onClick = { onSelectMovieGenre(null) }
                                        )
                                        .testTag("clear_genre_filter_button")
                                ) {
                                    Text("Tutti i film", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        when {
                            movieGenreState.isLoading -> {
                                item(key = "movies_genre_loading", contentType = "status") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 56.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = NetflixRed)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Caricamento ${activeMovieGenre.name} da TMDB…",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NetflixLightGray
                                            )
                                        }
                                    }
                                }
                            }

                            movieGenreState.errorMessage != null -> {
                                item(key = "movies_genre_error", contentType = "status") {
                                    val prev = prevTargetFor("movies_genre_error")
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 40.dp),
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
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Text(
                                                text = movieGenreState.errorMessage,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NetflixLightGray,
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = onRetryMovieGenre,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NetflixRed,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .safeFocusRequester(firstRowFocusRequester)
                                                    .onPreviewKeyEvent { keyEvent ->
                                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                                            when (keyEvent.key) {
                                                                Key.DirectionUp -> if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp()
                                                                Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                                                else -> false
                                                            }
                                                        } else {
                                                            false
                                                        }
                                                    }
                                                    .focusProperties {
                                                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                                                        movieGenreBarFocusRequester.takeIfAttached()?.let { up = it }
                                                    }
                                                    .tvFocusableControl(
                                                        shape = RoundedCornerShape(8.dp),
                                                        onClick = onRetryMovieGenre
                                                    )
                                                    .testTag("retry_genre_movies_button")
                                            ) {
                                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Riprova")
                                            }
                                        }
                                    }
                                }
                            }

                            movieGenreState.movies.isEmpty() -> {
                                item(key = "movies_genre_empty", contentType = "status") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Nessun film trovato su TMDB per \"${activeMovieGenre.name}\".",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = NetflixLightGray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            else -> {
                                items(
                                    count = genreRows.size,
                                    key = { rowIdx -> "genre_${activeMovieGenre.id}_row_$rowIdx" },
                                    contentType = { "genre_grid_row" }
                                ) { rowIdx ->
                                    val rowKey = "genre_grid_row_$rowIdx"
                                    val prev = prevTargetFor(rowKey)
                                    val next = nextTargetFor(rowKey)
                                    val rowItems = genreRows[rowIdx]
                                    val rowZeroColZero = if (rowIdx == 0) firstRowFocusRequester else null
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                            .focusGroup()
                                    ) {
                                        for (colIdx in 0 until gridColumns) {
                                            if (colIdx < rowItems.size) {
                                                val item = rowItems[colIdx]
                                                val itemFocusRequester = gridCellRequester(
                                                    rowKey = rowKey,
                                                    colIdx = colIdx,
                                                    rowZeroColZeroRequester = rowZeroColZero
                                                )
                                                val leftColRequester = if (colIdx > 0) {
                                                    gridCellRequester(
                                                        rowKey = rowKey,
                                                        colIdx = colIdx - 1,
                                                        rowZeroColZeroRequester = rowZeroColZero
                                                    )
                                                } else {
                                                    navRailFocusRequester
                                                }
                                                val rightColRequester = if (colIdx + 1 < rowItems.size) {
                                                    gridCellRequester(
                                                        rowKey = rowKey,
                                                        colIdx = colIdx + 1,
                                                        rowZeroColZeroRequester = rowZeroColZero
                                                    )
                                                } else {
                                                    null
                                                }
                                                val downCellTarget: CatalogNavTarget? = if (next != null && rowIdx + 1 < genreRows.size) {
                                                    val nextRowKey = "genre_grid_row_${rowIdx + 1}"
                                                    val nextRowCol = colIdx.coerceAtMost(genreRows[rowIdx + 1].lastIndex)
                                                    next.copy(
                                                        requester = gridCellRequester(
                                                            rowKey = nextRowKey,
                                                            colIdx = nextRowCol,
                                                            rowZeroColZeroRequester = null
                                                        )
                                                    )
                                                } else {
                                                    next
                                                }
                                                val upCellTarget: CatalogNavTarget? = if (prev != null && rowIdx > 0) {
                                                    val prevRowKey = "genre_grid_row_${rowIdx - 1}"
                                                    val prevRowCol = colIdx.coerceAtMost(genreRows[rowIdx - 1].lastIndex)
                                                    val prevRowZeroColZero = if (rowIdx - 1 == 0) firstRowFocusRequester else null
                                                    prev.copy(
                                                        requester = gridCellRequester(
                                                            rowKey = prevRowKey,
                                                            colIdx = prevRowCol,
                                                            rowZeroColZeroRequester = prevRowZeroColZero
                                                        )
                                                    )
                                                } else {
                                                    prev
                                                }
                                                MediaPosterCard(
                                                    item = item,
                                                    itemIndex = rowIdx * gridColumns + colIdx,
                                                    onClick = { onOpenDetails(item) },
                                                    onQuickPlay = { onPlayItem(item) },
                                                    onLongClick = { onTvLongPressPoster(item) },
                                                    onFocused = {
                                                        gridRowLastCol[rowKey] = colIdx
                                                        onRememberContentFocus(itemFocusRequester)
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .safeFocusRequester(itemFocusRequester)
                                                        .onPreviewKeyEvent { keyEvent ->
                                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                                when (keyEvent.key) {
                                                                    Key.DirectionDown -> {
                                                                        if (downCellTarget != null) {
                                                                            if (rowIdx + 1 < genreRows.size) {
                                                                                gridRowLastCol["genre_grid_row_${rowIdx + 1}"] =
                                                                                    colIdx.coerceAtMost(genreRows[rowIdx + 1].lastIndex)
                                                                            }
                                                                            navigateToTarget(downCellTarget)
                                                                        } else {
                                                                            false
                                                                        }
                                                                    }
                                                                    Key.DirectionUp -> {
                                                                        if (rowIdx == 0 && colIdx == rowItems.lastIndex && colIdx > 0 && clearGenreFilterFocusRequester.safeRequestFocus()) {
                                                                            true
                                                                        } else if (upCellTarget != null) {
                                                                            if (rowIdx > 0) {
                                                                                gridRowLastCol["genre_grid_row_${rowIdx - 1}"] =
                                                                                    colIdx.coerceAtMost(genreRows[rowIdx - 1].lastIndex)
                                                                            }
                                                                            navigateToTarget(upCellTarget)
                                                                        } else {
                                                                            handleTopBoundaryUp()
                                                                        }
                                                                    }
                                                                    Key.DirectionLeft -> leftColRequester.safeRequestFocus()
                                                                    Key.DirectionRight -> rightColRequester?.safeRequestFocus() ?: true
                                                                    else -> false
                                                                }
                                                            } else {
                                                                false
                                                            }
                                                        }
                                                        .focusProperties {
                                                            leftColRequester.takeIfAttached()?.let { left = it }
                                                            rightColRequester?.takeIfAttached()?.let { right = it }
                                                            if (rowIdx == 0 && colIdx == rowItems.lastIndex && colIdx > 0) {
                                                                clearGenreFilterFocusRequester.takeIfAttached()?.let { up = it }
                                                            } else {
                                                                upCellTarget?.requester?.takeIfAttached()?.let { up = it }
                                                            }
                                                            downCellTarget?.requester?.takeIfAttached()?.let { down = it }
                                                        }
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        moviesCatalogRows.forEach { spec ->
                            item(key = spec.key, contentType = "media_row") {
                                val currentTarget = targetFor(spec.key)
                                val prev = prevTargetFor(spec.key)
                                val next = nextTargetFor(spec.key)
                                MediaCatalogRow(
                                    title = spec.title,
                                    items = spec.items,
                                    onItemClick = onOpenDetails,
                                    onItemQuickPlay = onPlayItem,
                                    onItemLongClick = onTvLongPressPoster,
                                    onItemFocused = onRememberContentFocus,
                                    firstItemFocusRequester = currentTarget?.requester,
                                    upFocusRequester = prev?.requester,
                                    downFocusRequester = next?.requester,
                                    onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                                    onDownKey = { if (next != null) navigateToTarget(next) else false },
                                    navRailFocusRequester = navRailFocusRequester
                                )
                            }
                        }
                    }
                }

                NavTab.NEW_POPULAR -> {
                    newPopularCatalogRows.forEach { spec ->
                        item(key = spec.key, contentType = if (spec.isTop10) "top10_row" else "media_row") {
                            val currentTarget = targetFor(spec.key)
                            val prev = prevTargetFor(spec.key)
                            val next = nextTargetFor(spec.key)
                            if (spec.isTop10) {
                                Top10Section(
                                    title = spec.title,
                                    items = spec.items,
                                    onItemClick = onOpenDetails,
                                    onItemQuickPlay = onPlayItem,
                                    onItemLongClick = onTvLongPressPoster,
                                    onItemFocused = onRememberContentFocus,
                                    firstItemFocusRequester = currentTarget?.requester,
                                    upFocusRequester = prev?.requester,
                                    downFocusRequester = next?.requester,
                                    onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                                    onDownKey = { if (next != null) navigateToTarget(next) else false },
                                    navRailFocusRequester = navRailFocusRequester
                                )
                            } else {
                                MediaCatalogRow(
                                    title = spec.title,
                                    items = spec.items,
                                    onItemClick = onOpenDetails,
                                    onItemQuickPlay = onPlayItem,
                                    onItemLongClick = onTvLongPressPoster,
                                    onItemFocused = onRememberContentFocus,
                                    firstItemFocusRequester = currentTarget?.requester,
                                    upFocusRequester = prev?.requester,
                                    downFocusRequester = next?.requester,
                                    onUpKey = { if (prev != null) navigateToTarget(prev) else handleTopBoundaryUp() },
                                    onDownKey = { if (next != null) navigateToTarget(next) else false },
                                    navRailFocusRequester = navRailFocusRequester
                                )
                            }
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun SearchResultsGrid(
    query: String,
    isSearching: Boolean,
    results: List<MediaItem>,
    isTvLayout: Boolean,
    firstItemFocusRequester: FocusRequester,
    navRailFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    onItemFocused: (FocusRequester) -> Unit = {},
    onItemQuickPlay: (MediaItem) -> Unit = {},
    onItemLongClick: (MediaItem) -> Unit = {},
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

    val safeResults = remember(results) { results.distinctBy { it.uniqueKey } }
    if (safeResults.isEmpty()) {
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

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridColumns = when {
            maxWidth >= 1240.dp -> 6
            maxWidth >= 960.dp -> 5
            maxWidth >= 680.dp -> 4
            maxWidth >= 420.dp -> 3
            else -> 2
        }

        val itemFocusRequesters = remember(safeResults) {
            List(safeResults.size) { index ->
                if (index == 0) firstItemFocusRequester else FocusRequester()
            }
        }

        val navigateToGridIndex: (Int, Boolean) -> Boolean = { targetIdx, resetToTop ->
            val targetRequester = itemFocusRequesters.getOrNull(targetIdx)
            if (targetRequester == null) {
                false
            } else if (resetToTop) {
                coroutineScope.launch {
                    runCatching { gridState.scrollToItem(0, 0) }
                    for (attempt in 1..6) {
                        androidx.compose.runtime.withFrameNanos { }
                        if (targetRequester.safeRequestFocus()) break
                    }
                    androidx.compose.runtime.withFrameNanos { }
                    if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
                        runCatching { gridState.scrollToItem(0, 0) }
                    }
                }
                targetRequester.safeRequestFocus()
                true
            } else if (targetRequester.safeRequestFocus()) {
                true
            } else {
                coroutineScope.launch {
                    runCatching { gridState.scrollToItem(targetIdx, 0) }
                    for (attempt in 1..6) {
                        androidx.compose.runtime.withFrameNanos { }
                        if (targetRequester.safeRequestFocus()) break
                    }
                }
                true
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(gridColumns),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("search_results_grid")
        ) {
            itemsIndexed(
                items = safeResults,
                key = { _, it -> "search_${it.uniqueKey}" },
                contentType = { _, _ -> "search_poster" }
            ) { index, item ->
                val itemFocusRequester = itemFocusRequesters[index]
                val isFirstColumn = (index % gridColumns) == 0
                val isFirstRow = index < gridColumns
                val upIndex = index - gridColumns
                val downIndex = if (index + gridColumns < safeResults.size) {
                    index + gridColumns
                } else if ((index / gridColumns) < ((safeResults.lastIndex) / gridColumns)) {
                    safeResults.lastIndex
                } else {
                    -1
                }

                MediaPosterCard(
                    item = item,
                    itemIndex = index,
                    onClick = { onItemClick(item) },
                    onQuickPlay = { onItemQuickPlay(item) },
                    onLongClick = { onItemLongClick(item) },
                    onFocused = { onItemFocused(itemFocusRequester) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .safeFocusRequester(itemFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        if (isFirstRow) {
                                            coroutineScope.launch {
                                                runCatching { gridState.scrollToItem(0, 0) }
                                            }
                                            upFocusRequester.safeRequestFocus()
                                        } else {
                                            navigateToGridIndex(upIndex, upIndex < gridColumns)
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (downIndex >= 0) {
                                            navigateToGridIndex(downIndex, false)
                                        } else {
                                            false
                                        }
                                    }
                                    Key.DirectionLeft -> {
                                        if (isFirstColumn) {
                                            navRailFocusRequester.safeRequestFocus()
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            if (isFirstColumn) {
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                            }
                            if (isFirstRow) {
                                upFocusRequester.takeIfAttached()?.let { up = it }
                            } else {
                                itemFocusRequesters.getOrNull(upIndex)?.takeIfAttached()?.let { up = it }
                            }
                            if (downIndex >= 0) {
                                itemFocusRequesters.getOrNull(downIndex)?.takeIfAttached()?.let { down = it }
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun MyListScreen(
    watchlist: List<WatchlistEntity>,
    continueWatching: List<WatchProgressEntity>,
    isTvLayout: Boolean,
    firstRowFocusRequester: FocusRequester,
    navRailFocusRequester: FocusRequester,
    topBarFocusRequester: FocusRequester?,
    tabScrollResetNonce: Int = 0,
    onBrowseCatalog: () -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onPlayItem: (MediaItem) -> Unit = onOpenItem,
    onResumeContinueWatching: (WatchProgressEntity) -> Unit,
    onRemoveContinueWatching: (WatchProgressEntity) -> Unit,
    onTvLongPressContinueWatching: (WatchProgressEntity) -> Unit = {},
    onTvLongPressPoster: (MediaItem) -> Unit = {},
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    contentPadding: PaddingValues
) {
    val safeWatchlist = remember(watchlist) { watchlist.distinctBy { it.id } }
    val mappedItems = remember(safeWatchlist) {
        safeWatchlist.map {
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
    }

    val myListState = rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val watchlistSecondRowRequester = remember { FocusRequester() }
    val hasContinueWatching = continueWatching.isNotEmpty()
    val hasWatchlist = mappedItems.isNotEmpty()

    LaunchedEffect(tabScrollResetNonce) {
        if (tabScrollResetNonce > 0) {
            runCatching { myListState.scrollToItem(0, 0) }
        }
    }

    val navigateMyListSection: (Int, FocusRequester, Boolean) -> Boolean = { targetIdx, targetRequester, resetToTop ->
        if (resetToTop) {
            coroutineScope.launch {
                runCatching { myListState.scrollToItem(0, 0) }
                for (attempt in 1..6) {
                    androidx.compose.runtime.withFrameNanos { }
                    if (targetRequester.safeRequestFocus()) break
                }
                androidx.compose.runtime.withFrameNanos { }
                if (myListState.firstVisibleItemIndex != 0 || myListState.firstVisibleItemScrollOffset != 0) {
                    runCatching { myListState.scrollToItem(0, 0) }
                }
            }
            targetRequester.safeRequestFocus()
            true
        } else {
            val isTargetFullyVisible = myListState.layoutInfo.visibleItemsInfo.any { info ->
                info.index == targetIdx &&
                    info.offset >= 0 &&
                    (info.offset + info.size) <= myListState.layoutInfo.viewportEndOffset
            }
            if (isTargetFullyVisible && targetRequester.safeRequestFocus()) {
                true
            } else {
                coroutineScope.launch {
                    runCatching { myListState.scrollToItem(targetIdx, 0) }
                    for (attempt in 1..6) {
                        androidx.compose.runtime.withFrameNanos { }
                        if (targetRequester.safeRequestFocus()) break
                    }
                }
                true
            }
        }
    }

    val handleTopBoundaryUp: () -> Boolean = {
        if (myListState.firstVisibleItemIndex != 0 || myListState.firstVisibleItemScrollOffset != 0) {
            coroutineScope.launch {
                runCatching { myListState.scrollToItem(0, 0) }
            }
        }
        topBarFocusRequester.safeRequestFocus()
    }

    LazyColumn(
        state = myListState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("my_list_screen"),
        contentPadding = contentPadding
    ) {
        if (hasContinueWatching) {
            item(key = "my_list_continue_watching", contentType = "continue_watching") {
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
                    onRemoveClick = onRemoveContinueWatching,
                    onTvLongPress = onTvLongPressContinueWatching,
                    onItemFocused = onRememberContentFocus,
                    isTvLayout = isTvLayout,
                    firstItemFocusRequester = firstRowFocusRequester,
                    upFocusRequester = topBarFocusRequester,
                    downFocusRequester = if (hasWatchlist) watchlistSecondRowRequester else null,
                    onUpKey = handleTopBoundaryUp,
                    onDownKey = {
                        if (hasWatchlist) {
                            navigateMyListSection(1, watchlistSecondRowRequester, false)
                        } else {
                            false
                        }
                    },
                    navRailFocusRequester = navRailFocusRequester
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (safeWatchlist.isEmpty() && continueWatching.isEmpty()) {
            item(key = "my_list_empty", contentType = "empty_state") {
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
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onBrowseCatalog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NetflixRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .safeFocusRequester(firstRowFocusRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionUp -> handleTopBoundaryUp()
                                            Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .focusProperties {
                                    navRailFocusRequester.takeIfAttached()?.let { left = it }
                                    topBarFocusRequester.takeIfAttached()?.let { up = it }
                                }
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedScale = 1.05f
                                )
                                .testTag("browse_catalog_from_empty_list_button")
                        ) {
                            Text(
                                text = "Sfoglia il Catalogo",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        } else if (hasWatchlist) {
            val watchlistIsFirstRow = !hasContinueWatching
            val watchlistRequester = if (watchlistIsFirstRow) firstRowFocusRequester else watchlistSecondRowRequester
            item(key = "my_list_watchlist", contentType = "media_row") {
                MediaCatalogRow(
                    title = "Salvati nella Mia Lista (${mappedItems.size})",
                    items = mappedItems,
                    onItemClick = onOpenItem,
                    onItemQuickPlay = onPlayItem,
                    onItemLongClick = onTvLongPressPoster,
                    onItemFocused = onRememberContentFocus,
                    firstItemFocusRequester = watchlistRequester,
                    upFocusRequester = if (watchlistIsFirstRow) topBarFocusRequester else firstRowFocusRequester,
                    onUpKey = {
                        if (watchlistIsFirstRow) {
                            handleTopBoundaryUp()
                        } else {
                            navigateMyListSection(0, firstRowFocusRequester, true)
                        }
                    },
                    navRailFocusRequester = navRailFocusRequester
                )
            }
        }
    }
}
