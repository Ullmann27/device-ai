package at.heinz.deviceai.intelligence.ewm1

import at.heinz.deviceai.intelligence.ewm1.Ewm1Lexicon.lower
import at.heinz.deviceai.intelligence.ewm1.Ewm1Lexicon.stem

/** One surface token of a fact or question. */
internal data class Ewm1Token(val text: String, val lower: String, val index: Int, val capitalized: Boolean,
                              val sentenceInitial: Boolean)

internal object Ewm1Tokens {
    private val WORD = Regex("[\\p{L}\\p{N}]+(?:[-.,][\\p{L}\\p{N}]+)*['’]?")
    private val BREAK = Regex("[.!?:;–]\\s*$")

    fun of(text: String): List<Ewm1Token> {
        val out = ArrayList<Ewm1Token>()
        var previousEnd = 0
        for (m in WORD.findAll(text)) {
            val before = text.substring(previousEnd, m.range.first)
            val initial = out.isEmpty() || (BREAK.containsMatchIn(before) && !before.trim().startsWith(","))
            val raw = m.value.trimEnd('\'', '’')
            out += Ewm1Token(raw, lower(raw), out.size, raw.first().isUpperCase(), initial)
            previousEnd = m.range.last + 1
        }
        return out
    }
}

/** A named or nominal thing the memories talk about, keyed by its normalized head word. */
internal data class Ewm1Mention(val key: String, val label: String, val start: Int, val end: Int,
                                val determiner: String?, val place: Boolean, val qualifier: String? = null)

/** One stored memory sentence, read conservatively. Nothing here becomes a new fact. */
internal class Ewm1Fact(val id: String, val text: String, val order: Int, genitiveBase: (String) -> String = { it },
               keyOf: (Ewm1Mention) -> String = { it.key }) {
    val tokens = Ewm1Tokens.of(text)
    val mentions: List<Ewm1Mention> = readMentions(tokens).map { it.copy(key = genitiveBase(it.key)) }.map { it.copy(key = keyOf(it)) }
    val keys: Set<String> = mentions.flatMap { m -> listOf(m.key) + m.label.split(' ', '-').map { genitiveBase(lower(it)) } }
        .filter { it.length >= 2 }.toSet()
    val subject: String? = mentions.firstOrNull()?.key
    val contentStems: Set<String> = tokens.filter { !it.capitalized && it.lower !in Ewm1Lexicon.FUNCTION && it.lower.length >= 3 }
        .map { stem(it.lower) }.toSet()
    val groups: Set<String> = tokens.flatMap { Ewm1Lexicon.groupsOf(it.lower) }.toSet()
    val lowerText = lower(text)
    val hasUpdateMarker = Ewm1Lexicon.UPDATE_MARKERS.any { marker ->
        if (' ' in marker) lowerText.contains(marker) else tokens.any { it.lower == marker } }

    companion object {
        fun readMentions(tokens: List<Ewm1Token>): List<Ewm1Mention> {
            val out = ArrayList<Ewm1Mention>()
            var i = 0
            while (i < tokens.size) {
                val t = tokens[i]
                if (!isNominal(t)) { i++; continue }
                var j = i
                while (j + 1 < tokens.size && isNominal(tokens[j + 1]) && !tokens[j + 1].sentenceInitial && !isRole(tokens[j].lower)) j++
                val run = tokens.subList(i, j + 1)
                val head = run.last()
                val determiner = tokens.getOrNull(i - 1)?.lower?.takeIf { it in Ewm1Lexicon.ARTICLES }
                var k = i - 1
                while (k >= 0 && tokens[k].lower in Ewm1Lexicon.ARTICLES) k--
                val place = k >= 0 && tokens[k].lower in Ewm1Lexicon.PLACE_PREPOSITIONS
                val qualifier = if (run.size >= 2) run.first().lower else null
                out += Ewm1Mention(head.lower, run.joinToString(" ") { it.text }, i, j, determiner, place, qualifier)
                i = j + 1
            }
            return out
        }

        fun isRole(word: String): Boolean = (Ewm1Lexicon.ROLE_M + Ewm1Lexicon.ROLE_F).any { word == it || (word.length > it.length + 2 && word.endsWith(it)) }

        /** Capitalized, not a closed-class word, not an attribute/time/unit noun, not a pure number. */
        fun isNominal(t: Ewm1Token): Boolean =
            t.capitalized && t.lower !in Ewm1Lexicon.FUNCTION && t.lower !in Ewm1Lexicon.GENERIC &&
                t.lower.any { it.isLetter() } && !(t.lower.length == 1 && !t.text.first().isLetterOrDigit())
    }
}

