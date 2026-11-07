package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedStreamingSpeechAssemblerTest {
    @Test fun onlyCompleteSentencesBecomeCandidates() {
        val a = ApprovedStreamingSpeechAssembler()
        assertTrue(a.append("Hallo Heinz, ich prüfe ").isEmpty())
        val first = a.append("das jetzt. Der zweite Satz")
        assertEquals(1, first.size)
        assertEquals("Hallo Heinz, ich prüfe das jetzt.", first.single().rawText)
        assertEquals("Hallo Heinz, ich prüfe das jetzt.", first.single().speakableText)
        assertTrue(a.append(" ist noch offen").isEmpty())
        assertEquals("Der zweite Satz ist noch offen", a.flushFinal()!!.rawText)
    }

    @Test fun fencedAndInlineCodeAreNeverSpokenRaw() {
        val a = ApprovedStreamingSpeechAssembler()
        val fence = CharArray(3) { 96.toChar() }.concatToString()
        val input = "Ergebnis folgt. " + fence + "kotlin\nval secret = 42.\n" + fence + "\nFertig."
        val out = a.append(input)
        assertEquals(2, out.size)
        assertEquals("Ergebnis folgt.", out[0].speakableText)
        assertFalse(out[1].speakableText.contains("val secret"))
        assertTrue(out[1].speakableText.contains("Codebeispiel ausgelassen"))
        assertTrue(out[1].speakableText.contains("Fertig"))
    }

    @Test fun abbreviationsAndDecimalsDoNotSplitEarly() {
        val a = ApprovedStreamingSpeechAssembler()
        assertTrue(a.append("Das gilt z. B. bei Version 3.5 und ").isEmpty())
        val out = a.append("bleibt so.")
        assertEquals(1, out.size)
        assertTrue(out.single().speakableText.contains("3.5"))
    }

    @Test fun boundedBufferFailsClosedInsteadOfGrowingWithoutLimit() {
        val a = ApprovedStreamingSpeechAssembler(maxBufferedChars = 256)
        assertTrue(a.append("x".repeat(250)).isEmpty())
        assertTrue(a.append("noch mehr ohne satzende").isEmpty())
        assertNull(a.flushFinal())
    }

    @Test fun unfinishedCodeAtFinalNeverBecomesSpeechCandidate() {
        val a = ApprovedStreamingSpeechAssembler()
        val fence = CharArray(3) { 96.toChar() }.concatToString()
        a.append("Hinweis. " + fence + "kotlin\nval token = PRIVATE")
        assertNull(a.flushFinal())
    }
}
