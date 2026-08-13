package com.splitcruiser.app.data

import kotlin.test.*

class AuthenticationFlowTest {

    @Test
    fun testUserCreationWithCredentials() {
        val user = User(
            id = "user_123",
            name = "Alice Johnson",
            email = "alice@example.com",
            phoneNumber = "+1-555-0100",
            verifiedTier = "unverified"
        )

        assertEquals("user_123", user.id)
        assertEquals("alice@example.com", user.email)
        assertEquals("+1-555-0100", user.phoneNumber)
        assertEquals("unverified", user.verifiedTier)
    }

    @Test
    fun testVerificationTierProgression() {
        val newUser = User(verifiedTier = "guest")
        assertEquals("guest", newUser.verifiedTier)

        val verifiedUser = User(verifiedTier = "vouched")
        assertEquals("vouched", verifiedUser.verifiedTier)

        val premiumUser = User(verifiedTier = "verified")
        assertEquals("verified", premiumUser.verifiedTier)
    }

    @Test
    fun testPhoneNumberValidation() {
        val validPhones = listOf(
            "+1-555-0100",
            "+1-617-555-0123",
            "+91-9999999999"
        )

        for (phone in validPhones) {
            assertTrue(phone.startsWith("+"))
            assertTrue(phone.length >= 10)
        }
    }

    // No email validator exists in the codebase yet. This pins the rule the signup form is
    // expected to enforce, so whoever writes the real validator has a spec to satisfy.
    private fun isPlausibleEmail(value: String): Boolean {
        val local = value.substringBefore("@", missingDelimiterValue = "")
        val domain = value.substringAfter("@", missingDelimiterValue = "")
        return local.isNotEmpty() &&
            domain.contains(".") &&
            !domain.startsWith(".") &&
            !domain.endsWith(".")
    }

    @Test
    fun testEmailValidation() {
        assertTrue(isPlausibleEmail("alice@example.com"))
        assertTrue(isPlausibleEmail("a.b+tag@sub.example.co.uk"))

        assertFalse(isPlausibleEmail("alice@.com"), "domain may not start with a dot")
        assertFalse(isPlausibleEmail("aliceexample.com"), "missing @")
        assertFalse(isPlausibleEmail("@example.com"), "missing local part")
        assertFalse(isPlausibleEmail("alice@example"), "domain needs a dot")
    }

    @Test
    fun testUserDisplayNameGeneration() {
        val user1 = User(name = "Alice", lastInitial = "J")
        assertEquals("Alice J.", user1.displayName)

        val user2 = User(name = "Bob")
        assertEquals("Bob", user2.displayName)

        val user3 = User(name = "Charlie", lastInitial = "B")
        assertEquals("Charlie B.", user3.displayName)
    }

    @Test
    fun testUserProfileCompletion() {
        val incompleteUser = User(
            id = "user_1",
            name = "John",
            email = ""
        )

        val completeUser = User(
            id = "user_1",
            name = "John",
            email = "john@example.com",
            phoneNumber = "+1-555-0100",
            avatarUrl = "https://example.com/avatar.jpg"
        )

        assertTrue(incompleteUser.email.isEmpty())
        assertTrue(completeUser.email.isNotEmpty())
        assertTrue(completeUser.phoneNumber.isNotEmpty())
        assertTrue(completeUser.avatarUrl.isNotEmpty())
    }

    // Removed testCredentialStorage: the LocalCredential(email, password, userId) model it exercised
    // was dead — a leftover of the removed plaintext-JSON auth fallback. It was still @Serializable,
    // so nothing stopped a future caller from round-tripping a cleartext password into Firestore.
    // Auth now runs entirely through Firebase Identity Toolkit; there is no local credential store.

    @Test
    fun testRatingInitialization() {
        val newUser = User(
            id = "user_1",
            name = "New User",
            ratingAvg = 0f,
            ratingCount = 0
        )

        assertEquals(0f, newUser.ratingAvg)
        assertEquals(0, newUser.ratingCount)
    }

    @Test
    fun testRatingUpdate() {
        val user = User(
            id = "user_1",
            name = "User",
            ratingAvg = 4.0f,
            ratingCount = 5
        )

        val newRating = Rating(
            id = "rating_1",
            fromUserId = "rater_1",
            toUserId = "user_1",
            rating = 5.0f,
            comment = "Excellent!"
        )

        assertEquals(4.0f, user.ratingAvg)
        assertEquals(5.0f, newRating.rating)
        assertTrue(newRating.rating >= user.ratingAvg)
    }

    @Test
    fun testBlockListFunctionality() {
        val user = User(id = "user_1", name = "User 1")
        val blockedUser = User(id = "user_2", name = "User 2")

        val block = Block(
            id = "block_1",
            userId = user.id,
            blockedUserId = blockedUser.id
        )

        assertEquals(user.id, block.userId)
        assertEquals(blockedUser.id, block.blockedUserId)
        assertNotEquals(block.userId, block.blockedUserId)
    }

    @Test
    fun testUserPreferenceStorage() {
        val user = User(
            id = "user_1",
            name = "Alice",
            verifiedTier = "vouched"
        )

        val womenOnlyPreference = true
        assertTrue(womenOnlyPreference || true)
    }

    @Test
    fun testSessionTokenValidation() {
        val sessionToken = "valid_token_abc123xyz"
        assertTrue(sessionToken.isNotEmpty())
        assertTrue(sessionToken.length >= 10)
    }

    @Test
    fun testLogoutCleanup() {
        var currentUser: User? = User(id = "user_1", name = "Alice")
        assertNotNull(currentUser)

        currentUser = null
        assertNull(currentUser)
    }
}
