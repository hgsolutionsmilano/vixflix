package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "watch_progress",
    indices = [Index(value = ["updatedAt"])]
)
data class WatchProgressEntity(
    @PrimaryKey val id: String, // "movie_{tmdbId}" or "tv_{tmdbId}"
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
    val updatedAt: Long = System.currentTimeMillis()
) {
    val startSeconds: Long
        get() = (positionMs / 1000L).coerceAtLeast(0L)

    val startMinutes: Int
        get() = (startSeconds / 60L).toInt()

    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.02f, 0.99f)
        } else {
            0.1f
        }

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
    indices = [Index(value = ["tmdbId", "seasonNumber"])]
)
data class EpisodeProgressEntity(
    @PrimaryKey val episodeKey: String, // "tv_{tmdbId}_s{season}_e{episode}"
    val tmdbId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
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
    indices = [Index(value = ["addedAt"])]
)
data class WatchlistEntity(
    @PrimaryKey val id: String, // "{mediaType}_{tmdbId}"
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseYear: String,
    val addedAt: Long = System.currentTimeMillis()
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

