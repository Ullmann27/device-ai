package at.heinz.deviceai.intelligence.ewm1

import at.heinz.deviceai.intelligence.ewm1.Ewm1Lexicon.lower
import at.heinz.deviceai.intelligence.ewm1.Ewm1Lexicon.stem
import org.json.JSONArray
import org.json.JSONObject

/** What the workspace hands to the language model: ordered evidence with roles and status, or a reason not to answer. */
data class Ewm1Pack(
    val decision: String,
    val reason: String,
    val query: String,
    val answerFacts: List<String>,
    val linkFacts: List<String>,
    val outdatedFacts: List<String>,
    val focus: List<String>
) {
    /** Cited fact ids in order (answer evidence first, then the facts that connect the question to it). */
    val cited: List<String> get() = (answerFacts + linkFacts).distinct().take(3)

    /** Data block for the model prompt; it carries no instruction or authority. */
    fun render(text: (String) -> String): String = JSONObject()
        .put("decision", decision).put("reason", reason)
        .put("evidence", JSONArray(answerFacts.map { JSONObject().put("id", it).put("text", text(it)).put("role", "answer") } +
            linkFacts.map { JSONObject().put("id", it).put("text", text(it)).put("role", "link") }))
        .put("possibly_outdated", JSONArray(outdatedFacts.map { JSONObject().put("id", it).put("text", text(it)) }))
        .toString()
}

/**
 * EWM1: discourse focus + entity graph + recency in front of the unchanged B0 ranker.
 * [b0Rank] ranks a subset of fact ids for a query exactly like ContinuumMemoryDialogue.rank (top-3, may be empty).
 */
class Ewm1Workspace(rawFacts: List<Pair<String, String>>, private val b0Rank: (String, Set<String>) -> List<String>) {
    internal val memory = Ewm1Memory(rawFacts)
    private val allIds = memory.facts.map { it.id }.toSet()

    private data class Focus(val entity: String, val recency: Double)
    private val focus = ArrayList<Focus>()
    private var turn = 0
    private var lastAttr: List<String> = emptyList()
    private var lastType: String = "ANY"
    private var lastTargets: Set<String> = emptySet()

    private enum class Origin { EXPLICIT, PRONOUN, INHERITED, NONE }

    fun reset() { focus.clear(); turn = 0; lastAttr = emptyList(); lastType = "ANY"; lastTargets = emptySet() }

