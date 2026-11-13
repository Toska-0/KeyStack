package com.toska.keystack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun KeyStackTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark", "amoled" -> true
        else -> isSystemInDarkTheme()
    }
    // Динамические цвета Material You доступны только на Android 12+ — на десктопе свои схемы
    val colorScheme = when {
        themeMode == "amoled" -> darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF141414),
            surfaceContainer = Color(0xFF0D0D0D),
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1A1A1A)
        )
        darkTheme -> darkColorScheme(
            primary = Color(0xFFECECEC),
            onPrimary = Color(0xFF202020),
            background = Color(0xFF212121),
            onBackground = Color(0xFFECECEC),
            surface = Color(0xFF212121),
            onSurface = Color(0xFFECECEC),
            surfaceVariant = Color(0xFF303030),
            onSurfaceVariant = Color(0xFFB4B4B4),
            surfaceContainer = Color(0xFF262626),
            surfaceContainerLow = Color(0xFF252525),
            surfaceContainerLowest = Color(0xFF202020),
            surfaceContainerHigh = Color(0xFF303030),
            surfaceContainerHighest = Color(0xFF383838),
            secondaryContainer = Color(0xFF303030),
            onSecondaryContainer = Color(0xFFECECEC),
            outline = Color(0xFF555555)
        )
        else -> lightColorScheme(
            primary = Color(0xFF202020),
            onPrimary = Color.White,
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF4F4F4),
            surfaceContainer = Color(0xFFF7F7F7),
            surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainerHigh = Color(0xFFF1F1F1),
            surfaceContainerHighest = Color(0xFFEAEAEA),
            secondaryContainer = Color(0xFFECECEC),
            outline = Color(0xFFD7D7D7)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
