package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vixflix_prefs")

class AppPreferences(private val context: Context) {
    companion object {
        private val KEY_GITHUB_REPO = stringPreferencesKey("github_repo")
        private val KEY_STREAM_LANG = stringPreferencesKey("stream_lang")
        private val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }

    val githubRepoFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GITHUB_REPO]?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_GITHUB_REPO
    }

    val streamLanguageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_STREAM_LANG] ?: "it"
    }

    val lastUpdateCheckFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATE_CHECK] ?: 0L
    }

    suspend fun setGithubRepo(ownerAndRepo: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GITHUB_REPO] = ownerAndRepo.trim().removePrefix("https://github.com/").removeSuffix("/")
        }
    }

    suspend fun setStreamLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STREAM_LANG] = lang
        }
    }

    suspend fun recordUpdateCheck() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_CHECK] = System.currentTimeMillis()
        }
    }
}
