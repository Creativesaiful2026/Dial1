package com.example.core.telecom

import android.annotation.SuppressLint
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.example.domain.model.ActiveCallSession
import com.example.domain.model.AudioEndpointItem
import com.example.domain.model.AudioRouteType
import com.example.domain.model.CallStateType
import com.example.domain.model.CallerVerificationStatus
import com.example.domain.model.InCallUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object TelecomCallRepository {

    private val _uiState = MutableStateFlow(InCallUiState())
    val uiState: StateFlow<InCallUiState> = _uiState.asStateFlow()

    private val activeCalls = ConcurrentHashMap<String, Call>()
    private var inCallServiceRef: WeakReference<InCallService>? = null
    private val inCallService: InCallService?
        get() = inCallServiceRef?.get()
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private var currentEndpoints: List<AudioEndpointItem> = defaultFallbackEndpoints()
    private var currentRoute: AudioRouteType = AudioRouteType.EARPIECE
    private var currentMuted: Boolean = false

    fun attachService(service: InCallService?) {
        inCallServiceRef = service?.let { WeakReference(it) }
        if (service == null) {
            activeCalls.clear()
            publishState()
        }
    }

    fun registerCall(call: Call, resolvedName: String? = null, resolvedPhoto: String? = null) {
        val id = callId(call)
        activeCalls[id] = call
        publishState(mapOf(id to Pair(resolvedName, resolvedPhoto)))
    }

    fun updateCall(call: Call, resolvedName: String? = null, resolvedPhoto: String? = null) {
        val id = callId(call)
        if (activeCalls.containsKey(id)) {
            publishState(mapOf(id to Pair(resolvedName, resolvedPhoto)))
        }
    }

    fun unregisterCall(call: Call) {
        val id = callId(call)
        activeCalls.remove(id)
        publishState()
    }

    fun onMuteChanged(isMuted: Boolean) {
        currentMuted = isMuted
        publishState()
    }

    @Suppress("DEPRECATION")
    fun onLegacyAudioStateChanged(audioState: CallAudioState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        currentMuted = audioState.isMuted
        currentRoute = when (audioState.route) {
            CallAudioState.ROUTE_SPEAKER -> AudioRouteType.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> AudioRouteType.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioRouteType.WIRED_HEADSET
            CallAudioState.ROUTE_STREAMING -> AudioRouteType.STREAMING
            else -> AudioRouteType.EARPIECE
        }
        val mask = audioState.supportedRouteMask
        val list = mutableListOf<AudioEndpointItem>()
        if ((mask and CallAudioState.ROUTE_EARPIECE) != 0) {
            list += AudioEndpointItem("EARPIECE", "Phone Earpiece", AudioRouteType.EARPIECE, currentRoute == AudioRouteType.EARPIECE)
        }
        if ((mask and CallAudioState.ROUTE_SPEAKER) != 0) {
            list += AudioEndpointItem("SPEAKER", "Speaker", AudioRouteType.SPEAKER, currentRoute == AudioRouteType.SPEAKER)
        }
        if ((mask and CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
            list += AudioEndpointItem("WIRED", "Wired Headset", AudioRouteType.WIRED_HEADSET, currentRoute == AudioRouteType.WIRED_HEADSET)
        }
        if ((mask and CallAudioState.ROUTE_BLUETOOTH) != 0) {
            val btName = resolveBluetoothDeviceName(audioState)
            list += AudioEndpointItem("BLUETOOTH", btName, AudioRouteType.BLUETOOTH, currentRoute == AudioRouteType.BLUETOOTH)
        }
        currentEndpoints = if (list.isNotEmpty()) list else defaultFallbackEndpoints()
        publishState()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun resolveBluetoothDeviceName(audioState: CallAudioState): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "Bluetooth"
        return try {
            audioState.activeBluetoothDevice?.name?.takeIf { it.isNotBlank() } ?: "Bluetooth"
        } catch (_: SecurityException) {
            "Bluetooth"
        }
    }

    private var endpointObjects: Map<String, Any> = emptyMap()

    fun onCallEndpointChanged(endpoint: CallEndpoint) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        currentRoute = mapEndpointType(endpoint.endpointType)
        val selectedId = endpoint.identifier.toString()
        currentEndpoints = currentEndpoints.map {
            it.copy(isSelected = it.id == selectedId)
        }
        publishState()
    }

    fun onAvailableEndpointsChanged(endpoints: List<CallEndpoint>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val map = mutableMapOf<String, Any>()
        val items = endpoints.map { ep ->
            val id = ep.identifier.toString()
            map[id] = ep
            val routeType = mapEndpointType(ep.endpointType)
            AudioEndpointItem(
                id = id,
                label = ep.endpointName.toString().ifBlank { routeType.name },
                routeType = routeType,
                isSelected = routeType == currentRoute
            )
        }
        endpointObjects = map
        if (items.isNotEmpty()) {
            currentEndpoints = items
        }
        publishState()
    }

    private fun mapEndpointType(type: Int): AudioRouteType {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            when (type) {
                CallEndpoint.TYPE_SPEAKER -> AudioRouteType.SPEAKER
                CallEndpoint.TYPE_BLUETOOTH -> AudioRouteType.BLUETOOTH
                CallEndpoint.TYPE_WIRED_HEADSET -> AudioRouteType.WIRED_HEADSET
                CallEndpoint.TYPE_STREAMING -> AudioRouteType.STREAMING
                else -> AudioRouteType.EARPIECE
            }
        } else {
            AudioRouteType.EARPIECE
        }
    }

    fun answer(callId: String? = null) {
        val target = resolveCall(callId) { it.state == Call.STATE_RINGING } ?: return
        target.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject(callId: String? = null, withMessage: String? = null) {
        val target = resolveCall(callId) { it.state == Call.STATE_RINGING } ?: return
        if (!withMessage.isNullOrBlank()) {
            target.reject(true, withMessage)
        } else {
            target.reject(false, null)
        }
    }

    fun disconnect(callId: String? = null) {
        val target = resolveCall(callId) ?: return
        if (target.state == Call.STATE_RINGING) {
            target.reject(false, null)
        } else {
            target.disconnect()
        }
    }

    fun toggleHold(callId: String? = null) {
        val target = resolveCall(callId) ?: return
        if (target.state == Call.STATE_HOLDING) {
            target.unhold()
        } else if (target.state == Call.STATE_ACTIVE) {
            target.hold()
        }
    }

    fun swapCalls() {
        val holding = activeCalls.values.firstOrNull { it.state == Call.STATE_HOLDING }
        val active = activeCalls.values.firstOrNull { it.state == Call.STATE_ACTIVE }
        if (holding != null) {
            holding.unhold()
        } else if (active != null) {
            active.hold()
        }
    }

    fun mergeConference() {
        val primary = activeCalls.values.firstOrNull { it.state == Call.STATE_ACTIVE } ?: return
        val other = activeCalls.values.firstOrNull { it != primary && it.state == Call.STATE_HOLDING }
        if (other != null) {
            primary.conference(other)
        } else if (primary.conferenceableCalls.isNotEmpty()) {
            primary.conference(primary.conferenceableCalls.first())
        } else {
            primary.mergeConference()
        }
    }

    fun setMuted(muted: Boolean) {
        inCallService?.setMuted(muted)
        currentMuted = muted
        publishState()
    }

    @Suppress("DEPRECATION")
    fun selectAudioEndpoint(endpointId: String) {
        val service = inCallService ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ep = endpointObjects[endpointId] as? CallEndpoint
            if (ep != null) {
                service.requestCallEndpointChange(ep, callbackExecutor) {}
                return
            }
        }
        val route = when (endpointId) {
            "SPEAKER" -> CallAudioState.ROUTE_SPEAKER
            "BLUETOOTH" -> CallAudioState.ROUTE_BLUETOOTH
            "WIRED" -> CallAudioState.ROUTE_WIRED_HEADSET
            else -> CallAudioState.ROUTE_EARPIECE
        }
        service.setAudioRoute(route)
    }

    fun toggleSpeakerQuick() {
        val next = if (currentRoute == AudioRouteType.SPEAKER) {
            currentEndpoints.firstOrNull { it.routeType == AudioRouteType.EARPIECE }?.id ?: "EARPIECE"
        } else {
            currentEndpoints.firstOrNull { it.routeType == AudioRouteType.SPEAKER }?.id ?: "SPEAKER"
        }
        selectAudioEndpoint(next)
    }

    fun playDtmf(digit: Char) {
        val target = resolveCall(null) { it.state == Call.STATE_ACTIVE } ?: return
        target.playDtmfTone(digit)
    }

    fun stopDtmf() {
        val target = resolveCall(null) { it.state == Call.STATE_ACTIVE } ?: return
        target.stopDtmfTone()
    }

    private fun resolveCall(callId: String?, predicate: (Call) -> Boolean = { true }): Call? {
        if (callId != null) return activeCalls[callId]
        return activeCalls.values.firstOrNull(predicate) ?: activeCalls.values.firstOrNull()
    }

    private fun callId(call: Call): String = System.identityHashCode(call).toString()

    private val cachedContactInfo = ConcurrentHashMap<String, Pair<String?, String?>>()

    private fun publishState(overrides: Map<String, Pair<String?, String?>> = emptyMap()) {
        overrides.forEach { (k, v) ->
            if (v.first != null || v.second != null) {
                cachedContactInfo[k] = v
            }
        }
        val sessions = activeCalls.entries.map { (id, call) ->
            val details = call.details
            val rawHandle = details?.handle?.schemeSpecificPart.orEmpty()
            val isHidden = details?.handlePresentation != android.telecom.TelecomManager.PRESENTATION_ALLOWED
            val cached = cachedContactInfo[id]
            val callerDisplay = cached?.first?.takeIf { it.isNotBlank() }
                ?: details?.callerDisplayName?.takeIf { it.isNotBlank() }
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    details?.contactDisplayName?.takeIf { it.isNotBlank() }
                } else {
                    null
                }

            val verification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (details?.callerNumberVerificationStatus) {
                    android.telecom.Connection.VERIFICATION_STATUS_PASSED -> CallerVerificationStatus.PASSED
                    android.telecom.Connection.VERIFICATION_STATUS_FAILED -> CallerVerificationStatus.FAILED
                    else -> CallerVerificationStatus.NOT_VERIFIED
                }
            } else {
                CallerVerificationStatus.NOT_VERIFIED
            }

            val caps = details?.callCapabilities ?: 0
            val canHold = (caps and Call.Details.CAPABILITY_HOLD) != 0 ||
                (caps and Call.Details.CAPABILITY_SUPPORT_HOLD) != 0
            val canMerge = (caps and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0 ||
                call.conferenceableCalls.isNotEmpty()
            val canSwap = (caps and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0 ||
                activeCalls.size > 1

            val photoUriString = cached?.second
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    details?.contactPhotoUri?.toString()
                } else {
                    null
                }

            ActiveCallSession(
                callId = id,
                number = if (isHidden && rawHandle.isBlank()) "Private Number" else rawHandle,
                displayName = callerDisplay,
                photoUri = photoUriString,
                isIncoming = call.state == Call.STATE_RINGING ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        details?.callDirection == Call.Details.DIRECTION_INCOMING),
                isUnknownPresentation = isHidden,
                state = mapCallState(call.state),
                connectTimeMillis = details?.connectTimeMillis ?: 0L,
                canHold = canHold,
                canMerge = canMerge,
                canSwap = canSwap,
                isConference = call.children.isNotEmpty(),
                simLabel = details?.accountHandle?.id,
                verificationStatus = verification
            )
        }.sortedBy {
            when (it.state) {
                CallStateType.RINGING -> 0
                CallStateType.ACTIVE -> 1
                CallStateType.DIALING, CallStateType.CONNECTING -> 2
                CallStateType.HOLDING -> 3
                else -> 4
            }
        }

        _uiState.value = InCallUiState(
            calls = sessions,
            primaryCall = sessions.firstOrNull(),
            secondaryCall = sessions.getOrNull(1),
            isMuted = currentMuted,
            activeAudioRoute = currentRoute,
            availableAudioEndpoints = currentEndpoints
        )
    }

    @Suppress("DEPRECATION")
    private fun mapCallState(state: Int): CallStateType = when (state) {
        Call.STATE_RINGING -> CallStateType.RINGING
        Call.STATE_DIALING -> CallStateType.DIALING
        Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT -> CallStateType.CONNECTING
        Call.STATE_ACTIVE -> CallStateType.ACTIVE
        Call.STATE_HOLDING -> CallStateType.HOLDING
        Call.STATE_DISCONNECTING -> CallStateType.DISCONNECTING
        Call.STATE_DISCONNECTED -> CallStateType.DISCONNECTED
        else -> CallStateType.IDLE
    }

    private fun defaultFallbackEndpoints(): List<AudioEndpointItem> = listOf(
        AudioEndpointItem("EARPIECE", "Phone Earpiece", AudioRouteType.EARPIECE, true),
        AudioEndpointItem("SPEAKER", "Speaker", AudioRouteType.SPEAKER, false)
    )
}
