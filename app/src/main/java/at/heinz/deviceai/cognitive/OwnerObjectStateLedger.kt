package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * #758 (Fold7 Code53): ephemeral state of objects the owner described in THIS conversation –
 * colour, counts and place – folded message by message from the owner's own words.
 *
 * Root cause it addresses: the dialogue window keeps only [LocalDialogueContextLimits.MAX_TURNS]
 * turns. After „blaue Werkzeugkiste mit zwölf Schrauben in der Garage“, a few short questions and
 * two corrections, the first statement had left the model prompt; „Farbe und Standort bleiben
 * gleich“ then had no referent and the model answered that it had no access to the owner's things.
 * This ledger keeps the last valid value of every attribute beyond that window, including spoken
 * number words, several corrections in one sentence („nicht 17, sondern 19; nein, 19 auch nicht,
 * es sind 21“), negations („nicht im Keller, in der Garage“), references („die Zahl davor“,
 * „doch der andere“) and the temporal order of the owner's earlier statements.
 *
 * Boundaries: owner text of the current session only – never model replies, web data, tool
 * output or persistent memory; RAM only, reset together with the dialogue turns; no tool,
 * memory-write or fact authority. A statement that cannot be bound unambiguously is recorded as
 * unclear and is never guessed; questions, conditionals and hedges never assert a value.
 */
internal class OwnerObjectStateLedger {
    enum class Kind { STATED, CORRECTED, REJECTED, RETRACTED, CONFIRMED }
    data class Event(val value: String?, val kind: Kind, val message: Int)

    private enum class Attr { COLOR, COUNT, PLACE }

    private class Slot(val attr: Attr, val unit: String?) {
        val events = mutableListOf<Event>()
        var current: String? = null
        var uncertain: String? = null
        var lastPair: List<String> = emptyList()
        var historyTruncated = false
        fun copy() = Slot(attr, unit).also {
            it.events += events; it.current = current; it.uncertain = uncertain; it.lastPair = lastPair
            it.historyTruncated = historyTruncated
        }
        fun trimHistory() {
            while (events.size > MAX_EVENTS) {
                events.removeAt(0)
                historyTruncated = true
            }
        }
        /** Distinct accepted values in their temporal order (rejected intermediates excluded). */
        fun accepted(): List<String> = events.filter { it.kind == Kind.STATED || it.kind == Kind.CORRECTED }
            .mapNotNull { it.value }.fold(mutableListOf()) { acc, v -> if (acc.lastOrNull() != v) acc += v; acc }
    }

    private class Item(val noun: String) {
        val slots = LinkedHashMap<String, Slot>()
        fun copy() = Item(noun).also { c -> slots.forEach { (k, v) -> c.slots[k] = v.copy() } }
        fun countSlots() = slots.values.filter { it.attr == Attr.COUNT }
    }

    private val items = LinkedHashMap<String, Item>()
    private var message = 0
    private var incomplete = false
    private val unresolved = mutableListOf<String>()

    fun reset() { items.clear(); message = 0; incomplete = false; unresolved.clear() }

    /** An owner turn of this session could not be read completely: no deterministic answer any more. */
    fun markIncomplete() { incomplete = true }

    fun hasState(): Boolean = items.isNotEmpty()

    /** Recognizes updates without changing state. */
    fun isObjectCorrection(text: String): Boolean = objectCorrectionBody(text.trim()) != null

    private fun objectCorrectionBody(text: String): String? {
        if (incomplete || items.isEmpty() || text.length > 512) return null
        val body = objectCorrection.matchEntire(text)?.groupValues?.get(1)?.trim() ?: return null
        if (quotedOrReported.containsMatchIn(body) || questionSentences(body).isNotEmpty()) return null
        val words = tokens(body)
        if (correctionQualification.containsMatchIn(words.joinToString(" ") { it.low })) return null
        if (words.isEmpty() || words.any { it.low in hedges || it.low in conditionals ||
                it.low in correctionActions } || words.groupBy { it.sentence }.values.any { !supportedStatement(it) })
            return null
        val candidate = copy().also { it.observeOwnerTurn(body) }
        if (candidate.incomplete || candidate.unresolved != unresolved || candidate.items.keys != items.keys)
            return null
        val updated = candidate.items.values.any { item -> item.slots.values.any {
            it.events.lastOrNull()?.message == candidate.message
        } }
        return body.takeIf { updated }
    }

    private fun copy(): OwnerObjectStateLedger = OwnerObjectStateLedger().also { c ->
        items.forEach { (k, v) -> c.items[k] = v.copy() }
        c.message = message; c.incomplete = incomplete; c.unresolved += unresolved
    }

    private fun fingerprint(): String = items.values.joinToString("|") { item ->
        item.noun + ":" + item.slots.entries.joinToString(",") { (k, s) -> "$k=${s.current}/${s.uncertain}/${s.events.size}" }
    } + "#" + unresolved.joinToString() + "#" + incomplete

    // ---------------------------------------------------------------- tokens

    private data class Tok(val raw: String, val low: String, val start: Int, val sentence: Int, val clause: Int)

