package at.heinz.deviceai.learning.lm

import at.heinz.deviceai.learning.lm.reference.LmBpeTokenizer as RefTokenizer
import java.util.Locale
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class LmHexR3Checks {
    @Test fun bytesAndSeededLengthsMatch() {
        val random = Random(37)
        val cases = mutableListOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(-1), ByteArray(256) { it.toByte() })
        for (length in listOf(0, 1, 2, 3, 31, 32, 64, 255, 256, 1024, 4096)) {
            repeat(3) { cases.add(ByteArray(length).also(random::nextBytes)) }
        }
        for (bytes in cases) {
            val copy = bytes.copyOf()
            val actual = LmBpeTokenizer.hex(bytes)
            assertEquals(RefTokenizer.hex(bytes), actual)
            assertEquals(bytes.size * 2, actual.length)
            assertTrue(actual.all { it in '0'..'9' || it in 'a'..'f' })
            assertArrayEquals(copy, bytes)
        }
        assertEquals("", LmBpeTokenizer.hex(byteArrayOf()))
        assertEquals("00", LmBpeTokenizer.hex(byteArrayOf(0)))
        assertEquals("ff", LmBpeTokenizer.hex(byteArrayOf(-1)))
    }

    @Test fun defaultLocalesKeepRepresentation() {
        val original = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        val bytes = ByteArray(256) { it.toByte() }
        val expected = RefTokenizer.hex(bytes)
        try {
            for (tag in listOf("de-AT", "en-US", "tr-TR", "ar-EG", "ja-JP")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals(expected, RefTokenizer.hex(bytes))
                assertEquals(expected, LmBpeTokenizer.hex(bytes))
                assertEquals("", LmBpeTokenizer.hex(byteArrayOf()))
            }
        } finally {
            Locale.setDefault(original)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
    }
}
