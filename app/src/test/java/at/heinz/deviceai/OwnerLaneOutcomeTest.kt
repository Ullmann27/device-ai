package at.heinz.deviceai

import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.ARGON
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_2
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.NewsTestFeeds
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.sending.FakeSendHost
import at.heinz.deviceai.sending.OwnerMailHandover
import at.heinz.deviceai.sending.OwnerSendDirector
import at.heinz.deviceai.sending.OwnerSendHost
import at.heinz.deviceai.sending.SendChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every owner lane reports how an order ended as a typed [OwnerLaneOutcome] through its host – the real result, the
 * question it keeps open, the reason it failed or the owner's stop. The lanes are the productive classes on their
 * own harnesses; only a listener is added. A host that does not listen (the default) changes nothing.
 */
class OwnerLaneOutcomeTest {
    private class Tools(val inner: OwnerWorkToolsHarness, val lane: (() -> OwnerSendDirector)? = null) : OwnerWorkToolsHost by inner {
        val outcomes = mutableListOf<OwnerLaneOutcome>()
        override fun outcome(outcome: OwnerLaneOutcome) { outcomes += outcome }
        override fun sendMailYourself(order: OwnerMailHandover): Boolean = lane?.invoke()?.mail(order) ?: false
    }

    private class Desk(val inner: OwnerDocumentDeskHarness) : OwnerDocumentDeskHost by inner {
        val outcomes = mutableListOf<OwnerLaneOutcome>()
        override fun outcome(outcome: OwnerLaneOutcome) { outcomes += outcome }
    }

    private class Send(val inner: FakeSendHost) : OwnerSendHost by inner {
        val outcomes = mutableListOf<OwnerLaneOutcome>()
        override fun outcome(outcome: OwnerLaneOutcome) { outcomes += outcome }
    }

    private val briefing = "Die Nationalbank von Musterstadt hat den Leitzins auf 2,5 Prozent gesenkt, das berichten die " +
        "tagesschau und der ORF. Außerdem müssen in Testland neue Häuser ab 2028 eine Solaranlage haben. Währenddessen " +
        "warnt ein Bericht vor häufigeren Dürren in Beispielia, wie die Deutsche Welle meldet."

