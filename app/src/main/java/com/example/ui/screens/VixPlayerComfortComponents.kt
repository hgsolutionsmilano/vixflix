package com.example.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.local.PlayerComfortSettings
import com.example.data.local.SubtitleBackgroundStyle
import com.example.data.local.SubtitleTextColor
import com.example.data.local.SubtitleTextSize
import com.example.data.local.VideoAspectMode
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

/**
 * Applica in modo idempotente formato immagine/zoom e stile sottotitoli (dimensione, colore, sfondo)
 * alla vista nativa Media3 `PlayerView` e al relativo `SubtitleView`.
 */
@OptIn(UnstableApi::class)
internal fun applyComfortSettingsToPlayerView(
    playerView: PlayerView,
    settings: PlayerComfortSettings
) {
    val targetResizeMode = settings.videoAspectMode.toExoResizeMode()
    if (playerView.resizeMode != targetResizeMode) {
        playerView.resizeMode = targetResizeMode
    }

    playerView.subtitleView?.let { subtitleView ->
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setStyle(settings.toCaptionStyleCompat())
        subtitleView.setFractionalTextSize(settings.subtitleTextSize.fractionalTextSize)
        subtitleView.setBottomPaddingFraction(0.08f)
    }
}

/**
 * Pannello Comfort Player ottimizzato per telecomando Fire TV Stick / Android TV:
 * 1) FORMATO VIDEO (Adatta / Riempi / Allarga)
 * 2) STILE SOTTOTITOLI (Dimensione / Colore / Contrasto) con anteprima live immediata.
 *
 * Navigazione D-Pad deterministica:
 * - Sinistra / Destra: scorre le opzioni della riga corrente.
 * - Su / Giù: passa tra le 4 righe di opzioni preservando la colonna.
 * - Indietro / Escape: chiude il pannello ripristinando il focus sul pulsante Comfort.
 */
