package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.CockpitWorkJournal.Episode
import at.heinz.deviceai.cognitive.CockpitWorkJournal.Kind
import at.heinz.deviceai.cognitive.CockpitWorkJournal.State
import at.heinz.deviceai.cognitive.WorkDecisionRecall.Ask
import at.heinz.deviceai.cognitive.WorkDecisionRecall.OwnerStatement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Owner brief 06.11.2026, Priorität 1: „Was haben wir gestern bei unserem Projekt entschieden?“ – „Nein, ich meinte die
 * Entscheidung davor.“ – „Was davon haben wir schon umgesetzt?“ – „Dann mach bitte beim letzten offenen Punkt weiter.“
 * RED before this change: none of the four sentences was understood by WorkRecall, OwnerCorrectionIntent or
 * OwnerDocumentIntent (all null, host run 06.11.) – they fell through to the local model, which has no access to the
 * work journal or the owner ledger and could only guess.
 */
class WorkDecisionRecallTest {
    private val vienna = TimeZone.getTimeZone("Europe/Vienna")
    private fun at(day: Int, hour: Int, minute: Int) = Calendar.getInstance(vienna).apply {
        clear(); set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
    }.timeInMillis
    private val now = at(6, 10, 0)

    // The record as it really is: two days ago a saved draft, yesterday a stopped run, a corrected draft (still
    // unsaved) and a confirmed owner statement; today one that was revoked.
    private val episodes = listOf(
        Episode("a", Kind.DOCUMENT, "Fasse den Vertrag zusammen", at(4, 9, 0), at(4, 9, 30), State.SAVED, "Vertrag.pdf", 4, 5_000,
            5_000, draft = "…", draftUri = "content://saved/a"),
        Episode("b", Kind.DOCUMENT, "Fasse den Bericht zusammen", at(5, 11, 0), at(5, 11, 2), State.ABORTED, "Bericht.pdf", 12,
            9_000, documentUri = "content://doc/b"),
        Episode("c", Kind.DOCUMENT, "Lies diese PDF und erstelle einen Entwurf", at(5, 14, 12), at(5, 14, 40), State.DRAFTED,
            "Projektübersicht.pdf", 3, 68, 68, draft = "Entwurf …", ownerEdits = 1,
            correction = "Nein, ich meinte den letzten Entwurf", correctedAt = at(5, 14, 30)),
        Episode("d", Kind.DOCUMENT, "Öffne das Dokument", at(6, 9, 5), at(6, 9, 5), State.READ, "Heute.pdf", 1, 10)
    )
    private val statements = listOf(
        OwnerStatement("Wir haben entschieden, die Zusammenfassung auf eine Seite zu kürzen.", at(5, 16, 0)),
        OwnerStatement("Ich wohne in Gänserndorf.", at(5, 16, 5)),
        OwnerStatement("Wir nehmen ab jetzt das Deckblatt aus der Vorlage B.", at(6, 8, 0), revokedAt = at(6, 8, 30))
    )

    @Test fun theOwnersFourSentencesAreUnderstoodAndUnrelatedOnesAreNot() {
        assertEquals(Ask.Decisions(WorkRecall.Period.YESTERDAY, changes = false),
            WorkDecisionRecall.parse("JARVIS, was haben wir gestern bei unserem Projekt entschieden?"))
        assertEquals(Ask.Previous, WorkDecisionRecall.parse("Nein, ich meinte die Entscheidung davor."))
        assertEquals(Ask.Implemented, WorkDecisionRecall.parse("Was davon haben wir schon umgesetzt?"))
        assertEquals(Ask.ContinueOpen, WorkDecisionRecall.parse("Dann mach bitte beim letzten offenen Punkt weiter."))
        assertEquals(Ask.Decisions(WorkRecall.Period.RECENT, changes = true),
            WorkDecisionRecall.parse("Was hat sich seitdem geändert?"))
        assertEquals(Ask.Decisions(WorkRecall.Period.TODAY, changes = false),
            WorkDecisionRecall.parse("Welche Entscheidungen haben wir heute getroffen?"))
        listOf("Wo waren wir gestern?", "Was ist eine Entscheidung?", "Ich habe mich gestern entschieden, früher zu schlafen",
            "Nein, ich meinte den letzten Entwurf", "Mach weiter", "Was haben wir gestern gemacht?").forEach {
            assertNull(it, WorkDecisionRecall.parse(it))
        }
        // The existing intents keep their sentences: no double handling.
        assertNotNull(OwnerCorrectionIntent.parse("Nein, ich meinte den letzten Entwurf"))
        assertNotNull(WorkRecall.parse("Was haben wir gestern gemacht?"))
    }

