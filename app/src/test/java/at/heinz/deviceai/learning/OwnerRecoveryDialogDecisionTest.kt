package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class OwnerRecoveryDialogDecisionTest {
    private val first = RecoveredOwnerFact("lx-" + "a".repeat(24), "a".repeat(64), "Ich trinke gerne Tee.")
    private val second = RecoveredOwnerFact("lx-" + "b".repeat(24), "b".repeat(64), "Ich wohne in Steyr.")
    private fun review() = OwnerRecoveryReview("exact-ledger-and-marker-fingerprint", listOf(first, second))
    private fun decision(review: OwnerRecoveryReview = review()) =
        OwnerRecoveryDialogDecision(review, { 1234L }, "owner-dialog-test")

    @Test fun openingOrEmptySelectionDoesNotCreateOwnerConsent() {
        var clockCalls = 0
        val decision = OwnerRecoveryDialogDecision(review(), { clockCalls++; 1234L }, "owner-dialog-test")
        assertEquals(0, decision.selectedCount)
        assertNull(decision.takeConfirmation())
        assertEquals(0, clockCalls)
        assertTrue(decision.select(first.id, true))
        assertTrue(decision.select(first.id, false))
        assertNull(decision.takeConfirmation())
        assertEquals(0, clockCalls)
    }

    @Test fun positiveDecisionBindsOnlyTheConcreteSelectedDisplayedHash() {
        val review = review()
        val decision = decision(review)
        assertSame(review, decision.review)
        assertTrue(decision.select(second.id, true))
        assertEquals(1, decision.selectedCount)
        val proof = decision.takeConfirmation()!!
        assertEquals(OwnerConfirmationSource.OWNER_PILL, proof.source)
        assertTrue(proof.approvedByOwner)
        assertEquals("owner-dialog-test", proof.turnId)
        assertEquals(1234L, proof.atMillis)
        assertEquals(review.fingerprint, proof.reviewFingerprint)
        assertEquals(mapOf(second.id to second.contentHash), proof.confirmedFactHashes)
    }

    @Test fun cancelAfterSelectionNeverProducesAConfirmation() {
        val decision = decision()
        decision.select(first.id, true)
        decision.cancel()
        assertEquals(0, decision.selectedCount)
        assertNull(decision.takeConfirmation())
        assertFalse(decision.select(second.id, true))
        assertNull(decision.takeConfirmation())
    }

    @Test fun positiveDecisionIsOneShotAndCannotBeChangedByLateClicks() {
        val decision = decision()
        decision.select(first.id, true)
        val proof = decision.takeConfirmation()!!
        assertFalse(decision.select(first.id, false))
        assertFalse(decision.select(second.id, true))
        decision.cancel()
        assertNull(decision.takeConfirmation())
        assertEquals(mapOf(first.id to first.contentHash), proof.confirmedFactHashes)
    }

    @Test fun undisplayedIdentitiesCannotEnterTheSelection() {
        val decision = decision()
        assertThrows(IllegalArgumentException::class.java) { decision.select("unseen", true) }
        assertNull(decision.takeConfirmation())
        assertEquals(0, decision.selectedCount)
    }

    @Test fun submittedHashesCannotBeMutatedAfterTheOwnerClick() {
        val decision = decision()
        decision.select(first.id, true)
        val proof = decision.takeConfirmation()!!
        @Suppress("UNCHECKED_CAST")
        val mutable = proof.confirmedFactHashes as MutableMap<String, String>
        assertThrows(UnsupportedOperationException::class.java) { mutable[second.id] = second.contentHash }
        assertEquals(mapOf(first.id to first.contentHash), proof.confirmedFactHashes)
    }

    @Test fun ambiguousDuplicateDisplayIdentitiesAreRejected() {
        val ambiguous = OwnerRecoveryReview("duplicate", listOf(first, first.copy(content = "Different content")))
        assertThrows(IllegalArgumentException::class.java) { decision(ambiguous) }
    }

    @Test fun invalidClockDoesNotCreateAConsentProof() {
        val decision = OwnerRecoveryDialogDecision(review(), { -1L }, "owner-dialog-test")
        decision.select(first.id, true)
        assertThrows(IllegalArgumentException::class.java) { decision.takeConfirmation() }
    }
}
