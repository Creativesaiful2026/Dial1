package com.example.core.telecom

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Call recorder with two capture modes:
 *
 * 1. "call" - tries the telephony voice-call audio source, which records BOTH sides without
 *    speaker. Android only allows this for system apps, but some phones / custom ROMs do
 *    allow it for the default dialer. If it is blocked (the recording stays digitally
 *    silent) the recorder automatically falls back to mode 2 after a few seconds.
 * 2. "mic" - records through the microphone: your voice always, the other person only when
 *    the call is on speaker.
 *
 * File name: rec_<time>_<call|mic>_<last 9 digits>.m4a, stored privately in the app folder.
 */
object CallRecorder {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private var verifyId = 0

    val isRecording: Boolean
        @Synchronized get() = recorder != null

    fun recordingsDir(context: Context): File =
        File(context.filesDir, "recordings").apply { mkdirs() }

    fun numberToken(number: String): String =
        number.filter { it.isDigit() }.takeLast(9).ifEmpty { "unknown" }

    @Synchronized
    fun start(context: Context, number: String, tryCallAudio: Boolean = true): Boolean {
        if (recorder != null) return true
        val appContext = context.applicationContext
        val token = numberToken(number)
        if (tryCallAudio && begin(appContext, token, MediaRecorder.AudioSource.VOICE_CALL, "call")) {
            scheduleVerification(appContext, token)
            return true
        }
        return begin(appContext, token, MediaRecorder.AudioSource.MIC, "mic")
    }

    private fun begin(context: Context, token: String, source: Int, tag: String): Boolean {
        val file = File(recordingsDir(context), "rec_${System.currentTimeMillis()}_${tag}_$token.m4a")
        var r: MediaRecorder? = null
        return try {
            r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(source)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            currentFile = file
            true
        } catch (e: Exception) {
            runCatching { r?.release() }
            runCatching { file.delete() }
            recorder = null
            currentFile = null
            false
        }
    }

    /** If call audio is blocked the stream is exactly silent: detect that and use the mic. */
    private fun scheduleVerification(context: Context, token: String) {
        val id = ++verifyId
        handler.postDelayed({
            synchronized(this) {
                if (id == verifyId) recorder?.let { runCatching { it.maxAmplitude } } // reset peak
            }
        }, 2_000)
        handler.postDelayed({
            synchronized(this) {
                if (id != verifyId) return@synchronized
                val active = recorder ?: return@synchronized
                val peak = runCatching { active.maxAmplitude }.getOrDefault(0)
                if (peak == 0) {
                    stopInternal(deleteFile = true)
                    begin(context, token, MediaRecorder.AudioSource.MIC, "mic")
                }
            }
        }, 5_500)
    }

    private fun stopInternal(deleteFile: Boolean) {
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
        currentFile?.let { if (it.exists() && (deleteFile || it.length() < 2048)) it.delete() }
        currentFile = null
    }

    @Synchronized
    fun stop() {
        verifyId++
        handler.removeCallbacksAndMessages(null)
        stopInternal(deleteFile = false)
    }

    fun recordingsFor(context: Context, number: String): List<File> {
        val token = numberToken(number)
        return recordingsDir(context).listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith("_$token.m4a") }
            .sortedByDescending { it.name }
    }
}
