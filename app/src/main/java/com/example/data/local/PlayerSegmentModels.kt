package com.example.data.local

import androidx.compose.runtime.Immutable

/**
 * Origine dei marker di introduzione (sigla) per un episodio di una serie TV.
 * Non genera mai intervalli casuali o stimati: in assenza di dati affidabili resta UNAVAILABLE.
 */
enum class IntroSegmentSource(val storageKey: String) {
    VERIFIED_METADATA("verified_metadata"),
    LOCAL_DB("local_db"),
    UNAVAILABLE("unavailable");

    companion object {
        fun fromStorageKey(key: String?): IntroSegmentSource =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: UNAVAILABLE
    }
}

/**
 * Stato e affidabilità del rilevamento titoli di coda.
 * Distingue chiaramente i marker affidabili (metadati/DB locale) dalla soglia configurabile di fallback.
 */
enum class CreditsDetectionState(val storageKey: String, val isReliableMetadata: Boolean) {
    VERIFIED_METADATA("verified_metadata", true),
    LOCAL_DB("local_db", true),
    FALLBACK_THRESHOLD("fallback_threshold", false),
    UNAVAILABLE("unavailable", false);

    companion object {
        fun fromStorageKey(key: String?): CreditsDetectionState =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: UNAVAILABLE
    }
}

/**
 * Impostazioni avanzate del video player, persistenti in DataStore e configurabili da Impostazioni.
 */
@Immutable
data class PlayerAdvancedSettings(
    val skipIntroEnabled: Boolean = true,
    val nextEpisodePromptEnabled: Boolean = true,
    val autoPlayNextEpisodeEnabled: Boolean = true,
    val nextEpisodeCountdownSec: Int = DEFAULT_COUNTDOWN_SECONDS,
    val creditsDetectionEnabled: Boolean = true,
    val manualMovieCompletionEnabled: Boolean = true
) {
    val sanitizedCountdownSec: Int
        get() = if (nextEpisodeCountdownSec in ALLOWED_COUNTDOWN_OPTIONS) {
            nextEpisodeCountdownSec
        } else {
            DEFAULT_COUNTDOWN_SECONDS
        }

    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 10
        val ALLOWED_COUNTDOWN_OPTIONS = listOf(5, 10, 15, 20)
    }
}

/**
 * Rappresenta un episodio successivo realmente verificato nei metadati TMDB della serie
 * (stessa stagione o prima puntata della stagione successiva valida).
 */
@Immutable
data class VerifiedNextEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String? = null,
    val stillUrl: String? = null,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val isCrossSeason: Boolean = false,
    val airDate: String? = null
)

/**
 * Rappresenta un episodio precedente realmente verificato nei metadati TMDB della serie
 * (stessa stagione o ultima puntata della stagione precedente valida).
 */
@Immutable
data class VerifiedPreviousEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String? = null,
    val stillUrl: String? = null,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val isCrossSeason: Boolean = false
)

/**
 * Trigger risolto per l'inizio dei titoli di coda, con distinzione esplicita della sorgente.
 */
@Immutable
data class ResolvedCreditsTrigger(
    val creditsStartMs: Long,
    val source: CreditsDetectionState
)

object PlayerSegmentKeys {
    fun introEpisodeKey(seriesTmdbId: Int, seasonNumber: Int, episodeNumber: Int): String =
        "tv_${seriesTmdbId}_s${seasonNumber}_e${episodeNumber}"

    fun creditsContentKey(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String = if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
        "tv_${tmdbId}_s${seasonNumber}_e${episodeNumber}"
    } else {
        "${mediaType}_${tmdbId}"
    }

