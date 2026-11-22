package com.example

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IntroSegmentEntity
import com.example.data.local.IntroSegmentSource
import com.example.data.local.PlayerAdvancedSettings
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.VerifiedNextEpisode
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.formatVideoResolutionLabel
import com.example.data.local.isIntroActiveAtPosition
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.TmdbCastMemberDto
import com.example.data.remote.VixSrcAvailabilityStatus
import com.example.data.remote.VixSrcStreamResolver
import com.example.data.repository.VixFlixRepository
import com.example.ui.DetailUiState
import com.example.ui.NavTab
import com.example.ui.PlaybackSession
import com.example.ui.components.ContinueWatchingSection
import com.example.ui.components.LocalVixSrcAvailabilityResolver
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.VixSrcAvailabilityResolver
import com.example.ui.screens.MediaDetailScreen
import com.example.ui.screens.VixPlayerScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch-dpad")
class UiVixSrcAndPlayerUpdatesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: FakeVixAndTmdbHttpServer
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = FakeVixAndTmdbHttpServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `req1 - Top and Nuovi tab is completely removed from NavTab entries`() {
        val tabNames = NavTab.entries.map { it.name }
        val tabLabels = NavTab.entries.map { it.label }

        assertFalse("NEW_POPULAR non deve più esistere in NavTab", "NEW_POPULAR" in tabNames)
        assertFalse("Nuovi & Popolari non deve più esistere", "Nuovi & Popolari" in tabLabels)
        assertFalse("Top & Nuovi non deve più esistere", "Top & Nuovi" in tabLabels)
        assertEquals(
            listOf("HOME", "SERIES", "MOVIES", "MY_LIST", "SETTINGS"),
            tabNames
        )
    }

    @Test
    fun `req2 - ContinueWatchingCard shows season-episode badge only on series and remaining minutes badge on top-right`() {
        val seriesEntry = WatchProgressEntity(
            id = "tv_101",
            tmdbId = 101,
            mediaType = "tv",
            title = "Stranger Things",
            posterPath = "/st_poster.jpg",
            backdropPath = "/st_backdrop.jpg",
            overview = "Serie sci-fi",
            voteAverage = 8.7,
            releaseYear = "2016",
            seasonNumber = 2,
            episodeNumber = 5,
            episodeTitle = "Dig Dug",
            positionMs = 15 * 60_000L, // 15 min
            durationMs = 50 * 60_000L  // 50 min -> 35 MIN RIMANENTI
        )
        val movieEntry = WatchProgressEntity(
            id = "movie_202",
            tmdbId = 202,
            mediaType = "movie",
            title = "Oppenheimer",
            posterPath = "/opp_poster.jpg",
            backdropPath = "/opp_backdrop.jpg",
            overview = "Film biografico",
            voteAverage = 8.5,
            releaseYear = "2023",
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
            positionMs = 90 * 60_000L, // 90 min
            durationMs = 102 * 60_000L // 102 min -> 12 MIN RIMANENTI
        )

        var entriesState by mutableStateOf(
            listOf(
                seriesEntry,
                movieEntry,
                movieEntry.copy(id = "movie_203", tmdbId = 203, title = "Dune", durationMs = 0L)
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                ContinueWatchingSection(
                    entries = entriesState,
                    isTvLayout = true,
                    onResumeClick = {},
                    onInfoClick = {},
                    onRemoveClick = {}
                )
            }
        }

        // 1. Serie TV: badge alto-sinistra "STAGIONE 2 · EPISODIO 5" e alto-destra "35 MIN RIMANENTI"
        composeRule.onNodeWithTag("cw_season_episode_badge_tv_101", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("STAGIONE 2 · EPISODIO 5").assertIsDisplayed()
        composeRule.onNodeWithTag("cw_remaining_minutes_badge_tv_101", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("35 MIN RIMANENTI").assertIsDisplayed()

        // 2. Film con durata nota: NESSUN badge stagione/episodio in alto a sinistra, ma "12 MIN RIMANENTI" in alto a destra
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("cw_season_episode_badge_movie_202", useUnmergedTree = true).fetchSemanticsNodes().size
        )
        composeRule.onNodeWithTag("cw_remaining_minutes_badge_movie_202", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("12 MIN RIMANENTI").assertIsDisplayed()

        // 3. Contenuto con durata non ancora disponibile (durationMs = 0L): badge minuti rimanenti nascosto
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("cw_remaining_minutes_badge_movie_203", useUnmergedTree = true).fetchSemanticsNodes().size
        )

        // Quando i metadati della durata reale vengono caricati (es. 115 min totali, 90 min guardati -> 25 MIN RIMANENTI), il badge compare automaticamente
        entriesState = listOf(
            seriesEntry,
            movieEntry,
            movieEntry.copy(id = "movie_203", tmdbId = 203, title = "Dune", durationMs = 115 * 60_000L)
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cw_remaining_minutes_badge_movie_203", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("25 MIN RIMANENTI").assertIsDisplayed()
    }

    @Test
    fun `req3 and req4 - MediaPosterCard hides title on focus and displays VixSrc availability dot accurately`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val baseUrl = "http://127.0.0.1:${server.port}"
            val repo = VixFlixRepository(
                dao = db.vixFlixDao(),
                tmdbBaseUrl = "$baseUrl/3/",
                vixBaseUrl = baseUrl
            )

            val itemAvailable = MediaItem(
                id = 501,
                mediaType = "movie",
                title = "Film Disponibile",
                overview = "",
                posterPath = "/p1.jpg",
                backdropPath = null,
                voteAverage = 8.0,
                releaseYear = "2024"
            )
            val itemUnavailable = MediaItem(
                id = 502,
                mediaType = "movie",
                title = "Film Non Disponibile",
                overview = "",
                posterPath = "/p2.jpg",
                backdropPath = null,
                voteAverage = 7.5,
                releaseYear = "2024"
            )
            val itemNetworkError = MediaItem(
                id = 503,
                mediaType = "movie",
                title = "Film Errore Rete",
                overview = "",
                posterPath = "/p3.jpg",
                backdropPath = null,
                voteAverage = 7.0,
                releaseYear = "2024"
            )

            assertEquals(VixSrcAvailabilityStatus.AVAILABLE, repo.checkVixSrcAvailability(itemAvailable, "it"))
            assertEquals(VixSrcAvailabilityStatus.UNAVAILABLE, repo.checkVixSrcAvailability(itemUnavailable, "it"))
            assertEquals(VixSrcAvailabilityStatus.UNKNOWN, repo.checkVixSrcAvailability(itemNetworkError, "it"))

            // Verifica cache hit senza nuova chiamata HTTP per itemAvailable
            val hitsBeforeCacheCheck = server.movie501Hits.get()
            assertEquals(VixSrcAvailabilityStatus.AVAILABLE, repo.checkVixSrcAvailability(itemAvailable, "it"))
            assertEquals(hitsBeforeCacheCheck, server.movie501Hits.get())

            val resolver = VixSrcAvailabilityResolver(
                getCached = { item -> repo.getCachedVixSrcAvailability(item, "it") },
                checkAsync = { item -> repo.checkVixSrcAvailability(item, "it") }
            )

            composeRule.setContent {
                MyApplicationTheme {
                    CompositionLocalProvider(LocalVixSrcAvailabilityResolver provides resolver) {
                        androidx.compose.foundation.layout.Row {
                            MediaPosterCard(item = itemAvailable, onClick = {})
                            MediaPosterCard(item = itemUnavailable, onClick = {})
                            MediaPosterCard(item = itemNetworkError, onClick = {})
                        }
                    }
                }
            }

            composeRule.waitForIdle()

            // Pallino verde presente per film disponibile
            val greenDot = composeRule.onNodeWithTag("poster_vixsrc_indicator_movie_501", useUnmergedTree = true)
            greenDot.assertIsDisplayed()
            val greenDesc = greenDot.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
            assertTrue(greenDesc.contains("Disponibile su VixSrc"))

            // Pallino rosso presente per film verificato non disponibile
            val redDot = composeRule.onNodeWithTag("poster_vixsrc_indicator_movie_502", useUnmergedTree = true)
            redDot.assertIsDisplayed()
            val redDesc = redDot.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
            assertTrue(redDesc.contains("Non disponibile su VixSrc"))

            // Pallino nascosto in caso di errore di rete / stato UNKNOWN
            assertEquals(
                0,
                composeRule.onAllNodesWithTag("poster_vixsrc_indicator_movie_503", useUnmergedTree = true).fetchSemanticsNodes().size
            )

            // Verifica focus sulla locandina: nessun titolo e nessuna scritta aggiuntiva mostrata durante il focus
            val availableCard = composeRule.onNodeWithTag("poster_card_movie_501")
            availableCard.requestFocus()
            composeRule.waitForIdle()
            availableCard.assertIsFocused()
            assertEquals(0, composeRule.onAllNodesWithText("Film Disponibile").fetchSemanticsNodes().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `req5 and req6 - MediaDetailScreen highlights focused CastCard and updates Similar Movies accurately without self or wrong mediaType`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val baseUrl = "http://127.0.0.1:${server.port}"
            val repo = VixFlixRepository(
                dao = db.vixFlixDao(),
                tmdbBaseUrl = "$baseUrl/3/",
                vixBaseUrl = baseUrl
            )
            val currentMovie = MediaItem(
                id = 100,
                mediaType = "movie",
                title = "Inception",
                overview = "Sogni condivisi",
                posterPath = "/inc.jpg",
                backdropPath = "/inc_bg.jpg",
                voteAverage = 8.8,
                releaseYear = "2010",
                genreIds = listOf(878, 28)
            )

            val details = repo.fetchMediaDetails(currentMovie).getOrThrow()

            // Verifica Req 6:
            // - Il film stesso (id=100) è stato escluso
            // - La serie TV intrusa (id=999, media_type="tv") è stata esclusa
            // - "The Matrix" (id=102, con generi 878 e 28 in comune) è stato ordinato prima di "Commedia Senza Generi In Comune" (id=101)
            assertEquals(listOf(102, 101), details.similar.map { it.id })
            assertTrue(details.similar.all { it.mediaType == "movie" })

            // Verifica Req 5 (focus CastCard) e Req 6 (aggiornamento dinamico riga Titoli Simili al cambio film)
            var detailState by mutableStateOf(
                DetailUiState(
                    mediaItem = currentMovie,
                    isLoading = false,
                    details = details
                )
            )
            var clickedActor: TmdbCastMemberDto? = null

            composeRule.setContent {
                MyApplicationTheme {
                    MediaDetailScreen(
                        state = detailState,
                        savedProgress = null,
                        isInWatchlist = false,
                        onBack = {},
                        onClose = {},
                        onPlayMain = {},
                        onPlayEpisode = { _, _, _ -> },
                        onOpenYouTubeTrailerPopup = {},
                        onSelectYouTubeTrailer = {},
                        onRetryYouTubeTrailer = {},
                        onCloseTrailer = {},
                        onSelectSeason = {},
                        onToggleWatchlist = {},
                        onSelectSimilar = {},
                        onSelectActor = { actor, _, _ -> clickedActor = actor }
                    )
                }
            }

            // Scorri fino alla sezione Cast nel LazyColumn della scheda dettaglio
            composeRule.onNodeWithTag("media_detail_lazy_column")
                .performScrollToNode(androidx.compose.ui.test.hasTestTag("detail_cast_section"))
            composeRule.waitForIdle()

            // Verifica navigazione D-Pad tra gli attori del Cast e visibilità focus
            val firstCastNode = composeRule.onNodeWithTag("cast_card_11")
            val secondCastNode = composeRule.onNodeWithTag("cast_card_12")
            firstCastNode.requestFocus()
            composeRule.waitForIdle()
            firstCastNode.assertIsFocused()

            firstCastNode.performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
            secondCastNode.assertIsFocused()

            secondCastNode.performClick()
            assertEquals(12, clickedActor?.id)

            // Verifica che cambiando film selezionato la sezione Titoli Simili si aggiorni immediatamente senza mantenere i vecchi elementi
            val secondMovie = currentMovie.copy(id = 200, title = "Gladiator")
            val secondMovieSimilar = listOf(
                MediaItem(
                    id = 205,
                    mediaType = "movie",
                    title = "Troy",
                    overview = "",
                    posterPath = "/troy.jpg",
                    backdropPath = null,
                    voteAverage = 7.3,
                    releaseYear = "2004"
                )
            )
            detailState = DetailUiState(
                mediaItem = secondMovie,
                isLoading = false,
                details = MediaDetails(
                    item = secondMovie,
                    genres = listOf("Azione"),
                    tagline = "",
                    runtimeMinutes = 155,
                    numberOfSeasons = 0,
                    seasons = emptyList(),
                    cast = emptyList(),
                    similar = secondMovieSimilar
                )
            )
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("media_detail_lazy_column")
                .performScrollToNode(androidx.compose.ui.test.hasTestTag("detail_similar_section_${secondMovie.uniqueKey}"))
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("poster_card_movie_205", useUnmergedTree = true).assertIsDisplayed()
            assertEquals(
                0,
                composeRule.onAllNodesWithTag("poster_card_movie_102", useUnmergedTree = true).fetchSemanticsNodes().size
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `req7 and req8 - VixPlayerScreen displays streaming resolution on same row as controls and executes Skip Intro without loading overlay`() {
        // Verifica formattazione etichette risoluzione
        assertEquals("4K", formatVideoResolutionLabel(3840, 2160))
        assertEquals("1080p", formatVideoResolutionLabel(1920, 1080))
        assertEquals("720p", formatVideoResolutionLabel(1280, 720))
        assertEquals("480p", formatVideoResolutionLabel(854, 480))
        assertNull(formatVideoResolutionLabel(0, 0))

        // Verifica parser intro markers reali da HTML VixSrc
        val parsedFromEmbed = VixSrcStreamResolver.parseIntroSegmentFromEmbedHtml(
            """<script>window.videoConfig = { "intro": { "start": 5, "end": 85 } };</script>"""
        )
        assertEquals(5_000L to 85_000L, parsedFromEmbed)

        val seriesItem = MediaItem(
            id = 777,
            mediaType = "tv",
            title = "Arcane",
            overview = "Serie animata",
            posterPath = "/arc.jpg",
            backdropPath = "/arc_bg.jpg",
            voteAverage = 9.0,
            releaseYear = "2021"
        )
        val introSegment = IntroSegmentEntity(
            episodeKey = "tv_777_s1_e1",
            seriesTmdbId = 777,
            seasonNumber = 1,
            episodeNumber = 1,
            introStartMs = 5_000L,
            introEndMs = 85_000L,
            source = IntroSegmentSource.LOCAL_DB.storageKey
        )

        // Verifica che isIntroActiveAtPosition rifiuti segmenti appartenenti a un episodio diverso
        assertTrue(
            isIntroActiveAtPosition(
                segment = introSegment,
                positionMs = 15_000L,
                durationMs = 2_400_000L,
                seriesTmdbId = 777,
                seasonNumber = 1,
                episodeNumber = 1
            )
        )
        assertFalse(
            isIntroActiveAtPosition(
                segment = introSegment,
                positionMs = 15_000L,
                durationMs = 2_400_000L,
                seriesTmdbId = 777,
                seasonNumber = 1,
                episodeNumber = 2
            )
        )

        val nextEp = VerifiedNextEpisode(
            seasonNumber = 1,
            episodeNumber = 2,
            title = "Episodio 2",
            stillUrl = null,
            overview = "",
            runtimeMinutes = 40
        )

        var skippedToMs = -1L
        val session = PlaybackSession(
            mediaItem = seriesItem,
            seasonNumber = 1,
            episodeNumber = 1,
            episodeTitle = "Welcome to the Playground",
            startPositionMs = 12_000L, // Dentro l'intervallo intro [5_000, 85_000)
            metadataDurationMs = 2_400_000L,
            verifiedNextEpisode = nextEp,
            introSegment = introSegment,
            resolvedStream = ResolvedStream(
                streamUrl = "https://vixsrc.to/playlist/777.m3u8",
                embedUrl = "https://vixsrc.to/tv/777/1/1/",
                isDirectHls = true
            ),
            isResolvingStream = false
        )

        composeRule.setContent {
            MyApplicationTheme {
                VixPlayerScreen(
                    session = session,
                    streamLang = "it",
                    bufferPreset = StreamingBufferPreset.DEFAULT,
                    vixSrcResolver = VixSrcStreamResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    advancedSettings = PlayerAdvancedSettings(skipIntroEnabled = true),
                    initialDurationMsOverride = 2_400_000L,
                    initialReadyForTest = true,
                    simulateBufferingOnSeekForTest = true,
                    videoResolutionOverrideForTest = "1080p",
                    onSkipIntroTriggered = { targetMs -> skippedToMs = targetMs }
                )
            }
        }

        composeRule.waitForIdle()

        // Req 7: Verifica che il badge risoluzione "1080p" sia visibile nella barra inferiore sulla STESSA riga dei pulsanti e del tempo
        val playButtonBounds = composeRule.onNodeWithTag("exo_play_pause_button").getUnclippedBoundsInRoot()
        val resolutionBounds = composeRule.onNodeWithTag("player_resolution_badge").getUnclippedBoundsInRoot()
        val timeDisplayBounds = composeRule.onNodeWithTag("player_time_display").getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("player_resolution_badge").assertIsDisplayed()
        composeRule.onNodeWithText("1080p").assertIsDisplayed()

        val playCenterY = (playButtonBounds.top.value + playButtonBounds.bottom.value) / 2f
        val resCenterY = (resolutionBounds.top.value + resolutionBounds.bottom.value) / 2f
        val timeCenterY = (timeDisplayBounds.top.value + timeDisplayBounds.bottom.value) / 2f

        assertTrue(
            "La risoluzione (${resCenterY}dp) deve trovarsi sulla stessa riga orizzontale dei pulsanti (${playCenterY}dp) e del tempo (${timeCenterY}dp)",
            abs(resCenterY - timeCenterY) <= 18f && abs(resCenterY - playCenterY) <= 18f
        )

        // Req 8: Verifica che il pulsante SALTA INTRO sia visibile durante l'intro, e che l'attivazione porti a introEndMs (85_000L)
        // senza mostrare il popup di caricamento (anche con simulateBufferingOnSeekForTest = true)
        val skipIntroButton = composeRule.onNodeWithTag("player_skip_intro_button")
        skipIntroButton.assertIsDisplayed()

        skipIntroButton.performClick()
        composeRule.waitForIdle()

        assertEquals(85_000L, skippedToMs)
        // Il pulsante SALTA INTRO scompare subito dopo il salto oltre introEndMs
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("player_skip_intro_button").fetchSemanticsNodes().size
        )
        // Nessun overlay di caricamento durante il seek di Salto Intro
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("vix_stream_loading_popup").fetchSemanticsNodes().size
        )
    }
}

private class FakeVixAndTmdbHttpServer {
    private val serverSocket = ServerSocket(0)
    private val executor = Executors.newCachedThreadPool()
    val movie501Hits = AtomicInteger(0)

    @Volatile
    private var running = false

    val port: Int get() = serverSocket.localPort

    fun start() {
        running = true
        executor.execute {
            while (running && !serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                executor.execute { handleClient(socket) }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    private fun handleClient(socket: java.net.Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
            }
            val rawPath = requestLine.split(" ").getOrNull(1).orEmpty()
            val (status, contentType, body) = route(rawPath)
            val bytes = body.toByteArray(Charsets.UTF_8)
            val writer = OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8)
            writer.write("HTTP/1.1 $status\r\n")
            writer.write("Content-Type: $contentType\r\n")
            writer.write("Content-Length: ${bytes.size}\r\n")
            writer.write("Connection: close\r\n\r\n")
            writer.flush()
            client.getOutputStream().write(bytes)
            client.getOutputStream().flush()
        }
    }

    private fun route(rawPath: String): Triple<String, String, String> {
        val path = rawPath.substringBefore("?")
        return when {
            path == "/api/movie/501" -> {
                movie501Hits.incrementAndGet()
                Triple("200 OK", "application/json; charset=utf-8", """{"src":"/embed/movie/501"}""")
            }
            path == "/embed/movie/501" -> {
                Triple(
                    "200 OK",
                    "text/html; charset=utf-8",
                    """
                    <html><script>
                    window.masterPlaylist = { url: 'https://vixsrc.to/playlist/501.m3u8', params: { token: 'abc', expires: '9999999999' } };
                    </script></html>
                    """.trimIndent()
                )
            }
            path == "/api/movie/502" || path == "/embed/movie/502" -> {
                Triple("404 Not Found", "application/json; charset=utf-8", """{"error":"not found"}""")
            }
            path.startsWith("/api/list/movie") -> {
                Triple("200 OK", "application/json; charset=utf-8", """[{"tmdb_id":501}]""")
            }
            path == "/api/movie/503" || path == "/embed/movie/503" -> {
                Triple("503 Service Unavailable", "text/plain; charset=utf-8", "Service Unavailable")
            }
            path == "/3/movie/100" -> {
                Triple(
                    "200 OK",
                    "application/json; charset=utf-8",
                    """
                    {
                      "id": 100,
                      "title": "Inception",
                      "overview": "Sogni condivisi",
                      "poster_path": "/inc.jpg",
                      "backdrop_path": "/inc_bg.jpg",
                      "release_date": "2010-07-16",
                      "runtime": 148,
                      "vote_average": 8.8,
                      "genres": [{"id": 878, "name": "Fantascienza"}, {"id": 28, "name": "Azione"}],
                      "credits": {
                        "cast": [
                          {"id": 11, "name": "Leonardo DiCaprio", "character": "Cobb", "profile_path": "/leo.jpg"},
                          {"id": 12, "name": "Joseph Gordon-Levitt", "character": "Arthur", "profile_path": "/jgl.jpg"}
                        ]
                      },
                      "similar": {
                        "results": [
                          {"id": 100, "title": "Inception", "poster_path": "/inc.jpg", "media_type": "movie", "genre_ids": [878, 28]},
                          {"id": 999, "name": "Serie Intrusa", "poster_path": "/tv.jpg", "media_type": "tv", "genre_ids": [878]},
                          {"id": 101, "title": "Commedia Senza Generi In Comune", "poster_path": "/c1.jpg", "media_type": "movie", "genre_ids": [35]},
                          {"id": 102, "title": "The Matrix", "poster_path": "/mat.jpg", "media_type": "movie", "genre_ids": [878, 28]}
                        ]
                      },
                      "videos": {"results": []}
                    }
                    """.trimIndent()
                )
            }
            else -> Triple("404 Not Found", "application/json; charset=utf-8", """{"error":"not found"}""")
        }
    }
}
