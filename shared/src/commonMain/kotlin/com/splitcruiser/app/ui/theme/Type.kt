package com.splitcruiser.app.ui.theme

// Typography configuration as plain objects - Compose-specific types are in androidMain/iosMain
object SplitCruiserTypography {
    data class TextStyleConfig(
        val fontFamily: String = "Default",
        val fontWeight: Int = 400,
        val fontSize: Int = 16,
        val lineHeight: Int = 24,
        val letterSpacing: Int = 5
    )

    val bodyLarge = TextStyleConfig(
        fontFamily = "Default",
        fontWeight = 400,
        fontSize = 16,
        lineHeight = 24,
        letterSpacing = 5
    )
}
