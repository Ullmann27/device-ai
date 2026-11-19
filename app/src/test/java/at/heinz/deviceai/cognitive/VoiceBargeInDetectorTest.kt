package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.VoiceBargeInDetector.Decision
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Voice barge-in while the assistant speaks ("Stopp, erklär nur den zweiten.").
 *
 * All signals are synthetic level sequences: a syllable-shaped playback envelope, an echo that
 * is a delayed, scaled copy of that envelope, and stationary noise with frame-to-frame jitter.
 * They prove the logic of the detector. They do not prove that a real loudspeaker, room and
 * echo canceller behave like this model; that needs a measurement on the device.
 */
class VoiceBargeInDetectorTest {
    private class Sim(val detector: VoiceBargeInDetector = VoiceBargeInDetector()) {
        var nowMs = 0
        var triggers = 0

        /** Feeds [durationMs] of frames; returns the clock at the end of the triggering frame. */
        fun feed(durationMs: Int, frameMs: Int = FRAME_MS, playback: (Int) -> Float = SILENT,
                 mic: (Int) -> Float): Int? {
            var triggeredAt: Int? = null
            val end = nowMs + durationMs
            while (nowMs < end) {
                val decision = detector.onFrame(mic(nowMs), playback(nowMs), frameMs)
                nowMs += frameMs
                if (decision == Decision.BARGE_IN) {
                    triggers++
                    if (triggeredAt == null) triggeredAt = nowMs
                }
            }
            return triggeredAt
        }
    }

    private fun armed() = Sim().also { it.detector.arm() }

    // ---- silence and stationary noise -------------------------------------------------------

    @Test fun silenceNeverTriggers() {
        assertNull(armed().feed(30_000) { 0f })
        assertNull(armed().feed(30_000) { QUIET_ROOM })
        assertNull(armed().feed(30_000) { noise(it, QUIET_ROOM) })
    }

    @Test fun steadyFanAndCarCabinNoiseNeverTrigger() {
        assertNull(armed().feed(30_000) { 0.03f })
        assertNull(armed().feed(60_000) { noise(it, CAR_CABIN) })
        assertNull(armed().feed(60_000) { noise(it, 0.15f) })
        val car = armed()
        assertNull(car.feed(60_000, playback = { tts(it - 200, 0.3f) }) {
            mix(noise(it, CAR_CABIN), 0.15f * tts(it - 260, 0.3f))
        })
        // The floor follows the lower envelope of the fluctuating noise, not its peaks.
        assertTrue(car.detector.noiseFloor in CAR_CABIN * 0.6f..CAR_CABIN * 1.2f)
    }

    // ---- the assistant's own voice ----------------------------------------------------------

    @Test fun assistantEchoAloneNeverTriggersAtAnyPlaybackLevel() {
        for (peak in floatArrayOf(0.05f, 0.15f, 0.3f, 0.6f, 1f)) {
            for (coupling in floatArrayOf(0.05f, 0.15f, 0.4f, 0.9f)) {
                for (delayMs in intArrayOf(0, 40, 80)) {
                    for (phaseMs in intArrayOf(0, 190, 380, 570, 760)) {
                        val sim = armed()
                        val triggered = sim.feed(15_000, playback = { tts(it + phaseMs, peak) }) {
                            mix(QUIET_ROOM, coupling * tts(it + phaseMs - delayMs, peak))
                        }
                        assertNull("peak=$peak coupling=$coupling delay=$delayMs phase=$phaseMs", triggered)
                    }
                }
            }
        }
    }

    @Test fun echoStaysExplainedWhenThePlaybackGetsLouderAfterLearning() {
        for (coupling in floatArrayOf(0.1f, 0.3f)) {
            val sim = armed()
            // Soft first two seconds (learning happens here), then more than three times louder.
            val level = { t: Int -> tts(t - 100, if (t < 2_000) 0.15f else 0.5f) }
            assertNull(sim.feed(12_000, playback = level) { mix(QUIET_ROOM, coupling * level(it - 60)) })
            assertTrue(sim.detector.echoCouplingLearned)
            assertTrue(sim.detector.echoCoupling >= coupling)
        }
    }

