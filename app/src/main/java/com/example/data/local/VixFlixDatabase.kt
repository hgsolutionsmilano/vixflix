package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.example.data.local.simkl.SimklEpisodeStateEntity
import com.example.data.local.simkl.SimklLibraryItemEntity
import com.example.data.local.simkl.SimklPendingScrobbleEntity
import com.example.data.local.simkl.SimklPlaybackBaselineEntity
import com.example.data.local.simkl.SimklProfileSyncMetaEntity

@Database(
    entities = [
        UserProfileEntity::class,
        WatchProgressEntity::class,
        EpisodeProgressEntity::class,
        WatchlistEntity::class,
        CatalogSnapshotEntity::class,
        MediaDetailsCacheEntity::class,
        SeasonEpisodesCacheEntity::class,
        ResolvedStreamCacheEntity::class,
        IntroSegmentEntity::class,
        CreditsSegmentEntity::class,
        CompletedMediaEntity::class,
        SimklProfileSyncMetaEntity::class,
        SimklLibraryItemEntity::class,
        SimklEpisodeStateEntity::class,
        SimklPlaybackBaselineEntity::class,
        SimklPendingScrobbleEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class VixFlixDatabase : RoomDatabase() {
    abstract fun vixFlixDao(): VixFlixDao

    companion object {
        @Volatile
        private var INSTANCE: VixFlixDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalog_snapshot_cache` (
                        `id` TEXT NOT NULL,
                        `feedJson` TEXT NOT NULL,
                        `movieGenresJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_details_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `detailsJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `lastAccessedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `season_episodes_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `tvId` INTEGER NOT NULL,
                        `seasonNumber` INTEGER NOT NULL,
                        `episodesJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `lastAccessedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stream_link_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `lang` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `embedUrl` TEXT NOT NULL,
                        `referer` TEXT NOT NULL,
                        `userAgent` TEXT NOT NULL,
                        `startSeconds` INTEGER NOT NULL,
                        `startMinutes` INTEGER NOT NULL,
                        `subtitlesJson` TEXT NOT NULL,
                        `expiresAtMs` INTEGER NOT NULL,
                        `verifiedAtMs` INTEGER NOT NULL,
                        `lastUsedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_progress_updatedAt` ON `watch_progress` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_progress_tmdbId_seasonNumber` ON `episode_progress` (`tmdbId`, `seasonNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_my_list_addedAt` ON `my_list` (`addedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_details_cache_lastAccessedAtMs` ON `media_details_cache` (`lastAccessedAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_season_episodes_cache_lastAccessedAtMs` ON `season_episodes_cache` (`lastAccessedAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stream_link_cache_expiresAtMs` ON `stream_link_cache` (`expiresAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stream_link_cache_lastUsedAtMs` ON `stream_link_cache` (`lastUsedAtMs`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_profiles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `avatarId` TEXT NOT NULL,
                        `accentColorHex` INTEGER NOT NULL,
                        `isPrimary` INTEGER NOT NULL,
                        `preferredLanguage` TEXT NOT NULL,
                        `preferredAudioLanguage` TEXT NOT NULL,
                        `subtitlesEnabled` INTEGER NOT NULL,
                        `preferredSubtitleLanguage` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_createdAtMs` ON `user_profiles` (`createdAtMs`)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `user_profiles` (
                        `id`, `name`, `avatarId`, `accentColorHex`, `isPrimary`,
                        `preferredLanguage`, `preferredAudioLanguage`, `subtitlesEnabled`,
                        `preferredSubtitleLanguage`, `createdAtMs`, `updatedAtMs`
                    ) VALUES (
                        '$DEFAULT_PRIMARY_PROFILE_ID', 'Profilo Principale', 'cinema_crimson', 4293200148, 1,
                        'it', 'it', 0, 'it', $now, $now
                    )
                    """.trimIndent()
                )

                // Migrazione senza perdita di dati per watch_progress
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watch_progress_new` (
                        `id` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `overview` TEXT NOT NULL,
                        `posterPath` TEXT,
                        `backdropPath` TEXT,
                        `voteAverage` REAL NOT NULL,
                        `releaseYear` TEXT NOT NULL,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `episodeTitle` TEXT,
                        `positionMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `profileId` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `watch_progress_new` (
                        `id`, `tmdbId`, `mediaType`, `title`, `overview`, `posterPath`, `backdropPath`,
                        `voteAverage`, `releaseYear`, `seasonNumber`, `episodeNumber`, `episodeTitle`,
                        `positionMs`, `durationMs`, `updatedAt`, `profileId`
                    )
                    SELECT
                        `id`, `tmdbId`, `mediaType`, `title`, `overview`, `posterPath`, `backdropPath`,
                        `voteAverage`, `releaseYear`, `seasonNumber`, `episodeNumber`, `episodeTitle`,
                        `positionMs`, `durationMs`, `updatedAt`, '$DEFAULT_PRIMARY_PROFILE_ID'
                    FROM `watch_progress`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `watch_progress`")
                db.execSQL("ALTER TABLE `watch_progress_new` RENAME TO `watch_progress`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_progress_profileId_updatedAt` ON `watch_progress` (`profileId`, `updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_progress_updatedAt` ON `watch_progress` (`updatedAt`)")

                // Migrazione senza perdita di dati per episode_progress
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episode_progress_new` (
                        `episodeKey` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `seasonNumber` INTEGER NOT NULL,
                        `episodeNumber` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `profileId` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `episodeKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `episode_progress_new` (
                        `episodeKey`, `tmdbId`, `seasonNumber`, `episodeNumber`,
                        `positionMs`, `durationMs`, `updatedAt`, `profileId`
                    )
                    SELECT
                        `episodeKey`, `tmdbId`, `seasonNumber`, `episodeNumber`,
                        `positionMs`, `durationMs`, `updatedAt`, '$DEFAULT_PRIMARY_PROFILE_ID'
                    FROM `episode_progress`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `episode_progress`")
                db.execSQL("ALTER TABLE `episode_progress_new` RENAME TO `episode_progress`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_progress_profileId_tmdbId_seasonNumber` ON `episode_progress` (`profileId`, `tmdbId`, `seasonNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_progress_tmdbId_seasonNumber` ON `episode_progress` (`tmdbId`, `seasonNumber`)")

                // Migrazione senza perdita di dati per my_list (preferiti)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `my_list_new` (
                        `id` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `overview` TEXT NOT NULL,
                        `posterPath` TEXT,
                        `backdropPath` TEXT,
                        `voteAverage` REAL NOT NULL,
                        `releaseYear` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        `profileId` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `my_list_new` (
                        `id`, `tmdbId`, `mediaType`, `title`, `overview`, `posterPath`, `backdropPath`,
                        `voteAverage`, `releaseYear`, `addedAt`, `profileId`
                    )
                    SELECT
                        `id`, `tmdbId`, `mediaType`, `title`, `overview`, `posterPath`, `backdropPath`,
                        `voteAverage`, `releaseYear`, `addedAt`, '$DEFAULT_PRIMARY_PROFILE_ID'
                    FROM `my_list`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `my_list`")
                db.execSQL("ALTER TABLE `my_list_new` RENAME TO `my_list`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_my_list_profileId_addedAt` ON `my_list` (`profileId`, `addedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_my_list_addedAt` ON `my_list` (`addedAt`)")

                // Allinea le tabelle di cache effimere allo schema Room corrente
                db.execSQL("DROP TABLE IF EXISTS `catalog_snapshot_cache`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalog_snapshot_cache` (
                        `id` TEXT NOT NULL,
                        `feedJson` TEXT NOT NULL,
                        `movieGenresJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS `media_details_cache`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_details_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `detailsJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `lastAccessedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_details_cache_lastAccessedAtMs` ON `media_details_cache` (`lastAccessedAtMs`)")

                db.execSQL("DROP TABLE IF EXISTS `season_episodes_cache`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `season_episodes_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `tvId` INTEGER NOT NULL,
                        `seasonNumber` INTEGER NOT NULL,
                        `episodesJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `lastAccessedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_season_episodes_cache_lastAccessedAtMs` ON `season_episodes_cache` (`lastAccessedAtMs`)")

                db.execSQL("DROP TABLE IF EXISTS `stream_link_cache`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stream_link_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `lang` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `embedUrl` TEXT NOT NULL,
                        `referer` TEXT NOT NULL,
                        `userAgent` TEXT NOT NULL,
                        `startSeconds` INTEGER NOT NULL,
                        `startMinutes` INTEGER NOT NULL,
                        `subtitlesJson` TEXT NOT NULL,
                        `expiresAtMs` INTEGER NOT NULL,
                        `verifiedAtMs` INTEGER NOT NULL,
                        `lastUsedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stream_link_cache_expiresAtMs` ON `stream_link_cache` (`expiresAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stream_link_cache_lastUsedAtMs` ON `stream_link_cache` (`lastUsedAtMs`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `intro_segments` (
                        `episodeKey` TEXT NOT NULL,
                        `seriesTmdbId` INTEGER NOT NULL,
                        `seasonNumber` INTEGER NOT NULL,
                        `episodeNumber` INTEGER NOT NULL,
                        `introStartMs` INTEGER NOT NULL,
                        `introEndMs` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`episodeKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_intro_segments_seriesTmdbId_seasonNumber_episodeNumber` ON `intro_segments` (`seriesTmdbId`, `seasonNumber`, `episodeNumber`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `credits_segments` (
                        `contentKey` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `creditsStartMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `detectionState` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`contentKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_credits_segments_tmdbId_mediaType` ON `credits_segments` (`tmdbId`, `mediaType`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `completed_media` (
                        `profileId` TEXT NOT NULL,
                        `contentKey` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `completedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `contentKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_completed_media_profileId_tmdbId_mediaType` ON `completed_media` (`profileId`, `tmdbId`, `mediaType`)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `simkl_profile_sync_meta` (
                        `profileId` TEXT NOT NULL,
                        `connectionState` TEXT NOT NULL,
                        `accountDisplayName` TEXT,
                        `accountId` TEXT,
                        `lastActivitiesAll` TEXT,
                        `lastMoviesPlaybackTs` TEXT,
                        `lastShowsPlaybackTs` TEXT,
                        `lastMoviesWatchedTs` TEXT,
                        `lastShowsWatchedTs` TEXT,
                        `lastSyncAtMs` INTEGER,
                        `lastErrorMessage` TEXT,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_simkl_profile_sync_meta_updatedAtMs` ON `simkl_profile_sync_meta` (`updatedAtMs`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `simkl_playback_baseline` (
                        `profileId` TEXT NOT NULL,
                        `contentKey` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `simklPlaybackId` INTEGER,
                        `lastSyncedProgressPercent` REAL NOT NULL,
                        `lastRemotePausedAtMs` INTEGER NOT NULL,
                        `lastLocalUpdatedAtMs` INTEGER NOT NULL,
                        `localDirty` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `contentKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_simkl_playback_baseline_profileId_tmdbId_mediaType` ON `simkl_playback_baseline` (`profileId`, `tmdbId`, `mediaType`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_simkl_playback_baseline_profileId_localDirty` ON `simkl_playback_baseline` (`profileId`, `localDirty`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `simkl_pending_scrobble` (
                        `profileId` TEXT NOT NULL,
                        `contentKey` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `releaseYear` TEXT NOT NULL,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `action` TEXT NOT NULL,
                        `progressPercent` REAL NOT NULL,
                        `minProgress` INTEGER NOT NULL,
                        `simklPlaybackId` INTEGER,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `nextRetryAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `contentKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_simkl_pending_scrobble_profileId_nextRetryAtMs` ON `simkl_pending_scrobble` (`profileId`, `nextRetryAtMs`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_simkl_pending_scrobble_profileId_updatedAtMs` ON `simkl_pending_scrobble` (`profileId`, `updatedAtMs`)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `lastShowsAllTs` TEXT")
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `lastMoviesAllTs` TEXT")
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `lastAnimeAllTs` TEXT")
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `lastRemovedShowsTs` TEXT")
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `lastRemovedMoviesTs` TEXT")
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `lastRemovedAnimeTs` TEXT")
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `initialFullImportDone` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `simkl_library_item` (
                        `profileId` TEXT NOT NULL,
                        `stableKey` TEXT NOT NULL,
                        `simklId` INTEGER NOT NULL,
                        `mediaCategory` TEXT NOT NULL,
                        `tmdbMediaType` TEXT NOT NULL,
                        `tmdbId` INTEGER,
                        `imdbId` TEXT,
                        `tvdbId` INTEGER,
                        `slug` TEXT,
                        `title` TEXT NOT NULL,
                        `year` INTEGER,
                        `status` TEXT NOT NULL,
                        `watchedEpisodesCount` INTEGER NOT NULL,
                        `totalEpisodesCount` INTEGER NOT NULL,
                        `lastWatchedAt` TEXT,
                        `lastWatchedAtMs` INTEGER NOT NULL,
                        `nextToWatch` TEXT,
                        `lastWatchedEpisodeCode` TEXT,
                        `seasonsJson` TEXT,
                        `tmdbResolved` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `stableKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_simkl_library_item_profileId_simklId` ON `simkl_library_item` (`profileId`, `simklId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_simkl_library_item_profileId_mediaCategory_status` ON `simkl_library_item` (`profileId`, `mediaCategory`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_simkl_library_item_profileId_tmdbId` ON `simkl_library_item` (`profileId`, `tmdbId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `simkl_library_item` ADD COLUMN `posterPath` TEXT")
                db.execSQL("ALTER TABLE `simkl_library_item` ADD COLUMN `backdropPath` TEXT")
                db.execSQL("ALTER TABLE `simkl_library_item` ADD COLUMN `overview` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `simkl_library_item` ADD COLUMN `voteAverage` REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `simkl_pending_scrobble` ADD COLUMN `episodeTitle` TEXT")
                db.execSQL("ALTER TABLE `simkl_pending_scrobble` ADD COLUMN `simklId` INTEGER")
                db.execSQL("ALTER TABLE `simkl_pending_scrobble` ADD COLUMN `imdbId` TEXT")
                db.execSQL("ALTER TABLE `simkl_pending_scrobble` ADD COLUMN `tvdbId` INTEGER")
                db.execSQL("ALTER TABLE `simkl_pending_scrobble` ADD COLUMN `simklEpisodeId` INTEGER")
                db.execSQL("ALTER TABLE `simkl_pending_scrobble` ADD COLUMN `batchEpisodesJson` TEXT")
                db.execSQL("ALTER TABLE `simkl_pending_scrobble` ADD COLUMN `triggerEvent` TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `simkl_episode_state` (
                        `profileId` TEXT NOT NULL,
                        `seriesStableKey` TEXT NOT NULL,
                        `simklSeriesId` INTEGER NOT NULL,
                        `tmdbSeriesId` INTEGER,
                        `seasonNumber` INTEGER NOT NULL,
                        `episodeNumber` INTEGER NOT NULL,
                        `simklEpisodeId` INTEGER,
                        `watched` INTEGER NOT NULL,
                        `watchedAt` TEXT,
                        `watchedAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `seriesStableKey`, `seasonNumber`, `episodeNumber`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_simkl_episode_state_profileId_tmdbSeriesId` ON `simkl_episode_state` (`profileId`, `tmdbSeriesId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_simkl_episode_state_profileId_simklSeriesId` ON `simkl_episode_state` (`profileId`, `simklSeriesId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_simkl_episode_state_profileId_seriesStableKey` ON `simkl_episode_state` (`profileId`, `seriesStableKey`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `lastAnimePlaybackTs` TEXT")
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `lastAnimeWatchedTs` TEXT")
                db.execSQL("ALTER TABLE `simkl_profile_sync_meta` ADD COLUMN `lastFullReconcileAtMs` INTEGER")
            }
        }

        fun getInstance(context: Context): VixFlixDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VixFlixDatabase::class.java,
                    "vixflix_stream.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
