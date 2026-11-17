package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveComputerDispatchGuardTest {
    private var now = 1_000_000_000L
    private val task = "owner-task"
    private val session = 47L
    private val pkg = "com.whatsapp"
    private val binding = LiveFrameBinding(task, session, pkg, "at.heinz.deviceai.continuumlocal",
        LiveFrameOrigin.ACCESSIBILITY_WINDOW, staleAfterMillis = 1_500)
    private val feed = LatestLiveFrameHolder<Int>({}, { now })

    private fun fresh(next: LiveFrameBinding = binding, submitted: Boolean = true) {
        feed.bind(next)
        assertTrue(feed.offer(LiveFrameMeta(next.captureSession, now, 400, 600,
            next.targetPackage, 42, false), 1) is LiveFrameOffer.Accepted)
        assertEquals(LiveMonitorState.LIVE, feed.status(now).state)
        if (submitted) {
            val state = feed.status(now)
            val imported = requireNotNull(feed.monitorSubmission.imported(state, now))
            assertTrue(feed.monitorSubmission.submitted(imported, state, now))
        }
    }

    private fun checkLive() = LiveComputerDispatchGuard.checkLive(feed, task, session, now)
    private fun denied(code: String = "COMPUTER_LIVE_FRAME_REQUIRED") {
        val error = assertThrows(IllegalStateException::class.java) { checkLive() }
        assertEquals(code, error.message)
        assertFalse(LiveComputerDispatchGuard.isReady(feed, task, session, now))
    }

    @Test fun freshSubmittedFrameAllowsInputWithoutConsumingIt() {
        fresh()
        checkLive()
        checkLive()
        feed.acquire()!!.use { assertEquals(session, it.binding.captureSession) }
        assertEquals(LiveMonitorState.LIVE, feed.status(now).state)
        assertTrue(LiveComputerDispatchGuard.isReady(feed, task, session, now))
    }

    @Test fun captureAndImportWithoutRenderCannotAuthorizeInput() {
        fresh(submitted = false)
        denied("COMPUTER_MONITOR_FRAME_REQUIRED")
        assertNotNull(feed.monitorSubmission.imported(feed.status(now), now))
        denied("COMPUTER_MONITOR_FRAME_REQUIRED")
    }

    @Test fun lostSurfaceImmediatelyRevokesDispatch() {
        fresh()
        checkLive()
        feed.monitorSubmission.invalidate()
        denied("COMPUTER_MONITOR_FRAME_REQUIRED")
    }

    @Test fun eachDispatchRechecksAgeInsteadOfTrustingFirstObservation() {
        fresh()
        checkLive()
        now += 1_500_000_000L
        checkLive()
        now += 1_000_000L
        assertEquals(LiveMonitorState.PAUSED, feed.status(now).state)
        denied()
    }

    @Test fun noSourceAndWaitingNeverPermitNativeInput() {
        assertEquals(LiveMonitorState.NO_SOURCE, feed.status(now).state)
        denied()
        feed.bind(binding)
        assertEquals(LiveMonitorState.WAITING, feed.status(now).state)
        denied()
    }

    @Test fun stoppedCaptureAndRevokedCaptureRejectFormerlyFreshFrame() {
        for (reason in listOf(LiveMonitorReason.CAPTURE_STOPPED, LiveMonitorReason.PERMISSION_REVOKED)) {
            fresh()
            checkLive()
            feed.end(reason)
            assertEquals(LiveMonitorState.ENDED, feed.status(now).state)
            denied()
        }
    }

    @Test fun coveredOrPausedSourceRejectsInputImmediately() {
        for (reason in listOf(LiveMonitorReason.SOURCE_HIDDEN, LiveMonitorReason.SECURE_CONTENT,
                LiveMonitorReason.FRAME_STALE, LiveMonitorReason.PERMISSION_REVOKED)) {
            fresh()
            checkLive()
            feed.pause(reason, session)
            assertNotEquals(LiveMonitorState.LIVE, feed.status(now).state)
            denied()
        }
    }

    @Test fun anotherFreshCaptureCannotAuthorizeOldSession() {
        fresh()
        checkLive()
        fresh(binding.copy(captureSession = session + 1))
        denied()
    }

    @Test fun anotherFreshTaskCannotBorrowSameCaptureId() {
        fresh()
        checkLive()
        fresh(binding.copy(taskId = "other-task"))
        denied()
    }
}
