package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.TtsHologramSignal
import at.heinz.deviceai.avatar.*
import org.junit.Assert.*
import org.junit.Test

class AvatarMeasuredSilenceTest {
    private val names = AvatarGlbContract.TARGETS

    @Test fun silentPcmClosesAnActiveVowelWithoutFallbackMotion() {
        val pipeline = Pipeline()
        pipeline.signal.append("live", pcm(250, 64))
        pipeline.signal.append("live", pcm(100, 0))
        pipeline.start()
        pipeline.frame(1_200)
        assertTrue(pipeline.map.jawPitch > 0f)
        pipeline.frame(1_260)
        pipeline.assertClosed()
        assertEquals(0f, pipeline.reducer.pose(1_260).speakEnergy, 0f)
    }

    @Test fun exhaustedPcmRemainsKnownSilenceDuringAWordCallbackWindow() {
        val pipeline = Pipeline()
        pipeline.signal.append("live", pcm(250, 64))
        pipeline.start()
        pipeline.frame(1_200)
        assertTrue(pipeline.map.jawPitch > 0f)
        pipeline.frame(1_400)
        pipeline.assertClosed()
        assertEquals(0f, pipeline.reducer.pose(1_400).speakEnergy, 0f)
    }

    @Test fun missingPcmKeepsTheExistingWordRangeFallback() {
        val pipeline = Pipeline()
        pipeline.start()
        assertNull(pipeline.signal.measuredLevel("live", 1_200))
        pipeline.frame(1_200)
        assertTrue(pipeline.map.jawPitch > 0f)
        assertTrue(pipeline.reducer.pose(1_200).speakEnergy > 0f)
    }

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

    @Test fun staleUtteranceCannotClaimCurrentPcmAvailability() {
        val signal = TtsHologramSignal()
        signal.activate("old")
        signal.begin("old", 8_000, 2, 1)
        signal.append("old", pcm(100, 64))
        signal.anchor("old", 0, 1_000)
        assertEquals(.5f, requireNotNull(signal.measuredLevel("old", 1_000)), .001f)
        signal.activate("new")
        assertNull(signal.measuredLevel("old", 1_000))
        assertNull(signal.measuredLevel("new", 1_000))
        signal.begin("new", 8_000, 2, 1)
        signal.append("new", pcm(100, 0))
        signal.anchor("new", 0, 1_000)
        assertEquals(0f, requireNotNull(signal.measuredLevel("new", 1_000)), 0f)
        signal.stop()
        assertNull(signal.measuredLevel("new", 1_000))
    }

    private fun pcm(milliseconds: Int, highByte: Int) =
        ByteArray(milliseconds * 16) { if (it % 2 == 0) 0 else highByte.toByte() }

    private inner class Pipeline {
        var now = 1_000L
        val signal = TtsHologramSignal()
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(clock = { now })
        val map = AvatarMorphMapper(names)
        init {
            director.attach(AvatarSink(reducer::apply))
            signal.activate("live")
            signal.begin("live", 8_000, 2, 1)
        }
        fun start() {
            director.speechStarted("live", "a")
            signal.anchor("live", 0, now)
            director.onRuntime(AvatarRuntimeSignals(speaking = true))
            director.wordRange("live", 0, 1, 0)
        }
        fun frame(at: Long) {
            now = at
            val measured = signal.measuredLevel("live", now)
            director.audioLevel("live", measured ?: 0f, measuredPcmAvailable = measured != null)
            map.update(reducer.pose(now), now / 1_000f, true)
        }
        fun assertClosed() {
            assertEquals(0f, map.jawPitch, 0f)
            for (viseme in Viseme.entries) assertEquals(0f, map.weights[names.indexOf(viseme.name)], 0f)
        }
    }
}
