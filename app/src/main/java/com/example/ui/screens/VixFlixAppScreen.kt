package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
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
    var isRailFocused by remember { mutableStateOf(false) }
    var tvQuickActionEntry by remember { mutableStateOf<WatchProgressEntity?>(null) }
    var tvQuickActionItem by remember { mutableStateOf<MediaItem?>(null) }
    var lastBackPressMs by remember { mutableLongStateOf(0L) }
    var showExitHintBanner by remember { mutableStateOf(false) }

    val heroPlayFocusRequester = remember { FocusRequester() }
    val movieGenreBarFocusRequester = remember { FocusRequester() }
    val platformStripFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }
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
            delay(80L)
            searchInputFocusRequester.safeRequestFocus()
        }
    }

    // Focus iniziale per cambio scheda / caricamento completato / uscita dallo splash
    LaunchedEffect(
        isTvDevice,
        isCatalogInteractive,
        selectedTab,
        catalogState is CatalogUiState.Success,
        catalogState is CatalogUiState.Error
    ) {
        if (isTvDevice && isCatalogInteractive && !isSearchBarVisible) {
            val target = tabContentEntryFocusRequester ?: return@LaunchedEffect
            delay(140L)
            target.safeRequestFocus()
        }
    }

    // Ripristino focus sull'ultima locandina alla chiusura di Dettagli / Player
    LaunchedEffect(isCatalogInteractive, isTvDevice) {
        if (wasOverlayActive && isCatalogInteractive && isTvDevice && !isSearchBarVisible) {
            val target = lastFocusedContentRequester.takeIfAttached() ?: tabContentEntryFocusRequester
            if (target != null) {
                delay(120L)
                target.safeRequestFocus()
            }
        }
        wasOverlayActive = !isCatalogInteractive
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
        val useTvNavigationRail = isTvDevice || maxWidth >= 840.dp

        // Gestione tasto INDIETRO ottimale per Android TV / Google TV nel catalogo principale:
        // 1. Chiude menu rapido o ricerca se aperti
        // 2. Sposta prima il fuoco sulla barra laterale compatta se l'utente è sui contenuti
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
                useTvNavigationRail && !isRailFocused -> {
                    navRailSelectedFocusRequester.safeRequestFocus()
                }
                selectedTab != NavTab.HOME -> {
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = if (useTvNavigationRail) 72.dp else 0.dp)
                        .background(NetflixBlack)
                ) {
                    val topBarOffset = if (useTvNavigationRail) 78.dp else 122.dp
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
                                firstItemFocusRequester = searchFirstItemFocusRequester,
                                navRailFocusRequester = navRailSelectedFocusRequester,
                                upFocusRequester = searchInputFocusRequester,
                                onItemFocused = { lastFocusedContentRequester = it },
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
                                firstControlFocusRequester = settingsFirstControlFocusRequester,
                                navRailFocusRequester = navRailSelectedFocusRequester,
                                upFocusRequester = topBarBrandFocusRequester,
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
                                firstRowFocusRequester = firstRowFocusRequester,
                                navRailFocusRequester = navRailSelectedFocusRequester,
                                topBarFocusRequester = topBarBrandFocusRequester,
                                onBrowseCatalog = { viewModel.selectTab(NavTab.HOME) },
                                onOpenItem = viewModel::openDetails,
                                onPlayItem = { viewModel.startPlayback(it) },
                                onResumeContinueWatching = viewModel::resumeFromContinueWatching,
                                onRemoveContinueWatching = { viewModel.removeContinueWatching(it.id) },
                                onTvLongPressContinueWatching = { entry -> tvQuickActionEntry = entry },
                                onTvLongPressPoster = { item -> tvQuickActionItem = item },
                                onRememberContentFocus = { requester -> lastFocusedContentRequester = requester },
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
                                                    .safeFocusRequester(retryButtonFocusRequester)
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
                                        heroPlayFocusRequester = heroPlayFocusRequester,
                                        movieGenreBarFocusRequester = movieGenreBarFocusRequester,
                                        platformStripFocusRequester = platformStripFocusRequester,
                                        firstRowFocusRequester = firstRowFocusRequester,
                                        navRailFocusRequester = navRailSelectedFocusRequester,
                                        topBarFocusRequester = topBarBrandFocusRequester,
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
                        isExpanded = isRailFocused,
                        selectedItemFocusRequester = navRailSelectedFocusRequester,
                        rightToContentFocusRequester = activeContentEntryFocusRequester,
                        fallbackRightFocusRequester = topBarBrandFocusRequester,
                        onFocusChanged = { isRailFocused = it },
                        onSelectTab = { tab ->
                            isSearchBarVisible = false
                            viewModel.clearSearch()
                            viewModel.selectTab(tab)
                        },
                        onMoveFocusRight = {
                            lastFocusedContentRequester.safeRequestFocus() ||
                                tabContentEntryFocusRequester.safeRequestFocus() ||
                                heroPlayFocusRequester.safeRequestFocus() ||
                                firstRowFocusRequester.safeRequestFocus()
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
    isExpanded: Boolean,
    selectedItemFocusRequester: FocusRequester,
    rightToContentFocusRequester: FocusRequester?,
    fallbackRightFocusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit,
    onSelectTab: (NavTab) -> Unit,
    onMoveFocusRight: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val railWidth by animateDpAsState(
        targetValue = if (isExpanded) 216.dp else 72.dp,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "tvExpandableRailWidth"
    )

    Column(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(if (isExpanded) NetflixCardSurface else NetflixDarkSurface)
            .statusBarsPadding()
            .padding(top = 14.dp, bottom = 16.dp, start = 10.dp, end = 10.dp)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .focusGroup()
            .focusProperties {
                (rightToContentFocusRequester.takeIfAttached() ?: fallbackRightFocusRequester.takeIfAttached())?.let {
                    right = it
                }
            }
            .testTag("tv_navigation_rail")
    ) {
        // Mini badge brand in cima alla barra laterale
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
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

        Spacer(modifier = Modifier.height(18.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            navItems.forEach { (tab, label, icon) ->
                val isSelected = selectedTab == tab
                var itemFocused by remember { mutableStateOf(false) }

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
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .then(
                            if (itemFocused) {
                                Modifier.border(2.dp, NetflixWhite, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (isSelected) {
                                Modifier.safeFocusRequester(selectedItemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .focusProperties {
                            (rightToContentFocusRequester.takeIfAttached() ?: fallbackRightFocusRequester.takeIfAttached())?.let {
                                right = it
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.DirectionRight
                            ) {
                                onMoveFocusRight()
                            } else if (keyEvent.type == KeyEventType.KeyUp &&
                                (keyEvent.key == Key.DirectionCenter ||
                                    keyEvent.key == Key.Enter ||
                                    keyEvent.key == Key.NumPadEnter)
                            ) {
                                activateAndMoveRight()
                                true
                            } else {
                                false
                            }
                        }
                        .onFocusChanged { itemFocused = it.isFocused }
                        .focusable()
                        .clickable { activateAndMoveRight() }
                        .padding(horizontal = 12.dp)
                        .testTag("tv_rail_${tab.name.lowercase()}"),
                    contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
                ) {
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
    topBarFocusRequester: FocusRequester,
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
            NavTab.SERIES -> continueWatching.filter { it.mediaType == "tv" }
            NavTab.MOVIES -> continueWatching.filter { it.mediaType == "movie" }
            else -> continueWatching
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
    val firstRowUpTarget = when {
        selectedTab == NavTab.MOVIES && !showHero -> movieGenreBarFocusRequester
        selectedTab == NavTab.HOME && activePlatform != null -> platformStripFocusRequester
        showHero -> heroPlayFocusRequester
        else -> topBarFocusRequester
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("catalog_lazy_column"),
            contentPadding = PaddingValues(bottom = bottomPadding + 32.dp)
        ) {
            if (selectedTab == NavTab.MOVIES) {
                item(key = "movies_top_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(if (isTvLayout) 74.dp else 118.dp))
                }
                item(key = "movies_genre_chip_bar", contentType = "genre_bar") {
                    MovieGenreChipBar(
                        genres = movieGenreState.genres,
                        selectedGenre = activeMovieGenre,
                        onSelectGenre = onSelectMovieGenre,
                        firstChipFocusRequester = movieGenreBarFocusRequester,
                        upFocusRequester = topBarFocusRequester,
                        downFocusRequester = when {
                            showHero -> heroPlayFocusRequester
                            activeMovieGenre != null && (movieGenreState.isLoading || (movieGenreState.errorMessage == null && movieGenreState.movies.isEmpty())) -> null
                            else -> firstRowFocusRequester
                        },
                        navRailFocusRequester = navRailFocusRequester,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            if (selectedTab == NavTab.HOME && activePlatform != null) {
                item(key = "home_platform_top_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(if (isTvLayout) 74.dp else 118.dp))
                }
            }

            if (showHero) {
                item(key = "hero_${heroItem.uniqueKey}", contentType = "hero") {
                    val isInWatchlist = heroItem.uniqueKey in watchlistIds
                    val savedProgress = progressByKey[heroItem.uniqueKey]
                    NetflixHeroBanner(
                        item = heroItem,
                        isInWatchlist = isInWatchlist,
                        savedProgress = savedProgress,
                        onPlayClick = { onPlayItem(heroItem) },
                        onToggleWatchlist = { onToggleWatchlist(heroItem) },
                        onInfoClick = { onOpenDetails(heroItem) },
                        playButtonFocusRequester = heroPlayFocusRequester,
                        navRailFocusRequester = navRailFocusRequester,
                        topBarFocusRequester = if (selectedTab == NavTab.MOVIES) movieGenreBarFocusRequester else topBarFocusRequester,
                        firstRowFocusRequester = if (selectedTab == NavTab.HOME && !hasContinueWatching) platformStripFocusRequester else firstRowFocusRequester,
                        isTvLayout = isTvLayout
                    )
                }
            } else if (selectedTab == NavTab.NEW_POPULAR) {
                item(key = "new_popular_top_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(if (isTvLayout) 84.dp else 118.dp))
                }
            }

            // Sezione "Continua a guardare" (Room DB + startAt VixSrc API)
            if (hasContinueWatching) {
                item(key = "row_continue_watching", contentType = "continue_watching") {
                    ContinueWatchingSection(
                        entries = filteredContinueWatching,
                        onResumeClick = onResumeContinueWatching,
                        onInfoClick = onInfoContinueWatching,
                        onRemoveClick = onRemoveContinueWatching,
                        onTvLongPress = onTvLongPressContinueWatching,
                        onItemFocused = onRememberContentFocus,
                        isTvLayout = isTvLayout,
                        firstItemFocusRequester = firstRowFocusRequester,
                        upFocusRequester = firstRowUpTarget,
                        navRailFocusRequester = navRailFocusRequester
                    )
                }
            }

        when (selectedTab) {
            NavTab.HOME -> {
                // Striscia orizzontale con tutte le piattaforme streaming e relativo logo ufficiale TMDB
                // (Disney+, Paramount+, Prime Video, Netflix e le altre principali)
                item(key = "row_streaming_platforms_strip", contentType = "platform_strip") {
                    StreamingPlatformsStrip(
                        providers = platformState.providers,
                        selectedProvider = activePlatform,
                        onSelectProvider = onSelectStreamingPlatform,
                        showHeader = true,
                        firstItemFocusRequester = platformStripFocusRequester,
                        upFocusRequester = if (activePlatform != null) topBarFocusRequester else heroPlayFocusRequester,
                        downFocusRequester = if (activePlatform != null && (platformState.isLoading || (platformState.errorMessage == null && platformState.displayedTitles.isEmpty()))) {
                            null
                        } else {
                            firstRowFocusRequester
                        },
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
                                                .focusProperties {
                                                    navRailFocusRequester.takeIfAttached()?.let { left = it }
                                                    platformStripFocusRequester.takeIfAttached()?.let { up = it }
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
                            if (platformState.mediaTypeFilter == PlatformFilterMediaType.ALL &&
                                platformState.movies.isNotEmpty() &&
                                platformState.series.isNotEmpty()
                            ) {
                                item(key = "platform_${activePlatform.providerId}_movies_row", contentType = "media_row") {
                                    MediaCatalogRow(
                                        title = "Film Popolari su ${activePlatform.displayName}",
                                        items = platformState.movies,
                                        onItemClick = onOpenDetails,
                                        onItemQuickPlay = onPlayItem,
                                        onItemLongClick = onTvLongPressPoster,
                                        onItemFocused = onRememberContentFocus,
                                        firstItemFocusRequester = firstRowFocusRequester,
                                        upFocusRequester = platformStripFocusRequester,
                                        navRailFocusRequester = navRailFocusRequester
                                    )
                                }
                                item(key = "platform_${activePlatform.providerId}_series_row", contentType = "media_row") {
                                    MediaCatalogRow(
                                        title = "Serie TV Popolari su ${activePlatform.displayName}",
                                        items = platformState.series,
                                        onItemClick = onOpenDetails,
                                        onItemQuickPlay = onPlayItem,
                                        onItemLongClick = onTvLongPressPoster,
                                        onItemFocused = onRememberContentFocus,
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

                            val gridHasFirstRowRequester = !(
                                platformState.mediaTypeFilter == PlatformFilterMediaType.ALL &&
                                    platformState.movies.isNotEmpty() &&
                                    platformState.series.isNotEmpty()
                                )

                            items(
                                count = platformRows.size,
                                key = { rowIdx -> "platform_${activePlatform.providerId}_${platformState.mediaTypeFilter.name}_row_$rowIdx" },
                                contentType = { "platform_grid_row" }
                            ) { rowIdx ->
                                val rowItems = platformRows[rowIdx]
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
                                            val itemFocusRequester = if (gridHasFirstRowRequester && rowIdx == 0 && colIdx == 0) {
                                                firstRowFocusRequester
                                            } else {
                                                remember(activePlatform.providerId, platformState.mediaTypeFilter, item.uniqueKey) {
                                                    FocusRequester()
                                                }
                                            }
                                            MediaPosterCard(
                                                item = item,
                                                itemIndex = rowIdx * gridColumns + colIdx,
                                                onClick = { onOpenDetails(item) },
                                                onQuickPlay = { onPlayItem(item) },
                                                onLongClick = { onTvLongPressPoster(item) },
                                                onFocused = { onRememberContentFocus(itemFocusRequester) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .safeFocusRequester(itemFocusRequester)
                                                    .focusProperties {
                                                        if (colIdx == 0) {
                                                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                                                        }
                                                        if (gridHasFirstRowRequester && rowIdx == 0) {
                                                            platformStripFocusRequester.takeIfAttached()?.let { up = it }
                                                        }
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
                    val top10IsFirstRow = !hasContinueWatching && feed.top10Today.isNotEmpty()
                    val trendingMoviesIsFirstRow = !hasContinueWatching && feed.top10Today.isEmpty()

                    item(key = "row_top_10", contentType = "top10_row") {
                        Top10Section(
                            title = "Top 10 in Italia Oggi",
                            items = feed.top10Today,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            firstItemFocusRequester = if (top10IsFirstRow) firstRowFocusRequester else null,
                            upFocusRequester = if (top10IsFirstRow) platformStripFocusRequester else null,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "row_trending_movies", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Film di Tendenza su TMDB",
                            items = feed.trendingMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            firstItemFocusRequester = if (trendingMoviesIsFirstRow) firstRowFocusRequester else null,
                            upFocusRequester = if (trendingMoviesIsFirstRow) platformStripFocusRequester else null,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "row_trending_tv", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Serie TV del Momento",
                            items = feed.trendingTv,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "row_popular_movies", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Film Popolari su VixFlix",
                            items = feed.popularMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "row_popular_tv", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Serie TV Acclamate",
                            items = feed.popularTv,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "row_now_playing", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Ora al Cinema",
                            items = feed.nowPlayingMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "row_top_rated", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Più Votati dalla Critica",
                            items = feed.topRatedMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "row_action", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Azione & Adrenalina",
                            items = feed.actionMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "row_scifi", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Fantascienza & Mondi Futuri",
                            items = feed.sciFiMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                }
            }

            NavTab.SERIES -> {
                item(key = "tv_trending", contentType = "media_row") {
                    MediaCatalogRow(
                        title = "Serie TV in Tendenza",
                        items = feed.trendingTv,
                        onItemClick = onOpenDetails,
                        onItemQuickPlay = onPlayItem,
                        onItemLongClick = onTvLongPressPoster,
                        onItemFocused = onRememberContentFocus,
                        firstItemFocusRequester = if (!hasContinueWatching) firstRowFocusRequester else null,
                        upFocusRequester = if (!hasContinueWatching) firstRowUpTarget else null,
                        navRailFocusRequester = navRailFocusRequester
                    )
                }
                item(key = "tv_popular", contentType = "media_row") {
                    MediaCatalogRow(
                        title = "Serie TV Popolari",
                        items = feed.popularTv,
                        onItemClick = onOpenDetails,
                        onItemQuickPlay = onPlayItem,
                        onItemLongClick = onTvLongPressPoster,
                        onItemFocused = onRememberContentFocus,
                        navRailFocusRequester = navRailFocusRequester
                    )
                }
            }

            NavTab.MOVIES -> {
                if (activeMovieGenre != null) {
                    item(key = "movies_genre_header_${activeMovieGenre.id}", contentType = "genre_header") {
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
                                    .focusProperties {
                                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                                        movieGenreBarFocusRequester.takeIfAttached()?.let { up = it }
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
                                val rowItems = genreRows[rowIdx]
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
                                            val itemFocusRequester = if (rowIdx == 0 && colIdx == 0) {
                                                firstRowFocusRequester
                                            } else {
                                                remember(activeMovieGenre.id, item.uniqueKey) { FocusRequester() }
                                            }
                                            MediaPosterCard(
                                                item = item,
                                                itemIndex = rowIdx * gridColumns + colIdx,
                                                onClick = { onOpenDetails(item) },
                                                onQuickPlay = { onPlayItem(item) },
                                                onLongClick = { onTvLongPressPoster(item) },
                                                onFocused = { onRememberContentFocus(itemFocusRequester) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .safeFocusRequester(itemFocusRequester)
                                                    .focusProperties {
                                                        if (colIdx == 0) {
                                                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                                                        }
                                                        if (rowIdx == 0) {
                                                            movieGenreBarFocusRequester.takeIfAttached()?.let { up = it }
                                                        }
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
                    item(key = "movies_trending", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Film di Tendenza",
                            items = feed.trendingMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            firstItemFocusRequester = if (!hasContinueWatching) firstRowFocusRequester else null,
                            upFocusRequester = if (!hasContinueWatching) firstRowUpTarget else null,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "movies_now_playing", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Ora al Cinema",
                            items = feed.nowPlayingMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "movies_popular", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Film Popolari",
                            items = feed.popularMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "movies_top_rated", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Film Più Votati",
                            items = feed.topRatedMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "movies_action", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Azione & Avventura",
                            items = feed.actionMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                    item(key = "movies_scifi", contentType = "media_row") {
                        MediaCatalogRow(
                            title = "Fantascienza",
                            items = feed.sciFiMovies,
                            onItemClick = onOpenDetails,
                            onItemQuickPlay = onPlayItem,
                            onItemLongClick = onTvLongPressPoster,
                            onItemFocused = onRememberContentFocus,
                            navRailFocusRequester = navRailFocusRequester
                        )
                    }
                }
            }

            NavTab.NEW_POPULAR -> {
                val top10IsFirstRow = !hasContinueWatching && feed.top10Today.isNotEmpty()
                val cinemaIsFirstRow = !hasContinueWatching && feed.top10Today.isEmpty()

                item(key = "new_top10", contentType = "top10_row") {
                    Top10Section(
                        title = "Top 10 in Italia Oggi",
                        items = feed.top10Today,
                        onItemClick = onOpenDetails,
                        onItemQuickPlay = onPlayItem,
                        onItemLongClick = onTvLongPressPoster,
                        onItemFocused = onRememberContentFocus,
                        firstItemFocusRequester = if (top10IsFirstRow) firstRowFocusRequester else null,
                        upFocusRequester = if (top10IsFirstRow) firstRowUpTarget else null,
                        navRailFocusRequester = navRailFocusRequester
                    )
                }
                item(key = "new_cinema", contentType = "media_row") {
                    MediaCatalogRow(
                        title = "Nuove Uscite al Cinema",
                        items = feed.nowPlayingMovies,
                        onItemClick = onOpenDetails,
                        onItemQuickPlay = onPlayItem,
                        onItemLongClick = onTvLongPressPoster,
                        onItemFocused = onRememberContentFocus,
                        firstItemFocusRequester = if (cinemaIsFirstRow) firstRowFocusRequester else null,
                        upFocusRequester = if (cinemaIsFirstRow) firstRowUpTarget else null,
                        navRailFocusRequester = navRailFocusRequester
                    )
                }
                item(key = "new_tv", contentType = "media_row") {
                    MediaCatalogRow(
                        title = "Serie TV Nuove e Popolari",
                        items = feed.trendingTv,
                        onItemClick = onOpenDetails,
                        onItemQuickPlay = onPlayItem,
                        onItemLongClick = onTvLongPressPoster,
                        onItemFocused = onRememberContentFocus,
                        navRailFocusRequester = navRailFocusRequester
                    )
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

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (isTvLayout) 136.dp else 110.dp),
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
            val itemFocusRequester = if (index == 0) {
                firstItemFocusRequester
            } else {
                remember(item.uniqueKey) { FocusRequester() }
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
                    .focusProperties {
                        if (index == 0) {
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                            upFocusRequester.takeIfAttached()?.let { up = it }
                        }
                    }
            )
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
    topBarFocusRequester: FocusRequester,
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("my_list_screen"),
        contentPadding = contentPadding
    ) {
        if (continueWatching.isNotEmpty()) {
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
        } else if (mappedItems.isNotEmpty()) {
            val watchlistIsFirstRow = continueWatching.isEmpty()
            item(key = "my_list_watchlist", contentType = "media_row") {
                MediaCatalogRow(
                    title = "Salvati nella Mia Lista (${mappedItems.size})",
                    items = mappedItems,
                    onItemClick = onOpenItem,
                    onItemQuickPlay = onPlayItem,
                    onItemLongClick = onTvLongPressPoster,
                    onItemFocused = onRememberContentFocus,
                    firstItemFocusRequester = if (watchlistIsFirstRow) firstRowFocusRequester else null,
                    upFocusRequester = if (watchlistIsFirstRow) topBarFocusRequester else null,
                    navRailFocusRequester = navRailFocusRequester
                )
            }
        }
    }
}
