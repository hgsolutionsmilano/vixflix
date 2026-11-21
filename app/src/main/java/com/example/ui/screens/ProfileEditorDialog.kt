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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import com.example.data.local.ProfileAvatarPreset
import com.example.data.local.ProfileCatalogDefaults
import com.example.data.local.UserProfileEntity
import com.example.ui.components.ProfileAvatarArtwork
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
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

/**
 * Dialogo modale cinematografico per creare un nuovo profilo o modificare un profilo esistente.
 * In modalita modifica salva automaticamente ogni personalizzazione (avatar, colore, lingua,
 * audio, sottotitoli e nome valido).
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
        editingProfile?.avatarId ?: ProfileAvatarPreset.entries.first().id
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

    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFieldFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }
    val deleteCancelFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

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

    LaunchedEffect(isEditMode) {
        repeat(3) {
            withFrameNanos { }
            if (isEditMode) {
                primaryActionFocusRequester.safeRequestFocus()
            } else {
                nameFieldFocusRequester.safeRequestFocus()
            }
        }
    }

    LaunchedEffect(showDeleteConfirm) {
        if (showDeleteConfirm) {
            repeat(3) {
                withFrameNanos { }
                deleteCancelFocusRequester.safeRequestFocus()
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
            .background(NetflixBlack.copy(alpha = 0.88f))
            .padding(horizontal = if (isTvLayout) 40.dp else 16.dp, vertical = 20.dp)
            .testTag("profile_editor_dialog"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = NetflixDarkSurface,
            border = BorderStroke(1.5.dp, Color(selectedAccentHex).copy(alpha = 0.65f)),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(if (isTvLayout) 28.dp else 20.dp)
            ) {
                // Header
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
                            .tvFocusableControl(shape = CircleShape, focusTag = "profile_editor_close")
                            .testTag("profile_editor_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Chiudi",
                            tint = NetflixWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Anteprima profilo live
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(selectedAccentHex).copy(alpha = 0.24f),
                                    NetflixCardSurface
                                )
                            )
                        )
                        .border(1.dp, NetflixElevatedSurface, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    ProfileAvatarArtwork(
                        avatarId = selectedAvatarId,
                        accentColorHex = selectedAccentHex,
                        size = if (isTvLayout) 88.dp else 72.dp,
                        cornerRadius = 16.dp
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = name.trim().ifBlank { "Nuovo spettatore" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                Spacer(modifier = Modifier.height(18.dp))

                // Campo nome profilo
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
                    placeholder = { Text("Es. Marco, Sara, Salotto…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            primaryActionFocusRequester.safeRequestFocus()
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
                        .fillMaxWidth()
                        .safeFocusRequester(nameFieldFocusRequester)
                        .testTag("profile_name_input")
                )

                val shownError = validationError ?: errorMessage
                if (!shownError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = shownError,
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixRed,
                        modifier = Modifier.testTag("profile_editor_error_text")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Galleria 12 Avatar
                Text(
                    text = "Scegli un avatar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.testTag("profile_avatar_picker_row")
                ) {
                    itemsIndexed(ProfileAvatarPreset.entries) { _, preset ->
                        val isChosen = selectedAvatarId == preset.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (isChosen) {
                                        Modifier.border(2.5.dp, NetflixWhite, RoundedCornerShape(14.dp))
                                    } else {
                                        Modifier.border(1.dp, NetflixElevatedSurface, RoundedCornerShape(14.dp))
                                    }
                                )
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(14.dp),
                                    focusedScale = 1.07f,
                                    focusedBorderColor = NetflixWhite,
                                    focusTag = "profile_avatar_option_${preset.id}",
                                    onClick = {
                                        selectedAvatarId = preset.id
                                        persistAutoSaveIfEditing(
                                            name,
                                            preset.id,
                                            selectedAccentHex,
                                            preferredLanguage,
                                            preferredAudio,
                                            subtitlesEnabled,
                                            preferredSubLang
                                        )
                                    }
                                )
                                .padding(4.dp)
                                .testTag("profile_avatar_option_${preset.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            ProfileAvatarArtwork(
                                avatarId = preset.id,
                                accentColorHex = selectedAccentHex,
                                size = 58.dp,
                                cornerRadius = 11.dp
                            )
                            if (isChosen) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(NetflixMatchGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Avatar selezionato",
                                        tint = NetflixBlack,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Selettore Colore Identificativo
                Text(
                    text = "Colore identificativo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.testTag("profile_color_picker_row")
                ) {
                    itemsIndexed(ProfileCatalogDefaults.accentOptions) { idx, option ->
                        val isChosen = selectedAccentHex == option.colorHex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(option.colorHex))
                                .then(
                                    if (isChosen) {
                                        Modifier.border(3.dp, NetflixWhite, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, NetflixWhite.copy(alpha = 0.25f), CircleShape)
                                    }
                                )
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

                Spacer(modifier = Modifier.height(20.dp))

                // Preferenze personali (Lingua, Audio, Sottotitoli)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NetflixCardSurface,
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(16.dp)
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
                                listOf("it" to "Italiano", "en" to "English").forEach { (code, label) ->
                                    ProfileChoiceChip(
                                        label = label,
                                        selected = preferredLanguage == code,
                                        accentColor = Color(selectedAccentHex),
                                        tag = "profile_lang_$code",
                                        onClick = {
                                            preferredLanguage = code
                                            persistAutoSaveIfEditing(
                                                name,
                                                selectedAvatarId,
                                                selectedAccentHex,
                                                code,
                                                preferredAudio,
                                                subtitlesEnabled,
                                                preferredSubLang
                                            )
                                        }
                                    )
                                }
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
                                listOf("it" to "ITA", "en" to "ENG", "original" to "Originale").forEach { (code, label) ->
                                    ProfileChoiceChip(
                                        label = label,
                                        selected = preferredAudio == code,
                                        accentColor = Color(selectedAccentHex),
                                        tag = "profile_audio_$code",
                                        onClick = {
                                            preferredAudio = code
                                            persistAutoSaveIfEditing(
                                                name,
                                                selectedAvatarId,
                                                selectedAccentHex,
                                                preferredLanguage,
                                                code,
                                                subtitlesEnabled,
                                                preferredSubLang
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Preferenza sottotitoli
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .tvFocusableControl(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedScale = 1.01f,
                                    focusTag = "profile_subtitles_toggle",
                                    onClick = {
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
                                onCheckedChange = { checked ->
                                    subtitlesEnabled = checked
                                    persistAutoSaveIfEditing(
                                        name,
                                        selectedAvatarId,
                                        selectedAccentHex,
                                        preferredLanguage,
                                        preferredAudio,
                                        checked,
                                        preferredSubLang
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NetflixWhite,
                                    checkedTrackColor = Color(selectedAccentHex)
                                )
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
                                    listOf("it" to "Italiano", "en" to "English").forEach { (code, label) ->
                                        ProfileChoiceChip(
                                            label = label,
                                            selected = preferredSubLang == code,
                                            accentColor = Color(selectedAccentHex),
                                            tag = "profile_sub_lang_$code",
                                            onClick = {
                                                preferredSubLang = code
                                                persistAutoSaveIfEditing(
                                                    name,
                                                    selectedAvatarId,
                                                    selectedAccentHex,
                                                    preferredLanguage,
                                                    preferredAudio,
                                                    subtitlesEnabled,
                                                    code
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Sezione profilo predefinito ed eliminazione (solo in modalita modifica)
                if (editingProfile != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val isDefault = defaultProfileId == editingProfile.id
                    OutlinedButton(
                        onClick = {
                            onSetDefaultProfile(if (isDefault) null else editingProfile.id)
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isDefault) NetflixMatchGreen else NetflixElevatedSurface),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDefault) NetflixMatchGreen else NetflixWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .tvFocusableControl(
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.02f,
                                focusTag = "profile_set_default_button"
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
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NetflixRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
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

                Spacer(modifier = Modifier.height(22.dp))

                // Pulsanti finali
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
                        onClick = {
                            val trimmed = name.trim()
                            if (trimmed.isEmpty()) {
                                validationError = "Inserisci un nome per il profilo."
                                nameFieldFocusRequester.safeRequestFocus()
                                return@Button
                            }
                            if (editingProfile != null) {
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
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(selectedAccentHex),
                            contentColor = NetflixWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(primaryActionFocusRequester)
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
                            OutlinedButton(
                                onClick = { showDeleteConfirm = false },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NetflixElevatedSurface),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(deleteCancelFocusRequester)
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusTag = "profile_delete_cancel"
                                    )
                                    .testTag("profile_delete_cancel_button")
                            ) {
                                Text("Annulla")
                            }
                            Button(
                                onClick = {
                                    showDeleteConfirm = false
                                    onDeleteProfile(editingProfile.id)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed,
                                    contentColor = NetflixWhite
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
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
