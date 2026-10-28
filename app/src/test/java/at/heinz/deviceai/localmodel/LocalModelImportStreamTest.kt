package at.heinz.deviceai.localmodel

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.security.MessageDigest

class LocalModelImportStreamTest {
    private val caps = LocalModelCapabilities(true, false, false, false, false,
        1024, "0.18.0", setOf(LocalModelBackend.CPU))
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun noTemporaryFiles(directory: java.io.File) {
        assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith("model-import-") })
    }
    private class ZeroStream : InputStream() {
        var calls = 0
        var closed = false
        override fun read(): Int = 0
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            calls++
            check(calls <= 32) { "TEST_SAFETY_CUTOFF" }
            return 0
        }
        override fun close() { closed = true }
    }

    @Test fun stalledProviderFailsBeforeInspectionAndCleansTemporaryFile() {
        val dir = Files.createTempDirectory("model-stalled-").toFile()
        try {
            var inspected = false
            val catalog = LocalModelCatalog(dir, LocalModelInspector { inspected = true; caps })
            val input = ZeroStream()
            assertThrows(IOException::class.java) { catalog.import("sample", "v1", input, "0".repeat(64)) }
            assertEquals(16, input.calls)
            assertTrue(input.closed)
            assertFalse(inspected)
            assertTrue(catalog.all().isEmpty())
            noTemporaryFiles(dir)
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancelledBeforeReadPreservesInterruptAndDoesNotInspect() {
        val dir = Files.createTempDirectory("model-interrupt-").toFile()
        val input = ZeroStream()
        try {
            val catalog = LocalModelCatalog(dir, LocalModelInspector { error("MUST_NOT_INSPECT") })
            Thread.currentThread().interrupt()
            try {
                assertThrows(InterruptedIOException::class.java) { catalog.import("sample", "v1", input, "0".repeat(64)) }
                assertTrue(Thread.currentThread().isInterrupted)
                assertEquals(0, input.calls)
                assertTrue(input.closed)
            } finally { Thread.interrupted() }
            assertTrue(catalog.all().isEmpty())
            noTemporaryFiles(dir)
        } finally { Thread.interrupted(); dir.deleteRecursively() }
    }

    @Test fun interruptedReadCannotCommitEvenIfItReturnsValidBytes() {
        val dir = Files.createTempDirectory("model-mid-read-").toFile()
        val bytes = "synthetic fixture bytes".toByteArray()
        try {
            val input = object : ByteArrayInputStream(bytes) {
                override fun read(target: ByteArray, offset: Int, length: Int): Int {
                    val count = super.read(target, offset, length)
                    Thread.currentThread().interrupt()
                    return count
                }
            }
            val catalog = LocalModelCatalog(dir, LocalModelInspector { error("MUST_NOT_INSPECT") })
            try { assertThrows(InterruptedIOException::class.java) { catalog.import("sample", "v1", input, sha(bytes)) } }
            finally { Thread.interrupted() }
            assertTrue(catalog.all().isEmpty())
            noTemporaryFiles(dir)
        } finally { Thread.interrupted(); dir.deleteRecursively() }
    }

    @Test fun interruptedNativeInspectionNeverPublishesModel() {
        val dir = Files.createTempDirectory("model-inspector-cancel-").toFile()
        val bytes = "verified synthetic bytes".toByteArray()
        try {
            val catalog = LocalModelCatalog(dir, LocalModelInspector {
                Thread.currentThread().interrupt()
                caps
            })
            try {
                assertThrows(InterruptedIOException::class.java) {
                    catalog.import("sample", "v1", ByteArrayInputStream(bytes), sha(bytes))
                }
                assertTrue(Thread.currentThread().isInterrupted)
            } finally {
                Thread.interrupted()
            }
            assertTrue(catalog.all().isEmpty())
            assertNull(catalog.get("sample", "v1"))
            noTemporaryFiles(dir)
        } finally {
            Thread.interrupted()
            dir.deleteRecursively()
        }
    }

    @Test fun transientEmptyReadsAreToleratedAndHashIsPreserved() {
        val dir = Files.createTempDirectory("model-transient-").toFile()
        val bytes = "valid synthetic bytes".toByteArray()
        try {
            var calls = 0
            val input = object : ByteArrayInputStream(bytes) {
                override fun read(target: ByteArray, offset: Int, length: Int): Int =
                    if (++calls <= 2) 0 else super.read(target, offset, length)
            }
            val catalog = LocalModelCatalog(dir, LocalModelInspector { caps })
            val record = catalog.import("sample", "v1", input, sha(bytes))
            assertEquals(sha(bytes), record.sha256)
            assertEquals(bytes.size.toLong(), record.byteCount)
            assertArrayEquals(bytes, catalog.modelFile(record).readBytes())
            noTemporaryFiles(dir)
        } finally { dir.deleteRecursively() }
    }

    @Test fun progressResetsConsecutiveEmptyReadCounter() {
        val dir = Files.createTempDirectory("model-progress-").toFile()
        try {
            var calls = 0
            val input = object : InputStream() {
                override fun read(): Int = -1
                override fun read(target: ByteArray, offset: Int, length: Int): Int {
                    calls++
                    if (calls == 16 || calls == 32) { target[offset] = 42; return 1 }
                    if (calls >= 33) return -1
                    return 0
                }
            }
            val catalog = LocalModelCatalog(dir, LocalModelInspector { caps })
            val record = catalog.import("sample", "v1", input, sha(byteArrayOf(42, 42)))
            assertEquals(2L, record.byteCount)
            assertEquals(33, calls)
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedImportPreservesPreviouslyImportedRecordAndBytes() {
        val dir = Files.createTempDirectory("model-preserve-").toFile()
        try {
            val bytes = "previous verified fixture".toByteArray()
            val catalog = LocalModelCatalog(dir, LocalModelInspector { caps })
            val record = catalog.import("sample", "v1", ByteArrayInputStream(bytes), sha(bytes))
            assertThrows(IOException::class.java) { catalog.import("sample", "v2", ZeroStream(), "0".repeat(64)) }
            assertEquals(listOf(record), catalog.all())
            assertEquals(record, catalog.get("sample", "v1"))
            assertArrayEquals(bytes, catalog.modelFile(record).readBytes())
            assertNull(catalog.get("sample", "v2"))
            noTemporaryFiles(dir)
        } finally { dir.deleteRecursively() }
    }

    @Test fun providerReadFailurePropagatesAndCleansUp() {
        val dir = Files.createTempDirectory("model-read-error-").toFile()
        try {
            val input = object : InputStream() { override fun read(): Int { throw IOException("CONTROLLED_READ_ERROR") } }
            val catalog = LocalModelCatalog(dir, LocalModelInspector { error("MUST_NOT_INSPECT") })
            val failure = assertThrows(IOException::class.java) { catalog.import("sample", "v1", input, "0".repeat(64)) }
            assertEquals("CONTROLLED_READ_ERROR", failure.message)
            assertTrue(catalog.all().isEmpty())
            noTemporaryFiles(dir)
        } finally { dir.deleteRecursively() }
    }

    @Test fun emptyFileStillFailsWithoutInspectionOrCommit() {
        val dir = Files.createTempDirectory("model-empty-").toFile()
        try {
            val catalog = LocalModelCatalog(dir, LocalModelInspector { error("MUST_NOT_INSPECT") })
            assertThrows(IllegalArgumentException::class.java) { catalog.import("sample", "v1", ByteArrayInputStream(byteArrayOf()), sha(byteArrayOf())) }
            assertTrue(catalog.all().isEmpty())
            noTemporaryFiles(dir)
        } finally { dir.deleteRecursively() }
    }
}
