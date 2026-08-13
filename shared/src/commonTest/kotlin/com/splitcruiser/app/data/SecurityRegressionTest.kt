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
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression tests for the security-audit fixes. Each one reproduces a finding and asserts the
 * fixed behaviour, so a future change that reopens the hole fails here.
 *
 * These cover the client-side half of the fixes — the Firestore/Storage rules and the Cloud
 * Functions are the server-side half and are not executable from this suite (no emulator). The
 * assertions here check that the client cooperates with the tightened rules: it stops writing the
 * fields the rules now forbid, and it validates inputs before they reach the network.
 */
class SecurityRegressionTest {

    private val now = 1_000_000L

    private val config = FirebaseConfig(
        apiKey = "test-key",
        projectId = "split-cruiser-test",
        storageBucket = "bucket.appspot.com",
    )

    private val requests = mutableListOf<HttpRequestData>()
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

    private fun scriptedBackend(): (HttpRequestData) -> Pair<HttpStatusCode, String> = { request ->
        val url = request.url.toString()
        val stored = documents.entries.firstOrNull { (key, _) ->
            url.contains("/documents/$key") && request.method.value == "GET"
        }?.value
        when {
            url.contains("signInWithPassword") || url.contains("accounts:signUp") -> HttpStatusCode.OK to
                """{"localId":"me","email":"ana@neu.edu","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
            url.contains("signInWithIdp") -> HttpStatusCode.OK to
                """{"localId":"me","email":"ana@gmail.com","idToken":"tok","refreshToken":"ref","expiresIn":"3600","displayName":"Ana","photoUrl":""}"""
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

    private fun HttpRequestData.bodyText(): String = (body as? TextContent)?.text ?: ""

    /** PATCH writes, as (url, body) pairs — how every Firestore document write appears on the wire. */
    private fun patches(): List<Pair<String, String>> =
        requests.filter { it.method.value == "PATCH" }.map { it.url.toString() to it.bodyText() }

    // --- M3: Google ID token must be URL-encoded into the form-encoded postBody ---------------

    @Test
    fun googleTokenIsUrlEncodedSoItCannotInjectPostBodyParameters() = runTest {
        val repo = repository(scriptedBackend())
        // A token whose raw form carries `&providerId=…` would, unescaped, add a second postBody
        // parameter the endpoint might honour. Encoding neutralises it.
        repo.signInWithGoogle("evil&providerId=attacker.com")

        val idp = requests.single { it.url.toString().contains("signInWithIdp") }.bodyText()
        assertTrue(
            idp.contains("id_token=evil%26providerId%3Dattacker.com"),
            "token must be percent-encoded in postBody: $idp",
        )
        assertTrue(
            !idp.contains("providerId=attacker.com"),
            "an injected providerId must not survive as its own parameter: $idp",
        )
    }

    // --- M1: rating value must be bounded and non-self before it reaches the aggregate ---------

    @Test
    fun submitRatingRejectsAnOutOfRangeScoreBeforeTheNetwork() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        requests.clear()
        assertFailsWith<SplitCruiserException> { repo.submitRating("victim", 1e9f, "") }
        assertFailsWith<SplitCruiserException> { repo.submitRating("victim", 0f, "") }
        assertTrue(requests.isEmpty(), "an invalid rating must never be written: ${requests.map { it.url }}")
    }

    @Test
    fun submitRatingRejectsRatingYourself() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        requests.clear()
        assertFailsWith<SplitCruiserException> { repo.submitRating("me", 5f, "great, me") }
        assertTrue(requests.isEmpty())
    }

    // --- C3: the client never writes reputation aggregates onto the user document -------------

    @Test
    fun submitRatingWritesTheRatingButNeverPatchesTheUsersAggregate() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        requests.clear()
        repo.submitRating("victim", 5f, "solid")

        assertTrue(
            patches().any { (url, _) -> url.contains("/documents/ratings/") },
            "the rating document must be written",
        )
        assertTrue(
            patches().none { (url, body) ->
                url.contains("/users/") && (url.contains("ratingAvg") || body.contains("ratingAvg") ||
                    url.contains("ratingCount") || body.contains("ratingCount"))
            },
            "the client must not write ratingAvg/ratingCount — the aggregation function owns them: ${patches()}",
        )
    }

    @Test
    fun recordNoShowFilesAReportAndNeverPatchesTheUsersCounter() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        requests.clear()
        repo.recordNoShow("victim")

        assertTrue(
            patches().any { (url, _) -> url.contains("/documents/no_show_reports/noshow_me_victim") },
            "a no-show report document must be written: ${patches()}",
        )
        assertTrue(
            patches().none { (url, body) ->
                url.contains("/users/") && (url.contains("noShowCount") || body.contains("noShowCount"))
            },
            "the client must not write noShowCount directly: ${patches()}",
        )
    }

    @Test
    fun recordNoShowRejectsReportingYourself() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        requests.clear()
        assertFailsWith<SplitCruiserException> { repo.recordNoShow("me") }
        assertTrue(requests.isEmpty())
    }

    // --- C3: profile edits never re-send the protected reputation/tier fields -----------------

    @Test
    fun editingTheProfileNeverTouchesReputationOrTierFields() = runTest {
        val repo = signedIn(repository(scriptedBackend()))
        requests.clear()
        repo.updateUserProfileDetails(name = "Ana", lastInitial = "R", avatarUrl = "https://x/a.jpg")

        val userWrites = patches().filter { (url, _) -> url.contains("/documents/users/me") }
        assertTrue(userWrites.isNotEmpty(), "the profile edit must write the user document")
        assertTrue(
            userWrites.none { (url, body) ->
                listOf("ratingAvg", "ratingCount", "noShowCount", "verifiedTier").any {
                    url.contains(it) || body.contains(it)
                }
            },
            "a profile edit must not send any protected field: $userWrites",
        )
    }
}
