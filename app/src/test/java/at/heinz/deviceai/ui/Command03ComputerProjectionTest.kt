package at.heinz.deviceai.ui

import at.heinz.deviceai.cognitive.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

class Command03ComputerProjectionTest {
    private val session = "11111111-1111-4111-8111-111111111111"
    private var now = 1_000_000_000L
    private val binding = LiveFrameBinding("computer-test", 7, "com.example.target", "at.heinz.deviceai",
        LiveFrameOrigin.ACCESSIBILITY_WINDOW, minFrameIntervalMillis = 0)
    private val released = mutableListOf<Int>()
    private val holder = LatestLiveFrameHolder<Int>({ released += it }, { now }).apply { bind(binding) }
    private val jobs = ArrayDeque<() -> Unit>()
    private val deliveries = ArrayDeque<() -> Unit>()
    private val packets = mutableListOf<Command03ComputerPacket>()
    private var reject = false
    private var invalidations = 0
    private val projection = Command03ComputerProjection<Int>(
        work = { if (reject) throw RejectedExecutionException() else jobs.addLast(it) },
        ui = { deliveries.addLast(it) },
        encode = { Command03EncodedFrame(320, 240, byteArrayOf(-1, -40, -1, -39)) },
        emit = { packets += it }, invalidateView = { invalidations++ }, clock = { now }
    ).apply { bind(session, holder) }

    private fun offer(value: Int = 1, age: Long = 0) {
        assertTrue(holder.offer(LiveFrameMeta(7, now, 320, 240, binding.targetPackage, 12,
            false, observationRevision = 3, sourceViewport = LiveTargetBounds(0f, 0f, 1f, 1f),
            captureAgeMillis = age), value) is LiveFrameOffer.Accepted)
    }
    private fun finishEncoding() { jobs.removeFirst().invoke(); deliveries.removeFirst().invoke() }
    private fun ready() = holder.monitorSubmission.isReady(holder.status(now), now)
    private fun latestFrame() = packets.last { it.frame != null }

    @Test fun sendingAndDecodingNeverSubmitUntilTheVisibleNativeDrawBoundary() {
        offer(); projection.tick(true)
        assertFalse(ready())
        finishEncoding()
        val ack = latestFrame().acknowledgement
        assertFalse(ready())
        assertFalse(projection.drawn(ack, visible = false))
        assertFalse(projection.drawn(ack.copy(sequence = ack.sequence + 1), true))
        assertTrue(projection.drawn(ack, true))
        assertTrue(ready())
        assertFalse("A replay cannot renew a rendered receipt", projection.drawn(ack, true))
    }

    @Test fun encodingPreservesOriginalAgeAndDropsFramesThatExpiredWhileWorking() {
        offer(age = 1_000); projection.tick(true)
        now += 300_000_000
        finishEncoding()
        assertEquals(1_300L, latestFrame().ageMs)
        assertEquals(1_500L, latestFrame().staleAfterMs)
        now += 201_000_000
        assertFalse(projection.drawn(latestFrame().acknowledgement, true))
        projection.tick(true)
        assertEquals("PAUSED", packets.last().status)
        assertFalse(ready())
    }

    @Test fun stopRebindAndSessionChangeDiscardAlreadyEncodedLateDeliveries() {
        offer(); projection.tick(true)
        jobs.removeFirst().invoke()
        projection.suspend()
        val afterStop = packets.size
        deliveries.removeFirst().invoke()
        assertEquals(afterStop, packets.size)
        assertFalse(ready())
        projection.bind("22222222-2222-4222-8222-222222222222", holder)
        now += 200_000_000
        offer(2); projection.tick(true); finishEncoding()
        val packet = latestFrame()
        assertFalse(projection.drawn(packet.acknowledgement.copy(sessionId = session), true))
        assertTrue(projection.drawn(packet.acknowledgement, true))
    }

    @Test fun oneEncoderAndOnePendingImageKeepTheExistingTwoFrameLeaseBound() {
        offer(1); projection.tick(true)
        repeat(5) {
            now += 200_000_000
            offer(it + 2); projection.tick(true)
            assertEquals(1, jobs.size)
            assertTrue(holder.retainedFrames() <= 2)
        }
        finishEncoding()
        assertTrue(released.contains(1))
        repeat(5) { projection.tick(true) }
        assertEquals("An unacknowledged image cannot create an encode queue", 0, jobs.size)
        assertFalse(ready())
        projection.suspend()
        holder.end()
        assertEquals(0, holder.retainedFrames())
    }

