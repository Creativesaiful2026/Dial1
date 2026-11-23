package com.example.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.core.database.DialMeDatabase
import com.example.core.telecom.CallNotificationManager
import com.example.domain.model.ReminderStatus

class CallReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        val displayName = inputData.getString(KEY_DISPLAY_NAME).orEmpty()
        val number = inputData.getString(KEY_PHONE_NUMBER).orEmpty()
        val note = inputData.getString(KEY_NOTE).orEmpty()

        CallNotificationManager.ensureChannels(applicationContext)

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DIAL_NUMBER, number)
        }
        val contentPending = PendingIntent.getActivity(
            applicationContext,
            reminderId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Call Reminder: ${displayName.ifBlank { number }}"
        val body = note.ifBlank { "Tap to call $number" }

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(applicationContext, CallNotificationManager.CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPending)
            .build()

        nm?.notify((5000 + reminderId).toInt(), notification)

        if (reminderId > 0L) {
            val db = DialMeDatabase.build(applicationContext)
            db.callReminderDao().updateStatus(reminderId, ReminderStatus.FIRED.name)
        }
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PHONE_NUMBER = "phone_number"
        const val KEY_NOTE = "note"
        const val EXTRA_DIAL_NUMBER = "extra_dial_number"
    }
}
