package at.heinz.deviceai

import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.ARGON
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_2
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_3
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerDocumentDialogue
import at.heinz.deviceai.cognitive.OwnerLibraryMatcher
import at.heinz.deviceai.cognitive.OwnerPageSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The document desk end to end on the host (owner goal 18.11.2026): „Öffne das PDF Argon4“ → local search, real hits,
 * question when unclear, the chosen PDF opened; „Fass Seite 2 zusammen und speichere es als Word“ → that page, the
 * model, a readable Word file in Downloads, the real name and place; „Ich meinte Seite 3“, a missing file, a refused
 * grant and „Stopp“. Parsers, matcher, state machine, page task, Word writer and all wording are the productive code;
 * the phone (storage, PDF reader, model text, Download folder, settings, picker) is the in-memory stand-in of the
 * harness. Nothing here proves the Fold7 – that stays NOT_RUN.
 */
class OwnerDocumentDeskTest {
    private val wordName = "Zusammenfassung Argon4 Seite 2 18.11.2026.docx"
    private val argonLine = "„Argon4.pdf“ – Ordner Download · 1,2 MB · geändert am 17.11.2026"

    private fun desk(configure: OwnerDocumentDeskHarness.() -> Unit = {}): Pair<OwnerDocumentDeskHarness, OwnerDocumentDesk> {
        val host = OwnerDocumentDeskHarness().apply(configure)
        return host to OwnerDocumentDeskHarness.desk(host)
    }

    /** Argon4 is on the device and open in the chat; the model answers with the summary of the page it was given. */
    private fun opened(configure: OwnerDocumentDeskHarness.() -> Unit = {}): Pair<OwnerDocumentDeskHarness, OwnerDocumentDesk> {
        val (host, desk) = desk {
            put("Argon4.pdf", pages = ARGON)
            model = { prompt -> OwnerWorkGeneration(if (prompt.contains("Seite 3 des Dokuments")) SUMMARY_3 else SUMMARY_2, null, true) }
            configure()
        }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertEquals("Argon4.pdf", host.shown.single().name)
        return host to desk
    }

    private fun size(host: OwnerDocumentDeskHarness, name: String) = OwnerLibraryMatcher.sizeText(host.downloads.getValue(name).size.toLong())

