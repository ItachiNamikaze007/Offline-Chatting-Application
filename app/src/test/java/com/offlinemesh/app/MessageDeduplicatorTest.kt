package com.offlinemesh.app

import com.google.common.truth.Truth.assertThat
import com.offlinemesh.app.domain.mesh.MessageDeduplicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class MessageDeduplicatorTest {

    @Test
    fun isDuplicate_detectsDuplicatePacketIds() {
        val deduplicator = MessageDeduplicator(maxCacheSize = 100)
        val packetId = UUID.randomUUID().toString()

        // First appearance should NOT be duplicate
        val firstCheck = deduplicator.isDuplicate(packetId)
        assertThat(firstCheck).isFalse()

        // Second appearance MUST be recognized as duplicate
        val secondCheck = deduplicator.isDuplicate(packetId)
        assertThat(secondCheck).isTrue()

        // Third check also duplicate
        val thirdCheck = deduplicator.isDuplicate(packetId)
        assertThat(thirdCheck).isTrue()
    }

    @Test
    fun isDuplicate_acceptsDistinctPacketIds() {
        val deduplicator = MessageDeduplicator(maxCacheSize = 100)
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()

        assertThat(deduplicator.isDuplicate(id1)).isFalse()
        assertThat(deduplicator.isDuplicate(id2)).isFalse()
    }

    @Test
    fun deduplicator_evictsOldestEntriesWhenFull() {
        val deduplicator = MessageDeduplicator(maxCacheSize = 3)
        val id1 = "ID-1"
        val id2 = "ID-2"
        val id3 = "ID-3"
        val id4 = "ID-4"

        deduplicator.isDuplicate(id1)
        deduplicator.isDuplicate(id2)
        deduplicator.isDuplicate(id3)
        deduplicator.isDuplicate(id4) // Should evict ID-1

        assertThat(deduplicator.hasSeen(id4)).isTrue()
        assertThat(deduplicator.hasSeen(id3)).isTrue()
        assertThat(deduplicator.hasSeen(id2)).isTrue()
        assertThat(deduplicator.hasSeen(id1)).isFalse()
    }

    @Test
    fun deduplicator_concurrentAccess_isThreadSafeAndAccurate() = runBlocking {
        val deduplicator = MessageDeduplicator(maxCacheSize = 2000)
        val acceptedCount = AtomicInteger(0)
        val duplicateCount = AtomicInteger(0)

        val packetId = UUID.randomUUID().toString()

        // Launch 50 concurrent coroutines attempting to process the SAME packetId simultaneously
        val jobs = (1..50).map {
            async(Dispatchers.Default) {
                if (deduplicator.isDuplicate(packetId)) {
                    duplicateCount.incrementAndGet()
                } else {
                    acceptedCount.incrementAndGet()
                }
            }
        }
        jobs.awaitAll()

        // Exactly one thread must succeed in being first (not duplicate), and 49 must be marked duplicate
        assertThat(acceptedCount.get()).isEqualTo(1)
        assertThat(duplicateCount.get()).isEqualTo(49)
    }
}