/** Entity graph over the current memory snapshot: mentions, aliases (roles, appositions, "namens"), gender cues. */
internal class Ewm1Memory(rawFacts: List<Pair<String, String>>) {
    val facts: List<Ewm1Fact>
    init {
        // "Annas", "Martins", "Lukas'" denote the same entity as "Anna", "Martin", "Lukas".
        val raw = rawFacts.flatMap { (_, text) -> Ewm1Fact.readMentions(Ewm1Tokens.of(text)) }
        val plain = raw.map { it.key }.toSet()
        val base = { key: String -> if (key.length >= 4 && key.endsWith("s") && key.dropLast(1) in plain) key.dropLast(1) else key }
        // One name for two different things ("die App Nord-Licht", "das Projekt Nord-Licht"): the class word joins the key.
        val qualifiers = raw.filter { it.qualifier != null }.groupBy { base(it.key) }
            .mapValues { (_, ms) -> ms.map { stem(base(it.qualifier!!)) }.toSet() }
        val keyOf = { m: Ewm1Mention -> if (m.qualifier != null && (qualifiers[m.key]?.size ?: 0) >= 2)
            stem(base(m.qualifier)) + " " + m.key else m.key }
        facts = rawFacts.mapIndexed { i, (id, text) -> Ewm1Fact(id, text, i, base, keyOf) }
    }
    val byId = facts.associateBy { it.id }
    /** role or nominal key -> entity keys it denotes, with the fact that states the link */
    val aliases = LinkedHashMap<String, MutableMap<String, String>>()
    /** group noun -> members ("meine Söhne" -> Florian, Daniel) */
    val groups = LinkedHashMap<String, MutableSet<String>>()
    val gender = HashMap<String, MutableSet<Char>>()
    val places = HashSet<String>()
    val names = HashSet<String>()
    /** later fact id -> earlier fact ids it replaces */
    val supersedes = HashMap<String, MutableSet<String>>()
    val supersededBy = HashMap<String, String>()
    val allKeys: Set<String>

    init {
        for (f in facts) readFact(f)
        allKeys = facts.flatMap { it.keys }.toSet() + aliases.keys + groups.keys
        for ((i, later) in facts.withIndex()) for (earlier in facts.take(i)) if (replaces(later, earlier)) {
            supersedes.getOrPut(later.id) { linkedSetOf() } += earlier.id
            supersededBy[earlier.id] = later.id
        }
    }

    private fun link(role: String, entity: String, factId: String) {
        if (role == entity) return
        aliases.getOrPut(role) { LinkedHashMap() }.putIfAbsent(entity, factId)
    }

    private fun cue(entity: String, g: Char) { gender.getOrPut(entity) { linkedSetOf() } += g }

    private fun roleGender(role: String): Char? = when {
        role in Ewm1Lexicon.ROLE_M || Ewm1Lexicon.ROLE_M.any { role.length > it.length + 2 && role.endsWith(it) } -> 'm'
        role in Ewm1Lexicon.ROLE_F || Ewm1Lexicon.ROLE_F.any { role.length > it.length + 2 && role.endsWith(it) } -> 'f'
        else -> null
    }

