package com.example.domain.usecase

import com.example.data.RideRequest
import com.example.domain.repository.SawaariRepository
import kotlinx.coroutines.flow.StateFlow

class RequestsUseCase(private val repository: SawaariRepository) {

    val activeRequests: StateFlow<List<RideRequest>>
        get() = repository.activeRequests

    val myRideRequests: StateFlow<List<RideRequest>>
        get() = repository.myRideRequests

    suspend fun postRideRequest(
        origin: String,
        destination: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        departureTime: Long,
        seatsNeeded: Int = 1,
        notes: String = "",
        womenOnly: Boolean = false
    ): Result<RideRequest> {
        // Validation
        if (origin.isEmpty() || destination.isEmpty()) {
            return Result.failure(IllegalArgumentException("Origin and destination required"))
        }
        if (seatsNeeded <= 0) {
            return Result.failure(IllegalArgumentException("Seats needed must be positive"))
        }
        if (departureTime <= System.currentTimeMillis()) {
            return Result.failure(IllegalArgumentException("Departure time must be in the future"))
        }

        val request = RideRequest(
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
            status = "active"
        )

        return repository.postRideRequest(request)
    }

    suspend fun updateRideRequestStatus(requestId: String, newStatus: String): Result<Boolean> {
        if (requestId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Request ID required"))
        }
        if (newStatus !in listOf("active", "matched", "cancelled")) {
            return Result.failure(IllegalArgumentException("Invalid status"))
        }

        return repository.updateRideRequestStatus(requestId, newStatus)
    }

    suspend fun cancelRideRequest(requestId: String): Result<Boolean> {
        return updateRideRequestStatus(requestId, "cancelled")
    }

    suspend fun fetchActiveRequests(): Result<List<RideRequest>> {
        return repository.fetchActiveRequests()
    }

    suspend fun fetchMyRequests(userId: String): Result<List<RideRequest>> {
        if (userId.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID required"))
        }

        return repository.fetchMyRideRequests(userId)
    }
}
