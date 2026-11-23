package com.example.domain.model

import kotlinx.serialization.Serializable

data class ContactItem(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val phoneNumbers: List<PhoneEntry>,
    val photoUri: String? = null,
    val isStarred: Boolean = false,
    val customRingtoneUri: String? = null,
    val groupNames: List<String> = emptyList(),
    val accountType: String? = null,
    val customVibrationLabel: String? = null,
    val localSpamFlag: SpamFlag = SpamFlag.NONE
) {
    val primaryNumber: String
        get() = phoneNumbers.firstOrNull()?.number.orEmpty()
}

data class PhoneEntry(
    val number: String,
    val normalizedNumber: String,
    val typeLabel: String = "Mobile"
)

data class ContactGroup(
    val id: Long,
    val title: String,
    val memberCount: Int = 0
)

data class DuplicateGroup(
    val key: String,
    val reason: String,
    val contacts: List<ContactItem>
)

data class T9MatchResult(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val photoUri: String?,
    val isStarred: Boolean,
    val score: Int
)

enum class CallDirection {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    VOICEMAIL
}

enum class CallTypeFilter {
    ALL,
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED
}

enum class CallerVerificationStatus {
    NOT_VERIFIED,
    PASSED,
    FAILED
}

enum class SpamFlag {
    NONE,
    SUSPECTED,
    SPAM
}

data class CallLogItem(
    val id: Long,
    val number: String,
    val normalizedNumber: String,
    val cachedName: String?,
    val contactLookupKey: String?,
    val photoUri: String?,
    val direction: CallDirection,
    val timestampMillis: Long,
    val durationSeconds: Long,
    val simSlotLabel: String? = null,
    val phoneAccountId: String? = null,
    val hasNote: Boolean = false
)

data class SimAccount(
    val id: String,
    val label: String,
    val carrierName: String,
    val phoneNumber: String,
    val slotIndex: Int,
    val colorArgb: Int
)

@Serializable
data class SpeedDialEntry(
    val slot: Int,
    val contactLookupKey: String? = null,
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val simAccountId: String? = null
)

@Serializable
enum class BlockRuleType {
    EXACT,
    PREFIX,
    REGEX,
    UNKNOWN_HIDDEN,
    NON_CONTACT
}

@Serializable
enum class BlockAction {
    REJECT,
    SILENCE
}

@Serializable
data class BlockRule(
    val id: Long = 0,
    val ruleType: BlockRuleType,
    val pattern: String,
    val label: String = "",
    val action: BlockAction = BlockAction.REJECT,
    val skipCallLog: Boolean = false,
    val skipNotification: Boolean = true,
    val isEnabled: Boolean = true,
    val syncedToSystemBlocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class BlockedCallEvent(
    val id: Long = 0,
    val phoneNumber: String,
    val normalizedNumber: String,
    val matchedRuleId: Long?,
    val reason: String,
    val actionTaken: BlockAction,
    val timestamp: Long
)

@Serializable
data class CallNote(
    val id: Long = 0,
    val callLogId: Long? = null,
    val normalizedNumber: String,
    val contactLookupKey: String? = null,
    val noteText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class ReminderStatus {
    PENDING,
    FIRED,
    SNOOZED,
    COMPLETED,
    DISMISSED
}

@Serializable
data class CallReminder(
    val id: Long = 0,
    val phoneNumber: String,
    val normalizedNumber: String,
    val displayName: String,
    val note: String = "",
    val triggerAtMillis: Long,
    val status: ReminderStatus = ReminderStatus.PENDING
)

@Serializable
data class QuickResponse(
    val id: Long = 0,
    val messageText: String,
    val sortOrder: Int = 0,
    val isBuiltIn: Boolean = false
)

@Serializable
data class PrivateContact(
    val id: Long = 0,
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class AutomationTrigger {
    MISSED_FAVORITE_CALL,
    QUIET_HOURS_INCOMING,
    UNKNOWN_CALLER_INCOMING
}

@Serializable
enum class AutomationAction {
    CREATE_CALLBACK_REMINDER,
    CUSTOM_VIBRATION_ALERT,
    SILENCE_RING
}

@Serializable
data class AutomationRule(
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val trigger: AutomationTrigger,
    val action: AutomationAction,
    val paramValue: String = ""
)

data class ContactStatSummary(
    val displayName: String,
    val phoneNumber: String,
    val callCount: Int,
    val totalDurationSeconds: Long
)

data class CallStatistics(
    val totalCalls: Int = 0,
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val missedCount: Int = 0,
    val rejectedCount: Int = 0,
    val totalTalkSeconds: Long = 0,
    val averageTalkSeconds: Long = 0,
    val topContacts: List<ContactStatSummary> = emptyList(),
    val simDistribution: Map<String, Int> = emptyMap(),
    val hourlyCounts: List<Int> = List(24) { 0 }
)

enum class CallStateType {
    IDLE,
    RINGING,
    DIALING,
    CONNECTING,
    ACTIVE,
    HOLDING,
    DISCONNECTING,
    DISCONNECTED
}

enum class AudioRouteType {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET,
    STREAMING
}

data class AudioEndpointItem(
    val id: String,
    val label: String,
    val routeType: AudioRouteType,
    val isSelected: Boolean
)

data class ActiveCallSession(
    val callId: String,
    val number: String,
    val displayName: String?,
    val photoUri: String?,
    val isIncoming: Boolean,
    val isUnknownPresentation: Boolean,
    val state: CallStateType,
    val connectTimeMillis: Long,
    val canHold: Boolean,
    val canMerge: Boolean,
    val canSwap: Boolean,
    val isConference: Boolean,
    val simLabel: String?,
    val verificationStatus: CallerVerificationStatus = CallerVerificationStatus.NOT_VERIFIED
)

data class InCallUiState(
    val calls: List<ActiveCallSession> = emptyList(),
    val primaryCall: ActiveCallSession? = null,
    val secondaryCall: ActiveCallSession? = null,
    val isMuted: Boolean = false,
    val activeAudioRoute: AudioRouteType = AudioRouteType.EARPIECE,
    val availableAudioEndpoints: List<AudioEndpointItem> = emptyList()
)

@Serializable
data class CustomThemePreset(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean = false,
    val themeMode: String = "DARK",
    val accentHex: String = "#14B8A6",
    val secondaryHex: String = "#3B82F6",
    val useGradient: Boolean = true,
    val cardAlpha: Float = 0.88f,
    val buttonShape: String = "SQUIRCLE",
    val cornerRadiusDp: Int = 20,
    val fontFamilyKey: String = "PLUS_JAKARTA"
)
