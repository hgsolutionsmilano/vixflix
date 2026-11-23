package com.example

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.local.VixFlixDatabase
import com.example.data.local.WatchProgressEntity
import com.example.data.local.simkl.SimklSecureTokenStore
import com.example.data.local.simkl.SimklTokenBundle
import com.example.data.local.simkl.SoftwareAesGcmSimklCipherEngine
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.simkl.SimklConfig
import com.example.data.remote.simkl.SimklSyncManager
import com.example.data.repository.VixFlixRepository
import com.example.ui.CalendarDate
import com.example.ui.UpcomingEpisodeBadgeFormatter
import com.example.ui.components.ContinueWatchingSection
import com.example.ui.components.consumeBackAndEscapeKey
import com.example.ui.components.tvFocusableControl
import com.example.ui.screens.VixFlixExitConfirmationDialog
import com.example.ui.theme.MyApplicationTheme
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

        // Stesso anno -> "GG MMM"
        assertEquals("23 NOV", UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2026-11-23", today))
        assertEquals("05 LUG", UpcomingEpisodeBadgeFormatter.formatUpcomingEpisodeAirDate("2026-07-05", today))

        // Anno diverso -> "GG MMM YYYY"
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

        // Avanzando a Ep 2 (non ancora trasmesso, stesso anno) -> "23 NOV"
        val tvEntryEp2 = tvEntryEp1.copy(episodeNumber = 2)
        assertEquals(
            "23 NOV",
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

        var badgesMap by mutableStateOf(mapOf("tv_701" to "23 NOV", "movie_703" to "23 NOV"))

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

        // Visibile per la serie TV con episodio futuro
        composeRule.onNodeWithTag("cw_season_episode_badge_tv_701", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("cw_upcoming_date_badge_tv_701", useUnmergedTree = true)
            .assertIsDisplayed()
            .onChild()
            .assertTextEquals("23 NOV")

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
