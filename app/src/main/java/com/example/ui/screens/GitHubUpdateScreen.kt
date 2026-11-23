package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.GitHubUpdateUiState
import com.example.ui.components.DpadFocusScope
import com.example.ui.components.DpadFocusScopeProvider
import com.example.ui.components.consumeBackAndEscapeKey
import com.example.ui.components.recoverFocusFromCandidates
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

@Composable
fun GitHubUpdateDialog(
    state: GitHubUpdateUiState,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit,
    onOpenGitHubTab: () -> Unit
) {
    val release = state.releaseInfo
    if (!state.showUpdateDialog || release == null) return

    val confirmFocusRequester = remember { FocusRequester() }
    val detailsFocusRequester = remember { FocusRequester() }
    val dismissFocusRequester = remember { FocusRequester() }

    // Focus iniziale deterministico e passaggio automatico su "Più tardi" se il download disabilita "Installa Ora"
    LaunchedEffect(release.tagName, state.isDownloadingApk) {
        val primaryTarget = if (state.isDownloadingApk) dismissFocusRequester else confirmFocusRequester
        for (attempt in 1..4) {
            androidx.compose.runtime.withFrameNanos { }
            if (primaryTarget.safeRequestFocus()) break
        }
        delay(60L)
        primaryTarget.safeRequestFocus()
    }

    DpadFocusScopeProvider(
        scope = DpadFocusScope.MODAL_QUICK_ACTION,
        onRecoverFocus = {
            recoverFocusFromCandidates(
                "github_update_confirm_button" to if (!state.isDownloadingApk) confirmFocusRequester else null,
                "github_update_dismiss_button" to dismissFocusRequester,
                "github_update_details_button" to detailsFocusRequester
            )
        }
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = NetflixCardSurface,
            titleContentColor = NetflixWhite,
            textContentColor = NetflixLightGray,
            modifier = Modifier
                .consumeBackAndEscapeKey(onBack = onDismiss)
                .testTag("github_update_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Aggiornamento GitHub ${release.tagName}",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Versione installata: v${release.currentVersion} → Nuova versione: ${release.tagName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = NetflixMatchGreen
                    )
                    Text(
                        text = release.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixWhite
                    )
                    if (state.isDownloadingApk) {
                        val pct = (state.downloadProgress * 100).toInt()
                        Text(
                            text = "Scaricamento automatico in background in corso ($pct%)… L'installazione partirà al termine.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NetflixMatchGreen
                        )
                        LinearProgressIndicator(
                            progress = { state.downloadProgress.coerceIn(0f, 1f) },
                            color = NetflixMatchGreen,
                            trackColor = NetflixElevatedSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    } else if (state.autoInstallTriggered) {
                        Text(
                            text = "Download completato! Apertura installer pacchetto Android…",
                            style = MaterialTheme.typography.labelSmall,
                            color = NetflixMatchGreen
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmUpdate()
                        if (!state.isDownloadingApk) {
                            onDismiss()
                        }
                    },
                    enabled = !state.isDownloadingApk,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .safeFocusRequester(confirmFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> dismissFocusRequester.safeRequestFocus()
                                    Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusProperties {
                            dismissFocusRequester.takeIfAttached()?.let { left = it }
                        }
                        .tvFocusableControl(
                            shape = RoundedCornerShape(20.dp),
                            focusedScale = 1.05f,
                            focusTag = "github_update_confirm_button"
                        )
                        .testTag("github_update_confirm_button")
                ) {
                    Text(
                        if (state.isDownloadingApk) {
                            "Download ${(state.downloadProgress * 100).toInt()}%…"
                        } else {
                            "Installa Ora"
                        }
                    )
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onOpenGitHubTab()
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(detailsFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionRight -> dismissFocusRequester.safeRequestFocus()
                                        Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                dismissFocusRequester.takeIfAttached()?.let { right = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(20.dp),
                                focusedScale = 1.04f,
                                focusTag = "github_update_details_button"
                            )
                            .testTag("github_update_details_button")
                    ) {
                        Text("Dettagli CI", color = NetflixLightGray)
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .safeFocusRequester(dismissFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> detailsFocusRequester.safeRequestFocus()
                                        Key.DirectionRight -> if (!state.isDownloadingApk) confirmFocusRequester.safeRequestFocus() else true
                                        Key.DirectionUp, Key.DirectionDown -> true
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                detailsFocusRequester.takeIfAttached()?.let { left = it }
                                if (!state.isDownloadingApk) {
                                    confirmFocusRequester.takeIfAttached()?.let { right = it }
                                }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(20.dp),
                                focusedScale = 1.04f,
                                focusTag = "github_update_dismiss_button"
                            )
                            .testTag("github_update_dismiss_button")
                    ) {
                        Text("Più tardi", color = NetflixMutedGray)
                    }
                }
            }
        )
    }
}

