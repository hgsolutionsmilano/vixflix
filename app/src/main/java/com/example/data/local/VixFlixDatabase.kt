package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchProgressEntity::class,
        EpisodeProgressEntity::class,
        WatchlistEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VixFlixDatabase : RoomDatabase() {
    abstract fun vixFlixDao(): VixFlixDao

    companion object {
        @Volatile
        private var INSTANCE: VixFlixDatabase? = null

        fun getInstance(context: Context): VixFlixDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VixFlixDatabase::class.java,
                    "vixflix_stream.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
