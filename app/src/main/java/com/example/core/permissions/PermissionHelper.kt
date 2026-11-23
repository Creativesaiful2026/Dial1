package com.example.core.permissions

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

data class PermissionSnapshot(
    val isDefaultDialer: Boolean = false,
    val isCallScreeningHeld: Boolean = false,
    val hasCallPhone: Boolean = false,
    val hasReadPhoneState: Boolean = false,
    val hasReadPhoneNumbers: Boolean = false,
    val hasReadContacts: Boolean = false,
    val hasWriteContacts: Boolean = false,
    val hasReadCallLog: Boolean = false,
    val hasWriteCallLog: Boolean = false,
    val hasPostNotifications: Boolean = true,
    val hasBluetoothConnect: Boolean = true,
    val canUseFullScreenIntent: Boolean = true
)

object PermissionHelper {

    fun snapshot(context: Context): PermissionSnapshot {
        return PermissionSnapshot(
            isDefaultDialer = isDefaultDialer(context),
            isCallScreeningHeld = isCallScreeningHeld(context),
            hasCallPhone = has(context, Manifest.permission.CALL_PHONE),
            hasReadPhoneState = has(context, Manifest.permission.READ_PHONE_STATE),
            hasReadPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                has(context, Manifest.permission.READ_PHONE_NUMBERS)
            } else {
                has(context, Manifest.permission.READ_PHONE_STATE)
            },
            hasReadContacts = has(context, Manifest.permission.READ_CONTACTS),
            hasWriteContacts = has(context, Manifest.permission.WRITE_CONTACTS),
            hasReadCallLog = has(context, Manifest.permission.READ_CALL_LOG),
            hasWriteCallLog = has(context, Manifest.permission.WRITE_CALL_LOG),
            hasPostNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                has(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            },
            hasBluetoothConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                has(context, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                true
            },
            canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.canUseFullScreenIntent() ?: true
            } else {
                true
            }
        )
    }

    fun has(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isDefaultDialer(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            val tm = context.getSystemService(TelecomManager::class.java)
            tm?.defaultDialerPackage == context.packageName
        }
    }

    fun isCallScreeningHeld(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            false
        }
    }

    fun buildDefaultDialerIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                null
            }
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun buildCallScreeningRoleIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            } else {
                null
            }
        } else {
            null
        }
    }

    fun buildAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildFullScreenIntentSettings(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            buildAppSettingsIntent(context)
        }
    }

    fun coreDialerRuntimePermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            list.add(Manifest.permission.READ_PHONE_NUMBERS)
            list.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }
}
