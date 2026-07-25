package com.splitcruiser.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitcruiser.app.data.Community
import com.splitcruiser.app.data.Message
import com.splitcruiser.app.data.NotificationAlert
import com.splitcruiser.app.data.RideRequest
import com.splitcruiser.app.data.SawaariRepository
import com.splitcruiser.app.data.TripMatch
import com.splitcruiser.app.data.TripOffer
import com.splitcruiser.app.data.User
import com.splitcruiser.app.data.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SawaariRepository(application.applicationContext)

    // --- State Streams mapped from SawaariRepository ---
    val currentUser: StateFlow<User?> = repository.currentUser
    val activeOffers: StateFlow<List<TripOffer>> = repository.activeOffers
    val activeRequests: StateFlow<List<RideRequest>> = repository.activeRequests
    val myRideRequests: StateFlow<List<RideRequest>> = repository.myRideRequests
    val userMatches: StateFlow<List<TripMatch>> = repository.userMatches
    val allCommunities: StateFlow<List<Community>> = repository.allCommunities
    val notifications: StateFlow<List<NotificationAlert>> = repository.notifications

    private val _hostedRides = MutableStateFlow<List<TripOffer>>(emptyList())
    val hostedRides: StateFlow<List<TripOffer>> = _hostedRides.asStateFlow()

    private val _joinedRides = MutableStateFlow<List<TripOffer>>(emptyList())
    val joinedRides: StateFlow<List<TripOffer>> = _joinedRides.asStateFlow()

    init {
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

    // --- UI Local View State ---
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

    // --- Auth Flows ---
    fun loginWithEmail(email: String, password: String, onFinished: (isNewUser: Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.logInWithEmail(
                    email = email,
                    password = password,
                    onSuccess = { isNewUser ->
                        onFinished(isNewUser)
                    },
                    onFailure = { errorMsg ->
                        _uiError.value = errorMsg
                    }
                )
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Failed to log in."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, onFinished: (isNewUser: Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.signUpWithEmail(
                    email = email,
                    password = password,
                    onSuccess = { isNewUser ->
                        onFinished(isNewUser)
                    },
                    onFailure = { errorMsg ->
                        _uiError.value = errorMsg
                    }
                )
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Failed to sign up."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun redeemInviteCode(code: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.redeemInviteCode(code)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Invalid invite code."
                }
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Failed to redeem code."
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
        vehicle: Vehicle?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.createUserProfile(name, lastInitial, communityId, homeArea, vehicle)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to setup profile."
                }
            } catch (e: Exception) {
                _uiError.value = e.message ?: "An unexpected error occurred."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun verifyCollegeEmail(email: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.verifyCollegeEmail(email)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Verification failed."
                    onFailure(errMsg)
                }
            } catch (e: Exception) {
                onFailure(e.message ?: "An unexpected error occurred.")
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
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.updateUserProfileDetails(name, lastInitial, collegeName, avatarUrl, verifiedEmail)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to update profile."
                }
            } catch (e: Exception) {
                _uiError.value = e.message ?: "An unexpected error occurred."
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Core Ride matching and creation ---

    fun postOffer(offer: TripOffer, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.postTripOffer(offer)
                if (result.isSuccess) {
                    refreshMyTrips()
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to post offer."
                }
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Error posting offer."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun postRequest(request: RideRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.postRideRequest(request)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to post request."
                }
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Error posting request."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelRideRequest(requestId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.updateRideRequestStatus(requestId, "cancelled")
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to cancel ride request."
                }
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Error cancelling ride request."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun requestJoin(offerId: String, requestId: String, contribution: Double, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.validateAndCreateMatch(offerId, requestId, contribution)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to join ride."
                }
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Error joining ride."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun joinTripOfferDirect(offerId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.joinTripOfferDirect(offerId)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to join Sawaari."
                }
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Error joining Sawaari."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateTripOfferStatus(offerId: String, newStatus: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.updateTripOfferStatus(offerId, newStatus)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to update Sawaari status."
                }
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Error updating status."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptMatch(matchId: String) {
        viewModelScope.launch {
            try {
                repository.acceptMatch(matchId)
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Failed to accept ride."
            }
        }
    }

    fun declineMatch(matchId: String) {
        viewModelScope.launch {
            try {
                repository.declineMatch(matchId)
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Failed to decline ride."
            }
        }
    }

    fun completeTrip(matchId: String) {
        viewModelScope.launch {
            try {
                repository.completeTrip(matchId)
            } catch (e: Exception) {
                _uiError.value = e.message ?: "Failed to complete trip."
            }
        }
    }

    // --- Messaging ---

    fun getChatMessages(matchId: String): Flow<List<Message>> {
        return repository.getChatMessages(matchId)
    }

    fun sendMessage(matchId: String, text: String) {
        viewModelScope.launch {
            try {
                val result = repository.sendMessage(matchId, text)
                if (result.isFailure) {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Message failed to send."
                }
            } catch (e: Exception) {
                _uiError.value = e.message
            }
        }
    }

    // --- Ratings, Blocks and Safety ---

    fun submitRating(toUserId: String, rating: Float, comment: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.submitRating(toUserId, rating, comment)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to submit rating."
                }
            } catch (e: Exception) {
                _uiError.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Uploads a profile picture picked from the gallery. Returns true on success; the repository
     * updates [currentUser] with the new avatarUrl as a side effect.
     */
    suspend fun uploadProfilePicture(userId: String, imageUri: android.net.Uri): Boolean {
        return try {
            repository.uploadProfilePicture(userId, imageUri).isSuccess
        } catch (e: Exception) {
            _uiError.value = e.message
            false
        }
    }

    fun blockUser(blockedUserId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.blockUser(blockedUserId)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiError.value = result.exceptionOrNull()?.message ?: "Failed to block user."
                }
            } catch (e: Exception) {
                _uiError.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun unblockUser(blockedUserId: String) {
        viewModelScope.launch {
            try {
                repository.unblockUser(blockedUserId)
            } catch (e: Exception) {
                _uiError.value = e.message
            }
        }
    }

    fun getBlockedUsers(): List<User> {
        return repository.getBlockedUsers()
    }

    fun getUserPublicProfile(userId: String): User? {
        return repository.getUserPublicProfile(userId)
    }

    fun getTripOfferById(offerId: String): TripOffer? {
        return repository.getTripOfferById(offerId)
    }

    fun getVehicleInfo(userId: String): Vehicle? {
        return repository.getVehicleInfo(userId)
    }

    fun recordNoShow(userId: String) {
        repository.recordNoShow(userId)
    }

    fun toggleWomenOnlyFilter(enabled: Boolean) {
        repository.toggleWomenOnlyFilter(enabled)
    }

    fun toggleEmailNotifications(enabled: Boolean) {
        repository.toggleEmailNotifications(enabled)
    }

    fun togglePushNotifications(enabled: Boolean) {
        repository.togglePushNotifications(enabled)
    }

    fun clearNotifications() {
        repository.clearNotifications()
    }

    fun markNotificationAsRead(id: String) {
        repository.markNotificationAsRead(id)
    }

    fun logout() {
        repository.logout()
    }
}
