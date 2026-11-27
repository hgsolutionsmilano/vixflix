package com.example.ui.screens

import android.graphics.Bitmap
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
import coil.size.Scale
import coil.size.Size
import coil.transform.Transformation
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
 * Risolve l'URL dell'immagine orizzontale utilizzata nella sezione "Continua a guardare"
 * (`ContinueWatchingCard`: backdrop TMDB `w780`) da usare come sfondo cinematografico
 * durante l'avvio dello streaming.
 *
 * Non effettua mai fallback sulla locandina verticale (`posterPath`): se l'immagine
 * orizzontale manca, restituisce `null` per mostrare uno sfondo scuro elegante e coerente.
 */
internal fun resolveLaunchContinueWatchingBackdropUrl(mediaItem: MediaItem): String? {
    val rawBackdrop = mediaItem.backdropPath?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (rawBackdrop.startsWith("http://") || rawBackdrop.startsWith("https://")) {
        rawBackdrop
    } else {
        val normalizedPath = if (rawBackdrop.startsWith("/")) rawBackdrop else "/$rawBackdrop"
        "https://image.tmdb.org/t/p/w780$normalizedPath"
    }
}

/**
 * Trasformazione Coil pura Kotlin compatibile con tutte le API (minSdk 24+ / Fire OS 7-8 / Android TV 9-14)
 * che applica una leggera sfocatura cinematografica mantenendo l'immagine orizzontale nitida,
 * riconoscibile e a basso impatto in RAM su TV, tablet e dispositivi Fire TV.
 */
