package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.BuildConfig
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbGenreDto
import com.example.data.remote.TmdbGenreMap
import com.example.data.remote.TmdbWatchProviderDto
import com.example.ui.NavTab
import com.example.ui.theme.BebasNeueFontFamily
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixGold
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite

@Composable
fun NetflixTopBar(
    selectedTab: NavTab,
    onSelectTab: (NavTab) -> Unit,
    searchQuery: String,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    streamLang: String,
    onToggleLang: () -> Unit,
    hasUpdateAvailable: Boolean,
    onClickUpdateIcon: () -> Unit,
    isTvLayout: Boolean = false,
    searchFocusRequester: FocusRequester? = null,
    brandLogoFocusRequester: FocusRequester? = null,
    navRailFocusRequester: FocusRequester? = null,
    downToContentFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isTvLayout && !isSearchOpen) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NetflixBlack.copy(alpha = 0.96f),
                        NetflixBlack.copy(alpha = 0.85f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = if (isTvLayout) 28.dp else 16.dp, vertical = 8.dp)
    ) {
        val resolvedDownTarget = if (isSearchOpen) {
            searchFocusRequester ?: downToContentFocusRequester
        } else {
            downToContentFocusRequester
        }

        if (!isTvLayout) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo VixFlix (visibile solo in visualizzazione verticale)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .safeFocusRequester(brandLogoFocusRequester)
                        .focusProperties {
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                            resolvedDownTarget.takeIfAttached()?.let { down = it }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.DirectionDown
                            ) {
                                resolvedDownTarget.safeRequestFocus()
                            } else {
                                false
                            }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(8.dp),
                            focusedBorderColor = NetflixRed,
                            onClick = { onSelectTab(NavTab.HOME) }
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("brand_logo")
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(NetflixRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "V",
                            fontFamily = BebasNeueFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VIXFLIX",
                        fontFamily = BebasNeueFontFamily,
                        fontSize = 28.sp,
                        letterSpacing = 2.sp,
                        color = NetflixRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = NetflixElevatedSurface,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NetflixLightGray,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Switch lingua Audio/Sub VixSrc (IT / EN)
                    Surface(
                        color = NetflixCardSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NetflixElevatedSurface),
                        modifier = Modifier
                            .focusProperties {
                                resolvedDownTarget.takeIfAttached()?.let { down = it }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.DirectionDown
                                ) {
                                    resolvedDownTarget.safeRequestFocus()
                                } else {
                                    false
                                }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderColor = NetflixWhite,
                                onClick = onToggleLang
                            )
                            .testTag("lang_toggle_button")
                    ) {
                        Text(
                            text = streamLang.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = NetflixWhite,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Pulsante GitHub Update check
                    Box {
                        IconButton(
                            onClick = onClickUpdateIcon,
                            modifier = Modifier
                                .focusProperties {
                                    resolvedDownTarget.takeIfAttached()?.let { down = it }
                                }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown &&
                                        keyEvent.key == Key.DirectionDown
                                    ) {
                                        resolvedDownTarget.safeRequestFocus()
                                    } else {
                                        false
                                    }
                                }
                                .tvFocusableControl(
                                    shape = CircleShape,
                                    focusedBorderColor = NetflixWhite
                                )
                                .testTag("github_update_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Controlla aggiornamenti GitHub",
                                tint = if (hasUpdateAvailable) NetflixMatchGreen else NetflixWhite
                            )
                        }
                        if (hasUpdateAvailable) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(NetflixRed)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                            )
                        }
                    }

                    // Pulsante Cerca TMDB
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier
                            .focusProperties {
                                resolvedDownTarget.takeIfAttached()?.let { down = it }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.DirectionDown
                                ) {
                                    resolvedDownTarget.safeRequestFocus()
                                } else {
                                    false
                                }
                            }
                            .tvFocusableControl(
                                shape = CircleShape,
                                focusedBorderColor = NetflixWhite
                            )
                            .testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Cerca su TMDB",
                            tint = NetflixWhite
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isSearchOpen,
            enter = fadeIn(tween(120, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(90))
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Cerca film, serie TV, attori in tempo reale su TMDB…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NetflixMutedGray
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = NetflixRed
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty() || isTvLayout) {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    onSearchQueryChange("")
                                } else {
                                    onToggleSearch()
                                }
                            },
                            modifier = Modifier.tvFocusableControl(shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancella o chiudi ricerca",
                                tint = NetflixLightGray
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        downToContentFocusRequester.safeRequestFocus()
                    }
                ),
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NetflixCardSurface,
                    unfocusedContainerColor = NetflixCardSurface,
                    focusedTextColor = NetflixWhite,
                    unfocusedTextColor = NetflixWhite,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NetflixRed
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isTvLayout) 4.dp else 8.dp)
                    .safeFocusRequester(searchFocusRequester)
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                        downToContentFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionDown -> {
                                    keyboardController?.hide()
                                    downToContentFocusRequester.safeRequestFocus()
                                }
                                Key.DirectionLeft -> {
                                    if (searchQuery.isEmpty()) {
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
                    .testTag("search_input_field")
            )
        }

        if (!isTvLayout) {
            Spacer(modifier = Modifier.height(8.dp))

            // Pillole categorie stile Netflix (completamente focusabili anche con D-pad)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val pillBg = if (isSelected) NetflixRed else NetflixCardSurface.copy(alpha = 0.85f)
                    val pillBorder = if (isSelected) NetflixRed else NetflixElevatedSurface
                    Surface(
                        color = pillBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            width = 1.dp,
                            color = pillBorder
                        ),
                        modifier = Modifier
                            .tvFocusableControl(
                                shape = RoundedCornerShape(50),
                                focusedBorderColor = NetflixWhite,
                                onClick = { onSelectTab(tab) }
                            )
                            .testTag("nav_tab_${tab.name.lowercase()}")
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = NetflixWhite,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSizedImageRequest(
    url: String?,
    widthDp: Dp,
    heightDp: Dp,
    crossfadeMs: Int = 0
): Any? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.roundToPx().coerceAtLeast(64) }
    val heightPx = with(density) { heightDp.roundToPx().coerceAtLeast(64) }
    return remember(context, url, widthPx, heightPx, crossfadeMs) {
        if (url.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(url)
                .size(widthPx, heightPx)
                .precision(Precision.INEXACT)
                .scale(Scale.FILL)
                .allowHardware(true)
                .allowRgb565(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(crossfadeMs)
                .build()
        }
    }
}

