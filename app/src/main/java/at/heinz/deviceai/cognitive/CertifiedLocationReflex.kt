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
        val (found, snapshot) = prove(question, listOf(item)) ?: return null
        return Answer("Im ausdrücklich angegebenen Beleg steht: " + found.text +
            "\nQuelle: aktueller Eingabetext, nicht unabhängig bestätigter Weltfakt." +
            "\nBelegposition: [" + found.start + "," + found.endExclusive + ")" +
            "\nBelegsnapshot SHA-256: " + snapshot.sha256,
            snapshot.sha256, found.start, found.endExclusive)
    }

    /**
     * Session specialist (Argon E7, Platz 1): a bare location question is answered from the
     * owner's own earlier messages of the SAME session before any model or web route runs.
     * Evidence is only what the owner typed (never model replies, research text or stored
     * memories); the rule core must find exactly one supporting clause, otherwise null and the
     * normal cognitive chain continues. Deterministic, read-only, zero LLM tokens.
     */
    fun answerFromSession(question: String, priorUserTurns: List<String>): Answer? {
        val q = question.trim()
        if (q.length !in 5..160 || priorUserTurns.isEmpty()) return null
        val subject = questionShape.matchEntire(q)?.groupValues?.get(1) ?: return null
        val mentions = Regex("""(?iu)(?<![\p{L}\p{M}\p{N}-])""" + Regex.escape(subject) + """(?![\p{L}\p{M}\p{N}-])""")
        // Only turns that mention the asked person are evidence; unrelated small talk must
        // neither support nor (via the verifier's pronoun rule) veto the proof.
        val items = priorUserTurns.takeLast(4).mapIndexedNotNull { index, turn ->
            val raw = turn.trim()
            if (raw.isBlank() || raw.length > 1000 || format.matches(raw) || !mentions.containsMatchIn(raw)) null
            else EvidenceBoundNaturalLocationProbe.Evidence(
                "session-turn-" + (index + 1), 1, EvidenceBoundNaturalLocationProbe.digest(raw), raw)
        }
        if (items.isEmpty()) return null
        val (found, snapshot) = prove(q, items) ?: return null
        return Answer("Du hast in dieser Sitzung gesagt: " + found.text +
            "\nQuelle: deine eigene frühere Nachricht (" + found.sourceId + "), keine bestätigte Erinnerung und kein geprüfter Weltfakt." +
            "\nBelegsnapshot SHA-256: " + snapshot.sha256,
            snapshot.sha256, found.start, found.endExclusive)
    }

    private val questionShape = Regex("""(?iu)^\s*wo\s+(?:ist|wohnt|lebt|steht)\s+([\p{L}][\p{L}\p{M}-]{1,31})\s*\?\s*$""")

    private fun prove(question: String, items: List<EvidenceBoundNaturalLocationProbe.Evidence>):
            Pair<EvidenceBoundNaturalLocationProbe.Decision.Found, LocationEvidenceSnapshot>? {
        val snapshot = runCatching { LocationEvidenceSnapshot.freeze(items) }.getOrNull() ?: return null
        val found = EvidenceBoundNaturalLocationProbe.answer(question, items)
            as? EvidenceBoundNaturalLocationProbe.Decision.Found ?: return null
        if (!EvidenceBoundNaturalLocationVerifier.verify(question, items, found)) return null
        val roles = EntityRelationLocationShadowAdapter.encode(question, snapshot, enabled = true) ?: return null
        if (roles.location != found || roles.snapshotSha256 != snapshot.sha256) return null
        return found to snapshot
    }
}
