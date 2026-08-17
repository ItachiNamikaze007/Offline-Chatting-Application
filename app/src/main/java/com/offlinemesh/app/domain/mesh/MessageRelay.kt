package com.offlinemesh.app.domain.mesh

import com.offlinemesh.app.data.transport.protocol.ProtocolPacket

sealed class RelayDecision {
    data class Forward(val updatedPacket: ProtocolPacket) : RelayDecision()
    data class DeliverLocally(val packet: ProtocolPacket) : RelayDecision()
    data class Drop(val reason: DropReason) : RelayDecision()
}

enum class DropReason {
    DUPLICATE,
    TTL_EXPIRED,
    ORIGINATED_FROM_SELF,
    INVALID_SIGNATURE,
    UNROUTABLE
}

interface MessageRelay {
    /**
     * Evaluates an incoming packet to decide whether to consume locally, forward, or drop.
     */
    fun evaluate(packet: ProtocolPacket, localUserId: String): RelayDecision
}

/**
 * Standard implementation of message relay logic.
 */
class DefaultMessageRelay(
    private val deduplicator: MessageDeduplicator
) : MessageRelay {

    override fun evaluate(packet: ProtocolPacket, localUserId: String): RelayDecision {
        // 1. Drop if originated from self
        if (packet.senderId == localUserId) {
            return RelayDecision.Drop(DropReason.ORIGINATED_FROM_SELF)
        }

        // 2. Drop if duplicate
        if (deduplicator.isDuplicate(packet.packetId)) {
            return RelayDecision.Drop(DropReason.DUPLICATE)
        }

        // 3. Check if intended for local user
        if (packet.recipientId == localUserId || packet.recipientId == ProtocolPacket.BROADCAST_ID) {
            return RelayDecision.DeliverLocally(packet)
        }

        // 4. Check TTL for forwarding
        if (packet.ttl <= 1) {
            return RelayDecision.Drop(DropReason.TTL_EXPIRED)
        }

        // 5. Prepare packet for next-hop forward (decrement TTL, increment hopCount)
        val forwardedPacket = packet.copy(
            ttl = packet.ttl - 1,
            hopCount = packet.hopCount + 1
        )

        return RelayDecision.Forward(forwardedPacket)
    }
}
