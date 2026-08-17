package com.offlinemesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "communities")
data class CommunityEntity(
    @PrimaryKey
    val communityId: String,             // Format: COMM-XXXXXXXX
    val name: String,
    val description: String,
    val memberCount: Int = 1,
    val joinedAt: Long = System.currentTimeMillis(),
    val isMember: Boolean = true
)
