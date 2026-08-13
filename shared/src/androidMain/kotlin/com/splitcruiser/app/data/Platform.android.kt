package com.splitcruiser.app.data

import android.util.Log

internal actual fun logDebug(tag: String, message: String) {
    Log.d(tag, scrubSensitive(message))
}

internal actual fun logWarn(tag: String, message: String, error: Throwable?) {
    val safe = scrubSensitive(message)
    // Do not hand the raw Throwable to Log.w: a Ktor exception's message can carry the request URL,
    // which includes the Firebase API key as a `?key=` query parameter. Log a scrubbed one-liner.
    if (error != null) {
        Log.w(tag, "$safe | ${error::class.simpleName}: ${scrubSensitive(error.message ?: "")}")
    } else {
        Log.w(tag, safe)
    }
}
