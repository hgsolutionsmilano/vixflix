package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
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
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Formatta un tempo in millisecondi nel formato fisso `HH:MM:SS` (es. `00:35:42` / `01:52:18`).
 */
fun formatMsToHhMmSs(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

/**
 * Informazione temporale compatta e pulita subito sotto la barra di avanzamento:
 * `00:35:42 / 01:52:18` (aggiornata in tempo reale durante la riproduzione e durante lo scrubbing).
 */
@Composable
internal fun PlayerCleanTimeDisplay(
    positionMsProvider: () -> Long,
    durationMsProvider: () -> Long,
    modifier: Modifier = Modifier
) {
    val currentMs = positionMsProvider().coerceAtLeast(0L)
    val totalMs = durationMsProvider().coerceAtLeast(0L)
    val formatted = if (totalMs > 0L) {
        "${formatMsToHhMmSs(currentMs)} / ${formatMsToHhMmSs(totalMs)}"
    } else {
        "${formatMsToHhMmSs(currentMs)} / --:--:--"
    }

    Text(
        text = formatted,
        style = MaterialTheme.typography.labelLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.5.sp
        ),
        color = NetflixWhite.copy(alpha = 0.94f),
        maxLines = 1,
        modifier = modifier.testTag("player_time_display")
    )
}

/**
 * Rappresentazione di una traccia audio selezionabile nel player:
 * può corrispondere a una traccia reale esposta da ExoPlayer (`mediaTrackGroup != null`)
 * oppure a una sorgente lingua VixSrc già supportata dall'app (`streamLangCode != null`).
 */
@Immutable
data class PlayerAudioTrackOption(
    val id: String,
    val title: String,
    val details: String? = null,
    val isSelected: Boolean = false,
    val mediaTrackGroup: TrackGroup? = null,
    val trackIndex: Int = -1,
    val streamLangCode: String? = null
)

private fun formatLanguageDisplayName(rawCode: String?): String {
    val clean = rawCode?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (clean.isBlank() || clean == "und") return "Audio Originale"
    val locale = when (clean) {
        "ita", "it" -> Locale.ITALIAN
        "eng", "en" -> Locale.ENGLISH
        "spa", "es" -> Locale("es")
        "fra", "fre", "fr" -> Locale.FRENCH
        "deu", "ger", "de" -> Locale.GERMAN
        "jpn", "ja" -> Locale.JAPANESE
        else -> Locale.forLanguageTag(clean)
    }
    val display = locale.getDisplayLanguage(Locale.ITALIAN)
    val capitalized = display.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.ITALIAN) else ch.toString()
    }
    val isoShort = locale.language.uppercase(Locale.ROOT).ifBlank { clean.uppercase(Locale.ROOT) }
    return if (capitalized.isNotBlank()) "$capitalized ($isoShort)" else clean.uppercase(Locale.ROOT)
}

