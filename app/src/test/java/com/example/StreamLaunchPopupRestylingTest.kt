package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.data.local.StreamingBufferPreset
import com.example.data.remote.MediaItem
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.screens.VixPlayerScreen
import com.example.ui.screens.VixStreamLaunchErrorPopup
import com.example.ui.screens.VixStreamLoadingPopup
import com.example.ui.theme.MyApplicationTheme
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-notouch-dpad")
class StreamLaunchPopupRestylingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleSeries = MediaItem(
        id = 1399,
        mediaType = "tv",
        title = "House of the Dragon",
        overview = "Trama lunghissima che non deve appesantire il popup di avvio streaming.",
        posterPath = "/poster.jpg",
        backdropPath = "/backdrop.jpg",
        voteAverage = 8.4,
        releaseYear = "2022"
    )

    private val sampleMovie = MediaItem(
        id = 550,
        mediaType = "movie",
        title = "Fight Club",
        overview = "Sinossi del film che non deve apparire nel popup minimal.",
        posterPath = "/fc.jpg",
        backdropPath = "/fc_bg.jpg",
        voteAverage = 8.8,
        releaseYear = "1999"
    )

    @Test
    fun fireTvLoadingPopup_showsMinimalHierarchyRemovesClutterTrapsFocusAndDebouncesCancel() {
        val cancelClicks = AtomicInteger(0)
        val session = PlaybackSession(
            mediaItem = sampleSeries,
            seasonNumber = 2,
            episodeNumber = 5,
            episodeTitle = "Reggente",
            startPositionMs = 750_000L, // 12:30
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 101L
        )

        composeRule.setContent {
            MyApplicationTheme {
                val cancelFocus = remember { FocusRequester() }
                Box(modifier = Modifier.fillMaxSize()) {
                    VixStreamLoadingPopup(
                        session = session,
                        streamLang = "it",
                        bufferPreset = StreamingBufferPreset.BALANCED,
                        cancelFocusRequester = cancelFocus,
                        onCancel = { cancelClicks.incrementAndGet() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 20.dp)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Elementi essenziali presenti e leggibili
        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
        composeRule.onNodeWithText("House of the Dragon").assertIsDisplayed()
        composeRule.onNodeWithTag("loading_popup_series_info").assertIsDisplayed()
        composeRule.onNodeWithText("S2 · E5 · Reggente").assertIsDisplayed()
        composeRule.onNodeWithTag("loading_popup_resume_badge").assertIsDisplayed()
        composeRule.onNodeWithText("Ripresa da 12:30").assertIsDisplayed()
        composeRule.onNodeWithTag("loading_popup_source_label").assertIsDisplayed()
        composeRule.onNodeWithText("VixSrc").assertIsDisplayed()
        composeRule.onNodeWithText("Ricerca sorgente…").assertIsDisplayed()

        // 2. Rumore visivo e testi tecnici superflui rimossi
        assertEquals(0, composeRule.onAllNodesWithText("Compatibile", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Trama lunghissima", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Connessione diretta a vixsrc.to", substring = true).fetchSemanticsNodes().size)

        // 3. Dimensioni compatte e proporzionate allo schermo TV (nessun taglio)
        val popupBounds = composeRule.onNodeWithTag("video_loading_popup").getUnclippedBoundsInRoot()
        assertTrue("Il popup deve restare compatto (<= 400dp), trovato ${popupBounds.width}", popupBounds.width <= 400.dp)
        assertTrue("Il popup non deve superare i limiti dello schermo TV", popupBounds.left >= 0.dp && popupBounds.right <= 960.dp)
        assertTrue("Il popup deve occupare altezza compatta (<= 240dp), trovato ${popupBounds.height}", popupBounds.height <= 240.dp)

        // 4. Focus D-Pad automatico su Annulla + trappola direzionale
        val cancelBtn = composeRule.onNodeWithTag("loading_cancel_button")
        cancelBtn.assertIsDisplayed().assertIsFocused()
        val cancelBounds = cancelBtn.getUnclippedBoundsInRoot()
        assertTrue("Target touch/TV minimo 48dp", cancelBounds.height >= 48.dp)

        cancelBtn.performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        cancelBtn.assertIsFocused()

        // 5. Protezione doppio evento: doppio click ravvicinato chiama onCancel 1 sola volta
        cancelBtn.performClick()
        cancelBtn.performClick()
        composeRule.waitForIdle()
        assertEquals("La doppia pressione su Annulla non deve emettere doppi eventi", 1, cancelClicks.get())
    }

    @Test
    fun fireTvErrorPopup_navigatesDpadBetweenRetryAndCloseAndPreventsDuplicateStreamStart() {
        val retryCalls = AtomicInteger(0)
        val closeCalls = AtomicInteger(0)
        val errorSession = PlaybackSession(
            mediaItem = sampleMovie,
            isResolvingStream = false,
            resolvedStream = null,
            errorMessage = "Accesso negato dal server VixSrc (HTTP 403).",
            sessionToken = 202L
        )

        composeRule.setContent {
            MyApplicationTheme {
                VixPlayerScreen(
                    session = errorSession,
                    streamLang = "it",
                    vixSrcResolver = VixSrcStreamResolver(OkHttpClient()),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onInvalidateAndRetryStream = { retryCalls.incrementAndGet() },
                    onClose = { closeCalls.incrementAndGet() },
                    initialReadyForTest = false
                )
            }
        }
        composeRule.waitForIdle()

        // Popup errore minimal visibile con focus immediato su Riprova
        composeRule.onNodeWithTag("video_error_popup").assertIsDisplayed()
        composeRule.onNodeWithTag("video_error_message").assertIsDisplayed()
        val retryBtn = composeRule.onNodeWithTag("player_retry_stream_button")
        val closeBtn = composeRule.onNodeWithTag("player_error_close_button")

        retryBtn.assertIsDisplayed().assertIsFocused()
        closeBtn.assertIsDisplayed()

        // Assenza sovrapposizioni tra Riprova e Chiudi
        val retryBounds = retryBtn.getUnclippedBoundsInRoot()
        val closeBounds = closeBtn.getUnclippedBoundsInRoot()
        assertTrue("Riprova e Chiudi non devono sovrapporsi", retryBounds.right < closeBounds.left)
        assertTrue("Altezza Riprova >= 48dp", retryBounds.height >= 48.dp)
        assertTrue("Altezza Chiudi >= 48dp", closeBounds.height >= 48.dp)

        // Navigazione D-Pad fluida Riprova -> Destra -> Chiudi -> Sinistra -> Riprova
        retryBtn.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        closeBtn.assertIsFocused()

        closeBtn.performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        retryBtn.assertIsFocused()

        // Doppia pressione rapida su Riprova non deve riavviare due volte la stessa sorgente
        retryBtn.performClick()
        retryBtn.performClick()
        composeRule.waitForIdle()
        assertEquals("Una doppia pressione rapida su Riprova deve avviare 1 solo retry", 1, retryCalls.get())
        assertEquals(0, closeCalls.get())
    }

    @Test
    fun fireTvLoadingPopup_backKeyClosesCleanlyInsidePlayerScreen() {
        val closeCalls = AtomicInteger(0)
        val resolvingSession = PlaybackSession(
            mediaItem = sampleMovie,
            isResolvingStream = true,
            resolvedStream = null,
            sessionToken = 303L
        )

        composeRule.setContent {
            MyApplicationTheme {
                VixPlayerScreen(
                    session = resolvingSession,
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

        composeRule.onNodeWithTag("video_loading_popup").assertIsDisplayed()
        composeRule.onNodeWithTag("loading_cancel_button").assertIsFocused()

        // BACK (Escape) chiude immediatamente il popup tornando alla schermata precedente
        composeRule.onNodeWithTag("loading_cancel_button").performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.waitForIdle()
        assertEquals(1, closeCalls.get())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-port")
    fun androidPhoneNarrowScreen_rendersLoadingAndErrorPopupsWithoutClippingOrOverlap() {
        val closeCalls = AtomicInteger(0)
        val session = PlaybackSession(
            mediaItem = sampleMovie,
            isResolvingStream = false,
            resolvedStream = null,
            errorMessage = "Timeout di connessione verso VixSrc. Verifica la rete e riprova.",
            sessionToken = 404L
        )

        composeRule.setContent {
            MyApplicationTheme {
                val retryFocus = remember { FocusRequester() }
                Box(modifier = Modifier.fillMaxSize()) {
                    VixStreamLaunchErrorPopup(
                        session = session,
                        streamLang = "it",
                        errorMessage = session.errorMessage!!,
                        retryFocusRequester = retryFocus,
                        onRetry = {},
                        onClose = { closeCalls.incrementAndGet() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 20.dp)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val popupBounds = composeRule.onNodeWithTag("video_error_popup").getUnclippedBoundsInRoot()
        assertTrue("Su smartphone 360dp il popup deve rientrare nei margini", popupBounds.left >= 0.dp && popupBounds.right <= 360.dp)

        val retryBounds = composeRule.onNodeWithTag("player_retry_stream_button").getUnclippedBoundsInRoot()
        val closeBounds = composeRule.onNodeWithTag("player_error_close_button").getUnclippedBoundsInRoot()
        assertTrue("Nessuna sovrapposizione pulsanti su smartphone", retryBounds.right < closeBounds.left)
        assertTrue("Target touch Riprova >= 48dp", retryBounds.height >= 48.dp)
        assertTrue("Target touch Chiudi >= 48dp", closeBounds.height >= 48.dp)

        composeRule.onNodeWithTag("player_error_close_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, closeCalls.get())
    }
}
