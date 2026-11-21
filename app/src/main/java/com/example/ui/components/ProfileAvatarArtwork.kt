package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.AvatarEmblemStyle
import com.example.data.local.ProfileAvatarPreset
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixWhite

/**
 * Avatar cinematografico originale renderizzato via Canvas vettoriale (100% offline,
 * nitido a qualsiasi risoluzione TV 4K / smartphone, zero asset proprietari copiati).
 */
@Composable
fun ProfileAvatarArtwork(
    avatarId: String,
    accentColorHex: Long,
    size: Dp,
    cornerRadius: Dp = 18.dp,
    showEditOverlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val preset = remember(avatarId) { ProfileAvatarPreset.fromId(avatarId) }
    val accentColor = remember(accentColorHex) { Color(accentColorHex) }
    val primaryColor = remember(preset, accentColor) {
        Color(preset.primaryColorHex).copy(alpha = 0.92f)
    }
    val secondaryColor = remember(preset) { Color(preset.secondaryColorHex) }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(accentColor, primaryColor, secondaryColor)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Luce radiale superiore stile cinematografico
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(this.size.width * 0.25f, this.size.height * 0.2f),
                    radius = this.size.minDimension * 0.85f
                )
            )
            // Vignettatura scura inferiore per profondita
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.34f)
                    ),
                    startY = this.size.height * 0.45f,
                    endY = this.size.height
                )
            )
            drawAvatarEmblem(preset.emblemStyle)
        }

        if (showEditOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixBlack.copy(alpha = 0.56f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((size * 0.42f).coerceIn(28.dp, 54.dp))
                        .clip(CircleShape)
                        .background(NetflixBlack.copy(alpha = 0.72f))
                        .border(2.dp, NetflixWhite, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifica profilo",
                        tint = NetflixWhite,
                        modifier = Modifier.size((size * 0.22f).coerceIn(16.dp, 28.dp))
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawAvatarEmblem(style: AvatarEmblemStyle) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val strokeW = (w * 0.068f).coerceAtLeast(2.2f)
    val ink = Color.White.copy(alpha = 0.94f)
    val softInk = Color.White.copy(alpha = 0.26f)

    when (style) {
        AvatarEmblemStyle.CINEMA_SMILE -> {
            // Espressione amichevole minimalista stile streaming
            drawCircle(ink, radius = w * 0.07f, center = Offset(w * 0.33f, h * 0.39f))
            drawCircle(ink, radius = w * 0.07f, center = Offset(w * 0.67f, h * 0.39f))
            val smile = Path().apply {
                moveTo(w * 0.28f, h * 0.58f)
                quadraticTo(cx, h * 0.77f, w * 0.74f, h * 0.55f)
            }
            drawPath(smile, color = ink, style = Stroke(width = strokeW * 1.1f, cap = StrokeCap.Round))
        }

        AvatarEmblemStyle.NEON_VISOR -> {
            // Visiera futuristica cyber
            drawRoundRect(
                color = softInk,
                topLeft = Offset(w * 0.16f, h * 0.33f),
                size = Size(w * 0.68f, h * 0.26f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(w * 0.16f, h * 0.33f),
                size = Size(w * 0.68f, h * 0.26f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                style = Stroke(width = strokeW)
            )
            drawLine(ink, Offset(w * 0.28f, h * 0.46f), Offset(w * 0.72f, h * 0.46f), strokeWidth = strokeW * 0.75f, cap = StrokeCap.Round)
            drawLine(ink, Offset(w * 0.38f, h * 0.72f), Offset(w * 0.62f, h * 0.72f), strokeWidth = strokeW * 0.9f, cap = StrokeCap.Round)
        }

        AvatarEmblemStyle.OCEAN_WAVE -> {
            // Onde fluide oceaniche
            for (i in 0..2) {
                val y = h * (0.36f + i * 0.15f)
                val wave = Path().apply {
                    moveTo(w * 0.18f, y)
                    cubicTo(w * 0.34f, y - h * 0.10f, w * 0.50f, y + h * 0.10f, w * 0.66f, y)
                    quadraticTo(w * 0.74f, y - h * 0.05f, w * 0.82f, y)
                }
                drawPath(
                    wave,
                    color = ink.copy(alpha = 0.95f - i * 0.18f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
        }

        AvatarEmblemStyle.SOLAR_FLARE -> {
            // Sole radioso geometrico
            drawCircle(softInk, radius = w * 0.28f, center = Offset(cx, cy))
            drawCircle(ink, radius = w * 0.17f, center = Offset(cx, cy))
            val rayCount = 8
            for (i in 0 until rayCount) {
                val angle = Math.toRadians(i * (360.0 / rayCount))
                val inner = w * 0.24f
                val outer = w * 0.36f
                drawLine(
                    color = ink,
                    start = Offset(cx + (kotlin.math.cos(angle) * inner).toFloat(), cy + (kotlin.math.sin(angle) * inner).toFloat()),
                    end = Offset(cx + (kotlin.math.cos(angle) * outer).toFloat(), cy + (kotlin.math.sin(angle) * outer).toFloat()),
                    strokeWidth = strokeW * 0.85f,
                    cap = StrokeCap.Round
                )
            }
        }

        AvatarEmblemStyle.EMERALD_DIAMOND -> {
            // Gemma sfaccettata
            val diamond = Path().apply {
                moveTo(cx, h * 0.18f)
                lineTo(w * 0.78f, cy)
                lineTo(cx, h * 0.82f)
                lineTo(w * 0.22f, cy)
                close()
            }
            drawPath(diamond, color = softInk)
            drawPath(diamond, color = ink, style = Stroke(width = strokeW, join = StrokeJoin.Round))
            drawLine(ink, Offset(w * 0.22f, cy), Offset(w * 0.78f, cy), strokeWidth = strokeW * 0.65f)
            drawLine(ink, Offset(cx, h * 0.18f), Offset(cx, h * 0.82f), strokeWidth = strokeW * 0.55f)
        }

        AvatarEmblemStyle.AMETHYST_STARS -> {
            // Costellazione brillante
            drawFourPointStar(cx, cy, w * 0.26f, ink)
            drawFourPointStar(w * 0.27f, h * 0.30f, w * 0.10f, ink.copy(alpha = 0.8f))
            drawFourPointStar(w * 0.74f, h * 0.70f, w * 0.11f, ink.copy(alpha = 0.85f))
        }

        AvatarEmblemStyle.MIDNIGHT_CAT -> {
            // Silhouette stilizzata felina notturna
            val ears = Path().apply {
                moveTo(w * 0.24f, h * 0.45f)
                lineTo(w * 0.28f, h * 0.22f)
                lineTo(w * 0.42f, h * 0.34f)
                lineTo(w * 0.58f, h * 0.34f)
                lineTo(w * 0.72f, h * 0.22f)
                lineTo(w * 0.76f, h * 0.45f)
                quadraticTo(w * 0.76f, h * 0.74f, cx, h * 0.76f)
                quadraticTo(w * 0.24f, h * 0.74f, w * 0.24f, h * 0.45f)
                close()
            }
            drawPath(ears, color = softInk)
            drawPath(ears, color = ink, style = Stroke(width = strokeW * 0.9f, join = StrokeJoin.Round))
            drawCircle(ink, radius = w * 0.055f, center = Offset(w * 0.39f, h * 0.52f))
            drawCircle(ink, radius = w * 0.055f, center = Offset(w * 0.61f, h * 0.52f))
        }

        AvatarEmblemStyle.COSMIC_PLANET -> {
            // Pianeta con anello orbitale
            drawCircle(softInk, radius = w * 0.22f, center = Offset(cx, cy))
            drawCircle(ink, radius = w * 0.22f, center = Offset(cx, cy), style = Stroke(width = strokeW * 0.9f))
            val ring = Path().apply {
                moveTo(w * 0.15f, h * 0.62f)
                cubicTo(w * 0.28f, h * 0.36f, w * 0.72f, h * 0.32f, w * 0.85f, h * 0.40f)
                cubicTo(w * 0.72f, h * 0.66f, w * 0.28f, h * 0.70f, w * 0.15f, h * 0.62f)
            }
            drawPath(ring, color = ink, style = Stroke(width = strokeW * 0.85f, cap = StrokeCap.Round))
        }

        AvatarEmblemStyle.RETRO_REEL -> {
            // Bobina cinema retro
            drawCircle(ink, radius = w * 0.30f, center = Offset(cx, cy), style = Stroke(width = strokeW))
            drawCircle(ink, radius = w * 0.07f, center = Offset(cx, cy))
            val holes = listOf(
                Offset(cx, cy - h * 0.17f),
                Offset(cx, cy + h * 0.17f),
                Offset(cx - w * 0.17f, cy),
                Offset(cx + w * 0.17f, cy)
            )
            holes.forEach { offset ->
                drawCircle(softInk, radius = w * 0.065f, center = offset)
                drawCircle(ink, radius = w * 0.065f, center = offset, style = Stroke(width = strokeW * 0.5f))
            }
        }

        AvatarEmblemStyle.CORAL_SUNSET -> {
            // Tramonto cinematografico sull'orizzonte
            drawArc(
                color = ink,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w * 0.26f, h * 0.28f),
                size = Size(w * 0.48f, h * 0.48f)
            )
            drawLine(ink, Offset(w * 0.16f, h * 0.56f), Offset(w * 0.84f, h * 0.56f), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.75f), Offset(w * 0.24f, h * 0.68f), Offset(w * 0.76f, h * 0.68f), strokeWidth = strokeW * 0.8f, cap = StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.55f), Offset(w * 0.34f, h * 0.79f), Offset(w * 0.66f, h * 0.79f), strokeWidth = strokeW * 0.7f, cap = StrokeCap.Round)
        }

        AvatarEmblemStyle.ARCTIC_PRISM -> {
            // Prisma triangolare cristallino
            val tri = Path().apply {
                moveTo(cx, h * 0.20f)
                lineTo(w * 0.80f, h * 0.76f)
                lineTo(w * 0.20f, h * 0.76f)
                close()
            }
            drawPath(tri, color = softInk)
            drawPath(tri, color = ink, style = Stroke(width = strokeW, join = StrokeJoin.Round))
            drawLine(ink.copy(alpha = 0.75f), Offset(cx, h * 0.20f), Offset(cx, h * 0.76f), strokeWidth = strokeW * 0.6f)
        }

        AvatarEmblemStyle.OBSIDIAN_CROWN -> {
            // Corona moderna elegante
            val crown = Path().apply {
                moveTo(w * 0.20f, h * 0.70f)
                lineTo(w * 0.24f, h * 0.34f)
                lineTo(w * 0.40f, h * 0.50f)
                lineTo(cx, h * 0.26f)
                lineTo(w * 0.60f, h * 0.50f)
                lineTo(w * 0.76f, h * 0.34f)
                lineTo(w * 0.80f, h * 0.70f)
                close()
            }
            drawPath(crown, color = softInk)
            drawPath(crown, color = ink, style = Stroke(width = strokeW, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawFourPointStar(cx: Float, cy: Float, radius: Float, color: Color) {
    val inner = radius * 0.30f
    val path = Path().apply {
        moveTo(cx, cy - radius)
        lineTo(cx + inner, cy - inner)
        lineTo(cx + radius, cy)
        lineTo(cx + inner, cy + inner)
        lineTo(cx, cy + radius)
        lineTo(cx - inner, cy + inner)
        lineTo(cx - radius, cy)
        lineTo(cx - inner, cy - inner)
        close()
    }
    drawPath(path, color = color)
}
