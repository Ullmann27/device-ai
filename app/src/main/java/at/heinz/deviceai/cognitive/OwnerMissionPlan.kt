package at.heinz.deviceai.cognitive

import at.heinz.deviceai.sending.OwnerSendIntentParser
import java.util.Locale

/**
 * One sub-order of an owner mission („Auftragsfolge“): a sentence one existing owner lane understands when it is said
 * alone, with the arguments that lane's own parser took out of it. [order] is exactly what the lane is handed.
 */
internal sealed interface OwnerMissionStep {
    val order: String
    /** What the step does, for the spoken plan, the work window and the report. */
    val label: String
    /** Stable form for the regression sets: lane and arguments. */
    val code: String
    /** Index of the earlier step whose real result this one works on; null = none (or the chat's existing result). */
    val source: Int?

    /** „Öffne das PDF Argon4“ – document desk. [open] false: a pure search that shows the hits. */
    data class OpenPdf(override val order: String, val query: String, val open: Boolean, val folderHint: String?,
                       override val source: Int? = null) : OwnerMissionStep {
        override val label get() = "das PDF „$query“ suchen" + if (open) " und öffnen" else ""
        override val code get() = (if (open) "OPEN:" else "FIND:") + query
    }

    /** „Fass Seite 2 zusammen und speichere es als Word“ – document desk. [format] null: the desk asks for it. */
    data class PageSave(override val order: String, val page: Int, val format: OwnerExportCommand.Format?, val name: String?,
                        override val source: Int? = null) : OwnerMissionStep {
        val pageName get() = if (page == OwnerPageReference.LAST) "die letzte Seite" else "Seite $page"
        override val label get() = "$pageName zusammenfassen und " + (format?.let { "als ${it.label} " } ?: "") + "speichern"
        override val code get() = "PAGE:" + (if (page == OwnerPageReference.LAST) "LAST" else page.toString()) + ":" + (format?.name ?: "ASK")
    }

    /**
     * „Analysiere Seite zwei und fasse sie zusammen“ – document desk: the page is read and summarised, the summary is
     * shown in the chat as a result and NOT saved. Only a step of a sequence ([OwnerPageSummaryCommand]).
     */
    data class PageSummary(override val order: String, val page: Int, override val source: Int? = null) : OwnerMissionStep {
        val pageName get() = if (page == OwnerPageReference.LAST) "die letzte Seite" else "Seite $page"
        override val label get() = "$pageName zusammenfassen"
        override val code get() = "PAGESUM:" + if (page == OwnerPageReference.LAST) "LAST" else page.toString()
    }

    /** „Fass mir die Nachrichten von heute zusammen“ – work tools. */
    data class News(override val order: String, val topic: String?, override val source: Int? = null) : OwnerMissionStep {
        override val label get() = "die Nachrichten " + (topic?.let { "zu $it" } ?: "von heute") + " zusammenfassen"
        override val code get() = "NEWS"
    }

    /** „Speichere sie als PDF“ – work tools. */
    data class Export(override val order: String, val format: OwnerExportCommand.Format, val name: String?,
                      override val source: Int? = null) : OwnerMissionStep {
        override val label get() = "als ${format.label} speichern" + (name?.let { " („$it“)" } ?: "")
        override val code get() = "EXPORT:" + format.name
    }

    /** „Schick sie per Mail an …“ – work tools; with the owner's standing Gmail grant the send lane takes it over. */
    data class Mail(override val order: String, val address: String?, val recipientName: String?,
                    override val source: Int? = null) : OwnerMissionStep {
        val to get() = address ?: recipientName.orEmpty()
        override val label get() = "per E-Mail an $to schicken"
        override val code get() = "MAIL:$to"
    }

    /** „Such das neueste PDF in Downloads“ – a recipe of the task chains. */
    data class FindFile(override val order: String, val plan: ChainPlan, override val source: Int? = null) : OwnerMissionStep {
        val newest get() = plan.recipe == OwnerTaskChainRecipes.NEWEST_FILE
        override val label get() = plan.steps.first().label.let { if (newest) "die neueste Datei suchen ($it)" else it }
        override val code get() = "FILE:" + if (newest) "NEWEST" else "SEARCH"
    }

    /** „… und öffne es“ after [FindFile]: the found file is opened by the document desk. */
    data class OpenFound(override val order: String, override val source: Int?) : OwnerMissionStep {
        override val label get() = "die gefundene Datei öffnen"
        override val code get() = "OPEN_FOUND"
    }

    /** „… und öffne die Datei“ after a save: the work tools hand the saved file to the owner's viewer. */
    data class OpenSaved(override val order: String, override val source: Int?) : OwnerMissionStep {
        override val label get() = "die gespeicherte Datei öffnen"
        override val code get() = "OPEN_SAVED"
    }

    /**
     * „Prüf meinen Speicher“, „bereinige die sicheren temporären Dateien“, „sag mir, wie viel Platz ich wieder habe“ –
     * the storage lane. [mode] is the lane's own mode name; only modes that need no selection are steps of a plan.
     */
    data class Storage(override val order: String, val mode: String, override val source: Int? = null) : OwnerMissionStep {
        override val label get() = when (mode) {
            "SAFE_CLEAN" -> "die eindeutig entbehrlichen Dateien entfernen"
            "REPORT" -> "sagen, wie viel Platz wieder frei ist"
            "OPTIMISE" -> "die Last von Continuum prüfen"
            "DEEP_ANALYSE" -> "den Speicher gründlich prüfen"
            else -> "den Speicher prüfen"
        }
        override val code get() = "STORAGE:$mode"
    }
}

/** The owner's sentence as an ordered plan. [request]: the whole sentence as he said it. */
internal class OwnerMissionPlan(val request: String, val steps: List<OwnerMissionStep>) {
    val codes: List<String> get() = steps.map { it.code }

    /** „Ich mache das in drei Schritten: 1. … 2. … 3. …“ */
    fun announcement(lead: String = "Ich mache das in", verb: String = ""): String =
        "$lead ${OwnerMissionPlanner.count(steps.size)} Schritten$verb: " + steps.mapIndexed { index, step ->
            "${index + 1}. ${step.label.replaceFirstChar { it.uppercase(Locale.GERMAN) }}."
        }.joinToString(" ")
}

