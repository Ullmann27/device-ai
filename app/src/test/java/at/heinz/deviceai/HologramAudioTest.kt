package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HologramAudioTest {
    @Test fun pcm16UsesLittleEndianSignedSamples() {
        assertEquals(0.5f, HologramPcmLevel.rms(byteArrayOf(0, 64, 0, -64), 2), .0001f)
        assertEquals(1f, HologramPcmLevel.rms(byteArrayOf(0, -128), 2), .0001f)
    }

    @Test fun pcm8UsesUnsignedMidpointForSilence() {
        assertEquals(0f, HologramPcmLevel.rms(byteArrayOf(-128, -128), 3), 0f)
        assertEquals(1f, HologramPcmLevel.rms(byteArrayOf(0), 3), .0001f)
    }

    @Test fun pcmLevelMeasuresAveragePowerAcrossTheBuffer() {
        assertEquals(.70710677f, HologramPcmLevel.rms(byteArrayOf(0, 0, 0, -128), 2), .0001f)
    }

    @Test fun floatPcmIgnoresNonFiniteSamplesAndBoundsClippedAudio() {
        val samples = byteArrayOf(0, 0, 0, 63, 0, 0, 0, -65)
        assertEquals(0.5f, HologramPcmLevel.rms(samples, 4), .0001f)
        assertEquals(0f, HologramPcmLevel.rms(byteArrayOf(0, 0, -64, 127), 4), 0f)
        assertEquals(1f, HologramPcmLevel.rms(byteArrayOf(0, 0, 0, 64), 4), 0f)
    }

    @Test fun malformedOrUnsupportedPcmProducesSilence() {
        assertEquals(0f, HologramPcmLevel.rms(byteArrayOf(127), 2), 0f)
        assertEquals(0f, HologramPcmLevel.rms(byteArrayOf(), 3), 0f)
        assertEquals(0f, HologramPcmLevel.rms(byteArrayOf(1, 2, 3), 99), 0f)
    }

    @Test fun envelopeFollowsAudioAndReleasesWhenSamplesStop() {
        val envelope = HologramAudioEnvelope()
        envelope.submit(1f, 0)
        envelope.valueAt(0)
        assertTrue(envelope.valueAt(80) > .8f)
        assertTrue(envelope.valueAt(800) < .02f)
    }

    @Test fun envelopeNeverLetsInvalidAudioPoisonLaterSamples() {
        val envelope = HologramAudioEnvelope()
        envelope.submit(Float.NaN, 0)
        assertEquals(0f, envelope.valueAt(16), 0f)
        envelope.submit(Float.POSITIVE_INFINITY, 16)
        assertEquals(0f, envelope.valueAt(32), 0f)
        envelope.submit(5f, 32)
        assertTrue(envelope.valueAt(112) in .8f..1f)
        envelope.reset()
        assertEquals(0f, envelope.valueAt(128), 0f)
    }

    @Test fun envelopeAttackIsIndependentOfFrameRate() {
        val fast = HologramAudioEnvelope()
        val slow = HologramAudioEnvelope()
        fast.submit(.6f, 0)
        slow.submit(.6f, 0)
        fast.valueAt(0)
        slow.valueAt(0)
        for (time in 10..100 step 10) fast.valueAt(time.toLong())
        slow.valueAt(100)
        assertEquals(fast.valueAt(100), slow.valueAt(100), .0001f)
    }
}
