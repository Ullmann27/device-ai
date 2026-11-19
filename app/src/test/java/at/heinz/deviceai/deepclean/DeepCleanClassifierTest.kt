package at.heinz.deviceai.deepclean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Every rule of the protection levels, the file system's link and path guards, and the duplicate finder. */
class DeepCleanClassifierTest {
    private val now = 1_800_000_000_000L
    private val day = 24 * 3_600_000L

    private fun shared(path: String, size: Long = 1_000, age: Long = 3 * day) = DeepCleanEntry(DeepCleanRoot.SHARED, path, size, now - age)
    private fun level(entry: DeepCleanEntry, active: Set<String> = emptySet()) = DeepCleanClassifier.classify(entry, now, active).level

    @Test fun greenOnlyForOwnRegenerableData() {
        assertEquals(DeepCleanLevel.GREEN, level(DeepCleanEntry(DeepCleanRoot.OWN_CACHE, "WebView/Default/HTTP Cache/abc", 500, now - day)))
        assertEquals(DeepCleanLevel.GREEN, level(DeepCleanEntry(DeepCleanRoot.OWN_EXTERNAL_CACHE, "preview-1.png", 500, now - day)))
        assertEquals(DeepCleanLevel.GREEN, level(DeepCleanEntry(DeepCleanRoot.OWN_FILES, "journal/journal-1234567890.tmp", 500, now - 2 * day)))
        // Cache the app needs: the model kernels, the update staging, the diagnosis ledger.
        for (path in listOf("litertlm/model.bin", "owner-update/continuum-download.part", "system-diagnostics/summary-1.cdiag"))
            assertEquals(path, DeepCleanLevel.RED, level(DeepCleanEntry(DeepCleanRoot.OWN_CACHE, path, 500, now - day)))
        // Running work: changed in the last 15 minutes.
        assertEquals(DeepCleanLevel.EXCLUDED, level(DeepCleanEntry(DeepCleanRoot.OWN_CACHE, "tile.png", 500, now - 60_000)))
        // A temp file younger than a day may belong to a write in progress; anything else in the own files is memory.
        assertEquals(DeepCleanLevel.RED, level(DeepCleanEntry(DeepCleanRoot.OWN_FILES, "journal/journal-1234567890.tmp", 500, now - 3_600_000)))
        for (path in listOf("models/gemma.litertlm", "memory/facts.json", "dialogue/history.jsonl", "tasks.db", "speech/model.bin", "notes.tmp"))
            assertEquals(path, DeepCleanLevel.RED, level(DeepCleanEntry(DeepCleanRoot.OWN_FILES, path, 500, now - 30 * day)))
    }

    @Test fun nothingInSharedStorageIsEverGreen() {
        val folders = listOf("", "Download/", "DCIM/Camera/", "Pictures/", "Documents/", "Android/data/com.x/cache/", "Android/media/com.whatsapp/",
            "Movies/", "Music/", ".thumbnails/", "Download/Continuum/", "tmp/", "cache/", "Backups/", "Projects/app/src/")
        val names = listOf("a.tmp", "b.crdownload", "c.part", "d.apk", "e.jpg", "f.mp4", "g.pdf", "h.log", "cache.bin", "journal-1234567890.tmp",
            "x (1).pdf", "big.zip", "thumb.png")
        for (folder in folders) for (name in names) for (age in listOf(60_000L, 2 * day, 400 * day)) for (size in listOf(10L, 500_000_000L))
            assertNotEquals("$folder$name", DeepCleanLevel.GREEN, level(shared("$folder$name", size, age)))
    }

