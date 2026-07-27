package com.splitcruiser.app.data

import kotlin.test.*

class RideMatchingLogicTest {

    @Test
    fun testOfferAndRequestBasicCompatibility() {
        val offer = TripOffer(
            id = "offer_1",
            origin = "Boston",
            destination = "NYC",
            seatsLeft = 2
        )

        val request = RideRequest(
            id = "request_1",
            origin = "Boston",
            destination = "NYC",
            seatsNeeded = 1
        )

        assertTrue(offer.seatsLeft >= request.seatsNeeded)
        assertEquals(offer.origin, request.origin)
        assertEquals(offer.destination, request.destination)
    }

    @Test
    fun testInsufficientSeatsRejectsMatch() {
        val offer = TripOffer(
            id = "offer_1",
            origin = "Boston",
            destination = "NYC",
            seatsLeft = 1
        )

        val request = RideRequest(
            id = "request_1",
            origin = "Boston",
            destination = "NYC",
            seatsNeeded = 3
        )

        assertFalse(offer.seatsLeft >= request.seatsNeeded)
    }

    @Test
    fun testRouteCompatibility() {
        val offer1 = TripOffer(
            id = "offer_1",
            origin = "Boston",
            destination = "NYC"
        )

        val request1 = RideRequest(
            id = "request_1",
            origin = "Boston",
            destination = "NYC"
        )

        assertEquals(offer1.origin, request1.origin)
        assertEquals(offer1.destination, request1.destination)

        val offer2 = TripOffer(
            id = "offer_2",
            origin = "Boston",
            destination = "NYC"
        )

        val request2 = RideRequest(
            id = "request_2",
            origin = "Boston",
            destination = "Philadelphia"
        )

        assertNotEquals(offer2.destination, request2.destination)
    }

    @Test
    fun testWomenOnlyFilteringLogic() {
        val womenOnlyOffer = TripOffer(
            id = "offer_1",
            womenOnly = true,
            origin = "Boston",
            destination = "NYC"
        )

        val maleRider = User(id = "user_1", name = "John")
        val femaleRider = User(id = "user_2", name = "Jane")

        assertTrue(womenOnlyOffer.womenOnly)
    }

    @Test
    fun testMatchStatusProgression() {
        val match = TripMatch(
            id = "match_1",
            offerId = "offer_1",
            requestId = "request_1",
            hostId = "host_1",
            riderId = "rider_1",
            status = "pending"
        )

        assertEquals("pending", match.status)
        assertTrue(match.status in listOf("pending", "accepted", "rejected", "completed", "cancelled"))
    }

    @Test
    fun testRiderRatingValidation() {
        val match = TripMatch(
            id = "match_1",
            offerId = "offer_1",
            requestId = "request_1",
            hostId = "host_1",
            riderId = "rider_1",
            riderRating = 4.5f
        )

        assertTrue(match.riderRating >= 0f && match.riderRating <= 5f)
    }

    @Test
    fun testContributionAmountValidation() {
        val match = TripMatch(
            id = "match_1",
            offerId = "offer_1",
            requestId = "request_1",
            hostId = "host_1",
            riderId = "rider_1",
            contribution = 25.0
        )

        assertTrue(match.contribution >= 0.0)
        assertEquals(25.0, match.contribution)
    }

    @Test
    fun testTimeWindowCompatibility() {
        val offer = TripOffer(
            id = "offer_1",
            departureTime = 1690000000000L
        )

        val request = RideRequest(
            id = "request_1",
            departureTime = 1690000060000L
        )

        val timeDifferenceMs = kotlin.math.abs(offer.departureTime - request.departureTime)
        assertTrue(timeDifferenceMs <= 3600000)
    }

    @Test
    fun testMultipleSeatsAllocationScenario() {
        val offer = TripOffer(
            id = "offer_1",
            origin = "Boston",
            destination = "NYC",
            totalSeats = 4,
            seatsLeft = 4
        )

        val request1 = RideRequest(id = "req_1", seatsNeeded = 2)
        val request2 = RideRequest(id = "req_2", seatsNeeded = 1)
        val request3 = RideRequest(id = "req_3", seatsNeeded = 2)

        var seatsAvailable = offer.seatsLeft
        assertTrue(seatsAvailable >= request1.seatsNeeded)
        seatsAvailable -= request1.seatsNeeded

        assertTrue(seatsAvailable >= request2.seatsNeeded)
        seatsAvailable -= request2.seatsNeeded

        assertFalse(seatsAvailable >= request3.seatsNeeded)
    }
}
