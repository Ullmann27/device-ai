package at.heinz.deviceai

import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerMissionPlanner
import at.heinz.deviceai.gmail.Mails
import at.heinz.deviceai.sending.FakeContacts
import at.heinz.deviceai.sending.OwnerRelation
import at.heinz.deviceai.sending.SendChannel
import at.heinz.deviceai.sending.SendPlan
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/**
 * The persistent journal of a sequence of orders (architecture review C.4, experiment E9): what it stores, the
 * reservation before the one press of a send step, and what the app does after a restart at every step boundary –
 * with the side effects counted on the fake phone, the fake Gmail and the Download folder. A real process death on a
 * phone is NOT_RUN here: the restart is a new chat with new lanes on the same journal.
 */
class OwnerMissionJournalTest {
    @get:Rule val folder = TemporaryFolder()

    private val goal = "Schau bitte meine E-Mails durch, fasse mir das Wichtigste zusammen, mach daraus ein PDF und schick meinem " +
        "Vater auf WhatsApp, dass ich mich später melde."
    private val summary = "Anna bittet um die Unterlagen bis Freitag, und die Kanzlei bestätigt den Termin am Montag."

    private fun steps(sentence: String = goal) = checkNotNull(OwnerMissionPlanner.plan(sentence, "Jarvis")).steps

    private fun ready(): OwnerMissionHarness = OwnerMissionHarness().apply {
        sendHost.contacts.phoneRows = listOf(FakeContacts.phone(7, "Thomas Muster", "+43 660 1234567"))
        sendHost.device.whatsAppWorks()
        sendHost.grant(SendChannel.WHATSAPP)
        sendHost.relations.remember(OwnerRelation.VATER, 7, "Thomas Muster")
        sendHost.relations.markSent(OwnerRelation.VATER)
        val now = mailHost.now.toEpochMilli()
        mailHost.gmail.add(Mails.message("m1", "Anna Beispiel <anna@example.org>", "Unterlagen", now - 3_600_000L, body = "Bitte schick mir die Unterlagen bis Freitag."))
        mailHost.gmail.add(Mails.message("m2", "Kanzlei <recht@kanzlei.example>", "Termin", now - 7_200_000L, body = "Der Termin am Montag ist bestätigt."))
        mailHost.modelAnswer = { OwnerWorkGeneration(summary, null, true) }
        mailHost.connected()
    }

    private fun states(h: OwnerMissionHarness): List<String> = JSONObject(h.journalStore.document!!).getJSONArray("steps")
        .let { steps -> (0 until steps.length()).map { steps.getJSONObject(it).getString("state") } }

    private fun done(h: OwnerMissionHarness): Int = h.journalStore.document?.let { document ->
        JSONObject(document).getJSONArray("steps").let { steps -> (0 until steps.length()).count {
            steps.getJSONObject(it).getString("state") in setOf("DONE", "SENT", "PREPARED") } } } ?: 0

    // ── The journal itself ──────────────────────────────────────────────────────────────────────────────────

