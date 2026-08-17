package com.offlinemesh.app.core.model

data class Community(
    val communityId: String,
    val name: String,
    val description: String,
    val memberCount: Int = 1,
    val joinedAt: Long = System.currentTimeMillis(),
    val isMember: Boolean = true
)
