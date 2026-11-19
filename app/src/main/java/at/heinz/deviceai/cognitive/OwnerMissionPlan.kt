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
    /**
     * What KIND of result of [source] this step works on. The director hands the step exactly that result object of
     * that earlier step – never „the last answer of the chat“ – and refuses to start it when the object is not there.
     */
    val needs: OwnerStepSlot? get() = null

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
        override val needs get() = OwnerStepSlot.OPEN_DOCUMENT.takeIf { source != null }
    }

    /**
     * „Analysiere Seite zwei und fasse sie zusammen“ – document desk: the page is read and summarised, the summary is
     * shown in the chat as a result and NOT saved. Only a step of a sequence ([OwnerPageSummaryCommand]).
     */
    data class PageSummary(override val order: String, val page: Int, override val source: Int? = null) : OwnerMissionStep {
        val pageName get() = if (page == OwnerPageReference.LAST) "die letzte Seite" else "Seite $page"
        override val label get() = "$pageName zusammenfassen"
        override val code get() = "PAGESUM:" + if (page == OwnerPageReference.LAST) "LAST" else page.toString()
        override val needs get() = OwnerStepSlot.OPEN_DOCUMENT.takeIf { source != null }
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
        override val needs get() = OwnerStepSlot.DELIVERABLE.takeIf { source != null }
    }

    /** „Schick sie per Mail an …“ – work tools; with the owner's standing Gmail grant the send lane takes it over. */
    data class Mail(override val order: String, val address: String?, val recipientName: String?,
                    override val source: Int? = null) : OwnerMissionStep {
        val to get() = address ?: recipientName.orEmpty()
        override val label get() = "per E-Mail an $to schicken"
        override val code get() = "MAIL:$to"
        override val needs get() = OwnerStepSlot.DELIVERABLE.takeIf { source != null }
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
        override val needs get() = OwnerStepSlot.FOUND_FILE
    }

    /** „… und öffne die Datei“ after a save: the work tools hand the saved file to the owner's viewer. */
    data class OpenSaved(override val order: String, override val source: Int?) : OwnerMissionStep {
        override val label get() = "die gespeicherte Datei öffnen"
        override val code get() = "OPEN_SAVED"
        override val needs get() = OwnerStepSlot.SAVED_FILE
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

    /**
     * „Schick meinem Vater auf WhatsApp, dass ich mich später melde“ – the send lane, through its own entry for a
     * step of a sequence. [order] is the owner's own clause, word for word: the lane's parser reads recipient and text
     * out of it, decides about indirect speech, asks, confirms and verifies on screen exactly as for an order said
     * alone. The text is the owner's; no result of another step is attached unless he ordered an attachment (none of
     * today's forms does). [recipient]: as said („meinem Vater“, „Anna“), null when the contacts decide where the
     * name ends. [channelFromOpening]: the clause does not say „WhatsApp“ – the owner said „Öffne WhatsApp und …“ in
     * front of it. [corrected]: [recipient] was changed by the owner after the plan was made.
     */
    data class SendChat(override val order: String, val recipient: String?, val channelFromOpening: Boolean = false,
                        val draftOnly: Boolean = false, val corrected: Boolean = false,
                        override val source: Int? = null) : OwnerMissionStep {
        override val label get() = (recipient?.let { "$it " } ?: "") + "auf WhatsApp " +
            if (draftOnly) "einen Entwurf vorbereiten" else "schreiben"
        override val code get() = "SEND:WHATSAPP:" + (recipient ?: "?")
    }

    /** „Öffne Chrome“ – the app lane starts the installed app; done = the lane itself saw the launched app. */
    data class OpenApp(override val order: String, val app: String, val appName: String,
                       override val source: Int? = null) : OwnerMissionStep {
        override val label get() = "$appName öffnen"
        override val code get() = "APP:$app"
    }

    /**
     * „Schau meine E-Mails durch“, „Such die letzte Mail von Anna“ – the Gmail lane's search (read-only, only with a
     * connected account and the reading right). [fromSaid]: the sender as the owner said it („meinem Anwalt“).
     */
    data class MailSearch(override val order: String, val from: String? = null, val about: String? = null,
                          val day: String? = null, val unreadOnly: Boolean = false, val newest: Boolean = false,
                          val fromSaid: String? = null, override val source: Int? = null) : OwnerMissionStep {
        override val label get() = when {
            newest && from != null -> "die letzte Mail von ${fromSaid ?: from} suchen"
            from != null && about != null -> "Mails von ${fromSaid ?: from} zu „$about“ suchen"
            from != null -> "Mails von ${fromSaid ?: from} suchen"
            about != null -> "Mails zu „$about“ suchen"
            day != null -> "die Mails von " + (if (day == "TODAY") "heute" else "gestern") + " suchen"
            unreadOnly -> "die neuen Mails ansehen"
            else -> "die neuesten Mails im Posteingang ansehen"
        }
        override val code get() = "MAILFIND:" + what
        val what get() = when {
            newest && from != null -> "LATEST:$from"
            day != null -> "DAY:$day"
            from != null && about != null -> "FROM:$from|ABOUT:$about"
            from != null -> "FROM:$from"
            about != null -> "ABOUT:$about"
            unreadOnly -> "NEW"
            else -> "INBOX"
        }
    }

    /** „… und lies sie vor“ – the Gmail lane reads the one mail the search in front found. [own]: its own search. */
    data class MailRead(override val order: String, override val source: Int?, val own: MailSearch? = null) : OwnerMissionStep {
        override val label get() = own?.let { it.label.removeSuffix(" suchen") + " suchen und lesen" } ?: "die gefundene Mail lesen"
        override val code get() = "MAILREAD" + (own?.let { ":" + it.what } ?: "")
        override val needs get() = OwnerStepSlot.MAILS.takeIf { source != null }
    }

    /**
     * „… und fass sie zusammen“ – the Gmail lane summarises the mails the search in front found, with the brain the
     * owner's settings allow for mail text. [own]: „Fass meine neuen Mails zusammen“ searches itself first.
     */
    data class MailSummary(override val order: String, override val source: Int?, val own: MailSearch? = null) : OwnerMissionStep {
        override val label get() = if (own == null) "die gefundenen Mails zusammenfassen" else
            (if (own.unreadOnly) "die neuen Mails" else "die neuesten Mails im Posteingang") + " zusammenfassen"
        override val code get() = "MAILSUM" + (own?.let { ":" + it.what } ?: "")
        override val needs get() = OwnerStepSlot.MAILS.takeIf { source != null }
    }

    /**
     * „Schau, welche Termine ich diese Woche habe, und sag mir, ob ich etwas vergessen könnte“ – the briefing lane
     * reads the calendar and checks it with its fixed rules. [what]: the lane's own route code.
     */
    data class Calendar(override val order: String, val what: String, val span: String, val ask: String,
                        override val source: Int? = null) : OwnerMissionStep {
        override val label get() = when (ask) {
            "CHECK" -> "die Termine für $span prüfen"
            "FREE" -> "freie Zeit für $span suchen"
            else -> "die Termine für $span nennen"
        }
        override val code get() = "CAL:$what"
    }

    /** „Recherchiere …“ – the existing web research with the owner's internet consent; done = a source was really read. */
    data class Research(override val order: String, val topic: String, override val source: Int? = null) : OwnerMissionStep {
        override val label get() = "zu „$topic“ recherchieren"
        override val code get() = "RESEARCH:$topic"
    }
}

