package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.StreamingBufferPreset
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbEpisodeDto
import com.example.ui.PlaybackSession
import com.example.ui.components.ImageTargetSizes
import com.example.ui.components.TvSeekTimeBar
import com.example.ui.components.formatSecondsToMmSs
import com.example.ui.components.rememberOptimizedImageRequest
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
 * Barra di avanzamento isolata che legge posizione e durata tramite provider lambda,
 * limitando la ricomposizione del ticker 1s esclusivamente allo slider.
 */
@Composable
internal fun VixPlayerSeekBarIsolated(
    positionMsProvider: () -> Long,
    durationMsProvider: () -> Long,
    onSeekPreview: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onInteraction: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester,
    showAssistiveHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    TvSeekTimeBar(
        currentPositionMs = positionMsProvider(),
        durationMs = durationMsProvider(),
        onSeekPreview = onSeekPreview,
        onSeekCommit = onSeekCommit,
        onTogglePlayPause = onTogglePlayPause,
        onInteraction = onInteraction,
        onFocusChanged = onFocusChanged,
        onScrubbingChanged = onScrubbingChanged,
        focusRequester = focusRequester,
        upFocusRequester = upFocusRequester,
        downFocusRequester = downFocusRequester,
        compact = false,
        showAssistiveHeader = showAssistiveHeader,
        modifier = modifier
    )
}

/**
 * Card overlay "Prossimo Episodio in Xs" estratta per mantenere modulare il player
 * e isolare le ricomposizioni del countdown a 1s.
 */
