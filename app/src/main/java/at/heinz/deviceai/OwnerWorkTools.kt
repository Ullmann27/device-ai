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
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerNewsCommand
import at.heinz.deviceai.cognitive.ResearchDialogueHandoff
import at.heinz.deviceai.cognitive.WebToolRequest
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CancellationException

/** One local generation as the work tools see it: the text, or why there is none. */
internal data class OwnerWorkGeneration(val text: String, val failure: String?, val complete: Boolean)

/** What the Activity lends a running web job. Every call is a real action of the existing app paths. */
internal interface OwnerWorkWebJob {
    /** Reads the feeds through the ToolGate under the job's web grant; throws when the grant was revoked. */
    fun readFeeds(feeds: List<NewsFeed>): List<NewsFeedResult>
    /** Blocking generation on the active local runtime (text only, no tools). */
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
    /** UTF-8 bytes a text-only task may have for the loaded model. */
    fun promptBudgetBytes(): Int
    fun showSources(sources: List<NewsSourceRef>)
    /** Hands the result to the local dialogue context as external, unverified data. */
    fun rememberResearch(digest: ResearchDialogueHandoff.Digest)
}

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

    private var briefed: Briefed? = null
    private var lastDeliverable: Pair<String, OwnerDeliverable>? = null

    /** The last deliverable of the current chat, if any. */
    val deliverable: OwnerDeliverable? get() = lastDeliverable?.takeIf { it.first == host.sessionId }?.second

    /**
     * true = [request] was an order for a work tool and is completely handled (a reply was or will be given).
     * Call on the UI thread while no other job runs. false = not a work-tool order; nothing happened.
     */
    fun handle(request: String): Boolean {
        val name = host.assistantName
        OwnerNewsCommand.parse(request, name)?.let { startBriefing(request, it); return true }
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
            if (session == null) host.activity("WEB · nicht freigegeben · keine Anfrage gesendet")
            else host.runWebJob(session, request, { job -> brief(job, request, command, feeds) }) { result ->
                result.onSuccess { publish(request, command, it) }
                    .onFailure { host.say(NewsBriefing.failed(it.message)) }
            }
        }
    }

    private sealed interface Outcome {
        data class Message(val text: String) : Outcome
        data class Briefing(val result: NewsBriefing.Result, val selection: NewsSelection) : Outcome
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
        fun headlines(reason: NewsBriefing.Fallback): Outcome {
            job.step(STEP_SUMMARY, "Schlagzeilen zusammenstellen", StepState.DONE)
            return Outcome.Briefing(NewsBriefing.headlineBriefing(selection, command, reason, date, time.toEpochMilli()), selection)
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
        return Outcome.Briefing(NewsBriefing.modelBriefing(accepted, prompt, selection, command, date, time.toEpochMilli()), selection)
    }

    private fun publish(request: String, command: OwnerNewsCommand.Request, outcome: Outcome) {
        when (outcome) {
            is Outcome.Message -> host.reply(request, outcome.text)
            is Outcome.Briefing -> {
                val result = outcome.result
                host.say(result.text)
                host.showSources(result.sources)
                lastDeliverable = host.sessionId to result.deliverable
                briefed = Briefed(host.sessionId, host.ownerTurn, result.stories, now().toEpochMilli())
                NewsBriefing.dialogueDigest(result, command, result.deliverable.createdAtMillis)?.let(host::rememberResearch)
                host.activity("NACHRICHTEN · ${briefed?.stories?.size ?: 0} Meldungen · ${result.sources.size} Quellen · " +
                    if (result.modelSummary) "lokale Zusammenfassung" else "Schlagzeilen ohne Modell")
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

    private companion object {
        const val FOLLOW_UP_MILLIS = 30 * 60_000L
        const val STEP_FETCH = "news-fetch"
        const val STEP_SELECT = "news-select"
        const val STEP_SUMMARY = "news-summary"
    }
}
