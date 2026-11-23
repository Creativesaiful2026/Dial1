package com.example.core.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.example.core.permissions.PermissionHelper
import com.example.domain.model.SimAccount

class TelecomCallPlacer(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun getSimAccounts(): List<SimAccount> {
        if (!PermissionHelper.has(context, android.Manifest.permission.READ_PHONE_STATE)) {
            return emptyList()
        }
        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        val subManager = context.getSystemService(SubscriptionManager::class.java)
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)

        val handles: List<PhoneAccountHandle> = try {
            telecomManager.callCapablePhoneAccounts
        } catch (_: SecurityException) {
            emptyList()
        }

        val activeSubs: List<SubscriptionInfo> = try {
            subManager?.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }

        return handles.mapIndexed { index, handle ->
            val account = try {
                telecomManager.getPhoneAccount(handle)
            } catch (_: SecurityException) {
                null
            }

            val resolvedSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && telephonyManager != null) {
                try {
                    telephonyManager.getSubscriptionId(handle)
                } catch (_: Exception) {
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                }
            } else {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }

            val matchedSub = activeSubs.firstOrNull { sub ->
                (resolvedSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && sub.subscriptionId == resolvedSubId) ||
                    handle.id == sub.subscriptionId.toString() ||
                    handle.id.contains(sub.subscriptionId.toString()) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !sub.iccId.isNullOrBlank() && handle.id.contains(sub.iccId))
            } ?: activeSubs.getOrNull(index)

            val slotIndex = (matchedSub?.simSlotIndex ?: index) + 1
            val carrier = matchedSub?.carrierName?.toString()?.takeIf { it.isNotBlank() }
                ?: matchedSub?.displayName?.toString()?.takeIf { it.isNotBlank() }
                ?: account?.label?.toString()?.takeIf { it.isNotBlank() }
                ?: "SIM $slotIndex"
            @Suppress("DEPRECATION")
            val ownNumber = matchedSub?.number.orEmpty()
            val color = matchedSub?.iconTint?.takeIf { it != 0 }
                ?: if (slotIndex == 1) 0xFF14B8A6.toInt() else 0xFF3B82F6.toInt()

            SimAccount(
                id = encodeHandleKey(handle),
                label = "SIM $slotIndex · $carrier",
                carrierName = carrier,
                phoneNumber = ownNumber,
                slotIndex = slotIndex,
                colorArgb = color
            )
        }
            .distinctBy { it.id }
            .sortedBy { it.slotIndex }
    }

    @SuppressLint("MissingPermission")
    fun placeCall(rawNumber: String, preferredSimAccountId: String? = null): Boolean {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) return false
        val uri = Uri.fromParts("tel", trimmed, null)

        val hasCallPhone = PermissionHelper.has(context, android.Manifest.permission.CALL_PHONE)
        val telecomManager = context.getSystemService(TelecomManager::class.java)

        if (hasCallPhone && telecomManager != null) {
            val extras = Bundle()
            if (!preferredSimAccountId.isNullOrBlank() &&
                PermissionHelper.has(context, android.Manifest.permission.READ_PHONE_STATE)
            ) {
                val handle = try {
                    telecomManager.callCapablePhoneAccounts.firstOrNull {
                        encodeHandleKey(it) == preferredSimAccountId || it.id == preferredSimAccountId
                    }
                } catch (_: SecurityException) {
                    null
                }
                if (handle != null) {
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
            }
            return try {
                telecomManager.placeCall(uri, extras)
                true
            } catch (_: SecurityException) {
                fallbackActionDial(uri)
            }
        }
        return fallbackActionDial(uri)
    }

    private fun encodeHandleKey(handle: PhoneAccountHandle): String =
        "${handle.componentName.flattenToShortString()}|${handle.id}"

    private fun fallbackActionDial(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
