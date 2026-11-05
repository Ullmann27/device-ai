package at.heinz.deviceai.learning.lm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import kotlin.math.abs

/** Behavioral tests for the isolated experiment; these never read or replace an owner checkpoint. */
class LmWeightSurgeryTest {
    private val tokenizer = LmBpeTokenizer.bytes()
    private val metadata = mapOf("purpose" to "weight-surgery-test")

    private fun fixture(seed: Long = 19L): LmParams = LmParams.init(
        LmConfig(LmArch.MINGRU, tokenizer.vocabSize, 8, 2, context = 32, mlp = 12), seed
    ).also { params ->
        // Nonuniform learned gains and nonzero biases expose incorrect special-case copying.
        for ((name, matrix) in params.mats) {
            if (name == "nf" || name.endsWith(".n1") || name.endsWith(".n2")) {
                for (i in matrix.data.indices) matrix.data[i] = 0.7f + (i % 7) * 0.09f
            } else if (name.substringAfterLast('.').startsWith("b")) {
                for (i in matrix.data.indices) matrix.data[i] = ((i % 5) - 2) * 0.03f
            }
        }
    }

    private fun bytes(params: LmParams): ByteArray = LmCheckpoint.encode(params, tokenizer, metadata)

    private fun logits(params: LmParams, ids: IntArray): FloatArray {
        val model = LmModel(params)
        val tape = Tape(training = false)
        return model.logits(tape, model.frozenNodes(tape), ids).value.data
    }

    private fun assertClose(label: String, expected: FloatArray, actual: FloatArray, absolute: Float, relative: Float) {
        assertEquals("$label length", expected.size, actual.size)
        for (i in expected.indices) {
            assertTrue("$label[$i] finite", expected[i].isFinite() && actual[i].isFinite())
            val tolerance = absolute + relative * maxOf(abs(expected[i]), abs(actual[i]))
            assertTrue("$label[$i]: ${expected[i]} != ${actual[i]} (tolerance $tolerance)",
                abs(expected[i] - actual[i]) <= tolerance)
        }
    }

    private fun assertSeparate(source: LmParams, candidate: LmParams) {
        assertNotSame(source, candidate)
        assertNotSame(source.mats, candidate.mats)
        for ((name, matrix) in source.mats) {
            assertNotSame(name, matrix, candidate.mats.getValue(name))
            assertNotSame(name, matrix.data, candidate.mats.getValue(name).data)
        }
    }

    private fun rejects(label: String, block: () -> Unit) {
        try {
            block()
            fail("$label must be rejected")
        } catch (_: IllegalArgumentException) {
            // Validation must fail before the caller's model is changed.
        }
    }

    @Test fun rescalingChangesOnlyTheAllowedGainAndDoesNotAliasTheSource() {
        val source = fixture()
        val original = bytes(source)
        for (name in listOf("l0.n1", "l1.n1", "nf")) {
            val candidate = LmWeightSurgery.rescaleNorm(source, name, 1.25f)
            assertEquals(source.config, candidate.config)
            assertSeparate(source, candidate)
            for ((key, matrix) in source.mats) {
                val expected = if (key == name) FloatArray(matrix.data.size) { matrix.data[it] * 1.25f } else matrix.data
                assertArrayEquals(key, expected, candidate.mats.getValue(key).data, 0f)
            }
            candidate.mats.getValue("tok").data[0] += 1f
            assertArrayEquals(original, bytes(source))
        }
        assertArrayEquals(original, bytes(LmWeightSurgery.rescaleNorm(source, "nf", 1f)))
    }

    @Test fun finalNormRescalingHasTheExpectedRealLogitEffect() {
        val source = fixture()
        val ids = intArrayOf(tokenizer.bos, 65, 66, 67, 65)
        val baseline = logits(source, ids)
        val candidate = logits(LmWeightSurgery.rescaleNorm(source, "nf", 0.75f), ids)
        assertClose("scaled logits", FloatArray(baseline.size) { baseline[it] * 0.75f }, candidate, 2e-6f, 2e-5f)
    }

