package com.splitcruiser.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Lets Swift observe the repository.
 *
 * `kotlinx.coroutines.Flow` does not export to Objective-C in any usable form, and coroutines is an
 * `implementation` dependency so it is not re-exported from the framework either. Android keeps
 * using the `StateFlow`s directly; iOS gets these callback subscriptions, which hand back a handle
 * to cancel.
 *
 * The observe* functions below are deliberately non-generic and take no default arguments — generic
 * signatures and Kotlin defaults both come out unusable on the Swift side.
 */
class FlowSubscription internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

private fun <T> Flow<T>.subscribeOnMain(onEach: (T) -> Unit): FlowSubscription =
    FlowSubscription(
        // Dispatchers.Main is the main queue on Darwin, so Swift can assign straight to @Published.
        CoroutineScope(Dispatchers.Main).launch { collect { onEach(it) } }
    )

fun SplitCruiserRepository.observeCurrentUser(onChange: (User?) -> Unit): FlowSubscription =
    currentUser.subscribeOnMain(onChange)

fun SplitCruiserRepository.observeActiveOffers(onChange: (List<TripOffer>) -> Unit): FlowSubscription =
    activeOffers.subscribeOnMain(onChange)

fun SplitCruiserRepository.observeActiveRequests(onChange: (List<RideRequest>) -> Unit): FlowSubscription =
    activeRequests.subscribeOnMain(onChange)

fun SplitCruiserRepository.observeMyRideRequests(onChange: (List<RideRequest>) -> Unit): FlowSubscription =
    myRideRequests.subscribeOnMain(onChange)

fun SplitCruiserRepository.observeUserMatches(onChange: (List<TripMatch>) -> Unit): FlowSubscription =
    userMatches.subscribeOnMain(onChange)

fun SplitCruiserRepository.observeCommunities(onChange: (List<Community>) -> Unit): FlowSubscription =
    allCommunities.subscribeOnMain(onChange)

fun SplitCruiserRepository.observeNotifications(onChange: (List<NotificationAlert>) -> Unit): FlowSubscription =
    notifications.subscribeOnMain(onChange)

fun SplitCruiserRepository.observeConnection(onChange: (Boolean) -> Unit): FlowSubscription =
    isConnected.subscribeOnMain(onChange)

fun SplitCruiserRepository.observeChat(
    matchId: String,
    onChange: (List<Message>) -> Unit,
): FlowSubscription = getChatMessages(matchId).subscribeOnMain(onChange)

/**
 * Argument-light builders for Swift.
 *
 * A Kotlin data class with defaults still requires *every* argument in the generated Swift
 * initialiser — `TripOffer` would need 23 and `User` 19. The repository fills in the identity,
 * geohash and cost fields on post anyway, so these expose only what a form actually collects.
 */
fun newTripOffer(
    origin: String,
    destination: String,
    originLat: Double,
    originLng: Double,
    destLat: Double,
    destLng: Double,
    departureTime: Long,
    totalSeats: Int,
    costPerRider: Double,
    womenOnly: Boolean,
    vehicleInfo: String,
): TripOffer = TripOffer(
    origin = origin,
    destination = destination,
    originLat = originLat,
    originLng = originLng,
    destLat = destLat,
    destLng = destLng,
    departureTime = departureTime,
    totalSeats = totalSeats,
    seatsLeft = totalSeats,
    costPerRider = costPerRider,
    womenOnly = womenOnly,
    vehicleInfo = vehicleInfo,
)

fun newRideRequest(
    origin: String,
    destination: String,
    originLat: Double,
    originLng: Double,
    destLat: Double,
    destLng: Double,
    departureTime: Long,
    seatsNeeded: Int,
    notes: String,
    womenOnly: Boolean,
): RideRequest = RideRequest(
    origin = origin,
    destination = destination,
    originLat = originLat,
    originLng = originLng,
    destLat = destLat,
    destLng = destLng,
    departureTime = departureTime,
    seatsNeeded = seatsNeeded,
    notes = notes,
    womenOnly = womenOnly,
)
