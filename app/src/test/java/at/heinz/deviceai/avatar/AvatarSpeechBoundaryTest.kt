package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarSpeechBoundaryTest {
    @Test fun repeatedStartDoesNotResetTheActiveUtterance() {
        var now = 1_000L
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { now }
        director.speechStarted("a", "Hallo Heinz")
        director.wordRange("a", 0, 5)
        val beforeRepeat = events.toList()

        now += 10
        director.speechStarted("a", "Hallo Heinz")

        assertEquals(beforeRepeat, events)
    }

    @Test fun stoppedUtterancesCannotRestartAfterAnotherUtteranceStops() {
        var now = 1_000L
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { now }
        for (id in listOf("a", "b")) {
            director.speechStarted(id, "Hallo")
            director.speechEnded(id, true)
            now += 10
        }
        val beforeLateStart = events.toList()

        director.speechStarted("a", "Hallo")
        director.wordRange("a", 0, 5)
        director.audioLevel("a", 1f)

        assertEquals(beforeLateStart, events)
    }

    @Test fun supersededUtteranceCannotStealANewerUtterance() {
        var now = 1_000L
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { now }
        director.speechStarted("a", "Hallo")
        now += 10
        director.speechStarted("b", "Weiter")
        val beforeLateStart = events.size

        now += 10
        director.speechStarted("a", "Hallo")
        director.audioLevel("b", .5f)

        assertEquals(beforeLateStart + 1, events.size)
        assertTrue("The current utterance must still deliver audio", events.last() is AvatarEvent.AudioLevel)
        assertEquals("b", (events.last() as AvatarEvent.AudioLevel).utteranceId)
    }

    @Test fun duplicateAndBackwardTextRangesDoNotReplaceTheCurrentWord() {
        var now = 1_000L
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { now }
        director.speechStarted("a", "Hallo Heinz")
        director.wordRange("a", 6, 11)
        val beforeReplay = events.toList()

        now += 10
        director.wordRange("a", 6, 11)
        now += 10
        director.wordRange("a", 0, 5)

        assertEquals(beforeReplay, events)
    }

    @Test fun aNewUtteranceHasIndependentWordOrderingAndIgnoresTheOldStop() {
        var now = 1_000L
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { now }
        director.speechStarted("a", "Hallo Heinz")
        director.wordRange("a", 6, 11)
        now += 10
        director.speechStarted("b", "Mama")
        director.speechEnded("a", true)
        val beforeCurrentRange = events.size

        director.wordRange("b", 0, 4)

        val currentCues = events.drop(beforeCurrentRange).filterIsInstance<AvatarEvent.VisemeCue>()
        assertTrue(currentCues.isNotEmpty())
        assertTrue(currentCues.all { it.utteranceId == "b" })
        assertEquals(listOf(Viseme.SIL, Viseme.PP, Viseme.AA, Viseme.PP, Viseme.AA),
            currentCues.map { it.viseme })
    }

    @Test fun aBackwardAudioFrameCannotReplaceAWordEvenWhenTheTextRangeIsNewer() {
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { 1_000L }
        director.speechStarted("a", "Hallo Heinz")
        director.wordRange("a", 0, 5, audioFrame = 100)
        val beforeBackwardFrame = events.toList()

        director.wordRange("a", 6, 11, audioFrame = 90)

        assertEquals(beforeBackwardFrame, events)
        director.wordRange("a", 6, 11, audioFrame = 200)
        assertTrue(events.size > beforeBackwardFrame.size)
    }

    @Test fun distinctOrderedTextRangesMayShareAnAudioFrameWithoutReplayingDuplicates() {
        val events = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(events::add)) { 1_000L }
        director.speechStarted("a", "Hallo Heinz")
        director.wordRange("a", 0, 5, audioFrame = 0)
        val beforeNextRange = events.size

        director.wordRange("a", 6, 11, audioFrame = 0)

        assertTrue("A distinct text range at a tied audio mark is still ordered", events.size > beforeNextRange)
        val afterNextRange = events.toList()
        director.wordRange("a", 6, 11, audioFrame = 0)
        assertEquals(afterNextRange, events)
    }
}