    @Test fun echoCouplingIsLearnedPerPlaybackAndClamped() {
        val detector = VoiceBargeInDetector()
        assertEquals(VoiceBargeInDetector.ECHO_COUPLING_MAX, detector.echoCoupling, 0f)
        assertFalse(detector.echoCouplingLearned)

        val measured = Sim(detector).also { detector.arm() }
        measured.feed(3_000, playback = { tts(it, 0.3f) }) { mix(QUIET_ROOM, 0.15f * tts(it - 40, 0.3f)) }
        assertTrue(detector.echoCouplingLearned)
        assertTrue("coupling=${detector.echoCoupling}", detector.echoCoupling in 0.15f..0.2f)

        // A new playback measures again and assumes the worst until then.
        detector.arm()
        assertFalse(detector.echoCouplingLearned)
        assertEquals(VoiceBargeInDetector.ECHO_COUPLING_MAX, detector.echoCoupling, 0f)

        // Headphones: no echo at all still leaves the minimum protection.
        measured.feed(3_000, playback = { tts(it, 0.3f) }) { QUIET_ROOM }
        assertEquals(VoiceBargeInDetector.ECHO_COUPLING_MIN, detector.echoCoupling, 0f)

        // An implausibly strong coupling is clamped.
        detector.arm()
        measured.feed(3_000, playback = { tts(it, 0.2f) }) { (3f * tts(it, 0.2f)).coerceAtMost(1f) }
        assertEquals(VoiceBargeInDetector.ECHO_COUPLING_MAX, detector.echoCoupling, 0f)

        // Without any audible playback nothing is measured.
        detector.arm()
        measured.feed(3_000) { QUIET_ROOM }
        assertFalse(detector.echoCouplingLearned)
    }

    @Test fun speechMustBeClearlyLouderThanTheEchoOfTheCurrentPlaybackLevel() {
        val sim = armed()
        sim.feed(300) { QUIET_ROOM } // the synthesis has not produced audio yet
        // Loud, badly cancelled playback: echo 0.2 at the microphone.
        val playback = { _: Int -> 0.5f }
        sim.feed(520, playback = playback) { 0.2f }
        assertTrue(sim.detector.echoCouplingLearned)
        assertEquals(0.4f, sim.detector.echoCoupling, 0.001f)
        // Normal speech on top of that echo stays below playback level x coupling x margin = 0.4.
        val withSpeech = mix(0.2f, 0.25f)
        assertNull(sim.feed(300, playback = playback) { withSpeech })
        // It was the echo threshold that held it back, not the (slowly rising) noise floor.
        assertTrue(withSpeech > sim.detector.noiseFloor * VoiceBargeInDetector.NOISE_MARGIN_RATIO)
        assertTrue(withSpeech < 0.5f * sim.detector.echoCoupling * VoiceBargeInDetector.ECHO_MARGIN_RATIO)
        // A raised voice exceeds it.
        val start = sim.nowMs
        assertEquals(start + 240, sim.feed(3_000, playback = playback) { mix(0.2f, 0.5f) })
    }

    // ---- real speech ------------------------------------------------------------------------

    @Test fun speechOverPlaybackTriggersWithin240To400Ms() {
        for (peak in floatArrayOf(0.1f, 0.3f)) {
            for (coupling in floatArrayOf(0.05f, 0.15f)) {
                for (onsetMs in intArrayOf(3_000, 3_100, 3_260, 4_440)) {
                    val sim = armed()
                    val triggeredAt = sim.feed(8_000, playback = { tts(it - 200, peak) }) {
                        mix(QUIET_ROOM, coupling * tts(it - 260, peak), speech(it, onsetMs, 0.25f))
                    }
                    val label = "peak=$peak coupling=$coupling onset=$onsetMs at=$triggeredAt"
                    assertNotNull(label, triggeredAt)
                    assertTrue(label, triggeredAt!! - onsetMs in 240..400)
                    assertEquals(label, 1, sim.triggers)
                }
            }
        }
    }

