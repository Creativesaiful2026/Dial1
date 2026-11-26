package com.example.feature.callblocking

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.domain.model.BlockAction
import com.example.domain.model.BlockRule
import com.example.domain.model.BlockRuleType
import com.example.feature.common.DialMeMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingAndPrivacyScreen(
    viewModel: DialMeMainViewModel,
    initialTab: Int = 0,
    onRequestCallScreeningRole: () -> Unit,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val config by viewModel.config.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val rules by viewModel.blockRules.collectAsStateWithLifecycle()
    val blockedEvents by viewModel.blockedEvents.collectAsStateWithLifecycle()
    val privateContacts by viewModel.privateContacts.collectAsStateWithLifecycle()

    var selectedSection by remember { mutableStateOf(initialTab) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showAddPrivateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_privacy)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = selectedSection == 0, onClick = { selectedSection = 0 }, label = { Text(stringResource(R.string.ui_block_rules)) })
            FilterChip(selected = selectedSection == 1, onClick = { selectedSection = 1 }, label = { Text(stringResource(R.string.ui_blocked_log)) })
            FilterChip(selected = selectedSection == 2, onClick = { selectedSection = 2 }, label = { Text(stringResource(R.string.ui_private_vault)) })
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedSection == 0) {
                item {
                    DialMeCard(containerColor = cs.primaryContainer) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Shield, null, tint = cs.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (permissions.isCallScreeningHeld) {
                                        "Call Screening Role Active"
                                    } else {
                                        "Call Screening Role Required for Prefix/Regex Blocking"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "Exact numbers sync with Android's BlockedNumberContract when DialMe is Default Phone App. Prefix, Regex, Unknown/Hidden, and Non-Contact screening require Android's Call Screening role.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant
                            )
                            if (!permissions.hasReadContacts && rules.any { it.ruleType == BlockRuleType.NON_CONTACT && it.isEnabled }) {
                                Text(
                                    stringResource(R.string.block_non_contact_needs_permission),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.error
                                )
                            }
                            if (!permissions.isCallScreeningHeld) {
                                Button(onClick = onRequestCallScreeningRole) {
                                    Text(stringResource(R.string.role_screening_cta))
                                }
                            }
                        }
                    }
                }

                item {
                    DialMeCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.ui_privacy_mode), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.ui_mask_caller_names_numbers_in_recents), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                }
                                Switch(
                                    checked = config.privacyModeEnabled,
                                    onCheckedChange = { v -> viewModel.updateConfig { it.copy(privacyModeEnabled = v) } }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.ui_biometric_app_lock), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.ui_require_fingerprint_pin_when_opening), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                }
                                Switch(
                                    checked = config.appLockEnabled,
                                    onCheckedChange = { v -> viewModel.updateConfig { it.copy(appLockEnabled = v) } }
                                )
                            }
                            if (config.appLockEnabled) {
                                Text(stringResource(R.string.app_lock_timeout_label), style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0 to R.string.lock_timeout_immediately, 30 to R.string.lock_timeout_30s, 60 to R.string.lock_timeout_1m, 300 to R.string.lock_timeout_5m).forEach { (sec, label) ->
                                        FilterChip(
                                            selected = config.appLockTimeoutSec == sec,
                                            onClick = { viewModel.updateConfig { it.copy(appLockTimeoutSec = sec) } },
                                            label = { Text(stringResource(label)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.ui_active_block_rules, rules.size), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { showAddRuleDialog = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.ui_add_rule))
                        }
                    }
                }

                items(rules, key = { it.id }) { rule ->
                    DialMeCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Block, null, tint = cs.error)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.label.ifBlank { rule.pattern.ifBlank { rule.ruleType.name } }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${rule.ruleType.name} · ${rule.pattern.ifBlank { "All matching" }} · ${rule.action.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { viewModel.toggleBlockRule(rule) }
                            )
                            IconButton(onClick = { viewModel.deleteBlockRule(rule) }) {
                                Icon(Icons.Rounded.Delete, "Delete rule", tint = cs.error)
                            }
                        }
                    }
                }
            } else if (selectedSection == 1) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.ui_screened_blocked_calls, blockedEvents.size), style = MaterialTheme.typography.titleMedium)
                        if (blockedEvents.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearBlockedEvents() }) { Text(stringResource(R.string.ui_clear_log)) }
                        }
                    }
                }
                items(blockedEvents, key = { it.id }) { ev ->
                    DialMeCard {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ev.phoneNumber, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${ev.reason} · ${ev.actionTaken.name} · ${DateTimeUtils.formatFullDateTime(ev.timestamp)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    DialMeCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Lock, null, tint = cs.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ui_private_contact_vault), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Button(onClick = { showAddPrivateDialog = true }) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.ui_add))
                                }
                            }
                            Text(
                                text = "Stored exclusively inside DialMe's local database—never written to Android's shared ContactsContract. Note: Android carrier/Telecom call logs remain visible to system Call Log readers unless cleared.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                items(privateContacts, key = { it.id }) { pc ->
                    DialMeCard {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pc.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(pc.phoneNumber, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                if (pc.notes.isNotBlank()) {
                                    Text(pc.notes, style = MaterialTheme.typography.labelSmall, color = cs.primary)
                                }
                            }
                            IconButton(onClick = { viewModel.initiateCall(pc.phoneNumber) }) {
                                Icon(Icons.Rounded.Call, "Call", tint = cs.primary)
                            }
                            IconButton(onClick = { viewModel.deletePrivateContact(pc.id) }) {
                                Icon(Icons.Rounded.Delete, "Delete", tint = cs.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        var selectedType by remember { mutableStateOf(BlockRuleType.EXACT) }
        var selectedAction by remember { mutableStateOf(BlockAction.REJECT) }
        var pattern by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text(stringResource(R.string.ui_add_block_rule)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BlockRuleType.entries) { t ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = { selectedType = t },
                                label = { Text(t.name) }
                            )
                        }
                    }
                    if (selectedType != BlockRuleType.UNKNOWN_HIDDEN && selectedType != BlockRuleType.NON_CONTACT) {
                        OutlinedTextField(
                            value = pattern,
                            onValueChange = { pattern = it },
                            label = { Text(if (selectedType == BlockRuleType.PREFIX) "Prefix (e.g. +1800)" else "Number or Regex") },
                            singleLine = true
                        )
                    }
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.ui_label_optional)) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BlockAction.entries.forEach { a ->
                            FilterChip(
                                selected = selectedAction == a,
                                onClick = { selectedAction = a },
                                label = { Text(a.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val needsPattern = selectedType != BlockRuleType.UNKNOWN_HIDDEN && selectedType != BlockRuleType.NON_CONTACT
                    if (!needsPattern || pattern.isNotBlank()) {
                        viewModel.addBlockRule(
                            BlockRule(
                                ruleType = selectedType,
                                pattern = pattern.trim(),
                                label = label.trim(),
                                action = selectedAction
                            )
                        )
                        showAddRuleDialog = false
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showAddPrivateDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPrivateDialog = false },
            title = { Text(stringResource(R.string.ui_add_private_contact)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ui_name)) }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.ui_phone_number)) }, singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.ui_notes)) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        viewModel.addPrivateContact(name, phone, notes)
                        showAddPrivateDialog = false
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddPrivateDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
