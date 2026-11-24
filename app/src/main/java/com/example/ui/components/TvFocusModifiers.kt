package com.example.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import java.util.WeakHashMap
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Registro sincrono dei [FocusRequester] attualmente montati nella gerarchia Compose.
 * Previene crash `IllegalStateException: FocusRequester is not initialized` su Android TV
 * quando un target di `focusProperties` o `requestFocus()` scorre fuori schermo in LazyRow/LazyColumn
 * o viene smontato durante un cambio categoria/scheda.
 */
private object FocusAttachmentTracker {
    private val attachedCounts = WeakHashMap<FocusRequester, Int>()

    @Synchronized
    fun onAttach(requester: FocusRequester) {
        attachedCounts[requester] = (attachedCounts[requester] ?: 0) + 1
    }

    @Synchronized
    fun onDetach(requester: FocusRequester) {
        val current = attachedCounts[requester] ?: return
        if (current <= 1) {
            attachedCounts.remove(requester)
        } else {
            attachedCounts[requester] = current - 1
        }
    }

    @Synchronized
    fun isAttached(requester: FocusRequester?): Boolean {
        return requester != null && (attachedCounts[requester] ?: 0) > 0
    }
}

private class FocusAttachmentTrackNode(
    var requester: FocusRequester
) : Modifier.Node() {
    override fun onAttach() {
        FocusAttachmentTracker.onAttach(requester)
    }

    override fun onDetach() {
        FocusAttachmentTracker.onDetach(requester)
    }
}

private data class FocusAttachmentTrackElement(
    val requester: FocusRequester
) : ModifierNodeElement<FocusAttachmentTrackNode>() {
    override fun create(): FocusAttachmentTrackNode = FocusAttachmentTrackNode(requester)

    override fun update(node: FocusAttachmentTrackNode) {
        if (node.requester !== requester) {
            if (node.isAttached) {
                FocusAttachmentTracker.onDetach(node.requester)
                node.requester = requester
                FocusAttachmentTracker.onAttach(requester)
            } else {
                node.requester = requester
            }
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "safeFocusRequester"
    }
}

fun Modifier.safeFocusRequester(focusRequester: FocusRequester?): Modifier {
    if (focusRequester == null) return this
    return this
        .then(FocusAttachmentTrackElement(focusRequester))
        .focusRequester(focusRequester)
}

fun FocusRequester?.takeIfAttached(): FocusRequester? {
    val candidate = this ?: return null
    return if (FocusAttachmentTracker.isAttached(candidate)) candidate else null
}

fun FocusRequester?.safeRequestFocus(): Boolean {
    val candidate = takeIfAttached() ?: return false
    return runCatching {
        candidate.requestFocus()
        true
    }.getOrDefault(false)
}

/**
 * Rileva se l'app deve usare la modalità Widescreen unificata per Android TV, Google TV,
 * Fire TV Stick e Tablet / modalità Orizzontale (larghezza >= 600dp o orientamento landscape),
 * garantendo parità 1:1 di layout, scheda dettaglio e navigazione Touch + D-Pad.
 */
@Composable
fun rememberIsTvDevice(): Boolean {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(
        context,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
        configuration.uiMode
    ) {
        isAndroidTvDevice(context, configuration)
    }
}

@Suppress("DEPRECATION")
fun isAndroidTvDevice(
    context: Context,
    configuration: Configuration = context.resources.configuration
): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val pm = context.packageManager
    val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasTelevision = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    val isAmazonFireTv = Build.MANUFACTURER.equals("Amazon", ignoreCase = true) &&
        Build.MODEL.startsWith("AFT", ignoreCase = true)
    val isLandscapeOrTablet = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600 ||
        configuration.smallestScreenWidthDp >= 600 ||
        (configuration.screenWidthDp > 0 && configuration.screenHeightDp > 0 &&
            configuration.screenWidthDp > configuration.screenHeightDp)
    return isTvMode || hasLeanback || hasTelevision || isAmazonFireTv || isLandscapeOrTablet
}

