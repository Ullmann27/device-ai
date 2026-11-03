package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.TemporalRoleEvidenceLedger
import at.heinz.deviceai.intelligence.ewm.Ewm1Lexicon
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * M2 cognitive memory: ONE time-aware view over the canonical owner memory, not a second database.
 *
 * Long-term tier = the USER_FACT records of [FileLearningExperienceStore] (including revocation tombstones and
 * held, unconfirmed statements). Working tier = this session's own user turns, passed in by the caller and never
 * written anywhere. Every statement becomes a ClaimEvent (Argon-4 dossier 6.1: subject, predicate, value,
 * assertedAt, validFrom, validTo, supersedes, state) and is resolved on two time axes (dossier 6.2):
 * validity time (when was it true in the world?) and knowledge time (when did Continuum learn or unlearn it?).
 *
 * Rules (fail closed):
 * - only ASSERTED statements supply values; NEGATED ones only end or retract values; hypothetical, uncertain,
 *   planned, questioned and reported statements never answer (see [OwnerStatementModality]);
 * - an explicit change ("jetzt", "seit", "gezogen", "nicht mehr" …, the EWM update markers) ends the previous value;
 * - an explicit correction ("Korrektur:", "nicht X, sondern Y") retracts the wrong value for the same period;
 * - a later, unmarked, different value for a single-valued predicate is a CONFLICT and leads to a question,
 *   never to a silent overwrite (same principle as [TemporalRoleEvidenceLedger], which makes the final point lookup);
 * - an age is reported as of the statement, never extrapolated.
 * The bounded German grammar covers residence, work, age and "Mein X heißt Y"; anything else returns null.
 */
enum class MemoryTier { WORKING, LONG_TERM }
data class WorkingTurn(val text:String, val atMillis:Long) {
    init { require(text.length <= 4096 && atMillis >= 0) }
}
enum class TemporalAnswerKind { ANSWER, CLARIFY, NO_EVIDENCE, UNCONFIRMED_ONLY }
data class TemporalAnswer(val kind:TemporalAnswerKind, val text:String, val citedIds:List<String>, val tiers:Set<MemoryTier>)
data class TemporalValue(val value:String, val sourceIds:List<String>, val validFromMillis:Long?, val validToMillis:Long?, val tier:MemoryTier)
sealed class TemporalResolution {
    data class Found(val values:List<TemporalValue>):TemporalResolution()
    data class Conflict(val sourceIds:List<String>, val values:List<String>):TemporalResolution()
    data class UnconfirmedOnly(val statements:List<String>):TemporalResolution()
    object NoEvidence:TemporalResolution()
}

enum class ClaimRevision { NONE, CHANGE, CORRECTION }

data class ClaimEvent(
    val sourceId:String,
    val tier:MemoryTier,
    val sourceText:String,
    val subject:String,
    val predicate:String,
    val value:String?,
    val negatedValue:String?,
    val modality:StatementModality,
    val assertedAt:Long,
    val validFrom:Long?,
    val validTo:Long?,
    val validFromLabel:String?,
    val revision:ClaimRevision,
    val confirmed:Boolean,
    val retractedAt:Long?
)

