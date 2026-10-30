package com.offlinemesh.app.domain.mesh

import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class QueuedPacket(
    val packet: ProtocolPacket,
    val enqueuedTime: Long = System.currentTimeMillis(),
    var retryCount: Int = 0
)

interface StoreAndForwardManager {
    suspend fun queuePacket(packet: ProtocolPacket): Boolean
    suspend fun getPendingPacketsForPeer(peerUserId: String): List<ProtocolPacket>
    suspend fun getAllPendingPackets(): List<ProtocolPacket>
    suspend fun incrementRetryAndCheckExpired(packetId: String): Boolean
    suspend fun markPacketDelivered(packetId: String)
    suspend fun purgeExpiredPackets(maxAgeMillis: Long = MAX_PACKET_AGE_MILLIS)
    suspend fun clear()
    suspend fun size(): Int

    companion object {
        const val MAX_QUEUE_SIZE = 100
        const val MAX_PACKET_AGE_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
        const val MAX_RETRY_COUNT = 5
    }
}

class InMemoryStoreAndForwardManager : StoreAndForwardManager {
    private val mutex = Mutex()
    private val packetQueue = LinkedHashMap<String, QueuedPacket>()

    override suspend fun queuePacket(packet: ProtocolPacket): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        // Evict expired packets first
        packetQueue.entries.removeIf { now - it.value.enqueuedTime > StoreAndForwardManager.MAX_PACKET_AGE_MILLIS }

        // Enforce max capacity by removing oldest entry
        if (packetQueue.size >= StoreAndForwardManager.MAX_QUEUE_SIZE && !packetQueue.containsKey(packet.packetId)) {
            val oldestKey = packetQueue.keys.firstOrNull()
            if (oldestKey != null) {
                packetQueue.remove(oldestKey)
            }
        }

        packetQueue[packet.packetId] = QueuedPacket(packet = packet, enqueuedTime = now, retryCount = 0)
        true
    }

    override suspend fun getPendingPacketsForPeer(peerUserId: String): List<ProtocolPacket> = mutex.withLock {
        val now = System.currentTimeMillis()
        packetQueue.entries.removeIf { now - it.value.enqueuedTime > StoreAndForwardManager.MAX_PACKET_AGE_MILLIS }
        packetQueue.values
            .filter { it.packet.recipientId == peerUserId }
            .map { it.packet }
    }

    override suspend fun getAllPendingPackets(): List<ProtocolPacket> = mutex.withLock {
        val now = System.currentTimeMillis()
        packetQueue.entries.removeIf { now - it.value.enqueuedTime > StoreAndForwardManager.MAX_PACKET_AGE_MILLIS }
        packetQueue.values.map { it.packet }
    }

    override suspend fun incrementRetryAndCheckExpired(packetId: String): Boolean = mutex.withLock {
        val item = packetQueue[packetId] ?: return false
        item.retryCount++
        if (item.retryCount >= StoreAndForwardManager.MAX_RETRY_COUNT) {
            packetQueue.remove(packetId)
            return false // Expired due to retry limit exceeded
        }
        true
    }

    override suspend fun markPacketDelivered(packetId: String) = mutex.withLock {
        packetQueue.remove(packetId)
        Unit
    }

    override suspend fun purgeExpiredPackets(maxAgeMillis: Long) = mutex.withLock {
        val now = System.currentTimeMillis()
        packetQueue.entries.removeIf {
            (now - it.value.enqueuedTime >= maxAgeMillis) || (it.value.retryCount >= StoreAndForwardManager.MAX_RETRY_COUNT)
        }
        Unit
    }

    override suspend fun clear() = mutex.withLock {
        packetQueue.clear()
    }

    override suspend fun size(): Int = mutex.withLock {
        packetQueue.size
    }
}
