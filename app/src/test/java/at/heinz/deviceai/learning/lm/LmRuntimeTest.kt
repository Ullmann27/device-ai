package at.heinz.deviceai.learning.lm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Random

class LmRuntimeTest {
    private val text = "der kern lernt schnell. der kern prüft jede quittung. der kern lernt schnell."
    private val tok = LmBpeTokenizer.train(List(10) { text }, 300)

    private fun trained(arch: LmArch, steps: Int = 200): LmModel {
        val seq = intArrayOf(tok.bos) + tok.encode(text) + tok.eos
        val model = LmModel(LmParams.init(LmConfig(arch, tok.vocabSize, 16, 2, 2, 64, 32), 3))
        LmTrainer(model, 1e-2f, 0f, 5, steps, threads = 1).use { t -> repeat(steps) { t.step(listOf(seq)) } }
        return model
    }

    private fun sha(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun runtime(model: LmModel): LmRuntime {
        val bytes = LmCheckpoint.encode(model.params, tok, mapOf("family" to "test"))
        return LmRuntime.load(bytes, tok.serialize(), sha(bytes))
    }

    @Test fun stepwiseMinGruGivesBitIdenticalLogitsToTheFullForwardPass() {
        for (seed in listOf(7L, 8L)) {
            val model = if (seed == 7L) LmModel(LmParams.init(LmConfig(LmArch.MINGRU, tok.vocabSize, 16, 2, 2, 64, 32), seed))
                else trained(LmArch.MINGRU, 80)
            val rt = runtime(model)
            val rnd = Random(seed)
            val ids = IntArray(40) { if (it == 0) tok.bos else rnd.nextInt(tok.bos) }
            for (t in listOf(1, 2, 7, 23, 40)) {
                val prefix = ids.copyOf(t)
                val tape = Tape(training = false)
                val full = model.logits(tape, model.frozenNodes(tape), prefix).value
                val v = full.cols
                assertArrayEquals("prefix $t", full.data.copyOfRange((t - 1) * v, t * v), rt.stepwiseLogits(prefix), 0f)
            }
        }
    }

    @Test fun greedyGenerationMatchesTheResearchGeneratorAndSamplingIsReproducible() {
        for (arch in LmArch.values()) {
            val model = trained(arch)
            val rt = runtime(model)
            val r = rt.generate(LmRuntime.Request("der kern", maxNewTokens = 30))
            assertEquals(LmEval.generate(model, tok, "der kern", 30, 0.0, 1, Random(0)), r.text)
            assertTrue("$arch learned the phrase: ${r.text}", r.text.startsWith(" lernt schnell") || r.text.startsWith(" prüft"))
            assertEquals(LmRuntime.Kind.FREE_GENERATION, r.kind)
            val a = rt.generate(LmRuntime.Request("der kern", maxNewTokens = 20, temperature = 1.2, topK = 50, seed = 3))
            val b = rt.generate(LmRuntime.Request("der kern", maxNewTokens = 20, temperature = 1.2, topK = 50, seed = 3))
            assertEquals(a.text, b.text)
        }
    }

    @Test fun damagedForeignOrUnexpectedCheckpointsAreRejected() {
        val model = trained(LmArch.MINGRU, 20)
        val bytes = LmCheckpoint.encode(model.params, tok, emptyMap())
        fun rejects(why: String, block: () -> Unit) = try { block(); fail("accepted: $why") } catch (_: IllegalArgumentException) { }
        val damaged = bytes.copyOf().also { it[200] = (it[200] + 1).toByte() }
        rejects("damaged byte, expected hash of the damaged file") { LmRuntime.load(damaged, tok.serialize(), sha(damaged)) }
        rejects("damaged byte, original hash") { LmRuntime.load(damaged, tok.serialize(), sha(bytes)) }
        rejects("another checkpoint than expected") { LmRuntime.load(bytes, tok.serialize(), "0".repeat(64)) }
        val other = LmBpeTokenizer.train(listOf("ganz andere texte mit anderen paaren"), 300)
        rejects("foreign tokenizer") { LmRuntime.load(bytes, other.serialize(), sha(bytes)) }
        rejects("truncated") { LmRuntime.load(bytes.copyOf(60), tok.serialize(), sha(bytes.copyOf(60))) }
        rejects("too large") { LmRuntime.load(ByteArray(LmRuntime.MAX_CHECKPOINT_BYTES + 1), tok.serialize(), "0".repeat(64)) }
        val dir = Files.createTempDirectory("lm-runtime").toFile()
        val store = LmCheckpointStore(dir)
        val hash = store.put(bytes)
        assertEquals(hash, LmRuntime.load(store, hash, tok.serialize()).identity.checkpointSha256)
        java.io.File(dir, "$hash.lm1").writeBytes(damaged)
        try { LmRuntime.load(store, hash, tok.serialize()); fail("damaged store file accepted") } catch (_: IllegalStateException) { }
        dir.deleteRecursively()
    }

    @Test fun limitsStopGenerationAndAreReported() {
        val rt = runtime(trained(LmArch.MINGRU, 60))
        val few = rt.generate(LmRuntime.Request("der", maxNewTokens = 3, temperature = 1.0, topK = 1000, seed = 1))
        assertTrue(few.newTokens <= 3)
        if (few.stop == LmRuntime.Stop.MAX_TOKENS) assertEquals(3, few.newTokens)
        var polls = 0
        val cancelled = rt.generate(LmRuntime.Request("der", maxNewTokens = 50)) { ++polls > 2 }
        assertEquals(LmRuntime.Stop.CANCELLED, cancelled.stop)
        assertTrue(cancelled.newTokens <= 2)
        val long = "der kern lernt schnell. ".repeat(40)
        val truncated = rt.generate(LmRuntime.Request(long, maxNewTokens = 10))
        assertTrue(truncated.promptTruncated)
        assertEquals(64 - 1 - 10, truncated.promptTokens)
        val noRoom = rt.generate(LmRuntime.Request(long, maxNewTokens = 63, temperature = 1.0, topK = 1000, seed = 2))
        assertTrue(noRoom.promptTokens == 1 && noRoom.newTokens <= 62)
        assertFalse(noRoom.stop == LmRuntime.Stop.MAX_TOKENS)
        val slow = runtime(trained(LmArch.TRANSFORMER, 30)).generate(LmRuntime.Request(long, maxNewTokens = 60, maxMillis = 1))
        assertTrue("${slow.stop}", slow.stop == LmRuntime.Stop.TIME || slow.stop == LmRuntime.Stop.EOS)
        for (bad in listOf(LmRuntime.Request("x", maxNewTokens = 0), LmRuntime.Request("x", temperature = -1.0),
            LmRuntime.Request("x", maxMillis = 0), LmRuntime.Request("x".repeat(LmRuntime.MAX_PROMPT_BYTES + 1))))
            try { rt.generate(bad); fail("accepted $bad") } catch (_: IllegalArgumentException) { }
    }

    @Test fun anIncompleteCharacterAtTheEndIsDropped() {
        val bytes = "Grüße".toByteArray(Charsets.UTF_8)
        assertEquals("Grüße", LmRuntime.completeUtf8(bytes))
        assertEquals("Grü", LmRuntime.completeUtf8(bytes.copyOf(5)))   // "ß" cut after its first byte
        assertEquals("Gr", LmRuntime.completeUtf8(bytes.copyOf(3)))
        assertArrayEquals("€".toByteArray(Charsets.UTF_8), LmRuntime.completeUtf8("€".toByteArray(Charsets.UTF_8)).toByteArray(Charsets.UTF_8))
        assertEquals("", LmRuntime.completeUtf8("€".toByteArray(Charsets.UTF_8).copyOf(2)))
    }
}