    @Test fun currentHideIsPersistentButARealNewFeedBindingCanStartAgain() {
        offer(); projection.tick(true); finishEncoding()
        val old = latestFrame().acknowledgement
        assertTrue(projection.drawn(old, true))
        assertTrue(projection.hidden(old))
        now += 200_000_000; offer(2)
        repeat(3) { projection.tick(true) }
        assertEquals(0, jobs.size)
        assertFalse(ready())
        // OwnerComputerSessionController.begin calls stop/setFeed(null) before rebinding the singleton feed.
        projection.bind(session, null)
        projection.bind(session, holder)
        projection.tick(true); finishEncoding()
        val current = latestFrame().acknowledgement
        assertTrue(current.generation > old.generation)
        assertFalse(projection.hidden(old))
        assertTrue(projection.drawn(current, true))
    }

    @Test fun replacementHidesOnlyTheOldReceiptAndKeepsItsPendingSuccessor() {
        offer(); projection.tick(true); finishEncoding()
        val old = latestFrame().acknowledgement
        assertTrue(projection.drawn(old, true))
        now += 200_000_000; offer(2); projection.tick(true); finishEncoding()
        val current = latestFrame().acknowledgement
        assertFalse(projection.hidden(old))
        assertFalse(ready())
        assertTrue(projection.drawn(current, true))
        assertFalse(projection.hidden(old))
        assertTrue(ready())
    }

    @Test fun rejectedExecutorImmediatelyWithdrawsThePreviousVisibleReceipt() {
        offer(); projection.tick(true); finishEncoding()
        assertTrue(projection.drawn(latestFrame().acknowledgement, true))
        reject = true
        now += 200_000_000; offer(2); projection.tick(true)
        assertEquals("PAUSED", packets.last().status)
        assertFalse(ready())
        assertTrue(invalidations > 0)
        holder.end()
        assertEquals(0, holder.retainedFrames())
    }

    @Test fun coverBeforeEncoderCompletionCannotSubmitTheRecoveredSourcesPixels() {
        offer(); projection.tick(true)
        holder.pause(LiveMonitorReason.SECURE_CONTENT)
        now += 200_000_000; offer(2)
        finishEncoding()
        assertTrue(packets.none { it.frame != null })
        assertFalse(ready())
        projection.tick(true); finishEncoding()
        assertTrue(projection.drawn(latestFrame().acknowledgement, true))
    }

    @Test fun aFramePacketCarriesTheMeasuredGeometryOfExactlyThatFrameAndAStatusPacketCarriesNone() {
        val crop = LiveTargetBounds(.1f, .2f, .9f, .8f)
        assertTrue(holder.offer(LiveFrameMeta(7, now, 320, 240, binding.targetPackage, 12, false,
            observationRevision = 5, sourceViewport = crop), 1) is LiveFrameOffer.Accepted)
        val sourceSequence = holder.status(now).sequence
        projection.tick(true); finishEncoding()
        val frame = latestFrame()
        assertEquals(sourceSequence, frame.sourceSequence)
        assertEquals(5L, frame.observationRevision)
        assertEquals(crop, frame.viewport)
        assertEquals("com.example.target", frame.binding?.targetPackage)
        // Without a fresh frame only the state is reported: no geometry of an older image is repeated.
        now += 1_600_000_000
        projection.tick(true)
        val status = packets.last()
        assertNull(status.frame)
        assertNull(status.sourceSequence)
        assertNull(status.viewport)
        assertNull(status.observationRevision)
    }

    @Test fun malformedOrOversizeEncodedImagesNeverSatisfyTheTransportBudget() {
        assertFalse(Command03EncodedFrame(641, 240, byteArrayOf(-1, -40, -1, -39)).valid())
        assertFalse(Command03EncodedFrame(320, 240, ByteArray(131_073)).valid())
        assertFalse(Command03EncodedFrame(320, 240, byteArrayOf(0, 1, 2, 3)).valid())
    }
}