    fun decide(input: String): Ewm1Pack {
        turn++
        val tokens = Ewm1Tokens.of(input)
        val lowerTokens = tokens.map { it.lower }

        // 1. Mentions (read as name groups like the facts): known, unknown names, unknown nouns; negated ones are corrections.
        val negated = HashSet<Int>()
        val trailingNot = lowerTokens.lastOrNull() == "nicht" && lowerTokens.any { it in setOf("meine", "meinte", "gemeint") }
        for (i in tokens.indices) {
            var k = i - 1
            while (k >= 0 && lowerTokens[k] in setOf("der", "die", "das", "den", "dem", "mehr")) k--
            if (k >= 0 && lowerTokens[k] in setOf("nicht", "kein", "keine")) negated += i
            if (trailingNot) negated += i
        }
        val known = LinkedHashMap<Int, Set<String>>()
        val knownSpan = HashMap<Int, IntRange>()
        val instances = HashSet<Int>()
        val unknownNames = ArrayList<String>()
        val unknownNouns = ArrayList<String>()
        for (m in Ewm1Fact.readMentions(tokens)) {
            val headToken = tokens[m.end]
            val parts = headToken.text.split('-').filter { it.isNotBlank() }
            var resolved = memory.resolveQualified(headToken.text, m.qualifier)
            if (resolved.isEmpty() && parts.size > 1) resolved = memory.resolve(parts.last())
            if (resolved.isEmpty() && m.qualifier != null) resolved = memory.resolve(tokens[m.start].text)
            val instance = m.end > m.start || m.determiner == null || headToken.text.any { it.isDigit() || it == '.' } ||
                resolved.any { it in memory.names }
            if (resolved.isNotEmpty()) {
                known[m.end] = resolved; knownSpan[m.end] = m.start..m.end
                if (instance && !headToken.sentenceInitial || resolved.any { it in memory.names }) instances += m.end
                continue
            }
            if (parts.size > 1 && parts.dropLast(1).any { memory.resolve(it).isNotEmpty() }) {
                // "WLAN-Passwort": known modifier, unknown head -> asks for something the memory lacks
                known[m.end] = parts.dropLast(1).flatMap { memory.resolve(it) }.toSet(); knownSpan[m.end] = m.start..m.end
                unknownNouns += parts.last(); continue
            }
            if (headToken.sentenceInitial && m.end == m.start) continue   // "Speichert …", "Entschuldige …": not a referent
            var k = m.start - 1
            while (k >= 0 && lowerTokens[k] in Ewm1Lexicon.TITLES) k--
            val before = lowerTokens.getOrNull(k)
            val nameSlot = before == null || before == "und" || before == "von" && m.determiner == null ||
                tokens[k].text.endsWith(",") || (before !in Ewm1Lexicon.ARTICLES && before !in Ewm1Lexicon.QUANTIFIERS &&
                before !in Ewm1Lexicon.FUNCTION && !Regex("(e|en|er|es|em)$").containsMatchIn(before)) ||
                before in setOf("ist", "heißt", "wohnt", "arbeitet", "lebt", "hat", "macht", "spielt", "eigentlich", "auch", "mit")
            val genitiveName = headToken.lower.endsWith("s") && m.determiner == null && m.end == m.start &&
                tokens.getOrNull(m.end + 1)?.capitalized == true
            if ((nameSlot || genitiveName) && m.determiner == null && m.end == m.start) unknownNames += headToken.text
            else unknownNouns += headToken.text
        }
        val positive = known.filterKeys { it !in negated }
        val rejected = known.filterKeys { it in negated }.values.flatten().toSet()
        if (rejected.isNotEmpty()) focus.removeAll { it.entity in rejected }

        // 2. Attribute words and expected answer type ("wie viel/groß/lange" are type cues, not attributes).
        val typeCue = tokens.indices.filter { i -> i > 0 && lowerTokens[i - 1] == "wie" && Ewm1Lexicon.groupsOf(lowerTokens[i]).isEmpty() }.toSet()
        val mentionIdx = known.keys + typeCue + tokens.indices.filter { lowerTokens[it] in Ewm1Lexicon.PRONOUN_THING }
        var attr = tokens.filter { it.index !in mentionIdx && it.lower !in Ewm1Lexicon.FUNCTION && it.lower.length >= 3 &&
            it.text !in unknownNames }.map { it.lower }
        var type = answerType(lowerTokens)
        val strongOf = { a: List<String> -> a.filter { it !in Ewm1Lexicon.GENERIC && it !in Ewm1Lexicon.TIME } }
        val onlyNames = positive.isNotEmpty() && positive.values.all { set -> set.any { it in memory.names } }
        val ellipsis = (lowerTokens.firstOrNull() == "und" && strongOf(attr).isEmpty() && (positive.isEmpty() || onlyNames)) ||
            (positive.isEmpty() && unknownNouns.isEmpty() && unknownNames.isEmpty() && strongOf(attr).isEmpty() && tokens.size <= 3)
        if (ellipsis) {
            attr = (attr + strongOf(lastAttr)).distinct()
            if (type == "ANY") type = lastType
        }

        // 3. Unknown referents end the turn honestly; their pronouns then refer to the unknown.
        if (unknownNames.isNotEmpty()) {
            focus += Focus("?" + unknownNames.first(), turn + 0.5)
            lastAttr = attr; lastType = type; lastTargets = emptySet()
            return abstain("UNKNOWN_NAME:" + unknownNames.joinToString(","), input)
        }

        // 4. Targets: explicit mentions, pronoun/group referents, else the inherited topic.
        var origin = Origin.NONE
        val positiveIdx = positive.keys.toList()
        var targets: List<Set<String>> = positiveIdx.map { positive.getValue(it) }
        if (targets.isNotEmpty()) origin = Origin.EXPLICIT
        val groupWord = lowerTokens.any { it in setOf("beiden", "beide", "zwei") }
        // "und" only coordinates when it stands between two named things ("Basko und Nuri"), not as a discourse opener
        val undBetween = positiveIdx.size >= 2 && tokens.indices.any { i -> lowerTokens[i] == "und" &&
            positiveIdx.any { (knownSpan[it]?.last ?: it) < i } && positiveIdx.any { (knownSpan[it]?.first ?: it) > i } }
        val plural = groupWord || (lowerTokens.contains("sie") &&
            lowerTokens.any { it in setOf("wohnen", "arbeiten", "leben", "gehen", "sind", "haben", "treffen", "spielen", "liegen", "schlafen") })
        val pronoun = tokens.firstOrNull { isPronoun(it, tokens) }
        val possessive = pronoun != null && pronoun.lower in setOf("sein", "seine", "seinen", "seinem", "seiner", "seines", "ihr", "ihre",
            "ihren", "ihrem", "ihrer", "dessen", "deren")
        val namedInstance = positiveIdx.any { it in instances }
        if (plural && !undBetween && positiveIdx.count { it in instances } < 2) {
            val group = lastGroup()
            if (group.size >= 2) { targets = targets + group.map { setOf(it) }; origin = Origin.PRONOUN }
        } else if (pronoun != null && !namedInstance &&
            (targets.isEmpty() || possessive || pronoun.lower in Ewm1Lexicon.PRONOUN_THING)) {
            // "Hat er Kinder?", "Welche Farbe hat ihr Napf?", "Wie viele Fische sind darin?": referent plus the named thing
            val resolved = resolvePronoun(pronoun.lower, strongOf(attr), type)
            when {
                resolved == null -> {}
                resolved.size > 1 -> { lastAttr = attr; lastType = type; return Ewm1Pack("CLARIFY", "AMBIGUOUS_REFERENCE", input,
                    emptyList(), emptyList(), emptyList(), focusList()) }
                resolved.single().startsWith("?") -> { lastAttr = attr; lastType = type; return abstain("UNKNOWN_REFERENT", input) }
                else -> { targets = listOf(memory.resolve(resolved.single()) + resolved.single()) + targets
                    if (origin == Origin.NONE) origin = Origin.PRONOUN }
            }
        }
        if (targets.isEmpty() && unknownNouns.isNotEmpty()) {
            focus += Focus("?" + unknownNouns.first(), turn + 0.5)
            lastAttr = attr; lastType = type; lastTargets = emptySet()
            return abstain("UNKNOWN_THING:" + unknownNouns.joinToString(","), input)
        }
        if (targets.isEmpty() && ellipsis && lastTargets.isNotEmpty()) { targets = listOf(lastTargets); origin = Origin.INHERITED }

        // 5. Candidates in scope; acceptable when they carry the asked relation and answer type.
        val rewritten = rewrite(input, targets)
        val strongAttr = strongOf(attr)
        val b0Order = { query: String, ids: Set<String> -> b0Rank(query, ids).withIndex().associate { it.value to it.index } }
        fun order(cands: List<Ewm1Fact>, b0: Map<String, Int>): List<String> =
            current(cands.filter { acceptable(it, strongAttr, type, targets) }
                .sortedWith(compareByDescending<Ewm1Fact> { relation(it, strongAttr) }.thenByDescending { relation(it, attr) }
                    .thenByDescending { if (typeOk(it, type, targets)) 1 else 0 }
                    .thenBy { b0[it.id] ?: 99 }.thenByDescending { it.order }).map { it.id })
        val links = LinkedHashSet<String>()
        var answer: List<String>
        if (targets.isEmpty()) {
            val b0 = b0Order(input, allIds)
            val ranked = b0.keys.toList()
            val ok = order(ranked.map { memory.byId.getValue(it) }, b0)
            answer = (if (ok.isNotEmpty()) ok else current(ranked)).take(1)
        } else {
            for (i in positive.keys) links += memory.linkFacts(tokens[i].text.split('-').last())
            val perTarget = targets.map { set -> set.flatMap { memory.factsMentioning(it) }.distinct() }
            val coordinated = groupWord || undBetween || (plural && targets.size >= 2)
            val b0 = b0Order(rewritten, perTarget.flatten().map { it.id }.toSet())
            if (coordinated) {
                answer = perTarget.mapNotNull { list -> order(list, b0).firstOrNull() }.distinct().take(3)
            } else if (targets.size >= 2) {
                val joint = perTarget.reduce { acc, list -> acc.filter { f -> list.any { it.id == f.id } } }
                answer = order(joint, b0).take(1)
                if (answer.isEmpty()) answer = current(joint.filter { typeOk(it, type, targets) }
                    .sortedWith(compareByDescending<Ewm1Fact> { relation(it, attr) }.thenBy { b0[it.id] ?: 99 }).map { it.id }).take(1)
                if (answer.isEmpty()) {
                    // Bridge: the last named thing answers, a fact naming both connects it ("Wie heißt seine Band?").
                    val asked = perTarget.last()
                    val bridges = perTarget.dropLast(1).flatten().filter { f -> asked.any { it.id == f.id } }
                    val viaBridge = order(asked, b0).firstOrNull { id -> bridges.any { it.id != id } }
                    if (viaBridge != null) { answer = listOf(viaBridge); links += bridges.map { it.id }.filter { it != viaBridge }.take(1) }
                }
            } else answer = order(perTarget.single(), b0).take(1)
            if (answer.isEmpty()) {
                lastAttr = attr; lastType = type
                if (origin == Origin.INHERITED) {
                    val anywhere = order(memory.facts, b0Order(input, allIds))
                    if (anywhere.isNotEmpty() && strongAttr.isNotEmpty())
                        return finish(rewritten, anywhere.take(1), emptySet(), emptyList(), attr, type)
                }
                lastTargets = targets.flatten().toSet()
                remember(targets, emptyList())
                return abstain(if (targets.size >= 2) "NO_FACT_LINKS_ALL" else "UNKNOWN_ATTRIBUTE", rewritten)
            }
            links.retainAll { it !in answer }
        }
        if (answer.isEmpty()) { lastAttr = attr; lastType = type; return abstain("NO_EVIDENCE", rewritten) }
        return finish(rewritten, answer, links, targets, attr, type)
    }

