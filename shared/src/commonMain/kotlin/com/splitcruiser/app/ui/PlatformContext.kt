package com.splitcruiser.app.ui

expect object PlatformContext {
    fun vibrate(duration: Long = 50)
    fun showMessage(message: String)
}
