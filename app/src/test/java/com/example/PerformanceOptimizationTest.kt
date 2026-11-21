package com.example

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.VixFlixDatabase
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.SubtitleTrack
import com.example.data.remote.TmdbCastMemberDto
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbMediaItemDto
import com.example.data.remote.TmdbPagedResponse
import com.example.data.remote.TmdbSeasonSummaryDto
import com.example.data.remote.TmdbWatchProviderDto
import com.example.data.remote.TmdbWatchProvidersResponse
import com.example.data.remote.VixSrcStreamResolver
import com.example.data.repository.CatalogFeed
import com.example.data.repository.VixFlixRepository
import com.example.ui.screens.toExoLoadControl
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceOptimizationTest {

    @Test
    fun `zero reflection Moshi codegen serializes and deserializes all core models without KotlinJsonAdapterFactory`() {
        // Verifica che Moshi.Builder().build() puro usi esclusivamente gli adapter generati da KSP
        val moshi = Moshi.Builder().build()

        val sampleItem = MediaItem(
            id = 550,
            mediaType = "movie",
            title = "Fight Club",
            overview = "An insomniac office worker forms an underground club.",
            posterPath = "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
            backdropPath = "/hZkgoQYus5vegHoetLkCJzb17zJ.jpg",
            voteAverage = 8.4,
            releaseYear = "1999",
            genreIds = listOf(18, 53)
        )
        val feed = CatalogFeed(
            heroBanner = sampleItem,
            top10Today = listOf(sampleItem),
            trendingMovies = listOf(sampleItem),
            trendingTv = emptyList(),
            popularMovies = listOf(sampleItem),
            popularTv = emptyList(),
            nowPlayingMovies = listOf(sampleItem),
            topRatedMovies = listOf(sampleItem),
            actionMovies = listOf(sampleItem),
            sciFiMovies = emptyList()
        )
        val feedAdapter = moshi.adapter(CatalogFeed::class.java)
        val feedJson = feedAdapter.toJson(feed)
        val decodedFeed = feedAdapter.fromJson(feedJson)
        assertNotNull(decodedFeed)
        assertEquals("Fight Club", decodedFeed?.heroBanner?.title)
        assertEquals(listOf(18, 53), decodedFeed?.heroBanner?.genreIds)

        val details = MediaDetails(
            item = sampleItem,
            tagline = "Mischief. Mayhem. Soap.",
            runtimeMinutes = 139,
            genres = listOf("Dramma", "Thriller"),
            cast = listOf(TmdbCastMemberDto(819, "Edward Norton", "The Narrator", "/5XBzD5WuTyVQZeS4VI25z2moMeY.jpg")),
            similar = listOf(sampleItem),
            seasons = listOf(TmdbSeasonSummaryDto(1, 1, "Stagione 1", 10, null, "2020-01-01")),
            numberOfSeasons = 1,
            trailers = emptyList()
        )
        val detailsAdapter = moshi.adapter(MediaDetails::class.java)
        val decodedDetails = detailsAdapter.fromJson(detailsAdapter.toJson(details))
        assertNotNull(decodedDetails)
        assertEquals(139, decodedDetails?.runtimeMinutes)
        assertEquals("Edward Norton", decodedDetails?.cast?.firstOrNull()?.name)
        assertEquals(listOf("Dramma", "Thriller"), decodedDetails?.genres)

        val episodeAdapter = moshi.adapter(TmdbEpisodeDto::class.java)
        val episode = TmdbEpisodeDto(1, 1, 1, "Pilot", "Intro", null, 45, "2020-01-01", 8.2)
        assertEquals("Pilot", episodeAdapter.fromJson(episodeAdapter.toJson(episode))?.name)

        val pagedType = Types.newParameterizedType(TmdbPagedResponse::class.java, TmdbMediaItemDto::class.java)
        val pagedAdapter = moshi.adapter<TmdbPagedResponse<TmdbMediaItemDto>>(pagedType)
        val pagedJson = """{"page":1,"results":[{"id":101,"title":"Matrix","media_type":"movie","vote_average":8.7,"release_date":"1999-03-31","genre_ids":[28,878]}]}"""
        val decodedPaged = pagedAdapter.fromJson(pagedJson)
        assertEquals(1, decodedPaged?.results?.size)
        assertEquals("Matrix", decodedPaged?.results?.first()?.title)

        val subAdapter = moshi.adapter(SubtitleTrack::class.java)
        val sub = SubtitleTrack("Italiano", "it", "https://vixsrc.to/sub.vtt")
        assertEquals(sub, subAdapter.fromJson(subAdapter.toJson(sub)))

        val providerRespAdapter = moshi.adapter(TmdbWatchProvidersResponse::class.java)
        val providerResp = TmdbWatchProvidersResponse(
            listOf(TmdbWatchProviderDto(8, "Netflix", "/t2yyOv40HZeVlLjYsCsPHnWLk4W.jpg", 1))
        )
        assertEquals(8, providerRespAdapter.fromJson(providerRespAdapter.toJson(providerResp))?.results?.first()?.providerId)
    }

    @Test
    fun `migration 2 to 3 adds all SQLite performance indices while preserving existing data`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_2_to_3_perf_test.db"
        context.deleteDatabase(dbName)

        val v2Callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watch_progress` (
                        `id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `overview` TEXT NOT NULL, `posterPath` TEXT,
                        `backdropPath` TEXT, `voteAverage` REAL NOT NULL, `releaseYear` TEXT NOT NULL,
                        `seasonNumber` INTEGER, `episodeNumber` INTEGER, `episodeTitle` TEXT,
                        `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episode_progress` (
                        `episodeKey` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL,
                        `episodeNumber` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`episodeKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `my_list` (
                        `id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `overview` TEXT NOT NULL, `posterPath` TEXT,
                        `backdropPath` TEXT, `voteAverage` REAL NOT NULL, `releaseYear` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalog_snapshot_cache` (
                        `id` TEXT NOT NULL, `feedJson` TEXT NOT NULL, `movieGenresJson` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_details_cache` (
                        `cacheKey` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL,
                        `detailsJson` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL,
                        `lastAccessedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `season_episodes_cache` (
                        `cacheKey` TEXT NOT NULL, `tvId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL,
                        `episodesJson` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL,
                        `lastAccessedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stream_link_cache` (
                        `cacheKey` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL,
                        `seasonNumber` INTEGER, `episodeNumber` INTEGER, `lang` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL, `embedUrl` TEXT NOT NULL, `referer` TEXT NOT NULL,
                        `userAgent` TEXT NOT NULL, `startSeconds` INTEGER NOT NULL,
                        `startMinutes` INTEGER NOT NULL, `subtitlesJson` TEXT NOT NULL,
                        `expiresAtMs` INTEGER NOT NULL, `verifiedAtMs` INTEGER NOT NULL,
                        `lastUsedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `watch_progress` VALUES (
                        'movie_550', 550, 'movie', 'Fight Club', 'Overview', '/p.jpg', '/b.jpg',
                        8.4, '1999', NULL, NULL, NULL, 120000, 3600000, 1700000000000
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(v2Callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val upgradedDb = Room.databaseBuilder(context, VixFlixDatabase::class.java, dbName)
            .addMigrations(
                VixFlixDatabase.MIGRATION_1_2,
                VixFlixDatabase.MIGRATION_2_3,
                VixFlixDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

        try {
            val existing = upgradedDb.vixFlixDao().getWatchProgress("movie_550")
            assertNotNull(existing)
            assertEquals("Fight Club", existing?.title)

            val sqlite = upgradedDb.openHelper.readableDatabase
            val indexNames = mutableSetOf<String>()
            sqlite.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { cursor ->
                while (cursor.moveToNext()) {
                    indexNames.add(cursor.getString(0))
                }
            }
            val expectedIndices = setOf(
                "index_watch_progress_updatedAt",
                "index_episode_progress_tmdbId_seasonNumber",
                "index_my_list_addedAt",
                "index_media_details_cache_lastAccessedAtMs",
                "index_season_episodes_cache_lastAccessedAtMs",
                "index_stream_link_cache_expiresAtMs",
                "index_stream_link_cache_lastUsedAtMs"
            )
            assertTrue("Missing indices: ${expectedIndices - indexNames}", indexNames.containsAll(expectedIndices))
        } finally {
            upgradedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @OptIn(UnstableApi::class)
    @Test
    fun `streaming buffer presets build valid LoadControl on both standard and low RAM Fire TV devices`() {
        StreamingBufferPreset.entries.forEach { preset ->
            val standardControl = preset.toExoLoadControl(isLowRamDevice = false)
            val lowRamControl = preset.toExoLoadControl(isLowRamDevice = true)
            assertNotNull(standardControl)
            assertNotNull(lowRamControl)
        }
    }

    @Test
    fun `precompiled VixSrcStreamResolver regex parses master playlist and expires timestamp`() {
        val resolver = VixSrcStreamResolver(baseOkHttpClient = OkHttpClient())
        val html = """
            <script>
            window.masterPlaylist = {
                params: { 'token': 'abc123token', 'expires': '1893456000' },
                url: 'https://vixsrc.to/playlist/550?type=video'
            };
            window.canPlayFHD = true;
            </script>
        """.trimIndent()

        val parsedUrl = resolver.parseMasterPlaylistFromHtml(html, "https://vixsrc.to/movie/550?lang=it", "it")
        assertNotNull(parsedUrl)
        assertTrue(parsedUrl!!.contains("token=abc123token"))
        assertTrue(parsedUrl.contains("expires=1893456000"))
        assertTrue(parsedUrl.contains("h=1"))

        val safeExp = resolver.computeSafeExpiresAtMs(parsedUrl, nowMs = 1_700_000_000_000L)
        assertTrue(safeExp > 1_700_000_000_000L)
    }

    @Test
    fun `repository trimMemoryCaches clears bounded caches cleanly under low memory pressure`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = VixFlixRepository(dao = db.vixFlixDao(), httpCacheDir = context.cacheDir)
            repo.trimMemoryCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            assertNull(repo.getCachedMoviesByGenre(28))
            assertNull(repo.getCachedPlatformCatalog(8))
        } finally {
            db.close()
        }
    }
}