/**
 * Owner goal (18.11.2026): one sentence that needs several of the existing abilities in sequence – „Öffne das PDF
 * Argon4, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an anna@example.org“ – becomes an
 * ordered plan of sub-orders. This object only splits and recognises; nothing here acts.
 *
 * Fail closed. The sentence is cut at its connectors (comma, „und“, „und dann“, „danach“, „anschließend“, „dann“, a
 * sentence end) and every part must be an order one existing lane parser takes when it is said alone – in the
 * Activity's own routing order, so a part means in a mission what it means alone. One part that no lane knows, a
 * reference without a step that produces what it means, a negation, a quote, a condition, an alternative, a sentence
 * that one lane reads as a whole today (also the send or the computer lane, whose tail may be message text) or more
 * than [MAX_STEPS] steps: no plan, and the sentence keeps today's route. A single order is not a plan either.
 *
 * Since 19.11.2026 the planner also READS a sentence that is no complete plan ([read]): every clause with its order
 * verb ([OwnerVerbLexicon]), its free text (after „dass“, after a colon, inside quotes – only outside of it a
 * negation, a condition or an alternative rules an order out) and what recognised it – a step of today, an order
 * another lane takes alone (sending, another app, the calendar: steps of the next phase), an ability the app does not
 * have yet (reading mails), or nothing. From that reading come the honest partial answer and the check that a model
 * plan drops no order. Adding a step kind for the next phase means: one more [OwnerMissionStep], its branch in
 * [recognise] – the clause is then a [OwnerClauseReading.Step] instead of [OwnerClauseReading.Later] – and its rules in
 * [assemble]; cutting, free text and the veto are already as that step needs them.
 */
internal object OwnerMissionPlanner {
    const val MAX_STEPS = 6
    const val MAX_CHARS = 600
    private const val MAX_FRAGMENTS = 14

    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private const val NEXT = "(?:dann|danach|anschließend|anschliessend|zuletzt|schließlich|schliesslich|zum\\s+schluss|außerdem|ausserdem)"
    private val connector = Regex("(?iu)\\s*[,;]\\s*(?:und\\s+)?(?:$NEXT\\s+)*|\\s*[.!?]\\s+(?:und\\s+)?(?:$NEXT\\s+)*|" +
        "\\s+und\\s+(?:$NEXT\\s+)*|\\s+$NEXT\\s+")
    private val leadIn = Regex("(?iu)^(?:(?:bitte|zuerst|zunächst|zunaechst|erst|als\\s+erstes|und|$NEXT)\\s+)+")
    private val quote = Regex("[\"„“”«»]")
    private val blocked = Regex("(?iu)$S(?:nicht|nichts|nie|niemals|ohne|weder|kein\\p{L}*|oder|falls|wenn|sobald|bevor|nachdem)$E")
    /** No lane order without one of these: an ordinary question with commas never reaches the parsers. */
    private val laneWord = Regex("(?iu)(?:pdf|nachricht|schlagzeil|news|meldung|weltgeschehen|speicher|sichere|export|mail|datei|word|docx|platz)")
    private val openIt = Regex("(?iu)^(?:öffne|öffnen|zeig|zeige)\\s+(?:mir\\s+)?(?:es|sie|ihn|das|die|diese|dieses|" +
        "(?:die|diese)\\s+(?:gefundene\\s+|gespeicherte\\s+|neue\\s+)?(?:datei|pdf|pdf-datei)|" +
        "(?:das|dieses)\\s+(?:gefundene\\s+|gespeicherte\\s+|neue\\s+)?(?:pdf|dokument|ergebnis))" +
        "(?:\\s+(?:mir|bitte|mal|gleich|an|auf|dann|danach|anschließend|anschliessend))*$|^mach(?:e)?\\s+(?:es|sie|ihn|das|die|die\\s+datei|das\\s+pdf)\\s+(?:bitte\\s+)?auf$")
    private val thenAfterVerb = Regex("(?iu)^(\\p{L}+)\\s+$NEXT\\s+")
    private val findRecipes = setOf(OwnerTaskChainRecipes.NEWEST_FILE, OwnerTaskChainRecipes.FILE_SEARCH)
    private val counts = listOf("zwei", "drei", "vier", "fünf", "sechs")

    fun count(steps: Int): String = counts.getOrNull(steps - 2) ?: steps.toString()

    /** „… und öffne es“ before it is known what „es“ is; [assemble] turns it into [OwnerMissionStep.OpenFound] or [OwnerMissionStep.OpenSaved]. */
    private data class OpenIt(override val order: String) : OwnerMissionStep {
        override val label get() = "öffnen"
        override val code get() = "OPEN_IT"
        override val source: Int? get() = null
    }

