package at.heinz.deviceai.cognitive

import at.heinz.deviceai.intelligence.ewm.Ewm1ClaimCheck
import at.heinz.deviceai.intelligence.ewm.Ewm1Lexicon

/**
 * SHADOW audit of a finished local-model answer against the retrieval it was given in the same turn.
 *
 * It connects the existing deterministic claim check (EWM2 H4) to the generative Gemma path, which never ran it:
 * names/nouns, numbers, time words and colours of the answer must occur in the evidence, and the polarity of each
 * answer sentence must match the evidence sentence it overlaps most. (Ewm1ClaimCheck compares polarity across the
 * whole evidence, which was right for its one-fact answers but flags every answer once a multi-sentence retrieval
 * contains any "nicht"; the frozen check itself is not changed.)
 *
 * M12 adds four deterministic checks, developed on the DEV cases only (holdout sealed beforehand):
 *  - attribute binding per clause: a name the answer ties to a subject must occur in an evidence clause about it;
 *  - outdated statements: the answer restates the "bis/früher" part of evidence that also has a "seit/inzwischen" part;
 *  - new actions: a participle ("gestohlen", "verheiratet") whose core occurs nowhere in the evidence;
 *  - self-contradicting evidence: two evidence clauses about the same thing with different numbers or time words
 *    (stage CLAIM_AUDIT_CONFLICT, whatever the answer says).
 *
 * The result is ONE trace stage in the existing metadata-only turn trace, nothing else: the visible answer, the
 * prompt, model calls, tools and memories are unchanged, and no token of the answer or the evidence is stored.
 *
 * Limits, stated so nobody reads more into it: this is token, clause and polarity matching, not entailment. A flag
 * means "a detail is not carried by the retrieval" (the model may have added a legitimate conclusion); a pass does
 * not prove the answer correct. It becomes a quality signal only together with owner feedback on the same trace.
 */
object LocalModelClaimAudit {
    /** Answers longer than the model's bounded reply are not audited (the stage then says so). */
    const val MAX_ANSWER_CHARS = 8_000
    private val POLARITY = setOf("Verneinung fehlt", "unbelegte Verneinung", "Ja trotz Verneinung")
    // "am 6. Oktober": a dot right after a digit is an ordinal, not a sentence or clause end (holdout-1 false alarms)
    private val SENTENCE_END = Regex("(?<=[!?]|(?<!\\d)\\.)\\s+")
    private val CLAUSE_BREAK = Regex("(?<=[!?;,]|(?<!\\d)\\.)\\s+|\\s+(?=(?:und|während|aber|sowie|sondern|doch|inzwischen|seit)\\s)")
    private val CONTENT_WORD = Regex("\\p{L}{4,}")
    private val WORD = Regex("\\p{L}[\\p{L}\\p{N}-]*|\\p{N}+")
    private val PAST = setOf("bis", "früher", "ehemals", "zuvor", "damals", "vorher", "einst", "ursprünglich", "vormals")
    private val CURRENT = setOf("seit", "inzwischen", "mittlerweile", "jetzt", "heute", "nun", "aktuell", "neuerdings")
    private val SEPARABLE = Regex("^(?:ab|an|auf|aus|ein|mit|nach|vor|zu|zurück|weg|her|hin|los|fest)?ge(\\p{L}{3,}?)(?:et|t|en)$")
    private val PRONOUNS = setOf("er", "sie", "es", "ihn", "ihm", "ihr")
    private val INSEPARABLE = Regex("^(?:ver|be|er|ent|zer|miss)(\\p{L}{3,}?)(?:et|t|en)$")

    fun stage(answer: String, recall: LocalGroundedRecall): TraceStage {
        if (answer.isBlank() || answer.length > MAX_ANSWER_CHARS) return TraceStage.CLAIM_AUDIT_SKIPPED
        return runCatching {
            val evidenceClauses = clauses(recall.answer)
            when {
                selfContradicting(evidenceClauses) -> TraceStage.CLAIM_AUDIT_CONFLICT
                unsupportedDetails(answer, listOf(recall.answer, recall.provenance)) ||
                    polarityMismatch(answer, sentences(recall.answer)) ||
                    unboundAttribute(answer, evidenceClauses) ||
                    outdated(answer, evidenceClauses) ||
                    newAction(answer, recall.answer) -> TraceStage.CLAIM_AUDIT_UNSUPPORTED
                else -> TraceStage.CLAIM_AUDIT_SUPPORTED
            }
        }.getOrDefault(TraceStage.CLAIM_AUDIT_SKIPPED)
    }

