package at.heinz.deviceai.sending

import at.heinz.deviceai.sending.FakeContacts.Companion.phone
import at.heinz.deviceai.sending.OwnerSendRecipientResolver.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixed rule (b): one address-book entry or a question – never a guess. Fictional contacts only. */
class OwnerSendRecipientResolverTest {
    private val book = listOf(
        phone(1, "Anna Beispiel", "+43 000 555 0101"),
        phone(2, "Anna Muster", "+43 000 555 0102"),
        phone(3, "Thomas Muster", "+43 000 555 0103"),
        phone(3, "Thomas Muster", "+43 000 555 0199", mobile = false),
        phone(4, "Mama", "0000 555 0104", e164 = "+430005550104"),
        phone(5, "Festnetz Oma", "+43 000 555 0105", mobile = false),
        phone(6, "Karl Ohnevorwahl", "0000 555 0106", e164 = null),
        phone(7, "Doppel Nummer", "+43 000 555 0107"),
        phone(7, "Doppel Nummer", "+43 000 555 0108"),
        // The same person synced twice (two raw contacts, one number).
        phone(8, "Berta Zweifach", "+43 000 555 0109"),
        phone(9, "Berta Zweifach", "+430005550109"))

    private fun resolved(phrase: String) = (OwnerSendRecipientResolver.whatsapp(phrase, book) as Resolution.Resolved).candidate

    @Test fun exactlyOneContactWithAMobileNumberIsResolved() {
        resolved("Thomas").let { assertEquals("Thomas Muster", it.displayName); assertEquals("430005550103", it.value) }
        assertEquals("430005550101", resolved("Anna Beispiel").value)
        assertEquals("430005550104", resolved("meiner Mama").value)
        assertEquals("430005550101", resolved("beispiel anna").value)
        // No number is marked mobile: the only number is used; WhatsApp itself then shows whether it exists.
        assertEquals("430005550105", resolved("Oma").value)
        // Two address-book entries of the same person with the same number are one recipient.
        assertEquals("430005550109", resolved("Berta").value)
    }

    @Test fun twoAnnasAreAQuestionNamingBoth() {
        val asked = OwnerSendRecipientResolver.whatsapp("Anna", book) as Resolution.Ambiguous
        assertEquals(listOf("Anna Beispiel", "Anna Muster"), asked.candidates.map { it.label })
        assertEquals(2, asked.total)
    }

    @Test fun severalMobileNumbersOfOneContactAreAQuestionToo() {
        val asked = OwnerSendRecipientResolver.whatsapp("Doppel Nummer", book) as Resolution.Ambiguous
        assertEquals(listOf("Doppel Nummer (Nummer endet auf 07)", "Doppel Nummer (Nummer endet auf 08)"), asked.candidates.map { it.label })
    }

    @Test fun unknownNamesAndNumbersWithoutCountryCodeAreNeverGuessed() {
        assertEquals(Resolution.NotFound("Chef"), OwnerSendRecipientResolver.whatsapp("Chef", book))
        assertEquals(Resolution.NotFound("Ann"), OwnerSendRecipientResolver.whatsapp("Ann", book))
        assertEquals(Resolution.NotFound("meiner"), OwnerSendRecipientResolver.whatsapp("meiner", book))
        val unusable = OwnerSendRecipientResolver.whatsapp("Karl", book) as Resolution.Unusable
        assertTrue(unusable.reason.contains("ohne Ländervorwahl"))
        assertNull(OwnerSendRecipientResolver.waDigits("0000 555 0106"))
        assertNull(OwnerSendRecipientResolver.waDigits(null))
        assertEquals("430005550101", OwnerSendRecipientResolver.waDigits("+43 000 555 0101"))
    }

    @Test fun theAnswerToTheQuestionPicksByOrdinalNamePartOrEndDigits() {
        val annas = (OwnerSendRecipientResolver.whatsapp("Anna", book) as Resolution.Ambiguous).candidates
        assertEquals("Anna Muster", OwnerSendRecipientResolver.pick("die zweite", annas)?.displayName)
        assertEquals("Anna Beispiel", OwnerSendRecipientResolver.pick("Anna Beispiel", annas)?.displayName)
        assertEquals("Anna Muster", OwnerSendRecipientResolver.pick("Muster", annas)?.displayName)
        assertEquals("Anna Beispiel", OwnerSendRecipientResolver.pick("die erste bitte", annas)?.displayName)
        assertNull(OwnerSendRecipientResolver.pick("Anna", annas))
        assertNull(OwnerSendRecipientResolver.pick("Wie spät ist es?", annas))
        assertNull(OwnerSendRecipientResolver.pick("die dritte", annas))
        val numbers = (OwnerSendRecipientResolver.whatsapp("Doppel Nummer", book) as Resolution.Ambiguous).candidates
        assertEquals("430005550108", OwnerSendRecipientResolver.pick("die mit 08", numbers)?.value)
    }

    @Test fun unseparatedSpeechIsSplitAtTheLongestContactName() {
        val names = book.map { it.displayName }.distinct()
        assertEquals("Anna Beispiel" to "ich komme später", OwnerSendRecipientResolver.split("Anna Beispiel ich komme später", names))
        assertEquals("Thomas" to "bin gleich da", OwnerSendRecipientResolver.split("Thomas bin gleich da", names))
        assertEquals("Mama" to "", OwnerSendRecipientResolver.split("Mama", names))
        assertNull(OwnerSendRecipientResolver.split("Chef ich komme später", names))
    }

    @Test fun mailRecipientsFollowTheSameRule() {
        val mails = listOf(
            OwnerContactMail(1, "Anna Beispiel", "anna.beispiel@example.org"),
            OwnerContactMail(2, "Anna Muster", "anna.muster@example.org"),
            OwnerContactMail(3, "Thomas Muster", "thomas.muster@example.org"),
            OwnerContactMail(3, "Thomas Muster", "Thomas.Muster@example.org"),
            OwnerContactMail(4, "Kaputt Adresse", "keine adresse"))
        assertEquals("thomas.muster@example.org", (OwnerSendRecipientResolver.mail("Thomas", mails) as Resolution.Resolved).candidate.value)
        val asked = OwnerSendRecipientResolver.mail("Anna", mails) as Resolution.Ambiguous
        assertEquals(listOf("Anna Beispiel (anna.beispiel@example.org)", "Anna Muster (anna.muster@example.org)"), asked.candidates.map { it.label })
        assertEquals("anna.muster@example.org", OwnerSendRecipientResolver.pick("Anna Muster", asked.candidates)?.value)
        assertEquals(Resolution.NotFound("meinen Chef"), OwnerSendRecipientResolver.mail("meinen Chef", mails))
        assertEquals(Resolution.NotFound("Kaputt"), OwnerSendRecipientResolver.mail("Kaputt", mails))
    }
}
