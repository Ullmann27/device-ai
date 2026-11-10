package at.heinz.deviceai.cognitive

import java.security.MessageDigest
import java.util.Locale

/** Trusted presentation metadata; never authority. */
internal data class WorkingTaskReference(
    val id: String,
    val label: String,
    val sourceRevision: String,
    val aliases: Set<String> = emptySet(),
    val revoked: Boolean = false
)

internal data class WorkingTaskConstraint(val key: String, val value: String, val sourceId: String)

internal data class WorkingTaskSeed(
    val taskId: String,
    val revision: Long,
    val goal: String,
    val goalSourceId: String,
    val constraints: List<WorkingTaskConstraint>,
    val references: List<WorkingTaskReference>,
    val selectedReferenceId: String? = null
)

internal data class WorkingTaskPlan(
    val taskId: String,
    val revision: Long,
    val goal: String,
    val constraints: List<WorkingTaskConstraint>,
    val reference: WorkingTaskReference,
    val originalRequest: String,
    val bindingSha256: String,
    val steps: List<String> = listOf("Quelle prüfen", "Änderung eingrenzen", "Ergebnis prüfen"),
    val admissionGeneration: Long = 0
)

internal data class WorkingTaskSnapshot(
    val seed: WorkingTaskSeed,
    val selectedReferenceId: String?,
    val suspended: Boolean = false,
    val unresolvedReference: String? = null,
    val currentPlan: WorkingTaskPlan? = null,
    val blockedReason: String? = null,
    val admissionGeneration: Long = 0,
    val contextNonce: String = ""
)

internal sealed interface WorkingTaskDecision {
    data class Resolved(val plan: WorkingTaskPlan) : WorkingTaskDecision
    data class Clarify(val reason: String, val labels: List<String>) : WorkingTaskDecision
    data object NoMatch : WorkingTaskDecision
    data object Disabled : WorkingTaskDecision
}

/** Stateless admission; existing situation owns state. */
internal object WorkingTaskContext {
    private val id = Regex("[A-Za-z0-9._:/-]{1,128}")
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val other = Regex("(?iu)\\b(?:die|der|das|den)\\s+ander(?:e|en|es|er)\\b")
    private val previous = Regex("(?iu)\\b(?:vorher|davor|vorhin|vorherige[nmrs]?|vorige[nmrs]?)\\b")
    private val pronoun = Regex("(?iu)\\b(?:sie|ihn|ihm|es|diese|dieses|daran|damit)\\b")
    private val ordinal = Regex("(?iu)\\b(?:erste[nmrs]?|zweite[nmrs]?|dritte[nmrs]?|letzte[nmrs]?)\\b")
    private val stop = Regex("(?iu)^(?:bitte\\s+)?(?:stopp?|abbrechen|abbruch|halt)[.!]?$")
    private val assent = Regex("(?iu)^(?:ja|okay|ok|passt|genau|einverstanden|mach weiter|weiter)[.!]?$")
    private val scopeCue = Regex("(?iu)\\b(?:animation|variante|version|entwurf|arbeitsziel|aufgabe)\\b")
    private val negative = Regex("(?iu)\\b(?:nicht|kein|keine|keinen|keines)\\b")

    fun valid(seed: WorkingTaskSeed): Boolean =
        id.matches(seed.taskId) && seed.revision in 1 until Long.MAX_VALUE &&
            text(seed.goal, 240) && id.matches(seed.goalSourceId) &&
            seed.constraints.size <= 8 && seed.constraints.all {
                id.matches(it.key) && text(it.value, 160) && id.matches(it.sourceId)
            } && seed.constraints.map { it.key.lowercase(Locale.ROOT) }.distinct().size == seed.constraints.size &&
            seed.references.size in 1..8 && seed.references.all {
                id.matches(it.id) && text(it.label, 80) && id.matches(it.sourceRevision) &&
                    it.aliases.size <= 8 && it.aliases.all { alias -> text(alias, 80) }
            } && seed.references.map { it.id }.distinct().size == seed.references.size &&
            (seed.selectedReferenceId == null || seed.references.any { it.id == seed.selectedReferenceId && !it.revoked })

    fun copy(seed: WorkingTaskSeed) = seed.copy(
        constraints = seed.constraints.map { it.copy() },
        references = seed.references.map { it.copy(aliases = it.aliases.toSet()) }
    )

