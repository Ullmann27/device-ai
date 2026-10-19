package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class TtsHologramSignalTest {
    @Test fun invalidPlaybackFrameCannotStartTheAudioSignal() {
        val signal = TtsHologramSignal()
        signal.activate("A")
        signal.begin("A", 8_000, 3, 1)
        signal.append("A", ByteArray(800) { 0 })
        signal.anchor("A", -1, 1_000)
        assertEquals(0f, signal.level(1_050), 0f)
    }

    @Test fun invalidPlaybackFrameCannotRewindAnActiveSignal() {
        val signal = TtsHologramSignal()
        signal.activate("A")
        signal.begin("A", 8_000, 3, 1)
        signal.append("A", ByteArray(800) { 128.toByte() })
        signal.append("A", ByteArray(800) { 0 })
        signal.anchor("A", 0, 1_000)
        signal.anchor("A", -1, 1_150)
        assertEquals(1f, signal.level(1_150), 0f)
        assertEquals(0f, signal.level(1_200), 0f)
    }

    @Test fun longSynthesisKeepsLaterAudioAndItsPlaybackPosition() {
        val signal = TtsHologramSignal()
        signal.activate("long")
        signal.begin("long", 8_000, 3, 1)
        repeat(4_096) { signal.append("long", ByteArray(80) { 128.toByte() }) }
        signal.append("long", ByteArray(80) { 0 })
        signal.anchor("long", 0, 0)
        assertEquals(0f, signal.level(40_959), 0f)
        assertEquals(1f, signal.level(40_960), 0f)
        assertEquals(1f, signal.level(40_969), 0f)
        assertEquals(0f, signal.level(40_970), 0f)
    }

    @Test fun boundedHistoryPreservesDurationWeightedAudioPower() {
        val signal = TtsHologramSignal()
        signal.activate("mixed")
        signal.begin("mixed", 8_000, 3, 1)
        signal.append("mixed", ByteArray(80) { 128.toByte() })
        signal.append("mixed", ByteArray(240) { 0 })
        repeat(1_023) { signal.append("mixed", ByteArray(80) { 128.toByte() }) }
        signal.anchor("mixed", 0, 0)
        assertEquals(.8660254f, signal.level(0), .0001f)
        assertEquals(.8660254f, signal.level(39), .0001f)
        assertEquals(0f, signal.level(40), 0f)
    }

    @Test fun synthesisIsSilentUntilAudibleStartAndFollowsPlaybackFrame() {
        val signal = TtsHologramSignal()
        signal.activate("A")
        signal.begin("A",8_000, 2, 1)
        signal.append("A",ByteArray(1_600)) // 100ms silence
        signal.append("A",ByteArray(1_600) { if(it % 2 == 0) 0 else 64 }) // 100ms at 0.5
        assertEquals(0f, signal.level(1000), 0f)
        signal.anchor("A",0,1000)
        assertEquals(0f,signal.level(1050),0f)
        assertEquals(.5f,signal.level(1150),.001f)
        signal.anchor("A",800,2000) // actual TTS range callback corrects the clock
        assertEquals(.5f,signal.level(2000),.001f)
        assertEquals(0f,signal.level(2100),0f)
        signal.stop()
        assertEquals(0f,signal.level(2000),0f)
    }

    @Test fun channelCountAndNewUtterancesDoNotReuseStaleAmplitude() {
        val signal = TtsHologramSignal()
        signal.activate("A")
        signal.begin("A",8_000,3,2)
        signal.append("A",ByteArray(1_600) { 0 }) // 100ms stereo, full amplitude
        signal.anchor("A",0,0)
        assertEquals(1f,signal.level(99),0f)
        assertEquals(0f,signal.level(100),0f)
        signal.begin("A",8_000,3,1)
        assertEquals(0f,signal.level(0),0f)
        signal.begin("A",0,99,0)
        signal.append("A",byteArrayOf(127))
        assertEquals(0f,signal.level(0),0f)
    }
    @Test fun delayedCallbacksCannotResetOrMoveTheNextUtterance() {
        val signal=TtsHologramSignal()
        signal.activate("old")
        signal.begin("old",8_000,3,1)
        signal.activate("new")
        signal.begin("new",8_000,3,1)
        signal.append("new",ByteArray(800) { 0 })
        signal.anchor("new",0,1000)
        signal.begin("old",8_000,3,1)
        signal.append("old",ByteArray(800) { 128.toByte() })
        signal.anchor("old",8_000,0)
        assertEquals(1f,signal.level(1050),0f)
        signal.stop()
        signal.anchor("new",0,2000)
        assertEquals(0f,signal.level(2000),0f)
    }

}
