package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppPreferences
import com.example.data.local.CreditsDetectionState
import com.example.data.local.CreditsSegmentEntity
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.IntroSegmentEntity
import com.example.data.local.IntroSegmentSource
import com.example.data.local.PlayerAdvancedSettings
import com.example.data.local.PlayerSegmentKeys
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.VerifiedNextEpisode
import com.example.data.local.VixFlixDatabase
import com.example.data.local.formatSeriesSeasonEpisodeLine
import com.example.data.local.isIntroActiveAtPosition
import com.example.data.local.resolveCreditsTrigger
import com.example.data.remote.MediaItem
import com.example.data.remote.ResolvedStream
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbSeasonSummaryDto
import com.example.data.remote.VixSrcStreamResolver
import com.example.data.repository.VixFlixRepository
import com.example.ui.GitHubUpdateUiState
import com.example.ui.PlaybackSession
import com.example.ui.screens.PlayerAudioTrackOption
import com.example.ui.screens.SettingsCategory
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VixPlayerScreen
import com.example.ui.screens.formatMsToHhMmSs
import com.example.ui.theme.VixFlixTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = VixFlixApplication::class)
class AdvancedVideoPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleMovie = MediaItem(
        id = 7001,
        mediaType = "movie",
        title = "Interstellar: Viaggio oltre le stelle con un titolo particolarmente lungo",
        overview = "Film di fantascienza.",
        posterPath = null,
        backdropPath = null,
        voteAverage = 8.7,
        releaseYear = "2014"
    )

    private val sampleSeries = MediaItem(
        id = 8002,
        mediaType = "tv",
        title = "Il mistero della villa",
        overview = "Serie thriller.",
        posterPath = null,
        backdropPath = null,
        voteAverage = 8.4,
        releaseYear = "2024"
    )

    private fun testResolver(): VixSrcStreamResolver = VixSrcStreamResolver(OkHttpClient())

    @Test
    fun pureFormattingAndDetectionHelpers_respectReliabilityRules() {
        // Stagione/Episodio formattazione
        assertEquals(
            "STAGIONE 2 • EPISODIO 5 – Il segreto",
            formatSeriesSeasonEpisodeLine(2, 5, "Il segreto")
        )
        assertEquals(
            "STAGIONE 1 • EPISODIO 3",
            formatSeriesSeasonEpisodeLine(1, 3, null)
        )
        assertEquals(
            "STAGIONE 1 • EPISODIO 3",
            formatSeriesSeasonEpisodeLine(1, 3, "   ")
        )
        // Non inventa mai dati se mancanti o invalidi
        assertNull(formatSeriesSeasonEpisodeLine(null, 5, "Titolo"))
        assertNull(formatSeriesSeasonEpisodeLine(2, null, "Titolo"))
        assertNull(formatSeriesSeasonEpisodeLine(0, 1, "Speciale"))

        // Intro detection: mai attivo senza segmento affidabile
        assertFalse(isIntroActiveAtPosition(null, 30_000L, 2_400_000L))

        val validIntro = IntroSegmentEntity(
            episodeKey = PlayerSegmentKeys.introEpisodeKey(8002, 2, 5),
            seriesTmdbId = 8002,
            seasonNumber = 2,
            episodeNumber = 5,
            introStartMs = 15_000L,
            introEndMs = 75_000L,
            source = IntroSegmentSource.LOCAL_DB.storageKey
        )
        assertFalse(isIntroActiveAtPosition(validIntro, 14_999L, 2_400_000L))
        assertTrue(isIntroActiveAtPosition(validIntro, 15_000L, 2_400_000L))
        assertTrue(isIntroActiveAtPosition(validIntro, 74_999L, 2_400_000L))
        assertFalse(isIntroActiveAtPosition(validIntro, 75_000L, 2_400_000L))

        val unavailableIntro = validIntro.copy(source = IntroSegmentSource.UNAVAILABLE.storageKey)
        assertFalse(isIntroActiveAtPosition(unavailableIntro, 30_000L, 2_400_000L))

        // Credits detection: distingue marker affidabili da fallback configurabile
        val storedCredits = CreditsSegmentEntity(
            contentKey = PlayerSegmentKeys.creditsContentKey(7001, "movie"),
            tmdbId = 7001,
            mediaType = "movie",
            creditsStartMs = 6_800_000L,
            durationMs = 7_200_000L,
            detectionState = CreditsDetectionState.VERIFIED_METADATA.storageKey
        )
        val verifiedTrigger = resolveCreditsTrigger(
            segment = storedCredits,
            durationMs = 7_200_000L,
            isSeries = false,
            creditsDetectionEnabled = true
        )
        assertNotNull(verifiedTrigger)
        assertEquals(6_800_000L, verifiedTrigger?.creditsStartMs)
        assertEquals(CreditsDetectionState.VERIFIED_METADATA, verifiedTrigger?.source)

        val fallbackTrigger = resolveCreditsTrigger(
            segment = null,
            durationMs = 3_600_000L,
            isSeries = true,
            creditsDetectionEnabled = true
        )
        assertNotNull(fallbackTrigger)
        assertEquals(CreditsDetectionState.FALLBACK_THRESHOLD, fallbackTrigger?.source)
        assertTrue((fallbackTrigger?.creditsStartMs ?: 0L) >= 3_300_000L)

        // Disabilitato da impostazioni -> null
        assertNull(
            resolveCreditsTrigger(
                segment = storedCredits,
                durationMs = 7_200_000L,
                isSeries = false,
                creditsDetectionEnabled = false
            )
        )
        // Clip troppo corta senza marker affidabili -> null
        assertNull(
            resolveCreditsTrigger(
                segment = null,
                durationMs = 45_000L,
                isSeries = false,
                creditsDetectionEnabled = true
            )
        )
    }

    @Test
    fun moviePlayer_showsCleanLayoutWithoutTopBar_timeBelowSeekBar_andThreeCircularControls() {
        val movieSession = PlaybackSession(
            mediaItem = sampleMovie,
            startPositionMs = 2_142_000L, // 00:35:42
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/movie.m3u8",
                embedUrl = "https://vixsrc.to/movie/7001",
                isDirectHls = true
            )
        )

        composeRule.setContent {
            VixFlixTheme {
                VixPlayerScreen(
                    session = movieSession,
                    streamLang = "it",
                    vixSrcResolver = testResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    initialDurationMsOverride = 6_738_000L, // 01:52:18
                    initialReadyForTest = true
                )
            }
        }

        // 1. Nessun elemento superiore (nessuna freccia indietro, nessuna scritta verde, nessun pulsante top)
        composeRule.onNodeWithTag("player_back_button").assertDoesNotExist()
        composeRule.onNodeWithTag("player_audio_lang_button").assertDoesNotExist()
        composeRule.onNodeWithTag("open_external_vixsrc_button").assertDoesNotExist()
        composeRule.onNodeWithText("VixSrc HLS 1080p", substring = true).assertDoesNotExist()

        // 2. Titolo film sopra la barra, nessun sottotitolo serie
        composeRule.onNodeWithTag("player_media_title_header").assertIsDisplayed()
        composeRule.onNodeWithTag("player_media_main_title")
            .assertIsDisplayed()
            .assertTextEquals(sampleMovie.title)
        composeRule.onNodeWithTag("player_media_series_subtitle").assertDoesNotExist()

        // 3. Barra di avanzamento + formato tempo esatto "00:35:42 / 01:52:18" subito sotto
        composeRule.onNodeWithTag("player_time_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("player_time_display")
            .assertIsDisplayed()
            .assertTextEquals("00:35:42 / 01:52:18")

        // 4. Esclusivamente i tre pulsanti circolari sotto le informazioni temporali
        composeRule.onNodeWithTag("player_controls_row").assertIsDisplayed()
        composeRule.onNodeWithTag("exo_play_pause_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_audio_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_vixsrc_link_button").assertIsDisplayed()

        // 5. Vecchi pulsanti eliminati definitivamente
        composeRule.onNodeWithTag("player_replay_10_button").assertDoesNotExist()
        composeRule.onNodeWithTag("player_forward_10_button").assertDoesNotExist()
        composeRule.onNodeWithTag("player_resize_button").assertDoesNotExist()
        composeRule.onNodeWithTag("player_manual_complete_button").assertDoesNotExist()
        composeRule.onNodeWithTag("next_episode_button").assertDoesNotExist()
        composeRule.onNodeWithTag("player_skip_intro_button").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun playerCircularButtons_togglePlayPause_selectAudioTrack_andHandleVixSrcLinkWithDpad() {
        val movieSession = PlaybackSession(
            mediaItem = sampleMovie,
            startPositionMs = 90_000L,
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/movie.m3u8",
                embedUrl = "https://vixsrc.to/movie/7001?lang=it",
                isDirectHls = true
            )
        )
        val mockTracks = listOf(
            PlayerAudioTrackOption(
                id = "track_it",
                title = "Italiano (IT)",
                details = "Surround 5.1 • Dolby Digital+ • 384 kbps",
                isSelected = true
            ),
            PlayerAudioTrackOption(
                id = "track_en",
                title = "Inglese (EN)",
                details = "Stereo 2.0 • AAC • 192 kbps",
                isSelected = false
            )
        )
        var selectedTrack: PlayerAudioTrackOption? = null
        var openedVixUrl: String? = null
        var closedCalled = false

        composeRule.setContent {
            VixFlixTheme {
                VixPlayerScreen(
                    session = movieSession,
                    streamLang = "it",
                    vixSrcResolver = testResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = { closedCalled = true },
                    initialDurationMsOverride = 3_600_000L,
                    initialReadyForTest = true,
                    audioTracksOverrideForTest = mockTracks,
                    onAudioTrackSelectedForTest = { selectedTrack = it },
                    onOpenVixSrcLinkOverrideForTest = { openedVixUrl = it }
                )
            }
        }

        // Play / Pausa commuta lo stato e l'icona (Pausa -> Riproduci -> Pausa)
        composeRule.onNodeWithTag("exo_play_pause_button")
            .assertContentDescriptionEquals("Pausa")
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exo_play_pause_button")
            .assertContentDescriptionEquals("Riproduci")
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exo_play_pause_button")
            .assertContentDescriptionEquals("Pausa")

        // Navigazione D-Pad tra i 3 pulsanti circolari e la barra del tempo
        composeRule.onNodeWithTag("exo_play_pause_button").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exo_play_pause_button").assertIsFocused()

        composeRule.onNodeWithTag("exo_play_pause_button").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_audio_button").assertIsFocused()

        composeRule.onNodeWithTag("player_audio_button").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_vixsrc_link_button").assertIsFocused()

        composeRule.onNodeWithTag("player_vixsrc_link_button").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_time_bar").assertIsFocused()

        composeRule.onNodeWithTag("player_time_bar").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exo_play_pause_button").assertIsFocused()

        // Apri selettore Audio, verifica lingua/dettagli e seleziona Inglese
        composeRule.onNodeWithTag("player_audio_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_audio_selector_panel").assertIsDisplayed()
        composeRule.onNodeWithText("Italiano (IT)").assertIsDisplayed()
        composeRule.onNodeWithText("Surround 5.1 • Dolby Digital+ • 384 kbps").assertIsDisplayed()
        composeRule.onNodeWithText("Inglese (EN)").assertIsDisplayed()

        composeRule.onNodeWithTag("player_audio_option_track_en").performClick()
        composeRule.waitForIdle()
        assertEquals("track_en", selectedTrack?.id)
        composeRule.onNodeWithTag("player_audio_selector_panel").assertDoesNotExist()
        assertFalse(closedCalled)

        // Apri pannello Link VixSrc, verifica URL reale VixSrc, testa BACK e apertura link
        composeRule.onNodeWithTag("player_vixsrc_link_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_vixsrc_link_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("vixsrc_link_url_text")
            .assertTextContains("https://vixsrc.to/movie/7001", substring = true)

        // BACK chiude il pannello senza chiudere il player
        composeRule.onNodeWithTag("vix_player_screen").performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_vixsrc_link_panel").assertDoesNotExist()
        assertFalse(closedCalled)

        // Riapri pannello Link VixSrc e premi "Apri Link"
        composeRule.onNodeWithTag("player_vixsrc_link_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vixsrc_copy_link_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Copiato").assertIsDisplayed()

        composeRule.onNodeWithTag("vixsrc_open_link_button").performClick()
        composeRule.waitForIdle()
        assertNotNull(openedVixUrl)
        assertTrue(openedVixUrl!!.startsWith("https://vixsrc.to/movie/7001"))
        composeRule.onNodeWithTag("player_vixsrc_link_panel").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun playerSeekBarScrubbing_showsOnlyThumbnailPreview_andHidesItOnConfirm() {
        val intro = IntroSegmentEntity(
            episodeKey = PlayerSegmentKeys.introEpisodeKey(sampleSeries.id, 2, 5),
            seriesTmdbId = sampleSeries.id,
            seasonNumber = 2,
            episodeNumber = 5,
            introStartMs = 20_000L,
            introEndMs = 90_000L,
            source = IntroSegmentSource.LOCAL_DB.storageKey
        )
        val seriesSession = PlaybackSession(
            mediaItem = sampleSeries,
            seasonNumber = 2,
            episodeNumber = 5,
            episodeTitle = "Il segreto",
            startPositionMs = 30_000L, // 00:00:30
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/s2e5.m3u8",
                embedUrl = "https://vixsrc.to/tv/8002/2/5",
                isDirectHls = true
            ),
            introSegment = intro
        )
        val decodedFrame = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        var lastCommittedMs = -1L

        composeRule.setContent {
            VixFlixTheme {
                VixPlayerScreen(
                    session = seriesSession,
                    streamLang = "it",
                    vixSrcResolver = testResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { pos, _ -> lastCommittedMs = pos },
                    onNextEpisode = {},
                    onClose = {},
                    initialDurationMsOverride = 3_600_000L, // 01:00:00
                    initialReadyForTest = true,
                    previewBitmapProvider = { decodedFrame }
                )
            }
        }

        // Prima dello scrubbing: titolo visibile, nessuna miniatura
        composeRule.onNodeWithTag("player_media_title_header").assertIsDisplayed()
        composeRule.onNodeWithTag("player_seek_thumbnail_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("player_time_display").assertTextEquals("00:00:30 / 01:00:00")

        // Focalizza barra del tempo e premi freccia DESTRA -> entra in scrubbing
        composeRule.onNodeWithTag("player_time_bar").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_time_bar").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        // Durante lo scrubbing: ESCLUSIVAMENTE la miniatura visibile; titolo e Salta Intro nascosti; tempo aggiornato
        composeRule.onNodeWithTag("player_seek_thumbnail_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("player_media_title_header").assertDoesNotExist()
        composeRule.onNodeWithTag("player_skip_intro_button").assertDoesNotExist()
        composeRule.onNodeWithTag("player_time_display").assertTextEquals("00:00:40 / 01:00:00")

        // Premi OK -> conferma la posizione selezionata e nasconde subito la miniatura
        composeRule.onNodeWithTag("player_time_bar").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        assertEquals(40_000L, lastCommittedMs)
        composeRule.onNodeWithTag("player_seek_thumbnail_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("player_media_title_header").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun playerSeekBarScrubbing_withoutDecodableFrame_neverShowsFakeThumbnail() {
        val movieSession = PlaybackSession(
            mediaItem = sampleMovie,
            startPositionMs = 60_000L,
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/movie.m3u8",
                embedUrl = "https://vixsrc.to/movie/7001",
                isDirectHls = true
            )
        )

        composeRule.setContent {
            VixFlixTheme {
                VixPlayerScreen(
                    session = movieSession,
                    streamLang = "it",
                    vixSrcResolver = testResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    initialDurationMsOverride = 3_600_000L,
                    initialReadyForTest = true,
                    previewBitmapProvider = { null }
                )
            }
        }

        assertEquals("00:35:42", formatMsToHhMmSs(2_142_000L))
        assertEquals("01:52:18", formatMsToHhMmSs(6_738_000L))

        composeRule.onNodeWithTag("player_time_bar").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_time_bar").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        // Se il frame reale non è disponibile -> nessuna immagine segnaposto o casuale mostrata
        composeRule.onNodeWithTag("player_seek_thumbnail_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("player_time_display").assertTextEquals("00:01:10 / 01:00:00")
    }

    @Test
    fun seriesPlayer_showsTitleAndSeasonEpisodeAboveSeekBar_andSkipIntroOnlyWhenReliable() {
        val intro = IntroSegmentEntity(
            episodeKey = PlayerSegmentKeys.introEpisodeKey(sampleSeries.id, 2, 5),
            seriesTmdbId = sampleSeries.id,
            seasonNumber = 2,
            episodeNumber = 5,
            introStartMs = 20_000L,
            introEndMs = 88_000L,
            source = IntroSegmentSource.LOCAL_DB.storageKey
        )
        val seriesSession = PlaybackSession(
            mediaItem = sampleSeries,
            seasonNumber = 2,
            episodeNumber = 5,
            episodeTitle = "Il segreto",
            startPositionMs = 35_000L, // Dentro l'intervallo intro [20s, 88s)
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/s2e5.m3u8",
                embedUrl = "https://vixsrc.to/tv/8002/2/5",
                isDirectHls = true
            ),
            introSegment = intro,
            verifiedNextEpisode = VerifiedNextEpisode(
                seasonNumber = 2,
                episodeNumber = 6,
                title = "La fuga"
            ),
            hasCheckedNextEpisode = true
        )

        var skippedToMs: Long? = null
        composeRule.setContent {
            VixFlixTheme {
                VixPlayerScreen(
                    session = seriesSession,
                    streamLang = "it",
                    vixSrcResolver = testResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    initialDurationMsOverride = 2_700_000L,
                    initialReadyForTest = true,
                    onSkipIntroTriggered = { skippedToMs = it }
                )
            }
        }

        composeRule.onNodeWithTag("player_media_main_title")
            .assertIsDisplayed()
            .assertTextEquals("Il mistero della villa")
        composeRule.onNodeWithTag("player_media_series_subtitle")
            .assertIsDisplayed()
            .assertTextEquals("STAGIONE 2 • EPISODIO 5 – Il segreto")
        composeRule.onNodeWithTag("player_skip_intro_button").assertIsDisplayed()

        // Premendo SALTA INTRO salta esattamente a introEndMs (88_000L) e nasconde il pulsante
        composeRule.onNodeWithTag("player_skip_intro_button").performClick()
        composeRule.waitForIdle()

        assertEquals(88_000L, skippedToMs)
        composeRule.onNodeWithTag("player_skip_intro_button").assertDoesNotExist()
    }

    @Test
    fun seriesPlayer_withoutIntroMetadata_neverShowsSkipIntroButton() {
        val seriesSessionNoIntro = PlaybackSession(
            mediaItem = sampleSeries,
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "Ombre",
            startPositionMs = 30_000L,
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/s1e2.m3u8",
                embedUrl = "https://vixsrc.to/tv/8002/1/2",
                isDirectHls = true
            ),
            introSegment = null,
            verifiedNextEpisode = null,
            hasCheckedNextEpisode = true
        )

        composeRule.setContent {
            VixFlixTheme {
                VixPlayerScreen(
                    session = seriesSessionNoIntro,
                    streamLang = "it",
                    vixSrcResolver = testResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = {},
                    initialDurationMsOverride = 2_700_000L,
                    initialReadyForTest = true
                )
            }
        }

        composeRule.onNodeWithTag("player_skip_intro_button").assertDoesNotExist()
        // Nessun episodio successivo verificato -> pulsante "Prossimo Ep." non mostrato
        composeRule.onNodeWithTag("next_episode_button").assertDoesNotExist()
    }

    @Test
    fun nextEpisodePopup_crossSeasonTransition_respectsCountdownAndCancel() {
        val credits = CreditsSegmentEntity(
            contentKey = PlayerSegmentKeys.creditsContentKey(sampleSeries.id, "tv", 1, 8),
            tmdbId = sampleSeries.id,
            mediaType = "tv",
            seasonNumber = 1,
            episodeNumber = 8,
            creditsStartMs = 2_500_000L,
            durationMs = 2_700_000L,
            detectionState = CreditsDetectionState.VERIFIED_METADATA.storageKey
        )
        val crossSeasonSession = PlaybackSession(
            mediaItem = sampleSeries,
            seasonNumber = 1,
            episodeNumber = 8,
            episodeTitle = "Finale di stagione",
            startPositionMs = 2_520_000L, // Nei titoli di coda
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/s1e8.m3u8",
                embedUrl = "https://vixsrc.to/tv/8002/1/8",
                isDirectHls = true
            ),
            creditsSegment = credits,
            verifiedNextEpisode = VerifiedNextEpisode(
                seasonNumber = 2,
                episodeNumber = 1,
                title = "Nuovo inizio",
                isCrossSeason = true
            ),
            hasCheckedNextEpisode = true
        )

        var nextTriggered = false
        composeRule.setContent {
            VixFlixTheme {
                VixPlayerScreen(
                    session = crossSeasonSession,
                    streamLang = "it",
                    vixSrcResolver = testResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = { nextTriggered = true },
                    onClose = {},
                    advancedSettings = PlayerAdvancedSettings(
                        autoPlayNextEpisodeEnabled = true,
                        nextEpisodeCountdownSec = 15
                    ),
                    initialDurationMsOverride = 2_700_000L,
                    initialReadyForTest = true
                )
            }
        }

        composeRule.onNodeWithTag("auto_next_episode_card").assertIsDisplayed()
        composeRule.onNodeWithText("PROSSIMO EPISODIO").assertIsDisplayed()
        composeRule.onNodeWithText("NUOVA STAGIONE").assertIsDisplayed()
        composeRule.onNodeWithTag("next_episode_season_episode")
            .assertTextEquals("STAGIONE 2 • EPISODIO 1")
        composeRule.onNodeWithTag("next_episode_countdown_text")
            .assertTextContains("15 secondi", substring = true)

        // Annullamento chiude il popup e NON avvia il prossimo episodio
        composeRule.onNodeWithTag("auto_next_cancel_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auto_next_episode_card").assertDoesNotExist()
        assertFalse(nextTriggered)
    }

    @Test
    fun nextEpisodePopup_withAutoPlayOff_doesNotAutoAdvance_andAllowsManualPlayNow() {
        val credits = CreditsSegmentEntity(
            contentKey = PlayerSegmentKeys.creditsContentKey(sampleSeries.id, "tv", 2, 1),
            tmdbId = sampleSeries.id,
            mediaType = "tv",
            seasonNumber = 2,
            episodeNumber = 1,
            creditsStartMs = 2_400_000L,
            durationMs = 2_700_000L,
            detectionState = CreditsDetectionState.LOCAL_DB.storageKey
        )
        val session = PlaybackSession(
            mediaItem = sampleSeries,
            seasonNumber = 2,
            episodeNumber = 1,
            episodeTitle = "Nuovo inizio",
            startPositionMs = 2_450_000L,
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/s2e1.m3u8",
                embedUrl = "https://vixsrc.to/tv/8002/2/1",
                isDirectHls = true
            ),
            creditsSegment = credits,
            verifiedNextEpisode = VerifiedNextEpisode(
                seasonNumber = 2,
                episodeNumber = 2,
                title = "Secondo atto"
            ),
            hasCheckedNextEpisode = true
        )

        var nextTriggered = false
        composeRule.setContent {
            VixFlixTheme {
                VixPlayerScreen(
                    session = session,
                    streamLang = "it",
                    vixSrcResolver = testResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = { nextTriggered = true },
                    onClose = {},
                    advancedSettings = PlayerAdvancedSettings(
                        autoPlayNextEpisodeEnabled = false,
                        nextEpisodeCountdownSec = 5
                    ),
                    initialDurationMsOverride = 2_700_000L,
                    initialReadyForTest = true
                )
            }
        }

        composeRule.onNodeWithTag("auto_next_episode_card").assertIsDisplayed()
        composeRule.onNodeWithTag("next_episode_manual_hint").assertIsDisplayed()
        composeRule.onNodeWithTag("next_episode_countdown_text").assertDoesNotExist()
        assertFalse(nextTriggered)

        composeRule.onNodeWithTag("auto_next_play_now_button").performClick()
        composeRule.waitForIdle()
        assertTrue(nextTriggered)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun movieEndedPopup_continueWatchingAndBack_doNotCompleteMovie() {
        val movieCredits = CreditsSegmentEntity(
            contentKey = PlayerSegmentKeys.creditsContentKey(sampleMovie.id, "movie"),
            tmdbId = sampleMovie.id,
            mediaType = "movie",
            creditsStartMs = 6_800_000L,
            durationMs = 7_200_000L,
            detectionState = CreditsDetectionState.VERIFIED_METADATA.storageKey
        )
        val movieAtCredits = PlaybackSession(
            mediaItem = sampleMovie,
            startPositionMs = 6_850_000L,
            isResolvingStream = false,
            resolvedStream = ResolvedStream(
                streamUrl = "https://example.com/movie.m3u8",
                embedUrl = "https://vixsrc.to/movie/7001",
                isDirectHls = true
            ),
            creditsSegment = movieCredits
        )

        var completedCalled = false
        var closedCalled = false
        composeRule.setContent {
            VixFlixTheme {
                VixPlayerScreen(
                    session = movieAtCredits,
                    streamLang = "it",
                    vixSrcResolver = testResolver(),
                    onDynamicHlsFound = {},
                    onTogglePlayerMode = {},
                    onToggleLanguage = {},
                    onProgressUpdate = { _, _ -> },
                    onNextEpisode = {},
                    onClose = { closedCalled = true },
                    onCompleteMovieAndClose = { completedCalled = true },
                    initialDurationMsOverride = 7_200_000L,
                    initialReadyForTest = true
                )
            }
        }

        composeRule.onNodeWithTag("movie_ended_popup_card").assertIsDisplayed()
        composeRule.onNodeWithText("IL FILM È TERMINATO").assertIsDisplayed()

        // Premendo BACK (Key.Escape) chiude solo il popup e NON segna completato né chiude il player
        composeRule.onNodeWithTag("vix_player_screen").performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("movie_ended_popup_card").assertDoesNotExist()
        assertFalse(completedCalled)
        assertFalse(closedCalled)
    }

    @Test
    fun roomMigration4to5_andPerProfileCompletionIsolation_workLosslessly() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "test_player_v4_to_v5_${System.nanoTime()}.db"
        val now = System.currentTimeMillis()

        val v4Callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `user_profiles` (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `avatarId` TEXT NOT NULL,
                        `accentColorHex` INTEGER NOT NULL, `isPrimary` INTEGER NOT NULL,
                        `preferredLanguage` TEXT NOT NULL, `preferredAudioLanguage` TEXT NOT NULL,
                        `subtitlesEnabled` INTEGER NOT NULL, `preferredSubtitleLanguage` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_user_profiles_createdAtMs` ON `user_profiles` (`createdAtMs`)")
                db.execSQL(
                    """
                    INSERT INTO `user_profiles` VALUES (
                        '$DEFAULT_PRIMARY_PROFILE_ID', 'Profilo Principale', 'cinema_crimson', 4293200148, 1,
                        'it', 'it', 0, 'it', $now, $now
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE `watch_progress` (
                        `id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `overview` TEXT NOT NULL, `posterPath` TEXT, `backdropPath` TEXT,
                        `voteAverage` REAL NOT NULL, `releaseYear` TEXT NOT NULL,
                        `seasonNumber` INTEGER, `episodeNumber` INTEGER, `episodeTitle` TEXT,
                        `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                        `profileId` TEXT NOT NULL, PRIMARY KEY(`profileId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_watch_progress_profileId_updatedAt` ON `watch_progress` (`profileId`, `updatedAt`)")
                db.execSQL("CREATE INDEX `index_watch_progress_updatedAt` ON `watch_progress` (`updatedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE `episode_progress` (
                        `episodeKey` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL,
                        `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                        `profileId` TEXT NOT NULL, PRIMARY KEY(`profileId`, `episodeKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_episode_progress_profileId_tmdbId_seasonNumber` ON `episode_progress` (`profileId`, `tmdbId`, `seasonNumber`)")
                db.execSQL("CREATE INDEX `index_episode_progress_tmdbId_seasonNumber` ON `episode_progress` (`tmdbId`, `seasonNumber`)")
                db.execSQL(
                    """
                    CREATE TABLE `my_list` (
                        `id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `overview` TEXT NOT NULL, `posterPath` TEXT, `backdropPath` TEXT,
                        `voteAverage` REAL NOT NULL, `releaseYear` TEXT NOT NULL, `addedAt` INTEGER NOT NULL,
                        `profileId` TEXT NOT NULL, PRIMARY KEY(`profileId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_my_list_profileId_addedAt` ON `my_list` (`profileId`, `addedAt`)")
                db.execSQL("CREATE INDEX `index_my_list_addedAt` ON `my_list` (`addedAt`)")
                db.execSQL("CREATE TABLE `catalog_snapshot_cache` (`id` TEXT NOT NULL, `feedJson` TEXT NOT NULL, `movieGenresJson` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE `media_details_cache` (`cacheKey` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `detailsJson` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `lastAccessedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))")
                db.execSQL("CREATE INDEX `index_media_details_cache_lastAccessedAtMs` ON `media_details_cache` (`lastAccessedAtMs`)")
                db.execSQL("CREATE TABLE `season_episodes_cache` (`cacheKey` TEXT NOT NULL, `tvId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL, `episodesJson` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `lastAccessedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))")
                db.execSQL("CREATE INDEX `index_season_episodes_cache_lastAccessedAtMs` ON `season_episodes_cache` (`lastAccessedAtMs`)")
                db.execSQL("CREATE TABLE `stream_link_cache` (`cacheKey` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, `seasonNumber` INTEGER, `episodeNumber` INTEGER, `lang` TEXT NOT NULL, `streamUrl` TEXT NOT NULL, `embedUrl` TEXT NOT NULL, `referer` TEXT NOT NULL, `userAgent` TEXT NOT NULL, `startSeconds` INTEGER NOT NULL, `startMinutes` INTEGER NOT NULL, `subtitlesJson` TEXT NOT NULL, `expiresAtMs` INTEGER NOT NULL, `verifiedAtMs` INTEGER NOT NULL, `lastUsedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))")
                db.execSQL("CREATE INDEX `index_stream_link_cache_expiresAtMs` ON `stream_link_cache` (`expiresAtMs`)")
                db.execSQL("CREATE INDEX `index_stream_link_cache_lastUsedAtMs` ON `stream_link_cache` (`lastUsedAtMs`)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(dbName)
                .callback(v4Callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val db = Room.databaseBuilder(app, VixFlixDatabase::class.java, dbName)
            .addMigrations(VixFlixDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = VixFlixRepository(db.vixFlixDao())
            val profileA = DEFAULT_PRIMARY_PROFILE_ID
            val profileB = repo.createProfile("Sara", "emerald_pulse", 0xFF10B981L).getOrThrow().id

            // Intro & Credits persistence
            repo.saveIntroSegment(8002, 2, 5, 18_000L, 84_000L, IntroSegmentSource.LOCAL_DB)
            val savedIntro = repo.getIntroSegment(8002, 2, 5)
            assertNotNull(savedIntro)
            assertEquals(18_000L, savedIntro?.introStartMs)
            assertEquals(84_000L, savedIntro?.introEndMs)

            repo.saveCreditsSegment(7001, "movie", null, null, 6_800_000L, 7_200_000L, CreditsDetectionState.VERIFIED_METADATA)
            val savedCredits = repo.getCreditsSegment(7001, "movie")
            assertNotNull(savedCredits)
            assertEquals(6_800_000L, savedCredits?.creditsStartMs)

            // Entrambi i profili stanno guardando lo stesso film
            repo.saveWatchProgress(sampleMovie, null, null, null, 3_600_000L, 7_200_000L, profileA, allowAutoComplete = false)
            repo.saveWatchProgress(sampleMovie, null, null, null, 2_400_000L, 7_200_000L, profileB, allowAutoComplete = false)

            // Se l'utente sceglie "CONTINUA A GUARDARE" sui titoli di coda (97%), allowAutoComplete=false mantiene il film in Continua a guardare!
            repo.saveWatchProgress(sampleMovie, null, null, null, 7_000_000L, 7_200_000L, profileA, allowAutoComplete = false)
            assertNotNull(repo.getWatchProgress("movie_7001", profileA))
            assertFalse(repo.isMediaCompleted(7001, "movie", profileId = profileA))

            // Conferma "TERMINA VISUALIZZAZIONE" -> completato e rimosso da Continua a guardare SOLO per Profilo A
            repo.markMovieCompleted(sampleMovie, profileA)
            assertTrue(repo.isMediaCompleted(7001, "movie", profileId = profileA))
            assertNull(repo.getWatchProgress("movie_7001", profileA))

            // Profilo B resta completamente intatto!
            assertFalse(repo.isMediaCompleted(7001, "movie", profileId = profileB))
            assertNotNull(repo.getWatchProgress("movie_7001", profileB))

            // Serie TV: completamento episodio S1E8 con transizione a S2E1 aggiorna Continua a guardare solo per Profilo A
            repo.markEpisodeCompletedAndAdvanceSeries(
                item = sampleSeries,
                completedSeason = 1,
                completedEpisode = 8,
                nextEpisode = VerifiedNextEpisode(seasonNumber = 2, episodeNumber = 1, title = "Nuovo inizio", isCrossSeason = true),
                profileId = profileA
            )
            assertTrue(repo.isMediaCompleted(8002, "tv", 1, 8, profileA))
            val cwSeriesA = repo.getWatchProgress("tv_8002", profileA)
            assertNotNull(cwSeriesA)
            assertEquals(2, cwSeriesA?.seasonNumber)
            assertEquals(1, cwSeriesA?.episodeNumber)
            assertNull(repo.getWatchProgress("tv_8002", profileB))
        } finally {
            db.close()
            app.deleteDatabase(dbName)
        }
    }

    @Test
    fun resolveVerifiedNextEpisode_handlesSameSeason_crossSeason_andSeriesFinale() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val server = MockWebServer()
        server.start()
        try {
            val repo = VixFlixRepository(
                dao = db.vixFlixDao(),
                tmdbBaseUrl = server.url("/").toString()
            )
            val season1Episodes = listOf(
                TmdbEpisodeDto(id = 1, episodeNumber = 1, seasonNumber = 1, name = "Pilota", overview = ""),
                TmdbEpisodeDto(id = 2, episodeNumber = 2, seasonNumber = 1, name = "Svolta", overview = "")
            )
            val seasons = listOf(
                TmdbSeasonSummaryDto(id = 100, seasonNumber = 0, name = "Specials", episodeCount = 2),
                TmdbSeasonSummaryDto(id = 101, seasonNumber = 1, name = "Stagione 1", episodeCount = 2),
                TmdbSeasonSummaryDto(id = 102, seasonNumber = 2, name = "Stagione 2", episodeCount = 1)
            )

            // 1. Stessa stagione: S1E1 -> S1E2
            val sameSeasonNext = repo.resolveVerifiedNextEpisode(
                item = sampleSeries,
                currentSeason = 1,
                currentEpisode = 1,
                currentSeasonEpisodes = season1Episodes,
                knownSeasons = seasons
            )
            assertNotNull(sameSeasonNext)
            assertEquals(1, sameSeasonNext?.seasonNumber)
            assertEquals(2, sameSeasonNext?.episodeNumber)
            assertEquals("Svolta", sameSeasonNext?.title)
            assertFalse(sameSeasonNext?.isCrossSeason ?: true)

            // 2. Cambio stagione: S1E2 -> S2E1 (fetch reale da MockWebServer)
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "id": 102,
                          "season_number": 2,
                          "name": "Stagione 2",
                          "episodes": [
                            { "id": 201, "episode_number": 1, "season_number": 2, "name": "Ritorno", "overview": "Prima puntata S2" }
                          ]
                        }
                        """.trimIndent()
                    )
            )
            val crossSeasonNext = repo.resolveVerifiedNextEpisode(
                item = sampleSeries,
                currentSeason = 1,
                currentEpisode = 2,
                currentSeasonEpisodes = season1Episodes,
                knownSeasons = seasons
            )
            assertNotNull(crossSeasonNext)
            assertEquals(2, crossSeasonNext?.seasonNumber)
            assertEquals(1, crossSeasonNext?.episodeNumber)
            assertEquals("Ritorno", crossSeasonNext?.title)
            assertTrue(crossSeasonNext?.isCrossSeason == true)

            // 3. Finale serie (S2E1 senza S3) -> null (MAI inventato!)
            val season2Episodes = listOf(
                TmdbEpisodeDto(id = 201, episodeNumber = 1, seasonNumber = 2, name = "Ritorno", overview = "")
            )
            val finaleNext = repo.resolveVerifiedNextEpisode(
                item = sampleSeries,
                currentSeason = 2,
                currentEpisode = 1,
                currentSeasonEpisodes = season2Episodes,
                knownSeasons = seasons
            )
            assertNull(finaleNext)
        } finally {
            server.shutdown()
            db.close()
        }
    }

    @Test
    fun playerAdvancedSettings_persistInDataStore_andRenderInSettingsScreen() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = AppPreferences(app)

        prefs.setSkipIntroEnabled(false)
        prefs.setNextEpisodePromptEnabled(false)
        prefs.setAutoPlayNextEpisodeEnabled(false)
        prefs.setNextEpisodeCountdownSec(20)
        prefs.setCreditsDetectionEnabled(false)
        prefs.setManualMovieCompletionEnabled(false)

        val updated = prefs.playerAdvancedSettingsFlow.first()
        assertFalse(updated.skipIntroEnabled)
        assertFalse(updated.nextEpisodePromptEnabled)
        assertFalse(updated.autoPlayNextEpisodeEnabled)
        assertEquals(20, updated.nextEpisodeCountdownSec)
        assertFalse(updated.creditsDetectionEnabled)
        assertFalse(updated.manualMovieCompletionEnabled)

        // Ripristina valori validi e verifica sanitizzazione di un valore non consentito
        prefs.setNextEpisodeCountdownSec(99)
        assertEquals(10, prefs.playerAdvancedSettingsFlow.first().nextEpisodeCountdownSec)

        var selectedCountdown = 10
        var toggledSkipIntro = true
        composeRule.setContent {
            VixFlixTheme {
                SettingsScreen(
                    gitHubState = GitHubUpdateUiState(),
                    streamLang = "it",
                    preferWebPlayer = false,
                    bufferPreset = StreamingBufferPreset.DEFAULT,
                    autoCheckUpdates = true,
                    continueWatchingCount = 0,
                    watchlistCount = 0,
                    isTvLayout = true,
                    requestedCategory = SettingsCategory.PLAYBACK,
                    onSelectStreamLang = {},
                    onSetPreferWebPlayer = {},
                    onSelectBufferPreset = {},
                    onSetAutoCheckUpdates = {},
                    onRefreshCatalog = {},
                    onClearContinueWatching = {},
                    onRepoSlugChange = {},
                    onCheckUpdates = {},
                    onTriggerUpdate = {},
                    onUpdateTmdbKey = {},
                    onResetTmdbKey = {},
                    playerAdvancedSettings = PlayerAdvancedSettings(
                        skipIntroEnabled = true,
                        nextEpisodeCountdownSec = 10
                    ),
                    onSetSkipIntroEnabled = { toggledSkipIntro = it },
                    onSetNextEpisodeCountdownSec = { selectedCountdown = it }
                )
            }
        }

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_skip_intro_switch"))
        composeRule.onNodeWithTag("settings_skip_intro_switch").performClick()
        assertFalse(toggledSkipIntro)

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_countdown_15"))
        composeRule.onNodeWithTag("settings_countdown_15").performClick()
        assertEquals(15, selectedCountdown)
    }
}
