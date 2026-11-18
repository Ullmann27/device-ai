package at.heinz.deviceai

import at.heinz.deviceai.cognitive.NewsTestFeeds
import at.heinz.deviceai.cognitive.OwnerDeliverable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * After a result: „speichere das als PDF/Word“, „soll ich sie öffnen?“ and „schick das per Mail an …“ end to end on
 * the host. Orders, target choice, file names, document blocks, pagination, the Word file and all wording are the
 * productive code; the Download folder, the viewer and the mail app are in-memory stand-ins of the harness.
 */
class OwnerWorkToolsExportFlowTest {
    private val briefing = "Die Nationalbank von Musterstadt hat den Leitzins auf 2,5 Prozent gesenkt, das berichten die " +
        "tagesschau und der ORF. Außerdem müssen in Testland neue Häuser ab 2028 eine Solaranlage haben. Währenddessen " +
        "warnt ein Bericht vor häufigeren Dürren in Beispielia, wie die Deutsche Welle meldet."
    private val pdfName = "Nachrichten von heute 18.11.2026.pdf"

    private fun briefed(): Pair<OwnerWorkToolsHarness, OwnerWorkTools> {
        val host = OwnerWorkToolsHarness(model = { OwnerWorkGeneration(briefing, null, true) })
        val tools = OwnerWorkTools(host, now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
        assertTrue(tools.handle("Fass mir die Nachrichten von heute zusammen"))
        return host to tools
    }

    @Test fun theOwnersSentenceSavesADatedPdfInDownloadsAndReportsItLikeAColleague() {
        val (host, tools) = briefed()
        assertTrue(tools.handle("Ja, dann fasse mir die bitte in Word zusammen und speichere sie als PDF ab in meinen " +
            "Download-Ordner, als Nachrichten von heute"))
        assertEquals(listOf(pdfName), host.downloads.keys.toList())
        val text = String(host.downloads.getValue(pdfName), Charsets.UTF_8)
        // The document is the briefing itself with date, note and every source – not the closing question.
        listOf("Nachrichten von heute", "Mittwoch, 18. November 2026", "Die Nationalbank von Musterstadt", "Quellen",
            "tagesschau: Leitzins sinkt", "https://orf.at/stories/9000002/", "BBC News: Musterstadt central bank")
            .forEach { assertTrue(it, text.contains(it)) }
        assertFalse(text.contains("Soll ich"))
        assertEquals(listOf("export-build:Dokument aufbauen:ACTIVE:TOOL", "export-build:Dokument aufbauen:DONE",
            "export-save:Im Download-Ordner speichern:ACTIVE", "export-save:Im Download-Ordner speichern:DONE"),
            host.lines("STEP:").takeLast(4))
        val said = host.lines("SAY:").last()
        assertEquals("Erledigt. „$pdfName“ liegt jetzt in deinem Download-Ordner (1 Seite, mit Quellenliste). Word selbst " +
            "habe ich dafür nicht geöffnet – ich habe das Dokument direkt erstellt. Mit „als Word-Datei speichern“ bekommst " +
            "du zusätzlich die Word-Fassung. Soll ich sie öffnen?", said)
    }

    @Test fun aSecondSaveNeverOverwritesAndWordIsARealPackage() {
        val (host, tools) = briefed()
        assertTrue(tools.handle("Speichere das als PDF"))
        assertTrue(tools.handle("Speicher das bitte nochmal als PDF"))
        assertEquals(listOf(pdfName, "Nachrichten von heute 18.11.2026 (2).pdf"), host.downloads.keys.toList())
        assertTrue(host.lines("SAY:").last().contains("„Nachrichten von heute 18.11.2026 (2).pdf“"))
        assertTrue(tools.handle("Als Word-Datei speichern"))
        val docx = host.downloads.getValue("Nachrichten von heute 18.11.2026.docx")
        val parts = buildList {
            ZipInputStream(ByteArrayInputStream(docx)).use { zip -> while (true) { add((zip.nextEntry ?: break).name) } }
        }
        assertTrue(parts.containsAll(listOf("[Content_Types].xml", "word/document.xml")))
        assertTrue(host.lines("SAY:").last().startsWith("Erledigt. „Nachrichten von heute 18.11.2026.docx“ liegt jetzt in " +
            "deinem Download-Ordner (Word-Datei, mit Quellenliste)."))
        assertTrue(tools.handle("Speichere das als PDF unter dem Namen Lagebild/Mittwoch"))
        assertNotNull(host.downloads["Lagebild Mittwoch.pdf"])
    }

    @Test fun aFailedSaveIsReportedWithTheRealReasonAndNoFile() {
        val (host, tools) = briefed()
        host.saveProblem = "Kein Speicherplatz mehr frei"
        assertTrue(tools.handle("Speichere das als PDF"))
        assertTrue(host.downloads.isEmpty())
        assertEquals("Das Speichern hat nicht geklappt: Kein Speicherplatz mehr frei. Es wurde keine Datei angelegt.",
            host.lines("SAY:").last())
        assertEquals("export-save:Im Download-Ordner speichern:FAILED", host.lines("STEP:").last())
        // No offer to open a file that does not exist.
        assertFalse(tools.handle("Ja"))
        assertTrue(host.opened.isEmpty())
    }

    @Test fun openingIsOfferedOnlyWhenPossibleAndDoneOnYes() {
        val (host, tools) = briefed()
        assertTrue(tools.handle("Speichere das als PDF"))
        assertTrue(tools.handle("Ja bitte"))
        assertEquals(pdfName, host.opened.single().displayName)
        assertEquals("application/pdf", host.opened.single().mime)
        assertEquals("„$pdfName“ ist geöffnet.", host.lines("REPLY:").last())
        // „Nein“ leaves it; a problem of the viewer is said as it is.
        assertTrue(tools.handle("Als Word-Datei speichern"))
        assertTrue(tools.handle("Nein danke"))
        assertEquals("Alles klar, sie bleibt im Download-Ordner.", host.lines("REPLY:").last())
        assertEquals(1, host.opened.size)
        host.openProblem = "Ich habe keine App gefunden, die diese Datei öffnen kann."
        assertTrue(tools.handle("Öffne die gespeicherte Datei"))
        assertTrue(host.lines("REPLY:").last().startsWith("Öffnen hat nicht geklappt: Ich habe keine App gefunden"))
        // An old Android without MediaStore downloads: honest place, no offer.
        val (old, oldTools) = briefed()
        old.mediaStore = false
        assertTrue(oldTools.handle("Speichere das als PDF"))
        val said = old.lines("SAY:").last()
        assertTrue(said.contains("aber nicht im Download-Ordner"))
        assertFalse(said.contains("Soll ich sie öffnen?"))
        assertFalse(oldTools.handle("Ja"))
    }

    @Test fun withoutAnythingToSaveItSaysSoAndWithAnotherAnswerItSavesThatAnswer() {
        val empty = OwnerWorkToolsHarness()
        val emptyTools = OwnerWorkTools(empty, now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
        assertTrue(emptyTools.handle("Speichere das als PDF"))
        assertTrue(empty.lines("REPLY:").single().startsWith("In diesem Gespräch gibt es noch nichts, das ich speichern könnte."))
        assertTrue(empty.downloads.isEmpty())
        // „Das“ after an answer of another lane means that answer, not an older briefing …
        val (host, tools) = briefed()
        val answer = "Die Photosynthese wandelt Lichtenergie in chemische Energie um. Pflanzen bilden dabei aus Wasser " +
            "und Kohlendioxid Zucker und geben Sauerstoff ab."
        host.foreignTurn("Erklär mir die Photosynthese", answer)
        assertTrue(tools.handle("Speichere das als PDF"))
        val note = String(host.downloads.getValue("Continuum Notiz 18.11.2026.pdf"), Charsets.UTF_8)
        assertTrue(note.contains("Photosynthese") && !note.contains("Musterstadt"))
        assertTrue(host.lines("SAY:").last().contains("Darin steht meine letzte Antwort aus diesem Gespräch."))
        assertEquals(OwnerDeliverable.Kind.ASSISTANT_REPLY, tools.deliverable!!.kind)
        // … unless he names the news.
        val (named, namedTools) = briefed()
        named.foreignTurn("Erklär mir die Photosynthese", answer)
        assertTrue(namedTools.handle("Speichere die Nachrichten als PDF"))
        assertEquals(listOf(pdfName), named.downloads.keys.toList())
    }

    @Test fun mailIsPreparedWithTheSavedFileAndNothingIsSent() {
        val (host, tools) = briefed()
        assertTrue(tools.handle("Speichere das als PDF"))
        assertTrue(tools.handle("Schick das per Mail an anna.muster@example.org"))
        val mail = host.mails.single()
        assertEquals("anna.muster@example.org", mail.address)
        assertEquals("Nachrichten von heute 18.11.2026", mail.subject)
        assertTrue(mail.body.startsWith("Nachrichten von heute\nMittwoch, 18. November 2026"))
        assertTrue(mail.body.contains("Die Nationalbank von Musterstadt") && mail.body.contains("Quellen\n– tagesschau: Leitzins sinkt"))
        assertEquals(pdfName, mail.attachment!!.displayName)
        assertEquals("Die E-Mail ist vorbereitet: Betreff „Nachrichten von heute 18.11.2026“, der Text steht drin, " +
            "„$pdfName“ hängt an. Empfänger: anna.muster@example.org. Gesendet habe ich nichts – du musst im Mailprogramm " +
            "nur noch auf Senden tippen.", host.lines("REPLY:").last())
    }

    @Test fun aNameGetsNoAddressAndAnUnsavedResultNoAttachment() {
        val (host, tools) = briefed()
        assertTrue(tools.handle("Schicke bitte diese Zusammenfassung an meinen Arbeitskollegen oder meinen Chef"))
        val mail = host.mails.single()
        assertNull(mail.address)
        assertNull(mail.attachment)
        val reply = host.lines("REPLY:").last()
        assertTrue(reply.contains("ohne Anhang – gespeichert ist das Dokument noch nicht"))
        assertTrue(reply.contains("Für „meinen Arbeitskollegen oder meinen Chef“ habe ich keine Adresse – trag sie bitte " +
            "im Mailprogramm ein; auf deine Kontakte greife ich nicht zu."))
        assertTrue(reply.endsWith("Gesendet habe ich nichts – du musst im Mailprogramm nur noch auf Senden tippen."))
        // No mail app: said plainly.
        host.mailProblem = "Ich habe kein Mailprogramm gefunden, an das ich die Nachricht übergeben kann."
        assertTrue(tools.handle("Schick das per Mail an chef@firma.example"))
        assertEquals(1, host.mails.size)
        assertTrue(host.lines("REPLY:").last().startsWith("Die E-Mail konnte ich nicht vorbereiten: Ich habe kein Mailprogramm gefunden"))
        assertTrue(host.lines("REPLY:").last().endsWith("Es wurde nichts gesendet."))
    }

    @Test fun mailOrdersWithoutAResultOrForOtherChannelsStayWithTheOtherLanes() {
        val (host, tools) = briefed()
        // Another lane answered in between: „schick das an Anna“ without „Mail“ is no longer about the briefing.
        host.foreignTurn("Wie spät ist es?", "Es ist 10:30 Uhr.")
        assertFalse(tools.handle("Schick das an Anna"))
        assertFalse(tools.handle("Schick das per WhatsApp an Anna"))
        assertFalse(tools.handle("Schreib Anna eine Nachricht"))
        assertTrue(host.mails.isEmpty())
        val fresh = OwnerWorkToolsHarness()
        val freshTools = OwnerWorkTools(fresh, now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
        assertFalse(freshTools.handle("Schick das an Anna"))
        assertTrue(freshTools.handle("Schick das per Mail an anna@example.org"))
        assertEquals("In diesem Gespräch gibt es noch nichts, das ich verschicken könnte. Es wurde nichts vorbereitet.",
            fresh.lines("REPLY:").single())
        assertTrue(fresh.mails.isEmpty())
    }
}