@Composable
internal fun VixAutoNextEpisodeCard(
    session: PlaybackSession,
    currentSeason: Int,
    currentEpisode: Int,
    nextEpisodeDto: TmdbEpisodeDto?,
    remainingSecProvider: () -> Int,
    playFocusRequester: FocusRequester,
    cancelFocusRequester: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingSec = remainingSecProvider()
    val thumb = nextEpisodeDto?.stillUrl ?: session.mediaItem.backdropUrl ?: session.mediaItem.posterUrl
    val thumbRequest = rememberOptimizedImageRequest(
        url = thumb,
        widthDp = ImageTargetSizes.EPISODE_STILL_WIDTH_DP.dp,
        heightDp = ImageTargetSizes.EPISODE_STILL_HEIGHT_DP.dp,
        allowRgb565 = true
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.5.dp, NetflixRed),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
        modifier = modifier
            .widthIn(max = 390.dp)
            .fillMaxWidth()
            .testTag("auto_next_episode_card")
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "PROSSIMO EPISODIO IN ${remainingSec}s",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = NetflixMatchGreen
                )
                Surface(
                    color = NetflixRed,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "S${currentSeason}:E${currentEpisode + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!thumb.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = nextEpisodeDto?.name ?: "Prossimo episodio",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 96.dp, height = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NetflixDarkSurface)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nextEpisodeDto?.name ?: "Episodio ${currentEpisode + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetflixWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = session.mediaItem.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LinearProgressIndicator(
                progress = { ((10 - remainingSec) / 10f).coerceIn(0f, 1f) },
                color = NetflixRed,
                trackColor = NetflixElevatedSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onPlayNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixWhite,
                        contentColor = NetflixBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(playFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft -> true
                                    Key.DirectionRight -> cancelFocusRequester.safeRequestFocus()
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            cancelFocusRequester.takeIfAttached()?.let { right = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = NetflixRed,
                            focusedScale = 1.04f,
                            focusTag = "auto_next_play_now_button"
                        )
                        .testTag("auto_next_play_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Riproduci Ora", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(cancelFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp, Key.DirectionDown, Key.DirectionRight -> true
                                    Key.DirectionLeft -> playFocusRequester.safeRequestFocus()
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            playFocusRequester.takeIfAttached()?.let { left = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = NetflixWhite,
                            focusedScale = 1.04f,
                            focusTag = "auto_next_cancel_button"
                        )
                        .testTag("auto_next_cancel_button")
                ) {
                    Text("Annulla")
                }
            }
        }
    }
}

/**
 * Risolve l'URL della locandina da usare nello sfondo a tutto schermo durante l'avvio dello streaming.
 * Per film e serie TV riutilizza direttamente la locandina principale già presente nei metadati/cache
 * (preferendo sempre la locandina della serie TV anche quando si avvia un singolo episodio),
 * senza generare alcuna chiamata API aggiuntiva.
 */
internal fun resolveLaunchBackgroundPosterUrl(mediaItem: MediaItem): String? {
    val rawPoster = mediaItem.posterPath?.trim()?.takeIf { it.isNotBlank() }
    if (rawPoster != null) {
        return if (rawPoster.startsWith("http://") || rawPoster.startsWith("https://")) {
            rawPoster
        } else {
            "https://image.tmdb.org/t/p/w500$rawPoster"
        }
    }
    return mediaItem.backdropUrl?.takeIf { it.isNotBlank() }
}

/**
 * Sfondo cinematografico a tutto schermo visualizzato sotto al popup di attesa durante l'avvio
 * dello streaming. Copre l'intera area disponibile (cover / ContentScale.Crop) con un overlay
 * scuro a più livelli (scurimento generale, gradiente verticale verso il basso e leggera vignettatura)
 * e gestisce in modo elegante stati di caricamento, assenza o errore senza interferire con focus o ExoPlayer.
 */
@Composable
internal fun VixStreamLaunchPosterBackdrop(
    mediaItem: MediaItem,
    isLowRamDevice: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val posterUrl = remember(mediaItem.id, mediaItem.mediaType, mediaItem.posterPath, mediaItem.backdropPath) {
        resolveLaunchBackgroundPosterUrl(mediaItem)
    }

    var isPosterReady by remember(posterUrl) { mutableStateOf(false) }
    var isPosterFailed by remember(posterUrl) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (posterUrl != null && isPosterReady && !isPosterFailed) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "stream_launch_poster_alpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .focusProperties { canFocus = false }
            .testTag("stream_launch_poster_backdrop")
    ) {
        if (posterUrl != null) {
            val rawWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(48) }
            val rawHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(48) }
            val maxDimensionCapPx = if (isLowRamDevice) 720 else 1080
            val largestSidePx = maxOf(rawWidthPx, rawHeightPx)
            val downscale = if (largestSidePx > maxDimensionCapPx) {
                maxDimensionCapPx.toFloat() / largestSidePx.toFloat()
            } else {
                1f
            }
            val targetWidthPx = (rawWidthPx * downscale).toInt().coerceAtLeast(48)
            val targetHeightPx = (rawHeightPx * downscale).toInt().coerceAtLeast(48)

            val backdropRequest = remember(context, posterUrl, targetWidthPx, targetHeightPx) {
                ImageTargetSizes.buildRequest(
                    context = context,
                    url = posterUrl,
                    widthPx = targetWidthPx,
                    heightPx = targetHeightPx,
                    allowRgb565 = true
                )
            }

            AsyncImage(
                model = backdropRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                onSuccess = {
                    isPosterFailed = false
                    isPosterReady = true
                },
                onError = {
                    isPosterReady = false
                    isPosterFailed = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = imageAlpha }
                    .testTag("stream_launch_poster_image")
            )
        }

        // Overlay cinematografico a più livelli:
        // 1) Scurimento generale discreto
        // 2) Gradiente verticale più intenso verso il basso
        // 3) Leggera vignettatura radiale sui bordi
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NetflixBlack.copy(alpha = 0.52f))
                .background(
                    Brush.verticalGradient(
                        0.0f to NetflixBlack.copy(alpha = 0.42f),
                        0.36f to NetflixBlack.copy(alpha = 0.20f),
                        0.72f to NetflixBlack.copy(alpha = 0.64f),
                        1.0f to NetflixBlack.copy(alpha = 0.92f)
                    )
                )
                .drawBehind {
                    val radius = (maxOf(size.width, size.height) * 0.72f).coerceAtLeast(1f)
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to Color.Transparent,
                            0.55f to NetflixBlack.copy(alpha = 0.16f),
                            1.0f to NetflixBlack.copy(alpha = 0.62f),
                            center = center,
                            radius = radius
                        )
                    )
                }
                .focusProperties { canFocus = false }
                .testTag("stream_launch_poster_overlay")
        )
    }
}

