package at.heinz.deviceai.learning

import at.heinz.deviceai.intelligence.ewm.Ewm1Fact
import at.heinz.deviceai.intelligence.ewm.Ewm1Lexicon
import at.heinz.deviceai.intelligence.ewm.Ewm1Workspace
import java.text.Normalizer
import java.util.Locale

/** Remove-only guard for explicit named-owner attributes. No new store or NLP
 * authority: unsupported grammar abstains within this scope. At most 16 short
 * assertions enter the existing EWM reader, avoiding its quadratic full-ledger
 * comparison on every query. Revocations remain invalidators, never answers.
 */
internal object OwnerAttributeEvidenceScope {
    private data class Request(val holder: String, val kind: String, val item: String? = null)
    private const val NAME = "([\\p{L}\\p{M}][\\p{L}\\p{M}'’-]{1,79}(?:\\s+[\\p{L}\\p{M}][\\p{L}\\p{M}'’-]{1,39}){0,2})"
    private val location = Regex("(?iu)^wo\\s+(?:wohnt|lebt)\\s+$NAME(?:\\s+(?:jetzt|aktuell|derzeit|heute))?\\s*\\?$" )
    private val color = Regex("(?iu)^welche\\s+farbe\\s+(?:hat|besitzt|trägt)\\s+$NAME\\s+([\\p{L}\\p{M}-]+)\\s*\\?$" )
    private val age = Regex("(?iu)^wie\\s+alt\\s+ist\\s+$NAME\\s*\\?$" )
    private val work = Regex("(?iu)^was\\s+arbeitet\\s+$NAME\\s*\\?$" )
    private val claim = Regex("(?u)^(?:(?:Jetzt|Derzeit|Aktuell|Früher|Heute|Damals|Inzwischen)\\s+)?" +
        "(\\p{Lu}[\\p{L}\\p{M}-]{1,39}(?:\\s+\\p{Lu}[\\p{L}\\p{M}-]{1,39}){0,2})\\s+" +
        "(wohnt|wohnte|lebt|lebte|hat|besitzt|trägt|ist|arbeitet)\\b")
    // A confirmed age statement can introduce the holder through a directly
    // bound family role, e.g. "Meine Tochter Klara ist 29.".
    // This is deliberately not a general role or reported-speech parser.
    private val familyAge = Regex("(?iu)^(?:meine|mein|unsere|unser)\\s+" +
        "(?:tochter|sohn|schwester|bruder|enkelin|enkel)\\s+$NAME\\s+ist\\s+[0-9]{1,3}\\s*\\.?$")
    private val correction = Regex("(?iu)^(?:korrektur|berichtigung)\\s*:\\s*")
    private fun key(s: String) = Normalizer.normalize(s.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)
    private fun request(q: String): Request? {
        location.matchEntire(q)?.let { m ->
            val name = m.groupValues[1].replace(Regex("(?iu)\\s+(?:jetzt|aktuell|derzeit|heute)$"), "")
            return Request(key(name), "LOCATION")
        }
        color.matchEntire(q)?.let { m ->
            val genitive = m.groupValues[1]
            val holder = when {
                genitive.endsWith("'") || genitive.endsWith("’") -> genitive.dropLast(1)
                genitive.endsWith("'s", true) || genitive.endsWith("’s", true) -> genitive.dropLast(2)
                genitive.endsWith("s", true) -> genitive.dropLast(1)
                else -> return null
            }
            return Request(key(holder), "COLOR", Ewm1Lexicon.stem(key(m.groupValues[2])))
        }
        age.matchEntire(q)?.let { return Request(key(it.groupValues[1]), "AGE") }
        work.matchEntire(q)?.let { return Request(key(it.groupValues[1]), "WORK") }
        return null
    }

