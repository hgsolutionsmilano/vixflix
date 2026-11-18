package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "watch_progress")
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
@Entity(tableName = "episode_progress")
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
@Entity(tableName = "my_list")
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
