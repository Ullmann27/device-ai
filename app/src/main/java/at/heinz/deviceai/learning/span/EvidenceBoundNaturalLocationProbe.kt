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
        """(?iu)^\s*wo\s+(?:ist|wohnt|lebt|steht)\s+([\p{L}][\p{L}\p{M}-]{1,31})\s*\?\s*$"""
    )
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
        val name = questionPattern.matchEntire(question)?.groups?.get(1)?.value
            ?: return Decision.Abstained("UNSUPPORTED_QUESTION")
        val ids = sources.map { it.sourceId }
        if (ids.distinct().size != ids.size) return Decision.Abstained("DUPLICATE_SOURCE")
        val quotedName = Regex.escape(name)
        val mention = Regex("""(?iu)(?<![\p{L}\p{M}])""" + quotedName + """(?![\p{L}\p{M}])""")
        val pattern = Regex(
            """(?iu)(?<![\p{L}\p{M}])""" + quotedName +
            """\s+(?:ist|wohnt|lebt|steht)\s+in\s+(""" + cityPattern +
            """)(?=\s*(?:[.!?;]|$))"""
        )
        val matches = mutableListOf<Decision.Found>()
        for (source in sources) {
            if (source.revision < 1 || !validId.matches(source.sourceId) ||
                !validSha.matches(source.sha256) || source.text.isBlank() ||
                source.text.any(Char::isISOControl) ||
                digest(source.text) != source.sha256)
                return Decision.Abstained("UNTRUSTED_EVIDENCE")
            // Time-scoped evidence requires a separate, explicit temporal resolver.
            for (m in mention.findAll(source.text)) {
                val sentenceStart = source.text.substring(0, m.range.first)
                    .indexOfLast { it == '.' || it == '!' || it == '?' || it == ';' } + 1
                val sentenceEnd = source.text.indexOfAny(
                    charArrayOf('.', '!', '?', ';'), m.range.first
                ).let { if (it == -1) source.text.length else it }
                if (timeWords.containsMatchIn(source.text.substring(sentenceStart, sentenceEnd)))
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
    fun verify(
        question: String,
        sources: List<EvidenceBoundNaturalLocationProbe.Evidence>,
        found: EvidenceBoundNaturalLocationProbe.Decision.Found
    ): Boolean {
        val source = sources.singleOrNull { it.sourceId == found.sourceId } ?: return false
        if (source.revision != found.revision || source.sha256 != found.sha256 ||
            EvidenceBoundNaturalLocationProbe.digest(source.text) != found.sha256 ||
            found.start < 0 || found.start >= found.endExclusive ||
            found.endExclusive > source.text.length ||
            source.text.substring(found.start, found.endExclusive) != found.text)
            return false
        val subject = Regex(
            """(?iu)^\s*wo\s+(?:ist|wohnt|lebt|steht)\s+([\p{L}][\p{L}\p{M}-]{1,31})\s*\?\s*$"""
        ).matchEntire(question)?.groups?.get(1)?.value ?: return false
        val prefix = source.text.substring(0, found.start)
        val start = prefix.indexOfLast { it == '.' || it == '!' || it == '?' || it == ';' } + 1
        val localPrefix = prefix.substring(start)
        return Regex(
            """(?iu)(?:^|\s)""" + Regex.escape(subject) +
                """\s+(?:ist|wohnt|lebt|steht)\s+in\s+$"""
        ).containsMatchIn(localPrefix) && found.text.firstOrNull()?.isUpperCase() == true
    }
}
