package com.splitcruiser.app.data

import platform.Foundation.NSLog

internal actual fun logDebug(tag: String, message: String) {
    NSLog("%s: %s", tag, message)
}

internal actual fun logWarn(tag: String, message: String, error: Throwable?) {
    if (error != null) {
        NSLog("%s: %s (%s)", tag, message, error.toString())
    } else {
        NSLog("%s: %s", tag, message)
    }
}
