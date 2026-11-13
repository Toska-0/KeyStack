package com.toska.keystack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toska.keystack.ui.ChatScreen
import com.toska.keystack.ui.SetupScreen
import com.toska.keystack.ui.theme.KeyStackTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KeyStackTheme {
                val settingsStore = remember { SettingsStore(applicationContext) }
                val chatStore = remember { ChatStore(applicationContext) }
                val vm: ChatViewModel = viewModel()
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) { vm.init(settingsStore, chatStore) }

                val settings by vm.settings.collectAsState()
                var showSetup by remember { mutableStateOf(true) }

                LaunchedEffect(settings.providerId, settings.apiKey, settings.model) {
                    if (settings.providerId.isNotBlank() &&
                        settings.apiKey.isNotBlank() &&
                        settings.model.isNotBlank()
                    ) {
                        showSetup = false
                    }
                }

                if (showSetup) {
                    SetupScreen(
                        initial = settings,
                        onDone = { p, k, m, b ->
                            scope.launch { settingsStore.save(p, k, m, b) }
                            showSetup = false
                        },
                        onBack = if (settings.providerId.isNotBlank() && settings.apiKey.isNotBlank()) {
                            { showSetup = false }
                        } else null
                    )
                } else {
                    ChatScreen(
                        vm = vm,
                        onOpenSettings = { showSetup = true }
                    )
                }
            }
        }
    }
}
