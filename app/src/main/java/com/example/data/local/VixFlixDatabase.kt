package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        WatchProgressEntity::class,
        EpisodeProgressEntity::class,
        WatchlistEntity::class,
        CatalogSnapshotEntity::class,
        MediaDetailsCacheEntity::class,
        SeasonEpisodesCacheEntity::class,
        ResolvedStreamCacheEntity::class
    ],
    version = 4,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
