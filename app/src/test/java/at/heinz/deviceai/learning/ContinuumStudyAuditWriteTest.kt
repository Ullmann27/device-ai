package at.heinz.deviceai.learning

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Issue #76: FAILED_SAVE_CYCLE_IllegalStateException in the public-study job. */
class ContinuumStudyAuditWriteTest {
    @After fun clearInterruptFlag() { Thread.interrupted() }

    /**
     * Same structure as AOSP SharedPreferencesImpl.EditorImpl.commit(): changes go to memory, a synchronous commit
     * writes on the calling thread and counts the latch down, then awaits it; InterruptedException -> false.
     */
    private class AndroidLikePrefs {
        val memory = mutableMapOf<String, Long>()
        val disk = mutableMapOf<String, Long>()
        var writes = 0
        var failingWrites = 0                       // writes that fail on disk (reported false, nothing written)
        var duringWrite: (() -> Unit)? = null       // one-shot: e.g. onStopJob arriving while the write completes
        inner class Editor {
            private val modified = mutableMapOf<String, Long>()
            fun putLong(key: String, value: Long) = apply { modified[key] = value }
            fun commit(): Boolean {
                memory.putAll(modified); modified.clear()       // commitToMemory clears the editor
                val written = CountDownLatch(1)
                if (failingWrites > 0) { failingWrites--; return false }
                if (disk != memory) { disk.clear(); disk.putAll(memory); writes++ }
                duringWrite?.also { duringWrite = null }?.invoke()
                written.countDown()                             // the write is complete
                return try { written.await(); true } catch (_: InterruptedException) { false }
            }
        }
    }

    @Test fun anInterruptFlagMakesAnAlreadyFinishedAndroidCommitReportFalse() {
        val prefs = AndroidLikePrefs()
        val edit = prefs.Editor().putLong("attempts", 1)
        Thread.currentThread().interrupt()
        assertFalse("the mechanism behind #76", edit.commit())
        assertEquals(1L, prefs.disk["attempts"])               // ... although the data is on disk
    }

    @Test fun anInterruptThatIsNoStopRequestKeepsTheAuditAndCountsNothingTwice() {
        val prefs = AndroidLikePrefs()
        val edit = prefs.Editor().putLong("attempts", 7)
        Thread.currentThread().interrupt()
        val reason = ContinuumStudyAuditWrite.commit({ edit.commit() }, { false }, { Long.MAX_VALUE }, { true })
        assertNull(reason)
        assertEquals(7L, prefs.disk["attempts"])
        assertEquals("one write; the retry only confirms it", 1, prefs.writes)
        assertTrue("the caller still sees its interrupt", Thread.currentThread().isInterrupted)
    }

    @Test fun aRealStopIsReportedAsStoppedAndNotRetried() {
        var calls = 0
        Thread.currentThread().interrupt()
        assertEquals("STOPPED", ContinuumStudyAuditWrite.commit({ calls++; false }, { true }, { Long.MAX_VALUE }, { true }))
        assertEquals(1, calls)
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @Test fun aStopArrivingDuringTheWriteKeepsTheWrittenAuditOnce() {
        val prefs = AndroidLikePrefs()
        val stop = AtomicBoolean(false)
        prefs.duringWrite = { stop.set(true); Thread.currentThread().interrupt() }   // onStopJob mid-write
        val edit = prefs.Editor().putLong("attempts", 3)
        assertNull("written once, confirmed by the retry", ContinuumStudyAuditWrite.commit({ edit.commit() }, { stop.get() },
            { Long.MAX_VALUE }, { true }))
        assertEquals(3L, prefs.disk["attempts"])
        assertEquals(1, prefs.writes)
    }

    @Test fun aStopDuringTheRetryOfAFailedWriteIsAnInterruptionNotADiskFault() {
        val prefs = AndroidLikePrefs()
        val stop = AtomicBoolean(false)
        prefs.failingWrites = 1
        prefs.duringWrite = { stop.set(true); Thread.currentThread().interrupt() }   // arrives during the retry
        val edit = prefs.Editor().putLong("attempts", 4)
        assertEquals("STOPPED", ContinuumStudyAuditWrite.commit({ edit.commit() }, { stop.get() }, { Long.MAX_VALUE }, { true }))
        assertEquals("the retry did write", 4L, prefs.disk["attempts"])
        assertEquals(1, prefs.writes)
    }

    @Test fun aWriteThatKeepsFailingGetsAFixedCause() {
        assertNull(ContinuumStudyAuditWrite.commit({ true }, { false }, { 0L }, { false }))
        var transient = 0
        assertNull("one transient failure is retried once",
            ContinuumStudyAuditWrite.commit({ ++transient > 1 }, { false }, { Long.MAX_VALUE }, { true }))
        assertEquals("STORAGE_FULL", ContinuumStudyAuditWrite.commit({ false }, { false }, { 1024L }, { true }))
        assertEquals("NOT_WRITABLE", ContinuumStudyAuditWrite.commit({ false }, { false }, { Long.MAX_VALUE }, { false }))
        assertEquals("COMMIT_FALSE", ContinuumStudyAuditWrite.commit({ false }, { false }, { Long.MAX_VALUE }, { true }))
        assertEquals("a failing probe is no evidence of a full disk", "COMMIT_FALSE",
            ContinuumStudyAuditWrite.commit({ false }, { false }, { error("statfs") }, { error("access") }))
        assertEquals("FAILED", ContinuumPublicStudyCycleResult.outcome("FAILED_SAVE_CYCLE_STORAGE_FULL"))
        assertFalse(ContinuumPublicStudyCycleResult.markTopicCompleted("FAILED_SAVE_CYCLE_COMMIT_FALSE"))
    }
}
