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
                val text = claim.text.trim().replace(Regex("\\s+"), " ").take(400)
                if (text.length < 12 || text.contains(Regex("""(?i)\b(ignore previous|system prompt|developer instruction|api.?key|password|passwort)\b""")))
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
            .take(8)
            .toList()
    }
    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
