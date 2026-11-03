package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * M5 milestone 1 – ToolGate <-> durable receipt journal.
 *
 * The production journal is capped at 512 receipts and nothing in the app ever
 * removes one. Every gate call (even a rejected one) records a receipt, so after
 * 512 tool calls in the life of an installation every further device tool fails
 * closed with "Durable journal unavailable" – a permanent ToolGate outage.
 *
 * Retention contract: only receipts with a certain terminal outcome may be
 * dropped (oldest first). Pending or UNKNOWN-after-dispatch receipts are never
 * evicted, and a journal full of them still blocks execution.
 */
class ToolReceiptJournalRetentionTest {
    private val capacity = 512

    private fun <T> withDirectory(block: (File) -> T): T {
        val directory = Files.createTempDirectory("device-ai-receipt-retention").toFile()
        try { return block(directory) } finally { directory.deleteRecursively() }
    }

    private fun proposed(id: String, at: Long) = ToolExecutionReceipt(
        callId = id, toolName = "get_device_info", argumentFingerprint = toolArgumentFingerprint(emptyMap()),
        sanitizedArguments = emptyMap(), risk = Risk.READ_ONLY, createdAtMillis = at,
        transitions = listOf(ToolReceiptTransition(ToolReceiptState.PROPOSED, at))
    )

    private fun FileToolExecutionReceiptStore.makeUnknownAfterDispatch(id: String, at: Long) {
        propose(proposed(id, at))
        assertNotNull(transition(id, ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED, at))
        assertNotNull(transition(id, ToolReceiptState.APPROVED, ToolReceiptState.START_PENDING, at))
        assertNotNull(transition(id, ToolReceiptState.START_PENDING, ToolReceiptState.UNKNOWN_AFTER_DISPATCH, at))
    }

    @Test
    fun toolGateKeepsExecutingAfterJournalReachesCapacity() = withDirectory { directory ->
        val store = FileToolExecutionReceiptStore(directory)
        val runs = AtomicInteger()
        var clock = 1_000L
        val started = System.nanoTime()
        val statuses = (1..capacity + 3).map { index ->
            // Like AgentActivity: one gate per owner turn, fresh call id per invocation.
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { runs.incrementAndGet(); "ok" },
                receiptStore = store, receiptClock = { clock++ })
            gate.execute(ToolProposal("get_device_info", emptyMap()).invocation("turn-$index")).record
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("M5-M1 journal: ${statuses.size} gate calls in ${elapsedMs} ms")
        val firstFailure = statuses.indexOfFirst { it.status != ToolExecutionStatus.SUCCEEDED }
        assertEquals("first non-success at call #${firstFailure + 1}: " +
            statuses.getOrNull(firstFailure)?.decisionReason, -1, firstFailure)
        assertEquals(capacity + 3, runs.get())
        val kept = store.all()
        assertEquals(capacity, kept.size)
        // Oldest certain receipts were evicted; the newest are retained for replay suppression.
        assertNull(store.get("turn-1"))
        assertNull(store.get("turn-3"))
        assertEquals(ToolReceiptState.SUCCEEDED, store.get("turn-4")?.state)
        assertEquals(ToolReceiptState.SUCCEEDED, store.get("turn-${capacity + 3}")?.state)
    }

    @Test
    fun uncertainAndPendingReceiptsAreNeverEvicted() = withDirectory { directory ->
        val store = FileToolExecutionReceiptStore(directory)
        // One uncertain receipt and one never-dispatched pending receipt are the OLDEST entries.
        store.makeUnknownAfterDispatch("unknown-oldest", 1)
        store.propose(proposed("pending-oldest", 2))
        // Fill the rest with certain terminal receipts (REJECTED without dispatch).
        for (index in 3..capacity) {
            store.propose(proposed("certain-$index", index.toLong()))
            assertNotNull(store.transition("certain-$index", ToolReceiptState.PROPOSED,
                ToolReceiptState.REJECTED, index.toLong()))
        }
        assertEquals(capacity, store.all().size)
        store.propose(proposed("new-1", 10_000))
        store.propose(proposed("new-2", 10_001))
        assertEquals(capacity, store.all().size)
        assertEquals(ToolReceiptState.UNKNOWN_AFTER_DISPATCH, store.get("unknown-oldest")?.state)
        assertTrue(store.get("unknown-oldest")!!.outcomeUncertain)
        assertEquals(ToolReceiptState.PROPOSED, store.get("pending-oldest")?.state)
        assertNull(store.get("certain-3"))
        assertNull(store.get("certain-4"))
        assertNotNull(store.get("certain-5"))
        assertNotNull(store.get("new-2"))
        // A restart sees the same retained journal.
        assertEquals(ToolReceiptState.UNKNOWN_AFTER_DISPATCH,
            FileToolExecutionReceiptStore(directory).get("unknown-oldest")?.state)
    }

    @Test
    fun journalFullOfUncertainReceiptsStillFailsClosed() = withDirectory { directory ->
        val store = FileToolExecutionReceiptStore(directory)
        for (index in 1..capacity) store.makeUnknownAfterDispatch("unknown-$index", index.toLong())
        try {
            store.propose(proposed("one-too-many", 99_999))
            fail("A journal containing only UNKNOWN outcomes must not drop any of them.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("full"))
        }
        val runs = AtomicInteger()
        val result = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { runs.incrementAndGet(); "x" },
            receiptStore = store).execute(ToolProposal("get_device_info", emptyMap()).invocation("blocked-call"))
        assertEquals(ToolExecutionStatus.REJECTED, result.record.status)
        assertTrue(result.record.outcomeUncertain)
        assertEquals(0, runs.get())
        assertFalse(store.all().any { it.callId == "blocked-call" })
        assertEquals(capacity, store.all().count { it.outcomeUncertain })
    }
}
