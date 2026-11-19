package at.heinz.deviceai.sending

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** On-screen verification, send-control location and effect check on synthetic WhatsApp and Gmail node trees. */
class OwnerSendScreensTest {
    private val text = "Ich komme später"
    private fun wa(screen: SendScreen?, name: String = "Anna Beispiel", digits: String = "430005550101", draft: String = text) =
        OwnerSendScreens.verifyWhatsApp(screen, name, digits, draft)
    private fun reason(result: SendVerification) = (result as SendVerification.Mismatch).reason

    @Test fun whatsAppChatWithTheRightTitleAndTextYieldsTheSendControlById() {
        val verified = wa(SendScreens.whatsAppChat("Anna Beispiel", text)) as SendVerification.Verified
        assertEquals(SendLocator("com.whatsapp:id/send", null), verified.send)
        // Case, surrounding blanks and invisible direction marks in the header do not matter; the words do.
        assertTrue(wa(SendScreens.whatsAppChat("‎ANNA  beispiel ", "$text ")) is SendVerification.Verified)
        // WhatsApp shows the bare number for a contact it does not know.
        assertTrue(wa(SendScreens.whatsAppChat("+43 000 555 0101", text)) is SendVerification.Verified)
    }

    @Test fun whatsAppSendControlIsFoundByGermanOrEnglishLabelWhenTheIdChanged() {
        val renamed = wa(SendScreens.whatsAppChat("Anna Beispiel", text, sendId = "conversation_send_v2")) as SendVerification.Verified
        assertEquals(SendLocator("com.whatsapp:id/conversation_send_v2", null), renamed.send)
        val english = wa(SendScreens.whatsAppChat("Anna Beispiel", text, sendId = null, sendLabel = "Send")) as SendVerification.Verified
        assertEquals(SendLocator(null, "send"), english.send)
        assertEquals(SendProblem.SEND_NOT_FOUND, reason(wa(SendScreens.whatsAppChat("Anna Beispiel", text, sendId = "x", sendLabel = "Abschicken?"))))
        val two = SendScreens.whatsAppChat("Anna Beispiel", text).let {
            it.copy(nodes = it.nodes + SendNode("com.whatsapp:id/send", "android.widget.ImageButton", description = "Senden", clickable = true)) }
        assertEquals(SendProblem.SEND_AMBIGUOUS, reason(wa(two)))
        val disabled = SendScreens.whatsAppChat("Anna Beispiel", text).let { screen ->
            screen.copy(nodes = screen.nodes.map { if (it.idName == "send") it.copy(enabled = false) else it }) }
        assertEquals(SendProblem.SEND_NOT_FOUND, reason(wa(disabled)))
    }

    @Test fun anythingUnexpectedInWhatsAppMeansNoClick() {
        assertEquals(SendProblem.NOT_IN_FRONT, reason(wa(null)))
        assertEquals(SendProblem.NOT_IN_FRONT, reason(wa(SendScreens.gmailInbox())))
        assertEquals(SendProblem.TITLE_MISMATCH, reason(wa(SendScreens.whatsAppChat("Anna Muster", text))))
        assertEquals(SendProblem.TITLE_MISMATCH, reason(wa(SendScreens.whatsAppChat("+43 000 555 0199", text))))
        assertEquals(SendProblem.TITLE_MISMATCH, reason(wa(SendScreens.whatsAppChat("Familie Beispiel", text))))
        assertEquals(SendProblem.TEXT_MISMATCH, reason(wa(SendScreens.whatsAppChat("Anna Beispiel", "Ich komme später nicht"))))
        // An empty field shows its hint: that is not the draft.
        assertEquals(SendProblem.TEXT_MISMATCH, reason(wa(SendScreens.whatsAppChat("Anna Beispiel", ""), draft = "Nachricht")))
        assertEquals(SendProblem.SCREEN_INCOMPLETE, reason(wa(SendScreens.whatsAppChat("Anna Beispiel", text, complete = false))))
        assertEquals(SendProblem.NOT_ON_WHATSAPP, reason(wa(SendScreens.dialog("com.whatsapp",
            "Die Telefonnummer +43 000 555 0101 ist nicht bei WhatsApp."))))
        assertEquals(SendProblem.DIALOG, reason(wa(SendScreens.dialog("com.whatsapp", "WhatsApp aktualisieren?"))))
        val noTitle = SendScreens.whatsAppChat("Anna Beispiel", text).let { screen ->
            screen.copy(nodes = screen.nodes.filter { it.idName != "conversation_contact_name" }) }
        assertEquals(SendProblem.TITLE_NOT_FOUND, reason(wa(noTitle)))
        val twoFields = SendScreens.whatsAppChat("Anna Beispiel", text).let { screen ->
            screen.copy(nodes = screen.nodes.map { if (it.idName == "entry") it.copy(viewId = null) else it } +
                SendNode(null, "android.widget.EditText", text = "Suche", editable = true)) }
        assertEquals(SendProblem.FIELD_NOT_FOUND, reason(wa(twoFields)))
        // Invisible nodes never count – neither as title nor as send control.
        val hidden = SendScreens.whatsAppChat("Anna Beispiel", text).let { screen ->
            screen.copy(nodes = screen.nodes.map { if (it.idName == "send") it.copy(visible = false) else it }) }
        assertEquals(SendProblem.SEND_NOT_FOUND, reason(wa(hidden)))
    }

