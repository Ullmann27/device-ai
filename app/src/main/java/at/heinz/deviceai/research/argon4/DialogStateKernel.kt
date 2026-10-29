package at.heinz.deviceai.research.argon4

import at.heinz.deviceai.intelligence.ewm.Ewm1Lexicon
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * ARGON4 F1 · compact, verifiable conversation state (research prototype; integrated SHADOW only).
 *
 * Replaces nothing: LocalConversationContext keeps its four bounded turn pairs for the model, EWM2 keeps owner facts.
 * This kernel adds what neither holds across more than four turns: who/what "er/sie/es/das/dort" can mean, the
 * corrected referent ("nein, ich meinte Rita"), the active time window ("gestern", "letzte Woche") and open questions.
 * It never stores a fact: entities are mentions, not truths, and the state dies with the session.
 */
class DialogStateKernel(
    val sessionId: String,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val maxEntities: Int = 12
) {
    enum class Kind { PERSON, THING, PLACE }
    enum class Decision { PASS, RESOLVED, CLARIFY }

    data class EntityRef(val key: String, val label: String, val kind: Kind, val gender: Char?, val lastTurn: Int, val mentions: Int)
    data class TemporalScope(val from: LocalDate, val to: LocalDate, val expression: String)
    data class Binding(val word: String, val entity: String, val via: String)
    data class Resolution(
        val original: String,
        val rewritten: String,
        val decision: Decision,
        val bindings: List<Binding>,
        val temporal: TemporalScope?,
        val correction: Boolean,
        val reason: String
    )

    private var turn = 0
    private val entities = LinkedHashMap<String, EntityRef>()
    private var temporal: TemporalScope? = null
    private var lastQuestion: String? = null
    private var lastResolution: Resolution? = null
    private val rejected = HashSet<String>()   // "word|entity" pairs the owner corrected away
    private val openQuestions = ArrayDeque<String>()

    init { require(sessionId.isNotBlank() && maxEntities in 2..64) }

    /** One user turn: resolve references and time against this session only, then remember its mentions. */
    @Synchronized fun observeUser(input: String): Resolution {
        val text = input.trim().take(512)
        require(text.isNotEmpty())
        turn++
        if (TOPIC_SWITCH.containsMatchIn(text)) {
            entities.clear(); temporal = null; lastQuestion = null; lastResolution = null; openQuestions.clear()
            return Resolution(text, text, Decision.PASS, emptyList(), null, false, "TOPIC_SWITCH")
        }
        correction(text)?.let { return it }
        val time = parseTime(text)?.also { temporal = it } ?: temporal.takeIf { FOLLOW_UP.containsMatchIn(text) }
        val tokens = words(text)
        val ms = mentions(tokens, text.endsWith("?"))
        var applied = 0
        val bindings = ArrayList<Binding>()
        var rewritten = text
        for ((index, w) in tokens.withIndex()) {
            val lower = Ewm1Lexicon.lower(w)
            val wanted = PRONOUNS[lower] ?: continue
            while (applied < ms.size && ms[applied].index < index) touch(ms[applied++], countMention = true)
            if (lower in setOf("das", "dies") && !demonstrativeUse(tokens, index)) continue
            if (lower == "sie" && index > 0 && w == "Sie") continue          // polite "Sie" addresses the assistant
            if (lower in setOf("ihr", "ihre", "ihren", "ihrem", "ihrer") && index == 0) continue
            // "Was macht sie beruflich?", "Wo wohnt er?": a person predicate prefers people over "die Antwort"
            val personPredicate = tokens.any { t -> Ewm1Lexicon.lower(t).let { it in Ewm1Lexicon.PERSON_VERBS || it in PERSON_WORDS } }
            val pool = candidates(lower, wanted).let { all ->
                if (personPredicate && all.any { it.kind == Kind.PERSON }) all.filter { it.kind == Kind.PERSON } else all }
            when {
                // "Wie spät ist es?", "Es regnet.", "Wann war das?": impersonal/event readings need no referent
                pool.isEmpty() && lower in setOf("es", "das", "dies") -> continue
                pool.isEmpty() -> {
                    val r = Resolution(text, text, Decision.CLARIFY, bindings, time, false, "NO_REFERENT:$lower")
                    remember(text, ms.drop(applied), r); return r
                }
                pool.size > 1 && score(pool[0], lower) - score(pool[1], lower) < MARGIN -> {
                    val r = Resolution(text, text, Decision.CLARIFY, bindings, time, false,
                        "AMBIGUOUS:$lower:" + pool.take(2).joinToString("/") { it.label })
                    remember(text, ms.drop(applied), r); return r
                }
                else -> {
                    val e = pool[0]
                    bindings += Binding(lower, e.key, if (pool.size == 1) "einziger passender Bezug" else "jüngster passender Bezug")
                    rewritten = replaceWord(rewritten, w, e.label)
                }
            }
        }
        val r = Resolution(text, rewritten, if (bindings.isEmpty()) Decision.PASS else Decision.RESOLVED, bindings, time, false,
            if (bindings.isEmpty()) "NO_REFERENCE" else "RESOLVED")
        remember(text, ms.drop(applied), r)
        return r
    }

    /** The answer's own mentions count as discourse, never as facts; an answered question is closed. */
    @Synchronized fun observeAnswer(answer: String, answered: Boolean = true) {
        if (answer.isBlank()) return
        mentions(words(answer.take(1_000))).forEach { touch(it, countMention = false) }
        if (answered) openQuestions.removeLastOrNull() else lastQuestion?.let { if (it !in openQuestions) openQuestions.addLast(it) }
        while (openQuestions.size > 4) openQuestions.removeFirst()
    }

    @Synchronized fun entities(): List<EntityRef> = entities.values.sortedByDescending { score(it) }

    @Synchronized fun temporal(): TemporalScope? = temporal

    /** Compact model context (data, not instructions); bounded and stable in order. */
    @Synchronized fun render(maxChars: Int = 600): String = buildString {
        append("GESPRÄCHSZUSTAND (nur diese Sitzung, keine Fakten): ")
        entities().take(6).joinToString("; ") { e ->
            e.label + "(" + e.kind.name.lowercase() + (e.gender?.let { ",$it" } ?: "") + ",Zug " + e.lastTurn + ")"
        }.takeIf { it.isNotEmpty() }?.let { append("Bezüge: ").append(it).append(". ") }
        temporal?.let { append("Zeitraum: ").append(it.from).append("…").append(it.to).append(" (").append(it.expression).append("). ") }
        if (rejected.isNotEmpty()) append("Korrigiert: ").append(rejected.take(3).joinToString(", ") { it.replace('|', '≠') }).append(". ")
        if (openQuestions.isNotEmpty()) append("Offen: ").append(openQuestions.last().take(120)).append(". ")
    }.take(maxChars)

    // ---- internals -------------------------------------------------------------------------------------------

    private fun correction(text: String): Resolution? {
        val m = CORRECTION.matchEntire(text) ?: return null
        val target = m.groupValues[1].trim().trimEnd('.', '!', '?').replace(Regex("(?iu)^(den|dem|der|die|das|meine[nmr]?|mein)\\s+"), "")
        val previous = lastResolution ?: return Resolution(text, text, Decision.CLARIFY, emptyList(), temporal, true, "CORRECTION_WITHOUT_QUESTION")
        val question = lastQuestion ?: return Resolution(text, text, Decision.CLARIFY, emptyList(), temporal, true, "CORRECTION_WITHOUT_QUESTION")
        val label = target.split(Regex("\\s+")).last()
        if (!label.first().isUpperCase()) return Resolution(text, text, Decision.CLARIFY, emptyList(), temporal, true, "CORRECTION_TARGET_UNCLEAR")
        val key = Ewm1Lexicon.lower(label)
        touch(Mention(key, label, entities[key]?.kind ?: Kind.PERSON, entities[key]?.gender), countMention = true)
        // The wrongly bound referent is excluded for this word from now on; the question is asked again with the target.
        val wrong = previous.bindings.lastOrNull()
        var rewritten = question
        val bindings = ArrayList<Binding>()
        if (wrong != null) {
            rejected += wrong.word + "|" + wrong.entity
            rewritten = replaceWord(previous.rewritten, entities[wrong.entity]?.label ?: wrong.entity, label)
            bindings += Binding(wrong.word, key, "Korrektur durch den Besitzer")
        } else {
            rewritten = question.trimEnd('?', '.', '!') + " (gemeint: $label)?"
            bindings += Binding("?", key, "Korrektur durch den Besitzer")
        }
        val r = Resolution(text, rewritten, Decision.RESOLVED, bindings, previous.temporal, true, "CORRECTED")
        lastResolution = r
        return r
    }

    private fun candidates(word: String, wanted: Set<Kind>): List<EntityRef> {
        val gender: Set<Char>? = when (word) {
            // "er/ihn/ihm" take a masculine antecedent ("der Ofen"), a neuter one takes "es"; possessives allow both
            "er", "ihn", "ihm" -> setOf('m')
            "sein", "seine", "seinen", "seinem", "seiner", "dessen" -> setOf('m', 'n')
            "sie", "ihr", "ihre", "ihren", "ihrem", "ihrer", "deren" -> setOf('f')
            "es" -> setOf('n')
            else -> null
        }
        return entities.values.filter { e ->
            e.kind in wanted && "$word|${e.key}" !in rejected &&
                (gender == null || e.gender == null || e.gender in gender) &&
                // "er/sie" for a thing needs the article's gender; an unknown-gender thing is not a person referent
                !(e.kind == Kind.THING && gender != null && e.gender == null) &&
                turn - e.lastTurn <= WINDOW
        }.sortedByDescending { score(it, word) }
    }

    private fun score(e: EntityRef): Double = 1.0 / (1 + turn - e.lastTurn) + 0.05 * minOf(e.mentions, 4)

    /** A referent whose gender is known from a role or article outranks one whose gender is only "not excluded". */
    private fun score(e: EntityRef, word: String): Double = score(e) + if (e.gender != null && word in GENDERED) 0.5 else 0.0

    private data class Mention(val key: String, val label: String, val kind: Kind, val gender: Char?, val index: Int = -1)

    private fun mentions(tokens: List<String>, questionText: Boolean = false): List<Mention> {
        val out = ArrayList<Mention>()
        for ((i, w) in tokens.withIndex()) {
            val lower = Ewm1Lexicon.lower(w)
            val prev = tokens.getOrNull(i - 1)?.let(Ewm1Lexicon::lower)
            val roleGender = roleGender(lower)
            if (roleGender != null && prev != null && prev.startsWith("mein") || roleGender != null && prev != null && prev.startsWith("unser")) {
                val name = tokens.getOrNull(i + 1)
                if (name != null && name.first().isUpperCase() && Ewm1Lexicon.lower(name) !in Ewm1Lexicon.FUNCTION) continue
                out += Mention(lower, w, Kind.PERSON, roleGender, i); continue
            }
            if (!w.first().isUpperCase() || lower in Ewm1Lexicon.FUNCTION || lower in NON_REFERENTS || lower in PRONOUNS) continue
            if (i == 0) {
                // "Anna wohnt in Linz.": a sentence-initial capital followed by a lowercase verb is taken as a name mention
                val known = entities[lower]
                if (known != null) out += Mention(lower, w, known.kind, known.gender, i)
                // ... but not the verb of a verb-first question ("Funktioniert er jetzt?", "Wohnt sie dort?")
                else if (tokens.getOrNull(1)?.first()?.isLowerCase() == true && !questionText &&
                    Ewm1Lexicon.lower(tokens[1]) !in PRONOUNS && Ewm1Lexicon.lower(tokens[1]) !in OBJECT_PRONOUNS &&
                    Ewm1Lexicon.lower(tokens[1]) !in Ewm1Lexicon.FUNCTION && !nounShaped(lower))
                    out += Mention(lower, w, Kind.PERSON, null, i)
                continue
            }
            val before = tokens.getOrNull(i - 1)
            val roleBefore = before?.let { roleGender(Ewm1Lexicon.lower(it)) }
            val prev2 = tokens.getOrNull(i - 2)?.let(Ewm1Lexicon::lower)
            when {
                roleBefore != null -> out += Mention(lower, w, Kind.PERSON, roleBefore, i)
                entities[lower] != null -> out += Mention(lower, w, entities.getValue(lower).kind, entities.getValue(lower).gender, i)
                prev in PLACE_PREPOSITIONS -> out += Mention(lower, w, Kind.PLACE, null, i)
                prev in ARTICLE_GENDER.keys -> out += Mention(lower, w, Kind.THING, ARTICLE_GENDER[prev], i)
                // "einen kurzen Witz", "ein gutes Frühstück": article, adjective, noun
                prev2 in ARTICLE_GENDER.keys && prev != null && prev.first().isLowerCase() -> out += Mention(lower, w, Kind.THING, ARTICLE_GENDER[prev2], i)
                // "ruhige Musik", "gegen Müdigkeit", "Photosynthese": bare nouns are things, not people
                prev != null && (prev in NOUN_PREPOSITIONS || ADJECTIVE_ENDING.matches(prev)) || nounShaped(lower) ->
                    out += Mention(lower, w, Kind.THING, null, i)
                else -> out += Mention(lower, w, Kind.PERSON, null, i)
            }
        }
        return out
    }

    private fun remember(text: String, rest: List<Mention>, r: Resolution) {
        rest.forEach { touch(it, countMention = true) }
        // a resolved pronoun refreshes its referent: "Und wo wohnt sie?" keeps Anna salient
        r.bindings.forEach { b -> entities[b.entity]?.let { entities[b.entity] = it.copy(lastTurn = turn, mentions = it.mentions + 1) } }
        if (text.endsWith("?") || QUESTION_START.containsMatchIn(text)) {
            lastQuestion = text
            openQuestions.addLast(r.rewritten); while (openQuestions.size > 4) openQuestions.removeFirst()
        }
        lastResolution = r
    }

    private fun touch(m: Mention, countMention: Boolean) {
        val old = entities[m.key]
        entities.remove(m.key)
        entities[m.key] = EntityRef(m.key, old?.label ?: m.label, old?.kind ?: m.kind, old?.gender ?: m.gender, turn,
            (old?.mentions ?: 0) + if (countMention) 1 else 0)
        while (entities.size > maxEntities) entities.remove(entities.values.minByOrNull { score(it) }!!.key)
    }

    private fun demonstrativeUse(tokens: List<String>, index: Int): Boolean {
        // "Wann war das?" / "Wer hat das gesagt?": no noun follows; "das Auto" is an article
        val next = tokens.getOrNull(index + 1) ?: return true
        return !next.first().isUpperCase()
    }

    internal fun parseTime(text: String): TemporalScope? {
        val t = Ewm1Lexicon.lower(text)
        val d = today()
        fun day(x: LocalDate, e: String) = TemporalScope(x, x, e)
        Regex("vor (\\d{1,3}|zwei|drei|vier|fünf) tagen").find(t)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: NUMBERS.getValue(it.groupValues[1])
            return day(d.minusDays(n.toLong()), it.value)
        }
        return when {
            "vorgestern" in t -> day(d.minusDays(2), "vorgestern")
            "gestern" in t -> day(d.minusDays(1), "gestern")
            "übermorgen" in t -> day(d.plusDays(2), "übermorgen")
            Regex("\\bmorgen\\b").containsMatchIn(t) && !t.contains("morgens") -> day(d.plusDays(1), "morgen")
            Regex("\\bheute\\b").containsMatchIn(t) -> day(d, "heute")
            Regex("letzte[nr]? woche|vorige[nr]? woche").containsMatchIn(t) -> {
                val monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
                TemporalScope(monday, monday.plusDays(6), "letzte Woche")
            }
            Regex("nächste[nr]? woche").containsMatchIn(t) -> {
                val monday = d.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                TemporalScope(monday, monday.plusDays(6), "nächste Woche")
            }
            Regex("diese[nr]? woche").containsMatchIn(t) -> {
                val monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                TemporalScope(monday, monday.plusDays(6), "diese Woche")
            }
            Regex("letzte[nr]? monat|vorige[nr]? monat").containsMatchIn(t) -> {
                val first = d.withDayOfMonth(1).minusMonths(1)
                TemporalScope(first, first.plusMonths(1).minusDays(1), "letzten Monat")
            }
            else -> {
                WEEKDAYS.entries.firstOrNull { (name, _) -> Regex("\\bam $name\\b").containsMatchIn(t) }?.let { (name, dow) ->
                    val future = FUTURE.containsMatchIn(t)
                    val x = if (future) d.with(TemporalAdjusters.nextOrSame(dow)) else d.with(TemporalAdjusters.previousOrSame(dow))
                    return day(x, "am $name")
                }
                MONTHS.entries.firstOrNull { (name, _) -> Regex("\\bim $name\\b").containsMatchIn(t) }?.let { (name, month) ->
                    var first = d.withMonth(month).withDayOfMonth(1)
                    if (first.isAfter(d) && !FUTURE.containsMatchIn(t)) first = first.minusYears(1)
                    return TemporalScope(first, first.plusMonths(1).minusDays(1), "im $name")
                }
                null
            }
        }
    }

    /** Days between the scope and today, for tests and the router (negative = past). */
    fun offsetDays(scope: TemporalScope): Long = ChronoUnit.DAYS.between(today(), scope.from)

    companion object {
        private const val WINDOW = 12
        private const val MARGIN = 0.08
        private val PERSONAL = setOf(Kind.PERSON)
        private val ANY = setOf(Kind.PERSON, Kind.THING)
        private val PRONOUNS: Map<String, Set<Kind>> = mapOf(
            "er" to ANY, "ihn" to ANY, "ihm" to ANY, "sie" to ANY, "es" to setOf(Kind.THING),
            "sein" to ANY, "seine" to ANY, "seinen" to ANY, "seinem" to ANY, "seiner" to ANY, "dessen" to ANY,
            "ihr" to PERSONAL, "ihre" to ANY, "ihren" to ANY, "ihrem" to ANY, "ihrer" to ANY, "deren" to ANY,
            "dort" to setOf(Kind.PLACE), "dorthin" to setOf(Kind.PLACE), "das" to setOf(Kind.THING), "dies" to setOf(Kind.THING)
        )
        private val ARTICLE_GENDER = mapOf("der" to 'm', "den" to 'm', "dem" to 'm', "die" to 'f', "das" to 'n',
            "ein" to 'n', "eine" to 'f', "einen" to 'm', "mein" to 'n', "meine" to 'f', "meinen" to 'm', "unser" to 'n', "unsere" to 'f')
        private val PLACE_PREPOSITIONS = setOf("in", "nach", "aus", "bei", "im")
        private val NOUN_PREPOSITIONS = setOf("gegen", "für", "über", "ohne", "um", "durch", "zum", "zur", "vom")
        private val OBJECT_PRONOUNS = setOf("mir", "mich", "dir", "dich", "uns", "euch", "bitte", "mal", "doch")
        private val ADJECTIVE_ENDING = Regex("\\p{Ll}{2,}(e|en|er|es|em)")
        private val NOUN_SUFFIX = Regex(".{3,}(ung|heit|keit|schaft|ion|tät|nis|ismus|ment|ik|ie|ur|ei|ese|enz|anz)")
        private val PERSON_WORDS = setOf("beruflich", "besucht", "besuchen", "erzählt", "gesagt", "kommt", "fährt", "arbeitet",
            "lebt", "heißt", "geboren", "alt", "geht", "ruft", "schreibt", "denkt", "meint", "mag")
        private val GENDERED = setOf("er", "ihn", "ihm", "sie", "ihr", "ihre", "ihren", "ihrem", "ihrer", "deren",
            "sein", "seine", "seinen", "seinem", "seiner", "dessen")
        private fun nounShaped(word: String) = NOUN_SUFFIX.matches(word)
        private val NON_REFERENTS = setOf("bitte", "danke", "hallo", "ja", "nein", "okay", "gemeint", "thema", "woche", "monat",
            "jahr", "tag", "uhr", "montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag", "sonntag")
        private val TOPIC_SWITCH = Regex("(?iu)^(anderes|neues) thema\\b")
        private val CORRECTION = Regex("(?iu)^(?:nein[,!:]?\\s+)?(?:ich (?:meinte|meine)|gemeint war|nicht \\p{L}+,? sondern)\\s+(.+)$")
        private val FOLLOW_UP = Regex("(?iu)^(und|und dann|danach|davor|außerdem)\\b|\\b(damals|dabei|danach)\\b")
        private val QUESTION_START = Regex("(?iu)^(wer|wie|was|wo|wann|warum|wieso|welche[rsnm]?|hat|ist|sind|kannst|weißt)\\b")
        private val FUTURE = Regex("(?iu)\\b(werde|wirst|wird|werden|kommende[nr]?|nächste[nr]?)\\b")
        private val NUMBERS = mapOf("zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5)
        private val WEEKDAYS = linkedMapOf("montag" to DayOfWeek.MONDAY, "dienstag" to DayOfWeek.TUESDAY,
            "mittwoch" to DayOfWeek.WEDNESDAY, "donnerstag" to DayOfWeek.THURSDAY, "freitag" to DayOfWeek.FRIDAY,
            "samstag" to DayOfWeek.SATURDAY, "sonntag" to DayOfWeek.SUNDAY)
        private val MONTHS = linkedMapOf("jänner" to 1, "januar" to 1, "februar" to 2, "feber" to 2, "märz" to 3, "april" to 4,
            "mai" to 5, "juni" to 6, "juli" to 7, "august" to 8, "september" to 9, "oktober" to 10, "november" to 11, "dezember" to 12)

        private fun roleGender(word: String): Char? = when {
            word in Ewm1Lexicon.ROLE_M -> 'm'
            word in Ewm1Lexicon.ROLE_F -> 'f'
            else -> null
        }

        internal fun words(text: String): List<String> = Regex("[\\p{L}][\\p{L}\\p{N}'-]*").findAll(text).map { it.value }.toList()

        private fun replaceWord(text: String, word: String, by: String): String =
            Regex("(?<![\\p{L}])" + Regex.escape(word) + "(?![\\p{L}])").replaceFirst(text, Regex.escapeReplacement(by))
    }
}
