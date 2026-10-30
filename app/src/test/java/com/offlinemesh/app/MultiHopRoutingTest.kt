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

/**
 * Validates Phone A -> Phone B -> Phone C -> Phone D multi-hop forwarding chain.
 */
class MultiHopRoutingTest {

    @Test
    fun multiHop_fourNodeRelayChain_deliversSuccessfullyToDestination() {
        // Node IDs
        val nodeA = "OFC-AAAA1111"
        val nodeB = "OFC-BBBB2222"
        val nodeC = "OFC-CCCC3333"
        val nodeD = "OFC-DDDD4444"

        // Endpoint IDs
        val epA_B = "EP_A_TO_B"
        val epB_A = "EP_B_TO_A"
        val epB_C = "EP_B_TO_C"
        val epC_B = "EP_C_TO_B"
        val epC_D = "EP_C_TO_D"
        val epD_C = "EP_D_TO_C"

        // Setup Node B relay & routing table
        val dedupB = MessageDeduplicator()
        val routingB = InMemoryRoutingManager()
        routingB.addOrUpdateRoute(destinationUserId = nodeD, nextHopEndpointId = epB_C, nextHopUserId = nodeC, hopCount = 2)
        val relayB = DefaultMessageRelay(dedupB, routingB)

        // Setup Node C relay & routing table
        val dedupC = MessageDeduplicator()
        val routingC = InMemoryRoutingManager()
        routingC.addOrUpdateRoute(destinationUserId = nodeD, nextHopEndpointId = epC_D, nextHopUserId = nodeD, hopCount = 1)
        val relayC = DefaultMessageRelay(dedupC, routingC)

        // Setup Node D receiver
        val dedupD = MessageDeduplicator()
        val routingD = InMemoryRoutingManager()
        val relayD = DefaultMessageRelay(dedupD, routingD)

        // STEP 1: Phone A generates original packet for Phone D
        val originalPacketId = UUID.randomUUID().toString()
        val packetFromA = ProtocolPacket(
            packetId = originalPacketId,
            packetType = PacketType.DATA_DIRECT,
            senderId = nodeA,
            senderDisplayName = "Alice",
            senderPublicKey = "KEY_A",
            recipientId = nodeD,
            timestamp = 1000L,
            ttl = 8,
            hopCount = 0,
            payload = "Hello Dave via MultiHop Mesh"
        )

        // STEP 2: Phone B receives packet from A over endpoint epB_A
        val decisionB = relayB.evaluate(packetFromA, incomingEndpointId = epB_A, localUserId = nodeB)
        assertThat(decisionB).isInstanceOf(RelayDecision.Forward::class.java)
        val forwardB = decisionB as RelayDecision.Forward
        assertThat(forwardB.targetEndpointId).isEqualTo(epB_C)
        assertThat(forwardB.updatedPacket.packetId).isEqualTo(originalPacketId)
        assertThat(forwardB.updatedPacket.ttl).isEqualTo(7) // 8 - 1
        assertThat(forwardB.updatedPacket.hopCount).isEqualTo(1) // 0 + 1
        assertThat(forwardB.updatedPacket.payload).isEqualTo("Hello Dave via MultiHop Mesh")

        // STEP 3: Phone C receives packet from B over endpoint epC_B
        val packetAtC = forwardB.updatedPacket
        val decisionC = relayC.evaluate(packetAtC, incomingEndpointId = epC_B, localUserId = nodeC)
        assertThat(decisionC).isInstanceOf(RelayDecision.Forward::class.java)
        val forwardC = decisionC as RelayDecision.Forward
        assertThat(forwardC.targetEndpointId).isEqualTo(epC_D)
        assertThat(forwardC.updatedPacket.packetId).isEqualTo(originalPacketId)
        assertThat(forwardC.updatedPacket.ttl).isEqualTo(6) // 7 - 1
        assertThat(forwardC.updatedPacket.hopCount).isEqualTo(2) // 1 + 1
        assertThat(forwardC.updatedPacket.payload).isEqualTo("Hello Dave via MultiHop Mesh")

        // STEP 4: Phone D receives packet from C over endpoint epD_C
        val packetAtD = forwardC.updatedPacket
        val decisionD = relayD.evaluate(packetAtD, incomingEndpointId = epD_C, localUserId = nodeD)
        assertThat(decisionD).isInstanceOf(RelayDecision.DeliverLocally::class.java)
        val delivered = (decisionD as RelayDecision.DeliverLocally).packet
        assertThat(delivered.packetId).isEqualTo(originalPacketId)
        assertThat(delivered.senderId).isEqualTo(nodeA)
        assertThat(delivered.recipientId).isEqualTo(nodeD)
        assertThat(delivered.hopCount).isEqualTo(2) // Traversed 2 hops B & C before arriving at D
        assertThat(delivered.payload).isEqualTo("Hello Dave via MultiHop Mesh")
    }
}
