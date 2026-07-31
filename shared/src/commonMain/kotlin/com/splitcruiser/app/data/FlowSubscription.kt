package com.splitcruiser.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A cancellable observation, handed to Swift.
 *
 * `kotlinx.coroutines.Flow` does not export to Objective-C in any usable form, and coroutines is an
 * `implementation` dependency so it is not re-exported from the framework either. Android keeps
 * using the `StateFlow`s directly; iOS uses the `observe*` methods on the repository, which are
 * deliberately non-generic and take no default arguments — generics and Kotlin defaults both come
 * out unusable on the Swift side.
 */
class FlowSubscription internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

internal fun <T> Flow<T>.subscribeOnMain(onEach: (T) -> Unit): FlowSubscription =
    FlowSubscription(
        // Dispatchers.Main is the main queue on Darwin, so Swift can assign straight to @Published.
        CoroutineScope(Dispatchers.Main).launch { collect { onEach(it) } }
    )

/**
 * Argument-light builders for Swift.
 *
 * A Kotlin data class with defaults still requires *every* argument in the generated Swift
 * initialiser — `TripOffer` would need 23 and `User` 19. The repository fills in identity, geohash
 * and cost fields on post anyway, so these expose only what a form actually collects.
 *
 * An object rather than top-level functions: top-level Kotlin functions land in Swift on a synthetic
 * file class (`FlowSubscriptionKt`), which is not something a caller should have to know about.
 *
 * Named `make*`, not `new*`: a Kotlin/Native member whose Swift-exported selector starts with
 * `new` collides with Objective-C ARC's method-family naming convention (same family as `alloc`/
 * `init`/`copy`), and Swift's Clang importer silently declines to expose it as an ordinary method
 * — Xcode fails with "value of type 'RideFactory' has no member 'newTripOffer'", not a warning
 * about the naming. This was never caught locally: `:shared:compileCommonMainKotlinMetadata`
 * compiles common code without ever generating the Objective-C header, so nothing on Linux can
 * see this class of bug — only an actual `xcodebuild archive` on the macOS runner can.
 */
object RideFactory {

    fun makeTripOffer(
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
        exitLocation: String,
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
        exitLocation = exitLocation,
    )

    fun makeRideRequest(
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
        exitLocation: String,
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
        exitLocation = exitLocation,
    )
}
