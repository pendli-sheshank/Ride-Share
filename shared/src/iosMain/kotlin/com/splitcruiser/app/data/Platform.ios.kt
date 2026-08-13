package com.splitcruiser.app.data

import platform.Foundation.NSLog

internal actual fun logDebug(tag: String, message: String) {
    NSLog("%s: %s", tag, scrubSensitive(message))
}

internal actual fun logWarn(tag: String, message: String, error: Throwable?) {
    val safe = scrubSensitive(message)
    // NSLog writes to the unified system log in release builds too, so never emit a raw exception
    // string — a Ktor error carries the request URL and its Firebase `?key=` API-key parameter.
    if (error != null) {
        NSLog("%s: %s (%s)", tag, safe, scrubSensitive(error.toString()))
    } else {
        NSLog("%s: %s", tag, safe)
    }
}
