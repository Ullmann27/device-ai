package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.*
import org.junit.Assert.*
import org.junit.Test

class MeasuredSilenceBaselineTest {
    private val names = AvatarGlbContract.TARGETS

    @Test fun measuredSilenceCanResumeTheStillCurrentWordCue() {
        val reducer = AvatarPoseReducer()
        val map = AvatarMorphMapper(names)
        reducer.apply(AvatarEvent.SpeechStarted("live", "a", 1_000))
        reducer.apply(AvatarEvent.VisemeCue("live", Viseme.AA, 1f, 1_000, 1_000))
        reducer.apply(AvatarEvent.AudioLevel("live", .7f, 1_010))
        map.update(reducer.pose(1_010), 1.01f, true)
        assertTrue(map.jawPitch > 0f)
        reducer.apply(AvatarEvent.AudioLevel("live", 0f, 1_043))
        map.update(reducer.pose(1_043), 1.043f, true)
        assertEquals(0f, map.jawPitch, 0f)
        reducer.apply(AvatarEvent.AudioLevel("live", .7f, 1_076))
        map.update(reducer.pose(1_076), 1.076f, true)
        assertTrue(map.jawPitch > 0f)
        assertTrue(map.weights[names.indexOf("AA")] > 0f)
    }

    @Test fun expiredMeasuredAudioCannotLeaveAQueuedVowelOpen() {
        val reducer = AvatarPoseReducer()
        val map = AvatarMorphMapper(names)
        reducer.apply(AvatarEvent.SpeechStarted("live", "a", 1_000))
        reducer.apply(AvatarEvent.VisemeCue("live", Viseme.AA, 1f, 1_000, 1_000))
        reducer.apply(AvatarEvent.AudioLevel("live", .7f, 1_010))
        map.update(reducer.pose(1_010), 1.01f, true)
        assertTrue(map.jawPitch > 0f)
        map.update(reducer.pose(1_261), 1.261f, true)
        assertEquals(0f, map.jawPitch, 0f)
        assertEquals(0f, map.weights[names.indexOf("AA")], 0f)
    }

}
