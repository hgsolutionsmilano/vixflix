package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    val lastUpdateCheckFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATE_CHECK] ?: 0L
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

    suspend fun recordUpdateCheck() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_CHECK] = System.currentTimeMillis()
        }
    }
}
