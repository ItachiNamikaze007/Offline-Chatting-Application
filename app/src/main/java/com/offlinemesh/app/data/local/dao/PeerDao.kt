package com.offlinemesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offlinemesh.app.data.local.entity.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePeer(peer: PeerEntity)

    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE userId = :userId LIMIT 1")
    suspend fun getPeerById(userId: String): PeerEntity?

    @Query("SELECT * FROM peers WHERE userId = :userId LIMIT 1")
    fun observePeerById(userId: String): Flow<PeerEntity?>

    @Query("UPDATE peers SET lastSeen = :timestamp WHERE userId = :userId")
    suspend fun updateLastSeen(userId: String, timestamp: Long)

    @Query("DELETE FROM peers WHERE userId = :userId")
    suspend fun deletePeer(userId: String)
}
