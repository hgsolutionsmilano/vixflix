package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.MediaItem
import com.example.ui.components.DpadFocusScope
import com.example.ui.components.DpadFocusScopeProvider
import com.example.ui.components.formatSecondsToMmSs
import com.example.ui.components.recoverFocusFromCandidates
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

@Composable
internal fun TvContinueWatchingQuickDialog(
    entry: WatchProgressEntity,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onOpenDetails: () -> Unit,
    onRestart: () -> Unit,
    onRemove: () -> Unit
) {
    val resumeFocus = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    val restartFocus = remember { FocusRequester() }
    val removeFocus = remember { FocusRequester() }

    LaunchedEffect(entry.id) {
        for (attempt in 1..4) {
            withFrameNanos { }
            if (resumeFocus.safeRequestFocus()) break
        }
        delay(60L)
        resumeFocus.safeRequestFocus()
    }

    DpadFocusScopeProvider(
        scope = DpadFocusScope.MODAL_QUICK_ACTION,
        onRecoverFocus = {
            recoverFocusFromCandidates(
                "quick_cw_resume_button" to resumeFocus,
                "quick_cw_details_button" to detailsFocus,
                "quick_cw_restart_button" to restartFocus,
                "quick_cw_remove_button" to removeFocus
            )
        }
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                border = BorderStroke(1.5.dp, NetflixElevatedSurface),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag("quick_cw_dialog")
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
                            .safeFocusRequester(resumeFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> detailsFocus.safeRequestFocus()
                                        Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                detailsFocus.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = NetflixRed,
                                focusedScale = 1.03f,
                                focusTag = "quick_cw_resume_button"
                            )
                            .testTag("quick_cw_resume_button")
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
                            .safeFocusRequester(detailsFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> resumeFocus.safeRequestFocus()
                                        Key.DirectionDown -> restartFocus.safeRequestFocus()
                                        Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                resumeFocus.takeIfAttached()?.let { up = it }
                                restartFocus.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.03f,
                                focusTag = "quick_cw_details_button"
                            )
                            .testTag("quick_cw_details_button")
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
                            .safeFocusRequester(restartFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> detailsFocus.safeRequestFocus()
                                        Key.DirectionDown -> removeFocus.safeRequestFocus()
                                        Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                detailsFocus.takeIfAttached()?.let { up = it }
                                removeFocus.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.03f,
                                focusTag = "quick_cw_restart_button"
                            )
                            .testTag("quick_cw_restart_button")
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
                            .safeFocusRequester(removeFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> restartFocus.safeRequestFocus()
                                        Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                restartFocus.takeIfAttached()?.let { up = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = NetflixRed,
                                focusedScale = 1.03f,
                                focusTag = "quick_cw_remove_button"
                            )
                            .testTag("quick_cw_remove_button")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rimuovi da Continua a guardare")
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvPosterQuickActionDialog(
    item: MediaItem,
    isInWatchlist: Boolean,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit,
    onQuickPlay: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val playFocus = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    val watchlistFocus = remember { FocusRequester() }

    LaunchedEffect(item.uniqueKey) {
        for (attempt in 1..4) {
            withFrameNanos { }
            if (playFocus.safeRequestFocus()) break
        }
        delay(60L)
        playFocus.safeRequestFocus()
    }

    DpadFocusScopeProvider(
        scope = DpadFocusScope.MODAL_QUICK_ACTION,
        onRecoverFocus = {
            recoverFocusFromCandidates(
                "quick_poster_play_button" to playFocus,
                "quick_poster_details_button" to detailsFocus,
                "quick_poster_watchlist_button" to watchlistFocus
            )
        }
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                border = BorderStroke(1.5.dp, NetflixElevatedSurface),
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag("quick_poster_dialog")
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
                            .safeFocusRequester(playFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> detailsFocus.safeRequestFocus()
                                        Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                detailsFocus.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = NetflixRed,
                                focusedScale = 1.03f,
                                focusTag = "quick_poster_play_button"
                            )
                            .testTag("quick_poster_play_button")
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
                            .safeFocusRequester(detailsFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> playFocus.safeRequestFocus()
                                        Key.DirectionDown -> watchlistFocus.safeRequestFocus()
                                        Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                playFocus.takeIfAttached()?.let { up = it }
                                watchlistFocus.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.03f,
                                focusTag = "quick_poster_details_button"
                            )
                            .testTag("quick_poster_details_button")
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
                            .safeFocusRequester(watchlistFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> detailsFocus.safeRequestFocus()
                                        Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                detailsFocus.takeIfAttached()?.let { up = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.03f,
                                focusTag = "quick_poster_watchlist_button"
                            )
                            .testTag("quick_poster_watchlist_button")
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
}
