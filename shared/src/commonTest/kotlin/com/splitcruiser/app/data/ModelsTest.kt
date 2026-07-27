package com.splitcruiser.app.data

import kotlin.test.*

/**
 * Tests for shared data models
 * Verifies model creation, serialization, and business logic
 */
class ModelsTest {

    @Test
    fun testUserCreation() {
        val user = User(
            id = "user_123",
            phoneNumber = "+1-555-0100",
            email = "alice@example.com",
            name = "Alice",
            lastInitial = "A",
            avatarUrl = "https://example.com/avatar.jpg",
            verifiedTier = "vouched",
            ratingAvg = 4.8f,
            ratingCount = 15
        )

        assertEquals("user_123", user.id)
        assertEquals("Alice", user.name)
        assertEquals("Alice A.", user.displayName)
        assertEquals(4.8f, user.ratingAvg)
    }

    @Test
    fun testUserDisplayName() {
        val userWithInitial = User(name = "Bob", lastInitial = "B")
        assertEquals("Bob B.", userWithInitial.displayName)

        val userNoInitial = User(name = "Charlie", lastInitial = "")
        assertEquals("Charlie", userNoInitial.displayName)
    }

    @Test
    fun testTripOfferCreation() {
        val offer = TripOffer(
            id = "offer_456",
            hostId = "host_123",
            hostName = "Driver Dave",
            origin = "Boston",
            destination = "New York",
            originLat = 42.3601,
            originLng = -71.0589,
            destLat = 40.7128,
            destLng = -74.0060,
            departureTime = 1690000000000L,
            totalSeats = 4,
            seatsLeft = 2,
            costPerRider = 25.0,
            womenOnly = false,
            status = "active"
        )

        assertEquals("offer_456", offer.id)
        assertEquals("Boston", offer.origin)
        assertEquals("New York", offer.destination)
        assertEquals(2, offer.seatsLeft)
        assertEquals(4, offer.totalSeats)
        assertEquals(25.0, offer.costPerRider)
        assertTrue(offer.status == "active")
    }

    @Test
    fun testRideRequestCreation() {
        val request = RideRequest(
            id = "request_789",
            riderId = "rider_456",
            riderName = "Passenger Paul",
            origin = "MIT",
            destination = "Logan Airport",
            originLat = 42.3592,
            originLng = -71.0932,
            destLat = 42.3656,
            destLng = -71.0096,
            departureTime = 1690010000000L,
            seatsNeeded = 2,
            notes = "Have luggage",
            womenOnly = false,
            status = "active"
        )

        assertEquals("request_789", request.id)
        assertEquals("Passenger Paul", request.riderName)
        assertEquals(2, request.seatsNeeded)
        assertEquals("Have luggage", request.notes)
    }

    @Test
    fun testTripMatchCreation() {
        val match = TripMatch(
            id = "match_001",
            offerId = "offer_456",
            requestId = "request_789",
            hostId = "host_123",
            riderId = "rider_456",
            riderName = "Passenger Paul",
            riderRating = 4.5f,
            contribution = 25.0,
            status = "pending",
            timestamp = 1690000000000L
        )

        assertEquals("match_001", match.id)
        assertEquals("offer_456", match.offerId)
        assertEquals("request_789", match.requestId)
        assertEquals("pending", match.status)
    }

    @Test
    fun testMessageCreation() {
        val message = Message(
            id = "msg_123",
            matchId = "match_001",
            senderId = "user_123",
            senderName = "Alice",
            text = "When should I pick you up?",
            timestamp = 1690000000000L
        )

        assertEquals("msg_123", message.id)
        assertEquals("match_001", message.matchId)
        assertEquals("When should I pick you up?", message.text)
    }

    @Test
    fun testRatingCreation() {
        val rating = Rating(
            id = "rating_001",
            fromUserId = "user_123",
            toUserId = "user_456",
            rating = 5.0f,
            comment = "Great driver!",
            timestamp = 1690000000000L
        )

        assertEquals("rating_001", rating.id)
        assertEquals(5.0f, rating.rating)
        assertEquals("Great driver!", rating.comment)
    }

    @Test
    fun testMatchDetailsCreation() {
        val match = TripMatch(id = "match_001", status = "accepted")
        val offer = TripOffer(id = "offer_456", origin = "Boston", destination = "NYC")
        val request = RideRequest(id = "request_789", origin = "Boston", destination = "NYC")
        val driver = User(id = "driver_1", name = "Driver")
        val rider = User(id = "rider_1", name = "Rider")

        val details = MatchDetails(
            match = match,
            offer = offer,
            request = request,
            hostProfile = driver,
            riderProfile = rider
        )

        assertEquals("match_001", details.match.id)
        assertEquals("Boston", details.offer.origin)
        assertEquals("Driver", details.hostProfile?.name)
        assertEquals("Rider", details.riderProfile?.name)
    }