private fun formatAudioDetails(format: Format): String? {
    val parts = mutableListOf<String>()
    val channelLabel = when (format.channelCount) {
        1 -> "Mono"
        2 -> "Stereo 2.0"
        6 -> "Surround 5.1"
        8 -> "Surround 7.1"
        else -> if (format.channelCount > 0) "${format.channelCount} ch" else null
    }
    if (channelLabel != null) parts.add(channelLabel)

    val codecLabel = when (format.sampleMimeType) {
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "Dolby AC-3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        else -> null
    }
    if (codecLabel != null) parts.add(codecLabel)

    if (format.bitrate > 0) {
        parts.add("${format.bitrate / 1000} kbps")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

/**
 * Estrae le tracce audio reali da Media3 ExoPlayer (quando disponibili nello stream)
 * e garantisce la disponibilità delle lingue audio VixSrc già supportate dall'applicazione.
 */
@OptIn(UnstableApi::class)
internal fun extractPlayerAudioTrackOptions(
    exoPlayer: ExoPlayer,
    currentStreamLang: String
): List<PlayerAudioTrackOption> {
    val result = mutableListOf<PlayerAudioTrackOption>()
    val tracks = exoPlayer.currentTracks
    var groupIdx = 0
    for (group in tracks.groups) {
        if (group.type == C.TRACK_TYPE_AUDIO) {
            for (trackIdx in 0 until group.length) {
                if (!group.isTrackSupported(trackIdx)) continue
                val format = group.getTrackFormat(trackIdx)
                val langTitle = formatLanguageDisplayName(format.language)
                val labelSuffix = format.label
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && !langTitle.contains(it, ignoreCase = true) }
                val title = if (labelSuffix != null) "$langTitle – $labelSuffix" else langTitle
                result.add(
                    PlayerAudioTrackOption(
                        id = "exo_${groupIdx}_$trackIdx",
                        title = title,
                        details = formatAudioDetails(format),
                        isSelected = group.isTrackSelected(trackIdx),
                        mediaTrackGroup = group.mediaTrackGroup,
                        trackIndex = trackIdx
                    )
                )
            }
            groupIdx++
        }
    }

    if (result.isNotEmpty()) {
        return result
    }

    // Fallback coerente sulle lingue stream VixSrc già gestite dall'applicazione (IT / EN)
    val normalizedLang = currentStreamLang.lowercase(Locale.ROOT)
    return listOf(
        PlayerAudioTrackOption(
            id = "lang_it",
            title = "Italiano (IT)",
            details = "Traccia stream principale",
            isSelected = normalizedLang == "it",
            streamLangCode = "it"
        ),
        PlayerAudioTrackOption(
            id = "lang_en",
            title = "Inglese (EN)",
            details = "Traccia lingua originale",
            isSelected = normalizedLang == "en",
            streamLangCode = "en"
        )
    )
}

@OptIn(UnstableApi::class)
internal fun applyAudioTrackSelection(
    exoPlayer: ExoPlayer,
    option: PlayerAudioTrackOption,
    onToggleLanguage: (String) -> Unit
) {
    val group = option.mediaTrackGroup
    if (group != null && option.trackIndex >= 0) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group, option.trackIndex))
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
    } else if (!option.streamLangCode.isNullOrBlank()) {
        onToggleLanguage(option.streamLangCode)
    }
}

/**
 * Fila minimalista di ESCLUSIVAMENTE TRE pulsanti circolari sotto la barra temporale:
 * 1) Play / Pausa
 * 2) Audio (apre selettore tracce audio)
 * 3) Link VixSrc (gestione collegamento VixSrc esistente)
 */
