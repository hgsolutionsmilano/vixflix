package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.focusProperties
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.StreamingBufferPreset
import com.example.data.remote.ResolvedStream
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.components.TvSeekTimeBar
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
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
internal fun StreamingBufferPreset.toExoLoadControl(): DefaultLoadControl {
    return DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
        .setBufferDurationsMs(
            minBufferMs,
            maxBufferMs,
            bufferForPlaybackMs,
            bufferForPlaybackAfterRebufferMs
        )
        .setBackBuffer(15_000, true)
        .setTargetBufferBytes(targetBufferBytes)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()
}

@OptIn(UnstableApi::class)
@Composable
fun VixPlayerScreen(
    session: PlaybackSession,
    streamLang: String,
    bufferPreset: StreamingBufferPreset = StreamingBufferPreset.DEFAULT,
    vixSrcResolver: VixSrcStreamResolver,
    onDynamicHlsFound: (String) -> Unit,
    onTogglePlayerMode: (Boolean) -> Unit,
    onToggleLanguage: (String) -> Unit,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    onNextEpisode: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val resolvedStream = session.resolvedStream

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerErrorMessage by remember(session.errorMessage) { mutableStateOf(session.errorMessage) }
    var currentPositionMs by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableLongStateOf(session.startPositionMs)
    }
    LaunchedEffect(session.startPositionMs, session.isResolvingStream) {
        if (session.isResolvingStream) {
            currentPositionMs = session.startPositionMs
        }
    }
    var durationMs by remember { mutableLongStateOf(0L) }
    var hasSeekedInitial by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var interactionTick by remember { mutableLongStateOf(0L) }
    var isRemoteScrubbing by remember { mutableStateOf(false) }
    var isTimeBarFocused by remember { mutableStateOf(false) }
    var focusTimeBarOnReveal by remember { mutableStateOf(false) }
    var wasControlsVisible by remember { mutableStateOf(false) }
    var playbackEnded by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var autoNextDismissed by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var autoNextRemainingSec by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableIntStateOf(10)
    }
    var mediaActionBadge by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }

    val isTvDevice = rememberIsTvDevice()
    val backButtonFocusRequester = remember { FocusRequester() }
    val audioLangFocusRequester = remember { FocusRequester() }
    val vixLinkFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val timeBarFocusRequester = remember { FocusRequester() }
    val resizeFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val cancelLoadingFocusRequester = remember { FocusRequester() }
    val autoNextPlayFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    val currentSeason = session.seasonNumber ?: 1
    val currentEpisode = session.episodeNumber ?: 1
    val nextEpisodeDto = remember(session.seasonEpisodes, currentEpisode) {
        session.seasonEpisodes.firstOrNull { it.episodeNumber == currentEpisode + 1 }
    }
    val hasNextEpisodeCandidate = session.mediaItem.isSeries && (
        nextEpisodeDto != null || session.seasonEpisodes.isEmpty()
    )
    val currentError = playerErrorMessage ?: session.errorMessage
    val nearEpisodeEnd = durationMs > 60_000L && currentPositionMs >= (durationMs - 12_000L)
    val showAutoNext = hasNextEpisodeCandidate &&
        currentError == null &&
        !isBuffering &&
        !session.isResolvingStream &&
        !autoNextDismissed &&
        (playbackEnded || nearEpisodeEnd)

    // Usa OkHttpDataSource.Factory con l'interceptor VixSrc per gestire playlist HLS,
    // chiave AES-128 (/storage/enc.key) e segmenti MPEG-TS direttamente da vixsrc.to
    val okHttpDataSourceFactory = remember(vixSrcResolver, resolvedStream?.referer) {
        val headers = mapOf(
            "Referer" to (resolvedStream?.referer ?: "https://vixsrc.to/"),
            "Origin" to "https://vixsrc.to"
        )
        OkHttpDataSource.Factory(vixSrcResolver.exoOkHttpClient)
            .setUserAgent(resolvedStream?.userAgent ?: ResolvedStream.DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(headers)
    }

    val exoPlayer = remember(context, bufferPreset) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(bufferPreset.toExoLoadControl())
            .setAudioAttributes(audioAttributes, true)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Imposta la lingua audio preferita (ita / eng) su ogni istanza attiva di ExoPlayer
    LaunchedEffect(exoPlayer, streamLang) {
        val preferredIso = if (streamLang.equals("it", ignoreCase = true)) "ita" else "eng"
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(preferredIso)
            .build()
    }

    BackHandler {
        when {
            showAutoNext -> {
                autoNextDismissed = true
                if (isTvDevice) {
                    rootFocusRequester.safeRequestFocus()
                }
            }
            controlsVisible && currentError == null && !isBuffering && !session.isResolvingStream -> {
                controlsVisible = false
                if (isTvDevice) {
                    rootFocusRequester.safeRequestFocus()
                }
            }
            else -> {
                if (currentPositionMs > 0L) {
                    onProgressUpdate(currentPositionMs, durationMs)
                }
                onClose()
            }
        }
    }

    // Configura HlsMediaSource in Media3 ExoPlayer (ri-eseguito anche se exoPlayer viene ricreato da cambio preset buffer)
    LaunchedEffect(
        exoPlayer,
        session.mediaItem.uniqueKey,
        session.seasonNumber,
        session.episodeNumber,
        resolvedStream?.streamUrl,
        session.isResolvingStream,
        retryNonce
    ) {
        if (session.isResolvingStream) return@LaunchedEffect
        val stream = resolvedStream
        if (stream == null || !stream.isDirectHls) {
            isBuffering = false
            return@LaunchedEffect
        }

        playerErrorMessage = null
        hasSeekedInitial = false
        isBuffering = true
        val targetUri = Uri.parse(stream.streamUrl)
        val mediaItem = ExoMediaItem.Builder()
            .setUri(targetUri)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val mediaSource = HlsMediaSource.Factory(okHttpDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(4))
            .createMediaSource(mediaItem)

        val resumePosMs = maxOf(session.startPositionMs, currentPositionMs)
        if (resumePosMs > 0L) {
            exoPlayer.setMediaSource(mediaSource, resumePosMs)
        } else {
            exoPlayer.setMediaSource(mediaSource)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Listener eventi Player + salvataggio su Room Database
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerErrorMessage = null
                    playbackEnded = false
                    val d = exoPlayer.duration
                    if (d != C.TIME_UNSET && d > 0L) {
                        durationMs = d
                    }
                    if (!hasSeekedInitial) {
                        hasSeekedInitial = true
                        val targetStart = maxOf(session.startPositionMs, currentPositionMs)
                        if (targetStart > 0L && durationMs > 0L) {
                            val maxSafePos = (durationMs - 5_000L).coerceAtLeast(0L)
                            if (exoPlayer.currentPosition > maxSafePos) {
                                exoPlayer.seekTo(maxSafePos)
                                currentPositionMs = maxSafePos
                            }
                        }
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isBuffering = false
                    isPlaying = false
                    playbackEnded = true
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && exoPlayer.currentPosition > 0L) {
                    onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerErrorMessage = "Errore stream VixSrc: ${error.errorCodeName} (${error.localizedMessage ?: "connessione interrotta"})"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val finalPos = currentPositionMs.coerceAtLeast(exoPlayer.currentPosition)
            val finalDur = durationMs.coerceAtLeast(exoPlayer.duration.coerceAtLeast(0L))
            if (finalPos > 0L) {
                onProgressUpdate(finalPos, finalDur)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Loop di aggiornamento UI + persistenza su Room ogni 3 secondi
    LaunchedEffect(exoPlayer) {
        var tick = 0
        while (true) {
            if (exoPlayer.isPlaying && !isRemoteScrubbing) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: durationMs
                if (pos > 0L) {
                    currentPositionMs = pos
                    durationMs = dur
                    if (tick % 3 == 0) {
                        onProgressUpdate(pos, dur)
                    }
                    tick++
                }
            }
            delay(1_000L)
        }
    }

    // Timer conto alla rovescia automatico Prossimo Episodio
    LaunchedEffect(showAutoNext, currentSeason, currentEpisode) {
        if (!showAutoNext) return@LaunchedEffect
        autoNextRemainingSec = 10
        controlsVisible = false
        if (isTvDevice) {
            delay(60L)
            autoNextPlayFocusRequester.safeRequestFocus()
        }
        while (autoNextRemainingSec > 0) {
            delay(1_000L)
            autoNextRemainingSec--
        }
        if (showAutoNext && !autoNextDismissed) {
            onNextEpisode()
        }
    }

    LaunchedEffect(mediaActionBadge) {
        if (mediaActionBadge != null) {
            delay(1_200L)
            mediaActionBadge = null
        }
    }

    // Auto-hide dei controlli dopo 4.5 secondi (sospeso durante lo scorrimento sulla barra del tempo)
    LaunchedEffect(
        controlsVisible,
        isPlaying,
        playerErrorMessage,
        isBuffering,
        session.isResolvingStream,
        isRemoteScrubbing,
        showAutoNext,
        interactionTick
    ) {
        if (controlsVisible && isPlaying && !isBuffering && !session.isResolvingStream && !isRemoteScrubbing && playerErrorMessage == null && !showAutoNext) {
            delay(4_500L)
            controlsVisible = false
            if (isTvDevice) {
                rootFocusRequester.safeRequestFocus()
            }
        }
    }

    // Sposta automaticamente il focus sui controlli TV solo quando compaiono (senza rubare focus alla barra del tempo durante i seek)
    LaunchedEffect(controlsVisible, playerErrorMessage, session.errorMessage, isBuffering, session.isResolvingStream, showAutoNext) {
        if (showAutoNext) {
            wasControlsVisible = false
            return@LaunchedEffect
        }
        val hasErr = (playerErrorMessage ?: session.errorMessage) != null && !session.isResolvingStream
        if (hasErr) {
            retryFocusRequester.safeRequestFocus()
            wasControlsVisible = false
            return@LaunchedEffect
        }

        if (isBuffering || session.isResolvingStream) {
            wasControlsVisible = false
            if (isTvDevice) {
                delay(60L)
                cancelLoadingFocusRequester.safeRequestFocus()
            }
            return@LaunchedEffect
        }

        val readyControls = controlsVisible
        if (readyControls) {
            if (!wasControlsVisible && !isTimeBarFocused) {
                if (focusTimeBarOnReveal) {
                    focusTimeBarOnReveal = false
                    timeBarFocusRequester.safeRequestFocus()
                } else {
                    playPauseFocusRequester.safeRequestFocus()
                }
            }
            wasControlsVisible = true
        } else {
            wasControlsVisible = false
            focusTimeBarOnReveal = false
            if (isTvDevice) {
                rootFocusRequester.safeRequestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeFocusRequester(rootFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                interactionTick++
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.MediaPlayPause -> {
                            val willPlay = !exoPlayer.isPlaying
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            mediaActionBadge = if (willPlay) "Riproduzione" else "In Pausa"
                            if (!willPlay) controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaPlay -> {
                            exoPlayer.play()
                            mediaActionBadge = "Riproduzione"
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaPause -> {
                            exoPlayer.pause()
                            mediaActionBadge = "In Pausa"
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaFastForward -> {
                            val target = exoPlayer.currentPosition + 10_000L
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            mediaActionBadge = "+10s"
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaRewind -> {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            playbackEnded = false
                            mediaActionBadge = "-10s"
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaNext -> {
                            if (session.mediaItem.isSeries) {
                                onNextEpisode()
                                return@onPreviewKeyEvent true
                            }
                        }
                        else -> Unit
                    }

                    val canRevealOrSeek = !controlsVisible &&
                        playerErrorMessage == null &&
                        session.errorMessage == null &&
                        !isBuffering &&
                        !session.isResolvingStream
                    if (canRevealOrSeek) {
                        when (keyEvent.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                            Key.DirectionUp, Key.DirectionDown -> {
                                controlsVisible = true
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionLeft -> {
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                onProgressUpdate(currentPositionMs, durationMs)
                                focusTimeBarOnReveal = true
                                controlsVisible = true
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionRight -> {
                                val maxDur = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                                val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                onProgressUpdate(currentPositionMs, durationMs)
                                focusTimeBarOnReveal = true
                                controlsVisible = true
                                return@onPreviewKeyEvent true
                            }
                            else -> Unit
                        }
                    }
                }
                false
            }
            .focusable()
            .testTag("vix_player_screen")
    ) {
        // Vista nativa 100% Media3 ExoPlayer (isFocusable = false così i tasti telecomando restano ai controlli Compose)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setKeepContentOnPlayerReset(true)
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
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
                if (view.resizeMode != resizeMode) {
                    view.resizeMode = resizeMode
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                }
        )

        if (currentError != null && !session.isResolvingStream) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NetflixCardSurface.copy(alpha = 0.95f))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = currentError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NetflixWhite,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        playerErrorMessage = null
                        isBuffering = true
                        retryNonce++
                        onToggleLanguage(streamLang)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .safeFocusRequester(retryFocusRequester)
                        .focusProperties {
                            backButtonFocusRequester.takeIfAttached()?.let { up = it }
                        }
                        .tvFocusableControl(shape = RoundedCornerShape(8.dp), focusedScale = 1.06f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Riprova Connessione VixSrc")
                }
            }
        } else if (isBuffering || session.isResolvingStream) {
            // Popup di Loading in fase di avvio/attesa dello streaming con tasto Annulla focusabile su TV
            VixStreamLoadingPopup(
                session = session,
                streamLang = streamLang,
                bufferPreset = bufferPreset,
                cancelFocusRequester = cancelLoadingFocusRequester,
                upFocusRequester = backButtonFocusRequester,
                onCancel = {
                    if (currentPositionMs > 0L) {
                        onProgressUpdate(currentPositionMs, durationMs)
                    }
                    onClose()
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
            )
        }

        // Badge discreto per tasti multimediali dedicati telecomando (Play/Pausa/±10s)
        AnimatedVisibility(
            visible = mediaActionBadge != null && !controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 20.dp, end = 24.dp)
        ) {
            Surface(
                color = NetflixBlack.copy(alpha = 0.85f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, NetflixRed)
            ) {
                Text(
                    text = mediaActionBadge.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = NetflixWhite,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Overlay Top Bar
        AnimatedVisibility(
            visible = (controlsVisible || currentError != null || isBuffering || session.isResolvingStream) && !showAutoNext,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val readyOverlayActive = controlsVisible && currentError == null && !isBuffering && !session.isResolvingStream
            val topBarDownTarget: FocusRequester? = when {
                currentError != null && !session.isResolvingStream -> retryFocusRequester
                isBuffering || session.isResolvingStream -> cancelLoadingFocusRequester
                readyOverlayActive -> playPauseFocusRequester
                else -> null
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NetflixBlack.copy(alpha = 0.88f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            if (currentPositionMs > 0L) {
                                onProgressUpdate(currentPositionMs, durationMs)
                            }
                            onClose()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .safeFocusRequester(backButtonFocusRequester)
                            .focusProperties {
                                audioLangFocusRequester.takeIfAttached()?.let { right = it }
                                topBarDownTarget.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(shape = CircleShape, focusedScale = 1.12f)
                            .testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
                            tint = NetflixWhite
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.mediaItem.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = NetflixWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subText = buildString {
                            if (session.mediaItem.isSeries && session.seasonNumber != null && session.episodeNumber != null) {
                                append("S${session.seasonNumber}:E${session.episodeNumber}")
                                if (!session.episodeTitle.isNullOrBlank()) {
                                    append(" - ${session.episodeTitle}")
                                }
                                append(" • ")
                            }
                            append("VixSrc HLS 1080p • startAt=${currentPositionMs / 1000L}s (min ${currentPositionMs / 60_000L})")
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.labelSmall,
                            color = NetflixMatchGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val vixEmbedUrl = resolvedStream?.embedUrl
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Selettore Lingua Audio (ITA / ENG)
                    Surface(
                        color = NetflixCardSurface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NetflixElevatedSurface),
                        modifier = Modifier
                            .safeFocusRequester(audioLangFocusRequester)
                            .focusProperties {
                                backButtonFocusRequester.takeIfAttached()?.let { left = it }
                                if (!vixEmbedUrl.isNullOrBlank()) {
                                    vixLinkFocusRequester.takeIfAttached()?.let { right = it }
                                }
                                topBarDownTarget.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(6.dp),
                                focusedScale = 1.08f,
                                onClick = {
                                    onToggleLanguage(if (streamLang == "it") "en" else "it")
                                }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = NetflixRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Audio: ${streamLang.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NetflixWhite
                            )
                        }
                    }

                    // Link diretto esterno VixSrc con startAt
                    if (!vixEmbedUrl.isNullOrBlank()) {
                        Surface(
                            color = NetflixCardSurface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, NetflixElevatedSurface),
                            modifier = Modifier
                                .safeFocusRequester(vixLinkFocusRequester)
                                .focusProperties {
                                    audioLangFocusRequester.takeIfAttached()?.let { left = it }
                                    topBarDownTarget.takeIfAttached()?.let { down = it }
                                }
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(6.dp),
                                    focusedScale = 1.08f,
                                    onClick = {
                                        runCatching {
                                            val liveUrl = vixSrcResolver.buildVixSrcApiUrl(
                                                tmdbId = session.mediaItem.id,
                                                mediaType = session.mediaItem.mediaType,
                                                season = session.seasonNumber,
                                                episode = session.episodeNumber,
                                                startSeconds = currentPositionMs / 1000L,
                                                lang = streamLang
                                            )
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(liveUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }
                                    }
                                )
                                .testTag("open_external_vixsrc_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Apri URL VixSrc",
                                    tint = NetflixWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "VixSrc Link",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NetflixWhite
                                )
                            }
                        }
                    }
                }
            }
        }

        // Controlli centrali e barra inferiore per ExoPlayer Nativo (visibili quando il popup di loading non è mostrato)
        AnimatedVisibility(
            visible = controlsVisible && currentError == null && !isBuffering && !session.isResolvingStream && !showAutoNext,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val bottomPrimaryFocusRequester = if (session.mediaItem.isSeries) {
                nextEpisodeFocusRequester
            } else {
                resizeFocusRequester
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Pulsanti centrali -10s / Play-Pause / +10s
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            playbackEnded = false
                            onProgressUpdate(currentPositionMs, durationMs)
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .focusProperties {
                                backButtonFocusRequester.takeIfAttached()?.let { up = it }
                                timeBarFocusRequester.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(shape = CircleShape, focusedScale = 1.14f)
                            .clip(CircleShape)
                            .background(NetflixBlack.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Indietro 10 secondi",
                            tint = NetflixWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(70.dp)
                            .safeFocusRequester(playPauseFocusRequester)
                            .focusProperties {
                                backButtonFocusRequester.takeIfAttached()?.let { up = it }
                                timeBarFocusRequester.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(shape = CircleShape, focusedScale = 1.14f)
                            .clip(CircleShape)
                            .background(NetflixRed)
                            .testTag("exo_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = exoPlayer.currentPosition + 10_000L
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            onProgressUpdate(currentPositionMs, durationMs)
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .focusProperties {
                                backButtonFocusRequester.takeIfAttached()?.let { up = it }
                                timeBarFocusRequester.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(shape = CircleShape, focusedScale = 1.14f)
                            .clip(CircleShape)
                            .background(NetflixBlack.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Avanti 10 secondi",
                            tint = NetflixWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Barra inferiore con Slider rosso Netflix, timestamp salvato su Room e Prossimo Episodio
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    NetflixBlack.copy(alpha = 0.92f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    TvSeekTimeBar(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onSeekPreview = { previewMs ->
                            currentPositionMs = previewMs
                            interactionTick++
                        },
                        onSeekCommit = { targetMs ->
                            currentPositionMs = targetMs
                            playbackEnded = false
                            exoPlayer.seekTo(targetMs)
                            onProgressUpdate(targetMs, durationMs)
                            interactionTick++
                        },
                        onTogglePlayPause = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            interactionTick++
                        },
                        onInteraction = { interactionTick++ },
                        onFocusChanged = { isTimeBarFocused = it },
                        onScrubbingChanged = { isRemoteScrubbing = it },
                        focusRequester = timeBarFocusRequester,
                        upFocusRequester = playPauseFocusRequester,
                        downFocusRequester = bottomPrimaryFocusRequester,
                        compact = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_time_bar")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${formatSecondsToMmSs(currentPositionMs / 1000L)} / ${
                                    if (durationMs > 0L) formatSecondsToMmSs(durationMs / 1000L) else "HLS STREAM"
                                } • Salvato in Room (min ${currentPositionMs / 60_000L})",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixWhite
                            )
                            Text(
                                text = "VixSrc Direct MasterPlaylist • Audio ${streamLang.uppercase()} • startAt=${currentPositionMs / 1000L}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixLightGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    } else {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .safeFocusRequester(resizeFocusRequester)
                                    .focusProperties {
                                        timeBarFocusRequester.takeIfAttached()?.let { up = it }
                                        if (session.mediaItem.isSeries) {
                                            nextEpisodeFocusRequester.takeIfAttached()?.let { right = it }
                                        }
                                    }
                                    .tvFocusableControl(shape = CircleShape, focusedScale = 1.12f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Cambia proporzioni schermo",
                                    tint = NetflixWhite
                                )
                            }

                            if (session.mediaItem.isSeries) {
                                Button(
                                    onClick = onNextEpisode,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NetflixWhite,
                                        contentColor = NetflixBlack
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .safeFocusRequester(nextEpisodeFocusRequester)
                                        .focusProperties {
                                            timeBarFocusRequester.takeIfAttached()?.let { up = it }
                                            resizeFocusRequester.takeIfAttached()?.let { left = it }
                                        }
                                        .tvFocusableControl(
                                            shape = RoundedCornerShape(6.dp),
                                            focusedBorderColor = NetflixRed,
                                            focusedScale = 1.06f
                                        )
                                        .testTag("next_episode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Prossimo Ep.",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlay automatico "Prossimo Episodio tra 10s" per visione continua da divano TV
        AnimatedVisibility(
            visible = showAutoNext,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixCardSurface.copy(alpha = 0.96f)),
                border = BorderStroke(1.5.dp, NetflixRed),
                elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
                modifier = Modifier
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
                            text = "PROSSIMO EPISODIO IN ${autoNextRemainingSec}s",
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
                        val thumb = nextEpisodeDto?.stillUrl ?: session.mediaItem.backdropUrl ?: session.mediaItem.posterUrl
                        if (!thumb.isNullOrBlank()) {
                            AsyncImage(
                                model = thumb,
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
                        progress = { ((10 - autoNextRemainingSec) / 10f).coerceIn(0f, 1f) },
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
                            onClick = onNextEpisode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NetflixWhite,
                                contentColor = NetflixBlack
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .safeFocusRequester(autoNextPlayFocusRequester)
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = NetflixRed,
                                    focusedScale = 1.04f
                                )
                                .testTag("auto_next_play_now_button")
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Riproduci Ora", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { autoNextDismissed = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                            border = BorderStroke(1.dp, NetflixElevatedSurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = NetflixWhite,
                                    focusedScale = 1.04f
                                )
                                .testTag("auto_next_cancel_button")
                        ) {
                            Text("Annulla")
                        }
                    }
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
private fun VixStreamLoadingPopup(
    session: PlaybackSession,
    streamLang: String,
    bufferPreset: StreamingBufferPreset,
    cancelFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
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
                            model = imageUrl,
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
                        .focusProperties {
                            upFocusRequester.takeIfAttached()?.let { up = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(8.dp),
                            focusedBorderColor = NetflixRed,
                            focusedScale = 1.04f
                        )
                        .testTag("loading_cancel_button")
                ) {
                    Text("Annulla", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
