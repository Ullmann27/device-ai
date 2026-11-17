package at.heinz.deviceai.learning.span

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import kotlin.math.abs

/** Exact-gradient, determinism and checkpoint invariants for every CSX1 feature variant (JVM, no Android). */
class ContinuumResidualSpanCoreVariantsTest {
    private val variants = listOf(
        ContinuumResidualSpanCore.Features(),
        ContinuumResidualSpanCore.Features(positionFeatures = false),
        ContinuumResidualSpanCore.Features(identifierPlaceholders = true),
        ContinuumResidualSpanCore.Features(positionFeatures = false, entityProximity = true),
        ContinuumResidualSpanCore.Features(positionFeatures = false, entityProximity = true, excludeCueWords = true),
        ContinuumResidualSpanCore.Features(positionFeatures = false, entityProximity = true, excludeCueWords = true,
            multiTokenSpans = true)
    )
    private val examples = listOf(
        ContinuumResidualSpanCore.Example("Einheit AB12 steht in Wien, Einheit CD34 steht in Graz.", "Wo steht CD34?", "Graz"),
        ContinuumResidualSpanCore.Example("Gerät EF56 verwendet Modul GH-123.", "Welches Werkzeug nutzt EF56?", null),
        ContinuumResidualSpanCore.Example("In Linz steht Einheit AA11.", "Wo steht AA11?", "Linz")
    )

    /** Central finite differences on trained (non-zero) weights, including shared hash buckets. */
    @Test fun gradientsMatchFiniteDifferencesForEveryVariantBeforeAndAfterTraining() {
        for (features in variants) {
            val model = ContinuumResidualSpanCore(77, features)
            repeat(40) { model.train(examples[it % examples.size], rate = 0.05) }
            for (example in examples) {
                val g = model.gradient(example)
                val params = model.parameters()
                val indices = (0 until ContinuumResidualSpanCore.PARAMETERS).filter { abs(g.values[it]) > 1e-9 }
                    .let { it.take(30) + it.takeLast(14) }.distinct()
                assertTrue("variant $features has gradient", indices.isNotEmpty())
                for (i in indices) {
                    model.loadParameters(params.copyOf().also { it[i] += 1e-6 }); val hi = model.loss(example)
                    model.loadParameters(params.copyOf().also { it[i] -= 1e-6 }); val lo = model.loss(example)
                    model.loadParameters(params)
                    val numeric = (hi - lo) / 2e-6
                    assertTrue("variant $features index $i analytic ${g.values[i]} numeric $numeric",
                        abs(numeric - g.values[i]) < 1e-6)
                }
            }
        }
    }

    @Test fun checkpointV2KeepsVariantAndEveryParameter() {
        for (features in variants) {
            val model = ContinuumResidualSpanCore(91, features)
            repeat(25) { model.train(examples[it % examples.size]) }
            val bytes = model.save()
            assertEquals(ContinuumResidualSpanCore.SERIALIZED_BYTES, bytes.size)
            val restored = ContinuumResidualSpanCore.restore(bytes)
            assertEquals(features, restored.features)
            assertEquals(model.updates, restored.updates)
            assertArrayEquals(model.parameters(), restored.parameters(), 0.0)
            for (e in examples) assertEquals(model.predict(e.evidence, e.question), restored.predict(e.evidence, e.question))
        }
    }

    @Test fun legacyV1CheckpointRestoresWithPr61Features() {
        val model = ContinuumResidualSpanCore(77)
        repeat(10) { model.train(examples[it % examples.size]) }
        val payload = ByteArrayOutputStream().also { bos ->
            DataOutputStream(bos).use { out ->
                out.writeInt(0x43535833); out.writeInt(1); out.writeInt(77); out.writeInt(model.updates)
                model.parameters().forEach(out::writeDouble)
            }
        }.toByteArray()
        assertEquals(ContinuumResidualSpanCore.LEGACY_PAYLOAD_BYTES, payload.size)
        val legacy = payload + MessageDigest.getInstance("SHA-256").digest(payload)
        val restored = ContinuumResidualSpanCore.restore(legacy)
        assertEquals(ContinuumResidualSpanCore.Features(), restored.features)
        assertArrayEquals(model.parameters(), restored.parameters(), 0.0)
    }