@Composable
internal fun PlayerCircularControlsRow(
    isPlaying: Boolean,
    playPauseFocusRequester: FocusRequester,
    audioFocusRequester: FocusRequester,
    vixLinkFocusRequester: FocusRequester,
    upToTimeBarFocusRequester: FocusRequester,
    onTogglePlayPause: () -> Unit,
    onOpenAudioSelector: () -> Unit,
    onOpenVixLinkPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .focusGroup()
            .testTag("player_controls_row"),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. PLAY / PAUSA
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .size(56.dp)
                .safeFocusRequester(playPauseFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp -> upToTimeBarFocusRequester.safeRequestFocus()
                            Key.DirectionRight -> audioFocusRequester.safeRequestFocus()
                            Key.DirectionLeft, Key.DirectionDown -> true
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .focusProperties {
                    upToTimeBarFocusRequester.takeIfAttached()?.let { up = it }
                    audioFocusRequester.takeIfAttached()?.let { right = it }
                }
                .tvFocusableControl(
                    shape = CircleShape,
                    focusedBorderColor = NetflixWhite,
                    focusedScale = 1.12f,
                    focusTag = "exo_play_pause_button"
                )
                .clip(CircleShape)
                .background(NetflixRed)
                .testTag("exo_play_pause_button")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        // 2. AUDIO
        Surface(
            color = NetflixCardSurface.copy(alpha = 0.88f),
            shape = CircleShape,
            border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.28f)),
            modifier = Modifier
                .size(52.dp)
                .safeFocusRequester(audioFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp -> upToTimeBarFocusRequester.safeRequestFocus()
                            Key.DirectionLeft -> playPauseFocusRequester.safeRequestFocus()
                            Key.DirectionRight -> vixLinkFocusRequester.safeRequestFocus()
                            Key.DirectionDown -> true
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .focusProperties {
                    upToTimeBarFocusRequester.takeIfAttached()?.let { up = it }
                    playPauseFocusRequester.takeIfAttached()?.let { left = it }
                    vixLinkFocusRequester.takeIfAttached()?.let { right = it }
                }
                .tvFocusableControl(
                    shape = CircleShape,
                    focusedBorderColor = NetflixWhite,
                    focusedScale = 1.12f,
                    focusTag = "player_audio_button",
                    onClick = onOpenAudioSelector
                )
                .testTag("player_audio_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = "Seleziona traccia audio",
                    tint = NetflixWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 3. LINK VIXSRC
        Surface(
            color = NetflixCardSurface.copy(alpha = 0.88f),
            shape = CircleShape,
            border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.28f)),
            modifier = Modifier
                .size(52.dp)
                .safeFocusRequester(vixLinkFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp -> upToTimeBarFocusRequester.safeRequestFocus()
                            Key.DirectionLeft -> audioFocusRequester.safeRequestFocus()
                            Key.DirectionRight, Key.DirectionDown -> true
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .focusProperties {
                    upToTimeBarFocusRequester.takeIfAttached()?.let { up = it }
                    audioFocusRequester.takeIfAttached()?.let { left = it }
                }
                .tvFocusableControl(
                    shape = CircleShape,
                    focusedBorderColor = NetflixWhite,
                    focusedScale = 1.12f,
                    focusTag = "player_vixsrc_link_button",
                    onClick = onOpenVixLinkPanel
                )
                .testTag("player_vixsrc_link_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Collegamento VixSrc",
                    tint = NetflixWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Selettore D-Pad delle tracce audio disponibili nel player.
 */
@Composable
internal fun PlayerAudioSelectorPanel(
    tracks: List<PlayerAudioTrackOption>,
    onSelectTrack: (PlayerAudioTrackOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemFocusRequesters = remember(tracks.size) {
        List(tracks.size) { FocusRequester() }
    }
    val initialFocusIndex = remember(tracks) {
        tracks.indexOfFirst { it.isSelected }.takeIf { it >= 0 } ?: 0
    }

    LaunchedEffect(tracks.size) {
        if (itemFocusRequesters.isNotEmpty()) {
            delay(50L)
            itemFocusRequesters[initialFocusIndex.coerceIn(itemFocusRequesters.indices)].safeRequestFocus()
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.5.dp, NetflixWhite.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .testTag("player_audio_selector_panel")
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "TRACCE AUDIO",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = NetflixWhite
                )
            }

            tracks.forEachIndexed { index, track ->
                val requester = itemFocusRequesters[index]
                val prevRequester = itemFocusRequesters.getOrNull(index - 1)
                val nextRequester = itemFocusRequesters.getOrNull(index + 1)

                Surface(
                    color = if (track.isSelected) {
                        NetflixRed.copy(alpha = 0.2f)
                    } else {
                        NetflixDarkSurface.copy(alpha = 0.9f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (track.isSelected) NetflixRed else NetflixElevatedSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .safeFocusRequester(requester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> prevRequester?.safeRequestFocus() ?: true
                                    Key.DirectionDown -> nextRequester?.safeRequestFocus() ?: true
                                    Key.DirectionLeft, Key.DirectionRight -> true
                                    Key.Escape -> {
                                        onDismiss()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            prevRequester.takeIfAttached()?.let { up = it }
                            nextRequester.takeIfAttached()?.let { down = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(12.dp),
                            focusedBorderColor = NetflixWhite,
                            focusedScale = 1.03f,
                            focusTag = "player_audio_option_${track.id}",
                            onClick = { onSelectTrack(track) }
                        )
                        .testTag("player_audio_option_${track.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetflixWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!track.details.isNullOrBlank()) {
                                Text(
                                    text = track.details,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixLightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (track.isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Traccia attiva",
                                tint = NetflixMatchGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pannello compatto D-Pad per visualizzare, copiare o aprire il collegamento VixSrc già associato
 * alla riproduzione corrente (senza modificare l'integrazione o gli endpoint VixSrc esistenti).
 */
@Composable
internal fun PlayerVixSrcLinkPanel(
    vixSrcUrl: String,
    onOpenExternalLink: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openFocusRequester = remember { FocusRequester() }
    val copyFocusRequester = remember { FocusRequester() }
    var copiedFeedback by remember(vixSrcUrl) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50L)
        openFocusRequester.safeRequestFocus()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.5.dp, NetflixWhite.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .testTag("player_vixsrc_link_panel")
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "COLLEGAMENTO VIXSRC",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = NetflixWhite
                )
            }

            Surface(
                color = NetflixDarkSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = vixSrcUrl,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NetflixLightGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("vixsrc_link_url_text")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenExternalLink,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(openFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionRight -> copyFocusRequester.safeRequestFocus()
                                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft -> true
                                    Key.Escape -> {
                                        onDismiss()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            copyFocusRequester.takeIfAttached()?.let { right = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = NetflixWhite,
                            focusedScale = 1.04f,
                            focusTag = "vixsrc_open_link_button"
                        )
                        .testTag("vixsrc_open_link_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apri Link", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        copiedFeedback = true
                        onCopyLink()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                    border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(copyFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> openFocusRequester.safeRequestFocus()
                                    Key.DirectionUp, Key.DirectionDown, Key.DirectionRight -> true
                                    Key.Escape -> {
                                        onDismiss()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            openFocusRequester.takeIfAttached()?.let { left = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = NetflixWhite,
                            focusedScale = 1.04f,
                            focusTag = "vixsrc_copy_link_button"
                        )
                        .testTag("vixsrc_copy_link_button")
                ) {
                    Icon(
                        imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = if (copiedFeedback) NetflixMatchGreen else NetflixWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (copiedFeedback) "Copiato" else "Copia Link",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Anteprima in miniatura del fotogramma reale durante lo spostamento manuale sulla barra temporale.
 * - Riutilizza Media3 ExoPlayer (motore già presente nel progetto) con un'istanza secondaria muta
 *   e leggera a bassa risoluzione (320x180, audio/testo disattivati, SeekParameters.CLOSEST_SYNC).
 * - Visualizza ESCLUSIVAMENTE la miniatura dell'immagine (nessun titolo, testo, badge o popup).
 * - Se non è possibile decodificare un fotogramma reale attendibile, non mostra alcuna immagine
 *   casuale o segnaposto.
 * - Sparisce immediatamente al rilascio del cursore.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun SeekThumbnailPreviewStrip(
    isScrubbing: Boolean,
    previewPositionMsProvider: () -> Long,
    durationMsProvider: () -> Long,
    streamUrl: String?,
    isDirectHls: Boolean,
    okHttpDataSourceFactory: OkHttpDataSource.Factory,
    previewBitmapProvider: ((Long) -> Bitmap?)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewPosMs = previewPositionMsProvider().coerceAtLeast(0L)
    val durationMs = durationMsProvider().coerceAtLeast(1L)
    val progressFraction = (previewPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    // Se è fornito un provider deterministico di bitmap reali (es. frame estratti o test), usalo direttamente
    if (previewBitmapProvider != null) {
        if (!isScrubbing) return
        val frameBitmap = remember(previewPosMs, previewBitmapProvider) {
            previewBitmapProvider(previewPosMs)
        } ?: return

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .height(99.dp)
        ) {
            val thumbWidth = 176.dp
            val maxOffsetPx = (constraints.maxWidth - with(androidx.compose.ui.platform.LocalDensity.current) { thumbWidth.roundToPx() })
                .coerceAtLeast(0)
            val offsetX = (maxOffsetPx * progressFraction).roundToInt()

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black,
                border = BorderStroke(1.5.dp, NetflixWhite.copy(alpha = 0.85f)),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .offset { IntOffset(offsetX, 0) }
                    .size(width = thumbWidth, height = 99.dp)
                    .testTag("player_seek_thumbnail_preview")
            ) {
                Image(
                    bitmap = frameBitmap.asImageBitmap(),
                    contentDescription = "Anteprima fotogramma",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    // Istanziazione lazy del decoder Media3 di anteprima SOLO quando lo stream HLS diretto è disponibile
    // e solo mentre l'utente sta effettuando una ricerca manuale
    if (!isScrubbing || !isDirectHls || streamUrl.isNullOrBlank()) return

    var hasVerifiedFrame by remember(streamUrl) { mutableStateOf(false) }
    var previewFailed by remember(streamUrl) { mutableStateOf(false) }

    val previewPlayer = remember(context, streamUrl, okHttpDataSourceFactory) {
        runCatching {
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setMaxVideoSize(320, 180)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .setForceLowestBitrate(true)
                    .build()
            }
            val tinyLoadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_500, 4_000, 400, 800)
                .setTargetBufferBytes(2 * 1024 * 1024)
                .build()
            ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setLoadControl(tinyLoadControl)
                .build()
                .apply {
                    volume = 0f
                    playWhenReady = false
                    setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    val mediaItem = ExoMediaItem.Builder()
                        .setUri(Uri.parse(streamUrl))
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                    val source = HlsMediaSource.Factory(okHttpDataSourceFactory)
                        .setAllowChunklessPreparation(true)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1))
                        .createMediaSource(mediaItem)
                    setMediaSource(source, previewPosMs)
                    prepare()
                }
        }.getOrNull()
    }

    if (previewPlayer == null || previewFailed) return

    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasVerifiedFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                hasVerifiedFrame = false
                previewFailed = true
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
        }
    }

    LaunchedEffect(previewPlayer, previewPosMs) {
        // Piccola coalescenza per mantenere fluido lo scrubbing rapido senza saturare il decoder
        delay(45L)
        runCatching {
            previewPlayer.seekTo(previewPosMs)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .height(99.dp)
    ) {
        val thumbWidth = 176.dp
        val maxOffsetPx = (constraints.maxWidth - with(androidx.compose.ui.platform.LocalDensity.current) { thumbWidth.roundToPx() })
            .coerceAtLeast(0)
        val offsetX = (maxOffsetPx * progressFraction).roundToInt()

        // Il TextureView resta collegato per decodificare il primo fotogramma reale;
        // resta completamente invisibile (alpha 0) finché non è stato decodificato un frame autentico.
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (hasVerifiedFrame) Color.Black else Color.Transparent,
            border = if (hasVerifiedFrame) BorderStroke(1.5.dp, NetflixWhite.copy(alpha = 0.85f)) else null,
            shadowElevation = if (hasVerifiedFrame) 14.dp else 0.dp,
            modifier = Modifier
                .offset { IntOffset(offsetX, 0) }
                .size(width = thumbWidth, height = 99.dp)
                .then(
                    if (hasVerifiedFrame) {
                        Modifier.testTag("player_seek_thumbnail_preview")
                    } else {
                        Modifier
                    }
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        alpha = if (hasVerifiedFrame) 1f else 0f
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        previewPlayer.setVideoTextureView(this)
                    }
                },
                update = { view ->
                    view.alpha = if (hasVerifiedFrame) 1f else 0f
                },
                onRelease = { view ->
                    previewPlayer.clearVideoTextureView(view)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
            )
        }
    }
}