    /** null means outside this bounded grammar; empty means no safe evidence. */
    fun select(query: String, records: List<LearningExperience>, ranked: List<ContinuumMemoryHit>): Set<String>? {
        val ask = request(query) ?: return null
        if(ask.holder.split(' ').any { it in setOf("mein", "meine", "meiner", "unser", "unsere", "dein", "deine") })return null
        val owner = records.filter { it.type == LearningExperienceType.USER_FACT &&
            it.provenance == LearningProvenance.USER_CONFIRMED && it.trust == LearningTrustClass.OWNER_CONFIRMED &&
            it.status in setOf(LearningStatus.CONFIRMED, LearningStatus.LEARNED, LearningStatus.REPLAY_PENDING, LearningStatus.REVOKED) }
        val heads = owner.mapNotNull { e ->
            val cleaned = e.content.replaceFirst(correction, "")
            val direct = claim.find(cleaned.take(160))
            val roleAgeHolder = if(ask.kind=="AGE" && direct==null)
                familyAge.matchEntire(cleaned.trim())?.groupValues?.get(1) else null
            val verb = direct?.groupValues?.get(2) ?: if(roleAgeHolder!=null)"ist" else null
            val holder = direct?.groupValues?.get(1) ?: roleAgeHolder
            val compatible = when(ask.kind) {
                "LOCATION" -> verb in setOf("wohnt", "wohnte", "lebt", "lebte")
                "COLOR" -> verb in setOf("hat", "besitzt", "trägt")
                "WORK" -> verb in setOf("arbeitet", "ist")
                else -> verb == "ist"
            }
            if(compatible && holder!=null)Triple(e, cleaned, key(holder)) else null
        }
        // A bare first name is no proof that two differently named people are one.
        if(heads.any { it.third != ask.holder && it.third.startsWith(ask.holder + " ") })return emptySet()
        val candidates = heads.filter { it.third == ask.holder }
        if(candidates.isEmpty() || candidates.size > 16 || candidates.any { it.second.length > 512 })return emptySet()
        // A holder in the first clause cannot own arbitrary attributes in a
        // later clause. This bounded direct-assertion reader does not prove
        // coordination, reported speech or multiple assertions.
        if(candidates.any { (_, text, _) ->
            Regex("[.!?;\\r\\n]").containsMatchIn(text.trimEnd().trimEnd('.', '!', '?')) ||
                Regex("(?iu)\\b(?:und|oder)\\b").containsMatchIn(text) ||
                (',' in text && !Regex("(?iu)\\bsondern\\b").containsMatchIn(text))
        })return emptySet()
        val parsed = candidates.mapIndexed { i, (e, text, _) -> e to Ewm1Fact(e.id, text, i) }
            .filter { (_, f) -> f.subjectKnown && f.tense == 'n' &&
                f.tokens.none { it.lower in setOf("wenn", "falls", "würde", "würden", "hätte", "hätten") } && when(ask.kind) {
                "LOCATION" -> "wohnen" in f.relationGroups
                "COLOR" -> f.tokens.any { Ewm1Lexicon.stem(it.lower) == ask.item } &&
                    (f.tokens.any { Ewm1Lexicon.isColor(it.lower) } || f.tokens.any { it.lower in Ewm1Lexicon.NEGATIONS })
                "WORK" -> "arbeiten" in f.relationGroups
                else -> "alter" in f.relationGroups || f.tokens.any { it.lower.any(Char::isDigit) }
            } }
        if(parsed.isEmpty())return emptySet()
        // CONFIRMED updatedAt can be an explicit reactivation by confirmOwnerFact.
        // A later revocation of an older assertion does not make that assertion new.
        fun assertionAt(e: LearningExperience) = if(e.status == LearningStatus.CONFIRMED)e.updatedAt else e.createdAt
        val newestTime = parsed.maxOf { assertionAt(it.first) }
        val tied = parsed.filter { assertionAt(it.first) == newestTime }
        val newest = tied.singleOrNull() ?: tied.filter { it.second.hasUpdateMarker }.singleOrNull() ?: return emptySet()
        if(newest.first.status == LearningStatus.REVOKED)return emptySet()
        val f = newest.second
        if(f.tokens.any { it.lower in Ewm1Lexicon.NEGATIONS } && !f.correction)return emptySet()
        val workspace = Ewm1Workspace(listOf(newest.first.id to f.text)) { _, ids ->
            ranked.filter { it.id in ids }.map { it.id }
        }
        val selected = workspace.decide(query)
        return if(selected.decision == "QUERY")selected.answerFacts.toSet() else emptySet()
    }
}