/** The kinds of result one step can hand to a later one. */
internal enum class OwnerStepSlot {
    /** A text result: the news briefing, a summary, a research dossier – or the content of a file that was saved. */
    DELIVERABLE,
    /** A file this app wrote in this sequence. */
    SAVED_FILE,
    /** A file a search found. */
    FOUND_FILE,
    /** The PDF an earlier step opened in this chat. */
    OPEN_DOCUMENT,
    /** Mails the Gmail lane found or read: references only. */
    MAILS
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
    private val laneWord = Regex("(?iu)(?:pdf|nachricht|schlagzeil|news|meldung|weltgeschehen|speicher|sichere|export|mail|datei|word|docx|platz|" +
        "whats\\s?app|termin|kalender|recherch|posteingang|postfach)")
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

    /** „… fass sie zusammen“ before it is known what „sie“ is; after a mail search [assemble] makes it [OwnerMissionStep.MailSummary]. */
    private data class SummariseIt(override val order: String) : OwnerMissionStep {
        override val label get() = "zusammenfassen"
        override val code get() = "SUM_IT"
        override val source: Int? get() = null
    }

    /** „… lies sie vor“: after a mail search [assemble] makes it [OwnerMissionStep.MailRead]. */
    private data class ReadIt(override val order: String) : OwnerMissionStep {
        override val label get() = "lesen"
        override val code get() = "READ_IT"
        override val source: Int? get() = null
    }

