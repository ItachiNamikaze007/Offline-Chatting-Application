package com.offlinemesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.data.local.entity.PeerEntity
import com.offlinemesh.app.domain.repository.ChatRepository
import com.offlinemesh.app.domain.repository.PeerRepository
import com.offlinemesh.app.domain.usecase.ConnectPeerUseCase
import com.offlinemesh.app.domain.usecase.DeleteConversationUseCase
import com.offlinemesh.app.domain.usecase.DeleteMessageUseCase
import com.offlinemesh.app.domain.usecase.GetOrCreateIdentityUseCase
import com.offlinemesh.app.domain.usecase.MarkConversationAsReadUseCase
import com.offlinemesh.app.domain.usecase.ObserveConnectedPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveDiscoveredPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveMessagesUseCase
import com.offlinemesh.app.domain.usecase.RetrySendMessageUseCase
import com.offlinemesh.app.domain.usecase.SendMessageUseCase
import com.offlinemesh.app.domain.usecase.ToggleStarMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    val conversationId: String,
    val peerName: String,
    val peerAvatarColor: String?,
    private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val retrySendMessageUseCase: RetrySendMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val deleteConversationUseCase: DeleteConversationUseCase,
    private val markConversationAsReadUseCase: MarkConversationAsReadUseCase,
    private val toggleStarMessageUseCase: ToggleStarMessageUseCase,
    private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
    private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
    private val connectPeerUseCase: ConnectPeerUseCase,
    private val peerRepository: PeerRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _userIdentity = MutableStateFlow<UserIdentity?>(null)
    val userIdentity: StateFlow<UserIdentity?> = _userIdentity.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = observeMessagesUseCase(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peerEntity: StateFlow<PeerEntity?> = peerRepository.getAllKnownPeers().map { list ->
        list.find { it.userId == conversationId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPeerConnected: StateFlow<Boolean> = observeConnectedPeersUseCase().map { connectedList ->
        connectedList.any { it.userId == conversationId || it.endpointId == conversationId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _replyingTo = MutableStateFlow<MessageEntity?>(null)
    val replyingTo: StateFlow<MessageEntity?> = _replyingTo.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        loadIdentity()
        markAsRead()
        checkAndAutoConnect()
        requestPeerAvatarIfNeeded()
    }

    fun markAsRead() {
        viewModelScope.launch {
            markConversationAsReadUseCase(conversationId)
        }
    }

    private fun loadIdentity() {
        viewModelScope.launch {
            _userIdentity.value = getOrCreateIdentityUseCase()
        }
    }

    private fun requestPeerAvatarIfNeeded() {
        viewModelScope.launch {
            chatRepository.requestPeerAvatar(conversationId)
        }
    }

    private fun checkAndAutoConnect() {
        viewModelScope.launch {
            val connected = observeConnectedPeersUseCase().value.any { it.userId == conversationId || it.endpointId == conversationId }
            if (!connected) {
                val discovered = observeDiscoveredPeersUseCase().value.find { it.userId == conversationId || it.endpointId == conversationId }
                if (discovered != null) {
                    connectPeerUseCase(discovered.endpointId)
                }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun setReplyingTo(message: MessageEntity?) {
        _replyingTo.value = message
    }

    fun getMeshHopDistance(): Int? {
        val route = peerRepository.getRoute(conversationId)
        return route?.hopCount
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        val currentReply = _replyingTo.value
        _inputText.value = ""
        _replyingTo.value = null

        viewModelScope.launch {
            val connected = observeConnectedPeersUseCase().value.any { it.userId == conversationId || it.endpointId == conversationId }
            if (!connected) {
                val discovered = observeDiscoveredPeersUseCase().value.find { it.userId == conversationId || it.endpointId == conversationId }
                if (discovered != null) {
                    connectPeerUseCase(discovered.endpointId)
                }
            }

            sendMessageUseCase(
                recipientId = conversationId,
                recipientName = peerName,
                content = text,
                replyToMessageId = currentReply?.messageId,
                replyToSenderId = currentReply?.senderId,
                replyToSenderName = currentReply?.senderName,
                replyToPreview = currentReply?.content?.take(100),
                replyToMessageType = "TEXT"
            )
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            val connected = observeConnectedPeersUseCase().value.any { it.userId == conversationId || it.endpointId == conversationId }
            if (!connected) {
                val discovered = observeDiscoveredPeersUseCase().value.find { it.userId == conversationId || it.endpointId == conversationId }
                if (discovered != null) {
                    connectPeerUseCase(discovered.endpointId)
                }
            }
            retrySendMessageUseCase(messageId)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            deleteMessageUseCase(messageId)
            _toastMessage.value = "Message deleted"
        }
    }

    fun deleteConversation() {
        viewModelScope.launch {
            deleteConversationUseCase(conversationId)
            _toastMessage.value = "Conversation deleted"
        }
    }

    fun toggleStar(message: MessageEntity) {
        viewModelScope.launch {
            toggleStarMessageUseCase(message.messageId, !message.isStarred)
            _toastMessage.value = if (!message.isStarred) "Message starred" else "Message unstarred"
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    class Factory(
        private val conversationId: String,
        private val peerName: String,
        private val peerAvatarColor: String?,
        private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
        private val observeMessagesUseCase: ObserveMessagesUseCase,
        private val sendMessageUseCase: SendMessageUseCase,
        private val retrySendMessageUseCase: RetrySendMessageUseCase,
        private val deleteMessageUseCase: DeleteMessageUseCase,
        private val deleteConversationUseCase: DeleteConversationUseCase,
        private val markConversationAsReadUseCase: MarkConversationAsReadUseCase,
        private val toggleStarMessageUseCase: ToggleStarMessageUseCase,
        private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
        private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
        private val connectPeerUseCase: ConnectPeerUseCase,
        private val peerRepository: PeerRepository,
        private val chatRepository: ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(
                conversationId,
                peerName,
                peerAvatarColor,
                getOrCreateIdentityUseCase,
                observeMessagesUseCase,
                sendMessageUseCase,
                retrySendMessageUseCase,
                deleteMessageUseCase,
                deleteConversationUseCase,
                markConversationAsReadUseCase,
                toggleStarMessageUseCase,
                observeConnectedPeersUseCase,
                observeDiscoveredPeersUseCase,
                connectPeerUseCase,
                peerRepository,
                chatRepository
            ) as T
        }
    }
}