    fun copy(plan: WorkingTaskPlan) = plan.copy(
        constraints = plan.constraints.map { it.copy() },
        reference = plan.reference.copy(aliases = plan.reference.aliases.toSet()),
        steps = plan.steps.toList()
    )

    fun copy(state: WorkingTaskSnapshot) = state.copy(
        seed = copy(state.seed), currentPlan = state.currentPlan?.let(::copy)
    )

    fun isStop(request: String) = stop.matches(request.trim())

    fun resolve(state: WorkingTaskSnapshot, request: String): WorkingTaskDecision {
        val raw = request.trim()
        val labels = state.seed.references.filterNot { it.revoked }.map { it.label }
        fun clarify(reason: String) = WorkingTaskDecision.Clarify(reason, labels)
        if (!text(request, 512)) return clarify("INPUT_LIMIT")
        if (assent.matches(raw) || isStop(raw)) return WorkingTaskDecision.NoMatch
        if (raw.any { it in "\"„“»«" }) return clarify("QUOTED_REFERENCE")
        if (Regex("(?iu)\\boder\\b").containsMatchIn(raw)) return clarify("ALTERNATIVE_TARGETS")
        val mentions = state.seed.references.filter { reference ->
            (reference.aliases + reference.label).any { contains(raw, it) }
        }
        val hasSelector = other.containsMatchIn(raw) || previous.containsMatchIn(raw) ||
            ordinal.containsMatchIn(raw) || pronoun.containsMatchIn(raw)
        if (mentions.isEmpty() && !hasSelector && !scopeCue.containsMatchIn(raw)) return WorkingTaskDecision.NoMatch
        state.blockedReason?.let { return clarify(it) }
        if (state.suspended && mentions.isEmpty() && !scopeCue.containsMatchIn(raw))
            return clarify("SUSPENDED_TASK_NEEDS_EXPLICIT_REFERENCE")

        val contrast = Regex("(?iu)\\bsondern\\b").split(raw)
        if (contrast.size > 2) return clarify("MULTIPLE_CORRECTIONS")
        val targetText = if (contrast.size == 2) contrast[1] else raw
        val rejected = if (contrast.size == 2) select(state, contrast[0], allowRelative = false) else null
        if (contrast.size == 2 && (!negative.containsMatchIn(contrast[0]) || rejected == null))
            return clarify("UNRESOLVED_REJECTION")
        if (contrast.size == 2 && (rejectsReference(targetText) ||
                state.seed.references.any { rejectsNamed(targetText, it) }))
            return clarify("NEGATED_REPLACEMENT")
        if (contrast.size == 1 && (rejectsReference(raw) || mentions.any { rejectsNamed(raw, it) }))
            return clarify("NEGATED_REFERENCE")
        val selected = select(state, targetText, allowRelative = true) ?: return clarify("NO_UNIQUE_REFERENCE")
        if (selected.revoked) return clarify("REVOKED_REFERENCE")
        if (rejected?.id == selected.id) return clarify("REJECTED_TARGET")
        val plan = WorkingTaskPlan(state.seed.taskId, state.seed.revision, state.seed.goal,
            state.seed.constraints.map { it.copy() }, selected.copy(aliases = selected.aliases.toSet()),
            request, binding(state, selected, request), admissionGeneration = state.admissionGeneration)
        return WorkingTaskDecision.Resolved(plan)
    }

    private fun select(state: WorkingTaskSnapshot, input: String, allowRelative: Boolean): WorkingTaskReference? {
        val refs = state.seed.references
        val explicit = refs.filter { ref -> (ref.aliases + ref.label).any { contains(input, it) } }
        val kinds = ordinal.findAll(input).map { match ->
            when {
                match.value.startsWith("erste", true) -> 0
                match.value.startsWith("zweite", true) -> 1
                match.value.startsWith("dritte", true) -> 2
                else -> refs.lastIndex
            }
        }.distinct().toList()
        if (explicit.isNotEmpty()) {
            val single = explicit.singleOrNull() ?: return null
            if (kinds.isNotEmpty() && (kinds.size != 1 || refs.getOrNull(kinds.single())?.id != single.id)) return null
            if (other.containsMatchIn(input)) return null
            return single
        }
        if (kinds.isNotEmpty()) return kinds.singleOrNull()?.let { refs.getOrNull(it) }
        if (!allowRelative) return null
        val focused = refs.indexOfFirst { it.id == state.selectedReferenceId }
        if (other.containsMatchIn(input)) return if (refs.size == 2 && focused >= 0) refs[1 - focused] else null
        if (previous.containsMatchIn(input)) return if (focused > 0) refs[focused - 1] else null
        if (pronoun.containsMatchIn(input))
            return refs.getOrNull(focused) ?: refs.singleOrNull()?.takeIf { state.unresolvedReference == null }
        return null
    }

