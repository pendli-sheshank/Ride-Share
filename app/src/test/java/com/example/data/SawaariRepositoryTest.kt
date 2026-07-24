package com.example.data

import com.example.data.models.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SawaariRepositoryTest {

    private lateinit var repository: SawaariRepository

    @Before
    fun setUp() {
        repository = SawaariRepository()
    }

    // ==================== Trip Offer Tests ====================

    @Test
    fun testCreateTripOffer_ValidOffer_Succeeds() = runTest {
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

        repository.createTripOffer(offer)
        val result = repository.getTripOffers().firstOrNull { it.id == "offer1" }

        assertNotNull(result)
        assertEquals("offer1", result?.id)
        assertEquals("Cambridge, MA", result?.origin)
        assertEquals(4, result?.totalSeats)
    }

    @Test
    fun testCreateTripOffer_InvalidOrigin_Fails() {
        val offer = TripOffer(
            id = "offer2",
            hostId = "user1",
            origin = "",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            totalSeats = 4,
            costPerRider = 10.0,
            status = "active"
        )

        val result = repository.validateTripOffer(offer)
        assertTrue(result.isFailure)
    }

    @Test
    fun testCreateTripOffer_PastDeparture_Fails() {
        val offer = TripOffer(
            id = "offer3",
            hostId = "user1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() - 1000,
            totalSeats = 4,
            costPerRider = 10.0,
            status = "active"
        )

        val result = repository.validateTripOffer(offer)
        assertTrue(result.isFailure)
    }

    @Test
    fun testGetHostedRides_ReturnsOnlyUserRides() = runTest {
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

        repository.createTripOffer(offer1)
        repository.createTripOffer(offer2)

        val user1Rides = repository.getHostedRides("user1")
        assertEquals(1, user1Rides.size)
        assertEquals("user1", user1Rides[0].hostId)
    }

    // ==================== Ride Request Tests ====================

    @Test
    fun testCreateRideRequest_ValidRequest_Succeeds() = runTest {
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

        repository.createRideRequest(request)
        val result = repository.getRideRequests().firstOrNull { it.id == "request1" }

        assertNotNull(result)
        assertEquals("user1", result?.riderId)
        assertEquals(2, result?.seatsNeeded)
    }

    @Test
    fun testGetRiderRequests_ReturnsOnlyUserRequests() = runTest {
        val request1 = RideRequest(
            id = "request1",
            riderId = "user1",
            origin = "Cambridge, MA",
            destination = "Boston, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            seatsNeeded = 2,
            maxCostPerSeat = 15.0,
            status = "active"
        )
        val request2 = RideRequest(
            id = "request2",
            riderId = "user2",
            origin = "Boston, MA",
            destination = "Cambridge, MA",
            departureTime = System.currentTimeMillis() + 3600000,
            seatsNeeded = 1,
            maxCostPerSeat = 12.0,
            status = "active"
        )

        repository.createRideRequest(request1)
        repository.createRideRequest(request2)

        val user1Requests = repository.getRiderRequests("user1")
        assertEquals(1, user1Requests.size)
        assertEquals("user1", user1Requests[0].riderId)
    }

    // ==================== Cost Calculation Tests ====================

    @Test
    fun testCalculateCostSplit_CorrectDivision() {
        val totalCost = 20.0
        val riders = 4
        val splitCost = repository.calculateCostSplit(totalCost, riders)

        assertEquals(5.0, splitCost, 0.01)
    }

    @Test
    fun testCalculateCostSplit_DivisionByZero_ReturnsZero() {
        val totalCost = 20.0
        val riders = 0
        val splitCost = repository.calculateCostSplit(totalCost, riders)

        assertEquals(0.0, splitCost, 0.01)
    }

    @Test
    fun testCalculateCostSplit_SingleRider() {
        val totalCost = 15.0
        val riders = 1
        val splitCost = repository.calculateCostSplit(totalCost, riders)

        assertEquals(15.0, splitCost, 0.01)
    }

    // ==================== User Tests ====================

    @Test
    fun testCreateUser_ValidUser_Succeeds() = runTest {
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

        repository.createOrUpdateUser(user)
        val result = repository.getUser("user1")

        assertNotNull(result)
        assertEquals("John", result?.firstName)
        assertEquals("D", result?.lastInitial)
    }

    @Test
    fun testGetUserByEmail_ReturnsCorrectUser() = runTest {
        val user = User(
            id = "user1",
            email = "test@mit.edu",
            firstName = "Jane",
            lastInitial = "S",
            rating = 4.5,
            rideCount = 3,
            verified = true,
            communities = listOf("MIT"),
            homeArea = "Cambridge, MA"
        )

        repository.createOrUpdateUser(user)
        val result = repository.getUserByEmail("test@mit.edu")

        assertNotNull(result)
        assertEquals("Jane", result?.firstName)
    }

    // ==================== Trip Match Tests ====================

    @Test
    fun testCreateTripMatch_ValidMatch_Succeeds() = runTest {
        val match = TripMatch(
            id = "match1",
            offerId = "offer1",
            requestId = "request1",
            hostId = "user1",
            riderId = "user2",
            status = "pending",
            contribution = 12.5
        )

        repository.createTripMatch(match)
        val result = repository.getTripMatches().firstOrNull { it.id == "match1" }

        assertNotNull(result)
        assertEquals("pending", result?.status)
        assertEquals(12.5, result?.contribution, 0.01)
    }

    @Test
    fun testUpdateTripMatchStatus_ToAccepted_Succeeds() = runTest {
        val match = TripMatch(
            id = "match1",
            offerId = "offer1",
            requestId = "request1",
            hostId = "user1",
            riderId = "user2",
            status = "pending",
            contribution = 12.5
        )

        repository.createTripMatch(match)
        repository.updateTripMatchStatus("match1", "accepted")

        val result = repository.getTripMatches().firstOrNull { it.id == "match1" }
        assertEquals("accepted", result?.status)
    }

    // ==================== Message Tests ====================

    @Test
    fun testCreateMessage_ValidMessage_Succeeds() = runTest {
        val message = Message(
            id = "msg1",
            matchId = "match1",
            senderId = "user1",
            text = "Hello there!",
            timestamp = System.currentTimeMillis(),
            isSystemMessage = false,
            isRead = false
        )

        repository.createMessage("match1", message)
        val messages = repository.getMatchMessages("match1")

        assertEquals(1, messages.size)
        assertEquals("Hello there!", messages[0].text)
    }

    @Test
    fun testMarkMessageAsRead_UpdatesReadStatus() = runTest {
        val message = Message(
            id = "msg1",
            matchId = "match1",
            senderId = "user1",
            text = "Test message",
            timestamp = System.currentTimeMillis(),
            isSystemMessage = false,
            isRead = false
        )

        repository.createMessage("match1", message)
        repository.markMessageAsRead("msg1")

        val messages = repository.getMatchMessages("match1")
        assertTrue(messages[0].isRead)
    }

    // ==================== Rating Tests ====================

    @Test
    fun testSubmitRating_ValidRating_Succeeds() = runTest {
        val rating = Rating(
            id = "rating1",
            matchId = "match1",
            reviewerId = "user1",
            revieweeId = "user2",
            score = 5,
            comment = "Great driver!",
            timestamp = System.currentTimeMillis()
        )

        repository.submitRating(rating)
        val result = repository.getRatingsForUser("user2")

        assertEquals(1, result.size)
        assertEquals(5, result[0].score)
    }

    @Test
    fun testCalculateAverageRating_CorrectlyComputes() = runTest {
        val rating1 = Rating(
            id = "rating1",
            matchId = "match1",
            reviewerId = "user1",
            revieweeId = "user3",
            score = 5,
            comment = "Excellent!",
            timestamp = System.currentTimeMillis()
        )
        val rating2 = Rating(
            id = "rating2",
            matchId = "match2",
            reviewerId = "user2",
            revieweeId = "user3",
            score = 3,
            comment = "Good",
            timestamp = System.currentTimeMillis()
        )

        repository.submitRating(rating1)
        repository.submitRating(rating2)

        val average = repository.getRatingsForUser("user3").map { it.score }.average()
        assertEquals(4.0, average, 0.01)
    }

    // ==================== Community Tests ====================

    @Test
    fun testGetCommunities_ReturnsAllCommunities() = runTest {
        val communities = repository.getAllCommunities()
        assertTrue(communities.isNotEmpty())
    }

    @Test
    fun testCommunityExists_ValidId_ReturnsTrue() = runTest {
        val communities = repository.getAllCommunities()
        if (communities.isNotEmpty()) {
            val communityId = communities[0].id
            assertTrue(repository.communityExists(communityId))
        }
    }
}
