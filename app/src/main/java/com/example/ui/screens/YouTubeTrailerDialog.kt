package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbTrailerResolver
import com.example.data.remote.YouTubeTrailerCandidate
import com.example.ui.TrailerPlaybackState
import com.example.ui.components.TvSeekTimeBar
import com.example.ui.components.animatedPosterShowcase
import com.example.ui.components.formatSecondsToMmSs
import com.example.ui.components.rememberIsTvDevice
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixGold
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/**
 * Modale cinematografico centrato 16:9 dedicato esclusivamente ai trailer recuperati direttamente da YouTube.
 * - Mostra prima i trailer in Italiano (ITA) poi in Inglese / originale (ENG) in una lista selezionabile.
 * - Gestisce errori espliciti con Riprova, Cambio trailer e Chiudi.
 * - Non contiene alcuna azione per avviare lo streaming completo del film o serie TV.
 */
@OptIn(UnstableApi::class)
@Composable
fun YouTubeTrailerDialog(
    mediaItem: MediaItem,
    trailerState: TrailerPlaybackState,
    onSelectTrailer: (YouTubeTrailerCandidate) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val resolved = trailerState.resolvedTrailer
    val selectedVideo = trailerState.selectedVideo
    val activeKey = selectedVideo?.videoId ?: "searching"
    val useWebEmbed = resolved?.isYouTubeEmbedFallback == true && !resolved.embedVideoId.isNullOrBlank()

    var isBuffering by remember(activeKey) { mutableStateOf(trailerState.isLoadingStream) }
    var isPlaying by remember(activeKey) { mutableStateOf(false) }
    var currentPosMs by remember(activeKey) { mutableLongStateOf(0L) }
    var durationMs by remember(activeKey) { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackError by remember(activeKey, trailerState.errorMessage) {
        mutableStateOf(trailerState.errorMessage)
    }
    var interactionTick by remember { mutableLongStateOf(0L) }
    var isRemoteScrubbing by remember { mutableStateOf(false) }
    var embedRetryNonce by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val isTvDevice = rememberIsTvDevice()
    val playPauseFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }
    val playlistState = rememberLazyListState()

    val trailerOkHttpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val okHttpDataSourceFactory = remember(trailerOkHttpClient, resolved?.referer, resolved?.userAgent) {
        val factory = OkHttpDataSource.Factory(trailerOkHttpClient)
            .setUserAgent(resolved?.userAgent ?: "Mozilla/5.0")
        val ref = resolved?.referer.orEmpty()
        if (ref.isNotBlank()) {
            factory.setDefaultRequestProperties(mapOf("Referer" to ref))
        }
        factory
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    val togglePlayPause: () -> Unit = {
        if (useWebEmbed) {
            val js = if (isPlaying) {
                "window.vixPause && window.vixPause();"
            } else {
                "window.vixPlay && window.vixPlay();"
            }
            webViewRef?.evaluateJavascript(js, null)
        } else {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
        interactionTick++
    }

    val playAction: () -> Unit = {
        if (useWebEmbed) {
            webViewRef?.evaluateJavascript("window.vixPlay && window.vixPlay();", null)
        } else {
            exoPlayer.play()
        }
        interactionTick++
    }

    val pauseAction: () -> Unit = {
        if (useWebEmbed) {
            webViewRef?.evaluateJavascript("window.vixPause && window.vixPause();", null)
        } else {
            exoPlayer.pause()
        }
        interactionTick++
    }

    val seekToAction: (Long) -> Unit = { targetMs ->
        val clamped = targetMs.coerceAtLeast(0L).let { t ->
            if (durationMs > 0L) t.coerceAtMost(durationMs) else t
        }
        currentPosMs = clamped
        if (useWebEmbed) {
            val sec = clamped / 1000.0
            webViewRef?.evaluateJavascript("window.vixSeekTo && window.vixSeekTo($sec);", null)
        } else {
            exoPlayer.seekTo(clamped)
        }
        interactionTick++
    }

    // Prepara o ferma ExoPlayer / IFrame Embed in base allo stato della ricerca e dello stream selezionato
    LaunchedEffect(
        resolved?.streamUrl,
        resolved?.audioStreamUrl,
        resolved?.isYouTubeEmbedFallback,
        resolved?.embedVideoId,
        trailerState.isSearching,
        trailerState.isLoadingStream,
        trailerState.errorMessage,
        okHttpDataSourceFactory
    ) {
        if (trailerState.isSearching || trailerState.isLoadingStream) {
            isBuffering = true
            isPlaying = false
            playbackError = null
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }

        if (!trailerState.errorMessage.isNullOrBlank()) {
            isBuffering = false
            isPlaying = false
            showControls = true
            playbackError = trailerState.errorMessage
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }

        if (resolved == null || resolved.streamUrl.isBlank()) {
            isBuffering = false
            isPlaying = false
            showControls = true
            playbackError = "Trailer non disponibile o non riproducibile su YouTube."
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }

        if (resolved.isYouTubeEmbedFallback && !resolved.embedVideoId.isNullOrBlank()) {
            // Modalità ufficiale YouTube IFrame Embed: ferma ExoPlayer per evitare conflitti
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            playbackError = null
            isBuffering = true
            isPlaying = false
            currentPosMs = 0L
            durationMs = (selectedVideo?.durationSeconds ?: 0).toLong() * 1000L
            return@LaunchedEffect
        }

        playbackError = null
        isBuffering = true

        val videoUri = Uri.parse(resolved.streamUrl)
        val itemBuilder = ExoMediaItem.Builder().setUri(videoUri)
        if (resolved.isHls) {
            itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        val primarySource = if (resolved.isHls) {
            HlsMediaSource.Factory(okHttpDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(itemBuilder.build())
        } else {
            ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
                .createMediaSource(itemBuilder.build())
        }

        val finalMediaSource = if (!resolved.audioStreamUrl.isNullOrBlank() && !resolved.isHls) {
            val audioSource = ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
                .createMediaSource(ExoMediaItem.Builder().setUri(Uri.parse(resolved.audioStreamUrl)).build())
            MergingMediaSource(primarySource, audioSource)
        } else {
            primarySource
        }

        exoPlayer.setMediaSource(finalMediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Watchdog anti-blocco: dopo 15 secondi in buffering mostra errore esplicito
    LaunchedEffect(
        resolved?.streamUrl,
        useWebEmbed,
        embedRetryNonce,
        isBuffering,
        trailerState.isSearching,
        trailerState.isLoadingStream,
        playbackError
    ) {
        if (!trailerState.isSearching && !trailerState.isLoadingStream && isBuffering && playbackError == null && resolved != null) {
            delay(15_000L)
            val stillStuck = if (useWebEmbed) {
                isBuffering && !isPlaying && playbackError == null
            } else {
                isBuffering && playbackError == null && exoPlayer.playbackState != Player.STATE_READY
            }
            if (stillStuck) {
                if (!useWebEmbed) {
                    exoPlayer.stop()
                }
                isBuffering = false
                isPlaying = false
                showControls = true
                playbackError = "Timeout di caricamento del video YouTube. Prova un altro trailer dalla lista o chiudi."
            }
        }
    }

    // Aggiornamento timeline per ExoPlayer diretto
    LaunchedEffect(exoPlayer, useWebEmbed) {
        if (useWebEmbed) return@LaunchedEffect
        while (true) {
            delay(500L)
            if (!isRemoteScrubbing) {
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            val dur = exoPlayer.duration
            if (dur > 0L) {
                durationMs = dur
            }
            if (exoPlayer.isPlaying && isBuffering) {
                isBuffering = false
            }
        }
    }

    // Auto-scomparsa controlli interni sul riquadro 16:9
    LaunchedEffect(showControls, isPlaying, isBuffering, isRemoteScrubbing, trailerState.isSearching, trailerState.isLoadingStream, interactionTick) {
        if (showControls && isPlaying && !isBuffering && !isRemoteScrubbing && !trailerState.isSearching && !trailerState.isLoadingStream) {
            delay(3800L)
            showControls = false
        }
    }

    // Focus automatico Android TV su Errore o Chiudi / Play
    LaunchedEffect(playbackError, trailerState.isSearching, trailerState.isLoadingStream) {
        if (!isTvDevice) return@LaunchedEffect
        if (!playbackError.isNullOrBlank()) {
            runCatching { retryFocusRequester.requestFocus() }
        } else if (!trailerState.isSearching && !trailerState.isLoadingStream) {
            runCatching { playPauseFocusRequester.requestFocus() }
        } else {
            runCatching { closeFocusRequester.requestFocus() }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (useWebEmbed || !playbackError.isNullOrBlank()) {
                    if (!useWebEmbed) isBuffering = false
                    return
                }
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                    isBuffering = false
                    val dur = exoPlayer.duration
                    if (dur > 0L) {
                        durationMs = dur
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isBuffering = false
                    isPlaying = false
                    showControls = true
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (useWebEmbed) return
                isPlaying = playing
                if (playing) {
                    isBuffering = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (useWebEmbed) return
                isBuffering = false
                isPlaying = false
                showControls = true
                playbackError = "Impossibile riprodurre questo video YouTube (${error.errorCodeName}). Scegli un altro trailer o chiudi."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // Scrim scuro cinematografico centrato
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NetflixBlack.copy(alpha = 0.88f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .onPreviewKeyEvent { keyEvent ->
                    interactionTick++
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.MediaPlayPause -> {
                                togglePlayPause()
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaPlay -> {
                                playAction()
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaPause -> {
                                pauseAction()
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaFastForward -> {
                                seekToAction(currentPosMs + 10_000L)
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaRewind -> {
                                seekToAction(currentPosMs - 10_000L)
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                            else -> Unit
                        }
                    }
                    false
                }
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .testTag("in_app_trailer_overlay"),
            contentAlignment = Alignment.Center
        ) {
            var cardEntered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                cardEntered = true
            }

            // Card Modale Cinematografica Centrata con ingresso morbido
            androidx.compose.animation.AnimatedVisibility(
                visible = cardEntered,
                enter = fadeIn(tween(220)) +
                    scaleIn(
                        initialScale = 0.91f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) +
                    slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 12 },
                modifier = Modifier
                    .widthIn(max = 880.dp)
                    .fillMaxWidth(0.96f)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Blocca la propagazione click verso lo scrim esterno
                        )
                        .testTag("youtube_trailer_modal_card")
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Linea accento superiore rossa cinematografica
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(NetflixRed, NetflixGold, NetflixRed)
                                )
                            )
                    )

                    // 1. HEADER MODALE: Badge YouTube + Lingua + Titolo + Toggle Aspetto + Chiudi
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = NetflixRed,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SmartDisplay,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "YOUTUBE TRAILER",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                if (selectedVideo != null) {
                                    Surface(
                                        color = if (selectedVideo.languageLabel == "ITA") {
                                            NetflixMatchGreen.copy(alpha = 0.2f)
                                        } else {
                                            NetflixElevatedSurface
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (selectedVideo.languageLabel == "ITA") NetflixMatchGreen else NetflixLightGray.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Text(
                                            text = selectedVideo.languageLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedVideo.languageLabel == "ITA") NetflixMatchGreen else NetflixWhite,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = mediaItem.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NetflixLightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = selectedVideo?.title ?: if (trailerState.isSearching) {
                                    "Ricerca trailer su YouTube…"
                                } else {
                                    "Trailer YouTube"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetflixWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (selectedVideo != null) {
                                Text(
                                    text = "${selectedVideo.channelName} • ${selectedVideo.durationText}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixLightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!useWebEmbed && !trailerState.isSearching && playbackError.isNullOrBlank()) {
                                Surface(
                                    color = NetflixDarkSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                                    modifier = Modifier
                                        .tvFocusableControl(
                                            shape = RoundedCornerShape(8.dp),
                                            focusedScale = 1.06f,
                                            onClick = {
                                                resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                } else {
                                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                }
                                            }
                                        )
                                        .testTag("toggle_trailer_aspect_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                                                Icons.Default.FullscreenExit
                                            } else {
                                                Icons.Default.Fullscreen
                                            },
                                            contentDescription = "Cambia proporzioni",
                                            tint = NetflixWhite,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) "Adatta" else "Riempi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NetflixWhite
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(38.dp)
                                    .focusRequester(closeFocusRequester)
                                    .tvFocusableControl(shape = CircleShape, focusedScale = 1.12f)
                                    .clip(CircleShape)
                                    .background(NetflixDarkSurface)
                                    .testTag("close_trailer_overlay_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Chiudi Popup Trailer",
                                    tint = NetflixWhite
                                )
                            }
                        }
                    }

                    // 2. PALCOSCENICO VIDEO CENTRATO 16:9
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showControls = !showControls
                                interactionTick++
                            }
                            .testTag("fullscreen_trailer_player_view")
                    ) {
                        if (useWebEmbed &&
                            playbackError.isNullOrBlank() &&
                            !trailerState.isSearching &&
                            !trailerState.isLoadingStream &&
                            !resolved?.embedVideoId.isNullOrBlank()
                        ) {
                            YouTubeWebEmbedPlayer(
                                videoId = resolved!!.embedVideoId!!,
                                reloadNonce = embedRetryNonce,
                                onWebViewReady = { webViewRef = it },
                                onReady = { durMs ->
                                    if (durMs > 0L) durationMs = durMs
                                    playbackError = null
                                },
                                onPlaybackStateChanged = { playing, buffering ->
                                    isPlaying = playing
                                    isBuffering = buffering
                                    if (playing) {
                                        playbackError = null
                                    } else if (!buffering) {
                                        showControls = true
                                    }
                                },
                                onProgress = { posMs, durMs ->
                                    if (!isRemoteScrubbing) {
                                        currentPosMs = posMs
                                    }
                                    if (durMs > 0L) durationMs = durMs
                                },
                                onError = { msg ->
                                    isBuffering = false
                                    isPlaying = false
                                    showControls = true
                                    playbackError = msg
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    (LayoutInflater.from(ctx).inflate(R.layout.trailer_player_view, null, false) as PlayerView).apply {
                                        player = exoPlayer
                                        useController = false
                                        keepScreenOn = true
                                        isFocusable = false
                                        isFocusableInTouchMode = false
                                        this.resizeMode = resizeMode
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { view ->
                                    view.player = exoPlayer
                                    view.resizeMode = resizeMode
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Intercettore tocchi trasparente sopra il player (così il WebView non cattura i tap o i link esterni)
                        if (playbackError.isNullOrBlank() && !trailerState.isSearching && !trailerState.isLoadingStream) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showControls = !showControls
                                        interactionTick++
                                    }
                            )
                        }

                        // Stato Errore Esplicito (prioritario) con Riprova / Cambio trailer / Chiudi
                        if (!playbackError.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(NetflixBlack.copy(alpha = 0.9f))
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.widthIn(max = 460.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = NetflixRed,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Text(
                                        text = playbackError.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NetflixWhite,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.testTag("trailer_error_text")
                                    )
                                    if (trailerState.trailers.size > 1) {
                                        Text(
                                            text = "Puoi selezionare un altro trailer dalla lista YouTube qui sotto.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NetflixLightGray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                playbackError = null
                                                isBuffering = true
                                                embedRetryNonce++
                                                onRetry()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NetflixRed,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .focusRequester(retryFocusRequester)
                                                .tvFocusableControl(shape = RoundedCornerShape(8.dp), focusedScale = 1.06f)
                                                .testTag("retry_trailer_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Riprova")
                                        }

                                        OutlinedButton(
                                            onClick = onClose,
                                            border = BorderStroke(1.dp, NetflixElevatedSurface),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = NetflixDarkSurface,
                                                contentColor = NetflixWhite
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.tvFocusableControl(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedScale = 1.06f
                                            )
                                        ) {
                                            Text("Chiudi")
                                        }
                                    }
                                }
                            }
                        } else if (trailerState.isSearching || trailerState.isLoadingStream || isBuffering) {
                            // Stato caricamento ricerca o risoluzione stream YouTube
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(NetflixBlack.copy(alpha = 0.72f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val bgThumb = selectedVideo?.thumbnailUrl ?: mediaItem.backdropUrl ?: mediaItem.posterUrl
                                if (!bgThumb.isNullOrBlank()) {
                                    AsyncImage(
                                        model = bgThumb,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        alpha = 0.30f,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NetflixBlack.copy(alpha = 0.84f))
                                        .padding(horizontal = 22.dp, vertical = 16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = NetflixRed,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = when {
                                            trailerState.isSearching -> "Ricerca trailer su YouTube (ITA / ENG)…"
                                            trailerState.isLoadingStream -> "Caricamento stream YouTube…"
                                            else -> "Buffering video in corso…"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NetflixWhite,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Controlli overlay sul riquadro 16:9 (nessun avvio film completo)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showControls || !isPlaying,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Controlli centrali (-10s / Play-Pausa / +10s)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                                        modifier = Modifier.align(Alignment.Center)
                                    ) {
                                        IconButton(
                                            onClick = { seekToAction(currentPosMs - 10_000L) },
                                            modifier = Modifier
                                                .size(46.dp)
                                                .tvFocusableControl(shape = CircleShape, focusedScale = 1.14f)
                                                .clip(CircleShape)
                                                .background(NetflixBlack.copy(alpha = 0.68f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Replay10,
                                                contentDescription = "Indietro 10 secondi",
                                                tint = NetflixWhite,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = togglePlayPause,
                                            modifier = Modifier
                                                .size(62.dp)
                                                .focusRequester(playPauseFocusRequester)
                                                .tvFocusableControl(shape = CircleShape, focusedScale = 1.14f)
                                                .clip(CircleShape)
                                                .background(NetflixRed.copy(alpha = 0.92f))
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                                                tint = Color.White,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { seekToAction(currentPosMs + 10_000L) },
                                            modifier = Modifier
                                                .size(46.dp)
                                                .tvFocusableControl(shape = CircleShape, focusedScale = 1.14f)
                                                .clip(CircleShape)
                                                .background(NetflixBlack.copy(alpha = 0.68f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Forward10,
                                                contentDescription = "Avanti 10 secondi",
                                                tint = NetflixWhite,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }

                                    // Seekbar temporale inferiore compatibile con telecomando TV
                                    if (durationMs > 0L) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        0f to Color.Transparent,
                                                        1f to NetflixBlack.copy(alpha = 0.88f)
                                                    )
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            TvSeekTimeBar(
                                                currentPositionMs = currentPosMs,
                                                durationMs = durationMs,
                                                onSeekPreview = { previewMs ->
                                                    currentPosMs = previewMs
                                                    interactionTick++
                                                },
                                                onSeekCommit = { targetMs ->
                                                    seekToAction(targetMs)
                                                },
                                                onTogglePlayPause = togglePlayPause,
                                                onInteraction = { interactionTick++ },
                                                onScrubbingChanged = { isRemoteScrubbing = it },
                                                compact = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("trailer_time_bar")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. FASCIA INFERIORE: LISTA MULTIPLA TRAILER YOUTUBE (ITA prima, poi ENG)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NetflixDarkSurface)
                            .padding(top = 12.dp, bottom = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = if (trailerState.isSearching) {
                                    "Ricerca risultati su YouTube…"
                                } else {
                                    "Trailer trovati su YouTube (${trailerState.trailers.size})"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NetflixWhite
                            )
                            Text(
                                text = "Priorità: ITA → ENG",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixMatchGreen
                            )
                        }

                        if (trailerState.trailers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                state = playlistState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("youtube_trailer_playlist_row")
                            ) {
                                itemsIndexed(
                                    items = trailerState.trailers,
                                    key = { _, it -> "yt_card_${it.videoId}" }
                                ) { index, candidate ->
                                    YouTubeModalTrailerCard(
                                        candidate = candidate,
                                        isSelected = candidate.videoId == selectedVideo?.videoId,
                                        itemIndex = index,
                                        listState = playlistState,
                                        onClick = { onSelectTrailer(candidate) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(220),
                                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                    )
                                }
                            }
                        } else if (!trailerState.isSearching) {
                            Text(
                                text = "Nessun altro trailer disponibile per questo titolo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NetflixLightGray,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun YouTubeModalTrailerCard(
    candidate: YouTubeTrailerCandidate,
    isSelected: Boolean,
    itemIndex: Int,
    listState: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val thumbRequest = remember(context, candidate.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(candidate.thumbnailUrl)
            .crossfade(250)
            .build()
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) NetflixElevatedSurface else NetflixCardSurface
        ),
        border = BorderStroke(
            width = if (isSelected || isFocused) 2.dp else 1.dp,
            color = when {
                isFocused -> NetflixWhite
                isSelected -> NetflixRed
                else -> NetflixElevatedSurface
            }
        ),
        modifier = modifier
            .width(206.dp)
            .animatedPosterShowcase(
                itemIndex = itemIndex,
                listState = listState,
                isFocused = isFocused,
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.07f,
                onFocusedChanged = { isFocused = it },
                onClick = onClick
            )
            .testTag("youtube_trailer_option_${candidate.videoId}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(NetflixBlack)
            ) {
                AsyncImage(
                    model = thumbRequest,
                    contentDescription = candidate.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NetflixBlack.copy(alpha = if (isSelected) 0.15f else 0.35f))
                )

                // Badge lingua (ITA / ENG)
                Surface(
                    color = if (candidate.languageLabel == "ITA") NetflixMatchGreen else NetflixBlack.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = candidate.languageLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (candidate.languageLabel == "ITA") NetflixBlack else NetflixWhite,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                // Badge stato attivo
                if (isSelected) {
                    Surface(
                        color = NetflixRed,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "ATTIVO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                // Durata
                Surface(
                    color = NetflixBlack.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = candidate.durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixWhite,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                if (!isSelected) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(NetflixRed.copy(alpha = 0.88f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Riproduci Trailer",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = NetflixWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = candidate.channelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = NetflixLightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Riproduce il trailer YouTube tramite YouTube IFrame JS API ufficiale all'interno di WebView,
 * gestendo BotGuard/PoToken in automatico, evitando focus trap su Android TV e prevenendo crash del renderer.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebEmbedPlayer(
    videoId: String,
    reloadNonce: Int,
    onWebViewReady: (WebView?) -> Unit,
    onReady: (Long) -> Unit,
    onPlaybackStateChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
    onProgress: (posMs: Long, durMs: Long) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val safeVideoId = remember(videoId) { videoId.replace(Regex("[^A-Za-z0-9_-]"), "") }
    val loadKey = "$safeVideoId#$reloadNonce"
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var activeVideoId by remember { mutableStateOf(safeVideoId) }
    activeVideoId = safeVideoId

    var hasStartedOrPaused by remember { mutableStateOf(false) }
    var isDestroyed by remember { mutableStateOf(false) }

    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            keepScreenOn = true
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }
    }

    // Se IFrame API è pronto ma autoplay resta in UNSTARTED dopo 2.2s, scopre i controlli Play/Pause
    LaunchedEffect(loadKey) {
        hasStartedOrPaused = false
        delay(2_200L)
        if (!hasStartedOrPaused && !isDestroyed) {
            onPlaybackStateChanged(false, false)
        }
    }

    DisposableEffect(webView) {
        val bridge = YouTubeEmbedBridge(
            currentVideoId = { activeVideoId },
            mainHandler = mainHandler,
            onReadyCallback = { durMs ->
                if (!isDestroyed) onReady(durMs)
            },
            onStateChangeCallback = { state, curMs, durMs ->
                if (isDestroyed) return@YouTubeEmbedBridge
                if (durMs > 0L) onProgress(curMs, durMs)
                when (state) {
                    1 -> { // PLAYING
                        hasStartedOrPaused = true
                        onPlaybackStateChanged(true, false)
                    }
                    2, 0, 5 -> { // PAUSED / ENDED / CUED
                        hasStartedOrPaused = true
                        onPlaybackStateChanged(false, false)
                    }
                    3 -> { // BUFFERING
                        onPlaybackStateChanged(false, true)
                    }
                }
            },
            onTimeUpdateCallback = { curMs, durMs ->
                if (!isDestroyed) onProgress(curMs, durMs)
            },
            onErrorCallback = { code ->
                if (isDestroyed) return@YouTubeEmbedBridge
                hasStartedOrPaused = true
                onError(mapYouTubeEmbedError(code))
            }
        )

        webView.addJavascriptInterface(bridge, "AndroidBridge")
        webView.webChromeClient = object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Mantiene l'utente dentro il modale trailer
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && !isDestroyed) {
                    mainHandler.post {
                        onError("Errore di rete nel caricamento del player YouTube.")
                    }
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                val urlStr = request?.url?.toString().orEmpty()
                val code = errorResponse?.statusCode ?: return
                if ((request?.isForMainFrame == true || urlStr.contains("/embed/")) && code >= 400 && !isDestroyed) {
                    mainHandler.post {
                        onError("Trailer YouTube non disponibile o bloccato (HTTP $code).")
                    }
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                isDestroyed = true
                TmdbTrailerResolver.isWebViewRendererUnstable = true
                mainHandler.post {
                    onWebViewReady(null)
                    onError("Il player YouTube è stato interrotto dal sistema. Seleziona un altro trailer dalla lista o riprova.")
                }
                runCatching {
                    (view?.parent as? ViewGroup)?.removeView(view)
                    view?.destroy()
                }
                return true
            }
        }

        onWebViewReady(webView)
        onDispose {
            onWebViewReady(null)
            if (!isDestroyed) {
                isDestroyed = true
                runCatching {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.removeJavascriptInterface("AndroidBridge")
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                }
            }
        }
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            if (!isDestroyed && view.tag != loadKey && safeVideoId.isNotBlank()) {
                view.tag = loadKey
                view.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    buildYouTubeEmbedHtml(safeVideoId),
                    "text/html",
                    "UTF-8",
                    "https://www.youtube.com"
                )
            }
        },
        modifier = modifier
    )
}

private class YouTubeEmbedBridge(
    private val currentVideoId: () -> String,
    private val mainHandler: Handler,
    private val onReadyCallback: (Long) -> Unit,
    private val onStateChangeCallback: (Int, Long, Long) -> Unit,
    private val onTimeUpdateCallback: (Long, Long) -> Unit,
    private val onErrorCallback: (Int) -> Unit
) {
    @JavascriptInterface
    fun onReady(videoId: String, durationSec: Double) {
        mainHandler.post {
            if (videoId == currentVideoId()) {
                onReadyCallback((durationSec * 1000.0).toLong().coerceAtLeast(0L))
            }
        }
    }

    @JavascriptInterface
    fun onStateChange(videoId: String, state: Int, currentSec: Double, durationSec: Double) {
        mainHandler.post {
            if (videoId == currentVideoId()) {
                onStateChangeCallback(
                    state,
                    (currentSec * 1000.0).toLong().coerceAtLeast(0L),
                    (durationSec * 1000.0).toLong().coerceAtLeast(0L)
                )
            }
        }
    }

    @JavascriptInterface
    fun onTimeUpdate(videoId: String, currentSec: Double, durationSec: Double) {
        mainHandler.post {
            if (videoId == currentVideoId()) {
                onTimeUpdateCallback(
                    (currentSec * 1000.0).toLong().coerceAtLeast(0L),
                    (durationSec * 1000.0).toLong().coerceAtLeast(0L)
                )
            }
        }
    }

    @JavascriptInterface
    fun onError(videoId: String, errorCode: Int) {
        mainHandler.post {
            if (videoId == currentVideoId()) {
                onErrorCallback(errorCode)
            }
        }
    }
}

private fun mapYouTubeEmbedError(code: Int): String {
    return when (code) {
        2 -> "Parametro video YouTube non valido."
        5 -> "Errore del player HTML5 su questo dispositivo."
        100 -> "Trailer YouTube non trovato, privato o rimosso."
        101, 150 -> "Riproduzione incorporata bloccata dal proprietario del video YouTube. Scegli un altro trailer dalla lista."
        153 -> "Configurazione player YouTube non accettata (153). Premi Riprova o seleziona un altro trailer."
        -101 -> "Impossibile contattare il player ufficiale YouTube. Controlla la connessione o riprova."
        else -> "Impossibile riprodurre questo video YouTube (codice $code). Scegli un altro trailer o chiudi."
    }
}

private fun buildYouTubeEmbedHtml(videoId: String): String {
    return """
        <!DOCTYPE html>
        <html lang="it">
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            html, body {
              margin: 0;
              padding: 0;
              width: 100%;
              height: 100%;
              background-color: #000000;
              overflow: hidden;
            }
            #player {
              position: fixed;
              inset: 0;
              width: 100%;
              height: 100%;
            }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script>
            var VIDEO_ID = '$videoId';
            var player = null;
            var apiReady = false;
            var pollHandle = null;

            function startPolling() {
              if (pollHandle) clearInterval(pollHandle);
              pollHandle = setInterval(function() {
                try {
                  if (player && typeof player.getCurrentTime === 'function') {
                    var cur = player.getCurrentTime() || 0;
                    var dur = player.getDuration() || 0;
                    AndroidBridge.onTimeUpdate(VIDEO_ID, cur, dur);
                  }
                } catch (e) {}
              }, 350);
            }

            window.vixPlay = function() {
              try { if (player && player.playVideo) player.playVideo(); } catch (e) {}
            };
            window.vixPause = function() {
              try { if (player && player.pauseVideo) player.pauseVideo(); } catch (e) {}
            };
            window.vixSeekTo = function(sec) {
              try { if (player && player.seekTo) player.seekTo(sec, true); } catch (e) {}
            };

            function onYouTubeIframeAPIReady() {
              apiReady = true;
              try {
                player = new YT.Player('player', {
                  width: '100%',
                  height: '100%',
                  videoId: VIDEO_ID,
                  host: 'https://www.youtube.com',
                  playerVars: {
                    autoplay: 1,
                    controls: 0,
                    disablekb: 1,
                    fs: 0,
                    rel: 0,
                    iv_load_policy: 3,
                    playsinline: 1,
                    enablejsapi: 1,
                    origin: 'https://www.youtube.com',
                    hl: 'it'
                  },
                  events: {
                    onReady: function(event) {
                      var dur = 0;
                      try { dur = event.target.getDuration() || 0; } catch (e) {}
                      AndroidBridge.onReady(VIDEO_ID, dur);
                      startPolling();
                      try { event.target.playVideo(); } catch (e) {}
                    },
                    onStateChange: function(event) {
                      var cur = 0, dur = 0;
                      try {
                        cur = event.target.getCurrentTime() || 0;
                        dur = event.target.getDuration() || 0;
                      } catch (e) {}
                      AndroidBridge.onStateChange(VIDEO_ID, event.data, cur, dur);
                      if (event.data === 5) {
                        try { event.target.playVideo(); } catch (e) {}
                      }
                    },
                    onError: function(event) {
                      AndroidBridge.onError(VIDEO_ID, event.data);
                    }
                  }
                });
              } catch (e) {
                AndroidBridge.onError(VIDEO_ID, -101);
              }
            }

            setTimeout(function() {
              if (!apiReady && typeof YT === 'undefined') {
                AndroidBridge.onError(VIDEO_ID, -101);
              }
            }, 9000);
          </script>
          <script src="https://www.youtube.com/iframe_api" onerror="AndroidBridge.onError('$videoId', -101)"></script>
        </body>
        </html>
    """.trimIndent()
}
