package at.heinz.deviceai.intelligence.ewm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integration regression on product classes. Research freezes and TEST4 are untouched. */
class Ewm1ProductFixTest {
    private fun workspace() = Ewm1Workspace(listOf(
        "person-anna" to "Meine Schwester heißt Anna.",
        "instrument-anna" to "Anna spielt Cello.",
        "instrument-rita" to "Meine Nachbarin Rita spielt Geige."
    )) { _, subset -> subset.toList() }

    @Test
    fun anUnresolvedPersonalPronounCannotBorrowAnotherConversationsReferent() {
        val conversation = workspace()
        val unresolved = conversation.decide("Was spielt sie?")
        // The integrated product already has a stricter abstention than PR227's
        // alternative CLARIFY fix. Preserve it rather than replacing the guard.
        assertEquals("ABSTAIN", unresolved.decision)
        assertEquals("UNRESOLVED_PRONOUN", unresolved.reason)
        assertTrue(unresolved.cited.isEmpty())

        assertEquals("instrument-anna", conversation.decide("Was spielt Anna?").cited.first())
        assertEquals("instrument-anna", conversation.decide("Was spielt sie?").cited.first())

        val separate = workspace().decide("Was spielt sie?")
        assertEquals("ABSTAIN", separate.decision)
        assertTrue(separate.cited.isEmpty())
    }
}