    private fun readFact(f: Ewm1Fact) {
        val t = f.tokens
        val ms = f.mentions
        for (m in ms) {
            if (m.place) places += m.key
            when (m.determiner) {
                "der", "dem", "den", "des" -> cue(m.key, 'm')
                "die" -> cue(m.key, 'f')
                "das" -> cue(m.key, 'n')
                "eine", "einer", "meine", "meiner", "unsere", "unserer" -> cue(m.key, 'f')
                null -> if (!m.place) names += m.key
            }
            roleGender(m.key)?.let { cue(m.key, it) }
        }
        for ((idx, m) in ms.withIndex()) {
            val next = ms.getOrNull(idx + 1) ?: continue
            val between = (m.end + 1 until next.start).map { t[it].lower }.filter { it !in Ewm1Lexicon.TITLES }
            // "Mein Bruder heißt Max." / "Die Band heißt Nachtfalter." / "Martins Frau heißt Claudia."
            if (between == listOf("heißt") || between == listOf("namens") ||
                between.lastOrNull() == "namens" && between.size <= 3) {
                link(m.key, next.key, f.id); roleGender(m.key)?.let { cue(next.key, it) }; names += next.key
            }
            // "Meine Hausärztin ist Dr. Leitner." / "Unser Auto ist ein VW Touran." / "Martin ist mein Nachbar."
            if (between.firstOrNull() == "ist" && between.drop(1).all { it in Ewm1Lexicon.ARTICLES || it == "beste" || it == "dr" }) {
                val possessiveObject = between.drop(1).any { it.startsWith("mein") || it.startsWith("unser") }
                if (possessiveObject) {
                    link(next.key, m.key, f.id); roleGender(next.key)?.let { cue(m.key, it) }
                } else if (m.determiner == null) {
                    // "Lisa ist Lehrerin …", "Bello ist ein Labrador.": the class word may point to the name, not back
                    link(next.key, m.key, f.id)
                } else {
                    // "Unser Auto ist ein VW Touran.", "Die Heizung ist eine Wärmepumpe …": both words denote one thing
                    link(m.key, next.key, f.id); link(next.key, m.key, f.id); roleGender(m.key)?.let { cue(next.key, it) }
                }
            }
            // "Meine Schwester Lisa wohnt …" / "mit meiner Schwester Petra" / "Die Katze Mimi …" (role directly before name)
            if (between.isEmpty() && next.determiner == null && m.end + 1 == next.start) {
                link(m.key, next.key, f.id); roleGender(m.key)?.let { cue(next.key, it) }
            }
        }
        // Appositions: NAME , (mein|meine|NAMEs) ROLE ,
        val commaText = f.text
        Regex("^(\\p{Lu}[\\p{L}-]+), (?:mein|meine|unser|unsere|(\\p{Lu}[\\p{L}]+?)s) (\\p{Lu}[\\p{L}-]+),").find(commaText)?.let { r ->
            val name = lower(r.groupValues[1]); val role = lower(r.groupValues[3])
            link(role, name, f.id); roleGender(role)?.let { cue(name, it) }; names += name
        }
        // Groups: "Florian und Daniel sind meine Söhne." / "Meine Tarockrunde besteht aus Fritz, Hans und Walter."
        Regex("^(\\p{Lu}[\\p{L}-]+) und (\\p{Lu}[\\p{L}-]+) sind (?:meine|unsere) (\\p{Lu}[\\p{L}-]+)").find(commaText)?.let { r ->
            groups.getOrPut(lower(r.groupValues[3])) { linkedSetOf() } += listOf(lower(r.groupValues[1]), lower(r.groupValues[2]))
        }
        Regex("^(?:Meine|Mein|Unsere|Unser) (\\p{Lu}[\\p{L}-]+) besteht aus (.+)\\.$").find(commaText)?.let { r ->
            val members = Regex("\\p{Lu}[\\p{L}-]+").findAll(r.groupValues[2]).map { lower(it.value) }.toList()
            if (members.size >= 2) groups.getOrPut(lower(r.groupValues[1])) { linkedSetOf() } += members
        }
        // Possessive pronoun after the subject: "Dr. Huber hat ihre Praxis …", "… wegen seiner Zähne"
        f.subject?.let { s ->
            if (t.any { it.lower in setOf("ihre", "ihren", "ihrer", "ihrem") } && t.none { it.lower in setOf("wir", "ich") }) cue(s, 'f')
            if (t.any { it.lower in setOf("seine", "seinen", "seiner", "seinem") }) cue(s, 'm')
        }
    }

