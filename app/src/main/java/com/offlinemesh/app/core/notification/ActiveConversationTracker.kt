package com.offlinemesh.app.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveConversationTracker {
    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    fun isConversationActive(conversationId: String): Boolean {
        return _activeConversationId.value == conversationId
    }
}
