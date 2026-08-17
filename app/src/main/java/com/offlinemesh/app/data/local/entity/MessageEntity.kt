package com.offlinemesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.offlinemesh.app.core.model.DeliveryStatus

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["senderId"]),
        Index(value = ["recipientId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val messageId: String,
    val conversationId: String,          // The peer's OFC ID for 1-to-1, or Community ID
    val senderId: String,                // OFC-XXXXXXXX of sender
    val senderName: String,              // Sender's display name
    val recipientId: String,             // OFC-XXXXXXXX of recipient or Community ID
    val content: String,                 // Plaintext / decrypted message text
    val timestamp: Long,                 // Epoch millis
    val status: DeliveryStatus,          // PENDING, SENDING, SENT, DELIVERED, FAILED
    val isOutgoing: Boolean,             // True if sent by local user
    val hopCount: Int = 0,               // Hops traversed (0 for direct 1-hop)
    val signature: String? = null        // Cryptographic signature
)