    @Test fun whatsAppEffectNeedsAnEmptyFieldAndTheBubble() {
        assertEquals(SendEffect.CONFIRMED, OwnerSendScreens.whatsAppEffect(SendScreens.whatsAppChat("Anna Beispiel", "", listOf("Hallo", text)), text))
        assertEquals(SendEffect.STILL_DRAFT, OwnerSendScreens.whatsAppEffect(SendScreens.whatsAppChat("Anna Beispiel", text), text))
        assertEquals(SendEffect.PENDING, OwnerSendScreens.whatsAppEffect(SendScreens.whatsAppChat("Anna Beispiel", "", listOf("Hallo")), text))
        assertEquals(SendEffect.UNCLEAR, OwnerSendScreens.whatsAppEffect(null, text))
        assertEquals(SendEffect.ERROR, OwnerSendScreens.whatsAppEffect(SendScreens.dialog("com.whatsapp", "Senden fehlgeschlagen"), text))
        val long = "Sehr lange Nachricht ".repeat(20).trim()
        assertEquals(SendEffect.CONFIRMED, OwnerSendScreens.whatsAppEffect(
            SendScreens.whatsAppChat("Anna Beispiel", "", listOf(long.take(120) + "… Mehr lesen")), long))
    }

    private val to = "anna.beispiel@example.org"
    private val subject = "Nachrichtenüberblick 18.11.2026"
    private val body = "Erster Absatz der Zusammenfassung.\nZweiter Absatz mit Zahlen: 42."
    private fun gm(screen: SendScreen?, attachment: String? = null, text: String = body) =
        OwnerSendScreens.verifyGmail(screen, to, subject, text, attachment)

    @Test fun gmailDraftWithRecipientSubjectTextAndAttachmentYieldsTheSendControl() {
        val verified = gm(SendScreens.gmailCompose(listOf(to), subject, body)) as SendVerification.Verified
        assertEquals(SendLocator("com.google.android.gm:id/send", null), verified.send)
        // Older compose screens keep the recipients in one text field; a signature may contain the owner's own address.
        assertTrue(gm(SendScreens.gmailCompose(listOf("Anna.Beispiel@example.org"), subject, body, chips = false,
            signature = "\n\n--\nBesitzer · besitzer@example.org")) is SendVerification.Verified)
        assertTrue(gm(SendScreens.gmailCompose(listOf(to), subject, body, "Nachrichtenüberblick-2026-11-18.pdf"),
            attachment = "Nachrichtenüberblick-2026-11-18.pdf") is SendVerification.Verified)
        // Attachment-only mail: no accompanying text is required, the signature may stand alone.
        assertTrue(gm(SendScreens.gmailCompose(listOf(to), subject, "", "Bericht.pdf", signature = "Gesendet mit Gmail"),
            attachment = "Bericht.pdf", text = "") is SendVerification.Verified)
        val english = gm(SendScreens.gmailCompose(listOf(to), subject, body, sendId = null, sendLabel = "Send")) as SendVerification.Verified
        assertEquals(SendLocator(null, "send"), english.send)
        // A rich-text editor exposing each paragraph as its own node still shows the prepared text.
        val paragraphs = SendScreens.gmailCompose(listOf(to), subject, "", extra = body.split('\n').map {
            SendNode("com.google.android.gm:id/wc_paragraph", "android.view.View", text = it) })
        assertTrue(gm(paragraphs) is SendVerification.Verified)
    }

