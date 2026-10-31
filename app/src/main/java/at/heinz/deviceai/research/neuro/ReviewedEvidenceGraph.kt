package at.heinz.deviceai.research.neuro

import java.security.MessageDigest
import java.util.Locale

/**
 * Experimental, read-only proof search. Only caller-reviewed, hash-verified relations
 * may enter the graph. Neither source excerpts nor derived paths become owner facts.
 * The graph does not grant answer authority or execute tools.
 */
class ReviewedEvidenceGraph(relations: List<Relation>) {
    enum class Level { OBJECT, CITY, REGION, COUNTRY }
    enum class Status { SUPPORTED_REVIEWED, CONFLICTING_PATHS, NOT_SUPPORTED }

    data class Evidence(
        val sourceId: String,
        val text: String,
        val sha256: String,
        val start: Int,
        val end: Int,
        val reviewed: Boolean
    ) {
        init {
            require(sourceId.matches(Regex("[A-Za-z0-9._:-]{1,96}")))
            require(text.length in 8..8_192 && !text.contains('\u0000'))
            require(sha256.matches(Regex("[a-f0-9]{64}")))
            require(start in 0 until end && end <= text.length && end - start <= 512)
            require(digest(text) == sha256) { "EVIDENCE_HASH_MISMATCH" }
            require(reviewed) { "UNREVIEWED_RELATION" }
        }
        val quotation: String get() = text.substring(start, end)
    }

    data class Relation(
        val subject: String,
        val subjectLevel: Level,
        val container: String,
        val containerLevel: Level,
        val evidence: Evidence
    ) {
        init {
            require(subject.isNotBlank() && subject.length <= 80 &&
                container.isNotBlank() && container.length <= 80)
            require(subject.none(Char::isISOControl) && container.none(Char::isISOControl))
            require(subjectLevel.ordinal < containerLevel.ordinal) { "INVALID_RELATION_LEVEL" }
            val quote = evidence.quotation.lowercase(Locale.ROOT)
            require(subject.lowercase(Locale.ROOT) in quote &&
                container.lowercase(Locale.ROOT) in quote) { "EVIDENCE_DOES_NOT_MENTION_BOTH_ENTITIES" }
        }
    }

    data class Proof(
        val status: Status,
        val subject: String,
        val requestedLevel: Level,
        val resolvedValue: String?,
        val supportingSourceIds: List<String>,
        val alternativeValues: List<String>,
        val hops: Int
    ) {
        /** This is a research finding, not permission to replace any chat response. */
        val answerAuthority: Boolean = false
        val mutatesMemory: Boolean = false
    }

    private val edges: List<Relation>

    init {
        require(relations.size <= 128) { "GRAPH_CAPACITY_EXCEEDED" }
        val key = relations.map {
            listOf(it.subjectLevel.name, it.subject.lowercase(Locale.ROOT),
                it.containerLevel.name, it.container.lowercase(Locale.ROOT),
                it.evidence.sourceId, it.evidence.sha256).joinToString("\u001f")
        }
        require(key.distinct().size == key.size) { "DUPLICATED_RELATION" }
        edges = relations.toList()
    }

    /**
     * Traverse strictly increasing containment levels with a finite depth limit.
     * The visited set belongs to each path; shared ancestors in a DAG are valid.
     * Different target values at the requested level remain ambiguous.
     */
    fun infer(subject: String, subjectLevel: Level, requestedLevel: Level): Proof {
        require(subject.isNotBlank() && subject.length <= 80)
        require(requestedLevel.ordinal > subjectLevel.ordinal)
        data class Path(val entity: String, val level: Level, val links: List<Relation>,
                        val visited: Set<String>)
        fun identity(entity: String, level: Level) =
            level.name + ":" + entity.lowercase(Locale.ROOT)

        val matches = ArrayList<Path>()
        val initial = Path(subject, subjectLevel, emptyList(), setOf(identity(subject, subjectLevel)))
        val pending = ArrayDeque<Path>()
        pending.add(initial)
        var expansions = 0
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (current.level == requestedLevel) {
                matches += current
                continue
            }
            if (current.links.size >= 3) continue
            for (edge in edges) {
                if (edge.subjectLevel != current.level ||
                    !edge.subject.equals(current.entity, ignoreCase = true) ||
                    edge.containerLevel.ordinal > requestedLevel.ordinal) continue
                expansions++
                require(expansions <= 512) { "GRAPH_SEARCH_BUDGET_EXCEEDED" }
                val key = identity(edge.container, edge.containerLevel)
                if (key in current.visited) continue
                pending.add(Path(edge.container, edge.containerLevel,
                    current.links + edge, current.visited + key))
            }
        }
        val grouped = matches.groupBy { it.entity.lowercase(Locale.ROOT) }
        if (grouped.isEmpty())
            return Proof(Status.NOT_SUPPORTED, subject, requestedLevel, null,
                emptyList(), emptyList(), 0)
        if (grouped.size > 1)
            return Proof(Status.CONFLICTING_PATHS, subject, requestedLevel, null,
                emptyList(), matches.map { it.entity }.distinct().sorted(), 0)
        val best = grouped.values.single().sortedWith(
            compareBy<Path> { it.links.size }.thenBy {
                it.links.joinToString("|") { link -> link.evidence.sourceId }
            }).first()
        return Proof(Status.SUPPORTED_REVIEWED, subject, requestedLevel, best.entity,
            best.links.map { it.evidence.sourceId }.distinct(), emptyList(), best.links.size)
    }

    companion object {
        fun digest(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
