package com.offlinemesh.app.domain.repository

import com.offlinemesh.app.core.model.PeerDevice
import com.offlinemesh.app.data.local.entity.PeerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PeerRepository {
    val discoveredPeers: StateFlow<List<PeerDevice>>
    val connectedPeers: StateFlow<List<PeerDevice>>
    val isAdvertising: StateFlow<Boolean>
    val isDiscovering: StateFlow<Boolean>

    fun startDiscoveryAndAdvertising(localDisplayName: String)
    fun stopDiscoveryAndAdvertising()

    fun connectToPeer(endpointId: String)
    fun disconnectPeer(endpointId: String)

    fun getAllKnownPeers(): Flow<List<PeerEntity>>
    suspend fun getPeer(userId: String): PeerEntity?
    suspend fun saveOrUpdatePeer(peer: PeerEntity)
}
