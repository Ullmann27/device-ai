package at.heinz.deviceai

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class OwnerFaceFrameTest {
    @Test fun stridedYuvPlanesBecomeOwnedI420WithoutPadding() {
        val planes = listOf(
            OwnerFaceYuvPlane(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 99, 99, 5, 6, 7, 8, 99, 99)), 6, 1),
            OwnerFaceYuvPlane(ByteBuffer.wrap(byteArrayOf(11, 99, 12)), 4, 2),
            OwnerFaceYuvPlane(ByteBuffer.wrap(byteArrayOf(21, 99, 22)), 4, 2)
        )
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 21, 22), OwnerFaceYuvCopy.copy(4, 2, planes))
    }

    @Test fun copyRespectsBufferPositionAndRejectsTruncationOrInvalidDimensions() {
        val y = ByteBuffer.wrap(byteArrayOf(99, 1, 2, 3, 4)).apply { position(1) }
        val planes = listOf(OwnerFaceYuvPlane(y, 2, 1),
            OwnerFaceYuvPlane(ByteBuffer.wrap(byteArrayOf(5)), 1, 1),
            OwnerFaceYuvPlane(ByteBuffer.wrap(byteArrayOf(6)), 1, 1))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), OwnerFaceYuvCopy.copy(2, 2, planes))
        assertNull(OwnerFaceYuvCopy.copy(3, 2, planes))
        assertNull(OwnerFaceYuvCopy.copy(641, 2, planes))
        assertNull(OwnerFaceYuvCopy.copy(4, 4, planes))
        assertNull(OwnerFaceYuvCopy.copy(2, 2, planes.dropLast(1)))
    }

    @Test fun closedFramesErasePixelsAndCannotBeReused() {
        val bytes = ByteArray(6) { 17 }
        val frame = OwnerFaceFrame(2, 2, 90, 100, 1, bytes)
        assertTrue(frame.valid())
        frame.close()
        frame.close()
        assertFalse(frame.valid())
        assertTrue(bytes.all { it == 0.toByte() })
        assertFalse(OwnerFaceFrame(2, 2, 45, 100, 1, ByteArray(6)).valid())
        assertFalse(OwnerFaceFrame(2, 2, 90, 100, 0, ByteArray(6)).valid())
    }
}
