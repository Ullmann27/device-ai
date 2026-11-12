package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveComputerFramesTest {
    private class Frame(val id: Int) { var released = 0 }
    private var now = 1_000_000_000L
    private val released = mutableListOf<Int>()
    private val holder = LatestLiveFrameHolder<Frame>({ it.released++; released += it.id }, { now })
    private val own = "at.heinz.deviceai.continuumlocal"
    private val whatsapp = "com.whatsapp"
    private fun binding(target: String = whatsapp, session: Long = 1, task: String = "task-1",
                        origin: LiveFrameOrigin = LiveFrameOrigin.PROJECTION_SINGLE_APP) =
        LiveFrameBinding(task, session, target, own, origin, staleAfterMillis = 1_500, minFrameIntervalMillis = 100)
    private var frameTime = 10L
    private fun meta(source: String? = whatsapp, session: Long = 1, ownVisible: Boolean = false, secure: Boolean = false,
                     window: Int? = 7, time: Long = ++frameTime * 1_000_000, w: Int = 1080, h: Int = 1200) =
        LiveFrameMeta(session, time, w, h, source, window, ownVisible, secure)
    private fun advance(ms: Long) { now += ms * 1_000_000 }

    @Test fun unboundHolderRefusesAndReleasesEveryFrame() {
        val f = Frame(1)
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.NOT_BOUND), holder.offer(meta(), f))
        assertEquals(1, f.released)
        assertEquals(LiveMonitorState.NO_SOURCE, holder.status(now).state)
        assertNull(holder.acquire())
    }

    @Test fun liveOnlyForFreshFrameOfBoundSessionThenPaused() {
        holder.bind(binding())
        assertEquals(LiveMonitorState.WAITING, holder.status(now).state)
        assertTrue(holder.offer(meta(), Frame(1)) is LiveFrameOffer.Accepted)
        assertEquals(LiveMonitorState.LIVE, holder.status(now).state)
        assertEquals("Live", holder.status(now).label)
        advance(1_501)
        val paused = holder.status(now)
        assertEquals(LiveMonitorState.PAUSED, paused.state)
        assertEquals(LiveMonitorReason.FRAME_STALE, paused.reason)
        assertTrue(paused.label.startsWith("Pausiert"))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.WRONG_SESSION), holder.offer(meta(session = 2), Frame(2)))
        assertEquals(LiveMonitorState.PAUSED, holder.status(now).state)
    }

    @Test fun ownWindowAndScreenInScreenAreRefusedAndCoverTheMonitor() {
        holder.bind(binding(origin = LiveFrameOrigin.PROJECTION_DISPLAY))
        assertTrue(holder.offer(meta(), Frame(1)) is LiveFrameOffer.Accepted)
        advance(200)
        // Whole-display capture while any Continuum window is visible = mirror loop, even if the target is active.
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.OWN_WINDOW_MIRROR), holder.offer(meta(ownVisible = true), Frame(2)))
        val covered = holder.status(now)
        assertEquals(LiveMonitorState.COVERED, covered.state)
        assertEquals(LiveMonitorReason.OWN_WINDOW_MIRROR, covered.reason)
        // The older accepted frame is not kept on screen as if current.
        assertNull(holder.acquire())
        assertEquals(listOf(1, 2), released.sorted())
        advance(200)
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.OWN_WINDOW_MIRROR), holder.offer(meta(source = own), Frame(3)))
        assertThrows(IllegalArgumentException::class.java) { binding(target = own) }
    }

    @Test fun secureUnverifiedAndForeignSourcesNeverShow() {
        holder.bind(binding(origin = LiveFrameOrigin.ACCESSIBILITY_WINDOW))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.SECURE_CONTENT), holder.offer(meta(secure = true), Frame(1)))
        assertEquals(LiveMonitorReason.SECURE_CONTENT, holder.status(now).reason)
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.SOURCE_UNVERIFIED), holder.offer(meta(source = null), Frame(2)))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.SOURCE_UNVERIFIED), holder.offer(meta(window = null), Frame(3)))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.WRONG_SOURCE), holder.offer(meta(source = "com.facebook.katana"), Frame(4)))
        assertEquals(LiveMonitorState.COVERED, holder.status(now).state)
        assertEquals(listOf(1, 2, 3, 4), released)
        // A matching frame lifts the cover again.
        assertTrue(holder.offer(meta(), Frame(5)) is LiveFrameOffer.Accepted)
        assertEquals(LiveMonitorState.LIVE, holder.status(now).state)
    }

    @Test fun appSwitchDropsPreviousAppFramesImmediately() {
        holder.bind(binding())
        assertTrue(holder.offer(meta(), Frame(1)) is LiveFrameOffer.Accepted)
        holder.bind(binding(target = "com.sec.android.app.myfiles", session = 2, task = "task-1"))
        assertEquals(listOf(1), released)
        assertNull(holder.acquire())
        assertEquals(LiveMonitorState.WAITING, holder.status(now).state)
        // A late WhatsApp frame of the new session is a wrong source, not a live frame.
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.WRONG_SOURCE), holder.offer(meta(session = 2), Frame(2)))
        assertTrue(holder.offer(meta(source = "com.sec.android.app.myfiles", session = 2), Frame(3)) is LiveFrameOffer.Accepted)
        holder.acquire()!!.use { assertEquals("com.sec.android.app.myfiles", it.binding.targetPackage); assertEquals(3, it.frame.id) }
    }

    @Test fun latestOnlyWithAtMostTwoFramesAliveAndLeaseSafeRelease() {
        holder.bind(binding())
        holder.offer(meta(), Frame(1))
        val lease = holder.acquire()!!
        assertNull("one lease at a time", holder.acquire())
        for (i in 2..20) { advance(100); assertTrue(holder.offer(meta(), Frame(i)) is LiveFrameOffer.Accepted) }
        assertTrue(holder.retainedFrames() <= 2)
        assertFalse("leased frame is not released under the renderer", 1 in released)
        assertNull("no second lease while the predecessor is still drawn", holder.acquire())
        lease.close(); lease.close()
        assertEquals(1, released.count { it == 1 })
        assertEquals((1..19).toList(), released.sorted())
        holder.acquire()!!.use { assertEquals(20, it.frame.id) }
        assertEquals(1, holder.retainedFrames())
    }

    @Test fun rateLimitOutOfOrderAndOversizedFramesAreDroppedBeforeAnyWork() {
        holder.bind(binding())
        assertTrue(holder.offer(meta(), Frame(1)) is LiveFrameOffer.Accepted)
        assertFalse(holder.wantsFrame(1))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.RATE_LIMITED), holder.offer(meta(), Frame(2)))
        advance(100)
        assertTrue(holder.wantsFrame(1))
        assertFalse(holder.wantsFrame(2))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.OUT_OF_ORDER), holder.offer(meta(time = 1), Frame(3)))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.INVALID_SIZE), holder.offer(meta(w = 4_000), Frame(4)))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.INVALID_SIZE), holder.offer(meta(w = 3_000, h = 2_000), Frame(5)))
        assertEquals(listOf(2, 3, 4, 5), released)
    }

    @Test fun pauseAndEndAreVisibleAndReleaseFrames() {
        holder.bind(binding())
        holder.offer(meta(), Frame(1))
        val versionBefore = holder.version()
        holder.pause(LiveMonitorReason.SOURCE_HIDDEN)
        assertTrue(holder.version() > versionBefore)
        assertEquals(LiveMonitorState.COVERED, holder.status(now).state)
        assertEquals("Verdeckt · Ziel-App nicht sichtbar", holder.status(now).label)
        assertNull(holder.acquire())
        advance(100)
        holder.offer(meta(), Frame(2))
        val lease = holder.acquire()!!
        holder.end()
        assertEquals(LiveMonitorState.ENDED, holder.status(now).state)
        assertFalse(2 in released)
        lease.close()
        assertEquals(listOf(1, 2), released)
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.STOPPED), holder.offer(meta(), Frame(3)))
        assertFalse(holder.wantsFrame(1))
        holder.pause(LiveMonitorReason.FRAME_STALE)
        assertEquals(LiveMonitorState.ENDED, holder.status(now).state)
    }

    @Test fun oldSessionCannotPauseNewCapture() {
        holder.bind(binding(session = 2, origin = LiveFrameOrigin.ACCESSIBILITY_WINDOW))
        holder.offer(meta(session = 2, w = 2184, h = 1968), Frame(1))
        holder.pause(LiveMonitorReason.SOURCE_HIDDEN, captureSession = 1)
        assertEquals(LiveMonitorState.LIVE, holder.status(now).state)
    }

    @Test fun protectionClearsFrameEvenInsideRateLimit() {
        holder.bind(binding())
        holder.offer(meta(), Frame(1))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.SECURE_CONTENT), holder.offer(meta(secure = true), Frame(2)))
        assertEquals(LiveMonitorState.COVERED, holder.status(now).state)
        assertNull(holder.acquire())
    }

    @Test fun pausedSourceCannotReplayOlderFrame() {
        holder.bind(binding())
        holder.offer(meta(time = 100_000_000), Frame(1))
        holder.pause(LiveMonitorReason.SOURCE_HIDDEN)
        advance(200)
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.OUT_OF_ORDER), holder.offer(meta(time = 50_000_000), Frame(2)))
        assertNull(holder.acquire())
    }

    @Test fun deliveryAndGpuDelayDoNotRenewCaptureFreshness() {
        holder.bind(binding(origin = LiveFrameOrigin.ACCESSIBILITY_WINDOW))
        val delayed = meta().copy(captureAgeMillis = 1_400)
        assertTrue(holder.offer(delayed, Frame(1)) is LiveFrameOffer.Accepted)
        assertEquals(1_400L, holder.status(now).ageMillis)
        advance(101)
        assertEquals(1_501L, holder.status(now).ageMillis)
        assertEquals(LiveMonitorState.PAUSED, holder.status(now).state)
        assertEquals(LiveMonitorReason.FRAME_STALE, holder.status(now).reason)
    }

    @Test fun alreadyStaleOrInvalidCaptureIsReleasedWithoutBecomingLive() {
        holder.bind(binding())
        holder.offer(meta(), Frame(1))
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.STALE_CAPTURE),
            holder.offer(meta().copy(captureAgeMillis = 1_501), Frame(2)))
        assertEquals(LiveMonitorState.PAUSED, holder.status(now).state)
        assertNull(holder.acquire())
        assertEquals(listOf(1, 2), released)
        assertEquals(LiveFrameOffer.Refused(LiveFrameRefusal.STALE_CAPTURE),
            holder.offer(meta().copy(captureAgeMillis = -1), Frame(3)))
    }
}
