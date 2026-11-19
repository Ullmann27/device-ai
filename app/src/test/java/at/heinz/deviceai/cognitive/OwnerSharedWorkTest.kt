package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerSharedWorkCommand.Command
import at.heinz.deviceai.cognitive.OwnerSharedWorkCommand.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random

/**
 * The shared-work record with time and provenance, the owner's sentences about it and the answers from it: bounded,
 * removable, never more than the entries say – and with no entry, no sentence about earlier work.
 */
class OwnerSharedWorkTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(time: String): Long = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-11-19T10:00:00")

    private fun entry(id: String, kind: SharedWorkKind, action: SharedWorkAction, title: String, state: SharedWorkState, ended: String,
                      resume: String? = null, pausedAt: String? = null) =
        SharedWorkEntry(id, kind, action, title, state, at(ended), at(ended), "Test", resume, pausedAt)

    private val opened = entry("doc", SharedWorkKind.DOCUMENT, SharedWorkAction.OPENED, "Argon4.pdf", SharedWorkState.DONE, "2026-11-18T21:28:00", "Öffne das PDF Argon4")
    private val news = entry("news", SharedWorkKind.RESEARCH, SharedWorkAction.SUMMARISED, "Nachrichten", SharedWorkState.DONE, "2026-11-19T08:30:00")
    private val paused = entry("mission", SharedWorkKind.MISSION, SharedWorkAction.SEQUENCE, "3 Schritte", SharedWorkState.PAUSED,
        "2026-11-17T15:00:00", "Öffne das PDF Argon4 und fass Seite 2 zusammen", "Schritt 2 von 3")
    private val sent = entry("send", SharedWorkKind.SEND, SharedWorkAction.SENT, "Anna über WhatsApp", SharedWorkState.DONE, "2026-11-18T17:10:00")

    // ── the record ────────────────────────────────────────────────────────────────────────────────────────

    @Test fun anEntryKeepsKindTitleStateTimesAndProvenanceAcrossAReload() {
        val store = InMemoryOwnerSharedWorkStore()
        OwnerSharedWorkRecord(store) { now }.note(paused.copy(provenance = "Auftragsfolge"))
        val read = OwnerSharedWorkRecord(store) { now }.entries().single()
        assertEquals(paused.copy(provenance = "Auftragsfolge"), read)
        assertTrue(read.unfinished)
    }

    @Test fun theSameIdIsUpdatedAndKeepsItsFirstStart() {
        val record = OwnerSharedWorkRecord(InMemoryOwnerSharedWorkStore()) { now }
        record.note(paused)
        record.note(paused.copy(state = SharedWorkState.DONE, startedAt = now, endedAt = now, pausedAt = null))
        val entry = record.entries().single()
        assertEquals(SharedWorkState.DONE, entry.state)
        assertEquals(paused.startedAt, entry.startedAt)
        assertEquals(now, entry.endedAt)
    }

    @Test fun theRecordIsBoundedToTheLastFortyAndNewestComesFirst() {
        val store = InMemoryOwnerSharedWorkStore()
        val record = OwnerSharedWorkRecord(store) { now }
        repeat(55) { index -> record.note(opened.copy(id = "e$index", title = "Datei$index.pdf", startedAt = now - 100_000L + index, endedAt = now - 100_000L + index)) }
        val entries = record.entries()
        assertEquals(OwnerSharedWorkRecord.MAX_ENTRIES, entries.size)
        assertEquals("Datei54.pdf", entries.first().title)
        assertEquals("Datei15.pdf", entries.last().title)
        assertTrue("bounded text", store.load()!!.length < 16_000)
    }

    @Test fun titlesAreOneCleanBoundedLineAndABrokenStoreIsAnEmptyRecord() {
        val record = OwnerSharedWorkRecord(InMemoryOwnerSharedWorkStore()) { now }
        record.note(opened.copy(title = "Zeile eins\nZeile „zwei“ " + "x".repeat(200), resume = "  ", pausedAt = "\u0007"))
        val entry = record.entries().single()
        assertFalse(entry.title.contains('\n') || entry.title.contains('„'))
        assertTrue(entry.title.length <= OwnerSharedWorkRecord.MAX_TITLE)
        assertNull(entry.resume)
        assertNull(entry.pausedAt)
        val broken = InMemoryOwnerSharedWorkStore().apply { save("{not json") }
        assertTrue(OwnerSharedWorkRecord(broken) { now }.entries().isEmpty())
    }

    @Test fun theOwnerCanRemoveEverything() {
        val store = InMemoryOwnerSharedWorkStore()
        val record = OwnerSharedWorkRecord(store) { now }
        record.noteAll(listOf(opened, news, paused))
        assertEquals(3, record.clear())
        assertTrue(record.entries().isEmpty())
        assertNull(store.load())
        assertEquals(0, record.clear())
    }

    // ── his sentences ─────────────────────────────────────────────────────────────────────────────────────

    @Test fun theOwnersSentencesAboutTheSharedWorkAreRecognised() {
        fun parse(text: String) = OwnerSharedWorkCommand.parse(text, "Jarvis")
        assertEquals(Command.Recall(Period.RECENT), parse("Wo waren wir?"))
        assertEquals(Command.Recall(Period.YESTERDAY), parse("Wo waren wir gestern?"))
        assertEquals(Command.Recall(Period.RECENT), parse("Jarvis, wo waren wir stehen geblieben?"))
        assertEquals(Command.Recall(Period.RECENT, list = true), parse("Was haben wir zuletzt gemacht?"))
        assertEquals(Command.Recall(Period.YESTERDAY, list = true), parse("Woran haben wir gestern gearbeitet?"))
        assertEquals(Command.Recall(Period.TODAY, list = true), parse("Was haben wir heute gemacht?"))
        assertEquals(Command.Recall(Period.BEFORE_YESTERDAY), parse("Wo waren wir vorgestern?"))
        assertEquals(Command.Recall(Period.WEEKDAY, DayOfWeek.TUESDAY, list = true), parse("Was haben wir am Dienstag gemacht?"))
        assertEquals(Command.Resume(Period.RECENT), parse("Mach da weiter"))
        assertEquals(Command.Resume(Period.YESTERDAY), parse("Weiter mit dem von gestern"))
        assertEquals(Command.Resume(Period.RECENT), parse("Mach weiter, wo wir aufgehört haben"))
        assertEquals(Command.Forget, parse("Vergiss, woran wir gearbeitet haben"))
        // Spoken forms.
        assertEquals(Command.Recall(Period.RECENT), parse("wo warn wir"))
        assertEquals(Command.Recall(Period.YESTERDAY, list = true), parse("na was ham wir gestern gemacht"))
        assertEquals(Command.Resume(Period.RECENT), parse("mach mal da weiter"))
    }

    @Test fun anythingElseKeepsItsRoute() {
        for (other in listOf("Wo waren wir, öffne dann das PDF", "JARVIS, wo waren wir gestern bei unserem Projekt? Nimm die ausgewählte PDF dazu.",
            "Wo ist mein Handy?", "Weiter", "Mach weiter", "Vergiss das", "Vergiss, dass ich in Wien wohne", "Was haben wir im Kühlschrank?",
            "Wo waren wir im Urlaub?", "Mach da weiter mit Seite 3 und speichere es", "das davor", "ich meinte heute", ""))
            assertNull(other, OwnerSharedWorkCommand.parse(other, "Jarvis"))
        // „Das davor“ and „ich meinte heute“ are corrections only right after a recall.
        assertEquals(Command.Earlier, OwnerSharedWorkCommand.parse("nein, das davor", "Jarvis", afterRecall = true))
        assertEquals(Command.Instead(Period.TODAY), OwnerSharedWorkCommand.parse("nein, ich meinte heute", "Jarvis", afterRecall = true))
        assertEquals(Command.Instead(Period.WEEKDAY, DayOfWeek.MONDAY), OwnerSharedWorkCommand.parse("ich meinte am Montag", "Jarvis", afterRecall = true))
    }

    // ── the answers ───────────────────────────────────────────────────────────────────────────────────────

    @Test fun anAnswerNamesTheWorkWithItsRealTime() {
        val answer = OwnerSharedWorkRecall.recall(listOf(opened, news, paused), Command.Recall(Period.YESTERDAY), now, zone)
        assertEquals("Gestern Abend gegen halb zehn haben wir das PDF „Argon4.pdf“ geöffnet.", answer.text)
        assertEquals(listOf(opened), answer.entries)
        val listed = OwnerSharedWorkRecall.recall(listOf(opened, news, paused), Command.Recall(Period.RECENT, list = true), now, zone)
        assertEquals("Heute früh gegen halb neun haben wir die Nachrichten zusammengefasst. Davor, gestern Abend gegen halb zehn, haben wir " +
            "das PDF „Argon4.pdf“ geöffnet. Und vorgestern haben wir bei der Auftragsfolge (3 Schritte) aufgehört – Schritt 2 von 3.", listed.text)
    }

    @Test fun unfinishedWorkIsSaidAsUnfinishedAndOfferedOnce() {
        val answer = OwnerSharedWorkRecall.recall(listOf(paused), Command.Recall(Period.RECENT), now, zone)
        assertEquals("Vorgestern haben wir bei der Auftragsfolge (3 Schritte) aufgehört – Schritt 2 von 3. Soll ich dort weitermachen?", answer.text)
        assertEquals(paused, answer.resumable)
        // Newer finished work is named first; the open piece is not lost.
        val both = OwnerSharedWorkRecall.recall(listOf(news, paused), Command.Recall(Period.RECENT), now, zone)
        assertEquals("Heute früh gegen halb neun haben wir die Nachrichten zusammengefasst. Offen ist außerdem noch etwas: Vorgestern " +
            "haben wir bei der Auftragsfolge (3 Schritte) aufgehört – Schritt 2 von 3.", both.text)
        assertEquals("Wir haben vorgestern bei der Auftragsfolge (3 Schritte) aufgehört – Schritt 2 von 3. Möchtest du dort weitermachen?",
            OwnerSharedWorkRecall.offer(paused, now, zone))
    }

    @Test fun withNoEntryThereIsNoSentenceAboutEarlierWork() {
        assertEquals("Dazu habe ich nichts festgehalten.", OwnerSharedWorkRecall.recall(emptyList(), Command.Recall(Period.RECENT), now, zone).text)
        assertEquals("Dazu habe ich nichts festgehalten.", OwnerSharedWorkRecall.recall(emptyList(), Command.Recall(Period.YESTERDAY), now, zone).text)
        // Nothing in the asked period: said so, and the newest entry named with its own time – not moved to „gestern“.
        val other = OwnerSharedWorkRecall.recall(listOf(paused), Command.Recall(Period.YESTERDAY, list = true), now, zone)
        assertEquals("Für gestern habe ich nichts festgehalten. Das Letzte war vorgestern: Da haben wir bei der Auftragsfolge (3 Schritte) " +
            "aufgehört – Schritt 2 von 3.", other.text)
        assertTrue(other.entries.isEmpty())
        assertEquals("Für Freitag habe ich nichts festgehalten. Das Letzte war vorgestern: Da haben wir bei der Auftragsfolge (3 Schritte) " +
            "aufgehört – Schritt 2 von 3.", OwnerSharedWorkRecall.recall(listOf(paused), Command.Recall(Period.WEEKDAY, DayOfWeek.FRIDAY), now, zone).text)
    }

    @Test fun anEntryWithoutAnHonestTimeIsNeverSpokenOf() {
        val future = opened.copy(id = "f", startedAt = now + 3_600_000L, endedAt = now + 3_600_000L)
        val ancient = opened.copy(id = "a", startedAt = at("2026-08-01T10:00:00"), endedAt = at("2026-08-01T10:00:00"))
        assertEquals("Dazu habe ich nichts festgehalten.",
            OwnerSharedWorkRecall.recall(listOf(future, ancient), Command.Recall(Period.RECENT), now, zone).text)
        assertNull(OwnerSharedWorkRecall.offerable(listOf(future.copy(state = SharedWorkState.PAUSED)), now, zone))
        assertNull(OwnerSharedWorkRecall.offer(future, now, zone))
    }

    @Test fun onlySafeUnfinishedRecentWorkIsOfferedAtAGreeting() {
        val draft = entry("d", SharedWorkKind.DOCUMENT, SharedWorkAction.DRAFTED, "Bericht.pdf", SharedWorkState.PAUSED, "2026-11-18T17:00:00", "Mach beim letzten offenen Punkt weiter")
        assertEquals(draft, OwnerSharedWorkRecall.offerable(listOf(draft, opened, news), now, zone))
        // Not a finished entry, not one without an order, not a message, not one that is a week old.
        assertNull(OwnerSharedWorkRecall.offerable(listOf(opened, news), now, zone))
        assertNull(OwnerSharedWorkRecall.offerable(listOf(draft.copy(resume = null)), now, zone))
        assertNull(OwnerSharedWorkRecall.offerable(listOf(sent.copy(state = SharedWorkState.PAUSED, resume = "Schreib Anna auf WhatsApp")), now, zone))
        assertNull(OwnerSharedWorkRecall.offerable(listOf(draft.copy(endedAt = at("2026-11-11T17:00:00"))), now, zone))
        // The same work finished later: nothing is left to take up.
        val finished = draft.copy(id = "d2", state = SharedWorkState.DONE, endedAt = at("2026-11-18T19:00:00"))
        assertNull(OwnerSharedWorkRecall.offerable(listOf(draft, finished), now, zone))
    }

    @Test fun aSentMessageIsOnlyMentionedNeverRepeated() {
        assertNull(OwnerSharedWorkRecall.resumable(listOf(sent)))
        val said = OwnerSharedWorkRecall.notResumable(sent, now, zone)
        assertEquals("Gestern Nachmittag gegen fünf haben wir eine Nachricht an Anna über WhatsApp gesendet. Eine Nachricht schicke ich " +
            "nicht von mir aus noch einmal. Sag mir einfach, was ich jetzt tun soll.", said)
        assertEquals(paused, OwnerSharedWorkRecall.resumable(listOf(news, sent, paused)))
        assertEquals("Vorgestern haben wir bei der Auftragsfolge (3 Schritte) aufgehört – Schritt 2 von 3. Ich mache dort weiter.",
            OwnerSharedWorkRecall.resuming(paused, now, zone))
    }

    @Test fun everyKindAndStateHasItsWording() {
        val all = SharedWorkAction.entries.flatMap { action -> SharedWorkState.entries.map { state ->
            SharedWorkEntry("x", SharedWorkKind.DOCUMENT, action, "Name", state, now, now, "Test") } }
        all.forEach { e ->
            val clause = OwnerSharedWorkWording.clause(e)
            assertTrue(clause, clause.startsWith("haben wir ") || clause.startsWith("hat es mit "))
            assertFalse(clause, clause.contains("null") || clause.contains("  "))
            assertNotNull(OwnerSharedWorkWording.topic(e.copy(title = "")))
        }
        SharedWorkKind.entries.forEach { assertTrue(OwnerSharedWorkWording.kindLabel(it).isNotBlank()) }
        SharedWorkState.entries.forEach { assertTrue(OwnerSharedWorkWording.stateLabel(it).isNotBlank()) }
    }

    // ── property: no word about time or work without a backing entry ───────────────────────────────────

    private val timeWord = Regex("(?iu)(?<![\\p{L}])(gerade eben|vorhin|heute (?:früh|vormittag|mittag|nachmittag|abend|nacht|am späten abend)|" +
        "gestern(?: (?:früh|vormittag|mittag|nachmittag|abend|am späten abend|in der nacht))?|vorgestern|" +
        "am (?:montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)|vor (?:einer|gut einer|knapp zwei|zwei|drei) wochen?|" +
        "vor etwa einem monat|gegen (?:halb )?\\p{L}+)(?![\\p{L}])")
    private val quoted = Regex("„([^“]*)“")

    @Test fun everyTimeAndEveryTitleInAnAnswerComesFromAnEntry() {
        val random = Random(20261119)
        val titles = listOf("Argon4.pdf", "Vertrag.pdf", "Bericht.docx", "", "Wärmepumpe", "Anna über WhatsApp", "3 Schritte")
        repeat(600) {
            val entries = (0 until random.nextInt(6)).map { index ->
                val ended = now - random.nextInt(70 * 24 * 60) * 60_000L + (if (random.nextInt(12) == 0) 5 * 24 * 3_600_000L else 0L)
                SharedWorkEntry("e$index", SharedWorkKind.entries[random.nextInt(SharedWorkKind.entries.size)],
                    SharedWorkAction.entries[random.nextInt(SharedWorkAction.entries.size)], titles[random.nextInt(titles.size)],
                    SharedWorkState.entries[random.nextInt(SharedWorkState.entries.size)], ended, ended, "Test",
                    resume = "Öffne das PDF X".takeIf { random.nextBoolean() }, pausedAt = "Schritt 1 von 2".takeIf { random.nextInt(4) == 0 })
            }
            val period = Period.entries[random.nextInt(Period.entries.size)]
            val command = Command.Recall(period, DayOfWeek.of(1 + random.nextInt(7)).takeIf { period == Period.WEEKDAY }, list = random.nextBoolean())
            val answer = OwnerSharedWorkRecall.recall(entries, command, now, zone, variation = random.nextInt(9), skip = random.nextInt(2))
            val texts = listOf(answer.text) + listOfNotNull(OwnerSharedWorkRecall.offerable(entries, now, zone)?.let { OwnerSharedWorkRecall.offer(it, now, zone, random.nextInt(5)) })
            // What may be said: the spoken time of an entry, and the owner's own word for the period he asked about.
            val backed = entries.mapNotNull { OwnerSpokenTime.atAbout(it.endedAt, now, zone)?.lowercase() } +
                entries.mapNotNull { OwnerSpokenTime.at(it.endedAt, now, zone)?.lowercase() }
            val asked = listOf("gestern", "vorgestern", "heute")
            texts.forEach { text ->
                timeWord.findAll(text).map { it.value.lowercase() }.forEach { word ->
                    val echo = text.startsWith("Für $word") || asked.any { text.lowercase().startsWith("für $it") && word == it }
                    assertTrue("„$word“ has no entry behind it: $text · $entries", echo || backed.any { it.contains(word) })
                }
                quoted.findAll(text).map { it.groupValues[1] }.forEach { title ->
                    assertTrue("„$title“ is no entry's title: $text", entries.any { it.title == title })
                }
                if (entries.none { OwnerSpokenTime.at(it.endedAt, now, zone) != null })
                    assertFalse("a sentence about work without any entry: $text", text.contains("haben wir") || text.contains("hat es mit"))
            }
        }
    }
}
