package at.heinz.deviceai.deepclean

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
import at.heinz.deviceai.briefing.ContinuumSystemFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Whole owner turns through the lane on a temporary phone: which mode may reach a delete call, what is removed,
 * what is offered, what Android's dialogs decide, and which numbers are said.
 */
class DeepCleanLaneTest {
    /** A phone with own cache (GREEN), own memory (RED), personal photos (RED) and YELLOW downloads. */
    private fun stocked(host: FakeDeepCleanHost) {
        val p = host.phone
        p.file(p.cache, "WebView/HTTP Cache/a.bin", 4_000)
        p.file(p.cache, "previews/b.png", 6_000)
        p.file(p.cache, "litertlm/kernel.bin", 9_000)
        p.file(p.files, "journal/journal-1234567890.tmp", 1_500)
        p.file(p.files, "memory/facts.json", content = "Heinz mag Kaffee")
        p.file(p.files, "models/gemma.litertlm", 20_000)
        p.file(p.shared, "DCIM/Camera/IMG_0001.jpg", 300_000)
        p.file(p.shared, "Documents/Steuer.pdf", 50_000)
        p.file(p.shared, "Download/app-alt.apk", 70_000)
        p.file(p.shared, "Download/bericht.pdf", content = "X".repeat(300_000), modified = p.old - 86_400_000L)
        p.file(p.shared, "Download/bericht (1).pdf", content = "X".repeat(300_000))
    }

    private val greenBytes = 4_000L + 6_000L + 1_500L

    @Test fun analysisOnlyNeverReachesADeleteCall() {
        for (sentence in listOf("Schau, was unnötig Speicher verbraucht", "Schau, warum mein Speicher so voll ist",
                "Bereinige mein Handy, aber lösch nichts", "Bereinige mein Handy, nur überprüfen", "Mach eine Tiefenanalyse",
                "Mach eine Tiefenbereinigung, aber nichts löschen", "Optimier mein Handy", "Kannst du mein Handy wieder schneller machen?",
                "Lösch die alten APK-Dateien, aber nur zeigen", "Bereinige mein Handy, aber pass auf die Sachen von Anna auf")) {
            val host = FakeDeepCleanHost()
            stocked(host)
            val lane = DeepCleanDirector(host)
            assertTrue(sentence, host.say(lane, sentence))
            // Every follow-up the owner could say afterwards stays read-only as well.
            for (follow in listOf("alle", "die 1", "ja", "ja, die drei")) host.say(lane, follow)
            assertEquals(sentence, 0, host.counting.deleteCalls)
            assertTrue(sentence, host.trashCalls.isEmpty())
            assertTrue(sentence, File(host.phone.cache, "previews/b.png").exists())
        }
    }

    @Test fun theSafeCleanRemovesExactlyTheGreenFilesAndMeasures() {
        val host = FakeDeepCleanHost()
        stocked(host)
        val lane = DeepCleanDirector(host)
        host.say(lane, "Continuum, bereinige mein Handy")
        assertEquals(setOf("OWN_CACHE:WebView/HTTP Cache/a.bin", "OWN_CACHE:previews/b.png", "OWN_FILES:journal/journal-1234567890.tmp"),
            host.counting.deleted.toSet())
        assertTrue(host.trashCalls.isEmpty())
        // Everything else is still there, byte for byte.
        val p = host.phone
        for (f in listOf(File(p.cache, "litertlm/kernel.bin"), File(p.files, "memory/facts.json"), File(p.files, "models/gemma.litertlm"),
                File(p.shared, "DCIM/Camera/IMG_0001.jpg"), File(p.shared, "Documents/Steuer.pdf"), File(p.shared, "Download/app-alt.apk"),
                File(p.shared, "Download/bericht (1).pdf"))) assertTrue(f.path, f.exists())
        assertEquals("Heinz mag Kaffee", File(p.files, "memory/facts.json").readText())
        val text = host.answers.last()
        assertTrue(text, text.startsWith("Alles klar, Heinz."))
        assertTrue(text, text.contains("Entfernt habe ich 3 Dateien, zusammen " + DeepCleanText.bytes(greenBytes) + " (Summe der Dateigrößen)"))
        assertTrue(text, text.contains("Deine persönlichen Dateien habe ich nicht angefasst"))
        assertTrue(text, text.contains("Gegenprobe"))
        assertTrue(text, text.contains("alle unverändert"))
        val result = (host.outcomes.last() as OwnerLaneOutcome.Done).result as OwnerLaneResult.Storage
        assertEquals("SAFE_CLEAN", result.mode)
        assertEquals(3, result.removedFiles)
        assertEquals(greenBytes, result.removedBytes)
        assertEquals(host.freeBytes, result.freeBefore)
        // The stage carries the same measured figures and no file name.
        val stage = lane.stage.snapshot("chat-1")!!
        assertEquals("DONE", stage.getString("phase"))
        assertEquals(greenBytes, stage.getJSONObject("removed").getLong("bytes"))
        assertEquals("sum_of_removed_file_sizes", stage.getJSONObject("removed").getString("source"))
        assertFalse(stage.toString().contains("facts.json") || stage.toString().contains("IMG_0001"))
        assertEquals(null, lane.stage.snapshot("other-chat"))
    }

