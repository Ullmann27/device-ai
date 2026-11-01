package at.heinz.deviceai.learning.span

import java.security.MessageDigest

/**
 * Research-only, conservative baseline for a named person's location.
 * Does not train, persist, call a model, authorize tools or alter owner state.
 * This is NOT a learned neural decoder or general language understanding.
 */
object EvidenceBoundNaturalLocationProbe {
    data class Evidence(val sourceId: String, val revision: Int, val sha256: String, val text: String)
    sealed class Decision {
        data class Found(
            val text: String,
            val sourceId: String,
            val revision: Int,
            val sha256: String,
            val start: Int,
            val endExclusive: Int
        ) : Decision()
        data class Abstained(val reason: String) : Decision()
    }

    private val questionPattern = Regex(
        """(?iu)^\s*wo\s+(ist|wohnt|lebt|steht)\s+([\p{L}][\p{L}\p{M}-]{1,31})\s*\?\s*$"""
    )
    private val contradictoryWords = Regex("""(?iu)\b(?:nicht|kein|keine|sondern|stattdessen)\b""")
    private val timeWords = Regex(
        """(?iu)\b(?:gestern|heute|morgen|früher|damals|aktuell|seit|vorher|später)\b"""
    )
    private val validId = Regex("[a-zA-Z0-9._:-]{1,96}")
    private val validSha = Regex("[0-9a-f]{64}")
    private const val cityPattern =
        """[\p{Lu}][\p{L}\p{M}-]{1,40}(?:\s+[\p{Lu}][\p{L}\p{M}-]{1,40}){0,2}"""

    fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun answer(question: String, sources: List<Evidence>): Decision {
        if (question.length > 160 || question.any(Char::isISOControl) ||
            sources.size !in 1..4 || sources.sumOf { it.text.length } > 4096)
            return Decision.Abstained("INVALID_REQUEST")
        val questionMatch = questionPattern.matchEntire(question)
            ?: return Decision.Abstained("UNSUPPORTED_QUESTION")
        val name = questionMatch.groupValues[2]
        val relation = questionMatch.groupValues[1].lowercase(java.util.Locale.ROOT)
        val evidenceRelations = Regex.escape(relation)
        val ids = sources.map { it.sourceId }
        if (ids.distinct().size != ids.size) return Decision.Abstained("DUPLICATE_SOURCE")
        val quotedName = Regex.escape(name)
        val mention = Regex("""(?iu)(?<![\p{L}\p{M}])""" + quotedName + """(?![\p{L}\p{M}])""")
        // Clause boundaries: sentence punctuation, comma and the coordinating "und", so that
        // "Anna ist in Perg, Emma ist in Berg." binds each person to her own clause. Negation and
        // time words are still checked on the whole sentence below (fail closed).
        val pattern = Regex(
            """(?ium)(?:^|(?<=[.!?;,])|(?<=\bund\s))\s*""" + quotedName +
            """\s+""" + evidenceRelations + """\s+in\s+(""" + cityPattern +
            """)(?=\s*(?:[.!?;,]|\s+und\s+[\p{L}][\p{L}\p{M}-]{1,31}\s+(?:ist|wohnt|lebt|steht)\s+in\s|$))"""
        )
        val matches = mutableListOf<Decision.Found>()
        for (source in sources) {
            if (source.revision < 1 || !validId.matches(source.sourceId) ||
                !validSha.matches(source.sha256) || source.text.isBlank() ||
                source.text.any(Char::isISOControl) || !Charsets.UTF_8.newEncoder().canEncode(source.text) ||
                digest(source.text) != source.sha256)
                return Decision.Abstained("UNTRUSTED_EVIDENCE")
            // Time-scoped evidence requires a separate, explicit temporal resolver.
            for (m in mention.findAll(source.text)) {
                val sentenceStart = source.text.substring(0, m.range.first)
                    .indexOfLast { it == '.' || it == '!' || it == '?' || it == ';' } + 1
                val sentenceEnd = source.text.indexOfAny(
                    charArrayOf('.', '!', '?', ';'), m.range.first
                ).let { if (it == -1) source.text.length else it }
                val sentence = source.text.substring(sentenceStart, sentenceEnd)
                if (contradictoryWords.containsMatchIn(sentence))
                    return Decision.Abstained("NEGATED_OR_CONTRASTIVE_EVIDENCE")
                if (timeWords.containsMatchIn(sentence))
                    return Decision.Abstained("AMBIGUOUS_TIME_SCOPE")
            }
            for (m in pattern.findAll(source.text)) {
                val group = m.groups[1] ?: continue
                matches += Decision.Found(
                    group.value, source.sourceId, source.revision, source.sha256,
                    group.range.first, group.range.last + 1
                )
            }
        }
        if (matches.size != 1) return Decision.Abstained(
            if (matches.isEmpty()) "NO_SUPPORTED_SPAN" else "AMBIGUOUS_OR_CONTRADICTORY"
        )
        val found = matches.single()
        return if (EvidenceBoundNaturalLocationVerifier.verify(question, sources, found))
            found else Decision.Abstained("VERIFICATION_REJECTED")
    }
}

