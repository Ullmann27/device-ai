package at.heinz.deviceai

import android.graphics.ColorSpace
import android.hardware.DataSpace
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidCaptureColorTest {
    @Test fun unknownInputNeverAssumesSrgb() {
        assertNull(AndroidGpuFrameCopier.observedColorSpace(DataSpace.DATASPACE_UNKNOWN))
    }

    @Test fun knownSrgbRemainsSrgb() {
        assertEquals(ColorSpace.get(ColorSpace.Named.SRGB),
            AndroidGpuFrameCopier.observedColorSpace(DataSpace.DATASPACE_SRGB))
    }

    @Test fun knownP3KeepsItsActualInputProfile() {
        val source = AndroidGpuFrameCopier.observedColorSpace(DataSpace.DATASPACE_DISPLAY_P3)
        assertEquals(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), source)
        assertFalse(source!!.isSrgb)
    }
}