    /**
     * The plan for [utterance], or null when it is not a clear sequence of at least two lane orders.
     * [noBriefingYet]: this chat holds no news briefing. Then „Fass mir die Nachrichten von heute zusammen und
     * speichere sie als PDF“ – which the export lane reads as one order on the briefing we talk about – has nothing
     * to save and is planned as what it says: fetch the news, then save them. With a briefing in the chat (the
     * default) the sentence stays the export lane's single order as before.
     */
    fun plan(utterance: String, assistantName: String? = null, noBriefingYet: Boolean = false): OwnerMissionPlan? {
        if (utterance.length !in 16..MAX_CHARS || utterance.any { it.isISOControl() }) return null
        val said = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trim().trimEnd('.', '!', '?', ' ')
        if (!laneWord.containsMatchIn(said)) return null
        // Free text – after „dass“, after a colon, inside quotes – is never cut at a connector.
        val free = payloads(said)
        val cuts = cutsOutside(said, free)
        if (cuts.isEmpty() || cuts.size >= MAX_FRAGMENTS) return null
        // A sentence one lane takes as a whole today is that lane's single order, however it could be cut.
        if (singleOrder(said, assistantName) && !(noBriefingYet && onlyNewsExport(said, assistantName))) return null
        // Fragment i is said[starts[i], ends[i]); a part of the plan is one fragment or several with their connectors.
        val starts = listOf(0) + cuts.map { it.range.last + 1 }
        val ends = cuts.map { it.range.first } + said.length
        val parts = starts.size
        fun part(from: Int, to: Int): OwnerMissionStep? = recognise(said.substring(starts[from], ends[to - 1]), assistantName)
        // The finest cut in which every part is a lane order. Of two equally fine cuts the one that ends each order at
        // the first connector where it is complete: „Öffne das PDF Argon4, fass Seite 2 zusammen, …“ opens „Argon4“,
        // not a file called „Argon4, fass Seite 2 zusammen“.
        val best = arrayOfNulls<List<Planned>>(parts + 1)
        best[parts] = emptyList()
        for (from in parts - 1 downTo 0) {
            var found: List<Planned>? = null
            for (to in from + 1..parts) {
                val rest = best[to] ?: continue
                if (found != null && rest.size + 1 <= found.size) continue
                val step = part(from, to) ?: continue
                // „Fass Seite 2 zusammen, speichere es als Word“ is the desk's one order to summarise AND save.
                if (summaryThenSave(step, rest.firstOrNull()?.step)) continue
                found = listOf(Planned(step, starts[from], ends[to - 1])) + rest
            }
            best[from] = found
        }
        val cut = best[0]?.takeIf { it.size in 2..MAX_STEPS + 1 } ?: return null
        // A negation, a condition or an alternative rules an order out where it stands in a COMMAND – not in the free
        // text of a step that carries text („…, dass ich nicht komme“). No step of today carries any, so a sentence with
        // free text is no complete plan yet; [read] says what of it can run.
        for (planned in cut) {
            val inside = free.filter { it.first < planned.end && it.last >= planned.start }
            if (inside.isNotEmpty() && !carriesText(planned.step)) return null
            val command = inside.fold(said.substring(planned.start, planned.end)) { text, range ->
                val from = maxOf(range.first, planned.start) - planned.start
                val until = minOf(range.last, planned.end - 1) - planned.start + 1
                text.replaceRange(from, until, " ".repeat(until - from))
            }
            if (blocked.containsMatchIn(command) || ':' in command || quote.containsMatchIn(command)) return null
        }
        val steps = assemble(cut.map { it.step })?.takeIf { it.size in 2..MAX_STEPS } ?: return null
        // A sentence the send or the computer lane takes as a whole stays theirs: its tail may be message text.
        if (OwnerSendIntentParser.parse(said, assistantName) != null) return null
        if (OwnerComputerIntentParser.parse(said) != OwnerComputerIntentParser.Result.NotComputerCommand) return null
        if (OwnerDeviceRequestSafety.inspect(said).roleManipulation) return null
        return OwnerMissionPlan(utterance.trim(), steps)
    }

    /** A step with where it stands in the sentence: [start] until [end]. */
    private class Planned(val step: OwnerMissionStep, val start: Int, val end: Int)

    /**
     * true = [step] carries free text of the owner (a message). No step kind of today does; the send step of the next
     * phase is listed here, and with that a sentence with „…, dass ich nicht komme“ can be a complete plan.
     */
    private fun carriesText(step: OwnerMissionStep): Boolean = false

    /** The connectors of [said] that cut it: none inside free text, and none in front of the „dass“ that starts it. */
    private fun cutsOutside(said: String, free: List<IntRange>): List<MatchResult> = connector.findAll(said).filter { cut ->
        cut.range.first > 0 && cut.range.last < said.length - 1 &&
            free.none { cut.range.first in it || cut.range.last in it || cut.range.last + 1 == it.first }
    }.toList()

    /** A page summary directly followed by a save of it is never cut in two: together they are [OwnerMissionStep.PageSave]. */
    private fun summaryThenSave(step: OwnerMissionStep, next: OwnerMissionStep?): Boolean =
        step is OwnerMissionStep.PageSummary && next is OwnerMissionStep.Export

    // ── Reading a sentence clause by clause ─────────────────────────────────────────────────────────────────

    private val dass = Regex("(?iu)$S(?:dass|daß)$E")
    private val mailNoun = Regex("(?iu)$S(?:e-?mails?|mails?|posteingang|postfach|inbox)$E")
    /** „eine Mail an …“, „per Mail“: a new mail is written – that is sending, not reading. */
    private val newMail = Regex("(?iu)$S(?:(?:eine|ne|a)\\s+(?:kurze\\s+)?(?:e-?mail|mail)|per\\s+(?:e-?mail|mail))$E")
    private val channelWord = Regex("(?iu)$S(?:whats\\s?app|sms|signal|telegram|threema|messenger|nachricht|bescheid)$E")
    /** After a verb of writing: someone it is said or written to („ihr“, „meinem Vater“, „an Anna“, „Elena“). */
    private val addressee = Regex("(?u)^\\S+\\s+(?:(?:bitte|mal|kurz|dann|noch|schnell)\\s+)*(?:(?i:es|sie|ihn|das|die|den)\\s+)?(?:(?i:ihr|ihm|ihnen)|" +
        "(?i:meine[mnr]|deine[mnr]|unsere[mnr]|seine[mnr]|ihre[mnr])\\s+\\p{L}+|(?i:dem|der|den)\\s+\\p{Lu}\\p{L}+|(?i:an)\\s+\\p{L}+|" +
        "\\p{Lu}[\\p{L}'-]+)$E")
    /** „Fass sie zusammen“, „fasse mir das Wichtigste zusammen“, „erklär es mir“: a summary of what the clause before means. */
    private val summaryOfIt = Regex("(?iu)^(?:(?:fass|fasse|fasst)\\s+(?:(?:mir|uns|bitte|mal|kurz|dann|sie|es|das|die|ihn|alles|" +
        "den\\s+inhalt|das\\s+wichtigste|das\\s+wesentliche|für\\s+mich)\\s+){0,5}zusammen|" +
        "(?:analysier\\p{L}*|erklär\\p{L}*|erläuter\\p{L}*|beschreib\\p{L}*)\\s+(?:(?:mir|uns|bitte|mal|kurz)\\s+)*" +
        "(?:sie|es|das|ihn|den\\s+inhalt)(?:\\s+(?:mir|uns|bitte|mal|kurz))*)$")
    private val writes = setOf(OwnerVerbLexicon.Family.SEND, OwnerVerbLexicon.Family.WRITE)

