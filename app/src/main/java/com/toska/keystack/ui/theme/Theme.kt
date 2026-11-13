package com.toska.keystack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Фирменный градиент Mistral — от жёлтого к красному (логотипы, кнопка отправки). */
val MistralGradient = listOf(
    Color(0xFFFFD800),
    Color(0xFFFFAF00),
    Color(0xFFFF8205),
    Color(0xFFFA500F),
    Color(0xFFE10500)
)

@Composable
fun KeyStackTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    accentColor: String = "orange",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark", "amoled" -> true
        else -> isSystemInDarkTheme()
    }
    val accent = when (accentColor) { "blue" -> Color(0xFF4D8DFF); "purple" -> Color(0xFF9B72E8); "green" -> Color(0xFF35A879); "pink" -> Color(0xFFE65B91); "cyan" -> Color(0xFF20AFC2); else -> Color(0xFFFF5A1F) }
    val colorScheme = when {
        themeMode == "amoled" -> darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = Color.Black,
            onBackground = Color(0xFFF4F1EE),
            surface = Color.Black,
            onSurface = Color(0xFFF4F1EE),
            surfaceVariant = Color(0xFF141414),
            onSurfaceVariant = Color(0xFFB8B2AD),
            surfaceContainer = Color(0xFF0D0D0D),
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1A1A1A),
            secondaryContainer = Color(0xFF38251F),
            onSecondaryContainer = Color(0xFFFFD8CB),
            outline = Color(0xFF49413D)
        )
        false -> darkColorScheme()
        darkTheme -> darkColorScheme(
            primary = Color(0xFFFF5A1F),
            onPrimary = Color.White,
            background = Color(0xFF171717),
            onBackground = Color(0xFFF4F1EE),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFF4F1EE),
            surfaceVariant = Color(0xFF292929),
            onSurfaceVariant = Color(0xFFB8B2AD),
            surfaceContainer = Color(0xFF202020),
            surfaceContainerLow = Color(0xFF1B1B1B),
            surfaceContainerLowest = Color(0xFF151515),
            surfaceContainerHigh = Color(0xFF292929),
            surfaceContainerHighest = Color(0xFF333333),
            secondaryContainer = Color(0xFF38251F),
            onSecondaryContainer = Color(0xFFFFD8CB),
            outline = Color(0xFF49413D)
        )
        else -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = Color(0xFFFAF8F6),
            onBackground = Color(0xFF252321),
            surface = Color(0xFFFFFEFC),
            onSurface = Color(0xFF252321),
            surfaceVariant = Color(0xFFF1EEEB),
            onSurfaceVariant = Color(0xFF706B66),
            surfaceContainer = Color(0xFFF4F1EE),
            surfaceContainerLow = Color(0xFFFAF8F6),
            surfaceContainerHigh = Color(0xFFEDE9E5),
            surfaceContainerHighest = Color(0xFFE6E1DC),
            secondaryContainer = Color(0xFFFFE8DF),
            onSecondaryContainer = Color(0xFF6D2B12),
            outline = Color(0xFFD9D2CC)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
