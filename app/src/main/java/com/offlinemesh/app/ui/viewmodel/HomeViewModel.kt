package com.offlinemesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.offlinemesh.app.core.model.Community
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.domain.repository.CommunityRepository
import com.offlinemesh.app.domain.usecase.GetOrCreateIdentityUseCase
import com.offlinemesh.app.domain.usecase.ObserveConnectedPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveDiscoveredPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveRecentConversationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
    private val observeRecentConversationsUseCase: ObserveRecentConversationsUseCase,
    private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
    private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
    private val communityRepository: CommunityRepository
) : ViewModel() {

    private val _userIdentity = MutableStateFlow<UserIdentity?>(null)
    val userIdentity: StateFlow<UserIdentity?> = _userIdentity.asStateFlow()

    val recentConversations: StateFlow<List<MessageEntity>> = observeRecentConversationsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredPeers = observeDiscoveredPeersUseCase()
    val connectedPeers = observeConnectedPeersUseCase()

    val communities: StateFlow<List<Community>> = communityRepository.getAllCommunities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadIdentity()
    }

    fun loadIdentity() {
        viewModelScope.launch {
            _userIdentity.value = getOrCreateIdentityUseCase()
        }
    }

    class Factory(
        private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
        private val observeRecentConversationsUseCase: ObserveRecentConversationsUseCase,
        private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
        private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
        private val communityRepository: CommunityRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                getOrCreateIdentityUseCase,
                observeRecentConversationsUseCase,
                observeDiscoveredPeersUseCase,
                observeConnectedPeersUseCase,
                communityRepository
            ) as T
        }
    }
}
