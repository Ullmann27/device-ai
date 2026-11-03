package at.heinz.deviceai.chief

/** Compact hashes, never the raw owner conversation, code context or secrets. */
data class ChiefStrategyAttempt(
    val attemptId: String,
    val workerId: String,
    val sourceCommit: String,
    val failureSignatureSha256: String,
    val strategySha256: String
) {
    init {
        require(ChiefSpecialist.ID.matches(attemptId))
        require(ChiefSpecialist.ID.matches(workerId))
        require(Regex("[0-9a-f]{40}").matches(sourceCommit))
        require(Regex("[0-9a-f]{64}").matches(failureSignatureSha256))
        require(Regex("[0-9a-f]{64}").matches(strategySha256))
    }
}

enum class ChiefVerifiedStrategyOutcome { RED_CONFIRMED, GREEN_CONFIRMED }

/**
 * Returned by a trusted, independent host-side verifier that actually checks the pinned checkout,
 * unmodified acceptance tests and test artifacts. Not constructible from model statements as evidence.
 * SHA-256 alone is not a signature, and this object is not proof without the external verifier.
 */
data class ChiefStrategyAttestation(
    val attemptId: String,
    val workerId: String,
    val sourceCommit: String,
    val failureSignatureSha256: String,
    val strategySha256: String,
    val verifierId: String,
    val receiptSha256: String,
    val outcome: ChiefVerifiedStrategyOutcome,
    val testExitCode: Int
) {
    init {
        require(ChiefSpecialist.ID.matches(attemptId))
        require(ChiefSpecialist.ID.matches(workerId))
        require(ChiefSpecialist.ID.matches(verifierId))
        require(Regex("[0-9a-f]{40}").matches(sourceCommit))
        require(Regex("[0-9a-f]{64}").matches(failureSignatureSha256))
        require(Regex("[0-9a-f]{64}").matches(strategySha256))
        require(Regex("[0-9a-f]{64}").matches(receiptSha256))
        require(testExitCode in 0..255)
    }
}

enum class ChiefStrategyRecordResult {
    STORED, UNVERIFIED, INVALID_BINDING, DUPLICATE, CONFLICT, FULL
}

/**
 * Bounded experimental strategy ledger for Chief's task selection and CCC1.
 *
 * Never calls any model and never writes to the owner's existing memory. The verifier callback
 * must independently read/check the actual immutable Git/test receipt. Its default denies learning.
 * A reported DONE, a boolean in a JSON message or a syntactically valid hash are not trusted.
 *
 * Only a verified RED is a narrow retry veto, and only for EXACT same failure/strategy/source SHA.
 * Only a verified GREEN is a reusable strategy candidate; this does NOT authorize promotion
 * to RoutineBook or production code without separate owner approval.
 */
class ChiefProofGatedStrategyLedger(
    private val independentVerifier: (ChiefStrategyAttempt) -> ChiefStrategyAttestation? = { null },
    private val maxEntries: Int = 512
) {
    init { require(maxEntries in 1..4096) }

    private val entries = LinkedHashMap<String, Pair<ChiefStrategyAttempt, ChiefStrategyAttestation>>()
    private val usedReceipts = HashSet<String>()

    fun record(attempt: ChiefStrategyAttempt): ChiefStrategyRecordResult {
        // Never call an external verifier while holding the ledger monitor.
        val attestation = try { independentVerifier(attempt) } catch (_: Exception) { null }
            ?: return ChiefStrategyRecordResult.UNVERIFIED

        val exactBinding = attestation.attemptId == attempt.attemptId &&
            attestation.workerId == attempt.workerId &&
            attestation.sourceCommit == attempt.sourceCommit &&
            attestation.failureSignatureSha256 == attempt.failureSignatureSha256 &&
            attestation.strategySha256 == attempt.strategySha256 &&
            attestation.verifierId != attempt.workerId &&
            when (attestation.outcome) {
                ChiefVerifiedStrategyOutcome.GREEN_CONFIRMED -> attestation.testExitCode == 0
                ChiefVerifiedStrategyOutcome.RED_CONFIRMED -> attestation.testExitCode != 0
            }
        if (!exactBinding) return ChiefStrategyRecordResult.INVALID_BINDING

        return synchronized(this) {
            when {
                attempt.attemptId in entries || attestation.receiptSha256 in usedReceipts ->
                    ChiefStrategyRecordResult.DUPLICATE
                entries.values.any { (prior, proof) ->
                    prior.sourceCommit == attempt.sourceCommit &&
                        prior.failureSignatureSha256 == attempt.failureSignatureSha256 &&
                        prior.strategySha256 == attempt.strategySha256 &&
                        proof.outcome != attestation.outcome
                } -> ChiefStrategyRecordResult.CONFLICT
                entries.size >= maxEntries -> ChiefStrategyRecordResult.FULL
                else -> {
                    entries[attempt.attemptId] = attempt to attestation
                    usedReceipts += attestation.receiptSha256
                    ChiefStrategyRecordResult.STORED
                }
            }
        }
    }

    /**
     * A narrow, exact-match warning only. A change of source commit or failure signature
     * invalidates the veto instead of carrying old repair assumptions into new code.
     */
    @Synchronized fun shouldAvoid(
        sourceCommit: String,
        failureSignatureSha256: String,
        strategySha256: String
    ): Boolean {
        require(Regex("[0-9a-f]{40}").matches(sourceCommit))
        require(Regex("[0-9a-f]{64}").matches(failureSignatureSha256))
        require(Regex("[0-9a-f]{64}").matches(strategySha256))
        return entries.values.any { (attempt, attestation) ->
            attestation.outcome == ChiefVerifiedStrategyOutcome.RED_CONFIRMED &&
                attempt.sourceCommit == sourceCommit &&
                attempt.failureSignatureSha256 == failureSignatureSha256 &&
                attempt.strategySha256 == strategySha256
        }
    }

    /** Trusted GREEN evidence, not automatic owner-approved routine installation. */
    @Synchronized fun successfulStrategyHashes(
        sourceCommit: String,
        failureSignatureSha256: String,
        limit: Int = 8
    ): List<String> {
        require(Regex("[0-9a-f]{40}").matches(sourceCommit))
        require(Regex("[0-9a-f]{64}").matches(failureSignatureSha256))
        require(limit in 1..32)
        return entries.values.asSequence().filter { (attempt, attestation) ->
            attestation.outcome == ChiefVerifiedStrategyOutcome.GREEN_CONFIRMED &&
                attempt.sourceCommit == sourceCommit &&
                attempt.failureSignatureSha256 == failureSignatureSha256
        }.map { it.first.strategySha256 }.distinct().take(limit).toList()
    }

    @Synchronized fun recordCount(): Int = entries.size
}
