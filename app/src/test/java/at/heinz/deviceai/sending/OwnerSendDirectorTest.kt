package at.heinz.deviceai.sending

import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.OwnerWorkGeneration
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.sending.FakeContacts.Companion.phone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole send lane on a fake phone: owner turn → contact → (question) → flow → report. Fictional data only. */
class OwnerSendDirectorTest {
    private val book = listOf(
        phone(1, "Anna Beispiel", "+43 000 555 0101"),
        phone(2, "Anna Muster", "+43 000 555 0102"),
        phone(3, "Thomas Muster", "+43 000 555 0103"),
        phone(4, "Mama", "+43 000 555 0104"))
    private val mails = listOf(
        OwnerContactMail(3, "Thomas Muster", "thomas.muster@example.org"),
        OwnerContactMail(1, "Anna Beispiel", "anna.beispiel@example.org"),
        OwnerContactMail(2, "Anna Muster", "anna.muster@example.org"))

    private fun newLane(vararg granted: SendChannel): Pair<FakeSendHost, OwnerSendDirector> {
        val host = FakeSendHost(contacts = FakeContacts(book, mails))
        host.device.whatsAppWorks()
        host.grant(*granted)
        return host to OwnerSendDirector(host)
    }

    // ── Standing permission ─────────────────────────────────────────────────────────────────────────────────

