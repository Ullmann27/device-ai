// Product copy of the EWM2 research class (PR #224, code freeze e1669271): same logic; only the package,
// the removed EWM1_TRACE debug output and the removed research-only N0 baseline differ.
package at.heinz.deviceai.intelligence.ewm

import at.heinz.deviceai.intelligence.ewm.Ewm1Lexicon.lower
import at.heinz.deviceai.intelligence.ewm.Ewm1Lexicon.stem

/**
 * Deterministic claim check of a model answer against the evidence it was given (no model call).
 * Checks only what can be checked: names/nouns, numbers, time words, colours, negation and simple antonyms.
 * A pass does not confirm the answer; a flag means "this detail is not carried by the evidence".
 */
object Ewm1ClaimCheck {
    data class Result(val flagged: Boolean, val unsupported: List<String>)

    private val PRONOUN_LIKE = setOf("er", "sie", "es", "du", "dein", "deine", "deinem", "deinen", "deiner", "ihr", "ihre",
        "ja", "nein", "das", "die", "der", "dort", "darin", "inzwischen", "ab", "seit", "im", "in", "am", "auf", "bei",
        "mit", "ich", "wir", "ihm", "ihn", "jetzt", "heute", "dann", "also")
    private val TIME_SYNONYMS = listOf(setOf("früh", "morgen", "morgens", "vormittag"), setOf("abend", "abends"),
        setOf("nacht", "nachts"), setOf("jänner", "januar"))

    fun check(answer: String, evidence: List<String>): Result {
        val a = Ewm1Tokens.of(answer)
        val ev = evidence.flatMap { Ewm1Tokens.of(it) }
        val evWords = ev.map { it.lower }.toSet()
        val evNumbers = ev.mapNotNull { numberOf(it) }.toSet()
        val unsupported = ArrayList<String>()

        for (t in a) {
            val w = t.lower
            val n = numberOf(t)
            if (n != null) { if (n !in evNumbers) unsupported += t.text; continue }
            if (w in Ewm1Lexicon.COLORS || w.split('-').let { p -> p.size > 1 && p.all { it in Ewm1Lexicon.COLORS } }) {
                if (w !in evWords) unsupported += t.text; continue
            }
            if (w in Ewm1Lexicon.TIME && w !in setOf("seit", "jeden", "jede", "heute")) {
                if (w !in evWords && TIME_SYNONYMS.none { w in it && it.any { s -> s in evWords } }) unsupported += t.text
                continue
            }
            if (!t.capitalized || w in PRONOUN_LIKE || w in Ewm1Lexicon.GENERIC) continue
            if (t.sentenceInitial && w in Ewm1Lexicon.FUNCTION) continue
            if (w in Ewm1Lexicon.FUNCTION) continue
            if (!ev.any { e -> sameWord(w, e.lower) }) unsupported += t.text
        }
        // Polarity: an affirmed detail the evidence negates, or a negation the evidence does not state.
        val evNegated = ev.any { it.lower in Ewm1Lexicon.NEGATIONS }
        val ansNegated = a.any { it.lower in Ewm1Lexicon.NEGATIONS }
        if (evNegated && !ansNegated) unsupported += "Verneinung fehlt"
        if (!evNegated && ansNegated && a.none { it.lower == "mehr" }) unsupported += "unbelegte Verneinung"
        if (a.firstOrNull()?.lower == "ja" && evNegated) unsupported += "Ja trotz Verneinung"
        for ((x, y) in Ewm1Lexicon.ANTONYMS) for ((p, q) in listOf(x to y, y to x))
            if (a.any { it.lower == p } && p !in evWords && q in evWords) unsupported += p
        return Result(unsupported.isNotEmpty(), unsupported.distinct())
    }

    private fun numberOf(t: Ewm1Token): String? {
        if (t.lower in setOf("ein", "eine", "einen", "einem", "einer")) return null
        return Ewm1Lexicon.numberValue(t.lower)
    }

    private fun sameWord(a: String, b: String): Boolean =
        a == b || (a.length >= 4 && b.length >= 4 && stem(a) == stem(b)) ||
            (a.length >= 4 && (b.endsWith(a) || b.startsWith(a))) || (b.length >= 4 && (a.endsWith(b) || a.startsWith(b))) ||
            b.split('-').any { it == a } || a.split('-').any { it == b }
}
