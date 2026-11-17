package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarSpeechReattachmentTest {
    @Test fun attachRestoresOnlyLiveCuesWithTheirOriginalDeadlines() {
        var now = 1_000L
        val original = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink(original::add)) { now }
        director.speechStarted("u", "A")
        director.wordRange("u", 0, 1, 0)
        val word = original.filterIsInstance<AvatarEvent.VisemeCue>()
        now = 1_170L
        val attached = mutableListOf<AvatarEvent>()
        val reducer = AvatarPoseReducer()
        director.attach(AvatarSink { attached += it; reducer.apply(it) })

        assertEquals(word.filter { it.atMillis + it.durationMs > now },
            attached.filterIsInstance<AvatarEvent.VisemeCue>())
        assertEquals(Viseme.AA, reducer.pose(now).viseme)
        assertEquals(Viseme.SIL, reducer.pose(1_320L).viseme)
        val beforeReplay = attached.toList()
        director.wordRange("u", 0, 1, 0)
        assertEquals(beforeReplay, attached)
    }

    @Test fun attachRestoresUpcomingCuesWithoutPlayingThemEarly() {
        var now = 1_000L
        val director = AvatarDirector(clock = { now })
        director.speechStarted("u", "A")
        director.wordRange("u", 0, 1, 0)
        now = 1_050L
        val reducer = AvatarPoseReducer()
        director.attach(AvatarSink(reducer::apply))

        assertEquals(Viseme.SIL, reducer.pose(now).viseme)
        assertEquals(Viseme.AA, reducer.pose(1_170L).viseme)
        assertEquals(Viseme.SIL, reducer.pose(1_320L).viseme)
    }

    @Test fun repeatedAttachCannotExtendTheWordOrMultiplyQueuedCues() {
        var now = 1_000L
        val director = AvatarDirector(clock = { now })
        director.speechStarted("u", "A")
        director.wordRange("u", 0, 1, 0)
        now = 1_170L
        val reducer = AvatarPoseReducer()
        val sink = AvatarSink(reducer::apply)
        repeat(256) { director.attach(sink) }

        assertEquals(Viseme.AA, reducer.pose(now).viseme)
        val queue = AvatarPoseReducer::class.java.getDeclaredField("cues").apply { isAccessible = true }
        assertEquals("Reattaching one live cue must not multiply the reducer queue",
            1, (queue.get(reducer) as List<*>).size)
        assertEquals(Viseme.SIL, reducer.pose(1_320L).viseme)
    }

    @Test fun expiredOrEndedSpeechCannotBeReplayedByAttach() {
        var now = 1_000L
        val director = AvatarDirector(clock = { now })
        director.speechStarted("u", "A")
        director.wordRange("u", 0, 1, 0)
        now = 1_320L
        val expired = mutableListOf<AvatarEvent>()
        director.attach(AvatarSink(expired::add))
        assertTrue(expired.none { it is AvatarEvent.VisemeCue })

        director.speechEnded("u", true)
        val ended = mutableListOf<AvatarEvent>()
        director.attach(AvatarSink(ended::add))
        assertTrue(ended.none { it is AvatarEvent.VisemeCue || it is AvatarEvent.SpeechStarted })
    }

    @Test fun replacingTheUtteranceDropsThePreviousWord() {
        var now = 1_000L
        val director = AvatarDirector(clock = { now })
        director.speechStarted("old", "A")
        director.wordRange("old", 0, 1, 0)
        now = 1_100L
        director.speechStarted("new", "O")
        val events = mutableListOf<AvatarEvent>()
        director.attach(AvatarSink(events::add))

        assertEquals("new", events.filterIsInstance<AvatarEvent.SpeechStarted>().single().utteranceId)
        assertTrue(events.none { it is AvatarEvent.VisemeCue })
    }

    @Test fun aClockBeforeTheAcceptedWordCannotRescheduleThatWord() {
        var now = 1_000L
        val director = AvatarDirector(clock = { now })
        director.speechStarted("u", "A")
        director.wordRange("u", 0, 1, 0)
        now = 900L
        val events = mutableListOf<AvatarEvent>()
        director.attach(AvatarSink(events::add))
        assertTrue(events.none { it is AvatarEvent.VisemeCue })
        events.clear()
        now = 1_170L
        director.attach(AvatarSink(events::add))
        assertTrue("A rejected clock epoch cannot revive cached word motion",
            events.none { it is AvatarEvent.VisemeCue })
    }
}
