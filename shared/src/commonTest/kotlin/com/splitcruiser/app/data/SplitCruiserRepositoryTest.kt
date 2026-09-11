package com.splitcruiser.app.data

import com.splitcruiser.app.data.firebase.InMemoryStore
import com.splitcruiser.app.data.firebase.SplitCruiserException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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

    /**
     * Per-document version counters, standing in for Firestore's `updateTime`.
     *
     * Real Firestore returns an `updateTime` on every read and honours a `currentDocument.updateTime`
     * precondition on a commit. The fake models both, because the seat-booking path now depends on
     * them: without a version the conditional write cannot be exercised at all, and a fake that
     * accepts every commit unconditionally would report the overbooking race as fixed while proving
     * nothing.
     */
    private val documentVersions = mutableMapOf<String, Int>()

    /**
     * Fired just after a document GET is served, so a test can simulate another client writing in
     * the window between our read and our commit — which is the whole point of the precondition.
     * Sequential test code cannot otherwise produce that interleaving.
     */
    private var onDocumentRead: ((String) -> Unit)? = null

    /** Writes to [documents] the way a competing client would, bumping the version. */
    private fun competingWrite(key: String, fields: Map<String, String>) {
        val existing = documents[key]
            ?.let { Json.parseToJsonElement(it).jsonObject["fields"]?.jsonObject }
            ?: JsonObject(emptyMap())
        val merged = buildJsonObject {
            existing.forEach { (k, v) -> put(k, v) }
            fields.forEach { (k, v) -> put(k, Json.parseToJsonElement(v)) }
        }
        documents[key] = buildJsonObject { put("fields", merged) }.toString()
        documentVersions[key] = (documentVersions[key] ?: 1) + 1
    }

    private fun versionTokenFor(key: String): String {
        val version = documentVersions.getOrPut(key) { 1 }
        return "2026-07-28T00:00:00.${version.toString().padStart(6, '0')}Z"
    }

    /** Serves a stored document the way Firestore does: fields plus the current `updateTime`. */
    private fun documentWithVersion(key: String, stored: String): String {
        val parsed = Json.parseToJsonElement(stored).jsonObject
        val rebuilt = buildJsonObject {
            parsed.forEach { (k, v) -> put(k, v) }
            put("name", JsonPrimitive("projects/split-cruiser-test/databases/splitcruiser/documents/$key"))
            put("updateTime", JsonPrimitive(versionTokenFor(key)))
        }
        return rebuilt.toString()
    }

    /**
     * Applies a PATCH write to [documents], so a document the app just wrote is visible to the next
     * read — as it is in Firestore.
     *
     * The fake used to accept writes and discard them, which was survivable while every read that
     * mattered was served from the repository's own cache. The seat-booking path now reads the
     * offer back from the server before claiming a seat, so a write-only fake would 404 on a ride
     * the test had just posted.
     */
    private fun applyPatch(url: String, body: String) {
        val key = url.substringAfter("/documents/").substringBefore('?')
        val fields = Json.parseToJsonElement(body).jsonObject["fields"]?.jsonObject ?: return
        val existing = documents[key]
            ?.let { Json.parseToJsonElement(it).jsonObject["fields"]?.jsonObject }
            ?: JsonObject(emptyMap())
        val merged = buildJsonObject {
            existing.forEach { (k, v) -> put(k, v) }
            fields.forEach { (k, v) -> put(k, v) }
        }
        documents[key] = buildJsonObject { put("fields", merged) }.toString()
        documentVersions[key] = (documentVersions[key] ?: 1) + 1
    }

    /**
     * Applies a `documents:commit` body to [documents], honouring `currentDocument.updateTime`.
     *
     * Returns null when every precondition held (and the writes were applied), or a 412 the way
     * Firestore reports a lost race.
     */
    private fun applyCommit(body: String): Pair<HttpStatusCode, String>? {
        val writes = Json.parseToJsonElement(body).jsonObject["writes"]?.jsonArray ?: return null

        // Firestore applies a commit atomically, so check every precondition before writing any of it.
        val planned = writes.mapNotNull { write ->
            val update = write.jsonObject["update"]?.jsonObject ?: return@mapNotNull null
            val key = update["name"]!!.jsonPrimitive.content.substringAfter("/documents/")
            val expected = write.jsonObject["currentDocument"]?.jsonObject?.get("updateTime")?.jsonPrimitive?.content
            if (expected != null && expected != versionTokenFor(key)) return HttpStatusCode.PreconditionFailed to
                """{"error":{"code":400,"status":"FAILED_PRECONDITION","message":"document has been modified"}}"""
            key to update["fields"]!!.jsonObject
        }

        planned.forEach { (key, fields) ->
            val existing = documents[key]
                ?.let { Json.parseToJsonElement(it).jsonObject["fields"]?.jsonObject }
                ?: JsonObject(emptyMap())
            val merged = buildJsonObject {
                existing.forEach { (k, v) -> put(k, v) }
                fields.forEach { (k, v) -> put(k, v) }
            }
            documents[key] = buildJsonObject { put("fields", merged) }.toString()
            documentVersions[key] = (documentVersions[key] ?: 1) + 1
        }
        return null
    }

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
        val storedKey = documents.keys.firstOrNull { key ->
            url.contains("/documents/$key") && request.method.value == "GET"
        }
        when {
            url.contains("signInWithPassword") || url.contains("accounts:signUp") -> HttpStatusCode.OK to
                """{"localId":"me","email":"ana@neu.edu","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
            url.contains(":runQuery") -> HttpStatusCode.OK to """[{"readTime":"2026-07-28T00:00:00Z"}]"""
            url.contains("documents:commit") ->
                applyCommit((request.body as TextContent).text) ?: (HttpStatusCode.OK to """{"writeResults":[{}]}""")
            storedKey != null -> {
                val served = HttpStatusCode.OK to documentWithVersion(storedKey, documents.getValue(storedKey))
                onDocumentRead?.invoke(storedKey)
                served
            }
            request.method.value == "GET" -> HttpStatusCode.NotFound to "{}"
            request.method.value == "PATCH" -> {
                applyPatch(url, (request.body as TextContent).text)
                HttpStatusCode.OK to "{}"
            }
            else -> HttpStatusCode.OK to "{}"
        }
    }

    private suspend fun signedIn(repo: SplitCruiserRepository): SplitCruiserRepository {
        repo.logInWithEmail("ana@neu.edu", "hunter2")
        return repo
    }

    /** A second signed-in client over the same [documents], for contention tests. */
    private suspend fun secondClientAs(uid: String): SplitCruiserRepository {
        val repo = repository { request ->
            val url = request.url.toString()
            if (url.contains("signInWithPassword")) {
                HttpStatusCode.OK to
                    """{"localId":"$uid","email":"$uid@x.test","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
            } else {
                scriptedBackend()(request)
            }
        }
        repo.logInWithEmail("$uid@x.test", "hunter2")
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
        // place a name and home area are collected — think it had already run.
        assertTrue(needsProfile, "a Google account still has to complete onboarding")
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
    fun onboardingKeepsTheHomeAddressOutOfTheReadableDocument() = runTest {
        val repo = signedIn(repository(scriptedBackend()))

        repo.createUserProfile(
            name = "Ana",
            lastInitial = "R",
            homeArea = "Mission Hill",
            contact = ContactDetails("+16175550100", "12 Tremont St, Boston, MA", 42.3332, -71.1054),
            vehicle = null,
        )

        val writes = requests.filter { it.method.value == "PATCH" }.map { it.url.toString() }
        // `users` is world-readable, so the address goes to the owner-only subcollection instead.
        assertTrue(
            writes.any { it.contains("/users/me/private/profile") },
            "the private details must be written: $writes",
        )
        assertEquals("12 Tremont St, Boston, MA", repo.contactDetails.value?.homeAddress)
        assertTrue(repo.contactDetails.value?.hasHomeLocation == true)
        // The phone number is the deliberate exception — the trip detail screen shows a host's.
        assertEquals("+16175550100", repo.currentUser.value?.phoneNumber)
    }

    @Test
    fun signingOutForgetsTheHomeAddress() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.saveContactDetails(ContactDetails("+16175550100", "12 Tremont St", 42.33, -71.10))
        repo.logout()
        assertNull(repo.contactDetails.value)
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
            RideFactory.makeTripOffer(
                origin = "Snell Library",
                destination = "Logan Airport",
                originLat = 42.3383,
                originLng = -71.0881,
                destLat = 42.3656,
                destLng = -71.0096,
                departureTime = future,
                totalSeats = 3,
                totalCost = 48.0,
                womenOnly = false,
                vehicleInfo = "Blue Civic",
                exitLocation = "",
            )
        )
        val posted = repo.getHostedRides("me").single()
        assertEquals("me", posted.hostId)
        assertEquals(3, posted.seatsLeft)
        // $48 across three riders *and the driver* is $12 each — the host is travelling too, so the
        // cost divides `totalSeats + 1` ways. The caller never supplied a per-rider figure; this is
        // the first time anything in the product actually divided a cost.
        assertEquals(48.0, posted.totalCost)
        assertEquals(12.0, posted.costPerRider)
        // What the three passengers cover between them, the driver's own share excluded.
        assertEquals(36.0, posted.costEstimate)
        assertEquals(7, posted.originGeohash.length)
        assertTrue(posted.id.startsWith("offer_"))
    }

    @Test
    fun aRideInThePastIsRejected() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        val failure = assertFailsWith<SplitCruiserException> {
            repo.postTripOffer(
                RideFactory.makeTripOffer(
                    "A", "B", 1.0, 1.0, 2.0, 2.0,
                    departureTime = now - 1, totalSeats = 2, totalCost = 15.0,
                    womenOnly = false, vehicleInfo = "", exitLocation = "",
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
                RideFactory.makeTripOffer(
                    "A", "B", 0.0, 0.0, 0.0, 0.0,
                    departureTime = future, totalSeats = 2, totalCost = 15.0,
                    womenOnly = false, vehicleInfo = "", exitLocation = "",
                )
            )
        }
    }

    @Test
    fun postingRequiresBeingLoggedIn() = runTest {
        val repo = repository(scriptedBackend())
        val failure = assertFailsWith<SplitCruiserException> {
            repo.postRideRequest(
                RideFactory.makeRideRequest("A", "B", 1.0, 1.0, 2.0, 2.0, future, 1, "", false, "")
            )
        }
        assertEquals("UNAUTHENTICATED", failure.code)
    }

    @Test
    fun postingARideRequestFillsInTheRiderAndGeohashes() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.postRideRequest(
            RideFactory.makeRideRequest("Back Bay", "South Station", 42.3503, -71.081, 42.3519, -71.0552, future, 2, "Two bags", false, "")
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
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"2"},"totalSeats":{"integerValue":"4"},
             "status":{"stringValue":"active"},"origin":{"stringValue":"A"},
             "destination":{"stringValue":"B"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        repo.joinTripOfferDirect("offer_1")

        // The seat write is a conditional commit now, not a bare PATCH — see claimSeat.
        val commit = requests.last { it.url.toString().contains("documents:commit") }
        val body = (commit.body as TextContent).text
        // A non-host may only touch these four fields; a wider mask is denied by the rules.
        assertContains(body, "\"passengers\"")
        assertContains(body, "\"seatsLeft\"")
        assertContains(body, "\"status\"")
        assertTrue(!body.contains("\"hostId\""), "must not claim fields the rules forbid: $body")
        assertContains(body, "currentDocument", message = "the write must be conditional")
        assertEquals(1, repo.getTripOfferById("offer_1")?.seatsLeft)
    }

    // --- Ride validation bounds --------------------------------------------------------------
    //
    // The two forms that create money and capacity applied none of this. A bare `if (isNotEmpty())`
    // with no else swallowed the tap, `costPerRider.toDoubleOrNull() ?: 10.0` posted a $10 ride for
    // the input "abc", `totalSeats.toIntOrNull() ?: 4` accepted "0", and the date picker offered
    // past dates. The forms now surface these; the bounds live here so both platforms share them.

    @Test
    fun anOfferCannotAskForMoreThanTheContributionCap() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        val failure = assertFailsWith<SplitCruiserException> {
            repo.postTripOffer(anOffer(costPerRider = 999_999.0))
        }
        assertContains(failure.message.orEmpty(), "cannot exceed")
    }

    @Test
    fun anOfferCannotHaveZeroSeats() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        // seatsLeft = 0 at creation is a ride nobody can ever join.
        assertFailsWith<SplitCruiserException> { repo.postTripOffer(anOffer(totalSeats = 0)) }
        assertFailsWith<SplitCruiserException> { repo.postTripOffer(anOffer(totalSeats = -1)) }
    }

    @Test
    fun anOfferCannotHaveANegativePrice() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        assertFailsWith<SplitCruiserException> { repo.postTripOffer(anOffer(costPerRider = -5.0)) }
    }

    @Test
    fun anOfferCannotDepartInThePast() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        val failure = assertFailsWith<SplitCruiserException> {
            repo.postTripOffer(anOffer(departureTime = now - 3_600_000L))
        }
        assertContains(failure.message.orEmpty(), "future")
    }

    @Test
    fun unboundedFreeTextIsRefused() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        // Firestore charges by document size and the feed renders every one of these strings.
        assertFailsWith<SplitCruiserException> { repo.postTripOffer(anOffer(origin = "x".repeat(201))) }
        assertFailsWith<SplitCruiserException> {
            repo.postTripOffer(anOffer(exitLocation = "x".repeat(501)))
        }
    }

    @Test
    fun aRequestCannotAskForZeroOrTooManySeats() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        assertFailsWith<SplitCruiserException> { repo.postRideRequest(aRequest(seatsNeeded = 0)) }
        assertFailsWith<SplitCruiserException> { repo.postRideRequest(aRequest(seatsNeeded = 9)) }
    }

    @Test
    fun aRequestCannotCarryAnUnboundedNote() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        assertFailsWith<SplitCruiserException> { repo.postRideRequest(aRequest(notes = "x".repeat(501))) }
    }

    @Test
    fun aValidOfferAndRequestStillPost() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        // The bounds must not have made an ordinary ride unpostable.
        repo.postTripOffer(anOffer())
        repo.postRideRequest(aRequest())
        assertTrue(requests.any { it.url.toString().contains("/documents/trip_offers/") })
        assertTrue(requests.any { it.url.toString().contains("/documents/ride_requests/") })
    }

    private fun anOffer(
        origin: String = "Back Bay",
        destination: String = "Providence",
        costPerRider: Double = 12.0,
        totalCost: Double = 0.0,
        totalSeats: Int = 3,
        departureTime: Long = future,
        exitLocation: String = "",
    ) = TripOffer(
        origin = origin, destination = destination,
        originLat = 42.3, originLng = -71.1, destLat = 41.8, destLng = -71.4,
        costPerRider = costPerRider, totalCost = totalCost,
        totalSeats = totalSeats, seatsLeft = totalSeats,
        departureTime = departureTime, exitLocation = exitLocation,
    )

    private fun aRequest(
        seatsNeeded: Int = 1,
        notes: String = "",
    ) = RideRequest(
        origin = "Back Bay", destination = "Providence",
        originLat = 42.3, originLng = -71.1, destLat = 41.8, destLng = -71.4,
        seatsNeeded = seatsNeeded, departureTime = future, notes = notes,
    )

    // --- Session lifecycle -------------------------------------------------------------------

    /**
     * logout() calls stop(), which cancels and clears every poll job, and no login path called
     * start() again -- its only callers are the two ViewModels' `init`, once per process. Signing
     * out and back in without killing the app left feeds, matches, notifications and chat
     * permanently dead. The user sees a frozen app with no error anywhere.
     */
    @Test
    fun signingBackInAfterALogoutResumesPolling() = runTest {
        documents["users/me"] =
            """{"fields":{"id":{"stringValue":"me"},"name":{"stringValue":"Ana"}}}"""
        val repo = repository(scriptedBackend())
        repo.start()
        repo.logInWithEmail("ana@neu.edu", "hunter2")

        repo.logout()
        assertEquals(null, repo.currentUser.value, "logout clears the session")

        val requestsBeforeSecondLogin = requests.size
        repo.logInWithEmail("ana@neu.edu", "hunter2")

        // Let the resumed loops tick at least once.
        advanceTimeBy(25_000)

        assertTrue(
            requests.size > requestsBeforeSecondLogin + 2,
            "the refresh loops must be running again after signing back in",
        )
        repo.stop()
    }

    @Test
    fun startIsIdempotentSoADoubleCallDoesNotDoubleThePollRate() = runTest {
        val repo = repository(scriptedBackend())
        repo.start()
        repo.start()
        advanceTimeBy(25_000)
        val afterTwoStarts = requests.size

        repo.stop()
        advanceTimeBy(25_000)
        assertEquals(afterTwoStarts, requests.size, "stop() must actually stop every loop")
    }

    // --- Seat-booking concurrency ----------------------------------------------------------
    //
    // The overbooking race. joinTripOfferDirect read the offer through a cache-first fetch and
    // PATCHed an absolute seatsLeft computed from it, so two riders taking the last seat inside the
    // 20s poll window both read seatsLeft=1, both passed the check, and both wrote 0. The rules
    // could not catch it: the non-host branch only bounds seatsLeft to 0..totalSeats, which both
    // writes satisfy. The seat write is now a commit with a currentDocument.updateTime precondition.
    //
    // These tests interleave a competing write between our read and our commit, which is the only
    // way to reach the losing branch from sequential test code.

    @Test
    fun losingTheRaceForTheLastSeatIsReportedAndDoesNotOverbook() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"1"},"totalSeats":{"integerValue":"4"},
             "passengers":{"arrayValue":{"values":[]}},
             "passengerNames":{"arrayValue":{"values":[]}},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        // Another rider takes the last seat immediately after we read, before we commit.
        var interleaved = false
        onDocumentRead = { key ->
            if (key == "trip_offers/offer_1" && !interleaved) {
                interleaved = true
                competingWrite("trip_offers/offer_1", mapOf(
                    "seatsLeft" to """{"integerValue":"0"}""",
                    "status" to """{"stringValue":"full"}""",
                    "passengers" to """{"arrayValue":{"values":[{"stringValue":"someone_else"}]}}""",
                    "passengerNames" to """{"arrayValue":{"values":[{"stringValue":"Kai"}]}}""",
                ))
            }
        }

        val failure = assertFailsWith<SplitCruiserException> { repo.joinTripOfferDirect("offer_1") }
        assertContains(failure.message.orEmpty(), "no seats left")

        // The decisive assertion: the other rider is still the only passenger. Before the fix both
        // riders ended up on the manifest with seatsLeft written to 0 by whichever wrote last.
        val stored = Json.parseToJsonElement(documents.getValue("trip_offers/offer_1")).jsonObject
        val fields = stored["fields"]!!.jsonObject
        val passengers = fields["passengers"]!!.jsonObject["arrayValue"]!!.jsonObject["values"]!!.jsonArray
        assertEquals(1, passengers.size, "the ride must not be overbooked")
        assertEquals("someone_else", passengers[0].jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals("0", fields["seatsLeft"]!!.jsonObject["integerValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun losingOneRaceButFindingASeatOnTheRetrySucceedsWithTheRightCount() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"2"},"totalSeats":{"integerValue":"4"},
             "passengers":{"arrayValue":{"values":[]}},
             "passengerNames":{"arrayValue":{"values":[]}},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        var interleaved = false
        onDocumentRead = { key ->
            if (key == "trip_offers/offer_1" && !interleaved) {
                interleaved = true
                competingWrite("trip_offers/offer_1", mapOf(
                    "seatsLeft" to """{"integerValue":"1"}""",
                    "passengers" to """{"arrayValue":{"values":[{"stringValue":"someone_else"}]}}""",
                    "passengerNames" to """{"arrayValue":{"values":[{"stringValue":"Kai"}]}}""",
                ))
            }
        }

        repo.joinTripOfferDirect("offer_1")

        val fields = Json.parseToJsonElement(documents.getValue("trip_offers/offer_1"))
            .jsonObject["fields"]!!.jsonObject
        val passengers = fields["passengers"]!!.jsonObject["arrayValue"]!!.jsonObject["values"]!!.jsonArray
        // The retry re-read the *competitor's* state and appended to it, rather than overwriting it
        // with a manifest computed from the stale copy.
        assertEquals(2, passengers.size)
        assertEquals("someone_else", passengers[0].jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals("me", passengers[1].jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals("0", fields["seatsLeft"]!!.jsonObject["integerValue"]!!.jsonPrimitive.content)
        assertEquals("full", fields["status"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun reservingASeatTwiceDoesNotSeatTheRiderTwice() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"3"},"totalSeats":{"integerValue":"4"},
             "passengers":{"arrayValue":{"values":[{"stringValue":"me"}]}},
             "passengerNames":{"arrayValue":{"values":[{"stringValue":"Ana"}]}},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        // `passengers + riderId` had no dedup guard at all, so a retry or a double tap could seat
        // the same rider twice and consume two seats.
        assertFailsWith<SplitCruiserException> { repo.joinTripOfferDirect("offer_1") }
    }

    @Test
    fun decliningReturnsTheSeatAndDropsTheRightNameWhenTwoPassengersShareOne() = runTest {
        // List.minus removes only the FIRST match, so `passengerNames - "Alex"` dropped the other
        // Alex's name and left the two parallel arrays out of step. The UI zips them, which then
        // pairs a name with the wrong id. Names are dropped by index now.
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"me"},
             "seatsLeft":{"integerValue":"0"},"totalSeats":{"integerValue":"3"},
             "passengers":{"arrayValue":{"values":[{"stringValue":"alex_one"},{"stringValue":"alex_two"},{"stringValue":"kai"}]}},
             "passengerNames":{"arrayValue":{"values":[{"stringValue":"Alex"},{"stringValue":"Alex"},{"stringValue":"Kai"}]}},
             "status":{"stringValue":"full"}}}
        """.trimIndent()
        documents["ride_requests/req_1"] = """
            {"fields":{"id":{"stringValue":"req_1"},"riderId":{"stringValue":"alex_two"},
             "seatsNeeded":{"integerValue":"1"},"status":{"stringValue":"matched"}}}
        """.trimIndent()
        documents["trip_matches/match_1"] = """
            {"fields":{"id":{"stringValue":"match_1"},"hostId":{"stringValue":"me"},
             "riderId":{"stringValue":"alex_two"},"riderName":{"stringValue":"Alex"},
             "offerId":{"stringValue":"offer_1"},"requestId":{"stringValue":"req_1"},
             "status":{"stringValue":"accepted"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))
        repo.refreshNow()

        repo.declineMatch("match_1")

        val fields = Json.parseToJsonElement(documents.getValue("trip_offers/offer_1"))
            .jsonObject["fields"]!!.jsonObject
        val ids = fields["passengers"]!!.jsonObject["arrayValue"]!!.jsonObject["values"]!!.jsonArray
            .map { it.jsonObject["stringValue"]!!.jsonPrimitive.content }
        val names = fields["passengerNames"]!!.jsonObject["arrayValue"]!!.jsonObject["values"]!!.jsonArray
            .map { it.jsonObject["stringValue"]!!.jsonPrimitive.content }
        assertEquals(listOf("alex_one", "kai"), ids)
        assertEquals(listOf("Alex", "Kai"), names, "the remaining Alex must keep their name")
        assertEquals("1", fields["seatsLeft"]!!.jsonObject["integerValue"]!!.jsonPrimitive.content)
        assertEquals("active", fields["status"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun theLastSeatMarksTheRideFull() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"1"},"totalSeats":{"integerValue":"4"},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

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

    // --- Ratings -----------------------------------------------------------------------------

    /**
     * Ratings used to be write-only: they were read back only to compute an average, then
     * discarded, so nothing could tell whether you had already rated someone and the rating form
     * kept offering the same person forever.
     */
    @Test
    fun submittingARatingRecordsWhoYouRated() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        assertTrue(repo.getRatedUserIds().isEmpty())

        repo.submitRating(toUserId = "bo", ratingValue = 5f, comment = "Easy to find")

        assertEquals(listOf("bo"), repo.getRatedUserIds())
    }

    /**
     * The id is derived from the pair, not random. With a random id a second rating created a
     * second document, and the average counts documents — so rating someone twice moved their
     * score by two ratings' worth.
     */
    @Test
    fun ratingTheSamePersonTwiceOverwritesRatherThanCounting() = runTest {
        val repo = signedIn(repository(scriptedBackend()))

        repo.submitRating(toUserId = "bo", ratingValue = 5f, comment = "first")
        repo.submitRating(toUserId = "bo", ratingValue = 2f, comment = "second")

        val writes = requests.filter {
            it.url.toString().contains("/documents/ratings/") && it.method.value == "PATCH"
        }
        val ids = writes.map { it.url.toString().substringAfter("/documents/ratings/").substringBefore("?") }
        assertEquals(1, ids.distinct().size, "both ratings must land on one document, got $ids")
        assertEquals(listOf("bo"), repo.getRatedUserIds())
    }

    @Test
    fun ratedUserIdsAreClearedOnLogout() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.submitRating(toUserId = "bo", ratingValue = 4f, comment = "")
        assertEquals(listOf("bo"), repo.getRatedUserIds())

        repo.logout()

        assertTrue(repo.getRatedUserIds().isEmpty())
    }

    // --- Accepting a request with no ride posted --------------------------------------------

    /** The request every direct-accept test accepts, unless it overrides it. */
    private fun openRequest(
        riderId: String = "bo",
        seatsNeeded: Int = 1,
        womenOnly: Boolean = false,
        status: String = "active",
    ) = """
        {"fields":{"id":{"stringValue":"request_1"},"riderId":{"stringValue":"$riderId"},
         "riderName":{"stringValue":"Bo"},"origin":{"stringValue":"Snell"},
         "destination":{"stringValue":"Logan"},"originLat":{"doubleValue":42.34},
         "originLng":{"doubleValue":-71.09},"destLat":{"doubleValue":42.37},
         "destLng":{"doubleValue":-71.02},"seatsNeeded":{"integerValue":"$seatsNeeded"},
         "departureTime":{"integerValue":"$future"},"womenOnly":{"booleanValue":$womenOnly},
         "status":{"stringValue":"$status"}}}
    """.trimIndent()

    /**
     * A driver with nothing posted used to hit "Post a ride first, then offer it here" — the
     * host-side entry point needed an offer id, and they had none. The backing offer is minted
     * here now, mirroring the [findOrCreateBackingRequest] the rider side has always had.
     */
    @Test
    fun acceptingARequestWithNoPostedRideMintsTheBackingOffer() = runTest {
        documents["ride_requests/request_1"] = openRequest()
        val repo = signedIn(repository(scriptedBackend()))

        val match = repo.acceptRideRequestDirect("request_1", contribution = 12.0)

        assertEquals("accepted", match.status)
        assertEquals("me", match.hostId)
        assertEquals("bo", match.riderId)

        val minted = repo.getTripOfferById(match.offerId)
        assertTrue(minted != null, "the accept should have created a backing offer")
        assertEquals("me", minted.hostId)
        assertEquals("Snell", minted.origin)
        assertEquals("Logan", minted.destination)
        assertEquals(12.0, minted.costPerRider)
    }

    /**
     * The driver did not ask to advertise a ride, so the minted offer must never reach a feed.
     * Seats are sized to exactly this rider, so accepting takes the last one and
     * [applyAcceptedMatch] flips it to "full" — which [FeedProjector] filters out.
     */
    @Test
    fun theMintedOfferIsFullAndNeverReachesTheBrowseFeed() = runTest {
        documents["ride_requests/request_1"] = openRequest()
        val repo = signedIn(repository(scriptedBackend()))

        val match = repo.acceptRideRequestDirect("request_1", contribution = 8.0)

        val minted = repo.getTripOfferById(match.offerId)!!
        assertEquals("full", minted.status)
        assertEquals(0, minted.seatsLeft)
        assertTrue(
            repo.activeOffers.value.none { it.id == minted.id },
            "a ride the driver never posted must not appear in the feed",
        )
    }

    /** A women-only request must not quietly become a mixed ride. */
    @Test
    fun aWomenOnlyRequestMintsAWomenOnlyOffer() = runTest {
        documents["ride_requests/request_1"] = openRequest(womenOnly = true)
        val repo = signedIn(repository(scriptedBackend()))

        val match = repo.acceptRideRequestDirect("request_1", contribution = 6.0)

        assertTrue(repo.getTripOfferById(match.offerId)!!.womenOnly)
    }

    /** The rider's own seat count decides the ride's size, not a default of one. */
    @Test
    fun theMintedOfferSeatsTheWholeParty() = runTest {
        documents["ride_requests/request_1"] = openRequest(seatsNeeded = 3)
        val repo = signedIn(repository(scriptedBackend()))

        val match = repo.acceptRideRequestDirect("request_1", contribution = 5.0)

        assertEquals(3, repo.getTripOfferById(match.offerId)!!.totalSeats)
    }

    @Test
    fun youCannotAcceptYourOwnRideRequest() = runTest {
        documents["ride_requests/request_1"] = openRequest(riderId = "me")
        val repo = signedIn(repository(scriptedBackend()))

        val failure = assertFailsWith<SplitCruiserException> {
            repo.acceptRideRequestDirect("request_1", contribution = 5.0)
        }
        assertContains(failure.message!!, "your own ride request")
    }

    @Test
    fun aRequestThatIsNoLongerOpenCannotBeAccepted() = runTest {
        documents["ride_requests/request_1"] = openRequest(status = "cancelled")
        val repo = signedIn(repository(scriptedBackend()))

        val failure = assertFailsWith<SplitCruiserException> {
            repo.acceptRideRequestDirect("request_1", contribution = 5.0)
        }
        assertContains(failure.message!!, "no longer looking")
    }

    /** Two taps on the same request must not carry the rider twice. */
    @Test
    fun acceptingTheSameRequestTwiceIsRejected() = runTest {
        documents["ride_requests/request_1"] = openRequest()
        val repo = signedIn(repository(scriptedBackend()))

        repo.acceptRideRequestDirect("request_1", contribution = 9.0)
        assertFailsWith<SplitCruiserException> {
            repo.acceptRideRequestDirect("request_1", contribution = 9.0)
        }
    }

    /**
     * Instant-reserve used to mutate the offer directly and create no [TripMatch] at all, so a
     * rider who used this button had no `matchId` and no way to ever open chat with the host. It
     * now goes through the same accept path [offerSeatForRequest] uses.
     */
    @Test
    fun joinTripOfferDirectCreatesAnAcceptedMatchWithChatParticipants() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"3"},"totalSeats":{"integerValue":"3"},
             "costPerRider":{"doubleValue":10.0},"status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        val match = repo.joinTripOfferDirect("offer_1")

        assertEquals("accepted", match.status)
        assertEquals(listOf("bo", "me"), match.participants)
        assertTrue(repo.userMatches.value.any { it.id == match.id })
    }

    @Test
    fun joinTripOfferDirectDoesNotSelfNotifyTheRider() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"3"},"totalSeats":{"integerValue":"3"},
             "costPerRider":{"doubleValue":10.0},"status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        repo.joinTripOfferDirect("offer_1")

        // The rider is the one who just tapped the button; a "your request was accepted"
        // notification about their own instant action would be pointless.
        assertTrue(repo.notifications.value.none { it.title.contains("Ride Request Accepted") })
    }

    @Test
    fun joinTripOfferDirectNotifiesTheHost() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"3"},"totalSeats":{"integerValue":"3"},
             "costPerRider":{"doubleValue":10.0},"status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        repo.joinTripOfferDirect("offer_1")

        val notificationWrite = requests.singleOrNull {
            it.url.toString().contains("/documents/notifications/") && it.method.value == "PATCH"
        }
        assertTrue(notificationWrite != null, "the host must be notified of the instant join")
        val body = (notificationWrite!!.body as TextContent).text
        assertContains(body, "\"bo\"")
    }

    @Test
    fun joinTripOfferDirectSeedsAnInstantJoinSystemMessageDistinctFromAnAcceptedProposal() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"3"},"totalSeats":{"integerValue":"3"},
             "costPerRider":{"doubleValue":10.0},"status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        val match = repo.joinTripOfferDirect("offer_1")

        val messageWrite = requests.first {
            it.url.toString().contains("/documents/messages/") && it.method.value == "PATCH"
        }
        val body = (messageWrite.body as TextContent).text
        assertTrue(!body.contains("accepted by the host"), "nobody approved an instant reservation")
        assertContains(body, "\"isSystem\":{\"booleanValue\":true}")
        assertTrue(match.status == "accepted")
    }

    @Test
    fun joinTripOfferDirectReusesAPendingProposalRequestAndRejectsTheDoubleMatch() = runTest {
        documents["trip_offers/offer_1"] = """
            {"fields":{"id":{"stringValue":"offer_1"},"hostId":{"stringValue":"bo"},
             "seatsLeft":{"integerValue":"3"},"costPerRider":{"doubleValue":10.0},
             "departureTime":{"integerValue":"$future"},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        repo.requestSeatOnOffer("offer_1", contribution = 10.0)
        val failure = assertFailsWith<SplitCruiserException> { repo.joinTripOfferDirect("offer_1") }
        assertContains(failure.message!!, "already exists")
    }

    // --- Reading a request/offer that a filtered feed excludes -----------------------------

    @Test
    fun getRideRequestByIdReturnsFromCacheEvenAfterItLeavesActiveRequests() = runTest {
        documents["trip_offers/offer_mine"] = """
            {"fields":{"id":{"stringValue":"offer_mine"},"hostId":{"stringValue":"me"},
             "seatsLeft":{"integerValue":"3"},"totalSeats":{"integerValue":"3"},
             "departureTime":{"integerValue":"$future"},
             "costPerRider":{"doubleValue":10.0},"status":{"stringValue":"active"}}}
        """.trimIndent()
        documents["ride_requests/req_1"] = """
            {"fields":{"id":{"stringValue":"req_1"},"riderId":{"stringValue":"zo"},
             "riderName":{"stringValue":"Zo"},"seatsNeeded":{"integerValue":"1"},
             "departureTime":{"integerValue":"$future"},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        repo.offerSeatForRequest("req_1", "offer_mine", contribution = 10.0)

        // acceptMatch flips this to "matched", which activeRequests excludes — the exact case
        // that made a successful accept look like the request had vanished.
        assertEquals("matched", repo.getRideRequestById("req_1")?.status)
    }

    @Test
    fun fetchTripOfferFallsBackToNetworkAndPopulatesTheCache() = runTest {
        documents["trip_offers/offer_cold"] = """
            {"fields":{"id":{"stringValue":"offer_cold"},"hostId":{"stringValue":"bo"},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))
        assertNull(repo.getTripOfferById("offer_cold"), "must start with an empty cache")

        val fetched = repo.fetchTripOffer("offer_cold")

        assertEquals("bo", fetched.hostId)
        assertEquals("offer_cold", repo.getTripOfferById("offer_cold")?.id)
    }

    @Test
    fun fetchRideRequestFallsBackToNetworkAndPopulatesTheCache() = runTest {
        documents["ride_requests/req_cold"] = """
            {"fields":{"id":{"stringValue":"req_cold"},"riderId":{"stringValue":"zo"},
             "status":{"stringValue":"matched"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))
        assertNull(repo.getRideRequestById("req_cold"))

        val fetched = repo.fetchRideRequest("req_cold")

        assertEquals("zo", fetched.riderId)
        assertEquals("req_cold", repo.getRideRequestById("req_cold")?.id)
    }

    @Test
    fun fetchTripOfferThrowsWhenTrulyMissing() = runTest {
        val repo = signedIn(repository(scriptedBackend())) // no trip_offers/ghost seeded -> 404
        val failure = assertFailsWith<SplitCruiserException> { repo.fetchTripOffer("ghost") }
        assertContains(failure.message!!, "not found")
    }

    @Test
    fun fetchRideRequestThrowsWhenTrulyMissing() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        val failure = assertFailsWith<SplitCruiserException> { repo.fetchRideRequest("ghost") }
        assertContains(failure.message!!, "no longer exists")
    }

    // --- Accepting -------------------------------------------------------------------------

    @Test
    fun acceptingAMatchStillNotifiesTheRiderAndSeedsTheDefaultChatMessage() = runTest {
        documents["trip_offers/offer_mine"] = """
            {"fields":{"id":{"stringValue":"offer_mine"},"hostId":{"stringValue":"me"},
             "seatsLeft":{"integerValue":"3"},"totalSeats":{"integerValue":"3"},
             "departureTime":{"integerValue":"$future"},
             "costPerRider":{"doubleValue":10.0},"status":{"stringValue":"active"}}}
        """.trimIndent()
        documents["ride_requests/req_1"] = """
            {"fields":{"id":{"stringValue":"req_1"},"riderId":{"stringValue":"zo"},
             "riderName":{"stringValue":"Zo"},"seatsNeeded":{"integerValue":"1"},
             "departureTime":{"integerValue":"$future"},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        // offerSeatForRequest already calls acceptMatch internally; this asserts the notification
        // and system message acceptMatch itself is responsible for, which no test covered before.
        repo.offerSeatForRequest("req_1", "offer_mine", contribution = 10.0)

        val notificationWrite = requests.singleOrNull {
            it.url.toString().contains("/documents/notifications/") && it.method.value == "PATCH"
        }
        assertTrue(notificationWrite != null, "the rider must be notified of the acceptance")
        assertContains((notificationWrite!!.body as TextContent).text, "\"zo\"")

        val messageWrite = requests.first {
            it.url.toString().contains("/documents/messages/") && it.method.value == "PATCH"
        }
        assertContains((messageWrite.body as TextContent).text, "You're in!")
    }

    @Test
    fun sendSystemMessageIsFlaggedIsSystemAndKeepsARealSenderId() = runTest {
        documents["trip_offers/offer_mine"] = """
            {"fields":{"id":{"stringValue":"offer_mine"},"hostId":{"stringValue":"me"},
             "seatsLeft":{"integerValue":"3"},"totalSeats":{"integerValue":"3"},
             "departureTime":{"integerValue":"$future"},
             "costPerRider":{"doubleValue":10.0},"status":{"stringValue":"active"}}}
        """.trimIndent()
        documents["ride_requests/req_1"] = """
            {"fields":{"id":{"stringValue":"req_1"},"riderId":{"stringValue":"zo"},
             "riderName":{"stringValue":"Zo"},"seatsNeeded":{"integerValue":"1"},
             "departureTime":{"integerValue":"$future"},
             "status":{"stringValue":"active"}}}
        """.trimIndent()
        val repo = signedIn(repository(scriptedBackend()))

        repo.offerSeatForRequest("req_1", "offer_mine", contribution = 10.0)

        // A literal senderId of "system" would be rejected by the messages create rule, which
        // requires senderId == request.auth.uid — the real accepting user's uid, "me" here.
        val messageWrite = requests.first {
            it.url.toString().contains("/documents/messages/") && it.method.value == "PATCH"
        }
        val body = (messageWrite.body as TextContent).text
        assertContains(body, "\"senderId\":{\"stringValue\":\"me\"}")
        assertContains(body, "\"isSystem\":{\"booleanValue\":true}")
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
                     "departureTime":{"integerValue":"$future"},
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
                     "departureTime":{"integerValue":"$future"},
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
             "departureTime":{"integerValue":"$future"},
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
             "departureTime":{"integerValue":"$future"},
             "costPerRider":{"doubleValue":10.0},"status":{"stringValue":"active"}}}
        """.trimIndent()
        documents["ride_requests/req_1"] = """
            {"fields":{"id":{"stringValue":"req_1"},"riderId":{"stringValue":"zo"},
             "riderName":{"stringValue":"Zo"},"seatsNeeded":{"integerValue":"1"},
             "departureTime":{"integerValue":"$future"},
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

    /**
     * The driver is a traveller, not a vendor, so the trip cost divides `totalSeats + 1` ways.
     *
     * This is the difference between a split and a fare, and it is the whole reason the product
     * asks for a trip cost rather than a per-seat price. `calculateCostSplit` had encoded the
     * division since the beginning and had no callers at all; nothing in the app divided anything.
     */
    @Test
    fun theTripCostDividesAcrossTheRidersAndTheDriver() {
        val repo = repository(scriptedBackend())
        assertEquals(12.0, repo.perRiderShare(totalCost = 60.0, totalSeats = 4))
        assertEquals(30.0, repo.perRiderShare(totalCost = 60.0, totalSeats = 1))
        // A solo driver offering no seats keeps the whole cost rather than dividing by zero.
        assertEquals(60.0, repo.perRiderShare(totalCost = 60.0, totalSeats = 0))
    }

    /**
     * Rounded *up* to the cent. $100 over two riders and a driver is $33.333…; three shares of
     * $33.33 leave the host a cent short on every trip, which is the worse of the two errors.
     */
    @Test
    fun anUnevenShareRoundsUpToTheCentSoTheHostIsNeverShort() {
        val repo = repository(scriptedBackend())
        assertEquals(33.34, repo.perRiderShare(totalCost = 100.0, totalSeats = 2))
        assertEquals(0.0, repo.perRiderShare(totalCost = 0.0, totalSeats = 3))
        // Never negative: a caller that somehow gets a negative cost past validation gets zero,
        // not a share the app would render as "-$4.00 each".
        assertEquals(0.0, repo.perRiderShare(totalCost = -12.0, totalSeats = 3))
    }

    /**
     * Posting derives the share; it does not trust one the caller supplied.
     *
     * This is what keeps the stored figure and the figure previewed under the post-offer form the
     * same number, and it is why `RideFactory.makeTripOffer` takes no `costPerRider` at all.
     */
    @Test
    fun postingDerivesThePerRiderShareAndIgnoresAnySuppliedOne() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.postTripOffer(anOffer(costPerRider = 499.0, totalCost = 60.0, totalSeats = 3))

        val posted = repo.getHostedRides("me").single()
        assertEquals(15.0, posted.costPerRider)
        assertEquals(60.0, posted.totalCost)
    }

    /** An offer posted before `totalCost` existed keeps the per-rider figure it was created with. */
    @Test
    fun anOfferWithNoTripCostKeepsItsTypedPerRiderFigure() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.postTripOffer(anOffer(costPerRider = 12.0, totalCost = 0.0, totalSeats = 3))

        assertEquals(12.0, repo.getHostedRides("me").single().costPerRider)
    }

    /**
     * The trip-cost ceiling scales with the seat count, and its message names the field the host
     * typed into.
     *
     * A flat whole-trip cap cannot do that job. `MAX_CONTRIBUTION` on the derived share is what
     * actually binds, so a single ceiling is either unreachable — at eight seats the share cap is
     * hit first — or wrong for every smaller ride. And a host who entered a trip cost never chose
     * a per-rider figure, so "cost per rider cannot exceed $500" sends them looking for a field
     * the form does not have.
     */
    @Test
    fun theTripCostCeilingScalesWithTheSeatCountAndSaysSo() = runTest {
        val repo = signedIn(repository(scriptedBackend()))

        // Three seats plus the driver: $2000 divides to exactly $500 each and is allowed.
        repo.postTripOffer(anOffer(totalCost = 2_000.0, totalSeats = 3))
        assertEquals(500.0, repo.getHostedRides("me").single().costPerRider)

        val failure = assertFailsWith<SplitCruiserException> {
            repo.postTripOffer(anOffer(totalCost = 2_000.01, totalSeats = 3))
        }
        assertContains(failure.message!!, "3-seat ride")
        assertContains(failure.message!!, "2000")
    }

    @Test
    fun aNegativeTripCostIsRejected() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        assertFailsWith<SplitCruiserException> { repo.postTripOffer(anOffer(totalCost = -1.0)) }
    }

    // --- Chat ------------------------------------------------------------------------------

    /**
     * The regression behind "messages only arrive after I restart the app".
     *
     * The `messages` read rule tests `uid in resource.data.participants`, and Firestore rejects a
     * query it cannot prove only matches readable documents. A query narrowed to `matchId` alone
     * was therefore denied on every poll — and because the app pins `openChatMatchId` on the first
     * chat opened, that was the only query it ever issued again.
     */
    @Test
    fun theOpenChatQueryIsNarrowedByParticipantsAndNotJustByMatchId() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.openChat("match_1")

        repo.refreshNow()

        val chatQuery = requests.last { it.url.toString().contains(":runQuery") }
        val body = (chatQuery.body as TextContent).text
        assertContains(body, "\"matchId\"")
        assertContains(
            body,
            "ARRAY_CONTAINS",
            message = "without a participants filter the rule rejects the whole query",
        )
        assertContains(body, "\"participants\"")
    }

    /**
     * The second tap must issue no write at all.
     *
     * This test used to assert `2` here — "both taps are sent" — on the reasoning that they address
     * the same deterministic id, so only one card renders. Against a real Firestore that is wrong:
     * the second tap is an *update*, it carried a fresh `timestamp`, and the `messages` update rule
     * forbids changing `timestamp` (along with matchId, senderId, participants and contribution, so
     * neither party can repudiate a price both have seen). The second tap therefore came back
     * PERMISSION_DENIED and the user was told they lacked permission to confirm their own pickup.
     * The MockEngine accepts everything, so the suite could not see it; `client-writes.rules.test.ts`
     * pins the rule half of this contract against the emulator.
     */
    @Test
    fun confirmingTheSameProposalTwiceWritesOneMessage() = runTest {
        val repo = signedIn(repository(chatBackendServing(aProposalFrom = "bo")))
        repo.openChat("match_1")
        repo.refreshNow()

        repo.confirmPickupProposal("msg_proposal")
        repo.confirmPickupProposal("msg_proposal")

        val confirmationWrites = requests.filter {
            it.url.toString().contains("/documents/messages/msg_confirm_msg_proposal_me")
        }
        assertEquals(1, confirmationWrites.size, "the repeat tap must not re-issue a denied write")
    }

    /**
     * A message must never be written with only its sender in `participants`.
     *
     * The read rule is `uid in resource.data.participants`, so such a message is readable by its
     * sender and nobody else, for ever — and the old cache-miss fallback produced exactly that
     * shape with no error anywhere. Failing the send is the lesser harm.
     */
    @Test
    fun sendingIntoAnUnresolvableMatchFailsInsteadOfWritingAnUnreadableMessage() = runTest {
        val repo = signedIn(repository(scriptedBackend()))

        assertFailsWith<SplitCruiserException> {
            repo.sendPickupProposal("match_unknown", "360 Huntington Ave", "700 Comm Ave", "5:45 PM", 14.5)
        }
        assertTrue(
            requests.none { it.url.toString().contains("/documents/messages/") },
            "no message document should have been written",
        )
    }

    @Test
    fun confirmingAProposalMakesItsAmountTheRidesContribution() = runTest {
        val repo = signedIn(repository(chatBackendServing(aProposalFrom = "bo")))
        repo.openChat("match_1")
        repo.refreshNow()

        repo.confirmPickupProposal("msg_proposal")

        val matchWrite = requests.last { it.url.toString().contains("/documents/trip_matches/match_1") }
        assertContains((matchWrite.body as TextContent).text, "\"contribution\"")
        assertEquals(14.5, repo.getTripMatchById("match_1")?.contribution)
    }

    @Test
    fun youCannotConfirmYourOwnProposal() = runTest {
        val repo = signedIn(repository(chatBackendServing(aProposalFrom = "me")))
        repo.openChat("match_1")
        repo.refreshNow()

        val failure = assertFailsWith<SplitCruiserException> { repo.confirmPickupProposal("msg_proposal") }
        assertContains(failure.message!!, "your own")
    }

    @Test
    fun aPickupProposalCarriesTheAddressesAndTheAmount() = runTest {
        seedAcceptedMatch()
        val repo = signedIn(repository(scriptedBackend()))

        repo.sendPickupProposal("match_1", "360 Huntington Ave", "700 Comm Ave", "5:45 PM", 14.5)

        val write = requests.first {
            it.url.toString().contains("/documents/messages/") && it.method.value == "PATCH"
        }
        val body = (write.body as TextContent).text
        assertContains(body, "360 Huntington Ave")
        assertContains(body, "700 Comm Ave")
        assertContains(body, "\"contribution\"")
        // The readable fallback, for a push notification or a client too old to know the fields.
        assertContains(body, "for $14.50")
    }

    /**
     * An accepted match between "bo" (host) and "me" (rider).
     *
     * Every message write resolves the thread's `participants` from the match, so a test that sends
     * a message needs the match to exist — exactly as production does.
     */
    private fun seedAcceptedMatch() {
        documents["trip_matches/match_1"] = """
            {"fields":{"id":{"stringValue":"match_1"},"hostId":{"stringValue":"bo"},
             "riderId":{"stringValue":"me"},"contribution":{"doubleValue":9.0},
             "status":{"stringValue":"accepted"}}}
        """.trimIndent()
    }

    /** Serves one conversation containing a single pickup proposal sent by [aProposalFrom]. */
    private fun chatBackendServing(aProposalFrom: String): (HttpRequestData) -> Pair<HttpStatusCode, String> {
        seedAcceptedMatch()
        val proposal = """
            {"document":{"fields":{"id":{"stringValue":"msg_proposal"},
             "matchId":{"stringValue":"match_1"},"senderId":{"stringValue":"$aProposalFrom"},
             "senderName":{"stringValue":"Bo"},"type":{"stringValue":"pickup_proposal"},
             "pickupSpot":{"stringValue":"360 Huntington Ave"},
             "dropoffSpot":{"stringValue":"700 Comm Ave"},
             "pickupTime":{"stringValue":"5:45 PM"},
             "contribution":{"doubleValue":14.5},
             "participants":{"arrayValue":{"values":[{"stringValue":"me"},{"stringValue":"bo"}]}},
             "timestamp":{"integerValue":"$now"}}},"readTime":"x"}
        """.trimIndent()
        val base = scriptedBackend()
        return { request ->
            val url = request.url.toString()
            val isMessageQuery = url.contains(":runQuery") &&
                ((request.body as? TextContent)?.text?.contains("\"messages\"") == true)
            if (isMessageQuery) HttpStatusCode.OK to "[$proposal]" else base(request)
        }
    }

    @Test
    fun matchingOffersUseTheLooseOriginComparison() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        repo.postTripOffer(
            RideFactory.makeTripOffer(
                origin = "Snell Library Boston",
                destination = "Logan Airport",
                originLat = 42.3383, originLng = -71.0881,
                destLat = 42.3656, destLng = -71.0096,
                departureTime = future, totalSeats = 3, totalCost = 48.0,
                womenOnly = false, vehicleInfo = "", exitLocation = "",
            )
        )
        val matches = repo.findMatchingOffers(
            RideFactory.makeRideRequest("Snell Library", "Logan Airport", 1.0, 1.0, 2.0, 2.0, future, 1, "", false, "")
        )
        assertEquals(1, matches.size)
    }
}
