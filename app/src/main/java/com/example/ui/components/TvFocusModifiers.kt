package com.example.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ui.theme.NetflixGold
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlin.math.abs

/**
 * Rileva se l'app è eseguita su Android TV / Google TV (o dispositivo con interfaccia Leanback).
 */
@Composable
fun rememberIsTvDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) { isAndroidTvDevice(context) }
}

fun isAndroidTvDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return isTvMode || hasLeanback
}

/**
 * Modificatore universale per pulsanti, chip e voci di menu compatibile con Telecomando Android TV / Google TV
 * (D-pad + Invio/OK + visualizzazione chiara dello stato di focus).
 */
fun Modifier.tvFocusableControl(
    shape: Shape = RoundedCornerShape(8.dp),
    focusedScale: Float = 1.05f,
    focusedBorderColor: Color = NetflixWhite,
    unfocusedBorderColor: Color = Color.Transparent,
    borderWidth: Dp = 2.dp,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
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
            isFocused = focusedNow
            onFocusChange?.invoke(focusedNow)
        }
        .then(
            if (onClick != null) {
                Modifier
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp &&
                            (keyEvent.key == Key.DirectionCenter ||
                                keyEvent.key == Key.Enter ||
                                keyEvent.key == Key.NumPadEnter)
                        ) {
                            onClick()
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .clickable { onClick() }
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
fun Modifier.animatedPosterShowcase(
    itemIndex: Int,
    listState: LazyListState?,
    isFocused: Boolean,
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = 1.11f,
    onFocusedChanged: (Boolean) -> Unit,
    onClick: () -> Unit
): Modifier = composed {
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

            val finalScale = if (isFocused) focusScale else scrollScale
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
            onFocusedChanged(state.isFocused)
        }
        .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyUp &&
                (keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter)
            ) {
                onClick()
                true
            } else {
                false
            }
        }
        .focusable(interactionSource = interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
