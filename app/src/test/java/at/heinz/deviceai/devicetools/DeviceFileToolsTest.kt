package at.heinz.deviceai.devicetools

import at.heinz.deviceai.cognitive.ChainExtract
import at.heinz.deviceai.cognitive.OwnerChainStepCatalog
import at.heinz.deviceai.ui.Command03FilesProjection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

/** The in-process file backends on a real temporary directory tree: shapes, ranking, bounds, stops and refusals. */
class DeviceFileToolsTest {
    private val storage = TestStorage()
    private val tools = DeviceFileTools(storage, { TestStorage.ZONE })

    @After fun clean() = storage.delete()

    private fun tree() {
        storage.file("Download/continuum-0.3.69.apk", ByteArray(3_000), "2026-11-17T20:00")
        storage.file("Download/continuum-0.3.70.apk", ByteArray(5_000), "2026-11-18T21:00")
        storage.file("Download/Experimente.pdf", ByteArray(2_400_000), "2026-11-17T14:02")
        storage.file("Download/Neuro-Experimente 2026.pdf", ByteArray(1_500), "2026-11-10T09:00")
        storage.text("Download/experimente_notizen.txt", "Argon bei 4 bar\nzweite Zeile mit Neon", "2026-11-18T08:00")
        storage.file("Download/alt/tief/noch tiefer/alte.apk", ByteArray(10), "2026-11-19T10:00")
        storage.file("Documents/Labor/Argon4.pdf", ByteArray(700), "2026-11-12T10:30")
        storage.text("Documents/Notizen/todo.md", "- Argon bestellen\n- Müller anrufen", "2026-11-14T22:00")
        storage.file("DCIM/Camera/IMG_1.jpg", ByteArray(4_200), "2026-11-18T10:15")
        storage.file("Android/data/com.other/secret.pdf", ByteArray(5), "2026-11-19T11:00")
        storage.file(".hidden/geheim.pdf", ByteArray(5), "2026-11-19T11:00")
    }

    @Test fun aListingHasTheShellShapeTheChainAndTheFilesStageParse() {
        tree()
        val listing = tools.listFiles("/sdcard/Download")
        val lines = listing.lines()
        assertTrue(lines.first(), lines.first().startsWith("total "))
        assertTrue(listing, lines.contains("-rw-rw---- 1 - - 2.3M 2026-11-17 14:02 Experimente.pdf"))
        assertTrue(listing, lines.contains("-rw-rw---- 1 - - 4.9K 2026-11-18 21:00 continuum-0.3.70.apk"))
        assertTrue(listing, lines.any { it.startsWith("drwxrwx--- 2 - - ") && it.endsWith(" alt") })
        // The chain's own extractors read it: newest PDF, and one named entry.
        val newest = ChainExtract.NewestEntry(setOf("pdf")).extract(listing) { _, _ -> null } as ChainExtract.Result.Values
        assertEquals("Experimente.pdf", newest.values["name"])
        assertEquals("2.3M", newest.values["size"])
        assertEquals("2026-11-17", newest.values["date"])
        assertEquals("2", newest.values["count"])
        val entry = ChainExtract.ListingEntry("find", "path").extract(listing) { _, _ -> "/sdcard/Download/continuum-0.3.70.apk" } as ChainExtract.Result.Values
        assertEquals("4.9K", entry.values["size"])
        // The files stage accepts it as a folder record.
        val stage = Command03FilesProjection()
        assertTrue(stage.accept("session", "list_files", mapOf("path" to "/sdcard/Download"), listing))
        assertEquals(6, stage.snapshot("session")!!.getInt("total"))
        // Android's long form and a relative path name the same folder; a single file is listed like `ls` does.
        assertEquals(listing, tools.listFiles("/storage/emulated/0/Download/"))
        assertEquals(listing, tools.listFiles("Download"))
        assertEquals("-rw-rw---- 1 - - 700 2026-11-12 10:30 Argon4.pdf", tools.listFiles("/sdcard/Documents/Labor/Argon4.pdf"))
    }

