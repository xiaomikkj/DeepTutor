package com.deeptutor.mobile.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 聊天 WebSocket 客户端：对接 DeepTutor 后端 ``/api/v1/chat``。
 *
 * 服务端事件协议（见后端 chat.py）：
 *   session / status / stream / sources / result / error
 * 流式 ``stream`` 事件携带增量文本，``result`` 事件携带完整回答。
 */
class ChatWebSocketClient(private val baseUrl: String) {

    interface Listener {
        fun onConnected()
        fun onSessionId(id: String)
        fun onStatus(message: String)
        fun onStreamChunk(chunk: String)
        fun onResult(fullText: String)
        fun onError(message: String)
        fun onDisconnected()
    }

    var listener: Listener? = null
    var sessionId: String? = null
        private set

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接，不设读超时
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect() {
        val wsUrl = "${ServerConfig.baseToWs(baseUrl)}/api/v1/chat"
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, SocketListener())
    }

    fun sendMessage(text: String, language: String = "zh") {
        val payload = JSONObject().apply {
            put("message", text)
            put("language", language)
            put("enable_rag", false)
            put("enable_web_search", false)
            sessionId?.let { put("session_id", it) }
        }
        webSocket?.send(payload.toString())
    }

    fun close() {
        webSocket?.close(1000, "client close")
        webSocket = null
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            listener?.onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (obj.optString("type")) {
                "session" -> {
                    sessionId = obj.optString("session_id")
                    listener?.onSessionId(sessionId.orEmpty())
                }
                "status" -> listener?.onStatus(obj.optString("message"))
                "stream" -> listener?.onStreamChunk(obj.optString("content"))
                "result" -> listener?.onResult(obj.optString("content"))
                "error" -> listener?.onError(obj.optString("message"))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            listener?.onError(t.message ?: "连接失败")
            listener?.onDisconnected()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            listener?.onDisconnected()
        }
    }
}