    @Test fun eachExpansionPreservesFullLogitsAndNextTokenDistributions() {
        val sequences = listOf(
            intArrayOf(tokenizer.bos),
            intArrayOf(tokenizer.bos, 0, 255, 65, 65, tokenizer.eos),
            IntArray(24) { if (it == 0) tokenizer.bos else (it * 37) % 256 }
        )
        for (seed in listOf(3L, 19L)) {
            val source = fixture(seed)
            val original = bytes(source)
            for (factor in listOf(2, 4, 8)) {
                val candidate = LmWeightSurgery.widenMinGru(source, factor)
                assertEquals(source.config.copy(dim = source.config.dim * factor, mlp = source.config.mlp * factor), candidate.config)
                assertSeparate(source, candidate)
                assertTrue(candidate.count > source.count)
                for (ids in sequences) {
                    assertClose("factor $factor logits", logits(source, ids), logits(candidate, ids), 2e-5f, 2e-4f)
                    val actual = LmModel(candidate).nextDistribution(ids)
                    assertClose("factor $factor probabilities", LmModel(source).nextDistribution(ids), actual, 2e-6f, 2e-4f)
                    assertEquals(1.0, actual.sumOf { it.toDouble() }, 1e-6)
                }
                candidate.mats.getValue("tok").data[0] += 1f
                assertArrayEquals(original, bytes(source))
            }
        }
    }

    @Test fun millionParameterShapesAreRealTrainableMatrices() {
        val source = LmParams.init(LmConfig(LmArch.MINGRU, 512, 64, 2, context = 32, mlp = 256), 1L)
        assertEquals(132_160, source.count)
        assertEquals(1_708_288, LmWeightSurgery.widenMinGru(source, 4).count)
        val largest = LmWeightSurgery.widenMinGru(source, 8)
        assertEquals(6_562_304, largest.count)
        assertEquals(512, largest.config.dim)
        assertEquals(2048, largest.config.mlp)
        val ids = intArrayOf(510, 17, 23)
        assertClose("million parameter logits", logits(source, ids), logits(largest, ids), 2e-5f, 2e-4f)
    }

    @Test fun seededZeroSumNoisePreservesTheInitialFunctionAndIsReproducible() {
        val source = fixture()
        val original = bytes(source)
        val factor = 4
        val expanded = LmWeightSurgery.widenMinGru(source, factor, 0.001f, 73L)
        assertArrayEquals(bytes(expanded), bytes(LmWeightSurgery.widenMinGru(source, factor, 0.001f, 73L)))
        assertTrue(!bytes(expanded).contentEquals(bytes(LmWeightSurgery.widenMinGru(source, factor, 0.001f, 74L))))
        for ((name, matrix) in source.mats) {
            if (!name.substringAfterLast('.').startsWith("w")) continue
            val wide = expanded.mats.getValue(name)
            for (row in 0 until matrix.rows) for (col in 0 until wide.cols) {
                val groupSum = (0 until factor).sumOf { wide[row + it * matrix.rows, col].toDouble() }
                assertEquals("$name conserved incoming weight", matrix[row, col % matrix.cols].toDouble(), groupSum, 2e-7)
            }
        }
        for (ids in listOf(intArrayOf(tokenizer.bos, 12, 12, 90), IntArray(24) { it * 7 })) {
            assertClose("noisy logits", logits(source, ids), logits(expanded, ids), 3e-5f, 3e-4f)
            assertClose("noisy probabilities", LmModel(source).nextDistribution(ids), LmModel(expanded).nextDistribution(ids), 3e-6f, 3e-4f)
        }
        assertArrayEquals(original, bytes(source))
    }

    @Test fun expansionRoundTripsThroughTheExistingChecksummedCheckpoint() {
        val source = fixture()
        for (factor in listOf(2, 4, 8)) {
            val wide = LmWeightSurgery.widenMinGru(source, factor, 0.001f, 11L)
            val encoded = bytes(wide)
            val restored = LmCheckpoint.decode(encoded, tokenizer)
            assertEquals(metadata, restored.meta)
            assertArrayEquals(encoded, bytes(restored.params))
            val ids = intArrayOf(tokenizer.bos, 20, 200, 17)
            assertArrayEquals(logits(wide, ids), logits(restored.params, ids), 0f)
        }
    }

    @Test fun oneRealLearningStepCanSeparateReplicasAfterZeroSumNoise() {
        val source = fixture()
        val original = bytes(source)
        val expanded = LmWeightSurgery.widenMinGru(source, 4, 0.001f, 31L)
        val embedding = expanded.mats.getValue("tok")
        fun replicaGap(): Double {
            var maximum = 0.0
            for (row in 0 until embedding.rows) for (col in 0 until source.config.dim) {
                maximum = maxOf(maximum, abs(embedding[row, col] - embedding[row, col + source.config.dim]).toDouble())
            }
            return maximum
        }
        assertEquals("embedding replicas start identical", 0.0, replicaGap(), 0.0)
        val before = bytes(expanded)
        val sequence = intArrayOf(tokenizer.bos, 97, 98, 97, 99, 97, 98, tokenizer.eos)
        LmTrainer(LmModel(expanded), 0.001f, 0f, warmup = 0, totalSteps = 1, threads = 1).use { trainer ->
            assertTrue(trainer.step(listOf(sequence)).isFinite())
            assertEquals(1, trainer.steps)
        }
        assertTrue("a real update changes parameters", !before.contentEquals(bytes(expanded)))
        assertTrue("the extra channels can learn different values", replicaGap() > 1e-8)
        assertTrue(expanded.allFinite())
        assertArrayEquals(original, bytes(source))
    }

