package com.example.feature.customization

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.datastore.CallerAnswerStyle
import com.example.core.datastore.CallerNamePosition
import com.example.core.datastore.CallerScreenThemeStyle
import com.example.core.datastore.DialpadButtonShape
import com.example.core.datastore.DialpadLayoutStyle
import com.example.core.datastore.FontFamilyOption
import com.example.core.datastore.IncomingCallPresentation
import com.example.core.datastore.KeyPressEffect
import com.example.core.datastore.MainTabId
import com.example.core.datastore.ThemeMode
import com.example.core.designsystem.DialMeCard
import com.example.core.designsystem.parseHexColor
import com.example.feature.common.DialMeMainViewModel
import kotlin.math.roundToInt

private val ACCENT_PALETTES = listOf(
    "#14B8A6" to "Teal",
    "#3B82F6" to "Cobalt",
    "#10B981" to "Emerald",
    "#F97316" to "Coral",
    "#8B5CF6" to "Violet",
    "#EC4899" to "Rose",
    "#F59E0B" to "Amber",
    "#EF4444" to "Crimson"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    viewModel: DialMeMainViewModel,
    initialFocusSection: String = "theme",
    onBack: () -> Unit,
    onPreviewCallerScreen: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val config by viewModel.config.collectAsStateWithLifecycle()
    val presets by viewModel.customThemes.collectAsStateWithLifecycle()

    var showSavePresetDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.importCustomBackground(it) } }

    val callerPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.importCallerBackground(it) } }

    val exportThemeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportThemeJson(it) } }

    val importThemeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importThemeJson(it) } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (initialFocusSection == "caller") {
                        stringResource(R.string.settings_caller_screen)
                    } else {
                        stringResource(R.string.settings_appearance)
                    }
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Presets Carousel
            item {
                DialMeCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Palette, null, tint = cs.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_theme_presets), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showSavePresetDialog = true }) {
                                Icon(Icons.Rounded.Save, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.ui_save))
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets, key = { it.id }) { preset ->
                                FilterChip(
                                    selected = config.accentHex.equals(preset.accentHex, true) &&
                                        config.themeMode.name == preset.themeMode,
                                    onClick = { viewModel.applyThemePreset(preset) },
                                    label = { Text(preset.name) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportThemeLauncher.launch("dialme_theme.json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Upload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.ui_export))
                            }
                            OutlinedButton(
                                onClick = { importThemeLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.ui_import))
                            }
                            OutlinedButton(onClick = { viewModel.resetAllDefaults() }) {
                                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Theme Mode & Colors
            item {
                DialMeCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ui_theme_mode_surface), style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ThemeMode.entries) { mode ->
                                FilterChip(
                                    selected = config.themeMode == mode,
                                    onClick = { viewModel.updateConfig { it.copy(themeMode = mode) } },
                                    label = { Text(mode.name) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ui_material_you_dynamic_colors_android), modifier = Modifier.weight(1f))
                            Switch(
                                checked = config.useDynamicColor,
                                onCheckedChange = { v -> viewModel.updateConfig { it.copy(useDynamicColor = v) } }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ui_ambient_gradient_backdrop), modifier = Modifier.weight(1f))
                            Switch(
                                checked = config.useGradientBackground,
                                onCheckedChange = { v -> viewModel.updateConfig { it.copy(useGradientBackground = v) } }
                            )
                        }
                        Text(stringResource(R.string.ui_accent_color), style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(ACCENT_PALETTES) { (hex, label) ->
                                val c = parseHexColor(hex, cs.primary)
                                val selected = config.accentHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) cs.onSurface else cs.outline,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            viewModel.updateConfig { it.copy(accentHex = hex, useDynamicColor = false) }
                                        }
                                )
                            }
                        }
                        Text(stringResource(R.string.ui_card_glass_transparency, (config.cardTransparencyAlpha * 100).roundToInt()))
                        Slider(
                            value = config.cardTransparencyAlpha,
                            onValueChange = { v -> viewModel.updateConfig { it.copy(cardTransparencyAlpha = v) } },
                            valueRange = 0.50f..1.0f
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Image, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_background_image))
                            }
                            if (config.customBackgroundPath != null) {
                                TextButton(onClick = { viewModel.clearCustomBackground() }) {
                                    Text(stringResource(R.string.ui_clear))
                                }
                            }
                        }
                    }
                }
            }

            // Dialpad & Layout Customization
            item {
                DialMeCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.ui_dialpad_typography), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.ui_keypad_layout), style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DialpadLayoutStyle.entries) { style ->
                                FilterChip(
                                    selected = config.dialpadLayoutStyle == style,
                                    onClick = { viewModel.updateConfig { it.copy(dialpadLayoutStyle = style) } },
                                    label = { Text(style.name.replace('_', ' ')) }
                                )
                            }
                        }
                        Text(stringResource(R.string.ui_button_shape), style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DialpadButtonShape.entries) { shape ->
                                FilterChip(
                                    selected = config.dialpadButtonShape == shape,
                                    onClick = { viewModel.updateConfig { it.copy(dialpadButtonShape = shape) } },
                                    label = { Text(shape.name.replace('_', ' ')) }
                                )
                            }
                        }
                        Text(stringResource(R.string.ui_corner_radius_dp, config.dialpadCornerRadiusDp))
                        Slider(
                            value = config.dialpadCornerRadiusDp.toFloat(),
                            onValueChange = { v -> viewModel.updateConfig { it.copy(dialpadCornerRadiusDp = v.roundToInt()) } },
                            valueRange = 8f..32f
                        )
                        Text(stringResource(R.string.ui_button_size_scale, (config.dialpadButtonScale * 100).roundToInt()))
                        Slider(
                            value = config.dialpadButtonScale,
                            onValueChange = { v -> viewModel.updateConfig { it.copy(dialpadButtonScale = v) } },
                            valueRange = 0.85f..1.25f
                        )
                        Text(stringResource(R.string.ui_key_spacing_dp, config.dialpadSpacingDp))
                        Slider(
                            value = config.dialpadSpacingDp.toFloat(),
                            onValueChange = { v -> viewModel.updateConfig { it.copy(dialpadSpacingDp = v.roundToInt()) } },
                            valueRange = 0f..24f
                        )
                        Text(stringResource(R.string.key_effect_label), style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(KeyPressEffect.entries) { effect ->
                                FilterChip(
                                    selected = config.keyPressEffect == effect,
                                    onClick = { viewModel.updateConfig { it.copy(keyPressEffect = effect) } },
                                    label = { Text(effect.name.replace('_', ' ')) }
                                )
                            }
                        }
                        Text(stringResource(R.string.key_effect_intensity, (config.keyEffectIntensity * 100).roundToInt()))
                        Slider(
                            value = config.keyEffectIntensity,
                            onValueChange = { v -> viewModel.updateConfig { it.copy(keyEffectIntensity = v) } },
                            valueRange = 0.4f..1.6f
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.key_glass_label), modifier = Modifier.weight(1f))
                            Switch(
                                checked = config.dialpadGlass,
                                onCheckedChange = { v -> viewModel.updateConfig { it.copy(dialpadGlass = v) } }
                            )
                        }
                        if (config.dialpadGlass) {
                            Text(stringResource(R.string.key_glass_strength, (config.dialpadGlassAlpha * 100).roundToInt()))
                            Slider(
                                value = config.dialpadGlassAlpha,
                                onValueChange = { v -> viewModel.updateConfig { it.copy(dialpadGlassAlpha = v) } },
                                valueRange = 0.05f..0.6f
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.key_border_label), modifier = Modifier.weight(1f))
                                Switch(
                                    checked = config.dialpadKeyBorder,
                                    onCheckedChange = { v -> viewModel.updateConfig { it.copy(dialpadKeyBorder = v) } }
                                )
                            }
                        }
                        Text(stringResource(R.string.ui_call_button_radius_dp, config.callerButtonRadiusDp))
                        Slider(
                            value = config.callerButtonRadiusDp.toFloat(),
                            onValueChange = { v -> viewModel.updateConfig { it.copy(callerButtonRadiusDp = v.roundToInt()) } },
                            valueRange = 12f..48f
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ui_show_letters_on_keys_abc), modifier = Modifier.weight(1f))
                            Switch(
                                checked = config.showDialpadLetters,
                                onCheckedChange = { v -> viewModel.updateConfig { it.copy(showDialpadLetters = v) } }
                            )
                        }
                        Text(stringResource(R.string.ui_font_family), style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(FontFamilyOption.entries) { f ->
                                FilterChip(
                                    selected = config.fontFamily == f,
                                    onClick = { viewModel.updateConfig { it.copy(fontFamily = f) } },
                                    label = { Text(f.name.replace('_', ' ')) }
                                )
                            }
                        }
                        Text(stringResource(R.string.ui_font_scale, (config.fontScale * 100).roundToInt()))
                        Slider(
                            value = config.fontScale,
                            onValueChange = { v -> viewModel.updateConfig { it.copy(fontScale = v) } },
                            valueRange = 0.85f..1.25f
                        )
                    }
                }
            }

            // Incoming call presentation
            item {
                DialMeCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.auto_record_label), modifier = Modifier.weight(1f))
                            Switch(
                                checked = config.autoRecordCalls,
                                onCheckedChange = { v -> viewModel.updateConfig { it.copy(autoRecordCalls = v) } }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.record_try_call_audio_label), modifier = Modifier.weight(1f))
                            Switch(
                                checked = config.recordTryCallAudio,
                                onCheckedChange = { v -> viewModel.updateConfig { it.copy(recordTryCallAudio = v) } }
                            )
                        }
                        Text(
                            stringResource(R.string.auto_record_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.group_recents_label), modifier = Modifier.weight(1f))
                            Switch(
                                checked = config.groupRecentsByNumber,
                                onCheckedChange = { v -> viewModel.updateConfig { it.copy(groupRecentsByNumber = v) } }
                            )
                        }
                        Text(stringResource(R.string.incoming_presentation_label), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = config.incomingCallPresentation == IncomingCallPresentation.FULL_SCREEN,
                                onClick = { viewModel.updateConfig { it.copy(incomingCallPresentation = IncomingCallPresentation.FULL_SCREEN) } },
                                label = { Text(stringResource(R.string.incoming_presentation_full)) }
                            )
                            FilterChip(
                                selected = config.incomingCallPresentation == IncomingCallPresentation.HEADS_UP_WHEN_UNLOCKED,
                                onClick = { viewModel.updateConfig { it.copy(incomingCallPresentation = IncomingCallPresentation.HEADS_UP_WHEN_UNLOCKED) } },
                                label = { Text(stringResource(R.string.incoming_presentation_heads_up)) }
                            )
                        }
                        Text(
                            stringResource(R.string.incoming_presentation_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        Text(stringResource(R.string.caller_bg_label), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    callerPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Image, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.caller_bg_pick))
                            }
                            if (config.callerBackgroundPath != null) {
                                TextButton(onClick = { viewModel.clearCallerBackground() }) {
                                    Text(stringResource(R.string.ui_clear))
                                }
                            }
                        }
                        if (config.callerBackgroundPath != null) {
                            Text(stringResource(R.string.caller_bg_dim, (config.callerBackgroundDim * 100).roundToInt()))
                            Slider(
                                value = config.callerBackgroundDim,
                                onValueChange = { v -> viewModel.updateConfig { it.copy(callerBackgroundDim = v) } },
                                valueRange = 0f..0.9f
                            )
                        }
                    }
                }
            }

            // Caller Screen Customization
            item {
                DialMeCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PhoneInTalk, null, tint = cs.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_caller_screen),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = onPreviewCallerScreen) {
                                Text(stringResource(R.string.ui_preview))
                            }
                        }
                        Text(stringResource(R.string.ui_backdrop_style), style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CallerScreenThemeStyle.entries) { s ->
                                FilterChip(
                                    selected = config.callerScreenTheme == s,
                                    onClick = { viewModel.updateConfig { it.copy(callerScreenTheme = s) } },
                                    label = { Text(s.name.replace('_', ' ')) }
                                )
                            }
                        }
                        Text(stringResource(R.string.ui_answer_controls), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CallerAnswerStyle.entries.forEach { a ->
                                FilterChip(
                                    selected = config.callerAnswerStyle == a,
                                    onClick = { viewModel.updateConfig { it.copy(callerAnswerStyle = a) } },
                                    label = { Text(a.name.replace('_', ' ')) }
                                )
                            }
                        }
                        Text(stringResource(R.string.ui_caller_name_position), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CallerNamePosition.entries.forEach { pos ->
                                FilterChip(
                                    selected = config.callerNamePosition == pos,
                                    onClick = { viewModel.updateConfig { it.copy(callerNamePosition = pos) } },
                                    label = { Text(pos.name) }
                                )
                            }
                        }
                    }
                }
            }

            // Navigation Tab Arrangement
            item {
                DialMeCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ui_navigation_tab_order_start_tab), style = MaterialTheme.typography.titleMedium)
                        config.navTabOrder.forEachIndexed { idx, tab ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${idx + 1}. ${tab.name}", modifier = Modifier.weight(1f))
                                FilterChip(
                                    selected = config.startTab == tab,
                                    enabled = tab !in config.hiddenTabs,
                                    onClick = { viewModel.updateConfig { it.copy(startTab = tab) } },
                                    label = { Text(stringResource(R.string.ui_start)) }
                                )
                                Switch(
                                    checked = tab !in config.hiddenTabs,
                                    enabled = tab != MainTabId.SETTINGS,
                                    onCheckedChange = { show ->
                                        viewModel.updateConfig { cfg ->
                                            cfg.copy(
                                                hiddenTabs = if (show) cfg.hiddenTabs - tab else (cfg.hiddenTabs + tab).distinct()
                                            )
                                        }
                                    }
                                )
                                IconButton(
                                    enabled = idx > 0,
                                    onClick = {
                                        viewModel.updateConfig { cfg ->
                                            val list = cfg.navTabOrder.toMutableList()
                                            val tmp = list[idx - 1]
                                            list[idx - 1] = list[idx]
                                            list[idx] = tmp
                                            cfg.copy(navTabOrder = list)
                                        }
                                    }
                                ) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                                IconButton(
                                    enabled = idx < config.navTabOrder.lastIndex,
                                    onClick = {
                                        viewModel.updateConfig { cfg ->
                                            val list = cfg.navTabOrder.toMutableList()
                                            val tmp = list[idx + 1]
                                            list[idx + 1] = list[idx]
                                            list[idx] = tmp
                                            cfg.copy(navTabOrder = list)
                                        }
                                    }
                                ) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSavePresetDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text(stringResource(R.string.ui_save_theme_preset)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ui_preset_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveCurrentAsPreset(name)
                    showSavePresetDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
