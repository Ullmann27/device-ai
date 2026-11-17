package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Speech boundaries are observable facial behaviour, independent of callback/render cadence. */
class AvatarArticulationBoundaryTest {
    @Test fun bilabialOnsetClosesOutgoingVowelsAndMeasuredJawTogether() {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(pose(Viseme.AA, 1f), 1f, true)

        mapper.update(pose(Viseme.PP, 1f), 1.016f, true)

        assertEquals(1f, weight(mapper, "PP"), 0f)
        assertEquals(0f, weight(mapper, "AA"), 0f)
        assertEquals(0f, weight(mapper, "JawOpen"), 0f)
        assertEquals(0f, mapper.jawPitch, 0f)
        for (viseme in Viseme.entries.filter { it != Viseme.PP }) {
            assertEquals(viseme.name, 0f, weight(mapper, viseme.name), 0f)
        }
    }

    @Test fun bilabialClosureAlsoWinsAtAnUnchangedRenderTimestamp() {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(pose(Viseme.O, .8f), 1f, true)

        mapper.update(pose(Viseme.PP, .8f), 1f, true)

        assertEquals(1f, weight(mapper, "PP"), 0f)
        assertEquals(0f, weight(mapper, "O"), 0f)
        assertEquals(0f, weight(mapper, "JawOpen"), 0f)
        assertEquals(0f, mapper.jawPitch, 0f)
    }

    @Test fun renderingTheSameInstantTwiceDoesNotAdvanceAVowelTransition() {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(pose(Viseme.AA, .6f), 1f, true)
        mapper.update(pose(Viseme.O, .6f), 1.016f, true)
        val first = mapper.weights.copyOf()
        val firstJaw = mapper.jawPitch

        mapper.update(pose(Viseme.O, .6f), 1.016f, true)

        assertArrayEquals(first, mapper.weights, 0f)
        assertEquals(firstJaw, mapper.jawPitch, 0f)
    }

    @Test fun vowelTransitionAtTheSameElapsedTimeDoesNotDependOnFrameCount() {
        val frequent = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val sparse = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        for (mapper in listOf(frequent, sparse)) mapper.update(pose(Viseme.AA, .6f), 1f, true)

        frequent.update(pose(Viseme.O, .6f), 1.016f, true)
        frequent.update(pose(Viseme.O, .6f), 1.032f, true)
        sparse.update(pose(Viseme.O, .6f), 1.032f, true)

        for (target in listOf("AA", "O", "JawOpen")) {
            assertEquals(target, weight(frequent, target), weight(sparse, target), .00001f)
        }
        assertEquals(frequent.jawPitch, sparse.jawPitch, .0001f)
    }

    private fun pose(viseme: Viseme, jaw: Float) =
        AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, 0f, 0f, jaw, viseme, 1f, jaw)

    private fun weight(mapper: AvatarMorphMapper, name: String) =
        mapper.weights[AvatarGlbContract.TARGETS.indexOf(name)]
}
