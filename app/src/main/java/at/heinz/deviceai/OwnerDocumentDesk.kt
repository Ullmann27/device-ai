package at.heinz.deviceai

import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cognitive.NewsBriefing
import at.heinz.deviceai.cognitive.OwnerCorrectionIntent
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerDeskCorrection
import at.heinz.deviceai.cognitive.OwnerDeskReply
import at.heinz.deviceai.cognitive.OwnerDocumentDialogue
import at.heinz.deviceai.cognitive.OwnerDocumentDocx
import at.heinz.deviceai.cognitive.OwnerDocumentLayout
import at.heinz.deviceai.cognitive.OwnerExportCommand
import at.heinz.deviceai.cognitive.OwnerLibraryChoice
import at.heinz.deviceai.cognitive.OwnerLibraryCommand
import at.heinz.deviceai.cognitive.OwnerLibraryFile
import at.heinz.deviceai.cognitive.OwnerLibraryMatcher
import at.heinz.deviceai.cognitive.OwnerPageJobCommand
import at.heinz.deviceai.cognitive.OwnerPageReference
import at.heinz.deviceai.cognitive.OwnerPageSummary
import at.heinz.deviceai.cognitive.OwnerPageSummaryCommand
import at.heinz.deviceai.localmodel.LocalModelThermalGuard
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CancellationException

/** Whether this app may read the owner's shared storage by path (Android's „Zugriff auf alle Dateien“). */
internal enum class OwnerLibraryAccess { GRANTED, MISSING, UNSUPPORTED }

/**
 * One walk through the shared storage: every PDF that was seen, the top folders that were looked into, and – when a
 * bound ended the walk early – [stoppedEarly] with the reason in the owner's language.
 */
internal class OwnerLibraryScan(val files: List<OwnerLibraryFile>, val folders: List<String>, val stoppedEarly: String? = null)

/** A PDF that was really read: its facts for wording, and [payload] – whatever the host needs to show it. */
internal class OwnerDeskPdf(val name: String, val uri: String, val pageCount: Int, val textState: OwnerDocumentDialogue.TextState,
                            val textChars: Int, val payload: Any? = null)

/** The PDF that is open in this chat. [keptPage]: the page's text as the reader kept it (1-based), or null. */
internal class OwnerDeskDocument(val name: String, val pageCount: Int, val uri: String?, val keptPage: (Int) -> String? = { null })

/** One page as read for a summary. Only [Text] carries anything a model may see. */
internal sealed interface OwnerDeskPage {
    data class Text(val text: String) : OwnerDeskPage
    data object NoText : OwnerDeskPage
    data object NeedsAndroid15 : OwnerDeskPage
    data class Missing(val pageCount: Int) : OwnerDeskPage
    data class Failed(val reason: String) : OwnerDeskPage
}

/** A search that waits for the owner's grant in Android's settings. Kept by the host so it survives a restart. */
internal data class OwnerDeskPendingSearch(val session: String, val request: String, val query: String,
                                           val openWhenClear: Boolean, val folderHint: String?, val atMillis: Long)

/** How a job ended for the desk: normally, by the owner's stop, or after the owner switched to another chat. */
internal enum class OwnerDeskJobEnd { FINISHED, STOPPED, SESSION_CHANGED }

/** What the Activity lends a running desk job. Every call is a real action; all of them may throw. */
internal interface OwnerDeskJob {
    /** A real work step for the stage and the cockpit timeline; a later call with the same key updates it. */
    fun step(key: String, label: String, state: StepState, action: CockpitAction? = null)
    fun cancelled(): Boolean
    /** Walks the shared storage; [progress] gets the number of PDFs seen so far. Throws CancellationException on stop. */
    fun scan(progress: (Int) -> Unit): OwnerLibraryScan
    fun readPdf(file: OwnerLibraryFile): OwnerDeskPdf
    fun readPage(document: OwnerDeskDocument, page: Int): OwnerDeskPage
    /** Blocking generation on the active runtime (text only); [progress] gets the characters written so far. */
    fun generate(prompt: String, progress: (Int) -> Unit): OwnerWorkGeneration
}

/** The small surface the desk needs from the Activity: thin hooks onto existing paths. */
internal interface OwnerDocumentDeskHost {
    val sessionId: String
    /** Number of owner turns shown so far in this chat; unchanged means „the very next turn“. */
    val ownerTurn: Long
    val assistantName: String?
    fun acceptOwnerTurn(request: String)
    /** A reply that did not come from the model. */
    fun reply(request: String, text: String)
    /** A result as assistant message (shown and spoken). */
    fun say(text: String)
    fun activity(line: String)
    /** true when no other job occupies the single work lane. */
    fun idle(): Boolean
    /** May be called off the UI thread. */
    fun access(): OwnerLibraryAccess
    /** Opens Android's „Zugriff auf alle Dateien“ page for this app; null = started, otherwise the reason. */
    fun openAccessSettings(): String?
    /** Android's own file picker (the existing path). */
    fun openPicker(request: String)
    var pendingSearch: OwnerDeskPendingSearch?
    /**
     * Runs [work] off the UI thread as the single busy job. [done] is called on the UI thread exactly once, after the
     * worker returned and the lane is free again – also after a stop, so the desk can say what was not done.
     */
    fun <T> runJob(request: String, work: (OwnerDeskJob) -> T, done: (Result<T>, OwnerDeskJobEnd) -> Unit)
    /** Asks the running job to stop (model generation included). [done] of that job still follows. */
    fun cancelJob()
    /** The real hits for the work window. */
    fun showHits(query: String, files: List<OwnerLibraryFile>)
    /** Shows the read PDF in the work window and binds it to this chat (the existing reading path). */
    fun showPdf(request: String, pdf: OwnerDeskPdf)
    /** A read PDF that is not shown after all. */
    fun discardPdf(pdf: OwnerDeskPdf) {}
    fun openDocument(): OwnerDeskDocument?
    fun modelReady(): Boolean
    fun cloudBrain(): Boolean = false
    fun promptBudgetBytes(): Int
    fun renderPdf(blocks: List<OwnerDocumentLayout.TextBlock>): Pair<ByteArray, Int>
    fun downloadNameTaken(displayName: String): Boolean
    fun saveDownload(displayName: String, mime: String, bytes: ByteArray): OwnerSavedFile
    /** The saved file read back from where it was written, or null when that is not possible. */
    fun readBack(file: OwnerSavedFile): ByteArray?
    /** Where the file really lies, in the words of Android's file manager („Interner Speicher › Download“), or null. */
    fun location(file: OwnerSavedFile): String? = null
    /** Hands the saved result to the work tools, so „öffne die gespeicherte Datei“ and „schick das per Mail“ find it. */
    fun adopt(result: OwnerDeliverable, file: OwnerSavedFile) {}
    /** „Ich meinte als PDF“ after a save of the work tools; true = they took the turn over. */
    fun exportAgain(request: String, format: OwnerExportCommand.Format): Boolean = false
    /** How an order of this lane ended (see [OwnerLaneOutcome]); nobody has to listen. */
    fun outcome(outcome: OwnerLaneOutcome) {}
    /**
     * true = „Soll ich sie öffnen?“ may be asked after a save right now (false while a sequence of orders goes on, or
     * when the owner wants no offers). A question only: it is asked more than once for one save, so the answer must not
     * count or change anything. What was really asked is reported once with [openOffered].
     */
    fun offerToOpen(): Boolean = true
    /** „Soll ich sie öffnen?“ was said just now. */
    fun openOffered() {}
    /**
     * true while a sequence of owner orders hands its steps to the lanes. Only then „Fass Seite 2 zusammen“ – without
     * a save order – is the desk's own order; said alone it keeps its answer through the reference registry. Its own
     * question: [offerToOpen] can be false for other reasons (the owner's initiative level), and the desk asks this
     * on every turn it sees.
     */
    fun inSequence(): Boolean = false
    /**
     * A result of this lane that is shown in the chat and NOT saved (the summary of one page): the host hands it to the
     * work tools, so „speichere das als Word“ and „schick das per Mail an …“ mean exactly this text. true = it now is
     * what the chat is about; the default keeps nothing, and the desk then does not promise that it can be saved.
     */
    fun present(result: OwnerDeliverable): Boolean = false
}

