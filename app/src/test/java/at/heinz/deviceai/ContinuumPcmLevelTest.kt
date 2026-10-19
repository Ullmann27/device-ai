package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumPcmLevelTest {
    @Test fun silenceIsZero() {
        assertEquals(0f, ContinuumPcmLevel.rms16Le(byteArrayOf(0, 0, 0, 0)), 0.000001f)
        assertEquals(0f, ContinuumPcmLevel.rms16Le(byteArrayOf(127)), 0.000001f)
    }

    @Test fun pcm16LittleEndianAmplitudeMatchesExpectedRms() {
        // +16384 and -16384 represent equal absolute sample amplitudes.
        val frame = byteArrayOf(0, 64, 0, -64)
        assertEquals(0.5f, ContinuumPcmLevel.rms16Le(frame), 0.0001f)
    }

    @Test fun clippedAndOddSizedPacketsStayBounded() {
        assertTrue(ContinuumPcmLevel.rms16Le(byteArrayOf(-1, 127, -1, -1, 42)) <= 1f)
        assertEquals(0f, ContinuumPcmLevel.rms16Le(byteArrayOf()), 0f)
    }

    @Test fun longPcmPacketUsesBoundedSampleCount() {
        val samples = ByteArray(8192) { if (it % 2 == 0) 0 else 64 }
        assertEquals(0.5f, ContinuumPcmLevel.rms16Le(samples), 0.0001f)
    }
}