    fun completedMediaKey(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String = creditsContentKey(tmdbId, mediaType, seasonNumber, episodeNumber)
}

/**
 * Formatta la riga secondaria di stagione ed episodio per le serie TV:
 * `STAGIONE 2 • EPISODIO 5` oppure `STAGIONE 2 • EPISODIO 5 – Il segreto`.
 * Restituisce `null` se mancano dati affidabili su stagione o episodio (non inventa mai valori).
 */
fun formatSeriesSeasonEpisodeLine(
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?
): String? {
    if (seasonNumber == null || seasonNumber <= 0 || episodeNumber == null || episodeNumber <= 0) {
        return null
    }
    val base = "STAGIONE $seasonNumber • EPISODIO $episodeNumber"
    val cleanTitle = episodeTitle?.trim().orEmpty()
    return if (cleanTitle.isNotEmpty()) {
        "$base – $cleanTitle"
    } else {
        base
    }
}

/**
 * Verifica in modo rigoroso se la posizione corrente rientra in un intervallo intro affidabile
 * associato esattamente alla serie, stagione ed episodio attualmente in riproduzione.
 * Restituisce sempre `false` quando non esistono dati intro reali per lo specifico episodio.
 */
fun isIntroActiveAtPosition(
    segment: IntroSegmentEntity?,
    positionMs: Long,
    durationMs: Long,
    seriesTmdbId: Int? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null
): Boolean {
    if (segment == null) return false
    val source = IntroSegmentSource.fromStorageKey(segment.source)
    if (source == IntroSegmentSource.UNAVAILABLE) return false
    if (seriesTmdbId != null && segment.seriesTmdbId != seriesTmdbId) return false
    if (seasonNumber != null && segment.seasonNumber != seasonNumber) return false
    if (episodeNumber != null && segment.episodeNumber != episodeNumber) return false
    if (segment.introStartMs < 0L) return false
    if (segment.introEndMs <= segment.introStartMs + 3_000L) return false
    if (durationMs > 0L && segment.introEndMs > durationMs) return false
    return positionMs in segment.introStartMs until segment.introEndMs
}

/**
 * Formatta la risoluzione video reale dello stream ExoPlayer in formato compatto leggibile
 * (es. `4K`, `1440p`, `1080p`, `720p`, `480p`, `360p`), oppure `null` se la traccia video non ha ancora esposto dimensioni valide.
 */
fun formatVideoResolutionLabel(width: Int, height: Int): String? {
    if (width <= 0 && height <= 0) return null
    val effectiveHeight = when {
        height > 0 && width > 0 -> minOf(width, height)
        height > 0 -> height
        else -> (width * 9) / 16
    }
    val effectiveWidth = maxOf(width, height)
    return when {
        effectiveHeight >= 2160 || effectiveWidth >= 3840 -> "4K"
        effectiveHeight >= 1440 || effectiveWidth >= 2560 -> "1440p"
        effectiveHeight >= 1080 || effectiveWidth >= 1920 -> "1080p"
        effectiveHeight >= 720 || effectiveWidth >= 1280 -> "720p"
        effectiveHeight >= 540 -> "540p"
        effectiveHeight >= 480 || effectiveWidth >= 854 -> "480p"
        effectiveHeight >= 360 || effectiveWidth >= 640 -> "360p"
        effectiveHeight >= 240 -> "240p"
        effectiveHeight > 0 -> "${effectiveHeight}p"
        else -> null
    }
}

/**
 * Risolve il punto di inizio dei titoli di coda distinguendo esplicitamente tra:
 * 1) Marker affidabili in database locale / metadati (`VERIFIED_METADATA` / `LOCAL_DB`)
 * 2) Soglia di fallback in prossimità della fine (`FALLBACK_THRESHOLD`) solo per contenuti di durata adeguata.
 * Non segna mai automaticamente completato il contenuto.
 */
fun resolveCreditsTrigger(
    segment: CreditsSegmentEntity?,
    durationMs: Long,
    isSeries: Boolean,
    creditsDetectionEnabled: Boolean
): ResolvedCreditsTrigger? {
    if (!creditsDetectionEnabled) return null

    if (segment != null) {
        val state = CreditsDetectionState.fromStorageKey(segment.detectionState)
        if (state != CreditsDetectionState.UNAVAILABLE && segment.creditsStartMs > 0L) {
            val validAgainstDuration = durationMs <= 0L || segment.creditsStartMs < durationMs
            if (validAgainstDuration) {
                return ResolvedCreditsTrigger(
                    creditsStartMs = segment.creditsStartMs,
                    source = state
                )
            }
        }
    }

    // Fallback prudenziale vicino alla fine reale (solo per durate >= 2 minuti per evitare falsi positivi)
    if (durationMs < 120_000L) return null
    val fallbackStart = if (isSeries) {
        maxOf((durationMs * 0.94).toLong(), durationMs - 45_000L)
            .coerceAtMost(durationMs - 15_000L)
    } else {
        maxOf((durationMs * 0.95).toLong(), durationMs - 60_000L)
            .coerceAtMost(durationMs - 15_000L)
    }
    if (fallbackStart <= 0L || fallbackStart >= durationMs) return null
    return ResolvedCreditsTrigger(
        creditsStartMs = fallbackStart,
        source = CreditsDetectionState.FALLBACK_THRESHOLD
    )
}
