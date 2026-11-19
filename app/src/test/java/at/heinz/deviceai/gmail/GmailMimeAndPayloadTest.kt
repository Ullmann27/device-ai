package at.heinz.deviceai.gmail

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

/** RFC 2822 / RFC 2047 messages for drafts and sends, and the payload trees Gmail returns. */
class GmailMimeAndPayloadTest {
    private fun decoded(mail: GmailOutgoing): String = String(GmailMime.decodeBase64Url(GmailMime.raw(mail))!!, Charsets.UTF_8)
    private fun header(message: String, name: String): String? = message.substringBefore("\r\n\r\n")
        .replace(Regex("\\r\\n[ \\t]+"), " ").lines().firstOrNull { it.startsWith("$name: ") }?.removePrefix("$name: ")
    private fun body(message: String): String = String(Base64.getMimeDecoder().decode(message.substringAfter("\r\n\r\n").trim()), Charsets.UTF_8)

    @Test fun umlautsInSubjectAndBodyRoundTrip() {
        val message = decoded(GmailOutgoing("anna@example.org", "Grüße aus Österreich – Größe ß", "Schöne Grüße,\nHeinz äöü €"))
        assertEquals("anna@example.org", header(message, "To"))
        val subject = header(message, "Subject")!!
        assertTrue(subject.startsWith("=?UTF-8?B?"))
        assertEquals("Grüße aus Österreich – Größe ß", GmailMime.decodeHeader(subject))
        assertEquals("text/plain; charset=UTF-8", header(message, "Content-Type"))
        assertEquals("Schöne Grüße,\r\nHeinz äöü €", body(message))
        // The raw value is base64url: no '+' or '/'.
        assertFalse(GmailMime.raw(GmailOutgoing("a@example.org", "Ä", "x".repeat(500))).any { it == '+' || it == '/' })
    }

    @Test fun aLongSubjectIsFoldedIntoEncodedWordsOfAtMost75Characters() {
        val long = "Zusammenfassung der Besprechung über die Wärmepumpe, den Heizkörpertausch und die Förderung – bitte prüfen"
        val raw = String(GmailMime.decodeBase64Url(GmailMime.raw(GmailOutgoing("a@example.org", long, "x")))!!, Charsets.UTF_8)
        val lines = raw.substringBefore("\r\n\r\n").split("\r\n")
        val start = lines.indexOfFirst { it.startsWith("Subject: ") }
        val words = listOf(lines[start].removePrefix("Subject: ")) + lines.drop(start + 1).takeWhile { it.startsWith(" ") }.map { it.trim() }
        assertTrue(words.size > 1)
        words.forEach { assertTrue(it, it.length <= 75 && it.startsWith("=?UTF-8?B?") && it.endsWith("?=")) }
        assertEquals(long, GmailMime.decodeHeader(header(raw, "Subject")!!))
    }

