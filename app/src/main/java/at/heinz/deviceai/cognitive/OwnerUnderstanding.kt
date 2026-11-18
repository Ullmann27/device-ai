package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.BootstrapKind
import java.util.Locale

/**
 * Which deterministic lane an owner utterance takes, computed with the productive parsers in the order the
 * owner-turn intake consults them. Used to measure the understanding layer and to keep it from turning a statement
 * into a command. It recognises only; nothing is started.
 */
internal object OwnerRouteSignature {
    /** `COMPUTER:…`, `LOCAL:…`, `NAV:…`, `RESEARCH`, `APP:…`, `TOOL:…` or `CHAT`. */
    fun of(utterance: String, assistantName: String? = null): String {
        val addressed = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName)
        when (val computer = OwnerComputerIntentParser.parse(addressed)) {
            is OwnerComputerIntentParser.Result.Ready -> return "COMPUTER:${computer.intent.app.name}:${computer.intent.goal.name}"
            is OwnerComputerIntentParser.Result.Ask -> return "COMPUTER:ASK"
            is OwnerComputerIntentParser.Result.Unsupported -> return "COMPUTER:UNSUPPORTED"
            OwnerComputerIntentParser.Result.NotComputerCommand -> Unit
        }
        OwnerChatIntentRouter.route(utterance)?.let { return "LOCAL:${it.name}" }
        // Date and time questions are answered from the device clock after the address was removed.
        if (runCatching { OwnerDeviceClockReply.answer(addressed, java.time.Instant.EPOCH, java.time.ZoneOffset.UTC) }
                .getOrNull() != null) return "LOCAL:CLOCK"
        OwnerInternalNavigationIntent.resolve(utterance, assistantName)?.let { return "NAV:${it.target}" }
        if (runCatching { OwnerResearchCommand.parse(utterance, assistantName) }.getOrNull() != null) return "RESEARCH"
        when (val intake = OwnerAppLookupIntent.inspect(utterance, assistantName)) {
            is OwnerAppLookupIntent.Intake.Lookup ->
                return "APP:${if (intake.opening) "OPEN" else "LOOKUP"}:${intake.query.lowercase(Locale.ROOT)}"
            is OwnerAppLookupIntent.Intake.Clarify -> return "APP:CLARIFY"
            is OwnerAppLookupIntent.Intake.Planning -> return "APP:PLANNING"
            null -> Unit
        }
        val decision = BootstrapBrain.decide(addressed, emptyList())
        return if (decision.kind == BootstrapKind.DEVICE_TOOL) "TOOL:${decision.tool}" else "CHAT"
    }

    fun lane(signature: String): String = signature.substringBefore(':')

    /** A lane that already knows exactly what to do; the understanding layer never touches such an utterance. */
    fun resolved(signature: String): Boolean = lane(signature) in setOf("LOCAL", "NAV", "TOOL") ||
        // A computer lane that still has to ask („In welcher App …?“) or refuses is not final: the app name may be the
        // misheard word. A ready computer intent is.
        (lane(signature) == "COMPUTER" && signature != "COMPUTER:ASK" && signature != "COMPUTER:UNSUPPORTED")

    /** Lanes that start an action or a request; LOCAL answers and the read-only device probes of TOOL do not. */
    fun acts(signature: String): Boolean = lane(signature) in setOf("COMPUTER", "APP", "NAV", "RESEARCH")
}

/**
 * Understanding despite misrecognised words (owner brief 18.11.2026): a deterministic interpretation of one owner
 * utterance before it is routed. A token the recogniser produced for a name Continuum knows („Thermux“ for Termux,
 * „Javis“ for Jarvis, „Gamma 4“ for Gemma 4) is repaired by sound and spelling against [OwnerUnderstandingLexicon].
 *
 * Fail-closed decision per utterance:
 *  - one clearly best candidate → [Result.Repaired] with the repaired utterance and what was replaced;
 *  - two close candidates → [Result.Clarify] with one short question naming both;
 *  - anything else → [Result.Unchanged].
 *
 * Never repaired: a word that is a lexicon name or a frequent German/Austrian word, numbers, negations, quoted
 * text, everything after a colon, the body of a message or memory command, any utterance a deterministic lane
 * already resolves, and any repair that would move a non-command utterance into a command lane.
 */
