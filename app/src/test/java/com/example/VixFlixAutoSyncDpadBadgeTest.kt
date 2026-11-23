package com.example

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CompletedMediaEntity
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.UserProfileEntity
import com.example.data.local.UserProfileUiState
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.local.simkl.SimklLibraryItemEntity
import com.example.data.local.simkl.SimklSecureTokenStore
import com.example.data.local.simkl.SimklTokenBundle
import com.example.data.local.simkl.SoftwareAesGcmSimklCipherEngine
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbGenreDto
import com.example.data.remote.simkl.SimklConfig
import com.example.data.remote.simkl.SimklSyncManager
import com.example.data.repository.CatalogFeed
import com.example.data.repository.VixFlixRepository
import com.example.ui.CalendarDate
import com.example.ui.GitHubUpdateUiState
import com.example.ui.MovieGenreFilterUiState
import com.example.ui.NavTab
import com.example.ui.StreamingPlatformUiState
import com.example.ui.UpcomingEpisodeBadgeFormatter
import com.example.ui.components.ContinueWatchingSection
import com.example.ui.components.consumeBackAndEscapeKey
import com.example.ui.components.tvFocusableControl
import com.example.ui.screens.CatalogFeedContent
import com.example.ui.screens.SettingsCategory
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VixFlixExitConfirmationDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.data.local.StreamingBufferPreset
import kotlin.math.abs
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-notouch-dpad")
class VixFlixAutoSyncDpadBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: VixFlixDatabase
    private lateinit var vaultDir: File
    private lateinit var tokenStore: SimklSecureTokenStore
    private lateinit var repo: VixFlixRepository
    private lateinit var testScope: CoroutineScope
    private lateinit var server: FakePeriodicSimklServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val uiModeManager = app.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        shadowOf(uiModeManager).currentModeType = Configuration.UI_MODE_TYPE_TELEVISION
        shadowOf(app.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, true)
        shadowOf(app.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION, true)

        server = FakePeriodicSimklServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(app, VixFlixDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vaultDir = File(app.cacheDir, "autosync_vault_${System.nanoTime()}").apply { mkdirs() }
        tokenStore = SimklSecureTokenStore(
            vaultDir = vaultDir,
            cipherEngine = SoftwareAesGcmSimklCipherEngine()
        )
        repo = VixFlixRepository(
            dao = db.vixFlixDao(),
            tmdbBaseUrl = server.baseUrl,
            vixBaseUrl = server.baseUrl,
            httpCacheDir = app.cacheDir
        )
        runBlocking {
            repo.ensureDefaultPrimaryProfile()
        }
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        server.stop()
        db.close()
        vaultDir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // 1. SINCRONIZZAZIONE AUTOMATICA SIMKL OGNI 5 MINUTI (FOREGROUND / BACKGROUND)
    // -------------------------------------------------------------------------

    @Test
    fun defaultPeriodicSyncInterval_isExactlyFiveMinutes() {
        assertEquals(300_000L, SimklSyncManager.DEFAULT_PERIODIC_SYNC_INTERVAL_MS)
    }

    @Test
    fun periodicSync_runsInForeground_pausesInBackground_andCatchesUpOnResume() = runBlocking {
        val fakeClock = AtomicLong(1_700_000_000_000L)
        val profileId = DEFAULT_PRIMARY_PROFILE_ID
        tokenStore.saveTokens(
            profileId,
            SimklTokenBundle(
                accessToken = "tok_periodic_test",
                refreshToken = "ref_periodic_test",
                tokenType = "Bearer",
                expiresAtMs = fakeClock.get() + 3_600_000L
            )
        )

        val manager = SimklSyncManager(
            repository = repo,
            tokenStore = tokenStore,
            configProvider = {
                SimklConfig(
                    apiBaseUrl = server.baseUrl,
                    authBaseUrl = server.baseUrl.trimEnd('/'),
                    overrideClientId = "test_client_id_periodic"
                )
            },
            syncCooldownMs = 1_000L,
            periodicSyncIntervalMs = 220L,
            clockMs = { fakeClock.get() },
            scope = testScope
        )

        try {
            // 1. Avvia polling in foreground (chiamata ripetuta non deve creare timer duplicati)
            manager.startForegroundPeriodicSync(profileId)
            manager.startForegroundPeriodicSync(profileId)
            assertTrue(manager.isPeriodicSyncActiveForTest())

            // Attende sincronizzazione iniziale + almeno un ciclo periodico successivo
            val deadlineInitial = System.currentTimeMillis() + 4_000L
            while (manager.syncRevision.value < 2L && System.currentTimeMillis() < deadlineInitial) {
                delay(40L)
            }
            assertTrue(
                "Ci si aspetta almeno 2 revisioni (iniziale + ciclo periodico), trovate=${manager.syncRevision.value}",
                manager.syncRevision.value >= 2L
            )

            // Verifica che la serie iniziale sia stata importata come plantowatch
            val ptwBefore = db.vixFlixDao().observeSimklPlanToWatch(profileId).first()
            assertTrue(ptwBefore.any { it.tmdbId == 5501 })

            // 2. Passa in background: il polling si sospende e non invia richieste
            manager.pauseForegroundPeriodicSync()
            assertFalse(manager.isPeriodicSyncActiveForTest())
            delay(120L)
            val hitsAfterPause = server.activitiesHits.get()
            delay(600L)
            assertEquals(
                "In background il polling non deve inviare richieste Simkl",
                hitsAfterPause,
                server.activitiesHits.get()
            )

            // 3. Mentre è in background, Simkl aggiorna lo stato della serie da plantowatch a watching S1E2
            server.phase = 2
            fakeClock.addAndGet(SimklSyncManager.DEFAULT_PERIODIC_SYNC_INTERVAL_MS + 10_000L)

            // 4. Ritorno in primo piano: catch-up immediato recupera gli aggiornamenti mancati
            val revBeforeResume = manager.syncRevision.value
            manager.startForegroundPeriodicSync(profileId)
            assertTrue(manager.isPeriodicSyncActiveForTest())

            val deadlineResume = System.currentTimeMillis() + 4_000L
            while (manager.syncRevision.value <= revBeforeResume && System.currentTimeMillis() < deadlineResume) {
                delay(40L)
            }
            assertTrue(manager.syncRevision.value > revBeforeResume)

            // La serie ora non è più in plantowatch ma è stata aggiornata nello stato watching
            val ptwAfter = db.vixFlixDao().observeSimklPlanToWatch(profileId).first()
            assertFalse(ptwAfter.any { it.tmdbId == 5501 })
            val libItem = db.vixFlixDao().observeSimklLibraryForProfile(profileId).first()
                .firstOrNull { it.tmdbId == 5501 }
            assertEquals("watching", libItem?.status)
        } finally {
            manager.stopPeriodicSync()
        }
    }

    @Test
    fun periodicSync_recoversFromServerError_preservesSnapshot_andRetriesNextCycle() = runBlocking {
        val fakeClock = AtomicLong(1_700_000_000_000L)
        val profileId = DEFAULT_PRIMARY_PROFILE_ID
        tokenStore.saveTokens(
            profileId,
            SimklTokenBundle(
                accessToken = "tok_error_recovery",
                refreshToken = null,
                tokenType = "Bearer",
                expiresAtMs = fakeClock.get() + 3_600_000L
            )
        )

        val manager = SimklSyncManager(
            repository = repo,
            tokenStore = tokenStore,
            configProvider = {
                SimklConfig(
                    apiBaseUrl = server.baseUrl,
                    authBaseUrl = server.baseUrl.trimEnd('/'),
                    overrideClientId = "test_client_id_err"
                )
            },
            syncCooldownMs = 500L,
            periodicSyncIntervalMs = 200L,
            clockMs = { fakeClock.get() },
            scope = testScope
        )

        try {
            manager.startForegroundPeriodicSync(profileId)
            val deadline1 = System.currentTimeMillis() + 4_000L
            while (manager.syncRevision.value < 1L && System.currentTimeMillis() < deadline1) {
                delay(30L)
            }
            assertTrue(manager.syncRevision.value >= 1L)
            val snapshotBeforeError = db.vixFlixDao().observeSimklPlanToWatch(profileId).first()
            assertTrue(snapshotBeforeError.any { it.tmdbId == 5501 })

            // Simula errore temporaneo 500 lato Simkl
            server.failActivitiesWith500.set(true)
            val hitsBeforeError = server.activitiesHits.get()
            val deadlineErr = System.currentTimeMillis() + 3_000L
            while (server.activitiesHits.get() <= hitsBeforeError && System.currentTimeMillis() < deadlineErr) {
                delay(30L)
            }
            // Lo snapshot precedente deve restare integro e il timer sempre vivo
            assertTrue(manager.isPeriodicSyncActiveForTest())
            val snapshotDuringError = db.vixFlixDao().observeSimklPlanToWatch(profileId).first()
            assertTrue(snapshotDuringError.any { it.tmdbId == 5501 })

            // Ripristina Simkl e verifica che il ciclo successivo sincronizzi regolarmente
            val revBeforeRecovery = manager.syncRevision.value
            server.failActivitiesWith500.set(false)
            val deadlineRecovery = System.currentTimeMillis() + 4_000L
            while (manager.syncRevision.value <= revBeforeRecovery && System.currentTimeMillis() < deadlineRecovery) {
                delay(40L)
            }
            assertTrue(manager.syncRevision.value > revBeforeRecovery)
        } finally {
            manager.stopPeriodicSync()
        }
    }

    @Test
    fun periodicSync_doesNotOverwriteActivePlaybackTarget() = runBlocking {
        val fakeClock = AtomicLong(1_700_000_000_000L)
        val profileId = DEFAULT_PRIMARY_PROFILE_ID
        tokenStore.saveTokens(
            profileId,
            SimklTokenBundle(
                accessToken = "tok_playback_guard",
                refreshToken = null,
                tokenType = "Bearer",
                expiresAtMs = fakeClock.get() + 3_600_000L
            )
        )

        // Salva un progresso locale attivo per il film TMDB 8800 (42 minuti)
        db.vixFlixDao().upsertWatchProgress(
            WatchProgressEntity(
                id = "movie_8800",
                profileId = profileId,
                tmdbId = 8800,
                mediaType = "movie",
                title = "Film In Riproduzione",
                overview = "",
                posterPath = "/p8800.jpg",
                backdropPath = "/b8800.jpg",
                voteAverage = 8.0,
                releaseYear = "2025",
                positionMs = 42L * 60_000L,
                durationMs = 100L * 60_000L,
                updatedAt = fakeClock.get()
            )
        )

        val manager = SimklSyncManager(
            repository = repo,
            tokenStore = tokenStore,
            configProvider = {
                SimklConfig(
                    apiBaseUrl = server.baseUrl,
                    authBaseUrl = server.baseUrl.trimEnd('/'),
                    overrideClientId = "test_client_id_pb"
                )
            },
            syncCooldownMs = 0L,
            periodicSyncIntervalMs = 200L,
            clockMs = { fakeClock.get() },
            scope = testScope
        )

        // Segna il film 8800 come attualmente in riproduzione
        manager.setActivePlaybackTarget(profileId = profileId, mediaType = "movie", tmdbId = 8800)
        server.returnRemoteMoviePlayback8800.set(true)

        val ok = manager.syncProfileNow(profileId, com.example.data.remote.simkl.SimklSyncTrigger.PERIODIC)
        assertTrue(ok)

        val progressAfter = db.vixFlixDao().observeContinueWatching(profileId).first()
            .firstOrNull { it.id == "movie_8800" }
        assertEquals(
            "La sincronizzazione automatica non deve mai sovrascrivere o interferire con la riproduzione attiva",
            42L * 60_000L,
            progressAfter?.positionMs
        )
    }

    // -------------------------------------------------------------------------
    // 2. CORREZIONI COMANDI PULSANTI ED EVENTI D-PAD (ZERO CLICK FANTASMA / DOPPI BACK)
    // -------------------------------------------------------------------------

    @Test
    fun tvFocusableControl_ignoresOrphanKeyUp_andFiresSingleActionOnPress() {
        var clickCount = 0
        var longClickCount = 0

        composeRule.setContent {
            MyApplicationTheme {
                Row {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .focusable()
                            .testTag("source_btn")
                    )
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .tvFocusableControl(
                                focusTag = "test_btn",
                                onLongClick = { longClickCount++ },
                                onClick = { clickCount++ }
                            )
                            .testTag("test_btn")
                    )
                }
            }
        }

        val source = composeRule.onNodeWithTag("source_btn")
        val node = composeRule.onNodeWithTag("test_btn")

        // Simula cambio di focus durante la pressione (es. chiusura dialog su KeyDown -> KeyUp orfano su test_btn)
        source.requestFocus()
        composeRule.waitForIdle()
        source.performKeyInput {
            keyDown(Key.DirectionCenter)
        }
        node.requestFocus()
        composeRule.waitForIdle()
        node.performKeyInput {
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals("KeyUp orfano non deve attivare onClick", 0, clickCount)
        assertEquals("KeyUp orfano non deve attivare onLongClick", 0, longClickCount)

        // Una pressione completa (KeyDown + KeyUp) deve eseguire esattamente una volta onClick
        node.performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals("Una pressione fisica deve eseguire esattamente 1 azione", 1, clickCount)
        assertEquals(0, longClickCount)

        // Pressione prolungata > 500ms attiva onLongClick ma NON deve attivare onClick al rilascio
        node.performKeyInput {
            keyDown(Key.DirectionCenter)
        }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        node.performKeyInput {
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals(1, longClickCount)
        assertEquals("Il rilascio dopo long-press non deve eseguire onClick", 1, clickCount)
    }

    @Test
    fun consumeBackAndEscapeKey_firesOncePerFullPress_andIgnoresOrphanKeyUp() {
        var backCount = 0

        composeRule.setContent {
            MyApplicationTheme {
                Row {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .focusable()
                            .testTag("other_focus_host")
                    )
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .consumeBackAndEscapeKey(onBack = { backCount++ })
                            .focusable()
                            .testTag("back_host")
                    )
                }
            }
        }

        val other = composeRule.onNodeWithTag("other_focus_host")
        val host = composeRule.onNodeWithTag("back_host")

        // KeyUp orfano di BACK dopo cambio focus non deve scatenare un secondo BACK
        other.requestFocus()
        composeRule.waitForIdle()
        other.performKeyInput { keyDown(Key.Back) }
        host.requestFocus()
        composeRule.waitForIdle()
        host.performKeyInput { keyUp(Key.Back) }
        composeRule.waitForIdle()
        assertEquals("KeyUp orfano di BACK non deve attivare onBack", 0, backCount)

        // KeyUp orfano di ESCAPE dopo cambio focus non deve scatenare un secondo BACK
        other.requestFocus()
        composeRule.waitForIdle()
        other.performKeyInput { keyDown(Key.Escape) }
        host.requestFocus()
        composeRule.waitForIdle()
        host.performKeyInput { keyUp(Key.Escape) }
        composeRule.waitForIdle()
        assertEquals("KeyUp orfano di ESCAPE non deve attivare onBack", 0, backCount)

        // Pressione completa di BACK -> esattamente 1 volta
        host.performKeyInput {
            pressKey(Key.Back)
        }
        composeRule.waitForIdle()
        assertEquals(1, backCount)

        // Pressione completa di ESCAPE -> esattamente 1 volta
        host.performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.waitForIdle()
        assertEquals(2, backCount)
    }

    @Test
    fun m3ButtonWithTvFocusableControl_andExitDialog_executeSingleActionPerPressAndBack() {
        var buttonClicks = 0
        var cancelCount = 0
        var confirmCount = 0
        var showExitDialog by mutableStateOf(false)

        composeRule.setContent {
            MyApplicationTheme {
                Row {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .focusable()
                            .testTag("pre_button_focus")
                    )
                    Button(
                        onClick = { buttonClicks++ },
                        modifier = Modifier
                            .tvFocusableControl(focusTag = "m3_action_btn")
                            .testTag("m3_action_btn")
                    ) {
                        Text("Azione")
                    }
                }
                if (showExitDialog) {
                    VixFlixExitConfirmationDialog(
                        onCancel = {
                            cancelCount++
                            showExitDialog = false
                        },
                        onConfirmExit = {
                            confirmCount++
                            showExitDialog = false
                        }
                    )
                }
            }
        }

        // 1) KeyUp orfano su M3 Button con tvFocusableControl non deve attivare onClick
        val preFocus = composeRule.onNodeWithTag("pre_button_focus")
        val m3Btn = composeRule.onNodeWithTag("m3_action_btn")
        preFocus.requestFocus()
        composeRule.waitForIdle()
        preFocus.performKeyInput { keyDown(Key.DirectionCenter) }
        m3Btn.requestFocus()
        composeRule.waitForIdle()
        m3Btn.performKeyInput { keyUp(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertEquals("KeyUp orfano su Button M3 non deve scatenare click", 0, buttonClicks)

        // 2) Una pressione completa D-Pad Center su M3 Button esegue onClick esattamente una volta
        m3Btn.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertEquals("Button M3 con tvFocusableControl deve eseguire esattamente 1 azione", 1, buttonClicks)

        // 3) Dialog di uscita: BACK esegue onCancel una sola volta
        showExitDialog = true
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vixflix_exit_confirmation_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("exit_dialog_cancel_button").performKeyInput {
            pressKey(Key.Back)
        }
        composeRule.waitForIdle()
        assertEquals("BACK sul dialog uscita deve chiudere una sola volta", 1, cancelCount)
        assertEquals(0, confirmCount)
        assertFalse(showExitDialog)

        // 4) Dialog di uscita: conferma ESCI esegue onConfirmExit una sola volta
        showExitDialog = true
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.waitForIdle()
        val confirmBtn = composeRule.onNodeWithTag("exit_dialog_confirm_button")
        confirmBtn.requestFocus()
        composeRule.waitForIdle()
        confirmBtn.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertEquals(1, confirmCount)
        assertEquals(1, cancelCount)
    }

    // -------------------------------------------------------------------------
    // 3. BADGE DATA DI USCITA PROSSIMO EPISODIO NON ANCORA TRASMESSO
    // -------------------------------------------------------------------------

    @Test
    fun upcomingEpisodeBadgeFormatter_formatsItalianDatesAndAppliesStrictVisibilityRules() {
        val today = CalendarDate(year = 2026, month = 6, day = 10)

        // Anno sempre incluso -> "GG MMM YYYY"
        assertEquals("23 NOV 2026", UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2026-11-23", today))
        assertEquals("26 NOV 2026", UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2026-11-26", today))
        assertEquals("05 LUG 2026", UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2026-07-05", today))

        // Anno successivo -> "GG MMM YYYY"
        assertEquals("08 GEN 2027", UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2027-01-08", today))
        assertEquals("14 FEB 2027", UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2027-02-14", today))

        // Tutti i 12 mesi italiani maiuscoli
        val expectedMonths = listOf("GEN", "FEB", "MAR", "APR", "MAG", "GIU", "LUG", "AGO", "SET", "OTT", "NOV", "DIC")
        expectedMonths.forEachIndexed { idx, abbr ->
            val monthStr = String.format("%02d", idx + 1)
            assertEquals(
                "15 $abbr 2028",
                UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2028-$monthStr-15", today)
            )
        }

        // Episodio già uscito o in uscita oggi -> null (nascosto)
        assertNull(UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2026-06-10", today))
        assertNull(UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2026-06-09", today))
        assertNull(UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2025-12-31", today))

        // Date mancanti, vuote o non valide -> null (mai inventare)
        assertNull(UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate(null, today))
        assertNull(UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("", today))
        assertNull(UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("TBD", today))
        assertNull(UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2026-02-30", today))
        assertNull(UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2026-13-01", today))
    }

    @Test
    fun resolveUpcomingEpisodeBadge_respectsSeriesVsMovieCompletedAndEpisodeAdvance() {
        val today = CalendarDate(year = 2026, month = 6, day = 10)
        val episodes = listOf(
            TmdbEpisodeDto(id = 1, episodeNumber = 1, seasonNumber = 2, name = "Ep 1", airDate = "2026-06-01"),
            TmdbEpisodeDto(id = 2, episodeNumber = 2, seasonNumber = 2, name = "Ep 2", airDate = "2026-11-23"),
            TmdbEpisodeDto(id = 3, episodeNumber = 3, seasonNumber = 2, name = "Ep 3", airDate = "2027-01-12")
        )

        val tvEntryEp1 = WatchProgressEntity(
            id = "tv_900",
            tmdbId = 900,
            mediaType = "tv",
            title = "Serie Test",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.4,
            releaseYear = "2025",
            seasonNumber = 2,
            episodeNumber = 1,
            positionMs = 0L,
            durationMs = 2_700_000L
        )
        // Ep 1 è già uscito -> nessun badge
        assertNull(
            UpcomingEpisodeBadgeFormatter.resolveUpcomingEpisodeBadgeForEntry(
                entry = tvEntryEp1,
                seasonEpisodes = episodes,
                isSeriesCompleted = false,
                today = today
            )
        )

        // Avanzando a Ep 2 (non ancora trasmesso, anno corrente) -> "23 NOV 2026"
        val tvEntryEp2 = tvEntryEp1.copy(episodeNumber = 2)
        assertEquals(
            "23 NOV 2026",
            UpcomingEpisodeBadgeFormatter.resolveUpcomingEpisodeBadgeForEntry(
                entry = tvEntryEp2,
                seasonEpisodes = episodes,
                isSeriesCompleted = false,
                today = today
            )
        )

        // Avanzando a Ep 3 (anno successivo) -> "12 GEN 2027"
        val tvEntryEp3 = tvEntryEp1.copy(episodeNumber = 3)
        assertEquals(
            "12 GEN 2027",
            UpcomingEpisodeBadgeFormatter.resolveUpcomingEpisodeBadgeForEntry(
                entry = tvEntryEp3,
                seasonEpisodes = episodes,
                isSeriesCompleted = false,
                today = today
            )
        )

        // Serie completata -> nessun badge
        assertNull(
            UpcomingEpisodeBadgeFormatter.resolveUpcomingEpisodeBadgeForEntry(
                entry = tvEntryEp2,
                seasonEpisodes = episodes,
                isSeriesCompleted = true,
                today = today
            )
        )

        // Film -> mai mostrato
        val movieEntry = tvEntryEp2.copy(id = "movie_901", mediaType = "movie")
        assertNull(
            UpcomingEpisodeBadgeFormatter.resolveUpcomingEpisodeBadgeForEntry(
                entry = movieEntry,
                seasonEpisodes = episodes,
                isSeriesCompleted = false,
                today = today
            )
        )
    }

    @Test
    fun continueWatchingCard_rendersUpcomingBadgeOnlyForFutureTvEpisode_andUpdatesReactively() {
        val futureShow = WatchProgressEntity(
            id = "tv_701",
            tmdbId = 701,
            mediaType = "tv",
            title = "Serie Futura",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.7,
            releaseYear = "2026",
            seasonNumber = 3,
            episodeNumber = 4,
            positionMs = 0L,
            durationMs = 2_700_000L
        )
        val airedShow = WatchProgressEntity(
            id = "tv_702",
            tmdbId = 702,
            mediaType = "tv",
            title = "Serie Già Uscita",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.1,
            releaseYear = "2025",
            seasonNumber = 1,
            episodeNumber = 2,
            positionMs = 600_000L,
            durationMs = 3_000_000L
        )
        val movieEntry = WatchProgressEntity(
            id = "movie_703",
            tmdbId = 703,
            mediaType = "movie",
            title = "Film In Corso",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 7.9,
            releaseYear = "2025",
            positionMs = 1_200_000L,
            durationMs = 6_000_000L
        )

        var badgesMap by mutableStateOf(mapOf("tv_701" to "26 NOV 2026", "movie_703" to "26 NOV 2026"))

        composeRule.setContent {
            MyApplicationTheme {
                ContinueWatchingSection(
                    entries = listOf(futureShow, airedShow, movieEntry),
                    onResumeClick = {},
                    onInfoClick = {},
                    onRemoveClick = {},
                    isTvLayout = true,
                    upcomingDateBadges = badgesMap
                )
            }
        }
        composeRule.waitForIdle()

        // Visibile per la serie TV con episodio futuro (anno incluso)
        composeRule.onNodeWithTag("cw_season_episode_badge_tv_701", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("cw_upcoming_date_badge_tv_701", useUnmergedTree = true)
            .assertIsDisplayed()
            .onChild()
            .assertTextEquals("26 NOV 2026")

        // Assente per serie già uscita e per film (anche se erroneamente presente nella mappa)
        assertTrue(composeRule.onAllNodesWithTag("cw_upcoming_date_badge_tv_702", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("cw_upcoming_date_badge_movie_703", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        // Aggiornamento dinamico in-place (es. dopo sincronizzazione Simkl o cambio data)
        badgesMap = mapOf("tv_701" to "05 GEN 2027")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cw_upcoming_date_badge_tv_701", useUnmergedTree = true)
            .assertIsDisplayed()
            .onChild()
            .assertTextEquals("05 GEN 2027")

        // Quando l'episodio esce / la serie si aggiorna, il badge sparisce senza cambiare schermata
        badgesMap = emptyMap()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("cw_upcoming_date_badge_tv_701", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    // -------------------------------------------------------------------------
    // 4. CORREZIONI GRAFICHE E FUNZIONALI: DIMENSIONI LOCANDINE FILM, PROGRESSO 0
    //    SU EPISODI FUTURI E CONFERMA SVUOTAMENTO CONTINUA A GUARDARE
    // -------------------------------------------------------------------------

    @Test
    fun moviesCategoryGrid_postersKeepExactStandardDimensions_matchingRowsAcrossAllCategories() {
        fun sampleMovie(id: Int, title: String) = MediaItem(
            id = id,
            mediaType = "movie",
            title = title,
            overview = "Descrizione $title",
            posterPath = "/poster_$id.jpg",
            backdropPath = "/backdrop_$id.jpg",
            voteAverage = 7.8,
            releaseYear = "2025"
        )

        val homeMovies = (1..6).map { sampleMovie(it, "Home Film $it") }
        val actionMovies = (101..106).map { sampleMovie(it, "Azione $it") }
        val comedyMovies = (201..206).map { sampleMovie(it, "Commedia $it") }
        val sciFiMovies = (301..306).map { sampleMovie(it, "SciFi $it") }

        val feed = CatalogFeed(
            heroBanner = homeMovies.first(),
            top10Today = homeMovies,
            trendingMovies = homeMovies,
            trendingTv = emptyList(),
            popularMovies = homeMovies,
            popularTv = emptyList(),
            nowPlayingMovies = homeMovies,
            topRatedMovies = homeMovies,
            actionMovies = actionMovies,
            sciFiMovies = sciFiMovies
        )

        val genres = listOf(
            TmdbGenreDto(28, "Azione"),
            TmdbGenreDto(35, "Commedia"),
            TmdbGenreDto(878, "Fantascienza")
        )

        var selectedTab by mutableStateOf(NavTab.MOVIES)
        var genreState by mutableStateOf(MovieGenreFilterUiState(genres = genres, selectedGenre = null, movies = emptyList()))

        composeRule.setContent {
            MyApplicationTheme {
                CatalogFeedContent(
                    feed = feed,
                    selectedTab = selectedTab,
                    continueWatching = emptyList(),
                    watchlist = emptyList(),
                    movieGenreState = genreState,
                    platformState = StreamingPlatformUiState(),
                    isTvLayout = true,
                    heroPlayFocusRequester = remember { FocusRequester() },
                    movieGenreBarFocusRequester = remember { FocusRequester() },
                    platformStripFocusRequester = remember { FocusRequester() },
                    firstRowFocusRequester = remember { FocusRequester() },
                    navRailFocusRequester = remember { FocusRequester() },
                    topBarFocusRequester = null,
                    onSelectMovieGenre = {},
                    onRetryMovieGenre = {},
                    onSelectStreamingPlatform = {},
                    onSelectPlatformMediaTypeFilter = {},
                    onRetryStreamingPlatform = {},
                    onPlayItem = {},
                    onOpenDetails = {},
                    onToggleWatchlist = {},
                    onResumeContinueWatching = {},
                    onInfoContinueWatching = {},
                    onRemoveContinueWatching = {},
                    bottomPadding = 24.dp
                )
            }
        }
        composeRule.waitForIdle()

        // Riferimento standard da riga normale (Film -> "Tutti"): scorre oltre Hero e chip bar
        composeRule.onNodeWithTag("catalog_lazy_column").performScrollToIndex(2)
        composeRule.waitForIdle()
        val refBounds = composeRule.onNodeWithTag("poster_card_movie_1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue("Larghezza standard deve essere 130.dp (effettiva ${refBounds.width})", abs(refBounds.width.value - 130f) <= 1.5f)
        assertTrue("Altezza standard deve rispettare 0.67f (effettiva ${refBounds.height})", abs(refBounds.height.value - (130f / 0.67f)) <= 2.5f)

        // Verifica tutte le categorie selezionabili in Film: le locandine devono mantenere identiche dimensioni e proporzioni
        val categoryCases = listOf(
            genres[0] to actionMovies,
            genres[1] to comedyMovies,
            genres[2] to sciFiMovies
        )
        for ((genre, movies) in categoryCases) {
            genreState = MovieGenreFilterUiState(
                genres = genres,
                selectedGenre = genre,
                movies = movies
            )
            composeRule.waitForIdle()
            val catBounds = composeRule.onNodeWithTag("poster_card_movie_${movies.first().id}", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            assertEquals(
                "Larghezza locandina in categoria '${genre.name}' deve essere uguale alle altre sezioni",
                refBounds.width.value,
                catBounds.width.value,
                1.5f
            )
            assertEquals(
                "Altezza locandina in categoria '${genre.name}' deve essere uguale alle altre sezioni",
                refBounds.height.value,
                catBounds.height.value,
                2.0f
            )
        }
    }

    @Test
    fun futureEpisodeInContinueWatching_hasEmptyZeroProgressBar_noMinutes_redBorderYearBadge_andPreservesRealProgressElsewhere() = runBlocking {
        // 1. Verifica entità WatchProgressEntity: positionMs=0 -> progressFraction 0f e completionPercent 0
        val docFutureEntity = WatchProgressEntity(
            id = "tv_9801",
            tmdbId = 9801,
            mediaType = "tv",
            title = "DOC - Nelle tue mani",
            overview = "Serie TV",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.4,
            releaseYear = "2020",
            seasonNumber = 4,
            episodeNumber = 15,
            episodeTitle = "Episodio Futuro",
            positionMs = 0L,
            durationMs = 52 * 60_000L
        )
        assertEquals(0f, docFutureEntity.progressFraction, 0.0001f)
        assertEquals(0, docFutureEntity.completionPercent)
        assertEquals(0f, docFutureEntity.copy(durationMs = 0L).progressFraction, 0.0001f)

        // Anche se una riga sporca avesse positionMs > 0 su un episodio futuro, ContinueWatchingCard
        // forza la barra a 0f, nasconde MIN RIMANENTI e mantiene visibile il badge data anno incluso anche con focus
        val docFutureStale = docFutureEntity.copy(positionMs = 10 * 60_000L)
        val docAiredReal = WatchProgressEntity(
            id = "tv_9802",
            tmdbId = 9802,
            mediaType = "tv",
            title = "Serie In Corso",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.1,
            releaseYear = "2024",
            seasonNumber = 2,
            episodeNumber = 4,
            positionMs = 15 * 60_000L,
            durationMs = 50 * 60_000L
        )
        val movieReal = WatchProgressEntity(
            id = "movie_9803",
            tmdbId = 9803,
            mediaType = "movie",
            title = "Film In Corso",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 7.9,
            releaseYear = "2025",
            positionMs = 90 * 60_000L,
            durationMs = 102 * 60_000L
        )

        val futureBadge = UpcomingEpisodeBadgeFormatter.resolveUpcomingEpisodeBadgeForEntry(
            entry = docFutureEntity,
            seasonEpisodes = listOf(
                TmdbEpisodeDto(id = 414, episodeNumber = 14, seasonNumber = 4, name = "Ep 14", airDate = "2026-05-01"),
                TmdbEpisodeDto(id = 415, episodeNumber = 15, seasonNumber = 4, name = "Ep 15", airDate = "2026-11-26", runtime = 52)
            ),
            isSeriesCompleted = false,
            today = CalendarDate(2026, 6, 10)
        )
        assertEquals("26 NOV 2026", futureBadge)

        composeRule.setContent {
            MyApplicationTheme {
                ContinueWatchingSection(
                    entries = listOf(docFutureStale, docAiredReal, movieReal),
                    onResumeClick = {},
                    onInfoClick = {},
                    onRemoveClick = {},
                    isTvLayout = true,
                    upcomingDateBadges = mapOf("tv_9801" to futureBadge!!)
                )
            }
        }
        composeRule.waitForIdle()

        // Badge data anno completo visibile sia non focalizzato che focalizzato con D-Pad
        composeRule.onNodeWithTag("cw_upcoming_date_badge_tv_9801", useUnmergedTree = true)
            .assertIsDisplayed()
            .onChild()
            .assertTextEquals("26 NOV 2026")

        composeRule.onNodeWithTag("continue_watching_card_tv_9801")
            .requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("continue_watching_card_tv_9801").assertIsFocused()
        composeRule.onNodeWithTag("cw_upcoming_date_badge_tv_9801", useUnmergedTree = true)
            .assertIsDisplayed()
            .onChild()
            .assertTextEquals("26 NOV 2026")

        // Barra progresso episodio futuro rigorosamente vuota (0f) e NESSUN "MIN RIMANENTI"
        composeRule.onNodeWithTag("cw_progress_bar_tv_9801", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..1f)))
        assertTrue(
            "Episodio non ancora trasmesso non deve mostrare minuti rimanenti",
            composeRule.onAllNodesWithTag("cw_remaining_minutes_badge_tv_9801", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        )

        // Episodio già trasmesso e film mantengono normalmente barra avanzamento reale (> 0f) e MIN RIMANENTI
        composeRule.onNodeWithTag("cw_progress_bar_tv_9802", useUnmergedTree = true)
            .assert(SemanticsMatcher("progress > 0f") { node ->
                node.config[SemanticsProperties.ProgressBarRangeInfo].current > 0.25f
            })
        composeRule.onNodeWithText("35 MIN RIMANENTI").assertIsDisplayed()
        composeRule.onNodeWithTag("cw_progress_bar_movie_9803", useUnmergedTree = true)
            .assert(SemanticsMatcher("movie progress > 0.8f") { node ->
                node.config[SemanticsProperties.ProgressBarRangeInfo].current > 0.8f
            })
        composeRule.onNodeWithText("12 MIN RIMANENTI").assertIsDisplayed()

        // Verifica isolamento DB per (serie, stagione, episodio): segnare S4 E14 come visto
        // NON deve copiare progressi di S4 E14 o altri episodi su S4 E15 (2099 futuro -> positionMs = 0L)
        val dao = db.vixFlixDao()
        val seriesItem = MediaItem(
            id = 9804,
            mediaType = "tv",
            title = "DOC Future Isolation",
            overview = "",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.5,
            releaseYear = "2020"
        )
        dao.upsertEpisodeProgress(
            EpisodeProgressEntity(
                episodeKey = "tv_9804_s4_e13",
                tmdbId = 9804,
                seasonNumber = 4,
                episodeNumber = 13,
                positionMs = 1_500_000L,
                durationMs = 3_000_000L,
                profileId = DEFAULT_PRIMARY_PROFILE_ID
            )
        )
        repo.markEpisodeAsWatched(
            tmdbId = 9804,
            seasonNumber = 4,
            episodeNumber = 14,
            profileId = DEFAULT_PRIMARY_PROFILE_ID,
            mediaItem = seriesItem,
            currentSeasonEpisodes = listOf(
                TmdbEpisodeDto(id = 13, episodeNumber = 13, seasonNumber = 4, name = "Ep 13", airDate = "2026-04-01", runtime = 50),
                TmdbEpisodeDto(id = 14, episodeNumber = 14, seasonNumber = 4, name = "Ep 14", airDate = "2026-05-01", runtime = 50),
                TmdbEpisodeDto(id = 15, episodeNumber = 15, seasonNumber = 4, name = "Ep 15", airDate = "2099-11-26", runtime = 52)
            )
        )
        val cwAfterMark = dao.getWatchProgress("tv_9804", DEFAULT_PRIMARY_PROFILE_ID)
        assertEquals(4, cwAfterMark?.seasonNumber)
        assertEquals(15, cwAfterMark?.episodeNumber)
        assertEquals(0L, cwAfterMark?.positionMs)
        assertEquals(0f, cwAfterMark?.progressFraction ?: -1f, 0.0001f)
        // Il progresso reale di E13 resta intatto senza essere alterato né cancellato
        val ep13StillIntact = dao.getEpisodeProgress("tv_9804_s4_e13", DEFAULT_PRIMARY_PROFILE_ID)
        assertEquals(1_500_000L, ep13StillIntact?.positionMs)
    }

    @Test
    fun settingsClearContinueWatching_showsDpadConfirmationDialog_cancelsSafely_andClearsOnlyActiveContinueWatchingOnConfirm() = runBlocking {
        val dao = db.vixFlixDao()
        val secondaryProfileId = "profile_secondary"
        dao.upsertProfile(
            UserProfileEntity(
                id = DEFAULT_PRIMARY_PROFILE_ID,
                name = "Utente 1",
                avatarId = "avatar_1",
                accentColorHex = 0xFFE50914L,
                isPrimary = true
            )
        )
        dao.upsertProfile(
            UserProfileEntity(
                id = secondaryProfileId,
                name = "Utente 2",
                avatarId = "avatar_2",
                accentColorHex = 0xFF00A8E8L,
                isPrimary = false
            )
        )

        // Dati profilo attivo: 2 voci in Continua a guardare + Preferiti + Simkl + Completati + Progresso episodio
        dao.upsertWatchProgress(
            WatchProgressEntity(
                id = "movie_101",
                tmdbId = 101,
                mediaType = "movie",
                title = "Film Da Rimuovere",
                overview = "",
                posterPath = null,
                backdropPath = null,
                voteAverage = 7.5,
                releaseYear = "2025",
                positionMs = 1_200_000L,
                durationMs = 5_400_000L,
                profileId = DEFAULT_PRIMARY_PROFILE_ID
            )
        )
        dao.upsertWatchProgress(
            WatchProgressEntity(
                id = "movie_202",
                tmdbId = 202,
                mediaType = "movie",
                title = "Film Profilo 2",
                overview = "",
                posterPath = null,
                backdropPath = null,
                voteAverage = 8.0,
                releaseYear = "2024",
                positionMs = 600_000L,
                durationMs = 5_000_000L,
                profileId = secondaryProfileId
            )
        )
        dao.addToWatchlist(
            WatchlistEntity(
                id = "movie_303",
                tmdbId = 303,
                mediaType = "movie",
                title = "Film Preferito Intatto",
                overview = "",
                posterPath = null,
                backdropPath = null,
                voteAverage = 8.8,
                releaseYear = "2023",
                profileId = DEFAULT_PRIMARY_PROFILE_ID
            )
        )
        dao.upsertSimklLibraryItem(
            SimklLibraryItemEntity(
                profileId = DEFAULT_PRIMARY_PROFILE_ID,
                stableKey = "simkl_shows_40400",
                simklId = 40400L,
                mediaCategory = "shows",
                tmdbMediaType = "tv",
                tmdbId = 404,
                title = "Serie Simkl Watchlist",
                status = "plantowatch"
            )
        )
        dao.upsertCompletedMedia(
            CompletedMediaEntity(
                profileId = DEFAULT_PRIMARY_PROFILE_ID,
                contentKey = "tv_404_s1_e1",
                tmdbId = 404,
                mediaType = "tv",
                seasonNumber = 1,
                episodeNumber = 1,
                completedAtMs = 1_000L
            )
        )
        dao.upsertEpisodeProgress(
            EpisodeProgressEntity(
                episodeKey = "tv_404_s1_e2",
                tmdbId = 404,
                seasonNumber = 1,
                episodeNumber = 2,
                positionMs = 450_000L,
                durationMs = 2_700_000L,
                profileId = DEFAULT_PRIMARY_PROFILE_ID
            )
        )

        var cwCountState by mutableIntStateOf(1)
        var clearCallbacks by mutableIntStateOf(0)
        val primaryProfile = UserProfileEntity(
            id = DEFAULT_PRIMARY_PROFILE_ID,
            name = "Utente 1",
            avatarId = "avatar_1",
            accentColorHex = 0xFFE50914L,
            isPrimary = true
        )

        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    gitHubState = GitHubUpdateUiState(),
                    streamLang = "it",
                    preferWebPlayer = false,
                    bufferPreset = StreamingBufferPreset.BALANCED,
                    autoCheckUpdates = true,
                    continueWatchingCount = cwCountState,
                    watchlistCount = 1,
                    isTvLayout = true,
                    requestedCategory = SettingsCategory.SYSTEM,
                    requestedCategoryNonce = 1,
                    profileUiState = UserProfileUiState(
                        profiles = listOf(primaryProfile),
                        activeProfile = primaryProfile
                    ),
                    onSelectStreamLang = {},
                    onSetPreferWebPlayer = {},
                    onSelectBufferPreset = {},
                    onSetAutoCheckUpdates = {},
                    onRefreshCatalog = {},
                    onClearContinueWatching = {
                        clearCallbacks++
                        runBlocking {
                            repo.clearAllContinueWatching(DEFAULT_PRIMARY_PROFILE_ID)
                        }
                        cwCountState = 0
                    },
                    onRepoSlugChange = {},
                    onCheckUpdates = {},
                    onTriggerUpdate = {},
                    onUpdateTmdbKey = {},
                    onResetTmdbKey = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Scorre fino al pulsante "Svuota Continua a Guardare" e lo preme: NON deve svuotare subito
        val clearBtn = composeRule.onNodeWithTag("settings_clear_history_button")
        clearBtn.performScrollTo()
        composeRule.waitForIdle()
        clearBtn.requestFocus()
        composeRule.waitForIdle()
        clearBtn.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.waitForIdle()

        assertEquals("Premere Svuota non deve svuotare immediatamente", 0, clearCallbacks)
        composeRule.onNodeWithTag("clear_continue_watching_confirm_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("SVUOTARE CONTINUA A GUARDARE?").assertIsDisplayed()
        composeRule.onNodeWithText("Vuoi davvero rimuovere tutti i contenuti dalla sezione Continua a guardare? Questa operazione non può essere annullata.")
            .assertIsDisplayed()

        // Focus iniziale obbligatoriamente su ANNULLA
        composeRule.onNodeWithTag("clear_cw_cancel_button").assertIsFocused()

        // Test navigazione D-Pad Destra -> CONFERMA, Sinistra -> ANNULLA, poi BACK -> annulla senza svuotare
        composeRule.onNodeWithTag("clear_cw_cancel_button").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_cw_confirm_button").assertIsFocused()

        composeRule.onNodeWithTag("clear_cw_confirm_button").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_cw_cancel_button").assertIsFocused()

        composeRule.onNodeWithTag("clear_cw_cancel_button").performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("clear_continue_watching_confirm_dialog").fetchSemanticsNodes().isEmpty())
        assertEquals(0, clearCallbacks)
        assertEquals(1, dao.observeContinueWatching(DEFAULT_PRIMARY_PROFILE_ID).first().size)

        // Riapre e preme OK direttamente su ANNULLA -> nessuna modifica ai dati
        clearBtn.performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_cw_cancel_button").assertIsFocused()
        composeRule.onNodeWithTag("clear_cw_cancel_button").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("clear_continue_watching_confirm_dialog").fetchSemanticsNodes().isEmpty())
        assertEquals(0, clearCallbacks)
        assertEquals(1, dao.observeContinueWatching(DEFAULT_PRIMARY_PROFILE_ID).first().size)

        // Riapre, si sposta su CONFERMA con freccia Destra e conferma -> svuota SOLO Continua a guardare attivo
        clearBtn.performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_cw_cancel_button").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_cw_confirm_button").assertIsFocused()
        composeRule.onNodeWithTag("clear_cw_confirm_button").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithTag("clear_continue_watching_confirm_dialog").fetchSemanticsNodes().isEmpty())
        assertEquals(1, clearCallbacks)
        assertEquals(0, cwCountState)
        assertTrue(dao.observeContinueWatching(DEFAULT_PRIMARY_PROFILE_ID).first().isEmpty())

        // Altri dati intatti: profilo secondario, La Mia Lista (preferiti), Simkl watchlist, completati ed episodi parziali
        assertEquals(1, dao.observeContinueWatching(secondaryProfileId).first().size)
        assertEquals(1, dao.observeWatchlist(DEFAULT_PRIMARY_PROFILE_ID).first().size)
        assertEquals(1, dao.observeSimklLibraryForProfile(DEFAULT_PRIMARY_PROFILE_ID).first().size)
        assertTrue(dao.getCompletedEpisodesForSeries(404, DEFAULT_PRIMARY_PROFILE_ID).isNotEmpty())
        assertEquals(450_000L, dao.getEpisodeProgress("tv_404_s1_e2", DEFAULT_PRIMARY_PROFILE_ID)?.positionMs)
    }

    // -------------------------------------------------------------------------
    // Fake HTTP Simkl Server per i test di sincronizzazione periodica
    // -------------------------------------------------------------------------

    private class FakePeriodicSimklServer {
        private var serverSocket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()
        private val running = AtomicBoolean(false)

        val activitiesHits = AtomicInteger(0)
        val failActivitiesWith500 = AtomicBoolean(false)
        val returnRemoteMoviePlayback8800 = AtomicBoolean(false)

        @Volatile
        var phase: Int = 1

        val baseUrl: String
            get() = "http://127.0.0.1:${serverSocket!!.localPort}/"

        fun start() {
            serverSocket = ServerSocket(0)
            running.set(true)
            executor.submit {
                while (running.get()) {
                    val client = try {
                        serverSocket?.accept()
                    } catch (_: Exception) {
                        null
                    } ?: break
                    executor.submit { handleClient(client) }
                }
            }
        }

        fun stop() {
            running.set(false)
            runCatching { serverSocket?.close() }
            executor.shutdownNow()
        }

        private fun handleClient(socket: Socket) {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val path = parts.getOrNull(1) ?: "/"

                var contentLength = 0
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = reader.read(buf, readTotal, contentLength - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                }

                val (statusCode, body) = route(path)
                val out = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                val bytes = body.toByteArray(Charsets.UTF_8)
                out.write("HTTP/1.1 $statusCode OK\r\n")
                out.write("Content-Type: application/json; charset=utf-8\r\n")
                out.write("Content-Length: ${bytes.size}\r\n")
                out.write("Connection: close\r\n\r\n")
                out.flush()
                s.getOutputStream().write(bytes)
                s.getOutputStream().flush()
            }
        }

        private fun route(path: String): Pair<Int, String> {
            return when {
                path.startsWith("/sync/activities") -> {
                    activitiesHits.incrementAndGet()
                    if (failActivitiesWith500.get()) {
                        500 to """{"error":"server_error"}"""
                    } else if (phase == 1) {
                        200 to """
                            {
                              "all": "2026-06-10T10:00:00Z",
                              "tv_shows": {
                                "all": "2026-06-10T10:00:00Z",
                                "playback": "2026-06-10T10:00:00Z",
                                "plantowatch": "2026-06-10T10:00:00Z",
                                "watching": "2026-06-10T10:00:00Z"
                              },
                              "movies": {
                                "all": "2026-06-10T10:00:00Z",
                                "playback": "2026-06-10T10:00:00Z"
                              },
                              "anime": {
                                "all": "2026-06-10T10:00:00Z"
                              }
                            }
                        """.trimIndent()
                    } else {
                        200 to """
                            {
                              "all": "2026-06-10T10:15:00Z",
                              "tv_shows": {
                                "all": "2026-06-10T10:15:00Z",
                                "playback": "2026-06-10T10:15:00Z",
                                "plantowatch": "2026-06-10T10:15:00Z",
                                "watching": "2026-06-10T10:15:00Z"
                              },
                              "movies": {
                                "all": "2026-06-10T10:15:00Z",
                                "playback": "2026-06-10T10:15:00Z"
                              },
                              "anime": {
                                "all": "2026-06-10T10:15:00Z"
                              }
                            }
                        """.trimIndent()
                    }
                }
                path.startsWith("/sync/playback") -> {
                    if (returnRemoteMoviePlayback8800.get()) {
                        200 to """
                            [
                              {
                                "id": 999,
                                "progress": 10.0,
                                "paused_at": "2026-06-10T10:05:00Z",
                                "type": "movie",
                                "movie": {
                                  "title": "Film In Riproduzione",
                                  "year": 2025,
                                  "ids": { "simkl": 88001, "tmdb": "8800" }
                                }
                              }
                            ]
                        """.trimIndent()
                    } else {
                        200 to "[]"
                    }
                }
                path.startsWith("/sync/all-items") -> {
                    if (phase == 1) {
                        200 to """
                            {
                              "shows": [
                                {
                                  "last_watched_at": null,
                                  "status": "plantowatch",
                                  "show": {
                                    "title": "Serie Sync Periodica",
                                    "year": 2026,
                                    "ids": { "simkl": 55010, "tmdb": "5501" }
                                  }
                                }
                              ],
                              "movies": [],
                              "anime": []
                            }
                        """.trimIndent()
                    } else {
                        200 to """
                            {
                              "shows": [
                                {
                                  "last_watched_at": "2026-06-10T10:14:00Z",
                                  "status": "watching",
                                  "last_watched": "S01E01",
                                  "next_to_watch": "S01E02",
                                  "show": {
                                    "title": "Serie Sync Periodica",
                                    "year": 2026,
                                    "ids": { "simkl": 55010, "tmdb": "5501" }
                                  },
                                  "seasons": [
                                    {
                                      "number": 1,
                                      "episodes": [
                                        { "number": 1 }
                                      ]
                                    }
                                  ]
                                }
                              ],
                              "movies": [],
                              "anime": []
                            }
                        """.trimIndent()
                    }
                }
                else -> 200 to "{}"
            }
        }
    }
}
