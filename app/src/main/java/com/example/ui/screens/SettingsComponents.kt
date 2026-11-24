package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.StreamingBufferPreset
import com.example.ui.components.DpadFocusScope
import com.example.ui.components.DpadFocusScopeProvider
import com.example.ui.components.consumeBackAndEscapeKey
import com.example.ui.components.recoverFocusFromCandidates
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

/**
 * Intestazione leggera di macro-gruppo per separare chiaramente preferenze generali,
 * prestazioni e operazioni sensibili sui dati nel menu Impostazioni.
 */
@Composable
internal fun SettingsGroupHeader(
    title: String,
    isDangerZone: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isDangerZone) NetflixRed else NetflixRed.copy(alpha = 0.72f))
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            ),
            color = if (isDangerZone) NetflixRed.copy(alpha = 0.92f) else NetflixLightGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Card di sezione modulare e pulita per le impostazioni VixFlix.
 * Evita sovrapposizioni tra titolo, sottotitolo e badge su TV 720p/1080p/4K e distingue
 * chiaramente le zone di gestione/eliminazione dati (`isDangerZone = true`).
 */
@Composable
internal fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String? = null,
    badgeColor: Color = NetflixMatchGreen,
    isDangerZone: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = if (isDangerZone) {
        Color(0xFF1A1315)
    } else {
        NetflixCardSurface
    }
    val borderColor = if (isDangerZone) {
        NetflixRed.copy(alpha = 0.34f)
    } else {
        NetflixElevatedSurface.copy(alpha = 0.85f)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isDangerZone) {
                                    NetflixRed.copy(alpha = 0.16f)
                                } else {
                                    NetflixElevatedSurface.copy(alpha = 0.72f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isDangerZone) NetflixRed else NetflixRed.copy(alpha = 0.95f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = NetflixWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!badgeText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            ),
                            color = badgeColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

/**
 * Selettore a opzioni orizzontali Fire TV-first:
 * - Usa `focusedScale = 1.0f` + bordo bianco nitido `2.dp` e sfondo ad alto contrasto per eliminare
 *   qualsiasi sovrapposizione tra pulsanti adiacenti o ritaglio sui bordi della card.
 * - Mostra un indicatore spunta sull'opzione attualmente attiva.
 * - Supporta `downFocusRequester` opzionale per catene D-Pad verticali 100% deterministiche.
 */
@Composable
internal fun SettingsChoiceRow(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    firstOptionFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Boolean)? = null,
    onDownKey: (() -> Boolean)? = null,
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    navRailFocusRequester: FocusRequester?,
    tagPrefix: String
) {
    val optionDedicatedRequesters = remember(options) {
        options.associate { (key, _) -> key to FocusRequester() }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
    ) {
        val resolvedRequesters = options.mapIndexed { idx, (k, _) ->
            if (idx == 0 && firstOptionFocusRequester != null) {
                firstOptionFocusRequester
            } else {
                optionDedicatedRequesters[k] ?: remember { FocusRequester() }
            }
        }
        options.forEachIndexed { index, (key, label) ->
            val selected = key == selectedKey
            val optionRequester = resolvedRequesters[index]
            val prevRequester = resolvedRequesters.getOrNull(index - 1)
            val nextRequester = resolvedRequesters.getOrNull(index + 1)
            var isFocused by remember { mutableStateOf(false) }

            val containerColor by animateColorAsState(
                targetValue = when {
                    selected -> NetflixRed
                    isFocused -> Color(0xFF282B34)
                    else -> NetflixDarkSurface
                },
                animationSpec = tween(140),
                label = "choiceBg"
            )

            Surface(
                color = containerColor,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) NetflixRed else NetflixElevatedSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .safeFocusRequester(optionRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp -> {
                                    if (onUpKey != null || upFocusRequester != null) {
                                        (onUpKey?.invoke() == true) || upFocusRequester.safeRequestFocus()
                                    } else {
                                        false
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (onDownKey != null || downFocusRequester != null) {
                                        (onDownKey?.invoke() == true) || downFocusRequester.safeRequestFocus()
                                    } else {
                                        false
                                    }
                                }
                                Key.DirectionLeft -> {
                                    if (index == 0) {
                                        navRailFocusRequester.safeRequestFocus()
                                    } else {
                                        prevRequester.safeRequestFocus()
                                    }
                                }
                                Key.DirectionRight -> {
                                    if (index == options.lastIndex) {
                                        true
                                    } else {
                                        nextRequester.safeRequestFocus()
                                    }
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusProperties {
                        if (index == 0) {
                            navRailFocusRequester.takeIfAttached()?.let { left = it }
                        } else {
                            prevRequester.takeIfAttached()?.let { left = it }
                        }
                        nextRequester.takeIfAttached()?.let { right = it }
                        upFocusRequester.takeIfAttached()?.let { up = it }
                        downFocusRequester.takeIfAttached()?.let { down = it }
                    }
                    .tvFocusableControl(
                        shape = RoundedCornerShape(10.dp),
                        focusedBorderColor = NetflixWhite,
                        focusedScale = 1.0f,
                        borderWidth = 2.dp,
                        focusTag = "${tagPrefix}_$key",
                        onFocusChange = { focused ->
                            isFocused = focused
                            if (focused) onRememberContentFocus(optionRequester)
                        },
                        onClick = { onSelect(key) }
                    )
                    .testTag("${tagPrefix}_$key")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = NetflixWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Riga impostazione con interruttore ottimizzata per TV:
 * - Stato esplicito `ATTIVO` / `OFF` ben leggibile a distanza.
 * - Zero overflow orizzontale al focus (`focusedScale = 1.0f` + bordo bianco `2.dp` + tinta elevata).
 * - Touch target minimo `58.dp` e chiara gerarchia titolo/descrizione.
 */
@Composable
internal fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Boolean)? = null,
    onDownKey: (() -> Boolean)? = null,
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    navRailFocusRequester: FocusRequester?,
    testTagName: String
) {
    val resolvedFocusRequester = focusRequester ?: remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF242731) else NetflixDarkSurface,
        animationSpec = tween(140),
        label = "switchRowBg"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) NetflixElevatedSurface.copy(alpha = 0.95f) else NetflixElevatedSurface.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .safeFocusRequester(resolvedFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (onUpKey != null || upFocusRequester != null) {
                                (onUpKey?.invoke() == true) || upFocusRequester.safeRequestFocus()
                            } else {
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            if (onDownKey != null || downFocusRequester != null) {
                                (onDownKey?.invoke() == true) || downFocusRequester.safeRequestFocus()
                            } else {
                                false
                            }
                        }
                        Key.DirectionLeft -> navRailFocusRequester.safeRequestFocus()
                        Key.DirectionRight -> true
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusProperties {
                navRailFocusRequester.takeIfAttached()?.let { left = it }
                upFocusRequester.takeIfAttached()?.let { up = it }
                downFocusRequester.takeIfAttached()?.let { down = it }
            }
            .tvFocusableControl(
                shape = RoundedCornerShape(10.dp),
                focusedBorderColor = NetflixWhite,
                focusedScale = 1.0f,
                borderWidth = 2.dp,
                focusTag = testTagName,
                onFocusChange = { focused ->
                    isFocused = focused
                    if (focused) onRememberContentFocus(resolvedFocusRequester)
                },
                onClick = { onCheckedChange(!checked) }
            )
            .testTag(testTagName)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = NetflixWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NetflixLightGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (checked) "ATTIVO" else "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (checked) NetflixMatchGreen else NetflixMutedGray,
                    maxLines = 1,
                    softWrap = false
                )
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NetflixRed,
                        uncheckedThumbColor = NetflixLightGray,
                        uncheckedTrackColor = NetflixElevatedSurface
                    )
                )
            }
        }
    }
}

