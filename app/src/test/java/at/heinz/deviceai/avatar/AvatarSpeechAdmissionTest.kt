package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarSpeechAdmissionTest {
    @Test fun acceptedStartResultRejectsDuplicateSupersededAndEndedIds() {
        val director = AvatarDirector(clock = { 1_000L })
        assertFalse(director.speechStarted(" ", "A"))
        assertTrue(director.speechStarted("old", "A"))
        assertFalse(director.speechStarted("old", "O"))
        assertTrue(director.speechStarted("current", "O"))
        assertFalse(director.speechStarted("old", "A"))
        director.speechEnded("current", false)
        assertFalse(director.speechStarted("current", "O"))
    }

    @Test fun acceptedRangeResultSharesTextAndFrameOrderingWithTheCaller() {
        val director = AvatarDirector(clock = { 1_000L })
        director.speechStarted("u", "A O I")
        assertFalse(director.wordRange("other", 0, 1, 0))
        assertFalse(director.wordRange("u", -1, 1, 0))
        assertFalse(director.wordRange("u", 0, 1, -1))
        assertTrue(director.wordRange("u", 0, 1, 100))
        assertFalse(director.wordRange("u", 0, 1, 100))
        assertFalse(director.wordRange("u", 2, 3, 99))
        assertTrue(director.wordRange("u", 2, 3, 100))
        assertTrue(director.wordRange("u", 4, 5, 200))
        director.speechEnded("u", true)
        assertFalse(director.wordRange("u", 4, 5, 200))
    }

    @Test fun punctuationAdvancesAdmissionWithoutInventingSpeechMotion() {
        var now = 1_000L
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { now }
        director.speechStarted("u", "!!! A")
        assertTrue(director.wordRange("u", 0, 3, 100))
        assertTrue(events.none { it is AvatarEvent.VisemeCue })
        assertFalse(director.wordRange("u", 0, 3, 100))
        assertFalse(director.wordRange("u", 4, 5, 99))
        now += 10
        director.audioLevel("u", 0f)
        val audio = events.last() as AvatarEvent.AudioLevel
        assertEquals(0f, audio.level, 0f)
        assertEquals(AudioLevelSource.PCM_ENVELOPE, audio.source)
        assertTrue(director.wordRange("u", 4, 5, 100))
    }

    @Test fun punctuationCannotExtendThePreviousWordsFallbackOrReattachIt() {
        var now = 1_000L
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { now }
        director.speechStarted("u", "A !!!")
        assertTrue(director.wordRange("u", 0, 1, 100))
        now = 1_100L
        assertTrue(director.wordRange("u", 2, 5, 200))
        val attached = mutableListOf<AvatarEvent>()
        director.attach(AvatarSink(attached::add))
        assertTrue(attached.none { it is AvatarEvent.VisemeCue })
        now = 1_500L
        director.audioLevel("u", 0f)
        val audio = attached.last() as AvatarEvent.AudioLevel
        assertEquals(0f, audio.level, 0f)
        assertEquals(AudioLevelSource.PCM_ENVELOPE, audio.source)
    }

    @Test fun sinkFailureDoesNotRejectAnOtherwiseAcceptedTtsCallback() {
        val director = AvatarDirector(AvatarSink { error("synthetic rendering failure") }) { 1_000L }
        assertTrue(director.speechStarted("u", "A O"))
        assertTrue(director.wordRange("u", 0, 1, 0))
        assertFalse(director.wordRange("u", 0, 1, 0))
        assertTrue(director.wordRange("u", 2, 3, 1))
    }
}
