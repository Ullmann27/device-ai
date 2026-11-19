package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * A plan the model proposes for a multi-step device goal that the owner did not say as a clear sequence („Mach aus
 * Seite 2 vom PDF Argon4 eine Word-Datei und die soll dann per Mail an anna@example.org“). Off by default (the
 * owner's switch for model plans of the task chains).
 *
 * The model's text is data. It must be a short numbered list, every line an order one lane parser takes when it is
 * said alone ([OwnerMissionPlanner.recognise]), the lines must fit together ([OwnerMissionPlanner.assemble]), and
 * every name, address, page, format, folder and topic in the plan must occur in the owner's own request – a model
 * cannot add a recipient, a file or a text. Anything else rejects the whole plan; it is never repaired. An accepted
 * plan is only spoken: the mission starts after the owner's explicit yes, and every step keeps its lane's own gates.
 *
 * A plan may neither add work nor drop it (review findings B1 and B7, 19.11.2026):
 *  - every KIND of step needs its word in the owner's sentence – a save needs a verb of saving or making (or „als
 *    PDF“), an opening a verb of opening or showing, a search a verb of searching, a page summary a verb of
 *    summarising, the storage the same order said by the owner himself. A step whose kind he did not order rejects
 *    the plan;
 *  - every clause of his sentence that starts with an order verb ([OwnerMissionPlanner.read]) must be covered by a
 *    step. If one is not, the result is [Result.Uncovered]: nothing runs by itself, the app says what it cannot do
 *    yet and offers what is left;
 *  - one order runs without a question only when the sentence really is ONE order.
 */
internal object OwnerMissionModelPlan {
    sealed interface Result {
        class Accepted(val plan: OwnerMissionPlan) : Result
        /**
         * The plan itself is valid, but [open] – orders of the owner's sentence – are covered by no step. Nothing
         * runs by itself. [rest]: the steps that may be offered, already bound to each other; empty when what the
         * plan holds builds on the open orders.
         */
        class Uncovered(val open: List<OwnerClause>, val rest: List<OwnerMissionStep>) : Result
        class Rejected(val reason: String) : Result
    }

    const val MAX_CHARS = 1_200
    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private val quote = Regex("[\"„“”«»]")
    private val blocked = Regex("(?iu)$S(?:nicht|nichts|nie|niemals|ohne|weder|kein\\p{L}*|oder|falls|wenn|sobald|bevor|nachdem)$E")
    private val abilities = listOf(
        Regex("(?iu)$S(?:pdf\\p{L}*|seite|seiten)$E"),
        Regex("(?iu)$S(?:nachrichten\\p{L}*|schlagzeilen|news|meldungen)$E"),
        Regex("(?iu)$S(?:speicher\\p{L}*|abspeicher\\p{L}*|word\\p{L}*|word-datei|docx|datei)$E"),
        Regex("(?iu)$S(?:mail|e-mail|email|maile|mailen)$E"))
    private val line = Regex("^(?:\\d{1,2}[.)]|[-–•*])\\s+(.+)$")
    private val noPlan = Regex("(?iu)${S}kein\\s+plan$E")
    private val newsNoun = Regex("(?iu)$S(?:\\p{L}*nachrichten\\p{L}*|schlagzeilen|news|meldungen|weltgeschehen|tagesgeschehen)$E")
    private val wordTarget = Regex("(?iu)$S(?:word|word-datei|word-dokument|docx|wort-datei)$E")
    private val pdfTarget = Regex("(?iu)$S(?:als|in|im|ein|eine|einem|einer|zum|zur)\\s+(?:pdf|pdf-datei|pdf-dokument)$E")
    private val newest = Regex("(?iu)$S(?:neueste[nrs]?|neuste[nrs]?|letzte[nrs]?|jüngste[nrs]?|aktuellste[nrs]?)$E")
    private val mailWord = Regex("(?iu)$S(?:mail|e-mail|email|maile|mailen)$E")

    /** A command about at least two of the lane abilities, without anything that rules an action out. */
    fun wants(utterance: String, assistantName: String? = null): Boolean =
        utterance.length in 24..400 && utterance.none { it.isISOControl() } && ':' !in utterance &&
            !quote.containsMatchIn(utterance) && !blocked.containsMatchIn(utterance) &&
            abilities.count { it.containsMatchIn(utterance) } >= 2 &&
            OwnerUnderstanding.commandShaped(utterance, assistantName) &&
            !OwnerDeviceRequestSafety.inspect(utterance).roleManipulation

