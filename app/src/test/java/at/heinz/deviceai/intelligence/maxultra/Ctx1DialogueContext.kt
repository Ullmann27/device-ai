package at.heinz.deviceai.intelligence.maxultra

/**
 * CTX1 bounded context routine (S1/S2): rewrites a German user turn into an explicit memory query before the
 * unchanged dialogue core answers it, or decides that the turn needs a question back or concerns nobody known.
 * Pre-registered in docs/research/CTX1-DIALOGUE-CONTEXT-PREREG-20261024.md; rules were developed on TRAIN only.
 *
 * Bounded state per session: the entity registry derived from the stored owner facts, at most [MAX_SALIENT] recent
 * mentions, the last query and its entity. Nothing is learned, persisted or used to grant any action.
 *
 * [generalized] (CTX2, docs/research/CTX2-FACT-PHRASING-PREREG-20261024.md; off = CTX1 exactly as measured) adds more
 * owner phrasings to the registry, takes the subject name of any fact as an entity of unknown gender, and calls a name
 * unknown only when no stored fact contains it.
 */
class Ctx1DialogueContext(facts: List<String>, private val generalized: Boolean = false) {
    enum class G { M, F, N }
    /** A referable thing; [known] is false for a name the owner never stored. */
    data class Ent(val surface: String, val genders: Set<G>, val aliases: Set<String>, val known: Boolean = true)

    sealed interface Decision
    /** Ask the core [query]; [scope] (when set) limits the memory pool to the facts of the entities it is about. */
    data class Ask(val query: String, val scope: Set<String>? = null) : Decision
    object Clarify : Decision { override fun toString() = "CLARIFY" }
    object Unknown : Decision { override fun toString() = "UNKNOWN" }

    val registry: List<Ent> = buildRegistry(facts, generalized)
    private val facts = facts.toList()
    private val salient = ArrayList<Pair<Ent, Int>>()
    private var askedFor: Ent? = null
    private var turn = 0
    private var lastQuery: String? = null
    private var lastEntity: Ent? = null
    private var lastDay: String? = null

    /** Entity of every stored fact that names exactly one registry entity (used to follow what the core cited). */
    private val factEntity: Map<String, Ent> = facts.mapNotNull { f ->
        val hits = registry.filter { e -> tokens(f).any { t -> e.aliases.any { it.equals(t, true) } } }
        hits.singleOrNull()?.let { f to it }
    }.toMap()

    fun decide(input: String): Decision {
        turn++
        val toks = tokens(input)
        val lower = toks.map { it.lowercase() }
        val mentions = mentions(toks)
        val day = toks.firstOrNull { it in DAYS }
        val pronoun = lower.withIndex().firstOrNull { it.value in PRONOUNS }
        val cue = lower.any { it in CORRECTION_CUES } || lower.firstOrNull() == "und"
        val questionLike = input.trimEnd().endsWith("?") && lower.firstOrNull() != "und"

        // A short re-targeting turn ("Und Paul?", "Nein, ich meinte Anna.", "Nicht Max, sondern Paul.", "Und am Mittwoch?")
        // repeats the last question for a new entity or day.
        if (!questionLike && cue && lastQuery != null) {
            val target = mentions.lastOrNull { !negated(lower, it.second) }?.first
            if (target != null && lastEntity != null) {
                if (!target.known) return remember(Unknown, target)
                touch(target)
                return remember(Ask(replaceEntity(lastQuery!!, lastEntity!!, target), scope(listOf(target))), target)
            }
            val previousDay = lastDay
            if (target == null && day != null && previousDay != null)
                return remember(Ask(lastQuery!!.replace(previousDay, day)), null, day).also { askedFor = null }
        }
        val wanted = mentions.filter { !negated(lower, it.second) }.map { it.first }
        if (wanted.isNotEmpty()) {
            // only negated mentions ("nicht X" with no target it could read) fall through instead of crashing
            mentions.forEach { (e, _) -> touch(e) }
            val main = wanted.first()
            if (!main.known) return remember(Unknown, main)
            return remember(Ask(cleanNegation(input), scope(wanted.filter { it.known })), main, day)
        }
        if (pronoun != null) {
            val g = PRONOUNS.getValue(pronoun.value)
            val candidates = salient.asReversed().filter { (e, _) -> g in e.genders }
            if (candidates.isEmpty()) return Clarify
            val (best, at) = candidates.first()
            if (candidates.drop(1).any { (e, t) -> t == at && e != best }) return Clarify
            if (!best.known) return remember(Unknown, best)
            touch(best)
            return remember(Ask(cleanNegation(input.replaceToken(toks[pronoun.index], best.surface)), scope(listOf(best))), best, day)
        }
        return remember(Ask(cleanNegation(input)), null, day).also { askedFor = null }
    }

