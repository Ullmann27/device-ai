package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M5 milestone 3 – speech -> composer context handoff. Dictation must insert
 * the recognised words at the owner's cursor; it must never silently replace
 * (and thereby lose) a message the owner already typed or restored.
 */
class SpeechDictationDraftTest {
    private fun merge(draft: String, start: Int, end: Int, spoken: String) =
        LocalSpeechInputPolicy.mergeDictation(draft, start, end, spoken)

    @Test fun emptyComposerReceivesTranscript() {
        val merged = merge("", 0, 0, "Wie spät ist es")
        assertEquals("Wie spät ist es", merged.text)
        assertEquals(merged.text.length, merged.cursor)
    }

    @Test fun typedDraftIsKeptAndDictationContinuesAtCursor() {
        val draft = "Erinnere mich morgen an"
        val merged = merge(draft, draft.length, draft.length, "den Zahnarzt um 9 Uhr")
        assertEquals("Erinnere mich morgen an den Zahnarzt um 9 Uhr", merged.text)
        assertEquals(merged.text.length, merged.cursor)
    }

    @Test fun dictationInsertsInTheMiddleWithSingleSpaces() {
        val draft = "Schreib Papa komme gleich"
        val cursor = "Schreib Papa".length
        val merged = merge(draft, cursor, cursor, "dass ich")
        assertEquals("Schreib Papa dass ich komme gleich", merged.text)
        assertEquals("Schreib Papa dass ich".length, merged.cursor)
    }

    @Test fun selectedTextIsReplacedByDictation() {
        val draft = "Termin am Montag bestätigen"
        val from = draft.indexOf("Montag")
        val merged = merge(draft, from + "Montag".length, from, "Dienstag")
        assertEquals("Termin am Dienstag bestätigen", merged.text)
        assertEquals("Termin am Dienstag".length, merged.cursor)
    }

    @Test fun unknownSelectionAppendsInsteadOfOverwriting() {
        val merged = merge("Notiz:", -1, -1, "Milch kaufen")
        assertEquals("Notiz: Milch kaufen", merged.text)
        assertEquals(merged.text.length, merged.cursor)
    }
}
