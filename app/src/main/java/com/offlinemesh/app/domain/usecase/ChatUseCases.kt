package com.offlinemesh.app.domain.usecase

import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class ObserveMessagesUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(conversationId: String): Flow<List<MessageEntity>> {
        return chatRepository.getMessagesForConversation(conversationId)
    }
}

class ObserveRecentConversationsUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<MessageEntity>> {
        return chatRepository.getRecentConversations()
    }
}

class SendMessageUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(recipientId: String, recipientName: String, content: String): MessageEntity {
        return chatRepository.sendMessage(recipientId, recipientName, content)
    }
}

class RetrySendMessageUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messageId: String) {
        chatRepository.retrySendMessage(messageId)
    }
}
