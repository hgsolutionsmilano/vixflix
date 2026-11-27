package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
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
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Size
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.screens.CinematicBackdropBlurTransformation
import com.example.ui.screens.VixPlayerScreen
import com.example.ui.screens.resolveLaunchContinueWatchingBackdropUrl
import com.example.ui.theme.MyApplicationTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @After
    fun tearDown() {
        Coil.reset()
    }

    private val movieWithContinueWatchingBackdrop = MediaItem(
        id = 550,
        mediaType = "movie",
        title = "Fight Club",
        overview = "Sinossi film",
        posterPath = "/movie_poster_vertical.jpg",
        backdropPath = "/movie_cw_backdrop.jpg",
        voteAverage = 8.8,
        releaseYear = "1999"
    )

    private val seriesWithContinueWatchingBackdrop = MediaItem(
        id = 1399,
        mediaType = "tv",
        title = "House of the Dragon",
        overview = "Sinossi serie TV",
        posterPath = "/series_poster_vertical.jpg",
        backdropPath = "/series_cw_backdrop.jpg",
        voteAverage = 8.4,
        releaseYear = "2022"
    )

    private val itemWithVerticalPosterOnly = MediaItem(
        id = 888,
        mediaType = "movie",
        title = "Solo Poster Verticale",
        overview = "",
        posterPath = "/only_vertical_poster.jpg",
        backdropPath = null,
        voteAverage = 7.4,
        releaseYear = "2023"
    )

    private val itemWithoutImages = MediaItem(
        id = 999,
        mediaType = "movie",
        title = "Senza Immagini",
        overview = "",
        posterPath = null,
        backdropPath = null,
        voteAverage = 7.0,
        releaseYear = "2024"
    )

    private fun installFakeBackdropImageLoader(
        context: Context,
        widthPx: Int = 780,
        heightPx: Int = 439,
        shouldFail: Boolean = false,
        capturedUrls: MutableList<String>? = null
    ) {
        val sampleBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(AndroidColor.rgb(24, 52, 92))
            val accentPaint = Paint().apply { color = AndroidColor.rgb(220, 40, 48) }
            canvas.drawRect(0f, 0f, widthPx * 0.45f, heightPx * 0.4f, accentPaint)
        }
        val fakeLoader = object : ImageLoader {
            override val defaults = coil.request.DefaultRequestOptions()
            override val components = coil.ComponentRegistry()
            override val memoryCache = null
            override val diskCache = null

            override fun enqueue(request: ImageRequest): coil.request.Disposable {
                capturedUrls?.add(request.data.toString())
                val result = runBlocking { execute(request) }
                when (result) {
                    is SuccessResult -> {
                        request.target?.onSuccess(result.drawable)
                        request.listener?.onSuccess(request, result)
                    }
                    is ErrorResult -> {
                        request.target?.onError(result.drawable)
                        request.listener?.onError(request, result)
                    }
                }
                return object : coil.request.Disposable {
                    override val isDisposed = true
                    override val job = kotlinx.coroutines.CompletableDeferred(result)
                    override fun dispose() = Unit
                }
            }

            override suspend fun execute(request: ImageRequest): ImageResult {
                if (shouldFail) {
                    return ErrorResult(
                        drawable = null,
                        request = request,
                        throwable = IllegalStateException("Simulated image error")
                    )
                }
                val transformed = request.transformations.fold(sampleBitmap) { acc, t ->
                    t.transform(acc, Size.ORIGINAL)
                }
                return SuccessResult(
                    drawable = BitmapDrawable(context.resources, transformed),
                    request = request,
                    dataSource = DataSource.MEMORY_CACHE
                )
            }

            override fun newBuilder(): ImageLoader.Builder = ImageLoader.Builder(context)
            override fun shutdown() = Unit
        }
        Coil.setImageLoader(fakeLoader)
    }

    @Test
    fun movieLaunch_usesContinueWatchingHorizontalW780UrlAndFillsTvLandscapeWithoutAlterationsToPopup() {
        val capturedUrls = mutableListOf<String>()
        installFakeBackdropImageLoader(
            context = composeRule.activity,
            widthPx = 780,
            heightPx = 439,
            capturedUrls = capturedUrls
        )
        val session = PlaybackSession(
            mediaItem = movieWithContinueWatchingBackdrop,
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 11L
        )

        val expectedCwUrl = "https://image.tmdb.org/t/p/w780/movie_cw_backdrop.jpg"
        assertEquals(expectedCwUrl, resolveLaunchContinueWatchingBackdropUrl(movieWithContinueWatchingBackdrop))

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

        assertTrue(
            "Lo sfondo deve caricare esattamente l'URL orizzontale w780 di Continua a guardare",
            capturedUrls.contains(expectedCwUrl)
        )

        val backdrop = composeRule.onNodeWithTag("stream_launch_poster_backdrop")
        backdrop.assertIsDisplayed()
        val backdropBounds = backdrop.getUnclippedBoundsInRoot()
        assertEquals(960.dp, backdropBounds.width)
        assertEquals(540.dp, backdropBounds.height)

        val cwImage = composeRule.onNodeWithTag("stream_launch_backdrop_image")
        cwImage.assertIsDisplayed()
        val cwBounds = cwImage.getUnclippedBoundsInRoot()
        assertEquals(960.dp, cwBounds.width)
        assertEquals(540.dp, cwBounds.height)

        // Overlay scuro e popup originale invariato in primo piano
        composeRule.onNodeWithTag("stream_launch_poster_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
        composeRule.onNodeWithText("Fight Club").assertIsDisplayed()
        composeRule.onNodeWithTag("loading_cancel_button").assertIsDisplayed().assertIsFocused()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
    fun tablet16by10Landscape_rendersFullScreenContinueWatchingHorizontalBackdrop() {
        installFakeBackdropImageLoader(composeRule.activity, widthPx = 780, heightPx = 439)
        val session = PlaybackSession(
            mediaItem = movieWithContinueWatchingBackdrop,
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 15L
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

        val cwBounds = composeRule.onNodeWithTag("stream_launch_backdrop_image").getUnclippedBoundsInRoot()
        assertEquals(1280.dp, cwBounds.width)
        assertEquals(800.dp, cwBounds.height)
        composeRule.onNodeWithTag("stream_launch_poster_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-port")
    fun smartphonePortrait_fillsScreenWithHorizontalBackdropAndKeepsPopupIntact() {
        installFakeBackdropImageLoader(composeRule.activity, widthPx = 780, heightPx = 439)
        val session = PlaybackSession(
            mediaItem = movieWithContinueWatchingBackdrop,
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 16L
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

        val cwBounds = composeRule.onNodeWithTag("stream_launch_backdrop_image").getUnclippedBoundsInRoot()
        assertEquals(360.dp, cwBounds.width)
        assertEquals(780.dp, cwBounds.height)
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w780dp-h360dp-land")
    fun smartphoneLandscape_fillsWideViewportWithHorizontalBackdrop() {
        installFakeBackdropImageLoader(composeRule.activity, widthPx = 780, heightPx = 439)
        val session = PlaybackSession(
            mediaItem = movieWithContinueWatchingBackdrop,
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 17L
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

        val cwBounds = composeRule.onNodeWithTag("stream_launch_backdrop_image").getUnclippedBoundsInRoot()
        assertEquals(780.dp, cwBounds.width)
        assertEquals(360.dp, cwBounds.height)
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
    }

    @Test
    fun cinematicBlurTransformation_keepsHorizontalBackdropRecognizableAndCompactInRam() = runBlocking {
        val input = Bitmap.createBitmap(780, 439, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(AndroidColor.rgb(18, 28, 48))
            val accentPaint = Paint().apply { color = AndroidColor.rgb(220, 180, 60) }
            canvas.drawRect(300f, 140f, 480f, 300f, accentPaint)
        }
        val transformation = CinematicBackdropBlurTransformation(
            maxSidePx = 540,
            blurRadius = 2,
            passes = 1
        )
        val output = transformation.transform(input, Size(540, 304))

        assertTrue("L'immagine sfocata resta entro 540px di lato massimo", maxOf(output.width, output.height) <= 540)
        assertEquals("Il rapporto 16:9 orizzontale è preservato", 780f / 439f, output.width.toFloat() / output.height.toFloat(), 0.02f)

        val centerPixel = output.getPixel(output.width / 2, output.height / 2)
        val centerRed = (centerPixel ushr 16) and 0xFF
        val centerGreen = (centerPixel ushr 8) and 0xFF
        assertTrue(
            "La sfocatura leggera mantiene i colori e i dettagli centrali ben riconoscibili (r=$centerRed, g=$centerGreen)",
            centerRed > 180 && centerGreen > 140
        )
    }

    @Test
    fun seriesEpisodeLaunch_usesSeriesContinueWatchingBackdropAndPreservesDpadFocusAndCancel() {
        val capturedUrls = mutableListOf<String>()
        installFakeBackdropImageLoader(
            context = composeRule.activity,
            widthPx = 780,
            heightPx = 439,
            capturedUrls = capturedUrls
        )
        val closeCalls = AtomicInteger(0)
        val episodeStillDto = TmdbEpisodeDto(
            id = 5001,
            episodeNumber = 4,
            name = "Il Drago Rosso e Oro",
            stillPath = "/episode_4_still_only.jpg"
        )
        val session = PlaybackSession(
            mediaItem = seriesWithContinueWatchingBackdrop,
            seasonNumber = 2,
            episodeNumber = 4,
            episodeTitle = episodeStillDto.name,
            seasonEpisodes = listOf(episodeStillDto),
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 22L
        )

        val expectedSeriesCwUrl = "https://image.tmdb.org/t/p/w780/series_cw_backdrop.jpg"
        assertEquals(expectedSeriesCwUrl, resolveLaunchContinueWatchingBackdropUrl(session.mediaItem))

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

        assertTrue(
            "Per gli episodi serie TV lo sfondo usa l'immagine orizzontale della serie (Continua a guardare)",
            capturedUrls.contains(expectedSeriesCwUrl)
        )
        composeRule.onNodeWithTag("stream_launch_poster_backdrop").assertIsDisplayed()
        composeRule.onNodeWithTag("stream_launch_backdrop_image").assertIsDisplayed()
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
    fun missingHorizontalBackdrop_neverFallsBackToVerticalPosterAndShowsDarkBackground() {
        val capturedUrls = mutableListOf<String>()
        installFakeBackdropImageLoader(
            context = composeRule.activity,
            capturedUrls = capturedUrls
        )
        // Anche se posterPath è valorizzato, senza backdropPath orizzontale NON deve usare il poster verticale
        assertNull(resolveLaunchContinueWatchingBackdropUrl(itemWithVerticalPosterOnly))
        assertNull(resolveLaunchContinueWatchingBackdropUrl(itemWithoutImages))

        val session = PlaybackSession(
            mediaItem = itemWithVerticalPosterOnly,
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

        assertTrue(
            "La locandina verticale non deve mai essere caricata come sfondo principale",
            capturedUrls.none { it.contains("only_vertical_poster") }
        )
        composeRule.onNodeWithTag("stream_launch_poster_backdrop").assertIsDisplayed()
        composeRule.onNodeWithTag("stream_launch_backdrop_image").assertDoesNotExist()
        composeRule.onNodeWithTag("stream_launch_poster_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
        composeRule.onNodeWithText("Solo Poster Verticale").assertIsDisplayed()
    }

    @Test
    fun backdropLoadError_fallsBackGracefullyToDarkBackgroundWithoutCrash() {
        installFakeBackdropImageLoader(
            context = composeRule.activity,
            shouldFail = true
        )
        val session = PlaybackSession(
            mediaItem = movieWithContinueWatchingBackdrop,
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 34L
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
        composeRule.onNodeWithTag("stream_launch_poster_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
    }

    @Test
    fun backdropHidesCleanlyWhenLaunchFailsOrPlayerOpens() {
        installFakeBackdropImageLoader(composeRule.activity, widthPx = 780, heightPx = 439)
        var currentSession by mutableStateOf(
            PlaybackSession(
                mediaItem = movieWithContinueWatchingBackdrop,
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
        composeRule.onNodeWithTag("stream_launch_backdrop_image").assertIsDisplayed()

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