    @Test fun personalDataIsRedEvenWhenOldOrLarge() {
        for (path in listOf("DCIM/Camera/IMG_0001.jpg", "DCIM/Camera/VID_0001.mp4", "Pictures/Familie/oma.png", "Documents/Steuer/2025.pdf",
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/IMG-1.jpg", "Music/lied.mp3", "Recordings/aufnahme.m4a",
                "Backups/handy.bak", "Projects/app/Main.kt"))
            assertEquals(path, DeepCleanLevel.RED, level(shared(path, size = 2_000_000_000, age = 3_000 * day)))
        // A document copy in Download that is not a duplicate is personal as well.
        assertEquals(DeepCleanLevel.RED, level(shared("Download/Vertrag.pdf", size = 300_000)))
        assertEquals(DeepCleanLevel.RED, level(shared("Android/data/com.other/cache/x.bin")))
    }

    @Test fun yellowNeedsAFindingThatIsNeverPersonalByItself() {
        assertEquals(DeepCleanLevel.YELLOW, level(shared("Download/app-release.apk", 50_000_000)))
        assertEquals(DeepCleanLevel.YELLOW, level(shared("Download/film.mkv.crdownload", 900_000_000, age = 2 * day)))
        assertEquals(DeepCleanLevel.YELLOW, level(shared("Download/archiv.zip", 500_000_000)))
        assertEquals(DeepCleanLevel.YELLOW, level(shared("DCIM/.thumbnails/1.jpg", 40_000)))
    }

    @Test fun runningDownloadsAndActiveFilesAreExcluded() {
        // A partial download changed in the last 24 hours may still be running.
        assertEquals(DeepCleanLevel.EXCLUDED, level(shared("Download/film.mkv.crdownload", 900_000_000, age = 3_600_000)))
        assertEquals(DeepCleanLevel.EXCLUDED, level(shared("Download/x.part", 10, age = 23 * 3_600_000)))
        val running = shared("Download/old.apk", 50_000_000)
        assertEquals(DeepCleanLevel.EXCLUDED, level(running, active = setOf(running.key)))
    }

    @Test fun linksAndEscapingPathsAreNeverCandidates() {
        assertEquals(DeepCleanLevel.RED, level(DeepCleanEntry(DeepCleanRoot.OWN_CACHE, "link", 0, now - day, symlink = true)))
        assertEquals(DeepCleanLevel.EXCLUDED, level(DeepCleanEntry(DeepCleanRoot.OWN_CACHE, "../files/memory.json", 10, now - day)))
        assertEquals(DeepCleanLevel.EXCLUDED, level(DeepCleanEntry(DeepCleanRoot.OWN_CACHE, "x", 10, now - day, insideRoot = false)))
    }

    @Test fun theFileSystemNeverFollowsALinkOrLeavesItsRoot() {
        val phone = TempPhone()
        val secret = phone.file(phone.files, "memory/facts.json", content = "geheim")
        phone.file(phone.cache, "real.bin")
        Files.createSymbolicLink(File(phone.cache, "facts-link").toPath(), secret.toPath())
        Files.createSymbolicLink(File(phone.cache, "dir-link").toPath(), File(phone.files, "memory").toPath())
        val fs = phone.fs()
        val seen = ArrayList<DeepCleanEntry>()
        fs.walk(DeepCleanRoot.OWN_CACHE, { false }) { seen += it; true }
        assertEquals(setOf("dir-link", "facts-link", "real.bin"), seen.map { it.relative }.toSet())
        assertTrue(seen.filter { it.relative.endsWith("link") }.all { it.symlink })
        // Neither the link nor a path through the linked folder can be read or removed.
        assertNull(fs.stat(DeepCleanRoot.OWN_CACHE, "facts-link"))
        assertNull(fs.stat(DeepCleanRoot.OWN_CACHE, "dir-link/facts.json"))
        assertNull(fs.stat(DeepCleanRoot.OWN_CACHE, "../files/memory/facts.json"))
        assertEquals(false, fs.delete(DeepCleanRoot.OWN_CACHE, "facts-link"))
        assertEquals(false, fs.delete(DeepCleanRoot.OWN_CACHE, "dir-link/facts.json"))
        assertEquals(false, fs.delete(DeepCleanRoot.OWN_CACHE, "../files/memory/facts.json"))
        assertTrue(secret.exists())
        assertEquals("geheim", secret.readText())
        // The shared storage's Android folder (other apps) is not entered at all.
        phone.file(phone.shared, "Android/data/com.other/files/a.bin")
        val sharedSeen = ArrayList<String>()
        val result = fs.walk(DeepCleanRoot.SHARED, { false }) { sharedSeen += it.relative; true }
        assertTrue(sharedSeen.none { it.startsWith("Android/") })
        assertTrue(result.skipped.contains("Android"))
    }

