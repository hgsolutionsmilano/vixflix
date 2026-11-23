package com.example

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.TmdbWatchProviderDto
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.PlaybackSession
import com.example.ui.components.ContinueWatchingSection
import com.example.ui.components.MediaCatalogRow
import com.example.ui.components.SafeEdgeHorizontalBringIntoViewSpec
import com.example.ui.components.StreamingPlatformsStrip
import com.example.ui.components.Top10Section
import com.example.ui.screens.TV_NAV_RAIL_COLLAPSED_WIDTH
import com.example.ui.screens.VixPlayerScreen
import com.example.ui.screens.shouldKeepScreenOnDuringPlayback
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.abs
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = VixFlixApplication::class, qualifiers = "w960dp-h540dp-television-notouch-dpad")
class FireTvOptimizationTest {

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

    private fun sampleContinueWatchingEntries(count: Int): List<WatchProgressEntity> {
        return (1..count).map { idx ->
            WatchProgressEntity(
                id = "tv_cw_$idx",
                tmdbId = 5000 + idx,
                mediaType = "tv",
                title = "Serie FireTV $idx",
                overview = "Descrizione serie $idx",
                posterPath = "/poster_$idx.jpg",
                backdropPath = "/backdrop_$idx.jpg",
                voteAverage = 8.2,
                releaseYear = "2026",
                seasonNumber = 1,
                episodeNumber = idx,
                positionMs = 12 * 60_000L,
                durationMs = 45 * 60_000L
            )
        }
    }

    private fun sampleMediaItems(prefix: String, count: Int): List<MediaItem> {
        return (1..count).map { idx ->
            MediaItem(
                id = 8000 + idx,
                mediaType = "movie",
                title = "$prefix $idx",
                overview = "Trama $idx",
                posterPath = "/p_$idx.jpg",
                backdropPath = "/b_$idx.jpg",
                voteAverage = 8.0,
                releaseYear = "2026"
            )
        }
    }

    private fun sampleProviders(count: Int): List<TmdbWatchProviderDto> {
        return (1..count).map { idx ->
            TmdbWatchProviderDto(
                providerId = 100 + idx,
                providerName = "Piattaforma $idx",
                logoPath = "/logo_$idx.png",
                displayPriority = idx
            )
        }
    }