    @Test fun speechInAPauseOfThePlaybackTriggersAfterExactly240Ms() {
        val sim = armed()
        val triggeredAt = sim.feed(5_000) { if (it < 2_000) QUIET_ROOM else 0.1f }
        assertEquals(2_240, triggeredAt)
    }

    @Test fun inALoudCabinOnlyAClearlyRaisedVoiceTriggers() {
        val playback = { t: Int -> tts(t - 200, 0.3f) }
        val cabin = { t: Int -> mix(noise(t, CAR_CABIN), 0.15f * tts(t - 260, 0.3f)) }
        // A voice at the level of the cabin noise is not separable by level alone.
        assertNull(armed().feed(10_000, playback = playback) { mix(cabin(it), speech(it, 4_000, 0.06f)) })
        val raised = armed()
        val triggeredAt = raised.feed(10_000, playback = playback) { mix(cabin(it), speech(it, 4_000, 0.4f)) }
        assertNotNull(triggeredAt)
        assertTrue("at=$triggeredAt", triggeredAt!! - 4_000 in 240..400)
    }

    // ---- short events -----------------------------------------------------------------------

    @Test fun coughDoorSlamAndSingleSyllableDoNotTrigger() {
        val cough = armed()
        assertNull(cough.feed(5_000) { if (it in 2_000 until 2_160) 0.5f else QUIET_ROOM })
        val slam = armed()
        assertNull(slam.feed(5_000) { if (it in 2_000 until 2_040) 0.95f else QUIET_ROOM })
        val syllable = armed()
        assertNull(syllable.feed(5_000) { if (it in 2_000 until 2_220) 0.2f else QUIET_ROOM })
        // Repeated coughs with real gaps between them never add up.
        val series = armed()
        assertNull(series.feed(20_000) { if (it >= 2_000 && it % 260 < 160) 0.5f else QUIET_ROOM })
        // The same events over a running playback.
        val over = armed()
        assertNull(over.feed(8_000, playback = { tts(it, 0.3f) }) {
            mix(QUIET_ROOM, 0.15f * tts(it - 40, 0.3f), if (it in 3_000 until 3_160) 0.5f else 0f)
        })
    }

    @Test fun dropoutsOfUpTo60MsDoNotResetTheCount() {
        // 100 ms sound, 60 ms gap, repeated: 240 ms of sound are reached after 360 ms.
        val tolerated = armed()
        assertEquals(1_360, tolerated.feed(4_000) { burst(it, 1_000, onMs = 100, offMs = 60) })
        // 200 ms, a 60 ms gap, then 40 ms more.
        val word = armed()
        assertEquals(1_300, word.feed(4_000) {
            if (it in 1_000 until 1_200 || it in 1_260 until 1_300) 0.2f else QUIET_ROOM
        })
        // An 80 ms gap ends the candidate, so 100 ms pieces never reach 240 ms.
        val reset = armed()
        assertNull(reset.feed(20_000) { burst(it, 1_000, onMs = 100, offMs = 80) })
        // 200 ms, an 80 ms gap and 200 ms more are two separate short events.
        val twoEvents = armed()
        assertNull(twoEvents.feed(4_000) {
            if (it in 1_000 until 1_200 || it in 1_280 until 1_480) 0.2f else QUIET_ROOM
        })
    }

    @Test fun sparseFragmentsInsideTheWindowNeverAddUp() {
        // 40 ms sound per 100 ms: at most 160 ms of sound inside any 400 ms window.
        assertNull(armed().feed(20_000) { burst(it, 1_000, onMs = 40, offMs = 60) })
        // 220 ms and then silence is still short of 240 ms.
        assertNull(armed().feed(4_000) { if (it in 1_000 until 1_220) 0.2f else QUIET_ROOM })
    }

    // ---- guard interval, arming -------------------------------------------------------------

