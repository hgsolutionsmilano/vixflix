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

    // --- Catalog Snapshot Cache ---
    @Query("SELECT * FROM catalog_snapshot_cache WHERE id = :id LIMIT 1")
    suspend fun getCatalogSnapshot(id: String = "home_feed_v1"): CatalogSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogSnapshot(entity: CatalogSnapshotEntity)

    @Query("DELETE FROM catalog_snapshot_cache")
    suspend fun clearCatalogSnapshot()

    // --- Media Details LRU Cache ---
    @Query("SELECT * FROM media_details_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getMediaDetailsCache(cacheKey: String): MediaDetailsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaDetailsCache(entity: MediaDetailsCacheEntity)

    @Query("UPDATE media_details_cache SET lastAccessedAtMs = :accessedAtMs WHERE cacheKey = :cacheKey")
    suspend fun touchMediaDetailsCache(cacheKey: String, accessedAtMs: Long)

    @Query(
        "DELETE FROM media_details_cache WHERE cacheKey NOT IN " +
            "(SELECT cacheKey FROM media_details_cache ORDER BY lastAccessedAtMs DESC LIMIT :maxCount)"
    )
    suspend fun trimMediaDetailsCache(maxCount: Int = 60)

    @Query("SELECT COUNT(*) FROM media_details_cache")
    suspend fun countMediaDetailsCache(): Int

    @Query("DELETE FROM media_details_cache")
    suspend fun clearMediaDetailsCache()

    // --- Season Episodes LRU Cache ---
    @Query("SELECT * FROM season_episodes_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getSeasonEpisodesCache(cacheKey: String): SeasonEpisodesCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasonEpisodesCache(entity: SeasonEpisodesCacheEntity)

    @Query("UPDATE season_episodes_cache SET lastAccessedAtMs = :accessedAtMs WHERE cacheKey = :cacheKey")
    suspend fun touchSeasonEpisodesCache(cacheKey: String, accessedAtMs: Long)

    @Query(
        "DELETE FROM season_episodes_cache WHERE cacheKey NOT IN " +
            "(SELECT cacheKey FROM season_episodes_cache ORDER BY lastAccessedAtMs DESC LIMIT :maxCount)"
    )
    suspend fun trimSeasonEpisodesCache(maxCount: Int = 40)

    @Query("SELECT COUNT(*) FROM season_episodes_cache")
    suspend fun countSeasonEpisodesCache(): Int

    @Query("DELETE FROM season_episodes_cache")
    suspend fun clearSeasonEpisodesCache()

    // --- Valid VixSrc Stream Link Cache ---
    @Query("SELECT * FROM stream_link_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getStreamCache(cacheKey: String): ResolvedStreamCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreamCache(entity: ResolvedStreamCacheEntity)

    @Query("UPDATE stream_link_cache SET lastUsedAtMs = :usedAtMs WHERE cacheKey = :cacheKey")
    suspend fun touchStreamCache(cacheKey: String, usedAtMs: Long)

    @Query("DELETE FROM stream_link_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteStreamCache(cacheKey: String)

    @Query("DELETE FROM stream_link_cache WHERE expiresAtMs <= :nowMs")
    suspend fun deleteExpiredStreamCaches(nowMs: Long)

    @Query(
        "DELETE FROM stream_link_cache WHERE cacheKey NOT IN " +
            "(SELECT cacheKey FROM stream_link_cache ORDER BY lastUsedAtMs DESC LIMIT :maxCount)"
    )
    suspend fun trimStreamCaches(maxCount: Int = 40)

    @Query("SELECT COUNT(*) FROM stream_link_cache WHERE expiresAtMs > :nowMs")
    suspend fun countValidStreamCaches(nowMs: Long): Int

    @Query("DELETE FROM stream_link_cache")
    suspend fun clearStreamCaches()
}
