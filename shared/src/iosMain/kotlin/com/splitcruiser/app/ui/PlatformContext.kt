package com.splitcruiser.app.ui

actual object PlatformContext {
    actual fun vibrate(duration: Long) {
        // TODO: Implement iOS haptic feedback using UIImpactFeedbackGenerator
    }

    actual fun showMessage(message: String) {
        // TODO: Implement iOS alert/toast using UIAlertController or similar
    }
}
