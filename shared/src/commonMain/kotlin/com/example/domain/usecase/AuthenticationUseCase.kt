package com.example.domain.usecase

import com.example.data.User
import com.example.domain.repository.SawaariRepository
import kotlinx.coroutines.flow.StateFlow

class AuthenticationUseCase(private val repository: SawaariRepository) {

    suspend fun signUp(email: String, password: String, name: String, lastInitial: String): Result<User> {
        // Validate inputs
        if (email.isEmpty() || !email.contains("@")) {
            return Result.failure(IllegalArgumentException("Invalid email format"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        if (name.isEmpty()) {
            return Result.failure(IllegalArgumentException("Name cannot be empty"))
        }

        return repository.signUpWithEmail(email, password, name, lastInitial)
    }

    suspend fun login(email: String, password: String): Result<User> {
        if (email.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Email and password required"))
        }

        return repository.logInWithEmail(email, password)
    }

    suspend fun logout(): Result<Unit> {
        return repository.logout()
    }

    suspend fun verifyEmail(userId: String, verificationCode: String): Result<Boolean> {
        if (userId.isEmpty() || verificationCode.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID and verification code required"))
        }

        return repository.verifyCollegeEmail(userId, verificationCode)
    }

    suspend fun redeemInvite(userId: String, inviteCode: String): Result<Boolean> {
        if (userId.isEmpty() || inviteCode.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID and invite code required"))
        }

        return repository.redeemInviteCode(userId, inviteCode)
    }

    fun getCurrentUser(): StateFlow<User?> = repository.currentUser
}
