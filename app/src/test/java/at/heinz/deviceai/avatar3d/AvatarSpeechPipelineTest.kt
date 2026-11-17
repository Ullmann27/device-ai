package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.TtsHologramSignal
import at.heinz.deviceai.avatar.*
import org.junit.Assert.*
import org.junit.Test

class AvatarSpeechPipelineTest {
    @Test fun playbackPcmAndWordRangesDriveTheMouthAndStopRejectsLateCallbacks() {
        var now = 1_000L
        val signal = TtsHologramSignal()
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(clock = { now })
        director.attach(AvatarSink(reducer::apply))
        val names = AvatarGlbContract.TARGETS
        val map = AvatarMorphMapper(names, -22.0)
        signal.activate("live")
        signal.begin("live", 8_000, 2, 1)
        signal.append("live", ByteArray(8_000) { if (it % 2 == 0) 0 else 64 })
        assertEquals(0f, signal.level(now), 0f)
        assertTrue(director.speechStarted("live", "Papa"))
        signal.anchor("live", 0, now)
        director.onRuntime(AvatarRuntimeSignals(speaking = true))
        assertTrue(director.wordRange("live", 0, 4, 0))

        now = 1_150
        assertEquals(.5f, signal.level(now), .001f)
        director.audioLevel("live", signal.level(now))
        val vowel = reducer.pose(now)
        assertEquals(Viseme.AA, vowel.viseme)
        map.update(vowel, 1.15f, true)
        assertTrue(map.jawPitch > 0f)
        assertTrue(map.weights[names.indexOf("AA")] > 0f)
        assertEquals(0f, map.weights[names.indexOf("Smile")], 0f)

        now = 1_220
        director.audioLevel("live", signal.level(now))
        assertEquals(Viseme.PP, reducer.pose(now).viseme)
        map.update(reducer.pose(now), 1.22f, true)
        assertEquals(0f, map.jawPitch, 0f)
        assertEquals(0f, map.weights[names.indexOf("AA")], 0f)

        now = 1_230
        director.speechEnded("live", true)
        signal.stop()
        now = 1_240
        signal.append("live", ByteArray(800) { 64 })
        signal.anchor("live", 0, now)
        director.audioLevel("live", 1f)
        assertFalse(director.wordRange("live", 0, 4, 0))
        assertFalse(director.speechStarted("live", "replayed"))
        map.update(reducer.pose(now), 1.24f, true)
        assertEquals(0f, signal.level(now), 0f)
        assertEquals(0f, map.jawPitch, 0f)
        for (viseme in Viseme.entries) assertEquals(0f, map.weights[names.indexOf(viseme.name)], 0f)
    }
}