    @Test fun aListingSaysWhatIsMissingEmptyOrOutsideAndNeverInventsEntries() {
        tree()
        storage.folder("Download/leer")
        assertTrue(tools.listFiles("/sdcard/Gibtsnicht").startsWith("Nicht gefunden: /sdcard/Gibtsnicht"))
        assertTrue(tools.listFiles("/sdcard/Download/leer").contains("ist leer"))
        assertEquals(DeviceFileTools.OUTSIDE, tools.listFiles("/sdcard/Android/data"))
        assertEquals(DeviceFileTools.OUTSIDE, tools.listFiles("/sdcard/Download/../../etc"))
        assertEquals(DeviceFileTools.OUTSIDE, tools.listFiles("/data/data/at.heinz"))
    }

    @Test fun aHugeFolderIsCutToItsNewestEntriesWithAnHonestNotice() {
        repeat(450) { index -> storage.file("Download/datei-%03d.txt".format(index), ByteArray(1), "2026-11-01T10:00") }
        storage.file("Download/neu.pdf", ByteArray(1), "2026-11-19T10:00")
        val listing = tools.listFiles("/sdcard/Download")
        val first = listing.lines().first()
        assertTrue(first, Regex("total \\S+ · Hinweis: nur die \\d+ zuletzt geänderten von 451 Einträgen").matches(first))
        assertTrue(listing.length <= 8_000)
        // The newest file is never the one that was cut off.
        assertEquals("neu.pdf", (ChainExtract.NewestEntry(setOf("pdf")).extract(listing) { _, _ -> null } as ChainExtract.Result.Values).values["name"])
    }

    @Test fun findFilesReturnsPathsRankedAndMatchesSpellingVariants() {
        tree()
        assertEquals(listOf("/sdcard/Download/Experimente.pdf", "/sdcard/Download/experimente_notizen.txt",
            "/sdcard/Download/Neuro-Experimente 2026.pdf"), tools.findFiles("experimente", emptyList(), 100).lines())
        // Number word, hyphen and case: the shell's `-iname '*argon vier*'` found nothing here.
        assertEquals("/sdcard/Documents/Labor/Argon4.pdf", tools.findFiles("ARGON vier", emptyList(), 100))
        // Roots narrow the search; the limit keeps the best.
        assertEquals("/sdcard/Documents/Notizen/todo.md", tools.findFiles("todo", listOf("/sdcard/Documents"), 5))
        assertEquals(1, tools.findFiles("experimente", emptyList(), 1).lines().size)
        // Other apps' private folder and hidden folders are never walked.
        assertTrue(tools.findFiles("secret", emptyList(), 10).startsWith("Keine Datei gefunden"))
        assertTrue(tools.findFiles("geheim", emptyList(), 10).startsWith("Keine Datei gefunden"))
        // The chain takes one path, asks for several and reports „kein Treffer“ for the sentence of an empty result.
        val contract = OwnerChainStepCatalog.contract("find_files")!!.gives.single()
        assertEquals(mapOf("path" to "/sdcard/Documents/Labor/Argon4.pdf"),
            (contract.extract(tools.findFiles("argon4", emptyList(), 20)) { _, _ -> null } as ChainExtract.Result.Values).values)
        assertTrue(contract.extract(tools.findFiles("experimente", emptyList(), 20)) { _, _ -> null } is ChainExtract.Result.Choice)
        assertTrue(contract.extract(tools.findFiles("qqqq", emptyList(), 20)) { _, _ -> null } is ChainExtract.Result.Missing)
        // The files stage shows a real search record.
        val stage = Command03FilesProjection()
        assertTrue(stage.accept("s", "find_files", mapOf("query" to "experimente", "limit" to 20), tools.findFiles("experimente", emptyList(), 20)))
        try { tools.findFiles("x", listOf("/data/local"), 5); fail("a root outside the storage must be refused") } catch (_: IllegalArgumentException) { }
    }

