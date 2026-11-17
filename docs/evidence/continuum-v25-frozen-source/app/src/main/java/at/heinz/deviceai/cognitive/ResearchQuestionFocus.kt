package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Question-topic focus for public web discovery only.
 * This is not a semantic fact verifier; unrelated search titles are dropped
 * rather than promoted into research evidence.
 */
internal object ResearchQuestionFocus {
    private val interrogatives = setOf(
        "wie", "was", "warum", "wieso", "weshalb", "wo", "wer", "welche",
        "welcher", "welches", "wann", "womit", "wodurch", "how", "what",
        "why", "where", "when", "which", "who"
    )
    private val generic = interrogatives + setOf(
        "funktioniert", "funktionieren", "funktioniere", "geht", "gehts",
        "ist", "sind", "das", "die", "der", "den", "dem", "des", "ein",
        "eine", "einen", "einem", "eines", "und", "oder", "von", "für",
        "auf", "mit", "durch", "man", "mir", "mein", "meine", "meinen",
        "kann", "können", "werden", "wird", "gibt", "bitte", "erkläre",
        "erklären", "erklärt", "erklärung", "etwas", "über", "zum",
        "zur", "ich", "du", "es", "sie", "wir", "the", "does", "do",
        "did", "are", "is", "can", "work", "works", "working", "about",
        "please", "tell", "me", "of", "for", "in", "a", "an", "and"
    )

    private fun tokens(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }

    private fun meaningfulTokens(question: String): List<String> =
        tokens(question).filterNot { it in generic }.take(12)

    private fun stem(token: String): String {
        if (token.length <= 5) return token
        return when {
            token.endsWith("en") -> token.dropLast(2)
            token.endsWith("ern") -> token.dropLast(3)
            token.endsWith("e") || token.endsWith("n") || token.endsWith("s") ->
                token.dropLast(1)
            else -> token
        }
    }

    private fun isQuestion(text: String): Boolean {
        val first = tokens(text).firstOrNull()
        return '?' in text || first in interrogatives
    }

    fun searchText(question: String): String {
        val cleaned = question.trim().replace(Regex("\\s+"), " ")
        if (!isQuestion(cleaned)) return cleaned.take(180)
        val focus = meaningfulTokens(cleaned)
        return (if (focus.isEmpty()) cleaned else focus.joinToString(" ")).take(180)
    }

    fun titleMatches(question: String, title: String?): Boolean {
        if (!isQuestion(question)) return true
        val focus = meaningfulTokens(question).map(::stem)
        if (focus.isEmpty()) return true
        val titleTokens = title?.let(::tokens)?.map(::stem).orEmpty()
        if (titleTokens.isEmpty()) return false
        return focus.any { term ->
            titleTokens.any { word ->
                word == term || (term.length >= 5 && word.startsWith(term)) ||
                    (word.length >= 5 && term.startsWith(word))
            }
        }
    }
}
