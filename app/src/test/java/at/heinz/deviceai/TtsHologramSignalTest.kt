package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class TtsHologramSignalTest {
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