    /** All text of `word/document.xml`, parsed as XML (a broken document fails here). */
    private fun wordText(docx: ByteArray): String {
        var xml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(docx)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; if (entry.name == "word/document.xml") xml = zip.readBytes() }
        }
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(ByteArrayInputStream(checkNotNull(xml) { "word/document.xml fehlt" }))
        val paragraphs = document.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "p")
        return (0 until paragraphs.length).joinToString("\n") { paragraphs.item(it).textContent }
    }

    // ── „Öffne das PDF Argon4“ ──────────────────────────────────────────────────────────────────────────────

    @Test fun oneClearHitIsOpenedAndTheWorkWindowShowsWhatReallyHappened() {
        val (host, desk) = desk {
            put("Argon4.pdf", pages = ARGON)
            put("Rechnung November.pdf", "Documents")
            put("Mietvertrag_2024.pdf", "Documents/Verträge")
        }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertEquals(listOf(
            "desk-access:Dateizugriff prüfen:ACTIVE:TOOL", "desk-access:Dateizugriff erlaubt:DONE",
            "desk-scan:Speicher durchsuchen:ACTIVE", "desk-scan:Speicher durchsuchen · 3 PDFs geprüft:ACTIVE",
            "desk-scan:Speicher durchsucht · 3 PDFs geprüft:DONE", "desk-hits:1 Treffer:DONE",
            "desk-open:PDF öffnen:ACTIVE:EXECUTE", "desk-open:PDF geöffnet · 4 Seiten:DONE",
            "desk-text:Text gelesen · ${OwnerPageSummary.count(ARGON.sumOf { it.length })} Zeichen:DONE"), host.steps)
        assertEquals("Gefunden: $argonLine. Ich öffne es.", host.lines("SAY:").single())
        val shown = host.shown.single()
        assertEquals("Argon4.pdf", shown.name)
        assertEquals("file:///storage/emulated/0/Download/Argon4.pdf", shown.uri)
        assertEquals(listOf(listOf("Argon4.pdf")), host.hitsShown)
        // The found file is what the chat now refers to.
        assertEquals("Argon4.pdf", host.openDocument()!!.name)
        assertEquals(4, host.openDocument()!!.pageCount)
        assertFalse(desk.jobRunning)
    }

    @Test fun speechVariantsOfTheNameFindTheSameFile() {
        listOf("Öffne die PDF-Datei Argon 4", "öffne das pdf argon vier", "Zeig mir das PDF Argon4", "Öffne Argon4.pdf",
            "Such das PDF Argon4 und öffne es", "Mach bitte das PDF argon-4 auf").forEach { order ->
            val (host, desk) = desk { put("Argon4.pdf", pages = ARGON); put("Xenon.pdf") }
            assertTrue(order, desk.handle(order))
            assertEquals(order, "Argon4.pdf", host.shown.singleOrNull()?.name)
        }
    }

    @Test fun severalHitsAreShownNumberedAndTheOwnersChoiceIsOpened() {
        fun asked(): Pair<OwnerDocumentDeskHarness, OwnerDocumentDesk> {
            val (host, desk) = desk {
                put("Argon4 Datenblatt.pdf", pages = ARGON)
                put("Argon4 Messprotokoll.pdf", "Documents/Labor", listOf("Messwerte."), size = 317_440, modified = "2026-10-02")
                put("Xenon.pdf")
            }
            assertTrue(desk.handle("Öffne das PDF Argon4"))
            return host to desk
        }
        val (host, desk) = asked()
        assertEquals("Zu „Argon4“ habe ich 2 PDFs gefunden:\n" +
            "1. „Argon4 Datenblatt.pdf“ – Ordner Download · 1,2 MB · geändert am 17.11.2026\n" +
            "2. „Argon4 Messprotokoll.pdf“ – Ordner Documents/Labor · 310 KB · geändert am 02.10.2026\n" +
            "Welches soll ich öffnen? Sag zum Beispiel „das zweite“ oder einen Teil des Namens.", host.lines("REPLY:").single())
        assertEquals("desk-hits:2 Treffer:DONE", host.steps.last())
        assertEquals(listOf(listOf("Argon4 Datenblatt.pdf", "Argon4 Messprotokoll.pdf")), host.hitsShown)
        assertTrue("nothing is opened before the owner chose", host.shown.isEmpty())
        // Another topic leaves the question open, exactly like a chain's choice.
        assertFalse(desk.handle("Wie spät ist es?"))
        host.foreignTurn()
        assertTrue(desk.handle("die zweite"))
        assertEquals("Argon4 Messprotokoll.pdf", host.shown.single().name)
        assertEquals("Ich öffne „Argon4 Messprotokoll.pdf“ – Ordner Documents/Labor · 310 KB · geändert am 02.10.2026.",
            host.lines("SAY:").single())
        assertEquals(listOf("desk-open:PDF öffnen:ACTIVE:EXECUTE", "desk-open:PDF geöffnet · 1 Seite:DONE",
            "desk-text:Text gelesen · 10 Zeichen:DONE"), host.steps.takeLast(3))

        mapOf("Nummer 1" to "Argon4 Datenblatt.pdf", "2" to "Argon4 Messprotokoll.pdf", "das Datenblatt" to "Argon4 Datenblatt.pdf",
            "die neueste" to "Argon4 Datenblatt.pdf", "nimm das Messprotokoll" to "Argon4 Messprotokoll.pdf").forEach { (answer, name) ->
            val (other, otherDesk) = asked()
            assertTrue(answer, otherDesk.handle(answer))
            assertEquals(answer, name, other.shown.single().name)
        }
        // A number the list does not have is said and the question stays.
        val (wrong, wrongDesk) = asked()
        assertTrue(wrongDesk.handle("die fünfte"))
        assertEquals("Diese Auswahl gibt es nicht. Ich habe 2 Treffer genannt – welchen meinst du?", wrong.lines("REPLY:").last())
        assertTrue(wrongDesk.handle("die erste"))
        assertEquals("Argon4 Datenblatt.pdf", wrong.shown.single().name)
        // „Keines davon“ closes it; a later number is not taken any more.
        val (none, noneDesk) = asked()
        assertTrue(noneDesk.handle("keines davon"))
        assertEquals("In Ordnung, ich öffne keines davon.", none.lines("REPLY:").last())
        assertFalse(noneDesk.handle("die zweite"))
        assertTrue(none.shown.isEmpty())
    }

    @Test fun anExactNameIsOpenedTheOthersAreNamedAndACorrectionOpensTheOther() {
        fun found(): Pair<OwnerDocumentDeskHarness, OwnerDocumentDesk> {
            val (host, desk) = desk {
                put("Argon4.pdf", pages = ARGON)
                put("Argon4 Datenblatt.pdf", "Documents", listOf("Datenblatt."), size = 317_440, modified = "2026-10-02")
                put("Xenon.pdf", pages = listOf("Xenon."))
            }
            assertTrue(desk.handle("Öffne das PDF Argon4"))
            assertEquals("Argon4.pdf", host.shown.single().name)
            return host to desk
        }
        val (host, desk) = found()
        assertEquals("Gefunden: $argonLine. Ich öffne es. Es gibt einen weiteren Treffer: 2. „Argon4 Datenblatt.pdf“ " +
            "(Documents). Wenn du einen davon meinst, sag zum Beispiel „ich meinte die zweite“.", host.lines("SAY:").single())
        assertEquals("desk-hits:2 Treffer · einer genau:DONE", host.steps.first { it.startsWith("desk-hits") })
        // The correction changes the next action: the other hit is opened.
        assertTrue(desk.intercept("Ich meinte die andere Datei"))
        assertEquals(listOf("Argon4.pdf", "Argon4 Datenblatt.pdf"), host.shown.map { it.name })
        assertEquals("Argon4 Datenblatt.pdf", host.openDocument()!!.name)
        // … and back again: „die andere“ is now the first one.
        assertTrue(desk.intercept("Nein, die andere"))
        assertEquals("Argon4.pdf", host.shown.last().name)

        listOf("Nein, Argon4 Datenblatt", "Ich meinte die zweite", "Ich meinte das Datenblatt").forEach { correction ->
            val (other, otherDesk) = found()
            assertTrue(correction, otherDesk.intercept(correction))
            assertEquals(correction, "Argon4 Datenblatt.pdf", other.shown.last().name)
        }
        // A named PDF that was not among the hits is a new search.
        val (renamed, renamedDesk) = found()
        assertTrue(renamedDesk.intercept("Nein, ich meinte das PDF Xenon"))
        assertEquals("Xenon.pdf", renamed.shown.last().name)
        assertEquals(2, renamed.scans)
        // After another lane's turn the correction is no longer about the desk's search.
        val (late, lateDesk) = found()
        late.foreignTurn()
        assertFalse(lateDesk.intercept("Ich meinte die andere Datei"))
        assertEquals(1, late.shown.size)
    }

    @Test fun withASingleHitThereIsNoOtherFileToMean() {
        val (host, desk) = desk { put("Argon4.pdf", pages = ARGON) }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertTrue(desk.intercept("Ich meinte die andere Datei"))
        assertEquals("Zu „Argon4“ gab es nur diesen einen Treffer. Wie heißt die Datei, die du meinst?", host.lines("REPLY:").last())
        assertEquals(1, host.shown.size)
    }

    @Test fun aMissingFileIsSaidWithWhatWasSearchedAndOnlyThePickerIsOffered() {
        val (host, desk) = desk { put("Rechnung.pdf"); put("Notiz.pdf", "Documents") }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertEquals("Ein PDF namens „Argon4“ habe ich nicht gefunden. Geprüft habe ich 2 PDFs im internen Speicher " +
            "(Ordner: Download, Documents, DCIM). Wenn die Datei woanders liegt oder anders heißt, kannst du sie selbst " +
            "auswählen. Soll ich die Android-Dateiauswahl öffnen?", host.lines("REPLY:").single())
        assertEquals("desk-hits:Kein Treffer:DONE", host.steps.last())
        assertTrue(host.shown.isEmpty() && host.pickers.isEmpty())
        assertTrue(desk.handle("Ja"))
        assertEquals(listOf("Öffne das PDF Argon4"), host.pickers)
        assertEquals("Ich öffne die Android-Dateiauswahl.", host.lines("REPLY:").last())

        val (declined, declinedDesk) = desk { put("Rechnung.pdf") }
        assertTrue(declinedDesk.handle("Öffne das PDF Argon4"))
        assertTrue(declinedDesk.handle("Nein danke"))
        assertEquals("Alles klar, ich öffne nichts.", declined.lines("REPLY:").last())
        assertTrue(declined.pickers.isEmpty())
        // A bound that ended the walk early is part of the honest answer.
        val (bounded, boundedDesk) = desk {
            put("Rechnung.pdf")
            scanStopped = "Nach 12 Sekunden habe ich die Suche beendet; nicht alle Ordner wurden geprüft."
        }
        assertTrue(boundedDesk.handle("Öffne das PDF Argon4"))
        assertTrue(bounded.lines("REPLY:").single().contains("Geprüft habe ich 1 PDF im internen Speicher (Ordner: Download, " +
            "Documents, DCIM). Nach 12 Sekunden habe ich die Suche beendet; nicht alle Ordner wurden geprüft."))
        // The offer belongs to the very next turn only.
        val (stale, staleDesk) = desk { put("Rechnung.pdf") }
        assertTrue(staleDesk.handle("Öffne das PDF Argon4"))
        stale.foreignTurn()
        assertFalse(staleDesk.handle("Ja"))
        assertTrue(stale.pickers.isEmpty())
    }

    @Test fun aNearMissIsAskedAboutAndNeverOpenedSilently() {
        val (host, desk) = desk { put("Argon4.pdf", pages = ARGON) }
        assertTrue(desk.handle("Öffne das PDF Organ 4"))
        assertEquals("Ein PDF namens „Organ 4“ habe ich nicht gefunden. Geprüft habe ich 1 PDF im internen Speicher " +
            "(Ordner: Download, Documents, DCIM). Ähnlich heißt: $argonLine. Meinst du das?", host.lines("REPLY:").single())
        assertEquals("desk-hits:Kein Treffer · nur ähnliche Namen:DONE", host.steps.last())
        assertTrue(host.shown.isEmpty())
        assertTrue(desk.handle("Ja, das meine ich"))
        assertEquals("Argon4.pdf", host.shown.single().name)

        val (declined, declinedDesk) = desk { put("Argon4.pdf", pages = ARGON) }
        assertTrue(declinedDesk.handle("Öffne das PDF Organ 4"))
        assertTrue(declinedDesk.handle("Nein"))
        assertEquals("In Ordnung, ich öffne nichts. Soll ich die Android-Dateiauswahl öffnen?", declined.lines("REPLY:").last())
        assertTrue(declined.shown.isEmpty())
    }

    @Test fun aPureSearchShowsTheHitAndOpensItOnlyOnYes() {
        val (host, desk) = desk { put("Argon4.pdf", pages = ARGON) }
        assertTrue(desk.handle("Such das PDF Argon4"))
        assertEquals("Gefunden: $argonLine. Soll ich es öffnen?", host.lines("REPLY:").single())
        assertTrue(host.shown.isEmpty())
        assertFalse(host.steps.any { it.startsWith("desk-open") })
        assertTrue(desk.handle("Ja bitte"))
        assertEquals("Argon4.pdf", host.shown.single().name)
        // With further hits the search shows all of them and waits for the choice.
        val (many, manyDesk) = desk {
            put("Argon4.pdf", pages = ARGON)
            put("Argon4 Datenblatt.pdf", "Documents", listOf("Datenblatt."), size = 317_440, modified = "2026-10-02")
        }
        assertTrue(manyDesk.handle("Wo ist das PDF Argon4?"))
        assertEquals("Zu „Argon4“ habe ich 2 PDFs gefunden:\n1. $argonLine\n" +
            "2. „Argon4 Datenblatt.pdf“ – Ordner Documents · 310 KB · geändert am 02.10.2026\n" +
            "Der erste Treffer heißt genau so. Welches soll ich öffnen? Sag zum Beispiel „das erste“ oder einen Teil des Namens.",
            many.lines("REPLY:").single())
        assertTrue(many.shown.isEmpty())
        assertTrue(manyDesk.handle("das Datenblatt"))
        assertEquals("Argon4 Datenblatt.pdf", many.shown.single().name)
    }

    @Test fun aFileThatVanishedOrCannotBeReadIsReportedAndNothingIsShown() {
        val (host, desk) = desk { put("Argon4.pdf", pages = ARGON).also { pdfs.remove(it.path) } }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertEquals("desk-open:PDF ließ sich nicht öffnen:FAILED", host.steps.last())
        assertEquals("Gefunden: $argonLine. Öffnen ließ es sich aber nicht: Argon4.pdf ist nicht mehr da. An der Datei wurde " +
            "nichts verändert.", host.lines("SAY:").single())
        assertTrue(host.shown.isEmpty())
        assertNull(host.openDocument())
    }

    // ── Refused or missing grant ────────────────────────────────────────────────────────────────────────────

    @Test fun withoutTheGrantNothingIsSearchedAndAfterTheOwnersYesTheSearchContinuesByItself() {
        val (host, desk) = desk { libraryAccess = OwnerLibraryAccess.MISSING; put("Argon4.pdf", pages = ARGON) }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertEquals(OwnerDocumentDesk.GRANT_MISSING, host.lines("REPLY:").single())
        assertEquals(listOf("desk-access:Dateizugriff prüfen:ACTIVE:TOOL", "desk-access:Dateizugriff fehlt:BLOCKED"), host.steps)
        assertEquals(0, host.scans)
        assertEquals(0, host.settingsOpened)
        assertNull(host.pendingSearch)

        assertTrue(desk.handle("Ja"))
        assertEquals(1, host.settingsOpened)
        assertEquals("Ich öffne die Android-Einstellung. Schalte dort „Zugriff auf alle Dateien zulassen“ für Continuum ein " +
            "und komm zurück – dann suche ich „Argon4“ sofort weiter.", host.lines("REPLY:").last())
        assertEquals("Argon4", host.pendingSearch!!.query)
        assertEquals(0, host.scans)

        // The owner switched it on and came back.
        host.libraryAccess = OwnerLibraryAccess.GRANTED
        desk.onResumed()
        assertNull(host.pendingSearch)
        assertEquals(listOf("Der Zugriff auf alle Dateien ist jetzt erlaubt. Ich suche „Argon4“.", "Gefunden: $argonLine. Ich öffne es."),
            host.lines("SAY:"))
        assertEquals("Argon4.pdf", host.shown.single().name)
        // Coming to the front again does nothing more.
        desk.onResumed()
        assertEquals(1, host.scans)
    }

    @Test fun returningWithoutTheGrantIsSaidAndDecliningOpensNothing() {
        val (host, desk) = desk { libraryAccess = OwnerLibraryAccess.MISSING; put("Argon4.pdf", pages = ARGON) }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertTrue(desk.handle("Ja, öffne die Einstellung"))
        desk.onResumed()
        assertEquals("Der Zugriff auf alle Dateien ist weiterhin nicht erlaubt. Ich habe nichts durchsucht. Soll ich die " +
            "Android-Dateiauswahl öffnen?", host.lines("SAY:").single())
        assertEquals(0, host.scans)
        assertNull(host.pendingSearch)
        assertTrue(desk.handle("Ja"))
        assertEquals(listOf("Öffne das PDF Argon4"), host.pickers)

        // „Nein“: nothing is opened; Android's own picker is offered as the alternative.
        val (declined, declinedDesk) = desk { libraryAccess = OwnerLibraryAccess.MISSING; put("Argon4.pdf", pages = ARGON) }
        assertTrue(declinedDesk.handle("Öffne das PDF Argon4"))
        assertTrue(declinedDesk.handle("Nein"))
        assertEquals("In Ordnung – ich öffne nichts und habe nichts durchsucht. Du kannst die Datei auch selbst auswählen. " +
            "Soll ich die Android-Dateiauswahl öffnen?", declined.lines("REPLY:").last())
        assertEquals(0, declined.settingsOpened)
        assertNull(declined.pendingSearch)
        assertTrue(declined.shown.isEmpty() && declined.pickers.isEmpty())
        assertTrue(declinedDesk.handle("Ja"))
        assertEquals(1, declined.pickers.size)
        // Coming back later without a pending search changes nothing.
        declined.libraryAccess = OwnerLibraryAccess.GRANTED
        declinedDesk.onResumed()
        assertEquals(0, declined.scans)
    }

    @Test fun theWaitingSearchSurvivesARestartAndExpires() {
        val (host, desk) = desk { libraryAccess = OwnerLibraryAccess.MISSING; put("Argon4.pdf", pages = ARGON) }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertTrue(desk.handle("Ja"))
        host.libraryAccess = OwnerLibraryAccess.GRANTED
        // Android recreated the Activity while the settings were open: a new desk on the same stored state.
        OwnerDocumentDeskHarness.desk(host).onResumed()
        assertEquals("Argon4.pdf", host.shown.single().name)
        // Another chat, or much later: the stored search is dropped without a word.
        val (other, otherDesk) = desk { put("Argon4.pdf", pages = ARGON) }
        other.pendingSearch = OwnerDeskPendingSearch("chat-9", "Öffne das PDF Argon4", "Argon4", true, null, OwnerDocumentDeskHarness.NOW.toEpochMilli())
        otherDesk.onResumed()
        other.pendingSearch = OwnerDeskPendingSearch("chat-1", "Öffne das PDF Argon4", "Argon4", true, null,
            OwnerDocumentDeskHarness.NOW.toEpochMilli() - 11 * 60_000L)
        otherDesk.onResumed()
        assertNull(other.pendingSearch)
        assertTrue(other.log.isEmpty())
        // The settings page cannot be opened: said, with the way to it, and nothing stays pending.
        val (blocked, blockedDesk) = desk { libraryAccess = OwnerLibraryAccess.MISSING; settingsProblem = "Android hat die Seite nicht geöffnet" }
        assertTrue(blockedDesk.handle("Öffne das PDF Argon4"))
        assertTrue(blockedDesk.handle("Ja"))
        assertTrue(blocked.lines("REPLY:").last().startsWith("Die Einstellung ließ sich nicht öffnen: Android hat die Seite nicht " +
            "geöffnet. Du findest sie unter Einstellungen › Apps › Spezieller Zugriff › Zugriff auf alle Dateien."))
        assertNull(blocked.pendingSearch)
        // An Android without this grant: said, picker offered.
        val (old, oldDesk) = desk { libraryAccess = OwnerLibraryAccess.UNSUPPORTED }
        assertTrue(oldDesk.handle("Öffne das PDF Argon4"))
        assertEquals("Die Suche im Gerätespeicher gibt es erst ab Android 11. Ich habe nichts durchsucht. Soll ich die " +
            "Android-Dateiauswahl öffnen?", old.lines("REPLY:").single())
    }

    // ── „Fass Seite 2 zusammen und speichere es als Word“ ───────────────────────────────────────────────────

    @Test fun thePageIsSummarisedFromItsOwnTextSavedAsAReadableWordFileAndReportedWithItsRealPlace() {
        val (host, desk) = opened()
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        // The model saw exactly that page.
        val prompt = host.prompts.single()
        assertTrue(prompt.contains("Seite 2 des Dokuments „Argon4“") && prompt.contains(ARGON[1]))
        assertFalse(prompt.contains(ARGON[0]) || prompt.contains(ARGON[2]))
        assertTrue(prompt.toByteArray(Charsets.UTF_8).size <= OwnerPageSummary.MAX_PROMPT_BYTES)
        // One file, readable as Word, with title, source line and the summary.
        assertEquals(listOf(wordName), host.downloads.keys.toList())
        val docx = host.downloads.getValue(wordName)
        assertTrue(OwnerPageSummary.readableDocx(docx))
        assertEquals("Zusammenfassung Argon4 Seite 2\nMittwoch, 18. November 2026\n" +
            "Quelle: „Argon4.pdf“, Seite 2 von 4 · zusammengefasst vom Sprachmodell auf dem Gerät am 18.11.2026 · vor der " +
            "Verwendung prüfen.\n$SUMMARY_2", wordText(docx))
        assertEquals(listOf(
            "desk-page:Seite 2 lesen:ACTIVE:TOOL", "desk-page:Seite 2 gelesen · ${ARGON[1].length} Zeichen:DONE",
            "desk-summary:Zusammenfassen:ACTIVE:THINK", "desk-summary:Zusammenfassen · ${SUMMARY_2.length} Zeichen:ACTIVE",
            "desk-summary:Zusammengefasst · ${SUMMARY_2.length} Zeichen:DONE",
            "desk-build:Word-Datei aufbauen:ACTIVE:TOOL", "desk-build:Word-Datei aufbauen:DONE",
            "desk-save:In Downloads speichern:ACTIVE", "desk-save:In Downloads gespeichert:DONE",
            "desk-verify:Gespeicherte Datei prüfen:ACTIVE:VERIFY", "desk-verify:Datei geprüft · ${size(host, wordName)}:DONE"),
            host.steps.dropWhile { !it.startsWith("desk-page") })
        assertEquals("Seite 2 von „Argon4.pdf“ – Zusammenfassung:\n\n$SUMMARY_2\n\nGespeichert als Word-Datei: „$wordName“ in " +
            "deinem Download-Ordner (Interner Speicher › Download), ${size(host, wordName)}. Soll ich sie öffnen?",
            host.lines("SAY:").last())
        // The result is handed to the work tools for „öffne die gespeicherte Datei“ and „schick das per Mail“.
        val (result, file) = host.adopted.single()
        assertEquals(OwnerDeliverable.Kind.PAGE_SUMMARY, result.kind)
        assertEquals(wordName, file.displayName)
        assertNotNull(file.uri)
    }

    @Test fun theOwnersVariantsOfThePageOrderAreTheSameOrder() {
        mapOf(
            "Fasse die zweite Seite zusammen und speichere sie als Word-Datei" to wordName,
            "Fass Seite 2 zusammen und speichere es als PDF" to "Zusammenfassung Argon4 Seite 2 18.11.2026.pdf",
            "Fass Seite 2 zusammen und speichere es in meinen Downloads als Word" to wordName,
            "Fass Seite 2 zusammen und speicher das als Word unter dem Namen Argon Kurzfassung" to "Argon Kurzfassung.docx",
            "fass seite zwei zusammen und speicher es als word" to wordName
        ).forEach { (order, name) ->
            val (host, desk) = opened()
            assertTrue(order, desk.handle(order))
            assertEquals(order, listOf(name), host.downloads.keys.toList())
            assertTrue(order, host.prompts.single().contains(ARGON[1]))
        }
        // A PDF is built from the same blocks and says so.
        val (host, desk) = opened()
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als PDF"))
        val text = String(host.downloads.values.single(), Charsets.UTF_8)
        assertTrue(text.contains(SUMMARY_2) && text.contains("Quelle: „Argon4.pdf“, Seite 2 von 4"))
        assertTrue(host.lines("SAY:").last().contains("Gespeichert als PDF: „Zusammenfassung Argon4 Seite 2 18.11.2026.pdf“ in deinem Download-Ordner"))
        assertTrue(host.steps.contains("desk-build:PDF aufbauen:DONE"))
        // Without a format the desk asks once and then does it.
        val (asked, askedDesk) = opened()
        assertTrue(askedDesk.handle("Fass Seite 2 zusammen und speichere es"))
        assertEquals("Als was soll ich die Zusammenfassung speichern – als Word-Datei oder als PDF?", asked.lines("REPLY:").last())
        assertTrue(asked.downloads.isEmpty())
        assertTrue(askedDesk.handle("Als Word"))
        assertEquals(listOf(wordName), asked.downloads.keys.toList())
        // Several pages at once are said plainly; nothing half is done.
        val (several, severalDesk) = opened()
        assertTrue(severalDesk.handle("Fass die Seiten 2 bis 4 zusammen und speichere sie als Word"))
        assertTrue(several.lines("REPLY:").last().startsWith("Im Moment fasse ich pro Auftrag genau eine Seite zusammen"))
        assertTrue(several.downloads.isEmpty() && several.prompts.isEmpty())
    }

    @Test fun aSecondSaveNeverOverwritesAndAPdfFromThePickerWorksTheSame() {
        val (host, desk) = opened()
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals(listOf(wordName, "Zusammenfassung Argon4 Seite 2 18.11.2026 (2).docx"), host.downloads.keys.toList())
        // A document that was opened through Android's picker earlier in this chat.
        val (picked, pickedDesk) = desk {
            opened("Bericht 2026.pdf", listOf("Erste Seite des Berichts mit genug Text für eine Zusammenfassung."))
            model = { OwnerWorkGeneration(SUMMARY_2, null, true) }
        }
        assertTrue(pickedDesk.handle("Fass die letzte Seite zusammen und speichere sie als Word"))
        assertEquals(listOf("Zusammenfassung Bericht 2026 Seite 1 18.11.2026.docx"), picked.downloads.keys.toList())
        assertTrue(picked.prompts.single().contains("Erste Seite des Berichts"))
    }

    @Test fun withoutALoadedModelNothingIsInventedAndNothingIsSaved() {
        val (host, desk) = opened { modelLoaded = false }
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals("Seite 2 von „Argon4.pdf“ habe ich gelesen (${ARGON[1].length} Zeichen). Für die Zusammenfassung brauche ich " +
            "aber das Sprachmodell, und im Moment ist keines geladen. Ich habe nichts zusammengefasst und nichts gespeichert. " +
            "Lade das Modell in der Modellverwaltung und gib mir den Auftrag dann noch einmal.", host.lines("SAY:").last())
        assertEquals("desk-summary:Kein Sprachmodell geladen:BLOCKED", host.steps.last())
        assertTrue(host.downloads.isEmpty() && host.prompts.isEmpty() && host.adopted.isEmpty())
    }

    @Test fun aPageWithoutTextOrBeyondTheDocumentIsSaidAsSuch() {
        val (host, desk) = opened()
        assertTrue(desk.handle("Fass Seite 4 zusammen und speichere es als Word"))
        assertEquals("Seite 4 von „Argon4.pdf“ hat keinen auslesbaren Text – zum Beispiel ein Scan. Ich habe nichts " +
            "zusammengefasst und nichts gespeichert.", host.lines("SAY:").last())
        assertEquals("desk-page:Seite 4 hat keinen Text:FAILED", host.steps.last())
        assertTrue(desk.handle("Fass Seite 9 zusammen und speichere es als Word"))
        assertEquals("„Argon4.pdf“ hat nur 4 Seiten – eine Seite 9 gibt es nicht. Ich habe nichts zusammengefasst und nichts " +
            "gespeichert.", host.lines("REPLY:").last())
        assertTrue(host.downloads.isEmpty() && host.prompts.isEmpty())
        // The correction of that order still changes the next action.
        assertTrue(desk.intercept("Ich meinte Seite 3"))
        assertEquals(listOf("Zusammenfassung Argon4 Seite 3 18.11.2026.docx"), host.downloads.keys.toList())
        assertTrue(host.lines("SAY:").last().startsWith("Seite 3 von „Argon4.pdf“ – statt Seite 9 – Zusammenfassung:\n\n$SUMMARY_3"))

        val (none, noneDesk) = desk()
        assertTrue(noneDesk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals("Im Moment ist kein PDF geöffnet. Sag zum Beispiel „Öffne das PDF Argon4“ – danach fasse ich dir die Seite " +
            "zusammen und speichere sie. Ich habe nichts gespeichert.", none.lines("REPLY:").single())
        assertTrue(none.steps.isEmpty())
        // An Android that cannot extract page text.
        val (old, oldDesk) = desk {
            put("Scan.pdf", pages = listOf("", ""), textState = OwnerDocumentDialogue.TextState.NEEDS_ANDROID_15)
            model = { OwnerWorkGeneration(SUMMARY_2, null, true) }
        }
        assertTrue(oldDesk.handle("Öffne das PDF Scan"))
        assertEquals("desk-text:Textauszug braucht Android 15:BLOCKED", old.steps.last())
        assertTrue(oldDesk.handle("Fass Seite 1 zusammen und speichere es als Word"))
        assertEquals("Den Text einer PDF-Seite kann ich erst ab Android 15 auslesen. Ich habe nichts zusammengefasst und nichts " +
            "gespeichert.", old.lines("SAY:").last())
    }

    @Test fun aPageLongerThanTheModelsRoomIsCutAndTheResultSaysSo() {
        val long = (1..400).joinToString(" ") { "Satz $it der langen zweiten Seite steht hier." }
        val (host, desk) = desk {
            put("Argon4.pdf", pages = listOf(ARGON[0], long))
            model = { OwnerWorkGeneration(SUMMARY_2, null, true) }
        }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        val prompt = host.prompts.single()
        assertTrue(prompt.toByteArray(Charsets.UTF_8).size <= OwnerPageSummary.MAX_PROMPT_BYTES)
        assertTrue(prompt.contains("Satz 1 der langen") && !prompt.contains("Satz 400 der langen"))
        val said = host.lines("SAY:").last()
        val covered = Regex("Grundlage sind die ersten ([\\d.]+) von ([\\d.]+) Zeichen der Seite\\.").find(said)!!
        assertEquals(OwnerPageSummary.count(long.length), covered.groupValues[2])
        assertTrue(covered.groupValues[1].replace(".", "").toInt() in 2_000..OwnerPageSummary.MAX_EXCERPT_CHARS)
        assertTrue(wordText(host.downloads.values.single()).contains("Grundlage sind die ersten ${covered.groupValues[1]} von"))
    }

    @Test fun aModelThatFailsOrAFolderThatRefusesIsReportedWithoutAFile() {
        val (host, desk) = opened { model = { OwnerWorkGeneration("", "BOT_DEADLINE_EXCEEDED", false) } }
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals("Das Sprachmodell hat für Seite 2 keine brauchbare Zusammenfassung geliefert (BOT_DEADLINE_EXCEEDED). Es wurde " +
            "nichts gespeichert.", host.lines("SAY:").last())
        assertEquals("desk-summary:Zusammenfassen fehlgeschlagen:FAILED", host.steps.last())
        assertTrue(host.downloads.isEmpty())
        // A cut-off or empty answer is not a summary either.
        val (cut, cutDesk) = opened { model = { OwnerWorkGeneration(SUMMARY_2, null, false) } }
        assertTrue(cutDesk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertTrue(cut.lines("SAY:").last().startsWith("Das Sprachmodell hat für Seite 2 keine brauchbare Zusammenfassung geliefert."))
        assertTrue(cut.downloads.isEmpty())
        // Saving fails: the reason, no file, and the summary is not lost.
        val (full, fullDesk) = opened { saveProblem = "Kein Speicherplatz mehr frei" }
        assertTrue(fullDesk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals("Die Zusammenfassung von Seite 2 steht, aber das Speichern hat nicht geklappt: Kein Speicherplatz mehr " +
            "frei. Es wurde keine Datei angelegt.\n\n$SUMMARY_2", full.lines("SAY:").last())
        assertEquals("desk-save:Speichern fehlgeschlagen:FAILED", full.steps.last())
        assertTrue(full.downloads.isEmpty() && full.adopted.isEmpty())
        // … and the same order again reuses it instead of computing once more.
        full.saveProblem = null
        assertTrue(fullDesk.intercept("Nein, als Word"))
        assertEquals(listOf(wordName), full.downloads.keys.toList())
        assertEquals(1, full.prompts.size)
        // A file that cannot be read back is not called verified.
        val (blind, blindDesk) = opened { readBackWorks = false }
        assertTrue(blindDesk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals("desk-verify:Datei nicht zurückgelesen:UNVERIFIED", blind.steps.last())
        assertTrue(blind.lines("SAY:").last().contains("Zurücklesen konnte ich die Datei danach nicht – bitte prüf sie, bevor du dich darauf verlässt."))
        // An Android without a shared Download folder: the honest place, no offer.
        val (legacy, legacyDesk) = opened { mediaStore = false }
        assertTrue(legacyDesk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        val said = legacy.lines("SAY:").last()
        assertTrue(said.contains("aber nicht im Download-Ordner") && !said.contains("Soll ich sie öffnen?"))
    }

    @Test fun theCloudBrainIsNeverCalledTheModelOnTheDevice() {
        val (host, desk) = opened { cloud = true }
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertTrue(host.lines("SAY:").last().contains("Zusammengefasst habe ich das über die Cloud, nicht mit dem Modell auf dem Gerät."))
        assertTrue(wordText(host.downloads.values.single()).contains("zusammengefasst vom Cloud-Gehirn am 18.11.2026"))
    }

    // ── Corrections change the next action ──────────────────────────────────────────────────────────────────

    @Test fun iMeantPageThreeRunsTheSameOrderForThatPageAndThePageTwoFileStays() {
        listOf("Ich meinte Seite 3", "Nein, Seite 3", "Nicht Seite 2, sondern Seite 3", "Seite drei meinte ich").forEach { correction ->
            val (host, desk) = opened()
            assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
            assertTrue(correction, desk.intercept(correction))
            val third = "Zusammenfassung Argon4 Seite 3 18.11.2026.docx"
            assertEquals(correction, listOf(wordName, third), host.downloads.keys.toList())
            assertTrue(host.prompts[1].contains(ARGON[2]) && !host.prompts[1].contains(ARGON[1]))
            assertTrue(wordText(host.downloads.getValue(third)).contains("Quelle: „Argon4.pdf“, Seite 3 von 4") &&
                wordText(host.downloads.getValue(third)).endsWith(SUMMARY_3))
            assertEquals("Seite 3 von „Argon4.pdf“ – statt Seite 2 – Zusammenfassung:\n\n$SUMMARY_3\n\nGespeichert als Word-Datei: " +
                "„$third“ in deinem Download-Ordner (Interner Speicher › Download), ${size(host, third)}. Die Datei von vorhin " +
                "(Seite 2, „$wordName“) gibt es weiterhin. Soll ich sie öffnen?", host.lines("SAY:").last())
            assertEquals(third, host.adopted.last().second.displayName)
        }
    }

    @Test fun iMeantAsPdfSavesTheSameSummaryInTheOtherFormatWithoutComputingAgain() {
        val (host, desk) = opened()
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertTrue(desk.intercept("Ich meinte als PDF"))
        val pdf = "Zusammenfassung Argon4 Seite 2 18.11.2026.pdf"
        assertEquals(listOf(wordName, pdf), host.downloads.keys.toList())
        assertEquals("the accepted summary is reused", 1, host.prompts.size)
        assertTrue(String(host.downloads.getValue(pdf), Charsets.UTF_8).contains(SUMMARY_2))
        assertEquals("Erledigt – dieselbe Zusammenfassung von Seite 2 jetzt als PDF: „$pdf“ in deinem Download-Ordner (Interner " +
            "Speicher › Download), ${size(host, pdf)}. Die Datei von vorhin (Word-Datei, „$wordName“) gibt es weiterhin. Soll " +
            "ich sie öffnen?", host.lines("SAY:").last())
        // The very same order again is not repeated blindly.
        assertTrue(desk.intercept("Ich meinte als PDF"))
        assertEquals("Genau das habe ich gerade gespeichert: „$pdf“ (Seite 2 als PDF). Was soll ich anders machen?",
            host.lines("REPLY:").last())
        assertEquals(2, host.downloads.size)
    }

    @Test fun aCorrectionWhileThePageJobRunsStopsItAndRunsTheCorrectedOrder() {
        val (host, desk) = opened()
        host.duringGeneration = { assertTrue(desk.intercept("Nein, Seite 3")) }
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        val third = "Zusammenfassung Argon4 Seite 3 18.11.2026.docx"
        assertEquals("nothing half-written of page 2 remains", listOf(third), host.downloads.keys.toList())
        assertEquals(1, host.cancels)
        assertEquals("Verstanden – Seite 3 statt Seite 2. Den laufenden Auftrag breche ich dafür ab.", host.lines("REPLY:").last())
        assertTrue(host.steps.contains("desk-summary:Zusammenfassen gestoppt:FAILED"))
        assertEquals("Seite 3 von „Argon4.pdf“ – statt Seite 2 – Zusammenfassung:\n\n$SUMMARY_3\n\nGespeichert als Word-Datei: " +
            "„$third“ in deinem Download-Ordner (Interner Speicher › Download), ${size(host, third)}. Der abgebrochene Auftrag " +
            "(Seite 2) hat nichts gespeichert. Soll ich sie öffnen?", host.lines("SAY:").single { it.startsWith("Seite 3") })
        assertFalse(host.spoken.any { it.startsWith("Gestoppt") })
        assertFalse(desk.jobRunning)
        // Saying the page that already runs changes nothing.
        val (same, sameDesk) = opened()
        same.duringGeneration = { assertTrue(sameDesk.intercept("Ich meinte Seite 2")) }
        assertTrue(sameDesk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals(0, same.cancels)
        assertEquals(listOf(wordName), same.downloads.keys.toList())
        assertTrue(same.lines("REPLY:").contains("Genau das läuft gerade: Seite 2 als Word-Datei."))
    }

    @Test fun aCorrectionWithNothingToCorrectAsksOneShortQuestionOrLeavesItToItsOwnLane() {
        val (host, desk) = desk()
        assertFalse("nothing happened before: not a correction of the desk's work", desk.intercept("Ich meinte Seite 3"))
        assertTrue(desk.handle("Ich meinte Seite 3"))
        assertEquals("Worauf bezieht sich die Seite? Im Moment ist kein PDF geöffnet und kein Seitenauftrag offen.",
            host.lines("REPLY:").single())
        host.foreignTurn()
        assertTrue(desk.handle("Ich meinte als PDF"))
        assertEquals("Was soll ich als PDF speichern? Ich habe gerade nichts gespeichert, das ich ändern könnte.", host.lines("REPLY:").last())
        host.foreignTurn()
        assertTrue(desk.handle("Ich meinte die andere Datei"))
        assertEquals("Welche Datei meinst du? Ich habe gerade keine Trefferliste offen. Sag zum Beispiel „Öffne das PDF Argon4“.",
            host.lines("REPLY:").last())
        // The existing correction of the work journal keeps its sentence, and so do answers of other lanes.
        listOf("Nein, ich meinte das andere PDF", "Nein, ich meinte den letzten Entwurf", "Nein, die zweite", "Nein, die andere",
            "Nein, Anna Muster").forEach {
            host.foreignTurn()
            assertFalse(it, desk.intercept(it))
            assertFalse(it, desk.handle(it))
        }
        // With a PDF open, a page without a page order is the reference registry's focus change.
        val (open, openDesk) = opened()
        open.foreignTurn()
        assertFalse(openDesk.intercept("Nein, Seite 3"))
        assertFalse(openDesk.handle("Nein, Seite 3"))
        // „Ich meinte als Word“ after a save of the work tools goes back to them.
        val asked = mutableListOf<String>()
        val (tools, toolsDesk) = desk { exportAgainTakes = { request, format -> asked += "$request:${format.name}"; true } }
        assertTrue(toolsDesk.handle("Ich meinte als Word"))
        assertEquals(listOf("Ich meinte als Word:DOCX"), asked)
        assertTrue(tools.spoken.isEmpty())
    }

    // ── „Stopp“ ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun stoppEndsARunningPageJobWithoutAFileAndSaysWhatWasNotDone() {
        listOf("Stopp", "Stop", "Halt", "Abbrechen").forEach { word ->
            val (host, desk) = opened()
            host.duringGeneration = {
                assertTrue(desk.jobRunning && desk.controlsRunningJob(word))
                assertTrue(word, desk.intercept(word))
            }
            assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
            assertEquals(word, 1, host.cancels)
            assertTrue(word, host.downloads.isEmpty() && host.adopted.isEmpty())
            assertEquals("Gestoppt. Seite 2 wurde nicht fertig; es wurde nichts gespeichert.", host.lines("SAY:").last())
            assertEquals("desk-summary:Zusammenfassen gestoppt:FAILED", host.steps.last())
            assertFalse(host.steps.any { it.startsWith("desk-save") })
            assertEquals(word, host.lines("USER:").last())
            assertFalse(desk.jobRunning)
            // Afterwards the same order runs again from the start.
            assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
            assertEquals(listOf(wordName), host.downloads.keys.toList())
        }
    }

    @Test fun theStopButtonEndsTheJobTheSameWayAndStoppIsOnlyTakenWhileAJobRuns() {
        val (host, desk) = opened()
        // The Activity's STOP cancels the lane; the desk only learns it from the job's end.
        host.duringGeneration = { host.cancelJob() }
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertTrue(host.downloads.isEmpty())
        assertEquals("Gestoppt. Seite 2 wurde nicht fertig; es wurde nichts gespeichert.", host.lines("SAY:").last())
        // No job: the word belongs to whoever else wants it.
        assertFalse(desk.intercept("Stopp"))
        assertFalse(desk.stop("Stopp"))
        assertFalse(desk.controlsRunningJob("Stopp"))
        assertFalse(desk.handle("Stopp"))
        assertEquals("only the button's own stop", 1, host.cancels)
    }

    @Test fun stoppEndsARunningSearchAndNothingIsOpened() {
        val (host, desk) = desk { put("Argon4.pdf", pages = ARGON) }
        host.duringScan = { assertTrue(desk.intercept("Halt")) }
        assertTrue(desk.handle("Öffne das PDF Argon4"))
        assertEquals("Gestoppt. Die Suche ist abgebrochen; ich habe nichts geöffnet.", host.lines("SAY:").single())
        assertEquals("desk-scan:Suche gestoppt:FAILED", host.steps.last())
        assertTrue(host.shown.isEmpty())
        assertNull(host.openDocument())
        // „Stopp die Musik“ is not the stop word, and a file correction does not touch a running search.
        val (other, otherDesk) = desk { put("Argon4.pdf", pages = ARGON) }
        other.duringScan = {
            assertFalse(otherDesk.intercept("Stopp die Musik"))
            assertFalse(otherDesk.intercept("Ich meinte Seite 3"))
        }
        assertTrue(otherDesk.handle("Öffne das PDF Argon4"))
        assertEquals("Argon4.pdf", other.shown.single().name)
    }

    @Test fun aStopThatArrivesAfterTheFileWasWrittenReportsTheFileThatExists() {
        val (host, desk) = opened()
        host.beforeSave = { host.cancelJob() }
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals(listOf(wordName), host.downloads.keys.toList())
        val said = host.lines("SAY:").last()
        assertTrue(said.contains("Gespeichert als Word-Datei: „$wordName“") &&
            said.endsWith("Diese Datei war schon gespeichert, bevor der Abbruch ankam."))
        assertFalse(said.contains("es wurde nichts gespeichert"))
    }

    @Test fun aChatSwitchDuringTheJobReportsNothingIntoTheOtherChat() {
        val (host, desk) = opened()
        val before = host.spoken.size
        host.duringGeneration = { host.sessionId = "chat-2" }
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals(before, host.spoken.size)
        assertEquals("DOKUMENTE · Gespräch gewechselt · Ergebnis hier nicht gemeldet · gespeichert wurde $wordName", host.lines("ACTIVITY:").last())
        assertFalse(desk.jobRunning)
    }

    // ── A page summary that is shown and not saved – a step of a sequence (19.11.2026) ──────────────────────

    @Test fun insideASequenceAPageIsReadSummarisedAndShownAndNothingIsSaved() {
        val (host, desk) = opened { sequence = true }
        assertTrue(desk.handle("analysiere Seite zwei und fasse sie zusammen"))
        // The model saw exactly that page – the same task as for the order with a save.
        val prompt = host.prompts.single()
        assertTrue(prompt.contains("Seite 2 des Dokuments „Argon4“") && prompt.contains(ARGON[1]))
        assertFalse(prompt.contains(ARGON[0]) || prompt.contains(ARGON[2]))
        assertEquals("Seite 2 von „Argon4.pdf“ – Zusammenfassung:\n\n$SUMMARY_2\n\nGespeichert habe ich nichts.", host.lines("SAY:").last())
        // Read and summarised – and no step builds, saves or checks a file.
        assertEquals(listOf("desk-page:Seite 2 lesen:ACTIVE:TOOL", "desk-page:Seite 2 gelesen · ${ARGON[1].length} Zeichen:DONE",
            "desk-summary:Zusammenfassen:ACTIVE:THINK", "desk-summary:Zusammenfassen · ${SUMMARY_2.length} Zeichen:ACTIVE",
            "desk-summary:Zusammengefasst · ${SUMMARY_2.length} Zeichen:DONE"), host.steps.dropWhile { !it.startsWith("desk-page") })
        assertTrue(host.downloads.isEmpty() && host.adopted.isEmpty())
        assertEquals("DOKUMENTE · Seite 2 zusammengefasst · im Gespräch gezeigt · nichts gespeichert", host.lines("ACTIVITY:").last())
        // The typed result: this page, really read, with the same deliverable a saved summary carries.
        val result = host.presented.single()
        assertEquals(OwnerDeliverable.Kind.PAGE_SUMMARY, result.kind)
        assertEquals("Zusammenfassung Argon4 Seite 2", result.title)
        assertEquals("Quelle: „Argon4.pdf“, Seite 2 von 4 · zusammengefasst vom Sprachmodell auf dem Gerät am 18.11.2026 · vor der " +
            "Verwendung prüfen.", result.note)
        assertEquals(OwnerLaneOutcome.Done(OwnerLaneResult.PageSummarised(result, "Argon4.pdf", 2, ARGON[1].length, ARGON[1].length)),
            host.outcomes.last())
        // The other forms of the same order.
        for ((order, page) in listOf("Fass Seite 3 zusammen" to 3, "erkläre mir Seite 3" to 3, "Analysiere die dritte Seite" to 3,
            "fasse bitte Seite drei kurz zusammen" to 3, "Fasst Seite 3 zusammen" to 3)) {
            assertTrue(order, desk.handle(order))
            assertEquals(order, page, (host.outcomes.last() as OwnerLaneOutcome.Done).result.let { (it as OwnerLaneResult.PageSummarised).page })
        }
        assertTrue(host.downloads.isEmpty())
    }

    @Test fun outsideASequenceTheSameWordsAreNotTheDesksOrder() {
        val (host, desk) = opened()
        val before = host.log.size
        for (order in listOf("Fass Seite 2 zusammen", "analysiere Seite zwei und fasse sie zusammen", "erkläre mir Seite 3")) {
            host.foreignTurn()
            assertFalse(order, desk.handle(order))
        }
        assertEquals(before, host.log.size)
        assertTrue(host.prompts.isEmpty() && host.presented.isEmpty())
        // Inside a sequence everything that is more than this order still is not it.
        host.sequence = true
        for (order in listOf("Fass Seite 2 nicht zusammen", "Fass die Seiten 2 bis 4 zusammen", "Fass Seite 2 in einem Wort zusammen",
            "Was steht auf Seite 2?", "Schau auf Seite 2", "Fass Seite 2 zusammen und lösche die Datei", "Fass es zusammen")) {
            host.foreignTurn()
            assertFalse(order, desk.handle(order))
        }
        // With a save order it is the page order it always was.
        assertTrue(desk.handle("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals(listOf(wordName), host.downloads.keys.toList())
    }

    @Test fun aSummaryThatCannotBeMadeSaysWhyAndIsNeverReportedAsDone() {
        val (host, desk) = opened { sequence = true }
        assertTrue(desk.handle("Fass Seite 4 zusammen"))
        assertEquals("Seite 4 von „Argon4.pdf“ hat keinen auslesbaren Text – zum Beispiel ein Scan. Ich habe nichts zusammengefasst.",
            host.lines("SAY:").last())
        assertTrue(host.outcomes.last() is OwnerLaneOutcome.Failed)
        assertTrue(desk.handle("Fass Seite 9 zusammen"))
        assertEquals("„Argon4.pdf“ hat nur 4 Seiten – eine Seite 9 gibt es nicht. Ich habe nichts zusammengefasst.", host.lines("REPLY:").last())
        assertTrue(host.prompts.isEmpty() && host.presented.isEmpty())
        // No model, a model that does not finish, a model that fails.
        val (unloaded, unloadedDesk) = opened { sequence = true; modelLoaded = false }
        assertTrue(unloadedDesk.handle("Fass Seite 2 zusammen"))
        assertEquals("Seite 2 von „Argon4.pdf“ habe ich gelesen (${ARGON[1].length} Zeichen). Für die Zusammenfassung brauche ich aber das " +
            "Sprachmodell, und im Moment ist keines geladen. Ich habe nichts zusammengefasst.", unloaded.lines("SAY:").last())
        val (cut, cutDesk) = opened { sequence = true }
        cut.model = { OwnerWorkGeneration(SUMMARY_2.take(50), null, false) }
        assertTrue(cutDesk.handle("Fass Seite 2 zusammen"))
        assertEquals("Das Sprachmodell hat für Seite 2 keine brauchbare Zusammenfassung geliefert. Ich habe nichts zusammengefasst.",
            cut.lines("SAY:").last())
        for (h in listOf(unloaded, cut)) {
            assertTrue(h.presented.isEmpty() && h.downloads.isEmpty())
            assertTrue(h.outcomes.last() is OwnerLaneOutcome.Failed)
        }
        // No PDF open at all.
        val (none, noneDesk) = desk { sequence = true }
        assertTrue(noneDesk.handle("Fass Seite 2 zusammen"))
        assertEquals("Im Moment ist kein PDF geöffnet. Ich habe nichts zusammengefasst.", none.lines("REPLY:").single())
        // The owner's stop while the model writes: nothing is shown.
        val (stopped, stoppedDesk) = opened { sequence = true }
        stopped.duringGeneration = { assertTrue(stoppedDesk.stop("Stopp")) }
        assertTrue(stoppedDesk.handle("Fass Seite 2 zusammen"))
        assertEquals("Gestoppt. Seite 2 wurde nicht fertig zusammengefasst; gespeichert habe ich nichts.", stopped.lines("SAY:").last())
        assertEquals(OwnerLaneOutcome.Stopped, stopped.outcomes.last())
        assertTrue(stopped.presented.isEmpty())
    }

    @Test fun theOfferToKeepTheSummaryIsMadeOnlyWhenTheHostReallyKeepsItAndTheCloudIsNamed() {
        val (kept, keptDesk) = opened { sequence = true; presentTaken = true }
        assertTrue(keptDesk.handle("Fass Seite 2 zusammen"))
        assertTrue(kept.lines("SAY:").last().endsWith("Gespeichert habe ich nichts. Wenn du sie behalten willst, sag zum Beispiel „Speichere das als Word“."))
        val (viaCloud, cloudDesk) = opened { sequence = true; cloud = true }
        assertTrue(cloudDesk.handle("Fass Seite 2 zusammen"))
        assertTrue(viaCloud.lines("SAY:").last().endsWith("Gespeichert habe ich nichts. Zusammengefasst habe ich das über die Cloud, nicht mit dem Modell auf dem Gerät."))
        assertTrue(viaCloud.presented.single().note.orEmpty().contains("zusammengefasst vom Cloud-Gehirn"))
    }

    // ── What stays with the other lanes ─────────────────────────────────────────────────────────────────────

    @Test fun requestsWithoutANameAndOtherOrdersAreNotTaken() {
        val (host, desk) = opened()
        val before = host.log.size
        listOf("Öffne eine PDF", "Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.", "Suche mir eine PDF",
            "Öffne das PDF Argon4 im Internet", "Öffne das PDF Argon4 nicht", "Öffne das PDF „Argon4“", "Fass Seite 2 zusammen",
            "Was steht auf Seite 2?", "Schau auf Seite drei", "Speichere das als PDF", "Fass mir die Nachrichten von heute zusammen",
            "Öffne WhatsApp", "Ja", "die zweite", "Stopp", "Wie spät ist es?").forEach {
            host.foreignTurn()
            assertFalse(it, desk.intercept(it))
            assertFalse(it, desk.handle(it))
        }
        assertEquals(before, host.log.size)
    }
}
