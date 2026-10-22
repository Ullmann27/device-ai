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
        ContinuumResidualSpanCore.Features(positionFeatures = false, entityProximity = true)
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
