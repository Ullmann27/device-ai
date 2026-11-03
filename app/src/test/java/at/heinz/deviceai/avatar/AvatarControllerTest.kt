package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Avatar-Integrationsport (Vorbereitung): Dialog-/TTS-Zustand → AvatarState, Lipsync nur
 * während SPEAKING der aktuellen Äußerung, defekte Avatar-Implementierung bricht nie den Dialog.
 */
class AvatarControllerTest {
    private class Recorder : AvatarPort {
        val states = mutableListOf<AvatarState>(); val levels = mutableListOf<Float>()
        val ranges = mutableListOf<Pair<Int, Int>>()
        override fun onState(state: AvatarState) { states += state }
        override fun onLipSync(utteranceId: String, level: Float, atMillis: Long) { levels += level }
        override fun onSpeechRange(utteranceId: String, start: Int, end: Int) { ranges += start to end }
    }

    @Test fun mapsTheExistingHologramStatesOneToOne() {
        mapOf("LISTENING" to AvatarActivity.LISTENING, "SPEAKING" to AvatarActivity.SPEAKING,
            "RESEARCH" to AvatarActivity.RESEARCHING, "LEARNING" to AvatarActivity.LEARNING,
            "THINKING" to AvatarActivity.THINKING, "READY" to AvatarActivity.IDLE, "unknown" to AvatarActivity.IDLE)
            .forEach { (h, a) -> assertEquals(h, a, AvatarController.activityOf(h)) }
    }

    @Test fun everyHologramStateHasAnAvatarActivity() {
        at.heinz.deviceai.ContinuumHologramView.State.values().forEach { h ->
            val a = AvatarController.activityOf(h.name)
            assertEquals(h.name, h.name == "READY", a == AvatarActivity.IDLE)
        }
    }

    @Test fun forwardsOnlyRealStateChanges() {
        val r = Recorder(); val c = AvatarController(r)
        c.update(AvatarActivity.THINKING, null); c.update(AvatarActivity.THINKING, null)
        c.update(AvatarActivity.SPEAKING, "u1"); c.emotion(AvatarEmotion.HAPPY)
        assertEquals(listOf(AvatarActivity.THINKING, AvatarActivity.SPEAKING, AvatarActivity.SPEAKING),
            r.states.map { it.activity })
        assertEquals(AvatarEmotion.HAPPY, c.state.emotion)
        assertEquals("u1", c.state.utteranceId)
    }

    @Test fun lipSyncAndRangesOnlyWhileSpeakingTheCurrentUtteranceAndClamped() {
        val r = Recorder(); val c = AvatarController(r)
        c.lipSync(0.5f, 1L)                                   // idle: dropped
        c.update(AvatarActivity.SPEAKING, "u1")
        c.lipSync(2f, 2L); c.lipSync(Float.NaN, 3L); c.lipSync(-1f, 4L)
        c.speechRange("u0", 0, 4)                             // stale utterance: dropped
        c.speechRange("u1", 5, 9)
        c.update(AvatarActivity.IDLE, null); c.lipSync(0.7f, 5L)
        assertEquals(listOf(1f, 0f, 0f), r.levels)
        assertEquals(listOf(5 to 9), r.ranges)
    }

    @Test fun aFailingAvatarNeverBreaksTheDialogue() {
        val c = AvatarController(object : AvatarPort {
            override fun onState(state: AvatarState) = error("render crash")
            override fun onLipSync(utteranceId: String, level: Float, atMillis: Long) = error("gl lost")
        })
        c.update(AvatarActivity.SPEAKING, "u1"); c.lipSync(0.3f, 1L)
        assertEquals(AvatarActivity.SPEAKING, c.state.activity)
        assertTrue(NoOpAvatarPort is AvatarPort)
    }
}
