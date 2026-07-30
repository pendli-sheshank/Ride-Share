package com.splitcruiser.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostControlsPolicyTest {

    @Test
    fun activeOfferAllowsBothActions() {
        val availability = HostControlsPolicy.availability(TripOffer(status = "active"))
        assertTrue(availability.canComplete)
        assertTrue(availability.canCancel)
    }

    @Test
    fun fullOfferAllowsBothActions() {
        val availability = HostControlsPolicy.availability(TripOffer(status = "full"))
        assertTrue(availability.canComplete)
        assertTrue(availability.canCancel)
    }

    @Test
    fun closedOfferStillAllowsBothActions() {
        // closed means "time ran out and this still needs a human answer" — not locked out.
        val availability = HostControlsPolicy.availability(TripOffer(status = "closed"))
        assertTrue(availability.canComplete)
        assertTrue(availability.canCancel)
    }

    @Test
    fun completedOfferAllowsNeitherAction() {
        val availability = HostControlsPolicy.availability(TripOffer(status = "completed"))
        assertFalse(availability.canComplete)
        assertFalse(availability.canCancel)
    }

    @Test
    fun cancelledOfferAllowsNeitherAction() {
        val availability = HostControlsPolicy.availability(TripOffer(status = "cancelled"))
        assertFalse(availability.canComplete)
        assertFalse(availability.canCancel)
    }

    @Test
    fun statusOverloadMatchesOfferOverload() {
        for (status in listOf("active", "full", "closed", "completed", "cancelled")) {
            assertEqualsAvailability(
                HostControlsPolicy.availability(TripOffer(status = status)),
                HostControlsPolicy.availability(status),
            )
        }
    }

    private fun assertEqualsAvailability(a: HostControlsAvailability, b: HostControlsAvailability) {
        assertTrue(a == b)
    }
}
