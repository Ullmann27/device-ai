package at.heinz.deviceai

import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.ARGON
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_2
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_3
import at.heinz.deviceai.OwnerMissionHarness.Companion.BRIEFING
import at.heinz.deviceai.OwnerMissionHarness.Companion.NEWS_PDF
import at.heinz.deviceai.OwnerMissionHarness.Companion.WORD_2
import at.heinz.deviceai.cognitive.OwnerDeliverable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Sentences with several orders after the architecture review of 19.11.2026, end to end on the host: the productive
 * planner and director with the productive lanes behind them ([OwnerMissionHarness]). The five sentences of the review
 * and the owner's goal sentence are pinned with what the app does with them TODAY – what runs, what it says it cannot
 * do yet – and that nothing is reported as done that was not done, nothing is dropped without a word and nothing is
 * added that he did not order.
 *
 * Device behaviour – storage, PDF reader, model, WhatsApp, the calendar – is NOT_RUN here.
 */
class OwnerMissionMultiStepFlowTest {
    private val goal = "Schau bitte meine E-Mails durch, fasse mir das Wichtigste zusammen, mach daraus ein PDF und schick meinem " +
        "Vater auf WhatsApp, dass ich mich später melde."
    private val two = "Öffne bitte WhatsApp und schreib meinem Vater guten Morgen."
    private val three = "Suche die letzte Mail von meinem Anwalt, fasse sie zusammen, erstelle daraus ein PDF und öffne es."
    private val five = "Such die PDF-Datei Argon4, öffne sie, analysiere Seite zwei und fasse sie zusammen."

    /** A chat with „Argon4.pdf“ in Downloads and a model that summarises the page it is given. */
    private fun argon(): OwnerMissionHarness = OwnerMissionHarness().apply {
        deskHost.put("Argon4.pdf", pages = ARGON)
        deskHost.model = { prompt -> OwnerWorkGeneration(if (prompt.contains("Seite drei")) SUMMARY_3 else SUMMARY_2, null, true) }
        toolsHost.model = { OwnerWorkGeneration(BRIEFING, null, true) }
    }

