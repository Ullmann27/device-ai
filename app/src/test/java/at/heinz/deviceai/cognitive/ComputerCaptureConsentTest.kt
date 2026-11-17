package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ComputerCaptureConsentTest {
    @Test fun reservedRangeExcludesPdfAndExistingRequestCodes() {
        for (code in listOf(-1, 2245, 2460, 30_000, 60_000, 65_000, 65_535, Int.MAX_VALUE)) {
            assertFalse(ComputerCaptureConsent.ownsRequestCode(code))
        }
        assertTrue(ComputerCaptureConsent.ownsRequestCode(60_001))
        assertTrue(ComputerCaptureConsent.ownsRequestCode(64_999))
    }

    @Test fun onlyMatchingResponseConsumesCurrentTask() {
        val slot = ComputerCaptureConsent()
        val request = slot.begin("task-a")
        assertNull(slot.accept(2245))
        assertEquals("task-a", slot.accept(request.requestCode))
        assertNull(slot.accept(request.requestCode))
    }

    @Test fun oldResponseCannotAuthorizeSuccessorTask() {
        val slot = ComputerCaptureConsent()
        val first = slot.begin("task-a")
        val second = slot.begin("task-b")
        assertNotEquals(first.requestCode, second.requestCode)
        assertNull(slot.accept(first.requestCode))
        assertEquals("task-b", slot.accept(second.requestCode))
    }

    @Test fun cancelInvalidatesPendingAndAlreadyQueuedResponse() {
        val slot = ComputerCaptureConsent()
        val request = slot.begin("task-a")
        slot.cancel()
        assertNull(slot.accept(request.requestCode))
        slot.cancel()
        assertNull(slot.accept(request.requestCode))
    }

    @Test fun cancelledTaskIdReuseStillGetsNewCode() {
        val slot = ComputerCaptureConsent()
        val first = slot.begin("same-task")
        slot.cancel()
        val second = slot.begin("same-task")
        assertNotEquals(first.requestCode, second.requestCode)
        assertNull(slot.accept(first.requestCode))
        assertEquals("same-task", slot.accept(second.requestCode))
    }

    @Test fun newControllerCannotAcceptOldControllerResponse() {
        val old = ComputerCaptureConsent()
        val first = old.begin("task-a")
        val replacement = ComputerCaptureConsent()
        val second = replacement.begin("task-b")
        assertNotEquals(first.requestCode, second.requestCode)
        assertNull(replacement.accept(first.requestCode))
        assertEquals("task-b", replacement.accept(second.requestCode))
        assertEquals("task-a", old.accept(first.requestCode))
    }

    @Test fun matchedResponseReturnsOnlyItsOriginalTaskIdentity() {
        val slot = ComputerCaptureConsent()
        val request = slot.begin("original-task")
        val modifiedCopy = request.copy(taskId = "unrelated-task")
        assertEquals("original-task", slot.accept(modifiedCopy.requestCode))
    }

    @Test fun processWideAllocationIsUniqueAcrossConcurrentControllers() {
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val jobs = (0 until 24).map { index -> pool.submit<Int> {
                start.await()
                val slot = ComputerCaptureConsent()
                val request = slot.begin("task-$index")
                assertEquals("task-$index", slot.accept(request.requestCode))
                request.requestCode
            } }
            start.countDown()
            val codes = jobs.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(24, codes.distinct().size)
            assertTrue(codes.all(ComputerCaptureConsent::ownsRequestCode))
        } finally { pool.shutdownNow() }
    }

    @Test fun concurrentDuplicateResponsesAreConsumedOnce() {
        val slot = ComputerCaptureConsent()
        val request = slot.begin("task-a")
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val jobs = (0 until 2).map { pool.submit<String?> {
                start.await()
                slot.accept(request.requestCode)
            } }
            start.countDown()
            assertEquals(listOf("task-a"), jobs.mapNotNull { it.get(5, TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
    }

    @Test fun exhaustedRangeNeverWrapsOrLeavesPreviousRequestActive() {
        val counter = AtomicInteger(ComputerCaptureConsent.LAST_REQUEST)
        val slot = ComputerCaptureConsent(counter)
        val last = slot.begin("last-task")
        assertEquals(64_999, last.requestCode)
        repeat(3) {
            assertThrows(IllegalStateException::class.java) { slot.begin("unavailable-task") }
            assertEquals(65_000, counter.get())
        }
        assertNull(slot.accept(last.requestCode))
    }

    @Test fun invalidAllocatorNeverOverflowsIntoReusedCodes() {
        for (initial in listOf(-1, 0, 60_000, 65_000, Int.MAX_VALUE)) {
            val counter = AtomicInteger(initial)
            val slot = ComputerCaptureConsent(counter)
            assertThrows(IllegalStateException::class.java) { slot.begin("task") }
            assertEquals(initial, counter.get())
        }
    }

    @Test fun invalidTaskCannotConsumeRequestCodes() {
        val counter = AtomicInteger(ComputerCaptureConsent.FIRST_REQUEST)
        val slot = ComputerCaptureConsent(counter)
        for (task in listOf("", " ", "task\nother", "x".repeat(129))) {
            assertThrows(IllegalArgumentException::class.java) { slot.begin(task) }
        }
        assertEquals(ComputerCaptureConsent.FIRST_REQUEST, counter.get())
    }
}
