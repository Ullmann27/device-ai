package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveSupportRegressionTest {
    private val pkg = "com.whatsapp"
    private val own = "at.heinz.deviceai.continuumlocal"

    private fun stamp(revision: Long, digest: String, window: Int = 42) =
        ObservationStamp("review-task", 3, revision, pkg, window, digest, revision * 1_000_000)

    private fun node(label: String, scrollable: Boolean = false) = ActionNode(
        label, if (scrollable) "$pkg:id/list" else "$pkg:id/badge", true, true,
        false, scrollable, false, false, LiveTargetBounds(0f, .1f, 1f, .8f)
    )

    @Test fun unrelatedBadgeChangeDoesNotConfirmScroll() {
        val container = node("Chats", scrollable = true)
        val before = NodeActionFrame(stamp(10, "unchanged-list-badge-1"), true, true,
            listOf(container, node("1")))
        val after = NodeActionFrame(stamp(11, "unchanged-list-badge-2"), true, true,
            listOf(container, node("2")))
        val port = object : NodeActionPort {
            override fun checkActive() = Unit
            override fun current() = before
            override fun performOnce(request: NodeActionRequest, target: ActionNode) = true
            override fun awaitPostcondition() = after
        }
        val request = NodeActionRequest("review-task", "scroll-1", pkg, 3, 10,
            NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
        val result = VerifiedNodeAction(LiveActionJournal("review-task")).execute(request, port)
        assertFalse("Unrelated badge changed; no scroll evidence", result.confirmed)
    }

    @Test fun anotherWindowDoesNotConfirmSameTaskAction() {
        val verdict = ActionEffectVerifier.verify(stamp(10, "before", 42), true,
            stamp(11, "after", 43), true)
        assertEquals(EffectVerdict.UNCLEAR_CONTEXT_CHANGED, verdict)
    }

    @Test fun uncertainActionNeverBecomesRetryableThroughEviction() {
        val journal = LiveActionJournal("review-task", capacity = 2)
        for (i in 1..3) {
            val before = stamp(i.toLong(), "screen-$i")
            assertEquals(LiveActionJournal.Admission.ALLOWED, journal.admit("action-$i", before))
            journal.finish("action-$i", before, EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION, null)
        }
        val admission = journal.admit("action-1", stamp(10, "latest-screen"))
        assertNotEquals("Uncertain action forgotten by eviction", LiveActionJournal.Admission.ALLOWED, admission)
    }

    private fun binding(session: Long = 1) = LiveFrameBinding("review-task", session, pkg, own,
        LiveFrameOrigin.ACCESSIBILITY_WINDOW, minFrameIntervalMillis = 100)

    private fun meta(time: Long, session: Long = 1, secure: Boolean = false) =
        LiveFrameMeta(session, time, 100, 100, pkg, 42, false, secure)

    @Test fun secureEvidenceStillCoversRateLimitedFrame() {
        var now = 1_000_000_000L
        val released = mutableListOf<Int>()
        val holder = LatestLiveFrameHolder<Int>({ released += it }, { now })
        holder.bind(binding())
        assertTrue(holder.offer(meta(100), 1) is LiveFrameOffer.Accepted)
        now += 10_000_000
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.SECURE_CONTENT),
            holder.offer(meta(101, secure = true), 2))
        assertEquals(LiveMonitorState.COVERED, holder.status(now).state)
        assertNull(holder.acquire())
        assertEquals(listOf(1, 2), released)
    }

    @Test fun pauseDoesNotForgetSessionTimestamp() {
        var now = 1_000_000_000L
        val holder = LatestLiveFrameHolder<Int>({}, { now })
        holder.bind(binding())
        assertTrue(holder.offer(meta(200), 1) is LiveFrameOffer.Accepted)
        holder.pause(LiveMonitorReason.SOURCE_HIDDEN)
        now += 200_000_000
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.OUT_OF_ORDER), holder.offer(meta(100), 2))
    }

    @Test fun stopRetainsLeasedFrameUntilExactOnceRelease() {
        var now = 1_000_000_000L
        val released = mutableListOf<Int>()
        val holder = LatestLiveFrameHolder<Int>({ released += it }, { now })
        holder.bind(binding())
        holder.offer(meta(100), 1)
        val lease = holder.acquire()!!
        now += 200_000_000
        holder.offer(meta(200), 2)
        holder.end()
        assertEquals(listOf(2), released)
        assertEquals(LiveMonitorState.ENDED, holder.status(now).state)
        assertNull(holder.acquire())
        lease.close()
        lease.close()
        assertEquals(listOf(2, 1), released)
        assertEquals(0, holder.retainedFrames())
    }
}