    @Test fun yesterdaysDecisionsComeFromBothRecordsWithTimeOriginAndOpenStep() {
        val reply = WorkDecisionRecall.answer(Ask.Decisions(WorkRecall.Period.YESTERDAY, false), null, episodes, statements,
            now, vienna)
        val text = reply.text
        assertTrue(text, text.startsWith("Gestern"))
        // Newest first, every item with its time and what really happened.
        val stop = text.indexOf("gestern 11:02: Arbeit an „Bericht.pdf“ mit STOP beendet – offen: neu starten oder verwerfen")
        val correction = text.indexOf("gestern 14:30: Deine Korrektur zu „Lies diese PDF und erstelle einen Entwurf“: " +
            "„Nein, ich meinte den letzten Entwurf“ – offen: Entwurf prüfen und speichern")
        val statement = text.indexOf("gestern 16:00: Von dir bestätigt: „Wir haben entschieden, die Zusammenfassung auf eine Seite zu kürzen.“")
        assertTrue(text, stop > 0 && correction > 0 && statement > 0)
        assertTrue("newest first: $text", statement < correction && correction < stop)
        // Not a decision: a plain owner fact, a read-only document, the other days.
        assertFalse(text, text.contains("Gänserndorf"))
        assertFalse(text, text.contains("Heute.pdf"))
        assertFalse(text, text.contains("Vertrag.pdf"))
        assertFalse(text, text.contains("Deckblatt"))
        assertTrue(text, text.endsWith("(Aus dem Arbeitsverlauf und deinen bestätigten Aussagen dieses Geräts – Erinnerung, kein Modelllernen.)"))
        assertEquals(3, reply.hits)
        assertNotNull(reply.focus)
        assertEquals(3, reply.focus!!.shown.size)
        // The focus points at the newest shown item within the complete, ordered record (today's items lie before it).
        assertEquals(reply.focus!!.shown.first(), reply.focus!!.items[reply.focus!!.index])
    }

    @Test fun aRevokedStatementStaysVisibleAsRevokedAndIsNeverReportedAsValid() {
        val reply = WorkDecisionRecall.answer(Ask.Decisions(WorkRecall.Period.TODAY, false), null, episodes, statements, now, vienna)
        assertTrue(reply.text, reply.text.contains("heute 08:00: Von dir bestätigt: „Wir nehmen ab jetzt das Deckblatt aus der " +
            "Vorlage B.“ – widerrufen heute 08:30"))
        assertEquals(1, reply.hits)
        val implemented = WorkDecisionRecall.answer(Ask.Implemented, reply.focus, episodes, statements, now, vienna)
        assertFalse(implemented.text, implemented.text.contains("Umgesetzt:"))
        assertTrue(implemented.text, implemented.text.contains("Widerrufen"))
    }

    @Test fun theDecisionBeforeMovesOneStepBackAcrossDaysAndAsksWhenNothingWasShown() {
        val first = WorkDecisionRecall.answer(Ask.Decisions(WorkRecall.Period.YESTERDAY, false), null, episodes, statements, now, vienna)
        // „Nein, ich meinte die Entscheidung davor.“ – from the newest shown (16:00 statement) to the correction at 14:30.
        val previous = WorkDecisionRecall.answer(Ask.Previous, first.focus, episodes, statements, now, vienna)
        assertTrue(previous.text, previous.text.startsWith("Verstanden – die Entscheidung davor:"))
        assertTrue(previous.text, previous.text.contains("gestern 14:30: Deine Korrektur"))
        assertEquals(1, previous.hits)
        assertTrue(previous.understood)
        assertTrue(previous.focus!!.shown.single().text.startsWith("Deine Korrektur"))
        // Again: the STOP at 11:00; again: the saved draft two days ago (outside „gestern“, still in order).
        val second = WorkDecisionRecall.answer(Ask.Previous, previous.focus, episodes, statements, now, vienna)
        assertTrue(second.text, second.text.contains("gestern 11:02: Arbeit an „Bericht.pdf“ mit STOP beendet"))
        val third = WorkDecisionRecall.answer(Ask.Previous, second.focus, episodes, statements, now, vienna)
        assertTrue(third.text, third.text.contains("4. Nov., 09:30: Entwurf zu „Vertrag.pdf“ gespeichert – umgesetzt"))
        val none = WorkDecisionRecall.answer(Ask.Previous, third.focus, episodes, statements, now, vienna)
        assertEquals(0, none.hits)
        assertFalse(none.understood)
        assertTrue(none.text, none.text.startsWith("Davor finde ich keine weitere Entscheidung"))
        assertEquals(third.focus, none.focus)
        // After a restart nothing was shown in this conversation: one short question, no guess.
        val cold = WorkDecisionRecall.answer(Ask.Previous, null, episodes, statements, now, vienna)
        assertEquals(0, cold.hits)
        assertTrue(cold.text, cold.text.contains("noch keine Entscheidung genannt"))
        assertNull(cold.focus)
    }

