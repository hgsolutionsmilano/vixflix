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
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.local.CacheStatsSummary
import com.example.data.local.StreamingBufferPreset
import com.example.data.remote.TmdbApiConfig
import com.example.ui.GitHubUpdateUiState
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

/**
 * Categorie modulari della sezione Impostazioni.
 * Aggiungere una voce qui consente di estendere facilmente nuove aree di configurazione future.
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    PLAYBACK("Riproduzione", "Lingua audio, player e catalogo", Icons.Default.Tune),
    UPDATES("Aggiornamenti", "Release GitHub e auto-check", Icons.Default.SystemUpdate),
    API_SOURCES("API & Sorgenti", "Chiave TMDB e config", Icons.Default.Key),
    SYSTEM("Sistema & Dati", "Cronologia locale e info app", Icons.Default.Info)
}

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
    firstControlFocusRequester: FocusRequester? = null,
    navRailFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var selectedCategory by remember { mutableStateOf(requestedCategory) }

    LaunchedEffect(requestedCategoryNonce, requestedCategory) {
        if (requestedCategoryNonce > 0) {
            selectedCategory = requestedCategory
        }
    }

    val hasUpdateAvailable = gitHubState.releaseInfo?.isUpdateAvailable == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 14.dp,
            bottom = contentPadding.calculateBottomPadding() + 48.dp,
            start = if (isTvLayout) 28.dp else 16.dp,
            end = if (isTvLayout) 28.dp else 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "settings_header") {
            SettingsHeaderBanner(
                isTvLayout = isTvLayout,
                streamLang = streamLang,
                preferWebPlayer = preferWebPlayer,
                bufferPreset = bufferPreset,
                hasUpdateAvailable = hasUpdateAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp)
            )
        }

        item(key = "settings_category_bar") {
            SettingsCategoryTabRow(
                selectedCategory = selectedCategory,
                hasUpdateAvailable = hasUpdateAvailable,
                onSelectCategory = { selectedCategory = it },
                firstControlFocusRequester = firstControlFocusRequester,
                navRailFocusRequester = navRailFocusRequester,
                upFocusRequester = upFocusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp)
            )
        }

        item(key = "settings_active_panel") {
            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = {
                    (fadeIn(tween(220)) + androidx.compose.animation.slideInVertically(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 16 }
                    )).togetherWith(fadeOut(tween(150)))
                },
                label = "settingsCategoryTransition",
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp)
            ) { category ->
                when (category) {
                    SettingsCategory.PLAYBACK -> PlaybackSettingsPanel(
                        streamLang = streamLang,
                        preferWebPlayer = preferWebPlayer,
                        bufferPreset = bufferPreset,
                        onSelectStreamLang = onSelectStreamLang,
                        onSetPreferWebPlayer = onSetPreferWebPlayer,
                        onSelectBufferPreset = onSelectBufferPreset,
                        onRefreshCatalog = onRefreshCatalog,
                        navRailFocusRequester = navRailFocusRequester
                    )

                    SettingsCategory.UPDATES -> UpdatesSettingsPanel(
                        state = gitHubState,
                        autoCheckUpdates = autoCheckUpdates,
                        onSetAutoCheckUpdates = onSetAutoCheckUpdates,
                        onRepoSlugChange = onRepoSlugChange,
                        onCheckUpdates = onCheckUpdates,
                        onTriggerUpdate = onTriggerUpdate,
                        navRailFocusRequester = navRailFocusRequester
                    )

                    SettingsCategory.API_SOURCES -> ApiSourcesSettingsPanel(
                        state = gitHubState,
                        onUpdateTmdbKey = onUpdateTmdbKey,
                        onResetTmdbKey = onResetTmdbKey,
                        navRailFocusRequester = navRailFocusRequester
                    )

                    SettingsCategory.SYSTEM -> SystemSettingsPanel(
                        isTvLayout = isTvLayout,
                        continueWatchingCount = continueWatchingCount,
                        watchlistCount = watchlistCount,
                        backgroundPrefetchEnabled = backgroundPrefetchEnabled,
                        cacheStats = cacheStats,
                        isBackgroundCatalogRefreshing = isBackgroundCatalogRefreshing,
                        cacheClearedFeedbackNonce = cacheClearedFeedbackNonce,
                        onSetBackgroundPrefetch = onSetBackgroundPrefetch,
                        onRefreshCatalog = onRefreshCatalog,
                        onClearLocalCache = onClearLocalCache,
                        onClearContinueWatching = onClearContinueWatching,
                        navRailFocusRequester = navRailFocusRequester
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeaderBanner(
    isTvLayout: Boolean,
    streamLang: String,
    preferWebPlayer: Boolean,
    bufferPreset: StreamingBufferPreset,
    hasUpdateAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NetflixCardSurface,
        border = BorderStroke(1.dp, NetflixElevatedSurface),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NetflixRed.copy(alpha = 0.22f),
                            NetflixCardSurface,
                            NetflixDarkSurface
                        )
                    )
                )
                .padding(18.dp)
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
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(NetflixRed.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Impostazioni",
                            tint = NetflixRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Impostazioni VixFlix",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} • ${if (isTvLayout) "Fire TV / Android TV" else "Mobile & Tablet"} • ${if (preferWebPlayer) "Web Player" else "ExoPlayer"} (${bufferPreset.sizeMb} MB)",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = NetflixElevatedSurface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = streamLang.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    if (hasUpdateAvailable) {
                        Surface(
                            color = NetflixMatchGreen.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NetflixMatchGreen)
                        ) {
                            Text(
                                text = "UPDATE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetflixMatchGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
    onSelectCategory: (SettingsCategory) -> Unit,
    firstControlFocusRequester: FocusRequester?,
    navRailFocusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .focusGroup()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsCategory.entries.forEachIndexed { index, category ->
            val isSelected = category == selectedCategory
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) NetflixRed else NetflixCardSurface,
                animationSpec = tween(200),
                label = "settingsTabBg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) NetflixRed else NetflixElevatedSurface,
                animationSpec = tween(200),
                label = "settingsTabBorder"
            )

            Surface(
                color = bgColor,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .then(
                        if (index == 0) {
                            Modifier.safeFocusRequester(firstControlFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .focusProperties {
                        if (index == 0) {
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                        }
                        upFocusRequester.takeIfAttached()?.let { up = it }
                    }
                    .tvFocusableControl(
                        shape = RoundedCornerShape(12.dp),
                        focusedBorderColor = NetflixWhite,
                        focusedScale = 1.04f,
                        onClick = { onSelectCategory(category) }
                    )
                    .testTag("settings_tab_${category.name.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else NetflixLightGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = NetflixWhite
                    )
                    if (category == SettingsCategory.UPDATES && hasUpdateAvailable) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NetflixMatchGreen)
                        )
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
    onSelectStreamLang: (String) -> Unit,
    onSetPreferWebPlayer: (Boolean) -> Unit,
    onSelectBufferPreset: (StreamingBufferPreset) -> Unit,
    onRefreshCatalog: () -> Unit,
    navRailFocusRequester: FocusRequester?
) {
    var catalogRefreshed by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                navRailFocusRequester = navRailFocusRequester
            )
        }

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
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                    }
                    .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f)
                    .testTag("settings_refresh_catalog_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (catalogRefreshed) "Aggiornamento Catalogo Avviato" else "Ricarica Catalogo TMDB Ora",
                    style = MaterialTheme.typography.labelLarge
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
    navRailFocusRequester: FocusRequester?
) {
    var inputRepo by remember(state.repoSlug) { mutableStateOf(state.repoSlug) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val checkUpdatesFocusRequester = remember { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    focusedBorderColor = NetflixRed,
                    unfocusedBorderColor = NetflixElevatedSurface,
                    focusedTextColor = NetflixWhite,
                    unfocusedTextColor = NetflixWhite,
                    focusedLabelColor = NetflixRed,
                    unfocusedLabelColor = NetflixLightGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                        checkUpdatesFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            keyboardController?.hide()
                            checkUpdatesFocusRequester.safeRequestFocus()
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
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                    }
                    .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f)
                    .testTag("check_github_updates_button")
            ) {
                if (state.isChecking) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Controllo GitHub Releases in corso…")
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verifica Nuova Versione su GitHub",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = NetflixRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NetflixRed),
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
                            modifier = Modifier.size(28.dp)
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
                            .focusProperties {
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                            }
                            .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f)
                            .testTag("download_update_apk_button")
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                state.isDownloadingApk -> "Scaricamento (${(state.downloadProgress * 100).toInt()}%)…"
                                state.downloadedApkPath != null -> "Installa Aggiornamento (${release.tagName})"
                                release.isUpdateAvailable -> "Scarica e Aggiorna Ora (${release.tagName})"
                                else -> "Riscarica APK Release (${release.tagName})"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
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
    navRailFocusRequester: FocusRequester?
) {
    var inputTmdbKey by remember(state.tmdbApiKey) { mutableStateOf(state.tmdbApiKey) }
    var copiedFeedback by remember { mutableStateOf(false) }
    var showTechnicalDetails by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val saveKeyFocusRequester = remember { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    focusedBorderColor = NetflixRed,
                    unfocusedBorderColor = NetflixElevatedSurface,
                    focusedTextColor = NetflixWhite,
                    unfocusedTextColor = NetflixWhite,
                    focusedLabelColor = NetflixRed,
                    unfocusedLabelColor = NetflixLightGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                        saveKeyFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            keyboardController?.hide()
                            saveKeyFocusRequester.safeRequestFocus()
                        } else {
                            false
                        }
                    }
                    .testTag("tmdb_api_key_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                Button(
                    onClick = { onUpdateTmdbKey(inputTmdbKey) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(saveKeyFocusRequester)
                        .focusProperties {
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                        }
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f)
                        .testTag("save_tmdb_key_button")
                ) {
                    Text("Applica Chiave", style = MaterialTheme.typography.labelLarge)
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
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.03f)
                        .testTag("copy_tmdb_key_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copia chiave API",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (copiedFeedback) "Copiata!" else "Copia")
                }

                if (inputTmdbKey != TmdbApiConfig.INTEGRATED_TMDB_API_KEY) {
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
                            .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.03f)
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
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                    }
                    .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.01f)
            ) {
                Icon(
                    imageVector = if (showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showTechnicalDetails) "Nascondi Dettagli Configurazione" else "Mostra Dettagli Configurazione",
                    style = MaterialTheme.typography.labelLarge
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
    continueWatchingCount: Int,
    watchlistCount: Int,
    backgroundPrefetchEnabled: Boolean,
    cacheStats: CacheStatsSummary,
    isBackgroundCatalogRefreshing: Boolean,
    cacheClearedFeedbackNonce: Int,
    onSetBackgroundPrefetch: (Boolean) -> Unit,
    onRefreshCatalog: () -> Unit,
    onClearLocalCache: () -> Unit,
    onClearContinueWatching: () -> Unit,
    navRailFocusRequester: FocusRequester?
) {
    var clearedFeedback by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

            Spacer(modifier = Modifier.height(10.dp))

            SettingsInfoLine(
                label = "Snapshot Catalogo Home",
                value = formatRelativeCacheAge(cacheStats.catalogUpdatedAtMs)
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitchRow(
                label = "Precaricamento leggero locandine",
                description = "Precarica in background Hero e locandine principali senza rallentare UI o streaming",
                checked = backgroundPrefetchEnabled,
                onCheckedChange = onSetBackgroundPrefetch,
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
                        .focusProperties {
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                        }
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f)
                        .testTag("settings_refresh_catalog_cache_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBackgroundCatalogRefreshing) "Aggiornamento…" else "Aggiorna Ora",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onClearLocalCache,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NetflixRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f)
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
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        SettingsSectionCard(
            title = "Archivio & Cronologia Locale",
            subtitle = "Dati utente persistenti (non rimossi dallo svuotamento cache)",
            icon = Icons.Default.Storage
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
                    onClearContinueWatching()
                    clearedFeedback = true
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
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                    }
                    .tvFocusableControl(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f)
                    .testTag("settings_clear_history_button")
            ) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        clearedFeedback && continueWatchingCount == 0 -> "Cronologia Svuotata"
                        continueWatchingCount == 0 -> "Nessuna Cronologia da Rimuovere"
                        else -> "Svuota Continua a Guardare ($continueWatchingCount)"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        SettingsSectionCard(
            title = "Informazioni Applicazione",
            subtitle = "Dettagli build e compatibilità dispositivo",
            icon = Icons.Default.Info
        ) {
            SettingsInfoLine(label = "Versione App", value = "v${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})")
            SettingsInfoLine(label = "Application ID", value = BuildConfig.APPLICATION_ID)
            SettingsInfoLine(label = "Interfaccia Attiva", value = if (isTvLayout) "Android TV / Fire TV (Telecomando)" else "Smartphone / Tablet (Touch)")
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
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String? = null,
    badgeColor: Color = NetflixMatchGreen,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
        border = BorderStroke(1.dp, NetflixElevatedSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = NetflixLightGray
                        )
                    }
                }

                if (!badgeText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = badgeColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    navRailFocusRequester: FocusRequester?,
    tagPrefix: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
    ) {
        options.forEachIndexed { index, (key, label) ->
            val selected = key == selectedKey
            val containerColor by animateColorAsState(
                targetValue = if (selected) NetflixRed else NetflixDarkSurface,
                animationSpec = tween(180),
                label = "choiceBg"
            )
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (selected) NetflixRed else NetflixElevatedSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .focusProperties {
                        if (index == 0) {
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                        }
                    }
                    .tvFocusableControl(
                        shape = RoundedCornerShape(10.dp),
                        focusedBorderColor = NetflixWhite,
                        focusedScale = 1.03f,
                        onClick = { onSelect(key) }
                    )
                    .testTag("${tagPrefix}_$key")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = NetflixWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    navRailFocusRequester: FocusRequester?,
    testTagName: String
) {
    Surface(
        color = NetflixDarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NetflixElevatedSurface),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusProperties {
                navRailFocusRequester.takeIfAttached()?.let { left = it }
            }
            .tvFocusableControl(
                shape = RoundedCornerShape(10.dp),
                focusedBorderColor = NetflixWhite,
                focusedScale = 1.01f,
                onClick = { onCheckedChange(!checked) }
            )
            .testTag(testTagName)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = NetflixWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = NetflixLightGray
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NetflixRed,
                    uncheckedThumbColor = NetflixLightGray,
                    uncheckedTrackColor = NetflixElevatedSurface
                )
            )
        }
    }
}

@Composable
private fun SettingsStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NetflixDarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NetflixElevatedSurface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NetflixRed
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NetflixLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsInfoLine(
    label: String,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NetflixLightGray
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = NetflixWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BufferPresetGrid(
    selectedPreset: StreamingBufferPreset,
    onSelectPreset: (StreamingBufferPreset) -> Unit,
    navRailFocusRequester: FocusRequester?
) {
    val rows = remember { StreamingBufferPreset.entries.chunked(2) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
    ) {
        rows.forEach { rowPresets ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowPresets.forEachIndexed { colIndex, preset ->
                    val isSelected = preset == selectedPreset
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) NetflixRed.copy(alpha = 0.22f) else NetflixDarkSurface,
                        animationSpec = tween(180),
                        label = "bufferBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) NetflixRed else NetflixElevatedSurface,
                        animationSpec = tween(180),
                        label = "bufferBorder"
                    )

                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 66.dp)
                            .focusProperties {
                                if (colIndex == 0) {
                                    navRailFocusRequester.takeIfAttached()?.let { left = it }
                                }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(12.dp),
                                focusedBorderColor = NetflixWhite,
                                focusedScale = 1.03f,
                                onClick = { onSelectPreset(preset) }
                            )
                            .testTag("settings_buffer_${preset.storageKey}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NetflixWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = if (isSelected) NetflixRed else NetflixElevatedSurface,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${preset.sizeMb} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.windowHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) NetflixMatchGreen else NetflixLightGray,
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
