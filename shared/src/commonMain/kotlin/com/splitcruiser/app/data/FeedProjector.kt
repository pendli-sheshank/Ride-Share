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

    fun project(
        currentUser: User?,
        offers: Collection<TripOffer>,
        requests: Collection<RideRequest>,
        matches: Collection<TripMatch>,
        blocks: Collection<Block>,
        now: Long,
    ): Feeds {
        val currentUserId = currentUser?.id ?: ""

        // Blocking is symmetric: hide people I blocked and people who blocked me.
        val blockedUserIds =
            blocks.filter { it.userId == currentUserId }.map { it.blockedUserId }.toSet() +
                blocks.filter { it.blockedUserId == currentUserId }.map { it.userId }.toSet()

        val activeOffers = offers.filter { offer ->
            offer.status == "active" &&
                offer.hostId != currentUserId &&
                offer.departureTime > now &&
                offer.hostId !in blockedUserIds &&
                (!offer.womenOnly || currentUser?.isWomenOnlyFilterEnabled == true)
        }.sortedByDescending { it.hostRating }

        val activeRequests = requests.filter { request ->
            request.status == "active" &&
                request.riderId != currentUserId &&
                request.departureTime > now &&
                request.riderId !in blockedUserIds &&
                (!request.womenOnly || currentUser?.isWomenOnlyFilterEnabled == true)
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
