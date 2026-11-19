package at.heinz.deviceai

import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.ARGON
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_2
import at.heinz.deviceai.OwnerMissionHarness.Companion.ANNA
import at.heinz.deviceai.OwnerMissionHarness.Companion.BRIEFING
import at.heinz.deviceai.cognitive.CockpitWorkJournal
import at.heinz.deviceai.cognitive.InMemoryOwnerSharedWorkStore
import at.heinz.deviceai.cognitive.OwnerComputerWorkflow
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerSharedWorkRecord
import at.heinz.deviceai.cognitive.SharedWorkAction
import at.heinz.deviceai.cognitive.SharedWorkKind
import at.heinz.deviceai.cognitive.SharedWorkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What feeds the shared-work record: the typed reports the app already has. The lanes here are the productive ones
 * (document desk, work tools, mission director) on their host harnesses, listened to at the one place every lane's
 * outcome passes – [OwnerMissionDirector.onLaneOutcome] – exactly as the Activity wires it. Nothing is read out of a
 * reply's wording, and an entry for something that left the device never carries an order to repeat it.
 */
class OwnerSharedWorkFeedTest {
    private var now = 1_763_542_800_000L
    private val record = OwnerSharedWorkRecord(InMemoryOwnerSharedWorkStore()) { now }
    private val feed = OwnerSharedWorkFeed(record) { now }

    private fun chat(): OwnerMissionHarness = OwnerMissionHarness().apply {
        deskHost.put("Argon4.pdf", pages = ARGON)
        deskHost.model = { OwnerWorkGeneration(SUMMARY_2, null, true) }
        toolsHost.model = { OwnerWorkGeneration(BRIEFING, null, true) }
        mission.onLaneOutcome = feed::lane
        mission.onSettled = feed::mission
    }

    // ── lanes ───────────────────────────────────────────────────────────────────────────────────────────

    @Test fun anOpenedPdfIsNotedWithTheNameTheDeskShowsAndTheDesksOwnOrderToOpenItAgain() {
        val h = chat()
        assertEquals("DESK", h.owner("Öffne das PDF Argon4"))
        val entry = record.entries().single()
        assertEquals(SharedWorkKind.DOCUMENT, entry.kind)
        assertEquals(SharedWorkAction.OPENED, entry.action)
        assertEquals("Argon4.pdf", entry.title)
        assertEquals(SharedWorkState.DONE, entry.state)
        assertEquals("Dokumentenablage", entry.provenance)
        assertEquals(now, entry.endedAt)
        assertEquals("Öffne das PDF Argon4", entry.resume)
        // The order really is one the desk takes: handing it back opens the same PDF and nothing else.
        assertEquals("DESK", h.owner(entry.resume!!))
        assertEquals(listOf("Argon4.pdf", "Argon4.pdf"), h.deskHost.shown.map { it.name })
        assertTrue(h.downloads().isEmpty())
    }

    @Test fun theNewsOverviewIsNotedAsResearchAndASavedFileAsADocumentWithoutAnOrderToRepeatIt() {
        val h = chat()
        assertEquals("TOOLS", h.owner("Fass mir die Nachrichten von heute zusammen"))
        now += 60_000L
        assertEquals("TOOLS", h.owner("Speichere das als PDF"))
        val (saved, news) = record.entries()
        assertEquals(SharedWorkKind.RESEARCH to SharedWorkAction.SUMMARISED, news.kind to news.action)
        assertEquals("Fass mir die Nachrichten von heute zusammen", news.resume)
        assertEquals(SharedWorkKind.DOCUMENT to SharedWorkAction.SAVED, saved.kind to saved.action)
        assertEquals(h.downloads().single(), saved.title)
        assertNull("saving again would write a second file", saved.resume)
        assertEquals("Arbeitswerkzeuge", saved.provenance)
    }

