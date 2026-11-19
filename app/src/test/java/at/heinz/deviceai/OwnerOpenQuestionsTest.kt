package at.heinz.deviceai

import at.heinz.deviceai.cognitive.OwnerConversationTone
import at.heinz.deviceai.sending.FakeContacts
import at.heinz.deviceai.sending.FakeContacts.Companion.phone
import at.heinz.deviceai.sending.FakeSendHost
import at.heinz.deviceai.sending.OwnerSendDirector
import at.heinz.deviceai.sending.SendChannel
import at.heinz.deviceai.sending.SendPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * An open question of a lane is served before the whole-sentence lanes, in the Activity's order: open questions →
 * conversation manner (OwnerTalk) → … → send lane. Real [OwnerTalk] in the personal tone and the real send lane on
 * the fake phone; fictional contacts only.
 */
class OwnerOpenQuestionsTest {
    private class Talk(private val send: FakeSendHost) : OwnerTalkHost {
        val replies = mutableListOf<String>()
        override val sessionId get() = send.sessionId
        override val assistantName: String? = null
        override fun nowMillis(): Long = 1_763_542_800_000L
        override fun zone(): ZoneId = ZoneId.of("Europe/Vienna")
        override fun acceptOwnerTurn(request: String) { send.ownerTurn++ }
        override fun reply(request: String, text: String) { replies += text }
        override fun greet(text: String) {}
        override fun activity(line: String) {}
        override fun quiet(): Boolean = false
        override fun confirmedFacts(): List<String> = emptyList()
    }

    private class Chat {
        val host = FakeSendHost(contacts = FakeContacts(listOf(phone(5, "Papa", "+43 000 555 0105"))))
            .apply { device.whatsAppWorks(); grant(SendChannel.WHATSAPP) }
        val send = OwnerSendDirector(host)
        val talkHost = Talk(host)
        val talk = OwnerTalk(talkHost, InMemoryOwnerTalkStore().apply { tone = OwnerConversationTone.PERSONAL })
        val questions = OwnerOpenQuestions(session = { host.sessionId }, turn = { host.ownerTurn })
            .lane("SEND", awaiting = { send.awaitingAnswer }) { send.handle(it) }

        /** The Activity's order of the lanes that matter here. */
        fun owner(text: String): String = when {
            questions.serve(text) -> "QUESTION"
            talk.handle(text) -> "TALK"
            send.handle(text) -> "SEND"
            else -> { host.ownerTurn++; "OTHER" }
        }
    }

    @Test fun theAnswerToTheSendLanesQuestionIsTheMessageNotAGreeting() {
        val chat = Chat()
        assertEquals("SEND", chat.owner("Schreib Papa auf WhatsApp"))
        assertEquals("Was soll ich Papa schreiben? Sag mir den Wortlaut.", chat.host.lastReply)
        assertEquals("SEND", chat.questions.waiting())
        assertEquals("QUESTION", chat.owner("Guten Morgen"))
        assertTrue("OwnerTalk did not greet back", chat.talkHost.replies.isEmpty())
        assertEquals(SendPlan.WhatsApp("Papa", "430005550105", "Guten Morgen", send = true), chat.host.device.opened.single())
        assertNull(chat.questions.waiting())
        // Without an open question the same words are a greeting again.
        assertEquals("TALK", chat.owner("Guten Morgen"))
        assertEquals(1, chat.talkHost.replies.size)
    }

    @Test fun aTurnThatIsNotTheAnswerKeepsItsOrdinaryRoute() {
        val chat = Chat()
        chat.owner("Schreib Papa auf WhatsApp, ich komme gleich")
        assertNull("nothing open after a sent message", chat.questions.waiting())
        val other = OwnerOpenQuestions(session = { "chat-1" }, turn = { 7L })
            .lane("DESK") { false }
        other.noteOutcome("DESK", OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.CHOICE, "Welche Datei meinst du?"))
        assertEquals("DESK", other.waiting())
        assertFalse("the lane declined: the turn goes on", other.serve("Wie spät ist es?"))
        assertNull("asked once, served once", other.waiting())
    }

    @Test fun aReportedQuestionCountsOnlyForTheVeryNextTurnOfTheSameChat() {
        var turn = 3L
        var session = "chat-1"
        val answers = mutableListOf<String>()
        val questions = OwnerOpenQuestions(session = { session }, turn = { turn })
            .lane("DESK") { answers += it; true }
        questions.noteOutcome("DESK", OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.DETAIL, "Welche Seite?"))
        turn++
        assertNull(questions.waiting())
        turn--
        session = "chat-2"
        assertNull(questions.waiting())
        session = "chat-1"
        questions.noteOutcome("DESK", OwnerLaneOutcome.Done(OwnerLaneResult.FileFound("a.pdf", null)))
        assertNull("a later outcome of the same lane closes its question", questions.waiting())
        assertTrue(answers.isEmpty())
    }
}