/**
 * Gestore atomico e privo di doppie attivazioni per i tasti BACK ed ESCAPE su Android TV / Fire TV / tastiera:
 * - Consuma `KeyDown` registrando che la pressione è iniziata su questo contenitore (ignorando i repeat).
 * - Esegue [onBack] su `KeyUp` SOLO se il corrispondente `KeyDown` è stato ricevuto su questo stesso nodo.
 * - Consuma sempre `KeyUp` per impedire che eventi orfani o rilasci tardivi propaghino al `BackHandler`
 *   o a schermate sottostanti (evitando doppie chiusure o popup di uscita involontari).
 */
fun Modifier.consumeBackAndEscapeKey(
    enabled: Boolean = true,
    onBack: () -> Unit
): Modifier = composed {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnBack by rememberUpdatedState(onBack)
    var backKeyDownSeen by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            backKeyDownSeen = false
        }
    }

    this
        .onFocusChanged { state ->
            if (!state.hasFocus) {
                backKeyDownSeen = false
            }
        }
        .onPreviewKeyEvent { keyEvent ->
            if (!latestEnabled) {
                backKeyDownSeen = false
                return@onPreviewKeyEvent false
            }
            val isBackOrEscape = keyEvent.key == Key.Back || keyEvent.key == Key.Escape
            if (!isBackOrEscape) return@onPreviewKeyEvent false

            when (keyEvent.type) {
                KeyEventType.KeyDown -> {
                    if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                        backKeyDownSeen = true
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    val shouldFire = backKeyDownSeen
                    backKeyDownSeen = false
                    if (shouldFire) {
                        latestOnBack()
                    }
                    true
                }
                else -> true
            }
        }
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
    focusTag: String? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val dpadFocusScope = LocalDpadFocusScope.current
    val resolvedFocusTag = remember(focusTag, dpadFocusScope) {
        focusTag ?: "tv_control_${dpadFocusScope.name.lowercase()}_${System.identityHashCode(Any())}"
    }
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var centerKeyHeld by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    androidx.compose.runtime.DisposableEffect(dpadFocusScope, resolvedFocusTag) {
        onDispose {
            if (isFocused) {
                DpadFocusObserverRegistry.onNodeFocusChanged(
                    scope = dpadFocusScope,
                    tag = resolvedFocusTag,
                    isFocused = false
                )
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed || centerKeyHeld -> 0.96f
            isFocused -> focusedScale
            else -> 1f
        },
        animationSpec = tween(durationMillis = 100, easing = LinearOutSlowInEasing),
        label = "tvControlScale"
    )

    val activeBorderColor = if (isFocused) focusedBorderColor else unfocusedBorderColor

    this
        .zIndex(if (isFocused) 4f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
        .then(
            if (activeBorderColor != Color.Transparent) {
                Modifier.border(width = borderWidth, color = activeBorderColor, shape = shape)
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
            if (isFocused != focusedNow) {
                isFocused = focusedNow
                DpadFocusObserverRegistry.onNodeFocusChanged(
                    scope = dpadFocusScope,
                    tag = resolvedFocusTag,
                    isFocused = focusedNow
                )
            }
            onFocusChange?.invoke(focusedNow)
        }
        .then(
            if (onClick != null) {
                Modifier
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Menu && onLongClick != null) {
                            when (keyEvent.type) {
                                KeyEventType.KeyDown -> {
                                    if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                        onLongClick()
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                KeyEventType.KeyUp -> return@onPreviewKeyEvent true
                                else -> return@onPreviewKeyEvent true
                            }
                        }
                        val isCenterKey = keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        if (!isCenterKey) return@onPreviewKeyEvent false

                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> {
                                val isNativeLongPress = keyEvent.nativeKeyEvent.isLongPress ||
                                    keyEvent.nativeKeyEvent.repeatCount > 0 ||
                                    (keyEvent.nativeKeyEvent.flags and android.view.KeyEvent.FLAG_LONG_PRESS) != 0
                                if (isNativeLongPress && onLongClick != null && !longPressTriggered) {
                                    centerKeyHeld = true
                                    longPressTriggered = true
                                    longPressJob?.cancel()
                                    onLongClick()
                                } else if (!centerKeyHeld) {
                                    centerKeyHeld = true
                                    longPressTriggered = false
                                    if (onLongClick != null) {
                                        longPressJob?.cancel()
                                        longPressJob = scope.launch {
                                            delay(480L)
                                            if (centerKeyHeld && !longPressTriggered) {
                                                longPressTriggered = true
                                                onLongClick()
                                            }
                                        }
                                    }
                                }
                                true
                            }
                            KeyEventType.KeyUp -> {
                                val wasHeld = centerKeyHeld
                                val wasLong = longPressTriggered
                                centerKeyHeld = false
                                longPressTriggered = false
                                longPressJob?.cancel()
                                if (wasHeld && !wasLong) {
                                    onClick()
                                }
                                true
                            }
                            else -> false
                        }
                    }
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
                // Il componente Material3 ospite (Button/OutlinedButton/TextButton/IconButton/Switch)
                // fornisce già il proprio nodo clickable+focusable: evitiamo un doppio focus target
                // e blocchiamo eventuali KeyUp orfani di OK/Invio che genererebbero click fantasma.
                Modifier.onPreviewKeyEvent { keyEvent ->
                    val isCenterKey = keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.NumPadEnter
                    if (!isCenterKey) return@onPreviewKeyEvent false
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            centerKeyHeld = true
                            false
                        }
                        KeyEventType.KeyUp -> {
                            val wasHeld = centerKeyHeld
                            centerKeyHeld = false
                            !wasHeld
                        }
                        else -> false
                    }
                }
            }
        )
}

