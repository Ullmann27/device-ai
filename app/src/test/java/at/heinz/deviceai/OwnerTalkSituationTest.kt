package at.heinz.deviceai

import at.heinz.deviceai.cognitive.InMemoryOwnerInitiativeStore
import at.heinz.deviceai.cognitive.InMemoryOwnerSharedWorkStore
import at.heinz.deviceai.cognitive.OwnerAbsence
import at.heinz.deviceai.cognitive.OwnerConversationTone
import at.heinz.deviceai.cognitive.OwnerInitiative
import at.heinz.deviceai.cognitive.OwnerInitiativeLevel
import at.heinz.deviceai.cognitive.OwnerReturn
import at.heinz.deviceai.cognitive.OwnerSharedWorkRecord
import at.heinz.deviceai.cognitive.OwnerSmallTalk
import at.heinz.deviceai.cognitive.OwnerSpokenTime
import at.heinz.deviceai.cognitive.OwnerSuggestion
import at.heinz.deviceai.cognitive.OwnerSuggestionKind
import at.heinz.deviceai.cognitive.OwnerSuggestionProvider
import at.heinz.deviceai.cognitive.SharedWorkAction
import at.heinz.deviceai.cognitive.SharedWorkEntry
import at.heinz.deviceai.cognitive.SharedWorkKind
import at.heinz.deviceai.cognitive.SharedWorkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random

/**
 * „Gesetz Nummer eins – Persönlichkeit“ on the productive [OwnerTalk] with a fake clock, a fake chat and real stores:
 * the greeting by situation with at most one offer, the three initiative levels, „Wo waren wir?“ with a resume through
 * the lane's own order, small talk without claimed feelings, the follow-up suggestion after a result, and the true
 * conversation facts for the cloud brain. The host has no way to call a model or a tool; an accepted offer is an order
 * string handed back to the host – nothing else can happen.
 */
class OwnerTalkSituationTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(time: String): Long = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()

    private inner class Chat(time: String = "2026-11-19T08:10:00", lastSeen: String? = "2026-11-18T21:30:00") : OwnerTalkHost {
        var now = at(time)
        var quiet = true
        var messages: OwnerOpenMessageCount? = OwnerOpenMessageCount(0, "0")
        val said = mutableListOf<String>()
        val orders = mutableListOf<Pair<String, Boolean>>()
        val turns = mutableListOf<String>()
        val activity = mutableListOf<String>()
        override val sessionId = "chat-1"
        override val assistantName: String? = "Jarvis"
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = this@OwnerTalkSituationTest.zone
        override fun acceptOwnerTurn(request: String) { turns += request; talk.noteOwnerTurn(request) }
        override fun reply(request: String, text: String) { said += text }
        override fun greet(text: String) { said += text }
        override fun activity(line: String) { activity += line }
        override fun quiet(): Boolean = quiet
        override fun confirmedFacts(): List<String> = emptyList()
        override fun submit(order: String, onHeldResult: Boolean) { orders += order to onHeldResult }
        override fun openMessages(): OwnerOpenMessageCount? = messages
        /** Non-null = reports of work are still being written: what waits for them is kept here until the test lets it run. */
        var writing: MutableList<() -> Unit>? = null
        override fun whenWorkSettled(action: () -> Unit) { writing?.add(action) ?: action() }

        val store = InMemoryOwnerTalkStore().apply {
            name = OwnerTalkName("Heinz", "Nenn mich Heinz", at("2026-11-01T09:00:00"))
            lastSeenMillis = lastSeen?.let(::at) ?: 0L
            lastTurnMillis = lastSeenMillis
        }
        val workStore = InMemoryOwnerSharedWorkStore()
        val record = OwnerSharedWorkRecord(workStore) { now }
        val initiativeStore = InMemoryOwnerInitiativeStore()
        val initiative = OwnerInitiative(initiativeStore) { now }
        val talk = OwnerTalk(this, store, record, initiative)
        fun forward(minutes: Long) { now += minutes * 60_000L }
        fun to(time: String) { now = at(time) }
        /** He was away and comes back at [time]. */
        fun returnAt(time: String) { talk.onLeave(); to(time); talk.onReturn() }
        /** An owner turn in the Activity's order: the conversation manner first; otherwise some lane or the model takes it. */
        fun owner(text: String): Boolean = talk.handle(text).also { if (!it) acceptOwnerTurn(text) }
    }

    private fun entry(id: String, kind: SharedWorkKind, action: SharedWorkAction, title: String, state: SharedWorkState, ended: String,
                      resume: String? = null, pausedAt: String? = null) =
        SharedWorkEntry(id, kind, action, title, state, at(ended), at(ended), "Test", resume, pausedAt)

    private val pausedDocument = entry("doc", SharedWorkKind.DOCUMENT, SharedWorkAction.OPENED, "Argon4.pdf", SharedWorkState.PAUSED,
        "2026-11-18T21:28:00", "Öffne das PDF Argon4")
    private val news = entry("news", SharedWorkKind.RESEARCH, SharedWorkAction.SUMMARISED, "Nachrichten", SharedWorkState.DONE,
        "2026-11-19T07:40:00", "Fass mir die Nachrichten von heute zusammen")

    // ── the greeting by situation ───────────────────────────────────────────────────────────────────────

    @Test fun theOwnersOwnExamplesAreSaidWhenTheirFactsAreStored() {
        // „Guten Morgen, Heinz. Schön, dass du wieder da bist. … Wie geht's dir heute?“
        val morning = Chat().apply { store.greetingCount = 3 }
        morning.talk.onReturn()
        assertEquals("Guten Morgen, Heinz. Schön, dass du wieder da bist. Wir haben gestern Abend zuletzt gesprochen. Wie geht's dir heute?",
            morning.said.single())
        // „Hallo Heinz! Schön, dich wiederzusehen. Wir haben gestern Abend zuletzt gesprochen. …“
        val hello = Chat().apply { store.greetingCount = 4 }
        hello.talk.onReturn()
        assertEquals("Hallo Heinz! Schön, dich wiederzusehen. Wir haben gestern Abend zuletzt gesprochen. Was haben wir heute vor?", hello.said.single())
        // „Na, Heinz? Was haben wir heute vor?“ – when nothing is known about a pause.
        val na = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply { store.greetingCount = 5 }
        assertTrue(na.talk.handle("Hallo"))
        assertEquals("Na, Heinz? Was haben wir heute vor?", na.said.single())
        // „Heinz, wir haben gestern Abend bei … aufgehört. Möchtest du dort weitermachen?“
        val work = Chat().apply { record.note(pausedDocument) }
        work.talk.onReturn()
        assertEquals("Hallo Heinz. Du hast dich ja seit gestern nicht gerührt. Wir haben gestern Abend bei dem PDF „Argon4.pdf“ aufgehört. " +
            "Möchtest du dort weitermachen?", work.said.single())
        assertEquals(OwnerSuggestionKind.RESUME_WORK, work.talk.lastOfferKind)
    }

    @Test fun aGreetingCarriesAtMostOneOfferByAFixedOrder() {
        fun greeting(setup: Chat.() -> Unit): Chat = Chat().apply { talk.greetingExtra = { "Soll ich dir dein Briefing für heute geben?" }; setup(); talk.onReturn() }
        // Unfinished work first, …
        val all = greeting { record.note(pausedDocument); messages = OwnerOpenMessageCount(3, "m3") }
        assertEquals(OwnerSuggestionKind.RESUME_WORK, all.talk.lastOfferKind)
        assertFalse(all.said.single().contains("Nachricht") || all.said.single().contains("Briefing"))
        // … then open messages, …
        val open = greeting { messages = OwnerOpenMessageCount(3, "m3") }
        assertEquals("Hallo Heinz. Du hast dich ja seit gestern nicht gerührt. Es sind noch drei Nachrichten offen. Soll ich sie mit dir durchgehen?",
            open.said.single())
        assertEquals(OwnerSuggestionKind.OPEN_MESSAGES, open.talk.lastOfferKind)
        // … then the briefing lane's offer, which keeps its own question open (no order of this class).
        val briefing = greeting { }
        assertEquals("Hallo Heinz. Du hast dich ja seit gestern nicht gerührt. Soll ich dir dein Briefing für heute geben?", briefing.said.single())
        assertEquals(OwnerSuggestionKind.BRIEFING, briefing.talk.lastOfferKind)
        assertFalse(briefing.talk.handle("ja"))
        // Whatever is offered: one question per greeting.
        listOf(all, open, briefing).forEach { assertEquals(it.said.single(), 1, it.said.single().count { char -> char == '?' }) }
    }

    @Test fun theSupplierOfTheBriefingOfferIsNotAskedWhenSomethingElseIsOffered() {
        var asked = 0
        val chat = Chat().apply { talk.greetingExtra = { asked++; "Soll ich dir dein Briefing für heute geben?" }; record.note(pausedDocument) }
        chat.talk.onReturn()
        assertEquals("the day's offer is not used up by a greeting that does not say it", 0, asked)
    }

    @Test fun inZurueckhaltendTheGreetingOffersNothing() {
        val chat = Chat().apply {
            initiative.setLevel(OwnerInitiativeLevel.RESERVED)
            record.note(pausedDocument)
            messages = OwnerOpenMessageCount(3, "m3")
            talk.greetingExtra = { error("the briefing offer must not even be asked for") }
        }
        chat.talk.onReturn()
        assertEquals("Hallo Heinz, wie geht's dir? Du hast dich ja seit gestern nicht gerührt.", chat.said.single())
        assertNull(chat.talk.lastOfferKind)
        assertFalse("no offer is open", chat.talk.handle("ja"))
    }

    @Test fun inAktivAnAppointmentThatComesUpIsMentionedOnce() {
        val appointment = OwnerSuggestionProvider { now -> OwnerContextSuggestions.appointment("Zahnarzt", at("2026-11-19T15:30:00"), now, zone) }
        val active = Chat("2026-11-19T14:00:00", "2026-11-19T11:55:00").apply {
            initiative.setLevel(OwnerInitiativeLevel.ACTIVE)
            talk.contextSuggestions = listOf(appointment)
        }
        active.talk.onReturn()
        assertEquals("Hallo Heinz, wie geht's dir? Du hast dich ja zwei Stunden nicht gerührt. Übrigens: Um 15:30 Uhr steht „Zahnarzt“ in deinem Kalender.",
            active.said.single())
        active.returnAt("2026-11-19T14:45:00")
        assertFalse("mentioned once", active.said.last().contains("Zahnarzt"))
        val normal = Chat("2026-11-19T14:00:00", "2026-11-19T11:55:00").apply { talk.contextSuggestions = listOf(appointment) }
        normal.talk.onReturn()
        assertFalse(normal.said.single().contains("Zahnarzt"))
    }

    @Test fun theSameStructureIsNeverUsedThreeTimesInARowAndTheSameWordingNeverTwice() {
        val chat = Chat("2026-11-19T06:00:00", "2026-11-19T05:58:00")
        val shapes = mutableListOf<String>()
        repeat(14) { round ->
            // Alternating pools on purpose: a return after two hours, then a „Hallo“ a few minutes later.
            if (round % 2 == 0) chat.returnAt(java.time.Instant.ofEpochMilli(chat.now + 125 * 60_000L).atZone(zone).toLocalDateTime().toString())
            else { chat.forward(6); assertTrue(chat.talk.handle("Hallo")) }
            shapes += checkNotNull(chat.talk.lastGreetingShape)
        }
        assertEquals(14, chat.said.size)
        chat.said.zipWithNext().forEach { (first, second) -> assertNotEquals(first, second) }
        shapes.windowed(3).forEach { assertTrue(shapes.toString(), it.toSet().size > 1) }
        // A store that says the last two greetings had one structure: the next one is another, whatever the counter.
        val forced = Chat().apply { store.greetingShapes = "ASK_AWAY,ASK_AWAY"; store.greetingCount = 0 }
        forced.talk.onReturn()
        assertNotEquals("ASK_AWAY", forced.talk.lastGreetingShape)
    }

    @Test fun aSentenceAboutTheLastConversationNeedsTheStoredTimeOfHisLastTurn() {
        // He only left the app yesterday evening; his last own sentence is not stored: nothing is said about „zuletzt“.
        val unknown = Chat().apply { store.lastTurnMillis = 0L; store.greetingCount = 3 }
        unknown.talk.onReturn()
        assertEquals("Guten Morgen, Heinz. Schön, dass du wieder da bist. Seit gestern war es still hier. Wie geht's dir heute?", unknown.said.single())
        // His last sentence was two days ago although the app was open yesterday: the stored time is said, not a guess.
        val earlier = Chat().apply { store.lastTurnMillis = at("2026-11-17T20:00:00"); store.greetingCount = 3 }
        earlier.talk.onReturn()
        assertTrue(earlier.said.single(), earlier.said.single().contains("Wir haben vorgestern zuletzt gesprochen."))
        // „Zuletzt gesprochen“ means the turn before this greeting, also when he greets first.
        val greets = Chat().apply { store.greetingCount = 4 }
        assertTrue(greets.talk.handle("Hallo"))
        assertTrue(greets.said.single(), greets.said.single().contains("Wir haben gestern Abend zuletzt gesprochen."))
        assertEquals(greets.now, greets.store.lastTurnMillis)
        assertEquals(at("2026-11-18T21:30:00"), greets.store.previousTalkMillis)
    }

    // ── offers and their answers ────────────────────────────────────────────────────────────────────────

    @Test fun hisYesHandsTheLanesOwnOrderBackAndNothingElse() {
        val chat = Chat().apply { record.note(pausedDocument) }
        chat.talk.onReturn()
        chat.forward(1)
        assertTrue(chat.talk.handle("ja"))
        assertEquals(listOf("Öffne das PDF Argon4" to false), chat.orders)
        assertEquals(OwnerTalkRoute.OFFER_ACCEPTED, chat.talk.lastRoute)
        assertEquals("nothing more is said: the lane answers", 1, chat.said.size)
        assertFalse("answered once", chat.talk.handle("ja"))
    }

    @Test fun hisNoIsRememberedForTheThingAndForAWhileForTheKind() {
        val chat = Chat().apply { record.note(pausedDocument) }
        chat.talk.onReturn()
        chat.forward(1)
        assertTrue(chat.talk.handle("nein"))
        assertEquals("In Ordnung.", chat.said.last())
        assertTrue(chat.orders.isEmpty())
        // Three hours later and the next morning: the same work is not offered again.
        chat.returnAt("2026-11-19T11:30:00")
        assertNull(chat.talk.lastOfferKind)
        chat.returnAt("2026-11-20T08:30:00")
        assertNull(chat.talk.lastOfferKind)
        assertFalse(chat.said.takeLast(2).any { it.contains("Argon4") })
        // Asked directly it is still there – a no to an offer deletes nothing.
        assertTrue(chat.talk.handle("Wo waren wir stehen geblieben?"))
        assertTrue(chat.said.last(), chat.said.last().contains("Argon4.pdf"))
    }

    @Test fun anOrderInsteadOfAnAnswerEndsTheOfferWithoutCountingAsANo() {
        val chat = Chat().apply { record.note(pausedDocument) }
        chat.talk.onReturn()
        chat.forward(1)
        assertFalse(chat.owner("Öffne das PDF Zeta"))
        assertFalse("the offer is over", chat.talk.handle("ja"))
        assertFalse(chat.initiative.resting(OwnerSuggestionKind.RESUME_WORK))
    }

    @Test fun aQuestionOfAnotherPartOfTheAppEndsAnOpenOffer() {
        val chat = Chat().apply { record.note(pausedDocument) }
        chat.talk.onReturn()
        // The app's own greeting passing the Activity's message path changes nothing …
        chat.talk.noteAppLine(chat.said.single())
        // … but a question somebody else asks is what his „ja“ answers.
        chat.talk.noteAppLine("Neue Nachricht von Anna. Darf ich sie vorlesen?")
        assertFalse(chat.talk.handle("ja"))
        assertTrue(chat.orders.isEmpty())
    }

    @Test fun anOfferWaitsOnlyAFewMinutesAndOnlyInItsChat() {
        val chat = Chat().apply { record.note(pausedDocument) }
        chat.talk.onReturn()
        chat.forward(6)
        assertFalse(chat.talk.handle("ja"))
    }

    // ── small talk ──────────────────────────────────────────────────────────────────────────────────────

    @Test fun hisAnswerToTheGreetingIsAnsweredBrieflyWithoutAFeelingAndWithAnOfferToHelp() {
        val chat = Chat()
        chat.talk.onReturn()
        chat.forward(1)
        assertTrue(chat.talk.handle("gut, und dir?"))
        assertEquals("Das hört man gern. Bei mir läuft alles rund. Womit fangen wir an?", chat.said.last())
        assertEquals(OwnerTalkRoute.SMALLTALK, chat.talk.lastRoute)
        assertFalse(OwnerSmallTalk.claimsFeeling(chat.said.last()))
        // One turn later the same word is no answer to a greeting any more.
        chat.forward(1)
        assertFalse(chat.talk.handle("müde"))
    }

    @Test fun whenTheGreetingCarriedAnOfferAMoodWordIsNotTakenForItsAnswer() {
        val chat = Chat().apply { talk.greetingExtra = { "Soll ich dir dein Briefing für heute geben?" } }
        chat.talk.onReturn()
        chat.forward(1)
        assertFalse("„ok“ belongs to the briefing lane's question", chat.talk.handle("ok"))
        assertFalse(chat.talk.handle("gut"))
    }

    @Test fun inSachlichThereIsNoSmallTalkButTheRecordAndTheLevelStillAnswer() {
        val chat = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply { store.tone = OwnerConversationTone.FACTUAL; record.note(pausedDocument) }
        for (text in listOf("Hallo", "Wie geht's dir?", "Danke", "Gute Nacht")) assertFalse(text, chat.talk.handle(text))
        assertTrue(chat.talk.handle("Wo waren wir gestern?"))
        assertEquals("Gestern Abend gegen halb zehn haben wir bei dem PDF „Argon4.pdf“ aufgehört. Soll ich dort weitermachen?", chat.said.last())
        assertTrue(chat.talk.handle("Sei zurückhaltender"))
        assertEquals(OwnerInitiativeLevel.RESERVED, chat.initiative.level)
    }

    // ── „Wo waren wir?“ ─────────────────────────────────────────────────────────────────────────────────

    @Test fun machDaWeiterSaysWhereTheWorkStoppedAndHandsTheOrderToItsLane() {
        val chat = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply {
            record.note(entry("m", SharedWorkKind.MISSION, SharedWorkAction.SEQUENCE, "3 Schritte", SharedWorkState.PAUSED, "2026-11-19T08:40:00",
                "Öffne das PDF Argon4 und fass Seite 2 zusammen", "Schritt 2 von 3"))
        }
        assertTrue(chat.talk.handle("Mach da weiter"))
        assertEquals("Heute früh haben wir bei der Auftragsfolge (3 Schritte) aufgehört – Schritt 2 von 3. Ich mache dort weiter.", chat.said.single())
        assertEquals(listOf("Öffne das PDF Argon4 und fass Seite 2 zusammen" to false), chat.orders)
        assertEquals(OwnerTalkRoute.RESUME, chat.talk.lastRoute)
        assertTrue(chat.activity.last().startsWith("ARBEITSSTAND · Fortsetzen"))
    }

    @Test fun whileAMissionOrAChainWaitsForWeiterMachDaWeiterIsTheirs() {
        val waiting = object : OwnerTalkHost {
            val said = mutableListOf<String>()
            override val sessionId = "chat-1"
            override val assistantName: String? = null
            override fun nowMillis(): Long = at("2026-11-19T10:00:00")
            override fun zone(): ZoneId = this@OwnerTalkSituationTest.zone
            override fun acceptOwnerTurn(request: String) {}
            override fun reply(request: String, text: String) { said += text }
            override fun greet(text: String) {}
            override fun activity(line: String) {}
            override fun quiet(): Boolean = false
            override fun confirmedFacts(): List<String> = emptyList()
            override fun continuationOpen(): Boolean = true
        }
        val record = OwnerSharedWorkRecord(InMemoryOwnerSharedWorkStore()) { waiting.nowMillis() }.apply { note(pausedDocument) }
        val talk = OwnerTalk(waiting, InMemoryOwnerTalkStore(), record)
        assertFalse(talk.handle("Mach da weiter"))
        // Asking where we were is still answered: it continues nothing.
        assertTrue(talk.handle("Wo waren wir?"))
        assertEquals(1, waiting.said.size)
    }

    @Test fun aSentMessageIsMentionedAndNeverSentAgain() {
        val chat = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply {
            record.note(entry("s", SharedWorkKind.SEND, SharedWorkAction.SENT, "Anna über WhatsApp", SharedWorkState.DONE, "2026-11-18T17:10:00"))
        }
        assertTrue(chat.talk.handle("Weiter mit dem von gestern"))
        assertEquals(OwnerTalkRoute.RESUME_UNAVAILABLE, chat.talk.lastRoute)
        assertTrue(chat.said.single().contains("Eine Nachricht schicke ich nicht von mir aus noch einmal."))
        assertTrue(chat.orders.isEmpty())
    }

    @Test fun withAnEmptyRecordTheJournalsOwnRecallKeepsItsSentencesAndTheRestIsAnsweredHonestly() {
        val chat = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00")
        assertFalse("the work journal's recall takes this one", chat.talk.handle("Wo waren wir gestern?"))
        assertTrue(chat.said.isEmpty())
        assertTrue(chat.talk.handle("Mach da weiter"))
        assertEquals("Dazu habe ich nichts festgehalten.", chat.said.single())
        assertEquals(OwnerTalkRoute.RECALL_EMPTY, chat.talk.lastRoute)
        assertTrue(chat.orders.isEmpty())
    }

    @Test fun theAnswerWaitsForWorkThatIsStillBeingWrittenAndSaysTheStateItReallyReached() {
        val opened = entry("journal:1", SharedWorkKind.DOCUMENT, SharedWorkAction.OPENED, "Bericht.pdf", SharedWorkState.DONE, "2026-11-19T09:50:00")
        val chat = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply { record.note(opened); writing = mutableListOf() }
        assertTrue(chat.talk.handle("Was haben wir heute gemacht?"))
        assertEquals("his turn is shown at once", listOf("Was haben wir heute gemacht?"), chat.turns)
        assertTrue("nothing is said from a state that may be stale", chat.said.isEmpty())
        // The STOP of a moment ago reaches the record; only then the answer is built.
        chat.record.note(opened.copy(action = SharedWorkAction.DRAFTED, state = SharedWorkState.STOPPED, endedAt = chat.now,
            resume = OwnerSharedWorkFeed.JOURNAL_CONTINUE_ORDER))
        chat.writing!!.removeAt(0)()
        assertEquals("Gerade eben haben wir bei der Zusammenfassung von „Bericht.pdf“ abgebrochen. Soll ich dort weitermachen?", chat.said.single())
        // A turn that came before the record was settled: the late answer is dropped, not said into another turn.
        assertTrue(chat.talk.handle("Wo waren wir?"))
        chat.owner("Wie spät ist es?")
        chat.writing!!.removeAt(0)()
        assertEquals(1, chat.said.size)
    }

    @Test fun theOwnerCanMakeTheAppForgetTheSharedWork() {
        val chat = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply { record.noteAll(listOf(pausedDocument, news)) }
        assertTrue(chat.talk.handle("Vergiss, woran wir gearbeitet haben"))
        assertEquals("Erledigt. Was ich über unsere gemeinsame Arbeit festgehalten hatte, ist gelöscht – 2 Einträge. Deine Dateien und " +
            "Gespräche habe ich nicht angerührt.", chat.said.single())
        assertTrue(chat.record.entries().isEmpty())
        assertNull(chat.workStore.load())
        assertEquals(0, chat.talk.forgetWork())
    }

    @Test fun aCorrectionAfterTheAnswerGoesBackOrToAnotherDay() {
        val chat = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply { record.noteAll(listOf(pausedDocument.copy(state = SharedWorkState.DONE), news)) }
        assertTrue(chat.talk.handle("Wo waren wir?"))
        assertTrue(chat.said.last(), chat.said.last().contains("Nachrichten"))
        assertTrue(chat.talk.handle("nein, das davor"))
        assertEquals("Das Letzte war gestern Abend gegen halb zehn: Da haben wir das PDF „Argon4.pdf“ geöffnet.", chat.said.last())
        assertTrue(chat.talk.handle("ich meinte heute"))
        assertTrue(chat.said.last(), chat.said.last().contains("Nachrichten") && !chat.said.last().contains("Argon4"))
        // Without a recall right before it, the same words are not this.
        chat.owner("Wie spät ist es?")
        assertFalse(chat.talk.handle("das davor"))
    }

    // ── the level ───────────────────────────────────────────────────────────────────────────────────────

    @Test fun theSpokenLevelCommandsMoveBetweenTheThreeLevelsAndTakeBackAnOpenOffer() {
        val chat = Chat().apply { record.note(pausedDocument) }
        chat.talk.onReturn()
        assertTrue(chat.talk.handle("Schlag mir nichts mehr vor"))
        assertEquals(OwnerInitiativeLevel.RESERVED, chat.initiative.level)
        assertTrue(chat.said.last().startsWith("In Ordnung, ich halte mich zurück: keine Angebote und keine Vorschläge von mir aus."))
        assertFalse("the offer that was open is taken back with it", chat.talk.handle("ja"))
        assertTrue(chat.talk.handle("Du darfst mehr vorschlagen"))
        assertEquals(OwnerInitiativeLevel.NORMAL, chat.initiative.level)
        assertTrue(chat.talk.handle("Du darfst mehr vorschlagen"))
        assertEquals(OwnerInitiativeLevel.ACTIVE, chat.initiative.level)
        assertTrue(chat.talk.handle("Du darfst mehr vorschlagen"))
        assertTrue(chat.said.last().startsWith("Mehr geht nicht – ich schlage schon aktiv vor"))
        assertTrue(chat.activity.last().startsWith("INITIATIVE · Stufe „Aktiv“"))
    }

    // ── suggestions after a result ──────────────────────────────────────────────────────────────────────

    @Test fun aResearchResultWithSeveralSourcesGetsOneOfferOfAReportAndHisYesGoesToTheExportLane() {
        val chat = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00")
        assertTrue(chat.talk.afterResult(OwnerResultHint.Research(sources = 3)))
        assertEquals("Soll ich daraus einen PDF-Bericht machen?", chat.said.single())
        chat.forward(1)
        assertTrue(chat.talk.handle("ja"))
        assertEquals(listOf("ja"), chat.turns)
        assertEquals(listOf(OwnerTalk.PDF_ORDER to true), chat.orders)
    }

    @Test fun noSuggestionWithOneSourceWhileSomethingRunsInZurueckhaltendOrAfterHisNo() {
        fun chat() = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00")
        assertFalse(chat().talk.afterResult(OwnerResultHint.Research(sources = 1)))
        assertFalse(chat().talk.afterResult(OwnerResultHint.Other))
        assertFalse(chat().apply { quiet = false }.talk.afterResult(OwnerResultHint.Research(sources = 4)))
        assertFalse(chat().apply { initiative.setLevel(OwnerInitiativeLevel.RESERVED) }.talk.afterResult(OwnerResultHint.Research(sources = 4)))
        val declined = chat()
        assertTrue(declined.talk.afterResult(OwnerResultHint.Research(sources = 4)))
        assertTrue(declined.talk.handle("nein"))
        declined.forward(30)
        assertFalse(declined.talk.afterResult(OwnerResultHint.Research(sources = 5)))
        assertEquals(2, declined.said.size)
    }

    @Test fun aSuggestionNobodyProvidesTodayPassesTheSamePolicyWithAFakeProvider() {
        // Address in a message → maps: the policy slot exists; the app has no provider for it yet.
        val maps = OwnerSuggestionProvider { OwnerSuggestion(OwnerSuggestionKind.OPEN_MAPS,
            "In der Nachricht von Anna steht eine Adresse. Soll ich sie in der Karten-App öffnen?", "Öffne die Adresse in der Karten-App", "msg-1") }
        val normal = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply { talk.contextSuggestions = listOf(maps) }
        assertFalse("context suggestions need „Aktiv“", normal.talk.afterResult(OwnerResultHint.Other))
        val active = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply { initiative.setLevel(OwnerInitiativeLevel.ACTIVE); talk.contextSuggestions = listOf(maps) }
        assertTrue(active.talk.afterResult(OwnerResultHint.Other))
        assertEquals(OwnerSuggestionKind.OPEN_MAPS, active.talk.lastOfferKind)
        assertTrue(active.talk.handle("ja"))
        assertEquals(listOf("Öffne die Adresse in der Karten-App" to false), active.orders)
        // The same message is not suggested a second time.
        active.forward(30)
        assertFalse(active.talk.afterResult(OwnerResultHint.Other))
        // A provider that fails or returns nonsense is ignored.
        val broken = Chat("2026-11-19T10:00:00", "2026-11-19T09:58:00").apply {
            initiative.setLevel(OwnerInitiativeLevel.ACTIVE)
            talk.contextSuggestions = listOf(OwnerSuggestionProvider { error("no data") },
                OwnerSuggestionProvider { OwnerSuggestion(OwnerSuggestionKind.OPEN_MAPS, "Zeile\nzwei", "x") })
        }
        assertFalse(broken.talk.afterResult(OwnerResultHint.Other))
    }

    @Test fun theAppointmentMentionNeedsAFreshReadOfTodayAndANearStart() {
        val now = at("2026-11-19T14:00:00")
        fun mention(start: String, readAt: Long = now) = OwnerContextSuggestions.appointment("Zahnarzt", at(start), now, zone, readAt)
        assertEquals("Übrigens: Um 15:30 Uhr steht „Zahnarzt“ in deinem Kalender.", mention("2026-11-19T15:30:00")!!.text)
        assertNull("already started", mention("2026-11-19T13:30:00"))
        assertNull("more than three hours away", mention("2026-11-19T17:30:00"))
        assertNull("the calendar was read too long ago", mention("2026-11-19T15:30:00", readAt = now - 20 * 60_000L))
        assertNull("tomorrow", OwnerContextSuggestions.appointment("Zahnarzt", at("2026-11-20T00:30:00"), at("2026-11-19T22:30:00"), zone))
        assertEquals("Übrigens: Um 15:30 Uhr steht ein Termin in deinem Kalender.",
            OwnerContextSuggestions.appointment("  ", at("2026-11-19T15:30:00"), now, zone)!!.text)
    }

    @Test fun theAcknowledgementVaries() {
        val chat = Chat()
        val lines = (1..5).map { chat.talk.acknowledge() }
        assertEquals("Verstanden. Ich kümmere mich darum.", lines.first())
        lines.zipWithNext().forEach { (first, second) -> assertNotEquals(first, second) }
    }

    // ── the cloud brain's conversation facts ─────────────────────────────────────────────────────────────

    private fun Chat.facts(titles: Boolean) = OwnerConversationFacts(entries = { record.entries() }, level = { initiative.level },
        titlesAllowed = { titles }, zone = { zone })

    @Test fun theCloudBlockCarriesTrueConversationFactsAndTheRules() {
        val chat = Chat().apply { record.noteAll(listOf(pausedDocument, news)) }
        chat.to("2026-11-19T08:15:00")
        chat.owner("Erklär mir die Relativitätstheorie")
        val block = OwnerTalk.cloudBlock(chat.store, { emptyList() }, chat.facts(titles = false)) { false }!!
        assertTrue(block.startsWith("GESPRÄCHSTON „PERSÖNLICH“"))
        assertTrue(block, block.contains("– Tageszeit: früher Morgen (Donnerstag)."))
        assertTrue(block, block.contains("– Letztes Gespräch vor diesem: gestern Abend."))
        assertTrue(block, block.contains("– Gesprächsinitiative, vom Besitzer eingestellt: Normal"))
        assertTrue(block, block.contains("  · Recherche · erledigt · vorhin\n"))
        assertTrue(block, block.contains("  · Dokument · unterbrochen · gestern Abend\n"))
        for (rule in listOf("Keine erfundenen Erinnerungen", "Behaupte keine eigenen Gefühle und gib dich nicht als Mensch aus",
            "Wechsle die Formulierungen", "Antworte kurz, wenn er kurz schreibt", "Höchstens eine Frage pro Antwort", "Initiative nur nach der eingestellten Stufe"))
            assertTrue(rule, block.contains(rule))
        // Without the owner's consent for names: kinds, states and times only – no file name, and not his name either.
        assertFalse(block.contains("Argon4") || block.contains("Heinz"))
        assertTrue(block.contains("Namen sind nicht freigegeben"))
        val named = OwnerTalk.cloudBlock(chat.store, { emptyList() }, chat.facts(titles = true)) { false }!!
        assertTrue(named, named.contains("  · Dokument · unterbrochen · gestern Abend · „Argon4.pdf“\n"))
    }

    @Test fun theBlockIsByteStableWithinATurnAndChangesOnlyWithHisNextTurn() {
        val chat = Chat().apply { record.note(pausedDocument) }
        chat.to("2026-11-19T08:15:00")
        chat.owner("Erklär mir die Relativitätstheorie")
        fun block() = OwnerTalk.cloudBlock(chat.store, { emptyList() }, chat.facts(titles = true)) { false }
        val first = block()
        // The clock runs on, a lane finishes something, the app goes to the background: the turn's block stays the same.
        chat.forward(4)
        chat.record.note(news.copy(endedAt = chat.now, startedAt = chat.now))
        chat.talk.onLeave()
        assertEquals(first, block())
        chat.forward(50)
        chat.owner("Und die Quantenmechanik?")
        assertNotEquals(first, block())
        assertTrue(block()!!.contains("· Recherche · erledigt · vorhin"))
    }

    @Test fun theBlockIsBoundedAndSaysWhatIsNotKnownInsteadOfGuessing() {
        val chat = Chat("2026-11-19T10:00:00", lastSeen = null)
        // Before his first turn there is no time to compute from: the rules alone.
        val before = OwnerTalk.cloudBlock(chat.store, { emptyList() }, chat.facts(titles = true)) { false }!!
        assertFalse(before.contains("GESPRÄCHSLAGE"))
        assertTrue(before.contains("Keine erfundenen Erinnerungen"))
        chat.owner("Hallo, wie spät ist es?")
        repeat(60) { index -> chat.record.note(pausedDocument.copy(id = "e$index", title = "Sehr langer Dateiname ".repeat(6) + index,
            endedAt = chat.now - index * 60_000L, startedAt = chat.now - index * 60_000L)) }
        val block = OwnerTalk.cloudBlock(chat.store, { emptyList() }, chat.facts(titles = true)) { false }!!
        assertTrue(block.contains("– Letztes Gespräch vor diesem: keines gespeichert."))
        assertEquals(OwnerConversationFacts.MAX_ENTRIES, block.lines().count { it.startsWith("  · ") })
        val facts = block.substring(block.indexOf("GESPRÄCHSLAGE"))
        assertTrue("${facts.length}", facts.length <= OwnerConversationFacts.MAX_CHARS)
        // „Sachlich“: no block at all, as before.
        chat.store.tone = OwnerConversationTone.FACTUAL
        assertNull(OwnerTalk.cloudBlock(chat.store, { emptyList() }, chat.facts(titles = true)) { false })
    }

    // ── property: nothing in a greeting without a stored fact behind it ─────────────────────────────────

    private val timeWord = Regex("(?iu)(?<![\\p{L}])(gerade eben|vorhin|heute (?:früh|vormittag|mittag|nachmittag|abend|nacht|am späten abend)|" +
        "gestern(?: (?:früh|vormittag|mittag|nachmittag|abend|am späten abend|in der nacht))?|vorgestern|" +
        "am (?:montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)|vor (?:einer|gut einer|knapp zwei|zwei|drei) wochen?|" +
        "vor etwa einem monat|seit \\p{L}+ tagen|(?:gut |fast )?(?:eine|\\p{L}+) stunden?)(?![\\p{L}])")

    @Test fun everyWordAboutTimeWorkOrMessagesInAGreetingHasAStoredFactBehindIt() {
        val random = Random(1911)
        val base = at("2026-11-19T10:00:00")
        repeat(1_500) { round ->
            val chat = Chat("2026-11-19T10:00:00", lastSeen = null)
            chat.now = base + random.nextInt(14 * 60) * 60_000L - 4 * 3_600_000L
            // Any stored state, including none and nonsense (a clock that went backwards, an absurd gap).
            fun stamp(): Long = when (random.nextInt(7)) {
                0 -> 0L
                1 -> chat.now + random.nextInt(600) * 60_000L
                2 -> chat.now - (50L + random.nextInt(300)) * 24 * 3_600_000L
                else -> chat.now - random.nextInt(9 * 24 * 60) * 60_000L
            }
            chat.store.lastSeenMillis = stamp()
            chat.store.lastTurnMillis = stamp()
            chat.store.greetingCount = random.nextInt(50)
            chat.store.greetingVariant = random.nextInt(8) - 1
            chat.store.returnGreeting = random.nextInt(8) != 0
            chat.initiative.setLevel(OwnerInitiativeLevel.entries[random.nextInt(3)])
            val entries = (0 until random.nextInt(4)).map { index ->
                val ended = stamp().takeIf { it > 0L } ?: (chat.now - 60_000L)
                SharedWorkEntry("e$index", SharedWorkKind.entries[random.nextInt(SharedWorkKind.entries.size)],
                    SharedWorkAction.entries[random.nextInt(SharedWorkAction.entries.size)], listOf("Argon4.pdf", "Vertrag.pdf", "")[random.nextInt(3)],
                    SharedWorkState.entries[random.nextInt(4)], ended, ended, "Test", "Öffne das PDF X".takeIf { random.nextBoolean() })
            }
            chat.record.noteAll(entries)
            val open = random.nextInt(4).takeIf { random.nextBoolean() }
            chat.messages = open?.let { OwnerOpenMessageCount(it, "m$it") }
            val briefing = random.nextBoolean()
            if (briefing) chat.talk.greetingExtra = { "Soll ich dir dein Briefing für heute geben?" }
            val lastSeen = chat.store.lastSeenMillis
            val lastTurn = chat.store.lastTurnMillis
            if (random.nextBoolean()) chat.talk.onReturn() else chat.talk.handle("Hallo")
            val text = chat.said.singleOrNull() ?: return@repeat
            val absence = OwnerReturn.absence(lastSeen.takeIf { it > 0L }, chat.now, zone)
            // A turn can only be as old as his last sign of life or older; a store that says otherwise backs nothing.
            val talked = OwnerSpokenTime.at(lastTurn.takeIf { it in 1..lastSeen }, chat.now, zone)
            val backed = listOfNotNull(absence?.let(OwnerReturn::spoken), talked) +
                entries.mapNotNull { OwnerSpokenTime.at(it.endedAt, chat.now, zone) }
            timeWord.findAll(text).map { it.value.lowercase() }.forEach { word ->
                assertTrue("round $round: „$word“ has no stored fact behind it: $text · seen=$lastSeen turn=$lastTurn $entries",
                    backed.any { it.lowercase().contains(word) })
            }
            if (text.contains("zuletzt gesprochen")) assertTrue(text, talked != null &&
                (absence == OwnerAbsence.SinceYesterday || absence is OwnerAbsence.Days))
            if (text.contains("aufgehört") || text.contains("abgebrochen")) assertTrue(text, entries.any { it.unfinished && it.resume != null })
            Regex("„([^“]*)“").findAll(text).forEach { match -> assertTrue(text, entries.any { it.title == match.groupValues[1] }) }
            val messageWork = setOf(SharedWorkAction.SENT, SharedWorkAction.PREPARED, SharedWorkAction.SUMMARISED)
            if (text.contains("Nachricht")) assertTrue(text, (open ?: 0) > 0 || entries.any { it.unfinished && it.action in messageWork })
            if (text.contains("Briefing")) assertTrue(text, briefing || entries.any { it.unfinished && it.action == SharedWorkAction.BRIEFED })
            assertFalse(text, OwnerSmallTalk.claimsFeeling(text))
            assertTrue("more than one question: $text", text.split(Regex("(?<=[.!?])\\s+")).count { it.endsWith("?") && it.split(' ').size >= 3 } <= 1)
            if (chat.initiative.level == OwnerInitiativeLevel.RESERVED) assertNull(text, chat.talk.lastOfferKind)
        }
    }
}
