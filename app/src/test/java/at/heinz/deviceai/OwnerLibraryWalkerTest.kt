package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CancellationException

/** The bounded storage walk of the document desk on a real directory tree (java.io only – no Android needed). */
class OwnerLibraryWalkerTest {
    @get:Rule val storage = TemporaryFolder()

    private fun put(path: String, bytes: Int = 10): File = File(storage.root, path).apply {
        parentFile!!.mkdirs()
        writeBytes(ByteArray(bytes))
    }

    @Test fun itListsEveryPdfWithItsRealFolderSizeAndDateAndSkipsWhatIsNotTheOwners() {
        put("Download/Argon4.pdf", 1_200).setLastModified(1_794_000_000_000)
        put("Download/Labor/Messung.PDF", 300)
        put("Documents/Bericht.pdf")
        put("Wurzel.pdf")
        put("Pictures/Scan/2026/Beleg.pdf")
        put("Download/Notiz.txt")
        put("Download/pdf")                              // a file merely called „pdf“
        File(storage.root, "Documents/Ordner.pdf").mkdirs() // a folder that looks like a PDF
        put("Android/data/com.example/files/Fremd.pdf")  // other apps' private data
        put(".Papierkorb/Geloescht.pdf")                 // hidden
        put("Download/.cache/Rest.pdf")
        val seen = mutableListOf<Int>()
        val scan = OwnerLibraryWalker.scan(storage.root, progress = { seen += it })
        assertEquals(listOf("Wurzel.pdf", "Argon4.pdf", "Bericht.pdf", "Messung.PDF", "Beleg.pdf"), scan.files.map { it.name })
        assertEquals(listOf("", "Download", "Documents", "Download/Labor", "Pictures/Scan/2026"), scan.files.map { it.folder })
        val argon = scan.files.single { it.name == "Argon4.pdf" }
        assertEquals(1_200L, argon.sizeBytes)
        assertEquals(1_794_000_000_000, argon.modifiedMillis)
        assertEquals(File(storage.root, "Download/Argon4.pdf").absolutePath, argon.path)
        assertEquals("Hauptordner", scan.files.first().folderLabel)
        // The usual document folders are walked first; Android/ and hidden folders are not entered at all.
        assertEquals(listOf("Download", "Documents", "Pictures"), scan.folders)
        assertNull(scan.stoppedEarly)
        assertEquals(listOf(5), seen)
    }

    @Test fun everyBoundEndsTheWalkAndSaysSo() {
        repeat(30) { put("Download/Datei$it.pdf") }
        repeat(5) { put("Documents/Tief/Tiefer/Bericht$it.pdf") }
        val byCount = OwnerLibraryWalker.scan(storage.root, OwnerLibraryWalker.Limits(maxPdfs = 12))
        assertEquals(12, byCount.files.size)
        assertEquals("Nach 12 PDFs habe ich die Suche beendet; nicht alle Ordner wurden geprüft.", byCount.stoppedEarly)
        val byEntries = OwnerLibraryWalker.scan(storage.root, OwnerLibraryWalker.Limits(maxEntries = 10))
        assertTrue(byEntries.files.size < 10)
        assertNotNull(byEntries.stoppedEarly)
        // Depth 1: the top folders are listed, their sub-folders are not entered.
        val shallow = OwnerLibraryWalker.scan(storage.root, OwnerLibraryWalker.Limits(maxDepth = 1))
        assertEquals(30, shallow.files.size)
        assertNull(shallow.stoppedEarly)
        // Time: the clock is injected, so the bound is exact – after the first folder nothing more is entered.
        var now = 0L
        val byTime = OwnerLibraryWalker.scan(storage.root, OwnerLibraryWalker.Limits(maxMillis = 1_000), nanoTime = { now.also { now += 600_000_000L } })
        assertEquals("Nach 1 Sekunden habe ich die Suche beendet; nicht alle Ordner wurden geprüft.", byTime.stoppedEarly)
        assertTrue(byTime.files.size < 35)
        // Progress is reported while walking, and once more at the end.
        val seen = mutableListOf<Int>()
        OwnerLibraryWalker.scan(storage.root, progress = { seen += it })
        assertEquals(listOf(25, 35), seen)
    }

    @Test fun stopEndsTheWalkAtOnceAndAMissingRootIsEmpty() {
        repeat(40) { put("Download/Datei$it.pdf") }
        var checks = 0
        try {
            OwnerLibraryWalker.scan(storage.root, cancelled = { ++checks > 7 })
            fail("the walk must end with the owner's stop")
        } catch (stopped: CancellationException) {
            assertEquals("Suche gestoppt", stopped.message)
        }
        assertEquals(8, checks)
        val empty = OwnerLibraryWalker.scan(File(storage.root, "gibt-es-nicht"))
        assertTrue(empty.files.isEmpty() && empty.folders.isEmpty())
        assertNull(empty.stoppedEarly)
    }
}
