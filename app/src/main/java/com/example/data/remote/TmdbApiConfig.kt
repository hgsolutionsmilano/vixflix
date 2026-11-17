package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import com.example.R

/**
 * Configurazione centrale della chiave API TMDB integrata nell'applicazione
 * (sia a livello codice sia nel file risorsa `res/raw/tmdb_config.json`),
 * visibile e gestibile anche dalla sezione Menu GitHub dell'app.
 */
object TmdbApiConfig {
    const val BUNDLED_FILE_PATH = "app/src/main/res/raw/tmdb_config.json"
    const val KOTLIN_CONFIG_PATH = "app/src/main/java/com/example/data/remote/TmdbApiConfig.kt"

    // Chiave API TMDB integrata nell'applicazione
    const val INTEGRATED_TMDB_API_KEY: String = "8bbf9bcc418b752d137d973fd97fcb0f"

    @Volatile
    var currentApiKey: String = resolveInitialKey()
        private set

    private fun resolveInitialKey(): String {
        val fromBuildConfig = BuildConfig.TMDB_API_KEY.trim()
        return if (fromBuildConfig.isNotBlank() && fromBuildConfig != "MY_TMDB_API_KEY") {
            fromBuildConfig
        } else {
            INTEGRATED_TMDB_API_KEY
        }
    }

    fun updateApiKey(newKey: String) {
        val cleaned = newKey.trim()
        currentApiKey = if (cleaned.isNotBlank()) cleaned else INTEGRATED_TMDB_API_KEY
    }

    fun resetToDefault(): String {
        currentApiKey = INTEGRATED_TMDB_API_KEY
        return currentApiKey
    }

    fun readBundledJsonPreview(context: Context): String {
        return runCatching {
            context.resources.openRawResource(R.raw.tmdb_config)
                .bufferedReader()
                .use { it.readText().trim() }
        }.getOrElse {
            """
            {
              "provider": "The Movie Database (TMDB API v3)",
              "tmdb_api_key": "$INTEGRATED_TMDB_API_KEY",
              "tmdb_base_url": "https://api.themoviedb.org/3/",
              "vixsrc_base_url": "https://vixsrc.to"
            }
            """.trimIndent()
        }
    }
}