    @Test fun deepeningCopiesTheLearnedCoreAndStartsNewResidualBranchesAtIdentity() {
        val source = fixture()
        val original = bytes(source)
        for (additional in listOf(1, 3, 10)) {
            val deep = LmWeightSurgery.deepenMinGru(source, additional, 89L)
            assertEquals(source.config.copy(layers = source.config.layers + additional), deep.config)
            assertSeparate(source, deep)
            for ((name, matrix) in source.mats) {
                assertArrayEquals("preserved $name", matrix.data, deep.mats.getValue(name).data, 0f)
            }
            for (layer in source.config.layers until deep.config.layers) {
                for (leaf in listOf("wo", "w2", "b2")) {
                    assertTrue("new l$layer.$leaf starts at zero", deep.mats.getValue("l$layer.$leaf").data.all { it == 0f })
                }
                for (leaf in listOf("wz", "wc", "wg", "w1")) {
                    assertTrue("new l$layer.$leaf retains initialized capacity", deep.mats.getValue("l$layer.$leaf").data.any { it != 0f })
                }
            }
            assertTrue(deep.allFinite())
            assertArrayEquals("fixed initialization is reproducible", bytes(deep), bytes(LmWeightSurgery.deepenMinGru(source, additional, 89L)))
            val otherSeed = LmWeightSurgery.deepenMinGru(source, additional, 90L)
            assertTrue("new trainable weights depend on seed", !deep.mats.getValue("l2.wc").data.contentEquals(otherSeed.mats.getValue("l2.wc").data))
            deep.mats.getValue("tok").data[0] += 1f
            assertArrayEquals(original, bytes(source))
        }
    }