internal object OwnerUnderstanding {
    data class Repair(val heard: String, val understood: String)

    sealed interface Result {
        data object Unchanged : Result
        data class Repaired(val text: String, val repairs: List<Repair>) : Result {
            /** „Ich habe „Thermux“ als Termux verstanden.“ */
            val notice: String get() = "Ich habe " + repairs.joinToString(" und ") { "„${it.heard}“ als ${it.understood}" } +
                " verstanden."
        }
        /** [candidates]: the utterance with each option filled in, in the order of [options]. */
        data class Clarify(val question: String, val heard: String, val options: List<String>,
                           val candidates: List<String>) : Result
    }

    /** Repair in a name slot. */
    const val ACCEPT_IN_SLOT = 0.86
    /** Repair of a distinctive product name anywhere in the utterance. */
    const val ACCEPT_FREE = 0.90
    /** Both candidates of a clarifying question must reach this. */
    const val CLARIFY_MIN = 0.80
    /** The best candidate must lead the second by this much to be used without asking. */
    const val MARGIN = 0.08
    const val MAX_LENGTH = 400

    private data class Token(val text: String, val start: Int, val end: Int)
    private data class Scored(val entry: OwnerUnderstandingLexicon.Entry, val score: Double)
    private class Change(val from: Int, val to: Int, val heard: String, val replacement: String)

    private val word = Regex("\\p{L}[\\p{L}\\p{M}]*(?:[-'’][\\p{L}\\p{M}]+)*")
    private val quoted = Regex("\"[^\"]*\"|„[^“”\"]*[“”\"]|‚[^‘’]*[‘’]|«[^»]*»|»[^«]*«")
    private val negation = Regex("(?iu)^(?:nicht|nichts|nie|niemals|ohne|weder|kein\\p{L}*|not|never|no)$")
    private val verbatimCommand = Regex("(?iu)^(?:\\P{L}*\\p{L}+[,:]\\s+)?(?:bitte\\s+)?(?:merke?\\s+dir|notiere?|diktiere?|" +
        "schreibe?\\s+(?:dir\\s+)?auf|trainiere|korrigiere|widerrufe|prüfe\\s+wissen|neuron|vergiss)(?![\\p{L}])")
    private val messageCommand = Regex("(?iu)^(?:\\P{L}*\\p{L}+[,:]\\s+)?(?:bitte\\s+)?(?:schreibe?|sende|schicke?|verfasse|" +
        "bereite|antworte|richte)(?![\\p{L}])")
    private val imperative = Regex("(?iu)^(?:bitte\\s+)?(?:öffne|oeffne|starte|start|mach|mache|zeig|zeige|such|suche|finde?|" +
        "prüfe?|überprüfe?|checke?|schließe?|beende|stoppe?|installiere?|deinstalliere?|aktualisiere?|wechsle?|gehe?|" +
        "schreibe?|sende|schicke?|lies|lese|spiele?|rufe?|miss|messe|frage?|sage?|erstelle|speichere?|lösche?|hole?|lade?)" +
        "(?![\\p{L}])")
    private val verbFinal = Regex("(?iu)(?<![\\p{L}])(?:öffnen|starten|aufmachen|zumachen|schließen|beenden|installieren|" +
        "deinstallieren|aktualisieren)(?:\\s+bitte)?[.!?]*$")
    private val politeStart = Regex("(?iu)^(?:kannst|könntest|würdest|magst)\\s+du(?![\\p{L}])")

    private const val STOP = "(?=$|[,.;!?:]|\\s+(?:und|dann|danach|sondern|statt|aber)(?![\\p{L}]))"
    private const val LEAD = "(?:^|[,.;!?]\\s*|(?<![\\p{L}])(?:und|dann|danach|sondern|aber)\\s+)(?:bitte\\s+)?"
    private val ANY = OwnerUnderstandingLexicon.Kind.entries.toSet() - OwnerUnderstandingLexicon.Kind.ASSISTANT

