package at.heinz.deviceai.learning.synapse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Security and falsification cases. These do NOT imply learned natural-language generalization. */
class ContinuumSynapseZeroShadowTest {
    private val core = ContinuumSynapseZeroShadow

    @Test fun explicitTwoHopHasTwoVerifiedSourceSpans() {
        val text = "Roboter Z77 nutzt Modul M4. Für Modul M4 ist Kühlmittel Helium vorgeschrieben."
        val result = core.deliberate(text, "Welches Kühlmittel benötigt Z77?")
        assertEquals(ContinuumSynapseZeroShadow.Decision.TWO_HOP, result.decision)
        assertEquals("Helium", result.answer)
        assertEquals(2, result.proofs.size)
        assertEquals("M4", result.proofs[0].value)
        assertEquals("Helium", result.proofs[1].value)
        for (proof in result.proofs) assertTrue(core.verify(text, proof))
        assertTrue(result.proofs[0].clauseIndex != result.proofs[1].clauseIndex)
    }

    @Test fun brokenAndAmbiguousBridgesAlwaysAbstain() {
        val broken = "Roboter Z77 nutzt Modul M4. Für Modul M9 ist Kühlmittel Helium vorgeschrieben."
        assertEquals(ContinuumSynapseZeroShadow.Decision.ABSTAIN_NO_EVIDENCE, core.deliberate(broken, "Welches Kühlmittel benötigt Z77?").decision)
        val common = "Roboter Z77 nutzt Modul M4. Für Modul M4 ist Kühlmittel Helium vorgeschrieben. Für Modul M4 ist Kühlmittel Neon vorgeschrieben."
        assertEquals(ContinuumSynapseZeroShadow.Decision.ABSTAIN_CONFLICT, core.deliberate(common, "Welches Kühlmittel benötigt Z77?").decision)
    }

    @Test fun explicitLocationSelectionDoesNotUseForeignEntity() {
        val text = "Einheit R10 steht in Wien. Einheit R20 befindet sich in Salzburg."
        val answer = core.deliberate(text, "Wo steht R20?")
        assertEquals(ContinuumSynapseZeroShadow.Decision.DIRECT, answer.decision)
        assertEquals("Salzburg", answer.answer)
        assertEquals(1, answer.proofs.size)
        assertTrue(core.verify(text, answer.proofs.single()))
        assertEquals(ContinuumSynapseZeroShadow.Decision.ABSTAIN_NO_EVIDENCE, core.deliberate(text, "Wo steht R30?").decision)
    }

    @Test fun mentionsWithoutTypedRelationsAreNeverProof() {
        val text = "Roboter Z77, Modul M4 und Helium wurden im Protokoll erwähnt."
        assertEquals(ContinuumSynapseZeroShadow.Decision.ABSTAIN_NO_EVIDENCE, core.deliberate(text, "Welches Kühlmittel benötigt Z77?").decision)
    }

    @Test fun sourceNegationIsConservativelyBlocking() {
        val text = "Roboter Z77 nutzt nicht Modul M4. Für Modul M4 ist Kühlmittel Helium vorgeschrieben."
        assertEquals(ContinuumSynapseZeroShadow.Decision.ABSTAIN_NEGATION, core.deliberate(text, "Welches Kühlmittel benötigt Z77?").decision)
    }

    @Test fun manipulatedOffsetsAndModifiedEvidenceFailVerification() {
        val text = "Roboter Z77 nutzt Modul M4. Für Modul M4 ist Kühlmittel Helium vorgeschrieben."
        val proof = core.deliberate(text, "Welches Kühlmittel benötigt Z77?").proofs.last()
        assertFalse(core.verify(text, proof.copy(charStart = proof.charStart - 1)))
        assertFalse(core.verify(text, proof.copy(charEnd = proof.charEnd + 1)))
        assertFalse(core.verify(text, proof.copy(clauseIndex = 0)))
        assertFalse(core.verify(text.replace("Helium", "Neon"), proof))
        assertFalse(core.verify(text, proof.copy(subject = "M9")))
    }

    @Test fun adversarialInputsHaveNoAnswer() {
        val text = "Roboter Z77 nutzt Modul M4. Für Modul M4 ist Kühlmittel Helium vorgeschrieben."
        assertFalse(core.deliberate(text, "Welches Schutzgas benötigt Z77?").accepted)
        assertFalse(core.deliberate(text, "Wo befindet sich Z77?").accepted)
        assertFalse(core.deliberate(text, "Welches Kühlmittel benötigt Z77 und Z88?").accepted)
        assertFalse(core.deliberate(text, "Welches Kühlmittel benötigt Z77?" + "x".repeat(513)).accepted)
    }

    @Test fun holographyAndSinkhornAreMathDiagnosticsNotProofs() {
        val probe = SynapseHolographicProbe(16)
        val identity = DoubleArray(16).also { it[0] = 1.0 }
        val signal = DoubleArray(16) { it.toDouble() / 7.0 }
        val out = probe.convolution(identity, signal)
        for (i in signal.indices) assertEquals(signal[i], out[i], 1e-12)
        val unbound = probe.correlation(identity, out)
        for (i in signal.indices) assertEquals(signal[i], unbound[i], 1e-12)
        val equilibrium = probe.sinkhorn(arrayOf(doubleArrayOf(8.0, 1.0), doubleArrayOf(1.0, 8.0)), 10)
        for (row in equilibrium) assertEquals(1.0, row.sum(), 1e-9)
        for (j in 0..1) assertEquals(1.0, equilibrium.sumOf { it[j] }, 1e-9)
        assertTrue(equilibrium[0][0] > equilibrium[0][1])
    }
}
