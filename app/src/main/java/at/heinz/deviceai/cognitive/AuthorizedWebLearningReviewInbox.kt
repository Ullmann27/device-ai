package at.heinz.deviceai.cognitive

/**
 * Keeps normal-chat web research available for the existing owner-reviewed
 * source-note dialog without granting any web text authority over app state.
 *
 * Evidence is memory-only, never durable until the owner separately approves
 * a single note in WebLearningJournal. Consent revocation also revokes review.
 */
class AuthorizedWebLearningReviewInbox {
    private data class Snapshot(
        val candidates: List<WebLearningCandidate>,
        val permitted: () -> Boolean
    )

    private var snapshot: Snapshot? = null

    @Synchronized
    fun publish(dossier: ResearchDossier, permitted: () -> Boolean): Boolean {
        // A fresh failed/revoked run must never leave old candidates visible.
        snapshot = null
        if (!permitted()) return false
        val candidates = WebLearningInbox.candidates(dossier).take(8)
        if (candidates.isEmpty() || !permitted()) return false
        val next = Snapshot(candidates.toList(), permitted)
        snapshot = next
        if (!permitted()) {
            snapshot = null
            return false
        }
        return true
    }

    @Synchronized
    fun candidates(): List<WebLearningCandidate> {
        val current = validSnapshot() ?: return emptyList()
        return current.candidates.toList()
    }

    @Synchronized
    fun canReview(candidate: WebLearningCandidate): Boolean {
        val current = validSnapshot() ?: return false
        return candidate in current.candidates && current.permitted()
    }

    @Synchronized
    fun clear() {
        snapshot = null
    }

    private fun validSnapshot(): Snapshot? {
        val current = snapshot ?: return null
        if (!current.permitted()) {
            snapshot = null
            return null
        }
        return current
    }
}