    @Test
    fun testLocationPlaceCreation() {
        val place = LocationPlace(
            name = "Logan International Airport",
            address = "1 Harborside Drive, Boston, MA 02128",
            category = "Airport",
            lat = 42.3656,
            lng = -71.0096
        )

        assertEquals("Logan International Airport", place.name)
        assertEquals("Airport", place.category)
        assertEquals(42.3656, place.lat)
    }

    @Test
    fun testDefaultLocationPlaces() {
        assertTrue(DEFAULT_LOCATION_PLACES.isNotEmpty())

        // Verify some well-known places exist
        val nehuLibrary = DEFAULT_LOCATION_PLACES.find { it.name.contains("Snell Library") }
        assertNotNull(nehuLibrary)
        assertEquals("Campus", nehuLibrary.category)

        val logon = DEFAULT_LOCATION_PLACES.find { it.name.contains("Logan") }
        assertNotNull(logon)
        assertEquals("Airport", logon.category)
    }

    @Test
    fun testTripOfferToMap() {
        val offer = TripOffer(
            id = "offer_123",
            hostId = "host_456",
            hostName = "Driver",
            origin = "Boston",
            destination = "NYC",
            costPerRider = 25.0,
            seatsLeft = 2
        )

        val map = offer.toMap()
        assertEquals("offer_123", map["id"])
        assertEquals("host_456", map["hostId"])
        assertEquals("Boston", map["origin"])
        assertEquals(25.0, map["costPerRider"])
        assertEquals(2, map["seatsLeft"])
    }

    @Test
    fun testRideRequestToMap() {
        val request = RideRequest(
            id = "request_123",
            riderId = "rider_456",
            riderName = "Passenger",
            origin = "MIT",
            destination = "Airport",
            seatsNeeded = 2
        )

        val map = request.toMap()
        assertEquals("request_123", map["id"])
        assertEquals("rider_456", map["riderId"])
        assertEquals("Passenger", map["riderName"])
        assertEquals(2, map["seatsNeeded"])
    }

    @Test
    fun testUserToMap() {
        val user = User(
            id = "user_123",
            name = "Alice",
            email = "alice@example.com",
            phoneNumber = "+1-555-0100",
            ratingAvg = 4.8f
        )

        val map = user.toMap()
        assertEquals("user_123", map["id"])
        assertEquals("Alice", map["name"])
        assertEquals("alice@example.com", map["email"])
        assertEquals(4.8f, map["ratingAvg"])
    }

    @Test
    fun testMessageToMap() {
        val message = Message(
            id = "msg_123",
            matchId = "match_001",
            senderId = "user_123",
            senderName = "Alice",
            text = "Hello!",
            timestamp = 1690000000000L
        )

        val map = message.toMap()
        assertEquals("msg_123", map["id"])
        assertEquals("match_001", map["matchId"])
        assertEquals("Hello!", map["text"])
    }

    @Test
    fun testNotificationAlertToMap() {
        val alert = NotificationAlert(
            id = "alert_123",
            userId = "user_456",
            title = "Ride Matched",
            message = "You've been matched with a driver!",
            type = "match",
            timestamp = 1690000000000L,
            isRead = false
        )

        val map = alert.toMap()
        assertEquals("alert_123", map["id"])
        assertEquals("user_456", map["userId"])
        assertEquals("Ride Matched", map["title"])
        assertEquals("match", map["type"])
        assertEquals(false, map["isRead"])
    }

    @Test
    fun testModelDefaults() {
        val user = User()
        assertEquals("", user.id)
        assertEquals("", user.name)
        assertEquals("guest", user.verifiedTier)

        val offer = TripOffer()
        assertEquals("active", offer.status)
        assertEquals(4, offer.totalSeats)
        assertEquals(0.0, offer.costPerRider)

        val request = RideRequest()
        assertEquals("active", request.status)
        assertEquals(1, request.seatsNeeded)
    }

    @Test
    fun testModelEquality() {
        val user1 = User(id = "user_123", name = "Alice")
        val user2 = User(id = "user_123", name = "Alice")
        val user3 = User(id = "user_456", name = "Bob")

        // Kotlin data classes provide equals() by default
        assertEquals(user1, user2)
        assertNotEquals(user1, user3)
    }
}
