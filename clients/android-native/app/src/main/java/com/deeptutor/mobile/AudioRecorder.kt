package com.deeptutor.mobile

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/**
 * 原生麦克风录音封装：输出 m4a（AAC），直接喂给 STT 接口。
 * 使用 MediaRecorder 的 MIC 源 + MPEG_4 容器 + AAC 编码，全 Android 版本可用。
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    @Suppress("DEPRECATION")
    fun start(): Boolean = runCatching {
        val file = File(context.cacheDir, "voice_input.m4a")
        if (file.exists()) file.delete()
        outputFile = file

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        true
    }.getOrElse {
        recorder?.release()
        recorder = null
        outputFile = null
        false
    }

    /** 停止录音并返回音频字节；失败返回 null。 */
    fun stop(): ByteArray? = runCatching {
        val r = recorder
        if (r == null) return null
        try {
            r.stop()
        } finally {
            r.release()
            recorder = null
        }
        outputFile?.takeIf { it.exists() }?.readBytes()
    }.getOrNull()

    fun cancel() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }
}