    @Test fun identityLayersPreserveFullForwardAndStepwiseRuntimeAtWidth512() {
        fun runtime(params: LmParams): LmRuntime {
            val checkpoint = bytes(params)
            val hash = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(checkpoint))
            return LmRuntime.load(checkpoint, tokenizer.serialize(), hash)
        }
        val cases = listOf(
            fixture() to 3,
            LmParams.init(LmConfig(LmArch.MINGRU, tokenizer.vocabSize, 512, 2, context = 16, mlp = 2048), 23L) to 1
        )
        val sequence = intArrayOf(tokenizer.bos, 65, 0, 255, 65, tokenizer.eos)
        for ((source, additional) in cases) {
            val original = bytes(source)
            val deep = LmWeightSurgery.deepenMinGru(source, additional, 101L)
            assertArrayEquals("all positions at width ${source.config.dim}", logits(source, sequence), logits(deep, sequence), 0f)
            val originalRuntime = runtime(source)
            val deepRuntime = runtime(deep)
            assertEquals(deep.count, deepRuntime.identity.parameters)
            for (length in listOf(1, 3, sequence.size)) {
                val prefix = sequence.copyOf(length)
                val expected = originalRuntime.stepwiseLogits(prefix)
                assertArrayEquals("stepwise depth parity width=${source.config.dim} prefix=$length", expected, deepRuntime.stepwiseLogits(prefix), 0f)
                val full = logits(deep, prefix)
                assertArrayEquals("stepwise and full forward agree", full.copyOfRange(full.size - source.config.vocab, full.size), deepRuntime.stepwiseLogits(prefix), 0f)
            }
            assertArrayEquals(original, bytes(source))
        }
    }

    @Test fun anAddedIdentityLayerLearnsItsResidualOutputsInOneRealStep() {
        val source = fixture()
        val original = bytes(source)
        val deep = LmWeightSurgery.deepenMinGru(source, 1, 31L)
        val sequence = intArrayOf(tokenizer.bos, 97, 98, 97, 99, 97, 98, tokenizer.eos)
        for (leaf in listOf("wo", "w2")) {
            assertTrue(deep.mats.getValue("l2.$leaf").data.all { it == 0f })
        }
        LmTrainer(LmModel(deep), 0.001f, 0f, warmup = 0, totalSteps = 1, threads = 1).use { trainer ->
            assertTrue(trainer.step(listOf(sequence)).isFinite())
            assertEquals(1, trainer.steps)
        }
        for (leaf in listOf("wo", "w2")) {
            assertTrue("new l2.$leaf receives a real learning update", deep.mats.getValue("l2.$leaf").data.any { it != 0f })
        }
        assertTrue(deep.allFinite())
        assertArrayEquals(original, bytes(source))
    }

    @Test fun depthExpansionRejectsInvalidDepthArchitectureAndSourceBeforeMutation() {
        val source = fixture()
        val original = bytes(source)
        for (additional in listOf(-1, 0, 11, Int.MAX_VALUE)) rejects("additional depth $additional") {
            LmWeightSurgery.deepenMinGru(source, additional)
        }
        val fullDepth = LmParams.init(source.config.copy(layers = 12), 1L)
        rejects("depth above twelve") { LmWeightSurgery.deepenMinGru(fullDepth, 1) }
        val transformer = LmParams.init(source.config.copy(arch = LmArch.TRANSFORMER), 1L)
        rejects("transformer depth") { LmWeightSurgery.deepenMinGru(transformer, 1) }
        val notFinite = fixture().also { it.mats.getValue("nf").data[0] = Float.POSITIVE_INFINITY }
        rejects("nonfinite depth source") { LmWeightSurgery.deepenMinGru(notFinite, 1) }
        val malformed = fixture().also { it.mats["l0.wz"] = Mat(1, 1) }
        rejects("malformed depth source") { LmWeightSurgery.deepenMinGru(malformed, 1) }
        assertArrayEquals(original, bytes(source))
    }

    @Test fun rejectsUnsupportedOperationsAndNonfiniteOrMalformedSources() {
        val source = fixture()
        val original = bytes(source)
        for (factor in listOf(-1, 0, 1, 3, Int.MAX_VALUE)) rejects("factor $factor") {
            LmWeightSurgery.widenMinGru(source, factor)
        }
        for (noise in listOf(-0.001f, 0.011f, Float.NaN, Float.POSITIVE_INFINITY)) rejects("noise $noise") {
            LmWeightSurgery.widenMinGru(source, 2, noise, 1L)
        }
        for (name in listOf("tok", "l0.n2", "l0.wz", "l2.n1", " NF ")) rejects("gain $name") {
            LmWeightSurgery.rescaleNorm(source, name, 1f)
        }
        for (factor in listOf(-1f, 0f, 0.2f, 4.1f, Float.NaN, Float.POSITIVE_INFINITY)) rejects("gain factor $factor") {
            LmWeightSurgery.rescaleNorm(source, "nf", factor)
        }
        val transformer = LmParams.init(source.config.copy(arch = LmArch.TRANSFORMER), 1L)
        rejects("transformer expansion") { LmWeightSurgery.widenMinGru(transformer, 2) }
        rejects("transformer rescale") { LmWeightSurgery.rescaleNorm(transformer, "nf", 1f) }
        val tooWide = LmParams.init(source.config.copy(dim = 72), 1L)
        rejects("expanded width") { LmWeightSurgery.widenMinGru(tooWide, 8) }
        val tooLargeMlp = LmParams.init(source.config.copy(mlp = 520), 1L)
        rejects("expanded MLP") { LmWeightSurgery.widenMinGru(tooLargeMlp, 8) }
        val notFinite = fixture().also { it.mats.getValue("tok").data[3] = Float.NaN }
        rejects("nonfinite expansion") { LmWeightSurgery.widenMinGru(notFinite, 2) }
        rejects("nonfinite rescale") { LmWeightSurgery.rescaleNorm(notFinite, "nf", 1f) }
        val overflow = fixture().also { it.mats.getValue("nf").data[0] = Float.MAX_VALUE }
        rejects("overflowing norm") { LmWeightSurgery.rescaleNorm(overflow, "nf", 4f) }
        val malformed = fixture().also { it.mats["l0.wz"] = Mat(1, 1) }
        rejects("mutated layout") { LmWeightSurgery.widenMinGru(malformed, 2) }
        rejects("mutated layout rescale") { LmWeightSurgery.rescaleNorm(malformed, "nf", 1f) }
        val singleLayer = LmParams.init(source.config.copy(layers = 1), 1L)
        rejects("absent norm") { LmWeightSurgery.rescaleNorm(singleLayer, "l1.n1", 1f) }
        assertArrayEquals(original, bytes(source))
    }
}
