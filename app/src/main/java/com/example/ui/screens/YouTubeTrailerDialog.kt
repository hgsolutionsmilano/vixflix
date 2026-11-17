package com.example.ui.screens

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.example.R
import com.example.data.remote.MediaItem
import com.example.data.remote.YouTubeTrailerCandidate
import com.example.ui.TrailerPlaybackState
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

    var isBuffering by remember(activeKey) { mutableStateOf(trailerState.isLoadingStream) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember(activeKey) { mutableLongStateOf(0L) }
    var durationMs by remember(activeKey) { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackError by remember(activeKey, trailerState.errorMessage) {
        mutableStateOf(trailerState.errorMessage)
    }
    var interactionTick by remember { mutableLongStateOf(0L) }

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

    // Prepara o ferma ExoPlayer in base allo stato della ricerca e dello stream selezionato
    LaunchedEffect(
        resolved?.streamUrl,
        resolved?.audioStreamUrl,
        trailerState.isSearching,
        trailerState.isLoadingStream,
        trailerState.errorMessage,
        okHttpDataSourceFactory
    ) {
        if (trailerState.isSearching || trailerState.isLoadingStream) {
            isBuffering = true
            playbackError = null
            exoPlayer.stop()
            return@LaunchedEffect
        }

        if (!trailerState.errorMessage.isNullOrBlank()) {
            isBuffering = false
            isPlaying = false
            showControls = true
            playbackError = trailerState.errorMessage
            exoPlayer.stop()
            return@LaunchedEffect
        }

        if (resolved == null || resolved.streamUrl.isBlank()) {
            isBuffering = false
            isPlaying = false
            showControls = true
            playbackError = "Trailer non disponibile o non riproducibile su YouTube."
            exoPlayer.stop()
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
    LaunchedEffect(resolved?.streamUrl, isBuffering, trailerState.isSearching, trailerState.isLoadingStream, playbackError) {
        if (!trailerState.isSearching && !trailerState.isLoadingStream && isBuffering && playbackError == null && resolved != null) {
            delay(15_000L)
            if (isBuffering && playbackError == null && exoPlayer.playbackState != Player.STATE_READY) {
                exoPlayer.stop()
                isBuffering = false
                isPlaying = false
                showControls = true
                playbackError = "Timeout di caricamento del video YouTube. Prova un altro trailer dalla lista o chiudi."
            }
        }
    }

    // Aggiornamento timeline
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(500L)
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
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
    LaunchedEffect(showControls, isPlaying, isBuffering, trailerState.isSearching, trailerState.isLoadingStream, interactionTick) {
        if (showControls && isPlaying && !isBuffering && !trailerState.isSearching && !trailerState.isLoadingStream) {
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
                if (!playbackError.isNullOrBlank()) {
                    isBuffering = false
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
                isPlaying = playing
                if (playing) {
                    isBuffering = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
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
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaPlay -> {
                                exoPlayer.play()
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaPause -> {
                                exoPlayer.pause()
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaFastForward -> {
                                exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                                showControls = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaRewind -> {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
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
            // Card Modale Cinematografica Centrata
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
                modifier = Modifier
                    .widthIn(max = 880.dp)
                    .fillMaxWidth(0.96f)
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
                            if (!trailerState.isSearching && playbackError.isNullOrBlank()) {
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
                                            onClick = onRetry,
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
                                            onClick = {
                                                exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                                                interactionTick++
                                            },
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
                                            onClick = {
                                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                                interactionTick++
                                            },
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
                                            onClick = {
                                                exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                                                interactionTick++
                                            },
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

                                    // Seekbar temporale inferiore
                                    if (durationMs > 0L) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                            Text(
                                                text = formatSecondsToMmSs(currentPosMs / 1000L),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NetflixWhite
                                            )
                                            Slider(
                                                value = currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                                                onValueChange = { newValue ->
                                                    currentPosMs = newValue.toLong()
                                                    exoPlayer.seekTo(currentPosMs)
                                                    interactionTick++
                                                },
                                                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = NetflixRed,
                                                    activeTrackColor = NetflixRed,
                                                    inactiveTrackColor = NetflixElevatedSurface
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = formatSecondsToMmSs(durationMs / 1000L),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NetflixLightGray
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
                                        onClick = { onSelectTrailer(candidate) }
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

@Composable
private fun YouTubeModalTrailerCard(
    candidate: YouTubeTrailerCandidate,
    isSelected: Boolean,
    itemIndex: Int,
    listState: LazyListState,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

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
        modifier = Modifier
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
                    model = candidate.thumbnailUrl,
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
