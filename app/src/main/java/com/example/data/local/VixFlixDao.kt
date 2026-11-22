package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.simkl.SimklPendingScrobbleEntity
import com.example.data.local.simkl.SimklPlaybackBaselineEntity
import com.example.data.local.simkl.SimklProfileSyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VixFlixDao {
    // --- User Profiles ---
    @Query("SELECT * FROM user_profiles ORDER BY isPrimary DESC, createdAtMs ASC")
    fun observeAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles ORDER BY isPrimary DESC, createdAtMs ASC")
    suspend fun getAllProfiles(): List<UserProfileEntity>

    @Query("SELECT * FROM user_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: String): UserProfileEntity?

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun countProfiles(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: String)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun deleteWatchProgressForProfile(profileId: String)

    @Query("DELETE FROM episode_progress WHERE profileId = :profileId")
    suspend fun deleteEpisodeProgressForProfile(profileId: String)

    @Query("DELETE FROM my_list WHERE profileId = :profileId")
    suspend fun deleteWatchlistForProfile(profileId: String)

    @Query("DELETE FROM completed_media WHERE profileId = :profileId")
    suspend fun deleteCompletedMediaForProfile(profileId: String)

    @Query("DELETE FROM simkl_profile_sync_meta WHERE profileId = :profileId")
    suspend fun deleteSimklProfileMeta(profileId: String)

    @Query("DELETE FROM simkl_playback_baseline WHERE profileId = :profileId")
    suspend fun deleteSimklBaselinesForProfile(profileId: String)

    @Query("DELETE FROM simkl_pending_scrobble WHERE profileId = :profileId")
    suspend fun deleteSimklPendingForProfile(profileId: String)

    @Transaction
    suspend fun deleteProfileAndPersonalData(profileId: String) {
        deleteWatchProgressForProfile(profileId)
        deleteEpisodeProgressForProfile(profileId)
        deleteWatchlistForProfile(profileId)
        deleteCompletedMediaForProfile(profileId)
        deleteSimklPendingForProfile(profileId)
        deleteSimklBaselinesForProfile(profileId)
        deleteSimklProfileMeta(profileId)
        deleteProfileById(profileId)
    }

    // --- Profile-scoped Continue Watching & Playback Progress ---
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId ORDER BY updatedAt DESC")
    fun observeContinueWatching(profileId: String = DEFAULT_PRIMARY_PROFILE_ID): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun getWatchProgress(
        id: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchProgress(entity: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND id = :id")
    suspend fun deleteWatchProgress(
        id: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    )

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun clearAllWatchProgress(profileId: String = DEFAULT_PRIMARY_PROFILE_ID)

    @Query("SELECT * FROM episode_progress WHERE profileId = :profileId AND tmdbId = :tmdbId AND seasonNumber = :seasonNumber")
    fun observeSeasonProgress(
        tmdbId: Int,
        seasonNumber: Int,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): Flow<List<EpisodeProgressEntity>>

    @Query("SELECT * FROM episode_progress WHERE profileId = :profileId AND episodeKey = :episodeKey LIMIT 1")
    suspend fun getEpisodeProgress(
        episodeKey: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): EpisodeProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeProgress(entity: EpisodeProgressEntity)

    @Query("DELETE FROM episode_progress WHERE profileId = :profileId AND episodeKey = :episodeKey")
    suspend fun deleteEpisodeProgress(
        episodeKey: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    )

    // --- Profile-scoped Favorites / Watchlist ---
    @Query("SELECT * FROM my_list WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeWatchlist(profileId: String = DEFAULT_PRIMARY_PROFILE_ID): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(entity: WatchlistEntity)

    @Query("DELETE FROM my_list WHERE profileId = :profileId AND id = :id")
    suspend fun removeFromWatchlist(
        id: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    )

    @Query("SELECT EXISTS(SELECT 1 FROM my_list WHERE profileId = :profileId AND id = :id)")
    fun observeIsInWatchlist(
        id: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): Flow<Boolean>

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

    // --- Intro Segments (TV Series) ---
    @Query("SELECT * FROM intro_segments WHERE episodeKey = :episodeKey LIMIT 1")
    suspend fun getIntroSegment(episodeKey: String): IntroSegmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIntroSegment(entity: IntroSegmentEntity)

    @Query("DELETE FROM intro_segments WHERE episodeKey = :episodeKey")
    suspend fun deleteIntroSegment(episodeKey: String)

    // --- Credits Segments (Movies & TV Series) ---
    @Query("SELECT * FROM credits_segments WHERE contentKey = :contentKey LIMIT 1")
    suspend fun getCreditsSegment(contentKey: String): CreditsSegmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCreditsSegment(entity: CreditsSegmentEntity)

    @Query("DELETE FROM credits_segments WHERE contentKey = :contentKey")
    suspend fun deleteCreditsSegment(contentKey: String)

    // --- Per-Profile Completed Media State (NO viewing history) ---
    @Query("SELECT * FROM completed_media WHERE profileId = :profileId AND contentKey = :contentKey LIMIT 1")
    suspend fun getCompletedMedia(
        contentKey: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): CompletedMediaEntity?

    @Query(
        "SELECT DISTINCT episodeNumber FROM completed_media " +
            "WHERE profileId = :profileId AND tmdbId = :tmdbId AND mediaType = 'tv' " +
            "AND seasonNumber = :seasonNumber AND episodeNumber IS NOT NULL"
    )
    fun observeWatchedEpisodeNumbers(
        tmdbId: Int,
        seasonNumber: Int,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): Flow<List<Int>>

    @Query(
        "SELECT DISTINCT episodeNumber FROM completed_media " +
            "WHERE profileId = :profileId AND tmdbId = :tmdbId AND mediaType = 'tv' " +
            "AND seasonNumber = :seasonNumber AND episodeNumber IS NOT NULL"
    )
    suspend fun getWatchedEpisodeNumbers(
        tmdbId: Int,
        seasonNumber: Int,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): List<Int>

    @Query(
        "SELECT seasonNumber, COUNT(DISTINCT episodeNumber) AS watchedCount " +
            "FROM completed_media " +
            "WHERE profileId = :profileId AND tmdbId = :tmdbId AND mediaType = 'tv' " +
            "AND seasonNumber IS NOT NULL AND episodeNumber IS NOT NULL " +
            "GROUP BY seasonNumber"
    )
    fun observeWatchedCountsBySeason(
        tmdbId: Int,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): Flow<List<SeasonWatchedCount>>

    @Query(
        "SELECT seasonNumber, episodeNumber FROM completed_media " +
            "WHERE profileId = :profileId AND tmdbId = :tmdbId AND mediaType = 'tv' " +
            "AND seasonNumber IS NOT NULL AND episodeNumber IS NOT NULL"
    )
    suspend fun getCompletedEpisodesForSeries(
        tmdbId: Int,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    ): List<SeriesCompletedEpisodeTuple>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletedMedia(entity: CompletedMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletedMediaList(entities: List<CompletedMediaEntity>)

    @Query("DELETE FROM completed_media WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun deleteCompletedMedia(
        contentKey: String,
        profileId: String = DEFAULT_PRIMARY_PROFILE_ID
    )

    // --- Simkl Per-Profile Sync Metadata, Baselines & Offline Queue (NO tokens) ---
    @Query("SELECT * FROM simkl_profile_sync_meta WHERE profileId = :profileId LIMIT 1")
    fun observeSimklProfileMeta(profileId: String): Flow<SimklProfileSyncMetaEntity?>

    @Query("SELECT * FROM simkl_profile_sync_meta WHERE profileId = :profileId LIMIT 1")
    suspend fun getSimklProfileMeta(profileId: String): SimklProfileSyncMetaEntity?

    @Query("SELECT * FROM simkl_profile_sync_meta")
    suspend fun getAllSimklProfileMetas(): List<SimklProfileSyncMetaEntity>

    @Query("SELECT * FROM simkl_profile_sync_meta WHERE accountId = :accountId")
    suspend fun getSimklProfileMetasByAccountId(accountId: String): List<SimklProfileSyncMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSimklProfileMeta(entity: SimklProfileSyncMetaEntity)

    @Query("SELECT * FROM simkl_playback_baseline WHERE profileId = :profileId")
    suspend fun getSimklBaselinesForProfile(profileId: String): List<SimklPlaybackBaselineEntity>

    @Query("SELECT * FROM simkl_playback_baseline WHERE profileId = :profileId AND contentKey = :contentKey LIMIT 1")
    suspend fun getSimklBaseline(profileId: String, contentKey: String): SimklPlaybackBaselineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSimklBaseline(entity: SimklPlaybackBaselineEntity)

    @Query("DELETE FROM simkl_playback_baseline WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun deleteSimklBaseline(profileId: String, contentKey: String)

    @Query("SELECT COUNT(*) FROM simkl_pending_scrobble WHERE profileId = :profileId")
    fun observeSimklPendingCount(profileId: String): Flow<Int>

    @Query("SELECT * FROM simkl_pending_scrobble WHERE profileId = :profileId ORDER BY updatedAtMs ASC")
    suspend fun getSimklPendingScrobbles(profileId: String): List<SimklPendingScrobbleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSimklPendingScrobble(entity: SimklPendingScrobbleEntity)

    @Query("DELETE FROM simkl_pending_scrobble WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun deleteSimklPendingScrobble(profileId: String, contentKey: String)

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId ORDER BY updatedAt DESC")
    suspend fun getWatchProgressListForProfile(profileId: String): List<WatchProgressEntity>
}

data class SeasonWatchedCount(
    val seasonNumber: Int,
    val watchedCount: Int
)

data class SeriesCompletedEpisodeTuple(
    val seasonNumber: Int,
    val episodeNumber: Int
)