internal class CinematicBackdropBlurTransformation(
    private val maxSidePx: Int = 540,
    private val blurRadius: Int = 2,
    private val passes: Int = 1
) : Transformation {

    override val cacheKey: String = "vix_cw_cinematic_blur_${maxSidePx}_r${blurRadius}_p${passes}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val srcW = input.width.coerceAtLeast(1)
        val srcH = input.height.coerceAtLeast(1)
        val scale = (maxSidePx.toFloat() / maxOf(srcW, srcH).toFloat()).coerceAtMost(1f)
        val dstW = (srcW * scale).toInt().coerceAtLeast(2)
        val dstH = (srcH * scale).toInt().coerceAtLeast(2)

        val scaled = Bitmap.createScaledBitmap(input, dstW, dstH, true)
        val working = if (scaled.config == Bitmap.Config.ARGB_8888 && scaled.isMutable) {
            scaled
        } else {
            val copy = scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (scaled !== input) {
                scaled.recycle()
            }
            copy
        } ?: return input

        val effectiveRadius = blurRadius.coerceIn(1, (minOf(dstW, dstH) - 1).coerceAtLeast(1))
        applyFastBoxBlur(working, dstW, dstH, effectiveRadius, passes.coerceIn(1, 3))
        return working
    }

    private fun applyFastBoxBlur(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        radius: Int,
        passes: Int
    ) {
        val pixels = IntArray(width * height)
        val scratch = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val div = radius * 2 + 1
        repeat(passes) {
            // Passaggio orizzontale
            for (y in 0 until height) {
                val rowOffset = y * width
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (i in -radius..radius) {
                    val c = pixels[rowOffset + i.coerceIn(0, width - 1)]
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (x in 0 until width) {
                    val out = (0xFF shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                    scratch[rowOffset + x] = out
                    val removeIdx = rowOffset + (x - radius).coerceIn(0, width - 1)
                    val addIdx = rowOffset + (x + radius + 1).coerceIn(0, width - 1)
                    val oldPx = pixels[removeIdx]
                    val newPx = pixels[addIdx]
                    rSum += ((newPx ushr 16) and 0xFF) - ((oldPx ushr 16) and 0xFF)
                    gSum += ((newPx ushr 8) and 0xFF) - ((oldPx ushr 8) and 0xFF)
                    bSum += (newPx and 0xFF) - (oldPx and 0xFF)
                }
            }

            // Passaggio verticale
            for (x in 0 until width) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (i in -radius..radius) {
                    val c = scratch[i.coerceIn(0, height - 1) * width + x]
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (y in 0 until height) {
                    val out = (0xFF shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                    pixels[y * width + x] = out
                    val removeIdx = (y - radius).coerceIn(0, height - 1) * width + x
                    val addIdx = (y + radius + 1).coerceIn(0, height - 1) * width + x
                    val oldPx = scratch[removeIdx]
                    val newPx = scratch[addIdx]
                    rSum += ((newPx ushr 16) and 0xFF) - ((oldPx ushr 16) and 0xFF)
                    gSum += ((newPx ushr 8) and 0xFF) - ((oldPx ushr 8) and 0xFF)
                    bSum += (newPx and 0xFF) - (oldPx and 0xFF)
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

/**
 * Sfondo cinematografico mostrato sotto al popup di attesa durante l'avvio dello streaming.
 *
 * Gerarchia visiva:
 * 1) Base scura premium (usata anche se l'immagine orizzontale è assente, senza fallback su poster verticale);
 * 2) Immagine orizzontale di "Continua a guardare" (`backdropPath`, `w780`) a tutto schermo con proporzioni
 *    naturali (`ContentScale.Crop` centrato) e leggerissima sfocatura cinematografica che la mantiene riconoscibile;
 * 3) Overlay nero semitrasparente + gradiente scuro di profondità + leggera vignettatura ai bordi;
 * 4) Popup originale `VixStreamLoadingPopup` invariato in primo piano.
 */
@Composable
internal fun VixStreamLaunchPosterBackdrop(
    mediaItem: MediaItem,
    isLowRamDevice: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val backdropUrl = remember(mediaItem.id, mediaItem.mediaType, mediaItem.backdropPath) {
        resolveLaunchContinueWatchingBackdropUrl(mediaItem)
    }
    val blurMaxSidePx = if (isLowRamDevice) 360 else 540
    val blurTransformation = remember(blurMaxSidePx) {
        listOf(
            CinematicBackdropBlurTransformation(
                maxSidePx = blurMaxSidePx,
                blurRadius = 2,
                passes = 1
            )
        )
    }

    var isBackdropReady by remember(backdropUrl) { mutableStateOf(false) }
    var isBackdropFailed by remember(backdropUrl) { mutableStateOf(false) }

    val backdropAlpha by animateFloatAsState(
        targetValue = if (backdropUrl != null && isBackdropReady && !isBackdropFailed) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "stream_launch_cw_backdrop_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .drawBehind {
                val radius = (maxOf(size.width, size.height) * 0.72f).coerceAtLeast(1f)
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to Color(0xFF171720),
                        0.65f to Color(0xFF0C0C10),
                        1.0f to NetflixBlack,
                        center = center,
                        radius = radius
                    )
                )
            }
            .focusProperties { canFocus = false }
            .testTag("stream_launch_poster_backdrop")
    ) {
        if (backdropUrl != null) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
            ) {
                val maxDecodeCapPx = if (isLowRamDevice) 720 else 1080
                val rawWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(120) }
                val rawHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(120) }
                val capScale = (maxDecodeCapPx.toFloat() / maxOf(rawWidthPx, rawHeightPx).toFloat()).coerceAtMost(1f)
                val targetWidthPx = (rawWidthPx * capScale).toInt().coerceAtLeast(120)
                val targetHeightPx = (rawHeightPx * capScale).toInt().coerceAtLeast(120)

                val backdropRequest = remember(context, backdropUrl, targetWidthPx, targetHeightPx, blurMaxSidePx) {
                    ImageTargetSizes.buildRequest(
                        context = context,
                        url = backdropUrl,
                        widthPx = targetWidthPx,
                        heightPx = targetHeightPx,
                        allowRgb565 = false,
                        scale = Scale.FILL,
                        allowHardware = false,
                        memoryCacheKey = "${backdropUrl}_launch_cw_bg_${blurMaxSidePx}",
                        transformations = blurTransformation
                    )
                }

                AsyncImage(
                    model = backdropRequest,
                    contentDescription = mediaItem.title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    onSuccess = {
                        isBackdropFailed = false
                        isBackdropReady = true
                    },
                    onError = {
                        isBackdropReady = false
                        isBackdropFailed = true
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = backdropAlpha }
                        .focusProperties { canFocus = false }
                        .testTag("stream_launch_backdrop_image")
                )
            }
        }

        // Overlay nero semitrasparente + gradiente scuro verticale + leggera vignettatura ai bordi
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NetflixBlack.copy(alpha = 0.40f))
                .background(
                    Brush.verticalGradient(
                        0.0f to NetflixBlack.copy(alpha = 0.42f),
                        0.32f to NetflixBlack.copy(alpha = 0.16f),
                        0.68f to NetflixBlack.copy(alpha = 0.46f),
                        1.0f to NetflixBlack.copy(alpha = 0.86f)
                    )
                )
                .drawBehind {
                    val radius = (maxOf(size.width, size.height) * 0.74f).coerceAtLeast(1f)
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to Color.Transparent,
                            0.55f to NetflixBlack.copy(alpha = 0.18f),
                            1.0f to NetflixBlack.copy(alpha = 0.56f),
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
