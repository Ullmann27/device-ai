package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveScrollEvidenceTest {
    private val target = ActionNode("", "com.example.app:id/list", true, true, false, true, false, false,
        LiveTargetBounds(0f, .2f, 1f, 1f))
    private val before = ObservationStamp("task", 3, 10, "com.example.app", 42, "before", 100, 1, 8)
    private val request = NodeActionRequest("task", "scroll", before.packageName, 3, 10,
        NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
    private val dispatch = LiveScrollDispatch(before, request.stepId, request.fingerprint,
        LiveScrollDispatch.targetFingerprint(target), 110, 1000)
    private val evidence = LiveScrollEvidence(dispatch, 11, 120, 1001, LiveScrollMotion(pixelsY = 80))
    private val after = NodeActionFrame(before.copy(revision = 12, observedAtNanos = 130, digest = "after"),
        true, true, listOf(target), evidence)

    @Test fun realTargetMotionConfirmsOneScroll() {
        var calls = 0
        var released = 0
        val port = object : NodeActionPort {
            override fun checkActive() {}
            override fun current() = NodeActionFrame(before, true, true, listOf(target))
            override fun performOnce(request: NodeActionRequest, target: ActionNode): Boolean { calls++; return true }
            override fun awaitPostcondition() = after
            override fun finished() { released++ }
        }
        val action = VerifiedNodeAction(LiveActionJournal(request.taskId))
        assertTrue(action.execute(request, port).confirmed)
        assertEquals("JOURNAL_DUPLICATE_ON_SAME_OBSERVATION", action.execute(request, port).reason)
        assertEquals(1, calls)
        assertEquals(2, released)
    }

    @Test fun unrelatedBadgeChangeCannotConfirmScroll() {
        val badge = target.copy(label = "1 neue Nachricht", viewId = "com.example.app:id/badge", scrollable = false)
        val port = object : NodeActionPort {
            var calls = 0
            override fun checkActive() {}
            override fun current() = NodeActionFrame(before, true, true, listOf(target))
            override fun performOnce(request: NodeActionRequest, target: ActionNode): Boolean { calls++; return true }
            override fun awaitPostcondition() = after.copy(nodes = listOf(target, badge), scrollEvidence = null)
        }
        val action = VerifiedNodeAction(LiveActionJournal(request.taskId))
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET, action.execute(request, port).verdict)
        assertEquals("JOURNAL_BLOCKED_AFTER_UNCLEAR", action.execute(request, port).reason)
        assertEquals(1, port.calls)
    }

    @Test fun taskStepSessionWindowAndObservationMustRemainBound() {
        val sources = listOf(before.copy(taskId = "other"), before.copy(epoch = 4),
            before.copy(captureSession = 9), before.copy(captureSession = 0), before.copy(windowId = 43),
            before.copy(windowId = null), before.copy(revision = 9), before.copy(digest = "other"),
            before.copy(packageName = "com.other.app"))
        for (source in sources) assertFalse(source.toString(),
            evidence.copy(dispatch = dispatch.copy(source = source)).confirms(request, before, target, after))
        for (bound in listOf(dispatch.copy(stepId = "previous"), dispatch.copy(fingerprint = "other"),
            dispatch.copy(targetFingerprint = "other"))) {
            assertFalse(evidence.copy(dispatch = bound).confirms(request, before, target, after))
        }
        for (stamp in listOf(after.stamp.copy(taskId = "other"), after.stamp.copy(epoch = 4),
            after.stamp.copy(captureSession = 9), after.stamp.copy(windowId = 43),
            after.stamp.copy(packageName = "com.other.app"))) {
            assertFalse(evidence.confirms(request, before, target, after.copy(stamp = stamp)))
        }
    }

    @Test fun preDispatchEventsAndMissingMovementFailClosed() {
        val invalid = listOf(evidence.copy(eventRevision = before.revision),
            evidence.copy(eventRevision = after.stamp.revision + 1), evidence.copy(receivedAtNanos = 109),
            evidence.copy(receivedAtNanos = 131), evidence.copy(eventUptimeMillis = 999),
            evidence.copy(motion = LiveScrollMotion()),
            evidence.copy(dispatch = dispatch.copy(dispatchedAtNanos = 99)))
        invalid.forEach { assertFalse(it.toString(), it.confirms(request, before, target, after)) }
        assertFalse(evidence.confirms(request.copy(stepId = "next"), before, target, after))
        assertFalse(evidence.confirms(request.copy(kind = NodeActionKind.SCROLL_BACKWARD), before, target, after))
    }

    @Test fun postObservationMustContainOnlyTheSameSafeTarget() {
        for (nodes in listOf(emptyList(), listOf(target, target), listOf(target.copy(enabled = false)),
            listOf(target.copy(password = true)), listOf(target.copy(visible = false)),
            listOf(target.copy(scrollable = false)), listOf(target.copy(viewId = "com.example.app:id/other")))) {
            assertFalse(evidence.confirms(request, before, target, after.copy(nodes = nodes)))
        }
    }

    @Test fun undefinedAndZeroDeltasAreNotMovement() {
        assertNull(LiveScrollSample().movementSince(null))
        assertNull(LiveScrollSample(deltaX = -1, deltaY = -1).movementSince(null))
        assertNull(LiveScrollSample(deltaX = Int.MIN_VALUE, deltaY = 0).movementSince(null))
        assertEquals(80L, LiveScrollSample(deltaY = 80).movementSince(null)?.pixelsY)
        assertEquals(-80L, LiveScrollSample(deltaY = -80).movementSince(null)?.pixelsY)
    }

    @Test fun extremeDeltasAndOffsetsCannotInventMovement() {
        assertNull(LiveScrollSample(deltaX = Int.MAX_VALUE, deltaY = Int.MAX_VALUE).movementSince(null))
        assertNull(LiveScrollSample(offsetY = Int.MAX_VALUE).movementSince(LiveScrollSample(offsetY = 0)))
        assertNull(LiveScrollSample(offsetY = 80).movementSince(LiveScrollSample(offsetY = Int.MAX_VALUE)))
        assertNull(LiveScrollSample(offsetX = Int.MAX_VALUE).movementSince(LiveScrollSample(offsetX = 0)))
        assertNull(LiveScrollSample(offsetX = 80).movementSince(LiveScrollSample(offsetX = Int.MAX_VALUE)))
        assertEquals(80L, LiveScrollSample(deltaX = Int.MAX_VALUE, deltaY = 80).movementSince(null)?.pixelsY)
        assertEquals(-80L, LiveScrollSample(offsetY = 0).movementSince(LiveScrollSample(offsetY = 80))?.pixelsY)
    }

    @Test fun offsetOrIndexSequenceProvidesMovementWithoutDeltas() {
        assertNull(LiveScrollSample(offsetY = 80).movementSince(null))
        assertEquals(80L, LiveScrollSample(offsetY = 80).movementSince(LiveScrollSample(offsetY = 0))?.pixelsY)
        assertNull(LiveScrollSample(offsetY = 80).movementSince(LiveScrollSample(offsetY = 80)))
        assertNull(LiveScrollSample(offsetY = 80).movementSince(LiveScrollSample(offsetY = -1)))
        assertEquals(3L, LiveScrollSample(firstIndex = 4, itemCount = 20)
            .movementSince(LiveScrollSample(firstIndex = 1, itemCount = 20))?.firstIndexDelta)
        assertNull(LiveScrollSample(firstIndex = 4, itemCount = 21)
            .movementSince(LiveScrollSample(firstIndex = 1, itemCount = 20)))
        assertNull(LiveScrollSample(firstIndex = 20, itemCount = 20)
            .movementSince(LiveScrollSample(firstIndex = 1, itemCount = 20)))
    }
}
