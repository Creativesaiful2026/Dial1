package com.example.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DialMeCard
import com.example.feature.common.DialMeMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupAndRestoreScreen(
    viewModel: DialMeMainViewModel,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var confirmReset by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportFullBackup(it) } }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFullBackup(it) } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_backup)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DialMeCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Security, null, tint = cs.primary)
                    Text(stringResource(R.string.ui_100_local_offline_backup), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Exports all DialMe settings, custom theme configuration, speed dials, block rules, call notes, and private vault entries into a local JSON file via Android's Storage Access Framework. Nothing is ever uploaded to any cloud.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { exportBackupLauncher.launch("dialme_backup.json") },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_export_local_backup_json))
            }

            OutlinedButton(
                onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_import_restore_backup_json))
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)
            ) {
                Icon(Icons.Rounded.RestartAlt, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_reset))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.ui_restore_default_preferences)) },
            text = { Text(stringResource(R.string.ui_this_resets_dialme_s_theme_layout_di)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllDefaults()
                        confirmReset = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.error)
                ) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
