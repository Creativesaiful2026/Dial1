package com.example.feature.contacts

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.datastore.ContactViewMode
import com.example.core.designsystem.ContactAvatar
import com.example.core.designsystem.DialMeCard
import com.example.core.designsystem.LocalDialMeConfig
import com.example.core.designsystem.PermissionCalloutCard
import com.example.core.permissions.PermissionHelper
import com.example.domain.model.BlockRule
import com.example.domain.model.BlockRuleType
import com.example.domain.model.ContactItem
import com.example.domain.model.DuplicateGroup
import com.example.feature.common.DialMeMainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    viewModel: DialMeMainViewModel,
    favoritesOnly: Boolean,
    onRequestContactsPermission: () -> Unit,
    onOpenPrivateVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalDialMeConfig.current
    val cs = MaterialTheme.colorScheme

    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val contacts by if (favoritesOnly) {
        viewModel.favoriteContacts.collectAsStateWithLifecycle()
    } else {
        viewModel.filteredContacts.collectAsStateWithLifecycle()
    }
    val groups by viewModel.contactGroups.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val searchQuery by viewModel.contactSearchQuery.collectAsStateWithLifecycle()

    var selectedContact by remember { mutableStateOf<ContactItem?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>?>(null) }
    var noteContact by remember { mutableStateOf<ContactItem?>(null) }
    var reminderContact by remember { mutableStateOf<ContactItem?>(null) }
    var ringtoneTargetContactId by remember { mutableStateOf<Long?>(null) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            ringtoneTargetContactId?.let { cid ->
                viewModel.setCustomRingtone(cid, uri?.toString())
            }
        }
        ringtoneTargetContactId = null
    }

    val exportVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri -> uri?.let { viewModel.exportContactsVcf(it) } }

    val importVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importContactsVcf(it) } }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (!permissions.hasReadContacts) {
                PermissionCalloutCard(
                    title = stringResource(R.string.permissions_needed_title),
                    description = stringResource(R.string.permissions_contacts_desc),
                    onGrantClick = onRequestContactsPermission,
                    onSettingsClick = { context.startActivity(PermissionHelper.buildAppSettingsIntent(context)) }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenPrivateVault,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.contacts_private_vault))
                }
                return@Column
            }

            // Search + actions bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setContactSearch,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("contacts_search_field")
                )
                IconButton(
                    onClick = {
                        viewModel.updateConfig {
                            val next = if (it.contactViewMode == ContactViewMode.LIST) {
                                ContactViewMode.GRID
                            } else {
                                ContactViewMode.LIST
                            }
                            it.copy(contactViewMode = next)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (config.contactViewMode == ContactViewMode.LIST) {
                            Icons.Rounded.GridView
                        } else {
                            Icons.Rounded.ViewList
                        },
                        contentDescription = "Toggle list or grid view"
                    )
                }
                if (!favoritesOnly) {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More contact tools")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.contacts_private_vault)) },
                                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenPrivateVault()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.contacts_duplicates)) },
                                leadingIcon = { Icon(Icons.Rounded.MergeType, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    duplicateGroups = viewModel.findDuplicateGroups()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.contacts_export_vcf)) },
                                leadingIcon = { Icon(Icons.Rounded.Upload, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    exportVcfLauncher.launch("dialme_contacts.vcf")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.contacts_import_vcf)) },
                                leadingIcon = { Icon(Icons.Rounded.Download, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    importVcfLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "*/*"))
                                }
                            )
                        }
                    }
                }
            }

            // Group chips on full Contacts tab
            if (!favoritesOnly && groups.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedGroup == null,
                            onClick = { viewModel.setSelectedGroup(null) },
                            label = { Text(stringResource(R.string.ui_all, contacts.size)) }
                        )
                    }
                    items(groups, key = { it.id }) { group ->
                        FilterChip(
                            selected = selectedGroup == group.title,
                            onClick = {
                                viewModel.setSelectedGroup(
                                    if (selectedGroup == group.title) null else group.title
                                )
                            },
                            label = { Text("${group.title} (${group.memberCount})") }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }

            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (favoritesOnly) {
                            stringResource(R.string.contacts_favorites_empty)
                        } else {
                            stringResource(R.string.contacts_empty)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (config.contactViewMode == ContactViewMode.GRID || favoritesOnly) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(contacts, key = { it.contactId }) { contact ->
                        DialMeCard(
                            onClick = { selectedContact = contact },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ContactAvatar(name = contact.displayName, photoUri = contact.photoUri, size = 56.dp)
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = viewModel.formatForDisplay(contact.primaryNumber),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { viewModel.initiateCall(contact.primaryNumber) }) {
                                        Icon(Icons.Rounded.Call, stringResource(R.string.dialpad_call), tint = cs.primary)
                                    }
                                    IconButton(onClick = { viewModel.toggleFavorite(contact) }) {
                                        Icon(
                                            if (contact.isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            "Favorite",
                                            tint = if (contact.isStarred) cs.tertiary else cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(contacts, key = { it.contactId }) { contact ->
                        DialMeCard(
                            onClick = { selectedContact = contact },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ContactAvatar(name = contact.displayName, photoUri = contact.photoUri, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = viewModel.formatForDisplay(contact.primaryNumber),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(contact) }) {
                                    Icon(
                                        imageVector = if (contact.isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (contact.isStarred) cs.tertiary else cs.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.initiateCall(contact.primaryNumber) }) {
                                    Icon(Icons.Rounded.Call, stringResource(R.string.dialpad_call), tint = cs.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!favoritesOnly && permissions.hasReadContacts) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_contact_fab"),
                containerColor = cs.primary
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = stringResource(R.string.contacts_new))
            }
        }
    }

    // Contact Details Bottom Sheet
    selectedContact?.let { contact ->
        ModalBottomSheet(onDismissRequest = { selectedContact = null }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContactAvatar(name = contact.displayName, photoUri = contact.photoUri, size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(contact.displayName, style = MaterialTheme.typography.titleLarge)
                        if (contact.groupNames.isNotEmpty()) {
                            Text(
                                contact.groupNames.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.primary
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(contact) }) {
                        Icon(
                            if (contact.isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            "Favorite",
                            tint = if (contact.isStarred) cs.tertiary else cs.onSurfaceVariant
                        )
                    }
                }

                contact.phoneNumbers.forEach { phone ->
                    DialMeCard(onClick = {
                        viewModel.initiateCall(phone.number)
                        selectedContact = null
                    }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(viewModel.formatForDisplay(phone.number), style = MaterialTheme.typography.titleMedium)
                                Text(phone.typeLabel, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.number}"))
                                runCatching { context.startActivity(smsIntent) }
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.Message, stringResource(R.string.dialpad_send_sms))
                            }
                            IconButton(onClick = {
                                viewModel.initiateCall(phone.number)
                                selectedContact = null
                            }) {
                                Icon(Icons.Rounded.Call, stringResource(R.string.dialpad_call), tint = cs.primary)
                            }
                        }
                    }
                }

                // Quick actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ringtoneTargetContactId = contact.contactId
                            val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                contact.customRingtoneUri?.let {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                }
                            }
                            ringtoneLauncher.launch(pickerIntent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_ringtone), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { noteContact = contact },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.NoteAdd, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_note), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { reminderContact = contact },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_remind), maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        viewModel.addBlockRule(
                            BlockRule(
                                ruleType = BlockRuleType.EXACT,
                                pattern = contact.primaryNumber,
                                label = contact.displayName
                            )
                        )
                        selectedContact = null
                    }) {
                        Icon(Icons.Rounded.Block, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_block))
                    }
                    TextButton(onClick = {
                        viewModel.deleteContact(contact.contactId)
                        selectedContact = null
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

    // Create Contact Dialog
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.contacts_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ui_name)) }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.ui_phone_number)) }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        viewModel.createContact(name, phone)
                        showCreateDialog = false
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Duplicate Finder & Merge Dialog
    duplicateGroups?.let { groupsList ->
        AlertDialog(
            onDismissRequest = { duplicateGroups = null },
            title = { Text(stringResource(R.string.contacts_duplicates)) },
            text = {
                if (groupsList.isEmpty()) {
                    Text(stringResource(R.string.ui_no_duplicate_contacts_detected))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groupsList, key = { it.key }) { grp ->
                            DialMeCard {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(grp.reason, style = MaterialTheme.typography.labelSmall, color = cs.primary)
                                    grp.contacts.forEach { c ->
                                        Text("${c.displayName} · ${c.primaryNumber}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (grp.contacts.size >= 2) {
                                        TextButton(onClick = {
                                            viewModel.mergeDuplicates(grp.contacts[0].contactId, grp.contacts[1].contactId)
                                            duplicateGroups = null
                                        }) {
                                            Text(stringResource(R.string.ui_merge_first_two))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { duplicateGroups = null }) { Text(stringResource(R.string.ui_close)) }
            }
        )
    }

    // Contact Note Dialog
    noteContact?.let { contact ->
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteContact = null },
            title = { Text(stringResource(R.string.ui_note_for, contact.displayName)) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.ui_call_contact_note)) },
                    minLines = 3
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveCallNote(contact.primaryNumber, noteText, null, contact.lookupKey)
                    noteContact = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { noteContact = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Contact Reminder Dialog
    reminderContact?.let { contact ->
        var delayMins by remember { mutableStateOf("15") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reminderContact = null },
            title = { Text(stringResource(R.string.ui_remind_to_call, contact.displayName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = delayMins,
                        onValueChange = { delayMins = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.ui_remind_in_minutes)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.ui_reminder_note_optional)) }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val mins = delayMins.toLongOrNull()?.coerceAtLeast(1L) ?: 15L
                    viewModel.scheduleReminder(contact.displayName, contact.primaryNumber, note, mins)
                    reminderContact = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { reminderContact = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