    private val orderWords = Regex("(?iu)$S(?:pdf\\p{L}*|seite|seiten|dokument\\p{L}*|datei\\p{L}*|word\\p{L}*|word-datei|word-dokument|docx|" +
        "zusammenfassung\\p{L}*|entwurf\\p{L}*|nachrichten\\p{L}*|schlagzeilen|news|meldungen|mail|e-mail|email|maile|mailen|" +
        "speicher\\p{L}*|abspeicher\\p{L}*|download\\p{L}*)$E")
    /** „Ich suche …“, „ich bräuchte …“: how the owner says an order when he does not use the command form. */
    private val wishForm = Regex("(?iu)${S}ich\\s+(?:such|suche|brauch|brauche|bräuchte|braeuchte|möchte|moechte|will|" +
        "hätte\\s+gern(?:e)?|haette\\s+gern(?:e)?|würde\\s+gern(?:e)?)$E")
    /** A question or a request to explain is answered, never mapped to an order. */
    private val askForm = Regex("(?iu)^\\s*(?:(?:bitte|und|also|sag\\s+mal|kannst\\s+du\\s+mir|könntest\\s+du\\s+mir)[,\\s]+)*" +
        "(?:was|wer|wie|warum|wieso|weshalb|wann|wo|woher|wozu|welche\\p{L}*|erklär\\p{L}*|beschreib\\p{L}*|erzähl\\p{L}*|" +
        "definier\\p{L}*|erläuter\\p{L}*)$E")