/**
 * Feedback visivo essenziale e reattivo per le locandine (Film / Serie TV / Top 10 / Trailer):
 * - Usa una singola scala 2D rapida e un bordo luminoso istantaneo al focus,
 *   senza calcoli 3D per-frame, ombre dinamiche o gradienti continui durante lo scorrimento.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.animatedPosterShowcase(
    itemIndex: Int,
    @Suppress("UNUSED_PARAMETER") listState: LazyListState?,
    isFocused: Boolean,
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = 1.07f,
    focusTag: String? = null,
    onFocusedChanged: (Boolean) -> Unit,
    onLongClick: (() -> Unit)? = null,
    onQuickPlay: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val dpadFocusScope = LocalDpadFocusScope.current
    val resolvedFocusTag = remember(focusTag, itemIndex, dpadFocusScope) {
        focusTag ?: "poster_${dpadFocusScope.name.lowercase()}_idx_$itemIndex"
    }
    var lastReportedFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var centerKeyHeld by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    androidx.compose.runtime.DisposableEffect(dpadFocusScope, resolvedFocusTag) {
        onDispose {
            if (lastReportedFocus) {
                DpadFocusObserverRegistry.onNodeFocusChanged(
                    scope = dpadFocusScope,
                    tag = resolvedFocusTag,
                    isFocused = false
                )
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed || centerKeyHeld -> 0.97f
        isFocused -> focusedScale.coerceAtMost(1.08f)
        else -> 1f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 110, easing = LinearOutSlowInEasing),
        label = "posterFocusScale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing),
        label = "posterFocusGlowAlpha"
    )

    this
        .zIndex(if (isFocused) 10f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
        .drawBehind {
            if (glowAlpha > 0.01f) {
                val baseCornerPx = 10.dp.toPx()
                // Alone luminoso morbido multistrato attorno al contorno della locandina (senza cornice bianca rigida)
                val outerSpread1 = 11.dp.toPx()
                drawRoundRect(
                    color = NetflixRed.copy(alpha = 0.09f * glowAlpha),
                    topLeft = Offset(-outerSpread1, -outerSpread1),
                    size = Size(size.width + outerSpread1 * 2f, size.height + outerSpread1 * 2f),
                    cornerRadius = CornerRadius(baseCornerPx + outerSpread1, baseCornerPx + outerSpread1)
                )
                val outerSpread2 = 7.5.dp.toPx()
                drawRoundRect(
                    color = NetflixRed.copy(alpha = 0.16f * glowAlpha),
                    topLeft = Offset(-outerSpread2, -outerSpread2),
                    size = Size(size.width + outerSpread2 * 2f, size.height + outerSpread2 * 2f),
                    cornerRadius = CornerRadius(baseCornerPx + outerSpread2, baseCornerPx + outerSpread2)
                )
                val outerSpread3 = 4.5.dp.toPx()
                drawRoundRect(
                    color = Color(0xFFFF2A36).copy(alpha = 0.25f * glowAlpha),
                    topLeft = Offset(-outerSpread3, -outerSpread3),
                    size = Size(size.width + outerSpread3 * 2f, size.height + outerSpread3 * 2f),
                    cornerRadius = CornerRadius(baseCornerPx + outerSpread3, baseCornerPx + outerSpread3)
                )
                val outerSpread4 = 2.dp.toPx()
                drawRoundRect(
                    color = Color(0xFFFF5C66).copy(alpha = 0.34f * glowAlpha),
                    topLeft = Offset(-outerSpread4, -outerSpread4),
                    size = Size(size.width + outerSpread4 * 2f, size.height + outerSpread4 * 2f),
                    cornerRadius = CornerRadius(baseCornerPx + outerSpread4, baseCornerPx + outerSpread4)
                )
                drawRoundRect(
                    color = Color(0xFFFF8F96).copy(alpha = 0.42f * glowAlpha),
                    topLeft = Offset(-0.75.dp.toPx(), -0.75.dp.toPx()),
                    size = Size(size.width + 1.5.dp.toPx(), size.height + 1.5.dp.toPx()),
                    cornerRadius = CornerRadius(baseCornerPx + 1.dp.toPx(), baseCornerPx + 1.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
        .onFocusChanged { state ->
            if (!state.isFocused) {
                centerKeyHeld = false
                longPressTriggered = false
                longPressJob?.cancel()
            }
            if (lastReportedFocus != state.isFocused) {
                lastReportedFocus = state.isFocused
                DpadFocusObserverRegistry.onNodeFocusChanged(
                    scope = dpadFocusScope,
                    tag = resolvedFocusTag,
                    isFocused = state.isFocused
                )
            }
            onFocusedChanged(state.isFocused)
        }
        .onPreviewKeyEvent { keyEvent ->
            if ((keyEvent.key == Key.MediaPlay || keyEvent.key == Key.MediaPlayPause) &&
                onQuickPlay != null
            ) {
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                            onQuickPlay()
                        }
                        return@onPreviewKeyEvent true
                    }
                    KeyEventType.KeyUp -> return@onPreviewKeyEvent true
                    else -> return@onPreviewKeyEvent true
                }
            }
            if (keyEvent.key == Key.Menu && onLongClick != null) {
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                            onLongClick()
                        }
                        return@onPreviewKeyEvent true
                    }
                    KeyEventType.KeyUp -> return@onPreviewKeyEvent true
                    else -> return@onPreviewKeyEvent true
                }
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
                    val wasHeld = centerKeyHeld
                    val wasLong = longPressTriggered
                    centerKeyHeld = false
                    longPressTriggered = false
                    longPressJob?.cancel()
                    if (wasHeld && !wasLong) {
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
    showAssistiveHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    val maxMs = if (durationMs > 0L) {
        durationMs.coerceAtLeast(1_000L)
    } else {
        (currentPositionMs + 60_000L).coerceAtLeast(1_000L)
    }

    val dpadFocusScope = LocalDpadFocusScope.current
    var isFocused by remember { mutableStateOf(false) }
    var isRemoteScrubbing by remember { mutableStateOf(false) }
    var isTouchScrubbing by remember { mutableStateOf(false) }
    var remotePreviewMs by remember { mutableLongStateOf(currentPositionMs) }
    var touchPreviewMs by remember { mutableLongStateOf(currentPositionMs) }
    var scrubStartMs by remember { mutableLongStateOf(currentPositionMs) }
    var consecutivePresses by remember { mutableIntStateOf(0) }
    var lastDirection by remember { mutableIntStateOf(0) }
    var scrubTick by remember { mutableLongStateOf(0L) }

    val isAnyScrubbing = isRemoteScrubbing || isTouchScrubbing

    androidx.compose.runtime.DisposableEffect(dpadFocusScope) {
        onDispose {
            if (isFocused) {
                DpadFocusObserverRegistry.onNodeFocusChanged(
                    scope = dpadFocusScope,
                    tag = "player_time_bar",
                    isFocused = false
                )
            }
        }
    }

    val displayPosMs = when {
        isTouchScrubbing -> touchPreviewMs
        isRemoteScrubbing -> remotePreviewMs
        else -> currentPositionMs
    }.coerceIn(0L, maxMs)

    // Auto-commit di sicurezza dopo pausa prolungata durante lo scorrimento da telecomando
    LaunchedEffect(isRemoteScrubbing, scrubTick) {
        if (isRemoteScrubbing) {
            delay(1_600L)
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

    val borderColor = when {
        isAnyScrubbing -> NetflixGold
        isFocused -> NetflixWhite
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused || isAnyScrubbing) {
                    NetflixBlack.copy(alpha = 0.65f)
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
                horizontal = if (isFocused || isAnyScrubbing) 10.dp else 0.dp,
                vertical = if (isFocused || isAnyScrubbing) 4.dp else 0.dp
            )
            .safeFocusRequester(focusRequester)
            .focusProperties {
                upFocusRequester.takeIfAttached()?.let { up = it }
                downFocusRequester.takeIfAttached()?.let { down = it }
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
                if (isFocused != focusedNow) {
                    isFocused = focusedNow
                    DpadFocusObserverRegistry.onNodeFocusChanged(
                        scope = dpadFocusScope,
                        tag = "player_time_bar",
                        isFocused = focusedNow
                    )
                }
                onFocusChanged(focusedNow)
            }
            .onPreviewKeyEvent { keyEvent ->
                if (!isFocused) return@onPreviewKeyEvent false

                when (keyEvent.key) {
                    Key.DirectionUp -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            onInteraction()
                            if (isRemoteScrubbing) {
                                val target = remotePreviewMs
                                isRemoteScrubbing = false
                                consecutivePresses = 0
                                lastDirection = 0
                                onScrubbingChanged(false)
                                onSeekCommit(target)
                            }
                            return@onPreviewKeyEvent upFocusRequester?.safeRequestFocus() ?: true
                        }
                        return@onPreviewKeyEvent true
                    }
                    Key.DirectionDown -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            onInteraction()
                            if (isRemoteScrubbing) {
                                val target = remotePreviewMs
                                isRemoteScrubbing = false
                                consecutivePresses = 0
                                lastDirection = 0
                                onScrubbingChanged(false)
                                onSeekCommit(target)
                            }
                            return@onPreviewKeyEvent downFocusRequester?.safeRequestFocus() ?: true
                        }
                        return@onPreviewKeyEvent true
                    }
                    Key.Escape -> {
                        if (isRemoteScrubbing) {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                val restoreTarget = scrubStartMs
                                isRemoteScrubbing = false
                                consecutivePresses = 0
                                lastDirection = 0
                                onScrubbingChanged(false)
                                onSeekPreview(restoreTarget)
                            }
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
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
                                // Consuma KeyUp senza interrompere la sessione di scrubbing tra pressioni ripetute
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent true
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            onInteraction()
                            if (isRemoteScrubbing) {
                                val commitTarget = remotePreviewMs
                                isRemoteScrubbing = false
                                consecutivePresses = 0
                                lastDirection = 0
                                onScrubbingChanged(false)
                                onSeekCommit(commitTarget)
                            } else if (compact) {
                                onTogglePlayPause()
                            }
                        }
                        // Consuma sia KeyDown che KeyUp per evitare conflitti o pause involontarie
                        return@onPreviewKeyEvent true
                    }
                    else -> Unit
                }
                false
            }
            .focusable()
    ) {
        if (showAssistiveHeader) {
            AnimatedVisibility(visible = isFocused || isAnyScrubbing) {
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
                            color = if (isAnyScrubbing) NetflixRed else NetflixElevatedSurface,
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
                        text = if (compact) "◀ ▶ Scorri • OK Pausa/Play" else "◀ ▶ Avanti/Indietro • OK Conferma",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray
                    )
                }
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
                    color = if (isAnyScrubbing) NetflixGold else NetflixWhite
                )
                Slider(
                    value = displayPosMs.toFloat().coerceIn(0f, maxMs.toFloat()),
                    onValueChange = { newValue ->
                        val target = newValue.toLong().coerceIn(0L, maxMs)
                        touchPreviewMs = target
                        if (!isTouchScrubbing) {
                            isTouchScrubbing = true
                            onScrubbingChanged(true)
                        }
                        onInteraction()
                        onSeekPreview(target)
                    },
                    onValueChangeFinished = {
                        if (isTouchScrubbing) {
                            val commitTarget = touchPreviewMs
                            isTouchScrubbing = false
                            onScrubbingChanged(false)
                            onSeekCommit(commitTarget)
                        }
                    },
                    valueRange = 0f..maxMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isFocused || isAnyScrubbing) NetflixWhite else NetflixRed,
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
                    touchPreviewMs = target
                    if (!isTouchScrubbing) {
                        isTouchScrubbing = true
                        onScrubbingChanged(true)
                    }
                    onInteraction()
                    onSeekPreview(target)
                },
                onValueChangeFinished = {
                    if (isTouchScrubbing) {
                        val commitTarget = touchPreviewMs
                        isTouchScrubbing = false
                        onScrubbingChanged(false)
                        onSeekCommit(commitTarget)
                    }
                },
                valueRange = 0f..maxMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = if (isFocused || isAnyScrubbing) NetflixWhite else NetflixRed,
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

/**
 * BringIntoViewSpec orizzontale con margini di sicurezza start/end per Amazon Fire TV Stick e Android TV.
 * Impedisce al focus D-Pad di allineare la prima card a ridosso del menu laterale (offset 0)
 * o l'ultima card contro il bordo destro dello schermo (tagliandone scala/alone di focus).
 */
