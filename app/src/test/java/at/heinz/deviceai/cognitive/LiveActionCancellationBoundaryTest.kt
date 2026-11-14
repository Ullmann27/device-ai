package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveActionCancellationBoundaryTest {
    private val source = ObservationStamp("task", 3, 10, "com.example.app", 42, "before", 100,
        captureSession = 7)
    private val field = ActionNode("Search", "com.example.app:id/search", true, true, false, false,
        true, false, LiveTargetBounds(.1f, .1f, .9f, .2f))
    private val request = NodeActionRequest("task", "type", source.packageName, source.epoch,
        source.revision, NodeActionKind.SET_TEXT, NodeSelector(viewId = field.viewId), "Orion")

    private inner class Port(
        val before: ObservationStamp = source,
        private val cancelAt: Int = Int.MAX_VALUE,
        private val dispatchThrows: Boolean = false
    ) : NodeActionPort {
        var checks = 0
        var dispatched = 0
        var finished = 0
        override fun checkActive() { check(++checks != cancelAt) { "CANCELLED" } }
        override fun current() = NodeActionFrame(before, true, true, listOf(field))
        override fun performOnce(request: NodeActionRequest, target: ActionNode): Boolean {
            dispatched++
            check(!dispatchThrows) { "BINDER_DIED" }
            return true
        }
        override fun awaitPostcondition() = NodeActionFrame(
            before.copy(revision = before.revision + 1, observedAtNanos = before.observedAtNanos + 1,
                digest = "after"), true, true, listOf(field.copy(textDigest = liveSha256("Orion"))))
        override fun finished() { finished++ }
    }

    @Test fun cancellationAfterAdmissionAllowsOnlyFreshRetry() {
        val events = mutableListOf<LiveActionEvent>()
        val action = VerifiedNodeAction(LiveActionJournal("task")) { events += it }
        val stopped = Port(cancelAt = 2)

        assertThrows(IllegalStateException::class.java) { action.execute(request, stopped) }

        assertEquals(0, stopped.dispatched)
        assertEquals(1, stopped.finished)
        assertTrue(events.isEmpty())
        val unchanged = Port()
        assertEquals("JOURNAL_DUPLICATE_ON_SAME_OBSERVATION", action.execute(request, unchanged).reason)
        assertEquals(0, unchanged.dispatched)
        val fresh = Port(source.copy(revision = 12, observedAtNanos = 120, digest = "fresh"))
        assertTrue(action.execute(request.copy(revision = 12), fresh).confirmed)
        assertEquals(1, fresh.dispatched)
        assertEquals(1, fresh.finished)
    }

    @Test fun dispatchFailureStillForbidsFreshRetry() {
        val action = VerifiedNodeAction(LiveActionJournal("task"))
        val failed = Port(dispatchThrows = true)
        assertEquals(EffectVerdict.UNCLEAR_DISPATCH, action.execute(request, failed).verdict)
        assertEquals(1, failed.dispatched)
        assertEquals(1, failed.finished)

        val fresh = Port(source.copy(revision = 12, observedAtNanos = 120, digest = "fresh"))
        assertEquals("JOURNAL_BLOCKED_AFTER_UNCLEAR",
            action.execute(request.copy(revision = 12), fresh).reason)
        assertEquals(0, fresh.dispatched)
    }

    @Test fun cancellationAfterDispatchStillForbidsFreshRetry() {
        val action = VerifiedNodeAction(LiveActionJournal("task"))
        val stopped = Port(cancelAt = 3)
        assertEquals(EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION, action.execute(request, stopped).verdict)
        assertEquals(1, stopped.dispatched)
        assertEquals(1, stopped.finished)

        val fresh = Port(source.copy(revision = 12, observedAtNanos = 120, digest = "fresh"))
        assertEquals("JOURNAL_BLOCKED_AFTER_UNCLEAR",
            action.execute(request.copy(revision = 12), fresh).reason)
        assertEquals(0, fresh.dispatched)
    }
}
