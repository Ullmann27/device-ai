package at.heinz.deviceai.intelligence.ewm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Product fixes on top of the research freeze; each one was measured not to change the DEV corpora. */
class Ewm1ProductFixTest {
    private fun ws(vararg facts: String) = Ewm1Workspace(facts.mapIndexed { i, f -> "f$i" to f }) { _, subset -> subset.toList() }

    @Test fun aPronounWithoutReferentInThisConversationAsksBackInsteadOfMatchingTheVerb() {
        val w = ws("Meine Schwester heißt Anna.", "Anna spielt Cello.", "Meine Nachbarin Rita spielt Geige.")
        val first = w.decide("Was spielt sie?")
        assertEquals("CLARIFY", first.decision)
        assertEquals("UNRESOLVED_REFERENCE", first.reason)
        assertTrue(first.cited.isEmpty())
        // with a referent in the conversation the pronoun resolves as before
        w.decide("Wo wohnt Anna?")
        assertEquals("f1", w.decide("Was spielt sie?").cited.first())
    }
}
