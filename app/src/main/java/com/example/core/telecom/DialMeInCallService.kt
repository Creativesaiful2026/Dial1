package com.example.core.telecom

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import com.example.feature.incall.InCallActivity

class DialMeInCallService : InCallService() {

    private val callCallbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.ensureChannels(this)
        TelecomCallRepository.attachService(this)
    }

    override fun onDestroy() {
        callCallbacks.forEach { (call, cb) -> call.unregisterCallback(cb) }
        callCallbacks.clear()
        TelecomCallRepository.attachService(null)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                TelecomCallRepository.updateCall(call)
                refreshForegroundNotification()
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                TelecomCallRepository.updateCall(call)
                refreshForegroundNotification()
            }
        }
        callCallbacks[call] = callback
        call.registerCallback(callback)
        TelecomCallRepository.registerCall(call)
        refreshForegroundNotification()

        // Launch InCallActivity for incoming or outgoing calls
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callCallbacks.remove(call)?.let { call.unregisterCallback(it) }
        TelecomCallRepository.unregisterCall(call)
        if (TelecomCallRepository.uiState.value.calls.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            refreshForegroundNotification()
        }
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        TelecomCallRepository.onMuteChanged(isMuted)
    }

    @Deprecated("Deprecated in API 34")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        TelecomCallRepository.onLegacyAudioStateChanged(audioState)
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        TelecomCallRepository.onCallEndpointChanged(callEndpoint)
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        TelecomCallRepository.onAvailableEndpointsChanged(availableEndpoints)
    }

    private fun refreshForegroundNotification() {
        val primary = TelecomCallRepository.uiState.value.primaryCall ?: return
        val notification = CallNotificationManager.buildCallNotification(this, primary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                CallNotificationManager.CALL_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(CallNotificationManager.CALL_NOTIFICATION_ID, notification)
        }
    }
}
