package at.heinz.deviceai.learning.synapse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynapseContrastiveRoleProbeTest {
    @Test fun pairwiseLearningIncreasesCorrectClauseMargin() {
        val model = SynapseContrastiveRoleProbe(47)
        val q = "Welches Kühlmittel benötigt Roboter Z77?"
        val positive = "Modul M4 benötigt Kühlmittel Helium."
        val negative = "Modul M4 benötigt Schutzgas Argon."
        val before = model.rankScore(q, positive) - model.rankScore(q, negative)
        repeat(240) { model.trainPair(q, positive, negative, 0.2) }
        val after = model.rankScore(q, positive) - model.rankScore(q, negative)
        assertTrue("Did not learn contrastive margin: $before => $after", after > before + 0.25)
        assertTrue("Expected positive margin, got $after", after > 0.0)
        assertEquals(240, model.updates)
        assertEquals(4096, model.parameterCount)
    }

    @Test fun modelStartsUntrainedAndIsDeterministicForSameSeed() {
        val a = SynapseContrastiveRoleProbe(99)
        val b = SynapseContrastiveRoleProbe(99)
        assertEquals(0, a.updates)
        assertEquals(a.rankScore("Wo ist R10?", "R10 steht in Wien."), b.rankScore("Wo ist R10?", "R10 steht in Wien."), 1e-12)
        a.trainPair("Wo ist R10?", "R10 steht in Wien.", "R10 verwendet Modul M4.")
        assertEquals(0, b.updates)
    }
}
