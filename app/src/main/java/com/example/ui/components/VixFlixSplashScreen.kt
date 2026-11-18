package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BebasNeueFontFamily
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixRedDark
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

@Composable
fun VixFlixSplashScreen(
    isCatalogLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val isTvDevice = rememberIsTvDevice()
    val logoScale = remember { Animatable(0.90f) }
    val underlineFraction = remember { Animatable(0f) }
    var showWordmark by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "splashPulse")
    val glowPulse by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashGlowPulse"
    )

    LaunchedEffect(Unit) {
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    LaunchedEffect(Unit) {
        delay(110L)
        showWordmark = true
        underlineFraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
        )
        showTagline = true
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .onPreviewKeyEvent { true }
            .focusable()
            .drawBehind {
                val center = Offset(size.width * 0.5f, size.height * 0.44f)
                val maxDim = size.maxDimension
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NetflixRed.copy(alpha = 0.24f),
                            NetflixRedDark.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxDim * 0.42f * glowPulse
                    ),
                    radius = maxDim * 0.45f * glowPulse,
                    center = center
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("vixflix_splash_screen")
    ) {
        val isLargeScreen = isTvDevice || maxWidth >= 840.dp
        val emblemSize = if (isLargeScreen) 128.dp else 96.dp
        val titleSize = if (isLargeScreen) 56.sp else 44.sp
        val letterSpacing = if (isLargeScreen) 8.sp else 5.5.sp

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(emblemSize)
                    .testTag("splash_logo_badge")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_splash_logo),
                    contentDescription = stringResource(id = R.string.splash_logo_description),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                        }
                )
            }

            Spacer(modifier = Modifier.height(if (isLargeScreen) 20.dp else 14.dp))

            AnimatedVisibility(
                visible = showWordmark,
                enter = fadeIn(tween(280)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.app_name).uppercase(),
                            fontFamily = BebasNeueFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = titleSize,
                            letterSpacing = letterSpacing,
                            color = NetflixWhite,
                            modifier = Modifier.testTag("splash_brand_title")
                        )
                        if (isLargeScreen) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                color = NetflixRed,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "TV",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .width(if (isLargeScreen) 180.dp else 140.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(NetflixElevatedSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(underlineFraction.value.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(NetflixRedDark, NetflixRed, NetflixWhite.copy(alpha = 0.9f), NetflixRed)
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(
                visible = showTagline,
                enter = fadeIn(tween(260))
            ) {
                Text(
                    text = stringResource(id = R.string.splash_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = if (isLargeScreen) 2.6.sp else 1.8.sp,
                    color = NetflixLightGray,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = if (isLargeScreen) 44.dp else 32.dp)
                .widthIn(max = 260.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                color = NetflixRed,
                trackColor = NetflixElevatedSurface,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .testTag("splash_loading_indicator")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(
                    id = if (isCatalogLoading) R.string.splash_loading_catalog else R.string.splash_ready
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NetflixMutedGray,
                textAlign = TextAlign.Center
            )
        }
    }
}
