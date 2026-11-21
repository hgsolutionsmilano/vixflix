package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.StreamingBufferPreset
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
 * Popup modale di caricamento mostrato in fase di avvio del video e in attesa dello streaming,
 * con locandina/miniature, titolo, stagione/episodio (se Serie TV), minuto di ripresa e stato del buffer.
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
    val resolvedOverview = currentEpisodeDto?.overview?.takeIf { it.isNotBlank() }
        ?: item.overview
    val imageUrl = currentEpisodeDto?.stillUrl ?: item.backdropUrl ?: item.posterUrl
    val previewRequest = rememberOptimizedImageRequest(
        url = imageUrl,
        widthDp = ImageTargetSizes.EPISODE_STILL_WIDTH_DP.dp,
        heightDp = ImageTargetSizes.EPISODE_STILL_HEIGHT_DP.dp,
        allowRgb565 = true
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = NetflixCardSurface.copy(alpha = 0.96f)
        ),
        border = BorderStroke(1.5.dp, NetflixRed.copy(alpha = 0.75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .testTag("video_loading_popup")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Riga superiore: Locandina/Fotogramma + Dati principali del titolo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 112.dp, height = 74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NetflixDarkSurface)
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = previewRequest,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Badge tipo media nell'angolo dell'anteprima
                    Surface(
                        color = NetflixRed,
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isSeries) Icons.Default.Tv else Icons.Default.Movie,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (item.isSeries) "SERIE TV" else "FILM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetflixWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${item.matchPercentage}% Compatibile",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetflixMatchGreen
                        )
                        if (item.releaseYear.isNotBlank()) {
                            Text(
                                text = "• ${item.releaseYear}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixLightGray
                            )
                        }
                        Surface(
                            color = NetflixElevatedSurface,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "1080p ${streamLang.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixWhite,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Box dedicato a Stagione ed Episodio se è una Serie TV
            if (item.isSeries && (session.seasonNumber != null || session.episodeNumber != null)) {
                val seasonNum = session.seasonNumber ?: 1
                val episodeNum = session.episodeNumber ?: 1
                Surface(
                    color = NetflixDarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loading_popup_series_info")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stagione $seasonNum • Episodio $episodeNum (S${seasonNum}:E${episodeNum})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NetflixRed
                            )
                            if (!resolvedEpisodeTitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "\"$resolvedEpisodeTitle\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NetflixWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Sinossi sintetica del film o episodio
            if (resolvedOverview.isNotBlank()) {
                Text(
                    text = resolvedOverview,
                    style = MaterialTheme.typography.bodySmall,
                    color = NetflixLightGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Badge punto di ripresa ("Continua a guardare") se presente
            if (session.startSeconds > 0L) {
                Surface(
                    color = NetflixMatchGreen.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NetflixMatchGreen.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ripresa automatica da ${formatSecondsToMmSs(session.startSeconds)} (Minuto ${session.startMinutes})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetflixMatchGreen,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Indicatore di caricamento e stato della connessione allo stream VixSrc / ExoPlayer
            LinearProgressIndicator(
                color = NetflixRed,
                trackColor = NetflixElevatedSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CircularProgressIndicator(
                        color = NetflixRed,
                        strokeWidth = 2.2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (session.isResolvingStream) {
                            "Connessione diretta a vixsrc.to e recupero stream HLS…"
                        } else {
                            "Avvio rapido video (Buffer ${bufferPreset.label} • ${bufferPreset.sizeMb} MB)…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixWhite
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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
                            shape = RoundedCornerShape(8.dp),
                            focusedBorderColor = NetflixRed,
                            focusedScale = 1.04f,
                            focusTag = "loading_cancel_button"
                        )
                        .testTag("loading_cancel_button")
                ) {
                    Text("Annulla", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
