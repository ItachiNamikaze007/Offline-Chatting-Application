package com.offlinemesh.app.domain.mesh

import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface StoreAndForwardManager {
    suspend fun queuePacket(packet: ProtocolPacket)
    suspend fun getPendingPacketsForPeer(peerUserId: String): List<ProtocolPacket>
    suspend fun markPacketDelivered(packetId: String)
    suspend fun purgeExpiredPackets(maxAgeMillis: Long)
}

class InMemoryStoreAndForwardManager : StoreAndForwardManager {
    private val mutex = Mutex()
    private val packetQueue = mutableMapOf<String, ProtocolPacket>()

    override suspend fun queuePacket(packet: ProtocolPacket) = mutex.withLock {
        packetQueue[packet.packetId] = packet
    }

    override suspend fun getPendingPacketsForPeer(peerUserId: String): List<ProtocolPacket> = mutex.withLock {
        packetQueue.values.filter { it.recipientId == peerUserId }
    }

    override suspend fun markPacketDelivered(packetId: String) = mutex.withLock {
        packetQueue.remove(packetId)
        Unit
    }

    override suspend fun purgeExpiredPackets(maxAgeMillis: Long) = mutex.withLock {
        val now = System.currentTimeMillis()
        packetQueue.entries.removeIf { now - it.value.timestamp > maxAgeMillis }
        Unit
    }
}