    private fun tools(mode: InternetAccessMode = InternetAccessMode.ALWAYS_ON): Pair<Tools, OwnerWorkTools> {
        val host = Tools(OwnerWorkToolsHarness(mode, model = { OwnerWorkGeneration(briefing, null, true) }))
        return host to OwnerWorkTools(host, now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
    }

    private fun desk(): Pair<Desk, OwnerDocumentDesk> {
        val host = Desk(OwnerDocumentDeskHarness().apply {
            put("Argon4.pdf", pages = ARGON)
            model = { OwnerWorkGeneration(SUMMARY_2, null, true) }
        })
        return host to OwnerDocumentDesk(host, now = { OwnerDocumentDeskHarness.NOW }, zone = { OwnerDocumentDeskHarness.ZONE })
    }

    @Test fun theWorkToolsReportTheBriefingTheSavedFileTheOpenedFileAndThePreparedMail() {
        val (host, lane) = tools()
        assertTrue(lane.handle("Fass mir die Nachrichten von heute zusammen"))
        val brief = (host.outcomes.single() as OwnerLaneOutcome.Done).result as OwnerLaneResult.Briefing
        assertEquals(OwnerDeliverable.Kind.NEWS_BRIEFING, brief.deliverable.kind)
        assertSame(lane.deliverable, brief.deliverable)
        assertTrue(brief.stories > 0 && brief.byModel)

        assertTrue(lane.handle("Speichere das als PDF"))
        val saved = (host.outcomes[1] as OwnerLaneOutcome.Done).result as OwnerLaneResult.FileSaved
        assertEquals("Nachrichten von heute 18.11.2026.pdf", saved.file.displayName)
        assertSame(brief.deliverable, saved.of)

        assertTrue(lane.handle("Öffne die gespeicherte Datei"))
        assertEquals(saved.file, ((host.outcomes[2] as OwnerLaneOutcome.Done).result as OwnerLaneResult.FileOpened).file)

        assertTrue(lane.handle("Schick das per Mail an anna@example.org"))
        val mail = (host.outcomes[3] as OwnerLaneOutcome.Done).result as OwnerLaneResult.Delivered
        assertEquals(OwnerLaneResult.Delivered.State.HANDED_OVER, mail.state)
        assertEquals("anna@example.org", mail.to)
        assertEquals(saved.file, mail.attachment)
        assertEquals(4, host.outcomes.size)
    }

    @Test fun theWorkToolsReportWhyAnOrderWasNotDone() {
        val (offline, offlineLane) = tools(InternetAccessMode.OFF)
        assertTrue(offlineLane.handle("Fass mir die Nachrichten von heute zusammen"))
        assertTrue((offline.outcomes.single() as OwnerLaneOutcome.Failed).reason.startsWith("Internetzugang ist AUS"))

        val (host, lane) = tools()
        assertTrue(lane.handle("Speichere das als PDF"))
        assertEquals("In diesem Gespräch gibt es noch nichts, das ich speichern könnte.", (host.outcomes.single() as OwnerLaneOutcome.Failed).reason)
        lane.handle("Fass mir die Nachrichten von heute zusammen")
        host.inner.saveProblem = "Kein Speicherplatz"
        lane.handle("Speichere das als PDF")
        assertEquals("Das Speichern hat nicht geklappt: Kein Speicherplatz. Es wurde keine Datei angelegt.", (host.outcomes.last() as OwnerLaneOutcome.Failed).reason)
        host.inner.mailProblem = "Kein Mailprogramm"
        lane.handle("Schick das per Mail an anna@example.org")
        assertTrue((host.outcomes.last() as OwnerLaneOutcome.Failed).reason.startsWith("Die E-Mail konnte ich nicht vorbereiten: Kein Mailprogramm."))
    }

    @Test fun theOfferToOpenASavedFileIsTheHostsDecisionAndCanBeMadeLater() {
        val inner = OwnerWorkToolsHarness(model = { OwnerWorkGeneration(briefing, null, true) })
        var offers = false
        val host = object : OwnerWorkToolsHost by inner { override fun offerToOpen(): Boolean = offers }
        val lane = OwnerWorkTools(host, now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
        lane.handle("Fass mir die Nachrichten von heute zusammen")
        lane.handle("Speichere das als PDF")
        assertEquals("Erledigt. „Nachrichten von heute 18.11.2026.pdf“ liegt jetzt in deinem Download-Ordner (1 Seite, mit Quellenliste).",
            inner.lines("SAY:").last())
        // Without the offer „Ja“ answers nothing …
        assertEquals(false, lane.handle("Ja"))
        // … until the offer is made for exactly that result; a result that is no longer the chat's is refused.
        val result = checkNotNull(lane.deliverable)
        val file = OwnerSavedFile("Nachrichten von heute 18.11.2026.pdf", "Download-Ordner", "content://media/external/downloads/1", "application/pdf")
        assertTrue(lane.offerOpening(result, file))
        assertTrue(lane.handle("Ja"))
        assertEquals(file, inner.opened.single())
        assertEquals(false, lane.offerOpening(result.copy(title = "Etwas anderes"), file))
        assertTrue(lane.continueOn(result))
        assertEquals(false, lane.continueOn(result.copy(title = "Etwas anderes")))
        offers = true
        lane.handle("Speichere das als Word-Datei")
        assertTrue(inner.lines("SAY:").last().endsWith("Soll ich sie öffnen?"))
    }

    @Test fun theDeskReportsTheOpenedPdfTheSavedSummaryItsQuestionsAndItsFailures() {
        val (host, lane) = desk()
        assertTrue(lane.handle("Öffne das PDF Argon4"))
        val opened = (host.outcomes.single() as OwnerLaneOutcome.Done).result as OwnerLaneResult.DocumentOpened
        assertEquals(OwnerLaneResult.DocumentOpened("Argon4.pdf", "Download", 4, "Argon4"), opened)

        assertTrue(lane.handle("Fass Seite 2 zusammen und speichere es als Word"))
        val saved = (host.outcomes[1] as OwnerLaneOutcome.Done).result as OwnerLaneResult.FileSaved
        assertEquals("Zusammenfassung Argon4 Seite 2 18.11.2026.docx", saved.file.displayName)
        assertEquals(OwnerDeliverable.Kind.PAGE_SUMMARY, saved.of.kind)
        assertEquals(true, saved.verified)
        assertEquals("Interner Speicher › Download", saved.location)

        assertTrue(lane.handle("Fass Seite 9 zusammen und speichere es als Word"))
        assertEquals("„Argon4.pdf“ hat nur 4 Seiten – eine Seite 9 gibt es nicht. Ich habe nichts zusammengefasst und nichts gespeichert.",
            (host.outcomes[2] as OwnerLaneOutcome.Failed).reason)
        assertTrue(lane.handle("Fass Seite 3 zusammen und speichere es"))
        assertEquals(OwnerLaneOutcome.Need.DETAIL, (host.outcomes[3] as OwnerLaneOutcome.NeedsOwner).need)
        assertTrue(lane.handle("Öffne das PDF Xenon5"))
        assertTrue((host.outcomes[4] as OwnerLaneOutcome.Failed).reason.startsWith("Ein PDF namens „Xenon5“ habe ich nicht gefunden."))
        // A pure search reports what it found and opens nothing.
        assertTrue(lane.handle("Such das PDF Argon4"))
        assertEquals(OwnerLaneResult.FilesFound("Argon4", listOf("Argon4.pdf")), (host.outcomes[5] as OwnerLaneOutcome.Done).result)
        assertEquals(6, host.outcomes.size)
    }

    @Test fun aChoiceAMissingGrantAndTheOwnersNoOrStopAreReportedAsSuch() {
        val (host, lane) = desk()
        host.inner.put("Argon4 Datenblatt.pdf", folder = "Documents")
        assertTrue(lane.handle("Öffne das PDF Argon"))
        assertEquals(OwnerLaneOutcome.Need.CHOICE, (host.outcomes.single() as OwnerLaneOutcome.NeedsOwner).need)
        assertTrue(lane.handle("das dritte"))
        assertEquals(OwnerLaneOutcome.Need.CHOICE, (host.outcomes[1] as OwnerLaneOutcome.NeedsOwner).need)
        assertTrue(lane.handle("keines davon"))
        assertEquals(OwnerLaneOutcome.Stopped, host.outcomes[2])

        host.inner.libraryAccess = OwnerLibraryAccess.MISSING
        assertTrue(lane.handle("Öffne das PDF Argon4"))
        assertEquals(OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.GRANT, OwnerDocumentDesk.GRANT_MISSING), host.outcomes[3])
        assertTrue(lane.handle("Nein"))
        assertEquals(OwnerLaneOutcome.Stopped, host.outcomes[4])

        // The stop word during a job: one report when the worker has really ended; a corrected order reports only its own end.
        host.inner.libraryAccess = OwnerLibraryAccess.GRANTED
        lane.handle("Öffne das PDF Argon4")
        host.inner.duringGeneration = { assertTrue(lane.intercept("Stopp")) }
        lane.handle("Fass Seite 2 zusammen und speichere es als Word")
        assertEquals(OwnerLaneOutcome.Stopped, host.outcomes.last())
        val before = host.outcomes.size
        host.inner.duringGeneration = { assertTrue(lane.intercept("Ich meinte Seite 3")) }
        lane.handle("Fass Seite 2 zusammen und speichere es als Word")
        assertEquals(before + 1, host.outcomes.size)
        assertEquals("Zusammenfassung Argon4 Seite 3 18.11.2026.docx",
            ((host.outcomes.last() as OwnerLaneOutcome.Done).result as OwnerLaneResult.FileSaved).file.displayName)
    }

    @Test fun aFileAnotherLaneFoundIsOpenedThroughTheDesksOwnSearchAndALaterCorrectionRedoesItsLastOrder() {
        val (host, lane) = desk()
        lane.openFound("öffne es", "Argon4.pdf", "Download")
        assertEquals("Argon4.pdf", ((host.outcomes.single() as OwnerLaneOutcome.Done).result as OwnerLaneResult.DocumentOpened).name)
        assertEquals(1, host.inner.scans)
        lane.handle("Fass Seite 2 zusammen und speichere es als Word")
        // Another lane's turn in between: the desk's own „right after my turn“ rule no longer applies …
        host.inner.foreignTurn()
        assertEquals(false, lane.intercept("Ich meinte als PDF"))
        // … but the correction can be handed to it explicitly; the accepted summary is used again.
        assertTrue(lane.correct("Ich meinte als PDF"))
        assertEquals("Zusammenfassung Argon4 Seite 2 18.11.2026.pdf",
            ((host.outcomes.last() as OwnerLaneOutcome.Done).result as OwnerLaneResult.FileSaved).file.displayName)
        assertEquals(1, host.inner.prompts.size)
        assertEquals(false, lane.correct("Wie spät ist es?"))
    }

    @Test fun theSendLaneReportsTheSentMailItsQuestionAndTheOwnersCancel() {
        val host = Send(FakeSendHost().apply { device.gmailWorks(); grant(SendChannel.GMAIL) })
        val lane = OwnerSendDirector(host)
        val file = OwnerSavedFile("Bericht.pdf", "Download-Ordner", "content://media/external/downloads/1", "application/pdf")
        assertTrue(lane.mail(OwnerMailHandover("Schick das per Mail an anna@example.org", "anna@example.org", null, "Bericht", "Text", file)))
        val sent = (host.outcomes.single() as OwnerLaneOutcome.Done).result as OwnerLaneResult.Delivered
        assertEquals(OwnerLaneResult.Delivered("Gmail", "anna@example.org", file, OwnerLaneResult.Delivered.State.SENT), sent)

        // Generated text without a file: shown once, the question is reported; „abbrechen“ ends it.
        assertTrue(lane.mail(OwnerMailHandover("Schick das per Mail an anna@example.org", "anna@example.org", null, "Bericht", "Text", null)))
        assertEquals(OwnerLaneOutcome.Need.CONFIRMATION, (host.outcomes[1] as OwnerLaneOutcome.NeedsOwner).need)
        assertTrue(lane.handle("abbrechen"))
        assertEquals(OwnerLaneOutcome.Stopped, host.outcomes[2])
        assertEquals(1, host.inner.device.pressed.size)

        // A draft the screen does not show: not sent, failed with the lane's own sentence.
        host.inner.device.beforeClick = { _, _ -> null }
        assertTrue(lane.mail(OwnerMailHandover("Schick das per Mail an anna@example.org", "anna@example.org", null, "Bericht", "Text", file)))
        assertTrue(host.outcomes.last() is OwnerLaneOutcome.Failed)
        assertEquals(1, host.inner.device.pressed.size)
    }
}
