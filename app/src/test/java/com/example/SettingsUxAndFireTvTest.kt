package com.example

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CacheStatsSummary
import com.example.data.local.PlayerAdvancedSettings
import com.example.data.local.PlayerComfortSettings
import com.example.data.local.StreamingBufferPreset
import com.example.data.local.UserProfileEntity
import com.example.data.local.UserProfileUiState
import com.example.data.remote.simkl.SimklConnectionState
import com.example.data.remote.simkl.SimklProfileUiState
import com.example.ui.GitHubUpdateUiState
import com.example.ui.screens.SettingsCategory
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
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
class SettingsUxAndFireTvTest {

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

    private fun sampleProfiles(): UserProfileUiState {
        val primary = UserProfileEntity(
            id = "p1",
            name = "Marco",
            avatarId = "cinema_crimson",
            accentColorHex = 0xFFE50914L,
            isPrimary = true,
            preferredLanguage = "it",
            preferredAudioLanguage = "it",
            preferredSubtitleLanguage = "it",
            subtitlesEnabled = true
        )
        val secondary = UserProfileEntity(
            id = "p2",
            name = "Giulia",
            avatarId = "neon_cyber",
            accentColorHex = 0xFF2196F3L,
            isPrimary = false,
            preferredLanguage = "it",
            preferredAudioLanguage = "en",
            preferredSubtitleLanguage = "it",
            subtitlesEnabled = true
        )
        return UserProfileUiState(
            profiles = listOf(primary, secondary),
            activeProfile = primary,
            autoLoginEnabled = true,
            defaultProfileId = null,
            maxProfiles = 5
        )
    }

    private fun sampleConnectedSimkl(): SimklProfileUiState = SimklProfileUiState(
        profileId = "p1",
        connectionState = SimklConnectionState.CONNECTED_SYNCED,
        accountDisplayName = "MarcoTV",
        accountId = "445566",
        lastSyncAtMs = System.currentTimeMillis() - 120_000L,
        isClientIdConfigured = true
    )

    private fun assertNonOverlappingHorizontal(leftTag: String, rightTag: String) {
        val leftRect = composeRule.onNodeWithTag(leftTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rightRect = composeRule.onNodeWithTag(rightTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected $leftTag (right=${leftRect.right}) to end at or before $rightTag (left=${rightRect.left})",
            leftRect.right <= rightRect.left + 1f
        )
    }

    private fun assertInsideViewportHorizontally(tag: String, rootBounds: Rect) {
        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected $tag left=${bounds.left} >= rootLeft=${rootBounds.left}",
            bounds.left >= rootBounds.left - 1f
        )
        assertTrue(
            "Expected $tag right=${bounds.right} <= rootRight=${rootBounds.right}",
            bounds.right <= rootBounds.right + 1f
        )
    }

    @Test
    fun dpadNavigation_acrossAllCategoriesAndNavRail_isDeterministic() {
        composeRule.setContent {
            MyApplicationTheme {
                val navRailRequester = remember { FocusRequester() }
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .fillMaxHeight()
                            .focusRequester(navRailRequester)
                            .focusable()
                            .testTag("mock_tv_nav_rail")
                    )
                    SettingsScreen(
                        gitHubState = GitHubUpdateUiState(),
                        streamLang = "it",
                        preferWebPlayer = false,
                        bufferPreset = StreamingBufferPreset.DEFAULT,
                        autoCheckUpdates = true,
                        continueWatchingCount = 3,
                        watchlistCount = 5,
                        isTvLayout = true,
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
                        profileUiState = sampleProfiles(),
                        simklUiState = sampleConnectedSimkl(),
                        navRailFocusRequester = navRailRequester
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Focus iniziale sulla prima categoria
        composeRule.onNodeWithTag("settings_tab_playback").assertIsFocused()

        // Left dalla prima tab porta al menu laterale
        composeRule.onNodeWithTag("settings_tab_playback").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mock_tv_nav_rail").assertIsFocused()

        // Ritorno su playback e navigazione Right su tutte le 4 categorie
        composeRule.onNodeWithTag("settings_tab_playback").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_playback").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_updates").assertIsFocused()
        composeRule.onNodeWithTag("settings_auto_update_switch").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_tab_updates").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_api_sources").assertIsFocused()
        composeRule.onNodeWithTag("tmdb_api_key_input").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_tab_api_sources").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_system").assertIsFocused()
        composeRule.onNodeWithTag("settings_switch_profile_button").assertIsDisplayed()

        // Down entra nel pannello Sistema e Up ritorna esattamente alla tab Sistema
        composeRule.onNodeWithTag("settings_tab_system").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_switch_profile_button").assertIsFocused()

