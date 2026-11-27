package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.screens.VixPlayerScreen
import com.example.ui.screens.resolveLaunchBackgroundPosterUrl
import com.example.ui.theme.MyApplicationTheme
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-notouch-dpad")
class StreamLaunchBackdropTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val movieWithPoster = MediaItem(
        id = 550,
        mediaType = "movie",
        title = "Fight Club",
        overview = "Sinossi film",
        posterPath = "/movie_poster.jpg",
        backdropPath = "/movie_backdrop.jpg",
        voteAverage = 8.8,
        releaseYear = "1999"
    )

    private val seriesWithPoster = MediaItem(
        id = 1399,
        mediaType = "tv",
        title = "House of the Dragon",
        overview = "Sinossi serie TV",
        posterPath = "/series_poster.jpg",
        backdropPath = "/series_backdrop.jpg",
        voteAverage = 8.4,
        releaseYear = "2022"
    )

    private val itemWithoutImages = MediaItem(
        id = 999,
        mediaType = "movie",
        title = "Senza Locandina",
        overview = "",
        posterPath = null,
        backdropPath = null,
        voteAverage = 7.0,
        releaseYear = "2024"
    )

    @Test
    fun movieLaunch_rendersFullScreenPosterBackdropAndDarkOverlayUnderUnchangedLoadingPopup() {
        val session = PlaybackSession(
            mediaItem = movieWithPoster,
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 11L
        )

        assertEquals(
            "https://image.tmdb.org/t/p/w500/movie_poster.jpg",
            resolveLaunchBackgroundPosterUrl(movieWithPoster)
        )

        composeRule.setContent {
            MyApplicationTheme {
                VixPlayerScreen(
                    session = session,
                    streamLang = "it",
                    vixSrcResolver = VixSrcStreamResolver(OkHttpClient()),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    initialReadyForTest = false
                )
            }
        }
        composeRule.waitForIdle()

        val backdrop = composeRule.onNodeWithTag("stream_launch_poster_backdrop")
        backdrop.assertIsDisplayed()
        val backdropBounds = backdrop.getUnclippedBoundsInRoot()
        assertEquals(0.dp, backdropBounds.left)
        assertEquals(0.dp, backdropBounds.top)
        assertEquals(960.dp, backdropBounds.width)
        assertEquals(540.dp, backdropBounds.height)

        composeRule.onNodeWithTag("stream_launch_poster_image").assertExists()
        composeRule.onNodeWithTag("stream_launch_poster_overlay").assertIsDisplayed()

        // Il popup originale resta visibile in primo piano, compatto e con focus immediato su Annulla
        val popup = composeRule.onNodeWithTag("video_loading_popup")
        popup.assertIsDisplayed()
        composeRule.onNodeWithText("Fight Club").assertIsDisplayed()
        composeRule.onNodeWithText("Ricerca sorgente…").assertIsDisplayed()
        composeRule.onNodeWithTag("loading_cancel_button").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun seriesEpisodeLaunch_usesSeriesPosterAndPreservesDpadFocusAndCancel() {
        val closeCalls = AtomicInteger(0)
        val episodeStillDto = TmdbEpisodeDto(
            id = 5001,
            episodeNumber = 4,
            name = "Il Drago Rosso e Oro",
            stillPath = "/episode_4_still_only.jpg"
        )
        val session = PlaybackSession(
            mediaItem = seriesWithPoster,
            seasonNumber = 2,
            episodeNumber = 4,
            episodeTitle = episodeStillDto.name,
            seasonEpisodes = listOf(episodeStillDto),
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 22L
        )

        assertEquals(
            "https://image.tmdb.org/t/p/w500/series_poster.jpg",
            resolveLaunchBackgroundPosterUrl(session.mediaItem)
        )

        composeRule.setContent {
            MyApplicationTheme {
                VixPlayerScreen(
                    session = session,
                    streamLang = "it",
                    vixSrcResolver = VixSrcStreamResolver(OkHttpClient()),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = { closeCalls.incrementAndGet() },
                    initialReadyForTest = false
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("stream_launch_poster_backdrop").assertIsDisplayed()
        composeRule.onNodeWithTag("stream_launch_poster_image").assertExists()
        composeRule.onNodeWithTag("stream_launch_poster_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
        composeRule.onNodeWithText("House of the Dragon").assertIsDisplayed()
        composeRule.onNodeWithText("S2 · E4 · Il Drago Rosso e Oro").assertIsDisplayed()

        // Lo sfondo non ruba il focus del telecomando e Annulla funziona al primo invio
        val cancelBtn = composeRule.onNodeWithTag("loading_cancel_button")
        cancelBtn.assertIsFocused()
        cancelBtn.performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        cancelBtn.assertIsFocused()

        cancelBtn.performClick()
        composeRule.waitForIdle()
        assertEquals(1, closeCalls.get())
    }

    @Test
    fun missingPoster_fallsBackGracefullyToCinematicDarkBackgroundWithoutCrash() {
        assertNull(resolveLaunchBackgroundPosterUrl(itemWithoutImages))

        val session = PlaybackSession(
            mediaItem = itemWithoutImages,
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 33L
        )

        composeRule.setContent {
            MyApplicationTheme {
                VixPlayerScreen(
                    session = session,
                    streamLang = "it",
                    vixSrcResolver = VixSrcStreamResolver(OkHttpClient()),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    initialReadyForTest = false
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("stream_launch_poster_backdrop").assertIsDisplayed()
        composeRule.onNodeWithTag("stream_launch_poster_image").assertDoesNotExist()
        composeRule.onNodeWithTag("stream_launch_poster_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
        composeRule.onNodeWithText("Senza Locandina").assertIsDisplayed()
    }

    @Test
    fun backdropHidesCleanlyWhenLaunchFailsOrPlayerOpens() {
        var currentSession by mutableStateOf(
            PlaybackSession(
                mediaItem = movieWithPoster,
                isResolvingStream = true,
                resolvedStream = null,
                sessionToken = 44L
            )
        )
        var playerReady by mutableStateOf(false)

        composeRule.setContent {
            MyApplicationTheme {
                VixPlayerScreen(
                    session = currentSession,
                    streamLang = "it",
                    vixSrcResolver = VixSrcStreamResolver(OkHttpClient()),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    initialReadyForTest = playerReady
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stream_launch_poster_backdrop").assertIsDisplayed()

        // Quando l'avvio fallisce, lo sfondo sparisce e lascia spazio al popup errore
        currentSession = currentSession.copy(
            isResolvingStream = false,
            errorMessage = "Contenuto non disponibile"
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stream_launch_poster_backdrop").assertDoesNotExist()
        composeRule.onNodeWithTag("video_error_popup").assertIsDisplayed()

        // Nuovo tentativo in fase di avvio -> riappare lo sfondo
        currentSession = currentSession.copy(
            isResolvingStream = true,
            errorMessage = null,
            sessionToken = 45L
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stream_launch_poster_backdrop").assertIsDisplayed()

        // Quando il player si apre pronto -> sparisce definitivamente
        playerReady = true
        currentSession = currentSession.copy(isResolvingStream = false)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stream_launch_poster_backdrop").assertDoesNotExist()
    }
}
