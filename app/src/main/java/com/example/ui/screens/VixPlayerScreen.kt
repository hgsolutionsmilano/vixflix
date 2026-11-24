package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.VixFlixApplication
import com.example.data.local.PlayerAdvancedSettings
import com.example.data.local.PlayerComfortSettings
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.SubtitleBackgroundStyle
import com.example.data.local.SubtitleTextColor
import com.example.data.local.SubtitleTextSize
import com.example.data.local.VideoAspectMode
import com.example.data.local.formatVideoResolutionLabel
import com.example.data.local.isIntroActiveAtPosition
import com.example.data.local.resolveCreditsTrigger
import com.example.data.remote.ResolvedStream
import com.example.data.remote.VixSrcDiagnosticEvent
import com.example.data.remote.VixSrcDiagnostics
import com.example.data.remote.VixSrcErrorCategory
import com.example.data.remote.VixSrcResolutionPhase
import com.example.data.remote.VixSrcStreamResolver
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
    COMFORT_BUTTON,
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
internal fun readVerifiedExoDurationMs(exoPlayer: ExoPlayer): Long {
    val direct = exoPlayer.duration
    if (direct != C.TIME_UNSET && direct > 0L) {
        return direct
    }
    val timeline = exoPlayer.currentTimeline
    if (!timeline.isEmpty) {
        val windowIndex = exoPlayer.currentMediaItemIndex
        if (windowIndex in 0 until timeline.windowCount) {
            val windowDuration = timeline.getWindow(windowIndex, Timeline.Window()).durationMs
            if (windowDuration != C.TIME_UNSET && windowDuration > 0L) {
                return windowDuration
            }
        }
    }
    return 0L
}

