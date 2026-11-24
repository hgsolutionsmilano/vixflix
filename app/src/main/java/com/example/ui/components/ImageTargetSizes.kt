package com.example.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import kotlin.math.roundToInt

/**
 * Dimensioni target condivise tra prefetcher in background e componenti UI Compose.
 * Mantenere dimensioni identiche garantisce il 100% di hit nella MemoryCache Coil
 * evitando doppie decodifiche bitmap su Fire TV Stick e Android TV entry-level.
 */
object ImageTargetSizes {
    const val POSTER_WIDTH_DP = 130f
    const val POSTER_HEIGHT_DP = 194f

    const val TOP10_POSTER_WIDTH_DP = 124f
    const val TOP10_POSTER_HEIGHT_DP = 190f

    const val CONTINUE_WATCHING_WIDTH_DP = 228f
    const val CONTINUE_WATCHING_HEIGHT_DP = 126f

    const val HERO_WIDTH_DP = 960f
    const val HERO_HEIGHT_TV_DP = 410f
    const val HERO_HEIGHT_MOBILE_DP = 500f

    const val DETAIL_BACKDROP_WIDTH_DP = 960f
    const val DETAIL_BACKDROP_HEIGHT_DP = 360f

    const val CAST_AVATAR_DP = 68f

    const val EPISODE_STILL_WIDTH_DP = 132f
    const val EPISODE_STILL_HEIGHT_DP = 76f

    const val PROVIDER_LOGO_DP = 56f
    const val PROVIDER_CARD_WIDTH_DP = 120f
    const val PROVIDER_CARD_HEIGHT_DP = 84f

    fun dpToPx(dp: Float, density: Float): Int = (dp * density).roundToInt().coerceAtLeast(48)

    fun buildRequest(
        context: Context,
        url: String?,
        widthPx: Int,
        heightPx: Int,
        allowRgb565: Boolean = true,
        memoryCachePolicy: CachePolicy = CachePolicy.ENABLED,
        scale: Scale = Scale.FILL,
        crossfadeMs: Int = 0
    ): ImageRequest {
        return ImageRequest.Builder(context)
            .data(url)
            .size(widthPx.coerceAtLeast(48), heightPx.coerceAtLeast(48))
            .precision(Precision.INEXACT)
            .scale(scale)
            .allowHardware(true)
            .allowRgb565(allowRgb565)
            .memoryCachePolicy(memoryCachePolicy)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .apply {
                if (crossfadeMs > 0) crossfade(crossfadeMs) else crossfade(false)
            }
            .build()
    }
}

@Composable
fun rememberOptimizedImageRequest(
    url: String?,
    widthDp: Dp,
    heightDp: Dp,
    allowRgb565: Boolean = true,
    scale: Scale = Scale.FILL,
    crossfadeMs: Int = 0
): ImageRequest? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.roundToPx().coerceAtLeast(48) }
    val heightPx = with(density) { heightDp.roundToPx().coerceAtLeast(48) }
    return remember(context, url, widthPx, heightPx, allowRgb565, scale, crossfadeMs) {
        if (url.isNullOrBlank()) {
            null
        } else {
            ImageTargetSizes.buildRequest(
                context = context,
                url = url,
                widthPx = widthPx,
                heightPx = heightPx,
                allowRgb565 = allowRgb565,
                scale = scale,
                crossfadeMs = crossfadeMs
            )
        }
    }
}
