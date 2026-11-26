package com.example.core.telecom

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import com.example.core.datastore.IncomingCallPresentation
import com.example.feature.incall.InCallActivity
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DialMeInCallService : InCallService() {

    private val callCallbacks = mutableMapOf<Call, Call.Callback>()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
    )

    @Volatile
    private var headsUpWhenUnlocked = false

    override fun onCreate() {
        super.onCreate()
        val container = (application as com.example.DialMeApplication).container
        serviceScope.launch {
            container.preferencesRepository.configFlow.collect { cfg ->
                headsUpWhenUnlocked =
                    cfg.incomingCallPresentation == IncomingCallPresentation.HEADS_UP_WHEN_UNLOCKED
            }
        }
        CallNotificationManager.ensureChannels(this)
        TelecomCallRepository.attachService(this)
    }

    override fun onDestroy() {
        serviceScope.cancel()
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

        // Optional: for an incoming call while the phone is in use and unlocked, rely on the
        // call notification (heads-up) instead of taking over the whole screen.
        if (call.state == Call.STATE_RINGING && headsUpWhenUnlocked && isDeviceInUseAndUnlocked()) {
            return
        }

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
            CallRecorder.stop()
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

    private fun isDeviceInUseAndUnlocked(): Boolean {
        val km = getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        return km?.isKeyguardLocked == false && pm?.isInteractive == true
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
