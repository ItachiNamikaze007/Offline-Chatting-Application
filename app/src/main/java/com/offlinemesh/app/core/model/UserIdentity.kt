package com.offlinemesh.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserIdentity(
    val userId: String,           // Format: OFC-7K4P9X2M
    val displayName: String,
    val publicKey: String,        // Base64 encoded X.509 EC Public Key
    val avatarColorHex: String,   // Color hex for user avatar e.g. #3B82F6
    val createdAt: Long = System.currentTimeMillis()
)
