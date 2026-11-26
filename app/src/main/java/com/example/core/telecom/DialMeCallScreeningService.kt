package com.example.core.telecom

import android.Manifest
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.example.core.common.T9Utils
import com.example.core.database.BlockedCallEventEntity
import com.example.core.database.DialMeDatabase
import com.example.core.permissions.PermissionHelper
import com.example.domain.model.BlockAction
import com.example.domain.model.BlockRuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class DialMeCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val isUnknownHidden = rawNumber.isBlank() ||
            callDetails.handlePresentation != android.telecom.TelecomManager.PRESENTATION_ALLOWED
        val normalized = T9Utils.normalizeDigitsOnly(rawNumber)
        val digitsOnly = T9Utils.digitsOnly(rawNumber)

        scope.launch {
            try {
                screenCall(callDetails, rawNumber, isUnknownHidden, normalized, digitsOnly)
            } catch (e: Exception) {
                // Never leave the system waiting: on any failure let the call through.
                runCatching { respondToCall(callDetails, CallResponse.Builder().build()) }
            }
        }
    }

    private suspend fun screenCall(
        callDetails: Call.Details,
        rawNumber: String,
        isUnknownHidden: Boolean,
        normalized: String,
        digitsOnly: String
    ) {
        run {
            val db = DialMeDatabase.build(applicationContext)
            val rules = db.blockRuleDao().getEnabledRulesOnce()
            val matched = rules.firstOrNull { rule ->
                when (runCatching { BlockRuleType.valueOf(rule.ruleType) }.getOrNull()) {
                    BlockRuleType.UNKNOWN_HIDDEN -> isUnknownHidden
                    BlockRuleType.EXACT -> {
                        val ruleDigits = T9Utils.digitsOnly(rule.pattern)
                        digitsOnly.isNotEmpty() && (digitsOnly == ruleDigits || normalized == rule.normalizedPattern)
                    }
                    BlockRuleType.PREFIX -> {
                        val prefixDigits = T9Utils.digitsOnly(rule.pattern)
                        prefixDigits.isNotEmpty() &&
                            (digitsOnly.startsWith(prefixDigits) || normalized.startsWith(rule.pattern))
                    }
                    BlockRuleType.REGEX -> {
                        rule.pattern.length <= MAX_REGEX_LENGTH &&
                            runCatching { Regex(rule.pattern).containsMatchIn(rawNumber.take(MAX_NUMBER_LENGTH)) }
                                .getOrDefault(false)
                    }
                    BlockRuleType.NON_CONTACT -> {
                        !isKnownSystemContact(rawNumber)
                    }
                    null -> false
                }
            }

            if (matched != null) {
                val action = runCatching { BlockAction.valueOf(matched.action) }
                    .getOrDefault(BlockAction.REJECT)
                db.blockedCallEventDao().insert(
                    BlockedCallEventEntity(
                        phoneNumber = rawNumber.ifBlank { "Private Number" },
                        normalizedNumber = normalized,
                        matchedRuleId = matched.id,
                        reason = matched.label.ifBlank { matched.ruleType },
                        actionTaken = action.name,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val response = CallResponse.Builder()
                    .setDisallowCall(action == BlockAction.REJECT)
                    .setRejectCall(action == BlockAction.REJECT)
                    .setSilenceCall(true)
                    .setSkipCallLog(matched.skipCallLog)
                    .setSkipNotification(matched.skipNotification)
                    .build()
                respondToCall(callDetails, response)
            } else {
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
    }

    private companion object {
        const val MAX_REGEX_LENGTH = 100
        const val MAX_NUMBER_LENGTH = 64
    }

    private fun isKnownSystemContact(number: String): Boolean {
        if (number.isBlank() || !PermissionHelper.has(this, Manifest.permission.READ_CONTACTS)) {
            return true
        }
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        return try {
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use {
                it.moveToFirst()
            } ?: false
        } catch (_: Exception) {
            true
        }
    }
}
