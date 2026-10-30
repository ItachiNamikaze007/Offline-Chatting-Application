package com.offlinemesh.app

import com.google.common.truth.Truth.assertThat
import com.offlinemesh.app.data.transport.protocol.PacketType
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket
import com.offlinemesh.app.domain.mesh.DefaultMessageRelay
import com.offlinemesh.app.domain.mesh.InMemoryRoutingManager
import com.offlinemesh.app.domain.mesh.MessageDeduplicator
import com.offlinemesh.app.domain.mesh.RelayDecision
import org.junit.Test
import java.util.UUID

class MultiHopAckTest {

    @Test
    fun multiHopAck_propagatesBackThroughRelaysToOriginalSender() {
        val nodeA = "OFC-AAAA1111"
        val nodeB = "OFC-BBBB2222"
        val nodeC = "OFC-CCCC3333"
        val nodeD = "OFC-DDDD4444"

        val epC_B = "EP_C_TO_B"
        val epB_A = "EP_B_TO_A"

        // Set up routing tables for reverse ACK path (D -> C -> B -> A)
        val routingC = InMemoryRoutingManager().apply {
            addOrUpdateRoute(destinationUserId = nodeA, nextHopEndpointId = epC_B, nextHopUserId = nodeB, hopCount = 2)
        }
        val routingB = InMemoryRoutingManager().apply {
            addOrUpdateRoute(destinationUserId = nodeA, nextHopEndpointId = epB_A, nextHopUserId = nodeA, hopCount = 1)
        }
        val routingA = InMemoryRoutingManager()

        val relayC = DefaultMessageRelay(MessageDeduplicator(), routingC)
        val relayB = DefaultMessageRelay(MessageDeduplicator(), routingB)
        val relayA = DefaultMessageRelay(MessageDeduplicator(), routingA)

        val originalMessageId = UUID.randomUUID().toString()

        // 1. Phone D generates DELIVERY_ACK targeting A
        val ackPacketD = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.DELIVERY_ACK,
            senderId = nodeD,
            senderDisplayName = "Dave",
            senderPublicKey = "KEY_D",
            recipientId = nodeA,
            timestamp = System.currentTimeMillis(),
            ttl = 8,
            hopCount = 0,
            payload = originalMessageId
        )

        // 2. Phone C receives ACK from D: forwards to next hop towards A (epC_B)
        val decisionC = relayC.evaluate(ackPacketD, incomingEndpointId = "EP_C_FROM_D", localUserId = nodeC)
        assertThat(decisionC).isInstanceOf(RelayDecision.Forward::class.java)
        val forwardC = decisionC as RelayDecision.Forward
        assertThat(forwardC.targetEndpointId).isEqualTo(epC_B)
        assertThat(forwardC.updatedPacket.ttl).isEqualTo(7)
        assertThat(forwardC.updatedPacket.hopCount).isEqualTo(1)

        // 3. Phone B receives ACK from C: forwards to next hop towards A (epB_A)
        val decisionB = relayB.evaluate(forwardC.updatedPacket, incomingEndpointId = "EP_B_FROM_C", localUserId = nodeB)
        assertThat(decisionB).isInstanceOf(RelayDecision.Forward::class.java)
        val forwardB = decisionB as RelayDecision.Forward
        assertThat(forwardB.targetEndpointId).isEqualTo(epB_A)
        assertThat(forwardB.updatedPacket.ttl).isEqualTo(6)
        assertThat(forwardB.updatedPacket.hopCount).isEqualTo(2)

        // 4. Phone A receives ACK: destination is A, delivers locally!
        val decisionA = relayA.evaluate(forwardB.updatedPacket, incomingEndpointId = "EP_A_FROM_B", localUserId = nodeA)
        assertThat(decisionA).isInstanceOf(RelayDecision.DeliverLocally::class.java)
        val deliveredAck = (decisionA as RelayDecision.DeliverLocally).packet
        assertThat(deliveredAck.packetType).isEqualTo(PacketType.DELIVERY_ACK)
        assertThat(deliveredAck.recipientId).isEqualTo(nodeA)
        assertThat(deliveredAck.payload).isEqualTo(originalMessageId) // Acknowledged original message
    }
}
