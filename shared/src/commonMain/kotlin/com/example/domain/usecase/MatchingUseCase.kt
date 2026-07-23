package com.example.domain.usecase

import com.example.data.TripMatch
import com.example.domain.repository.SawaariRepository
import kotlinx.coroutines.flow.StateFlow

class MatchingUseCase(private val repository: SawaariRepository) {

    val userMatches: StateFlow<List<TripMatch>>
        get() = repository.userMatches

    suspend fun validateAndCreateMatch(
        offerId: String,
        requestId: String,
        contribution: Double
    ): Result<TripMatch> {
        if (offerId.isEmpty() || requestId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Offer ID and Request ID required"))
        }
        if (contribution < 0) {
            return Result.failure(IllegalArgumentException("Contribution cannot be negative"))
        }

        return repository.validateAndCreateMatch(offerId, requestId, contribution)
    }

    suspend fun joinTripOfferDirect(offerId: String): Result<TripMatch> {
        if (offerId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Offer ID required"))
        }

        return repository.joinTripOfferDirect(offerId)
    }

    suspend fun acceptMatch(matchId: String): Result<Boolean> {
        if (matchId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Match ID required"))
        }

        return repository.acceptMatch(matchId)
    }

    suspend fun declineMatch(matchId: String): Result<Boolean> {
        if (matchId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Match ID required"))
        }

        return repository.declineMatch(matchId)
    }

    suspend fun completeTrip(matchId: String): Result<Boolean> {
        if (matchId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Match ID required"))
        }

        return repository.completeTrip(matchId)
    }

    suspend fun fetchMatchesForOffer(offerId: String): Result<List<TripMatch>> {
        if (offerId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Offer ID required"))
        }

        return repository.fetchMatchesForOffer(offerId)
    }

    suspend fun fetchMatchesForRequest(requestId: String): Result<List<TripMatch>> {
        if (requestId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Request ID required"))
        }

        return repository.fetchMatchesForRequest(requestId)
    }
}
