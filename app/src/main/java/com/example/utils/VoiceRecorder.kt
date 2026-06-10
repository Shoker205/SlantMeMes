package com.example.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var currentOutputFile: File? = null
        private set

    fun startRecording(): Boolean {
        try {
            currentOutputFile = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.mp4")
            
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentOutputFile?.absolutePath)
                prepare()
                start()
            }
            return true
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Start recording failed", e)
            recorder?.release()
            recorder = null
            return false
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            currentOutputFile
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Stop recording failed", e)
            recorder?.release()
            recorder = null
            null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
             Log.e("VoiceRecorder", "Cancel recording failed", e)
        } finally {
            recorder = null
            currentOutputFile?.delete()
            currentOutputFile = null
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
