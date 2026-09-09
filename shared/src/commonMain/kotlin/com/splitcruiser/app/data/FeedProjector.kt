package com.splitcruiser.app.data

/** What the dashboard shows, derived from the cached collections. */
data class Feeds(
    val activeOffers: List<TripOffer>,
    val activeRequests: List<RideRequest>,
    val myRideRequests: List<RideRequest>,
    val userMatches: List<TripMatch>,
)

/**
 * Turns the cached collections into the four lists the UI renders.
 *
 * Pulled out of the repository as a pure function: this is the only real business logic in the data
 * layer — who may see which ride — and it is worth being able to test without a network, a clock or
 * a Firebase project. The rules are carried over unchanged from the Android repository.
 */
object FeedProjector {

    /**
     * [viewerEligibleForWomenOnly] comes from the viewer's own private profile
     * ([ContactDetails.isEligibleForWomenOnly]) — it is not on [User], because gender is not
     * something to publish on a document every signed-in user can read.
     *
     * Note this is the *display* half only. The server-side half is in `firestore.rules`, on the
     * write that actually takes a seat, and that is what makes it a restriction. It deliberately is
     * not on the trip_offers *read* rule: a rule that calls get() breaks list queries, which is a
     * failure this repo has already hit once.
     */
    fun project(
        currentUser: User?,
        offers: Collection<TripOffer>,
        requests: Collection<RideRequest>,
        matches: Collection<TripMatch>,
        blocks: Collection<Block>,
        now: Long,
        viewerEligibleForWomenOnly: Boolean = false,
    ): Feeds {
        val currentUserId = currentUser?.id ?: ""

        // Blocking is symmetric: hide people I blocked and people who blocked me.
        val blockedUserIds =
            blocks.filter { it.userId == currentUserId }.map { it.blockedUserId }.toSet() +
                blocks.filter { it.blockedUserId == currentUserId }.map { it.userId }.toSet()

        // Two independent things, which the old single condition conflated:
        //
        //   `womenOnly` on a ride is a RESTRICTION — only eligible riders may see or join it. It
        //   used to be gated on isWomenOnlyFilterEnabled, so flipping a settings switch was all it
        //   took to see and join every women-only ride on the platform.
        //
        //   `isWomenOnlyFilterEnabled` is a PREFERENCE — "show me only women-only rides". It only
        //   makes sense for someone who is eligible in the first place.
        val preferWomenOnly = viewerEligibleForWomenOnly && currentUser?.isWomenOnlyFilterEnabled == true

        fun womenOnlyPermits(rideIsWomenOnly: Boolean): Boolean =
            if (rideIsWomenOnly) viewerEligibleForWomenOnly else !preferWomenOnly

        val activeOffers = offers.filter { offer ->
            offer.status == "active" &&
                offer.hostId != currentUserId &&
                offer.departureTime > now &&
                offer.hostId !in blockedUserIds &&
                womenOnlyPermits(offer.womenOnly)
        }.sortedByDescending { it.hostRating }

        val activeRequests = requests.filter { request ->
            request.status == "active" &&
                request.riderId != currentUserId &&
                request.departureTime > now &&
                request.riderId !in blockedUserIds &&
                womenOnlyPermits(request.womenOnly)
        }.sortedBy { it.departureTime }

        val userMatches = matches
            .filter { it.hostId == currentUserId || it.riderId == currentUserId }
            .sortedByDescending { it.timestamp }

        val myRideRequests = requests
            .filter { it.riderId == currentUserId }
            .sortedBy { it.departureTime }

        return Feeds(activeOffers, activeRequests, myRideRequests, userMatches)
    }
}