        composeRule.onNodeWithTag("settings_switch_profile_button").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_system").assertIsFocused()
    }

    @Test
    fun systemPanel_defaultProfileChipsAndSimklDpadChain_worksBidirectionally() {
        var selectedDefaultProfileId by mutableStateOf<String?>(null)

        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    gitHubState = GitHubUpdateUiState(),
                    streamLang = "it",
                    preferWebPlayer = false,
                    bufferPreset = StreamingBufferPreset.DEFAULT,
                    autoCheckUpdates = true,
                    continueWatchingCount = 2,
                    watchlistCount = 4,
                    isTvLayout = true,
                    requestedCategory = SettingsCategory.SYSTEM,
                    requestedCategoryNonce = 1,
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
                    profileUiState = sampleProfiles().copy(defaultProfileId = selectedDefaultProfileId),
                    onSetDefaultProfileId = { selectedDefaultProfileId = it },
                    simklUiState = sampleConnectedSimkl()
                )
            }
        }
        composeRule.waitForIdle()

        // Entra nel pannello Sistema: Cambia profilo -> Accesso automatico -> Chip "Ultimo usato"
        composeRule.onNodeWithTag("settings_tab_system").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_switch_profile_button").assertIsFocused()

        composeRule.onNodeWithTag("settings_switch_profile_button").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_autologin_profile_toggle").assertIsFocused()

        composeRule.onNodeWithTag("settings_autologin_profile_toggle").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_default_profile_last_used")
            .assertIsFocused()
            .assertHeightIsAtLeast(48.dp)

        // Navigazione orizzontale sui chip profilo + selezione
        composeRule.onNodeWithTag("settings_default_profile_last_used").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_default_profile_p1")
            .assertIsFocused()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("settings_default_profile_p1").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_default_profile_p2").assertIsFocused()
        composeRule.onNodeWithTag("settings_default_profile_p2").performClick()
        composeRule.waitForIdle()
        assertEquals("p2", selectedDefaultProfileId)

        // Down dai chip profilo atterra su Simkl e Up torna sul chip "Ultimo usato" (mai saltato!)
        composeRule.onNodeWithTag("settings_default_profile_p2").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_sync_now_button").assertIsFocused()

        composeRule.onNodeWithTag("simkl_sync_now_button").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_default_profile_last_used").assertIsFocused()

        composeRule.onNodeWithTag("settings_default_profile_last_used").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_autologin_profile_toggle").assertIsFocused()
    }

    @Test
    fun confirmationDialogs_trapFocusAndRestoreFocusOnBackAndCancel() {
        var unlinkCalls = 0
        var clearCwCalls = 0

        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    gitHubState = GitHubUpdateUiState(),
                    streamLang = "it",
                    preferWebPlayer = false,
                    bufferPreset = StreamingBufferPreset.DEFAULT,
                    autoCheckUpdates = true,
                    continueWatchingCount = 4,
                    watchlistCount = 2,
                    isTvLayout = true,
                    requestedCategory = SettingsCategory.SYSTEM,
                    requestedCategoryNonce = 1,
                    onSelectStreamLang = {},
                    onSetPreferWebPlayer = {},
                    onSelectBufferPreset = {},
                    onSetAutoCheckUpdates = {},
                    onRefreshCatalog = {},
                    onClearContinueWatching = { clearCwCalls++ },
                    onRepoSlugChange = {},
                    onCheckUpdates = {},
                    onTriggerUpdate = {},
                    onUpdateTmdbKey = {},
                    onResetTmdbKey = {},
                    profileUiState = sampleProfiles(),
                    simklUiState = sampleConnectedSimkl(),
                    onUnlinkSimkl = { unlinkCalls++ }
                )
            }
        }
        composeRule.waitForIdle()

        // 1) Popup scollegamento Simkl: focus trap + Back chiude + ripristina focus su simkl_unlink_button
        composeRule.onNodeWithTag("simkl_unlink_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_unlink_confirm_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("simkl_cancel_unlink_button").assertIsFocused()

        // Trap Up/Down/Left su Annulla
        composeRule.onNodeWithTag("simkl_cancel_unlink_button").performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionLeft)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_cancel_unlink_button").assertIsFocused()

        // Right -> Scollega, Trap Right/Up/Down, Left -> Annulla
        composeRule.onNodeWithTag("simkl_cancel_unlink_button").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_confirm_unlink_button").assertIsFocused()

        composeRule.onNodeWithTag("simkl_confirm_unlink_button").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_confirm_unlink_button").assertIsFocused()

        // Back chiude il popup senza scollegare e ripristina focus su simkl_unlink_button
        composeRule.onNodeWithTag("simkl_confirm_unlink_button").performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simkl_unlink_confirm_dialog").assertDoesNotExist()
        assertEquals(0, unlinkCalls)
        composeRule.onNodeWithTag("simkl_unlink_button").assertIsFocused()

        // 2) Popup svuotamento Continua a guardare: focus trap + chiusura su Annulla + ripristino focus
        composeRule.onNodeWithTag("settings_clear_history_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_continue_watching_confirm_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("clear_cw_cancel_button").assertIsFocused()

        composeRule.onNodeWithTag("clear_cw_cancel_button").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_cw_confirm_button").assertIsFocused()

        composeRule.onNodeWithTag("clear_cw_confirm_button").performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_continue_watching_confirm_dialog").assertDoesNotExist()
        assertEquals(0, clearCwCalls)
        composeRule.onNodeWithTag("settings_clear_history_button").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-television-notouch-dpad")
    fun layoutBounds_at720pFireTv_haveZeroOverlapAndZeroClipping() {
        assertSettingsGeometryZeroOverlapAtCurrentResolution()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-television-notouch-dpad")
    fun layoutBounds_at1080pFireTv_haveZeroOverlapAndZeroClipping() {
        assertSettingsGeometryZeroOverlapAtCurrentResolution()
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-television-notouch-dpad")
    fun layoutBounds_at4kFireTv_haveZeroOverlapAndZeroClipping() {
        assertSettingsGeometryZeroOverlapAtCurrentResolution()
    }

    private fun assertSettingsGeometryZeroOverlapAtCurrentResolution() {
        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    gitHubState = GitHubUpdateUiState(),
                    streamLang = "it",
                    preferWebPlayer = false,
                    bufferPreset = StreamingBufferPreset.DEFAULT,
                    autoCheckUpdates = true,
                    continueWatchingCount = 3,
                    watchlistCount = 7,
                    isTvLayout = true,
                    requestedCategory = SettingsCategory.SYSTEM,
                    requestedCategoryNonce = 1,
                    cacheStats = CacheStatsSummary(
                        imageCacheSizeBytes = 14L * 1024L * 1024L,
                        validStreamLinksCount = 8,
                        cachedDetailsCount = 24,
                        cachedSeasonsCount = 12,
                        catalogUpdatedAtMs = System.currentTimeMillis() - 60_000L
                    ),
                    playerAdvancedSettings = PlayerAdvancedSettings(),
                    playerComfortSettings = PlayerComfortSettings(),
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
                    profileUiState = sampleProfiles(),
                    simklUiState = sampleConnectedSimkl()
                )
            }
        }
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

        // 1) Le 4 categorie devono stare interamente nello schermo senza sovrapposizioni
        val tabTags = listOf(
            "settings_tab_playback",
            "settings_tab_updates",
            "settings_tab_api_sources",
            "settings_tab_system"
        )
        tabTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
            assertInsideViewportHorizontally(tag, rootBounds)
        }
        for (i in 0 until tabTags.lastIndex) {
            assertNonOverlappingHorizontal(tabTags[i], tabTags[i + 1])
        }

        // 2) Pulsanti affiancati Profili: zero overlap e dentro i limiti viewport
        assertInsideViewportHorizontally("settings_switch_profile_button", rootBounds)
        assertInsideViewportHorizontally("settings_manage_profiles_button", rootBounds)
        assertNonOverlappingHorizontal("settings_switch_profile_button", "settings_manage_profiles_button")

        // 3) Pulsanti affiancati Simkl: zero overlap e dentro i limiti viewport
        composeRule.onNodeWithTag("simkl_sync_now_button").performScrollTo()
        composeRule.waitForIdle()
        assertInsideViewportHorizontally("simkl_sync_now_button", rootBounds)
        assertInsideViewportHorizontally("simkl_unlink_button", rootBounds)
        assertNonOverlappingHorizontal("simkl_sync_now_button", "simkl_unlink_button")

        // 4) Pulsanti affiancati Cache: zero overlap e dentro i limiti viewport
        composeRule.onNodeWithTag("settings_refresh_catalog_cache_button").performScrollTo()
        composeRule.waitForIdle()
        assertInsideViewportHorizontally("settings_refresh_catalog_cache_button", rootBounds)
        assertInsideViewportHorizontally("settings_clear_cache_button", rootBounds)
        assertNonOverlappingHorizontal("settings_refresh_catalog_cache_button", "settings_clear_cache_button")

        // 5) Dialogo conferma Simkl: pulsanti dentro i limiti e non sovrapposti
        composeRule.onNodeWithTag("simkl_unlink_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        val dialogBounds = composeRule.onNodeWithTag("simkl_unlink_confirm_dialog").getUnclippedBoundsInRoot()
        assertTrue(dialogBounds.width > 0.dp)
        assertNonOverlappingHorizontal("simkl_cancel_unlink_button", "simkl_confirm_unlink_button")
        composeRule.onNodeWithTag("simkl_cancel_unlink_button").performClick()
        composeRule.waitForIdle()
    }
}
