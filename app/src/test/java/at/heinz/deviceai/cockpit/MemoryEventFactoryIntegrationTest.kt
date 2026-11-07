package at.heinz.deviceai.cockpit

import at.heinz.deviceai.learning.ContinuumMemoryEventHub
import at.heinz.deviceai.learning.DocumentLearned
import at.heinz.deviceai.learning.MemoryEntryStatus
import at.heinz.deviceai.learning.MemoryStored
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the Activity-facing factory with the real application event hub. */
class MemoryEventFactoryIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun appFactoryRejectsRawTextMetadataButDeliversValidStorageEvents() {
        val directory = temporary.newFolder()
        val hub = ContinuumMemoryEventHub.forApp(directory)
        val sink = Recorder()
        val subscription = MemoryEventSources.forApp(directory).subscribe(sink)
        try {
            hub.emit(DocumentLearned("synthetic private document text", "PDF", 1, 10L))
            hub.emit(MemoryStored("synthetic private entry text", MemoryEntryStatus.CONFIRMED, 11L))
            hub.emit(MemoryStored("safe-entry", MemoryEntryStatus.CONFIRMED, 12L))
            hub.awaitIdle(5_000)

            assertEquals(listOf("stored:safe-entry:CONFIRMED"), sink.seen.toList())
        } finally { subscription.close() }
    }

    @Test fun closingAppFactorySubscriptionSuppressesAnAlreadyCapturedHubListener() {
        val directory = temporary.newFolder()
        val hub = ContinuumMemoryEventHub.forApp(directory)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocker = hub.addListener { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val sink = Recorder()
        val subscription = MemoryEventSources.forApp(directory).subscribe(sink)
        try {
            hub.emit(MemoryStored("safe-entry", MemoryEntryStatus.CONFIRMED, 10L))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            subscription.close()
            release.countDown()
            hub.awaitIdle(5_000)

            assertTrue("A captured callback must not revive a closed screen subscription", sink.seen.isEmpty())
        } finally { release.countDown(); subscription.close(); blocker.close() }
    }

    private class Recorder : MemoryEventSink {
        val seen = CopyOnWriteArrayList<String>()
        override fun documentLearned(docId: String, kind: String, entriesAdded: Int, atEpochMs: Long) {
            seen += "document:$docId:$kind"
        }
        override fun memoryStored(entryId: String, status: String, atEpochMs: Long) {
            seen += "stored:$entryId:$status"
        }
        override fun recallHits(queryHash: String, count: Int, topScore: Float?, atEpochMs: Long) {
            seen += "recall:$queryHash:$count"
        }
    }
}
