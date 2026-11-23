package com.example.core.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.example.R
import com.example.domain.model.ActiveCallSession
import com.example.domain.model.CallStateType
import com.example.feature.incall.InCallActivity

object CallNotificationManager {

    const val CHANNEL_INCOMING_CALLS = "dialme_incoming_calls"
    const val CHANNEL_ONGOING_CALLS = "dialme_ongoing_calls"
    const val CHANNEL_MISSED_CALLS = "dialme_missed_calls"
    const val CHANNEL_REMINDERS = "dialme_call_reminders"

    const val CALL_NOTIFICATION_ID = 4001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val incoming = NotificationChannel(
            CHANNEL_INCOMING_CALLS,
            "Incoming SIM Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen incoming call alerts"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val ongoing = NotificationChannel(
            CHANNEL_ONGOING_CALLS,
            "Ongoing Calls",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Active and held call controls"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val missed = NotificationChannel(
            CHANNEL_MISSED_CALLS,
            "Missed Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for missed calls"
        }

        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            "Call Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Scheduled callback reminders"
        }

        nm.createNotificationChannels(listOf(incoming, ongoing, missed, reminders))
    }

    fun buildCallNotification(context: Context, session: ActiveCallSession): Notification {
        ensureChannels(context)
        val isRinging = session.state == CallStateType.RINGING
        val channelId = if (isRinging) CHANNEL_INCOMING_CALLS else CHANNEL_ONGOING_CALLS

        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            100,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = PendingIntent.getBroadcast(
            context,
            101,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_REJECT
                putExtra(CallActionReceiver.EXTRA_CALL_ID, session.callId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = PendingIntent.getBroadcast(
            context,
            102,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_ANSWER
                putExtra(CallActionReceiver.EXTRA_CALL_ID, session.callId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = PendingIntent.getBroadcast(
            context,
            103,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_HANGUP
                putExtra(CallActionReceiver.EXTRA_CALL_ID, session.callId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerName = session.displayName?.takeIf { it.isNotBlank() }
            ?: session.number.ifBlank { "Unknown Caller" }

        val callerPerson = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val style = if (isRinging) {
            NotificationCompat.CallStyle.forIncomingCall(callerPerson, declineIntent, answerIntent)
        } else {
            NotificationCompat.CallStyle.forOngoingCall(callerPerson, hangupIntent)
        }

        val statusText = when (session.state) {
            CallStateType.RINGING -> context.getString(R.string.incall_incoming)
            CallStateType.DIALING -> context.getString(R.string.incall_dialing)
            CallStateType.CONNECTING -> context.getString(R.string.incall_connecting)
            CallStateType.HOLDING -> context.getString(R.string.incall_on_hold)
            else -> session.number
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(callerName)
            .setContentText(statusText)
            .setStyle(style)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPending)
            .setPriority(if (isRinging) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)

        if (isRinging) {
            builder.setFullScreenIntent(contentPending, true)
        }
        if (session.state == CallStateType.ACTIVE && session.connectTimeMillis > 0L) {
            builder.setWhen(session.connectTimeMillis).setUsesChronometer(true)
        }
        return builder.build()
    }
}
