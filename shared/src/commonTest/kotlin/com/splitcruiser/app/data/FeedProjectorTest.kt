package com.splitcruiser.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Who may see which ride is the only real business logic in the data layer, so it is worth being
 * able to test without a network, a clock or a Firebase project.
 */
class FeedProjectorTest {

    private val now = 1_000_000L
    private val soon = now + 60_000L
    private val past = now - 60_000L

    private val me = User(id = "me", name = "Ana")

    private val womenOnlyOffer =
        TripOffer(id = "wo", hostId = "bo", status = "active", departureTime = soon, womenOnly = true)
    private val openOffer =
        TripOffer(id = "open", hostId = "bo", status = "active", departureTime = soon)

    private fun project(
        user: User? = me,
        offers: List<TripOffer> = emptyList(),
        requests: List<RideRequest> = emptyList(),
        matches: List<TripMatch> = emptyList(),
        blocks: List<Block> = emptyList(),
        eligibleForWomenOnly: Boolean = false,
    ) = FeedProjector.project(user, offers, requests, matches, blocks, now, eligibleForWomenOnly)

    @Test
    fun myOwnOfferIsNotInTheFeedIAmBrowsing() {
        val feeds = project(
            offers = listOf(
                TripOffer(id = "mine", hostId = "me", status = "active", departureTime = soon),
                TripOffer(id = "theirs", hostId = "bo", status = "active", departureTime = soon),
            )
        )
        assertEquals(listOf("theirs"), feeds.activeOffers.map { it.id })
    }

    @Test
    fun departedRidesDropOutOfTheFeed() {
        val feeds = project(
            offers = listOf(
                TripOffer(id = "gone", hostId = "bo", status = "active", departureTime = past),
                TripOffer(id = "upcoming", hostId = "bo", status = "active", departureTime = soon),
            )
        )
        assertEquals(listOf("upcoming"), feeds.activeOffers.map { it.id })
    }

    @Test
    fun onlyActiveOffersAreShown() {
        val feeds = project(
            offers = listOf(
                TripOffer(id = "full", hostId = "bo", status = "full", departureTime = soon),
                TripOffer(id = "cancelled", hostId = "bo", status = "cancelled", departureTime = soon),
                TripOffer(id = "live", hostId = "bo", status = "active", departureTime = soon),
            )
        )
        assertEquals(listOf("live"), feeds.activeOffers.map { it.id })
    }

    @Test
    fun blockingHidesRidesInBothDirections() {
        val offers = listOf(
            TripOffer(id = "byBlocked", hostId = "bo", status = "active", departureTime = soon),
            TripOffer(id = "byBlocker", hostId = "cy", status = "active", departureTime = soon),
            TripOffer(id = "byOther", hostId = "di", status = "active", departureTime = soon),
        )
        val blocks = listOf(
            Block(id = "b1", userId = "me", blockedUserId = "bo"), // I blocked Bo
            Block(id = "b2", userId = "cy", blockedUserId = "me"), // Cy blocked me
        )
        val feeds = project(offers = offers, blocks = blocks)
        assertEquals(listOf("byOther"), feeds.activeOffers.map { it.id })
    }

    // "Women only" is a restriction on who may see and join a ride, and is separate from the
    // viewer's preference about which rides to show. Those two were conflated: the ride was gated
    // solely on `isWomenOnlyFilterEnabled`, a self-service boolean any account could set from its
    // own settings screen, with no gender recorded anywhere and no rule behind it. Flipping a
    // switch was all it took to see every women-only ride on the platform.
    //
    // Eligibility comes from the viewer's private profile (ContactDetails.isEligibleForWomenOnly),
    // not from `User`, because gender does not belong on a document every signed-in user can read.
    // The half with teeth is in firestore.rules, on the write that takes a seat.

    @Test
    fun anIneligibleViewerNeverSeesAWomenOnlyRide() {
        val offers = listOf(womenOnlyOffer)

        assertTrue(project(offers = offers).activeOffers.isEmpty())
        // Not even by turning the settings switch on: that used to be the whole gate.
        assertTrue(
            project(user = me.copy(isWomenOnlyFilterEnabled = true), offers = offers)
                .activeOffers.isEmpty(),
        )
    }