    private fun abstain(reason: String, query: String) =
        Ewm1Pack("ABSTAIN", reason, query, emptyList(), emptyList(), emptyList(), focusList())

    private fun finish(rewritten: String, answer: List<String>, links: Set<String>, targets: List<Set<String>>,
                       attr: List<String>, type: String): Ewm1Pack {
        val outdated = (answer + links).flatMap { memory.supersedes[it].orEmpty() }.distinct().filter { it !in answer }
        remember(targets, answer)
        lastAttr = attr; lastType = type
        lastTargets = targets.flatten().toSet()
        return Ewm1Pack("QUERY", "SUPPORTED", rewritten, answer, links.toList().take(2), outdated, focusList())
    }

    /** Newest statement first: a superseded fact never precedes the fact that replaced it. */
    private fun current(ids: List<String>): List<String> {
        val pool = ids.toSet()
        val out = ArrayList<String>()
        for (id in ids) {
            var newest = id
            while (memory.supersededBy[newest]?.let { it in pool } == true) newest = memory.supersededBy.getValue(newest)
            if (newest !in out) out += newest
        }
        return out
    }

    private val HARD_TYPES = setOf("COLOR", "NUMBER", "TIME")

    /** Relation support, plus one point when the fact has the expected answer type (soft types rank, never exclude). */
    private fun relation(f: Ewm1Fact, attr: List<String>): Int = attr.count { a -> supports(f, a) }

