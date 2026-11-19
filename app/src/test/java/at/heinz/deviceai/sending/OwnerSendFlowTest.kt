package at.heinz.deviceai.sending

import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.cockpit.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prepare by intent → verify on screen → press once → confirm the effect → report. Fake device, real flow. */
class OwnerSendFlowTest {
    private val chat = SendPlan.WhatsApp("Anna Beispiel", "430005550101", "Ich komme später", send = true)
    private val file = OwnerSavedFile("Nachrichtenüberblick-2026-11-18.pdf", "Download", "content://fiktiv/1", "application/pdf")
    private val mail = SendPlan.Mail("anna.beispiel@example.org", "Anna Beispiel", "Nachrichtenüberblick-2026-11-18", "", file, send = true)
    private val steps = ArrayList<Pair<String, StepState>>()
    private fun flow(device: FakeSendDevice, grants: OwnerSendGrants) =
        OwnerSendFlow(device, grants, { _, label, state -> steps += label to state })

    @Test fun whatsAppIsVerifiedPressedOnceConfirmedAndReported() {
        val device = FakeSendDevice().apply { whatsAppWorks() }
        val grants = fakeGrants(on = arrayOf(SendChannel.WHATSAPP))
        val outcome = flow(device, grants).run(chat)
        assertEquals(SendOutcome.State.SENT, outcome.state)
        assertEquals(listOf(chat), device.opened)
        assertEquals(listOf(SendLocator("com.whatsapp:id/send", null)), device.pressed)
        assertEquals(1, device.returned)
        assertEquals(1, grants.sentToday())
        assertEquals("Gesendet an Anna Beispiel (WhatsApp): „Ich komme später“", OwnerSendReport.text(outcome))
        assertEquals(listOf("WhatsApp öffnen" to StepState.ACTIVE, "WhatsApp geöffnet" to StepState.DONE,
            "Entwurf prüfen" to StepState.ACTIVE, "Entwurf geprüft" to StepState.DONE,
            "Senden" to StepState.ACTIVE, "Gesendet" to StepState.DONE), steps)
    }

    @Test fun theChatMayNeedAMomentToAppearButTheWaitIsBounded() {
        val slow = FakeSendDevice().apply {
            whatsAppWorks()
            val ready = beforeClick
            beforeClick = { plan, now -> if (now < 2_000) null else ready(plan, now) }
        }
        assertEquals(SendOutcome.State.SENT, flow(slow, fakeGrants(on = arrayOf(SendChannel.WHATSAPP))).run(chat).state)
        val never = FakeSendDevice()
        val grants = fakeGrants(on = arrayOf(SendChannel.WHATSAPP))
        val outcome = flow(never, grants).run(chat)
        assertEquals(SendOutcome.State.NOT_SENT, outcome.state)
        assertEquals(SendProblem.NOT_IN_FRONT, outcome.problem)
        assertTrue(never.pressed.isEmpty())
        assertTrue("bounded wait", never.clock in OwnerSendFlow.VERIFY_MILLIS..OwnerSendFlow.VERIFY_MILLIS + 1_000)
        assertEquals(0, grants.sentToday())
    }