    private fun tokens(text: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var sentence = 0
        var clause = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isLetterOrDigit() -> {
                    var j = i
                    while (j < text.length && (text[j].isLetterOrDigit() ||
                            (text[j] == '-' && j + 1 < text.length && text[j + 1].isLetter()))) j++
                    val raw = text.substring(i, j)
                    out += Tok(raw, raw.lowercase(Locale.GERMAN), i, sentence, clause)
                    i = j
                    continue
                }
                c == '.' || c == '!' || c == '?' || c == ';' || c == '\n' -> { sentence++; clause++ }
                c == ',' || c == ':' || c == '–' || c == '(' || c == ')' -> clause++
            }
            i++
        }
        return out
    }

    private fun sentences(text: String): List<String> =
        Regex("""[^.!?;\n]+[.!?;]?""").findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()

    private fun questionSentences(text: String): Set<Int> {
        val result = mutableSetOf<Int>()
        var index = 0
        for (match in Regex("""[^.!?;\n]*[.!?;\n]?""").findAll(text)) {
            if (match.value.isEmpty()) continue
            val sentence = match.value.trim()
            if (sentence.endsWith("?") || questionStart.containsMatchIn(sentence)) result += index
            index++
        }
        return result
    }

    private val questionStart = Regex(
        """(?iu)^(?:und\s+|aber\s+)?(?:welche\p{L}*|wie|wieviele|wo|wohin|woher|was|wer|warum|wieso|wann|ist|sind|hat|haben|steht|stehen|liegt|liegen|in\s+welcher)(?!\p{L})""")
    private val quotedOrReported = Regex(
        """(?iu)[„“«»"]|^(?:schreib\p{L}*|erzähl\p{L}*|übersetz\p{L}*|zeig\p{L}*|suche?|finde?|lies|erkläre?)\s|(?<!\p{L})(?:beispiel|zitat|im\s+(?:buch|text)|sagt|sagte|behauptet|behauptete)(?!\p{L})""")
    private val acquisition = Regex(
        """(?iu)^(?:ich\s+(?:habe|besitze)|wir\s+(?:haben|besitzen))\s+(?:(?:noch|jetzt)\s+)?""")
    private val unsupportedChange = setOf("verkauft", "verschenkt", "verloren", "entsorgt", "weggeworfen",
        "geliehen", "verliehen", "gestohlen", "leer", "umgestellt", "verschoben", "verlegt", "abgeholt")
    private val consumption = setOf("getrunken", "gegessen", "verbraucht", "gelesen", "geschlafen", "gewartet")

    private fun supportedStatement(list: List<Tok>): Boolean {
        val text = list.joinToString(" ") { it.low }
        if (countChangeSentence(list)) return true
        val owned = acquisition.find(text)
        if (owned != null) {
            val next = text.substring(owned.range.last + 1).substringBefore(' ')
            return (next in articles || GermanNumberWords.parse(next) != null) &&
                list.none { it.low in consumption }
        }
        val named = list.indexOfFirst { resolveItemName(it.low) != null }
        // "Nein, die Werkzeugkiste ist rot" is a correction of an explicitly
        // named existing item. Leading "nein" does not negate the new value;
        // the actual value's own "nicht" remains handled by mention polarity.
        if (named in 0..4 && list.take(named).withIndex().all { (index, token) ->
                (index == 0 && token.low == "nein") ||
                    token.low in articles + setOf("in", "im", "auf", "unter", "neben") ||
                    color(token.low) != null
            }) return true
        if (Regex("""^(?:nein\s+)?nicht\s|^(?:es|sie|er)\s+(?:ist|sind|steht|stehen|liegt|liegen)\s|^(?:korrigiere|ändere)\s+(?:die\s+)?(?:anzahl|zahl|farbe|ort|standort)\s""")
                .containsMatchIn(text)) return true
        val firstValue = list.dropWhile { it.low in setOf("nein", "doch", "ach") }.firstOrNull()
        if (firstValue != null && number(firstValue) != null &&
            list.any { it.low in negators + replacing + setOf("falsch") }) return true
        if (list.first().low in setOf("farbe", "anzahl", "zahl", "ort", "standort", "rest", "alles")) return true
        if (list.first().low in setOf("im", "in", "auf", "unter", "neben") && list.any { it.low in replacing + negators }) return true
        if (ownObjectSubject(list)) return true
        return previousRef.containsMatchIn(text) || otherRef.containsMatchIn(text)
    }

    private val subjectDeterminers = setOf("mein", "meine", "unser", "unsere", "die", "der", "das")
    private val stateVerbs = setOf("ist", "sind", "steht", "stehen", "liegt", "liegen", "befindet", "befinden")
    private val containVerbs = setOf("enthält", "enthalten", "hat", "haben")

    /**
     * Code55 field bug D: „Meine Werkzeugkiste ist blau, steht im Keller und enthält zwölf Schrauben.“ – the owner's
     * object as grammatical subject, introduced without „Ich habe …“. Only „mein/unser/der/die/das [colour] Noun“
     * followed by a state verb, or by „enthält/hat“ plus a number, counts. „Mein Nachbar hat eine rote …“ (another
     * person's possession) and „… hat ihre Werkzeugkiste rot gestrichen“ stay unsupported.
     */
    private fun ownObjectSubject(list: List<Tok>): Boolean {
        if (list.size < 3 || list[0].low !in subjectDeterminers) return false
        val nounAt = if (color(list[1].low) != null) 2 else 1
        val noun = list.getOrNull(nounAt) ?: return false
        if (!isNounToken(noun) || noun.low in nonItemNouns || noun.low in nonCountUnits || color(noun.low) != null) return false
        val verb = list.getOrNull(nounAt + 1)?.takeIf { it.clause == noun.clause }?.low ?: return false
        if (verb in stateVerbs) return true
        if (verb !in containVerbs) return false
        val counted = list.drop(nounAt + 2).dropWhile { it.low in presentMarkers + setOf("noch", "genau") }.firstOrNull()
        return counted != null && number(counted) != null
    }

    private fun rememberUnresolved(value: String) {
        unresolved += value
        while (unresolved.size > MAX_UNRESOLVED) unresolved.removeAt(0)
    }

    // ---------------------------------------------------------------- vocabulary

    private val colorStems = listOf("blau", "rot", "grün", "gruen", "gelb", "schwarz", "weiß", "weiss", "grau",
        "orange", "braun", "lila", "violett", "rosa", "pink", "türkis", "beige", "silbern", "golden", "bunt")
    private fun color(low: String): String? {
        val base = low.removePrefix("hell").removePrefix("dunkel")
        val prefix = low.substring(0, low.length - base.length)
        for (stem in colorStems) {
            if (base == stem || (base.startsWith(stem) && base.length - stem.length <= 2 &&
                    base.substring(stem.length) in setOf("e", "en", "er", "es", "em"))) {
                val canonical = when (stem) { "gruen" -> "grün"; "weiss" -> "weiß"; else -> stem }
                return prefix + canonical
            }
        }
        return null
    }

    private val articles = setOf("ein", "eine", "einen", "einem", "einer", "eines", "mein", "meine", "meinen",
        "meinem", "meiner", "meines", "die", "der", "das", "den", "dem", "des", "diese", "dieser", "diesen",
        "dieses", "unsere", "unser", "unseren", "unserem", "deine", "dein", "deinen")
    private val negators = setOf("nicht", "kein", "keine", "keinen", "keiner", "keinem", "nie", "niemals")
    private val hedges = setOf("vielleicht", "eventuell", "ungefähr", "etwa", "circa", "ca", "wahrscheinlich",
        "vermutlich", "möglicherweise", "glaube", "glaub", "schätze")
    private val conditionals = setOf("wenn", "falls", "angenommen", "hätte", "hätten", "wäre", "wären", "würde")
    private val pastMarkers = setOf("früher", "vorher", "ursprünglich", "damals", "gestern", "zuerst", "anfangs")
    private val presentMarkers = setOf("jetzt", "nun", "inzwischen", "mittlerweile", "heute", "aktuell")
    private val replacing = setOf("statt", "anstatt", "anstelle")
    private val nonItemNouns = setOf("farbe", "anzahl", "zahl", "menge", "ort", "standort", "platz", "angabe",
        "angaben", "gespräch", "gespräches", "gesprächs", "korrektur", "korrekturen", "frage", "fragen", "antwort",
        "wert", "werte", "stand", "information", "informationen", "daten", "überprüfung", "prüfung", "nachricht",
        "rest", "moment", "test", "aufgabe", "beispiel", "erinnerung", "kontext", "verifikation", "gedächtnis",
        "reihenfolge", "verlauf", "sache", "sachen", "ding", "dinge", "zugriff", "ergebnis", "auftrag", "hinweis")
    private val nonPlaceNouns = setOf("gespräch", "gesprächs", "moment", "ordnung", "zukunft", "prinzip", "grunde",
        "allgemeinen", "augenblick", "detail", "ernst", "ganzen", "wesentlichen", "voraus", "nachhinein",
        "folgenden", "rahmen", "sinne", "text", "satz", "nachricht", "chat", "internet", "gedächtnis", "kontext",
        "kopf", "zweifel", "übrigen", "vergleich", "fall", "gegenteil", "nachgang", "anschluss", "laufe")
    private val nonCountUnits = setOf("uhr", "prozent", "jahr", "jahre", "jahren", "tag", "tage", "tagen", "woche",
        "wochen", "monat", "monate", "monaten", "minute", "minuten", "stunde", "stunden", "sekunde", "sekunden",
        "euro", "cent", "grad", "mal", "punkt", "punkte", "seite", "seiten")
    private val genericUnits = setOf("stück", "stk", "exemplare", "teile")
    private val placePreps = listOf(listOf("in", "der"), listOf("in", "dem"), listOf("in", "den"), listOf("auf", "dem"),
        listOf("auf", "der"), listOf("unter", "dem"), listOf("unter", "der"), listOf("hinter", "dem"),
        listOf("hinter", "der"), listOf("neben", "dem"), listOf("neben", "der"), listOf("vor", "dem"),
        listOf("vor", "der"), listOf("bei", "der"), listOf("bei", "dem"), listOf("im"), listOf("am"), listOf("beim"),
        listOf("ins"), listOf("unterm"), listOf("hinterm"), listOf("auf"), listOf("in"))

    private fun isNounToken(t: Tok) = t.raw.length in 3..48 && t.raw[0].isUpperCase() && t.raw.all { it.isLetter() || it == '-' }
    private fun number(t: Tok): Int? = if (t.low in setOf("ein", "eine", "einen", "einem", "einer", "eines")) null
        else GermanNumberWords.parse(t.low)
    private fun unitKey(unit: String) = unit.lowercase(Locale.GERMAN).replace(Regex("(?:en|e|n|s)$"), "")

    // ---------------------------------------------------------------- mentions

    private enum class Polarity { ASSERT, NEGATE, OLD }
    private data class Mention(val attr: Attr, val value: String, val unit: String?, val index: Int,
                               val polarity: Polarity, val sentence: Int, val hedged: Boolean, val explicitItem: String?)

    /** Value/polarity mentions of one owner message, in textual order. */
    private fun mentions(toks: List<Tok>, questions: Set<Int>, knownItems: Map<String, Item>,
                         introduced: Map<Int, String>): List<Mention> {
        val out = mutableListOf<Mention>()
        val skipSentences = mutableSetOf<Int>()
        toks.groupBy { it.sentence }.forEach { (s, list) ->
            if (s in questions || list.any { it.low in conditionals }) skipSentences += s
        }
        val hedgedSentences = toks.groupBy { it.sentence }.filter { (_, list) ->
            list.any { it.low in hedges } || list.indices.any { i ->
                list[i].low == "oder" && i > 0 && i + 1 < list.size &&
                    (number(list[i - 1]) != null || color(list[i - 1].low) != null) &&
                    (number(list[i + 1]) != null || color(list[i + 1].low) != null)
            }
        }.keys

        fun polarityAt(start: Int, end: Int): Polarity {
            val before = (1..3).mapNotNull { toks.getOrNull(start - it) }.filter { it.clause == toks[start].clause }
            val b1 = before.getOrNull(0)?.low
            val b2 = before.getOrNull(1)?.low
            if (b1 in replacing || (b1 == "von" && b2 in replacing) ||
                (b1 == "von" && toks.drop(end + 1).take(4).any { it.low == "auf" && it.sentence == toks[start].sentence }))
                return Polarity.OLD
            if (b1 in negators || (b1 == "mehr" && b2 == "nicht") || (b2 in negators && b1 in setOf("mehr", "auch", "die", "der", "das", "den")))
                return Polarity.NEGATE
            // A past marker earlier in the same clause frames this value as the old one („früher 12, jetzt 21“).
            val clauseBefore = toks.subList(0, start).filter { it.clause == toks[start].clause }
            if (clauseBefore.any { it.low in pastMarkers } &&
                clauseBefore.none { it.low in presentMarkers }) return Polarity.OLD
            // „neunzehn auch nicht“, „17 stimmt nicht“, „Garage ist falsch“ – the negation closes the clause.
            val after = toks.drop(end + 1).takeWhile { it.clause == toks[end].clause }.map { it.low }
            val tail = after.dropWhile { it in setOf("auch", "ebenfalls", "doch", "ist", "war", "stimmt", "gilt", "ist's") }
            if (after.isNotEmpty() && tail.firstOrNull() in setOf("nicht", "falsch") &&
                tail.drop(1).all { it in setOf("mehr", "richtig", "korrekt", "so") }) return Polarity.NEGATE
            return Polarity.ASSERT
        }

        var i = 0
        while (i < toks.size) {
            val t = toks[i]
            val s = t.sentence
            if (s in skipSentences) { i++; continue }
            val hedged = s in hedgedSentences
            // Place: preposition (+ article) [+ adjective] + noun.
            val prep = placePreps.firstOrNull { p -> p.indices.all { k -> toks.getOrNull(i + k)?.low == p[k] } &&
                p.indices.all { k -> toks[i + k].sentence == s } }
            if (prep != null) {
                var n = i + prep.size
                if (toks.getOrNull(n)?.let { !isNounToken(it) && it.low.endsWith("en") && it.low !in articles } == true &&
                    toks.getOrNull(n + 1)?.let(::isNounToken) == true) n++
                val noun = toks.getOrNull(n)
                if (noun != null && noun.sentence == s && isNounToken(noun) && noun.low !in nonPlaceNouns &&
                    noun.low !in nonItemNouns && noun.low !in knownItems && introduced[n] == null &&
                    number(noun) == null && color(noun.low) == null) {
                    val phrase = toks.subList(i, n + 1).joinToString(" ") { if (it === noun) it.raw else it.low }
                    out += Mention(Attr.PLACE, phrase, null, i, polarityAt(i, n), s, hedged, null)
                    i = n + 1
                    continue
                }
            }
            // Bare place noun after „statt/nicht/sondern/doch“ that names a place already used („im Keller statt Garage“).
            if (isNounToken(t) && toks.getOrNull(i - 1)?.low in (replacing + negators + setOf("sondern", "doch")) &&
                (knownPlace(t.low, knownItems) != null || out.any { it.attr == Attr.PLACE && it.sentence == s })) {
                val known = knownPlace(t.low, knownItems)
                out += Mention(Attr.PLACE, known ?: t.raw, null, i, polarityAt(i, i), s, hedged, null)
                i++
                continue
            }
            // Colour adjective or predicate.
            val colour = color(t.low)
            if (colour != null) {
                val next = toks.getOrNull(i + 1)
                val explicit = next?.takeIf { it.sentence == s && it.low in knownItems }?.low ?: introduced[i + 1]
                out += Mention(Attr.COLOR, colour, null, i, polarityAt(i, i), s, hedged, explicit)
                i++
                continue
            }
            // Count: number [adjective] [unit].
            val value = number(t)
            if (value == null || toks.getOrNull(i - 1)?.low in setOf("um", "seit", "vor")) { i++; continue }
            var u = i + 1
            val adjective = toks.getOrNull(u)?.takeIf { it.sentence == s &&
                (color(it.low) != null || (!isNounToken(it) && it.low.endsWith("e") && it.low !in negators)) &&
                toks.getOrNull(u + 1)?.let { n -> isNounToken(n) && n.sentence == s } == true }
            if (adjective != null) u++
            val unitTok = toks.getOrNull(u)?.takeIf { it.sentence == s && isNounToken(it) && it.low !in nonItemNouns }
            if (unitTok != null && unitTok.low in nonCountUnits) { i = u + 1; continue }
            val unit = unitTok?.raw?.takeIf { unitTok.low !in genericUnits }
            out += Mention(Attr.COUNT, value.toString(), unit, i,
                polarityAt(i, if (unitTok != null) u else i), s, hedged, null)
            // „fünf blaue Schrauben“: the colour describes the counted things, never a container.
            if (adjective != null && unitTok != null) color(adjective.low)?.let { c ->
                out += Mention(Attr.COLOR, c, null, i + 1, polarityAt(i, u), s, hedged, unitTok.low)
            }
            i = if (unitTok != null) u + 1 else i + 1
        }
        return out
    }

    private fun knownPlace(noun: String, known: Map<String, Item>): String? =
        known.values.flatMap { it.slots.values }.filter { it.attr == Attr.PLACE }
            .flatMap { s -> s.events.mapNotNull { it.value } + s.lastPair }
            .firstOrNull { it.substringAfterLast(' ').lowercase(Locale.GERMAN) == noun }

    // ---------------------------------------------------------------- fold

    fun observeOwnerTurn(text: String) {
        val original = text.trim()
        if (original.isEmpty()) return
        if (original.length > 4_000 || original.any { it.isISOControl() && it != '\n' }) { incomplete = true; return }
        // The owner may scope a factual object statement to this conversation rather than
        // asking for a persistent fact. Strip ONLY this explicit wrapper. Other imperatives,
        // quotations, questions and instructions retain the existing rejection policy.
        val statement = original.replaceFirst(
            Regex("""(?iu)^(?:merke|merk)\s+dir\s+für\s+(?:dieses|unser)\s+gespräch\s*:\s*(?=\p{L})"""), ""
        )
        val clean = objectCorrectionBody(statement) ?: statement
        if (quotedOrReported.containsMatchIn(clean)) return
        message++
        val toks = tokens(clean)
        if (toks.isEmpty()) return
        val questions = questionSentences(clean).toMutableSet()
        toks.groupBy { it.sentence }.forEach { (sentence, list) ->
            if (list.any { it.low in conditionals } || !supportedStatement(list)) questions += sentence
            if (sentence !in questions && list.any { resolveItemName(it.low) != null } &&
                list.any { it.low in unsupportedChange } && !countChangeSentence(list)) incomplete = true
        }
        val activeText = toks.filter { it.sentence !in questions }.joinToString(" ") { it.low }
        val ambiguousObject = Regex("""(?iu)(?:noch\s+ein|weiter|zweit|ander)\p{L}*\s+(?:\p{L}+\s+)?(\p{L}+)|nicht\s+(?:die|der|das|den)\s+(\p{L}+)[^.!?]{0,80}sondern\s+(?:die|der|das|den)\s+(\p{L}+)""")
        if (ambiguousObject.findAll(activeText).any { match ->
                match.groupValues.drop(1).any { resolveItemName(it) != null || color(it) != null } }) incomplete = true
        if (incomplete) return
        // New objects: article [+ colour] + noun in a statement, with an attribute in the same sentence.
        val introduced = mutableMapOf<Int, String>()
        for (k in toks.indices) {
            val noun = toks[k]
            if (!isNounToken(noun) || noun.low in nonItemNouns || noun.low in nonCountUnits || noun.sentence in questions ||
                noun.low in items || color(noun.low) != null || number(noun) != null) continue
            val b1 = toks.getOrNull(k - 1)
            val b2 = toks.getOrNull(k - 2)
            val hasArticle = b1?.low in articles || (b1 != null && color(b1.low) != null && b2?.low in articles)
            if (!hasArticle) continue
            if (b1 != null && number(b1) != null) continue
            // „in der Garage“ is a place, not an object.
            if (b1?.low in setOf("der", "dem", "den") &&
                b2?.low in setOf("in", "auf", "unter", "hinter", "neben", "vor", "bei")) continue
            val sameSentence = toks.filter { it.sentence == noun.sentence }
            val after = toks.drop(k + 1).takeWhile { it.sentence == noun.sentence }
            val attributeNearby = (b1 != null && color(b1.low) != null) ||
                (after.firstOrNull()?.low == "mit" && after.getOrNull(1)?.let { number(it) } != null) ||
                after.take(6).windowed(2).any { (a, b) -> (a.low == "im" || a.low == "in" || a.low == "auf") && (isNounToken(b) || b.low in articles) } ||
                sameSentence.any { color(it.low) != null } && sameSentence.any { it.low in setOf("ist", "sind") }
            if (attributeNearby) introduced[k] = noun.raw
        }
        val newNames = introduced.values.map { it.lowercase(Locale.GERMAN) }.distinct().count { it !in items }
        if (items.size + newNames > MAX_ITEMS) { incomplete = true; return }
        introduced.values.forEach { noun -> items.getOrPut(noun.lowercase(Locale.GERMAN)) { Item(noun) } }

        val split = splitCountChanges(toks, questions, mentions(toks, questions, items, introduced))
        val found = split.absolute
        val namedItems = toks.filter { it.sentence !in questions }.mapNotNull { resolveItemName(it.low) }.distinct()
        val byItem = mutableMapOf<Pair<Item, String>, MutableList<Mention>>()
        val anchors = mutableMapOf<Attr, Pair<Item, String>>()
        for (m in found) {
            val previousAnchor = anchors[m.attr]
            val explicitSubject = toks.take(m.index).any {
                it.sentence == m.sentence && resolveItemName(it.low) != null
            }
            if (m.attr == Attr.COUNT && previousAnchor != null && m.unit != null &&
                previousAnchor.second != "anzahl|" + unitKey(m.unit) && !explicitSubject) {
                rememberUnresolved("Zahl ${m.value} (Nachricht $message) mit abweichender Einheit")
                continue
            }
            val anchor = anchors[m.attr]?.takeIf { (item, key) ->
                m.unit == null || key == "anzahl|" + unitKey(m.unit) ||
                    item.slots[key]?.unit?.let { unitKey(it) == unitKey(m.unit) } == true
            }
            val item = bind(m, toks, introduced, namedItems) ?: anchor?.first ?: run {
                if (m.explicitItem == null) rememberUnresolved("„${m.value}“ (Nachricht $message) ohne eindeutigen Gegenstand")
                continue
            }
            val key = slotKey(m, item) ?: anchor?.takeIf { it.first === item }?.second ?: run {
                rememberUnresolved("Zahl ${m.value} (Nachricht $message) ohne eindeutige Einheit")
                continue
            }
            val target = item to key
            if (key !in item.slots && byItem.keys.none { it == target } &&
                item.slots.size + byItem.keys.count { it.first === item && it.second !in item.slots } >= MAX_SLOTS) {
                incomplete = true
                continue
            }
            byItem.getOrPut(target) { mutableListOf() } += m
            anchors[m.attr] = target
        }
        for ((target, list) in byItem) {
            val (item, key) = target
            val slot = item.slots.getOrPut(key) { Slot(list.first().attr, list.firstOrNull { it.unit != null }?.unit) }
            fold(slot, list)
        }
        applyCountChanges(split, toks, introduced, namedItems, byItem)
        applyUnchanged(toks, questions, namedItems, byItem.keys)
        applyReferences(clean, toks, questions, namedItems)
    }

    private fun resolveItemName(low: String): Item? {
        items[low]?.let { return it }
        if (low.length < 4) return null
        val tails = items.values.filter { it.noun.lowercase(Locale.GERMAN).endsWith(low) }
        return tails.singleOrNull()
    }

    /** A mention belongs to the object named next to it; otherwise to the one whose value it negates or the only candidate. */
    private fun bind(m: Mention, toks: List<Tok>, introduced: Map<Int, String>, named: List<Item>): Item? {
        // A colour written directly before a noun describes that noun only – never the container.
        m.explicitItem?.let { name -> return items[name.lowercase(Locale.GERMAN)] }
        // Nearest object noun before the mention in the same sentence („Werkzeugkiste mit zwölf Schrauben in der Garage“).
        for (k in m.index - 1 downTo 0) {
            val t = toks[k]
            if (t.sentence != m.sentence) break
            introduced[k]?.let { return items[it.lowercase(Locale.GERMAN)] }
            resolveItemName(t.low)?.let { return it }
        }
        // „Die Schrauben … : es sind 21 Schrauben“ – a count whose unit is itself a tracked object.
        if (m.attr == Attr.COUNT && m.unit != null) {
            val unit = unitKey(m.unit)
            items.values.firstOrNull { unitKey(it.noun) == unit }?.let { return it }
            val owners = items.values.filter { it.countSlots().any { s -> s.unit != null && unitKey(s.unit) == unit } }
            owners.singleOrNull()?.let { return it }
            if (owners.isEmpty() && named.isEmpty()) {
                if (items.size >= MAX_ITEMS) { incomplete = true; return null }
                return items.getOrPut(m.unit.lowercase(Locale.GERMAN)) { Item(m.unit) }
            }
        }
        if (named.size == 1) return named.single()
        val candidates = items.values.filter { item -> item.slots.values.any { it.attr == m.attr } }
        if (m.polarity != Polarity.ASSERT) {
            candidates.filter { item -> item.slots.values.any { s -> s.attr == m.attr && s.current == m.value } }
                .singleOrNull()?.let { return it }
        }
        if (m.attr == Attr.COUNT && m.unit != null && candidates.any { item ->
                item.countSlots().any { it.unit != null && unitKey(it.unit) != unitKey(m.unit) } }) return null
        if (candidates.size == 1) return candidates.single()
        if (candidates.isEmpty() && items.size == 1) return items.values.single()
        return null
    }

    private fun slotKey(m: Mention, item: Item): String? = when (m.attr) {
        Attr.COLOR -> "farbe"
        Attr.PLACE -> "ort"
        Attr.COUNT -> {
            if (m.unit == null && m.polarity != Polarity.ASSERT) item.slots.entries.singleOrNull {
                it.value.attr == Attr.COUNT && it.value.current == m.value
            }?.let { return it.key }
            val unit = m.unit ?: item.countSlots().singleOrNull()?.unit
                ?: item.countSlots().singleOrNull()?.let { return "anzahl|" }
            when {
                unit != null -> "anzahl|" + unitKey(unit)
                item.countSlots().isEmpty() -> "anzahl|"
                else -> null
            }
        }
    }

    private fun fold(slot: Slot, list: List<Mention>) {
        val asserted = list.filter { it.polarity == Polarity.ASSERT }
        val denied = list.filter { it.polarity != Polarity.ASSERT }
        val survivors = asserted.filter { a -> denied.none { it.value == a.value && it.index > a.index } }
        val final = survivors.lastOrNull()
        val rejected = asserted.filter { it !in survivors }.map { it.value }.distinct().filter { it != final?.value }
        rejected.forEach { slot.events += Event(it, Kind.REJECTED, message) }
        val distinct = list.map { it.value }.distinct()
        if (distinct.size == 2) slot.lastPair = distinct
        val hedged = list.any { it.hedged }
        when {
            final != null -> {
                val kind = when (slot.current) { null -> if (slot.events.any { it.kind != Kind.REJECTED }) Kind.CORRECTED else Kind.STATED
                    final.value -> Kind.CONFIRMED; else -> Kind.CORRECTED }
                slot.events += Event(final.value, kind, message)
                slot.current = final.value
                slot.uncertain = if (hedged) "unsicher formuliert" else null
            }
            denied.any { it.value == slot.current } -> {
                slot.events += Event(slot.current, Kind.RETRACTED, message)
                slot.current = null
                slot.uncertain = "zuletzt verneint, ohne neuen Wert"
            }
        }
        slot.trimHistory()
    }

    private val unchanged = Regex("""(?iu)(?<!\p{L})(?:unverändert|bleib(?:t|en)\s+(?:gleich|so|unverändert|wie\s+(?:vorher|gehabt|bisher))|gleich\s+geblieben|wie\s+gehabt|stimm(?:t|en)\s+(?:noch|weiterhin))(?!\p{L})""")

    private fun applyUnchanged(toks: List<Tok>, questions: Set<Int>, named: List<Item>, changed: Set<Pair<Item, String>>) {
        val bySentence = toks.groupBy { it.sentence }.filterKeys { it !in questions }
        for ((_, list) in bySentence) {
            val sentence = list.joinToString(" ") { it.low }
            if (!unchanged.containsMatchIn(sentence)) continue
            val all = list.any { it.low in setOf("rest", "alles", "sonst", "andere") }
            val attrs = buildSet {
                if (all || list.any { it.low == "farbe" }) add(Attr.COLOR)
                if (all || list.any { it.low in setOf("ort", "standort", "platz", "stelle") }) add(Attr.PLACE)
                if (all || list.any { it.low in setOf("anzahl", "zahl", "menge") }) add(Attr.COUNT)
            }
            val targets = if (named.isNotEmpty()) named else if (items.size == 1) items.values.toList() else emptyList()
            for (item in targets) for ((key, slot) in item.slots) {
                if (slot.attr !in attrs || (item to key) in changed || slot.current == null) continue
                slot.events += Event(slot.current, Kind.CONFIRMED, message)
                slot.trimHistory()
            }
        }
    }

    // ---------------------------------------------------------------- relative count changes (Code55)

    private val additionMarkers = setOf("dazu", "dazubekommen", "dazugekauft", "dazugekommen", "dazugelegt", "hinzu",
        "hinzugefügt", "hinzugekommen", "zusätzlich", "zusätzliche", "zusätzlichen", "weitere", "weiteren", "extra",
        "nachgekauft")
    private val removalMarkers = setOf("weniger", "weg", "weggenommen", "entnommen", "herausgenommen", "rausgenommen",
        "abgegeben", "verbraucht", "verloren", "verschenkt", "verkauft", "weggeworfen", "entsorgt", "verliehen",
        "ausgeliehen")
    /** A change whose direction the words do not fix: „5 Schrauben bekommen“ may be a new total or an addition. */
    private val undirectedChange = setOf("bekommen", "gekauft", "geschenkt", "gefunden", "erhalten", "besorgt")
    private val totalMarkers = setOf("insgesamt", "gesamt", "zusammen")

    /** A word naming a unit this conversation already counts („Schrauben“ after „17 Schrauben“). */
    private fun trackedCount(low: String): Boolean = items.values.any { item ->
        item.countSlots().any { s -> s.unit != null && unitKey(s.unit) == unitKey(low) }
    }

    /** „Ich habe 3 Schrauben verbraucht“, „5 Schrauben sind hinzugekommen“: number + counted unit + change word. */
    private fun countChangeSentence(list: List<Tok>): Boolean =
        list.any { it.low in additionMarkers || it.low in removalMarkers } && list.indices.any { k ->
            number(list[k]) != null && list.getOrNull(k + 1)?.let { trackedCount(it.low) } == true
        }

    private class CountSplit(val absolute: List<Mention>, val relative: List<Mention>, val dropped: List<Mention>,
                             val unitOnlyChanges: List<Pair<Int, String>>, val changeSentences: Set<Int>)

    /**
     * Code55 field evidence: „17 Schrauben … 5 Schrauben dazu bekommen, drei blaue und zwei rote“. A count in a clause
     * with a change word is relative to an already stated count, never a new total; „drei blaue“ (number + colour,
     * no noun) or „davon zwei rot“ describes a part, never the colour or total of the object; place and colour words
     * of a change sentence belong to the changed part. Only the remaining mentions are folded as absolute values.
     */
    private fun splitCountChanges(toks: List<Tok>, questions: Set<Int>, found: List<Mention>): CountSplit {
        fun clauseOf(m: Mention) = toks[m.index].clause
        fun clauseWords(clause: Int) = toks.filter { it.clause == clause }.map { it.low }
        val parts = mutableSetOf<Mention>()
        for (c in found) {
            if (c.attr != Attr.COLOR || c.explicitItem != null) continue
            val count = found.firstOrNull { it.attr == Attr.COUNT && it.unit == null && it.index == c.index - 1 &&
                clauseOf(it) == clauseOf(c) }
            if (count == null && "davon" !in clauseWords(clauseOf(c))) continue
            parts += c
            count?.let { parts += it }
            val phrase = listOfNotNull(count?.let { toks[it.index].raw }, toks[c.index].raw).joinToString(" ")
            rememberUnresolved("Teilmenge „$phrase“ (Nachricht $message) nicht als Farbe oder Gesamtzahl übernommen")
        }
        val rest = found.filter { it !in parts }
        fun changeClause(clause: Int): Boolean = clauseWords(clause).let { words ->
            words.any { it in additionMarkers || it in removalMarkers || it in undirectedChange } &&
                words.none { it in totalMarkers }
        }
        fun trackedBefore(m: Mention): Boolean = if (m.unit != null) items.values.any { item ->
            item.countSlots().any { s -> s.current != null && s.unit != null && unitKey(s.unit) == unitKey(m.unit) }
        } else items.values.flatMap { it.countSlots() }.count { it.current != null } == 1
        val relative = rest.filter { m ->
            m.attr == Attr.COUNT && changeClause(clauseOf(m)) && (trackedBefore(m) || rest.any { earlier ->
                earlier.attr == Attr.COUNT && earlier.sentence < m.sentence && !changeClause(clauseOf(earlier)) &&
                    (m.unit == null || earlier.unit != null && unitKey(earlier.unit) == unitKey(m.unit))
            })
        }
        // „Ich habe ein paar Schrauben dazu bekommen“: a directed change of a counted unit without a number.
        val unitOnly = mutableListOf<Pair<Int, String>>()
        toks.groupBy { it.clause }.forEach { (clause, list) ->
            if (list.first().sentence in questions || relative.any { clauseOf(it) == clause } ||
                rest.any { it.attr == Attr.COUNT && clauseOf(it) == clause }) return@forEach
            if (list.none { it.low in additionMarkers || it.low in removalMarkers }) return@forEach
            list.firstOrNull { isNounToken(it) && trackedCount(it.low) }?.let { unitOnly += it.sentence to it.raw }
        }
        val changeSentences = (relative.map { it.sentence } + unitOnly.map { it.first }).toSet()
        val dropped = rest.filter { m -> m !in relative && m.attr != Attr.COUNT && m.sentence in changeSentences }
        dropped.forEach { m ->
            rememberUnresolved("„${m.value}“ (Nachricht $message) gehört zu einem Zu- oder Abgang, nicht zum ganzen Gegenstand")
        }
        return CountSplit(rest.filter { it !in relative && it !in dropped }, relative, dropped, unitOnly, changeSentences)
    }

    /**
     * Applies each relative change after the absolute fold: one clear number with a clear direction changes the stated
     * count (17 + 5 = 22, 17 − 3 = 14). Anything else – no number, two numbers, an undirected verb, a place of the
     * changed part, a result below zero – leaves the count unclear and asks for the current total; nothing is guessed.
     * A later absolute total in the same message wins over the change.
     */
    private fun applyCountChanges(split: CountSplit, toks: List<Tok>, introduced: Map<Int, String>, named: List<Item>,
                                  absolute: Map<Pair<Item, String>, List<Mention>>) {
        for (sentence in split.changeSentences.sorted()) {
            val ms = split.relative.filter { it.sentence == sentence }
            val targets = if (ms.isNotEmpty()) ms.map { m -> bind(m, toks, introduced, named)?.let { item ->
                slotKey(m, item)?.let { key -> item to key } } }.distinct()
            else split.unitOnlyChanges.filter { it.first == sentence }.map { (_, unit) ->
                items.values.flatMap { item -> item.slots.entries.filter { (_, s) ->
                    s.attr == Attr.COUNT && s.unit != null && unitKey(s.unit) == unitKey(unit) }.map { item to it.key } }
                    .singleOrNull() }.distinct()
            val target = targets.singleOrNull()
            val slot = target?.let { (item, key) -> item.slots[key] }
            if (target == null || slot == null) {
                rememberUnresolved("Zu- oder Abgang (Nachricht $message) ohne eindeutigen Gegenstand")
                continue
            }
            if (absolute[target]?.any { it.sentence > sentence } == true) continue
            val words = toks.filter { it.sentence == sentence && (ms.isEmpty() ||
                ms.any { m -> toks[m.index].clause == it.clause }) }.map { it.low }
            val adds = words.any { it in additionMarkers }
            val removes = words.any { it in removalMarkers }
            val amount = ms.singleOrNull()?.takeIf { it.polarity == Polarity.ASSERT && !it.hedged }?.value?.toIntOrNull()
            val base = slot.current?.toIntOrNull()
            // „… dazu bekommen, die liegen im Keller“: possibly another set at another place – not one total.
            val partElsewhere = split.dropped.any { it.sentence == sentence && it.attr == Attr.PLACE }
            val result = when {
                amount == null || base == null || adds == removes || partElsewhere -> null
                adds -> base + amount
                base >= amount -> base - amount
                else -> null
            }
            if (result != null) {
                slot.events += Event(result.toString(), Kind.CORRECTED, message)
                slot.current = result.toString()
                slot.uncertain = null
            } else {
                slot.current = null
                slot.uncertain = "Zu- oder Abgang genannt, aktuelle Gesamtzahl nicht eindeutig – bitte nenne die aktuelle Gesamtzahl"
                rememberUnresolved("Zu- oder Abgang (Nachricht $message) ohne eindeutige neue Gesamtzahl")
            }
            slot.trimHistory()
        }
    }

    private val previousRef = Regex("""(?iu)(?<!\p{L})(?:(?:der|die|das|den)\s+(?:(wert|zahl|anzahl|ort|standort|farbe|angabe)\s+)?(?:davor|von\s+vorher)|(?:der|die|das|den)\s+vorherige[nrs]?\s+(wert|zahl|anzahl|ort|standort|farbe|angabe)|(?:der|die|das|den)\s+alte[nrs]?\s+(wert|zahl|anzahl|ort|standort|farbe|angabe))(?!\p{L})""")
    private val otherRef = Regex("""(?iu)(?<!\p{L})doch\s+(?:lieber\s+)?(?:der|die|das|den)\s+andere[nrs]?(?:\s+(wert|zahl|anzahl|ort|standort|farbe))?(?!\p{L})""")

    private fun applyReferences(text: String, toks: List<Tok>, questions: Set<Int>, named: List<Item>) {
        val statementText = toks.filter { it.sentence !in questions }.joinToString(" ") { it.low }
        val previous = previousRef.find(statementText)
        val other = otherRef.find(statementText)
        val match = previous ?: other ?: return
        val word = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.lowercase(Locale.GERMAN)
        val attr = when (word) {
            "farbe" -> Attr.COLOR
            "ort", "standort" -> Attr.PLACE
            "zahl", "anzahl" -> Attr.COUNT
            else -> null
        }
        val targets = if (named.isNotEmpty()) named else items.values.toList()
        val slots = targets.flatMap { item -> item.slots.values.filter { attr == null || it.attr == attr } }
        // Without a named attribute the reference binds to the attribute changed last in this conversation.
        val slot = if (attr != null) slots.singleOrNull() else slots.filter { s -> s.events.any { it.kind == Kind.CORRECTED } }
            .maxByOrNull { s -> s.events.lastOrNull { it.kind == Kind.CORRECTED }?.message ?: 0 }
            ?.takeIf { best -> slots.count { s -> s.events.lastOrNull { it.kind == Kind.CORRECTED }?.message ==
                best.events.lastOrNull { it.kind == Kind.CORRECTED }?.message } == 1 }
        if (slot == null || slot.current == null) {
            rememberUnresolved("Rückbezug „${match.value}“ (Nachricht $message) ohne eindeutiges Merkmal")
            return
        }
        val target = if (match === previous) {
            val accepted = slot.accepted()
            accepted.getOrNull(accepted.lastIndexOf(slot.current) - 1)
        } else {
            slot.lastPair.takeIf { it.size == 2 && slot.current in it }?.first { it != slot.current }
                ?: slot.accepted().distinct().takeIf { it.size == 2 }?.first { it != slot.current }
        }
        if (target == null) {
            rememberUnresolved("Rückbezug „${match.value}“ (Nachricht $message) ohne eindeutigen früheren Wert")
            slot.uncertain = "Rückbezug unklar"
            return
        }
        slot.events += Event(target, Kind.CORRECTED, message)
        slot.trimHistory()
        slot.current = target
        slot.uncertain = null
    }

    // ---------------------------------------------------------------- rendering

    private fun slotName(slot: Slot): String = when (slot.attr) {
        Attr.COLOR -> "Farbe"
        Attr.PLACE -> "Ort"
        Attr.COUNT -> slot.unit?.let { "Anzahl $it" } ?: "Anzahl"
    }

    private fun history(slot: Slot): String {
        val parts = mutableListOf<String>()
        for (e in slot.events) when (e.kind) {
            Kind.STATED, Kind.CORRECTED -> if (parts.lastOrNull() != e.value) parts += e.value.orEmpty()
            Kind.REJECTED -> parts += "${e.value} (in derselben Nachricht verworfen)"
            Kind.RETRACTED -> parts += "${e.value} verneint"
            Kind.CONFIRMED -> Unit
        }
        return (if (slot.historyTruncated) "früherer Verlauf nicht mehr vollständig → " else "") +
            parts.joinToString(" → ")
    }

    private fun currentText(slot: Slot): String = slot.current?.let { value ->
        value + (slot.uncertain?.let { " (unsicher: $it)" } ?: "")
    } ?: "unklar (${slot.uncertain ?: "kein gültiger Wert"})"

    /** Required prompt block for a request about the tracked objects; "" when there is nothing relevant. */
    fun promptBlock(request: String): String {
        val view = copy().also { it.observeOwnerTurn(request) }
        if (view.items.isEmpty()) return ""
        if (!view.relevantTo(request) && view.fingerprint() == fingerprint()) return ""
        val includeHistory = mode(request) != Mode.CURRENT || historicalRequest.containsMatchIn(request) ||
            sentences(request).any(::asksForChangeHistory)
        return view.render(includeHistory, statedNow = view.fingerprint() != fingerprint())
    }

    private val historicalRequest = Regex(
        """(?iu)(?<!\p{L})(?:früher\p{L}*|vorher\p{L}*|vorhin|damals|gestern|ursprünglich\p{L}*|anfangs|anfänglich\p{L}*|historie|historisch\p{L}*|verlaufs|verlaufes|verläuf\p{L}*)(?!\p{L})""")

    private val historyReadCommand = Regex(
        """(?iu)^(?:bitte\s+)?(?:fasse?|beschreibe?|erkläre?|zeige?|liste|nenne|vergleiche?)(?!\p{L})""")
    private val changeHistoryQuestion = Regex(
        """(?iu)^(?:(?:was|wie)(?!\p{L}).*?(?<!\p{L})(?:hat|habe|haben|hatte|hatten|wurde|wurden|war|waren)(?!\p{L})|welche[nmrs]?\s+(?:korrektur(?:en)?|änderung(?:en)?|veränderung(?:en)?)(?!\p{L}))""")
    private val changeHistoryTopic = Regex(
        """(?iu)(?<!\p{L})(?:korrektur(?:en)?|änderung(?:en)?|veränderung(?:en)?|korrigiert\p{L}*|geändert\p{L}*|verändert\p{L}*)(?!\p{L})""")
    private val prospectiveChange = Regex(
        """(?iu)(?<!\p{L})(?:geplant\p{L}*|möglich\p{L}*|künftig\p{L}*|zukünftig\p{L}*|morgen|demnächst|könnte\p{L}*|würde\p{L}*|soll\p{L}*|möchte\p{L}*|(?:ideen|vorschläge|tipps|anleitungen)\s+(?:für|zu|zur|zum))(?!\p{L})""")
    private val currentValueRequest = Regex(
        """(?iu)(?<!\p{L})(?:aktuell\p{L}*|jetzig\p{L}*|heutig\p{L}*)\s+(?:angaben|werte|farbe|anzahl|zahl|ort|standort)(?!\p{L})""")
    private val oldAttribute = Regex(
        """(?iu)(?<!\p{L})alte[nrsm]?\s+(?:farbe|anzahl|zahl|ort|standort|wert(?:e[nrs]?)?|angabe[n]?)(?!\p{L})""")
    private val beforeCorrection = Regex(
        """(?iu)(?<!\p{L})vor\s+(?:(?:der|dem|meiner|meinem|deiner|deinem)\s+)?(?:(?:letzten|vorigen|vorherigen)\s+)?(?:korrektur|änderung|veränderung)(?!\p{L})""")
    private val afterCorrection = Regex(
        """(?iu)(?<!\p{L})nach\s+(?:(?:der|den|meiner|meinen|deiner|deinen|allen)\s+)?(?:(?:letzten|vorigen|vorherigen)\s+)?(?:korrektur(?:en)?|änderung(?:en)?|veränderung(?:en)?)(?!\p{L})""")
    private val pastStateQuestion = Regex(
        """(?iu)^(?:war|waren|hatte|hatten|stand|standen|lag|lagen|befand|befanden)(?!\p{L})""")

    private fun asksForChangeHistory(sentence: String): Boolean {
        if (prospectiveChange.containsMatchIn(sentence)) return false
        val command = historyReadCommand.containsMatchIn(sentence)
        val recall = command || questionStart.containsMatchIn(sentence) || pastStateQuestion.containsMatchIn(sentence)
        if (recall && (oldAttribute.containsMatchIn(sentence) || beforeCorrection.containsMatchIn(sentence))) return true
        if (currentValueRequest.containsMatchIn(sentence)) return false
        return changeHistoryTopic.containsMatchIn(afterCorrection.replace(sentence, "")) &&
            (command || changeHistoryQuestion.containsMatchIn(sentence))
    }

    private fun relevantTo(request: String): Boolean {
        val low = request.lowercase(Locale.GERMAN)
        val words = Regex("""[\p{L}\p{N}-]+""").findAll(low).map { it.value }.toList()
        if (words.any { resolveItemName(it) != null || items.values.any { item -> item.countSlots().any { s ->
                s.unit != null && unitKey(it) == unitKey(s.unit) } } }) return true
        if (Regex("""(?iu)(?<!\p{L})(?:korrektur\p{L}*|gespräch\p{L}*|angaben|sondern|statt|davor|andere[nrs]?|unverändert|bleib(?:t|en)\s+gleich)(?!\p{L})""")
                .containsMatchIn(low)) return true
        val asksAttribute = Regex("""(?iu)(?<!\p{L})(?:farbe|anzahl|wie\s*viele?|standort)(?!\p{L})""").containsMatchIn(low) ||
            Regex("""(?iu)^\s*wo\s+(?:steht|stehen|ist|sind|liegt|liegen|befindet)\s+(?:sie|er|es|die|der|das)\s*\?""").containsMatchIn(low)
        return asksAttribute && contentWords(words).isEmpty()
    }

    private val questionFiller = setOf("welche", "welcher", "welches", "welchen", "farbe", "wie", "viele", "viel",
        "wieviele", "wo", "steht", "stehen", "ist", "sind", "hat", "haben", "hatte", "hatten", "liegt", "liegen",
        "befindet", "befinden", "sich", "denn", "jetzt", "nun", "noch", "mal", "nochmal", "bitte", "aktuell",
        "momentan", "gerade", "eigentlich", "insgesamt", "darin", "drin", "dort", "sie", "er", "es", "ihn", "ihr",
        "die", "der", "das", "den", "dem", "nach", "allen", "alle", "korrekturen", "korrektur", "ursprünglich",
        "zuerst", "anfangs", "davor", "vorher", "zuvor", "anzahl", "zahl", "ort", "standort", "laut", "meinen",
        "deinen", "angaben", "gesagt", "genannt", "erzählt", "und", "oder", "was", "ich", "dir", "mir", "du", "war",
        "waren", "stand", "sein", "also", "sag", "nenne", "wiederhole", "verlauf", "reihenfolge", "oft", "habe",
        "hab", "über", "zu", "zum", "am", "im", "in", "mein", "meine", "meiner", "meinem", "unser", "unserem",
        "gespräch", "diesem", "dieser", "bisherigen", "bisher", "inzwischen", "mittlerweile", "gezählt", "weißt",
        "gemerkt", "continuum", "jarvis", "drinnen", "enthalten", "enthält", "befanden", "befand", "ein", "eine",
        "einer", "erste", "angabe", "vorige", "vorherige", "vorigen", "vorherigen", "wert", "werte", "gilt", "korrigiert")

    /** Words of a question that are neither question filler nor a tracked object/unit. */
    private fun contentWords(words: List<String>): List<String> = words.filter { w ->
        w.length >= 3 && w !in questionFiller && GermanNumberWords.parse(w) == null && color(w) == null &&
            resolveItemName(w) == null && items.values.none { item -> item.countSlots().any { s ->
                s.unit != null && unitKey(w) == unitKey(s.unit) } }
    }

    fun render(): String = render(includeHistory = true)

    private fun render(includeHistory: Boolean, statedNow: Boolean = false): String = buildString {
        append("\nGESPRÄCHSSTAND AUS DEN EIGENEN ANGABEN DES NUTZERS (nur dieses Gespräch; ")
        append("abgeleiteter Kontext, kein neuer Gedächtniseintrag; spätere Angaben ersetzen frühere):\n")
        if (incomplete) {
            append("Der Gesprächsstand ist unvollständig oder mehrdeutig. Frage nach dem gemeinten Gegenstand und aktuellen Angaben.\n")
            append("Frühere Werte dürfen nicht als aktueller Stand ausgegeben werden.\n")
            return@buildString
        }
        items.values.forEach { item ->
            append("- ").append(item.noun).append(": ")
            append(item.slots.values.joinToString(" · ") { slot ->
                val h = if (includeHistory) history(slot) else ""
                slotName(slot) + " " + currentText(slot) +
                    (if (h.contains("→")) " (Verlauf: $h)" else if (includeHistory &&
                        slot.events.any { it.kind == Kind.CONFIRMED }) " (bestätigt unverändert)" else "")
            })
            append('\n')
        }

        if (!includeHistory && items.values.any { item -> item.slots.values.any {
                it.events.size > 1 || it.historyTruncated
            } }) {
            append("Änderungsverläufe für diese Anfrage ausgelassen; vorhandene begrenzte Ereignisse bleiben intern unverändert. ")
            append("Historische Werte nicht aus dieser Kurzansicht ableiten.\n")
        }
        unresolved.takeLast(3).forEach { append("Ungeklärt: ").append(it).append('\n') }
        append("Fragen nach diesem Gesprächsstand beantwortest du direkt aus diesen Angaben mit „laut deinen Angaben …“. ")
        append("Das ist keine externe Wirklichkeitsprüfung: lehne sie nicht ab und behaupte nicht, dafür keinen Zugriff zu haben. ")
        append("Ungeklärtes nennst du als ungeklärt.\n")
        // Code55 field bug D: a plain statement about the owner's own object was answered with „no tool to search it“.
        if (statedNow) append("Die aktuelle Nachricht enthält neue eigene Angaben des Nutzers; nimm sie als Gesprächsstand an. " +
            "Dafür brauchst du kein Werkzeug, keine Suche und keinen Gerätezugriff.\n")
    }

    // ---------------------------------------------------------------- deterministic recall

    private enum class Mode { CURRENT, ORIGINAL, PREVIOUS, HISTORY }

    private val metaSentence = listOf(
        Regex("""(?iu)^(?:ich\s+)?(?:brauche|benötige|will|möchte)\s+(?:dafür\s+|hier\s+)?keine\s+(?:externe[n]?\s+|zusätzliche[n]?\s+|weitere[n]?\s+)?(?:überprüfung|prüfung|verifikation|bestätigung|recherche|quellen?|messung)$"""),
        Regex("""(?iu)^(?:bitte\s+)?bezieh(?:e)?\s+dich\s+(?:bitte\s+)?(?:ausschließlich|nur|allein)?\s*(?:auf\s+)?(?:unser(?:e[ns]?)?|das|die|meine)\s+(?:bisherige[ns]?\s+)?(?:gespräch|angaben|unterhaltung)(?:\s+(?:hier|bisher))?$"""),
        Regex("""(?iu)^(?:bitte\s+)?(?:antworte\s+)?(?:ausschließlich|nur)\s+(?:aus|laut|anhand|nach)\s+(?:unserem|dem|meinen)\s+(?:bisherigen\s+)?(?:gespräch|angaben)$"""),
        Regex("""(?iu)^(?:bitte\s+)?ohne\s+(?:externe|zusätzliche|weitere)\s+(?:prüfung|überprüfung|recherche|quellen|daten)$"""),
        Regex("""(?iu)^(?:bitte\s+)?(?:keine?|ohne)\s+(?:internet|web|recherche|internetrecherche|messung)$"""),
        Regex("""(?iu)^(?:continuum|jarvis|bitte|danke|ok|okay|also|gut|nun|jetzt|so|hallo|hey)$"""),
        Regex("""(?iu)^(?:bitte\s+)?(?:sag|nenne|verrate)\s+(?:mir\s+)?(?:bitte\s+)?(?:noch\s*mal|nochmal|erneut)?$""")
    )

    private fun requestedAttrs(sentence: String): Set<Attr>? {
        val s = sentence.lowercase(Locale.GERMAN)
        val wanted = buildSet {
            if (Regex("""(?<!\p{L})farbe(?!\p{L})""").containsMatchIn(s)) add(Attr.COLOR)
            if (Regex("""(?<!\p{L})(?:wie\s*viele?|anzahl|zahl|menge)(?!\p{L})""").containsMatchIn(s)) add(Attr.COUNT)
            if (Regex("""(?<!\p{L})(?:wo(?:\s+(?:steht|stehen|ist|sind|liegt|liegen|befindet|befinden|war|waren|stand))?|standort|ort)(?!\p{L})""").containsMatchIn(s)) add(Attr.PLACE)
            if (Regex("""(?<!\p{L})(?:was|welche\s+angaben)(?!\p{L}).*(?<!\p{L})(?:gesagt|erzählt|genannt|angegeben|weißt\s+du|gemerkt)(?!\p{L})""").containsMatchIn(s)) {
                add(Attr.COLOR); add(Attr.COUNT); add(Attr.PLACE)
            }
        }
        return wanted.ifEmpty { null }
    }

    private fun mode(sentence: String): Mode {
        val s = sentence.lowercase(Locale.GERMAN)
        return when {
            Regex("""(?<!\p{L})(?:verlauf|reihenfolge|nacheinander|wie\s+oft|welche\s+korrekturen|alle\s+(?:angaben|werte|korrekturen))(?!\p{L})""").containsMatchIn(s) -> Mode.HISTORY
            Regex("""(?<!\p{L})(?:ursprünglich|zuerst|anfangs|am\s+anfang|als\s+erstes|erste\s+angabe)(?!\p{L})""").containsMatchIn(s) -> Mode.ORIGINAL
            // Code55: „Was hatte ich vorher gesagt?“ asks for the owner's earlier statements – the correction history.
            Regex("""(?<!\p{L})(?:was|welche\s+angaben)(?!\p{L}).*(?<!\p{L})(?:vorher|zuvor)(?!\p{L}).*(?<!\p{L})(?:gesagt|erzählt|genannt|angegeben)(?!\p{L})""").containsMatchIn(s) -> Mode.HISTORY
            Regex("""(?<!\p{L})(?:davor|zuvor|vorher|vorige[nrs]?|vorherige[nrs]?)(?!\p{L})""").containsMatchIn(s) -> Mode.PREVIOUS
            else -> Mode.CURRENT
        }
    }

    /**
     * Zero-token answer to an explicit question about the tracked objects – never a refusal, never
     * an external check. Null whenever the message also asks for something else, asserts a new
     * value, or the object/attribute cannot be resolved; the normal path (with [promptBlock]) runs then.
     */
    fun answer(question: String): String? {
        val q = question.trim()
        if (incomplete || items.isEmpty() || q.length !in 4..600 || q.any { it.isISOControl() }) return null
        // A message that also states or corrects something is not a pure recall question.
        if (copy().also { it.observeOwnerTurn(q) }.fingerprint() != fingerprint()) return null
        val parts = sentences(q)
        if (parts.none { it.endsWith("?") } && !Regex("""(?iu)^(?:sag|nenne|wiederhole)\s""").containsMatchIn(q)) return null
        val requests = mutableListOf<Pair<Set<Attr>, Mode>>()
        val asked = StringBuilder()
        for (part in parts) {
            val body = part.trimEnd('.', '!', '?', ';').trim().replace(Regex("""^(?:continuum|jarvis)\s*,?\s*""", RegexOption.IGNORE_CASE), "")
            if (body.isEmpty() || metaSentence.any { it.matches(body) }) continue
            val clauses = body.split(Regex("""\s*,\s*(?:und\s+)?|\s+und\s+(?=(?:wo|wie|welche|was)(?!\p{L}))"""))
            for (clause in clauses) {
                if (clause.isBlank()) continue
                if (metaSentence.any { it.matches(clause.trim()) }) continue
                if (!Regex("""(?iu)^(?:und\s+)?(?:in\s+welcher|welche[rsnm]?|wie|wo|was|sag|nenne|wiederhole|hat|ist|sind|steht|stehen)(?!\p{L})""").containsMatchIn(clause.trim()))
                    return null
                if (Regex("""(?iu)(?<!\p{L})(?:öffne|starte|lösche|sende|schick|recherchier\p{L}*|such\p{L}*|miss|messe|erkläre|warum|wieso|weshalb|vergleich\p{L}*|plane|schreib\p{L}*)(?!\p{L})""").containsMatchIn(clause))
                    return null
                val attrs = requestedAttrs(clause) ?: return null
                requests += attrs to mode(clause)
                asked.append(clause).append(' ')
            }
        }
        if (requests.isEmpty()) return null
        val item = resolveQuestionItem(asked.toString()) ?: return null
        val lines = mutableListOf<String>()
        val notes = mutableListOf<String>()
        for ((attrs, mode) in requests) for (attr in attrs) {
            val slots = item.slots.values.filter { it.attr == attr }
            if (slots.isEmpty()) {
                lines += when (attr) {
                    Attr.COLOR -> "Farbe: im verfügbaren Gesprächsstand nicht eindeutig genannt"
                    Attr.PLACE -> "Ort: im verfügbaren Gesprächsstand nicht eindeutig genannt"
                    Attr.COUNT -> "Anzahl: im verfügbaren Gesprächsstand nicht eindeutig genannt"
                }
                continue
            }
            for (slot in slots) {
                val name = slotName(slot)
                if (mode != Mode.CURRENT && slot.historyTruncated) return null
                val accepted = slot.accepted()
                val text = when (mode) {
                    Mode.CURRENT -> currentText(slot)
                    Mode.ORIGINAL -> accepted.firstOrNull()?.let { valueText(slot, it) + " (deine erste Angabe)" } ?: "keine erste Angabe"
                    Mode.PREVIOUS -> slot.current?.let { cur ->
                        accepted.getOrNull(accepted.lastIndexOf(cur) - 1)?.let { valueText(slot, it) + " (vor der aktuellen Angabe ${valueText(slot, cur)})" }
                    } ?: "keine frühere Angabe"
                    Mode.HISTORY -> history(slot).ifEmpty { currentText(slot) } +
                        " · aktuell ${currentText(slot)} · Änderungen: ${slot.events.count { it.kind == Kind.CORRECTED || it.kind == Kind.RETRACTED }}"
                }
                val line = "$name: $text"
                if (line !in lines) lines += line
                if (mode == Mode.CURRENT) history(slot).takeIf { it.contains("→") }?.let { notes += "$name $it" }
                if (mode == Mode.CURRENT && !slot.historyTruncated && slot.events.size > 1 && slot.events.all { it.kind != Kind.CORRECTED && it.kind != Kind.REJECTED })
                    notes += "$name seit deiner ersten Angabe unverändert"
            }
        }
        return buildString {
            append("Laut deinen Angaben in diesem Gespräch – ").append(item.noun).append(":\n")
            lines.forEach { append("• ").append(it).append('\n') }
            if (notes.isNotEmpty()) append("Verlauf: ").append(notes.distinct().joinToString("; ")).append('\n')
            if (unresolved.isNotEmpty()) append("Nicht zugeordnet: ").append(unresolved.last()).append('\n')
            append("(Gesprächsstand aus deinen eigenen Nachrichten – keine externe Prüfung der Wirklichkeit.)")
        }
    }

    private fun valueText(@Suppress("UNUSED_PARAMETER") slot: Slot, value: String) = value

    private fun resolveQuestionItem(q: String): Item? {
        val words = Regex("""[\p{L}\p{N}-]+""").findAll(q).map { it.value.lowercase(Locale.GERMAN) }.toList()
        // „Wo ist mein Handy?“ names something this conversation never described: no answer from another object.
        if (contentWords(words).isNotEmpty() || words.any { color(it) != null || GermanNumberWords.parse(it) != null }) return null
        val named = words.mapNotNull { resolveItemName(it) }.distinct()
        if (named.size == 1) return named.single()
        if (named.size > 1) {
            // „Wie viele Schrauben sind in der Werkzeugkiste?“: the unit names the counted thing, the container the object.
            return named.singleOrNull { item -> named.all { other -> other === item ||
                item.countSlots().any { s -> s.unit != null && unitKey(s.unit) == unitKey(other.noun) } } }
        }
        val byUnit = items.values.filter { item -> item.countSlots().any { s ->
            s.unit != null && words.any { unitKey(it) == unitKey(s.unit) } } }
        if (byUnit.size == 1) return byUnit.single()
        return items.values.singleOrNull()
    }

    private companion object {
        val objectCorrection = Regex(
            "(?iu)^(?:nein[,!:]?\\s+)?(?:ich (?:meinte|meine)|gemeint war)\\s+(.+)$")
        val correctionActions = setOf("öffne", "öffnen", "starte", "installiere", "lösche", "sende", "schicke",
            "schick", "suche", "recherchiere", "ignoriere", "kopiere", "kaufe", "bestelle")
        val correctionQualification = Regex(
            """(?iu)(?<![\p{L}\p{N}])(?:sofern|vorausgesetzt|es sei denn|stimmt|stimmte|falsch|unwahr|unsicher|unklar|denke|vermute|ich (?:weiß|weiss|wüsste)|(?:weiß|weiss|wüsste) ich)(?![\p{L}\p{N}])""")
        const val MAX_ITEMS = 6
        const val MAX_EVENTS = 24
        const val MAX_SLOTS = 6
        const val MAX_UNRESOLVED = 3
    }
}
