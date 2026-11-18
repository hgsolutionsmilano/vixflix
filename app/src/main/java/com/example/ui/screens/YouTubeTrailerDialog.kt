package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
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
import androidx.activity.compose.BackHandler
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.data.local.StreamingBufferPreset
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbTrailerResolver
import com.example.data.remote.YouTubeTrailerCandidate
import com.example.ui.TrailerPlaybackState
import com.example.ui.components.animatedPosterShowcase
import com.example.ui.components.rememberIsTvDevice
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
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
import kotlinx.coroutines.delay

/**
 * Modale cinematografico centrato 16:9 dedicato esclusivamente ai trailer ufficiali YouTube
 * incorporati direttamente tramite il player ufficiale YouTube IFrame Embed.
 * - Mostra prima i trailer in Italiano (ITA) poi in Inglese / originale (ENG) in una lista selezionabile.
 * - Gestisce errori espliciti con Riprova, Cambio trailer e Chiudi.
 * - Non contiene alcuna azione per avviare lo streaming completo del film o serie TV.
 */
@Composable
fun YouTubeTrailerDialog(
    mediaItem: MediaItem,
    trailerState: TrailerPlaybackState,
    @Suppress("UNUSED_PARAMETER") bufferPreset: StreamingBufferPreset = StreamingBufferPreset.DEFAULT,
    onSelectTrailer: (YouTubeTrailerCandidate) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val resolved = trailerState.resolvedTrailer
    val selectedVideo = trailerState.selectedVideo
    val activeVideoId = resolved?.embedVideoId ?: selectedVideo?.videoId
    val activeKey = activeVideoId ?: "searching"

    var isEmbedLoading by remember(activeKey) {
        mutableStateOf(!trailerState.isSearching && !trailerState.isLoadingStream && !activeVideoId.isNullOrBlank())
    }
    var isPlaying by remember(activeKey) { mutableStateOf(false) }
    var currentPosMs by remember(activeKey) { mutableLongStateOf(0L) }
    var durationMs by remember(activeKey) { mutableLongStateOf(0L) }
    var playbackError by remember(activeKey, trailerState.errorMessage) {
        mutableStateOf(trailerState.errorMessage)
    }
    var embedRetryNonce by remember { mutableIntStateOf(0) }
    var rendererRestartNonce by remember { mutableIntStateOf(0) }
    val isGpuLessEmulator = remember { TmdbTrailerResolver.isSoftwareOnlyEmulator() }
    val shouldMountWebView = !isGpuLessEmulator
    val allowAutoplay = remember(activeKey, rendererRestartNonce) {
        !TmdbTrailerResolver.isEmulator() && !TmdbTrailerResolver.isWebViewRendererUnstable
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    val isTvDevice = rememberIsTvDevice()
    val modalPlayerFocusRequester = remember { FocusRequester() }
    val firstPlaylistFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }
    val playlistState = rememberLazyListState()

    val uniqueTrailers = remember(trailerState.trailers) {
        trailerState.trailers.distinctBy { it.videoId }
    }

    val togglePlayPause: () -> Unit = {
        val js = if (isPlaying) {
            "window.vixPause && window.vixPause();"
        } else {
            "window.vixPlay && window.vixPlay();"
        }
        runCatching { webViewRef?.evaluateJavascript(js, null) }
    }

    val playAction: () -> Unit = {
        runCatching { webViewRef?.evaluateJavascript("window.vixPlay && window.vixPlay();", null) }
    }

    val pauseAction: () -> Unit = {
        runCatching { webViewRef?.evaluateJavascript("window.vixPause && window.vixPause();", null) }
    }

    val seekToAction: (Long) -> Unit = { targetMs ->
        val clamped = targetMs.coerceAtLeast(0L).let { t ->
            if (durationMs > 0L) t.coerceAtMost(durationMs) else t
        }
        currentPosMs = clamped
        val sec = clamped / 1000.0
        runCatching { webViewRef?.evaluateJavascript("window.vixSeekTo && window.vixSeekTo($sec);", null) }
    }

    // Sincronizza stato caricamento ed errori con lo stato corrente del trailer selezionato
    LaunchedEffect(
        activeVideoId,
        embedRetryNonce,
        shouldMountWebView,
        trailerState.isSearching,
        trailerState.isLoadingStream,
        trailerState.errorMessage
    ) {
        if (trailerState.isSearching || trailerState.isLoadingStream) {
            isEmbedLoading = true
            isPlaying = false
            playbackError = null
            return@LaunchedEffect
        }

        if (!trailerState.errorMessage.isNullOrBlank()) {
            isEmbedLoading = false
            isPlaying = false
            playbackError = trailerState.errorMessage
            return@LaunchedEffect
        }

        if (activeVideoId.isNullOrBlank()) {
            isEmbedLoading = false
            isPlaying = false
            playbackError = "Trailer non disponibile o non riproducibile su YouTube."
            return@LaunchedEffect
        }

        playbackError = null
        isEmbedLoading = shouldMountWebView
        isPlaying = false
        currentPosMs = 0L
        durationMs = (selectedVideo?.durationSeconds ?: 0).toLong() * 1000L
    }

    // Watchdog anti-blocco: se l'embed ufficiale YouTube non risponde entro 15 secondi mostra errore esplicito
    LaunchedEffect(
        activeVideoId,
        embedRetryNonce,
        shouldMountWebView,
        isEmbedLoading,
        trailerState.isSearching,
        trailerState.isLoadingStream,
        playbackError
    ) {
        if (shouldMountWebView &&
            !trailerState.isSearching &&
            !trailerState.isLoadingStream &&
            isEmbedLoading &&
            playbackError == null &&
            !activeVideoId.isNullOrBlank()
        ) {
            delay(15_000L)
            if (isEmbedLoading && playbackError == null) {
                isEmbedLoading = false
                isPlaying = false
                playbackError = "Timeout di caricamento del player ufficiale YouTube. Prova un altro trailer dalla lista o chiudi."
            }
        }
    }

    // Focus automatico Android TV sui pulsanti principali del modale (senza intrappolare il fuoco nella WebView)
    LaunchedEffect(activeKey, playbackError, trailerState.isSearching, trailerState.isLoadingStream) {
        if (!isTvDevice) return@LaunchedEffect
        if (!playbackError.isNullOrBlank()) {
            retryFocusRequester.safeRequestFocus()
        } else {
            closeFocusRequester.safeRequestFocus()
        }
    }

    BackHandler(enabled = true) {
        onClose()
    }

    // Scrim scuro cinematografico in-window (evita una seconda Window Dialog sopra la WebView)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(NetflixBlack.copy(alpha = 0.90f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
            .safeFocusRequester(modalPlayerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Escape, Key.Back -> {
                            onClose()
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaPlayPause -> {
                            togglePlayPause()
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaPlay -> {
                            playAction()
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaPause -> {
                            pauseAction()
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaFastForward -> {
                            seekToAction(currentPosMs + 10_000L)
                            return@onPreviewKeyEvent true
                        }
                        Key.MediaRewind -> {
                            seekToAction(currentPosMs - 10_000L)
                            return@onPreviewKeyEvent true
                        }
                        else -> Unit
                    }
                }
                false
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("in_app_trailer_overlay"),
        contentAlignment = Alignment.Center
    ) {
        val modalMaxWidth = (maxWidth * 0.96f).coerceAtMost(880.dp)
        val maxStageHeight = (maxHeight - 230.dp).coerceAtLeast(150.dp)
        val stageWidth = modalMaxWidth.coerceAtMost(maxStageHeight * (16f / 9f))
        val hasActiveEmbedStage = shouldMountWebView &&
            playbackError.isNullOrBlank() &&
            !trailerState.isSearching &&
            !trailerState.isLoadingStream &&
            !activeVideoId.isNullOrBlank()

        // Contenitore modale piatto (senza Card shadowElevation o verticalScroll attorno alla WebView)
        Box(
            modifier = Modifier
                .width(modalMaxWidth)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NetflixCardSurface, RoundedCornerShape(14.dp))
                    .border(1.dp, NetflixElevatedSurface, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Blocca la propagazione click verso lo scrim esterno
                    )
                    .testTag("youtube_trailer_modal_card")
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

                        // 1. HEADER MODALE: Badge YouTube + Lingua + Titolo + Chiudi
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

                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(44.dp)
                                    .safeFocusRequester(closeFocusRequester)
                                    .focusProperties {
                                        if (!playbackError.isNullOrBlank()) {
                                            retryFocusRequester.takeIfAttached()?.let { down = it }
                                        } else if (uniqueTrailers.isNotEmpty()) {
                                            firstPlaylistFocusRequester.takeIfAttached()?.let { down = it }
                                        }
                                    }
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

                        // 2. PALCOSCENICO VIDEO CENTRATO 16:9 CON PLAYER UFFICIALE YOUTUBE EMBED
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(stageWidth)
                                .aspectRatio(16f / 9f)
                                .then(if (!hasActiveEmbedStage) Modifier.background(Color.Black) else Modifier)
                                .testTag("fullscreen_trailer_player_view"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playbackError.isNullOrBlank() &&
                                !trailerState.isSearching &&
                                !trailerState.isLoadingStream &&
                                !activeVideoId.isNullOrBlank()
                            ) {
                                if (shouldMountWebView) {
                                    YouTubeWebEmbedPlayer(
                                        videoId = activeVideoId,
                                        reloadNonce = embedRetryNonce,
                                        rendererRestartNonce = rendererRestartNonce,
                                        autoplay = allowAutoplay,
                                        isTvDevice = isTvDevice,
                                        onWebViewReady = { webViewRef = it },
                                        onReady = { durMs ->
                                            isEmbedLoading = false
                                            if (durMs > 0L) durationMs = durMs
                                            playbackError = null
                                        },
                                        onPlaybackStateChanged = { playing, buffering ->
                                            isPlaying = playing
                                            if (!buffering) {
                                                isEmbedLoading = false
                                            }
                                            if (playing) {
                                                playbackError = null
                                            }
                                        },
                                        onProgress = { posMs, durMs ->
                                            currentPosMs = posMs
                                            if (durMs > 0L) durationMs = durMs
                                        },
                                        onRendererCrashed = {
                                            TmdbTrailerResolver.isWebViewRendererUnstable = true
                                            isEmbedLoading = false
                                            isPlaying = false
                                            playbackError =
                                                "Il player WebView YouTube è stato interrotto dal sistema. Premi Riprova, Apri su YouTube o scegli un altro trailer."
                                        },
                                        onError = { msg ->
                                            isEmbedLoading = false
                                            isPlaying = false
                                            playbackError = msg
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("youtube_official_embed_view")
                                    )
                                } else {
                                    YouTubeEmulatorSafePreviewCard(
                                        videoId = activeVideoId,
                                        title = selectedVideo?.title ?: mediaItem.title,
                                        thumbnailUrl = selectedVideo?.thumbnailUrl
                                            ?: "https://i.ytimg.com/vi/$activeVideoId/hqdefault.jpg",
                                        onOpenExternalYouTube = {
                                            runCatching {
                                                val ytIntent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://www.youtube.com/watch?v=$activeVideoId")
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(ytIntent)
                                            }
                                        },
                                        onClose = onClose,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
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
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.focusGroup()
                                        ) {
                                            Button(
                                                onClick = {
                                                    playbackError = null
                                                    isEmbedLoading = true
                                                    embedRetryNonce++
                                                    rendererRestartNonce++
                                                    onRetry()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NetflixRed,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .safeFocusRequester(retryFocusRequester)
                                                    .focusProperties {
                                                        closeFocusRequester.takeIfAttached()?.let { up = it }
                                                        if (uniqueTrailers.isNotEmpty()) {
                                                            firstPlaylistFocusRequester.takeIfAttached()?.let { down = it }
                                                        }
                                                    }
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

                                            if (!activeVideoId.isNullOrBlank()) {
                                                OutlinedButton(
                                                    onClick = {
                                                        runCatching {
                                                            val ytIntent = Intent(
                                                                Intent.ACTION_VIEW,
                                                                Uri.parse("https://www.youtube.com/watch?v=$activeVideoId")
                                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            context.startActivity(ytIntent)
                                                        }
                                                    },
                                                    border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.7f)),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = NetflixDarkSurface,
                                                        contentColor = NetflixWhite
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier
                                                        .focusProperties {
                                                            closeFocusRequester.takeIfAttached()?.let { up = it }
                                                            if (uniqueTrailers.isNotEmpty()) {
                                                                firstPlaylistFocusRequester.takeIfAttached()?.let { down = it }
                                                            }
                                                        }
                                                        .tvFocusableControl(
                                                            shape = RoundedCornerShape(8.dp),
                                                            focusedScale = 1.06f
                                                        )
                                                        .testTag("open_external_youtube_button")
                                                ) {
                                                    Text("Apri su YouTube")
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = onClose,
                                                border = BorderStroke(1.dp, NetflixElevatedSurface),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = NetflixDarkSurface,
                                                    contentColor = NetflixWhite
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .focusProperties {
                                                        closeFocusRequester.takeIfAttached()?.let { up = it }
                                                        if (uniqueTrailers.isNotEmpty()) {
                                                            firstPlaylistFocusRequester.takeIfAttached()?.let { down = it }
                                                        }
                                                    }
                                                    .tvFocusableControl(
                                                        shape = RoundedCornerShape(8.dp),
                                                        focusedScale = 1.06f
                                                    )
                                            ) {
                                                Text("Chiudi")
                                            }
                                        }
                                    }
                                }
                            } else if (trailerState.isSearching || trailerState.isLoadingStream || isEmbedLoading) {
                                // Stato caricamento iniziale del player ufficiale YouTube
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(NetflixBlack.copy(alpha = 0.72f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bgThumb = selectedVideo?.thumbnailUrl ?: mediaItem.backdropUrl ?: mediaItem.posterUrl
                                    if (!bgThumb.isNullOrBlank()) {
                                        val density = LocalDensity.current
                                        val wPx = with(density) { 480.dp.roundToPx() }
                                        val hPx = with(density) { 270.dp.roundToPx() }
                                        val thumbModel = remember(context, bgThumb, wPx, hPx) {
                                            ImageRequest.Builder(context)
                                                .data(bgThumb)
                                                .size(wPx, hPx)
                                                .precision(Precision.INEXACT)
                                                .scale(Scale.FILL)
                                                .allowHardware(true)
                                                .allowRgb565(true)
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .crossfade(false)
                                                .build()
                                        }
                                        AsyncImage(
                                            model = thumbModel,
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
                                                else -> "Caricamento player ufficiale YouTube…"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NetflixWhite,
                                            textAlign = TextAlign.Center
                                        )
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
                                        "Trailer trovati su YouTube (${uniqueTrailers.size})"
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

                            if (uniqueTrailers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(
                                    state = playlistState,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .safeFocusRequester(firstPlaylistFocusRequester)
                                        .focusGroup()
                                        .testTag("youtube_trailer_playlist_row")
                                ) {
                                    itemsIndexed(
                                        items = uniqueTrailers,
                                        key = { _, it -> "yt_card_${it.videoId}" },
                                        contentType = { _, _ -> "youtube_trailer_card" }
                                    ) { index, candidate ->
                                        YouTubeModalTrailerCard(
                                            candidate = candidate,
                                            isSelected = candidate.videoId == selectedVideo?.videoId,
                                            itemIndex = index,
                                            listState = playlistState,
                                            onClick = { onSelectTrailer(candidate) },
                                            modifier = Modifier
                                                .focusProperties {
                                                    val upTarget = if (!playbackError.isNullOrBlank()) {
                                                        retryFocusRequester
                                                    } else {
                                                        closeFocusRequester
                                                    }
                                                    upTarget.takeIfAttached()?.let { up = it }
                                                }
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
    val density = LocalDensity.current
    val thumbWidthPx = with(density) { 206.dp.roundToPx() }
    val thumbHeightPx = with(density) { 116.dp.roundToPx() }
    val thumbRequest = remember(context, candidate.thumbnailUrl, thumbWidthPx, thumbHeightPx) {
        ImageRequest.Builder(context)
            .data(candidate.thumbnailUrl)
            .size(thumbWidthPx, thumbHeightPx)
            .precision(Precision.INEXACT)
            .scale(Scale.FILL)
            .allowHardware(true)
            .allowRgb565(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }

    val cardShape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .width(206.dp)
            .background(
                color = if (isSelected) NetflixElevatedSurface else NetflixCardSurface,
                shape = cardShape
            )
            .border(
                width = if (isSelected || isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> NetflixWhite
                    isSelected -> NetflixRed
                    else -> NetflixElevatedSurface
                },
                shape = cardShape
            )
            .clip(cardShape)
            .animatedPosterShowcase(
                itemIndex = itemIndex,
                listState = listState,
                isFocused = isFocused,
                shape = cardShape,
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

@Composable
private fun YouTubeEmulatorSafePreviewCard(
    videoId: String,
    title: String,
    thumbnailUrl: String,
    onOpenExternalYouTube: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(NetflixBlack)
            .testTag("youtube_safe_preview_card"),
        contentAlignment = Alignment.Center
    ) {
        val safeThumb = thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
        val context = LocalContext.current
        val density = LocalDensity.current
        val wPx = with(density) { 480.dp.roundToPx() }
        val hPx = with(density) { 270.dp.roundToPx() }
        val previewModel = remember(context, safeThumb, wPx, hPx) {
            ImageRequest.Builder(context)
                .data(safeThumb)
                .size(wPx, hPx)
                .precision(Precision.INEXACT)
                .scale(Scale.FILL)
                .allowHardware(true)
                .allowRgb565(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = previewModel,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            alpha = 0.42f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NetflixBlack.copy(alpha = 0.35f),
                            NetflixBlack.copy(alpha = 0.88f)
                        )
                    )
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NetflixRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Riproduci su YouTube",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = NetflixWhite,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Player WebView disattivato su ambiente privo di GPU hardware.",
                style = MaterialTheme.typography.labelSmall,
                color = NetflixLightGray,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenExternalYouTube,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .tvFocusableControl(shape = RoundedCornerShape(8.dp), focusedScale = 1.06f)
                        .testTag("open_external_youtube_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartDisplay,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Apri YouTube", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onClose,
                    border = BorderStroke(1.dp, NetflixLightGray.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NetflixDarkSurface.copy(alpha = 0.85f),
                        contentColor = NetflixWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .tvFocusableControl(shape = RoundedCornerShape(8.dp), focusedScale = 1.06f)
                        .testTag("close_safe_preview_button")
                ) {
                    Text("Chiudi", maxLines = 1)
                }
            }
        }
    }
}

/**
 * Riproduce il trailer YouTube incorporando direttamente il codice iframe ufficiale YouTube
 * insieme alle YouTube IFrame Player API all'interno della WebView, con origine HTTPS conforme
 * per evitare errori 153 e supportare comandi diretti touch e media Android TV.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebEmbedPlayer(
    videoId: String,
    reloadNonce: Int,
    rendererRestartNonce: Int,
    autoplay: Boolean,
    isTvDevice: Boolean,
    onWebViewReady: (WebView?) -> Unit,
    onReady: (Long) -> Unit,
    onPlaybackStateChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
    onProgress: (posMs: Long, durMs: Long) -> Unit,
    onRendererCrashed: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val safeVideoId = remember(videoId) { videoId.replace(Regex("[^A-Za-z0-9_-]"), "") }
    val embedOrigin = remember(context) {
        val pkg = context.packageName.ifBlank { "com.example.vixflix" }
        "https://$pkg"
    }
    val loadKey = "$safeVideoId#$reloadNonce#$rendererRestartNonce#$autoplay"
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var activeVideoId by remember { mutableStateOf(safeVideoId) }
    activeVideoId = safeVideoId

    var hasPlayerReady by remember { mutableStateOf(false) }

    val webView = remember(context, rendererRestartNonce) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            keepScreenOn = true
            setLayerType(View.LAYER_TYPE_NONE, null)
            isFocusable = !isTvDevice
            isFocusableInTouchMode = !isTvDevice
            descendantFocusability = if (isTvDevice) {
                ViewGroup.FOCUS_BLOCK_DESCENDANTS
            } else {
                ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
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
                mediaPlaybackRequiresUserGesture = !autoplay
                loadWithOverviewMode = false
                useWideViewPort = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }
        }
    }

    val cleanupDone = remember(webView) { AtomicBoolean(false) }

    fun destroyWebViewOnce(target: WebView?) {
        if (target == null) return
        if (!cleanupDone.compareAndSet(false, true)) return
        runCatching {
            target.onPause()
            target.stopLoading()
            target.loadUrl("about:blank")
            target.removeJavascriptInterface("AndroidBridge")
            target.removeAllViews()
            target.destroy()
        }
    }

    LaunchedEffect(webView, isTvDevice) {
        if (!cleanupDone.get()) {
            webView.isFocusable = !isTvDevice
            webView.isFocusableInTouchMode = !isTvDevice
            webView.descendantFocusability = if (isTvDevice) {
                ViewGroup.FOCUS_BLOCK_DESCENDANTS
            } else {
                ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
        }
    }

    // Se l'iframe carica ma onReady JS ritarda, rivela comunque il player ufficiale YouTube
    LaunchedEffect(loadKey) {
        hasPlayerReady = false
        delay(2_800L)
        if (!hasPlayerReady && !cleanupDone.get()) {
            onReady(0L)
        }
    }

    DisposableEffect(webView) {
        val bridge = YouTubeEmbedBridge(
            currentVideoId = { activeVideoId },
            mainHandler = mainHandler,
            onReadyCallback = { durMs ->
                if (!cleanupDone.get()) {
                    hasPlayerReady = true
                    onReady(durMs)
                }
            },
            onStateChangeCallback = { state, curMs, durMs ->
                if (cleanupDone.get()) return@YouTubeEmbedBridge
                hasPlayerReady = true
                if (durMs > 0L) onProgress(curMs, durMs)
                when (state) {
                    1 -> { // PLAYING
                        onPlaybackStateChanged(true, false)
                    }
                    2, 0, 5 -> { // PAUSED / ENDED / CUED
                        onPlaybackStateChanged(false, false)
                    }
                    3 -> { // BUFFERING
                        onPlaybackStateChanged(false, true)
                    }
                }
            },
            onTimeUpdateCallback = { curMs, durMs ->
                if (!cleanupDone.get()) onProgress(curMs, durMs)
            },
            onErrorCallback = { code ->
                if (cleanupDone.get()) return@YouTubeEmbedBridge
                hasPlayerReady = true
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
                return request?.isForMainFrame == true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && !cleanupDone.get()) {
                    mainHandler.post {
                        onError("Errore di rete nel caricamento del player ufficiale YouTube.")
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
                if ((request?.isForMainFrame == true || urlStr.contains("/embed/")) && code >= 400 && !cleanupDone.get()) {
                    mainHandler.post {
                        onError("Trailer YouTube non disponibile o bloccato (HTTP $code).")
                    }
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                val target = view ?: webView
                if (cleanupDone.compareAndSet(false, true)) {
                    mainHandler.post {
                        onWebViewReady(null)
                        onRendererCrashed()
                    }
                    runCatching {
                        target.onPause()
                        target.stopLoading()
                        (target.parent as? ViewGroup)?.removeView(target)
                        target.removeAllViews()
                        target.destroy()
                    }
                }
                return true
            }
        }

        webView.onResume()
        onWebViewReady(webView)
        onDispose {
            onWebViewReady(null)
            destroyWebViewOnce(webView)
        }
    }

    Box(modifier = modifier) {
        key(webView) {
            AndroidView(
                factory = { webView },
                update = { view ->
                    if (!cleanupDone.get() && view.tag != loadKey && safeVideoId.isNotBlank()) {
                        view.tag = loadKey
                        view.loadDataWithBaseURL(
                            embedOrigin,
                            buildYouTubeEmbedHtml(
                                videoId = safeVideoId,
                                originUrl = embedOrigin,
                                autoplay = autoplay
                            ),
                            "text/html",
                            "UTF-8",
                            embedOrigin
                        )
                    }
                },
                onRelease = { view ->
                    destroyWebViewOnce(view)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
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

internal fun buildYouTubeEmbedHtml(
    videoId: String,
    originUrl: String = "https://com.example.vixflix",
    autoplay: Boolean = true
): String {
    val safeVideoId = videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
    val safeOrigin = originUrl.trimEnd('/')
    val autoplayFlag = if (autoplay) 1 else 0
    val embedSrc =
        "https://www.youtube.com/embed/$safeVideoId?enablejsapi=1&autoplay=$autoplayFlag&playsinline=1&controls=1&rel=0&iv_load_policy=3&fs=0&hl=it&origin=$safeOrigin"
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
              background-color: transparent;
              overflow: hidden;
            }
            #youtube-player {
              display: block;
              width: 100%;
              height: 100%;
              border: 0;
            }
          </style>
        </head>
        <body>
          <iframe
            id="youtube-player"
            width="100%"
            height="100%"
            src="$embedSrc"
            title="YouTube video player"
            frameborder="0"
            allow="accelerometer; autoplay; clipboard-write; gyroscope; web-share"
            referrerpolicy="strict-origin-when-cross-origin"
            allowfullscreen
            onload="window.vixNotifyIframeLoaded && window.vixNotifyIframeLoaded();">
          </iframe>
          <script>
            var VIDEO_ID = '$safeVideoId';
            var player = null;

            window.vixNotifyIframeLoaded = function() {
              try { AndroidBridge.onReady(VIDEO_ID, 0); } catch (e) {}
            };

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
              try {
                player = new YT.Player('youtube-player', {
                  events: {
                    onReady: function(event) {
                      var dur = 0;
                      try { dur = event.target.getDuration() || 0; } catch (e) {}
                      AndroidBridge.onReady(VIDEO_ID, dur);
                    },
                    onStateChange: function(event) {
                      var cur = 0, dur = 0;
                      try {
                        cur = event.target.getCurrentTime() || 0;
                        dur = event.target.getDuration() || 0;
                      } catch (e) {}
                      AndroidBridge.onStateChange(VIDEO_ID, event.data, cur, dur);
                    },
                    onError: function(event) {
                      AndroidBridge.onError(VIDEO_ID, event.data);
                    }
                  }
                });
              } catch (e) {}
            }
          </script>
          <script src="https://www.youtube.com/iframe_api"></script>
        </body>
        </html>
    """.trimIndent()
}
