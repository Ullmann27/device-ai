package at.heinz.deviceai.learning.span

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class NaturalEntitySpanCoreTest {
    private val features = ContinuumResidualSpanCore.Features(positionFeatures = false, entityProximity = true,
        excludeCueWords = true, multiTokenSpans = true, naturalEntityBindings = true)

    @Test fun naturalBindingHasExactGradientAndVersionedCheckpointWithoutChangingLegacy() {
        val model = ContinuumResidualSpanCore(77, features)
        val example = ContinuumResidualSpanCore.Example("Anna ist in Graz. Ben ist in Linz.", "Wo ist Ben?", "Linz")
        repeat(30) { model.train(example) }
        val params = model.parameters()
        val grad = model.gradient(example)
        for (i in grad.values.indices.filter { abs(grad.values[it]) > 1e-8 }.takeLast(24)) {
            model.loadParameters(params.copyOf().also { it[i] += 1e-6 }); val hi = model.loss(example)
            model.loadParameters(params.copyOf().also { it[i] -= 1e-6 }); val lo = model.loss(example)
            model.loadParameters(params)
            assertEquals(grad.values[i], (hi - lo) / 2e-6, 1e-6)
        }
        assertEquals(4110, model.parameterCount)
        assertEquals(3, model.save()[7].toInt())
        val restored = ContinuumResidualSpanCore.restore(model.save())
        assertEquals(features, restored.features)
        assertArrayEquals(params, restored.parameters(), 0.0)
        assertEquals(model.predict(example.evidence, example.question), restored.predict(example.evidence, example.question))
        assertEquals(2, ContinuumResidualSpanCore(77).save()[7].toInt())
        assertTrue(runCatching { ContinuumResidualSpanCore.Features(naturalEntityBindings = true) }.isFailure)
    }
}
