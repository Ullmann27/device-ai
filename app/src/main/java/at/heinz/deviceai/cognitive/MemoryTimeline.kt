package at.heinz.deviceai.cognitive

/**
 * M11 · deterministic timeline check over ACTIVE owner facts (no model, no write, no new fact).
 *
 * Two facts about the same named subject that share a sentence frame but differ in their values describe the same
 * attribute at different times or contradict each other:
 *  - a fact with "bis/früher/damals/wohnte..." is SUPERSEDED by one with "seit/jetzt/inzwischen..." (outdated);
 *  - a fact marked "Korrektur:" or "nicht ..., sondern ..." CORRECTS the other one;
 *  - without any marker the pair is a CONTRADICTION: the answer must ask back instead of picking one.
 * Facts about different attributes ("wohnt in" vs "arbeitet in") share no content word and are left alone.
 *
 * The frozen EWM2 brain is not changed; this check reads its cited fact ids afterwards (SHADOW research).
 */
object MemoryTimeline {
    enum class Relation { SUPERSEDES, CORRECTS, CONTRADICTS }
    data class Link(val newer: String, val older: String, val relation: Relation)
    data class Verdict(val decision: String, val factIds: List<String>, val reason: String)

    private val WORD = Regex("\\p{L}[\\p{L}\\p{N}-]*|\\p{N}+")
    private val PAST = setOf("bis", "früher", "ehemals", "zuvor", "damals", "vorher", "einst", "ursprünglich", "vormals")
    private val CURRENT = setOf("seit", "jetzt", "inzwischen", "mittlerweile", "heute", "nun", "aktuell", "neuerdings")
    private val STOP = setOf("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "von", "in", "im",
        "am", "an", "auf", "mit", "und", "ist", "sind", "war", "hat", "zu", "zum", "zur", "bei", "für", "nicht", "sondern",
        "korrektur", "laut", "auch", "noch", "aber") + PAST + CURRENT

    /** Links between facts (id to text); bounded by the caller's active-fact limit. */
    fun links(facts: List<Pair<String, String>>): List<Link> {
        val out = ArrayList<Link>()
        val parsed = facts.take(200).map { (id, text) -> Parsed(id, text) }
        for (i in parsed.indices) for (j in i + 1 until parsed.size) {
            val a = parsed[i]; val b = parsed[j]
            if (a.subject == null || a.subject != b.subject) continue
            val frame = a.stems.intersect(b.stems)
            if (frame.isEmpty()) continue                                // different attribute ("wohnt" vs "arbeitet")
            val differing = (a.valueTokens - b.valueTokens) + (b.valueTokens - a.valueTokens)
            if (differing.isEmpty()) continue                            // same statement repeated or paraphrased
            out += when {
                b.correction && !a.correction -> Link(b.id, a.id, Relation.CORRECTS)
                a.correction && !b.correction -> Link(a.id, b.id, Relation.CORRECTS)
                b.current && a.past -> Link(b.id, a.id, Relation.SUPERSEDES)
                a.current && b.past -> Link(a.id, b.id, Relation.SUPERSEDES)
                else -> Link(a.id, b.id, Relation.CONTRADICTS)
            }
        }
        return out
    }

    /** What a brain answer citing [cited] should become given the timeline of [facts]. */
    fun check(cited: List<String>, facts: List<Pair<String, String>>): Verdict {
        val first = cited.firstOrNull() ?: return Verdict("UNCHANGED", cited, "NO_CITATION")
        val ls = links(facts)
        ls.firstOrNull { it.relation == Relation.CONTRADICTS && (it.newer == first || it.older == first) }
            ?.let { return Verdict("CLARIFY", listOf(it.newer, it.older), "CONTRADICTING_FACTS") }
        var current = first; val seen = HashSet<String>()
        while (seen.add(current)) {
            val newer = ls.firstOrNull { it.older == current && it.relation != Relation.CONTRADICTS } ?: break
            current = newer.newer
        }
        return if (current == first) Verdict("UNCHANGED", cited, "CURRENT") else
            Verdict("REPLACE", listOf(current) + cited.filter { it != first && it != current }, "OUTDATED_CITATION")
    }

    private class Parsed(val id: String, text: String) {
        val tokens = WORD.findAll(text).map { it.value }.toList()
        val lower = tokens.map { it.lowercase() }
        /** A name: capitalised, not preceded by an article or an inflected adjective ("der Geburtstag", "roten Kombi"). */
        private val names = tokens.indices.filter { i ->
            val t = tokens[i]; val prev = lower.getOrNull(i - 1)
            t[0].isUpperCase() && t.length >= 3 && lower[i] !in STOP && lower[i] !in ARTICLES &&
                (prev == null || (prev !in ARTICLES && !ADJ.matches(prev))) &&
                (i > 0 || tokens.getOrNull(1)?.get(0)?.isLowerCase() == true)
        }.map { lower[it] }
        val subject: String? = names.firstOrNull()
        val past = lower.any { it in PAST }
        val current = lower.any { it in CURRENT }
        val correction = lower.firstOrNull() == "korrektur" || ("sondern" in lower && "nicht" in lower)
        /** every content token except the subject, as a 4-letter stem ("wohnte"/"wohnt" -> "wohn") */
        val stems: Set<String> = lower.filter { it !in STOP && it !in ARTICLES && it != subject && it.length >= 3 && !it[0].isDigit() }
            .map { it.take(4) }.toSet()
        /** tokens that can carry a value: numbers, names, inflected adjectives ("roten") */
        val valueTokens: Set<String> = lower.filterIndexed { i, t -> t != subject &&
            (t[0].isDigit() || t in names || ADJ.matches(t) && lower.getOrNull(i + 1)?.let { n -> tokens[i + 1][0].isUpperCase() } == true) }.toSet()
    }

    private val ARTICLES = setOf("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines")
    private val ADJ = Regex("\\p{L}{3,}(e|en|er|es|em)")
}
