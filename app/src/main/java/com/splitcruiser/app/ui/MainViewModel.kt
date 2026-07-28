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
import com.splitcruiser.app.data.Community
import com.splitcruiser.app.data.ContactDetails
import com.splitcruiser.app.data.FirebaseConfig
import com.splitcruiser.app.data.Message
import com.splitcruiser.app.data.NotificationAlert
import com.splitcruiser.app.data.ProfileImages
import com.splitcruiser.app.data.RideRequest
import com.splitcruiser.app.data.SplitCruiserRepository
import com.splitcruiser.app.data.TripMatch
import com.splitcruiser.app.data.TripOffer
import com.splitcruiser.app.data.User
import com.splitcruiser.app.data.Vehicle
import com.splitcruiser.app.data.blockUserResult
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
import com.splitcruiser.app.data.signInWithGoogleResult
import com.splitcruiser.app.data.signUpWithEmailResult
import com.splitcruiser.app.data.submitRatingResult
import com.splitcruiser.app.data.updateRideRequestStatusResult
import com.splitcruiser.app.data.updateTripOfferStatusResult
import com.splitcruiser.app.data.updateUserProfileDetailsResult
import com.splitcruiser.app.data.uploadProfilePictureResult
import com.splitcruiser.app.data.validateAndCreateMatchResult
import com.splitcruiser.app.data.verifyCollegeEmailResult
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
    val allCommunities: StateFlow<List<Community>> = repository.allCommunities
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
            _isLoading.value = true
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
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- UI local view state ---
    var currentMode by mutableStateOf("Rider") // "Rider" or "Host"
        private set

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
            _isLoading.value = true
            try {
                val result = repository.logInWithEmailResult(email, password)
                result.fold(
                    onSuccess = { isNewUser -> onFinished(isNewUser) },
                    onFailure = { _uiError.value = it.message ?: "Failed to log in." },
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, onFinished: (isNewUser: Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.signUpWithEmailResult(email, password)
                result.fold(
                    onSuccess = { isNewUser -> onFinished(isNewUser) },
                    onFailure = { _uiError.value = it.message ?: "Failed to sign up." },
                )
            } finally {
                _isLoading.value = false
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
            _isLoading.value = true
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
                _isLoading.value = false
            }
        }
    }

    fun completeProfile(
        name: String,
        lastInitial: String,
        communityId: String,
        homeArea: String,
        contact: ContactDetails,
        vehicle: Vehicle?,
        onSuccess: () -> Unit
    ) {
        runGuarded(
            block = {
                repository.createUserProfileResult(name, lastInitial, communityId, homeArea, contact, vehicle)
            },
            fallbackMessage = "Failed to setup profile.",
            onSuccess = { onSuccess() },
        )
    }

    fun verifyCollegeEmail(email: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.verifyCollegeEmailResult(email).fold(
                    onSuccess = { onSuccess() },
                    onFailure = { onFailure(it.message ?: "Verification failed.") },
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUserProfileDetails(
        name: String,
        lastInitial: String,
        collegeName: String,
        avatarUrl: String,
        verifiedEmail: String,
        onSuccess: () -> Unit
    ) {
        runGuarded(
            block = {
                repository.updateUserProfileDetailsResult(name, lastInitial, collegeName, avatarUrl, verifiedEmail)
            },
            fallbackMessage = "Failed to update profile.",
            onSuccess = { onSuccess() },
        )
    }

    // --- Core ride matching and creation ---

    fun postOffer(offer: TripOffer, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.postTripOfferResult(offer) },
            fallbackMessage = "Failed to post offer.",
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
            onSuccess = { onSuccess() },
        )
    }

    fun cancelRideRequest(requestId: String, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.updateRideRequestStatusResult(requestId, "cancelled") },
            fallbackMessage = "Failed to cancel ride request.",
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
            onSuccess = { onSuccess() },
        )
    }

    /** The host offering one of their own rides to a rider who posted a request. */
    fun offerSeat(requestId: String, offerId: String, contribution: Double, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.offerSeatForRequestResult(requestId, offerId, contribution) },
            fallbackMessage = "Failed to offer the ride.",
            onSuccess = {
                refreshMyTrips()
                onSuccess()
            },
        )
    }

    fun requestJoin(offerId: String, requestId: String, contribution: Double, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.validateAndCreateMatchResult(offerId, requestId, contribution) },
            fallbackMessage = "Failed to join ride.",
            onSuccess = { onSuccess() },
        )
    }

    fun joinTripOfferDirect(offerId: String, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.joinTripOfferDirectResult(offerId) },
            fallbackMessage = "Failed to join the ride.",
            onSuccess = { onSuccess() },
        )
    }

    fun updateTripOfferStatus(offerId: String, newStatus: String, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.updateTripOfferStatusResult(offerId, newStatus) },
            fallbackMessage = "Failed to update the ride status.",
            onSuccess = { onSuccess() },
        )
    }

    fun acceptMatch(matchId: String) {
        viewModelScope.launch {
            runCatching { repository.acceptMatch(matchId) }
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

    fun getChatMessages(matchId: String): Flow<List<Message>> {
        // Tells the refresher to poll this conversation quickly while it is on screen.
        repository.openChat(matchId)
        return repository.getChatMessages(matchId)
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

    // --- Ratings, blocks and safety ---

    fun submitRating(toUserId: String, rating: Float, comment: String, onSuccess: () -> Unit) {
        runGuarded(
            block = { repository.submitRatingResult(toUserId, rating, comment) },
            fallbackMessage = "Failed to submit rating.",
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
        onSuccess: (T) -> Unit,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                block().fold(
                    onSuccess = { onSuccess(it) },
                    onFailure = { _uiError.value = it.message ?: fallbackMessage },
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}
