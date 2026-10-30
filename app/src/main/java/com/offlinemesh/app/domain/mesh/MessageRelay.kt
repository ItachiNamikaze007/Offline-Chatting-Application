package com.offlinemesh.app.domain.mesh

import com.offlinemesh.app.data.transport.protocol.PacketType
import com.offlinemesh.app.data.transport.protocol.ProtocolPacket

sealed class RelayDecision {
    /**
     * Packet should be forwarded over radio transport.
     * @param updatedPacket Packet with decremented TTL and incremented hopCount.
     * @param targetEndpointId Specific next-hop endpoint ID for unicast, or null to broadcast.
     * @param excludeEndpointId Endpoint ID to exclude from broadcast (usually incoming endpoint).
     */
    data class Forward(
        val updatedPacket: ProtocolPacket,
        val targetEndpointId: String? = null,
        val excludeEndpointId: String? = null
    ) : RelayDecision()

    /**
     * Packet reached its intended destination and should be delivered to the local device.
     */
    data class DeliverLocally(val packet: ProtocolPacket) : RelayDecision()

    /**
     * Packet should be discarded.
     */
    data class Drop(val reason: DropReason) : RelayDecision()
}

enum class DropReason {
    DUPLICATE,
    TTL_EXPIRED,
    ORIGINATED_FROM_SELF,
    INVALID_SIGNATURE,
    UNROUTABLE,
    LOOP_DETECTED
}

interface MessageRelay {
    /**
     * Evaluates an incoming packet to decide whether to consume locally, forward, or drop.
     *
     * @param packet The incoming protocol packet
     * @param incomingEndpointId The physical transport endpoint ID where the packet was received
     * @param localUserId The local device's permanent OFC ID
     */
    fun evaluate(
        packet: ProtocolPacket,
        incomingEndpointId: String?,
        localUserId: String
    ): RelayDecision
}

/**
 * Standard implementation of multi-hop message relay logic.
 */
class DefaultMessageRelay(
    private val deduplicator: MessageDeduplicator,
    private val routingManager: RoutingManager
) : MessageRelay {

    override fun evaluate(
        packet: ProtocolPacket,
        incomingEndpointId: String?,
        localUserId: String
    ): RelayDecision {
        // 1. Drop if originated from self
        if (packet.senderId == localUserId) {
            return RelayDecision.Drop(DropReason.ORIGINATED_FROM_SELF)
        }

        // 2. Drop if duplicate (already seen / processed)
        if (deduplicator.isDuplicate(packet.packetId)) {
            return RelayDecision.Drop(DropReason.DUPLICATE)
        }

        // 3. Check if intended for local user or is a broadcast packet that must be delivered locally
        val isRecipientLocal = packet.recipientId == localUserId || packet.recipientId == ProtocolPacket.BROADCAST_ID
        if (isRecipientLocal) {
            return RelayDecision.DeliverLocally(packet)
        }

        // 4. Check TTL for forwarding
        if (packet.ttl <= 1) {
            return RelayDecision.Drop(DropReason.TTL_EXPIRED)
        }

        // 5. Prepare forwarded packet (decrement TTL, increment hopCount, keep packetId unchanged)
        val forwardedPacket = packet.copy(
            ttl = packet.ttl - 1,
            hopCount = packet.hopCount + 1
        )

        // 6. Next-hop routing lookup
        val nextHopEndpointId = routingManager.getNextHopEndpoint(packet.recipientId)

        return when {
            // Unicast route exists in routing table
            nextHopEndpointId != null -> {
                if (nextHopEndpointId == incomingEndpointId) {
                    // Prevent looping back to the same endpoint it arrived from
                    RelayDecision.Drop(DropReason.LOOP_DETECTED)
                } else {
                    RelayDecision.Forward(
                        updatedPacket = forwardedPacket,
                        targetEndpointId = nextHopEndpointId,
                        excludeEndpointId = incomingEndpointId
                    )
                }
            }

            // Route request or broadcast frame: flood to neighbors excluding incoming endpoint
            packet.packetType == PacketType.ROUTE_REQUEST || packet.recipientId == ProtocolPacket.BROADCAST_ID -> {
                RelayDecision.Forward(
                    updatedPacket = forwardedPacket,
                    targetEndpointId = null,
                    excludeEndpointId = incomingEndpointId
                )
            }

            // Unicast packet with unknown route: drop from relay (sender or node store-and-forward can handle)
            else -> {
                RelayDecision.Drop(DropReason.UNROUTABLE)
            }
        }
    }
}
