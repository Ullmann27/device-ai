package at.heinz.deviceai

import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.ARGON
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_2
import at.heinz.deviceai.cognitive.NewsTestFeeds
import at.heinz.deviceai.sending.FakeSendHost
import at.heinz.deviceai.sending.OwnerMailHandover
import at.heinz.deviceai.sending.OwnerSendDirector
import at.heinz.deviceai.sending.SendChannel
import at.heinz.deviceai.sending.SendPlan
import at.heinz.deviceai.sending.SendScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two owner flows across the lanes, on the host, with the productive classes of every lane and one shared chat:
 *
 *  1. Document desk → work tools: the saved page summary is what „ja“ (open), „öffne die gespeicherte Datei“,
 *     „speichere das als PDF“ and „schick das per Mail an …“ refer to afterwards.
 *  2. News briefing → „speichere das als PDF“ → „schick das per Mail an <Adresse>“: without the Gmail grant the mail
 *     app gets the prepared mail with the PDF attached; with the grant the send lane verifies the Gmail draft on the
 *     (synthetic) screen – recipient, subject, attachment – and sends it once.
 *
 * Stand-ins: screen, model text, feeds, Download folder, mail app and the phone boundary of the send lane (synthetic
 * accessibility trees). Gmail, the viewer and the real Download folder are NOT part of this run – device: NOT_RUN.
 */
class OwnerDeskAndWorkToolsChainTest {
    private val wordName = "Zusammenfassung Argon4 Seite 2 18.11.2026.docx"

    /** The Activity's order for one owner turn: the desk first, then the work tools. */
    private class Chat(val desk: OwnerDocumentDesk, val tools: OwnerWorkTools) {
        fun say(text: String): String = when {
            desk.intercept(text) || desk.handle(text) -> "DESK"
            tools.handle(text) -> "TOOLS"
            else -> "OTHER"
        }
    }

    private fun chat(): Triple<Chat, OwnerDocumentDeskHarness, OwnerWorkToolsHarness> {
        val toolsHost = OwnerWorkToolsHarness()
        val tools = OwnerWorkTools(toolsHost, now = { OwnerDocumentDeskHarness.NOW }, zone = { OwnerDocumentDeskHarness.ZONE })
        val deskHost = OwnerDocumentDeskHarness(toolsHost).apply {
            put("Argon4.pdf", pages = ARGON)
            model = { OwnerWorkGeneration(SUMMARY_2, null, true) }
            onAdopt = { result, file -> tools.adopt(result, file, offerToOpen = true) }
            exportAgainTakes = tools::exportAgain
        }
        return Triple(Chat(OwnerDocumentDeskHarness.desk(deskHost), tools), deskHost, toolsHost)
    }

    @Test fun theSavedPageSummaryIsWhatTheFollowUpOrdersReferTo() {
        val (chat, desk, tools) = chat()
        assertEquals("DESK", chat.say("Öffne das PDF Argon4"))
        assertEquals("DESK", chat.say("Fass Seite 2 zusammen und speichere es als Word"))
        assertTrue(desk.lines("SAY:").last().endsWith("Soll ich sie öffnen?"))
        // „Ja“ answers the offer: the work tools open exactly that file.
        assertEquals("TOOLS", chat.say("Ja"))
        assertEquals(wordName, tools.opened.single().displayName)
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", tools.opened.single().mime)
        assertEquals("„$wordName“ ist geöffnet.", tools.lines("REPLY:").last())
        assertEquals("TOOLS", chat.say("Öffne die gespeicherte Datei"))
        assertEquals(2, tools.opened.size)
        // The mail carries the summary with its source line, and the saved Word file is attached.
        assertEquals("TOOLS", chat.say("Schick das per Mail an anna.muster@example.org"))
        val mail = tools.mails.single()
        assertEquals("anna.muster@example.org", mail.address)
        assertEquals("Zusammenfassung Argon4 Seite 2 18.11.2026", mail.subject)
        assertTrue(mail.body.contains(SUMMARY_2) && mail.body.contains("Quelle: „Argon4.pdf“, Seite 2 von 4"))
        assertEquals(wordName, mail.attachment!!.displayName)
        assertTrue(tools.lines("REPLY:").last().contains("„$wordName“ hängt an") && tools.lines("REPLY:").last().contains("Gesendet habe ich nichts"))
        // „Speichere das als PDF“ keeps meaning the page summary.
        assertEquals("TOOLS", chat.say("Speichere das als PDF"))
        val pdf = "Zusammenfassung Argon4 Seite 2 18.11.2026.pdf"
        assertEquals(listOf(wordName, pdf), tools.downloads.keys.toList())
        assertTrue(String(tools.downloads.getValue(pdf), Charsets.UTF_8).contains(SUMMARY_2))
        assertEquals("Erledigt. „$pdf“ liegt jetzt in deinem Download-Ordner (1 Seite). Darin steht die Seitenzusammenfassung " +
            "aus diesem Gespräch. Soll ich sie öffnen?", tools.lines("SAY:").last())
        // One model generation for all of it.
        assertEquals(1, desk.prompts.size)
    }

