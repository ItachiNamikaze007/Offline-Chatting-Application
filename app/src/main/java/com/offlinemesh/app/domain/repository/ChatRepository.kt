package com.offlinemesh.app.domain.repository

import com.offlinemesh.app.core.model.DeliveryStatus
import com.offlinemesh.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class MessagePayload(
    val text: String,
    val replyToMessageId: String? = null,
    val replyToSenderId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    val replyToMessageType: String? = null
)

interface ChatRepository {
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>
    fun getRecentConversations(): Flow<List<MessageEntity>>

    suspend fun sendMessage(
        recipientId: String,
        recipientName: String,
        content: String,
        replyToMessageId: String? = null,
        replyToSenderId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
        replyToMessageType: String? = null
    ): MessageEntity

    suspend fun retrySendMessage(messageId: String)
    suspend fun deleteMessage(messageId: String)
    suspend fun clearConversation(conversationId: String)
    suspend fun clearAllMessages()
    suspend fun markConversationAsRead(conversationId: String)
    fun getUnreadCount(conversationId: String): Flow<Int>
    fun getTotalUnreadCount(): Flow<Int>
    suspend fun toggleStarMessage(messageId: String, isStarred: Boolean)
    fun searchMessages(query: String): Flow<List<MessageEntity>>
    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus)
    suspend fun syncProfilePhoto()
    suspend fun requestPeerAvatar(peerUserId: String)
}
