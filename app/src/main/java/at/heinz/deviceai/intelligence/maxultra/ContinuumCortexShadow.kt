package at.heinz.deviceai.intelligence.maxultra

import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Experimental, RAM-only online linear predictor. This is numerical adaptation, not a
 * replacement for dialogue memory, factual reasoning, or owner-approved model training.
 */
class EphemeralFastWeightCore(private val maxFrobeniusNorm: Double = 2.5) {
    private val weights = DoubleArray(DIM * DIM)
    private var previous: DoubleArray? = null
    private var focusedSubject: String? = null
    var updates: Int = 0
        private set

    init { require(maxFrobeniusNorm.isFinite() && maxFrobeniusNorm in 0.1..10.0) }

    @Synchronized fun observe(vector: DoubleArray, explicitSubject: String? = null): Double {
        require(vector.size == DIM && vector.all { it.isFinite() && it in -100.0..100.0 })
        val norm = sqrt(vector.sumOf { it * it })
        require(norm.isFinite() && norm > 1e-8)
        val input = DoubleArray(DIM) { vector[it] / norm }
        val cleanSubject = explicitSubject?.trim()
        require(cleanSubject == null || cleanSubject.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")))
        val old = previous
        if (old != null) {
            // L = 0.5 ||W * previous - input||² + 0.5 * 0.002 ||W||²
            // dL/dW_rc = (prediction_r - input_r) * previous_c + 0.002 * W_rc.
            // Unlike the supplied W*previous+input formula, gradients do NOT vanish at W=0.
            val next = weights.copyOf()
            for (r in 0 until DIM) {
                var prediction = 0.0
                for (c in 0 until DIM) prediction += weights[r * DIM + c] * old[c]
                val error = (prediction - input[r]).coerceIn(-2.0, 2.0)
                for (c in 0 until DIM) {
                    val i = r * DIM + c
                    val gradient = error * old[c] + 0.002 * weights[i]
                    next[i] = weights[i] - 0.08 * gradient
                }
            }
            val nextNorm = sqrt(next.sumOf { it * it })
            check(nextNorm.isFinite())
            if (nextNorm > maxFrobeniusNorm) {
                val scale = maxFrobeniusNorm / nextNorm
                for (i in next.indices) next[i] *= scale
            }
            check(next.all { it.isFinite() })
            next.copyInto(weights)
            updates++
        }
        previous = input
        if (cleanSubject != null) focusedSubject = cleanSubject
        return weightNorm()
    }

    @Synchronized fun focusedSubjectFor(query: String): String? {
        if (query.length > 1200 || query.any { it == '\u0000' }) return null
        return if (Regex("\\b(er|sie|es|ihn|dort|davon|dieser|diese|dieses)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(query)) focusedSubject else null
    }

    @Synchronized fun weightNorm(): Double = sqrt(weights.sumOf { it * it })
    @Synchronized fun reset() { weights.fill(0.0); previous = null; focusedSubject = null; updates = 0 }
    companion object { const val DIM = 16 }
}

/** Caller-reviewed provenance with SHA-256 of the EXACT structured edge bytes. */
data class ReviewedKnowledgeEdge(
    val sourceId: String,
    val subject: String,
    val relation: Relation,
    val target: String,
    val evidenceSha256: String,
    val ownerReviewed: Boolean
) {
    enum class Relation { LOCATED_IN, CITY, NOMINAL_GRID_FREQUENCY }

    fun integrityValid(): Boolean =
        ownerReviewed && sourceId.matches(Regex("[A-Za-z0-9._-]{1,96}")) &&
            listOf(subject, target).all { it.length in 1..80 && it.isNotBlank() &&
                it.none { char -> char.isISOControl() } } &&
            evidenceSha256 == pin(sourceId, subject, relation, target)

    companion object {
        fun pin(id: String, subject: String, relation: Relation, target: String): String =
            sha256(listOf(id, subject, relation.name, target).joinToString("\u001f"))
    }
}

/**
 * Finite, typed evidence traversal. It demonstrates a graph query, NOT validated
 * latent continuous reasoning. Relations are deliberately not composed arbitrarily.
 */
class EvidenceBoundedThoughtSearch(edges: List<ReviewedKnowledgeEdge>) {
    enum class Question { CITY, NOMINAL_GRID_FREQUENCY_AT_LOCATION }
    data class Query(val subject: String, val question: Question)
    data class Conclusion(val value: String, val edgeIds: List<String>, val fingerprint: String) {
        val hops: Int get() = edgeIds.size
    }

    private val graph: List<ReviewedKnowledgeEdge>
    val fingerprint: String

    init {
        require(edges.size in 1..64 && edges.all(ReviewedKnowledgeEdge::integrityValid))
        require(edges.map { it.sourceId }.toSet().size == edges.size)
        graph = edges.toList()
        fingerprint = sha256(graph.map { it.evidenceSha256 }.sorted().joinToString("|"))
    }

    /** Different supported conclusions => abstain. No unbounded branch exploration. */
    fun solve(query: Query): Conclusion? {
        if (query.subject.isBlank() || query.subject.length > 80) return null
        val patterns = when (query.question) {
            Question.CITY -> listOf(
                listOf(ReviewedKnowledgeEdge.Relation.LOCATED_IN, ReviewedKnowledgeEdge.Relation.CITY),
                listOf(ReviewedKnowledgeEdge.Relation.CITY))
            Question.NOMINAL_GRID_FREQUENCY_AT_LOCATION -> listOf(
                listOf(ReviewedKnowledgeEdge.Relation.LOCATED_IN, ReviewedKnowledgeEdge.Relation.CITY,
                    ReviewedKnowledgeEdge.Relation.NOMINAL_GRID_FREQUENCY),
                listOf(ReviewedKnowledgeEdge.Relation.CITY, ReviewedKnowledgeEdge.Relation.NOMINAL_GRID_FREQUENCY),
                listOf(ReviewedKnowledgeEdge.Relation.NOMINAL_GRID_FREQUENCY))
        }
        val matches = mutableListOf<Pair<String, List<String>>>()
        var ambiguousPrefix = false
        fun walk(subject: String, sequence: List<ReviewedKnowledgeEdge.Relation>,
                 at: Int, ids: List<String>, visited: Set<String>) {
            if (at == sequence.size) { matches.add(subject to ids); return }
            val outgoing = graph.filter { edge ->
                edge.relation == sequence[at] && edge.subject.equals(subject, ignoreCase = true)
            }
            if (outgoing.map { it.target.lowercase() }.distinct().size > 1) ambiguousPrefix = true
            for (edge in outgoing) {
                if (edge.target.lowercase() !in visited) {
                    walk(edge.target, sequence, at + 1, ids + edge.sourceId,
                        visited + edge.target.lowercase())
                }
            }
        }
        for (pattern in patterns) walk(query.subject, pattern, 0, emptyList(), setOf(query.subject.lowercase()))
        if (ambiguousPrefix || matches.isEmpty() || matches.map { it.first.lowercase() }.distinct().size != 1) return null
        val path = matches.sortedWith(compareBy<Pair<String, List<String>>> { it.second.size }
            .thenBy { it.second.joinToString("|") }).first()
        return Conclusion(path.first, path.second, fingerprint)
    }
}

/**
 * Offline wake/sleep research cache: explicit holdouts are necessary; no optimizer,
 * production checkpoint, persistent weight update, or actual curriculum triplets.
 */
class WakeSleepShadowHabitCore(private val planner: EvidenceBoundedThoughtSearch) {
    data class Probe(val query: EvidenceBoundedThoughtSearch.Query, val expected: String?)
    data class Report(val accepted: Boolean, val positiveProbes: Int, val negativeProbes: Int,
                      val reason: String, val activeHabits: Int)

    private val staged = linkedMapOf<EvidenceBoundedThoughtSearch.Query, EvidenceBoundedThoughtSearch.Conclusion>()
    private val habits = linkedMapOf<EvidenceBoundedThoughtSearch.Query, EvidenceBoundedThoughtSearch.Conclusion>()

    @Synchronized fun stage(query: EvidenceBoundedThoughtSearch.Query): Boolean {
        val found = planner.solve(query) ?: return false
        if (found.hops < 2 || staged.size >= 32) return false
        staged[query] = found
        return true
    }

    @Synchronized fun read(query: EvidenceBoundedThoughtSearch.Query): EvidenceBoundedThoughtSearch.Conclusion? {
        val cached = habits[query]
        if (cached != null && cached.fingerprint == planner.fingerprint) return cached
        return planner.solve(query)
    }

    @Synchronized fun consolidate(probes: List<Probe>): Report {
        val positive = probes.count { it.expected != null }
        val negative = probes.size - positive
        if (staged.isEmpty() || probes.size !in 3..128 || positive == 0 || negative == 0 ||
            staged.keys.none { key -> probes.any { it.query == key && it.expected != null } }) {
            return Report(false, positive, negative, "INSUFFICIENT_INDEPENDENT_HOLDOUT", habits.size)
        }
        val candidate = LinkedHashMap(habits).apply { putAll(staged) }
        fun answer(query: EvidenceBoundedThoughtSearch.Query,
                   cache: Map<EvidenceBoundedThoughtSearch.Query, EvidenceBoundedThoughtSearch.Conclusion>): String? {
            val entry = cache[query]
            return if (entry != null && entry.fingerprint == planner.fingerprint) entry.value
                   else planner.solve(query)?.value
        }
        val verified = probes.all { probe ->
            val expected = probe.expected
            val baseline = planner.solve(probe.query)?.value
            baseline == expected && answer(probe.query, candidate) == expected
        }
        if (!verified) return Report(false, positive, negative, "HOLDOUT_MISMATCH_NO_PROMOTION", habits.size)
        habits.clear(); habits.putAll(candidate); staged.clear()
        return Report(true, positive, negative, "RAM_ONLY_RESEARCH_CACHE_ACCEPTED", habits.size)
    }

    @Synchronized fun habitCount(): Int = habits.size
    @Synchronized fun reset() { staged.clear(); habits.clear() }
}

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
