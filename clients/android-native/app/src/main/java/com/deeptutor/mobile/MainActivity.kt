package com.deeptutor.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deeptutor.mobile.ui.ChatScreen
import com.deeptutor.mobile.ui.ServerSetupScreen
import com.deeptutor.mobile.ui.theme.DeepTutorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeepTutorTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showSetup by rememberSaveable { mutableStateOf(state.serverUrl.isBlank()) }

    if (showSetup) {
        ServerSetupScreen(
            initialUrl = state.serverUrl,
            onConnect = { url ->
                viewModel.saveServer(url)
                showSetup = false
                viewModel.connect()
            },
        )
    } else {
        ChatScreen(
            state = state,
            onBack = {
                viewModel.disconnect()
                showSetup = true
            },
            onSend = viewModel::sendMessage,
            onInputChange = viewModel::onInputChange,
            onToggleRecord = viewModel::toggleRecording,
            onClearError = viewModel::clearError,
        )
    }
}