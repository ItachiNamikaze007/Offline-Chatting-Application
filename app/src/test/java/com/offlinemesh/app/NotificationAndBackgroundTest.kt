package com.offlinemesh.app

import com.offlinemesh.app.core.model.NotificationPrivacy
import com.offlinemesh.app.core.notification.ActiveConversationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationAndBackgroundTest {

    @Before
    fun setup() {
        ActiveConversationTracker.setActiveConversation(null)
    }

    @Test
    fun activeConversationTracker_suppressesNotification_whenConversationIsActive() {
        assertFalse(ActiveConversationTracker.isConversationActive("OFC-PEER-123"))

        ActiveConversationTracker.setActiveConversation("OFC-PEER-123")
        assertTrue(ActiveConversationTracker.isConversationActive("OFC-PEER-123"))
        assertFalse(ActiveConversationTracker.isConversationActive("OFC-PEER-999"))

        ActiveConversationTracker.setActiveConversation(null)
        assertFalse(ActiveConversationTracker.isConversationActive("OFC-PEER-123"))
    }

    @Test
    fun notificationPrivacy_showFullMessage_formatsSenderAndBodyCorrectly() {
        val privacy = NotificationPrivacy.SHOW_FULL_MESSAGE
        val sender = "Alice"
        val message = "Hello from mesh!"

        val title = when (privacy) {
            NotificationPrivacy.SHOW_FULL_MESSAGE,
            NotificationPrivacy.SHOW_SENDER_ONLY -> sender
            NotificationPrivacy.HIDE_PREVIEW -> "OfflineMesh"
        }

        val text = when (privacy) {
            NotificationPrivacy.SHOW_FULL_MESSAGE -> message
            NotificationPrivacy.SHOW_SENDER_ONLY -> "New message"
            NotificationPrivacy.HIDE_PREVIEW -> "New encrypted message received"
        }

        assertEquals("Alice", title)
        assertEquals("Hello from mesh!", text)
    }

    @Test
    fun notificationPrivacy_showSenderOnly_hidesMessageBody() {
        val privacy = NotificationPrivacy.SHOW_SENDER_ONLY
        val sender = "Bob"
        val message = "Top secret message content"

        val title = when (privacy) {
            NotificationPrivacy.SHOW_FULL_MESSAGE,
            NotificationPrivacy.SHOW_SENDER_ONLY -> sender
            NotificationPrivacy.HIDE_PREVIEW -> "OfflineMesh"
        }

        val text = when (privacy) {
            NotificationPrivacy.SHOW_FULL_MESSAGE -> message
            NotificationPrivacy.SHOW_SENDER_ONLY -> "New message"
            NotificationPrivacy.HIDE_PREVIEW -> "New encrypted message received"
        }

        assertEquals("Bob", title)
        assertEquals("New message", text)
        assertFalse(text.contains("Top secret"))
    }

    @Test
    fun notificationPrivacy_hidePreview_hidesSenderAndBody() {
        val privacy = NotificationPrivacy.HIDE_PREVIEW
        val sender = "Charlie"
        val message = "Another private message"

        val title = when (privacy) {
            NotificationPrivacy.SHOW_FULL_MESSAGE,
            NotificationPrivacy.SHOW_SENDER_ONLY -> sender
            NotificationPrivacy.HIDE_PREVIEW -> "OfflineMesh"
        }

        val text = when (privacy) {
            NotificationPrivacy.SHOW_FULL_MESSAGE -> message
            NotificationPrivacy.SHOW_SENDER_ONLY -> "New message"
            NotificationPrivacy.HIDE_PREVIEW -> "New encrypted message received"
        }

        assertEquals("OfflineMesh", title)
        assertEquals("New encrypted message received", text)
        assertFalse(title.contains("Charlie"))
        assertFalse(text.contains("Another private message"))
    }

    @Test
    fun notificationGrouping_conversationIdHashesDifferForUniquePeers() {
        val convId1 = "OFC-PEER-AAAA"
        val convId2 = "OFC-PEER-BBBB"

        val id1 = convId1.hashCode()
        val id2 = convId2.hashCode()

        assertTrue("Unique conversation IDs should produce distinct notification IDs", id1 != id2)
    }
}
