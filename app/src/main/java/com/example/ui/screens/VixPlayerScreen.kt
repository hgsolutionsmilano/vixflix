package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.remote.ResolvedStream
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.components.TvSeekTimeBar
import com.example.ui.components.formatSecondsToMmSs
import com.example.ui.components.rememberIsTvDevice
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
@Composable
fun VixPlayerScreen(
    session: PlaybackSession,
    streamLang: String,
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
    val isTvDevice = rememberIsTvDevice()
    val playPauseFocusRequester = remember { FocusRequester() }
    val timeBarFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

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

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Imposta la lingua audio preferita (ita / eng) nel selettore tracce di ExoPlayer
    LaunchedEffect(streamLang) {
        val preferredIso = if (streamLang.equals("it", ignoreCase = true)) "ita" else "eng"
        exoPlayer.trackSelectionParameters = TrackSelectionParameters.Builder(context)
            .setPreferredAudioLanguage(preferredIso)
            .build()
    }

    BackHandler {
        if (currentPositionMs > 0L) {
            onProgressUpdate(currentPositionMs, durationMs)
        }
        onClose()
    }

    // Configura HlsMediaSource in Media3 ExoPlayer quando lo stream reale di vixsrc.to è risolto
    LaunchedEffect(resolvedStream?.streamUrl, session.isResolvingStream) {
        if (session.isResolvingStream) return@LaunchedEffect
        val stream = resolvedStream
        if (stream == null || !stream.isDirectHls) {
            isBuffering = false
            return@LaunchedEffect
        }

        playerErrorMessage = null
        isBuffering = true
        val targetUri = Uri.parse(stream.streamUrl)
        val mediaItem = ExoMediaItem.Builder()
            .setUri(targetUri)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val mediaSource = HlsMediaSource.Factory(okHttpDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)

        exoPlayer.setMediaSource(mediaSource)
        if (session.startPositionMs > 0L && !hasSeekedInitial) {
            exoPlayer.seekTo(session.startPositionMs)
            hasSeekedInitial = true
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
                    val d = exoPlayer.duration
                    if (d != C.TIME_UNSET && d > 0L) {
                        durationMs = d
                    }
                    if (session.startPositionMs > 0L && !hasSeekedInitial) {
                        val safeSeek = if (durationMs > 0L) {
                            session.startPositionMs.coerceAtMost((durationMs - 5_000L).coerceAtLeast(0L))
                        } else {
                            session.startPositionMs
                        }
                        exoPlayer.seekTo(safeSeek)
                        currentPositionMs = safeSeek
                        hasSeekedInitial = true
                    }
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

    // Auto-hide dei controlli dopo 4.5 secondi (sospeso durante lo scorrimento sulla barra del tempo)
    LaunchedEffect(
        controlsVisible,
        isPlaying,
        playerErrorMessage,
        isBuffering,
        session.isResolvingStream,
        isRemoteScrubbing,
        interactionTick
    ) {
        if (controlsVisible && isPlaying && !isBuffering && !session.isResolvingStream && !isRemoteScrubbing && playerErrorMessage == null) {
            delay(4_500L)
            controlsVisible = false
            if (isTvDevice) {
                runCatching { rootFocusRequester.requestFocus() }
            }
        }
    }

    // Sposta automaticamente il focus sui controlli TV solo quando compaiono (senza rubare focus alla barra del tempo durante i seek)
    LaunchedEffect(controlsVisible, playerErrorMessage, session.errorMessage, isBuffering, session.isResolvingStream) {
        val hasErr = (playerErrorMessage ?: session.errorMessage) != null && !session.isResolvingStream
        if (hasErr) {
            runCatching { retryFocusRequester.requestFocus() }
            wasControlsVisible = false
            return@LaunchedEffect
        }

        val readyControls = controlsVisible && !isBuffering && !session.isResolvingStream
        if (readyControls) {
            if (!wasControlsVisible && !isTimeBarFocused) {
                if (focusTimeBarOnReveal) {
                    focusTimeBarOnReveal = false
                    runCatching { timeBarFocusRequester.requestFocus() }
                } else {
                    runCatching { playPauseFocusRequester.requestFocus() }
                }
            }
            wasControlsVisible = true
        } else if (!controlsVisible) {
            wasControlsVisible = false
            focusTimeBarOnReveal = false
            if (isTvDevice && !isBuffering && !session.isResolvingStream) {
                runCatching { rootFocusRequester.requestFocus() }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                interactionTick++
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.MediaPlayPause -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaPlay -> {
                            exoPlayer.play()
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaPause -> {
                            exoPlayer.pause()
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaFastForward -> {
                            val target = exoPlayer.currentPosition + 10_000L
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaRewind -> {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
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
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                }
        )

        val currentError = playerErrorMessage ?: session.errorMessage
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
                    onClick = { onToggleLanguage(streamLang) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .focusRequester(retryFocusRequester)
                        .tvFocusableControl(shape = RoundedCornerShape(8.dp), focusedScale = 1.06f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Riprova Connessione VixSrc")
                }
            }
        } else if (isBuffering || session.isResolvingStream) {
            // Popup di Loading in fase di avvio/attesa dello streaming con dati completi del video e stagione/episodio
            VixStreamLoadingPopup(
                session = session,
                streamLang = streamLang,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
            )
        }

        // Overlay Top Bar
        AnimatedVisibility(
            visible = controlsVisible || currentError != null || isBuffering || session.isResolvingStream,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Selettore Lingua Audio (ITA / ENG)
                    Surface(
                        color = NetflixCardSurface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NetflixElevatedSurface),
                        modifier = Modifier.tvFocusableControl(
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
                    val vixEmbedUrl = resolvedStream?.embedUrl
                    if (!vixEmbedUrl.isNullOrBlank()) {
                        Surface(
                            color = NetflixCardSurface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, NetflixElevatedSurface),
                            modifier = Modifier
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
            visible = controlsVisible && currentError == null && !isBuffering && !session.isResolvingStream,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Pulsanti centrali -10s / Play-Pause / +10s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            onProgressUpdate(currentPositionMs, durationMs)
                        },
                        modifier = Modifier
                            .size(54.dp)
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
                            .focusRequester(playPauseFocusRequester)
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        compact = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_time_bar")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier.tvFocusableControl(shape = CircleShape, focusedScale = 1.12f)
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
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    color = NetflixRed,
                    strokeWidth = 2.2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (session.isResolvingStream) {
                        "Connessione a vixsrc.to e recupero masterPlaylist HLS…"
                    } else {
                        "Preparazione flusso video in corso su ExoPlayer…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = NetflixWhite
                )
            }
        }
    }
}
