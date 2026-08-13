package com.splitcruiser.app.data

import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * The handful of things the old Android repository reached for that do not exist in commonMain.
 *
 * `System.currentTimeMillis()`, `java.util.UUID` and `android.util.Log` appear in roughly a hundred
 * places across the code being ported; funnelling them through here is what lets that code compile
 * for iOS. Time and id generation are injectable so tests are deterministic.
 */

/** Overridable so tests can freeze time. */
internal var currentTimeProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() }

internal fun nowMs(): Long = currentTimeProvider()

internal var randomProvider: () -> Random = { Random.Default }

private const val HEX = "0123456789abcdef"

internal fun randomHex(length: Int): String {
    val random = randomProvider()
    return buildString(length) { repeat(length) { append(HEX[random.nextInt(16)]) } }
}

/** Matches the id shapes the existing documents already use, e.g. `offer_1a2b3c4d`. */
internal fun newId(prefix: String): String = "${prefix}_${randomHex(8)}"

internal expect fun logDebug(tag: String, message: String)

internal expect fun logWarn(tag: String, message: String, error: Throwable?)

internal const val LOG_TAG: String = "SplitCruiser"

/**
 * Redacts credentials from anything about to be logged.
 *
 * The values that reach a log are static messages plus, on warnings, an exception. A Ktor exception
 * carries the failed request URL in its message, and Firebase's Identity Toolkit and SecureToken
 * endpoints put the API key in a `?key=…` query parameter (and the Google sign-in body an
 * `id_token=…`). Passing such a throwable straight to `Log.w`/`NSLog` would surface those in logcat
 * or the iOS unified log, both readable off-device in the wrong hands. The platform log sinks route
 * every message and exception string through here first.
 */
internal fun scrubSensitive(text: String): String =
    SENSITIVE_PARAM.replace(text) { "${it.groupValues[1]}=REDACTED" }

private val SENSITIVE_PARAM =
    Regex("(?i)(key|password|access_token|refresh_token|id_token|token)=([^&\\s\"')]+)")