    /** Name slots: where the grammar of the utterance expects the name of an app, product, model or feature. */
    private val slotPatterns: List<Pair<Regex, Set<OwnerUnderstandingLexicon.Kind>>> = listOf(
        Regex("(?iu)$LEAD(?:öffne|oeffne|starte|start|schließe?|schliesse?|beende|stoppe?|installiere?|deinstalliere?|" +
            "aktualisiere?|update|wechsle?\\s+(?:zu|zur|zum|in|auf)|gehe?\\s+(?:zu|zur|zum|in|auf))\\s+(.+?)$STOP") to ANY,
        Regex("(?iu)${LEAD}mach(?:e|st)?\\s+(.+?)\\s+(?:auf|zu|an|aus)(?![\\p{L}])") to ANY,
        // Verb-final and polite forms: only the three words in front of the verb can be the name.
        Regex("(?iu)((?:[\\p{L}-]+\\s+){0,2}[\\p{L}-]+)\\s+" +
            "(?:öffnen|starten|aufmachen|zumachen|schließen|beenden|installieren|deinstallieren|aktualisieren|" +
            "neu\\s+starten)(?![\\p{L}])") to ANY,
        Regex("(?iu)(?<![\\p{L}])(?:sondern|statt|stattdessen)\\s+(.+?)$STOP") to ANY,
        Regex("(?iu)(?<![\\p{L}])(?:suche?|finde?|schaue?)\\s+(?:mal\\s+|bitte\\s+)?(?:in|auf|bei)\\s+" +
            "(?:der\\s+|dem\\s+|die\\s+|das\\s+)?([\\p{L}-]+(?:\\s+[\\p{L}-]+)?)") to ANY,
        Regex("(?iu)(?<![\\p{L}])(?:app|anwendung|programm)\\s+([\\p{L}-]+(?:\\s+[\\p{L}-]+)?)") to ANY,
        Regex("(?iu)(?<![\\p{L}])([\\p{L}]+)[\\s-]+app(?![\\p{L}])") to ANY,
        Regex("(?iu)(?<![\\p{L}])(?:ob|ist|hab\\s+ich|habe\\s+ich)\\s+(.+?)\\s+(?:auf\\s+\\p{L}+\\s+\\p{L}+\\s+)?installiert(?![\\p{L}])") to ANY,
        Regex("(?iu)(?<![\\p{L}])(?:version|versionsnummer)\\s+(?:von|vom|der)\\s+(.+?)" +
            "(?=$|[,.;!?]|\\s+(?:habe|hab|ist|läuft|installiert|drauf)(?![\\p{L}]))") to ANY,
        Regex("(?iu)(?<![\\p{L}])(?:modell|sprachmodell|ki-modell)\\s+([\\p{L}-]+)") to setOf(OwnerUnderstandingLexicon.Kind.MODEL),
        Regex("(?iu)(?<![\\p{L}])([\\p{L}-]+)\\s+(?=\\d|e[24]b(?![\\p{L}]))") to setOf(OwnerUnderstandingLexicon.Kind.MODEL),
        Regex("(?iu)(?<![\\p{L}])([\\p{L}-]+)[\\s-]+(?:modell|laufzeit)(?![\\p{L}])") to setOf(OwnerUnderstandingLexicon.Kind.MODEL),
        Regex("(?iu)^(?:\\P{L}*\\p{L}+[,:]\\s+)?(?:bitte\\s+)?(?:prüfe?|überprüfe?|checke?|zeige?|miss|messe|" +
            "wie\\s+(?:voll|viel|hoch|ist|steht|schaut)|wieviel|mach(?:e|st)?|erstelle?|nimm)\\s+(.+)$") to
            setOf(OwnerUnderstandingLexicon.Kind.FEATURE)
    )
    private val messageSlots: List<Pair<Regex, Set<OwnerUnderstandingLexicon.Kind>>> = listOf(
        Regex("(?iu)(?<![\\p{L}])(?:in|auf|per|via|über|ueber)\\s+([\\p{L}-]+)") to setOf(OwnerUnderstandingLexicon.Kind.APP),
        Regex("(?iu)(?<![\\p{L}])([\\p{L}-]+)[\\s-]+nachricht(?![\\p{L}])") to setOf(OwnerUnderstandingLexicon.Kind.APP)
    )
    private val addressSlot = Regex("(?iu)^\\s*(?:(?:hey|hallo|ok|okay|servus)\\s+)?([\\p{L}-]{4,20})(?=\\s*[,:!]|\\s+\\p{L}|\\s*$)")

