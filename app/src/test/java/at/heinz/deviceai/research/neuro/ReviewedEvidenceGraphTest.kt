package at.heinz.deviceai.research.neuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import at.heinz.deviceai.research.neuro.ReviewedEvidenceGraph.Level

class ReviewedEvidenceGraphTest {
    private fun edge(id: String, subject: String, from: ReviewedEvidenceGraph.Level,
                     target: String, to: ReviewedEvidenceGraph.Level): ReviewedEvidenceGraph.Relation {
        val text = "$subject befindet sich in $target."
        val evidence = ReviewedEvidenceGraph.Evidence(
            id, text, ReviewedEvidenceGraph.digest(text), 0, text.length, true)
        return ReviewedEvidenceGraph.Relation(subject, from, target, to, evidence)
    }

    @Test fun reviewedThreeHopReasoningRemainsResearchOnly() {
        val g = ReviewedEvidenceGraph(listOf(
            edge("s1", "Z77", Level.OBJECT, "Graz", Level.CITY),
            edge("s2", "Graz", Level.CITY, "Steiermark", Level.REGION),
            edge("s3", "Steiermark", Level.REGION, "Österreich", Level.COUNTRY)
        ))
        val result = g.infer("Z77", Level.OBJECT, Level.COUNTRY)
        assertEquals(ReviewedEvidenceGraph.Status.SUPPORTED_REVIEWED, result.status)
        assertEquals("Österreich", result.resolvedValue)
        assertEquals(3, result.hops)
        assertEquals(3, result.supportingSourceIds.size)
        assertFalse(result.answerAuthority)
        assertFalse(result.mutatesMemory)
    }

    @Test fun independentDiamondPathsAreNotMistakenForCycles() {
        val g = ReviewedEvidenceGraph(listOf(
            edge("s1", "Z77", Level.OBJECT, "Graz", Level.CITY),
            edge("s2", "Z77", Level.OBJECT, "Innsbruck", Level.CITY),
            edge("s3", "Graz", Level.CITY, "Österreich", Level.COUNTRY),
            edge("s4", "Innsbruck", Level.CITY, "Österreich", Level.COUNTRY)
        ))
        val result = g.infer("Z77", Level.OBJECT, Level.COUNTRY)
        assertEquals(ReviewedEvidenceGraph.Status.SUPPORTED_REVIEWED, result.status)
        assertEquals("Österreich", result.resolvedValue)
        assertEquals(2, result.hops)
    }

    @Test fun conflictingSourcePathsCannotBecomeAnswer() {
        val g = ReviewedEvidenceGraph(listOf(
            edge("s1", "Z77", Level.OBJECT, "Graz", Level.CITY),
            edge("s2", "Z77", Level.OBJECT, "Salzburg", Level.CITY)
        ))
        val result = g.infer("Z77", Level.OBJECT, Level.CITY)
        assertEquals(ReviewedEvidenceGraph.Status.CONFLICTING_PATHS, result.status)
        assertEquals(null, result.resolvedValue)
        assertEquals(listOf("Graz", "Salzburg"), result.alternativeValues)
        assertTrue(result.supportingSourceIds.isEmpty())
    }

    @Test fun missingKnowledgeAbstains() {
        val g = ReviewedEvidenceGraph(emptyList())
        assertEquals(ReviewedEvidenceGraph.Status.NOT_SUPPORTED,
            g.infer("Z77", Level.OBJECT, Level.CITY).status)
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
            edge("a", "Graz", Level.CITY, "Z77", Level.OBJECT)
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
            ReviewedEvidenceGraph.Relation("Z77", Level.OBJECT, "Innsbruck", Level.CITY, evidence)
            fail("Expected unsupported relation rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("BOTH_ENTITIES"))
        }
    }

    @Test fun sourceCapRejectsUnboundedGraphs() {
        val edges = (1..129).map { i -> edge("source-$i", "Z$i", Level.OBJECT, "Graz", Level.CITY) }
        try {
            ReviewedEvidenceGraph(edges)
            fail("Expected graph cap rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("GRAPH_CAPACITY"))
        }
    }

    @Test fun negativeQuotedStatementCannotBecomePositiveEvidence() {
        val negative = "Z77 befindet sich nicht in Graz."
        val evidence = ReviewedEvidenceGraph.Evidence(
            "negative", negative, ReviewedEvidenceGraph.digest(negative),
            0, negative.length, true
        )
        try {
            ReviewedEvidenceGraph.Relation("Z77", Level.OBJECT, "Graz", Level.CITY, evidence)
            fail("Explicit negation must not count as containment")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("NEGATED_RELATION_EVIDENCE"))
        }
    }

    @Test fun substringOfDifferentEntityIsNotEvidence() {
        val unrelated = "Z77 befindet sich in Grazer Bezirk."
        val evidence = ReviewedEvidenceGraph.Evidence(
            "unrelated", unrelated, ReviewedEvidenceGraph.digest(unrelated),
            0, unrelated.length, true
        )
        try {
            ReviewedEvidenceGraph.Relation("Z77", Level.OBJECT, "Graz", Level.CITY, evidence)
            fail("Partial entity must not be a grounded relation")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("BOTH_ENTITIES"))
        }
    }

    @Test fun positiveReviewedContainmentStillPasses() {
        val relation = edge("positive", "Z77", Level.OBJECT, "Graz", Level.CITY)
        assertEquals("Graz", ReviewedEvidenceGraph(listOf(relation))
            .infer("Z77", Level.OBJECT, Level.CITY).resolvedValue)
    }
}