    @Test fun anythingUnexpectedInGmailMeansNoClick() {
        assertEquals(SendProblem.NOT_IN_FRONT, reason(gm(null)))
        assertEquals(SendProblem.NOT_COMPOSE, reason(gm(SendScreens.gmailInbox())))
        assertEquals(SendProblem.RECIPIENT_MISSING, reason(gm(SendScreens.gmailCompose(emptyList(), subject, body))))
        assertEquals(SendProblem.RECIPIENT_MISSING, reason(gm(SendScreens.gmailCompose(listOf("anna.muster@example.org"), subject, body))))
        assertEquals(SendProblem.RECIPIENT_EXTRA, reason(gm(SendScreens.gmailCompose(listOf(to, "thomas.muster@example.org"), subject, body))))
        assertEquals(SendProblem.SUBJECT_MISMATCH, reason(gm(SendScreens.gmailCompose(listOf(to), "Anderer Betreff", body))))
        assertEquals(SendProblem.SUBJECT_MISMATCH, reason(gm(SendScreens.gmailCompose(listOf(to), "", body))))
        assertEquals(SendProblem.BODY_MISMATCH, reason(gm(SendScreens.gmailCompose(listOf(to), subject, "Ganz anderer Text"))))
        assertEquals(SendProblem.ATTACHMENT_MISSING, reason(gm(SendScreens.gmailCompose(listOf(to), subject, body), attachment = "Bericht.pdf")))
        assertEquals(SendProblem.SEND_NOT_FOUND, reason(gm(SendScreens.gmailCompose(listOf(to), subject, body, sendId = "x", sendLabel = "Planen"))))
        assertEquals(SendProblem.DIALOG, reason(gm(SendScreens.dialog("com.google.android.gm", "Entwurf verwerfen?"))))
    }

    @Test fun gmailEffectIsAClosedComposeScreenWithoutDialogOrError() {
        val gmail = "com.google.android.gm"
        assertEquals(SendEffect.CONFIRMED, OwnerSendScreens.gmailEffect(null, "at.heinz.deviceai.continuumlocal"))
        // No Gmail window and no proof of what is in front instead: that is not a confirmation.
        assertEquals(SendEffect.UNCLEAR, OwnerSendScreens.gmailEffect(null, null))
        assertEquals(SendEffect.UNCLEAR, OwnerSendScreens.gmailEffect(null, gmail))
        assertEquals(SendEffect.CONFIRMED, OwnerSendScreens.gmailEffect(SendScreens.gmailInbox(), gmail))
        assertEquals(SendEffect.PENDING, OwnerSendScreens.gmailEffect(SendScreens.gmailCompose(listOf(to), subject, body), gmail))
        assertEquals(SendEffect.ERROR, OwnerSendScreens.gmailEffect(SendScreens.dialog(gmail, "Ohne Text senden?"), gmail))
        assertEquals(SendEffect.ERROR, OwnerSendScreens.gmailEffect(SendScreens.gmailCompose(listOf(to), subject, body,
            extra = listOf(SendNode(null, "android.widget.TextView", text = "Füge mindestens einen Empfänger hinzu"))), gmail))
        assertEquals(SendEffect.UNCLEAR, OwnerSendScreens.gmailEffect(SendScreens.whatsAppChat("Anna Beispiel", ""), "com.whatsapp"))
    }

    @Test fun theDiagnosisNamesControlsButNeverMessageTextOrNames() {
        val chat = SendScreens.whatsAppChat("Anna Beispiel", "Streng geheimer Entwurf", listOf("Private Nachricht von gestern"))
            .let { it.copy(nodes = it.nodes + SendNode("com.whatsapp:id/contact_row", "android.widget.LinearLayout",
                description = "Thomas Muster, zuletzt online heute", clickable = true)) }
        val report = OwnerSendDiagnostics.describe(chat, "2.26.1 (fiktiv)", "2026-11-18T10:00:00Z")
        listOf("com.whatsapp:id/send", "com.whatsapp:id/entry", "„Senden“", "„Anhängen“", "ImageButton",
            "Senden-Knopf: gefunden über View-ID com.whatsapp:id/send", "Chat-Titel (conversation_contact_name): vorhanden",
            "Version: 2.26.1 (fiktiv)", "(Eingabefeld, Inhalt nicht aufgezeichnet)", "Zeichen, nicht aufgezeichnet").forEach {
            assertTrue("missing $it in\n$report", report.contains(it)) }
        listOf("Anna", "Beispiel", "geheim", "Private", "Thomas", "Muster", "online").forEach {
            assertFalse("leaked $it in\n$report", report.contains(it)) }
        val mail = OwnerSendDiagnostics.describe(SendScreens.gmailCompose(listOf(to), subject, body, sendId = "x", sendLabel = "Planen"), null, "t")
        assertTrue(mail.contains("Senden-Knopf: NICHT gefunden (SEND_NOT_FOUND)"))
        assertTrue(mail.contains("Betreff-Feld (subject): vorhanden"))
        listOf("anna.beispiel", "Nachrichtenüberblick", "Absatz", "besitzer@").forEach { assertFalse("leaked $it in\n$mail", mail.contains(it)) }
    }
}
