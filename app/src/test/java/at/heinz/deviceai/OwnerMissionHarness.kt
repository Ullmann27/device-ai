package at.heinz.deviceai

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.BoundedAgentOrchestrator
import at.heinz.deviceai.cognitive.ChainPlan
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.NewsTestFeeds
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerExportCommand
import at.heinz.deviceai.cognitive.OwnerTaskChain
import at.heinz.deviceai.briefing.ContinuumBriefingDirector
import at.heinz.deviceai.briefing.ContinuumBriefingHarness
import at.heinz.deviceai.briefing.ContinuumBriefingHost
import at.heinz.deviceai.gmail.GmailCapability
import at.heinz.deviceai.gmail.MailHarness
import at.heinz.deviceai.gmail.MailLaneOutcome
import at.heinz.deviceai.gmail.MailQuery
import at.heinz.deviceai.gmail.MailRef
import at.heinz.deviceai.sending.FakeSendHost
import at.heinz.deviceai.sending.OwnerMailHandover
import at.heinz.deviceai.sending.OwnerRelation
import at.heinz.deviceai.sending.OwnerSendDirector
import at.heinz.deviceai.sending.OwnerSendGuard
import at.heinz.deviceai.sending.OwnerSendHost
import at.heinz.deviceai.sending.OwnerSendJob
import at.heinz.deviceai.sending.OwnerSendProgress
import at.heinz.deviceai.cognitive.ApprovedWebResearchSession

/**
 * One chat with the mission director and the productive lanes behind it, wired as the Activity wires them: document
 * desk, work tools, send lane and the task chains, one owner-turn counter, one Download folder, one transcript.
 *
 * Everything that decides or words is the productive code – planner, director, every lane's parser, state machine and
 * wording, the real chain through the real orchestrator and ToolGate. Stand-ins are only the phone (the hosts of the
 * lanes' own harnesses) and the model text. Jobs run inline; what the director posts with [later] runs when the
 * current owner turn is completely handled, as on the UI thread.
 */
