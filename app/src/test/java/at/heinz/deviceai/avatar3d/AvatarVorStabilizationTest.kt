package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AvatarVorStabilizationTest {
    private fun pose(gazeX: Float = .5f, directed: Boolean = true, confirm: Float = -1f) =
        AvatarPose(AvatarState.LISTENING, AvatarExpression.NEUTRAL, gazeX, 0f, 0f, Viseme.SIL,
            0f, 0f, gazeDirected = directed, confirmationProgress = confirm)

    @Test fun extraHeadNodIsCounterRotatedByTheEyesOnADirectedGaze() {
        val base = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val nodded = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        base.update(pose(), 1f, true)
        nodded.update(pose(confirm = .5f), 1f, true)
        val extraPitch = nodded.headPitch - base.headPitch
        assertTrue("confirmation still nods the head", extraPitch > 1f)
        assertEquals(
            "eyes cancel the extra head pitch so the look-at point stays put",
            base.eyePitch - extraPitch * AvatarMorphMapper.VOR_GAIN,
            nodded.eyePitch,
            1e-4f,
        )
    }

    @Test fun idleSaccadesAreNotOverwrittenByCounterRotation() {
        val idle = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        idle.update(pose(gazeX = 0f, directed = false).copy(
            state = AvatarState.IDLE, gazeObserved = false), 2.4f, true)
        assertTrue(abs(idle.eyeYaw) > 0f || abs(idle.headYaw) > 0f)
    }
}
