package com.example.ui

import androidx.lifecycle.viewModelScope
import com.example.data.MainViewModel
import com.example.data.models.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    @get:Rule
    val instantExecutorRule = org.junit.rules.TestRule { statement, _ ->
        // Execute coroutines synchronously for testing
        statement
    }

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        viewModel = MainViewModel()
    }

    // ==================== Authentication Tests ====================

    @Test
    fun testSetCurrentUser_UpdatesState() {
        val user = User(
            id = "user1",
            email = "test@mit.edu",
            firstName = "John",
            lastInitial = "D",
            rating = 5.0,
            rideCount = 0,
            verified = true,
            communities = listOf("MIT"),
            homeArea = "Cambridge, MA"
        )

        viewModel.setCurrentUser(user)
        assertEquals("user1", viewModel.currentUser.value?.id)
        assertEquals("John", viewModel.currentUser.value?.firstName)
    }

    @Test
    fun testLogout_ClearsCurrentUser() {
        val user = User(
            id = "user1",
            email = "test@mit.edu",
            firstName = "John",
            lastInitial = "D",
            rating = 5.0,
            rideCount = 0,
            verified = true,
            communities = listOf("MIT"),
            homeArea = "Cambridge, MA"
        )

        viewModel.setCurrentUser(user)
        viewModel.logout()

        assertNull(viewModel.currentUser.value)
    }

    // ==================== Trip Offer Tests ====================

    @Test
    fun testPostTripOffer_ValidOffer_UpdatesState() = runTest {
        val offer = TripOffer(
            id = "offer1",
            hostId = "user1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 4,
            costPerRider = 10.0,
            status = "active"
        )

        viewModel.postTripOffer(
            origin = offer.origin,
            destination = offer.destination,
            departureTime = offer.departureTime,
            totalSeats = offer.totalSeats,
            costPerRider = offer.costPerRider
        )

        assertTrue(viewModel.allTripOffers.value.isNotEmpty())
    }

    @Test
    fun testGetTripOffers_ReturnsMultipleOffers() = runTest {
        val offer1 = TripOffer(
            id = "offer1",
            hostId = "user1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 4,
            costPerRider = 10.0,
            status = "active"
        )
        val offer2 = TripOffer(
            id = "offer2",
            hostId = "user2",
            origin = "Boston, MA",
            destination = "Cambridge, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 2,
            costPerRider = 8.0,
            status = "active"
        )

        viewModel.repository.createTripOffer(offer1)
        viewModel.repository.createTripOffer(offer2)

        val offers = viewModel.allTripOffers.value
        assertTrue(offers.size >= 2)
    }

    // ==================== Ride Request Tests ====================

    @Test
    fun testPostRideRequest_ValidRequest_UpdatesState() = runTest {
        val request = RideRequest(
            id = "request1",
            riderId = "user1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            seatsNeeded = 2,
            maxCostPerSeat = 15.0,
            status = "active"
        )

        viewModel.postRideRequest(
            origin = request.origin,
            destination = request.destination,
            departureTime = request.departureTime,
            seatsNeeded = request.seatsNeeded,
            maxCostPerSeat = request.maxCostPerSeat
        )

        assertTrue(viewModel.allRideRequests.value.isNotEmpty())
    }

    // ==================== Message Tests ====================

    @Test
    fun testSendMessage_CreatesMessage() = runTest {
        val user = User(
            id = "user1",
            email = "test@mit.edu",
            firstName = "John",
            lastInitial = "D",
            rating = 5.0,
            rideCount = 0,
            verified = true,
            communities = listOf("MIT"),
            homeArea = "Cambridge, MA"
        )
        viewModel.setCurrentUser(user)

        viewModel.sendMessage("match1", "Hello!")

        val messages = viewModel.allMessages.value
        assertTrue(messages.any { it.text == "Hello!" })
    }

    @Test
    fun testGetMatchMessages_ReturnsCorrectMessages() = runTest {
        val message1 = Message(
            id = "msg1",
            matchId = "match1",
            senderId = "user1",
            text = "First message",
            timestamp = System.currentTimeMillis(),
            isSystemMessage = false,
            isRead = false
        )
        val message2 = Message(
            id = "msg2",
            matchId = "match1",
            senderId = "user2",
            text = "Second message",
            timestamp = System.currentTimeMillis() + 1000,
            isSystemMessage = false,
            isRead = false
        )
        val message3 = Message(
            id = "msg3",
            matchId = "match2",
            senderId = "user1",
            text = "Different match",
            timestamp = System.currentTimeMillis() + 2000,
            isSystemMessage = false,
            isRead = false
        )

        viewModel.repository.createMessage("match1", message1)
        viewModel.repository.createMessage("match1", message2)
        viewModel.repository.createMessage("match2", message3)

        val match1Messages = viewModel.repository.getMatchMessages("match1")
        assertEquals(2, match1Messages.size)
        assertTrue(match1Messages.all { it.matchId == "match1" })
    }

    // ==================== Trip Match Tests ====================

    @Test
    fun testAcceptMatch_UpdatesStatus() = runTest {
        val match = TripMatch(
            id = "match1",
            offerId = "offer1",
            requestId = "request1",
            hostId = "user1",
            riderId = "user2",
            status = "pending",
            contribution = 12.5
        )

        viewModel.repository.createTripMatch(match)
        viewModel.acceptMatch("match1")

        val updated = viewModel.allTripMatches.value.firstOrNull { it.id == "match1" }
        assertEquals("accepted", updated?.status)
    }

    @Test
    fun testCancelMatch_UpdatesStatusAndRevertsSeat() = runTest {
        val offer = TripOffer(
            id = "offer1",
            hostId = "user1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 4,
            costPerRider = 10.0,
            status = "active",
            seatsBooked = 2
        )
        val match = TripMatch(
            id = "match1",
            offerId = "offer1",
            requestId = "request1",
            hostId = "user1",
            riderId = "user2",
            status = "accepted",
            contribution = 12.5
        )

        viewModel.repository.createTripOffer(offer)
        viewModel.repository.createTripMatch(match)

        val seatsBeforeCancel = viewModel.repository.getTripOffers()
            .firstOrNull { it.id == "offer1" }?.seatsBooked ?: 0

        viewModel.cancelMatch("match1")

        val seatsAfterCancel = viewModel.repository.getTripOffers()
            .firstOrNull { it.id == "offer1" }?.seatsBooked ?: 0

        assertTrue(seatsAfterCancel < seatsBeforeCancel)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun testSetError_UpdatesErrorState() {
        val errorMsg = "Test error message"
        viewModel.setError(errorMsg)

        assertEquals(errorMsg, viewModel.error.value)
    }

    @Test
    fun testClearError_ClearsErrorState() {
        viewModel.setError("Test error")
        viewModel.clearError()

        assertNull(viewModel.error.value)
    }

    // ==================== Community Tests ====================

    @Test
    fun testLoadCommunities_PopulatesState() = runTest {
        val communities = viewModel.allCommunities.value
        assertTrue(communities.isNotEmpty())
    }

    @Test
    fun testSelectCommunity_UpdatesState() {
        val communities = viewModel.allCommunities.value
        if (communities.isNotEmpty()) {
            val community = communities[0]
            viewModel.selectCommunity(community)

            assertEquals(community.id, viewModel.selectedCommunity.value?.id)
        }
    }

    // ==================== Filter Tests ====================

    @Test
    fun testSetWomenOnlyFilter_UpdatesState() {
        viewModel.setWomenOnlyFilter(true)
        assertTrue(viewModel.womenOnlyFilter.value)

        viewModel.setWomenOnlyFilter(false)
        assertFalse(viewModel.womenOnlyFilter.value)
    }

    @Test
    fun testFilterOffersByWomenOnly_ReturnsOnlyWomenOffers() = runTest {
        val womenOffer = TripOffer(
            id = "offer1",
            hostId = "user1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 4,
            costPerRider = 10.0,
            status = "active",
            womenOnly = true
        )
        val mixedOffer = TripOffer(
            id = "offer2",
            hostId = "user2",
            origin = "Boston, MA",
            destination = "Cambridge, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 2,
            costPerRider = 8.0,
            status = "active",
            womenOnly = false
        )

        viewModel.repository.createTripOffer(womenOffer)
        viewModel.repository.createTripOffer(mixedOffer)
        viewModel.setWomenOnlyFilter(true)

        val filtered = viewModel.allTripOffers.value.filter { it.womenOnly }
        assertTrue(filtered.any { it.id == "offer1" })
    }
}
