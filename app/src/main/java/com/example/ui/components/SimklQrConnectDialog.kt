package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.simkl.SimklQrDialogState
import com.example.data.remote.simkl.SimklQrMatrix
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite

@Composable
fun SimklQrConnectDialog(
    state: SimklQrDialogState,
    profileName: String,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedFeedback by remember(state.userCode) { mutableStateOf(false) }

    val copyButtonFocus = remember { FocusRequester() }
    val regenerateButtonFocus = remember { FocusRequester() }
    val closeButtonFocus = remember { FocusRequester() }

    LaunchedEffect(state.userCode, state.isLoading, state.isExpired, state.isDenied) {
        repeat(3) { withFrameNanos { } }
        when {
            state.canRegenerate && (state.isExpired || state.isDenied || state.errorMessage != null) ->
                regenerateButtonFocus.safeRequestFocus()
            !state.userCode.isNullOrBlank() ->
                copyButtonFocus.safeRequestFocus()
            else ->
                closeButtonFocus.safeRequestFocus()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(onBack = onDismiss)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.key == Key.Back || keyEvent.key == Key.Escape)
                    ) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Surface(
                color = NetflixCardSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp)
                    .testTag("simkl_qr_connect_dialog")
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    val isWideLayout = maxWidth >= 560.dp

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Intestazione dialog
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = NetflixRed.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.45f))
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCode2,
                                            contentDescription = null,
                                            tint = NetflixRed,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "COLLEGA IL TUO ACCOUNT SIMKL",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NetflixWhite
                                    )
                                    Text(
                                        text = "Sincronizza film e serie TV in cloud per il profilo \"$profileName\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NetflixLightGray
                                    )
                                }
                            }

                            if (!state.isLoading && !state.isExpired && !state.isDenied && !state.isCompleted && state.remainingSeconds > 0) {
                                Surface(
                                    color = NetflixDarkSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, NetflixElevatedSurface)
                                ) {
                                    Text(
                                        text = formatCountdownMmSs(state.remainingSeconds),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.remainingSeconds <= 60) NetflixRed else NetflixMatchGreen,
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .testTag("simkl_qr_countdown")
                                    )
                                }
                            }
                        }

                        // Contenuto principale: QR ad alto contrasto + codice / istruzioni
                        if (isWideLayout) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SimklQrBox(
                                    state = state,
                                    modifier = Modifier.size(196.dp)
                                )
                                SimklQrDetailsColumn(
                                    state = state,
                                    copiedFeedback = copiedFeedback,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SimklQrBox(
                                    state = state,
                                    modifier = Modifier.size(204.dp)
                                )
                                SimklQrDetailsColumn(
                                    state = state,
                                    copiedFeedback = copiedFeedback,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Barra azioni con supporto completo D-Pad e touch
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusGroup()
                        ) {
                            val hasCode = !state.userCode.isNullOrBlank() && !state.isExpired && !state.isCompleted
                            if (hasCode) {
                                OutlinedButton(
                                    onClick = {
                                        val code = state.userCode.orEmpty()
                                        if (code.isNotBlank()) {
                                            clipboardManager.setText(AnnotatedString(code))
                                            copiedFeedback = true
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .safeFocusRequester(copyButtonFocus)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.DirectionRight -> {
                                                        if (state.canRegenerate) regenerateButtonFocus.safeRequestFocus()
                                                        else closeButtonFocus.safeRequestFocus()
                                                    }
                                                    Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> true
                                                    else -> false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                        .focusProperties {
                                            if (state.canRegenerate) {
                                                regenerateButtonFocus.takeIfAttached()?.let { right = it }
                                            } else {
                                                closeButtonFocus.takeIfAttached()?.let { right = it }
                                            }
                                        }
                                        .tvFocusableControl(
                                            shape = RoundedCornerShape(10.dp),
                                            focusedScale = 1.02f,
                                            focusTag = "simkl_copy_code_button"
                                        )
                                        .testTag("simkl_copy_code_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (copiedFeedback) "CODICE COPIATO" else "COPIA CODICE",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    copiedFeedback = false
                                    onRegenerate()
                                },
                                enabled = state.canRegenerate,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(regenerateButtonFocus)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> {
                                                    if (hasCode) copyButtonFocus.safeRequestFocus() else true
                                                }
                                                Key.DirectionRight -> closeButtonFocus.safeRequestFocus()
                                                Key.DirectionUp, Key.DirectionDown -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .focusProperties {
                                        if (hasCode) {
                                            copyButtonFocus.takeIfAttached()?.let { left = it }
                                        }
                                        closeButtonFocus.takeIfAttached()?.let { right = it }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedScale = 1.02f,
                                        focusTag = "simkl_regenerate_code_button"
                                    )
                                    .testTag("simkl_regenerate_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "RIGENERA CODICE",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NetflixElevatedSurface),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixLightGray),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(closeButtonFocus)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> {
                                                    if (state.canRegenerate) regenerateButtonFocus.safeRequestFocus()
                                                    else if (hasCode) copyButtonFocus.safeRequestFocus()
                                                    else true
                                                }
                                                Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .focusProperties {
                                        if (state.canRegenerate) {
                                            regenerateButtonFocus.takeIfAttached()?.let { left = it }
                                        } else if (hasCode) {
                                            copyButtonFocus.takeIfAttached()?.let { left = it }
                                        }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedScale = 1.02f,
                                        focusTag = "simkl_close_qr_dialog_button"
                                    )
                                    .testTag("simkl_close_qr_dialog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CHIUDI",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimklQrBox(
    state: SimklQrDialogState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (state.qrMatrix != null && !state.isExpired && !state.isDenied && !state.isCompleted) {
            Color.White
        } else {
            NetflixDarkSurface
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, NetflixElevatedSurface),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(14.dp)
        ) {
            when {
                state.isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = NetflixRed)
                        Text(
                            text = "Generazione QR…",
                            style = MaterialTheme.typography.labelMedium,
                            color = NetflixLightGray
                        )
                    }
                }
                state.isCompleted -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Collegamento Simkl completato",
                            tint = NetflixMatchGreen,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "Collegato!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite
                        )
                    }
                }
                state.isExpired || state.isDenied || state.errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Codice non attivo",
                            tint = NetflixRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = when {
                                state.isExpired -> "Codice scaduto"
                                state.isDenied -> "Autorizzazione negata"
                                else -> "QR non disponibile"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                state.qrMatrix != null -> {
                    SimklQrCodeCanvas(
                        matrix = state.qrMatrix,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("simkl_qr_code")
                    )
                }
            }
        }
    }
}

