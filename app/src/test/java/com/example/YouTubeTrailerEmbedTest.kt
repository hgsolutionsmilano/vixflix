package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.ResolvedTrailerStream
import com.example.data.remote.TmdbTrailerResolver
import com.example.data.remote.TmdbVideoDto
import com.example.data.remote.VixSrcStreamResolver
import com.example.data.remote.YouTubeTrailerCandidate
import com.example.ui.TrailerPlaybackState
import com.example.ui.screens.YouTubeTrailerDialog
import com.example.ui.screens.buildYouTubeEmbedHtml
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YouTubeTrailerEmbedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        TmdbTrailerResolver.isWebViewRendererUnstable = false
        TmdbTrailerResolver.forceSoftwareEmulatorMode = null
    }

    @After
    fun tearDown() {
        TmdbTrailerResolver.isWebViewRendererUnstable = false
        TmdbTrailerResolver.forceSoftwareEmulatorMode = null
    }

    @Test
    fun `resolveYouTubeCandidate returns official YouTube embed directly without ExoPlayer extraction`() = runBlocking {
        val client = OkHttpClient()
        val vixSrc = VixSrcStreamResolver(client)
        val resolver = TmdbTrailerResolver(client, vixSrc)
        val candidate = YouTubeTrailerCandidate(
            videoId = "dQw4w9WgXcQ",
            title = "Trailer Ufficiale ITA",
            channelName = "Warner Bros. Italia",
            durationText = "2:15",
            durationSeconds = 135,
            languageLabel = "ITA"
        )

        val result = resolver.resolveYouTubeCandidateForExoPlayer(candidate)
        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()
        assertTrue(resolved.isYouTubeEmbedFallback)
        assertEquals("dQw4w9WgXcQ", resolved.embedVideoId)
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", resolved.streamUrl)
        assertFalse(resolved.isHls)
    }

    @Test
    fun `buildYouTubeEmbedHtml contains official YouTube iframe embed code and strict origin attributes`() {
        val htmlAutoplay = buildYouTubeEmbedHtml(
            videoId = "TcMBFSGVi1c",
            originUrl = "https://com.aistudio.vixflix.rxmklp",
            autoplay = true
        )

        assertTrue(htmlAutoplay.contains("<iframe"))
        assertTrue(htmlAutoplay.contains("""src="https://www.youtube.com/embed/TcMBFSGVi1c?"""))
        assertTrue(htmlAutoplay.contains("autoplay=1"))
        assertTrue(htmlAutoplay.contains("origin=https://com.aistudio.vixflix.rxmklp"))
        assertTrue(htmlAutoplay.contains("""referrerpolicy="strict-origin-when-cross-origin""""))
        assertTrue(htmlAutoplay.contains("""allow="accelerometer; autoplay; clipboard-write; gyroscope; web-share""""))
        assertTrue(htmlAutoplay.contains("https://www.youtube.com/iframe_api"))

        val htmlSafeEmulator = buildYouTubeEmbedHtml(
            videoId = "TcMBFSGVi1c",
            originUrl = "https://com.aistudio.vixflix.rxmklp",
            autoplay = false
        )
        assertTrue(htmlSafeEmulator.contains("autoplay=0"))
    }

    @Test
    fun `YouTubeTrailerDialog embeds official YouTube player view instead of ExoPlayer`() {
        val candidate = YouTubeTrailerCandidate(
            videoId = "TcMBFSGVi1c",
            title = "Avengers: Endgame - Trailer Ufficiale",
            channelName = "Marvel Italy",
            durationText = "2:26",
            durationSeconds = 146,
            languageLabel = "ITA"
        )
        val mediaItem = MediaItem(
            id = 299534,
            mediaType = "movie",
            title = "Avengers: Endgame",
            overview = "Test trailer dialog",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.4,
            releaseYear = "2019"
        )
        val trailerState = TrailerPlaybackState(
            trailers = listOf(candidate),
            selectedVideo = candidate,
            isSearching = false,
            isLoadingStream = false,
            resolvedTrailer = ResolvedTrailerStream(
                video = TmdbVideoDto(
                    id = "yt_TcMBFSGVi1c",
                    key = "TcMBFSGVi1c",
                    name = candidate.title,
                    site = "YouTube",
                    type = "Trailer"
                ),
                streamUrl = "https://www.youtube.com/embed/TcMBFSGVi1c",
                isHls = false,
                sourceLabel = "TRAILER • YouTube",
                userAgent = ResolvedStream.DEFAULT_USER_AGENT,
                isYouTubeEmbedFallback = true,
                embedVideoId = "TcMBFSGVi1c"
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                YouTubeTrailerDialog(
                    mediaItem = mediaItem,
                    trailerState = trailerState,
                    onSelectTrailer = {},
                    onRetry = {},
                    onClose = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("youtube_trailer_modal_card").assertIsDisplayed()
        composeRule.onNodeWithTag("fullscreen_trailer_player_view", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("youtube_official_embed_view", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("youtube_trailer_playlist_row", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_trailer_aspect_button", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `YouTubeTrailerDialog mounts cleanly in safe mode when renderer marked unstable`() {
        TmdbTrailerResolver.isWebViewRendererUnstable = true
        val candidate = YouTubeTrailerCandidate(
            videoId = "TcMBFSGVi1c",
            title = "Avengers: Endgame - Trailer Ufficiale",
            channelName = "Marvel Italy",
            durationText = "2:26",
            durationSeconds = 146,
            languageLabel = "ITA"
        )
        val mediaItem = MediaItem(
            id = 299534,
            mediaType = "movie",
            title = "Avengers: Endgame",
            overview = "Test safe mode",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.4,
            releaseYear = "2019"
        )
        val trailerState = TrailerPlaybackState(
            trailers = listOf(candidate),
            selectedVideo = candidate,
            isSearching = false,
            isLoadingStream = false,
            resolvedTrailer = ResolvedTrailerStream(
                video = TmdbVideoDto(
                    id = "yt_TcMBFSGVi1c",
                    key = "TcMBFSGVi1c",
                    name = candidate.title,
                    site = "YouTube",
                    type = "Trailer"
                ),
                streamUrl = "https://www.youtube.com/embed/TcMBFSGVi1c",
                isHls = false,
                sourceLabel = "TRAILER • YouTube",
                userAgent = ResolvedStream.DEFAULT_USER_AGENT,
                isYouTubeEmbedFallback = true,
                embedVideoId = "TcMBFSGVi1c"
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                YouTubeTrailerDialog(
                    mediaItem = mediaItem,
                    trailerState = trailerState,
                    onSelectTrailer = {},
                    onRetry = {},
                    onClose = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("youtube_official_embed_view", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `YouTubeTrailerDialog retry and external YouTube buttons appear on error and retry restarts cleanly`() {
        var retryCount = 0
        val candidate = YouTubeTrailerCandidate(
            videoId = "TcMBFSGVi1c",
            title = "Avengers: Endgame - Trailer Ufficiale",
            channelName = "Marvel Italy",
            durationText = "2:26",
            durationSeconds = 146,
            languageLabel = "ITA"
        )
        val mediaItem = MediaItem(
            id = 299534,
            mediaType = "movie",
            title = "Avengers: Endgame",
            overview = "Test retry recovery",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.4,
            releaseYear = "2019"
        )
        val errorState = TrailerPlaybackState(
            trailers = listOf(candidate),
            selectedVideo = candidate,
            isSearching = false,
            isLoadingStream = false,
            errorMessage = "Il player WebView YouTube è stato interrotto dal sistema."
        )

        composeRule.setContent {
            MyApplicationTheme {
                YouTubeTrailerDialog(
                    mediaItem = mediaItem,
                    trailerState = errorState,
                    onSelectTrailer = {},
                    onRetry = { retryCount++ },
                    onClose = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trailer_error_text", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("open_external_youtube_button", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("retry_trailer_button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, retryCount)
    }

    @Test
    fun `YouTubeTrailerDialog skips WebView and renders safe preview card on GPU-less emulator`() {
        TmdbTrailerResolver.forceSoftwareEmulatorMode = true
        val candidate = YouTubeTrailerCandidate(
            videoId = "TcMBFSGVi1c",
            title = "Avengers: Endgame - Trailer Ufficiale",
            channelName = "Marvel Italy",
            durationText = "2:26",
            durationSeconds = 146,
            languageLabel = "ITA"
        )
        val mediaItem = MediaItem(
            id = 299534,
            mediaType = "movie",
            title = "Avengers: Endgame",
            overview = "Test GPU-less emulator guard",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.4,
            releaseYear = "2019"
        )
        val trailerState = TrailerPlaybackState(
            trailers = listOf(candidate),
            selectedVideo = candidate,
            isSearching = false,
            isLoadingStream = false,
            resolvedTrailer = ResolvedTrailerStream(
                video = TmdbVideoDto(
                    id = "yt_TcMBFSGVi1c",
                    key = "TcMBFSGVi1c",
                    name = candidate.title,
                    site = "YouTube",
                    type = "Trailer"
                ),
                streamUrl = "https://www.youtube.com/embed/TcMBFSGVi1c",
                isHls = false,
                sourceLabel = "TRAILER • YouTube",
                userAgent = ResolvedStream.DEFAULT_USER_AGENT,
                isYouTubeEmbedFallback = true,
                embedVideoId = "TcMBFSGVi1c"
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                YouTubeTrailerDialog(
                    mediaItem = mediaItem,
                    trailerState = trailerState,
                    onSelectTrailer = {},
                    onRetry = {},
                    onClose = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("youtube_safe_preview_card", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("open_external_youtube_button", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("close_safe_preview_button", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("youtube_official_embed_view", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h720dp-land")
    fun `YouTubeTrailerDialog retains D-Pad focus while navigating and selecting across multiple available trailers`() {
        TmdbTrailerResolver.forceSoftwareEmulatorMode = true
        val candidates = (1..6).map { idx ->
            YouTubeTrailerCandidate(
                videoId = "vid_00$idx",
                title = "Trailer Ufficiale #$idx",
                channelName = "Canale Ufficiale",
                durationText = "2:0$idx",
                durationSeconds = 120 + idx,
                languageLabel = if (idx <= 3) "ITA" else "ENG"
            )
        }
        val mediaItem = MediaItem(
            id = 299534,
            mediaType = "movie",
            title = "Avengers: Endgame",
            overview = "Test D-Pad navigation inside trailer popup",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.4,
            releaseYear = "2019"
        )

        var currentState by androidx.compose.runtime.mutableStateOf(
            TrailerPlaybackState(
                trailers = candidates,
                selectedVideo = candidates.first(),
                isSearching = false,
                isLoadingStream = false,
                resolvedTrailer = ResolvedTrailerStream(
                    video = TmdbVideoDto(
                        id = "yt_${candidates.first().videoId}",
                        key = candidates.first().videoId,
                        name = candidates.first().title,
                        site = "YouTube",
                        type = "Trailer"
                    ),
                    streamUrl = "https://www.youtube.com/embed/${candidates.first().videoId}",
                    isHls = false,
                    sourceLabel = "TRAILER • YouTube",
                    userAgent = ResolvedStream.DEFAULT_USER_AGENT,
                    isYouTubeEmbedFallback = true,
                    embedVideoId = candidates.first().videoId
                )
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                YouTubeTrailerDialog(
                    mediaItem = mediaItem,
                    trailerState = currentState,
                    onSelectTrailer = { chosen ->
                        currentState = currentState.copy(
                            selectedVideo = chosen,
                            resolvedTrailer = ResolvedTrailerStream(
                                video = TmdbVideoDto(
                                    id = "yt_${chosen.videoId}",
                                    key = chosen.videoId,
                                    name = chosen.title,
                                    site = "YouTube",
                                    type = "Trailer"
                                ),
                                streamUrl = "https://www.youtube.com/embed/${chosen.videoId}",
                                isHls = false,
                                sourceLabel = "TRAILER • YouTube",
                                userAgent = ResolvedStream.DEFAULT_USER_AGENT,
                                isYouTubeEmbedFallback = true,
                                embedVideoId = chosen.videoId
                            )
                        )
                    },
                    onRetry = {},
                    onClose = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 1. All'apertura del popup il pulsante Chiudi ha il focus automatico
        val closeConfiguredFocused = composeRule.onNodeWithTag("close_trailer_overlay_button")
            .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
        assertTrue("Close button must receive initial D-Pad focus when popup opens", closeConfiguredFocused)

        // 2. Premendo D-Pad Giù dal pulsante Chiudi si scende direttamente sul trailer attivo nella playlist
        composeRule.onNodeWithTag("close_trailer_overlay_button")
            .performKeyInput {
                with(this) {
                    pressKey(androidx.compose.ui.input.key.Key.DirectionDown)
                }
            }
        composeRule.waitForIdle()

        val firstCardFocused = composeRule.onNodeWithTag("youtube_trailer_option_vid_001")
            .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
        assertTrue("First trailer card must receive D-Pad focus when pressing Down from Close", firstCardFocused)

        // 3. Premendo Sinistra sul primo trailer o Giù sul fondo del popup, il focus NON deve mai perdersi
        composeRule.onNodeWithTag("youtube_trailer_option_vid_001")
            .performKeyInput {
                with(this) {
                    pressKey(androidx.compose.ui.input.key.Key.DirectionLeft)
                    pressKey(androidx.compose.ui.input.key.Key.DirectionDown)
                }
            }
        composeRule.waitForIdle()
        assertTrue(
            "First trailer card must keep D-Pad focus when pressing Left or Down at boundary",
            composeRule.onNodeWithTag("youtube_trailer_option_vid_001")
                .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
        )

        // 4. Spostandosi a Destra tra tutti i 6 trailer disponibili (incluso lo scroll della LazyRow),
        // ogni card riceve il focus senza mai perderlo
        for (idx in 1 until 6) {
            val currentTag = "youtube_trailer_option_vid_00$idx"
            val nextTag = "youtube_trailer_option_vid_00${idx + 1}"
            composeRule.onNodeWithTag(currentTag)
                .performKeyInput {
                    with(this) {
                        pressKey(androidx.compose.ui.input.key.Key.DirectionRight)
                    }
                }
            composeRule.waitForIdle()
            val nextFocused = composeRule.onNodeWithTag(nextTag)
                .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
            assertTrue("Trailer card #$idx -> #${idx + 1} must be focused after D-Pad Right", nextFocused)
        }

        // 5. Premendo Destra sull'ultimo trailer (#6), il focus resta bloccato sull'ultimo trailer
        composeRule.onNodeWithTag("youtube_trailer_option_vid_006")
            .performKeyInput {
                with(this) {
                    pressKey(androidx.compose.ui.input.key.Key.DirectionRight)
                }
            }
        composeRule.waitForIdle()
        assertTrue(
            "Last trailer card must keep D-Pad focus when pressing Right at right boundary",
            composeRule.onNodeWithTag("youtube_trailer_option_vid_006")
                .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
        )

        // 6. Selezionando un altro trailer (es. #5), il focus D-Pad rimane sulla card selezionata senza perdersi!
        composeRule.onNodeWithTag("youtube_trailer_option_vid_006")
            .performKeyInput {
                with(this) {
                    pressKey(androidx.compose.ui.input.key.Key.DirectionLeft)
                }
            }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("youtube_trailer_option_vid_005").performClick()
        composeRule.waitForIdle()

        assertEquals("vid_005", currentState.selectedVideo?.videoId)
        val fifthStillFocused = composeRule.onNodeWithTag("youtube_trailer_option_vid_005")
            .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
        assertTrue("Selected trailer card #5 must keep D-Pad focus after switching active trailer", fifthStillFocused)

        // 7. Premendo D-Pad Su dalla lista dei trailer, il focus torna al pulsante Chiudi
        composeRule.onNodeWithTag("youtube_trailer_option_vid_005")
            .performKeyInput {
                with(this) {
                    pressKey(androidx.compose.ui.input.key.Key.DirectionUp)
                }
            }
        composeRule.waitForIdle()
        assertTrue(
            "Close button must receive D-Pad focus when pressing Up from trailer playlist",
            composeRule.onNodeWithTag("close_trailer_overlay_button")
                .fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }
        )
    }
}

