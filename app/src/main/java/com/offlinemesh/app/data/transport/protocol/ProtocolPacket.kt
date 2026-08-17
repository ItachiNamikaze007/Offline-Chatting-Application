package com.offlinemesh.app.data.transport.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class PacketType {
    HANDSHAKE,
    HANDSHAKE_ACK,
    DATA_DIRECT,
    DATA_COMMUNITY,
    DELIVERY_ACK,
    PING
}

@Serializable
data class ProtocolPacket(
    val protocolVersion: Int = 1,
    val packetId: String,               // Unique packet UUID
    val packetType: PacketType,         // HANDSHAKE, DATA_DIRECT, DELIVERY_ACK, etc.
    val senderId: String,               // Permanent OFC-XXXXXXXX ID
    val senderDisplayName: String,
    val senderPublicKey: String,        // Base64 encoded EC Public Key
    val recipientId: String,            // Destination OFC-XXXXXXXX or Community ID or BROADCAST
    val timestamp: Long,                // Creation epoch timestamp
    val ttl: Int = 3,                   // Remaining Time-To-Live hops (default: 3)
    val hopCount: Int = 0,              // Hops traversed so far (starts at 0)
    val payload: String,                // Text content or serialized inner data
    val signature: String? = null       // ECDSA SHA256withECDSA signature of critical fields
) {
    companion object {
        const val BROADCAST_ID = "OFC-BROADCAST"
        const val DEFAULT_TTL = 3
    }
}
