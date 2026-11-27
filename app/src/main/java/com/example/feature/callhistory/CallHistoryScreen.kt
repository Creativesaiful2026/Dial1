package com.example.feature.callhistory

import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.clickable
import androidx.compose.runtime.DisposableEffect
import com.example.core.telecom.CallRecorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.DateTimeUtils
import com.example.core.designsystem.ContactAvatar
import com.example.core.designsystem.DialMeCard
import com.example.core.designsystem.LocalDialMeConfig
import com.example.core.designsystem.PermissionCalloutCard
import com.example.core.permissions.PermissionHelper
import com.example.domain.model.BlockRule
import com.example.domain.model.BlockRuleType
import com.example.domain.model.CallDirection
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallTypeFilter
import com.example.feature.common.DialMeMainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CallHistoryScreen(
    viewModel: DialMeMainViewModel,
    onRequestCallLogPermission: () -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalDialMeConfig.current
    val cs = MaterialTheme.colorScheme

    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val history by viewModel.filteredCallHistory.collectAsStateWithLifecycle()
    val filter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearch.collectAsStateWithLifecycle()
    val notes by viewModel.callNotes.collectAsStateWithLifecycle()
    val allHistory by viewModel.callHistory.collectAsStateWithLifecycle()

    var selectedEntry by remember { mutableStateOf<CallLogItem?>(null) }
    var multiSelectIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var noteEntry by remember { mutableStateOf<CallLogItem?>(null) }
    var reminderEntry by remember { mutableStateOf<CallLogItem?>(null) }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportCallHistoryCsv(it) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        if (!permissions.hasReadCallLog) {
            PermissionCalloutCard(
                title = stringResource(R.string.permissions_needed_title),
                description = stringResource(R.string.permissions_call_log_desc),
                onGrantClick = onRequestCallLogPermission,
                onSettingsClick = { context.startActivity(PermissionHelper.buildAppSettingsIntent(context)) }
            )
            return@Column
        }

        // Top search + stats + export bar (or multi-select bar)
        if (multiSelectIds.isNotEmpty()) {
            DialMeCard(containerColor = cs.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { multiSelectIds = emptySet() }) {
                        Icon(Icons.Rounded.Close, "Cancel selection")
                    }
                    Text(stringResource(R.string.ui_selected, multiSelectIds.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        viewModel.deleteCallLogs(multiSelectIds)
                        multiSelectIds = emptySet()
                    }) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = cs.error)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_delete), color = cs.error)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setHistorySearch,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(stringResource(R.string.ui_search_recents)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("history_search_field")
                )
                IconButton(onClick = onOpenStatistics, modifier = Modifier.testTag("open_stats_button")) {
                    Icon(Icons.Rounded.BarChart, "Call Statistics", tint = cs.primary)
                }
                IconButton(onClick = { exportCsvLauncher.launch("dialme_call_history.csv") }) {
                    Icon(Icons.Rounded.Download, stringResource(R.string.recents_export))
                }
            }
        }

        // Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                CallTypeFilter.ALL to R.string.recents_filter_all,
                CallTypeFilter.INCOMING to R.string.recents_filter_incoming,
                CallTypeFilter.OUTGOING to R.string.recents_filter_outgoing,
                CallTypeFilter.MISSED to R.string.recents_filter_missed,
                CallTypeFilter.REJECTED to R.string.recents_filter_rejected
            )
            items(filters, key = { it.first.name }) { (type, labelRes) ->
                FilterChip(
                    selected = filter == type,
                    onClick = { viewModel.setHistoryFilter(type) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.recents_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant
                )
            }
        } else {
            // One row per number: every call (incoming, outgoing, missed, declined) of the
            // same number is kept together; the newest call represents the group.
            val groupMembers = remember(history, config.groupRecentsByNumber) {
                history.groupBy { numberKey(it) }
            }
            val displayList = remember(history, config.groupRecentsByNumber) {
                if (config.groupRecentsByNumber) {
                    groupMembers.values
                        .mapNotNull { list -> list.maxByOrNull { it.timestampMillis } }
                        .sortedByDescending { it.timestampMillis }
                } else {
                    history
                }
            }
            val grouped = remember(displayList) {
                displayList.groupBy { DateTimeUtils.formatDateBucket(it.timestampMillis) }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                grouped.forEach { (dateLabel, entries) ->
                    item(key = "hdr_$dateLabel") {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.primary,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        val members = if (config.groupRecentsByNumber) {
                            groupMembers[numberKey(entry)].orEmpty().ifEmpty { listOf(entry) }
                        } else {
                            listOf(entry)
                        }
                        val memberIds = members.map { it.id }.toSet()
                        val isSelected = memberIds.all { multiSelectIds.contains(it) }
                        val displayName = if (config.privacyModeEnabled) {
                            "Private Entry"
                        } else {
                            entry.cachedName ?: viewModel.formatForDisplay(entry.number).ifBlank { "Unknown" }
                        }
                        val subNumber = if (config.privacyModeEnabled) {
                            "••••••"
                        } else {
                            viewModel.formatForDisplay(entry.number)
                        }

                        DialMeCard(
                            containerColor = if (isSelected) cs.primaryContainer else cs.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (multiSelectIds.isNotEmpty()) {
                                            multiSelectIds = if (isSelected) {
                                                multiSelectIds - memberIds
                                            } else {
                                                multiSelectIds + memberIds
                                            }
                                        } else {
                                            selectedEntry = entry
                                        }
                                    },
                                    onLongClick = {
                                        multiSelectIds = multiSelectIds + memberIds
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = cs.primary, modifier = Modifier.size(40.dp))
                                } else {
                                    ContactAvatar(name = displayName, photoUri = entry.photoUri, size = 40.dp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (entry.direction == CallDirection.MISSED) cs.error else cs.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (members.size > 1) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "(${members.size})",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = cs.primary
                                            )
                                        }
                                        if (members.any { it.hasNote }) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Rounded.Description, "Has note", tint = cs.primary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val (dirIcon, dirTint) = when (entry.direction) {
                                            CallDirection.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived to Color(0xFF10B981)
                                            CallDirection.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade to cs.primary
                                            CallDirection.MISSED -> Icons.AutoMirrored.Rounded.CallMissed to cs.error
                                            CallDirection.REJECTED, CallDirection.BLOCKED -> Icons.Rounded.Block to Color(0xFFF59E0B)
                                            CallDirection.VOICEMAIL -> Icons.Rounded.Call to cs.secondary
                                        }
                                        Icon(dirIcon, null, tint = dirTint, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "$subNumber · ${DateTimeUtils.formatTimeShort(entry.timestampMillis)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (members.size > 1) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            val inCount = members.count { it.direction == CallDirection.INCOMING }
                                            val outCount = members.count { it.direction == CallDirection.OUTGOING }
                                            val missCount = members.count { it.direction == CallDirection.MISSED }
                                            val rejCount = members.count {
                                                it.direction == CallDirection.REJECTED || it.direction == CallDirection.BLOCKED
                                            }
                                            if (inCount > 0) DirectionCount(Icons.AutoMirrored.Rounded.CallReceived, Color(0xFF10B981), inCount)
                                            if (outCount > 0) DirectionCount(Icons.AutoMirrored.Rounded.CallMade, cs.primary, outCount)
                                            if (missCount > 0) DirectionCount(Icons.AutoMirrored.Rounded.CallMissed, cs.error, missCount)
                                            if (rejCount > 0) DirectionCount(Icons.Rounded.Block, Color(0xFFF59E0B), rejCount)
                                        }
                                    }
                                }
                                if (entry.durationSeconds > 0) {
                                    Text(
                                        text = DateTimeUtils.formatDurationReadable(entry.durationSeconds),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.initiateCall(entry.number) }) {
                                    Icon(Icons.Rounded.Call, stringResource(R.string.dialpad_call), tint = cs.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Call Entry Detail Sheet
    selectedEntry?.let { entry ->
        val matchingNotes = notes.filter { it.normalizedNumber == entry.normalizedNumber }
        val callsForNumber = remember(allHistory, entry.id, config.groupRecentsByNumber) {
            if (config.groupRecentsByNumber) {
                allHistory.filter { numberKey(it) == numberKey(entry) }
                    .sortedByDescending { it.timestampMillis }
                    .ifEmpty { listOf(entry) }
            } else {
                listOf(entry)
            }
        }
        ModalBottomSheet(onDismissRequest = { selectedEntry = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContactAvatar(
                        name = entry.cachedName ?: entry.number,
                        photoUri = entry.photoUri,
                        size = 50.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.cachedName ?: viewModel.formatForDisplay(entry.number), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${entry.direction.name} · ${DateTimeUtils.formatFullDateTime(entry.timestampMillis)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        if (entry.durationSeconds > 0) {
                            Text(
                                "Duration: ${DateTimeUtils.formatDurationReadable(entry.durationSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.primary
                            )
                        }
                    }
                }

                if (callsForNumber.size > 1) {
                    var callsExpanded by remember(entry.id) { mutableStateOf(false) }
                    DialMeCard {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { callsExpanded = !callsExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.ui_calls, callsForNumber.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = cs.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (callsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = cs.primary
                                )
                            }
                            if (callsExpanded) callsForNumber.forEach { call ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (icon, tint, labelRes) = when (call.direction) {
                                        CallDirection.INCOMING -> Triple(Icons.AutoMirrored.Rounded.CallReceived, Color(0xFF10B981), R.string.recents_filter_incoming)
                                        CallDirection.OUTGOING -> Triple(Icons.AutoMirrored.Rounded.CallMade, cs.primary, R.string.recents_filter_outgoing)
                                        CallDirection.MISSED -> Triple(Icons.AutoMirrored.Rounded.CallMissed, cs.error, R.string.recents_filter_missed)
                                        CallDirection.REJECTED, CallDirection.BLOCKED -> Triple(Icons.Rounded.Block, Color(0xFFF59E0B), R.string.recents_filter_rejected)
                                        CallDirection.VOICEMAIL -> Triple(Icons.Rounded.Call, cs.secondary, R.string.direction_voicemail)
                                    }
                                    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            DateTimeUtils.formatFullDateTime(call.timestampMillis),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                    if (call.durationSeconds > 0) {
                                        Text(
                                            DateTimeUtils.formatDurationReadable(call.durationSeconds),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                RecordingsSection(entry.normalizedNumber.ifBlank { entry.number })

                if (matchingNotes.isNotEmpty()) {
                    DialMeCard {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.ui_call_notes), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                            matchingNotes.forEach { n ->
                                Text("• ${n.noteText}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.initiateCall(entry.number)
                        selectedEntry = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Call, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_call, viewModel.formatForDisplay(entry.number)))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { noteEntry = entry }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NoteAdd, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_note))
                    }
                    OutlinedButton(onClick = { reminderEntry = entry }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_remind))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (entry.cachedName == null && entry.number.isNotBlank()) {
                        TextButton(onClick = {
                            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, entry.number)
                            }
                            runCatching { context.startActivity(intent) }
                        }) {
                            Icon(Icons.Rounded.PersonAdd, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.dialpad_add_contact))
                        }
                    }
                    TextButton(onClick = {
                        viewModel.addBlockRule(
                            BlockRule(
                                ruleType = BlockRuleType.EXACT,
                                pattern = entry.number,
                                label = entry.cachedName ?: entry.number
                            )
                        )
                        selectedEntry = null
                    }) {
                        Icon(Icons.Rounded.Block, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_block))
                    }
                    TextButton(onClick = {
                        viewModel.deleteCallLogs(callsForNumber.map { it.id }.toSet())
                        selectedEntry = null
                    }) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = cs.error)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_delete), color = cs.error)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Call Note Dialog
    noteEntry?.let { entry ->
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteEntry = null },
            title = { Text(stringResource(R.string.recents_add_note)) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.ui_note_for, entry.cachedName ?: entry.number)) },
                    minLines = 3
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveCallNote(entry.number, noteText, entry.id, entry.contactLookupKey)
                    noteEntry = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { noteEntry = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Call Reminder Dialog
    reminderEntry?.let { entry ->
        var delayMins by remember { mutableStateOf("15") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reminderEntry = null },
            title = { Text(stringResource(R.string.recents_add_reminder)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = delayMins,
                        onValueChange = { delayMins = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.ui_remind_in_minutes)) },
                        singleLine = true
                    )
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.ui_note)) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val mins = delayMins.toLongOrNull()?.coerceAtLeast(1L) ?: 15L
                    viewModel.scheduleReminder(entry.cachedName ?: entry.number, entry.number, note, mins)
                    reminderEntry = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { reminderEntry = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}


