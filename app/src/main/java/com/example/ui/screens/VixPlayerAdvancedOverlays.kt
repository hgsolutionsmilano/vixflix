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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.VerifiedNextEpisode
import com.example.data.local.formatSeriesSeasonEpisodeLine
import com.example.ui.PlaybackSession
import com.example.ui.components.ImageTargetSizes
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
 * Intestazione del contenuto posizionata immediatamente sopra la barra di avanzamento temporale.
 * - Per i film mostra esclusivamente il titolo principale in evidenza.
 * - Per le serie TV mostra il titolo principale e, immediatamente sotto, la riga
 *   `STAGIONE X • EPISODIO Y` (con `– Titolo Episodio` se disponibile).
 */
@Composable
internal fun PlayerMediaTitleHeader(
    session: PlaybackSession,
    modifier: Modifier = Modifier
) {
    val resolvedEpisodeTitle = remember(
        session.mediaItem.isSeries,
        session.episodeTitle,
        session.episodeNumber,
        session.seasonEpisodes
    ) {
        if (!session.mediaItem.isSeries) {
            null
        } else {
            session.episodeTitle?.takeIf { it.isNotBlank() }
                ?: session.seasonEpisodes
                    .firstOrNull { it.episodeNumber == session.episodeNumber }
                    ?.name
                    ?.takeIf { it.isNotBlank() }
        }
    }

    val seriesSubtitleLine = remember(
        session.mediaItem.isSeries,
        session.seasonNumber,
        session.episodeNumber,
        resolvedEpisodeTitle
    ) {
        if (session.mediaItem.isSeries) {
            formatSeriesSeasonEpisodeLine(
                seasonNumber = session.seasonNumber,
                episodeNumber = session.episodeNumber,
                episodeTitle = resolvedEpisodeTitle
            )
        } else {
            null
        }
    }

    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.85f),
            offset = Offset(0f, 2f),
            blurRadius = 6f
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .testTag("player_media_title_header"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = session.mediaItem.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                shadow = textShadow
            ),
            color = NetflixWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("player_media_main_title")
        )

        if (!seriesSubtitleLine.isNullOrBlank()) {
            Text(
                text = seriesSubtitleLine,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                    shadow = textShadow
                ),
                color = NetflixWhite.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("player_media_series_subtitle")
            )
        }
    }
}

/**
 * Pulsante fluttuante moderno "SALTA INTRO" mostrato in basso a destra solo quando esistono
 * dati intro affidabili per lo specifico episodio e la posizione corrente rientra nell'intervallo.
 */
@Composable
internal fun SkipIntroFloatingButton(
    focusRequester: FocusRequester,
    controlsVisible: Boolean,
    leftFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    onSkipIntro: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NetflixBlack.copy(alpha = 0.84f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, NetflixWhite.copy(alpha = 0.85f)),
        shadowElevation = 14.dp,
        modifier = modifier
            .heightIn(min = 48.dp)
            .safeFocusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (controlsVisible) {
                                leftFocusRequester?.safeRequestFocus() ?: true
                            } else {
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            if (controlsVisible) {
                                downFocusRequester?.safeRequestFocus() ?: true
                            } else {
                                false
                            }
                        }
                        Key.DirectionUp -> if (controlsVisible) leftFocusRequester?.safeRequestFocus() ?: true else false
                        Key.DirectionRight -> true
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusProperties {
                if (controlsVisible) {
                    leftFocusRequester.takeIfAttached()?.let { left = it }
                    downFocusRequester.takeIfAttached()?.let { down = it }
                }
            }
            .tvFocusableControl(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = NetflixRed,
                focusedScale = 1.06f,
                focusTag = "player_skip_intro_button",
                onClick = onSkipIntro
            )
            .testTag("player_skip_intro_button")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = null,
                tint = NetflixWhite,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "SALTA INTRO",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                ),
                color = NetflixWhite
            )
        }
    }
}

/**
 * Popup elegante "PROSSIMO EPISODIO" per serie TV:
 * - Mostrato solo se esiste un episodio successivo reale (stessa stagione o stagione successiva).
 * - Supporta conto alla rovescia configurabile e modalita auto-play ON/OFF.
 * - Navigabile al 100% con D-Pad (auto-focus su "RIPRODUCI ORA", frecce sinistra/destra, OK, BACK).
 */
