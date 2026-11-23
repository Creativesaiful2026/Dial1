package com.example.feature.incall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.DateTimeUtils
import com.example.core.datastore.CallerAnswerStyle
import com.example.core.datastore.CallerNamePosition
import com.example.core.datastore.CallerScreenThemeStyle
import com.example.core.datastore.DialMeConfig
import com.example.core.designsystem.ContactAvatar
import com.example.core.designsystem.DialMeCard
import com.example.core.designsystem.JetBrainsMonoFontFamily
import com.example.domain.model.ActiveCallSession
import com.example.domain.model.AudioRouteType
import com.example.domain.model.CallStateType
import com.example.domain.model.CallerVerificationStatus
import com.example.domain.model.InCallUiState
import com.example.domain.model.QuickResponse
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(
    uiState: InCallUiState,
    config: DialMeConfig,
    quickResponses: List<QuickResponse>,
    isPreviewMode: Boolean = false,
    onAnswer: (String) -> Unit,
    onReject: (String, String?) -> Unit,
    onDisconnect: (String) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleSpeaker: () -> Unit,
    onSelectAudioEndpoint: (String) -> Unit,
    onToggleHold: (String) -> Unit,
    onSwapCalls: () -> Unit,
    onMergeConference: () -> Unit,
    onPlayDtmf: (Char) -> Unit,
    onStopDtmf: () -> Unit,
    onSaveNote: (String, String) -> Unit,
    onExitPreview: () -> Unit = {}
) {
    val primary = uiState.primaryCall ?: return
    val cs = MaterialTheme.colorScheme
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSmsSheet by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    var elapsedSeconds by remember(primary.callId, primary.connectTimeMillis, primary.state) {
        mutableLongStateOf(0L)
    }
    LaunchedEffect(primary.callId, primary.connectTimeMillis, primary.state) {
        while (primary.state == CallStateType.ACTIVE && primary.connectTimeMillis > 0L) {
            elapsedSeconds = ((System.currentTimeMillis() - primary.connectTimeMillis) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070B14))) {
        // Configurable Caller Screen Backdrop
        when (config.callerScreenTheme) {
            CallerScreenThemeStyle.FULL_PHOTO -> {
                if (!primary.photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = primary.photoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.55f)
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.img_caller_backdrop),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.7f)
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f))
                        )
                    )
                )
            }
            CallerScreenThemeStyle.AURORA_GRADIENT -> {
                Image(
                    painter = painterResource(R.drawable.img_caller_backdrop),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.72f)
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(cs.primary.copy(alpha = 0.22f), Color(0xCC060912))
                        )
                    )
                )
            }
            CallerScreenThemeStyle.MINIMAL_GLASS -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            listOf(cs.primary.copy(alpha = 0.3f), Color(0xFF060912))
                        )
                    )
                )
            }
            CallerScreenThemeStyle.CLASSIC_SOLID -> {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B1120)))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: Preview badge or secondary held call
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isPreviewMode) {
                    AssistChip(
                        onClick = onExitPreview,
                        label = { Text("Caller Screen Theme Preview · Tap to Close") }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                uiState.secondaryCall?.let { secondary ->
                    DialMeCard(containerColor = Color(0x551E293B), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = secondary.displayName ?: secondary.number,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.incall_on_hold),
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (primary.canSwap) {
                                TextButton(onClick = onSwapCalls) {
                                    Icon(Icons.Rounded.SwapCalls, null, Modifier.size(16.dp), tint = Color(0xFF2DD4BF))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.incall_swap), color = Color(0xFF2DD4BF))
                                }
                            }
                            if (primary.canMerge) {
                                TextButton(onClick = onMergeConference) {
                                    Icon(Icons.Rounded.CallMerge, null, Modifier.size(16.dp), tint = Color(0xFF60A5FA))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.incall_merge), color = Color(0xFF60A5FA))
                                }
                            }
                        }
                    }
                }
            }

            if (config.callerNamePosition == CallerNamePosition.TOP) {
                CallerIdentityBlock(primary, elapsedSeconds, cs.primary)
            } else {
                Spacer(Modifier.height(12.dp))
                CallerIdentityBlock(primary, elapsedSeconds, cs.primary)
            }

            // Bottom area: Incoming vs Active controls
            if (primary.state == CallStateType.RINGING) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TextButton(onClick = { showSmsSheet = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Message, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.incall_quick_sms), color = Color.White)
                    }

                    if (config.callerAnswerStyle == CallerAnswerStyle.SWIPE_PILL) {
                        SwipeAnswerBar(
                            onAnswer = { onAnswer(primary.callId) },
                            onReject = { onReject(primary.callId, null) }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val btnRadius = RoundedCornerShape(config.callerButtonRadiusDp.dp)
                            Button(
                                onClick = { onReject(primary.callId, null) },
                                shape = btnRadius,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.size(width = 136.dp, height = 64.dp).testTag("incall_reject_btn")
                            ) {
                                Icon(Icons.Rounded.CallEnd, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.incall_reject))
                            }
                            Button(
                                onClick = { onAnswer(primary.callId) },
                                shape = btnRadius,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.size(width = 136.dp, height = 64.dp).testTag("incall_answer_btn")
                            ) {
                                Icon(Icons.Rounded.Call, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.incall_answer))
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    AnimatedVisibility(visible = showKeypad) {
                        InCallDtmfPad(
                            onDigitDown = onPlayDtmf,
                            onDigitUp = onStopDtmf
                        )
                    }

                    // 2x3 Active Call Control Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallControlButton(
                            icon = Icons.Rounded.MicOff,
                            label = stringResource(R.string.incall_mute),
                            active = uiState.isMuted,
                            onClick = { onToggleMute(!uiState.isMuted) }
                        )
                        InCallControlButton(
                            icon = Icons.Rounded.Dialpad,
                            label = stringResource(R.string.incall_keypad),
                            active = showKeypad,
                            onClick = { showKeypad = !showKeypad }
                        )
                        InCallControlButton(
                            icon = Icons.Rounded.VolumeUp,
                            label = stringResource(R.string.incall_speaker),
                            active = uiState.activeAudioRoute == AudioRouteType.SPEAKER,
                            onClick = onToggleSpeaker
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallControlButton(
                            icon = if (primary.state == CallStateType.HOLDING) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            label = stringResource(R.string.incall_hold),
                            active = primary.state == CallStateType.HOLDING,
                            enabled = primary.canHold,
                            onClick = { onToggleHold(primary.callId) }
                        )
                        InCallControlButton(
                            icon = when (uiState.activeAudioRoute) {
                                AudioRouteType.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                                AudioRouteType.SPEAKER -> Icons.Rounded.VolumeUp
                                else -> Icons.Rounded.Hearing
                            },
                            label = stringResource(R.string.incall_bluetooth),
                            active = uiState.activeAudioRoute == AudioRouteType.BLUETOOTH,
                            onClick = { showAudioSheet = true }
                        )
                        InCallControlButton(
                            icon = Icons.Rounded.NoteAdd,
                            label = stringResource(R.string.recents_add_note),
                            active = false,
                            onClick = { showNoteDialog = true }
                        )
                    }

                    Button(
                        onClick = { onDisconnect(primary.callId) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier
                            .size(width = 196.dp, height = 62.dp)
                            .testTag("incall_end_btn")
                    ) {
                        Icon(Icons.Rounded.CallEnd, stringResource(R.string.incall_end))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.incall_end), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAudioSheet) {
        ModalBottomSheet(onDismissRequest = { showAudioSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Select Audio Output", style = MaterialTheme.typography.titleLarge)
                uiState.availableAudioEndpoints.forEach { endpoint ->
                    DialMeCard(
                        containerColor = if (endpoint.isSelected) cs.primaryContainer else cs.surfaceContainerLow,
                        onClick = {
                            onSelectAudioEndpoint(endpoint.id)
                            showAudioSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(endpoint.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (endpoint.isSelected) {
                                Text("Active", color = cs.primary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showSmsSheet) {
        ModalBottomSheet(onDismissRequest = { showSmsSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.incall_quick_sms), style = MaterialTheme.typography.titleLarge)
                quickResponses.forEach { qr ->
                    DialMeCard(
                        onClick = {
                            onReject(primary.callId, qr.messageText)
                            showSmsSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = qr.messageText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showNoteDialog) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text(stringResource(R.string.recents_add_note)) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note for ${primary.displayName ?: primary.number}") },
                    minLines = 3
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSaveNote(primary.number, noteText)
                    showNoteDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun CallerIdentityBlock(
    session: ActiveCallSession,
    elapsedSeconds: Long,
    accent: Color
) {
    val pulse = rememberInfiniteTransition(label = "ring_pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (session.state == CallStateType.RINGING) {
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.22f))
                )
            }
            ContactAvatar(
                name = session.displayName ?: session.number,
                photoUri = session.photoUri,
                size = 104.dp
            )
        }
        Text(
            text = session.displayName ?: session.number.ifBlank { "Private Number" },
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        if (!session.displayName.isNullOrBlank() && session.number.isNotBlank()) {
            Text(
                text = session.number,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                color = Color(0xFFCBD5E1)
            )
        }
        if (session.verificationStatus == CallerVerificationStatus.PASSED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Carrier Verified", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
            }
        }
        val stateLabel = when (session.state) {
            CallStateType.RINGING -> stringResource(R.string.incall_incoming)
            CallStateType.DIALING -> stringResource(R.string.incall_dialing)
            CallStateType.CONNECTING -> stringResource(R.string.incall_connecting)
            CallStateType.HOLDING -> stringResource(R.string.incall_on_hold)
            CallStateType.ACTIVE -> DateTimeUtils.formatCallDuration(elapsedSeconds)
            CallStateType.DISCONNECTED, CallStateType.DISCONNECTING -> stringResource(R.string.incall_ended)
            else -> ""
        }
        Text(
            text = stateLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = accent
        )
    }
}

@Composable
private fun SwipeAnswerBar(
    onAnswer: () -> Unit,
    onReject: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val threshold = 160f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = CircleShape,
            color = Color(0x441E293B)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CallEnd, "Decline", tint = Color(0xFFEF4444), modifier = Modifier.size(26.dp))
                    Text(
                        text = stringResource(R.string.incall_swipe_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                    Icon(Icons.Rounded.Call, "Answer", tint = Color(0xFF10B981), modifier = Modifier.size(26.dp))
                }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                offsetX > 40f -> Color(0xFF10B981)
                                offsetX < -40f -> Color(0xFFEF4444)
                                else -> Color.White
                            }
                        )
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount ->
                                    offsetX = (offsetX + dragAmount).coerceIn(-240f, 240f)
                                },
                                onDragEnd = {
                                    when {
                                        offsetX >= threshold -> onAnswer()
                                        offsetX <= -threshold -> onReject()
                                    }
                                    offsetX = 0f
                                },
                                onDragCancel = { offsetX = 0f }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.incall_answer),
                        tint = Color(0xFF0F172A)
                    )
                }
            }
        }
    }
}

@Composable
private fun InCallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(62.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (active) Color.White else Color(0x33FFFFFF),
                contentColor = if (active) Color(0xFF0F172A) else Color.White
            )
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFE2E8F0))
    }
}

@Composable
private fun InCallDtmfPad(
    onDigitDown: (Char) -> Unit,
    onDigitUp: () -> Unit
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )
    DialMeCard(containerColor = Color(0x880F172A), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { ch ->
                        Surface(
                            onClick = {
                                onDigitDown(ch)
                                onDigitUp()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x33FFFFFF)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ch.toString(),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