    @Test fun nothingTriggersInsideTheGuardInterval() {
        for (onsetMs in intArrayOf(0, 20, 40, 100)) {
            for (level in floatArrayOf(0.1f, 0.5f, 1f)) {
                val fresh = armed()
                assertNull(fresh.feed(VoiceBargeInDetector.GUARD_INTERVAL_MS) { if (it >= onsetMs) level else 0f })
                // Also with a quiet noise floor already learned in an earlier period.
                val primed = armed()
                primed.feed(3_000) { QUIET_ROOM }
                primed.detector.arm()
                val start = primed.nowMs
                assertNull(primed.feed(VoiceBargeInDetector.GUARD_INTERVAL_MS) {
                    if (it - start >= onsetMs) level else QUIET_ROOM
                })
            }
        }
    }

    @Test fun speechRightAfterTheGuardIntervalTriggers() {
        val sim = armed()
        val triggeredAt = sim.feed(3_000) { if (it >= 300) 0.2f else QUIET_ROOM }
        assertEquals(540, triggeredAt)
    }

    @Test fun triggersExactlyOncePerArmedPeriodAndDisarmsItself() {
        val sim = armed()
        val triggeredAt = sim.feed(10_000) { speech(it, 1_000, 0.25f).coerceAtLeast(QUIET_ROOM) }
        assertNotNull(triggeredAt)
        assertEquals(1, sim.triggers)
        assertFalse(sim.detector.armed)
        assertEquals(Decision.NONE, sim.detector.onFrame(0.9f, 0f, FRAME_MS))
    }

    @Test fun reArmStartsANewPeriodThatCanTriggerAgain() {
        val sim = armed()
        assertNotNull(sim.feed(2_000) { if (it >= 1_000) 0.2f else QUIET_ROOM })
        sim.detector.arm()
        assertTrue(sim.detector.armed)
        val start = sim.nowMs
        val again = sim.feed(3_000) { if (it - start >= 1_000) 0.2f else QUIET_ROOM }
        assertEquals(start + 1_240, again)
        assertEquals(2, sim.triggers)
        sim.detector.arm()
        val third = sim.nowMs
        assertEquals(third + 1_240, sim.feed(3_000) { if (it - third >= 1_000) 0.2f else QUIET_ROOM })
        assertEquals(3, sim.triggers)
    }

    @Test fun disarmedDetectorIgnoresFramesAndKeepsWhatItLearned() {
        val never = Sim()
        assertNull(never.feed(5_000) { 0.5f })
        assertEquals(0f, never.detector.noiseFloor, 0f)

        val sim = armed()
        sim.feed(1_000) { CAR_CABIN }
        val floor = sim.detector.noiseFloor
        assertEquals(CAR_CABIN, floor, 0.001f)
        sim.feed(200) { 0.5f } // 200 ms of a candidate, then the playback ends.
        sim.detector.disarm()
        assertFalse(sim.detector.armed)
        assertNull(sim.feed(5_000) { 0.5f })
        assertEquals(floor, sim.detector.noiseFloor, 0f)
        // The interrupted candidate is not carried into the next period.
        sim.detector.arm()
        val start = sim.nowMs
        assertNull(sim.feed(300) { CAR_CABIN })
        assertEquals(start + 300 + 240, sim.feed(2_000) { 0.5f })
    }

    @Test fun resetForgetsFloorAndCoupling() {
        val sim = armed()
        sim.feed(3_000, playback = { tts(it, 0.3f) }) { mix(CAR_CABIN, 0.15f * tts(it, 0.3f)) }
        assertTrue(sim.detector.noiseFloor > 0f)
        assertTrue(sim.detector.echoCouplingLearned)
        sim.detector.reset()
        assertFalse(sim.detector.armed)
        assertEquals(0f, sim.detector.noiseFloor, 0f)
        assertFalse(sim.detector.echoCouplingLearned)
        assertEquals(VoiceBargeInDetector.ECHO_COUPLING_MAX, sim.detector.echoCoupling, 0f)
        assertNull(sim.feed(2_000) { 0.5f })
    }

    // ---- input validation -------------------------------------------------------------------

