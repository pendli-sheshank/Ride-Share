package com.splitcruiser.app.data

import android.util.Log

internal actual fun logDebug(tag: String, message: String) {
    Log.d(tag, message)
}

internal actual fun logWarn(tag: String, message: String, error: Throwable?) {
    if (error != null) Log.w(tag, message, error) else Log.w(tag, message)
}
