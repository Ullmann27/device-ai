package at.heinz.deviceai.avatar

import org.junit.Assert.*
import org.junit.Test

/**
 * A TTS engine that delivers neither onRangeStart nor PCM (onAudioAvailable) – observed with some vendor engines –
 * used to leave the mouth closed for the whole audible utterance: every tick was PCM_ENVELOPE 0. The Director now
 * paces the spoken text itself after a short grace period, clearly flagged as TEXT_PACE_FALLBACK, and hands over to the
 * real callbacks the moment one arrives.
 */
class AvatarTextPaceFallbackTest {
    private var now = 10_000L
    private val events = mutableListOf<AvatarEvent>()
    private val d = AvatarDirector(AvatarSink(events::add)) { now }

    private fun tick(times: Int, level: Float = 0f) = repeat(times) { now += 33; d.audioLevel("u", level) }
    private val audio get() = events.filterIsInstance<AvatarEvent.AudioLevel>()
    private val cues get() = events.filterIsInstance<AvatarEvent.VisemeCue>()

    @Test fun withinTheGracePeriodNothingIsInvented() {
        d.speechStarted("u", "Hallo Heinz, wie geht es dir heute?")
        tick(7) // 231 ms
        assertTrue(audio.all { it.source == AudioLevelSource.PCM_ENVELOPE && it.level == 0f })
        assertTrue(cues.isEmpty())
    }

    @Test fun silentEngineGetsTextPacedMouthMotionVisemesAndSentenceEnds() {
        val text = "Hallo Heinz. Wie geht es dir heute?"
        d.speechStarted("u", text)
        tick(80) // ~2.6 s: all 7 words (250 ms grace + 6 × 360 ms) have started
        val paced = audio.filter { it.source == AudioLevelSource.TEXT_PACE_FALLBACK }
        assertTrue("paced levels: ${paced.size}", paced.size >= 40)
        assertTrue(paced.all { it.level in 0.10f..0.28f })
        assertTrue("no PCM level after pacing began", audio.dropWhile { it.source == AudioLevelSource.PCM_ENVELOPE }
            .none { it.source == AudioLevelSource.PCM_ENVELOPE })
        assertTrue("visemes: ${cues.size}", cues.size >= 7)
        assertTrue(cues.all { it.utteranceId == "u" && it.durationMs >= 40 })
        val ends = events.filterIsInstance<AvatarEvent.SentenceEnd>()
        assertEquals(listOf(SpeechBoundary.STATEMENT, SpeechBoundary.QUESTION), ends.map { it.boundary })
        // Cue times advance monotonically word by word.
        val firstCueTimes = cues.filter { it.viseme == Viseme.SIL }.map { it.atMillis }
        assertEquals(firstCueTimes, firstCueTimes.sorted())
    }

    @Test fun afterTheEstimatedDurationTheMouthClosesHonestly() {
        d.speechStarted("u", "Ja.")
        tick(90) // ~3 s for one word
        val last = audio.takeLast(10)
        assertTrue(last.all { it.source == AudioLevelSource.PCM_ENVELOPE && it.level == 0f })
    }

    @Test fun aRealWordRangeTakesOverFromThePaceAndIsNotRejectedAsStale() {
        d.speechStarted("u", "Hallo Heinz, wie geht es dir heute?")
        tick(30) // pacing has advanced past the first words
        assertTrue(audio.any { it.source == AudioLevelSource.TEXT_PACE_FALLBACK })
        // The engine wakes up with the real first word – behind the paced cursor.
        assertTrue(d.wordRange("u", 0, 5, 10))
        events.clear()
        tick(3)
        assertTrue(audio.isNotEmpty())
        assertTrue(audio.all { it.source == AudioLevelSource.WORD_RANGE_FALLBACK })
        assertTrue(cues.isEmpty()) // no more paced cues once the engine reports ranges
        // Normal ordering rules apply again from the real range on.
        assertFalse(d.wordRange("u", 0, 5, 10))
        assertTrue(d.wordRange("u", 6, 12, 20))
    }

    @Test fun measuredPcmAlwaysWinsOverThePace() {
        d.speechStarted("u", "Hallo Heinz, wie geht es dir heute?")
        now += 400
        d.audioLevel("u", 0f, measuredPcmAvailable = true)
        d.audioLevel("u", 0.5f, measuredPcmAvailable = true)
        assertTrue(audio.all { it.source == AudioLevelSource.PCM_ENVELOPE })
        assertTrue(cues.isEmpty())
    }

    @Test fun pacingStopsAtSpeechEndAndNeverLeaksIntoTheNextUtterance() {
        d.speechStarted("u", "Hallo Heinz, wie geht es dir heute?")
        tick(20)
        d.speechEnded("u", interrupted = true)
        events.clear()
        tick(5)
        assertTrue(events.isEmpty())
        d.speechStarted("v", "Ja.")
        now += 33; d.audioLevel("v", 0f)
        assertTrue(cues.isEmpty())
        assertEquals(AudioLevelSource.PCM_ENVELOPE, audio.single().source)
    }
}