    /**
     * What the core cited for the last [Ask] that named nobody ("Wer ist mein Bruder?"): the first cited fact's entity
     * becomes the most recent mention. After a resolved entity a citation never adds another one.
     */
    fun observe(cited: List<String>) {
        if (askedFor != null) return
        cited.firstOrNull()?.let { factEntity[it] }?.let { e -> if (salient.lastOrNull()?.first != e) touch(e) }
    }

    /** Recorded state: entities in the registry and in the mention list (for the resource report). */
    fun stateSize(): Int = registry.size + salient.size

    /** Facts that name one of [entities]; null (the whole pool) when they name none. */
    private fun scope(entities: List<Ent>): Set<String>? = facts.filter { f ->
        tokens(f).any { t -> entities.any { e -> e.aliases.any { it.equals(t, true) } } }
    }.toSet().ifEmpty { null }

    private fun remember(decision: Decision, entity: Ent?, day: String? = null): Decision {
        val d = if (decision is Ask && decision.scope != null) decision.copy(query = repairTypo(decision.query, decision.scope)) else decision
        if (d is Ask) { lastQuery = d.query; lastEntity = entity ?: lastEntity; askedFor = entity; if (day != null) lastDay = day }
        if (d is Unknown && entity != null) { touch(entity); lastEntity = entity }
        return d
    }

    private fun touch(e: Ent) {
        salient.add(e to turn)
        while (salient.size > MAX_SALIENT) salient.removeAt(0)
    }

    /** Registry entities and unknown names, each with the token index of its mention, in reading order. */
    private fun mentions(toks: List<String>): List<Pair<Ent, Int>> {
        val out = ArrayList<Pair<Ent, Int>>()
        for ((i, t) in toks.withIndex()) {
            val known = registry.firstOrNull { e -> e.aliases.any { it.equals(t, true) } }
            if (known != null) { if (out.none { it.first == known }) out += known to i; continue }
            if (i == 0 || !t[0].isUpperCase() || t in DAYS) continue
            val before = toks[i - 1].lowercase()
            if (before in DETERMINERS || before in PREPOSITIONS) continue   // a common noun or a place, not a person
            val stored = generalized && facts.any { f -> tokens(f).any { it.equals(t, true) } }
            out += Ent(t, setOf(G.M, G.F), setOf(t), known = stored) to i
        }
        return out
    }

    private fun negated(lower: List<String>, index: Int) = index > 0 && lower[index - 1] == "nicht"

    private fun replaceEntity(query: String, old: Ent, new: Ent): String {
        val alias = old.aliases.firstOrNull { a -> tokens(query).any { it.equals(a, true) } } ?: return "$query ${new.surface}"
        return query.replaceToken(alias, new.surface)
    }

