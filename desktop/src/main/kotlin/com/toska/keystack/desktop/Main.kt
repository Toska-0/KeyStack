package com.toska.keystack.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.toska.keystack.ChatStore
import com.toska.keystack.ChatViewModel
import com.toska.keystack.Settings
import com.toska.keystack.SettingsStore
import com.toska.keystack.ui.ChatScreen
import com.toska.keystack.ui.SetupScreen
import com.toska.keystack.ui.theme.KeyStackTheme
import kotlinx.coroutines.launch
import java.io.File

fun main() = application {
    // Данные приложения: ~/.keystack (настройки, история чатов)
    val dataDir = File(System.getProperty("user.home"), ".keystack").apply { mkdirs() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "KeyStack",
        state = rememberWindowState(width = 1280.dp, height = 832.dp)
    ) {
        AppRoot(dataDir)
    }
}

@Composable
fun AppRoot(dataDir: File) {
    val settingsStore = remember { SettingsStore(dataDir) }
    val chatStore = remember { ChatStore(dataDir) }
    val vm = remember { ChatViewModel() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.init(settingsStore, chatStore) }

    val settings by settingsStore.settings.collectAsState(initial = Settings("", "", "", ""))
    var showSetup by remember { mutableStateOf(true) }

    LaunchedEffect(settings.providerId, settings.apiKey, settings.model) {
        if (settings.providerId.isNotBlank() &&
            settings.apiKey.isNotBlank() &&
            settings.model.isNotBlank()
        ) {
            showSetup = false
        }
    }

    KeyStackTheme(themeMode = settings.themeMode) {
        if (showSetup) {
            SetupScreen(
                initial = settings,
                onDone = { p, k, m, b ->
                    scope.launch { settingsStore.saveProviderCreds(p, k, m, b) }
                    showSetup = false
                },
                onSaveTheme = { mode, dyn ->
                    scope.launch { settingsStore.saveTheme(mode, dyn) }
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
