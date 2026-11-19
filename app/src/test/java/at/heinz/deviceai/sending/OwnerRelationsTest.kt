package at.heinz.deviceai.sending

import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.sending.FakeContacts.Companion.phone
import at.heinz.deviceai.sending.OwnerSendRecipientResolver.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Relations instead of names („meinem Vater“) and the two leaks of the send lane found in the architecture review of
 * 19.11.2026: a kinship word matched as a name token, recipient words inside the sent text, and a free answer after
 * „not found“ sent at once. Fictional contacts only; the phone is the fake of the host tests.
 */
class OwnerRelationsTest {
    private val book = listOf(
        phone(1, "Anna Beispiel", "+43 000 555 0101"),
        phone(3, "Thomas Muster", "+43 000 555 0103"),
        phone(4, "Mama", "+43 000 555 0104"),
        // The father of a child in the owner's kindergarten group – not the owner's father.
        phone(10, "Vater Lukas (Kita)", "+43 000 555 0110"),
        phone(11, "Chef Pizzeria Roma", "+43 000 555 0111"),
        phone(12, "Bernd Muster", "+43 000 555 0112"),
        phone(13, "Bernd Beispiel", "+43 000 555 0113"))
    private val mails = listOf(OwnerContactMail(3, "Thomas Muster", "thomas.muster@example.org"),
        OwnerContactMail(11, "Chef Pizzeria Roma", "roma@example.org"))

    private fun newLane(vararg granted: SendChannel): Pair<FakeSendHost, OwnerSendDirector> {
        val host = FakeSendHost(contacts = FakeContacts(book, mails))
        if (SendChannel.GMAIL in granted) host.device.gmailWorks() else host.device.whatsAppWorks()
        host.grant(*granted)
        return host to OwnerSendDirector(host)
    }

    // ── Recognition ─────────────────────────────────────────────────────────────────────────────────────────

    @Test fun relationWordsWithAndWithoutPossessiveAndTheirSynonyms() {
        assertEquals(OwnerRelation.VATER, OwnerRelation.parse("meinem Vater"))
        assertEquals(OwnerRelation.VATER, OwnerRelation.parse("mein Papa"))
        assertEquals(OwnerRelation.VATER, OwnerRelation.parse("Papa"))
        assertEquals(OwnerRelation.MUTTER, OwnerRelation.parse("meiner Mutter"))
        assertEquals(OwnerRelation.MUTTER, OwnerRelation.parse("der Mama"))
        assertEquals(OwnerRelation.ANWAELTIN, OwnerRelation.parse("meiner Anwältin"))
        assertEquals(OwnerRelation.CHEFIN, OwnerRelation.parse("meine Chefin"))
        assertEquals(OwnerRelation.AERZTIN, OwnerRelation.parse("meiner Ärztin"))
        assertEquals(OwnerRelation.FRAU, OwnerRelation.parse("meiner Frau"))
        // „Frau“ and „Mann“ are only a relation with a possessive; a name is never a relation.
        listOf("Frau", "die Frau", "Frau Huber", "Vater Lukas", "Thomas", "meinem Vater Lukas", "Mann").forEach {
            assertNull(it, OwnerRelation.parse(it))
        }
        assertEquals(Triple(OwnerRelation.CHEF, "meinem Chef", ", ich komme später"), OwnerRelation.split("meinem Chef, ich komme später"))
        assertNull("without a possessive the start of a tail is a name", OwnerRelation.split("Chef Pizzeria ich komme"))
        assertEquals("mein Vater", OwnerRelation.VATER.mine)
        assertEquals("meine Mutter", OwnerRelation.MUTTER.mine)
        assertEquals(OwnerRelation.VATER, OwnerRelation.forgetCommand("Vergiss, wer mein Vater ist"))
        assertEquals(OwnerRelation.MUTTER, OwnerRelation.forgetCommand("Bitte vergiss, dass Anna meine Mama ist."))
        assertNull(OwnerRelation.forgetCommand("Vergiss das"))
    }

