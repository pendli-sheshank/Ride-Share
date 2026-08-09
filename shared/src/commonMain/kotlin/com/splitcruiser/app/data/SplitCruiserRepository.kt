package com.splitcruiser.app.data

import com.splitcruiser.app.data.firebase.FieldFilter
import com.splitcruiser.app.data.firebase.FilterOp
import com.splitcruiser.app.data.firebase.FirebaseAuthClient
import com.splitcruiser.app.data.firebase.FirebaseStorageClient
import com.splitcruiser.app.data.firebase.FirestoreClient
import com.splitcruiser.app.data.firebase.KeyValueStore
import com.splitcruiser.app.data.firebase.OrderBy
import com.splitcruiser.app.data.firebase.SplitCruiserException
import com.splitcruiser.app.data.firebase.StoredSession
import com.splitcruiser.app.data.firebase.StructuredQuery
import com.splitcruiser.app.data.firebase.TokenProvider
import com.splitcruiser.app.data.firebase.arrayOfStrings
import com.splitcruiser.app.data.firebase.booleanValue
import com.splitcruiser.app.data.firebase.buildFields
import com.splitcruiser.app.data.firebase.createFirebaseHttpClient
import com.splitcruiser.app.data.firebase.doubleValue
import com.splitcruiser.app.data.firebase.firebaseJson
import com.splitcruiser.app.data.firebase.integerValue
import com.splitcruiser.app.data.firebase.stringValue
import io.ktor.client.engine.HttpClientEngine
import kotlin.math.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer

/**
 * The whole backend, shared by Android and iOS.
 *
 * This replaces the Android-only `SawaariRepository`, which could not be part of `:shared` because
 * it took an `android.content.Context` and drove the native Firebase SDKs — which is why iOS had no
 * backend at all. Everything here is plain Kotlin over Firebase's REST APIs, so both platforms run
 * this exact code against the same Firestore collections.
 *
 * Two deliberate differences from the implementation it replaces:
 *
 * 1. **Firebase is the only store.** The old repository silently fell back to Moshi-serialised JSON
 *    files (including plaintext passwords) whenever Firebase was unconfigured, which is what CI and
 *    any unsigned build actually ran on. The in-memory maps here are a cache of Firestore, never a
 *    substitute for it.
 * 2. **Polling instead of snapshot listeners.** Firestore's realtime channel is gRPC-only and has no
 *    REST equivalent. In practice this changes less than it sounds: the old code already re-derived
 *    every feed on a 10-second timer. Writes update the cache immediately, so the UI stays instant.
 */
