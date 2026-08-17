package com.offlinemesh.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.offlinemesh.app.data.local.dao.CommunityDao
import com.offlinemesh.app.data.local.dao.MessageDao
import com.offlinemesh.app.data.local.dao.PeerDao
import com.offlinemesh.app.data.local.entity.CommunityEntity
import com.offlinemesh.app.data.local.entity.MessageEntity
import com.offlinemesh.app.data.local.entity.PeerEntity

@Database(
    entities = [
        MessageEntity::class,
        PeerEntity::class,
        CommunityEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OfflineMeshDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun communityDao(): CommunityDao

    companion object {
        private const val DATABASE_NAME = "offlinemesh_database.db"

        @Volatile
        private var INSTANCE: OfflineMeshDatabase? = null

        fun getInstance(context: Context): OfflineMeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineMeshDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
