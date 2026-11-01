package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationProbe
import at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationVerifier
import at.heinz.deviceai.learning.span.LocationEvidenceSnapshot
import at.heinz.deviceai.learning.span.EntityRelationLocationShadowAdapter

/**
 * Explicit per-turn source reflex: deterministic, read-only and zero LLM tokens.
 * Never consumes stored personal memories, writes facts or requests a capability.
 */
internal object CertifiedLocationReflex {
    private val format = Regex("""(?is)^\s*Beleg:\s*([^\r\n]{1,2000})\s*\r?\n\s*Frage:\s*([^\r\n]{1,160})\s*$""")
    data class Answer(val text: String, val sourceHash: String, val spanStart: Int,
                      val spanEnd: Int)

    fun answer(userTurn: String): Answer? {
        if (userTurn.length > 2250) return null
        val groups = format.matchEntire(userTurn) ?: return null
        val raw = groups.groupValues[1].trim()
        val question = groups.groupValues[2].trim()
        if (raw.isBlank()) return null
        val item = EvidenceBoundNaturalLocationProbe.Evidence(
            "explicit-turn", 1, EvidenceBoundNaturalLocationProbe.digest(raw), raw)
        val snapshot = runCatching { LocationEvidenceSnapshot.freeze(listOf(item)) }.getOrNull() ?: return null
        val found = EvidenceBoundNaturalLocationProbe.answer(question, listOf(item))
            as? EvidenceBoundNaturalLocationProbe.Decision.Found ?: return null
        if (!EvidenceBoundNaturalLocationVerifier.verify(question, listOf(item), found)) return null
        val roles = EntityRelationLocationShadowAdapter.encode(question, snapshot, enabled = true) ?: return null
        if (roles.location != found || roles.snapshotSha256 != snapshot.sha256) return null
        return Answer("Im ausdrücklich angegebenen Beleg steht: " + found.text +
            "\nQuelle: aktueller Eingabetext, nicht unabhängig bestätigter Weltfakt." +
            "\nBelegposition: [" + found.start + "," + found.endExclusive + ")" +
            "\nBelegsnapshot SHA-256: " + snapshot.sha256,
            snapshot.sha256, found.start, found.endExclusive)
    }
}