@Composable
internal fun PlayerComfortPanel(
    settings: PlayerComfortSettings,
    onSelectAspectMode: (VideoAspectMode) -> Unit,
    onSelectSubtitleSize: (SubtitleTextSize) -> Unit,
    onSelectSubtitleColor: (SubtitleTextColor) -> Unit,
    onSelectSubtitleBackground: (SubtitleBackgroundStyle) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aspectOptions = remember { VideoAspectMode.entries }
    val sizeOptions = remember { SubtitleTextSize.entries }
    val colorOptions = remember { SubtitleTextColor.entries }
    val backgroundOptions = remember { SubtitleBackgroundStyle.entries }

    val aspectRequesters = remember { List(aspectOptions.size) { FocusRequester() } }
    val sizeRequesters = remember { List(sizeOptions.size) { FocusRequester() } }
    val colorRequesters = remember { List(colorOptions.size) { FocusRequester() } }
    val backgroundRequesters = remember { List(backgroundOptions.size) { FocusRequester() } }

    val rows = remember {
        listOf(aspectRequesters, sizeRequesters, colorRequesters, backgroundRequesters)
    }
    val initialAspectIdx = remember {
        aspectOptions.indexOf(settings.videoAspectMode).coerceAtLeast(0)
    }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        delay(50L)
        aspectRequesters.getOrNull(initialAspectIdx)?.safeRequestFocus()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.5.dp, NetflixWhite.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .heightIn(max = 490.dp)
            .testTag("player_comfort_panel")
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // SEZIONE 1: FORMATO VIDEO / ZOOM
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_comfort_aspect_section")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_comfort_aspect_section).uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = NetflixWhite
                    )
                    Text(
                        text = "• ${settings.videoAspectMode.description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                ComfortChoicePillRow(
                    rowIndex = 0,
                    rows = rows,
                    itemCount = aspectOptions.size,
                    onDismiss = onDismiss
                ) { colIdx, requester, leftReq, rightReq, upReq, downReq ->
                    val mode = aspectOptions[colIdx]
                    ComfortOptionPill(
                        label = mode.label,
                        isSelected = settings.videoAspectMode == mode,
                        swatchColor = null,
                        testTag = "player_aspect_option_${mode.storageKey}",
                        focusRequester = requester,
                        leftRequester = leftReq,
                        rightRequester = rightReq,
                        upRequester = upReq,
                        downRequester = downReq,
                        onClick = { onSelectAspectMode(mode) },
                        onDismiss = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(
                color = NetflixElevatedSurface.copy(alpha = 0.9f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 1.dp)
            )

            // SEZIONE 2: STILE SOTTOTITOLI
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_comfort_subtitles_section")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_comfort_subtitles_section).uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = NetflixWhite
                    )
                }

                // Anteprima compatta immediata per verificare la leggibilità anche su scene senza dialogo
                SubtitleStylePreviewBox(
                    settings = settings,
                    compact = true,
                    modifier = Modifier.testTag("player_comfort_subtitle_preview")
                )

                // Dimensione testo
                ComfortSubLabel(text = stringResource(R.string.player_subtitle_size_label))
                ComfortChoicePillRow(
                    rowIndex = 1,
                    rows = rows,
                    itemCount = sizeOptions.size,
                    onDismiss = onDismiss
                ) { colIdx, requester, leftReq, rightReq, upReq, downReq ->
                    val size = sizeOptions[colIdx]
                    ComfortOptionPill(
                        label = size.label,
                        isSelected = settings.subtitleTextSize == size,
                        swatchColor = null,
                        testTag = "player_sub_size_option_${size.storageKey}",
                        focusRequester = requester,
                        leftRequester = leftReq,
                        rightRequester = rightReq,
                        upRequester = upReq,
                        downRequester = downReq,
                        onClick = { onSelectSubtitleSize(size) },
                        onDismiss = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Colore testo
                ComfortSubLabel(text = stringResource(R.string.player_subtitle_color_label))
                ComfortChoicePillRow(
                    rowIndex = 2,
                    rows = rows,
                    itemCount = colorOptions.size,
                    onDismiss = onDismiss
                ) { colIdx, requester, leftReq, rightReq, upReq, downReq ->
                    val colorOption = colorOptions[colIdx]
                    ComfortOptionPill(
                        label = colorOption.label,
                        isSelected = settings.subtitleTextColor == colorOption,
                        swatchColor = Color(colorOption.argbColor),
                        testTag = "player_sub_color_option_${colorOption.storageKey}",
                        focusRequester = requester,
                        leftRequester = leftReq,
                        rightRequester = rightReq,
                        upRequester = upReq,
                        downRequester = downReq,
                        onClick = { onSelectSubtitleColor(colorOption) },
                        onDismiss = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Stile sfondo / contrasto
                ComfortSubLabel(text = stringResource(R.string.player_subtitle_background_label))
                ComfortChoicePillRow(
                    rowIndex = 3,
                    rows = rows,
                    itemCount = backgroundOptions.size,
                    onDismiss = onDismiss
                ) { colIdx, requester, leftReq, rightReq, upReq, downReq ->
                    val bgOption = backgroundOptions[colIdx]
                    ComfortOptionPill(
                        label = bgOption.label,
                        isSelected = settings.subtitleBackgroundStyle == bgOption,
                        swatchColor = null,
                        testTag = "player_sub_bg_option_${bgOption.storageKey}",
                        focusRequester = requester,
                        leftRequester = leftReq,
                        rightRequester = rightReq,
                        upRequester = upReq,
                        downRequester = downReq,
                        onClick = { onSelectSubtitleBackground(bgOption) },
                        onDismiss = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComfortSubLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        ),
        color = NetflixLightGray
    )
}

@Composable
private fun ComfortChoicePillRow(
    rowIndex: Int,
    rows: List<List<FocusRequester>>,
    itemCount: Int,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.(
        colIdx: Int,
        requester: FocusRequester,
        leftReq: FocusRequester?,
        rightReq: FocusRequester?,
        upReq: FocusRequester?,
        downReq: FocusRequester?
    ) -> Unit
) {
    val currentRow = rows[rowIndex]
    val upperRow = rows.getOrNull(rowIndex - 1)
    val lowerRow = rows.getOrNull(rowIndex + 1)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        for (colIdx in 0 until itemCount) {
            val requester = currentRow[colIdx]
            val leftReq = currentRow.getOrNull(colIdx - 1)
            val rightReq = currentRow.getOrNull(colIdx + 1)
            val upReq = upperRow?.getOrNull(colIdx.coerceAtMost(upperRow.lastIndex))
            val downReq = lowerRow?.getOrNull(colIdx.coerceAtMost(lowerRow.lastIndex))
            content(colIdx, requester, leftReq, rightReq, upReq, downReq)
        }
    }
}

@Composable
private fun ComfortOptionPill(
    label: String,
    isSelected: Boolean,
    swatchColor: Color?,
    testTag: String,
    focusRequester: FocusRequester,
    leftRequester: FocusRequester?,
    rightRequester: FocusRequester?,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) NetflixRed.copy(alpha = 0.24f) else NetflixDarkSurface.copy(alpha = 0.84f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) NetflixRed else NetflixElevatedSurface
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .safeFocusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> leftRequester?.safeRequestFocus() ?: true
                        Key.DirectionRight -> rightRequester?.safeRequestFocus() ?: true
                        Key.DirectionUp -> upRequester?.safeRequestFocus() ?: true
                        Key.DirectionDown -> downRequester?.safeRequestFocus() ?: true
                        Key.Back, Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusProperties {
                leftRequester.takeIfAttached()?.let { left = it }
                rightRequester.takeIfAttached()?.let { right = it }
                upRequester.takeIfAttached()?.let { up = it }
                downRequester.takeIfAttached()?.let { down = it }
            }
            .tvFocusableControl(
                shape = RoundedCornerShape(10.dp),
                focusedBorderColor = NetflixWhite,
                focusedScale = 1.04f,
                focusTag = testTag,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (swatchColor != null) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                )
            } else if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) NetflixWhite else NetflixLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Feedback compatto in alto a destra mostrato per ~2 secondi al cambio di formato immagine nel player.
 */