    private fun supports(f: Ewm1Fact, a: String): Boolean {
        val groups = Ewm1Lexicon.groupsOf(a)
        if ("arbeiten" in groups && occupation(f)) return true
        if (Ewm1Lexicon.TIME_SYNONYMS.any { a in it && f.tokens.any { t -> t.lower in it } }) return true
        return f.tokens.any { t ->
            val w = t.lower
            w == a || (a.length >= 5 && w.length >= 5 && Ewm1Lexicon.fold(a).take(5) == Ewm1Lexicon.fold(w).take(5)) ||
                (a.length >= 4 && w.length >= a.length + 3 && w.endsWith(a)) ||
                (w.length >= 4 && a.length >= w.length + 3 && a.endsWith(w)) ||
                (a.length >= 4 && stem(a) == stem(w)) || groups.any { g -> g in Ewm1Lexicon.groupsOf(w) }
        }
    }

    private fun acceptable(f: Ewm1Fact, strongAttr: List<String>, type: String, targets: List<Set<String>> = emptyList()): Boolean =
        (strongAttr.isEmpty() || strongAttr.any { supports(f, it) }) && (type !in HARD_TYPES || typeOk(f, type, targets))

    /** "Lisa ist Lehrerin …", "Daniel ist Bergführer.", "… arbeitet als Ingenieur …" */
    private fun occupation(f: Ewm1Fact): Boolean = f.tokens.withIndex().any { (i, t) ->
        (t.lower == "ist" || t.lower == "als") && f.tokens.getOrNull(i + 1)?.let { n ->
            n.capitalized && Ewm1Lexicon.occupationLike(n.lower) && n.lower !in Ewm1Lexicon.GENERIC } == true }

