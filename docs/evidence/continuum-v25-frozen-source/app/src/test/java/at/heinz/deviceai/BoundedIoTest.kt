package at.heinz.deviceai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedIoTest {
    /** Delivers at most 3 bytes per read, like many ContentResolver streams. */
    private class Trickle(private val data: ByteArray) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xff else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val n = minOf(len, 3, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }
    }

    @Test fun readsSmallInputCompletely() {
        val data = ByteArray(10) { it.toByte() }
        assertArrayEquals(data, Trickle(data).readAtMost(100))
        assertArrayEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAtMost(5))
    }

    @Test fun stopsOneByteAfterTheLimitSoOversizeIsDetectable() {
        val data = ByteArray(50_000) { (it % 251).toByte() }
        val read = Trickle(data).readAtMost(4_000)
        assertEquals(4_001, read.size)
        assertArrayEquals(data.copyOf(4_001), read)
        assertEquals(4_000, ByteArrayInputStream(data, 0, 4_000).readAtMost(4_000).size)
    }
}
