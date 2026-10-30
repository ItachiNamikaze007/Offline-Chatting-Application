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
    suspend operator fun invoke(
        recipientId: String,
        recipientName: String,
        content: String,
        replyToMessageId: String? = null,
        replyToSenderId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
        replyToMessageType: String? = null
    ): MessageEntity {
        return chatRepository.sendMessage(
            recipientId = recipientId,
            recipientName = recipientName,
            content = content,
            replyToMessageId = replyToMessageId,
            replyToSenderId = replyToSenderId,
            replyToSenderName = replyToSenderName,
            replyToPreview = replyToPreview,
            replyToMessageType = replyToMessageType
        )
    }
}

class RetrySendMessageUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messageId: String) {
        chatRepository.retrySendMessage(messageId)
    }
}

class DeleteMessageUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messageId: String) {
        chatRepository.deleteMessage(messageId)
    }
}

class DeleteConversationUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(conversationId: String) {
        chatRepository.clearConversation(conversationId)
    }
}

class ClearAllMessagesUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke() {
        chatRepository.clearAllMessages()
    }
}

class MarkConversationAsReadUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(conversationId: String) {
        chatRepository.markConversationAsRead(conversationId)
    }
}

class GetUnreadCountUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(conversationId: String): Flow<Int> {
        return chatRepository.getUnreadCount(conversationId)
    }
}

class ToggleStarMessageUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messageId: String, isStarred: Boolean) {
        chatRepository.toggleStarMessage(messageId, isStarred)
    }
}

class SearchMessagesUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(query: String): Flow<List<MessageEntity>> {
        return chatRepository.searchMessages(query)
    }
}
