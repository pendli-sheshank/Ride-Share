package com.example.domain.usecase

import kotlinx.datetime.Clock

import com.example.data.TripOffer
import com.example.domain.repository.SawaariRepository
import kotlinx.coroutines.flow.StateFlow

class TripsUseCase(private val repository: SawaariRepository) {

    val activeOffers: StateFlow<List<TripOffer>>
        get() = repository.activeOffers

    suspend fun postTripOffer(
        origin: String,
        destination: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        departureTime: Long,
        totalSeats: Int,
        costPerRider: Double,
        vehicleInfo: String,
        womenOnly: Boolean = false
    ): Result<TripOffer> {
        // Validation
        if (origin.isEmpty() || destination.isEmpty()) {
            return Result.failure(IllegalArgumentException("Origin and destination required"))
        }
        if (totalSeats <= 0) {
            return Result.failure(IllegalArgumentException("Total seats must be positive"))
        }
        if (costPerRider < 0) {
            return Result.failure(IllegalArgumentException("Cost per rider cannot be negative"))
        }
        if (departureTime <= Clock.System.now().toEpochMilliseconds()) {
            return Result.failure(IllegalArgumentException("Departure time must be in the future"))
        }

        val offer = TripOffer(
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
            vehicleInfo = vehicleInfo,
            womenOnly = womenOnly,
            status = "active"
        )

        return repository.postTripOffer(offer)
    }

    suspend fun updateTripOfferStatus(offerId: String, newStatus: String): Result<Boolean> {
        if (offerId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Offer ID required"))
        }
        if (newStatus !in listOf("active", "completed", "cancelled")) {
            return Result.failure(IllegalArgumentException("Invalid status"))
        }

        return repository.updateTripOfferStatus(offerId, newStatus)
    }

    suspend fun fetchMyTrips(userId: String): Result<List<TripOffer>> {
        if (userId.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID required"))
        }

        return repository.fetchMyTripsFromFirestore(userId)
    }

    suspend fun fetchActiveOffers(): Result<List<TripOffer>> {
        return repository.fetchActiveOffers()
    }

    suspend fun cancelTripOffer(offerId: String): Result<Boolean> {
        return updateTripOfferStatus(offerId, "cancelled")
    }
}