private fun inferSubtitleMimeType(url: String): String {
    val lower = url.lowercase()
    return when {
        lower.contains(".srt") -> MimeTypes.APPLICATION_SUBRIP
        lower.contains(".ttml") || lower.contains(".xml") -> MimeTypes.APPLICATION_TTML
        lower.contains(".ass") || lower.contains(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}

@OptIn(UnstableApi::class)
internal fun extractActiveVideoDimensions(exoPlayer: ExoPlayer): Pair<Int, Int> {
    val directSize = exoPlayer.videoSize
    if (directSize.width > 0 && directSize.height > 0) {
        return directSize.width to directSize.height
    }
    val groups = exoPlayer.currentTracks.groups
    for (group in groups) {
        if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
            for (trackIdx in 0 until group.length) {
                if (group.isTrackSelected(trackIdx)) {
                    val format = group.getTrackFormat(trackIdx)
                    if (format.width > 0 && format.height > 0) {
                        return format.width to format.height
                    }
                }
            }
        }
    }
    val format = exoPlayer.videoFormat
    if (format != null && format.width > 0 && format.height > 0) {
        return format.width to format.height
    }
    return 0 to 0
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Politica nativa Android / Fire TV Stick per impedire lo screensaver esclusivamente
 * durante una riproduzione video attiva (incluso buffering temporaneo con playWhenReady = true).
 * Viene sempre disattivata su pausa, fine contenuto, errore o app in background.
 */
internal fun shouldKeepScreenOnDuringPlayback(
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    isResolvingStream: Boolean,
    playbackEnded: Boolean,
    hasError: Boolean,
    isAppInBackground: Boolean
): Boolean {
    if (isAppInBackground || hasError || playbackEnded) return false
    if (isPlaying) return true
    return playWhenReady && (isBuffering || isResolvingStream)
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
    onPreviousEpisode: () -> Unit = {},
    onInvalidateAndRetryStream: (() -> Unit)? = null,
    onPreResolveNextEpisode: () -> Unit = {},
    advancedSettings: PlayerAdvancedSettings = PlayerAdvancedSettings(),
    comfortSettings: PlayerComfortSettings = PlayerComfortSettings(),
    onSelectVideoAspectMode: (VideoAspectMode) -> Unit = {},
    onSelectSubtitleTextSize: (SubtitleTextSize) -> Unit = {},
    onSelectSubtitleTextColor: (SubtitleTextColor) -> Unit = {},
    onSelectSubtitleBackgroundStyle: (SubtitleBackgroundStyle) -> Unit = {},
    onCompleteMovieAndClose: () -> Unit = onClose,
    initialDurationMsOverride: Long = 0L,
    initialReadyForTest: Boolean = false,
    onSkipIntroTriggered: ((Long) -> Unit)? = null,
    previewBitmapProvider: ((Long) -> Bitmap?)? = null,
    audioTracksOverrideForTest: List<PlayerAudioTrackOption>? = null,
    onAudioTrackSelectedForTest: ((PlayerAudioTrackOption) -> Unit)? = null,
    subtitleTracksOverrideForTest: List<PlayerSubtitleTrackOption>? = null,
    onSubtitleTrackSelectedForTest: ((PlayerSubtitleTrackOption) -> Unit)? = null,
    onOpenVixSrcLinkOverrideForTest: ((String) -> Unit)? = null,
    simulateBufferingOnSeekForTest: Boolean = false,
    videoResolutionOverrideForTest: String? = null,
    onSaveIntroSegment: ((Long, Long) -> Unit)? = null,
    onScrobbleStart: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onScrobblePause: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onScrobbleStop: (positionMs: Long, durationMs: Long, isCompleted: Boolean) -> Unit = { _, _, _ -> },
    onPlaybackCheckpoint: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onManualNextEpisode: ((positionMs: Long, durationMs: Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLowRamDevice = remember(context) { VixFlixApplication.isLowRamDevice(context) }
    val resolvedStream = session.resolvedStream
    val episodeKey = "${session.mediaItem.uniqueKey}_s${session.seasonNumber ?: 0}_e${session.episodeNumber ?: 0}_t${session.sessionToken}"

    // Stato persistente unico lungo tutto il ciclo di vita di VixPlayerScreen (MAI ricreato con remember(episodeKey)):
    // elimina definitivamente qualsiasi closure orfana nei listener di ExoPlayer quando si cambia episodio.
    var isPlaying by remember { mutableStateOf(session.errorMessage == null) }
    var playWhenReadyState by remember { mutableStateOf(session.errorMessage == null) }
    var isBuffering by remember(initialReadyForTest) { mutableStateOf(!initialReadyForTest && session.errorMessage == null) }
    var isAppInBackground by remember { mutableStateOf(false) }
    var playerErrorMessage by remember { mutableStateOf(session.errorMessage) }
    LaunchedEffect(session.errorMessage) {
        playerErrorMessage = session.errorMessage
        if (session.errorMessage != null) {
            isPlaying = false
            isBuffering = false
            playWhenReadyState = false
        }
    }

    val currentPositionMsState = remember {
        mutableLongStateOf(session.startPositionMs.coerceAtLeast(0L))
    }
    val durationMsState = remember {
        mutableLongStateOf(
            initialDurationMsOverride.takeIf { it > 0L }
                ?: session.metadataDurationMs?.takeIf { !session.isResolvingStream && it > 0L }
                ?: 0L
        )
    }
    var hasSeekedInitial by remember { mutableStateOf(false) }
    var hasPreResolvedNext by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableLongStateOf(0L) }
    var isRemoteScrubbing by remember { mutableStateOf(false) }
    var isManualSeekInProgress by remember { mutableStateOf(false) }
    var isTimeBarFocused by remember { mutableStateOf(false) }
    var focusTimeBarOnReveal by remember { mutableStateOf(false) }
    var wasControlsVisible by remember { mutableStateOf(false) }
    var showAudioSelector by remember { mutableStateOf(false) }
    var showComfortPanel by remember { mutableStateOf(false) }
    var showVixLinkPanel by remember { mutableStateOf(false) }
    var aspectToastNonce by remember { mutableIntStateOf(0) }
    var aspectToastMode by remember { mutableStateOf<VideoAspectMode?>(null) }
    var pendingFocusRestore by remember { mutableStateOf(PlayerFocusRestoreTarget.NONE) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    val videoWidthState = remember { mutableIntStateOf(0) }
    val videoHeightState = remember { mutableIntStateOf(0) }
    val activeResolutionLabel by remember(videoResolutionOverrideForTest, tracksVersion) {
        derivedStateOf {
            videoResolutionOverrideForTest?.takeIf { it.isNotBlank() }
                ?: formatVideoResolutionLabel(
                    width = videoWidthState.intValue,
                    height = videoHeightState.intValue
                )
        }
    }
    var selectedOverrideAudioId by remember(audioTracksOverrideForTest) {
        mutableStateOf(audioTracksOverrideForTest?.firstOrNull { it.isSelected }?.id)
    }
    var selectedOverrideSubtitleId by remember(subtitleTracksOverrideForTest) {
        mutableStateOf(subtitleTracksOverrideForTest?.firstOrNull { it.isSelected }?.id)
    }
    var playbackEnded by remember { mutableStateOf(false) }
    var introSkipped by remember { mutableStateOf(false) }
    var introDismissed by remember { mutableStateOf(false) }
    var autoNextDismissed by remember { mutableStateOf(false) }
    var movieEndedDismissed by remember { mutableStateOf(false) }
    val autoNextRemainingSecState = remember {
        mutableIntStateOf(advancedSettings.sanitizedCountdownSec)
    }
    var retryNonce by remember { mutableIntStateOf(0) }
    var hasAutoRetriedCachedStream by remember { mutableStateOf(false) }
    var scrobbleStartedForEpisode by remember { mutableStateOf(false) }
    var scrobblePausedSinceStart by remember { mutableStateOf(false) }
    var scrobbleStoppedForEpisode by remember { mutableStateOf(false) }

    val isTvDevice = rememberIsTvDevice()
    val timeBarFocusRequester = remember { FocusRequester() }
    val previousEpisodeFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val audioButtonFocusRequester = remember { FocusRequester() }
    val comfortButtonFocusRequester = remember { FocusRequester() }
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
    val verifiedPreviousEpisode = session.verifiedPreviousEpisode
    val hasVerifiedPreviousEpisode = session.mediaItem.isSeries && verifiedPreviousEpisode != null
    val verifiedNextEpisode = session.verifiedNextEpisode
    val hasVerifiedNextEpisode = session.mediaItem.isSeries && verifiedNextEpisode != null
    val currentError = playerErrorMessage ?: session.errorMessage
    val hasActivePlaybackError = currentError != null && !session.isResolvingStream
    val keepScreenOnActive = shouldKeepScreenOnDuringPlayback(
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        playWhenReady = playWhenReadyState,
        isResolvingStream = session.isResolvingStream,
        playbackEnded = playbackEnded,
        hasError = hasActivePlaybackError,
        isAppInBackground = isAppInBackground
    )

    // Meccanismo nativo Android / Fire TV Stick per prevenire lo screensaver esclusivamente durante
    // la riproduzione attiva (e buffering temporaneo con playWhenReady = true), rimuovendo sempre
    // il flag su pausa, errore, fine riproduzione, background e uscita dal player.
    val hostWindow = remember(context) { context.findActivity()?.window }
    DisposableEffect(hostWindow, keepScreenOnActive) {
        if (keepScreenOnActive) {
            hostWindow?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            hostWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            hostWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Sopprime gli indicatori grafici di loading durante la ricerca manuale sulla barra temporale
    // senza alterare la gestione tecnica di isBuffering, sync o errori reali del player.
    val suppressSeekLoadingUi = isRemoteScrubbing || isManualSeekInProgress
    val showLoadingOverlay = currentError == null && (isBuffering || session.isResolvingStream) && !suppressSeekLoadingUi

    val showSkipIntro by remember(
        session.mediaItem.id,
        session.mediaItem.isSeries,
        session.seasonNumber,
        session.episodeNumber,
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
                    durationMs = durationMsState.longValue,
                    seriesTmdbId = session.mediaItem.id,
                    seasonNumber = session.seasonNumber,
                    episodeNumber = session.episodeNumber
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
        !showComfortPanel &&
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
        !showComfortPanel &&
        !showVixLinkPanel &&
        (playbackEnded || inCreditsWindow)

    val anyBlockingPopupVisible = showAutoNext || showMovieEndedPopup
    val anySubPanelVisible = showAudioSelector || showComfortPanel || showVixLinkPanel

    // Usa OkHttpDataSource.Factory con client VixSrc (cookie jar + interceptor coerente) per
    // playlist HLS, chiave AES-128 e segmenti MPEG-TS mantenendo Referer/Origin dinamici della sorgente
    val okHttpDataSourceFactory = remember(vixSrcResolver, resolvedStream?.referer, resolvedStream?.origin) {
        val referer = resolvedStream?.referer ?: "https://vixsrc.to/"
        val origin = resolvedStream?.origin ?: VixSrcDiagnostics.extractOrigin(referer)
        val headers = mapOf(
            "Referer" to referer,
            "Origin" to origin
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
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
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

    val latestSession by rememberUpdatedState(session)
    val latestEpisodeKey by rememberUpdatedState(episodeKey)
    val latestResolvedStream by rememberUpdatedState(resolvedStream)
    val latestOnProgressUpdate by rememberUpdatedState(onProgressUpdate)
    val latestOnInvalidateAndRetryStream by rememberUpdatedState(onInvalidateAndRetryStream)
    val latestOnPreResolveNextEpisode by rememberUpdatedState(onPreResolveNextEpisode)
    val latestHasVerifiedNextEpisode by rememberUpdatedState(hasVerifiedNextEpisode)
    val latestIsRemoteScrubbing by rememberUpdatedState(isRemoteScrubbing)
    val latestInitialReadyForTest by rememberUpdatedState(initialReadyForTest)
    val latestOnScrobbleStart by rememberUpdatedState(onScrobbleStart)
    val latestOnScrobblePause by rememberUpdatedState(onScrobblePause)
    val latestOnScrobbleStop by rememberUpdatedState(onScrobbleStop)
    val latestOnPlaybackCheckpoint by rememberUpdatedState(onPlaybackCheckpoint)
    val latestOnManualNextEpisode by rememberUpdatedState(onManualNextEpisode)

    // Reset deterministico e completo dello stato al cambio episodio/contenuto:
    // azzera posizione, durata, barra, overlay, tracce e vecchi mediaItem prima del caricamento del nuovo episodio.
    var hasInitializedEpisode by remember { mutableStateOf(false) }
    LaunchedEffect(episodeKey) {
        currentPositionMsState.longValue = session.startPositionMs.coerceAtLeast(0L)
        durationMsState.longValue = initialDurationMsOverride.takeIf { it > 0L }
            ?: session.metadataDurationMs?.takeIf { !session.isResolvingStream && it > 0L }
            ?: 0L
        hasSeekedInitial = false
        hasPreResolvedNext = false
        playbackEnded = false
        introSkipped = false
        introDismissed = false
        autoNextDismissed = false
        movieEndedDismissed = false
        isRemoteScrubbing = false
        isManualSeekInProgress = false
        showAudioSelector = false
        showComfortPanel = false
        showVixLinkPanel = false
        playerErrorMessage = session.errorMessage
        hasAutoRetriedCachedStream = false
        scrobbleStartedForEpisode = false
        scrobblePausedSinceStart = false
        scrobbleStoppedForEpisode = false
        val hasSessionError = session.errorMessage != null
        isPlaying = !hasSessionError
        playWhenReadyState = !hasSessionError
        videoWidthState.intValue = 0
        videoHeightState.intValue = 0
        autoNextRemainingSecState.intValue = advancedSettings.sanitizedCountdownSec

        if (hasInitializedEpisode) {
            controlsVisible = true
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            if (!initialReadyForTest) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                isBuffering = !hasSessionError
            } else {
                isBuffering = false
            }
            tracksVersion++
        }
        hasInitializedEpisode = true
    }

    // Mantiene sincronizzata la posizione iniziale durante la risoluzione e aggiorna subito la durata affidabile
    // (reale da ExoPlayer o fallback metadati TMDB se lo stream non ha ancora notificato la durata)
    LaunchedEffect(session.startPositionMs, session.isResolvingStream) {
        if (session.isResolvingStream) {
            currentPositionMsState.longValue = session.startPositionMs.coerceAtLeast(0L)
        }
    }
    LaunchedEffect(episodeKey, session.metadataDurationMs, session.isResolvingStream, initialDurationMsOverride) {
        if (initialDurationMsOverride > 0L) {
            durationMsState.longValue = initialDurationMsOverride
        } else if (!session.isResolvingStream) {
            val exoDur = if (!initialReadyForTest && exoPlayer.currentMediaItem?.mediaId == episodeKey) {
                readVerifiedExoDurationMs(exoPlayer)
            } else {
                0L
            }
            if (exoDur > 0L) {
                durationMsState.longValue = exoDur
            } else if (durationMsState.longValue <= 0L) {
                val metaDur = session.metadataDurationMs ?: 0L
                if (metaDur > 0L) {
                    durationMsState.longValue = metaDur
                }
            }
        }
    }

    // Mette in pausa il decoder hardware e salva i progressi quando l'app va in background (tasto Home Fire TV)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isAppInBackground = false
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isAppInBackground = true
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    }
                    playWhenReadyState = false
                    isPlaying = false
                    val pos = exoPlayer.currentPosition.coerceAtLeast(currentPositionMsState.longValue)
                    val dur = readVerifiedExoDurationMs(exoPlayer).takeIf { it > 0L }
                        ?: durationMsState.longValue.takeIf { it > 0L }
                        ?: (latestSession.metadataDurationMs ?: 0L)
                    if (pos > 0L) {
                        latestOnProgressUpdate(pos, dur)
                    }
                    if ((pos > 0L || scrobbleStartedForEpisode) && !playbackEnded && !scrobblePausedSinceStart && !scrobbleStoppedForEpisode) {
                        scrobblePausedSinceStart = true
                        latestOnScrobblePause(pos, dur)
                    }
                }
                else -> Unit
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
            playWhenReadyState = false
            isPlaying = false
        } else {
            exoPlayer.play()
            playWhenReadyState = true
            playbackEnded = false
            isPlaying = true
        }
        interactionTick++
    }

    val closeAudioSelector: () -> Unit = {
        showAudioSelector = false
        interactionTick++
        pendingFocusRestore = PlayerFocusRestoreTarget.AUDIO_BUTTON
    }

    val closeComfortPanel: () -> Unit = {
        showComfortPanel = false
        interactionTick++
        pendingFocusRestore = PlayerFocusRestoreTarget.COMFORT_BUTTON
    }

    val closeVixLinkPanel: () -> Unit = {
        showVixLinkPanel = false
        interactionTick++
        pendingFocusRestore = PlayerFocusRestoreTarget.VIX_LINK_BUTTON
    }

    val performSkipIntro: () -> Unit = {
        val segment = session.introSegment
        val targetMs = segment?.introEndMs
        if (
            targetMs != null &&
            targetMs > 0L &&
            isIntroActiveAtPosition(
                segment = segment,
                positionMs = currentPositionMsState.longValue,
                durationMs = durationMsState.longValue,
                seriesTmdbId = session.mediaItem.id,
                seasonNumber = session.seasonNumber,
                episodeNumber = session.episodeNumber
            )
        ) {
            introSkipped = true
            isManualSeekInProgress = true
            if (simulateBufferingOnSeekForTest) isBuffering = true
            exoPlayer.seekTo(targetMs)
            currentPositionMsState.longValue = targetMs
            playbackEnded = false
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
            showComfortPanel -> closeComfortPanel()
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
                val dur = durationMsState.longValue.takeIf { it > 0L } ?: (latestSession.metadataDurationMs ?: 0L)
                if (pos > 0L) {
                    onProgressUpdate(pos, dur)
                }
                if ((pos > 0L || scrobbleStartedForEpisode) && !scrobbleStoppedForEpisode) {
                    scrobbleStoppedForEpisode = true
                    latestOnScrobbleStop(pos, dur, playbackEnded)
                }
                onClose()
            }
        }
    }

    // Configura HlsMediaSource (+ eventuali tracce sottotitoli esterne tramite MergingMediaSource)
    LaunchedEffect(
        exoPlayer,
        episodeKey,
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
            .setMediaId(episodeKey)
            .setUri(targetUri)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val hlsMediaSource = HlsMediaSource.Factory(okHttpDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(4))
            .createMediaSource(mediaItem)

        val externalSubtitleSources = stream.subtitles
            .filter { it.url.isNotBlank() }
            .mapIndexed { idx, sub ->
                val subConfig = ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(inferSubtitleMimeType(sub.url))
                    .setLanguage(sub.language.ifBlank { null })
                    .setLabel(sub.label.ifBlank { null })
                    .setId("vix_sub_$idx")
                    .build()
                SingleSampleMediaSource.Factory(okHttpDataSourceFactory)
                    .createMediaSource(subConfig, C.TIME_UNSET)
            }

        val mediaSource = if (externalSubtitleSources.isNotEmpty()) {
            MergingMediaSource(hlsMediaSource, *externalSubtitleSources.toTypedArray())
        } else {
            hlsMediaSource
        }

        val resumePosMs = maxOf(session.startPositionMs, currentPositionMsState.longValue)
        if (resumePosMs > 0L) {
            exoPlayer.setMediaSource(mediaSource, resumePosMs)
        } else {
            exoPlayer.setMediaSource(mediaSource)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        playWhenReadyState = true
    }

    // Listener eventi Player + salvataggio su Room + sincronizzazione affidabile durata/tracce audio e sottotitoli reali
    DisposableEffect(exoPlayer) {
        fun syncVerifiedDurationFromPlayer() {
            if (exoPlayer.currentMediaItem?.mediaId != latestEpisodeKey) return
            val realDuration = readVerifiedExoDurationMs(exoPlayer)
            if (realDuration > 0L) {
                durationMsState.longValue = realDuration
            } else if (durationMsState.longValue <= 0L && !latestSession.isResolvingStream) {
                val fallbackMeta = latestSession.metadataDurationMs ?: 0L
                if (fallbackMeta > 0L) {
                    durationMsState.longValue = fallbackMeta
                }
            }
        }

        fun syncVideoResolutionFromPlayer() {
            if (exoPlayer.currentMediaItem?.mediaId != latestEpisodeKey) return
            val (w, h) = extractActiveVideoDimensions(exoPlayer)
            if (w > 0 && h > 0 && (videoWidthState.intValue != w || videoHeightState.intValue != h)) {
                videoWidthState.intValue = w
                videoHeightState.intValue = h
            }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val currentMediaId = exoPlayer.currentMediaItem?.mediaId
                if (currentMediaId != latestEpisodeKey) {
                    return
                }
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerErrorMessage = null
                    playbackEnded = false
                    if (!latestIsRemoteScrubbing) {
                        isManualSeekInProgress = false
                    }
                    syncVerifiedDurationFromPlayer()
                    syncVideoResolutionFromPlayer()
                    val currentExoPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    if (!latestIsRemoteScrubbing && (currentExoPos > 0L || currentPositionMsState.longValue == 0L)) {
                        currentPositionMsState.longValue = currentExoPos
                    }
                    if (!hasSeekedInitial) {
                        hasSeekedInitial = true
                        val targetStart = maxOf(latestSession.startPositionMs, currentPositionMsState.longValue)
                        val dur = durationMsState.longValue
                        if (targetStart > 0L && dur > 0L) {
                            val maxSafePos = (dur - 5_000L).coerceAtLeast(0L)
                            if (exoPlayer.currentPosition > maxSafePos) {
                                exoPlayer.seekTo(maxSafePos)
                                currentPositionMsState.longValue = maxSafePos
                            }
                        }
                    }
                    if (!scrobbleStartedForEpisode && exoPlayer.playWhenReady) {
                        scrobbleStartedForEpisode = true
                        scrobblePausedSinceStart = false
                        scrobbleStoppedForEpisode = false
                        val effectiveDur = readVerifiedExoDurationMs(exoPlayer).takeIf { it > 0L }
                            ?: durationMsState.longValue.takeIf { it > 0L }
                            ?: (latestSession.metadataDurationMs ?: 0L)
                        latestOnScrobbleStart(currentPositionMsState.longValue, effectiveDur)
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isBuffering = false
                    isPlaying = false
                    isManualSeekInProgress = false
                    playbackEnded = true
                    if (!scrobbleStoppedForEpisode) {
                        scrobbleStoppedForEpisode = true
                        val dur = readVerifiedExoDurationMs(exoPlayer).takeIf { it > 0L }
                            ?: durationMsState.longValue.takeIf { it > 0L }
                            ?: (latestSession.metadataDurationMs ?: 0L)
                        val endPos = maxOf(currentPositionMsState.longValue, dur)
                        latestOnScrobbleStop(endPos, dur, true)
                    }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncVerifiedDurationFromPlayer()
            }

            override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                if (mediaItem?.mediaId == latestEpisodeKey) {
                    syncVerifiedDurationFromPlayer()
                    syncVideoResolutionFromPlayer()
                    tracksVersion++
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (exoPlayer.currentMediaItem?.mediaId == latestEpisodeKey) {
                    syncVerifiedDurationFromPlayer()
                    syncVideoResolutionFromPlayer()
                    tracksVersion++
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (exoPlayer.currentMediaItem?.mediaId == latestEpisodeKey) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoWidthState.intValue = videoSize.width
                        videoHeightState.intValue = videoSize.height
                    } else {
                        syncVideoResolutionFromPlayer()
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (exoPlayer.currentMediaItem?.mediaId != latestEpisodeKey) return
                isPlaying = playing
                val pos = exoPlayer.currentPosition.coerceAtLeast(currentPositionMsState.longValue)
                val dur = readVerifiedExoDurationMs(exoPlayer).takeIf { it > 0L }
                    ?: durationMsState.longValue.takeIf { it > 0L }
                    ?: (latestSession.metadataDurationMs ?: 0L)
                if (playing) {
                    if (!scrobbleStartedForEpisode || scrobblePausedSinceStart) {
                        scrobbleStartedForEpisode = true
                        scrobblePausedSinceStart = false
                        scrobbleStoppedForEpisode = false
                        latestOnScrobbleStart(pos, dur)
                    }
                } else {
                    if (pos > 0L) {
                        latestOnProgressUpdate(pos, dur)
                    }
                    if ((pos > 0L || scrobbleStartedForEpisode) && !playbackEnded && exoPlayer.playbackState == Player.STATE_READY && !scrobblePausedSinceStart && !scrobbleStoppedForEpisode) {
                        scrobblePausedSinceStart = true
                        latestOnScrobblePause(pos, dur)
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val currentMediaId = exoPlayer.currentMediaItem?.mediaId
                if (currentMediaId != null && currentMediaId != latestEpisodeKey) return
                playWhenReadyState = playWhenReady
            }

            override fun onPlayerError(error: PlaybackException) {
                val currentMediaId = exoPlayer.currentMediaItem?.mediaId
                if (currentMediaId != null && currentMediaId != latestEpisodeKey) return
                isManualSeekInProgress = false
                if (latestResolvedStream?.fromCache == true && !hasAutoRetriedCachedStream && latestOnInvalidateAndRetryStream != null) {
                    hasAutoRetriedCachedStream = true
                    isBuffering = true
                    playerErrorMessage = null
                    latestOnInvalidateAndRetryStream?.invoke()
                    return
                }
                isBuffering = false
                isPlaying = false
                playWhenReadyState = false
                playerErrorMessage = formatVixSrcPlaybackError(
                    error = error,
                    streamUrl = latestResolvedStream?.streamUrl
                )
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val finalPos = currentPositionMsState.longValue.coerceAtLeast(exoPlayer.currentPosition)
            val finalDur = durationMsState.longValue
                .coerceAtLeast(readVerifiedExoDurationMs(exoPlayer))
                .takeIf { it > 0L }
                ?: (latestSession.metadataDurationMs ?: 0L)
            if (finalPos > 0L) {
                latestOnProgressUpdate(finalPos, finalDur)
            }
            if ((finalPos > 0L || scrobbleStartedForEpisode) && !scrobbleStoppedForEpisode) {
                scrobbleStoppedForEpisode = true
                latestOnScrobbleStop(finalPos, finalDur, playbackEnded)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Loop di aggiornamento posizione/durata ogni 1s + persistenza su Room ogni 10s + pre-risoluzione silenziosa prossimo episodio
    LaunchedEffect(exoPlayer) {
        var tick = 0
        while (true) {
            if (!latestInitialReadyForTest && exoPlayer.currentMediaItem?.mediaId == latestEpisodeKey) {
                val verifiedDur = readVerifiedExoDurationMs(exoPlayer)
                if (verifiedDur > 0L && durationMsState.longValue != verifiedDur) {
                    durationMsState.longValue = verifiedDur
                } else if (durationMsState.longValue <= 0L && !latestSession.isResolvingStream) {
                    val fallbackMeta = latestSession.metadataDurationMs ?: 0L
                    if (fallbackMeta > 0L) {
                        durationMsState.longValue = fallbackMeta
                    }
                }
                val (w, h) = extractActiveVideoDimensions(exoPlayer)
                if (w > 0 && h > 0 && (videoWidthState.intValue != w || videoHeightState.intValue != h)) {
                    videoWidthState.intValue = w
                    videoHeightState.intValue = h
                }
                if (exoPlayer.isPlaying && !latestIsRemoteScrubbing) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = verifiedDur.takeIf { it > 0L } ?: durationMsState.longValue
                    currentPositionMsState.longValue = pos
                    if (dur > 0L) {
                        durationMsState.longValue = dur
                    }
                    if (pos > 0L) {
                        if (tick % 10 == 0) {
                            latestOnProgressUpdate(pos, dur)
                        }
                        if (tick > 0 && tick % 60 == 0) {
                            latestOnPlaybackCheckpoint(pos, dur)
                        }
                        if (!hasPreResolvedNext && latestHasVerifiedNextEpisode && dur > 120_000L && pos >= (dur - 90_000L)) {
                            hasPreResolvedNext = true
                            latestOnPreResolveNextEpisode()
                        }
                        tick++
                    }
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
        rootFocusRequester.safeRequestFocus()
        autoNextPlayFocusRequester.safeRequestFocus()
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
        rootFocusRequester.safeRequestFocus()
        movieEndedFinishFocusRequester.safeRequestFocus()
        delay(60L)
        movieEndedFinishFocusRequester.safeRequestFocus()
    }

    // Auto-focus sul pulsante "SALTA INTRO" quando i controlli sono nascosti per conferma immediata con tasto OK
    LaunchedEffect(showSkipIntro, controlsVisible, anyBlockingPopupVisible, anySubPanelVisible, isRemoteScrubbing) {
        if (showSkipIntro && !controlsVisible && !anyBlockingPopupVisible && !anySubPanelVisible && !isRemoteScrubbing) {
            skipIntroFocusRequester.safeRequestFocus()
            delay(60L)
            skipIntroFocusRequester.safeRequestFocus()
        }
    }

    // Ripristino deterministico del focus dopo la chiusura dei pannelli Audio, Comfort o Link VixSrc
    LaunchedEffect(pendingFocusRestore, showAudioSelector, showComfortPanel, showVixLinkPanel) {
        if (pendingFocusRestore != PlayerFocusRestoreTarget.NONE && !showAudioSelector && !showComfortPanel && !showVixLinkPanel) {
            delay(50L)
            when (pendingFocusRestore) {
                PlayerFocusRestoreTarget.AUDIO_BUTTON -> audioButtonFocusRequester.safeRequestFocus()
                PlayerFocusRestoreTarget.COMFORT_BUTTON -> comfortButtonFocusRequester.safeRequestFocus()
                PlayerFocusRestoreTarget.VIX_LINK_BUTTON -> vixLinkButtonFocusRequester.safeRequestFocus()
                PlayerFocusRestoreTarget.NONE -> Unit
            }
            pendingFocusRestore = PlayerFocusRestoreTarget.NONE
        }
    }

    // Nasconde automaticamente il banner di conferma cambio formato immagine dopo 2 secondi
    LaunchedEffect(aspectToastNonce) {
        if (aspectToastNonce > 0) {
            delay(2_000L)
            aspectToastMode = null
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

    val availableSubtitleTracks = remember(exoPlayer, tracksVersion, subtitleTracksOverrideForTest, selectedOverrideSubtitleId) {
        if (subtitleTracksOverrideForTest != null) {
            subtitleTracksOverrideForTest.map { option ->
                if (selectedOverrideSubtitleId != null) {
                    option.copy(isSelected = option.id == selectedOverrideSubtitleId)
                } else {
                    option
                }
            }
        } else {
            extractPlayerSubtitleTrackOptions(exoPlayer = exoPlayer)
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
                                    showComfortPanel -> closeComfortPanel()
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
                                        val dur = durationMsState.longValue.takeIf { it > 0L } ?: (latestSession.metadataDurationMs ?: 0L)
                                        if (pos > 0L) {
                                            onProgressUpdate(pos, dur)
                                        }
                                        if ((pos > 0L || scrobbleStartedForEpisode) && !scrobbleStoppedForEpisode) {
                                            scrobbleStoppedForEpisode = true
                                            latestOnScrobbleStop(pos, dur, playbackEnded)
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
                                playWhenReadyState = true
                                playbackEnded = false
                                isPlaying = true
                                return@onPreviewKeyEvent true
                            }
                            Key.MediaPause -> {
                                exoPlayer.pause()
                                playWhenReadyState = false
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
                                    val pos = currentPositionMsState.longValue
                                    val dur = durationMsState.longValue.takeIf { it > 0L } ?: (latestSession.metadataDurationMs ?: 0L)
                                    scrobbleStoppedForEpisode = true
                                    val manualHandler = latestOnManualNextEpisode
                                    if (manualHandler != null) {
                                        manualHandler(pos, dur)
                                    } else {
                                        if (pos > 0L) {
                                            onProgressUpdate(pos, dur)
                                        }
                                        onNextEpisode()
                                    }
                                    return@onPreviewKeyEvent true
                                }
                            }
                            Key.MediaPrevious -> {
                                if (hasVerifiedPreviousEpisode) {
                                    val pos = currentPositionMsState.longValue
                                    val dur = durationMsState.longValue.takeIf { it > 0L } ?: (latestSession.metadataDurationMs ?: 0L)
                                    if (pos > 0L) {
                                        onProgressUpdate(pos, dur)
                                    }
                                    if ((pos > 0L || scrobbleStartedForEpisode) && !scrobbleStoppedForEpisode) {
                                        scrobbleStoppedForEpisode = true
                                        latestOnScrobbleStop(pos, dur, false)
                                    }
                                    onPreviousEpisode()
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
                                    if (showSkipIntro) {
                                        performSkipIntro()
                                        return@onPreviewKeyEvent true
                                    } else {
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
                        keepScreenOn = keepScreenOnActive
                        setKeepContentOnPlayerReset(true)
                        isFocusable = false
                        isFocusableInTouchMode = false
                        applyComfortSettingsToPlayerView(this, comfortSettings)
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
                    if (view.keepScreenOn != keepScreenOnActive) {
                        view.keepScreenOn = keepScreenOnActive
                    }
                    applyComfortSettingsToPlayerView(view, comfortSettings)
                },
                onRelease = { view ->
                    view.keepScreenOn = false
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
                                    showComfortPanel -> closeComfortPanel()
                                    showVixLinkPanel -> closeVixLinkPanel()
                                    else -> controlsVisible = !controlsVisible
                                }
                            }
                        )
                    }
            )

            // Stato errore stream o loading (stesso popup di avvio streaming)
            if (currentError != null && !session.isResolvingStream) {
                VixStreamLaunchErrorPopup(
                    session = session,
                    streamLang = streamLang,
                    errorMessage = currentError,
                    retryFocusRequester = retryFocusRequester,
                    onRetry = {
                        playerErrorMessage = null
                        isBuffering = true
                        playWhenReadyState = true
                        retryNonce++
                        if (onInvalidateAndRetryStream != null) {
                            onInvalidateAndRetryStream.invoke()
                        } else {
                            onToggleLanguage(streamLang)
                        }
                    },
                    onClose = {
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
                                showComfortPanel = false
                                showVixLinkPanel = false
                                showAudioSelector = true
                                interactionTick++
                            },
                            comfortFocusRequester = comfortButtonFocusRequester,
                            onOpenComfortPanel = {
                                showAudioSelector = false
                                showVixLinkPanel = false
                                showComfortPanel = true
                                interactionTick++
                            },
                            onOpenVixLinkPanel = {
                                showAudioSelector = false
                                showComfortPanel = false
                                showVixLinkPanel = true
                                interactionTick++
                            },
                            isSeries = session.mediaItem.isSeries,
                            hasPreviousEpisode = hasVerifiedPreviousEpisode,
                            hasNextEpisode = hasVerifiedNextEpisode,
                            previousEpisodeFocusRequester = previousEpisodeFocusRequester,
                            nextEpisodeFocusRequester = nextEpisodeFocusRequester,
                            onPreviousEpisode = {
                                val pos = currentPositionMsState.longValue
                                val dur = durationMsState.longValue.takeIf { it > 0L } ?: (latestSession.metadataDurationMs ?: 0L)
                                if (pos > 0L) {
                                    onProgressUpdate(pos, dur)
                                }
                                if ((pos > 0L || scrobbleStartedForEpisode) && !scrobbleStoppedForEpisode) {
                                    scrobbleStoppedForEpisode = true
                                    latestOnScrobbleStop(pos, dur, false)
                                }
                                onPreviousEpisode()
                            },
                            onNextEpisode = {
                                val pos = currentPositionMsState.longValue
                                val dur = durationMsState.longValue.takeIf { it > 0L } ?: (latestSession.metadataDurationMs ?: 0L)
                                scrobbleStoppedForEpisode = true
                                val manualHandler = latestOnManualNextEpisode
                                if (manualHandler != null) {
                                    manualHandler(pos, dur)
                                } else {
                                    if (pos > 0L) {
                                        onProgressUpdate(pos, dur)
                                    }
                                    onNextEpisode()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            PlayerStreamingResolutionBadge(
                                resolutionLabel = activeResolutionLabel
                            )

                            PlayerCleanTimeDisplay(
                                positionMsProvider = { currentPositionMsState.longValue },
                                durationMsProvider = { durationMsState.longValue }
                            )
                        }
                    }
                }
            }

            // Selettore Tracce Audio & Sottotitoli (aperto dal pulsante circolare AUDIO in basso a sinistra)
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
                    subtitleTracks = availableSubtitleTracks,
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
                    onSelectSubtitle = { selected ->
                        selectedOverrideSubtitleId = selected.id
                        onSubtitleTrackSelectedForTest?.invoke(selected)
                        if (subtitleTracksOverrideForTest == null) {
                            applySubtitleTrackSelection(
                                exoPlayer = exoPlayer,
                                option = selected
                            )
                            tracksVersion++
                        }
                        closeAudioSelector()
                    },
                    onDismiss = closeAudioSelector,
                    isSeries = session.mediaItem.isSeries,
                    currentIntroSegment = session.introSegment,
                    currentPositionMsProvider = { currentPositionMsState.longValue },
                    onSaveIntroSegment = onSaveIntroSegment
                )
            }

            // Pannello Comfort Player (Formato Video / Zoom + Stile Sottotitoli live)
            AnimatedVisibility(
                visible = showComfortPanel && currentError == null && !showLoadingOverlay,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                PlayerComfortPanel(
                    settings = comfortSettings,
                    onSelectAspectMode = { mode ->
                        onSelectVideoAspectMode(mode)
                        aspectToastMode = mode
                        aspectToastNonce++
                        interactionTick++
                    },
                    onSelectSubtitleSize = { size ->
                        onSelectSubtitleTextSize(size)
                        interactionTick++
                    },
                    onSelectSubtitleColor = { color ->
                        onSelectSubtitleTextColor(color)
                        interactionTick++
                    },
                    onSelectSubtitleBackground = { bg ->
                        onSelectSubtitleBackgroundStyle(bg)
                        interactionTick++
                    },
                    onDismiss = closeComfortPanel
                )
            }

            // Feedback discreto in alto a destra al cambio formato immagine
            AnimatedVisibility(
                visible = aspectToastMode != null && currentError == null && !showLoadingOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 24.dp, end = 28.dp)
            ) {
                aspectToastMode?.let { mode ->
                    VideoAspectToastBanner(aspectMode = mode)
                }
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

/**
 * Classifica un [PlaybackException] di Media3 distinguendo con precisione:
 * - errori HTTP dal server VixSrc/CDN (400/401/403/404/410/429/5xx),
 * - errori di rete o timeout,
 * - errori di parsing del manifest HLS/media,
 * - errori interni del decoder/player ExoPlayer.
 * Registra inoltre un evento diagnostico sanitizzato (senza token né cookie).
 */
@OptIn(UnstableApi::class)
internal fun formatVixSrcPlaybackError(
    error: PlaybackException,
    streamUrl: String? = null
): String {
    var cause: Throwable? = error.cause
    var depth = 0
    var httpResponseException: HttpDataSource.InvalidResponseCodeException? = null
    var httpDataSourceException: HttpDataSource.HttpDataSourceException? = null
    var timeoutException: SocketTimeoutException? = null
    var unknownHostException: UnknownHostException? = null
    var parserException: ParserException? = null

    while (cause != null && depth < 8) {
        when (cause) {
            is HttpDataSource.InvalidResponseCodeException -> {
                if (httpResponseException == null) httpResponseException = cause
            }
            is SocketTimeoutException -> {
                if (timeoutException == null) timeoutException = cause
            }
            is UnknownHostException -> {
                if (unknownHostException == null) unknownHostException = cause
            }
            is HttpDataSource.HttpDataSourceException -> {
                if (httpDataSourceException == null) httpDataSourceException = cause
            }
            is ParserException -> {
                if (parserException == null) parserException = cause
            }
        }
        cause = cause.cause
        depth++
    }

    val sanitizedTarget = VixSrcDiagnostics.sanitizeUrl(
        httpResponseException?.dataSpec?.uri?.toString()
            ?: httpDataSourceException?.dataSpec?.uri?.toString()
            ?: streamUrl.orEmpty()
    )

    if (httpResponseException != null) {
        val code = httpResponseException.responseCode
        val message = VixSrcDiagnostics.formatHttpUserMessage(code, VixSrcResolutionPhase.PLAYER_PLAYBACK)
        VixSrcDiagnostics.logEvent(
            VixSrcDiagnosticEvent(
                phase = VixSrcResolutionPhase.PLAYER_PLAYBACK,
                method = "GET",
                sanitizedUrl = sanitizedTarget,
                httpStatusCode = code,
                category = VixSrcDiagnostics.classifyHttpStatus(code),
                errorDetail = error.errorCodeName
            )
        )
        return message
    }

    if (timeoutException != null) {
        VixSrcDiagnostics.logEvent(
            VixSrcDiagnosticEvent(
                phase = VixSrcResolutionPhase.PLAYER_PLAYBACK,
                method = "GET",
                sanitizedUrl = sanitizedTarget,
                category = VixSrcErrorCategory.NETWORK_TIMEOUT,
                errorDetail = error.errorCodeName
            )
        )
        return "Timeout di rete durante la riproduzione dello stream VixSrc (${error.errorCodeName}). Verifica la connessione e riprova."
    }

    if (unknownHostException != null) {
        VixSrcDiagnostics.logEvent(
            VixSrcDiagnosticEvent(
                phase = VixSrcResolutionPhase.PLAYER_PLAYBACK,
                method = "GET",
                sanitizedUrl = sanitizedTarget,
                category = VixSrcErrorCategory.NETWORK_ERROR,
                errorDetail = error.errorCodeName
            )
        )
        return "Impossibile raggiungere il server streaming VixSrc (errore DNS/rete)."
    }

    if (httpDataSourceException != null) {
        VixSrcDiagnostics.logEvent(
            VixSrcDiagnosticEvent(
                phase = VixSrcResolutionPhase.PLAYER_PLAYBACK,
                method = "GET",
                sanitizedUrl = sanitizedTarget,
                category = VixSrcErrorCategory.NETWORK_ERROR,
                errorDetail = error.errorCodeName
            )
        )
        return "Errore di connessione HTTP durante la riproduzione dello stream VixSrc (${error.errorCodeName})."
    }

    if (parserException != null) {
        VixSrcDiagnostics.logEvent(
            VixSrcDiagnosticEvent(
                phase = VixSrcResolutionPhase.PLAYER_PLAYBACK,
                method = "GET",
                sanitizedUrl = sanitizedTarget,
                category = VixSrcErrorCategory.INVALID_PLAYLIST_MANIFEST,
                errorDetail = error.errorCodeName
            )
        )
        return "Manifest HLS o formato multimediale VixSrc non valido (${error.errorCodeName})."
    }

    VixSrcDiagnostics.logEvent(
        VixSrcDiagnosticEvent(
            phase = VixSrcResolutionPhase.PLAYER_PLAYBACK,
            method = "PLAY",
            sanitizedUrl = sanitizedTarget,
            category = VixSrcErrorCategory.PLAYER_PLAYBACK_ERROR,
            errorDetail = error.errorCodeName
        )
    )
    return "Errore player video (${error.errorCodeName}): ${error.localizedMessage ?: "riproduzione interrotta"}"
}
