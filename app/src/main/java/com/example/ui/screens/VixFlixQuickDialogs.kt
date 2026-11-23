package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbEpisodeDto
import com.example.ui.components.DpadFocusScope
import com.example.ui.components.DpadFocusScopeProvider
import com.example.ui.components.consumeBackAndEscapeKey
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
                    .consumeBackAndEscapeKey(onBack = onDismiss)
                    .testTag("quick_cw_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
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
                    .consumeBackAndEscapeKey(onBack = onDismiss)
                    .testTag("quick_poster_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
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

/**
 * Popup di conferma uscita dall'applicazione mostrato sulla schermata principale alla pressione di BACK.
 * Il focus iniziale è posizionato su "ANNULLA" per prevenire chiusure accidentali.
 */
@Composable
internal fun VixFlixExitConfirmationDialog(
    onCancel: () -> Unit,
    onConfirmExit: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }
    val exitFocus = remember { FocusRequester() }
    var actionDispatched by remember { mutableStateOf(false) }

    val safeCancel: () -> Unit = {
        if (!actionDispatched) {
            actionDispatched = true
            onCancel()
        }
    }
    val safeConfirmExit: () -> Unit = {
        if (!actionDispatched) {
            actionDispatched = true
            onConfirmExit()
        }
    }

    LaunchedEffect(Unit) {
        for (frame in 1..4) {
            withFrameNanos { }
            if (cancelFocus.safeRequestFocus()) break
        }
        delay(50L)
        if (!actionDispatched) {
            cancelFocus.safeRequestFocus()
        }
    }

    Dialog(
        onDismissRequest = safeCancel,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        DpadFocusScopeProvider(
            scope = DpadFocusScope.MODAL_QUICK_ACTION,
            onRecoverFocus = {
                recoverFocusFromCandidates(
                    "exit_dialog_cancel_button" to cancelFocus,
                    "exit_dialog_confirm_button" to exitFocus
                )
            }
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                border = BorderStroke(1.5.dp, NetflixElevatedSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .consumeBackAndEscapeKey(onBack = safeCancel)
                    .testTag("vixflix_exit_confirmation_dialog")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 26.dp)
                        .focusGroup()
                ) {
                    Text(
                        text = "VUOI USCIRE DA VIXFLIX?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = NetflixWhite,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = safeCancel,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                            border = BorderStroke(1.dp, NetflixElevatedSurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .safeFocusRequester(cancelFocus)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionRight -> exitFocus.safeRequestFocus()
                                            Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> true
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusProperties {
                                    exitFocus.takeIfAttached()?.let { right = it }
                                }
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedScale = 1.04f,
                                    focusedBorderColor = NetflixWhite,
                                    focusTag = "exit_dialog_cancel_button"
                                )
                                .testTag("exit_dialog_cancel_button")
                        ) {
                            Text(
                                text = "ANNULLA",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = safeConfirmExit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NetflixRed,
                                contentColor = NetflixWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .safeFocusRequester(exitFocus)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionLeft -> cancelFocus.safeRequestFocus()
                                            Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusProperties {
                                    cancelFocus.takeIfAttached()?.let { left = it }
                                }
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedScale = 1.04f,
                                    focusedBorderColor = NetflixWhite,
                                    focusTag = "exit_dialog_confirm_button"
                                )
                                .testTag("exit_dialog_confirm_button")
                        ) {
                            Text(
                                text = "ESCI",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Menu contestuale attivabile con pressione prolungata del tasto OK su un episodio
 * nella schermata di dettaglio delle serie TV.
 */
@Composable
internal fun TvEpisodeWatchedContextDialog(
    episode: TmdbEpisodeDto,
    seasonEpisodeCount: Int = 0,
    isEpisodeWatched: Boolean = false,
    onMarkEpisodeWatched: () -> Unit,
    onUnmarkEpisodeWatched: () -> Unit = {},
    onMarkSeasonWatched: () -> Unit,
    onDismiss: () -> Unit
) {
    val markEpFocus = remember { FocusRequester() }
    val markSeasonFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }

    LaunchedEffect(episode.seasonNumber, episode.episodeNumber) {
        for (frame in 1..4) {
            withFrameNanos { }
            if (markEpFocus.safeRequestFocus()) break
        }
        delay(50L)
        markEpFocus.safeRequestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        DpadFocusScopeProvider(
            scope = DpadFocusScope.MODAL_QUICK_ACTION,
            onRecoverFocus = {
                recoverFocusFromCandidates(
                    "tv_episode_ctx_mark_episode" to markEpFocus,
                    "tv_episode_ctx_mark_season" to markSeasonFocus,
                    "tv_episode_ctx_cancel" to cancelFocus
                )
            }
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                border = BorderStroke(1.5.dp, NetflixElevatedSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .consumeBackAndEscapeKey(onBack = onDismiss)
                    .testTag("tv_episode_watched_context_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "S${episode.seasonNumber} · E${episode.episodeNumber} — ${episode.name ?: "Episodio ${episode.episodeNumber}"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetflixWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (seasonEpisodeCount > 0) {
                        Text(
                            text = "Stagione ${episode.seasonNumber} ($seasonEpisodeCount episodi)",
                            style = MaterialTheme.typography.labelMedium,
                            color = NetflixLightGray
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val toggleEpisodeAction = if (isEpisodeWatched) {
                        onUnmarkEpisodeWatched
                    } else {
                        onMarkEpisodeWatched
                    }

                    Button(
                        onClick = toggleEpisodeAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixWhite,
                            contentColor = NetflixBlack
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .safeFocusRequester(markEpFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> markSeasonFocus.safeRequestFocus()
                                        Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                markSeasonFocus.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.03f,
                                focusedBorderColor = NetflixRed,
                                focusTag = "tv_episode_ctx_mark_episode"
                            )
                            .testTag("tv_episode_ctx_mark_episode")
                    ) {
                        Icon(
                            imageVector = if (isEpisodeWatched) Icons.Default.Close else Icons.Default.Check,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEpisodeWatched) {
                                "SEGNA EPISODIO COME NON VISTO"
                            } else {
                                "SEGNA EPISODIO COME VISTO"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onMarkSeasonWatched,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                        border = BorderStroke(1.dp, NetflixElevatedSurface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .safeFocusRequester(markSeasonFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> markEpFocus.safeRequestFocus()
                                        Key.DirectionDown -> cancelFocus.safeRequestFocus()
                                        Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                markEpFocus.takeIfAttached()?.let { up = it }
                                cancelFocus.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.03f,
                                focusedBorderColor = NetflixWhite,
                                focusTag = "tv_episode_ctx_mark_season"
                            )
                            .testTag("tv_episode_ctx_mark_season")
                    ) {
                        Icon(imageVector = Icons.Default.DoneAll, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SEGNA INTERA STAGIONE COME VISTA",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixLightGray),
                        border = BorderStroke(1.dp, NetflixElevatedSurface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .safeFocusRequester(cancelFocus)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> markSeasonFocus.safeRequestFocus()
                                        Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                markSeasonFocus.takeIfAttached()?.let { up = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.03f,
                                focusedBorderColor = NetflixWhite,
                                focusTag = "tv_episode_ctx_cancel"
                            )
                            .testTag("tv_episode_ctx_cancel")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ANNULLA",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

