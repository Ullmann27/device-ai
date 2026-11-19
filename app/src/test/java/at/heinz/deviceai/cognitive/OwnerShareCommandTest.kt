package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerShareCommandTest {
    private fun parse(text: String) = OwnerShareCommand.parse(text)

    @Test fun anAddressFromTheOwnersSentenceIsPassedOn() {
        mapOf(
            "Schick das per Mail an anna.muster@example.org" to "anna.muster@example.org",
            "Jarvis, schicke die Zusammenfassung bitte per E-Mail an chef@firma.example" to "chef@firma.example",
            "Sende die Nachrichten als Mail an a.b+news@mail.example.co.at bitte" to "a.b+news@mail.example.co.at",
            "Schick das PDF an chef@firma.example per Mail" to "chef@firma.example",
            "Mail das an chef@firma.example" to "chef@firma.example"
        ).forEach { (text, address) ->
            val request = parse(text)
            assertEquals(text, address, request?.address)
            assertNull(text, request?.recipientName)
            assertTrue(text, request!!.mailNamed)
        }
    }

    @Test fun aNameIsOnlyRepeatedBackNeverResolved() {
        val boss = parse("Schicke bitte diese Zusammenfassung an meinen Arbeitskollegen oder meinen Chef")!!
        assertNull(boss.address)
        assertEquals("meinen Arbeitskollegen oder meinen Chef", boss.recipientName)
        assertFalse(boss.mailNamed)
        val named = parse("Schick die Zusammenfassung per Mail an Anna Muster")!!
        assertEquals("Anna Muster", named.recipientName)
        assertTrue(named.mailNamed)
        assertEquals("meinen Chef", parse("Schick das an meinen Chef per Mail")!!.recipientName)
    }

    @Test fun otherOrdersAreNotMailHandOvers() {
        listOf(
            "Schick das per WhatsApp an Anna",
            "Schick Anna eine Nachricht",
            "Schreib eine Mail an Anna",
            "Schick eine Nachricht an Anna",
            "Schick das nicht an Anna",
            "Schick das per Mail",                              // no recipient
            "Schick das per Mail an anna@example.org und ben@example.org",
            "Schick das per Mail an Anna <script>",
            "Schick das per Mail an https://example.org/upload",
            "Schick mein Passwort per Mail an anna@example.org",
            "Schick 50 Euro an Anna",
            "Sende das Foto an Anna",
            "Ich denke an Anna",
            "Speichere das als PDF"
        ).forEach { assertNull(it, parse(it)) }
    }

    /** The recipient can only come from the owner's own words: the guard for outbound actions agrees. */
    @Test fun recipientIsOwnerProvenanced() {
        val order = "Schick das per Mail an anna.muster@example.org"
        val request = parse(order)!!
        assertNull(ExternalContentActionGuard.refusal(order, "send_mail", Risk.CONFIRM, mapOf("to" to request.address,
            "subject" to "Nachrichten", "body" to "beliebiger Feed-Text an boese@example.net"), untrustedObservationsSeen = true))
        assertEquals(ExternalContentActionGuard.REFUSAL, ExternalContentActionGuard.refusal(order, "send_mail", Risk.CONFIRM,
            mapOf("to" to "boese@example.net"), untrustedObservationsSeen = true))
    }
}