internal class OwnerMissionHarness(
    mode: InternetAccessMode = InternetAccessMode.ALWAYS_ON,
    /** What survives an app restart: the journal, the phone behind the send lane, and Gmail. See [restarted]. */
    val journalStore: MemoryOwnerMissionJournalStore = MemoryOwnerMissionJournalStore(),
    val sendHost: FakeSendHost = FakeSendHost(),
    val mailHost: MailHarness = MailHarness(now = java.time.Instant.parse("2026-11-19T09:00:00Z"))
) : OwnerMissionHost {
    val toolsHost = OwnerWorkToolsHarness(mode)
    val deskHost = OwnerDocumentDeskHarness(toolsHost)
    /** The briefing lane's own harness: the calendar behind the real briefing director. */
    val briefingHost = ContinuumBriefingHarness()

    /** Everything said to the owner, by whom, in order: `MISSION:…`, `DESK:…`, `TOOLS:…`, `SEND:…`, `OTHER:…`. */
    val said = mutableListOf<String>()
    val activities = mutableListOf<String>()
    /** The mission's own step display, as it was last sent to the work window. */
    var steps: List<String> = emptyList()
    var stageEnded: Boolean? = null
    /** Every event the mission handed to the stage, in order. */
    val staged = mutableListOf<CockpitAnimationEvent>()
    var now = 1_000_000L
    /** The device behind the task chains: tool name → output; a missing tool fails like a device that is not there. */
    val device = HashMap<String, String>()
    var bridgeReady = true
    val chainCalls = mutableListOf<String>()
    var modelPlansOn = false
    /** The cloud brain proposes: one sentence may then also be mapped to a single lane order. */
    var cloudBrain = false
    /** false = the owner ruled the cloud out in this sentence: it must not reach the plan model. */
    var planAllowed: (String) -> Boolean = { true }
    var modelPlan: (String) -> String? = { null }
    val planPrompts = mutableListOf<String>()
    val ordinaryTurns = mutableListOf<String>()
    /** Results the desk showed without saving them (the summary of one page). */
    val presented = mutableListOf<OwnerDeliverable>()
    /**
     * true = as the Activity is wired since 19.11.2026: a summary the desk shows without saving it becomes the chat's
     * result in the work tools ([DeskHost.present]). false = the hook is not connected (the state before).
     */
    var presentWired = true
    /** Real steps the send lane reported for the stage. */
    val sendProgress = mutableListOf<OwnerSendProgress>()
    /** true = the process dies while a send job runs: whatever the job did, nothing is reported any more. */
    var killDuringSend = false
    /** Checked whenever the director posts work for „after this turn“: true = the app is gone from here on. */
    var killWhen: (() -> Boolean)? = null
    var dead = false
        private set
    /** Apps the app lane opened for a step, and why it could not (null = it can). */
    val appsOpened = mutableListOf<String>()
    var appProblem: String? = null
    /** Topics a step researched; [researchSources] sources were really read. */
    val researched = mutableListOf<String>()
    var researchSources = 2
    /** Wall clock of this chat: 19.11.2026, 14:02 in Vienna. */
    var wall: Long = java.time.Instant.parse("2026-11-19T13:02:00Z").toEpochMilli()
    private val queue = ArrayDeque<() -> Unit>()

    private inner class WorkHost : OwnerWorkToolsHost by toolsHost {
        override fun acceptOwnerTurn(request: String) { if (!mission.laneTurn(OwnerMissionLane.WORK)) toolsHost.acceptOwnerTurn(request) }
        override fun reply(request: String, text: String, kind: at.heinz.deviceai.cognitive.NonModelReplyKind) {
            toolsHost.reply(request, text, kind); said += "TOOLS:$text"
        }
        override fun say(text: String) { toolsHost.say(text); said += "TOOLS:$text" }
        override fun outcome(outcome: OwnerLaneOutcome) = mission.laneOutcome(OwnerMissionLane.WORK, outcome)
        override fun offerToOpen(): Boolean = !mission.active
        override fun sendMailYourself(order: OwnerMailHandover): Boolean = send.mail(order, mission.sendGuard())
        /** The Activity reports a web job whose result it drops (stop, revoked grant) as ended. */
        override fun <T> runWebJob(session: ApprovedWebResearchSession, request: String, work: (OwnerWorkWebJob) -> T,
                                   done: (Result<T>) -> Unit) {
            var reported = false
            toolsHost.runWebJob(session, request, work) { reported = true; done(it) }
            if (!reported) outcome(OwnerLaneOutcome.Stopped)
        }
    }

    private inner class DeskHost : OwnerDocumentDeskHost by deskHost {
        override fun acceptOwnerTurn(request: String) { if (!mission.laneTurn(OwnerMissionLane.DESK)) deskHost.acceptOwnerTurn(request) }
        override fun reply(request: String, text: String) { deskHost.reply(request, text); said += "DESK:$text" }
        override fun say(text: String) { deskHost.say(text); said += "DESK:$text" }
        override fun outcome(outcome: OwnerLaneOutcome) = mission.laneOutcome(OwnerMissionLane.DESK, outcome)
        override fun offerToOpen(): Boolean = !mission.active
        // As the Activity's adapter answers it: from the mission itself, not from the offer rule.
        override fun inSequence(): Boolean = mission.active
        override fun present(result: OwnerDeliverable): Boolean {
            presented += result
            if (!presentWired) return false
            // As the Activity's adapter does: the shown summary is the chat's result – the object itself, with its own title.
            tools.adoptDeliverable(result)
            return true
        }
        override fun adopt(result: OwnerDeliverable, file: OwnerSavedFile) { deskHost.adopt(result, file); tools.adopt(result, file, offerToOpen = offerToOpen()) }
        override fun exportAgain(request: String, format: OwnerExportCommand.Format): Boolean = tools.exportAgain(request, format)
    }

    private inner class SendHost : OwnerSendHost by sendHost {
        override val sessionId: String get() = toolsHost.sessionId
        override val ownerTurn: Long get() = toolsHost.ownerTurn
        override fun acceptOwnerTurn(request: String) { if (!mission.laneTurn(OwnerMissionLane.SEND)) toolsHost.acceptOwnerTurn(request) }
        override fun reply(request: String, text: String) { sendHost.reply(request, text); said += "SEND:$text" }
        override fun outcome(outcome: OwnerLaneOutcome) = mission.laneOutcome(OwnerMissionLane.SEND, outcome)
        override fun progress(event: OwnerSendProgress) { sendProgress += event }
        override fun <T> runJob(request: String, work: (OwnerSendJob) -> T, done: (Result<T>) -> Unit) {
            if (!killDuringSend) return sendHost.runJob(request, work, done)
            // The job runs on the phone as far as it gets; the process is gone before anything is reported.
            sendHost.runJob(request, work) { }
            dead = true
        }
    }

    private inner class BriefHost : ContinuumBriefingHost by briefingHost {
        override fun acceptOwnerTurn(request: String) { if (!mission.laneTurn(OwnerMissionLane.BRIEFING)) briefingHost.acceptOwnerTurn(request) }
        override fun answer(request: String?, spoken: String, shown: String) { briefingHost.answer(request, spoken, shown); said += "BRIEFING:$shown" }
        override fun outcome(outcome: OwnerLaneOutcome) = mission.laneOutcome(OwnerMissionLane.BRIEFING, outcome)
    }

    val tools = OwnerWorkTools(WorkHost(), now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
    val desk = OwnerDocumentDesk(DeskHost(), now = { OwnerDocumentDeskHarness.NOW }, zone = { OwnerDocumentDeskHarness.ZONE })
    val send = OwnerSendDirector(SendHost())
    val briefing = ContinuumBriefingDirector(BriefHost(), now = { briefingHost.clock }, zone = { ContinuumBriefingHarness.ZONE })
    val journal = OwnerMissionJournal(journalStore)
    val mission = OwnerMissionDirector(this, journal)

    /**
     * The app after a restart: a new chat with new lanes and nothing in memory – the same journal, the same phone
     * behind the send lane (its counters keep counting) and the same Gmail.
     */
    fun restarted(): OwnerMissionHarness = OwnerMissionHarness(journalStore = journalStore, sendHost = sendHost, mailHost = mailHost)
        .also { it.wall = wall + 5 * 60_000L }

    // ── The owner ───────────────────────────────────────────────────────────────────────────────────────────

    /** One owner turn on a free lane, routed in the Activity's order. Returns who took it. */
    fun owner(text: String): String {
        val route = when {
            mission.intercept(text) -> "MISSION"
            desk.intercept(text) -> "DESK"
            mission.handle(text) -> "MISSION"
            desk.handle(text) -> "DESK"
            send.handle(text) -> "SEND"
            tools.handle(text) -> "TOOLS"
            mission.proposeFromModel(text) -> "MISSION"
            // Where the Activity would start a free model turn: the cloud brain may map the sentence to one order.
            mission.proposeOrderFromModel(text) -> "MISSION"
            // Any other lane or the model: the turn is shown and answered elsewhere.
            else -> { toolsHost.foreignTurn(text, "Das beantworte ich dir gleich."); said += "OTHER:Das beantworte ich dir gleich."; "OTHER" }
        }
        settle()
        return route
    }

    /** An owner turn while a job runs: only the controls the Activity lets through when it is busy. */
    fun interrupt(text: String): Boolean = mission.intercept(text) || desk.intercept(text)

    /** Runs what was posted for „after this turn“ until nothing is left. */
    fun settle() { while (queue.isNotEmpty()) queue.removeFirst().invoke() }

    val missionSaid: List<String> get() = said.filter { it.startsWith("MISSION:") }.map { it.removePrefix("MISSION:") }
    val lastSaid: String get() = said.last().substringAfter(':')
    fun downloads(): List<String> = toolsHost.downloads.keys.toList()

    // ── OwnerMissionHost ────────────────────────────────────────────────────────────────────────────────────

    override val sessionId: String get() = toolsHost.sessionId
    override val assistantName: String? = "Jarvis"
    override fun nowMillis(): Long = now
    override fun acceptOwnerTurn(request: String) = toolsHost.acceptOwnerTurn(request)
    override fun reply(request: String, text: String) { toolsHost.log += "REPLY:$text"; said += "MISSION:$text" }
    override fun say(text: String) { toolsHost.log += "SAY:$text"; said += "MISSION:$text" }
    override fun activity(line: String) { activities += line }
    override fun idle(): Boolean = deskHost.idle()
    override fun later(action: () -> Unit) {
        if (!dead && killWhen?.invoke() == true) dead = true
        if (!dead) queue += action
    }

    /** true = the plan is still being spoken: what waits for its end is kept until [announcementSpoken]. */
    var speakingPlan = false
    private var afterPlan: (() -> Unit)? = null
    override fun afterAnnouncement(action: () -> Unit) { if (speakingPlan) afterPlan = action else later(action) }
    /** The spoken plan has ended: the first step may start. */
    fun announcementSpoken() { speakingPlan = false; afterPlan?.let { queue += it }; afterPlan = null; settle() }

    override fun stage(event: CockpitAnimationEvent) {
        staged += event
        when (event) {
            is CockpitAnimationEvent.ProcessTimeline -> steps = event.steps.map { "${it.label}:${it.state.name}" }
            is CockpitAnimationEvent.TurnEnded -> stageEnded = event.delivered
            else -> Unit
        }
    }

    override fun desk(order: String): Boolean = desk.handle(order)
    override fun deskOpenFound(order: String, name: String, folderHint: String?) = desk.openFound(order, name, folderHint)
    override fun deskIntercept(request: String): Boolean = desk.intercept(request)
    override fun deskCorrect(request: String): Boolean = desk.correct(request)
    override fun openDocumentName(): String? = deskHost.openDocument()?.name
    override fun work(order: String): Boolean = tools.handle(order)
    override fun noBriefingYet(): Boolean = tools.deliverable?.kind != OwnerDeliverable.Kind.NEWS_BRIEFING
    override fun continueOn(result: OwnerDeliverable): Boolean = tools.continueOn(result)
    override fun offerOpening(result: OwnerDeliverable, file: OwnerSavedFile): Boolean = tools.offerOpening(result, file)

    /** The real chain through the real orchestrator and ToolGate; only the device behind the tools is [device]. */
    override fun chain(order: String, plan: ChainPlan): Boolean {
        if (!bridgeReady) {
            mission.laneOutcome(OwnerMissionLane.CHAIN, OwnerLaneOutcome.Failed("Der Gerätezugriff für die Dateisuche ist nicht bereit; es wurde nichts gesucht."))
            return true
        }
        val chain = OwnerTaskChain(plan, order)
        val backend = ToolRunner { request ->
            chainCalls += request.name
            device[request.name] ?: throw ToolBackendFailure(ToolExecutionStatus.FAILED, "Kein Gerät hinter ${request.name}")
        }
        val orchestrator = BoundedAgentOrchestrator(model = chain, memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), backend), evaluator = chain,
            experienceStore = InMemoryExperienceStore(), evaluationConfirmsOutcome = false,
            approvalProvider = chain.approvals { _, _ -> AgentToolApproval(false) },
            limits = OwnerTaskChain.limits(plan), callIdPrefix = "mission-chain-${chainCalls.size}",
            onToolStarted = { _, _ -> chain.toolStarted() })
        val outcome = chain.runWith(orchestrator).outcome
        said += "CHAIN:${outcome.report}"
        mission.chainSettled(chain, outcome)
        return true
    }

    override fun cancelRunning() = deskHost.cancelJob()
    override fun modelPlans(): Boolean = modelPlansOn
    override fun modelOrders(): Boolean = cloudBrain
    override fun mayPlan(request: String): Boolean = planAllowed(request)
    override fun holdSubject() = tools.holdSubject()
    override fun workOnHeld(order: String): Boolean = tools.handleOnHeld(order)
    override fun proposePlan(request: String, prompt: String, done: (String?) -> Unit) { planPrompts += prompt; done(modelPlan(request)) }
    override fun answerWithoutPlan(request: String) { ordinaryTurns += request }

    // ── Steps of the second phase ───────────────────────────────────────────────────────────────────────────

    override fun wallMillis(): Long = wall
    override fun clockLabel(wallMillis: Long): String = java.time.Instant.ofEpochMilli(wallMillis).atZone(ContinuumBriefingHarness.ZONE)
        .let { String.format(java.util.Locale.GERMAN, "%02d:%02d", it.hour, it.minute) }
    override fun today(): java.time.LocalDate = java.time.LocalDate.of(2026, 11, 19)
    override fun missionSend(order: String, channelGiven: Boolean, recipientOverride: String?, guard: OwnerSendGuard): Boolean =
        send.missionSend(order, channelGiven, recipientOverride, guard)
    override fun adoptResult(result: OwnerDeliverable) = tools.adoptDeliverable(result)
    override fun openSaved(order: String, file: OwnerSavedFile): Boolean = tools.openSaved(order, file)
    override fun mailReadable(): Boolean = mailHost.connectionState.connected && mailHost.connectionState.grant.granted(GmailCapability.READ)
    override fun mailSearch(order: String, query: MailQuery, done: (MailLaneOutcome) -> Unit) = mailHost.director.search(order, query, done)
    override fun mailRead(order: String, ref: MailRef, done: (MailLaneOutcome) -> Unit) = mailHost.director.read(order, ref, false, done)
    override fun mailSummarize(order: String, refs: List<MailRef>, done: (MailLaneOutcome) -> Unit) = mailHost.director.summarize(order, refs, done)
    override fun relationName(word: String): String? = OwnerRelation.parse(word)?.let { sendHost.relations.get(it)?.displayName }
    override fun briefing(order: String): Boolean = briefing.handle(order)

    /** The app lane: it opened the app and saw it – or says why not. */
    override fun openApp(order: String, appName: String): Boolean {
        val problem = appProblem
        if (problem == null) appsOpened += appName
        said += "APP:" + (problem ?: "$appName ist geöffnet.")
        mission.laneOutcome(OwnerMissionLane.APP, if (problem == null) OwnerLaneOutcome.Done(OwnerLaneResult.AppOpened(appName, "example." + appName.lowercase()))
            else OwnerLaneOutcome.Failed(problem))
        return true
    }

    /** The research run: [researchSources] sources were read; without one there is no result. */
    override fun research(order: String, topic: String): Boolean {
        researched += topic
        val sources = List(researchSources) { at.heinz.deviceai.cognitive.NewsSourceRef("quelle${it + 1}.example", "Quelle ${it + 1}", "https://quelle${it + 1}.example/a") }
        val result = OwnerDeliverable.ofResearch(topic, RESEARCH_TEXT, sources, today(), wall)
        said += "RESEARCH:" + if (result == null) "Keine Quelle gelesen." else RESEARCH_TEXT
        mission.laneOutcome(OwnerMissionLane.RESEARCH, if (result == null) OwnerLaneOutcome.Failed("Die Recherche hat keine Quelle gelesen.")
            else OwnerLaneOutcome.Done(OwnerLaneResult.Researched(result, topic, sources.size)))
        return true
    }

    companion object {
        const val ANNA = "anna@example.org"
        const val WORD_2 = "Zusammenfassung Argon4 Seite 2 18.11.2026.docx"
        const val NEWS_PDF = "Nachrichten von heute 18.11.2026.pdf"
        const val RESEARCH_TEXT = "Auf dem Gerät laufende Sprachmodelle werden kleiner und sparsamer; mehrere Hersteller " +
            "haben dafür eigene Laufzeiten vorgestellt. Die gelesenen Quellen nennen als nächste Schritte lokale Werkzeugaufrufe."
        const val BRIEFING = "Die Nationalbank von Musterstadt hat den Leitzins auf 2,5 Prozent gesenkt, das berichten die " +
            "tagesschau und der ORF. Außerdem müssen in Testland neue Häuser ab 2028 eine Solaranlage haben. Währenddessen " +
            "warnt ein Bericht vor häufigeren Dürren in Beispielia, wie die Deutsche Welle meldet."
        /** A real `ls -lah` listing of the Download folder: the newest PDF is „Angebot.pdf“. */
        const val DOWNLOAD_LISTING = "total 1.1G\ndrwxrwx--- 2 u0_a290 media_rw 3.4K 2026-11-18 09:12 .\n" +
            "drwxrwx--- 5 u0_a290 media_rw 3.4K 2026-11-01 08:00 ..\n" +
            "-rw-rw---- 1 u0_a290 media_rw 512M 2026-11-17 14:02 continuum-0.3.66.apk\n" +
            "-rw-rw---- 1 u0_a290 media_rw 2.3M 2026-11-16 10:30 Rechnung November.pdf\n" +
            "-rw-rw---- 1 u0_a290 media_rw 1.1M 2026-11-18 08:55 Angebot.pdf\n"
    }
}