/**
 * The document desk (owner goal 18.11.2026): „Öffne das PDF Argon4“ → local search by name, real hits, a question when
 * it is not clear, the chosen PDF opened in the work window; then „Fass Seite 2 zusammen und speichere es als Word“ →
 * that page read, summarised by the model, written as a document into Downloads, read back and reported with its
 * real name and place. „Ich meinte Seite 3“, „ich meinte die andere Datei“, „ich meinte als PDF“ change the next
 * action; „Stopp“ ends a running job and says what was not done.
 *
 * Deterministic and fail closed: this class decides and words, everything real goes through [OwnerDocumentDeskHost].
 * It is free of Android, so every path runs in host tests with the productive parsers, matcher and wording. It sits
 * beside the task chains (which run device tools through the Shizuku bridge and the ToolGate): its steps need the
 * owner's storage grant, a model generation and questions that stay open over several turns – but it shows its work
 * through the same step timeline and keeps a question open in the same way a chain's choice does.
 */
internal class OwnerDocumentDesk(
    private val host: OwnerDocumentDeskHost,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    private class Search(val request: String, val query: String, val openWhenClear: Boolean, val folderHint: String?)
    private class Found(val session: String, val query: String, val files: List<OwnerLibraryFile>, var opened: OwnerLibraryFile? = null)
    private data class PageOrder(val page: Int, val format: OwnerExportCommand.Format?, val name: String?)
    private class PageDone(val session: String, val order: PageOrder, val documentName: String, val documentUri: String?,
                           val summary: OwnerPageSummary.Summary?, val saved: OwnerSavedFile?)
    private class Running(val session: String, val page: PageOrder?, val documentName: String? = null, val documentUri: String? = null)

    private sealed class Question(val session: String, val turn: Long, val atMillis: Long) {
        class Grant(session: String, turn: Long, at: Long, val search: Search) : Question(session, turn, at)
        class Picker(session: String, turn: Long, at: Long, val request: String) : Question(session, turn, at)
        /** [confirm]: one file and „Meinst du das?“ / „Soll ich es öffnen?“; otherwise a numbered list. */
        class Hits(session: String, turn: Long, at: Long, val found: Found, val files: List<OwnerLibraryFile>, val confirm: Boolean) :
            Question(session, turn, at)
        class FormatOf(session: String, turn: Long, at: Long, val order: PageOrder) : Question(session, turn, at)
    }

    private var question: Question? = null
    private var lastSearch: Found? = null
    private var lastPage: PageDone? = null
    /** The owner turn up to which the desk's own work was the subject without another lane in between. */
    private var subjectThrough = -1L
    private var running: Running? = null
    private var afterStop: (() -> Unit)? = null

    /** true while a search, an opening or a page job of this desk runs. */
    val jobRunning: Boolean get() = running != null

    private fun mark() { subjectThrough = host.ownerTurn }
    private fun failed(reason: String) = host.outcome(OwnerLaneOutcome.Failed(reason))
    private fun needs(need: OwnerLaneOutcome.Need, question: String) = host.outcome(OwnerLaneOutcome.NeedsOwner(need, question))
    private fun millis() = now().toEpochMilli()
    private fun ask(build: (String, Long, Long) -> Question) { question = build(host.sessionId, host.ownerTurn, millis()) }

    // ── Entry points ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * First look at an owner turn, also while a job runs: the stop word and a correction of the running page job,
     * and – on a free lane – a correction of what the desk did in the turn before. true = handled.
     */
    fun intercept(request: String): Boolean {
        val name = host.assistantName
        val run = running?.takeIf { it.session == host.sessionId }
        if (run != null) {
            if (OwnerDeskReply.isStop(request, name)) return stop(request)
            val current = run.page ?: return false
            val next = when (val correction = OwnerDeskCorrection.parse(request, name)) {
                is OwnerDeskCorrection.Correction.Page -> current.copy(page = correction.page)
                is OwnerDeskCorrection.Correction.Format -> current.copy(format = correction.format)
                else -> return false
            }
            host.acceptOwnerTurn(request)
            if (next == current) {
                host.reply(request, "Genau das läuft gerade: ${what(current)}.")
            } else {
                host.reply(request, "Verstanden – ${change(current, next)}. Den laufenden Auftrag breche ich dafür ab.")
                host.activity("DOKUMENTE · Korrektur während des Auftrags · ${change(current, next)}")
                afterStop = {
                    val before = lastPage?.takeIf { it.session == run.session && it.order == current }
                        ?: PageDone(run.session, current, run.documentName.orEmpty(), run.documentUri, null, null)
                    startPage(request, next, before, interrupted = true)
                }
                host.cancelJob()
            }
            mark()
            return true
        }
        if (!host.idle() || subjectThrough != host.ownerTurn) return false
        val correction = OwnerDeskCorrection.parse(request, name) ?: return false
        return correctOwnWork(request, correction).also { if (it) mark() }
    }

    /** true = [request] would be taken by [intercept] while a job runs (for surfaces that refuse input when busy). */
    fun controlsRunningJob(request: String): Boolean {
        val run = running?.takeIf { it.session == host.sessionId } ?: return false
        if (OwnerDeskReply.isStop(request, host.assistantName)) return true
        return run.page != null && when (OwnerDeskCorrection.parse(request, host.assistantName)) {
            is OwnerDeskCorrection.Correction.Page, is OwnerDeskCorrection.Correction.Format -> true
            else -> false
        }
    }

    /** The owner's stop word for a running desk job (typed, spoken, or the hands-free „Stopp“). false = no job. */
    fun stop(spoken: String): Boolean {
        if (running?.takeIf { it.session == host.sessionId } == null) return false
        host.acceptOwnerTurn(spoken)
        host.activity("DOKUMENTE · Stopp des Besitzers · der laufende Auftrag wird beendet")
        afterStop = null
        host.cancelJob()
        mark()
        return true
    }

    /**
     * true = [request] was an order or an answer for the desk and is completely handled (a reply was or will be
     * given). Call on the UI thread while no other job runs. false = not for the desk; nothing happened.
     */
    fun handle(request: String): Boolean {
        val handled = route(request)
        if (handled) mark()
        return handled
    }

    /**
     * „… und öffne es“ after another owner lane found a file: the real [name] of that file is searched and opened
     * through the same grant, search and matcher as „Öffne das PDF …“. [request] is the owner's own sub-order.
     */
    fun openFound(request: String, name: String, folderHint: String?) {
        host.acceptOwnerTurn(request)
        question = null
        startSearch(Search(request, name.replace(Regex("(?i)\\.pdf$"), "").trim(), openWhenClear = true, folderHint = folderHint))
        mark()
    }

    /**
     * A correction of the desk's last page order or search that reaches it after other lanes worked in between
     * (a sequence of owner orders). Same handling as right after the desk's own turn; false = nothing to correct.
     */
    fun correct(request: String): Boolean {
        if (!host.idle() || running != null) return false
        val correction = OwnerDeskCorrection.parse(request, host.assistantName) ?: return false
        return correctOwnWork(request, correction).also { if (it) mark() }
    }

    private fun route(request: String): Boolean {
        val name = host.assistantName
        if (answer(request, name)) return true
        OwnerPageJobCommand.parse(request, name)?.let { pageOrder(request, it); return true }
        // A step of a sequence („…, analysiere Seite zwei und fasse sie zusammen“): summarised and shown, never saved.
        if (host.inSequence()) (OwnerPageSummaryCommand.parse(request, name) as? OwnerPageSummaryCommand.Result.Order)?.let {
            summaryOrder(request, it.page)
            return true
        }
        OwnerLibraryCommand.parseOpen(request, name)?.let { order ->
            host.acceptOwnerTurn(request)
            question = null
            startSearch(Search(request, order.query, order.openWhenClear, order.folderHint))
            return true
        }
        return OwnerDeskCorrection.parse(request, name)?.let { nothingToCorrect(request, it) } ?: false
    }

    /** The app is in front again: a search that waited for the owner's grant continues by itself. */
    fun onResumed() {
        val pending = host.pendingSearch ?: return
        if (pending.session != host.sessionId || millis() - pending.atMillis !in 0..OPEN_MILLIS) { host.pendingSearch = null; return }
        if (!host.idle() || running != null) return
        host.pendingSearch = null
        when (host.access()) {
            OwnerLibraryAccess.GRANTED -> {
                host.say("Der Zugriff auf alle Dateien ist jetzt erlaubt. Ich suche „${pending.query}“.")
                startSearch(Search(pending.request, pending.query, pending.openWhenClear, pending.folderHint))
            }
            else -> {
                host.say("Der Zugriff auf alle Dateien ist weiterhin nicht erlaubt. Ich habe nichts durchsucht. $PICKER_OFFER")
                ask { session, turn, at -> Question.Picker(session, turn, at, pending.request) }
                failed("Der Zugriff auf alle Dateien ist weiterhin nicht erlaubt; ich habe nichts durchsucht.")
            }
        }
        mark()
    }

    // ── Open questions ──────────────────────────────────────────────────────────────────────────────────────

    private fun answer(request: String, name: String?): Boolean {
        val asked = question?.takeIf { it.session == host.sessionId } ?: return false
        val immediate = asked.turn == host.ownerTurn
        when (asked) {
            is Question.Grant -> {
                if (!immediate) { question = null; return false }
                if (OwnerDeskReply.isYes(request, name)) {
                    host.acceptOwnerTurn(request)
                    question = null
                    val search = asked.search
                    host.pendingSearch = OwnerDeskPendingSearch(host.sessionId, search.request, search.query, search.openWhenClear,
                        search.folderHint, millis())
                    val problem = host.openAccessSettings()
                    if (problem == null) {
                        host.activity("DOKUMENTE · Android-Einstellung „Zugriff auf alle Dateien“ geöffnet · die Suche wartet")
                        host.reply(request, "Ich öffne die Android-Einstellung. Schalte dort „Zugriff auf alle Dateien zulassen“ " +
                            "für Continuum ein und komm zurück – dann suche ich „${search.query}“ sofort weiter.")
                        needs(OwnerLaneOutcome.Need.GRANT, "Schalte „Zugriff auf alle Dateien zulassen“ für Continuum ein.")
                    } else {
                        host.pendingSearch = null
                        host.reply(request, "Die Einstellung ließ sich nicht öffnen: ${NewsBriefing.clean(problem, 140)}. Du findest " +
                            "sie unter Einstellungen › Apps › Spezieller Zugriff › Zugriff auf alle Dateien. Durchsucht habe ich nichts. " +
                            PICKER_OFFER)
                        ask { session, turn, at -> Question.Picker(session, turn, at, search.request) }
                        failed("Die Android-Einstellung für den Dateizugriff ließ sich nicht öffnen; ich habe nichts durchsucht.")
                    }
                    return true
                }
                if (OwnerDeskReply.isNo(request, name)) {
                    host.acceptOwnerTurn(request)
                    host.reply(request, "In Ordnung – ich öffne nichts und habe nichts durchsucht. Du kannst die Datei auch selbst " +
                        "auswählen. $PICKER_OFFER")
                    ask { session, turn, at -> Question.Picker(session, turn, at, asked.search.request) }
                    host.outcome(OwnerLaneOutcome.Stopped)
                    return true
                }
                return false
            }
            is Question.Picker -> {
                if (!immediate) { question = null; return false }
                if (OwnerDeskReply.isYes(request, name)) {
                    host.acceptOwnerTurn(request)
                    question = null
                    host.activity("DOKUMENTE · Android-Dateiauswahl · die Wahl trifft der Besitzer")
                    host.reply(request, "Ich öffne die Android-Dateiauswahl.")
                    host.openPicker(asked.request)
                    return true
                }
                if (OwnerDeskReply.isNo(request, name)) {
                    host.acceptOwnerTurn(request)
                    question = null
                    host.reply(request, "Alles klar, ich öffne nichts.")
                    return true
                }
                return false
            }
            is Question.FormatOf -> {
                if (!immediate) { question = null; return false }
                val format = OwnerDeskReply.format(request, name) ?: return false
                host.acceptOwnerTurn(request)
                question = null
                startPage(request, asked.order.copy(format = format), replaces = null)
                return true
            }
            is Question.Hits -> {
                if (millis() - asked.atMillis !in 0..OPEN_MILLIS) { question = null; return false }
                if (asked.confirm && immediate) {
                    if (OwnerDeskReply.isYes(request, name)) {
                        host.acceptOwnerTurn(request)
                        open(request, asked.files.first(), asked.found)
                        return true
                    }
                    if (OwnerDeskReply.isNo(request, name)) {
                        host.acceptOwnerTurn(request)
                        host.reply(request, "In Ordnung, ich öffne nichts. $PICKER_OFFER")
                        ask { session, turn, at -> Question.Picker(session, turn, at, request) }
                        host.outcome(OwnerLaneOutcome.Stopped)
                        return true
                    }
                }
                if (!asked.confirm && immediate && OwnerDeskReply.isYes(request, name)) {
                    host.acceptOwnerTurn(request)
                    host.reply(request, "Welches davon? Nenn mir die Nummer oder einen Teil des Namens.")
                    ask { session, turn, at -> Question.Hits(session, turn, at, asked.found, asked.files, asked.confirm) }
                    needs(OwnerLaneOutcome.Need.CHOICE, "Welches davon? Nenn mir die Nummer oder einen Teil des Namens.")
                    return true
                }
                if (!asked.confirm && immediate && OwnerDeskReply.isNo(request, name)) {
                    host.acceptOwnerTurn(request)
                    question = null
                    host.reply(request, "In Ordnung, ich öffne keines davon.")
                    host.outcome(OwnerLaneOutcome.Stopped)
                    return true
                }
                when (val picked = OwnerLibraryChoice.parse(request, asked.files, name)) {
                    is OwnerLibraryChoice.Answer.Pick -> {
                        host.acceptOwnerTurn(request)
                        open(request, asked.files[picked.index], asked.found)
                    }
                    OwnerLibraryChoice.Answer.Invalid -> {
                        host.acceptOwnerTurn(request)
                        host.reply(request, "Diese Auswahl gibt es nicht. Ich habe ${asked.files.size} Treffer genannt – welchen meinst du?")
                        ask { session, turn, at -> Question.Hits(session, turn, at, asked.found, asked.files, asked.confirm) }
                        needs(OwnerLaneOutcome.Need.CHOICE, "Ich habe ${asked.files.size} Treffer genannt – welchen meinst du?")
                    }
                    OwnerLibraryChoice.Answer.Drop -> {
                        host.acceptOwnerTurn(request)
                        question = null
                        host.reply(request, "In Ordnung, ich öffne keines davon.")
                        host.outcome(OwnerLaneOutcome.Stopped)
                    }
                    // Anything else leaves the question open and is routed normally.
                    null -> return false
                }
                return true
            }
        }
    }

    // ── Search and open ─────────────────────────────────────────────────────────────────────────────────────

    private sealed interface SearchOutcome {
        data object NeedsGrant : SearchOutcome
        data object Unsupported : SearchOutcome
        class Decided(val scan: OwnerLibraryScan, val decision: OwnerLibraryMatcher.Decision, val opened: Opened?) : SearchOutcome
    }

    private class Opened(val file: OwnerLibraryFile, val pdf: OwnerDeskPdf?, val problem: String?)

    private fun startSearch(order: Search) {
        host.activity("DOKUMENTE · Suche nach „${order.query}“ · nur auf diesem Gerät, ohne Netz")
        launch(order.request, Running(host.sessionId, null), "Gestoppt. Die Suche ist abgebrochen; ich habe nichts geöffnet.",
            { job -> searchWork(job, order) }) { outcome, late ->
            if (late) {
                (outcome as? SearchOutcome.Decided)?.opened?.pdf?.let(host::discardPdf)
                host.say("Gestoppt. Ich habe nichts geöffnet.")
                host.outcome(OwnerLaneOutcome.Stopped)
            } else publishSearch(order, outcome)
        }
    }

    private fun searchWork(job: OwnerDeskJob, order: Search): SearchOutcome {
        job.step(STEP_ACCESS, "Dateizugriff prüfen", StepState.ACTIVE, CockpitAction.TOOL)
        when (host.access()) {
            OwnerLibraryAccess.MISSING -> { job.step(STEP_ACCESS, "Dateizugriff fehlt", StepState.BLOCKED); return SearchOutcome.NeedsGrant }
            OwnerLibraryAccess.UNSUPPORTED -> {
                job.step(STEP_ACCESS, "Dateizugriff nicht verfügbar", StepState.BLOCKED)
                return SearchOutcome.Unsupported
            }
            OwnerLibraryAccess.GRANTED -> job.step(STEP_ACCESS, "Dateizugriff erlaubt", StepState.DONE)
        }
        job.step(STEP_SCAN, "Speicher durchsuchen", StepState.ACTIVE)
        // Real progress, but only a handful of updates: the stage shall not work harder than the search itself.
        var shown = -PROGRESS_PDFS
        val scan = try {
            job.scan { seen ->
                if (seen - shown >= PROGRESS_PDFS) {
                    shown = seen
                    job.step(STEP_SCAN, "Speicher durchsuchen · ${pdfs(seen)} geprüft", StepState.ACTIVE)
                }
            }
        } catch (error: Exception) {
            job.step(STEP_SCAN, if (error is CancellationException) "Suche gestoppt" else "Suche fehlgeschlagen", StepState.FAILED)
            throw error
        }
        if (job.cancelled()) {
            job.step(STEP_SCAN, "Suche gestoppt", StepState.FAILED)
            throw CancellationException("Suche gestoppt")
        }
        job.step(STEP_SCAN, "Speicher durchsucht · ${pdfs(scan.files.size)} geprüft", StepState.DONE)
        val decision = OwnerLibraryMatcher.decide(order.query, scan.files, order.folderHint)
        job.step(STEP_HITS, when (decision) {
            OwnerLibraryMatcher.Decision.None -> "Kein Treffer"
            is OwnerLibraryMatcher.Decision.Near -> "Kein Treffer · nur ähnliche Namen"
            is OwnerLibraryMatcher.Decision.Ask -> "${decision.hits.size} Treffer"
            is OwnerLibraryMatcher.Decision.Open -> if (decision.others.isEmpty()) "1 Treffer" else "${decision.others.size + 1} Treffer · einer genau"
        }, StepState.DONE)
        val opened = (decision as? OwnerLibraryMatcher.Decision.Open)?.takeIf { order.openWhenClear }?.let { openWork(job, it.hit.file) }
        return SearchOutcome.Decided(scan, decision, opened)
    }

    private fun openWork(job: OwnerDeskJob, file: OwnerLibraryFile): Opened {
        job.step(STEP_OPEN, "PDF öffnen", StepState.ACTIVE, CockpitAction.EXECUTE)
        val pdf = try { job.readPdf(file) } catch (error: Exception) {
            job.step(STEP_OPEN, if (error is CancellationException) "Öffnen gestoppt" else "PDF ließ sich nicht öffnen", StepState.FAILED)
            if (error is CancellationException) throw error
            return Opened(file, null, NewsBriefing.clean(error.message ?: error.javaClass.simpleName, 140))
        }
        if (job.cancelled()) {
            host.discardPdf(pdf)
            job.step(STEP_OPEN, "Öffnen gestoppt", StepState.FAILED)
            throw CancellationException("Öffnen gestoppt")
        }
        job.step(STEP_OPEN, "PDF geöffnet · " + (if (pdf.pageCount == 1) "1 Seite" else "${pdf.pageCount} Seiten"), StepState.DONE)
        when (pdf.textState) {
            OwnerDocumentDialogue.TextState.EXTRACTED ->
                job.step(STEP_TEXT, "Text gelesen · ${OwnerPageSummary.count(pdf.textChars)} Zeichen", StepState.DONE)
            OwnerDocumentDialogue.TextState.NO_TEXT_LAYER -> job.step(STEP_TEXT, "Kein auslesbarer Text (Scan)", StepState.BLOCKED)
            OwnerDocumentDialogue.TextState.NEEDS_ANDROID_15 -> job.step(STEP_TEXT, "Textauszug braucht Android 15", StepState.BLOCKED)
        }
        return Opened(file, pdf, null)
    }

    private fun publishSearch(order: Search, outcome: SearchOutcome) {
        val request = order.request
        when (outcome) {
            SearchOutcome.NeedsGrant -> {
                host.activity("DOKUMENTE · „Zugriff auf alle Dateien“ fehlt · nichts durchsucht")
                host.reply(request, GRANT_MISSING)
                ask { session, turn, at -> Question.Grant(session, turn, at, order) }
                needs(OwnerLaneOutcome.Need.GRANT, GRANT_MISSING)
            }
            SearchOutcome.Unsupported -> {
                host.reply(request, "Die Suche im Gerätespeicher gibt es erst ab Android 11. Ich habe nichts durchsucht. $PICKER_OFFER")
                ask { session, turn, at -> Question.Picker(session, turn, at, request) }
                failed("Die Suche im Gerätespeicher gibt es erst ab Android 11; ich habe nichts durchsucht.")
            }
            is SearchOutcome.Decided -> when (val decision = outcome.decision) {
                OwnerLibraryMatcher.Decision.None -> {
                    lastSearch = null
                    host.activity("DOKUMENTE · kein Treffer · ${pdfs(outcome.scan.files.size)} geprüft")
                    host.reply(request, "Ein PDF namens „${order.query}“ habe ich nicht gefunden. ${searched(outcome.scan)} " +
                        "Wenn die Datei woanders liegt oder anders heißt, kannst du sie selbst auswählen. $PICKER_OFFER")
                    ask { session, turn, at -> Question.Picker(session, turn, at, request) }
                    failed("Ein PDF namens „${order.query}“ habe ich nicht gefunden. ${searched(outcome.scan)}")
                }
                is OwnerLibraryMatcher.Decision.Near -> {
                    val files = decision.hits.map { it.file }
                    val found = Found(host.sessionId, order.query, files).also { lastSearch = it }
                    host.showHits(order.query, files)
                    host.reply(request, "Ein PDF namens „${order.query}“ habe ich nicht gefunden. ${searched(outcome.scan)} " +
                        if (files.size == 1) "Ähnlich heißt: ${OwnerLibraryMatcher.describe(files.single(), zone())}. Meinst du das?"
                        else "Ähnlich heißen:\n${numbered(files)}\nMeinst du eines davon?")
                    ask { session, turn, at -> Question.Hits(session, turn, at, found, files, confirm = files.size == 1) }
                    needs(OwnerLaneOutcome.Need.CHOICE, "Ein PDF namens „${order.query}“ gibt es nicht, nur ähnliche Namen. Welches meinst du?")
                }
                is OwnerLibraryMatcher.Decision.Ask -> {
                    val files = decision.hits.take(OwnerLibraryMatcher.MAX_LISTED).map { it.file }
                    val found = Found(host.sessionId, order.query, files).also { lastSearch = it }
                    val more = decision.hits.size - files.size
                    host.showHits(order.query, files)
                    host.reply(request, "Zu „${order.query}“ habe ich ${decision.hits.size} PDFs gefunden:\n${numbered(files)}\n" +
                        (if (more > 0) "Dazu kommen $more weitere – nenn mir dafür einen genaueren Namen. " else "") +
                        "Welches soll ich öffnen? Sag zum Beispiel „das zweite“ oder einen Teil des Namens.")
                    ask { session, turn, at -> Question.Hits(session, turn, at, found, files, confirm = false) }
                    needs(OwnerLaneOutcome.Need.CHOICE, "Zu „${order.query}“ gibt es ${decision.hits.size} PDFs. Welches soll ich öffnen?")
                }
                is OwnerLibraryMatcher.Decision.Open -> {
                    val files = (listOf(decision.hit) + decision.others).take(OwnerLibraryMatcher.MAX_LISTED).map { it.file }
                    val found = Found(host.sessionId, order.query, files).also { lastSearch = it }
                    val file = decision.hit.file
                    val others = if (files.size < 2) "" else " Es gibt " + (if (files.size == 2) "einen weiteren Treffer" else
                        "${files.size - 1} weitere Treffer") + ": " + files.drop(1).mapIndexed { index, other ->
                        "${index + 2}. „${other.name}“ (${other.folderLabel})" }.joinToString(", ") +
                        ". Wenn du einen davon meinst, sag zum Beispiel „ich meinte die zweite“."
                    host.showHits(order.query, files)
                    val opened = outcome.opened
                    when {
                        // A pure search („Such das PDF …“): the hits are shown; opening is the owner's next word.
                        opened == null && files.size > 1 -> {
                            host.reply(request, "Zu „${order.query}“ habe ich ${files.size} PDFs gefunden:\n${numbered(files)}\n" +
                                "Der erste Treffer heißt genau so. Welches soll ich öffnen? Sag zum Beispiel „das erste“ oder einen Teil des Namens.")
                            ask { session, turn, at -> Question.Hits(session, turn, at, found, files, confirm = false) }
                            host.outcome(OwnerLaneOutcome.Done(OwnerLaneResult.FilesFound(order.query, files.map { it.name })))
                        }
                        opened == null -> {
                            host.reply(request, "Gefunden: ${OwnerLibraryMatcher.describe(file, zone())}. Soll ich es öffnen?")
                            ask { session, turn, at -> Question.Hits(session, turn, at, found, listOf(file), confirm = true) }
                            host.outcome(OwnerLaneOutcome.Done(OwnerLaneResult.FilesFound(order.query, listOf(file.name))))
                        }
                        opened.pdf != null -> {
                            found.opened = file
                            question = null
                            host.activity("DOKUMENTE · geöffnet · ${file.name}")
                            host.say("Gefunden: ${OwnerLibraryMatcher.describe(file, zone())}. Ich öffne es.$others")
                            host.showPdf(request, opened.pdf)
                            host.outcome(OwnerLaneOutcome.Done(OwnerLaneResult.DocumentOpened(file.name, file.folderLabel,
                                opened.pdf.pageCount, order.query)))
                        }
                        else -> {
                            host.say("Gefunden: ${OwnerLibraryMatcher.describe(file, zone())}. Öffnen ließ es sich aber nicht: " +
                                "${opened.problem}. An der Datei wurde nichts verändert.$others")
                            failed("„${file.name}“ ließ sich nicht öffnen: ${opened.problem}.")
                        }
                    }
                }
            }
        }
    }

    /** Opens one already found file (the owner's choice or correction). */
    private fun open(request: String, file: OwnerLibraryFile, found: Found?) {
        question = null
        host.activity("DOKUMENTE · öffne ${file.name}")
        launch(request, Running(host.sessionId, null), "Gestoppt. Ich habe nichts geöffnet.", { job -> openWork(job, file) }) { opened, late ->
            when {
                late -> {
                    opened.pdf?.let(host::discardPdf)
                    host.say("Gestoppt. Ich habe nichts geöffnet.")
                    host.outcome(OwnerLaneOutcome.Stopped)
                }
                opened.pdf != null -> {
                    found?.opened = file
                    host.say("Ich öffne ${OwnerLibraryMatcher.describe(file, zone())}.")
                    host.showPdf(request, opened.pdf)
                    host.outcome(OwnerLaneOutcome.Done(OwnerLaneResult.DocumentOpened(file.name, file.folderLabel,
                        opened.pdf.pageCount, found?.query)))
                }
                else -> {
                    host.say("„${file.name}“ ließ sich nicht öffnen: ${opened.problem}. An der Datei wurde nichts verändert.")
                    failed("„${file.name}“ ließ sich nicht öffnen: ${opened.problem}.")
                }
            }
        }
    }

    private fun numbered(files: List<OwnerLibraryFile>): String =
        files.mapIndexed { index, file -> "${index + 1}. ${OwnerLibraryMatcher.describe(file, zone())}" }.joinToString("\n")

    private fun pdfs(count: Int): String = if (count == 1) "1 PDF" else "${OwnerPageSummary.count(count)} PDFs"

    /** What was really looked at – count, top folders and whether a bound ended the walk early. */
    private fun searched(scan: OwnerLibraryScan): String {
        val folders = scan.folders.take(MAX_FOLDERS_NAMED)
        val where = when {
            folders.isEmpty() -> ""
            else -> " (Ordner: " + folders.joinToString(", ") +
                (if (scan.folders.size > folders.size) " und ${scan.folders.size - folders.size} weitere" else "") + ")"
        }
        return "Geprüft habe ich ${pdfs(scan.files.size)} im internen Speicher$where." +
            (scan.stoppedEarly?.let { " $it" } ?: "")
    }

    // ── „Fass Seite 2 zusammen und speichere es als Word“ ───────────────────────────────────────────────────

    private sealed interface PageOutcome {
        data object NoText : PageOutcome
        data object NeedsAndroid15 : PageOutcome
        data class Missing(val pageCount: Int) : PageOutcome
        data class ReadFailed(val reason: String) : PageOutcome
        data class NoModel(val chars: Int) : PageOutcome
        data class ModelFailed(val problem: String?) : PageOutcome
        class SaveFailed(val summary: OwnerPageSummary.Summary, val reason: String) : PageOutcome
        class Saved(val summary: OwnerPageSummary.Summary, val result: OwnerDeliverable, val file: OwnerSavedFile, val bytes: Int,
                    val verified: Boolean, val location: String?) : PageOutcome
    }

    private fun pageOrder(request: String, parsed: OwnerPageJobCommand.Result) {
        host.acceptOwnerTurn(request)
        question = null
        when (parsed) {
            OwnerPageJobCommand.Result.SeveralPages -> {
                host.reply(request, "Im Moment fasse ich pro Auftrag genau eine Seite " +
                    "zusammen und speichere sie. Ich habe nichts zusammengefasst und nichts gespeichert. Welche Seite soll es sein?")
                failed("Pro Auftrag fasse ich genau eine Seite zusammen; ich habe nichts zusammengefasst und nichts gespeichert.")
            }
            is OwnerPageJobCommand.Result.Order -> {
                val order = PageOrder(parsed.page, parsed.format, parsed.name)
                if (order.format == null) {
                    host.reply(request, "Als was soll ich die Zusammenfassung speichern – als Word-Datei oder als PDF?")
                    ask { session, turn, at -> Question.FormatOf(session, turn, at, order) }
                    needs(OwnerLaneOutcome.Need.DETAIL, "Als was soll ich die Zusammenfassung speichern – als Word-Datei oder als PDF?")
                } else startPage(request, order, replaces = null)
            }
        }
    }

    /**
     * Runs the page order. [replaces]: the order this one corrects – its saved file stays and is named in the reply;
     * [interrupted]: that order was still running and was stopped for this one.
     */
    private fun startPage(request: String, wanted: PageOrder, replaces: PageDone?, interrupted: Boolean = false) {
        val format = checkNotNull(wanted.format)
        val document = host.openDocument()
        if (document == null) {
            host.reply(request, "Im Moment ist kein PDF geöffnet. Sag zum Beispiel „Öffne das PDF Argon4“ – danach fasse ich " +
                "dir die Seite zusammen und speichere sie. Ich habe nichts gespeichert.")
            failed("Im Moment ist kein PDF geöffnet; ich habe nichts gespeichert.")
            return
        }
        val page = if (wanted.page == OwnerPageReference.LAST) document.pageCount else wanted.page
        val order = wanted.copy(page = page)
        if (page < 1 || (document.pageCount > 0 && page > document.pageCount)) {
            lastPage = PageDone(host.sessionId, order, document.name, document.uri, null, null)
            host.reply(request, missingPage(document.name, document.pageCount, page))
            failed(missingPage(document.name, document.pageCount, page))
            return
        }
        // The same page of the same file in another format: the accepted summary is reused, not generated again.
        val reuse = replaces?.takeIf { it.documentName == document.name && it.documentUri == document.uri && it.order.page == page }?.summary
        host.activity("DOKUMENTE · Seite $page von „${document.name}“ · ${format.label} · Download-Ordner")
        launch(request, Running(host.sessionId, order, document.name, document.uri),
            "Gestoppt. Seite $page wurde nicht fertig; es wurde nichts gespeichert.",
            { job -> pageWork(job, document, order, format, reuse) }) { outcome, late ->
            publishPage(request, document, order, format, outcome, replaces, interrupted, late)
        }
    }

    // ── „…, analysiere Seite zwei und fasse sie zusammen“ – a step of a sequence, nothing is saved ─────────────

    private sealed interface PageRead {
        class Summarised(val summary: OwnerPageSummary.Summary) : PageRead
        class Not(val why: PageOutcome) : PageRead
    }

    /**
     * The page is read, summarised and shown in the chat as a result of its own – and no file is written, because
     * none was ordered. Done only when the page's text was really read and the summary was accepted, exactly as the
     * page order with a save does it.
     */
    private fun summaryOrder(request: String, wanted: Int) {
        host.acceptOwnerTurn(request)
        question = null
        val nothing = "Ich habe nichts zusammengefasst."
        val document = host.openDocument()
        if (document == null) {
            host.reply(request, "Im Moment ist kein PDF geöffnet. $nothing")
            failed("Im Moment ist kein PDF geöffnet; ich habe nichts zusammengefasst.")
            return
        }
        val page = if (wanted == OwnerPageReference.LAST) document.pageCount else wanted
        if (page < 1 || (document.pageCount > 0 && page > document.pageCount)) {
            val said = "„${document.name}“ hat nur " + (if (document.pageCount == 1) "1 Seite" else "${document.pageCount} Seiten") +
                " – eine Seite $page gibt es nicht. $nothing"
            host.reply(request, said)
            failed(said)
            return
        }
        host.activity("DOKUMENTE · Seite $page von „${document.name}“ · Zusammenfassung im Gespräch · es wird nichts gespeichert")
        launch(request, Running(host.sessionId, null, document.name, document.uri),
            "Gestoppt. Seite $page wurde nicht fertig zusammengefasst; gespeichert habe ich nichts.",
            { job -> summarise(job, document, page) }) { read, _ -> publishSummary(document, page, read) }
    }

    private fun publishSummary(document: OwnerDeskDocument, page: Int, read: PageRead) {
        val nothing = "Ich habe nichts zusammengefasst."
        when (read) {
            is PageRead.Not -> {
                val said = when (val why = read.why) {
                    PageOutcome.NoText -> "Seite $page von „${document.name}“ hat keinen auslesbaren Text – zum Beispiel ein Scan. $nothing"
                    PageOutcome.NeedsAndroid15 -> "Den Text einer PDF-Seite kann ich erst ab Android 15 auslesen. $nothing"
                    is PageOutcome.Missing -> "„${document.name}“ hat nur " + (if (why.pageCount == 1) "1 Seite" else "${why.pageCount} Seiten") +
                        " – eine Seite $page gibt es nicht. $nothing"
                    is PageOutcome.ReadFailed -> "Seite $page von „${document.name}“ ließ sich nicht lesen: ${why.reason}. $nothing"
                    is PageOutcome.NoModel -> "Seite $page von „${document.name}“ habe ich gelesen (${OwnerPageSummary.count(why.chars)} " +
                        "Zeichen). Für die Zusammenfassung brauche ich aber das Sprachmodell, und im Moment ist keines geladen. $nothing"
                    is PageOutcome.ModelFailed -> if (LocalModelThermalGuard.isThermal(why.problem)) LocalModelThermalGuard.ownerMessage(why.problem) +
                        " $nothing" else "Das Sprachmodell hat für Seite $page keine brauchbare Zusammenfassung geliefert" +
                        (why.problem?.let { " (${NewsBriefing.clean(it, 120)})" } ?: "") + ". $nothing"
                    else -> "Seite $page ließ sich nicht zusammenfassen. $nothing"
                }
                host.say(said)
                failed(said)
            }
            is PageRead.Summarised -> {
                val summary = read.summary
                val time = now()
                val result = OwnerPageSummary.deliverable(document.name, page, document.pageCount, summary, time.atZone(zone()).toLocalDate(),
                    time.toEpochMilli())
                val kept = host.present(result)
                host.activity("DOKUMENTE · Seite $page zusammengefasst · im Gespräch gezeigt · nichts gespeichert")
                val basis = if (summary.complete) "" else "\n\nHinweis: Die Seite ist länger, als das Modell auf einmal lesen kann. " +
                    "Grundlage sind die ersten ${OwnerPageSummary.count(summary.coveredChars)} von " +
                    "${OwnerPageSummary.count(summary.totalChars)} Zeichen der Seite."
                val author = if (summary.cloud) " Zusammengefasst habe ich das über die Cloud, nicht mit dem Modell auf dem Gerät." else ""
                // The offer is made only when the result really is what a save order would take.
                val offer = if (kept) " Wenn du sie behalten willst, sag zum Beispiel „Speichere das als Word“." else ""
                host.say("Seite $page von „${document.name}“ – Zusammenfassung:\n\n${summary.text}$basis\n\nGespeichert habe ich nichts.$author$offer")
                host.outcome(OwnerLaneOutcome.Done(OwnerLaneResult.PageSummarised(result, document.name, page, summary.coveredChars,
                    summary.totalChars)))
            }
        }
    }

    /** Reads [page] and has the model summarise it; why not, when that is not possible. Nothing is written. */
    private fun summarise(job: OwnerDeskJob, document: OwnerDeskDocument, page: Int): PageRead {
        fun stopped(key: String, label: String): Nothing {
            job.step(key, label, StepState.FAILED)
            throw CancellationException(label)
        }
        fun not(why: PageOutcome): PageRead = PageRead.Not(why)
        job.step(STEP_PAGE, "Seite $page lesen", StepState.ACTIVE, CockpitAction.TOOL)
        val text = when (val read = try { job.readPage(document, page) } catch (error: Exception) {
            if (error is CancellationException) stopped(STEP_PAGE, "Lesen gestoppt")
            OwnerDeskPage.Failed(NewsBriefing.clean(error.message ?: error.javaClass.simpleName, 140))
        }) {
            is OwnerDeskPage.Text -> read.text.trim()
            OwnerDeskPage.NoText -> { job.step(STEP_PAGE, "Seite $page hat keinen Text", StepState.FAILED); return not(PageOutcome.NoText) }
            OwnerDeskPage.NeedsAndroid15 -> {
                job.step(STEP_PAGE, "Textauszug braucht Android 15", StepState.BLOCKED)
                return not(PageOutcome.NeedsAndroid15)
            }
            is OwnerDeskPage.Missing -> {
                job.step(STEP_PAGE, "Seite $page gibt es nicht", StepState.FAILED)
                return not(PageOutcome.Missing(read.pageCount))
            }
            is OwnerDeskPage.Failed -> {
                job.step(STEP_PAGE, "Seite $page nicht lesbar", StepState.FAILED)
                return not(PageOutcome.ReadFailed(read.reason))
            }
        }
        if (text.isEmpty()) { job.step(STEP_PAGE, "Seite $page hat keinen Text", StepState.FAILED); return not(PageOutcome.NoText) }
        job.step(STEP_PAGE, "Seite $page gelesen · ${OwnerPageSummary.count(text.length)} Zeichen", StepState.DONE)
        if (job.cancelled()) stopped(STEP_SUMMARY, "Zusammenfassen gestoppt")
        if (!host.modelReady()) {
            job.step(STEP_SUMMARY, "Kein Sprachmodell geladen", StepState.BLOCKED)
            return not(PageOutcome.NoModel(text.length))
        }
        val prompt = OwnerPageSummary.prompt(document.name, page, text, host.promptBudgetBytes()) ?: run {
            job.step(STEP_SUMMARY, "Zusammenfassen nicht möglich", StepState.FAILED)
            return not(PageOutcome.ModelFailed("der Seitentext passt nicht in die Aufgabe für das Modell"))
        }
        job.step(STEP_SUMMARY, "Zusammenfassen", StepState.ACTIVE, CockpitAction.THINK)
        // While the model computes the phone is warm enough: the step shows the growing text in a few coarse steps.
        var written = 0
        val generated = try {
            job.generate(prompt.text) { chars ->
                if (written == 0 || chars - written >= PROGRESS_CHARS) {
                    written = chars
                    job.step(STEP_SUMMARY, "Zusammenfassen · ${OwnerPageSummary.count(chars)} Zeichen", StepState.ACTIVE)
                }
            }
        } catch (error: CancellationException) { stopped(STEP_SUMMARY, "Zusammenfassen gestoppt") }
        if (job.cancelled()) stopped(STEP_SUMMARY, "Zusammenfassen gestoppt")
        val paragraphs = if (generated.failure != null || !generated.complete) null else OwnerPageSummary.accept(generated.text)
        if (paragraphs == null || prompt.coveredChars <= 0) {
            job.step(STEP_SUMMARY, "Zusammenfassen fehlgeschlagen", StepState.FAILED)
            return not(PageOutcome.ModelFailed(generated.failure))
        }
        return PageRead.Summarised(OwnerPageSummary.Summary(paragraphs, prompt.coveredChars, prompt.totalChars, host.cloudBrain()).also {
            job.step(STEP_SUMMARY, "Zusammengefasst · ${OwnerPageSummary.count(it.text.length)} Zeichen", StepState.DONE)
        })
    }

    private fun pageWork(job: OwnerDeskJob, document: OwnerDeskDocument, order: PageOrder, format: OwnerExportCommand.Format,
                         reuse: OwnerPageSummary.Summary?): PageOutcome {
        val page = order.page
        fun stopped(key: String, label: String): Nothing {
            job.step(key, label, StepState.FAILED)
            throw CancellationException(label)
        }
        // Reading and summarising are the same work as for a summary that is only shown ([summarise]).
        val summary = reuse ?: when (val read = summarise(job, document, page)) {
            is PageRead.Summarised -> read.summary
            is PageRead.Not -> return read.why
        }
        val time = now()
        val date = time.atZone(zone()).toLocalDate()
        val result = OwnerPageSummary.deliverable(document.name, page, document.pageCount, summary, date, time.toEpochMilli())
        val buildLabel = if (format == OwnerExportCommand.Format.DOCX) "Word-Datei aufbauen" else "PDF aufbauen"
        job.step(STEP_BUILD, buildLabel, StepState.ACTIVE, CockpitAction.TOOL)
        val bytes = try {
            val blocks = OwnerDocumentLayout.blocks(result)
            when (format) {
                OwnerExportCommand.Format.PDF -> host.renderPdf(blocks).first
                OwnerExportCommand.Format.DOCX -> OwnerDocumentDocx.write(blocks).also { check(OwnerPageSummary.readableDocx(it)) { "Die Word-Datei ist nicht lesbar" } }
            }.also { check(it.isNotEmpty()) { "Das Dokument ist leer" } }
        } catch (error: Exception) {
            job.step(STEP_BUILD, buildLabel, StepState.FAILED)
            return PageOutcome.SaveFailed(summary, NewsBriefing.clean(error.message ?: error.javaClass.simpleName, 140))
        }
        job.step(STEP_BUILD, buildLabel, StepState.DONE)
        // The last exit before anything is written: after this line the file is saved whole or not at all.
        if (job.cancelled()) stopped(STEP_SAVE, "Speichern gestoppt")
        job.step(STEP_SAVE, "In Downloads speichern", StepState.ACTIVE)
        val file = try {
            val name = OwnerExportCommand.fileName(order.name, result.title, date, format, host::downloadNameTaken)
            host.saveDownload(name, format.mime, bytes)
        } catch (error: Exception) {
            job.step(STEP_SAVE, "Speichern fehlgeschlagen", StepState.FAILED)
            return PageOutcome.SaveFailed(summary, NewsBriefing.clean(error.message ?: error.javaClass.simpleName, 140))
        }
        job.step(STEP_SAVE, "In Downloads gespeichert", StepState.DONE)
        job.step(STEP_VERIFY, "Gespeicherte Datei prüfen", StepState.ACTIVE, CockpitAction.VERIFY)
        val back = runCatching { host.readBack(file) }.getOrNull()
        val verified = back != null && back.contentEquals(bytes)
        job.step(STEP_VERIFY, if (verified) "Datei geprüft · ${OwnerLibraryMatcher.sizeText(bytes.size.toLong())}" else
            "Datei nicht zurückgelesen", if (verified) StepState.DONE else StepState.UNVERIFIED)
        return PageOutcome.Saved(summary, result, file, bytes.size, verified, runCatching { host.location(file) }.getOrNull())
    }

    private fun publishPage(request: String, document: OwnerDeskDocument, order: PageOrder, format: OwnerExportCommand.Format,
                            outcome: PageOutcome, replaces: PageDone?, interrupted: Boolean, late: Boolean) {
        val page = order.page
        val nothing = "Ich habe nichts zusammengefasst und nichts gespeichert."
        fun remember(summary: OwnerPageSummary.Summary?, saved: OwnerSavedFile?) {
            lastPage = PageDone(host.sessionId, order, document.name, document.uri, summary, saved)
        }
        when (outcome) {
            PageOutcome.NoText -> {
                remember(null, null)
                host.say("Seite $page von „${document.name}“ hat keinen auslesbaren Text – zum Beispiel ein Scan. $nothing")
                failed("Seite $page von „${document.name}“ hat keinen auslesbaren Text.")
            }
            PageOutcome.NeedsAndroid15 -> {
                remember(null, null)
                host.say("Den Text einer PDF-Seite kann ich erst ab Android 15 auslesen. $nothing")
                failed("Den Text einer PDF-Seite kann ich erst ab Android 15 auslesen.")
            }
            is PageOutcome.Missing -> {
                remember(null, null)
                host.say(missingPage(document.name, outcome.pageCount, page))
                failed(missingPage(document.name, outcome.pageCount, page))
            }
            is PageOutcome.ReadFailed -> {
                remember(null, null)
                host.say("Seite $page von „${document.name}“ ließ sich nicht lesen: ${outcome.reason}. $nothing")
                failed("Seite $page von „${document.name}“ ließ sich nicht lesen: ${outcome.reason}.")
            }
            is PageOutcome.NoModel -> {
                remember(null, null)
                host.say("Seite $page von „${document.name}“ habe ich gelesen (${OwnerPageSummary.count(outcome.chars)} Zeichen). Für " +
                    "die Zusammenfassung brauche ich aber das Sprachmodell, und im Moment ist keines geladen. $nothing Lade das " +
                    "Modell in der Modellverwaltung und gib mir den Auftrag dann noch einmal.")
                failed("Für die Zusammenfassung ist kein Sprachmodell geladen; es wurde nichts gespeichert.")
            }
            is PageOutcome.ModelFailed -> {
                remember(null, null)
                val said = if (LocalModelThermalGuard.isThermal(outcome.problem)) LocalModelThermalGuard.ownerMessage(outcome.problem) +
                    " Es wurde nichts gespeichert." else "Das Sprachmodell hat für Seite $page keine brauchbare Zusammenfassung " +
                    "geliefert" + (outcome.problem?.let { " (${NewsBriefing.clean(it, 120)})" } ?: "") + ". Es wurde nichts gespeichert."
                host.say(said)
                failed(said)
            }
            is PageOutcome.SaveFailed -> {
                remember(outcome.summary, null)
                host.say("Die Zusammenfassung von Seite $page steht, aber das Speichern hat nicht geklappt: ${outcome.reason}. " +
                    "Es wurde keine Datei angelegt.\n\n${outcome.summary.text}")
                failed("Die Zusammenfassung von Seite $page steht, aber das Speichern hat nicht geklappt: ${outcome.reason}. " +
                    "Es wurde keine Datei angelegt.")
            }
            is PageOutcome.Saved -> {
                remember(outcome.summary, outcome.file)
                val file = outcome.file
                val size = OwnerLibraryMatcher.sizeText(outcome.bytes.toLong())
                val openable = file.uri != null
                host.adopt(outcome.result, file)
                host.activity("DOKUMENTE · gespeichert · ${file.displayName}")
                val place = if (openable) "„${file.displayName}“ in deinem Download-Ordner" +
                    (outcome.location?.let { " ($it)" } ?: "") + ", $size." else
                    "„${file.displayName}“ ($size), aber nicht im Download-Ordner: Auf dieser Android-Version darf ich dort " +
                    "nicht schreiben. Die Datei liegt im ${file.folder}."
                val unchecked = if (outcome.verified) "" else " Zurücklesen konnte ich die Datei danach nicht – bitte prüf sie, " +
                    "bevor du dich darauf verlässt."
                val previous = replaces?.takeIf { it.order != order }?.let { before ->
                    val what = if (before.order.page != page) "Seite ${before.order.page}" else before.order.format?.label ?: "vorher"
                    when {
                        before.saved != null -> " Die Datei von vorhin (" + what + ", „${before.saved.displayName}“) gibt es weiterhin."
                        interrupted -> " Der abgebrochene Auftrag ($what) hat nichts gespeichert."
                        else -> ""
                    }
                }.orEmpty()
                val sameSummary = replaces?.summary != null && replaces.summary === outcome.summary
                val basis = if (outcome.summary.complete) "" else "\n\nHinweis: Die Seite ist länger, als das Modell auf einmal lesen " +
                    "kann. Grundlage sind die ersten ${OwnerPageSummary.count(outcome.summary.coveredChars)} von " +
                    "${OwnerPageSummary.count(outcome.summary.totalChars)} Zeichen der Seite."
                val author = if (outcome.summary.cloud) " Zusammengefasst habe ich das über die Cloud, nicht mit dem Modell auf dem Gerät." else ""
                val lateNote = if (late) " Diese Datei war schon gespeichert, bevor der Abbruch ankam." else ""
                val offer = if (openable && !late && host.offerToOpen()) " Soll ich sie öffnen?" else ""
                host.say(if (sameSummary) "Erledigt – dieselbe Zusammenfassung von Seite $page jetzt als ${format.label}: $place" +
                    "$unchecked$previous$lateNote$offer"
                else "Seite $page von „${document.name}“" + (replaces?.takeIf { it.order.page != page }?.let { " – statt Seite ${it.order.page}" } ?: "") +
                    " – Zusammenfassung:\n\n${outcome.summary.text}$basis\n\nGespeichert als ${format.label}: $place$author" +
                    "$unchecked$previous$lateNote$offer")
                if (offer.isNotEmpty()) host.openOffered()
                host.outcome(OwnerLaneOutcome.Done(OwnerLaneResult.FileSaved(file, outcome.result, outcome.location, outcome.verified)))
            }
        }
    }

    private fun missingPage(name: String, pageCount: Int, page: Int): String =
        "„$name“ hat nur " + (if (pageCount == 1) "1 Seite" else "$pageCount Seiten") + " – eine Seite $page gibt es nicht. " +
            "Ich habe nichts zusammengefasst und nichts gespeichert."

    private fun pageName(page: Int): String = if (page == OwnerPageReference.LAST) "die letzte Seite" else "Seite $page"

    private fun what(order: PageOrder): String = pageName(order.page) + (order.format?.let { " als ${it.label}" } ?: "")

    private fun change(from: PageOrder, to: PageOrder): String = when {
        from.page != to.page -> "${pageName(to.page)} statt ${pageName(from.page)}"
        else -> "als ${to.format?.label} statt als ${from.format?.label}"
    }

    // ── Corrections ─────────────────────────────────────────────────────────────────────────────────────────

    /** A correction right after the desk's own turn. false = it does not concern what the desk did. */
    private fun correctOwnWork(request: String, correction: OwnerDeskCorrection.Correction): Boolean {
        val session = host.sessionId
        when (correction) {
            is OwnerDeskCorrection.Correction.Page, is OwnerDeskCorrection.Correction.Format -> {
                val last = lastPage?.takeIf { it.session == session } ?: return false
                val next = if (correction is OwnerDeskCorrection.Correction.Page) last.order.copy(page = correction.page)
                    else last.order.copy(format = (correction as OwnerDeskCorrection.Correction.Format).format)
                host.acceptOwnerTurn(request)
                question = null
                if (next == last.order && last.saved != null) {
                    host.reply(request, "Genau das habe ich gerade gespeichert: „${last.saved.displayName}“ (${what(last.order)}). " +
                        "Was soll ich anders machen?")
                    return true
                }
                host.activity("DOKUMENTE · Korrektur · ${change(last.order, next)}")
                startPage(request, next, replaces = last)
                return true
            }
            OwnerDeskCorrection.Correction.OtherFile -> {
                val found = lastSearch?.takeIf { it.session == session } ?: return false
                val others = found.files.filter { it != found.opened }
                host.acceptOwnerTurn(request)
                when {
                    found.opened == null || others.isEmpty() -> host.reply(request, "Zu „${found.query}“ gab es nur diesen einen " +
                        "Treffer. Wie heißt die Datei, die du meinst?")
                    others.size == 1 -> open(request, others.single(), found)
                    else -> {
                        host.showHits(found.query, found.files)
                        host.reply(request, "Zu „${found.query}“ gab es ${found.files.size} Treffer:\n${numbered(found.files)}\n" +
                            "Welchen meinst du?")
                        ask { s, turn, at -> Question.Hits(s, turn, at, found, found.files, confirm = false) }
                        needs(OwnerLaneOutcome.Need.CHOICE, "Zu „${found.query}“ gab es ${found.files.size} Treffer. Welchen meinst du?")
                    }
                }
                return true
            }
            is OwnerDeskCorrection.Correction.Pick -> {
                val found = lastSearch?.takeIf { it.session == session && it.files.size > 1 } ?: return false
                host.acceptOwnerTurn(request)
                val file = found.files.getOrNull(correction.number - 1)
                if (file == null) host.reply(request, "Diese Auswahl gibt es nicht. Zu „${found.query}“ gab es ${found.files.size} Treffer:\n" +
                    "${numbered(found.files)}\nWelchen meinst du?").also {
                    ask { s, turn, at -> Question.Hits(s, turn, at, found, found.files, confirm = false) }
                } else open(request, file, found)
                return true
            }
            is OwnerDeskCorrection.Correction.Name -> {
                val found = lastSearch?.takeIf { it.session == session }
                val picked = found?.let { OwnerLibraryMatcher.pick(correction.fragment, it.files).singleOrNull() }
                if (picked != null) {
                    host.acceptOwnerTurn(request)
                    open(request, picked, found)
                    return true
                }
                // „Nein, ich meinte das PDF Xenon5“: a named PDF that was not among the hits is a new search.
                if (found == null || !correction.namedPdf) return false
                host.acceptOwnerTurn(request)
                question = null
                startSearch(Search(request, correction.fragment, openWhenClear = true, folderHint = null))
                return true
            }
        }
    }

    /** A correction that reached the desk although it did nothing in the turn before: one short question, or not ours. */
    private fun nothingToCorrect(request: String, correction: OwnerDeskCorrection.Correction): Boolean = when (correction) {
        is OwnerDeskCorrection.Correction.Page ->
            // With a PDF open the page is a matter of the reference registry („mein Bezug ist jetzt Seite 3“).
            if (host.openDocument() != null) false else {
                host.acceptOwnerTurn(request)
                host.reply(request, "Worauf bezieht sich die Seite? Im Moment ist kein PDF geöffnet und kein Seitenauftrag offen.")
                true
            }
        is OwnerDeskCorrection.Correction.Format ->
            if (host.exportAgain(request, correction.format)) true else {
                host.acceptOwnerTurn(request)
                host.reply(request, "Was soll ich als ${correction.format.label} speichern? Ich habe gerade nichts gespeichert, " +
                    "das ich ändern könnte.")
                true
            }
        OwnerDeskCorrection.Correction.OtherFile ->
            // „Nein, ich meinte das andere Dokument“ belongs to the documents of this chat and the work journal, and a
            // bare „die andere“ may answer another lane's question: only a named file or PDF is asked about here.
            if (OwnerCorrectionIntent.parse(request) != null || host.openDocument() != null || !namesFile.containsMatchIn(request)) false else {
                host.acceptOwnerTurn(request)
                host.reply(request, "Welche Datei meinst du? Ich habe gerade keine Trefferliste offen. Sag zum Beispiel " +
                    "„Öffne das PDF Argon4“.")
                true
            }
        is OwnerDeskCorrection.Correction.Pick, is OwnerDeskCorrection.Correction.Name -> false
    }

    // ── Job plumbing ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * One busy job. [finished] gets the work's result – with late = true when the owner's stop arrived after the work
     * was already complete (a written file is then still reported). A stopped job says [stopped] unless a corrected
     * order follows right away.
     */
    private fun <T> launch(request: String, run: Running, stopped: String, work: (OwnerDeskJob) -> T, finished: (T, Boolean) -> Unit) {
        running = run
        host.runJob(request, work) { result, end ->
            if (running === run) running = null
            val next = afterStop
            afterStop = null
            val value = result.getOrNull()
            when {
                end == OwnerDeskJobEnd.SESSION_CHANGED -> host.activity("DOKUMENTE · Gespräch gewechselt · Ergebnis hier nicht gemeldet" +
                    ((value as? PageOutcome.Saved)?.let { " · gespeichert wurde ${it.file.displayName}" } ?: ""))
                result.isSuccess && value != null && (end == OwnerDeskJobEnd.FINISHED || value is PageOutcome.Saved) ->
                    finished(value, end == OwnerDeskJobEnd.STOPPED)
                end == OwnerDeskJobEnd.STOPPED || result.exceptionOrNull() is CancellationException -> {
                    (value as? SearchOutcome.Decided)?.opened?.pdf?.let(host::discardPdf)
                    (value as? Opened)?.pdf?.let(host::discardPdf)
                    host.activity("DOKUMENTE · gestoppt")
                    // A corrected order follows right away: only that one ends the owner's order.
                    if (next == null) { host.say(stopped); host.outcome(OwnerLaneOutcome.Stopped) }
                }
                else -> {
                    val said = "Das hat nicht geklappt: " +
                        NewsBriefing.clean(result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "unbekannter Fehler", 160) +
                        ". Es wurde nichts gespeichert."
                    host.say(said)
                    failed(said)
                }
            }
            if (end != OwnerDeskJobEnd.SESSION_CHANGED) next?.invoke()
        }
    }

    internal companion object {
        private val namesFile = Regex("(?iu)(?<![\\p{L}\\p{N}])(?:datei|pdf|pdf-datei)(?![\\p{L}\\p{N}])")
        const val GRANT_MISSING = "Dafür fehlt mir der Zugriff auf deine Dateien: In Android ist „Zugriff auf alle Dateien“ für " +
            "Continuum nicht erlaubt. Ich habe nichts durchsucht und nichts verändert. Soll ich die Android-Einstellung dafür öffnen?"
        const val PICKER_OFFER = "Soll ich die Android-Dateiauswahl öffnen?"
        const val OPEN_MILLIS = 10 * 60_000L
        private const val MAX_FOLDERS_NAMED = 6
        private const val PROGRESS_PDFS = 100
        private const val PROGRESS_CHARS = 200
        const val STEP_ACCESS = "desk-access"
        const val STEP_SCAN = "desk-scan"
        const val STEP_HITS = "desk-hits"
        const val STEP_OPEN = "desk-open"
        const val STEP_TEXT = "desk-text"
        const val STEP_PAGE = "desk-page"
        const val STEP_SUMMARY = "desk-summary"
        const val STEP_BUILD = "desk-build"
        const val STEP_SAVE = "desk-save"
        const val STEP_VERIFY = "desk-verify"
    }
}