    private fun findFirstPlayerView(root: View?): PlayerView? {
        if (root == null) return null
        if (root is PlayerView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findFirstPlayerView(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun isKeepScreenOnWindowFlagSet(): Boolean {
        val flags = composeRule.activity.window.attributes.flags
        return (flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
    }

    // -------------------------------------------------------------------------
    // 1. LAYOUT STRISCIA "CONTINUA A GUARDARE" E ALTRE STRISCE HOME SU FIRE TV
    // -------------------------------------------------------------------------

    @Test
    fun safeEdgeHorizontalBringIntoViewSpec_enforcesStartAndEndSafetyMargins() {
        val spec = SafeEdgeHorizontalBringIntoViewSpec(
            startMarginPx = 32f,
            endMarginPx = 36f
        )
        val containerWidth = 888f
        val cardWidth = 228f

        // Card a ridosso del menu laterale (offset 0) -> deve scorrere indietro di -32px per ripristinare il margine sinistro
        assertEquals(-32f, spec.calculateScrollDistance(0f, cardWidth, containerWidth), 0.01f)
        // Card parzialmente nascosta a sinistra
        assertEquals(-47f, spec.calculateScrollDistance(-15f, cardWidth, containerWidth), 0.01f)
        // Card a ridosso del bordo destro (offset + size == 888) -> deve scorrere avanti di +36px per liberare il bordo destro
        assertEquals(36f, spec.calculateScrollDistance(containerWidth - cardWidth, cardWidth, containerWidth), 0.01f)
        // Card già nell'area sicura [32, 852] -> nessuno scorrimento spurio
        assertEquals(0f, spec.calculateScrollDistance(32f, cardWidth, containerWidth), 0.01f)
        assertEquals(0f, spec.calculateScrollDistance(containerWidth - 36f - cardWidth, cardWidth, containerWidth), 0.01f)
        // Contenitore stretto: i margini si scalano proporzionalmente senza inversioni
        val narrowDelta = spec.calculateScrollDistance(0f, 200f, 220f)
        assertTrue("Il delta deve essere negativo ma limitato allo spazio residuo", narrowDelta < 0f && narrowDelta >= -20f)
    }

    @Test
    fun fireTvHome_continueWatchingStrip_neverOverlapsLeftRailOrClipsLastCard_acrossDpadNavigation() {
        val entries = sampleContinueWatchingEntries(6)

        composeRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = TV_NAV_RAIL_COLLAPSED_WIDTH)
                            .clipToBounds()
                    ) {
                        ContinueWatchingSection(
                            entries = entries,
                            onResumeClick = {},
                            onInfoClick = {},
                            onRemoveClick = {},
                            isTvLayout = true
                        )
                    }
                    // Simulazione fedele della barra menu laterale TV collassata (72.dp, zIndex 30f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(TV_NAV_RAIL_COLLAPSED_WIDTH)
                            .fillMaxHeight()
                            .zIndex(30f)
                            .testTag("mock_tv_nav_rail")
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val railRight = composeRule.onNodeWithTag("mock_tv_nav_rail").getUnclippedBoundsInRoot().right
        assertEquals("La barra laterale TV collassata deve occupare 72.dp", 72f, railRight.value, 1f)

        // 1. Prima locandina (non focalizzata): deve iniziare chiaramente dopo il margine occupato dal menu laterale
        val firstCardNode = composeRule.onNodeWithTag("continue_watching_card_tv_cw_1")
        val initialFirstBounds = firstCardNode.getUnclippedBoundsInRoot()
        assertTrue(
            "La prima locandina (left=${initialFirstBounds.left}) non deve sovrapporsi al menu laterale (right=$railRight)",
            initialFirstBounds.left >= railRight + 30.dp
        )
        assertTrue(
            "Larghezza standard card Continua a guardare deve restare 228.dp (effettiva=${initialFirstBounds.width})",
            abs(initialFirstBounds.width.value - 228f) <= 1.5f
        )

        // 2. Focus sulla prima locandina (scala 1.07x = ~8dp di espansione laterale): zero sovrapposizione con la barra laterale
        firstCardNode.requestFocus()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(180L)
        composeRule.waitForIdle()
        firstCardNode.assertIsFocused()

        val focusedFirstBounds = firstCardNode.getUnclippedBoundsInRoot()
        val outerGlowSpread = 11.dp * 1.07f // ~11.8dp di alone esterno animato su scala 1.07x
        // Nota: .testTag() è dopo graphicsLayer(scaleX = 1.07f), quindi .left riflette già il bordo sinistro scalato (~96.02dp)
        assertTrue(
            "Anche con scala focus 1.07x, il bordo sinistro scalato della prima card (left=${focusedFirstBounds.left}) deve restare ben oltre il menu ($railRight)",
            focusedFirstBounds.left >= railRight + 22.dp
        )
        assertTrue(
            "Anche includendo l'alone luminoso esterno (${outerGlowSpread}), la prima card non deve toccare il menu ($railRight)",
            (focusedFirstBounds.left - outerGlowSpread) > railRight
        )

        // 3. Navigazione D-Pad Destra fino all'ultima locandina (indice 5)
        for (step in 1..5) {
            composeRule.onNodeWithTag("continue_watching_card_tv_cw_$step")
                .performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(250L)
            composeRule.waitForIdle()
        }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()

        val lastCardNode = composeRule.onNodeWithTag("continue_watching_card_tv_cw_6")
        lastCardNode.assertIsDisplayed()
        lastCardNode.assertIsFocused()
        val lastBounds = lastCardNode.getUnclippedBoundsInRoot()
        val lastScaledRight = lastBounds.left + (lastBounds.width * 1.07f)
        assertTrue(
            "L'ultima locandina scalata (left=${lastBounds.left}, width=${lastBounds.width}, rightScaled=$lastScaledRight) deve mantenere margine sicuro dal bordo destro (960.dp)",
            lastScaledRight <= 960.dp - 24.dp
        )
        assertTrue(
            "Anche includendo l'alone luminoso esterno, l'ultima locandina (rightWithGlow=${lastScaledRight + outerGlowSpread}) non deve superare 960.dp",
            (lastScaledRight + outerGlowSpread) < 960.dp
        )
        assertEquals(
            "Tutte le locandine devono mantenere larghezza coerente",
            initialFirstBounds.width.value,
            lastBounds.width.value,
            1.5f
        )

        // 4. Ritorno D-Pad Sinistra fino alla prima locandina: non deve mai finire sotto il menu laterale
        for (step in 6 downTo 2) {
            composeRule.onNodeWithTag("continue_watching_card_tv_cw_$step")
                .performKeyInput { pressKey(Key.DirectionLeft) }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(250L)
            composeRule.waitForIdle()
        }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()

        firstCardNode.assertIsFocused()
        val returnedFirstBounds = firstCardNode.getUnclippedBoundsInRoot()
        assertTrue(
            "Dopo lo scorrimento di ritorno, la prima locandina scalata (left=${returnedFirstBounds.left}) deve mantenere pieno margine dal menu ($railRight)",
            returnedFirstBounds.left >= railRight + 22.dp
        )
        assertTrue(
            "Dopo il ritorno, nemmeno l'alone luminoso esterno (${returnedFirstBounds.left - outerGlowSpread}) deve sovrapporsi al menu laterale ($railRight)",
            (returnedFirstBounds.left - outerGlowSpread) > railRight
        )
    }

    @Test
    fun fireTvHome_otherHorizontalRows_alsoMaintainSafeLeftRailAndRightEdgeBounds() {
        val providers = sampleProviders(9)
        val top10Items = sampleMediaItems("Top10", 7)
        val catalogItems = sampleMediaItems("Catalog", 9)

        composeRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = TV_NAV_RAIL_COLLAPSED_WIDTH)
                            .clipToBounds()
                            .verticalScroll(rememberScrollState())
                    ) {
                        StreamingPlatformsStrip(
                            providers = providers,
                            selectedProvider = null,
                            onSelectProvider = {},
                            isTvLayout = true
                        )
                        Top10Section(
                            title = "Top 10 Oggi",
                            items = top10Items,
                            onItemClick = {},
                            isTvLayout = true
                        )
                        MediaCatalogRow(
                            title = "Film di Tendenza",
                            items = catalogItems,
                            onItemClick = {},
                            isTvLayout = true
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(TV_NAV_RAIL_COLLAPSED_WIDTH)
                            .fillMaxHeight()
                            .zIndex(30f)
                            .testTag("mock_tv_nav_rail_rows")
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val railRight = composeRule.onNodeWithTag("mock_tv_nav_rail_rows").getUnclippedBoundsInRoot().right

        // A. StreamingPlatformsStrip: prima card dopo il rail, ultima card dentro 960dp dopo D-Pad Right
        val firstPlatform = composeRule.onNodeWithTag("platform_card_101")
        assertTrue(firstPlatform.getUnclippedBoundsInRoot().left >= railRight + 30.dp)
        firstPlatform.requestFocus()
        composeRule.waitForIdle()
        for (i in 101..108) {
            composeRule.onNodeWithTag("platform_card_$i").performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(160L)
            composeRule.waitForIdle()
        }
        val lastPlatform = composeRule.onNodeWithTag("platform_card_109")
        lastPlatform.assertIsFocused()
        assertTrue(
            "L'ultima card piattaforma non deve essere tagliata sul bordo destro",
            lastPlatform.getUnclippedBoundsInRoot().right <= 960.dp - 32.dp
        )

        // B. Top10Section: prima card dopo il rail, ultima card dentro 960dp
        val firstTop10 = composeRule.onNodeWithTag("top10_card_1")
        assertTrue(firstTop10.getUnclippedBoundsInRoot().left >= railRight + 30.dp)
        firstTop10.requestFocus()
        composeRule.waitForIdle()
        for (rank in 1..6) {
            composeRule.onNodeWithTag("top10_card_$rank").performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(160L)
            composeRule.waitForIdle()
        }
        val lastTop10 = composeRule.onNodeWithTag("top10_card_7")
        lastTop10.assertIsFocused()
        assertTrue(
            "L'ultima card Top 10 non deve superare il bordo destro",
            lastTop10.getUnclippedBoundsInRoot().right <= 960.dp - 32.dp
        )

        // C. MediaCatalogRow: prima card dopo il rail, ultima card dentro 960dp
        val firstCatalog = composeRule.onNodeWithTag("poster_card_movie_8001")
        assertTrue(firstCatalog.getUnclippedBoundsInRoot().left >= railRight + 30.dp)
        firstCatalog.requestFocus()
        composeRule.waitForIdle()
        for (id in 8001..8008) {
            composeRule.onNodeWithTag("poster_card_movie_$id").performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(160L)
            composeRule.waitForIdle()
        }
        val lastCatalog = composeRule.onNodeWithTag("poster_card_movie_8009")
        lastCatalog.assertIsFocused()
        assertTrue(
            "L'ultima locandina del catalogo non deve superare il bordo destro",
            lastCatalog.getUnclippedBoundsInRoot().right <= 960.dp - 32.dp
        )
    }

    // -------------------------------------------------------------------------
    // 2. PREVENZIONE NATIVA SCREENSAVER DURANTE RIPRODUZIONE VIDEO (FIRE TV)
    // -------------------------------------------------------------------------

    @Test
    fun keepScreenOnPolicy_enablesOnlyDuringActivePlaybackOrActiveBuffering() {
        // Riproduzione attiva -> ON
        assertTrue(
            shouldKeepScreenOnDuringPlayback(
                isPlaying = true,
                isBuffering = false,
                playWhenReady = true,
                isResolvingStream = false,
                playbackEnded = false,
                hasError = false,
                isAppInBackground = false
            )
        )
        // Buffering temporaneo mentre la riproduzione è attiva (playWhenReady = true) -> ON
        assertTrue(
            shouldKeepScreenOnDuringPlayback(
                isPlaying = false,
                isBuffering = true,
                playWhenReady = true,
                isResolvingStream = false,
                playbackEnded = false,
                hasError = false,
                isAppInBackground = false
            )
        )
        // Risoluzione iniziale/cambio episodio con playWhenReady = true -> ON
        assertTrue(
            shouldKeepScreenOnDuringPlayback(
                isPlaying = false,
                isBuffering = false,
                playWhenReady = true,
                isResolvingStream = true,
                playbackEnded = false,
                hasError = false,
                isAppInBackground = false
            )
        )
        // In pausa -> OFF (anche se c'è buffering)
        assertFalse(
            shouldKeepScreenOnDuringPlayback(
                isPlaying = false,
                isBuffering = false,
                playWhenReady = false,
                isResolvingStream = false,
                playbackEnded = false,
                hasError = false,
                isAppInBackground = false
            )
        )
        assertFalse(
            shouldKeepScreenOnDuringPlayback(
                isPlaying = false,
                isBuffering = true,
                playWhenReady = false,
                isResolvingStream = false,
                playbackEnded = false,
                hasError = false,
                isAppInBackground = false
            )
        )
        // Fine riproduzione -> OFF
        assertFalse(
            shouldKeepScreenOnDuringPlayback(
                isPlaying = false,
                isBuffering = false,
                playWhenReady = true,
                isResolvingStream = false,
                playbackEnded = true,
                hasError = false,
                isAppInBackground = false
            )
        )
        // Errore stream -> OFF
        assertFalse(
            shouldKeepScreenOnDuringPlayback(
                isPlaying = true,
                isBuffering = false,
                playWhenReady = true,
                isResolvingStream = false,
                playbackEnded = false,
                hasError = true,
                isAppInBackground = false
            )
        )
        // App in background -> sempre OFF
        assertFalse(
            shouldKeepScreenOnDuringPlayback(
                isPlaying = true,
                isBuffering = false,
                playWhenReady = true,
                isResolvingStream = false,
                playbackEnded = false,
                hasError = false,
                isAppInBackground = true
            )
        )
    }

    @Test
    fun vixPlayerScreen_togglesNativeWindowFlagAndPlayerViewKeepScreenOn_acrossAllStates() {
        val movie = MediaItem(
            id = 9101,
            mediaType = "tv",
            title = "Serie KeepScreenOn",
            overview = "Test prevenzione screensaver Fire TV",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.6,
            releaseYear = "2026"
        )
        val ep1Session = PlaybackSession(
            mediaItem = movie,
            seasonNumber = 1,
            episodeNumber = 1,
            episodeTitle = "Episodio 1",
            startPositionMs = 60_000L,
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/s1e1.m3u8",
                embedUrl = "https://vixsrc.to/tv/9101/1/1",
                isDirectHls = true
            ),
            sessionToken = 1L
        )
        val errorEpisodeSession = ep1Session.copy(
            episodeNumber = 2,
            episodeTitle = "Episodio 2 Errore",
            resolvedStream = null,
            errorMessage = "Flusso temporaneamente non disponibile",
            sessionToken = 2L
        )
        val ep3Session = ep1Session.copy(
            episodeNumber = 3,
            episodeTitle = "Episodio 3",
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/s1e3.m3u8",
                embedUrl = "https://vixsrc.to/tv/9101/1/3",
                isDirectHls = true
            ),
            errorMessage = null,
            sessionToken = 3L
        )

        var showPlayer by mutableStateOf(false)
        var currentSession by mutableStateOf(ep1Session)
        val resolver = VixSrcStreamResolver(OkHttpClient())

        composeRule.setContent {
            MyApplicationTheme {
                if (showPlayer) {
                    VixPlayerScreen(
                        session = currentSession,
                        streamLang = "it",
                        vixSrcResolver = resolver,
                        onDynamicHlsFound = {},
                        onTogglePlayerMode = {},
                        onToggleLanguage = {},
                        onProgressUpdate = { _, _ -> },
                        onNextEpisode = {},
                        onClose = { showPlayer = false },
                        initialDurationMsOverride = 3_000_000L,
                        initialReadyForTest = true,
                        simulateBufferingOnSeekForTest = true
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().testTag("non_player_home"))
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Schermata non-player (Home): FLAG_KEEP_SCREEN_ON deve essere disattivo
        assertFalse("Fuori dal player lo screensaver NON deve essere bloccato", isKeepScreenOnWindowFlagSet())

        // 2. Avvio riproduzione video: sia FLAG_KEEP_SCREEN_ON sulla Window sia PlayerView.keepScreenOn attivi
        showPlayer = true
        composeRule.waitForIdle()
        val playerView = findFirstPlayerView(composeRule.activity.window.decorView)
        assertNotNull("PlayerView deve essere montato nel player", playerView)
        assertTrue("FLAG_KEEP_SCREEN_ON deve essere attivo durante la riproduzione", isKeepScreenOnWindowFlagSet())
        assertTrue("PlayerView.keepScreenOn deve essere true durante la riproduzione", playerView!!.keepScreenOn)

        // 3. Controlli nascosti (tutto schermo) e poi nuovamente visibili: resta attivo in entrambi i casi
        composeRule.onNodeWithTag("vix_player_screen").performKeyInput { pressKey(Key.Menu) }
        composeRule.waitForIdle()
        assertTrue("A tutto schermo senza controlli FLAG_KEEP_SCREEN_ON deve restare attivo", isKeepScreenOnWindowFlagSet())
        assertTrue("A tutto schermo senza controlli PlayerView.keepScreenOn deve restare true", playerView.keepScreenOn)

        composeRule.onNodeWithTag("vix_player_screen").performKeyInput { pressKey(Key.Menu) }
        composeRule.waitForIdle()
        assertTrue("Con controlli visibili durante riproduzione FLAG_KEEP_SCREEN_ON deve restare attivo", isKeepScreenOnWindowFlagSet())

        // 4. Buffering temporaneo durante riproduzione attiva (seek): resta attivo
        composeRule.onNodeWithTag("vix_player_screen").performKeyInput { pressKey(Key.MediaFastForward) }
        composeRule.waitForIdle()
        assertTrue("Durante buffering temporaneo con riproduzione attiva FLAG_KEEP_SCREEN_ON deve restare attivo", isKeepScreenOnWindowFlagSet())
        assertTrue("Durante buffering temporaneo PlayerView.keepScreenOn deve restare true", playerView.keepScreenOn)

        // 5. Pausa video: disattiva immediatamente FLAG_KEEP_SCREEN_ON e PlayerView.keepScreenOn
        composeRule.onNodeWithTag("vix_player_screen").performKeyInput { pressKey(Key.MediaPause) }
        composeRule.waitForIdle()
        assertFalse("In pausa FLAG_KEEP_SCREEN_ON deve essere disattivato", isKeepScreenOnWindowFlagSet())
        assertFalse("In pausa PlayerView.keepScreenOn deve essere false", playerView.keepScreenOn)

        // 6. Ripresa riproduzione: riattiva immediatamente FLAG_KEEP_SCREEN_ON e PlayerView.keepScreenOn
        composeRule.onNodeWithTag("vix_player_screen").performKeyInput { pressKey(Key.MediaPlay) }
        composeRule.waitForIdle()
        assertTrue("Alla ripresa FLAG_KEEP_SCREEN_ON deve riattivarsi", isKeepScreenOnWindowFlagSet())
        assertTrue("Alla ripresa PlayerView.keepScreenOn deve tornare true", playerView.keepScreenOn)

        // 7. Cambio episodio verso uno stato di errore: disattiva immediatamente
        currentSession = errorEpisodeSession
        composeRule.waitForIdle()
        assertFalse("In stato di errore FLAG_KEEP_SCREEN_ON deve essere disattivato", isKeepScreenOnWindowFlagSet())
        assertFalse("In stato di errore PlayerView.keepScreenOn deve essere false", playerView.keepScreenOn)

        // 8. Cambio episodio verso un episodio valido: riattiva correttamente
        currentSession = ep3Session
        composeRule.waitForIdle()
        assertTrue("Sul nuovo episodio valido FLAG_KEEP_SCREEN_ON deve essere attivo", isKeepScreenOnWindowFlagSet())
        assertTrue("Sul nuovo episodio valido PlayerView.keepScreenOn deve essere true", playerView.keepScreenOn)

        // 9. App in background (ON_PAUSE / ON_STOP): disattiva immediatamente
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        assertFalse("In background FLAG_KEEP_SCREEN_ON deve essere disattivato", isKeepScreenOnWindowFlagSet())
        assertFalse("In background PlayerView.keepScreenOn deve essere false", playerView.keepScreenOn)

        // 10. Ritorno in primo piano + ripresa riproduzione: si riattiva
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vix_player_screen").performKeyInput { pressKey(Key.MediaPlay) }
        composeRule.waitForIdle()
        assertTrue("Al ritorno in primo piano in riproduzione FLAG_KEEP_SCREEN_ON deve tornare attivo", isKeepScreenOnWindowFlagSet())

        // 11. Uscita dal player: flag rimosso e mai mantenuto nelle altre schermate
        showPlayer = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("non_player_home").assertIsDisplayed()
        assertFalse("All'uscita dal player FLAG_KEEP_SCREEN_ON deve essere rimosso", isKeepScreenOnWindowFlagSet())
    }
}
