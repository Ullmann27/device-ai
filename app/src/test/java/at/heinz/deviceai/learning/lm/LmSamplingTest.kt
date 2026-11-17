package at.heinz.deviceai.learning.lm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.exp
import kotlin.math.ln

/** Frozen sampling implementation from e101e393dd6c80803ac9d9ac91fccd9812c4a18d. */
internal object SortedSamplingReference {
    fun sample(p: FloatArray, temperature: Double, topK: Int, rnd: Random): Int {
        val order = p.indices.sortedByDescending { p[it] }.take(topK.coerceIn(1, p.size))
        if (temperature <= 0.0) return order.first()
        val w = order.map { exp(ln(p[it].toDouble().coerceAtLeast(1e-30)) / temperature) }
        var r = rnd.nextDouble() * w.sum()
        for ((i, id) in order.withIndex()) { r -= w[i]; if (r <= 0) return id }
        return order.last()
    }
}

class LmSamplingTest {
    private fun parity(p: FloatArray, temperature: Double, topK: Int, seed: Long = 37L) {
        val referenceRandom = Random(seed)
        val actualRandom = Random(seed)
        val before = IntArray(p.size) { p[it].toRawBits() }
        val reference = runCatching { SortedSamplingReference.sample(p, temperature, topK, referenceRandom) }
        val actual = runCatching { LmEval.sample(p, temperature, topK, actualRandom) }
        assertEquals("selection", reference.getOrNull(), actual.getOrNull())
        assertEquals("exception type", reference.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
        assertEquals("exception message", reference.exceptionOrNull()?.message, actual.exceptionOrNull()?.message)
        assertEquals("random state", referenceRandom.nextLong(), actualRandom.nextLong())
        assertArrayEquals("input bits", before, IntArray(p.size) { p[it].toRawBits() })
    }

    @Test fun greedyPreservesStableTiesNaNAndSignedZero() {
        assertEquals(0, LmEval.sample(floatArrayOf(.5f, .5f, .25f), 0.0, 2, Random(1)))
        assertEquals(1, LmEval.sample(floatArrayOf(-0.0f, 0.0f), 0.0, 1, Random(1)))
        assertEquals(0, LmEval.sample(floatArrayOf(0.0f, -0.0f), 0.0, 1, Random(1)))
        assertEquals(1, LmEval.sample(floatArrayOf(1f, Float.NaN, Float.NaN), 0.0, 3, Random(1)))
        val values = floatArrayOf(Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, -1f, -Float.MIN_VALUE,
            -0.0f, 0.0f, Float.MIN_VALUE, 1f, Float.MAX_VALUE, Float.POSITIVE_INFINITY,
            Float.NaN, Float.fromBits(0xffc00001.toInt()))
        for (a in values) for (b in values) for (c in values) {
            for (temperature in doubleArrayOf(Double.NEGATIVE_INFINITY, -1.0, -0.0, 0.0)) {
                for (topK in intArrayOf(Int.MIN_VALUE, 0, 1, 2, Int.MAX_VALUE)) {
                    parity(floatArrayOf(a, b, c), temperature, topK)
                }
            }
        }
    }

    @Test fun greedyMatchesReferenceAcrossVocabularySizesAndRawFloatBits() {
        val random = Random(815)
        for (size in intArrayOf(1, 2, 7, 128, 258, 512, 2048, 8192)) {
            repeat(8) { trial ->
                val p = FloatArray(size) { if (trial % 2 == 0) random.nextFloat() else Float.fromBits(random.nextInt()) }
                for (topK in intArrayOf(-1, 1, size / 2, size, Int.MAX_VALUE)) parity(p, 0.0, topK)
            }
        }
    }

    @Test fun positiveAndNaNTemperaturesRetainSamplingAndRandomConsumption() {
        val random = Random(19)
        val distributions = listOf(floatArrayOf(1f), floatArrayOf(-0.0f, 0.0f, 1f, 1f),
            floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, .5f),
            FloatArray(258) { random.nextFloat() }, FloatArray(512) { random.nextFloat() })
        for (p in distributions) for (temperature in doubleArrayOf(Double.MIN_VALUE, .25, 1.0,
            Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN)) {
            for (topK in intArrayOf(Int.MIN_VALUE, 0, 1, 3, p.size, Int.MAX_VALUE)) {
                repeat(4) { parity(p, temperature, topK, it.toLong()) }
            }
        }
    }

    @Test fun emptyInputKeepsTheExistingFailureAndRandomState() {
        for (temperature in doubleArrayOf(Double.NEGATIVE_INFINITY, -1.0, -0.0, 0.0, 1.0, Double.NaN)) {
            for (topK in intArrayOf(Int.MIN_VALUE, 0, 1, Int.MAX_VALUE)) parity(floatArrayOf(), temperature, topK)
        }
        assertTrue(runCatching { LmEval.sample(floatArrayOf(), 0.0, 1, Random(1)) }
            .exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun unicodePromptsRetainGeneratedTokenIdsAndTokenizerBytes() {
        val prompts = listOf("Grüße aus Österreich", "日本語 👩🏽‍🔧", "e\u0301 café", "\u0000\n\t", "")
        val tokenizer = LmBpeTokenizer.train(prompts, 280)
        val tokenizerBefore = tokenizer.serialize()
        for (arch in LmArch.values()) {
            val model = LmModel(LmParams.init(LmConfig(arch, tokenizer.vocabSize, 8, 1, 1, 64, 16), 31))
            for (prompt in prompts) {
                var ids = intArrayOf(tokenizer.bos) + tokenizer.encode(prompt)
                val referenceRandom = Random(41)
                val actualRandom = Random(41)
                repeat(5) {
                    val p = model.nextDistribution(ids)
                    p[tokenizer.bos] = 0f
                    val reference = SortedSamplingReference.sample(p, 0.0, 1, referenceRandom)
                    val actual = LmEval.sample(p, 0.0, 1, actualRandom)
                    assertEquals("$arch / $prompt", reference, actual)
                    ids += actual
                }
                assertEquals(referenceRandom.nextLong(), actualRandom.nextLong())
            }
        }
        assertArrayEquals(tokenizerBefore, tokenizer.serialize())
    }
}
