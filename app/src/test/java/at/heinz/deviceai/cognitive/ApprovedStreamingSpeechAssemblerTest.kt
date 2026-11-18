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
        val input = "Ergebnis folgt. " + fence + "kotlin\nval secret = 42.\n" + fence + "\nFertig. "
        val out = a.append(input)
        assertEquals(2, out.size)
        assertEquals("Ergebnis folgt.", out[0].speakableText)
        assertFalse(out[1].speakableText.contains("val secret"))
        assertTrue(out[1].speakableText.contains("Codebeispiel ausgelassen"))
        assertTrue(out[1].speakableText.contains("Fertig"))
    }

    @Test fun codeFenceSplitAcrossDeltasCannotLeakCodeAsSpeech() {
        val a = ApprovedStreamingSpeechAssembler()
        val fence = CharArray(3) { 96.toChar() }.concatToString()
        assertTrue(a.append("Vorher. " + fence + "kotlin\nval secret = ").size == 1)
        val out = a.append("42.\n" + fence + "\nDanach. ")
        assertEquals(1, out.size)
        assertFalse(out.single().speakableText.contains("val secret"))
        assertTrue(out.single().speakableText.contains("Codebeispiel ausgelassen"))
        assertTrue(out.single().speakableText.contains("Danach"))
    }

    @Test fun abbreviationsAndDecimalsDoNotSplitEarly() {
        val a = ApprovedStreamingSpeechAssembler()
        assertTrue(a.append("Das gilt z. B. bei Version 3.5 und ").isEmpty())
        val out = a.append("bleibt so. ")
        assertEquals(1, out.size)
        assertTrue(out.single().speakableText.contains("3.5"))
    }

    @Test fun dotOnlyDeltaWaitsForDecimalLookahead() {
        val a = ApprovedStreamingSpeechAssembler()
        assertTrue(a.append("Es kostet 3").isEmpty())
        assertTrue(a.append(".").isEmpty())
        val out = a.append("5 Euro. ")
        assertEquals(1, out.size)
        assertEquals("Es kostet 3.5 Euro.", out.single().rawText)
    }

    @Test fun abbreviationFragmentWaitsForFollowingDelta() {
        val a = ApprovedStreamingSpeechAssembler()
        assertTrue(a.append("Das gilt z.").isEmpty())
        val out = a.append(" B. weiterhin. ")
        assertEquals(1, out.size)
        assertEquals("Das gilt z. B. weiterhin.", out.single().rawText)
    }

    @Test fun abbreviationFragmentWithTrailingSpaceWaitsForFollowingDelta() {
        val a = ApprovedStreamingSpeechAssembler()
        assertTrue(a.append("Das gilt z. ").isEmpty())
        val out = a.append("B. weiterhin. ")
        assertEquals(1, out.size)
        assertEquals("Das gilt z. B. weiterhin.", out.single().rawText)
    }

    @Test fun finalFlushOwnsPunctuationAtEndOfBuffer() {
        val a = ApprovedStreamingSpeechAssembler()
        assertTrue(a.append("Fertig.").isEmpty())
        assertEquals("Fertig.", a.flushFinal()!!.rawText)
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

    private fun raw(vararg deltas: String): List<String> {
        val a = ApprovedStreamingSpeechAssembler()
        return deltas.flatMap { a.append(it) }.map { it.rawText } + listOfNotNull(a.flushFinal()?.rawText)
    }

    @Test fun ordinalsAndDatesDoNotEndASentence() {
        assertEquals(listOf("Wir treffen uns am 3. Mai in Wien.", "Passt das?"),
            raw("Wir treffen uns am 3. Mai in Wien. Passt das?"))
        assertEquals(listOf("Am 17.11. war der 2. Versuch erfolgreich.", "Gut so."),
            raw("Am 17.11. war der 2. Versuch erfolgreich. Gut so."))
        // The ordinal is still undecided while its dot is the last character received.
        val a = ApprovedStreamingSpeechAssembler()
        assertTrue(a.append("Der Termin ist am 12.").isEmpty())
        assertTrue(a.append(" ").isEmpty())
        assertEquals(listOf("Der Termin ist am 12. Juni."), a.append("Juni. Danach").map { it.rawText })
    }

    @Test fun aLongerNumberCanEndASentence() {
        assertEquals(listOf("Das war im Jahr 2026.", "Danach kam nichts mehr."),
            raw("Das war im Jahr 2026. Danach kam nichts mehr."))
    }

    @Test fun numberedListItemsKeepTheirNumberAndTheirText() {
        assertEquals(listOf("So geht es:", "1. Wasser kochen.", "2. Tee ziehen lassen.", "3. Trinken"),
            raw("So geht es:\n1. Wasser kochen.\n2. Tee ziehen lassen.\n3. Trinken"))
    }

    @Test fun bulletItemsWithoutPunctuationAreSeparateCandidates() {
        assertEquals(listOf("Du brauchst:", "- zwei Eier", "- etwas Mehl", "- eine Prise Salz", "Mehr nicht."),
            raw("Du brauchst:\n- zwei Eier\n- etwas Mehl\n", "- eine Prise Salz\n\nMehr nicht."))
    }

    @Test fun anOrdinaryLineBreakInsideAParagraphIsNotABoundary() {
        assertEquals(listOf("Das ist ein Satz\nüber zwei Zeilen."), raw("Das ist ein Satz\nüber zwei Zeilen. "))
    }

    @Test fun sentenceMarksInsideQuotesAndBracketsDoNotSplit() {
        assertEquals(listOf("Er rief „Stopp! Halt!“ und blieb stehen.", "Dann war Ruhe."),
            raw("Er rief „Stopp! Halt!“ und blieb stehen. Dann war Ruhe."))
        assertEquals(listOf("Das Gerät (Modell 7. Es ist neu.) bleibt aus.", "Gut."),
            raw("Das Gerät (Modell 7. Es ist neu.) bleibt aus. Gut."))
        assertEquals(listOf("She said \"Wait. Not yet.\" and left.", "Fine."),
            raw("She said \"Wait. Not yet.\" and left. Fine."))
    }

    @Test fun aClosingQuoteBelongsToTheSentenceItEnds() {
        assertEquals(listOf("Sie sagte: „Komm jetzt!“", "Dann ging sie."),
            raw("Sie sagte: „Komm jetzt!“ Dann ging sie."))
        assertEquals(listOf("„Komm jetzt!“, sagte sie.", "Ende."), raw("„Komm jetzt!“, sagte sie. Ende."))
    }

    @Test fun aStrayOpenerCannotSuppressBoundariesForever() {
        val out = raw("Er sagte „hallo. " + "Und noch ein Satz. ".repeat(30) + "Schluss.")
        assertTrue("an unclosed quote must not swallow the reply: ${out.size}", out.size > 3)
        assertEquals("Schluss.", out.last())
        assertEquals(listOf("Schade :( Das klappt nicht.", "Weiter."), raw("Schade :( Das klappt nicht. Weiter."))
    }

    @Test fun commonGermanAbbreviationsAreNotSentenceEnds() {
        for (abbreviation in listOf("z. B.", "z.B.", "d. h.", "u. a.", "bzw.", "ca.", "vgl.", "Nr.", "Dr.", "evtl.",
            "inkl.", "Mio.", "St.", "bspw.", "sog.")) {
            assertEquals(abbreviation, listOf("Das gilt $abbreviation Wien und Graz.", "Ende."),
                raw("Das gilt $abbreviation Wien und Graz. Ende."))
        }
    }

    @Test fun anAbbreviationIsOnlyRecognisedAsAWholeWord() {
        // "Start." ends with the letters of "Art." and "Luca." with those of "ca.".
        assertEquals(listOf("Das war ein guter Start.", "Jetzt kommt Luca.", "Er hilft."),
            raw("Das war ein guter Start. Jetzt kommt Luca. Er hilft."))
    }

    @Test fun versionsTimesAndAddressesStayInOneSentence() {
        assertEquals(listOf("Version 3.5 läuft seit 15.30 Uhr auf 192.168.0.1 stabil.", "Fertig."),
            raw("Version 3.5 läuft seit 15.30 Uhr auf 192.168.0.1 stabil. Fertig."))
    }

    @Test fun everyDeltaSplitYieldsTheSameSentences() {
        val text = "Am 3. Mai, z. B. um 15.30 Uhr, rief er „Los! Jetzt!“ und ging. " +
            "Das kostet ca. 3.5 Euro.\n- erstens\n- zweitens\n\nDas war alles."
        val whole = raw(text)
        assertEquals(listOf("Am 3. Mai, z. B. um 15.30 Uhr, rief er „Los! Jetzt!“ und ging.", "Das kostet ca. 3.5 Euro.",
            "- erstens", "- zweitens", "Das war alles."), whole)
        assertEquals("one character per delta", whole, raw(*text.map { it.toString() }.toTypedArray()))
        for (size in listOf(2, 3, 5, 7, 11)) assertEquals("delta size $size", whole, raw(*text.chunked(size).toTypedArray()))
    }

    @Test fun overflowKeepsSequenceNumbersMonotonicAndIsReported() {
        val a = ApprovedStreamingSpeechAssembler(maxBufferedChars = 256)
        assertEquals(1, a.append("Erster Satz. ").single().sequence)
        assertFalse(a.overflowed)
        assertTrue(a.append("y".repeat(250)).isEmpty())
        assertTrue(a.append("z".repeat(20)).isEmpty())
        assertTrue(a.overflowed)
        assertEquals("a later sentence is never mistaken for an earlier one", 2, a.append("Neuer Satz. ").single().sequence)
        a.reset()
        assertFalse(a.overflowed)
        assertEquals(1, a.append("Wieder vorn. ").single().sequence)
    }

    @Test fun aLongSentenceIsKeptCompleteInsteadOfBeingDropped() {
        val a = ApprovedStreamingSpeechAssembler()
        val long = (1..60).joinToString(", ") { "Teil $it der langen Aufzählung" } + "."
        assertTrue("fixture exceeds two speech pieces: ${long.length}", long.length > 1_200)
        val candidate = a.append("$long Ende").single()
        assertEquals(long, candidate.rawText)
        assertEquals(long, candidate.speakableText)
    }

    @Test fun pendingTextIsWhatFollowsTheLastProposedBoundary() {
        val a = ApprovedStreamingSpeechAssembler()
        a.append("Erster Satz. Zweiter")
        assertEquals("Zweiter", a.pendingText().trim())
    }
}
