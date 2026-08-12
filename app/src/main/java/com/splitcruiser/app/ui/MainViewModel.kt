package com.splitcruiser.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitcruiser.app.auth.GoogleSignInCancelledException
import com.splitcruiser.app.auth.requestGoogleIdToken
import com.splitcruiser.app.data.ContactDetails
import com.splitcruiser.app.data.FirebaseConfig
import com.splitcruiser.app.data.Message
import com.splitcruiser.app.data.MessageType
import com.splitcruiser.app.data.NotificationAlert
import com.splitcruiser.app.data.ProfileImages
import com.splitcruiser.app.data.RideRequest
import com.splitcruiser.app.data.SplitCruiserRepository
import com.splitcruiser.app.data.TripMatch
import com.splitcruiser.app.data.TripOffer
import com.splitcruiser.app.data.User
import com.splitcruiser.app.data.Vehicle
import com.splitcruiser.app.data.acceptRideRequestDirectResult
import com.splitcruiser.app.data.blockUserResult
import com.splitcruiser.app.data.confirmPickupProposalResult
import com.splitcruiser.app.data.createUserProfileResult
import com.splitcruiser.app.data.fetchMyTripsFromFirestore
import com.splitcruiser.app.data.firebase.SharedPreferencesStore
import com.splitcruiser.app.data.joinTripOfferDirectResult
import com.splitcruiser.app.data.logInWithEmailResult
import com.splitcruiser.app.data.offerSeatForRequestResult
import com.splitcruiser.app.data.postRideRequestResult
import com.splitcruiser.app.data.postTripOfferResult
import com.splitcruiser.app.data.requestSeatOnOfferResult
import com.splitcruiser.app.data.sendMessageResult
import com.splitcruiser.app.data.sendPickupProposalResult
import com.splitcruiser.app.data.signInWithGoogleResult
import com.splitcruiser.app.data.signUpWithEmailResult
import com.splitcruiser.app.data.submitRatingResult
import com.splitcruiser.app.data.suggestedContributionResult
import com.splitcruiser.app.data.updateRideRequestStatusResult
import com.splitcruiser.app.data.updateTripOfferStatusResult
import com.splitcruiser.app.data.updateUserProfileDetailsResult
import com.splitcruiser.app.data.uploadProfilePictureResult
import com.splitcruiser.app.data.validateAndCreateMatchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * The backend now lives in `:shared`, so iOS runs this same code. The `*Result` extensions
     * imported above are Android-only wrappers: the shared API throws, because `kotlin.Result`
     * cannot be exported to Swift.
     */
    val repository = SplitCruiserRepository(
        FirebaseConfig.fromBuild(),
        SharedPreferencesStore(application.applicationContext),
    )

    // --- State streams mapped from the repository ---
    val currentUser: StateFlow<User?> = repository.currentUser
    val activeOffers: StateFlow<List<TripOffer>> = repository.activeOffers
    val activeRequests: StateFlow<List<RideRequest>> = repository.activeRequests
    val myRideRequests: StateFlow<List<RideRequest>> = repository.myRideRequests
    val userMatches: StateFlow<List<TripMatch>> = repository.userMatches
    val notifications: StateFlow<List<NotificationAlert>> = repository.notifications

    /** What onboarding stored: the home address a ride request prefills from. */
    val contactDetails: StateFlow<ContactDetails?> = repository.contactDetails

    private val _hostedRides = MutableStateFlow<List<TripOffer>>(emptyList())
    val hostedRides: StateFlow<List<TripOffer>> = _hostedRides.asStateFlow()

    private val _joinedRides = MutableStateFlow<List<TripOffer>>(emptyList())
    val joinedRides: StateFlow<List<TripOffer>> = _joinedRides.asStateFlow()

    init {
        // Restores a stored session and starts the polling refreshers.
        repository.start()

        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    refreshMyTrips()
                } else {
                    _hostedRides.value = emptyList()
                    _joinedRides.value = emptyList()
                }
            }
        }
    }

    override fun onCleared() {
        repository.stop()
        super.onCleared()
    }

    fun refreshMyTrips() {
        viewModelScope.launch {
            beginLoading("Refreshing your trips…")
            try {
                loadMyTrips()
            } finally {
                endLoading()
            }
        }
    }

    /**
     * The pull-to-refresh gesture, matching iOS's `.refreshable`.
     *
     * Deliberately not routed through [beginLoading]: the gesture draws its own indicator, and
     * raising the full-screen overlay on top of it would hide the list the user just pulled.
     */
    fun refreshFeeds() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                runCatching { repository.refreshNow() }
                    .onFailure { _uiError.value = it.message ?: "Could not refresh." }
                loadMyTrips()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun loadMyTrips() {
        try {
            val result = repository.fetchMyTripsFromFirestore()
            if (result.isSuccess) {
                val pair = result.getOrNull()
                if (pair != null) {
                    _hostedRides.value = pair.first
                    _joinedRides.value = pair.second
                }
            } else {
                _uiError.value = result.exceptionOrNull()?.message ?: "Failed to load travel schedule."
            }
        } catch (e: Exception) {
            _uiError.value = e.message ?: "Error retrieving trips."
        }
    }

    // --- UI local view state ---
    var currentMode by mutableStateOf("Rider") // "Rider" or "Host"
        private set

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * What the full-screen loader should say. Every action sets its own; the overlay used to be
     * hardcoded to "Securing your ride…" no matter what was actually in flight.
     */
    private val _loadingMessage = MutableStateFlow(DEFAULT_LOADING_MESSAGE)
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    /** Separate from [isLoading]: the pull-to-refresh gesture draws its own indicator. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError.asStateFlow()

    fun switchMode(mode: String) {
        if (mode == "Rider" || mode == "Host") {
            currentMode = mode
        }
    }

    fun clearError() {
        _uiError.value = null
    }

    fun setError(message: String) {
        _uiError.value = message
    }

    // --- Auth ---
    fun loginWithEmail(email: String, password: String, onFinished: (isNewUser: Boolean) -> Unit) {
        viewModelScope.launch {
            beginLoading("Logging you in…")
            try {
                val result = repository.logInWithEmailResult(email, password)
                result.fold(
                    onSuccess = { isNewUser -> onFinished(isNewUser) },
                    onFailure = { _uiError.value = it.message ?: "Failed to log in." },
                )
            } finally {
                endLoading()
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, onFinished: (isNewUser: Boolean) -> Unit) {
        viewModelScope.launch {
            beginLoading("Creating your account…")
            try {
                val result = repository.signUpWithEmailResult(email, password)
                result.fold(
                    onSuccess = { isNewUser -> onFinished(isNewUser) },
                    onFailure = { _uiError.value = it.message ?: "Failed to sign up." },
                )
            } finally {
                endLoading()
            }
        }
    }

    /**
     * [activityContext] must be the Activity: Credential Manager shows a bottom sheet, so the
     * application context this ViewModel already holds is not usable here.
     *
     * A cancelled picker is not an error — the user closed a sheet — so it leaves no message.
     */
    fun signInWithGoogle(activityContext: Context, onFinished: (isNewUser: Boolean) -> Unit) {
        viewModelScope.launch {
            beginLoading("Signing you in with Google…")
            try {
                val idToken = requestGoogleIdToken(activityContext, repository.googleWebClientId)
                repository.signInWithGoogleResult(idToken).fold(
                    onSuccess = { isNewUser -> onFinished(isNewUser) },
                    onFailure = { _uiError.value = it.message ?: "Failed to sign in with Google." },
                )
            } catch (e: GoogleSignInCancelledException) {
                // Deliberately silent.
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Failed to sign in with Google."
            } finally {
                endLoading()
            }
        }
    }

    fun completeProfile(
        name: String,
        lastInitial: String,
        homeArea: String,
        contact: ContactDetails,
        vehicle: Vehicle?,
        onSuccess: () -> Unit
    ) {
        runGuarded(
            block = {
                repository.createUserProfileResult(name, lastInitial, homeArea, contact, vehicle)
            },
            fallbackMessage = "Failed to setup profile.",
            loadingMessage = "Setting up your profile…",
            onSuccess = { onSuccess() },
        )
    }

    fun updateUserProfileDetails(
        name: String,
        lastInitial: String,
        avatarUrl: String,
        onSuccess: () -> Unit
    ) {
        runGuarded(
            block = {
                repository.updateUserProfileDetailsResult(name, lastInitial, avatarUrl)
            },
            fallbackMessage = "Failed to update profile.",
            loadingMessage = "Saving your profile…",
            onSuccess = { onSuccess() },
        )
    }

    // --- Core ride matching and creation ---

    fun postOffer(offer: TripOffer, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.postTripOfferResult(offer) },
            fallbackMessage = "Failed to post offer.",
            loadingMessage = "Posting your ride offer…",
            onSuccess = {
                refreshMyTrips()
                onSuccess()
            },
        )
    }

    fun postRequest(request: RideRequest, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.postRideRequestResult(request) },
            fallbackMessage = "Failed to post request.",
            loadingMessage = "Posting your ride request…",
            onSuccess = { onSuccess() },
        )
    }

    fun cancelRideRequest(requestId: String, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.updateRideRequestStatusResult(requestId, "cancelled") },
            fallbackMessage = "Failed to cancel ride request.",
            loadingMessage = "Cancelling your request…",
            onSuccess = { onSuccess() },
        )
    }

    /**
     * A rider asking for a seat. The backing ride request is created by the repository, which is
     * the point: the screens used to invent an id from the clock, and two riders joining in the
     * same 17-minute window could collide on it.
     */
    fun requestSeat(offerId: String, contribution: Double, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.requestSeatOnOfferResult(offerId, contribution) },
            fallbackMessage = "Failed to request a seat.",
            loadingMessage = "Securing your ride…",
            onSuccess = { onSuccess() },
        )
    }

    /** The host offering one of their own rides to a rider who posted a request. */
    fun offerSeat(requestId: String, offerId: String, contribution: Double, onSuccess: (TripMatch) -> Unit) {
        runGuarded(
            block = { repository.offerSeatForRequestResult(requestId, offerId, contribution) },
            fallbackMessage = "Failed to offer the ride.",
            loadingMessage = "Offering the seat…",
            onSuccess = { match ->
                refreshMyTrips()
                onSuccess(match)
            },
        )
    }

    /**
     * A driver taking a rider's request without having posted a ride of their own.
     *
     * [offerSeat] needs an offer id the caller already owns, so a driver with nothing posted was
     * shown "Post a ride first, then offer it here" — a dead end asking them to do the app's
     * bookkeeping. The shared side mints the backing offer instead.
     */
    fun acceptRequestDirect(requestId: String, contribution: Double, onSuccess: (TripMatch) -> Unit) {
        runGuarded(
            block = { repository.acceptRideRequestDirectResult(requestId, contribution) },
            fallbackMessage = "Failed to accept the ride request.",
            loadingMessage = "Offering the seat…",
            onSuccess = { match ->
                refreshMyTrips()
                onSuccess(match)
            },
        )
    }

    /**
     * What to prefill the contribution field with when accepting directly. Silent on failure — a
     * missing suggestion means an empty field, not a blocked accept.
     */
    fun suggestedContribution(request: RideRequest, onResult: (Double) -> Unit) {
        viewModelScope.launch {
            onResult(repository.suggestedContributionResult(request).getOrDefault(0.0))
        }
    }

    /**
     * Who this user has already rated, so the rating form stops offering them.
     *
     * A plain cache read — the repository keeps the list warm from `refreshNow()` and updates it
     * optimistically on submit. Callers must hold it in state and re-read after submitting, or
     * Compose has nothing to recompose on.
     */
    fun ratedUserIds(): List<String> = repository.getRatedUserIds()

    fun requestJoin(offerId: String, requestId: String, contribution: Double, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.validateAndCreateMatchResult(offerId, requestId, contribution) },
            fallbackMessage = "Failed to join ride.",
            loadingMessage = "Securing your ride…",
            onSuccess = { onSuccess() },
        )
    }

    fun joinTripOfferDirect(offerId: String, onSuccess: (TripMatch) -> Unit) {
        runGuarded(
            block = { repository.joinTripOfferDirectResult(offerId) },
            fallbackMessage = "Failed to join the ride.",
            loadingMessage = "Securing your ride…",
            onSuccess = { match -> onSuccess(match) },
        )
    }

    fun updateTripOfferStatus(offerId: String, newStatus: String, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.updateTripOfferStatusResult(offerId, newStatus) },
            fallbackMessage = "Failed to update the ride status.",
            loadingMessage = "Updating the ride…",
            onSuccess = { onSuccess() },
        )
    }

    fun acceptMatch(matchId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.acceptMatch(matchId) }
                .onSuccess { onSuccess() }
                .onFailure { _uiError.value = it.message ?: "Failed to accept ride." }
        }
    }

    fun declineMatch(matchId: String) {
        viewModelScope.launch {
            runCatching { repository.declineMatch(matchId) }
                .onFailure { _uiError.value = it.message ?: "Failed to decline ride." }
        }
    }

    fun completeTrip(matchId: String) {
        viewModelScope.launch {
            runCatching { repository.completeTrip(matchId) }
                .onFailure { _uiError.value = it.message ?: "Failed to complete trip." }
        }
    }

    // --- Messaging ---

    fun getChatMessages(matchId: String): Flow<List<Message>> = repository.getChatMessages(matchId)

    /**
     * Tells the refresher to poll this conversation quickly (3s) while it is on screen.
     *
     * Pair every call with [closeChat]. This used to be a side effect inside [getChatMessages],
     * which meant it ran during composition and was never undone — so `openChatMatchId` stayed
     * pinned to the first conversation ever opened, for the life of the process.
     */
    fun openChat(matchId: String) {
        repository.openChat(matchId)
    }

    fun closeChat() {
        repository.openChat(null)
    }

    fun sendMessage(matchId: String, text: String) {
        viewModelScope.launch {
            repository.sendMessageResult(matchId, text)
                .onFailure { _uiError.value = it.message ?: "Message failed to send." }
        }
    }

    /** Proposes where to meet, where the ride ends, when, and what it costs. */
    fun sendPickupProposal(
        matchId: String,
        pickupAddress: String,
        dropoffAddress: String,
        pickupTime: String,
        contribution: Double,
    ) {
        viewModelScope.launch {
            repository
                .sendPickupProposalResult(matchId, pickupAddress, dropoffAddress, pickupTime, contribution)
                .onFailure { _uiError.value = it.message ?: "Could not send that proposal." }
        }
    }

    /**
     * Agrees to a proposal.
     *
     * Safe to call twice: the confirmation's document id is derived from the proposal, so a second
     * tap overwrites rather than posting another bubble. [onFinished] runs either way, so the card
     * can re-enable its button if the write failed.
     */
    fun confirmPickup(proposalMessageId: String, onFinished: () -> Unit) {
        viewModelScope.launch {
            repository.confirmPickupProposalResult(proposalMessageId)
                .onFailure { _uiError.value = it.message ?: "Could not confirm that pickup." }
            onFinished()
        }
    }

    // --- Ratings, blocks and safety ---

    fun submitRating(toUserId: String, rating: Float, comment: String, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.submitRatingResult(toUserId, rating, comment) },
            fallbackMessage = "Failed to submit rating.",
            loadingMessage = "Submitting your rating…",
            onSuccess = { onSuccess() },
        )
    }

    /**
     * Uploads a profile picture picked from the gallery. Returns true on success; the repository
     * updates [currentUser] with the new avatarUrl as a side effect.
     *
     * Decoding and resizing stay here because they are `android.graphics`; the shared client takes
     * bytes so iOS can hand it a UIImage's JPEG data instead.
     */
    suspend fun uploadProfilePicture(userId: String, imageUri: android.net.Uri): Boolean {
        val bytes = ProfileImages.readResizedJpeg(getApplication(), imageUri)
        if (bytes == null) {
            _uiError.value = "Could not read that image."
            return false
        }
        return repository.uploadProfilePictureResult(userId, bytes)
            .onFailure { _uiError.value = it.message }
            .isSuccess
    }

    fun blockUser(blockedUserId: String, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.blockUserResult(blockedUserId) },
            fallbackMessage = "Failed to block user.",
            loadingMessage = "Blocking this rider…",
            onSuccess = { onSuccess() },
        )
    }

    fun unblockUser(blockedUserId: String) {
        viewModelScope.launch {
            runCatching { repository.unblockUser(blockedUserId) }
                .onFailure { _uiError.value = it.message }
        }
    }

    fun getBlockedUsers(): List<User> = repository.getBlockedUsers()

    fun getUserPublicProfile(userId: String): User? = repository.getUserPublicProfile(userId)

    fun getTripOfferById(offerId: String): TripOffer? = repository.getTripOfferById(offerId)

    fun getRideRequestById(requestId: String): RideRequest? = repository.getRideRequestById(requestId)

    /** Cold-cache fallback for [getTripOfferById] — a network fetch that populates the cache. */
    fun fetchTripOffer(offerId: String, onResult: (TripOffer?) -> Unit) {
        viewModelScope.launch { onResult(runCatching { repository.fetchTripOffer(offerId) }.getOrNull()) }
    }

    /** Cold-cache fallback for [getRideRequestById]. */
    fun fetchRideRequest(requestId: String, onResult: (RideRequest?) -> Unit) {
        viewModelScope.launch { onResult(runCatching { repository.fetchRideRequest(requestId) }.getOrNull()) }
    }

    fun getVehicleInfo(userId: String): Vehicle? = repository.getVehicleInfo(userId)

    fun recordNoShow(userId: String) {
        viewModelScope.launch {
            runCatching { repository.recordNoShow(userId) }
                .onFailure { _uiError.value = it.message }
        }
    }

    fun toggleWomenOnlyFilter(enabled: Boolean) {
        viewModelScope.launch { repository.toggleWomenOnlyFilter(enabled) }
    }

    fun toggleEmailNotifications(enabled: Boolean) {
        viewModelScope.launch { repository.toggleEmailNotifications(enabled) }
    }

    fun togglePushNotifications(enabled: Boolean) {
        viewModelScope.launch { repository.togglePushNotifications(enabled) }
    }

    fun clearNotifications() {
        viewModelScope.launch { repository.clearNotifications() }
    }

    fun markNotificationAsRead(id: String) {
        viewModelScope.launch {
            runCatching { repository.markNotificationAsRead(id) }
        }
    }

    fun logout() {
        repository.logout()
    }

    /** The load/error/success dance every one of these actions repeated verbatim. */
    private fun <T> runGuarded(
        block: suspend () -> Result<T>,
        fallbackMessage: String,
        loadingMessage: String = DEFAULT_LOADING_MESSAGE,
        onSuccess: (T) -> Unit,
    ) {
        viewModelScope.launch {
            beginLoading(loadingMessage)
            try {
                block().fold(
                    onSuccess = { onSuccess(it) },
                    onFailure = { _uiError.value = it.message ?: fallbackMessage },
                )
            } finally {
                endLoading()
            }
        }
    }

    private fun beginLoading(message: String) {
        _loadingMessage.value = message
        _isLoading.value = true
    }

    private fun endLoading() {
        _isLoading.value = false
        _loadingMessage.value = DEFAULT_LOADING_MESSAGE
    }

    companion object {
        /** Neutral, so it reads sensibly for any action that does not set its own. */
        const val DEFAULT_LOADING_MESSAGE = "Just a moment…"
    }
}