    @Test fun aWrongChatADifferentTextOrAMissingControlIsNeverPressed() {
        fun notSent(device: FakeSendDevice): SendOutcome {
            val grants = fakeGrants(on = arrayOf(SendChannel.WHATSAPP))
            val outcome = flow(device, grants).run(chat)
            assertEquals(SendOutcome.State.NOT_SENT, outcome.state)
            assertTrue(device.pressed.isEmpty())
            assertEquals(0, device.returned)
            assertEquals(0, grants.sentToday())
            return outcome
        }
        assertEquals(SendProblem.TITLE_MISMATCH, notSent(FakeSendDevice().apply { whatsAppWorks { "Anna Muster" } }).problem)
        assertEquals(SendProblem.TEXT_MISMATCH, notSent(FakeSendDevice().apply {
            beforeClick = { _, _ -> SendScreens.whatsAppChat("Anna Beispiel", "Alter Entwurf") } }).problem)
        val noControl = notSent(FakeSendDevice().apply {
            beforeClick = { _, _ -> SendScreens.whatsAppChat("Anna Beispiel", "Ich komme später", sendId = "x", sendLabel = "?") } })
        assertEquals(SendProblem.SEND_NOT_FOUND, noControl.problem)
        assertEquals("Der Entwurf an Anna Beispiel steht in WhatsApp bereit, gesendet habe ich nichts, weil ich den Senden-Knopf " +
            "nicht gefunden habe. Wenn das wieder passiert, sag „Sende-Diagnose“ – dann zeichne ich die Kennungen der " +
            "Bedienelemente für die Fehlersuche auf.", OwnerSendReport.text(noControl))
        assertEquals(SendProblem.NOT_ON_WHATSAPP, notSent(FakeSendDevice().apply {
            beforeClick = { _, _ -> SendScreens.dialog("com.whatsapp", "Die Nummer ist nicht bei WhatsApp.") } }).problem)
        assertEquals(SendProblem.ACCESSIBILITY_OFF, notSent(FakeSendDevice().apply { whatsAppWorks(); accessibility = false }).problem)
    }

    @Test fun theScreenIsCheckedAgainRightBeforeTheClick() {
        // The chat changes between the first verification and the click: the fresh observation stops the send.
        var looks = 0
        val device = FakeSendDevice().apply {
            beforeClick = { _, _ -> if (looks++ == 0) SendScreens.whatsAppChat("Anna Beispiel", "Ich komme später")
                else SendScreens.whatsAppChat("Thomas Muster", "Ich komme später") }
        }
        val outcome = flow(device, fakeGrants(on = arrayOf(SendChannel.WHATSAPP))).run(chat)
        assertEquals(SendOutcome.State.NOT_SENT, outcome.state)
        assertEquals(SendProblem.TITLE_MISMATCH, outcome.problem)
        assertTrue(device.pressed.isEmpty())
    }

    @Test fun theClickIsBoundToTheVeryReadingThatIsPressed() {
        // The chat changes after both verifications, in the reading the click itself is made on: nothing is pressed.
        var looks = 0
        val device = FakeSendDevice().apply {
            beforeClick = { _, _ -> if (looks++ < 2) SendScreens.whatsAppChat("Anna Beispiel", "Ich komme später")
                else SendScreens.whatsAppChat("Anna Beispiel", "Ich komme später und noch etwas") }
        }
        val grants = fakeGrants(on = arrayOf(SendChannel.WHATSAPP))
        val outcome = flow(device, grants).run(chat)
        assertEquals(SendOutcome.State.NOT_SENT, outcome.state)
        assertEquals(SendProblem.CLICK_REFUSED, outcome.problem)
        assertTrue(device.pressed.isEmpty())
    }

    @Test fun withoutTheGrantOrAsADraftNothingIsPressed() {
        val device = FakeSendDevice().apply { whatsAppWorks() }
        val draft = flow(device, fakeGrants()).run(chat.copy(send = false))
        assertEquals(SendOutcome.State.DRAFT_READY, draft.state)
        assertTrue(device.pressed.isEmpty())
        assertEquals("Der Entwurf an Anna Beispiel steht in WhatsApp bereit: „Ich komme später“. Gesendet habe ich nichts, weil " +
            "„WhatsApp-Nachrichten selbst senden“ ausgeschaltet ist. Einschalten kannst du es unter Einstellungen → Selbst senden.",
            OwnerSendReport.text(draft))
        assertTrue(OwnerSendReport.text(draft, draftWanted = true).endsWith("Gesendet habe ich nichts – du wolltest einen Entwurf."))
        // A plan that wants to send while the grant is off (revoked meanwhile) is stopped at the reservation.
        val revokedDevice = FakeSendDevice().apply { whatsAppWorks() }
        val revoked = flow(revokedDevice, fakeGrants()).run(chat)
        assertEquals(SendOutcome.State.NOT_SENT, revoked.state)
        assertEquals(SendProblem.GRANT_REVOKED, revoked.problem)
        assertTrue(revokedDevice.pressed.isEmpty())
    }

