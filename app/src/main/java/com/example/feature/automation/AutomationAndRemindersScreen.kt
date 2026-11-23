package com.example.feature.automation

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.DateTimeUtils
import com.example.core.designsystem.DialMeCard
import com.example.domain.model.AutomationAction
import com.example.domain.model.AutomationRule
import com.example.domain.model.AutomationTrigger
import com.example.feature.common.DialMeMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationAndRemindersScreen(
    viewModel: DialMeMainViewModel,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val rules by viewModel.automationRules.collectAsStateWithLifecycle()
    val quickResponses by viewModel.quickResponses.collectAsStateWithLifecycle()

    var showAddReminder by remember { mutableStateOf(false) }
    var showAddRule by remember { mutableStateOf(false) }
    var showAddQuickSms by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_automation)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Callback Reminders
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = cs.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Call Reminders (${reminders.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = { showAddReminder = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }

            if (reminders.isEmpty()) {
                item {
                    DialMeCard {
                        Text(
                            "No scheduled callback reminders. Schedule one from here or from any Contact / Recent Call.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(reminders, key = { it.id }) { rem ->
                    DialMeCard {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rem.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${rem.phoneNumber} · ${DateTimeUtils.formatFullDateTime(rem.triggerAtMillis)} (${rem.status.name})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                                if (rem.note.isNotBlank()) {
                                    Text(rem.note, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            IconButton(onClick = { viewModel.deleteReminder(rem.id) }) {
                                Icon(Icons.Rounded.Delete, "Delete reminder", tint = cs.error)
                            }
                        }
                    }
                }
            }

            // Automation Rules
            item {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = cs.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Local Automation Rules (${rules.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = { showAddRule = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rule")
                    }
                }
            }

            items(rules, key = { it.id }) { rule ->
                DialMeCard {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${rule.trigger.name.replace('_', ' ')} -> ${rule.action.name.replace('_', ' ')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Switch(checked = rule.isEnabled, onCheckedChange = { viewModel.toggleAutomationRule(rule) })
                        IconButton(onClick = { viewModel.deleteAutomationRule(rule.id) }) {
                            Icon(Icons.Rounded.Delete, "Delete rule", tint = cs.error)
                        }
                    }
                }
            }

            // Quick SMS Responses
            item {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quick SMS Reject Templates", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showAddQuickSms = true }) { Text("Add") }
                }
            }

            items(quickResponses, key = { it.id }) { qr ->
                DialMeCard {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(qr.messageText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (!qr.isBuiltIn) {
                            IconButton(onClick = { viewModel.deleteQuickResponse(qr.id) }) {
                                Icon(Icons.Rounded.Delete, "Delete template", tint = cs.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddReminder) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var mins by remember { mutableStateOf("15") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddReminder = false },
            title = { Text("Schedule Call Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Contact Name") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true)
                    OutlinedTextField(
                        value = mins,
                        onValueChange = { mins = it.filter { c -> c.isDigit() } },
                        label = { Text("Delay (minutes)") },
                        singleLine = true
                    )
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (phone.isNotBlank()) {
                        viewModel.scheduleReminder(
                            name.ifBlank { phone },
                            phone,
                            note,
                            mins.toLongOrNull()?.coerceAtLeast(1L) ?: 15L
                        )
                        showAddReminder = false
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddReminder = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showAddRule) {
        var name by remember { mutableStateOf("") }
        var trigger by remember { mutableStateOf(AutomationTrigger.MISSED_FAVORITE_CALL) }
        var action by remember { mutableStateOf(AutomationAction.CREATE_CALLBACK_REMINDER) }
        AlertDialog(
            onDismissRequest = { showAddRule = false },
            title = { Text("New Automation Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Rule Name") }, singleLine = true)
                    Text("Trigger", style = MaterialTheme.typography.labelLarge)
                    AutomationTrigger.entries.forEach { t ->
                        FilterChip(
                            selected = trigger == t,
                            onClick = { trigger = t },
                            label = { Text(t.name.replace('_', ' ')) }
                        )
                    }
                    Text("Action", style = MaterialTheme.typography.labelLarge)
                    AutomationAction.entries.forEach { a ->
                        FilterChip(
                            selected = action == a,
                            onClick = { action = a },
                            label = { Text(a.name.replace('_', ' ')) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addAutomationRule(
                        AutomationRule(
                            name = name.ifBlank { trigger.name.replace('_', ' ') },
                            trigger = trigger,
                            action = action
                        )
                    )
                    showAddRule = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddRule = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showAddQuickSms) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddQuickSms = false },
            title = { Text("Add Quick SMS Response") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Message") })
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addQuickResponse(text)
                    showAddQuickSms = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddQuickSms = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
