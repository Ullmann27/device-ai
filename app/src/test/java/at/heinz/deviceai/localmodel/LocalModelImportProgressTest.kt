package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelImportProgressTest {
    @Test fun validImportHasOrderedStagesAndBoundedElapsedTime() {
        var nanos = 0L
        val progress = LocalModelImportProgress { nanos }
        assertNull(progress.snapshot())
        val stages = listOf(LocalModelImportPhase.STREAM_OPENED, LocalModelImportPhase.COPYING,
            LocalModelImportPhase.COPY_COMPLETED, LocalModelImportPhase.HASH_VERIFIED,
            LocalModelImportPhase.INSPECTED, LocalModelImportPhase.COMMITTED)
        stages.forEachIndexed { i, phase ->
            nanos += 2_000_000L
            val snapshot = progress.record(phase, if (i < 2) 0 else 27)
            assertEquals(phase, snapshot.phase)
            assertEquals((i+1)*2L, snapshot.elapsedMs)
        }
        assertEquals(27L, progress.snapshot()!!.copiedBytes)
    }

    @Test fun repeatedCopyUpdatesAreAllowedAndNeverDecrement() {
        val p=LocalModelImportProgress()
        p.record(LocalModelImportPhase.STREAM_OPENED,0)
        p.record(LocalModelImportPhase.COPYING,0)
        p.record(LocalModelImportPhase.COPYING,128)
        p.record(LocalModelImportPhase.COPYING,256)
        assertThrows(IllegalArgumentException::class.java) {p.record(LocalModelImportPhase.COPYING,255)}
        assertEquals(256L,p.snapshot()!!.copiedBytes)
    }

    @Test fun cannotSkipHashVerificationOrInspect() {
        val p=LocalModelImportProgress()
        p.record(LocalModelImportPhase.STREAM_OPENED,0)
        assertThrows(IllegalArgumentException::class.java) {p.record(LocalModelImportPhase.HASH_VERIFIED,300)}
        p.record(LocalModelImportPhase.COPYING,16)
        p.record(LocalModelImportPhase.COPY_COMPLETED,16)
        assertThrows(IllegalArgumentException::class.java) {p.record(LocalModelImportPhase.COMMITTED,16)}
    }

    @Test fun invalidNegativeAndOversizedCountersNeverReplaceLastProof() {
        val p=LocalModelImportProgress()
        p.record(LocalModelImportPhase.STREAM_OPENED,0)
        assertThrows(IllegalArgumentException::class.java) {p.record(LocalModelImportPhase.COPYING,-1)}
        assertThrows(IllegalArgumentException::class.java) {p.record(LocalModelImportPhase.COPYING,65L*1024*1024*1024)}
        assertEquals(LocalModelImportPhase.STREAM_OPENED,p.snapshot()!!.phase)
    }

    @Test fun failureIsTerminalAndAllowsNoSubsequentSuccess() {
        val p=LocalModelImportProgress()
        p.record(LocalModelImportPhase.STREAM_OPENED,0)
        p.record(LocalModelImportPhase.FAILED,0)
        assertThrows(IllegalArgumentException::class.java) {p.record(LocalModelImportPhase.COMMITTED,1)}
    }

    @Test fun successIsTerminalAndCannotBeRewrittenAsFailure() {
        val p=LocalModelImportProgress()
        p.record(LocalModelImportPhase.STREAM_OPENED,0)
        p.record(LocalModelImportPhase.COPYING,4)
        p.record(LocalModelImportPhase.COPY_COMPLETED,4)
        p.record(LocalModelImportPhase.HASH_VERIFIED,4)
        p.record(LocalModelImportPhase.INSPECTED,4)
        p.record(LocalModelImportPhase.COMMITTED,4)
        assertThrows(IllegalArgumentException::class.java) {p.record(LocalModelImportPhase.FAILED,4)}
    }

    @Test fun emptyModelMayNotBeMarkedHashed() {
        val p=LocalModelImportProgress()
        p.record(LocalModelImportPhase.STREAM_OPENED,0)
        p.record(LocalModelImportPhase.COPYING,0)
        assertThrows(IllegalArgumentException::class.java) {p.record(LocalModelImportPhase.COPY_COMPLETED,0)}
    }

    @Test fun clockReversalCannotCreateNegativeElapsedTime() {
        var nanos=10_000_000L
        val p=LocalModelImportProgress { nanos }
        nanos=9_000_000L
        assertEquals(0L,p.record(LocalModelImportPhase.STREAM_OPENED,0).elapsedMs)
        nanos=12_000_000L
        assertEquals(2L,p.record(LocalModelImportPhase.COPYING,0).elapsedMs)
        nanos=10_500_000L
        assertEquals(2L,p.record(LocalModelImportPhase.COPYING,3).elapsedMs)
    }

    @Test fun noFileUriCredentialOrPayloadFieldsAreStored() {
        val p=LocalModelImportProgress()
        val s=p.record(LocalModelImportPhase.STREAM_OPENED,0)
        assertEquals(3,s.javaClass.declaredFields.size)
        assertTrue(s.toString().contains("STREAM_OPENED"))
        assertTrue(s.toString().contains("copiedBytes"))
    }
}
