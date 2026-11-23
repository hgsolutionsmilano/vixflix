package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["createdAtMs"])]
)
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarId: String,
    val accentColorHex: Long,
    val isPrimary: Boolean = false,
    val preferredLanguage: String = "it",
    val preferredAudioLanguage: String = "it",
    val subtitlesEnabled: Boolean = false,
    val preferredSubtitleLanguage: String = "it",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "watch_progress",
    primaryKeys = ["profileId", "id"],
    indices = [
        Index(value = ["profileId", "updatedAt"]),
        Index(value = ["updatedAt"])
    ]
)
data class WatchProgressEntity(
    val id: String, // "movie_{tmdbId}" or "tv_{tmdbId}"
    val tmdbId: Int,
    val mediaType: String, // "movie" or "tv"
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseYear: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val profileId: String = DEFAULT_PRIMARY_PROFILE_ID
) {
    val startSeconds: Long
        get() = (positionMs / 1000L).coerceAtLeast(0L)

    val startMinutes: Int
        get() = (startSeconds / 60L).toInt()

    val progressFraction: Float
        get() = when {
            positionMs <= 0L -> 0f
            durationMs > 0L -> (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.02f, 0.99f)
            else -> 0.1f
        }

    val completionPercent: Int
        get() = if (positionMs <= 0L) 0 else (progressFraction * 100f).toInt().coerceIn(1, 99)

    val remainingMinutes: Int
        get() = if (durationMs > positionMs) {
            (((durationMs - positionMs) / 1000L) / 60L).toInt().coerceAtLeast(1)
        } else {
            0
        }
}

@Immutable
@Entity(
    tableName = "episode_progress",
    primaryKeys = ["profileId", "episodeKey"],
    indices = [
        Index(value = ["profileId", "tmdbId", "seasonNumber"]),
        Index(value = ["tmdbId", "seasonNumber"])
    ]
)
data class EpisodeProgressEntity(
    val episodeKey: String, // "tv_{tmdbId}_s{season}_e{episode}"
    val tmdbId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val profileId: String = DEFAULT_PRIMARY_PROFILE_ID
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val startSeconds: Long
        get() = (positionMs / 1000L).coerceAtLeast(0L)
}

@Immutable
@Entity(
    tableName = "my_list",
    primaryKeys = ["profileId", "id"],
    indices = [
        Index(value = ["profileId", "addedAt"]),
        Index(value = ["addedAt"])
    ]
)
data class WatchlistEntity(
    val id: String, // "{mediaType}_{tmdbId}"
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseYear: String,
    val addedAt: Long = System.currentTimeMillis(),
    val profileId: String = DEFAULT_PRIMARY_PROFILE_ID
)

@Immutable
@Entity(tableName = "catalog_snapshot_cache")
data class CatalogSnapshotEntity(
    @PrimaryKey val id: String = "home_feed_v1",
    val feedJson: String,
    val movieGenresJson: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "media_details_cache",
    indices = [Index(value = ["lastAccessedAtMs"])]
)
data class MediaDetailsCacheEntity(
    @PrimaryKey val cacheKey: String, // "{mediaType}_{tmdbId}"
    val mediaType: String,
    val tmdbId: Int,
    val detailsJson: String,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val lastAccessedAtMs: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "season_episodes_cache",
    indices = [Index(value = ["lastAccessedAtMs"])]
)
data class SeasonEpisodesCacheEntity(
    @PrimaryKey val cacheKey: String, // "tv_{tvId}_s{seasonNumber}"
    val tvId: Int,
    val seasonNumber: Int,
    val episodesJson: String,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val lastAccessedAtMs: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "stream_link_cache",
    indices = [
        Index(value = ["expiresAtMs"]),
        Index(value = ["lastUsedAtMs"])
    ]
)
data class ResolvedStreamCacheEntity(
    @PrimaryKey val cacheKey: String, // "{mediaType}_{tmdbId}_{seasonOr0}_{episodeOr0}_{lang}"
    val tmdbId: Int,
    val mediaType: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val lang: String,
    val streamUrl: String,
    val embedUrl: String,
    val referer: String,
    val userAgent: String,
    val startSeconds: Long,
    val startMinutes: Int,
    val subtitlesJson: String,
    val expiresAtMs: Long,
    val verifiedAtMs: Long = System.currentTimeMillis(),
    val lastUsedAtMs: Long = System.currentTimeMillis()
)

@Immutable
data class CacheStatsSummary(
    val catalogUpdatedAtMs: Long? = null,
    val cachedDetailsCount: Int = 0,
    val cachedSeasonsCount: Int = 0,
    val validStreamLinksCount: Int = 0,
    val imageCacheSizeBytes: Long = 0L
)

/**
 * Intervallo intro affidabile per uno specifico episodio di serie TV.
 * Ogni episodio conserva il proprio intervallo reale (mai stimato o uguale per tutti).
 */
@Immutable
@Entity(
    tableName = "intro_segments",
    indices = [
        Index(value = ["seriesTmdbId", "seasonNumber", "episodeNumber"])
    ]
)
data class IntroSegmentEntity(
    @PrimaryKey val episodeKey: String, // "tv_{seriesTmdbId}_s{seasonNumber}_e{episodeNumber}"
    val seriesTmdbId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val introStartMs: Long,
    val introEndMs: Long,
    val source: String = IntroSegmentSource.LOCAL_DB.storageKey,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Dati sui titoli di coda per film o episodi di serie TV, con stato esplicito dell'affidabilità.
 */
@Immutable
@Entity(
    tableName = "credits_segments",
    indices = [
        Index(value = ["tmdbId", "mediaType"])
    ]
)
data class CreditsSegmentEntity(
    @PrimaryKey val contentKey: String, // "movie_{tmdbId}" oppure "tv_{tmdbId}_s{season}_e{episode}"
    val tmdbId: Int,
    val mediaType: String, // "movie" | "tv"
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val creditsStartMs: Long,
    val durationMs: Long = 0L,
    val detectionState: String = CreditsDetectionState.LOCAL_DB.storageKey,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Stato di completamento persistente per singolo profilo utente (film o episodio completato).
 * Utilizzato esclusivamente per stato completamento e gestione Continua a guardare (nessuna cronologia).
 */
@Immutable
@Entity(
    tableName = "completed_media",
    primaryKeys = ["profileId", "contentKey"],
    indices = [
        Index(value = ["profileId", "tmdbId", "mediaType"])
    ]
)
data class CompletedMediaEntity(
    val profileId: String = DEFAULT_PRIMARY_PROFILE_ID,
    val contentKey: String, // "movie_{tmdbId}" oppure "tv_{tmdbId}_s{season}_e{episode}"
    val tmdbId: Int,
    val mediaType: String, // "movie" | "tv"
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val completedAtMs: Long = System.currentTimeMillis()
)


