package com.example.feature.incall

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.DialMeApplication
import com.example.core.datastore.DialMeConfig
import com.example.core.designsystem.DialMeTheme
import com.example.core.telecom.TelecomCallRepository
import kotlinx.coroutines.launch

class InCallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenFlags()

        val container = (application as DialMeApplication).container

        setContent {
            val config by container.preferencesRepository.configFlow
                .collectAsStateWithLifecycle(initialValue = DialMeConfig())
            val uiState by TelecomCallRepository.uiState.collectAsStateWithLifecycle()
            val quickResponses by container.localRepository.quickResponses
                .collectAsStateWithLifecycle(initialValue = emptyList())

            LaunchedEffect(uiState.calls.isEmpty()) {
                if (uiState.calls.isEmpty()) {
                    finish()
                }
            }

            DialMeTheme(config = config) {
                InCallScreen(
                    uiState = uiState,
                    config = config,
                    quickResponses = quickResponses,
                    onAnswer = { TelecomCallRepository.answer(it) },
                    onReject = { id, msg -> TelecomCallRepository.reject(id, msg) },
                    onDisconnect = { TelecomCallRepository.disconnect(it) },
                    onToggleMute = { TelecomCallRepository.setMuted(it) },
                    onToggleSpeaker = { TelecomCallRepository.toggleSpeakerQuick() },
                    onSelectAudioEndpoint = { TelecomCallRepository.selectAudioEndpoint(it) },
                    onToggleHold = { TelecomCallRepository.toggleHold(it) },
                    onSwapCalls = { TelecomCallRepository.swapCalls() },
                    onMergeConference = { TelecomCallRepository.mergeConference() },
                    onPlayDtmf = { TelecomCallRepository.playDtmf(it) },
                    onStopDtmf = { TelecomCallRepository.stopDtmf() },
                    onSaveNote = { number, text ->
                        lifecycleScope.launch {
                            container.localRepository.saveCallNote(number, text)
                        }
                    }
                )
            }
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}