@OptIn(ExperimentalFoundationApi::class)
internal class SafeEdgeHorizontalBringIntoViewSpec(
    private val startMarginPx: Float,
    private val endMarginPx: Float
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float {
        if (containerSize <= 0f || size <= 0f) return 0f
        val safeStartRaw = startMarginPx.coerceAtLeast(0f)
        val safeEndRaw = endMarginPx.coerceAtLeast(0f)
        val totalMargin = safeStartRaw + safeEndRaw
        val maxAllowedMargin = (containerSize - size).coerceAtLeast(0f)

        val effectiveStart: Float
        val effectiveEnd: Float
        if (totalMargin <= maxAllowedMargin) {
            effectiveStart = safeStartRaw
            effectiveEnd = safeEndRaw
        } else if (totalMargin > 0f) {
            val scale = maxAllowedMargin / totalMargin
            effectiveStart = safeStartRaw * scale
            effectiveEnd = safeEndRaw * scale
        } else {
            effectiveStart = 0f
            effectiveEnd = 0f
        }

        val trailingEdge = offset + size
        val safeTrailingLimit = containerSize - effectiveEnd
        return when {
            offset < effectiveStart -> offset - effectiveStart
            trailingEdge > safeTrailingLimit -> trailingEdge - safeTrailingLimit
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberTvHorizontalBringIntoViewSpec(
    startMargin: Dp,
    endMargin: Dp,
    trailingScaleAllowance: Dp = 16.dp
): BringIntoViewSpec {
    val density = LocalDensity.current
    val startPx = with(density) { startMargin.toPx() }
    val endPx = with(density) { (endMargin + trailingScaleAllowance).toPx() }
    return remember(startPx, endPx) {
        SafeEdgeHorizontalBringIntoViewSpec(
            startMarginPx = startPx,
            endMarginPx = endPx
        )
    }
}

/**
 * BringIntoViewSpec verticale con margini di sicurezza top/bottom per Fire TV Stick e Android TV.
 * Garantisce che durante la navigazione D-Pad nei menu (es. Impostazioni), l'elemento focalizzato
 * non venga mai tagliato ai bordi superiore o inferiore dello schermo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberTvVerticalBringIntoViewSpec(
    topMargin: Dp = 44.dp,
    bottomMargin: Dp = 56.dp
): BringIntoViewSpec {
    val density = LocalDensity.current
    val topPx = with(density) { topMargin.toPx() }
    val bottomPx = with(density) { bottomMargin.toPx() }
    return remember(topPx, bottomPx) {
        SafeEdgeHorizontalBringIntoViewSpec(
            startMarginPx = topPx,
            endMarginPx = bottomPx
        )
    }
}


