package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    val isTvDevice = rememberIsTvDevice()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("media_detail_screen")
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { it.mediaItem.uniqueKey },
            transitionSpec = {
                (fadeIn(tween(280)) +
                    slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 10 } +
                    scaleIn(initialScale = 0.97f, animationSpec = tween(280)))
                    .togetherWith(
                        fadeOut(tween(180)) +
                            slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 12 }
                    )
            },
            label = "detailItemTransition",
            modifier = Modifier.fillMaxSize()
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
            val detailPlayFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val detailTrailerFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val detailCloseFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val detailBackFocusRequester = remember(currentItem.uniqueKey) { FocusRequester() }
            val firstTrailerFocus = remember(currentItem.uniqueKey) { FocusRequester() }
            val firstSeasonFocus = remember(currentItem.uniqueKey) { FocusRequester() }
            val firstEpisodeFocus = remember(currentItem.uniqueKey) { FocusRequester() }
            val firstSimilarFocus = remember(currentItem.uniqueKey) { FocusRequester() }
            var isPlayButtonFocused by remember(currentItem.uniqueKey) { mutableStateOf(false) }
            var isTrailerButtonFocused by remember(currentItem.uniqueKey) { mutableStateOf(false) }
            var isOtherActionFocused by remember(currentItem.uniqueKey) { mutableStateOf(false) }
            var wasTrailerOpen by remember(currentItem.uniqueKey) { mutableStateOf(false) }

            val isCurrentActive = currentItem.uniqueKey == item.uniqueKey
            val isTrailerOpen = currentState.activeTrailer != null

            LaunchedEffect(isCurrentActive, isInteractive, isTrailerOpen) {
                if (isCurrentActive && isInteractive && !isTrailerOpen) {
                    val returnToTrailer = wasTrailerOpen
                    val targetRequester = if (returnToTrailer) detailTrailerFocusRequester else detailPlayFocusRequester
                    if (!returnToTrailer && listState.firstVisibleItemIndex > 0) {
                        runCatching { listState.scrollToItem(0) }
                    }
                    var didAutoFocusOnce = false
                    androidx.compose.runtime.withFrameNanos { }
                    val retryDelaysMs = longArrayOf(0L, 40L, 90L, 160L, 280L)
                    for (stepDelay in retryDelaysMs) {
                        if (stepDelay > 0L) {
                            kotlinx.coroutines.delay(stepDelay)
                        }
                        val targetAlreadyFocused = if (returnToTrailer) isTrailerButtonFocused else isPlayButtonFocused
                        if (targetAlreadyFocused) break
                        if (didAutoFocusOnce && isOtherActionFocused) break
                        val requested = targetRequester.safeRequestFocus()
                        if (requested) {
                            kotlinx.coroutines.yield()
                            didAutoFocusOnce = true
                            val focusedAfterYield = if (returnToTrailer) isTrailerButtonFocused else isPlayButtonFocused
                            if (focusedAfterYield) break
                        }
                    }
                }
                if (isCurrentActive) {
                    wasTrailerOpen = isTrailerOpen
                }
            }

            val downTargetFromActions: FocusRequester? = when {
                trailers.isNotEmpty() -> firstTrailerFocus
                currentItem.isSeries && !details?.seasons.isNullOrEmpty() -> firstSeasonFocus
                currentItem.isSeries && episodesList.isNotEmpty() -> firstEpisodeFocus
                similarList.isNotEmpty() -> firstSimilarFocus
                else -> null
            }

            var backdropRevealStarted by remember(currentItem.uniqueKey) { mutableStateOf(false) }
            LaunchedEffect(currentItem.uniqueKey) {
                backdropRevealStarted = true
            }
            val backdropScale by animateFloatAsState(
                targetValue = if (backdropRevealStarted) 1f else 1.07f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
                label = "detailBackdropSettle"
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .testTag("media_detail_lazy_column"),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // Header con Backdrop
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(0.dp))
                    ) {
                        AsyncImage(
                            model = rememberDetailImageRequest(currentItem.backdropUrl ?: currentItem.posterUrl),
                            contentDescription = currentItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = backdropScale
                                    scaleY = backdropScale
                                }
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

                        // Su Touch mostra Play sul backdrop; su Android TV evita il doppio bottone Play per non creare trappole D-pad
                        if (!isTvDevice) {
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

                        // Barra superiore: Torna al titolo precedente (se stack > 1) e Chiudi scheda
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
                                        .focusProperties {
                                            detailPlayFocusRequester.takeIfAttached()?.let { down = it }
                                            detailCloseFocusRequester.takeIfAttached()?.let { right = it }
                                        }
                                        .tvFocusableControl(
                                            shape = RoundedCornerShape(50),
                                            focusedBorderColor = NetflixWhite,
                                            focusedScale = 1.08f,
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
                                    .focusProperties {
                                        detailPlayFocusRequester.takeIfAttached()?.let { down = it }
                                        if (detailStackDepth > 1) {
                                            detailBackFocusRequester.takeIfAttached()?.let { left = it }
                                        }
                                    }
                                    .tvFocusableControl(shape = CircleShape, focusedScale = 1.12f)
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

            // Info principali + Pulsanti Riproduci / Riprendi / Guarda Trailer
            item {
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

                    // Barra di avanzamento se presente in Continua a guardare (Room DB)
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

                    val watchlistBg by animateColorAsState(
                        targetValue = if (isInWatchlist) NetflixMatchGreen.copy(alpha = 0.22f) else NetflixCardSurface,
                        animationSpec = tween(220),
                        label = "detailWatchlistBg"
                    )
                    val watchlistBorder by animateColorAsState(
                        targetValue = if (isInWatchlist) NetflixMatchGreen else NetflixElevatedSurface,
                        animationSpec = tween(220),
                        label = "detailWatchlistBorder"
                    )
                    val watchlistTint by animateColorAsState(
                        targetValue = if (isInWatchlist) NetflixMatchGreen else NetflixWhite,
                        animationSpec = tween(220),
                        label = "detailWatchlistTint"
                    )

                    // Riga unica con icone rotonde: Riproduci, Trailer YouTube, La Mia Lista (+ Dall'inizio se presente)
                    val topReturnTarget = if (detailStackDepth > 1) detailBackFocusRequester else detailCloseFocusRequester
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
                            onFocusChange = { focused -> isPlayButtonFocused = focused },
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
                            onFocusChange = { focused ->
                                isTrailerButtonFocused = focused
                                if (focused) isOtherActionFocused = true
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
                            upFocusRequester = topReturnTarget,
                            downFocusRequester = downTargetFromActions,
                            onFocusChange = { focused ->
                                if (focused) isOtherActionFocused = true
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
                                upFocusRequester = topReturnTarget,
                                downFocusRequester = downTargetFromActions,
                                onFocusChange = { focused ->
                                    if (focused) isOtherActionFocused = true
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

            // SEZIONE DEDICATA: Trailer & Video Ufficiali da TMDB (/movie/{id}/videos o /tv/{id}/videos)
            if (trailers.isNotEmpty()) {
                item {
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
                        LazyRow(
                            state = trailersListState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(trailers, key = { _, it -> it.id.ifBlank { it.key } }) { index, video ->
                                TmdbTrailerCard(
                                    video = video,
                                    fallbackBackdropUrl = currentItem.backdropUrl,
                                    itemIndex = index,
                                    listState = trailersListState,
                                    onClick = onOpenYouTubeTrailerPopup,
                                    modifier = Modifier
                                        .then(
                                            if (index == 0) Modifier.safeFocusRequester(firstTrailerFocus) else Modifier
                                        )
                                        .focusProperties {
                                            if (index == 0) {
                                                detailPlayFocusRequester.takeIfAttached()?.let { up = it }
                                            }
                                        }
                                        .animateItem(
                                            fadeInSpec = tween(250),
                                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Cast TMDB
            if (castList.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Cast Principale",
                        style = MaterialTheme.typography.titleMedium,
                        color = NetflixWhite,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(castList, key = { it.id }) { actor ->
                            CastCard(
                                actor = actor,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(240),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            )
                        }
                    }
                }
            }

            // Selettore Stagioni ed Episodi per Serie TV
            if (currentItem.isSeries) {
                item {
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
                                    val seasonBg by animateColorAsState(
                                        targetValue = if (isSelected) NetflixRed else NetflixCardSurface,
                                        animationSpec = tween(200),
                                        label = "seasonChipBg"
                                    )
                                    val seasonBorder by animateColorAsState(
                                        targetValue = if (isSelected) NetflixRed else NetflixElevatedSurface,
                                        animationSpec = tween(200),
                                        label = "seasonChipBorder"
                                    )
                                    Surface(
                                        color = seasonBg,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, seasonBorder),
                                        modifier = Modifier
                                            .then(
                                                if (sIndex == 0) Modifier.safeFocusRequester(firstSeasonFocus) else Modifier
                                            )
                                            .focusProperties {
                                                if (sIndex == 0 && trailers.isEmpty()) {
                                                    detailPlayFocusRequester.takeIfAttached()?.let { up = it }
                                                }
                                            }
                                            .tvFocusableControl(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedScale = 1.06f,
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
                    item {
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
                        key = { _, ep -> "ep_${ep.seasonNumber}_${ep.episodeNumber}" }
                    ) { epIndex, ep ->
                        EpisodeRowItem(
                            episode = ep,
                            savedEpProgress = currentState.episodeProgressMap[ep.episodeNumber],
                            fallbackBackdropUrl = currentItem.backdropUrl,
                            onPlayEpisode = {
                                onPlayEpisode(ep.seasonNumber, ep.episodeNumber, ep.name)
                            },
                            modifier = Modifier
                                .then(
                                    if (epIndex == 0) Modifier.safeFocusRequester(firstEpisodeFocus) else Modifier
                                )
                                .focusProperties {
                                    if (epIndex == 0 && !details?.seasons.isNullOrEmpty()) {
                                        firstSeasonFocus.takeIfAttached()?.let { up = it }
                                    }
                                }
                                .animateItem(
                                    fadeInSpec = tween(240),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    fadeOutSpec = tween(180)
                                )
                        )
                    }
                }
            }

            // Contenuti simili da TMDB
            if (similarList.isNotEmpty()) {
                val upToBeforeSimilar = when {
                    currentItem.isSeries && !details?.seasons.isNullOrEmpty() -> firstSeasonFocus
                    trailers.isNotEmpty() -> firstTrailerFocus
                    else -> detailPlayFocusRequester
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    MediaCatalogRow(
                        title = "Titoli Simili su TMDB",
                        items = similarList,
                        onItemClick = onSelectSimilar,
                        onItemQuickPlay = { sim -> onSelectSimilar(sim) },
                        firstItemFocusRequester = firstSimilarFocus,
                        upFocusRequester = if (currentItem.isSeries && episodesList.isNotEmpty()) null else upToBeforeSimilar
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
    onFocusChange: ((Boolean) -> Unit)? = null,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Surface(
            color = containerColor,
            shape = CircleShape,
            border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(56.dp)
                .safeFocusRequester(focusRequester)
                .focusProperties {
                    upFocusRequester.takeIfAttached()?.let { up = it }
                    downFocusRequester.takeIfAttached()?.let { down = it }
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
private fun rememberDetailImageRequest(url: String?): Any? {
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
                    model = rememberDetailImageRequest(if (video.isYouTube) video.thumbnailUrl else fallbackBackdropUrl),
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
        modifier = modifier.width(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = rememberDetailImageRequest(actor.profileUrl),
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
                        model = rememberDetailImageRequest(episode.stillUrl ?: fallbackBackdropUrl),
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
