package com.offlinemesh.app.domain.mesh

import java.util.Collections
import java.util.LinkedHashMap

/**
 * Thread-safe LRU Cache for packet deduplication.
 * Prevents redundant packet processing or infinite loops in mesh topologies.
 */
class MessageDeduplicator(
    private val maxCacheSize: Int = 2000
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
     * If not seen before, records the packetId and returns false.
     * If already seen, returns true.
     */
    fun isDuplicate(packetId: String): Boolean {
        synchronized(seenPacketIds) {
            if (seenPacketIds.containsKey(packetId)) {
                return true
            }
            seenPacketIds[packetId] = System.currentTimeMillis()
            return false
        }
    }

    /**
     * Checks whether a packet has been seen without marking it as seen.
     */
    fun hasSeen(packetId: String): Boolean {
        return seenPacketIds.containsKey(packetId)
    }

    fun clear() {
        seenPacketIds.clear()
    }

    fun size(): Int = seenPacketIds.size
}