@Composable
fun NetflixHeroBanner(
    item: MediaItem,
    isInWatchlist: Boolean,
    savedProgress: WatchProgressEntity?,
    onPlayClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onInfoClick: () -> Unit,
    playButtonFocusRequester: FocusRequester? = null,
    navRailFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
    firstRowFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Boolean)? = null,
    onDownKey: (() -> Boolean)? = null,
    isTvLayout: Boolean = false,
    modifier: Modifier = Modifier
) {
    var watchFocused by remember { mutableStateOf(false) }
    var playFocused by remember { mutableStateOf(false) }
    var infoFocused by remember { mutableStateOf(false) }

    val watchFocusRequester = remember { FocusRequester() }
    val resolvedPlayFocusRequester = playButtonFocusRequester ?: remember { FocusRequester() }
    val infoFocusRequester = remember { FocusRequester() }
    val heroGenres = remember(item.genreIds) { TmdbGenreMap.namesFor(item.genreIds) }
    val heroHeight = if (isTvLayout) 340.dp else 500.dp

    val handleHeroVerticalKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.DirectionDown -> (onDownKey?.invoke() == true) || firstRowFocusRequester.safeRequestFocus()
                Key.DirectionUp -> (onUpKey?.invoke() == true) || topBarFocusRequester.safeRequestFocus()
                else -> false
            }
        } else {
            false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .pointerInput(isTvLayout) {
                if (!isTvLayout) {
                    detectTapGestures { onInfoClick() }
                }
            }
            .testTag("hero_banner_card")
    ) {
        AsyncImage(
            model = rememberSizedImageRequest(
                url = item.backdropUrl ?: item.posterUrl,
                widthDp = 960.dp,
                heightDp = heroHeight,
                crossfadeMs = 120
            ),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Vignettatura cinematografica
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to NetflixBlack.copy(alpha = 0.65f),
                        0.3f to Color.Transparent,
                        0.65f to NetflixBlack.copy(alpha = 0.65f),
                        1.0f to NetflixBlack
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = if (isTvLayout) 14.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = NetflixRed,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "TOP 10",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = if (item.isSeries) "SERIE TV #1 OGGI" else "FILM #1 OGGI",
                        style = MaterialTheme.typography.labelLarge,
                        color = NetflixWhite
                    )
                    Text(
                        text = "• ${item.matchPercentage}% Compatibile",
                        style = MaterialTheme.typography.labelLarge,
                        color = NetflixMatchGreen
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.title.uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = NetflixWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (heroGenres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = heroGenres.joinToString("  •  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixLightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (savedProgress != null && savedProgress.startSeconds > 0L) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(0.75f)
                ) {
                    LinearProgressIndicator(
                        progress = { savedProgress.progressFraction },
                        color = NetflixRed,
                        trackColor = NetflixElevatedSurface,
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "Dal min ${savedProgress.startMinutes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isTvLayout) 10.dp else 14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsante + La mia lista
                OutlinedButton(
                    onClick = onToggleWatchlist,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        if (watchFocused) 2.5.dp else 1.dp,
                        if (watchFocused) NetflixWhite else NetflixElevatedSurface
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (watchFocused) NetflixElevatedSurface else NetflixCardSurface.copy(alpha = 0.8f),
                        contentColor = NetflixWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .safeFocusRequester(watchFocusRequester)
                        .onPreviewKeyEvent(handleHeroVerticalKey)
                        .focusProperties {
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                            resolvedPlayFocusRequester.takeIfAttached()?.let { right = it }
                            topBarFocusRequester.takeIfAttached()?.let { up = it }
                            firstRowFocusRequester.takeIfAttached()?.let { down = it }
                        }
                        .onFocusChanged { watchFocused = it.isFocused }
                        .testTag("hero_watchlist_button")
                ) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isInWatchlist) "Nella lista" else "La mia lista",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Pulsante principale Riproduci (Bianco stile Netflix, fuoco iniziale TV)
                Button(
                    onClick = onPlayClick,
                    shape = RoundedCornerShape(6.dp),
                    border = if (playFocused) BorderStroke(3.dp, NetflixRed) else null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixWhite,
                        contentColor = NetflixBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 11.dp),
                    modifier = Modifier
                        .safeFocusRequester(resolvedPlayFocusRequester)
                        .onPreviewKeyEvent(handleHeroVerticalKey)
                        .focusProperties {
                            watchFocusRequester.takeIfAttached()?.let { left = it }
                            infoFocusRequester.takeIfAttached()?.let { right = it }
                            topBarFocusRequester.takeIfAttached()?.let { up = it }
                            firstRowFocusRequester.takeIfAttached()?.let { down = it }
                        }
                        .onFocusChanged { playFocused = it.isFocused }
                        .testTag("hero_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (savedProgress != null && savedProgress.startSeconds > 0L) {
                            "Riprendi (${savedProgress.startMinutes}m)"
                        } else {
                            "Riproduci"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Pulsante Info
                OutlinedButton(
                    onClick = onInfoClick,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        if (infoFocused) 2.5.dp else 1.dp,
                        if (infoFocused) NetflixWhite else NetflixElevatedSurface
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (infoFocused) NetflixElevatedSurface else NetflixCardSurface.copy(alpha = 0.8f),
                        contentColor = NetflixWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .safeFocusRequester(infoFocusRequester)
                        .onPreviewKeyEvent(handleHeroVerticalKey)
                        .focusProperties {
                            resolvedPlayFocusRequester.takeIfAttached()?.let { left = it }
                            topBarFocusRequester.takeIfAttached()?.let { up = it }
                            firstRowFocusRequester.takeIfAttached()?.let { down = it }
                        }
                        .onFocusChanged { infoFocused = it.isFocused }
                        .testTag("hero_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Info",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * Sezione "Continua a guardare" alimentata dal database locale Room.
 * Passa tramite API `startAt` (secondi) e `startMinutes` (minuti) al player.
 */
@Composable
fun ContinueWatchingSection(
    entries: List<WatchProgressEntity>,
    onResumeClick: (WatchProgressEntity) -> Unit,
    onInfoClick: (WatchProgressEntity) -> Unit,
    onRemoveClick: (WatchProgressEntity) -> Unit,
    onTvLongPress: ((WatchProgressEntity) -> Unit)? = null,
    onItemFocused: ((FocusRequester) -> Unit)? = null,
    isTvLayout: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Boolean)? = null,
    onDownKey: (() -> Boolean)? = null,
    navRailFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val safeEntries = remember(entries) { entries.distinctBy { it.id } }
    if (safeEntries.isEmpty()) return

    val listState = rememberLazyListState()
    val firstVisibleIdx = listState.firstVisibleItemIndex.coerceIn(0, safeEntries.lastIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("continue_watching_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTvLayout) 24.dp else 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NetflixRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Continua a guardare",
                    style = MaterialTheme.typography.titleLarge,
                    color = NetflixWhite
                )
            }
            Text(
                text = if (isTvLayout) "${safeEntries.size} in corso • Tieni premuto OK per opzioni" else "Room DB • ${safeEntries.size} in corso",
                style = MaterialTheme.typography.labelSmall,
                color = NetflixLightGray
            )
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = if (isTvLayout) 24.dp else 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = safeEntries,
                key = { _, it -> it.id },
                contentType = { _, _ -> "continue_watching_card" }
            ) { index, entry ->
                val itemFocusRequester = if (index == firstVisibleIdx && firstItemFocusRequester != null) {
                    firstItemFocusRequester
                } else {
                    remember(entry.id) { FocusRequester() }
                }
                ContinueWatchingCard(
                    entry = entry,
                    itemIndex = index,
                    listState = listState,
                    isTvLayout = isTvLayout,
                    onResume = { onResumeClick(entry) },
                    onInfo = { onInfoClick(entry) },
                    onRemove = { onRemoveClick(entry) },
                    onLongPress = onTvLongPress?.let { handler -> { handler(entry) } },
                    onFocused = { onItemFocused?.invoke(itemFocusRequester) },
                    modifier = Modifier
                        .safeFocusRequester(itemFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> (onDownKey?.invoke() == true) || downFocusRequester.safeRequestFocus()
                                    Key.DirectionUp -> (onUpKey?.invoke() == true) || upFocusRequester.safeRequestFocus()
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            if (index == 0) {
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                            }
                            upFocusRequester.takeIfAttached()?.let { up = it }
                            downFocusRequester.takeIfAttached()?.let { down = it }
                        }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchProgressEntity,
    itemIndex: Int,
    listState: LazyListState,
    isTvLayout: Boolean,
    onResume: () -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val imageUrl = entry.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
        ?: entry.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
        modifier = modifier
            .width(228.dp)
            .animatedPosterShowcase(
                itemIndex = itemIndex,
                listState = listState,
                isFocused = isFocused,
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.07f,
                onFocusedChanged = { focused ->
                    isFocused = focused
                    if (focused) onFocused()
                },
                onLongClick = onLongPress,
                onQuickPlay = onResume,
                onClick = onResume
            )
            .testTag("continue_watching_card_${entry.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
            ) {
                AsyncImage(
                    model = rememberSizedImageRequest(imageUrl, 228.dp, 126.dp),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NetflixBlack.copy(alpha = if (isFocused) 0.2f else 0.38f))
                )

                // Badge Episodio / Minuto di partenza API
                Surface(
                    color = NetflixBlack.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    val label = if (entry.mediaType == "tv" && entry.seasonNumber != null && entry.episodeNumber != null) {
                        "S${entry.seasonNumber}:E${entry.episodeNumber} • Min ${entry.startMinutes}"
                    } else {
                        "Da min ${entry.startMinutes} (${formatSecondsToMmSs(entry.startSeconds)})"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixWhite,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Icona Play centrale con cerchio bianco (evidenziata su focus TV)
                val playBorderColor = if (isFocused) NetflixRed else NetflixWhite
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(if (isFocused) NetflixRed.copy(alpha = 0.9f) else NetflixBlack.copy(alpha = 0.65f))
                        .border(2.dp, playBorderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Riprendi riproduzione",
                        tint = NetflixWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Barra rossa progresso stile Netflix sul bordo inferiore dell'immagine
                LinearProgressIndicator(
                    progress = { entry.progressFraction },
                    color = NetflixRed,
                    trackColor = NetflixElevatedSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                )
            }

            // Barra inferiore con titolo, minuti rimanenti, Info e Rimuovi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NetflixDarkSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = NetflixWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isTvLayout && isFocused -> "OK Riprendi • Tieni premuto Opzioni"
                            entry.remainingMinutes > 0 -> "-${entry.remainingMinutes} min • dal ${entry.startMinutes}°m"
                            else -> "Riprendi da ${formatSecondsToMmSs(entry.startSeconds)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTvLayout && isFocused) NetflixMatchGreen else NetflixLightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isTvLayout) {
                    IconButton(
                        onClick = onInfo,
                        modifier = Modifier
                            .size(34.dp)
                            .tvFocusableControl(shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Dettagli",
                            tint = NetflixLightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(34.dp)
                            .tvFocusableControl(shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Rimuovi da Continua a guardare",
                            tint = NetflixMutedGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Top10Section(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onItemQuickPlay: ((MediaItem) -> Unit)? = null,
    onItemLongClick: ((MediaItem) -> Unit)? = null,
    onItemFocused: ((FocusRequester) -> Unit)? = null,
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Boolean)? = null,
    onDownKey: (() -> Boolean)? = null,
    navRailFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val safeItems = remember(items) { items.distinctBy { it.uniqueKey } }
    if (safeItems.isEmpty()) return

    val listState = rememberLazyListState()
    val firstVisibleIdx = listState.firstVisibleItemIndex.coerceIn(0, safeItems.lastIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = NetflixWhite,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = safeItems,
                key = { _, item -> "top10_${item.uniqueKey}" },
                contentType = { _, _ -> "top10_card" }
            ) { index, item ->
                val itemFocusRequester = if (index == firstVisibleIdx && firstItemFocusRequester != null) {
                    firstItemFocusRequester
                } else {
                    remember(item.uniqueKey) { FocusRequester() }
                }
                Top10PosterCard(
                    rank = index + 1,
                    itemIndex = index,
                    listState = listState,
                    item = item,
                    onClick = { onItemClick(item) },
                    onQuickPlay = onItemQuickPlay?.let { handler -> { handler(item) } },
                    onLongClick = onItemLongClick?.let { handler -> { handler(item) } },
                    onFocused = { onItemFocused?.invoke(itemFocusRequester) },
                    modifier = Modifier
                        .safeFocusRequester(itemFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> (onDownKey?.invoke() == true) || downFocusRequester.safeRequestFocus()
                                    Key.DirectionUp -> (onUpKey?.invoke() == true) || upFocusRequester.safeRequestFocus()
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            if (index == 0) {
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                            }
                            upFocusRequester.takeIfAttached()?.let { up = it }
                            downFocusRequester.takeIfAttached()?.let { down = it }
                        }
                )
            }
        }
    }
}

@Composable
private fun Top10PosterCard(
    rank: Int,
    itemIndex: Int,
    listState: LazyListState,
    item: MediaItem,
    onClick: () -> Unit,
    onQuickPlay: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(176.dp)
            .height(190.dp)
            .animatedPosterShowcase(
                itemIndex = itemIndex,
                listState = listState,
                isFocused = isFocused,
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.10f,
                onFocusedChanged = { focused ->
                    isFocused = focused
                    if (focused) onFocused()
                },
                onLongClick = onLongClick,
                onQuickPlay = onQuickPlay,
                onClick = onClick
            )
            .testTag("top10_card_$rank")
    ) {
        // Numero gigante stilizzato Netflix in basso a sinistra su layer hardware dedicato
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-2).dp, y = 14.dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) {
            Text(
                text = rank.toString(),
                style = TextStyle(
                    fontFamily = BebasNeueFontFamily,
                    fontSize = 132.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) NetflixRed else NetflixElevatedSurface,
                    drawStyle = Stroke(
                        width = 6f,
                        join = StrokeJoin.Round
                    )
                )
            )
            Text(
                text = rank.toString(),
                style = TextStyle(
                    fontFamily = BebasNeueFontFamily,
                    fontSize = 132.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetflixBlack.copy(alpha = 0.85f)
                )
            )
        }

        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
            modifier = Modifier
                .width(124.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = rememberSizedImageRequest(item.posterUrl, 124.dp, 190.dp),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    color = NetflixRed,
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Sovrimpressione immediata con il titolo quando evidenziato con il telecomando
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, NetflixBlack.copy(alpha = 0.94f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCatalogRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onItemQuickPlay: ((MediaItem) -> Unit)? = null,
    onItemLongClick: ((MediaItem) -> Unit)? = null,
    onItemFocused: ((FocusRequester) -> Unit)? = null,
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Boolean)? = null,
    onDownKey: (() -> Boolean)? = null,
    navRailFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val safeItems = remember(items) { items.distinctBy { it.uniqueKey } }
    if (safeItems.isEmpty()) return

    val listState = rememberLazyListState()
    val firstVisibleIdx = listState.firstVisibleItemIndex.coerceIn(0, safeItems.lastIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = NetflixWhite,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = safeItems,
                key = { _, item -> "${title}_${item.uniqueKey}" },
                contentType = { _, _ -> "poster_card" }
            ) { index, item ->
                val itemFocusRequester = if (index == firstVisibleIdx && firstItemFocusRequester != null) {
                    firstItemFocusRequester
                } else {
                    remember("${title}_${item.uniqueKey}") { FocusRequester() }
                }
                MediaPosterCard(
                    item = item,
                    itemIndex = index,
                    listState = listState,
                    onClick = { onItemClick(item) },
                    onQuickPlay = onItemQuickPlay?.let { handler -> { handler(item) } },
                    onLongClick = onItemLongClick?.let { handler -> { handler(item) } },
                    onFocused = { onItemFocused?.invoke(itemFocusRequester) },
                    modifier = Modifier
                        .safeFocusRequester(itemFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> (onDownKey?.invoke() == true) || downFocusRequester.safeRequestFocus()
                                    Key.DirectionUp -> (onUpKey?.invoke() == true) || upFocusRequester.safeRequestFocus()
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            if (index == 0) {
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                            }
                            upFocusRequester.takeIfAttached()?.let { up = it }
                            downFocusRequester.takeIfAttached()?.let { down = it }
                        }
                )
            }
        }
    }
}

@Composable
fun MediaPosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemIndex: Int = 0,
    listState: LazyListState? = null,
    onQuickPlay: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
        modifier = modifier
            .width(130.dp)
            .aspectRatio(0.67f)
            .animatedPosterShowcase(
                itemIndex = itemIndex,
                listState = listState,
                isFocused = isFocused,
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.12f,
                onFocusedChanged = { focused ->
                    isFocused = focused
                    if (focused) onFocused()
                },
                onLongClick = onLongClick,
                onQuickPlay = onQuickPlay,
                onClick = onClick
            )
            .testTag("poster_card_${item.uniqueKey}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = rememberSizedImageRequest(item.posterUrl, 130.dp, 194.dp),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Badge tipo media (SERIE / FILM) in alto a sinistra
            Surface(
                color = NetflixBlack.copy(alpha = 0.78f),
                shape = RoundedCornerShape(bottomEnd = 6.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = if (item.isSeries) "SERIE" else "FILM",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isSeries) NetflixRed else NetflixWhite,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Voto TMDB in alto a destra
            if (item.voteAverage > 0.0) {
                Surface(
                    color = NetflixBlack.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = NetflixGold,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", item.voteAverage),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = NetflixWhite
                        )
                    }
                }
            }

            // Overlay titolo + anno immediato quando la locandina è selezionata col telecomando TV
            if (isFocused) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to NetflixBlack.copy(alpha = 0.85f),
                                1f to NetflixBlack.copy(alpha = 0.96f)
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetflixWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.releaseYear.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${item.releaseYear} • OK per aprire",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = NetflixMatchGreen,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieGenreChipBar(
    genres: List<TmdbGenreDto>,
    selectedGenre: TmdbGenreDto?,
    onSelectGenre: (TmdbGenreDto?) -> Unit,
    firstChipFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Boolean)? = null,
    onDownKey: (() -> Boolean)? = null,
    navRailFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val safeGenres = remember(genres) { genres.distinctBy { it.id } }
    val listState = rememberLazyListState()
    val targetIndex = remember(safeGenres, selectedGenre) {
        if (selectedGenre == null) {
            0
        } else {
            val found = safeGenres.indexOfFirst { it.id == selectedGenre.id }
            if (found >= 0) found + 1 else 0
        }
    }

    LaunchedEffect(targetIndex) {
        if (targetIndex >= 0) {
            runCatching { listState.animateScrollToItem(targetIndex) }
        }
    }

    val handleGenreVerticalKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.DirectionDown -> (onDownKey?.invoke() == true) || downFocusRequester.safeRequestFocus()
                Key.DirectionUp -> (onUpKey?.invoke() == true) || upFocusRequester.safeRequestFocus()
                else -> false
            }
        } else {
            false
        }
    }

    val firstVisibleIdx = listState.firstVisibleItemIndex.coerceIn(0, safeGenres.size)
    val focusTargetChipIdx = if (selectedGenre != null) {
        val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (targetIndex in visibleIndices || visibleIndices.isEmpty()) targetIndex else firstVisibleIdx
    } else {
        firstVisibleIdx
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .testTag("movie_genre_chip_bar")
    ) {
        item(key = "genre_all", contentType = "genre_chip") {
            val isSelected = selectedGenre == null
            GenreFilterChipItem(
                label = "Tutti",
                isSelected = isSelected,
                onClick = { onSelectGenre(null) },
                modifier = Modifier
                    .then(
                        if (focusTargetChipIdx == 0) {
                            Modifier.safeFocusRequester(firstChipFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .onPreviewKeyEvent(handleGenreVerticalKey)
                    .focusProperties {
                        navRailFocusRequester.takeIfAttached()?.let { left = it }
                        upFocusRequester.takeIfAttached()?.let { up = it }
                        downFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    .testTag("genre_chip_all")
            )
        }

        itemsIndexed(
            items = safeGenres,
            key = { _, genre -> "genre_${genre.id}" },
            contentType = { _, _ -> "genre_chip" }
        ) { idx, genre ->
            val isSelected = selectedGenre?.id == genre.id
            val chipLazyIndex = idx + 1
            GenreFilterChipItem(
                label = genre.name,
                isSelected = isSelected,
                onClick = { onSelectGenre(genre) },
                modifier = Modifier
                    .then(
                        if (focusTargetChipIdx == chipLazyIndex) {
                            Modifier.safeFocusRequester(firstChipFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .onPreviewKeyEvent(handleGenreVerticalKey)
                    .focusProperties {
                        upFocusRequester.takeIfAttached()?.let { up = it }
                        downFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    .testTag("genre_chip_${genre.id}")
            )
        }
    }
}

@Composable
private fun GenreFilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isSelected -> NetflixRed
        isFocused -> NetflixElevatedSurface
        else -> NetflixCardSurface
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .heightIn(min = 48.dp)
            .tvFocusableControl(
                shape = RoundedCornerShape(50),
                focusedScale = 1.05f,
                focusedBorderColor = NetflixWhite,
                unfocusedBorderColor = if (isSelected) NetflixRed else NetflixElevatedSurface,
                borderWidth = if (isFocused) 2.dp else 1.dp,
                onFocusChange = { isFocused = it },
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected || isFocused) NetflixWhite else NetflixLightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatSecondsToMmSs(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val seconds = safe % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun StreamingPlatformsStrip(
    providers: List<TmdbWatchProviderDto>,
    selectedProvider: TmdbWatchProviderDto?,
    onSelectProvider: (TmdbWatchProviderDto?) -> Unit,
    showHeader: Boolean = true,
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Boolean)? = null,
    onDownKey: (() -> Boolean)? = null,
    navRailFocusRequester: FocusRequester? = null,
    onItemFocused: ((FocusRequester) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val safeProviders = remember(providers) { providers.distinctBy { it.providerId } }
    if (safeProviders.isEmpty()) return

    val listState = rememberLazyListState()
    val targetIndex = remember(safeProviders, selectedProvider) {
        if (selectedProvider == null) {
            0
        } else {
            val found = safeProviders.indexOfFirst { it.providerId == selectedProvider.providerId }
            if (found >= 0) found else 0
        }
    }

    LaunchedEffect(targetIndex, selectedProvider?.providerId) {
        if (targetIndex >= 0) {
            runCatching { listState.animateScrollToItem(targetIndex) }
        }
    }

    val handlePlatformVerticalKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.DirectionDown -> (onDownKey?.invoke() == true) || downFocusRequester.safeRequestFocus()
                Key.DirectionUp -> (onUpKey?.invoke() == true) || upFocusRequester.safeRequestFocus()
                else -> false
            }
        } else {
            false
        }
    }

    val firstVisibleProviderIdx = listState.firstVisibleItemIndex.coerceIn(0, safeProviders.lastIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (showHeader) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp)
            ) {
                Column {
                    Text(
                        text = "Piattaforme Streaming",
                        style = MaterialTheme.typography.titleLarge,
                        color = NetflixWhite
                    )
                    Text(
                        text = "Disney+, Paramount+, Prime Video, Netflix e altre da TMDB",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray
                    )
                }

                if (selectedProvider != null) {
                    Surface(
                        color = NetflixElevatedSurface,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, NetflixRed),
                        modifier = Modifier
                            .onPreviewKeyEvent(handlePlatformVerticalKey)
                            .focusProperties {
                                upFocusRequester.takeIfAttached()?.let { up = it }
                                downFocusRequester.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(50),
                                focusedBorderColor = NetflixWhite,
                                onClick = { onSelectProvider(null) }
                            )
                            .testTag("platform_strip_reset_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Mostra tutte",
                                tint = NetflixWhite,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Mostra Home completa",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NetflixWhite
                            )
                        }
                    }
                }
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .testTag("streaming_platforms_strip")
        ) {
            if (selectedProvider != null) {
                item(key = "platform_all_reset", contentType = "platform_chip") {
                    GenreFilterChipItem(
                        label = "Tutte",
                        isSelected = false,
                        onClick = { onSelectProvider(null) },
                        modifier = Modifier
                            .onPreviewKeyEvent(handlePlatformVerticalKey)
                            .focusProperties {
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                                upFocusRequester.takeIfAttached()?.let { up = it }
                                downFocusRequester.takeIfAttached()?.let { down = it }
                            }
                            .testTag("platform_card_all")
                    )
                }
            }

            itemsIndexed(
                items = safeProviders,
                key = { _, provider -> "platform_${provider.providerId}" },
                contentType = { _, _ -> "platform_card" }
            ) { index, provider ->
                val isSelected = selectedProvider?.providerId == provider.providerId
                val itemFocusRequester = if (index == firstVisibleProviderIdx && firstItemFocusRequester != null) {
                    firstItemFocusRequester
                } else {
                    remember(provider.providerId) { FocusRequester() }
                }
                StreamingPlatformCard(
                    provider = provider,
                    isSelected = isSelected,
                    onClick = {
                        if (isSelected) {
                            onSelectProvider(null)
                        } else {
                            onSelectProvider(provider)
                        }
                    },
                    onFocused = { onItemFocused?.invoke(itemFocusRequester) },
                    modifier = Modifier
                        .safeFocusRequester(itemFocusRequester)
                        .onPreviewKeyEvent(handlePlatformVerticalKey)
                        .focusProperties {
                            if (index == 0 && selectedProvider == null) {
                                navRailFocusRequester.takeIfAttached()?.let { left = it }
                            }
                            upFocusRequester.takeIfAttached()?.let { up = it }
                            downFocusRequester.takeIfAttached()?.let { down = it }
                        }
                        .testTag("platform_card_${provider.providerId}")
                )
            }
        }
    }
}

@Composable
private fun StreamingPlatformCard(
    provider: TmdbWatchProviderDto,
    isSelected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val brandColor = remember(provider.brandColorHex) { Color(provider.brandColorHex) }

    val borderColor = when {
        isFocused -> NetflixWhite
        isSelected -> brandColor
        else -> NetflixElevatedSurface
    }

    val containerBrush = remember(isSelected, isFocused, brandColor) {
        when {
            isSelected -> Brush.horizontalGradient(
                colors = listOf(
                    brandColor.copy(alpha = 0.34f),
                    NetflixCardSurface
                )
            )
            isFocused -> Brush.horizontalGradient(
                colors = listOf(
                    brandColor.copy(alpha = 0.24f),
                    NetflixElevatedSurface
                )
            )
            else -> Brush.horizontalGradient(
                colors = listOf(
                    NetflixCardSurface,
                    NetflixDarkSurface
                )
            )
        }
    }

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .heightIn(min = 62.dp)
            .tvFocusableControl(
                shape = RoundedCornerShape(14.dp),
                focusedScale = 1.06f,
                focusedBorderColor = NetflixWhite,
                unfocusedBorderColor = borderColor,
                borderWidth = if (isFocused || isSelected) 2.dp else 1.dp,
                onFocusChange = { focused ->
                    isFocused = focused
                    if (focused) onFocused()
                },
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .background(containerBrush, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            PlatformLogoBadge(
                provider = provider,
                size = 44.dp
            )

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetflixWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = NetflixMatchGreen,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = if (isSelected) "Catalogo attivo" else "Catalogo TMDB",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) NetflixMatchGreen else NetflixLightGray,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PlatformLogoBadge(
    provider: TmdbWatchProviderDto,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val brandColor = remember(provider.brandColorHex) { Color(provider.brandColorHex) }
    val shape = RoundedCornerShape(10.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(brandColor)
            .border(1.dp, NetflixWhite.copy(alpha = 0.18f), shape)
    ) {
        Text(
            text = provider.shortBadge,
            fontFamily = BebasNeueFontFamily,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        if (!provider.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = rememberSizedImageRequest(provider.logoUrl, size, size),
                contentDescription = "Logo ${provider.displayName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
            )
        }
    }
}

