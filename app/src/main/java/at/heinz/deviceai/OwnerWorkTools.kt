package at.heinz.deviceai

import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.NewsBriefing
import at.heinz.deviceai.cognitive.NewsFeed
import at.heinz.deviceai.cognitive.NewsFeedCatalog
import at.heinz.deviceai.cognitive.NewsFeedResult
import at.heinz.deviceai.cognitive.NewsSelection
import at.heinz.deviceai.cognitive.NewsSelector
import at.heinz.deviceai.cognitive.NewsSourceRef
import at.heinz.deviceai.cognitive.NewsStory
import at.heinz.deviceai.cognitive.NonModelReplyKind
import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerDocumentDocx
import at.heinz.deviceai.cognitive.OwnerDocumentLayout
import at.heinz.deviceai.cognitive.OwnerExportCommand
import at.heinz.deviceai.cognitive.OwnerNewsCommand
import at.heinz.deviceai.cognitive.OwnerShareCommand
import at.heinz.deviceai.cognitive.ResearchDialogueHandoff
import at.heinz.deviceai.cognitive.WebToolRequest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.CancellationException

/** One local generation as the work tools see it: the text, or why there is none. */
internal data class OwnerWorkGeneration(val text: String, val failure: String?, val complete: Boolean)

/** What the Activity lends a running web job. Every call is a real action of the existing app paths. */
internal interface OwnerWorkWebJob {
    /** Reads the feeds through the ToolGate under the job's web grant; throws when the grant was revoked. */
    fun readFeeds(feeds: List<NewsFeed>): List<NewsFeedResult>
    /** Blocking generation on the active runtime – the on-device model or the owner's cloud brain (text only, no tools). */
    fun generate(prompt: String): OwnerWorkGeneration
    /** A real work step for the stage and the cockpit timeline. */
    fun step(key: String, label: String, state: StepState, action: CockpitAction? = null)
    fun cancelled(): Boolean
}

/**
 * The small surface the work tools need from the Activity. It keeps [AgentActivity] to thin hooks: every method maps
 * to an existing path (owner turn, non-model reply, web consent, single busy lane, sources window, context handoff).
 */
internal interface OwnerWorkToolsHost {
    val sessionId: String
    /** Number of owner turns shown so far in this chat; unchanged means „the very next turn“. */
    val ownerTurn: Long
    val assistantName: String?
    /** The owner's stored internet setting (restored before it is read). */
    fun internetMode(): InternetAccessMode
    /** Takes the text out of the input field and shows it as the owner's turn. */
    fun acceptOwnerTurn(request: String)
    /** A reply that did not come from the model. */
    fun reply(request: String, text: String, kind: NonModelReplyKind = NonModelReplyKind.READ_ONLY_TOOL)
    /** A finished result as assistant message (shown and spoken). */
    fun say(text: String)
    fun activity(line: String)
    /** The existing web consent: ASK dialog, ALWAYS_ON grant; null = not granted (the notice is already shown). */
    fun confirmWeb(call: WebToolRequest, explanation: String?, notGranted: String?,
                   completed: (ApprovedWebResearchSession?) -> Unit)
    /** The existing research run for one approved request (reading a single article). */
    fun startResearch(session: ApprovedWebResearchSession)
    /**
     * Runs [work] off the UI thread as the single busy job under [session]. [done] is called on the UI thread only
     * while the job is still current (same chat, not stopped, grant not revoked); otherwise the result is dropped.
     */
    fun <T> runWebJob(session: ApprovedWebResearchSession, request: String, work: (OwnerWorkWebJob) -> T,
                      done: (Result<T>) -> Unit)
    fun modelReady(): Boolean
    /** true while the owner's cloud brain answers instead of the on-device model: its text is never called local. */
    fun cloudBrain(): Boolean = false
    /** UTF-8 bytes a text-only task may have for the loaded model. */
    fun promptBudgetBytes(): Int
    fun showSources(sources: List<NewsSourceRef>)
    /** Hands the result to the local dialogue context as external, unverified data. */
    fun rememberResearch(digest: ResearchDialogueHandoff.Digest)