    @Test fun theDailyLimitStopsTheSendWithAnHonestMessage() {
        val grants = fakeGrants(on = arrayOf(SendChannel.WHATSAPP))
        repeat(OwnerSendGrants.DAILY_CAP) { assertTrue(grants.reserveSend(SendChannel.WHATSAPP)) }
        val device = FakeSendDevice().apply { whatsAppWorks() }
        val outcome = flow(device, grants).run(chat)
        assertEquals(SendOutcome.State.NOT_SENT, outcome.state)
        assertEquals(SendProblem.DAILY_CAP, outcome.problem)
        assertTrue(device.pressed.isEmpty())
        assertEquals("Der Entwurf an Anna Beispiel steht in WhatsApp bereit, gesendet habe ich nichts, weil die feste Tagesgrenze " +
            "von 30 selbst gesendeten Nachrichten erreicht ist.", OwnerSendReport.text(outcome))
    }

    @Test fun anUnconfirmedEffectIsReportedHonestlyAndNeverPressedAgain() {
        val stuck = FakeSendDevice().apply { whatsAppWorks(); afterClick = beforeClick }
        val grants = fakeGrants(on = arrayOf(SendChannel.WHATSAPP))
        val still = flow(stuck, grants).run(chat)
        assertEquals(SendOutcome.State.SEND_UNCONFIRMED, still.state)
        assertEquals(SendProblem.STILL_DRAFT, still.problem)
        assertEquals(1, stuck.pressed.size)
        assertEquals(0, stuck.returned)
        assertEquals("the pressed control counts towards the limit", 1, grants.sentToday())
        assertEquals("Der Entwurf steht in WhatsApp bereit, das Senden konnte ich nicht bestätigen, weil der Text danach noch im " +
            "Entwurf stand. Ich drücke nicht noch einmal.", OwnerSendReport.text(still))
        val noBubble = FakeSendDevice().apply { whatsAppWorks(); afterClick = { _, _ -> SendScreens.whatsAppChat("Anna Beispiel", "") } }
        val unclear = flow(noBubble, fakeGrants(on = arrayOf(SendChannel.WHATSAPP))).run(chat)
        assertEquals(SendProblem.NOT_CONFIRMED, unclear.problem)
        assertEquals(1, noBubble.pressed.size)
        assertTrue(OwnerSendReport.text(unclear).startsWith("Ich habe in WhatsApp auf Senden gedrückt, konnte das Senden an Anna Beispiel aber nicht bestätigen"))
        val gone = FakeSendDevice().apply { whatsAppWorks(); afterClick = { _, _ -> null } }
        assertEquals(SendProblem.LEFT_APP, flow(gone, fakeGrants(on = arrayOf(SendChannel.WHATSAPP))).run(chat).problem)
        val refused = FakeSendDevice().apply { whatsAppWorks(); pressAccepted = false }
        val notPressed = flow(refused, fakeGrants(on = arrayOf(SendChannel.WHATSAPP))).run(chat)
        assertEquals(SendOutcome.State.NOT_SENT, notPressed.state)
        assertEquals(SendProblem.CLICK_REFUSED, notPressed.problem)
    }

    @Test fun oneFlowSendsOneMessage() {
        val device = FakeSendDevice().apply { whatsAppWorks() }
        val flow = flow(device, fakeGrants(on = arrayOf(SendChannel.WHATSAPP)))
        flow.run(chat)
        assertThrows(IllegalStateException::class.java) { flow.run(chat) }
        assertEquals(1, device.pressed.size)
    }