    @Test fun keepRestrictionsAndLearnedProtectionsAreEnforced() {
        val host = FakeDeepCleanHost()
        stocked(host)
        host.phone.file(host.phone.cache, "thumbs/foto.jpg", 2_000)
        val lane = DeepCleanDirector(host)
        host.say(lane, "Bereinige mein Handy, Fotos behalten")
        assertTrue(File(host.phone.cache, "thumbs/foto.jpg").exists())
        // „Fotos behalten“ narrows even the own cache: picture files stay, other cache files go.
        assertTrue(File(host.phone.cache, "previews/b.png").exists())
        assertFalse(File(host.phone.cache, "WebView/HTTP Cache/a.bin").exists())
    }

    @Test fun theDeepCleanOffersYellowAndTrashesOnlyWhatWasChosenAndConfirmed() {
        val host = FakeDeepCleanHost()
        stocked(host)
        val lane = DeepCleanDirector(host)
        host.say(lane, "Mach eine Tiefenbereinigung")
        val offered = lane.offered()
        assertTrue(offered.map { it.relative }.toString(), offered.map { it.relative }.toSet() ==
            setOf("Download/app-alt.apk", "Download/bericht (1).pdf"))
        assertTrue(host.shownAnswers.last(), host.shownAnswers.last().contains("1. /sdcard/"))
        assertTrue(host.shownAnswers.last().contains("im Android-Papierkorb wiederherstellbar"))
        // Only GREEN went directly; nothing YELLOW was touched yet.
        assertEquals(3, host.counting.deleteCalls)
        assertTrue(host.trashCalls.isEmpty())
        val duplicate = offered.indexOfFirst { it.relative == "Download/bericht (1).pdf" } + 1
        host.say(lane, "die $duplicate")
        assertTrue(host.answers.last(), host.answers.last().startsWith("Probelauf"))
        assertTrue(host.trashCalls.isEmpty())
        host.say(lane, "ja")
        assertEquals(listOf(listOf("SHARED:Download/bericht (1).pdf")), host.trashCalls)
        assertFalse(File(host.phone.shared, "Download/bericht (1).pdf").exists())
        assertTrue(File(host.phone.shared, "Download/bericht.pdf").exists())
        assertTrue(File(host.phone.shared, "Download/app-alt.apk").exists())
        assertTrue(host.answers.last(), host.answers.last().contains("In den Android-Papierkorb verschoben: eine Datei, " + DeepCleanText.bytes(300_000)))
        // „Stell wieder her“ brings it back through Android's dialog.
        host.say(lane, "Stell die gelöschten Dateien wieder her")
        assertTrue(File(host.phone.shared, "Download/bericht (1).pdf").exists())
        assertTrue(host.answers.last(), host.answers.last().contains("wieder an ihrem Platz"))
        // Still exactly three direct deletes in the whole test.
        assertEquals(3, host.counting.deleteCalls)
    }

