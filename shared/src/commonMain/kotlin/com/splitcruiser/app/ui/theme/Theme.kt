package com.splitcruiser.app.ui.theme

// Platform-agnostic theme configuration
expect fun shouldUseDarkTheme(): Boolean

object SawaariThemeConfig {
    fun isDark(): Boolean = shouldUseDarkTheme()
}
