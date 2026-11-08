package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HypothesisRobustProbeTest {
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
            val chosen = workspace.selectCertificateProbe(probes, observed, 3 - step, 9f)!!
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
}