@Composable
internal fun VideoAspectToastBanner(
    aspectMode: VideoAspectMode,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NetflixBlack.copy(alpha = 0.84f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.24f)),
        shadowElevation = 10.dp,
        modifier = modifier.testTag("player_aspect_toast_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AspectRatio,
                contentDescription = null,
                tint = NetflixRed,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.player_aspect_toast_format, aspectMode.label),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = NetflixWhite
            )
        }
    }
}

/**
 * Anteprima visiva live dei sottotitoli su scena scura cinematografica.
 * Usata sia nella scheda Impostazioni sia in versione compatta dentro `PlayerComfortPanel`.
 */
@Composable
internal fun SubtitleStylePreviewBox(
    settings: PlayerComfortSettings,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val textColor = Color(settings.subtitleTextColor.argbColor)
    val fontSize = (if (compact) settings.subtitleTextSize.previewFontSp - 1 else settings.subtitleTextSize.previewFontSp).sp
    val textShadow = when (settings.subtitleBackgroundStyle) {
        SubtitleBackgroundStyle.DROP_SHADOW -> Shadow(
            color = Color.Black.copy(alpha = 0.95f),
            offset = Offset(2f, 2f),
            blurRadius = 6f
        )
        SubtitleBackgroundStyle.OUTLINE -> Shadow(
            color = Color.Black,
            offset = Offset(0f, 0f),
            blurRadius = 4f
        )
        SubtitleBackgroundStyle.DARK_BOX -> null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1F2430),
                        Color(0xFF11141C),
                        Color(0xFF0A0B0F)
                    )
                )
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Sottile etichetta "ANTEPRIMA" in alto a sinistra
        Text(
            text = stringResource(R.string.player_subtitle_preview_badge),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            color = NetflixMutedGray.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        val pillModifier = when (settings.subtitleBackgroundStyle) {
            SubtitleBackgroundStyle.DARK_BOX -> Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.76f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
            SubtitleBackgroundStyle.OUTLINE -> Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.34f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
            SubtitleBackgroundStyle.DROP_SHADOW -> Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
        }

        Box(modifier = pillModifier) {
            Text(
                text = stringResource(R.string.player_subtitle_preview_sample),
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = textShadow
                )
            )
        }
    }
}
