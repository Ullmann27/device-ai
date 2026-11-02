package at.heinz.deviceai.learning

import java.security.MessageDigest
import java.util.Locale

/** A small, explicit language; ordinary prose is never silently promoted to a rule. */
data class KnowledgeAtom(val subject: String, val relation: String, val value: String)
data class KnowledgeLiteral(val atom: KnowledgeAtom, val negative: Boolean = false)
data class KnowledgeStatement(val literal: KnowledgeLiteral, val universal: Boolean)
enum class KnowledgeStatus { SUPPORTED, REFUTED, CONFLICT, UNKNOWN, LIMIT }
data class KnowledgeEvidence(val id: String, val text: String)
data class KnowledgeAnswer(
    val status: KnowledgeStatus,
    val evidence: List<KnowledgeEvidence> = emptyList(),
    val steps: List<String> = emptyList(),
    val ruleChecks: Int = 0
) {
    fun describe(): String = buildString {
        append(when (status) {
            KnowledgeStatus.SUPPORTED -> if (steps.isEmpty()) "BESTÄTIGT gespeichert" else "ABGELEITET"
            KnowledgeStatus.REFUTED -> "GEGENAUSSAGE belegt"
            KnowledgeStatus.CONFLICT -> "WIDERSPRUCH – ungeklärt"
            KnowledgeStatus.UNKNOWN -> "UNBEKANNT – keine widerspruchsfreie Ableitung"
            KnowledgeStatus.LIMIT -> "UNGEKLÄRT – Rechengrenze erreicht"
        })
        append(". Gilt relativ zu deinen ausdrücklich gespeicherten Angaben; keine unabhängige Wahrheitsprüfung.")
        steps.forEach { append("\n→ ").append(it) }
        evidence.forEach { append("\nQuelle ").append(it.id.take(12)).append(": ").append(it.text) }
    }
}

object KnowledgeSyntax {
    private val field = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{0,63}")
    fun literal(text: String): KnowledgeLiteral? {
        if (text.length > 220) return null
        val parts = text.split('|').map { it.trim().lowercase(Locale.ROOT) }
        if (parts.size != 3) return null
        val negative = parts[1].startsWith("nicht ")
        val relation = if (negative) parts[1].removePrefix("nicht ") else parts[1]
        if (!field.matches(parts[0]) || !field.matches(relation) || !field.matches(parts[2])) return null
        return KnowledgeLiteral(KnowledgeAtom(parts[0], relation, parts[2]), negative)
    }
    fun statement(text: String): KnowledgeStatement? {
        if (text.length > 320) return null
        val prefix = text.substringBefore(':').trim().lowercase(Locale.ROOT)
        if (prefix !in setOf("fakt", "regel") || ':' !in text) return null
        return literal(text.substringAfter(':'))?.let { KnowledgeStatement(it, prefix == "regel") }
    }
    fun isStructured(text: String): Boolean =
        text.trim().startsWith("Fakt:", true) || text.trim().startsWith("Regel:", true)
}

/** Rebuilds from the current confirmed memory snapshot: revocation cannot leave stale inferences.
 * Finite forward chaining, explicit negation, no closed-world assumption, no tool dependency.
 * A conflicting intermediate atom taints every downstream proof (conservative abstention).
 */
class StructuredKnowledge(private val maxChecks: Int = 4096) {
    init { require(maxChecks in 1..4096) }
    private data class Proof(val sources: Set<Int>, val dependencies: Set<KnowledgeAtom>, val steps: List<String>)

    fun query(confirmedFacts: List<String>, query: KnowledgeLiteral): KnowledgeAnswer {
        require(confirmedFacts.size <= 32 && confirmedFacts.all { it.length <= 320 })
        val source = confirmedFacts.toList()
        val parsed = source.mapIndexedNotNull { i, s -> KnowledgeSyntax.statement(s)?.let { i to it } }
        val known = linkedMapOf<KnowledgeLiteral, Proof>()
        parsed.filter { !it.second.universal }.forEach { (i, statement) ->
            known.putIfAbsent(statement.literal, Proof(setOf(i), setOf(statement.literal.atom), emptyList()))
        }
        val rules = parsed.filter { it.second.universal }
        var checks = 0
        var limited = false
        var changed = true
        while (changed && !limited) {
            changed = false
            val memberships = known.entries.filter { !it.key.negative && it.key.atom.relation == "ist" }
            for ((premise, proof) in memberships) {
                for ((id, rule) in rules) {
                    if (checks >= maxChecks || Thread.currentThread().isInterrupted) { limited = true; break }
                    checks++
                    if (premise.atom.value != rule.literal.atom.subject) continue
                    val next = rule.literal.copy(atom = rule.literal.atom.copy(subject = premise.atom.subject))
                    if (next in known) continue
                    if (known.size >= 256 || proof.steps.size >= 16) { limited = true; break }
                    known[next] = Proof(proof.sources + id, proof.dependencies + next.atom,
                        proof.steps + "${next.atom.subject} | ${if (next.negative) "nicht " else ""}${next.atom.relation} | ${next.atom.value}")
                    changed = true
                }
                if (limited) break
            }
        }
        if (limited) return KnowledgeAnswer(KnowledgeStatus.LIMIT, ruleChecks = checks)
        val conflicts = known.keys.filter { it.copy(negative = !it.negative) in known }.map { it.atom }.toSet()
        val positive = known[query]
        val negative = known[query.copy(negative = !query.negative)]
        val selected = listOfNotNull(positive, negative)
        val tainted = selected.any { proof -> proof.dependencies.any { it in conflicts } }
        val status = when {
            query.atom in conflicts -> KnowledgeStatus.CONFLICT
            tainted -> KnowledgeStatus.UNKNOWN
            positive != null -> KnowledgeStatus.SUPPORTED
            negative != null -> KnowledgeStatus.REFUTED
            else -> KnowledgeStatus.UNKNOWN
        }
        // Include both sides of every conflict in a returned proof, not just the preferred side.
        val conflictProofs = selected.flatMap { it.dependencies }.filter { it in conflicts }
            .flatMap { atom -> listOfNotNull(known[KnowledgeLiteral(atom)], known[KnowledgeLiteral(atom, true)]) }
        val ids = (selected + conflictProofs).flatMap { it.sources }.distinct().sorted()
        val evidence = ids.map { i -> KnowledgeEvidence(
            MessageDigest.getInstance("SHA-256").digest(source[i].toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }, source[i]) }
        return KnowledgeAnswer(status, evidence, selected.flatMap { it.steps }.distinct(), checks)
    }
}

/** Atomic, exact-value replacement shared by persistence and regression tests. */
object KnowledgeRevision {
    fun replace(facts: List<String>, previous: String, replacement: String?): List<String>? {
        if (facts.size > 32 || previous !in facts || replacement == previous) return null
        if (replacement != null && (replacement.length !in 3..320 ||
                KnowledgeSyntax.statement(replacement) == null)) return null
        return (facts.filterNot { it == previous } + listOfNotNull(replacement)).distinct().sorted()
    }
}
