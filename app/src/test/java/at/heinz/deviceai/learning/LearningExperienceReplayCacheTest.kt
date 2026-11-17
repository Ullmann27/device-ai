package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Synthetic development regressions. Independent QA cases are not part of this file. */
class LearningExperienceReplayCacheTest {
    private fun record(index: Int, type: LearningExperienceType = LearningExperienceType.USER_FACT,
        status: LearningStatus = LearningStatus.CONFIRMED) = LearningExperienceFactory.create(
        type, LearningProvenance.SYNTHETIC_FIXTURE, LearningTrustClass.OWNER_CONFIRMED,
        status, "Synthetic replay record $index — café 東京", now = (index / 7).toLong())

    private fun fixture(count: Int = 300, block: (File, FileLearningExperienceStore) -> Unit) {
        val dir = Files.createTempDirectory("replay-cache-fixture-").toFile()
        try {
            val store = FileLearningExperienceStore(dir)
            store.appendMissing((0 until count).map { record(it) })
            block(dir, store)
        } finally { dir.deleteRecursively() }
    }

    // Independent oracle: filter first, descending comparator, bounded newest selection, then ascending.
    private fun expected(items: List<LearningExperience>, type: LearningExperienceType, limit: Int) =
        items.filter { it.type == type && it.status != LearningStatus.REJECTED && it.status != LearningStatus.REVOKED }
            .sortedWith(compareByDescending<LearningExperience> { it.updatedAt }
                .thenByDescending { it.createdAt }.thenByDescending { it.id })
            .take(limit).reversed()

