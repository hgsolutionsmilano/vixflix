package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VixFlixDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    fun observeContinueWatching(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE id = :id LIMIT 1")
    suspend fun getWatchProgress(id: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchProgress(entity: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE id = :id")
    suspend fun deleteWatchProgress(id: String)

    @Query("DELETE FROM watch_progress")
    suspend fun clearAllWatchProgress()

    @Query("SELECT * FROM episode_progress WHERE tmdbId = :tmdbId AND seasonNumber = :seasonNumber")
    fun observeSeasonProgress(tmdbId: Int, seasonNumber: Int): Flow<List<EpisodeProgressEntity>>

    @Query("SELECT * FROM episode_progress WHERE episodeKey = :episodeKey LIMIT 1")
    suspend fun getEpisodeProgress(episodeKey: String): EpisodeProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeProgress(entity: EpisodeProgressEntity)

    @Query("SELECT * FROM my_list ORDER BY addedAt DESC")
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(entity: WatchlistEntity)

    @Query("DELETE FROM my_list WHERE id = :id")
    suspend fun removeFromWatchlist(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM my_list WHERE id = :id)")
    fun observeIsInWatchlist(id: String): Flow<Boolean>
}
