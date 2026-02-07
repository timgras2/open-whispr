package com.openwhispr.android.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {
        val file = File(context.cacheDir, "whispr_${System.currentTimeMillis()}.m4a")

        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setAudioChannels(1)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = mr
        outputFile = file
        return file
    }

    fun stop(): File? {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: RuntimeException) {
            // stop() can throw if called too quickly after start()
            outputFile?.delete()
            outputFile = null
        }
        recorder = null
        return outputFile
    }

    fun release() {
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
    }
}
