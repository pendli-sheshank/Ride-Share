package com.splitcruiser.app.data

import com.splitcruiser.app.data.firebase.FirestoreCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The typed-value wire format is the one place in this backend where a mistake corrupts data
 * silently rather than failing loudly, so every model gets a round trip.
 */
class FirestoreCodecTest {

    @Test
    fun integersAreStringEncodedOnTheWire() {
        val fields = FirestoreCodec.encode(serializer<TripOffer>(), TripOffer(seatsLeft = 3))
        val seats = fields["seatsLeft"]!!.jsonObject
        assertTrue(seats.containsKey("integerValue"), "Int must use integerValue, got $seats")
        // Firestore rejects an unquoted integerValue.
        assertEquals("3", seats["integerValue"]!!.jsonPrimitive.content)
        assertTrue(seats["integerValue"]!!.jsonPrimitive.isString)
    }

    @Test
    fun longsAreStringEncodedToo() {
        val fields = FirestoreCodec.encode(serializer<TripOffer>(), TripOffer(departureTime = 1753900000000L))
        val departure = fields["departureTime"]!!.jsonObject
        assertTrue(departure.containsKey("integerValue"))
        assertEquals("1753900000000", departure["integerValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun floatsAndDoublesUseDoubleValueEvenWhenWhole() {
        val fields = FirestoreCodec.encode(
            serializer<TripOffer>(),
            TripOffer(hostRating = 5.0f, costPerRider = 12.0)
        )
        assertTrue(fields["hostRating"]!!.jsonObject.containsKey("doubleValue"))
        assertTrue(fields["costPerRider"]!!.jsonObject.containsKey("doubleValue"))
    }

    @Test
    fun booleansAreNotConfusedWithStrings() {
        val fields = FirestoreCodec.encode(serializer<TripOffer>(), TripOffer(womenOnly = true, status = "true"))
        assertTrue(fields["womenOnly"]!!.jsonObject.containsKey("booleanValue"))
        // A string that happens to read "true" must stay a string.
        assertTrue(fields["status"]!!.jsonObject.containsKey("stringValue"))
    }

    @Test
    fun emptyListsSurviveTheRoundTrip() {
        val original = TripOffer(passengers = emptyList(), routeSamplePoints = emptyList())
        val restored = FirestoreCodec.decode(
            serializer<TripOffer>(),
            FirestoreCodec.encode(serializer<TripOffer>(), original)
        )
        assertEquals(emptyList(), restored.passengers)
        assertEquals(emptyList(), restored.routeSamplePoints)
    }

    @Test
    fun populatedListsSurviveTheRoundTrip() {
        val original = TripOffer(
            passengers = listOf("user_a", "user_b"),
            passengerNames = listOf("Ana", "Bo"),
            routeSamplePoints = listOf("drt2z", "drt2y")
        )
        val restored = roundTrip(original)
        assertEquals(original.passengers, restored.passengers)
        assertEquals(original.passengerNames, restored.passengerNames)
        assertEquals(original.routeSamplePoints, restored.routeSamplePoints)
    }

    @Test
    fun tripOfferRoundTrips() {
        val original = TripOffer(
            id = "offer_1a2b3c4d",
            hostId = "user_host",
            hostName = "Ana",
            hostRating = 4.5f,
            origin = "Northeastern University",
            destination = "Logan Airport",
            originLat = 42.3398,
            originLng = -71.0892,
            destLat = 42.3656,
            destLng = -71.0096,
            originGeohash = "drt2ypp",
            destGeohash = "drt3b1h",
            departureTime = 1753900000000L,
            totalSeats = 4,
            seatsLeft = 2,
            vehicleInfo = "Blue Civic",
            costPerRider = 12.5,
            womenOnly = true,
            status = "active",
            routeSamplePoints = listOf("drt2ypp"),
            costEstimate = 50.0,
            passengers = listOf("user_r1"),
            passengerNames = listOf("Bo")
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun rideRequestRoundTrips() {
        val original = RideRequest(
            id = "request_9f8e7d6c",
            riderId = "user_rider",
            riderName = "Bo",
            riderRating = 3.75f,
            origin = "Back Bay",
            destination = "South Station",
            originLat = 42.3503,
            originLng = -71.0810,
            destLat = 42.3519,
            destLng = -71.0552,
            originGeohash = "drt2ypn",
            destGeohash = "drt2ypq",
            departureTime = 1753900000000L,
            seatsNeeded = 2,
            notes = "Two bags, and a \"quoted\" note",
            womenOnly = false,
            status = "active"
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun userRoundTrips() {
        val original = User(
            id = "user_abc123",
            phoneNumber = "+16175550100",
            email = "ana@northeastern.edu",
            name = "Ana",
            lastInitial = "K",
            avatarUrl = "https://example.invalid/a.jpg",
            verifiedTier = "vouched",
            invitedBy = "user_zzz",
            ratingAvg = 4.25f,
            ratingCount = 12,
            noShowCount = 1,
            communityId = "neu",
            homeArea = "Mission Hill",
            isWomenOnlyFilterEnabled = true,
            fcmToken = "token",
            emailNotificationsEnabled = true,
            pushNotificationsEnabled = false,
            collegeName = "Northeastern University",
            verifiedEmail = "ana@northeastern.edu"
        )
        val restored = roundTrip(original)
        assertEquals(original, restored)
        // `displayName` is a computed getter, so it must not leak into the document.
        val fields = FirestoreCodec.encode(serializer<User>(), original)
        assertTrue(!fields.containsKey("displayName"), "computed properties must not be persisted")
    }

    @Test
    fun tripMatchRoundTrips() {
        val original = TripMatch(
            id = "match_1",
            offerId = "offer_1",
            requestId = "request_1",
            hostId = "user_host",
            riderId = "user_rider",
            riderName = "Bo",
            riderRating = 4.0f,
            contribution = 12.5,
            status = "pending",
            timestamp = 1753900000000L
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun messageRoundTrips() {
        val original = Message(
            id = "msg_1",
            matchId = "match_1",
            senderId = "user_host",
            senderName = "Ana",
            text = "On my way — ETA 5 min",
            timestamp = 1753900000000L
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun systemMessageFlagRoundTrips() {
        val original = Message(
            id = "msg_sys_1",
            matchId = "match_1",
            senderId = "user_host",
            senderName = "Split Cruiser",
            text = "Trip request accepted by the host.",
            timestamp = 1753900000000L,
            isSystem = true,
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun ratingRoundTrips() {
        val original = Rating(
            id = "rating_1",
            fromUserId = "user_rider",
            toUserId = "user_host",
            rating = 4.5f,
            comment = "Great driver",
            timestamp = 1753900000000L
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun notificationAlertRoundTrips() {
        val original = NotificationAlert(
            id = "notif_1",
            userId = "user_host",
            title = "Seat reserved",
            message = "Bo reserved a seat on your ride",
            type = "match",
            timestamp = 1753900000000L,
            isRead = true
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun smallModelsRoundTrip() {
        // Doubles survive the codec's string/number handling, which is what the coordinates ride on.
        assertEquals(
            ContactDetails("+16175550100", "12 Mission Hill, Boston, MA", 42.3332, -71.1054),
            roundTrip(ContactDetails("+16175550100", "12 Mission Hill, Boston, MA", 42.3332, -71.1054))
        )
        assertEquals(
            Community("neu", "Northeastern University", "Boston, MA"),
            roundTrip(Community("neu", "Northeastern University", "Boston, MA"))
        )
        assertEquals(
            Vehicle("user_a", "Honda", "Civic", "2019", "Blue", "1ABC234"),
            roundTrip(Vehicle("user_a", "Honda", "Civic", "2019", "Blue", "1ABC234"))
        )
        assertEquals(
            Block("block_1", "user_a", "user_b"),
            roundTrip(Block("block_1", "user_a", "user_b"))
        )
        assertEquals(
            LocationPlace("place_1", "Snell Library", "Boston", 42.3387, -71.0881),
            roundTrip(LocationPlace("place_1", "Snell Library", "Boston", 42.3387, -71.0881))
        )
    }

    @Test
    fun missingFieldsFallBackToModelDefaults() {
        val sparse = Json.parseToJsonElement(
            """{ "id": { "stringValue": "offer_x" } }"""
        ).jsonObject
        val offer = FirestoreCodec.decode(serializer<TripOffer>(), sparse)
        assertEquals("offer_x", offer.id)
        assertEquals(4, offer.totalSeats)
        assertEquals("active", offer.status)
    }

    @Test
    fun unknownFieldsFromOlderDocumentsAreIgnored() {
        val withExtra = Json.parseToJsonElement(
            """{ "id": { "stringValue": "offer_x" }, "legacyField": { "stringValue": "gone" } }"""
        ).jsonObject
        assertEquals("offer_x", FirestoreCodec.decode(serializer<TripOffer>(), withExtra).id)
    }

    @Test
    fun aNullDocumentDecodesToDefaults() {
        assertEquals(TripOffer(), FirestoreCodec.decode(serializer<TripOffer>(), null))
    }

    @Test
    fun aWholeDoubleArrivingAsAnIntegerStillDecodes() {
        // The Android Firestore SDK wrote some numbers as integers; those documents must not
        // break a field the model declares as Float.
        val fields = Json.parseToJsonElement(
            """{ "hostRating": { "integerValue": "5" } }"""
        ).jsonObject
        assertEquals(5.0f, FirestoreCodec.decode(serializer<TripOffer>(), fields).hostRating)
    }

    private inline fun <reified T> roundTrip(value: T): T =
        FirestoreCodec.decode(serializer<T>(), FirestoreCodec.encode(serializer<T>(), value))
}
