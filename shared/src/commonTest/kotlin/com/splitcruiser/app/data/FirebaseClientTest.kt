package com.splitcruiser.app.data

import com.splitcruiser.app.data.firebase.FilterOp
import com.splitcruiser.app.data.firebase.FirebaseAuthClient
import com.splitcruiser.app.data.firebase.FirebaseStorageClient
import com.splitcruiser.app.data.firebase.FirestoreClient
import com.splitcruiser.app.data.firebase.InMemoryStore
import com.splitcruiser.app.data.firebase.SplitCruiserException
import com.splitcruiser.app.data.firebase.StoredSession
import com.splitcruiser.app.data.firebase.StructuredQuery
import com.splitcruiser.app.data.firebase.TokenProvider
import com.splitcruiser.app.data.firebase.createFirebaseHttpClient
import com.splitcruiser.app.data.firebase.firebaseJson
import com.splitcruiser.app.data.firebase.stringValue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These exercise the wire format rather than Firebase itself. Everything asserted here is something
 * that fails silently or misleadingly in production if it is wrong — an auth scheme, a query
 * parameter, a JSON casing convention.
 */
class FirebaseClientTest {

    private val config = FirebaseConfig(
        apiKey = "test-key",
        projectId = "split-cruiser-test",
        storageBucket = "split-cruiser-test.appspot.com",
    )

    private val requests = mutableListOf<HttpRequestData>()

    @BeforeTest
    fun freezeTime() {
        currentTimeProvider = { 1_000_000L }
    }

