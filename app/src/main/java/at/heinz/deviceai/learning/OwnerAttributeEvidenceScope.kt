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
    // Keep the named holder even when a later age claim is negated or uses a unit.
    // Head recognition only supplies an invalidator/candidate, never a literal answer.
    private val familyAgeHead = Regex("(?iu)^(?:meine|mein|unsere|unser)\\s+" +
        "(?:tochter|sohn|schwester|bruder|enkelin|enkel)\\s+$NAME\\s+ist\\b")
    private val correction = Regex("(?iu)^(?:korrektur|berichtigung)\\s*:\\s*")
    private val locationChange = Regex("(?iu)\\b(?:umgezogen|weggezogen|ausgezogen|fortgezogen|verzogen|widerrufen|ungültig)\\b|" +
        "\\b(?:zieht|zog)\\s+(?:weg|fort|um|nach|aus)\\b")
    private val directAgeValue = Regex("(?iu)^$NAME\\s+ist\\s+([0-9]{1,3})(?:\\s+Jahre(?:\\s+alt)?)?\\s*\\.?$")
    private val familyAgeValue = Regex("(?iu)^(?:meine|mein|unsere|unser)\\s+" +
        "(?:tochter|sohn|schwester|bruder|enkelin|enkel)\\s+$NAME\\s+ist\\s+([0-9]{1,3})(?:\\s+Jahre(?:\\s+alt)?)?\\s*\\.?$")
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
        // A move need not contain "wohnen" or a new place. Keep its ledger revision as
        // a REMOVE-ONLY barrier before narrowing to parseable residence assertions.
        // It cannot supply an answer, and a later explicit residence can supersede it.
        fun assertionAt(e: LearningExperience) = if(e.status == LearningStatus.CONFIRMED)e.updatedAt else e.createdAt
        val namedLocationHead = if (ask.kind == "LOCATION") Regex("(?iu)^" + Regex.escape(ask.holder) +
            "\\s+(?:ist|war|wurde|wohnt|wohnte|lebt|lebte|zieht|zog)\\b") else null
        val locationBarrier = if (namedLocationHead == null) null else owner.filter { e ->
            val text = Normalizer.normalize(e.content.replaceFirst(correction, ""), Normalizer.Form.NFC)
            namedLocationHead.containsMatchIn(text) && locationChange.containsMatchIn(text)
        }.maxOfOrNull(::assertionAt)
        val heads = owner.mapNotNull { e ->
            val cleaned = e.content.replaceFirst(correction, "")
            // The broad NAME grammar can read "Meine Tochter Klara" as an
            // apparent full subject and shadow the actual named person Klara.
            // Prefer the anchored kinship-age grammar BEFORE generic parsing.
            val roleAgeHolder = if(ask.kind=="AGE")
                familyAgeHead.find(cleaned.trim())?.groupValues?.get(1) else null
            val direct = if(roleAgeHolder == null)claim.find(cleaned.take(160)) else null
            val verb = if(roleAgeHolder!=null)"ist" else direct?.groupValues?.get(2)
            val holder = roleAgeHolder ?: direct?.groupValues?.get(1)
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
            .filter { (_, f) ->
                // The generic EWM grammar parses "Klara ist 29" as a subject,
                // but not "Meine Tochter Klara ist 29". For a single directly
                // attributed and bounded kinship-age sentence, strict matching
                // above already establishes the named holder. Let that exact
                // grammar through without inventing general NLP authority.
                val directlyBoundFamilyAge = ask.kind == "AGE" && familyAgeHead.containsMatchIn(f.text)
                (directlyBoundFamilyAge || (f.subjectKnown && f.tense == 'n')) &&
                    f.tokens.none { it.lower in setOf("wenn", "falls", "würde", "würden", "hätte", "hätten") } &&
                    when(ask.kind) {
                        "LOCATION" -> "wohnen" in f.relationGroups
                        "COLOR" -> f.tokens.any { Ewm1Lexicon.stem(it.lower) == ask.item } &&
                            (f.tokens.any { Ewm1Lexicon.isColor(it.lower) } || f.tokens.any { it.lower in Ewm1Lexicon.NEGATIONS })
                        "WORK" -> "arbeiten" in f.relationGroups
                        else -> directlyBoundFamilyAge || "alter" in f.relationGroups ||
                            f.tokens.any { it.lower.any(Char::isDigit) }
                    }
            }
        if(parsed.isEmpty())return emptySet()
        // CONFIRMED updatedAt can be an explicit reactivation by confirmOwnerFact.
        // A later revocation of an older assertion does not make that assertion new.
        val newestTime = parsed.maxOf { assertionAt(it.first) }
        if (locationBarrier != null && locationBarrier >= newestTime) return emptySet()
        val tied = parsed.filter { assertionAt(it.first) == newestTime }
        val newest = tied.singleOrNull() ?: tied.filter { it.second.hasUpdateMarker }.singleOrNull() ?: return emptySet()
        if(newest.first.status == LearningStatus.REVOKED)return emptySet()
        val f = newest.second
        if(f.tokens.any { it.lower in Ewm1Lexicon.NEGATIONS } && !f.correction)return emptySet()
        val workspace = Ewm1Workspace(listOf(newest.first.id to f.text)) { _, ids ->
            ranked.filter { it.id in ids }.map { it.id }
        }
        val selected = workspace.decide(query)
        if(selected.decision == "QUERY")return selected.answerFacts.toSet()
        // EWM's general-purpose grammar can abstain on "Meine Tochter Klara
        // ist 29." even after the owner scope has proven an exact single-clause
        // age assertion. Do not let that suppress this precisely scoped fact.
        // Never introduce a new candidate: it MUST already be a genuine
        // ranked OWNER_CONFIRMED record, named explicitly by the strict
        // kinship-age grammar and selected by the existing latest/revocation
        // and conflict checks above. No web observations, third-party claims,
        // or unsupported grammar may take this branch.
        if(ask.kind == "AGE" && familyAgeValue.matches(f.text) &&
            ranked.any { it.id == newest.first.id })return setOf(newest.first.id)
        return emptySet()
    }

    /** Exact AGE presentation after the existing ledger/scope has selected ONE genuine hit.
     * This is the age at the owner's statement, not a calculated current age or a new fact.
     * Strict whole-sentence matching rejects measurements, reported speech and temporal claims.
     * No new database, model state, revision rule or authority is introduced.
     */
    fun exactAgeReply(query: String, records: List<LearningExperience>, ranked: List<ContinuumMemoryHit>): String? {
        val ask = request(query) ?: return null
        if (ask.kind != "AGE") return null
        val id = select(query, records, ranked)?.singleOrNull() ?: return null
        val source = records.singleOrNull { it.id == id } ?: return null
        if (ranked.none { it.id == id && it.text == source.content && it.kind == "Bestätigte Nutzererinnerung" }) return null
        val literal = source.content.replaceFirst(correction, "").trim()
        val match = familyAgeValue.matchEntire(literal) ?: directAgeValue.matchEntire(literal) ?: return null
        if (key(match.groupValues[1]) != ask.holder) return null
        val value = match.groupValues[2]
        if (value.toIntOrNull() !in 0..130) return null
        return "Aus deiner bestätigten Altersangabe: $value Jahre\n" +
            "Beleg: Erinnerung $id (Stand der bestätigten Angabe, kein automatisch fortgeschriebenes Alter; " +
            "SHA-256 ${source.contentHash.take(16)}…)"
    }
}
