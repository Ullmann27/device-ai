package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarSentenceGesture
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.SpeechBoundary
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CinemaAvatarExperimentTest {
    private fun pose(gazeX: Float = .7f, gazeY: Float = -.3f, sentence: SpeechBoundary? = null,
                     progress: Float = -1f, jaw: Float = 0f, viseme: Viseme = Viseme.SIL,
                     directed: Boolean = false, state: AvatarState = AvatarState.LISTENING) =
        AvatarPose(state, AvatarExpression.NEUTRAL, gazeX, gazeY, jaw, viseme,
            if (viseme == Viseme.SIL) 0f else 1f, jaw, gazeDirected = directed,
            sentenceEnd = sentence, sentenceEndProgress = progress)

    @Test fun exp07OnOffChangesNeckShareWithoutMovingTheEyesAlone() {
        val coupled = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val independent = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val looking = pose()
        coupled.update(looking, 1f, true, eyeNeckCouplingEnabled = true)
        independent.update(looking, 1f, true, eyeNeckCouplingEnabled = false)
        assertEquals(coupled.headYaw * AvatarMorphMapper.NECK_SHARE, coupled.neckYaw, 1e-5f)
        assertEquals(0f, independent.neckYaw, 0f)
        assertEquals(0f, independent.neckPitch, 0f)
        assertTrue("head still turns toward the window", abs(independent.headYaw) > 1f)
        assertTrue("eyes still look at the window", abs(independent.eyeYaw) > 1f)
        assertNotEquals(coupled.neckYaw, independent.neckYaw)
    }

    @Test fun exp10OnOffAppliesOrSuppressesTheExistingSentenceNod() {
        val speaking = pose(gazeX = 0f, gazeY = 0f, sentence = SpeechBoundary.STATEMENT,
            progress = .5f, state = AvatarState.SPEAKING)
        val silent = pose(gazeX = 0f, gazeY = 0f, state = AvatarState.SPEAKING)
        val on = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val off = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val none = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        on.update(speaking, 1f, true, sentenceGesturesEnabled = true)
        off.update(speaking, 1f, true, sentenceGesturesEnabled = false)
        none.update(silent, 1f, true, sentenceGesturesEnabled = true)
        assertTrue("statement settle moves the head when EXP10 is on", abs(on.headPitch - none.headPitch) > .2f)
        assertEquals("off matches a pose with no sentence boundary", none.headPitch, off.headPitch, 1e-4f)
        assertNotEquals(on.headPitch, off.headPitch)
    }

    @Test fun sentenceGestureGateRequiresRealProgressAndAnEnabledSwitch() {
        assertEquals(SpeechBoundary.QUESTION,
            AvatarSentenceGesture.published(SpeechBoundary.QUESTION, .4f, true))
        assertNull(AvatarSentenceGesture.published(SpeechBoundary.QUESTION, .4f, false))
        assertNull(AvatarSentenceGesture.published(null, .4f, true))
        assertNull(AvatarSentenceGesture.published(SpeechBoundary.STATEMENT, -1f, true))
        assertNull(AvatarSentenceGesture.published(SpeechBoundary.STATEMENT, Float.NaN, true))
    }

    @Test fun exp09GazeBlinkStaysOffDuringAnOpenSyllable() {
        val blink = AvatarGlbContract.TARGETS.indexOf("BlinkLeft")
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(pose(gazeX = -.6f, directed = true, jaw = .7f, viseme = Viseme.AA,
            state = AvatarState.SPEAKING), 1f, true, gazeBlinkEnabled = true)
        mapper.update(pose(gazeX = .6f, directed = true, jaw = .7f, viseme = Viseme.AA,
            state = AvatarState.SPEAKING), 1.155f, true, gazeBlinkEnabled = true)
        assertEquals(0f, mapper.weights[blink], 0f)
        val idle = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        idle.update(pose(gazeX = -.6f, directed = true), 1f, true, gazeBlinkEnabled = true)
        idle.update(pose(gazeX = .6f, directed = true), 1.155f, true, gazeBlinkEnabled = true)
        assertTrue("a directed jump still blinks when the mouth is closed", idle.weights[blink] > .5f)
    }
}
