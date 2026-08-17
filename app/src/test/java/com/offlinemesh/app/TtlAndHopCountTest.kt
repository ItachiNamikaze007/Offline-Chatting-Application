package com.offlinemesh.app

import com.google.common.truth.Truth.assertThat
import com.offlinemesh.app.data.transport.protocol.PacketType
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import com.offlinemesh.app.domain.mesh.DefaultMessageRelay
import com.offlinemesh.app.domain.mesh.DropReason
import com.offlinemesh.app.domain.mesh.MessageDeduplicator
import com.offlinemesh.app.domain.mesh.RelayDecision
import org.junit.Test
import java.util.UUID

class TtlAndHopCountTest {

    private val deduplicator = MessageDeduplicator()
    private val relay = DefaultMessageRelay(deduplicator)
    private val localUserId = "OFC-LOCAL001"

    @Test
    fun relay_deliversLocally_whenRecipientIsSelf() {
        val packet = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.DATA_DIRECT,
            senderId = "OFC-REMOTE01",
            senderDisplayName = "Remote",
            senderPublicKey = "KEY",
            recipientId = localUserId,
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            hopCount = 0,
            payload = "Hello Local"
        )

        val decision = relay.evaluate(packet, localUserId)
        assertThat(decision).isInstanceOf(RelayDecision.DeliverLocally::class.java)
        val delivered = (decision as RelayDecision.DeliverLocally).packet
        assertThat(delivered.payload).isEqualTo("Hello Local")
    }

    @Test
    fun relay_forwardsPacket_withDecrementedTtlAndIncrementedHops() {
        val packet = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.DATA_DIRECT,
            senderId = "OFC-REMOTE01",
            senderDisplayName = "Remote",
            senderPublicKey = "KEY",
            recipientId = "OFC-DESTINAT", // Not local
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            hopCount = 1,
            payload = "Forward me"
        )

        val decision = relay.evaluate(packet, localUserId)
        assertThat(decision).isInstanceOf(RelayDecision.Forward::class.java)
        val forwarded = (decision as RelayDecision.Forward).updatedPacket
        assertThat(forwarded.ttl).isEqualTo(2) // Decremented from 3
        assertThat(forwarded.hopCount).isEqualTo(2) // Incremented from 1
    }

    @Test
    fun relay_dropsPacket_whenTtlIsExpired() {
        val packet = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.DATA_DIRECT,
            senderId = "OFC-REMOTE01",
            senderDisplayName = "Remote",
            senderPublicKey = "KEY",
            recipientId = "OFC-DESTINAT",
            timestamp = System.currentTimeMillis(),
            ttl = 1, // Will expire because ttl <= 1 cannot forward
            hopCount = 3,
            payload = "Expired"
        )

        val decision = relay.evaluate(packet, localUserId)
        assertThat(decision).isInstanceOf(RelayDecision.Drop::class.java)
        val drop = decision as RelayDecision.Drop
        assertThat(drop.reason).isEqualTo(DropReason.TTL_EXPIRED)
    }

    @Test
    fun relay_dropsPacket_whenOriginatedFromSelf() {
        val packet = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.DATA_DIRECT,
            senderId = localUserId, // Self
            senderDisplayName = "Me",
            senderPublicKey = "KEY",
            recipientId = "OFC-DESTINAT",
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            hopCount = 0,
            payload = "Loopback"
        )

        val decision = relay.evaluate(packet, localUserId)
        assertThat(decision).isInstanceOf(RelayDecision.Drop::class.java)
        val drop = decision as RelayDecision.Drop
        assertThat(drop.reason).isEqualTo(DropReason.ORIGINATED_FROM_SELF)
    }
}
