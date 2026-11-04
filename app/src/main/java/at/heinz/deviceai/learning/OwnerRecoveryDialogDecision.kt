package at.heinz.deviceai.learning

import java.util.Collections
import java.util.UUID

/** One displayed review and one explicit button decision. Selection alone grants no consent. */
internal class OwnerRecoveryDialogDecision(
    val review: OwnerRecoveryReview,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val turnId: String = "memory-review-${UUID.randomUUID()}"
) {
    private enum class State { OPEN, SUBMITTED, CANCELLED }

    private val displayed = review.facts.associateBy { it.id }
    private val selected = LinkedHashSet<String>()
    private var state = State.OPEN

    init {
        require(displayed.size == review.facts.size) { "Review contains duplicate fact identities" }
        require(turnId.matches(Regex("[A-Za-z0-9_.:-]{1,64}")))
    }

    val selectedCount: Int
        @Synchronized get() = selected.size

    @Synchronized
    fun select(id: String, checked: Boolean): Boolean {
        require(id in displayed) { "Selection must come from this displayed review" }
        if (state != State.OPEN) return false
        if (checked) selected.add(id) else selected.remove(id)
        return true
    }

    @Synchronized
    fun cancel() {
        if (state == State.OPEN) {
            state = State.CANCELLED
            selected.clear()
        }
    }

    /** The Android helper calls this ONLY from its explicit positive-button listener. */
    @Synchronized
    fun takeConfirmation(): OwnerReconfirmationProof? {
        if (state != State.OPEN || selected.isEmpty()) return null
        val atMillis = nowMillis()
        require(atMillis >= 0) { "Owner decision needs a valid timestamp" }
        val hashes = LinkedHashMap<String, String>()
        review.facts.filter { it.id in selected }.forEach { hashes[it.id] = it.contentHash }
        val proof = OwnerReconfirmationProof(
            source = OwnerConfirmationSource.OWNER_PILL,
            approvedByOwner = true,
            turnId = turnId,
            atMillis = atMillis,
            reviewFingerprint = review.fingerprint,
            confirmedFactHashes = Collections.unmodifiableMap(hashes)
        )
        state = State.SUBMITTED
        return proof
    }
}