    fun interpret(utterance: String, lexicon: OwnerUnderstandingLexicon, assistantName: String? = null): Result {
        if (utterance.length !in 3..MAX_LENGTH || utterance.any { it.isISOControl() }) return Result.Unchanged
        val original = OwnerRouteSignature.of(utterance, assistantName)
        if (OwnerRouteSignature.resolved(original)) return Result.Unchanged
        if (verbatimCommand.containsMatchIn(utterance.trim())) return Result.Unchanged

        // Everything after the first colon and everything in quotes is the owner's own wording.
        val colon = utterance.indexOf(':').takeIf { it >= 0 && !addressOnlyBefore(utterance, it) } ?: utterance.length
        val head = utterance.substring(0, colon)
        val protectedRanges = quoted.findAll(head).map { it.range }.toList()
        val tokens = word.findAll(head).map { Token(it.value, it.range.first, it.range.last + 1) }
            .filter { token -> protectedRanges.none { token.start >= it.first && token.end - 1 <= it.last } }.toList()
        if (tokens.isEmpty()) return Result.Unchanged

        val message = messageCommand.containsMatchIn(head.trim())
        val slots = HashMap<Int, Set<OwnerUnderstandingLexicon.Kind>>()
        fun mark(range: IntRange, kinds: Set<OwnerUnderstandingLexicon.Kind>) = tokens.forEachIndexed { index, token ->
            if (token.start >= range.first && token.end - 1 <= range.last) slots[index] = (slots[index] ?: emptySet()) + kinds
        }
        (if (message) messageSlots else slotPatterns).forEach { (pattern, kinds) ->
            pattern.findAll(head).forEach { match -> match.groups[1]?.let { mark(it.range, kinds) } }
        }
        addressSlot.find(head)?.groups?.get(1)?.let { mark(it.range, setOf(OwnerUnderstandingLexicon.Kind.ASSISTANT)) }

        val changes = ArrayList<Change>()
        var question: Triple<Token, Scored, Scored>? = null
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val next = tokens.getOrNull(index + 1)
            // A name the recogniser split into two words („Ter Mux“, „Schi Suku“): only an exact known spelling counts.
            // Outside a name slot two ordinary words are never read as one name („was App“).
            if (next != null && !message && head.substring(token.end, next.start).isBlank() &&
                !lexicon.isName(token.text) && !lexicon.isName(next.text) &&
                (slots[index] != null || slots[index + 1] != null ||
                    (!lexicon.isKnownWord(token.text) && !lexicon.isKnownWord(next.text)))) {
                val joined = OwnerUnderstandingLexicon.letters(token.text + next.text)
                val allowed = (slots[index] ?: emptySet()) + (slots[index + 1] ?: emptySet())
                val entry = lexicon.entries.firstOrNull { e ->
                    (e.kind in allowed || e.distinctive) && (e.words.size == 1 && e.key == joined ||
                        e.heardAs.any { OwnerUnderstandingLexicon.letters(it) == joined && ' ' in it })
                }
                if (entry != null) {
                    changes += Change(token.start, next.end, head.substring(token.start, next.end), entry.name)
                    index += 2
                    continue
                }
            }
            // In a message command only the app slot of the command head is a name; the rest is the owner's text.
            if (message && slots[index] == null) { index++; continue }
            val decision = decide(token, slots[index], head, lexicon, splitSuffix = false)
                ?: decide(token, slots[index], head, lexicon, splitSuffix = true)
            when {
                decision == null -> Unit
                decision.second == null -> changes += Change(token.start, token.end, token.text,
                    decision.first.entry.name + decision.third)
                question == null -> question = Triple(token, decision.first, decision.second!!)
            }
            index++
        }

        fun rebuilt(extra: Change? = null): String {
            val all = (changes + listOfNotNull(extra)).sortedBy { it.from }
            val out = StringBuilder()
            var at = 0
            all.forEach { change -> out.append(utterance, at, change.from).append(change.replacement); at = change.to }
            return out.append(utterance, at, utterance.length).toString()
        }