    private fun replaces(later: Ewm1Fact, earlier: Ewm1Fact): Boolean {
        val sameSubject = later.subject != null && later.subject == earlier.subject
        val sharedGroup = later.groups.intersect(earlier.groups).filter { it != "zeitpunkt" }
        val sharedStems = later.contentStems.intersect(earlier.contentStems)
        val sharedOtherEntity = (later.mentions.map { it.key }.toSet() - setOfNotNull(later.subject))
            .intersect(earlier.mentions.map { it.key }.toSet())
        fun timed(f: Ewm1Fact) = f.tokens.any { it.lower in Ewm1Lexicon.TIME && it.lower !in setOf("seit", "jeden", "jede", "heuer") }
        return if (sameSubject) {
            (later.hasUpdateMarker && (sharedGroup.isNotEmpty() || sharedStems.isNotEmpty() || sharedOtherEntity.isNotEmpty() ||
                "zeitpunkt" in later.groups.intersect(earlier.groups) || (timed(later) && timed(earlier)))) ||
                sharedGroup.any { it in Ewm1Lexicon.SINGLE_VALUED }
        } else if (later.subject == null && earlier.subject == null) {
            later.hasUpdateMarker && (sharedStems.size + later.tokens.map { it.lower }.toSet()
                .intersect(earlier.tokens.filter { it.lower in Ewm1Lexicon.GENERIC || it.lower in Ewm1Lexicon.TIME }.map { it.lower }.toSet()).size) >= 2
        } else false
    }

    /** Does a question word denote this key (inflection, genitive -s and compound tolerant)? */
    fun matches(question: String, key: String): Boolean {
        val q = lower(question).trimEnd('\'', '’')
        if (q == key) return true
        if (q.length >= 4 && q.endsWith("s") && q.dropLast(1) == key) return true
        if (q.length >= 4 && key.length >= 4 && stem(q) == stem(key)) return true
        if (' ' in key && matches(q, key.substringAfter(' '))) return true
        if (q.length >= 4 && key.length >= 4 && (key.endsWith(q) || q.endsWith(key) || q.startsWith(key) || key.startsWith(q)))
            return key.length - q.length <= 12 && q.length - key.length <= 12
        return false
    }

    fun factsMentioning(entity: String): List<Ewm1Fact> =
        if (' ' in entity) facts.filter { f -> f.mentions.any { it.key == entity } }
        else facts.filter { f -> f.keys.any { matches(entity, it) || matches(it, entity) } }

    /** Keys a question name denotes; a class word ("die App …") selects among same-named things. */
    fun resolveQualified(head: String, qualifier: String?): Set<String> {
        if (qualifier != null) {
            val q = stem(qualifier)
            val composite = allKeys.filter { ' ' in it && it.substringAfter(' ') .let { h -> matches(head, h) } && it.substringBefore(' ') == q }
            if (composite.isNotEmpty()) return composite.toSet() + composite.flatMap { aliases[it]?.keys.orEmpty() }
        }
        return resolve(head)
    }

    /** head key -> every word of the names it was written with ("x1" -> thinkpad, x1) */
    private val labelParts: Map<String, Set<String>> by lazy {
        facts.flatMap { it.mentions }.groupBy { it.key }.mapValues { (k, ms) ->
            ms.flatMap { m -> m.label.split(' ', '-').map { lower(it) } }.filter { it.length >= 2 }.toSet() + k }
    }

    fun resolve(word: String): Set<String> {
        val direct = allKeys.filter { matches(word, it) }.toSet()
        val viaAlias = direct.flatMap { aliases[it]?.keys.orEmpty() }.flatMap { labelParts[it] ?: setOf(it) }.toSet()
        val viaGroup = direct.flatMap { groups[it].orEmpty() }.toSet()
        return direct + viaAlias + viaGroup
    }

    fun linkFacts(word: String): Set<String> = allKeys.filter { matches(word, it) }
        .flatMap { aliases[it]?.values.orEmpty() }.toSet()

    fun genderOf(entity: String): Set<Char> = gender[entity]?.takeIf { it.isNotEmpty() } ?: setOf('m', 'f', 'n')
    fun isPlace(entity: String) = entity in places && entity !in names
}
