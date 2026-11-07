package at.heinz.deviceai.learning.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceBackedRelationGraphTest {
    @Test fun onlyVerifiedEvidenceEdgesReachRoleResolver() {
        val admitted=EvidenceBackedRelationGraph.admitted(listOf(
            EvidenceBackedRelationGraph.EvidenceEdge(
                "alina","SISTER","zoey",setOf("verified-memory"),verified=true),
            EvidenceBackedRelationGraph.EvidenceEdge(
                "alina","SISTER","invented",setOf("model-hypothesis"),verified=false)
        ))
        assertEquals(1,admitted.size)
        assertEquals("zoey",admitted.single().toId)
        assertEquals(setOf("verified-memory"),admitted.single().sourceIds)
    }

    @Test fun duplicateRelationEdgesCollapseWithoutLosingFirstEvidenceBoundary() {
        val admitted=EvidenceBackedRelationGraph.admitted(listOf(
            EvidenceBackedRelationGraph.EvidenceEdge(
                "a","RELATED_TO","b",setOf("s1"),true),
            EvidenceBackedRelationGraph.EvidenceEdge(
                "a","related_to","b",setOf("s2"),true)
        ))
        assertEquals(1,admitted.size)
        assertTrue(admitted.single().sourceIds.isNotEmpty())
    }
}