        question?.let { (token, first, second) ->
            val options = listOf(first.entry.name, second.entry.name)
            return Result.Clarify("Meinst du ${options[0]} oder ${options[1]}?", token.text, options,
                options.map { rebuilt(Change(token.start, token.end, token.text, it)) })
        }
        if (changes.isEmpty()) return Result.Unchanged
        val repaired = rebuilt()
        if (repaired == utterance) return Result.Unchanged
        // A statement or question stays what it was: only a command-shaped utterance may reach a command lane.
        val after = OwnerRouteSignature.of(repaired, assistantName)
        if (OwnerRouteSignature.lane(after) != OwnerRouteSignature.lane(original) && OwnerRouteSignature.acts(after) &&
            !commandShaped(utterance, assistantName)) return Result.Unchanged
        return Result.Repaired(repaired, changes.sortedBy { it.from }.map { Repair(it.heard, it.replacement) })
    }

    /** „Jarvis: öffne …“ – a colon right after the address is punctuation, not the start of dictated text. */
    private fun addressOnlyBefore(utterance: String, colon: Int): Boolean =
        word.findAll(utterance.substring(0, colon)).count() <= 1 && colon < 24

    fun commandShaped(utterance: String, assistantName: String? = null): Boolean {
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName)
            .replace(Regex("^\\p{L}+[,:]\\s+"), "").trim()
        if (imperative.containsMatchIn(text)) return true
        if (politeStart.containsMatchIn(text)) return verbFinal.containsMatchIn(text)
        return verbFinal.containsMatchIn(text) && !OwnerAppLookupIntent.hasNonCommandGrammar(text) &&
            !text.trimEnd().endsWith("?")
    }

    /**
     * Null: leave the token. Otherwise the best candidate, the close second when a question is needed (else null)
     * and a suffix that was split off the heard token („Thermuxapp“ → „Termux“ + „ App“).
     */
    private fun decide(token: Token, slot: Set<OwnerUnderstandingLexicon.Kind>?, head: String,
                       lexicon: OwnerUnderstandingLexicon, splitSuffix: Boolean): Triple<Scored, Scored?, String>? {
        val heard = token.text
        if (negation.matches(heard)) return null
        // A valid German word is this name only in its listed context („Gamma 4“ next to a model word).
        lexicon.entries.firstOrNull { entry ->
            entry.contextual.any { it.word.equals(heard, ignoreCase = true) && it.context.containsMatchIn(head) }
        }?.let { return Triple(Scored(it, 1.0), null, "") }
        if (lexicon.isKnownWord(heard)) return null
        var core = heard
        var suffix = ""
        if (splitSuffix) {
            // „Thermuxapp“: the recogniser glued the name to the word App. An ordinary word in front is left alone.
            if (heard.length < 7 || !heard.endsWith("app", ignoreCase = true)) return null
            core = heard.dropLast(3).trimEnd('-')
            if (lexicon.isKnownWord(core) && !lexicon.isName(core)) return null
            suffix = " App"
        }
        val coreLetters = OwnerUnderstandingLexicon.letters(core)
        if (coreLetters.length < 3) return null
        val inSlot = slot != null || suffix.isNotEmpty()
        val kinds = slot ?: if (suffix.isNotEmpty()) ANY else emptySet()
        val scored = lexicon.entries.asSequence()
            .filter { it.words.size == 1 && (it.kind in kinds || it.distinctive) }
            .mapNotNull { entry ->
                if (entry.key == coreLetters) return@mapNotNull Scored(entry, 1.0)
                var best = GermanPhonetics.similarity(core, entry.name)
                entry.heardAs.forEach { spelling ->
                    if (' ' in spelling) return@forEach
                    best = maxOf(best, if (OwnerUnderstandingLexicon.letters(spelling) == coreLetters) 1.0
                        else GermanPhonetics.similarity(core, spelling) * 0.97)
                }
                // Three letters are too little evidence for anything but the same sound („Aku“ → Akku).
                if (coreLetters.length < 4 && GermanPhonetics.fold(core) != GermanPhonetics.fold(entry.name)) return@mapNotNull null
                if (lexicon.mentionedRecently(entry)) best = minOf(1.0, best + 0.03)
                Scored(entry, best).takeIf { best >= CLARIFY_MIN }
            }.sortedByDescending { it.score }.toList()
        val top = scored.firstOrNull() ?: return null
        val second = scored.getOrNull(1)
        val freeOnly = !inSlot || top.entry.kind !in kinds
        val accept = if (freeOnly) ACCEPT_FREE else ACCEPT_IN_SLOT
        if (top.score >= accept && (second == null || top.score - second.score >= MARGIN)) return Triple(top, null, suffix)
        // A question needs a real name slot and two plausible names; everything else is passed on unchanged.
        if (inSlot && second != null && top.entry.kind in kinds && second.entry.kind in kinds &&
            top.score >= CLARIFY_MIN && second.score >= CLARIFY_MIN) return Triple(top, second, suffix)
        return null
    }

    /** The owner's answer to a clarifying question: the option it names, or null when it names none or both. */
    fun chosenOption(answer: String, options: List<String>): Int? {
        val text = answer.trim().trimEnd('.', '!', '?').lowercase(Locale.GERMAN)
        if (text.length > 80) return null
        val ordinal = when {
            Regex("(?iu)^(?:(?:das|die|den|der)\\s+)?(?:erste\\p{L}*|1|eins)$").matches(text) -> 0
            Regex("(?iu)^(?:(?:das|die|den|der)\\s+)?(?:zweite\\p{L}*|letztere\\p{L}*|2|zwei)$").matches(text) -> 1
            else -> null
        }
        if (ordinal != null) return ordinal.takeIf { it < options.size }
        val words = word.findAll(text).map { it.value }.toList()
        val named = options.indices.filter { index ->
            val option = options[index].lowercase(Locale.GERMAN)
            text.contains(option) || words.any { GermanPhonetics.similarity(it, option) >= 0.93 }
        }
        return named.singleOrNull()
    }
}

