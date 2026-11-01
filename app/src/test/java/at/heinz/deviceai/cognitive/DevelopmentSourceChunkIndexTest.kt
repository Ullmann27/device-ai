package at.heinz.deviceai.cognitive

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DevelopmentSourceChunkIndexTest {
    private fun withPrivateDir(test: (File) -> Unit) {
        val dir = Files.createTempDirectory("continuum-chunk-index-").toFile()
        try { test(dir) } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test fun persistedIndexRoundTripDoesNotSplitMultibyteCharacters() = withPrivateDir { dir ->
        val text = "A".repeat(65535) + "€\n" + "B".repeat(70000) + "\nEND"
        val source = DevelopmentSourceStreamImport.importUtf8(
            ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), "src/Utf8.txt", dir)
        val built = DevelopmentSourceChunkIndex.build(source)
        assertTrue(built.chunks.size >= 3)
        assertEquals(source.bytes, built.chunks.sumOf { it.end - it.start })
        val restored = DevelopmentSourceChunkIndex.load(source)
        assertEquals(built.chunks, restored.chunks)
        val reconstructed = restored.chunks.indices.joinToString("") {
            DevelopmentSourceChunkIndex.read(restored, it).text
        }
        assertEquals(text, reconstructed)
        assertEquals(1L, restored.chunks.first().firstLine)
        assertEquals(3L, restored.chunks.last().lastLine)
    }

    @Test fun lateMarkerCanBeReadWithoutLoadingWholeSource() = withPrivateDir { dir ->
        val offset = 3 * 1024 * 1024
        val bytes = ByteArray(offset + 8192) { 65 }.also {
            "LATE_MARKER".toByteArray().copyInto(it, offset)
        }
        val source = DevelopmentSourceStreamImport.importUtf8(
            ByteArrayInputStream(bytes), "src/Large.txt", dir)
        val index = DevelopmentSourceChunkIndex.build(source)
        val target = index.chunks.indexOfFirst { it.start <= offset && it.end > offset }
        assertTrue(target > 0)
        val excerpt = DevelopmentSourceChunkIndex.read(index, target)
        assertTrue(excerpt.text.contains("LATE_MARKER"))
        assertTrue(excerpt.text.toByteArray(Charsets.UTF_8).size <= DevelopmentSourceChunkIndex.CHUNK_BYTES + 3)
    }

    @Test fun sourceMutationIsRejectedOnLoadAndRead() = withPrivateDir { dir ->
        val source = DevelopmentSourceStreamImport.importUtf8(
            ByteArrayInputStream("source\n".toByteArray()), "src/Source.txt", dir)
        val index = DevelopmentSourceChunkIndex.build(source)
        java.io.RandomAccessFile(source.file, "rw").use { file ->
            file.seek(0)
            file.writeByte('X'.code)
        }
        try { DevelopmentSourceChunkIndex.load(source); fail("Changed source accepted") }
        catch (_: IOException) { }
        try { DevelopmentSourceChunkIndex.read(index, 0); fail("Changed chunk accepted") }
        catch (_: IOException) { }
    }

    @Test fun corruptedSidecarAndCancellationAreRejectedWithoutNewArtifacts() = withPrivateDir { dir ->
        val source = DevelopmentSourceStreamImport.importUtf8(
            ByteArrayInputStream(ByteArray(130000) { 65 }), "src/Source.txt", dir)
        try {
            DevelopmentSourceChunkIndex.build(source, cancelled = { true })
            fail("Cancelled index accepted")
        } catch (_: IOException) { }
        assertEquals(1, dir.listFiles()!!.size)
        val index = DevelopmentSourceChunkIndex.build(source)
        index.indexFile.appendText("tampered")
        try { DevelopmentSourceChunkIndex.load(source); fail("Tampered index accepted") }
        catch (_: IOException) { }
    }

    @Test fun alteredLineMetadataCannotBorrowValidWholeSourceHash() = withPrivateDir { dir ->
        val source = DevelopmentSourceStreamImport.importUtf8(
            ByteArrayInputStream("first\nsecond\nthird".toByteArray()),
            "src/Provenance.txt", dir)
        val index = DevelopmentSourceChunkIndex.build(source)
        // The file and its genuine SHA-256 are unchanged. Only a persisted
        // line number is corrupted, which a whole-source hash cannot detect.
        java.io.RandomAccessFile(index.indexFile, "rw").use { file ->
            file.readUTF() // magic
            file.readLong() // source length
            file.readUTF() // source SHA-256
            file.readInt() // chunk count
            file.readLong() // first chunk start
            file.readLong() // first chunk end
            val firstLineOffset = file.filePointer
            file.seek(firstLineOffset)
            file.writeLong(999L)
        }
        try { DevelopmentSourceChunkIndex.load(source); fail("Forged line provenance accepted") }
        catch (_: IOException) { }
    }

    @Test fun alteredChunkDigestCannotBorrowValidWholeSourceHash() = withPrivateDir { dir ->
        val source = DevelopmentSourceStreamImport.importUtf8(
            ByteArrayInputStream("alpha\nbeta\ngamma".toByteArray()),
            "src/Digest.txt", dir)
        val index = DevelopmentSourceChunkIndex.build(source)
        java.io.RandomAccessFile(index.indexFile, "rw").use { file ->
            file.readUTF()
            file.readLong()
            file.readUTF()
            file.readInt()
            repeat(4) { file.readLong() } // start, end, firstLine, lastLine
            val digestLength = file.readUnsignedShort()
            assertEquals(64, digestLength)
            val digestStart = file.filePointer
            file.seek(digestStart)
            val old = file.readUnsignedByte()
            file.seek(digestStart)
            file.writeByte(if (old == '0'.code) '1'.code else '0'.code)
        }
        try { DevelopmentSourceChunkIndex.load(source); fail("Forged chunk digest accepted") }
        catch (_: IOException) { }
    }

    @Test fun hundredMiBIndexReadsNinetyFiveMiBMarkerWithBoundedBuffers() = withPrivateDir { dir ->
        val file = File(dir, "large.utf8")
        val marker = "MARKER_AT_95_MIB".toByteArray()
        val block = ByteArray(DevelopmentSourceChunkIndex.CHUNK_BYTES) { 65 }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.outputStream().buffered(DevelopmentSourceChunkIndex.CHUNK_BYTES).use { output ->
            repeat(1600) { part ->
                if (part == 95 * 16) marker.copyInto(block, 0)
                output.write(block)
                digest.update(block)
                if (part == 95 * 16) java.util.Arrays.fill(block, 0, marker.size, 65.toByte())
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        val source = DevelopmentSourceStreamImport.ImportedSource("src/Large.txt", file, file.length(), hash)
        assertEquals(DevelopmentSourceStreamImport.MAX_SOURCE_BYTES, source.bytes)
        val index = DevelopmentSourceChunkIndex.build(source)
        assertEquals(1600, index.chunks.size)
        assertTrue(DevelopmentSourceChunkIndex.read(index, 95 * 16).text.startsWith("MARKER_AT_95_MIB"))
        assertEquals(index.chunks, DevelopmentSourceChunkIndex.load(source).chunks)
    }
}
