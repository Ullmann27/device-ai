package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reducer-only checks; these do not claim Android, audio-output or native-renderer acceptance. */
class AvatarSpeechOrderingRegressionTest {
    @Test fun olderAudioCannotResmoothTheCurrentEnvelope() {
        val reducer = speakingReducer()
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))
        reducer.apply(AvatarEvent.AudioLevel("current", .1f, 250))
        val beforeReplay = reducer.pose(260)
        // The second sample is 50 ms later; smoothing preserves the 33 ms reference response.
        assertEquals(.21990544f, beforeReplay.speakEnergy, .000001f)

        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))

        assertEquals(beforeReplay, reducer.pose(260))
    }

    @Test fun olderAudioCannotMoveTheLatestSamplesExpiry() {
        val reducer = speakingReducer()
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))
        reducer.apply(AvatarEvent.AudioLevel("current", .1f, 250))
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))

        assertEquals(.21990544f, reducer.pose(500).speakEnergy, .000001f)
        assertTrue(reducer.pose(500).jawOpen > 0f)
        assertEquals(0f, reducer.pose(501).speakEnergy, 0f)
        assertEquals(0f, reducer.pose(501).jawOpen, 0f)
    }

    @Test fun repeatedTimestampIsIdempotent() {
        val reducer = speakingReducer()
        val sample = AvatarEvent.AudioLevel("current", .9f, 200)
        reducer.apply(sample)
        val first = reducer.pose(200)

        repeat(3) { reducer.apply(sample) }
        assertEquals(first, reducer.pose(200))

        reducer.apply(sample.copy(level = .1f))
        assertEquals(first, reducer.pose(200))
    }

    @Test fun rejectedAudioCannotRewindOrAdvanceTheDefaultPoseClock() {
        val reducer = speakingReducer()
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))
        reducer.apply(AvatarEvent.AudioLevel("current", .1f, 250))
        reducer.apply(AvatarEvent.VisemeCue("current", Viseme.AA, .7f, 100, 350))
        reducer.apply(AvatarEvent.StateChanged(
            AvatarState.SPEAKING, AvatarExpression.NEUTRAL, GazeTarget.OWNER, 400
        ))
        val currentPose = reducer.pose()
        assertEquals(Viseme.AA, currentPose.viseme)
        assertEquals(.21990544f, currentPose.speakEnergy, .000001f)

        for (rejected in listOf(
            AvatarEvent.AudioLevel("current", .9f, 200),
            AvatarEvent.AudioLevel("current", .1f, 250),
            AvatarEvent.AudioLevel("current", Float.NaN, 201),
            AvatarEvent.AudioLevel("previous", 1f, 202),
            AvatarEvent.AudioLevel("previous", 1f, Long.MAX_VALUE)
        )) {
            reducer.apply(rejected)
            assertEquals("Rejected audio changed the default pose: $rejected", currentPose, reducer.pose())
        }
    }

    @Test fun replacementSpeechStartsWithItsOwnAudioOrdering() {
        val reducer = speakingReducer()
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 900))
        assertTrue(reducer.pose(900).jawOpen > 0f)

        reducer.apply(AvatarEvent.SpeechStarted("replacement", "Guten Tag", 100))
        reducer.apply(AvatarEvent.AudioLevel("replacement", .5f, 110))
        reducer.apply(AvatarEvent.AudioLevel("current", 1f, 901))

        assertEquals(.2f, reducer.pose(110).speakEnergy, .000001f)
        assertTrue(reducer.pose(110).jawOpen > 0f)
    }

    @Test fun rejectedFutureAudioCannotPoisonEnergyOrderingOrTheDefaultPoseClock() {
        val reducer = speakingReducer()
        reducer.apply(AvatarEvent.VisemeCue("current", Viseme.AA, .7f, 100, 200))
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))
        assertEquals(.36f, reducer.pose(200).speakEnergy, .000001f)

        reducer.apply(AvatarEvent.AudioLevel("current", 1f, Long.MAX_VALUE))
        val afterRejection = reducer.pose(201)
        assertEquals(0f, afterRejection.speakEnergy, 0f)
        assertEquals(Viseme.SIL, afterRejection.viseme)
        assertEquals(0f, afterRejection.visemeWeight, 0f)
        assertEquals(afterRejection, reducer.pose())

        // Rejecting a future timestamp must preserve the last confirmed ordering boundary.
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 199))
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))
        assertEquals(0f, reducer.pose(201).speakEnergy, 0f)

        reducer.apply(AvatarEvent.AudioLevel("current", .1f, 202))
        assertEquals(.04f, reducer.pose().speakEnergy, .000001f)
        assertEquals(Viseme.AA, reducer.pose().viseme)
    }

    @Test fun futureAudioBetweenFramesCannotReopenFromAlreadyDeliveredSamples() {
        val reducer = speakingReducer()
        // Several callbacks can arrive before the first renderer frame confirms any audio.
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))
        reducer.apply(AvatarEvent.AudioLevel("current", 1f, Long.MAX_VALUE))
        assertEquals(0f, reducer.pose(201).speakEnergy, 0f)

        for (timestamp in listOf(199L, 200L, 201L)) {
            reducer.apply(AvatarEvent.AudioLevel("current", .9f, timestamp))
            assertEquals("Queued audio revived after future rejection: $timestamp", 0f,
                reducer.pose(201).speakEnergy, 0f)
        }

        reducer.apply(AvatarEvent.AudioLevel("current", .1f, 202))
        assertEquals(.04f, reducer.pose(202).speakEnergy, .000001f)
    }

    @Test fun aDefaultPoseCannotKeepFutureAudioAheadOfTheExplicitRenderClock() {
        val reducer = speakingReducer()
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))
        reducer.pose(200)
        reducer.apply(AvatarEvent.AudioLevel("current", 1f, Long.MAX_VALUE))
        // The default follows the event timestamp; the next explicit frame must be authoritative.
        reducer.pose()
        assertEquals(0f, reducer.pose(201).speakEnergy, 0f)

        reducer.apply(AvatarEvent.AudioLevel("current", .1f, 202))

        assertEquals(.04f, reducer.pose(202).speakEnergy, .000001f)
    }

    @Test fun endedSpeechCannotRestartFromQueuedAudioOrVisemes() {
        val reducer = speakingReducer()
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))
        reducer.apply(AvatarEvent.VisemeCue("current", Viseme.AA, 1f, 100, 200))
        reducer.apply(AvatarEvent.SpeechEnded("current", false, 210))

        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 220))
        reducer.apply(AvatarEvent.VisemeCue("current", Viseme.AA, 1f, 100, 220))

        assertSilent(reducer.pose(230))
        reducer.apply(AvatarEvent.SpeechStarted("replacement", "Weiter", 240))
        reducer.apply(AvatarEvent.AudioLevel("replacement", .5f, 250))
        assertEquals(.2f, reducer.pose(250).speakEnergy, .000001f)
    }

    @Test fun pauseClearsAudioOrderingWithoutAdmittingLateCallbacks() {
        val reducer = speakingReducer()
        reducer.apply(AvatarEvent.AudioLevel("current", .9f, 200))
        reducer.apply(AvatarEvent.VisemeCue("current", Viseme.AA, 1f, 100, 200))
        reducer.clearSpeech()

        reducer.apply(AvatarEvent.AudioLevel("current", .9f, Long.MAX_VALUE))
        reducer.apply(AvatarEvent.VisemeCue("current", Viseme.AA, 1f, 100, 220))

        assertSilent(reducer.pose(230))
        reducer.apply(AvatarEvent.SpeechStarted("replacement", "Weiter", 240))
        reducer.apply(AvatarEvent.AudioLevel("replacement", .5f, 250))
        assertEquals(.2f, reducer.pose(250).speakEnergy, .000001f)
    }

    private fun speakingReducer() = AvatarPoseReducer().apply {
        apply(AvatarEvent.SpeechStarted("current", "Hallo", 100))
    }

    private fun assertSilent(pose: AvatarPose) {
        assertEquals(0f, pose.jawOpen, 0f)
        assertEquals(0f, pose.speakEnergy, 0f)
        assertEquals(Viseme.SIL, pose.viseme)
    }
}