    @AfterTest
    fun unfreezeTime() {
        currentTimeProvider = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() }
    }

    private fun engine(handler: (HttpRequestData) -> Pair<HttpStatusCode, String>) = MockEngine { request ->
        requests += request
        val (status, body) = handler(request)
        respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    private fun tokenProvider(
        session: StoredSession? = StoredSession("u1", "a@b.c", "id-token", "refresh-token", 9_000_000L),
        refresh: suspend (String) -> StoredSession = { StoredSession("u1", "", "refreshed", it, 9_000_000L) },
    ): TokenProvider {
        val store = InMemoryStore()
        val provider = TokenProvider(store, firebaseJson, refresh)
        if (session != null) provider.set(session)
        return provider
    }

    // --- Auth ------------------------------------------------------------------------------

    @Test
    fun signInParsesTheCamelCaseIdentityToolkitResponse() = runTest {
        val http = createFirebaseHttpClient(
            engine {
                HttpStatusCode.OK to """
                {"localId":"user_1","email":"ana@neu.edu","idToken":"tok",
                 "refreshToken":"ref","expiresIn":"3600","registered":true}
                """.trimIndent()
            }
        )
        val session = FirebaseAuthClient(http, config).signIn("ana@neu.edu", "hunter2")

        assertEquals("user_1", session.uid)
        assertEquals("ana@neu.edu", session.email)
        assertEquals("tok", session.idToken)
        // expiresIn is a *string* of seconds on both endpoints.
        assertEquals(1_000_000L + 3_600_000L, session.expiresAtMs)
        assertContains(requests.single().url.toString(), "accounts:signInWithPassword")
        assertContains(requests.single().url.toString(), "key=test-key")
    }

    @Test
    fun refreshUsesFormEncodingAndSnakeCase() = runTest {
        val http = createFirebaseHttpClient(
            engine {
                HttpStatusCode.OK to """
                {"user_id":"user_1","id_token":"new-tok","refresh_token":"new-ref","expires_in":"3600"}
                """.trimIndent()
            }
        )
        val session = FirebaseAuthClient(http, config).refresh("old-ref")

        assertEquals("user_1", session.uid)
        assertEquals("new-tok", session.idToken)
        assertEquals("new-ref", session.refreshToken)
        val request = requests.single()
        // Different host from every other auth call.
        assertContains(request.url.toString(), "securetoken.googleapis.com")
        assertContains(request.body.contentType.toString(), "application/x-www-form-urlencoded")
    }

    @Test
    fun identityToolkitErrorCodesBecomeReadableMessages() = runTest {
        val http = createFirebaseHttpClient(
            engine { HttpStatusCode.BadRequest to """{"error":{"code":400,"message":"EMAIL_EXISTS"}}""" }
        )
        val failure = assertFailsWith<SplitCruiserException> {
            FirebaseAuthClient(http, config).signUp("ana@neu.edu", "hunter2")
        }
        assertEquals("EMAIL_EXISTS", failure.code)
        assertContains(failure.message!!, "already registered")
    }

    @Test
    fun weakPasswordIsReportedInPlainLanguage() = runTest {
        val http = createFirebaseHttpClient(
            engine {
                HttpStatusCode.BadRequest to
                    """{"error":{"code":400,"message":"WEAK_PASSWORD : Password should be at least 6 characters"}}"""
            }
        )
        val failure = assertFailsWith<SplitCruiserException> {
            FirebaseAuthClient(http, config).signUp("ana@neu.edu", "x")
        }
        assertContains(failure.message!!, "at least 6 characters")
    }

    // --- Token refresh ---------------------------------------------------------------------

    @Test
    fun anExpiredTokenIsRefreshedBeforeUse() = runTest {
        var refreshes = 0
        val provider = tokenProvider(
            session = StoredSession("u1", "a@b.c", "stale", "ref", expiresAtMs = 1_000L),
            refresh = { refreshes++; StoredSession("u1", "", "fresh", it, 9_000_000L) },
        )
        assertEquals("fresh", provider.idToken())
        assertEquals(1, refreshes)
    }

    @Test
    fun aValidTokenIsNotRefreshed() = runTest {
        var refreshes = 0
        val provider = tokenProvider(refresh = { refreshes++; StoredSession("u1", "", "fresh", it, 9_000_000L) })
        assertEquals("id-token", provider.idToken())
        assertEquals(0, refreshes)
    }

    @Test
    fun aRestoredSessionSurvivesAColdStart() = runTest {
        val store = InMemoryStore()
        TokenProvider(store, firebaseJson) { StoredSession("u1", "", "x", it, 0) }
            .set(StoredSession("u1", "ana@neu.edu", "id", "ref", 9_000_000L))

        // A second provider over the same store stands in for the next launch.
        val restored = TokenProvider(store, firebaseJson) { StoredSession("u1", "", "x", it, 0) }.restore()
        assertNotNull(restored)
        assertEquals("u1", restored.uid)
        assertEquals("ana@neu.edu", restored.email)
    }

    @Test
    fun aCorruptStoredSessionIsDiscardedRatherThanCrashing() = runTest {
        val store = InMemoryStore()
        store.putString("firebase_session", "{ not json")
        val provider = TokenProvider(store, firebaseJson) { StoredSession("u1", "", "x", it, 0) }
        assertNull(provider.restore())
        assertNull(store.getString("firebase_session"))
    }

    // --- Firestore -------------------------------------------------------------------------

    @Test
    fun aFullWriteSendsAnUpdateMaskForEveryField() = runTest {
        val http = createFirebaseHttpClient(engine { HttpStatusCode.OK to "{}" })
        FirestoreClient(http, config, tokenProvider())
            .setDocument("trip_offers", "offer_1", TripOffer(id = "offer_1"), serializer<TripOffer>())

        val url = requests.single().url.toString()
        // Without a mask, PATCH merges and stale fields survive — the opposite of set() semantics.
        assertContains(url, "updateMask.fieldPaths=id")
        assertContains(url, "updateMask.fieldPaths=seatsLeft")
        assertContains(url, "updateMask.fieldPaths=passengerNames")
    }

    @Test
    fun aPartialWriteMasksOnlyTheFieldsItTouches() = runTest {
        val http = createFirebaseHttpClient(engine { HttpStatusCode.OK to "{}" })
        FirestoreClient(http, config, tokenProvider()).updateFields(
            "trip_offers",
            "offer_1",
            com.splitcruiser.app.data.firebase.buildFields("status" to stringValue("full")),
        )

        val url = requests.single().url.toString()
        assertContains(url, "updateMask.fieldPaths=status")
        // The security rules use affectedKeys().hasOnly([...]), so an over-broad mask is denied.
        assertTrue(!url.contains("updateMask.fieldPaths=hostId"), "mask must not widen: $url")
    }

    @Test
    fun firestoreCallsCarryABearerToken() = runTest {
        val http = createFirebaseHttpClient(engine { HttpStatusCode.OK to "{}" })
        FirestoreClient(http, config, tokenProvider())
            .getDocument("users", "u1", serializer<User>())
        assertEquals("Bearer id-token", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun aMissingDocumentIsNullRatherThanAnError() = runTest {
        val http = createFirebaseHttpClient(
            engine { HttpStatusCode.NotFound to """{"error":{"status":"NOT_FOUND","message":"nope"}}""" }
        )
        assertNull(FirestoreClient(http, config, tokenProvider()).getDocument("users", "ghost", serializer<User>()))
    }

    @Test
    fun aUnauthorizedResponseRefreshesOnceAndRetries() = runTest {
        var call = 0
        val http = createFirebaseHttpClient(
            engine {
                call++
                if (call == 1) {
                    HttpStatusCode.Unauthorized to """{"error":{"status":"UNAUTHENTICATED","message":"expired"}}"""
                } else {
                    HttpStatusCode.OK to """{"fields":{"id":{"stringValue":"u1"},"name":{"stringValue":"Ana"}}}"""
                }
            }
        )
        val user = FirestoreClient(http, config, tokenProvider())
            .getDocument("users", "u1", serializer<User>())

        assertEquals("Ana", user?.name)
        assertEquals(2, call)
        assertEquals("Bearer refreshed", requests.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun anEmptyQueryResultDecodesToAnEmptyList() = runTest {
        // Firestore answers an empty result set with one entry carrying only readTime.
        val http = createFirebaseHttpClient(
            engine { HttpStatusCode.OK to """[{"readTime":"2026-07-28T00:00:00Z"}]""" }
        )
        val offers = FirestoreClient(http, config, tokenProvider()).runQuery(
            StructuredQuery("trip_offers", listOf()),
            serializer<TripOffer>(),
        )
        assertEquals(emptyList(), offers)
    }

    @Test
    fun aQueryDecodesTheDocumentsItFinds() = runTest {
        val http = createFirebaseHttpClient(
            engine {
                HttpStatusCode.OK to """
                [{"document":{"name":"projects/p/databases/(default)/documents/trip_offers/o1",
                  "fields":{"id":{"stringValue":"o1"},"seatsLeft":{"integerValue":"2"},
                            "costPerRider":{"doubleValue":12.5}}}},
                 {"document":{"name":"projects/p/databases/(default)/documents/trip_offers/o2",
                  "fields":{"id":{"stringValue":"o2"}}}}]
                """.trimIndent()
            }
        )
        val offers = FirestoreClient(http, config, tokenProvider()).runQuery(
            StructuredQuery("trip_offers"),
            serializer<TripOffer>(),
        )
        assertEquals(listOf("o1", "o2"), offers.map { it.id })
        assertEquals(2, offers[0].seatsLeft)
        assertEquals(12.5, offers[0].costPerRider)
        // The second document omits seatsLeft, so the model default must apply.
        assertEquals(4, offers[1].seatsLeft)
    }

    @Test
    fun structuredQueriesSerialiseFiltersOrderingAndLimit() {
        val json = StructuredQuery(
            collection = "trip_offers",
            filters = listOf(
                com.splitcruiser.app.data.firebase.FieldFilter("status", FilterOp.Equal, stringValue("active")),
                com.splitcruiser.app.data.firebase.FieldFilter(
                    "departureTime",
                    FilterOp.GreaterThan,
                    com.splitcruiser.app.data.firebase.integerValue(123L),
                ),
            ),
            orderBy = listOf(com.splitcruiser.app.data.firebase.OrderBy("departureTime", descending = false)),
            limit = 200,
        ).toJson().toString()

        assertContains(json, "\"collectionId\":\"trip_offers\"")
        assertContains(json, "compositeFilter")
        assertContains(json, "\"op\":\"AND\"")
        assertContains(json, "GREATER_THAN")
        assertContains(json, "\"direction\":\"ASCENDING\"")
        assertContains(json, "\"limit\":200")
    }

    @Test
    fun aSingleFilterSkipsTheCompositeWrapper() {
        val json = StructuredQuery(
            collection = "users",
            filters = listOf(
                com.splitcruiser.app.data.firebase.FieldFilter("id", FilterOp.Equal, stringValue("u1"))
            ),
        ).toJson().toString()
        assertTrue(!json.contains("compositeFilter"), "single filter must not be wrapped: $json")
        assertContains(json, "fieldFilter")
    }

    @Test
    fun permissionDeniedIsReportedAsSuch() = runTest {
        val http = createFirebaseHttpClient(
            engine {
                HttpStatusCode.Forbidden to
                    """{"error":{"status":"PERMISSION_DENIED","message":"Missing or insufficient permissions."}}"""
            }
        )
        val failure = assertFailsWith<SplitCruiserException> {
            FirestoreClient(http, config, tokenProvider())
                .setDocument("trip_offers", "o1", TripOffer(), serializer<TripOffer>())
        }
        assertEquals("PERMISSION_DENIED", failure.code)
        assertContains(failure.message!!, "permission")
    }

    @Test
    fun aMissingIndexIsNamedRatherThanLookingLikeARulesFailure() = runTest {
        val http = createFirebaseHttpClient(
            engine {
                HttpStatusCode.BadRequest to
                    """{"error":{"status":"FAILED_PRECONDITION","message":"The query requires an index."}}"""
            }
        )
        val failure = assertFailsWith<SplitCruiserException> {
            FirestoreClient(http, config, tokenProvider())
                .runQuery(StructuredQuery("messages"), serializer<Message>())
        }
        assertContains(failure.message!!, "index")
    }

    @Test
    fun subcollectionPathsAreEncodedSegmentWise() = runTest {
        val http = createFirebaseHttpClient(engine { HttpStatusCode.OK to "{}" })
        FirestoreClient(http, config, tokenProvider())
            .deleteDocument("users/u1/blockedUsers", "u2")
        val url = requests.single().url.toString()
        // Slashes between segments must stay slashes; only the segments themselves are encoded.
        assertContains(url, "/documents/users/u1/blockedUsers/u2")
    }

    // --- Storage ---------------------------------------------------------------------------

    @Test
    fun storageUsesTheFirebaseAuthSchemeNotBearer() = runTest {
        val http = createFirebaseHttpClient(
            engine { HttpStatusCode.OK to """{"downloadTokens":"abc-123"}""" }
        )
        FirebaseStorageClient(http, config, tokenProvider())
            .uploadBytes("profile_pictures/u1.jpg", ByteArray(16), "image/jpeg")

        // Storage v0 rejects `Bearer`; the rest of Firebase rejects `Firebase`.
        assertEquals("Firebase id-token", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun uploadPercentEncodesThePathAndReturnsATokenisedUrl() = runTest {
        val http = createFirebaseHttpClient(
            engine { HttpStatusCode.OK to """{"downloadTokens":"abc-123"}""" }
        )
        val url = FirebaseStorageClient(http, config, tokenProvider())
            .uploadBytes("profile_pictures/u1.jpg", ByteArray(16), "image/jpeg")

        // The slash must be encoded here — Storage treats the object name as one opaque string.
        assertContains(requests.single().url.toString(), "profile_pictures%2Fu1.jpg")
        assertContains(url, "alt=media")
        assertContains(url, "token=abc-123")
    }

    @Test
    fun uploadRejectsAnOversizedImageBeforeHittingTheNetwork() = runTest {
        val http = createFirebaseHttpClient(engine { HttpStatusCode.OK to "{}" })
        assertFailsWith<IllegalArgumentException> {
            FirebaseStorageClient(http, config, tokenProvider())
                .uploadBytes("profile_pictures/u1.jpg", ByteArray(6 * 1024 * 1024), "image/jpeg")
        }
        assertTrue(requests.isEmpty(), "the request should never have been sent")
    }

    @Test
    fun callingFirestoreWhileSignedOutFailsClearly() = runTest {
        val http = createFirebaseHttpClient(engine { HttpStatusCode.OK to "{}" })
        val failure = assertFailsWith<SplitCruiserException> {
            FirestoreClient(http, config, tokenProvider(session = null))
                .getDocument("users", "u1", serializer<User>())
        }
        assertEquals("UNAUTHENTICATED", failure.code)
    }
}
