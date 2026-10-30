package com.offlinemesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.offlinemesh.app.core.model.Community
import com.offlinemesh.app.core.model.DeliveryStatus
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.data.local.entity.PeerEntity
import com.offlinemesh.app.domain.repository.ChatRepository
import com.offlinemesh.app.domain.repository.CommunityRepository
import com.offlinemesh.app.domain.repository.IdentityRepository
import com.offlinemesh.app.domain.repository.PeerRepository
import com.offlinemesh.app.domain.usecase.GetOrCreateIdentityUseCase
import com.offlinemesh.app.domain.usecase.ObserveConnectedPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveDiscoveredPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveKnownPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveRecentConversationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationItemUiModel(
    val conversationId: String,
    val peerName: String,
    val avatarColorHex: String?,
    val avatarImagePath: String? = null,
    val lastMessageContent: String,
    val lastMessageTimestamp: Long,
    val lastMessageStatus: DeliveryStatus,
    val isOutgoing: Boolean,
    val unreadCount: Int = 0,
    val isConnected: Boolean = false,
    val isMeshReachable: Boolean = false
)

class HomeViewModel(
    private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
    private val observeRecentConversationsUseCase: ObserveRecentConversationsUseCase,
    private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
    private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
    private val observeKnownPeersUseCase: ObserveKnownPeersUseCase,
    private val communityRepository: CommunityRepository,
    private val identityRepository: IdentityRepository,
    private val peerRepository: PeerRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    // Identity is directly observed from IdentityRepository StateFlow (auto-refreshes immediately on profile save)
    val userIdentity: StateFlow<UserIdentity?> = identityRepository.currentIdentity

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val discoveredPeers = observeDiscoveredPeersUseCase()
    val connectedPeers = observeConnectedPeersUseCase()
    val knownPeers = observeKnownPeersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawRecentConversations: StateFlow<List<MessageEntity>> = observeRecentConversationsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined UI flow for conversations with search filtering, unread badges, peer names, and connection status
    val conversationItems: StateFlow<List<ConversationItemUiModel>> = combine(
        rawRecentConversations,
        knownPeers,
        connectedPeers,
        _searchQuery
    ) { conversations, peers, connected, query ->
        val peerMap = peers.associateBy { it.userId }
        val connectedIds = connected.mapNotNull { it.userId }.toSet()

        val items = conversations.map { msg ->
            val peer = peerMap[msg.conversationId]
            val peerName = peer?.displayName
                ?: if (msg.isOutgoing) msg.recipientId else msg.senderName
            val avatarColor = peer?.avatarColorHex
            val isDirectConnected = msg.conversationId in connectedIds
            val isMeshReachable = isDirectConnected || peerRepository.getRoute(msg.conversationId) != null

            ConversationItemUiModel(
                conversationId = msg.conversationId,
                peerName = peerName,
                avatarColorHex = avatarColor,
                avatarImagePath = peer?.avatarImagePath,
                lastMessageContent = msg.content,
                lastMessageTimestamp = msg.timestamp,
                lastMessageStatus = msg.status,
                isOutgoing = msg.isOutgoing,
                unreadCount = if (!msg.isOutgoing && !msg.isRead) 1 else 0,
                isConnected = isDirectConnected,
                isMeshReachable = isMeshReachable
            )
        }

        if (query.trim().isEmpty()) {
            items
        } else {
            val q = query.trim().lowercase()
            items.filter {
                it.peerName.lowercase().contains(q) ||
                        it.conversationId.lowercase().contains(q) ||
                        it.lastMessageContent.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communities: StateFlow<List<Community>> = communityRepository.getAllCommunities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadIdentity()
    }

    fun loadIdentity() {
        viewModelScope.launch {
            getOrCreateIdentityUseCase()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    class Factory(
        private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
        private val observeRecentConversationsUseCase: ObserveRecentConversationsUseCase,
        private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
        private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
        private val observeKnownPeersUseCase: ObserveKnownPeersUseCase,
        private val communityRepository: CommunityRepository,
        private val identityRepository: IdentityRepository,
        private val peerRepository: PeerRepository,
        private val chatRepository: ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                getOrCreateIdentityUseCase,
                observeRecentConversationsUseCase,
                observeDiscoveredPeersUseCase,
                observeConnectedPeersUseCase,
                observeKnownPeersUseCase,
                communityRepository,
                identityRepository,
                peerRepository,
                chatRepository
            ) as T
        }
    }
}
