package com.offlinemesh.app.data.repository

import com.offlinemesh.app.core.model.PeerDevice
import com.offlinemesh.app.data.local.dao.PeerDao
import com.offlinemesh.app.data.local.entity.PeerEntity
import com.offlinemesh.app.data.transport.TransportManager
import com.offlinemesh.app.domain.repository.PeerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PeerRepositoryImpl(
    private val transportManager: TransportManager,
    private val peerDao: PeerDao
) : PeerRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val discoveredPeers: StateFlow<List<PeerDevice>> = transportManager.discoveredPeers
    override val connectedPeers: StateFlow<List<PeerDevice>> = transportManager.connectedPeers
    override val isAdvertising: StateFlow<Boolean> = transportManager.isAdvertising
    override val isDiscovering: StateFlow<Boolean> = transportManager.isDiscovering

    init {
        // Automatically save/update connected peers to local Room database
        scope.launch {
            transportManager.connectedPeers.collectLatest { peers ->
                peers.forEach { peer ->
                    if (peer.userId != null && peer.publicKey != null) {
                        peerDao.insertOrUpdatePeer(
                            PeerEntity(
                                userId = peer.userId,
                                displayName = peer.displayName,
                                publicKey = peer.publicKey,
                                avatarColorHex = peer.avatarColorHex ?: "#3B82F6",
                                lastSeen = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }

    override fun startDiscoveryAndAdvertising(localDisplayName: String) {
        transportManager.startAdvertising(localDisplayName)
        transportManager.startDiscovery()
    }

    override fun stopDiscoveryAndAdvertising() {
        transportManager.stopAdvertising()
        transportManager.stopDiscovery()
    }

    override fun connectToPeer(endpointId: String) {
        transportManager.connectToPeer(endpointId)
    }

    override fun disconnectPeer(endpointId: String) {
        transportManager.disconnectPeer(endpointId)
    }

    override fun getAllKnownPeers(): Flow<List<PeerEntity>> {
        return peerDao.getAllPeers()
    }

    override suspend fun getPeer(userId: String): PeerEntity? {
        return peerDao.getPeerById(userId)
    }

    override suspend fun saveOrUpdatePeer(peer: PeerEntity) {
        peerDao.insertOrUpdatePeer(peer)
    }
}
