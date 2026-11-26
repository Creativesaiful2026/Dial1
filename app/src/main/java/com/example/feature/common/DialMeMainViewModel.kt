package com.example.feature.common

import com.example.R
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.DialMeAppContainer
import com.example.core.common.PhoneFormatUtils
import com.example.core.datastore.DialMeConfig
import com.example.core.datastore.PreferencesRepository
import com.example.core.permissions.PermissionHelper
import com.example.core.permissions.PermissionSnapshot
import com.example.data.CallHistoryRepository
import com.example.data.ContactsRepository
import com.example.data.DialMeLocalRepository
import com.example.domain.model.AutomationRule
import com.example.domain.model.BlockRule
import com.example.domain.model.CallDirection
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallNote
import com.example.domain.model.CallReminder
import com.example.domain.model.CallStatistics
import com.example.domain.model.CallTypeFilter
import com.example.domain.model.ContactGroup
import com.example.domain.model.ContactItem
import com.example.domain.model.CustomThemePreset
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.PrivateContact
import com.example.domain.model.QuickResponse
import com.example.domain.model.SimAccount
import com.example.domain.model.SpeedDialEntry
import com.example.domain.model.T9MatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DialMeMainViewModel(
    private val appContext: Context,
    private val container: DialMeAppContainer
) : ViewModel() {

    private val prefsRepo: PreferencesRepository = container.preferencesRepository
    private val contactsRepo: ContactsRepository = container.contactsRepository
    private val callHistoryRepo: CallHistoryRepository = container.callHistoryRepository
    private val localRepo: DialMeLocalRepository = container.localRepository
    private val callPlacer = container.telecomCallPlacer

    val config: StateFlow<DialMeConfig> = prefsRepo.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DialMeConfig())

    private val _permissions = MutableStateFlow(PermissionHelper.snapshot(appContext))
    val permissions: StateFlow<PermissionSnapshot> = _permissions

    private val _simAccounts = MutableStateFlow(callPlacer.getSimAccounts())
    val simAccounts: StateFlow<List<SimAccount>> = _simAccounts

    private val _dialInput = MutableStateFlow("")
    val dialInput: StateFlow<String> = _dialInput

    private val _clipboardCandidate = MutableStateFlow<String?>(null)
    val clipboardCandidate: StateFlow<String?> = _clipboardCandidate

    private val _contactSearchQuery = MutableStateFlow("")
    val contactSearchQuery: StateFlow<String> = _contactSearchQuery

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup

    private val _historyFilter = MutableStateFlow(CallTypeFilter.ALL)
    val historyFilter: StateFlow<CallTypeFilter> = _historyFilter

    private val _historySearch = MutableStateFlow("")
    val historySearch: StateFlow<String> = _historySearch

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    private val _pendingSimChoiceNumber = MutableStateFlow<String?>(null)
    val pendingSimChoiceNumber: StateFlow<String?> = _pendingSimChoiceNumber

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    val contacts: StateFlow<List<ContactItem>> = contactsRepo.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val t9Cache = contactsRepo.observeT9Cache()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val t9Matches: StateFlow<List<T9MatchResult>> = combine(_dialInput, t9Cache) { query, cache ->
        contactsRepo.searchT9(query, cache)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val callHistory: StateFlow<List<CallLogItem>> = callHistoryRepo.observeCallHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredCallHistory: StateFlow<List<CallLogItem>> = combine(
        callHistory,
        _historyFilter,
        _historySearch
    ) { items, filter, query ->
        val q = query.trim().lowercase()
        items.filter { item ->
            val matchesFilter = when (filter) {
                CallTypeFilter.ALL -> true
                CallTypeFilter.INCOMING -> item.direction == CallDirection.INCOMING
                CallTypeFilter.OUTGOING -> item.direction == CallDirection.OUTGOING
                CallTypeFilter.MISSED -> item.direction == CallDirection.MISSED
                CallTypeFilter.REJECTED -> item.direction == CallDirection.REJECTED || item.direction == CallDirection.BLOCKED
            }
            val matchesQuery = q.isEmpty() ||
                (item.cachedName?.lowercase()?.contains(q) == true) ||
                item.number.contains(q)
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredContacts: StateFlow<List<ContactItem>> = combine(
        contacts,
        _contactSearchQuery,
        _selectedGroup
    ) { list, query, group ->
        val q = query.trim().lowercase()
        list.filter { c ->
            val inGroup = group == null || c.groupNames.contains(group)
            val matchesQuery = q.isEmpty() ||
                c.displayName.lowercase().contains(q) ||
                c.phoneNumbers.any { it.number.contains(q) || it.normalizedNumber.contains(q) }
            inGroup && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteContacts: StateFlow<List<ContactItem>> = combine(contacts, _contactSearchQuery) { list, q ->
        val query = q.trim().lowercase()
        list.filter { it.isStarred && (query.isEmpty() || it.displayName.lowercase().contains(query)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val speedDials: StateFlow<List<SpeedDialEntry>> = localRepo.speedDials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockRules: StateFlow<List<BlockRule>> = localRepo.blockRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockedEvents = localRepo.blockedEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val callNotes: StateFlow<List<CallNote>> = localRepo.callNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<CallReminder>> = localRepo.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quickResponses: StateFlow<List<QuickResponse>> = localRepo.quickResponses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val privateContacts: StateFlow<List<PrivateContact>> = localRepo.privateContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val automationRules: StateFlow<List<AutomationRule>> = localRepo.automationRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customThemes: StateFlow<List<CustomThemePreset>> = localRepo.customThemes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val callStatistics: StateFlow<CallStatistics> = combine(callHistory) { (items) ->
        callHistoryRepo.computeStatistics(items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallStatistics())

    private val _contactGroups = MutableStateFlow<List<ContactGroup>>(emptyList())
    val contactGroups: StateFlow<List<ContactGroup>> = _contactGroups

    init {
        viewModelScope.launch {
            contacts.collect { list ->
                if (list.isNotEmpty()) {
                    contactsRepo.syncT9Cache(list)
                }
                _contactGroups.value = contactsRepo.getGroups(list)
            }
        }
    }

    fun refreshSystemState() {
        _permissions.value = PermissionHelper.snapshot(appContext)
        _simAccounts.value = callPlacer.getSimAccounts()
    }

    fun markUnlocked() {
        _isUnlocked.value = true
    }

    private var backgroundedAtMs: Long = 0L

    /** Call when the app leaves the foreground. */
    fun onAppBackgrounded() {
        backgroundedAtMs = android.os.SystemClock.elapsedRealtime()
    }

    /**
     * Call when the app returns to the foreground. Re-locks if app lock is on and the
     * app was away longer than [graceMs] (so permission dialogs do not force a re-auth).
     */
    fun onAppForegrounded(graceMs: Long = config.value.appLockTimeoutSec.coerceAtLeast(0) * 1000L) {
        if (backgroundedAtMs == 0L) return
        val away = android.os.SystemClock.elapsedRealtime() - backgroundedAtMs
        backgroundedAtMs = 0L
        if (config.value.appLockEnabled && away > graceMs) {
            _isUnlocked.value = false
        }
    }

    fun updateClipboardCandidate(candidate: String?) {
        _clipboardCandidate.value = candidate
    }

    fun appendDialDigit(digit: String) {
        _dialInput.value += digit
    }

    fun setDialInput(number: String) {
        _dialInput.value = number
    }

    fun backspaceDialDigit() {
        val current = _dialInput.value
        if (current.isNotEmpty()) {
            _dialInput.value = current.dropLast(1)
        }
    }

    fun clearDialInput() {
        _dialInput.value = ""
    }

    fun setContactSearch(query: String) {
        _contactSearchQuery.value = query
    }

    fun setSelectedGroup(groupTitle: String?) {
        _selectedGroup.value = groupTitle
    }

    fun setHistoryFilter(filter: CallTypeFilter) {
        _historyFilter.value = filter
    }

    fun setHistorySearch(query: String) {
        _historySearch.value = query
    }

    fun initiateCall(number: String, explicitSimAccountId: String? = null) {
        val cleaned = number.trim()
        if (cleaned.isEmpty()) return
        val sims = _simAccounts.value
        val cfg = config.value

        if (explicitSimAccountId == null && sims.size > 1) {
            when (cfg.defaultSimMode) {
                com.example.core.datastore.SimSelectionMode.ASK_EVERY_TIME -> {
                    _pendingSimChoiceNumber.value = cleaned
                    return
                }
                com.example.core.datastore.SimSelectionMode.SIM_1 -> {
                    callPlacer.placeCall(cleaned, sims.getOrNull(0)?.id)
                    return
                }
                com.example.core.datastore.SimSelectionMode.SIM_2 -> {
                    callPlacer.placeCall(cleaned, sims.getOrNull(1)?.id)
                    return
                }
            }
        }
        val simId = explicitSimAccountId ?: cfg.preferredSimAccountId ?: sims.firstOrNull()?.id
        callPlacer.placeCall(cleaned, simId)
    }

    fun dismissSimChooser() {
        _pendingSimChoiceNumber.value = null
    }

    fun confirmSimAndCall(simAccountId: String) {
        val num = _pendingSimChoiceNumber.value ?: return
        _pendingSimChoiceNumber.value = null
        callPlacer.placeCall(num, simAccountId)
    }

    fun triggerSpeedDial(slot: Int): SpeedDialEntry? {
        val entry = speedDials.value.firstOrNull { it.slot == slot }
        if (entry != null) {
            initiateCall(entry.phoneNumber, entry.simAccountId)
        }
        return entry
    }

    fun saveSpeedDial(slot: Int, name: String, number: String, lookupKey: String? = null) {
        viewModelScope.launch {
            localRepo.saveSpeedDial(slot, name, number, lookupKey)
            notifyRes(R.string.notify_speed_dial_saved, slot)
        }
    }

    fun clearSpeedDial(slot: Int) {
        viewModelScope.launch {
            localRepo.clearSpeedDial(slot)
            notifyRes(R.string.notify_speed_dial_cleared, slot)
        }
    }

    fun toggleFavorite(contact: ContactItem) {
        viewModelScope.launch {
            val ok = contactsRepo.toggleFavorite(contact.contactId, !contact.isStarred)
            if (!ok) notifyRes(R.string.notify_write_contacts_required)
        }
    }

    fun createContact(name: String, phone: String) {
        viewModelScope.launch {
            val ok = contactsRepo.createContact(name, phone)
            notify(if (ok) "Contact saved" else "Unable to save contact (check permissions)")
        }
    }

    fun deleteContact(contactId: Long) {
        viewModelScope.launch {
            val ok = contactsRepo.deleteContact(contactId)
            notify(if (ok) "Contact deleted" else "Unable to delete contact")
        }
    }

    fun setCustomRingtone(contactId: Long, ringtoneUri: String?) {
        viewModelScope.launch {
            val ok = contactsRepo.setCustomRingtone(contactId, ringtoneUri)
            notify(if (ok) "Custom ringtone updated" else "Permission required to update ringtone")
        }
    }

    fun findDuplicateGroups(): List<DuplicateGroup> = contactsRepo.findDuplicates(contacts.value)

    fun mergeDuplicates(primaryId: Long, duplicateId: Long) {
        viewModelScope.launch {
            val ok = contactsRepo.mergeContacts(primaryId, duplicateId)
            notify(if (ok) "Contacts merged" else "Merge failed or requires WRITE_CONTACTS")
        }
    }

    fun exportContactsVcf(uri: Uri) {
        viewModelScope.launch {
            val vcf = contactsRepo.exportContactsToVcf(contacts.value)
            val ok = writeTextToUri(uri, vcf)
            notify(if (ok) "Exported ${contacts.value.size} contacts to VCF" else "Export failed")
        }
    }

    fun importContactsVcf(uri: Uri) {
        viewModelScope.launch {
            val text = readTextFromUri(uri)
            if (text.isNullOrBlank()) {
                notifyRes(R.string.notify_vcf_read_failed)
                return@launch
            }
            val count = contactsRepo.importContactsFromVcf(text)
            notifyRes(R.string.notify_imported_contacts, count)
        }
    }

    fun deleteCallLogs(ids: Set<Long>) {
        viewModelScope.launch {
            val count = callHistoryRepo.deleteCallLogs(ids)
            notify(if (count > 0) "Deleted $count call log entries" else "Permission required to delete call logs")
        }
    }

    fun exportCallHistoryCsv(uri: Uri) {
        viewModelScope.launch {
            val csv = callHistoryRepo.exportCsv(callHistory.value)
            val ok = writeTextToUri(uri, csv)
            notify(if (ok) "Exported call history CSV" else "Failed to export CSV")
        }
    }

    fun addBlockRule(rule: BlockRule) {
        viewModelScope.launch {
            localRepo.addBlockRule(rule)
            notifyRes(R.string.notify_block_rule_added)
        }
    }

    fun toggleBlockRule(rule: BlockRule) {
        viewModelScope.launch { localRepo.toggleBlockRule(rule) }
    }

    fun deleteBlockRule(rule: BlockRule) {
        viewModelScope.launch { localRepo.deleteBlockRule(rule) }
    }

    fun clearBlockedEvents() {
        viewModelScope.launch { localRepo.clearBlockedEvents() }
    }

    fun saveCallNote(number: String, noteText: String, callLogId: Long? = null, lookupKey: String? = null) {
        viewModelScope.launch {
            if (noteText.isNotBlank()) {
                localRepo.saveCallNote(number, noteText, callLogId, lookupKey)
                notifyRes(R.string.notify_call_note_saved)
            }
        }
    }

    fun deleteCallNote(id: Long) {
        viewModelScope.launch { localRepo.deleteCallNote(id) }
    }

    fun scheduleReminder(displayName: String, number: String, note: String, delayMinutes: Long) {
        viewModelScope.launch {
            localRepo.scheduleReminder(displayName, number, note, delayMinutes)
            notifyRes(R.string.notify_reminder_scheduled, delayMinutes)
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { localRepo.deleteReminder(id) }
    }

    fun addQuickResponse(msg: String) {
        viewModelScope.launch { localRepo.addQuickResponse(msg) }
    }

    fun deleteQuickResponse(id: Long) {
        viewModelScope.launch { localRepo.deleteQuickResponse(id) }
    }

    fun addPrivateContact(name: String, phone: String, notes: String) {
        viewModelScope.launch {
            localRepo.addPrivateContact(name, phone, notes)
            notifyRes(R.string.notify_saved_to_vault)
        }
    }

    fun deletePrivateContact(id: Long) {
        viewModelScope.launch { localRepo.deletePrivateContact(id) }
    }

    fun addAutomationRule(rule: AutomationRule) {
        viewModelScope.launch {
            localRepo.addAutomationRule(rule)
            notifyRes(R.string.notify_automation_created)
        }
    }

    fun toggleAutomationRule(rule: AutomationRule) {
        viewModelScope.launch { localRepo.toggleAutomationRule(rule) }
    }

    fun deleteAutomationRule(id: Long) {
        viewModelScope.launch { localRepo.deleteAutomationRule(id) }
    }

    fun updateConfig(transform: (DialMeConfig) -> DialMeConfig) {
        viewModelScope.launch { prefsRepo.updateConfig(transform) }
    }

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            prefsRepo.updateConfig { it.copy(languageTag = tag) }
            val locales = if (tag == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun applyThemePreset(preset: CustomThemePreset) {
        val mode = runCatching { com.example.core.datastore.ThemeMode.valueOf(preset.themeMode) }
            .getOrDefault(com.example.core.datastore.ThemeMode.DARK)
        val shape = runCatching { com.example.core.datastore.DialpadButtonShape.valueOf(preset.buttonShape) }
            .getOrDefault(com.example.core.datastore.DialpadButtonShape.SQUIRCLE)
        val font = runCatching { com.example.core.datastore.FontFamilyOption.valueOf(preset.fontFamilyKey) }
            .getOrDefault(com.example.core.datastore.FontFamilyOption.PLUS_JAKARTA)
        updateConfig {
            it.copy(
                themeMode = mode,
                useDynamicColor = false,
                accentHex = preset.accentHex,
                secondaryAccentHex = preset.secondaryHex,
                useGradientBackground = preset.useGradient,
                cardTransparencyAlpha = preset.cardAlpha,
                dialpadButtonShape = shape,
                dialpadCornerRadiusDp = preset.cornerRadiusDp,
                fontFamily = font
            )
        }
        notifyRes(R.string.notify_theme_applied, preset.name)
    }

    fun saveCurrentAsPreset(name: String) {
        viewModelScope.launch {
            val cfg = config.value
            localRepo.saveCustomTheme(
                CustomThemePreset(
                    id = "",
                    name = name.ifBlank { "My Theme" },
                    isBuiltIn = false,
                    themeMode = cfg.themeMode.name,
                    accentHex = cfg.accentHex,
                    secondaryHex = cfg.secondaryAccentHex,
                    useGradient = cfg.useGradientBackground,
                    cardAlpha = cfg.cardTransparencyAlpha,
                    buttonShape = cfg.dialpadButtonShape.name,
                    cornerRadiusDp = cfg.dialpadCornerRadiusDp,
                    fontFamilyKey = cfg.fontFamily.name
                )
            )
            notifyRes(R.string.notify_theme_preset_saved)
        }
    }

    fun importCustomBackground(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(appContext.filesDir, "themes").apply { mkdirs() }
                val dest = File(dir, "custom_bg.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                prefsRepo.updateConfig { it.copy(customBackgroundPath = dest.absolutePath) }
                notifyRes(R.string.notify_background_applied)
            } catch (_: Exception) {
                notifyRes(R.string.notify_background_failed)
            }
        }
    }

    fun importCallerBackground(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(appContext.filesDir, "themes").apply { mkdirs() }
                // Unique file name so the image loader never shows a stale cached picture.
                val dest = File(dir, "caller_bg_${System.currentTimeMillis()}.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                val old = config.value.callerBackgroundPath
                prefsRepo.updateConfig { it.copy(callerBackgroundPath = dest.absolutePath) }
                old?.let { runCatching { File(it).delete() } }
                notifyRes(R.string.notify_background_applied)
            } catch (_: Exception) {
                notifyRes(R.string.notify_background_failed)
            }
        }
    }

    fun clearCallerBackground() {
        val old = config.value.callerBackgroundPath
        updateConfig { it.copy(callerBackgroundPath = null) }
        old?.let { runCatching { File(it).delete() } }
    }

    fun clearCustomBackground() {
        updateConfig { it.copy(customBackgroundPath = null) }
    }

    fun exportThemeJson(uri: Uri) {
        viewModelScope.launch {
            val cfg = config.value
            val preset = CustomThemePreset(
                id = "exported_${System.currentTimeMillis()}",
                name = "DialMe Theme",
                themeMode = cfg.themeMode.name,
                accentHex = cfg.accentHex,
                secondaryHex = cfg.secondaryAccentHex,
                useGradient = cfg.useGradientBackground,
                cardAlpha = cfg.cardTransparencyAlpha,
                buttonShape = cfg.dialpadButtonShape.name,
                cornerRadiusDp = cfg.dialpadCornerRadiusDp,
                fontFamilyKey = cfg.fontFamily.name
            )
            val ok = writeTextToUri(uri, localRepo.exportThemePresetJson(preset))
            notify(if (ok) "Theme JSON exported" else "Theme export failed")
        }
    }

    fun importThemeJson(uri: Uri) {
        viewModelScope.launch {
            val raw = readTextFromUri(uri)
            val preset = raw?.let { localRepo.importThemePresetJson(it) }
            if (preset != null) {
                localRepo.saveCustomTheme(preset)
                applyThemePreset(preset)
            } else {
                notifyRes(R.string.notify_theme_json_invalid)
            }
        }
    }

    fun exportFullBackup(uri: Uri) {
        viewModelScope.launch {
            val payload = localRepo.exportFullBackupJson()
            val ok = writeTextToUri(uri, payload)
            notify(if (ok) "Full backup exported locally" else "Backup export failed")
        }
    }

    fun importFullBackup(uri: Uri) {
        viewModelScope.launch {
            val raw = readTextFromUri(uri)
            val ok = raw != null && localRepo.importFullBackupJson(raw)
            notify(if (ok) "Backup restored successfully" else "Invalid backup file")
        }
    }

    fun resetAllDefaults() {
        viewModelScope.launch {
            prefsRepo.resetDefaults()
            notifyRes(R.string.notify_prefs_restored)
        }
    }

    fun formatForDisplay(raw: String): String =
        PhoneFormatUtils.formatDisplay(raw, config.value.countryCodeOverride)

    fun notify(msg: String) {
        _snackbarMessage.value = msg
    }

    /** Posts a snackbar message in the language chosen inside the app. */
    fun notifyRes(@androidx.annotation.StringRes resId: Int, vararg args: Any) {
        _snackbarMessage.value = localizedContext().getString(resId, *args)
    }

    private fun localizedContext(): Context {
        val tag = config.value.languageTag
        if (tag == "system" || tag.isBlank()) return appContext
        return runCatching {
            val cfg = android.content.res.Configuration(appContext.resources.configuration)
            cfg.setLocale(java.util.Locale.forLanguageTag(tag))
            appContext.createConfigurationContext(cfg)
        }.getOrDefault(appContext)
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private suspend fun writeTextToUri(uri: Uri, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun readTextFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun factory(container: DialMeAppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DialMeMainViewModel(container.applicationContext, container)
            }
        }
    }
}