    @Test fun withTheStandingGrantADictatedMessageIsSentWithoutAnyQuestion() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        assertTrue(lane.handle("Schreib Thomas auf WhatsApp, ich komme später"))
        assertEquals(listOf("Gesendet an Thomas Muster (WhatsApp): „Ich komme später“"), host.replies)
        assertEquals(listOf(SendPlan.WhatsApp("Thomas Muster", "430005550103", "Ich komme später", send = true)), host.device.opened)
        assertEquals(1, host.device.pressed.size)
        assertFalse(lane.awaitingAnswer)
        assertEquals(listOf("SENDEN · WhatsApp · geprüft und gesendet"), host.activities)
        assertEquals(listOf("Kontakt suchen" to StepState.ACTIVE, "Kontakt gefunden" to StepState.DONE,
            "WhatsApp öffnen" to StepState.ACTIVE, "WhatsApp geöffnet" to StepState.DONE, "Entwurf prüfen" to StepState.ACTIVE,
            "Entwurf geprüft" to StepState.DONE, "Senden" to StepState.ACTIVE, "Gesendet" to StepState.DONE),
            host.steps.map { it.second to it.third })
        assertTrue("no model is needed", host.prompts.isEmpty())
    }

    @Test fun withoutTheGrantTheSameOrderStaysADraftAsBefore() {
        val (host, lane) = newLane()
        assertTrue(lane.handle("Schreib Thomas auf WhatsApp, ich komme später"))
        assertEquals(listOf(SendPlan.WhatsApp("Thomas Muster", "430005550103", "Ich komme später", send = false)), host.device.opened)
        assertTrue(host.device.pressed.isEmpty())
        assertTrue(host.lastReply.startsWith("Der Entwurf an Thomas Muster steht in WhatsApp bereit: „Ich komme später“. Gesendet habe ich nichts"))
        assertTrue(host.lastReply.contains("„WhatsApp-Nachrichten selbst senden“ ausgeschaltet"))
        assertEquals(0, host.grants.sentToday())
    }

    @Test fun anExplicitDraftWishStaysADraftEvenWithTheGrant() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        assertTrue(lane.handle("Bereite eine Nachricht an Thomas in WhatsApp vor: Bin gleich da"))
        assertTrue(host.device.pressed.isEmpty())
        assertTrue(host.lastReply.endsWith("Gesendet habe ich nichts – du wolltest einen Entwurf."))
    }

    @Test fun theGmailGrantDoesNotAllowWhatsAppAndTheOtherWayRound() {
        val (host, lane) = newLane(SendChannel.GMAIL)
        lane.handle("Schreib Thomas auf WhatsApp, ich komme später")
        assertTrue(host.device.pressed.isEmpty())
        val (other, otherLane) = newLane(SendChannel.WHATSAPP)
        assertFalse("no Gmail grant: the existing hand-over stays", otherLane.mail(OwnerMailHandover("Schick das per Mail an anna@example.org",
            "anna@example.org", null, "Betreff", "Text", null)))
        assertTrue(other.replies.isEmpty() && other.device.opened.isEmpty())
    }

    // ── Fixed rule (a): only dictated or confirmed text is sent ─────────────────────────────────────────────

    @Test fun modelWrittenTextIsShownOnceAndSentOnlyAfterAClearYes() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        assertTrue(lane.handle("Schreib Thomas auf WhatsApp was Nettes"))
        assertEquals(1, host.prompts.size)
        assertTrue(host.prompts.single().contains("was Nettes"))
        assertFalse("the address book never reaches the model", host.prompts.single().contains("Muster"))
        assertEquals("Ich würde Thomas Muster auf WhatsApp schreiben:\n\n„Alles Liebe zum Geburtstag, ich denke an dich!“\n\nSoll ich das so senden?", host.lastReply)
        assertTrue(host.device.opened.isEmpty() && host.device.pressed.isEmpty())
        assertTrue(lane.awaitingAnswer)
        assertTrue(lane.handle("Ja, schick es ab"))
        assertEquals("Gesendet an Thomas Muster (WhatsApp): „Alles Liebe zum Geburtstag, ich denke an dich!“", host.lastReply)
        assertEquals(1, host.device.pressed.size)
        assertEquals("only one generation", 1, host.prompts.size)
    }

    @Test fun withoutAClearYesTheModelTextIsNeverSent() {
        listOf("nein", "vielleicht später", "Wie spät ist es?", "ja aber schreib noch Bussi dazu").forEach { answer ->
            val (host, lane) = newLane(SendChannel.WHATSAPP)
            lane.handle("Schreib Thomas auf WhatsApp einen lieben Geburtstagsgruß")
            val handled = lane.handle(answer)
            assertTrue("$answer must not send", host.device.opened.isEmpty() && host.device.pressed.isEmpty())
            if (answer == "nein") { assertTrue(handled); assertEquals("In Ordnung. Es wurde nichts gesendet.", host.lastReply) }
            else assertFalse("$answer belongs to the other lanes", handled)
            // The proposal is gone: a later bare yes confirms nothing.
            host.ownerTurn++
            assertFalse(lane.handle("ja"))
            assertTrue(host.device.pressed.isEmpty())
        }
    }

    @Test fun aYesOnlyCountsForTheVeryNextTurnOfTheSameChatAndNotForever() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("Schreib Thomas auf WhatsApp was Nettes")
        host.ownerTurn++ // another lane answered a turn in between
        assertFalse(lane.handle("ja"))
        lane.handle("Schreib Thomas auf WhatsApp was Nettes")
        host.sessionId = "chat-2"
        assertFalse(lane.handle("ja"))
        host.sessionId = "chat-1"
        lane.handle("Schreib Thomas auf WhatsApp was Nettes")
        host.device.clock += OwnerSendDirector.PENDING_MILLIS + 1
        assertFalse(lane.handle("ja"))
        assertTrue(host.device.pressed.isEmpty())
    }

    @Test fun withoutTheGrantModelTextOnlyBecomesADraftAndWithoutAModelTheWordingIsAskedFor() {
        val (host, lane) = newLane()
        lane.handle("Schreib Thomas auf WhatsApp was Nettes")
        assertEquals(false, (host.device.opened.single() as SendPlan.WhatsApp).send)
        assertTrue(host.device.pressed.isEmpty())
        val (noModel, noModelLane) = newLane(SendChannel.WHATSAPP)
        noModel.model = false
        noModelLane.handle("Schreib Thomas auf WhatsApp was Nettes")
        assertTrue(noModel.lastReply.contains("Sprachmodell ist aber gerade nicht bereit"))
        assertTrue(noModel.prompts.isEmpty() && noModel.device.opened.isEmpty())
        val (failed, failedLane) = newLane(SendChannel.WHATSAPP)
        failed.generated = OwnerWorkGeneration("", "Zeitüberschreitung", false)
        failedLane.handle("Schreib Thomas auf WhatsApp was Nettes")
        assertTrue(failed.lastReply.startsWith("Ich konnte keinen passenden Text formulieren."))
        assertTrue(failed.device.opened.isEmpty())
    }

    @Test fun indirectSpeechIsSentInTheOwnersWordsOrAskedForNeverParaphrased() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("Sag dem Thomas per WhatsApp, dass ich später komme")
        assertEquals("Gesendet an Thomas Muster (WhatsApp): „Ich komme später“", host.lastReply)
        assertTrue(host.prompts.isEmpty())
        val (asked, askedLane) = newLane(SendChannel.WHATSAPP)
        askedLane.handle("Sag dem Thomas per WhatsApp, dass er mich anrufen soll")
        assertEquals("Was soll ich Thomas Muster schreiben? Sag mir den Wortlaut.", asked.lastReply)
        assertTrue(asked.device.opened.isEmpty() && asked.prompts.isEmpty())
        assertTrue(askedLane.handle("Ich bin ab fünf wieder erreichbar"))
        assertEquals("Gesendet an Thomas Muster (WhatsApp): „Ich bin ab fünf wieder erreichbar“", asked.lastReply)
    }

    @Test fun aWordingAnswerThatReadsLikeAnOrderToContinuumIsConfirmedFirst() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("Schreib Thomas auf WhatsApp")
        assertEquals("Was soll ich Thomas Muster schreiben? Sag mir den Wortlaut.", host.lastReply)
        assertTrue(lane.handle("Wann kommst du heute heim?"))
        assertEquals("Ich würde Thomas Muster auf WhatsApp schreiben:\n\n„Wann kommst du heute heim?“\n\nSoll ich das so senden?", host.lastReply)
        assertTrue(host.device.pressed.isEmpty())
        assertTrue(lane.handle("ja"))
        assertEquals("Gesendet an Thomas Muster (WhatsApp): „Wann kommst du heute heim?“", host.lastReply)
        // „Ruf mich an“ could be an order to Continuum as well: one question, then it is the owner's confirmed text.
        lane.handle("Schreib Mama auf WhatsApp")
        assertTrue(lane.handle("Ruf mich bitte an, wenn du Zeit hast"))
        assertTrue(host.lastReply.startsWith("Ich würde Mama auf WhatsApp schreiben:\n\n„Ruf mich bitte an, wenn du Zeit hast“"))
        assertEquals(1, host.device.pressed.size)
    }

    // ── Fixed rule (b): unresolved recipient → question, never a guess ──────────────────────────────────────

    @Test fun twoAnnasAreAskedAndTheAnswerDecides() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        assertTrue(lane.handle("Schreib Anna auf WhatsApp, ich komme später"))
        assertEquals("Zu „Anna“ gibt es mehrere passende Kontakte: Anna Beispiel, Anna Muster. Welchen meinst du?", host.lastReply)
        assertTrue(host.device.opened.isEmpty())
        assertTrue(lane.handle("Anna Muster"))
        assertEquals("Gesendet an Anna Muster (WhatsApp): „Ich komme später“", host.lastReply)
        assertEquals("430005550102", (host.device.opened.single() as SendPlan.WhatsApp).waDigits)
    }

    @Test fun anAnswerThatNamesNoCandidateSendsNothing() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("Schreib Anna auf WhatsApp, ich komme später")
        assertFalse(lane.handle("Anna"))
        assertFalse(lane.awaitingAnswer)
        host.ownerTurn++
        assertFalse(lane.handle("die zweite"))
        assertTrue(host.device.opened.isEmpty())
    }

    @Test fun unknownRecipientsGroupsSeveralRecipientsAndPronounsNeverSend() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("Schreib meinem Chef auf WhatsApp, ich komme später")
        assertEquals("Ich habe keinen Kontakt „meinem Chef“ gefunden. Nenn mir den Namen so, wie er in deinen Kontakten steht. " +
            "Es wurde nichts vorbereitet und nichts gesendet.", host.lastReply)
        lane.handle("Schreib in die Familiengruppe auf WhatsApp, ich komme später")
        assertEquals(OwnerSendIntentParser.REFUSED_GROUP, host.lastReply)
        lane.handle("Schreib Thomas und Mama auf WhatsApp, ich komme später")
        assertEquals(OwnerSendIntentParser.REFUSED_SEVERAL, host.lastReply)
        lane.handle("Schreib Thomas nicht auf WhatsApp, dass ich komme")
        assertEquals(OwnerSendIntentParser.REFUSED_NEGATED, host.lastReply)
        assertTrue(host.device.opened.isEmpty() && host.contacts.reads == 1)
        lane.handle("Schreib ihr auf WhatsApp, ich komme später")
        assertEquals("An wen soll die WhatsApp gehen? Nenn mir den Namen, wie er in deinen Kontakten steht.", host.lastReply)
        assertTrue(lane.handle("der Mama"))
        assertEquals("Gesendet an Mama (WhatsApp): „Ich komme später“", host.lastReply)
    }

    @Test fun spokenOrderWithoutSeparatorIsSplitByTheAddressBook() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("schick eine WhatsApp an Thomas Muster bin in zehn Minuten da")
        assertEquals("Gesendet an Thomas Muster (WhatsApp): „Bin in zehn Minuten da“", host.lastReply)
        val (none, noneLane) = newLane(SendChannel.WHATSAPP)
        noneLane.handle("schick eine WhatsApp an Berta bin in zehn Minuten da")
        assertTrue(none.lastReply.startsWith("Ich habe keinen Kontakt „Berta“ gefunden."))
        assertTrue(none.device.opened.isEmpty())
    }

    @Test fun contactsAreReadOnlyAfterTheOwnerAllowedItOnce() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        host.contactsGranted = false
        host.grantOnRequest = false
        lane.handle("Schreib Thomas auf WhatsApp, ich komme später")
        assertEquals(1, host.contactRequests)
        assertEquals(OwnerSendDirector.CONTACTS_DENIED, host.lastReply)
        assertEquals(0, host.contacts.reads)
        host.grantOnRequest = true
        lane.handle("Schreib Thomas auf WhatsApp, ich komme später")
        assertEquals("Gesendet an Thomas Muster (WhatsApp): „Ich komme später“", host.lastReply)
        lane.handle("Schreib Mama auf WhatsApp, bin gleich da")
        assertEquals("asked only while not granted", 2, host.contactRequests)
        assertTrue(OwnerSendDirector.CONTACTS_EXPLANATION.contains("nur auf diesem Gerät"))
    }

    @Test fun oneOrderIsOneMessageAndTheDailyLimitHolds() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        repeat(OwnerSendGrants.DAILY_CAP) { lane.handle("Schreib Thomas auf WhatsApp, Nachricht Nummer $it") }
        assertEquals(OwnerSendGrants.DAILY_CAP, host.device.pressed.size)
        assertEquals(OwnerSendGrants.DAILY_CAP, host.device.opened.size)
        lane.handle("Schreib Thomas auf WhatsApp, eine zu viel")
        assertEquals(OwnerSendGrants.DAILY_CAP, host.device.pressed.size)
        assertTrue(host.lastReply.contains("weil die feste Tagesgrenze von 30 selbst gesendeten Nachrichten erreicht ist"))
        host.day = host.day.plusDays(1)
        lane.handle("Schreib Thomas auf WhatsApp, neuer Tag")
        assertEquals(OwnerSendGrants.DAILY_CAP + 1, host.device.pressed.size)
    }

    @Test fun aMissingAppOrAFailedVerificationIsReportedHonestly() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        host.device.installedApps = setOf(SendChannel.GMAIL)
        lane.handle("Schreib Thomas auf WhatsApp, ich komme später")
        assertEquals("WhatsApp ist auf diesem Gerät nicht installiert. Es wurde nichts vorbereitet und nichts gesendet.", host.lastReply)
        val (wrong, wrongLane) = newLane(SendChannel.WHATSAPP)
        wrong.device.whatsAppWorks { "Unbekannter Chat" }
        wrongLane.handle("Schreib Thomas auf WhatsApp, ich komme später")
        assertEquals("Der Entwurf an Thomas Muster steht in WhatsApp bereit, gesendet habe ich nichts, weil der geöffnete Chat " +
            "nicht den erwarteten Empfänger zeigt.", wrong.lastReply)
        assertEquals(listOf("SENDEN · WhatsApp · nicht gesendet · TITLE_MISMATCH"), wrong.activities)
    }

    @Test fun otherTurnsAreLeftToTheOtherLanes() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        listOf("Nachricht von Anna vorlesen", "Was hat Thomas auf WhatsApp geschrieben?", "Öffne WhatsApp", "ja",
            "Fass mir die Nachrichten von heute zusammen", "Wie spät ist es?").forEach { assertFalse(it, lane.handle(it)) }
        assertTrue(host.accepted.isEmpty() && host.replies.isEmpty() && host.contacts.reads == 0)
    }

    // ── Gmail ───────────────────────────────────────────────────────────────────────────────────────────────

    private val file = OwnerSavedFile("Nachrichtenüberblick-2026-11-18.pdf", "Download", "content://fiktiv/1", "application/pdf")
    private fun order(address: String?, name: String?, attachment: OwnerSavedFile? = null) = OwnerMailHandover(
        "Schick die Zusammenfassung an ${address ?: name}", address, name, "Nachrichtenüberblick 18.11.",
        "Erster Absatz der Zusammenfassung.\nZweiter Absatz.", attachment)

    @Test fun aSavedFileTheOwnerOrderedGoesOutAsAttachmentWithoutModelTextAndWithoutAQuestion() {
        val (host, lane) = newLane(SendChannel.GMAIL)
        host.device.gmailWorks()
        assertTrue(lane.mail(order("anna@example.org", null, file)))
        val plan = host.device.opened.single() as SendPlan.Mail
        assertEquals("anna@example.org", plan.address)
        assertEquals("", plan.body)
        assertEquals("Nachrichtenüberblick-2026-11-18", plan.subject)
        assertEquals(file, plan.attachment)
        assertEquals("Gesendet an anna@example.org (Gmail): Betreff „Nachrichtenüberblick-2026-11-18“, " +
            "Anhang „Nachrichtenüberblick-2026-11-18.pdf“, ohne Begleittext", host.lastReply)
        assertEquals(1, host.device.pressed.size)
    }

    @Test fun aGeneratedSummaryAsMailTextIsShownOnceAndSentOnlyAfterAClearYes() {
        val (host, lane) = newLane(SendChannel.GMAIL)
        host.device.gmailWorks()
        assertTrue(lane.mail(order("anna@example.org", null)))
        assertTrue(host.device.opened.isEmpty())
        assertTrue(host.lastReply.startsWith("Ich würde an anna@example.org diese E-Mail über Gmail senden:\n\nBetreff: Nachrichtenüberblick 18.11.\n\nErster Absatz"))
        assertTrue(host.lastReply.contains("Soll ich das so senden?"))
        assertFalse(lane.handle("Was steht da genau drin?"))
        assertTrue(host.device.opened.isEmpty())
        assertTrue(lane.mail(order("anna@example.org", null)))
        assertTrue(lane.handle("Ja, senden"))
        val plan = host.device.opened.single() as SendPlan.Mail
        assertEquals("Erster Absatz der Zusammenfassung.\nZweiter Absatz.", plan.body)
        assertTrue(host.lastReply.startsWith("Gesendet an anna@example.org (Gmail): Betreff „Nachrichtenüberblick 18.11.“, ohne Anhang"))
        assertEquals(1, host.device.pressed.size)
    }

    @Test fun aNamedMailRecipientIsOneContactOrAQuestion() {
        val (host, lane) = newLane(SendChannel.GMAIL)
        host.device.gmailWorks()
        assertTrue(lane.mail(order(null, "Thomas", file)))
        assertEquals("thomas.muster@example.org", (host.device.opened.single() as SendPlan.Mail).address)
        assertTrue(host.lastReply.startsWith("Gesendet an Thomas Muster (thomas.muster@example.org) (Gmail)"))
        val (two, twoLane) = newLane(SendChannel.GMAIL)
        two.device.gmailWorks()
        twoLane.mail(order(null, "Anna", file))
        assertEquals("Zu „Anna“ gibt es mehrere passende Kontakte: Anna Beispiel (anna.beispiel@example.org), " +
            "Anna Muster (anna.muster@example.org). Welchen meinst du?", two.lastReply)
        assertTrue(two.device.opened.isEmpty())
        assertTrue(twoLane.handle("die erste"))
        assertEquals("anna.beispiel@example.org", (two.device.opened.single() as SendPlan.Mail).address)
        val (none, noneLane) = newLane(SendChannel.GMAIL)
        noneLane.mail(order(null, "meinen Chef", file))
        assertTrue(none.lastReply.startsWith("Ich habe keinen Kontakt „meinen Chef“ mit E-Mail-Adresse gefunden."))
        assertTrue(none.device.opened.isEmpty())
    }

    @Test fun withoutGmailTheExistingHandOverStays() {
        val (host, lane) = newLane(SendChannel.GMAIL)
        host.device.installedApps = setOf(SendChannel.WHATSAPP)
        assertFalse(lane.mail(order("anna@example.org", null, file)))
        assertTrue(host.replies.isEmpty())
    }

    // ── „Sende-Diagnose“ ────────────────────────────────────────────────────────────────────────────────────

    @Test fun theDiagnosisIsRecordedOnlyOnRequestStaysLocalAndSendsNothing() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        host.device.idle = mapOf(SendChannel.WHATSAPP to SendScreens.whatsAppChat("Anna Beispiel", "Geheimer Entwurf"))
        assertTrue(lane.handle("Sende-Diagnose"))
        assertEquals(OwnerSendDirector.DIAGNOSIS_START, host.replies.first())
        val report = host.diagnoses.single()
        assertTrue(report.contains("com.whatsapp:id/send"))
        assertFalse(report.contains("Anna") || report.contains("Geheimer"))
        assertFalse("the report is not put into the conversation", host.replies.any { it.contains("com.whatsapp:id") })
        assertTrue(host.device.pressed.isEmpty() && host.device.opened.isEmpty())
        val (nothing, nothingLane) = newLane(SendChannel.WHATSAPP)
        assertTrue(nothingLane.handle("starte die Sendediagnose"))
        assertTrue(nothing.diagnoses.isEmpty())
        assertTrue(nothing.lastReply.contains("weder WhatsApp noch Gmail im Vordergrund gesehen"))
        val (off, offLane) = newLane(SendChannel.WHATSAPP)
        off.device.accessibility = false
        offLane.handle("Sende-Diagnose")
        assertTrue(off.lastReply.contains("Continuum-Bedienungshilfe"))
    }
}
