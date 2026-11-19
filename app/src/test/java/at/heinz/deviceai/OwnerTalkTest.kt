package at.heinz.deviceai

import at.heinz.deviceai.cognitive.ConversationRegister
import at.heinz.deviceai.cognitive.OwnerConversationTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The owner's conversation manner end to end on the productive [OwnerTalk] with a fake clock and a fake chat: the
 * setting and its commands, „Nenn mich …“ with provenance and revocation, and the greeting when he gets in touch –
 * every bucket of the pause, once per return, as a clause in front of an order, never while something is open, never
 * in „Sachlich“, with the extension clause. The host has no way to call a model, a tool or a plan: a greeting cannot
 * trigger one.
 */
class OwnerTalkTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(time: String): Long = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()

    private inner class Chat(time: String = "2026-11-19T10:00:00") : OwnerTalkHost {
        var now = at(time)
        var quiet = true
        var cloud = false
        var facts = listOf<String>()
        var factReads = 0
        /** `USER:…`, `REPLY:…`, `GREET:…`, `ACTIVITY:…` in order. */
        val log = mutableListOf<String>()
        override val sessionId = "chat-1"
        override val assistantName: String? = "Jarvis"
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = this@OwnerTalkTest.zone
        override fun acceptOwnerTurn(request: String) { log += "USER:$request"; talk.noteOwnerTurn(request) }
        override fun reply(request: String, text: String) { log += "REPLY:$text" }
        override fun greet(text: String) { log += "GREET:$text" }
        override fun activity(line: String) { log += "ACTIVITY:$line" }
        override fun quiet(): Boolean = quiet
        override fun confirmedFacts(): List<String> { factReads++; return facts }
        override fun cloudBrain(): Boolean = cloud

        val store = InMemoryOwnerTalkStore()
        val talk = OwnerTalk(this, store)
        fun lines(prefix: String) = log.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
        val replies get() = lines("REPLY:")
        val greetings get() = lines("GREET:")
        fun forward(minutes: Long) { now += minutes * 60_000L }
        fun to(time: String) { now = at(time) }
        /** An ordinary owner turn that some lane or the model answers. */
        fun turn(text: String) { log += "USER:$text"; talk.noteOwnerTurn(text) }
    }

    private fun chatWithHeinz(time: String = "2026-11-19T10:00:00") = Chat(time).apply {
        assertTrue(talk.handle("Nenn mich Heinz"))
        log.clear()
    }

    // ── setting and commands ────────────────────────────────────────────────────────────────────────────

    @Test fun theToneIsPersonalByDefaultAndTheOwnersSentenceSwitchesItBothWays() {
        val chat = Chat()
        assertEquals(OwnerConversationTone.PERSONAL, chat.talk.tone)
        assertTrue(chat.talk.handle("Sprich wieder sachlich"))
        assertEquals(OwnerConversationTone.FACTUAL, chat.store.tone)
        assertEquals("In Ordnung. Ich spreche ab jetzt sachlich mit dir und begrüße dich nicht mehr von mir aus. " +
            "Mit „Sprich persönlich mit mir“ änderst du das wieder.", chat.replies.single())
        assertTrue(chat.talk.handle("Sprich persönlich mit mir"))
        assertEquals(OwnerConversationTone.PERSONAL, chat.store.tone)
        assertTrue(chat.replies.last().startsWith("Alles klar – ab jetzt locker, wie unter alten Freunden."))
        assertTrue(chat.replies.last().contains("Bei Freigaben und Fehlern bleibe ich trotzdem eindeutig."))
        assertTrue(chat.talk.handle("Sprich persönlich mit mir"))
        assertEquals("Der Gesprächston ist schon „Persönlich“.", chat.replies.last())
        assertEquals(listOf("USER:Sprich wieder sachlich", "USER:Sprich persönlich mit mir", "USER:Sprich persönlich mit mir"),
            chat.log.filter { it.startsWith("USER:") })
        assertTrue(chat.lines("ACTIVITY:").first().startsWith("GESPRÄCHSTON · Sachlich"))
    }

    @Test fun anyOtherSentenceIsNotTaken() {
        val chat = Chat()
        for (other in listOf("Öffne das PDF Argon4", "Wie spät ist es?", "Erklär mir das sachlich", "Hallo, wie spät ist es?",
            "Schreib Anna auf WhatsApp, ich komme später", "Fass mir die Nachrichten von heute zusammen", "Ja", "Stopp"))
            assertFalse(other, chat.talk.handle(other))
        assertTrue(chat.log.isEmpty())
    }

    // ── his name ──────────────────────────────────────────────────────────────────────────────────────────

    @Test fun withoutHisWordThereIsNoName() {
        val chat = Chat()
        assertNull(chat.talk.name())
        assertEquals("Kein Name bekannt. Sag „Nenn mich …“.", chat.talk.nameSource())
        assertTrue(chat.talk.handle("Hallo"))
        assertEquals("Hallo! Schön, dass du da bist. Was liegt an?", chat.replies.single())
        assertFalse(chat.talk.localLine(ConversationRegister.CASUAL).contains("Heinz"))
    }

    @Test fun nennMichSetsTheNameWithItsProvenanceAndVergissTakesItBack() {
        val chat = Chat()
        assertTrue(chat.talk.handle("Nenn mich Heinz"))
        assertEquals("Heinz", chat.talk.name())
        assertEquals("Alles klar, Heinz. So spreche ich dich ab jetzt an – das hast du mir gerade selbst gesagt. " +
            "Mit „Vergiss meinen Namen“ nimmst du es zurück.", chat.replies.single())
        // Where the name comes from stays with it: his own sentence and when he said it (device clock).
        assertEquals(OwnerTalkName("Heinz", "Nenn mich Heinz", at("2026-11-19T10:00:00")), chat.store.name)
        assertEquals("Von dir gesagt: „Nenn mich Heinz“ (19.11.2026, 10:00).", chat.talk.nameSource())
        assertTrue(chat.talk.localLine(ConversationRegister.CASUAL).contains("mit Heinz"))

        assertTrue(chat.talk.handle("Vergiss meinen Namen"))
        assertNull(chat.talk.name())
        assertNull(chat.store.name)
        assertEquals("In Ordnung, den Namen habe ich gelöscht und spreche dich nicht mehr damit an.", chat.replies.last())
        assertFalse(chat.talk.localLine(ConversationRegister.CASUAL).contains("Heinz"))
        chat.forward(1)
        assertTrue(chat.talk.handle("Hallo"))
        assertFalse(chat.replies.last().contains("Heinz"))
    }

    @Test fun aNameFromTheConfirmedFactsIsUsedUntilHeSaysOtherwise() {
        val chat = Chat().apply { facts = listOf("Ich wohne in Wien", "Ich heiße Heinz") }
        assertEquals("Heinz", chat.talk.name())
        assertEquals("Aus deinen bestätigten Fakten.", chat.talk.nameSource())
        // Read once and kept for a moment: a prompt assembly does not parse the facts on every call.
        val reads = chat.factReads
        repeat(5) { chat.talk.name() }
        assertEquals(reads, chat.factReads)
        // His own „Nenn mich …“ wins over the fact.
        assertTrue(chat.talk.handle("Nenn mich Chef"))
        assertEquals("Chef", chat.talk.name())
        // „Vergiss meinen Namen“ also stops the name from the facts; the facts themselves are not touched – and he is told.
        assertTrue(chat.talk.handle("Vergiss meinen Namen"))
        assertNull(chat.talk.name())
        assertTrue(chat.replies.last().endsWith("In deinen bestätigten Fakten steht dein Name weiterhin; die ändere ich damit nicht."))
        assertEquals(listOf("Ich wohne in Wien", "Ich heiße Heinz"), chat.facts)
        assertEquals("Anrede mit Namen ist abgeschaltet („Vergiss meinen Namen“).", chat.talk.nameSource())
    }

    @Test fun theSettingsCanTypeAndDeleteTheNameWithTheSameCheck() {
        val chat = Chat()
        assertFalse(chat.talk.typeName("so"))
        assertFalse(chat.talk.typeName("  "))
        assertNull(chat.talk.name())
        assertTrue(chat.talk.typeName(" heinz "))
        assertEquals("Heinz", chat.talk.name())
        assertTrue(chat.talk.nameSource().startsWith("Von dir gesagt: „In den Einstellungen eingetragen: Heinz“"))
        chat.talk.forgetName()
        assertNull(chat.talk.name())
    }

    // ── the cloud brain gets the name only with his consent ─────────────────────────────────────────────

    @Test fun theCloudBlockCarriesTheNameOnlyUnderTheOwnersSwitch() {
        val chat = chatWithHeinz()
        var shareLocalMemory = false
        fun block() = OwnerTalk.cloudBlock(chat.store, { chat.facts }) { chat.store.nameToCloud || shareLocalMemory }
        // Default: the personal manner goes to the cloud brain, his name does not.
        assertTrue(block()!!.contains("seit dreißig Jahren"))
        assertFalse(block()!!.contains("Heinz"))
        assertTrue(block()!!.contains("rate keinen"))
        // His explicit switch – or his existing opt-in for memory marked „nur auf dem Gerät“ – covers the name.
        chat.store.nameToCloud = true
        assertTrue(block()!!.contains("„Heinz“"))
        chat.store.nameToCloud = false
        shareLocalMemory = true
        assertTrue(block()!!.contains("„Heinz“"))
        // The same for a name that comes from his confirmed facts.
        shareLocalMemory = false
        chat.store.name = null
        chat.facts = listOf("Ich heiße Heinz")
        assertFalse(block()!!.contains("Heinz"))
        chat.store.nameToCloud = true
        assertTrue(block()!!.contains("„Heinz“"))
        // Revoked: no name, whatever the switches say. „Sachlich“: no block at all.
        chat.store.addressByName = false
        assertFalse(block()!!.contains("Heinz"))
        chat.store.tone = OwnerConversationTone.FACTUAL
        assertNull(block())
    }

    @Test fun withTheCloudBrainTheOnDeviceLineIsEmptyAndNennMichSaysWhereTheNameGoes() {
        val chat = Chat().apply { cloud = true }
        assertTrue(chat.talk.handle("Nenn mich Heinz"))
        assertTrue(chat.replies.single().endsWith("An das Cloud-Gehirn gebe ich deinen Namen nur weiter, wenn du das in den " +
            "Einstellungen unter „Gesprächston“ erlaubst; die Begrüßung der App nennt ihn auch so."))
        assertEquals("", chat.talk.localLine(ConversationRegister.CASUAL))
        chat.cloud = false
        assertTrue(chat.talk.localLine(ConversationRegister.CASUAL).contains("mit Heinz"))
        chat.store.tone = OwnerConversationTone.FACTUAL
        assertEquals("", chat.talk.localLine(ConversationRegister.CASUAL))
    }

    // ── the greeting turn ───────────────────────────────────────────────────────────────────────────────

    @Test fun halloAfterTwoHoursIsAnsweredWithTheRealPause() {
        val chat = chatWithHeinz("2026-11-19T08:00:00")
        chat.to("2026-11-19T10:05:00")
        assertTrue(chat.talk.handle("Hallo"))
        assertEquals("Hallo Heinz, wie geht's dir? Du hast dich ja zwei Stunden nicht gerührt.", chat.replies.single())
        assertEquals(listOf("USER:Hallo", "REPLY:Hallo Heinz, wie geht's dir? Du hast dich ja zwei Stunden nicht gerührt.",
            "ACTIVITY:BEGRÜSSUNG · feste Antwort der App · kein Modell- oder Werkzeugaufruf · Pause aus der Geräteuhr berechnet"), chat.log)
    }

    @Test fun everyBucketOfThePauseIsSaidAsItIs() {
        fun greetingAfter(last: String, now: String): String {
            val chat = chatWithHeinz(last)
            chat.to(now)
            assertTrue(chat.talk.handle("Hallo Jarvis"))
            return chat.replies.single()
        }
        // A few minutes: he was never away – a greeting, not a word about time.
        assertEquals("Hallo Heinz! Schön, dass du da bist. Was liegt an?", greetingAfter("2026-11-19T09:58:00", "2026-11-19T10:00:00"))
        // Under an hour: „da bist du ja wieder“, no duration.
        assertEquals("Da bist du ja wieder, Heinz. Was gibt's?", greetingAfter("2026-11-19T09:20:00", "2026-11-19T10:00:00"))
        // From an hour on: the rounded duration.
        assertEquals("Hallo Heinz, wie geht's dir? Du hast dich ja gut zwei Stunden nicht gerührt.",
            greetingAfter("2026-11-19T07:40:00", "2026-11-19T10:00:00"))
        assertEquals("Hallo Heinz, wie geht's dir? Du hast dich ja seit gestern nicht gerührt.",
            greetingAfter("2026-11-18T21:00:00", "2026-11-19T08:00:00"))
        assertEquals("Hallo Heinz, wie geht's dir? Du hast dich ja seit drei Tagen nicht gerührt.",
            greetingAfter("2026-11-16T12:00:00", "2026-11-19T10:00:00"))
        // An implausible clock (set back, or an absurd gap): a greeting without any duration.
        assertEquals("Hallo Heinz! Schön, dass du da bist. Was liegt an?", greetingAfter("2026-11-19T11:00:00", "2026-11-19T10:00:00"))
        assertEquals("Hallo Heinz! Schön, dass du da bist. Was liegt an?", greetingAfter("2026-08-01T10:00:00", "2026-11-19T10:00:00"))
    }

    @Test fun theFirstGreetingEverSaysNothingAboutTime() {
        val chat = Chat()
        assertEquals(0L, chat.store.lastSeenMillis)
        assertTrue(chat.talk.handle("Guten Morgen"))
        assertEquals("Hallo! Schön, dass du da bist. Was liegt an?", chat.replies.single())
        assertEquals(chat.now, chat.store.lastSeenMillis)
    }

    @Test fun theWordingVariesFromGreetingToGreeting() {
        val chat = chatWithHeinz("2026-11-19T06:00:00")
        val said = mutableListOf<String>()
        repeat(6) {
            chat.forward(125)
            assertTrue(chat.talk.handle("Hallo"))
            said += chat.replies.last()
        }
        said.zipWithNext().forEach { (first, second) -> assertNotEquals(first, second) }
        assertTrue(said.toSet().size >= 3)
        said.forEach { assertTrue(it, it.contains("Heinz") && it.contains("wei Stunden")) }
    }

    // ── he comes back to the app ──────────────────────────────────────────────────────────────────────────

    @Test fun comingBackAfterTwoHoursIsGreetedOnceAndNotAgain() {
        val chat = chatWithHeinz("2026-11-19T08:00:00")
        chat.forward(3)
        chat.talk.onLeave()
        chat.to("2026-11-19T10:03:00")
        chat.talk.onReturn()
        assertEquals(listOf("Hallo Heinz, wie geht's dir? Du hast dich ja zwei Stunden nicht gerührt."), chat.greetings)
        assertEquals("Hallo Heinz, wie geht's dir? Du hast dich ja zwei Stunden nicht gerührt.", chat.talk.recentGreeting())
        // The same return again (a dialog closed, the Activity was recreated): nothing more.
        chat.talk.onReturn()
        chat.forward(1)
        chat.talk.onLeave()
        chat.talk.onReturn()
        assertEquals(1, chat.greetings.size)
        // His first order after this greeted return gets no clause either.
        chat.turn("Fass mir die Nachrichten von heute zusammen")
        assertNull(chat.talk.returnClause("chat-1", "Hier sind die Nachrichten von heute."))
        chat.forward(2)
        assertNull(chat.talk.recentGreeting())
    }

    @Test fun aRotationOrAShortAbsenceIsNoReturn() {
        val chat = chatWithHeinz()
        // Rotation, fold, a permission dialog: leave and return within the same moment.
        chat.talk.onLeave()
        chat.talk.onReturn()
        chat.forward(10)
        chat.talk.onReturn()
        chat.talk.onLeave()
        chat.forward(29)
        chat.talk.onReturn()
        assertTrue(chat.greetings.isEmpty())
        // Half an hour away is a return: „da bist du ja wieder“, still without a duration.
        chat.talk.onLeave()
        chat.forward(30)
        chat.talk.onReturn()
        assertEquals(listOf("Da bist du ja wieder, Heinz. Was gibt's?"), chat.greetings)
    }

    @Test fun whileSomethingIsOpenTheAppDoesNotGreet() {
        val chat = chatWithHeinz("2026-11-19T08:00:00")
        chat.talk.onLeave()
        chat.to("2026-11-19T11:00:00")
        chat.quiet = false       // a job runs, an approval question or a mission is open
        chat.talk.onReturn()
        assertTrue(chat.greetings.isEmpty())
        // His answer to the open question gets no clause in front of what follows.
        chat.turn("Ja")
        assertNull(chat.talk.returnClause("chat-1", "Die Mail ist gesendet."))
        chat.quiet = true
        chat.talk.onReturn()
        assertTrue("the return is over once he has answered", chat.greetings.isEmpty())
    }

    @Test fun beforeHisFirstSignOfLifeThereIsNothingToReturnFrom() {
        val chat = Chat()
        chat.talk.onLeave()
        chat.forward(600)
        chat.talk.onReturn()
        assertTrue(chat.greetings.isEmpty())
        assertEquals(0L, chat.store.lastSeenMillis)
    }

    @Test fun inSachlichThereIsNoGreetingBehaviourAtAll() {
        val chat = chatWithHeinz("2026-11-19T08:00:00")
        chat.store.tone = OwnerConversationTone.FACTUAL
        chat.talk.onLeave()
        chat.to("2026-11-19T11:00:00")
        chat.talk.onReturn()
        assertTrue(chat.greetings.isEmpty())
        // „Hallo“ keeps its old route (the model or the fixed start reply), and an order gets no clause.
        assertFalse(chat.talk.handle("Hallo"))
        chat.turn("Öffne das PDF Argon4")
        assertNull(chat.talk.returnClause("chat-1", "Ich öffne Argon4.pdf."))
        assertNull(chat.talk.recentGreeting())
    }

    @Test fun theReturnGreetingHasItsOwnSwitch() {
        val chat = chatWithHeinz("2026-11-19T08:00:00")
        assertTrue(chat.talk.handle("Begrüß mich nicht mehr"))
        assertFalse(chat.store.returnGreeting)
        assertEquals("In Ordnung, ich begrüße dich nicht mehr von mir aus. Mit „Begrüß mich wieder“ schaltest du es ein.", chat.replies.last())
        chat.talk.onLeave()
        chat.to("2026-11-19T11:00:00")
        chat.talk.onReturn()
        assertTrue(chat.greetings.isEmpty())
        chat.turn("Öffne das PDF Argon4")
        assertNull(chat.talk.returnClause("chat-1", "Ich öffne Argon4.pdf."))
        // He greets himself: answered personally by name, but without the pause he switched off.
        chat.to("2026-11-19T14:00:00")
        assertTrue(chat.talk.handle("Hallo"))
        assertTrue(chat.replies.last(), chat.replies.last().contains("Heinz"))
        assertFalse(chat.replies.last().contains("Stunden"))
        // The tone stays personal: the switch is separate from it.
        assertEquals(OwnerConversationTone.PERSONAL, chat.store.tone)
        assertTrue(chat.talk.handle("Begrüß mich wieder"))
        assertTrue(chat.store.returnGreeting)
        assertEquals("Gut, ich begrüße dich wieder, wenn du dich nach einer Pause meldest.", chat.replies.last())
    }

    // ── his first turn after the pause is already an order ──────────────────────────────────────────────

    @Test fun anOrderAfterAPauseGetsOneShortClauseInFrontOfTheAnswer() {
        val chat = chatWithHeinz("2026-11-19T08:00:00")
        chat.to("2026-11-19T10:02:00")
        chat.turn("Fass mir die Nachrichten von heute zusammen")
        assertEquals("Hallo Heinz – zwei Stunden nichts von dir gehört.",
            chat.talk.returnClause("chat-1", "Hier sind die Nachrichten von heute: …"))
        // Once: the next answer of the same turn, and the next turn, get none.
        assertNull(chat.talk.returnClause("chat-1", "Hier sind die Nachrichten von heute: …"))
        chat.forward(1)
        chat.turn("Speichere das als PDF")
        assertNull(chat.talk.returnClause("chat-1", "Gespeichert."))
        assertTrue("an order starts no greeting of the app", chat.greetings.isEmpty() && chat.replies.isEmpty())
    }

    @Test fun theClauseNeverStandsInFrontOfAQuestionAnApprovalOrAFailure() {
        for (answer in listOf("Ich verstehe das so: per E-Mail an Anna schicken. Soll ich das so machen?",
            "Ein PDF namens „Argon“ gibt es nicht, nur ähnliche Namen. Welches meinst du?",
            "Das Speichern ist fehlgeschlagen; es wurde nichts gespeichert.",
            "Mein Cloud-Gehirn hat diese Anfrage abgelehnt (Sicherheitsprüfung von Anthropic).")) {
            val chat = chatWithHeinz("2026-11-19T08:00:00")
            chat.to("2026-11-19T10:02:00")
            chat.turn("Schick das per Mail an Anna")
            assertNull(answer, chat.talk.returnClause("chat-1", answer))
            // Dropped, not postponed: it does not turn up in front of a later message.
            assertNull(chat.talk.returnClause("chat-1", "Erledigt."))
        }
    }

    @Test fun theClauseBelongsToItsChatAndDoesNotWaitForever() {
        val chat = chatWithHeinz("2026-11-19T08:00:00")
        chat.to("2026-11-19T10:02:00")
        chat.turn("Erklär mir die Relativitätstheorie")
        assertNull(chat.talk.returnClause("chat-2", "Gern."))
        chat.to("2026-11-19T13:00:00")
        chat.turn("Erklär mir die Relativitätstheorie")
        chat.forward(6)
        assertNull("an answer that comes much later comes without it", chat.talk.returnClause("chat-1", "Gern."))
    }

    @Test fun aGreetingOrAnAnswerToAnOpenQuestionIsNoOrder() {
        for (word in listOf("Ja", "Nein", "Stopp", "weiter", "Hallo")) {
            val chat = chatWithHeinz("2026-11-19T08:00:00")
            chat.to("2026-11-19T10:02:00")
            chat.turn(word)
            assertNull(word, chat.talk.returnClause("chat-1", "In Ordnung."))
        }
    }

    // ── the extension point ─────────────────────────────────────────────────────────────────────────────

    @Test fun anotherModuleMayAppendExactlyOneClauseToTheGreeting() {
        val chat = chatWithHeinz("2026-11-19T08:00:00")
        chat.talk.greetingExtra = { "Soll ich dir den Tag kurz zusammenfassen?" }
        chat.talk.onLeave()
        chat.to("2026-11-19T10:02:00")
        chat.talk.onReturn()
        // One question per greeting: with a clause that asks something, the opening does not also ask „wie geht's dir?“.
        assertEquals("Hallo Heinz. Du hast dich ja zwei Stunden nicht gerührt. Soll ich dir den Tag kurz zusammenfassen?",
            chat.greetings.single())
        // It takes the place of the greeting's own closing question: never two questions in a row.
        chat.to("2026-11-19T12:10:00")
        assertTrue(chat.talk.handle("Hallo"))
        assertEquals("Na Heinz, da bist du ja wieder. Zwei Stunden habe ich nichts von dir gehört. Soll ich dir den Tag kurz zusammenfassen?",
            chat.replies.last())
        // The clause in front of an order stays short: no extension there.
        chat.to("2026-11-19T15:00:00")
        chat.turn("Öffne das PDF Argon4")
        assertEquals("Hallo Heinz – fast drei Stunden nichts von dir gehört.", chat.talk.returnClause("chat-1", "Ich öffne Argon4.pdf."))
    }

    @Test fun aBrokenOrOversizedExtensionIsIgnored() {
        for (bad in listOf<() -> String?>({ null }, { "" }, { "  " }, { "x".repeat(OwnerTalk.MAX_EXTRA_CHARS + 1) },
            { "Zeile eins\nZeile zwei" }, { error("provider failed") })) {
            val chat = chatWithHeinz("2026-11-19T08:00:00")
            chat.talk.greetingExtra = bad
            chat.to("2026-11-19T10:02:00")
            assertTrue(chat.talk.handle("Hallo"))
            assertEquals("Hallo Heinz, wie geht's dir? Du hast dich ja zwei Stunden nicht gerührt.", chat.replies.single())
        }
    }

    @Test fun inSachlichTheExtensionIsNeverAsked() {
        val chat = chatWithHeinz("2026-11-19T08:00:00")
        var asked = 0
        chat.talk.greetingExtra = { asked++; "Soll ich dir den Tag kurz zusammenfassen?" }
        chat.store.tone = OwnerConversationTone.FACTUAL
        chat.talk.onLeave()
        chat.to("2026-11-19T10:02:00")
        chat.talk.onReturn()
        assertFalse(chat.talk.handle("Hallo"))
        assertEquals(0, asked)
    }
}
