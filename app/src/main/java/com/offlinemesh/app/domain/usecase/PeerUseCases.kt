package com.offlinemesh.app.domain.usecase

import com.offlinemesh.app.core.model.PeerDevice
import com.offlinemesh.app.data.local.entity.PeerEntity
import com.offlinemesh.app.domain.repository.PeerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ObserveDiscoveredPeersUseCase(
    private val peerRepository: PeerRepository
) {
    operator fun invoke(): StateFlow<List<PeerDevice>> {
        return peerRepository.discoveredPeers
    }
}

class ObserveConnectedPeersUseCase(
    private val peerRepository: PeerRepository
) {
    operator fun invoke(): StateFlow<List<PeerDevice>> {
        return peerRepository.connectedPeers
    }
}

class ConnectPeerUseCase(
    private val peerRepository: PeerRepository
) {
    operator fun invoke(endpointId: String) {
        peerRepository.connectToPeer(endpointId)
    }
}

class DisconnectPeerUseCase(
    private val peerRepository: PeerRepository
) {
    operator fun invoke(endpointId: String) {
        peerRepository.disconnectPeer(endpointId)
    }
}

class StartDiscoveryUseCase(
    private val peerRepository: PeerRepository
) {
    operator fun invoke(localDisplayName: String) {
        peerRepository.startDiscoveryAndAdvertising(localDisplayName)
    }
}

class StopDiscoveryUseCase(
    private val peerRepository: PeerRepository
) {
    operator fun invoke() {
        peerRepository.stopDiscoveryAndAdvertising()
    }
}

class ObserveKnownPeersUseCase(
    private val peerRepository: PeerRepository
) {
    operator fun invoke(): Flow<List<PeerEntity>> {
        return peerRepository.getAllKnownPeers()
    }
}
