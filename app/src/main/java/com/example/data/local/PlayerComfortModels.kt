package com.example.data.local

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat

/**
 * Modalità di adattamento immagine / zoom per il player nativo ExoPlayer.
 * Calibrata per TV 16:9 (Fire TV Stick / Android TV) e dispositivi mobili.
 */
enum class VideoAspectMode(
    val storageKey: String,
    val label: String,
    val description: String
) {
    FIT(
        storageKey = "fit",
        label = "Adatta",
        description = "Mantiene le proporzioni originali del video"
    ),
    ZOOM(
        storageKey = "zoom",
        label = "Riempi",
        description = "Zoom proporzionale per ridurre le bande nere"
    ),
    FILL(
        storageKey = "fill",
        label = "Allarga",
        description = "Adatta il video all'intero schermo"
    );

    @OptIn(UnstableApi::class)
    fun toExoResizeMode(): Int = when (this) {
        FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    companion object {
        fun fromStorageKey(key: String?): VideoAspectMode =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: FIT
    }
}

/**
 * Dimensione dei sottotitoli calibrata per la leggibilità dal divano su Fire TV / Android TV.
 * `fractionalTextSize` viene applicata a Media3 `SubtitleView.setFractionalTextSize(...)`.
 */
enum class SubtitleTextSize(
    val storageKey: String,
    val label: String,
    val fractionalTextSize: Float,
    val previewFontSp: Int
) {
    SMALL(
        storageKey = "small",
        label = "Piccolo",
        fractionalTextSize = 0.045f,
        previewFontSp = 13
    ),
    MEDIUM(
        storageKey = "medium",
        label = "Medio",
        fractionalTextSize = 0.056f,
        previewFontSp = 16
    ),
    LARGE(
        storageKey = "large",
        label = "Grande",
        fractionalTextSize = 0.068f,
        previewFontSp = 19
    ),
    EXTRA_LARGE(
        storageKey = "extra_large",
        label = "Extra",
        fractionalTextSize = 0.082f,
        previewFontSp = 22
    );

    companion object {
        fun fromStorageKey(key: String?): SubtitleTextSize =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: MEDIUM
    }
}

/**
 * Colore principale del testo dei sottotitoli ad alta leggibilità su TV.
 */
enum class SubtitleTextColor(
    val storageKey: String,
    val label: String,
    val argbColor: Int
) {
    WHITE(
        storageKey = "white",
        label = "Bianco",
        argbColor = AndroidColor.WHITE
    ),
    YELLOW(
        storageKey = "yellow",
        label = "Giallo",
        argbColor = 0xFFFFEB3B.toInt()
    ),
    CYAN(
        storageKey = "cyan",
        label = "Ciano",
        argbColor = 0xFF4DD0E1.toInt()
    );

    companion object {
        fun fromStorageKey(key: String?): SubtitleTextColor =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: WHITE
    }
}

/**
 * Stile di sfondo e contrasto dei sottotitoli.
 */
enum class SubtitleBackgroundStyle(
    val storageKey: String,
    val label: String,
    val description: String
) {
    DROP_SHADOW(
        storageKey = "drop_shadow",
        label = "Ombra",
        description = "Testo pulito con ombra cinematografica"
    ),
    DARK_BOX(
        storageKey = "dark_box",
        label = "Sfondo scuro",
        description = "Riquadro semi-trasparente ad alto contrasto"
    ),
    OUTLINE(
        storageKey = "outline",
        label = "Bordo nero",
        description = "Contorno netto ideale su scene luminose"
    );

    companion object {
        fun fromStorageKey(key: String?): SubtitleBackgroundStyle =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: DROP_SHADOW
    }
}

/**
 * Preferenze persistenti Comfort Player (Formato Video + Stile Sottotitoli).
 */
@Immutable
data class PlayerComfortSettings(
    val videoAspectMode: VideoAspectMode = VideoAspectMode.FIT,
    val subtitleTextSize: SubtitleTextSize = SubtitleTextSize.MEDIUM,
    val subtitleTextColor: SubtitleTextColor = SubtitleTextColor.WHITE,
    val subtitleBackgroundStyle: SubtitleBackgroundStyle = SubtitleBackgroundStyle.DROP_SHADOW
) {
    @OptIn(UnstableApi::class)
    fun toCaptionStyleCompat(): CaptionStyleCompat {
        val backgroundColor = when (subtitleBackgroundStyle) {
            SubtitleBackgroundStyle.DARK_BOX -> 0xB8000000.toInt()
            SubtitleBackgroundStyle.DROP_SHADOW,
            SubtitleBackgroundStyle.OUTLINE -> AndroidColor.TRANSPARENT
        }
        val edgeType = when (subtitleBackgroundStyle) {
            SubtitleBackgroundStyle.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            SubtitleBackgroundStyle.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            SubtitleBackgroundStyle.DARK_BOX -> CaptionStyleCompat.EDGE_TYPE_NONE
        }
        val edgeColor = when (subtitleBackgroundStyle) {
            SubtitleBackgroundStyle.DROP_SHADOW,
            SubtitleBackgroundStyle.OUTLINE -> AndroidColor.BLACK
            SubtitleBackgroundStyle.DARK_BOX -> AndroidColor.TRANSPARENT
        }
        return CaptionStyleCompat(
            subtitleTextColor.argbColor,
            backgroundColor,
            AndroidColor.TRANSPARENT,
            edgeType,
            edgeColor,
            Typeface.DEFAULT_BOLD
        )
    }
}
