package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDraftPromptTest {
    @Test fun aShortDocumentGoesInCompletelyBetweenTheMarkers() {
        val text = "Projektübersicht Continuum. Ziel: ein lokales Cockpit auf dem Fold7."
        val prompt = requireNotNull(DocumentDraftPrompt.build("Projektübersicht.pdf", text))
        assertTrue(prompt.complete)
        assertEquals(text.length, prompt.coveredChars)
        assertTrue(prompt.question.contains("<<<DOKUMENT\n$text\nDOKUMENT>>>"))
        assertTrue("only the document's text is the source", prompt.question.contains("ausschließlich Aussagen aus dem Text"))
        assertEquals("Entwurf: Zusammenfassung von „Projektübersicht.pdf“", prompt.request)
    }

    @Test fun aLongDocumentFitsTheCompleteTaskBudgetAndSaysHowMuchItCovers() {
        val text = (1..2_000).joinToString(" ") { "Abschnitt$it Größenordnung" }
        val prompt = requireNotNull(DocumentDraftPrompt.build("Bericht.pdf", text))
        assertTrue("fits the task budget", LocalChatPromptBudget.acceptsCompleteTask(prompt.question))
        assertFalse(prompt.complete)
        assertEquals(text.length, prompt.totalChars)
        val excerpt = prompt.question.substringAfter("<<<DOKUMENT\n").substringBefore("\nDOKUMENT>>>")
        assertEquals(excerpt.length, prompt.coveredChars)
        assertTrue("a prefix of the document, nothing else", text.startsWith(excerpt))
        assertTrue("cut between words", text[excerpt.length] == ' ')
    }

    @Test fun pageOneOnlyDraftExplicitlyLimitsThePromptAndDoesNotUseOtherPages() {
        val pageOne = "Seite eins: Der Rechenfehler wurde korrigiert."
        val nextPage = "Seite zwei: andere Zahlen"
        val prompt = requireNotNull(DocumentDraftPrompt.build("Prüfung.pdf", pageOne,
            firstPageOnly = true))
        assertTrue(prompt.firstPageOnly)
        assertTrue(prompt.complete)
        assertTrue(prompt.question.contains("ausschließlich Seite 1"))
        assertTrue(prompt.question.contains("<<<DOKUMENT\n" + pageOne + "\nDOKUMENT>>>"))
        assertFalse(prompt.question.contains(nextPage))
        assertEquals(pageOne.length, prompt.coveredChars)
    }

    @Test fun noTextNoDraft() {
        assertNull(DocumentDraftPrompt.build("Scan.pdf", "  \n "))
    }
}