    private fun unsupportedDetails(answer: String, evidence: List<String>): Boolean =
        Ewm1ClaimCheck.check(answer, evidence).unsupported.any { it !in POLARITY }

    private fun polarityMismatch(answer: String, evidence: List<String>): Boolean = sentences(answer).any { sentence ->
        val words = contentWords(sentence)
        val best = evidence.maxByOrNull { e -> contentWords(e).count { it in words } }
        best != null && contentWords(best).any { it in words } &&
            Ewm1ClaimCheck.check(sentence, listOf(best)).unsupported.any { it in POLARITY }
    }

    /** "Markus arbeitet in Graz" while the only evidence clause about Markus names Linz. */
    private fun unboundAttribute(answer: String, evidence: List<String>): Boolean {
        // "..., seit 2024 wohnt sie in Graz": a clause led by a pronoun continues the previous clause's subject
        val bound = evidence.indices.map { i ->
            if (i > 0 && words(evidence[i]).any { it in PRONOUNS }) names(evidence[i]) + names(evidence[i - 1]) else names(evidence[i])
        }
        return clauses(answer).any { clause ->
            val names = names(clause)
            if (names.size < 2) return@any false
            val subject = names.firstOrNull { n -> bound.any { n in it } } ?: return@any false
            val about = bound.filter { subject in it }
            names.filter { it != subject && bound.any { e -> it in e } }.any { other -> about.none { other in it } }
        }
    }

    /** The answer restates a clause marked as past while the same evidence also holds a current clause. */
    private fun outdated(answer: String, evidence: List<String>): Boolean {
        if (evidence.none { c -> words(c).any { it in CURRENT } }) return false
        return clauses(answer).any { clause ->
            val names = names(clause)
            if (names.isEmpty() || words(clause).any { it in PAST || it in CURRENT }) return@any false
            val best = evidence.maxByOrNull { e -> names(e).count { it in names } } ?: return@any false
            names(best).count { it in names } >= minOf(2, names.size) && words(best).any { it in PAST }
        }
    }

    /** A participle naming an action or state whose core never occurs in the evidence. */
    private fun newAction(answer: String, evidence: String): Boolean {
        val evidenceWords = words(evidence)
        return words(answer).any { w ->
            val core = (SEPARABLE.matchEntire(w) ?: INSEPARABLE.matchEntire(w))?.groupValues?.get(1) ?: return@any false
            w.length >= 6 && core.length >= 3 && evidenceWords.none { it.contains(core) }
        }
    }

    /** Two clauses about the same thing (shared name or noun) with different numbers or time words, no past/current marker. */
    private fun selfContradicting(evidence: List<String>): Boolean {
        val plain = evidence.filter { c -> words(c).none { it in PAST || it in CURRENT } }
        for (i in plain.indices) for (j in i + 1 until plain.size) {
            val a = plain[i]; val b = plain[j]
            if (names(a).none { it in names(b) }) continue
            val numsA = numbers(a); val numsB = numbers(b)
            if (numsA.isNotEmpty() && numsB.isNotEmpty() && numsA.none { it in numsB }) return true
            val timeA = words(a).filter { it in Ewm1Lexicon.TIME && it !in setOf("uhr", "jeden", "jede") }
            val timeB = words(b).filter { it in Ewm1Lexicon.TIME && it !in setOf("uhr", "jeden", "jede") }
            if (timeA.isNotEmpty() && timeB.isNotEmpty() && timeA.none { it in timeB }) return true
        }
        return false
    }

    private fun names(text: String): Set<String> = WORD.findAll(text).map { it.value }
        .filter { it[0].isUpperCase() && it.length >= 3 }.map { it.lowercase() }
        .filter { it !in Ewm1Lexicon.FUNCTION && it !in Ewm1Lexicon.GENERIC && it !in Ewm1Lexicon.TIME && it !in PAST && it !in CURRENT && it != "laut" }
        .toSet()
    private fun numbers(text: String): Set<String> = WORD.findAll(text).mapNotNull { Ewm1Lexicon.numberValue(it.value) }.toSet()
    private fun words(text: String): List<String> = WORD.findAll(text).map { it.value.lowercase() }.toList()
    private fun clauses(text: String): List<String> = text.split(CLAUSE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }
    private fun sentences(text: String): List<String> = text.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }
    private fun contentWords(text: String): Set<String> = CONTENT_WORD.findAll(text.lowercase()).map { it.value }.toSet()
}
