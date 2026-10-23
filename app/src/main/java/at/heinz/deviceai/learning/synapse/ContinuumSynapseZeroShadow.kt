package at.heinz.deviceai.learning.synapse

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.sqrt

/** Experimental, read-only reasoner. Never reads or writes SWB1, CDX1, CSX1 or owner files.
 * Explicit-source proof is mandatory; scores and competing activations cannot create facts. */
object ContinuumSynapseZeroShadow {
    enum class Decision { DIRECT, TWO_HOP, ABSTAIN_NO_EVIDENCE, ABSTAIN_CONFLICT, ABSTAIN_NEGATION }
    enum class Relation { LOCATION, USES_MODULE, COOLANT, GAS }
    data class Edge(val subject: String, val relation: Relation, val value: String,
                    val start: Int, val end: Int, val clauseIndex: Int)
    data class Proof(val relation: Relation, val subject: String, val value: String,
                     val charStart: Int, val charEnd: Int, val clauseIndex: Int,
                     val sourceSha256: String)
    data class Result(val decision: Decision, val answer: String?, val proofs: List<Proof>,
                      val candidateCount: Int, val sourceSha256: String) {
        val accepted: Boolean get() = decision == Decision.DIRECT || decision == Decision.TWO_HOP
    }

    private val identifier = Regex("\\b[A-Za-z]{1,12}[-]?[0-9]+[A-Za-z0-9-]*\\b")
    private val subjectToken = "([A-Za-z]{1,12}[-]?[0-9]+[A-Za-z0-9-]*)"
    private val moduleToken = subjectToken
    private val textValue = "([\\p{L}][\\p{L}0-9-]{1,49})"
    private val location = Regex("(?:Einheit|Roboter)\\s+$subjectToken\\s+(?:steht\\s+in|befindet\\s+sich\\s+in)\\s+$textValue", RegexOption.IGNORE_CASE)
    private val uses = Regex("(?:Einheit|Roboter)\\s+$subjectToken\\s+(?:nutzt|verwendet)\\s+(?:das\\s+)?(?:Modul|Baugruppe)\\s+$moduleToken", RegexOption.IGNORE_CASE)
    private val provision = Regex("(?:Für\\s+)?(?:Modul|Baugruppe)\\s+$moduleToken\\s+(?:ist\\s+)?(Kühlmittel|Schutzgas)\\s+$textValue\\s+(?:vorgeschrieben|erforderlich|notwendig)", RegexOption.IGNORE_CASE)
    private val needs = Regex("(?:Modul|Baugruppe)\\s+$moduleToken\\s+(?:benötigt|braucht)\\s+(?:das\\s+)?(Kühlmittel|Schutzgas)\\s+$textValue", RegexOption.IGNORE_CASE)
    private val unsafeNegation = Regex("\\b(?:nicht|kein|keine|keinen|keinem|keiner|ohne|weder|statt)\\b", RegexOption.IGNORE_CASE)
    private val breakClause = Regex("[.!?]+")
    private fun canonical(s: String) = s.uppercase(Locale.ROOT)
    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    private data class Clause(val content: String, val offset: Int, val index: Int)
    private fun clauses(text: String): List<Clause> {
        val result = ArrayList<Clause>()
        var previous = 0
        for (delimiter in breakClause.findAll(text)) {
            if (delimiter.range.first > previous) result += Clause(text.substring(previous, delimiter.range.first), previous, result.size)
            previous = delimiter.range.last + 1
        }
        if (previous < text.length) result += Clause(text.substring(previous), previous, result.size)
        return result
    }
    private fun edges(text: String): Pair<List<Edge>, Boolean> {
        val result = ArrayList<Edge>()
        var negated = false
        for (clause in clauses(text)) {
            if (unsafeNegation.containsMatchIn(clause.content)) { negated = true; continue }
            fun capture(regex: Regex, relation: Relation, subjectGroup: Int, valueGroup: Int) {
                for (m in regex.findAll(clause.content)) {
                    val span = m.groups[valueGroup] ?: continue
                    result += Edge(canonical(m.groupValues[subjectGroup]), relation, span.value,
                                   clause.offset + span.range.first, clause.offset + span.range.last + 1, clause.index)
                }
            }
            capture(location, Relation.LOCATION, 1, 2)
            capture(uses, Relation.USES_MODULE, 1, 2)
            for (regex in listOf(provision, needs)) for (m in regex.findAll(clause.content)) {
                val span = m.groups[3] ?: continue
                val relation = if (m.groupValues[2].equals("Kühlmittel", true)) Relation.COOLANT else Relation.GAS
                result += Edge(canonical(m.groupValues[1]), relation, span.value,
                               clause.offset + span.range.first, clause.offset + span.range.last + 1, clause.index)
            }
        }
        return result to negated
    }

    private fun questionRelation(question: String): Relation? = when {
        Regex("\\b(?:kühlmittel|kuehlmittel)\\b", RegexOption.IGNORE_CASE).containsMatchIn(question) -> Relation.COOLANT
        Regex("\\b(?:schutzgas)\\b", RegexOption.IGNORE_CASE).containsMatchIn(question) -> Relation.GAS
        Regex("\\b(?:wo|standort)\\b", RegexOption.IGNORE_CASE).containsMatchIn(question) -> Relation.LOCATION
        else -> null
    }

