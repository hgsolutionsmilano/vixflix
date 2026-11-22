package com.example.data.local

import androidx.compose.runtime.Immutable

const val DEFAULT_PRIMARY_PROFILE_ID: String = "profile_primary"
const val MAX_USER_PROFILES: Int = 8
const val INITIAL_AVATAR_ID: String = "initial_letter"

/**
 * Estrae la lettera iniziale (in maiuscolo) dal nome del profilo per l'avatar automatico con iniziale.
 * Esempi:
 * - "Marco" -> "M"
 * - "Veronica" -> "V"
 * - "Prime Pixel" -> "P"
 */
fun extractProfileInitial(name: String?): String {
    val trimmed = name?.trim().orEmpty()
    val firstChar = trimmed.firstOrNull { it.isLetterOrDigit() } ?: trimmed.firstOrNull()
    return firstChar?.uppercaseChar()?.toString() ?: "P"
}

/**
 * Restituisce `true` se il profilo utilizza l'avatar automatico basato sull'iniziale del nome
 * (nessun avatar grafico personalizzato selezionato manualmente).
 */
fun isInitialAvatarId(avatarId: String?): Boolean {
    if (avatarId.isNullOrBlank()) return true
    if (avatarId.equals(INITIAL_AVATAR_ID, ignoreCase = true)) return true
    return ProfileAvatarPreset.fromIdOrNull(avatarId) == null
}

enum class AvatarEmblemStyle {
    CINEMA_SMILE,
    NEON_VISOR,
    OCEAN_WAVE,
    SOLAR_FLARE,
    EMERALD_DIAMOND,
    AMETHYST_STARS,
    MIDNIGHT_CAT,
    COSMIC_PLANET,
    RETRO_REEL,
    CORAL_SUNSET,
    ARCTIC_PRISM,
    OBSIDIAN_CROWN
}

@Immutable
enum class ProfileAvatarPreset(
    val id: String,
    val label: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val emblemStyle: AvatarEmblemStyle
) {
    CINEMA_CRIMSON(
        id = "cinema_crimson",
        label = "Cinema",
        primaryColorHex = 0xFFE50914L,
        secondaryColorHex = 0xFF8C060CL,
        emblemStyle = AvatarEmblemStyle.CINEMA_SMILE
    ),
    NEON_CYBER(
        id = "neon_cyber",
        label = "Cyber",
        primaryColorHex = 0xFF00B4D8L,
        secondaryColorHex = 0xFF004E92L,
        emblemStyle = AvatarEmblemStyle.NEON_VISOR
    ),
    OCEAN_PULSE(
        id = "ocean_pulse",
        label = "Oceano",
        primaryColorHex = 0xFF2EC4B6L,
        secondaryColorHex = 0xFF0F5C58L,
        emblemStyle = AvatarEmblemStyle.OCEAN_WAVE
    ),
    SOLAR_GOLD(
        id = "solar_gold",
        label = "Oro",
        primaryColorHex = 0xFFFF9F1CL,
        secondaryColorHex = 0xFFB55400L,
        emblemStyle = AvatarEmblemStyle.SOLAR_FLARE
    ),
    EMERALD_QUEST(
        id = "emerald_pulse",
        label = "Smeraldo",
        primaryColorHex = 0xFF46D369L,
        secondaryColorHex = 0xFF1B6E33L,
        emblemStyle = AvatarEmblemStyle.EMERALD_DIAMOND
    ),
    AMETHYST_DUSK(
        id = "amethyst_dusk",
        label = "Ametista",
        primaryColorHex = 0xFF9B5DE5L,
        secondaryColorHex = 0xFF4A1D85L,
        emblemStyle = AvatarEmblemStyle.AMETHYST_STARS
    ),
    MIDNIGHT_FOX(
        id = "midnight_fox",
        label = "Notte",
        primaryColorHex = 0xFFFF006EL,
        secondaryColorHex = 0xFF83003BL,
        emblemStyle = AvatarEmblemStyle.MIDNIGHT_CAT
    ),
    COSMIC_ORBIT(
        id = "cosmic_orbit",
        label = "Cosmo",
        primaryColorHex = 0xFF3A86FFL,
        secondaryColorHex = 0xFF16357AL,
        emblemStyle = AvatarEmblemStyle.COSMIC_PLANET
    ),
    RETRO_REEL(
        id = "retro_reel",
        label = "Pellicola",
        primaryColorHex = 0xFFE76F51L,
        secondaryColorHex = 0xFF7D2E1BL,
        emblemStyle = AvatarEmblemStyle.RETRO_REEL
    ),
    CORAL_HORIZON(
        id = "coral_horizon",
        label = "Corallo",
        primaryColorHex = 0xFFFF595EL,
        secondaryColorHex = 0xFF8F1D24L,
        emblemStyle = AvatarEmblemStyle.CORAL_SUNSET
    ),
    ARCTIC_PRISM(
        id = "arctic_prism",
        label = "Artico",
        primaryColorHex = 0xFF4CC9F0L,
        secondaryColorHex = 0xFF22577AL,
        emblemStyle = AvatarEmblemStyle.ARCTIC_PRISM
    ),
    OBSIDIAN_CROWN(
        id = "obsidian_crown",
        label = "Corona",
        primaryColorHex = 0xFFF4A261L,
        secondaryColorHex = 0xFF432818L,
        emblemStyle = AvatarEmblemStyle.OBSIDIAN_CROWN
    );

    companion object {
        fun fromIdOrNull(id: String?): ProfileAvatarPreset? {
            if (id.isNullOrBlank() || id.equals(INITIAL_AVATAR_ID, ignoreCase = true)) return null
            val normalized = id.trim().lowercase()
            if (normalized == "emerald_quest") return EMERALD_QUEST
            return entries.firstOrNull { it.id == normalized }
        }

        fun fromId(id: String?): ProfileAvatarPreset =
            fromIdOrNull(id) ?: CINEMA_CRIMSON

        fun normalizeAvatarId(id: String?): String =
            fromIdOrNull(id)?.id ?: INITIAL_AVATAR_ID
    }
}

