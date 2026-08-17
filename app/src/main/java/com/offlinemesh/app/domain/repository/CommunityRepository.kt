package com.offlinemesh.app.domain.repository

import com.offlinemesh.app.core.model.Community
import kotlinx.coroutines.flow.Flow

interface CommunityRepository {
    fun getAllCommunities(): Flow<List<Community>>
    suspend fun joinCommunity(communityId: String)
    suspend fun leaveCommunity(communityId: String)
    suspend fun createCommunity(name: String, description: String): Community
}
