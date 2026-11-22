package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppPreferences
import com.example.data.local.CatalogSnapshotEntity
import com.example.data.local.ResolvedStreamCacheEntity
import com.example.data.local.VixFlixDao
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.VixSrcStreamResolver
import com.example.data.repository.VixFlixRepository
import com.example.ui.VixFlixViewModel
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CacheSystemTest {

    private lateinit var context: Context
    private lateinit var db: VixFlixDatabase
    private lateinit var dao: VixFlixDao
    private lateinit var server: MockBackendServer
    private lateinit var repository: VixFlixRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.vixFlixDao()

        server = MockBackendServer()
        server.start()

        val loopbackDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return if (hostname.equals("vixsrc.to", ignoreCase = true) ||
                    hostname.endsWith(".vixsrc.to", ignoreCase = true) ||
                    hostname.equals("api.themoviedb.org", ignoreCase = true)
                ) {
                    listOf(InetAddress.getLoopbackAddress())
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }

        val testClient = OkHttpClient.Builder()
            .dns(loopbackDns)
            .build()

        repository = VixFlixRepository(
            dao = dao,
            baseHttpClient = testClient,
            tmdbBaseUrl = "http://api.themoviedb.org:${server.port}/3/",
            vixBaseUrl = "http://vixsrc.to:${server.port}"
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.stop()
    }

    @Test
    fun `migration 1 to 2 preserves watch_progress and my_list while creating cache tables`() {
        val dbName = "migration_test_vixflix.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watch_progress` (
                        `id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `overview` TEXT NOT NULL, `posterPath` TEXT,
                        `backdropPath` TEXT, `voteAverage` REAL NOT NULL, `releaseYear` TEXT NOT NULL,
                        `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL,
                        `seasonNumber` INTEGER, `episodeNumber` INTEGER, `episodeTitle` TEXT,
                        `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episode_progress` (
                        `episodeKey` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL,
                        `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`episodeKey`)
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
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val v1Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        v1Helper.writableDatabase.use { v1Db ->
            v1Db.execSQL(
                """
                INSERT INTO `watch_progress`
                VALUES ('movie_120', 120, 'movie', 'Il Signore degli Anelli', 'Desc', '/p.jpg', '/b.jpg', 8.9, '2001', 120000, 600000, NULL, NULL, NULL, 1000)
                """.trimIndent()
            )
            v1Db.execSQL(
                """
                INSERT INTO `my_list`
                VALUES ('tv_1399', 1399, 'tv', 'Game of Thrones', 'Desc', '/p2.jpg', '/b2.jpg', 8.4, '2011', 2000)
                """.trimIndent()
            )
        }
        v1Helper.close()

        val upgradedDb = Room.databaseBuilder(context, VixFlixDatabase::class.java, dbName)
            .addMigrations(
                VixFlixDatabase.MIGRATION_1_2,
                VixFlixDatabase.MIGRATION_2_3,
                VixFlixDatabase.MIGRATION_3_4,
                VixFlixDatabase.MIGRATION_4_5,
                VixFlixDatabase.MIGRATION_5_6,
                VixFlixDatabase.MIGRATION_6_7
            )
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val migratedDao = upgradedDb.vixFlixDao()
            val cw = migratedDao.observeContinueWatching().first()
            val wl = migratedDao.observeWatchlist().first()
            assertEquals(1, cw.size)
            assertEquals("Il Signore degli Anelli", cw.first().title)
            assertEquals(1, wl.size)
            assertEquals("Game of Thrones", wl.first().title)

            migratedDao.upsertStreamCache(
                ResolvedStreamCacheEntity(
                    cacheKey = "movie_120_0_0_it",
                    tmdbId = 120,
                    mediaType = "movie",
                    seasonNumber = null,
                    episodeNumber = null,
                    lang = "it",
                    streamUrl = "http://vixsrc.to/playlist/170360",
                    embedUrl = "http://vixsrc.to/movie/120?lang=it",
                    referer = "http://vixsrc.to/",
                    userAgent = "UA",
                    startSeconds = 120L,
                    startMinutes = 2,
                    subtitlesJson = "[]",
                    expiresAtMs = System.currentTimeMillis() + 60_000L,
                    verifiedAtMs = System.currentTimeMillis(),
                    lastUsedAtMs = System.currentTimeMillis()
                )
            )
            assertEquals(1, migratedDao.countValidStreamCaches(System.currentTimeMillis()))
        }

        upgradedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `catalog snapshot supports stale-while-revalidate and clearAllLocalCaches preserves user lists`() = runBlocking {
        val sampleMovie = MediaItem(
            id = 120,
            mediaType = "movie",
            title = "Il Signore degli Anelli",
            overview = "Un anello",
            posterPath = "/lotr.jpg",
            backdropPath = "/lotr_bg.jpg",
            voteAverage = 8.9,
            releaseYear = "2001"
        )
        dao.upsertWatchProgress(
            WatchProgressEntity(
                id = "movie_120",
                tmdbId = 120,
                mediaType = "movie",
                title = sampleMovie.title,
                overview = sampleMovie.overview,
                posterPath = sampleMovie.posterPath,
                backdropPath = sampleMovie.backdropPath,
                voteAverage = 8.9,
                releaseYear = "2001",
                positionMs = 45_000L,
                durationMs = 180_000L
            )
        )
        dao.addToWatchlist(
            WatchlistEntity(
                id = "movie_120",
                tmdbId = 120,
                mediaType = "movie",
                title = sampleMovie.title,
                overview = sampleMovie.overview,
                posterPath = sampleMovie.posterPath,
                backdropPath = sampleMovie.backdropPath,
                voteAverage = 8.9,
                releaseYear = "2001"
            )
        )

        val liveResult = repository.fetchLiveCatalogFeed()
        assertTrue("Expected live catalog fetch to succeed: ${liveResult.exceptionOrNull()}", liveResult.isSuccess)

        val freshSnapshot = repository.getCachedCatalogFeed()
        assertNotNull(freshSnapshot)
        assertFalse("Fresh snapshot should not be marked stale", freshSnapshot!!.isStale)
        assertEquals(120, freshSnapshot.feed.heroBanner.id)

        // Simula snapshot vecchio di 20 minuti (> 15m freshness threshold)
        val scopedSnapshotId = "u${"http://api.themoviedb.org:${server.port}/3/".hashCode()}_home_feed_v1"
        val rawSnapshot = dao.getCatalogSnapshot(scopedSnapshotId)!!
        dao.upsertCatalogSnapshot(
            CatalogSnapshotEntity(
                id = rawSnapshot.id,
                feedJson = rawSnapshot.feedJson,
                movieGenresJson = rawSnapshot.movieGenresJson,
                updatedAtMs = System.currentTimeMillis() - (20L * 60L * 1000L)
            )
        )
        val staleSnapshot = repository.getCachedCatalogFeed()
        assertNotNull(staleSnapshot)
        assertTrue("20-minute-old snapshot should be returned immediately and flagged as stale", staleSnapshot!!.isStale)

        // Pulizia cache: elimina solo snapshot/cache ma preserva watch_progress e my_list
        var imageClearCalled = false
        repository.clearAllLocalCaches { imageClearCalled = true }

        assertTrue(imageClearCalled)
        assertEquals(null, repository.getCachedCatalogFeed())
        assertEquals(1, dao.observeContinueWatching().first().size)
        assertEquals(1, dao.observeWatchlist().first().size)
    }

    @Test
    fun `media details and season episodes round-trip cleanly via Moshi and Room cache`() = runBlocking {
        val tvItem = MediaItem(
            id = 1399,
            mediaType = "tv",
            title = "Game of Thrones",
            overview = "Serie test",
            posterPath = "/got.jpg",
            backdropPath = "/got_bg.jpg",
            voteAverage = 8.4,
            releaseYear = "2011"
        )

        val detailsRes = repository.fetchMediaDetails(tvItem)
        assertTrue("Expected TV details fetch success: ${detailsRes.exceptionOrNull()}", detailsRes.isSuccess)
        assertEquals(1, server.tvDetailCalls.get())

        val cachedDetails = repository.getCachedMediaDetails(tvItem)
        assertNotNull(cachedDetails)
        assertFalse(cachedDetails!!.isStale)
        assertEquals("Il trono", cachedDetails.value.tagline)
        assertEquals(1, cachedDetails.value.seasons.size)
        assertEquals(1, cachedDetails.value.cast.size)

        val episodesRes = repository.fetchSeasonEpisodes(1399, 1)
        assertTrue("Expected season episodes fetch success: ${episodesRes.exceptionOrNull()}", episodesRes.isSuccess)
        assertEquals(1, server.tvSeasonCalls.get())

        val cachedEpisodes = repository.getCachedSeasonEpisodes(1399, 1)
        assertNotNull(cachedEpisodes)
        assertEquals(2, cachedEpisodes!!.value.size)
        assertEquals("L'inverno sta arrivando", cachedEpisodes.value.first().name)

        val stats = repository.getCacheStatsSummary(imageCacheBytes = 4096L)
        assertEquals(1, stats.cachedDetailsCount)
        assertEquals(1, stats.cachedSeasonsCount)
        assertEquals(4096L, stats.imageCacheSizeBytes)
    }

    @Test
    fun `resolveVixSrcStream caches valid future link, respects expiry, and refreshes on invalidation`() = runBlocking {
        val futureEpochSeconds = (System.currentTimeMillis() / 1000L) + 3600L
        server.vixExpiresValue.set(futureEpochSeconds)

        val movie = MediaItem(
            id = 120,
            mediaType = "movie",
            title = "LOTR",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.9,
            releaseYear = "2001"
        )

        val first = repository.resolveVixSrcStream(movie, season = null, episode = null, startPositionMs = 60_000L, lang = "it")
        assertTrue("First resolution must succeed: ${first.exceptionOrNull()}", first.isSuccess)
        assertFalse(first.getOrThrow().fromCache)
        assertEquals(1, server.vixApiCalls.get())

        // Seconda chiamata: deve usare la cache senza colpire HTTP /api/movie/120
        val second = repository.resolveVixSrcStream(movie, season = null, episode = null, startPositionMs = 120_000L, lang = "it")
        assertTrue(second.isSuccess)
        assertTrue("Second resolution should hit cache", second.getOrThrow().fromCache)
        assertEquals(120L, second.getOrThrow().startSeconds)
        assertEquals(1, server.vixApiCalls.get())

        // Invalidazione esplicita (es. errore 403/playback sul link cached): forza nuova risoluzione
        repository.invalidateCachedStream(movie, season = null, episode = null, lang = "it")
        val third = repository.resolveVixSrcStream(movie, season = null, episode = null, startPositionMs = 120_000L, lang = "it")
        assertTrue(third.isSuccess)
        assertFalse(third.getOrThrow().fromCache)
        assertEquals(2, server.vixApiCalls.get())

        // Controllo fallback per valori sintetici/scaduti in computeSafeExpiresAtMs
        val now = System.currentTimeMillis()
        val resolver = VixSrcStreamResolver(OkHttpClient(), "http://vixsrc.to:${server.port}")
        val fallbackExpiry = resolver.computeSafeExpiresAtMs(
            "http://vixsrc.to/playlist/170360?token=tok&expires=9999999",
            nowMs = now
        )
        assertTrue("Synthetic non-future expires should fall back to ~20m", fallbackExpiry >= now + 19L * 60_000L)
    }

    @Test
    fun `fetchWatchProviders prioritizes Disney+, Paramount+, Prime Video, and Netflix with official TMDB logos and discovers titles`() = runBlocking {
        val providers = repository.fetchWatchProviders()
        assertTrue("Should return streaming platforms", providers.size >= 4)
        assertEquals(337, providers[0].providerId)
        assertEquals("Disney+", providers[0].displayName)
        assertTrue(providers[0].logoUrl?.contains("image.tmdb.org/t/p/w300") == true)

        assertEquals(531, providers[1].providerId)
        assertEquals("Paramount+", providers[1].displayName)

        assertEquals(119, providers[2].providerId)
        assertEquals("Prime Video", providers[2].displayName)

        assertEquals(8, providers[3].providerId)
        assertEquals("Netflix", providers[3].displayName)

        val catalogRes = repository.fetchTitlesByWatchProvider(providers[0])
        assertTrue("Platform catalog discovery must succeed: ${catalogRes.exceptionOrNull()}", catalogRes.isSuccess)
        val content = catalogRes.getOrThrow()
        assertEquals(337, content.provider.providerId)
        assertTrue(content.movies.isNotEmpty())
        assertTrue(content.series.isNotEmpty())
        assertTrue(content.allCombined.isNotEmpty())
    }

    @Test
    fun `VixFlixViewModel toggles background prefetch and increments cacheClearedFeedbackNonce`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = AppPreferences(app)
        prefs.setBackgroundPrefetch(true)

        val vm = VixFlixViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()

        vm.setBackgroundPrefetchEnabled(false)
        var toggled = false
        for (i in 0 until 40) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!vm.backgroundPrefetchEnabled.value) {
                toggled = true
                break
            }
            delay(30L)
        }
        assertTrue("Expected backgroundPrefetchEnabled to become false", toggled)

        val initialNonce = vm.cacheClearedFeedbackNonce.value
        vm.clearLocalCache()
        var cleared = false
        for (i in 0 until 40) {
            shadowOf(Looper.getMainLooper()).idle()
            if (vm.cacheClearedFeedbackNonce.value > initialNonce) {
                cleared = true
                break
            }
            delay(30L)
        }
        assertTrue("Expected clearLocalCache() to increment cacheClearedFeedbackNonce", cleared)
    }

    private class MockBackendServer {
        private var socket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()
        val vixApiCalls = AtomicInteger(0)
        val tvDetailCalls = AtomicInteger(0)
        val tvSeasonCalls = AtomicInteger(0)
        val vixExpiresValue = AtomicLong(9999999L)

        val port: Int
            get() = socket?.localPort ?: error("Server not started")

        fun start() {
            socket = ServerSocket(0, 32, InetAddress.getLoopbackAddress())
            val serverSocket = socket!!
            executor.submit {
                while (!serverSocket.isClosed) {
                    try {
                        val client = serverSocket.accept()
                        executor.submit { handleClient(client) }
                    } catch (_: SocketException) {
                        break
                    }
                }
            }
        }

        fun stop() {
            runCatching { socket?.close() }
            executor.shutdownNow()
        }

        private fun handleClient(client: Socket) {
            client.use { s ->
                val input = BufferedInputStream(s.getInputStream())
                val requestLine = readLine(input) ?: return
                while (true) {
                    val h = readLine(input) ?: break
                    if (h.isEmpty()) break
                }
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]

                when {
                    path.startsWith("/3/watch/providers/movie") ||
                        path.startsWith("/3/watch/providers/tv") -> {
                        val json = """
                            {
                              "results": [
                                {"provider_id": 8, "provider_name": "Netflix", "logo_path": "/pbpMk2JmcoNnQwx5JGpXngfoWtp.jpg", "display_priority": 1},
                                {"provider_id": 119, "provider_name": "Amazon Prime Video", "logo_path": "/pvske1MyAoymrs5bguRfVqYiM9a.jpg", "display_priority": 2},
                                {"provider_id": 337, "provider_name": "Disney Plus", "logo_path": "/97yvRBw1GzX7fXprcF80er19ot.jpg", "display_priority": 3},
                                {"provider_id": 531, "provider_name": "Paramount Plus", "logo_path": "/h5DcR0J2EESLitnhR8xLG1QymTE.jpg", "display_priority": 4}
                              ]
                            }
                        """.trimIndent()
                        writeResponse(s, 200, "application/json", json.toByteArray(), method == "HEAD")
                    }

                    path.startsWith("/3/discover/tv") -> {
                        val json = """
                            {
                              "page": 1,
                              "results": [
                                {
                                  "id": 82856,
                                  "media_type": "tv",
                                  "name": "The Mandalorian",
                                  "overview": "Star Wars series",
                                  "poster_path": "/mandalorian.jpg",
                                  "backdrop_path": "/mandalorian_bg.jpg",
                                  "vote_average": 8.5,
                                  "first_air_date": "2019-11-12"
                                }
                              ],
                              "total_pages": 1,
                              "total_results": 1
                            }
                        """.trimIndent()
                        writeResponse(s, 200, "application/json", json.toByteArray(), method == "HEAD")
                    }

                    // TMDB catalog list endpoints
                    path.startsWith("/3/trending/") ||
                        path.startsWith("/3/movie/popular") ||
                        path.startsWith("/3/tv/popular") ||
                        path.startsWith("/3/movie/now_playing") ||
                        path.startsWith("/3/movie/top_rated") ||
                        path.startsWith("/3/discover/movie") -> {
                        val json = """
                            {
                              "page": 1,
                              "results": [
                                {
                                  "id": 120,
                                  "media_type": "movie",
                                  "title": "Il Signore degli Anelli",
                                  "overview": "Epic",
                                  "poster_path": "/lotr.jpg",
                                  "backdrop_path": "/lotr_bg.jpg",
                                  "vote_average": 8.9,
                                  "release_date": "2001-12-19"
                                }
                              ],
                              "total_pages": 1,
                              "total_results": 1
                            }
                        """.trimIndent()
                        writeResponse(s, 200, "application/json", json.toByteArray(), method == "HEAD")
                    }

                    // TMDB TV Details
                    path.startsWith("/3/tv/1399/season/1") -> {
                        tvSeasonCalls.incrementAndGet()
                        val json = """
                            {
                              "id": 3624,
                              "season_number": 1,
                              "name": "Stagione 1",
                              "episodes": [
                                {"id": 63056, "season_number": 1, "episode_number": 1, "name": "L'inverno sta arrivando", "overview": "Ep 1", "runtime": 62},
                                {"id": 63057, "season_number": 1, "episode_number": 2, "name": "La strada del Re", "overview": "Ep 2", "runtime": 56}
                              ]
                            }
                        """.trimIndent()
                        writeResponse(s, 200, "application/json", json.toByteArray(), method == "HEAD")
                    }

                    path.startsWith("/3/tv/1399") -> {
                        tvDetailCalls.incrementAndGet()
                        val json = """
                            {
                              "id": 1399,
                              "name": "Game of Thrones",
                              "overview": "Serie",
                              "tagline": "Il trono",
                              "poster_path": "/got.jpg",
                              "backdrop_path": "/got_bg.jpg",
                              "vote_average": 8.4,
                              "first_air_date": "2011-04-17",
                              "number_of_seasons": 1,
                              "episode_run_time": [57],
                              "genres": [{"id": 18, "name": "Dramma"}],
                              "seasons": [{"id": 3624, "season_number": 1, "name": "Stagione 1", "episode_count": 2}],
                              "credits": {"cast": [{"id": 1, "name": "Sean Bean", "character": "Ned Stark"}]},
                              "similar": {"results": []},
                              "videos": {"results": [{"id": "v1", "key": "KPLWWIOCOOQ", "name": "Trailer", "site": "YouTube", "type": "Trailer", "official": true}]}
                            }
                        """.trimIndent()
                        writeResponse(s, 200, "application/json", json.toByteArray(), method == "HEAD")
                    }

                    // VixSrc endpoints (rewritten via /api/list/..%2f..%2f...)
                    path.contains("api%2fmovie%2f120") -> {
                        vixApiCalls.incrementAndGet()
                        writeResponse(s, 200, "application/json", """{"src":"/embed/170360?lang=it"}""".toByteArray(), method == "HEAD")
                    }

                    path.contains("embed%2f170360") -> {
                        val exp = vixExpiresValue.get()
                        val html = """
                            <html><script>
                            window.masterPlaylist = {
                                params: { 'token': 'tok_cached', 'expires': '$exp' },
                                url: 'http://vixsrc.to:$port/playlist/170360'
                            };
                            </script></html>
                        """.trimIndent()
                        writeResponse(s, 200, "text/html", html.toByteArray(), method == "HEAD")
                    }

                    else -> {
                        writeResponse(s, 404, "text/plain", "Not found: $path".toByteArray(), method == "HEAD")
                    }
                }
            }
        }

        private fun readLine(input: BufferedInputStream): String? {
            val buffer = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
                if (b == '\n'.code) return buffer.toString("UTF-8").trimEnd('\r')
                buffer.write(b)
            }
        }

        private fun writeResponse(socket: Socket, code: Int, contentType: String, body: ByteArray, headOnly: Boolean) {
            val statusText = if (code == 200) "OK" else "Not Found"
            val header = "HTTP/1.1 $code $statusText\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            val out = socket.getOutputStream()
            out.write(header.toByteArray(Charsets.UTF_8))
            if (!headOnly) out.write(body)
            out.flush()
        }
    }
}
