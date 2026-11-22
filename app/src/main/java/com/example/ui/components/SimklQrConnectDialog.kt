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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
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
import com.example.data.remote.simkl.SimklAuthFlowMode
import com.example.data.remote.simkl.SimklQrDialogState
import com.example.data.remote.simkl.SimklQrMatrix
import com.example.data.remote.simkl.SimklQrResolutionPrompt
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
    onDismiss: () -> Unit,
    onConfirmSwitchProfile: () -> Unit = {},
    onConfirmReplaceAccount: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedFeedback by remember(state.userCode) { mutableStateOf(false) }

    val resolutionActionFocus = remember { FocusRequester() }
    val copyButtonFocus = remember { FocusRequester() }
    val regenerateButtonFocus = remember { FocusRequester() }
    val closeButtonFocus = remember { FocusRequester() }

    val hasCodeForCopy = !state.userCode.isNullOrBlank() &&
        !state.isExpired &&
        !state.isDenied &&
        !state.isCompleted &&
        state.resolutionPrompt == null

    LaunchedEffect(
        state.userCode,
        state.isLoading,
        state.isExpired,
        state.isDenied,
        state.isClientIdMissing,
        state.resolutionPrompt
    ) {
        repeat(3) { withFrameNanos { } }
        when {
            state.resolutionPrompt != null -> resolutionActionFocus.safeRequestFocus()
            state.canRegenerate && (state.isExpired || state.isDenied || state.errorMessage != null) ->
                regenerateButtonFocus.safeRequestFocus()
            hasCodeForCopy ->
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
                .background(Color.Black.copy(alpha = 0.84f))
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
                    .widthIn(max = 880.dp)
                    .testTag("simkl_qr_connect_dialog")
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp)
                ) {
                    val isWideLayout = maxWidth >= 560.dp

                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Intestazione TV chiara e priva di input manuali
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
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCode2,
                                            contentDescription = null,
                                            tint = NetflixRed,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "COLLEGA IL TUO ACCOUNT SIMKL",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NetflixWhite
                                    )
                                    Text(
                                        text = "Scansiona il QR Code con il tuo smartphone per collegare il tuo account Simkl.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NetflixLightGray
                                    )
                                    Text(
                                        text = if (state.flowMode == SimklAuthFlowMode.AUTHENTICATE_OR_CREATE_PROFILE) {
                                            "Riconoscimento automatico o creazione profilo locale VixFlix"
                                        } else {
                                            "Profilo di destinazione: \"$profileName\""
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NetflixLightGray.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            if (!state.isLoading &&
                                !state.isExpired &&
                                !state.isDenied &&
                                !state.isCompleted &&
                                state.resolutionPrompt == null &&
                                state.remainingSeconds > 0
                            ) {
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

                        // Contenuto principale: colonna QR + PIN temporaneo sotto il QR, e istruzioni/conflitti
                        if (isWideLayout) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SimklQrAndPinColumn(
                                    state = state,
                                    copiedFeedback = copiedFeedback,
                                    qrBoxSize = 212.dp,
                                    modifier = Modifier.width(228.dp)
                                )
                                SimklQrDetailsColumn(
                                    state = state,
                                    resolutionActionFocus = resolutionActionFocus,
                                    downActionFocus = closeButtonFocus,
                                    onConfirmSwitchProfile = onConfirmSwitchProfile,
                                    onConfirmReplaceAccount = onConfirmReplaceAccount,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SimklQrAndPinColumn(
                                    state = state,
                                    copiedFeedback = copiedFeedback,
                                    qrBoxSize = 212.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                SimklQrDetailsColumn(
                                    state = state,
                                    resolutionActionFocus = resolutionActionFocus,
                                    downActionFocus = closeButtonFocus,
                                    onConfirmSwitchProfile = onConfirmSwitchProfile,
                                    onConfirmReplaceAccount = onConfirmReplaceAccount,
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
                            if (hasCodeForCopy) {
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

                            if (state.resolutionPrompt == null && !state.isClientIdMissing) {
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
                                        .weight(1.15f)
                                        .heightIn(min = 48.dp)
                                        .safeFocusRequester(regenerateButtonFocus)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.DirectionLeft -> {
                                                        if (hasCodeForCopy) copyButtonFocus.safeRequestFocus() else true
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
                                            if (hasCodeForCopy) {
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
                                        text = "GENERA NUOVO CODICE",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NetflixElevatedSurface),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixLightGray),
                                modifier = Modifier
                                    .weight(0.85f)
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(closeButtonFocus)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionUp -> {
                                                    if (state.resolutionPrompt != null) {
                                                        resolutionActionFocus.safeRequestFocus()
                                                    } else {
                                                        true
                                                    }
                                                }
                                                Key.DirectionLeft -> {
                                                    if (state.resolutionPrompt == null && !state.isClientIdMissing && state.canRegenerate) {
                                                        regenerateButtonFocus.safeRequestFocus()
                                                    } else if (hasCodeForCopy) {
                                                        copyButtonFocus.safeRequestFocus()
                                                    } else {
                                                        true
                                                    }
                                                }
                                                Key.DirectionRight, Key.DirectionDown -> true
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    .focusProperties {
                                        if (state.resolutionPrompt != null) {
                                            resolutionActionFocus.takeIfAttached()?.let { up = it }
                                        } else {
                                            if (!state.isClientIdMissing && state.canRegenerate) {
                                                regenerateButtonFocus.takeIfAttached()?.let { left = it }
                                            } else if (hasCodeForCopy) {
                                                copyButtonFocus.takeIfAttached()?.let { left = it }
                                            }
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
                                    text = "ANNULLA",
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
private fun SimklQrAndPinColumn(
    state: SimklQrDialogState,
    copiedFeedback: Boolean,
    qrBoxSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        Text(
            text = "Inquadra il QR Code con il telefono",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = NetflixWhite,
            textAlign = TextAlign.Center
        )

        SimklQrBox(
            state = state,
            modifier = Modifier.size(qrBoxSize)
        )

        // PIN temporaneo ben visibile direttamente sotto il QR Code
        if (!state.userCode.isNullOrBlank() && state.resolutionPrompt == null && !state.isClientIdMissing) {
            Surface(
                color = NetflixDarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NetflixElevatedSurface),
                modifier = Modifier.widthIn(min = qrBoxSize)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "PIN TEMPORANEO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetflixLightGray
                    )
                    Text(
                        text = state.userCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NetflixWhite,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("simkl_user_code")
                    )
                    if (copiedFeedback) {
                        Text(
                            text = "Codice copiato",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetflixMatchGreen
                        )
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
    val showQrMatrix = state.qrMatrix != null &&
        !state.isLoading &&
        !state.isExpired &&
        !state.isDenied &&
        !state.isCompleted &&
        state.errorMessage == null &&
        state.resolutionPrompt == null

    Surface(
        color = if (showQrMatrix) Color.White else NetflixDarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, NetflixElevatedSurface),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
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
                state.resolutionPrompt != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Account Simkl autorizzato",
                            tint = NetflixMatchGreen,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = "Account autorizzato",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                state.isExpired || state.isDenied || state.errorMessage != null || state.qrMatrix == null -> {
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
                                state.isClientIdMissing -> "Configurazione assente"
                                else -> "QR non disponibile"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    SimklQrCodeCanvas(
                        matrix = state.qrMatrix!!,
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

        val gridX = FloatArray(matrix.size + 1) { c ->
            kotlin.math.round(originX + (c + quietModules) * cellPx)
        }
        val gridY = FloatArray(matrix.size + 1) { r ->
            kotlin.math.round(originY + (r + quietModules) * cellPx)
        }

        for (r in 0 until matrix.size) {
            val top = gridY[r]
            val height = gridY[r + 1] - top
            if (height <= 0f) continue
            for (c in 0 until matrix.size) {
                if (matrix[r, c]) {
                    val left = gridX[c]
                    val width = gridX[c + 1] - left
                    if (width > 0f) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimklQrDetailsColumn(
    state: SimklQrDialogState,
    resolutionActionFocus: FocusRequester,
    downActionFocus: FocusRequester,
    onConfirmSwitchProfile: () -> Unit,
    onConfirmReplaceAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        val prompt = state.resolutionPrompt
        if (prompt != null) {
            Surface(
                color = NetflixDarkSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simkl_resolution_prompt")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = when (prompt) {
                                is SimklQrResolutionPrompt.AlreadyLinkedToOtherProfile ->
                                    "Account Simkl già collegato su questo dispositivo"
                                is SimklQrResolutionPrompt.ConfirmReplaceExistingSimkl ->
                                    "Sostituire l'account Simkl collegato?"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite
                        )
                    }

                    when (prompt) {
                        is SimklQrResolutionPrompt.AlreadyLinkedToOtherProfile -> {
                            Text(
                                text = "Questo account Simkl (${prompt.simklDisplayName}) è già associato al profilo VixFlix \"${prompt.existingProfileName}\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = NetflixWhite
                            )
                            Text(
                                text = "Per evitare duplicati tra profili sullo stesso televisore, puoi passare direttamente a \"${prompt.existingProfileName}\" senza perdere preferiti o progressi.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixLightGray
                            )
                            Button(
                                onClick = onConfirmSwitchProfile,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(resolutionActionFocus)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            keyEvent.key == Key.DirectionDown
                                        ) {
                                            downActionFocus.safeRequestFocus()
                                        } else {
                                            false
                                        }
                                    }
                                    .focusProperties {
                                        downActionFocus.takeIfAttached()?.let { down = it }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedScale = 1.02f,
                                        focusTag = "simkl_switch_existing_profile_button"
                                    )
                                    .testTag("simkl_switch_existing_profile_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PASSA A \"${prompt.existingProfileName}\"",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        is SimklQrResolutionPrompt.ConfirmReplaceExistingSimkl -> {
                            Text(
                                text = "Il profilo \"${prompt.targetProfileName}\" è già collegato a ${prompt.currentAccountName ?: "un altro account Simkl"}. Vuoi collegarlo invece a ${prompt.newAccountName}?",
                                style = MaterialTheme.typography.bodySmall,
                                color = NetflixWhite
                            )
                            Text(
                                text = "Preferiti (La Mia Lista), progressi di visione e impostazioni locali di \"${prompt.targetProfileName}\" resteranno intatti.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixLightGray
                            )
                            Button(
                                onClick = onConfirmReplaceAccount,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .safeFocusRequester(resolutionActionFocus)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            keyEvent.key == Key.DirectionDown
                                        ) {
                                            downActionFocus.safeRequestFocus()
                                        } else {
                                            false
                                        }
                                    }
                                    .focusProperties {
                                        downActionFocus.takeIfAttached()?.let { down = it }
                                    }
                                    .tvFocusableControl(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedScale = 1.02f,
                                        focusTag = "simkl_confirm_replace_account_button"
                                    )
                                    .testTag("simkl_confirm_replace_account_button")
                            ) {
                                Text(
                                    text = "SOSTITUISCI ACCOUNT",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
            return@Column
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
                if (!state.isCompleted && state.errorMessage == null && !state.isExpired && !state.isDenied && !state.isLoading) {
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

        if (!state.isClientIdMissing) {
            if (!state.verificationUrl.isNullOrBlank()) {
                Surface(
                    color = NetflixDarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NetflixElevatedSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (state.hasCompleteVerificationUri) {
                            "Oppure apri dal browser sullo smartphone: ${state.verificationUrl}"
                        } else {
                            "Oppure visita ${state.verificationUrl} e inserisci il PIN ${state.userCode.orEmpty()}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixLightGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("simkl_verification_url")
                    )
                }
            }

            // 4 passaggi chiari per registrazione o login su smartphone
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SimklStepRow(
                    number = "1",
                    text = "Inquadra il QR Code con la fotocamera dello smartphone."
                )
                SimklStepRow(
                    number = "2",
                    text = "Accedi con un account Simkl esistente oppure registrane uno nuovo."
                )
                SimklStepRow(
                    number = "3",
                    text = if (state.hasCompleteVerificationUri) {
                        "Conferma l'autorizzazione per VixFlix sulla pagina ufficiale Simkl."
                    } else {
                        "Inserisci il PIN temporaneo mostrato sotto il QR Code e autorizza VixFlix."
                    }
                )
                SimklStepRow(
                    number = "4",
                    text = "Il collegamento e la sincronizzazione sulla TV si completeranno automaticamente."
                )
            }
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
