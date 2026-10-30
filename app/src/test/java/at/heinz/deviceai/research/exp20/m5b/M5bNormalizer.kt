package at.heinz.deviceai.research.exp20.m5b

/**
 * EXP-20 M5b arm B: small, auditable deterministic normalisation, FROZEN before any VAL wording was written.
 * Day rules cover exactly the two TRAIN construction types ("am N-ten Arbeitstag der Woche", "einen Tag vor dem X").
 * The place glossary is derived mechanically from the TRAIN family texts: a content word maps to a place only if it
 * occurs in that place's TRAIN paraphrases and in no other place's. Unknown wording stays unchanged (no guessing).
 * The recognised span is REPLACED by the canonical slot phrase, so a misleading weekday inside "vor dem Dienstag"
 * cannot keep matching Tuesday memories.
 */
object M5bNormalizer {
    private val weekdays = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    private val ordinals = listOf("ersten", "zweiten", "dritten", "vierten", "fünften")
    private val ordinal = Regex("""am (${ordinals.joinToString("|")}) Arbeitstag der Woche""")
    private val dayBefore = Regex("""einen Tag vor dem (${weekdays.joinToString("|")})""")
    private val stop = setOf("wo", "man", "die", "der", "das", "den", "dem", "des", "mit", "bei", "zwischen", "zu", "wird", "werden",
        "sind", "kann", "an", "voller", "von", "und", "im", "in", "am")

    private fun words(t: String) = Regex("[\\p{L}]+").findAll(t.lowercase()).map { it.value }.filter { it !in stop && it.length > 2 }.toSet()

    /** keyword -> place index, unique to one place across the TRAIN families. */
    val glossary: Map<String, Int> = run {
        val perPlace = M5bData.train.places.map { fam -> fam.flatMap { words(it) }.toSet() }
        val m = HashMap<String, Int>()
        perPlace.forEachIndexed { i, ws -> for (w in ws) if (perPlace.withIndex().none { (j, o) -> j != i && w in o }) m[w] = i }
        m
    }

    fun normalize(query: String): String {
        var q = query
        ordinal.find(q)?.let { q = q.replace(it.value, M5bData.days[ordinals.indexOf(it.groupValues[1])]) }
        dayBefore.find(q)?.let { m -> val i = weekdays.indexOf(m.groupValues[1]) - 1; if (i in M5bData.days.indices) q = q.replace(m.value, M5bData.days[i]) }
        val hits = words(q).mapNotNull { glossary[it] }.toSet()
        if (hits.size == 1) q = q.trimEnd('?') + " " + M5bData.places[hits.single()] + "?"
        return q
    }
}
