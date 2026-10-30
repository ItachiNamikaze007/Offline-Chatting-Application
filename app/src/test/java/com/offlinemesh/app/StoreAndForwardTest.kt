package com.offlinemesh.app

import com.google.common.truth.Truth.assertThat
import com.offlinemesh.app.data.transport.protocol.PacketType
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import com.offlinemesh.app.domain.mesh.InMemoryStoreAndForwardManager
import com.offlinemesh.app.domain.mesh.StoreAndForwardManager
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class StoreAndForwardTest {

    @Test
    fun storeAndForward_queuesAndRetrievesPacketsForPeer() = runTest {
        val manager = InMemoryStoreAndForwardManager()

        val packet1 = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.DATA_DIRECT,
            senderId = "OFC-SENDER01",
            senderDisplayName = "Sender",
            senderPublicKey = "KEY_S",
            recipientId = "OFC-TARGET01",
            timestamp = System.currentTimeMillis(),
            ttl = 8,
            hopCount = 0,
            payload = "Pending Message 1"
        )

        val packet2 = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.DATA_DIRECT,
            senderId = "OFC-SENDER01",
            senderDisplayName = "Sender",
            senderPublicKey = "KEY_S",
            recipientId = "OFC-OTHER002",
            timestamp = System.currentTimeMillis(),
            ttl = 8,
            hopCount = 0,
            payload = "Other Message"
        )

        manager.queuePacket(packet1)
        manager.queuePacket(packet2)

        val targetPackets = manager.getPendingPacketsForPeer("OFC-TARGET01")
        assertThat(targetPackets).hasSize(1)
        assertThat(targetPackets[0].packetId).isEqualTo(packet1.packetId)
        assertThat(targetPackets[0].payload).isEqualTo("Pending Message 1")

        // Mark delivered removes it
        manager.markPacketDelivered(packet1.packetId)
        val remaining = manager.getPendingPacketsForPeer("OFC-TARGET01")
        assertThat(remaining).isEmpty()
    }

    @Test
    fun storeAndForward_enforcesMaxQueueLimit() = runTest {
        val manager = InMemoryStoreAndForwardManager()

        // Fill past MAX_QUEUE_SIZE
        for (i in 0 until StoreAndForwardManager.MAX_QUEUE_SIZE + 10) {
            val packet = ProtocolPacket(
                packetId = "PACKET-$i",
                packetType = PacketType.DATA_DIRECT,
                senderId = "OFC-SENDER01",
                senderDisplayName = "Sender",
                senderPublicKey = "KEY_S",
                recipientId = "OFC-TARGET01",
                timestamp = System.currentTimeMillis(),
                ttl = 8,
                hopCount = 0,
                payload = "Message $i"
            )
            manager.queuePacket(packet)
        }

        val totalSize = manager.size()
        assertThat(totalSize).isAtMost(StoreAndForwardManager.MAX_QUEUE_SIZE)
    }

    @Test
    fun storeAndForward_purgesExpiredPackets() = runTest {
        val manager = InMemoryStoreAndForwardManager()
        val packet = ProtocolPacket(
            packetId = "EXPIRED-1",
            packetType = PacketType.DATA_DIRECT,
            senderId = "OFC-SENDER01",
            senderDisplayName = "Sender",
            senderPublicKey = "KEY_S",
            recipientId = "OFC-TARGET01",
            timestamp = System.currentTimeMillis() - 100_000L,
            ttl = 8,
            hopCount = 0,
            payload = "Old Message"
        )

        manager.queuePacket(packet)
        assertThat(manager.size()).isEqualTo(1)

        // Purge with max age 0
        manager.purgeExpiredPackets(maxAgeMillis = 0L)
        assertThat(manager.size()).isEqualTo(0)
    }
}