    /**
     * Cloud brain only (owner goal 19.11.2026: „dass Claude wirklich jedes Werkzeug bedienen kann“): a sentence that
     * may be ONE order for a lane although no lane parser took it – „Kannst du mir bitte mal das Argon-Dokument
     * aufmachen“, „Ich suche eine PDF, die heißt Argon4 Experimente“. Bounded on purpose, because every yes costs a
     * request: it must name a thing of the lanes, be said as an order or a wish, and rule nothing out.
     */
    fun wantsOrder(utterance: String, assistantName: String? = null): Boolean {
        if (utterance.length !in 12..400 || utterance.any { it.isISOControl() } || ':' in utterance) return false
        if (quote.containsMatchIn(utterance) || blocked.containsMatchIn(utterance) || !orderWords.containsMatchIn(utterance)) return false
        val said = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName)
        if (askForm.containsMatchIn(said)) return false
        return (OwnerUnderstanding.commandShaped(utterance, assistantName) || wishForm.containsMatchIn(said)) &&
            !OwnerDeviceRequestSafety.inspect(utterance).roleManipulation
    }

    /**
     * The instruction for [wantsOrder]: the same closed forms as [prompt], and a list of one line is allowed. Names
     * stay exactly as the owner said them, also when they look like a mishearing – the document desk finds near
     * matches itself, and a „corrected“ name would be something he did not say.
     */
    fun orderPrompt(request: String): String = buildString {
        append("Der Besitzer hat der App einen Auftrag gegeben, den ihre festen Abläufe nicht erkannt haben. Schreibe ihn ")
        append("als höchstens ${OwnerMissionPlanner.MAX_STEPS} Teilaufträge um, in der Reihenfolge der Ausführung; ein einzelner ")
        append("Auftrag ist eine Liste mit einer Zeile. Erlaubt sind nur diese Formen, Wort für Wort:\n")
        append("Such das PDF <Name>\n")
        append("Öffne das PDF <Name>\n")
        append("Fass Seite <Zahl> zusammen und speichere es als <Word oder PDF>\n")
        append("Fass Seite <Zahl> zusammen\n")
        append("Fass mir die Nachrichten von heute zusammen\n")
        append("Speichere das als <PDF oder Word-Datei>\n")
        append("Schick das per Mail an <Adresse oder Name>\n")
        append("Such das neueste PDF in <Downloads oder Dokumente>\n")
        append("Öffne es\n")
        append("Namen, Adressen, Seiten, Formate und Ordner übernimmst du genau so, wie sie im Auftrag stehen – auch wenn ")
        append("ein Name nach einem Hörfehler aussieht: Die Suche findet ähnliche Namen selbst. Füge nichts hinzu, was der ")
        append("Besitzer nicht gesagt hat. „Such das PDF“ sucht nach dem Namen und öffnet nichts; „Öffne das PDF“ nur, ")
        append("wenn er öffnen, aufmachen oder zeigen gesagt hat; „… und speichere es“ nur, wenn er speichern oder eine ")
        append("Datei verlangt hat. Lass keinen Teil des Auftrags weg: Passt ein Teil zu keiner Form, antworte KEIN PLAN.\n")
        append("Antworte nur mit der nummerierten Liste (1. …), ein Teilauftrag je Zeile, ohne Erklärung. ")
        append("Passt der Auftrag zu keiner dieser Formen, antworte nur mit: KEIN PLAN\n")
        append("Auftrag: ").append(request.filterNot { it.isISOControl() }.take(400))
    }

    /**
     * Whether model plans are on. [saved]: the owner's own switch („Modellpläne an/aus“), null while he never set it.
     * Unset, plans are off for the model on the device (as before – every plan costs time and heat there) and on for
     * the cloud brain, which the owner selected deliberately and asked to operate every ability: a plan is one short
     * text request, it is validated like any other, and nothing of it runs before his „Ja“.
     */
    fun switchedOn(saved: Boolean?, cloudBrain: Boolean): Boolean = saved ?: cloudBrain

    /**
     * true = [plan] may run without a question: one step that neither sends nor shares anything, for a sentence that
     * really is ONE order – with a second clause that starts with an order verb it is asked first, whatever the plan
     * says (review finding B1: one accepted line used to run at once and the rest of the sentence was dropped).
     */
    fun runsWithoutQuestion(plan: OwnerMissionPlan, assistantName: String? = null): Boolean =
        plan.steps.size == 1 && plan.steps.single() !is OwnerMissionStep.Mail &&
            (OwnerMissionPlanner.read(plan.request, assistantName)?.orders?.size ?: 2) <= 1

    /** The bounded instruction for a text runtime; the listed forms are the only ones a line may have. */
    fun prompt(request: String): String = buildString {
        append("Zerlege den Auftrag in höchstens ${OwnerMissionPlanner.MAX_STEPS} Teilaufträge, in der Reihenfolge, in der sie ")
        append("ausgeführt werden. Erlaubt sind nur diese Formen, Wort für Wort, mit den Namen, Adressen, Seiten und ")
        append("Formaten aus dem Auftrag – erfinde nichts dazu:\n")
        append("Öffne das PDF <Name>\n")
        append("Fass Seite <Zahl> zusammen und speichere es als <Word oder PDF>\n")
        append("Fass Seite <Zahl> zusammen\n")
        append("Fass mir die Nachrichten von heute zusammen\n")
        append("Speichere das als <PDF oder Word-Datei>\n")
        append("Schick das per Mail an <Adresse oder Name>\n")
        append("Such das neueste PDF in <Downloads oder Dokumente>\n")
        append("Öffne es\n")
        append("Speichern nur, wenn der Auftrag es verlangt; lass keinen Teil des Auftrags weg.\n")
        append("Antworte nur mit der nummerierten Liste (1. …, 2. …), ein Teilauftrag je Zeile, ohne Erklärung. ")
        append("Passt der Auftrag nicht zu diesen Formen, antworte nur mit: KEIN PLAN\n")
        append("Auftrag: ").append(request.filterNot { it.isISOControl() }.take(400))
    }

    /** The plan in [text] when it is completely valid for [request]; otherwise why it was dropped. */
    fun check(request: String, text: String?, assistantName: String? = null, allowSingle: Boolean = false): Result {
        val least = if (allowSingle) 1 else 2
        val body = text?.trim().orEmpty()
        if (body.isEmpty()) return Result.Rejected("kein Plan erhalten")
        if (body.length > MAX_CHARS) return Result.Rejected("der Modellplan ist zu lang")
        if (noPlan.containsMatchIn(body)) return Result.Rejected("das Modell sieht keinen Plan")
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size !in least..OwnerMissionPlanner.MAX_STEPS) return Result.Rejected("der Modellplan hat ${lines.size} Zeilen")
        val parts = lines.mapIndexed { index, raw ->
            val order = line.matchEntire(raw)?.groupValues?.get(1)?.trim()
                ?: return Result.Rejected("Zeile ${index + 1} hält das Listenformat nicht ein")
            if (':' in order || quote.containsMatchIn(order) || blocked.containsMatchIn(order))
                return Result.Rejected("Zeile ${index + 1} ist kein klarer Auftrag")
            OwnerMissionPlanner.recognise(order, assistantName)
                ?: return Result.Rejected("Zeile ${index + 1} ist kein Auftrag, den eine Lenkung kennt")
        }
        val steps = OwnerMissionPlanner.assemble(parts)?.takeIf { it.size in least..OwnerMissionPlanner.MAX_STEPS }
            ?: return Result.Rejected("die Schritte passen nicht aufeinander")
        steps.forEachIndexed { index, step ->
            invented(request, step)?.let { return Result.Rejected("Schritt ${index + 1} nennt $it, das du nicht gesagt hast") }
        }
        // A summary of one page without a save is a step of a sequence; alone it stays the answer in the chat.
        if (steps.size == 1 && steps.single() is OwnerMissionStep.PageSummary)
            return Result.Rejected("eine einzelne Seite ohne Speichern beantworte ich im Gespräch")
        val reading = OwnerMissionPlanner.read(request, assistantName) ?: return Result.Rejected("der Auftrag ist zu lang für einen Plan")
        steps.forEachIndexed { index, step ->
            notOrdered(request, reading, step, steps.getOrNull(index + 1))?.let { return Result.Rejected("Schritt ${index + 1} $it, was du nicht bestellt hast") }
        }
        val (open, rest) = coverage(reading, steps) ?: return Result.Accepted(OwnerMissionPlan(request.trim(), steps))
        return Result.Uncovered(open, rest)
    }

    // ── No added kind of step (B7) ──────────────────────────────────────────────────────────────────────────

    private val saves = setOf(OwnerVerbLexicon.Family.SAVE, OwnerVerbLexicon.Family.CREATE, OwnerVerbLexicon.Family.MAKE)
    private val opens = setOf(OwnerVerbLexicon.Family.OPEN, OwnerVerbLexicon.Family.SHOW, OwnerVerbLexicon.Family.READ)
    private val searches = setOf(OwnerVerbLexicon.Family.SEARCH)
    private val summarises = setOf(OwnerVerbLexicon.Family.SUMMARISE)
    /** „… als PDF“, „… als Word-Datei“: a file is ordered although no verb of saving is said. */
    private val asFile = Regex("(?iu)${S}als\\s+(?:eine?\\s+)?(?:pdf|pdf-datei|pdf-dokument|word|word-datei|word-dokument|docx|wort-datei)$E")
    /** „Wo ist …“, „ich brauche …“: a search without the verb. */
    private val seeks = Regex("(?iu)${S}(?:wo\\s+(?:ist|liegt|steckt|finde)|ich\\s+(?:brauche|bräuchte|braeuchte|such|suche))$E")
    private val summaryNoun = Regex("(?iu)${S}(?:zusammenfassung|kurzfassung|überblick|ueberblick)$E")

    /**
     * What [step] does that the owner's [request] has no word for, or null. A step the next one needs – the PDF a page
     * step reads, the search whose hit is opened – is part of that order and needs no verb of its own.
     */
    private fun notOrdered(request: String, reading: OwnerSentenceReading, step: OwnerMissionStep, next: OwnerMissionStep?): String? {
        val said = OwnerVerbLexicon.families(request)
        fun has(kinds: Set<OwnerVerbLexicon.Family>) = said.any { it in kinds }
        val forPage = next is OwnerMissionStep.PageSave || next is OwnerMissionStep.PageSummary
        return when (step) {
            is OwnerMissionStep.PageSave -> when {
                !has(summarises) && !has(saves) && !summaryNoun.containsMatchIn(request) -> "fasst eine Seite zusammen"
                step.format == null && !has(saves) -> "speichert eine Datei"
                !has(saves) && !asFile.containsMatchIn(OwnerPageJobCommand.heard(request)) -> "speichert eine Datei"
                else -> null
            }
            is OwnerMissionStep.Export ->
                if (has(saves) || asFile.containsMatchIn(OwnerPageJobCommand.heard(request))) null else "speichert eine Datei"
            is OwnerMissionStep.PageSummary ->
                if (has(summarises) || summaryNoun.containsMatchIn(request)) null else "fasst eine Seite zusammen"
            is OwnerMissionStep.OpenPdf -> when {
                forPage -> null
                step.open && !has(opens) -> "öffnet ein PDF"
                !step.open && !has(searches) && !has(opens) && !seeks.containsMatchIn(request) -> "sucht ein PDF"
                else -> null
            }
            is OwnerMissionStep.FindFile ->
                if (next is OwnerMissionStep.OpenFound || has(searches) || seeks.containsMatchIn(request)) null else "sucht eine Datei"
            is OwnerMissionStep.OpenFound, is OwnerMissionStep.OpenSaved -> if (has(opens)) null else "öffnet eine Datei"
            // Checking or cleaning the storage is never the model's idea: the owner's own clause must be that order.
            is OwnerMissionStep.Storage -> if (reading.clauses.any { (it.step as? OwnerMissionStep.Storage)?.mode == step.mode }) null
                else "räumt den Speicher auf oder prüft ihn"
            else -> null
        }
    }

    // ── No dropped order (B1) ───────────────────────────────────────────────────────────────────────────────

    /** true = a step of this kind can be what a clause with [clause]'s verb orders. */
    private fun covers(step: OwnerMissionStep, clause: OwnerClause): Boolean {
        // A clause one of the other lanes takes, or that no lane can do yet, is never „covered“ by a different step.
        when (val reading = clause.reading) {
            is OwnerClauseReading.Step -> return reading.step::class == step::class ||
                (reading.step.code == "OPEN_IT" && (step is OwnerMissionStep.OpenFound || step is OwnerMissionStep.OpenSaved || step is OwnerMissionStep.OpenPdf))
            is OwnerClauseReading.Later, is OwnerClauseReading.NotYet -> return false
            else -> Unit
        }
        return when (clause.verb?.family) {
            OwnerVerbLexicon.Family.OPEN, OwnerVerbLexicon.Family.SHOW -> step is OwnerMissionStep.OpenPdf || step is OwnerMissionStep.OpenFound ||
                step is OwnerMissionStep.OpenSaved || (step is OwnerMissionStep.News && newsNoun.containsMatchIn(clause.text))
            OwnerVerbLexicon.Family.SEARCH -> step is OwnerMissionStep.OpenPdf || step is OwnerMissionStep.FindFile
            OwnerVerbLexicon.Family.READ, OwnerVerbLexicon.Family.LOOK, OwnerVerbLexicon.Family.TELL -> step is OwnerMissionStep.OpenPdf ||
                step is OwnerMissionStep.PageSummary || (step is OwnerMissionStep.News && newsNoun.containsMatchIn(clause.text)) ||
                step is OwnerMissionStep.Storage
            OwnerVerbLexicon.Family.SUMMARISE -> step is OwnerMissionStep.PageSave || step is OwnerMissionStep.PageSummary ||
                step is OwnerMissionStep.News || step is OwnerMissionStep.Export
            OwnerVerbLexicon.Family.SAVE, OwnerVerbLexicon.Family.CREATE, OwnerVerbLexicon.Family.MAKE ->
                step is OwnerMissionStep.PageSave || step is OwnerMissionStep.Export
            OwnerVerbLexicon.Family.SEND, OwnerVerbLexicon.Family.WRITE -> step is OwnerMissionStep.Mail && mailWord.containsMatchIn(clause.text)
            OwnerVerbLexicon.Family.CHECK, OwnerVerbLexicon.Family.CLEAN -> step is OwnerMissionStep.Storage
            else -> false
        }
    }

    /**
     * Matches the orders of the sentence to the steps of the plan, both in the order they were said: an order can only
     * be covered by a step behind the step of the order in front of it. Null = every order is covered. Otherwise the
     * orders that will not be carried out – the uncovered ones, and those whose step comes after the first gap and may
     * build on it – and what may be offered instead: the steps in front of that gap, which stand on their own.
     */
    private fun coverage(reading: OwnerSentenceReading, steps: List<OwnerMissionStep>): Pair<List<OwnerClause>, List<OwnerMissionStep>>? {
        val orders = reading.orders
        val stepOf = HashMap<Int, Int>()
        var used = 0
        var gapAt: Int? = null
        orders.forEachIndexed { position, clause ->
            // „Such das PDF Argon4, öffne es“ is one step: the opening is covered by the step that opens the PDF.
            if (clause.step?.code == "OPEN_IT" && (steps.getOrNull(used - 1) as? OwnerMissionStep.OpenPdf)?.open == true) {
                stepOf[position] = used - 1
                return@forEachIndexed
            }
            val at = (used until steps.size).firstOrNull { covers(steps[it], clause) }
            if (at == null) { if (gapAt == null) gapAt = used } else { stepOf[position] = at; used = at + 1 }
        }
        val keep = gapAt ?: return null
        val open = orders.mapIndexedNotNull { position, clause ->
            val at = stepOf[position]
            when {
                at == null -> clause
                at >= keep -> clause.copy(dependsOnOpen = true)
                else -> null
            }
        }
        return open to steps.take(keep)
    }

    private fun words(text: String): Set<String> = text.lowercase(Locale.GERMAN).replace(Regex("[^\\p{L}\\p{N}@._-]+"), " ")
        .split(' ').map { it.trim('.', '-', '_') }.filter { it.isNotEmpty() }.toSet()

    private fun said(request: String, phrase: String): Boolean = words(request).containsAll(words(phrase))

    private fun formatSaid(request: String, format: OwnerExportCommand.Format?): Boolean = when (format) {
        null -> true
        OwnerExportCommand.Format.DOCX -> wordTarget.containsMatchIn(OwnerPageJobCommand.heard(request))
        OwnerExportCommand.Format.PDF -> pdfTarget.containsMatchIn(request)
    }

    /** What [step] names that the owner's [request] does not contain, or null when everything in it was said. */
    private fun invented(request: String, step: OwnerMissionStep): String? = when (step) {
        is OwnerMissionStep.OpenPdf -> when {
            !OwnerLibraryMatcher.tokens(request).containsAll(OwnerLibraryMatcher.tokens(step.query)) -> "den Dateinamen „${step.query}“"
            step.folderHint == OwnerLibraryMatcher.FOLDER_DOWNLOAD && !request.contains("download", ignoreCase = true) -> "einen Ordner"
            step.folderHint == OwnerLibraryMatcher.FOLDER_DOCUMENTS && !request.contains("dokumente", ignoreCase = true) -> "einen Ordner"
            else -> null
        }
        is OwnerMissionStep.PageSave -> when {
            OwnerPageReference.one.findAll(request).mapNotNull(OwnerPageReference::page).none { it == step.page } -> "eine Seite"
            !formatSaid(request, step.format) -> "ein Format"
            step.name != null && !said(request, step.name) -> "den Dateinamen „${step.name}“"
            else -> null
        }
        is OwnerMissionStep.PageSummary ->
            if (OwnerPageReference.one.findAll(request).mapNotNull(OwnerPageReference::page).none { it == step.page }) "eine Seite" else null
        is OwnerMissionStep.News -> when {
            !newsNoun.containsMatchIn(request) -> "die Nachrichten"
            step.topic != null && !said(request, step.topic) -> "das Thema „${step.topic}“"
            else -> null
        }
        is OwnerMissionStep.Export -> when {
            !formatSaid(request, step.format) -> "ein Format"
            step.name != null && !said(request, step.name) -> "den Dateinamen „${step.name}“"
            else -> null
        }
        is OwnerMissionStep.Mail -> when {
            !mailWord.containsMatchIn(request) -> "eine E-Mail"
            step.address != null && !request.contains(step.address, ignoreCase = true) -> "die Adresse ${step.address}"
            step.recipientName != null && !said(request, step.recipientName) -> "den Empfänger „${step.recipientName}“"
            else -> null
        }
        is OwnerMissionStep.FindFile -> when {
            step.newest && !newest.containsMatchIn(request) -> "eine Suche nach der neuesten Datei"
            !said(request, step.order.replace(Regex("(?iu)^(?:such(?:e)?|finde?)\\s+"), "")
                .replace(Regex("(?iu)$S(?:das|die|den|der|dem|in|im|aus|nach|mir|bitte|datei|dateien|neueste[nrs]?|ordner)$E"), " ")) -> "einen Namen oder Ordner"
            else -> null
        }
        else -> null
    }
}
