package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarSpeechAcceptanceTest {
    @Test fun acceptanceDistinguishesNewDuplicateAndRetiredRequests() {
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { 1_000L }

        assertTrue(director.speechStarted("a", "A"))
        val started = events.toList()
        assertFalse(director.speechStarted("a", "Replacement text"))
        assertEquals(started, events)
        director.speechEnded("a", true)
        val stopped = events.toList()
        assertFalse(director.speechStarted("a", "A"))
        assertEquals(stopped, events)
        assertTrue(director.speechStarted("b", "O"))
    }

    @Test fun numericPlaybackRangeIsAcceptedWithoutInventingLetterVisemes() {
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { 1_000L }
        assertTrue(director.speechStarted("a", "A 42 O"))
        assertFalse(director.wordRange("a", 0, 1, audioFrame = -1))
        assertTrue(director.wordRange("a", 0, 1, audioFrame = 100))
        val letterEvents = events.toList()

        assertTrue(director.wordRange("a", 2, 4, audioFrame = 120))
        assertEquals("Numeric audio timing must not fabricate a letter-based cue", letterEvents, events)
        assertFalse(director.wordRange("a", 2, 4, audioFrame = 120))
        assertFalse(director.wordRange("a", 5, 6, audioFrame = 110))
        assertFalse(director.wordRange("other", 5, 6, audioFrame = 200))
        assertEquals(letterEvents, events)
        assertTrue(director.wordRange("a", 5, 6, audioFrame = 200))
        assertEquals(Viseme.O, (events.last() as AvatarEvent.VisemeCue).viseme)
    }
}