    @Test fun aFormatCorrectionReachesTheLaneThatSavedLast() {
        val (chat, desk, tools) = chat()
        // After a save of the work tools the correction is theirs …
        tools.foreignTurn("Erklär mir die Photosynthese", "Die Photosynthese wandelt Lichtenergie in chemische Energie um. Pflanzen " +
            "bilden dabei aus Wasser und Kohlendioxid Zucker und geben Sauerstoff ab.")
        assertEquals("TOOLS", chat.say("Speichere das als PDF"))
        assertEquals("DESK", chat.say("Ich meinte als Word"))
        assertEquals(listOf("Continuum Notiz 18.11.2026.pdf", "Continuum Notiz 18.11.2026.docx"), tools.downloads.keys.toList())
        assertTrue(desk.spoken.isEmpty())
        // … after the desk's page job it is the desk's, and the summary is not generated again.
        assertEquals("DESK", chat.say("Öffne das PDF Argon4"))
        assertEquals("DESK", chat.say("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals("DESK", chat.say("Ich meinte als PDF"))
        assertEquals("Zusammenfassung Argon4 Seite 2 18.11.2026.pdf", tools.downloads.keys.last())
        assertEquals(1, desk.prompts.size)
        // Without anything saved just before, the correction is one short question.
        val (fresh, freshDesk, freshTools) = chat()
        assertEquals("DESK", fresh.say("Ich meinte als PDF"))
        assertEquals("Was soll ich als PDF speichern? Ich habe gerade nichts gespeichert, das ich ändern könnte.", freshDesk.lines("REPLY:").single())
        assertTrue(freshTools.downloads.isEmpty())
    }

    // ── Second flow: news → PDF → mail ──────────────────────────────────────────────────────────────────────

    private val briefing = "Die Nationalbank von Musterstadt hat den Leitzins auf 2,5 Prozent gesenkt, das berichten die " +
        "tagesschau und der ORF. Außerdem müssen in Testland neue Häuser ab 2028 eine Solaranlage haben. Währenddessen " +
        "warnt ein Bericht vor häufigeren Dürren in Beispielia, wie die Deutsche Welle meldet."
    private val pdfName = "Nachrichten von heute 18.11.2026.pdf"

    /** The work tools with the send lane behind their mail order, as the Activity wires them. */
    private class Wired(private val inner: OwnerWorkToolsHarness, private val lane: OwnerSendDirector) : OwnerWorkToolsHost by inner {
        override fun sendMailYourself(order: OwnerMailHandover): Boolean = lane.mail(order)
    }

    private fun news(sendHost: FakeSendHost): Pair<OwnerWorkToolsHarness, OwnerWorkTools> {
        val host = OwnerWorkToolsHarness(model = { OwnerWorkGeneration(briefing, null, true) })
        val tools = OwnerWorkTools(Wired(host, OwnerSendDirector(sendHost)), now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
        assertTrue(tools.handle("Fass mir die Nachrichten von heute zusammen"))
        assertTrue(tools.handle("Speichere das als PDF"))
        assertEquals(listOf(pdfName), host.downloads.keys.toList())
        return host to tools
    }

    @Test fun withoutTheGmailGrantTheMailAppGetsThePreparedMailWithThePdfAttached() {
        val sendHost = FakeSendHost().apply { device.gmailWorks() }
        val (host, tools) = news(sendHost)
        assertTrue(tools.handle("Schick das per Mail an anna.muster@example.org"))
        val mail = host.mails.single()
        assertEquals("anna.muster@example.org", mail.address)
        assertEquals("Nachrichten von heute 18.11.2026", mail.subject)
        val attached = checkNotNull(mail.attachment)
        assertEquals(pdfName, attached.displayName)
        assertEquals("application/pdf", attached.mime)
        assertTrue(mail.body.contains("Die Nationalbank von Musterstadt") && mail.body.contains("Quellen"))
        assertTrue(host.lines("REPLY:").last().endsWith("Gesendet habe ich nichts – du musst im Mailprogramm nur noch auf Senden tippen."))
        assertTrue("the send lane did nothing", sendHost.device.opened.isEmpty() && sendHost.device.pressed.isEmpty())
    }

    @Test fun withTheGmailGrantTheDraftIsVerifiedWithItsAttachmentAndSentOnce() {
        val sendHost = FakeSendHost().apply { device.gmailWorks(); grant(SendChannel.GMAIL) }
        val (host, tools) = news(sendHost)
        assertTrue(tools.handle("Schick das per Mail an anna.muster@example.org"))
        assertTrue("the generic hand-over is not used as well", host.mails.isEmpty())
        val plan = sendHost.device.opened.single() as SendPlan.Mail
        assertEquals("anna.muster@example.org", plan.address)
        assertEquals("Nachrichten von heute 18.11.2026", plan.subject)
        val attached = checkNotNull(plan.attachment)
        assertEquals(pdfName, attached.displayName)
        assertTrue(checkNotNull(attached.uri).startsWith("content://"))
        assertTrue(plan.send)
        assertEquals(1, sendHost.device.pressed.size)
        assertTrue(sendHost.lastReply, sendHost.lastReply.startsWith("Gesendet an anna.muster@example.org (Gmail)"))
        assertEquals(1, sendHost.grants.sentToday())
    }

    @Test fun aGmailDraftWithoutTheAttachmentOnScreenIsNotSent() {
        val sendHost = FakeSendHost().apply { grant(SendChannel.GMAIL) }
        // Gmail shows the draft, but the attachment tile is missing.
        sendHost.device.beforeClick = { plan, _ -> (plan as SendPlan.Mail).let { SendScreens.gmailCompose(listOf(it.address), it.subject, it.body) } }
        val (host, tools) = news(sendHost)
        assertTrue(tools.handle("Schick das per Mail an anna.muster@example.org"))
        assertEquals(1, sendHost.device.opened.size)
        assertTrue(sendHost.device.pressed.isEmpty())
        assertFalse(sendHost.lastReply, sendHost.lastReply.startsWith("Gesendet"))
        assertEquals(0, sendHost.grants.sentToday())
        assertTrue(host.mails.isEmpty())
        assertNull(sendHost.device.opened.filterIsInstance<SendPlan.WhatsApp>().firstOrNull())
    }
}