    @Test fun latestApkIsNewestFirstInTheDownloadFolderAtMostThreeLevelsDeep() {
        tree()
        assertEquals(listOf("/sdcard/Download/continuum-0.3.70.apk", "/sdcard/Download/continuum-0.3.69.apk"), tools.findLatestApk(null).lines())
        assertEquals("/sdcard/Download/continuum-0.3.69.apk", tools.findLatestApk("0.3.69"))
        val none = tools.findLatestApk("gibtsnicht")
        assertTrue(none, none.startsWith("Keine passende APK-Datei"))
        assertFalse("the chain's success marker must not appear in an empty result", none.contains(".apk", ignoreCase = true))
        assertEquals("/sdcard/Download/continuum-0.3.70.apk",
            (OwnerChainStepCatalog.contract("find_latest_apk")!!.gives.single().extract(tools.findLatestApk(null)) { _, _ -> null } as ChainExtract.Result.Values).values["path"])
    }

    @Test fun largeFilesAreDuLinesWithFoldersCountedAndLargestFirst() {
        tree()
        val lines = tools.largeFiles(emptyList(), 4).lines()
        assertEquals(4, lines.size)
        // The folder weighs what its files weigh together (2 409 547 bytes), the largest file follows.
        assertEquals("2354\t/sdcard/Download", lines[0])
        assertEquals("2344\t/sdcard/Download/Experimente.pdf", lines[1])
        assertTrue(lines.all { Regex("\\d+\\t/sdcard/.+").matches(it) })
        assertFalse(lines.any { it.contains("/Android") || it.contains("/.hidden") })
        // The measured folder itself and its only file weigh the same; the path decides the order.
        assertEquals(listOf("1\t/sdcard/Documents/Labor", "1\t/sdcard/Documents/Labor/Argon4.pdf"),
            tools.largeFiles(listOf("/sdcard/Documents/Labor"), 2).lines())
        assertEquals("2354", (OwnerChainStepCatalog.contract("get_large_files")!!.gives.single()
            .extract(tools.largeFiles(emptyList(), 4)) { _, _ -> null } as ChainExtract.Result.Values).values["largest"]!!.substringBefore('\t'))
    }

    @Test fun aWalkThatHitsItsBoundSaysSoAndAStopEndsIt() {
        repeat(60) { index -> storage.file("Documents/viele/datei-$index.txt", ByteArray(1)) }
        val small = DeviceFileTools(storage, { TestStorage.ZONE }, DeviceFileWalk.Limits(maxEntries = 20))
        val found = small.findFiles("datei", emptyList(), 100)
        assertTrue(found, found.lines().last().startsWith("Hinweis: Suche nach 20 Einträgen beendet"))
        assertTrue(small.largeFiles(emptyList(), 5).lines().last().startsWith("Hinweis: Messung nach 20 Einträgen beendet"))
        assertTrue(small.recentFiles(null, 5).lines().last().startsWith("Hinweis: Suche nach 20 Einträgen beendet"))
        // A time bound of zero ends the walk at the first folder change, with the reason.
        var now = 0L
        val slow = DeviceFileTools(storage, { TestStorage.ZONE }, DeviceFileWalk.Limits(maxMillis = 5)) { now.also { now += 10_000_000L } }
        assertTrue(slow.findFiles("datei", emptyList(), 100).contains("Sekunden beendet"))
        // The owner's stop: the caller's flag, or the interrupt of the tool thread.
        try { tools.findFiles("datei", emptyList(), 100) { true }; fail("a stop must end the walk") } catch (_: CancellationException) { }
        Thread.currentThread().interrupt()
        try { tools.recentFiles(null, 5); fail("an interrupt must end the walk") } catch (_: CancellationException) { } finally { Thread.interrupted() }
    }

