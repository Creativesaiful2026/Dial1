package com.example.data

import android.Manifest
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import com.example.core.common.DateTimeUtils
import com.example.core.common.PhoneFormatUtils
import com.example.core.database.CallNoteDao
import com.example.core.permissions.PermissionHelper
import com.example.domain.model.CallDirection
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallStatistics
import com.example.domain.model.ContactStatSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Calendar

class CallHistoryRepository(
    private val context: Context,
    private val callNoteDao: CallNoteDao
) {

    private fun observeCallLogChanges(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        if (!PermissionHelper.has(context, Manifest.permission.READ_CALL_LOG)) {
            awaitClose { }
            return@callbackFlow
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            observer
        )
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    fun observeCallHistory(): Flow<List<CallLogItem>> {
        return combine(
            observeCallLogChanges(),
            callNoteDao.observeAll()
        ) { _, notes ->
            val noteNumbers = notes.map { it.normalizedNumber }.toSet()
            loadFromProvider(noteNumbers)
        }.flowOn(Dispatchers.IO)
    }

    private fun loadFromProvider(noteNumbers: Set<String>): List<CallLogItem> {
        if (!PermissionHelper.has(context, Manifest.permission.READ_CALL_LOG)) {
            return emptyList()
        }
        val items = mutableListOf<CallLogItem>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_LOOKUP_URI,
            CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idxId = cursor.getColumnIndex(CallLog.Calls._ID)
                val idxNum = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val idxName = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val idxLookup = cursor.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)
                val idxPhoto = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                val idxType = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val idxDate = cursor.getColumnIndex(CallLog.Calls.DATE)
                val idxDur = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val idxAccount = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    count++
                    val rawNumber = cursor.getString(idxNum).orEmpty()
                    val normalized = PhoneFormatUtils.toE164OrNormalized(rawNumber)
                    val direction = when (cursor.getInt(idxType)) {
                        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallDirection.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallDirection.BLOCKED
                        CallLog.Calls.VOICEMAIL_TYPE -> CallDirection.VOICEMAIL
                        else -> CallDirection.INCOMING
                    }
                    val acctId = if (idxAccount >= 0) cursor.getString(idxAccount) else null
                    items += CallLogItem(
                        id = cursor.getLong(idxId),
                        number = rawNumber,
                        normalizedNumber = normalized,
                        cachedName = cursor.getString(idxName)?.takeIf { it.isNotBlank() },
                        contactLookupKey = cursor.getString(idxLookup),
                        photoUri = cursor.getString(idxPhoto),
                        direction = direction,
                        timestampMillis = cursor.getLong(idxDate),
                        durationSeconds = cursor.getLong(idxDur),
                        simSlotLabel = acctId?.takeLast(4),
                        phoneAccountId = acctId,
                        hasNote = noteNumbers.contains(normalized)
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return items
    }

    suspend fun deleteCallLogs(ids: Set<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty() || !PermissionHelper.has(context, Manifest.permission.WRITE_CALL_LOG)) {
            return@withContext 0
        }
        var deleted = 0
        val resolver = context.contentResolver
        ids.chunked(50).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val args = chunk.map { it.toString() }.toTypedArray()
            try {
                deleted += resolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} IN ($placeholders)",
                    args
                )
            } catch (_: Exception) {
            }
        }
        deleted
    }

    fun computeStatistics(items: List<CallLogItem>): CallStatistics {
        if (items.isEmpty()) return CallStatistics()
        var incoming = 0
        var outgoing = 0
        var missed = 0
        var rejected = 0
        var totalTalk = 0L
        val hourly = IntArray(24)
        val simCounts = mutableMapOf<String, Int>()
        val contactAgg = mutableMapOf<String, Pair<String, MutableList<Long>>>()

        val cal = Calendar.getInstance()
        items.forEach { item ->
            when (item.direction) {
                CallDirection.INCOMING -> incoming++
                CallDirection.OUTGOING -> outgoing++
                CallDirection.MISSED -> missed++
                CallDirection.REJECTED, CallDirection.BLOCKED -> rejected++
                CallDirection.VOICEMAIL -> {}
            }
            totalTalk += item.durationSeconds
            cal.timeInMillis = item.timestampMillis
            hourly[cal.get(Calendar.HOUR_OF_DAY)]++

            val simKey = item.simSlotLabel?.let { "SIM ($it)" } ?: "Primary SIM"
            simCounts[simKey] = (simCounts[simKey] ?: 0) + 1

            val key = item.normalizedNumber.ifBlank { item.number }
            if (key.isNotBlank()) {
                val label = item.cachedName ?: item.number
                val pair = contactAgg.getOrPut(key) { label to mutableListOf() }
                pair.second.add(item.durationSeconds)
            }
        }

        val topContacts = contactAgg.entries.map { (num, pair) ->
            ContactStatSummary(
                displayName = pair.first,
                phoneNumber = num,
                callCount = pair.second.size,
                totalDurationSeconds = pair.second.sum()
            )
        }.sortedByDescending { it.callCount }.take(6)

        val answeredCount = (incoming + outgoing).coerceAtLeast(1)
        return CallStatistics(
            totalCalls = items.size,
            incomingCount = incoming,
            outgoingCount = outgoing,
            missedCount = missed,
            rejectedCount = rejected,
            totalTalkSeconds = totalTalk,
            averageTalkSeconds = totalTalk / answeredCount,
            topContacts = topContacts,
            simDistribution = simCounts,
            hourlyCounts = hourly.toList()
        )
    }

    fun exportCsv(items: List<CallLogItem>): String {
        return buildString {
            appendLine("DateTime,Name,Number,Direction,DurationSeconds")
            items.forEach { item ->
                val dt = DateTimeUtils.formatFullDateTime(item.timestampMillis).replace(",", " ")
                val name = (item.cachedName ?: "Unknown").replace(",", " ")
                appendLine("$dt,$name,${item.number},${item.direction.name},${item.durationSeconds}")
            }
        }
    }
}