    companion object {
        const val MAX_SALIENT = 16
        val DAYS = setOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
        val PRONOUNS = mapOf("er" to G.M, "ihn" to G.M, "ihm" to G.M, "sie" to G.F, "es" to G.N)
        val CORRECTION_CUES = setOf("nein", "nicht", "sondern", "meinte", "gemeint", "falsch", "eigentlich", "doch")
        val DETERMINERS = setOf("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer",
            "mein", "meine", "meinen", "meinem", "meiner", "unser", "unsere", "unseren", "unserem", "unserer",
            "welche", "welcher", "welches", "welchen", "welchem", "kein", "keine", "jede", "jeder", "jedes")
        val PREPOSITIONS = setOf("in", "im", "nach", "aus", "am", "beim", "zum", "zur", "vom", "bis", "seit")
        private val WORD = Regex("[\\p{L}\\p{N}]+")

        fun tokens(text: String): List<String> = WORD.findAll(text).map { it.value }.toList()

        private val KEYBOARD = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
        private val FUNCTION_WORDS = setOf("wie", "wo", "was", "wer", "ist", "is", "als", "hat", "und", "der", "die", "das",
            "denn", "gern", "mir", "von", "über", "mit", "bei", "jetzt", "eigentlich", "nicht", "nein")

        private fun neighbours(a: Char, b: Char): Boolean {
            val ra = KEYBOARD.indexOfFirst { a in it }; val rb = KEYBOARD.indexOfFirst { b in it }
            if (ra < 0 || rb < 0 || kotlin.math.abs(ra - rb) > 1) return false
            return kotlin.math.abs(KEYBOARD[ra].indexOf(a) - KEYBOARD[rb].indexOf(b)) <= 1
        }

        /** One keyboard-neighbour substitution or one swap of adjacent letters. */
        private fun oneSlip(typed: String, word: String): Boolean {
            if (typed.length != word.length || typed == word) return false
            val diff = typed.indices.filter { typed[it] != word[it] }
            return diff.size == 1 && neighbours(typed[diff[0]], word[diff[0]]) ||
                diff.size == 2 && diff[1] == diff[0] + 1 && typed[diff[0]] == word[diff[1]] && typed[diff[1]] == word[diff[0]]
        }

        /**
         * Spelling repair against the referent's own facts only: a word of 3..12 letters that none of them contains is
         * replaced when exactly one of their words is one keyboard slip away. At most one word per turn.
         */
        fun repairTypo(query: String, scope: Set<String>): String {
            val vocabulary = scope.flatMap { tokens(it) }.map { it.lowercase() }.filter { it.length >= 3 }.toSet()
            for (t in tokens(query)) {
                val lower = t.lowercase()
                if (lower.length !in 3..12 || lower in vocabulary || lower in FUNCTION_WORDS || lower in PRONOUNS || t[0].isUpperCase()) continue
                val fits = vocabulary.filter { oneSlip(lower, it) }
                if (fits.size == 1) return query.replaceToken(t, fits.single())
            }
            return query
        }

        /** A negated place ("nicht in Linz") is no search term: the question asks for the actual one. */
        fun cleanNegation(text: String): String =
            text.replace(Regex("(?iu)\\bnicht\\s+(?:mehr\\s+)?(?:in|nach|aus)\\s+\\p{Lu}[\\p{L}-]*"), " ").replace(Regex("\\s+"), " ").trim()

        private fun String.replaceToken(token: String, with: String): String =
            replace(Regex("(?iu)(?<![\\p{L}\\p{N}])" + Regex.escape(token) + "(?![\\p{L}\\p{N}])"), Regex.escapeReplacement(with))

        private val SUBJECT_VERBS = setOf("ist", "wohnt", "lebt", "arbeitet", "spielt", "frisst", "heißt", "hat")
        private val NO_NAME = setOf("Ich", "Wir", "Mein", "Meine", "Unser", "Unsere", "Der", "Die", "Das", "Am", "Bei",
            "Zu", "Ein", "Eine", "Es", "Er", "Sie", "Dein", "Deine")

        private fun articleGenders(article: String): Set<G> = when (article.lowercase()) {
            "eine", "die", "meine", "unsere" -> setOf(G.F)
            "einen", "der" -> setOf(G.M)
            "das" -> setOf(G.N)
            else -> setOf(G.M, G.N)
        }

        /** CTX2 additions: more owner phrasings, then every fact subject name not registered yet (gender unknown). */
        private fun generalizedEntities(facts: List<String>, out: LinkedHashMap<String, Ent>) {
            val word = "(\\p{Lu}[\\p{L}-]+)"
            val relationNamed = Regex("^(Mein|Meine) $word heißt $word\\.")
            val haveOne = Regex("^Ich habe (einen|eine|ein) $word, (?:er|sie|es) heißt $word\\.")
            val named = Regex("\\b(einen|eine|ein) $word namens $word\\b")
            val isOur = Regex("^$word ist (unser|unsere) $word\\.")
            val definite = Regex("^(Der|Die|Das) $word (?:ist|hat|steht|liegt)\\b")
            for (f in facts) {
                relationNamed.find(f)?.let { m -> out.putIfAbsent(m.groupValues[3], Ent(m.groupValues[3], articleGenders(m.groupValues[1]).let { if (it == setOf(G.M, G.N)) setOf(G.M) else it }, setOf(m.groupValues[3]))) }
                haveOne.find(f)?.let { m -> out.putIfAbsent(m.groupValues[3], Ent(m.groupValues[3], articleGenders(m.groupValues[1]), setOf(m.groupValues[3]))) }
                named.find(f)?.let { m -> out.putIfAbsent(m.groupValues[3], Ent(m.groupValues[3], articleGenders(m.groupValues[1]), setOf(m.groupValues[3], m.groupValues[2]))) }
                isOur.find(f)?.let { m -> out.putIfAbsent(m.groupValues[1], Ent(m.groupValues[1], articleGenders(m.groupValues[2]), setOf(m.groupValues[1], m.groupValues[3]))) }
                definite.find(f)?.let { m -> out.putIfAbsent(m.groupValues[2], Ent(m.groupValues[2], articleGenders(m.groupValues[1]), setOf(m.groupValues[2]))) }
            }
            for (f in facts) {
                val toks = tokens(f)
                val first = toks.firstOrNull() ?: continue
                if (!first[0].isUpperCase() || first in NO_NAME || first in DAYS || out.values.any { e -> e.aliases.any { it.equals(first, true) } }) continue
                val next = f.removePrefix(first).trimStart()
                if (next.startsWith(",") || toks.getOrNull(1)?.lowercase() in SUBJECT_VERBS)
                    out.putIfAbsent(first, Ent(first, setOf(G.M, G.F), setOf(first)))
            }
        }

        fun buildRegistry(facts: List<String>, generalized: Boolean = false): List<Ent> {
            val out = LinkedHashMap<String, Ent>()
            val person = Regex("^(\\p{Lu}[\\p{L}-]+) ist (mein|meine) \\p{Lu}")
            val pet = Regex("^(Unser|Unsere) (\\p{Lu}[\\p{L}-]+) heißt (\\p{Lu}[\\p{L}-]+)\\.")
            val thing = Regex("^(Mein|Meine) (\\p{Lu}[\\p{L}-]+) (?:ist|hat|steht|liegt)\\b")
            fun nounGenders(det: String, noun: String) = when {
                det.endsWith("e") -> setOf(G.F)
                noun.endsWith("chen") || noun.endsWith("lein") -> setOf(G.N)
                else -> setOf(G.M, G.N)
            }
            for (f in facts) {
                person.find(f)?.let { m ->
                    val name = m.groupValues[1]
                    out.putIfAbsent(name, Ent(name, if (m.groupValues[2] == "meine") setOf(G.F) else setOf(G.M), setOf(name)))
                }
                pet.find(f)?.let { m ->
                    val name = m.groupValues[3]
                    out.putIfAbsent(name, Ent(name, nounGenders(m.groupValues[1], m.groupValues[2]), setOf(name, m.groupValues[2])))
                }
                thing.find(f)?.let { m ->
                    val noun = m.groupValues[2]
                    out.putIfAbsent(noun, Ent(noun, nounGenders(m.groupValues[1], noun), setOf(noun)))
                }
            }
            if (generalized) generalizedEntities(facts, out)
            return out.values.toList()
        }
    }
}
