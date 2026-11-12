package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveMonitorSubmissionTest {
    private var now = 1_000_000_000L
    private val binding = LiveFrameBinding("render-task", 7L, "com.example.target", "at.heinz.deviceai",
        LiveFrameOrigin.PROJECTION_WINDOW_CROP, minFrameIntervalMillis = 0)
    private val holder = LatestLiveFrameHolder<Int>({}, { now }).apply { bind(binding) }
    private val viewport = LiveTargetBounds(0f, 0f, 1f, .7f)

    private fun offer(window: Int = 42, source: LiveTargetBounds = viewport, age: Long = 0L,
                      width: Int = 400) {
        assertTrue(holder.offer(LiveFrameMeta(binding.captureSession, now, width, 600,
            binding.targetPackage, window, false, sourceViewport = source, captureAgeMillis = age), 1)
            is LiveFrameOffer.Accepted)
    }

    private fun imported() = requireNotNull(holder.monitorSubmission.imported(holder.status(now), now))
    private fun submit(frame: LiveMonitorSubmission.ImportedFrame) =
        holder.monitorSubmission.submitted(frame, holder.status(now), now)
    private fun ready() = holder.monitorSubmission.isReady(holder.status(now), now)

    @Test fun importAloneDoesNotMeanRenderSubmission() {
        offer()
        val frame = imported()
        assertTrue(holder.monitorSubmission.usable(frame, holder.status(now), now))
        assertFalse(ready())
        assertTrue(submit(frame))
        assertTrue(ready())
    }

    @Test fun advancingProducerDoesNotStarveFreshRenderedSequence() {
        offer()
        val frame = imported()
        assertTrue(submit(frame))
        now += 100_000_000L
        offer()
        assertTrue(holder.status(now).sequence > frame.status.sequence)
        assertTrue(ready())
        assertTrue(submit(frame))
    }

    @Test fun submittedSnapshotUsesRenderedSequenceAndActualAge() {
        offer(age = 20L)
        val frame = imported()
        assertTrue(submit(frame))
        now += 100_000_000L
        offer()
        val shown = requireNotNull(holder.monitorSubmission.submittedFrame(holder.status(now), now))
        assertEquals(frame.status.sequence, shown.sequence)
        assertEquals(viewport, shown.meta?.sourceViewport)
        assertEquals(120L, shown.ageMillis)
        holder.monitorSubmission.invalidate()
        assertNull(holder.monitorSubmission.submittedFrame(holder.status(now), now))
    }

    @Test fun newCaptureCannotRenewOldRenderedImageAge() {
        offer(age = 1_200L)
        val frame = imported()
        assertTrue(submit(frame))
        now += 300_000_000L
        offer()
        assertTrue(submit(frame))
        now += 1_000_000L
        assertEquals(LiveMonitorState.LIVE, holder.status(now).state)
        assertFalse(ready())
        assertFalse(submit(frame))
        assertTrue(submit(imported()))
    }

    @Test fun surfaceInvalidationRejectsDelayedSubmissionToken() {
        offer()
        val frame = imported()
        assertTrue(submit(frame))
        holder.monitorSubmission.invalidate()
        assertFalse(ready())
        assertFalse(submit(frame))
        assertTrue(submit(imported()))
    }

    @Test fun resumedCaptureRequiresNewImportAndSubmission() {
        offer()
        val frame = imported()
        assertTrue(submit(frame))
        holder.pause(LiveMonitorReason.SOURCE_HIDDEN)
        now += 100_000_000L
        offer()
        assertFalse(ready())
        assertFalse(submit(frame))
        assertTrue(submit(imported()))
    }

    @Test fun pauseBetweenSnapshotAndImportCannotReviveOldPixels() {
        offer()
        val before = holder.status(now)
        holder.pause(LiveMonitorReason.SOURCE_HIDDEN)
        now += 100_000_000L
        offer()
        val delayed = requireNotNull(holder.monitorSubmission.imported(before, now))
        assertNotEquals(before.sourceEpoch, holder.status(now).sourceEpoch)
        assertFalse(submit(delayed))
        assertFalse(ready())
    }

    @Test fun geometryAndWindowChangesRequireMatchingRenderedImage() {
        offer()
        assertTrue(submit(imported()))
        now += 100_000_000L
        offer(source = LiveTargetBounds(0f, 0f, 1f, .5f))
        assertFalse(ready())
        assertTrue(submit(imported()))
        now += 100_000_000L
        offer(window = 43, source = LiveTargetBounds(0f, 0f, 1f, .5f))
        assertFalse(ready())
        assertTrue(submit(imported()))
        now += 100_000_000L
        offer(window = 43, source = LiveTargetBounds(0f, 0f, 1f, .5f), width = 600)
        assertFalse(ready())
    }

    @Test fun sourceRebindRejectsOldTokenEvenWithIdenticalBinding() {
        offer()
        val frame = imported()
        assertTrue(submit(frame))
        holder.bind(binding)
        now += 100_000_000L
        offer()
        assertFalse(submit(frame))
        assertFalse(ready())
    }

    @Test fun differentTrackerCannotBorrowSubmissionToken() {
        offer()
        val frame = imported()
        val other = LiveMonitorSubmission()
        assertFalse(other.submitted(frame, holder.status(now), now))
        assertFalse(other.isReady(holder.status(now), now))
    }

    @Test fun clockRegressionCannotAuthorizeSubmission() {
        offer()
        val frame = imported()
        assertTrue(submit(frame))
        now -= 1L
        assertFalse(ready())
        assertFalse(submit(frame))
    }
}
