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

class RouteDiscoveryTest {

    @Test
    fun routeDiscovery_requestAndReply_learnsCompleteEndToEndPath() {
        val nodeA = "OFC-AAAA1111"
        val nodeB = "OFC-BBBB2222"
        val nodeC = "OFC-CCCC3333"
        val nodeD = "OFC-DDDD4444"

        val epB_A = "EP_B_TO_A"
        val epB_C = "EP_B_TO_C"
        val epC_B = "EP_C_TO_B"
        val epC_D = "EP_C_TO_D"
        val epD_C = "EP_D_TO_C"

        val routingA = InMemoryRoutingManager()
        val routingB = InMemoryRoutingManager()
        val routingC = InMemoryRoutingManager()
        val routingD = InMemoryRoutingManager()

        val relayB = DefaultMessageRelay(MessageDeduplicator(), routingB)
        val relayC = DefaultMessageRelay(MessageDeduplicator(), routingC)
        val relayD = DefaultMessageRelay(MessageDeduplicator(), routingD)

        // 1. A initiates ROUTE_REQUEST looking for D
        val reqPacketA = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.ROUTE_REQUEST,
            senderId = nodeA,
            senderDisplayName = "Alice",
            senderPublicKey = "KEY_A",
            recipientId = nodeD,
            timestamp = System.currentTimeMillis(),
            ttl = 8,
            hopCount = 0,
            payload = ""
        )

        // 2. B receives ROUTE_REQUEST from A: learns route to A via epB_A
        routingB.addOrUpdateRoute(
            destinationUserId = reqPacketA.senderId,
            nextHopEndpointId = epB_A,
            nextHopUserId = nodeA,
            hopCount = reqPacketA.hopCount + 1
        )
        val decisionB = relayB.evaluate(reqPacketA, incomingEndpointId = epB_A, localUserId = nodeB)
        assertThat(decisionB).isInstanceOf(RelayDecision.Forward::class.java)
        val forwardB = decisionB as RelayDecision.Forward
        assertThat(forwardB.excludeEndpointId).isEqualTo(epB_A) // Does not flood back to A

        // 3. C receives ROUTE_REQUEST forwarded from B: learns route to A via epC_B
        val reqPacketB = forwardB.updatedPacket
        routingC.addOrUpdateRoute(
            destinationUserId = reqPacketB.senderId,
            nextHopEndpointId = epC_B,
            nextHopUserId = nodeB,
            hopCount = reqPacketB.hopCount + 1
        )
        val decisionC = relayC.evaluate(reqPacketB, incomingEndpointId = epC_B, localUserId = nodeC)
        assertThat(decisionC).isInstanceOf(RelayDecision.Forward::class.java)
        val forwardC = decisionC as RelayDecision.Forward
        assertThat(forwardC.excludeEndpointId).isEqualTo(epC_B)

        // 4. D receives ROUTE_REQUEST: delivers locally because recipient is D
        val reqPacketC = forwardC.updatedPacket
        val decisionD = relayD.evaluate(reqPacketC, incomingEndpointId = epD_C, localUserId = nodeD)
        assertThat(decisionD).isInstanceOf(RelayDecision.DeliverLocally::class.java)

        // 5. D generates ROUTE_REPLY directed to A
        val replyPacketD = ProtocolPacket(
            packetId = UUID.randomUUID().toString(),
            packetType = PacketType.ROUTE_REPLY,
            senderId = nodeD,
            senderDisplayName = "Dave",
            senderPublicKey = "KEY_D",
            recipientId = nodeA,
            timestamp = System.currentTimeMillis(),
            ttl = 8,
            hopCount = 0,
            payload = "#10B981"
        )

        // 6. C relays ROUTE_REPLY to next hop towards A (which is epC_B)
        val decisionReplyC = relayC.evaluate(replyPacketD, incomingEndpointId = epD_C, localUserId = nodeC)
        assertThat(decisionReplyC).isInstanceOf(RelayDecision.Forward::class.java)
        val forwardReplyC = decisionReplyC as RelayDecision.Forward
        assertThat(forwardReplyC.targetEndpointId).isEqualTo(epC_B)

        // 7. B relays ROUTE_REPLY to next hop towards A (which is epB_A)
        val decisionReplyB = relayB.evaluate(forwardReplyC.updatedPacket, incomingEndpointId = epB_C, localUserId = nodeB)
        assertThat(decisionReplyB).isInstanceOf(RelayDecision.Forward::class.java)
        val forwardReplyB = decisionReplyB as RelayDecision.Forward
        assertThat(forwardReplyB.targetEndpointId).isEqualTo(epB_A)

        // 8. A receives ROUTE_REPLY: learns route to D via its local connection to B!
        val replyAtA = forwardReplyB.updatedPacket
        routingA.addOrUpdateRoute(
            destinationUserId = replyAtA.senderId,
            nextHopEndpointId = "EP_A_TO_B",
            nextHopUserId = nodeB,
            hopCount = replyAtA.hopCount + 1,
            displayName = replyAtA.senderDisplayName,
            publicKey = replyAtA.senderPublicKey
        )

        val routeToD = routingA.getRoute(nodeD)
        assertThat(routeToD).isNotNull()
        assertThat(routeToD!!.nextHopEndpointId).isEqualTo("EP_A_TO_B")
        assertThat(routeToD.hopCount).isEqualTo(3)
        assertThat(routeToD.displayName).isEqualTo("Dave")
        assertThat(routeToD.publicKey).isEqualTo("KEY_D")
    }

    @Test
    fun routingManager_expiresStaleRoutes() {
        val routing = InMemoryRoutingManager()
        routing.addOrUpdateRoute(
            destinationUserId = "OFC-OLD00001",
            nextHopEndpointId = "EP_1",
            nextHopUserId = "OFC-PEER",
            hopCount = 1
        )

        assertThat(routing.getNextHopEndpoint("OFC-OLD00001")).isEqualTo("EP_1")

        // Force expiration
        routing.expireStaleRoutes(maxAgeMillis = 0L)
        assertThat(routing.getNextHopEndpoint("OFC-OLD00001")).isNull()
    }
}
