package at.heinz.deviceai.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionLibraryTest {
    @Test fun registersMathematikPresets() {
        assertSame(MotionPresets.flyInEasing, MotionLibrary.easing)
        assertSame(MotionPresets.widgetSpring, MotionLibrary.spring)
        assertSame(MotionPresets.panelStagger, MotionLibrary.stagger)
        assertTrue(MotionLibrary.faceWeights is LipSyncFaceSource)
        assertEquals(FaceChannels.RENDERER_ORDER, MotionLibrary.faceWeights!!.channelNames)
        assertEquals(null, MotionLibrary.visemeHint)
    }

    @Test fun defaultLevelZeroKeepsMouthExactlyClosed() {
        val face = MotionLibrary.faceWeights!!
        val out = FloatArray(face.channelNames.size)
        val mouth = (0 until FaceChannels.VISEME_COUNT) + face.channelNames.indexOf("JawOpen")
        for (t in 0L..2000L step 16) {
            assertEquals(0f, MotionLibrary.audioLevel.levelAt(t), 0f)
            face.weightsAt(t, out)
            mouth.forEach { assertEquals(face.channelNames[it], 0f, out[it], 0f) }
        }
    }
}
