package com.splitcruiser.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * A real `ColorScheme` built from the brand palette, so Material components that are *not* given
 * explicit colours — a stock `AlertDialog`, text-selection handles, the default ripple — land on
 * the same blue as everything the screens paint by hand.
 *
 * Dynamic (wallpaper-derived) colour is deliberately not used. It was on by default, and because
 * almost nothing in `SplitCruiserApp.kt` reads `MaterialTheme.colorScheme`, it meant the app
 * silently forked its own look on Android 12+: hardcoded brand blue next to whatever the user's
 * wallpaper produced.
 *
 * Light-only, for the same reason: the palette has no dark values, so there is nothing honest to
 * switch to.
 */
private val SplitCruiserColorScheme =
  lightColorScheme(
    primary = SplitCruiserPrimary,
    onPrimary = SplitCruiserOnPrimary,
    primaryContainer = SplitCruiserPrimaryContainer,
    onPrimaryContainer = SplitCruiserOnPrimaryContainer,
    secondary = SplitCruiserPrimary,
    onSecondary = SplitCruiserOnPrimary,
    secondaryContainer = SplitCruiserPrimaryContainer,
    onSecondaryContainer = SplitCruiserOnPrimaryContainer,
    tertiary = SplitCruiserSuccess,
    onTertiary = SplitCruiserOnPrimary,
    background = SplitCruiserSurface,
    onBackground = SplitCruiserTextPrimary,
    surface = SplitCruiserSurfaceCard,
    onSurface = SplitCruiserTextPrimary,
    surfaceVariant = SplitCruiserSurfaceMuted,
    onSurfaceVariant = SplitCruiserTextSecondary,
    outline = SplitCruiserOutline,
    outlineVariant = SplitCruiserOutline,
    error = SplitCruiserDanger,
    onError = SplitCruiserOnPrimary,
  )

@Composable
fun SplitCruiserTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = SplitCruiserColorScheme, typography = Typography, content = content)
}
