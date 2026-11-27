package com.example.feature.dialpad

import com.example.core.designsystem.keyPressEffect
import com.example.core.designsystem.dialKeyShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.PhoneFormatUtils
import com.example.core.datastore.DialpadButtonShape
import com.example.core.datastore.DialpadLayoutStyle
import com.example.core.designsystem.ContactAvatar
import com.example.core.designsystem.DialMeCard
import com.example.core.designsystem.JetBrainsMonoFontFamily
import com.example.core.designsystem.LocalDialMeConfig
import com.example.core.designsystem.RoleDialerBanner
import com.example.feature.common.DialMeMainViewModel

private data class DialKeySpec(
    val digit: String,
    val letters: String,
    val tone: Int
)

private val DIAL_KEYS = listOf(
    listOf(
        DialKeySpec("1", "∞", ToneGenerator.TONE_DTMF_1),
        DialKeySpec("2", "ABC", ToneGenerator.TONE_DTMF_2),
        DialKeySpec("3", "DEF", ToneGenerator.TONE_DTMF_3)
    ),
    listOf(
        DialKeySpec("4", "GHI", ToneGenerator.TONE_DTMF_4),
        DialKeySpec("5", "JKL", ToneGenerator.TONE_DTMF_5),
        DialKeySpec("6", "MNO", ToneGenerator.TONE_DTMF_6)
    ),
    listOf(
        DialKeySpec("7", "PQRS", ToneGenerator.TONE_DTMF_7),
        DialKeySpec("8", "TUV", ToneGenerator.TONE_DTMF_8),
        DialKeySpec("9", "WXYZ", ToneGenerator.TONE_DTMF_9)
    ),
    listOf(
        DialKeySpec("*", "", ToneGenerator.TONE_DTMF_S),
        DialKeySpec("0", "+", ToneGenerator.TONE_DTMF_0),
        DialKeySpec("#", "", ToneGenerator.TONE_DTMF_P)
    )
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialpadScreen(
    viewModel: DialMeMainViewModel,
    onRequestDefaultDialer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val config = LocalDialMeConfig.current
    val cs = MaterialTheme.colorScheme

    val dialInput by viewModel.dialInput.collectAsStateWithLifecycle()
    val t9Matches by viewModel.t9Matches.collectAsStateWithLifecycle()
    val callHistory by viewModel.callHistory.collectAsStateWithLifecycle()
    val speedDials by viewModel.speedDials.collectAsStateWithLifecycle()
    val simAccounts by viewModel.simAccounts.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val clipboardCandidate by viewModel.clipboardCandidate.collectAsStateWithLifecycle()

    var showSpeedDialSheet by remember { mutableStateOf(false) }
    var assignSpeedSlot by remember { mutableStateOf<Int?>(null) }
    var pendingCallAfterPerm by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val callPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshSystemState()
        pendingCallAfterPerm?.let { (num, simId) ->
            pendingCallAfterPerm = null
            if (num.isNotBlank()) {
                viewModel.initiateCall(num, simId)
            }
        }
    }

    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 65) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { toneGenerator?.release() } }
    }

    LaunchedEffect(config.clipboardAutoDetect) {
        if (config.clipboardAutoDetect) {
            val candidate = PhoneFormatUtils.extractPhoneCandidateFromClipboard(clipboard.getText())
            viewModel.updateClipboardCandidate(candidate)
        } else {
            viewModel.updateClipboardCandidate(null)
        }
    }

    val recentSuggestions = remember(callHistory) {
        callHistory.distinctBy { it.normalizedNumber.ifBlank { it.number } }.take(6)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (!permissions.isDefaultDialer) {
            Spacer(Modifier.height(6.dp))
            RoleDialerBanner(onSetDefaultClick = onRequestDefaultDialer)
        }

        // Clipboard detection chip
        AnimatedVisibility(visible = clipboardCandidate != null && dialInput.isEmpty()) {
            clipboardCandidate?.let { candidate ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AssistChip(
                        onClick = {
                            viewModel.setDialInput(candidate)
                            viewModel.updateClipboardCandidate(null)
                        },
                        leadingIcon = { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(16.dp)) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.dialpad_paste_clipboard,
                                    viewModel.formatForDisplay(candidate)
                                )
                            )
                        },
                        modifier = Modifier.testTag("clipboard_paste_chip")
                    )
                }
            }
        }

        // T9 search matches OR Recent number suggestions
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (dialInput.isNotEmpty()) {
                if (t9Matches.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.dialpad_no_t9_matches, dialInput),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AssistChip(
                                onClick = {
                                    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                        type = ContactsContract.RawContacts.CONTENT_TYPE
                                        putExtra(ContactsContract.Intents.Insert.PHONE, dialInput)
                                    }
                                    runCatching { context.startActivity(intent) }
                                },
                                leadingIcon = { Icon(Icons.Rounded.PersonAdd, null, Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.dialpad_add_contact)) }
                            )
                            AssistChip(
                                onClick = {
                                    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$dialInput"))
                                    runCatching { context.startActivity(smsIntent) }
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Message, null, Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.dialpad_send_sms)) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(t9Matches, key = { "${it.lookupKey}_${it.normalizedNumber}" }) { match ->
                            DialMeCard(
                                onClick = { viewModel.initiateCall(match.phoneNumber) },
                                modifier = Modifier.fillMaxWidth().testTag("t9_match_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ContactAvatar(name = match.displayName, photoUri = match.photoUri, size = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = match.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (match.isStarred) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Rounded.Star, null, tint = cs.tertiary, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Text(
                                            text = viewModel.formatForDisplay(match.phoneNumber),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.initiateCall(match.phoneNumber) }) {
                                        Icon(Icons.Rounded.Call, stringResource(R.string.dialpad_call), tint = cs.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Recent suggestions & Speed dial bar
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.dialpad_t9_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showSpeedDialSheet = true }) {
                                Icon(Icons.Rounded.Speed, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.dialpad_speed_dial))
                            }
                        }
                    }
                    items(recentSuggestions, key = { it.id }) { item ->
                        DialMeCard(
                            onClick = { viewModel.setDialInput(item.number) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ContactAvatar(
                                    name = item.cachedName ?: item.number,
                                    photoUri = item.photoUri,
                                    size = 38.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.cachedName ?: viewModel.formatForDisplay(item.number),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = viewModel.formatForDisplay(item.number),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.initiateCall(item.number) }) {
                                    Icon(Icons.Rounded.Call, stringResource(R.string.dialpad_call), tint = cs.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Number display bar (always centered)
        DialMeCard(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (dialInput.isEmpty()) {
                        stringResource(R.string.dialpad_enter_number)
                    } else {
                        viewModel.formatForDisplay(dialInput)
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = when {
                            dialInput.isEmpty() -> 14.sp
                            dialInput.length > 15 -> 19.sp
                            dialInput.length > 11 -> 23.sp
                            else -> 28.sp
                        }
                    ),
                    color = if (dialInput.isEmpty()) cs.onSurfaceVariant.copy(alpha = 0.6f) else cs.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 44.dp)
                        .testTag("dialpad_number_display")
                )
                if (dialInput.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.backspaceDialDigit() },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .combinedClickable(
                                onClick = { viewModel.backspaceDialDigit() },
                                onLongClick = { viewModel.clearDialInput() }
                            )
                            .testTag("dialpad_backspace")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = stringResource(R.string.dialpad_backspace),
                            tint = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Customizable Keypad Grid
        val keypadFraction = when (config.dialpadLayoutStyle) {
            DialpadLayoutStyle.ONE_HANDED_RIGHT, DialpadLayoutStyle.ONE_HANDED_LEFT -> 0.86f
            else -> 1.0f
        }
        val keypadAlign = when (config.dialpadLayoutStyle) {
            DialpadLayoutStyle.ONE_HANDED_RIGHT -> Alignment.End
            DialpadLayoutStyle.ONE_HANDED_LEFT -> Alignment.Start
            else -> Alignment.CenterHorizontally
        }
        val keyHeight = when (config.dialpadLayoutStyle) {
            DialpadLayoutStyle.COMPACT -> (52 * config.dialpadButtonScale).dp
            else -> (60 * config.dialpadButtonScale).dp
        }.coerceIn(48.dp, 78.dp)

        val keyShape = dialKeyShape(config.dialpadButtonShape, config.dialpadCornerRadiusDp)
        val isDarkSurface = cs.background.luminance() < 0.5f
        val glassTint = if (isDarkSurface) Color.White else cs.primary
        val glassAlpha = config.dialpadGlassAlpha.coerceIn(0.05f, 0.6f)
        val glassBrush = Brush.linearGradient(
            colors = listOf(
                glassTint.copy(alpha = (glassAlpha * 1.6f).coerceAtMost(0.9f)),
                glassTint.copy(alpha = glassAlpha * 0.45f)
            )
        )
        val glassBorder = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    glassTint.copy(alpha = if (isDarkSurface) 0.55f else 0.45f),
                    glassTint.copy(alpha = 0.08f)
                )
            )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(keypadFraction)
                .align(keypadAlign),
            verticalArrangement = Arrangement.spacedBy(config.dialpadSpacingDp.coerceIn(4, 16).dp)
        ) {
            DIAL_KEYS.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(config.dialpadSpacingDp.coerceIn(4, 16).dp)
                ) {
                    row.forEach { key ->
                        val speedEntry = key.digit.toIntOrNull()?.let { slot ->
                            speedDials.firstOrNull { it.slot == slot }
                        }
                        val keySource = remember { MutableInteractionSource() }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(keyHeight)
                                .keyPressEffect(
                                    effects = config.keyEffects,
                                    shape = keyShape,
                                    source = keySource,
                                    color = cs.primary,
                                    color2 = cs.secondary,
                                    intensity = config.keyEffectIntensity
                                )
                                .clip(keyShape)
                                .combinedClickable(
                                    interactionSource = keySource,
                                    indication = ripple(),
                                    onClick = {
                                        if (config.dialpadHaptics) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        if (config.dialpadKeySounds) {
                                            runCatching { toneGenerator?.startTone(key.tone, 90) }
                                        }
                                        viewModel.appendDialDigit(key.digit)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        when (key.digit) {
                                            "0" -> viewModel.appendDialDigit("+")
                                            "1" -> viewModel.initiateCall("*86")
                                            in "2".."9" -> {
                                                val slot = key.digit.toInt()
                                                val existing = viewModel.triggerSpeedDial(slot)
                                                if (existing == null) {
                                                    assignSpeedSlot = slot
                                                }
                                            }
                                        }
                                    }
                                )
                                .testTag("dial_key_${key.digit}"),
                            shape = keyShape,
                            color = if (config.dialpadGlass) Color.Transparent
                            else cs.surfaceContainer.copy(alpha = config.cardTransparencyAlpha),
                            border = if (config.dialpadGlass && config.dialpadKeyBorder) glassBorder else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (config.dialpadGlass) Modifier.background(glassBrush) else Modifier),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = key.digit,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 23.sp
                                    ),
                                    color = cs.onSurface
                                )
                                val subText = speedEntry?.displayName?.take(6) ?: (if (config.showDialpadLetters) key.letters else "")
                                if (subText.isNotEmpty()) {
                                    Text(
                                        text = subText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (speedEntry != null) cs.primary else cs.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!permissions.hasReadPhoneState || !permissions.hasCallPhone) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                AssistChip(
                    onClick = {
                        callPermLauncher.launch(
                            arrayOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_PHONE_STATE
                            )
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.SimCard, null, Modifier.size(16.dp)) },
                    label = { Text(stringResource(R.string.dialpad_enable_dual_sim)) },
                    modifier = Modifier.testTag("enable_dual_sim_chip")
                )
            }
        }

        // Call Action Row (Single or Dual SIM buttons)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (simAccounts.size >= 2) {
                simAccounts.take(2).forEachIndexed { idx, sim ->
                    if (idx > 0) Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val target = dialInput.ifBlank {
                                recentSuggestions.firstOrNull()?.number.orEmpty().also {
                                    if (it.isNotEmpty()) viewModel.setDialInput(it)
                                }
                            }
                            if (target.isNotEmpty()) {
                                if (!permissions.hasCallPhone) {
                                    pendingCallAfterPerm = target to sim.id
                                    callPermLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.CALL_PHONE,
                                            Manifest.permission.READ_PHONE_STATE
                                        )
                                    )
                                } else {
                                    viewModel.initiateCall(target, sim.id)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("call_sim_${sim.slotIndex}_button"),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (idx == 0) Color(0xFF10B981) else cs.secondary
                        )
                    ) {
                        Icon(Icons.Rounded.SimCard, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "SIM ${sim.slotIndex} · ${sim.carrierName.take(8)}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (dialInput.isNotEmpty()) {
                            if (!permissions.hasCallPhone || !permissions.hasReadPhoneState) {
                                pendingCallAfterPerm = dialInput to null
                                callPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CALL_PHONE,
                                        Manifest.permission.READ_PHONE_STATE
                                    )
                                )
                            } else {
                                viewModel.initiateCall(dialInput)
                            }
                        } else {
                            recentSuggestions.firstOrNull()?.let { viewModel.setDialInput(it.number) }
                        }
                    },
                    modifier = Modifier
                        .width(180.dp)
                        .height(56.dp)
                        .testTag("call_primary_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.dialpad_call))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dialpad_call), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Speed Dial Manager Sheet
    if (showSpeedDialSheet) {
        ModalBottomSheet(onDismissRequest = { showSpeedDialSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.dialpad_speed_dial), style = MaterialTheme.typography.titleLarge)
                (2..9).forEach { slot ->
                    val entry = speedDials.firstOrNull { it.slot == slot }
                    DialMeCard(
                        onClick = { assignSpeedSlot = slot },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(cs.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$slot", fontWeight = FontWeight.Bold, color = cs.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry?.displayName ?: "Empty slot (tap to assign)", style = MaterialTheme.typography.titleMedium)
                                if (entry != null) {
                                    Text(entry.phoneNumber, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                }
                            }
                            if (entry != null) {
                                TextButton(onClick = { viewModel.clearSpeedDial(slot) }) {
                                    Text(stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Assign Speed Dial Dialog
    assignSpeedSlot?.let { slot ->
        var name by remember { mutableStateOf("") }
        var number by remember { mutableStateOf(dialInput) }
        AlertDialog(
            onDismissRequest = { assignSpeedSlot = null },
            title = { Text(stringResource(R.string.ui_assign_speed_dial, slot)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.ui_label_name)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text(stringResource(R.string.ui_phone_number)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (number.isNotBlank()) {
                            viewModel.saveSpeedDial(slot, name, number)
                            assignSpeedSlot = null
                        }
                    }
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { assignSpeedSlot = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