class SplitCruiserRepository internal constructor(
    private val config: FirebaseConfig,
    store: KeyValueStore,
    engine: HttpClientEngine?,
) {
    /** The constructor Swift and Android call. Kotlin default arguments do not survive into Swift. */
    constructor(config: FirebaseConfig, store: KeyValueStore) : this(config, store, null)

    private val scope = CoroutineScope(SupervisorJob())
    private val http = createFirebaseHttpClient(engine)
    private val auth = FirebaseAuthClient(http, config)
    private val tokens = TokenProvider(store, firebaseJson) { auth.refresh(it) }
    private val firestore = FirestoreClient(http, config, tokens)
    private val storage = FirebaseStorageClient(http, config, tokens)

    /** False when the FIREBASE_* values are missing or still placeholders. */
    val isFirebaseEnabled: Boolean get() = config.isConfigured

    /** False when GOOGLE_WEB_CLIENT_ID is unset, in which case the UI hides the Google button. */
    val isGoogleSignInEnabled: Boolean get() = config.isGoogleSignInConfigured

    /** The audience the platform must request its Google ID token for. */
    val googleWebClientId: String get() = config.googleWebClientId

    // --- Caches -----------------------------------------------------------------------------

    private val users = MutableStateFlow<Map<String, User>>(emptyMap())
    private val offers = MutableStateFlow<Map<String, TripOffer>>(emptyMap())
    private val requests = MutableStateFlow<Map<String, RideRequest>>(emptyMap())
    private val matches = MutableStateFlow<Map<String, TripMatch>>(emptyMap())
    private val messages = MutableStateFlow<Map<String, Message>>(emptyMap())
    private val blocks = MutableStateFlow<Map<String, Block>>(emptyMap())
    private val vehicles = MutableStateFlow<Map<String, Vehicle>>(emptyMap())

    // --- Public state -----------------------------------------------------------------------

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /** Null until onboarding has run, or for an account that predates it. */
    private val _contactDetails = MutableStateFlow<ContactDetails?>(null)
    val contactDetails: StateFlow<ContactDetails?> = _contactDetails.asStateFlow()

    private val _activeOffers = MutableStateFlow<List<TripOffer>>(emptyList())
    val activeOffers: StateFlow<List<TripOffer>> = _activeOffers.asStateFlow()

    private val _activeRequests = MutableStateFlow<List<RideRequest>>(emptyList())
    val activeRequests: StateFlow<List<RideRequest>> = _activeRequests.asStateFlow()

    private val _myRideRequests = MutableStateFlow<List<RideRequest>>(emptyList())
    val myRideRequests: StateFlow<List<RideRequest>> = _myRideRequests.asStateFlow()

    private val _userMatches = MutableStateFlow<List<TripMatch>>(emptyList())
    val userMatches: StateFlow<List<TripMatch>> = _userMatches.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationAlert>>(emptyList())
    val notifications: StateFlow<List<NotificationAlert>> = _notifications.asStateFlow()

    private val _allMessages = MutableStateFlow<List<Message>>(emptyList())
    val allMessages: StateFlow<List<Message>> = _allMessages.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    // --- Lifecycle --------------------------------------------------------------------------

    private var syncJobs = mutableListOf<Job>()
    private var foreground = true
    private var openChatMatchId: String? = null

    /**
     * Restores any stored session and begins polling.
     *
     * Session restore is new: the old repository kept the signed-in user in memory only, so every
     * cold start landed on the login screen even though Firebase itself considered the user signed
     * in.
     */
    fun start() {
        if (!config.isConfigured) {
            logWarn(LOG_TAG, "Firebase is not configured; no backend calls will be made.", null)
            return
        }
        if (syncJobs.isNotEmpty()) return

        val restored = tokens.restore()
        if (restored != null) {
            scope.launch { runCatching { loadSignedInUser(restored) } }
        }

        syncJobs += scope.launch { pollLoop(::refreshFeeds) { if (foreground) 20_000L else 300_000L } }
        syncJobs += scope.launch { pollLoop(::refreshMatches) { if (foreground) 20_000L else 300_000L } }
        syncJobs += scope.launch { pollLoop(::refreshNotifications) { if (foreground) 60_000L else 600_000L } }
        syncJobs += scope.launch {
            pollLoop(::refreshChat) { if (openChatMatchId != null && foreground) 3_000L else 30_000L }
        }
        // Expiring a ride that has departed needs no network, so it stays on a cheap local timer.
        syncJobs += scope.launch {
            while (isActive) {
                delay(10_000L)
                recomputeFeeds()
            }
        }
    }

    fun stop() {
        syncJobs.forEach { it.cancel() }
        syncJobs.clear()
    }

    /** Backgrounded apps poll far less. Android calls this from the lifecycle, iOS from ScenePhase. */
    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
    }

    /** Tightens the message poll while a conversation is on screen. Pass null on leaving. */
    fun openChat(matchId: String?) {
        openChatMatchId = matchId
    }

    private suspend fun pollLoop(tick: suspend () -> Unit, interval: () -> Long) {
        var backoff = 1_000L
        while (scope.isActive) {
            val ok = runCatching { tick() }.isSuccess.also { succeeded ->
                _isConnected.value = succeeded
                if (succeeded) _lastSyncTime.value = nowMs()
            }
            if (ok) {
                backoff = 1_000L
                delay(interval())
            } else {
                // Back off on failure so a dead network does not hammer the API.
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    // --- Polling ----------------------------------------------------------------------------

    private suspend fun refreshFeeds() {
        val now = nowMs()
        val liveOffers = firestore.runQuery(
            StructuredQuery(
                collection = "trip_offers",
                filters = listOf(
                    FieldFilter("status", FilterOp.Equal, stringValue("active")),
                    FieldFilter("departureTime", FilterOp.GreaterThan, integerValue(now)),
                ),
                orderBy = listOf(OrderBy("departureTime", descending = false)),
                limit = 200,
            ),
            serializer<TripOffer>(),
        )
        val liveRequests = firestore.runQuery(
            StructuredQuery(
                collection = "ride_requests",
                filters = listOf(
                    FieldFilter("status", FilterOp.Equal, stringValue("active")),
                    FieldFilter("departureTime", FilterOp.GreaterThan, integerValue(now)),
                ),
                orderBy = listOf(OrderBy("departureTime", descending = false)),
                limit = 200,
            ),
            serializer<RideRequest>(),
        )
        offers.value = offers.value + liveOffers.associateBy { it.id }
        requests.value = requests.value + liveRequests.associateBy { it.id }
        loadMissingUsers((liveOffers.map { it.hostId } + liveRequests.map { it.riderId }).toSet())
        recomputeFeeds()
    }

    private suspend fun refreshMatches() {
        val uid = tokens.uid ?: return
        // Two queries rather than one: a rule cannot express "hostId or riderId" as a single filter,
        // and an unfiltered read of trip_matches is denied.
        val hosted = queryMatches("hostId", uid)
        val ridden = queryMatches("riderId", uid)
        matches.value = matches.value + (hosted + ridden).associateBy { it.id }
        recomputeFeeds()
    }

    private suspend fun queryMatches(field: String, uid: String): List<TripMatch> =
        firestore.runQuery(
            StructuredQuery(
                collection = "trip_matches",
                filters = listOf(FieldFilter(field, FilterOp.Equal, stringValue(uid))),
                orderBy = listOf(OrderBy("timestamp", descending = true)),
                limit = 100,
            ),
            serializer<TripMatch>(),
        )

    private suspend fun refreshNotifications() {
        val uid = tokens.uid ?: return
        val alerts = firestore.runQuery(
            StructuredQuery(
                collection = "notifications",
                filters = listOf(FieldFilter("userId", FilterOp.Equal, stringValue(uid))),
                orderBy = listOf(OrderBy("timestamp", descending = true)),
                limit = 50,
            ),
            serializer<NotificationAlert>(),
        )
        _notifications.value = alerts
    }

    private suspend fun refreshChat() {
        val uid = tokens.uid ?: return
        val matchId = openChatMatchId
        val found = if (matchId != null) {
            firestore.runQuery(
                StructuredQuery(
                    collection = "messages",
                    filters = listOf(FieldFilter("matchId", FilterOp.Equal, stringValue(matchId))),
                    orderBy = listOf(OrderBy("timestamp", descending = false)),
                    limit = 200,
                ),
                serializer<Message>(),
            )
        } else {
            firestore.runQuery(
                StructuredQuery(
                    collection = "messages",
                    filters = listOf(FieldFilter("participants", FilterOp.ArrayContains, stringValue(uid))),
                    orderBy = listOf(OrderBy("timestamp", descending = true)),
                    limit = 100,
                ),
                serializer<Message>(),
            )
        }
        messages.value = messages.value + found.associateBy { it.id }
        publishMessages()
    }

    /** Pull-to-refresh, and what the old `syncDataWithFirestore()` did. */
    @Throws(Exception::class)
    suspend fun refreshNow() {
        refreshBlocks()
        refreshFeeds()
        refreshMatches()
        refreshNotifications()
        refreshChat()
    }

    private suspend fun loadMissingUsers(ids: Set<String>) {
        val missing = ids.filter { it.isNotBlank() && !users.value.containsKey(it) }
        if (missing.isEmpty()) return
        val loaded = missing.mapNotNull { id ->
            runCatching { firestore.getDocument("users", id, serializer<User>()) }.getOrNull()
        }
        users.value = users.value + loaded.associateBy { it.id }
    }

    private fun recomputeFeeds() {
        val feeds = FeedProjector.project(
            currentUser = _currentUser.value,
            offers = offers.value.values,
            requests = requests.value.values,
            matches = matches.value.values,
            blocks = blocks.value.values,
            now = nowMs(),
        )
        _activeOffers.value = feeds.activeOffers
        _activeRequests.value = feeds.activeRequests
        _myRideRequests.value = feeds.myRideRequests
        _userMatches.value = feeds.userMatches
    }

    private fun publishMessages() {
        _allMessages.value = messages.value.values.sortedBy { it.timestamp }
    }

    // --- Authentication ---------------------------------------------------------------------

    /**
     * Returns true when the account still needs a profile.
     *
     * Throws rather than returning `Result`: `kotlin.Result` is an inline value class and does not
     * export usefully to Swift. Android gets its `Result` shape back through shims in androidMain.
     */
    @Throws(Exception::class)
    suspend fun signUpWithEmail(email: String, password: String): Boolean {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password.trim()
        requireValid(trimmedEmail.isNotEmpty() && trimmedPassword.isNotEmpty()) {
            "Email and password cannot be empty"
        }
        requireValid(trimmedPassword.length >= 6) { "Password must be at least 6 characters" }
        requireValid(trimmedEmail.contains("@") && trimmedEmail.contains(".")) {
            "Please enter a valid email address."
        }
        requireConfigured()

        val session = auth.signUp(trimmedEmail, trimmedPassword)
        tokens.set(session)
        runCatching { auth.sendEmailVerification(session.idToken) }
            .onFailure { logWarn(LOG_TAG, "Could not send the verification email", it) }

        val newUser = User(id = session.uid, email = trimmedEmail, verifiedTier = "vouched")
        firestore.setDocument("users", session.uid, newUser, serializer<User>())
        adoptUser(newUser)
        return true
    }

    @Throws(Exception::class)
    suspend fun logInWithEmail(email: String, password: String): Boolean {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password.trim()
        requireValid(trimmedEmail.isNotEmpty() && trimmedPassword.isNotEmpty()) {
            "Email and password cannot be empty"
        }
        requireConfigured()

        val session = auth.signIn(trimmedEmail, trimmedPassword)
        tokens.set(session)
        return loadSignedInUser(session)
    }

    /**
     * Signs in with a Google ID token that the platform has already obtained.
     *
     * There is no separate sign-up: Identity Toolkit creates the account on first exchange, and
     * [loadSignedInUser] writes the profile document when it finds none — the same path a returning
     * user takes. So the caller does not have to know which it is.
     */
    @Throws(Exception::class)
    suspend fun signInWithGoogle(googleIdToken: String): Boolean {
        requireValid(googleIdToken.isNotBlank()) {
            "Google sign-in did not return a credential. Please try again."
        }
        requireConfigured()

        val result = auth.signInWithGoogle(googleIdToken)
        tokens.set(result.session)
        return loadSignedInUser(result.session, avatarUrl = result.photoUrl)
    }

    /**
     * True when the profile is still incomplete, which is what routes the UI to profile setup.
     *
     * [avatarUrl] seeds a new document from the identity provider. Google's display name is
     * deliberately *not* seeded: this returns `name.isEmpty()`, so filling it in would skip the
     * profile screen — and with it the community and home area, which nothing else collects.
     */
    private suspend fun loadSignedInUser(session: StoredSession, avatarUrl: String = ""): Boolean {
        val existing = firestore.getDocument("users", session.uid, serializer<User>())
        val user = if (existing != null) {
            // The uid on the token is authoritative. A document written before the `id` field
            // existed — or hand-edited in the Firebase console — would otherwise leave the signed-in
            // user with an empty id, which silently breaks every ownership check downstream.
            existing.copy(
                id = session.uid,
                email = existing.email.ifBlank { session.email },
            )
        } else {
            User(
                id = session.uid,
                email = session.email,
                avatarUrl = avatarUrl,
                verifiedTier = "vouched",
            ).also { firestore.setDocument("users", session.uid, it, serializer<User>()) }
        }

        adoptUser(user)
        loadContactDetails(session.uid)
        runCatching { refreshNow() }
            .onFailure { logWarn(LOG_TAG, "First sync after login failed", it) }
        return user.name.isEmpty()
    }

    @Throws(Exception::class)
    suspend fun sendPasswordReset(email: String) {
        requireConfigured()
        auth.sendPasswordReset(email.trim().lowercase())
    }

    fun logout() {
        stop()
        tokens.clear()
        _currentUser.value = null
        _contactDetails.value = null
        users.value = emptyMap()
        offers.value = emptyMap()
        requests.value = emptyMap()
        matches.value = emptyMap()
        messages.value = emptyMap()
        blocks.value = emptyMap()
        _notifications.value = emptyList()
        _allMessages.value = emptyList()
        recomputeFeeds()
    }

    // --- Profile ----------------------------------------------------------------------------

    @Throws(Exception::class)
    suspend fun createUserProfile(
        name: String,
        lastInitial: String,
        homeArea: String,
        contact: ContactDetails,
        vehicle: Vehicle?,
    ) {
        val user = requireUser()
        requireValid(
            name.trim().isNotEmpty() && lastInitial.trim().isNotEmpty() && homeArea.isNotEmpty()
        ) { "All profile fields are required." }

        val updated = user.copy(
            name = name.trim(),
            lastInitial = lastInitial.trim(),
            homeArea = homeArea,
            phoneNumber = contact.phoneNumber.trim(),
        )
        firestore.setDocument("users", user.id, updated, serializer<User>())
        adoptUser(updated)

        // After the user document, and tolerantly: a rejected private write must not strand an
        // account with no profile at all, which would drop it back onto the onboarding screen.
        runCatching { saveContactDetails(contact.copy(phoneNumber = contact.phoneNumber.trim())) }
            .onFailure { logWarn(LOG_TAG, "Could not store the private contact details", it) }

        if (vehicle != null) saveVehicle(vehicle.copy(ownerId = user.id))
    }

    /**
     * The four-argument form, kept because `ViewModel.swift` calls it and Kotlin default arguments
     * do not survive into Swift. iOS has no onboarding fields yet, so it stores none.
     */
    @Throws(Exception::class)
    suspend fun createUserProfile(
        name: String,
        lastInitial: String,
        homeArea: String,
        vehicle: Vehicle?,
    ) = createUserProfile(name, lastInitial, homeArea, ContactDetails(), vehicle)

    @Throws(Exception::class)
    suspend fun updateUserProfileDetails(
        name: String,
        lastInitial: String,
        avatarUrl: String,
    ) {
        val user = requireUser()
        val updated = user.copy(
            name = name,
            lastInitial = lastInitial,
            avatarUrl = avatarUrl,
        )
        firestore.setDocument("users", user.id, updated, serializer<User>())
        adoptUser(updated)
    }

    /**
     * Stores the private half of a profile: where the user lives, so a ride request can fill its own
     * pickup in.
     *
     * A subcollection rather than fields on the user document, because `users` is world-readable —
     * the feeds need a host's name and rating — and a home address is not something to publish to
     * everyone who can open the app.
     */
    @Throws(Exception::class)
    suspend fun saveContactDetails(details: ContactDetails) {
        val user = requireUser()
        firestore.setDocument("users/${user.id}/private", CONTACT_DOC, details, serializer<ContactDetails>())
        _contactDetails.value = details

        // The phone number is the exception: the trip detail screen shows a matched host's number,
        // so it has to live on the readable document to be of any use.
        if (details.phoneNumber != user.phoneNumber) {
            firestore.updateFields(
                "users",
                user.id,
                buildFields("phoneNumber" to stringValue(details.phoneNumber)),
            )
            adoptUser(user.copy(phoneNumber = details.phoneNumber))
        }
    }

    private suspend fun loadContactDetails(uid: String) {
        _contactDetails.value = runCatching {
            firestore.getDocument("users/$uid/private", CONTACT_DOC, serializer<ContactDetails>())
        }.getOrNull()
    }

    @Throws(Exception::class)
    suspend fun saveVehicle(vehicle: Vehicle) {
        val user = requireUser()
        firestore.setDocument("vehicles", user.id, vehicle.copy(ownerId = user.id), serializer<Vehicle>())
        vehicles.value = vehicles.value + (user.id to vehicle)
    }

    fun getVehicleInfo(userId: String): Vehicle? = vehicles.value[userId]

    @Throws(Exception::class)
    suspend fun fetchVehicleInfo(userId: String): Vehicle? {
        val found = runCatching {
            firestore.getDocument("vehicles", userId, serializer<Vehicle>())
        }.getOrNull()
        if (found != null) vehicles.value = vehicles.value + (userId to found)
        return found
    }

    fun getUserPublicProfile(userId: String): User? = users.value[userId]

    @Throws(Exception::class)
    suspend fun fetchUserProfile(userId: String): User {
        val user = firestore.getDocument("users", userId, serializer<User>())
            ?: throw SplitCruiserException("User not found.")
        users.value = users.value + (userId to user)
        return user
    }

    @Throws(Exception::class)
    suspend fun uploadProfilePicture(userId: String, bytes: ByteArray): String {
        val url = storage.uploadBytes("profile_pictures/$userId.jpg", bytes, "image/jpeg")
        val user = requireUser()
        if (user.id == userId) {
            val updated = user.copy(avatarUrl = url)
            firestore.setDocument("users", userId, updated, serializer<User>())
            adoptUser(updated)
        }
        return url
    }

    @Throws(Exception::class)
    suspend fun deleteProfilePicture(userId: String) {
        storage.delete("profile_pictures/$userId.jpg")
        val user = requireUser()
        if (user.id == userId) {
            val updated = user.copy(avatarUrl = "")
            firestore.setDocument("users", userId, updated, serializer<User>())
            adoptUser(updated)
        }
    }

    // --- Posting rides ----------------------------------------------------------------------

    @Throws(Exception::class)
    suspend fun postTripOffer(offer: TripOffer) {
        val user = requireUser()
        validateTripOfferOrThrow(offer)

        val id = newId("offer")
        val finalOffer = offer.copy(
            id = id,
            hostId = user.id,
            hostName = user.name,
            hostRating = user.ratingAvg,
            originGeohash = GeoUtils.encodeGeohash(offer.originLat, offer.originLng, 7),
            destGeohash = GeoUtils.encodeGeohash(offer.destLat, offer.destLng, 7),
            costEstimate = offer.costPerRider * offer.totalSeats,
            seatsLeft = offer.totalSeats,
            status = "active",
        )
        firestore.setDocument("trip_offers", id, finalOffer, serializer<TripOffer>())
        offers.value = offers.value + (id to finalOffer)
        recomputeFeeds()

        notifyRidersOfMatchingOffer(finalOffer)
    }

    @Throws(Exception::class)
    suspend fun postRideRequest(request: RideRequest) {
        val user = requireUser()
        validateRideRequestOrThrow(request)

        val id = newId("request")
        val finalRequest = request.copy(
            id = id,
            riderId = user.id,
            riderName = user.name,
            riderRating = user.ratingAvg,
            originGeohash = GeoUtils.encodeGeohash(request.originLat, request.originLng, 7),
            destGeohash = GeoUtils.encodeGeohash(request.destLat, request.destLng, 7),
            status = "active",
        )
        firestore.setDocument("ride_requests", id, finalRequest, serializer<RideRequest>())
        requests.value = requests.value + (id to finalRequest)
        recomputeFeeds()

        notifyHostsOfMatchingRequest(finalRequest)
    }

    @Throws(Exception::class)
    suspend fun updateTripOfferStatus(offerId: String, newStatus: String) {
        val offer = offers.value[offerId] ?: throw SplitCruiserException("Trip offer not found.")
        firestore.updateFields(
            "trip_offers",
            offerId,
            buildFields("status" to stringValue(newStatus)),
        )
        offers.value = offers.value + (offerId to offer.copy(status = newStatus))
        recomputeFeeds()
    }

    @Throws(Exception::class)
    suspend fun updateRideRequestStatus(requestId: String, newStatus: String) {
        val request = requests.value[requestId] ?: throw SplitCruiserException("Ride request not found.")
        firestore.updateFields(
            "ride_requests",
            requestId,
            buildFields("status" to stringValue(newStatus)),
        )
        requests.value = requests.value + (requestId to request.copy(status = newStatus))
        recomputeFeeds()
    }

    // --- Joining and matching ---------------------------------------------------------------

    /**
     * Instantly reserves a seat — no host approval, unlike [requestSeatOnOffer]. Returns the
     * accepted [TripMatch] so the caller can open chat with the host immediately.
     *
     * This used to mutate the offer directly and create no [TripMatch] at all, which meant an
     * instant-reserve rider had no `matchId` and therefore no way to ever open a chat thread with
     * the host, despite being told to "coordinate details" with them. It now goes through the same
     * match machinery [offerSeatForRequest] uses — create, then immediately accept — so every
     * successful join, whichever button produced it, ends up with a real chat thread.
     */
    @Throws(Exception::class)
    suspend fun joinTripOfferDirect(offerId: String): TripMatch {
        val user = requireUser()
        val offer = loadOffer(offerId)

        if (offer.hostId == user.id) throw SplitCruiserException("You cannot join your own ride.")
        if (user.id in offer.passengers) {
            throw SplitCruiserException("You have already reserved a seat on this trip.")
        }
        if (offer.seatsLeft <= 0) throw SplitCruiserException("This trip has no seats left!")

        val request = findOrCreateBackingRequest(offer, user)
        val match = createMatch(offer, request, contribution = offer.costPerRider, notifyHost = false)
        applyAcceptedMatch(
            match,
            // The rider is the one who just tapped the button; notifying them of their own action
            // would be a pointless self-notification. Contrast offerSeatForRequest, where the rider
            // is not currently active and does need to be told.
            notifyRider = false,
            systemMessageText = "${user.displayName} reserved a seat instantly — no approval needed. " +
                "Use this chat to coordinate pickup.",
        )

        sendNotificationAlert(
            targetUserId = offer.hostId,
            title = "New Rider Joined Your Ride! 👋",
            message = "${user.displayName} reserved a seat on your ride from ${offer.origin} to ${offer.destination}.",
            type = "match",
        )
        return matches.value[match.id] ?: match
    }

    /**
     * A rider asking a specific host for a seat, subject to the cost cap and seat count.
     *
     * This exists because the screens used to invent a request id — `"req_joined_" + the last six
     * digits of the clock` — and hand it to [validateAndCreateMatch], which then created a ride
     * request document under that id. Two consequences, both real: those fabricated requests showed
     * up in the host feed as demand nobody had expressed, and the six-digit id repeats every ~17
     * minutes, so a second rider could land on the first rider's document and be denied by the
     * rules for writing a request that was not theirs.
     *
     * The backing request is created here instead, with a generated id and the offer's own route,
     * and is reused if the rider already has an open one for the same trip.
     */
    @Throws(Exception::class)
    suspend fun requestSeatOnOffer(offerId: String, contribution: Double): TripMatch {
        val user = requireUser()
        val offer = loadOffer(offerId)

        if (offer.hostId == user.id) {
            throw SplitCruiserException("You cannot request a seat on your own ride.")
        }
        if (offer.status != "active") {
            throw SplitCruiserException("This ride is no longer taking riders.")
        }
        val pending = matches.value.values.find {
            it.offerId == offerId && it.riderId == user.id && it.status != "declined"
        }
        if (pending != null) {
            throw SplitCruiserException("You already have a request on this ride.")
        }

        val request = findOrCreateBackingRequest(offer, user)
        return createMatch(offer, request, contribution)
    }

    /**
     * Finds or creates the "pending" backing [RideRequest] a match needs — shared by every path
     * that turns "this user wants a seat on this offer" into a real `trip_matches` document.
     *
     * "pending", not "active": this request exists to back a match, and an active one would be
     * advertised to every host as a rider looking for a ride they have already found.
     */
    private suspend fun findOrCreateBackingRequest(offer: TripOffer, user: User): RideRequest =
        requests.value.values.find {
            it.riderId == user.id && it.status == "pending" &&
                it.departureTime == offer.departureTime &&
                it.origin == offer.origin && it.destination == offer.destination
        } ?: RideRequest(
            id = newId("request"),
            riderId = user.id,
            riderName = user.displayName,
            riderRating = user.ratingAvg,
            origin = offer.origin,
            destination = offer.destination,
            originLat = offer.originLat,
            originLng = offer.originLng,
            destLat = offer.destLat,
            destLng = offer.destLng,
            originGeohash = offer.originGeohash,
            destGeohash = offer.destGeohash,
            seatsNeeded = 1,
            departureTime = offer.departureTime,
            status = "pending",
        ).also {
            firestore.setDocument("ride_requests", it.id, it, serializer<RideRequest>())
            requests.value = requests.value + (it.id to it)
        }

    /**
     * The host-side mirror of [findOrCreateBackingRequest]: the [TripOffer] a match needs when a
     * driver takes a rider's request without having posted a ride of their own.
     *
     * A driver *is* the ride. Making them publish an offer, then come back and pair it with the
     * request, is bookkeeping the app can do for them — so this mints the offer from the request's
     * own route.
     *
     * `"active"`, where the request mirror uses `"pending"`: [applyAcceptedMatch] flips an offer to
     * `"full"` the moment its last seat goes, and the seats here are sized to exactly this rider,
     * so the offer is full before any feed can project it — [FeedProjector] keeps only `"active"`
     * ones. `"pending"` would instead hide it from the host's *own* schedule, which reads plain
     * status strings.
     */
    private suspend fun findOrCreateBackingOffer(
        request: RideRequest,
        host: User,
        contribution: Double,
    ): TripOffer =
        offers.value.values.find {
            it.hostId == host.id && it.status == "active" &&
                it.departureTime == request.departureTime &&
                it.origin == request.origin && it.destination == request.destination &&
                it.seatsLeft >= request.seatsNeeded
        } ?: TripOffer(
            id = newId("offer"),
            hostId = host.id,
            hostName = host.displayName,
            hostRating = host.ratingAvg,
            origin = request.origin,
            destination = request.destination,
            originLat = request.originLat,
            originLng = request.originLng,
            destLat = request.destLat,
            destLng = request.destLng,
            originGeohash = request.originGeohash,
            destGeohash = request.destGeohash,
            departureTime = request.departureTime,
            totalSeats = request.seatsNeeded,
            seatsLeft = request.seatsNeeded,
            costPerRider = contribution,
            vehicleInfo = describeVehicle(vehicles.value[host.id]),
            // A women-only request must not quietly become a mixed ride.
            womenOnly = request.womenOnly,
            exitLocation = request.exitLocation,
            status = "active",
        ).also {
            firestore.setDocument("trip_offers", it.id, it, serializer<TripOffer>())
            offers.value = offers.value + (it.id to it)
        }

    /** "Silver Toyota Camry", or the same stand-in the post-offer form uses when none is set. */
    private fun describeVehicle(vehicle: Vehicle?): String {
        val described = listOf(vehicle?.color, vehicle?.make, vehicle?.model)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
        return described.ifEmpty { "Shared Sedan" }
    }

    /**
     * A driver taking a rider's request without having posted a ride.
     *
     * [offerSeatForRequest] needs an offer id the caller already owns, so a driver with nothing
     * posted had no id to pass and both screens showed "Post a ride first, then offer it here" — a
     * dead end that asked the driver to do the app's bookkeeping before it would let them help.
     * This mints the backing offer instead, exactly as [joinTripOfferDirect] mints a backing
     * request for a rider coming the other way.
     *
     * As in [offerSeatForRequest], creating the match *is* the host accepting it, so the seat
     * bookkeeping runs immediately rather than leaving a pending match only the host could approve.
     */
    @Throws(Exception::class)
    suspend fun acceptRideRequestDirect(requestId: String, contribution: Double): TripMatch {
        val host = requireUser()
        val request = loadRequest(requestId)

        if (request.riderId == host.id) {
            throw SplitCruiserException("That is your own ride request.")
        }
        if (request.status != "active" && request.status != "pending") {
            throw SplitCruiserException("That rider is no longer looking for a seat.")
        }
        val existing = matches.value.values.find {
            it.requestId == request.id && it.hostId == host.id && it.status != "declined"
        }
        if (existing != null) {
            throw SplitCruiserException("You have already offered this rider a seat.")
        }

        val offer = findOrCreateBackingOffer(request, host, contribution)
        val match = createMatch(offer, request, contribution, notifyHost = false)
        acceptMatch(match.id)
        return matches.value[match.id] ?: match
    }

    /**
     * The host side of the same handshake: agreeing to carry a rider who posted a request.
     *
     * [offerId] must be one of the caller's own rides. The screen used to invent this id too —
     * `"offer_quick_" + the clock` — which named no document at all, so the button failed with
     * "Trip offer not found" every single time it was pressed.
     *
     * Creating the match *is* the host accepting it, so the seat bookkeeping runs immediately
     * rather than leaving a pending match only the host could approve.
     */
    @Throws(Exception::class)
    suspend fun offerSeatForRequest(requestId: String, offerId: String, contribution: Double): TripMatch {
        val host = requireUser()
        val offer = loadOffer(offerId)

        if (offer.hostId != host.id) {
            throw SplitCruiserException("You can only offer a seat on a ride you are hosting.")
        }
        if (offer.status != "active") {
            throw SplitCruiserException("That ride of yours is no longer active.")
        }
        val request = loadRequest(requestId)
        if (request.riderId == host.id) {
            throw SplitCruiserException("That is your own ride request.")
        }
        if (request.status != "active" && request.status != "pending") {
            throw SplitCruiserException("That rider is no longer looking for a seat.")
        }

        val match = createMatch(offer, request, contribution, notifyHost = false)
        acceptMatch(match.id)
        return matches.value[match.id] ?: match
    }

    /** Requests a seat against an existing ride request. Returns the new match. */
    @Throws(Exception::class)
    suspend fun validateAndCreateMatch(
        offerId: String,
        requestId: String,
        contribution: Double,
    ): TripMatch {
        requireUser()
        return createMatch(loadOffer(offerId), loadRequest(requestId), contribution)
    }

    /** The checks and the write shared by every way of pairing an offer with a request. */
    private suspend fun createMatch(
        offer: TripOffer,
        request: RideRequest,
        contribution: Double,
        notifyHost: Boolean = true,
    ): TripMatch {
        val costLimit = offer.costPerRider * 2.0
        if (contribution > costLimit) {
            throw SplitCruiserException(
                "Rejected: a contribution of $contribution exceeds the 2x per-rider cost cap ($costLimit)."
            )
        }
        if (offer.seatsLeft < request.seatsNeeded) {
            throw SplitCruiserException(
                "Rejected: this ride has ${offer.seatsLeft} seats left, but you asked for ${request.seatsNeeded}."
            )
        }
        val duplicate = matches.value.values.find {
            it.offerId == offer.id && it.requestId == request.id && it.status != "declined"
        }
        if (duplicate != null) throw SplitCruiserException("Match already exists or is pending!")

        val match = TripMatch(
            id = newId("match"),
            offerId = offer.id,
            requestId = request.id,
            hostId = offer.hostId,
            riderId = request.riderId,
            riderName = request.riderName,
            riderRating = request.riderRating,
            contribution = contribution,
            status = "pending",
            timestamp = nowMs(),
            participants = listOf(offer.hostId, request.riderId),
        )
        firestore.setDocument("trip_matches", match.id, match, serializer<TripMatch>())
        matches.value = matches.value + (match.id to match)
        recomputeFeeds()

        if (notifyHost) {
            sendNotificationAlert(
                targetUserId = offer.hostId,
                title = "New Ride Request Received! 🚗",
                message = "${request.riderName} requested a seat on your ride from ${offer.origin} " +
                    "to ${offer.destination}.",
                type = "match",
            )
        }
        return match
    }

    @Throws(Exception::class)
    suspend fun createTripMatch(offerId: String, requestId: String): String {
        requireUser()
        val offer = loadOffer(offerId)
        val request = requests.value[requestId]
            ?: firestore.getDocument("ride_requests", requestId, serializer<RideRequest>())
            ?: throw SplitCruiserException("Request not found")

        if (offer.status != "active" && offer.status != "full") {
            throw SplitCruiserException("Offer is no longer active")
        }
        if (offer.seatsLeft < request.seatsNeeded) throw SplitCruiserException("Not enough seats available")
        if (request.status != "active") throw SplitCruiserException("Request is no longer active")
        if (matches.value.values.any { it.offerId == offerId && it.requestId == requestId }) {
            throw SplitCruiserException("Already matched")
        }

        val match = TripMatch(
            id = newId("match"),
            offerId = offerId,
            requestId = requestId,
            hostId = offer.hostId,
            riderId = request.riderId,
            riderName = request.riderName,
            riderRating = request.riderRating,
            contribution = offer.costPerRider * request.seatsNeeded,
            status = "pending",
            timestamp = nowMs(),
            participants = listOf(offer.hostId, request.riderId),
        )
        firestore.setDocument("trip_matches", match.id, match, serializer<TripMatch>())
        matches.value = matches.value + (match.id to match)
        recomputeFeeds()
        return match.id
    }

    @Throws(Exception::class)
    suspend fun acceptMatch(matchId: String) {
        val match = matches.value[matchId] ?: throw SplitCruiserException("Match not found.")
        applyAcceptedMatch(
            match,
            notifyRider = true,
            systemMessageText = "You're in! Chat here to sort out the pickup spot. Cash is settled in person.",
        )
    }

    /**
     * The bookkeeping and messaging shared by a host approving a rider's request
     * ([acceptMatch]/[offerSeatForRequest]) and a rider instant-reserving a seat
     * ([joinTripOfferDirect]) — both end with the same offer/request state, just reached from
     * opposite directions and needing different notification text.
     */
    private suspend fun applyAcceptedMatch(match: TripMatch, notifyRider: Boolean, systemMessageText: String) {
        val matchId = match.id
        val request = requests.value[match.requestId]
            ?: runCatching { firestore.getDocument("ride_requests", match.requestId, serializer<RideRequest>()) }
                .getOrNull()
        val offer = offers.value[match.offerId]
            ?: runCatching { firestore.getDocument("trip_offers", match.offerId, serializer<TripOffer>()) }
                .getOrNull()

        val accepted = match.copy(status = "accepted")
        firestore.updateFields(
            "trip_matches",
            matchId,
            buildFields("status" to stringValue("accepted")),
        )
        matches.value = matches.value + (matchId to accepted)

        if (request != null) {
            firestore.updateFields(
                "ride_requests",
                request.id,
                buildFields("status" to stringValue("matched")),
            )
            requests.value = requests.value + (request.id to request.copy(status = "matched"))
        }

        if (offer != null) {
            val seatsNeeded = request?.seatsNeeded ?: 1
            val seatsLeft = (offer.seatsLeft - seatsNeeded).coerceAtLeast(0)
            val updatedOffer = offer.copy(
                seatsLeft = seatsLeft,
                passengers = offer.passengers + match.riderId,
                passengerNames = offer.passengerNames + match.riderName,
                status = if (seatsLeft <= 0) "full" else offer.status,
            )
            firestore.updateFields(
                "trip_offers",
                offer.id,
                buildFields(
                    "passengers" to arrayOfStrings(updatedOffer.passengers),
                    "passengerNames" to arrayOfStrings(updatedOffer.passengerNames),
                    "seatsLeft" to integerValue(seatsLeft.toLong()),
                    "status" to stringValue(updatedOffer.status),
                ),
            )
            offers.value = offers.value + (offer.id to updatedOffer)
        }
        recomputeFeeds()

        sendSystemMessage(matchId, match.participants, systemMessageText)

        if (notifyRider) {
            sendNotificationAlert(
                targetUserId = match.riderId,
                title = "Ride Request Accepted! 🚗",
                message = "Your ride request from ${request?.origin ?: offer?.origin ?: "origin"} to " +
                    "${request?.destination ?: offer?.destination ?: "destination"} was accepted by " +
                    (offer?.hostName ?: _currentUser.value?.displayName ?: "the host") + ".",
                type = "ride_accepted",
            )
        }
    }

    @Throws(Exception::class)
    suspend fun declineMatch(matchId: String) {
        val match = matches.value[matchId] ?: throw SplitCruiserException("Match not found.")
        val offer = offers.value[match.offerId]
        val request = requests.value[match.requestId]

        // Only give the seat back if it had actually been taken.
        if (offer != null && request != null && match.status == "accepted") {
            val updatedOffer = offer.copy(
                seatsLeft = (offer.seatsLeft + request.seatsNeeded).coerceAtMost(offer.totalSeats),
                passengers = offer.passengers - match.riderId,
                passengerNames = offer.passengerNames - match.riderName,
                status = if (offer.status == "full") "active" else offer.status,
            )
            firestore.updateFields(
                "trip_offers",
                offer.id,
                buildFields(
                    "passengers" to arrayOfStrings(updatedOffer.passengers),
                    "passengerNames" to arrayOfStrings(updatedOffer.passengerNames),
                    "seatsLeft" to integerValue(updatedOffer.seatsLeft.toLong()),
                    "status" to stringValue(updatedOffer.status),
                ),
            )
            offers.value = offers.value + (offer.id to updatedOffer)
        }

        firestore.updateFields("trip_matches", matchId, buildFields("status" to stringValue("declined")))
        matches.value = matches.value + (matchId to match.copy(status = "declined"))
        recomputeFeeds()
    }

    @Throws(Exception::class)
    suspend fun completeTrip(matchId: String) {
        val match = matches.value[matchId] ?: throw SplitCruiserException("Match not found.")
        firestore.updateFields("trip_matches", matchId, buildFields("status" to stringValue("completed")))
        matches.value = matches.value + (matchId to match.copy(status = "completed"))

        val offer = offers.value[match.offerId]
        if (offer != null) {
            firestore.updateFields(
                "trip_offers",
                offer.id,
                buildFields("status" to stringValue("completed")),
            )
            offers.value = offers.value + (offer.id to offer.copy(status = "completed"))
        }
        recomputeFeeds()
    }

    @Throws(Exception::class)
    suspend fun cancelMatch(matchId: String, reason: String) {
        val match = matches.value[matchId] ?: throw SplitCruiserException("Match not found.")
        firestore.updateFields("trip_matches", matchId, buildFields("status" to stringValue("cancelled")))
        matches.value = matches.value + (matchId to match.copy(status = "cancelled"))
        recomputeFeeds()

        val other = if (_currentUser.value?.id == match.hostId) match.riderId else match.hostId
        sendNotificationAlert(
            targetUserId = other,
            title = "Ride Cancelled",
            message = if (reason.isBlank()) "A ride you were part of was cancelled." else reason,
            type = "match",
        )
    }

    /** Overload without a reason — Kotlin default arguments do not reach Swift. */
    @Throws(Exception::class)
    suspend fun cancelMatch(matchId: String) = cancelMatch(matchId, "")

    fun getTripMatchById(matchId: String): TripMatch? = matches.value[matchId]

    fun getActiveMatches(): List<TripMatch> =
        matches.value.values.filter { it.status == "accepted" || it.status == "pending" }

    /** Hosted and joined rides for the signed-in user. A named type, not a Pair, so Swift can read it. */
    @Throws(Exception::class)
    suspend fun fetchMyTrips(): MyTrips {
        val uid = tokens.uid ?: throw SplitCruiserException("You need to be logged in.")
        val hostedRaw = firestore.runQuery(
            StructuredQuery(
                collection = "trip_offers",
                filters = listOf(FieldFilter("hostId", FilterOp.Equal, stringValue(uid))),
                orderBy = listOf(OrderBy("departureTime", descending = true)),
                limit = 100,
            ),
            serializer<TripOffer>(),
        )
        val joinedRaw = firestore.runQuery(
            StructuredQuery(
                collection = "trip_offers",
                filters = listOf(FieldFilter("passengers", FilterOp.ArrayContains, stringValue(uid))),
                orderBy = listOf(OrderBy("departureTime", descending = true)),
                limit = 100,
            ),
            serializer<TripOffer>(),
        )
        val hosted = hostedRaw.map { closeIfExpired(it) }
        val joined = joinedRaw.map { closeIfExpired(it) }
        offers.value = offers.value + (hosted + joined).associateBy { it.id }
        recomputeFeeds()
        return MyTrips(hosted, joined)
    }

    // --- Chat -------------------------------------------------------------------------------

    /**
     * Messages for one conversation.
     *
     * Derived from the cache rather than launching a collector per call, which is what the old
     * implementation did — it leaked a coroutine on every invocation and never cancelled them.
     */
    fun getChatMessages(matchId: String): kotlinx.coroutines.flow.Flow<List<Message>> =
        messages.map { all -> all.values.filter { it.matchId == matchId }.sortedBy { it.timestamp } }
            .distinctUntilChanged()

    @Throws(Exception::class)
    suspend fun sendMessage(matchId: String, text: String) =
        sendMessage(matchId, text, MessageType.TEXT, "", "")

    /**
     * Sends a message of a given [MessageType].
     *
     * The pickup spot and time are their own fields rather than being encoded into [text] behind
     * a `[PROPOSAL]` prefix, which is how the chat screen used to tell a proposal apart from an
     * ordinary message. [text] is still filled in with a readable summary, so a proposal degrades
     * to a sensible sentence in a notification or an older client.
     */
    @Throws(Exception::class)
    suspend fun sendMessage(
        matchId: String,
        text: String,
        type: String,
        pickupSpot: String,
        pickupTime: String,
    ) {
        val user = requireUser()
        val match = matches.value[matchId]
        val participants = match?.participants?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(match?.hostId, match?.riderId).ifEmpty { listOf(user.id) }

        val message = Message(
            id = newId("msg"),
            matchId = matchId,
            senderId = user.id,
            senderName = user.displayName,
            text = text,
            timestamp = nowMs(),
            participants = participants,
            type = type,
            pickupSpot = pickupSpot,
            pickupTime = pickupTime,
        )
        firestore.setDocument("messages", message.id, message, serializer<Message>())
        messages.value = messages.value + (message.id to message)
        publishMessages()

        val recipient = participants.firstOrNull { it != user.id }.orEmpty()
        if (recipient.isNotEmpty()) {
            sendNotificationAlert(
                targetUserId = recipient,
                title = "New Message from ${user.displayName} 💬",
                message = text,
                type = "new_message",
            )
        }
    }

    private suspend fun sendSystemMessage(matchId: String, participants: List<String>, text: String) {
        val message = Message(
            id = newId("msg_sys"),
            matchId = matchId,
            senderId = _currentUser.value?.id.orEmpty(),
            senderName = "Split Cruiser",
            text = text,
            timestamp = nowMs(),
            participants = participants,
            isSystem = true,
        )
        runCatching {
            firestore.setDocument("messages", message.id, message, serializer<Message>())
        }.onFailure { logWarn(LOG_TAG, "Could not post the system message", it) }
        messages.value = messages.value + (message.id to message)
        publishMessages()
    }

    // --- Ratings, blocks, notifications ------------------------------------------------------

    @Throws(Exception::class)
    suspend fun submitRating(toUserId: String, ratingValue: Float, comment: String) {
        val user = requireUser()
        val rating = Rating(
            id = newId("rating"),
            fromUserId = user.id,
            toUserId = toUserId,
            rating = ratingValue,
            comment = comment,
            timestamp = nowMs(),
        )
        firestore.setDocument("ratings", rating.id, rating, serializer<Rating>())
        recomputeUserRating(toUserId)
    }

    private suspend fun recomputeUserRating(userId: String) {
        val ratings = firestore.runQuery(
            StructuredQuery(
                collection = "ratings",
                filters = listOf(FieldFilter("toUserId", FilterOp.Equal, stringValue(userId))),
                limit = 500,
            ),
            serializer<Rating>(),
        )
        if (ratings.isEmpty()) return
        val average = (ratings.sumOf { it.rating.toDouble() } / ratings.size).toFloat()

        // The rules allow a non-owner to touch only these two fields. Aggregating on the client is
        // spoofable; a Cloud Function with the Admin SDK is the real fix and is noted as a follow-up.
        firestore.updateFields(
            "users",
            userId,
            buildFields(
                "ratingAvg" to doubleValue(average.toDouble()),
                "ratingCount" to integerValue(ratings.size.toLong()),
            ),
        )
        val cached = users.value[userId]
        if (cached != null) {
            val updated = cached.copy(ratingAvg = average, ratingCount = ratings.size)
            users.value = users.value + (userId to updated)
            if (_currentUser.value?.id == userId) _currentUser.value = updated
        }
    }

    @Throws(Exception::class)
    suspend fun recordNoShow(userId: String) {
        val user = users.value[userId] ?: fetchUserProfile(userId)
        firestore.updateFields(
            "users",
            userId,
            buildFields("noShowCount" to integerValue((user.noShowCount + 1).toLong())),
        )
        val updated = user.copy(noShowCount = user.noShowCount + 1)
        users.value = users.value + (userId to updated)
        if (_currentUser.value?.id == userId) _currentUser.value = updated
    }

    @Throws(Exception::class)
    suspend fun blockUser(blockedUserId: String) {
        val user = requireUser()
        if (user.id == blockedUserId) throw SplitCruiserException("You cannot block yourself.")

        val block = Block(id = blockedUserId, userId = user.id, blockedUserId = blockedUserId)
        // Blocks live under the user document. A top-level `blocks` collection was never declared in
        // the security rules, so the old code's writes would have been denied outright.
        firestore.setDocument("users/${user.id}/blockedUsers", blockedUserId, block, serializer<Block>())
        blocks.value = blocks.value + (block.id to block)
        recomputeFeeds()
    }

    @Throws(Exception::class)
    suspend fun unblockUser(blockedUserId: String) {
        val user = requireUser()
        firestore.deleteDocument("users/${user.id}/blockedUsers", blockedUserId)
        blocks.value = blocks.value - blockedUserId
        recomputeFeeds()
    }

    fun getBlockedUsers(): List<User> {
        val uid = _currentUser.value?.id ?: return emptyList()
        val blockedIds = blocks.value.values.filter { it.userId == uid }.map { it.blockedUserId }.toSet()
        return users.value.values.filter { it.id in blockedIds }
    }

    private suspend fun refreshBlocks() {
        val uid = tokens.uid ?: return
        val loaded = firestore.listDocuments("users/$uid/blockedUsers", serializer<Block>(), pageSize = 200)
        blocks.value = loaded.associateBy { it.id }
        loadMissingUsers(loaded.map { it.blockedUserId }.toSet())
        recomputeFeeds()
    }

    @Throws(Exception::class)
    suspend fun sendNotificationAlert(
        targetUserId: String,
        title: String,
        message: String,
        type: String,
    ) {
        if (targetUserId.isEmpty()) return
        val alert = NotificationAlert(
            id = newId("notif"),
            userId = targetUserId,
            title = title,
            message = message,
            type = type,
            timestamp = nowMs(),
            isRead = false,
        )
        runCatching {
            firestore.setDocument("notifications", alert.id, alert, serializer<NotificationAlert>())
        }.onFailure { logWarn(LOG_TAG, "Could not deliver a notification to $targetUserId", it) }

        if (targetUserId == _currentUser.value?.id) {
            _notifications.value = listOf(alert) + _notifications.value
        }
    }

    @Throws(Exception::class)
    suspend fun markNotificationAsRead(id: String) {
        firestore.updateFields("notifications", id, buildFields("isRead" to booleanValue(true)))
        _notifications.value = _notifications.value.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    @Throws(Exception::class)
    suspend fun clearNotifications() {
        val current = _notifications.value
        _notifications.value = emptyList()
        current.forEach { alert ->
            runCatching { firestore.deleteDocument("notifications", alert.id) }
        }
    }

    // --- Settings ---------------------------------------------------------------------------

    @Throws(Exception::class)
    suspend fun toggleWomenOnlyFilter(enabled: Boolean) =
        updateOwnFlag("isWomenOnlyFilterEnabled", enabled) { it.copy(isWomenOnlyFilterEnabled = enabled) }

    @Throws(Exception::class)
    suspend fun toggleEmailNotifications(enabled: Boolean) =
        updateOwnFlag("emailNotificationsEnabled", enabled) { it.copy(emailNotificationsEnabled = enabled) }

    @Throws(Exception::class)
    suspend fun togglePushNotifications(enabled: Boolean) =
        updateOwnFlag("pushNotificationsEnabled", enabled) { it.copy(pushNotificationsEnabled = enabled) }

    private suspend fun updateOwnFlag(field: String, enabled: Boolean, apply: (User) -> User) {
        val user = _currentUser.value ?: return
        val updated = apply(user)
        adoptUser(updated)
        runCatching {
            firestore.updateFields("users", user.id, buildFields(field to booleanValue(enabled)))
        }.onFailure { logWarn(LOG_TAG, "Could not save the $field setting", it) }
    }

    // --- Read helpers used by the UI ---------------------------------------------------------

    fun getTripOfferById(offerId: String): TripOffer? = offers.value[offerId]

    fun getRideRequestById(requestId: String): RideRequest? = requests.value[requestId]

    /**
     * [getTripOfferById], with a network fallback for the cold-cache case — a detail screen opened
     * straight from a deep link, or a request/offer that `FeedProjector` deliberately excludes from
     * the feeds it derives its cache from (the viewer's own ride, a past ride, a request that was
     * just matched). Populates the cache on success, so a second call is instant.
     */
    @Throws(Exception::class)
    suspend fun fetchTripOffer(offerId: String): TripOffer {
        val fetched = offers.value[offerId]
            ?: firestore.getDocument("trip_offers", offerId, serializer<TripOffer>())
            ?: throw SplitCruiserException("Trip offer not found.")
        val offer = closeIfExpired(fetched)
        offers.value = offers.value + (offerId to offer)
        return offer
    }

    /** [fetchTripOffer]'s counterpart for ride requests. */
    @Throws(Exception::class)
    suspend fun fetchRideRequest(requestId: String): RideRequest {
        val fetched = requests.value[requestId]
            ?: runCatching { firestore.getDocument("ride_requests", requestId, serializer<RideRequest>()) }
                .getOrNull()
            ?: throw SplitCruiserException("That ride request no longer exists.")
        val request = closeIfExpired(fetched)
        requests.value = requests.value + (requestId to request)
        return request
    }

    /**
     * Lazy fallback for the scheduled auto-close: a ride nobody revisits stays stale until the
     * Cloud Function catches it, but a ride someone actually opens gets the correct status right
     * away. Only `active`/`full` flip, and only once departure has passed — `completed` and
     * `cancelled` are deliberate host/rider outcomes and are never overwritten.
     */
    private suspend fun closeIfExpired(offer: TripOffer): TripOffer {
        if (offer.status !in AUTO_CLOSEABLE_OFFER_STATUSES || offer.departureTime > nowMs()) return offer
        runCatching {
            firestore.updateFields("trip_offers", offer.id, buildFields("status" to stringValue("closed")))
        }.onFailure { logWarn(LOG_TAG, "Could not auto-close expired trip offer ${offer.id}", it) }
        return offer.copy(status = "closed")
    }

    private suspend fun closeIfExpired(request: RideRequest): RideRequest {
        if (request.status !in AUTO_CLOSEABLE_REQUEST_STATUSES || request.departureTime > nowMs()) return request
        runCatching {
            firestore.updateFields("ride_requests", request.id, buildFields("status" to stringValue("closed")))
        }.onFailure { logWarn(LOG_TAG, "Could not auto-close expired ride request ${request.id}", it) }
        return request.copy(status = "closed")
    }

    fun getHostedRides(userId: String): List<TripOffer> =
        offers.value.values.filter { it.hostId == userId }.sortedByDescending { it.departureTime }

    fun getActiveRides(): List<TripOffer> = _activeOffers.value

    fun getPassengerRequests(riderId: String): List<RideRequest> =
        requests.value.values.filter { it.riderId == riderId }

    fun getActiveRequests(): List<RideRequest> = _activeRequests.value

    fun calculateCostSplit(totalCost: Double, riders: Int): Double =
        if (riders <= 0) totalCost else totalCost / riders

    /**
     * What to prefill the contribution field with when a driver accepts a request directly.
     *
     * A [RideRequest] carries no cost — riders say where and when, not what they will pay — so
     * without a suggestion the driver would be typing into an empty box and guessing. Both
     * platforms call this so they propose the same number for the same trip.
     *
     * Returns 0.0 rather than throwing when the route cannot be resolved: a missing suggestion
     * should leave the field empty, not block the accept.
     */
    @Throws(Exception::class)
    suspend fun suggestedContribution(request: RideRequest): Double {
        val routed = OsrmRouteService.getRouteOrNull(
            request.originLat,
            request.originLng,
            request.destLat,
            request.destLng,
        )?.distanceMiles
        // The same straight-line fallback the route estimate card uses when OSRM is unreachable.
        val miles = routed ?: GeoUtils.distanceInMiles(
            request.originLat,
            request.originLng,
            request.destLat,
            request.destLng,
        )
        if (miles <= 0.0) return 0.0

        val estimate = (miles * SUGGESTED_COST_PER_MILE).coerceAtLeast(MIN_SUGGESTED_CONTRIBUTION)
        // To the nearest 50c, so the app suggests a number someone would say out loud.
        return round(estimate * 2.0) / 2.0
    }

    fun findMatchingOffers(request: RideRequest): List<TripOffer> {
        val now = nowMs()
        return offers.value.values.filter { offer ->
            offer.status == "active" &&
                offer.seatsLeft >= request.seatsNeeded &&
                offer.departureTime > now &&
                looselyMatches(offer.origin, request.origin) &&
                looselyMatches(offer.destination, request.destination)
        }
    }

    @Throws(Exception::class)
    suspend fun getMatchDetails(matchId: String): MatchDetails {
        val match = matches.value[matchId]
            ?: firestore.getDocument("trip_matches", matchId, serializer<TripMatch>())
            ?: throw SplitCruiserException("Match not found.")
        val offer = offers.value[match.offerId]
            ?: firestore.getDocument("trip_offers", match.offerId, serializer<TripOffer>())
            ?: throw SplitCruiserException("The ride behind this match no longer exists.")
        val request = requests.value[match.requestId]
            ?: firestore.getDocument("ride_requests", match.requestId, serializer<RideRequest>())
            ?: throw SplitCruiserException("The request behind this match no longer exists.")
        return MatchDetails(
            match = match,
            offer = offer,
            request = request,
            hostProfile = users.value[match.hostId] ?: runCatching { fetchUserProfile(match.hostId) }.getOrNull(),
            riderProfile = users.value[match.riderId] ?: runCatching { fetchUserProfile(match.riderId) }.getOrNull(),
        )
    }

    // --- Validation -------------------------------------------------------------------------

    private fun validateTripOfferOrThrow(offer: TripOffer) {
        requireValid(offer.origin.trim().isNotEmpty() && offer.destination.trim().isNotEmpty()) {
            "Origin and destination are required."
        }
        requireValid(
            offer.originLat != 0.0 && offer.originLng != 0.0 &&
                offer.destLat != 0.0 && offer.destLng != 0.0
        ) { "Valid pickup and dropoff locations required." }
        requireValid(offer.departureTime > nowMs()) { "Departure time must be in the future." }
        requireValid(offer.totalSeats in 1..8) { "Total seats must be between 1 and 8." }
        requireValid(offer.costPerRider >= 0.0) { "Cost per rider cannot be negative." }
    }

    private fun validateRideRequestOrThrow(request: RideRequest) {
        requireValid(request.origin.trim().isNotEmpty() && request.destination.trim().isNotEmpty()) {
            "Pickup and dropoff locations are required."
        }
        requireValid(
            request.originLat != 0.0 && request.originLng != 0.0 &&
                request.destLat != 0.0 && request.destLng != 0.0
        ) { "Valid pickup and dropoff coordinates required." }
        requireValid(request.departureTime > nowMs()) { "Departure time must be in the future." }
        requireValid(request.seatsNeeded in 1..8) { "Seats needed must be between 1 and 8." }
    }

    // --- Swift observation --------------------------------------------------------------------
    //
    // Members rather than extensions, so Swift calls `repository.observeActiveOffers { ... }`
    // instead of a synthetic file class. Android ignores these and collects the StateFlows.

    fun observeCurrentUser(onChange: (User?) -> Unit): FlowSubscription =
        currentUser.subscribeOnMain(onChange)

    fun observeActiveOffers(onChange: (List<TripOffer>) -> Unit): FlowSubscription =
        activeOffers.subscribeOnMain(onChange)

    fun observeActiveRequests(onChange: (List<RideRequest>) -> Unit): FlowSubscription =
        activeRequests.subscribeOnMain(onChange)

    fun observeMyRideRequests(onChange: (List<RideRequest>) -> Unit): FlowSubscription =
        myRideRequests.subscribeOnMain(onChange)

    fun observeUserMatches(onChange: (List<TripMatch>) -> Unit): FlowSubscription =
        userMatches.subscribeOnMain(onChange)

    fun observeNotifications(onChange: (List<NotificationAlert>) -> Unit): FlowSubscription =
        notifications.subscribeOnMain(onChange)

    fun observeConnection(onChange: (Boolean) -> Unit): FlowSubscription =
        isConnected.subscribeOnMain(onChange)

    fun observeChat(matchId: String, onChange: (List<Message>) -> Unit): FlowSubscription =
        getChatMessages(matchId).subscribeOnMain(onChange)

    /** What onboarding stored, so an iOS ride request can prefill its pickup the way Android's does. */
    fun observeContactDetails(onChange: (ContactDetails?) -> Unit): FlowSubscription =
        contactDetails.subscribeOnMain(onChange)

    // --- Internals --------------------------------------------------------------------------

    private suspend fun loadOffer(offerId: String): TripOffer = fetchTripOffer(offerId)

    private suspend fun loadRequest(requestId: String): RideRequest = fetchRideRequest(requestId)

    private fun adoptUser(user: User) {
        users.value = users.value + (user.id to user)
        _currentUser.value = user
        recomputeFeeds()
    }

    private fun requireUser(): User =
        _currentUser.value ?: throw SplitCruiserException("Please log in first.", code = "UNAUTHENTICATED")

    private fun requireConfigured() {
        if (!config.isConfigured) {
            throw SplitCruiserException(
                "This build has no Firebase configuration, so it cannot reach the backend.",
                code = "NOT_CONFIGURED",
            )
        }
    }

    private inline fun requireValid(condition: Boolean, message: () -> String) {
        if (!condition) throw SplitCruiserException(message())
    }

    private suspend fun notifyRidersOfMatchingOffer(offer: TripOffer) {
        val candidates = requests.value.values.filter { request ->
            request.status == "active" &&
                request.riderId != offer.hostId &&
                request.departureTime > nowMs() &&
                looselyMatches(request.origin, offer.origin) &&
                looselyMatches(request.destination, offer.destination)
        }
        candidates.map { it.riderId }.distinct().forEach { riderId ->
            val rider = users.value[riderId]
            if (rider?.pushNotificationsEnabled == true || rider?.emailNotificationsEnabled == true) {
                sendNotificationAlert(
                    targetUserId = riderId,
                    title = "New Trip Posted 🚗",
                    message = "${offer.hostName} just posted a trip from ${offer.origin} to " +
                        "${offer.destination} matching your active ride request.",
                    type = if (rider.pushNotificationsEnabled) "push" else "email",
                )
            }
        }
    }

    private suspend fun notifyHostsOfMatchingRequest(request: RideRequest) {
        findMatchingOffers(request).forEach { offer ->
            val host = users.value[offer.hostId] ?: return@forEach
            if (host.pushNotificationsEnabled || host.emailNotificationsEnabled) {
                sendNotificationAlert(
                    targetUserId = offer.hostId,
                    title = "Rider Looking for Your Route! 👥",
                    message = "${request.riderName} needs ${request.seatsNeeded} seat(s) from " +
                        "${request.origin} to ${request.destination}.",
                    type = if (host.pushNotificationsEnabled) "push" else "email",
                )
            }
        }
    }

    /** The loose origin/destination comparison the matching notifications have always used. */
    private fun looselyMatches(a: String, b: String): Boolean {
        val left = a.lowercase().trim()
        val right = b.lowercase().trim()
        if (left.isEmpty() || right.isEmpty()) return false
        return left == right || left.contains(right) || right.contains(left)
    }

    private companion object {
        /** The single document under `users/{uid}/private` that onboarding writes. */
        const val CONTACT_DOC = "profile"

        /** System-set statuses [closeIfExpired] may overwrite once departure has passed. */
        val AUTO_CLOSEABLE_OFFER_STATUSES = setOf("active", "full")
        val AUTO_CLOSEABLE_REQUEST_STATUSES = setOf("active")

        /**
         * Gas and wear per mile, for [suggestedContribution]. A starting point the driver types
         * over, not a fare — the product is a cost split settled in cash, in person.
         */
        const val SUGGESTED_COST_PER_MILE = 0.35

        /** Below this a split is not worth the arithmetic. */
        const val MIN_SUGGESTED_CONTRIBUTION = 3.0
    }
}

/** Named rather than a Pair: `Pair` exports to Swift as an opaque box that has to be force-cast. */
data class MyTrips(
    val hosted: List<TripOffer>,
    val joined: List<TripOffer>,
)
