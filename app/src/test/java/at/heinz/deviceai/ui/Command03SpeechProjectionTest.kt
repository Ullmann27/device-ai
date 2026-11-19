package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.*
import org.junit.Assert.*
import org.junit.Test

class Command03SpeechProjectionTest {
    private val session = "11111111-1111-4111-8111-111111111111"
    private var now = 1_000L
    private var current: String? = "speech-1"
    private val packets = mutableListOf<Command03SpeechPacket>()
    private val projection = Command03SpeechProjection { packets += it }
    private val director = AvatarDirector(AvatarSink { projection.accept(session, current, it) }, { now })
    private fun sample() = projection.sample(session, current, now, true)

    @Test fun admittedDirectorWordAndMeasuredPcmProduceTheExistingReducerPose() {
        assertTrue(director.speechStarted("speech-1", "A"))
        director.wordRange("speech-1", 0, 1, 0)
        director.audioLevel("speech-1", .7f, measuredPcmAvailable = true)
        now += 170
        director.audioLevel("speech-1", .7f, measuredPcmAvailable = true)
        sample()
        assertEquals("AA", packets.last().viseme)
        assertTrue(packets.last().jawOpen > 0)
        assertEquals(AudioLevelSource.PCM_ENVELOPE, packets.last().source)
        assertEquals(100L, packets.last().durationMs)
        assertTrue(packets.last().active)
    }

    @Test fun measuredSilenceClosesTheMouthAndFallbackProvenanceRemainsExplicit() {
        director.speechStarted("speech-1", "A")
        director.wordRange("speech-1", 0, 1)
        now += 170
        director.audioLevel("speech-1", 0f, measuredPcmAvailable = false)
        sample()
        assertEquals(AudioLevelSource.WORD_RANGE_FALLBACK, packets.last().source)
        assertTrue(packets.last().jawOpen > 0)
        now++
        director.audioLevel("speech-1", 0f, measuredPcmAvailable = true)
        sample()
        assertEquals("SIL", packets.last().viseme)
        assertEquals(0f, packets.last().jawOpen, 0f)
        assertEquals(AudioLevelSource.PCM_ENVELOPE, packets.last().source)
    }

    @Test fun stopAndOldUtteranceEventsCannotReopenTheMouth() {
        director.speechStarted("speech-1", "A")
        director.audioLevel("speech-1", .7f, true)
        sample()
        director.speechEnded("speech-1", true)
        assertFalse(packets.last().active)
        val count = packets.size
        projection.accept(session, "speech-1", AvatarEvent.SpeechStarted("speech-1", "A", ++now))
        projection.accept(session, "speech-1", AvatarEvent.AudioLevel("speech-1", 1f, ++now))
        sample()
        assertEquals(count, packets.size)
    }

    @Test fun chunkChangeAndReparentUseNewGenerationsWithoutInventingAnUtterance() {
        director.speechStarted("speech-1", "A")
        director.audioLevel("speech-1", .7f, true)
        sample()
        val first = packets.last().generation
        projection.clear()
        assertFalse(packets.last().active)
        director.attach(AvatarSink { projection.accept(session, current, it) })
        now += 33; director.audioLevel("speech-1", .7f, true); sample()
        assertTrue(packets.last().generation > first)
        val reattached = packets.last().generation
        director.speechEnded("speech-1", false)
        current = "speech-2"
        director.speechStarted("speech-2", "O")
        sample()
        assertTrue(packets.last().generation > reattached)
        assertEquals("speech-2", packets.last().utteranceId)
        assertEquals(0f, packets.last().jawOpen, 0f)
    }

    @Test fun noSpeechOrAnotherSessionCannotEmitAFabricatedPose() {
        sample()
        assertTrue(packets.isEmpty())
        director.speechStarted("speech-1", "A")
        director.audioLevel("speech-1", .7f, true)
        sample()
        projection.sample("22222222-2222-4222-8222-222222222222", current, ++now, true)
        assertFalse(packets.last().active)
        val count = packets.size
        projection.sample(session, current, ++now, true)
        assertEquals(count, packets.size)
    }
}
