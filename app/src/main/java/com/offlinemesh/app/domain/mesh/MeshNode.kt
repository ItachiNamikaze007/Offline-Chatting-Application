package com.offlinemesh.app.domain.mesh

import com.offlinemesh.app.core.model.PeerDevice

data class MeshNode(
    val userId: String,                  // Permanent OFC-XXXXXXXX
    val displayName: String,
    val publicKey: String,
    val directEndpointId: String?,       // Non-null if currently directly connected via radio
    val isDirectPeer: Boolean,
    val hopCount: Int = if (isDirectPeer) 1 else Int.MAX_VALUE,
    val lastSeen: Long = System.currentTimeMillis()
)