    @Test fun plainAsciiStaysReadableAndLineBreaksCannotInjectHeaders() {
        val message = decoded(GmailOutgoing("a@example.org", "Hallo\r\nBcc: boese@example.org", "Text"))
        assertEquals("Hallo Bcc: boese@example.org", header(message, "Subject"))
        assertNull(header(message, "Bcc"))
        try { GmailMime.raw(GmailOutgoing("a@example.org\r\nBcc: x@example.org", "s", "t")); fail() } catch (_: IllegalArgumentException) {}
        try { GmailMime.raw(GmailOutgoing("kein-empfaenger", "s", "t")); fail() } catch (_: IllegalArgumentException) {}
        try { GmailMime.raw(GmailOutgoing("a@example.org", "s", " ")); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun aReplyKeepsTheThreadHeaders() {
        val mail = GmailOutgoing("anna@example.org", GmailMime.replySubject("Termin Freitag"), "Passt.", "t1",
            "<m1@mail.example.org>", "<m0@mail.example.org> <m1@mail.example.org>")
        val message = decoded(mail)
        assertEquals("Re: Termin Freitag", header(message, "Subject"))
        assertEquals("<m1@mail.example.org>", header(message, "In-Reply-To"))
        assertEquals("<m0@mail.example.org> <m1@mail.example.org>", header(message, "References"))
        assertEquals("AW: Termin", GmailMime.replySubject("AW: Termin"))
        assertEquals("Re: (kein Betreff)", GmailMime.replySubject(""))
        assertNull(header(decoded(GmailOutgoing("a@example.org", "s", "t", inReplyTo = "kein-id")), "In-Reply-To"))
    }

    @Test fun encodedWordsInBothEncodingsAreDecoded() {
        assertEquals("Grüße", GmailMime.decodeHeader("=?UTF-8?B?R3LDvMOfZQ==?="))
        assertEquals("Grüße aus Wien", GmailMime.decodeHeader("=?ISO-8859-1?Q?Gr=FC=DFe_aus?= Wien"))
        assertEquals("ab", GmailMime.decodeHeader("=?UTF-8?B?YQ==?= =?UTF-8?B?Yg==?="))
    }

    // ── payload trees ─────────────────────────────────────────────────────────────────────────────────────

    private fun part(mime: String, data: String? = null, filename: String = "", attachmentId: String? = null, size: Int = 0,
                     charset: String = "UTF-8", parts: List<JSONObject>? = null): JSONObject = JSONObject().put("mimeType", mime)
        .put("filename", filename)
        .put("headers", Mails.headers("Content-Type" to "$mime; charset=\"$charset\""))
        .put("body", JSONObject().put("size", size).apply {
            data?.let { put("data", it) }
            attachmentId?.let { put("attachmentId", it) }
        }).apply { parts?.let { put("parts", JSONArray(it)) } }

    private fun resource(payload: JSONObject) = JSONObject().put("id", "m1").put("threadId", "t1").put("internalDate", "1763542800000")
        .put("labelIds", JSONArray(listOf("INBOX", "UNREAD"))).put("snippet", "Hallo &amp; willkommen")
        .put("payload", payload.put("headers", Mails.headers("From" to "\"Beispiel, Anna\" <Anna@Example.org>", "To" to "heinz@example.org, b@example.org",
            "Subject" to "=?UTF-8?B?UmVjaG51bmcgTsOkY2hzdGU=?=", "Message-ID" to "<abc@mail.example.org>")))

    @Test fun multipartAlternativePrefersPlainTextAndListsAttachments() {
        val tree = part("multipart/mixed", parts = listOf(
            part("multipart/alternative", parts = listOf(
                part("text/plain", Mails.b64("Hallo Heinz,\r\nbitte bis 30.11. zahlen.")),
                part("text/html", Mails.b64("<p>Hallo <b>Heinz</b></p>")))),
            part("application/pdf", filename = "Rechnung.pdf", attachmentId = "att1", size = 52_000)))
        val message = GmailPayloadParser.parse(resource(tree), withBody = true)!!
        assertEquals("Hallo Heinz,\nbitte bis 30.11. zahlen.", message.body)
        assertFalse(message.fromHtml)
        assertEquals("Beispiel, Anna", message.ref.from)
        assertEquals("anna@example.org", message.fromAddress)
        assertEquals(listOf("heinz@example.org", "b@example.org"), message.to)
        assertEquals("Rechnung Nächste", message.ref.subject)
        assertEquals(1763542800000L, message.ref.dateMillis)
        assertTrue(message.unread)
        assertEquals("Hallo & willkommen", message.snippet)
        assertEquals(listOf(GmailAttachmentRef("m1", "att1", "Rechnung.pdf", "application/pdf", 52_000)), message.attachments)
        assertEquals("<abc@mail.example.org>", message.messageIdHeader)
    }

    @Test fun htmlOnlyMailsAreStrippedToText() {
        val html = "<html><head><style>p{color:red}</style><script>alert(1)</script></head><body><p>Termin am <b>Freitag</b>&nbsp;um 10&#58;00</p>" +
            "<ul><li>Punkt eins</li></ul><!-- versteckt --></body></html>"
        val message = GmailPayloadParser.parse(resource(part("text/html", Mails.b64(html))), withBody = true)!!
        assertTrue(message.fromHtml)
        assertEquals("Termin am Freitag um 10:00\n\n– Punkt eins", message.body)
        assertFalse(message.body!!.contains("alert") || message.body!!.contains("color") || message.body!!.contains("versteckt"))
    }

    @Test fun theCharsetOfThePartIsHonouredAndLongBodiesAreBounded() {
        val latin = Base64.getUrlEncoder().encodeToString("Grüße aus Linz".toByteArray(Charsets.ISO_8859_1))
        assertEquals("Grüße aus Linz", GmailPayloadParser.parse(resource(part("multipart/mixed",
            parts = listOf(part("text/plain", latin, charset = "ISO-8859-1")))), withBody = true)!!.body)
        val long = (1..3_000).joinToString(" ") { "Wort$it" }
        val message = GmailPayloadParser.parse(resource(part("text/plain", Mails.b64(long))), withBody = true, maxBodyChars = 1_000)!!
        assertTrue(message.truncated)
        assertTrue(message.body!!.length <= 1_000)
        assertTrue(long.startsWith(message.body!!))
    }

    @Test fun aMetadataReadHasNoBodyAndDeepTreesAreBounded() {
        val meta = GmailPayloadParser.parse(resource(JSONObject().put("mimeType", "text/plain")), withBody = false)!!
        assertNull(meta.body)
        assertEquals("Hallo & willkommen", meta.text)
        var deep = part("text/plain", Mails.b64("tief"))
        repeat(20) { deep = part("multipart/mixed", parts = listOf(deep)) }
        assertEquals("", GmailPayloadParser.parse(resource(deep), withBody = true)!!.body)
        assertNull(GmailPayloadParser.parse(JSONObject().put("id", "../x"), withBody = true))
    }

    @Test fun bulkMailIsRecognisedForTheRanking() {
        val json = Mails.message("n1", "Shop <news@shop.example>", "Sale", 1L, extraHeaders = listOf("List-Unsubscribe" to "<mailto:x@shop.example>"))
        assertTrue(GmailPayloadParser.parse(json, withBody = false)!!.bulk)
    }
}
