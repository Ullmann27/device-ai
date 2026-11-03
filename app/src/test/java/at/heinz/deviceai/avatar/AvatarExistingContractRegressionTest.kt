package at.heinz.deviceai.avatar

import org.junit.Assert.*
import org.junit.Test

/** Uses only the base #390 public API so this exact file can run against the unmodified baseline. */
class AvatarExistingContractRegressionTest {
    @Test fun replacementUtteranceStartsWithClosedNeutralMouth() {
        val reducer = AvatarPoseReducer()
        reducer.apply(AvatarEvent.SpeechStarted("old", "Hallo", 100))
        reducer.apply(AvatarEvent.VisemeCue("old", Viseme.O, 1f, 80, 110))
        assertEquals(Viseme.O, reducer.pose().viseme)
        reducer.apply(AvatarEvent.SpeechStarted("new", "Guten Tag", 120))
        assertEquals(Viseme.SIL, reducer.pose().viseme)
        assertEquals(0f, reducer.pose().jawOpen)
    }

    @Test fun malformedAudioMustNotProduceNaNJaw() {
        val reducer = AvatarPoseReducer()
        reducer.apply(AvatarEvent.SpeechStarted("u", "Hallo", 100))
        reducer.apply(AvatarEvent.AudioLevel("u", Float.NaN, 101))
        assertEquals(0f, reducer.pose().jawOpen)
    }

    @Test fun replacingSinkDuringSpeechContinuesTheCurrentUtterance() {
        val director = AvatarDirector(clock = { 100L })
        director.speechStarted("u", "Hallo")
        val reducer = AvatarPoseReducer()
        director.attach(AvatarSink { reducer.apply(it) })
        director.audioLevel("u", 1f)
        assertTrue("New view must accept the ongoing TTS envelope", reducer.pose().jawOpen > 0f)
    }
}
