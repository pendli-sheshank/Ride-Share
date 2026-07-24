package com.example.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.MainViewModel
import com.example.data.models.*
import com.example.ui.theme.SawaariTheme
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class RideFlowIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        viewModel = MainViewModel()
    }

    @Test
    fun testHostPostsRideAndRiderMatches() = runTest {
        // Setup: Create host user
        val host = User(
            id = "host1",
            email = "host@mit.edu",
            firstName = "Alice",
            lastInitial = "H",
            rating = 5.0,
            rideCount = 10,
            verified = true,
            communities = listOf("MIT"),
            homeArea = "Cambridge, MA"
        )
        viewModel.setCurrentUser(host)

        // Host posts a ride offer
        viewModel.postTripOffer(
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 4,
            costPerRider = 12.0
        )

        // Verify offer was created
        val offers = viewModel.allTripOffers.value
        assert(offers.isNotEmpty()) { "No trip offers found" }
        assert(offers.last().origin == "Cambridge, MA")
        assert(offers.last().totalSeats == 4)

        // Setup: Switch to rider
        val rider = User(
            id = "rider1",
            email = "rider@northeastern.edu",
            firstName = "Bob",
            lastInitial = "R",
            rating = 4.5,
            rideCount = 5,
            verified = true,
            communities = listOf("Northeastern"),
            homeArea = "Boston, MA"
        )
        viewModel.setCurrentUser(rider)

        // Rider posts matching request
        viewModel.postRideRequest(
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            seatsNeeded = 2,
            maxCostPerSeat = 15.0
        )

        // Verify request was created
        val requests = viewModel.allRideRequests.value
        assert(requests.isNotEmpty()) { "No ride requests found" }
    }

    @Test
    fun testMatchCreationAndMessageFlow() = runTest {
        // Create host and rider users
        val host = User(
            id = "host1",
            email = "host@mit.edu",
            firstName = "Alice",
            lastInitial = "H",
            rating = 5.0,
            rideCount = 10,
            verified = true,
            communities = listOf("MIT"),
            homeArea = "Cambridge, MA"
        )
        val rider = User(
            id = "rider1",
            email = "rider@northeastern.edu",
            firstName = "Bob",
            lastInitial = "R",
            rating = 4.5,
            rideCount = 5,
            verified = true,
            communities = listOf("Northeastern"),
            homeArea = "Boston, MA"
        )

        // Create offer and request
        val offer = TripOffer(
            id = "offer1",
            hostId = "host1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 4,
            costPerRider = 12.0,
            status = "active"
        )
        val request = RideRequest(
            id = "request1",
            riderId = "rider1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            seatsNeeded = 2,
            maxCostPerSeat = 15.0,
            status = "active"
        )

        viewModel.repository.createTripOffer(offer)
        viewModel.repository.createRideRequest(request)

        // Create match
        val match = TripMatch(
            id = "match1",
            offerId = "offer1",
            requestId = "request1",
            hostId = "host1",
            riderId = "rider1",
            status = "pending",
            contribution = 12.0
        )
        viewModel.repository.createTripMatch(match)

        // Verify match was created
        val matches = viewModel.allTripMatches.value
        assert(matches.isNotEmpty()) { "No matches found" }
        assert(matches.last().status == "pending")

        // Send message from host
        viewModel.setCurrentUser(host)
        viewModel.sendMessage("match1", "Hi Bob! I'll pick you up at 10 AM")

        // Verify message was created
        val messages = viewModel.repository.getMatchMessages("match1")
        assert(messages.isNotEmpty()) { "No messages found" }
        assert(messages.any { it.text.contains("pick you up") })

        // Rider responds
        viewModel.setCurrentUser(rider)
        viewModel.sendMessage("match1", "Sounds good! I'll be ready")

        val updatedMessages = viewModel.repository.getMatchMessages("match1")
        assert(updatedMessages.size >= 2) { "Expected at least 2 messages" }
    }

    @Test
    fun testAcceptMatchAndRating() = runTest {
        // Setup match
        val match = TripMatch(
            id = "match1",
            offerId = "offer1",
            requestId = "request1",
            hostId = "host1",
            riderId = "rider1",
            status = "pending",
            contribution = 12.0
        )
        viewModel.repository.createTripMatch(match)

        // Accept match
        viewModel.acceptMatch("match1")

        // Verify status changed to accepted
        val updated = viewModel.allTripMatches.value.firstOrNull { it.id == "match1" }
        assert(updated?.status == "accepted") { "Match status not updated" }

        // Submit rating
        val rating = Rating(
            id = "rating1",
            matchId = "match1",
            reviewerId = "host1",
            revieweeId = "rider1",
            score = 5,
            comment = "Great passenger!",
            timestamp = System.currentTimeMillis()
        )
        viewModel.repository.submitRating(rating)

        // Verify rating was saved
        val ratings = viewModel.repository.getRatingsForUser("rider1")
        assert(ratings.isNotEmpty()) { "No ratings found" }
        assert(ratings.last().score == 5)
        assert(ratings.last().comment == "Great passenger!")
    }

    @Test
    fun testCostCalculationAndSplit() = runTest {
        val totalCost = 48.0
        val riders = 4

        val splitCost = viewModel.repository.calculateCostSplit(totalCost, riders)

        assert(splitCost == 12.0) { "Cost split calculation incorrect: $splitCost != 12.0" }
    }

    @Test
    fun testFiltersWork() = runTest {
        // Create offers with different properties
        val womenOnlyOffer = TripOffer(
            id = "offer1",
            hostId = "host1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 4,
            costPerRider = 12.0,
            status = "active",
            womenOnly = true
        )
        val mixedOffer = TripOffer(
            id = "offer2",
            hostId = "host2",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 2,
            costPerRider = 10.0,
            status = "active",
            womenOnly = false
        )

        viewModel.repository.createTripOffer(womenOnlyOffer)
        viewModel.repository.createTripOffer(mixedOffer)

        // Apply women-only filter
        viewModel.setWomenOnlyFilter(true)

        // Get filtered offers
        val offers = viewModel.allTripOffers.value
        val womenOnlyOffers = offers.filter { it.womenOnly }

        assert(womenOnlyOffers.isNotEmpty()) { "No women-only offers found" }
        assert(womenOnlyOffers.all { it.womenOnly }) { "Filter returned non-women-only offers" }
    }

    @Test
    fun testMatchCancellationReverts Seats() = runTest {
        // Create offer
        val offer = TripOffer(
            id = "offer1",
            hostId = "host1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 4,
            costPerRider = 12.0,
            status = "active",
            seatsBooked = 2
        )
        viewModel.repository.createTripOffer(offer)

        // Create match
        val match = TripMatch(
            id = "match1",
            offerId = "offer1",
            requestId = "request1",
            hostId = "host1",
            riderId = "rider1",
            status = "accepted",
            contribution = 12.0
        )
        viewModel.repository.createTripMatch(match)

        val seatsBeforeCancel = viewModel.repository.getTripOffers()
            .firstOrNull { it.id == "offer1" }?.seatsBooked ?: 0

        // Cancel match
        viewModel.cancelMatch("match1")

        val seatsAfterCancel = viewModel.repository.getTripOffers()
            .firstOrNull { it.id == "offer1" }?.seatsBooked ?: 0

        assert(seatsAfterCancel < seatsBeforeCancel) { "Seats not reverted after cancellation" }
    }
}
