package at.heinz.deviceai.research.neuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReviewedEvidenceGraphTest {
    private val L = ReviewedEvidenceGraph.Level
    private fun edge(id: String, subject: String, from: ReviewedEvidenceGraph.Level,
                     target: String, to: ReviewedEvidenceGraph.Level): ReviewedEvidenceGraph.Relation {
        val text = "$subject befindet sich in $target."
        val evidence = ReviewedEvidenceGraph.Evidence(
            id, text, ReviewedEvidenceGraph.digest(text), 0, text.length, true)
        return ReviewedEvidenceGraph.Relation(subject, from, target, to, evidence)
    }

    @Test fun reviewedThreeHopReasoningRemainsResearchOnly() {
        val g = ReviewedEvidenceGraph(listOf(
            edge("s1", "Z77", L.OBJECT, "Graz", L.CITY),
            edge("s2", "Graz", L.CITY, "Steiermark", L.REGION),
            edge("s3", "Steiermark", L.REGION, "Österreich", L.COUNTRY)
        ))
        val result = g.infer("Z77", L.OBJECT, L.COUNTRY)
        assertEquals(ReviewedEvidenceGraph.Status.SUPPORTED_REVIEWED, result.status)
        assertEquals("Österreich", result.resolvedValue)
        assertEquals(3, result.hops)
        assertEquals(3, result.supportingSourceIds.size)
        assertFalse(result.answerAuthority)
        assertFalse(result.mutatesMemory)
    }

    @Test fun independentDiamondPathsAreNotMistakenForCycles() {
        val g = ReviewedEvidenceGraph(listOf(
            edge("s1", "Z77", L.OBJECT, "Graz", L.CITY),
            edge("s2", "Z77", L.OBJECT, "Innsbruck", L.CITY),
            edge("s3", "Graz", L.CITY, "Österreich", L.COUNTRY),
            edge("s4", "Innsbruck", L.CITY, "Österreich", L.COUNTRY)
        ))
        val result = g.infer("Z77", L.OBJECT, L.COUNTRY)
        assertEquals(ReviewedEvidenceGraph.Status.SUPPORTED_REVIEWED, result.status)
        assertEquals("Österreich", result.resolvedValue)
        assertEquals(2, result.hops)
    }

    @Test fun conflictingSourcePathsCannotBecomeAnswer() {
        val g = ReviewedEvidenceGraph(listOf(
            edge("s1", "Z77", L.OBJECT, "Graz", L.CITY),
            edge("s2", "Z77", L.OBJECT, "Salzburg", L.CITY)
        ))
        val result = g.infer("Z77", L.OBJECT, L.CITY)
        assertEquals(ReviewedEvidenceGraph.Status.CONFLICTING_PATHS, result.status)
        assertEquals(null, result.resolvedValue)
        assertEquals(listOf("Graz", "Salzburg"), result.alternativeValues)
        assertTrue(result.supportingSourceIds.isEmpty())
    }

    @Test fun missingKnowledgeAbstains() {
        val g = ReviewedEvidenceGraph(emptyList())
        assertEquals(ReviewedEvidenceGraph.Status.NOT_SUPPORTED,
            g.infer("Z77", L.OBJECT, L.CITY).status)
    }

    @Test fun forgedOrUnreviewedEvidenceIsRejected() {
        val content = "Z77 befindet sich in Graz."
        try {
            ReviewedEvidenceGraph.Evidence("a", content, "0".repeat(64), 0, content.length, true)
            fail("Expected hash mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("HASH"))
        }
        try {
            ReviewedEvidenceGraph.Evidence("a", content, ReviewedEvidenceGraph.digest(content),
                0, content.length, false)
            fail("Expected review gate")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("UNREVIEWED"))
        }
    }

    @Test fun invalidRelationTypesCannotCreateCycles() {
        try {
            edge("a", "Graz", L.CITY, "Z77", L.OBJECT)
            fail("Expected level rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("INVALID_RELATION_LEVEL"))
        }
    }

    @Test fun quotationMustActuallyMentionBothEntities() {
        val content = "Z77 befindet sich in Graz."
        val evidence = ReviewedEvidenceGraph.Evidence(
            "a", content, ReviewedEvidenceGraph.digest(content), 0, content.length, true)
        try {
            ReviewedEvidenceGraph.Relation("Z77", L.OBJECT, "Innsbruck", L.CITY, evidence)
            fail("Expected unsupported relation rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("BOTH_ENTITIES"))
        }
    }

    @Test fun sourceCapRejectsUnboundedGraphs() {
        val edges = (1..129).map { i -> edge("source-$i", "Z$i", L.OBJECT, "Graz", L.CITY) }
        try {
            ReviewedEvidenceGraph(edges)
            fail("Expected graph cap rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("GRAPH_CAPACITY"))
        }
    }
}
