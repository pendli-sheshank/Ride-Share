package com.splitcruiser.app.data

import com.splitcruiser.app.data.firebase.InMemoryStore
import com.splitcruiser.app.data.firebase.SplitCruiserException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the repository end to end against a scripted backend — the first tests in this project
 * to cover a repository at all, since until now there was never one that could run off-device.
 */
class SplitCruiserRepositoryTest {

    private val now = 1_000_000L
    private val future = now + 3_600_000L

    private val config = FirebaseConfig(
        apiKey = "test-key",
        projectId = "split-cruiser-test",
        storageBucket = "bucket.appspot.com",
    )

    private val requests = mutableListOf<HttpRequestData>()

    /** Firestore documents by "<collection>/<id>", as the scripted backend sees them. */
    private val documents = mutableMapOf<String, String>()

    @BeforeTest
    fun freezeTime() {
        currentTimeProvider = { now }
        var counter = 0
        randomProvider = { kotlin.random.Random(counter++) }
    }

    @AfterTest
    fun restoreTime() {
        currentTimeProvider = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() }
        randomProvider = { kotlin.random.Random.Default }
    }

    private fun repository(handler: (HttpRequestData) -> Pair<HttpStatusCode, String>): SplitCruiserRepository {
        val engine = MockEngine { request ->
            requests += request
            val (status, body) = handler(request)
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return SplitCruiserRepository(config, InMemoryStore(), engine)
    }

    /** A backend that accepts writes, serves whatever is in [documents], and finds nothing else. */
    private fun scriptedBackend(): (HttpRequestData) -> Pair<HttpStatusCode, String> = { request ->
        val url = request.url.toString()
        val stored = documents.entries.firstOrNull { (key, _) ->
            url.contains("/documents/$key") && request.method.value == "GET"
        }?.value
        when {
            url.contains("signInWithPassword") || url.contains("accounts:signUp") -> HttpStatusCode.OK to
                """{"localId":"me","email":"ana@neu.edu","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
            url.contains(":runQuery") -> HttpStatusCode.OK to """[{"readTime":"2026-07-28T00:00:00Z"}]"""
            stored != null -> HttpStatusCode.OK to stored
            request.method.value == "GET" -> HttpStatusCode.NotFound to "{}"
            else -> HttpStatusCode.OK to "{}"
        }
    }

    private suspend fun signedIn(repo: SplitCruiserRepository): SplitCruiserRepository {
        repo.logInWithEmail("ana@neu.edu", "hunter2")
        return repo
    }

    // --- Configuration ---------------------------------------------------------------------

    @Test
    fun anUnconfiguredBuildRefusesToPretendItWorks() = runTest {
        val repo = SplitCruiserRepository(
            FirebaseConfig("PLACEHOLDER_KEY", "PLACEHOLDER_PROJECT", ""),
            InMemoryStore(),
        )
        assertTrue(!repo.isFirebaseEnabled)
        // The old repository silently fell back to a local JSON store here; that hid the
        // misconfiguration and is exactly what this change removes.
        val failure = assertFailsWith<SplitCruiserException> {
            repo.logInWithEmail("ana@neu.edu", "hunter2")
        }
        assertEquals("NOT_CONFIGURED", failure.code)
    }

    @Test
    fun communitiesArePopulatedBeforeAnyNetworkCall() = runTest {
        val repo = repository(scriptedBackend())
        // The rules make `communities` read-only, so a client-side seed would be denied and the
        // profile-setup picker would be empty on a first run.
        assertTrue(repo.allCommunities.value.isNotEmpty())
        assertContains(repo.allCommunities.value.map { it.name }, "Northeastern University")
    }

    // --- Auth ------------------------------------------------------------------------------

    @Test
    fun loggingInLoadsTheProfileAndReportsWhetherItNeedsSetup() = runTest {
        documents["users/me"] =
            """{"fields":{"id":{"stringValue":"me"},"name":{"stringValue":"Ana"},"email":{"stringValue":"ana@neu.edu"}}}"""
        val repo = repository(scriptedBackend())

        val needsProfile = repo.logInWithEmail("ana@neu.edu", "hunter2")

        assertTrue(!needsProfile, "a profile with a name is complete")
        assertEquals("Ana", repo.currentUser.value?.name)
    }

    @Test
    fun aBrandNewAccountIsRoutedToProfileSetup() = runTest {
        val repo = repository(scriptedBackend()) // users/me is absent -> 404
        assertTrue(repo.logInWithEmail("ana@neu.edu", "hunter2"), "an empty name means setup is needed")
    }

    @Test
    fun googleSignInCreatesTheProfileAndKeepsTheAvatar() = runTest {
        val repo = repository { request ->
            if (request.url.toString().contains("signInWithIdp")) {
                HttpStatusCode.OK to """
                {"localId":"me","email":"ana@gmail.com","idToken":"tok","refreshToken":"ref",
                 "expiresIn":"3600","displayName":"Ana R","photoUrl":"https://lh3.example/ana.jpg"}
                """.trimIndent()
            } else {
                scriptedBackend()(request)
            }
        }

        val needsProfile = repo.signInWithGoogle("google-jwt")

        // Google knows the display name, but filling it in would make the profile screen — the only
        // place community and home area are collected — think it had already run.
        assertTrue(needsProfile, "a Google account still has to pick a community")
        assertEquals("https://lh3.example/ana.jpg", repo.currentUser.value?.avatarUrl)
        assertEquals("ana@gmail.com", repo.currentUser.value?.email)
    }

    @Test
    fun anEmptyGoogleTokenNeverReachesTheNetwork() = runTest {
        val repo = repository(scriptedBackend())
        assertFailsWith<SplitCruiserException> { repo.signInWithGoogle("") }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun emptyCredentialsAreRejectedBeforeTheNetwork() = runTest {
        val repo = repository(scriptedBackend())
        assertFailsWith<SplitCruiserException> { repo.logInWithEmail("", "") }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aShortPasswordIsRejectedBeforeTheNetwork() = runTest {
        val repo = repository(scriptedBackend())
        val failure = assertFailsWith<SplitCruiserException> { repo.signUpWithEmail("ana@neu.edu", "12345") }
        assertContains(failure.message!!, "at least 6 characters")
        assertTrue(requests.isEmpty())
    }

    @Test
    fun loggingOutClearsTheSessionAndTheCaches() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.logout()
        assertNull(repo.currentUser.value)
        assertTrue(repo.activeOffers.value.isEmpty())
        assertTrue(repo.notifications.value.isEmpty())
    }

    // --- Posting ---------------------------------------------------------------------------

    @Test
    fun postingARideFillsInIdentityGeohashAndSeats() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.postTripOffer(
            RideFactory.newTripOffer(
                origin = "Snell Library",
                destination = "Logan Airport",
                originLat = 42.3383,
                originLng = -71.0881,
                destLat = 42.3656,
                destLng = -71.0096,
                departureTime = future,
                totalSeats = 3,
                costPerRider = 12.0,
                womenOnly = false,
                vehicleInfo = "Blue Civic",
            )
        )
        val posted = repo.getHostedRides("me").single()
        assertEquals("me", posted.hostId)
        assertEquals(3, posted.seatsLeft)
        assertEquals(36.0, posted.costEstimate)
        assertEquals(7, posted.originGeohash.length)
        assertTrue(posted.id.startsWith("offer_"))
    }

    @Test
    fun aRideInThePastIsRejected() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        val failure = assertFailsWith<SplitCruiserException> {
            repo.postTripOffer(
                RideFactory.newTripOffer(
                    "A", "B", 1.0, 1.0, 2.0, 2.0,
                    departureTime = now - 1, totalSeats = 2, costPerRider = 5.0,
                    womenOnly = false, vehicleInfo = "",
                )
            )
        }
        assertContains(failure.message!!, "future")
    }

    @Test
    fun aRideWithoutCoordinatesIsRejected() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        assertFailsWith<SplitCruiserException> {
            repo.postTripOffer(
                RideFactory.newTripOffer(
                    "A", "B", 0.0, 0.0, 0.0, 0.0,
                    departureTime = future, totalSeats = 2, costPerRider = 5.0,
                    womenOnly = false, vehicleInfo = "",
                )
            )
        }
    }

    @Test
    fun postingRequiresBeingLoggedIn() = runTest {
        val repo = repository(scriptedBackend())
        val failure = assertFailsWith<SplitCruiserException> {
            repo.postRideRequest(
                RideFactory.newRideRequest("A", "B", 1.0, 1.0, 2.0, 2.0, future, 1, "", false)
            )
        }
        assertEquals("UNAUTHENTICATED", failure.code)
    }

    @Test
    fun postingARideRequestFillsInTheRiderAndGeohashes() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.postRideRequest(
            RideFactory.newRideRequest("Back Bay", "South Station", 42.3503, -71.081, 42.3519, -71.0552, future, 2, "Two bags", false)
        )
        val posted = repo.getPassengerRequests("me").single()
        assertEquals("me", posted.riderId)
        assertEquals(2, posted.seatsNeeded)
        assertEquals("active", posted.status)
        assertEquals(7, posted.destGeohash.length)
    }

    // --- Joining ---------------------------------------------------------------------------

    @Test
    fun joiningARideTakesASeatAndNamesOnlyTheAllowedFields() = runTest {
        documents["users/me"] =
            """{"fields":{"id":{"stringValue":"me"},"name":{"stringValue":"Ana"}}}"""
        val repo = repository { request ->
            val url = request.url.toString()
            when {
                url.contains("signInWithPassword") -> HttpStatusCode.OK to
                    """{"localId":"me","email":"a@b.c","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
                url.contains(":runQuery") -> HttpStatusCode.OK to """[{"readTime":"x"}]"""
                url.contains("/documents/users/me") && request.method.value == "GET" ->
                    HttpStatusCode.OK to documents["users/me"]!!
                url.contains("/documents/trip_offers/offer_1") && request.method.value == "GET" ->
                    HttpStatusCode.OK to """
                    {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
                     "seatsLeft":{"integerValue":"2"},"totalSeats":{"integerValue":"4"},
                     "status":{"stringValue":"active"},"origin":{"stringValue":"A"},
                     "destination":{"stringValue":"B"}}}
                    """.trimIndent()
                else -> HttpStatusCode.OK to "{}"
            }
        }
        signedIn(repo)
        repo.joinTripOfferDirect("offer_1")

        val offerWrite = requests.last { it.url.toString().contains("/trip_offers/offer_1") && it.method.value == "PATCH" }
        val mask = offerWrite.url.toString()
        // A non-host may only touch these four fields; a wider mask is denied by the rules.
        assertContains(mask, "updateMask.fieldPaths=passengers")
        assertContains(mask, "updateMask.fieldPaths=seatsLeft")
        assertContains(mask, "updateMask.fieldPaths=status")
        assertTrue(!mask.contains("fieldPaths=hostId"), "must not claim fields the rules forbid: $mask")
        assertEquals(1, repo.getTripOfferById("offer_1")?.seatsLeft)
    }

    @Test
    fun theLastSeatMarksTheRideFull() = runTest {
        val repo = repository { request ->
            val url = request.url.toString()
            when {
                url.contains("signInWithPassword") -> HttpStatusCode.OK to
                    """{"localId":"me","email":"a@b.c","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
                url.contains(":runQuery") -> HttpStatusCode.OK to """[{"readTime":"x"}]"""
                url.contains("/documents/trip_offers/offer_1") && request.method.value == "GET" ->
                    HttpStatusCode.OK to """
                    {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
                     "seatsLeft":{"integerValue":"1"},"totalSeats":{"integerValue":"4"},
                     "status":{"stringValue":"active"}}}
                    """.trimIndent()
                else -> HttpStatusCode.OK to "{}"
            }
        }
        signedIn(repo)
        repo.joinTripOfferDirect("offer_1")
        assertEquals("full", repo.getTripOfferById("offer_1")?.status)
    }

    @Test
    fun youCannotJoinYourOwnRide() = runTest {
        val repo = repository { request ->
            val url = request.url.toString()
            when {
                url.contains("signInWithPassword") -> HttpStatusCode.OK to
                    """{"localId":"me","email":"a@b.c","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
                url.contains(":runQuery") -> HttpStatusCode.OK to """[{"readTime":"x"}]"""
                url.contains("/documents/trip_offers/offer_1") && request.method.value == "GET" ->
                    HttpStatusCode.OK to """
                    {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"me"},
                     "seatsLeft":{"integerValue":"2"},"status":{"stringValue":"active"}}}
                    """.trimIndent()
                else -> HttpStatusCode.OK to "{}"
            }
        }
        signedIn(repo)
        val failure = assertFailsWith<SplitCruiserException> { repo.joinTripOfferDirect("offer_1") }
        assertContains(failure.message!!, "your own ride")
    }

    @Test
    fun aFullRideCannotBeJoined() = runTest {
        val repo = repository { request ->
            val url = request.url.toString()
            when {
                url.contains("signInWithPassword") -> HttpStatusCode.OK to
                    """{"localId":"me","email":"a@b.c","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
                url.contains(":runQuery") -> HttpStatusCode.OK to """[{"readTime":"x"}]"""
                url.contains("/documents/trip_offers/offer_1") && request.method.value == "GET" ->
                    HttpStatusCode.OK to """
                    {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
                     "seatsLeft":{"integerValue":"0"},"status":{"stringValue":"full"}}}
                    """.trimIndent()
                else -> HttpStatusCode.OK to "{}"
            }
        }
        signedIn(repo)
        assertFailsWith<SplitCruiserException> { repo.joinTripOfferDirect("offer_1") }
    }

    // --- Matching --------------------------------------------------------------------------

    @Test
    fun aContributionAboveTwiceTheFareIsRejected() = runTest {
        val repo = repository { request ->
            val url = request.url.toString()
            when {
                url.contains("signInWithPassword") -> HttpStatusCode.OK to
                    """{"localId":"me","email":"a@b.c","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
                url.contains(":runQuery") -> HttpStatusCode.OK to """[{"readTime":"x"}]"""
                url.contains("/documents/trip_offers/offer_1") && request.method.value == "GET" ->
                    HttpStatusCode.OK to """
                    {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
                     "seatsLeft":{"integerValue":"3"},"costPerRider":{"doubleValue":10.0},
                     "status":{"stringValue":"active"}}}
                    """.trimIndent()
                url.contains("/documents/ride_requests/") && request.method.value == "GET" ->
                    HttpStatusCode.NotFound to "{}"
                else -> HttpStatusCode.OK to "{}"
            }
        }
        signedIn(repo)
        val failure = assertFailsWith<SplitCruiserException> {
            repo.requestSeatOnOffer("offer_1", contribution = 25.0)
        }
        assertContains(failure.message!!, "cost cap")
    }

    @Test
    fun aMatchCarriesItsParticipantsForTheSecurityRules() = runTest {
        val repo = repository { request ->
            val url = request.url.toString()
            when {
                url.contains("signInWithPassword") -> HttpStatusCode.OK to
                    """{"localId":"me","email":"a@b.c","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
                url.contains(":runQuery") -> HttpStatusCode.OK to """[{"readTime":"x"}]"""
                url.contains("/documents/trip_offers/offer_1") && request.method.value == "GET" ->
                    HttpStatusCode.OK to """
                    {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
                     "seatsLeft":{"integerValue":"3"},"costPerRider":{"doubleValue":10.0},
                     "status":{"stringValue":"active"}}}
                    """.trimIndent()
                url.contains("/documents/ride_requests/") && request.method.value == "GET" ->
                    HttpStatusCode.NotFound to "{}"
                else -> HttpStatusCode.OK to "{}"
            }
        }
        signedIn(repo)
        val match = repo.requestSeatOnOffer("offer_1", contribution = 10.0)

        // Rules cannot follow a reference cheaply, so participation must be on the document.
        assertEquals(listOf("bo", "me"), match.participants)
        assertEquals("pending", match.status)
    }

    /**
     * The screens used to invent this id from the clock and let the repository create a ride
     * request document under it, which put demand nobody had expressed into the host feed.
     */
    @Test
    fun aMatchAgainstAMissingRequestIsRefusedRatherThanInvented() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"3"},"costPerRider":{"doubleValue":10.0},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        val failure = assertFailsWith<SplitCruiserException> {
            repo.validateAndCreateMatch("offer_1", "req_joined_123456", contribution = 10.0)
        }
        assertContains(failure.message!!, "no longer exists")
        assertTrue(
            requests.none { it.url.toString().contains("/ride_requests/req_joined_123456") && it.method.value == "PATCH" },
            "a request document must not be conjured out of a made-up id",
        )
    }

    @Test
    fun theRequestBackingASeatRequestIsNotAdvertisedToHosts() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"3"},"costPerRider":{"doubleValue":10.0},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        repo.requestSeatOnOffer("offer_1", contribution = 10.0)

        val created = repo.myRideRequests.value.single()
        // "active" would put it in every host's feed as a rider still looking for a ride.
        assertEquals("pending", created.status)
        assertEquals("me", created.riderId)

        // And a second tap must not open a second match on the same ride.
        val failure = assertFailsWith<SplitCruiserException> {
            repo.requestSeatOnOffer("offer_1", contribution = 10.0)
        }
        assertContains(failure.message!!, "already have a request")
    }

    @Test
    fun aHostOfferingASeatAcceptsTheRiderOutright() = runTest {
        documents["trip_offers/offer_mine"] = """
            {"fields":{"id":{"stringValue":"offer_mine"},"hostId":{"stringValue":"me"},
             "seatsLeft":{"integerValue":"3"},"totalSeats":{"integerValue":"3"},
             "costPerRider":{"doubleValue":10.0},"status":{"stringValue":"active"}}}
        """.trimIndent()
        documents["ride_requests/req_1"] = """
            {"fields":{"id":{"stringValue":"req_1"},"riderId":{"stringValue":"zo"},
             "riderName":{"stringValue":"Zo"},"seatsNeeded":{"integerValue":"1"},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        val match = repo.offerSeatForRequest("req_1", "offer_mine", contribution = 10.0)

        assertEquals("accepted", match.status)
        assertEquals(2, repo.getTripOfferById("offer_mine")?.seatsLeft)
    }

    @Test
    fun aSeatCannotBeOfferedOnSomeoneElsesRide() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"3"},"costPerRider":{"doubleValue":10.0},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        val failure = assertFailsWith<SplitCruiserException> {
            repo.offerSeatForRequest("req_1", "offer_1", contribution = 10.0)
        }
        assertContains(failure.message!!, "you are hosting")
    }

    // --- Cost split ------------------------------------------------------------------------

    @Test
    fun costSplitsEvenlyAndSurvivesZeroRiders() {
        val repo = repository(scriptedBackend())
        assertEquals(25.0, repo.calculateCostSplit(100.0, 4))
        assertEquals(100.0, repo.calculateCostSplit(100.0, 0))
    }

    @Test
    fun matchingOffersUseTheLooseOriginComparison() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.postTripOffer(
            RideFactory.newTripOffer(
                origin = "Snell Library Boston",
                destination = "Logan Airport",
                originLat = 42.3383, originLng = -71.0881,
                destLat = 42.3656, destLng = -71.0096,
                departureTime = future, totalSeats = 3, costPerRider = 12.0,
                womenOnly = false, vehicleInfo = "",
            )
        )
        val matches = repo.findMatchingOffers(
            RideFactory.newRideRequest("Snell Library", "Logan Airport", 1.0, 1.0, 2.0, 2.0, future, 1, "", false)
        )
        assertEquals(1, matches.size)
    }
}
