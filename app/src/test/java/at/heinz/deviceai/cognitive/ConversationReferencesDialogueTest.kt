package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.ConversationReferences.Intent
import at.heinz.deviceai.cognitive.ConversationReferencesTest.Companion.ARGON
import at.heinz.deviceai.cognitive.ConversationReferencesTest.Companion.MIETE
import at.heinz.deviceai.cognitive.ConversationReferencesTest.Companion.NOTIZ
import at.heinz.deviceai.cognitive.ConversationReferencesTest.Companion.doc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code64 owner scenario through the session context the Activity uses: „Öffne das PDF Argon4.“ → „Schau auf Seite
 * drei.“ → „Was bedeutet der zweite Absatz?“ → second document → unrelated question → comparison → correction → third
 * document. Every model-bound turn is checked by the passage text that reaches the prompt. HOST, pure JVM.
 */
class ConversationReferencesDialogueTest {
    private val session = "chat-1"
    private val context = LocalConversationContext()

    private fun open(name: String, pages: List<String>) = doc(name, pages, session).also { context.recordDocument(session, it) }

    /** A fixed reply (no model) for [request]. */
    private fun reply(request: String, correction: Boolean = false): String {
        val route = context.routeDocumentReference(session, request, correctionOnly = correction)
        assertNotNull("„$request“ was not resolved", route)
        assertNotNull("„$request“ should be answered without the model", route!!.reply)
        context.recordNonModel(session, request, "(aus der gelesenen PDF beantwortet; kein Modelltext, keine Gedächtnisänderung)")
        return route.reply!!
    }

    /** A model turn for [request]: the resolution and the document block of its prompt; then the turn is recorded. */
    private fun model(request: String): Pair<ConversationReferences.Resolution.Resolved, String> {
        val route = context.routeDocumentReference(session, request)
        assertNotNull("„$request“ was not resolved", route)
        assertNull("„$request“ needs the model: ${route!!.reply}", route.reply)
        val block = context.documentPromptBlock(session, request)
        assertTrue("block has ${block.toByteArray(Charsets.UTF_8).size} bytes",
            block.toByteArray(Charsets.UTF_8).size <= ConversationReferences.MAX_BLOCK_BYTES)
        context.record(session, request, "Antwort des Modells.")
        assertEquals("the frozen passage ends with its turn", "", context.documentPromptBlock(session, "anderer Wortlaut"))
        return route.resolved!! to block
    }