private fun numberKey(item: CallLogItem): String =
    item.normalizedNumber.ifBlank { item.number }.ifBlank { "unknown_${item.id}" }

@Composable
private fun DirectionCount(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
private fun RecordingsSection(number: String) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var files by remember(number) { mutableStateOf(CallRecorder.recordingsFor(context, number)) }
    var playingPath by remember { mutableStateOf<String?>(null) }
    val playerHolder = remember { arrayOfNulls<android.media.MediaPlayer>(1) }
    DisposableEffect(Unit) {
        onDispose {
            playerHolder[0]?.release()
            playerHolder[0] = null
        }
    }
    if (files.isEmpty()) return

    DialMeCard {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.recordings_title, files.size),
                style = MaterialTheme.typography.labelLarge,
                color = cs.primary
            )
            files.forEach { f ->
                val isPlaying = playingPath == f.absolutePath
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        playerHolder[0]?.release()
                        playerHolder[0] = null
                        if (isPlaying) {
                            playingPath = null
                        } else {
                            runCatching {
                                val mp = android.media.MediaPlayer()
                                mp.setDataSource(f.absolutePath)
                                mp.prepare()
                                mp.setOnCompletionListener { playingPath = null }
                                mp.start()
                                playerHolder[0] = mp
                                playingPath = f.absolutePath
                            }.onFailure { playingPath = null }
                        }
                    }) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = cs.primary
                        )
                    }
                    val ts = f.name.removePrefix("rec_").substringBefore('_').toLongOrNull() ?: f.lastModified()
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            DateTimeUtils.formatFullDateTime(ts),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(
                                if (f.name.contains("_call_")) R.string.recording_src_call else R.string.recording_src_mic
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        if (isPlaying) {
                            playerHolder[0]?.release()
                            playerHolder[0] = null
                            playingPath = null
                        }
                        f.delete()
                        files = files - f
                    }) {
                        Icon(Icons.Rounded.Delete, null, tint = cs.error)
                    }
                }
            }
        }
    }
}