    /** Proof checks original UTF-16 offsets, cited source SHA, exact case-sensitive span, and clause source. */
    fun verify(text: String, proof: Proof): Boolean {
        if (proof.sourceSha256 != sha256(text)) return false
        if (proof.charStart !in 0..text.length || proof.charEnd !in 0..text.length) return false
        if (proof.charStart >= proof.charEnd) return false
        if (text.substring(proof.charStart, proof.charEnd) != proof.value) return false
        val parts = clauses(text)
        if (proof.clauseIndex !in parts.indices) return false
        val clause = parts[proof.clauseIndex]
        if (proof.charStart < clause.offset || proof.charEnd > clause.offset + clause.content.length) return false
        if (unsafeNegation.containsMatchIn(clause.content)) return false
        return edges(text).first.any { it.subject == proof.subject && it.relation == proof.relation &&
            it.value == proof.value && it.start == proof.charStart && it.end == proof.charEnd &&
            it.clauseIndex == proof.clauseIndex }
    }

    /** At most one distinct supported answer. No speculative pronoun coreference or transitive assumption. */
    fun deliberate(text: String, question: String): Result {
        val digest = sha256(text)
        fun stop(reason: Decision, n: Int = 0) = Result(reason, null, emptyList(), n, digest)
        if (text.isBlank() || question.isBlank() || text.length > 16384 || question.length > 512) return stop(Decision.ABSTAIN_NO_EVIDENCE)
        val relation = questionRelation(question) ?: return stop(Decision.ABSTAIN_NO_EVIDENCE)
        val subjects = identifier.findAll(question).map { canonical(it.value) }.distinct().toList()
        if (subjects.size != 1) return stop(Decision.ABSTAIN_NO_EVIDENCE)
        val (found, negated) = edges(text)
        // Conservative global veto until scope of contrastive negation is parsed safely.
        if (negated) return stop(Decision.ABSTAIN_NEGATION)
        val subject = subjects.single()
        val direct = found.filter { it.subject == subject && it.relation == relation }
        data class Candidate(val value: String, val chain: List<Edge>)
        val candidates = ArrayList<Candidate>()
        direct.forEach { candidates += Candidate(it.value, listOf(it)) }
        if (relation != Relation.LOCATION) {
            val bridges = found.filter { it.subject == subject && it.relation == Relation.USES_MODULE }
            for (bridge in bridges) for (tail in found) {
                if (tail.relation != relation || tail.subject != canonical(bridge.value) || tail.clauseIndex == bridge.clauseIndex) continue
                candidates += Candidate(tail.value, listOf(bridge, tail))
            }
        }
        if (candidates.isEmpty()) return stop(Decision.ABSTAIN_NO_EVIDENCE)
        val distinct = candidates.map { canonical(it.value) }.distinct()
        if (distinct.size != 1) return stop(Decision.ABSTAIN_CONFLICT, candidates.size)
        val chosen = candidates.sortedWith(compareBy<Candidate> { it.chain.size }.thenBy { it.chain.last().start }).first()
        val proofs = chosen.chain.map { edge -> Proof(edge.relation, edge.subject, edge.value, edge.start, edge.end, edge.clauseIndex, digest) }
        if (!proofs.all { verify(text, it) }) return stop(Decision.ABSTAIN_NO_EVIDENCE)
        return Result(if (chosen.chain.size == 1) Decision.DIRECT else Decision.TWO_HOP, chosen.value, proofs, candidates.size, digest)
    }
}

/** A self-contained algebra probe; energy signals never override source-proof rejection. */
class SynapseHolographicProbe(val dimension: Int = 16) {
    init { require(dimension in 4..128) }
    fun convolution(a: DoubleArray, b: DoubleArray): DoubleArray {
        require(a.size == dimension && b.size == dimension && a.all(Double::isFinite) && b.all(Double::isFinite))
        return DoubleArray(dimension) { k -> (0 until dimension).sumOf { j -> a[j] * b[(k - j + dimension) % dimension] } }
    }
    fun correlation(a: DoubleArray, b: DoubleArray): DoubleArray {
        require(a.size == dimension && b.size == dimension && a.all(Double::isFinite) && b.all(Double::isFinite))
        return DoubleArray(dimension) { k -> (0 until dimension).sumOf { j -> a[j] * b[(j + k) % dimension] } }
    }
    /** This normalizes activations, NOT factual truth. Only square, all-positive matrices. */
    fun sinkhorn(matrix: Array<DoubleArray>, rounds: Int = 5): Array<DoubleArray> {
        val n = matrix.size
        require(n in 1..64 && rounds in 1..100 && matrix.all { row -> row.size == n && row.all { it.isFinite() && it > 0.0 } })
        val result = Array(n) { matrix[it].copyOf() }
        repeat(rounds) {
            for (i in 0 until n) { val sum = result[i].sum(); for (j in 0 until n) result[i][j] /= sum }
            for (j in 0 until n) { val sum = (0 until n).sumOf { result[it][j] }; for (i in 0 until n) result[i][j] /= sum }
        }
        return result
    }
    fun cosine(a: DoubleArray, b: DoubleArray): Double {
        require(a.size == dimension && b.size == dimension)
        val prod = a.indices.sumOf { a[it] * b[it] }
        val an = sqrt(a.sumOf { it * it }); val bn = sqrt(b.sumOf { it * it })
        return if (an == 0.0 || bn == 0.0) 0.0 else prod / (an * bn)
    }
}
