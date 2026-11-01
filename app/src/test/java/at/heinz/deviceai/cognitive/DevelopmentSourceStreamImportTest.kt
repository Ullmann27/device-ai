package at.heinz.deviceai.cognitive

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class DevelopmentSourceStreamImportTest {
    private fun withPrivateDir(test: (File) -> Unit) {
        val dir = Files.createTempDirectory("continuum-source-test-").toFile()
        try { test(dir) } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }

    @Test fun validUtf8PersistsExactBytesAndHash() = withPrivateDir { dir ->
        val bytes = "fun grüßen() = \"Hallo\"\n".toByteArray(Charsets.UTF_8)
        val progress = mutableListOf<Long>()
        val result = DevelopmentSourceStreamImport.importUtf8(ByteArrayInputStream(bytes), "app/Greeter.kt", dir,
            onProgress = { progress.add(it) })
        assertEquals(bytes.size.toLong(), result.bytes)
        assertArrayEquals(bytes, result.file.readBytes())
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }, result.sha256)
        assertEquals(listOf(bytes.size.toLong()), progress)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test fun utf8InvalidAcrossChunkBoundaryRejectedWithoutResidue() = withPrivateDir { dir ->
        val bad = ByteArray(65537) { 'a'.code.toByte() }.also { it[65535] = 0xC3.toByte(); it[65536] = 0x28 }
        try {
            DevelopmentSourceStreamImport.importUtf8(ByteArrayInputStream(bad), "src/Bad.kt", dir)
            fail("Invalid UTF-8 accepted")
        } catch (_: IOException) { }
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun nullBytesAndBackupNamesRejected() = withPrivateDir { dir ->
        try { DevelopmentSourceStreamImport.importUtf8(ByteArrayInputStream(byteArrayOf(65, 0, 66)), "a/Test.kt", dir); fail("NUL accepted") }
        catch (_: IOException) { }
        try { DevelopmentSourceStreamImport.importUtf8(ByteArrayInputStream(byteArrayOf(65)), "backup.kt", dir); fail("backup accepted") }
        catch (_: IllegalArgumentException) { }
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun cancellationAndSmallBudgetLeaveNoPartialFile() = withPrivateDir { dir ->
        try {
            DevelopmentSourceStreamImport.importUtf8(ByteArrayInputStream(ByteArray(80) { 65 }), "a/Test.kt", dir, maxBytes = 16)
            fail("Oversized source accepted")
        } catch (_: IOException) { }
        var cancelled = false
        try {
            DevelopmentSourceStreamImport.importUtf8(ByteArrayInputStream(ByteArray(130000) { 65 }), "a/Test.kt", dir,
                cancelled = { cancelled }, onProgress = { cancelled = true })
            fail("Cancellation ignored")
        } catch (_: IOException) { }
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun hundredMiBStreamingFindsMarkerPastNinetyMiBWithoutWholeFileInMemory() = withPrivateDir { dir ->
        val size = DevelopmentSourceStreamImport.MAX_SOURCE_BYTES
        val markerOffset = 95L * 1024 * 1024
        val marker = "TARGET_AT_95_MIB".toByteArray()
        val stream = object : InputStream() {
            var position = 0L
            override fun read(): Int = if (position >= size) -1 else if (position >= markerOffset && position < markerOffset + marker.size) marker[(position++ - markerOffset).toInt()].toInt() and 255 else { position++; 65 }
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (position >= size) return -1
                val count = minOf(len.toLong(), size - position).toInt()
                java.util.Arrays.fill(buffer, off, off + count, 65.toByte())
                val start = maxOf(position, markerOffset)
                val end = minOf(position + count, markerOffset + marker.size)
                for (p in start until end) buffer[off + (p - position).toInt()] = marker[(p - markerOffset).toInt()]
                position += count
                return count
            }
        }
        val imported = DevelopmentSourceStreamImport.importUtf8(stream, "large/Source.txt", dir)
        assertEquals(size, imported.bytes)
        java.io.RandomAccessFile(imported.file, "r").use { f ->
            f.seek(markerOffset)
            val found = ByteArray(marker.size)
            f.readFully(found)
            assertArrayEquals(marker, found)
        }
    }

    @Test fun sourceOverHundredMiBFailsWithoutResidue() = withPrivateDir { dir ->
        val size = DevelopmentSourceStreamImport.MAX_SOURCE_BYTES + 1
        val stream = object : InputStream() {
            var position = 0L
            override fun read(): Int = if (position++ < size) 65 else -1
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (position >= size) return -1
                val n = minOf(len.toLong(), size - position).toInt()
                java.util.Arrays.fill(buffer, off, off + n, 65.toByte())
                position += n
                return n
            }
        }
        try { DevelopmentSourceStreamImport.importUtf8(stream, "large/TooBig.txt", dir); fail("Over-limit source accepted") }
        catch (_: IOException) { }
        assertTrue(dir.listFiles().isNullOrEmpty())
    }
}
