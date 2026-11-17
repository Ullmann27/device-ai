package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarSpeechClockTest {
    @Test fun aConstantAudioEnvelopeDependsOnElapsedTimeRatherThanCallbackCount() {
        fun sampled(stepMillis: Long): AvatarPose {
            val reducer = AvatarPoseReducer()
            reducer.apply(AvatarEvent.SpeechStarted("u", "A", 0))
            reducer.apply(AvatarEvent.AudioLevel("u", 0f, 0))
            for (time in stepMillis..99L step stepMillis) {
                reducer.apply(AvatarEvent.AudioLevel("u", 1f, time))
            }
            return reducer.pose(99)
        }

        val every33Millis = sampled(33)
        val every11Millis = sampled(11)

        // Three nominal 33 ms intervals retain 0.6^3 of the starting difference.
        assertEquals(.784f, every33Millis.speakEnergy, .000001f)
        assertEquals(every33Millis.speakEnergy, every11Millis.speakEnergy, .000001f)
    }

    @Test fun anEndedDifferentUtteranceCannotAdvanceTheCurrentSpeechClock() {
        val reducer = speakingReducer()
        val before = reducer.pose()

        reducer.apply(AvatarEvent.SpeechEnded("old", true, 10_000))

        assertEquals(before, reducer.pose())
    }

    @Test fun aCueFromAnotherUtteranceCannotAdvanceTheCurrentSpeechClock() {
        val reducer = speakingReducer()
        val before = reducer.pose()

        reducer.apply(AvatarEvent.VisemeCue("old", Viseme.AA, 1f, 80, 10_000))

        assertEquals(before, reducer.pose())
    }

    @Test fun duplicateStartCannotClearAnAlreadyVisibleWord() {
        val reducer = speakingReducer()
        val before = reducer.pose()

        reducer.apply(AvatarEvent.SpeechStarted("current", "O", 110))

        assertEquals(before, reducer.pose())
    }

    private fun speakingReducer() = AvatarPoseReducer().apply {
        apply(AvatarEvent.SpeechStarted("current", "O", 100))
        apply(AvatarEvent.AudioLevel("current", .5f, 100))
        apply(AvatarEvent.VisemeCue("current", Viseme.O, 1f, 300, 100))
    }
}