/**
 * Popup minimal e compatto di avvio streaming mostrato in fase di risoluzione e avvio del video.
 * Mantiene solo le informazioni essenziali (titolo, episodio/ripresa, sorgente attiva VixSrc e
 * stato sintetico) con focus D-Pad immediato e protezione contro doppie pressioni del telecomando.
 */
@Composable
internal fun VixStreamLoadingPopup(
    session: PlaybackSession,
    streamLang: String,
    bufferPreset: StreamingBufferPreset,
    cancelFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester? = null,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = session.mediaItem
    val currentEpisodeDto = remember(session.seasonEpisodes, session.episodeNumber) {
        session.seasonEpisodes.firstOrNull { it.episodeNumber == session.episodeNumber }
    }
    val resolvedEpisodeTitle = session.episodeTitle?.takeIf { it.isNotBlank() }
        ?: currentEpisodeDto?.name?.takeIf { it.isNotBlank() }
    val imageUrl = currentEpisodeDto?.stillUrl ?: item.backdropUrl ?: item.posterUrl
    val previewRequest = rememberOptimizedImageRequest(
        url = imageUrl,
        widthDp = 80.dp,
        heightDp = 50.dp,
        allowRgb565 = true
    )

    val seriesLabel = remember(item.isSeries, session.seasonNumber, session.episodeNumber, resolvedEpisodeTitle) {
        if (item.isSeries && (session.seasonNumber != null || session.episodeNumber != null)) {
            val seasonNum = session.seasonNumber ?: 1
            val episodeNum = session.episodeNumber ?: 1
            if (!resolvedEpisodeTitle.isNullOrBlank()) {
                "S$seasonNum · E$episodeNum · $resolvedEpisodeTitle"
            } else {
                "S$seasonNum · E$episodeNum"
            }
        } else {
            null
        }
    }

    val statusLabel = if (session.isResolvingStream) {
        "Ricerca sorgente…"
    } else {
        "Avvio video…"
    }

    // Evita doppi eventi/chiusure accidentali alla pressione ripetuta del telecomando
    var cancelDispatched by remember(session.sessionToken) { mutableStateOf(false) }
    val safeCancel: () -> Unit = {
        if (!cancelDispatched) {
            cancelDispatched = true
            onCancel()
        }
    }

    LaunchedEffect(session.sessionToken) {
        for (frame in 1..3) {
            withFrameNanos { }
            if (cancelFocusRequester.safeRequestFocus()) break
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NetflixDarkSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.10f)),
        shadowElevation = 0.dp,
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .testTag("video_loading_popup")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Intestazione essenziale: miniatura pulita + titolo + episodio/anno + ripresa
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 68.dp, height = 42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NetflixElevatedSurface)
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = previewRequest,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.isSeries) Icons.Default.Tv else Icons.Default.Movie,
                            contentDescription = null,
                            tint = NetflixMutedGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NetflixWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (seriesLabel != null) {
                        Text(
                            text = seriesLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("loading_popup_series_info")
                        )
                    } else if (item.releaseYear.isNotBlank()) {
                        Text(
                            text = item.releaseYear,
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray,
                            maxLines = 1
                        )
                    }

                    if (session.startSeconds > 0L) {
                        Text(
                            text = "Ripresa da ${formatSecondsToMmSs(session.startSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = NetflixMatchGreen,
                            maxLines = 1,
                            modifier = Modifier.testTag("loading_popup_resume_badge")
                        )
                    }
                }
            }

            // 2. Barra di avanzamento sottile (singolo indicatore minimal)
            LinearProgressIndicator(
                color = NetflixRed,
                trackColor = NetflixElevatedSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
            )

            // 3. Riga inferiore unificata: sorgente attiva VixSrc + stato sintetico + azione Annulla
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NetflixRed)
                        )
                        Text(
                            text = "VixSrc",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = NetflixWhite,
                            modifier = Modifier.testTag("loading_popup_source_label")
                        )
                        Surface(
                            color = NetflixElevatedSurface,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = streamLang.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixLightGray,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("loading_popup_status_text")
                    )
                }

                OutlinedButton(
                    onClick = safeCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NetflixElevatedSurface.copy(alpha = 0.55f),
                        contentColor = NetflixWhite
                    ),
                    border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.14f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(cancelFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> upFocusRequester?.safeRequestFocus() ?: true
                                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionDown -> true
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            upFocusRequester.takeIfAttached()?.let { up = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = NetflixWhite,
                            focusedScale = 1.02f,
                            focusTag = "loading_cancel_button"
                        )
                        .testTag("loading_cancel_button")
                ) {
                    Text(
                        text = "Annulla",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Stato errore integrato dello stesso popup di avvio streaming, coerente per dimensioni,
 * tipografia e navigazione D-Pad (Riprova <-> Chiudi, BACK per uscire, anti-doppio-invio).
 */
@Composable
internal fun VixStreamLaunchErrorPopup(
    session: PlaybackSession,
    streamLang: String,
    errorMessage: String,
    retryFocusRequester: FocusRequester,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val closeFocusRequester = remember { FocusRequester() }
    val item = session.mediaItem
    val contextSubtitle = remember(item.title, item.isSeries, session.seasonNumber, session.episodeNumber) {
        if (item.isSeries && (session.seasonNumber != null || session.episodeNumber != null)) {
            "${item.title} · S${session.seasonNumber ?: 1} · E${session.episodeNumber ?: 1}"
        } else {
            item.title
        }
    }

    // Impedisce che pressioni ravvicinate del telecomando riavviino più volte la stessa sorgente
    var actionDispatched by remember(errorMessage, session.sessionToken) { mutableStateOf(false) }
    val safeRetry: () -> Unit = {
        if (!actionDispatched) {
            actionDispatched = true
            onRetry()
        }
    }
    val safeClose: () -> Unit = {
        if (!actionDispatched) {
            actionDispatched = true
            onClose()
        }
    }

    LaunchedEffect(errorMessage, session.sessionToken) {
        for (frame in 1..3) {
            withFrameNanos { }
            if (retryFocusRequester.safeRequestFocus()) break
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NetflixDarkSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .testTag("video_error_popup")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Avvio non riuscito",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NetflixWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = NetflixElevatedSurface,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "VixSrc · ${streamLang.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = contextSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = NetflixMutedGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = NetflixLightGray,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("video_error_message")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = safeRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(retryFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionRight -> closeFocusRequester.safeRequestFocus()
                                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft -> true
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            closeFocusRequester.takeIfAttached()?.let { right = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = NetflixWhite,
                            focusedScale = 1.02f,
                            focusTag = "player_retry_stream_button"
                        )
                        .testTag("player_retry_stream_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Riprova",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = safeClose,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(closeFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> retryFocusRequester.safeRequestFocus()
                                    Key.DirectionUp, Key.DirectionDown, Key.DirectionRight -> true
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            retryFocusRequester.takeIfAttached()?.let { left = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = NetflixWhite,
                            focusedScale = 1.02f,
                            focusTag = "player_error_close_button"
                        )
                        .testTag("player_error_close_button")
                ) {
                    Text(
                        text = "Chiudi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
