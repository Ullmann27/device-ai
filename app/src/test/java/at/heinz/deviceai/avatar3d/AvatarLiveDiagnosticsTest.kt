package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarGestureIntent
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarLiveDiagnosticsTest {
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
                AvatarFrameReceipt.FILAMENT_BEGIN or AvatarFrameReceipt.FILAMENT_RENDERED
        }
        assertTrue(ring.diagnose().startsWith("AVATAR_PIPELINE_LIVE"))
    }
}
