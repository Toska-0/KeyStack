package com.toska.keystack.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** Фирменный градиент логотипа KeyStack. */
val MistralGradient = listOf(
    Color(0xFFFFD800),
    Color(0xFFFFAF00),
    Color(0xFFFF8205),
    Color(0xFFFA500F),
    Color(0xFFE10500)
)

/** Компактный знак KeyStack: три уровня, объединённые общей стойкой. */
@Composable
fun KeyStackMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val barH = h * 0.13f
        val radius = CornerRadius(h * 0.065f, h * 0.065f)
        drawRoundRect(Color.White, Offset(w * 0.28f, h * 0.19f), Size(w * 0.57f, barH), radius)
        drawRoundRect(Color.White, Offset(w * 0.28f, h * 0.435f), Size(w * 0.46f, barH), radius)
        drawRoundRect(Color.White, Offset(w * 0.28f, h * 0.68f), Size(w * 0.57f, barH), radius)
        drawRoundRect(Color.White, Offset(w * 0.13f, h * 0.19f), Size(w * 0.09f, h * 0.62f), radius)
    }
}

private fun accentColor(value: String): Color = when (value) {
    "blue" -> Color(0xFF4D8DFF)
    "purple" -> Color(0xFF9B72E8)
    "green" -> Color(0xFF35A879)
    "pink" -> Color(0xFFE65B91)
    "cyan" -> Color(0xFF20AFC2)
    else -> Color(0xFFFF5A1F)
}

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
    val accent = accentColor(accentColor)
    val colorScheme = when {
        themeMode == "amoled" -> darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.24f),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = Color.White,
            tertiary = accent,
            background = Color.Black,
            onBackground = Color(0xFFF4F1EE),
            surface = Color(0xFF090A0D),
            onSurface = Color(0xFFF4F1EE),
            surfaceVariant = Color(0xFF14151A),
            onSurfaceVariant = Color(0xFFB8B2AD),
            surfaceContainer = Color(0xFF0F1014),
            surfaceContainerLow = Color(0xFF101116),
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF191A20),
            surfaceContainerHighest = Color(0xFF23242B),
            secondaryContainer = accent.copy(alpha = 0.22f),
            onSecondaryContainer = Color.White,
            outline = Color(0xFF77727A)
        )
        darkTheme -> darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.24f),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = Color.White,
            tertiary = accent,
            background = Color(0xFF17171A),
            onBackground = Color(0xFFF4F1EE),
            surface = Color(0xFF1E1E22),
            onSurface = Color(0xFFF4F1EE),
            surfaceVariant = Color(0xFF29292E),
            onSurfaceVariant = Color(0xFFB8B2AD),
            surfaceContainer = Color(0xFF202025),
            surfaceContainerLow = Color(0xFF1B1B20),
            surfaceContainerLowest = Color(0xFF151519),
            surfaceContainerHigh = Color(0xFF29292E),
            surfaceContainerHighest = Color(0xFF33333A),
            secondaryContainer = accent.copy(alpha = 0.22f),
            onSecondaryContainer = Color.White,
            outline = Color(0xFF77727A)
        )
        else -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.16f),
            onPrimaryContainer = Color(0xFF24120B),
            secondary = accent,
            onSecondary = Color.White,
            tertiary = accent,
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
            secondaryContainer = accent.copy(alpha = 0.14f),
            onSecondaryContainer = Color(0xFF352018),
            outline = Color(0xFF8A817B)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
