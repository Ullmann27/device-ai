package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Offline retrieval from manually reviewed source notes.
 * This is lexical retrieval, not a neural or generative language model.
 * Neither answers nor retrieved source text can authorize tools.
 */
object GroundedWebRecall {
    private val prefixes = listOf(
        "was weißt du aus dem internet über ",
        "was weißt du über ",
        "was steht in deinen quellen über ",
        "was steht in meinen quellen über ",
        "was hast du im internet über ",
        "was hast du über ",
        "erzähl mir aus deinen quellen über ",
        "suche in meinen quellen nach "
    )
    private val stop = setOf("und","oder","aber","auch","das","der","die","den","dem","des",
        "ein","eine","einen","einem","einer","ist","sind","hat","haben","von","für","mit",
        "auf","bei","zum","zur","was","wie","wer","wo","wann","warum","über","aus","deinen",
        "meinen","etwas","bitte","kannst","du","mir","noch","nicht","gibt","es","denn")

    private fun tokens(text: String): Set<String> =
        text.lowercase(Locale.GERMAN).split(Regex("[^\\p{L}\\p{N}]+"))
            .asSequence().filter { it.length >= 3 && it !in stop }.take(60).toSet()

    fun topicOf(message: String): String? {
        if (message.length !in 5..320) return null
        val normalized = message.trim().lowercase(Locale.GERMAN)
        val prefix = prefixes.firstOrNull(normalized::startsWith) ?: return null
        return message.trim().drop(prefix.length).trim().trimEnd('?', '.', '!', ' ')
            .takeIf { it.length in 3..120 }
    }

    /** Returns null when no explicit knowledge question is recognized. */
    fun answer(message: String, notes: List<WebLearningCandidate>): String? {
        val topic = topicOf(message) ?: return null
        val wanted = tokens(topic)
        if (wanted.isEmpty()) return "Bitte nenne ein konkreteres Thema für die Quellensuche."
        require(notes.size <= 24)
        val ranked = notes.asSequence()
            .filter { !it.contradictory && it.text.length <= 400 }
            .map { note ->
                val overlap = tokens(note.text).intersect(wanted).size
                Triple(note, overlap, overlap.toDouble() / wanted.size)
            }
            .filter { (_, overlap, coverage) ->
                overlap >= (if (wanted.size == 1) 1 else 2) && coverage >= 0.66
            }
            .sortedWith(compareByDescending<Triple<WebLearningCandidate,Int,Double>> { it.third }
                .thenByDescending { it.second }.thenBy { it.first.id })
            .take(3).toList()
        if (ranked.isEmpty()) return "Zum Thema „" + topic.take(100) +
            "“ habe ich in meinen ausdrücklich gespeicherten Quellen noch keinen ausreichend passenden Beleg. " +
            "Das sagt nichts darüber aus, ob es im Internet dazu Antworten gibt."
        return buildString {
            append("Ich habe ").append(ranked.size).append(" Quellenhinweise gefunden. ")
            append("Das sind Quellenaussagen, keine unabhängig bestätigten Fakten:")
            ranked.forEachIndexed { i, (note,_,_) ->
                append("\n\n").append(i+1).append(". ").append(note.text.take(300))
                append("\nQuelle: ").append(note.sourceUrl.take(250))
                append("\nInhalt SHA-256: ").append(note.sourceHash.take(16))
            }
            append("\n\nMethode: lokale Wortübereinstimmung, keine KI-Sprachgenerierung.")
        }.take(2400)
    }
}
