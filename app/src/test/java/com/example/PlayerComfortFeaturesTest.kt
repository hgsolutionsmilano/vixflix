package com.example

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppPreferences
import com.example.data.local.PlayerComfortSettings
import com.example.data.local.SubtitleBackgroundStyle
import com.example.data.local.SubtitleTextColor
import com.example.data.local.SubtitleTextSize
import com.example.data.local.VideoAspectMode
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.screens.VixPlayerScreen
import com.example.ui.screens.applyComfortSettingsToPlayerView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = VixFlixApplication::class, qualifiers = "w960dp-h540dp-television-notouch-dpad")
class PlayerComfortFeaturesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun configureFireTvEnvironment() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uiModeManager = app.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        shadowOf(uiModeManager).currentModeType = Configuration.UI_MODE_TYPE_TELEVISION
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_TELEVISION, true)
    }

    private fun findPlayerView(root: View): PlayerView? {
        if (root is PlayerView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findPlayerView(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    @Test
    fun comfortModels_storageParsingAndMedia3Mapping_areSafeAndAccurate() {
        assertEquals(VideoAspectMode.FIT, VideoAspectMode.fromStorageKey("unknown"))
        assertEquals(VideoAspectMode.ZOOM, VideoAspectMode.fromStorageKey("zoom"))
        assertEquals(VideoAspectMode.FILL, VideoAspectMode.fromStorageKey("FILL"))

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, VideoAspectMode.FIT.toExoResizeMode())
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, VideoAspectMode.ZOOM.toExoResizeMode())
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, VideoAspectMode.FILL.toExoResizeMode())

        assertEquals(SubtitleTextSize.MEDIUM, SubtitleTextSize.fromStorageKey(null))
        assertEquals(SubtitleTextSize.EXTRA_LARGE, SubtitleTextSize.fromStorageKey("extra_large"))
        assertTrue(SubtitleTextSize.SMALL.fractionalTextSize < SubtitleTextSize.MEDIUM.fractionalTextSize)
        assertTrue(SubtitleTextSize.MEDIUM.fractionalTextSize < SubtitleTextSize.LARGE.fractionalTextSize)
        assertTrue(SubtitleTextSize.LARGE.fractionalTextSize < SubtitleTextSize.EXTRA_LARGE.fractionalTextSize)

        assertEquals(SubtitleTextColor.WHITE, SubtitleTextColor.fromStorageKey("bad"))
        assertEquals(SubtitleTextColor.YELLOW, SubtitleTextColor.fromStorageKey("yellow"))
        assertEquals(SubtitleTextColor.CYAN, SubtitleTextColor.fromStorageKey("cyan"))

        val darkBoxStyle = PlayerComfortSettings(
            subtitleTextColor = SubtitleTextColor.YELLOW,
            subtitleBackgroundStyle = SubtitleBackgroundStyle.DARK_BOX
        ).toCaptionStyleCompat()
        assertEquals(SubtitleTextColor.YELLOW.argbColor, darkBoxStyle.foregroundColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_NONE, darkBoxStyle.edgeType)
        assertTrue(darkBoxStyle.backgroundColor != AndroidColor.TRANSPARENT)

        val outlineStyle = PlayerComfortSettings(
            subtitleTextColor = SubtitleTextColor.CYAN,
            subtitleBackgroundStyle = SubtitleBackgroundStyle.OUTLINE
        ).toCaptionStyleCompat()
        assertEquals(SubtitleTextColor.CYAN.argbColor, outlineStyle.foregroundColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_OUTLINE, outlineStyle.edgeType)
        assertEquals(AndroidColor.BLACK, outlineStyle.edgeColor)
        assertEquals(AndroidColor.TRANSPARENT, outlineStyle.backgroundColor)
    }

    @Test
    fun appPreferences_persistsAndReadsPlayerComfortSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences(context)

        prefs.setVideoAspectMode(VideoAspectMode.ZOOM)
        prefs.setSubtitleTextSize(SubtitleTextSize.LARGE)
        prefs.setSubtitleTextColor(SubtitleTextColor.YELLOW)
        prefs.setSubtitleBackgroundStyle(SubtitleBackgroundStyle.DARK_BOX)

        val saved = prefs.playerComfortSettingsFlow.first()
        assertEquals(VideoAspectMode.ZOOM, saved.videoAspectMode)
        assertEquals(SubtitleTextSize.LARGE, saved.subtitleTextSize)
        assertEquals(SubtitleTextColor.YELLOW, saved.subtitleTextColor)
        assertEquals(SubtitleBackgroundStyle.DARK_BOX, saved.subtitleBackgroundStyle)

        // Ripristina valori predefiniti per isolamento test
        prefs.setVideoAspectMode(VideoAspectMode.FIT)
        prefs.setSubtitleTextSize(SubtitleTextSize.MEDIUM)
        prefs.setSubtitleTextColor(SubtitleTextColor.WHITE)
        prefs.setSubtitleBackgroundStyle(SubtitleBackgroundStyle.DROP_SHADOW)
    }

    @Test
    fun applyComfortSettingsToPlayerView_updatesResizeModeAndSubtitleView() {
        val playerView = PlayerView(composeRule.activity)
        applyComfortSettingsToPlayerView(
            playerView = playerView,
            settings = PlayerComfortSettings(
                videoAspectMode = VideoAspectMode.ZOOM,
                subtitleTextSize = SubtitleTextSize.EXTRA_LARGE,
                subtitleTextColor = SubtitleTextColor.CYAN,
                subtitleBackgroundStyle = SubtitleBackgroundStyle.OUTLINE
            )
        )
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, playerView.resizeMode)
        assertNotNull(playerView.subtitleView)

        applyComfortSettingsToPlayerView(
            playerView = playerView,
            settings = PlayerComfortSettings(videoAspectMode = VideoAspectMode.FILL)
        )
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, playerView.resizeMode)
    }

    @Test
    fun fireTvPlayer_comfortPanelOpensAppliesLiveChangesAndRestoresFocusOnBack() {
        val resolver = VixSrcStreamResolver(OkHttpClient())
        val movie = MediaItem(
            id = 4201,
            mediaType = "movie",
            title = "Test Cinema 21:9",
            overview = "Film di test",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            voteAverage = 8.4,
            releaseYear = "2026"
        )
        val session = PlaybackSession(
            mediaItem = movie,
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                embedUrl = "https://vixsrc.to/movie/4201",
                streamUrl = "https://example.invalid/master.m3u8"
            ),
            metadataDurationMs = 7_200_000L
        )
        var comfortState by mutableStateOf(PlayerComfortSettings())

        composeRule.setContent {
            MyApplicationTheme {
                VixPlayerScreen(
                    session = session,
                    streamLang = "it",
                    vixSrcResolver = resolver,
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    comfortSettings = comfortState,
                    onSelectVideoAspectMode = { comfortState = comfortState.copy(videoAspectMode = it) },
                    onSelectSubtitleTextSize = { comfortState = comfortState.copy(subtitleTextSize = it) },
                    onSelectSubtitleTextColor = { comfortState = comfortState.copy(subtitleTextColor = it) },
                    onSelectSubtitleBackgroundStyle = { comfortState = comfortState.copy(subtitleBackgroundStyle = it) },
                    initialDurationMsOverride = 7_200_000L,
                    initialReadyForTest = true
                )
            }
        }
        composeRule.waitForIdle()

        // Verifica che il pulsante Comfort sia raggiungibile tramite D-Pad da Audio -> Destra
        composeRule.onNodeWithTag("player_audio_button").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_audio_button").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_comfort_button").assertIsFocused()

        // Apri il pannello Comfort
        composeRule.onNodeWithTag("player_comfort_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_comfort_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("player_comfort_subtitle_preview").assertIsDisplayed()

        // Seleziona Formato Video "Riempi" (ZOOM)
        composeRule.onNodeWithTag("player_aspect_option_zoom").performClick()
        composeRule.waitForIdle()
        assertEquals(VideoAspectMode.ZOOM, comfortState.videoAspectMode)
        composeRule.onNodeWithTag("player_aspect_toast_banner").assertIsDisplayed()

        // Verifica applicazione live sul PlayerView nativo
        val playerView = findPlayerView(composeRule.activity.window.decorView)
        assertNotNull("PlayerView nativo deve essere montato", playerView)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, playerView!!.resizeMode)

        // Seleziona stile sottotitoli (Grande, Giallo, Sfondo scuro)
        composeRule.onNodeWithTag("player_sub_size_option_large").performClick()
        composeRule.onNodeWithTag("player_sub_color_option_yellow").performClick()
        composeRule.onNodeWithTag("player_sub_bg_option_dark_box").performClick()
        composeRule.waitForIdle()

        assertEquals(SubtitleTextSize.LARGE, comfortState.subtitleTextSize)
        assertEquals(SubtitleTextColor.YELLOW, comfortState.subtitleTextColor)
        assertEquals(SubtitleBackgroundStyle.DARK_BOX, comfortState.subtitleBackgroundStyle)

        // Premi Escape/Back dal pannello e verifica chiusura + ripristino focus sul pulsante Comfort
        composeRule.onNodeWithTag("player_aspect_option_zoom").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_aspect_option_zoom").performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("player_comfort_panel").assertDoesNotExist()
        composeRule.onNodeWithTag("player_comfort_button").assertIsFocused()
    }
}