    @Test fun theOwnerScenarioKeepsEveryReferenceUnambiguousAcrossSixteenTurns() {
        // 1 · „Öffne das PDF Argon4.“ – Android's picker; the read file becomes the session reference.
        assertNull(context.routeDocumentReference(session, "Öffne das PDF Argon4."))
        val argon = open("Argon4.pdf", ARGON)

        // 2 · page focus, no model.
        val page = reply("Schau auf Seite drei.")
        assertTrue(page, page.startsWith("Mein Bezug ist jetzt Seite 3 von „Argon4.pdf“"))

        // 3 · the paragraph is counted on the focused page.
        val (second, secondBlock) = model("Was bedeutet der zweite Absatz?")
        assertSame(argon, second.document); assertEquals(3, second.page); assertEquals(2, second.paragraph)
        assertEquals("Bitemporale Einträge behalten den Zeitpunkt der Aussage und den Zeitpunkt der Gültigkeit getrennt.", second.text)
        assertTrue(secondBlock, secondBlock.contains("Seite 3 · Absatz 2 von 3\n" + second.text + "\n"))
        assertFalse(secondBlock, secondBlock.contains("Widersprüche ohne Markierung"))

        // 4 · a second document; the first stays known.
        val miete = open("Mietvertrag_2024.pdf", MIETE)
        assertSame(miete, context.document(session))
        assertEquals(listOf(argon, miete), context.documents(session))

        // 5 · no page chosen in the new document: page 1 is taken and said.
        val (notice, noticeBlock) = model("Was heißt der zweite Absatz für mich als Mieter?")
        assertSame(miete, notice.document); assertEquals(1, notice.page); assertTrue(notice.pageAssumed)
        assertEquals("Die Kündigungsfrist beträgt drei Monate zum Monatsende.", notice.text)
        assertTrue(noticeBlock, noticeBlock.contains("(angenommen: keine andere Seite gewählt)"))

        // 6 · an unrelated question is none of the registry's business and changes nothing.
        assertNull(context.routeDocumentReference(session, "Wie wird das Wetter morgen in Wien?"))
        context.record(session, "Wie wird das Wetter morgen in Wien?", "Dazu habe ich keine aktuellen Daten.")
        assertEquals("", context.documentPromptBlock(session, "Wie wird das Wetter morgen in Wien?"))

        // 7 · comparison: „das“ is the passage in focus, „das erste Dokument“ the file opened first.
        val (comparison, comparisonBlock) = model("Vergleich das mit dem ersten Dokument.")
        assertEquals(Intent.COMPARISON, comparison.intent)
        assertSame(miete, comparison.document); assertEquals(1, comparison.page); assertEquals(2, comparison.paragraph)
        assertEquals("Die Kündigungsfrist beträgt drei Monate zum Monatsende.", comparison.text)
        assertSame(argon, comparison.comparedWith!!.document); assertNull(comparison.comparedWith!!.page)
        assertTrue(comparisonBlock, comparisonBlock.contains("Bezug A: „Mietvertrag_2024.pdf“ · 2. Dokument dieser Sitzung · 2 Seiten · Seite 1 · Absatz 2 von 2\n"))
        assertTrue(comparisonBlock, comparisonBlock.contains("Bezug B (Vergleich): „Argon4.pdf“ · 1. Dokument dieser Sitzung · 4 Seiten · gelesener Text ab Seite 1"))
        assertTrue(comparisonBlock, comparisonBlock.contains("Argon4 beschreibt ein lokales Gedächtnis mit zeitlicher Ordnung."))
        assertSame("a comparison does not move the owner", miete, context.document(session))

        // 8 · correction: with two documents „das andere“ is exactly one – and its page focus is still known.
        val corrected = reply("Nein, ich meinte das andere Dokument.", correction = true)
        assertTrue(corrected, corrected.startsWith("Mein Bezug ist jetzt „Argon4.pdf“ (1. Dokument dieser Sitzung, 4 Seiten)."))
        assertTrue(corrected, corrected.contains("Zuletzt gewählt war dort Seite 3."))
        assertSame(argon, context.document(session))

        // 9 · the next paragraph question is about the corrected document and its remembered page.
        val (third, _) = model("Was folgt aus dem dritten Absatz?")
        assertSame(argon, third.document); assertEquals(3, third.page); assertEquals(3, third.paragraph)
        assertEquals("Widersprüche ohne Markierung werden zurückgefragt und nie still entschieden.", third.text)

        // 10 · „der Absatz davor“ in the owner's words, relative to the focus.
        val (previous, _) = model("Und was bedeutet der vorige Absatz?")
        assertEquals(2, previous.paragraph)
        assertEquals(second.text, previous.text)

        // 11 · a third document makes „das andere Dokument“ ambiguous: asked back, not guessed.
        val notiz = open("Notiz.pdf", NOTIZ)
        val asked = reply("Vergleich das mit dem anderen Dokument.")
        assertTrue(asked, asked.startsWith("Welches Dokument meinst du?"))
        assertTrue(asked, asked.contains("„Argon4.pdf“ (1. Dokument)") && asked.contains("„Mietvertrag_2024.pdf“ (2. Dokument)"))
        assertSame(notiz, context.document(session))

        // 12 · the answer to the question back, by name.
        val (named, namedBlock) = model("Vergleich das mit dem Mietvertrag.")
        assertSame(notiz, named.document)
        assertSame(miete, named.comparedWith!!.document)
        assertTrue(namedBlock, namedBlock.contains("Einkauf: Brot, Milch, Kaffee."))

        // 13 · a page that does not exist is reported.
        assertEquals("„Notiz.pdf“ hat nur 1 Seite – nach Seite 1 kommt keine weitere.", reply("Nächste Seite."))
        assertEquals("„Argon4.pdf“ hat nur 4 Seiten – eine Seite 9 gibt es nicht.", reply("Schau im ersten Dokument auf Seite 9."))
        assertSame("a failed reference moves nothing", notiz, context.document(session))

        // 14 · two explicit operands in one sentence.
        val (both, _) = model("Was unterscheidet das erste Dokument vom zweiten?")
        assertSame(argon, both.document); assertSame(miete, both.comparedWith!!.document)

        // 15 · back to the second document and its last page.
        val back = reply("Zurück zum zweiten Dokument, letzte Seite.")
        assertTrue(back, back.startsWith("Mein Bezug ist jetzt Seite 2 von „Mietvertrag_2024.pdf“"))

        // 16 · a quotation needs no model.
        assertEquals("Absatz 1 auf Seite 2 von „Mietvertrag_2024.pdf“ lautet laut gelesenem Text:\n" +
            "„Die Kaution beträgt zwei Monatsmieten.“", reply("Was steht im ersten Absatz?"))
    }

