package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarGestureIntent
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarLiveDiagnosticsTest {
    @Test fun submittedFrameWithoutConsumerUpdateCannotClaimLive() {
        val ring = AvatarTraceRingBuffer(32)
        ring.beginFrame(1L).stageFlags = AvatarFrameReceipt.FRAME_DRAW or
            AvatarFrameReceipt.HEAD_3D_SELECTED or AvatarFrameReceipt.SURFACE_VALID or
            AvatarFrameReceipt.TEXTURE_VISIBLE or AvatarFrameReceipt.POSE_SAMPLED or
            AvatarFrameReceipt.MORPH_UPLOADED or AvatarFrameReceipt.FILAMENT_BEGIN or
            AvatarFrameReceipt.FILAMENT_SUBMITTED
        val summary = AvatarProofCompletion.summarize(1, 1, ring.diagnose())
        assertFalse(summary.verdict, summary.consumerObserved)
        assertTrue(summary.verdict.startsWith("RENDER_SUBMITTED"))
    }

    @Test fun deterministicProofCoversIdleBlinkGazeSpeechThinkingAndCard() {
        val idle = DeterministicAvatarProofRunner.frame(1_000)
        val blink = DeterministicAvatarProofRunner.frame(2_100)
        val gaze = DeterministicAvatarProofRunner.frame(3_100)
        val speech = DeterministicAvatarProofRunner.frame(5_900)
        val thinking = DeterministicAvatarProofRunner.frame(10_000)
        val card = DeterministicAvatarProofRunner.frame(13_000)

        assertEquals(AvatarState.IDLE, idle.pose.state)
        assertTrue((blink.blinkOverride ?: 0f) > 0f)
        assertTrue(gaze.pose.gazeX < 0f)
        assertEquals(AvatarState.SPEAKING, speech.pose.state)
        assertTrue(speech.pose.viseme != Viseme.SIL)
        assertEquals(AvatarState.THINKING, thinking.pose.state)
        assertEquals(AvatarGestureIntent.THINK_MICRO, thinking.pose.gesture)
        assertEquals(AvatarGestureIntent.WINDOW_FOCUS, card.pose.gesture)
        assertTrue(card.cardProgress > 0f)
    }

    @Test fun proofOutputsStayBounded() {
        for (ms in 0L..DeterministicAvatarProofRunner.DURATION_MS step 17L) {
            val f = DeterministicAvatarProofRunner.frame(ms)
            assertTrue(f.orbEnergy in 0f..1f)
            assertTrue(f.cardProgress in 0f..1f)
            assertTrue(f.pose.gazeX in -1f..1f)
            assertTrue(f.pose.gazeY in -1f..1f)
            assertTrue(f.pose.jawOpen in 0f..1f)
            assertTrue(f.pose.visemeWeight in 0f..1f)
            assertTrue(f.blinkOverride == null || f.blinkOverride in 0f..1f)
        }
    }

    @Test fun proofCompletionRequiresCallbacksCanvasAndConsumerTrace() {
        assertTrue(AvatarProofCompletion.summarize(0, 0, "CONSUMER_FRAME_OBSERVED").verdict.startsWith("BROKEN_AT_FRAME_LOOP"))
        assertTrue(AvatarProofCompletion.summarize(4, 0, "CONSUMER_FRAME_OBSERVED").verdict.startsWith("BROKEN_AT_CANVAS"))
        assertTrue(AvatarProofCompletion.summarize(4, 4, "BROKEN_AT_SURFACE: x").verdict.startsWith("BROKEN_AT_SURFACE"))
        assertFalse(AvatarProofCompletion.summarize(4, 4, "AVATAR_PIPELINE_LIVE").consumerObserved)
        val observed = AvatarProofCompletion.summarize(4, 4, "CONSUMER_FRAME_OBSERVED: frame=4")
        assertTrue(observed.consumerObserved)
        assertEquals(4L, observed.callbackDelta)
        assertEquals(4L, observed.drawDelta)
    }

    @Test fun currentProofCannotBorrowLiveTraceFromAnEarlierRun() {
        val ring = AvatarTraceRingBuffer(32)
        val surface = Any()
        ring.bindSurface(surface, 0L)
        repeat(4) {
            val r = ring.beginFrame(it.toLong())
            r.stageFlags = AvatarFrameReceipt.FRAME_DRAW or AvatarFrameReceipt.HEAD_3D_SELECTED or
                AvatarFrameReceipt.SURFACE_VALID or AvatarFrameReceipt.TEXTURE_VISIBLE or
                AvatarFrameReceipt.POSE_SAMPLED or AvatarFrameReceipt.MORPH_UPLOADED or
                AvatarFrameReceipt.FILAMENT_BEGIN or AvatarFrameReceipt.FILAMENT_SUBMITTED
            ring.surfaceUpdated(surface, it + 1L)
        }
        val proofStart = ring.frameNumber()
        repeat(4) {
            val r = ring.beginFrame((10 + it).toLong())
            r.stageFlags = AvatarFrameReceipt.FRAME_DRAW or AvatarFrameReceipt.HEAD_3D_SELECTED
        }
        assertTrue("unscoped history contains the earlier consumer update", ring.diagnose().startsWith("CONSUMER_FRAME_OBSERVED"))
        assertTrue("current proof must ignore old live receipts",
            ring.diagnoseSince(proofStart).startsWith("BROKEN_AT_SURFACE"))
    }

    @Test fun traceNamesTheBrokenStageInsteadOfGuessing() {
        val ring = AvatarTraceRingBuffer(32)
        assertTrue(ring.diagnose().startsWith("BROKEN_AT_ROOT"))

        repeat(4) {
            val r = ring.beginFrame(it.toLong())
            r.stageFlags = AvatarFrameReceipt.FRAME_DRAW or AvatarFrameReceipt.HEAD_3D_SELECTED
        }
        assertTrue(ring.diagnose().startsWith("BROKEN_AT_SURFACE"))

        repeat(4) {
            val r = ring.beginFrame(it.toLong())
            r.stageFlags = AvatarFrameReceipt.FRAME_DRAW or AvatarFrameReceipt.HEAD_3D_SELECTED or
                AvatarFrameReceipt.SURFACE_VALID or AvatarFrameReceipt.TEXTURE_VISIBLE or
                AvatarFrameReceipt.POSE_SAMPLED or AvatarFrameReceipt.MORPH_UPLOADED or
                AvatarFrameReceipt.FILAMENT_BEGIN or AvatarFrameReceipt.FILAMENT_SUBMITTED
        }
        assertTrue(ring.diagnose().startsWith("RENDER_SUBMITTED"))
    }

    @Test fun consumerEvidenceRequiresCallbackAndTimestampAdvanceOnTheBoundSurface() {
        val ring = AvatarTraceRingBuffer(32)
        val surface = Any()
        ring.bindSurface(surface, 0L)
        submitted(ring)
        ring.surfaceUpdated(Any(), 100L)
        assertEquals(0L, ring.consumerSample().updateCount)
        ring.surfaceUpdated(surface, 0L)
        assertTrue(ring.diagnose().startsWith("RENDER_SUBMITTED"))
        ring.surfaceUpdated(surface, null)
        ring.surfaceUpdated(surface, 10L)
        assertTrue("Unknown timestamp cannot prove advancement", ring.diagnose().startsWith("RENDER_SUBMITTED"))
        ring.surfaceUpdated(surface, 9L)
        assertTrue("Regressed timestamp cannot prove advancement", ring.diagnose().startsWith("RENDER_SUBMITTED"))
        ring.surfaceUpdated(surface, 20L)
        assertEquals(5L, ring.consumerSample().updateCount)
        assertEquals(20L, ring.consumerSample().timestampNanos)
        assertTrue(ring.diagnose().startsWith("CONSUMER_FRAME_OBSERVED"))
        assertTrue(ring.diagnose().contains("Pixelinhalt ungeprüft"))
    }

    @Test fun replacementAndReleaseRejectPreviousSurfaceEvidence() {
        val ring = AvatarTraceRingBuffer(32)
        val old = Any()
        val current = Any()
        ring.bindSurface(old, 0L)
        submitted(ring)
        ring.surfaceUpdated(old, 10L)
        val oldSample = ring.consumerSample()
        assertTrue(ring.diagnose().startsWith("CONSUMER_FRAME_OBSERVED"))
        ring.bindSurface(current, 0L)
        submitted(ring)
        ring.surfaceUpdated(old, 20L)
        ring.clearSurface(old)
        assertEquals(0L, ring.consumerSample().updateCount)
        assertTrue(ring.diagnose().startsWith("RENDER_SUBMITTED"))
        ring.surfaceUpdated(current, 5L)
        assertTrue(ring.diagnose().startsWith("CONSUMER_FRAME_OBSERVED"))
        assertTrue(ring.consumerSample().progressSince(oldSample).contains("Surface im Messfenster geändert"))
        ring.clearSurface()
        ring.surfaceUpdated(current, 30L)
        assertEquals(0L, ring.consumerSample().updateCount)
        assertFalse(ring.diagnose().startsWith("CONSUMER_FRAME_OBSERVED"))
    }

    @Test fun newSubmissionCannotBorrowEarlierConsumerUpdate() {
        val ring = AvatarTraceRingBuffer(32)
        val surface = Any()
        ring.bindSurface(surface, 0L)
        submitted(ring)
        ring.surfaceUpdated(surface, 10L)
        val proofStart = ring.frameNumber()
        submitted(ring)
        ring.surfaceUpdated(surface, 10L)
        assertTrue(ring.diagnoseSince(proofStart).startsWith("RENDER_SUBMITTED"))
        ring.surfaceUpdated(surface, 20L)
        assertTrue(ring.diagnoseSince(proofStart).startsWith("CONSUMER_FRAME_OBSERVED"))
    }

    @Test fun consumerReceiptExpiresWithTheBoundedTraceWindow() {
        val ring = AvatarTraceRingBuffer(16)
        val surface = Any()
        ring.bindSurface(surface, 0L)
        submitted(ring)
        ring.surfaceUpdated(surface, 10L)
        repeat(16) { submitted(ring) }
        assertTrue(ring.diagnose().startsWith("RENDER_SUBMITTED"))
        ring.surfaceUpdated(surface, 20L)
        assertTrue(ring.diagnose().startsWith("CONSUMER_FRAME_OBSERVED"))
    }

    @Test fun motionWindowReportsUpdatesAndTimestampProgressSeparately() {
        val ring = AvatarTraceRingBuffer(32)
        val surface = Any()
        ring.bindSurface(surface, 0L)
        val start = ring.consumerSample()
        submitted(ring)
        assertTrue(ring.consumerSample().progressSince(start).startsWith("Keine neue TextureView"))
        ring.surfaceUpdated(surface, 0L)
        assertTrue(ring.consumerSample().progressSince(start).contains("kein Zeitstempelfortschritt"))
        ring.surfaceUpdated(surface, 10L)
        assertTrue(ring.consumerSample().progressSince(start).contains("Consumer-Zeitstempel fortgeschritten"))
        val nextWindow = ring.consumerSample()
        submitted(ring)
        assertTrue(ring.consumerSample().progressSince(nextWindow).startsWith("Keine neue TextureView"))
        ring.surfaceUpdated(surface, null)
        assertTrue(ring.consumerSample().progressSince(nextWindow).contains("nicht messbar"))
    }

    private fun submitted(ring: AvatarTraceRingBuffer) {
        ring.beginFrame(ring.frameNumber() + 1).stageFlags = AvatarFrameReceipt.FRAME_DRAW or
            AvatarFrameReceipt.HEAD_3D_SELECTED or AvatarFrameReceipt.SURFACE_VALID or
            AvatarFrameReceipt.TEXTURE_VISIBLE or AvatarFrameReceipt.POSE_SAMPLED or
            AvatarFrameReceipt.MORPH_UPLOADED or AvatarFrameReceipt.FILAMENT_BEGIN or
            AvatarFrameReceipt.FILAMENT_SUBMITTED
    }
}