    @Test fun invalidFramesAreIgnoredAndChangeNothing() {
        val invalid = listOf(
            Triple(Float.NaN, 0f, FRAME_MS), Triple(-0.01f, 0f, FRAME_MS), Triple(1.01f, 0f, FRAME_MS),
            Triple(Float.POSITIVE_INFINITY, 0f, FRAME_MS), Triple(Float.NEGATIVE_INFINITY, 0f, FRAME_MS),
            Triple(0.5f, Float.NaN, FRAME_MS), Triple(0.5f, -0.01f, FRAME_MS), Triple(0.5f, 1.01f, FRAME_MS),
            Triple(0.5f, Float.POSITIVE_INFINITY, FRAME_MS),
            Triple(0.5f, 0f, 0), Triple(0.5f, 0f, -20), Triple(0.5f, 0f, Int.MIN_VALUE),
            Triple(0.5f, 0f, VoiceBargeInDetector.MAX_FRAME_MS + 1), Triple(0.5f, 0f, Int.MAX_VALUE)
        )
        // Only invalid frames: nothing happens, however many arrive.
        val only = armed()
        only.feed(1_000) { QUIET_ROOM }
        val floor = only.detector.noiseFloor
        repeat(1_000) {
            for ((rms, playback, frameMs) in invalid) {
                assertEquals(Decision.NONE, only.detector.onFrame(rms, playback, frameMs))
            }
        }
        assertEquals(floor, only.detector.noiseFloor, 0f)
        assertTrue(only.detector.armed)

        // Invalid frames between valid ones do not move the trigger.
        val mixed = armed()
        var triggeredAfterValidMs: Int? = null
        var validMs = 0
        while (validMs < 3_000 && triggeredAfterValidMs == null) {
            for ((rms, playback, frameMs) in invalid) {
                assertEquals(Decision.NONE, mixed.detector.onFrame(rms, playback, frameMs))
            }
            val level = if (validMs >= 1_000) 0.2f else QUIET_ROOM
            val decision = mixed.detector.onFrame(level, 0f, FRAME_MS)
            validMs += FRAME_MS
            if (decision == Decision.BARGE_IN) triggeredAfterValidMs = validMs
        }
        assertEquals(1_240, triggeredAfterValidMs)

        // The boundary values are valid.
        val edges = armed()
        assertNull(edges.feed(300) { 0f })
        assertNull(edges.feed(220, playback = { 0f }) { 1f })
        assertEquals(edges.nowMs + 20, edges.feed(20, playback = { 0f }) { 1f })
    }

    @Test fun sustainedDurationDoesNotDependOnTheFrameSize() {
        for (frameMs in intArrayOf(1, 10, 20, 30, 40)) {
            val sim = armed()
            val triggeredAt = sim.feed(3_000, frameMs = frameMs) { if (it >= 1_200) 0.2f else QUIET_ROOM }
            assertEquals("frameMs=$frameMs", 1_440, triggeredAt)
        }
        // Longest accepted frame: three frames of 100 ms are the first to reach 240 ms.
        val coarse = armed()
        assertEquals(1_300, coarse.feed(3_000, frameMs = 100) { if (it >= 1_000) 0.2f else QUIET_ROOM })
    }

    // ---- changing environment ---------------------------------------------------------------

    @Test fun graduallyLouderEnvironmentIsFollowedWithoutTriggering() {
        val sim = armed()
        // Accelerating car: the cabin noise quadruples within eight seconds.
        val ramp = { t: Int -> noise(t, 0.02f + 0.06f * ((t - 2_000).coerceIn(0, 8_000) / 8_000f)) }
        assertNull(sim.feed(20_000) { ramp(it) })
        assertTrue("floor=${sim.detector.noiseFloor}", sim.detector.noiseFloor in 0.045f..0.1f)
        // A voice that would have triggered in the quiet car no longer does, a raised one does.
        assertNull(sim.feed(3_000) { mix(noise(it, 0.08f), 0.06f) })
        val start = sim.nowMs
        assertEquals(start + 240, sim.feed(3_000) { mix(noise(it, 0.08f), 0.4f) })
    }

