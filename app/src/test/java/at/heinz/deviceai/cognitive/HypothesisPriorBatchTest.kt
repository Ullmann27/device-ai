package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HypothesisPriorBatchTest {
    private fun h(id: String, weight: Float = 1f, bucket: HypothesisWorkspace.Bucket = HypothesisWorkspace.Bucket.OTHER) =
        HypothesisWorkspace.Hypothesis(id, bucket, "Synthetic $id", weight)

    @Test fun unequalPriorRatiosArePreservedWithoutChangingLegacyAdd() {
        val batch = HypothesisWorkspace()
        assertTrue(batch.admitPriors(listOf(h("a", 2f), h("b", 6f))))
        assertEquals(0.25f, batch.snapshot().first { it.id == "a" }.weight, 1e-6f)
        val sequential = HypothesisWorkspace()
        assertTrue(sequential.add(h("a", 2f)))
        assertTrue(sequential.add(h("b", 6f)))
        assertEquals(1f / 7f, sequential.snapshot().first { it.id == "a" }.weight, 1e-6f)
    }

    @Test fun capacityDuplicatesAndBucketOverflowAreAtomicRejections() {
        val workspace = HypothesisWorkspace(maxHypotheses = 3, maxPerBucket = 2)
        for (invalid in listOf(emptyList(), listOf(h("a"), h("a")),
            listOf(h("a"), h("b"), h("c")), List(4) { h("h$it") })) {
            assertFalse(workspace.admitPriors(invalid))
            assertTrue(workspace.snapshot().isEmpty())
        }
        assertTrue(workspace.admitPriors(listOf(h("a"), h("b"))))
        val before = workspace.snapshot()
        assertFalse(workspace.admitPriors(listOf(h("c"))))
        assertEquals(before, workspace.snapshot())
    }

    @Test fun callerOwnedHypothesesAndEvidenceCannotMutateAdmittedPriors() {
        val a = h("a")
        val b = h("b")
        val workspace = HypothesisWorkspace()
        assertTrue(workspace.admitPriors(listOf(a, b)))
        a.weight = 100f
        a.supportingEvidence += "unverified caller mutation"
        b.contradictingEvidence += "another mutation"
        assertTrue(workspace.snapshot().all {
            it.weight == 0.5f && it.supportingEvidence.isEmpty() && it.contradictingEvidence.isEmpty()
        })
    }

    @Test fun largeFinitePriorsDoNotOverflowTheNormalizer() {
        val workspace = HypothesisWorkspace()
        assertTrue(workspace.admitPriors(listOf(h("a", Float.MAX_VALUE), h("b", Float.MAX_VALUE))))
        assertEquals(listOf(0.5f, 0.5f), workspace.snapshot().map { it.weight })
    }

    @Test fun mutatedInvalidWeightsAndUnrepresentableRatiosCannotPartiallyAdmit() {
        val invalid = h("invalid").apply { weight = Float.NaN }
        for (batch in listOf(listOf(h("valid"), invalid),
            listOf(h("tiny", Float.MIN_VALUE), h("large", Float.MAX_VALUE)))) {
            val workspace = HypothesisWorkspace()
            var rejected = false
            try {
                workspace.admitPriors(batch)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertTrue(workspace.snapshot().isEmpty())
        }
    }

    @Test fun evidenceAndProbeCostsStillDetermineTheNextStep() {
        val workspace = HypothesisWorkspace()
        assertTrue(workspace.admitPriors(listOf(h("a"), h("b"))))
        val cheap = HypothesisWorkspace.Probe("cheap", 1f, 0f, mapOf("a" to true, "b" to false))
        val costly = cheap.copy(id = "costly", latencyCostMs = 100f, thermalCost = 1f)
        assertEquals("cheap", workspace.selectNextProbe(listOf(costly, cheap))?.id)
        workspace.observe(cheap, false, "external synthetic observation")
        assertEquals("b", workspace.best()?.id)
        assertTrue(workspace.best()!!.supportingEvidence.contains("external synthetic observation"))
    }
}
