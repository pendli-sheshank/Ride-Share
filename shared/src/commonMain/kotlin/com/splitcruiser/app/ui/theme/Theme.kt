package com.splitcruiser.app.ui.theme

/**
 * Platform-agnostic theme configuration. The palette itself is in [SplitCruiserColors]; the
 * Compose `ColorScheme` is built from it in `app/.../ui/theme/Theme.kt`, and the SwiftUI
 * equivalent in `iosApp/iosApp/Theme.swift`.
 */
object SplitCruiserThemeConfig {
    /**
     * Both platforms render the brand palette in light mode only. Nothing here has dark-mode
     * values yet, and letting the system flip a half-defined palette is how you get white text on
     * a white card.
     */
    const val DEFAULT_DARK_MODE_ENABLED = false
}