    private fun typeOk(f: Ewm1Fact, type: String, targets: List<Set<String>> = emptyList()): Boolean = when (type) {
        "KIND" -> f.tokens.withIndex().any { (i, t) -> (t.lower == "ist" || t.lower == "sind") &&
            f.tokens.getOrNull(i + 1)?.lower in setOf("ein", "eine") } || f.tokens.any { it.lower in setOf("heißt", "namens") }
        "DEFINITION" -> targets.isEmpty() || f.mentions.firstOrNull()?.let { m -> targets.any { m.key in it } &&
            f.tokens.getOrNull(m.end + 1)?.lower in setOf("ist", "sind") } == true
        "TIME" -> f.tokens.any { it.lower in Ewm1Lexicon.TIME || it.lower.any(Char::isDigit) || it.lower in setOf("bis", "ab") }
        "NUMBER" -> f.tokens.any { it.lower.any(Char::isDigit) || it.lower in Ewm1Lexicon.NUMBER_WORDS.keys - setOf("ein", "eine") }
        "COLOR" -> f.tokens.any { t -> Ewm1Lexicon.isColor(t.lower) }
        "PLACE" -> f.tokens.any { it.lower in setOf("in", "im", "am", "auf", "bei", "neben", "hinter", "aus", "nach", "von", "zuhause", "vor", "unter") }
        "PERSON" -> f.mentions.any { it.key in memory.names || memory.genderOf(it.key).let { g -> g.size == 1 && 'n' !in g } }
        else -> true
    }

    private fun answerType(l: List<String>): String {
        val first = l.firstOrNull { it != "und" && it != "nein" && it != "jetzt" } ?: return "ANY"
        val text = l.joinToString(" ")
        return when {
            text.contains("welche farbe") || l.any { it.contains("farbe") || it == "farblich" } -> "COLOR"
            text.contains("wie alt") || text.contains("wie viel") || text.contains("wie viele") || text.contains("wie groß") ||
                text.contains("wie lange") -> "NUMBER"
            l.contains("wann") || text.contains("seit wann") -> "TIME"
            l.any { it in setOf("wo", "woher", "wohin") } -> "PLACE"
            l.any { it in Ewm1Lexicon.KIND_NOUNS } && (l.contains("welche") || l.contains("welches") || l.contains("welcher") ||
                text.contains("was für")) -> "KIND"
            (first == "was" || first == "wer") && l.getOrNull(l.indexOf(first) + 1) in setOf("ist", "sind") &&
                l.size <= 4 -> "DEFINITION"
            first in setOf("wer", "wem", "wen") || text.contains("mit wem") -> "PERSON"
            else -> "ANY"
        }
    }

    private fun isPronoun(t: Ewm1Token, all: List<Ewm1Token>): Boolean {
        val w = t.lower
        if (w in setOf("er", "ihn", "ihm", "sie", "es", "seine", "sein", "seinen", "seinem", "seiner", "ihre", "ihren", "ihrem",
                "ihrer", "dessen", "deren", "da", "dort", "darin", "darauf", "dafür", "dazu", "damit", "daran", "davon", "dorthin")) {
            if (w == "ihr" && t.index == 0) return false
            if (w == "da" && all.getOrNull(t.index + 1)?.lower == "der") return true
            return true
        }
        // standalone article used as pronoun: "wo lebt die?", "was studiert der?", "Welches Modell ist das?"
        if (w in setOf("die", "der", "das") && t.index == all.lastIndex && all.size >= 3) return true
        return false
    }

