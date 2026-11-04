package at.heinz.deviceai.learning.lm

import at.heinz.deviceai.learning.lm.reference.LmArch as RefArch
import at.heinz.deviceai.learning.lm.reference.LmConfig as RefConfig
import at.heinz.deviceai.learning.lm.reference.LmModel as RefModel
import at.heinz.deviceai.learning.lm.reference.LmParams as RefParams
import at.heinz.deviceai.learning.lm.reference.LmBpeTokenizer as RefTokenizer
import at.heinz.deviceai.learning.lm.reference.Kernels as RefKernels
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object R3Fixtures {
    val corpus = List(24) {
        "Heinz prüft den lokalen Kern. Gedächtnis und Sprache bleiben gleich. Grüße aus Österreich! " +
            "Der Kern prüft jeden Schritt. αβγ 中文 日本語 🔧👨‍👩‍👧‍👧 1234567890."
    }
    fun models(arch: LmArch, vocab: Int, seed: Long = 37): Pair<LmModel, RefModel> {
        val config = LmConfig(arch, vocab, 8, 1, 2, 256, 16)
        val ref = RefConfig(RefArch.valueOf(arch.name), vocab, 8, 1, 2, 256, 16)
        return LmModel(LmParams.init(config, seed)) to RefModel(RefParams.init(ref, seed))
    }
    fun texts(): List<String> {
        val rnd = Random(0xC0FFEE)
        return listOf("", "a", " Hallo Heinz", "  \t\r\n", "0123456789", "Grüße Österreich ß ä ö ü",
            "αβγ 中文 日本語", "👨‍👩‍👧‍👧🔧🏳️‍🌈", "e\u0301 a\u0308", "\uD800x\uDC00", "\u0000\u0001",
            "abc".repeat(1000), " Hallo 123! ".repeat(100)) + List(240) {
            String(CharArray(rnd.nextInt(120)) { rnd.nextInt(65536).toChar() })
        }
    }
    fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

class LmTuningR3Checks {
    private fun same(a: FloatArray, b: FloatArray) = assertArrayEquals(a.map { it.toRawBits() }.toIntArray(), b.map { it.toRawBits() }.toIntArray())
    private fun same(a: DoubleArray, b: DoubleArray) = assertArrayEquals(a.map { it.toRawBits() }.toLongArray(), b.map { it.toRawBits() }.toLongArray())
    private fun outcome(block: () -> Any): Pair<String, String?> = try {
        block(); "OK" to null
    } catch (e: RuntimeException) { e.javaClass.name to e.message }

    @Test fun nllPreservesEveryOutputBit() {
        for (arch in LmArch.values()) for (vocab in listOf(258, 512)) for (seed in listOf(7L, 37L)) {
            val (model, ref) = R3Fixtures.models(arch, vocab, seed)
            for (length in listOf(1, 2, 7, 16)) {
                val ids = IntArray(length) { (it * 67 + 3) % vocab }
                for (mode in 0..2) {
                    val targets = IntArray(length) { if (mode == 0 || mode == 1 && it % 3 == 0) -1 else (it * 31 + 11) % vocab }
                    same(ref.nll(ids, targets), model.nll(ids, targets))
                }
            }
        }
    }

    @Test fun nextDistributionPreservesEveryOutputBit() {
        for (arch in LmArch.values()) for (vocab in listOf(258, 512)) for (seed in listOf(7L, 37L)) {
            val (model, ref) = R3Fixtures.models(arch, vocab, seed)
            for (length in listOf(1, 2, 7, 16, 64)) {
                val ids = IntArray(length) { (it * 67 + 3) % vocab }
                same(ref.nextDistribution(ids), model.nextDistribution(ids))
                assertEquals(vocab, model.nextDistribution(ids).size)
            }
        }
    }

    @Test fun inferenceKeepsParametersAndResultsIndependent() {
        for (arch in LmArch.values()) {
            val (model, ref) = R3Fixtures.models(arch, 258)
            val original = model.params.mats.mapValues { it.value.data.copyOf() }
            val ids = intArrayOf(256, 7, 23, 41, 257)
            val first = model.nextDistribution(ids)
            val expected = ref.nextDistribution(ids)
            first.fill(-91f)
            same(expected, model.nextDistribution(ids))
            val loss = model.nll(ids, ids)
            loss.fill(-91.0)
            same(ref.nll(ids, ids), model.nll(ids, ids))
            for ((name, values) in original) same(values, model.params.mats.getValue(name).data)
            assertArrayEquals(intArrayOf(256, 7, 23, 41, 257), ids)
        }
    }

    @Test fun invalidInputsKeepTheirExistingOutcomes() {
        for (arch in LmArch.values()) {
            val (model, ref) = R3Fixtures.models(arch, 258)
            for (ids in listOf(intArrayOf(), intArrayOf(-1), intArrayOf(258), IntArray(257))) {
                assertEquals(outcome { ref.nextDistribution(ids) }, outcome { model.nextDistribution(ids) })
                assertEquals(outcome { ref.nll(ids, IntArray(ids.size)) }, outcome { model.nll(ids, IntArray(ids.size)) })
            }
            val ids = intArrayOf(1, 2)
            for (targets in listOf(intArrayOf(), intArrayOf(1), intArrayOf(1, Int.MAX_VALUE), intArrayOf(258, 1), intArrayOf(-7, -1))) {
                assertEquals(outcome { ref.nll(ids, targets) }, outcome { model.nll(ids, targets) })
                if (outcome { ref.nll(ids, targets) }.first == "OK") same(ref.nll(ids, targets), model.nll(ids, targets))
            }
        }
    }

    @Test fun existingSoftmaxSignatureKeepsOffsetsAndSpecialBits() {
        val inputs = listOf(floatArrayOf(), floatArrayOf(0f, -0f), floatArrayOf(1f, 2f, 3f),
            floatArrayOf(Float.NaN, 1f), floatArrayOf(Float.POSITIVE_INFINITY, 1f),
            floatArrayOf(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY), floatArrayOf(Float.MIN_VALUE, Float.MAX_VALUE))
        for (input in inputs) for (offset in listOf(0, 3)) {
            val src = FloatArray(offset + input.size + 3) { -123f }
            input.copyInto(src, offset)
            val before = src.copyOf()
            val a = FloatArray(src.size) { 71f }; val b = a.copyOf()
            val x = RefKernels.softmaxRow(src, offset, input.size, a)
            val y = Kernels.softmaxRow(src, offset, input.size, b)
            assertEquals(x.toRawBits(), y.toRawBits()); same(a, b); same(before, src)
            val aliasA = src.copyOf(); val aliasB = src.copyOf()
            assertEquals(RefKernels.softmaxRow(aliasA, offset, input.size, aliasA).toRawBits(),
                Kernels.softmaxRow(aliasB, offset, input.size, aliasB).toRawBits())
            same(aliasA, aliasB)
        }
    }

    @Test fun tokenizationPreservesUnicodeCacheAndVocabulary() {
        for (learned in listOf(false, true)) {
            val tok = if (learned) LmBpeTokenizer.train(R3Fixtures.corpus, 384) else LmBpeTokenizer.bytes()
            val ref = if (learned) RefTokenizer.train(R3Fixtures.corpus, 384) else RefTokenizer.bytes()
            assertArrayEquals(ref.serialize(), tok.serialize()); assertEquals(ref.sha256, tok.sha256)
            val serialized = tok.serialize()
            for (text in R3Fixtures.texts()) {
                val expected = ref.encode(text)
                assertArrayEquals(expected, tok.encode(text))
                val exposed = tok.encode(text); exposed.fill(-1)
                assertArrayEquals(expected, tok.encode(text))
                assertEquals(ref.decode(expected), tok.decode(expected))
            }
            assertArrayEquals(serialized, tok.serialize())
            val restored = LmBpeTokenizer.deserialize(serialized)
            for (text in R3Fixtures.texts().take(20)) assertArrayEquals(ref.encode(text), restored.encode(text))
        }
    }

    @Test fun concurrentReadersRetainExactOutputs() {
        val tok = LmBpeTokenizer.train(R3Fixtures.corpus, 384)
        val ref = RefTokenizer.deserialize(tok.serialize())
        val (model, original) = R3Fixtures.models(LmArch.MINGRU, 258)
        val texts = R3Fixtures.texts().take(48)
        val expected = texts.map { ref.encode(it) }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = pool.invokeAll(texts.indices.map { i -> Callable {
                assertArrayEquals(expected[i], tok.encode(texts[i]))
                val ids = IntArray(i % 16 + 1) { (it + i) % 258 }
                same(original.nextDistribution(ids), model.nextDistribution(ids))
                same(original.nll(ids, ids), model.nll(ids, ids))
            } })
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
