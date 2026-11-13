package com.toska.keystack.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun KeyStackTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark", "amoled" -> true
        else -> isSystemInDarkTheme()
    }
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
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
