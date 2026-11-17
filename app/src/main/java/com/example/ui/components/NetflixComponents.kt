package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.BuildConfig
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbGenreMap
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Logo VixFlix (navigabile con D-pad: Sinistra -> Barra laterale, Giù -> Titoli/Hero)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (brandLogoFocusRequester != null) {
                            Modifier.focusRequester(brandLogoFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .focusProperties {
                        if (navRailFocusRequester != null) {
                            left = navRailFocusRequester
                        }
                        if (resolvedDownTarget != null) {
                            down = resolvedDownTarget
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.DirectionDown &&
                            resolvedDownTarget != null
                        ) {
                            runCatching { resolvedDownTarget.requestFocus() }
                            true
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
                        text = if (isTvLayout) "TV • v${BuildConfig.VERSION_NAME}" else "v${BuildConfig.VERSION_NAME}",
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
                            if (resolvedDownTarget != null) {
                                down = resolvedDownTarget
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.DirectionDown &&
                                resolvedDownTarget != null
                            ) {
                                runCatching { resolvedDownTarget.requestFocus() }
                                true
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
                                if (resolvedDownTarget != null) {
                                    down = resolvedDownTarget
                                }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.DirectionDown &&
                                    resolvedDownTarget != null
                                ) {
                                    runCatching { resolvedDownTarget.requestFocus() }
                                    true
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
                            if (resolvedDownTarget != null) {
                                down = resolvedDownTarget
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.DirectionDown &&
                                resolvedDownTarget != null
                            ) {
                                runCatching { resolvedDownTarget.requestFocus() }
                                true
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

        androidx.compose.animation.AnimatedVisibility(
            visible = isSearchOpen,
            enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
            exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
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
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.tvFocusableControl(shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancella ricerca",
                                tint = NetflixLightGray
                            )
                        }
                    }
                },
                singleLine = true,
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
                    .padding(top = 8.dp)
                    .then(
                        if (searchFocusRequester != null) {
                            Modifier.focusRequester(searchFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .focusProperties {
                        if (navRailFocusRequester != null) {
                            left = navRailFocusRequester
                        }
                        if (downToContentFocusRequester != null) {
                            down = downToContentFocusRequester
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.DirectionDown &&
                            downToContentFocusRequester != null
                        ) {
                            runCatching { downToContentFocusRequester.requestFocus() }
                            true
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
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) NetflixRed else NetflixCardSurface.copy(alpha = 0.85f),
                        animationSpec = tween(220),
                        label = "navPillBg"
                    )
                    val pillBorder by animateColorAsState(
                        targetValue = if (isSelected) NetflixRed else NetflixElevatedSurface,
                        animationSpec = tween(220),
                        label = "navPillBorder"
                    )
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
private fun rememberCrossfadeImageRequest(url: String?): Any? {
    val context = LocalContext.current
    return remember(context, url) {
        if (url.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(280)
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
    isTvLayout: Boolean = false,
    modifier: Modifier = Modifier
) {
    var watchFocused by remember { mutableStateOf(false) }
    var playFocused by remember { mutableStateOf(false) }
    var infoFocused by remember { mutableStateOf(false) }

    val watchFocusRequester = remember { FocusRequester() }
    val resolvedPlayFocusRequester = playButtonFocusRequester ?: remember { FocusRequester() }
    val infoFocusRequester = remember { FocusRequester() }

    val watchScale by animateFloatAsState(
        targetValue = if (watchFocused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "heroWatchScale"
    )
    val playScale by animateFloatAsState(
        targetValue = if (playFocused) 1.07f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "heroPlayScale"
    )
    val infoScale by animateFloatAsState(
        targetValue = if (infoFocused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "heroInfoScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isTvLayout) 410.dp else 500.dp)
            .pointerInput(isTvLayout) {
                if (!isTvLayout) {
                    detectTapGestures { onInfoClick() }
                }
            }
            .testTag("hero_banner_card")
    ) {
        Crossfade(
            targetState = item,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            label = "heroBackdropCrossfade",
            modifier = Modifier.fillMaxSize()
        ) { currentHero ->
            AsyncImage(
                model = rememberCrossfadeImageRequest(currentHero.backdropUrl ?: currentHero.posterUrl),
                contentDescription = currentHero.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

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
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = item,
                transitionSpec = {
                    (fadeIn(tween(320)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 8 })
                        .togetherWith(fadeOut(tween(180)))
                },
                label = "heroTitleCrossfade"
            ) { currentHero ->
                val currentGenres = TmdbGenreMap.namesFor(currentHero.genreIds)
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
                            text = if (currentHero.isSeries) "SERIE TV #1 OGGI" else "FILM #1 OGGI",
                            style = MaterialTheme.typography.labelLarge,
                            color = NetflixWhite
                        )
                        Text(
                            text = "• ${currentHero.matchPercentage}% Compatibile",
                            style = MaterialTheme.typography.labelLarge,
                            color = NetflixMatchGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = currentHero.title.uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = NetflixWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (currentGenres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentGenres.joinToString("  •  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (savedProgress != null && savedProgress.startSeconds > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
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

            Spacer(modifier = Modifier.height(14.dp))

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
                        .graphicsLayer {
                            scaleX = watchScale
                            scaleY = watchScale
                        }
                        .focusRequester(watchFocusRequester)
                        .focusProperties {
                            if (navRailFocusRequester != null) left = navRailFocusRequester
                            right = resolvedPlayFocusRequester
                            if (topBarFocusRequester != null) up = topBarFocusRequester
                            if (firstRowFocusRequester != null) down = firstRowFocusRequester
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
                        .graphicsLayer {
                            scaleX = playScale
                            scaleY = playScale
                        }
                        .focusRequester(resolvedPlayFocusRequester)
                        .focusProperties {
                            left = watchFocusRequester
                            right = infoFocusRequester
                            if (topBarFocusRequester != null) up = topBarFocusRequester
                            if (firstRowFocusRequester != null) down = firstRowFocusRequester
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
                        .graphicsLayer {
                            scaleX = infoScale
                            scaleY = infoScale
                        }
                        .focusRequester(infoFocusRequester)
                        .focusProperties {
                            left = resolvedPlayFocusRequester
                            if (topBarFocusRequester != null) up = topBarFocusRequester
                            if (firstRowFocusRequester != null) down = firstRowFocusRequester
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
    isTvLayout: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    navRailFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("continue_watching_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                text = "Room DB • ${entries.size} in corso",
                style = MaterialTheme.typography.labelSmall,
                color = NetflixLightGray
            )
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(entries, key = { _, it -> it.id }) { index, entry ->
                ContinueWatchingCard(
                    entry = entry,
                    itemIndex = index,
                    listState = listState,
                    isTvLayout = isTvLayout,
                    onResume = { onResumeClick(entry) },
                    onInfo = { onInfoClick(entry) },
                    onRemove = { onRemoveClick(entry) },
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstItemFocusRequester != null) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .focusProperties {
                            if (index == 0 && navRailFocusRequester != null) {
                                left = navRailFocusRequester
                            }
                            if (upFocusRequester != null) {
                                up = upFocusRequester
                            }
                        }
                        .animateItem(
                            fadeInSpec = tween(250),
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            fadeOutSpec = tween(200)
                        )
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
                onFocusedChanged = { isFocused = it },
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
                    model = rememberCrossfadeImageRequest(imageUrl),
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
                val playBorderColor by animateColorAsState(
                    targetValue = if (isFocused) NetflixRed else NetflixWhite,
                    animationSpec = tween(180),
                    label = "cwPlayBorder"
                )
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
                        text = if (entry.remainingMinutes > 0) {
                            "-${entry.remainingMinutes} min • startAt=${entry.startSeconds}s"
                        } else {
                            "startAt=${entry.startSeconds}s"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray,
                        maxLines = 1
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
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    navRailFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = NetflixWhite,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(items, key = { _, item -> "top10_${item.uniqueKey}" }) { index, item ->
                Top10PosterCard(
                    rank = index + 1,
                    itemIndex = index,
                    listState = listState,
                    item = item,
                    onClick = { onItemClick(item) },
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstItemFocusRequester != null) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .focusProperties {
                            if (index == 0 && navRailFocusRequester != null) {
                                left = navRailFocusRequester
                            }
                            if (upFocusRequester != null) {
                                up = upFocusRequester
                            }
                        }
                        .animateItem(
                            fadeInSpec = tween(250),
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            fadeOutSpec = tween(200)
                        )
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
                onFocusedChanged = { isFocused = it },
                onClick = onClick
            )
            .testTag("top10_card_$rank")
    ) {
        // Numero gigante stilizzato Netflix in basso a sinistra
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
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-2).dp, y = 14.dp)
        )

        Text(
            text = rank.toString(),
            style = TextStyle(
                fontFamily = BebasNeueFontFamily,
                fontSize = 132.sp,
                fontWeight = FontWeight.Bold,
                color = NetflixBlack.copy(alpha = 0.85f)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-2).dp, y = 14.dp)
        )

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
                    model = rememberCrossfadeImageRequest(item.posterUrl),
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

                // Sovrimpressione animata con il titolo quando evidenziato con il telecomando
                androidx.compose.animation.AnimatedVisibility(
                    visible = isFocused,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(140)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
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
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    navRailFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = NetflixWhite,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items, key = { _, item -> "${title}_${item.uniqueKey}" }) { index, item ->
                MediaPosterCard(
                    item = item,
                    itemIndex = index,
                    listState = listState,
                    onClick = { onItemClick(item) },
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstItemFocusRequester != null) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .focusProperties {
                            if (index == 0 && navRailFocusRequester != null) {
                                left = navRailFocusRequester
                            }
                            if (upFocusRequester != null) {
                                up = upFocusRequester
                            }
                        }
                        .animateItem(
                            fadeInSpec = tween(250),
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            fadeOutSpec = tween(200)
                        )
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
    listState: LazyListState? = null
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
                onFocusedChanged = { isFocused = it },
                onClick = onClick
            )
            .testTag("poster_card_${item.uniqueKey}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = rememberCrossfadeImageRequest(item.posterUrl),
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

            // Overlay titolo + anno animato quando la locandina è selezionata/evidenziata col telecomando TV
            androidx.compose.animation.AnimatedVisibility(
                visible = isFocused,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
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
