package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.GitHubUpdateUiState
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite

@Composable
fun GitHubUpdateDialog(
    state: GitHubUpdateUiState,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit,
    onOpenGitHubTab: () -> Unit
) {
    val release = state.releaseInfo
    if (!state.showUpdateDialog || release == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetflixCardSurface,
        titleContentColor = NetflixWhite,
        textContentColor = NetflixLightGray,
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
                modifier = Modifier.tvFocusableControl(shape = RoundedCornerShape(20.dp), focusedScale = 1.05f)
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
            Row {
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenGitHubTab()
                    },
                    modifier = Modifier.tvFocusableControl(shape = RoundedCornerShape(20.dp), focusedScale = 1.04f)
                ) {
                    Text("Dettagli CI", color = NetflixLightGray)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.tvFocusableControl(shape = RoundedCornerShape(20.dp), focusedScale = 1.04f)
                ) {
                    Text("Più tardi", color = NetflixMutedGray)
                }
            }
        }
    )
}