    /** Nothing was searched, opened, written, mailed or sent, and no model was asked for anything. */
    private fun assertNothingRan(h: OwnerMissionHarness) {
        assertEquals(0, h.deskHost.scans)
        assertTrue(h.deskHost.shown.isEmpty() && h.deskHost.prompts.isEmpty())
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty() && h.toolsHost.consents.isEmpty())
        assertTrue(h.sendHost.device.opened.isEmpty() && h.sendHost.device.pressed.isEmpty())
        assertTrue(h.planPrompts.isEmpty() && h.chainCalls.isEmpty())
        assertFalse(h.mission.active)
        assertTrue(h.said.toString(), h.said.none { it.contains("Erledigt") || it.contains("Fertig") })
    }

    private fun documentText(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return ""
    }

    // ── Sentence 5: find → open → read page two → summary in the chat, and no file ──────────────────────────

    @Test fun findOpenAndSummariseAPageRunsAsOneOrderAndWritesNoFile() {
        val h = argon()
        assertEquals("MISSION", h.owner(five))
        assertEquals("Ich mache das in zwei Schritten: 1. Das PDF „Argon4“ suchen und öffnen. 2. Seite 2 zusammenfassen.", h.missionSaid.first())
        assertEquals(listOf("MISSION", "DESK", "DESK", "MISSION"), h.said.map { it.substringBefore(':') })
        assertEquals("Argon4.pdf", h.deskHost.shown.single().name)
        // Page two was really read: exactly its text went into the one model task.
        val prompt = h.deskHost.prompts.single()
        assertTrue(prompt, prompt.contains("Seite zwei erklärt den Abruf") && !prompt.contains("Seite eins beschreibt"))
        assertEquals("DESK:Seite 2 von „Argon4.pdf“ – Zusammenfassung:\n\n$SUMMARY_2\n\nGespeichert habe ich nichts.", h.said[2])
        assertEquals("Fertig – beide Schritte sind erledigt. 1. „Argon4.pdf“ geöffnet (Ordner Download). 2. Seite 2 von „Argon4.pdf“ " +
            "zusammengefasst und hier gezeigt – gespeichert habe ich nichts.", h.lastSaid)
        // No file nobody ordered, no question for a format, no mail.
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
        assertTrue(h.said.none { it.contains("Als was soll ich") })
        assertEquals(listOf("1/2 Das PDF „Argon4“ suchen und öffnen:DONE", "2/2 Seite 2 zusammenfassen:DONE"), h.steps)
        assertEquals(true, h.stageEnded)
        // The summary is a typed result: the page summary of that document, with its source line.
        val result = h.presented.single()
        assertEquals(OwnerDeliverable.Kind.PAGE_SUMMARY, result.kind)
        assertEquals("Zusammenfassung Argon4 Seite 2", result.title)
        assertEquals(listOf(SUMMARY_2), result.paragraphs)
        assertTrue(result.note.orEmpty(), result.note.orEmpty().startsWith("Quelle: „Argon4.pdf“, Seite 2 von 4"))
        assertEquals(listOf(five), h.toolsHost.lines("USER:"))
    }

    /** Done means: the page's text was read and the summary accepted. Anything less ends the sequence without „Fertig“. */
    @Test fun aPageWithoutTextOrASummaryTheModelDidNotFinishIsNotReportedAsDone() {
        val scan = OwnerMissionHarness().apply { deskHost.put("Argon4.pdf", pages = listOf("Seite eins.", "", "Seite drei.")) }
        assertEquals("MISSION", scan.owner(five))
        assertEquals("Die Auftragsfolge endet bei Schritt 2 von 2. Erledigt: 1. „Argon4.pdf“ geöffnet (Ordner Download). Nicht geklappt: " +
            "2. Seite 2 zusammenfassen – Seite 2 von „Argon4.pdf“ hat keinen auslesbaren Text – zum Beispiel ein Scan.", scan.lastSaid)
        assertTrue(scan.deskHost.prompts.isEmpty() && scan.presented.isEmpty())
        val cut = argon().apply { deskHost.model = { OwnerWorkGeneration(SUMMARY_2.take(60), null, false) } }
        assertEquals("MISSION", cut.owner(five))
        assertTrue(cut.lastSaid, cut.lastSaid.startsWith("Die Auftragsfolge endet bei Schritt 2 von 2."))
        assertTrue(cut.presented.isEmpty() && cut.downloads().isEmpty())
        assertTrue(cut.said.none { it.contains("Fertig") })
        // A page the PDF does not have.
        val short = argon()
        assertEquals("MISSION", short.owner("Öffne das PDF Argon4 und fass Seite 9 zusammen"))
        assertTrue(short.lastSaid, short.lastSaid.contains("Nicht geklappt: 2. Seite 9 zusammenfassen – „Argon4.pdf“ hat nur 4 Seiten"))
    }

    /** Said alone the order keeps its route (LIB-160): only a sequence hands it to the desk. */
    @Test fun aPageSummaryAloneIsNotTheDesksOrder() {
        val h = argon()
        assertEquals("DESK", h.owner("Öffne das PDF Argon4"))
        assertEquals("OTHER", h.owner("Fass Seite 2 zusammen"))
        assertEquals("OTHER", h.owner("Analysiere Seite zwei und fasse sie zusammen"))
        assertTrue(h.deskHost.prompts.isEmpty() && h.presented.isEmpty())
        // With a save it is the desk's page order, exactly as before.
        assertEquals("DESK", h.owner("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals(listOf(WORD_2), h.downloads())
    }

    @Test fun aCorrectedPageIsSummarisedAgainAndStillNothingIsSaved() {
        val h = argon()
        h.owner(five)
        assertEquals("MISSION", h.owner("Ich meinte Seite 3"))
        assertTrue(h.said.any { it == "MISSION:Verstanden – Seite 3 statt Seite 2. Ich mache Schritt 2 damit neu." })
        assertEquals(2, h.deskHost.prompts.size)
        assertTrue(h.said.any { it.startsWith("DESK:Seite 3 von „Argon4.pdf“ – Zusammenfassung:\n\n$SUMMARY_3") })
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – mit deiner Korrektur sind beide Schritte erledigt."))
        assertTrue(h.downloads().isEmpty())
    }

    @Test fun stopWhileThePageIsSummarisedEndsTheSequenceAndShowsNothing() {
        val h = argon()
        h.deskHost.duringGeneration = { assertTrue(h.interrupt("Stopp")) }
        assertEquals("MISSION", h.owner(five))
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Die Auftragsfolge ist gestoppt."))
        assertTrue(h.presented.isEmpty() && h.downloads().isEmpty())
        assertTrue(h.said.none { it.contains("Zusammenfassung:") })
    }

    /**
     * „Speichere das als Word“ afterwards: the desk hands the shown summary on through its host. In the Activity that
     * hook is not connected to the work tools yet (the adapter is the other developer's file) – so the desk does not
     * promise it. Connected, the save takes exactly the summary and nothing else of the chat.
     */
    @Test fun theShownSummaryIsWhatALaterSaveMeansOnceTheHostHandsItToTheWorkTools() {
        val today = argon()
        today.owner(five)
        assertFalse(today.said[2], today.said[2].contains("Speichere das als Word"))
        val wired = argon().apply { presentWired = true }
        wired.owner(five)
        assertTrue(wired.said[2], wired.said[2].endsWith("Gespeichert habe ich nichts. Wenn du sie behalten willst, sag zum Beispiel „Speichere das als Word“."))
        assertTrue(wired.downloads().isEmpty())
        assertEquals("TOOLS", wired.owner("Speichere das als Word"))
        val name = wired.downloads().single()
        assertEquals(WORD_2, name)
        val text = documentText(checkNotNull(wired.toolsHost.downloads[name]))
        assertTrue(text.contains("Die zweite Seite beschreibt, wie Erinnerungen abgerufen werden."))
        // Not the sequence's own report, which was the last thing said in the chat.
        assertFalse(text.contains("beide Schritte sind erledigt"))
    }

    // ── The goal sentence and the sentences 2 and 3: honest, and nothing runs ───────────────────────────────

    @Test fun theGoalSentenceIsAnsweredHonestlyAndNothingOfItRuns() {
        // Also with the cloud brain and model plans on: the answer comes from the sentence itself.
        for (cloud in listOf(false, true)) {
            val h = argon().apply { modelPlansOn = cloud; cloudBrain = cloud; modelPlan = { "1. Speichere das als PDF" } }
            // The chat holds an earlier answer – the one a „PDF daraus“ used to be made of.
            h.toolsHost.foreignTurn("Erklär mir Argon", "Argon ist ein Edelgas mit der Ordnungszahl 18 und kommt in der Luft vor.")
            assertEquals("MISSION", h.owner(goal))
            assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Deine E-Mails kann ich noch nicht lesen. Was darauf aufbaut, " +
                "lasse ich deshalb auch: zusammenfassen und als PDF speichern. Eine Nachricht kann ich in so einer Auftragsfolge noch " +
                "nicht mitschicken. Ich habe nichts davon ausgeführt.", h.lastSaid)
            assertNothingRan(h)
            assertFalse(h.lastSaid.contains("einzeln", ignoreCase = true))
            // Nothing was offered, so a „Ja“ starts nothing.
            assertEquals("OTHER", h.owner("Ja"))
            assertTrue(h.downloads().isEmpty() && h.sendHost.device.pressed.isEmpty())
        }
    }

    @Test fun openWhatsAppAndWriteIsNoDeadEndAndNothingIsOpenedOrSent() {
        val h = argon()
        assertEquals("MISSION", h.owner(two))
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Eine andere App öffnen oder darin suchen kann ich in so einer " +
            "Auftragsfolge noch nicht. Eine Nachricht kann ich in so einer Auftragsfolge noch nicht mitschicken. Ich habe nichts davon " +
            "ausgeführt.", h.lastSaid)
        assertNothingRan(h)
        assertTrue(h.sendHost.replies.isEmpty())
    }

    @Test fun theLawyersMailMakesNoPdfOfTheLastChatAnswer() {
        for (cloud in listOf(false, true)) {
            val h = argon().apply { modelPlansOn = cloud; cloudBrain = cloud; modelPlan = { "1. Speichere das als PDF\n2. Öffne es" } }
            h.toolsHost.foreignTurn("Erklär mir Argon", "Argon ist ein Edelgas mit der Ordnungszahl 18 und kommt in der Luft vor.")
            assertEquals("MISSION", h.owner(three))
            assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Deine E-Mails kann ich noch nicht lesen. Was darauf aufbaut, " +
                "lasse ich deshalb auch: zusammenfassen, als PDF speichern und öffnen. Ich habe nichts davon ausgeführt.", h.lastSaid)
            assertNothingRan(h)
            assertTrue(h.toolsHost.opened.isEmpty())
        }
    }

    // ── The rest is offered and runs only after the owner's yes ─────────────────────────────────────────────

    private val newsAndMessage = "Fass mir die Nachrichten von heute zusammen, speichere sie als PDF und schreib Anna auf WhatsApp, dass sie fertig sind"

    @Test fun theRestIsOfferedAndRunsOnlyAfterAYes() {
        val h = argon()
        assertEquals("MISSION", h.owner(newsAndMessage))
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Eine Nachricht kann ich in so einer Auftragsfolge noch nicht " +
            "mitschicken. Soll ich den Rest so machen: 1. Die Nachrichten von heute zusammenfassen. 2. Als PDF speichern?", h.lastSaid)
        assertNothingRan(h)
        assertEquals("MISSION", h.owner("Ja"))
        assertEquals(listOf(NEWS_PDF), h.downloads())
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – beide Schritte sind erledigt. 1. Nachrichten zusammengefasst ("))
        // The message was not sent with it, and the report does not claim it.
        assertTrue(h.sendHost.device.opened.isEmpty() && h.sendHost.device.pressed.isEmpty())
        assertFalse(h.lastSaid.contains("WhatsApp") || h.lastSaid.contains("Anna"))
    }

    @Test fun aNoOrAnotherTurnDropsTheOffer() {
        val h = argon()
        h.owner(newsAndMessage)
        assertEquals("MISSION", h.owner("Nein"))
        assertEquals("In Ordnung, ich mache nichts davon.", h.lastSaid)
        assertEquals("OTHER", h.owner("Ja"))
        h.owner(newsAndMessage)
        assertEquals("OTHER", h.owner("Wie spät ist es?"))
        assertEquals("OTHER", h.owner("Ja"))
        assertTrue(h.downloads().isEmpty() && h.toolsHost.consents.isEmpty())
    }

    @Test fun oneOfferedOrderRunsThroughItsOwnLane() {
        // The news alone: handed to the work tools as if the owner had said it.
        val news = argon()
        assertEquals("MISSION", news.owner("Fass mir die Nachrichten von heute zusammen und schreib Anna auf WhatsApp, dass ich nicht komme"))
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Eine Nachricht kann ich in so einer Auftragsfolge noch nicht " +
            "mitschicken. Soll ich den Rest machen: Die Nachrichten von heute zusammenfassen?", news.lastSaid)
        assertNothingRan(news)
        assertEquals("MISSION", news.owner("Ja"))
        assertEquals(1, news.toolsHost.consents.size)
        assertTrue(news.lastSaid, news.lastSaid.contains("Die Nationalbank von Musterstadt"))
        assertTrue(news.sendHost.device.pressed.isEmpty())
        // A page summary alone is the desk's order only inside a sequence: it runs as a sequence of one and is said once.
        val page = argon()
        assertEquals("DESK", page.owner("Öffne das PDF Argon4"))
        assertEquals("MISSION", page.owner("Fass Seite 2 zusammen und schreib Anna auf WhatsApp, dass ich fertig bin"))
        assertTrue(page.lastSaid, page.lastSaid.endsWith("Soll ich den Rest machen: Seite 2 zusammenfassen?"))
        assertTrue(page.deskHost.prompts.isEmpty())
        assertEquals("MISSION", page.owner("Ja"))
        assertEquals("Seite 2 von „Argon4.pdf“ – Zusammenfassung:\n\n$SUMMARY_2\n\nGespeichert habe ich nichts.", page.lastSaid)
        assertEquals(1, page.said.count { it.contains("Zusammenfassung:") })
        assertTrue(page.downloads().isEmpty() && page.sendHost.device.pressed.isEmpty())
    }

    // ── A model plan neither drops nor adds work ────────────────────────────────────────────────────────────

    @Test fun aModelOrderForOneOfTwoOrdersDoesNotRunByItself() {
        val h = argon().apply { modelPlansOn = true; cloudBrain = true; modelPlan = { "1. Öffne das PDF Argon4" } }
        val sentence = "Mach mir bitte das Argon4-Dokument auf und druck es dann aus"
        assertEquals("MISSION", h.owner(sentence))
        assertEquals(1, h.planPrompts.size)
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Für „druck es dann aus“ habe ich in einer Auftragsfolge noch " +
            "keinen festen Ablauf. Soll ich den Rest machen: Das PDF „Argon4“ suchen und öffnen?", h.lastSaid)
        // Before the review's fix the one line ran at once and the printing was dropped without a word.
        assertEquals(0, h.deskHost.scans)
        assertTrue(h.deskHost.shown.isEmpty())
        assertEquals("MISSION", h.owner("Ja"))
        assertEquals("Argon4.pdf", h.deskHost.shown.single().name)
    }

    @Test fun aModelPlanWithASaveNobodyOrderedIsDropped() {
        // A wording the planner does not read itself, so the model is asked.
        val sentence = "Ich bräuchte von der PDF-Datei Argon4 eine Zusammenfassung der Seite zwei"
        val h = argon().apply { modelPlansOn = true; cloudBrain = true; modelPlan = { "1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen und speichere es" } }
        assertEquals("MISSION", h.owner(sentence))
        assertEquals(listOf(sentence), h.ordinaryTurns)
        assertTrue(h.activities.last(), h.activities.last().startsWith("AUFTRAGSFOLGE · Modellplan verworfen · Schritt 2 speichert eine Datei, was du nicht bestellt hast"))
        assertTrue(h.missionSaid.isEmpty() && h.downloads().isEmpty())
        // The same goal as a plan without the save is proposed, and after his yes nothing is written.
        val right = argon().apply { modelPlansOn = true; cloudBrain = true; modelPlan = { "1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen" } }
        assertEquals("MISSION", right.owner(sentence))
        assertEquals("Ich würde das in zwei Schritten machen: 1. Das PDF „Argon4“ suchen und öffnen. 2. Seite 2 zusammenfassen. Soll ich " +
            "das so machen?", right.lastSaid)
        assertEquals("MISSION", right.owner("Ja"))
        assertTrue(right.lastSaid, right.lastSaid.startsWith("Fertig – beide Schritte sind erledigt."))
        assertTrue(right.downloads().isEmpty())
    }
}
