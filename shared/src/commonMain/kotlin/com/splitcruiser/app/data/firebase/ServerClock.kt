package com.splitcruiser.app.data.firebase

import com.splitcruiser.app.data.nowMs as deviceNowMs
import kotlin.math.abs

/**
 * Tracks how far this device's clock is from the server's, so timestamps a Firestore rule checks
 * against server time are written in server terms.
 *
 * The `notifications` create rule binds `timestamp` to `request.time` with only a minute of slack
 * either way:
 *
 * ```
 * request.resource.data.timestamp <= request.time.toMillis() + 60000 &&
 * request.resource.data.timestamp >= request.time.toMillis() - 300000
 * ```
 *
 * `sendNotificationAlert` filled that field from the device clock. A phone more than a minute fast
 * — which is entirely ordinary; phones with a wrong timezone-adjusted clock, or simply a bad NTP
 * sync, are common — had **every notification it sent denied**, and because the write is wrapped in
 * `runCatching {}` the sender saw nothing and the recipient was never told they had been accepted,
 * matched or messaged. The rule is right: a client-controlled timestamp on a list ordered by
 * timestamp DESC is a way to pin yourself to the top of someone's notifications forever.
 *
 * Every Firebase HTTP response carries a `Date` header, so the offset is available for free on the
 * traffic the app already makes. [observe] is fed by a response observer on the shared Ktor client.
 */
internal class ServerClock {

    /** serverTime - deviceTime, in milliseconds. Zero until the first response is seen. */
    private var offsetMs: Long = 0

    /** Whether a real server `Date` has been observed, as opposed to assuming the device is right. */
    var isSynced: Boolean = false
        private set

    /**
     * Server-anchored wall-clock time.
     *
     * Falls back to the device clock before the first response, which is correct: the very first
     * request of a session cannot have an offset yet, and an unsynced device is no worse off than
     * it was.
     */
    fun nowMs(): Long = deviceNowMs() + offsetMs

    /** Records the server's `Date` header, taken at the moment the response was received. */
    fun observe(httpDateHeader: String?) {
        val serverMs = parseHttpDate(httpDateHeader ?: return) ?: return
        offsetMs = serverMs - deviceNowMs()
        isSynced = true
    }

    /** How far off this device is, for diagnostics and tests. */
    fun offsetForTesting(): Long = offsetMs

    /** True when the device clock is skewed enough that the notification rule would reject it. */
    fun isDeviceClockSkewed(): Boolean = isSynced && abs(offsetMs) > ACCEPTABLE_SKEW_MS

    private companion object {
        /** The tighter half of the notification rule's window, with headroom for flight time. */
        const val ACCEPTABLE_SKEW_MS = 30_000L
    }
}

/**
 * Parses an RFC 7231 IMF-fixdate, the only format an HTTP `Date` header is required to use:
 * `Sun, 06 Nov 1994 08:49:37 GMT`.
 *
 * Hand-rolled rather than pulled from a date library because this has to compile for Kotlin/Native
 * as well as JVM, and the shape is fixed and tiny. Returns null for anything unexpected — an
 * unparseable header must leave the offset alone, not zero it.
 */
internal fun parseHttpDate(value: String): Long? {
    // "Sun, 06 Nov 1994 08:49:37 GMT"
    val parts = value.trim().split(' ')
    if (parts.size < 6) return null

    val day = parts[1].toIntOrNull() ?: return null
    val month = MONTHS.indexOf(parts[2]).takeIf { it >= 0 } ?: return null
    val year = parts[3].toIntOrNull() ?: return null

    val time = parts[4].split(':')
    if (time.size != 3) return null
    val hour = time[0].toIntOrNull() ?: return null
    val minute = time[1].toIntOrNull() ?: return null
    val second = time[2].toIntOrNull() ?: return null

    if (day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null

    return (daysFromCivil(year, month + 1, day) * 86_400L + hour * 3600L + minute * 60L + second) * 1000L
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * Days since the Unix epoch for a proleptic-Gregorian date — Howard Hinnant's `days_from_civil`.
 * Integer arithmetic only, so it behaves identically on every target.
 */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era.toLong() * 146_097L + doe.toLong() - 719_468L
}