@Composable
internal fun NextEpisodePopupCard(
    session: PlaybackSession,
    nextEpisode: VerifiedNextEpisode,
    autoPlayEnabled: Boolean,
    totalCountdownSec: Int,
    remainingSecProvider: () -> Int,
    playFocusRequester: FocusRequester,
    cancelFocusRequester: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingSec = remainingSecProvider().coerceAtLeast(0)
    val safeTotalSec = totalCountdownSec.coerceAtLeast(1)
    val thumb = nextEpisode.stillUrl ?: session.mediaItem.backdropUrl ?: session.mediaItem.posterUrl
    val thumbRequest = rememberOptimizedImageRequest(
        url = thumb,
        widthDp = ImageTargetSizes.EPISODE_STILL_WIDTH_DP.dp,
        heightDp = ImageTargetSizes.EPISODE_STILL_HEIGHT_DP.dp,
        allowRgb565 = true
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = NetflixCardSurface.copy(alpha = 0.94f)
        ),
        border = BorderStroke(1.5.dp, NetflixWhite.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
        modifier = modifier
            .widthIn(max = 430.dp)
            .fillMaxWidth()
            .testTag("auto_next_episode_card")
    ) {
        Column(modifier = Modifier.focusGroup()) {
            // Linea superiore di accento cinematografico
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(NetflixRed)
            )

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "PROSSIMO EPISODIO",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = NetflixWhite
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (nextEpisode.isCrossSeason) {
                            Surface(
                                color = NetflixMatchGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, NetflixMatchGreen.copy(alpha = 0.7f))
                            ) {
                                Text(
                                    text = "NUOVA STAGIONE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NetflixMatchGreen,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Surface(
                            color = NetflixRed,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "S${nextEpisode.seasonNumber}:E${nextEpisode.episodeNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!thumb.isNullOrBlank()) {
                        AsyncImage(
                            model = thumbRequest,
                            contentDescription = nextEpisode.title ?: "Anteprima prossimo episodio",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 112.dp, height = 64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NetflixDarkSurface)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = session.mediaItem.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = NetflixLightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "STAGIONE ${nextEpisode.seasonNumber} • EPISODIO ${nextEpisode.episodeNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetflixMatchGreen,
                            modifier = Modifier.testTag("next_episode_season_episode")
                        )
                        if (!nextEpisode.title.isNullOrBlank()) {
                            Text(
                                text = nextEpisode.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NetflixWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (autoPlayEnabled) {
                    Text(
                        text = "Il prossimo episodio inizierà tra $remainingSec secondi",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = NetflixWhite.copy(alpha = 0.9f),
                        modifier = Modifier.testTag("next_episode_countdown_text")
                    )
                    LinearProgressIndicator(
                        progress = {
                            ((safeTotalSec - remainingSec).toFloat() / safeTotalSec.toFloat()).coerceIn(0f, 1f)
                        },
                        color = NetflixRed,
                        trackColor = NetflixElevatedSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                } else {
                    Text(
                        text = "Riproduzione automatica disattivata • Avvia manualmente o annulla",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixLightGray,
                        modifier = Modifier.testTag("next_episode_manual_hint")
                    )
                }

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
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1.2f)
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
                        Text(
                            text = "RIPRODUCI ORA",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                        border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(0.8f)
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
                        Text(
                            text = "ANNULLA",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Popup elegante "IL FILM È TERMINATO" per i film:
 * - "TERMINA VISUALIZZAZIONE": segna il film come completato solo per il profilo attivo,
 *   lo rimuove da "Continua a guardare" e torna alla schermata precedente.
 * - "CONTINUA A GUARDARE": chiude il popup e prosegue la visione senza rimuoverlo da "Continua a guardare".
 */
@Composable
internal fun MovieEndedPopupCard(
    movieTitle: String,
    finishFocusRequester: FocusRequester,
    continueFocusRequester: FocusRequester,
    onFinishWatching: () -> Unit,
    onContinueWatching: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = NetflixCardSurface.copy(alpha = 0.94f)
        ),
        border = BorderStroke(1.5.dp, NetflixWhite.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .testTag("movie_ended_popup_card")
    ) {
        Column(modifier = Modifier.focusGroup()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(NetflixRed)
            )

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NetflixMatchGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "IL FILM È TERMINATO",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = NetflixWhite
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = movieTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetflixWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Conferma per segnare il film completato e rimuoverlo da Continua a guardare per questo profilo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixLightGray
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onFinishWatching,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(finishFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp, Key.DirectionLeft -> true
                                        Key.DirectionDown, Key.DirectionRight -> continueFocusRequester.safeRequestFocus()
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                continueFocusRequester.takeIfAttached()?.let {
                                    down = it
                                    right = it
                                }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = NetflixWhite,
                                focusedScale = 1.03f,
                                focusTag = "movie_ended_finish_button"
                            )
                            .testTag("movie_ended_finish_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TERMINA VISUALIZZAZIONE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedButton(
                        onClick = onContinueWatching,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                        border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(continueFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown, Key.DirectionRight -> true
                                        Key.DirectionUp, Key.DirectionLeft -> finishFocusRequester.safeRequestFocus()
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                finishFocusRequester.takeIfAttached()?.let {
                                    up = it
                                    left = it
                                }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = NetflixWhite,
                                focusedScale = 1.03f,
                                focusTag = "movie_ended_continue_button"
                            )
                            .testTag("movie_ended_continue_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CONTINUA A GUARDARE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
