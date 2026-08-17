package com.offlinemesh.app.data.transport

import com.offlinemesh.app.core.model.PeerDevice
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface TransportManager {
    val discoveredPeers: StateFlow<List<PeerDevice>>
    val connectedPeers: StateFlow<List<PeerDevice>>
    val incomingPackets: SharedFlow<ProtocolPacket>
    val isAdvertising: StateFlow<Boolean>
    val isDiscovering: StateFlow<Boolean>

    fun start(localUserIdentifier: String)
    fun stop()

    fun startAdvertising(localDisplayName: String)
    fun stopAdvertising()

    fun startDiscovery()
    fun stopDiscovery()

    fun connectToPeer(endpointId: String)
    fun disconnectPeer(endpointId: String)

    suspend fun sendPacket(packet: ProtocolPacket, destinationEndpointId: String? = null): Boolean
    suspend fun broadcastPacket(packet: ProtocolPacket): Boolean
}
