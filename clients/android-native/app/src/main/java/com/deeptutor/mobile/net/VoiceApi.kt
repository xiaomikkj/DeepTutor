package com.deeptutor.mobile.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 语音转文字（STT）HTTP 客户端：对接 ``POST /api/v1/voice/stt``。
 * 上传原生录音（m4a/AAC），返回识别文本。
 */
object VoiceApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 同步上传音频并返回识别文本。调用方需在 IO 协程中执行。
     */
    suspend fun transcribe(
        baseUrl: String,
        audioBytes: ByteArray,
        language: String = "zh",
        filename: String = "audio.m4a",
    ): String = withContext(Dispatchers.IO) {
        val url = "${ServerConfig.normalize(baseUrl)}/api/v1/voice/stt"
        val audioBody = audioBytes.toRequestBody("audio/mp4".toMediaType())
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, audioBody)
            .addFormDataPart("language", language)
            .build()

        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IllegalStateException("STT 失败 (${resp.code}): $text")
            }
            JSONObject(text).optString("text")
        }
    }
}