    @Test fun aFolderAndroidDoesNotLetMeReadIsSkippedAndSaidForAListing() {
        tree()
        val locked = storage.folder("Documents/gesperrt")
        storage.file("Documents/gesperrt/verborgen.pdf", ByteArray(1))
        // What Android does for a folder an app may not read: listFiles() answers null. A host that runs the tests
        // as root cannot take the right away, so the same answer is produced by a folder that is really a file.
        val unreadable = locked.setReadable(false, false) && locked.listFiles() == null
        try {
            if (unreadable) {
                assertTrue(tools.listFiles("/sdcard/Documents/gesperrt").startsWith("Nicht lesbar:"))
                assertTrue(tools.findFiles("verborgen", emptyList(), 10).startsWith("Keine Datei gefunden"))
            }
        } finally { locked.setReadable(true, false) }
        // Independent of the host's rights: a walk skips a folder whose listing is refused and goes on.
        var seen = 0
        val outcome = DeviceFileWalk.walk(storage.dir, File(storage.dir, "Documents/Labor/Argon4.pdf")) { seen++; true }
        assertEquals(0, seen)
        assertNull(outcome.stoppedEarly)
        assertTrue(tools.findFiles("argon4", emptyList(), 10).endsWith("Argon4.pdf"))
    }

    @Test fun recentFilesOverviewAndTextSearchReportOnlyWhatIsThere() {
        tree()
        val recent = tools.recentFiles(null, 3).lines()
        assertEquals("Zuletzt geänderte Dateien (3 von 9):", recent[0])
        assertEquals("1. /sdcard/Download/alt/tief/noch tiefer/alte.apk · 10 Byte · geändert 19.11.2026 10:00", recent[1])
        assertEquals("/sdcard/Download/alt/tief/noch tiefer/alte.apk",
            (OwnerChainStepCatalog.contract("list_recent_files")!!.gives.single().extract(recent.joinToString("\n")) { _, _ -> null } as ChainExtract.Result.Values).values["path"])
        assertTrue(tools.recentFiles("pdf", 10).lines().drop(1).all { it.contains(".pdf · ") })
        assertTrue(tools.recentFiles("bilder", 10).contains("IMG_1.jpg"))
        assertTrue(tools.recentFiles("xlsx", 10).startsWith("Keine Dateien gefunden (Filter „xlsx“)"))

        val overview = tools.folderOverview("/sdcard/Download")
        assertTrue(overview, overview.startsWith("Ordnerübersicht /sdcard/Download: 1 Unterordner, 5 Dateien direkt darin"))
        assertTrue(overview, overview.contains("6 Dateien mit allen Unterordnern"))
        assertTrue(overview, overview.contains("- alt/ · 1 Dateien · 10 Byte"))
        assertTrue(overview, overview.contains("PDF 2") && overview.contains("APK 3"))
        assertTrue(tools.folderOverview(null).startsWith("Ordnerübersicht /sdcard: 3 Unterordner"))
        assertTrue(tools.folderOverview("/sdcard/Nichts").startsWith("Nicht gefunden"))

        val text = tools.searchText("argon", null, 20, 6_000)
        assertTrue(text, text.startsWith("Textsuche „argon“ – 2 Fundstelle(n):"))
        assertTrue(text, text.contains("/sdcard/Download/experimente_notizen.txt · Zeile 1: Argon bei 4 bar"))
        assertTrue(text, text.contains("/sdcard/Documents/Notizen/todo.md · Zeile 1: - Argon bestellen"))
        assertTrue(text, text.contains("Durchsucht: 2 Textdatei(en)"))
        assertNotNull((OwnerChainStepCatalog.contract("search_file_text")!!.gives.single().extract(text) { _, _ -> null } as ChainExtract.Result.Values).values["path"])
        assertTrue(tools.searchText("xenon", null, 20, 6_000).startsWith("Textsuche „xenon“ – keine Fundstelle."))
        assertTrue(tools.searchText("argon", null, 1, 6_000).contains("gekürzt"))
    }