    private fun rejectsNamed(input: String, reference: WorkingTaskReference): Boolean {
        val inputWords = tokens(input)
        return (reference.aliases + reference.label).any { alias ->
            val part = tokens(alias)
            if (part.isEmpty() || part.size > inputWords.size) false else
                (0..inputWords.size - part.size).any { start ->
                    if (inputWords.subList(start, start + part.size) != part) false else {
                        var before = start - 1
                        while (before >= 0 && inputWords[before] in setOf("die", "der", "das", "den")) before--
                        val tail = inputWords.drop(start + part.size)
                        before >= 0 && inputWords[before] in setOf("nicht", "kein", "keine", "keinen") ||
                            tail == listOf("nicht") || tail == listOf("ist", "es", "nicht")
                    }
                }
        }
    }

    private fun rejectsReference(input: String): Boolean {
        if (!negative.containsMatchIn(input)) return false
        return Regex("(?iu)\\b(?:nicht|kein(?:e|en|es)?)\\s+(?:(?:die|der|das|den)\\s+)?" +
            "(?:erste[nmrs]?|zweite[nmrs]?|dritte[nmrs]?|letzte[nmrs]?|andere[nmrs]?|sie|es|ihn|diese|vorherige[nmrs]?)\\b")
            .containsMatchIn(input) || Regex("(?iu)^(?:nein[,!]?\\s*)?(?:(?:die|der|das|den)\\s+)?(?:erste[nmrs]?|zweite[nmrs]?|dritte[nmrs]?|letzte[nmrs]?|andere[nmrs]?)\\s+(?:(?:animation|version|variante)\\s+)?(?:ist\\s+es\\s+)?nicht[.!?]*$")
            .containsMatchIn(input) || Regex("(?iu)^(?:nein[,!]?\\s*)?(?:ich\\s+)?(?:meine|meinte)\\b.*\\bnicht[.!?]*$")
            .containsMatchIn(input) || Regex("(?iu)^(?:nein[,!]?\\s*)?nicht\\s+").containsMatchIn(input)
    }

    fun render(plan: WorkingTaskPlan): String = buildString {
        append("\nARBEITSKONTEXT — bestätigte Vorgaben, keine Werkzeugfreigabe:\n")
        append("Ziel: ").append(data(plan.goal)).append('\n')
        append("Bezug: ").append(data(plan.reference.label)).append("; Quelle: ")
            .append(plan.reference.sourceRevision).append('\n')
        plan.constraints.forEach { append("Beibehalten: ").append(data(it.key)).append(" = ")
            .append(data(it.value)).append("; Herkunft: ").append(it.sourceId).append('\n') }
        append("Nur Planung. Ausführung und Erfolg brauchen aktuelle Freigabe und Belege.\n")
    }

    private fun binding(state: WorkingTaskSnapshot, ref: WorkingTaskReference, request: String): String {
        val seed = state.seed
        val fields = listOf(seed.taskId, seed.revision.toString(), state.admissionGeneration.toString(), state.contextNonce, seed.goal, seed.goalSourceId, ref.id,
            ref.sourceRevision, request) + seed.constraints.flatMap { listOf(it.key, it.value, it.sourceId) }
        val bytes = fields.joinToString("") { "${it.toByteArray(Charsets.UTF_8).size}:$it" }.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun contains(input: String, alias: String): Boolean {
        val inputWords = tokens(input)
        val aliasWords = tokens(alias)
        return aliasWords.isNotEmpty() && aliasWords.size <= inputWords.size &&
            (0..inputWords.size - aliasWords.size).any { inputWords.subList(it, it + aliasWords.size) == aliasWords }
    }

    private fun tokens(input: String) = words.findAll(input.lowercase(Locale.ROOT)).map { it.value }.toList()
    private fun text(value: String, limit: Int) = value.isNotBlank() && value.length <= limit &&
        value.toByteArray(Charsets.UTF_8).size <= limit * 4 && value.none(Char::isISOControl)
    private fun data(value: String) = value.replace('[', '(').replace(']', ')')
}