    @Test fun androidsRefusalIsReportedAndNothingIsClaimed() {
        val host = FakeDeepCleanHost()
        stocked(host)
        host.trashGranted = false
        host.clearCacheOffered = true
        val lane = DeepCleanDirector(host)
        host.say(lane, "Mach eine Tiefenbereinigung")
        host.say(lane, "alle")
        host.say(lane, "ja")
        assertEquals(1, host.cacheCalls)
        val text = host.answers.last()
        assertTrue(text, text.contains("Android hat nichts in den Papierkorb verschoben"))
        assertTrue(text, text.contains("Gelöscht ist davon nichts"))
        assertTrue(text, text.contains("Die Zwischenspeicher der Apps hat Android nicht geleert"))
        assertFalse(text, text.contains("sind geleert"))
        assertTrue(File(host.phone.shared, "Download/bericht (1).pdf").exists())
        val result = (host.outcomes.last() as OwnerLaneOutcome.Done).result as OwnerLaneResult.Storage
        assertEquals(0, result.removedFiles)
        assertTrue(lane.log().any { it.contains("von Android abgelehnt") })
    }

    @Test fun aFileChangedAfterThePreviewIsNotTouched() {
        val host = FakeDeepCleanHost()
        stocked(host)
        val lane = DeepCleanDirector(host)
        host.say(lane, "Lösch die doppelten Dateien")
        assertEquals(1, lane.offered().size)
        // The owner edits the copy in the meantime – the content is no longer what the hash said.
        host.phone.file(host.phone.shared, "Download/bericht (1).pdf", content = "Y".repeat(300_000))
        host.say(lane, "alle")
        host.say(lane, "ja")
        assertTrue(host.trashCalls.isEmpty())
        assertTrue(File(host.phone.shared, "Download/bericht (1).pdf").exists())
        assertTrue(host.answers.last(), host.answers.last().contains("verändert"))
    }

    @Test fun theOwnersStopEndsTheRunBeforeTheNextFile() {
        val host = FakeDeepCleanHost()
        stocked(host)
        val lane = DeepCleanDirector(host)
        host.files()
        host.counting.afterDelete = { lane.intercept("Stopp") }
        host.say(lane, "Bereinige mein Handy")
        assertEquals(1, host.counting.deleteCalls)
        assertTrue(host.answers.any { it.startsWith("Ich stoppe sofort") })
        assertTrue(host.answers.last(), host.answers.last().contains("Gestoppt"))
        assertTrue(host.answers.last(), host.answers.last().contains("Entfernt habe ich eine Datei"))
        assertTrue(host.outcomes.last() is OwnerLaneOutcome.Stopped)
    }

    @Test fun aRestartNeitherDeletesTwiceNorReportsTwice() {
        val host = FakeDeepCleanHost()
        stocked(host)
        val lane = DeepCleanDirector(host)
        host.files()
        // The process dies right after the second removal, before its result is written.
        var deletes = 0
        host.counting.afterDelete = { if (++deletes == 2) throw IllegalStateException("Prozess beendet") }
        host.say(lane, "Bereinige mein Handy")
        assertEquals(2, host.counting.deleteCalls)
        // A new process: same store, same files.
        host.counting.afterDelete = {}
        val restarted = DeepCleanDirector(host)
        host.say(restarted, "Schau, was unnötig Speicher verbraucht")
        val first = host.answers.last()
        assertTrue(first, first.startsWith("Die letzte Bereinigung wurde unterbrochen"))
        assertTrue(first, first.contains("Bis dahin entfernt: eine Datei"))
        assertTrue(first, first.contains("Eine Datei war danach weg"))
        assertEquals(2, host.counting.deleteCalls)
        host.say(restarted, "Schau, was unnötig Speicher verbraucht")
        assertFalse(host.answers.last().contains("unterbrochen"))
        val third = DeepCleanDirector(host)
        host.say(third, "Schau, was unnötig Speicher verbraucht")
        assertFalse(host.answers.last().contains("unterbrochen"))
        assertEquals(2, host.counting.deleteCalls)
    }

