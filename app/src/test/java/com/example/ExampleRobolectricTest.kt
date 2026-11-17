package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VixFlix", appName)
    }

    @Test
    fun `room database persists continue watching timestamp and minutes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val dao = db.vixFlixDao()
        val entity = WatchProgressEntity(
            id = "movie_693134",
            tmdbId = 693134,
            mediaType = "movie",
            title = "Dune - Parte Due",
            overview = "Paul Atreides si unisce a Chani e ai Fremen.",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            voteAverage = 8.4,
            releaseYear = "2024",
            positionMs = 1_500_000L, // 25 minuti
            durationMs = 9_000_000L
        )
        dao.upsertWatchProgress(entity)

        val list = dao.observeContinueWatching().first()
        assertEquals(1, list.size)
        assertEquals(25, list.first().startMinutes)
        assertEquals(1500L, list.first().startSeconds)
        db.close()
    }
}