    @Test fun aSendStepIsReservedOnceAndNeverASecondTime() {
        val journal = OwnerMissionJournal(MemoryOwnerMissionJournalStore())
        assertTrue(journal.begin("mission-a", goal, 1_000L, steps()))
        assertTrue(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_000L))
        assertEquals(OwnerMissionJournal.State.INTENT_RESERVED, journal.state("mission-a", 3))
        // The same step again – with the same or with another text: refused while the first intent stands.
        assertFalse(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_100L))
        assertFalse(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Etwas anderes", null, 2_200L))
        // A running or waiting state of the director never overwrites the reservation.
        journal.step("mission-a", 3, OwnerMissionJournal.State.RUNNING, null, 2_300L)
        journal.step("mission-a", 3, OwnerMissionJournal.State.WAITING, null, 2_400L)
        assertEquals(OwnerMissionJournal.State.INTENT_RESERVED, journal.state("mission-a", 3))
        // The lane could not report a confirmed send: unknown, and it stays unknown.
        journal.step("mission-a", 3, OwnerMissionJournal.State.FAILED, null, 2_500L)
        assertEquals(OwnerMissionJournal.State.UNCONFIRMED, journal.state("mission-a", 3))
        journal.step("mission-a", 3, OwnerMissionJournal.State.RUNNING, null, 2_600L)
        assertEquals(OwnerMissionJournal.State.UNCONFIRMED, journal.state("mission-a", 3))
        assertFalse(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_700L))
        // Only the owner's explicit word releases it – once.
        assertTrue(journal.allowRetry("mission-a", 3, 2_800L))
        assertTrue(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_900L))
        assertFalse(journal.allowRetry("mission-b", 3, 3_000L))
    }

    @Test fun aPressThatDidNotHappenReleasesTheIntentAndAConfirmedSendClosesIt() {
        val journal = OwnerMissionJournal(MemoryOwnerMissionJournalStore())
        journal.begin("mission-a", goal, 1_000L, steps())
        assertTrue(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_000L))
        journal.pressed("mission-a", 3, clicked = false, at = 2_100L)
        assertEquals(OwnerMissionJournal.State.RUNNING, journal.state("mission-a", 3))
        assertTrue(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_200L))
        journal.pressed("mission-a", 3, clicked = true, at = 2_300L)
        assertEquals(OwnerMissionJournal.State.INTENT_RESERVED, journal.state("mission-a", 3))
        journal.step("mission-a", 3, OwnerMissionJournal.State.DONE, OwnerLaneResult.Delivered("WhatsApp", "Thomas Muster", null,
            OwnerLaneResult.Delivered.State.SENT), 2_400L)
        assertEquals(OwnerMissionJournal.State.SENT, journal.state("mission-a", 3))
        assertFalse(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_500L))
    }

    @Test fun withoutAWritableJournalNothingIsReserved() {
        val store = MemoryOwnerMissionJournalStore()
        val journal = OwnerMissionJournal(store)
        journal.begin("mission-a", goal, 1_000L, steps())
        store.failing = true
        assertFalse(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_000L))
        assertTrue(journal.broken)
        // Nothing of the failed attempt stays in memory either.
        store.failing = false
        assertEquals(OwnerMissionJournal.State.PENDING, journal.state("mission-a", 3))
        // No sequence, another sequence, an ended sequence: no reservation.
        assertFalse(journal.reserve("mission-x", 3, "WHATSAPP", "1", "a", null, 1L))
        journal.end("mission-a", "STOPPED", 3_000L)
        assertFalse(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 3_100L))
        assertFalse(OwnerMissionJournal(MemoryOwnerMissionJournalStore()).reserve("mission-a", 0, "WHATSAPP", "1", "a", null, 1L))
    }

    @Test fun theIdempotencyKeyIsTheDocumentedHash() {
        val text = OwnerMissionJournal.sha256("Ich melde mich später")
        assertEquals(OwnerMissionJournal.sha256("mission-a|3|WHATSAPP|436601234567|$text|-"),
            OwnerMissionJournal.key("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null))
        val file = OwnerMissionJournal.sha256("Bericht.pdf")
        assertEquals(OwnerMissionJournal.sha256("mission-a|2|GMAIL|anna@example.org|$text|$file"),
            OwnerMissionJournal.key("mission-a", 2, "GMAIL", "anna@example.org", "Ich melde mich später", "Bericht.pdf"))
        assertTrue(OwnerMissionJournal.key("a", 1, "WHATSAPP", "1", "x", null) != OwnerMissionJournal.key("a", 2, "WHATSAPP", "1", "x", null))
    }

    @Test fun resultsAreKeptAsReferencesNeverAsContent() {
        val store = MemoryOwnerMissionJournalStore()
        val journal = OwnerMissionJournal(store)
        journal.begin("mission-a", goal, 1_000L, steps())
        val secret = "GEHEIMER MAILINHALT: Kontonummer 12345"
        val of = OwnerDeliverable.ofMailSummary("Zusammenfassung 2 E-Mails", secret, 2, false, true, LocalDate.of(2026, 11, 19), 5L)!!
        journal.step("mission-a", 0, OwnerMissionJournal.State.DONE, OwnerLaneResult.MailsFound("von Anna Beispiel", listOf("m1" to "t1", "m2" to "t2")), 2_000L)
        journal.step("mission-a", 1, OwnerMissionJournal.State.DONE, OwnerLaneResult.MailSummarised(of, 2, false, true), 2_100L)
        journal.step("mission-a", 2, OwnerMissionJournal.State.DONE, OwnerLaneResult.FileSaved(
            OwnerSavedFile("Zusammenfassung 2 E-Mails 19.11.2026.pdf", "Download-Ordner", "content://media/1", "application/pdf"), of, null, null), 2_200L)
        journal.step("mission-a", 3, OwnerMissionJournal.State.DONE, OwnerLaneResult.Delivered("WhatsApp", "Thomas Muster", null,
            OwnerLaneResult.Delivered.State.SENT), 2_300L)
        val document = store.document!!
        assertFalse(document, document.contains("GEHEIM") || document.contains("12345") || document.contains("Anna Beispiel") || document.contains("Thomas Muster"))
        // What is there: the owner's own sentence, ids, a hash, the file's name.
        assertTrue(document, document.contains("dass ich mich später melde") && document.contains("\"m1\"") &&
            document.contains(OwnerMissionJournal.sha256(of.plainText)) && document.contains("Zusammenfassung 2 E-Mails 19.11.2026.pdf"))
        // Restored after a restart: what was found and the file – not the text.
        val record = OwnerMissionJournal(store).open()!!
        assertEquals(OwnerLaneResult.MailsFound("", listOf("m1" to "t1", "m2" to "t2")), OwnerMissionStepCodec.restore(record.steps[0].result))
        assertNull(OwnerMissionStepCodec.restore(record.steps[1].result))
        assertTrue(OwnerMissionStepCodec.restore(record.steps[2].result) is OwnerLaneResult.FileKept)
    }

    @Test fun everyStepKindIsReadBackExactlyAsItWasPlanned() {
        val sentences = listOf(goal, "Öffne bitte WhatsApp und schreib meinem Vater guten Morgen.",
            "Suche die letzte Mail von meinem Anwalt, fasse sie zusammen, erstelle daraus ein PDF und öffne es.",
            "Recherchiere die neuesten Entwicklungen zu Android-KI und speichere die Ergebnisse als Word-Dokument.",
            "Schau, welche Termine ich diese Woche habe, und sag mir, ob ich etwas vergessen könnte, und öffne danach das PDF Argon4",
            "Continuum, prüf zuerst meinen Speicher, bereinige die sicheren temporären Dateien und sag mir danach, wie viel Platz ich wieder habe.",
            "Öffne das PDF Argon4, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an anna@example.org",
            "Such das neueste PDF in Downloads und öffne es", "Speichere das als PDF und öffne Chrome",
            "Zeig mir meine neuen Mails und fass sie zusammen", "Such die neueste Mail von Anna Beispiel und lies sie vor")
        var kinds = 0
        sentences.forEach { sentence ->
            val planned = checkNotNull(OwnerMissionPlanner.plan(sentence, "Continuum") ?: OwnerMissionPlanner.plan(sentence, "Jarvis")) { sentence }.steps
            planned.forEach { step ->
                val back = OwnerMissionStepCodec.decode(JSONObject(OwnerMissionStepCodec.encode(step).toString()), "Continuum")
                assertNotNull(sentence + " · " + step.code, back)
                assertEquals(sentence, step.code, back!!.code)
                assertEquals(sentence, step.order, back.order)
                assertEquals(sentence, step.source, back.source)
                assertEquals(sentence, step::class, back::class)
                kinds++
            }
        }
        assertTrue("too few steps: $kinds", kinds >= 25)
    }

    @Test fun theFileStoreKeepsTheJournalAcrossInstancesAndDistrustsADamagedFile() {
        val directory = File(folder.root, "owner-mission")
        val journal = OwnerMissionJournal(FileOwnerMissionJournalStore(directory))
        assertTrue(journal.begin("mission-a", goal, 1_000L, steps()))
        assertTrue(journal.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_000L))
        // A new process: the reservation is there, and it still refuses.
        val again = OwnerMissionJournal(FileOwnerMissionJournalStore(directory))
        assertEquals(OwnerMissionJournal.State.INTENT_RESERVED, again.state("mission-a", 3))
        assertFalse(again.reserve("mission-a", 3, "WHATSAPP", "436601234567", "Ich melde mich später", null, 2_100L))
        assertEquals(goal, again.open()!!.request)
        // One changed byte: the checksum fails, the journal reads as „nothing open“ and nothing is continued from it.
        val file = File(directory, FileOwnerMissionJournalStore.FILE_NAME)
        val bytes = file.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        file.writeBytes(bytes)
        assertNull(OwnerMissionJournal(FileOwnerMissionJournalStore(directory)).open())
        assertFalse(OwnerMissionJournal(FileOwnerMissionJournalStore(directory)).reserve("mission-a", 3, "WHATSAPP", "1", "a", null, 3_000L))
        // No temporary file is left behind.
        assertEquals(listOf(FileOwnerMissionJournalStore.FILE_NAME), directory.list()!!.toList())
    }

    // ── After a restart ─────────────────────────────────────────────────────────────────────────────────────

    /** The app dies when [doneSteps] steps are done. After the restart: one sentence, nothing by itself, and one message in total. */
    private fun restartAfter(doneSteps: Int, expected: String) {
        val first = ready()
        first.killWhen = { done(first) == doneSteps }
        assertEquals("MISSION", first.owner(goal))
        assertTrue(first.dead)
        assertEquals("$doneSteps done", doneSteps, done(first))
        val pressedBefore = first.sendHost.device.pressed.size
        val second = first.restarted()
        // Nothing starts by itself: the offer is made, and until the owner answers nothing runs.
        assertTrue(second.mission.offerResume())
        second.settle()
        assertEquals(expected, second.lastSaid)
        assertFalse(second.mission.active)
        assertEquals(pressedBefore, second.sendHost.device.pressed.size)
        assertTrue(second.downloads().isEmpty())
        assertEquals("MISSION", second.owner("Ja"))
        // The whole order is done – and the message went out exactly once across both runs.
        assertEquals(1, second.sendHost.device.pressed.size)
        assertEquals("Ich melde mich später", (second.sendHost.device.opened.last() as SendPlan.WhatsApp).text)
        assertEquals(0, second.mailHost.gmail.sends + second.mailHost.gmail.drafts)
        assertEquals("COMPLETED", JSONObject(second.journalStore.document!!).getString("ended"))
        assertEquals("SENT", states(second)[3])
        assertTrue(second.lastSaid, second.lastSaid.contains("Nachricht an Thomas Muster über WhatsApp gesendet"))
        // A second look after the end offers nothing.
        assertFalse(second.restarted().mission.offerResume())
    }

    @Test fun restartBeforeTheFirstStep() = restartAfter(0, "Die Auftragsfolge von 14:02 steht bei Schritt 1 von 4. Weiter?")

    @Test fun restartAfterTheSearch() = restartAfter(1, "Die Auftragsfolge von 14:02 steht bei Schritt 2 von 4. Weiter?")

    /** The summary's text is not in the journal: the step that wrote it is done again – it sends nothing. */
    @Test fun restartAfterTheSummary() = restartAfter(2, "Die Auftragsfolge von 14:02 steht bei Schritt 3 von 4. Weiter? " +
        "Dafür mache ich Schritt 2 noch einmal – dabei geht nichts nach außen.")

    @Test fun restartAfterTheSave() = restartAfter(3, "Die Auftragsfolge von 14:02 steht bei Schritt 4 von 4. Weiter?")

    @Test fun aSendLeftAtIntentReservedIsNeverPressedAgainWithoutTheExplicitWord() {
        val first = ready()
        first.killDuringSend = true
        assertEquals("MISSION", first.owner(goal))
        assertTrue(first.dead)
        assertEquals(1, first.sendHost.device.pressed.size)
        assertEquals("INTENT_RESERVED", states(first)[3])
        val second = first.restarted()
        assertTrue(second.mission.offerResume())
        assertEquals("Die Auftragsfolge von 14:02 steht bei Schritt 4 von 4. Das ist: Meinem Vater auf WhatsApp schreiben. Ich weiß nicht, " +
            "ob diese Nachricht rausging: Die App wurde beendet, bevor ich das Senden bestätigen konnte. Bitte sieh in WhatsApp nach. " +
            "Ich drücke nicht noch einmal – nur wenn du „ja, nochmal“ sagst, sende ich sie erneut. Mit „weiter“ lasse ich diesen Schritt " +
            "aus, mit „abbrechen“ beende ich die Auftragsfolge.", second.lastSaid)
        // A plain yes is not the explicit word: nothing is pressed.
        assertEquals("MISSION", second.owner("Ja"))
        assertEquals(1, second.sendHost.device.pressed.size)
        assertTrue(second.lastSaid, second.lastSaid.startsWith("Die Auftragsfolge endet mit einem offenen Punkt."))
        assertTrue(second.lastSaid, second.lastSaid.contains("Unklar: 4. Meinem Vater auf WhatsApp schreiben – ob die Nachricht rausging, weiß ich nicht"))
        assertFalse(second.lastSaid, second.lastSaid.contains("Fertig"))
        assertEquals("UNCONFIRMED", states(second)[3])
    }

    @Test fun weiterAndAnyOtherTurnDoNotPressEither() {
        val first = ready().apply { killDuringSend = true }
        first.owner(goal)
        val second = first.restarted()
        second.mission.offerResume()
        // Something else: the offer is let go, nothing runs, the journal keeps the step as it is.
        assertEquals("OTHER", second.owner("Wie spät ist es?"))
        assertEquals(1, second.sendHost.device.pressed.size)
        assertEquals("INTENT_RESERVED", states(second)[3])
        val third = second.restarted()
        assertTrue(third.mission.offerResume())
        assertEquals("MISSION", third.owner("weiter"))
        assertEquals(1, third.sendHost.device.pressed.size)
    }

    @Test fun onlyJaNochmalSendsAgainAndThenWithEveryCheckOfTheSendLane() {
        val first = ready().apply { killDuringSend = true }
        first.owner(goal)
        val second = first.restarted()
        second.mission.offerResume()
        assertEquals("MISSION", second.owner("Ja, nochmal"))
        assertEquals(2, second.sendHost.device.pressed.size)
        assertEquals("SENT", states(second)[3])
        assertTrue(second.said.toString(), second.said.any { it.startsWith("MISSION:In Ordnung, auf dein Wort sende ich sie noch einmal") })
        // And never a third time by itself.
        assertFalse(second.restarted().mission.offerResume())
    }

    @Test fun abbrechenEndsTheSequenceFromTheJournal() {
        val first = ready()
        first.killWhen = { done(first) == 1 }
        first.owner(goal)
        val second = first.restarted()
        second.mission.offerResume()
        assertEquals("MISSION", second.owner("abbrechen"))
        assertEquals("In Ordnung, die Auftragsfolge ist beendet. Von den offenen Schritten habe ich nichts ausgeführt.", second.lastSaid)
        assertTrue(second.sendHost.device.pressed.isEmpty() && second.downloads().isEmpty())
        assertFalse(second.restarted().mission.offerResume())
    }

    @Test fun anOldSequenceIsNotOfferedAnyMore() {
        val first = ready()
        first.killWhen = { done(first) == 1 }
        first.owner(goal)
        val late = first.restarted().apply { wall += OwnerMissionDirector.RESUME_MILLIS + 60_000L }
        assertFalse(late.mission.offerResume())
        assertTrue(late.said.isEmpty())
    }

    /** With a journal that cannot be written, the message is prepared and verified – and the send control is not pressed. */
    @Test fun withoutAJournalTheSendStepDoesNotPress() {
        val h = ready()
        h.journalStore.failing = true
        assertEquals("MISSION", h.owner(goal))
        assertTrue(h.sendHost.device.pressed.isEmpty())
        assertTrue(h.said.toString(), h.said.any { it.startsWith("SEND:") && it.contains("gesendet habe ich nichts") && it.contains("ohne diesen Vermerk drücke ich nicht") })
        assertTrue(h.said.none { it.contains("Fertig") })
    }
}
