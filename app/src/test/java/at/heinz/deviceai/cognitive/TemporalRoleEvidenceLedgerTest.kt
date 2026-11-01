package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class TemporalRoleEvidenceLedgerTest {
    private fun fact(value: String, valid: Long, observed: Long, id: String,
                     validUntil: Long? = null): TemporalRoleEvidenceLedger.Fact {
        val text = "Anna ist in " + value + "."
        return TemporalRoleEvidenceLedger.Fact("Anna", "ist in", value, valid, observed,
            id, 1, text, TemporalRoleEvidenceLedger.sha256(text), validUntil)
    }

    @Test fun supportsWhatWasKnownAndWhatWasActuallyValidAtDistinctTimes() {
        val all = listOf(fact("Graz", 10, 15, "a"), fact("Linz", 20, 30, "b"))
        val historical = TemporalRoleEvidenceLedger.lookup(all,"Anna","ist in",11,40)
        assertEquals("Graz", (historical as TemporalRoleEvidenceLedger.Lookup.Found).value)
        val priorKnowledge = TemporalRoleEvidenceLedger.lookup(all,"Anna","ist in",31,20)
        assertEquals("Graz", (priorKnowledge as TemporalRoleEvidenceLedger.Lookup.Found).value)
        val current = TemporalRoleEvidenceLedger.lookup(all,"Anna","ist in",31,40)
        assertEquals("Linz", (current as TemporalRoleEvidenceLedger.Lookup.Found).value)
    }

    @Test fun simultaneousConflictingSourcesForceAbstention() {
        val all = listOf(fact("Graz", 20, 20, "a"), fact("Wien", 20, 20, "b"))
        val result = TemporalRoleEvidenceLedger.lookup(all,"Anna","ist in",31,40)
        assertTrue(result is TemporalRoleEvidenceLedger.Lookup.Conflict)
    }

    @Test fun invalidSourceHashAndTimeRangesCannotBecomeEvidence() {
        val good = fact("Graz",20,20,"s")
        assertTrue(runCatching {good.copy(sourceText="Anna ist in Wien.")}.isFailure)
        assertTrue(runCatching {good.copy(validUntilMillis=10)}.isFailure)
        assertTrue(TemporalRoleEvidenceLedger.lookup(listOf(good),"Emma","ist in",31,40)
            is TemporalRoleEvidenceLedger.Lookup.NoEvidence)
    }
}
