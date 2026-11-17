package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumLocalContrastiveProbeTest {
    @Test fun publicDiagnosticRunsWithoutChangingProductionSnapshot() {
        val baseline=TrainableSubwordEncoder(73L)
        val before=baseline.save()
        val result=ContinuumLocalContrastiveProbe.run(before,steps=60)
        assertTrue(result.completed)
        assertEquals(60,result.steps)
        assertArrayEquals(before,baseline.save())
        assertEquals(64,result.baselineHash.length)
        assertEquals(64,result.candidateHash.length)
        assertTrue(result.display().contains("keine Gewichtsübernahme"))
        assertTrue(result.holdoutAfter.reciprocalRank in 0.0..1.0)
    }
    @Test fun cancellationDoesNotReplaceWeightsOrClaimCompletion() {
        val snapshot=TrainableSubwordEncoder(21L).save()
        val result=ContinuumLocalContrastiveProbe.run(snapshot,cancelled={true},steps=100)
        assertFalse(result.completed)
        assertEquals(0,result.steps)
        assertEquals(result.baselineHash,result.candidateHash)
        assertArrayEquals(snapshot,TrainableSubwordEncoder.restore(snapshot).save())
    }
    @Test fun jobStatusDisplayDistinguishesUnscheduledAndFailure() {
        val missing=ContinuumOfflineJobSnapshot(null,"NOT_RUN",0,0,"").display()
        assertTrue(missing.contains("nicht geprüft"))
        val bad=ContinuumOfflineJobSnapshot(false,"FAILED",0,1000,"IllegalStateException").display()
        assertTrue(bad.contains("Einplanung fehlgeschlagen"))
        assertTrue(bad.contains("IllegalStateException"))
    }
}
