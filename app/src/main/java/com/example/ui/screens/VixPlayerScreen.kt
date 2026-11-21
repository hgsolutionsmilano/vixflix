package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.VixFlixApplication
import com.example.data.local.PlayerAdvancedSettings
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.isIntroActiveAtPosition
import com.example.data.local.resolveCreditsTrigger
import com.example.data.remote.ResolvedStream
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.components.DpadFocusObserverRegistry
import com.example.ui.components.DpadFocusScope
import com.example.ui.components.DpadFocusScopeProvider
import com.example.ui.components.rememberIsTvDevice
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

private const val LOW_RAM_MAX_BUFFER_BYTES = 32 * 1024 * 1024
private const val LOW_RAM_BACK_BUFFER_MS = 8_000
private const val STANDARD_BACK_BUFFER_MS = 15_000

private enum class PlayerFocusRestoreTarget {
    NONE,
    AUDIO_BUTTON,
    VIX_LINK_BUTTON
}

@OptIn(UnstableApi::class)
internal fun StreamingBufferPreset.toExoLoadControl(
    isLowRamDevice: Boolean = false
): DefaultLoadControl {
    val effectiveTargetBytes = if (isLowRamDevice) {
        minOf(targetBufferBytes, LOW_RAM_MAX_BUFFER_BYTES)
    } else {
        targetBufferBytes
    }
    val effectiveBackBufferMs = if (isLowRamDevice) {
        LOW_RAM_BACK_BUFFER_MS
    } else {
        STANDARD_BACK_BUFFER_MS
    }
    return DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
        .setBufferDurationsMs(
            minBufferMs,
            maxBufferMs,
            bufferForPlaybackMs,
            bufferForPlaybackAfterRebufferMs
        )
        .setBackBuffer(effectiveBackBufferMs, true)
        .setTargetBufferBytes(effectiveTargetBytes)
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
    onClose: () -> Unit,
    onInvalidateAndRetryStream: (() -> Unit)? = null,
    onPreResolveNextEpisode: () -> Unit = {},
    advancedSettings: PlayerAdvancedSettings = PlayerAdvancedSettings(),
    onCompleteMovieAndClose: () -> Unit = onClose,
    initialDurationMsOverride: Long = 0L,
    initialReadyForTest: Boolean = false,
    onSkipIntroTriggered: ((Long) -> Unit)? = null,
    previewBitmapProvider: ((Long) -> Bitmap?)? = null,
    audioTracksOverrideForTest: List<PlayerAudioTrackOption>? = null,
    onAudioTrackSelectedForTest: ((PlayerAudioTrackOption) -> Unit)? = null,
    onOpenVixSrcLinkOverrideForTest: ((String) -> Unit)? = null,
    simulateBufferingOnSeekForTest: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLowRamDevice = remember(context) { VixFlixApplication.isLowRamDevice(context) }
    val resolvedStream = session.resolvedStream

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember(initialReadyForTest) { mutableStateOf(!initialReadyForTest) }
    var playerErrorMessage by remember(session.errorMessage) { mutableStateOf(session.errorMessage) }

    // Stato posizione/durata mantenuto come State holder per evitare ricomposizioni ogni 1s del root
    val currentPositionMsState = remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableLongStateOf(session.startPositionMs)
    }
    LaunchedEffect(session.startPositionMs, session.isResolvingStream) {
        if (session.isResolvingStream) {
            currentPositionMsState.longValue = session.startPositionMs
        }
    }
    val durationMsState = remember(
        session.mediaItem.id,
        session.seasonNumber,
        session.episodeNumber,
        initialDurationMsOverride
    ) {
        mutableLongStateOf(initialDurationMsOverride)
    }
    var hasSeekedInitial by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var hasPreResolvedNext by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableLongStateOf(0L) }
    var isRemoteScrubbing by remember { mutableStateOf(false) }
    var isManualSeekInProgress by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var isTimeBarFocused by remember { mutableStateOf(false) }
    var focusTimeBarOnReveal by remember { mutableStateOf(false) }
    var wasControlsVisible by remember { mutableStateOf(false) }
    var showAudioSelector by remember { mutableStateOf(false) }
    var showVixLinkPanel by remember { mutableStateOf(false) }
    var pendingFocusRestore by remember { mutableStateOf(PlayerFocusRestoreTarget.NONE) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var selectedOverrideAudioId by remember(audioTracksOverrideForTest) {
        mutableStateOf(audioTracksOverrideForTest?.firstOrNull { it.isSelected }?.id)
    }
    var playbackEnded by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var introSkipped by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var introDismissed by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var autoNextDismissed by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    var movieEndedDismissed by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }
    val autoNextRemainingSecState = remember(
        session.mediaItem.id,
        session.seasonNumber,
        session.episodeNumber,
        advancedSettings.sanitizedCountdownSec
    ) {
        mutableIntStateOf(advancedSettings.sanitizedCountdownSec)
    }
    var retryNonce by remember { mutableIntStateOf(0) }
    var hasAutoRetriedCachedStream by remember(session.mediaItem.id, session.seasonNumber, session.episodeNumber) {
        mutableStateOf(false)
    }

    val isTvDevice = rememberIsTvDevice()
    val timeBarFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val audioButtonFocusRequester = remember { FocusRequester() }
    val vixLinkButtonFocusRequester = remember { FocusRequester() }
    val skipIntroFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val cancelLoadingFocusRequester = remember { FocusRequester() }
    val autoNextPlayFocusRequester = remember { FocusRequester() }
    val autoNextCancelFocusRequester = remember { FocusRequester() }
    val movieEndedFinishFocusRequester = remember { FocusRequester() }
    val movieEndedContinueFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    val currentSeason = session.seasonNumber ?: 1
    val currentEpisode = session.episodeNumber ?: 1
    val verifiedNextEpisode = session.verifiedNextEpisode
    val hasVerifiedNextEpisode = session.mediaItem.isSeries && verifiedNextEpisode != null
    val currentError = playerErrorMessage ?: session.errorMessage

    // Sopprime gli indicatori grafici di loading durante la ricerca manuale sulla barra temporale
    // senza alterare la gestione tecnica di isBuffering, sync o errori reali del player.
    val suppressSeekLoadingUi = isRemoteScrubbing || isManualSeekInProgress
    val showLoadingOverlay = currentError == null && (isBuffering || session.isResolvingStream) && !suppressSeekLoadingUi

    val showSkipIntro by remember(
        session.mediaItem.isSeries,
        session.introSegment,
        advancedSettings.skipIntroEnabled,
        currentError,
        isBuffering,
        session.isResolvingStream,
        isRemoteScrubbing,
        isManualSeekInProgress,
        introSkipped,
        introDismissed
    ) {
        derivedStateOf {
            session.mediaItem.isSeries &&
                advancedSettings.skipIntroEnabled &&
                currentError == null &&
                !isBuffering &&
                !session.isResolvingStream &&
                !isRemoteScrubbing &&
                !isManualSeekInProgress &&
                !introSkipped &&
                !introDismissed &&
                isIntroActiveAtPosition(
                    segment = session.introSegment,
                    positionMs = currentPositionMsState.longValue,
                    durationMs = durationMsState.longValue
                )
        }
    }

    val creditsTrigger by remember(
        session.creditsSegment,
        session.mediaItem.isSeries,
        advancedSettings.creditsDetectionEnabled
    ) {
        derivedStateOf {
            resolveCreditsTrigger(
                segment = session.creditsSegment,
                durationMs = durationMsState.longValue,
                isSeries = session.mediaItem.isSeries,
                creditsDetectionEnabled = advancedSettings.creditsDetectionEnabled
            )
        }
    }

    val inCreditsWindow by remember(creditsTrigger) {
        derivedStateOf {
            val trigger = creditsTrigger
            trigger != null && currentPositionMsState.longValue >= trigger.creditsStartMs
        }
    }

    val showAutoNext = hasVerifiedNextEpisode &&
        advancedSettings.nextEpisodePromptEnabled &&
        currentError == null &&
        !isBuffering &&
        !session.isResolvingStream &&
        !autoNextDismissed &&
        !isRemoteScrubbing &&
        !isManualSeekInProgress &&
        !showAudioSelector &&
        !showVixLinkPanel &&
        (playbackEnded || inCreditsWindow)

    val showMovieEndedPopup = !session.mediaItem.isSeries &&
        currentError == null &&
        !isBuffering &&
        !session.isResolvingStream &&
        !movieEndedDismissed &&
        !isRemoteScrubbing &&
        !isManualSeekInProgress &&
        !showAudioSelector &&
        !showVixLinkPanel &&
        (playbackEnded || inCreditsWindow)

    val anyBlockingPopupVisible = showAutoNext || showMovieEndedPopup
    val anySubPanelVisible = showAudioSelector || showVixLinkPanel

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

    val exoPlayer = remember(context, bufferPreset, isLowRamDevice) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setViewportSizeToPhysicalDisplaySize(context, true)
                .setForceHighestSupportedBitrate(false)
                .build()
        }
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(bufferPreset.toExoLoadControl(isLowRamDevice = isLowRamDevice))
            .setAudioAttributes(audioAttributes, true)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Mette in pausa il decoder hardware e salva i progressi quando l'app va in background (tasto Home Fire TV)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
                val pos = exoPlayer.currentPosition.coerceAtLeast(currentPositionMsState.longValue)
                val dur = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: durationMsState.longValue
                if (pos > 0L) {
                    onProgressUpdate(pos, dur)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

    val togglePlayPause: () -> Unit = {
        val currentlyPlaying = if (initialReadyForTest && exoPlayer.playbackState == Player.STATE_IDLE) {
            isPlaying
        } else {
            exoPlayer.isPlaying
        }
        if (currentlyPlaying) {
            exoPlayer.pause()
            isPlaying = false
        } else {
            exoPlayer.play()
            isPlaying = true
        }
        interactionTick++
    }

    val closeAudioSelector: () -> Unit = {
        showAudioSelector = false
        interactionTick++
        pendingFocusRestore = PlayerFocusRestoreTarget.AUDIO_BUTTON
    }

    val closeVixLinkPanel: () -> Unit = {
        showVixLinkPanel = false
        interactionTick++
        pendingFocusRestore = PlayerFocusRestoreTarget.VIX_LINK_BUTTON
    }

    val performSkipIntro: () -> Unit = {
        val targetMs = session.introSegment?.introEndMs
        if (targetMs != null && targetMs > 0L) {
            introSkipped = true
            exoPlayer.seekTo(targetMs)
            currentPositionMsState.longValue = targetMs
            onSkipIntroTriggered?.invoke(targetMs)
            onProgressUpdate(targetMs, durationMsState.longValue)
            if (!controlsVisible) {
                rootFocusRequester.safeRequestFocus()
            }
        }
    }

    val dismissMovieEndedPopup: () -> Unit = {
        movieEndedDismissed = true
        rootFocusRequester.safeRequestFocus()
    }

    BackHandler {
        when {
            showAudioSelector -> closeAudioSelector()
            showVixLinkPanel -> closeVixLinkPanel()
            isRemoteScrubbing -> {
                isRemoteScrubbing = false
                timeBarFocusRequester.safeRequestFocus()
            }
            showMovieEndedPopup -> dismissMovieEndedPopup()
            showAutoNext -> {
                autoNextDismissed = true
                rootFocusRequester.safeRequestFocus()
            }
            controlsVisible && currentError == null && !showLoadingOverlay -> {
                controlsVisible = false
                rootFocusRequester.safeRequestFocus()
            }
            showSkipIntro -> {
                introDismissed = true
                rootFocusRequester.safeRequestFocus()
            }
            else -> {
                val pos = currentPositionMsState.longValue
                if (pos > 0L) {
                    onProgressUpdate(pos, durationMsState.longValue)
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
        if (initialReadyForTest) {
            isBuffering = false
            return@LaunchedEffect
        }
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

        val resumePosMs = maxOf(session.startPositionMs, currentPositionMsState.longValue)
        if (resumePosMs > 0L) {
            exoPlayer.setMediaSource(mediaSource, resumePosMs)
        } else {
            exoPlayer.setMediaSource(mediaSource)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Listener eventi Player + salvataggio su Room Database + aggiornamento tracce audio reali
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerErrorMessage = null
                    playbackEnded = false
                    if (!isRemoteScrubbing) {
                        isManualSeekInProgress = false
                    }
                    val d = exoPlayer.duration
                    if (d != C.TIME_UNSET && d > 0L) {
                        durationMsState.longValue = d
                    }
                    if (!hasSeekedInitial) {
                        hasSeekedInitial = true
                        val targetStart = maxOf(session.startPositionMs, currentPositionMsState.longValue)
                        val dur = durationMsState.longValue
                        if (targetStart > 0L && dur > 0L) {
                            val maxSafePos = (dur - 5_000L).coerceAtLeast(0L)
                            if (exoPlayer.currentPosition > maxSafePos) {
                                exoPlayer.seekTo(maxSafePos)
                                currentPositionMsState.longValue = maxSafePos
                            }
                        }
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isBuffering = false
                    isPlaying = false
                    isManualSeekInProgress = false
                    playbackEnded = true
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && exoPlayer.currentPosition > 0L) {
                    onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isManualSeekInProgress = false
                if (resolvedStream?.fromCache == true && !hasAutoRetriedCachedStream && onInvalidateAndRetryStream != null) {
                    hasAutoRetriedCachedStream = true
                    isBuffering = true
                    playerErrorMessage = null
                    onInvalidateAndRetryStream.invoke()
                    return
                }
                isBuffering = false
                playerErrorMessage = "Errore stream VixSrc: ${error.errorCodeName} (${error.localizedMessage ?: "connessione interrotta"})"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val finalPos = currentPositionMsState.longValue.coerceAtLeast(exoPlayer.currentPosition)
            val finalDur = durationMsState.longValue.coerceAtLeast(exoPlayer.duration.coerceAtLeast(0L))
            if (finalPos > 0L) {
                onProgressUpdate(finalPos, finalDur)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Loop di aggiornamento posizione ogni 1s + persistenza su Room ogni 10s + pre-risoluzione silenziosa prossimo episodio
    LaunchedEffect(exoPlayer) {
        var tick = 0
        while (true) {
            if (exoPlayer.isPlaying && !isRemoteScrubbing) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: durationMsState.longValue
                if (pos > 0L) {
                    currentPositionMsState.longValue = pos
                    durationMsState.longValue = dur
                    if (tick % 10 == 0) {
                        onProgressUpdate(pos, dur)
                    }
                    if (!hasPreResolvedNext && hasVerifiedNextEpisode && dur > 120_000L && pos >= (dur - 90_000L)) {
                        hasPreResolvedNext = true
                        onPreResolveNextEpisode()
                    }
                    tick++
                }
            }
            delay(1_000L)
        }
    }

    // Timer conto alla rovescia configurabile Prossimo Episodio (auto-play solo se attivo nelle impostazioni)
    LaunchedEffect(
        showAutoNext,
        currentSeason,
        currentEpisode,
        advancedSettings.autoPlayNextEpisodeEnabled,
        advancedSettings.sanitizedCountdownSec
    ) {
        if (!showAutoNext) return@LaunchedEffect
        autoNextRemainingSecState.intValue = advancedSettings.sanitizedCountdownSec
        controlsVisible = false
        delay(60L)
        autoNextPlayFocusRequester.safeRequestFocus()

        if (!advancedSettings.autoPlayNextEpisodeEnabled) {
            return@LaunchedEffect
        }

        while (autoNextRemainingSecState.intValue > 0) {
            delay(1_000L)
            autoNextRemainingSecState.intValue--
        }
        if (showAutoNext && !autoNextDismissed && advancedSettings.autoPlayNextEpisodeEnabled) {
            onNextEpisode()
        }
    }

    // Auto-focus sul pulsante primario del popup "IL FILM È TERMINATO"
    LaunchedEffect(showMovieEndedPopup) {
        if (!showMovieEndedPopup) return@LaunchedEffect
        controlsVisible = false
        delay(60L)
        movieEndedFinishFocusRequester.safeRequestFocus()
    }

    // Auto-focus sul pulsante "SALTA INTRO" quando i controlli sono nascosti per conferma immediata con tasto OK
    LaunchedEffect(showSkipIntro, controlsVisible, anyBlockingPopupVisible, anySubPanelVisible, isRemoteScrubbing) {
        if (showSkipIntro && !controlsVisible && !anyBlockingPopupVisible && !anySubPanelVisible && !isRemoteScrubbing) {
            delay(60L)
            skipIntroFocusRequester.safeRequestFocus()
        }
    }

    // Ripristino deterministico del focus dopo la chiusura dei pannelli Audio o Link VixSrc
    LaunchedEffect(pendingFocusRestore, showAudioSelector, showVixLinkPanel) {
        if (pendingFocusRestore != PlayerFocusRestoreTarget.NONE && !showAudioSelector && !showVixLinkPanel) {
            delay(50L)
            when (pendingFocusRestore) {
                PlayerFocusRestoreTarget.AUDIO_BUTTON -> audioButtonFocusRequester.safeRequestFocus()
                PlayerFocusRestoreTarget.VIX_LINK_BUTTON -> vixLinkButtonFocusRequester.safeRequestFocus()
                PlayerFocusRestoreTarget.NONE -> Unit
            }
            pendingFocusRestore = PlayerFocusRestoreTarget.NONE
        }
    }

    // Ripristina automaticamente il flag di seek manuale se lo spostamento è avvenuto dentro il buffer già pronto
    LaunchedEffect(isManualSeekInProgress, isRemoteScrubbing, isBuffering) {
        if (isManualSeekInProgress && !isRemoteScrubbing && !isBuffering) {
            delay(800L)
            if (!isRemoteScrubbing && !isBuffering) {
                isManualSeekInProgress = false
            }
        }
    }

    // Auto-hide dei controlli dopo 4.5 secondi (sospeso durante lo scrubbing/seek manuale o con pannelli Audio / Link aperti)
    LaunchedEffect(
        controlsVisible,
        isPlaying,
        playerErrorMessage,
        isBuffering,
        session.isResolvingStream,
        isRemoteScrubbing,
        isManualSeekInProgress,
        anyBlockingPopupVisible,
        anySubPanelVisible,
        interactionTick
    ) {
        if (
            controlsVisible &&
            isPlaying &&
            !isBuffering &&
            !session.isResolvingStream &&
            !isRemoteScrubbing &&
            !isManualSeekInProgress &&
            playerErrorMessage == null &&
            !anyBlockingPopupVisible &&
            !anySubPanelVisible
        ) {
            delay(4_500L)
            controlsVisible = false
            if (showSkipIntro) {
                skipIntroFocusRequester.safeRequestFocus()
            } else if (isTvDevice) {
                rootFocusRequester.safeRequestFocus()
            }
        }
    }

    // Gestione pulita del focus TV quando i controlli compaiono/scompaiono (mai rubato durante un seek manuale)
    LaunchedEffect(
        controlsVisible,
        playerErrorMessage,
        session.errorMessage,
        showLoadingOverlay,
        anyBlockingPopupVisible,
        anySubPanelVisible
    ) {
        if (anyBlockingPopupVisible || anySubPanelVisible) {
            return@LaunchedEffect
        }
        val hasErr = (playerErrorMessage ?: session.errorMessage) != null && !session.isResolvingStream
        if (hasErr) {
            retryFocusRequester.safeRequestFocus()
            wasControlsVisible = false
            return@LaunchedEffect
        }

        if (showLoadingOverlay) {
            wasControlsVisible = false
            if (isTvDevice) {
                delay(60L)
                cancelLoadingFocusRequester.safeRequestFocus()
            }
            return@LaunchedEffect
        }

        if (controlsVisible) {
            if (!wasControlsVisible && !isTimeBarFocused && pendingFocusRestore == PlayerFocusRestoreTarget.NONE) {
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
            if (showSkipIntro) {
                skipIntroFocusRequester.safeRequestFocus()
            } else if (isTvDevice) {
                rootFocusRequester.safeRequestFocus()
            }
        }
    }

    val availableAudioTracks = remember(exoPlayer, streamLang, tracksVersion, audioTracksOverrideForTest, selectedOverrideAudioId) {
        if (audioTracksOverrideForTest != null) {
            audioTracksOverrideForTest.map { option ->
                if (selectedOverrideAudioId != null) {
                    option.copy(isSelected = option.id == selectedOverrideAudioId)
                } else {
                    option
                }
            }
        } else {
            extractPlayerAudioTrackOptions(exoPlayer = exoPlayer, currentStreamLang = streamLang)
        }
    }

    val liveVixSrcUrl = remember(
        session.mediaItem.id,
        session.mediaItem.mediaType,
        session.seasonNumber,
        session.episodeNumber,
        streamLang,
        resolvedStream?.embedUrl,
        showVixLinkPanel
    ) {
        val currentSec = (currentPositionMsState.longValue / 1000L).coerceAtLeast(0L)
        vixSrcResolver.buildVixSrcApiUrl(
            tmdbId = session.mediaItem.id,
            mediaType = session.mediaItem.mediaType,
            season = session.seasonNumber,
            episode = session.episodeNumber,
            startSeconds = currentSec,
            lang = streamLang
        ).ifBlank { resolvedStream?.embedUrl.orEmpty() }
    }

    DpadFocusScopeProvider(
        scope = DpadFocusScope.VIDEO_PLAYER,
        onRecoverFocus = {
            when {
                showMovieEndedPopup && movieEndedFinishFocusRequester.safeRequestFocus() -> "movie_ended_finish_button"
                showAutoNext && autoNextPlayFocusRequester.safeRequestFocus() -> "auto_next_play_now_button"
                currentError != null && !session.isResolvingStream && retryFocusRequester.safeRequestFocus() -> "player_retry_stream_button"
                showLoadingOverlay && cancelLoadingFocusRequester.safeRequestFocus() -> "loading_cancel_button"
                showSkipIntro && !controlsVisible && !isRemoteScrubbing && !anySubPanelVisible && skipIntroFocusRequester.safeRequestFocus() -> "player_skip_intro_button"
                controlsVisible && !anySubPanelVisible && playPauseFocusRequester.safeRequestFocus() -> "exo_play_pause_button"
                controlsVisible && !anySubPanelVisible && timeBarFocusRequester.safeRequestFocus() -> "player_time_bar"
                rootFocusRequester.safeRequestFocus() -> "vix_player_root"
                else -> null
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeFocusRequester(rootFocusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        DpadFocusObserverRegistry.onNodeFocusChanged(
                            scope = DpadFocusScope.VIDEO_PLAYER,
                            tag = "vix_player_root",
                            isFocused = true
                        )
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    interactionTick++
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.Menu -> {
                                if (!anyBlockingPopupVisible && !anySubPanelVisible && currentError == null && !showLoadingOverlay) {
                                    controlsVisible = !controlsVisible
                                    if (!controlsVisible && isTvDevice) {
                                        rootFocusRequester.safeRequestFocus()
                                    }
                                    return@onPreviewKeyEvent true
                                }
                            }
                            Key.Escape -> {
                                // Se la barra del tempo è in scrubbing, lascia che TvSeekTimeBar annulli lo scrubbing
                                if (isRemoteScrubbing) {
                                    return@onPreviewKeyEvent false
                                }
                                when {
                                    showAudioSelector -> closeAudioSelector()
                                    showVixLinkPanel -> closeVixLinkPanel()
                                    showMovieEndedPopup -> dismissMovieEndedPopup()
                                    showAutoNext -> {
                                        autoNextDismissed = true
                                        rootFocusRequester.safeRequestFocus()
                                    }
                                    controlsVisible && currentError == null && !showLoadingOverlay -> {
                                        controlsVisible = false
                                        rootFocusRequester.safeRequestFocus()
                                    }
                                    showSkipIntro -> {
                                        introDismissed = true
                                        rootFocusRequester.safeRequestFocus()
                                    }
                                    else -> {
                                        val pos = currentPositionMsState.longValue
                                        if (pos > 0L) {
                                            onProgressUpdate(pos, durationMsState.longValue)
                                        }
                                        onClose()
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaPlayPause -> {
                                togglePlayPause()
                                if (!isPlaying) controlsVisible = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaPlay -> {
                                exoPlayer.play()
                                isPlaying = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaPause -> {
                                exoPlayer.pause()
                                isPlaying = false
                                controlsVisible = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaFastForward -> {
                                val maxDur = durationMsState.longValue.takeIf { it > 0L } ?: Long.MAX_VALUE
                                val target = (currentPositionMsState.longValue + 10_000L).coerceAtMost(maxDur)
                                isManualSeekInProgress = true
                                if (simulateBufferingOnSeekForTest) isBuffering = true
                                exoPlayer.seekTo(target)
                                currentPositionMsState.longValue = target
                                controlsVisible = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaRewind -> {
                                val target = (currentPositionMsState.longValue - 10_000L).coerceAtLeast(0L)
                                isManualSeekInProgress = true
                                if (simulateBufferingOnSeekForTest) isBuffering = true
                                exoPlayer.seekTo(target)
                                currentPositionMsState.longValue = target
                                playbackEnded = false
                                controlsVisible = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaNext -> {
                                if (hasVerifiedNextEpisode) {
                                    onNextEpisode()
                                    return@onPreviewKeyEvent true
                                }
                            }
                            else -> Unit
                        }

                        val canRevealOrSeek = !controlsVisible &&
                            !anyBlockingPopupVisible &&
                            !anySubPanelVisible &&
                            playerErrorMessage == null &&
                            session.errorMessage == null &&
                            !showLoadingOverlay
                        if (canRevealOrSeek) {
                            when (keyEvent.key) {
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    if (!showSkipIntro) {
                                        controlsVisible = true
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                Key.DirectionUp, Key.DirectionDown -> {
                                    controlsVisible = true
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionLeft -> {
                                    val target = (currentPositionMsState.longValue - 10_000L).coerceAtLeast(0L)
                                    isManualSeekInProgress = true
                                    if (simulateBufferingOnSeekForTest) isBuffering = true
                                    exoPlayer.seekTo(target)
                                    currentPositionMsState.longValue = target
                                    playbackEnded = false
                                    onProgressUpdate(target, durationMsState.longValue)
                                    focusTimeBarOnReveal = true
                                    controlsVisible = true
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionRight -> {
                                    val maxDur = durationMsState.longValue.takeIf { it > 0L } ?: Long.MAX_VALUE
                                    val target = (currentPositionMsState.longValue + 10_000L).coerceAtMost(maxDur)
                                    isManualSeekInProgress = true
                                    if (simulateBufferingOnSeekForTest) isBuffering = true
                                    exoPlayer.seekTo(target)
                                    currentPositionMsState.longValue = target
                                    onProgressUpdate(target, durationMsState.longValue)
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
            // 1. VIDEO (Vista nativa 100% Media3 ExoPlayer, completamente pulita nella parte superiore)
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        isFocusable = false
                        isFocusableInTouchMode = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                },
                onRelease = { view ->
                    view.player = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
                    .pointerInput(anySubPanelVisible) {
                        detectTapGestures(
                            onTap = {
                                when {
                                    showAudioSelector -> closeAudioSelector()
                                    showVixLinkPanel -> closeVixLinkPanel()
                                    else -> controlsVisible = !controlsVisible
                                }
                            }
                        )
                    }
            )

            // Stato errore stream o loading
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
                            if (onInvalidateAndRetryStream != null) {
                                onInvalidateAndRetryStream.invoke()
                            } else {
                                onToggleLanguage(streamLang)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(retryFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight, Key.DirectionDown -> true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(8.dp),
                                focusedScale = 1.06f,
                                focusTag = "player_retry_stream_button"
                            )
                            .testTag("player_retry_stream_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Riprova Connessione VixSrc")
                    }
                }
            } else if (showLoadingOverlay) {
                VixStreamLoadingPopup(
                    session = session,
                    streamLang = streamLang,
                    bufferPreset = bufferPreset,
                    cancelFocusRequester = cancelLoadingFocusRequester,
                    upFocusRequester = null,
                    onCancel = {
                        val pos = currentPositionMsState.longValue
                        if (pos > 0L) {
                            onProgressUpdate(pos, durationMsState.longValue)
                        }
                        onClose()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 20.dp)
                )
            }

            // Controlli inferiori puliti e riorganizzati:
            // - Nessun controllo nella parte superiore
            // - Barra di avanzamento temporale a tutta larghezza
            // - In basso a sinistra: esclusivamente i 3 pulsanti circolari (Play/Pausa, Audio, Link VixSrc)
            // - In basso a destra: tempo di riproduzione attuale / durata totale ("00:35:42 / 01:52:18")
            AnimatedVisibility(
                visible = controlsVisible &&
                    currentError == null &&
                    !showLoadingOverlay &&
                    !anyBlockingPopupVisible &&
                    !anySubPanelVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    NetflixBlack.copy(alpha = 0.72f),
                                    NetflixBlack.copy(alpha = 0.95f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    if (isRemoteScrubbing) {
                        // Durante la ricerca manuale sulla barra: mostra ESCLUSIVAMENTE la miniatura del fotogramma reale
                        SeekThumbnailPreviewStrip(
                            isScrubbing = true,
                            previewPositionMsProvider = { currentPositionMsState.longValue },
                            durationMsProvider = { durationMsState.longValue },
                            streamUrl = resolvedStream?.streamUrl,
                            isDirectHls = resolvedStream?.isDirectHls == true,
                            okHttpDataSourceFactory = okHttpDataSourceFactory,
                            previewBitmapProvider = previewBitmapProvider,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        PlayerMediaTitleHeader(
                            session = session,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // BARRA DI AVANZAMENTO TEMPORALE
                    VixPlayerSeekBarIsolated(
                        positionMsProvider = { currentPositionMsState.longValue },
                        durationMsProvider = { durationMsState.longValue },
                        onSeekPreview = { previewMs ->
                            currentPositionMsState.longValue = previewMs
                            isManualSeekInProgress = true
                            if (simulateBufferingOnSeekForTest) isBuffering = true
                            interactionTick++
                        },
                        onSeekCommit = { targetMs ->
                            currentPositionMsState.longValue = targetMs
                            playbackEnded = false
                            isManualSeekInProgress = true
                            if (simulateBufferingOnSeekForTest) isBuffering = true
                            exoPlayer.seekTo(targetMs)
                            onProgressUpdate(targetMs, durationMsState.longValue)
                            interactionTick++
                        },
                        onTogglePlayPause = togglePlayPause,
                        onInteraction = { interactionTick++ },
                        onFocusChanged = { isTimeBarFocused = it },
                        onScrubbingChanged = { scrubbing ->
                            isRemoteScrubbing = scrubbing
                            if (scrubbing) {
                                isManualSeekInProgress = true
                            }
                            interactionTick++
                        },
                        focusRequester = timeBarFocusRequester,
                        upFocusRequester = if (showSkipIntro && !isRemoteScrubbing) skipIntroFocusRequester else null,
                        downFocusRequester = playPauseFocusRequester,
                        showAssistiveHeader = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_time_bar")
                    )

                    // ZONA INFERIORE: Pulsanti circolari a SINISTRA, Informazioni temporali a DESTRA
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("player_bottom_bar"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerCircularControlsRow(
                            isPlaying = isPlaying,
                            playPauseFocusRequester = playPauseFocusRequester,
                            audioFocusRequester = audioButtonFocusRequester,
                            vixLinkFocusRequester = vixLinkButtonFocusRequester,
                            upToTimeBarFocusRequester = timeBarFocusRequester,
                            onTogglePlayPause = togglePlayPause,
                            onOpenAudioSelector = {
                                showVixLinkPanel = false
                                showAudioSelector = true
                                interactionTick++
                            },
                            onOpenVixLinkPanel = {
                                showAudioSelector = false
                                showVixLinkPanel = true
                                interactionTick++
                            }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        PlayerCleanTimeDisplay(
                            positionMsProvider = { currentPositionMsState.longValue },
                            durationMsProvider = { durationMsState.longValue },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            // Selettore Tracce Audio (aperto dal pulsante circolare AUDIO in basso a sinistra)
            AnimatedVisibility(
                visible = showAudioSelector && currentError == null && !showLoadingOverlay,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                PlayerAudioSelectorPanel(
                    tracks = availableAudioTracks,
                    onSelectTrack = { selected ->
                        selectedOverrideAudioId = selected.id
                        onAudioTrackSelectedForTest?.invoke(selected)
                        if (audioTracksOverrideForTest == null) {
                            applyAudioTrackSelection(
                                exoPlayer = exoPlayer,
                                option = selected,
                                onToggleLanguage = onToggleLanguage
                            )
                            tracksVersion++
                        }
                        closeAudioSelector()
                    },
                    onDismiss = closeAudioSelector
                )
            }

            // Pannello Link VixSrc (aperto dal pulsante circolare LINK VIXSRC in basso a sinistra)
            AnimatedVisibility(
                visible = showVixLinkPanel && currentError == null && !showLoadingOverlay,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                PlayerVixSrcLinkPanel(
                    vixSrcUrl = liveVixSrcUrl,
                    onOpenExternalLink = {
                        if (onOpenVixSrcLinkOverrideForTest != null) {
                            onOpenVixSrcLinkOverrideForTest.invoke(liveVixSrcUrl)
                        } else if (liveVixSrcUrl.isNotBlank()) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(liveVixSrcUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        }
                        closeVixLinkPanel()
                    },
                    onCopyLink = {
                        if (liveVixSrcUrl.isNotBlank()) {
                            runCatching {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("VixSrc Link", liveVixSrcUrl))
                            }
                        }
                    },
                    onDismiss = closeVixLinkPanel
                )
            }

            // Pulsante fluttuante "SALTA INTRO" (nascosto durante scrubbing e pannelli aperti)
            AnimatedVisibility(
                visible = showSkipIntro && !anyBlockingPopupVisible && !anySubPanelVisible && !isRemoteScrubbing,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = 24.dp,
                        bottom = if (controlsVisible) 196.dp else 32.dp
                    )
            ) {
                SkipIntroFloatingButton(
                    focusRequester = skipIntroFocusRequester,
                    controlsVisible = controlsVisible,
                    leftFocusRequester = timeBarFocusRequester,
                    downFocusRequester = timeBarFocusRequester,
                    onSkipIntro = performSkipIntro
                )
            }

            // Overlay "PROSSIMO EPISODIO" per serie TV (solo con episodio successivo reale verificato)
            AnimatedVisibility(
                visible = showAutoNext && verifiedNextEpisode != null,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                verifiedNextEpisode?.let { nextEp ->
                    NextEpisodePopupCard(
                        session = session,
                        nextEpisode = nextEp,
                        autoPlayEnabled = advancedSettings.autoPlayNextEpisodeEnabled,
                        totalCountdownSec = advancedSettings.sanitizedCountdownSec,
                        remainingSecProvider = { autoNextRemainingSecState.intValue },
                        playFocusRequester = autoNextPlayFocusRequester,
                        cancelFocusRequester = autoNextCancelFocusRequester,
                        onPlayNow = onNextEpisode,
                        onCancel = {
                            autoNextDismissed = true
                            rootFocusRequester.safeRequestFocus()
                        }
                    )
                }
            }

            // Overlay "IL FILM È TERMINATO" per film (rilevamento titoli di coda / fine riproduzione)
            AnimatedVisibility(
                visible = showMovieEndedPopup,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                MovieEndedPopupCard(
                    movieTitle = session.mediaItem.title,
                    finishFocusRequester = movieEndedFinishFocusRequester,
                    continueFocusRequester = movieEndedContinueFocusRequester,
                    onFinishWatching = onCompleteMovieAndClose,
                    onContinueWatching = dismissMovieEndedPopup
                )
            }
        }
    }
}
