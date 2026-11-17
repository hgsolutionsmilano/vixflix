package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.GitHubUpdateUiState
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite

@Composable
fun GitHubUpdateSectionScreen(
    state: GitHubUpdateUiState,
    onRepoSlugChange: (String) -> Unit,
    onCheckUpdates: (String) -> Unit,
    onTriggerUpdate: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var inputRepo by remember(state.repoSlug) { mutableStateOf(state.repoSlug) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("github_update_screen"),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 48.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card Stato Versione Corrente & Build CI/CD
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = NetflixRed,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Versione Installata: v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NetflixWhite
                                )
                                Text(
                                    text = "Version Code: ${BuildConfig.VERSION_CODE} • App ID: ${BuildConfig.APPLICATION_ID}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixLightGray
                                )
                            }
                        }

                        Surface(
                            color = NetflixMatchGreen.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, NetflixMatchGreen)
                        ) {
                            Text(
                                text = "CI READY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NetflixMatchGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Controlla e scarica direttamente in-app le nuove release compilate automaticamente su GitHub ad ogni check-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixLightGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputRepo,
                        onValueChange = {
                            inputRepo = it
                            onRepoSlugChange(it)
                        },
                        label = { Text("Repository GitHub (owner/repo)") },
                        placeholder = { Text("es. hgsolutions/vixflix-android") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NetflixRed,
                            unfocusedBorderColor = NetflixElevatedSurface,
                            focusedTextColor = NetflixWhite,
                            unfocusedTextColor = NetflixWhite,
                            focusedLabelColor = NetflixRed,
                            unfocusedLabelColor = NetflixLightGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onCheckUpdates(inputRepo) },
                        enabled = !state.isChecking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("check_github_updates_button")
                    ) {
                        if (state.isChecking) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Controllo GitHub Releases in corso…")
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verifica Nuova Versione su GitHub",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (!state.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = NetflixRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NetflixRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = NetflixWhite,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Risultato controllo Release GitHub
        state.releaseInfo?.let { release ->
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NetflixDarkSurface),
                    border = BorderStroke(
                        1.5.dp,
                        if (release.isUpdateAvailable) NetflixMatchGreen else NetflixElevatedSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (release.isUpdateAvailable) {
                                    Icons.Default.NewReleases
                                } else {
                                    Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = if (release.isUpdateAvailable) NetflixMatchGreen else NetflixLightGray,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (release.isUpdateAvailable) {
                                        "Nuova Versione Disponibile: ${release.tagName}"
                                    } else {
                                        "App Aggiornata all'ultima release (${release.tagName})"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (release.isUpdateAvailable) NetflixMatchGreen else NetflixWhite
                                )
                                Text(
                                    text = "${release.releaseTitle} • Pubblicata: ${release.publishedAt.ifBlank { "Oggi" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixLightGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = release.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixWhite
                        )

                        if (release.apkFileName != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val sizeMb = String.format("%.1f MB", release.apkSizeBytes / (1024.0 * 1024.0))
                            Text(
                                text = "Pacchetto APK: ${release.apkFileName} ($sizeMb)",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixMatchGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onTriggerUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (release.isUpdateAvailable) NetflixMatchGreen else NetflixElevatedSurface,
                                contentColor = if (release.isUpdateAvailable) NetflixBlack else NetflixWhite
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("download_update_apk_button")
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (release.isUpdateAvailable) {
                                    "Scarica e Aggiorna Ora (${release.tagName})"
                                } else {
                                    "Riscarica APK Release (${release.tagName})"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Riepilogo Workflow GitHub Actions configurato nel progetto
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = NetflixRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pipeline GitHub Actions Inclusa nel Progetto",
                            style = MaterialTheme.typography.titleMedium,
                            color = NetflixWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Il file `.github/workflows/android-ci-release.yml` è già configurato per compilare e pubblicare una nuova versione numerata ad ogni check-in (`git push`):",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixLightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = NetflixBlack,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = buildString {
                                appendLine("# Flusso automatico ad ogni check-in:")
                                appendLine("1. Calcola CI_VERSION_NAME = 1.0.<run_number>")
                                appendLine("2. Calcola CI_VERSION_CODE = 100 + <run_number>")
                                appendLine("3. Inietta GITHUB_REPOSITORY e Secrets.TMDB_API_KEY")
                                appendLine("4. Compila APK: gradle assembleDebug")
                                append("5. Pubblica Release v1.0.<run_number> con APK allegato")
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = NetflixMatchGreen,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmUpdate()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NetflixRed,
                    contentColor = Color.White
                )
            ) {
                Text("Scarica e Aggiorna")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDismiss()
                    onOpenGitHubTab()
                }) {
                    Text("Dettagli CI", color = NetflixLightGray)
                }
                TextButton(onClick = onDismiss) {
                    Text("Più tardi", color = NetflixMutedGray)
                }
            }
        }
    )
}