    @Test fun aDocumentQuestionWithoutAResolvedPassageKeepsTheEarlierExcerptBlock() {
        open("Argon4.pdf", ARGON)
        assertNull(context.routeDocumentReference(session, "Welche PDF hast du gerade geöffnet?"))
        val block = context.documentPromptBlock(session, "Erkläre mir die Überschrift.")
        assertTrue(block, block.contains("Datei: „Argon4.pdf“ · 4 Seiten") && block.contains("[Seite 1]"))
        assertEquals("", context.documentPromptBlock(session, "Wie spät ist es?"))
    }

    @Test fun theRegistryIsClearedExactlyWhereTheSingleReferenceWas() {
        val argon = open("Argon4.pdf", ARGON)
        open("Mietvertrag_2024.pdf", MIETE)
        // A topic reset keeps the open files …
        context.record(session, "Hallo", "Hallo!")
        context.clear()
        assertEquals(2, context.documents(session).size)
        assertSame(argon, context.documents(session).first())
        // … another session never sees them, and they are gone for good once it took over …
        assertEquals(emptyList<OwnerDocumentDialogue.Reference>(), context.documents("chat-2"))
        assertEquals(ConversationReferences.NOTHING_OPENED,
            context.routeDocumentReference("chat-2", "Schau auf Seite drei.")!!.reply)
        assertEquals(2, context.documents(session).size)
        context.record("chat-2", "Hallo", "Hallo!")
        assertEquals(emptyList<OwnerDocumentDialogue.Reference>(), context.documents(session))
        assertNull(context.document(session))
        // … and a real exit forgets everything.
        open("Argon4.pdf", ARGON)
        context.forgetForExit()
        assertEquals(emptyList<OwnerDocumentDialogue.Reference>(), context.documents(session))
    }

    @Test fun theSavedStatePutsEveryDocumentBackAtItsPlace() {
        val argon = open("Argon4.pdf", ARGON)
        val miete = open("Mietvertrag_2024.pdf", MIETE)
        context.routeDocumentReference(session, "Geh im ersten Dokument auf Seite 3.")
        val marks = context.documentMarks(argon)
        val earlier = context.earlierDocumentsForState(session)
        assertEquals(listOf("Mietvertrag_2024.pdf"), earlier.map { it.reference.name })

        val recreated = LocalConversationContext()
        earlier.forEach { recreated.restoreDocument(session, it.reference, it.marks) }
        recreated.restoreDocument(session, argon, marks)
        assertSame(argon, recreated.document(session))
        assertEquals(listOf(argon, miete), recreated.documents(session))
        val resolved = recreated.routeDocumentReference(session, "Was bedeutet der zweite Absatz?")!!.resolved!!
        assertEquals(3, resolved.page)
        assertEquals("Mietvertrag_2024.pdf",
            recreated.routeDocumentReference(session, "Was steht im anderen Dokument auf Seite 2?")!!.resolved!!.document.name)
    }
}
