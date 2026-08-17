package com.offlinemesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.offlinemesh.app.core.model.PeerDevice
import com.offlinemesh.app.core.model.UserIdentity
import com.offlinemesh.app.data.local.entity.PeerEntity
import com.offlinemesh.app.domain.usecase.ConnectPeerUseCase
import com.offlinemesh.app.domain.usecase.DisconnectPeerUseCase
import com.offlinemesh.app.domain.usecase.GetOrCreateIdentityUseCase
import com.offlinemesh.app.domain.usecase.ObserveConnectedPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveDiscoveredPeersUseCase
import com.offlinemesh.app.domain.usecase.ObserveKnownPeersUseCase
import com.offlinemesh.app.domain.usecase.StartDiscoveryUseCase
import com.offlinemesh.app.domain.usecase.StopDiscoveryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NearbyViewModel(
    private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
    private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
    private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
    private val observeKnownPeersUseCase: ObserveKnownPeersUseCase,
    private val connectPeerUseCase: ConnectPeerUseCase,
    private val disconnectPeerUseCase: DisconnectPeerUseCase,
    private val startDiscoveryUseCase: StartDiscoveryUseCase,
    private val stopDiscoveryUseCase: StopDiscoveryUseCase
) : ViewModel() {

    private val _userIdentity = MutableStateFlow<UserIdentity?>(null)
    val userIdentity: StateFlow<UserIdentity?> = _userIdentity.asStateFlow()

    val discoveredPeers: StateFlow<List<PeerDevice>> = observeDiscoveredPeersUseCase()
    val connectedPeers: StateFlow<List<PeerDevice>> = observeConnectedPeersUseCase()

    val knownPeers: StateFlow<List<PeerEntity>> = observeKnownPeersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        loadIdentity()
    }

    private fun loadIdentity() {
        viewModelScope.launch {
            val identity = getOrCreateIdentityUseCase()
            _userIdentity.value = identity
        }
    }

    fun startScanning() {
        viewModelScope.launch {
            val identity = _userIdentity.value ?: getOrCreateIdentityUseCase()
            startDiscoveryUseCase(identity.displayName)
            _isScanning.value = true
        }
    }

    fun stopScanning() {
        stopDiscoveryUseCase()
        _isScanning.value = false
    }

    fun toggleScanning() {
        if (_isScanning.value) {
            stopScanning()
        } else {
            startScanning()
        }
    }

    fun connectPeer(endpointId: String) {
        connectPeerUseCase(endpointId)
    }

    fun disconnectPeer(endpointId: String) {
        disconnectPeerUseCase(endpointId)
    }

    class Factory(
        private val getOrCreateIdentityUseCase: GetOrCreateIdentityUseCase,
        private val observeDiscoveredPeersUseCase: ObserveDiscoveredPeersUseCase,
        private val observeConnectedPeersUseCase: ObserveConnectedPeersUseCase,
        private val observeKnownPeersUseCase: ObserveKnownPeersUseCase,
        private val connectPeerUseCase: ConnectPeerUseCase,
        private val disconnectPeerUseCase: DisconnectPeerUseCase,
        private val startDiscoveryUseCase: StartDiscoveryUseCase,
        private val stopDiscoveryUseCase: StopDiscoveryUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NearbyViewModel(
                getOrCreateIdentityUseCase,
                observeDiscoveredPeersUseCase,
                observeConnectedPeersUseCase,
                observeKnownPeersUseCase,
                connectPeerUseCase,
                disconnectPeerUseCase,
                startDiscoveryUseCase,
                stopDiscoveryUseCase
            ) as T
        }
    }
}
