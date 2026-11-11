package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class MayaSpeechAlignmentTest {
    private fun characters(text: String) = text.codePoints().toArray().map { String(Character.toChars(it)) }
    private fun aligned(text: String): List<MayaSpeechAlignment.Range> {
        val chars = characters(text)
        return MayaSpeechAlignment.ranges(text, chars,
            chars.indices.map { it * 0.05 }, chars.indices.map { (it + 1) * 0.05 }, 24_000)
    }

    @Test fun originalWordOffsetsUseActualCharacterTimes() {
        assertEquals(listOf(
            MayaSpeechAlignment.Range(0, 5, 0, 6000),
            MayaSpeechAlignment.Range(6, 10, 7200, 12000)
        ), aligned("Hallo Welt"))
    }

    @Test fun supplementaryCharactersPreserveUtf16Offsets() {
        assertEquals(listOf(MayaSpeechAlignment.Range(3, 8, 2400, 8400)), aligned("😀 Grüße"))
    }

    @Test fun normalizedOrMalformedAlignmentNeverInventsWordCues() {
        val text = "Hallo"
        val chars = characters(text)
        val starts = chars.indices.map { it * 0.05 }
        val ends = chars.indices.map { (it + 1) * 0.05 }
        assertTrue(MayaSpeechAlignment.ranges("5", characters("fünf"), starts, ends, 24_000).isEmpty())
        assertTrue(MayaSpeechAlignment.ranges(text, chars.dropLast(1), starts, ends, 24_000).isEmpty())
        assertTrue(MayaSpeechAlignment.ranges(text, listOf("Ha", "llo"), listOf(0.0, .1), listOf(.1, .2), 24_000).isEmpty())
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 2.0)) {
            assertTrue(MayaSpeechAlignment.ranges(text, chars, starts, ends.dropLast(1) + bad, 24_000).isEmpty())
        }
        assertTrue(MayaSpeechAlignment.ranges(text, chars, listOf(0.0, .1, .05, .15, .2), ends, 24_000).isEmpty())
        assertTrue(MayaSpeechAlignment.ranges(text, chars, starts, ends, 100).isEmpty())
    }

    @Test fun cursorFollowsPlaybackSkipsExpiredWordsAndNeverRewinds() {
        val first = MayaSpeechAlignment.Range(0, 1, 10, 20)
        val second = MayaSpeechAlignment.Range(2, 3, 30, 40)
        val third = MayaSpeechAlignment.Range(4, 5, 50, 60)
        val cursor = MayaSpeechAlignment.Cursor(listOf(first, second, third))
        assertNull(cursor.advance(-1))
        assertNull(cursor.advance(9))
        assertEquals(first, cursor.advance(10))
        assertNull(cursor.advance(11))
        assertEquals(third, cursor.advance(55))
        assertNull(cursor.advance(31))
        assertNull(cursor.advance(100))
    }

    @Test fun invalidProviderRangesFallBackToPcmOnly() {
        for (ranges in listOf(
            listOf(MayaSpeechAlignment.Range(-1, 1, 0, 10)),
            listOf(MayaSpeechAlignment.Range(0, 9, 0, 10)),
            listOf(MayaSpeechAlignment.Range(0, 1, -1, 10)),
            listOf(MayaSpeechAlignment.Range(0, 1, 10, 10)),
            listOf(MayaSpeechAlignment.Range(0, 1, 0, 1000)),
            listOf(MayaSpeechAlignment.Range(2, 3, 20, 30), MayaSpeechAlignment.Range(0, 1, 0, 10))
        )) assertTrue(MayaSpeechAlignment.validated("abc", 100, ranges).isEmpty())
    }
}
