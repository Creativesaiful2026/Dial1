package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "t9_contact_cache",
    indices = [
        Index(value = ["lookupKey"]),
        Index(value = ["t9Digits"]),
        Index(value = ["normalizedNumber"])
    ]
)
data class T9ContactCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val t9Digits: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val photoUri: String?,
    val isStarred: Boolean,
    val lastSyncedAt: Long
)

@Entity(tableName = "speed_dial_entries")
data class SpeedDialEntity(
    @PrimaryKey val slot: Int,
    val contactLookupKey: String?,
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val simAccountId: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "blocked_rules",
    indices = [Index(value = ["normalizedPattern"])]
)
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleType: String,
    val pattern: String,
    val normalizedPattern: String,
    val label: String,
    val action: String,
    val skipCallLog: Boolean,
    val skipNotification: Boolean,
    val isEnabled: Boolean,
    val syncedToSystemBlocked: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "blocked_call_events",
    foreignKeys = [
        ForeignKey(
            entity = BlockRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchedRuleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["matchedRuleId"]),
        Index(value = ["timestamp"])
    ]
)
data class BlockedCallEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val normalizedNumber: String,
    val matchedRuleId: Long?,
    val reason: String,
    val actionTaken: String,
    val timestamp: Long
)

@Entity(
    tableName = "call_notes",
    indices = [Index(value = ["normalizedNumber"])]
)
data class CallNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callLogId: Long?,
    val normalizedNumber: String,
    val contactLookupKey: String?,
    val noteText: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "call_reminders",
    indices = [Index(value = ["triggerAtMillis"])]
)
data class CallReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val normalizedNumber: String,
    val displayName: String,
    val note: String,
    val triggerAtMillis: Long,
    val status: String,
    val workRequestUuid: String?
)

@Entity(tableName = "quick_responses")
data class QuickResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageText: String,
    val sortOrder: Int,
    val isBuiltIn: Boolean
)

@Entity(tableName = "contact_extensions")
data class ContactExtensionEntity(
    @PrimaryKey val contactLookupKey: String,
    val customVibrationPatternJson: String?,
    val isHiddenFromMainList: Boolean,
    val localSpamFlag: String,
    val spamNote: String?,
    val updatedAt: Long
)

@Entity(tableName = "private_contacts")
data class PrivateContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isEnabled: Boolean,
    val triggerType: String,
    val actionType: String,
    val paramValue: String,
    val createdAt: Long
)

@Entity(tableName = "custom_themes")
data class CustomThemeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val themeJson: String,
    val createdAt: Long,
    val updatedAt: Long
)
