package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WatchProgressEntity::class,
        EpisodeProgressEntity::class,
        WatchlistEntity::class,
        CatalogSnapshotEntity::class,
        MediaDetailsCacheEntity::class,
        SeasonEpisodesCacheEntity::class,
        ResolvedStreamCacheEntity::class
    ],
    version = 2,
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

        fun getInstance(context: Context): VixFlixDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VixFlixDatabase::class.java,
                    "vixflix_stream.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
