package com.splitcruiser.app.data

/**
 * `Result`-returning wrappers for the Android side.
 *
 * The shared repository throws instead of returning `kotlin.Result`, because `Result` is an inline
 * value class and Kotlin/Native does not export those to Objective-C in a form Swift can use — a
 * `Result`-returning API would compile and then be unusable from iOS.
 *
 * Android has no such constraint and its ViewModel was written against `Result`, so these restore
 * that shape. They live in androidMain, so they never reach the generated Swift header.
 */

suspend fun SplitCruiserRepository.signUpWithEmailResult(email: String, password: String): Result<Boolean> =
    runCatching { signUpWithEmail(email, password) }

suspend fun SplitCruiserRepository.logInWithEmailResult(email: String, password: String): Result<Boolean> =
    runCatching { logInWithEmail(email, password) }

suspend fun SplitCruiserRepository.signInWithGoogleResult(googleIdToken: String): Result<Boolean> =
    runCatching { signInWithGoogle(googleIdToken) }

suspend fun SplitCruiserRepository.createUserProfileResult(
    name: String,
    lastInitial: String,
    communityId: String,
    homeArea: String,
    contact: ContactDetails,
    vehicle: Vehicle?,
): Result<Unit> = runCatching {
    createUserProfile(name, lastInitial, communityId, homeArea, contact, vehicle)
}

suspend fun SplitCruiserRepository.saveContactDetailsResult(details: ContactDetails): Result<Unit> =
    runCatching { saveContactDetails(details) }

suspend fun SplitCruiserRepository.updateUserProfileDetailsResult(
    name: String,
    lastInitial: String,
    collegeName: String,
    avatarUrl: String,
    verifiedEmail: String,
): Result<Unit> = runCatching {
    updateUserProfileDetails(name, lastInitial, collegeName, avatarUrl, verifiedEmail)
}

suspend fun SplitCruiserRepository.verifyCollegeEmailResult(email: String): Result<Unit> =
    runCatching { verifyCollegeEmail(email) }

suspend fun SplitCruiserRepository.postTripOfferResult(offer: TripOffer): Result<Unit> =
    runCatching { postTripOffer(offer) }

suspend fun SplitCruiserRepository.postRideRequestResult(request: RideRequest): Result<Unit> =
    runCatching { postRideRequest(request) }

suspend fun SplitCruiserRepository.joinTripOfferDirectResult(offerId: String): Result<Unit> =
    runCatching { joinTripOfferDirect(offerId) }

suspend fun SplitCruiserRepository.requestSeatOnOfferResult(
    offerId: String,
    contribution: Double,
): Result<TripMatch> = runCatching { requestSeatOnOffer(offerId, contribution) }

suspend fun SplitCruiserRepository.offerSeatForRequestResult(
    requestId: String,
    offerId: String,
    contribution: Double,
): Result<TripMatch> = runCatching { offerSeatForRequest(requestId, offerId, contribution) }

suspend fun SplitCruiserRepository.validateAndCreateMatchResult(
    offerId: String,
    requestId: String,
    contribution: Double,
): Result<TripMatch> = runCatching { validateAndCreateMatch(offerId, requestId, contribution) }

suspend fun SplitCruiserRepository.updateTripOfferStatusResult(
    offerId: String,
    newStatus: String,
): Result<Unit> = runCatching { updateTripOfferStatus(offerId, newStatus) }

suspend fun SplitCruiserRepository.updateRideRequestStatusResult(
    requestId: String,
    newStatus: String,
): Result<Unit> = runCatching { updateRideRequestStatus(requestId, newStatus) }

suspend fun SplitCruiserRepository.submitRatingResult(
    toUserId: String,
    ratingValue: Float,
    comment: String,
): Result<Unit> = runCatching { submitRating(toUserId, ratingValue, comment) }

suspend fun SplitCruiserRepository.blockUserResult(blockedUserId: String): Result<Unit> =
    runCatching { blockUser(blockedUserId) }

suspend fun SplitCruiserRepository.unblockUserResult(blockedUserId: String): Result<Unit> =
    runCatching { unblockUser(blockedUserId) }

suspend fun SplitCruiserRepository.sendMessageResult(matchId: String, text: String): Result<Unit> =
    runCatching { sendMessage(matchId, text) }

suspend fun SplitCruiserRepository.uploadProfilePictureResult(
    userId: String,
    bytes: ByteArray,
): Result<String> = runCatching { uploadProfilePicture(userId, bytes) }

/** Keeps the `Pair` shape `MainViewModel.refreshMyTrips()` was written against. */
suspend fun SplitCruiserRepository.fetchMyTripsFromFirestore(): Result<Pair<List<TripOffer>, List<TripOffer>>> =
    runCatching { fetchMyTrips().let { it.hosted to it.joined } }
