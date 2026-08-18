package com.splitcruiser.app.data

import kotlinx.serialization.Serializable

// --- Core Models ---

@Serializable
data class User(
    val id: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val name: String = "",
    val lastInitial: String = "",
    val avatarUrl: String = "",
    val verifiedTier: String = "vouched", // "vouched" or "guest"
    val invitedBy: String = "",
    val ratingAvg: Float = 0.0f,
    val ratingCount: Int = 0,
    val noShowCount: Int = 0,
    val homeArea: String = "",
    val isWomenOnlyFilterEnabled: Boolean = false,
    val fcmToken: String = "",
    val emailNotificationsEnabled: Boolean = false,
    val pushNotificationsEnabled: Boolean = false
) {
    val displayName: String
        get() = if (lastInitial.isNotEmpty()) "$name $lastInitial." else name
}

/**
 * The details onboarding collects so a ride request can fill itself in.
 *
 * Kept in `users/{uid}/private/profile` rather than on the user document, because `users` is
 * world-readable — feeds show a host's name and rating — and a home address is nobody else's
 * business. The phone number stays on the public document: the trip detail screen has always
 * shown a matched host's number, and moving it here would break that without being asked.
 */
@Serializable
data class ContactDetails(
    val phoneNumber: String = "",
    val homeAddress: String = "",
    val homeLat: Double = 0.0,
    val homeLng: Double = 0.0,
) {
    /** False when onboarding was skipped or predates this, in which case nothing is prefilled. */
    val hasHomeLocation: Boolean
        get() = homeAddress.isNotBlank() && homeLat != 0.0 && homeLng != 0.0
}

@Serializable
data class Vehicle(
    val ownerId: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val color: String = "",
    val licensePlate: String = ""
)

@Serializable
data class TripOffer(
    val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val hostRating: Float = 0.0f,
    val origin: String = "",
    val destination: String = "",
    val originLat: Double = 0.0,
    val originLng: Double = 0.0,
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val originGeohash: String = "",
    val destGeohash: String = "",
    val departureTime: Long = 0L,
    val totalSeats: Int = 4,
    val seatsLeft: Int = 4,
    val vehicleInfo: String = "",
    val costPerRider: Double = 0.0,
    val womenOnly: Boolean = false,
    // "active", "full" and "closed" are all system-set (seats and departure time drive them);
    // "completed" and "cancelled" are host decisions and, once set, are never overwritten by the
    // system. See HostControlsPolicy for which statuses still allow a host action.
    val status: String = "active",
    val routeSamplePoints: List<String> = emptyList(), // geohashes or locations
    val costEstimate: Double = 0.0,
    val passengers: List<String> = emptyList(),
    val passengerNames: List<String> = emptyList(),
    /** A precise spot within [destination], e.g. "North Gate, by the flagpole" — not free text in [routeSamplePoints]. */
    val exitLocation: String = ""
)

@Serializable
data class RideRequest(
    val id: String = "",
    val riderId: String = "",
    val riderName: String = "",
    val riderRating: Float = 0.0f,
    val origin: String = "",
    val destination: String = "",
    val originLat: Double = 0.0,
    val originLng: Double = 0.0,
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val originGeohash: String = "",
    val destGeohash: String = "",
    val departureTime: Long = 0L,
    val seatsNeeded: Int = 1,
    val notes: String = "",
    val womenOnly: Boolean = false,
    // "active" and "closed" (departure time passed, never matched) are system-set; "matched" and
    // "cancelled" are set by a match forming or the rider calling it off, and are never overwritten.
    val status: String = "active",
    /** A precise spot within [destination] — see [TripOffer.exitLocation]. */
    val exitLocation: String = ""
)

@Serializable
data class TripMatch(
    val id: String = "",
    val offerId: String = "",
    val requestId: String = "",
    val hostId: String = "",
    val riderId: String = "",
    val riderName: String = "",
    val riderRating: Float = 0.0f,
    val contribution: Double = 0.0,
    val status: String = "pending", // "pending", "accepted", "declined", "completed"
    val timestamp: Long = 0L,
    // Denormalised [hostId, riderId]. A security rule cannot follow a reference cheaply — Firestore
    // caps get()/exists() at ten per query — so participation has to be readable from the document
    // itself for "only participants may read this" to survive a list query.
    val participants: List<String> = emptyList()
)

/**
 * What kind of chat message this is.
 *
 * A plain string constant rather than an enum, because the field is serialised straight into
 * Firestore and an unknown value from a newer client must not fail to decode — [Message.kind]
 * falls back to [MessageType.TEXT].
 */
object MessageType {
    const val TEXT = "text"

    /** One side suggesting a pickup spot and time; carries [Message.pickupSpot]/[Message.pickupTime]. */
    const val PICKUP_PROPOSAL = "pickup_proposal"

