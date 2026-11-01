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
                      val spanEnd: Int, val support: EvidenceBoundNaturalLocationProbe.Decision.Found)

    sealed class Routing {
        data class Proven(val proof: Answer) : Routing()
        data class Withheld(val reason: String) : Routing() {
            val text: String get() = "Aus dem vorliegenden Text lässt sich kein eindeutiger Ort belegen. " +
                "Bitte kläre die Person, den Zeitpunkt oder die widersprüchliche Angabe."
        }
    }

    /** A recognized evidence request is terminal even when verification declines it. */
    fun routeExplicit(userTurn: String): Routing? {
        val groups = format.matchEntire(userTurn) ?: return null
        if (!questionShape.matches(groups.groupValues[2].trim())) return null
        return answer(userTurn)?.let { Routing.Proven(it) }
            ?: Routing.Withheld("EXPLICIT_EVIDENCE_NOT_PROVEN")
    }

    fun routeFromSession(question: String, priorUserTurns: List<String>?): Routing? {
        val subject = questionShape.matchEntire(question.trim())?.groupValues?.get(1) ?: return null
        if (priorUserTurns == null) return Routing.Withheld("INCOMPLETE_SESSION_EVIDENCE")
        val mentions = mention(subject)
        if (priorUserTurns.takeLast(4).none { mentions.containsMatchIn(it) }) return null
        return answerFromSession(question, priorUserTurns)?.let { Routing.Proven(it) }
            ?: Routing.Withheld("SESSION_EVIDENCE_NOT_PROVEN")
    }

    fun answer(userTurn: String): Answer? {
        if (userTurn.length > 2250) return null
        val groups = format.matchEntire(userTurn) ?: return null
        val raw = groups.groupValues[1] // Preserve literal whitespace for SHA and UTF-16 offsets.
        val question = groups.groupValues[2].trim()
        if (raw.isBlank()) return null
        val item = EvidenceBoundNaturalLocationProbe.Evidence(
            "explicit-turn", 1, EvidenceBoundNaturalLocationProbe.digest(raw), raw)
        val (found, snapshot) = prove(question, listOf(item)) ?: return null
        return Answer("Im ausdrücklich angegebenen Beleg steht: " + found.text +
            "\nQuelle: aktueller Eingabetext, nicht unabhängig bestätigter Weltfakt." +
            "\nBelegposition: [" + found.start + "," + found.endExclusive + ")" +
            "\nBelegsnapshot SHA-256: " + snapshot.sha256,
            snapshot.sha256, found.start, found.endExclusive, found)
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
        val mentions = mention(subject)
        val window = priorUserTurns.takeLast(4)
        // Never silently drop a long correction, nor guess a referent for an unnamed
        // retraction. These are invalidators, not positive evidence or new facts.
        if (window.any { it.length > 1000 || it.any(Char::isISOControl) ||
                !mentions.containsMatchIn(it) && unscopedCorrection.containsMatchIn(it) }) return null
        // Only turns that mention the asked person are evidence; unrelated small talk must
        // neither support nor (via the verifier's pronoun rule) veto the proof.
        val items = window.mapIndexedNotNull { index, turn ->
            val raw = turn // Hash and offsets bind the original user literal, not a display copy.
            if (raw.isBlank() || format.matches(raw) || questionShape.matches(raw.trim()) || !mentions.containsMatchIn(raw)) null
            else EvidenceBoundNaturalLocationProbe.Evidence(
                "session-turn-" + (index + 1), 1, EvidenceBoundNaturalLocationProbe.digest(raw), raw)
        }
        if (items.isEmpty()) return null
        val (found, snapshot) = prove(q, items) ?: return null
        return Answer("Du hast in dieser Sitzung gesagt: " + found.text +
            "\nQuelle: deine eigene frühere Nachricht (" + found.sourceId + "), keine bestätigte Erinnerung und kein geprüfter Weltfakt." +
            "\nBelegsnapshot SHA-256: " + snapshot.sha256,
            snapshot.sha256, found.start, found.endExclusive, found)
    }

    /**
     * EWM2 specialist (Argon E7 on long-term memory): the same rule core proves a location
     * question against the owner's CONFIRMED facts that the existing SWB1 ranking already
     * selected. Nothing is read from any other store, nothing is written, revoked facts never
     * arrive here. Exactly one supporting fact is required; conflicting or duplicate facts
     * (e.g. an unrevoked older residence) make the specialist abstain so the plain retrieval
     * list remains the answer. Returns the proven text and the id of the supporting fact.
     */
    fun answerFromConfirmedFacts(question: String, confirmedFacts: List<Pair<String, String>>): Answer? {
        val q = question.trim()
        if (q.length !in 5..160 || confirmedFacts.isEmpty()) return null
        val subject = questionShape.matchEntire(q)?.groupValues?.get(1) ?: return null
        val mentions = mention(subject)
        if (confirmedFacts.any { (_, text) -> text.length > 1000 && mentions.containsMatchIn(text) }) return null
        // A later owner-confirmed revocation without the word "in" must never disappear
        // while an older positive place fact survives the filter below.
        // No temporal precedence is inferred from this bounded source list.
        val namedRetraction = Regex("""(?iu)\b(?:weggezogen|umgezogen|widerruf(?:en)?|zurueckgezogen|zurückgezogen|ort\s+unbekannt)\b""")
        if (confirmedFacts.any { (_, text) -> mentions.containsMatchIn(text) &&
                namedRetraction.containsMatchIn(text) }) return null
        // Facts about the person that cannot be about a place ("Mama mag Kaffee.") are not
        // evidence; anything mentioning the person together with "in" (incl. negations, time
        // words, other relations) stays in so the verifier can veto it.
        val maybePlace = Regex("""(?iu)(?<![\p{L}\p{M}])in(?![\p{L}\p{M}])""")
        val items = confirmedFacts
            .filter { (id, text) -> id.isNotBlank() && text.isNotBlank() && text.length <= 1000 &&
                mentions.containsMatchIn(text) && maybePlace.containsMatchIn(text) }
            .map { (id, text) -> EvidenceBoundNaturalLocationProbe.Evidence(
                id, 1, EvidenceBoundNaturalLocationProbe.digest(text), text) }
        // More than four facts about the person cannot be checked for conflicts by the rule core
        // (its evidence bound) – abstain instead of silently dropping candidates.
        if (items.isEmpty() || items.size > 4 || items.map { it.sourceId }.distinct().size != items.size) return null
        val (found, snapshot) = prove(q, items) ?: return null
        return Answer("Aus deiner bestätigten Erinnerung: " + found.text +
            "\nBeleg: Erinnerung " + found.sourceId + " (Regelkern und Verifier geprüft, Snapshot " +
            snapshot.sha256.take(16) + "…)",
            snapshot.sha256, found.start, found.endExclusive, found)
    }

    private val questionShape = Regex("""(?iu)^\s*wo\s+(?:ist|wohnt|lebt|steht)\s+([\p{L}][\p{L}\p{M}-]{1,31})\s*\?\s*$""")
    private val unscopedCorrection = Regex("""(?iu)\b(?:nein|korrektur|widerruf|widerrufen|falsch|ungültig|nicht\s+mehr|nicht\s+dort)\b|\b(?:sie|er|es)\s+(?:ist|wohnt|lebt|steht|war|wohnte|lebte|stand)\b""")
    private fun mention(subject: String) = Regex("""(?iu)(?<![\p{L}\p{M}\p{N}-])""" +
        Regex.escape(subject) + """(?![\p{L}\p{M}\p{N}-])""")

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
