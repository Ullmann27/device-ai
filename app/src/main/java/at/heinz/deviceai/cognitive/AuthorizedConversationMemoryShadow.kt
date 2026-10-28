package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumDialogueTurn
import at.heinz.deviceai.learning.ContinuumMemoryHit
import at.heinz.deviceai.learning.ContinuumShadowScore

/**
 * Diagnostic only. The SWB1 dialogue and CRE1 approval gate remain authoritative.
 * The caller supplies the already-authorized hits of ONE completed conversation turn;
 * this observer cannot read the learning ledger, request additional memories, or
 * alter the dialogue, prompt, tool permissions, model weights or memory store.
 */
enum class AuthorizedHybridShadowStatus {
    NO_AUTHORIZED_EVIDENCE,
    REJECTED_EVIDENCE_IDENTITY,
    REJECTED_CRE1_SCORES,
    LEXICAL_ONLY,
    COMPARED_WITH_CRE1,
    EVALUATION_FAILED
}

/** Deliberately contains no query, memory ID, source URL or private content. */
data class AuthorizedHybridShadowReport(
    val status: AuthorizedHybridShadowStatus,
    val authorizedCount: Int = 0,
    val lexicalReturned: Int = 0,
    val cre1Returned: Int = 0,
    val lexicalTopMatchesDialogue: Boolean = false,
    val cre1TopMatchesDialogue: Boolean = false,
    val cre1WouldChangeTop: Boolean = false
)

object AuthorizedConversationMemoryShadow {
    private const val MAX_AUTHORIZED_HITS = 3
    private const val MAX_EVIDENCE_CHARS = 4_096
    private val OWNER_KIND = "Bestätigte Nutzererinnerung"
    private val SOURCE_KIND = "Unbestätigte Quellenbeobachtung"

    fun observe(
        turn: ContinuumDialogueTurn,
        authorizedHits: List<ContinuumMemoryHit>,
        cre1Scores: List<ContinuumShadowScore> = emptyList()
    ): AuthorizedHybridShadowReport {
        if (!turn.grounded && turn.memoryIds.isEmpty() && authorizedHits.isEmpty() &&
            cre1Scores.isEmpty()) {
            return AuthorizedHybridShadowReport(AuthorizedHybridShadowStatus.NO_AUTHORIZED_EVIDENCE)
        }

        // Never use an ID-only match to admit a new hit: the complete input list
        // must come from the same already-committed SWB1 conversation turn.
        if (!turn.grounded || turn.memoryIds.size !in 1..MAX_AUTHORIZED_HITS ||
            authorizedHits.map { it.id } != turn.memoryIds ||
            authorizedHits.map { it.id }.toSet().size != authorizedHits.size ||
            turn.resolvedQuery.isBlank() || turn.resolvedQuery.length > 512 ||
            authorizedHits.any {
                it.text.isBlank() || it.text.length > MAX_EVIDENCE_CHARS ||
                    it.kind != OWNER_KIND && it.kind != SOURCE_KIND ||
                    !it.score.isFinite() || it.score !in 0.0..1.0
            }) {
            return AuthorizedHybridShadowReport(AuthorizedHybridShadowStatus.REJECTED_EVIDENCE_IDENTITY)
        }

        val useCre1 = cre1Scores.isNotEmpty()
        if (useCre1 && (cre1Scores.size != authorizedHits.size ||
            cre1Scores.map { it.memoryId }.toSet() != turn.memoryIds.toSet() ||
            cre1Scores.map { it.memoryId }.toSet().size != cre1Scores.size ||
            cre1Scores.any { score ->
                !score.semanticScore.isFinite() || !score.lexicalScore.isFinite() ||
                    authorizedHits.none { it.id == score.memoryId && it.score == score.lexicalScore }
            })) {
            return AuthorizedHybridShadowReport(AuthorizedHybridShadowStatus.REJECTED_CRE1_SCORES)
        }

        // Ephemeral scoring view only. The actual ledger is NEVER copied or
        // mutated; no fabricated VERIFIED status, timestamp, or source reference.
        // DEVICE_LOCAL_ONLY prevents widening the privacy scope of this view.
        val records = authorizedHits.map { hit ->
            MemoryRecord(
                id = hit.id,
                type = MemoryType.SEMANTIC,
                content = hit.text,
                provenance = if (hit.kind == OWNER_KIND) ProvenanceType.USER_PROVIDED
                    else ProvenanceType.EXTERNAL_SOURCE,
                confidence = hit.score,
                createdAt = 0L,
                updatedAt = 0L,
                privacyClass = PrivacyClass.DEVICE_LOCAL_ONLY,
                validationState = ValidationState.UNVERIFIED
            )
        }
        val allowed = setOf(PrivacyClass.DEVICE_LOCAL_ONLY)
        val lexical = HybridMemoryRetriever.retrieve(
            query = turn.resolvedQuery,
            records = records,
            limit = records.size,
            nowMillis = turn.at,
            allowedPrivacy = allowed
        )
        val byId = cre1Scores.associateBy { it.memoryId }
        val candidate = if (useCre1) HybridMemoryRetriever.retrieve(
            query = turn.resolvedQuery,
            records = records,
            limit = records.size,
            nowMillis = turn.at,
            allowedPrivacy = allowed,
            semanticScorer = { _, record ->
                byId.getValue(record.id).semanticScore.coerceIn(0.0, 1.0)
            }
        ) else lexical

        val firstAuthorized = turn.memoryIds.first()
        val lexicalFirst = lexical.firstOrNull()?.memory?.id
        val candidateFirst = candidate.firstOrNull()?.memory?.id
        return AuthorizedHybridShadowReport(
            status = if (useCre1) AuthorizedHybridShadowStatus.COMPARED_WITH_CRE1
                else AuthorizedHybridShadowStatus.LEXICAL_ONLY,
            authorizedCount = authorizedHits.size,
            lexicalReturned = lexical.size,
            cre1Returned = candidate.size,
            lexicalTopMatchesDialogue = lexicalFirst == firstAuthorized,
            cre1TopMatchesDialogue = candidateFirst == firstAuthorized,
            cre1WouldChangeTop = useCre1 && candidateFirst != lexicalFirst
        )
    }
}