    @Test fun aContactWhoseNameMerelyContainsTheWordIsNeverPickedForTheRelation() {
        assertEquals(Resolution.NotFound("meinem Vater"), OwnerSendRecipientResolver.whatsapp("meinem Vater", book))
        assertEquals(Resolution.NotFound("meinem Chef"), OwnerSendRecipientResolver.whatsapp("meinem Chef", book))
        assertTrue(OwnerSendRecipientResolver.dial("meinen Vater", book) is Resolution.NotFound)
        assertTrue(OwnerSendRecipientResolver.mail("meinen Chef", mails) is Resolution.NotFound)
        assertNull(OwnerSendRecipientResolver.split("meinem Vater guten Morgen", book.map { it.displayName }))
        // The owner's own name for a contact – its whole name is the word – stays usable, also with a possessive.
        assertEquals("430005550104", (OwnerSendRecipientResolver.whatsapp("meiner Mama", book) as Resolution.Resolved).candidate.value)
        // Said as a name, the longer contact name is found as before.
        assertEquals("430005550110", (OwnerSendRecipientResolver.whatsapp("Vater Lukas", book) as Resolution.Resolved).candidate.value)
    }

    // ── Learning a relation ─────────────────────────────────────────────────────────────────────────────────

    @Test fun anUnknownRelationIsAskedRememberedOnlyOnYesAndTheFirstSendIsConfirmed() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        assertTrue(lane.handle("Schreib meinem Vater auf WhatsApp, ich komme später"))
        assertEquals("Ich habe keinen Kontakt „meinem Vater“ gefunden. Wer ist dein Vater in den Kontakten? Nenn mir den Namen, " +
            "wie er dort steht. Es wurde nichts vorbereitet und nichts gesendet.", host.lastReply)
        assertTrue("„Vater Lukas (Kita)“ is never taken", host.device.opened.isEmpty())
        assertTrue(lane.handle("Thomas Muster"))
        assertEquals("Soll ich mir merken, dass Thomas Muster dein Vater ist?", host.lastReply)
        assertNull("nothing is stored before the yes", host.relations.get(OwnerRelation.VATER))
        assertTrue(lane.handle("Ja"))
        val entry = host.relations.get(OwnerRelation.VATER)!!
        assertEquals(3L, entry.contactId)
        assertEquals("Thomas Muster", entry.displayName)
        assertEquals(OwnerRelations.PROVENANCE_OWNER_NAMED, entry.provenance)
        assertEquals("2026-11-19T08:00:00Z", entry.confirmedAt)
        assertFalse(entry.firstSendDone)
        assertTrue(host.replies.contains("Gut, ich merke mir: Thomas Muster ist dein Vater. Mit „Vergiss, wer mein Vater ist“ " +
            "lösche ich das wieder."))
        // The first send through the new relation asks, although the standing grant is on.
        assertEquals("Ich würde Thomas Muster auf WhatsApp schreiben:\n\n„Ich komme später“\n\nSoll ich das so senden?", host.lastReply)
        assertTrue(host.device.pressed.isEmpty())
        assertTrue(lane.handle("ja"))
        assertEquals("Gesendet an Thomas Muster (WhatsApp): „Ich komme später“", host.lastReply)
        assertTrue(host.relations.get(OwnerRelation.VATER)!!.firstSendDone)
        // From now on „mein Vater“ counts like a name the owner said himself.
        assertTrue(lane.handle("Schreib meinem Papa auf WhatsApp, bin gleich da"))
        assertEquals("Gesendet an Thomas Muster (WhatsApp): „Bin gleich da“", host.lastReply)
        assertEquals(2, host.device.pressed.size)
    }

    @Test fun withoutTheYesNothingIsRememberedAndTheSendIsStillConfirmedOnce() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("Schreib meinem Vater auf WhatsApp, ich komme später")
        lane.handle("Thomas Muster")
        assertTrue(lane.handle("nein"))
        assertNull(host.relations.get(OwnerRelation.VATER))
        assertTrue(host.replies.contains("In Ordnung, das merke ich mir nicht."))
        assertEquals("Ich würde Thomas Muster auf WhatsApp schreiben:\n\n„Ich komme später“\n\nSoll ich das so senden?", host.lastReply)
        assertTrue(host.device.pressed.isEmpty())
        assertTrue(lane.handle("ja"))
        assertEquals(1, host.device.pressed.size)
        // Next time the father is asked for again.
        lane.handle("Schreib meinem Vater auf WhatsApp, ich komme später")
        assertTrue(host.lastReply.contains("Wer ist dein Vater in den Kontakten?"))
    }

    @Test fun aStoredRelationThatWasNeverUsedToSendAsksFirstAndAForgottenOneIsAskedAgain() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        host.relations.remember(OwnerRelation.MUTTER, 1, "Anna Beispiel")
        lane.handle("Schreib meiner Mutter auf WhatsApp, ich komme später")
        assertEquals("Ich würde Anna Beispiel auf WhatsApp schreiben:\n\n„Ich komme später“\n\nSoll ich das so senden?", host.lastReply)
        assertTrue(lane.handle("nein"))
        assertTrue(host.device.pressed.isEmpty())
        assertFalse(host.relations.get(OwnerRelation.MUTTER)!!.firstSendDone)
        assertTrue(lane.handle("Vergiss, wer meine Mutter ist"))
        assertEquals("In Ordnung, ich habe vergessen, wer deine Mutter ist. Beim nächsten Mal frage ich wieder nach.", host.lastReply)
        assertNull(host.relations.get(OwnerRelation.MUTTER))
        assertTrue(lane.handle("Vergiss, wer meine Mutter ist"))
        assertEquals("Ich hatte mir nicht gemerkt, wer deine Mutter ist.", host.lastReply)
    }

    @Test fun aRelationAnswerWithSeveralCandidatesIsAChoiceAndThenStillAskedToRemember() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("Schreib meinem Bruder auf WhatsApp, ich komme später")
        assertTrue(lane.handle("Bernd"))
        assertEquals("Zu „Bernd“ gibt es mehrere passende Kontakte: Bernd Muster, Bernd Beispiel. Welchen meinst du?", host.lastReply)
        assertTrue(lane.handle("den zweiten"))
        assertEquals("Soll ich mir merken, dass Bernd Beispiel dein Bruder ist?", host.lastReply)
        assertTrue(lane.handle("ja"))
        assertEquals(13L, host.relations.get(OwnerRelation.BRUDER)!!.contactId)
        assertTrue(host.device.pressed.isEmpty())
    }

    @Test fun theOwnerCorrectsTheRecipientWithNotThisButThat() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        host.relations.remember(OwnerRelation.VATER, 3, "Thomas Muster")
        lane.handle("Schreib Papa auf WhatsApp, ich komme später")
        assertTrue(host.lastReply.startsWith("Ich würde Thomas Muster auf WhatsApp schreiben"))
        assertTrue(lane.handle("Nein, nicht Papa, Mama"))
        // The same message to the contact the owner named instead; the one confirmation that was due still comes.
        assertEquals("Ich würde Mama auf WhatsApp schreiben:\n\n„Ich komme später“\n\nSoll ich das so senden?", host.lastReply)
        assertTrue(host.device.pressed.isEmpty())
        assertTrue(lane.handle("ja"))
        assertEquals("Gesendet an Mama (WhatsApp): „Ich komme später“", host.lastReply)
        assertEquals("430005550104", (host.device.opened.single() as SendPlan.WhatsApp).waDigits)
        val (other, otherLane) = newLane(SendChannel.WHATSAPP)
        otherLane.handle("Schreib meinem Vater auf WhatsApp, ich komme später")
        assertTrue(otherLane.handle("Mama, nicht Papa"))
        assertEquals("Gesendet an Mama (WhatsApp): „Ich komme später“", other.lastReply)
        assertEquals("message text is no correction", null, OwnerSendIntentParser.correctedRecipient("Später, nicht jetzt"))
    }

    @Test fun aMailToARelationIsAskedLearntAndConfirmedOnceEvenWithAFile() {
        val (host, lane) = newLane(SendChannel.GMAIL)
        val file = OwnerSavedFile("Bericht.pdf", "Download", "content://fiktiv/2", "application/pdf")
        assertTrue(lane.mail(OwnerMailHandover("Schick das per Mail an meinen Chef", null, "meinen Chef", "Bericht", "", file)))
        assertTrue(host.lastReply.startsWith("Ich habe keinen Kontakt „meinen Chef“ mit E-Mail-Adresse gefunden. Wer ist dein Chef in den Kontakten?"))
        assertTrue("„Chef Pizzeria Roma“ is never taken", host.device.opened.isEmpty())
        assertTrue(lane.handle("Thomas Muster"))
        assertEquals("Soll ich mir merken, dass Thomas Muster dein Chef ist?", host.lastReply)
        assertTrue(lane.handle("ja"))
        assertEquals("Ich würde an Thomas Muster über Gmail senden: Betreff „Bericht“, Anhang „Bericht.pdf“. Soll ich das so senden?",
            host.lastReply)
        assertTrue(host.device.pressed.isEmpty())
        assertTrue(lane.handle("ja"))
        assertEquals("thomas.muster@example.org", (host.device.opened.single() as SendPlan.Mail).address)
        assertEquals(1, host.device.pressed.size)
    }

    // ── B4: recipient words never slip into the text ────────────────────────────────────────────────────────

    @Test fun aTailWithoutSeparatorIsSplitByRelationOrContactAndOtherwiseConfirmedOnce() {
        // The failing input of the review: before, the text sent was „Meinem Chef, ich komme später“.
        OwnerSendIntentParser.parse("Schick auf WhatsApp meinem Chef, ich komme später").let {
            val order = (it as OwnerSendIntentParser.Result.Order).order
            assertNull(order.recipient); assertNull(order.text)
            assertEquals("meinem Chef, ich komme später", order.unsplit)
            assertTrue(order.looseText)
        }
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("Schick auf WhatsApp meinem Chef, ich komme später")
        assertTrue(host.lastReply.contains("Wer ist dein Chef in den Kontakten?"))
        lane.handle("Thomas Muster")
        lane.handle("nein")
        assertEquals("Ich würde Thomas Muster auf WhatsApp schreiben:\n\n„Ich komme später“\n\nSoll ich das so senden?", host.lastReply)
        lane.handle("ja")
        assertEquals(SendPlan.WhatsApp("Thomas Muster", "430005550103", "Ich komme später", send = true), host.device.opened.single())

        // No recipient in front at all: the whole tail is the text, the recipient is asked, the wording shown once.
        val (loose, looseLane) = newLane(SendChannel.WHATSAPP)
        looseLane.handle("Schreib auf WhatsApp ich komme später")
        assertEquals(OwnerSendDirector.ASK_RECIPIENT, loose.lastReply)
        looseLane.handle("Thomas")
        assertEquals("Ich würde Thomas Muster auf WhatsApp schreiben:\n\n„Ich komme später“\n\nSoll ich das so senden?", loose.lastReply)
        assertTrue(loose.device.pressed.isEmpty())

        // A contact name in front still splits as before.
        val (named, namedLane) = newLane(SendChannel.WHATSAPP)
        namedLane.handle("Schreib auf WhatsApp Thomas Muster bin gleich da")
        assertEquals("Gesendet an Thomas Muster (WhatsApp): „Bin gleich da“", named.lastReply)
        // Separated text keeps its old meaning.
        assertEquals("Hallo zusammen", (OwnerSendIntentParser.parse("Schreib auf WhatsApp: Hallo zusammen") as
            OwnerSendIntentParser.Result.Order).order.text)
        assertEquals(OwnerSendIntentParser.REFUSED_GROUP, (OwnerSendIntentParser.parse("Schreib auf WhatsApp allen ich komme später")
            as OwnerSendIntentParser.Result.Refused).message)
    }

    // ── Point 3: a free answer after „not found“ is confirmed before anything is sent ───────────────────────

    @Test fun theNameGivenAfterAnUnknownContactIsConfirmedBeforeSending() {
        val (host, lane) = newLane(SendChannel.WHATSAPP)
        lane.handle("Schreib Berta auf WhatsApp, ich komme später")
        assertTrue(host.lastReply.startsWith("Ich habe keinen Kontakt „Berta“ gefunden."))
        assertTrue(lane.handle("Anna Beispiel"))
        assertEquals("Ich würde Anna Beispiel auf WhatsApp schreiben:\n\n„Ich komme später“\n\nSoll ich das so senden?", host.lastReply)
        assertTrue(host.device.opened.isEmpty() && host.device.pressed.isEmpty())
        assertTrue(lane.handle("ja"))
        assertEquals("Gesendet an Anna Beispiel (WhatsApp): „Ich komme später“", host.lastReply)
        assertEquals(1, host.device.pressed.size)
    }
}