    @Test fun missingFileAccessIsSaidAndSharedStorageIsNotScanned() {
        val host = FakeDeepCleanHost(sharedAccess = DeepCleanAccess.Shared.MISSING)
        stocked(host)
        val lane = DeepCleanDirector(host)
        host.say(lane, "Mach eine Tiefenbereinigung")
        val text = host.answers.last()
        assertTrue(text, text.contains("„Zugriff auf alle Dateien“ ist für Continuum nicht eingeschaltet"))
        assertTrue(lane.offered().isEmpty())
        assertEquals(3, host.counting.deleteCalls)
        assertTrue(File(host.phone.shared, "Download/app-alt.apk").exists())
        assertEquals("MISSING", lane.stage.snapshot("chat-1")!!.getString("sharedAccess"))
    }

    @Test fun aRunningDownloadIsNeverOffered() {
        val host = FakeDeepCleanHost()
        stocked(host)
        host.active = setOf("SHARED:Download/app-alt.apk")
        val lane = DeepCleanDirector(host)
        host.say(lane, "Lösch die alten APK-Dateien")
        assertTrue(lane.offered().isEmpty())
    }

    @Test fun keepingPhotosDuringTheSelectionTakesThemOutAndIsLearned() {
        val host = FakeDeepCleanHost()
        stocked(host)
        host.phone.file(host.phone.shared, "DCIM/Camera/IMG_7.jpg", content = "Z".repeat(400_000))
        host.phone.file(host.phone.shared, "Download/IMG_7.jpg", content = "Z".repeat(400_000))
        val lane = DeepCleanDirector(host)
        host.say(lane, "Mach eine Tiefenbereinigung")
        assertTrue(lane.offered().any { it.relative == "Download/IMG_7.jpg" })
        host.say(lane, "Fotos behalten")
        assertTrue(lane.offered().none { it.relative == "Download/IMG_7.jpg" })
        host.say(lane, "keine")
        // Refused twice → not proposed again.
        host.say(lane, "Mach eine Tiefenbereinigung")
        host.say(lane, "Fotos behalten")
        host.say(lane, "keine")
        host.say(lane, "Mach eine Tiefenbereinigung")
        assertTrue(lane.offered().none { it.relative.endsWith(".jpg") })
        assertTrue(host.answers.last(), host.answers.last().contains("schlage ich nicht mehr vor"))
        host.say(lane, "Schlag mir wieder alles vor")
        host.say(lane, "Mach eine Tiefenbereinigung")
        assertTrue(lane.offered().any { it.relative == "Download/IMG_7.jpg" })
    }

    @Test fun theReportUsesOnlyMeasuredNumbersAndSaysWhichIsWhich() {
        val host = FakeDeepCleanHost()
        stocked(host)
        val lane = DeepCleanDirector(host)
        host.say(lane, "Wie viel Platz habe ich wieder?")
        assertTrue(host.answers.last(), host.answers.last().startsWith("Ich habe noch nichts bereinigt."))
        host.say(lane, "Bereinige mein Handy")
        host.freeBytes += 11_000
        host.say(lane, "Wie viel Platz habe ich jetzt wieder?")
        val text = host.answers.last()
        assertTrue(text, text.contains("entfernt habe ich 3 Dateien, zusammen ${DeepCleanText.bytes(greenBytes)} (Summe der Dateigrößen)"))
        assertTrue(text, text.contains("Android maß davor"))
        assertTrue(text, text.contains(DeepCleanText.DIFFERENCE_NOTE))
        assertTrue(text, text.contains("Jetzt gerade sind ${DeepCleanText.bytes(host.freeBytes)} frei"))
    }

    @Test fun optimisationOffersOnlyTheAppsOwnLoadAndDeletesNothing() {
        val host = FakeDeepCleanHost()
        stocked(host)
        host.thermal = 3
        host.facts = ContinuumSystemFacts(ContinuumSystemFacts.Brain.Local(ContinuumSystemFacts.Brain.State.READY, "Gemma"), null, null,
            false, null, null, null, true, null, null, 50, false, 3, host.freeBytes, host.totalBytes)
        val lane = DeepCleanDirector(host)
        host.say(lane, "Optimier mein Handy")
        val text = host.answers.last()
        assertTrue(text, text.contains("gelöscht wird dabei nichts"))
        assertTrue(text, text.contains("Andere Apps beende ich nicht"))
        assertTrue(text, text.endsWith("Soll ich?"))
        host.say(lane, "ja")
        assertEquals(1, host.unloadCalls)
        assertEquals(0, host.counting.deleteCalls)
    }