    private val readIt = Regex("(?iu)^(?:lies|lese)\\s+(?:(?:mir|bitte|mal|kurz)\\s+)*(?:sie|es|ihn|die|das)\\s+(?:(?:mir|bitte|mal|kurz)\\s+)*vor$")

    /** A clause that only refers to what the clause in front produced; null when [text] is no such clause. */
    private fun refers(text: String): OwnerMissionStep? {
        val order = order(text)
        return when {
            summaryOfIt.matches(order) -> SummariseIt(order)
            readIt.matches(order) -> ReadIt(order)
            else -> null
        }
    }

    /**
     * A step that produces nothing a later „es“ or „das“ could mean: what follows refers to what stood in front of it.
     * The calendar check is not one of them – „… und speichere das als PDF“ behind it means the calendar's answer, which
     * is no result that is saved; nothing else of the chat is taken in its place.
     */
    private fun transparent(step: OwnerMissionStep): Boolean = step is OwnerMissionStep.SendChat || step is OwnerMissionStep.OpenApp

    /** A step that builds on nothing: it can run whatever stood in front of it. */
    private fun independent(step: OwnerMissionStep): Boolean = transparent(step) || step is OwnerMissionStep.Calendar

    /** true = a plan of this one step is a sequence all the same: two orders of the sentence became one send step. */
    private fun oneStepPlan(step: OwnerMissionStep): Boolean = step is OwnerMissionStep.SendChat && step.channelFromOpening

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
        fun part(from: Int, to: Int): OwnerMissionStep? = said.substring(starts[from], ends[to - 1]).let { text ->
            recognise(text, assistantName) ?: refers(text).takeIf { to == from + 1 }
        }
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
                // „Analysiere Seite zwei und fasse sie zusammen“ is one order about that page, not two.
                if ((step is OwnerMissionStep.PageSummary || step is OwnerMissionStep.PageSave) && rest.firstOrNull()?.step is SummariseIt) continue
                found = listOf(Planned(step, starts[from], ends[to - 1])) + rest
            }
            best[from] = found
        }
        val cut = best[0]?.takeIf { it.size in 2..MAX_STEPS + 1 || (it.size == 1 && oneStepPlan(it.single().step)) } ?: return null
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
        val steps = assemble(cut.map { it.step })?.takeIf { it.size in 2..MAX_STEPS || (it.size == 1 && oneStepPlan(it.single())) }
            ?: return null
        // A sentence the send lane takes as a whole stays that lane's single order: its tail may be message text. Only
        // when the message is delimited free text that ends before the next order („…, dass ich nicht komme, und öffne
        // das PDF Argon4“) the sentence is a sequence that begins with a message.
        if (OwnerSendIntentParser.parse(said, assistantName) != null) {
            val first = cut.first()
            val delimited = first.step is OwnerMissionStep.SendChat && free.any { it.first > first.start && it.last < first.end } &&
                free.none { it.first < first.end && it.last >= first.end }
            if (!delimited) return null
        }
        // A „dass“ text in the middle of the sentence must END at a comma or a sentence end: „…, dass ich komme und
        // öffne die Tür“ may be one message – its second half is never cut off and carried out as an order.
        cut.forEachIndexed { index, planned ->
            val next = cut.getOrNull(index + 1) ?: return@forEachIndexed
            if (planned.step is OwnerMissionStep.SendChat && free.any { it.first >= planned.start && it.last < planned.end } &&
                said.substring(planned.end, next.start).none { it in ",;.!?" }) return null
        }
        // The send lane stands in front of the computer lane in the app's routing: a sentence that begins with a
        // message is never the computer lane's.
        if (cut.first().step !is OwnerMissionStep.SendChat &&
            OwnerComputerIntentParser.parse(said) != OwnerComputerIntentParser.Result.NotComputerCommand) return null
        // A sentence the Gmail lane takes as ONE order („Suche Mails von Anna zu Rechnung und Mahnung“) stays that order.
        if (at.heinz.deviceai.gmail.OwnerMailCommands.standalone(said, assistantName) != null) return null
        if (OwnerDeviceRequestSafety.inspect(said).roleManipulation) return null
        return OwnerMissionPlan(utterance.trim(), steps)
    }

    /** A step with where it stands in the sentence: [start] until [end]. */
    private class Planned(val step: OwnerMissionStep, val start: Int, val end: Int)

    /** true = [step] carries free text of the owner (a message): a sentence with „…, dass ich nicht komme“ can be a plan. */
    private fun carriesText(step: OwnerMissionStep): Boolean = step is OwnerMissionStep.SendChat

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
                var reading = (if (to == from + 1) single(text, assistantName) else several(text, assistantName)) ?: continue
                // A complete plan is read exactly as it was planned: steps only – also a clause that only refers.
                if (plan != null && reading !is OwnerClauseReading.Step && to == from + 1)
                    refers(text)?.let { reading = OwnerClauseReading.Step(it) }
                if (plan != null && reading !is OwnerClauseReading.Step) continue
                val next = rest.parts.firstOrNull()?.reading
                if (reading is OwnerClauseReading.Step) {
                    if (summaryThenSave(reading.step, (next as? OwnerClauseReading.Step)?.step)) continue
                    // „Analysiere Seite zwei und fasse sie zusammen“ is one order about that page, not two.
                    val pageStep = reading.step is OwnerMissionStep.PageSummary || reading.step is OwnerMissionStep.PageSave
                    if (pageStep && (next as? OwnerClauseReading.Later)?.ability == OwnerClauseAbility.SUMMARY) continue
                    if (pageStep && (next as? OwnerClauseReading.Step)?.step is SummariseIt) continue
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
        for (planned in best[0]?.parts.orEmpty()) {
            val start = starts[planned.from]
            val end = ends[planned.to - 1]
            var depends = false
            // „… und fass sie zusammen“ right behind a mail search that can run is that search's summary.
            val bound = (planned.reading as? OwnerClauseReading.Step)?.let { planned } ?: refers(said.substring(start, end))
                ?.takeIf { planned.to == planned.from + 1 && !broken && kept.lastOrNull().let { last ->
                    last is OwnerMissionStep.MailSearch || last is OwnerMissionStep.MailRead } &&
                    (planned.reading == OwnerClauseReading.Unknown || (planned.reading as? OwnerClauseReading.Later)?.ability == OwnerClauseAbility.SUMMARY) }
                ?.let { Part(planned.from, planned.to, OwnerClauseReading.Step(it)) }
            val part = bound ?: planned
            when (val reading = part.reading) {
                is OwnerClauseReading.Step -> {
                    val step = reading.step
                    val fresh = step is OwnerMissionStep.News || step is OwnerMissionStep.OpenPdf || step is OwnerMissionStep.FindFile ||
                        step is OwnerMissionStep.Storage || step is OwnerMissionStep.MailSearch || step is OwnerMissionStep.Research ||
                        (step is OwnerMissionStep.MailSummary && step.own != null) || (step is OwnerMissionStep.MailRead && step.own != null)
                    if (fresh) broken = false
                    // A message, an app or the calendar builds on nothing: it can run whatever stood in front of it.
                    depends = broken && !independent(step)
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
        (reading is OwnerClauseReading.Later && reading.ability == OwnerClauseAbility.SEND) ||
            (reading is OwnerClauseReading.Step && reading.step is OwnerMissionStep.SendChat)

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
            // „Schreibe Lisa eine Nachricht“ – the computer lane would prepare a draft or ask in which app: an order to
            // write someone, and the partial answer has to say so instead of „in einer anderen App suchen“.
            else -> return if (OwnerComputerIntentParser.isMessageOrder(order)) OwnerClauseReading.Later(OwnerClauseAbility.SEND, transparent = true)
                else OwnerClauseReading.Later(OwnerClauseAbility.APP, transparent = computer is OwnerComputerIntentParser.Result.Ready &&
                    (computer.intent.goal == ComputerGoal.OPEN || computer.intent.goal == ComputerGoal.OPEN_DOWNLOADS))
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
        // The app lane answers the sentence itself – it looks the app up, or asks its own question about an app and a
        // message („Öffne Signal und schreibe Lisa eine Nachricht“ → which message, for whom?). Not a sentence the
        // computer lane handed on: that one used to end as the lane's refusal, and reading it here is what replaced it.
        appLaneAnswers(said, assistantName) && !OwnerComputerIntentParser.handsOn(said) -> "APP"
        else -> null
    }

    /** true = the app lane has an answer of its own for [said]; a planning hand-over to the model is none. */
    private fun appLaneAnswers(said: String, assistantName: String?): Boolean =
        when (val intake = OwnerAppLookupIntent.inspect(said, assistantName)) {
            is OwnerAppLookupIntent.Intake.Lookup, is OwnerAppLookupIntent.Intake.Clarify -> true
            is OwnerAppLookupIntent.Intake.Planning -> intake.clarification != null
            null -> false
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
        when (val send = OwnerSendIntentParser.parse(order, assistantName)) {
            // „Schick sie per WhatsApp an Anna“ would send a RESULT (a file, a summary) as a message. No step does that:
            // a message of a sequence carries the owner's own words and nothing else.
            is OwnerSendIntentParser.Result.Order -> return if (sendsAResult.containsMatchIn(order)) null
                else sendStep(order, send.order, fromOpening = false)
            // Negated, to a group or to several people: never carried out, so never a step.
            is OwnerSendIntentParser.Result.Refused -> return null
            null -> Unit
        }
        openThenWrite(order, assistantName)?.let { return it }
        when (val computer = OwnerComputerIntentParser.parse(order)) {
            OwnerComputerIntentParser.Result.NotComputerCommand -> Unit
            is OwnerComputerIntentParser.Result.Ready -> return if (computer.intent.goal == ComputerGoal.OPEN)
                OwnerMissionStep.OpenApp(order, computer.intent.app.name, computer.intent.app.discoveryQuery) else null
            else -> return null
        }
        if (plainGmail.matches(order)) return OwnerMissionStep.OpenApp(order, ComputerApp.GMAIL.name, ComputerApp.GMAIL.discoveryQuery)
        calendar(order, assistantName)?.let { return it }
        mail(order, assistantName)?.let { return it }
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
        research(order, assistantName)?.let { return it }
        return if (openIt.matches(order)) OpenIt(order) else null
    }

    /**
     * [recognise] for a line of a plan, where a clause may only refer to the step in front of it („Fass sie
     * zusammen“, „Lies sie vor“); [assemble] then binds it or rejects the plan.
     */
    internal fun recogniseInSequence(text: String, assistantName: String? = null): OwnerMissionStep? =
        recognise(text, assistantName) ?: refers(text)

    // ── Send, app, mail, calendar and research clauses ──────────────────────────────────────────────────────

    private fun sendStep(order: String, parsed: at.heinz.deviceai.sending.OwnerSendOrder, fromOpening: Boolean): OwnerMissionStep.SendChat {
        // „meinem Vater guten Morgen“ without a separator: the relation in front is who is meant; a name is the contacts' to decide.
        val recipient = parsed.recipient ?: parsed.unsplit?.let { at.heinz.deviceai.sending.OwnerRelation.split(it)?.second }
        return OwnerMissionStep.SendChat(order, recipient?.trim()?.take(60), fromOpening, parsed.draftOnly)
    }

    private val sendsAResult = Regex("(?iu)^(?:schick|schicke|schickst|send|sende|sendest|leite|leit)$E\\s+(?:(?:bitte|mal|dann|kurz|noch)\\s+)*" +
        "(?:es|sie|ihn|das|die|den|dies\\p{L}*)$E")

    private val openChat = Regex("(?iu)^(?:öffne|oeffne|starte|start)\\s+(?:(?:bitte|mal|kurz)\\s+)*whats\\s?app\\s*" +
        "(?:,\\s*(?:und\\s+)?|\\s+und\\s+)(?:(?:$NEXT|bitte)\\s+)*(.+)$", RegexOption.DOT_MATCHES_ALL)
    private val plainGmail = Regex("(?iu)^(?:öffne|oeffne|starte)\\s+(?:(?:bitte|mal|kurz)\\s+)*(?:gmail|g\\s+mail)$")

    /**
     * „Öffne WhatsApp und schreib meinem Vater guten Morgen“: two orders of the sentence, ONE send step – the send lane
     * opens WhatsApp itself, at the chat of the recipient it resolved, and verifies that screen. The clause in front
     * gives the channel the writing clause does not repeat.
     */
    private fun openThenWrite(order: String, assistantName: String?): OwnerMissionStep.SendChat? {
        val rest = openChat.matchEntire(order)?.groupValues?.get(1)?.trim() ?: return null
        if (OwnerVerbLexicon.leading(rest)?.family !in writes) return null
        val command = payloads(rest).fold(rest) { text, range -> text.replaceRange(range, " ".repeat(range.count())) }
        if (mailNoun.containsMatchIn(command)) return null
        val parsed = OwnerSendIntentParser.parse(rest, assistantName, channelGiven = true) as? OwnerSendIntentParser.Result.Order ?: return null
        return sendStep(rest, parsed.order, fromOpening = true)
    }

    private fun calendar(order: String, assistantName: String?): OwnerMissionStep.Calendar? =
        when (val command = at.heinz.deviceai.briefing.ContinuumBriefingCommands.parse(order, assistantName)) {
            is at.heinz.deviceai.briefing.ContinuumBriefingCommand.Agenda ->
                OwnerMissionStep.Calendar(order, command.code, command.span.label, command.ask.name)
            is at.heinz.deviceai.briefing.ContinuumBriefingCommand.Appointments -> OwnerMissionStep.Calendar(order, command.code,
                if (command.day == at.heinz.deviceai.briefing.BriefingDay.TOMORROW) "morgen" else "heute", "LIST")
            else -> null
        }

    private val lookThrough = Regex("^(?:schau|schaue|guck|gucke|sieh|geh|gehe|check|checke|prüf|prüfe|lies|lese|zeig|zeige)\\s+" +
        "(?:mir\\s+)?(?:(?:in|durch)\\s+)?(?:meine[nm]?|die|den|das)\\s+(?:(neuen?|ungelesenen?)\\s+)?" +
        "(?:e-?mails?|mails?|posteingang|postfach)(?:\\s+(?:durch|an|nach))?$")

    /** A clause for the Gmail lane: what its own parser reads when it is said alone, and „Schau meine Mails durch“. */
    private fun mail(order: String, assistantName: String?): OwnerMissionStep? {
        if (!mailNoun.containsMatchIn(order)) return null
        val inbox = OwnerMissionStep.MailSearch(order)
        val unread = OwnerMissionStep.MailSearch(order, unreadOnly = true)
        when (val command = at.heinz.deviceai.gmail.OwnerMailCommands.standalone(order, assistantName)) {
            is at.heinz.deviceai.gmail.OwnerMailCommand.Latest -> {
                val found = OwnerMissionStep.MailSearch(order, from = command.from, newest = true, fromSaid = saidSender(order, command.from))
                return if (command.aloud) OwnerMissionStep.MailRead(order, null, found) else found
            }
            is at.heinz.deviceai.gmail.OwnerMailCommand.Search -> return OwnerMissionStep.MailSearch(order, command.from, command.about,
                command.day?.name, fromSaid = command.from?.let { saidSender(order, it) })
            is at.heinz.deviceai.gmail.OwnerMailCommand.New -> return if (command.aloud) null else unread
            at.heinz.deviceai.gmail.OwnerMailCommand.Important -> return inbox
            is at.heinz.deviceai.gmail.OwnerMailCommand.Summarize -> return when (command.target) {
                at.heinz.deviceai.gmail.OwnerMailCommand.SummaryTarget.IMPORTANT -> OwnerMissionStep.MailSummary(order, null, inbox)
                at.heinz.deviceai.gmail.OwnerMailCommand.SummaryTarget.NEW -> OwnerMissionStep.MailSummary(order, null, unread)
                // „Fass die Mail zusammen“: the mail the search in front found.
                at.heinz.deviceai.gmail.OwnerMailCommand.SummaryTarget.CONTEXT -> if (command.index == null) SummariseIt(order) else null
                else -> null
            }
            is at.heinz.deviceai.gmail.OwnerMailCommand.Read -> return if (command.index == null && !command.thread) ReadIt(order) else null
            // Connecting, drafts, replies, sending, deadlines: orders of the lane itself, no step of a sequence.
            null -> Unit
            else -> return null
        }
        val plain = order.lowercase(Locale.GERMAN).replace(Regex("(?<![\\p{L}])(?:bitte|mal|doch|kurz)(?![\\p{L}])"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val look = lookThrough.matchEntire(plain) ?: return null
        return if (look.groupValues[1].isNotEmpty()) unread else inbox
    }

    /** „meinem Anwalt“ for the sender „Anwalt“ – only for what is said to the owner; the search uses [from]. */
    private fun saidSender(order: String, from: String): String =
        Regex("(?iu)$S((?:mein|unser)\\p{L}*\\s+" + Regex.escape(from) + ")$E").find(order)?.groupValues?.get(1) ?: from

    private fun research(order: String, assistantName: String?): OwnerMissionStep.Research? {
        if (!researchWord.containsMatchIn(order)) return null
        val command = OwnerResearchCommand.parse(order, assistantName) as? OwnerResearchCommand.Outcome.Research ?: return null
        return OwnerMissionStep.Research(order, command.topic)
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
                    is OwnerMissionStep.News, is OwnerMissionStep.PageSave, is OwnerMissionStep.Export, is OwnerMissionStep.MailSummary,
                    is OwnerMissionStep.Research, is OwnerMissionStep.PageSummary -> return at
                    is OwnerMissionStep.Mail, is OwnerMissionStep.OpenSaved -> at = earlier.source ?: return null
                    else -> if (transparent(earlier)) at-- else return null
                }
            }
            return null
        }
        for ((position, part) in parts.withIndex()) {
            // „Öffne WhatsApp“ right in front of a message on WhatsApp: the send step opens the app itself, at the chat.
            if (part is OwnerMissionStep.SendChat && (steps.lastOrNull() as? OwnerMissionStep.OpenApp)?.app == ComputerApp.WHATSAPP.name)
                steps.removeAt(steps.lastIndex)
            // What „es“ and „sie“ mean is looked up past a message, an app and the calendar, which produce nothing to point at.
            val anchor = steps.indexOfLast { !transparent(it) }
            val previous = steps.getOrNull(anchor)
            val index = steps.size
            // „Such das PDF Argon4 und öffne es“ is the desk's own order to open what it finds.
            if (part is OpenIt && previous is OwnerMissionStep.OpenPdf) {
                if (!previous.open) steps[anchor] = reworded(previous.copy(open = true)) ?: return null
                continue
            }
            steps += when (part) {
                is OwnerMissionStep.OpenPdf -> {
                    // A search that the next step needs opened is an order to open it.
                    val opens = part.open || parts.getOrNull(position + 1).let { it is OwnerMissionStep.PageSave || it is OwnerMissionStep.PageSummary }
                    if (opens == part.open) part else reworded(part.copy(open = true)) ?: return null
                }
                is OwnerMissionStep.PageSave -> part.copy(source = anchor.takeIf {
                    previous is OwnerMissionStep.OpenPdf || previous is OwnerMissionStep.OpenFound })
                // The summary is shown, not saved: nothing after it can be „the saved file“ – but a later save or
                // mail of the same sequence gets its text as the typed result of this step.
                is OwnerMissionStep.PageSummary -> part.copy(source = anchor.takeIf {
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
                    is OwnerMissionStep.FindFile -> OwnerMissionStep.OpenFound(part.order, anchor)
                    is OwnerMissionStep.PageSave, is OwnerMissionStep.Export -> OwnerMissionStep.OpenSaved(part.order, anchor)
                    is OwnerMissionStep.Mail -> {
                        val saved = deliverableBefore(index)?.takeIf { steps[it] is OwnerMissionStep.PageSave || steps[it] is OwnerMissionStep.Export }
                            ?: return null
                        OwnerMissionStep.OpenSaved(part.order, saved)
                    }
                    // „Such die letzte Mail von Anna und öffne sie“: the found mail is read.
                    is OwnerMissionStep.MailSearch -> OwnerMissionStep.MailRead(part.order, anchor)
                    else -> return null
                }
                is SummariseIt -> when (previous) {
                    is OwnerMissionStep.MailSearch, is OwnerMissionStep.MailRead -> OwnerMissionStep.MailSummary(part.order, anchor)
                    else -> return null
                }
                is ReadIt -> when (previous) {
                    is OwnerMissionStep.MailSearch -> OwnerMissionStep.MailRead(part.order, anchor)
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
        steps.all { transparent(it) || ((it is OwnerMissionStep.Mail || it is OwnerMissionStep.Export) && it.source == null) }

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
            // „Nicht Papa, Mama“: the other recipient of a message – a name or a relation, never a thing of the order.
            else -> OwnerSendIntentParser.correctedRecipient(text)
                ?.takeIf { whom -> whom.lowercase(Locale.GERMAN).split(Regex("\\s+")).none { it in things } }
                ?.let { Change.Recipient(null, it) }
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
