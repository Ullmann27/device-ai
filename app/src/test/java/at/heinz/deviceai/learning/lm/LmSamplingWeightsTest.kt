package at.heinz.deviceai.learning.lm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random
import kotlin.math.exp
import kotlin.math.ln

/** Frozen round-one method from db3295d51c769afdaf809ac1dfae2ada84a3274f. */
internal object RoundOneSamplingReference {
    fun sample(p: FloatArray, temperature: Double, topK: Int, rnd: Random): Int {
        if (temperature <= 0.0) {
            topK.coerceIn(1, p.size)
            var best = 0
            for (i in 1 until p.size) {
                // Match the stable sort's Float ordering, including NaN and signed zero.
                if (p[i].compareTo(p[best]) > 0) best = i
            }
            return best
        }
        val order = p.indices.sortedByDescending { p[it] }.take(topK.coerceIn(1, p.size))
        val w = order.map { exp(ln(p[it].toDouble().coerceAtLeast(1e-30)) / temperature) }
        var r = rnd.nextDouble() * w.sum()
        for ((i, id) in order.withIndex()) { r -= w[i]; if (r <= 0) return id }
        return order.last()
    }
}

class LmSamplingWeightsTest {
    private class FixedDrawRandom(private val draw: Double) : Random(73) {
        var calls = 0
        override fun nextDouble(): Double { calls++; return draw }
    }

    private fun parity(p: FloatArray, temperature: Double, topK: Int,
                       referenceRandom: Random, actualRandom: Random) {
        val inputBits = IntArray(p.size) { p[it].toRawBits() }
        val reference = runCatching { RoundOneSamplingReference.sample(p, temperature, topK, referenceRandom) }
        val actual = runCatching { LmEval.sample(p, temperature, topK, actualRandom) }
        assertEquals(reference.getOrNull(), actual.getOrNull())
        assertEquals(reference.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
        assertEquals(reference.exceptionOrNull()?.message, actual.exceptionOrNull()?.message)
        assertEquals(referenceRandom.nextLong(), actualRandom.nextLong())
        if (referenceRandom is FixedDrawRandom && actualRandom is FixedDrawRandom) {
            assertEquals(referenceRandom.calls, actualRandom.calls)
        }
        assertArrayEquals(inputBits, IntArray(p.size) { p[it].toRawBits() })
    }

    @Test fun samplingMatchesRoundOneAcrossNumericalAndTopKBoundaries() {
        val random = Random(125)
        val inputs = listOf(floatArrayOf(), floatArrayOf(1f), floatArrayOf(.5f, .5f, 0f),
            floatArrayOf(-0.0f, 0.0f), floatArrayOf(Float.MIN_VALUE, Float.MAX_VALUE,
                Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN, -1f),
            FloatArray(128) { random.nextFloat() }, FloatArray(512) { Float.fromBits(random.nextInt()) },
            FloatArray(2048) { random.nextFloat() })
        for (p in inputs) for (temperature in doubleArrayOf(-1.0, -0.0, 0.0, Double.MIN_VALUE,
            .01, .25, 1.0, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN)) {
            for (topK in intArrayOf(Int.MIN_VALUE, 0, 1, 2, 50, p.size, Int.MAX_VALUE)) {
                for (seed in longArrayOf(0, 7, 123, Long.MAX_VALUE)) {
                    parity(p, temperature, topK, Random(seed), Random(seed))
                }
            }
        }
    }

    @Test fun boundaryDrawsPreserveSelectionAndExactRandomCallCount() {
        val inputs = listOf(floatArrayOf(), floatArrayOf(.25f, .25f, .25f, .25f),
            floatArrayOf(0f, 1f, Float.MIN_VALUE, .5f), floatArrayOf(Float.NaN, 1f, Float.POSITIVE_INFINITY))
        for (p in inputs) for (temperature in doubleArrayOf(0.0, Double.MIN_VALUE, .5, 1.0, Double.NaN)) {
            for (topK in intArrayOf(0, 1, 2, Int.MAX_VALUE)) {
                for (draw in doubleArrayOf(0.0, Double.MIN_VALUE, .5, Math.nextDown(1.0), 1.0, Double.NaN)) {
                    parity(p, temperature, topK, FixedDrawRandom(draw), FixedDrawRandom(draw))
                }
            }
        }
    }

    @Test fun weightsAreComputedBeforeTheRandomCallbackAndItsFailureIsPreserved() {
        for (fail in listOf(false, true)) {
            val a = floatArrayOf(.1f, .2f, .7f)
            val b = a.copyOf()
            fun random(p: FloatArray) = object : Random(3) {
                override fun nextDouble(): Double {
                    p.fill(Float.NaN)
                    if (fail) throw IllegalStateException("fixed draw failure")
                    return .15
                }
            }
            val expected = runCatching { RoundOneSamplingReference.sample(a, 1.0, 3, random(a)) }
            val actual = runCatching { LmEval.sample(b, 1.0, 3, random(b)) }
            assertEquals(expected.getOrNull(), actual.getOrNull())
            assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
            assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message)
            assertArrayEquals(IntArray(a.size) { a[it].toRawBits() }, IntArray(b.size) { b[it].toRawBits() })
        }
    }

    @Test fun boxedAndPrimitiveSumsKeepTheSameDoubleBits() {
        val random = Random(316)
        val inputs = listOf(doubleArrayOf(), doubleArrayOf(-0.0), doubleArrayOf(1e16, 1.0, 1.0),
            doubleArrayOf(Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_VALUE),
            doubleArrayOf(Double.POSITIVE_INFINITY, 1.0), doubleArrayOf(Double.fromBits(0x7ff8000000000001L), 0.0)) +
            List(64) { DoubleArray(128) { exp(random.nextDouble() * 1400.0 - 700.0) } }
        for (values in inputs) assertEquals(values.toList().sum().toRawBits(), values.sum().toRawBits())
    }

    @Test fun unicodeAutoregressiveSamplingMatchesRoundOne() {
        val prompts = listOf("Grüße aus Österreich", "日本語 👩🏽‍🔧", "e\u0301 café", "\u0000\n\t", "")
        val tokenizer = LmBpeTokenizer.train(prompts, 280)
        val bytes = tokenizer.serialize()
        for (arch in LmArch.values()) for (temperature in doubleArrayOf(.8, Double.NaN)) {
            val model = LmModel(LmParams.init(LmConfig(arch, tokenizer.vocabSize, 8, 1, 1, 64, 16), 31))
            for (prompt in prompts) {
                var ids = intArrayOf(tokenizer.bos) + tokenizer.encode(prompt)
                val a = Random(109)
                val b = Random(109)
                repeat(4) {
                    val p = model.nextDistribution(ids)
                    p[tokenizer.bos] = 0f
                    val expected = RoundOneSamplingReference.sample(p, temperature, 40, a)
                    val actual = LmEval.sample(p, temperature, 40, b)
                    assertEquals(expected, actual)
                    ids += actual
                }
                assertEquals(a.nextLong(), b.nextLong())
            }
        }
        assertArrayEquals(bytes, tokenizer.serialize())
    }
}
