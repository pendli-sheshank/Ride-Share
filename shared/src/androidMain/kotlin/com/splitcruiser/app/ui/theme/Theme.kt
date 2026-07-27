package com.splitcruiser.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

actual fun shouldUseDarkTheme(): Boolean {
  return isSystemInDarkTheme()
}

// Compose-specific theme implementations for Android
private val DarkColorScheme = darkColorScheme(
    primary = Color(SawaariColors.Saffron),
    secondary = Color(SawaariColors.Indigo),
    tertiary = Color(SawaariColors.Emerald)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(SawaariColors.Purple40),
    secondary = Color(SawaariColors.PurpleGrey40),
    tertiary = Color(SawaariColors.Pink40)
)

@Composable
fun SawaariTheme(
    darkTheme: Boolean = shouldUseDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
