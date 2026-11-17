package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HypothesisWorkspaceTest {
    @Test fun bucketLimitForcesStructuralDiversity() {
        val w = HypothesisWorkspace(maxHypotheses = 6, maxPerBucket = 1)
        assertTrue(w.add(HypothesisWorkspace.Hypothesis("config",HypothesisWorkspace.Bucket.CONFIG,"Flag falsch",1f)))
        assertTrue(!w.add(HypothesisWorkspace.Hypothesis("config2",HypothesisWorkspace.Bucket.CONFIG,"Noch ein Flag",1f)))
        assertTrue(w.add(HypothesisWorkspace.Hypothesis("surface",HypothesisWorkspace.Bucket.UI,"Surface verdeckt",1f)))
        assertEquals(2, w.snapshot().size)
    }

    @Test fun cheapBalancedProbeWinsAndObservationMovesBestHypothesis() {
        val w = HypothesisWorkspace()
        w.add(HypothesisWorkspace.Hypothesis("h1",HypothesisWorkspace.Bucket.CONFIG,"3D deaktiviert",1f))
        w.add(HypothesisWorkspace.Hypothesis("h2",HypothesisWorkspace.Bucket.UI,"Texture verdeckt",1f))
        val cheap = HypothesisWorkspace.Probe("receipt",0.1f,0f,mapOf("h1" to true,"h2" to false))
        val expensive = HypothesisWorkspace.Probe("gpu-dump",500f,1f,mapOf("h1" to true,"h2" to false))
        val selected=w.selectNextProbe(listOf(expensive, cheap))
        assertNotNull(selected)
        assertEquals("receipt",selected!!.id)
        w.observe(cheap, result = false, evidence = "3D war aktiv")
        assertEquals("h2", w.best()!!.id)
        assertTrue(w.best()!!.supportingEvidence.contains("3D war aktiv"))
    }

    @Test fun nondiscriminatingProbeIsSkipped() {
        val w = HypothesisWorkspace()
        w.add(HypothesisWorkspace.Hypothesis("h1",HypothesisWorkspace.Bucket.CONFIG,"A",1f))
        w.add(HypothesisWorkspace.Hypothesis("h2",HypothesisWorkspace.Bucket.UI,"B",1f))
        val useless = HypothesisWorkspace.Probe("same",1f,0f,mapOf("h1" to true,"h2" to true))
        assertEquals(null, w.selectNextProbe(listOf(useless)))
    }
}