    @Test fun louderEnvironmentAtTheNextPlaybackDoesNotTriggerFromAStaleFloor() {
        val sim = armed()
        assertNull(sim.feed(5_000) { noise(it, QUIET_ROOM) })
        assertTrue(sim.detector.noiseFloor < 0.005f)
        sim.detector.disarm()
        // The next playback starts in a much louder place; the floor is stale and far too low.
        sim.detector.arm()
        assertNull(sim.feed(20_000) { noise(it, 0.12f) })
        assertTrue("floor=${sim.detector.noiseFloor}", sim.detector.noiseFloor > 0.07f)
    }

    @Test fun quieterEnvironmentDoesNotTriggerAndRestoresSensitivity() {
        val sim = armed()
        assertNull(sim.feed(5_000) { noise(it, 0.12f) })
        assertNull(sim.feed(1_000) { mix(noise(it, 0.12f), 0.08f) }) // not separable here
        assertNull(sim.feed(3_000) { noise(it, QUIET_ROOM) })
        assertTrue("floor=${sim.detector.noiseFloor}", sim.detector.noiseFloor < 0.01f)
        val start = sim.nowMs
        assertEquals(start + 240, sim.feed(2_000) { 0.08f })
    }

    /**
     * Documents the limit of a level-only detector instead of hiding it: a loud, sustained sound
     * that starts abruptly in the middle of a playback is not distinguishable from speech.
     */
    @Test fun abruptSustainedLoudSoundInsideAPlaybackIsIndistinguishableFromSpeech() {
        val sim = armed()
        val triggeredAt = sim.feed(10_000) { noise(it, if (it < 3_000) QUIET_ROOM else 0.12f) }
        assertNotNull(triggeredAt)
        assertTrue("at=$triggeredAt", triggeredAt!! - 3_000 in 240..400)
    }

    private companion object {
        const val FRAME_MS = 20
        const val QUIET_ROOM = 0.003f
        const val CAR_CABIN = 0.05f
        val SILENT: (Int) -> Float = { 0f }

        /** Levels of uncorrelated sources add in power. */
        fun mix(vararg levels: Float): Float {
            var power = 0f
            for (level in levels) power += level * level
            return sqrt(power).coerceAtMost(1f)
        }

        /** Deterministic stationary noise with +-30 % frame-to-frame variation of its level. */
        fun noise(tMs: Int, level: Float): Float {
            var x = (tMs / FRAME_MS) * -1640531535
            x = x xor (x ushr 15)
            x *= -2048144789
            x = x xor (x ushr 13)
            return level * (1f + 0.3f * ((x and 0xFFFF) / 32767.5f - 1f))
        }

        private val stress = floatArrayOf(1f, 0.6f, 0.85f, 0.5f)

        /**
         * Level envelope of synthesised speech: four syllables of 190 ms with different stress
         * (40 ms attack, 90 ms sustain, 60 ms decay) followed by a 140 ms pause.
         */
        fun tts(tMs: Int, peak: Float): Float {
            if (tMs < 0) return 0f
            val inPhrase = tMs % 900
            if (inPhrase >= 760) return 0f
            val inSyllable = inPhrase % 190
            val shape = when {
                inSyllable < 40 -> inSyllable / 40f
                inSyllable < 130 -> 1f
                else -> (190 - inSyllable) / 60f
            }
            return peak * stress[inPhrase / 190] * shape
        }

        // One value per 20 ms: a soft onset, two syllables and a 40 ms dropout between words.
        private val owner = floatArrayOf(
            0.35f, 0.7f, 1f, 0.95f, 0.8f, 0.6f, 0.9f, 1f, 0.85f, 0.15f, 0.1f, 0.75f, 1f, 0.9f, 0.7f, 0.4f)

        /** The owner's voice from [onsetMs] on, 0 before. */
        fun speech(tMs: Int, onsetMs: Int, level: Float): Float =
            if (tMs < onsetMs) 0f else level * owner[((tMs - onsetMs) / FRAME_MS) % owner.size]

        /** Rectangular bursts of 0.2 from [onsetMs] on, quiet room otherwise. */
        fun burst(tMs: Int, onsetMs: Int, onMs: Int, offMs: Int): Float =
            if (tMs >= onsetMs && (tMs - onsetMs) % (onMs + offMs) < onMs) 0.2f else QUIET_ROOM
    }
}
