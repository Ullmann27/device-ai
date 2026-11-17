package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Conservative lexical marker for explicit negation in retrieved source claims.
 * It is only a potential contradiction signal, never a fact-verification or
 * independent-corroboration authority. Semantic scope remains unverified.
 */
internal object ResearchNegationSignals {
    private val explicitNegations = setOf(
        "not", "no", "never", "false", "cannot", "isn't", "doesn't",
        "can't", "won't", "don't",
        "nicht", "kein", "keine", "keinen", "keinem", "keiner", "keines",
        "nie", "niemals"
    )

    private fun hasExplicitNegation(value: String): Boolean {
        val words = value.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}']+"))
            .filter(String::isNotBlank)
        return words.indices.any { index ->
            val word = words[index]
            word in explicitNegations &&
                !(word == "nicht" && words.getOrNull(index + 1) == "nur")
        }
    }

    fun hasOppositePolarity(first: String, second: String): Boolean =
        hasExplicitNegation(first) != hasExplicitNegation(second)
}
