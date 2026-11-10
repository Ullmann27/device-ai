package at.heinz.deviceai.cognitive

import java.security.MessageDigest
import java.util.Locale

/**
 * Ephemeral navigation over owner turns. It stores topic cues/labels only, never
 * facts, model answers, tool grants or executable old instructions.
 */
internal class LocalConversationSituationTracker(
    private val normalization: ConversationSituationModel.CueNormalization =
        ConversationSituationModel.CueNormalization.DEFAULT
) {
    sealed interface Hint {
        data class Resolved(val threadId: String, val label: String, val score: Float): Hint
        data class Ambiguous(val labels: List<String>): Hint
        data object NoMatch: Hint
    }

    private val model = ConversationSituationModel(normalization = normalization)

    fun reset(preserveWorkingTask: Boolean = false) = model.reset(preserveWorkingTask)

    fun bindWorkingTask(seed: WorkingTaskSeed) = model.bindWorkingTask(seed)
    fun workingTaskSnapshot() = model.workingTaskSnapshot()
    fun resolveWorkingTask(request: String) = model.resolveWorkingTask(request)
    fun invalidateWorkingTaskReference(referenceId: String) = model.invalidateWorkingTaskReference(referenceId)
    fun isWorkingTaskPlanCurrent(plan: WorkingTaskPlan) = model.isWorkingTaskPlanCurrent(plan)

    fun observeOwnerTurn(text: String) {
        val clean = text.trim().take(512)
        val turn = model.nextTurn()
        val words = words(clean)
        val cues = words.mapNotNull(normalization::normalize).toSet()
        if (cues.isEmpty()) return

        val snapshot = model.snapshot()
        val active = snapshot.currentThreadId?.let { id ->
            snapshot.threads.firstOrNull { it.id == id }
        }
        if (active != null) {
            val overlap = cues.intersect(active.cues)
            val sameNarrowTopic = overlap.size >= 2 ||
                (overlap.size == 1 && cues.size == 1 && active.cues.size == 1)
            if (sameNarrowTopic) {
                model.openThread(active.id, active.label, cues, atTurn = turn)
                return
            }
        }

        model.pauseActive()
        val id = threadId(cues)
        val existing = model.snapshot().threads.firstOrNull { it.id == id }
        model.openThread(
            id = id,
            label = existing?.label ?: label(words),
            cues = cues,
            atTurn = turn
        )
    }

    fun resolveReturn(text: String): Hint {
        val clean = text.trim().take(512)
        if (!RETURN_CUE.containsMatchIn(clean)) return Hint.NoMatch
        return when (val result = model.resolveReturn(clean, atTurn = model.snapshot().turn + 1)) {
            is ConversationSituationModel.Resolution.Resolved ->
                Hint.Resolved(result.thread.id, result.thread.label, result.score)
            is ConversationSituationModel.Resolution.Ambiguous ->
                Hint.Ambiguous(result.candidates.map { it.label }.distinct().take(2))
            ConversationSituationModel.Resolution.NoMatch -> Hint.NoMatch
        }
    }

    fun renderHint(text: String): String = when (val hint = resolveReturn(text)) {
        is Hint.Resolved ->
            "\nSITUATIONSHINWEIS — nur Gesprächsnavigation, kein Fakt/Beleg/Toolrecht:\n" +
                "Der explizite Rückbezug passt eindeutig zum früheren Thema «" +
                safe(hint.label) + "». Nutze das nur zur Themenzuordnung; fehlen die eigentlichen " +
                "Ausgangsdaten, frage danach statt sie zu erfinden.\n"
        is Hint.Ambiguous ->
            "\nSITUATIONSHINWEIS — Rückbezug mehrdeutig, keine automatische Bindung:\n" +
                "Mögliche frühere Themen: " + hint.labels.joinToString(" / ") { "«" + safe(it) + "»" } +
                ". Frage gezielt nach, welches gemeint ist.\n"
        Hint.NoMatch -> ""
    }

    internal fun snapshot(): ConversationSituationModel.Situation = model.snapshot()

    /** Raw lowercase words keep labels readable; matching uses the shared normalization. */
    private fun words(text: String): List<String> =
        WORD.findAll(text.lowercase(Locale.GERMAN))
            .map { it.value }
            .filter { it.length >= 4 && it !in STOP }
            .distinct()
            .take(12)
            .toList()

    private fun label(words: List<String>): String =
        words.take(3).joinToString(" · ") { it.take(10) }.take(120).ifBlank { "früheres Thema" }

    private fun threadId(cues: Set<String>): String {
        val canonical = cues.sorted().joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
        return "topic-$digest"
    }

    private fun safe(value: String): String =
        value.take(120).map { c ->
            when {
                c == '[' -> '('
                c == ']' -> ')'
                c.isISOControl() -> ' '
                else -> c
            }
        }.joinToString("")

    companion object {
        private val WORD = Regex("[\\p{L}\\p{N}]+")
        private val RETURN_CUE = Regex(
            "(?iu)\\b(?:vorhin|davor|dort\\s+weiter|weiter\\s+bei|zurück\\s+zu|" +
                "zurueck\\s+zu|wieder\\s+zu|fortsetzen)\\b"
        )
        private val STOP = setOf(
            "aber","auch","bitte","dann","dass","davor","dort","eine","einen","einer",
            "für","haben","jetzt","machen","mach","noch","oder","schon","weiter","wieder","fehler","offen","problem",
            "zurück","zurueck","vorhin","fortsetzen","beim","beim","über","ueber","unser",
            "unsere","dieses","dieser","diese","wurde","werden","soll","sollen"
        )
    }
}
