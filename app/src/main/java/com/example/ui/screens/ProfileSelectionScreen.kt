package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.local.UserProfileEntity
import com.example.data.local.UserProfileUiState
import com.example.ui.components.DpadFocusObserverRegistry
import com.example.ui.components.DpadFocusScope
import com.example.ui.components.ProfileAvatarArtwork
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.BebasNeueFontFamily
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite

/**
 * Schermata cinematografica "Chi sta guardando?" / "Gestisci profili".
 * Completamente navigabile con D-Pad e telecomando Android TV (frecce, OK, BACK),
 * responsiva su smartphone, tablet e TV.
 */
@Composable
fun ProfileSelectionScreen(
    state: UserProfileUiState,
    isTvLayout: Boolean,
    onSelectProfile: (String) -> Unit,
    onToggleManageMode: (Boolean) -> Unit,
    onCreateProfile: (
        name: String,
        avatarId: String,
        accentColorHex: Long,
        preferredLanguage: String,
        preferredAudioLanguage: String,
        subtitlesEnabled: Boolean,
        preferredSubtitleLanguage: String,
        onDone: (Boolean) -> Unit
    ) -> Unit,
    onUpdateProfile: (UserProfileEntity) -> Unit,
    onDeleteProfile: (String, onDone: (Boolean) -> Unit) -> Unit,
    onSetAutoLogin: (Boolean) -> Unit,
    onSetDefaultProfile: (String?) -> Unit,
    onClearError: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateEditor by remember { mutableStateOf(false) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var lastFocusedTileIndex by remember { mutableIntStateOf(0) }

    val editingProfile = remember(editingProfileId, state.profiles) {
        editingProfileId?.let { id -> state.profiles.firstOrNull { it.id == id } }
    }

    val isEditorOpen = showCreateEditor || editingProfile != null
    val totalTiles = state.profiles.size + if (state.canCreateMoreProfiles) 1 else 0
    val tileRequesters = remember(totalTiles) { List(totalTiles.coerceAtLeast(1)) { FocusRequester() } }
    val manageActionFocusRequester = remember { FocusRequester() }
    val autoLoginFocusRequester = remember { FocusRequester() }
    val closeActionFocusRequester = remember { FocusRequester() }

    val activeIdx = remember(state.profiles, state.activeProfile?.id) {
        val idx = state.profiles.indexOfFirst { it.id == state.activeProfile?.id }
        if (idx >= 0) idx else 0
    }

    // Focus iniziale deterministico sul profilo attivo o ripristino alla chiusura dell'editor
    LaunchedEffect(isEditorOpen, state.isManageMode, totalTiles) {
        if (!isEditorOpen) {
            val targetIdx = if (lastFocusedTileIndex in 0 until totalTiles) {
                lastFocusedTileIndex
            } else {
                activeIdx.coerceIn(0, (totalTiles - 1).coerceAtLeast(0))
            }
            val targetRequester = tileRequesters.getOrNull(targetIdx) ?: manageActionFocusRequester
            for (attempt in 1..6) {
                withFrameNanos { }
                if (targetRequester.safeRequestFocus()) break
            }
        }
    }

    BackHandler(enabled = !isEditorOpen) {
        when {
            state.isManageMode -> onToggleManageMode(false)
            !state.mustSelectBeforeClosing -> onClose()
        }
    }

    val activeAccentColor = remember(state.activeProfile?.accentColorHex) {
        Color(state.activeProfile?.accentColorHex ?: 0xFFE50914L)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(90f)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        activeAccentColor.copy(alpha = 0.16f),
                        NetflixBlack,
                        Color(0xFF070709)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("profile_selection_screen")
    ) {
        val columns = when {
            maxWidth >= 1040.dp -> 4
            maxWidth >= 700.dp -> 4
            maxWidth >= 520.dp -> 3
            else -> 2
        }
        val avatarSize: Dp = when {
            maxWidth >= 1040.dp -> 142.dp
            maxWidth >= 700.dp -> 124.dp
            else -> 108.dp
        }
        val tileGap: Dp = if (isTvLayout) 28.dp else 18.dp
        val scrollState = rememberScrollState()

        val rowsCount = ((totalTiles + columns - 1) / columns).coerceAtLeast(1)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = if (isTvLayout) 48.dp else 20.dp, vertical = 24.dp)
        ) {
            // Brand header discreto
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "VIXFLIX",
                    fontFamily = BebasNeueFontFamily,
                    fontSize = if (isTvLayout) 30.sp else 26.sp,
                    letterSpacing = 2.5.sp,
                    color = NetflixRed
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = NetflixCardSurface,
                    border = BorderStroke(1.dp, NetflixElevatedSurface)
                ) {
                    Text(
                        text = "${state.profiles.size} / ${state.maxProfiles} profili",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("profile_count_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isTvLayout) 22.dp else 16.dp))

            // Titolo principale animato
            AnimatedContent(
                targetState = state.isManageMode,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
                label = "profileScreenTitle"
            ) { manageMode ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (manageMode) "Gestisci profili" else "Chi sta guardando?",
                        style = if (isTvLayout) {
                            MaterialTheme.typography.displaySmall
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = FontWeight.Bold,
                        color = NetflixWhite,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("profile_screen_title")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (manageMode) {
                            "Seleziona un profilo per personalizzarlo o eliminarlo"
                        } else {
                            "Ogni profilo mantiene preferiti, Continua a guardare e preferenze indipendenti"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = NetflixLightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (!state.errorMessage.isNullOrBlank() && !isEditorOpen) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NetflixRed.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, NetflixRed),
                    modifier = Modifier.testTag("profile_selector_error_banner")
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixWhite,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isTvLayout) 34.dp else 24.dp))

            // Griglia profili + "+ Aggiungi profilo" con navigazione D-Pad bidimensionale esplicita
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isTvLayout) 26.dp else 18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .testTag("profiles_grid")
            ) {
                for (rowIdx in 0 until rowsCount) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(tileGap, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val startIdx = rowIdx * columns
                        val endIdx = (startIdx + columns).coerceAtMost(totalTiles)
                        for (tileIdx in startIdx until endIdx) {
                            val colIdx = tileIdx - startIdx
                            val leftRequester = if (colIdx > 0) tileRequesters.getOrNull(tileIdx - 1) else null
                            val rightRequester = if (tileIdx + 1 < endIdx) tileRequesters.getOrNull(tileIdx + 1) else null
                            val upRequester = if (rowIdx > 0) {
                                val targetAbove = ((rowIdx - 1) * columns + colIdx).coerceAtMost(totalTiles - 1)
                                tileRequesters.getOrNull(targetAbove)
                            } else {
                                null
                            }
                            val downRequester = if (rowIdx + 1 < rowsCount) {
                                val nextRowStart = (rowIdx + 1) * columns
                                val nextRowEnd = (nextRowStart + columns).coerceAtMost(totalTiles)
                                val targetBelow = (nextRowStart + colIdx).coerceAtMost(nextRowEnd - 1)
                                tileRequesters.getOrNull(targetBelow)
                            } else {
                                manageActionFocusRequester
                            }

                            if (tileIdx < state.profiles.size) {
                                val profile = state.profiles[tileIdx]
                                val isActive = profile.id == state.activeProfile?.id
                                val isDefault = profile.id == state.defaultProfileId
                                ProfileAvatarTile(
                                    profile = profile,
                                    avatarSize = avatarSize,
                                    isActive = isActive,
                                    isDefault = isDefault,
                                    isManageMode = state.isManageMode,
                                    focusRequester = tileRequesters[tileIdx],
                                    leftRequester = leftRequester,
                                    rightRequester = rightRequester,
                                    upRequester = upRequester,
                                    downRequester = downRequester,
                                    onFocused = { lastFocusedTileIndex = tileIdx },
                                    onClick = {
                                        lastFocusedTileIndex = tileIdx
                                        onClearError()
                                        if (state.isManageMode) {
                                            editingProfileId = profile.id
                                        } else {
                                            onSelectProfile(profile.id)
                                        }
                                    }
                                )
                            } else {
                                AddProfileTile(
                                    avatarSize = avatarSize,
                                    focusRequester = tileRequesters[tileIdx],
                                    leftRequester = leftRequester,
                                    rightRequester = rightRequester,
                                    upRequester = upRequester,
                                    downRequester = downRequester,
                                    onFocused = { lastFocusedTileIndex = tileIdx },
                                    onClick = {
                                        lastFocusedTileIndex = tileIdx
                                        onClearError()
                                        showCreateEditor = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!state.canCreateMoreProfiles) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Limite massimo di ${state.maxProfiles} profili raggiunto",
                    style = MaterialTheme.typography.labelMedium,
                    color = NetflixMutedGray,
                    modifier = Modifier.testTag("profile_limit_reached_text")
                )
            }

            Spacer(modifier = Modifier.height(if (isTvLayout) 36.dp else 28.dp))

            // Barra pulsanti inferiore (Gestisci profili / Fatto, Accesso automatico, Chiudi)
            val upToGridRequester = tileRequesters.getOrNull(
                lastFocusedTileIndex.coerceIn(0, (totalTiles - 1).coerceAtLeast(0))
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                if (state.isManageMode) {
                    Button(
                        onClick = { onToggleManageMode(false) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixWhite,
                            contentColor = NetflixBlack
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(manageActionFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                    upToGridRequester?.safeRequestFocus() ?: false
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                upToGridRequester.takeIfAttached()?.let { up = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.05f,
                                focusedBorderColor = NetflixRed,
                                focusTag = "profile_manage_done_button"
                            )
                            .testTag("profile_manage_done_button")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fatto", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val showCloseBtn = !state.mustSelectBeforeClosing

                    OutlinedButton(
                        onClick = { onToggleManageMode(true) },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, NetflixLightGray.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(manageActionFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> upToGridRequester?.safeRequestFocus() ?: false
                                        Key.DirectionRight -> autoLoginFocusRequester.safeRequestFocus()
                                        Key.DirectionLeft -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                upToGridRequester.takeIfAttached()?.let { up = it }
                                autoLoginFocusRequester.takeIfAttached()?.let { right = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.05f,
                                focusedBorderColor = NetflixWhite,
                                focusTag = "profile_manage_button"
                            )
                            .testTag("profile_manage_button")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gestisci profili")
                    }

                    // Toggle accesso automatico rapido
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (state.autoLoginEnabled) NetflixMatchGreen.copy(alpha = 0.2f) else NetflixCardSurface,
                        border = BorderStroke(
                            1.2.dp,
                            if (state.autoLoginEnabled) NetflixMatchGreen else NetflixElevatedSurface
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(autoLoginFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> upToGridRequester?.safeRequestFocus() ?: false
                                        Key.DirectionLeft -> manageActionFocusRequester.safeRequestFocus()
                                        Key.DirectionRight -> if (showCloseBtn) closeActionFocusRequester.safeRequestFocus() else true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusProperties {
                                upToGridRequester.takeIfAttached()?.let { up = it }
                                manageActionFocusRequester.takeIfAttached()?.let { left = it }
                                if (showCloseBtn) {
                                    closeActionFocusRequester.takeIfAttached()?.let { right = it }
                                }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.04f,
                                focusedBorderColor = NetflixWhite,
                                focusTag = "profile_autologin_toggle",
                                onClick = { onSetAutoLogin(!state.autoLoginEnabled) }
                            )
                            .testTag("profile_autologin_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (state.autoLoginEnabled) NetflixMatchGreen else NetflixElevatedSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.autoLoginEnabled) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = NetflixBlack,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (state.autoLoginEnabled) "Accesso automatico attivo" else "Ricorda all'avvio",
                                style = MaterialTheme.typography.labelLarge,
                                color = NetflixWhite
                            )
                        }
                    }

                    if (showCloseBtn) {
                        OutlinedButton(
                            onClick = onClose,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NetflixElevatedSurface),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixLightGray),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .safeFocusRequester(closeActionFocusRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionUp -> upToGridRequester?.safeRequestFocus() ?: false
                                            Key.DirectionLeft -> autoLoginFocusRequester.safeRequestFocus()
                                            Key.DirectionRight -> true
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusProperties {
                                    upToGridRequester.takeIfAttached()?.let { up = it }
                                    autoLoginFocusRequester.takeIfAttached()?.let { left = it }
                                }
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedScale = 1.05f,
                                    focusedBorderColor = NetflixWhite,
                                    focusTag = "profile_selector_close_button"
                                )
                                .testTag("profile_selector_close_button")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chiudi")
                        }
                    }
                }
            }
        }

        // Modale creazione o modifica profilo
        if (isEditorOpen) {
            ProfileEditorDialog(
                editingProfile = editingProfile,
                activeProfileId = state.activeProfile?.id,
                defaultProfileId = state.defaultProfileId,
                isTvLayout = isTvLayout,
                errorMessage = state.errorMessage,
                onCreateProfile = { name, avatarId, accentHex, lang, audio, subs, subLang ->
                    onCreateProfile(name, avatarId, accentHex, lang, audio, subs, subLang) { success ->
                        if (success) {
                            showCreateEditor = false
                        }
                    }
                },
                onAutoSaveProfile = onUpdateProfile,
                onSetDefaultProfile = onSetDefaultProfile,
                onDeleteProfile = { profileId ->
                    onDeleteProfile(profileId) { success ->
                        if (success) {
                            editingProfileId = null
                        }
                    }
                },
                onSwitchBeforeDelete = {
                    editingProfileId = null
                    onToggleManageMode(false)
                },
                onDismiss = {
                    showCreateEditor = false
                    editingProfileId = null
                    onClearError()
                }
            )
        }
    }
}

@Composable
private fun ProfileAvatarTile(
    profile: UserProfileEntity,
    avatarSize: Dp,
    isActive: Boolean,
    isDefault: Boolean,
    isManageMode: Boolean,
    focusRequester: FocusRequester,
    leftRequester: FocusRequester?,
    rightRequester: FocusRequester?,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isFocused -> 1.09f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "profileTileScale_${profile.id}"
    )
    val accentColor = remember(profile.accentColorHex) { Color(profile.accentColorHex) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(avatarSize + 18.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .safeFocusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) isPressed = false
                if (isFocused != focusState.isFocused) {
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) onFocused()
                    DpadFocusObserverRegistry.onNodeFocusChanged(
                        scope = DpadFocusScope.PROFILE_SELECTOR,
                        tag = "profile_tile_${profile.id}",
                        isFocused = focusState.isFocused
                    )
                }
            }
            .focusProperties {
                leftRequester.takeIfAttached()?.let { left = it }
                rightRequester.takeIfAttached()?.let { right = it }
                upRequester.takeIfAttached()?.let { up = it }
                downRequester.takeIfAttached()?.let { down = it }
            }
            .onPreviewKeyEvent { keyEvent ->
                val isCenter = keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> when {
                        isCenter -> {
                            isPressed = true
                            true
                        }
                        keyEvent.key == Key.DirectionLeft -> leftRequester?.safeRequestFocus() ?: true
                        keyEvent.key == Key.DirectionRight -> rightRequester?.safeRequestFocus() ?: true
                        keyEvent.key == Key.DirectionUp -> upRequester?.safeRequestFocus() ?: true
                        keyEvent.key == Key.DirectionDown -> downRequester?.safeRequestFocus() ?: true
                        else -> false
                    }
                    KeyEventType.KeyUp -> {
                        if (isCenter) {
                            isPressed = false
                            onClick()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable { onClick() }
            .testTag("profile_tile_${profile.id}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(avatarSize + 10.dp)
                .clip(RoundedCornerShape(22.dp))
                .then(
                    when {
                        isFocused -> Modifier.border(3.5.dp, NetflixWhite, RoundedCornerShape(22.dp))
                        isActive -> Modifier.border(2.5.dp, accentColor, RoundedCornerShape(22.dp))
                        else -> Modifier.border(1.5.dp, NetflixElevatedSurface, RoundedCornerShape(22.dp))
                    }
                )
                .padding(5.dp)
        ) {
            ProfileAvatarArtwork(
                avatarId = profile.avatarId,
                accentColorHex = profile.accentColorHex,
                size = avatarSize,
                cornerRadius = 17.dp,
                showEditOverlay = isManageMode,
                profileName = profile.name
            )

            // Indicatore colore / attivo in basso a destra
            if (isActive && !isManageMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .border(2.dp, NetflixWhite, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Profilo attivo",
                        tint = NetflixWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Badge profilo predefinito / principale in alto a sinistra
            if (profile.isPrimary || isDefault) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(NetflixBlack.copy(alpha = 0.72f))
                        .border(1.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = if (profile.isPrimary) "Profilo principale" else "Profilo predefinito",
                        tint = if (isDefault) NetflixMatchGreen else accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isFocused || isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isFocused || isActive) NetflixWhite else NetflixLightGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = when {
                    isManageMode -> "Modifica"
                    isActive -> "Attivo"
                    isDefault -> "Predefinito"
                    else -> profile.preferredLanguage.uppercase()
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) accentColor else NetflixMutedGray
            )
        }
    }
}

@Composable
private fun AddProfileTile(
    avatarSize: Dp,
    focusRequester: FocusRequester,
    leftRequester: FocusRequester?,
    rightRequester: FocusRequester?,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isFocused -> 1.08f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "addProfileTileScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(avatarSize + 18.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .safeFocusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) isPressed = false
                if (isFocused != focusState.isFocused) {
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) onFocused()
                    DpadFocusObserverRegistry.onNodeFocusChanged(
                        scope = DpadFocusScope.PROFILE_SELECTOR,
                        tag = "add_profile_tile",
                        isFocused = focusState.isFocused
                    )
                }
            }
            .focusProperties {
                leftRequester.takeIfAttached()?.let { left = it }
                rightRequester.takeIfAttached()?.let { right = it }
                upRequester.takeIfAttached()?.let { up = it }
                downRequester.takeIfAttached()?.let { down = it }
            }
            .onPreviewKeyEvent { keyEvent ->
                val isCenter = keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> when {
                        isCenter -> {
                            isPressed = true
                            true
                        }
                        keyEvent.key == Key.DirectionLeft -> leftRequester?.safeRequestFocus() ?: true
                        keyEvent.key == Key.DirectionRight -> rightRequester?.safeRequestFocus() ?: true
                        keyEvent.key == Key.DirectionUp -> upRequester?.safeRequestFocus() ?: true
                        keyEvent.key == Key.DirectionDown -> downRequester?.safeRequestFocus() ?: true
                        else -> false
                    }
                    KeyEventType.KeyUp -> {
                        if (isCenter) {
                            isPressed = false
                            onClick()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable { onClick() }
            .testTag("add_profile_tile")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(avatarSize + 10.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (isFocused) NetflixElevatedSurface else NetflixDarkSurface)
                .border(
                    width = if (isFocused) 3.dp else 1.5.dp,
                    color = if (isFocused) NetflixWhite else NetflixElevatedSurface,
                    shape = RoundedCornerShape(22.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize * 0.44f)
                    .clip(CircleShape)
                    .background(if (isFocused) NetflixRed else NetflixCardSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Aggiungi profilo",
                    tint = NetflixWhite,
                    modifier = Modifier.size(avatarSize * 0.28f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "+ Aggiungi profilo",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            color = if (isFocused) NetflixWhite else NetflixLightGray,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
