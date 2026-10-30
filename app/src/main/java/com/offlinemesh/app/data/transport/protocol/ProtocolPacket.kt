package com.offlinemesh.app.data.transport.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class PacketType {
    HANDSHAKE,
    HANDSHAKE_ACK,
    DATA_DIRECT,
    DATA_COMMUNITY,
    DELIVERY_ACK,
    ROUTE_REQUEST,
    ROUTE_REPLY,
    ROUTE_ADVERTISEMENT,
    AVATAR_REQUEST,
    AVATAR_RESPONSE,
    PING
}

@Serializable
data class ProtocolPacket(
    val protocolVersion: Int = 2,
    val packetId: String,               // Unique packet UUID (immutable across mesh hops)
    val packetType: PacketType,         // HANDSHAKE, DATA_DIRECT, ROUTE_REQUEST, DELIVERY_ACK, etc.
    val senderId: String,               // Originating OFC-XXXXXXXX ID
    val senderDisplayName: String,      // Originating display name
    val senderPublicKey: String,        // Base64 encoded EC Public Key of sender
    val recipientId: String,            // Destination OFC-XXXXXXXX or Community ID or BROADCAST
    val timestamp: Long,                // Creation epoch timestamp
    val ttl: Int = DEFAULT_TTL,         // Remaining Time-To-Live hops (decremented by relay)
    val hopCount: Int = 0,              // Total hops traversed (incremented by relay)
    val payload: String,                // Encrypted message payload, route payload, or ACK ID
    val signature: String? = null,      // ECDSA signature over critical immutable fields
    val routeMetadata: String? = null   // Optional routing headers (e.g. original incoming endpoint or route hints)
) {
    companion object {
        const val BROADCAST_ID = "OFC-BROADCAST"
        const val DEFAULT_TTL = 8
    }
}
