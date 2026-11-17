package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvenancePreservingConsolidationTest {
    private fun record(
        id:String,
        provenance:ProvenanceType=ProvenanceType.USER_PROVIDED,
        validation:ValidationState=ValidationState.VERIFIED
    ) = MemoryRecord(
        id=id,type=MemoryType.SEMANTIC,content="Beleg $id",provenance=provenance,
        confidence=.9,createdAt=1,updatedAt=1,validationState=validation,
        validationEvidenceReferences=if(validation==ValidationState.VERIFIED) listOf("proof:$id") else emptyList()
    )

    @Test fun summaryCanNeverServeAsHardProof() {
        val a=record("a")
        val summary=ProvenancePreservingConsolidation.plan("sum-1","Projektüberblick",listOf(a),10)
        assertFalse(summary.canServeAsHardProof)
        val resolved=ProvenancePreservingConsolidation.resolveHardEvidence(summary,listOf(a))
        assertTrue(resolved.complete)
        assertEquals(listOf("a"),resolved.hardEvidence.map { it.id })
    }

    @Test fun missingOrModelGeneratedSourcePreventsCompleteProofResolution() {
        val model=record("m",ProvenanceType.MODEL_GENERATED,ValidationState.VERIFIED)
        val good=record("g")
        val summary=ProvenancePreservingConsolidation.NavigationSummary(
            "sum-2","Navigation",listOf("g","m","missing"),10)
        val resolved=ProvenancePreservingConsolidation.resolveHardEvidence(summary,listOf(good,model))
        assertFalse(resolved.complete)
        assertEquals(listOf("g"),resolved.hardEvidence.map { it.id })
        assertEquals(listOf("missing"),resolved.missingSourceIds)
        assertEquals(listOf("m"),resolved.unverifiedSourceIds)
    }

    @Test fun unverifiedSourceNeverUpgradesThroughSummary() {
        val raw=record("raw",ProvenanceType.DIRECT_OBSERVATION,ValidationState.UNVERIFIED)
        val summary=ProvenancePreservingConsolidation.plan("sum-3","Zusammenfassung",listOf(raw),10)
        val resolved=ProvenancePreservingConsolidation.resolveHardEvidence(summary,listOf(raw))
        assertTrue(resolved.hardEvidence.isEmpty())
        assertEquals(listOf("raw"),resolved.unverifiedSourceIds)
    }
}
