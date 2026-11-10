package at.heinz.deviceai.systemintelligence

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OwnDiagnosticCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun record(time: Long = 1_000) = DiagnosticCacheRecord(time, 999_000L, 123_000L, 70, 0)
    private fun fixture(): Triple<OwnDiagnosticCache, File, File> {
        val cache = temporary.newFolder("cache")
        val owner = temporary.newFolder("owner")
        return Triple(OwnDiagnosticCache(cache, owner), cache, owner)
    }

    @Test fun onlyActualOwnedNumericHistoryIsRemovedAndRestorable() {
        val (cleaner, cache) = fixture()
        val id = cleaner.retain(record())
        val protected = listOf("litertlm/weights.bin", "owner-update/update.apk", "photo.jpg", "memory.json",
            "system-diagnostics/unknown.cdiag", "system-diagnostics/backup.zip")
        protected.forEach { File(cache, it).apply { parentFile.mkdirs(); writeText("must preserve") } }
        val preview = cleaner.preview()
        assertEquals(listOf(id), preview.entries.map { it.id })
        assertTrue(preview.protectedFileCount >= 6)
        val result = cleaner.cleanup(preview.previewId, preview.planDigest)
        assertEquals("COMPLETED", result.status)
        assertEquals(1, result.removedFiles)
        assertEquals(preview.eligibleBytes, result.logicalBytesRemoved)
        protected.forEach { assertEquals("must preserve", File(cache, it).readText()) }
        assertTrue(cleaner.history().isEmpty())
        assertEquals("RESTORED", cleaner.restore(result.undoId!!).status)
        assertEquals(listOf(record()), cleaner.history())
    }

    @Test fun unownedFileWithPerfectSchemaIsProtected() {
        val (cleaner, cache) = fixture()
        val id = cleaner.retain(record())
        val original = File(cache, "system-diagnostics/summary-$id.cdiag")
        val copied = File(cache, "system-diagnostics/summary-${"a".repeat(32)}.cdiag")
        original.copyTo(copied)
        val preview = cleaner.preview()
        assertEquals(1, preview.entries.size)
        assertEquals("COMPLETED", cleaner.cleanup(preview.previewId, preview.planDigest).status)
        assertTrue(copied.exists())
    }

    @Test fun sameLengthTamperRejectsWholePlan() {
        val (cleaner, cache) = fixture()
        val id = cleaner.retain(record())
        cleaner.retain(record(2_000))
        val preview = cleaner.preview()
        val file = File(cache, "system-diagnostics/summary-$id.cdiag")
        file.writeText(file.readText().replace("999000", "888000"))
        assertEquals("STALE_PREVIEW", cleaner.cleanup(preview.previewId, preview.planDigest).status)
        assertEquals(2, File(cache, "system-diagnostics").listFiles()!!.size)
    }

    @Test fun sameContentReplacementIsNotOldPreview() {
        val (cleaner, cache) = fixture()
        val id = cleaner.retain(record())
        val preview = cleaner.preview()
        val file = File(cache, "system-diagnostics/summary-$id.cdiag")
        val bytes = file.readBytes()
        val modified = file.lastModified()
        val replacement = File(cache, "replacement").apply { writeBytes(bytes); setLastModified(modified) }
        Files.move(replacement.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        assertEquals("STALE_PREVIEW", cleaner.cleanup(preview.previewId, preview.planDigest).status)
        assertTrue(file.exists())
    }

    @Test fun symlinkReplacementNeverTouchesTarget() {
        val (cleaner, cache) = fixture()
        val id = cleaner.retain(record())
        val preview = cleaner.preview()
        val file = File(cache, "system-diagnostics/summary-$id.cdiag")
        val outside = temporary.newFile("model.bin").apply { writeText("preserve model") }
        assertTrue(file.delete())
        Files.createSymbolicLink(file.toPath(), outside.toPath())
        assertEquals("STALE_PREVIEW", cleaner.cleanup(preview.previewId, preview.planDigest).status)
        assertEquals("preserve model", outside.readText())
    }

    @Test fun directorySymlinkCannotBecomeCleanupRoot() {
        val (cleaner, cache) = fixture()
        val id = cleaner.retain(record())
        val preview = cleaner.preview()
        val directory = File(cache, "system-diagnostics")
        val outside = temporary.newFolder("outside")
        val original = File(directory, "summary-$id.cdiag")
        original.copyTo(File(outside, original.name))
        assertTrue(original.delete())
        assertTrue(directory.delete())
        Files.createSymbolicLink(directory.toPath(), outside.toPath())
        assertEquals("STALE_PREVIEW", cleaner.cleanup(preview.previewId, preview.planDigest).status)
        assertEquals(1, outside.listFiles()!!.size)
    }

    @Test fun exactPreviewIsOneUseAndForgeryConsumesIt() {
        val (cleaner) = fixture()
        cleaner.retain(record())
        val preview = cleaner.preview()
        assertEquals("PREVIEW_MISMATCH", cleaner.cleanup(preview.previewId, "wrong").status)
        assertEquals("PREVIEW_REQUIRED", cleaner.cleanup(preview.previewId, preview.planDigest).status)
        assertEquals(1, cleaner.history().size)
    }

    @Test fun previewExpiresWithoutDeletingHistory() {
        val cache = temporary.newFolder("cache")
        val owner = temporary.newFolder("owner")
        var now = 1_000L
        val cleaner = OwnDiagnosticCache(cache, owner) { now }
        cleaner.retain(record())
        val preview = cleaner.preview()
        now += OwnDiagnosticCache.PREVIEW_TTL_MS
        assertEquals("PREVIEW_EXPIRED", cleaner.cleanup(preview.previewId, preview.planDigest).status)
        assertEquals(1, cleaner.history().size)
    }

    @Test fun newDiagnosisInvalidatesOldPreview() {
        val (cleaner) = fixture()
        cleaner.retain(record())
        val preview = cleaner.preview()
        cleaner.retain(record(2_000))
        assertEquals("PREVIEW_REQUIRED", cleaner.cleanup(preview.previewId, preview.planDigest).status)
        assertEquals(2, cleaner.history().size)
    }

    @Test fun cancellationPreservesAllFiles() {
        val (cleaner) = fixture()
        cleaner.retain(record())
        val preview = cleaner.preview()
        assertEquals("CANCELLED", cleaner.cleanup(preview.previewId, preview.planDigest) { true }.status)
        assertEquals(1, cleaner.history().size)
    }

    @Test fun emptyCacheDoesNotInventSavings() {
        val (cleaner) = fixture()
        val preview = cleaner.preview()
        val result = cleaner.cleanup(preview.previewId, preview.planDigest)
        assertEquals(0L, preview.eligibleBytes)
        assertEquals("NO_AUDITED_DISPOSABLE_FILES", result.status)
        assertEquals(0L, result.logicalBytesRemoved)
        assertNull(result.availableBytesDelta)
    }

    @Test fun changedFileBlocksUndoWithoutOverwriting() {
        val (cleaner, cache) = fixture()
        val id = cleaner.retain(record())
        val preview = cleaner.preview()
        val result = cleaner.cleanup(preview.previewId, preview.planDigest)
        val target = File(cache, "system-diagnostics/summary-$id.cdiag")
        target.writeText("new personal document")
        assertEquals("RESTORE_TARGET_EXISTS", cleaner.restore(result.undoId!!).status)
        assertEquals("new personal document", target.readText())
    }

    @Test fun independentInstanceRecoversOwnershipWithoutBroadClassification() {
        val (cleaner, cache, owner) = fixture()
        cleaner.retain(record())
        val resumed = OwnDiagnosticCache(cache, owner)
        assertEquals(listOf(record()), resumed.history())
        assertEquals(1, resumed.preview().entries.size)
    }

    @Test fun historyGrowthIsBoundedAndNoAutomaticEviction() {
        val (cleaner) = fixture()
        repeat(OwnDiagnosticCache.MAX_RECORDS) { cleaner.retain(record(it.toLong())) }
        assertThrows(IllegalStateException::class.java) { cleaner.retain(record(99)) }
        assertEquals(OwnDiagnosticCache.MAX_RECORDS, cleaner.history().size)
    }

    @Test fun invalidMetricsCannotBecomeCacheContent() {
        assertThrows(IllegalArgumentException::class.java) { DiagnosticCacheRecord(1, -1, null, 10, 0) }
        assertThrows(IllegalArgumentException::class.java) { DiagnosticCacheRecord(1, 1, null, 101, 0) }
        assertThrows(IllegalArgumentException::class.java) { DiagnosticCacheRecord(1, 1, null, 50, 7) }
    }

    @Test fun androidCacheEvictionDoesNotPermanentlyFillHistory() {
        val (cleaner, cache) = fixture()
        repeat(OwnDiagnosticCache.MAX_RECORDS) { cleaner.retain(record(it.toLong())) }
        File(cache, "system-diagnostics").listFiles()!!.forEach { assertTrue(it.delete()) }
        assertTrue(cleaner.preview().entries.isEmpty())
        cleaner.retain(record(999))
        assertEquals(listOf(record(999)), cleaner.history())
    }

    @Test fun stopWhileWaitingForMonitorCannotRetainSnapshot() {
        val (cleaner, cache) = fixture()
        val started = CountDownLatch(1)
        val outcome = AtomicReference<Throwable?>()
        val caller = Thread {
            started.countDown()
            try { cleaner.retain(record()) } catch (failure: Throwable) { outcome.set(failure) }
        }
        synchronized(cleaner) {
            caller.start()
            assertTrue(started.await(1, TimeUnit.SECONDS))
            caller.interrupt()
        }
        caller.join(1_000)
        assertFalse(caller.isAlive)
        assertEquals("CACHE_OPERATION_CANCELLED", outcome.get()?.message)
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun stopWhileWaitingForMonitorCannotRestoreSnapshot() {
        val (cleaner) = fixture()
        cleaner.retain(record())
        val preview = cleaner.preview()
        val cleanup = cleaner.cleanup(preview.previewId, preview.planDigest)
        val started = CountDownLatch(1)
        val outcome = AtomicReference<OwnCacheCleanupResult?>()
        val caller = Thread {
            started.countDown()
            outcome.set(cleaner.restore(cleanup.undoId!!))
        }
        synchronized(cleaner) {
            caller.start()
            assertTrue(started.await(1, TimeUnit.SECONDS))
            caller.interrupt()
        }
        caller.join(1_000)
        assertFalse(caller.isAlive)
        assertEquals("CANCELLED", outcome.get()?.status)
        assertTrue(cleaner.history().isEmpty())
    }

    @Test fun partialRestorePreservesRemainingUndo() {
        val (cleaner) = fixture()
        repeat(3) { cleaner.retain(record(it.toLong())) }
        val preview = cleaner.preview()
        val cleanup = cleaner.cleanup(preview.previewId, preview.planDigest)
        var checks = 0
        val partial = cleaner.restore(cleanup.undoId!!) { ++checks > 2 }
        assertEquals("RESTORE_CANCELLED_PARTIAL", partial.status)
        assertEquals(1, partial.restoredFiles)
        assertEquals(1, cleaner.history().size)
        assertEquals("RESTORED", cleaner.restore(cleanup.undoId).status)
        assertEquals(3, cleaner.history().size)
    }

    private fun failRestoreIndex(cleaner: OwnDiagnosticCache, owner: File, undoId: String): OwnCacheCleanupResult {
        val index = File(owner, "system-diagnostic-cache-v1.index")
        val held = File(owner, "held-index")
        var calls = 0
        try {
            return cleaner.restore(undoId) {
                if (++calls == 2) {
                    assertTrue(index.renameTo(held))
                    assertTrue(index.mkdir())
                }
                false
            }
        } finally {
            if (held.exists()) {
                assertTrue(index.delete())
                assertTrue(held.renameTo(index))
            }
        }
    }

    @Test fun failedRestoreIndexKeepsBackupAndRetriesOwnership() {
        val (cleaner, cache, owner) = fixture()
        val id = cleaner.retain(record())
        val preview = cleaner.preview()
        val cleanup = cleaner.cleanup(preview.previewId, preview.planDigest)
        val failure = failRestoreIndex(cleaner, owner, cleanup.undoId!!)
        assertEquals("RESTORE_INDEX_FAILED", failure.status)
        assertEquals(1, failure.restoredFiles)
        val file = File(cache, "system-diagnostics/summary-$id.cdiag")
        val restoredBytes = file.readBytes()
        val restoredIdentity = Files.readAttributes(file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey()
        assertTrue(cleaner.history().isEmpty())
        assertThrows(IllegalStateException::class.java) { cleaner.retain(record(2_000)) }
        val blockedPreview = cleaner.preview()
        assertEquals("RESTORE_INDEX_RECOVERY_REQUIRED", cleaner.cleanup(blockedPreview.previewId, blockedPreview.planDigest).status)
        assertEquals("CANCELLED", cleaner.restore(cleanup.undoId) { true }.status)
        assertEquals("RESTORED", cleaner.restore(cleanup.undoId).status)
        assertEquals(listOf(record()), cleaner.history())
        assertArrayEquals(restoredBytes, file.readBytes())
        assertEquals(restoredIdentity, Files.readAttributes(file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey())
        assertEquals("UNDO_UNAVAILABLE", cleaner.restore(cleanup.undoId).status)
    }

    @Test fun failedIndexRetryPreservesChangedTarget() {
        val (cleaner, cache, owner) = fixture()
        val id = cleaner.retain(record())
        val preview = cleaner.preview()
        val cleanup = cleaner.cleanup(preview.previewId, preview.planDigest)
        assertEquals("RESTORE_INDEX_FAILED", failRestoreIndex(cleaner, owner, cleanup.undoId!!).status)
        val file = File(cache, "system-diagnostics/summary-$id.cdiag")
        file.writeText("new private content")
        assertEquals("RESTORE_PENDING_CHANGED", cleaner.restore(cleanup.undoId).status)
        assertEquals("new private content", file.readText())
        assertTrue(cleaner.history().isEmpty())
    }

    @Test fun failedIndexRetryRejectsSameContentReplacementInode() {
        val (cleaner, cache, owner) = fixture()
        val id = cleaner.retain(record())
        val preview = cleaner.preview()
        val cleanup = cleaner.cleanup(preview.previewId, preview.planDigest)
        assertEquals("RESTORE_INDEX_FAILED", failRestoreIndex(cleaner, owner, cleanup.undoId!!).status)
        val file = File(cache, "system-diagnostics/summary-$id.cdiag")
        val bytes = file.readBytes()
        val replacement = File(cache, "replacement").apply { writeBytes(bytes); setLastModified(file.lastModified()) }
        Files.move(replacement.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        assertEquals("RESTORE_PENDING_CHANGED", cleaner.restore(cleanup.undoId).status)
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(cleaner.history().isEmpty())
    }

    @Test fun evictedPendingRestoreCanBeRecreatedWithoutOverwrite() {
        val (cleaner, cache, owner) = fixture()
        val id = cleaner.retain(record())
        val preview = cleaner.preview()
        val cleanup = cleaner.cleanup(preview.previewId, preview.planDigest)
        assertEquals("RESTORE_INDEX_FAILED", failRestoreIndex(cleaner, owner, cleanup.undoId!!).status)
        assertTrue(File(cache, "system-diagnostics/summary-$id.cdiag").delete())
        assertEquals("RESTORED", cleaner.restore(cleanup.undoId).status)
        assertEquals(listOf(record()), cleaner.history())
    }
    @Test fun expiredIndexRecoveryNeverBlocksNewRetentionOrCleanup() {
        for (retainFirst in listOf(true, false)) {
            var now = 1_000L
            val cache = temporary.newFolder()
            val owner = temporary.newFolder()
            val cleaner = OwnDiagnosticCache(cache, owner) { now }
            val id = cleaner.retain(record())
            val preview = cleaner.preview()
            val cleanup = cleaner.cleanup(preview.previewId, preview.planDigest)
            assertEquals("RESTORE_INDEX_FAILED", failRestoreIndex(cleaner, owner, cleanup.undoId!!).status)
            val old = File(cache, "system-diagnostics/summary-$id.cdiag")
            val bytes = old.readBytes()
            now += OwnDiagnosticCache.UNDO_TTL_MS
            if (retainFirst) {
                cleaner.retain(record(2_000))
                assertEquals(listOf(record(2_000)), cleaner.history())
            } else {
                val fresh = cleaner.preview()
                assertEquals("NO_AUDITED_DISPOSABLE_FILES", cleaner.cleanup(fresh.previewId, fresh.planDigest).status)
            }
            assertArrayEquals(bytes, old.readBytes())
            assertEquals("UNDO_UNAVAILABLE", cleaner.restore(cleanup.undoId).status)
        }
    }

}
