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
    val communityId: String = "",
    val homeArea: String = "",
    val isWomenOnlyFilterEnabled: Boolean = false,
    val fcmToken: String = "",
    val emailNotificationsEnabled: Boolean = false,
    val pushNotificationsEnabled: Boolean = false,
    val collegeName: String = "",
    val verifiedEmail: String = ""
) {
    val displayName: String
        get() = if (lastInitial.isNotEmpty()) "$name $lastInitial." else name
}

@Serializable
data class Invite(
    val code: String = "",
    val used: Boolean = false,
    val invitedBy: String = "",
    val usedBy: String = ""
)

@Serializable
data class LocalCredential(
    val email: String = "",
    val password: String = "",
    val userId: String = ""
)

@Serializable
data class Community(
    val id: String = "",
    val name: String = "",
    val location: String = ""
)

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
    val status: String = "active", // "active", "completed", "cancelled"
    val routeSamplePoints: List<String> = emptyList(), // geohashes or locations
    val costEstimate: Double = 0.0,
    val passengers: List<String> = emptyList(),
    val passengerNames: List<String> = emptyList()
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
    val status: String = "active" // "active", "matched", "cancelled"
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
    val timestamp: Long = 0L
)

@Serializable
data class Message(
    val id: String = "",
    val matchId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

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

// Conversion helpers for Firestore/JSON serialization
fun TripOffer.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "hostId" to hostId,
    "hostName" to hostName,
    "hostRating" to hostRating,
    "origin" to origin,
    "destination" to destination,
    "originLat" to originLat,
    "originLng" to originLng,
    "destLat" to destLat,
    "destLng" to destLng,
    "originGeohash" to originGeohash,
    "destGeohash" to destGeohash,
    "departureTime" to departureTime,
    "totalSeats" to totalSeats,
    "seatsLeft" to seatsLeft,
    "vehicleInfo" to vehicleInfo,
    "costPerRider" to costPerRider,
    "womenOnly" to womenOnly,
    "status" to status,
    "routeSamplePoints" to routeSamplePoints,
    "costEstimate" to costEstimate,
    "passengers" to passengers,
    "passengerNames" to passengerNames
)

fun RideRequest.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "riderId" to riderId,
    "riderName" to riderName,
    "riderRating" to riderRating,
    "origin" to origin,
    "destination" to destination,
    "originLat" to originLat,
    "originLng" to originLng,
    "destLat" to destLat,
    "destLng" to destLng,
    "originGeohash" to originGeohash,
    "destGeohash" to destGeohash,
    "departureTime" to departureTime,
    "seatsNeeded" to seatsNeeded,
    "notes" to notes,
    "womenOnly" to womenOnly,
    "status" to status
)

fun User.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "phoneNumber" to phoneNumber,
    "email" to email,
    "name" to name,
    "lastInitial" to lastInitial,
    "avatarUrl" to avatarUrl,
    "verifiedTier" to verifiedTier,
    "invitedBy" to invitedBy,
    "ratingAvg" to ratingAvg,
    "ratingCount" to ratingCount,
    "noShowCount" to noShowCount,
    "communityId" to communityId,
    "homeArea" to homeArea,
    "isWomenOnlyFilterEnabled" to isWomenOnlyFilterEnabled,
    "fcmToken" to fcmToken,
    "emailNotificationsEnabled" to emailNotificationsEnabled,
    "pushNotificationsEnabled" to pushNotificationsEnabled,
    "collegeName" to collegeName,
    "verifiedEmail" to verifiedEmail
)

fun Message.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "matchId" to matchId,
    "senderId" to senderId,
    "senderName" to senderName,
    "text" to text,
    "timestamp" to timestamp
)

fun NotificationAlert.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "title" to title,
    "message" to message,
    "type" to type,
    "timestamp" to timestamp,
    "isRead" to isRead
)

@Serializable
data class LocationPlace(
    val name: String,
    val address: String,
    val category: String, // "Campus", "Airport", "Transit", "Neighborhood", "Landmark"
    val lat: Double,
    val lng: Double
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
