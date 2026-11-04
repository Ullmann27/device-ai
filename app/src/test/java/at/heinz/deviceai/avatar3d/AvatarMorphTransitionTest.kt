package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared mouth opening must follow the visible morphs throughout their 70 ms crossfade. */
class AvatarMorphTransitionTest {
    @Test fun outgoingVowelDoesNotAddItsOpeningToTheMeasuredJaw() {
        val map = mapper()
        map.update(pose(Viseme.AA, .6f), 1f, true)
        for (frame in 1..5) {
            map.update(pose(Viseme.SS, .6f), 1f + frame * .016f, true)
            val aa = (1f - frame * .016f / .07f).coerceAtLeast(0f)
            assertEquals("AA at frame $frame", aa, weight(map, "AA"), EPSILON)
            assertEquals("SS at frame $frame", 1f - aa, weight(map, "SS"), EPSILON)
            // SS has no authored opening and does not require PP's immediate lip seal.
            // At 16 ms the outgoing AA opening is about .626785, already greater than .6.
            assertOpening(map, .6f, aa * AA_OPENING, "AA -> SS frame $frame")
        }
    }

    @Test fun bilabialSealRejectsSustainedMeasuredJawAfterAVowel() {
        val map = mapper()
        map.update(pose(Viseme.AA, .6f), 1f, true)
        for (frame in 1..5) {
            map.update(pose(Viseme.PP, .6f), 1f + frame * .016f, true)
            assertEquals("AA at frame $frame", 0f, weight(map, "AA"), 0f)
            assertEquals("PP at frame $frame", 1f, weight(map, "PP"), 0f)
            assertOpening(map, 0f, 0f, "AA -> PP frame $frame")
        }
    }

    @Test fun vowelTransitionCompensatesBothVisibleOpeningComponents() {
        val map = mapper()
        map.update(pose(Viseme.AA, .6f), 1f, true)
        for (frame in 1..5) {
            map.update(pose(Viseme.O, .6f), 1f + frame * .016f, true)
            val aa = (1f - frame * .016f / .07f).coerceAtLeast(0f)
            val rounded = 1f - aa
            assertEquals("AA at frame $frame", aa, weight(map, "AA"), EPSILON)
            assertEquals("O at frame $frame", rounded, weight(map, "O"), EPSILON)
            assertOpening(map, .6f, aa * AA_OPENING + rounded * O_OPENING, "AA -> O frame $frame")
        }
    }

    @Test fun incomingVowelDoesNotPrematurelyRemoveMeasuredJawOpening() {
        val map = mapper()
        map.update(pose(Viseme.PP, .6f), 1f, true)
        for (frame in 1..5) {
            map.update(pose(Viseme.AA, .6f), 1f + frame * .016f, true)
            val aa = (frame * .016f / .07f).coerceAtMost(1f)
            assertEquals("AA at frame $frame", aa, weight(map, "AA"), EPSILON)
            assertOpening(map, .6f, aa * AA_OPENING, "PP -> AA frame $frame")
        }
    }

    @Test fun outgoingVowelKeepsTheInnerJawAlignedWithoutMeasuredAudio() {
        val map = mapper()
        map.update(pose(Viseme.AA, 0f), 1f, true)
        for (frame in 1..5) {
            map.update(pose(Viseme.SS, 0f), 1f + frame * .016f, true)
            val aa = (1f - frame * .016f / .07f).coerceAtLeast(0f)
            assertEquals("AA at frame $frame", aa, weight(map, "AA"), EPSILON)
            assertEquals("SS at frame $frame", 1f - aa, weight(map, "SS"), EPSILON)
            assertOpening(map, 0f, aa * AA_OPENING, "cue-only AA -> SS frame $frame")
        }
    }

    @Test fun bilabialSealClosesCueOnlyOpeningWithoutMeasuredAudio() {
        val map = mapper()
        map.update(pose(Viseme.AA, 0f), 1f, true)
        for (frame in 1..5) {
            map.update(pose(Viseme.PP, 0f), 1f + frame * .016f, true)
            assertEquals(0f, weight(map, "AA"), 0f)
            assertEquals(1f, weight(map, "PP"), 0f)
            assertOpening(map, 0f, 0f, "cue-only AA -> PP frame $frame")
        }
    }

    @Test fun silenceOrZeroWeightClosesAnInProgressBlendImmediately() {
        for (stopped in listOf(pose(Viseme.SIL, 0f), pose(Viseme.O, 0f, 0f))) {
            val map = activeBlend()
            map.update(stopped, 1.032f, true)
            assertClosed(map)
            map.update(pose(Viseme.PP, 0f), 1.048f, true)
            assertEquals(0f, weight(map, "AA"), 0f)
            assertEquals(0f, weight(map, "O"), 0f)
            assertEquals(0f, map.jawPitch, 0f)
        }
    }

    @Test fun reducedMotionClearsTheBlendAndResumesWithoutAnOldVowel() {
        val map = activeBlend()
        map.update(pose(Viseme.O, .6f), 1.032f, false)
        assertClosed(map)
        map.update(pose(Viseme.PP, .6f), 1.048f, true)
        assertEquals(1f, weight(map, "PP"), 0f)
        assertEquals(0f, weight(map, "AA"), 0f)
        assertEquals(0f, weight(map, "O"), 0f)
        assertOpening(map, 0f, 0f, "bilabial closure after reduced motion")
    }

    @Test fun clockRollbackReinitializesTheBlendForANewAnimationEpoch() {
        val map = activeBlend()
        map.update(pose(Viseme.O, .6f), .5f, true)
        assertEquals(0f, weight(map, "AA"), 0f)
        assertEquals(1f, weight(map, "O"), 0f)
        assertOpening(map, .6f, O_OPENING, "clock discontinuity")
    }

    private fun activeBlend(): AvatarMorphMapper = mapper().also {
        it.update(pose(Viseme.AA, .6f), 1f, true)
        it.update(pose(Viseme.O, .6f), 1.016f, true)
        assertTrue(weight(it, "AA") > 0f && weight(it, "O") > 0f)
    }

    private fun assertOpening(map: AvatarMorphMapper, measuredJaw: Float, authoredOpening: Float, label: String) {
        val faceOpening = weight(map, "JawOpen") + authoredOpening
        val expected = maxOf(measuredJaw, authoredOpening)
        assertEquals("$label: common face opening", expected, faceOpening, EPSILON)
        assertEquals("$label: inner jaw follows the visible opening", expected * 12f, map.jawPitch, EPSILON * 12f)
        assertTrue("$label: finite, bounded morph weights", map.weights.all { it.isFinite() && it in 0f..1f })
    }

    private fun assertClosed(map: AvatarMorphMapper) {
        assertEquals(0f, weight(map, "JawOpen"), 0f)
        assertEquals(0f, map.jawPitch, 0f)
        for (viseme in Viseme.entries) assertEquals(viseme.name, 0f, weight(map, viseme.name), 0f)
    }

    private fun mapper() = AvatarMorphMapper(AvatarGlbContract.TARGETS)
    private fun weight(map: AvatarMorphMapper, name: String) = map.weights[AvatarGlbContract.TARGETS.indexOf(name)]
    private fun pose(viseme: Viseme, jaw: Float, amount: Float = 1f) =
        AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, 0f, 0f, jaw, viseme, amount, jaw)

    private companion object {
        // Authored CC0 mouth-open coefficients relative to the pinned JawOpen recipe's .8.
        const val AA_OPENING = .65f / .8f
        const val O_OPENING = .36f / .8f
        const val EPSILON = .00001f
    }
}
