package com.example.core.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID)
        when (intent.action) {
            ACTION_ANSWER -> TelecomCallRepository.answer(callId)
            ACTION_REJECT -> TelecomCallRepository.reject(callId)
            ACTION_HANGUP -> TelecomCallRepository.disconnect(callId)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.example.dialme.ACTION_ANSWER_CALL"
        const val ACTION_REJECT = "com.example.dialme.ACTION_REJECT_CALL"
        const val ACTION_HANGUP = "com.example.dialme.ACTION_HANGUP_CALL"
        const val EXTRA_CALL_ID = "extra_call_id"
    }
}