    /** The other side agreeing to a [PICKUP_PROPOSAL]. */
    const val PICKUP_CONFIRMED = "pickup_confirmed"

    val ALL = setOf(TEXT, PICKUP_PROPOSAL, PICKUP_CONFIRMED)
}

@Serializable
data class Message(
    val id: String = "",
    val matchId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    /** Denormalised from the match, for the same reason as [TripMatch.participants]. */
    val participants: List<String> = emptyList(),
    /**
     * True for messages the app injects on the sender's behalf (e.g. "request accepted"), so the
     * chat screen can render them as a neutral system bubble rather than the sender's own.
     *
     * `senderId` still has to be a real participant's uid, not a literal `"system"`: the `messages`
     * Firestore rule requires `senderId == request.auth.uid` on create, and no user is ever
     * authenticated as `"system"`.
     */
    val isSystem: Boolean = false,
    /**
     * One of [MessageType]. The chat screen used to infer this from the text — `text.startsWith
     * ("[PROPOSAL]")` — which meant anything a user happened to type starting with that literal
     * rendered as a system proposal card instead of their own message, and the spot and time had
     * to be recovered with `substringAfter`.
     *
     * Read through [kind], never directly: messages written before this field existed have `""`.
     */
    val type: String = MessageType.TEXT,
    /** The exact pickup address. Set on a [MessageType.PICKUP_PROPOSAL] or [MessageType.PICKUP_CONFIRMED]. */
    val pickupSpot: String = "",
    /** Set on a [MessageType.PICKUP_PROPOSAL] or [MessageType.PICKUP_CONFIRMED]. */
    val pickupTime: String = "",
    /**
     * The exact drop-off address. Empty on proposals written before this field existed, which is
     * why the card renders it conditionally rather than showing a blank row.
     */
    val dropoffSpot: String = "",
    /**
     * What the rider chips in, in dollars, as proposed or as agreed.
     *
     * The amount lives on the message rather than only on [TripMatch] so that both sides can see
     * the number they are agreeing to. Confirming a proposal writes it back to the match, which is
     * the only point in the flow where the price is ever actually confirmed by the other side.
     */
    val contribution: Double = 0.0,
    /**
     * On a [MessageType.PICKUP_CONFIRMED], the id of the [MessageType.PICKUP_PROPOSAL] it answers.
     *
     * Without this a card could not tell whether it had been confirmed, so the button stayed on
     * screen and every extra tap posted another confirmation.
     */
    val proposalId: String = "",
) {
    /**
     * The message's type, tolerating both a missing value and one this build does not know.
     *
     * Messages stored before the field existed carry their type in a `[PROPOSAL] Location: … |
     * Time: …` prefix, so those are still recognised — a conversation that predates this change
     * keeps rendering its proposal cards rather than turning into raw bracket text.
     */
    val kind: String
        get() = when {
            type in MessageType.ALL -> type
            text.startsWith(LEGACY_PROPOSAL_PREFIX) -> MessageType.PICKUP_PROPOSAL
            text.startsWith(LEGACY_CONFIRMED_PREFIX) -> MessageType.PICKUP_CONFIRMED
            else -> MessageType.TEXT
        }

    /** The pickup spot, falling back to parsing a legacy prefixed message. */
    val spot: String
        get() = pickupSpot.ifEmpty {
            when (kind) {
                MessageType.PICKUP_PROPOSAL ->
                    text.substringAfter("Location: ", "").substringBefore(" | Time:")
                MessageType.PICKUP_CONFIRMED ->
                    text.removePrefix(LEGACY_CONFIRMED_PREFIX).substringAfter("Meet at ", "").substringBefore(" at ")
                else -> ""
            }
        }

    /** The pickup time, falling back to parsing a legacy prefixed message. */
    val time: String
        get() = pickupTime.ifEmpty {
            when (kind) {
                MessageType.PICKUP_PROPOSAL -> text.substringAfter("| Time: ", "")
                MessageType.PICKUP_CONFIRMED -> text.substringAfterLast(" at ", "")
                else -> ""
            }
        }

}

// File-private, not a companion object on Message: kotlinx.serialization puts the generated
// `serializer()` on the companion, and declaring that companion `private` makes it inaccessible
// from outside the file. The symptom is an IllegalAccessError at the first `serializer<Message>()`
// call, not a compile error.
private const val LEGACY_PROPOSAL_PREFIX = "[PROPOSAL]"
private const val LEGACY_CONFIRMED_PREFIX = "[CONFIRMED]"

@Serializable
data class Rating(
    val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val rating: Float = 0.0f,
    val comment: String = "",
    val timestamp: Long = 0L
)

@Serializable
data class Block(
    val id: String = "",
    val userId: String = "",
    val blockedUserId: String = ""
)

