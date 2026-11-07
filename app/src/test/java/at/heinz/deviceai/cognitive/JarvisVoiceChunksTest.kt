package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #566: the whole reply is spoken as ordered pieces; the former single preview stopped at 480 characters. */
class JarvisVoiceChunksTest {
    private val long = (1..40).joinToString(" ") { "Satz $it beschreibt einen weiteren Teil der Antwort genau." }

    @Test fun longTextIsSplitAtSentenceEndsWithoutLosingText() {
        val chunks = JarvisVoiceProfile.speakableChunks(long)
        assertFalse(chunks.truncated)
        assertTrue(chunks.pieces.size >= 4)
        assertTrue(chunks.pieces.all { it.length <= 480 && it.endsWith(".") })
        assertEquals(long, chunks.pieces.joinToString(" "))
        assertEquals("the preview keeps its former behaviour", JarvisVoiceProfile.speakable(long), chunks.pieces.first())
    }

    @Test fun codeAndLinksAreStillNotReadAndShortTextIsOnePiece() {
        val chunks = JarvisVoiceProfile.speakableChunks("Hier: ```kotlin\nval x = 1\n``` siehe https://example.org bitte.")
        assertEquals(1, chunks.pieces.size)
        assertFalse(chunks.pieces.single().contains("val x"))
        assertFalse(chunks.pieces.single().contains("example.org"))
        assertTrue(JarvisVoiceProfile.speakableChunks("   ").pieces.isEmpty())
    }

    @Test fun wordsWithoutSentenceEndsSplitAtSpacesAndTheQueueBoundIsReported() {
        val words = (1..2_000).joinToString(" ") { "wort$it" }
        val chunks = JarvisVoiceProfile.speakableChunks(words, maxChunks = 3)
        assertEquals(3, chunks.pieces.size)
        assertTrue("text beyond the queue capacity is reported, not hidden", chunks.truncated)
        assertTrue(chunks.pieces.all { piece -> piece.split(' ').all { it.startsWith("wort") } })
    }
}
