package com.example.data

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.common.PhoneFormatUtils
import com.example.core.common.T9Utils
import com.example.core.database.AutomationRuleEntity
import com.example.core.database.BlockRuleEntity
import com.example.core.database.CallNoteEntity
import com.example.core.database.CallReminderEntity
import com.example.core.database.CustomThemeEntity
import com.example.core.database.DialMeDatabase
import com.example.core.common.VaultCrypto
import com.example.core.database.PrivateContactEntity
import com.example.core.database.QuickResponseEntity
import com.example.core.database.SpeedDialEntity
import com.example.core.datastore.DialMeConfig
import com.example.core.datastore.PreferencesRepository
import com.example.core.permissions.PermissionHelper
import com.example.domain.model.AutomationAction
import com.example.domain.model.AutomationRule
import com.example.domain.model.AutomationTrigger
import com.example.domain.model.BlockAction
import com.example.domain.model.BlockRule
import com.example.domain.model.BlockRuleType
import com.example.domain.model.BlockedCallEvent
import com.example.domain.model.CallNote
import com.example.domain.model.CallReminder
import com.example.domain.model.CustomThemePreset
import com.example.domain.model.PrivateContact
import com.example.domain.model.QuickResponse
import com.example.domain.model.ReminderStatus
import com.example.domain.model.SpeedDialEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
data class DialMeBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val config: DialMeConfig,
    val speedDials: List<SpeedDialEntry>,
    val blockRules: List<BlockRule>,
    val callNotes: List<CallNote>,
    val quickResponses: List<QuickResponse>,
    val privateContacts: List<PrivateContact>,
    val automationRules: List<AutomationRule>,
    val customThemes: List<CustomThemePreset>
)

