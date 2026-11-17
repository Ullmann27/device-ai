package at.heinz.deviceai.learning

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ContinuumEvidenceWindowTest {
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test fun lateRelevantEvidenceReplacesIrrelevantPrefixWithExactOffsets() {
        val source = "Navigation und allgemeiner Vorspann. ".repeat(30) +
            "Das Quarzkompassverfahren nutzt die Kennung ZIRKON-492 und arbeitet resonant. " +
            "Unbeteiligter Anhang. ".repeat(40)
        assertFalse(source.take(350).contains("ZIRKON-492"))
        val found = ContinuumEvidenceWindow.select("Was ist das Quarzkompassverfahren?", source, hash(source))!!
        assertTrue(found.text, found.text.contains("ZIRKON-492"))
        assertTrue(found.start > 350)
        assertEquals(found.text, source.substring(found.start, found.endExclusive))
        assertEquals(hash(source), found.sourceSha256)
        assertEquals(hash(found.text), found.quotationSha256)
        assertTrue(found.text.length <= 350)
    }

    @Test fun corruptionAndNoRelevantEvidenceAbstainWithoutEditingTheSource() {
        val text = "Diese Quelle beschreibt ausschließlich Zimmerpflanzen und deren Pflege."
        assertNull(ContinuumEvidenceWindow.select("Was ist Quarzkompassverfahren?", text, hash(text)))
        assertNull(ContinuumEvidenceWindow.select("Was ist Zimmerpflanzen?", text, "a".repeat(64)))
    }

    @Test fun multipleTermsAndUnicodeKeepAnExactBoundedQuotation() {
        val text = "😀 Allgemeiner Anfang. ".repeat(40) +
            "Züricher Quarzforschung beschreibt eine neue Resonanzmessung. " + "Datenende. ".repeat(40)
        val quote = ContinuumEvidenceWindow.select("Was ist die Züricher Resonanzmessung?", text, hash(text), 100)!!
        assertTrue(quote.text.contains("Resonanzmessung"))
        assertEquals(quote.text, text.substring(quote.start, quote.endExclusive))
        assertEquals(quote.text, text.toByteArray(Charsets.UTF_8)
            .copyOfRange(quote.utf8Start, quote.utf8EndExclusive).toString(Charsets.UTF_8))
        assertFalse(quote.text.first().isLowSurrogate())
        assertFalse(quote.text.last().isHighSurrogate())
    }

    @Test fun denseRepeatedTopicDoesNotHideTheDistinctiveLateTerm() {
        val text = "Quarzmessung allgemeiner Eintrag. ".repeat(120) +
            "Quarzmessung verwendet eine Resonanzkaskade für präzise Ergebnisse."
        val quote = ContinuumEvidenceWindow.select("Was ist Quarzmessung Resonanzkaskade?", text, hash(text))!!
        assertTrue(quote.text.contains("Resonanzkaskade"))
        assertEquals(quote.text, text.substring(quote.start, quote.endExclusive))
    }

    @Test fun aTermLongerThanTheQuotationBudgetCannotJustifyATruncatedCitation() {
        val term="Quarz".repeat(27)
        val text="$term bezeichnet ein synthetisches Verfahren mit einer sehr langen Kennung."
        assertNull(ContinuumEvidenceWindow.select("Was ist $term?", text, hash(text), 80))
    }
    @Test fun directGermanPluralKeepsDev5bArchiveSourceReachable() {
        // DEV5b asks "Was ist Thunfisch?", but the immutable source excerpt
        // says "Thunfische sind ...". Exact-only matching used to discard it.
        val text = "Thunfische sind große Raubfische der Hochsee."
        val quote = ContinuumEvidenceWindow.select("Was ist Thunfisch?", text, hash(text))
        assertNotNull(quote)
        assertTrue(quote!!.text.contains("Thunfische"))
        assertEquals(text.substring(quote.start, quote.endExclusive), quote.text)
        assertEquals(hash(quote.text), quote.quotationSha256)
        assertEquals(hash(text), quote.sourceSha256)
    }

    @Test fun unrelatedOrOnlyPartiallySimilarWordsMustStillAbstain() {
        val source = "Eine Thunfischerei betreibt Fischfang in bestimmten Küstenregionen."
        assertNull(ContinuumEvidenceWindow.select("Was ist Thunfisch?", source, hash(source)))
        val different = "Die Fische leben in tiefen Gewässern der Nordsee."
        assertNull(ContinuumEvidenceWindow.select("Was ist Thunfisch?", different, hash(different)))
        val swapped = "Thunfische sind große Raubfische der Hochsee."
        assertNull(ContinuumEvidenceWindow.select("Was ist Thunfisch?", swapped, "0".repeat(64)))
    }

}
