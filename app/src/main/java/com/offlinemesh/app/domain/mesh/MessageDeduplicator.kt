package com.offlinemesh.app.domain.mesh

import java.util.Collections
import java.util.LinkedHashMap

/**
 * Thread-safe LRU Cache for packet deduplication.
 * Prevents redundant packet processing and infinite loops in mesh topologies.
 */
class MessageDeduplicator(
    private val maxCacheSize: Int = 3000,
    private val entryExpiryMillis: Long = 10 * 60 * 1000L // 10 minutes retention
) {
    private val seenPacketIds = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(maxCacheSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > maxCacheSize
            }
        }
    )

    /**
     * Checks if a packet is a duplicate.
     * If not seen before (or expired), records the packetId and returns false.
     * If already seen within the expiration window, returns true.
     */
    fun isDuplicate(packetId: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(seenPacketIds) {
            val lastSeen = seenPacketIds[packetId]
            if (lastSeen != null && (now - lastSeen) < entryExpiryMillis) {
                return true
            }
            seenPacketIds[packetId] = now
            return false
        }
    }

    /**
     * Checks whether a packet has been seen without updating its last seen time.
     */
    fun hasSeen(packetId: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(seenPacketIds) {
            val lastSeen = seenPacketIds[packetId] ?: return false
            return (now - lastSeen) < entryExpiryMillis
        }
    }

    /**
     * Clears all cached entries.
     */
    fun clear() {
        seenPacketIds.clear()
    }

    /**
     * Returns the current number of cached entries.
     */
    fun size(): Int = seenPacketIds.size
}
