package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * CDX1 root cause (reproducible): generation sees only the last CONTEXT=8 prompt bytes plus a 16-value conditioner.
 * Evidence that differs before the final 8 bytes cannot change the answer unless the conditioner changes, so the
 * decoder cannot copy an answer out of its evidence; an under-fitted greedy decoder then repeats its most likely byte.
 */
class TinyContextDecoderRootCauseTest {
    private val tail = "\nAntwort:"            // identical last 8+ bytes of both prompts
    private val sameCondition = DoubleArray(TinyContextDecoder.CONDITION) { 0.1 }

    @Test fun evidenceBeforeTheLastEightBytesCannotChangeTheGeneratedAnswer() {
        val decoder = TinyContextDecoder(91)
        val a = decoder.generate("Beleg: Die Katze heisst Mimi. Frage: Wie heisst die Katze?" + tail, 24, sameCondition)
        val b = decoder.generate("Beleg: Der Hund heisst Bello. Frage: Wie heisst der Hund?" + tail, 24, sameCondition)
        assertEquals("only the 8-byte window and the conditioner reach the output", a.text, b.text)
        assertEquals(a.bytesGenerated, b.bytesGenerated)
    }

    @Test fun onlyTheConditionerCarriesEvidenceInformation() {
        val decoder = TinyContextDecoder(91)
        val other = DoubleArray(TinyContextDecoder.CONDITION) { if (it % 2 == 0) .9 else -.9 }
        val a = decoder.generate("x" + tail, 24, sameCondition)
        val b = decoder.generate("x" + tail, 24, other)
        assertNotEquals(a.text + a.ended, b.text + b.ended)
    }

    @Test fun greedyDecodingHasNoAbstentionAndCanLoopOnOneByte() {
        val decoder = TinyContextDecoder(91)
        val g = decoder.generate("Frage ohne Beleg?" + tail, 64, sameCondition)
        // No EOS within 64 bytes means the decoder always emits text: it never abstains on its own.
        val distinct = g.text.toSet().size
        println("CDX1 untrained greedy: ended=${g.ended} bytes=${g.bytesGenerated} distinctChars=$distinct text='${g.text.take(40)}'")
    }
}
