package at.heinz.deviceai.avatar

import org.junit.Assert.*
import org.junit.Test

class AvatarNativeRegressionTest {
    @Test fun futureVisemesFollowTheirTimestampAndExpire() {
        val reducer = AvatarPoseReducer()
        reducer.apply(AvatarEvent.SpeechStarted("u", "ignored by renderer", 100))
        reducer.apply(AvatarEvent.VisemeCue("u", Viseme.AA, 1f, 80, 200))
        reducer.apply(AvatarEvent.VisemeCue("u", Viseme.O, 1f, 80, 280))
        assertEquals(Viseme.SIL, reducer.pose(199).viseme)
        assertEquals(Viseme.AA, reducer.pose(200).viseme)
        assertEquals(Viseme.O, reducer.pose(280).viseme)
        assertEquals(Viseme.SIL, reducer.pose(360).viseme)
    }

    @Test fun replacementAndInterruptionDiscardOldMouthAndFutureCues() {
        val reducer = AvatarPoseReducer()
        reducer.apply(AvatarEvent.SpeechStarted("old", "Hallo", 100))
        reducer.apply(AvatarEvent.AudioLevel("old", 1f, 101))
        reducer.apply(AvatarEvent.VisemeCue("old", Viseme.O, 1f, 80, 110))
        assertTrue(reducer.pose(110).jawOpen > 0f)
        reducer.apply(AvatarEvent.SpeechStarted("new", "Guten Tag", 120))
        assertEquals(Viseme.SIL, reducer.pose(120).viseme)
        assertEquals(0f, reducer.pose(120).jawOpen)
        reducer.apply(AvatarEvent.AudioLevel("old", 1f, 121))
        reducer.apply(AvatarEvent.SpeechEnded("old", true, 122))
        reducer.apply(AvatarEvent.AudioLevel("new", 1f, 123))
        assertTrue(reducer.pose(123).jawOpen > 0f)
        reducer.apply(AvatarEvent.VisemeCue("new", Viseme.AA, 1f, 80, 200))
        reducer.apply(AvatarEvent.SpeechEnded("new", true, 124))
        assertEquals(0f, reducer.pose(200).jawOpen)
        assertEquals(Viseme.SIL, reducer.pose(200).viseme)
    }

    @Test fun invalidAudioCannotPoisonPoseAndMissingCallbacksCloseMouth() {
        val reducer = AvatarPoseReducer()
        reducer.apply(AvatarEvent.SpeechStarted("u", "Hallo", 100))
        reducer.apply(AvatarEvent.AudioLevel("u", Float.NaN, 101))
        reducer.apply(AvatarEvent.VisemeCue("u", Viseme.O, Float.NaN, 80, 102))
        assertEquals(0f, reducer.pose(102).jawOpen)
        assertEquals(Viseme.SIL, reducer.pose(102).viseme)
        reducer.apply(AvatarEvent.AudioLevel("u", 1f, 110))
        assertTrue(reducer.pose(110).jawOpen > 0f)
        assertEquals(0f, reducer.pose(361).jawOpen)
        reducer.clearSpeech()
        reducer.apply(AvatarEvent.AudioLevel("u", 1f, 400))
        assertEquals(0f, reducer.pose(400).jawOpen)
    }

    @Test fun rebuiltSinkReceivesCurrentSpeechAndStaleCallbacksStayRejected() {
        var now = 100L
        val director = AvatarDirector(clock = { now })
        director.onRuntime(AvatarRuntimeSignals(speaking = true))
        director.speechStarted("current", "Hallo")
        val reducer = AvatarPoseReducer()
        director.attach(AvatarSink { reducer.apply(it) })
        now = 110
        director.audioLevel("current", 1f)
        assertEquals(AvatarState.SPEAKING, reducer.pose(now).state)
        assertTrue(reducer.pose(now).jawOpen > 0f)
        director.speechEnded("current", true)
        director.audioLevel("current", 1f)
        director.wordRange("current", 0, 5)
        assertEquals(0f, reducer.pose(now).jawOpen)
        assertEquals(Viseme.SIL, reducer.pose(now).viseme)
    }
}