    /**
     * The free text of [said]: ranges that are never cut at a connector and never read as an order – inside quotes,
     * after a colon (dictated text runs to the end of the sentence), and a „dass“ clause up to the connector in front
     * of the next order („…, dass ich nicht komme, und öffne das PDF Argon4“).
     */
    internal fun payloads(said: String): List<IntRange> {
        val found = ArrayList<IntRange>()
        val marks = quote.findAll(said).map { it.range.first }.toList()
        var mark = 0
        while (mark < marks.size) { found += marks[mark]..(marks.getOrNull(mark + 1) ?: said.lastIndex); mark += 2 }
        fun free(at: Int) = found.any { at in it }
        val colon = said.indices.firstOrNull { at -> said[at] == ':' && !free(at) &&
            !(at in 1 until said.lastIndex && said[at - 1].isDigit() && said[at + 1].isDigit()) }
        if (colon != null) found += colon..said.lastIndex
        for (match in dass.findAll(said)) {
            val start = match.range.first
            if (free(start)) continue
            val end = connector.findAll(said, start).firstOrNull { cut ->
                cut.range.last < said.length - 1 && !free(cut.range.first) &&
                    OwnerVerbLexicon.startsOrder(said.substring(cut.range.last + 1))
            }?.range?.first ?: said.length
            if (end > start) found += start until end
        }
        return found.sortedBy { it.first }
    }

    private class Part(val from: Int, val to: Int, val reading: OwnerClauseReading)
    private class Cut(val unknown: Int, val parts: List<Part>)

    /**
     * [utterance] read clause by clause: what the owner ordered, with which verb, with which free text, and which
     * lane knows each order – also when not every order can run. Null only for input outside the planner's bounds.
     *
     * The clauses are cut at the connectors outside free text. Of all cuts the one with the fewest clauses nobody
     * knows wins, then the finest, then the one that ends each order at the first connector where it is complete –
     * for a complete plan that is the cut of [plan]. [OwnerSentenceReading.kind] says what follows from it.
     */
    fun read(utterance: String, assistantName: String? = null, noBriefingYet: Boolean = false): OwnerSentenceReading? {
        if (utterance.isBlank() || utterance.length > MAX_CHARS || utterance.any { it.isISOControl() }) return null
        val said = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trim().trimEnd('.', '!', '?', ' ')
        if (said.isEmpty()) return null
        val plan = plan(utterance, assistantName, noBriefingYet)
        val free = payloads(said)
        val cuts = cutsOutside(said, free)
        val manipulation = OwnerDeviceRequestSafety.inspect(said).roleManipulation
        if (cuts.size >= MAX_FRAGMENTS) return OwnerSentenceReading(utterance.trim(), said,
            listOf(clause(said, 0, said.length, free, single(said, assistantName))), plan, emptyList(), null, vetoed = true)
        val starts = listOf(0) + cuts.map { it.range.last + 1 }
        val ends = cuts.map { it.range.first } + said.length
        val fragments = starts.size
        val best = arrayOfNulls<Cut>(fragments + 1)
        best[fragments] = Cut(0, emptyList())
        for (from in fragments - 1 downTo 0) {
            var found: Cut? = null
            for (to in from + 1..fragments) {
                val rest = best[to] ?: continue
                val text = said.substring(starts[from], ends[to - 1])
                val reading = (if (to == from + 1) single(text, assistantName) else several(text, assistantName)) ?: continue
                // A complete plan is read exactly as it was planned: steps only.
                if (plan != null && reading !is OwnerClauseReading.Step) continue
                val next = rest.parts.firstOrNull()?.reading
                if (reading is OwnerClauseReading.Step) {
                    if (summaryThenSave(reading.step, (next as? OwnerClauseReading.Step)?.step)) continue
                    // „Analysiere Seite zwei und fasse sie zusammen“ is one order about that page, not two.
                    val pageStep = reading.step is OwnerMissionStep.PageSummary || reading.step is OwnerMissionStep.PageSave
                    if (pageStep && (next as? OwnerClauseReading.Later)?.ability == OwnerClauseAbility.SUMMARY) continue
                }
                val unknown = rest.unknown + if (reading is OwnerClauseReading.Unknown || reading is OwnerClauseReading.NoOrder) 1 else 0
                val better = found == null || unknown < found.unknown ||
                    (unknown == found.unknown && rest.parts.size + 1 > found.parts.size)
                if (better) found = Cut(unknown, listOf(Part(from, to, reading)) + rest.parts)
            }
            best[from] = found
        }
        // What an earlier clause would have produced is gone when that clause cannot run: what builds on it is not
        // offered either. A fresh producer (the news, a named PDF, a file search, the storage) starts anew.
        var broken = false
        val kept = ArrayList<OwnerMissionStep>()
        val clauses = ArrayList<OwnerClause>()
        for (part in best[0]?.parts.orEmpty()) {
            val start = starts[part.from]
            val end = ends[part.to - 1]
            var depends = false
            when (val reading = part.reading) {
                is OwnerClauseReading.Step -> {
                    val step = reading.step
                    val fresh = step is OwnerMissionStep.News || step is OwnerMissionStep.OpenPdf || step is OwnerMissionStep.FindFile ||
                        step is OwnerMissionStep.Storage
                    if (fresh) broken = false
                    depends = broken
                    if (!depends) kept += step
                }
                is OwnerClauseReading.Later -> {
                    depends = broken && reading.ability == OwnerClauseAbility.SUMMARY
                    if (!reading.transparent) broken = true
                }
                is OwnerClauseReading.NotYet, OwnerClauseReading.Unknown -> broken = true
                OwnerClauseReading.NoOrder -> {
                    // Words without a verb after an order nobody carries out belong to that order.
                    val before = clauses.lastOrNull()
                    if (before != null && before.reading !is OwnerClauseReading.Step && before.reading !is OwnerClauseReading.NoOrder) {
                        clauses[clauses.lastIndex] = clause(said, before.range.first, end, free, before.reading, before.dependsOnOpen)
                        continue
                    }
                }
            }
            clauses += clause(said, start, end, free, part.reading, depends)
        }
        val vetoed = manipulation || clauses.any { clause ->
            blocked.containsMatchIn(clause.command) || (clause.payload != null && !carriesText(clause.reading))
        }
        val whole = if (plan != null) null else wholeLane(said, assistantName, clauses.filter { it.reading !is OwnerClauseReading.NoOrder })
        val rest = if (plan != null || kept.isEmpty()) emptyList() else assemble(kept)?.takeIf { it.size <= MAX_STEPS }.orEmpty()
        // Steps that were kept but do not fit together are open as well: nothing of them is offered.
        val settled = if (plan != null || rest.isNotEmpty()) clauses else
            clauses.map { if (it.reading is OwnerClauseReading.Step) it.copy(dependsOnOpen = true) else it }
        return OwnerSentenceReading(utterance.trim(), said, settled, plan, rest, whole, vetoed)
    }