    @Test fun coldWarmAndChangingLimitsMatchFullOracleAt4096Records() = fixture(0) { _, store ->
        val records = (0 until 4096).map { i -> record(i,
            LearningExperienceType.entries[i % LearningExperienceType.entries.size],
            LearningStatus.entries[(i / 7) % LearningStatus.entries.size]) }
        store.appendMissing(records)
        for (type in LearningExperienceType.entries) {
            for (limit in listOf(1, 64, 128, 2, 96, 128)) {
                assertEquals(expected(records, type, limit), store.replay(type, limit))
                assertEquals(expected(records, type, limit), store.replay(type, limit))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { store.replay(LearningExperienceType.USER_FACT, 0) }
        assertThrows(IllegalArgumentException::class.java) { store.replay(LearningExperienceType.USER_FACT, 129) }
    }

    @Test fun legacyWarmReadDetectsSameLengthSameMtimeStatusChange() = fixture(40) { dir, store ->
        store.updateMatching(LearningExperienceType.USER_FACT, status = LearningStatus.OBSERVED, now = 100)
        val selected = store.replay(LearningExperienceType.USER_FACT, 1).single()
        val primary = dir.resolve("experiences-v2.bin")
        val size = primary.length()
        val mtime = primary.lastModified()
        FileLearningExperienceStore(dir).updateStatus(selected.id, LearningStatus.REJECTED, 1000)
        assertEquals(size, primary.length())
        assertTrue(primary.setLastModified(mtime))
        assertFalse(store.replay(LearningExperienceType.USER_FACT, 128).any { it.id == selected.id })
        assertEquals(expected(store.all(), LearningExperienceType.USER_FACT, 128), store.replay(LearningExperienceType.USER_FACT, 128))
    }

    @Test fun foreignWriterRevocationAndLocalNoopNeverResurrectCachedRecord() = fixture { dir, store ->
        val selected = store.replay(LearningExperienceType.USER_FACT, 1).single()
        val writer = FileLearningExperienceStore(dir)
        assertTrue(writer.updateStatus(selected.id, LearningStatus.REVOKED, 1000))
        assertFalse(store.append(selected))
        repeat(3) { assertFalse(store.replay(LearningExperienceType.USER_FACT, 128).any { it.id == selected.id }) }
        assertEquals(LearningStatus.REVOKED, writer.all().single { it.id == selected.id }.status)
    }

    @Test fun correctionAndReconfirmationInvalidateReplayWithoutLosingHistory() = fixture { _, store ->
        fun label(annotation: String, time: Long) = LearningExperienceFactory.create(
            LearningExperienceType.INTENT_LABEL, LearningProvenance.USER_CONFIRMED,
            LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED,
            "synthetic owner label", annotation = annotation, now = time)
        val old = label("0", 1000)
        val corrected = label("1", 2000)
        store.confirmIntentLabel(old)
        assertEquals(listOf(old), store.replay(LearningExperienceType.INTENT_LABEL))
        store.confirmIntentLabel(corrected)
        assertEquals(listOf(corrected), store.replay(LearningExperienceType.INTENT_LABEL))
        store.confirmIntentLabel(label("0", 3000))
        assertEquals(listOf(old.copy(updatedAt = 3000)), store.replay(LearningExperienceType.INTENT_LABEL))
        assertEquals(2, store.all().count { it.type == LearningExperienceType.INTENT_LABEL })
    }

    @Test fun emptyTypeCacheAndTypeSwitchCannotHideNewRecords() = fixture { dir, store ->
        repeat(2) { assertTrue(store.replay(LearningExperienceType.COMMAND_MAPPING).isEmpty()) }
        val added = record(800, LearningExperienceType.COMMAND_MAPPING)
        FileLearningExperienceStore(dir).append(added)
        assertEquals(listOf(added), store.replay(LearningExperienceType.COMMAND_MAPPING))
        assertEquals(64, store.replay(LearningExperienceType.USER_FACT).size)
        assertEquals(listOf(added), store.replay(LearningExperienceType.COMMAND_MAPPING))
    }

    @Test fun warmNegativeResultStillRejectsSameSizeSameMtimeUnrelatedSegmentCorruption() = fixture { dir, store ->
        assertTrue(store.replay(LearningExperienceType.COMMAND_MAPPING).isEmpty())
        assertTrue(store.replay(LearningExperienceType.COMMAND_MAPPING).isEmpty())
        val segment = dir.resolve("experiences-v3").listFiles()!!.first { it.extension == "seg" }
        val bytes = segment.readBytes()
        val mtime = segment.lastModified()
        segment.writeBytes(bytes.copyOf().also { it[30] = (it[30].toInt() xor 1).toByte() })
        assertTrue(segment.setLastModified(mtime))
        assertThrows(Exception::class.java) { store.replay(LearningExperienceType.COMMAND_MAPPING) }
        segment.writeBytes(bytes)
        assertTrue(store.replay(LearningExperienceType.COMMAND_MAPPING).isEmpty())
    }

    @Test fun warmPositiveResultRejectsMissingManifestAndSegment() = fixture { dir, store ->
        val before = store.replay(LearningExperienceType.USER_FACT)
        val primary = dir.resolve("experiences-v2.bin")
        val manifest = primary.readBytes()
        assertTrue(primary.delete())
        assertThrows(Exception::class.java) { store.replay(LearningExperienceType.USER_FACT) }
        primary.writeBytes(manifest)
        assertEquals(before, store.replay(LearningExperienceType.USER_FACT))
        val segment = dir.resolve("experiences-v3").listFiles()!!.first { it.extension == "seg" }
        assertTrue(segment.delete())
        assertThrows(Exception::class.java) { store.replay(LearningExperienceType.USER_FACT) }
    }

    @Test fun deletedAndRecreatedStoreCannotReturnOldReplay() = fixture { dir, store ->
        assertEquals(64, store.replay(LearningExperienceType.USER_FACT).size)
        assertTrue(dir.deleteRecursively())
        assertTrue(store.replay(LearningExperienceType.USER_FACT).isEmpty())
        assertFalse(dir.exists())
        val fresh = record(9900)
        FileLearningExperienceStore(dir).append(fresh)
        assertEquals(listOf(fresh), store.replay(LearningExperienceType.USER_FACT))
    }

    @Test fun returnedListsCannotMutateLaterReplaySelections() = fixture { _, store ->
        val before = store.replay(LearningExperienceType.USER_FACT, 128)
        val expected = before.toList()
        runCatching { (before as MutableList<LearningExperience>).clear() }
        val small = store.replay(LearningExperienceType.USER_FACT, 1)
        runCatching { (small as MutableList<LearningExperience>)[0] = record(9999) }
        assertEquals(expected, store.replay(LearningExperienceType.USER_FACT, 128))
        assertEquals(expected.takeLast(1), store.replay(LearningExperienceType.USER_FACT, 1))
    }

    @Test fun concurrentReadersAndForeignWriterNeverMixFingerprintAndRecords() = fixture { dir, store ->
        val pool = Executors.newFixedThreadPool(3)
        try {
            val readers = (0..1).map { pool.submit {
                repeat(30) {
                    val rows = store.replay(LearningExperienceType.USER_FACT, 128)
                    // Each updateMatching commits the complete type atomically.
                    assertTrue(rows.isEmpty() || rows.size == 128)
                    assertTrue(rows.all { it.status == LearningStatus.CONFIRMED })
                    assertTrue(rows.map { it.updatedAt }.distinct().size <= 1 || rows.all { it.updatedAt < 1000 })
                }
            } }
            val writer = pool.submit {
                val foreign = FileLearningExperienceStore(dir)
                repeat(12) { i ->
                    foreign.updateMatching(LearningExperienceType.USER_FACT, status = LearningStatus.REVOKED, now = 1000L + i * 2)
                    foreign.updateMatching(LearningExperienceType.USER_FACT, status = LearningStatus.CONFIRMED, now = 1001L + i * 2)
                }
            }
            (readers + writer).forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals(expected(store.all(), LearningExperienceType.USER_FACT, 128), store.replay(LearningExperienceType.USER_FACT, 128))
        } finally { pool.shutdownNow() }
    }
}
