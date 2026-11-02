package at.heinz.deviceai.intelligence

import at.heinz.deviceai.learning.span.ContinuumResidualSpanCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidualSpanResearchAdapterTest {
    @Test fun limitsRespectRealCxs3ByteBoundariesAndDoNotInventSpans() {
        val model = ContinuumResidualSpanCore()
        val adapter = ResidualSpanResearchAdapter(model)
        val sourceText = "Roboter Z77 steht in Graz."
        val source = ContinuumCognitiveSpine.Source(
            "fixture-1", sourceText, ContinuumCognitiveSpine.digest(sourceText), true)
        assertNull(adapter.propose("W".repeat(257), source))
        assertNull(adapter.propose("Wo steht Z77?",
            source.copy(text = "ä".repeat(513))))
        val proposed = adapter.propose("Wo steht Z77?", source)
        if (proposed != null) {
            assertEquals(source.id, proposed.sourceId)
            assertTrue(proposed.start in 0 until proposed.end)
            assertTrue(proposed.end <= sourceText.length)
        }
        // No model training is performed by predict or by this adapter.
        assertEquals(0, model.updates)
    }

    @Test fun respectsExplicitResearchThresholdAndSpineSourceIntegrity() {
        val model = ContinuumResidualSpanCore()
        val strict = ResidualSpanResearchAdapter(model, 1.0)
        val sourceText = "Roboter Z77 steht in Graz."
        val reviewed = ContinuumCognitiveSpine.Source(
            "fixture-1", sourceText, ContinuumCognitiveSpine.digest(sourceText), true)
        val result = ContinuumCognitiveSpine(strict).resolve(
            "Wo steht Z77?", ContinuumCognitiveSpine.Task.QUESTION,
            listOf(reviewed.copy(sha256 = "0".repeat(64))))
        assertEquals(ContinuumCognitiveSpine.Status.ABSTAINED, result.status)
        assertTrue(runCatching { ResidualSpanResearchAdapter(model, Double.NaN) }.isFailure)
        assertTrue(runCatching { ResidualSpanResearchAdapter(model, 1.1) }.isFailure)
    }
}
