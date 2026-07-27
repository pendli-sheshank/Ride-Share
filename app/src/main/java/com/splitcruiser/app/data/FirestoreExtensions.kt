package com.splitcruiser.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.splitcruiser.app.data.Message
import com.splitcruiser.app.data.NotificationAlert
import com.splitcruiser.app.data.RideRequest
import com.splitcruiser.app.data.TripOffer

fun DocumentSnapshot.toTripOfferSafe(): TripOffer? {
    return try {
        this.toObject(TripOffer::class.java)
    } catch (_: Exception) {
        try {
            val data = this.data ?: return null
            TripOffer(
                id = data["id"] as? String ?: this.id,
                hostId = data["hostId"] as? String ?: "",
                hostName = data["hostName"] as? String ?: "",
                hostRating = (data["hostRating"] as? Number)?.toFloat() ?: 0.0f,
                origin = data["origin"] as? String ?: "",
                destination = data["destination"] as? String ?: "",
                originLat = (data["originLat"] as? Number)?.toDouble() ?: 0.0,
                originLng = (data["originLng"] as? Number)?.toDouble() ?: 0.0,
                destLat = (data["destLat"] as? Number)?.toDouble() ?: 0.0,
                destLng = (data["destLng"] as? Number)?.toDouble() ?: 0.0,
                originGeohash = data["originGeohash"] as? String ?: "",
                destGeohash = data["destGeohash"] as? String ?: "",
                departureTime = (data["departureTime"] as? Number)?.toLong() ?: 0L,
                totalSeats = (data["totalSeats"] as? Number)?.toInt() ?: 4,
                seatsLeft = (data["seatsLeft"] as? Number)?.toInt() ?: 4,
                vehicleInfo = data["vehicleInfo"] as? String ?: "",
                costPerRider = (data["costPerRider"] as? Number)?.toDouble() ?: 0.0,
                womenOnly = data["womenOnly"] as? Boolean ?: false,
                status = data["status"] as? String ?: "active",
                routeSamplePoints = (data["routeSamplePoints"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                costEstimate = (data["costEstimate"] as? Number)?.toDouble() ?: 0.0,
                passengers = (data["passengers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                passengerNames = (data["passengerNames"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        } catch (_: Exception) {
            null
        }
    }
}

fun DocumentSnapshot.toRideRequestSafe(): RideRequest? {
    return try {
        this.toObject(RideRequest::class.java)
    } catch (_: Exception) {
        try {
            val data = this.data ?: return null
            RideRequest(
                id = data["id"] as? String ?: this.id,
                riderId = data["riderId"] as? String ?: "",
                riderName = data["riderName"] as? String ?: "",
                riderRating = (data["riderRating"] as? Number)?.toFloat() ?: 0.0f,
                origin = data["origin"] as? String ?: "",
                destination = data["destination"] as? String ?: "",
                originLat = (data["originLat"] as? Number)?.toDouble() ?: 0.0,
                originLng = (data["originLng"] as? Number)?.toDouble() ?: 0.0,
                destLat = (data["destLat"] as? Number)?.toDouble() ?: 0.0,
                destLng = (data["destLng"] as? Number)?.toDouble() ?: 0.0,
                originGeohash = data["originGeohash"] as? String ?: "",
                destGeohash = data["destGeohash"] as? String ?: "",
                departureTime = (data["departureTime"] as? Number)?.toLong() ?: 0L,
                seatsNeeded = (data["seatsNeeded"] as? Number)?.toInt() ?: 1,
                notes = data["notes"] as? String ?: "",
                womenOnly = data["womenOnly"] as? Boolean ?: false,
                status = data["status"] as? String ?: "active"
            )
        } catch (_: Exception) {
            null
        }
    }
}

fun DocumentSnapshot.toMessageSafe(): Message? {
    return try {
        this.toObject(Message::class.java)
    } catch (_: Exception) {
        try {
            val data = this.data ?: return null
            Message(
                id = data["id"] as? String ?: this.id,
                matchId = data["matchId"] as? String ?: "",
                senderId = data["senderId"] as? String ?: "",
                senderName = data["senderName"] as? String ?: "",
                text = data["text"] as? String ?: "",
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
            )
        } catch (_: Exception) {
            null
        }
    }
}

fun DocumentSnapshot.toNotificationAlertSafe(): NotificationAlert? {
    return try {
        this.toObject(NotificationAlert::class.java)
    } catch (_: Exception) {
        try {
            val data = this.data ?: return null
            NotificationAlert(
                id = data["id"] as? String ?: this.id,
                userId = data["userId"] as? String ?: "",
                title = data["title"] as? String ?: "",
                message = data["message"] as? String ?: "",
                type = data["type"] as? String ?: "",
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                isRead = data["isRead"] as? Boolean ?: false
            )
        } catch (_: Exception) {
            null
        }
    }
}
