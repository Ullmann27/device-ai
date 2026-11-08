package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HypothesisRobustProbeTest {
    private fun workspace(): HypothesisWorkspace = HypothesisWorkspace().apply {
        admitPriors(listOf(
            HypothesisWorkspace.Hypothesis("a", HypothesisWorkspace.Bucket.DATA, "A", 1f),
            HypothesisWorkspace.Hypothesis("b", HypothesisWorkspace.Bucket.CONFIG, "B", 1f)
        ))
    }
    private fun probes(): List<HypothesisWorkspace.Probe> = (0..2).map {
        HypothesisWorkspace.Probe("p$it", 1f, 0f, mapOf("a" to true, "b" to false))
    }

    @Test fun threeProbesCertifyDespiteOneWrongObservationAndWrongPrior() {
        val workspace = HypothesisWorkspace()
        val ids = listOf("a", "b", "c")
        assertTrue(workspace.admitPriors(ids.mapIndexed { index, id ->
            HypothesisWorkspace.Hypothesis(id, HypothesisWorkspace.Bucket.entries[index],
                "Synthetic $id", if (id == "b") 100f else 1f)
        }))
        val probes = (0..2).map {
            HypothesisWorkspace.Probe("a-$it", 1f, 0f, ids.associateWith { it == "a" })
        } + HypothesisWorkspace.Probe("cheap-b", 0.5f, 0f, ids.associateWith { it == "b" })
        val observed = mutableListOf<HypothesisWorkspace.ProbeObservation>()
        repeat(3) { step ->
            val chosen = checkNotNull(workspace.selectCertificateProbe(probes, observed, 3 - step, 9f)) {
                "No probe after $observed"
            }
            val correct = chosen.expectedByHypothesis.getValue("a")
            val result = if (step == 1) !correct else correct
            observed += HypothesisWorkspace.ProbeObservation(chosen.id, result)
            workspace.observe(chosen, result, "synthetic")
        }
        val compatible = ids.filter { id ->
            observed.count { observation ->
                probes.single { it.id == observation.probeId }.expectedByHypothesis[id] != observation.result
            } <= 1
        }
        assertEquals(listOf("a"), compatible)
    }

    @Test fun missingIsNotNegativeEvidenceAndWithdrawalRebuildsTheView() {
        val w = workspace()
        val p = probes()
        val seen = listOf(HypothesisWorkspace.ProbeObservation("p0", null))
        val before = w.snapshot()
        assertTrue(w.selectCertificateProbe(p, seen, 2, 2f)?.id in setOf("p1", "p2"))
        assertEquals(before, w.snapshot())
        assertEquals("p0", w.selectCertificateProbe(p, emptyList(), 3, 3f)?.id)
    }

    @Test fun invalidReceiptsAndBoundsAreExplicitlyRejected() {
        val w = workspace()
        val p = probes()
        val invalid = listOf(
            listOf(HypothesisWorkspace.ProbeObservation("unknown", true)),
            listOf(HypothesisWorkspace.ProbeObservation("p0", true), HypothesisWorkspace.ProbeObservation("p0", false))
        )
        invalid.forEach { seen ->
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                w.selectCertificateProbe(p, seen, 1, 3f)
            }
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            w.selectCertificateProbe(p, emptyList(), 4, 3f)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            w.selectCertificateProbe(p, emptyList(), 3, Float.NaN)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            w.selectCertificateProbe(p + p.first(), emptyList(), 3, 3f)
        }
        assertEquals(null, w.selectCertificateProbe(p, emptyList(), 3, 0f))
    }

    @Test fun ambiguousModelsAndAbsentPredictionsCannotCreateCertificates() {
        val p = probes().map { it.copy(expectedByHypothesis = mapOf("a" to true, "b" to true)) }
        assertEquals(null, workspace().selectCertificateProbe(p, emptyList(), 3, 3f))
        assertEquals(null, workspace().selectCertificateProbe(
            probes().map { it.copy(expectedByHypothesis = emptyMap()) }, emptyList(), 3, 3f))
    }

    @Test fun twoErrorsAreAnExplicitCounterexampleToUnboundedRobustness() {
        val seen = listOf(false, false, true)
        val compatible = listOf(true, false).filter { expected -> seen.count { it != expected } <= 1 }
        assertEquals(listOf(false), compatible)
        // True was the real cause: two flips violate the declared one-error assumption.
        assertTrue(true !in compatible)
    }
}
