package com.offlinemesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey
    val userId: String,                  // Permanent OFC-XXXXXXXX
    val displayName: String,
    val publicKey: String,               // Base64 EC Public Key
    val avatarColorHex: String = "#3B82F6",
    val avatarImagePath: String? = null, // Local cached file path for peer profile photo
    val avatarVersion: Long = 0L,        // Version timestamp of cached photo
    val lastSeen: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false
)
