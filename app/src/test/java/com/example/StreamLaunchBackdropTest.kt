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
import com.example.ui.screens.computeFittedPosterLayout
import com.example.ui.screens.resolveLaunchBackgroundPosterUrl
import com.example.ui.theme.MyApplicationTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    private fun installFakePosterImageLoader(
        context: Context,
        widthPx: Int,
        heightPx: Int
    ) {
        val sampleBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(AndroidColor.rgb(32, 64, 112))
            val accentPaint = Paint().apply { color = AndroidColor.rgb(220, 40, 48) }
            canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx * 0.25f, accentPaint)
        }
        val fakeLoader = object : ImageLoader {
            override val defaults = coil.request.DefaultRequestOptions()
            override val components = coil.ComponentRegistry()
            override val memoryCache = null
            override val diskCache = null

            override fun enqueue(request: ImageRequest): coil.request.Disposable {
                val result = runBlocking { execute(request) }
                if (result is SuccessResult) {
                    request.target?.onSuccess(result.drawable)
                    request.listener?.onSuccess(request, result)
                }
                return object : coil.request.Disposable {
                    override val isDisposed = true
                    override val job = kotlinx.coroutines.CompletableDeferred(result)
                    override fun dispose() = Unit
                }
            }

            override suspend fun execute(request: ImageRequest): ImageResult {
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
    fun movieLaunch_rendersTwoLayerCinematicPosterOnAndroidTvLandscapeWithoutZoomOrCrop() {
        installFakePosterImageLoader(composeRule.activity, widthPx = 500, heightPx = 750)
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

        // Livello 1: sfondo esteso sfocato riempie 100% dello schermo TV landscape 16:9
        val backdrop = composeRule.onNodeWithTag("stream_launch_poster_backdrop")
        backdrop.assertIsDisplayed()
        val backdropBounds = backdrop.getUnclippedBoundsInRoot()
        assertEquals(960.dp, backdropBounds.width)
        assertEquals(540.dp, backdropBounds.height)

        val blurBgBounds = composeRule.onNodeWithTag("stream_launch_poster_blur_bg").getUnclippedBoundsInRoot()
        assertEquals(960.dp, blurBgBounds.width)
        assertEquals(540.dp, blurBgBounds.height)

        // Livello 2: locandina principale completa (2:3), centrata e interamente visibile senza zoom/tagli
        val posterNode = composeRule.onNodeWithTag("stream_launch_poster_image")
        posterNode.assertIsDisplayed()
        val posterBounds = posterNode.getUnclippedBoundsInRoot()
        assertTrue("La locandina non deve uscire a sinistra/destra", posterBounds.left > 0.dp && posterBounds.right < 960.dp)
        assertTrue("La locandina non deve uscire sopra/sotto", posterBounds.top > 0.dp && posterBounds.bottom < 540.dp)
        assertTrue("In landscape la locandina 2:3 non deve allargarsi a tutta larghezza TV", posterBounds.width < 500.dp)

        val renderedAspect = posterBounds.width.value / posterBounds.height.value
        assertEquals("Il rapporto d'aspetto 2:3 deve restare invariato", 2f / 3f, renderedAspect, 0.02f)

        val centerDeltaX = abs(((posterBounds.left + posterBounds.right) / 2f).value - 480f)
        val centerDeltaY = abs(((posterBounds.top + posterBounds.bottom) / 2f).value - 270f)
        assertTrue("Centratura orizzontale esatta (delta=$centerDeltaX)", centerDeltaX <= 1.5f)
        assertTrue("Centratura verticale esatta (delta=$centerDeltaY)", centerDeltaY <= 1.5f)

        // Livello 3 + Popup originale invariato in primo piano
        composeRule.onNodeWithTag("stream_launch_poster_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
        composeRule.onNodeWithText("Fight Club").assertIsDisplayed()
        composeRule.onNodeWithTag("loading_cancel_button").assertIsDisplayed().assertIsFocused()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
    fun tablet16by10Landscape_rendersCompletePosterCenteredWithBlurredBackdrop() {
        installFakePosterImageLoader(composeRule.activity, widthPx = 600, heightPx = 800) // 3:4
        val session = PlaybackSession(
            mediaItem = movieWithPoster,
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

        val blurBounds = composeRule.onNodeWithTag("stream_launch_poster_blur_bg").getUnclippedBoundsInRoot()
        assertEquals(1280.dp, blurBounds.width)
        assertEquals(800.dp, blurBounds.height)

        val posterBounds = composeRule.onNodeWithTag("stream_launch_poster_image").getUnclippedBoundsInRoot()
        assertTrue(posterBounds.left > 0.dp && posterBounds.right < 1280.dp)
        assertTrue(posterBounds.top > 0.dp && posterBounds.bottom < 800.dp)
        assertEquals(3f / 4f, posterBounds.width.value / posterBounds.height.value, 0.02f)

        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-port")
    fun smartphonePortrait_keepsFullPosterUnclippedAndPopupIntact() {
        installFakePosterImageLoader(composeRule.activity, widthPx = 500, heightPx = 750)
        val session = PlaybackSession(
            mediaItem = movieWithPoster,
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

        val blurBounds = composeRule.onNodeWithTag("stream_launch_poster_blur_bg").getUnclippedBoundsInRoot()
        assertEquals(360.dp, blurBounds.width)
        assertEquals(780.dp, blurBounds.height)

        val posterBounds = composeRule.onNodeWithTag("stream_launch_poster_image").getUnclippedBoundsInRoot()
        assertTrue(posterBounds.left > 0.dp && posterBounds.right < 360.dp)
        assertTrue(posterBounds.top > 0.dp && posterBounds.bottom < 780.dp)
        assertEquals(2f / 3f, posterBounds.width.value / posterBounds.height.value, 0.02f)
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w780dp-h360dp-land")
    fun smartphoneLandscape_rendersFullPosterInsideCompactHeight() {
        installFakePosterImageLoader(composeRule.activity, widthPx = 600, heightPx = 600) // 1:1 square
        val session = PlaybackSession(
            mediaItem = movieWithPoster,
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

        val posterBounds = composeRule.onNodeWithTag("stream_launch_poster_image").getUnclippedBoundsInRoot()
        assertTrue(posterBounds.left > 0.dp && posterBounds.right < 780.dp)
        assertTrue(posterBounds.top > 0.dp && posterBounds.bottom < 360.dp)
        assertEquals(1.0f, posterBounds.width.value / posterBounds.height.value, 0.02f)
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
    }

    @Test
    fun computeFittedPosterLayout_preservesFullImageAcrossTv720p1080p4kTabletsAndPhones() {
        val viewports = listOf(
            "Phone Portrait" to (360f to 780f),
            "Phone Landscape" to (780f to 360f),
            "Tablet Landscape" to (1024f to 768f),
            "Tablet 16:10 Landscape" to (1280f to 800f),
            "Android TV / Fire TV 720p" to (1280f to 720f),
            "Android TV / Google TV 1080p" to (1920f to 1080f),
            "Android TV / Fire TV 4K" to (3840f to 2160f)
        )
        val posterRatios = listOf(2f / 3f, 3f / 4f, 1f, 16f / 9f)

        for ((label, dims) in viewports) {
            val (w, h) = dims
            for (aspect in posterRatios) {
                val layout = computeFittedPosterLayout(
                    availableWidthDp = w,
                    availableHeightDp = h,
                    imageAspectRatio = aspect
                )
                assertTrue(
                    "$label aspect=$aspect: width ${layout.widthDp} exceeded container ${layout.containerWidthDp}",
                    layout.widthDp <= layout.containerWidthDp + 0.01f
                )
                assertTrue(
                    "$label aspect=$aspect: height ${layout.heightDp} exceeded container ${layout.containerHeightDp}",
                    layout.heightDp <= layout.containerHeightDp + 0.01f
                )
                assertEquals(
                    "$label aspect=$aspect: proportion mismatch",
                    aspect,
                    layout.widthDp / layout.heightDp,
                    0.01f
                )
            }
        }
    }

    @Test
    fun cinematicBlurTransformation_downscalesAndBlursSafelyAcrossAllApiLevels() = runBlocking {
        val input = Bitmap.createBitmap(500, 750, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(AndroidColor.BLACK)
            val whitePaint = Paint().apply { color = AndroidColor.WHITE }
            canvas.drawRect(200f, 300f, 300f, 450f, whitePaint)
        }
        val transformation = CinematicBackdropBlurTransformation()
        val output = transformation.transform(input, Size(216, 216))

        assertTrue("L'immagine sfocata deve restare compatta in RAM", maxOf(output.width, output.height) <= 144)
        assertNotEquals("La sfocatura deve ammorbidire i bordi netti", input.width, output.width)
        val edgePixel = output.getPixel(output.width / 2, output.height / 2)
        assertTrue("Il pixel centrale resta valido", (edgePixel ushr 24) == 0xFF)
    }

    @Test
    fun seriesEpisodeLaunch_usesSeriesPosterAndPreservesDpadFocusAndCancel() {
        installFakePosterImageLoader(composeRule.activity, widthPx = 500, heightPx = 750)
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
        composeRule.onNodeWithTag("stream_launch_poster_blur_bg").assertIsDisplayed()
        composeRule.onNodeWithTag("stream_launch_poster_image").assertIsDisplayed()
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
        composeRule.onNodeWithTag("stream_launch_poster_blur_bg").assertDoesNotExist()
        composeRule.onNodeWithTag("stream_launch_poster_image").assertDoesNotExist()
        composeRule.onNodeWithTag("stream_launch_poster_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
        composeRule.onNodeWithText("Senza Locandina").assertIsDisplayed()
    }

    @Test
    fun backdropHidesCleanlyWhenLaunchFailsOrPlayerOpens() {
        installFakePosterImageLoader(composeRule.activity, widthPx = 500, heightPx = 750)
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
