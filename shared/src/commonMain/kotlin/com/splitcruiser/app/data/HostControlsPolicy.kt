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
