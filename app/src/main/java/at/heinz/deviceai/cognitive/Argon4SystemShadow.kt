package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumDialogueTurn
import at.heinz.deviceai.learning.ContinuumMemoryHit
import at.heinz.deviceai.research.argon4.DialogStateKernel
import at.heinz.deviceai.research.argon4.JustifiedRelationStore

enum class Argon4ShadowMode { OFF, SHADOW }
enum class Argon4ShadowStatus { OFF, NO_TURN, OBSERVED, NO_EVIDENCE, REJECTED_EVIDENCE, FAILED }

/** Counts only: no names, questions, rewrites, inferred facts or permission decisions. */
data class Argon4ShadowReport(
    val status: Argon4ShadowStatus,
    val version: Int = 1,
    val entities: Int = 0,
    val bindings: Int = 0,
    val wouldClarify: Boolean = false,
    val temporalScope: Boolean = false,
    val authorizedFacts: Int = 0,
    val derivedUnconfirmed: Int = 0,
    val contradictions: Int = 0
)

/** Owned by the existing ephemeral conversation context, not another memory store.
 * Rewrites never leave this observer. Bounded lifetime also bounds the prototype's
 * correction exclusions; resetting loses research context, never product context. */
class Argon4ConversationShadow(
    private val mode: Argon4ShadowMode = Argon4ShadowMode.SHADOW,
    private val kernelFactory: (String) -> DialogStateKernel = { DialogStateKernel(it) }
) {
    private var session: String? = null
    private var kernel: DialogStateKernel? = null
    private var turns = 0
    private var last = emptyReport()

    @Synchronized fun observeUser(sessionId: String, text: String): Argon4ShadowReport {
        if (mode == Argon4ShadowMode.OFF) return emptyReport().also { last = it }
        return runCatching {
            require(sessionId.isNotBlank() && text.trim().length in 1..512)
            if (session != sessionId || turns >= 64 || kernel == null) {
                kernel = kernelFactory(sessionId); session = sessionId; turns = 0
            }
            val k = requireNotNull(kernel)
            val result = k.observeUser(text)
            turns++
            Argon4ShadowReport(Argon4ShadowStatus.OBSERVED, entities = k.entities().size,
                bindings = result.bindings.size,
                wouldClarify = result.decision == DialogStateKernel.Decision.CLARIFY,
                temporalScope = result.temporal != null)
        }.getOrElse {
            kernel = null; session = null; turns = 0
            Argon4ShadowReport(Argon4ShadowStatus.FAILED)
        }.also { last = it }
    }

    @Synchronized fun observeAnswer(sessionId: String, text: String) {
        if (mode == Argon4ShadowMode.OFF || session != sessionId) return
        // Model mentions remain discourse only, never relation-store inputs.
        runCatching { kernel?.observeAnswer(text) }.onFailure {
            kernel = null; turns = 0; last = Argon4ShadowReport(Argon4ShadowStatus.FAILED)
        }
    }
    @Synchronized fun report(): Argon4ShadowReport = last
    @Synchronized fun clear() { kernel = null; session = null; turns = 0; last = emptyReport() }
    private fun emptyReport() = Argon4ShadowReport(if (mode == Argon4ShadowMode.OFF)
        Argon4ShadowStatus.OFF else Argon4ShadowStatus.NO_TURN)
}

/** Fresh, bounded projection of ONE already-authorized turn. Revoked facts cannot
 * survive in a retained relation graph because no graph is kept between turns. */
object Argon4EvidenceShadow {
    fun observe(turn: ContinuumDialogueTurn, hits: List<ContinuumMemoryHit>,
                mode: Argon4ShadowMode = Argon4ShadowMode.SHADOW): Argon4ShadowReport {
        if (mode == Argon4ShadowMode.OFF) return Argon4ShadowReport(Argon4ShadowStatus.OFF)
        if (!turn.grounded && turn.memoryIds.isEmpty() && hits.isEmpty())
            return Argon4ShadowReport(Argon4ShadowStatus.NO_EVIDENCE)
        if (!turn.grounded || hits.size !in 1..3 || hits.map { it.id } != turn.memoryIds ||
            hits.map { it.id }.distinct().size != hits.size || hits.any {
                it.text.isBlank() || it.text.length > 4096 || !it.score.isFinite() || it.score !in 0.0..1.0 ||
                    it.kind !in setOf("Bestätigte Nutzererinnerung", "Unbestätigte Quellenbeobachtung")
            }) return Argon4ShadowReport(Argon4ShadowStatus.REJECTED_EVIDENCE)
        return runCatching {
            // 3 base facts, 4 fixed two-clause rules, depth2: bound joins before
            // entering the research prototype, whose general inputs are unbounded.
            val store = JustifiedRelationStore(maxDerived = 32, maxDepth = 2)
            JustifiedRelationStore.kinshipRules().forEach(store::addRule)
            val facts = hits.filter { it.kind == "Bestätigte Nutzererinnerung" }
            facts.forEach { hit -> JustifiedRelationStore.parse(hit.text)?.let { store.assertFact(hit.id, it) } }
            Argon4ShadowReport(Argon4ShadowStatus.OBSERVED, authorizedFacts = facts.size,
                derivedUnconfirmed = store.derived().size,
                contradictions = store.contradictions(setOf("wohnt_in")).size)
        }.getOrElse { Argon4ShadowReport(Argon4ShadowStatus.FAILED) }
    }
}