    @Test fun unknownOrContradictoryFlagsAreRejected() {
        val bytes = ContinuumResidualSpanCore(77).save()
        val tampered = bytes.copyOfRange(0, ContinuumResidualSpanCore.PAYLOAD_BYTES).also { it[11] = 5 }
        val signed = tampered + MessageDigest.getInstance("SHA-256").digest(tampered)
        assertTrue(runCatching { ContinuumResidualSpanCore.restore(signed) }.isFailure)
        assertTrue(runCatching { ContinuumResidualSpanCore.Features(entityProximity = true) }.isFailure)
    }

    @Test fun defaultVariantIsBitIdenticalToPr61Predictions() {
        // PR #61 semantics: the default variant must not change any prediction of the legacy feature set
        val a = ContinuumResidualSpanCore(77)
        val b = ContinuumResidualSpanCore(77, ContinuumResidualSpanCore.Features())
        repeat(30) { a.train(examples[it % 3]); b.train(examples[it % 3]) }
        assertArrayEquals(a.parameters(), b.parameters(), 0.0)
    }
}

/** Span-specific safety: exact offsets, no clause/punctuation crossing, length bound, stop/cue/asked-id exclusion. */
class ContinuumResidualSpanCoreSpanTest {
    private val spans = ContinuumResidualSpanCore.Features(positionFeatures = false, entityProximity = true,
        excludeCueWords = true, multiTokenSpans = true)

    @Test fun spanGoldUsesExactSourceOffsetsAndRejectsIllegalSpans() {
        val model = ContinuumResidualSpanCore(77, spans)
        val evidence = "Gerät AB12 hat ein Gewicht von 150 Kilogramm. Paket CD34 stammt von Anna Huber, Wien."
        model.loss(ContinuumResidualSpanCore.Example(evidence, "Welches Gewicht hat AB12?", "150 Kilogramm"))
        model.loss(ContinuumResidualSpanCore.Example(evidence, "Von wem stammt CD34?", "Anna Huber"))
        fun illegal(answer: String) = runCatching {
            model.loss(ContinuumResidualSpanCore.Example(evidence, "Von wem stammt CD34?", answer)) }.isFailure
        assertTrue("crosses the comma", illegal("Huber, Wien"))
        assertTrue("crosses the sentence boundary", illegal("Kilogramm. Paket"))
        assertTrue("contains a stop word", illegal("Gewicht von 150"))
        assertTrue("longer than three tokens", illegal("ein Gewicht von 150 Kilogramm"))
        assertTrue("contains the asked id", illegal("CD34 stammt"))
        assertEquals(0, model.updates)
    }

    @Test fun predictedSpanIsTheExactSubstringAndGradientsStayExact() {
        val model = ContinuumResidualSpanCore(77, spans)
        val e = ContinuumResidualSpanCore.Example("Roboter EF56 ist 12 Meter lang.", "Wie lang ist EF56?", "12 Meter")
        repeat(60) { model.train(e, rate = 0.05) }
        val p = model.predict(e.evidence, e.question)
        assertEquals("12 Meter", p.answer)
        assertEquals("12 Meter", e.evidence.substring(p.start, p.end))
        val g = model.gradient(e); val params = model.parameters()
        for (i in (0 until ContinuumResidualSpanCore.PARAMETERS).filter { abs(g.values[it]) > 1e-9 }.take(40)) {
            model.loadParameters(params.copyOf().also { it[i] += 1e-6 }); val hi = model.loss(e)
            model.loadParameters(params.copyOf().also { it[i] -= 1e-6 }); val lo = model.loss(e)
            model.loadParameters(params)
            assertTrue(abs((hi - lo) / 2e-6 - g.values[i]) < 1e-6)
        }
        val restored = ContinuumResidualSpanCore.restore(model.save())
        assertEquals(spans, restored.features)
        assertEquals(p, restored.predict(e.evidence, e.question))
    }
}