    // ── Export and hand-over of the result ──────────────────────────────────────────────────────────────────
    /** The last assistant answer shown in this chat as plain text, or null. */
    fun lastAssistantReply(): String?
    /** Runs [work] off the UI thread as the single busy job; [done] on the UI thread. */
    fun <T> runFileJob(request: String, work: (OwnerWorkFileJob) -> T, done: (Result<T>) -> Unit)
    /** The blocks as an A4 PDF: bytes and page count. Called inside a file job. */
    fun renderPdf(blocks: List<OwnerDocumentLayout.TextBlock>): Pair<ByteArray, Int>
    /** true when the owner's Download folder already holds a file of that name written by this app. */
    fun downloadNameTaken(displayName: String): Boolean
    /** Writes the whole file into the owner's Download folder or throws with the reason. Called inside a file job. */
    fun saveDownload(displayName: String, mime: String, bytes: ByteArray): OwnerSavedFile
    /** Opens the file in the owner's viewer; null = started, otherwise the reason. */
    fun openFile(file: OwnerSavedFile): String?
    /** Opens the owner's mail app with the prepared message; null = started, otherwise the reason. Sends nothing. */
    fun composeMail(address: String?, subject: String, body: String, attachment: OwnerSavedFile?): String?
    /**
     * The owner's standing „E-Mails über Gmail selbst senden“ grant: true = the send lane took the order over
     * (verifies the Gmail draft on screen, sends, reports). false = no grant; the hand-over below stays as it was.
     */
    fun sendMailYourself(order: at.heinz.deviceai.sending.OwnerMailHandover): Boolean = false
    /** How an order of this lane ended (see [OwnerLaneOutcome]); nobody has to listen. */
    fun outcome(outcome: OwnerLaneOutcome) {}
    /** false while a sequence of orders goes on right after a save: „Soll ich sie öffnen?“ is then not asked in between. */
    fun offerToOpen(): Boolean = true
}

/** What the Activity lends a running file job. */
internal interface OwnerWorkFileJob {
    fun step(key: String, label: String, state: StepState, action: CockpitAction? = null)
}

/** A file this app wrote for the owner. [uri]: content URI other apps may be granted to read; null when there is none. */
internal data class OwnerSavedFile(val displayName: String, val folder: String, val uri: String?, val mime: String)

/**
 * Owner work tools (18.11.2026): the news briefing, and on its result „speichere das als PDF/Word“ and „schick das
 * per Mail an …“. This class decides and words; reading, generating and showing go through [OwnerWorkToolsHost].
 * It is free of Android so the whole chain runs in host tests with the real parsers, selector and wording.
 */
