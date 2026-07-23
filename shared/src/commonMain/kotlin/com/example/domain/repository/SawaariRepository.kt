package com.example.domain.repository

import com.example.data.Message
import com.example.data.NotificationAlert
import com.example.data.RideRequest
import com.example.data.TripMatch
import com.example.data.TripOffer
import com.example.data.User
import kotlinx.coroutines.flow.StateFlow

interface SawaariRepository {
    // State Flows for reactive updates
    val currentUser: StateFlow<User?>
    val activeOffers: StateFlow<List<TripOffer>>
    val activeRequests: StateFlow<List<RideRequest>>
    val myRideRequests: StateFlow<List<RideRequest>>
    val userMatches: StateFlow<List<TripMatch>>
    val notifications: StateFlow<List<NotificationAlert>>

    // Authentication
    suspend fun signUpWithEmail(email: String, password: String, name: String, lastInitial: String): Result<User>
    suspend fun logInWithEmail(email: String, password: String): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun verifyCollegeEmail(userId: String, verificationCode: String): Result<Boolean>
    suspend fun redeemInviteCode(userId: String, code: String): Result<Boolean>

    // Trip Offers
    suspend fun postTripOffer(offer: TripOffer): Result<TripOffer>
    suspend fun updateTripOfferStatus(offerId: String, status: String): Result<Boolean>
    suspend fun fetchMyTripsFromFirestore(userId: String): Result<List<TripOffer>>
    suspend fun fetchActiveOffers(): Result<List<TripOffer>>

    // Ride Requests
    suspend fun postRideRequest(request: RideRequest): Result<RideRequest>
    suspend fun updateRideRequestStatus(requestId: String, status: String): Result<Boolean>
    suspend fun fetchActiveRequests(): Result<List<RideRequest>>
    suspend fun fetchMyRideRequests(userId: String): Result<List<RideRequest>>

    // Matching
    suspend fun validateAndCreateMatch(offerId: String, requestId: String, contribution: Double): Result<TripMatch>
    suspend fun joinTripOfferDirect(offerId: String): Result<TripMatch>
    suspend fun acceptMatch(matchId: String): Result<Boolean>
    suspend fun declineMatch(matchId: String): Result<Boolean>
    suspend fun completeTrip(matchId: String): Result<Boolean>
    suspend fun fetchMatchesForOffer(offerId: String): Result<List<TripMatch>>
    suspend fun fetchMatchesForRequest(requestId: String): Result<List<TripMatch>>

    // Messaging
    suspend fun sendMessage(matchId: String, senderId: String, senderName: String, text: String): Result<Message>
    suspend fun getChatMessages(matchId: String): Result<List<Message>>
    suspend fun markMessageAsRead(matchId: String, messageId: String): Result<Boolean>
    suspend fun deleteMessage(matchId: String, messageId: String): Result<Boolean>

    // Safety & Ratings
    suspend fun blockUser(userId: String, blockedUserId: String): Result<Boolean>
    suspend fun unblockUser(userId: String, blockedUserId: String): Result<Boolean>
    suspend fun getBlockedUsers(userId: String): Result<List<String>>
    suspend fun submitRating(fromUserId: String, toUserId: String, rating: Float, comment: String): Result<Boolean>
    suspend fun recordNoShow(userId: String, tripId: String): Result<Boolean>
    suspend fun getUserRating(userId: String): Result<Pair<Float, Int>>
    suspend fun getNoShowCount(userId: String): Result<Int>

    // Notifications
    suspend fun sendNotificationAlert(userId: String, title: String, message: String, type: String): Result<Boolean>
    suspend fun markNotificationAsRead(notificationId: String): Result<Boolean>
    suspend fun clearNotifications(userId: String): Result<Boolean>

    // User Profile
    suspend fun createUserProfile(user: User): Result<User>
    suspend fun updateUserProfileDetails(userId: String, name: String, avatarUrl: String, homeArea: String): Result<User>
    suspend fun getUserProfile(userId: String): Result<User?>

    // Preferences
    suspend fun toggleWomenOnlyFilter(userId: String, enabled: Boolean): Result<Boolean>
    suspend fun toggleEmailNotifications(userId: String, enabled: Boolean): Result<Boolean>
    suspend fun togglePushNotifications(userId: String, enabled: Boolean): Result<Boolean>
}
