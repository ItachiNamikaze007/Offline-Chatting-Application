package com.offlinemesh.app.core.model

enum class PeerConnectionState {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

data class PeerDevice(
    val endpointId: String,               // Ephemeral Nearby Connections radio endpoint
    val userId: String? = null,           // Permanent OFC-XXXXXXXX (populated upon handshake)
    val displayName: String,
    val publicKey: String? = null,        // Base64 public key
    val avatarColorHex: String? = null,
    val connectionState: PeerConnectionState = PeerConnectionState.DISCOVERED,
    val lastSeen: Long = System.currentTimeMillis()
)
