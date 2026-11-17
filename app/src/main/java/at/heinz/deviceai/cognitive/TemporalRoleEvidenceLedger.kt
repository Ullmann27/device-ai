package at.heinz.deviceai.cognitive

import java.security.MessageDigest
import java.util.Locale

/**
 * Immutable bitemporal research view, not an owner-memory database.
 * Multiple incompatible active source statements are CONFLICT, never silently overwritten.
 * Observation time and validity time are independent, preserving historical corrections.
 */
internal object TemporalRoleEvidenceLedger {
    data class Fact(val subject: String, val relation: String, val value: String,
        val validFromMillis: Long, val observedAtMillis: Long,
        val sourceId: String, val sourceRevision: Int, val sourceText: String,
        val sourceSha256: String, val validUntilMillis: Long? = null,
        val supersedesSourceIds: Set<String> = emptySet()) {
        init {
            require(subject.length in 1..80 && relation.length in 1..80 && value.length in 1..160)
            require(subject.none(Char::isISOControl) && value.none(Char::isISOControl))
            require(validFromMillis >= 0 && observedAtMillis >= 0)
            require(validUntilMillis == null || validUntilMillis > validFromMillis)
            require(sourceId.matches(Regex("[a-zA-Z0-9._:-]{1,96}")) && sourceRevision > 0)
            require(sourceText.length in 1..4096)
            require(sourceSha256.matches(Regex("[a-f0-9]{64}")))
            require(sourceSha256 == sha256(sourceText))
            require(supersedesSourceIds.size <= 16 &&
                supersedesSourceIds.none { it == sourceId ||
                    !it.matches(Regex("[a-zA-Z0-9._:-]{1,96}")) })
        }
    }

    sealed class Lookup {
        data class Found(val value: String, val sources: List<String>, val validFromMillis: Long): Lookup()
        data class Conflict(val sourceIds: List<String>): Lookup()
        object NoEvidence: Lookup()
    }

    fun lookup(facts: Collection<Fact>, subject: String, relation: String,
               validAtMillis: Long, knownAtMillis: Long): Lookup {
        require(facts.size <= 256 && validAtMillis >= 0 && knownAtMillis >= 0)
        val relevant = facts.asSequence()
            .filter { it.subject.trim().lowercase(Locale.ROOT) == subject.trim().lowercase(Locale.ROOT) &&
                it.relation.trim().lowercase(Locale.ROOT) == relation.trim().lowercase(Locale.ROOT) &&
                it.validFromMillis <= validAtMillis && it.observedAtMillis <= knownAtMillis &&
                (it.validUntilMillis == null || validAtMillis < it.validUntilMillis) }
            .toList()
        if (relevant.isEmpty()) return Lookup.NoEvidence
        // A later timestamp is NOT authority to erase a conflicting source.
        // New location values supersede older active values only when the new
        // evidence explicitly identifies the older source being replaced.
        val latest = relevant.maxOf { it.validFromMillis }
        val candidates = relevant.filter { it.validFromMillis == latest }
        val allValues = candidates.map { it.value.lowercase(Locale.ROOT) }.distinct()
        val oldConflicts = relevant.filter { fact ->
            fact.validFromMillis < latest &&
                fact.value.lowercase(Locale.ROOT) !in allValues &&
                candidates.none { fact.sourceId in it.supersedesSourceIds }
        }
        if (allValues.size != 1 || oldConflicts.isNotEmpty())
            return Lookup.Conflict((candidates + oldConflicts).map { it.sourceId }.distinct().sorted())
        return Lookup.Found(candidates.first().value, candidates.map { it.sourceId }.distinct().sorted(), latest)
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
