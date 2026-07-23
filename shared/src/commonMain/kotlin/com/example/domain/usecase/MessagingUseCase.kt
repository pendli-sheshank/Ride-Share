package com.example.domain.usecase

import com.example.data.Message
import com.example.domain.repository.SawaariRepository

class MessagingUseCase(private val repository: SawaariRepository) {

    suspend fun sendMessage(
        matchId: String,
        senderId: String,
        senderName: String,
        text: String
    ): Result<Message> {
        if (matchId.isEmpty() || senderId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Match ID and Sender ID required"))
        }
        if (text.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message text cannot be empty"))
        }

        return repository.sendMessage(matchId, senderId, senderName, text)
    }

    suspend fun getChatMessages(matchId: String): Result<List<Message>> {
        if (matchId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Match ID required"))
        }

        return repository.getChatMessages(matchId)
    }

    suspend fun markMessageAsRead(matchId: String, messageId: String): Result<Boolean> {
        if (matchId.isEmpty() || messageId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Match ID and Message ID required"))
        }

        return repository.markMessageAsRead(matchId, messageId)
    }

    suspend fun deleteMessage(matchId: String, messageId: String): Result<Boolean> {
        if (matchId.isEmpty() || messageId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Match ID and Message ID required"))
        }

        return repository.deleteMessage(matchId, messageId)
    }
}