    /** Free text belongs to an order that sends it; anywhere else it is a quote, a note or a question. */
    private fun carriesText(reading: OwnerClauseReading): Boolean =
        reading is OwnerClauseReading.Later && reading.ability == OwnerClauseAbility.SEND

    private fun clause(said: String, start: Int, end: Int, free: List<IntRange>, reading: OwnerClauseReading,
                       depends: Boolean = false): OwnerClause {
        val raw = said.substring(start, end)
        val lead = leadIn.find(raw)?.value?.length ?: 0
        val text = raw.substring(lead)
        val from = start + lead
        // The free text of this clause, relative to it: from its first free character to its last.
        val inside = free.filter { it.first < end && it.last >= from }
        val payload = if (inside.isEmpty()) null else
            (maxOf(inside.minOf { it.first }, from) - from)..(minOf(inside.maxOf { it.last }, end - 1) - from)
        return OwnerClause(text, from until end, OwnerVerbLexicon.leading(text), payload, reading, depends)
    }

    private fun order(text: String): String = text.trim().replace(leadIn, "").trim().trimEnd('.', '!', '?', ' ')

    /** What one fragment between two connectors is. */
    private fun single(text: String, assistantName: String?): OwnerClauseReading {
        val order = order(text)
        recognise(order, assistantName)?.let { return OwnerClauseReading.Step(it) }
        byLane(order, assistantName)?.let { return it }
        byWords(order)?.let { return it }
        return if (OwnerVerbLexicon.startsOrder(order)) OwnerClauseReading.Unknown else OwnerClauseReading.NoOrder
    }

    /** Several fragments with their connectors are one clause only when a lane's own parser takes them as one order. */
    private fun several(text: String, assistantName: String?): OwnerClauseReading? {
        val order = order(text)
        recognise(order, assistantName)?.let { return OwnerClauseReading.Step(it) }
        return byLane(order, assistantName)
    }

    /** An order one of the other lanes takes when it is said alone – asked of that lane's own parser. */
    private fun byLane(order: String, assistantName: String?): OwnerClauseReading.Later? {
        if (order.length < 6) return null
        if (OwnerSendIntentParser.parse(order, assistantName) != null)
            return OwnerClauseReading.Later(OwnerClauseAbility.SEND, transparent = true)
        when (val computer = OwnerComputerIntentParser.parse(order)) {
            OwnerComputerIntentParser.Result.NotComputerCommand -> Unit
            is OwnerComputerIntentParser.Result.Ready -> return OwnerClauseReading.Later(OwnerClauseAbility.APP,
                transparent = computer.intent.goal == ComputerGoal.OPEN || computer.intent.goal == ComputerGoal.OPEN_DOWNLOADS)
            else -> return OwnerClauseReading.Later(OwnerClauseAbility.APP)
        }
        at.heinz.deviceai.briefing.ContinuumBriefingCommands.parse(order, assistantName)?.let { command ->
            return when (command) {
                is at.heinz.deviceai.briefing.ContinuumBriefingCommand.Call -> OwnerClauseReading.Later(OwnerClauseAbility.CALL, transparent = true)
                is at.heinz.deviceai.briefing.ContinuumBriefingCommand.Appointments,
                is at.heinz.deviceai.briefing.ContinuumBriefingCommand.Agenda -> OwnerClauseReading.Later(OwnerClauseAbility.APPOINTMENTS)
                is at.heinz.deviceai.briefing.ContinuumBriefingCommand.Weather -> OwnerClauseReading.Later(OwnerClauseAbility.WEATHER)
                else -> OwnerClauseReading.Later(OwnerClauseAbility.BRIEFING)
            }
        }
        if (at.heinz.deviceai.deepclean.DeepCleanCommands.parse(order, assistantName) != null)
            return OwnerClauseReading.Later(OwnerClauseAbility.STORAGE)
        if (researchWord.containsMatchIn(order) && OwnerResearchCommand.parse(order, assistantName) is OwnerResearchCommand.Outcome.Research)
            return OwnerClauseReading.Later(OwnerClauseAbility.RESEARCH)
        return null
    }

    private val researchWord = Regex("(?iu)$S(?:recherch\\p{L}*|internet|web|netz|google\\p{L}*|googel\\p{L}*|online)$E")

    /** An order no lane parser takes, known by its verb and what it is about. One fragment only. */
    private fun byWords(order: String): OwnerClauseReading? {
        val verb = OwnerVerbLexicon.leading(order) ?: return null
        val command = payloads(order).fold(order) { text, range -> text.replaceRange(range, " ".repeat(range.count())) }
        if (mailNoun.containsMatchIn(command)) {
            // „Schreib Anna eine Mail, dass …“ writes a new one; everything else needs a mail that is already there.
            return if (verb.family in writes && newMail.containsMatchIn(command))
                OwnerClauseReading.Later(OwnerClauseAbility.SEND, transparent = true)
            else OwnerClauseReading.NotYet(OwnerClauseAbility.MAIL)
        }
        if (verb.family in writes && (channelWord.containsMatchIn(command) || addressee.containsMatchIn(command)))
            return OwnerClauseReading.Later(OwnerClauseAbility.SEND, transparent = true)
        if (summaryOfIt.matches(command.trim())) return OwnerClauseReading.Later(OwnerClauseAbility.SUMMARY)
        // „ruf ihn an“: whom is said in the clause before – still an order to call.
        if (verb.family == OwnerVerbLexicon.Family.CALL) return OwnerClauseReading.Later(OwnerClauseAbility.CALL, transparent = true)
        return null
    }