/**
 * A report that another user did not show up for a shared ride.
 *
 * One immutable document per (reporter, target) pair. The `aggregateNoShow` Cloud Function counts
 * these with the Admin SDK and writes the target's `noShowCount`; clients can no longer write that
 * counter directly. The field set is fixed by the `no_show_reports` security rule
 * (`keys().hasOnly([...])`), so do not add fields here without updating the rule.
 */
@Serializable
data class NoShowReport(
    val id: String = "",
    val reporterId: String = "",
    val targetId: String = "",
    val timestamp: Long = 0L
)

@Serializable
data class NotificationAlert(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "", // "email", "push", "ride_accepted", "new_message", "match"
    val timestamp: Long = 0L,
    val isRead: Boolean = false
)

@Serializable
data class MatchDetails(
    val match: TripMatch,
    val offer: TripOffer,
    val request: RideRequest,
    val hostProfile: User?,
    val riderProfile: User?
)

@Serializable
data class LocationPlace(
    val name: String,
    val address: String,
    val category: String, // "Campus", "Airport", "Transit", "Neighborhood", "Landmark"
    val lat: Double,
    val lng: Double,
    /**
     * Google Places prediction id, empty for a seed place or a Photon result. When set with
     * [lat]/[lng] still `0.0`, the coordinates have not been fetched yet and the picker resolves them
     * with `OsmLocationService.resolvePlace` on selection. See [RankedPlace.providerId].
     */
    val providerId: String = "",
    /** The Google autocomplete session token this place belongs to. Empty off the Google path. */
    val sessionToken: String = "",
)

val DEFAULT_LOCATION_PLACES = listOf(
    LocationPlace("Snell Library - NEU", "360 Huntington Ave, Boston, MA", "Campus", 42.3383, -71.0881),
    LocationPlace("Curry Student Center - NEU", "346 Huntington Ave, Boston, MA", "Campus", 42.3391, -71.0878),
    LocationPlace("ISEC Building - NEU", "805 Columbus Ave, Boston, MA", "Campus", 42.3364, -71.0883),
    LocationPlace("EXP Research Complex - NEU", "815 Columbus Ave, Boston, MA", "Campus", 42.3358, -71.0890),
    LocationPlace("Ruggles MBTA Station", "Columbus Ave & Ruggles St, Boston, MA", "Transit", 42.3363, -71.0895),
    LocationPlace("Boston Logan International Airport (BOS)", "1 Harborside Dr, Boston, MA", "Airport", 42.3656, -71.0096),
    LocationPlace("South Station Transit Hub", "700 Atlantic Ave, Boston, MA", "Transit", 42.3519, -71.0552),
    LocationPlace("Back Bay Station", "145 Dartmouth St, Boston, MA", "Transit", 42.3473, -71.0754),
    LocationPlace("Mission Hill - Tremont St", "Tremont St & Brigham Circle, Boston, MA", "Neighborhood", 42.3332, -71.1054),
    LocationPlace("Fenway Park / Kenmore", "4 Jersey St, Boston, MA", "Landmark", 42.3467, -71.0972),
    LocationPlace("Harvard Square", "Massachusetts Ave, Cambridge, MA", "Campus", 42.3736, -71.1189),
    LocationPlace("MIT Kendall Square", "77 Massachusetts Ave, Cambridge, MA", "Campus", 42.3592, -71.0932),
    LocationPlace("Allston - Harvard Ave", "Harvard Ave & Commonwealth Ave, Boston, MA", "Neighborhood", 42.3512, -71.1311),
    LocationPlace("Coolidge Corner", "Beacon St & Harvard St, Brookline, MA", "Neighborhood", 42.3423, -71.1215),
    LocationPlace("Assembly Row", "Grand Union Blvd, Somerville, MA", "Landmark", 42.3925, -71.0772),
    LocationPlace("Malden Center MBTA Station", "Commercial St, Malden, MA", "Transit", 42.4265, -71.0692),
    LocationPlace("Quincy Center MBTA", "Hancock St, Quincy, MA", "Transit", 42.2515, -71.0051),
    LocationPlace("Burlington Mall", "75 Middlesex Turnpike, Burlington, MA", "Landmark", 42.4828, -71.2012),
    LocationPlace("Bentley University", "175 Forest St, Waltham, MA", "Campus", 42.3868, -71.2215),
    LocationPlace("Brandeis University", "415 South St, Waltham, MA", "Campus", 42.3653, -71.2586),
    LocationPlace("Worcester Union Station", "2 Washington Square, Worcester, MA", "Transit", 42.2618, -71.7957),
    LocationPlace("Providence Station", "100 Gaspee St, Providence, RI", "Transit", 41.8291, -71.4137)
)
