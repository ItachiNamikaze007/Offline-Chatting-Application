package com.offlinemesh.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserIdentity(
    val userId: String,                   // Format: OFC-7K4P9X2M
    val displayName: String,
    val publicKey: String,                // Base64 encoded X.509 EC Public Key
    val avatarColorHex: String,           // Color hex for user avatar e.g. #3B82F6
    val avatarImagePath: String? = null,  // Local file path to compressed avatar image
    val avatarVersion: Long = 0L,         // Avatar version timestamp / hash for sync
    val createdAt: Long = System.currentTimeMillis()
)
