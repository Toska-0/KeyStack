package com.toska.keystack.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import kotlinx.coroutines.delay
import java.io.File
import java.awt.Frame
import java.awt.Window as AwtWindow
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary

fun main() = application {
    // Данные приложения: ~/.keystack (настройки, история чатов)
    val dataDir = File(System.getProperty("user.home"), ".keystack").apply { mkdirs() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "KeyStack",
        state = rememberWindowState(width = 1280.dp, height = 832.dp)
    ) {
        val composeSystemDark = isSystemInDarkTheme()
        var systemDark by remember { mutableStateOf(readWindowsDarkMode() ?: composeSystemDark) }
        LaunchedEffect(composeSystemDark) {
            while (true) {
                systemDark = readWindowsDarkMode() ?: composeSystemDark
                delay(2000)
            }
        }
        AppRoot(dataDir, systemDark)
    }
}

@Composable
fun AppRoot(dataDir: File, systemDark: Boolean) {
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

    val windowDark = when (settings.themeMode) {
        "light" -> false
        "dark", "amoled" -> true
        else -> systemDark
    }
    SideEffect { applyWindowsTitleBarTheme(windowDark) }

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
                onSaveWorkspace = { path ->
                    scope.launch { settingsStore.saveWorkspace(path) }
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


private fun readWindowsDarkMode(): Boolean? {
    if (!System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) return null
    return runCatching {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) return@runCatching null
        val value = Regex("AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)")
            .find(output)?.groupValues?.get(1)?.toInt(16) ?: return@runCatching null
        value == 0
    }.getOrNull()
}

private fun applyWindowsTitleBarTheme(dark: Boolean) {
    if (!System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) return
    runCatching {
        // Use the actual KeyStack AWT window handle, not whichever window happens to be foreground.
        val frame = AwtWindow.getWindows()
            .filterIsInstance<Frame>()
            .firstOrNull { it.isDisplayable && it.title == "KeyStack" }
            ?: AwtWindow.getWindows().filterIsInstance<Frame>()
                .firstOrNull { it.isDisplayable && it.isActive }
            ?: return
        val hwnd = Native.getComponentPointer(frame)
        if (hwnd == null) return
        val value = Memory(4).apply { setInt(0, if (dark) 1 else 0) }
        val dwm = NativeLibrary.getInstance("dwmapi").getFunction("DwmSetWindowAttribute")
        val result = dwm.invokeInt(arrayOf(hwnd, 20, value, 4))
        if (result != 0) dwm.invokeInt(arrayOf(hwnd, 19, value, 4))
    }
}
