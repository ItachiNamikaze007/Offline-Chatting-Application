package com.offlinemesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offlinemesh.app.core.model.DeliveryStatus
import com.offlinemesh.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

data class ConversationSummary(
    val conversationId: String,
    val peerName: String,
    val lastMessageContent: String,
    val lastMessageTimestamp: Long,
    val lastMessageStatus: DeliveryStatus,
    val isOutgoing: Boolean,
    val unreadCount: Int = 0
)

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus)

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE messageId = :messageId)")
    suspend fun hasMessage(messageId: String): Boolean

    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT conversationId, MAX(timestamp) as maxTimestamp
            FROM messages
            GROUP BY conversationId
        ) latest ON m.conversationId = latest.conversationId AND m.timestamp = latest.maxTimestamp
        ORDER BY m.timestamp DESC
    """)
    fun getRecentConversations(): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)
}