internal class OwnerWorkTools(
    private val host: OwnerWorkToolsHost,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    private class Briefed(val session: String, val turn: Long, val stories: List<NewsStory>, val atMillis: Long)
    private class Saved(val session: String, val of: OwnerDeliverable, val file: OwnerSavedFile)
    private class OpenOffer(val session: String, val turn: Long, val file: OwnerSavedFile)

    private var briefed: Briefed? = null
    private var lastDeliverable: Pair<String, OwnerDeliverable>? = null
    /** The owner turn up to which the deliverable was the subject without another lane in between. */
    private var subjectThrough = -1L
    private var saved: Saved? = null
    private var openOffer: OpenOffer? = null
    private class Draft(val session: String, val turn: Long, val name: String, val text: String, val complete: Boolean,
                        val cloud: Boolean?, val atMillis: Long)
    private var draft: Draft? = null

    /**
     * The summary draft of the PDF [documentName] was just shown in this chat (field finding 19.11.2026: „Erstelle
     * bitte eine pdf“ right after it found nothing to save). Session-only; it is what an order to save means while
     * it is the newest result of the chat. [cloud]: which brain wrote it – null when that is not known (a draft from
     * the work journal shown again); the saved file then names no author.
     */
    fun noteDocumentDraft(documentName: String, text: String, complete: Boolean, cloud: Boolean? = null) {
        if (text.isBlank()) return
        draft = Draft(host.sessionId, host.ownerTurn, documentName, text.trim(), complete, cloud, now().toEpochMilli())
    }

    /** The owner edited the shown draft: the edited text is what gets saved. */
    fun reviseDocumentDraft(text: String) {
        val last = draft?.takeIf { it.session == host.sessionId } ?: return
        if (text.isNotBlank()) draft = Draft(last.session, last.turn, last.name, text.trim(), last.complete, last.cloud, last.atMillis)
    }

    /** The last deliverable of the current chat, if any. */
    val deliverable: OwnerDeliverable? get() = lastDeliverable?.takeIf { it.first == host.sessionId }?.second

    /**
     * A result another owner lane produced and saved in this chat (the document desk's page summary). From now on it
     * is what „öffne die gespeicherte Datei“, „ja“ after the offer and „schick das per Mail an …“ refer to.
     */
    fun adopt(result: OwnerDeliverable, file: OwnerSavedFile, offerToOpen: Boolean) {
        lastDeliverable = host.sessionId to result
        subjectThrough = host.ownerTurn
        saved = Saved(host.sessionId, result, file)
        openOffer = if (offerToOpen && file.uri != null) OpenOffer(host.sessionId, host.ownerTurn, file) else null
    }

    /**
     * A sequence of owner orders continues on [result] (a step's real outcome): true when it still is this chat's
     * deliverable – it is then what „das“ and „sie“ of the next order mean, and the offer to open the saved file is
     * over, because the sequence goes on instead of waiting for that answer. false = something newer took its place.
     */
    fun continueOn(result: OwnerDeliverable): Boolean {
        if (deliverable !== result) return false
        subjectThrough = host.ownerTurn
        openOffer = null
        return true
    }

    /**
     * The sequence that saved [file] has ended with that save: the offer to open it is made now. false = [result] is
     * no longer this chat's deliverable, or the file cannot be handed to a viewer.
     */
    fun offerOpening(result: OwnerDeliverable, file: OwnerSavedFile): Boolean {
        if (deliverable !== result || file.uri == null) return false
        subjectThrough = host.ownerTurn
        openOffer = OpenOffer(host.sessionId, host.ownerTurn, file)
        return true
    }

    /**
     * „Ich meinte als PDF“ directly after a save: the same result once more in [format]. false = the last turn was
     * not a save of this lane; nothing happened.
     */
    fun exportAgain(request: String, format: OwnerExportCommand.Format): Boolean {
        val last = saved?.takeIf { it.session == host.sessionId } ?: return false
        if (subjectThrough != host.ownerTurn || deliverable !== last.of) return false
        export(request, OwnerExportCommand.Request(format, null, viaWord = false, aboutNews = false, original = request), subject = true)
        subjectThrough = host.ownerTurn
        return true
    }

    /**
     * true = [request] was an order for a work tool and is completely handled (a reply was or will be given).
     * Call on the UI thread while no other job runs. false = not a work-tool order; nothing happened.
     */
    fun handle(request: String): Boolean = handle(request, host.ownerTurn)

    private class Held(val session: String, val turn: Long, val reply: String?)
    private var held: Held? = null

    /**
     * The owner's sentence that is about to be shown will be carried out later – after a plan was announced, a
     * question was asked or his „Ja“ was shown. What the chat is about right now is kept for [handleOnHeld], so
     * „das“ still means what it meant when he said it and not a line the app showed in between.
     */
    fun holdSubject() { held = Held(host.sessionId, host.ownerTurn, host.lastAssistantReply()) }

    /** [handle] for an order of the sentence [holdSubject] was called for; without one it is [handle]. */
    fun handleOnHeld(request: String): Boolean =
        handle(request, held?.takeIf { it.session == host.sessionId }?.turn ?: host.ownerTurn)

    private fun handle(request: String, said: Long): Boolean {
        val subject = deliverable != null && subjectThrough == said
        val handled = route(request, subject, said)
        // A work-tool turn keeps its result the subject of the conversation; a turn of any other lane ends that.
        if (handled && deliverable != null) subjectThrough = host.ownerTurn
        return handled
    }

    private fun route(request: String, subject: Boolean, said: Long = host.ownerTurn): Boolean {
        val name = host.assistantName
        OwnerNewsCommand.parse(request, name)?.let { startBriefing(request, it); return true }
        (OwnerExportCommand.parse(request, name) ?: OwnerExportCommand.parseMake(request, name))
            ?.let { export(request, it, subject, said); return true }
        OwnerShareCommand.parse(request, name)?.let { share ->
            // „Schick das an Anna“ without „Mail“ is only a mail order while the result is what we talk about.
            if (share.mailNamed || subject) { mail(request, share, subject, said); return true }
        }
        if (answerOpenOffer(request)) return true
        return handleFollowUp(request, name)
    }

    private fun handleFollowUp(request: String, name: String?): Boolean {
        val last = briefed?.takeIf { it.session == host.sessionId && now().toEpochMilli() - it.atMillis <= FOLLOW_UP_MILLIS }
            ?: return false
        val immediate = last.turn == host.ownerTurn
        when (val reply = OwnerNewsCommand.parseFollowUp(request, name, immediate, NewsSelector.briefedWords(last.stories))) {
            null -> return false
            is OwnerNewsCommand.FollowUp.Topics -> startBriefing(request, reply.request)
            is OwnerNewsCommand.FollowUp.Story -> readStory(request, last, reply.number)
            OwnerNewsCommand.FollowUp.AskWhich -> {
                host.acceptOwnerTurn(request)
                host.reply(request, NewsBriefing.askWhichTopic())
                // The question stays open for exactly one more turn.
                briefed = Briefed(last.session, host.ownerTurn, last.stories, last.atMillis)
            }
            OwnerNewsCommand.FollowUp.Decline -> {
                host.acceptOwnerTurn(request)
                host.reply(request, NewsBriefing.declined())
            }
        }
        return true
    }

    private fun startBriefing(request: String, command: OwnerNewsCommand.Request) {
        host.acceptOwnerTurn(request)
        val mode = host.internetMode()
        if (mode == InternetAccessMode.OFF) {
            host.activity("WEB · AUS · keine Anfrage gesendet")
            host.reply(request, OwnerNewsCommand.offlineReply())
            host.outcome(OwnerLaneOutcome.Failed(OwnerNewsCommand.offlineReply()))
            return
        }
        val feeds = NewsFeedCatalog.feedsFor(command.sections)
        val outlets = feeds.map { it.outlet }.distinct().joinToString(", ")
        host.activity("NACHRICHTEN · " + (if (mode == InternetAccessMode.ALWAYS_ON) "dauerhafte Freigabe" else "einmalige Freigabe nötig") +
            " · öffentliche Feeds: $outlets")
        host.confirmWeb(WebToolRequest.Search(OwnerNewsCommand.GRANT_LABEL),
            explanation = "Für die Nachrichten lese ich die öffentlichen Feeds von $outlets. Es wird nur gelesen: " +
                "keine Suchanfrage, kein Konto und nichts aus deinem Auftrag wird übertragen. ",
            notGranted = "Nachrichten nicht abgerufen: Der Internetzugriff wurde für diese Anfrage nicht freigegeben.") { session ->
            if (session == null) {
                host.activity("WEB · nicht freigegeben · keine Anfrage gesendet")
                host.outcome(OwnerLaneOutcome.Failed("Der Internetzugriff wurde für die Nachrichten nicht freigegeben."))
            } else host.runWebJob(session, request, { job -> brief(job, request, command, feeds) }) { result ->
                result.onSuccess { publish(request, command, it) }
                    .onFailure {
                        host.say(NewsBriefing.failed(it.message))
                        host.outcome(OwnerLaneOutcome.Failed(NewsBriefing.failed(it.message)))
                    }
            }
        }
    }

    private sealed interface Outcome {
        data class Message(val text: String) : Outcome
        data class Briefing(val result: NewsBriefing.Result, val selection: NewsSelection,
                            val brain: NewsBriefing.Brain = NewsBriefing.Brain.LOCAL) : Outcome
    }

    /** Off the UI thread: Quellen abrufen → Meldungen auswählen → Zusammenfassen. */
    private fun brief(job: OwnerWorkWebJob, request: String, command: OwnerNewsCommand.Request, feeds: List<NewsFeed>): Outcome {
        job.step(STEP_FETCH, "Quellen abrufen", StepState.ACTIVE, CockpitAction.RESEARCH)
        val results = try { job.readFeeds(feeds) } catch (error: Exception) {
            job.step(STEP_FETCH, "Quellen abrufen", StepState.FAILED)
            throw error
        }
        val time = now()
        val zoneId = zone()
        val date = time.atZone(zoneId).toLocalDate()
        val selection = NewsSelector.select(results, command, time, zoneId)
        if (selection.readOutlets.isEmpty()) {
            job.step(STEP_FETCH, "Quellen abrufen", StepState.FAILED)
            return Outcome.Message(NewsBriefing.nothingReachable(selection))
        }
        job.step(STEP_FETCH, "Quellen abrufen", StepState.DONE)
        job.step(STEP_SELECT, "Meldungen auswählen", StepState.ACTIVE)
        if (selection.stories.isEmpty()) {
            job.step(STEP_SELECT, "Keine passende Meldung", StepState.DONE)
            return Outcome.Message(NewsBriefing.nothingFound(selection, command))
        }
        job.step(STEP_SELECT, "Meldungen auswählen", StepState.DONE)
        job.step(STEP_SUMMARY, "Zusammenfassen", StepState.ACTIVE, CockpitAction.THINK)
        val brain = if (host.cloudBrain()) NewsBriefing.Brain.CLOUD else NewsBriefing.Brain.LOCAL
        fun headlines(reason: NewsBriefing.Fallback): Outcome {
            job.step(STEP_SUMMARY, "Schlagzeilen zusammenstellen", StepState.DONE)
            return Outcome.Briefing(NewsBriefing.headlineBriefing(selection, command, reason, date, time.toEpochMilli(), brain),
                selection, brain)
        }
        if (!host.modelReady()) return headlines(NewsBriefing.Fallback.MODEL_NOT_READY)
        val prompt = NewsBriefing.prompt(selection.stories, command, date,
            host.promptBudgetBytes().coerceAtMost(NewsBriefing.MAX_PROMPT_BYTES))
            ?: return headlines(NewsBriefing.Fallback.MODEL_FAILED)
        val generated = job.generate(prompt.text)
        if (job.cancelled()) throw CancellationException("Nachrichten abgebrochen")
        if (generated.failure != null || !generated.complete) return headlines(NewsBriefing.fallbackFor(generated.failure))
        val accepted = NewsBriefing.accept(request, generated.text, prompt, date)
            ?: return headlines(NewsBriefing.Fallback.OUTPUT_REJECTED)
        job.step(STEP_SUMMARY, "Zusammenfassen", StepState.DONE)
        return Outcome.Briefing(NewsBriefing.modelBriefing(accepted, prompt, selection, command, date, time.toEpochMilli(), brain),
            selection, brain)
    }

    private fun publish(request: String, command: OwnerNewsCommand.Request, outcome: Outcome) {
        when (outcome) {
            is Outcome.Message -> {
                host.reply(request, outcome.text)
                host.outcome(OwnerLaneOutcome.Failed(outcome.text))
            }
            is Outcome.Briefing -> {
                val result = outcome.result
                host.say(result.text)
                host.showSources(result.sources)
                lastDeliverable = host.sessionId to result.deliverable
                subjectThrough = host.ownerTurn
                openOffer = null
                briefed = Briefed(host.sessionId, host.ownerTurn, result.stories, now().toEpochMilli())
                NewsBriefing.dialogueDigest(result, command, result.deliverable.createdAtMillis)?.let(host::rememberResearch)
                host.activity("NACHRICHTEN · ${briefed?.stories?.size ?: 0} Meldungen · ${result.sources.size} Quellen · " +
                    if (result.modelSummary) outcome.brain.logLabel else "Schlagzeilen ohne Modell")
                host.outcome(OwnerLaneOutcome.Done(OwnerLaneResult.Briefing(result.deliverable, result.stories.size,
                    result.sources.size, result.modelSummary)))
            }
        }
    }

    /** „Mehr zur zweiten Meldung“: the article itself is read through the existing single-source research path. */
    private fun readStory(request: String, last: Briefed, number: Int) {
        host.acceptOwnerTurn(request)
        val story = if (number == -1) last.stories.lastOrNull() else last.stories.getOrNull(number - 1)
        if (story == null) {
            host.reply(request, "Ich habe dir ${last.stories.size} Meldungen genannt. Welche davon meinst du?")
            briefed = Briefed(last.session, host.ownerTurn, last.stories, last.atMillis)
            return
        }
        if (host.internetMode() == InternetAccessMode.OFF) {
            host.reply(request, OwnerNewsCommand.offlineReply())
            return
        }
        host.activity("NACHRICHTEN · nachlesen · ${story.outlet}")
        host.confirmWeb(WebToolRequest.Read(story.link), explanation = null,
            notGranted = "Nicht nachgelesen: Der Internetzugriff wurde für diese Meldung nicht freigegeben.") { session ->
            if (session == null) host.activity("WEB · nicht freigegeben · keine Anfrage gesendet") else host.startResearch(session)
        }
    }

    // ── „Speichere das als PDF/Word“ ────────────────────────────────────────────────────────────────────────

    /**
     * What „das“ means: the result we are talking about; the news when the owner names them; otherwise the last
     * assistant answer of the chat. Null when there is nothing worth keeping.
     */
    private fun subjectOf(aboutNews: Boolean, subject: Boolean, unpointed: Boolean = false, said: Long = host.ownerTurn): OwnerDeliverable? {
        val current = deliverable
        if (current != null && (subject || aboutNews)) return current
        val time = now()
        // An order that is carried out later means the answer that was the last one when the owner gave it.
        val kept = held?.takeIf { it.session == host.sessionId && it.turn == said && said != host.ownerTurn }
        val reply = if (kept != null) kept.reply else host.lastAssistantReply()
        // The summary draft of the open PDF: on the very next turn, while it is the last answer of the chat, or –
        // for „Erstelle eine PDF“ without a pointing word – while it is the chat's newest piece of work.
        draft?.takeIf { it.session == host.sessionId && time.toEpochMilli() - it.atMillis in 0..FOLLOW_UP_MILLIS }?.let { last ->
            val newest = current == null || current.createdAtMillis <= last.atMillis
            val meant = last.turn == said || reply?.trim() == last.text || (unpointed && newest)
            if (meant) OwnerDeliverable.ofDocumentDraft(last.name, last.text, last.complete, last.cloud,
                time.atZone(zone()).toLocalDate(), time.toEpochMilli())?.let { return it }
        }
        return reply?.let { OwnerDeliverable.ofReply(it, time.atZone(zone()).toLocalDate(), time.toEpochMilli()) } ?: current
    }

    private class Exported(val file: OwnerSavedFile, val pages: Int?)

    private fun export(request: String, command: OwnerExportCommand.Request, subject: Boolean, said: Long = host.ownerTurn) {
        val target = subjectOf(command.aboutNews, subject, command.unpointed, said)
        host.acceptOwnerTurn(request)
        if (target == null && command.unpointed) {
            // Not „kann ich nicht“: the app can make the file, it only needs to know of what.
            val ask = "Gern – was soll in der ${command.format.label} stehen? Sag zum Beispiel „Fass mir die Nachrichten von " +
                "heute zusammen“ oder lass mich ein PDF zusammenfassen; das Ergebnis speichere ich dir dann als ${command.format.label}."
            host.reply(request, ask)
            host.outcome(OwnerLaneOutcome.Failed("Es gibt in diesem Gespräch noch nichts, das in die Datei könnte."))
            return
        }
        if (target == null) {
            host.reply(request, "In diesem Gespräch gibt es noch nichts, das ich speichern könnte. Sag zum Beispiel " +
                "„Fass mir die Nachrichten von heute zusammen“ – danach speichere ich sie dir als PDF oder Word-Datei.")
            host.outcome(OwnerLaneOutcome.Failed("In diesem Gespräch gibt es noch nichts, das ich speichern könnte."))
            return
        }
        lastDeliverable = host.sessionId to target
        val format = command.format
        host.activity("EXPORT · ${format.label} · Download-Ordner")
        host.runFileJob(request, { job ->
            job.step(STEP_BUILD, "Dokument aufbauen", StepState.ACTIVE, CockpitAction.TOOL)
            val blocks = OwnerDocumentLayout.blocks(target)
            val (bytes, pages) = try {
                when (format) {
                    OwnerExportCommand.Format.PDF -> host.renderPdf(blocks).let { it.first to it.second as Int? }
                    OwnerExportCommand.Format.DOCX -> OwnerDocumentDocx.write(blocks) to null
                }
            } catch (error: Exception) {
                job.step(STEP_BUILD, "Dokument aufbauen", StepState.FAILED)
                throw error
            }
            job.step(STEP_BUILD, "Dokument aufbauen", StepState.DONE)
            job.step(STEP_SAVE, "Im Download-Ordner speichern", StepState.ACTIVE)
            try {
                val name = OwnerExportCommand.fileName(command.name, target.title, target.date, format, host::downloadNameTaken)
                Exported(host.saveDownload(name, format.mime, bytes), pages).also {
                    job.step(STEP_SAVE, "Im Download-Ordner speichern", StepState.DONE)
                }
            } catch (error: Exception) {
                job.step(STEP_SAVE, "Im Download-Ordner speichern", StepState.FAILED)
                throw error
            }
        }) { result ->
            result.onSuccess { exported ->
                val file = exported.file
                saved = Saved(host.sessionId, target, file)
                val openable = file.uri != null && host.offerToOpen()
                openOffer = if (openable) OpenOffer(host.sessionId, host.ownerTurn, file) else null
                host.say(savedMessage(target, command, exported, openable))
                host.activity("EXPORT · gespeichert · ${file.displayName}")
                host.outcome(OwnerLaneOutcome.Done(OwnerLaneResult.FileSaved(file, target, null, null)))
            }.onFailure { error ->
                val failed = "Das Speichern hat nicht geklappt: " + NewsBriefing.clean(error.message ?: error.javaClass.simpleName, 160) +
                    ". Es wurde keine Datei angelegt."
                host.say(failed)
                host.activity("EXPORT · fehlgeschlagen")
                host.outcome(OwnerLaneOutcome.Failed(failed))
            }
        }
    }

    private fun savedMessage(target: OwnerDeliverable, command: OwnerExportCommand.Request, exported: Exported, openable: Boolean): String {
        val file = exported.file
        val what = when (target.kind) {
            OwnerDeliverable.Kind.NEWS_BRIEFING -> ""
            OwnerDeliverable.Kind.ASSISTANT_REPLY -> " Darin steht meine letzte Antwort aus diesem Gespräch."
            OwnerDeliverable.Kind.PAGE_SUMMARY -> " Darin steht die Seitenzusammenfassung aus diesem Gespräch."
            OwnerDeliverable.Kind.DOCUMENT_DRAFT -> " Darin steht der Entwurf der Zusammenfassung aus diesem Gespräch; das PDF steht als Quelle dabei."
        }
        val shape = listOfNotNull(
            exported.pages?.let { if (it == 1) "1 Seite" else "$it Seiten" } ?: command.format.label,
            "mit Quellenliste".takeIf { target.sources.isNotEmpty() }).joinToString(", ")
        val place = if (file.uri != null) "„${file.displayName}“ liegt jetzt in deinem Download-Ordner ($shape)."
            else "„${file.displayName}“ ist gespeichert ($shape), aber nicht im Download-Ordner: Auf dieser Android-Version " +
                "darf ich dort nicht schreiben. Die Datei liegt im ${file.folder}."
        val word = if (command.viaWord) " Word selbst habe ich dafür nicht geöffnet – ich habe das Dokument direkt erstellt. " +
            "Mit „als Word-Datei speichern“ bekommst du zusätzlich die Word-Fassung." else ""
        return "Erledigt. $place$what$word" + if (openable) " Soll ich sie öffnen?" else ""
    }

    /** The owner's answer to „Soll ich sie öffnen?“, or an explicit „öffne die gespeicherte Datei“. */
    private fun answerOpenOffer(request: String): Boolean {
        val text = OwnerAppLookupIntent.stripAddress(request.trim(), host.assistantName).lowercase(Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N} -]+"), " ").replace(Regex("\\s+"), " ").trim()
        val offer = openOffer?.takeIf { it.session == host.sessionId && it.turn == host.ownerTurn }
        val file = when {
            offer != null && openYes.matches(text) -> offer.file
            openSaved.matches(text) -> saved?.takeIf { it.session == host.sessionId && it.file.uri != null }?.file
            offer != null && openNo.matches(text) -> {
                host.acceptOwnerTurn(request)
                host.reply(request, "Alles klar, sie bleibt im Download-Ordner.")
                return true
            }
            else -> null
        } ?: return false
        host.acceptOwnerTurn(request)
        val problem = host.openFile(file)
        val said = if (problem == null) "„${file.displayName}“ ist geöffnet."
            else "Öffnen hat nicht geklappt: ${NewsBriefing.clean(problem, 160)}. Die Datei liegt weiter im Download-Ordner."
        host.reply(request, said)
        host.outcome(if (problem == null) OwnerLaneOutcome.Done(OwnerLaneResult.FileOpened(file)) else OwnerLaneOutcome.Failed(said))
        return true
    }

    // ── „Schick das per Mail an …“ (hand-over only) ─────────────────────────────────────────────────────────

    private fun mail(request: String, command: OwnerShareCommand.Request, subject: Boolean, said: Long = host.ownerTurn) {
        val target = subjectOf(aboutNews = false, subject = subject, said = said)
        host.acceptOwnerTurn(request)
        if (target == null) {
            host.reply(request, "In diesem Gespräch gibt es noch nichts, das ich verschicken könnte. Es wurde nichts vorbereitet.")
            host.outcome(OwnerLaneOutcome.Failed("In diesem Gespräch gibt es noch nichts, das ich verschicken könnte."))
            return
        }
        lastDeliverable = host.sessionId to target
        val attachment = saved?.takeIf { it.session == host.sessionId && it.of === target && it.file.uri != null }?.file
        val title = target.title + " " + OwnerDeliverable.SHORT_DATE.format(target.date)
        if (host.sendMailYourself(at.heinz.deviceai.sending.OwnerMailHandover(request, command.address,
                command.recipientName, title, target.plainText, attachment))) return
        host.activity("E-MAIL · Übergabe an das Mailprogramm · nichts wird gesendet")
        val problem = host.composeMail(command.address, title, target.plainText, attachment)
        if (problem != null) {
            val failed = "Die E-Mail konnte ich nicht vorbereiten: ${NewsBriefing.clean(problem, 160)}. Es wurde nichts gesendet."
            host.reply(request, failed)
            host.outcome(OwnerLaneOutcome.Failed(failed))
            return
        }
        val file = if (attachment != null) ", „${attachment.displayName}“ hängt an"
            else ", ohne Anhang – gespeichert ist das Dokument noch nicht (sag „speichere das als PDF“, dann hänge ich es beim nächsten Mal an)"
        val to = command.address?.let { " Empfänger: $it." }
            ?: " Für „${command.recipientName}“ habe ich keine Adresse – trag sie bitte im Mailprogramm ein; auf deine Kontakte greife ich nicht zu."
        host.reply(request, "Die E-Mail ist vorbereitet: Betreff „$title“, der Text steht drin$file.$to " +
            "Gesendet habe ich nichts – du musst im Mailprogramm nur noch auf Senden tippen.")
        host.outcome(OwnerLaneOutcome.Done(OwnerLaneResult.Delivered("Mailprogramm", command.address ?: command.recipientName.orEmpty(),
            attachment, OwnerLaneResult.Delivered.State.HANDED_OVER)))
    }

    private companion object {
        const val STEP_BUILD = "export-build"
        const val STEP_SAVE = "export-save"
        val openYes = Regex("^(?:ja|jo|ja bitte|bitte|gern|gerne|ja gern|ja gerne|klar|ja klar|mach|ja mach|ok|okay|" +
            "(?:ja )?(?:bitte )?öffne(?:n)?(?: sie| es| die datei| das dokument| das pdf)?(?: bitte)?|mach (?:sie |es )?auf|" +
            "zeig (?:sie |es )?(?:mir|her)(?: bitte)?)$")
        val openNo = Regex("^(?:nein|nö|nee|nein danke|danke|danke nein|passt|später|nicht nötig|lass|lass es)$")
        val openSaved = Regex("^(?:bitte )?(?:öffne|zeig(?:e)?)(?: mir)? (?:die|das) (?:gerade |eben |soeben )?gespeicherte " +
            "(?:datei|pdf|pdf-datei|word-datei|dokument|zusammenfassung)(?: bitte)?$")
        const val FOLLOW_UP_MILLIS = 30 * 60_000L
        const val STEP_FETCH = "news-fetch"
        const val STEP_SELECT = "news-select"
        const val STEP_SUMMARY = "news-summary"
    }
}