    @Test fun moveAndDeleteChangeExactlyOneFileInsideTheSharedFoldersAndSayWhyTheyCannot() {
        tree()
        assertNull(tools.moveProblem("/sdcard/Download/Experimente.pdf", "/sdcard/Documents"))
        assertEquals("Verschoben.\nVon: /sdcard/Download/Experimente.pdf\nNach: /sdcard/Documents/Experimente.pdf",
            tools.move("/sdcard/Download/Experimente.pdf", "/sdcard/Documents"))
        assertTrue(File(storage.dir, "Documents/Experimente.pdf").isFile && !File(storage.dir, "Download/Experimente.pdf").exists())
        // Nothing is overwritten, nothing outside the shared folders is touched, a missing source is said before.
        assertTrue(tools.moveProblem("/sdcard/Download/continuum-0.3.69.apk", "/sdcard/Download/continuum-0.3.70.apk")!!.contains("überschreibe nichts"))
        assertTrue(tools.moveProblem("/sdcard/Download/fehlt.txt", "/sdcard/Documents")!!.contains("gibt es nicht"))
        assertTrue(tools.moveProblem("/sdcard/Download/continuum-0.3.69.apk", "/sdcard/Music/x.apk")!!.contains("außerhalb der freigegebenen Ordner"))
        assertTrue(tools.moveProblem("/sdcard/Download", "/sdcard/Documents/Download")!!.contains("Hauptordner"))

        assertNull(tools.deleteProblem("/sdcard/Download/continuum-0.3.69.apk"))
        assertEquals("Gelöscht.\nDatei: /sdcard/Download/continuum-0.3.69.apk", tools.delete("/sdcard/Download/continuum-0.3.69.apk"))
        assertFalse(File(storage.dir, "Download/continuum-0.3.69.apk").exists())
        assertTrue(File(storage.dir, "Download/continuum-0.3.70.apk").exists())
        assertTrue(tools.deleteProblem("/sdcard/Download")!!.contains("Ordnerwurzeln"))
        assertTrue(tools.deleteProblem("/sdcard/Download/alt")!!.contains("ist ein Ordner"))
        assertTrue(tools.deleteProblem("/sdcard/Download/fehlt.txt")!!.contains("gibt es nicht"))
        assertTrue(tools.deleteProblem("/sdcard/Android/data/com.other/secret.pdf")!!.startsWith("Nicht gelöscht"))
        try { tools.delete("/sdcard/Download/alt"); fail("a folder is never deleted") } catch (_: IllegalStateException) { }
        assertTrue(File(storage.dir, "Download/alt/tief/noch tiefer/alte.apk").exists())
    }

    @Test fun pathsAndSizesAreWhatTheShellToolsPrinted() {
        assertEquals("Download/a.pdf", DevicePaths.relative("/storage/emulated/0/Download/a.pdf"))
        assertEquals("Download/a.pdf", DevicePaths.relative("Download/a.pdf"))
        assertEquals("", DevicePaths.relative("/sdcard/"))
        assertNull(DevicePaths.relative("/sdcard/../data"))
        assertNull(DevicePaths.relative("/sdcard/Android/data"))
        assertNull(DevicePaths.relative("/etc/passwd"))
        assertEquals("pdf", DevicePaths.extension("Bericht.PDF"))
        assertEquals("", DevicePaths.extension(".nomedia"))
        assertEquals(listOf("512", "1.0K", "3.4K", "498M", "1.1G"), listOf(512L, 1024L, 3_482L, 498L * 1024 * 1024, 1_181_116_006L).map(DeviceFileTools::human))
        assertEquals(listOf(0L, 1L, 1L, 2L), listOf(0L, 1L, 1024L, 1025L).map(DeviceFileTools::kilobytes))
    }
}
