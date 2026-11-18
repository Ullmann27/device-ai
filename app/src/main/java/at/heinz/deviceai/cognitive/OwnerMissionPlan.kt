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
    private val laneWord = Regex("(?iu)(?:pdf|nachricht|schlagzeil|news|meldung|weltgeschehen|speicher|sichere|export|mail|datei|word|docx)")
    private val openIt = Regex("(?iu)^(?:öffne|öffnen|zeig|zeige)\\s+(?:mir\\s+)?(?:es|sie|ihn|das|die|diese|dieses|" +
        "(?:die|diese)\\s+(?:gefundene\\s+|gespeicherte\\s+|neue\\s+)?(?:datei|pdf|pdf-datei)|" +
        "(?:das|dieses)\\s+(?:gefundene\\s+|gespeicherte\\s+|neue\\s+)?(?:pdf|dokument|ergebnis))" +
        "(?:\\s+(?:mir|bitte|mal|gleich|an|auf))*$|^mach(?:e)?\\s+(?:es|sie|ihn|das|die|die\\s+datei|das\\s+pdf)\\s+(?:bitte\\s+)?auf$")
    private val findRecipes = setOf(OwnerTaskChainRecipes.NEWEST_FILE, OwnerTaskChainRecipes.FILE_SEARCH)
    private val counts = listOf("zwei", "drei", "vier", "fünf", "sechs")

    fun count(steps: Int): String = counts.getOrNull(steps - 2) ?: steps.toString()

    /** „… und öffne es“ before it is known what „es“ is; [assemble] turns it into [OwnerMissionStep.OpenFound] or [OwnerMissionStep.OpenSaved]. */
    private data class OpenIt(override val order: String) : OwnerMissionStep {
        override val label get() = "öffnen"
        override val code get() = "OPEN_IT"
        override val source: Int? get() = null
    }

    /** The plan for [utterance], or null when it is not a clear sequence of at least two lane orders. */
    fun plan(utterance: String, assistantName: String? = null): OwnerMissionPlan? {
        if (utterance.length !in 16..MAX_CHARS || utterance.any { it.isISOControl() }) return null
        val said = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trim().trimEnd('.', '!', '?', ' ')
        if (!laneWord.containsMatchIn(said) || ':' in said || quote.containsMatchIn(said) || blocked.containsMatchIn(said)) return null
        val cuts = connector.findAll(said).filter { it.range.first > 0 && it.range.last < said.length - 1 }.toList()
        if (cuts.isEmpty() || cuts.size >= MAX_FRAGMENTS) return null
        // A sentence one lane takes as a whole today is that lane's single order, however it could be cut.
        if (singleOrder(said, assistantName)) return null
        // Fragment i is said[starts[i], ends[i]); a part of the plan is one fragment or several with their connectors.
        val starts = listOf(0) + cuts.map { it.range.last + 1 }
        val ends = cuts.map { it.range.first } + said.length
        val parts = starts.size
        fun part(from: Int, to: Int): OwnerMissionStep? = recognise(said.substring(starts[from], ends[to - 1]), assistantName)
        // The finest cut in which every part is a lane order. Of two equally fine cuts the one that ends each order at
        // the first connector where it is complete: „Öffne das PDF Argon4, fass Seite 2 zusammen, …“ opens „Argon4“,
        // not a file called „Argon4, fass Seite 2 zusammen“.
        val best = arrayOfNulls<List<OwnerMissionStep>>(parts + 1)
        best[parts] = emptyList()
        for (from in parts - 1 downTo 0) {
            var found: List<OwnerMissionStep>? = null
            for (to in from + 1..parts) {
                val rest = best[to] ?: continue
                if (found != null && rest.size + 1 <= found.size) continue
                val step = part(from, to) ?: continue
                found = listOf(step) + rest
            }
            best[from] = found
        }
        val cut = best[0]?.takeIf { it.size in 2..MAX_STEPS + 1 } ?: return null
        val steps = assemble(cut)?.takeIf { it.size in 2..MAX_STEPS } ?: return null
        // A sentence the send or the computer lane takes as a whole stays theirs: its tail may be message text.
        if (OwnerSendIntentParser.parse(said, assistantName) != null) return null
        if (OwnerComputerIntentParser.parse(said) != OwnerComputerIntentParser.Result.NotComputerCommand) return null
        if (OwnerDeviceRequestSafety.inspect(said).roleManipulation) return null
        return OwnerMissionPlan(utterance.trim(), steps)
    }

    /**
     * true = one of the lanes' whole-sentence parsers takes [said] as it is („Fass Seite 2 zusammen und speichere es
     * als Word“, „Fass mir die in Word zusammen und speichere sie als PDF“). The task chains' recipes are not asked:
     * they recognise the beginning of a sentence and ignore its tail.
     */
    private fun singleOrder(said: String, assistantName: String?): Boolean =
        OwnerPageJobCommand.parse(said, assistantName) != null || OwnerLibraryCommand.parseOpen(said, assistantName) != null ||
            OwnerNewsCommand.parse(said, assistantName) != null || OwnerExportCommand.parse(said, assistantName) != null ||
            OwnerShareCommand.parse(said, assistantName) != null

    /**
     * The lane order [text] is when it is said alone, tried in the Activity's routing order (document desk, send lane,
     * computer lane, work tools, task chains); null when no lane of a mission takes it.
     */
    fun recognise(text: String, assistantName: String? = null): OwnerMissionStep? {
        val order = text.trim().replace(leadIn, "").trim().trimEnd('.', '!', '?', ' ')
        if (order.length < 6) return null
        when (val page = OwnerPageJobCommand.parse(order, assistantName)) {
            is OwnerPageJobCommand.Result.Order -> return OwnerMissionStep.PageSave(order, page.page, page.format, page.name)
            OwnerPageJobCommand.Result.SeveralPages -> return null
            null -> Unit
        }
        OwnerLibraryCommand.parseOpen(order, assistantName)?.let {
            return OwnerMissionStep.OpenPdf(order, it.query, it.openWhenClear, it.folderHint)
        }
        if (OwnerSendIntentParser.parse(order, assistantName) != null) return null
        if (OwnerComputerIntentParser.parse(order) != OwnerComputerIntentParser.Result.NotComputerCommand) return null
        OwnerNewsCommand.parse(order, assistantName)?.let { return OwnerMissionStep.News(order, it.topicLabel) }
        OwnerExportCommand.parse(order, assistantName)?.let { return OwnerMissionStep.Export(order, it.format, it.name) }
        OwnerShareCommand.parse(order, assistantName)?.let { share ->
            // Without „Mail“ and without an address the channel is not said: not a step of a plan.
            return if (share.mailNamed || share.address != null) OwnerMissionStep.Mail(order, share.address, share.recipientName) else null
        }
        OwnerTaskChainRecipes.recognise(order, assistantName)?.let { plan ->
            return if (plan.recipe in findRecipes) OwnerMissionStep.FindFile(order, plan) else null
        }
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
                    val opens = part.open || parts.getOrNull(position + 1) is OwnerMissionStep.PageSave
                    if (opens == part.open) part else reworded(part.copy(open = true)) ?: return null
                }
                is OwnerMissionStep.PageSave -> part.copy(source = (index - 1).takeIf {
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
            is OwnerMissionStep.Export -> read is OwnerMissionStep.Export && read.format == step.format && read.name == step.name
            is OwnerMissionStep.Mail -> read is OwnerMissionStep.Mail && read.address == step.address && read.recipientName == step.recipientName
            else -> true
        }
        if (!same) return null
        return when (step) {
            is OwnerMissionStep.OpenPdf -> step.copy(order = order)
            is OwnerMissionStep.PageSave -> step.copy(order = order)
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
        return if ('@' in whom) Change.Recipient(whom, null) else Change.Recipient(null, whom)
    }
}
