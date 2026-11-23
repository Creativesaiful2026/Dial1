package com.example.feature.callhistory

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
                    Text("${multiSelectIds.size} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
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
                    placeholder = { Text("Search recents") },
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
            val grouped = remember(history) {
                history.groupBy { DateTimeUtils.formatDateBucket(it.timestampMillis) }
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
                        val isSelected = multiSelectIds.contains(entry.id)
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
                                                multiSelectIds - entry.id
                                            } else {
                                                multiSelectIds + entry.id
                                            }
                                        } else {
                                            selectedEntry = entry
                                        }
                                    },
                                    onLongClick = {
                                        multiSelectIds = multiSelectIds + entry.id
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
                                        if (entry.hasNote) {
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
        ModalBottomSheet(onDismissRequest = { selectedEntry = null }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
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

                if (matchingNotes.isNotEmpty()) {
                    DialMeCard {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Call Notes", style = MaterialTheme.typography.labelLarge, color = cs.primary)
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
                    Text("Call ${viewModel.formatForDisplay(entry.number)}")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { noteEntry = entry }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NoteAdd, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Note")
                    }
                    OutlinedButton(onClick = { reminderEntry = entry }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remind")
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
                        viewModel.deleteCallLogs(setOf(entry.id))
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
                    label = { Text("Note for ${entry.cachedName ?: entry.number}") },
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
                        label = { Text("Remind in (minutes)") },
                        singleLine = true
                    )
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") })
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
