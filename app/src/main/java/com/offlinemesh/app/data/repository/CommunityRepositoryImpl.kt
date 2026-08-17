package com.offlinemesh.app.data.repository

import com.offlinemesh.app.core.model.Community
import com.offlinemesh.app.data.local.dao.CommunityDao
import com.offlinemesh.app.data.local.entity.CommunityEntity
import com.offlinemesh.app.domain.repository.CommunityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class CommunityRepositoryImpl(
    private val communityDao: CommunityDao
) : CommunityRepository {

    override fun getAllCommunities(): Flow<List<Community>> {
        return communityDao.getAllCommunities().map { list ->
            list.map { entity ->
                Community(
                    communityId = entity.communityId,
                    name = entity.name,
                    description = entity.description,
                    memberCount = entity.memberCount,
                    joinedAt = entity.joinedAt,
                    isMember = entity.isMember
                )
            }
        }
    }

    override suspend fun joinCommunity(communityId: String) {
        communityDao.setMembership(communityId, isMember = true)
    }

    override suspend fun leaveCommunity(communityId: String) {
        communityDao.setMembership(communityId, isMember = false)
    }

    override suspend fun createCommunity(name: String, description: String): Community {
        val id = "COMM-${UUID.randomUUID().toString().take(8).uppercase()}"
        val now = System.currentTimeMillis()
        val entity = CommunityEntity(
            communityId = id,
            name = name,
            description = description,
            memberCount = 1,
            joinedAt = now,
            isMember = true
        )
        communityDao.insertOrUpdateCommunity(entity)
        return Community(
            communityId = id,
            name = name,
            description = description,
            memberCount = 1,
            joinedAt = now,
            isMember = true
        )
    }
}
