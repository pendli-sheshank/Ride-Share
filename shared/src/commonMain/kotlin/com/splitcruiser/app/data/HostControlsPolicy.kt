package com.splitcruiser.app.data

/**
 * Which of the two host actions still make sense for a ride.
 *
 * Seats and departure time now drive `active`/`full`/`closed` automatically (see
 * [TripOffer.status]), so a host is left with exactly two real decisions. `closed` still shows
 * both — it means "time ran out and this still needs a human answer," not "locked out."
 */
data class HostControlsAvailability(
    val canComplete: Boolean,
    val canCancel: Boolean,
)

/**
 * Whether a ride still belongs on someone's schedule rather than in their history.
 *
 * `"full"` is the one that catches people out: a ride whose last seat has gone is still very much
 * happening. Android's trips tab filtered on `status == "active"` alone, so a fully-booked ride
 * dropped out of "Rides you're hosting" and reappeared under "Past rides" — while the host
 * dashboard, using its own copy of the rule, got it right. Every ride a driver accepts directly is
 * `"full"` from the moment they accept it, so the two definitions cannot both stay.
 *
 * One object, read by both platforms, for the same reason `statusColor` is one table.
 */
object RideSchedule {
    private val current = setOf("active", "full")

    /** Upcoming or in progress — show it on the schedule. */
    fun isCurrent(status: String): Boolean = status in current

    /** Finished, cancelled or timed out — show it in history. */
    fun isPast(status: String): Boolean = !isCurrent(status)
}

object HostControlsPolicy {
    private val terminal = setOf("completed", "cancelled")

    fun availability(offer: TripOffer): HostControlsAvailability =
        availability(offer.status)

    /** [availability] by status alone, for RideRequest — same terminal/non-terminal split. */
    fun availability(status: String): HostControlsAvailability =
        HostControlsAvailability(
            canComplete = status !in terminal,
            canCancel = status !in terminal,
        )
}
