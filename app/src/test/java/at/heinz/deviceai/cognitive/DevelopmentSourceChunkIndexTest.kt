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
}
