package com.offlinemesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offlinemesh.app.data.local.entity.CommunityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommunityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCommunity(community: CommunityEntity)

    @Query("SELECT * FROM communities ORDER BY joinedAt DESC")
    fun getAllCommunities(): Flow<List<CommunityEntity>>

    @Query("SELECT * FROM communities WHERE communityId = :communityId LIMIT 1")
    suspend fun getCommunityById(communityId: String): CommunityEntity?

    @Query("UPDATE communities SET isMember = :isMember WHERE communityId = :communityId")
    suspend fun setMembership(communityId: String, isMember: Boolean)

    @Query("DELETE FROM communities WHERE communityId = :communityId")
    suspend fun deleteCommunity(communityId: String)
}
