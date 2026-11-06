package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.CockpitWorkJournal.Episode
import at.heinz.deviceai.cognitive.CockpitWorkJournal.Kind
import at.heinz.deviceai.cognitive.CockpitWorkJournal.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WorkRecallTest {
    private val vienna = TimeZone.getTimeZone("Europe/Vienna")
    private fun at(day: Int, hour: Int, minute: Int) = Calendar.getInstance(vienna).apply {
        clear(); set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
    }.timeInMillis
    private val now = at(6, 10, 0)

    @Test fun theOwnersSentenceAsksForYesterdayAndTheChosenPdf() {
        val sentence = "JARVIS, wo waren wir gestern bei unserem Projekt? Nimm die ausgewählte PDF dazu und bereite mir eine Zusammenfassung vor."
        assertEquals(WorkRecall.Query(WorkRecall.Period.YESTERDAY, openOnly = false), WorkRecall.parse(sentence))
        assertEquals(OwnerDocumentIntent.Request(wantsDraft = true, reuse = true), OwnerDocumentIntent.parse(sentence))
        assertEquals(WorkRecall.Query(WorkRecall.Period.RECENT, openOnly = true), WorkRecall.parse("Was ist noch offen?"))
        assertEquals(WorkRecall.Query(WorkRecall.Period.TODAY, openOnly = false), WorkRecall.parse("Wie ist der Stand heute?"))
        listOf("Wo ist Wien?", "Was ist ein Projekt?", "Erzähl mir was von gestern Abend").forEach { assertNull(it, WorkRecall.parse(it)) }
    }

    @Test fun yesterdayIsAnsweredFromTheRecordWithTimeSourceStateAndWhatIsOpen() {
        val episodes = listOf(
            Episode("1", Kind.DOCUMENT, "Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.", at(5, 14, 12),
                at(5, 14, 20), State.DRAFTED, "Projektübersicht.pdf", 3, 68, 68, draft = "…", ownerEdits = 1),
            Episode("2", Kind.DOCUMENT, "Fasse den Bericht zusammen", at(5, 16, 40), at(5, 16, 41), State.ABORTED,
                "Bericht.pdf", 12, 9_000),
            Episode("3", Kind.DOCUMENT, "Öffne das Dokument", at(6, 9, 5), at(6, 9, 5), State.READ, "Heute.pdf", 1, 10)
        )
        val answer = WorkRecall.answer(episodes, WorkRecall.Query(WorkRecall.Period.YESTERDAY, false), now, vienna)
        assertTrue(answer, answer.startsWith("Gestern:"))
        assertTrue(answer, answer.contains("• gestern 14:12: „Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.“ – " +
            "Quelle: Projektübersicht.pdf, 3 Seiten – Stand: Entwurf erstellt und von dir bearbeitet, noch nicht gespeichert – " +
            "offen: Entwurf prüfen und speichern"))
        assertTrue("a stopped run is not done: $answer", answer.contains("Stand: mit STOP abgebrochen, nicht erledigt"))
        assertTrue("newest first", answer.indexOf("16:40") < answer.indexOf("14:12"))
        assertFalse("today's work is not yesterday's", answer.contains("Heute.pdf"))
        assertTrue(answer.endsWith("(Aus dem Arbeitsverlauf dieses Geräts – Erinnerung, kein Modelllernen.)"))
    }

    @Test fun anEmptyPeriodGetsOneTargetedQuestionInsteadOfAGuess() {
        val answer = WorkRecall.answer(emptyList(), WorkRecall.Query(WorkRecall.Period.YESTERDAY, false), now, vienna)
        assertEquals("Für gestern finde ich im Arbeitsverlauf keinen Auftrag. Meinst du einen anderen Tag – " +
            "oder soll ich die letzten Arbeiten zeigen?", answer)
        val saved = Episode("s", Kind.DOCUMENT, "x", at(6, 8, 0), at(6, 8, 1), State.SAVED)
        assertEquals("Im Arbeitsverlauf ist nichts mehr offen.",
            WorkRecall.answer(listOf(saved), WorkRecall.Query(WorkRecall.Period.RECENT, true), now, vienna))
    }
}