class ContinuumTemporalMemory private constructor(
    private val claims:List<ClaimEvent>,
    private val held:List<HeldStatement>,
    val zone:ZoneId
) {
    internal data class HeldStatement(val text:String, val atMillis:Long, val modality:StatementModality, val tier:MemoryTier)
    private data class Interval(val value:String, var from:Long, var to:Long?, val sources:MutableList<String>,
                                val tier:MemoryTier, val fromLabel:String?, val assertedAt:Long, val sourceText:String)

    val claimCount:Int get() = claims.size
    fun claims():List<ClaimEvent> = claims

    /** Point lookup on both time axes; the last step is the existing [TemporalRoleEvidenceLedger]. */
    fun valueAt(subject:String, predicate:String, validAtMillis:Long, knownAtMillis:Long):TemporalResolution {
        val intervals = timeline(subject, predicate, knownAtMillis)
        val live = intervals.filter { it.from <= validAtMillis && (it.to == null || validAtMillis < it.to!!) }
        if (live.isEmpty()) return unconfirmedOr(subject, predicate, knownAtMillis)
        val facts = live.map { iv ->
            TemporalRoleEvidenceLedger.Fact(subject.take(80), predicate, iv.value.take(160), iv.from, iv.assertedAt,
                iv.sources.first(), 1, iv.sourceText, TemporalRoleEvidenceLedger.sha256(iv.sourceText),
                iv.to?.takeIf { it > iv.from },
                // An explicit change/correction already ended or retracted older values in timeline();
                // whatever is still live at the same instant is genuinely concurrent.
                emptySet())
        }
        return when (val r = TemporalRoleEvidenceLedger.lookup(facts, subject.take(80), predicate, validAtMillis, knownAtMillis)) {
            is TemporalRoleEvidenceLedger.Lookup.Found -> {
                val chosen = live.filter { it.value.equals(r.value, ignoreCase = true) }
                TemporalResolution.Found(listOf(merge(chosen)))
            }
            is TemporalRoleEvidenceLedger.Lookup.Conflict ->
                TemporalResolution.Conflict(live.flatMap { it.sources }.distinct(), live.map { it.value }.distinct())
            TemporalRoleEvidenceLedger.Lookup.NoEvidence -> unconfirmedOr(subject, predicate, knownAtMillis)
        }
    }

    /** All values valid at some time in [fromMillis, toMillis), chronologically; overlapping different values conflict. */
    fun during(subject:String, predicate:String, fromMillis:Long, toMillis:Long, knownAtMillis:Long):TemporalResolution {
        val live = timeline(subject, predicate, knownAtMillis)
            .filter { it.from < toMillis && (it.to == null || it.to!! > fromMillis) }.sortedBy { it.from }
        if (live.isEmpty()) return unconfirmedOr(subject, predicate, knownAtMillis)
        for (i in live.indices) for (j in i + 1 until live.size) {
            val a = live[i]; val b = live[j]
            val overlap = maxOf(a.from, b.from, fromMillis) < minOf(a.to ?: Long.MAX_VALUE, b.to ?: Long.MAX_VALUE, toMillis)
            if (overlap && !a.value.equals(b.value, true))
                return TemporalResolution.Conflict((a.sources + b.sources).distinct(), listOf(a.value, b.value))
        }
        return TemporalResolution.Found(live.groupBy { it.value.lowercase(Locale.ROOT) }.values.map(::merge).sortedBy { it.validFromMillis })
    }

    /** The earliest known value (after corrections), e.g. "Wo habe ich ursprünglich gewohnt?". */
    fun original(subject:String, predicate:String, knownAtMillis:Long):TemporalResolution {
        val all = timeline(subject, predicate, knownAtMillis)
        if (all.isEmpty()) return unconfirmedOr(subject, predicate, knownAtMillis)
        val first = all.minOf { it.from }
        val earliest = all.filter { it.from == first }
        return if (earliest.map { it.value.lowercase(Locale.ROOT) }.distinct().size > 1)
            TemporalResolution.Conflict(earliest.flatMap { it.sources }.distinct(), earliest.map { it.value })
        else TemporalResolution.Found(listOf(merge(earliest)))
    }

    private fun merge(items:List<Interval>):TemporalValue {
        val sorted = items.sortedBy { it.from }
        return TemporalValue(sorted.first().value, sorted.flatMap { it.sources }.distinct(), sorted.first().from,
            if (sorted.any { it.to == null }) null else sorted.maxOf { it.to!! },
            if (sorted.any { it.tier == MemoryTier.WORKING }) MemoryTier.WORKING else MemoryTier.LONG_TERM)
    }

    private fun unconfirmedOr(subject:String, predicate:String, knownAt:Long):TemporalResolution {
        val relevant = held.filter { it.atMillis <= knownAt && mentions(it.text, subject, predicate) }
        return if (relevant.isEmpty()) TemporalResolution.NoEvidence
        else TemporalResolution.UnconfirmedOnly(relevant.map { it.text })
    }

    private fun mentions(text:String, subject:String, predicate:String):Boolean {
        val low = text.lowercase(Locale.ROOT)
        val who = when {
            subject == OWNER -> Regex("\\b(?:ich|mein|meine|mir|mich)\\b").containsMatchIn(low)
            subject.startsWith("mein ") -> low.contains(subject.removePrefix("mein "))
            else -> Regex("\\b" + Regex.escape(subject) + "\\b").containsMatchIn(low)
        }
        val what = when (predicate) {
            RESIDENCE -> Regex("wohn|leb|zieh|gezogen|umzug").containsMatchIn(low)
            WORK -> low.contains("arbeit")
            AGE -> low.contains("alt") || Regex("\\b\\d{1,3}\\b").containsMatchIn(low)
            NAME -> low.contains("heiß")
            else -> false
        }
        return who && what
    }

    /** Replays the confirmed claims known at [knownAt] in knowledge-time order into validity intervals. */
    private fun timeline(subject:String, predicate:String, knownAt:Long):List<Interval> {
        val relevant = claims.filter { it.confirmed && it.subject == subject && it.predicate == predicate &&
            it.assertedAt <= knownAt && (it.retractedAt == null || it.retractedAt > knownAt) }
            .sortedWith(compareBy<ClaimEvent> { it.assertedAt }.thenBy { it.sourceId })
        require(relevant.size <= MAX_CLAIMS_PER_KEY) { "too many claims for one subject/predicate" }
        val out = mutableListOf<Interval>()
        for (c in relevant) {
            if (c.value == null) {
                val v = c.negatedValue ?: continue
                if (c.revision == ClaimRevision.CHANGE) {
                    val end = c.validFrom ?: c.assertedAt
                    out.filter { it.value.equals(v, true) && (it.to == null || it.to!! > end) }.forEach {
                        if (it.from < end) it.to = end else out.remove(it)
                    }
                } else out.removeAll { it.value.equals(v, true) }
                continue
            }
            val explicitStart = c.validFrom
            var from = explicitStart ?: c.assertedAt
            val to = c.validTo
            when (c.revision) {
                ClaimRevision.CHANGE -> {
                    out.filter { !it.value.equals(c.value, true) && (it.to == null || it.to!! > from) }.forEach {
                        if (it.from < from) it.to = from else out.remove(it)
                    }
                }
                ClaimRevision.CORRECTION -> {
                    val wrong = out.filter { iv -> !iv.value.equals(c.value, true) &&
                        (c.negatedValue == null || iv.value.equals(c.negatedValue, true)) &&
                        iv.from < (to ?: Long.MAX_VALUE) && (iv.to ?: Long.MAX_VALUE) > (explicitStart ?: Long.MIN_VALUE) }
                    if (explicitStart == null && wrong.isNotEmpty()) from = minOf(from, wrong.minOf { it.from })
                    out.removeAll(wrong)
                }
                ClaimRevision.NONE -> Unit
            }
            val same = out.firstOrNull { it.value.equals(c.value, true) && it.to == to &&
                (it.to == null || it.from <= from) }
            if (same != null) {
                same.sources += c.sourceId
                if (from < same.from) same.from = from
            } else if (to == null || to > from) {
                out += Interval(c.value, from, to, mutableListOf(c.sourceId), c.tier,
                    c.validFromLabel, c.assertedAt, c.sourceText)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- natural-language questions

    private data class Ask(val subject:String, val predicate:String, val mode:String, val year:Int? = null,
                           val display:String, val place:String? = null)

    /** Bounded German question grammar; null = not a temporal memory question (caller keeps its own path). */
    fun answer(question:String, nowMillis:Long):TemporalAnswer? {
        val ask = parseQuestion(question) ?: return null
        val resolution = when (ask.mode) {
            "current", "since", "age", "name" -> valueAt(ask.subject, ask.predicate, nowMillis, nowMillis)
            "year" -> {
                val y = ask.year!!
                during(ask.subject, ask.predicate, startOfYear(y), startOfYear(y + 1), nowMillis)
            }
            "original" -> original(ask.subject, ask.predicate, nowMillis)
            else -> return null
        }
        return render(ask, resolution)
    }

    private fun render(ask:Ask, r:TemporalResolution):TemporalAnswer = when (r) {
        TemporalResolution.NoEvidence -> TemporalAnswer(TemporalAnswerKind.NO_EVIDENCE,
            "Dazu habe ich keine bestätigte Angabe" + (ask.year?.let { " für $it" } ?: "") + ".", emptyList(), emptySet())
        is TemporalResolution.UnconfirmedOnly -> TemporalAnswer(TemporalAnswerKind.UNCONFIRMED_ONLY,
            "Dazu habe ich keine bestätigte Angabe. Du hast dazu nur " +
                (if (r.statements.size == 1) "eine Aussage" else "${r.statements.size} Aussagen") +
                " als Möglichkeit, Plan, Frage oder Bericht gemacht; das ist nicht bestätigt und deshalb kein Fakt.",
            emptyList(), emptySet())
        is TemporalResolution.Conflict -> {
            val sources = r.sourceIds
            TemporalAnswer(TemporalAnswerKind.CLARIFY,
                "Dazu habe ich widersprüchliche Angaben ohne ausdrückliche Änderung: " + r.values.joinToString(" und ") +
                    ". Was davon stimmt? Sag z. B. „Merke dir: Ich wohne jetzt in …“ oder „Korrektur: …“.",
                sources, tiersOf(sources))
        }
        is TemporalResolution.Found -> {
            val values = r.values
            val ids = values.flatMap { it.sourceIds }.distinct()
            val tiers = tiersOf(ids)
            val v = values.first()
            val who = ask.display
            val owner = ask.subject == OWNER
            val main = when (ask.mode) {
                "current" -> when (ask.predicate) {
                    RESIDENCE -> if (owner) "Du wohnst in ${v.value}." else "$who wohnt in ${v.value}."
                    WORK -> if (owner) "Du arbeitest ${prep(v.value)}." else "$who arbeitet ${prep(v.value)}."
                    else -> v.value
                }
                "since" -> {
                    val label = claimFor(v)?.validFromLabel
                    val place = v.value
                    if (label != null) (if (owner) "Du wohnst seit $label in $place." else "$who wohnt seit $label in $place.")
                    else "Seit wann genau, hast du nicht gesagt; bekannt ist $place seit deiner Angabe vom ${date(v.validFromMillis!!)}."
                }
                "year" -> {
                    val parts = values.map { tv ->
                        tv.value + range(tv, ask.year!!)
                    }
                    val verb = if (ask.predicate == WORK) (if (owner) "hast du gearbeitet" else "hat $who gearbeitet")
                        else (if (owner) "wohntest du" else "wohnte $who")
                    "${ask.year} $verb laut deinen Angaben " +
                        (if (ask.predicate == WORK) parts.joinToString(" und ") { prep(it) } else "in " + parts.joinToString(" und ")) + "."
                }
                "original" -> if (ask.predicate == WORK) "Ursprünglich (früheste Angabe): ${prep(v.value)}."
                    else (if (owner) "Ursprünglich (früheste Angabe) hast du in ${v.value} gewohnt." else
                        "Ursprünglich (früheste Angabe) hat $who in ${v.value} gewohnt.")
                "age" -> {
                    val c = claimFor(v)
                    "$who war laut deiner Angabe vom ${date(c?.assertedAt ?: v.validFromMillis!!)} ${v.value} Jahre alt " +
                        "(Stand der Angabe, nicht automatisch fortgeschrieben)."
                }
                "name" -> "${ask.display.replaceFirstChar { it.uppercase() }} heißt ${v.value}."
                else -> v.value
            }
            val time = if (ask.mode in setOf("current", "original") && v.validFromMillis != null)
                "\nZeitbezug: gültig " + (claimFor(v)?.validFromLabel?.let { "seit $it" } ?: "ab deiner Angabe vom ${date(v.validFromMillis)}") +
                    (v.validToMillis?.let { " bis ${date(it)}" } ?: "") + "." else ""
            val tierText = if (MemoryTier.WORKING in tiers && MemoryTier.LONG_TERM !in tiers)
                "nur in diesem Gespräch gesagt, nicht dauerhaft gespeichert"
            else if (MemoryTier.WORKING in tiers) "bestätigte Erinnerung und Angabe aus diesem Gespräch"
            else "bestätigte Langzeiterinnerung"
            val proof = ids.take(4).joinToString("; ") { id ->
                val c = claims.first { it.sourceId == id }
                id + " (gesagt am " + date(c.assertedAt) + ", SHA-256 " + sha(c.sourceText).take(12) + "…)"
            }
            TemporalAnswer(TemporalAnswerKind.ANSWER, "$main$time\nBeleg: $proof · $tierText", ids, tiers)
        }
    }

    private fun prep(v:String):String =
        if (Regex("(?iu)^(?:bei|als|in|für|im)\\s").containsMatchIn(v)) v else "bei $v"

    private fun range(tv:TemporalValue, year:Int):String {
        val from = tv.validFromMillis; val to = tv.validToMillis
        val inYearStart = from != null && from > startOfYear(year)
        val inYearEnd = to != null && to < startOfYear(year + 1)
        return when {
            inYearStart && inYearEnd -> " (${date(from!!)}–${date(to!!)})"
            inYearStart -> " (ab ${date(from!!)})"
            inYearEnd -> " (bis ${date(to!!)})"
            else -> ""
        }
    }

    private fun claimFor(v:TemporalValue) = v.sourceIds.mapNotNull { id -> claims.firstOrNull { it.sourceId == id } }
        .minByOrNull { it.validFrom ?: it.assertedAt }

    private fun tiersOf(ids:List<String>) = ids.mapNotNull { id -> claims.firstOrNull { it.sourceId == id }?.tier }.toSet()

    private fun startOfYear(y:Int) = LocalDate.of(y, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun date(ms:Long) = DATE.format(Instant.ofEpochMilli(ms).atZone(zone))

    private fun parseQuestion(question:String):Ask? {
        val q = Normalizer.normalize(question.trim(), Normalizer.Form.NFC).trimEnd('?', '.', '!', ' ').take(160)
        val low = q.lowercase(Locale.ROOT)
        val now = "(?:\\s+(?:jetzt|aktuell|derzeit|heute|momentan|gerade))?"
        Regex("^wo\\s+(?:wohne|lebe)\\s+ich$now$").find(low)?.let { return Ask(OWNER, RESIDENCE, "current", display = "du") }
        Regex("^(?:wo|bei\\s+wem|für\\s+wen|bei\\s+welcher\\s+firma)\\s+arbeite\\s+ich$now$").find(low)
            ?.let { return Ask(OWNER, WORK, "current", display = "du") }
        Regex("^wie\\s+alt\\s+bin\\s+ich$").find(low)?.let { return Ask(OWNER, AGE, "age", display = "Du") }
        Regex("^wie\\s+heißt\\s+(mein|meine)\\s+(\\p{L}{2,40})$").find(low)?.let {
            return Ask("mein " + it.groupValues[2], NAME, "name", display = "dein" + (if (it.groupValues[1] == "meine") "e " else " ") +
                q.split(Regex("\\s+")).last())
        }
        val year = Regex("^wo\\s+(wohnte|lebte|arbeitete)\\s+(ich|$NAME_RE)\\s+(?:im\\s+jahr\\s+)?((?:19|20)\\d{2})$")
            .find(q.replaceFirst(Regex("^Wo"), "wo"))
        if (year != null) return named(year.groupValues[2], if (year.groupValues[1] == "arbeitete") WORK else RESIDENCE,
            "year", year.groupValues[3].toInt())
        Regex("^wo\\s+(habe|hat)\\s+(ich|$NAME_RE)\\s+(?:im\\s+jahr\\s+)?((?:19|20)\\d{2})\\s+(gewohnt|gelebt|gearbeitet)$")
            .find(q.replaceFirst(Regex("^Wo"), "wo"))?.let {
                return named(it.groupValues[2], if (it.groupValues[4] == "gearbeitet") WORK else RESIDENCE, "year", it.groupValues[3].toInt())
            }
        Regex("^wo\\s+(?:habe|hat)\\s+(ich|$NAME_RE)\\s+(?:ursprünglich|zuerst|anfangs|als\\s+erstes|zu\\s+beginn)\\s+(gewohnt|gelebt|gearbeitet)$")
            .find(q.replaceFirst(Regex("^Wo"), "wo"))?.let {
                return named(it.groupValues[1], if (it.groupValues[2] == "gearbeitet") WORK else RESIDENCE, "original")
            }
        Regex("^seit\\s+wann\\s+(?:wohne|lebe)\\s+ich\\s+in\\s+(\\p{Lu}[\\p{L}-]+)$").find(q.replaceFirst(Regex("^Seit wann"), "seit wann"))
            ?.let { return Ask(OWNER, RESIDENCE, "since", display = "du", place = it.groupValues[1]) }
        Regex("^wo\\s+(wohnt|lebt|arbeitet)\\s+($NAME_RE)$now$").find(q.replaceFirst(Regex("^Wo"), "wo"))?.let {
            return named(it.groupValues[2], if (it.groupValues[1] == "arbeitet") WORK else RESIDENCE, "current")
        }
        Regex("^wie\\s+alt\\s+ist\\s+($NAME_RE)$").find(q.replaceFirst(Regex("^Wie"), "wie"))?.let {
            return named(it.groupValues[1], AGE, "age")
        }
        return null
    }

    private fun named(who:String, predicate:String, mode:String, year:Int? = null):Ask? {
        if (who.lowercase(Locale.ROOT) == "ich") return Ask(OWNER, predicate, mode, year, "du")
        val key = who.trim().lowercase(Locale.ROOT)
        if (key in NOT_NAMES) return null
        return Ask(key, predicate, mode, year, who.trim())
    }

    companion object {
        val VIENNA:ZoneId = ZoneId.of("Europe/Vienna")
        const val OWNER = "ich"
        const val RESIDENCE = "wohnort"
        const val WORK = "arbeit"
        const val AGE = "alter"
        const val NAME = "name"
        private const val MAX_CLAIMS_PER_KEY = 256
        private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
        private const val NAME_RE = "\\p{Lu}[\\p{L}'’-]{1,39}(?:\\s+\\p{Lu}[\\p{L}'’-]{1,39})?"
        private val NOT_NAMES = setOf("mein", "meine", "dein", "deine", "er", "sie", "es", "man", "wir", "ihr")

        fun fromLedger(records:List<LearningExperience>, working:List<WorkingTurn> = emptyList(), zone:ZoneId = VIENNA):ContinuumTemporalMemory {
            require(records.size <= 4096 && working.size <= 64)
            val claims = mutableListOf<ClaimEvent>()
            val held = mutableListOf<HeldStatement>()
            for (r in records) {
                if (r.type != LearningExperienceType.USER_FACT) continue
                val modality = OwnerStatementModality.classify(r.content)
                val heldByLedger = r.annotation?.startsWith(OwnerStatementModality.HELD_PREFIX) == true ||
                    r.status == LearningStatus.OBSERVED || r.status == LearningStatus.REJECTED
                val confirmable = OwnerStatementModality.mayBecomeConfirmedFact(modality) && !heldByLedger
                if (!confirmable) {
                    if (r.status != LearningStatus.REVOKED && r.status != LearningStatus.REJECTED)
                        held += HeldStatement(r.content, r.createdAt, modality, MemoryTier.LONG_TERM)
                    continue
                }
                val retracted = if (r.status == LearningStatus.REVOKED) r.updatedAt else null
                ClaimGrammar.extract(r.content, r.id, MemoryTier.LONG_TERM, r.createdAt, modality, zone)
                    ?.let { claims += it.copy(confirmed = true, retractedAt = retracted) }
            }
            for (turn in working) {
                val modality = OwnerStatementModality.classify(turn.text)
                if (!OwnerStatementModality.mayBecomeConfirmedFact(modality)) {
                    held += HeldStatement(turn.text, turn.atMillis, modality, MemoryTier.WORKING)
                    continue
                }
                val id = "wm-" + sha(turn.text + "\u0000" + turn.atMillis).take(20)
                ClaimGrammar.extract(turn.text, id, MemoryTier.WORKING, turn.atMillis, modality, zone)
                    ?.let { claims += it.copy(confirmed = true) }
            }
            return ContinuumTemporalMemory(claims, held, zone)
        }

        internal fun sha(text:String):String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** Bounded German statement grammar → ClaimEvent. Unsupported sentences return null (no guessing). */
internal object ClaimGrammar {
    private const val PLACE = "(\\p{Lu}[\\p{L}-]{1,39}(?:\\s+(?:am|an\\s+der|im|ob\\s+der)\\s+\\p{Lu}[\\p{L}-]{1,39})?)"
    private const val NAME_RE = "(\\p{Lu}[\\p{L}'’-]{1,39}(?:\\s+\\p{Lu}[\\p{L}'’-]{1,39})?)"
    private val correctionPrefix = Regex("(?iu)^\\s*(?:korrektur|berichtigung)\\s*:\\s*")
    private val leadingAdverb = Regex("(?iu)^(?:jetzt|inzwischen|mittlerweile|nun|neuerdings|derzeit|aktuell|heute|früher|damals|ehemals)\\s+")
    private val months = mapOf("jänner" to 1, "januar" to 1, "jänner" to 1, "februar" to 2, "feber" to 2, "märz" to 3,
        "april" to 4, "mai" to 5, "juni" to 6, "juli" to 7, "august" to 8, "september" to 9, "oktober" to 10,
        "november" to 11, "dezember" to 12)
    private const val MONTH = "(?:(jänner|januar|februar|feber|märz|april|mai|juni|juli|august|september|oktober|november|dezember)\\s+)?"
    private val NOT_SUBJECTS = setOf("ich", "mein", "meine", "meinen", "meinem", "meiner", "wenn", "falls", "vielleicht",
        "korrektur", "jetzt", "früher", "damals", "seit", "heute", "nächstes", "dein", "deine", "er", "sie", "es", "wir")

    fun extract(text:String, sourceId:String, tier:MemoryTier, assertedAt:Long, modality:StatementModality, zone:ZoneId):ClaimEvent? {
        val original = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
        if (original.length > 320 || Regex("[.!?;]\\s+\\S").containsMatchIn(original.trimEnd('.', '!', ' '))) return null
        val corrected = correctionPrefix.containsMatchIn(original)
        val s0 = original.replaceFirst(correctionPrefix, "").trimEnd('.', '!', ' ')
        val s = s0.replaceFirst(leadingAdverb, "").let { it.replaceFirstChar { c -> c.uppercase() } }
        val low = s0.lowercase(Locale.ROOT)
        val words = low.split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotEmpty).toSet()
        val negated = modality == StatementModality.NEGATED
        val sondern = Regex("(?iu)\\bsondern\\s+(?:in|bei|als|im)?\\s*(\\p{Lu}[\\p{L}-]{1,39}(?:\\s+\\p{Lu}[\\p{L}-]{1,39})?)").find(s)
        val isCorrection = corrected || sondern != null
        val change = !isCorrection && (Ewm1Lexicon.UPDATE_MARKERS.any { m -> if (' ' in m) low.contains(m) else m in words })
        val past = words.any { it in setOf("wohnte", "lebte", "arbeitete", "gewohnt", "gelebt", "gearbeitet", "früher", "damals", "ehemals") }
        val revision = when { isCorrection -> ClaimRevision.CORRECTION; change -> ClaimRevision.CHANGE; else -> ClaimRevision.NONE }
        val time = parseTime(low, past, assertedAt, zone)

        fun claim(subject:String, predicate:String, rawValue:String?):ClaimEvent? {
            var value = rawValue?.let(::cleanValue)
            var negatedValue:String? = null
            if (sondern != null) {
                negatedValue = value
                value = cleanValue(sondern.groupValues[1])
            } else if (negated) {
                negatedValue = value; value = null
            }
            if (value == null && negatedValue == null) return null
            if (value != null && (value.isEmpty() || value.length > 160)) return null
            // A past statement without a stated end ("früher wohnte ich in Wien") ended at the latest when said.
            val validTo = time.second ?: if (past && time.first == null && !negated) assertedAt else null
            val validFrom = time.first ?: if (past && !negated) 0L else null
            return ClaimEvent(sourceId, tier, original, subject, predicate, value, negatedValue, modality, assertedAt,
                validFrom, validTo, time.third, if (negated && sondern == null && low.contains("nicht mehr"))
                    ClaimRevision.CHANGE else if (negated && sondern == null) ClaimRevision.CORRECTION else revision,
                confirmed = false, retractedAt = null)
        }

        // Residence: owner
        Regex("(?iu)^Ich\\s+(?:wohne|lebe|wohnte|lebte|habe)\\b.*?\\b(?:in|im)\\s+$PLACE").find(s)?.let {
            if (Regex("(?iu)^Ich\\s+habe\\b").containsMatchIn(s) && !Regex("(?iu)\\b(gewohnt|gelebt)\\b").containsMatchIn(s)) return null
            return claim(ContinuumTemporalMemory.OWNER, ContinuumTemporalMemory.RESIDENCE, placeAfterIn(s) ?: it.groupValues[1])
        }
        Regex("(?iu)^Ich\\s+bin\\s+(?:.*?\\s)?nach\\s+$PLACE\\s+(?:gezogen|umgezogen|übersiedelt|gezügelt)").find(s)?.let {
            return claim(ContinuumTemporalMemory.OWNER, ContinuumTemporalMemory.RESIDENCE, it.groupValues[1])
                ?.copy(revision = if (isCorrection) ClaimRevision.CORRECTION else ClaimRevision.CHANGE)
        }
        // Work: owner
        Regex("(?iu)^Ich\\s+(?:arbeite|arbeitete)\\s+(?:.*?\\s)?(bei|als|für)\\s+(.+)$").find(s)?.let {
            return claim(ContinuumTemporalMemory.OWNER, ContinuumTemporalMemory.WORK, workValue(it.groupValues[2]))
        }
        // Age: owner
        Regex("(?iu)^Ich\\s+bin\\s+(?:nicht\\s+)?(\\d{1,3})(?:\\s+Jahre(?:\\s+alt)?)?$").find(s)?.let {
            return claim(ContinuumTemporalMemory.OWNER, ContinuumTemporalMemory.AGE, it.groupValues[1])
        }
        // "Mein Hund heißt Bello."
        Regex("(?u)^(?:Mein|Meine)\\s+(\\p{L}{2,40})\\s+heißt\\s+(?:nicht\\s+)?(?:„|\")?(\\p{Lu}[\\p{L}-]{0,39}(?:\\s+\\p{Lu}[\\p{L}-]{0,39})?)(?:“|\")?$").find(s)?.let {
            return claim("mein " + it.groupValues[1].lowercase(Locale.ROOT), ContinuumTemporalMemory.NAME, it.groupValues[2])
        }
        // Family role + name + age: "Meine Tochter Klara ist 29."
        Regex("(?u)^(?:Mein|Meine|Unser|Unsere)\\s+(?:Tochter|Sohn|Schwester|Bruder|Enkelin|Enkel|Frau|Mann|Partner|Partnerin|Mutter|Vater|Oma|Opa)\\s+$NAME_RE\\s+ist\\s+(?:nicht\\s+)?(\\d{1,3})(?:\\s+Jahre(?:\\s+alt)?)?$").find(s)?.let {
            return claim(it.groupValues[1].lowercase(Locale.ROOT), ContinuumTemporalMemory.AGE, it.groupValues[2])
        }
        // Named person
        val person = Regex("(?u)^$NAME_RE\\s+(wohnt|lebt|wohnte|lebte|arbeitet|arbeitete|ist)\\b(.*)$").find(s) ?: return null
        val name = person.groupValues[1]
        if (name.split(Regex("\\s+")).first().lowercase(Locale.ROOT) in NOT_SUBJECTS) return null
        val key = name.lowercase(Locale.ROOT)
        val verb = person.groupValues[2]
        val rest = person.groupValues[3]
        return when (verb) {
            "wohnt", "lebt", "wohnte", "lebte" -> Regex("(?u)\\b(?:in|im)\\s+$PLACE").find(rest)
                ?.let { claim(key, ContinuumTemporalMemory.RESIDENCE, placeAfterIn(rest) ?: it.groupValues[1]) }
            "arbeitet", "arbeitete" -> Regex("(?iu)\\b(bei|als|für)\\s+(.+)$").find(rest)
                ?.let { claim(key, ContinuumTemporalMemory.WORK, workValue(it.groupValues[2])) }
            else -> {
                Regex("(?iu)^\\s+(?:.*?\\s)?nach\\s+$PLACE\\s+(?:gezogen|umgezogen|übersiedelt)$").find(rest)?.let {
                    return claim(key, ContinuumTemporalMemory.RESIDENCE, it.groupValues[1])
                        ?.copy(revision = if (isCorrection) ClaimRevision.CORRECTION else ClaimRevision.CHANGE)
                }
                Regex("(?iu)^\\s+(?:nicht\\s+)?(\\d{1,3})(?:\\s+Jahre(?:\\s+alt)?)?$").find(rest)
                    ?.let { claim(key, ContinuumTemporalMemory.AGE, it.groupValues[1]) }
            }
        }
    }

    /** The place after the LAST "in" that is not a time phrase ("seit März 2019 in Linz"). */
    private fun placeAfterIn(s:String):String? =
        Regex("(?u)\\b(?:in|im)\\s+$PLACE").findAll(s).map { it.groupValues[1] }
            .filterNot { it.lowercase(Locale.ROOT) in months.keys }.firstOrNull()

    private fun workValue(raw:String):String =
        raw.split(Regex("(?iu)\\s+(?:seit|von|bis|ab|im\\s+jahr|sondern)\\b|,")).first().trim()
            .replaceFirst(Regex("(?iu)^(?:der|die|dem|den|einer|einem)\\s+"), "").trim()

    private fun cleanValue(v:String):String = v.trim().trimEnd('.', ',', '!', ' ').take(160)

    /** (validFrom, validTo, label of validFrom) from explicit German time phrases; nulls when not stated. */
    private fun parseTime(low:String, past:Boolean, assertedAt:Long, zone:ZoneId):Triple<Long?, Long?, String?> {
        fun start(month:String?, year:Int) = LocalDate.of(year, months[month] ?: 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun endOf(month:String?, year:Int) = (if (month != null) LocalDate.of(year, months.getValue(month), 1).plusMonths(1)
            else LocalDate.of(year + 1, 1, 1)).atStartOfDay(zone).toInstant().toEpochMilli()
        fun label(month:String?, year:Int) = (month?.replaceFirstChar { it.uppercase() }?.plus(" ") ?: "") + year
        Regex("\\bvon\\s+$MONTH((?:19|20)\\d{2})\\s+bis\\s+$MONTH((?:19|20)\\d{2})\\b").find(low)?.let { m ->
            val (m1, y1, m2, y2) = m.destructured
            val from = start(m1.ifEmpty { null }, y1.toInt()); val to = endOf(m2.ifEmpty { null }, y2.toInt())
            return if (to > from) Triple(from, to, label(m1.ifEmpty { null }, y1.toInt())) else Triple(null, null, null)
        }
        val since = Regex("\\b(?:seit|ab)\\s+$MONTH((?:19|20)\\d{2})\\b").find(low)
        val until = Regex("\\bbis\\s+$MONTH((?:19|20)\\d{2})\\b").find(low)
        if (since != null || until != null) {
            val from = since?.let { start(it.groupValues[1].ifEmpty { null }, it.groupValues[2].toInt()) }
            val to = until?.let { endOf(it.groupValues[1].ifEmpty { null }, it.groupValues[2].toInt()) }
            if (from != null && to != null && to <= from) return Triple(null, null, null)
            return Triple(from, to, since?.let { label(it.groupValues[1].ifEmpty { null }, it.groupValues[2].toInt()) })
        }
        if (past) Regex("\\b(?:im\\s+jahr\\s+)?((?:19|20)\\d{2})\\b").find(low)?.let {
            val y = it.groupValues[1].toInt()
            return Triple(start(null, y), endOf(null, y), y.toString())
        }
        return Triple(null, null, null)
    }
}