    @Test fun aQuestionARefusalOrAStopOfALaneIsNoPieceOfWork() {
        feed.lane(OwnerMissionLane.DESK, OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.CHOICE, "Welches soll ich öffnen?"), partOfMission = false)
        feed.lane(OwnerMissionLane.WORK, OwnerLaneOutcome.Failed("Der Internetzugriff wurde nicht freigegeben."), partOfMission = false)
        feed.lane(OwnerMissionLane.SEND, OwnerLaneOutcome.Stopped, partOfMission = false)
        feed.lane(OwnerMissionLane.DESK, OwnerLaneOutcome.Done(OwnerLaneResult.FilesFound("Argon", listOf("Argon4.pdf"))), partOfMission = false)
        assertTrue(record.entries().isEmpty())
    }

    @Test fun aMessageIsNotedWithItsRealStateAndNeverWithAnOrderToSendItAgain() {
        val file = OwnerSavedFile("Notiz.pdf", "Download", "content://x/1", "application/pdf")
        feed.lane(OwnerMissionLane.SEND, OwnerLaneOutcome.Done(OwnerLaneResult.Delivered("WhatsApp", "Anna", null,
            OwnerLaneResult.Delivered.State.SENT)), partOfMission = false)
        now += 1_000L
        feed.lane(OwnerMissionLane.WORK, OwnerLaneOutcome.Done(OwnerLaneResult.Delivered("Mailprogramm", ANNA, file,
            OwnerLaneResult.Delivered.State.HANDED_OVER)), partOfMission = false)
        val (prepared, sent) = record.entries()
        assertEquals(Triple(SharedWorkKind.SEND, SharedWorkAction.SENT, "Anna über WhatsApp"), Triple(sent.kind, sent.action, sent.title))
        assertEquals(Triple(SharedWorkKind.SEND, SharedWorkAction.PREPARED, "$ANNA über Mailprogramm"), Triple(prepared.kind, prepared.action, prepared.title))
        assertNull(sent.resume)
        assertNull(prepared.resume)
    }

    @Test fun theStorageLaneIsNotedByWhatItReallyDid() {
        feed.lane(OwnerMissionLane.DEEPCLEAN, OwnerLaneOutcome.Done(OwnerLaneResult.Storage("REPORT", 10L, 10L, 0, 0L, 0)), partOfMission = false)
        now += 1_000L
        feed.lane(OwnerMissionLane.DEEPCLEAN, OwnerLaneOutcome.Done(OwnerLaneResult.Storage("SAFE_CLEAN", 10L, 12L, 7, 2_000L, 0)), partOfMission = false)
        assertEquals(listOf(SharedWorkAction.CLEANED, SharedWorkAction.CHECKED), record.entries().map { it.action })
        record.entries().forEach { assertNull("a clean-up is never repeated by itself", it.resume) }
    }

    // ── missions ────────────────────────────────────────────────────────────────────────────────────────

    @Test fun aMissionIsOneEntryForTheWholeSequenceAndItsStepsAreNotNotedAgain() {
        val h = chat()
        assertEquals("MISSION", h.owner("Öffne das PDF Argon4, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an $ANNA"))
        val entry = record.entries().single()
        assertEquals(SharedWorkKind.MISSION, entry.kind)
        assertEquals("3 Schritte", entry.title)
        assertEquals(SharedWorkState.DONE, entry.state)
        assertEquals("Auftragsfolge", entry.provenance)
        assertNull(entry.resume)
        assertNull(entry.pausedAt)
    }

    @Test fun aMissionThatWaitsIsPausedAtItsStepAndBecomesDoneUnderTheSameEntry() {
        val h = chat()
        h.deskHost.put("Argon4 Datenblatt.pdf", folder = "Documents", pages = listOf("Datenblatt."))
        h.deskHost.put("Argon4 Handbuch.pdf", folder = "Documents", pages = listOf("Handbuch."))
        h.deskHost.library.removeAll { it.name == "Argon4.pdf" }
        val order = "Öffne das PDF Argon4, fass Seite 1 zusammen und speichere es als Word"
        assertEquals("MISSION", h.owner(order))
        assertTrue(h.mission.active)
        val paused = record.entries().single()
        assertEquals(SharedWorkState.PAUSED, paused.state)
        assertEquals("Schritt 1 von 2", paused.pausedAt)
        // Nothing was saved and nothing leaves the device: the owner's own sentence can take it up again.
        assertEquals(order, paused.resume)
        now += 120_000L
        assertEquals("DESK", h.owner("das zweite"))
        val done = record.entries().single()
        assertEquals(paused.id, done.id)
        assertEquals(SharedWorkState.DONE, done.state)
        assertEquals(paused.startedAt, done.startedAt)
        assertEquals(now, done.endedAt)
        assertNull(done.resume)
    }

    @Test fun aMissionWithAMailNeverGetsAnOrderToRunAgain() {
        val h = chat()
        h.deskHost.put("Argon4 Datenblatt.pdf", folder = "Documents", pages = listOf("Datenblatt."))
        h.deskHost.library.removeAll { it.name == "Argon4.pdf" }
        h.deskHost.put("Argon4 Handbuch.pdf", folder = "Documents", pages = listOf("Handbuch."))
        assertEquals("MISSION", h.owner("Öffne das PDF Argon4, fass Seite 1 zusammen, speichere es als Word und schick es per Mail an $ANNA"))
        val paused = record.entries().single()
        assertEquals(SharedWorkState.PAUSED, paused.state)
        assertNull("it would prepare the mail a second time", paused.resume)
        assertEquals("MISSION", h.owner("Stopp"))
        assertEquals(SharedWorkState.STOPPED, record.entries().single().state)
    }

    // ── the work journal ────────────────────────────────────────────────────────────────────────────────

    private fun episode(id: String, state: CockpitWorkJournal.State, startedAt: Long, updatedAt: Long = startedAt, source: String? = "Bericht.pdf") =
        CockpitWorkJournal.Episode(id, CockpitWorkJournal.Kind.DOCUMENT, "Fass das Dokument zusammen", startedAt, updatedAt, state, source = source)

    @Test fun journalEpisodesAreMirroredWithTheJournalsOwnNotionOfWhatIsOpen() {
        val hour = 3_600_000L
        feed.journal(listOf(
            episode("read", CockpitWorkJournal.State.READ, now - 30 * hour),
            episode("saved", CockpitWorkJournal.State.SAVED, now - 28 * hour),
            episode("drafted", CockpitWorkJournal.State.DRAFTED, now - 20 * hour, source = "Vertrag.pdf"),
            episode("aborted", CockpitWorkJournal.State.ABORTED, now - 10 * hour),
            episode("failed", CockpitWorkJournal.State.NO_MODEL, now - 9 * hour),
            episode("cut", CockpitWorkJournal.State.INTERRUPTED, now - 3 * hour, source = "Angebot.pdf"),
            episode("running", CockpitWorkJournal.State.DRAFTING, now - 60_000L),
            CockpitWorkJournal.Episode("tool", CockpitWorkJournal.Kind.TOOL, "Werkzeugabfrage: device.battery", now, now, CockpitWorkJournal.State.READ)))
        val byId = record.entries().associateBy { it.id.removePrefix("journal:") }
        assertEquals(setOf("read", "saved", "drafted", "aborted", "failed", "cut"), byId.keys)
        assertEquals(SharedWorkState.DONE to SharedWorkAction.OPENED, byId.getValue("read").let { it.state to it.action })
        assertEquals(SharedWorkState.DONE to SharedWorkAction.DRAFTED, byId.getValue("saved").let { it.state to it.action })
        assertEquals(SharedWorkState.PAUSED, byId.getValue("drafted").state)
        assertEquals("der Entwurf ist noch nicht gespeichert", byId.getValue("drafted").pausedAt)
        assertEquals(SharedWorkState.STOPPED, byId.getValue("aborted").state)
        assertEquals(SharedWorkState.FAILED, byId.getValue("failed").state)
        assertEquals(SharedWorkState.PAUSED, byId.getValue("cut").state)
        // Only the newest open episode carries the journal's own order – it is the one that sentence continues.
        assertEquals(OwnerSharedWorkFeed.JOURNAL_CONTINUE_ORDER, byId.getValue("cut").resume)
        assertEquals(listOf("cut"), byId.filterValues { it.resume != null }.keys.toList())
        assertEquals("Vertrag.pdf", byId.getValue("drafted").title)
        assertTrue(byId.values.all { it.provenance == "Arbeitsverlauf" && it.kind == SharedWorkKind.DOCUMENT })
        assertEquals(at.heinz.deviceai.cognitive.WorkDecisionRecall.Ask.ContinueOpen,
            at.heinz.deviceai.cognitive.WorkDecisionRecall.parse(OwnerSharedWorkFeed.JOURNAL_CONTINUE_ORDER))
    }

    @Test fun mirroringAgainChangesNothingAndASavedDraftBecomesDone() {
        val episodes = listOf(episode("drafted", CockpitWorkJournal.State.DRAFTED, now - 3_600_000L))
        feed.journal(episodes)
        val first = record.entries()
        feed.journal(episodes)
        assertEquals(first, record.entries())
        now += 60_000L
        feed.journal(listOf(episodes.single().copy(state = CockpitWorkJournal.State.SAVED, updatedAt = now)))
        val saved = record.entries().single()
        assertEquals(SharedWorkState.DONE, saved.state)
        assertNull(saved.resume)
        assertEquals(now, saved.endedAt)
    }

    // ── research, briefing, coding agents, computer, conversation ───────────────────────────────────────

    @Test fun theOtherTypedReportsBecomeEntriesOfTheirKind() {
        feed.research("Wärmepumpe Förderung 2026", sources = 4, startedAt = now - 90_000L)
        now += 1_000L
        feed.research("Etwas ohne Treffer", sources = 0, startedAt = now)
        now += 1_000L
        feed.briefing()
        now += 1_000L
        feed.codingAgentStarted("run-1", "Login-Fehler beheben")
        now += 600_000L
        feed.codingAgentEnded("run-1", delivered = true)
        now += 1_000L
        feed.computer("WhatsApp", OwnerComputerWorkflow.Status.PAUSED, now - 30_000L)
        feed.computer("WhatsApp", OwnerComputerWorkflow.Status.ASK_OWNER, now)
        now += 1_000L
        feed.conversation("chat-1")
        now += 5_000L
        feed.conversation("chat-1")
        val entries = record.entries()
        assertEquals(listOf(SharedWorkKind.CONVERSATION, SharedWorkKind.COMPUTER, SharedWorkKind.CODING_AGENT, SharedWorkKind.BRIEFING,
            SharedWorkKind.RESEARCH, SharedWorkKind.RESEARCH), entries.map { it.kind })
        val (talk, computer, agent, briefing, empty, research) = entries.let { Six(it[0], it[1], it[2], it[3], it[4], it[5]) }
        assertEquals("one entry per chat, extended with every answer", now, talk.endedAt)
        assertTrue(talk.startedAt < talk.endedAt)
        assertEquals("", talk.title)
        assertEquals(SharedWorkState.PAUSED to "WhatsApp", computer.state to computer.title)
        assertNull("a computer order is never repeated by itself", computer.resume)
        assertEquals(Triple(SharedWorkState.DONE, "Login-Fehler beheben", 600_000L), Triple(agent.state, agent.title, agent.endedAt - agent.startedAt))
        assertEquals("Gib mir mein Briefing", briefing.resume)
        assertEquals(SharedWorkState.FAILED, empty.state)
        assertEquals(SharedWorkState.DONE to "Wärmepumpe Förderung 2026", research.state to research.title)
        assertNull("a research run needs the owner's web approval and his own sentence", research.resume)
        assertFalse(entries.any { it.provenance.isBlank() })
    }

    private data class Six<T>(val a: T, val b: T, val c: T, val d: T, val e: T, val f: T)

    @Test fun theOrderToOpenAPdfAgainIsOneTheDeskTakesOrThereIsNone() {
        assertEquals("Öffne das PDF Argon4 Experimente", OwnerSharedWorkFeed.openOrder("Argon4 Experimente.pdf"))
        assertNull(OwnerSharedWorkFeed.openOrder(".pdf"))
        assertNull(OwnerSharedWorkFeed.openOrder("x".repeat(200) + ".pdf"))
        // A deliverable's date or a model text never is the title: the lane's display name is.
        val saved = OwnerSavedFile("Zusammenfassung Argon4 Seite 2 18.11.2026.docx", "Download", null, "application/x")
        val of = OwnerDeliverable(OwnerDeliverable.Kind.PAGE_SUMMARY, "Modelltext als Titel", LocalDate.of(2026, 11, 18), listOf("Text"))
        feed.lane(OwnerMissionLane.DESK, OwnerLaneOutcome.Done(OwnerLaneResult.FileSaved(saved, of, "Download", true)), partOfMission = false)
        assertEquals("Zusammenfassung Argon4 Seite 2 18.11.2026.docx", record.entries().single().title)
    }
}
