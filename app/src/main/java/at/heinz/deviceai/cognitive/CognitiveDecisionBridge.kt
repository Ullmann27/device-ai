package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumCognitiveRuntime
import at.heinz.deviceai.learning.ContinuumCognitiveTurn
import at.heinz.deviceai.learning.ContinuumMemoryHit
import java.util.Locale

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

        if (turn.grounded && (hits.isEmpty() || hits.size != ids.size)) return fallback(true)
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
        private val numbers = Regex("""\d+(?:[.,]\d+)*""")
        private val words = Regex("""[\p{L}\p{N}]+""")
        private val negations = setOf("nicht", "kein", "keine", "keinen",
            "keinem", "keiner", "keines", "nie", "niemals", "ohne")
        private val starters = setOf("der", "die", "das", "mein", "meine",
            "dein", "deine", "ein", "eine", "ja", "nein", "laut", "gemäß", "beleg")

        /** A conservative lexical anchor check, NOT a semantic proof of truth.
         * Checks the highest ranked authorized raw hit, not a formatted answer.
         */
        internal fun preservesEvidenceAnchors(hits: List<ContinuumMemoryHit>,
                                              candidate: String): Boolean {
            if (hits.isEmpty()) return false
            val original = hits.first().text
            val lower = original.lowercase(Locale.ROOT)
            val proposed = candidate.lowercase(Locale.ROOT)
            if (numbers.findAll(lower).map { it.value }.toList() !=
                numbers.findAll(proposed).map { it.value }.toList()) return false
            fun hasNegation(value: String): Boolean =
                words.findAll(value).any { it.value in negations }
            if (hasNegation(lower) != hasNegation(proposed)) return false
            val anchored = words.findAll(original).map { it.value }
                .filter { it.length >= 3 && it.first().isUpperCase() &&
                    it.lowercase(Locale.ROOT) !in starters }
                .map { it.lowercase(Locale.ROOT) }.toSet()
            val visible = words.findAll(proposed).map { it.value }.toSet()
            return anchored.all { it in visible }
        }
    }
}
