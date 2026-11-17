package com.example.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixGold
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rileva se l'app è eseguita su Android TV / Google TV (o dispositivo con interfaccia Leanback).
 */
@Composable
fun rememberIsTvDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) { isAndroidTvDevice(context) }
}

@Suppress("DEPRECATION")
fun isAndroidTvDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val pm = context.packageManager
    val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasTelevision = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    val isAmazonFireTv = Build.MANUFACTURER.equals("Amazon", ignoreCase = true) &&
        Build.MODEL.startsWith("AFT", ignoreCase = true)
    return isTvMode || hasLeanback || hasTelevision || isAmazonFireTv
}

/**
 * Modificatore universale per pulsanti, chip e voci di menu compatibile con Telecomando Android TV / Google TV
 * (D-pad + Invio/OK + visualizzazione chiara dello stato di focus).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvFocusableControl(
    shape: Shape = RoundedCornerShape(8.dp),
    focusedScale: Float = 1.05f,
    focusedBorderColor: Color = NetflixWhite,
    unfocusedBorderColor: Color = Color.Transparent,
    borderWidth: Dp = 2.dp,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var centerKeyHeld by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed || centerKeyHeld -> 0.94f
            isFocused -> focusedScale
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tvControlScale"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) focusedBorderColor else unfocusedBorderColor,
        animationSpec = tween(durationMillis = 160),
        label = "tvControlBorderColor"
    )

    this
        .zIndex(if (isFocused) 4f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (animatedBorderColor != Color.Transparent) {
                Modifier.border(width = borderWidth, color = animatedBorderColor, shape = shape)
            } else {
                Modifier
            }
        )
        .onFocusChanged { state ->
            val focusedNow = state.isFocused || state.hasFocus
            if (!focusedNow) {
                centerKeyHeld = false
                longPressTriggered = false
                longPressJob?.cancel()
            }
            isFocused = focusedNow
            onFocusChange?.invoke(focusedNow)
        }
        .then(
            if (onClick != null) {
                Modifier
                    .onPreviewKeyEvent { keyEvent ->
                        val isCenterKey = keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        if (!isCenterKey) return@onPreviewKeyEvent false

                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> {
                                if (!centerKeyHeld) {
                                    centerKeyHeld = true
                                    longPressTriggered = false
                                    if (onLongClick != null) {
                                        longPressJob?.cancel()
                                        longPressJob = scope.launch {
                                            delay(480L)
                                            if (centerKeyHeld) {
                                                longPressTriggered = true
                                                onLongClick()
                                            }
                                        }
                                    }
                                } else if (keyEvent.nativeKeyEvent.isLongPress && onLongClick != null && !longPressTriggered) {
                                    longPressTriggered = true
                                    longPressJob?.cancel()
                                    onLongClick()
                                }
                                true
                            }
                            KeyEventType.KeyUp -> {
                                val wasLong = longPressTriggered
                                centerKeyHeld = false
                                longPressTriggered = false
                                longPressJob?.cancel()
                                if (!wasLong) {
                                    onClick()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable(interactionSource = interactionSource)
                    .then(
                        if (onLongClick != null) {
                            Modifier.combinedClickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                onLongClick = onLongClick,
                                onClick = onClick
                            )
                        } else {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                onClick = onClick
                            )
                        }
                    )
            } else {
                Modifier
            }
        )
}

/**
 * Animazione cinematografica per le locandine (Film / Serie TV / Top 10 / Trailer):
 * - Al passaggio da una locandina all'altra (sia tramite scorrimento touch sia tramite D-pad del telecomando TV):
 *   applica un effetto 3D coverflow dinamico (inclinazione Y, scala progressiva, parallasse morbida),
 *   sollevamento con bordo luminoso bicolore, ombra rossa cinematografica e bagliore satinato diagonale ("sheen").
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.animatedPosterShowcase(
    itemIndex: Int,
    listState: LazyListState?,
    isFocused: Boolean,
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = 1.11f,
    onFocusedChanged: (Boolean) -> Unit,
    onLongClick: (() -> Unit)? = null,
    onQuickPlay: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()
    var centerKeyHeld by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "posterFocusScale"
    )

    val sheenProgress by animateFloatAsState(
        targetValue = if (isFocused) 1.35f else -0.45f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "posterSheen"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.8.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "posterBorderWidth"
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isFocused) 18.dp else 2.dp,
        animationSpec = tween(durationMillis = 200),
        label = "posterElevation"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed || centerKeyHeld) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "posterPressScale"
    )

    this
        .zIndex(if (isFocused) 10f else 0f)
        .graphicsLayer {
            // Effetto 3D coverflow dinamico al passaggio tra una locandina e l'altra nella LazyRow
            var scrollRotationY = 0f
            var scrollScale = 1f
            var scrollAlpha = 1f
            var scrollTranslationY = 0f

            if (listState != null) {
                val layoutInfo = listState.layoutInfo
                val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
                if (visibleItem != null && layoutInfo.viewportSize.width > 0) {
                    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                    val itemCenter = visibleItem.offset + visibleItem.size / 2f
                    val distanceFromCenter = (itemCenter - viewportCenter) / (layoutInfo.viewportSize.width / 2f)
                    val clamped = distanceFromCenter.coerceIn(-1.2f, 1.2f)
                    val absDist = abs(clamped)

                    if (!isFocused) {
                        scrollScale = (1.03f - absDist * 0.09f).coerceIn(0.90f, 1.03f)
                        scrollRotationY = (-clamped * 8.5f).coerceIn(-10f, 10f)
                        scrollAlpha = (1f - absDist * 0.18f).coerceIn(0.76f, 1f)
                        scrollTranslationY = (absDist * 8f).coerceIn(0f, 10f)
                    }
                }
            }

            val finalScale = (if (isFocused) focusScale else scrollScale) * pressScale
            scaleX = finalScale
            scaleY = finalScale
            rotationY = if (isFocused) 0f else scrollRotationY
            translationY = if (isFocused) -6f else scrollTranslationY
            alpha = if (isFocused) 1f else scrollAlpha
            cameraDistance = 14f * density
            transformOrigin = TransformOrigin.Center
        }
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = if (isFocused) NetflixRed else Color.Black,
            spotColor = if (isFocused) NetflixRed else Color.Black
        )
        .then(
            if (borderWidth > 0.dp) {
                Modifier.border(
                    width = borderWidth,
                    brush = Brush.linearGradient(
                        colors = listOf(NetflixWhite, NetflixRed, NetflixGold)
                    ),
                    shape = shape
                )
            } else {
                Modifier
            }
        )
        .drawWithContent {
            drawContent()
            // Riflesso luminoso diagonale animato quando si passa sopra una locandina
            if (isFocused || sheenProgress > -0.35f) {
                val width = size.width
                val height = size.height
                val sweepStart = width * sheenProgress - width * 0.45f
                val sweepEnd = width * sheenProgress + width * 0.15f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isFocused) 0.22f else 0.08f),
                            Color.Transparent
                        ),
                        start = Offset(sweepStart, 0f),
                        end = Offset(sweepEnd, height)
                    )
                )
            }
        }
        .onFocusChanged { state ->
            if (!state.isFocused) {
                centerKeyHeld = false
                longPressTriggered = false
                longPressJob?.cancel()
            }
            onFocusedChanged(state.isFocused)
        }
        .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown &&
                (keyEvent.key == Key.MediaPlay || keyEvent.key == Key.MediaPlayPause) &&
                onQuickPlay != null
            ) {
                onQuickPlay()
                return@onPreviewKeyEvent true
            }

            val isCenterKey = keyEvent.key == Key.DirectionCenter ||
                keyEvent.key == Key.Enter ||
                keyEvent.key == Key.NumPadEnter
            if (!isCenterKey) return@onPreviewKeyEvent false

            when (keyEvent.type) {
                KeyEventType.KeyDown -> {
                    if (!centerKeyHeld) {
                        centerKeyHeld = true
                        longPressTriggered = false
                        if (onLongClick != null) {
                            longPressJob?.cancel()
                            longPressJob = scope.launch {
                                delay(480L)
                                if (centerKeyHeld) {
                                    longPressTriggered = true
                                    onLongClick()
                                }
                            }
                        }
                    } else if (keyEvent.nativeKeyEvent.isLongPress && onLongClick != null && !longPressTriggered) {
                        longPressTriggered = true
                        longPressJob?.cancel()
                        onLongClick()
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    val wasLong = longPressTriggered
                    centerKeyHeld = false
                    longPressTriggered = false
                    longPressJob?.cancel()
                    if (!wasLong) {
                        onClick()
                    }
                    true
                }
                else -> false
            }
        }
        .focusable(interactionSource = interactionSource)
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
            } else {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            }
        )
}

/**
 * Barra del tempo (Seekbar) ottimizzata per il telecomando su Android TV / Google TV:
 * - Riceve chiaramente il focus D-pad (bordo bianco luminoso, cursore evidenziato, badge tempo e suggerimenti).
 * - Frecce Sinistra/Destra sul telecomando scorrono avanti/indietro lungo la timeline con accelerazione
 *   progressiva se tenute premute e commit fluido al rilascio (o entro 650ms).
 * - Tasto OK / Invio conferma lo spostamento o mette in Play/Pausa.
 * - Mantiene pienamente il trascinamento touch su smartphone e tablet.
 */
