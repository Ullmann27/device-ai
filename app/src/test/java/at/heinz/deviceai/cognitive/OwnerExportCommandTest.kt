package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerExportCommand.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OwnerExportCommandTest {
    private val date = LocalDate.of(2026, 11, 18)
    private fun parse(text: String) = OwnerExportCommand.parse(text)

    @Test fun saveOrdersOnTheResultAreRecognisedWithTheirFormat() {
        mapOf(
            "Speichere das als PDF" to Format.PDF,
            "Speicher das bitte als PDF ab" to Format.PDF,
            "Jarvis, speichere die Zusammenfassung als PDF in meinen Download-Ordner" to Format.PDF,
            "Kannst du mir das als PDF speichern?" to Format.PDF,
            "Mach mir daraus ein PDF" to Format.PDF,
            "Speichere die Zusammenfassung in meinen Downloads" to Format.PDF,
            "Als Word-Datei speichern" to Format.DOCX,
            "Speichere das als Word-Dokument" to Format.DOCX,
            "Exportiere die Nachrichten als Word" to Format.DOCX,
            "Fass mir die in Word zusammen" to Format.DOCX,
            "Speichere das als docx" to Format.DOCX
        ).forEach { (text, format) ->
            val request = parse(text)
            assertNotNull(text, request)
            assertEquals(text, format, request!!.format)
            assertNull(text, request.name)
            assertFalse(text, request.viaWord)
        }
    }

    @Test fun theOwnersFullSentenceMeansAPdfNamedByHimWrittenViaWord() {
        val request = parse("Ja, dann fasse mir die bitte in Word zusammen und speichere sie als PDF ab in meinen " +
            "Download-Ordner, als Nachrichten von heute")!!
        assertEquals(Format.PDF, request.format)
        assertTrue(request.viaWord)
        assertEquals("Nachrichten von heute", request.name)
        assertTrue(request.aboutNews)
        assertEquals("Wochenbericht", parse("Speichere das als PDF unter dem Namen Wochenbericht")!!.name)
        assertEquals("Lagebild Mittwoch", parse("Speichere das als Lagebild Mittwoch als PDF")!!.name)
        assertEquals("Bericht für den Chef", parse("Speichere das als Word-Datei und nenne sie „Bericht für den Chef“")!!.name)
        assertTrue(parse("Speichere die Nachrichten als PDF")!!.aboutNews)
        assertFalse(parse("Speichere das als PDF")!!.aboutNews)
    }

    @Test fun otherOrdersAreNotExports() {
        listOf(
            "Speichere das",                       // memory or export? not guessed
            "Speichere dir das",
            "Merk dir das als wichtig",
            "Speichere meine Telefonnummer als PDF",
            "Speichere das nicht als PDF",
            "Lies diese PDF und erstelle mir eine Zusammenfassung",
            "Fasse diese PDF zusammen",             // about an existing file: document lane
            "Öffne die PDF aus dem Download-Ordner",
            "Speichere die PDF",
            "Speichere das als Entwurf",
            "Speichere das als PDF und schick es an Anna",
            "Speichere das Foto in den Downloads",
            "Lade das in die Cloud hoch",
            "Mach ein PDF",                         // of what?
            "Wie speichere ich etwas als PDF?",
            "Nachrichten bitte"
        ).forEach { assertNull(it, parse(it)) }
        // The document lane keeps its own orders; the export order is not one of them.
        assertNotNull(OwnerDocumentIntent.parse("Lies diese PDF und erstelle mir eine Zusammenfassung"))
        assertNull(OwnerNewsCommand.parse("Speichere die Nachrichten als PDF"))
        assertEquals("NONE", EvalProbe.computer("Speichere das als PDF"))
    }

    @Test fun fileNamesAreDatedSafeAndNeverOverwrite() {
        val none: (String) -> Boolean = { false }
        assertEquals("Nachrichten von heute 18.11.2026.pdf",
            OwnerExportCommand.fileName(null, "Nachrichten von heute", date, Format.PDF, none))
        assertEquals("Nachrichten von heute 18.11.2026.pdf",
            OwnerExportCommand.fileName("Nachrichten von heute", "Nachrichten von heute", date, Format.PDF, none))
        assertEquals("Nachrichten von heute – Wirtschaft 18.11.2026.docx",
            OwnerExportCommand.fileName(null, "Nachrichten von heute – Wirtschaft", date, Format.DOCX, none))
        assertEquals("Wochenbericht.pdf", OwnerExportCommand.fileName("Wochenbericht", "Nachrichten von heute", date, Format.PDF, none))
        assertEquals("Nachrichten vom 18.11.2026.pdf",
            OwnerExportCommand.fileName("Nachrichten vom 18.11.2026", "x", date, Format.PDF, none))
        assertEquals("Bericht.pdf", OwnerExportCommand.fileName("Bericht.pdf", "x", date, Format.PDF, none))
        // Path separators, reserved and invisible characters never reach the file system.
        assertEquals("etc passwd a b.pdf", OwnerExportCommand.fileName("../../etc/passwd\u0000 a:b‮", "x", date, Format.PDF, none))
        assertEquals("Continuum Dokument 18.11.2026.pdf", OwnerExportCommand.fileName("///", "///", date, Format.PDF, none))
        val long = OwnerExportCommand.fileName("Sehr ".repeat(60), "x", date, Format.PDF, none)
        assertTrue(long.length <= OwnerExportCommand.MAX_BASE + 4)
        // An existing name is kept; the new file gets the next free number.
        val taken = mutableSetOf("Nachrichten von heute 18.11.2026.pdf")
        assertEquals("Nachrichten von heute 18.11.2026 (2).pdf",
            OwnerExportCommand.fileName(null, "Nachrichten von heute", date, Format.PDF, taken::contains))
        taken += "Nachrichten von heute 18.11.2026 (2).pdf"
        assertEquals("Nachrichten von heute 18.11.2026 (3).pdf",
            OwnerExportCommand.fileName(null, "Nachrichten von heute", date, Format.PDF, taken::contains))
        assertEquals("Nachrichten von heute 18.11.2026.docx",
            OwnerExportCommand.fileName(null, "Nachrichten von heute", date, Format.DOCX, taken::contains))
    }
}