    @Test fun theAppThatCannotBeOpenedIsReportedAndNothingElseHappens() {
        val device = FakeSendDevice().apply { openProblem = "WhatsApp hat den Entwurf nicht angenommen" }
        val outcome = flow(device, fakeGrants(on = arrayOf(SendChannel.WHATSAPP))).run(chat)
        assertEquals(SendOutcome.State.NOT_PREPARED, outcome.state)
        assertEquals("Ich konnte WhatsApp nicht öffnen: WhatsApp hat den Entwurf nicht angenommen. Es wurde nichts gesendet.",
            OwnerSendReport.text(outcome))
    }

    @Test fun gmailIsVerifiedPressedOnceAndConfirmedByTheClosedComposeScreen() {
        val device = FakeSendDevice().apply { gmailWorks() }
        val grants = fakeGrants(on = arrayOf(SendChannel.GMAIL))
        val outcome = flow(device, grants).run(mail)
        assertEquals(SendOutcome.State.SENT, outcome.state)
        assertEquals(listOf(SendLocator("com.google.android.gm:id/send", null)), device.pressed)
        assertEquals("Gesendet an Anna Beispiel (anna.beispiel@example.org) (Gmail): Betreff „Nachrichtenüberblick-2026-11-18“, " +
            "Anhang „Nachrichtenüberblick-2026-11-18.pdf“, ohne Begleittext", OwnerSendReport.text(outcome))
        assertEquals(1, grants.sentToday())
    }

    @Test fun gmailProblemsBeforeAndAfterTheClickAreNotCountedAsSent() {
        val wrongRecipient = FakeSendDevice().apply {
            beforeClick = { _, _ -> SendScreens.gmailCompose(listOf("anna.muster@example.org"), mail.subject, "", file.displayName) } }
        val before = flow(wrongRecipient, fakeGrants(on = arrayOf(SendChannel.GMAIL))).run(mail)
        assertEquals(SendOutcome.State.NOT_SENT, before.state)
        assertEquals(SendProblem.RECIPIENT_MISSING, before.problem)
        assertTrue(wrongRecipient.pressed.isEmpty())
        val noAttachment = FakeSendDevice().apply {
            beforeClick = { _, _ -> SendScreens.gmailCompose(listOf(mail.address), mail.subject, "") } }
        assertEquals(SendProblem.ATTACHMENT_MISSING, flow(noAttachment, fakeGrants(on = arrayOf(SendChannel.GMAIL))).run(mail).problem)
        val question = FakeSendDevice().apply { gmailWorks(); afterClick = { _, _ -> SendScreens.dialog("com.google.android.gm", "Ohne Text senden?") } }
        val asked = flow(question, fakeGrants(on = arrayOf(SendChannel.GMAIL))).run(mail)
        assertEquals(SendOutcome.State.SEND_UNCONFIRMED, asked.state)
        assertEquals(SendProblem.ERROR_SHOWN, asked.problem)
        assertEquals(1, question.pressed.size)
        val stays = FakeSendDevice().apply { gmailWorks(); afterClick = beforeClick }
        assertEquals(SendProblem.STILL_DRAFT, flow(stays, fakeGrants(on = arrayOf(SendChannel.GMAIL))).run(mail).problem)
        // The compose screen closes a little later: still confirmed within the bound.
        val late = FakeSendDevice().apply { gmailWorks(); val compose = beforeClick
            afterClick = { plan, since -> if (since < 1_500) compose(plan, since) else SendScreens.gmailInbox() } }
        assertEquals(SendOutcome.State.SENT, flow(late, fakeGrants(on = arrayOf(SendChannel.GMAIL))).run(mail).state)
        assertFalse(OwnerSendReport.text(asked).contains("Gesendet an"))
        // The screen cannot be observed after the click: a vanished Gmail window alone proves nothing.
        val blind = FakeSendDevice().apply { gmailWorks(); frontWhenAway = null }
        val unread = flow(blind, fakeGrants(on = arrayOf(SendChannel.GMAIL))).run(mail)
        assertEquals(SendOutcome.State.SEND_UNCONFIRMED, unread.state)
        assertEquals(SendProblem.UNREADABLE, unread.problem)
        assertEquals(1, blind.pressed.size)
    }
}
