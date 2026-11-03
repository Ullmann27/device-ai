package at.heinz.deviceai.chief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChiefProofGatedStrategyLedgerTest {
    private val sha40 = "1".repeat(40)
    private val issue = "2".repeat(64)
    private val strategy = "3".repeat(64)
    private fun attempt(id: String) = ChiefStrategyAttempt(id, "coder", sha40, issue, strategy)
    private fun receipt(
        a: ChiefStrategyAttempt,
        outcome: ChiefVerifiedStrategyOutcome,
        exit: Int,
        signer: String = "verifier",
        hash: String = "a".repeat(64)
    ) = ChiefStrategyAttestation(a.attemptId, a.workerId, a.sourceCommit,
        a.failureSignatureSha256, a.strategySha256, signer, hash, outcome, exit)

    @Test fun noIndependentVerifierMeansNoLearningAtAll() {
        val ledger = ChiefProofGatedStrategyLedger()
        assertEquals(ChiefStrategyRecordResult.UNVERIFIED, ledger.record(attempt("a1")))
        assertEquals(0, ledger.recordCount())
        assertFalse(ledger.shouldAvoid(sha40, issue, strategy))
        assertEquals(emptyList<String>(), ledger.successfulStrategyHashes(sha40, issue))
    }

    @Test fun exactVerifiedRedVetoAppliesOnlyToSameSourceFailureAndStrategy() {
        val ledger = ChiefProofGatedStrategyLedger(independentVerifier = {
            receipt(it, ChiefVerifiedStrategyOutcome.RED_CONFIRMED, 1)
        })
        assertEquals(ChiefStrategyRecordResult.STORED, ledger.record(attempt("a1")))
        assertTrue(ledger.shouldAvoid(sha40, issue, strategy))
        assertFalse(ledger.shouldAvoid("f".repeat(40), issue, strategy))
        assertFalse(ledger.shouldAvoid(sha40, "f".repeat(64), strategy))
        assertFalse(ledger.shouldAvoid(sha40, issue, "f".repeat(64)))
        assertEquals(emptyList<String>(), ledger.successfulStrategyHashes(sha40, issue))
    }

    @Test fun trustedGreenCanBecomeCandidateButNeverImplicitOwnerApprovedRoutine() {
        val ledger = ChiefProofGatedStrategyLedger(independentVerifier = {
            receipt(it, ChiefVerifiedStrategyOutcome.GREEN_CONFIRMED, 0)
        })
        assertEquals(ChiefStrategyRecordResult.STORED, ledger.record(attempt("a1")))
        assertEquals(listOf(strategy), ledger.successfulStrategyHashes(sha40, issue))
        assertFalse(ledger.shouldAvoid(sha40, issue, strategy))
        assertEquals(1, ledger.recordCount())
    }

    @Test fun rejectsSelfVerificationForgedBindingAndContradictoryExitCodes() {
        val own = ChiefProofGatedStrategyLedger(independentVerifier = {
            receipt(it, ChiefVerifiedStrategyOutcome.GREEN_CONFIRMED, 0, signer = it.workerId)
        })
        assertEquals(ChiefStrategyRecordResult.INVALID_BINDING, own.record(attempt("self")))
        val wrong = ChiefProofGatedStrategyLedger(independentVerifier = {
            receipt(it, ChiefVerifiedStrategyOutcome.RED_CONFIRMED, 1)
                .copy(sourceCommit = "f".repeat(40))
        })
        assertEquals(ChiefStrategyRecordResult.INVALID_BINDING, wrong.record(attempt("wrong")))
        val fakeGreen = ChiefProofGatedStrategyLedger(independentVerifier = {
            receipt(it, ChiefVerifiedStrategyOutcome.GREEN_CONFIRMED, 1)
        })
        assertEquals(ChiefStrategyRecordResult.INVALID_BINDING, fakeGreen.record(attempt("fakegreen")))
        val fakeRed = ChiefProofGatedStrategyLedger(independentVerifier = {
            receipt(it, ChiefVerifiedStrategyOutcome.RED_CONFIRMED, 0)
        })
        assertEquals(ChiefStrategyRecordResult.INVALID_BINDING, fakeRed.record(attempt("fakered")))
    }

    @Test fun replayedTaskAndSameReceiptDoNotCreateNewTrainingExamples() {
        val ledger = ChiefProofGatedStrategyLedger(independentVerifier = {
            receipt(it, ChiefVerifiedStrategyOutcome.RED_CONFIRMED, 2)
        })
        assertEquals(ChiefStrategyRecordResult.STORED, ledger.record(attempt("a1")))
        assertEquals(ChiefStrategyRecordResult.DUPLICATE, ledger.record(attempt("a1")))
        assertEquals(ChiefStrategyRecordResult.DUPLICATE, ledger.record(attempt("a2")))
        assertEquals(1, ledger.recordCount())
    }

    @Test fun boundedStorageAndVerifierFailureStayFailClosed() {
        val ledger = ChiefProofGatedStrategyLedger(maxEntries = 1, independentVerifier = {
            receipt(it, ChiefVerifiedStrategyOutcome.RED_CONFIRMED, 1,
                hash = if (it.attemptId == "a1") "a".repeat(64) else "b".repeat(64))
        })
        assertEquals(ChiefStrategyRecordResult.STORED, ledger.record(attempt("a1")))
        assertEquals(ChiefStrategyRecordResult.FULL, ledger.record(attempt("a2")))
        assertEquals(1, ledger.recordCount())
        val errors = ChiefProofGatedStrategyLedger(independentVerifier = {
            throw IllegalStateException("verifier unavailable")
        })
        assertEquals(ChiefStrategyRecordResult.UNVERIFIED, errors.record(attempt("a3")))
        assertEquals(0, errors.recordCount())
    }
}
