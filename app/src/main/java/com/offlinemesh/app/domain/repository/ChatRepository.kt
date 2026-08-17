package com.offlinemesh.app.domain.repository

import com.offlinemesh.app.core.model.DeliveryStatus
import com.offlinemesh.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>
    fun getRecentConversations(): Flow<List<MessageEntity>>

    suspend fun sendMessage(recipientId: String, recipientName: String, content: String): MessageEntity
    suspend fun retrySendMessage(messageId: String)
    suspend fun clearConversation(conversationId: String)
    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus)
}