/**
 * State of the understanding layer for one owner-turn intake: the open clarifying question and the notice that a
 * repair happened, which the next answer states once. Main-thread only; nothing is persisted.
 */
internal class OwnerUnderstandingIntake(private val clock: () -> Long = System::currentTimeMillis) {
    sealed interface Decision {
        /** Route [text]; [changed] when it differs from what was heard. */
        data class Proceed(val text: String, val changed: Boolean) : Decision
        /** Answer with [question] and wait for the owner's choice; nothing else runs. */
        data class Ask(val question: String) : Decision
    }

    private class Pending(val session: String, val options: List<String>, val candidates: List<String>,
                          val heard: String, val at: Long)

    private var pending: Pending? = null
    private var notice: Triple<String, String, Long>? = null

    /** Test seam and kill switch: with false every utterance is passed on exactly as heard. */
    var enabled: Boolean = true

    fun onOwnerTurn(sessionId: String, heard: String, assistantName: String?,
                    lexicon: () -> OwnerUnderstandingLexicon): Decision {
        if (!enabled) return Decision.Proceed(heard, false)
        val open = pending
        pending = null
        if (open != null && open.session == sessionId && clock() - open.at <= PENDING_MILLIS) {
            OwnerUnderstanding.chosenOption(heard, open.options)?.let { choice ->
                notice = Triple(sessionId, "Ich habe „${open.heard}“ als ${open.options[choice]} verstanden.", clock())
                return Decision.Proceed(open.candidates[choice], true)
            }
        }
        return when (val result = runCatching { OwnerUnderstanding.interpret(heard, lexicon(), assistantName) }
            .getOrDefault(OwnerUnderstanding.Result.Unchanged)) {
            OwnerUnderstanding.Result.Unchanged -> Decision.Proceed(heard, false)
            is OwnerUnderstanding.Result.Repaired -> {
                notice = Triple(sessionId, result.notice, clock())
                Decision.Proceed(result.text, true)
            }
            is OwnerUnderstanding.Result.Clarify -> {
                pending = Pending(sessionId, result.options, result.candidates, result.heard, clock())
                Decision.Ask(result.question)
            }
        }
    }

    /** [answer] with the repair notice in front, once, for the session and turn the repair belongs to. */
    fun withNotice(sessionId: String, answer: String): String {
        val open = notice ?: return answer
        notice = null
        if (open.first != sessionId || clock() - open.third > NOTICE_MILLIS || answer.isBlank()) return answer
        return open.second + "\n" + answer
    }

    fun clear() { pending = null; notice = null }

    companion object {
        const val PENDING_MILLIS = 120_000L
        const val NOTICE_MILLIS = 120_000L
    }
}