    @Test fun duplicatesOnlyByFullHashAndTheKeeperStays() {
        val phone = TempPhone()
        phone.file(phone.shared, "Download/bericht.pdf", content = "A".repeat(300_000), modified = phone.old - day)
        phone.file(phone.shared, "Download/bericht (1).pdf", content = "A".repeat(300_000))
        // Same size, different content: never a duplicate.
        phone.file(phone.shared, "Download/anders.pdf", content = "A".repeat(299_999) + "B")
        phone.file(phone.shared, "DCIM/Camera/IMG_1.jpg", content = "C".repeat(400_000))
        phone.file(phone.shared, "Download/IMG_1.jpg", content = "C".repeat(400_000))
        val fs = phone.fs()
        val scan = DeepCleanScanner.scan(fs, setOf(DeepCleanRoot.SHARED), phone.now, withDuplicates = true)
        val byPath = scan.findings.associateBy { it.entry.relative }
        assertEquals(DeepCleanLevel.YELLOW, byPath.getValue("Download/bericht (1).pdf").level)
        assertEquals(DeepCleanCategory.DUPLICATE, byPath.getValue("Download/bericht (1).pdf").category)
        assertEquals("Download/bericht.pdf", byPath.getValue("Download/bericht (1).pdf").keeper?.relative)
        assertTrue(byPath.getValue("Download/bericht (1).pdf").reason.startsWith("Wiederholter Download"))
        assertEquals(DeepCleanLevel.RED, byPath.getValue("Download/bericht.pdf").level)
        assertEquals(DeepCleanLevel.RED, byPath.getValue("Download/anders.pdf").level)
        // The photo in the camera folder is the copy that stays; the copy in Download may go.
        assertEquals(DeepCleanLevel.RED, byPath.getValue("DCIM/Camera/IMG_1.jpg").level)
        assertEquals(DeepCleanLevel.YELLOW, byPath.getValue("Download/IMG_1.jpg").level)
        assertEquals("DCIM/Camera/IMG_1.jpg", byPath.getValue("Download/IMG_1.jpg").keeper?.relative)
        assertEquals(64, byPath.getValue("Download/IMG_1.jpg").sha256?.length)
        assertTrue(scan.duplicates!!.complete)
    }

    @Test fun theDuplicateSearchStopsWhenTheDeviceIsHot() {
        val phone = TempPhone()
        phone.file(phone.shared, "Download/a.bin", 300_000)
        phone.file(phone.shared, "Download/b.bin", 300_000)
        val scan = DeepCleanScanner.scan(phone.fs(), setOf(DeepCleanRoot.SHARED), phone.now, withDuplicates = true, throttled = { true })
        assertTrue(scan.duplicates!!.stoppedByHeat)
        assertTrue(scan.duplicates!!.groups.isEmpty())
        assertTrue(scan.findings.none { it.category == DeepCleanCategory.DUPLICATE })
    }

    @Test fun keepRestrictionsCoverTheirGroup() {
        val photo = DeepCleanClassifier.classify(shared("Download/IMG_9.jpg", 200_000_000), now, emptySet())
        assertTrue(DeepCleanClassifier.protectedBy(photo, setOf(DeepCleanGroup.PHOTOS)))
        assertTrue(DeepCleanClassifier.protectedBy(photo, setOf(DeepCleanGroup.DOWNLOADS)))
        assertTrue(DeepCleanClassifier.protectedBy(photo, setOf(DeepCleanGroup.PERSONAL)))
        assertTrue(!DeepCleanClassifier.protectedBy(photo, setOf(DeepCleanGroup.APKS)))
    }
}
