package at.heinz.deviceai.systemintelligence

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class StorageContentAnalysisTest {
    private class Source : StorageDocumentSource {
        val docs = linkedMapOf<String, StorageDocument>()
        val bytes = mutableMapOf<String, ByteArray>()
        val children = mutableMapOf<String, List<StorageDocument>>()
        var opened = 0
        var afterOpen: (() -> Unit)? = null
        var metadataHook: (() -> Unit)? = null
        fun file(id: String, name: String, value: String, virtual: Boolean = false): StorageDocument {
            bytes[id] = value.toByteArray()
            return StorageDocument(id, name, "application/octet-stream", bytes.getValue(id).size.toLong(),
                1_000, virtual = virtual).also { docs[id] = it }
        }
        override fun children(parentId: String, limit: Int) = children[parentId].orEmpty().take(limit)
        override fun metadata(id: String): StorageDocument? {
            metadataHook?.invoke()
            return docs[id]
        }
        override fun open(id: String): InputStream {
            opened++
            afterOpen?.invoke()
            return ByteArrayInputStream(bytes.getValue(id))
        }
    }

    @Test fun duplicateRequiresCompleteIdenticalContent() {
        val source = Source()
        val a = source.file("a", "same.jpg", "AAAA")
        val b = source.file("b", "same.jpg", "BBBB")
        val c = source.file("c", "different.jpg", "AAAA")
        val result = StorageContentAnalyzer().analyze(source, listOf(a, b, c))
        assertEquals(listOf("a", "c"), result.duplicates.single().documentIds)
        assertEquals(12L, result.hashedBytes)
        assertTrue(result.complete)
    }

    @Test fun shortAndGrowingContentNeverProduceDuplicates() {
        val source = Source()
        val a = source.file("a", "a", "abc")
        val b = source.file("b", "b", "abc")
        source.bytes["a"] = "ab".toByteArray()
        source.bytes["b"] = "abcd".toByteArray()
        val result = StorageContentAnalyzer().analyze(source, listOf(a, b))
        assertTrue(result.duplicates.isEmpty())
        assertFalse(result.complete)
        assertTrue(result.files.all { it.contentSha256 == null })
    }

    @Test fun changedMetadataInvalidatesFullHash() {
        val source = Source()
        val a = source.file("a", "a", "abc")
        val b = source.file("b", "b", "abc")
        source.afterOpen = { source.docs["a"] = a.copy(modifiedEpochMs = 2_000) }
        val result = StorageContentAnalyzer().analyze(source, listOf(a, b))
        assertTrue(result.duplicates.isEmpty())
        assertNull(result.files.first().contentSha256)
    }

    @Test fun hashBudgetNeverReadsBeyondCap() {
        val source = Source()
        val docs = listOf(source.file("a", "a", "abcdef"), source.file("b", "b", "abcdef"))
        val result = StorageContentAnalyzer(StorageScanLimits(maxHashBytes = 8, maxFileHashBytes = 8))
            .analyze(source, docs)
        assertTrue(result.hashedBytes <= 8)
        assertTrue(result.duplicates.isEmpty())
        assertFalse(result.complete)
    }

    @Test fun noHashWorkForUniqueSizes() {
        val source = Source()
        val docs = listOf(source.file("a", "a", "abc"), source.file("b", "b", "abcdef"))
        val result = StorageContentAnalyzer().analyze(source, docs)
        assertEquals(0, source.opened)
        assertEquals(9L, result.scannedBytes)
        assertTrue(result.complete)
    }

    @Test fun virtualDocumentsAreNotOpened() {
        val source = Source()
        val docs = listOf(source.file("a", "a", "abc", true), source.file("b", "b", "abc", true))
        val result = StorageContentAnalyzer().analyze(source, docs)
        assertEquals(0, source.opened)
        assertFalse(result.complete)
    }

    @Test fun cancelledScanDoesNotReadContent() {
        val source = Source()
        val a = source.file("a", "a", "abc")
        val result = StorageContentAnalyzer().analyze(source, listOf(a)) { true }
        assertEquals(0, source.opened)
        assertFalse(result.complete)
        assertTrue("CANCELLED" in result.limitations)
    }

    @Test fun deadlineAndClockRollbackStopWork() {
        val source = Source()
        val a = source.file("a", "a", "abc")
        var now = 100L
        val result = StorageContentAnalyzer(monotonicMs = { now.also { now = 99 } })
            .analyze(source, listOf(a))
        assertTrue("TIME_LIMIT" in result.limitations)
        assertEquals(0, source.opened)
    }

    @Test fun cyclesAndEntryCapsStayBounded() {
        val source = Source()
        val root = StorageDocument("root", "root", "directory", null, null, directory = true)
        source.docs["root"] = root
        val a = source.file("a", "a", "abc")
        source.children["root"] = listOf(root, a, source.file("b", "b", "abc"))
        val result = StorageContentAnalyzer(StorageScanLimits(maxEntries = 3)).analyze(source, listOf(root))
        assertTrue(result.files.size <= 2)
        assertFalse(result.complete)
        assertTrue("ENTRY_LIMIT" in result.limitations)
        assertTrue("REPEATED_DOCUMENT" in result.limitations)
    }

    @Test fun toolSummaryOmitsNamesUrisAndDigests() {
        val source = Source()
        val a = source.file("content://private/a", "Private passport.pdf", "abc")
        val b = source.file("content://private/b", "Private passport copy.pdf", "abc")
        val result = StorageContentAnalyzer().analyze(source, listOf(a, b))
        val text = result.summaryForTool()
        assertFalse(text.contains("Private"))
        assertFalse(text.contains("content://"))
        assertFalse(text.contains(result.files.first().contentSha256!!))
    }

    @Test fun aliasesOfOneDocumentAreNotDuplicates() {
        val source = Source()
        val a = source.file("a", "a", "abc").copy(stableKey = "provider/document")
        val b = source.file("b", "b", "abc").copy(stableKey = "provider/document")
        source.docs["a"] = a
        source.docs["b"] = b
        val result = StorageContentAnalyzer().analyze(source, listOf(a, b))
        assertEquals(1, result.files.size)
        assertTrue(result.duplicates.isEmpty())
        assertEquals(0, source.opened)
    }

    @Test fun deadlineAfterFinalMetadataRejectsLateHash() {
        val source = Source()
        val docs = listOf(source.file("a", "a", "abc"), source.file("b", "b", "abc"))
        var now = 0L
        var calls = 0
        source.metadataHook = { if (++calls >= 2) now = 100 }
        val result = StorageContentAnalyzer(StorageScanLimits(maxDurationMs = 50)) { now }.analyze(source, docs)
        assertTrue(result.duplicates.isEmpty())
        assertNull(result.files.first().contentSha256)
        assertFalse(result.complete)
    }
}
