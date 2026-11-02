package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumCognitiveRuntime
import at.heinz.deviceai.learning.ContinuumCognitiveTurn
import at.heinz.deviceai.learning.ContinuumMemoryHit

/** Proposed text, never a newly confirmed fact. Tool use and memory writes are
 * forbidden in this isolated reasoning pass.
 */
data class VerifiedCognitiveResponse(
    val turn: ContinuumCognitiveTurn,
    val evidenceHits: List<ContinuumMemoryHit>,
    val finalAnswer: String,
    val usedLocalModel: Boolean,
    val anchorRejectedModelOutput: Boolean,
    val modelId: String?
)

/** Combines an already committed SWB1/CRE1 dialogue turn with one bounded local
 * reasoning pass. Critically, this class NEVER calls replyWithEvidence() again:
 * raw hits arrive from the same atomic turn created by cognitiveRuntime.reply().
 */
class CognitiveDecisionBridge(
    private val cognitiveRuntime: ContinuumCognitiveRuntime,
    private val reasoningAdapter: ContinuumReasoningAdapter?,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun respond(input: String, reasoningBudgetMillis: Long = 15_000L): VerifiedCognitiveResponse {
        val turn = cognitiveRuntime.reply(input)
        val ids = turn.dialogue.memoryIds.toSet()
        val hits = if (turn.grounded) turn.evidenceHits.filter { it.id in ids } else emptyList()
        fun fallback(rejected: Boolean = false, modelId: String? = null) =
            VerifiedCognitiveResponse(turn, hits, turn.answer, false, rejected, modelId)

        // No grounded memory means the model has no authority to invent a personal fact.
        // A greeting or unknown query must retain the deterministic reply.
        if (!turn.grounded) return fallback()
        if (hits.isEmpty() || hits.size != ids.size) return fallback(true)
        val adapter = reasoningAdapter ?: return fallback()
        val prompt = buildString {
            append(turn.localModelPrompt)
            if (hits.isNotEmpty()) {
                append("\nFreigegebene Belege – keine Fakten erfinden oder verändern:\n")
                hits.take(3).forEachIndexed { i, hit ->
                    append("Beleg ").append(i + 1).append(": ")
                    append(hit.text.take(250)).append("\n")
                    append("Quellentyp: ").append(hit.kind.take(80)).append("\n")
                }
            } else {
                append("\nEs liegt kein bestätigter Gedächtnisbeleg vor. ")
                append("Erfinde keine persönlichen Informationen.\n")
            }
        }.take(4_000)
        val start = nowMillis().coerceAtLeast(0L)
        val budget = reasoningBudgetMillis.coerceIn(1_000L, 60_000L)
        val result = runCatching {
            adapter.execute(
                ReasoningPassRequest(
                    taskId = "cognitive-turn-$start",
                    role = ReasoningPassRole.CANDIDATE,
                    prompt = prompt,
                    maxOutputChars = 1_200,
                    deadlineMillis = start + budget
                )
            )
        }.getOrNull() ?: return fallback()
        // Tool intent is forbidden in a purely linguistic reasoning pass, even
        // when a careless adapter returns a normally formatted text answer.
        if (result.attemptedToolProposals != 0) return fallback(true, result.modelId)
        val generated = result.text.trim()
        if (generated.isBlank()) return fallback()
        if (hits.isNotEmpty() && !preservesEvidenceAnchors(hits, generated))
            return fallback(true, result.modelId)
        return VerifiedCognitiveResponse(turn, hits, generated, true, false, result.modelId)
    }

    companion object {
        /** Entity/number overlap cannot establish semantic entailment (for example,
         * "in Graz" versus "outside Graz"). Only an exact evidence restatement,
         * allowing whitespace changes, may replace the deterministic fact answer.
         */
        internal fun preservesEvidenceAnchors(hits: List<ContinuumMemoryHit>, candidate: String): Boolean {
            if (hits.isEmpty()) return false
            fun normalized(value: String) = value.trim().replace(Regex("\\s+"), " ")
            return normalized(candidate) == normalized(hits.first().text)
        }
    }
}