    @Test fun clarificationAndDeclineDoNothing() {
        val host = FakeDeepCleanHost()
        stocked(host)
        val lane = DeepCleanDirector(host)
        host.say(lane, "Lösch alles")
        assertTrue(host.answers.last().startsWith("Alles auf einmal lösche ich nicht"))
        host.say(lane, "Bereinige mein Handy nicht")
        assertEquals(DeepCleanText.DECLINE, host.answers.last())
        assertEquals(0, host.counting.deleteCalls)
    }

    @Test fun theForecastNeedsThreeSamplesOverThreeDays() {
        val store = MemoryDeepCleanStore()
        val forecast = DeepCleanForecast(store)
        val day = 24 * 3_600_000L
        forecast.add(DeepCleanSample(0, 10_000_000_000, 100_000_000_000))
        forecast.add(DeepCleanSample(day, 9_000_000_000, 100_000_000_000))
        assertTrue(forecast.outlook() is DeepCleanForecast.Outlook.TooFew)
        forecast.add(DeepCleanSample(2 * day, 8_000_000_000, 100_000_000_000))
        assertTrue(forecast.outlook() is DeepCleanForecast.Outlook.TooFew)
        forecast.add(DeepCleanSample(3 * day, 7_000_000_000, 100_000_000_000))
        val full = forecast.outlook() as DeepCleanForecast.Outlook.FullIn
        assertEquals(7.0, full.days, 0.01)
        assertTrue(DeepCleanText.forecastLine(full).contains("Schätzung"))
        assertTrue(DeepCleanText.forecastLine(DeepCleanForecast.Outlook.TooFew(1, 0.0)).contains("zu wenig Messwerte"))
        // Two samples within an hour count once.
        assertFalse(forecast.add(DeepCleanSample(3 * day + 60_000, 7_000_000_000, 100_000_000_000)))
    }

    @Test fun theAuditNoticesAChangedProtectedFile() {
        val phone = TempPhone()
        val photo = phone.file(phone.shared, "DCIM/Camera/IMG_1.jpg", 10_000)
        val fs = phone.fs()
        val scan = DeepCleanScanner.scan(fs, setOf(DeepCleanRoot.SHARED), phone.now)
        val prints = DeepCleanAudit.fingerprint(fs, DeepCleanAudit.sample(scan.findings))
        assertTrue(DeepCleanAudit.verify(fs, prints).clean)
        photo.writeBytes(ByteArray(10_000) { 7 })
        photo.setLastModified(phone.old)
        val after = DeepCleanAudit.verify(fs, prints)
        assertFalse(after.clean)
        assertEquals(listOf("DCIM/Camera/IMG_1.jpg"), after.changed)
    }

    @Test fun theStageSnapshotHasTheDocumentedShape() {
        val host = FakeDeepCleanHost()
        stocked(host)
        val lane = DeepCleanDirector(host)
        assertEquals(null, lane.stage.snapshot("chat-1"))
        host.say(lane, "Mach eine Tiefenbereinigung")
        val s = lane.stage.snapshot("chat-1")!!
        assertEquals(DeepCleanStage.SCHEMA, s.getString("schema"))
        assertEquals("DEEP_CLEAN", s.getString("mode"))
        assertEquals("CHOICE", s.getString("phase"))
        for (key in listOf("runId", "progress", "categories", "levels", "before", "after", "removed", "choice", "sharedAccess", "updatedAtMs"))
            assertTrue(key, s.has(key))
        assertEquals(2, s.getJSONObject("choice").getInt("count"))
        assertEquals(370_000L, s.getJSONObject("choice").getLong("bytes"))
        val categories = s.getJSONArray("categories")
        assertNotNull((0 until categories.length()).map { categories.getJSONObject(it) }.firstOrNull { it.getString("key") == "PHOTO" })
        assertEquals(host.freeBytes, s.getJSONObject("before").getLong("freeBytes"))
    }
}