    /** The lane that takes [said] as ONE order today, asked in the Activity's routing order; null = none of them. */
    private fun wholeLane(said: String, assistantName: String?, orders: List<OwnerClause>): String? = when {
        OwnerPageJobCommand.parse(said, assistantName) != null || OwnerLibraryCommand.parseOpen(said, assistantName) != null -> "DESK"
        OwnerSendIntentParser.parse(said, assistantName) != null -> "SEND"
        at.heinz.deviceai.briefing.ContinuumBriefingCommands.parse(said, assistantName) != null -> "BRIEFING"
        at.heinz.deviceai.deepclean.DeepCleanCommands.parse(said, assistantName) != null -> "STORAGE"
        OwnerComputerIntentParser.parse(said) != OwnerComputerIntentParser.Result.NotComputerCommand -> "COMPUTER"
        OwnerNewsCommand.parse(said, assistantName) != null || OwnerExportCommand.parse(said, assistantName) != null ||
            OwnerExportCommand.parseMake(said, assistantName) != null || OwnerShareCommand.parse(said, assistantName) != null -> "WORK"
        OwnerTaskChainRecipes.recognise(said, assistantName) != null -> "CHAIN"
        // Whole-sentence routes behind the planner in the Activity: their sentences are theirs as well.
        WorkRecall.parse(said) != null || WorkDecisionRecall.parse(said) != null -> "RECALL"
        // „Lies diese PDF und fass sie zusammen“: Android's picker and then the summary draft – that route does both
        // orders. With any other second order the picker would open and the rest would be dropped without a word.
        OwnerDocumentIntent.parse(said, assistantName)?.wantsDraft == true && orders.size == 2 &&
            (orders[1].reading as? OwnerClauseReading.Later)?.ability == OwnerClauseAbility.SUMMARY -> "DOCUMENT"
        // „Öffne meine PDF … und zeige mir jeden Arbeitsschritt“: the tail says how that route shows its work – the
        // route takes it off the sentence itself and shows the steps. Nothing is dropped, so it is no second order.
        orders.size == 2 && OwnerDocumentIntent.isPresentationOnly(orders[1].text) &&
            OwnerDocumentIntent.parse(said, assistantName) != null -> "DOCUMENT"
        OwnerChatIntentRouter.route(said) != null -> "LOCAL"
        OwnerInternalNavigationIntent.resolve(said, assistantName) != null -> "NAVIGATION"
        OwnerResearchCommand.parse(said, assistantName) != null -> "RESEARCH"
        else -> null
    }

    /**
     * true = one of the lanes' whole-sentence parsers takes [said] as it is („Fass Seite 2 zusammen und speichere es
     * als Word“, „Fass mir die in Word zusammen und speichere sie als PDF“). The task chains' recipes are not asked:
     * they recognise the beginning of a sentence and ignore its tail.
     */
    private fun singleOrder(said: String, assistantName: String?): Boolean =
        OwnerPageJobCommand.parse(said, assistantName) != null || OwnerLibraryCommand.parseOpen(said, assistantName) != null ||
            OwnerNewsCommand.parse(said, assistantName) != null || OwnerExportCommand.parse(said, assistantName) != null ||
            OwnerExportCommand.parseMake(said, assistantName) != null || OwnerShareCommand.parse(said, assistantName) != null

    /** true = of the whole-sentence parsers only the export lane takes [said], and it names the news. */
    private fun onlyNewsExport(said: String, assistantName: String?): Boolean =
        OwnerExportCommand.parse(said, assistantName)?.aboutNews == true &&
            OwnerPageJobCommand.parse(said, assistantName) == null && OwnerLibraryCommand.parseOpen(said, assistantName) == null &&
            OwnerNewsCommand.parse(said, assistantName) == null && OwnerShareCommand.parse(said, assistantName) == null

    /**
     * The lane order [text] is when it is said alone, tried in the Activity's routing order (document desk, send lane,
     * computer lane, work tools, task chains); null when no lane of a mission takes it.
     */
    fun recognise(text: String, assistantName: String? = null): OwnerMissionStep? {
        // „öffne danach das PDF Argon4“: a sequence word right behind the verb says when, not what.
        val order = text.trim().replace(leadIn, "").trim().trimEnd('.', '!', '?', ' ').let { said ->
            if (OwnerVerbLexicon.startsOrder(said)) said.replace(thenAfterVerb, "$1 ") else said
        }
        if (order.length < 6) return null
        when (val page = OwnerPageJobCommand.parse(order, assistantName)) {
            is OwnerPageJobCommand.Result.Order -> return OwnerMissionStep.PageSave(order, page.page, page.format, page.name)
            OwnerPageJobCommand.Result.SeveralPages -> return null
            null -> Unit
        }
        when (val summary = OwnerPageSummaryCommand.parse(order, assistantName)) {
            is OwnerPageSummaryCommand.Result.Order -> return OwnerMissionStep.PageSummary(order, summary.page)
            OwnerPageSummaryCommand.Result.SeveralPages -> return null
            null -> Unit
        }
        OwnerLibraryCommand.parseOpen(order, assistantName)?.let {
            // „Öffne das PDF Argon4, druck es aus“: a second order is never part of the file's name.
            if (OwnerVerbLexicon.hasFurtherOrder(it.query)) return null
            return OwnerMissionStep.OpenPdf(order, it.query, it.openWhenClear, it.folderHint)
        }
        if (OwnerSendIntentParser.parse(order, assistantName) != null) return null
        if (OwnerComputerIntentParser.parse(order) != OwnerComputerIntentParser.Result.NotComputerCommand) return null
        OwnerNewsCommand.parse(order, assistantName)?.let { return OwnerMissionStep.News(order, it.topicLabel) }
        // „Erstelle eine PDF“ without a pointing word is the same order on what the chat is about (field finding 19.11.2026).
        (OwnerExportCommand.parse(order, assistantName) ?: OwnerExportCommand.parseMake(order, assistantName))
            ?.let { return OwnerMissionStep.Export(order, it.format, it.name) }
        OwnerShareCommand.parse(order, assistantName)?.let { share ->
            // Without „Mail“ and without an address the channel is not said: not a step of a plan.
            return if (share.mailNamed || share.address != null) OwnerMissionStep.Mail(order, share.address, share.recipientName) else null
        }
        OwnerTaskChainRecipes.recognise(order, assistantName)?.let { plan ->
            return if (plan.recipe in findRecipes) OwnerMissionStep.FindFile(order, plan) else null
        }
        storage(order, assistantName)?.let { return it }
        return if (openIt.matches(order)) OpenIt(order) else null
    }

