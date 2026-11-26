package com.example.feature.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.datastore.SimSelectionMode
import com.example.core.designsystem.DialMeCard
import com.example.core.permissions.PermissionHelper
import com.example.feature.common.DialMeMainViewModel

private data class SettingsCategoryItem(
    val id: String,
    val titleRes: Int,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
fun SettingsScreen(
    viewModel: DialMeMainViewModel,
    onOpenAppearance: () -> Unit,
    onOpenCallerScreen: () -> Unit,
    onOpenPrivacyBlocking: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenBackup: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onRequestAllPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val config by viewModel.config.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

    var showDialpadPrefs by remember { mutableStateOf(false) }
    var showCallingPrefs by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHealthDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        SettingsCategoryItem("appearance", R.string.settings_appearance, "${config.themeMode.name} · ${config.fontFamily.name}", Icons.Rounded.Palette),
        SettingsCategoryItem("dialpad", R.string.settings_dialpad, "${config.dialpadButtonShape.name} · T9 & Clipboard", Icons.Rounded.Dialpad),
        SettingsCategoryItem("calling", R.string.settings_calling, "SIM Mode: ${config.defaultSimMode.name}", Icons.Rounded.Call),
        SettingsCategoryItem("contacts", R.string.settings_contacts, "View: ${config.contactViewMode.name} · Private Vault", Icons.Rounded.Contacts),
        SettingsCategoryItem("history", R.string.settings_call_history, "Analytics & Talk Time Dashboard", Icons.Rounded.BarChart),
        SettingsCategoryItem("caller", R.string.settings_caller_screen, "${config.callerScreenTheme.name} · ${config.callerAnswerStyle.name}", Icons.Rounded.PhoneInTalk),
        SettingsCategoryItem("privacy", R.string.settings_privacy, "Block Rules · App Lock · Call Screening", Icons.Rounded.Shield),
        SettingsCategoryItem("notifications", R.string.settings_notifications, "Health Check, Roles & Full-Screen Alerts", Icons.Rounded.NotificationsActive),
        SettingsCategoryItem("automation", R.string.settings_automation, "Callback Reminders, Rules & Quick SMS", Icons.Rounded.AutoAwesome),
        SettingsCategoryItem("backup", R.string.settings_backup, "Local JSON Backup & Restore", Icons.Rounded.Backup),
        SettingsCategoryItem("language", R.string.settings_language, when (config.languageTag) {
            "bn" -> "বাংলা (Bengali)"
            "en" -> "English"
            else -> "System Default"
        }, Icons.Rounded.Language),
        SettingsCategoryItem("about", R.string.settings_about, "GNU GPL v3 · Offline SIM Dialer", Icons.Rounded.Info)
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            DialMeCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                        }
                        if (!permissions.isDefaultDialer) {
                            Button(onClick = onRequestDefaultDialer) {
                                Text(stringResource(R.string.role_dialer_cta))
                            }
                        } else {
                            Text(stringResource(R.string.ui_default_dialer_active), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        }
                    }
                }
            }
        }

        items(categories, key = { it.id }) { cat ->
            DialMeCard(
                onClick = {
                    when (cat.id) {
                        "appearance" -> onOpenAppearance()
                        "dialpad" -> showDialpadPrefs = true
                        "calling" -> showCallingPrefs = true
                        "contacts", "privacy" -> onOpenPrivacyBlocking()
                        "history" -> onOpenStatistics()
                        "caller" -> onOpenCallerScreen()
                        "notifications" -> showHealthDialog = true
                        "automation" -> onOpenAutomation()
                        "backup" -> onOpenBackup()
                        "language" -> showLanguageDialog = true
                        "about" -> showAboutDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("settings_cat_${cat.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(cat.icon, null, tint = cs.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(cat.titleRes), style = MaterialTheme.typography.titleMedium)
                        Text(cat.subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
                }
            }
        }
    }

    if (showDialpadPrefs) {
        AlertDialog(
            onDismissRequest = { showDialpadPrefs = false },
            title = { Text(stringResource(R.string.settings_dialpad)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_keypad_dtmf_sounds), modifier = Modifier.weight(1f))
                        Switch(
                            checked = config.dialpadKeySounds,
                            onCheckedChange = { v -> viewModel.updateConfig { it.copy(dialpadKeySounds = v) } }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_keypad_haptic_vibration), modifier = Modifier.weight(1f))
                        Switch(
                            checked = config.dialpadHaptics,
                            onCheckedChange = { v -> viewModel.updateConfig { it.copy(dialpadHaptics = v) } }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_smart_clipboard_number_detect), modifier = Modifier.weight(1f))
                        Switch(
                            checked = config.clipboardAutoDetect,
                            onCheckedChange = { v -> viewModel.updateConfig { it.copy(clipboardAutoDetect = v) } }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialpadPrefs = false }) { Text(stringResource(R.string.ui_done)) }
            }
        )
    }

    if (showCallingPrefs) {
        AlertDialog(
            onDismissRequest = { showCallingPrefs = false },
            title = { Text(stringResource(R.string.settings_calling)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.ui_dual_sim_default_line), style = MaterialTheme.typography.labelLarge)
                    SimSelectionMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = config.defaultSimMode == mode,
                                onClick = { viewModel.updateConfig { it.copy(defaultSimMode = mode) } }
                            )
                            Text(mode.name.replace('_', ' '))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCallingPrefs = false }) { Text(stringResource(R.string.ui_done)) }
            }
        )
    }

    if (showLanguageDialog) {
        val opts = listOf(
            "system" to stringResource(R.string.language_system),
            "en" to stringResource(R.string.language_english),
            "bn" to stringResource(R.string.language_bengali)
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    opts.forEach { (tag, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = config.languageTag == tag,
                                onClick = {
                                    viewModel.setLanguage(tag)
                                    showLanguageDialog = false
                                }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showHealthDialog) {
        AlertDialog(
            onDismissRequest = { showHealthDialog = false },
            title = { Text(stringResource(R.string.ui_permissions_device_health)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Default Dialer Role: ${if (permissions.isDefaultDialer) "Active" else "Not Set"}")
                    Text("• Call Screening Role: ${if (permissions.isCallScreeningHeld) "Active" else "Not Set"}")
                    Text("• Contacts / Call Log: ${if (permissions.hasReadContacts && permissions.hasReadCallLog) "Granted" else "Missing"}")
                    Text("• Full-Screen Call Alerts: ${if (permissions.canUseFullScreenIntent) "Allowed" else "Revoked"}")
                    Text(
                        "Android 13–16 Sideload Note: If a role or permission is blocked by 'Restricted settings', open App Settings -> 3-dot menu -> Allow restricted settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onRequestAllPermissions, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.permissions_grant))
                    }
                    if (!permissions.canUseFullScreenIntent) {
                        OutlinedButton(
                            onClick = { context.startActivity(PermissionHelper.buildFullScreenIntentSettings(context)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.ui_enable_full_screen_call_alerts))
                        }
                    }
                    OutlinedButton(
                        onClick = { context.startActivity(PermissionHelper.buildAppSettingsIntent(context)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.permissions_open_settings))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHealthDialog = false }) { Text(stringResource(R.string.ui_close)) }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.settings_about)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        painter = painterResource(R.drawable.img_dialme_hero),
                        contentDescription = "DialMe banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Text(stringResource(R.string.ui_dialme_v1_0_0), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ui_your_dialer_your_style), color = cs.primary)
                    Text(
                        "Open-source, 100% offline-first Android SIM-based dialer. No VoIP, no internet permissions, no tracking.\n\nLicense: GNU General Public License v3.0 (GPL-3.0).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text(stringResource(R.string.ui_close)) }
            }
        )
    }
}