/**
 * Independent, narrow structural verifier: exact source bytes, source identity,
 * entity/relation prefix and offsets. Not a general semantic truth oracle.
 */
object EvidenceBoundNaturalLocationVerifier {
    /** Re-parse the complete bounded snapshot. Never trust the producer's selection. */
    fun verify(
        question: String,
        sources: List<EvidenceBoundNaturalLocationProbe.Evidence>,
        found: EvidenceBoundNaturalLocationProbe.Decision.Found
    ): Boolean {
        if (question.length > 160 || question.any(Char::isISOControl) ||
            sources.size !in 1..4 || sources.sumOf { it.text.length.toLong() } > 4096 ||
            sources.map { it.sourceId }.distinct().size != sources.size) return false
        val query = Regex(
            """(?iu)^\s*wo\s+(ist|wohnt|lebt|steht)\s+([\p{L}][\p{L}\p{M}-]{1,31})\s*\?\s*$"""
        ).matchEntire(question) ?: return false
        val subject = query.groupValues[2]
        val relation = query.groupValues[1].lowercase(java.util.Locale.ROOT)
        val mention = Regex("""(?iu)(?<![\p{L}\p{M}\p{N}-])""" + Regex.escape(subject) +
            """(?![\p{L}\p{M}\p{N}-])""")
        // Deliberately independently expressed full-clause grammar. Embedded speech,
        // negation, temporal modifiers and unknown clauses containing this person fail closed.
        val assertion = Regex("""(?iu)^\s*""" + Regex.escape(subject) +
            """\s+(ist|wohnt|lebt|steht)\s+in\s+([\p{Lu}][\p{L}\p{M}-]{1,40}(?:\s+[\p{Lu}][\p{L}\p{M}-]{1,40}){0,2})\s*$""")
        var supported = 0
        var exact = false
        for (source in sources) {
            if (source.revision < 1 || !source.sourceId.matches(Regex("[a-zA-Z0-9._:-]{1,96}")) ||
                !source.sha256.matches(Regex("[0-9a-f]{64}")) || source.text.isBlank() ||
                source.text.any(Char::isISOControl) || !Charsets.UTF_8.newEncoder().canEncode(source.text) ||
                EvidenceBoundNaturalLocationProbe.digest(source.text) != source.sha256) return false
            // This narrow static-assertion verifier cannot resolve cross-clause
            // pronouns, retractions or quotations. Refuse the whole source rather
            // than silently treating a preceding positive clause as still valid.
            if (source.text.any { it in "\"„“”«»" } || Regex(
                    """(?iu)\b(?:sie|er|es|das|dies|diese|dieser|dieses|jene|jener|korrektur|widerruf|widerrufen|falsch|nein|ungültig)\b"""
                ).containsMatchIn(source.text)) return false
            // Independently expressed clause split: sentence punctuation, comma, or a
            // coordinating " und " – each clause must be a complete static assertion.
            // " und " only separates clauses when a complete new assertion follows;
            // "in Graz und Wien" stays one (rejected) location coordination.
            val coordination = Regex("""(?iu)\s+und\s+(?=[\p{L}][\p{L}\p{M}-]{1,31}\s+(?:ist|wohnt|lebt|steht)\s+in\s)""")
            var start = 0
            for (end in 0..source.text.length) {
                if (end < source.text.length && source.text[end] !in ".!?;,") continue
                val clause = source.text.substring(start, end)
                var clauseStart = start
                val parts = ArrayList<Pair<Int, String>>()
                var cursor = 0
                for (c in coordination.findAll(clause)) {
                    parts += clauseStart + cursor to clause.substring(cursor, c.range.first)
                    cursor = c.range.last + 1
                }
                parts += clauseStart + cursor to clause.substring(cursor)
                for ((partStart, part) in parts) {
                if (mention.containsMatchIn(part)) {
                    if (end < source.text.length && source.text[end] == '?') return false
                    val match = assertion.matchEntire(part) ?: return false
                    if (!match.groupValues[1].equals(relation, ignoreCase = true)) return false
                    val span = match.groups[2] ?: return false
                    val locationWords = span.value.split(Regex("\\s+"))
                    if (locationWords.any { it.firstOrNull()?.isUpperCase() != true ||
                            it.lowercase(java.util.Locale.ROOT) in setOf("und", "oder", "aber") }) return false
                    supported++
                    exact = exact || (source.sourceId == found.sourceId && source.revision == found.revision &&
                        source.sha256 == found.sha256 && span.value == found.text &&
                        partStart + span.range.first == found.start && partStart + span.range.last + 1 == found.endExclusive)
                }
                }
                start = end + 1
            }
        }
        return supported == 1 && exact
    }
}
