package com.example.domain.usecase

import com.example.domain.repository.SawaariRepository

class SafetyUseCase(private val repository: SawaariRepository) {

    suspend fun blockUser(userId: String, blockedUserId: String): Result<Boolean> {
        if (userId.isEmpty() || blockedUserId.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID and Blocked User ID required"))
        }
        if (userId == blockedUserId) {
            return Result.failure(IllegalArgumentException("Cannot block yourself"))
        }

        return repository.blockUser(userId, blockedUserId)
    }

    suspend fun unblockUser(userId: String, blockedUserId: String): Result<Boolean> {
        if (userId.isEmpty() || blockedUserId.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID and Blocked User ID required"))
        }

        return repository.unblockUser(userId, blockedUserId)
    }

    suspend fun getBlockedUsers(userId: String): Result<List<String>> {
        if (userId.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID required"))
        }

        return repository.getBlockedUsers(userId)
    }

    suspend fun submitRating(
        fromUserId: String,
        toUserId: String,
        rating: Float,
        comment: String = ""
    ): Result<Boolean> {
        if (fromUserId.isEmpty() || toUserId.isEmpty()) {
            return Result.failure(IllegalArgumentException("From User ID and To User ID required"))
        }
        if (rating < 1.0f || rating > 5.0f) {
            return Result.failure(IllegalArgumentException("Rating must be between 1.0 and 5.0"))
        }
        if (fromUserId == toUserId) {
            return Result.failure(IllegalArgumentException("Cannot rate yourself"))
        }

        return repository.submitRating(fromUserId, toUserId, rating, comment)
    }

    suspend fun recordNoShow(userId: String, tripId: String): Result<Boolean> {
        if (userId.isEmpty() || tripId.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID and Trip ID required"))
        }

        return repository.recordNoShow(userId, tripId)
    }

    suspend fun getUserRating(userId: String): Result<Pair<Float, Int>> {
        if (userId.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID required"))
        }

        return repository.getUserRating(userId)
    }

    suspend fun getNoShowCount(userId: String): Result<Int> {
        if (userId.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID required"))
        }

        return repository.getNoShowCount(userId)
    }
}
