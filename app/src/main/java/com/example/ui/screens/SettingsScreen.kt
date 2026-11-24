package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.local.CacheStatsSummary
import com.example.data.local.PlayerAdvancedSettings
import com.example.data.local.PlayerComfortSettings
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.SubtitleBackgroundStyle
import com.example.data.local.SubtitleTextColor
import com.example.data.local.SubtitleTextSize
import com.example.data.local.UserProfileUiState
import com.example.data.local.VideoAspectMode
import com.example.data.remote.TmdbApiConfig
import com.example.data.remote.simkl.SimklConnectionState
import com.example.data.remote.simkl.SimklProfileUiState
import com.example.ui.GitHubUpdateUiState
import com.example.ui.components.DpadFocusObserverRegistry
import com.example.ui.components.DpadFocusScope
import com.example.ui.components.ProfileAvatarArtwork
import com.example.ui.components.rememberTvVerticalBringIntoViewSpec
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.launch

/**
 * Categorie modulari della sezione Impostazioni.
 * Aggiungere una voce qui consente di estendere facilmente nuove aree di configurazione future.
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    PLAYBACK("Riproduzione", "Audio, player e sottotitoli", Icons.Default.Tune),
    UPDATES("Aggiornamenti", "GitHub Releases e auto-check", Icons.Default.SystemUpdate),
    API_SOURCES("API & Sorgenti", "Chiave TMDB e config", Icons.Default.Key),
    SYSTEM("Sistema & Dati", "Profili, Simkl e cache", Icons.Default.Info)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    gitHubState: GitHubUpdateUiState,
    streamLang: String,
    preferWebPlayer: Boolean,
    bufferPreset: StreamingBufferPreset,
    autoCheckUpdates: Boolean,
    continueWatchingCount: Int,
    watchlistCount: Int,
    isTvLayout: Boolean,
    requestedCategory: SettingsCategory = SettingsCategory.PLAYBACK,
    requestedCategoryNonce: Int = 0,
    onSelectStreamLang: (String) -> Unit,
    onSetPreferWebPlayer: (Boolean) -> Unit,
    onSelectBufferPreset: (StreamingBufferPreset) -> Unit,
    onSetAutoCheckUpdates: (Boolean) -> Unit,
    onRefreshCatalog: () -> Unit,
    onClearContinueWatching: () -> Unit,
    onRepoSlugChange: (String) -> Unit,
    onCheckUpdates: (String) -> Unit,
    onTriggerUpdate: () -> Unit,
    onUpdateTmdbKey: (String) -> Unit,
    onResetTmdbKey: () -> Unit,
    backgroundPrefetchEnabled: Boolean = true,
    cacheStats: CacheStatsSummary = CacheStatsSummary(),
    isBackgroundCatalogRefreshing: Boolean = false,
    cacheClearedFeedbackNonce: Int = 0,
    onSetBackgroundPrefetch: (Boolean) -> Unit = {},
    onClearLocalCache: () -> Unit = {},
    profileUiState: UserProfileUiState = UserProfileUiState(),
    onOpenProfileSelector: (Boolean) -> Unit = {},
    onSetAutoLoginProfile: (Boolean) -> Unit = {},
    onSetDefaultProfileId: (String?) -> Unit = {},
    simklUiState: SimklProfileUiState = SimklProfileUiState(),
    onConnectSimkl: () -> Unit = {},
    onSyncSimklNow: () -> Unit = {},
    onUnlinkSimkl: () -> Unit = {},
    playerAdvancedSettings: PlayerAdvancedSettings = PlayerAdvancedSettings(),
    onSetSkipIntroEnabled: (Boolean) -> Unit = {},
    onSetNextEpisodePromptEnabled: (Boolean) -> Unit = {},
    onSetAutoPlayNextEpisodeEnabled: (Boolean) -> Unit = {},
    onSetNextEpisodeCountdownSec: (Int) -> Unit = {},
    onSetCreditsDetectionEnabled: (Boolean) -> Unit = {},
    onSetManualMovieCompletionEnabled: (Boolean) -> Unit = {},
    playerComfortSettings: PlayerComfortSettings = PlayerComfortSettings(),
    onSelectVideoAspectMode: (VideoAspectMode) -> Unit = {},
    onSelectSubtitleTextSize: (SubtitleTextSize) -> Unit = {},
    onSelectSubtitleTextColor: (SubtitleTextColor) -> Unit = {},
    onSelectSubtitleBackgroundStyle: (SubtitleBackgroundStyle) -> Unit = {},
    firstControlFocusRequester: FocusRequester? = null,
    navRailFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    tabScrollResetNonce: Int = 0,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var selectedCategory by remember { mutableStateOf(requestedCategory) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollStepPx = remember(density) { with(density) { 220.dp.roundToPx() } }

    val categoryTabFocusRequesters = remember {
        SettingsCategory.entries.associateWith { FocusRequester() }
    }
    val panelFirstControlFocusRequesters = remember {
        SettingsCategory.entries.associateWith { FocusRequester() }
    }
    val panelFirstControlFocusRequester = panelFirstControlFocusRequesters.getValue(selectedCategory)

    val resolveCategoryTabRequester: (SettingsCategory) -> FocusRequester = { category ->
        if (category == SettingsCategory.PLAYBACK && firstControlFocusRequester != null) {
            firstControlFocusRequester
        } else {
            categoryTabFocusRequesters.getValue(category)
        }
    }

    LaunchedEffect(Unit) {
        repeat(3) {
            withFrameNanos { }
            resolveCategoryTabRequester(selectedCategory).safeRequestFocus()
        }
    }

    LaunchedEffect(requestedCategoryNonce, requestedCategory) {
        if (requestedCategoryNonce > 0) {
            selectedCategory = requestedCategory
            runCatching { listState.scrollToItem(0, 0) }
            repeat(3) {
                withFrameNanos { }
                resolveCategoryTabRequester(requestedCategory).safeRequestFocus()
            }
        }
    }

    LaunchedEffect(tabScrollResetNonce) {
        if (tabScrollResetNonce > 0) {
            runCatching { listState.scrollToItem(0, 0) }
            repeat(3) {
                withFrameNanos { }
                resolveCategoryTabRequester(selectedCategory).safeRequestFocus()
            }
        }
    }

    val scrollToTopOnly: () -> Unit = {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            coroutineScope.launch {
                runCatching { listState.scrollToItem(0, 0) }
            }
        }
    }

    val scrollToTopAndFocusCategoryBar: () -> Boolean = {
        val targetRequester = resolveCategoryTabRequester(selectedCategory)
        coroutineScope.launch {
            runCatching { listState.scrollToItem(0, 0) }
            for (attempt in 1..6) {
                withFrameNanos { }
                if (targetRequester.safeRequestFocus()) {
                    break
                }
            }
            withFrameNanos { }
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                runCatching { listState.scrollToItem(0, 0) }
            }
        }
        targetRequester.safeRequestFocus()
        true
    }

    val scrollDownAndFocusPanel: (FocusRequester) -> Boolean = { targetRequester ->
        if (targetRequester.safeRequestFocus()) {
            true
        } else {
            coroutineScope.launch {
                repeat(2) { withFrameNanos { } }
                if (!targetRequester.safeRequestFocus()) {
                    for (attempt in 1..4) {
                        runCatching { listState.scrollBy(scrollStepPx.toFloat()) }
                        repeat(2) { withFrameNanos { } }
                        if (targetRequester.safeRequestFocus()) {
                            break
                        }
                    }
                }
            }
            true
        }
    }

    val hasUpdateAvailable = gitHubState.releaseInfo?.isUpdateAvailable == true
    val verticalBringIntoViewSpec = rememberTvVerticalBringIntoViewSpec(
        topMargin = 44.dp,
        bottomMargin = 56.dp
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        val availableWidth = maxWidth
        val isCompactTv = isTvLayout && availableWidth < 760.dp
        val isWideTabBar = availableWidth >= 620.dp
        val showTabSubtitles = availableWidth >= 820.dp
        val horizontalPadding = when {
            !isTvLayout -> 16.dp
            isCompactTv -> 20.dp
            else -> 32.dp
        }
        val maxContentWidth = 980.dp

        CompositionLocalProvider(
            LocalBringIntoViewSpec provides verticalBringIntoViewSpec
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings_screen"),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + if (isCompactTv) 12.dp else 18.dp,
                    bottom = contentPadding.calculateBottomPadding() + 56.dp,
                    start = horizontalPadding,
                    end = horizontalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(if (isCompactTv) 12.dp else 16.dp)
            ) {
                item(key = "settings_header") {
                    SettingsHeaderBanner(
                        isTvLayout = isTvLayout,
                        isCompactTv = isCompactTv,
                        streamLang = streamLang,
                        preferWebPlayer = preferWebPlayer,
                        bufferPreset = bufferPreset,
                        hasUpdateAvailable = hasUpdateAvailable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth)
                    )
                }

                item(key = "settings_category_bar") {
                    SettingsCategoryTabRow(
                        selectedCategory = selectedCategory,
                        hasUpdateAvailable = hasUpdateAvailable,
                        isWideTabBar = isWideTabBar,
                        showSubtitles = showTabSubtitles,
                        onSelectCategory = {
                            if (selectedCategory != it) {
                                selectedCategory = it
                            }
                            scrollToTopOnly()
                        },
                        resolveTabFocusRequester = resolveCategoryTabRequester,
                        panelFirstControlFocusRequester = panelFirstControlFocusRequester,
                        onTabFocused = { requester ->
                            onRememberContentFocus(requester)
                            scrollToTopOnly()
                        },
                        onUpFromTab = {
                            scrollToTopOnly()
                            upFocusRequester.safeRequestFocus()
                        },
                        onDownToPanel = {
                            scrollDownAndFocusPanel(panelFirstControlFocusRequesters.getValue(selectedCategory))
                        },
                        navRailFocusRequester = navRailFocusRequester,
                        upFocusRequester = upFocusRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth)
                    )
                }

                item(key = "settings_active_panel") {
                    AnimatedContent(
                        targetState = selectedCategory,
                        transitionSpec = {
                            (fadeIn(tween(180)) + androidx.compose.animation.slideInVertically(
                                animationSpec = tween(200, easing = FastOutSlowInEasing),
                                initialOffsetY = { it / 24 }
                            )).togetherWith(fadeOut(tween(130)))
                        },
                        label = "settingsCategoryTransition",
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth)
                    ) { category ->
                        val isCurrentCategory = category == selectedCategory
                        val categoryPanelFirstFocus = if (isCurrentCategory) {
                            panelFirstControlFocusRequesters.getValue(category)
                        } else {
                            remember { FocusRequester() }
                        }
                        val categoryTabRequester = resolveCategoryTabRequester(category)
                        when (category) {
                            SettingsCategory.PLAYBACK -> PlaybackSettingsPanel(
                                streamLang = streamLang,
                                preferWebPlayer = preferWebPlayer,
                                bufferPreset = bufferPreset,
                                playerAdvancedSettings = playerAdvancedSettings,
                                onSelectStreamLang = onSelectStreamLang,
                                onSetPreferWebPlayer = onSetPreferWebPlayer,
                                onSelectBufferPreset = onSelectBufferPreset,
                                onSetSkipIntroEnabled = onSetSkipIntroEnabled,
                                onSetNextEpisodePromptEnabled = onSetNextEpisodePromptEnabled,
                                onSetAutoPlayNextEpisodeEnabled = onSetAutoPlayNextEpisodeEnabled,
                                onSetNextEpisodeCountdownSec = onSetNextEpisodeCountdownSec,
                                onSetCreditsDetectionEnabled = onSetCreditsDetectionEnabled,
                                onSetManualMovieCompletionEnabled = onSetManualMovieCompletionEnabled,
                                playerComfortSettings = playerComfortSettings,
                                onSelectVideoAspectMode = onSelectVideoAspectMode,
                                onSelectSubtitleTextSize = onSelectSubtitleTextSize,
                                onSelectSubtitleTextColor = onSelectSubtitleTextColor,
                                onSelectSubtitleBackgroundStyle = onSelectSubtitleBackgroundStyle,
                                onRefreshCatalog = onRefreshCatalog,
                                firstControlFocusRequester = categoryPanelFirstFocus,
                                upToCategoryFocusRequester = categoryTabRequester,
                                onUpToCategoryBar = scrollToTopAndFocusCategoryBar,
                                onRememberContentFocus = onRememberContentFocus,
                                navRailFocusRequester = navRailFocusRequester
                            )

                            SettingsCategory.UPDATES -> UpdatesSettingsPanel(
                                state = gitHubState,
                                autoCheckUpdates = autoCheckUpdates,
                                onSetAutoCheckUpdates = onSetAutoCheckUpdates,
                                onRepoSlugChange = onRepoSlugChange,
                                onCheckUpdates = onCheckUpdates,
                                onTriggerUpdate = onTriggerUpdate,
                                firstControlFocusRequester = categoryPanelFirstFocus,
                                upToCategoryFocusRequester = categoryTabRequester,
                                onUpToCategoryBar = scrollToTopAndFocusCategoryBar,
                                onRememberContentFocus = onRememberContentFocus,
                                navRailFocusRequester = navRailFocusRequester
                            )

                            SettingsCategory.API_SOURCES -> ApiSourcesSettingsPanel(
                                state = gitHubState,
                                onUpdateTmdbKey = onUpdateTmdbKey,
                                onResetTmdbKey = onResetTmdbKey,
                                firstControlFocusRequester = categoryPanelFirstFocus,
                                upToCategoryFocusRequester = categoryTabRequester,
                                onUpToCategoryBar = scrollToTopAndFocusCategoryBar,
                                onRememberContentFocus = onRememberContentFocus,
                                navRailFocusRequester = navRailFocusRequester
                            )

                            SettingsCategory.SYSTEM -> SystemSettingsPanel(
                                isTvLayout = isTvLayout,
                                isWideStatRow = availableWidth >= 720.dp,
                                continueWatchingCount = continueWatchingCount,
                                watchlistCount = watchlistCount,
                                backgroundPrefetchEnabled = backgroundPrefetchEnabled,
                                cacheStats = cacheStats,
                                isBackgroundCatalogRefreshing = isBackgroundCatalogRefreshing,
                                cacheClearedFeedbackNonce = cacheClearedFeedbackNonce,
                                profileUiState = profileUiState,
                                onOpenProfileSelector = onOpenProfileSelector,
                                onSetAutoLoginProfile = onSetAutoLoginProfile,
                                onSetDefaultProfileId = onSetDefaultProfileId,
                                simklUiState = simklUiState,
                                onConnectSimkl = onConnectSimkl,
                                onSyncSimklNow = onSyncSimklNow,
                                onUnlinkSimkl = onUnlinkSimkl,
                                onSetBackgroundPrefetch = onSetBackgroundPrefetch,
                                onRefreshCatalog = onRefreshCatalog,
                                onClearLocalCache = onClearLocalCache,
                                onClearContinueWatching = onClearContinueWatching,
                                firstControlFocusRequester = categoryPanelFirstFocus,
                                upToCategoryFocusRequester = categoryTabRequester,
                                onUpToCategoryBar = scrollToTopAndFocusCategoryBar,
                                onRememberContentFocus = onRememberContentFocus,
                                navRailFocusRequester = navRailFocusRequester
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeaderBanner(
    isTvLayout: Boolean,
    isCompactTv: Boolean,
    streamLang: String,
    preferWebPlayer: Boolean,
    bufferPreset: StreamingBufferPreset,
    hasUpdateAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NetflixCardSurface,
        border = BorderStroke(1.dp, NetflixElevatedSurface.copy(alpha = 0.85f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NetflixRed.copy(alpha = 0.14f),
                            NetflixCardSurface,
                            NetflixDarkSurface
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = if (isCompactTv) 12.dp else 15.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompactTv) 40.dp else 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NetflixRed.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Impostazioni",
                            tint = NetflixRed,
                            modifier = Modifier.size(if (isCompactTv) 22.dp else 24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Impostazioni VixFlix",
                            style = if (isCompactTv) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.headlineSmall
                            },
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} • ${if (isTvLayout) "TV & Telecomando" else "Smartphone"} • ${if (preferWebPlayer) "Web Player" else "ExoPlayer"} (${bufferPreset.sizeMb} MB)",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = NetflixElevatedSurface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = streamLang.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (hasUpdateAvailable) {
                        Surface(
                            color = NetflixMatchGreen.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NetflixMatchGreen.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = "UPDATE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetflixMatchGreen,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryTabRow(
    selectedCategory: SettingsCategory,
    hasUpdateAvailable: Boolean,
    isWideTabBar: Boolean,
    showSubtitles: Boolean,
    onSelectCategory: (SettingsCategory) -> Unit,
    resolveTabFocusRequester: (SettingsCategory) -> FocusRequester,
    panelFirstControlFocusRequester: FocusRequester?,
    onTabFocused: (FocusRequester) -> Unit,
    onUpFromTab: () -> Boolean,
    onDownToPanel: () -> Boolean,
    navRailFocusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val rowModifier = if (isWideTabBar) {
        modifier
            .focusGroup()
            .padding(vertical = 2.dp)
    } else {
        modifier
            .focusGroup()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 4.dp)
    }

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsCategory.entries.forEachIndexed { index, category ->
            val isSelected = category == selectedCategory
            val tabFocusRequester = resolveTabFocusRequester(category)
            var isFocused by remember { mutableStateOf(false) }

            val bgColor by animateColorAsState(
                targetValue = when {
                    isSelected -> NetflixRed
                    isFocused -> Color(0xFF282B34)
                    else -> NetflixCardSurface
                },
                animationSpec = tween(160),
                label = "settingsTabBg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) NetflixRed else NetflixElevatedSurface,
                animationSpec = tween(160),
                label = "settingsTabBorder"
            )

            val prevTabRequester = if (index > 0) {
                resolveTabFocusRequester(SettingsCategory.entries[index - 1])
            } else {
                navRailFocusRequester
            }
            val nextTabRequester = if (index + 1 < SettingsCategory.entries.size) {
                resolveTabFocusRequester(SettingsCategory.entries[index + 1])
            } else {
                null
            }

            val itemModifier = if (isWideTabBar) {
                Modifier.weight(1f)
            } else {
                Modifier
            }

            Surface(
                color = bgColor,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = itemModifier
                    .heightIn(min = if (showSubtitles) 58.dp else 48.dp)
                    .safeFocusRequester(tabFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionDown -> onDownToPanel()
                                Key.DirectionUp -> onUpFromTab()
                                Key.DirectionLeft -> prevTabRequester.safeRequestFocus()
                                Key.DirectionRight -> nextTabRequester?.safeRequestFocus() ?: true
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusProperties {
                        prevTabRequester.takeIfAttached()?.let { left = it }
                        nextTabRequester?.takeIfAttached()?.let { right = it }
                        upFocusRequester.takeIfAttached()?.let { up = it }
                        panelFirstControlFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    .tvFocusableControl(
                        shape = RoundedCornerShape(12.dp),
                        focusedBorderColor = NetflixWhite,
                        focusedScale = 1.0f,
                        borderWidth = 2.dp,
                        focusTag = "settings_tab_${category.name.lowercase()}",
                        onFocusChange = { focused ->
                            isFocused = focused
                            if (focused) {
                                onSelectCategory(category)
                                onTabFocused(tabFocusRequester)
                            }
                        },
                        onClick = { onSelectCategory(category) }
                    )
                    .testTag("settings_tab_${category.name.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.padding(
                        horizontal = if (isWideTabBar) 12.dp else 14.dp,
                        vertical = if (showSubtitles) 10.dp else 12.dp
                    )
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else NetflixLightGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = if (isWideTabBar) Modifier.weight(1f) else Modifier) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = NetflixWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (category == SettingsCategory.UPDATES && hasUpdateAvailable) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(NetflixMatchGreen)
                                )
                            }
                        }
                        if (showSubtitles) {
                            Text(
                                text = category.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color.White.copy(alpha = 0.82f) else NetflixLightGray,
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

@Composable
private fun PlaybackSettingsPanel(
    streamLang: String,
    preferWebPlayer: Boolean,
    bufferPreset: StreamingBufferPreset,
    playerAdvancedSettings: PlayerAdvancedSettings,
    onSelectStreamLang: (String) -> Unit,
    onSetPreferWebPlayer: (Boolean) -> Unit,
    onSelectBufferPreset: (StreamingBufferPreset) -> Unit,
    onSetSkipIntroEnabled: (Boolean) -> Unit,
    onSetNextEpisodePromptEnabled: (Boolean) -> Unit,
    onSetAutoPlayNextEpisodeEnabled: (Boolean) -> Unit,
    onSetNextEpisodeCountdownSec: (Int) -> Unit,
    onSetCreditsDetectionEnabled: (Boolean) -> Unit,
    onSetManualMovieCompletionEnabled: (Boolean) -> Unit,
    playerComfortSettings: PlayerComfortSettings,
    onSelectVideoAspectMode: (VideoAspectMode) -> Unit,
    onSelectSubtitleTextSize: (SubtitleTextSize) -> Unit,
    onSelectSubtitleTextColor: (SubtitleTextColor) -> Unit,
    onSelectSubtitleBackgroundStyle: (SubtitleBackgroundStyle) -> Unit,
    onRefreshCatalog: () -> Unit,
    firstControlFocusRequester: FocusRequester?,
    upToCategoryFocusRequester: FocusRequester?,
    onUpToCategoryBar: () -> Boolean,
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    navRailFocusRequester: FocusRequester?
) {
    var catalogRefreshed by remember { mutableStateOf(false) }

    val langFirstFocus = firstControlFocusRequester ?: remember { FocusRequester() }
    val playerFirstFocus = remember { FocusRequester() }
    val bufferFirstFocus = remember { FocusRequester() }
    val aspectFirstFocus = remember { FocusRequester() }
    val subSizeFirstFocus = remember { FocusRequester() }
    val subColorFirstFocus = remember { FocusRequester() }
    val subBgFirstFocus = remember { FocusRequester() }
    val skipIntroFocus = remember { FocusRequester() }
    val nextPromptFocus = remember { FocusRequester() }
    val autoPlayNextFocus = remember { FocusRequester() }
    val countdownFirstFocus = remember { FocusRequester() }
    val creditsFocus = remember { FocusRequester() }
    val manualCompleteFocus = remember { FocusRequester() }
    val refreshCatalogFocusRequester = remember { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsGroupHeader(title = "Preferenze Streaming")

        SettingsSectionCard(
            title = "Lingua Audio & Sottotitoli",
            subtitle = "Seleziona la lingua predefinita per i flussi VixSrc su film ed episodi",
            icon = Icons.Default.Language,
            badgeText = streamLang.uppercase(),
            badgeColor = NetflixMatchGreen
        ) {
            SettingsChoiceRow(
                options = listOf(
                    "it" to "Italiano (IT)",
                    "en" to "Inglese (EN)"
                ),
                selectedKey = streamLang.lowercase(),
                onSelect = onSelectStreamLang,
                firstOptionFocusRequester = langFirstFocus,
                upFocusRequester = upToCategoryFocusRequester,
                downFocusRequester = playerFirstFocus,
                onUpKey = onUpToCategoryBar,
                onRememberContentFocus = onRememberContentFocus,
                navRailFocusRequester = navRailFocusRequester,
                tagPrefix = "settings_lang"
            )
        }

        SettingsSectionCard(
            title = "Motore Player Predefinito",
            subtitle = "ExoPlayer nativo abilita ricerca timeline D-pad su Fire TV e Google TV",
            icon = Icons.Default.PlayCircleOutline,
            badgeText = if (preferWebPlayer) "WEB" else "NATIVO",
            badgeColor = if (preferWebPlayer) NetflixLightGray else NetflixRed
        ) {
            SettingsChoiceRow(
                options = listOf(
                    "native" to "ExoPlayer Nativo",
                    "web" to "Web Player VixSrc"
                ),
                selectedKey = if (preferWebPlayer) "web" else "native",
                onSelect = { key -> onSetPreferWebPlayer(key == "web") },
                firstOptionFocusRequester = playerFirstFocus,
                upFocusRequester = langFirstFocus,
                downFocusRequester = bufferFirstFocus,
                onRememberContentFocus = onRememberContentFocus,
                navRailFocusRequester = navRailFocusRequester,
                tagPrefix = "settings_player"
            )
        }

        SettingsSectionCard(
            title = "Buffer di Streaming",
            subtitle = "${bufferPreset.label}: ${bufferPreset.description}",
            icon = Icons.Default.Memory,
            badgeText = "${bufferPreset.sizeMb} MB",
            badgeColor = NetflixMatchGreen
        ) {
            BufferPresetGrid(
                selectedPreset = bufferPreset,
                onSelectPreset = onSelectBufferPreset,
                upFocusRequester = playerFirstFocus,
                downFocusRequester = aspectFirstFocus,
                firstPresetFocusRequester = bufferFirstFocus,
                onRememberContentFocus = onRememberContentFocus,
                navRailFocusRequester = navRailFocusRequester
            )
        }

        SettingsGroupHeader(title = "Comfort & Aspetto Video")

        SettingsSectionCard(
            title = stringResource(R.string.settings_comfort_title),
            subtitle = stringResource(R.string.settings_comfort_subtitle),
            icon = Icons.Default.AspectRatio,
            badgeText = playerComfortSettings.videoAspectMode.label.uppercase(),
            badgeColor = NetflixMatchGreen
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("settings_comfort_section")
            ) {
                SubtitleStylePreviewBox(
                    settings = playerComfortSettings,
                    compact = false,
                    modifier = Modifier.testTag("settings_subtitle_preview")
                )

                Text(
                    text = stringResource(R.string.player_comfort_aspect_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                SettingsChoiceRow(
                    options = VideoAspectMode.entries.map { it.storageKey to it.label },
                    selectedKey = playerComfortSettings.videoAspectMode.storageKey,
                    onSelect = { key -> onSelectVideoAspectMode(VideoAspectMode.fromStorageKey(key)) },
                    firstOptionFocusRequester = aspectFirstFocus,
                    upFocusRequester = bufferFirstFocus,
                    downFocusRequester = subSizeFirstFocus,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    tagPrefix = "settings_aspect"
                )

                Text(
                    text = stringResource(R.string.player_subtitle_size_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                SettingsChoiceRow(
                    options = SubtitleTextSize.entries.map { it.storageKey to it.label },
                    selectedKey = playerComfortSettings.subtitleTextSize.storageKey,
                    onSelect = { key -> onSelectSubtitleTextSize(SubtitleTextSize.fromStorageKey(key)) },
                    firstOptionFocusRequester = subSizeFirstFocus,
                    upFocusRequester = aspectFirstFocus,
                    downFocusRequester = subColorFirstFocus,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    tagPrefix = "settings_sub_size"
                )

                Text(
                    text = stringResource(R.string.player_subtitle_color_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                SettingsChoiceRow(
                    options = SubtitleTextColor.entries.map { it.storageKey to it.label },
                    selectedKey = playerComfortSettings.subtitleTextColor.storageKey,
                    onSelect = { key -> onSelectSubtitleTextColor(SubtitleTextColor.fromStorageKey(key)) },
                    firstOptionFocusRequester = subColorFirstFocus,
                    upFocusRequester = subSizeFirstFocus,
                    downFocusRequester = subBgFirstFocus,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    tagPrefix = "settings_sub_color"
                )

                Text(
                    text = stringResource(R.string.player_subtitle_background_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                SettingsChoiceRow(
                    options = SubtitleBackgroundStyle.entries.map { it.storageKey to it.label },
                    selectedKey = playerComfortSettings.subtitleBackgroundStyle.storageKey,
                    onSelect = { key -> onSelectSubtitleBackgroundStyle(SubtitleBackgroundStyle.fromStorageKey(key)) },
                    firstOptionFocusRequester = subBgFirstFocus,
                    upFocusRequester = subColorFirstFocus,
                    downFocusRequester = skipIntroFocus,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    tagPrefix = "settings_sub_bg"
                )
            }
        }

        SettingsGroupHeader(title = "Automazioni Riproduzione")

        SettingsSectionCard(
            title = "Funzionalità Avanzate Player",
            subtitle = "Personalizza Salta Intro, titoli di coda, episodio successivo e completamento film",
            icon = Icons.Default.Tune,
            badgeText = "${playerAdvancedSettings.sanitizedCountdownSec}s",
            badgeColor = NetflixRed
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSwitchRow(
                    label = "Salta intro",
                    description = "Mostra il pulsante SALTA INTRO quando esistono dati affidabili per l'episodio",
                    checked = playerAdvancedSettings.skipIntroEnabled,
                    onCheckedChange = onSetSkipIntroEnabled,
                    focusRequester = skipIntroFocus,
                    upFocusRequester = subBgFirstFocus,
                    downFocusRequester = nextPromptFocus,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    testTagName = "settings_skip_intro_switch"
                )

                SettingsSwitchRow(
                    label = "Proposta episodio successivo",
                    description = "Mostra il popup PROSSIMO EPISODIO sui titoli di coda se esiste un episodio successivo",
                    checked = playerAdvancedSettings.nextEpisodePromptEnabled,
                    onCheckedChange = onSetNextEpisodePromptEnabled,
                    focusRequester = nextPromptFocus,
                    upFocusRequester = skipIntroFocus,
                    downFocusRequester = autoPlayNextFocus,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    testTagName = "settings_next_episode_prompt_switch"
                )

                SettingsSwitchRow(
                    label = "Riproduzione automatica episodio successivo",
                    description = "Avvia automaticamente il prossimo episodio allo scadere del conto alla rovescia",
                    checked = playerAdvancedSettings.autoPlayNextEpisodeEnabled,
                    onCheckedChange = onSetAutoPlayNextEpisodeEnabled,
                    focusRequester = autoPlayNextFocus,
                    upFocusRequester = nextPromptFocus,
                    downFocusRequester = countdownFirstFocus,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    testTagName = "settings_auto_play_next_switch"
                )

                Text(
                    text = "Conto alla rovescia episodio successivo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                SettingsChoiceRow(
                    options = PlayerAdvancedSettings.ALLOWED_COUNTDOWN_OPTIONS.map { sec ->
                        sec.toString() to "${sec}s"
                    },
                    selectedKey = playerAdvancedSettings.sanitizedCountdownSec.toString(),
                    onSelect = { key ->
                        key.toIntOrNull()?.let(onSetNextEpisodeCountdownSec)
                    },
                    firstOptionFocusRequester = countdownFirstFocus,
                    upFocusRequester = autoPlayNextFocus,
                    downFocusRequester = creditsFocus,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    tagPrefix = "settings_countdown"
                )

                SettingsSwitchRow(
                    label = "Rilevamento titoli di coda",
                    description = "Attiva il rilevamento dei titoli di coda per serie TV e film",
                    checked = playerAdvancedSettings.creditsDetectionEnabled,
                    onCheckedChange = onSetCreditsDetectionEnabled,
                    focusRequester = creditsFocus,
                    upFocusRequester = countdownFirstFocus,
                    downFocusRequester = manualCompleteFocus,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    testTagName = "settings_credits_detection_switch"
                )

                SettingsSwitchRow(
                    label = "Conferma manuale completamento film",
                    description = "Mostra il pulsante Termina film nei controlli del player per completare il film manualmente",
                    checked = playerAdvancedSettings.manualMovieCompletionEnabled,
                    onCheckedChange = onSetManualMovieCompletionEnabled,
                    focusRequester = manualCompleteFocus,
                    upFocusRequester = creditsFocus,
                    downFocusRequester = refreshCatalogFocusRequester,
                    onRememberContentFocus = onRememberContentFocus,
                    navRailFocusRequester = navRailFocusRequester,
                    testTagName = "settings_manual_movie_complete_switch"
                )
            }
        }

        SettingsGroupHeader(title = "Catalogo")

        SettingsSectionCard(
            title = "Catalogo Live TMDB",
            subtitle = "Forza l'aggiornamento immediato di film, serie TV e Top 10",
            icon = Icons.Default.Refresh
        ) {
            Button(
                onClick = {
                    catalogRefreshed = true
                    onRefreshCatalog()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NetflixElevatedSurface,
                    contentColor = NetflixWhite
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .safeFocusRequester(refreshCatalogFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp -> manualCompleteFocus.safeRequestFocus()
                                Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                Key.DirectionRight, Key.DirectionDown -> true
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusProperties {
                        manualCompleteFocus.takeIfAttached()?.let { up = it }
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                    }
                    .tvFocusableControl(
                        shape = RoundedCornerShape(10.dp),
                        focusedScale = 1.0f,
                        borderWidth = 2.dp,
                        focusTag = "settings_refresh_catalog_button",
                        onFocusChange = { focused ->
                            if (focused) onRememberContentFocus(refreshCatalogFocusRequester)
                        }
                    )
                    .testTag("settings_refresh_catalog_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (catalogRefreshed) "Aggiornamento Catalogo Avviato" else "Ricarica Catalogo TMDB Ora",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UpdatesSettingsPanel(
    state: GitHubUpdateUiState,
    autoCheckUpdates: Boolean,
    onSetAutoCheckUpdates: (Boolean) -> Unit,
    onRepoSlugChange: (String) -> Unit,
    onCheckUpdates: (String) -> Unit,
    onTriggerUpdate: () -> Unit,
    firstControlFocusRequester: FocusRequester?,
    upToCategoryFocusRequester: FocusRequester?,
    onUpToCategoryBar: () -> Boolean,
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    navRailFocusRequester: FocusRequester?
) {
    var inputRepo by remember(state.repoSlug) { mutableStateOf(state.repoSlug) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val autoUpdateSwitchFocus = firstControlFocusRequester ?: remember { FocusRequester() }
    val repoInputFocusRequester = remember { FocusRequester() }
    val checkUpdatesFocusRequester = remember { FocusRequester() }
    val downloadUpdateFocusRequester = remember { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsGroupHeader(title = "Controllo & Sorgente Aggiornamenti")

        SettingsSectionCard(
            title = "Controllo Automatico",
            subtitle = "Verifica nuove release APK all'avvio dell'app",
            icon = Icons.Default.SystemUpdate,
            badgeText = if (autoCheckUpdates) "ATTIVO" else "MANUALE",
            badgeColor = if (autoCheckUpdates) NetflixMatchGreen else NetflixMutedGray
        ) {
            SettingsSwitchRow(
                label = "Controlla aggiornamenti all'apertura",
                description = "Mostra una notifica quando è disponibile una nuova versione",
                checked = autoCheckUpdates,
                onCheckedChange = onSetAutoCheckUpdates,
                focusRequester = autoUpdateSwitchFocus,
                upFocusRequester = upToCategoryFocusRequester,
                downFocusRequester = repoInputFocusRequester,
                onUpKey = onUpToCategoryBar,
                onRememberContentFocus = onRememberContentFocus,
                navRailFocusRequester = navRailFocusRequester,
                testTagName = "settings_auto_update_switch"
            )
        }

        SettingsSectionCard(
            title = "Aggiornamenti da GitHub Releases",
            subtitle = "Versione installata: v${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
            icon = Icons.Default.CloudDownload,
            badgeText = "CI READY",
            badgeColor = NetflixMatchGreen
        ) {
            OutlinedTextField(
                value = inputRepo,
                onValueChange = {
                    inputRepo = it
                    onRepoSlugChange(it)
                },
                label = { Text("Repository GitHub (owner/repo)") },
                placeholder = { Text("es. hgsolutions/vixflix-android") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        checkUpdatesFocusRequester.safeRequestFocus()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NetflixWhite,
                    unfocusedBorderColor = NetflixElevatedSurface,
                    focusedTextColor = NetflixWhite,
                    unfocusedTextColor = NetflixWhite,
                    focusedLabelColor = NetflixWhite,
                    unfocusedLabelColor = NetflixLightGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .safeFocusRequester(repoInputFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onRememberContentFocus(repoInputFocusRequester)
                        }
                        DpadFocusObserverRegistry.onNodeFocusChanged(
                            scope = DpadFocusScope.MAIN_NAVIGATION,
                            tag = "github_repo_input",
                            isFocused = focusState.isFocused
                        )
                    }
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                        autoUpdateSwitchFocus.takeIfAttached()?.let { up = it }
                        checkUpdatesFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    keyboardController?.hide()
                                    autoUpdateSwitchFocus.safeRequestFocus()
                                }
                                Key.DirectionDown -> {
                                    keyboardController?.hide()
                                    checkUpdatesFocusRequester.safeRequestFocus()
                                }
                                Key.DirectionLeft -> {
                                    keyboardController?.hide()
                                    navRailFocusRequester.safeRequestFocus()
                                }
                                Key.DirectionRight -> true
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .testTag("github_repo_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onCheckUpdates(inputRepo) },
                enabled = !state.isChecking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NetflixRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .safeFocusRequester(checkUpdatesFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp -> repoInputFocusRequester.safeRequestFocus()
                                Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                Key.DirectionRight -> true
                                Key.DirectionDown -> {
                                    if (state.releaseInfo != null && !state.isDownloadingApk) {
                                        downloadUpdateFocusRequester.safeRequestFocus()
                                    } else {
                                        true
                                    }
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                        repoInputFocusRequester.takeIfAttached()?.let { up = it }
                        if (state.releaseInfo != null && !state.isDownloadingApk) {
                            downloadUpdateFocusRequester.takeIfAttached()?.let { down = it }
                        }
                    }
                    .tvFocusableControl(
                        shape = RoundedCornerShape(10.dp),
                        focusedScale = 1.0f,
                        borderWidth = 2.dp,
                        focusTag = "check_github_updates_button",
                        onFocusChange = { focused ->
                            if (focused) onRememberContentFocus(checkUpdatesFocusRequester)
                        }
                    )
                    .testTag("check_github_updates_button")
            ) {
                if (state.isChecking) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Controllo GitHub Releases in corso…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verifica Nuova Versione su GitHub",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = NetflixRed.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixWhite,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        state.releaseInfo?.let { release ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixDarkSurface),
                border = BorderStroke(
                    1.5.dp,
                    if (release.isUpdateAvailable) NetflixMatchGreen else NetflixElevatedSurface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (release.isUpdateAvailable) {
                                Icons.Default.NewReleases
                            } else {
                                Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = if (release.isUpdateAvailable) NetflixMatchGreen else NetflixLightGray,
                            modifier = Modifier.size(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (release.isUpdateAvailable) {
                                    "Nuova Versione Disponibile: ${release.tagName}"
                                } else {
                                    "App aggiornata all'ultima release (${release.tagName})"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (release.isUpdateAvailable) NetflixMatchGreen else NetflixWhite
                            )
                            Text(
                                text = "${release.releaseTitle} • ${release.publishedAt.ifBlank { "Oggi" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixLightGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = release.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixWhite
                    )

                    if (release.apkFileName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val sizeMb = String.format("%.1f MB", release.apkSizeBytes / (1024.0 * 1024.0))
                        Text(
                            text = "APK: ${release.apkFileName} ($sizeMb)",
                            style = MaterialTheme.typography.labelSmall,
                            color = NetflixMatchGreen
                        )
                    }

                    if (state.isDownloadingApk) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val pct = (state.downloadProgress * 100).toInt()
                        Text(
                            text = "Download in background: $pct%…",
                            style = MaterialTheme.typography.labelLarge,
                            color = NetflixMatchGreen
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { state.downloadProgress.coerceIn(0f, 1f) },
                            color = NetflixMatchGreen,
                            trackColor = NetflixElevatedSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    } else if (state.autoInstallTriggered) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Download completato! Installazione avviata.",
                            style = MaterialTheme.typography.labelLarge,
                            color = NetflixMatchGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onTriggerUpdate,
                        enabled = !state.isDownloadingApk,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (release.isUpdateAvailable) NetflixMatchGreen else NetflixElevatedSurface,
                            contentColor = if (release.isUpdateAvailable) NetflixBlack else NetflixWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(downloadUpdateFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> checkUpdatesFocusRequester.safeRequestFocus()
                                        Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                        Key.DirectionRight, Key.DirectionDown -> true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                                checkUpdatesFocusRequester.takeIfAttached()?.let { up = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.0f,
                                borderWidth = 2.dp,
                                focusTag = "download_update_apk_button",
                                onFocusChange = { focused ->
                                    if (focused) onRememberContentFocus(downloadUpdateFocusRequester)
                                }
                            )
                            .testTag("download_update_apk_button")
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                state.isDownloadingApk -> "Scaricamento (${(state.downloadProgress * 100).toInt()}%)…"
                                state.downloadedApkPath != null -> "Installa Aggiornamento (${release.tagName})"
                                release.isUpdateAvailable -> "Scarica e Aggiorna Ora (${release.tagName})"
                                else -> "Riscarica APK Release (${release.tagName})"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiSourcesSettingsPanel(
    state: GitHubUpdateUiState,
    onUpdateTmdbKey: (String) -> Unit,
    onResetTmdbKey: () -> Unit,
    firstControlFocusRequester: FocusRequester?,
    upToCategoryFocusRequester: FocusRequester?,
    onUpToCategoryBar: () -> Boolean,
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    navRailFocusRequester: FocusRequester?
) {
    var inputTmdbKey by remember(state.tmdbApiKey) { mutableStateOf(state.tmdbApiKey) }
    var copiedFeedback by remember { mutableStateOf(false) }
    var showTechnicalDetails by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val apiKeyInputFocusRequester = firstControlFocusRequester ?: remember { FocusRequester() }
    val saveKeyFocusRequester = remember { FocusRequester() }
    val copyKeyFocusRequester = remember { FocusRequester() }
    val resetKeyFocusRequester = remember { FocusRequester() }
    val techDetailsFocusRequester = remember { FocusRequester() }
    val showResetButton = inputTmdbKey != TmdbApiConfig.INTEGRATED_TMDB_API_KEY

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsGroupHeader(title = "Configurazione Sorgente Dati")

        SettingsSectionCard(
            title = "Chiave API TMDB",
            subtitle = "Usata per catalogo film, serie TV, locandine e ricerca",
            icon = Icons.Default.Key,
            badgeText = "ATTIVA",
            badgeColor = NetflixMatchGreen
        ) {
            Surface(
                color = NetflixBlack,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "CHIAVE IN USO:",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.tmdbApiKey,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NetflixMatchGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("visible_tmdb_api_key_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputTmdbKey,
                onValueChange = {
                    inputTmdbKey = it
                    copiedFeedback = false
                },
                label = { Text("Chiave API TMDB personalizzata") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        saveKeyFocusRequester.safeRequestFocus()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NetflixWhite,
                    unfocusedBorderColor = NetflixElevatedSurface,
                    focusedTextColor = NetflixWhite,
                    unfocusedTextColor = NetflixWhite,
                    focusedLabelColor = NetflixWhite,
                    unfocusedLabelColor = NetflixLightGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .safeFocusRequester(apiKeyInputFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onRememberContentFocus(apiKeyInputFocusRequester)
                        }
                        DpadFocusObserverRegistry.onNodeFocusChanged(
                            scope = DpadFocusScope.MAIN_NAVIGATION,
                            tag = "tmdb_api_key_input",
                            isFocused = focusState.isFocused
                        )
                    }
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                        upToCategoryFocusRequester.takeIfAttached()?.let { up = it }
                        saveKeyFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionDown -> {
                                    keyboardController?.hide()
                                    saveKeyFocusRequester.safeRequestFocus()
                                }
                                Key.DirectionUp -> {
                                    keyboardController?.hide()
                                    onUpToCategoryBar()
                                }
                                Key.DirectionLeft -> {
                                    keyboardController?.hide()
                                    navRailFocusRequester.safeRequestFocus()
                                }
                                Key.DirectionRight -> true
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .testTag("tmdb_api_key_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                Button(
                    onClick = { onUpdateTmdbKey(inputTmdbKey) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(saveKeyFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> apiKeyInputFocusRequester.safeRequestFocus()
                                    Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                    Key.DirectionRight -> copyKeyFocusRequester.safeRequestFocus()
                                    Key.DirectionDown -> techDetailsFocusRequester.safeRequestFocus()
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                            apiKeyInputFocusRequester.takeIfAttached()?.let { up = it }
                            copyKeyFocusRequester.takeIfAttached()?.let { right = it }
                            techDetailsFocusRequester.takeIfAttached()?.let { down = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.0f,
                            borderWidth = 2.dp,
                            focusTag = "save_tmdb_key_button",
                            onFocusChange = { focused ->
                                if (focused) onRememberContentFocus(saveKeyFocusRequester)
                            }
                        )
                        .testTag("save_tmdb_key_button")
                ) {
                    Text(
                        text = "Applica Chiave",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(state.tmdbApiKey))
                        copiedFeedback = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(copyKeyFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> apiKeyInputFocusRequester.safeRequestFocus()
                                    Key.DirectionLeft -> saveKeyFocusRequester.safeRequestFocus()
                                    Key.DirectionRight -> {
                                        if (showResetButton) resetKeyFocusRequester.safeRequestFocus() else true
                                    }
                                    Key.DirectionDown -> techDetailsFocusRequester.safeRequestFocus()
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            saveKeyFocusRequester.takeIfAttached()?.let { left = it }
                            apiKeyInputFocusRequester.takeIfAttached()?.let { up = it }
                            if (showResetButton) {
                                resetKeyFocusRequester.takeIfAttached()?.let { right = it }
                            }
                            techDetailsFocusRequester.takeIfAttached()?.let { down = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.0f,
                            borderWidth = 2.dp,
                            focusTag = "copy_tmdb_key_button",
                            onFocusChange = { focused ->
                                if (focused) onRememberContentFocus(copyKeyFocusRequester)
                            }
                        )
                        .testTag("copy_tmdb_key_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copia chiave API",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (copiedFeedback) "Copiata!" else "Copia",
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (showResetButton) {
                    OutlinedButton(
                        onClick = {
                            inputTmdbKey = TmdbApiConfig.INTEGRATED_TMDB_API_KEY
                            onResetTmdbKey()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NetflixElevatedSurface),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixLightGray),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(resetKeyFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> apiKeyInputFocusRequester.safeRequestFocus()
                                        Key.DirectionLeft -> copyKeyFocusRequester.safeRequestFocus()
                                        Key.DirectionRight -> true
                                        Key.DirectionDown -> techDetailsFocusRequester.safeRequestFocus()
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                copyKeyFocusRequester.takeIfAttached()?.let { left = it }
                                apiKeyInputFocusRequester.takeIfAttached()?.let { up = it }
                                techDetailsFocusRequester.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.0f,
                                borderWidth = 2.dp,
                                focusTag = "reset_tmdb_key_button",
                                onFocusChange = { focused ->
                                    if (focused) onRememberContentFocus(resetKeyFocusRequester)
                                }
                            )
                            .testTag("reset_tmdb_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Ripristina chiave predefinita",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showTechnicalDetails = !showTechnicalDetails },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixLightGray),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .safeFocusRequester(techDetailsFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp -> saveKeyFocusRequester.safeRequestFocus()
                                Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                Key.DirectionRight, Key.DirectionDown -> true
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                        saveKeyFocusRequester.takeIfAttached()?.let { up = it }
                    }
                    .tvFocusableControl(
                        shape = RoundedCornerShape(10.dp),
                        focusedScale = 1.0f,
                        borderWidth = 2.dp,
                        focusTag = "toggle_technical_details_button",
                        onFocusChange = { focused ->
                            if (focused) onRememberContentFocus(techDetailsFocusRequester)
                        }
                    )
                    .testTag("toggle_technical_details_button")
            ) {
                Icon(
                    imageVector = if (showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showTechnicalDetails) "Nascondi Dettagli Configurazione" else "Mostra Dettagli Configurazione",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(
                visible = showTechnicalDetails,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "JSON: ${state.bundledConfigFilePath}\nKotlin: ${state.kotlinConfigFilePath}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = NetflixLightGray
                    )
                    if (state.bundledConfigJson.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = NetflixBlack,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.bundledConfigJson,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = NetflixWhite,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemSettingsPanel(
    isTvLayout: Boolean,
    isWideStatRow: Boolean,
    continueWatchingCount: Int,
    watchlistCount: Int,
    backgroundPrefetchEnabled: Boolean,
    cacheStats: CacheStatsSummary,
    isBackgroundCatalogRefreshing: Boolean,
    cacheClearedFeedbackNonce: Int,
    profileUiState: UserProfileUiState,
    onOpenProfileSelector: (Boolean) -> Unit,
    onSetAutoLoginProfile: (Boolean) -> Unit,
    onSetDefaultProfileId: (String?) -> Unit,
    simklUiState: SimklProfileUiState,
    onConnectSimkl: () -> Unit,
    onSyncSimklNow: () -> Unit,
    onUnlinkSimkl: () -> Unit,
    onSetBackgroundPrefetch: (Boolean) -> Unit,
    onRefreshCatalog: () -> Unit,
    onClearLocalCache: () -> Unit,
    onClearContinueWatching: () -> Unit,
    firstControlFocusRequester: FocusRequester?,
    upToCategoryFocusRequester: FocusRequester?,
    onUpToCategoryBar: () -> Boolean,
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    navRailFocusRequester: FocusRequester?
) {
    var clearedFeedback by remember { mutableStateOf(false) }
    var showUnlinkSimklConfirm by remember(simklUiState.profileId) { mutableStateOf(false) }
    var wasUnlinkSimklDialogOpen by remember { mutableStateOf(false) }
    var showClearContinueWatchingConfirm by remember(profileUiState.activeProfile?.id) { mutableStateOf(false) }
    var wasClearCwDialogOpen by remember { mutableStateOf(false) }

    val switchProfileFocusRequester = firstControlFocusRequester ?: remember { FocusRequester() }
    val manageProfilesFocusRequester = remember { FocusRequester() }
    val autoLoginFocusRequester = remember { FocusRequester() }
    val defaultLastUsedFocusRequester = remember { FocusRequester() }
    val profileChipFocusRequesters = remember(profileUiState.profiles.map { it.id }) {
        profileUiState.profiles.associate { it.id to FocusRequester() }
    }
    val simklConnectFocusRequester = remember { FocusRequester() }
    val simklSyncFocusRequester = remember { FocusRequester() }
    val simklUnlinkFocusRequester = remember { FocusRequester() }
    val prefetchFocusRequester = remember { FocusRequester() }
    val refreshCacheFocusRequester = remember { FocusRequester() }
    val clearCacheFocusRequester = remember { FocusRequester() }
    val clearContinueWatchingFocusRequester = remember { FocusRequester() }

    val hasDefaultProfileChips = profileUiState.profiles.isNotEmpty()
    val showSimklConnectedActions = simklUiState.isConnected ||
        (simklUiState.connectionState == SimklConnectionState.ERROR && !simklUiState.accountDisplayName.isNullOrBlank())

    fun resolveSimklPrimaryFocus(): FocusRequester = when {
        !showSimklConnectedActions -> simklConnectFocusRequester
        simklUiState.connectionState == SimklConnectionState.SYNCING -> simklUnlinkFocusRequester
        else -> simklSyncFocusRequester
    }

    val simklUpFocusRequester = if (hasDefaultProfileChips) {
        defaultLastUsedFocusRequester
    } else {
        autoLoginFocusRequester
    }

    // Ripristino focus deterministico dopo chiusura dialogo scollegamento Simkl
    LaunchedEffect(showUnlinkSimklConfirm, showSimklConnectedActions) {
        if (wasUnlinkSimklDialogOpen && !showUnlinkSimklConfirm) {
            repeat(3) { withFrameNanos { } }
            val target = if (showSimklConnectedActions) {
                simklUnlinkFocusRequester
            } else {
                simklConnectFocusRequester
            }
            target.safeRequestFocus()
        }
        wasUnlinkSimklDialogOpen = showUnlinkSimklConfirm
    }

    // Ripristino focus deterministico dopo chiusura dialogo svuotamento Continua a guardare
    LaunchedEffect(showClearContinueWatchingConfirm, continueWatchingCount) {
        if (wasClearCwDialogOpen && !showClearContinueWatchingConfirm) {
            repeat(3) { withFrameNanos { } }
            if (continueWatchingCount > 0) {
                clearContinueWatchingFocusRequester.safeRequestFocus()
            } else {
                clearCacheFocusRequester.safeRequestFocus()
            }
        }
        wasClearCwDialogOpen = showClearContinueWatchingConfirm
    }

    if (showUnlinkSimklConfirm) {
        SimklUnlinkConfirmDialog(
            profileName = profileUiState.activeProfile?.name ?: "Profilo attivo",
            accountDisplayName = simklUiState.accountDisplayName,
            onDismiss = { showUnlinkSimklConfirm = false },
            onConfirmUnlink = {
                showUnlinkSimklConfirm = false
                onUnlinkSimkl()
            }
        )
    }

    if (showClearContinueWatchingConfirm) {
        ClearContinueWatchingConfirmDialog(
            onDismiss = { showClearContinueWatchingConfirm = false },
            onConfirmClear = {
                showClearContinueWatchingConfirm = false
                onClearContinueWatching()
                clearedFeedback = true
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsGroupHeader(title = "Account & Profili")

        val activeProfile = profileUiState.activeProfile
        SettingsSectionCard(
            title = "Profili Utente & Avvio",
            subtitle = "Preferiti, Continua a guardare, avanzamento e lingua separati per ogni profilo",
            icon = Icons.Default.Settings,
            badgeText = "${profileUiState.profiles.size}/${profileUiState.maxProfiles} PROFILI",
            badgeColor = Color(activeProfile?.accentColorHex ?: 0xFFE50914L)
        ) {
            if (activeProfile != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NetflixDarkSurface)
                        .border(
                            1.dp,
                            Color(activeProfile.accentColorHex).copy(alpha = 0.45f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    ProfileAvatarArtwork(
                        avatarId = activeProfile.avatarId,
                        accentColorHex = activeProfile.accentColorHex,
                        size = 46.dp,
                        cornerRadius = 10.dp,
                        profileName = activeProfile.name
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Profilo attivo: ${activeProfile.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Lingua ${activeProfile.preferredLanguage.uppercase()} • Audio ${activeProfile.preferredAudioLanguage.uppercase()} • Sottotitoli ${if (activeProfile.subtitlesEnabled) activeProfile.preferredSubtitleLanguage.uppercase() else "OFF"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                Button(
                    onClick = { onOpenProfileSelector(false) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(switchProfileFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> onUpToCategoryBar()
                                    Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                    Key.DirectionRight -> manageProfilesFocusRequester.safeRequestFocus()
                                    Key.DirectionDown -> autoLoginFocusRequester.safeRequestFocus()
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            upToCategoryFocusRequester.takeIfAttached()?.let { up = it }
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                            manageProfilesFocusRequester.takeIfAttached()?.let { right = it }
                            autoLoginFocusRequester.takeIfAttached()?.let { down = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.0f,
                            borderWidth = 2.dp,
                            focusTag = "settings_switch_profile_button",
                            onFocusChange = { focused ->
                                if (focused) onRememberContentFocus(switchProfileFocusRequester)
                            }
                        )
                        .testTag("settings_switch_profile_button")
                ) {
                    Text(
                        text = "Cambia profilo",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { onOpenProfileSelector(true) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(manageProfilesFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> onUpToCategoryBar()
                                    Key.DirectionLeft -> switchProfileFocusRequester.safeRequestFocus()
                                    Key.DirectionRight -> true
                                    Key.DirectionDown -> autoLoginFocusRequester.safeRequestFocus()
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            upToCategoryFocusRequester.takeIfAttached()?.let { up = it }
                            switchProfileFocusRequester.takeIfAttached()?.let { left = it }
                            autoLoginFocusRequester.takeIfAttached()?.let { down = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.0f,
                            borderWidth = 2.dp,
                            focusTag = "settings_manage_profiles_button",
                            onFocusChange = { focused ->
                                if (focused) onRememberContentFocus(manageProfilesFocusRequester)
                            }
                        )
                        .testTag("settings_manage_profiles_button")
                ) {
                    Text(
                        text = "Gestisci profili",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitchRow(
                label = "Accesso automatico all'avvio",
                description = if (profileUiState.autoLoginEnabled) {
                    "Avvia direttamente il profilo predefinito o l'ultimo usato senza mostrare la schermata profili"
                } else {
                    "Mostra sempre \"Chi sta guardando?\" all'avvio dell'app"
                },
                checked = profileUiState.autoLoginEnabled,
                onCheckedChange = onSetAutoLoginProfile,
                focusRequester = autoLoginFocusRequester,
                upFocusRequester = switchProfileFocusRequester,
                downFocusRequester = if (hasDefaultProfileChips) {
                    defaultLastUsedFocusRequester
                } else {
                    resolveSimklPrimaryFocus()
                },
                onUpKey = { switchProfileFocusRequester.safeRequestFocus() },
                onDownKey = {
                    if (hasDefaultProfileChips) {
                        defaultLastUsedFocusRequester.safeRequestFocus()
                    } else {
                        resolveSimklPrimaryFocus().safeRequestFocus()
                    }
                },
                onRememberContentFocus = onRememberContentFocus,
                navRailFocusRequester = navRailFocusRequester,
                testTagName = "settings_autologin_profile_toggle"
            )

            if (hasDefaultProfileChips) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Profilo predefinito all'avvio",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                val orderedChipRequesters = listOf(defaultLastUsedFocusRequester) +
                    profileUiState.profiles.map { profileChipFocusRequesters.getValue(it.id) }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp)
                ) {
                    val isLastUsedSelected = profileUiState.defaultProfileId == null
                    val nextAfterLastUsed = orderedChipRequesters.getOrNull(1)
                    Surface(
                        color = if (isLastUsedSelected) NetflixRed.copy(alpha = 0.24f) else NetflixDarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isLastUsedSelected) NetflixRed else NetflixElevatedSurface
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(defaultLastUsedFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> autoLoginFocusRequester.safeRequestFocus()
                                        Key.DirectionDown -> resolveSimklPrimaryFocus().safeRequestFocus()
                                        Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                        Key.DirectionRight -> nextAfterLastUsed?.safeRequestFocus() ?: true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                autoLoginFocusRequester.takeIfAttached()?.let { up = it }
                                resolveSimklPrimaryFocus().takeIfAttached()?.let { down = it }
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                                nextAfterLastUsed?.takeIfAttached()?.let { right = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = NetflixWhite,
                                focusedScale = 1.0f,
                                borderWidth = 2.dp,
                                focusTag = "settings_default_profile_last_used",
                                onFocusChange = { focused ->
                                    if (focused) onRememberContentFocus(defaultLastUsedFocusRequester)
                                },
                                onClick = { onSetDefaultProfileId(null) }
                            )
                            .testTag("settings_default_profile_last_used")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Ultimo usato",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isLastUsedSelected) FontWeight.Bold else FontWeight.Medium,
                                color = NetflixWhite,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    profileUiState.profiles.forEachIndexed { idx, profile ->
                        val selected = profileUiState.defaultProfileId == profile.id
                        val accent = Color(profile.accentColorHex)
                        val chipIndex = idx + 1
                        val chipRequester = orderedChipRequesters[chipIndex]
                        val prevChipRequester = orderedChipRequesters[chipIndex - 1]
                        val nextChipRequester = orderedChipRequesters.getOrNull(chipIndex + 1)

                        Surface(
                            color = if (selected) accent.copy(alpha = 0.24f) else NetflixDarkSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) accent else NetflixElevatedSurface
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .safeFocusRequester(chipRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionUp -> autoLoginFocusRequester.safeRequestFocus()
                                            Key.DirectionDown -> resolveSimklPrimaryFocus().safeRequestFocus()
                                            Key.DirectionLeft -> prevChipRequester.safeRequestFocus()
                                            Key.DirectionRight -> nextChipRequester?.safeRequestFocus() ?: true
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .focusProperties {
                                    autoLoginFocusRequester.takeIfAttached()?.let { up = it }
                                    resolveSimklPrimaryFocus().takeIfAttached()?.let { down = it }
                                    prevChipRequester.takeIfAttached()?.let { left = it }
                                    nextChipRequester?.takeIfAttached()?.let { right = it }
                                }
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = NetflixWhite,
                                    focusedScale = 1.0f,
                                    borderWidth = 2.dp,
                                    focusTag = "settings_default_profile_${profile.id}",
                                    onFocusChange = { focused ->
                                        if (focused) onRememberContentFocus(chipRequester)
                                    },
                                    onClick = { onSetDefaultProfileId(profile.id) }
                                )
                                .testTag("settings_default_profile_${profile.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                ProfileAvatarArtwork(
                                    avatarId = profile.avatarId,
                                    accentColorHex = profile.accentColorHex,
                                    size = 24.dp,
                                    cornerRadius = 6.dp
                                )
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = NetflixWhite,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        SimklSettingsSectionCard(
            activeProfileName = activeProfile?.name ?: "Profilo Principale",
            simklUiState = simklUiState,
            connectFocusRequester = simklConnectFocusRequester,
            syncFocusRequester = simklSyncFocusRequester,
            unlinkFocusRequester = simklUnlinkFocusRequester,
            upFocusRequester = simklUpFocusRequester,
            downFocusRequester = prefetchFocusRequester,
            navRailFocusRequester = navRailFocusRequester,
            onRememberContentFocus = onRememberContentFocus,
            onConnect = onConnectSimkl,
            onSyncNow = onSyncSimklNow,
            onRequestUnlink = { showUnlinkSimklConfirm = true }
        )

        SettingsGroupHeader(title = "Prestazioni & Cache")

        SettingsSectionCard(
            title = "Cache & Prestazioni",
            subtitle = "Avvio immediato da Room, link stream validi e precaricamento locandine Coil",
            icon = Icons.Default.Memory,
            badgeText = when {
                isBackgroundCatalogRefreshing -> "AGGIORNAMENTO…"
                cacheStats.catalogUpdatedAtMs != null -> "CACHE ATTIVA"
                else -> "VUOTA"
            },
            badgeColor = if (cacheStats.catalogUpdatedAtMs != null) NetflixMatchGreen else NetflixLightGray
        ) {
            if (isWideStatRow) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingsStatTile(
                        label = "Locandine Coil",
                        value = formatCacheSizeMb(cacheStats.imageCacheSizeBytes),
                        modifier = Modifier.weight(1f)
                    )
                    SettingsStatTile(
                        label = "Link Video Validi",
                        value = cacheStats.validStreamLinksCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SettingsStatTile(
                        label = "Schede Dettaglio",
                        value = cacheStats.cachedDetailsCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SettingsStatTile(
                        label = "Stagioni Episodi",
                        value = cacheStats.cachedSeasonsCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingsStatTile(
                        label = "Locandine Coil",
                        value = formatCacheSizeMb(cacheStats.imageCacheSizeBytes),
                        modifier = Modifier.weight(1f)
                    )
                    SettingsStatTile(
                        label = "Link Video Validi",
                        value = cacheStats.validStreamLinksCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingsStatTile(
                        label = "Schede Dettaglio",
                        value = cacheStats.cachedDetailsCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SettingsStatTile(
                        label = "Stagioni Episodi",
                        value = cacheStats.cachedSeasonsCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsInfoLine(
                label = "Snapshot Catalogo Home",
                value = formatRelativeCacheAge(cacheStats.catalogUpdatedAtMs)
            )

            Spacer(modifier = Modifier.height(10.dp))

            val firstCacheButtonFocus = if (!isBackgroundCatalogRefreshing) {
                refreshCacheFocusRequester
            } else {
                clearCacheFocusRequester
            }

            SettingsSwitchRow(
                label = "Precaricamento leggero locandine",
                description = "Precarica in background Hero e locandine principali senza rallentare UI o streaming",
                checked = backgroundPrefetchEnabled,
                onCheckedChange = onSetBackgroundPrefetch,
                focusRequester = prefetchFocusRequester,
                upFocusRequester = resolveSimklPrimaryFocus(),
                downFocusRequester = firstCacheButtonFocus,
                onUpKey = { resolveSimklPrimaryFocus().safeRequestFocus() },
                onDownKey = { firstCacheButtonFocus.safeRequestFocus() },
                onRememberContentFocus = onRememberContentFocus,
                navRailFocusRequester = navRailFocusRequester,
                testTagName = "settings_prefetch_switch"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                OutlinedButton(
                    onClick = onRefreshCatalog,
                    enabled = !isBackgroundCatalogRefreshing,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(refreshCacheFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> prefetchFocusRequester.safeRequestFocus()
                                    Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                    Key.DirectionRight -> clearCacheFocusRequester.safeRequestFocus()
                                    Key.DirectionDown -> {
                                        if (continueWatchingCount > 0) {
                                            clearContinueWatchingFocusRequester.safeRequestFocus()
                                        } else {
                                            true
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            prefetchFocusRequester.takeIfAttached()?.let { up = it }
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                            clearCacheFocusRequester.takeIfAttached()?.let { right = it }
                            if (continueWatchingCount > 0) {
                                clearContinueWatchingFocusRequester.takeIfAttached()?.let { down = it }
                            }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.0f,
                            borderWidth = 2.dp,
                            focusTag = "settings_refresh_catalog_cache_button",
                            onFocusChange = { focused ->
                                if (focused) onRememberContentFocus(refreshCacheFocusRequester)
                            }
                        )
                        .testTag("settings_refresh_catalog_cache_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBackgroundCatalogRefreshing) "Aggiornamento…" else "Aggiorna Ora",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onClearLocalCache,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.85f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(clearCacheFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> prefetchFocusRequester.safeRequestFocus()
                                    Key.DirectionLeft -> {
                                        if (!isBackgroundCatalogRefreshing) {
                                            refreshCacheFocusRequester.safeRequestFocus()
                                        } else {
                                            navRailFocusRequester.safeRequestFocus()
                                        }
                                    }
                                    Key.DirectionRight -> true
                                    Key.DirectionDown -> {
                                        if (continueWatchingCount > 0) {
                                            clearContinueWatchingFocusRequester.safeRequestFocus()
                                        } else {
                                            true
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            prefetchFocusRequester.takeIfAttached()?.let { up = it }
                            if (!isBackgroundCatalogRefreshing) {
                                refreshCacheFocusRequester.takeIfAttached()?.let { left = it }
                            } else {
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                            }
                            if (continueWatchingCount > 0) {
                                clearContinueWatchingFocusRequester.takeIfAttached()?.let { down = it }
                            }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.0f,
                            borderWidth = 2.dp,
                            focusTag = "settings_clear_cache_button",
                            onFocusChange = { focused ->
                                if (focused) onRememberContentFocus(clearCacheFocusRequester)
                            }
                        )
                        .testTag("settings_clear_cache_button")
                ) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (cacheClearedFeedbackNonce > 0 && cacheStats.catalogUpdatedAtMs == null) {
                            "Cache Svuotata!"
                        } else {
                            "Svuota Cache"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        SettingsGroupHeader(title = "Gestione Dati & Reset", isDangerZone = true)

        SettingsSectionCard(
            title = "Continua a Guardare & Dati Profilo",
            subtitle = "Dati locali del profilo attivo (non rimossi dallo svuotamento cache)",
            icon = Icons.Default.Storage,
            isDangerZone = true
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsStatTile(
                    label = "Continua a guardare",
                    value = continueWatchingCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                SettingsStatTile(
                    label = "La Mia Lista",
                    value = watchlistCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    if (continueWatchingCount > 0) {
                        showClearContinueWatchingConfirm = true
                    }
                },
                enabled = continueWatchingCount > 0,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (continueWatchingCount > 0) NetflixRed else NetflixElevatedSurface),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (continueWatchingCount > 0) NetflixWhite else NetflixMutedGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .safeFocusRequester(clearContinueWatchingFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp ->
                                    if (!isBackgroundCatalogRefreshing) refreshCacheFocusRequester.safeRequestFocus() else clearCacheFocusRequester.safeRequestFocus()
                                Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                Key.DirectionRight, Key.DirectionDown -> true
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                        (if (!isBackgroundCatalogRefreshing) refreshCacheFocusRequester else clearCacheFocusRequester)
                            .takeIfAttached()?.let { up = it }
                    }
                    .tvFocusableControl(
                        shape = RoundedCornerShape(10.dp),
                        focusedScale = 1.0f,
                        borderWidth = 2.dp,
                        focusTag = "settings_clear_history_button",
                        onFocusChange = { focused ->
                            if (focused) onRememberContentFocus(clearContinueWatchingFocusRequester)
                        }
                    )
                    .testTag("settings_clear_history_button")
            ) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        clearedFeedback && continueWatchingCount == 0 -> "Continua a guardare svuotato"
                        continueWatchingCount == 0 -> "Nessun contenuto in corso"
                        else -> "Svuota Continua a Guardare ($continueWatchingCount)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        SettingsGroupHeader(title = "Informazioni Sistema")

        SettingsSectionCard(
            title = "Informazioni Applicazione",
            subtitle = "Dettagli build e compatibilità dispositivo",
            icon = Icons.Default.Info
        ) {
            SettingsInfoLine(label = "Versione App", value = "v${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})")
            SettingsInfoLine(label = "Application ID", value = BuildConfig.APPLICATION_ID)
            SettingsInfoLine(
                label = "Interfaccia Attiva",
                value = if (isTvLayout) "Android TV / Google TV / Fire TV Stick (D-Pad + Touch)" else "Smartphone Verticale (Touch)"
            )
            SettingsInfoLine(label = "Firma Keystore", value = "SHA1 96:99:D9:34…BB:7C")
        }
    }
}

private fun formatCacheSizeMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb < 10.0) String.format("%.1f MB", mb) else "${mb.toInt()} MB"
}

private fun formatRelativeCacheAge(updatedAtMs: Long?): String {
    if (updatedAtMs == null || updatedAtMs <= 0L) return "Non ancora salvato"
    val diffSec = ((System.currentTimeMillis() - updatedAtMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Aggiornato ora"
        diffSec < 3600L -> "${diffSec / 60L} min fa"
        diffSec < 86_400L -> "${diffSec / 3600L} ore fa"
        else -> "${diffSec / 86_400L} giorni fa"
    }
}

@Composable
private fun SimklSettingsSectionCard(
    activeProfileName: String,
    simklUiState: SimklProfileUiState,
    connectFocusRequester: FocusRequester,
    syncFocusRequester: FocusRequester,
    unlinkFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    navRailFocusRequester: FocusRequester?,
    onRememberContentFocus: (FocusRequester) -> Unit,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onRequestUnlink: () -> Unit
) {
    val (badgeLabel, badgeColor) = when (simklUiState.connectionState) {
        SimklConnectionState.NOT_CONNECTED -> "NON COLLEGATO" to NetflixLightGray
        SimklConnectionState.AUTHORIZATION_WAITING -> "ATTESA QR" to Color(0xFFFFB74D)
        SimklConnectionState.AUTHORIZING_POLLING -> "VERIFICA QR…" to Color(0xFFFFB74D)
        SimklConnectionState.CONNECTED_SYNCED -> "COLLEGATO" to NetflixMatchGreen
        SimklConnectionState.SYNCING -> "SINCRONIZZAZIONE…" to Color(0xFF4FC3F7)
        SimklConnectionState.OFFLINE_QUEUE_PENDING -> "CODA OFFLINE (${simklUiState.pendingQueueCount})" to Color(0xFFFFB74D)
        SimklConnectionState.AUTH_EXPIRED -> "SESSIONE SCADUTA" to NetflixRed
        SimklConnectionState.ERROR -> "ERRORE SYNC" to NetflixRed
    }

    val statusDescription = when (simklUiState.connectionState) {
        SimklConnectionState.NOT_CONNECTED -> "Collega un account Simkl tramite QR Code per sincronizzare in cloud questo profilo."
        SimklConnectionState.AUTHORIZATION_WAITING -> "Preparazione codice QR Simkl AUTH V2…"
        SimklConnectionState.AUTHORIZING_POLLING -> "In attesa di conferma sullo smartphone…"
        SimklConnectionState.CONNECTED_SYNCED -> "Sincronizzazione cloud bidirezionale attiva e aggiornata."
        SimklConnectionState.SYNCING -> "Scambio progressi di film ed episodi con Simkl in corso…"
        SimklConnectionState.OFFLINE_QUEUE_PENDING -> "${simklUiState.pendingQueueCount} aggiornamenti locali in coda: invio automatico appena online."
        SimklConnectionState.AUTH_EXPIRED -> simklUiState.errorMessage ?: "Sessione Simkl scaduta o revocata. Ricollega l'account (i dati locali restano intatti)."
        SimklConnectionState.ERROR -> simklUiState.errorMessage ?: "Errore temporaneo di sincronizzazione. Riprova."
    }

    Box(modifier = Modifier.testTag("simkl_settings_card")) {
        SettingsSectionCard(
            title = "Sincronizzazione Simkl",
            subtitle = "Stato cloud per \"$activeProfileName\" (AUTH V2 + QR Code TV)",
            icon = Icons.Default.Sync,
            badgeText = badgeLabel,
            badgeColor = badgeColor
        ) {
            if (!simklUiState.isClientIdConfigured) {
                Surface(
                    color = Color(0xFFFFB74D).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Credenziale applicazione SIMKL_CLIENT_ID non configurata nella build: impostala nei Secrets di AI Studio per abilitare il collegamento tramite QR Code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixWhite,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Surface(
                color = NetflixDarkSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Stato connessione",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray
                        )
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("simkl_connection_badge")
                        )
                    }
                    if (!simklUiState.accountDisplayName.isNullOrBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Account Simkl",
                                style = MaterialTheme.typography.bodySmall,
                                color = NetflixLightGray
                            )
                            Text(
                                text = simklUiState.accountDisplayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetflixWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("simkl_account_label")
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Ultima sincronizzazione",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray
                        )
                        Text(
                            text = formatRelativeCacheAge(simklUiState.lastSyncAtMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = NetflixWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("simkl_last_sync_label")
                        )
                    }
                    Text(
                        text = statusDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (simklUiState.connectionState == SimklConnectionState.AUTH_EXPIRED ||
                            simklUiState.connectionState == SimklConnectionState.ERROR
                        ) {
                            NetflixRed
                        } else {
                            NetflixLightGray
                        }
                    )

                    val liveProgress = simklUiState.syncProgress
                    if (simklUiState.connectionState == SimklConnectionState.SYNCING && liveProgress != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFF4FC3F7).copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("simkl_live_progress_panel")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Text(
                                    text = liveProgress.currentPhaseLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4FC3F7),
                                    modifier = Modifier.testTag("simkl_live_progress_phase")
                                )
                                Text(
                                    text = "Serie TV: ${liveProgress.showsProcessed} / ${liveProgress.showsTotal}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NetflixWhite,
                                    modifier = Modifier.testTag("simkl_progress_shows")
                                )
                                Text(
                                    text = "Film: ${liveProgress.moviesProcessed} / ${liveProgress.moviesTotal}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NetflixWhite,
                                    modifier = Modifier.testTag("simkl_progress_movies")
                                )
                                Text(
                                    text = "Anime: ${liveProgress.animeProcessed} / ${liveProgress.animeTotal}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NetflixWhite,
                                    modifier = Modifier.testTag("simkl_progress_anime")
                                )
                            }
                        }
                    }

                    val syncSummary = simklUiState.lastSyncSummary
                    if (simklUiState.connectionState != SimklConnectionState.SYNCING && syncSummary != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val hasFailures = syncSummary.apiRequestsFailed > 0 || syncSummary.itemErrorsCount > 0
                        val summaryBorderColor = if (hasFailures) {
                            Color(0xFFFFB74D).copy(alpha = 0.45f)
                        } else {
                            NetflixMatchGreen.copy(alpha = 0.35f)
                        }
                        Surface(
                            color = NetflixElevatedSurface.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, summaryBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("simkl_sync_summary_panel")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Text(
                                    text = if (syncSummary.isIncremental) {
                                        "Riepilogo ultimo aggiornamento Simkl"
                                    } else {
                                        "Riepilogo importazione libreria Simkl"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NetflixWhite
                                )
                                Text(
                                    text = "Serie TV: ${syncSummary.showsImported} / ${syncSummary.showsFound} • Film: ${syncSummary.moviesImported} / ${syncSummary.moviesFound} • Anime: ${syncSummary.animeImported} / ${syncSummary.animeFound}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NetflixWhite,
                                    modifier = Modifier.testTag("simkl_summary_counts")
                                )
                                val deltaDetails = buildString {
                                    append("Nuovi: ${syncSummary.insertedCount} • Aggiornati: ${syncSummary.updatedCount} • Già presenti: ${syncSummary.alreadyPresentCount}")
                                    if (syncSummary.removedCount > 0) {
                                        append(" • Rimossi: ${syncSummary.removedCount}")
                                    }
                                }
                                Text(
                                    text = deltaDetails,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixLightGray,
                                    modifier = Modifier.testTag("simkl_summary_delta")
                                )
                                Text(
                                    text = "TMDB non riconosciuti: ${syncSummary.tmdbUnrecognizedCount} • Errori elementi: ${syncSummary.itemErrorsCount} • API ok/ko: ${syncSummary.apiRequestsCompleted}/${syncSummary.apiRequestsFailed}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasFailures) Color(0xFFFFB74D) else NetflixLightGray,
                                    modifier = Modifier.testTag("simkl_summary_diagnostics")
                                )
                                val metrics = simklUiState.apiMetrics
                                if (metrics.totalCallsSession > 0) {
                                    Text(
                                        text = "Sessione API: ${metrics.totalCallsSession} chiamate • Evitate: ${metrics.callsAvoidedUnchangedActivities} • Full/Incr: ${metrics.fullSyncsCount}/${metrics.incrementalSyncsCount} • 429/max_items: ${metrics.rateLimit429Count}/${metrics.maxItems400Count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NetflixLightGray,
                                        modifier = Modifier.testTag("simkl_summary_api_metrics")
                                    )
                                }
                                syncSummary.errors.take(3).forEachIndexed { idx, err ->
                                    val codeTag = err.httpCode?.let { "HTTP $it" } ?: err.errorType
                                    Text(
                                        text = "${err.source} [$codeTag]: ${err.message}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NetflixRed,
                                        modifier = Modifier.testTag("simkl_summary_error_$idx")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val showConnectedActions = simklUiState.isConnected ||
                (simklUiState.connectionState == SimklConnectionState.ERROR && !simklUiState.accountDisplayName.isNullOrBlank())

            if (!showConnectedActions) {
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(connectFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> upFocusRequester.safeRequestFocus()
                                    Key.DirectionDown -> downFocusRequester.safeRequestFocus()
                                    Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                    Key.DirectionRight -> true
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            upFocusRequester.takeIfAttached()?.let { up = it }
                            downFocusRequester.takeIfAttached()?.let { down = it }
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.0f,
                            borderWidth = 2.dp,
                            focusTag = "simkl_connect_button",
                            onFocusChange = { focused ->
                                if (focused) onRememberContentFocus(connectFocusRequester)
                            }
                        )
                        .testTag("simkl_connect_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (simklUiState.connectionState == SimklConnectionState.AUTH_EXPIRED) {
                            "Ricollega Account Simkl"
                        } else {
                            "Collega Account Simkl"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                val isSyncingNow = simklUiState.connectionState == SimklConnectionState.SYNCING
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup()
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncingNow,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(syncFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> upFocusRequester.safeRequestFocus()
                                        Key.DirectionDown -> downFocusRequester.safeRequestFocus()
                                        Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                                        Key.DirectionRight -> unlinkFocusRequester.safeRequestFocus()
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                upFocusRequester.takeIfAttached()?.let { up = it }
                                downFocusRequester.takeIfAttached()?.let { down = it }
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                                unlinkFocusRequester.takeIfAttached()?.let { right = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.0f,
                                borderWidth = 2.dp,
                                focusTag = "simkl_sync_now_button",
                                onFocusChange = { focused ->
                                    if (focused) onRememberContentFocus(syncFocusRequester)
                                }
                            )
                            .testTag("simkl_sync_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSyncingNow) "Sincronizzazione…" else "Sincronizza Ora",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onRequestUnlink,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.85f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(unlinkFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> upFocusRequester.safeRequestFocus()
                                        Key.DirectionDown -> downFocusRequester.safeRequestFocus()
                                        Key.DirectionLeft -> {
                                            if (!isSyncingNow) {
                                                syncFocusRequester.safeRequestFocus()
                                            } else {
                                                navRailFocusRequester.safeRequestFocus()
                                            }
                                        }
                                        Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                upFocusRequester.takeIfAttached()?.let { up = it }
                                downFocusRequester.takeIfAttached()?.let { down = it }
                                if (!isSyncingNow) {
                                    syncFocusRequester.takeIfAttached()?.let { left = it }
                                } else {
                                    navRailFocusRequester.takeIfAttached()?.let { left = it }
                                }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.0f,
                                borderWidth = 2.dp,
                                focusTag = "simkl_unlink_button",
                                onFocusChange = { focused ->
                                    if (focused) onRememberContentFocus(unlinkFocusRequester)
                                }
                            )
                            .testTag("simkl_unlink_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scollega Simkl",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
