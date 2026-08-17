package com.offlinemesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.domain.usecase.ConnectPeerUseCase
import com.offlinemesh.app.domain.usecase.GetOrCreateIdentityUseCase
import com.offlinemesh.app.domain.usecase.ObserveConnectedPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveDiscoveredPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveMessagesUseCase
import com.offlinemesh.app.domain.usecase.RetrySendMessageUseCase
import com.offlinemesh.app.domain.usecase.SendMessageUseCase
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
    private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
    private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
    private val connectPeerUseCase: ConnectPeerUseCase
) : ViewModel() {

    private val _userIdentity = MutableStateFlow<UserIdentity?>(null)
    val userIdentity: StateFlow<UserIdentity?> = _userIdentity.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = observeMessagesUseCase(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPeerConnected: StateFlow<Boolean> = observeConnectedPeersUseCase().map { connectedList ->
        connectedList.any { it.userId == conversationId || it.endpointId == conversationId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    init {
        loadIdentity()
        checkAndAutoConnect()
    }

    private fun loadIdentity() {
        viewModelScope.launch {
            _userIdentity.value = getOrCreateIdentityUseCase()
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

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _inputText.value = ""
        viewModelScope.launch {
            // If peer is discovered but not connected, trigger connect
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
                content = text
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

    class Factory(
        private val conversationId: String,
        private val peerName: String,
        private val peerAvatarColor: String?,
        private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
        private val observeMessagesUseCase: ObserveMessagesUseCase,
        private val sendMessageUseCase: SendMessageUseCase,
        private val retrySendMessageUseCase: RetrySendMessageUseCase,
        private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
        private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
        private val connectPeerUseCase: ConnectPeerUseCase
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
                observeConnectedPeersUseCase,
                observeDiscoveredPeersUseCase,
                connectPeerUseCase
            ) as T
        }
    }
}
