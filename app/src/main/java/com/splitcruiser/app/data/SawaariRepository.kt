package com.splitcruiser.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.splitcruiser.app.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.gms.tasks.Task
import java.io.File
import java.util.UUID

class SawaariRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // --- Firebase Availability Status ---
    var isFirebaseEnabled = false
        private set

    private var firebaseAuth: FirebaseAuth? = null
    private var firebaseFirestore: FirebaseFirestore? = null
    private var firebaseStorage: FirebaseStorage? = null

    // --- Local Fallback State (Simulates Firestore Collections) ---
    private val _users = MutableStateFlow<Map<String, User>>(emptyMap())
    private val _invites = MutableStateFlow<Map<String, Invite>>(emptyMap())
    private val _communities = MutableStateFlow<Map<String, Community>>(emptyMap())
    private val _tripOffers = MutableStateFlow<Map<String, TripOffer>>(emptyMap())
    private val _rideRequests = MutableStateFlow<Map<String, RideRequest>>(emptyMap())
    private val _tripMatches = MutableStateFlow<Map<String, TripMatch>>(emptyMap())
    private val _messages = MutableStateFlow<Map<String, Message>>(emptyMap())
    private val _ratings = MutableStateFlow<Map<String, Rating>>(emptyMap())
    private val _blocks = MutableStateFlow<Map<String, Block>>(emptyMap())
    private val _credentials = MutableStateFlow<Map<String, LocalCredential>>(emptyMap())

    // --- Real-time Connection State ---
    private val _isConnected = MutableStateFlow<Boolean>(isFirebaseEnabled)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long>(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private var listenerRegistrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

    // --- Public Reactive Streams ---
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _activeOffers = MutableStateFlow<List<TripOffer>>(emptyList())
    val activeOffers: StateFlow<List<TripOffer>> = _activeOffers.asStateFlow()

    private val _activeRequests = MutableStateFlow<List<RideRequest>>(emptyList())
    val activeRequests: StateFlow<List<RideRequest>> = _activeRequests.asStateFlow()

    private val _myRideRequests = MutableStateFlow<List<RideRequest>>(emptyList())
    val myRideRequests: StateFlow<List<RideRequest>> = _myRideRequests.asStateFlow()

    private val _userMatches = MutableStateFlow<List<TripMatch>>(emptyList())
    val userMatches: StateFlow<List<TripMatch>> = _userMatches.asStateFlow()

    private val _allCommunities = MutableStateFlow<List<Community>>(emptyList())
    val allCommunities: StateFlow<List<Community>> = _allCommunities.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationAlert>>(emptyList())
    val notifications: StateFlow<List<NotificationAlert>> = _notifications.asStateFlow()

    private val _allMessages = MutableStateFlow<List<Message>>(emptyList())
    val allMessages: StateFlow<List<Message>> = _allMessages.asStateFlow()

    // --- Adapters for local storage ---
    private val userListAdapter = moshi.adapter<List<User>>(Types.newParameterizedType(List::class.java, User::class.java))
    private val inviteListAdapter = moshi.adapter<List<Invite>>(Types.newParameterizedType(List::class.java, Invite::class.java))
    private val communityListAdapter = moshi.adapter<List<Community>>(Types.newParameterizedType(List::class.java, Community::class.java))
    private val tripOfferListAdapter = moshi.adapter<List<TripOffer>>(Types.newParameterizedType(List::class.java, TripOffer::class.java))
    private val rideRequestListAdapter = moshi.adapter<List<RideRequest>>(Types.newParameterizedType(List::class.java, RideRequest::class.java))
    private val tripMatchListAdapter = moshi.adapter<List<TripMatch>>(Types.newParameterizedType(List::class.java, TripMatch::class.java))
    private val messageListAdapter = moshi.adapter<List<Message>>(Types.newParameterizedType(List::class.java, Message::class.java))
    private val ratingListAdapter = moshi.adapter<List<Rating>>(Types.newParameterizedType(List::class.java, Rating::class.java))
    private val blockListAdapter = moshi.adapter<List<Block>>(Types.newParameterizedType(List::class.java, Block::class.java))
    private val localCredentialAdapter = moshi.adapter<List<LocalCredential>>(Types.newParameterizedType(List::class.java, LocalCredential::class.java))
    private val notificationListAdapter = moshi.adapter<List<NotificationAlert>>(Types.newParameterizedType(List::class.java, NotificationAlert::class.java))

    init {
        // Safe Firebase Initialization
        try {
            val apps = FirebaseApp.getApps(context)
            val app = if (apps.isEmpty()) {
                val apiKey = BuildConfig.FIREBASE_API_KEY
                val appId = BuildConfig.FIREBASE_APP_ID
                val projectId = BuildConfig.FIREBASE_PROJECT_ID
                val storageBucket = BuildConfig.FIREBASE_STORAGE_BUCKET

                if (apiKey.isNotBlank() && !apiKey.contains("PLACEHOLDER") &&
                    appId.isNotBlank() && !appId.contains("PLACEHOLDER") &&
                    projectId.isNotBlank() && !projectId.contains("PLACEHOLDER")) {
                    val options = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(appId)
                        .setProjectId(projectId)
                        .apply {
                            if (storageBucket.isNotBlank() && !storageBucket.contains("PLACEHOLDER")) {
                                setStorageBucket(storageBucket)
                            }
                        }
                        .build()
                    FirebaseApp.initializeApp(context, options)
                } else {
                    FirebaseApp.initializeApp(context)
                }
            } else {
                apps.first()
            }
            if (app != null) {
                firebaseAuth = FirebaseAuth.getInstance()
                firebaseFirestore = FirebaseFirestore.getInstance()
                firebaseStorage = FirebaseStorage.getInstance()
                isFirebaseEnabled = true
                Log.d("SawaariShare", "Firebase successfully initialized.")
            }
        } catch (e: Exception) {
            isFirebaseEnabled = false
            Log.w("SawaariShare", "Firebase initialization bypassed (Using persistent local storage). Reason: ${e.message}")
        }

        // Always load local collection backups for seamless fallback and pre-populating
        loadLocalDatabase()
        prepopulateDefaultDataIfNeeded()
        observeDataChanges()
    }

    // --- Data Persistence Helpers ---
    private fun loadLocalDatabase() {
        _users.value = loadList<User>("users.json", userListAdapter).associateBy { it.id }
        _invites.value = loadList<Invite>("invites.json", inviteListAdapter).associateBy { it.code }
        _communities.value = loadList<Community>("communities.json", communityListAdapter).associateBy { it.id }
        _tripOffers.value = loadList<TripOffer>("trip_offers.json", tripOfferListAdapter).associateBy { it.id }
        _rideRequests.value = loadList<RideRequest>("ride_requests.json", rideRequestListAdapter).associateBy { it.id }
        _tripMatches.value = loadList<TripMatch>("trip_matches.json", tripMatchListAdapter).associateBy { it.id }
        _messages.value = loadList<Message>("messages.json", messageListAdapter).associateBy { it.id }
        _allMessages.value = _messages.value.values.toList()
        _ratings.value = loadList<Rating>("ratings.json", ratingListAdapter).associateBy { it.id }
        _blocks.value = loadList<Block>("blocks.json", blockListAdapter).associateBy { it.id }
        _credentials.value = loadList<LocalCredential>("credentials.json", localCredentialAdapter).associateBy { it.email.lowercase() }
        _notifications.value = loadList<NotificationAlert>("notifications.json", notificationListAdapter)
    }

    private fun <T> loadList(filename: String, adapter: com.squareup.moshi.JsonAdapter<List<T>>): List<T> {
        val file = File(context.filesDir, filename)
        if (!file.exists()) return emptyList()
        return try {
            adapter.fromJson(file.readText()) ?: emptyList()
        } catch (e: Exception) {
            Log.e("SawaariShare", "Error parsing $filename: ${e.message}")
            emptyList()
        }
    }

    private fun <T> saveList(filename: String, list: List<T>, adapter: com.squareup.moshi.JsonAdapter<List<T>>) {
        scope.launch {
            try {
                val file = File(context.filesDir, filename)
                file.writeText(adapter.toJson(list))
            } catch (e: Exception) {
                Log.e("SawaariShare", "Error saving $filename: ${e.message}")
            }
        }
    }

    private fun prepopulateDefaultDataIfNeeded() {
        // Pre-populate standard communities
        if (_communities.value.isEmpty()) {
            val list = listOf(
                Community("neu_boston", "Northeastern University", "Boston, MA"),
                Community("asu_tempe", "Arizona State University", "Tempe, AZ"),
                Community("utd_dallas", "University of Texas at Dallas", "Richardson, TX"),
                Community("usc_la", "University of Southern California", "Los Angeles, CA"),
                Community("iub_bloom", "Indiana University Bloomington", "Bloomington, IN")
            )
            _communities.value = list.associateBy { it.id }
            saveList("communities.json", list, communityListAdapter)
        }

        // Pre-populate standard, usable invite codes
        if (_invites.value.isEmpty()) {
            val list = listOf(
                Invite("SAWAARISHARE", false, "system", ""),
                Invite("INDIANSTUDENTS", false, "system", ""),
                Invite("WELCOME2026", false, "system", ""),
                Invite("VOUCHEDCODE", false, "system", "")
            )
            _invites.value = list.associateBy { it.code }
            saveList("invites.json", list, inviteListAdapter)
        }

        _allCommunities.value = _communities.value.values.toList()
    }

    private fun observeDataChanges() {
        // Trigger updates in UI Flows based on active states, exclusion of blocked users, etc.
        scope.launch {
            _currentUser.collect { user ->
                updateFeeds(user)
            }
        }

        // Periodic background check/timer to automatically filter out expired rides in real-time
        scope.launch {
            while (true) {
                delay(10000) // Run every 10 seconds to auto-hide past rides
                updateFeeds(_currentUser.value)
            }
        }

        if (isFirebaseEnabled && firebaseFirestore != null) {
            setupRealtimeListeners()
        }
    }

    private fun setupRealtimeListeners() {
        // Enable offline persistence for seamless sync
        try {
            // The class is FirebaseFirestoreSettings (not FirestoreSettings), and
            // setPersistenceEnabled is superseded by setLocalCacheSettings in the modern SDK.
            firebaseFirestore?.firestoreSettings =
                com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build()
                    )
                    .build()
        } catch (e: Exception) {
            Log.w("SawaariShare", "Could not enable Firestore persistence: ${e.message}")
        }

        // Listen to trip_offers in real-time
        try {
            val offersListener = firebaseFirestore?.collection("trip_offers")?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SawaariShare", "Listen to trip_offers failed: ${error.message}")
                    _isConnected.value = false
                    return@addSnapshotListener
                }
                _isConnected.value = true
                if (snapshot != null) {
                    val offers = snapshot.documents.mapNotNull { doc -> doc.toTripOfferSafe() }
                    if (offers.isNotEmpty() || snapshot.isEmpty) {
                        val updatedOffersMap = _tripOffers.value.toMutableMap()
                        offers.forEach { offer ->
                            updatedOffersMap[offer.id] = offer
                        }
                        _tripOffers.value = updatedOffersMap
                        updateFeeds(_currentUser.value)
                        saveList("trip_offers.json", _tripOffers.value.values.toList(), tripOfferListAdapter)
                        _lastSyncTime.value = System.currentTimeMillis()
                    }
                }
            }
            if (offersListener != null) listenerRegistrations.add(offersListener)
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to register trip_offers listener: ${e.message}")
        }

        // Listen to ride_requests in real-time
        try {
            val requestsListener = firebaseFirestore?.collection("ride_requests")?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SawaariShare", "Listen to ride_requests failed: ${error.message}")
                    _isConnected.value = false
                    return@addSnapshotListener
                }
                _isConnected.value = true
                if (snapshot != null) {
                    val reqs = snapshot.documents.mapNotNull { doc -> doc.toRideRequestSafe() }
                    if (reqs.isNotEmpty() || snapshot.isEmpty) {
                        val updatedReqsMap = _rideRequests.value.toMutableMap()
                        reqs.forEach { req ->
                            updatedReqsMap[req.id] = req
                        }
                        _rideRequests.value = updatedReqsMap
                        updateFeeds(_currentUser.value)
                        saveList("ride_requests.json", _rideRequests.value.values.toList(), rideRequestListAdapter)
                        _lastSyncTime.value = System.currentTimeMillis()
                    }
                }
            }
            if (requestsListener != null) listenerRegistrations.add(requestsListener)
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to register ride_requests listener: ${e.message}")
        }

        // Listen to trip_matches in real-time
        try {
            val matchesListener = firebaseFirestore?.collection("trip_matches")?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SawaariShare", "Listen to trip_matches failed: ${error.message}")
                    _isConnected.value = false
                    return@addSnapshotListener
                }
                _isConnected.value = true
                if (snapshot != null) {
                    val matches = snapshot.documents.mapNotNull { doc -> doc.toObject(TripMatch::class.java) }
                    if (matches.isNotEmpty() || snapshot.isEmpty) {
                        val updatedMatchesMap = _tripMatches.value.toMutableMap()
                        matches.forEach { match ->
                            updatedMatchesMap[match.id] = match
                        }
                        _tripMatches.value = updatedMatchesMap
                        updateFeeds(_currentUser.value)
                        saveList("trip_matches.json", _tripMatches.value.values.toList(), tripMatchListAdapter)
                        _lastSyncTime.value = System.currentTimeMillis()
                    }
                }
            }
            if (matchesListener != null) listenerRegistrations.add(matchesListener)
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to register trip_matches listener: ${e.message}")
        }

        // Listen to messages in real-time
        try {
            val messagesListener = firebaseFirestore?.collection("messages")?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SawaariShare", "Listen to messages failed: ${error.message}")
                    _isConnected.value = false
                    return@addSnapshotListener
                }
                _isConnected.value = true
                if (snapshot != null) {
                    val msgs = snapshot.documents.mapNotNull { doc -> doc.toMessageSafe() }
                    if (msgs.isNotEmpty() || snapshot.isEmpty) {
                        val updatedMsgsMap = _messages.value.toMutableMap()
                        msgs.forEach { msg ->
                            updatedMsgsMap[msg.id] = msg
                        }
                        _messages.value = updatedMsgsMap
                        saveList("messages.json", _messages.value.values.toList(), messageListAdapter)
                        _lastSyncTime.value = System.currentTimeMillis()
                    }
                }
            }
            if (messagesListener != null) listenerRegistrations.add(messagesListener)
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to register messages listener: ${e.message}")
        }

        // Listen to notifications in real-time
        try {
            val currentUserId = _currentUser.value?.id ?: ""
            if (currentUserId.isNotEmpty()) {
                val notificationsListener = firebaseFirestore?.collection("notifications")
                    ?.whereEqualTo("userId", currentUserId)
                    ?.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("SawaariShare", "Listen to notifications failed: ${error.message}")
                            _isConnected.value = false
                            return@addSnapshotListener
                        }
                        _isConnected.value = true
                        if (snapshot != null) {
                            val alerts = snapshot.documents
                                .mapNotNull { doc -> doc.toNotificationAlertSafe() }
                                .sortedByDescending { it.timestamp }
                            if (alerts.isNotEmpty() || snapshot.isEmpty) {
                                _notifications.value = alerts
                                saveList("notifications.json", _notifications.value, notificationListAdapter)
                                _lastSyncTime.value = System.currentTimeMillis()
                            }
                        }
                    }
                if (notificationsListener != null) listenerRegistrations.add(notificationsListener)
            }
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to register notifications listener: ${e.message}")
        }

        // Listen to users for profile updates
        try {
            val usersListener = firebaseFirestore?.collection("users")?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SawaariShare", "Listen to users failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val users = snapshot.documents.mapNotNull { doc -> doc.toObject(User::class.java) }
                    if (users.isNotEmpty() || snapshot.isEmpty) {
                        val updatedUsersMap = _users.value.toMutableMap()
                        users.forEach { user ->
                            updatedUsersMap[user.id] = user
                            // Update current user if it's them
                            if (_currentUser.value?.id == user.id) {
                                _currentUser.value = user
                            }
                        }
                        _users.value = updatedUsersMap
                        saveList("users.json", _users.value.values.toList(), userListAdapter)
                    }
                }
            }
            if (usersListener != null) listenerRegistrations.add(usersListener)
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to register users listener: ${e.message}")
        }

        Log.d("SawaariShare", "Real-time listeners registered: ${listenerRegistrations.size}")
    }

    fun stopRealtimeListeners() {
        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
        Log.d("SawaariShare", "Real-time listeners stopped")
    }

    private fun updateFeeds(currentUser: User?) {
        val blocks = _blocks.value.values
        val currentUserId = currentUser?.id ?: ""
        val now = System.currentTimeMillis()
        
        // Block lists: users blocked by me or who blocked me
        val blockedUserIds = blocks.filter { it.userId == currentUserId }.map { it.blockedUserId }.toSet() +
                blocks.filter { it.blockedUserId == currentUserId }.map { it.userId }.toSet()

        // 1. Filter Offers (excluding blocked users, applying womenOnly rules, and not expired)
        val filteredOffers = _tripOffers.value.values.filter { offer ->
            offer.status == "active" &&
            offer.hostId != currentUserId &&
            offer.departureTime > now &&
            !blockedUserIds.contains(offer.hostId) &&
            // Women only logic: if offer is women-only, host must be a woman (or we respect filter), and only women can view it
            (!offer.womenOnly || (currentUser?.isWomenOnlyFilterEnabled == true))
        }.sortedByDescending { offer ->
            // Prioritize higher host ratings or proximity
            offer.hostRating
        }
        _activeOffers.value = filteredOffers

        // 2. Filter Requests (excluding expired, blocked, and womenOnly filtered requests)
        val filteredRequests = _rideRequests.value.values.filter { req ->
            req.status == "active" &&
            req.riderId != currentUserId &&
            req.departureTime > now &&
            !blockedUserIds.contains(req.riderId) &&
            (!req.womenOnly || (currentUser?.isWomenOnlyFilterEnabled == true))
        }.sortedBy { it.departureTime }
        _activeRequests.value = filteredRequests

        // 3. User matches
        val matches = _tripMatches.value.values.filter { match ->
            match.hostId == currentUserId || match.riderId == currentUserId
        }.sortedByDescending { it.timestamp }
        _userMatches.value = matches

        // 4. Own Ride Requests
        val ownRequests = _rideRequests.value.values.filter { req ->
            req.riderId == currentUserId
        }.sortedBy { it.departureTime }
        _myRideRequests.value = ownRequests
    }

    // --- Authentication & Account Setup ---

    fun signUpWithEmail(email: String, password: String, onSuccess: (isNewUser: Boolean) -> Unit, onFailure: (String) -> Unit) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password.trim()
        if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty()) {
            onFailure("Email and password cannot be empty")
            return
        }
        if (trimmedPassword.length < 6) {
            onFailure("Password must be at least 6 characters")
            return
        }
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            onFailure("Please enter a valid email address.")
            return
        }

        val initialVerifiedTier = "vouched"

        scope.launch {
            if (isFirebaseEnabled && firebaseAuth != null) {
                try {
                    val result = firebaseAuth?.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword)?.awaitTask()
                    val firebaseUser = result?.user
                    val userId = firebaseUser?.uid ?: UUID.randomUUID().toString()
                    
                    // Send verification email
                    try {
                        firebaseUser?.sendEmailVerification()?.awaitTask()
                    } catch (ev: Exception) {
                        Log.e("SawaariShare", "Failed to send email verification: ${ev.message}")
                    }
                    
                    val newUser = User(id = userId, email = trimmedEmail, verifiedTier = initialVerifiedTier)
                    _users.value = _users.value + (userId to newUser)
                    saveList("users.json", _users.value.values.toList(), userListAdapter)
                    _currentUser.value = newUser

                    try {
                        firebaseFirestore?.collection("users")?.document(userId)?.set(newUser)?.awaitTask()
                    } catch (fsEx: Exception) {
                        Log.e("SawaariShare", "Failed to save user to Firestore: ${fsEx.message}")
                    }
                    
                    withContext(Dispatchers.Main) { onSuccess(true) }
                } catch (e: Exception) {
                    val msg = e.message ?: "Registration failed."
                    if (msg.contains("ALREADY_IN_USE", ignoreCase = true) || msg.contains("already exists", ignoreCase = true)) {
                        withContext(Dispatchers.Main) { onFailure("An account with this email already exists.") }
                    } else {
                        withContext(Dispatchers.Main) { onFailure(msg) }
                    }
                }
            } else {
                if (_credentials.value.containsKey(trimmedEmail)) {
                    withContext(Dispatchers.Main) { onFailure("An account with this email already exists.") }
                    return@launch
                }
                
                val userId = "user_${UUID.randomUUID().toString().take(6)}"
                val newCred = LocalCredential(email = trimmedEmail, password = trimmedPassword, userId = userId)
                _credentials.value = _credentials.value + (trimmedEmail to newCred)
                saveList("credentials.json", _credentials.value.values.toList(), localCredentialAdapter)

                val newUser = User(id = userId, email = trimmedEmail, verifiedTier = initialVerifiedTier)
                _users.value = _users.value + (userId to newUser)
                saveList("users.json", _users.value.values.toList(), userListAdapter)
                _currentUser.value = newUser

                withContext(Dispatchers.Main) { onSuccess(true) }
            }
        }
    }

    suspend fun verifyCollegeEmail(collegeEmail: String): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmed = collegeEmail.trim().lowercase()
        if (!trimmed.contains("@") || !trimmed.contains(".")) {
            return@withContext Result.failure(Exception("Please enter a valid email address."))
        }
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("No logged in user found."))

        val domain = trimmed.substringAfter("@")
        val guessedCollege = if (user.collegeName.isEmpty()) {
            domain.substringBefore(".").replaceFirstChar { it.uppercase() } + " Org"
        } else {
            user.collegeName
        }

        val updatedUser = user.copy(
            verifiedTier = "vouched",
            verifiedEmail = trimmed,
            collegeName = guessedCollege
        )
        _users.value = _users.value + (user.id to updatedUser)
        _currentUser.value = updatedUser

        saveList("users.json", _users.value.values.toList(), userListAdapter)
        updateFeeds(updatedUser)

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("users")?.document(user.id)?.set(updatedUser)?.awaitTask()
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to sync verified status to Firestore: ${e.message}")
            }
        }

        return@withContext Result.success(Unit)
    }

    fun logInWithEmail(email: String, password: String, onSuccess: (isNewUser: Boolean) -> Unit, onFailure: (String) -> Unit) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password.trim()
        if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty()) {
            onFailure("Email and password cannot be empty")
            return
        }

        scope.launch {
            if (isFirebaseEnabled && firebaseAuth != null) {
                try {
                    val result = firebaseAuth?.signInWithEmailAndPassword(trimmedEmail, trimmedPassword)?.awaitTask()
                    val firebaseUser = result?.user
                    val userId = firebaseUser?.uid ?: ""
                    
                    var existingUser = _users.value[userId]
                    if (existingUser == null) {
                        existingUser = _users.value.values.find { it.email.lowercase() == trimmedEmail }
                    }
                    val isNewUser = existingUser == null || existingUser.name.isEmpty()
                    
                    val finalUser = existingUser ?: User(id = userId, email = trimmedEmail, verifiedTier = "vouched")
                    if (existingUser == null) {
                        _users.value = _users.value + (userId to finalUser)
                        saveList("users.json", _users.value.values.toList(), userListAdapter)
                    }
                    _currentUser.value = finalUser
                    
                    withContext(Dispatchers.Main) { onSuccess(isNewUser) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onFailure(e.message ?: "Invalid email or password.") }
                }
            } else {
                val cred = _credentials.value[trimmedEmail]
                if (cred == null || cred.password != trimmedPassword) {
                    withContext(Dispatchers.Main) { onFailure("Invalid email or password.") }
                    return@launch
                }

                var existingUser = _users.value[cred.userId]
                if (existingUser == null) {
                    existingUser = _users.value.values.find { it.email.lowercase() == trimmedEmail }
                }
                val isNewUser = existingUser == null || existingUser.name.isEmpty()
                
                val finalUser = existingUser ?: User(id = cred.userId, email = trimmedEmail, verifiedTier = "vouched")
                if (existingUser == null) {
                    _users.value = _users.value + (cred.userId to finalUser)
                    saveList("users.json", _users.value.values.toList(), userListAdapter)
                }
                _currentUser.value = finalUser

                withContext(Dispatchers.Main) { onSuccess(isNewUser) }
            }
        }
    }

    suspend fun redeemInviteCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val upperCode = code.trim().uppercase()
        val invite = _invites.value[upperCode]
            ?: return@withContext Result.failure(Exception("Invalid invite code. Try 'SAWAARISHARE'"))

        if (invite.used) {
            return@withContext Result.failure(Exception("Invite code already used!"))
        }

        val user = _currentUser.value ?: return@withContext Result.failure(Exception("No logged in user found."))

        // Atomic update of user tier and invite status
        val updatedInvite = invite.copy(used = true, usedBy = user.id)
        val updatedUser = user.copy(verifiedTier = "vouched", invitedBy = invite.invitedBy)

        _invites.value = _invites.value + (upperCode to updatedInvite)
        _users.value = _users.value + (user.id to updatedUser)
        _currentUser.value = updatedUser

        saveList("invites.json", _invites.value.values.toList(), inviteListAdapter)
        saveList("users.json", _users.value.values.toList(), userListAdapter)
        updateFeeds(updatedUser)

        return@withContext Result.success(Unit)
    }

    suspend fun createUserProfile(
        name: String,
        lastInitial: String,
        communityId: String,
        homeArea: String,
        vehicle: Vehicle? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("No logged in user found."))

        if (name.trim().isEmpty() || lastInitial.trim().isEmpty() || communityId.isEmpty() || homeArea.isEmpty()) {
            return@withContext Result.failure(Exception("All profile fields are required."))
        }

        val updatedUser = user.copy(
            name = name.trim(),
            lastInitial = lastInitial.trim(),
            communityId = communityId,
            homeArea = homeArea
        )

        _users.value = _users.value + (user.id to updatedUser)
        _currentUser.value = updatedUser

        saveList("users.json", _users.value.values.toList(), userListAdapter)

        // Sync to Firestore
        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("users")?.document(user.id)?.set(updatedUser)?.awaitTask()
                Log.d("SawaariShare", "User profile successfully saved to Firestore.")
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to sync profile to Firestore: ${e.message}")
                // Still succeeds locally even if Firestore fails
            }
        }

        if (vehicle != null) {
            saveVehicleInfo(vehicle)
        } else {
            updateFeeds(updatedUser)
        }

        return@withContext Result.success(Unit)
    }

    suspend fun updateUserProfileDetails(
        name: String,
        lastInitial: String,
        collegeName: String,
        avatarUrl: String,
        verifiedEmail: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("No logged in user found."))

        val updatedUser = user.copy(
            name = name,
            lastInitial = lastInitial,
            collegeName = collegeName,
            avatarUrl = avatarUrl,
            verifiedEmail = verifiedEmail
        )

        _users.value = _users.value + (user.id to updatedUser)
        _currentUser.value = updatedUser

        saveList("users.json", _users.value.values.toList(), userListAdapter)
        updateFeeds(updatedUser)

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("users")?.document(user.id)?.set(updatedUser)?.awaitTask()
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to sync profile update to Firestore: ${e.message}")
            }
        }

        return@withContext Result.success(Unit)
    }

    private fun saveVehicleInfo(vehicle: Vehicle) {
        // Simple mock / local store, could link to Firestore if enabled
        val file = File(context.filesDir, "vehicle_${vehicle.ownerId}.json")
        file.writeText(moshi.adapter(Vehicle::class.java).toJson(vehicle))
        updateFeeds(_currentUser.value)
    }

    fun getVehicleInfo(userId: String): Vehicle? {
        val file = File(context.filesDir, "vehicle_$userId.json")
        if (!file.exists()) return null
        return try {
            moshi.adapter(Vehicle::class.java).fromJson(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    // --- Core Carpooling: Offers & Requests ---

    suspend fun postTripOffer(offer: TripOffer): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUser = _currentUser.value ?: return@withContext Result.failure(Exception("Please log in to post a ride."))

        // Validation
        if (offer.origin.trim().isEmpty() || offer.destination.trim().isEmpty()) {
            return@withContext Result.failure(Exception("Origin and destination are required."))
        }
        if (offer.originLat == 0.0 || offer.originLng == 0.0 || offer.destLat == 0.0 || offer.destLng == 0.0) {
            return@withContext Result.failure(Exception("Valid pickup and dropoff locations required."))
        }
        if (offer.departureTime <= System.currentTimeMillis()) {
            return@withContext Result.failure(Exception("Departure time must be in the future."))
        }
        if (offer.totalSeats < 1 || offer.totalSeats > 8) {
            return@withContext Result.failure(Exception("Total seats must be between 1 and 8."))
        }
        if (offer.costPerRider < 0.0) {
            return@withContext Result.failure(Exception("Cost per rider cannot be negative."))
        }

        val id = "offer_${UUID.randomUUID().toString().take(8)}"
        val finalOffer = offer.copy(
            id = id,
            hostId = currentUser.id,
            hostName = currentUser.name,
            hostRating = currentUser.ratingAvg,
            originGeohash = GeoUtils.encodeGeohash(offer.originLat, offer.originLng, 7),
            destGeohash = GeoUtils.encodeGeohash(offer.destLat, offer.destLng, 7),
            costEstimate = offer.costPerRider * offer.totalSeats,
            seatsLeft = offer.totalSeats,
            status = "active"
        )

        _tripOffers.value = _tripOffers.value + (id to finalOffer)
        saveList("trip_offers.json", _tripOffers.value.values.toList(), tripOfferListAdapter)
        updateFeeds(_currentUser.value)

        // Evaluate matching notifications
        val now = System.currentTimeMillis()
        val otherUsersWithMatchingRequests = _users.value.values.filter { u ->
            u.id != finalOffer.hostId
        }.filter { u ->
            _rideRequests.value.values.any { req ->
                req.riderId == u.id &&
                req.status == "active" &&
                req.departureTime > now &&
                (req.origin.lowercase().trim() == finalOffer.origin.lowercase().trim() ||
                 req.origin.lowercase().trim().contains(finalOffer.origin.lowercase().trim()) ||
                 finalOffer.origin.lowercase().trim().contains(req.origin.lowercase().trim())) &&
                (req.destination.lowercase().trim() == finalOffer.destination.lowercase().trim() ||
                 req.destination.lowercase().trim().contains(finalOffer.destination.lowercase().trim()) ||
                 finalOffer.destination.lowercase().trim().contains(req.destination.lowercase().trim()))
            }
        }

        val newAlerts = mutableListOf<NotificationAlert>()
        otherUsersWithMatchingRequests.forEach { matchingUser ->
            if (matchingUser.emailNotificationsEnabled) {
                newAlerts.add(NotificationAlert(
                    id = "alert_${UUID.randomUUID().toString().take(8)}",
                    title = "New Trip Posted (Email Notification)",
                    message = "Hi ${matchingUser.name}, ${finalOffer.hostName} just posted a trip from ${finalOffer.origin} to ${finalOffer.destination} matching your active ride request route!",
                    type = "email",
                    timestamp = System.currentTimeMillis()
                ))
            }
            if (matchingUser.pushNotificationsEnabled) {
                newAlerts.add(NotificationAlert(
                    id = "alert_${UUID.randomUUID().toString().take(8)}",
                    title = "New Trip Posted (Push Notification)",
                    message = "${finalOffer.hostName} posted a trip from ${finalOffer.origin} to ${finalOffer.destination} matching your active ride request!",
                    type = "push",
                    timestamp = System.currentTimeMillis()
                ))
            }
        }

        if (newAlerts.isNotEmpty()) {
            _notifications.value = newAlerts + _notifications.value
            saveList("notifications.json", _notifications.value, notificationListAdapter)
        }

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("trip_offers")?.document(id)?.set(finalOffer.toMap())?.awaitTask(4000L)
                Log.d("SawaariShare", "Trip offer successfully posted to Firestore.")
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to post trip offer to Firestore, using local persistence: ${e.message}")
            }
        }

        return@withContext Result.success(Unit)
    }

    suspend fun postRideRequest(request: RideRequest): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUser = _currentUser.value ?: return@withContext Result.failure(Exception("Please log in to post a ride request."))

        // Validation
        if (request.origin.trim().isEmpty() || request.destination.trim().isEmpty()) {
            return@withContext Result.failure(Exception("Pickup and dropoff locations are required."))
        }
        if (request.originLat == 0.0 || request.originLng == 0.0 || request.destLat == 0.0 || request.destLng == 0.0) {
            return@withContext Result.failure(Exception("Valid pickup and dropoff coordinates required."))
        }
        if (request.departureTime <= System.currentTimeMillis()) {
            return@withContext Result.failure(Exception("Departure time must be in the future."))
        }
        if (request.seatsNeeded < 1 || request.seatsNeeded > 8) {
            return@withContext Result.failure(Exception("Seats needed must be between 1 and 8."))
        }

        val id = "request_${UUID.randomUUID().toString().take(8)}"
        val finalRequest = request.copy(
            id = id,
            riderId = currentUser.id,
            riderName = currentUser.name,
            riderRating = currentUser.ratingAvg,
            originGeohash = GeoUtils.encodeGeohash(request.originLat, request.originLng, 7),
            destGeohash = GeoUtils.encodeGeohash(request.destLat, request.destLng, 7),
            status = "active"
        )

        _rideRequests.value = _rideRequests.value + (id to finalRequest)
        saveList("ride_requests.json", _rideRequests.value.values.toList(), rideRequestListAdapter)
        updateFeeds(_currentUser.value)

        // Evaluate matching notifications
        val now = System.currentTimeMillis()
        val matchingOffers = _tripOffers.value.values.filter { offer ->
            offer.status == "active" &&
            offer.seatsLeft >= finalRequest.seatsNeeded &&
            offer.departureTime > now &&
            (offer.origin.lowercase().trim() == finalRequest.origin.lowercase().trim() ||
             offer.origin.lowercase().trim().contains(finalRequest.origin.lowercase().trim()) ||
             finalRequest.origin.lowercase().trim().contains(offer.origin.lowercase().trim())) &&
            (offer.destination.lowercase().trim() == finalRequest.destination.lowercase().trim() ||
             offer.destination.lowercase().trim().contains(finalRequest.destination.lowercase().trim()) ||
             finalRequest.destination.lowercase().trim().contains(offer.destination.lowercase().trim()))
        }

        matchingOffers.forEach { offer ->
            val driver = _users.value[offer.hostId]
            if (driver != null && driver.emailNotificationsEnabled) {
                sendNotificationAlert(
                    targetUserId = offer.hostId,
                    title = "New Ride Request Matching Your Trip! 🚗",
                    message = "${finalRequest.riderName} needs ${finalRequest.seatsNeeded} seat(s) from ${finalRequest.origin} to ${finalRequest.destination}.",
                    type = "email"
                )
            }
            if (driver != null && driver.pushNotificationsEnabled) {
                sendNotificationAlert(
                    targetUserId = offer.hostId,
                    title = "Rider Looking for Your Route! 👥",
                    message = "${finalRequest.riderName} needs ${finalRequest.seatsNeeded} seat(s) ${finalRequest.origin} → ${finalRequest.destination}",
                    type = "push"
                )
            }
        }

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("ride_requests")?.document(id)?.set(finalRequest.toMap())?.awaitTask(4000L)
                Log.d("SawaariShare", "Ride request successfully posted to Firestore.")
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to post ride request to Firestore, using local persistence: ${e.message}")
            }
        }

        return@withContext Result.success(Unit)
    }

    // --- Cost Cap & Invite Validation (Server-Side calculations run atomically here) ---

    suspend fun validateAndCreateMatch(
        offerId: String,
        requestId: String,
        contribution: Double
    ): Result<TripMatch> = withContext(Dispatchers.IO) {
        val offer = _tripOffers.value[offerId]
            ?: return@withContext Result.failure(Exception("Trip Offer not found."))
        
        var request = _rideRequests.value[requestId]
        if (request == null) {
            val currentU = _currentUser.value
            request = RideRequest(
                id = requestId,
                riderId = currentU?.id ?: "",
                riderName = currentU?.name ?: "Rider",
                riderRating = currentU?.ratingAvg ?: 5.0f,
                origin = offer.origin,
                destination = offer.destination,
                seatsNeeded = 1,
                departureTime = offer.departureTime,
                status = "active"
            )
            _rideRequests.value = _rideRequests.value + (requestId to request)
            saveList("ride_requests.json", _rideRequests.value.values.toList(), rideRequestListAdapter)
        }

        // 1. Cost sharing validation: contribution cap
        val costLimit = offer.costPerRider * 2.0
        if (contribution > costLimit) {
            return@withContext Result.failure(Exception("Rejected: Contribution of $$contribution exceeds the 2x per-rider cost cap ($$costLimit)."))
        }

        // 2. Seat limit validation
        if (offer.seatsLeft < request.seatsNeeded) {
            return@withContext Result.failure(Exception("Rejected: Offer only has ${offer.seatsLeft} seats left, but you requested ${request.seatsNeeded}."))
        }

        // 3. Prevent duplicate requests
        val duplicate = _tripMatches.value.values.find {
            it.offerId == offerId && it.requestId == requestId && it.status != "declined"
        }
        if (duplicate != null) {
            return@withContext Result.failure(Exception("Match already exists or is pending!"))
        }

        // Available seats are decremented once confirmed (accepted).
        val matchId = "match_${UUID.randomUUID().toString().take(8)}"
        val match = TripMatch(
            id = matchId,
            offerId = offerId,
            requestId = requestId,
            hostId = offer.hostId,
            riderId = request.riderId,
            riderName = request.riderName,
            riderRating = request.riderRating,
            contribution = contribution,
            status = "pending", // Initially pending, needs host approval
            timestamp = System.currentTimeMillis()
        )

        _tripMatches.value = _tripMatches.value + (matchId to match)

        saveList("trip_matches.json", _tripMatches.value.values.toList(), tripMatchListAdapter)
        updateFeeds(_currentUser.value)

        // Notify host about the new ride request
        sendNotificationAlert(
            targetUserId = offer.hostId,
            title = "New Ride Request Received! 🚗",
            message = "${request.riderName} requested a seat on your ride from ${offer.origin} to ${offer.destination}.",
            type = "match"
        )

        return@withContext Result.success(match)
    }

    suspend fun joinTripOfferDirect(offerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUser = _currentUser.value ?: return@withContext Result.failure(Exception("Please log in to join a Sawaari."))
        val offer = _tripOffers.value[offerId] ?: return@withContext Result.failure(Exception("Trip Offer not found."))

        if (offer.hostId == currentUser.id) {
            return@withContext Result.failure(Exception("You cannot join your own Sawaari."))
        }

        if (offer.passengers.contains(currentUser.id)) {
            return@withContext Result.failure(Exception("You have already reserved a seat on this trip."))
        }

        if (offer.seatsLeft <= 0) {
            return@withContext Result.failure(Exception("This trip has no seats left!"))
        }

        val updatedPassengers = offer.passengers + currentUser.id
        val updatedPassengerNames = offer.passengerNames + (currentUser.name ?: "Student")
        val newSeatsLeft = offer.seatsLeft - 1
        val newStatus = if (newSeatsLeft == 0) "full" else offer.status

        val updatedOffer = offer.copy(
            seatsLeft = newSeatsLeft,
            passengers = updatedPassengers,
            passengerNames = updatedPassengerNames,
            status = newStatus
        )

        _tripOffers.value = _tripOffers.value + (offerId to updatedOffer)
        saveList("trip_offers.json", _tripOffers.value.values.toList(), tripOfferListAdapter)
        updateFeeds(currentUser)

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("trip_offers")?.document(offerId)?.set(updatedOffer.toMap())?.awaitTask(4000L)
                Log.d("SawaariShare", "Successfully updated direct join on Firestore.")
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to sync direct join to Firestore: ${e.message}")
            }
        }

        // Notify host about the direct join
        sendNotificationAlert(
            targetUserId = offer.hostId,
            title = "New Rider Joined Your Ride! 👋",
            message = "${currentUser.name} reserved a seat on your Sawaari from ${offer.origin} to ${offer.destination}.",
            type = "ride_accepted"
        )

        return@withContext Result.success(Unit)
    }

    suspend fun updateTripOfferStatus(offerId: String, newStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUser = _currentUser.value ?: return@withContext Result.failure(Exception("Please log in."))
        val offer = _tripOffers.value[offerId] ?: return@withContext Result.failure(Exception("Trip Offer not found."))

        if (offer.hostId != currentUser.id) {
            return@withContext Result.failure(Exception("Only the host can modify this trip's status."))
        }

        val updatedOffer = offer.copy(status = newStatus)

        _tripOffers.value = _tripOffers.value + (offerId to updatedOffer)
        saveList("trip_offers.json", _tripOffers.value.values.toList(), tripOfferListAdapter)
        updateFeeds(currentUser)

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("trip_offers")?.document(offerId)?.set(updatedOffer.toMap())?.awaitTask(4000L)
                Log.d("SawaariShare", "Successfully updated status on Firestore.")
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to sync status to Firestore: ${e.message}")
            }
        }

        return@withContext Result.success(Unit)
    }

    suspend fun updateRideRequestStatus(requestId: String, newStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUser = _currentUser.value ?: return@withContext Result.failure(Exception("Please log in."))
        val request = _rideRequests.value[requestId] ?: return@withContext Result.failure(Exception("Ride Request not found."))

        if (request.riderId != currentUser.id) {
            return@withContext Result.failure(Exception("Only the passenger can modify this request's status."))
        }

        val updatedRequest = request.copy(status = newStatus)

        _rideRequests.value = _rideRequests.value + (requestId to updatedRequest)
        saveList("ride_requests.json", _rideRequests.value.values.toList(), rideRequestListAdapter)
        updateFeeds(currentUser)

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("ride_requests")?.document(requestId)?.set(updatedRequest.toMap())?.awaitTask(4000L)
                Log.d("SawaariShare", "Successfully updated ride request status on Firestore.")
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to sync ride request status to Firestore: ${e.message}")
            }
        }

        return@withContext Result.success(Unit)
    }

    suspend fun fetchMyTripsFromFirestore(): Result<Pair<List<TripOffer>, List<TripOffer>>> = withContext(Dispatchers.IO) {
        val currentUser = _currentUser.value ?: return@withContext Result.failure(Exception("Please log in to retrieve your trips."))
        val userId = currentUser.id

        if (!isFirebaseEnabled || firebaseFirestore == null) {
            val hosted = _tripOffers.value.values.filter { it.hostId == userId }.sortedBy { it.departureTime }
            val joined = _tripOffers.value.values.filter { it.passengers.contains(userId) }.sortedBy { it.departureTime }
            return@withContext Result.success(Pair(hosted, joined))
        }

        try {
            val hostedTask = firebaseFirestore!!.collection("trip_offers")
                .whereEqualTo("hostId", userId)
                .get()
                .awaitTask(4000L)
            
            val hostedList = hostedTask.documents.mapNotNull { doc -> doc.toTripOfferSafe() }.sortedBy { it.departureTime }

            val joinedTask = firebaseFirestore!!.collection("trip_offers")
                .whereArrayContains("passengers", userId)
                .get()
                .awaitTask(4000L)
            
            val joinedList = joinedTask.documents.mapNotNull { doc -> doc.toTripOfferSafe() }.sortedBy { it.departureTime }

            // Update real-time local cache with loaded values
            val updatedMap = _tripOffers.value.toMutableMap()
            hostedList.forEach { updatedMap[it.id] = it }
            joinedList.forEach { updatedMap[it.id] = it }
            _tripOffers.value = updatedMap

            return@withContext Result.success(Pair(hostedList, joinedList))
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to fetch my trips from Firestore: ${e.message}")
            val hosted = _tripOffers.value.values.filter { it.hostId == userId }.sortedBy { it.departureTime }
            val joined = _tripOffers.value.values.filter { it.passengers.contains(userId) }.sortedBy { it.departureTime }
            return@withContext Result.success(Pair(hosted, joined))
        }
    }

    suspend fun acceptMatch(matchId: String) = withContext(Dispatchers.IO) {
        val match = _tripMatches.value[matchId] ?: return@withContext
        val updatedMatch = match.copy(status = "accepted")

        // Also mark matching ride request as matched/completed
        val request = _rideRequests.value[match.requestId]
        if (request != null) {
            val updatedRequest = request.copy(status = "matched")
            _rideRequests.value = _rideRequests.value + (request.id to updatedRequest)
            saveList("ride_requests.json", _rideRequests.value.values.toList(), rideRequestListAdapter)
        }

        // Decrement seats and add passenger in Firestore and local state once confirmed
        val offer = _tripOffers.value[match.offerId]
        if (offer != null) {
            val seatsNeeded = request?.seatsNeeded ?: 1
            val newSeatsLeft = (offer.seatsLeft - seatsNeeded).coerceAtLeast(0)
            val updatedPassengers = offer.passengers + match.riderId
            val updatedPassengerNames = offer.passengerNames + match.riderName
            val newStatus = if (newSeatsLeft <= 0) "full" else offer.status

            val updatedOffer = offer.copy(
                seatsLeft = newSeatsLeft,
                passengers = updatedPassengers,
                passengerNames = updatedPassengerNames,
                status = newStatus
            )

            _tripOffers.value = _tripOffers.value + (offer.id to updatedOffer)
            saveList("trip_offers.json", _tripOffers.value.values.toList(), tripOfferListAdapter)

            if (isFirebaseEnabled && firebaseFirestore != null) {
                try {
                    firebaseFirestore?.collection("trip_offers")?.document(offer.id)?.set(updatedOffer)?.awaitTask()
                    Log.d("SawaariShare", "Successfully updated available seat count in Firebase once confirmed.")
                } catch (e: Exception) {
                    Log.e("SawaariShare", "Failed to sync offer seat count update to Firebase: ${e.message}")
                }
            }
        }

        _tripMatches.value = _tripMatches.value + (matchId to updatedMatch)
        saveList("trip_matches.json", _tripMatches.value.values.toList(), tripMatchListAdapter)
        updateFeeds(_currentUser.value)

        // System message inside chat
        sendSystemMessage(matchId, "Trip request accepted by host! You can now chat and coordinate cash-in-person split.")

        // Alert the rider that their ride request was accepted
        sendNotificationAlert(
            targetUserId = match.riderId,
            title = "Ride Request Accepted! 🚗",
            message = "Your ride request from ${request?.origin ?: offer?.origin ?: "origin"} to ${request?.destination ?: offer?.destination ?: "destination"} was accepted by ${offer?.hostName ?: _currentUser.value?.name ?: "the host"}.",
            type = "ride_accepted"
        )
    }

    suspend fun declineMatch(matchId: String) = withContext(Dispatchers.IO) {
        val match = _tripMatches.value[matchId] ?: return@withContext
        val updatedMatch = match.copy(status = "declined")

        // Revert seats count on the trip offer ONLY if the match was previously accepted
        val offer = _tripOffers.value[match.offerId]
        val request = _rideRequests.value[match.requestId]
        if (offer != null && request != null && match.status == "accepted") {
            val updatedOffer = offer.copy(
                seatsLeft = (offer.seatsLeft + request.seatsNeeded).coerceAtMost(offer.totalSeats),
                passengers = offer.passengers - match.riderId,
                passengerNames = offer.passengerNames - match.riderName,
                status = if (offer.status == "full") "active" else offer.status
            )
            _tripOffers.value = _tripOffers.value + (offer.id to updatedOffer)
            saveList("trip_offers.json", _tripOffers.value.values.toList(), tripOfferListAdapter)

            if (isFirebaseEnabled && firebaseFirestore != null) {
                try {
                    firebaseFirestore?.collection("trip_offers")?.document(offer.id)?.set(updatedOffer)?.awaitTask()
                } catch (e: Exception) {
                    Log.e("SawaariShare", "Failed to sync reverted seat count to Firebase on decline: ${e.message}")
                }
            }
        }

        _tripMatches.value = _tripMatches.value + (matchId to updatedMatch)
        saveList("trip_matches.json", _tripMatches.value.values.toList(), tripMatchListAdapter)
        updateFeeds(_currentUser.value)
    }

    suspend fun completeTrip(matchId: String) = withContext(Dispatchers.IO) {
        val match = _tripMatches.value[matchId] ?: return@withContext
        val updatedMatch = match.copy(status = "completed")

        _tripMatches.value = _tripMatches.value + (matchId to updatedMatch)
        saveList("trip_matches.json", _tripMatches.value.values.toList(), tripMatchListAdapter)

        // Also mark offer as completed if all matches of this offer are done
        val offer = _tripOffers.value[match.offerId]
        if (offer != null) {
            val updatedOffer = offer.copy(status = "completed")
            _tripOffers.value = _tripOffers.value + (offer.id to updatedOffer)
            saveList("trip_offers.json", _tripOffers.value.values.toList(), tripOfferListAdapter)
        }

        updateFeeds(_currentUser.value)
    }

    // --- Real-time Chats & Messages ---

    fun getChatMessages(matchId: String): Flow<List<Message>> {
        // Expose a flow of filtered messages
        return MutableStateFlow<List<Message>>(emptyList()).apply {
            scope.launch {
                _messages.collect { allMessages ->
                    value = allMessages.values.filter { it.matchId == matchId }.sortedBy { it.timestamp }
                }
            }
        }
    }

    suspend fun sendMessage(matchId: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Not logged in."))
        val messageId = "msg_${UUID.randomUUID().toString().take(8)}"
        val message = Message(
            id = messageId,
            matchId = matchId,
            senderId = user.id,
            senderName = user.name,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        _messages.value = _messages.value + (messageId to message)
        saveList("messages.json", _messages.value.values.toList(), messageListAdapter)

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("messages")?.document(messageId)?.set(message.toMap())?.awaitTask(4000L)
                Log.d("SawaariShare", "Message successfully saved to Firestore.")
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to send message to Firestore, using local persistence: ${e.message}")
            }
        }

        // Real-time alert to recipient when a new message is received
        val match = _tripMatches.value[matchId]
        val recipientId = if (match != null) {
            if (user.id == match.hostId) match.riderId else match.hostId
        } else ""
        if (recipientId.isNotEmpty()) {
            sendNotificationAlert(
                targetUserId = recipientId,
                title = "New Message from ${user.name} 💬",
                message = text,
                type = "new_message"
            )
        }

        return@withContext Result.success(Unit)
    }

    private suspend fun sendSystemMessage(matchId: String, text: String) {
        val messageId = "msg_sys_${UUID.randomUUID().toString().take(8)}"
        val message = Message(
            id = messageId,
            matchId = matchId,
            senderId = "system",
            senderName = "SawaariBot",
            text = text,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + (messageId to message)
        saveList("messages.json", _messages.value.values.toList(), messageListAdapter)

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("messages")?.document(messageId)?.set(message.toMap())?.awaitTask(4000L)
            } catch (_: Exception) {}
        }
    }

    // --- Ratings (Writes triggers onRatingWrite re-calculating averages) ---

    suspend fun submitRating(toUserId: String, ratingValue: Float, comment: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = _currentUser.value?.id ?: return@withContext Result.failure(Exception("Not logged in"))
        val ratingId = "rating_${UUID.randomUUID().toString().take(8)}"
        
        val rating = Rating(
            id = ratingId,
            fromUserId = currentUserId,
            toUserId = toUserId,
            rating = ratingValue,
            comment = comment,
            timestamp = System.currentTimeMillis()
        )

        _ratings.value = _ratings.value + (ratingId to rating)
        saveList("ratings.json", _ratings.value.values.toList(), ratingListAdapter)

        // Run rating trigger onRatingWrite recompute logic locally
        recomputeUserRating(toUserId)

        return@withContext Result.success(Unit)
    }

    private fun recomputeUserRating(userId: String) {
        val userRatings = _ratings.value.values.filter { it.toUserId == userId }
        if (userRatings.isEmpty()) return

        val sum = userRatings.sumOf { it.rating.toDouble() }
        val avg = (sum / userRatings.size).toFloat()
        val count = userRatings.size

        val user = _users.value[userId]
        if (user != null) {
            val updatedUser = user.copy(ratingAvg = avg, ratingCount = count)
            _users.value = _users.value + (userId to updatedUser)
            saveList("users.json", _users.value.values.toList(), userListAdapter)
            
            if (_currentUser.value?.id == userId) {
                _currentUser.value = updatedUser
            } else {
                updateFeeds(_currentUser.value)
            }
        }
    }

    fun getUserPublicProfile(userId: String): User? {
        return _users.value[userId]
    }

    suspend fun fetchUserProfileFromFirestore(userId: String): Result<User> = withContext(Dispatchers.IO) {
        if (!isFirebaseEnabled || firebaseFirestore == null) {
            val user = _users.value[userId]
            return@withContext if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User not found locally"))
            }
        }

        try {
            val doc = firebaseFirestore!!.collection("users").document(userId).get().awaitTask()
            val user = doc.toObject(User::class.java)
            return@withContext if (user != null) {
                _users.value = _users.value + (userId to user)
                Result.success(user)
            } else {
                Result.failure(Exception("User profile not found"))
            }
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to fetch user profile from Firestore: ${e.message}")
            val cachedUser = _users.value[userId]
            return@withContext if (cachedUser != null) {
                Result.success(cachedUser)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun isValidCollegeEmail(email: String): Boolean = withContext(Dispatchers.IO) {
        val domain = email.lowercase().substringAfter("@")
        val knownCollegeDomains = setOf(
            "northeastern.edu", "asu.edu", "utdallas.edu", "usc.edu", "indiana.edu",
            "mit.edu", "harvard.edu", "stanford.edu", "caltech.edu", "yale.edu",
            "columbia.edu", "upenn.edu", "dartmouth.edu", "brown.edu", "cornell.edu",
            "emory.edu", "michigan.edu", "northwestern.edu", "duke.edu", "chicago.edu"
        )
        return@withContext knownCollegeDomains.contains(domain)
    }

    fun getTripOfferById(offerId: String): TripOffer? {
        return _tripOffers.value[offerId]
    }

    suspend fun fetchTripOfferFromFirestore(offerId: String): Result<TripOffer> = withContext(Dispatchers.IO) {
        if (!isFirebaseEnabled || firebaseFirestore == null) {
            val offer = _tripOffers.value[offerId]
            return@withContext if (offer != null) {
                Result.success(offer)
            } else {
                Result.failure(Exception("Trip offer not found locally"))
            }
        }

        try {
            val doc = firebaseFirestore!!.collection("trip_offers").document(offerId).get().awaitTask()
            val offer = doc.toTripOfferSafe()
            return@withContext if (offer != null) {
                _tripOffers.value = _tripOffers.value + (offerId to offer)
                Result.success(offer)
            } else {
                Result.failure(Exception("Trip offer not found"))
            }
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to fetch trip offer from Firestore: ${e.message}")
            val cachedOffer = _tripOffers.value[offerId]
            return@withContext if (cachedOffer != null) {
                Result.success(cachedOffer)
            } else {
                Result.failure(e)
            }
        }
    }

    fun getHostedRides(userId: String): List<TripOffer> {
        return _tripOffers.value.values.filter { it.hostId == userId }.sortedByDescending { it.departureTime }
    }

    fun getActiveRides(): List<TripOffer> {
        val now = System.currentTimeMillis()
        return _tripOffers.value.values
            .filter { it.status == "active" && it.departureTime > now }
            .sortedBy { it.departureTime }
    }

    fun calculateCostSplit(totalCost: Double, riders: Int): Double {
        return if (riders > 0) totalCost / riders else 0.0
    }

    fun validateTripOffer(offer: TripOffer): Result<Unit> {
        if (offer.origin.trim().isEmpty() || offer.destination.trim().isEmpty()) {
            return Result.failure(Exception("Origin and destination are required."))
        }
        if (offer.departureTime <= System.currentTimeMillis()) {
            return Result.failure(Exception("Departure time must be in the future."))
        }
        if (offer.totalSeats < 1 || offer.totalSeats > 8) {
            return Result.failure(Exception("Total seats must be between 1 and 8."))
        }
        if (offer.costPerRider < 0.0) {
            return Result.failure(Exception("Cost per rider cannot be negative."))
        }
        return Result.success(Unit)
    }

    suspend fun fetchRideRequestFromFirestore(requestId: String): Result<RideRequest> = withContext(Dispatchers.IO) {
        if (!isFirebaseEnabled || firebaseFirestore == null) {
            val request = _rideRequests.value[requestId]
            return@withContext if (request != null) {
                Result.success(request)
            } else {
                Result.failure(Exception("Ride request not found locally"))
            }
        }

        try {
            val doc = firebaseFirestore!!.collection("ride_requests").document(requestId).get().awaitTask()
            val request = doc.toRideRequestSafe()
            return@withContext if (request != null) {
                _rideRequests.value = _rideRequests.value + (requestId to request)
                Result.success(request)
            } else {
                Result.failure(Exception("Ride request not found"))
            }
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to fetch ride request from Firestore: ${e.message}")
            val cachedRequest = _rideRequests.value[requestId]
            return@withContext if (cachedRequest != null) {
                Result.success(cachedRequest)
            } else {
                Result.failure(e)
            }
        }
    }

    fun getPassengerRequests(riderId: String): List<RideRequest> {
        return _rideRequests.value.values.filter { it.riderId == riderId }.sortedByDescending { it.departureTime }
    }

    fun getActiveRequests(): List<RideRequest> {
        val now = System.currentTimeMillis()
        return _rideRequests.value.values
            .filter { it.status == "active" && it.departureTime > now }
            .sortedBy { it.departureTime }
    }

    fun findMatchingOffers(request: RideRequest): List<TripOffer> {
        val now = System.currentTimeMillis()
        return _tripOffers.value.values.filter { offer ->
            offer.status == "active" &&
            offer.seatsLeft >= request.seatsNeeded &&
            offer.departureTime > now &&
            (offer.origin.lowercase().trim() == request.origin.lowercase().trim() ||
             offer.origin.lowercase().trim().contains(request.origin.lowercase().trim()) ||
             request.origin.lowercase().trim().contains(offer.origin.lowercase().trim())) &&
            (offer.destination.lowercase().trim() == request.destination.lowercase().trim() ||
             offer.destination.lowercase().trim().contains(request.destination.lowercase().trim()) ||
             request.destination.lowercase().trim().contains(offer.destination.lowercase().trim()))
        }.sortedBy { it.departureTime }
    }

    fun validateRideRequest(request: RideRequest): Result<Unit> {
        if (request.origin.trim().isEmpty() || request.destination.trim().isEmpty()) {
            return Result.failure(Exception("Pickup and dropoff locations are required."))
        }
        if (request.departureTime <= System.currentTimeMillis()) {
            return Result.failure(Exception("Departure time must be in the future."))
        }
        if (request.seatsNeeded < 1 || request.seatsNeeded > 8) {
            return Result.failure(Exception("Seats needed must be between 1 and 8."))
        }
        return Result.success(Unit)
    }

    fun recordNoShow(userId: String) {
        scope.launch {
            val user = _users.value[userId] ?: return@launch
            val updatedUser = user.copy(noShowCount = user.noShowCount + 1)
            _users.value = _users.value + (userId to updatedUser)
            saveList("users.json", _users.value.values.toList(), userListAdapter)
            if (_currentUser.value?.id == userId) {
                _currentUser.value = updatedUser
            } else {
                updateFeeds(_currentUser.value)
            }
        }
    }

    // --- Blocking / Exclusions ---

    suspend fun blockUser(blockedUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = _currentUser.value?.id ?: return@withContext Result.failure(Exception("Not logged in"))
        if (currentUserId == blockedUserId) return@withContext Result.failure(Exception("You cannot block yourself."))

        val id = "block_${currentUserId}_$blockedUserId"
        val block = Block(id = id, userId = currentUserId, blockedUserId = blockedUserId)

        _blocks.value = _blocks.value + (id to block)
        saveList("blocks.json", _blocks.value.values.toList(), blockListAdapter)
        updateFeeds(_currentUser.value)

        return@withContext Result.success(Unit)
    }

    suspend fun unblockUser(blockedUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = _currentUser.value?.id ?: return@withContext Result.failure(Exception("Not logged in"))
        val id = "block_${currentUserId}_$blockedUserId"

        _blocks.value = _blocks.value - id
        saveList("blocks.json", _blocks.value.values.toList(), blockListAdapter)
        updateFeeds(_currentUser.value)

        return@withContext Result.success(Unit)
    }

    fun getBlockedUsers(): List<User> {
        val currentUserId = _currentUser.value?.id ?: return emptyList()
        val blockedIds = _blocks.value.values.filter { it.userId == currentUserId }.map { it.blockedUserId }.toSet()
        return _users.value.values.filter { blockedIds.contains(it.id) }
    }

    // --- Settings and Filter Management ---

    fun toggleWomenOnlyFilter(enabled: Boolean) {
        val user = _currentUser.value ?: return
        val updatedUser = user.copy(isWomenOnlyFilterEnabled = enabled)
        _currentUser.value = updatedUser
        _users.value = _users.value + (user.id to updatedUser)
        saveList("users.json", _users.value.values.toList(), userListAdapter)
        updateFeeds(updatedUser)
    }

    fun toggleEmailNotifications(enabled: Boolean) {
        val user = _currentUser.value ?: return
        val updatedUser = user.copy(emailNotificationsEnabled = enabled)
        _currentUser.value = updatedUser
        _users.value = _users.value + (user.id to updatedUser)
        saveList("users.json", _users.value.values.toList(), userListAdapter)
        updateFeeds(updatedUser)
    }

    fun togglePushNotifications(enabled: Boolean) {
        val user = _currentUser.value ?: return
        val updatedUser = user.copy(pushNotificationsEnabled = enabled)
        _currentUser.value = updatedUser
        _users.value = _users.value + (user.id to updatedUser)
        saveList("users.json", _users.value.values.toList(), userListAdapter)
        updateFeeds(updatedUser)
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
        saveList("notifications.json", emptyList(), notificationListAdapter)
    }

    fun markNotificationAsRead(id: String) {
        val updated = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        _notifications.value = updated
        saveList("notifications.json", updated, notificationListAdapter)
    }

    suspend fun sendNotificationAlert(
        targetUserId: String,
        title: String,
        message: String,
        type: String = "push"
    ) {
        if (targetUserId.isEmpty()) return
        val alertId = "notif_${UUID.randomUUID().toString().take(8)}"
        val alert = NotificationAlert(
            id = alertId,
            userId = targetUserId,
            title = title,
            message = message,
            type = type,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )

        // If target is current logged in user, update local flow immediately
        if (targetUserId == _currentUser.value?.id) {
            _notifications.value = listOf(alert) + _notifications.value
            saveList("notifications.json", _notifications.value, notificationListAdapter)
        }

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("notifications")?.document(alertId)?.set(alert.toMap())?.awaitTask(4000L)
                Log.d("SawaariShare", "Notification alert sent to Firestore for $targetUserId")
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to send notification alert to Firestore: ${e.message}")
            }
        }
    }

    // --- Trip Matching (TripMatch Creation & Management) ---

    suspend fun createTripMatch(offerId: String, requestId: String): Result<String> = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Not logged in"))
        val offer = _tripOffers.value[offerId] ?: return@withContext Result.failure(Exception("Offer not found"))
        val request = _rideRequests.value[requestId] ?: return@withContext Result.failure(Exception("Request not found"))

        // Validate offer and request are compatible
        if (offer.status != "active" && offer.status != "full") {
            return@withContext Result.failure(Exception("Offer is no longer active"))
        }
        if (offer.seatsLeft < request.seatsNeeded) {
            return@withContext Result.failure(Exception("Not enough seats available"))
        }
        if (request.status != "active") {
            return@withContext Result.failure(Exception("Request is no longer active"))
        }

        // Check if already matched
        val existingMatch = _tripMatches.value.values.find {
            it.offerId == offerId && it.requestId == requestId
        }
        if (existingMatch != null) {
            return@withContext Result.failure(Exception("Already matched"))
        }

        // Create new match (driver accepts request)
        val matchId = "match_${UUID.randomUUID().toString().take(8)}"
        val match = TripMatch(
            id = matchId,
            offerId = offerId,
            requestId = requestId,
            hostId = offer.hostId,
            riderId = request.riderId,
            riderName = request.riderName,
            riderRating = request.riderRating,
            contribution = offer.costPerRider * request.seatsNeeded,
            status = "pending", // pending → accepted → completed/cancelled
            timestamp = System.currentTimeMillis()
        )

        _tripMatches.value = _tripMatches.value + (matchId to match)
        saveList("trip_matches.json", _tripMatches.value.values.toList(), tripMatchListAdapter)

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("trip_matches")?.document(matchId)?.set(
                    mapOf(
                        "id" to match.id,
                        "offerId" to match.offerId,
                        "requestId" to match.requestId,
                        "hostId" to match.hostId,
                        "riderId" to match.riderId,
                        "riderName" to match.riderName,
                        "riderRating" to match.riderRating,
                        "contribution" to match.contribution,
                        "status" to match.status,
                        "timestamp" to match.timestamp
                    )
                )?.awaitTask()
                Log.d("SawaariShare", "TripMatch created: $matchId")
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to create TripMatch in Firebase: ${e.message}")
            }
        }

        // System message in chat
        sendSystemMessage(matchId, "Match created! Waiting for driver to confirm.")

        // Notify passenger that driver is interested
        sendNotificationAlert(
            targetUserId = request.riderId,
            title = "Driver Interested! 🚗",
            message = "${offer.hostName} is interested in your ${request.origin} → ${request.destination} request for \$${match.contribution}",
            type = "match"
        )

        updateFeeds(_currentUser.value)
        return@withContext Result.success(matchId)
    }

    suspend fun getUserMatches(userId: String = _currentUser.value?.id ?: ""): Result<Pair<List<TripMatch>, List<TripMatch>>> = withContext(Dispatchers.IO) {
        if (userId.isEmpty()) {
            return@withContext Result.failure(Exception("User ID required"))
        }

        val hostedMatches = _tripMatches.value.values.filter { it.hostId == userId }
        val joinedMatches = _tripMatches.value.values.filter { it.riderId == userId }

        // Fetch from Firestore if available
        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("trip_matches")?.whereEqualTo("hostId", userId)?.get()?.awaitTask()?.documents?.mapNotNull {
                    it.toObject(TripMatch::class.java)
                }?.let { fbHosted ->
                    _tripMatches.value = _tripMatches.value + fbHosted.associateBy { it.id }
                }
            } catch (e: Exception) {
                Log.d("SawaariShare", "Could not fetch hosted matches from Firebase: ${e.message}")
            }
        }

        return@withContext Result.success(Pair(hostedMatches, joinedMatches))
    }

    fun getTripMatchById(matchId: String): TripMatch? {
        return _tripMatches.value[matchId]
    }

    suspend fun cancelMatch(matchId: String, reason: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        val match = _tripMatches.value[matchId] ?: return@withContext Result.failure(Exception("Match not found"))
        val user = _currentUser.value ?: return@withContext Result.failure(Exception("Not logged in"))

        // Only requester or host can cancel
        if (user.id != match.riderId && user.id != match.hostId) {
            return@withContext Result.failure(Exception("Only host or rider can cancel"))
        }

        // If already accepted, revert seat count
        if (match.status == "accepted") {
            val offer = _tripOffers.value[match.offerId]
            val request = _rideRequests.value[match.requestId]
            if (offer != null && request != null) {
                val updatedOffer = offer.copy(
                    seatsLeft = (offer.seatsLeft + request.seatsNeeded).coerceAtMost(offer.totalSeats),
                    passengers = offer.passengers - match.riderId,
                    passengerNames = offer.passengerNames - match.riderName,
                    status = if (offer.status == "full") "active" else offer.status
                )
                _tripOffers.value = _tripOffers.value + (offer.id to updatedOffer)
                saveList("trip_offers.json", _tripOffers.value.values.toList(), tripOfferListAdapter)

                if (isFirebaseEnabled && firebaseFirestore != null) {
                    try {
                        firebaseFirestore?.collection("trip_offers")?.document(offer.id)?.set(updatedOffer)?.awaitTask()
                    } catch (e: Exception) {
                        Log.e("SawaariShare", "Failed to sync seat count revert: ${e.message}")
                    }
                }
            }
        }

        val updatedMatch = match.copy(status = "cancelled")
        _tripMatches.value = _tripMatches.value + (matchId to updatedMatch)
        saveList("trip_matches.json", _tripMatches.value.values.toList(), tripMatchListAdapter)

        if (isFirebaseEnabled && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("trip_matches")?.document(matchId)?.set(
                    mapOf("status" to "cancelled", "id" to match.id, "offerId" to match.offerId, "requestId" to match.requestId, "hostId" to match.hostId, "riderId" to match.riderId, "riderName" to match.riderName, "riderRating" to match.riderRating, "contribution" to match.contribution, "timestamp" to match.timestamp)
                )?.awaitTask()
            } catch (e: Exception) {
                Log.e("SawaariShare", "Failed to sync match cancellation: ${e.message}")
            }
        }

        sendSystemMessage(matchId, "Match cancelled by ${user.displayName}${if (reason.isNotEmpty()) ": $reason" else ""}")

        val otherUserId = if (user.id == match.hostId) match.riderId else match.hostId
        val otherUserName = if (user.id == match.hostId) "Host" else match.riderName
        sendNotificationAlert(
            targetUserId = otherUserId,
            title = "$otherUserName Cancelled Match ❌",
            message = "Your trip from ${_tripOffers.value[match.offerId]?.origin} has been cancelled.",
            type = "match"
        )

        updateFeeds(_currentUser.value)
        return@withContext Result.success(Unit)
    }

    suspend fun getActiveMatches(): List<TripMatch> {
        return _tripMatches.value.values.filter {
            it.status == "pending" || it.status == "accepted"
        }.sortedBy { it.timestamp }.reversed()
    }

    suspend fun getMatchConversation(matchId: String): Flow<List<Message>> = withContext(Dispatchers.IO) {
        // Real-time message flow for a specific match
        return@withContext MutableStateFlow<List<Message>>(emptyList()).apply {
            scope.launch {
                _messages.collect { allMessages ->
                    value = allMessages.values
                        .filter { it.matchId == matchId }
                        .sortedBy { it.timestamp }
                }
            }
        }
    }

    suspend fun markMessagesAsRead(matchId: String, readUntilTimestamp: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val messages = _messages.value.values.filter {
            it.matchId == matchId && it.timestamp <= readUntilTimestamp
        }

        // Mark notification alerts as read
        _notifications.value = _notifications.value.map {
            if (it.type == "new_message" && it.timestamp <= readUntilTimestamp) {
                it.copy(isRead = true)
            } else it
        }
        saveList("notifications.json", _notifications.value, notificationListAdapter)
    }

    suspend fun getMatchDetails(matchId: String): Result<MatchDetails> = withContext(Dispatchers.IO) {
        val match = _tripMatches.value[matchId] ?: return@withContext Result.failure(Exception("Match not found"))
        val offer = _tripOffers.value[match.offerId]
        val request = _rideRequests.value[match.requestId]
        val host = _users.value[match.hostId]
        val rider = _users.value[match.riderId]

        if (offer == null || request == null) {
            return@withContext Result.failure(Exception("Match details incomplete"))
        }

        return@withContext Result.success(
            MatchDetails(
                match = match,
                offer = offer,
                request = request,
                hostProfile = host,
                riderProfile = rider
            )
        )
    }

    suspend fun fetchGoogleMapsMatrix(origin: String, destination: String): MapsRouteMatrixResult {
        val result = GoogleMapsGroundingService.getMapsDistanceAndRouteMatrix(origin, destination)
        return result.getOrDefault(
            MapsRouteMatrixResult(
                distanceText = "~5.0 miles",
                durationText = "~15 mins drive",
                routeSummary = "Driving route connecting $origin and $destination",
                pickupRecommendation = "Designated campus hub at $origin",
                dropoffRecommendation = "Main entrance dropoff at $destination",
                universityContext = "University commuter route",
                fullGroundedText = "Google Maps route info for $origin to $destination."
            )
        )
    }

    suspend fun syncDataWithFirestore(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isFirebaseEnabled || firebaseFirestore == null) {
            return@withContext Result.success(Unit)
        }

        try {
            // Force refresh all collections from Firestore
            val offers = firebaseFirestore?.collection("trip_offers")?.get()?.awaitTask()
                ?.documents?.mapNotNull { it.toTripOfferSafe() } ?: emptyList()
            val requests = firebaseFirestore?.collection("ride_requests")?.get()?.awaitTask()
                ?.documents?.mapNotNull { it.toRideRequestSafe() } ?: emptyList()
            val matches = firebaseFirestore?.collection("trip_matches")?.get()?.awaitTask()
                ?.documents?.mapNotNull { it.toObject(TripMatch::class.java) } ?: emptyList()
            val messages = firebaseFirestore?.collection("messages")?.get()?.awaitTask()
                ?.documents?.mapNotNull { it.toMessageSafe() } ?: emptyList()

            _tripOffers.value = offers.associateBy { it.id }
            _rideRequests.value = requests.associateBy { it.id }
            _tripMatches.value = matches.associateBy { it.id }
            _messages.value = messages.associateBy { it.id }

            saveList("trip_offers.json", _tripOffers.value.values.toList(), tripOfferListAdapter)
            saveList("ride_requests.json", _rideRequests.value.values.toList(), rideRequestListAdapter)
            saveList("trip_matches.json", _tripMatches.value.values.toList(), tripMatchListAdapter)
            saveList("messages.json", _messages.value.values.toList(), messageListAdapter)

            _lastSyncTime.value = System.currentTimeMillis()
            updateFeeds(_currentUser.value)

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to sync with Firestore: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    // --- Firebase Storage (Profile Pictures) ---

    suspend fun resizeImage(inputFile: File, maxWidth: Int = 512, maxHeight: Int = 512): Result<File> = withContext(Dispatchers.IO) {
        try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(inputFile.absolutePath)
                ?: return@withContext Result.failure(Exception("Failed to decode image"))

            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (newWidth, newHeight) = if (aspectRatio > 1) {
                maxWidth to (maxWidth / aspectRatio).toInt()
            } else {
                (maxHeight * aspectRatio).toInt() to maxHeight
            }

            val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

            val outputFile = File(context.cacheDir, "resized_${System.currentTimeMillis()}.jpg")
            val fos = java.io.FileOutputStream(outputFile)
            resizedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fos)
            fos.close()

            bitmap.recycle()
            resizedBitmap.recycle()

            return@withContext Result.success(outputFile)
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to resize image: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    suspend fun uploadProfilePicture(userId: String, imageFile: File): Result<String> {
        if (!isFirebaseEnabled) {
            // Fallback: return local path
            return Result.success(imageFile.absolutePath)
        }
        return uploadProfilePicture(userId, android.net.Uri.fromFile(imageFile))
    }

    /**
     * Uri overload used by the profile screen's image picker, which hands back a content:// Uri.
     * Firebase Storage accepts a Uri directly, so no temporary file copy is needed.
     */
    suspend fun uploadProfilePicture(userId: String, imageUri: android.net.Uri): Result<String> = withContext(Dispatchers.IO) {
        if (!isFirebaseEnabled) {
            return@withContext Result.success(imageUri.toString())
        }

        try {
            val storageRef = firebaseStorage?.reference?.child("profile_pictures/$userId.jpg")
                ?: return@withContext Result.failure(Exception("Storage not initialized"))

            // Upload to Firebase Storage
            val uploadTask = storageRef.putFile(imageUri)
            uploadTask.awaitTask()

            // Get download URL
            val downloadUrl = storageRef.downloadUrl.awaitTask()

            // Update user profile with avatar URL
            val updatedUser = _currentUser.value?.copy(avatarUrl = downloadUrl.toString())
            if (updatedUser != null) {
                firebaseFirestore?.collection("users")?.document(userId)
                    ?.update("avatarUrl", downloadUrl.toString())
                    ?.awaitTask()

                _currentUser.value = updatedUser
                _users.value = _users.value.toMutableMap().apply {
                    put(userId, updatedUser)
                }
                saveList("users.json", _users.value.values.toList(), userListAdapter)
            }

            return@withContext Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to upload profile picture: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    suspend fun deleteProfilePicture(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isFirebaseEnabled) {
            return@withContext Result.success(Unit)
        }

        try {
            val storageRef = firebaseStorage?.reference?.child("profile_pictures/$userId.jpg")
                ?: return@withContext Result.failure(Exception("Storage not initialized"))

            storageRef.delete().awaitTask()

            // Clear avatar URL from user profile
            val updatedUser = _currentUser.value?.copy(avatarUrl = "")
            if (updatedUser != null) {
                firebaseFirestore?.collection("users")?.document(userId)
                    ?.update("avatarUrl", "")
                    ?.awaitTask()

                _currentUser.value = updatedUser
                _users.value = _users.value.toMutableMap().apply {
                    put(userId, updatedUser)
                }
                saveList("users.json", _users.value.values.toList(), userListAdapter)
            }

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SawaariShare", "Failed to delete profile picture: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    fun logout() {
        stopRealtimeListeners()
        _currentUser.value = null
    }
}

suspend fun <T> Task<T>.awaitTask(timeoutMs: Long = 4000L): T = withTimeout(timeoutMs) {
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
    }
}
