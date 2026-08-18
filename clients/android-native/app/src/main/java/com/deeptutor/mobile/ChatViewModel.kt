package com.deeptutor.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deeptutor.mobile.net.ChatWebSocketClient
import com.deeptutor.mobile.net.ServerConfig
import com.deeptutor.mobile.net.VoiceApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Role { USER, ASSISTANT }

data class ChatMessage(val role: Role, val content: String)

data class UiState(
    val serverUrl: String = "",
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val statusText: String = "",
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var client: ChatWebSocketClient? = null
    private var pendingMessage: String? = null
    private val recorder = AudioRecorder(app)

    init {
        _uiState.update { it.copy(serverUrl = ServerConfig.load(app)) }
    }

    fun saveServer(url: String) {
        ServerConfig.save(getApplication(), url)
        _uiState.update { it.copy(serverUrl = ServerConfig.normalize(url)) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun onInputChange(text: String) = _uiState.update { it.copy(inputText = text) }

    /** 建立 WebSocket 连接（进入聊天页时调用）。 */
    fun connect() {
        val url = _uiState.value.serverUrl
        if (url.isBlank() || _uiState.value.connected || _uiState.value.connecting) return
        _uiState.update { it.copy(connecting = true) }

        val c = ChatWebSocketClient(url)
        c.listener = object : ChatWebSocketClient.Listener {
            override fun onConnected() {
                _uiState.update { it.copy(connected = true, connecting = false) }
                pendingMessage?.let { c.sendMessage(it) }
                pendingMessage = null
            }

            override fun onSessionId(id: String) = Unit

            override fun onStatus(message: String) =
                _uiState.update { it.copy(statusText = message) }

            override fun onStreamChunk(chunk: String) = appendToLastAssistant(chunk)

            override fun onResult(fullText: String) =
                _uiState.update {
                    it.copy(
                        messages = replaceLastAssistant(it.messages, fullText),
                        streaming = false,
                        statusText = "",
                    )
                }

            override fun onError(message: String) =
                _uiState.update {
                    it.copy(
                        streaming = false,
                        statusText = "",
                        error = message,
                    )
                }

            override fun onDisconnected() =
                _uiState.update { it.copy(connected = false, connecting = false, streaming = false) }
        }
        client = c
        c.connect()
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.streaming) return

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(Role.USER, text) + ChatMessage(Role.ASSISTANT, ""),
                inputText = "",
                streaming = true,
                error = null,
            )
        }

        val c = client
        if (c == null || !_uiState.value.connected) {
            pendingMessage = text
            connect()
            return
        }
        c.sendMessage(text)
    }

    private fun appendToLastAssistant(chunk: String) {
        _uiState.update {
            val msgs = it.messages.toMutableList()
            if (msgs.isNotEmpty() && msgs.last().role == Role.ASSISTANT) {
                msgs[msgs.lastIndex] = msgs.last().copy(content = msgs.last().content + chunk)
            }
            it.copy(messages = msgs, statusText = "")
        }
    }

    private fun replaceLastAssistant(msgs: List<ChatMessage>, text: String): List<ChatMessage> {
        val out = msgs.toMutableList()
        if (out.isNotEmpty() && out.last().role == Role.ASSISTANT) {
            out[out.lastIndex] = out.last().copy(content = text)
        }
        return out
    }

    fun disconnect() {
        client?.close()
        client = null
        _uiState.update {
            it.copy(connected = false, connecting = false, streaming = false)
        }
    }

    // ---------------------------------------------------------------------
    // 录音与语音转文字
    // ---------------------------------------------------------------------
    fun toggleRecording() {
        if (_uiState.value.recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (recorder.start()) {
            _uiState.update { it.copy(recording = true) }
        } else {
            _uiState.update { it.copy(error = "无法启动录音，请检查麦克风权限") }
        }
    }

    private fun stopRecording() {
        _uiState.update { it.copy(recording = false) }
        val bytes = recorder.stop() ?: return
        val url = _uiState.value.serverUrl
        viewModelScope.launch {
            _uiState.update { it.copy(transcribing = true) }
            try {
                val text = VoiceApi.transcribe(url, bytes)
                _uiState.update { it.copy(transcribing = false, inputText = text) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(transcribing = false, error = e.message ?: "语音识别失败")
                }
            }
        }
    }

    override fun onCleared() {
        recorder.release()
        disconnect()
        super.onCleared()
    }
}