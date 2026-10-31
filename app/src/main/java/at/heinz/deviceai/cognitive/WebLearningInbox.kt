package at.heinz.deviceai.cognitive

import java.security.MessageDigest

/** Provenance-preserving, purely advisory Internet learning candidates.
 * Retrieval and claimed corroboration do NOT make text true or make it neural training.
 */
data class WebLearningCandidate(
    val id: String,
    val sourceUrl: String,
    val sourceHash: String,
    val text: String,
    val sourceCount: Int,
    val contradictory: Boolean
)

object WebLearningInbox {
    /** Public web documents frequently contain navigation text and HTML entities. */
    internal fun cleanExcerpt(value: String): String = value
        .replace(Regex("(?i)&(?:nbsp|#0*160|#x0*a0);"), " ")
        .replace(Regex("(?i)&amp;"), "&")
        .replace(Regex("(?i)&quot;"), "\"")
        .replace(Regex("(?i)&(?:apos|#0*39);"), "'")
        .replace(Regex("\\s+"), " ")
        .trim().take(400)

    private fun isNavigation(text: String): Boolean {
        val lower = text.lowercase(java.util.Locale.ROOT)
        val signals = listOf("zum inhalt", "zur suche", "jetzt registrieren",
            "suchkategorien", "wörterbuch synonyme", "online-wörterbuch")
        return signals.count { it in lower } >= 2 ||
            lower.startsWith("jetzt registrieren") ||
            lower.startsWith("suchkategorien") ||
            lower.startsWith("erfahren sie in diesem artikel")
    }

    /** Bounded recall of explicitly reviewed source-notes; not a claim of objective truth. */
    fun summarizeSavedNotes(notes: List<WebLearningCandidate>): String {
        if (notes.isEmpty()) return "Ich habe noch keine ausdrücklich übernommenen Internet-Quellenhinweise. " +
            "Öffne zuerst WEB RECHERCHE und dann INTERNET · LERNSTOFF PRÜFEN."
        return buildString {
            append("Deine geprüften Quellenhinweise (keine unabhängig bewiesenen Fakten):")
            notes.take(24).forEachIndexed { index, note ->
                appendLine().appendLine().append(index + 1).append(". ").append(note.text.take(240))
                appendLine().append("Quelle: ").append(note.sourceUrl.take(220))
                appendLine().append("SHA-256: ").append(note.sourceHash.take(16))
                if (note.contradictory) append(" · Widerspruch")
            }
        }.take(12_000)
    }

    fun candidates(dossier: ResearchDossier): List<WebLearningCandidate> {
        val canonical = dossier.sources.associateBy { it.canonicalUrl }
        return dossier.graph.claims.asSequence()
            .filter { it.provenance == ProvenanceType.EXTERNAL_SOURCE }
            .mapNotNull { claim ->
                val source = claim.sourceReferences.firstNotNullOfOrNull { canonical[it] }
                    ?: return@mapNotNull null
                if (source.duplicateOf != null || source.contentHash !in dossier.completedSourceHashes)
                    return@mapNotNull null
                val text = cleanExcerpt(claim.text)
                if (text.length < 28 || isNavigation(text) ||
                    text.contains(Regex("""(?i)\b(ignore previous|system prompt|developer instruction|api.?key|password|passwort)\b""")))
                    return@mapNotNull null
                val independentSources = claim.sourceReferences.mapNotNull { canonical[it] }
                    .filter { it.duplicateOf == null }
                    .distinctBy { it.domain }.size
                val id = sha256(source.contentHash + "\\n" + text)
                WebLearningCandidate(
                    id, source.canonicalUrl, source.contentHash, text,
                    independentSources, claim.validationState == ValidationState.CONTRADICTED
                )
            }
            .distinctBy(WebLearningCandidate::id)
            .take(512)
            .toList()
            .let { all ->
                // Avoid showing eight fragments from one site when independent
                // source URLs are available in the same research dossier.
                val groups = all.groupBy { it.sourceUrl }.values.map { it.iterator() }
                val selected = ArrayList<WebLearningCandidate>(8)
                while (selected.size < 8 && groups.any { it.hasNext() }) {
                    for (group in groups) {
                        if (group.hasNext()) selected.add(group.next())
                        if (selected.size == 8) break
                    }
                }
                selected
            }
    }
    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