@Composable
private fun SimklQrCodeCanvas(
    matrix: SimklQrMatrix,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val quietModules = 4
        val totalModules = matrix.size + quietModules * 2
        val side = minOf(size.width, size.height)
        val cellPx = side / totalModules.toFloat()
        val originX = (size.width - cellPx * totalModules) / 2f
        val originY = (size.height - cellPx * totalModules) / 2f

        drawRect(
            color = Color.White,
            topLeft = Offset(originX, originY),
            size = Size(cellPx * totalModules, cellPx * totalModules)
        )

        for (r in 0 until matrix.size) {
            for (c in 0 until matrix.size) {
                if (matrix[r, c]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(
                            x = originX + (c + quietModules) * cellPx,
                            y = originY + (r + quietModules) * cellPx
                        ),
                        // Piccolo overlap sub-pixel per evitare linee bianche anti-aliased su schermi TV
                        size = Size(cellPx + 0.5f, cellPx + 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SimklQrDetailsColumn(
    state: SimklQrDialogState,
    copiedFeedback: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Codice breve manuale + URI di verifica
        if (!state.userCode.isNullOrBlank()) {
            Surface(
                color = NetflixDarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "CODICE DI VERIFICA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetflixLightGray
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.userCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 26.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NetflixWhite,
                            modifier = Modifier.testTag("simkl_user_code")
                        )
                        if (copiedFeedback) {
                            Surface(
                                color = NetflixMatchGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Copiato!",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NetflixMatchGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    if (!state.verificationUrl.isNullOrBlank()) {
                        Text(
                            text = "Oppure apri dal browser: ${state.verificationUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("simkl_verification_url")
                        )
                    }
                }
            }
        }

        // Stato live del polling o eventuale errore chiaro
        val statusColor = when {
            state.isCompleted -> NetflixMatchGreen
            state.errorMessage != null || state.isExpired || state.isDenied -> NetflixRed
            state.isSlowDownActive -> Color(0xFFFFB74D)
            else -> NetflixLightGray
        }
        Surface(
            color = statusColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (!state.isCompleted && state.errorMessage == null && !state.isExpired && !state.isDenied) {
                    CircularProgressIndicator(
                        color = NetflixRed,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = state.errorMessage ?: state.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.errorMessage != null) NetflixWhite else statusColor,
                    modifier = Modifier.testTag("simkl_qr_status")
                )
            }
        }

        // 4 passaggi chiari per smartphone
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SimklStepRow(number = "1", text = "Inquadra il QR Code con la fotocamera dello smartphone.")
            SimklStepRow(number = "2", text = "Accedi al tuo account Simkl oppure registrane uno nuovo.")
            SimklStepRow(number = "3", text = "Conferma l'autorizzazione per VixFlix con il codice mostrato.")
            SimklStepRow(number = "4", text = "Il collegamento su questa TV si completerà automaticamente.")
        }
    }
}

@Composable
private fun SimklStepRow(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(NetflixElevatedSurface)
                .border(1.dp, NetflixRed.copy(alpha = 0.6f), CircleShape)
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = NetflixWhite
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = NetflixLightGray
        )
    }
}

private fun formatCountdownMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val min = safe / 60
    val sec = safe % 60
    return "%02d:%02d".format(min, sec)
}