    @Test
    fun anEligibleViewerSeesWomenOnlyRidesAlongsideEverythingElse() {
        val feeds = project(
            offers = listOf(womenOnlyOffer, openOffer),
            eligibleForWomenOnly = true,
        )
        assertEquals(setOf("wo", "open"), feeds.activeOffers.map { it.id }.toSet())
    }

    @Test
    fun theFilterNarrowsAnEligibleViewerToWomenOnlyRides() {
        // This is the preference half: "show me only women-only rides".
        val feeds = project(
            user = me.copy(isWomenOnlyFilterEnabled = true),
            offers = listOf(womenOnlyOffer, openOffer),
            eligibleForWomenOnly = true,
        )
        assertEquals(listOf("wo"), feeds.activeOffers.map { it.id })
    }

    @Test
    fun theFilterIsInertForSomeoneWhoIsNotEligible() {
        // Otherwise an ineligible viewer who flips the switch gets an empty feed and no explanation.
        val feeds = project(
            user = me.copy(isWomenOnlyFilterEnabled = true),
            offers = listOf(openOffer),
        )
        assertEquals(listOf("open"), feeds.activeOffers.map { it.id })
    }

    @Test
    fun theSameRestrictionAppliesToRideRequests() {
        val requests = listOf(
            RideRequest(id = "wo", riderId = "bo", status = "active", departureTime = soon, womenOnly = true),
            RideRequest(id = "open", riderId = "bo", status = "active", departureTime = soon),
        )
        assertEquals(listOf("open"), project(requests = requests).activeRequests.map { it.id })
        assertEquals(
            setOf("wo", "open"),
            project(requests = requests, eligibleForWomenOnly = true).activeRequests.map { it.id }.toSet(),
        )
    }

    @Test
    fun offersAreRankedByHostRating() {
        val feeds = project(
            offers = listOf(
                TripOffer(id = "low", hostId = "bo", status = "active", departureTime = soon, hostRating = 2.0f),
                TripOffer(id = "high", hostId = "cy", status = "active", departureTime = soon, hostRating = 4.8f),
            )
        )
        assertEquals(listOf("high", "low"), feeds.activeOffers.map { it.id })
    }

    @Test
    fun requestsAreOrderedByDeparture() {
        val feeds = project(
            requests = listOf(
                RideRequest(id = "later", riderId = "bo", status = "active", departureTime = soon + 5_000),
                RideRequest(id = "sooner", riderId = "bo", status = "active", departureTime = soon),
            )
        )
        assertEquals(listOf("sooner", "later"), feeds.activeRequests.map { it.id })
    }

    @Test
    fun myOwnRequestsAppearInTheirOwnListRegardlessOfStatus() {
        val feeds = project(
            requests = listOf(
                RideRequest(id = "mine", riderId = "me", status = "matched", departureTime = past),
                RideRequest(id = "theirs", riderId = "bo", status = "active", departureTime = soon),
            )
        )
        assertEquals(listOf("mine"), feeds.myRideRequests.map { it.id })
        // ...and my own request is not in the browse feed.
        assertEquals(listOf("theirs"), feeds.activeRequests.map { it.id })
    }

    @Test
    fun matchesAreMineWhetherIHostOrRide() {
        val feeds = project(
            matches = listOf(
                TripMatch(id = "hosting", hostId = "me", riderId = "bo", timestamp = 2),
                TripMatch(id = "riding", hostId = "cy", riderId = "me", timestamp = 3),
                TripMatch(id = "neither", hostId = "cy", riderId = "bo", timestamp = 4),
            )
        )
        // Newest first.
        assertEquals(listOf("riding", "hosting"), feeds.userMatches.map { it.id })
    }

    @Test
    fun aSignedOutUserSeesEveryLiveRide() {
        val feeds = project(
            user = null,
            offers = listOf(TripOffer(id = "a", hostId = "bo", status = "active", departureTime = soon)),
        )
        assertEquals(listOf("a"), feeds.activeOffers.map { it.id })
        assertTrue(feeds.myRideRequests.isEmpty())
        assertTrue(feeds.userMatches.isEmpty())
    }
}