@Immutable
data class ProfileAccentOption(
    val colorHex: Long,
    val label: String
)

object ProfileCatalogDefaults {
    val accentOptions: List<ProfileAccentOption> = listOf(
        ProfileAccentOption(0xFFE50914L, "Rosso Cinema"),
        ProfileAccentOption(0xFF00B4D8L, "Azzurro"),
        ProfileAccentOption(0xFF2EC4B6L, "Turchese"),
        ProfileAccentOption(0xFFFF9F1CL, "Ambra"),
        ProfileAccentOption(0xFF9B5DE5L, "Viola"),
        ProfileAccentOption(0xFFFF006EL, "Magenta"),
        ProfileAccentOption(0xFF46D369L, "Verde"),
        ProfileAccentOption(0xFF3A86FFL, "Blu Reale")
    )

    fun defaultPrimaryProfile(): UserProfileEntity = UserProfileEntity(
        id = DEFAULT_PRIMARY_PROFILE_ID,
        name = "Profilo Principale",
        avatarId = INITIAL_AVATAR_ID,
        accentColorHex = 0xFFE50914L,
        isPrimary = true,
        preferredLanguage = "it",
        preferredAudioLanguage = "it",
        subtitlesEnabled = false,
        preferredSubtitleLanguage = "it"
    )
}

@Immutable
data class UserProfileUiState(
    val profiles: List<UserProfileEntity> = emptyList(),
    val activeProfile: UserProfileEntity? = null,
    val defaultProfileId: String? = null,
    val autoLoginEnabled: Boolean = false,
    val showProfileSelector: Boolean = false,
    val isManageMode: Boolean = false,
    val mustSelectBeforeClosing: Boolean = false,
    val maxProfiles: Int = MAX_USER_PROFILES,
    val errorMessage: String? = null,
    val isInitialized: Boolean = false
) {
    val canCreateMoreProfiles: Boolean get() = profiles.size < maxProfiles
}