@Composable
internal fun SettingsStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NetflixDarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NetflixElevatedSurface.copy(alpha = 0.8f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NetflixWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NetflixLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Riga informativa allineata in modo responsive per evitare sovrapposizioni tra etichetta e valore
 * anche su TV 720p con testi lunghi.
 */
@Composable
internal fun SettingsInfoLine(
    label: String,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NetflixLightGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = NetflixWhite,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
internal fun BufferPresetGrid(
    selectedPreset: StreamingBufferPreset,
    onSelectPreset: (StreamingBufferPreset) -> Unit,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    firstPresetFocusRequester: FocusRequester? = null,
    onRememberContentFocus: (FocusRequester) -> Unit = {},
    navRailFocusRequester: FocusRequester?
) {
    val rows = remember { StreamingBufferPreset.entries.chunked(2) }
    val presetRequesters = remember {
        StreamingBufferPreset.entries.associateWith { FocusRequester() }
    }
    fun requesterFor(preset: StreamingBufferPreset): FocusRequester {
        return if (preset == StreamingBufferPreset.entries.first() && firstPresetFocusRequester != null) {
            firstPresetFocusRequester
        } else {
            presetRequesters.getValue(preset)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
    ) {
        rows.forEachIndexed { rowIndex, rowPresets ->
            val upperRow = rows.getOrNull(rowIndex - 1)
            val lowerRow = rows.getOrNull(rowIndex + 1)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowPresets.forEachIndexed { colIndex, preset ->
                    val isSelected = preset == selectedPreset
                    val presetRequester = requesterFor(preset)
                    val prevColRequester = rowPresets.getOrNull(colIndex - 1)?.let { requesterFor(it) }
                    val nextColRequester = rowPresets.getOrNull(colIndex + 1)?.let { requesterFor(it) }
                    val upTarget = upperRow?.getOrNull(colIndex)?.let { requesterFor(it) } ?: upFocusRequester
                    val downTarget = lowerRow?.getOrNull(colIndex)?.let { requesterFor(it) } ?: downFocusRequester
                    var isFocused by remember { mutableStateOf(false) }

                    val bgColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> NetflixRed.copy(alpha = 0.22f)
                            isFocused -> Color(0xFF262933)
                            else -> NetflixDarkSurface
                        },
                        animationSpec = tween(140),
                        label = "bufferBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) NetflixRed else NetflixElevatedSurface,
                        animationSpec = tween(140),
                        label = "bufferBorder"
                    )

                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 68.dp)
                            .safeFocusRequester(presetRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            if (colIndex == 0) {
                                                navRailFocusRequester.safeRequestFocus()
                                            } else {
                                                prevColRequester.safeRequestFocus()
                                            }
                                        }
                                        Key.DirectionRight -> {
                                            if (colIndex == rowPresets.lastIndex) {
                                                true
                                            } else {
                                                nextColRequester.safeRequestFocus()
                                            }
                                        }
                                        Key.DirectionUp -> upTarget?.safeRequestFocus() ?: false
                                        Key.DirectionDown -> downTarget?.safeRequestFocus() ?: false
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                if (colIndex == 0) {
                                    navRailFocusRequester.takeIfAttached()?.let { left = it }
                                } else {
                                    prevColRequester.takeIfAttached()?.let { left = it }
                                }
                                nextColRequester.takeIfAttached()?.let { right = it }
                                upTarget.takeIfAttached()?.let { up = it }
                                downTarget.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(12.dp),
                                focusedBorderColor = NetflixWhite,
                                focusedScale = 1.0f,
                                borderWidth = 2.dp,
                                focusTag = "settings_buffer_${preset.storageKey}",
                                onFocusChange = { focused ->
                                    isFocused = focused
                                    if (focused) onRememberContentFocus(presetRequester)
                                },
                                onClick = { onSelectPreset(preset) }
                            )
                            .testTag("settings_buffer_${preset.storageKey}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NetflixWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isSelected) NetflixRed else NetflixElevatedSurface,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${preset.sizeMb} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.windowHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) NetflixMatchGreen else NetflixLightGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Popup di conferma scollegamento Simkl con focus-trap completo, `DpadFocusScope.MODAL_QUICK_ACTION`,
 * gestione atomica Back/Escape e margini di sicurezza TV 720p/1080p/4K.
 */
@Composable
internal fun SimklUnlinkConfirmDialog(
    profileName: String,
    accountDisplayName: String?,
    onDismiss: () -> Unit,
    onConfirmUnlink: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        for (frame in 1..4) {
            withFrameNanos { }
            if (cancelFocusRequester.safeRequestFocus()) break
        }
        delay(50L)
        cancelFocusRequester.safeRequestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        DpadFocusScopeProvider(
            scope = DpadFocusScope.MODAL_QUICK_ACTION,
            onRecoverFocus = {
                recoverFocusFromCandidates(
                    "simkl_cancel_unlink_button" to cancelFocusRequester,
                    "simkl_confirm_unlink_button" to confirmFocusRequester
                )
            }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .consumeBackAndEscapeKey(onBack = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                    border = BorderStroke(1.5.dp, NetflixElevatedSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp)
                        .testTag("simkl_unlink_confirm_dialog")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 22.dp)
                            .focusGroup()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(NetflixRed.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = NetflixRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "Scollegare account Simkl?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = buildString {
                                append("Stai scollegando Simkl ")
                                if (!accountDisplayName.isNullOrBlank()) {
                                    append("($accountDisplayName) ")
                                }
                                append("dal profilo \"$profileName\". I token di accesso verranno rimossi e revocati, ma i tuoi progressi locali in Continua a guardare resteranno intatti.")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = NetflixLightGray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NetflixElevatedSurface),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(cancelFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionRight -> confirmFocusRequester.safeRequestFocus()
                                                Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .focusProperties {
                                        confirmFocusRequester.takeIfAttached()?.let { right = it }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedScale = 1.0f,
                                        focusedBorderColor = NetflixWhite,
                                        borderWidth = 2.dp,
                                        focusTag = "simkl_cancel_unlink_button"
                                    )
                                    .testTag("simkl_cancel_unlink_button")
                            ) {
                                Text(
                                    text = "ANNULLA",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = onConfirmUnlink,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(confirmFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> cancelFocusRequester.safeRequestFocus()
                                                Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .focusProperties {
                                        cancelFocusRequester.takeIfAttached()?.let { left = it }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedScale = 1.0f,
                                        focusedBorderColor = NetflixWhite,
                                        borderWidth = 2.dp,
                                        focusTag = "simkl_confirm_unlink_button"
                                    )
                                    .testTag("simkl_confirm_unlink_button")
                            ) {
                                Text(
                                    text = "SCOLLEGA",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Popup di conferma svuotamento "Continua a guardare" con focus-trap interno, margini sicuri TV
 * e coerenza visiva con `SimklUnlinkConfirmDialog`.
 */
@Composable
internal fun ClearContinueWatchingConfirmDialog(
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        for (frame in 1..4) {
            withFrameNanos { }
            if (cancelFocusRequester.safeRequestFocus()) break
        }
        delay(50L)
        cancelFocusRequester.safeRequestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        DpadFocusScopeProvider(
            scope = DpadFocusScope.MODAL_QUICK_ACTION,
            onRecoverFocus = {
                recoverFocusFromCandidates(
                    "clear_cw_cancel_button" to cancelFocusRequester,
                    "clear_cw_confirm_button" to confirmFocusRequester
                )
            }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .consumeBackAndEscapeKey(onBack = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                    border = BorderStroke(1.5.dp, NetflixElevatedSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .testTag("clear_continue_watching_confirm_dialog")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 22.dp)
                            .focusGroup()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(NetflixRed.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = NetflixRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "SVUOTARE CONTINUA A GUARDARE?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Vuoi davvero rimuovere tutti i contenuti dalla sezione Continua a guardare? Questa operazione non può essere annullata.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NetflixLightGray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NetflixElevatedSurface),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(cancelFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionRight -> confirmFocusRequester.safeRequestFocus()
                                                Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .focusProperties {
                                        confirmFocusRequester.takeIfAttached()?.let { right = it }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedScale = 1.0f,
                                        focusedBorderColor = NetflixWhite,
                                        borderWidth = 2.dp,
                                        focusTag = "clear_cw_cancel_button"
                                    )
                                    .testTag("clear_cw_cancel_button")
                            ) {
                                Text(
                                    text = "ANNULLA",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = onConfirmClear,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(confirmFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> cancelFocusRequester.safeRequestFocus()
                                                Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .focusProperties {
                                        cancelFocusRequester.takeIfAttached()?.let { left = it }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedScale = 1.0f,
                                        focusedBorderColor = NetflixWhite,
                                        borderWidth = 2.dp,
                                        focusTag = "clear_cw_confirm_button"
                                    )
                                    .testTag("clear_cw_confirm_button")
                            ) {
                                Text(
                                    text = "CONFERMA",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
