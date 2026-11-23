package com.example.core.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateTimeUtils {
    fun formatCallDuration(seconds: Long): String {
        if (seconds <= 0L) return "00:00"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatDurationReadable(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "0s"
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return buildString {
            if (hrs > 0) append("${hrs}h ")
            if (mins > 0) append("${mins}m ")
            if (hrs == 0L && (secs > 0 || mins == 0L)) append("${secs}s")
        }.trim()
    }

    fun formatTimeShort(timestampMillis: Long): String {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(Date(timestampMillis))
    }

    fun formatDateBucket(timestampMillis: Long): String {
        val calNow = Calendar.getInstance()
        val calThen = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val sameDay = calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return "Today"

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) return "Yesterday"

        val daysDiff = TimeUnit.MILLISECONDS.toDays(calNow.timeInMillis - timestampMillis)
        return if (daysDiff in 0..6) {
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestampMillis))
        } else {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMillis))
        }
    }

    fun formatFullDateTime(timestampMillis: Long): String {
        return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(timestampMillis))
    }
}