    /**
     * Checks that every step has what it refers to and binds it: „es“ after a search is the found file, „sie“ after
     * the news is the briefing, „es“ after a page summary is the saved summary. A step whose reference has no
     * producer in front of it („Öffne das PDF Argon4 und speichere es als Word“) makes the plan invalid.
     */
    fun assemble(parts: List<OwnerMissionStep>): List<OwnerMissionStep>? {
        val steps = ArrayList<OwnerMissionStep>()
        /** The step whose deliverable „das“ means at this point of the plan, looked up through mails and openings. */
        fun deliverableBefore(index: Int): Int? {
            var at = index - 1
            while (at >= 0) {
                when (val earlier = steps[at]) {
                    is OwnerMissionStep.News, is OwnerMissionStep.PageSave, is OwnerMissionStep.Export -> return at
                    is OwnerMissionStep.Mail, is OwnerMissionStep.OpenSaved -> at = earlier.source ?: return null
                    else -> return null
                }
            }
            return null
        }
        for ((position, part) in parts.withIndex()) {
            val previous = steps.lastOrNull()
            val index = steps.size
            // „Such das PDF Argon4 und öffne es“ is the desk's own order to open what it finds.
            if (part is OpenIt && previous is OwnerMissionStep.OpenPdf) {
                if (!previous.open) steps[index - 1] = reworded(previous.copy(open = true)) ?: return null
                continue
            }
            steps += when (part) {
                is OwnerMissionStep.OpenPdf -> {
                    // A search that the next step needs opened is an order to open it.
                    val opens = part.open || parts.getOrNull(position + 1).let { it is OwnerMissionStep.PageSave || it is OwnerMissionStep.PageSummary }
                    if (opens == part.open) part else reworded(part.copy(open = true)) ?: return null
                }
                is OwnerMissionStep.PageSave -> part.copy(source = (index - 1).takeIf {
                    previous is OwnerMissionStep.OpenPdf || previous is OwnerMissionStep.OpenFound })
                // The summary is shown, not saved: nothing after it can be „the saved file“. Handing the unsaved
                // text on to a save or a mail of the same sequence comes with the typed results of the next phase.
                is OwnerMissionStep.PageSummary -> part.copy(source = (index - 1).takeIf {
                    previous is OwnerMissionStep.OpenPdf || previous is OwnerMissionStep.OpenFound })
                is OwnerMissionStep.Export -> {
                    val source = deliverableBefore(index)
                    if (index > 0 && source == null && !chatResultSoFar(steps)) return null
                    part.copy(source = source)
                }
                is OwnerMissionStep.Mail -> {
                    val source = deliverableBefore(index)
                    if (index > 0 && source == null && !chatResultSoFar(steps)) return null
                    part.copy(source = source)
                }
                is OpenIt -> when (previous) {
                    is OwnerMissionStep.FindFile -> OwnerMissionStep.OpenFound(part.order, index - 1)
                    is OwnerMissionStep.PageSave, is OwnerMissionStep.Export -> OwnerMissionStep.OpenSaved(part.order, index - 1)
                    is OwnerMissionStep.Mail -> {
                        val saved = deliverableBefore(index)?.takeIf { steps[it] !is OwnerMissionStep.News } ?: return null
                        OwnerMissionStep.OpenSaved(part.order, saved)
                    }
                    else -> return null
                }
                else -> part
            }
        }
        return steps
    }

    /** Storage orders that run without a selection; a restriction or an unsure clause keeps the sentence out of a plan. */
    private val storageModes = setOf("ANALYSE", "DEEP_ANALYSE", "SAFE_CLEAN", "REPORT", "OPTIMISE")

    private fun storage(order: String, assistantName: String?): OwnerMissionStep.Storage? {
        val command = at.heinz.deviceai.deepclean.DeepCleanCommands.parse(order, assistantName) ?: return null
        if (command.mode.name !in storageModes || command.keep.isNotEmpty() || command.noDelete || command.unsure) return null
        return OwnerMissionStep.Storage(order, command.mode.name)
    }

    /** Only mails and openings so far, all on the chat's existing result: the next one may refer to it as well. */
    private fun chatResultSoFar(steps: List<OwnerMissionStep>): Boolean =
        steps.all { (it is OwnerMissionStep.Mail || it is OwnerMissionStep.Export) && it.source == null }