    @Test fun implementedSplitsWhatWasReallyDoneFromOpenAndUncheckable() {
        val shown = WorkDecisionRecall.answer(Ask.Decisions(WorkRecall.Period.RECENT, false), null, episodes, statements, now, vienna)
        val reply = WorkDecisionRecall.answer(Ask.Implemented, shown.focus, episodes, statements, now, vienna)
        val text = reply.text
        val done = text.indexOf("Umgesetzt:")
        val open = text.indexOf("Noch offen:")
        val unverifiable = text.indexOf("Nicht prüfbar")
        assertTrue(text, done in 0 until open && open < unverifiable)
        assertTrue(text, text.substring(done, open).contains("Entwurf zu „Vertrag.pdf“ gespeichert"))
        assertTrue(text, text.substring(open, unverifiable).contains("Bericht.pdf“ mit STOP beendet – offen: neu starten oder verwerfen"))
        assertTrue(text, text.substring(open, unverifiable).contains("Deine Korrektur") &&
            text.substring(open, unverifiable).contains("offen: Entwurf prüfen und speichern"))
        assertTrue(text, text.substring(unverifiable).contains("eine Seite zu kürzen"))
        assertEquals(shown.focus, reply.focus)
        val cold = WorkDecisionRecall.answer(Ask.Implemented, null, episodes, statements, now, vienna)
        assertEquals(0, cold.hits)
        assertTrue(cold.text, cold.text.contains("noch keine Entscheidungen genannt"))
    }

    @Test fun continueResolvesTheNewestOpenJournalPointNeverAStatementOrADoneTask() {
        val shown = WorkDecisionRecall.answer(Ask.Decisions(WorkRecall.Period.RECENT, false), null, episodes, statements, now, vienna)
        val resume = WorkDecisionRecall.resolveContinue(shown.focus, episodes, now)
        assertNotNull(resume)
        assertEquals("c", resume!!.episode.id)
        assertEquals("Entwurf prüfen und speichern", resume.step)
        // Without anything shown the whole record counts – the same answer here, not a question.
        assertEquals("c", WorkDecisionRecall.resolveContinue(null, episodes, now)?.episode?.id)
        // Only done tasks and owner statements: nothing to continue.
        assertNull(WorkDecisionRecall.resolveContinue(null, listOf(episodes[0], episodes[3]), now))
        val nothing = WorkDecisionRecall.answer(Ask.ContinueOpen, null, listOf(episodes[0]), statements, now, vienna)
        assertEquals(0, nothing.hits)
        assertTrue(nothing.text, nothing.text.contains("kein Punkt offen"))
    }

    @Test fun emptyPeriodsGetAnHonestAnswerAndNoFocus() {
        val reply = WorkDecisionRecall.answer(Ask.Decisions(WorkRecall.Period.YESTERDAY, false), null, emptyList(), emptyList(), now, vienna)
        assertEquals(0, reply.hits)
        assertNull(reply.focus)
        assertTrue(reply.text, reply.text.startsWith("Für gestern finde ich weder im Arbeitsverlauf noch in deinen bestätigten Aussagen"))
        val changes = WorkDecisionRecall.answer(Ask.Decisions(WorkRecall.Period.RECENT, true), null, episodes, statements, now, vienna)
        // Changes only: the correction, the owner's edit, the STOP and the revocation – not the untouched saved draft.
        assertTrue(changes.text, changes.text.contains("Deine Korrektur") && changes.text.contains("widerrufen") &&
            changes.text.contains("STOP"))
        assertFalse(changes.text, changes.text.contains("Vertrag.pdf"))
    }

    @Test fun theJournalKeepsTheTimeOfACorrectionAcrossRestarts() {
        val dir = java.nio.file.Files.createTempDirectory("journal").toFile()
        var clock = at(5, 14, 12)
        val journal = CockpitWorkJournal(dir) { clock }
        val e = journal.begin(Kind.DOCUMENT, "Lies diese PDF", State.DRAFTED, "P.pdf", 1, 10)
        clock = at(5, 14, 30)
        journal.update(e.id) { it.copy(correction = "Nein, ich meinte den letzten Entwurf") }
        clock = at(5, 14, 40)
        journal.update(e.id) { it.copy(ownerEdits = 1) }
        val reloaded = CockpitWorkJournal(dir) { clock }.all().single()
        assertEquals(at(5, 14, 30), reloaded.correctedAt)
        assertEquals(at(5, 14, 40), reloaded.updatedAt)
        // An episode without a correction has no correction time; legacy rows decode without one.
        assertNull(journal.begin(Kind.DOCUMENT, "x", State.READ).correctedAt)
    }
}
