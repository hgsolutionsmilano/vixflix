package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.local.INITIAL_AVATAR_ID
import com.example.data.local.ProfileAvatarPreset
import com.example.data.local.ProfileCatalogDefaults
import com.example.data.local.UserProfileEntity
import com.example.ui.components.ProfileAvatarArtwork
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.consumeBackAndEscapeKey
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
import kotlinx.coroutines.launch

/**
 * Dialogo modale cinematografico per creare un nuovo profilo o modificare un profilo esistente.
 * Supporta navigazione completa tramite D-Pad Android TV (frecce direzionali, OK, BACK) senza
 * punti morti o blocchi del focus, e include l'avatar automatico con l'iniziale del nome profilo.
 */
@Composable
fun ProfileEditorDialog(
    editingProfile: UserProfileEntity?,
    activeProfileId: String?,
    defaultProfileId: String?,
    isTvLayout: Boolean,
    errorMessage: String?,
    onCreateProfile: (
        name: String,
        avatarId: String,
        accentColorHex: Long,
        preferredLanguage: String,
        preferredAudioLanguage: String,
        subtitlesEnabled: Boolean,
        preferredSubtitleLanguage: String
    ) -> Unit,
    onAutoSaveProfile: (UserProfileEntity) -> Unit,
    onSetDefaultProfile: (String?) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSwitchBeforeDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isEditMode = editingProfile != null
    val defaultAvatar = remember(editingProfile) {
        editingProfile?.avatarId?.takeIf { it.isNotBlank() } ?: INITIAL_AVATAR_ID
    }
    val defaultAccent = remember(editingProfile) {
        editingProfile?.accentColorHex ?: ProfileCatalogDefaults.accentOptions.first().colorHex
    }

    var name by remember(editingProfile?.id) { mutableStateOf(editingProfile?.name ?: "") }
    var selectedAvatarId by remember(editingProfile?.id) { mutableStateOf(defaultAvatar) }
    var selectedAccentHex by remember(editingProfile?.id) { mutableLongStateOf(defaultAccent) }
    var preferredLanguage by remember(editingProfile?.id) { mutableStateOf(editingProfile?.preferredLanguage ?: "it") }
    var preferredAudio by remember(editingProfile?.id) { mutableStateOf(editingProfile?.preferredAudioLanguage ?: "it") }
    var subtitlesEnabled by remember(editingProfile?.id) { mutableStateOf(editingProfile?.subtitlesEnabled ?: false) }
    var preferredSubLang by remember(editingProfile?.id) { mutableStateOf(editingProfile?.preferredSubtitleLanguage ?: "it") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val avatarOptionIds = remember {
        listOf(INITIAL_AVATAR_ID) + ProfileAvatarPreset.entries.map { it.id }
    }
    val accentOptions = ProfileCatalogDefaults.accentOptions

    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val avatarListState = rememberLazyListState()
    val colorListState = rememberLazyListState()

    // FocusRequester per ogni riga e controllo interattivo del dialogo
    val closeButtonFocusRequester = remember { FocusRequester() }
    val nameFieldFocusRequester = remember { FocusRequester() }
    val avatarFocusRequesters = remember(avatarOptionIds.size) {
        List(avatarOptionIds.size) { FocusRequester() }
    }
    val colorFocusRequesters = remember(accentOptions.size) {
        List(accentOptions.size) { FocusRequester() }
    }
    val langItFocusRequester = remember { FocusRequester() }
    val langEnFocusRequester = remember { FocusRequester() }
    val audioItFocusRequester = remember { FocusRequester() }
    val audioEnFocusRequester = remember { FocusRequester() }
    val audioOriginalFocusRequester = remember { FocusRequester() }
    val subtitlesToggleFocusRequester = remember { FocusRequester() }
    val subLangItFocusRequester = remember { FocusRequester() }
    val subLangEnFocusRequester = remember { FocusRequester() }
    val setDefaultFocusRequester = remember { FocusRequester() }
    val deleteOrSwitchFocusRequester = remember { FocusRequester() }
    val cancelButtonFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }
    val deleteCancelFocusRequester = remember { FocusRequester() }
    val deleteConfirmFocusRequester = remember { FocusRequester() }

    val hasDeleteOrSwitchAction = editingProfile != null && !editingProfile.isPrimary

    val preferredAvatarFocusRequester: () -> FocusRequester = {
        val selectedIdx = avatarOptionIds.indexOf(selectedAvatarId).coerceAtLeast(0)
        avatarFocusRequesters[selectedIdx].takeIfAttached()
            ?: avatarFocusRequesters.firstOrNull { it.takeIfAttached() != null }
            ?: avatarFocusRequesters.first()
    }

    val preferredColorFocusRequester: () -> FocusRequester = {
        val selectedIdx = accentOptions.indexOfFirst { it.colorHex == selectedAccentHex }.coerceAtLeast(0)
        colorFocusRequesters[selectedIdx].takeIfAttached()
            ?: colorFocusRequesters.firstOrNull { it.takeIfAttached() != null }
            ?: colorFocusRequesters.first()
    }

    val preferredLangFocusRequester: () -> FocusRequester = {
        if (preferredLanguage == "en") langEnFocusRequester else langItFocusRequester
    }

    val preferredAudioFocusRequester: () -> FocusRequester = {
        when (preferredAudio) {
            "en" -> audioEnFocusRequester
            "original" -> audioOriginalFocusRequester
            else -> audioItFocusRequester
        }
    }

    val preferredSubLangFocusRequester: () -> FocusRequester = {
        if (preferredSubLang == "en") subLangEnFocusRequester else subLangItFocusRequester
    }

    val focusBelowPreferences: () -> Boolean = {
        coroutineScope.launch {
            runCatching { scrollState.animateScrollTo(scrollState.maxValue) }
        }
        when {
            editingProfile != null -> {
                setDefaultFocusRequester.safeRequestFocus() || primaryActionFocusRequester.safeRequestFocus()
            }
            else -> primaryActionFocusRequester.safeRequestFocus()
        }
    }

    val focusAboveBottomButtons: () -> Boolean = {
        when {
            hasDeleteOrSwitchAction && deleteOrSwitchFocusRequester.safeRequestFocus() -> true
            editingProfile != null && setDefaultFocusRequester.safeRequestFocus() -> true
            subtitlesEnabled && preferredSubLangFocusRequester().safeRequestFocus() -> true
            else -> subtitlesToggleFocusRequester.safeRequestFocus()
        }
    }

    val persistAutoSaveIfEditing: (
        String, String, Long, String, String, Boolean, String
    ) -> Unit = { newName, newAvatar, newAccent, newLang, newAudio, newSubs, newSubLang ->
        val existing = editingProfile
        if (existing != null && newName.trim().isNotEmpty()) {
            onAutoSaveProfile(
                existing.copy(
                    name = newName.trim().take(24),
                    avatarId = newAvatar,
                    accentColorHex = newAccent,
                    preferredLanguage = newLang,
                    preferredAudioLanguage = newAudio,
                    subtitlesEnabled = newSubs,
                    preferredSubtitleLanguage = newSubLang
                )
            )
        }
    }

    val handleSaveOrCreate: () -> Unit = {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            validationError = "Inserisci un nome per il profilo."
            coroutineScope.launch {
                runCatching { scrollState.animateScrollTo(0) }
                nameFieldFocusRequester.safeRequestFocus()
            }
        } else if (editingProfile != null) {
            persistAutoSaveIfEditing(
                trimmed,
                selectedAvatarId,
                selectedAccentHex,
                preferredLanguage,
                preferredAudio,
                subtitlesEnabled,
                preferredSubLang
            )
            onDismiss()
        } else {
            onCreateProfile(
                trimmed,
                selectedAvatarId,
                selectedAccentHex,
                preferredLanguage,
                preferredAudio,
                subtitlesEnabled,
                preferredSubLang
            )
        }
    }

    LaunchedEffect(editingProfile?.id) {
        runCatching { scrollState.scrollTo(0) }
        repeat(4) {
            withFrameNanos { }
            if (nameFieldFocusRequester.safeRequestFocus()) return@LaunchedEffect
        }
    }

    LaunchedEffect(showDeleteConfirm) {
        if (showDeleteConfirm) {
            repeat(4) {
                withFrameNanos { }
                if (deleteCancelFocusRequester.safeRequestFocus()) return@LaunchedEffect
            }
        }
    }

    BackHandler {
        if (showDeleteConfirm) {
            showDeleteConfirm = false
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(95f)
            .consumeBackAndEscapeKey {
                if (showDeleteConfirm) {
                    showDeleteConfirm = false
                } else {
                    onDismiss()
                }
            }
            .background(NetflixBlack.copy(alpha = 0.88f))
            .padding(horizontal = if (isTvLayout) 32.dp else 12.dp, vertical = if (isTvLayout) 4.dp else 8.dp)
            .testTag("profile_editor_dialog"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NetflixDarkSurface,
            border = BorderStroke(1.5.dp, Color(selectedAccentHex).copy(alpha = 0.65f)),
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTvLayout) 20.dp else 16.dp, vertical = if (isTvLayout) 10.dp else 12.dp)
            ) {
                // Header fisso
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditMode) "Personalizza profilo" else "Nuovo profilo",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite
                        )
                        Text(
                            text = if (isEditMode) {
                                "Le modifiche vengono salvate automaticamente"
                            } else {
                                "Scegli nome, avatar, colore e preferenze personali"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .safeFocusRequester(closeButtonFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionDown -> nameFieldFocusRequester.safeRequestFocus()
                                        Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                nameFieldFocusRequester.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = CircleShape,
                                focusTag = "profile_editor_close"
                            )
                            .testTag("profile_editor_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Chiudi",
                            tint = NetflixWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isTvLayout) 4.dp else 8.dp))

                // Corpo centrale scorrevole
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {

                // Anteprima profilo live + Campo nome profilo affiancati per visibilità completa su schermi TV 540dp
                val previewDisplayName = name.trim().ifBlank { "Nuovo spettatore" }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .weight(0.95f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(selectedAccentHex).copy(alpha = 0.24f),
                                        NetflixCardSurface
                                    )
                                )
                            )
                            .border(1.dp, NetflixElevatedSurface, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("profile_editor_live_preview")
                    ) {
                        ProfileAvatarArtwork(
                            avatarId = selectedAvatarId,
                            accentColorHex = selectedAccentHex,
                            size = 52.dp,
                            cornerRadius = 12.dp,
                            profileName = previewDisplayName
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = previewDisplayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetflixWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (editingProfile?.isPrimary == true) {
                                    ProfileInfoPill(
                                        text = "Profilo Principale",
                                        color = NetflixRed
                                    )
                                }
                                if (editingProfile != null && editingProfile.id == defaultProfileId) {
                                    ProfileInfoPill(
                                        text = "Predefinito",
                                        color = NetflixMatchGreen
                                    )
                                }
                                ProfileInfoPill(
                                    text = "Lingua ${preferredLanguage.uppercase()}",
                                    color = Color(selectedAccentHex)
                                )
                            }
                        }
                    }

                    // Campo nome profilo (navigabile con D-Pad Su -> Chiudi, Giù -> Avatar)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { raw ->
                            val capped = raw.take(24)
                            name = capped
                            validationError = null
                            persistAutoSaveIfEditing(
                                capped,
                                selectedAvatarId,
                                selectedAccentHex,
                                preferredLanguage,
                                preferredAudio,
                                subtitlesEnabled,
                                preferredSubLang
                            )
                        },
                        label = { Text("Nome profilo") },
                        placeholder = { Text("Es. Marco, Veronica…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                preferredAvatarFocusRequester().safeRequestFocus()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(selectedAccentHex),
                            unfocusedBorderColor = NetflixElevatedSurface,
                            focusedLabelColor = NetflixWhite,
                            unfocusedLabelColor = NetflixLightGray,
                            focusedTextColor = NetflixWhite,
                            unfocusedTextColor = NetflixWhite,
                            cursorColor = NetflixRed
                        ),
                        modifier = Modifier
                            .weight(1.05f)
                            .safeFocusRequester(nameFieldFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionUp -> {
                                            keyboardController?.hide()
                                            closeButtonFocusRequester.safeRequestFocus()
                                        }
                                        Key.DirectionDown -> {
                                            keyboardController?.hide()
                                            val selectedIdx = avatarOptionIds.indexOf(selectedAvatarId).coerceAtLeast(0)
                                            coroutineScope.launch {
                                                runCatching { avatarListState.scrollToItem(selectedIdx) }
                                            }
                                            preferredAvatarFocusRequester().safeRequestFocus()
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                closeButtonFocusRequester.takeIfAttached()?.let { up = it }
                                preferredAvatarFocusRequester().takeIfAttached()?.let { down = it }
                            }
                            .testTag("profile_name_input")
                    )
                }

                val shownError = validationError ?: errorMessage
                if (!shownError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = shownError,
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixRed,
                        modifier = Modifier.testTag("profile_editor_error_text")
                    )
                }

                Spacer(modifier = Modifier.height(if (isTvLayout) 6.dp else 10.dp))

                // Galleria Avatar: Avatar automatico con Iniziale + 12 Avatar Grafici Personalizzati
                Text(
                    text = "Scegli un avatar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                Spacer(modifier = Modifier.height(if (isTvLayout) 4.dp else 6.dp))
                LazyRow(
                    state = avatarListState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    modifier = Modifier.testTag("profile_avatar_picker_row")
                ) {
                    itemsIndexed(
                        items = avatarOptionIds,
                        key = { _, avatarId -> avatarId }
                    ) { idx, optionAvatarId ->
                        val isChosen = selectedAvatarId == optionAvatarId
                        val itemRequester = avatarFocusRequesters[idx]
                        Box(
                            modifier = Modifier
                                .safeFocusRequester(itemRequester)
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (isChosen) {
                                        Modifier.border(2.5.dp, NetflixWhite, RoundedCornerShape(14.dp))
                                    } else {
                                        Modifier.border(1.dp, NetflixElevatedSurface, RoundedCornerShape(14.dp))
                                    }
                                )
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionUp -> nameFieldFocusRequester.safeRequestFocus()
                                            Key.DirectionDown -> {
                                                val colorIdx = accentOptions.indexOfFirst { it.colorHex == selectedAccentHex }.coerceAtLeast(0)
                                                coroutineScope.launch {
                                                    runCatching { colorListState.scrollToItem(colorIdx) }
                                                }
                                                preferredColorFocusRequester().safeRequestFocus()
                                            }
                                            Key.DirectionLeft -> {
                                                if (idx > 0) {
                                                    val prevIdx = idx - 1
                                                    coroutineScope.launch {
                                                        runCatching { avatarListState.animateScrollToItem(prevIdx) }
                                                        withFrameNanos { }
                                                        avatarFocusRequesters[prevIdx].safeRequestFocus()
                                                    }
                                                }
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                if (idx < avatarOptionIds.lastIndex) {
                                                    val nextIdx = idx + 1
                                                    coroutineScope.launch {
                                                        runCatching { avatarListState.animateScrollToItem(nextIdx) }
                                                        withFrameNanos { }
                                                        avatarFocusRequesters[nextIdx].safeRequestFocus()
                                                    }
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .focusProperties {
                                    nameFieldFocusRequester.takeIfAttached()?.let { up = it }
                                    preferredColorFocusRequester().takeIfAttached()?.let { down = it }
                                }
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(14.dp),
                                    focusedScale = 1.07f,
                                    focusedBorderColor = NetflixWhite,
                                    focusTag = "profile_avatar_option_$optionAvatarId",
                                    onClick = {
                                        selectedAvatarId = optionAvatarId
                                        persistAutoSaveIfEditing(
                                            name,
                                            optionAvatarId,
                                            selectedAccentHex,
                                            preferredLanguage,
                                            preferredAudio,
                                            subtitlesEnabled,
                                            preferredSubLang
                                        )
                                    }
                                )
                                .padding(4.dp)
                                .testTag("avatar_option_$optionAvatarId"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.testTag("profile_avatar_option_$optionAvatarId")) {
                                ProfileAvatarArtwork(
                                    avatarId = optionAvatarId,
                                    accentColorHex = selectedAccentHex,
                                    size = if (isTvLayout) 44.dp else 50.dp,
                                    cornerRadius = 10.dp,
                                    profileName = previewDisplayName
                                )
                            }
                            if (isChosen) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(NetflixMatchGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Avatar selezionato",
                                        tint = NetflixBlack,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isTvLayout) 6.dp else 10.dp))

                // Selettore Colore Identificativo
                Text(
                    text = "Colore identificativo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                Spacer(modifier = Modifier.height(if (isTvLayout) 4.dp else 6.dp))
                LazyRow(
                    state = colorListState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    modifier = Modifier.testTag("profile_color_picker_row")
                ) {
                    itemsIndexed(
                        items = accentOptions,
                        key = { idx, option -> "${idx}_${option.colorHex}" }
                    ) { idx, option ->
                        val isChosen = selectedAccentHex == option.colorHex
                        val colorRequester = colorFocusRequesters[idx]
                        Box(
                            modifier = Modifier
                                .size(if (isTvLayout) 34.dp else 40.dp)
                                .safeFocusRequester(colorRequester)
                                .clip(CircleShape)
                                .background(Color(option.colorHex))
                                .then(
                                    if (isChosen) {
                                        Modifier.border(3.dp, NetflixWhite, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, NetflixWhite.copy(alpha = 0.25f), CircleShape)
                                    }
                                )
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionUp -> preferredAvatarFocusRequester().safeRequestFocus()
                                            Key.DirectionDown -> preferredLangFocusRequester().safeRequestFocus()
                                            Key.DirectionLeft -> {
                                                if (idx > 0) {
                                                    val prevIdx = idx - 1
                                                    coroutineScope.launch {
                                                        runCatching { colorListState.animateScrollToItem(prevIdx) }
                                                        withFrameNanos { }
                                                        colorFocusRequesters[prevIdx].safeRequestFocus()
                                                    }
                                                }
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                if (idx < accentOptions.lastIndex) {
                                                    val nextIdx = idx + 1
                                                    coroutineScope.launch {
                                                        runCatching { colorListState.animateScrollToItem(nextIdx) }
                                                        withFrameNanos { }
                                                        colorFocusRequesters[nextIdx].safeRequestFocus()
                                                    }
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .focusProperties {
                                    preferredAvatarFocusRequester().takeIfAttached()?.let { up = it }
                                    preferredLangFocusRequester().takeIfAttached()?.let { down = it }
                                }
                                .tvFocusableControl(
                                    shape = CircleShape,
                                    focusedScale = 1.12f,
                                    focusedBorderColor = NetflixWhite,
                                    focusTag = "profile_color_option_$idx",
                                    onClick = {
                                        selectedAccentHex = option.colorHex
                                        persistAutoSaveIfEditing(
                                            name,
                                            selectedAvatarId,
                                            option.colorHex,
                                            preferredLanguage,
                                            preferredAudio,
                                            subtitlesEnabled,
                                            preferredSubLang
                                        )
                                    }
                                )
                                .testTag("accent_color_option_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("profile_color_option_$idx"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChosen) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = option.label,
                                        tint = NetflixWhite,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isTvLayout) 6.dp else 10.dp))

                // Preferenze personali (Lingua, Audio, Sottotitoli)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NetflixCardSurface,
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(if (isTvLayout) 6.dp else 8.dp),
                        modifier = Modifier.padding(if (isTvLayout) 10.dp else 12.dp)
                    ) {
                        Text(
                            text = "Preferenze personali",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NetflixWhite
                        )

                        // Lingua preferita
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Lingua preferita",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NetflixLightGray
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProfileChoiceChip(
                                    label = "Italiano",
                                    selected = preferredLanguage == "it",
                                    accentColor = Color(selectedAccentHex),
                                    tag = "profile_lang_it",
                                    focusRequester = langItFocusRequester,
                                    onUp = { preferredColorFocusRequester().safeRequestFocus() },
                                    onDown = { preferredAudioFocusRequester().safeRequestFocus() },
                                    onLeft = { true },
                                    onRight = { langEnFocusRequester.safeRequestFocus() },
                                    onClick = {
                                        preferredLanguage = "it"
                                        persistAutoSaveIfEditing(
                                            name,
                                            selectedAvatarId,
                                            selectedAccentHex,
                                            "it",
                                            preferredAudio,
                                            subtitlesEnabled,
                                            preferredSubLang
                                        )
                                    }
                                )
                                ProfileChoiceChip(
                                    label = "English",
                                    selected = preferredLanguage == "en",
                                    accentColor = Color(selectedAccentHex),
                                    tag = "profile_lang_en",
                                    focusRequester = langEnFocusRequester,
                                    onUp = { preferredColorFocusRequester().safeRequestFocus() },
                                    onDown = { preferredAudioFocusRequester().safeRequestFocus() },
                                    onLeft = { langItFocusRequester.safeRequestFocus() },
                                    onRight = { true },
                                    onClick = {
                                        preferredLanguage = "en"
                                        persistAutoSaveIfEditing(
                                            name,
                                            selectedAvatarId,
                                            selectedAccentHex,
                                            "en",
                                            preferredAudio,
                                            subtitlesEnabled,
                                            preferredSubLang
                                        )
                                    }
                                )
                            }
                        }

                        // Preferenza audio
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Audio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NetflixLightGray
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProfileChoiceChip(
                                    label = "ITA",
                                    selected = preferredAudio == "it",
                                    accentColor = Color(selectedAccentHex),
                                    tag = "profile_audio_it",
                                    focusRequester = audioItFocusRequester,
                                    onUp = { preferredLangFocusRequester().safeRequestFocus() },
                                    onDown = { subtitlesToggleFocusRequester.safeRequestFocus() },
                                    onLeft = { true },
                                    onRight = { audioEnFocusRequester.safeRequestFocus() },
                                    onClick = {
                                        preferredAudio = "it"
                                        persistAutoSaveIfEditing(
                                            name,
                                            selectedAvatarId,
                                            selectedAccentHex,
                                            preferredLanguage,
                                            "it",
                                            subtitlesEnabled,
                                            preferredSubLang
                                        )
                                    }
                                )
                                ProfileChoiceChip(
                                    label = "ENG",
                                    selected = preferredAudio == "en",
                                    accentColor = Color(selectedAccentHex),
                                    tag = "profile_audio_en",
                                    focusRequester = audioEnFocusRequester,
                                    onUp = { preferredLangFocusRequester().safeRequestFocus() },
                                    onDown = { subtitlesToggleFocusRequester.safeRequestFocus() },
                                    onLeft = { audioItFocusRequester.safeRequestFocus() },
                                    onRight = { audioOriginalFocusRequester.safeRequestFocus() },
                                    onClick = {
                                        preferredAudio = "en"
                                        persistAutoSaveIfEditing(
                                            name,
                                            selectedAvatarId,
                                            selectedAccentHex,
                                            preferredLanguage,
                                            "en",
                                            subtitlesEnabled,
                                            preferredSubLang
                                        )
                                    }
                                )
                                ProfileChoiceChip(
                                    label = "Originale",
                                    selected = preferredAudio == "original",
                                    accentColor = Color(selectedAccentHex),
                                    tag = "profile_audio_original",
                                    focusRequester = audioOriginalFocusRequester,
                                    onUp = { preferredLangFocusRequester().safeRequestFocus() },
                                    onDown = { subtitlesToggleFocusRequester.safeRequestFocus() },
                                    onLeft = { audioEnFocusRequester.safeRequestFocus() },
                                    onRight = { true },
                                    onClick = {
                                        preferredAudio = "original"
                                        persistAutoSaveIfEditing(
                                            name,
                                            selectedAvatarId,
                                            selectedAccentHex,
                                            preferredLanguage,
                                            "original",
                                            subtitlesEnabled,
                                            preferredSubLang
                                        )
                                    }
                                )
                            }
                        }

                        // Preferenza sottotitoli (Switch interno non-focusable per evitare trappole di focus D-Pad)
                        val toggleSubtitles: () -> Unit = {
                            val nextSubs = !subtitlesEnabled
                            subtitlesEnabled = nextSubs
                            persistAutoSaveIfEditing(
                                name,
                                selectedAvatarId,
                                selectedAccentHex,
                                preferredLanguage,
                                preferredAudio,
                                nextSubs,
                                preferredSubLang
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .safeFocusRequester(subtitlesToggleFocusRequester)
                                .clip(RoundedCornerShape(10.dp))
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionUp -> preferredAudioFocusRequester().safeRequestFocus()
                                            Key.DirectionDown -> {
                                                if (subtitlesEnabled) {
                                                    preferredSubLangFocusRequester().safeRequestFocus()
                                                } else {
                                                    focusBelowPreferences()
                                                }
                                            }
                                            Key.DirectionLeft, Key.DirectionRight -> true
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedScale = 1.01f,
                                    focusTag = "profile_subtitles_toggle",
                                    onClick = toggleSubtitles
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("profile_subtitles_toggle")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = if (subtitlesEnabled) Color(selectedAccentHex) else NetflixMutedGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Sottotitoli predefiniti",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NetflixWhite
                                )
                            }
                            Switch(
                                checked = subtitlesEnabled,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NetflixWhite,
                                    checkedTrackColor = Color(selectedAccentHex)
                                ),
                                modifier = Modifier.focusProperties { canFocus = false }
                            )
                        }

                        if (subtitlesEnabled) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Lingua sottotitoli",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NetflixLightGray
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ProfileChoiceChip(
                                        label = "Italiano",
                                        selected = preferredSubLang == "it",
                                        accentColor = Color(selectedAccentHex),
                                        tag = "profile_sub_lang_it",
                                        focusRequester = subLangItFocusRequester,
                                        onUp = { subtitlesToggleFocusRequester.safeRequestFocus() },
                                        onDown = { focusBelowPreferences() },
                                        onLeft = { true },
                                        onRight = { subLangEnFocusRequester.safeRequestFocus() },
                                        onClick = {
                                            preferredSubLang = "it"
                                            persistAutoSaveIfEditing(
                                                name,
                                                selectedAvatarId,
                                                selectedAccentHex,
                                                preferredLanguage,
                                                preferredAudio,
                                                subtitlesEnabled,
                                                "it"
                                            )
                                        }
                                    )
                                    ProfileChoiceChip(
                                        label = "English",
                                        selected = preferredSubLang == "en",
                                        accentColor = Color(selectedAccentHex),
                                        tag = "profile_sub_lang_en",
                                        focusRequester = subLangEnFocusRequester,
                                        onUp = { subtitlesToggleFocusRequester.safeRequestFocus() },
                                        onDown = { focusBelowPreferences() },
                                        onLeft = { subLangItFocusRequester.safeRequestFocus() },
                                        onRight = { true },
                                        onClick = {
                                            preferredSubLang = "en"
                                            persistAutoSaveIfEditing(
                                                name,
                                                selectedAvatarId,
                                                selectedAccentHex,
                                                preferredLanguage,
                                                preferredAudio,
                                                subtitlesEnabled,
                                                "en"
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Sezione profilo predefinito ed eliminazione (solo in modalita modifica)
                if (editingProfile != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val isDefault = defaultProfileId == editingProfile.id
                    val toggleDefaultProfile: () -> Unit = {
                        onSetDefaultProfile(if (isDefault) null else editingProfile.id)
                    }
                    OutlinedButton(
                        onClick = toggleDefaultProfile,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isDefault) NetflixMatchGreen else NetflixElevatedSurface),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDefault) NetflixMatchGreen else NetflixWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(setDefaultFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionUp -> {
                                            if (subtitlesEnabled) {
                                                preferredSubLangFocusRequester().safeRequestFocus()
                                            } else {
                                                subtitlesToggleFocusRequester.safeRequestFocus()
                                            }
                                        }
                                        Key.DirectionDown -> {
                                            if (hasDeleteOrSwitchAction) {
                                                deleteOrSwitchFocusRequester.safeRequestFocus()
                                            } else {
                                                primaryActionFocusRequester.safeRequestFocus()
                                            }
                                        }
                                        Key.DirectionLeft, Key.DirectionRight -> true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.02f,
                                focusTag = "profile_set_default_button",
                                onClick = toggleDefaultProfile
                            )
                            .testTag("profile_set_default_button")
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDefault) "Rimuovi come profilo predefinito all'avvio" else "Imposta come profilo predefinito all'avvio",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        editingProfile.isPrimary -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NetflixCardSurface,
                                border = BorderStroke(1.dp, NetflixElevatedSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_primary_protected_notice")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = NetflixLightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Il profilo principale è protetto e non può essere eliminato.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NetflixLightGray
                                    )
                                }
                            }
                        }

                        editingProfile.id == activeProfileId -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NetflixRed.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_active_delete_guard")
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "Questo profilo è attualmente attivo. Seleziona prima un altro profilo per poterlo eliminare.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NetflixWhite
                                    )
                                    OutlinedButton(
                                        onClick = onSwitchBeforeDelete,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, NetflixWhite),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                                        modifier = Modifier
                                            .heightIn(min = 44.dp)
                                            .safeFocusRequester(deleteOrSwitchFocusRequester)
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == KeyEventType.KeyDown) {
                                                    when (event.key) {
                                                        Key.DirectionUp -> setDefaultFocusRequester.safeRequestFocus()
                                                        Key.DirectionDown -> primaryActionFocusRequester.safeRequestFocus()
                                                        Key.DirectionLeft, Key.DirectionRight -> true
                                                        else -> false
                                                    }
                                                } else {
                                                    false
                                                }
                                            }
                                            .tvFocusableControl(
                                                shape = RoundedCornerShape(8.dp),
                                                focusTag = "profile_switch_before_delete_button"
                                            )
                                            .testTag("profile_switch_before_delete_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Seleziona un altro profilo")
                                    }
                                }
                            }
                        }

                        else -> {
                            val openDeleteConfirm: () -> Unit = { showDeleteConfirm = true }
                            OutlinedButton(
                                onClick = openDeleteConfirm,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NetflixRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(deleteOrSwitchFocusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionUp -> setDefaultFocusRequester.safeRequestFocus()
                                                Key.DirectionDown -> primaryActionFocusRequester.safeRequestFocus()
                                                Key.DirectionLeft, Key.DirectionRight -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedScale = 1.02f,
                                        focusedBorderColor = NetflixWhite,
                                        focusTag = "profile_delete_button"
                                    )
                                    .testTag("profile_delete_button")
                            ) {
                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Elimina profilo", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(if (isTvLayout) 10.dp else 16.dp))

                // Pulsanti finali sempre visibili (ANNULLA/Chiudi e SALVA/Fine/Crea profilo)
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
                            .safeFocusRequester(cancelButtonFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionUp -> focusAboveBottomButtons()
                                        Key.DirectionRight -> primaryActionFocusRequester.safeRequestFocus()
                                        Key.DirectionLeft, Key.DirectionDown -> true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                primaryActionFocusRequester.takeIfAttached()?.let { right = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.02f,
                                focusTag = "profile_editor_cancel"
                            )
                            .testTag("profile_editor_cancel_button")
                    ) {
                        Text(if (isEditMode) "Chiudi" else "Annulla")
                    }

                    Button(
                        onClick = handleSaveOrCreate,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(selectedAccentHex),
                            contentColor = NetflixWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(primaryActionFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionUp -> focusAboveBottomButtons()
                                        Key.DirectionLeft -> cancelButtonFocusRequester.safeRequestFocus()
                                        Key.DirectionRight, Key.DirectionDown -> true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                cancelButtonFocusRequester.takeIfAttached()?.let { left = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.03f,
                                focusedBorderColor = NetflixWhite,
                                focusTag = "profile_editor_save"
                            )
                            .testTag("profile_editor_save_button")
                    ) {
                        Text(
                            text = if (isEditMode) "Fine" else "Crea profilo",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Finestra di conferma eliminazione profilo + dati personali associati
        if (showDeleteConfirm && editingProfile != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixBlack.copy(alpha = 0.80f))
                    .testTag("profile_delete_confirm_dialog"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = NetflixCardSurface,
                    border = BorderStroke(1.5.dp, NetflixRed),
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Eliminare \"${editingProfile.name}\"?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite
                        )
                        Text(
                            text = "Verranno eliminati definitivamente questo profilo e tutti i suoi dati personali: preferiti, Continua a guardare, progressi di riproduzione e preferenze.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NetflixLightGray
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val cancelDeleteAction: () -> Unit = { showDeleteConfirm = false }
                            val confirmDeleteAction: () -> Unit = {
                                showDeleteConfirm = false
                                onDeleteProfile(editingProfile.id)
                            }
                            OutlinedButton(
                                onClick = cancelDeleteAction,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NetflixElevatedSurface),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(deleteCancelFocusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionRight -> deleteConfirmFocusRequester.safeRequestFocus()
                                                Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusTag = "profile_delete_cancel"
                                    )
                                    .testTag("profile_delete_cancel_button")
                            ) {
                                Text("Annulla")
                            }
                            Button(
                                onClick = confirmDeleteAction,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed,
                                    contentColor = NetflixWhite
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(deleteConfirmFocusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionLeft -> deleteCancelFocusRequester.safeRequestFocus()
                                                Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedBorderColor = NetflixWhite,
                                        focusTag = "profile_delete_confirm"
                                    )
                                    .testTag("profile_delete_confirm_button")
                            ) {
                                Text("Elimina", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileChoiceChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    tag: String,
    focusRequester: FocusRequester,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
    onLeft: () -> Boolean,
    onRight: () -> Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accentColor.copy(alpha = 0.25f) else NetflixDarkSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else NetflixElevatedSurface
        ),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .safeFocusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> onUp()
                        Key.DirectionDown -> onDown()
                        Key.DirectionLeft -> onLeft()
                        Key.DirectionRight -> onRight()
                        else -> false
                    }
                } else {
                    false
                }
            }
            .tvFocusableControl(
                shape = RoundedCornerShape(8.dp),
                focusedScale = 1.05f,
                focusedBorderColor = NetflixWhite,
                focusTag = tag,
                onClick = onClick
            )
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) NetflixWhite else NetflixLightGray
            )
        }
    }
}

@Composable
private fun ProfileInfoPill(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = NetflixWhite,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
