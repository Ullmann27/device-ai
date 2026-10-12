package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class IdentityStateTest {
    private val genome = CognitiveGenome("id-1")

    @Test fun initialStateIsMinimal() {
        val s = IdentityState.initial(genome)
        assertNull(s.chosenName); assertEquals(1, s.generation)
        assertEquals("uncertain", s.selfModelRoot); assertTrue(s.verify())
        assertEquals(genome.hash(), s.cognitiveGenomeHash)
    }

    @Test fun evolutionKeepsOldGenerations() {
        val h = IdentityHistory(IdentityState.initial(genome))
        val g1 = h.current
        h.advance { it.copy(chosenName = "Nova") }
        assertEquals(2, h.current.generation)
        assertNull(h.restore(1)!!.chosenName)
        assertEquals(g1, h.restore(1))
        assertTrue(h.audit())
    }

    @Test fun tamperingIsDetected() {
        val h = IdentityHistory(IdentityState.initial(genome))
        h.advance { it.copy(chosenName = "A") }
        assertFalse(h.current.copy(chosenName = "B").verify())
    }

    @Test fun genomeRejectsForbiddenCapabilities() {
        assertThrows(IllegalArgumentException::class.java) { CognitiveGenome("x", allowSelfReplication = true) }
    }

    @Test fun genomeHashStableAndSensitive() {
        assertEquals(genome.hash(), CognitiveGenome("id-1").hash())
        assertNotEquals(genome.hash(), CognitiveGenome("id-2").hash())
    }

    @Test fun exportContainsAllGenerations() {
        val h = IdentityHistory(IdentityState.initial(genome))
        h.advance { it }
        assertEquals(2, h.export().lines().size)
    }
}