@Composable
fun TvSeekTimeBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekPreview: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onInteraction: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
    onScrubbingChanged: (Boolean) -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val maxMs = if (durationMs > 0L) {
        durationMs.coerceAtLeast(1_000L)
    } else {
        (currentPositionMs + 60_000L).coerceAtLeast(1_000L)
    }

    var isFocused by remember { mutableStateOf(false) }
    var isRemoteScrubbing by remember { mutableStateOf(false) }
    var remotePreviewMs by remember { mutableLongStateOf(currentPositionMs) }
    var scrubStartMs by remember { mutableLongStateOf(currentPositionMs) }
    var consecutivePresses by remember { mutableIntStateOf(0) }
    var lastDirection by remember { mutableIntStateOf(0) }
    var scrubTick by remember { mutableLongStateOf(0L) }

    val displayPosMs = (if (isRemoteScrubbing) remotePreviewMs else currentPositionMs).coerceIn(0L, maxMs)

    // Auto-commit di sicurezza se il telecomando non invia KeyUp dopo lo scorrimento
    LaunchedEffect(isRemoteScrubbing, scrubTick) {
        if (isRemoteScrubbing) {
            delay(650L)
            if (isRemoteScrubbing) {
                val target = remotePreviewMs
                isRemoteScrubbing = false
                consecutivePresses = 0
                lastDirection = 0
                onScrubbingChanged(false)
                onSeekCommit(target)
            }
        }
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            isRemoteScrubbing -> NetflixGold
            isFocused -> NetflixWhite
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "seekBarFocusBorder"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused || isRemoteScrubbing) {
                    NetflixBlack.copy(alpha = 0.78f)
                } else {
                    Color.Transparent
                }
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = if (isFocused || isRemoteScrubbing) 10.dp else 0.dp,
                vertical = if (isFocused || isRemoteScrubbing) 6.dp else 0.dp
            )
            .focusRequester(focusRequester)
            .focusProperties {
                if (upFocusRequester != null) up = upFocusRequester
                if (downFocusRequester != null) down = downFocusRequester
            }
            .onFocusChanged { state ->
                val focusedNow = state.isFocused || state.hasFocus
                if (isFocused && !focusedNow && isRemoteScrubbing) {
                    val target = remotePreviewMs
                    isRemoteScrubbing = false
                    consecutivePresses = 0
                    lastDirection = 0
                    onScrubbingChanged(false)
                    onSeekCommit(target)
                }
                isFocused = focusedNow
                onFocusChanged(focusedNow)
            }
            .onPreviewKeyEvent { keyEvent ->
                if (!isFocused) return@onPreviewKeyEvent false

                when (keyEvent.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val dir = if (keyEvent.key == Key.DirectionLeft) -1 else 1
                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> {
                                onInteraction()
                                if (!isRemoteScrubbing) {
                                    scrubStartMs = currentPositionMs.coerceIn(0L, maxMs)
                                    remotePreviewMs = scrubStartMs
                                    isRemoteScrubbing = true
                                    onScrubbingChanged(true)
                                }
                                if (lastDirection == dir) {
                                    consecutivePresses++
                                } else {
                                    lastDirection = dir
                                    consecutivePresses = 1
                                }

                                val isLongVideo = durationMs == 0L || durationMs > 5 * 60_000L
                                val stepMs = if (isLongVideo) {
                                    when {
                                        consecutivePresses <= 1 -> 10_000L
                                        consecutivePresses <= 5 -> 20_000L
                                        consecutivePresses <= 12 -> 45_000L
                                        else -> 90_000L
                                    }
                                } else {
                                    when {
                                        consecutivePresses <= 1 -> 5_000L
                                        consecutivePresses <= 5 -> 10_000L
                                        else -> 15_000L
                                    }
                                }

                                val nextTarget = (remotePreviewMs + dir * stepMs).coerceIn(0L, maxMs)
                                remotePreviewMs = nextTarget
                                scrubTick++
                                onSeekPreview(nextTarget)
                                return@onPreviewKeyEvent true
                            }
                            KeyEventType.KeyUp -> {
                                if (isRemoteScrubbing) {
                                    val commitTarget = remotePreviewMs
                                    isRemoteScrubbing = false
                                    consecutivePresses = 0
                                    lastDirection = 0
                                    onScrubbingChanged(false)
                                    onSeekCommit(commitTarget)
                                }
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent true
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            onInteraction()
                            if (isRemoteScrubbing) {
                                val commitTarget = remotePreviewMs
                                isRemoteScrubbing = false
                                consecutivePresses = 0
                                lastDirection = 0
                                onScrubbingChanged(false)
                                onSeekCommit(commitTarget)
                            } else {
                                onTogglePlayPause()
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    else -> Unit
                }
                false
            }
            .focusable()
    ) {
        AnimatedVisibility(visible = isFocused || isRemoteScrubbing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (isRemoteScrubbing) NetflixRed else NetflixElevatedSurface,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = formatSecondsToMmSs(displayPosMs / 1000L),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isRemoteScrubbing) {
                        val deltaSec = (remotePreviewMs - scrubStartMs) / 1000L
                        val deltaSign = if (deltaSec >= 0L) "+" else ""
                        Text(
                            text = "$deltaSign${deltaSec}s",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetflixGold
                        )
                    }
                }

                Text(
                    text = if (compact) "◀ ▶ Scorri • OK Pausa/Play" else "◀ ▶ Avanti/Indietro • OK Pausa/Play",
                    style = MaterialTheme.typography.labelSmall,
                    color = NetflixLightGray
                )
            }
        }

        if (compact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatSecondsToMmSs(displayPosMs / 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRemoteScrubbing) NetflixGold else NetflixWhite
                )
                Slider(
                    value = displayPosMs.toFloat().coerceIn(0f, maxMs.toFloat()),
                    onValueChange = { newValue ->
                        val target = newValue.toLong().coerceIn(0L, maxMs)
                        onInteraction()
                        onSeekPreview(target)
                        onSeekCommit(target)
                    },
                    valueRange = 0f..maxMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isFocused || isRemoteScrubbing) NetflixWhite else NetflixRed,
                        activeTrackColor = NetflixRed,
                        inactiveTrackColor = NetflixElevatedSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false }
                )
                Text(
                    text = formatSecondsToMmSs(durationMs / 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = NetflixLightGray
                )
            }
        } else {
            Slider(
                value = displayPosMs.toFloat().coerceIn(0f, maxMs.toFloat()),
                onValueChange = { newValue ->
                    val target = newValue.toLong().coerceIn(0L, maxMs)
                    onInteraction()
                    onSeekPreview(target)
                    onSeekCommit(target)
                },
                valueRange = 0f..maxMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = if (isFocused || isRemoteScrubbing) NetflixWhite else NetflixRed,
                    activeTrackColor = NetflixRed,
                    inactiveTrackColor = NetflixElevatedSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = false }
            )
        }
    }
}
