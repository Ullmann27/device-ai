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
 */
internal object OwnerMissionModelPlan {
    sealed interface Result {
        class Accepted(val plan: OwnerMissionPlan) : Result
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
        append("Fass mir die Nachrichten von heute zusammen\n")
        append("Speichere das als <PDF oder Word-Datei>\n")
        append("Schick das per Mail an <Adresse oder Name>\n")
        append("Such das neueste PDF in <Downloads oder Dokumente>\n")
        append("Öffne es\n")
        append("Namen, Adressen, Seiten, Formate und Ordner übernimmst du genau so, wie sie im Auftrag stehen – auch wenn ")
        append("ein Name nach einem Hörfehler aussieht: Die Suche findet ähnliche Namen selbst. Füge nichts hinzu, was der ")
        append("Besitzer nicht gesagt hat. „Such das PDF“ sucht nach dem Namen und öffnet nichts; „Öffne das PDF“ nur, ")
        append("wenn er öffnen, aufmachen oder zeigen gesagt hat.\n")
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

    /** true = [plan] may run without a question: one step that neither sends nor shares anything. */
    fun runsWithoutQuestion(plan: OwnerMissionPlan): Boolean =
        plan.steps.size == 1 && plan.steps.single() !is OwnerMissionStep.Mail

    /** The bounded instruction for a text runtime; the listed forms are the only ones a line may have. */
    fun prompt(request: String): String = buildString {
        append("Zerlege den Auftrag in höchstens ${OwnerMissionPlanner.MAX_STEPS} Teilaufträge, in der Reihenfolge, in der sie ")
        append("ausgeführt werden. Erlaubt sind nur diese Formen, Wort für Wort, mit den Namen, Adressen, Seiten und ")
        append("Formaten aus dem Auftrag – erfinde nichts dazu:\n")
        append("Öffne das PDF <Name>\n")
        append("Fass Seite <Zahl> zusammen und speichere es als <Word oder PDF>\n")
        append("Fass mir die Nachrichten von heute zusammen\n")
        append("Speichere das als <PDF oder Word-Datei>\n")
        append("Schick das per Mail an <Adresse oder Name>\n")
        append("Such das neueste PDF in <Downloads oder Dokumente>\n")
        append("Öffne es\n")
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
        return Result.Accepted(OwnerMissionPlan(request.trim(), steps))
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
