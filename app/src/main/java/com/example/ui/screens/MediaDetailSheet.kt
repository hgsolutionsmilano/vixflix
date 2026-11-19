package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbCastMemberDto
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbVideoDto
import com.example.data.remote.YouTubeTrailerCandidate
import com.example.ui.DetailUiState
import com.example.ui.components.MediaCatalogRow
import com.example.ui.components.animatedPosterShowcase
import com.example.ui.components.formatSecondsToMmSs
import com.example.ui.components.rememberIsTvDevice
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.tvFocusableControl
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

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

@Composable
fun MediaDetailScreen(
    state: DetailUiState,
    savedProgress: WatchProgressEntity?,
    isInWatchlist: Boolean,
    bufferPreset: StreamingBufferPreset = StreamingBufferPreset.DEFAULT,
    isInteractive: Boolean = true,
    detailStackDepth: Int = 1,
    previousDetailTitle: String? = null,
    onBack: () -> Unit = {},
    onClose: () -> Unit,
    onPlayMain: (restartFromZero: Boolean) -> Unit,
    onPlayEpisode: (season: Int, episode: Int, title: String?) -> Unit,
    onOpenYouTubeTrailerPopup: () -> Unit,
    onSelectYouTubeTrailer: (YouTubeTrailerCandidate) -> Unit,
    onRetryYouTubeTrailer: () -> Unit,
    onCloseTrailer: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onToggleWatchlist: () -> Unit,
    onSelectSimilar: (MediaItem) -> Unit
) {
    val backAction = if (detailStackDepth > 1) onBack else onClose

    BackHandler(enabled = isInteractive) {
        if (state.activeTrailer != null) {
            onCloseTrailer()
        } else {
            backAction()
        }
    }

    val item = state.mediaItem
    val baseIsTvDevice = rememberIsTvDevice()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("media_detail_screen")
    ) {
        val isTvDevice = baseIsTvDevice || maxWidth >= 600.dp || maxWidth > maxHeight
        // Su TV Mode e Tablet Orizzontale il palco Hero occupa esattamente il 100% dell'altezza reale del viewport,
        // così non eccede mai l'altezza di LazyColumn e non innesca micro-scroll BringIntoView
        // quando ci si sposta tra le icone pulsanti.
        val tvHeroStageHeight = maxHeight
        val density = LocalDensity.current
        val revealBelowFoldOffsetPx = remember(tvHeroStageHeight, density) {
            with(density) { (tvHeroStageHeight * 0.48f).roundToPx().coerceAtLeast(0) }
        }
        val scrollStepPx = remember(density) {
            with(density) { 220.dp.roundToPx().coerceAtLeast(1) }
        }

        AnimatedContent(
            targetState = state,
            contentKey = { it.mediaItem.uniqueKey },
            transitionSpec = {
                fadeIn(tween(130)).togetherWith(fadeOut(tween(90)))
            },
            label = "detailItemTransition",
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = isInteractive && state.activeTrailer == null }
        ) { currentState ->
            val currentItem = currentState.mediaItem
            val details = currentState.details
            val trailers = remember(details?.trailers) {
                details?.trailers.orEmpty().distinctBy { it.id.ifBlank { it.key } }
            }
            val castList = remember(details?.cast) {
                details?.cast.orEmpty().distinctBy { it.id }
            }
            val episodesList = remember(currentState.episodes) {
                currentState.episodes.distinctBy { it.seasonNumber to it.episodeNumber }
            }
            val similarList = remember(details?.similar) {
                details?.similar.orEmpty().distinctBy { it.uniqueKey }
            }
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            val detailPlayFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val detailTrailerFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val detailWatchlistFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val detailRestartFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val detailCloseFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val detailBackFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val firstTrailerFocus = remember(currentItem.uniqueKey) { FocusRequester() }
            val firstCastFocus = remember(currentItem.uniqueKey) { FocusRequester() }
            val firstSeasonFocus = remember(currentItem.uniqueKey) { FocusRequester() }
            val firstEpisodeFocus = remember(currentItem.uniqueKey) { FocusRequester() }
            val lastEpisodeFocus = remember(currentItem.uniqueKey) { FocusRequester() }
            val firstSimilarFocus = remember(currentItem.uniqueKey) { FocusRequester() }

            // Tracciamento focus non-reattivo per evitare ricomposizioni dell'intera scheda
            // o snap-back anomali ad ogni pressione D-Pad sui bottoni.
            val focusTracker = remember(currentItem.uniqueKey) {
                object {
                    var playFocused: Boolean = false
                    var trailerFocused: Boolean = false
                    var userMovedFocus: Boolean = false
                    var wasTrailerOpen: Boolean = false
                }
            }

            val isCurrentActive = currentItem.uniqueKey == item.uniqueKey
            val isTrailerOpen = currentState.activeTrailer != null

            LaunchedEffect(isCurrentActive, isInteractive, isTrailerOpen) {
                if (isCurrentActive && isInteractive && !isTrailerOpen) {
                    val returnToTrailer = focusTracker.wasTrailerOpen
                    val targetRequester = if (returnToTrailer) detailTrailerFocusRequester else detailPlayFocusRequester
                    if (!returnToTrailer) {
                        focusTracker.userMovedFocus = false
                        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                            runCatching { listState.scrollToItem(0, 0) }
                        }
                    }
                    androidx.compose.runtime.withFrameNanos { }
                    val retryDelaysMs = longArrayOf(0L, 40L, 90L, 160L, 280L)
                    for (stepDelay in retryDelaysMs) {
                        if (stepDelay > 0L) {
                            kotlinx.coroutines.delay(stepDelay)
                        }
                        if (focusTracker.userMovedFocus) break
                        val targetAlreadyFocused = if (returnToTrailer) {
                            focusTracker.trailerFocused
                        } else {
                            focusTracker.playFocused
                        }
                        if (targetAlreadyFocused) break
                        val requested = targetRequester.safeRequestFocus()
                        if (requested) {
                            kotlinx.coroutines.yield()
                            val focusedAfterYield = if (returnToTrailer) {
                                focusTracker.trailerFocused
                            } else {
                                focusTracker.playFocused
                            }
                            if (focusedAfterYield || focusTracker.userMovedFocus) break
                        }
                    }
                }
                if (isCurrentActive) {
                    focusTracker.wasTrailerOpen = isTrailerOpen
                }
            }

            val downTargetFromActions: FocusRequester? = when {
                trailers.isNotEmpty() -> firstTrailerFocus
                castList.isNotEmpty() -> firstCastFocus
                currentItem.isSeries && !details?.seasons.isNullOrEmpty() -> firstSeasonFocus
                currentItem.isSeries && episodesList.isNotEmpty() -> firstEpisodeFocus
                similarList.isNotEmpty() -> firstSimilarFocus
                else -> null
            }

            val hasBelowFoldContent = trailers.isNotEmpty() ||
                castList.isNotEmpty() ||
                (currentItem.isSeries && (!details?.seasons.isNullOrEmpty() || episodesList.isNotEmpty())) ||
                similarList.isNotEmpty()

            val returnToHeroStage: () -> Boolean = {
                focusTracker.userMovedFocus = true
                if (isTvDevice) {
                    coroutineScope.launch {
                        runCatching { listState.scrollToItem(0, 0) }
                        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
                        detailPlayFocusRequester.safeRequestFocus()
                    }
                    true
                } else {
                    detailPlayFocusRequester.safeRequestFocus()
                }
            }

            val navigateBelowFold: (FocusRequester?, Boolean) -> Boolean = { targetRequester, isDown ->
                if (targetRequester == null) {
                    false
                } else {
                    focusTracker.userMovedFocus = true
                    if (targetRequester.safeRequestFocus()) {
                        true
                    } else {
                        coroutineScope.launch {
                            val delta = if (isDown) scrollStepPx.toFloat() else -scrollStepPx.toFloat()
                            for (step in 1..4) {
                                runCatching { listState.scrollBy(delta) }
                                repeat(2) { androidx.compose.runtime.withFrameNanos { } }
                                if (targetRequester.safeRequestFocus()) break
                            }
                        }
                        true
                    }
                }
            }

            val handleActionDownKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { keyEvent ->
                if (isTvDevice &&
                    keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown &&
                    hasBelowFoldContent
                ) {
                    focusTracker.userMovedFocus = true
                    coroutineScope.launch {
                        runCatching {
                            if (listState.firstVisibleItemIndex == 0 &&
                                listState.firstVisibleItemScrollOffset < revealBelowFoldOffsetPx
                            ) {
                                listState.scrollToItem(0, revealBelowFoldOffsetPx)
                            }
                        }
                        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
                        val focusedOnFirstTry = downTargetFromActions?.safeRequestFocus() == true
                        if (!focusedOnFirstTry) {
                            runCatching { listState.scrollToItem(1, 0) }
                            repeat(2) { androidx.compose.runtime.withFrameNanos { } }
                            downTargetFromActions?.safeRequestFocus()
                        }
                    }
                    true
                } else {
                    false
                }
            }

            val topReturnTarget = if (detailStackDepth > 1) detailBackFocusRequester else detailCloseFocusRequester
            val hasResumeProgress = savedProgress != null && savedProgress.startSeconds > 0L
            val playActionDesc = if (hasResumeProgress) {
                val epPrefix = if (savedProgress?.seasonNumber != null && savedProgress.episodeNumber != null) {
                    "S${savedProgress.seasonNumber}:E${savedProgress.episodeNumber} "
                } else ""
                "Riprendi ${epPrefix}da ${savedProgress?.startMinutes} min"
            } else if (currentItem.isSeries) {
                "Riproduci S1:E1"
            } else {
                "Riproduci Ora"
            }

            val watchlistBg = if (isInWatchlist) NetflixMatchGreen.copy(alpha = 0.22f) else NetflixCardSurface
            val watchlistBorder = if (isInWatchlist) NetflixMatchGreen else NetflixElevatedSurface
            val watchlistTint = if (isInWatchlist) NetflixMatchGreen else NetflixWhite

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isTvDevice) Modifier else Modifier.navigationBarsPadding())
                        .testTag("media_detail_lazy_column"),
                    contentPadding = if (isTvDevice) PaddingValues(0.dp) else PaddingValues(bottom = 40.dp)
                ) {
                    if (isTvDevice) {
                        // =========================================================================
                        // TV MODE (Android TV, Google TV, Fire TV Stick):
                        // Appena apri mostra perfettamente centrati al centro dello schermo
                        // Locandina 2:3, Breve Testo (max 3 righe) e Bottoni Rapidi.
                        // Non si muove spostandosi tra le icone pulsanti; scorre sotto SOLO con D-Pad Giù.
                        // =========================================================================
                        item(
                            key = "detail_tv_hero_stage_${currentItem.uniqueKey}",
                            contentType = "detail_tv_hero_stage"
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(tvHeroStageHeight)
                                    .testTag("detail_tv_hero_stage")
                            ) {
                                // Sfondo cinematografico ad alto contrasto
                                AsyncImage(
                                    model = rememberSizedDetailImageRequest(
                                        url = currentItem.backdropUrl ?: currentItem.posterUrl,
                                        widthDp = 960.dp,
                                        heightDp = tvHeroStageHeight
                                    ),
                                    contentDescription = currentItem.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Doppia vignettatura (orizzontale + verticale) per leggibilità ottimale a 3 metri
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                0f to NetflixBlack.copy(alpha = 0.94f),
                                                0.45f to NetflixBlack.copy(alpha = 0.80f),
                                                1f to NetflixBlack.copy(alpha = 0.45f)
                                            )
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                0f to NetflixBlack.copy(alpha = 0.65f),
                                                0.35f to Color.Transparent,
                                                0.82f to NetflixBlack.copy(alpha = 0.88f),
                                                1f to NetflixBlack
                                            )
                                        )
                                )

                                // Contenuto perfettamente centrato: Locandina a sinistra + Info brevi e Bottoni rapidi a destra
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth()
                                        .padding(horizontal = 44.dp),
                                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Locandina verticale 2:3 ben visibile e centrata
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                                        border = BorderStroke(1.5.dp, NetflixWhite.copy(alpha = 0.24f)),
                                        modifier = Modifier
                                            .width(180.dp)
                                            .height(270.dp)
                                            .shadow(16.dp, RoundedCornerShape(14.dp))
                                            .testTag("detail_tv_poster")
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = rememberSizedDetailImageRequest(
                                                    url = currentItem.posterUrl ?: currentItem.backdropUrl,
                                                    widthDp = 180.dp,
                                                    heightDp = 270.dp
                                                ),
                                                contentDescription = "Locandina ${currentItem.title}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Surface(
                                                color = NetflixBlack.copy(alpha = 0.78f),
                                                shape = RoundedCornerShape(bottomEnd = 10.dp),
                                                modifier = Modifier.align(Alignment.TopStart)
                                            ) {
                                                Text(
                                                    text = if (currentItem.isSeries) "SERIE TV" else "FILM",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NetflixWhite,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Colonna destra: Metadati compatti, Titolo, Breve Testo (max 3 righe) e Bottoni Rapidi
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = NetflixRed,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "VIXSRC STREAM",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = "${currentItem.matchPercentage}% Compatibile",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = NetflixMatchGreen
                                            )
                                            if (currentItem.releaseYear.isNotBlank()) {
                                                Text(
                                                    text = currentItem.releaseYear,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = NetflixLightGray
                                                )
                                            }
                                            Surface(
                                                color = NetflixElevatedSurface,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "FHD • HLS",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = NetflixWhite,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (details?.runtimeMinutes != null && details.runtimeMinutes > 0) {
                                                Text(
                                                    text = "${details.runtimeMinutes} min",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = NetflixLightGray
                                                )
                                            } else if (details != null && details.numberOfSeasons > 0) {
                                                Text(
                                                    text = "${details.numberOfSeasons} Stagioni",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = NetflixLightGray
                                                )
                                            }
                                            if (!details?.genres.isNullOrEmpty()) {
                                                Text(
                                                    text = "• ${details?.genres?.take(3)?.joinToString(", ")}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = NetflixLightGray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = currentItem.title,
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = NetflixWhite,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (!details?.tagline.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "\"${details?.tagline}\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NetflixLightGray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Breve testo (sinossi sintetica max 3 righe)
                                        if (currentItem.overview.isNotBlank()) {
                                            Text(
                                                text = currentItem.overview,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NetflixWhite.copy(alpha = 0.92f),
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("detail_short_overview")
                                            )
                                        }

                                        // Indicatore compatto di ripresa se salvato in Continua a guardare
                                        if (savedProgress != null && savedProgress.startSeconds > 0L) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.85f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(NetflixCardSurface.copy(alpha = 0.9f))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                val epBadge = if (savedProgress.seasonNumber != null && savedProgress.episodeNumber != null) {
                                                    "S${savedProgress.seasonNumber}:E${savedProgress.episodeNumber} • "
                                                } else ""
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${epBadge}Riprendi da ${formatSecondsToMmSs(savedProgress.startSeconds)}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = NetflixWhite
                                                    )
                                                    Text(
                                                        text = "${savedProgress.startMinutes} min",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = NetflixRed
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { savedProgress.progressFraction },
                                                    color = NetflixRed,
                                                    trackColor = NetflixElevatedSurface,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Bottoni Rapidi (navigazione D-Pad Sinistra/Destra bloccata nella riga senza
                                        // alcun movimento verticale; D-Pad Giù scorre alle sezioni sottostanti)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusGroup(),
                                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            DetailRoundActionButton(
                                                icon = Icons.Default.PlayArrow,
                                                label = if (hasResumeProgress) "Riprendi" else "Riproduci",
                                                contentDescription = playActionDesc,
                                                containerColor = NetflixWhite,
                                                contentColor = NetflixBlack,
                                                focusedBorderColor = NetflixRed,
                                                focusRequester = detailPlayFocusRequester,
                                                upFocusRequester = topReturnTarget,
                                                downFocusRequester = downTargetFromActions,
                                                leftFocusRequester = null,
                                                rightFocusRequester = detailTrailerFocusRequester,
                                                lockHorizontalRow = true,
                                                onDownKey = handleActionDownKey,
                                                onFocusChange = { focused ->
                                                    focusTracker.playFocused = focused
                                                },
                                                testTag = "detail_play_button",
                                                onClick = { onPlayMain(false) }
                                            )

                                            DetailRoundActionButton(
                                                icon = Icons.Default.SmartDisplay,
                                                label = "Trailer",
                                                contentDescription = "Guarda Trailer su YouTube",
                                                containerColor = NetflixRed,
                                                contentColor = Color.White,
                                                focusedBorderColor = NetflixWhite,
                                                focusRequester = detailTrailerFocusRequester,
                                                upFocusRequester = topReturnTarget,
                                                downFocusRequester = downTargetFromActions,
                                                leftFocusRequester = detailPlayFocusRequester,
                                                rightFocusRequester = detailWatchlistFocusRequester,
                                                lockHorizontalRow = true,
                                                onDownKey = handleActionDownKey,
                                                onFocusChange = { focused ->
                                                    focusTracker.trailerFocused = focused
                                                    if (focused) focusTracker.userMovedFocus = true
                                                },
                                                testTag = "detail_watch_trailer_button",
                                                onClick = onOpenYouTubeTrailerPopup
                                            )

                                            DetailRoundActionButton(
                                                icon = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                                                label = if (isInWatchlist) "Nella lista" else "La mia lista",
                                                contentDescription = if (isInWatchlist) "Rimuovi dalla Mia Lista" else "Aggiungi alla Lista",
                                                containerColor = watchlistBg,
                                                contentColor = watchlistTint,
                                                borderColor = watchlistBorder,
                                                focusedBorderColor = NetflixWhite,
                                                focusRequester = detailWatchlistFocusRequester,
                                                upFocusRequester = topReturnTarget,
                                                downFocusRequester = downTargetFromActions,
                                                leftFocusRequester = detailTrailerFocusRequester,
                                                rightFocusRequester = if (hasResumeProgress) detailRestartFocusRequester else null,
                                                lockHorizontalRow = true,
                                                onDownKey = handleActionDownKey,
                                                onFocusChange = { focused ->
                                                    if (focused) focusTracker.userMovedFocus = true
                                                },
                                                testTag = "detail_watchlist_button",
                                                onClick = onToggleWatchlist
                                            )

                                            if (hasResumeProgress) {
                                                DetailRoundActionButton(
                                                    icon = Icons.Default.Replay,
                                                    label = "Dall'inizio",
                                                    contentDescription = "Riproduci dall'inizio",
                                                    containerColor = NetflixCardSurface,
                                                    contentColor = NetflixWhite,
                                                    borderColor = NetflixElevatedSurface,
                                                    focusedBorderColor = NetflixWhite,
                                                    focusRequester = detailRestartFocusRequester,
                                                    upFocusRequester = topReturnTarget,
                                                    downFocusRequester = downTargetFromActions,
                                                    leftFocusRequester = detailWatchlistFocusRequester,
                                                    rightFocusRequester = null,
                                                    lockHorizontalRow = true,
                                                    onDownKey = handleActionDownKey,
                                                    onFocusChange = { focused ->
                                                        if (focused) focusTracker.userMovedFocus = true
                                                    },
                                                    testTag = "detail_restart_button",
                                                    onClick = { onPlayMain(true) }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Indicatore inferiore per far capire che premendo GIÙ sul D-Pad si accede al resto
                                if (hasBelowFoldContent) {
                                    Surface(
                                        color = NetflixBlack.copy(alpha = 0.72f),
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, NetflixElevatedSurface.copy(alpha = 0.7f)),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 12.dp)
                                            .testTag("detail_dpad_down_hint")
                                    ) {
                                        Text(
                                            text = if (currentItem.isSeries) {
                                                "Premi ▼ GIÙ sul telecomando per Episodi, Trailer, Cast e Titoli Simili"
                                            } else {
                                                "Premi ▼ GIÙ sul telecomando per Trailer, Cast e Titoli Simili"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NetflixLightGray,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // =========================================================================
                        // LAYOUT TOUCH / SMARTPHONE
                        // =========================================================================
                        item(key = "detail_backdrop_${currentItem.uniqueKey}", contentType = "detail_backdrop") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                            ) {
                                AsyncImage(
                                    model = rememberSizedDetailImageRequest(
                                        url = currentItem.backdropUrl ?: currentItem.posterUrl,
                                        widthDp = 640.dp,
                                        heightDp = 280.dp
                                    ),
                                    contentDescription = currentItem.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                0f to NetflixBlack.copy(alpha = 0.5f),
                                                0.5f to Color.Transparent,
                                                1f to NetflixBlack
                                            )
                                        )
                                )

                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .align(Alignment.Center)
                                        .focusProperties { canFocus = false }
                                        .clip(CircleShape)
                                        .background(NetflixRed.copy(alpha = 0.9f))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onPlayMain(false) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Avvia Streaming",
                                        tint = Color.White,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }

                        item(key = "detail_main_info_${currentItem.uniqueKey}", contentType = "detail_info") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = NetflixRed,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "VIXSRC STREAM",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "${currentItem.matchPercentage}% Compatibile",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NetflixMatchGreen
                                    )
                                    if (currentItem.releaseYear.isNotBlank()) {
                                        Text(
                                            text = currentItem.releaseYear,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NetflixLightGray
                                        )
                                    }
                                    Surface(
                                        color = NetflixElevatedSurface,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "FHD • HLS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NetflixWhite,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (details?.runtimeMinutes != null && details.runtimeMinutes > 0) {
                                        Text(
                                            text = "${details.runtimeMinutes} min",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NetflixLightGray
                                        )
                                    } else if (details != null && details.numberOfSeasons > 0) {
                                        Text(
                                            text = "${details.numberOfSeasons} Stagioni",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NetflixLightGray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = currentItem.title,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = NetflixWhite
                                )

                                if (!details?.tagline.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"${details?.tagline}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NetflixLightGray
                                    )
                                }

                                if (savedProgress != null && savedProgress.startSeconds > 0L) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NetflixCardSurface)
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val epBadge = if (savedProgress.seasonNumber != null && savedProgress.episodeNumber != null) {
                                                "S${savedProgress.seasonNumber}:E${savedProgress.episodeNumber} • "
                                            } else ""
                                            Text(
                                                text = "${epBadge}Salvato su Room al minuto ${savedProgress.startMinutes} (${formatSecondsToMmSs(savedProgress.startSeconds)})",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = NetflixWhite
                                            )
                                            Text(
                                                text = "startAt=${savedProgress.startSeconds}s",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NetflixRed
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { savedProgress.progressFraction },
                                            color = NetflixRed,
                                            trackColor = NetflixElevatedSurface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(if (hasResumeProgress) 18.dp else 24.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    DetailRoundActionButton(
                                        icon = Icons.Default.PlayArrow,
                                        label = if (hasResumeProgress) "Riprendi" else "Riproduci",
                                        contentDescription = playActionDesc,
                                        containerColor = NetflixWhite,
                                        contentColor = NetflixBlack,
                                        focusedBorderColor = NetflixRed,
                                        focusRequester = detailPlayFocusRequester,
                                        upFocusRequester = topReturnTarget,
                                        downFocusRequester = downTargetFromActions,
                                        rightFocusRequester = detailTrailerFocusRequester,
                                        onFocusChange = { focused -> focusTracker.playFocused = focused },
                                        testTag = "detail_play_button",
                                        onClick = { onPlayMain(false) }
                                    )

                                    DetailRoundActionButton(
                                        icon = Icons.Default.SmartDisplay,
                                        label = "Trailer",
                                        contentDescription = "Guarda Trailer su YouTube",
                                        containerColor = NetflixRed,
                                        contentColor = Color.White,
                                        focusedBorderColor = NetflixWhite,
                                        focusRequester = detailTrailerFocusRequester,
                                        upFocusRequester = topReturnTarget,
                                        downFocusRequester = downTargetFromActions,
                                        leftFocusRequester = detailPlayFocusRequester,
                                        rightFocusRequester = detailWatchlistFocusRequester,
                                        onFocusChange = { focused ->
                                            focusTracker.trailerFocused = focused
                                            if (focused) focusTracker.userMovedFocus = true
                                        },
                                        testTag = "detail_watch_trailer_button",
                                        onClick = onOpenYouTubeTrailerPopup
                                    )

                                    DetailRoundActionButton(
                                        icon = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                                        label = if (isInWatchlist) "Nella lista" else "La mia lista",
                                        contentDescription = if (isInWatchlist) "Rimuovi dalla Mia Lista" else "Aggiungi alla Lista",
                                        containerColor = watchlistBg,
                                        contentColor = watchlistTint,
                                        borderColor = watchlistBorder,
                                        focusedBorderColor = NetflixWhite,
                                        focusRequester = detailWatchlistFocusRequester,
                                        upFocusRequester = topReturnTarget,
                                        downFocusRequester = downTargetFromActions,
                                        leftFocusRequester = detailTrailerFocusRequester,
                                        rightFocusRequester = if (hasResumeProgress) detailRestartFocusRequester else null,
                                        onFocusChange = { focused ->
                                            if (focused) focusTracker.userMovedFocus = true
                                        },
                                        testTag = "detail_watchlist_button",
                                        onClick = onToggleWatchlist
                                    )

                                    if (hasResumeProgress) {
                                        DetailRoundActionButton(
                                            icon = Icons.Default.Replay,
                                            label = "Dall'inizio",
                                            contentDescription = "Riproduci dall'inizio",
                                            containerColor = NetflixCardSurface,
                                            contentColor = NetflixWhite,
                                            borderColor = NetflixElevatedSurface,
                                            focusedBorderColor = NetflixWhite,
                                            focusRequester = detailRestartFocusRequester,
                                            upFocusRequester = topReturnTarget,
                                            downFocusRequester = downTargetFromActions,
                                            leftFocusRequester = detailWatchlistFocusRequester,
                                            onFocusChange = { focused ->
                                                if (focused) focusTracker.userMovedFocus = true
                                            },
                                            testTag = "detail_restart_button",
                                            onClick = { onPlayMain(true) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                if (currentItem.overview.isNotBlank()) {
                                    Text(
                                        text = currentItem.overview,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = NetflixWhite
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                if (!details?.genres.isNullOrEmpty()) {
                                    Text(
                                        text = "Generi: ${details?.genres?.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NetflixLightGray
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "TMDB ID: ${currentItem.id} • Endpoint VixSrc: https://vixsrc.to/${currentItem.mediaType}/${currentItem.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixMutedGray
                                )
                            }
                        }
                    }

                    // SEZIONE DEDICATA: Trailer & Video Ufficiali da TMDB (/movie/{id}/videos o /tv/{id}/videos)
                    if (trailers.isNotEmpty()) {
                        val downFromTrailers = when {
                            castList.isNotEmpty() -> firstCastFocus
                            currentItem.isSeries && !details?.seasons.isNullOrEmpty() -> firstSeasonFocus
                            currentItem.isSeries && episodesList.isNotEmpty() -> firstEpisodeFocus
                            similarList.isNotEmpty() -> firstSimilarFocus
                            else -> null
                        }
                        item(key = "detail_trailers_section", contentType = "detail_trailers") {
                            Spacer(modifier = Modifier.height(20.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tmdb_trailers_section")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = NetflixRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Trailer & Anteprime Ufficiali (TMDB)",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = NetflixWhite
                                        )
                                    }
                                    Text(
                                        text = "${trailers.size} video",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NetflixMatchGreen
                                    )
                                }

                                Text(
                                    text = "Seleziona un trailer per guardarlo direttamente in-app prima di avviare lo streaming completo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NetflixLightGray,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val trailersListState = rememberLazyListState()
                                val firstVisibleTrailerIdx = trailersListState.firstVisibleItemIndex.coerceIn(0, trailers.lastIndex)
                                LazyRow(
                                    state = trailersListState,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    itemsIndexed(
                                        items = trailers,
                                        key = { _, it -> it.id.ifBlank { it.key } },
                                        contentType = { _, _ -> "detail_trailer_card" }
                                    ) { index, video ->
                                        TmdbTrailerCard(
                                            video = video,
                                            fallbackBackdropUrl = currentItem.backdropUrl,
                                            itemIndex = index,
                                            listState = trailersListState,
                                            onClick = onOpenYouTubeTrailerPopup,
                                            modifier = Modifier
                                                .then(
                                                    if (index == firstVisibleTrailerIdx) Modifier.safeFocusRequester(firstTrailerFocus) else Modifier
                                                )
                                                .onPreviewKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        when (keyEvent.key) {
                                                            Key.DirectionUp -> returnToHeroStage()
                                                            Key.DirectionDown -> navigateBelowFold(downFromTrailers, true)
                                                            else -> false
                                                        }
                                                    } else {
                                                        false
                                                    }
                                                }
                                                .focusProperties {
                                                    detailPlayFocusRequester.takeIfAttached()?.let { up = it }
                                                    downFromTrailers?.takeIfAttached()?.let { down = it }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Cast TMDB
                    if (castList.isNotEmpty()) {
                        val upFromCast = if (trailers.isNotEmpty()) firstTrailerFocus else null
                        val downFromCast = when {
                            currentItem.isSeries && !details?.seasons.isNullOrEmpty() -> firstSeasonFocus
                            currentItem.isSeries && episodesList.isNotEmpty() -> firstEpisodeFocus
                            similarList.isNotEmpty() -> firstSimilarFocus
                            else -> null
                        }
                        item(key = "detail_cast_section", contentType = "detail_cast") {
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Cast Principale",
                                style = MaterialTheme.typography.titleMedium,
                                color = NetflixWhite,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            val castListState = rememberLazyListState()
                            val firstVisibleCastIdx = castListState.firstVisibleItemIndex.coerceIn(0, castList.lastIndex)
                            LazyRow(
                                state = castListState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    items = castList,
                                    key = { _, actor -> actor.id },
                                    contentType = { _, _ -> "detail_cast_card" }
                                ) { index, actor ->
                                    CastCard(
                                        actor = actor,
                                        modifier = Modifier
                                            .then(
                                                if (index == firstVisibleCastIdx) Modifier.safeFocusRequester(firstCastFocus) else Modifier
                                            )
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.key) {
                                                        Key.DirectionUp -> {
                                                            if (upFromCast != null) {
                                                                navigateBelowFold(upFromCast, false)
                                                            } else {
                                                                returnToHeroStage()
                                                            }
                                                        }
                                                        Key.DirectionDown -> navigateBelowFold(downFromCast, true)
                                                        else -> false
                                                    }
                                                } else {
                                                    false
                                                }
                                            }
                                            .focusProperties {
                                                (upFromCast ?: detailPlayFocusRequester).takeIfAttached()?.let { up = it }
                                                downFromCast?.takeIfAttached()?.let { down = it }
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Selettore Stagioni ed Episodi per Serie TV
                    if (currentItem.isSeries) {
                        val upFromSeasons = when {
                            castList.isNotEmpty() -> firstCastFocus
                            trailers.isNotEmpty() -> firstTrailerFocus
                            else -> null
                        }
                        val downFromSeasons = when {
                            episodesList.isNotEmpty() -> firstEpisodeFocus
                            similarList.isNotEmpty() -> firstSimilarFocus
                            else -> null
                        }
                        item(key = "detail_seasons_header", contentType = "detail_seasons") {
                            Spacer(modifier = Modifier.height(22.dp))
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = "Episodi (Streaming diretto VixSrc)",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NetflixWhite
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                val seasons = details?.seasons.orEmpty()
                                if (seasons.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .focusGroup(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        seasons.forEachIndexed { sIndex, season ->
                                            val isSelected = currentState.selectedSeason == season.seasonNumber
                                            val seasonBg = if (isSelected) NetflixRed else NetflixCardSurface
                                            val seasonBorder = if (isSelected) NetflixRed else NetflixElevatedSurface
                                            Surface(
                                                color = seasonBg,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, seasonBorder),
                                                modifier = Modifier
                                                    .then(
                                                        if (sIndex == 0) Modifier.safeFocusRequester(firstSeasonFocus) else Modifier
                                                    )
                                                    .onPreviewKeyEvent { keyEvent ->
                                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                                            when (keyEvent.key) {
                                                                Key.DirectionUp -> {
                                                                    if (upFromSeasons != null) {
                                                                        navigateBelowFold(upFromSeasons, false)
                                                                    } else {
                                                                        returnToHeroStage()
                                                                    }
                                                                }
                                                                Key.DirectionDown -> navigateBelowFold(downFromSeasons, true)
                                                                else -> false
                                                            }
                                                        } else {
                                                            false
                                                        }
                                                    }
                                                    .focusProperties {
                                                        (upFromSeasons ?: detailPlayFocusRequester).takeIfAttached()?.let { up = it }
                                                        downFromSeasons?.takeIfAttached()?.let { down = it }
                                                    }
                                                    .tvFocusableControl(
                                                        shape = RoundedCornerShape(8.dp),
                                                        focusedScale = 1.06f,
                                                        onFocusChange = { focused ->
                                                            if (focused) focusTracker.userMovedFocus = true
                                                        },
                                                        onClick = { onSelectSeason(season.seasonNumber) }
                                                    )
                                            ) {
                                                Text(
                                                    text = "Stagione ${season.seasonNumber} (${season.episodeCount})",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = NetflixWhite,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (currentState.isLoadingEpisodes) {
                            item(key = "detail_episodes_loading", contentType = "detail_status") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = NetflixRed)
                                }
                            }
                        } else {
                            itemsIndexed(
                                episodesList,
                                key = { _, ep -> "ep_${ep.seasonNumber}_${ep.episodeNumber}" },
                                contentType = { _, _ -> "detail_episode_row" }
                            ) { epIndex, ep ->
                                val isFirstEp = epIndex == 0
                                val isLastEp = epIndex == episodesList.lastIndex
                                val upFromFirstEp = when {
                                    !details?.seasons.isNullOrEmpty() -> firstSeasonFocus
                                    castList.isNotEmpty() -> firstCastFocus
                                    trailers.isNotEmpty() -> firstTrailerFocus
                                    else -> null
                                }
                                EpisodeRowItem(
                                    episode = ep,
                                    savedEpProgress = currentState.episodeProgressMap[ep.episodeNumber],
                                    fallbackBackdropUrl = currentItem.backdropUrl,
                                    onPlayEpisode = {
                                        onPlayEpisode(ep.seasonNumber, ep.episodeNumber, ep.name)
                                    },
                                    modifier = Modifier
                                        .then(
                                            if (isFirstEp) Modifier.safeFocusRequester(firstEpisodeFocus) else Modifier
                                        )
                                        .then(
                                            if (isLastEp) Modifier.safeFocusRequester(lastEpisodeFocus) else Modifier
                                        )
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when {
                                                    isFirstEp && keyEvent.key == Key.DirectionUp -> {
                                                        if (upFromFirstEp != null) {
                                                            navigateBelowFold(upFromFirstEp, false)
                                                        } else {
                                                            returnToHeroStage()
                                                        }
                                                    }
                                                    isLastEp && keyEvent.key == Key.DirectionDown && similarList.isNotEmpty() -> {
                                                        navigateBelowFold(firstSimilarFocus, true)
                                                    }
                                                    else -> false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                        .focusProperties {
                                            if (isFirstEp) {
                                                (upFromFirstEp ?: detailPlayFocusRequester).takeIfAttached()?.let { up = it }
                                            }
                                            if (isLastEp && similarList.isNotEmpty()) {
                                                firstSimilarFocus.takeIfAttached()?.let { down = it }
                                            }
                                        }
                                )
                            }
                        }
                    }

                    // Contenuti simili da TMDB
                    if (similarList.isNotEmpty()) {
                        val upToBeforeSimilar = when {
                            currentItem.isSeries && !currentState.isLoadingEpisodes && episodesList.isNotEmpty() -> lastEpisodeFocus
                            currentItem.isSeries && !details?.seasons.isNullOrEmpty() -> firstSeasonFocus
                            castList.isNotEmpty() -> firstCastFocus
                            trailers.isNotEmpty() -> firstTrailerFocus
                            else -> null
                        }
                        item(key = "detail_similar_section", contentType = "detail_similar") {
                            Spacer(modifier = Modifier.height(20.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                            if (upToBeforeSimilar != null) {
                                                navigateBelowFold(upToBeforeSimilar, false)
                                            } else {
                                                returnToHeroStage()
                                            }
                                        } else {
                                            false
                                        }
                                    }
                            ) {
                                MediaCatalogRow(
                                    title = "Titoli Simili su TMDB",
                                    items = similarList,
                                    onItemClick = onSelectSimilar,
                                    onItemQuickPlay = { sim -> onSelectSimilar(sim) },
                                    firstItemFocusRequester = firstSimilarFocus,
                                    upFocusRequester = upToBeforeSimilar ?: detailPlayFocusRequester
                                )
                            }
                        }
                    }

                    if (isTvDevice) {
                        item(key = "detail_tv_bottom_spacer", contentType = "detail_spacer") {
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }

                // Barra superiore fissa: Torna al titolo precedente (se stack > 1) e Chiudi scheda
                Row(
                    horizontalArrangement = if (detailStackDepth > 1) Arrangement.SpaceBetween else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .focusGroup()
                ) {
                    if (detailStackDepth > 1) {
                        Surface(
                            color = NetflixBlack.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, NetflixElevatedSurface),
                            modifier = Modifier
                                .safeFocusRequester(detailBackFocusRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                        returnToHeroStage()
                                    } else {
                                        false
                                    }
                                }
                                .focusProperties {
                                    detailPlayFocusRequester.takeIfAttached()?.let { down = it }
                                    detailCloseFocusRequester.takeIfAttached()?.let { right = it }
                                }
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(50),
                                    focusedBorderColor = NetflixWhite,
                                    focusedScale = 1.08f,
                                    onFocusChange = { focused ->
                                        if (focused) focusTracker.userMovedFocus = true
                                    },
                                    onClick = onBack
                                )
                                .testTag("detail_back_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Torna al titolo precedente",
                                    tint = NetflixWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = previousDetailTitle?.let { "Torna a $it" } ?: "Indietro",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NetflixWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 220.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(44.dp)
                            .safeFocusRequester(detailCloseFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                    returnToHeroStage()
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                detailPlayFocusRequester.takeIfAttached()?.let { down = it }
                                if (detailStackDepth > 1) {
                                    detailBackFocusRequester.takeIfAttached()?.let { left = it }
                                }
                            }
                            .tvFocusableControl(
                                shape = CircleShape,
                                focusedScale = 1.12f,
                                onFocusChange = { focused ->
                                    if (focused) focusTracker.userMovedFocus = true
                                }
                            )
                            .clip(CircleShape)
                            .background(NetflixBlack.copy(alpha = 0.78f))
                            .testTag("detail_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Chiudi",
                            tint = NetflixWhite
                        )
                    }
                }
            }
        }

        // Nuovo Popup Modale Cinematografico 16:9 con ricerca diretta YouTube e lista multipla
        val currentTrailerState = state.activeTrailer
        if (currentTrailerState != null) {
            YouTubeTrailerDialog(
                mediaItem = item,
                trailerState = currentTrailerState,
                bufferPreset = bufferPreset,
                onSelectTrailer = onSelectYouTubeTrailer,
                onRetry = onRetryYouTubeTrailer,
                onClose = onCloseTrailer
            )
        }
    }
}

@Composable
private fun DetailRoundActionButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color = Color.Transparent,
    focusedBorderColor: Color = NetflixWhite,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    lockHorizontalRow: Boolean = false,
    onDownKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .height(80.dp)
    ) {
        Surface(
            color = containerColor,
            shape = CircleShape,
            border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null,
            modifier = Modifier
                .size(56.dp)
                .safeFocusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionDown -> onDownKey?.invoke(keyEvent) == true
                            Key.DirectionLeft -> {
                                if (leftFocusRequester != null) {
                                    leftFocusRequester.safeRequestFocus()
                                } else {
                                    lockHorizontalRow
                                }
                            }
                            Key.DirectionRight -> {
                                if (rightFocusRequester != null) {
                                    rightFocusRequester.safeRequestFocus()
                                } else {
                                    lockHorizontalRow
                                }
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .focusProperties {
                    upFocusRequester.takeIfAttached()?.let { up = it }
                    if (onDownKey == null) {
                        downFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    leftFocusRequester.takeIfAttached()?.let { left = it }
                    rightFocusRequester.takeIfAttached()?.let { right = it }
                }
                .tvFocusableControl(
                    shape = CircleShape,
                    focusedScale = 1.12f,
                    focusedBorderColor = focusedBorderColor,
                    onFocusChange = onFocusChange,
                    onClick = onClick
                )
                .testTag(testTag)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NetflixLightGray,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun rememberSizedDetailImageRequest(
    url: String?,
    widthDp: Dp,
    heightDp: Dp
): Any? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.roundToPx().coerceAtLeast(1) }
    val heightPx = with(density) { heightDp.roundToPx().coerceAtLeast(1) }
    return remember(context, url, widthPx, heightPx) {
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
                .crossfade(false)
                .build()
        }
    }
}

@Composable
private fun TmdbTrailerCard(
    video: TmdbVideoDto,
    fallbackBackdropUrl: String?,
    itemIndex: Int,
    listState: LazyListState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
        border = BorderStroke(1.dp, if (isFocused) NetflixWhite else NetflixElevatedSurface),
        modifier = modifier
            .width(236.dp)
            .animatedPosterShowcase(
                itemIndex = itemIndex,
                listState = listState,
                isFocused = isFocused,
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.08f,
                onFocusedChanged = { isFocused = it },
                onClick = onClick
            )
            .testTag("trailer_card_${video.key}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .background(NetflixDarkSurface)
            ) {
                AsyncImage(
                    model = rememberSizedDetailImageRequest(
                        url = if (video.isYouTube) video.thumbnailUrl else fallbackBackdropUrl,
                        widthDp = 236.dp,
                        heightDp = 128.dp
                    ),
                    contentDescription = video.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NetflixBlack.copy(alpha = 0.35f))
                )
                Surface(
                    color = NetflixRed,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = video.typeBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(NetflixRed.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Riproduci Trailer in App",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Riproduzione In-App • ${video.site} (${video.languageCode?.uppercase() ?: "INT"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = NetflixLightGray,
                    maxLines = 1
                )
            }
        }
    }
}


@Composable
private fun CastCard(
    actor: TmdbCastMemberDto,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .tvFocusableControl(
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.06f,
                focusedBorderColor = NetflixWhite
            )
            .padding(vertical = 4.dp)
            .testTag("cast_card_${actor.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = rememberSizedDetailImageRequest(
                url = actor.profileUrl,
                widthDp = 68.dp,
                heightDp = 68.dp
            ),
            contentDescription = actor.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(NetflixCardSurface)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actor.name,
            style = MaterialTheme.typography.labelSmall,
            color = NetflixWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!actor.character.isNullOrBlank()) {
            Text(
                text = actor.character,
                style = MaterialTheme.typography.labelSmall,
                color = NetflixMutedGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EpisodeRowItem(
    episode: TmdbEpisodeDto,
    savedEpProgress: EpisodeProgressEntity?,
    fallbackBackdropUrl: String?,
    onPlayEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) NetflixCardSurface else NetflixDarkSurface
        ),
        border = if (isFocused) BorderStroke(2.dp, NetflixWhite) else null,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .tvFocusableControl(
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.02f,
                focusedBorderColor = NetflixWhite,
                onFocusChange = { isFocused = it },
                onClick = onPlayEpisode
            )
            .testTag("episode_card_${episode.seasonNumber}_${episode.episodeNumber}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(132.dp)
                        .height(76.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NetflixCardSurface)
                ) {
                    AsyncImage(
                        model = rememberSizedDetailImageRequest(
                            url = episode.stillUrl ?: fallbackBackdropUrl,
                            widthDp = 132.dp,
                            heightDp = 76.dp
                        ),
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(NetflixBlack.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Riproduci episodio",
                            tint = NetflixWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (savedEpProgress != null && savedEpProgress.startSeconds > 0L) {
                        LinearProgressIndicator(
                            progress = { savedEpProgress.progressFraction },
                            color = NetflixRed,
                            trackColor = NetflixElevatedSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${episode.episodeNumber}. ${episode.name ?: "Episodio ${episode.episodeNumber}"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = NetflixWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (episode.runtime != null && episode.runtime > 0) {
                            Text(
                                text = "${episode.runtime}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixLightGray
                            )
                        }
                        if (episode.voteAverage != null && episode.voteAverage > 0.0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = NetflixGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = String.format("%.1f", episode.voteAverage),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixLightGray
                                )
                            }
                        }
                        if (savedEpProgress != null && savedEpProgress.startSeconds > 0L) {
                            Text(
                                text = "• Da ${formatSecondsToMmSs(savedEpProgress.startSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixRed
                            )
                        }
                    }
                }
            }

            if (!episode.overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = NetflixLightGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