    /** Most recent compatible referent; several equally recent ones that all fit the question are ambiguous. */
    private fun resolvePronoun(p: String, attr: List<String>, type: String): Set<String>? {
        val wanted: Set<Char>? = when (p) {
            in setOf("er", "ihn", "ihm", "sein", "seine", "seinen", "seinem", "seiner", "dessen", "der") -> setOf('m', 'n')
            in setOf("sie", "ihr", "ihre", "ihren", "ihrem", "ihrer", "deren", "die") -> setOf('f')
            "es" -> setOf('n')
            else -> null
        }
        val thing = p in Ewm1Lexicon.PRONOUN_THING
        val compatible = focus.filter { f ->
            if (f.entity.startsWith("?")) true
            else if (thing) memory.genderOf(f.entity).contains('n') || memory.isPlace(f.entity) || f.entity !in memory.names
            else !memory.isPlace(f.entity) && (wanted == null || memory.genderOf(f.entity).any { it in wanted }) &&
                (p !in setOf("er", "ihn", "ihm") || memory.genderOf(f.entity).contains('m') || f.entity in memory.names)
        }
        if (compatible.isEmpty()) return null
        val top = compatible.maxOf { it.recency }
        val best = compatible.filter { it.recency == top }.map { it.entity }.distinct()
        if (best.size == 1) return setOf(best.single())
        val strong = attr.filter { it !in Ewm1Lexicon.GENERIC && it !in Ewm1Lexicon.TIME }
        val fitting = best.filter { e -> !e.startsWith("?") && memory.factsMentioning(e).any { acceptable(it, strong, type) && (strong.isEmpty() || relation(it, strong) > 0) } }
        return when {
            fitting.size == 1 -> setOf(fitting.single())
            fitting.isEmpty() -> setOf(best.first())
            else -> fitting.toSet()
        }
    }

    private fun lastGroup(): List<String> {
        if (focus.isEmpty()) return emptyList()
        val persons = focus.filter { !it.entity.startsWith("?") && !memory.isPlace(it.entity) &&
            (it.entity in memory.names || memory.genderOf(it.entity).let { g -> 'n' !in g || g.size == 3 }) }
        if (persons.isEmpty()) return emptyList()
        val ordered = persons.sortedByDescending { it.recency }
        val top = ordered.first().recency
        val sameTurn = ordered.filter { it.recency >= top - 0.5 }.map { it.entity }.distinct()
        return if (sameTurn.size >= 2) sameTurn else ordered.map { it.entity }.distinct().take(2)
    }

    private fun primary(set: Set<String>): String = set.firstOrNull { it in memory.names } ?: set.first()

    private fun remember(targetSets: List<Set<String>>, answer: List<String>) {
        val targets = targetSets.filter { it.isNotEmpty() }.map { primary(it) }
        for (e in targets.distinct()) focus += Focus(e, turn + 0.5)
        answer.firstOrNull()?.let { id -> memory.byId.getValue(id).mentions.forEach { m -> if (m.key !in targets) focus += Focus(m.key, turn.toDouble()) } }
        // keep the newest entry per entity
        val newest = focus.groupBy { it.entity }.mapValues { (_, v) -> v.maxOf { it.recency } }
        focus.clear(); newest.forEach { (e, r) -> focus += Focus(e, r) }
        if (focus.size > 24) { focus.sortByDescending { it.recency }; while (focus.size > 24) focus.removeAt(focus.lastIndex) }
    }

    private fun focusList() = focus.sortedByDescending { it.recency }.map { it.entity }.take(8)

    private fun rewrite(input: String, targets: List<Set<String>>): String {
        val label = targets.flatten().firstOrNull { k -> memory.facts.any { f -> f.mentions.any { it.key == k } } } ?: return input
        val shown = memory.facts.asSequence().flatMap { it.mentions.asSequence() }.firstOrNull { it.key == label }?.label ?: label
        return (input + " " + shown).take(512)
    }
}
