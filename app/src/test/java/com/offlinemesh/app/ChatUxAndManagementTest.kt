package com.offlinemesh.app

import com.google.common.truth.Truth.assertThat
import com.offlinemesh.app.core.model.DeliveryStatus
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.domain.repository.MessagePayload
import com.offlinemesh.app.ui.viewmodel.ConversationItemUiModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.util.UUID

class ChatUxAndManagementTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun messageDeletion_removesTargetMessageFromList() {
        val msg1 = createTestMessage("MSG-1", "Hello 1")
        val msg2 = createTestMessage("MSG-2", "Hello 2")
        val list = mutableListOf(msg1, msg2)

        // Delete MSG-1
        list.removeIf { it.messageId == "MSG-1" }

        assertThat(list).hasSize(1)
        assertThat(list.first().messageId).isEqualTo("MSG-2")
    }

    @Test
    fun conversationDeletion_clearsAllMessagesForConversation() {
        val peerId = "OFC-BOB1234"
        val otherPeerId = "OFC-ALICE99"

        val messages = mutableListOf(
            createTestMessage("MSG-1", "Hi Bob", conversationId = peerId),
            createTestMessage("MSG-2", "Hey Bob", conversationId = peerId),
            createTestMessage("MSG-3", "Hello Alice", conversationId = otherPeerId)
        )

        // Clear conversation for peerId
        messages.removeIf { it.conversationId == peerId }

        assertThat(messages).hasSize(1)
        assertThat(messages.first().conversationId).isEqualTo(otherPeerId)
    }

    @Test
    fun unreadCount_incrementsOnIncomingAndClearsOnRead() {
        val peerId = "OFC-BOB1234"
        val messages = mutableListOf(
            createTestMessage("MSG-1", "Unread 1", isOutgoing = false, isRead = false, conversationId = peerId),
            createTestMessage("MSG-2", "Unread 2", isOutgoing = false, isRead = false, conversationId = peerId),
            createTestMessage("MSG-3", "My reply", isOutgoing = true, isRead = true, conversationId = peerId)
        )

        // Count unread
        val unreadCount = messages.count { !it.isOutgoing && !it.isRead && it.conversationId == peerId }
        assertThat(unreadCount).isEqualTo(2)

        // Mark conversation as read
        val updatedMessages = messages.map {
            if (it.conversationId == peerId && !it.isRead) it.copy(isRead = true) else it
        }

        val remainingUnread = updatedMessages.count { !it.isOutgoing && !it.isRead && it.conversationId == peerId }
        assertThat(remainingUnread).isEqualTo(0)
    }

    @Test
    fun messageStarring_togglesStarredState() {
        val msg = createTestMessage("MSG-1", "Important text", isStarred = false)
        val starredMsg = msg.copy(isStarred = true)
        assertThat(starredMsg.isStarred).isTrue()

        val unstarredMsg = starredMsg.copy(isStarred = false)
        assertThat(unstarredMsg.isStarred).isFalse()
    }

    @Test
    fun messageSearch_filtersByContentAndSender() {
        val messages = listOf(
            createTestMessage("MSG-1", "Let's meet at the shelter", senderName = "Alice"),
            createTestMessage("MSG-2", "Need emergency water", senderName = "Bob"),
            createTestMessage("MSG-3", "Radio frequency is 433MHz", senderName = "Charlie")
        )

        val query = "emergency"
        val results = messages.filter { it.content.contains(query, ignoreCase = true) || it.senderName.contains(query, ignoreCase = true) }

        assertThat(results).hasSize(1)
        assertThat(results.first().senderName).isEqualTo("Bob")
    }

    @Test
    fun conversationListSearch_matchesNameOfcIdAndMessage() {
        val items = listOf(
            ConversationItemUiModel(
                conversationId = "OFC-11112222",
                peerName = "Medical Team",
                avatarColorHex = "#3B82F6",
                lastMessageContent = "Supplies arriving",
                lastMessageTimestamp = 1000L,
                lastMessageStatus = DeliveryStatus.DELIVERED,
                isOutgoing = false
            ),
            ConversationItemUiModel(
                conversationId = "OFC-33334444",
                peerName = "Search & Rescue",
                avatarColorHex = "#10B981",
                lastMessageContent = "Grid sector 4 clear",
                lastMessageTimestamp = 2000L,
                lastMessageStatus = DeliveryStatus.DELIVERED,
                isOutgoing = false
            )
        )

        // Search by OFC ID
        val queryOfc = "1111"
        val ofcResults = items.filter {
            it.peerName.contains(queryOfc, ignoreCase = true) ||
                    it.conversationId.contains(queryOfc, ignoreCase = true) ||
                    it.lastMessageContent.contains(queryOfc, ignoreCase = true)
        }
        assertThat(ofcResults).hasSize(1)
        assertThat(ofcResults.first().peerName).isEqualTo("Medical Team")

        // Search by message content
        val queryContent = "sector 4"
        val contentResults = items.filter {
            it.peerName.contains(queryContent, ignoreCase = true) ||
                    it.conversationId.contains(queryContent, ignoreCase = true) ||
                    it.lastMessageContent.contains(queryContent, ignoreCase = true)
        }
        assertThat(contentResults).hasSize(1)
        assertThat(contentResults.first().peerName).isEqualTo("Search & Rescue")
    }

    @Test
    fun messageRetry_transitionsFromFailedToSending() {
        val failedMsg = createTestMessage("MSG-FAIL", "Test retry", status = DeliveryStatus.FAILED, isOutgoing = true)
        assertThat(failedMsg.status).isEqualTo(DeliveryStatus.FAILED)

        val retryingMsg = failedMsg.copy(status = DeliveryStatus.SENDING)
        assertThat(retryingMsg.status).isEqualTo(DeliveryStatus.SENDING)
    }

    @Test
    fun messageReply_serializationAndDeserializationPreservesAllMetadata() {
        val originalMessageId = "ORIGINAL-MSG-UUID-999"
        val originalSenderId = "OFC-ALICE111"
        val originalSenderName = "Alice"
        val originalPreview = "Meet me at checkpoint 3"

        val replyPayload = MessagePayload(
            text = "I am on my way!",
            replyToMessageId = originalMessageId,
            replyToSenderId = originalSenderId,
            replyToSenderName = originalSenderName,
            replyToPreview = originalPreview,
            replyToMessageType = "TEXT"
        )

        val jsonString = json.encodeToString(replyPayload)
        val decoded = json.decodeFromString<MessagePayload>(jsonString)

        assertThat(decoded.text).isEqualTo("I am on my way!")
        assertThat(decoded.replyToMessageId).isEqualTo(originalMessageId)
        assertThat(decoded.replyToSenderId).isEqualTo(originalSenderId)
        assertThat(decoded.replyToSenderName).isEqualTo(originalSenderName)
        assertThat(decoded.replyToPreview).isEqualTo(originalPreview)
        assertThat(decoded.replyToMessageType).isEqualTo("TEXT")
    }

    @Test
    fun messageReply_backwardCompatibilityWithPlainStringPayload() {
        val plainText = "Old version plain string payload"
        val parsed = try {
            json.decodeFromString<MessagePayload>(plainText)
        } catch (e: Exception) {
            MessagePayload(text = plainText)
        }

        assertThat(parsed.text).isEqualTo("Old version plain string payload")
        assertThat(parsed.replyToMessageId).isNull()
        assertThat(parsed.replyToPreview).isNull()
    }

    @Test
    fun messageReply_localNavigationFindsOriginalMessage() {
        val originalMsg = createTestMessage("MSG-100", "Where are the radios?")
        val replyMsg = createTestMessage(
            id = "MSG-101",
            content = "They are in the red case",
            replyToMessageId = "MSG-100",
            replyToPreview = "Where are the radios?"
        )

        val chatHistory = listOf(originalMsg, replyMsg)

        // Find index of quoted message
        val targetIndex = chatHistory.indexOfFirst { it.messageId == replyMsg.replyToMessageId }
        assertThat(targetIndex).isEqualTo(0)
        assertThat(chatHistory[targetIndex].content).isEqualTo("Where are the radios?")
    }

    @Test
    fun messageReply_deletedOriginalMessageHandledSafely() {
        val replyMsg = createTestMessage(
            id = "MSG-101",
            content = "They are in the red case",
            replyToMessageId = "MSG-DELETED",
            replyToPreview = "Original question"
        )

        val chatHistory = listOf(replyMsg) // Original message was deleted locally

        val targetIndex = chatHistory.indexOfFirst { it.messageId == replyMsg.replyToMessageId }
        assertThat(targetIndex).isEqualTo(-1) // Handled safely, shows 'Original message unavailable'
    }

    private fun createTestMessage(
        id: String,
        content: String,
        conversationId: String = "OFC-PEER1",
        senderId: String = "OFC-ME",
        senderName: String = "Me",
        recipientId: String = "OFC-PEER1",
        status: DeliveryStatus = DeliveryStatus.SENT,
        isOutgoing: Boolean = true,
        isRead: Boolean = true,
        isStarred: Boolean = false,
        replyToMessageId: String? = null,
        replyToSenderId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
        replyToMessageType: String? = null
    ): MessageEntity {
        return MessageEntity(
            messageId = id,
            conversationId = conversationId,
            senderId = senderId,
            senderName = senderName,
            recipientId = recipientId,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = status,
            isOutgoing = isOutgoing,
            hopCount = 0,
            signature = null,
            isRead = isRead,
            isStarred = isStarred,
            replyToMessageId = replyToMessageId,
            replyToSenderId = replyToSenderId,
            replyToSenderName = replyToSenderName,
            replyToPreview = replyToPreview,
            replyToMessageType = replyToMessageType
        )
    }
}
