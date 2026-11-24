package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.data.remote.TmdbApiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vixflix_prefs")

enum class StreamingBufferPreset(
    val storageKey: String,
    val label: String,
    val sizeMb: Int,
    val windowHint: String,
    val description: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
) {
    MINIMAL(
        storageKey = "minimal",
        label = "Minimo",
        sizeMb = 16,
        windowHint = "5–15s • Avvio rapido",
        description = "Ideale per dispositivi con poca RAM o connessioni lente",
        minBufferMs = 5_000,
        maxBufferMs = 15_000,
        bufferForPlaybackMs = 900,
        bufferForPlaybackAfterRebufferMs = 2_500
    ),
    DEFAULT(
        storageKey = "default",
        label = "Predefinito",
        sizeMb = 32,
        windowHint = "15–35s • HD standard",
        description = "Equilibrio ottimale tra velocità di avvio e fluidità",
        minBufferMs = 15_000,
        maxBufferMs = 35_000,
        bufferForPlaybackMs = 1_100,
        bufferForPlaybackAfterRebufferMs = 4_500
    ),
    BALANCED(
        storageKey = "balanced",
        label = "Bilanciato",
        sizeMb = 64,
        windowHint = "25–60s • 1080p Wi-Fi",
        description = "Riserva extra per flussi Full HD ad alto bitrate",
        minBufferMs = 25_000,
        maxBufferMs = 60_000,
        bufferForPlaybackMs = 1_200,
        bufferForPlaybackAfterRebufferMs = 5_000
    ),
    EXTENDED(
        storageKey = "extended",
        label = "Esteso",
        sizeMb = 128,
        windowHint = "45–120s • Max 4K",
        description = "Precaricamento massimo per evitare pause su reti instabili",
        minBufferMs = 45_000,
        maxBufferMs = 120_000,
        bufferForPlaybackMs = 1_400,
        bufferForPlaybackAfterRebufferMs = 5_500
    );

    val targetBufferBytes: Int get() = sizeMb * 1024 * 1024

    companion object {
        fun fromKey(key: String?): StreamingBufferPreset =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}

class AppPreferences(private val context: Context) {
    companion object {
        private val KEY_GITHUB_REPO = stringPreferencesKey("github_repo")
        private val KEY_STREAM_LANG = stringPreferencesKey("stream_lang")
        private val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        private val KEY_TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        private val KEY_PREFER_WEB_PLAYER = booleanPreferencesKey("prefer_web_player")
        private val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        private val KEY_BUFFER_PRESET = stringPreferencesKey("buffer_preset")
        private val KEY_BACKGROUND_PREFETCH = booleanPreferencesKey("background_image_prefetch")
        private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        private val KEY_DEFAULT_PROFILE_ID = stringPreferencesKey("default_profile_id")
        private val KEY_AUTO_LOGIN_PROFILE = booleanPreferencesKey("auto_login_profile")
        private val KEY_HAS_SELECTED_PROFILE_ONCE = booleanPreferencesKey("has_selected_profile_once")
        private val KEY_PLAYER_SKIP_INTRO = booleanPreferencesKey("player_skip_intro_enabled")
        private val KEY_PLAYER_NEXT_EPISODE_PROMPT = booleanPreferencesKey("player_next_episode_prompt_enabled")
        private val KEY_PLAYER_AUTO_PLAY_NEXT = booleanPreferencesKey("player_auto_play_next_episode_enabled")
        private val KEY_PLAYER_NEXT_COUNTDOWN_SEC = intPreferencesKey("player_next_episode_countdown_sec")
        private val KEY_PLAYER_CREDITS_DETECTION = booleanPreferencesKey("player_credits_detection_enabled")
        private val KEY_PLAYER_MANUAL_MOVIE_COMPLETE = booleanPreferencesKey("player_manual_movie_completion_enabled")
        private val KEY_PLAYER_VIDEO_ASPECT_MODE = stringPreferencesKey("player_video_aspect_mode")
        private val KEY_PLAYER_SUBTITLE_TEXT_SIZE = stringPreferencesKey("player_subtitle_text_size")
        private val KEY_PLAYER_SUBTITLE_TEXT_COLOR = stringPreferencesKey("player_subtitle_text_color")
        private val KEY_PLAYER_SUBTITLE_BG_STYLE = stringPreferencesKey("player_subtitle_bg_style")
    }

    val githubRepoFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GITHUB_REPO]?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_GITHUB_REPO
    }

    val tmdbApiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TMDB_API_KEY]?.takeIf { it.isNotBlank() }
            ?: TmdbApiConfig.INTEGRATED_TMDB_API_KEY
    }

    val streamLanguageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_STREAM_LANG] ?: "it"
    }

    val preferWebPlayerFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PREFER_WEB_PLAYER] ?: false
    }

    val autoCheckUpdatesFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_UPDATES] ?: true
    }

    val bufferPresetFlow: Flow<StreamingBufferPreset> = context.dataStore.data.map { prefs ->
        StreamingBufferPreset.fromKey(prefs[KEY_BUFFER_PRESET])
    }

    val backgroundPrefetchFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_PREFETCH] ?: true
    }

    val lastUpdateCheckFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATE_CHECK] ?: 0L
    }

    val activeProfileIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PROFILE_ID]?.takeIf { it.isNotBlank() } ?: DEFAULT_PRIMARY_PROFILE_ID
    }

    val defaultProfileIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_PROFILE_ID]?.takeIf { it.isNotBlank() }
    }

    val autoLoginProfileFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_LOGIN_PROFILE] ?: false
    }

    val hasSelectedProfileOnceFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAS_SELECTED_PROFILE_ONCE] ?: false
    }

    val playerAdvancedSettingsFlow: Flow<PlayerAdvancedSettings> = context.dataStore.data.map { prefs ->
        val rawCountdown = prefs[KEY_PLAYER_NEXT_COUNTDOWN_SEC]
            ?: PlayerAdvancedSettings.DEFAULT_COUNTDOWN_SECONDS
        val validCountdown = if (rawCountdown in PlayerAdvancedSettings.ALLOWED_COUNTDOWN_OPTIONS) {
            rawCountdown
        } else {
            PlayerAdvancedSettings.DEFAULT_COUNTDOWN_SECONDS
        }
        PlayerAdvancedSettings(
            skipIntroEnabled = prefs[KEY_PLAYER_SKIP_INTRO] ?: true,
            nextEpisodePromptEnabled = prefs[KEY_PLAYER_NEXT_EPISODE_PROMPT] ?: true,
            autoPlayNextEpisodeEnabled = prefs[KEY_PLAYER_AUTO_PLAY_NEXT] ?: true,
            nextEpisodeCountdownSec = validCountdown,
            creditsDetectionEnabled = prefs[KEY_PLAYER_CREDITS_DETECTION] ?: true,
            manualMovieCompletionEnabled = prefs[KEY_PLAYER_MANUAL_MOVIE_COMPLETE] ?: true
        )
    }

    val playerComfortSettingsFlow: Flow<PlayerComfortSettings> = context.dataStore.data.map { prefs ->
        PlayerComfortSettings(
            videoAspectMode = VideoAspectMode.fromStorageKey(prefs[KEY_PLAYER_VIDEO_ASPECT_MODE]),
            subtitleTextSize = SubtitleTextSize.fromStorageKey(prefs[KEY_PLAYER_SUBTITLE_TEXT_SIZE]),
            subtitleTextColor = SubtitleTextColor.fromStorageKey(prefs[KEY_PLAYER_SUBTITLE_TEXT_COLOR]),
            subtitleBackgroundStyle = SubtitleBackgroundStyle.fromStorageKey(prefs[KEY_PLAYER_SUBTITLE_BG_STYLE])
        )
    }

    suspend fun setGithubRepo(ownerAndRepo: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GITHUB_REPO] = ownerAndRepo.trim().removePrefix("https://github.com/").removeSuffix("/")
        }
    }

    suspend fun setTmdbApiKey(apiKey: String) {
        val cleaned = apiKey.trim().ifBlank { TmdbApiConfig.INTEGRATED_TMDB_API_KEY }
        TmdbApiConfig.updateApiKey(cleaned)
        context.dataStore.edit { prefs ->
            prefs[KEY_TMDB_API_KEY] = cleaned
        }
    }

    suspend fun setStreamLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STREAM_LANG] = lang
        }
    }

    suspend fun setPreferWebPlayer(preferWeb: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFER_WEB_PLAYER] = preferWeb
        }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_CHECK_UPDATES] = enabled
        }
    }

    suspend fun setBufferPreset(preset: StreamingBufferPreset) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BUFFER_PRESET] = preset.storageKey
        }
    }

    suspend fun setBackgroundPrefetch(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_PREFETCH] = enabled
        }
    }

    suspend fun setActiveProfileId(profileId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PROFILE_ID] = profileId.ifBlank { DEFAULT_PRIMARY_PROFILE_ID }
        }
    }

    suspend fun setDefaultProfileId(profileId: String?) {
        context.dataStore.edit { prefs ->
            if (profileId.isNullOrBlank()) {
                prefs.remove(KEY_DEFAULT_PROFILE_ID)
            } else {
                prefs[KEY_DEFAULT_PROFILE_ID] = profileId
            }
        }
    }

    suspend fun setAutoLoginProfile(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_LOGIN_PROFILE] = enabled
        }
    }

    suspend fun markProfileSelectedOnce() {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_SELECTED_PROFILE_ONCE] = true
        }
    }

    suspend fun setSkipIntroEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_SKIP_INTRO] = enabled
        }
    }

    suspend fun setNextEpisodePromptEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_NEXT_EPISODE_PROMPT] = enabled
        }
    }

    suspend fun setAutoPlayNextEpisodeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_AUTO_PLAY_NEXT] = enabled
        }
    }

    suspend fun setNextEpisodeCountdownSec(seconds: Int) {
        val sanitized = if (seconds in PlayerAdvancedSettings.ALLOWED_COUNTDOWN_OPTIONS) {
            seconds
        } else {
            PlayerAdvancedSettings.DEFAULT_COUNTDOWN_SECONDS
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_NEXT_COUNTDOWN_SEC] = sanitized
        }
    }

    suspend fun setCreditsDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_CREDITS_DETECTION] = enabled
        }
    }

    suspend fun setManualMovieCompletionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_MANUAL_MOVIE_COMPLETE] = enabled
        }
    }

    suspend fun setVideoAspectMode(mode: VideoAspectMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_VIDEO_ASPECT_MODE] = mode.storageKey
        }
    }

    suspend fun setSubtitleTextSize(size: SubtitleTextSize) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_SUBTITLE_TEXT_SIZE] = size.storageKey
        }
    }

    suspend fun setSubtitleTextColor(color: SubtitleTextColor) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_SUBTITLE_TEXT_COLOR] = color.storageKey
        }
    }

    suspend fun setSubtitleBackgroundStyle(style: SubtitleBackgroundStyle) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_SUBTITLE_BG_STYLE] = style.storageKey
        }
    }

    suspend fun recordUpdateCheck() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_CHECK] = System.currentTimeMillis()
        }
    }
}
