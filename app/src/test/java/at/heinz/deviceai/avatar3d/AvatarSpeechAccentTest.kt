package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner brief 05.11.2026: while speaking the face must not be frozen around a moving jaw. The head accents the real
 * mouth opening (driven by TTS word ranges via the director), stays inside the reach the framing reserves, and yields
 * to reduced motion and to a directed/observed gaze.
 */
class AvatarSpeechAccentTest {
    private fun pose(jaw: Float, directed: Boolean = false) = AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL,
        0f, 0f, jaw, if (jaw > 0f) Viseme.AA else Viseme.SIL, if (jaw > 0f) 1f else 0f, jaw, gazeDirected = directed)

    /** Syllable-like real mouth opening at ~4 Hz for 3 s, 60 fps. */
    private fun run(map: AvatarMorphMapper, motion: Boolean = true, directed: Boolean = false): Pair<List<Float>, List<Float>> {
        val pitch = ArrayList<Float>(); val yaw = ArrayList<Float>()
        for (i in 0 until 180) {
            val t = i / 60f
            map.update(pose(.6f * kotlin.math.abs(kotlin.math.sin(2.0 * Math.PI * 4.0 * t)).toFloat(), directed), t, motion)
            pitch += map.headPitch; yaw += map.headYaw
        }
        return pitch to yaw
    }

    private fun range(v: List<Float>) = v.max() - v.min()

    @Test fun speakingHeadAccentsTheRealMouthOpeningWithinTheReservedReach() {
        val (pitch, yaw) = run(AvatarMorphMapper(AvatarGlbContract.TARGETS))
        assertTrue("head nods with the syllables: pitch range ${range(pitch)}", range(pitch) > .3f)
        assertTrue("head is not frozen sideways: yaw range ${range(yaw)}", range(yaw) > .2f)
        assertTrue(pitch.all { kotlin.math.abs(it) <= AvatarMorphMapper.MAX_HEAD_PITCH_DEGREES })
        assertTrue(yaw.all { kotlin.math.abs(it) <= AvatarMorphMapper.MAX_HEAD_YAW_DEGREES })
    }

    @Test fun reducedMotionAndDirectedGazeKeepTheHeadStill() {
        val (pitch, yaw) = run(AvatarMorphMapper(AvatarGlbContract.TARGETS), motion = false)
        assertEquals(0f, range(pitch), 1e-4f); assertEquals(0f, range(yaw), 1e-4f)
        val (dPitch, dYaw) = run(AvatarMorphMapper(AvatarGlbContract.TARGETS), directed = true)
        assertEquals("a window glance wins", 0f, range(dPitch), 1e-3f); assertEquals(0f, range(dYaw), 1e-3f)
    }
}