class DialMeLocalRepository(
    private val context: Context,
    private val db: DialMeDatabase,
    private val prefsRepo: PreferencesRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    val speedDials: Flow<List<SpeedDialEntry>> = db.speedDialDao().observeAll().map { list ->
        list.map {
            SpeedDialEntry(
                slot = it.slot,
                contactLookupKey = it.contactLookupKey,
                displayName = it.displayName,
                phoneNumber = it.phoneNumber,
                normalizedNumber = it.normalizedNumber,
                simAccountId = it.simAccountId
            )
        }
    }

    val blockRules: Flow<List<BlockRule>> = db.blockRuleDao().observeAll().map { list ->
        list.map {
            BlockRule(
                id = it.id,
                ruleType = runCatching { BlockRuleType.valueOf(it.ruleType) }.getOrDefault(BlockRuleType.EXACT),
                pattern = it.pattern,
                label = it.label,
                action = runCatching { BlockAction.valueOf(it.action) }.getOrDefault(BlockAction.REJECT),
                skipCallLog = it.skipCallLog,
                skipNotification = it.skipNotification,
                isEnabled = it.isEnabled,
                syncedToSystemBlocked = it.syncedToSystemBlocked,
                createdAt = it.createdAt
            )
        }
    }

    val blockedEvents: Flow<List<BlockedCallEvent>> = db.blockedCallEventDao().observeRecent().map { list ->
        list.map {
            BlockedCallEvent(
                id = it.id,
                phoneNumber = it.phoneNumber,
                normalizedNumber = it.normalizedNumber,
                matchedRuleId = it.matchedRuleId,
                reason = it.reason,
                actionTaken = runCatching { BlockAction.valueOf(it.actionTaken) }.getOrDefault(BlockAction.REJECT),
                timestamp = it.timestamp
            )
        }
    }

    val callNotes: Flow<List<CallNote>> = db.callNoteDao().observeAll().map { list ->
        list.map {
            CallNote(
                id = it.id,
                callLogId = it.callLogId,
                normalizedNumber = it.normalizedNumber,
                contactLookupKey = it.contactLookupKey,
                noteText = it.noteText,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }
    }

    val reminders: Flow<List<CallReminder>> = db.callReminderDao().observeAll().map { list ->
        list.map {
            CallReminder(
                id = it.id,
                phoneNumber = it.phoneNumber,
                normalizedNumber = it.normalizedNumber,
                displayName = it.displayName,
                note = it.note,
                triggerAtMillis = it.triggerAtMillis,
                status = runCatching { ReminderStatus.valueOf(it.status) }.getOrDefault(ReminderStatus.PENDING)
            )
        }
    }

    val quickResponses: Flow<List<QuickResponse>> = db.quickResponseDao().observeAll().map { list ->
        list.map { QuickResponse(it.id, it.messageText, it.sortOrder, it.isBuiltIn) }
    }

    val privateContacts: Flow<List<PrivateContact>> = db.privateContactDao().observeAll().map { list ->
        list.map {
            PrivateContact(
                id = it.id,
                displayName = VaultCrypto.decrypt(it.displayName),
                phoneNumber = VaultCrypto.decrypt(it.phoneNumber),
                normalizedNumber = VaultCrypto.decrypt(it.normalizedNumber),
                notes = VaultCrypto.decrypt(it.notes),
                createdAt = it.createdAt
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    val automationRules: Flow<List<AutomationRule>> = db.automationRuleDao().observeAll().map { list ->
        list.map {
            AutomationRule(
                id = it.id,
                name = it.name,
                isEnabled = it.isEnabled,
                trigger = runCatching { AutomationTrigger.valueOf(it.triggerType) }
                    .getOrDefault(AutomationTrigger.MISSED_FAVORITE_CALL),
                action = runCatching { AutomationAction.valueOf(it.actionType) }
                    .getOrDefault(AutomationAction.CREATE_CALLBACK_REMINDER),
                paramValue = it.paramValue
            )
        }
    }

    val customThemes: Flow<List<CustomThemePreset>> = db.customThemeDao().observeAll().map { list ->
        list.mapNotNull { entity ->
            runCatching { json.decodeFromString<CustomThemePreset>(entity.themeJson) }.getOrNull()
        }
    }

    suspend fun ensureSeedDefaults() = withContext(Dispatchers.IO) {
        if (db.quickResponseDao().count() == 0) {
            db.quickResponseDao().insertAll(
                listOf(
                    QuickResponseEntity(messageText = "Can't talk right now. I'll call you back.", sortOrder = 0, isBuiltIn = true),
                    QuickResponseEntity(messageText = "In a meeting. Text me if urgent.", sortOrder = 1, isBuiltIn = true),
                    QuickResponseEntity(messageText = "Driving right now. Will call soon.", sortOrder = 2, isBuiltIn = true),
                    QuickResponseEntity(messageText = "একটু পরে কল করছি।", sortOrder = 3, isBuiltIn = true)
                )
            )
        }
        if (db.customThemeDao().count() == 0) {
            val presets = listOf(
                CustomThemePreset("aurora_teal", "Aurora Teal", true, "DARK", "#14B8A6", "#3B82F6", true, 0.88f, "SQUIRCLE", 22, "PLUS_JAKARTA"),
                CustomThemePreset("amoled_emerald", "AMOLED Emerald", true, "AMOLED", "#10B981", "#06B6D4", false, 0.94f, "CIRCLE", 28, "SPACE_GROTESK"),
                CustomThemePreset("solar_coral", "Solar Coral", true, "LIGHT", "#F97316", "#EC4899", true, 0.92f, "ROUNDED_RECT", 18, "PLUS_JAKARTA"),
                CustomThemePreset("midnight_amber", "Midnight Amber", true, "DARK", "#F59E0B", "#EF4444", true, 0.86f, "PILL", 24, "JETBRAINS_MONO")
            )
            val now = System.currentTimeMillis()
            db.customThemeDao().insertAll(
                presets.map {
                    CustomThemeEntity(
                        id = it.id,
                        name = it.name,
                        isBuiltIn = true,
                        themeJson = json.encodeToString(it),
                        createdAt = now,
                        updatedAt = now
                    )
                }
            )
        }
    }

    suspend fun saveSpeedDial(slot: Int, name: String, number: String, lookupKey: String? = null) =
        withContext(Dispatchers.IO) {
            db.speedDialDao().upsert(
                SpeedDialEntity(
                    slot = slot,
                    contactLookupKey = lookupKey,
                    displayName = name.ifBlank { number },
                    phoneNumber = number,
                    normalizedNumber = PhoneFormatUtils.toE164OrNormalized(number),
                    simAccountId = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

    suspend fun clearSpeedDial(slot: Int) = withContext(Dispatchers.IO) {
        db.speedDialDao().deleteSlot(slot)
    }

    suspend fun addBlockRule(rule: BlockRule): Long = withContext(Dispatchers.IO) {
        val norm = if (rule.ruleType == BlockRuleType.EXACT) {
            PhoneFormatUtils.toE164OrNormalized(rule.pattern)
        } else {
            T9Utils.digitsOnly(rule.pattern)
        }
        var syncedToSystem = false
        if (rule.ruleType == BlockRuleType.EXACT &&
            PermissionHelper.isDefaultDialer(context) &&
            runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)
        ) {
            try {
                val values = ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, rule.pattern)
                }
                context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
                syncedToSystem = true
            } catch (_: Exception) {
            }
        }
        db.blockRuleDao().upsert(
            BlockRuleEntity(
                id = rule.id,
                ruleType = rule.ruleType.name,
                pattern = rule.pattern,
                normalizedPattern = norm,
                label = rule.label,
                action = rule.action.name,
                skipCallLog = rule.skipCallLog,
                skipNotification = rule.skipNotification,
                isEnabled = rule.isEnabled,
                syncedToSystemBlocked = syncedToSystem,
                createdAt = rule.createdAt
            )
        )
    }

    suspend fun toggleBlockRule(rule: BlockRule) = withContext(Dispatchers.IO) {
        db.blockRuleDao().upsert(
            BlockRuleEntity(
                id = rule.id,
                ruleType = rule.ruleType.name,
                pattern = rule.pattern,
                normalizedPattern = PhoneFormatUtils.toE164OrNormalized(rule.pattern),
                label = rule.label,
                action = rule.action.name,
                skipCallLog = rule.skipCallLog,
                skipNotification = rule.skipNotification,
                isEnabled = !rule.isEnabled,
                syncedToSystemBlocked = rule.syncedToSystemBlocked,
                createdAt = rule.createdAt
            )
        )
    }

    suspend fun deleteBlockRule(rule: BlockRule) = withContext(Dispatchers.IO) {
        if (rule.syncedToSystemBlocked && PermissionHelper.isDefaultDialer(context)) {
            runCatching { BlockedNumberContract.unblock(context, rule.pattern) }
        }
        db.blockRuleDao().deleteById(rule.id)
    }

    suspend fun clearBlockedEvents() = withContext(Dispatchers.IO) {
        db.blockedCallEventDao().clearAll()
    }

    suspend fun saveCallNote(number: String, noteText: String, callLogId: Long? = null, lookupKey: String? = null) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            db.callNoteDao().upsert(
                CallNoteEntity(
                    callLogId = callLogId,
                    normalizedNumber = PhoneFormatUtils.toE164OrNormalized(number),
                    contactLookupKey = lookupKey,
                    noteText = noteText.trim(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

    suspend fun deleteCallNote(id: Long) = withContext(Dispatchers.IO) {
        db.callNoteDao().deleteById(id)
    }

    suspend fun scheduleReminder(
        displayName: String,
        phoneNumber: String,
        note: String,
        delayMinutes: Long
    ) = withContext(Dispatchers.IO) {
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val id = db.callReminderDao().upsert(
            CallReminderEntity(
                phoneNumber = phoneNumber,
                normalizedNumber = PhoneFormatUtils.toE164OrNormalized(phoneNumber),
                displayName = displayName.ifBlank { phoneNumber },
                note = note.trim(),
                triggerAtMillis = triggerAt,
                status = ReminderStatus.PENDING.name,
                workRequestUuid = null
            )
        )
        val input = Data.Builder()
            .putLong(CallReminderWorker.KEY_REMINDER_ID, id)
            .putString(CallReminderWorker.KEY_DISPLAY_NAME, displayName)
            .putString(CallReminderWorker.KEY_PHONE_NUMBER, phoneNumber)
            .putString(CallReminderWorker.KEY_NOTE, note)
            .build()
        val request = OneTimeWorkRequestBuilder<CallReminderWorker>()
            .setInitialDelay(delayMinutes.coerceAtLeast(1), TimeUnit.MINUTES)
            .setInputData(input)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        db.callReminderDao().updateWorkUuid(id, request.id.toString())
    }

    suspend fun deleteReminder(id: Long) = withContext(Dispatchers.IO) {
        db.callReminderDao().deleteById(id)
    }

    suspend fun addQuickResponse(message: String) = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext
        db.quickResponseDao().upsert(
            QuickResponseEntity(
                messageText = message.trim(),
                sortOrder = 99,
                isBuiltIn = false
            )
        )
    }

    suspend fun deleteQuickResponse(id: Long) = withContext(Dispatchers.IO) {
        db.quickResponseDao().deleteById(id)
    }

    suspend fun addPrivateContact(name: String, phone: String, notes: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.privateContactDao().upsert(
            PrivateContactEntity(
                displayName = VaultCrypto.encrypt(name.trim()),
                phoneNumber = VaultCrypto.encrypt(phone.trim()),
                normalizedNumber = VaultCrypto.encrypt(PhoneFormatUtils.toE164OrNormalized(phone)),
                notes = VaultCrypto.encrypt(notes.trim()),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deletePrivateContact(id: Long) = withContext(Dispatchers.IO) {
        db.privateContactDao().deleteById(id)
    }

    suspend fun addAutomationRule(rule: AutomationRule) = withContext(Dispatchers.IO) {
        db.automationRuleDao().upsert(
            AutomationRuleEntity(
                id = rule.id,
                name = rule.name,
                isEnabled = rule.isEnabled,
                triggerType = rule.trigger.name,
                actionType = rule.action.name,
                paramValue = rule.paramValue,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleAutomationRule(rule: AutomationRule) = withContext(Dispatchers.IO) {
        db.automationRuleDao().upsert(
            AutomationRuleEntity(
                id = rule.id,
                name = rule.name,
                isEnabled = !rule.isEnabled,
                triggerType = rule.trigger.name,
                actionType = rule.action.name,
                paramValue = rule.paramValue,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteAutomationRule(id: Long) = withContext(Dispatchers.IO) {
        db.automationRuleDao().deleteById(id)
    }

    suspend fun saveCustomTheme(preset: CustomThemePreset) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = preset.id.ifBlank { UUID.randomUUID().toString() }
        val finalPreset = preset.copy(id = id, isBuiltIn = false)
        db.customThemeDao().upsert(
            CustomThemeEntity(
                id = id,
                name = finalPreset.name,
                isBuiltIn = false,
                themeJson = json.encodeToString(finalPreset),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteCustomTheme(id: String) = withContext(Dispatchers.IO) {
        db.customThemeDao().deleteCustomById(id)
    }

    fun exportThemePresetJson(preset: CustomThemePreset): String = json.encodeToString(preset)

    fun importThemePresetJson(raw: String): CustomThemePreset? =
        runCatching { json.decodeFromString<CustomThemePreset>(raw) }.getOrNull()

    suspend fun exportFullBackupJson(): String = withContext(Dispatchers.IO) {
        val config = prefsRepo.getConfigOnce()
        val speeds = db.speedDialDao().getAllOnce().map {
            SpeedDialEntry(it.slot, it.contactLookupKey, it.displayName, it.phoneNumber, it.normalizedNumber, it.simAccountId)
        }
        val blocks = db.blockRuleDao().getAllOnce().map {
            BlockRule(
                id = it.id,
                ruleType = runCatching { BlockRuleType.valueOf(it.ruleType) }.getOrDefault(BlockRuleType.EXACT),
                pattern = it.pattern,
                label = it.label,
                action = runCatching { BlockAction.valueOf(it.action) }.getOrDefault(BlockAction.REJECT),
                skipCallLog = it.skipCallLog,
                skipNotification = it.skipNotification,
                isEnabled = it.isEnabled,
                syncedToSystemBlocked = it.syncedToSystemBlocked,
                createdAt = it.createdAt
            )
        }
        val notes = db.callNoteDao().getAllOnce().map {
            CallNote(it.id, it.callLogId, it.normalizedNumber, it.contactLookupKey, it.noteText, it.createdAt, it.updatedAt)
        }
        val quicks = db.quickResponseDao().getAllOnce().map {
            QuickResponse(it.id, it.messageText, it.sortOrder, it.isBuiltIn)
        }
        val privates = db.privateContactDao().getAllOnce().map {
            PrivateContact(
                it.id,
                VaultCrypto.decrypt(it.displayName),
                VaultCrypto.decrypt(it.phoneNumber),
                VaultCrypto.decrypt(it.normalizedNumber),
                VaultCrypto.decrypt(it.notes),
                it.createdAt
            )
        }
        val autos = db.automationRuleDao().getEnabledOnce().map {
            AutomationRule(
                id = it.id,
                name = it.name,
                isEnabled = it.isEnabled,
                trigger = runCatching { AutomationTrigger.valueOf(it.triggerType) }
                    .getOrDefault(AutomationTrigger.MISSED_FAVORITE_CALL),
                action = runCatching { AutomationAction.valueOf(it.actionType) }
                    .getOrDefault(AutomationAction.CREATE_CALLBACK_REMINDER),
                paramValue = it.paramValue
            )
        }
        val payload = DialMeBackupPayload(
            config = config,
            speedDials = speeds,
            blockRules = blocks,
            callNotes = notes,
            quickResponses = quicks,
            privateContacts = privates,
            automationRules = autos,
            customThemes = emptyList()
        )
        json.encodeToString(payload)
    }

    suspend fun importFullBackupJson(rawJson: String): Boolean = withContext(Dispatchers.IO) {
        val payload = runCatching { json.decodeFromString<DialMeBackupPayload>(rawJson) }.getOrNull()
            ?: return@withContext false
        prefsRepo.replaceConfig(payload.config)
        db.speedDialDao().clearAll()
        payload.speedDials.forEach {
            saveSpeedDial(it.slot, it.displayName, it.phoneNumber, it.contactLookupKey)
        }
        db.blockRuleDao().clearAll()
        payload.blockRules.forEach {
            addBlockRule(it.copy(id = 0))
        }
        db.callNoteDao().clearAll()
        payload.callNotes.forEach {
            saveCallNote(it.normalizedNumber, it.noteText, it.callLogId, it.contactLookupKey)
        }
        db.privateContactDao().clearAll()
        payload.privateContacts.forEach {
            addPrivateContact(it.displayName, it.phoneNumber, it.notes)
        }
        true
    }
}
