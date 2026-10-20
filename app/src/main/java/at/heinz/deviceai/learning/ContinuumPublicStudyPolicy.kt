package at.heinz.deviceai.learning

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Curated public-only discovery policy. This is intentionally NOT an unrestricted crawler.
 * Source text is always SOURCE_OBSERVATION, never owner instruction or verified ground truth.
 */
object ContinuumPublicStudyPolicy {
    const val PERIOD_MS = 60L * 60L * 1000L
    const val MAX_RESPONSE_BYTES = 96 * 1024
    const val MAX_EXCERPT_CHARS = 390
    const val MAX_ROUND_WALL_MS = 15_000L
    const val MAX_TOPICS = 48

    val topics = listOf(
        "Künstliche Intelligenz", "Maschinelles Lernen", "Künstliches neuronales Netz",
        "Kontinuierliches Lernen", "Wissensrepräsentation", "Semantisches Netz",
        "Natürliche Sprache", "Sprachmodell", "Information Retrieval",
        "Programmiersprache", "Algorithmus", "Softwaretest",
        "Versionsverwaltung", "Softwarearchitektur", "Android (Betriebssystem)",
        "Kryptographie", "Datenschutz", "Informatik",
        "Mathematik", "Wahrscheinlichkeitstheorie", "Statistik",
        "Lineare Algebra", "Optimierung (Mathematik)", "Wissenschaftliche Methode",
        "Physik", "Chemie", "Biologie", "Astronomie",
        "Geographie", "Geschichte", "Literatur",
        "Linguistik", "Pädagogik", "Psychologie",
        "Ökologie", "Energie", "Elektrotechnik",
        "Robotik", "Maschinenbau", "Gesundheitswesen",
        "Klimawandel", "Photovoltaik", "Geologie", "Musiktheorie",
        "Philosophie", "Wirtschaftswissenschaft", "Recht", "Meteorologie"
    )

    fun topicForCycle(index:Long):String {
        require(index>=0L)
        return topics[(index % topics.size).toInt()]
    }

    fun encyclopediaApiUrl(title:String):String {
        require(title in topics)
        val escaped=URLEncoder.encode(title,StandardCharsets.UTF_8.name())
        return "https://de.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1" +
            "&exintro=1&format=json&redirects=1&titles="+escaped
    }

    fun publicArticleUrl(title:String):String {
        require(title in topics)
        val escaped=URLEncoder.encode(title.replace(' ','_'),StandardCharsets.UTF_8.name())
            .replace("+","%20")
        return "https://de.wikipedia.org/wiki/"+escaped
    }

    fun normalizeExcerpt(raw:String):String {
        val text=raw.replace(Regex("<[^>]*>")," ").replace(Regex("\\s+")," ")
            .trim().take(MAX_EXCERPT_CHARS).trim()
        require(text.length in 20..MAX_EXCERPT_CHARS)
        require(!text.contains('\u0000'))
        return text
    }

    fun digest(text:String):String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT,it.toInt() and 255) }
}
