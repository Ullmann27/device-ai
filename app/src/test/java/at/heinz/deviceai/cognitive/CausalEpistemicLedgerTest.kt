package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CausalEpistemicLedgerTest {
    @Test fun repeatedCoOccurrenceIsCorrelationNotCausation() {
        var r=CausalEpistemicLedger.Relation("heat","full-render","thermal-rise")
        repeat(4){ r=r.observe(true,true) }
        assertEquals(CausalEpistemicLedger.Rank.CORRELATION,r.rank())
        assertFalse(r.permitsStrongCausalLanguage())
    }

    @Test fun mechanismWithoutInterventionRemainsHypothesis() {
        var r=CausalEpistemicLedger.Relation("shader","shader-on","crash")
            .observe(true,true)
            .withMechanism("stacktrace-1")
        assertEquals(CausalEpistemicLedger.Rank.MECHANISTIC_HYPOTHESIS,r.rank())
        assertFalse(r.permitsStrongCausalLanguage())
    }

    @Test fun mechanismPlusInterventionConfirmsStrongCausalLanguage() {
        val r=CausalEpistemicLedger.Relation("shader","shader-on","crash")
            .observe(true,true)
            .withMechanism("stacktrace-1")
            .withIntervention("ablation-1")
        assertEquals(CausalEpistemicLedger.Rank.INTERVENTION_CONFIRMED,r.rank())
        assertTrue(r.permitsStrongCausalLanguage())
    }

    @Test fun counterexamplesPreventWeakCorrelationPromotion() {
        var r=CausalEpistemicLedger.Relation("heat","render","thermal")
        repeat(3){ r=r.observe(true,true) }
        repeat(2){ r=r.observe(true,false) }
        assertEquals(CausalEpistemicLedger.Rank.TEMPORAL_SEQUENCE,r.rank())
    }
}