    /**
     * [step] with its order worded anew from its arguments (after a correction, or a search that has to open). Null
     * when the lane's own parser does not read exactly these arguments back out of the new wording.
     */
    fun reworded(step: OwnerMissionStep, assistantName: String? = null): OwnerMissionStep? {
        val order = when (step) {
            is OwnerMissionStep.OpenPdf -> "Öffne das PDF ${step.query}" + when (step.folderHint) {
                OwnerLibraryMatcher.FOLDER_DOWNLOAD -> " aus dem Download-Ordner"
                OwnerLibraryMatcher.FOLDER_DOCUMENTS -> " aus dem Dokumente-Ordner"
                else -> ""
            }
            is OwnerMissionStep.PageSave -> (if (step.page == OwnerPageReference.LAST) "Fass die letzte Seite zusammen und speichere sie"
                else "Fass Seite ${step.page} zusammen und speichere es") + formatWords(step.format) + nameWords(step.name)
            is OwnerMissionStep.PageSummary -> if (step.page == OwnerPageReference.LAST) "Fass die letzte Seite zusammen"
                else "Fass Seite ${step.page} zusammen"
            is OwnerMissionStep.Export -> "Speichere das" + formatWords(step.format) + nameWords(step.name)
            is OwnerMissionStep.Mail -> "Schick das per Mail an ${step.to}"
            else -> return step
        }
        val read = recognise(order, assistantName) ?: return null
        val same = when (step) {
            is OwnerMissionStep.OpenPdf -> read is OwnerMissionStep.OpenPdf && read.query == step.query && read.open == step.open &&
                read.folderHint == step.folderHint
            is OwnerMissionStep.PageSave -> read is OwnerMissionStep.PageSave && read.page == step.page && read.format == step.format &&
                read.name == step.name
            is OwnerMissionStep.PageSummary -> read is OwnerMissionStep.PageSummary && read.page == step.page
            is OwnerMissionStep.Export -> read is OwnerMissionStep.Export && read.format == step.format && read.name == step.name
            is OwnerMissionStep.Mail -> read is OwnerMissionStep.Mail && read.address == step.address && read.recipientName == step.recipientName
            else -> true
        }
        if (!same) return null
        return when (step) {
            is OwnerMissionStep.OpenPdf -> step.copy(order = order)
            is OwnerMissionStep.PageSave -> step.copy(order = order)
            is OwnerMissionStep.PageSummary -> step.copy(order = order)
            is OwnerMissionStep.Export -> step.copy(order = order)
            is OwnerMissionStep.Mail -> step.copy(order = order)
            else -> step
        }
    }

    private fun formatWords(format: OwnerExportCommand.Format?): String = when (format) {
        OwnerExportCommand.Format.PDF -> " als PDF"
        OwnerExportCommand.Format.DOCX -> " als Word-Datei"
        null -> ""
    }

    private fun nameWords(name: String?): String = name?.let { " unter dem Namen $it" } ?: ""
}

/**
 * What the owner says to a mission while it runs or waits: a change of the page, the format, the recipient or the
 * named PDF. Recognition only; which step it concerns and what is done again decides the mission. A correction
 * always carries a correction word – „Schick es an Bernd“ is an order of its own.
 */
internal object OwnerMissionCorrection {
    sealed interface Change {
        data class Page(val page: Int) : Change
        data class Format(val format: OwnerExportCommand.Format) : Change
        data class Recipient(val address: String?, val name: String?) : Change {
            val to get() = address ?: name.orEmpty()
        }
        data class File(val query: String) : Change
    }

    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private val marker = Regex("(?iu)$S(?:meinte|gemeint|nein|nee|nö|nicht|sondern|doch|stattdessen|falsch|eigentlich|lieber|statt|" +
        "anstatt|sorry|entschuldigung|korrektur|verhört|versprochen|vertan)$E|${S}ich\\s+meine$E")
    private const val ADDRESS = "[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]{1,63}(?:\\.[A-Za-z0-9-]{1,63})+"
    private const val NAME = "(?:(?:meinen|meinem|meine|meiner|den|die|dem|der)\\s+)?\\p{Lu}[\\p{L}'-]{1,30}(?:\\s+\\p{Lu}[\\p{L}'-]{1,30}){0,2}"
    private val recipient = Regex("(?u)$S[Aa]n\\s+($ADDRESS|$NAME)(?![\\p{L}\\p{N}_@])")
    private val ruledOut = Regex("(?iu)$S(?:nicht|statt|anstatt|anstelle|kein|keine)\\s+(?:mehr\\s+)?$")
    private val common = setOf("nein", "nee", "nö", "ne", "ich", "meinte", "meine", "gemeint", "war", "ist", "nicht", "sondern",
        "doch", "lieber", "stattdessen", "bitte", "falsch", "die", "der", "das", "sorry", "entschuldigung", "eigentlich",
        "statt", "anstatt", "anstelle", "und", "zwar", "natürlich", "also", "ach", "oh", "korrektur", "mail", "e-mail",
        "email", "soll", "sollte", "gehen", "geht", "schick", "schicke", "sende", "es", "sie", "per", "empfänger", "mehr",
        "hab", "habe", "mich", "vertan", "versprochen", "verhört", "halt", "stopp", "stop", "nochmal")

    fun parse(utterance: String, assistantName: String? = null): Change? {
        if (utterance.length !in 4..160 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trimEnd('.', '!', '?', ' ').trim()
        recipient(text)?.let { return it }
        return when (val desk = OwnerDeskCorrection.parse(utterance, assistantName)) {
            is OwnerDeskCorrection.Correction.Page -> Change.Page(desk.page)
            is OwnerDeskCorrection.Correction.Format -> Change.Format(desk.format)
            is OwnerDeskCorrection.Correction.Name -> if (desk.namedPdf) Change.File(desk.fragment) else null
            else -> null
        }
    }

    private fun recipient(text: String): Change.Recipient? {
        if (!marker.containsMatchIn(text)) return null
        val named = recipient.findAll(text).toList()
        if (named.size !in 1..2) return null
        val rest = text.replace(recipient, " ").lowercase(Locale.GERMAN).replace(Regex("[^\\p{L}\\p{N}-]+"), " ")
            .split(' ').map { it.trim('-') }.filter { it.isNotEmpty() }
        if (rest.any { it !in common }) return null
        val meant = named.filter { !ruledOut.containsMatchIn(text.substring(0, it.range.first)) }.singleOrNull() ?: return null
        if (named.size == 2 && named.count { ruledOut.containsMatchIn(text.substring(0, it.range.first)) } != 1) return null
        val whom = meant.groupValues[1]
        if ('@' in whom) return Change.Recipient(whom, null)
        // „an die Datei“, „an Seite 3“: a thing of the order itself is not a person.
        if (whom.lowercase(Locale.GERMAN).split(Regex("\\s+")).any { it in things }) return null
        return Change.Recipient(null, whom)
    }

    private val things = setOf("datei", "seite", "seiten", "pdf", "word", "word-datei", "mail", "e-mail", "nachrichten",
        "zusammenfassung", "dokument", "ordner", "download", "downloads", "anhang", "format")
}
