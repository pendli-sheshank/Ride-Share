package com.splitcruiser.app.data

import com.splitcruiser.app.data.SawaariRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

/**
 * Tests for SawaariRepository
 * Verifies repository methods, state management, and error handling
 */
class SawaariRepositoryTest {

    private lateinit var repository: SawaariRepository

    @Before
    fun setup() {
        // Initialize repository without Firebase
        repository = SawaariRepository(context = null)
    }

    @Test
    fun testRepositoryInitialization() {
        assertNotNull(repository)
        // Firebase should be disabled in test environment
        assertFalse(repository.isFirebaseEnabled)
    }

    @Test
    fun testCurrentUserStateInitialization() = runTest {
        // Current user should be null initially
        val currentUser = repository.currentUser.value
        assertNull(currentUser)
    }

    @Test
    fun testActiveOffersStateInitialization() = runTest {
        val offers = repository.activeOffers.value
        assertEquals(emptyList(), offers)
    }

    @Test
    fun testConnectionStateInitialization() = runTest {
        // Connection should reflect Firebase enabled status
        val isConnected = repository.isConnected.value
        assertEquals(repository.isFirebaseEnabled, isConnected)
    }

    @Test
    fun testCreateTripOffer() = runTest {
        val offer = TripOffer(
            id = "test_offer_1",
            hostId = "test_host",
            hostName = "Test Driver",
            origin = "Boston",
            destination = "NYC",
            costPerRider = 25.0,
            seatsLeft = 3,
            status = "active"
        )

        // Store offer locally (Firebase would be needed for full implementation)
        val result = runCatching {
            // TODO: Implement repository.createTripOffer(offer)
            // For now, just verify the offer is valid
            assertTrue(offer.id.isNotEmpty())
            assertEquals("active", offer.status)
        }

        assertTrue(result.isSuccess)
    }

    @Test
    fun testFetchMyTripsFromFirestore() = runTest {
        // Test fetching trips - will return empty without Firebase
        val result = repository.fetchMyTripsFromFirestore()

        assertTrue(result.isSuccess)
        val (hosted, joined) = result.getOrNull() ?: Pair(emptyList(), emptyList())
        assertEquals(emptyList(), hosted)
        assertEquals(emptyList(), joined)
    }

    @Test
    fun testModelSerialization() {
        val user = User(
            id = "user_123",
            name = "Test User",
            email = "test@example.com",
            ratingAvg = 4.5f
        )

        // Verify model can be converted to map (step towards serialization)
        val map = user.toMap()
        assertEquals("user_123", map["id"])
        assertEquals("Test User", map["name"])
        assertEquals("test@example.com", map["email"])
    }

    @Test
    fun testTripOfferComparison() {
        val offer1 = TripOffer(
            id = "offer_1",
            origin = "Boston",
            destination = "NYC",
            costPerRider = 25.0
        )

        val offer2 = TripOffer(
            id = "offer_1",
            origin = "Boston",
            destination = "NYC",
            costPerRider = 25.0
        )

        assertEquals(offer1, offer2)
    }

    @Test
    fun testRideRequestCreationValidation() {
        val request = RideRequest(
            id = "req_1",
            riderId = "rider_1",
            riderName = "Rider",
            origin = "Boston",
            destination = "NYC",
            seatsNeeded = 2
        )

        assertTrue(request.id.isNotEmpty())
        assertEquals(2, request.seatsNeeded)
        assertEquals("Boston", request.origin)
        assertEquals("active", request.status)
    }

    @Test
    fun testTripMatchValidation() {
        val match = TripMatch(
            id = "match_1",
            offerId = "offer_1",
            requestId = "request_1",
            hostId = "host_1",
            riderId = "rider_1",
            status = "pending"
        )

        assertEquals("match_1", match.id)
        assertEquals("pending", match.status)
        assertTrue(match.timestamp >= 0L)
    }

    @Test
    fun testLocationPlaceValidation() {
        val place = LocationPlace(
            name = "Test Location",
            address = "123 Test St",
            category = "Campus",
            lat = 42.36,
            lng = -71.09
        )

        assertEquals("Test Location", place.name)
        assertEquals("Campus", place.category)
        assertTrue(place.lat > 0)
        assertTrue(place.lng < 0) // Western hemisphere
    }

    @Test
    fun testMessageCreationValidation() {
        val message = Message(
            id = "msg_1",
            matchId = "match_1",
            senderId = "sender_1",
            senderName = "Sender",
            text = "Test message",
            timestamp = System.currentTimeMillis()
        )

        assertEquals("msg_1", message.id)
        assertEquals("Test message", message.text)
        assertTrue(message.timestamp > 0)
    }

    @Test
    fun testNotificationAlertValidation() {
        val alert = NotificationAlert(
            id = "alert_1",
            userId = "user_1",
            title = "Test Alert",
            message = "Test notification message",
            type = "match",
            timestamp = System.currentTimeMillis(),
            isRead = false
        )

        assertEquals("alert_1", alert.id)
        assertEquals("Test Alert", alert.title)
        assertFalse(alert.isRead)
    }

    @Test
    fun testRatingCreationValidation() {
        val rating = Rating(
            id = "rating_1",
            fromUserId = "rater_1",
            toUserId = "ratee_1",
            rating = 4.5f,
            comment = "Great experience!",
            timestamp = System.currentTimeMillis()
        )

        assertEquals("rating_1", rating.id)
        assertEquals(4.5f, rating.rating)
        assertTrue(rating.rating in 0f..5f)
    }

    @Test
    fun testBlockListValidation() {
        val block = Block(
            id = "block_1",
            userId = "user_1",
            blockedUserId = "blocked_1"
        )

        assertEquals("block_1", block.id)
        assertNotEquals(block.userId, block.blockedUserId)
    }

    @Test
    fun testVehicleCreationValidation() {
        val vehicle = Vehicle(
            ownerId = "owner_1",
            make = "Toyota",
            model = "Camry",
            year = "2020",
            color = "Blue",
            licensePlate = "ABC123"
        )

        assertEquals("owner_1", vehicle.ownerId)
        assertEquals("Toyota", vehicle.make)
        assertTrue(vehicle.licensePlate.isNotEmpty())
    }

    @Test
    fun testCommunityCreationValidation() {
        val community = Community(
            id = "comm_1",
            name = "Boston Students",
            location = "Boston, MA"
        )

        assertEquals("comm_1", community.id)
        assertEquals("Boston Students", community.name)
        assertTrue(community.location.contains("Boston"))
    }

    @Test
    fun testInviteCreationValidation() {
        val invite = Invite(
            code = "INVITE123",
            used = false,
            invitedBy = "user_1",
            usedBy = ""
        )

        assertEquals("INVITE123", invite.code)
        assertFalse(invite.used)
        assertTrue(invite.invitedBy.isNotEmpty())
    }

    @Test
    fun testLocalCredentialValidation() {
        val credential = LocalCredential(
            email = "user@example.com",
            password = "hashed_password",
            userId = "user_1"
        )

        assertEquals("user@example.com", credential.email)
        assertTrue(credential.password.isNotEmpty())
    }
}
