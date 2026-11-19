package at.heinz.deviceai

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.MissionOrder
import at.heinz.deviceai.cockpit.MissionOrderState
import at.heinz.deviceai.cockpit.MissionOrdersEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cognitive.ChainArgument
import at.heinz.deviceai.cognitive.ChainPlan
import at.heinz.deviceai.cognitive.NewsBriefing
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerClause
import at.heinz.deviceai.cognitive.OwnerDeskReply
import at.heinz.deviceai.cognitive.OwnerLibraryMatcher
import at.heinz.deviceai.cognitive.OwnerMissionCorrection
import at.heinz.deviceai.cognitive.OwnerMissionModelPlan
import at.heinz.deviceai.cognitive.OwnerMissionPartial
import at.heinz.deviceai.cognitive.OwnerMissionPlan
import at.heinz.deviceai.cognitive.OwnerMissionPlanCall
import at.heinz.deviceai.cognitive.OwnerStepSlot
import at.heinz.deviceai.gmail.MailDay
import at.heinz.deviceai.gmail.MailLaneOutcome
import at.heinz.deviceai.gmail.MailLaneResult
import at.heinz.deviceai.gmail.MailQuery
import at.heinz.deviceai.gmail.MailRef
import at.heinz.deviceai.sending.OwnerSendGuard
import at.heinz.deviceai.sending.SendPlan
import at.heinz.deviceai.cognitive.OwnerMissionPlanner
import at.heinz.deviceai.cognitive.OwnerMissionStep
import at.heinz.deviceai.cognitive.OwnerSentenceReading
import at.heinz.deviceai.cognitive.OwnerTaskChain
import at.heinz.deviceai.cognitive.OwnerTaskChainContinuation
import java.util.Locale

/** The owner lanes a mission step can run through. */
internal enum class OwnerMissionLane { DESK, WORK, SEND, CHAIN, DEEPCLEAN, MAIL, BRIEFING, APP, RESEARCH }

/**
 * The small surface a mission needs from the Activity: the chat, the single work lane, and one entry per owner lane.
 * Every lane entry is the lane's own entry point for an owner order – a mission has no other way to act.
 */
internal interface OwnerMissionHost {
    val sessionId: String
    val assistantName: String?
    fun nowMillis(): Long
    /** Takes the text out of the input field and shows it as the owner's turn. */
    fun acceptOwnerTurn(request: String)
    /** A reply to the owner's turn that did not come from the model. */
    fun reply(request: String, text: String)
    /** A report as assistant message (shown and spoken). */
    fun say(text: String)
    fun activity(line: String)
    /** true when no job occupies the single work lane. */
    fun idle(): Boolean
    /** Runs [action] on the UI thread after everything that is being handled right now has finished. */
    fun later(action: () -> Unit)
    /** Runs [action] on the UI thread after about [millis]; a host without a clock runs it like [later]. */
    fun afterMillis(millis: Long, action: () -> Unit) = later(action)
    /**
     * Runs [action] once the spoken plan has been said – at once when nothing is spoken, and never later than a
     * bounded wait. Step 1's first message would otherwise cut the announcement off (every reply stops the one before).
     */
    fun afterAnnouncement(action: () -> Unit) = later(action)
    /** The mission's own steps for the work window. */
    fun stage(event: CockpitAnimationEvent) {}

    /** The document desk's owner entry; true = it took [order]. */
    fun desk(order: String): Boolean
    /** The desk opens the file another step really found. */
    fun deskOpenFound(order: String, name: String, folderHint: String?)
    /** The desk's control of its running job („Ich meinte Seite 3“); true = it took the owner's turn. */
    fun deskIntercept(request: String): Boolean
    /** The desk's correction of its last finished order; true = it started the corrected order. */
    fun deskCorrect(request: String): Boolean
    /** Name of the PDF that is open in this chat, or null. */
    fun openDocumentName(): String?
    /** The work tools' owner entry (news, export, mail, opening the saved file); true = they took [order]. */
    fun work(order: String): Boolean
    /** true = this chat holds no news briefing the owner could mean with „speichere die Nachrichten …“. */
    fun noBriefingYet(): Boolean = false
    /** See [OwnerWorkTools.continueOn]. */
    fun continueOn(result: OwnerDeliverable): Boolean
    /** See [OwnerWorkTools.offerOpening]. */
    fun offerOpening(result: OwnerDeliverable, file: OwnerSavedFile): Boolean = false
    /** The storage lane's owner entry; true = it took [order]. */
    fun deepClean(order: String): Boolean = false
    /** Starts a recognised task chain; true = it runs and [OwnerMissionDirector.chainSettled] follows. */
    fun chain(order: String, plan: ChainPlan): Boolean
    /** The owner's stop for the job that runs right now. */
    fun cancelRunning()

    /** The owner's switch for model-proposed plans (off by default) together with a ready model. */
    fun modelPlans(): Boolean = false
    /** One bounded, tool-free generation for [prompt]; [done] on the UI thread with the raw text or null. */
    fun proposePlan(request: String, prompt: String, done: (String?) -> Unit) = done(null)
    /** The request takes its ordinary turn after a model plan was not accepted. */
    fun answerWithoutPlan(request: String) {}
    /**
     * true while the owner's cloud brain proposes the plans: then one sentence may also be mapped to a single lane
     * order ([OwnerMissionDirector.proposeOrderFromModel]). The on-device model keeps plans of at least two steps only.
     */
    fun modelOrders(): Boolean = false
    /**
     * false = [request] must not be handed to the plan model at all (the owner ruled the cloud out in this sentence,
     * see ClaudeTurnGate). It then takes its ordinary turn, which refuses it with the proper wording.
     */
    fun mayPlan(request: String): Boolean = true
    /** See [OwnerWorkTools.holdSubject]: called right before the owner's sentence is shown. */
    fun holdSubject() {}
    /**
     * [work] for an order on what the chat was about when the owner gave his sentence (the plan, a question or his
     * „Ja“ were shown in between). See [OwnerWorkTools.handleOnHeld].
     */
    fun workOnHeld(order: String): Boolean = work(order)

    // ── Send, app, mail, calendar and research steps (19.11.2026) ───────────────────────────────────────────

    /** Wall clock, for the journal and for „die Auftragsfolge von 14:02“. */
    fun wallMillis(): Long = System.currentTimeMillis()
    /** A wall-clock time as the owner reads it. */
    fun clockLabel(wallMillis: Long): String = java.time.Instant.ofEpochMilli(wallMillis).atZone(java.time.ZoneId.systemDefault())
        .let { String.format(Locale.GERMAN, "%02d:%02d", it.hour, it.minute) }
    fun today(): java.time.LocalDate = java.time.LocalDate.now()
    /**
     * The send lane's own entry for a message that is a step of a sequence (see OwnerSendDirector.missionSend): the
     * lane's parser, recipient resolution, confirmations, grant, screen verification and daily limit apply as for an
     * order said alone. [guard] is asked right before the one press. true = the lane took [order].
     */
    fun missionSend(order: String, channelGiven: Boolean, recipientOverride: String?, guard: OwnerSendGuard): Boolean = false
    /** A typed result of a step is from now on what „das“ means in this chat (see [OwnerWorkTools.adoptDeliverable]). */
    fun adoptResult(result: OwnerDeliverable) {}
    /** See [OwnerWorkTools.openSaved]: opens exactly the file an earlier step saved. */
    fun openSaved(order: String, file: OwnerSavedFile): Boolean = work(order)
    /** true = a Gmail account is connected and the owner granted reading. Only then a mail step is started at all. */
    fun mailReadable(): Boolean = false
    /** The Gmail lane's search, read and summary (OwnerMailLane); each ends in exactly one typed outcome through [done]. */
    fun mailSearch(order: String, query: MailQuery, done: (MailLaneOutcome) -> Unit) = done(MailLaneOutcome.Failed(MAIL_NOT_CONNECTED))
    fun mailRead(order: String, ref: MailRef, done: (MailLaneOutcome) -> Unit) = done(MailLaneOutcome.Failed(MAIL_NOT_CONNECTED))
    fun mailSummarize(order: String, refs: List<MailRef>, done: (MailLaneOutcome) -> Unit) = done(MailLaneOutcome.Failed(MAIL_NOT_CONNECTED))
    /** The contact name the owner confirmed for a relation word („Anwalt“), or null: the word is searched as said. */
    fun relationName(word: String): String? = null
    /** The briefing lane's owner entry (calendar); true = it took [order] and reports through [OwnerMissionDirector.laneOutcome]. */
    fun briefing(order: String): Boolean = false
    /** The app lane opens [appName]; true = started, the outcome follows as [OwnerLaneResult.AppOpened] or a failure. */
    fun openApp(order: String, appName: String): Boolean = false
    /** The existing web research for [topic] with the owner's internet consent; the outcome follows as [OwnerLaneResult.Researched]. */
    fun research(order: String, topic: String): Boolean = false

    companion object {
        const val MAIL_NOT_CONNECTED = "Gmail ist nicht verbunden; es wurde keine Mail gelesen."
    }
}

/**
 * How a mission stands when it pauses or ends – typed, for whoever keeps the record of the shared work. [step]: the
 * 1-based step it stands at. [resumable]: giving the owner's sentence [request] to the mission layer again would
 * repeat nothing that left the device, changed his storage or was already saved.
 */
internal data class OwnerMissionSettled(val id: String, val request: String, val steps: Int, val step: Int, val how: How,
                                        val resumable: Boolean, val startedAt: Long) {
    enum class How { PAUSED, COMPLETED, FAILED, STOPPED }
}

/**
 * The mission layer („Auftragsfolge“, owner goal 18.11.2026): a goal that needs several of the existing abilities in
 * sequence is carried out as one mission – plan, act step by step, verify, report.
 *
 *  - The plan comes from the owner's own sentence ([OwnerMissionPlanner]); it is said, and execution starts at once.
 *  - Every step is one owner order handed to its own lane through that lane's owner entry: the lane's parser, gates,
 *    grants, approvals and wording apply exactly as if the owner had said the step alone.
 *  - The lane reports how the order ended as a typed [OwnerLaneOutcome]; only a real result starts the next step,
 *    and a step that works on an earlier result is started only while that result still is what the chat is about.
 *  - A lane's question pauses the mission; the owner's answer goes to the lane, and the mission continues by itself.
 *  - A correction changes the plan: a step that has not run is changed, a step that has run is done again together
 *    with what depended on it – an outward step (mail) that already happened only after the owner's yes.
 *  - Failure, stop and the end are reported from the typed results only: what exists, what failed, what never began.
 *
 * It sits beside the task chains instead of extending them: a chain is the model and evaluator of one orchestrator
 * run over registered tools behind the ToolGate, while these lanes are owner-turn state machines with jobs, questions
 * over several turns and standing grants, and the send lane deliberately has no tool-registry entry. A chain recipe
 * can be a step of a mission, and the mission reuses the chains' „weiter“/„abbrechen“ words and owner switch.
 *
 * Bounded: at most [OwnerMissionPlanner.MAX_STEPS] steps, [MAX_RUNS] lane orders including repeated ones, a time
 * limit, and never a second attempt of a failed step without the owner. Free of Android.
 */
internal class OwnerMissionDirector(private val host: OwnerMissionHost,
                                    private val journal: OwnerMissionJournal = OwnerMissionJournal()) {
    /** [UNVERIFIED]: a send was pressed (or reserved) and never confirmed – not done, and never pressed again by itself. */
    private enum class State { PENDING, RUNNING, WAITING, DONE, FAILED, STOPPED, SKIPPED, UNVERIFIED }
    /** [RESUMING]: a sequence from the journal was offered after an app start and waits for the owner's word. */
    private enum class Phase { PROPOSED, RUNNING, WAITING, HELD, ASKING, ENDED, RESUMING }
    private enum class End { COMPLETED, FAILED, STOPPED, TIME, UNCONFIRMED }

    private class Slot(step: OwnerMissionStep) {
        /** true = the step's arguments changed since the journal last saw them (a correction). */
        var changed = false
        var step: OwnerMissionStep = step
            set(value) { field = value; changed = true }
        /** The owner corrected this very step after it had been sent: the repeat is asked with its own wording. */
        var confirmOwn = false
        /** Known from the journal after a restart: what the report says instead of a result that is not in memory. */
        var note: String? = null
        var state = State.PENDING
        var result: OwnerLaneResult? = null
        var problem: String? = null
        /** What earlier runs of this step left behind (a correction did it again). */
        val earlier = ArrayList<OwnerLaneResult>()
        /** The owner's correction sentence the desk redoes its own last order with. */
        var deskCorrection: String? = null
        /** An outward step that already happened once: repeated only after the owner's yes. */
        var confirmFirst = false
    }

    private class Queued(val target: Int, val step: OwnerMissionStep, val request: String, val deskOwn: Boolean)

    private class Mission(val session: String, val request: String, steps: List<OwnerMissionStep>, var startedAt: Long,
                          val turnId: String = "mission-" + java.util.UUID.randomUUID()) {
        val slots = steps.map(::Slot)
        /** Wall-clock start, for the journal. */
        var wallStartedAt = 0L
        var phase = Phase.RUNNING
        var current = 0
        var pausedAt = startedAt
        var runs = 0
        var stopping = false
        var noticed = false
        var corrected = false
        var queued: Queued? = null
        var answeredBy: OwnerMissionLane? = null
        var outcomes = 0
        /** The owner gave a new complete order while the mission waited: what its lane reports is not this step's. */
        var foreign = false
    }

    private var mission: Mission? = null

    /**
     * Every typed outcome a lane reports passes here first – the one place all owner lanes report to. [partOfMission]:
     * it settles the step this mission runs or waits on. Nobody has to listen.
     */
    var onLaneOutcome: (lane: OwnerMissionLane, outcome: OwnerLaneOutcome, partOfMission: Boolean) -> Unit = { _, _, _ -> }

    /** A mission paused or ended. Nobody has to listen. */
    var onSettled: (OwnerMissionSettled) -> Unit = {}

    private fun settled(m: Mission, how: OwnerMissionSettled.How) {
        // Taking the sentence up again must repeat nothing outward (mail), nothing that deletes (storage) and no save.
        val repeatable = m.slots.none { it.step is OwnerMissionStep.Mail || it.step is OwnerMissionStep.Storage } &&
            m.slots.none { it.state == State.DONE && (it.step is OwnerMissionStep.PageSave || it.step is OwnerMissionStep.Export) }
        runCatching { onSettled(OwnerMissionSettled(m.turnId, m.request, m.slots.size, m.current + 1, how,
            repeatable && how != OwnerMissionSettled.How.COMPLETED, m.startedAt)) }
    }

    /** true while the director hands a step to its lane: the lane's order is then not a new owner turn. */
    var dispatching = false
        private set

    /** true while a mission runs or waits in this chat. */
    val active: Boolean get() = open() != null

    private fun open(): Mission? = current()?.takeIf { it.phase != Phase.ENDED && it.phase != Phase.PROPOSED && it.phase != Phase.RESUMING }

    /** The mission of this chat while it is still within its bounds. */
    private fun current(): Mission? {
        val m = mission ?: return null
        val waited = host.nowMillis() - m.pausedAt
        if (m.session != host.sessionId || (m.phase != Phase.RUNNING && waited !in 0..PAUSE_MILLIS)) { mission = null; return null }
        return m
    }

    // ── Entry points ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * true = [request] is a sequence of lane orders and the mission has started (the plan is said, step 1 follows).
     * Call on the UI thread on a free lane, before the lanes themselves. false = not a mission; nothing happened.
     */
    fun handle(request: String): Boolean {
        val plan = OwnerMissionPlanner.plan(request, host.assistantName, host.noBriefingYet()) ?: return false
        val replaced = open() != null
        host.holdSubject()
        host.acceptOwnerTurn(request)
        // A step that needs the Gmail connection is not started without it: said plainly, with the rest offered.
        if (plan.steps.any(::mailStep) && !host.mailReadable()) {
            host.activity("AUFTRAGSFOLGE · Gmail ist nicht verbunden · die Mail-Schritte entfallen · nichts läuft ohne deine Bestätigung")
            offerWithoutMail(request, plan)
            return true
        }
        start(plan.request, plan, if (replaced) "Die frühere Auftragsfolge beende ich damit. " else "")
        return true
    }

    private fun mailStep(step: OwnerMissionStep): Boolean =
        step is OwnerMissionStep.MailSearch || step is OwnerMissionStep.MailRead || step is OwnerMissionStep.MailSummary

    /**
     * The honest partial answer when Gmail is not connected: the mail steps and what builds on their result are named
     * and left out; what does not depend on them is offered and runs only after the owner's yes.
     */
    private fun offerWithoutMail(request: String, plan: OwnerMissionPlan) {
        val steps = plan.steps
        val out = sortedSetOf<Int>()
        steps.forEachIndexed { index, step -> if (mailStep(step) || step.source?.let { it in out } == true) out += index }
        val kept = steps.indices.filter { it !in out }
        // The steps that stay keep pointing at each other: their sources are renumbered.
        val rest = kept.map { index -> withSource(steps[index], steps[index].source?.let(kept::indexOf)) }
        val hanging = out.filter { !mailStep(steps[it]) }.map { steps[it].label }
        mission = if (rest.isEmpty()) null else Mission(host.sessionId, request, rest, host.nowMillis()).also { it.phase = Phase.PROPOSED }
        host.reply(request, OwnerMissionPartial.withoutMail(hanging, rest))
    }

    private fun withSource(step: OwnerMissionStep, source: Int?): OwnerMissionStep = when (step) {
        is OwnerMissionStep.OpenPdf -> step.copy(source = source)
        is OwnerMissionStep.PageSave -> step.copy(source = source)
        is OwnerMissionStep.PageSummary -> step.copy(source = source)
        is OwnerMissionStep.News -> step.copy(source = source)
        is OwnerMissionStep.Export -> step.copy(source = source)
        is OwnerMissionStep.Mail -> step.copy(source = source)
        is OwnerMissionStep.FindFile -> step.copy(source = source)
        is OwnerMissionStep.OpenFound -> step.copy(source = source)
        is OwnerMissionStep.OpenSaved -> step.copy(source = source)
        is OwnerMissionStep.Storage -> step.copy(source = source)
        is OwnerMissionStep.SendChat -> step.copy(source = source)
        is OwnerMissionStep.OpenApp -> step.copy(source = source)
        is OwnerMissionStep.Calendar -> step.copy(source = source)
        is OwnerMissionStep.Research -> step.copy(source = source)
        else -> step
    }

    /**
     * First look at an owner turn, also while a step runs: stop, „weiter“, „abbrechen“, the answer to the mission's
     * own question and a correction of the plan. true = handled. Anything else is not the mission's turn.
     */
    fun intercept(request: String): Boolean {
        val m = current() ?: return false
        val name = host.assistantName
        if (m.stopping && host.idle()) { end(m, End.STOPPED); return false }
        when (m.phase) {
            Phase.ENDED -> {
                // A finished mission can still be corrected by the very next turn; any other turn lets it go.
                val change = OwnerMissionCorrection.parse(request, name)
                if (change != null && correct(m, request, change)) return true
                mission = null
                return false
            }
            Phase.PROPOSED -> {
                mission = null
                if (OwnerDeskReply.isYes(request, name)) {
                    host.acceptOwnerTurn(request)
                    // One confirmed order goes to its lane as if the owner had said it; the lane answers itself. A page
                    // summary without a save is the desk's order only inside a sequence: it runs as a sequence of one.
                    if (m.slots.size == 1 && handsOver(m.slots.single().step)) {
                        handOver(m.slots.single().step, request)
                        return true
                    }
                    // The plan was just said; it is not said again.
                    start(request, OwnerMissionPlan(m.request, m.slots.map { it.step }), "In Ordnung, ich fange an.", announce = false)
                    return true
                }
                if (OwnerDeskReply.isNo(request, name) || OwnerDeskReply.isStop(request, name)) {
                    host.acceptOwnerTurn(request)
                    host.reply(request, "In Ordnung, ich mache nichts davon.")
                    return true
                }
                return false
            }
            Phase.RESUMING -> return answerResume(m, request)
            Phase.RUNNING, Phase.WAITING, Phase.HELD, Phase.ASKING -> Unit
        }
        if (OwnerDeskReply.isStop(request, name)) return stop(m, request)
        OwnerMissionCorrection.parse(request, name)?.let { change -> if (correct(m, request, change)) return true }
        if (m.phase == Phase.RUNNING) return false
        if (m.phase == Phase.ASKING) {
            val slot = m.slots[m.current]
            if (OwnerDeskReply.isYes(request, name)) {
                host.acceptOwnerTurn(request)
                slot.confirmFirst = false
                slot.confirmOwn = false
                m.phase = Phase.RUNNING
                host.later { advance(m) }
                return true
            }
            if (OwnerDeskReply.isNo(request, name)) {
                host.acceptOwnerTurn(request)
                slot.confirmFirst = false
                slot.confirmOwn = false
                slot.state = State.SKIPPED
                m.phase = Phase.RUNNING
                host.later { advance(m) }
                return true
            }
        }
        when (OwnerTaskChainContinuation.parse(request, waitsForChoice = false, assistantName = name)) {
            is OwnerTaskChainContinuation.Command.Continue -> {
                host.acceptOwnerTurn(request)
                resume(m, request)
                return true
            }
            OwnerTaskChainContinuation.Command.Drop -> {
                host.acceptOwnerTurn(request)
                host.activity("AUFTRAGSFOLGE · vom Besitzer beendet")
                end(m, End.STOPPED)
                return true
            }
            null -> Unit
        }
        // Not for the mission. A new complete order is not the answer it waits for; whether the waiting lane takes
        // anything else as its answer shows right after this turn.
        if (m.phase == Phase.WAITING && OwnerMissionPlanner.recognise(request, name) != null) m.foreign = true
        watch(m)
        return false
    }

    /** true = [intercept] would take [request] while a step runs (for surfaces that refuse input when busy). */
    fun controls(request: String): Boolean {
        val m = open() ?: return false
        if (OwnerDeskReply.isStop(request, host.assistantName)) return true
        val change = OwnerMissionCorrection.parse(request, host.assistantName) ?: return false
        return target(m, change) >= 0
    }

    /** The owner's STOP control (button, hands-free stop) while a step runs; the running job is cancelled by the caller. */
    fun stopRequested() {
        val m = open()?.takeIf { it.phase == Phase.RUNNING } ?: return
        m.stopping = true
        host.activity("AUFTRAGSFOLGE · Stopp des Besitzers · die restlichen Schritte entfallen")
        stopWhenFree(m)
    }

    /**
     * A stopped step normally ends with its lane's own report, and that ends the mission. A job that was dropped
     * without a word leaves the lane free: then the mission reports by itself – now, or a moment later.
     */
    private fun stopWhenFree(m: Mission) {
        val check = { if (mission === m && m.stopping && host.idle()) end(m, End.STOPPED) }
        host.later(check)
        host.afterMillis(STOP_CHECK_MILLIS, check)
    }

    /**
     * A lane host asks before it shows an order as the owner's turn: true = it is the step the director is handing
     * over right now (the owner's sentence is already shown). Otherwise the lane took a real owner turn.
     */
    fun laneTurn(lane: OwnerMissionLane): Boolean {
        if (dispatching) return true
        open()?.answeredBy = lane
        return false
    }

    /** How an order ended in [lane]. Ignored unless it is the outcome of the step this mission runs or waits on. */
    fun laneOutcome(lane: OwnerMissionLane, outcome: OwnerLaneOutcome) {
        val own = accepts(lane, outcome)
        runCatching { onLaneOutcome(lane, outcome, own) }
        if (!own) return
        val m = open() ?: return
        val slot = m.slots[m.current]
        m.foreign = false
        m.outcomes++
        m.noticed = false
        // A send step whose intent is in the journal and that did not end as a confirmed send: whether the message
        // went out is not known. It is neither done nor simply failed, and it is never pressed again by itself.
        val unknown = outcome !is OwnerLaneOutcome.Done && outcome !is OwnerLaneOutcome.NeedsOwner &&
            journal.state(m.turnId, m.current) == OwnerMissionJournal.State.INTENT_RESERVED
        when (outcome) {
            is OwnerLaneOutcome.Done -> {
                slot.state = State.DONE; slot.result = outcome.result; slot.problem = null; slot.note = null
                // A text result of this step is what a later „das“ of the sequence means – as the object it is.
                when (val result = outcome.result) {
                    is OwnerLaneResult.MailSummarised -> host.adoptResult(result.of)
                    is OwnerLaneResult.Researched -> host.adoptResult(result.of)
                    is OwnerLaneResult.PageSummarised -> host.adoptResult(result.of)
                    else -> Unit
                }
            }
            is OwnerLaneOutcome.NeedsOwner -> { slot.state = State.WAITING; slot.problem = outcome.question }
            is OwnerLaneOutcome.Failed -> { slot.state = if (unknown) State.UNVERIFIED else State.FAILED; slot.problem = outcome.reason }
            OwnerLaneOutcome.Stopped -> slot.state = if (unknown) State.UNVERIFIED else State.STOPPED
        }
        stage(m)
        val queued = m.queued
        when {
            unknown -> end(m, End.UNCONFIRMED)
            m.stopping -> end(m, End.STOPPED)
            // The step that was running when the owner corrected the plan has settled: now the correction – unless
            // a step the correction does not concern has just failed.
            queued != null -> {
                m.queued = null
                if (outcome is OwnerLaneOutcome.Failed && m.current !in affected(m, queued.target)) end(m, End.FAILED)
                else host.later { redo(m, queued) }
            }
            outcome is OwnerLaneOutcome.Done -> { m.phase = Phase.RUNNING; host.later { advance(m) } }
            outcome is OwnerLaneOutcome.NeedsOwner -> {
                m.phase = Phase.WAITING
                m.pausedAt = host.nowMillis()
                settled(m, OwnerMissionSettled.How.PAUSED)
            }
            outcome is OwnerLaneOutcome.Failed -> end(m, End.FAILED)
            else -> end(m, End.STOPPED)
        }
    }

    /** true = [outcome] of [lane] is the outcome of the step this mission runs or waits on. */
    private fun accepts(lane: OwnerMissionLane, outcome: OwnerLaneOutcome): Boolean {
        val m = open() ?: return false
        if (m.phase != Phase.RUNNING && m.phase != Phase.WAITING) return false
        val slot = m.slots[m.current]
        if (slot.state != State.RUNNING && slot.state != State.WAITING) return false
        if (lane !in lanes(slot.step)) return false
        if (outcome is OwnerLaneOutcome.Done && !fits(slot.step, outcome.result)) return false
        // After a new order of the owner only the result this step was to produce still counts for it.
        if (m.foreign && outcome !is OwnerLaneOutcome.Done) return false
        return true
    }

    /** A task chain run ended. Counts only for the chain this mission started as its current step. */
    fun chainSettled(chain: OwnerTaskChain, outcome: OwnerTaskChain.Outcome) {
        val step = open()?.let { it.slots[it.current].step } as? OwnerMissionStep.FindFile ?: return
        if (chain.plan !== step.plan) return
        laneOutcome(OwnerMissionLane.CHAIN, when (outcome) {
            is OwnerTaskChain.Outcome.Completed -> {
                val name = chain.value("list", "name") ?: chain.value("folder", "name")
                val folder = ((step.plan.steps.first().arguments["path"] as? ChainArgument.Literal)?.value as? String)
                    ?: chain.value("find", "path")?.substringBeforeLast('/', "")
                if (name.isNullOrBlank()) OwnerLaneOutcome.Failed("Die Suche hat keinen Dateinamen geliefert.")
                else OwnerLaneOutcome.Done(OwnerLaneResult.FileFound(name, when (folder?.trimEnd('/')?.substringAfterLast('/')) {
                    "Download" -> OwnerLibraryMatcher.FOLDER_DOWNLOAD
                    "Documents" -> OwnerLibraryMatcher.FOLDER_DOCUMENTS
                    else -> null
                }))
            }
            is OwnerTaskChain.Outcome.Failed -> OwnerLaneOutcome.Failed(outcome.reason)
            is OwnerTaskChain.Outcome.Paused -> when (outcome.pause) {
                OwnerTaskChain.Pause.NEEDS_CHOICE -> OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.CHOICE, outcome.report)
                OwnerTaskChain.Pause.NEEDS_APPROVAL -> OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.CONFIRMATION, outcome.report)
                OwnerTaskChain.Pause.INTERRUPTED -> OwnerLaneOutcome.Stopped
            }
        })
    }

    /**
     * After every lane declined a turn: with the owner's switch on, the model may propose a plan for a multi-step
     * device goal. Its text is data – every line must be a lane order with nothing the owner did not say – and the
     * plan is only spoken. Nothing runs before the owner's yes. true = the turn is handled here.
     */
    fun proposeFromModel(request: String): Boolean = propose(request, single = false)

    /**
     * Cloud brain only (owner goal 19.11.2026: the cloud brain must be able to operate every ability): a free sentence
     * that is about a thing of the lanes but that no lane parser took – „Kannst du mir bitte mal das Argon-Dokument
     * aufmachen“ – is mapped by the model to ONE lane order, or to none. The same validation as for a plan applies:
     * the order must be one a lane parser takes, and may name nothing the owner did not say. A reading order or one
     * that writes the owner's own file is then handed to its lane at once; anything that leaves the device is asked
     * first, and the lane's own approvals stay in force either way. Call it where the turn would otherwise become a
     * free model turn, so no fixed route of the app is passed over. false = not taken; nothing happened.
     */
    fun proposeOrderFromModel(request: String): Boolean = host.modelOrders() && propose(request, single = true)

    private fun propose(request: String, single: Boolean): Boolean {
        val name = host.assistantName
        if (open() != null) return false
        // Several orders in one sentence, and not all of them can run (review finding B1, 19.11.2026): said plainly,
        // from the sentence itself and without a model – what cannot be done yet, and the offer for the rest.
        val reading = OwnerMissionPlanner.read(request, name, host.noBriefingYet())
        if (reading?.kind == OwnerSentenceReading.Kind.PARTIAL) {
            // Orders the app's own reading left open only because no lane knew their words: the cloud brain the owner
            // selected may map them – ONE text request that carries his sentence and nothing else. Orders the app
            // cannot carry out at all are said plainly, without a request.
            val ask = host.modelPlans() && host.modelOrders() && OwnerMissionPlanCall.wants(reading) && host.mayPlan(request)
            host.holdSubject()
            host.acceptOwnerTurn(request)
            if (!ask) {
                host.activity("AUFTRAGSFOLGE · " + reading.orders.size + " Aufträge erkannt · nicht alle sind schon möglich · nichts läuft ohne deine Bestätigung")
                offerRest(request, reading.open, reading.rest)
                return true
            }
            host.activity("AUFTRAGSFOLGE · " + reading.orders.size + " Aufträge erkannt · das Cloud-Gehirn ordnet die offenen zu · " +
                "gesendet wird nur dein Satz · jeder Schritt wird geprüft")
            val session = host.sessionId
            host.proposePlan(request, OwnerMissionPlanCall.prompt(request)) { text ->
                if (host.sessionId != session) return@proposePlan
                when (val checked = OwnerMissionPlanCall.check(request, text, name)) {
                    is OwnerMissionModelPlan.Result.Accepted -> {
                        mission = Mission(session, request, checked.plan.steps, host.nowMillis()).also { it.phase = Phase.PROPOSED }
                        host.reply(request, checked.plan.announcement("Ich würde das in", " machen") + " Soll ich das so machen?")
                    }
                    is OwnerMissionModelPlan.Result.Uncovered -> {
                        host.activity("AUFTRAGSFOLGE · der Modellplan deckt nicht jeden Auftrag deines Satzes · nichts läuft ohne deine Bestätigung")
                        offerRest(request, checked.open, checked.rest)
                    }
                    // No valid plan: the app's own reading stands – what cannot be done, and the offer for the rest.
                    is OwnerMissionModelPlan.Result.Rejected -> {
                        host.activity("AUFTRAGSFOLGE · Modellplan verworfen · " + checked.reason.take(120))
                        offerRest(request, reading.open, reading.rest)
                    }
                }
            }
            return true
        }
        if (!host.modelPlans()) return false
        val wanted = if (single) OwnerMissionModelPlan.wantsOrder(request, name) else OwnerMissionModelPlan.wants(request, name)
        if (!wanted || !host.mayPlan(request)) return false
        host.holdSubject()
        host.acceptOwnerTurn(request)
        host.activity(if (single) "AUFTRAG · das Modell ordnet deinen Satz einem festen Ablauf zu · geprüft wird, dass nichts dazukommt"
            else "AUFTRAGSFOLGE · das Modell schlägt Schritte vor · nichts läuft ohne deine Bestätigung")
        val session = host.sessionId
        // A sequence for the cloud brain is asked as a typed JSON plan; one order, and every plan of the model on the
        // device, keeps the numbered-line format (see OwnerMissionPlanCall for why). Both pass the same checks.
        val typed = !single && host.modelOrders()
        val prompt = when {
            single -> OwnerMissionModelPlan.orderPrompt(request)
            typed -> OwnerMissionPlanCall.prompt(request)
            else -> OwnerMissionModelPlan.prompt(request)
        }
        host.proposePlan(request, prompt) { text ->
            if (host.sessionId != session) return@proposePlan
            val result = if (typed) OwnerMissionPlanCall.check(request, text, name)
                else OwnerMissionModelPlan.check(request, text, name, allowSingle = single)
            when (val checked = result) {
                is OwnerMissionModelPlan.Result.Accepted -> {
                    val steps = checked.plan.steps
                    if (OwnerMissionModelPlan.runsWithoutQuestion(checked.plan, name)) {
                        // Reading or the owner's own file: his order, run by its lane with the lane's own gates.
                        host.activity("AUFTRAG · zugeordnet: „${steps.single().order.take(120)}“ · nichts hinzugefügt")
                        handOver(steps.single(), request)
                    } else {
                        mission = Mission(session, request, steps, host.nowMillis()).also { it.phase = Phase.PROPOSED }
                        host.reply(request, if (steps.size == 1) "Ich verstehe das so: ${label(steps.single())}. Soll ich das so machen?"
                            else checked.plan.announcement("Ich würde das in", " machen") + " Soll ich das so machen?")
                    }
                }
                // The plan is valid but leaves an order of the sentence open: nothing of it runs by itself.
                is OwnerMissionModelPlan.Result.Uncovered -> {
                    host.activity("AUFTRAGSFOLGE · der Modellplan deckt nicht jeden Auftrag deines Satzes · nichts läuft ohne deine Bestätigung")
                    offerRest(request, checked.open, checked.rest)
                }
                is OwnerMissionModelPlan.Result.Rejected -> {
                    host.activity("AUFTRAGSFOLGE · Modellplan verworfen · " + checked.reason.take(120))
                    host.answerWithoutPlan(request)
                }
            }
        }
        return true
    }

    /**
     * The honest partial answer: [open] cannot run, [rest] can. The rest is only offered – it waits for the owner's
     * yes like every proposed plan, and with nothing left to offer the reply says that nothing was carried out.
     */
    private fun offerRest(request: String, open: List<OwnerClause>, rest: List<OwnerMissionStep>) {
        mission = if (rest.isEmpty()) null else
            Mission(host.sessionId, request, rest, host.nowMillis()).also { it.phase = Phase.PROPOSED }
        host.reply(request, OwnerMissionPartial.text(open, rest))
    }

    /**
     * One validated order goes to its lane through the lane's owner entry, exactly as a mission hands over a step:
     * the lane's parser, gates, grants, questions and wording apply, and the lane itself answers. The owner's
     * sentence is already shown, so the order is not shown as a second owner turn.
     */
    /**
     * true = one confirmed order of this kind is handed to its lane as if the owner had said it. A message, another
     * app, mails, the calendar and a research run as a sequence of one instead: with the journal and its typed result.
     */
    private fun handsOver(step: OwnerMissionStep): Boolean = step is OwnerMissionStep.OpenPdf || step is OwnerMissionStep.PageSave ||
        step is OwnerMissionStep.News || step is OwnerMissionStep.Export || step is OwnerMissionStep.Mail ||
        step is OwnerMissionStep.FindFile || step is OwnerMissionStep.Storage

    private fun handOver(step: OwnerMissionStep, request: String) {
        host.later {
            if (!host.idle()) { host.reply(request, "Gerade läuft noch etwas anderes. Sag es bitte gleich noch einmal."); return@later }
            dispatching = true
            val taken = try {
                when (step) {
                    is OwnerMissionStep.OpenPdf, is OwnerMissionStep.PageSave -> host.desk(step.order)
                    is OwnerMissionStep.News -> host.work(step.order)
                    is OwnerMissionStep.Export, is OwnerMissionStep.Mail -> host.workOnHeld(step.order)
                    is OwnerMissionStep.FindFile -> host.chain(step.order, step.plan)
                    is OwnerMissionStep.Storage -> host.deepClean(step.order)
                    else -> false
                }
            } finally { dispatching = false }
            if (!taken) host.reply(request, "Diesen Auftrag hat der zuständige Ablauf nicht angenommen; es wurde nichts ausgeführt.")
        }
    }

    // ── Running the plan ────────────────────────────────────────────────────────────────────────────────────

    private fun start(request: String, plan: OwnerMissionPlan, lead: String, announce: Boolean = true) {
        val m = Mission(host.sessionId, plan.request, plan.steps, host.nowMillis())
        mission = m
        m.wallStartedAt = host.wallMillis()
        // Written down before the first step: what was ordered, and for every step that it has not acted yet.
        if (!journal.begin(m.turnId, plan.request, m.wallStartedAt, plan.steps))
            host.activity("AUFTRAGSFOLGE · das Journal ist nicht schreibbar · ein Sendeschritt wird deshalb nicht gedrückt")
        // Two orders that became one step („Öffne WhatsApp und schreib …“): no list to read out, the work starts.
        host.reply(request, when {
            !announce -> lead
            m.slots.size == 1 -> lead + "Verstanden. Ich kümmere mich darum."
            else -> lead + plan.announcement()
        })
        host.activity("AUFTRAGSFOLGE · ${m.slots.size} " + (if (m.slots.size == 1) "Schritt" else "Schritte nacheinander") +
            " · jeder läuft durch seine eigene Lenkung")
        if (announce) host.afterAnnouncement { advance(m) } else host.later { advance(m) }
    }

    /** Starts the first step that is still to do, or ends the mission. */
    private fun advance(m: Mission) {
        if (mission !== m || m.phase != Phase.RUNNING || m.slots.any { it.state == State.RUNNING }) return
        if (m.session != host.sessionId) { mission = null; return }
        sync(m)
        val next = m.slots.indexOfFirst { it.state == State.PENDING }
        if (next < 0) { end(m, if (m.slots.any { it.state == State.UNVERIFIED }) End.UNCONFIRMED else End.COMPLETED); return }
        m.current = next
        val slot = m.slots[next]
        if (host.nowMillis() - m.startedAt > MAX_TOTAL_MILLIS || m.runs >= MAX_RUNS) { end(m, End.TIME); return }
        if (slot.confirmFirst) {
            m.phase = Phase.ASKING
            m.pausedAt = host.nowMillis()
            val before = slot.earlier.lastOrNull()?.let { sentence(it) } ?: label(slot.step)
            // The owner corrected a step that had already gone out: it cannot be taken back, so the repeat is his decision.
            host.say(if (slot.confirmOwn) "Schritt ${next + 1} war schon erledigt: $before. Das lässt sich nicht zurücknehmen. " +
                "Soll ich ihn mit deiner Änderung noch einmal ausführen (${label(slot.step)})?"
                else "Schritt ${next + 1} war schon erledigt: $before. Soll ich ihn mit dem neuen Ergebnis noch einmal ausführen?")
            return
        }
        if (!host.idle()) { hold(m, "Gerade läuft noch etwas anderes."); return }
        dispatch(m, next)
    }

    private fun dispatch(m: Mission, index: Int) {
        val slot = m.slots[index]
        val step = slot.step
        val source = step.source?.let { m.slots[it].result }
        // A step that works on an earlier result starts only while that result still is what the chat is about.
        fun gone(what: String) = failBefore(m, slot, "$what aus Schritt ${(step.source ?: 0) + 1} ist nicht mehr das, worum es " +
            "in diesem Gespräch geht. Ich mache damit nicht weiter, statt etwas Falsches zu nehmen.")
        var found: OwnerLaneResult.FileFound? = null
        var file: OwnerSavedFile? = null
        var mails: List<MailRef> = emptyList()
        // The step gets the result OBJECT of the step it depends on, by its kind – never „the last answer of the chat“.
        when (step.needs) {
            OwnerStepSlot.DELIVERABLE -> {
                val subject = deliverable(source)
                if (subject == null || !host.continueOn(subject)) return gone("Das Ergebnis")
            }
            OwnerStepSlot.SAVED_FILE -> {
                file = when (source) {
                    // Saved in this run: its content must still be what the chat is about.
                    is OwnerLaneResult.FileSaved -> source.file.takeIf { host.continueOn(source.of) }
                    // Known from the journal after a restart: the file itself.
                    is OwnerLaneResult.FileKept -> source.file
                    else -> null
                } ?: return gone("Das Ergebnis")
            }
            OwnerStepSlot.OPEN_DOCUMENT -> {
                val opened = source as? OwnerLaneResult.DocumentOpened
                if (opened == null || host.openDocumentName() != opened.name) return gone("Das PDF")
            }
            OwnerStepSlot.FOUND_FILE -> {
                found = source as? OwnerLaneResult.FileFound ?: return gone("Die Datei")
                if (!found.name.endsWith(".pdf", ignoreCase = true)) return failBefore(m, slot, "„${found.name}“ ist kein PDF. " +
                    "Für diesen Dateityp gibt es noch kein freigegebenes Werkzeug zum Öffnen.")
            }
            OwnerStepSlot.MAILS -> {
                mails = when (source) {
                    is OwnerLaneResult.MailsFound -> source.ids.map { MailRef(it.first, it.second, "", "", 0L) }
                    is OwnerLaneResult.MailRead -> listOf(MailRef(source.id.first, source.id.second, "", "", 0L))
                    else -> emptyList()
                }
                if (mails.isEmpty()) return gone("Die Mail")
            }
            null -> Unit
        }
        if (mailStep(step) && !host.mailReadable()) return failBefore(m, slot, "Gmail ist nicht mehr verbunden; es wurde keine Mail gelesen.")
        // A send step whose message may already be out is never handed over again (see OwnerMissionJournal).
        if (step is OwnerMissionStep.SendChat || step is OwnerMissionStep.Mail) when (journal.state(m.turnId, index)) {
            OwnerMissionJournal.State.INTENT_RESERVED, OwnerMissionJournal.State.UNCONFIRMED -> {
                slot.state = State.UNVERIFIED
                stage(m)
                return end(m, End.UNCONFIRMED)
            }
            else -> Unit
        }
        slot.state = State.RUNNING
        slot.problem = null
        m.phase = Phase.RUNNING
        m.current = index
        m.runs++
        stage(m)
        host.activity("AUFTRAGSFOLGE · Schritt ${index + 1} von ${m.slots.size} · ${label(step)}")
        val correction = slot.deskCorrection
        slot.deskCorrection = null
        val seen = m.outcomes
        dispatching = true
        val taken = try {
            when (step) {
                is OwnerMissionStep.OpenPdf -> host.desk(step.order)
                is OwnerMissionStep.PageSave -> if (correction != null && host.deskCorrect(correction)) {
                    // The desk may answer that exactly this is what it saved; then nothing was done again.
                    if (slot.state == State.RUNNING && m.outcomes == seen && host.idle()) kept(m, index)
                    true
                } else host.desk(step.order)
                // Summarised and shown, never saved: the desk takes this order only from a sequence.
                is OwnerMissionStep.PageSummary -> host.desk(step.order)
                is OwnerMissionStep.News -> host.work(step.order)
                // A step on what the chat was about before this mission („Erstelle eine PDF und zeig sie mir“): the
                // plan that was just announced is not that result.
                is OwnerMissionStep.Export, is OwnerMissionStep.Mail ->
                    if (step.source == null) host.workOnHeld(step.order) else host.work(step.order)
                is OwnerMissionStep.OpenSaved -> host.openSaved(OPEN_SAVED_ORDER, checkNotNull(file))
                is OwnerMissionStep.FindFile -> host.chain(step.order, step.plan)
                is OwnerMissionStep.OpenFound -> { host.deskOpenFound(step.order, checkNotNull(found).name, found.folderHint); true }
                is OwnerMissionStep.Storage -> host.deepClean(step.order)
                // The send lane's own entry: its parser, its questions, its grant, its screen check, one press.
                is OwnerMissionStep.SendChat -> host.missionSend(step.order, step.channelFromOpening,
                    step.recipient.takeIf { step.corrected }, guard(m, index))
                is OwnerMissionStep.OpenApp -> host.openApp(step.order, step.appName)
                is OwnerMissionStep.Calendar -> host.briefing(step.order)
                is OwnerMissionStep.Research -> host.research(step.order, step.topic)
                is OwnerMissionStep.MailSearch -> { mailSearch(m, index, step) { hits -> mailSettled(m, index, hits) }; true }
                is OwnerMissionStep.MailRead -> {
                    val own = step.own
                    if (own == null) mailRead(m, index, step.order, mails) else mailSearch(m, index, own) { hits ->
                        val listed = refs(hits)
                        if (listed == null) mailSettled(m, index, hits) else mailRead(m, index, step.order, listed)
                    }
                    true
                }
                is OwnerMissionStep.MailSummary -> {
                    val own = step.own
                    if (own == null) host.mailSummarize(step.order, mails) { mailSettled(m, index, it) }
                    else mailSearch(m, index, own) { hits ->
                        val listed = refs(hits)
                        if (listed == null) mailSettled(m, index, hits)
                        else host.mailSummarize(step.order, listed) { summary -> mailSettled(m, index, summary) }
                    }
                    true
                }
                else -> false
            }
        } finally { dispatching = false }
        if (!taken && slot.state == State.RUNNING) failBefore(m, slot, "Diesen Teilauftrag hat die zuständige Lenkung nicht angenommen; " +
            "es wurde dafür nichts ausgeführt.")
    }

    // ── Mail steps: the Gmail lane's typed outcomes ─────────────────────────────────────────────────────────

    private fun mailSearch(m: Mission, index: Int, step: OwnerMissionStep.MailSearch, done: (MailLaneOutcome) -> Unit) {
        // „von meinem Anwalt“: the name the owner confirmed for that relation; without one the word as he said it.
        val from = step.from?.let { host.relationName(it) ?: it }
        val query = MailQuery(from = from, about = step.about, day = step.day?.let { name -> MailDay.entries.firstOrNull { it.name == name } },
            unreadOnly = step.unreadOnly, max = if (step.newest) 1 else MAIL_LIST, label = when {
                step.newest && step.from != null -> "letzte Mail von ${step.fromSaid ?: step.from}"
                step.from == null && step.about == null && step.day == null && !step.unreadOnly -> "die neuesten Mails im Posteingang"
                step.unreadOnly && step.from == null && step.about == null -> "neue Mails"
                else -> null
            })
        host.mailSearch(step.order, query) { outcome -> if (mission === m && m.current == index) done(outcome) }
    }

    /** The mails a search found, when it found any. */
    private fun refs(outcome: MailLaneOutcome): List<MailRef>? =
        ((outcome as? MailLaneOutcome.Done)?.result as? MailLaneResult.Found)?.refs?.takeIf { it.isNotEmpty() }

    private fun mailRead(m: Mission, index: Int, order: String, mails: List<MailRef>) {
        // „Lies sie vor“ means one mail. Which of several is the owner's choice, not a guess.
        if (mails.size != 1) return mailSettled(m, index, MailLaneOutcome.Failed("Die Suche hat ${mails.size} Mails gefunden. " +
            "Sag mir, welche ich vorlesen soll – zum Beispiel „Lies die erste vor“. Vorgelesen habe ich keine."))
        host.mailRead(order, mails.single()) { mailSettled(m, index, it) }
    }

    /**
     * How a mail step ended, as the typed result of this sequence. The mail lane has already shown what it found,
     * read or wrote – in its own private replies. Here only references and counts are kept; a mail's text is data of
     * the lane and is never read for an order.
     */
    private fun mailSettled(m: Mission, index: Int, outcome: MailLaneOutcome) {
        if (mission !== m || m.current != index) return
        laneOutcome(OwnerMissionLane.MAIL, when (outcome) {
            is MailLaneOutcome.Done -> when (val result = outcome.result) {
                is MailLaneResult.Found -> if (result.refs.isEmpty()) OwnerLaneOutcome.Failed("Dazu habe ich in deinem Posteingang keine Mail gefunden.")
                    else OwnerLaneOutcome.Done(OwnerLaneResult.MailsFound(result.query, result.refs.map { it.id to it.threadId }))
                is MailLaneResult.Read -> OwnerLaneOutcome.Done(OwnerLaneResult.MailRead(result.text.ref.id to result.text.ref.threadId, result.text.body.length))
                is MailLaneResult.Summary -> {
                    val title = if (result.refs.size == 1) "Zusammenfassung E-Mail" else "Zusammenfassung ${result.refs.size} E-Mails"
                    OwnerDeliverable.ofMailSummary(title, result.text, result.refs.size, result.cloud, result.complete, host.today(), host.wallMillis())
                        ?.let { OwnerLaneOutcome.Done(OwnerLaneResult.MailSummarised(it, result.refs.size, result.cloud, result.complete)) }
                        ?: OwnerLaneOutcome.Failed("Die Zusammenfassung der Mails ist leer geblieben.")
                }
                else -> OwnerLaneOutcome.Failed("Die Mail-Lenkung hat etwas anderes geliefert als bestellt; ich mache damit nicht weiter.")
            }
            is MailLaneOutcome.NeedsOwner -> OwnerLaneOutcome.NeedsOwner(outcome.need, outcome.question)
            is MailLaneOutcome.Failed -> OwnerLaneOutcome.Failed(outcome.reason)
            MailLaneOutcome.Stopped -> OwnerLaneOutcome.Stopped
        })
    }

    // ── Send steps: the journal around the one press ────────────────────────────────────────────────────────

    /** Asked by the send flow right before it presses: the intent is written down first, or nothing is pressed. */
    private fun guard(m: Mission, index: Int): OwnerSendGuard = object : OwnerSendGuard {
        override fun reserve(plan: SendPlan): Boolean = journal.reserve(m.turnId, index, plan.channel.name, when (plan) {
            is SendPlan.WhatsApp -> plan.waDigits
            is SendPlan.Mail -> plan.address
        }, when (plan) {
            is SendPlan.WhatsApp -> plan.text
            is SendPlan.Mail -> plan.subject + "\n" + plan.body
        }, (plan as? SendPlan.Mail)?.attachment?.displayName, host.wallMillis())

        override fun pressed(plan: SendPlan, clicked: Boolean) = journal.pressed(m.turnId, index, clicked, host.wallMillis())
    }

    /**
     * For the host that routes „schick das per Mail an …“ of the work tools to the send lane: the guard of the mail
     * step this sequence is handing over right now, else null (an order the owner gave himself has no sequence).
     */
    fun sendGuard(): OwnerSendGuard? {
        if (!dispatching) return null
        val m = open() ?: return null
        return if (m.slots[m.current].step is OwnerMissionStep.Mail) guard(m, m.current) else null
    }

    // ── After an app start: a sequence the journal still holds ──────────────────────────────────────────────

    /**
     * Call once when the chat is ready after the app was started. When the journal holds a sequence that never
     * ended, the owner is told where it stands and asked – one sentence. Nothing starts by itself: reading, searching,
     * summarising, opening and saving may be done again after his „weiter“; a send step whose intent was written down
     * and never confirmed is NOT pressed again – the app says that it does not know whether the message went out and
     * sends again only on his explicit „ja, nochmal“. true = the offer was made.
     */
    fun offerResume(): Boolean {
        if (mission != null) return false
        val record = journal.open() ?: return false
        val now = host.wallMillis()
        if (now - record.startedAt !in 0..RESUME_MILLIS) { journal.end(record.id, "EXPIRED", now); return false }
        val steps = record.steps.map { OwnerMissionStepCodec.decode(it.args, host.assistantName) }
        if (steps.any { it == null }) { journal.end(record.id, "UNREADABLE", now); return false }
        val m = Mission(host.sessionId, record.request, steps.filterNotNull(), host.nowMillis(), record.id)
        m.wallStartedAt = record.startedAt
        record.steps.forEachIndexed { index, kept ->
            val slot = m.slots[index]
            when (kept.state) {
                OwnerMissionJournal.State.DONE, OwnerMissionJournal.State.SENT, OwnerMissionJournal.State.PREPARED -> {
                    slot.state = State.DONE
                    slot.result = OwnerMissionStepCodec.restore(kept.result)
                    slot.note = label(slot.step) + when (kept.state) {
                        OwnerMissionJournal.State.SENT -> " – schon vor dem Neustart gesendet, nicht wiederholt"
                        OwnerMissionJournal.State.PREPARED -> " – schon vor dem Neustart vorbereitet, nichts gesendet"
                        else -> " – schon vor dem Neustart erledigt"
                    }
                }
                OwnerMissionJournal.State.INTENT_RESERVED, OwnerMissionJournal.State.UNCONFIRMED -> slot.state = State.UNVERIFIED
                OwnerMissionJournal.State.SKIPPED -> slot.state = State.SKIPPED
                else -> slot.state = State.PENDING
            }
        }
        val at = m.slots.indexOfFirst { it.state == State.PENDING || it.state == State.UNVERIFIED }
        if (at < 0) { journal.end(record.id, "COMPLETED", now); return false }
        // A step that is still to do may need the result of a step that was done: a text result is not in the
        // journal, so its step is done again. Only steps that send nothing are ever done again this way.
        val again = sortedSetOf<Int>()
        var grew = true
        while (grew) {
            grew = false
            m.slots.forEachIndexed { index, slot ->
                if (slot.state != State.PENDING && index !in again) return@forEachIndexed
                val source = slot.step.source ?: return@forEachIndexed
                val before = m.slots[source]
                if (source !in again && before.state == State.DONE && !provides(before.result, slot.step.needs) && replaySafe(before.step)) {
                    again += source
                    grew = true
                }
            }
        }
        again.forEach { m.slots[it].state = State.PENDING; m.slots[it].result = null; m.slots[it].note = null }
        m.current = at
        m.phase = Phase.RESUMING
        m.pausedAt = host.nowMillis()
        mission = m
        val total = m.slots.size
        val head = "Die Auftragsfolge von ${host.clockLabel(record.startedAt)} steht bei Schritt ${at + 1} von $total."
        val unknown = m.slots[at].takeIf { it.state == State.UNVERIFIED }
        host.activity("AUFTRAGSFOLGE · aus dem Journal · Schritt ${at + 1} von $total offen · nichts startet von selbst")
        host.say(if (unknown != null) {
            val where = if (unknown.step is OwnerMissionStep.Mail) "Gmail" else "WhatsApp"
            "$head Das ist: ${label(unknown.step)}. Ich weiß nicht, ob diese Nachricht rausging: Die App wurde beendet, bevor ich das " +
                "Senden bestätigen konnte. Bitte sieh in $where nach. Ich drücke nicht noch einmal – nur wenn du „ja, nochmal“ " +
                "sagst, sende ich sie erneut. Mit „weiter“ lasse ich diesen Schritt aus" +
                (if (m.slots.any { it.state == State.PENDING }) " und mache den Rest" else "") + ", mit „abbrechen“ beende ich die Auftragsfolge."
        } else head + " Weiter?" + when {
            again.isEmpty() -> ""
            else -> " Dafür mache ich " + (if (again.size == 1) "Schritt ${again.first() + 1}" else
                "die Schritte " + again.joinToString(", ") { (it + 1).toString() }) + " noch einmal – dabei geht nichts nach außen" +
                (if (again.any { m.slots[it].step.let { s -> s is OwnerMissionStep.Export || s is OwnerMissionStep.PageSave } })
                    "; die Datei von vorhin bleibt liegen, es kommt eine zweite dazu." else ".")
        })
        return true
    }

    /** true = [result] is in memory and is what a step that [needs] it can work on. */
    private fun provides(result: OwnerLaneResult?, needs: OwnerStepSlot?): Boolean = when (needs) {
        null -> true
        OwnerStepSlot.DELIVERABLE -> deliverable(result) != null
        OwnerStepSlot.SAVED_FILE -> result is OwnerLaneResult.FileSaved || result is OwnerLaneResult.FileKept
        OwnerStepSlot.FOUND_FILE -> result is OwnerLaneResult.FileFound
        OwnerStepSlot.OPEN_DOCUMENT -> result is OwnerLaneResult.DocumentOpened && host.openDocumentName() == result.name
        OwnerStepSlot.MAILS -> result is OwnerLaneResult.MailsFound || result is OwnerLaneResult.MailRead
    }

    /** Steps that may be done again after a restart: they read, search, summarise, open or write the owner's own file. */
    private fun replaySafe(step: OwnerMissionStep): Boolean = step !is OwnerMissionStep.SendChat && step !is OwnerMissionStep.Mail &&
        (step !is OwnerMissionStep.Storage || step.mode != "SAFE_CLEAN")

    private val retry = Regex("(?iu)^(?:ja[, ]+)?(?:bitte\\s+)?(?:(?:sende?|schicke?|drücke?)\\s+)?(?:(?:sie|es|die\\s+nachricht)\\s+)?" +
        "(?:nochmal|noch\\s+einmal|noch\\s+mal|erneut)(?:\\s+(?:senden|schicken|drücken|abschicken))?(?:\\s+bitte)?[.!]?$")

    /** The owner's word on the resume offer. Anything that is no answer lets the offer go; the journal keeps it. */
    private fun answerResume(m: Mission, request: String): Boolean {
        val name = host.assistantName
        val text = request.trim()
        val unknown = m.slots.indexOfFirst { it.state == State.UNVERIFIED }
        fun go(lead: String) {
            m.phase = Phase.RUNNING
            m.startedAt = host.nowMillis()
            journal.reopen(m.turnId)
            host.reply(request, lead)
            host.later { advance(m) }
        }
        when {
            unknown >= 0 && retry.matches(text) -> {
                host.acceptOwnerTurn(request)
                if (!journal.allowRetry(m.turnId, unknown, host.wallMillis())) {
                    host.reply(request, "Ich kann das im Journal nicht vermerken und sende deshalb nicht. Bitte schick die Nachricht selbst.")
                    return true
                }
                m.slots[unknown].state = State.PENDING
                go("In Ordnung, auf dein Wort sende ich sie noch einmal – mit allen Prüfungen wie beim ersten Mal.")
            }
            OwnerDeskReply.isYes(request, name) ||
                OwnerTaskChainContinuation.parse(request, waitsForChoice = false, assistantName = name) is OwnerTaskChainContinuation.Command.Continue -> {
                host.acceptOwnerTurn(request)
                val next = m.slots.indexOfFirst { it.state == State.PENDING }
                go(when {
                    next < 0 -> "In Ordnung, ich sende nicht noch einmal."
                    unknown >= 0 -> "In Ordnung, die Nachricht sende ich nicht noch einmal. Ich mache bei Schritt ${next + 1} von ${m.slots.size} weiter: ${label(m.slots[next].step)}."
                    else -> "Ich mache bei Schritt ${next + 1} von ${m.slots.size} weiter: ${label(m.slots[next].step)}."
                })
            }
            OwnerDeskReply.isNo(request, name) || OwnerDeskReply.isStop(request, name) ||
                OwnerTaskChainContinuation.parse(request, waitsForChoice = false, assistantName = name) == OwnerTaskChainContinuation.Command.Drop -> {
                host.acceptOwnerTurn(request)
                journal.end(m.turnId, "DROPPED", host.wallMillis())
                mission = null
                host.reply(request, "In Ordnung, die Auftragsfolge ist beendet. Von den offenen Schritten habe ich nichts ausgeführt.")
            }
            else -> { mission = null; return false }
        }
        return true
    }

    /** What the journal knows about this sequence right now, written through. A reservation is never overwritten. */
    private fun sync(m: Mission) {
        if (m.phase == Phase.PROPOSED || m.phase == Phase.RESUMING) return
        if (m.phase != Phase.ENDED) journal.reopen(m.turnId)
        val at = host.wallMillis()
        m.slots.forEachIndexed { index, slot ->
            journal.step(m.turnId, index, when (slot.state) {
                State.PENDING -> OwnerMissionJournal.State.PENDING
                State.RUNNING -> OwnerMissionJournal.State.RUNNING
                State.WAITING -> OwnerMissionJournal.State.WAITING
                State.DONE -> OwnerMissionJournal.State.DONE
                State.FAILED -> OwnerMissionJournal.State.FAILED
                State.STOPPED -> OwnerMissionJournal.State.STOPPED
                State.SKIPPED -> OwnerMissionJournal.State.SKIPPED
                State.UNVERIFIED -> OwnerMissionJournal.State.UNCONFIRMED
            }, slot.result, at, slot.step.takeIf { slot.changed })
            slot.changed = false
        }
    }

    /**
     * The lane left a corrected step as it was (it said so itself): the earlier results of that step and of what
     * depended on it stand. Nothing was done again, so nothing is reported again.
     */
    private fun kept(m: Mission, index: Int) {
        affected(m, index).forEach { at ->
            val slot = m.slots[at]
            val before = slot.earlier.removeLastOrNull() ?: return@forEach
            slot.state = State.DONE
            slot.result = before
            slot.confirmFirst = false
        }
        if (m.slots[index].state != State.DONE) return failBefore(m, m.slots[index], "Der Schritt wurde nicht noch einmal ausgeführt.")
        stage(m)
        if (m.slots.any { it.state == State.PENDING }) host.later { advance(m) }
        else { m.phase = Phase.ENDED; m.pausedAt = host.nowMillis(); journal.end(m.turnId, End.COMPLETED.name, host.wallMillis()) }
    }

    /** A step that could not even be handed to its lane. */
    private fun failBefore(m: Mission, slot: Slot, reason: String) {
        slot.state = State.FAILED
        slot.problem = reason
        stage(m)
        end(m, End.FAILED)
    }

    private fun hold(m: Mission, why: String) {
        m.phase = Phase.HELD
        m.pausedAt = host.nowMillis()
        settled(m, OwnerMissionSettled.How.PAUSED)
        host.say("$why Die Auftragsfolge wartet bei Schritt ${m.current + 1} von ${m.slots.size} (${label(m.slots[m.current].step)}). " +
            "Sag „weiter“, wenn ich dort weitermachen soll.")
    }

    /** „weiter“: the step the mission waits on is handed to its lane again; nothing of it has acted yet. */
    private fun resume(m: Mission, request: String) {
        val slot = m.slots[m.current]
        if (m.phase == Phase.ASKING) slot.confirmFirst = false
        if (slot.state == State.WAITING) slot.state = State.PENDING
        m.noticed = false
        m.foreign = false
        m.phase = Phase.RUNNING
        host.reply(request, "Ich mache bei Schritt ${m.current + 1} von ${m.slots.size} weiter: ${label(slot.step)}.")
        host.later { advance(m) }
    }

    private fun stop(m: Mission, request: String): Boolean {
        host.acceptOwnerTurn(request)
        host.activity("AUFTRAGSFOLGE · Stopp des Besitzers · die restlichen Schritte entfallen")
        m.queued = null
        if (m.phase == Phase.RUNNING && !host.idle()) {
            // The running job is asked to end; its lane says what was not done, then the mission reports.
            m.stopping = true
            host.cancelRunning()
            stopWhenFree(m)
        } else end(m, End.STOPPED)
        return true
    }

    /** One hint, right after an owner turn that neither the mission nor the lane it waits on took. */
    private fun watch(m: Mission) {
        if (m.noticed) return
        m.answeredBy = null
        val seen = m.outcomes
        host.later {
            val waiting = m.phase == Phase.WAITING || m.phase == Phase.HELD || m.phase == Phase.ASKING
            if (mission !== m || !waiting || m.noticed || m.outcomes != seen) return@later
            val slot = m.slots[m.current]
            // The lane the mission waits on took the turn: it is its answer, and the lane says what follows.
            if (m.phase == Phase.WAITING && !m.foreign && m.answeredBy?.let { it in lanes(slot.step) } == true) return@later
            m.noticed = true
            host.say("Die Auftragsfolge wartet noch bei Schritt ${m.current + 1} von ${m.slots.size} (${label(slot.step)}). " +
                "Mit „weiter“ mache ich dort weiter, mit „abbrechen“ beende ich sie.")
        }
    }

    private fun end(m: Mission, how: End) {
        m.stopping = false
        m.queued = null
        m.slots.forEach { if (it.state == State.RUNNING || it.state == State.WAITING) it.state = if (how == End.FAILED) State.FAILED else State.STOPPED }
        stage(m)
        host.stage(CockpitAnimationEvent.TurnEnded(m.turnId, host.nowMillis(), how == End.COMPLETED))
        m.phase = Phase.ENDED
        m.pausedAt = host.nowMillis()
        journal.end(m.turnId, how.name, host.wallMillis())
        // Only a completed mission stays open for a correction by the very next turn.
        if (how != End.COMPLETED && mission === m) mission = null
        settled(m, when (how) {
            End.COMPLETED -> OwnerMissionSettled.How.COMPLETED
            // An unconfirmed send is not done and must never be taken up again by „Mach da weiter“.
            End.FAILED, End.UNCONFIRMED -> OwnerMissionSettled.How.FAILED
            End.STOPPED, End.TIME -> OwnerMissionSettled.How.STOPPED
        })
        host.activity("AUFTRAGSFOLGE · " + when (how) {
            End.COMPLETED -> "alle Schritte erledigt"
            End.FAILED -> "bei Schritt ${m.current + 1} beendet · nichts Weiteres ausgeführt"
            End.STOPPED -> "gestoppt bei Schritt ${m.current + 1}"
            End.TIME -> "Grenze erreicht bei Schritt ${m.current + 1}"
            End.UNCONFIRMED -> "ein Senden ist unbestätigt · nicht wiederholt"
        })
        // A mission that ends with a save offers once what the lane offers after a save of its own.
        val saved = (m.slots.last().result as? OwnerLaneResult.FileSaved)?.takeIf { how == End.COMPLETED && m.slots.last().state == State.DONE }
        val offer = if (saved != null && host.offerOpening(saved.of, saved.file)) " Soll ich die Datei öffnen?" else ""
        // One order that ran as a sequence of one: its lane has already said how it ended.
        if (m.slots.size == 1 && how == End.COMPLETED && m.slots.single().state == State.DONE) return
        // One message that did not go out as a sequence of one: the send lane has said exactly why, and what it did not do.
        if (m.slots.size == 1 && m.slots.single().step is OwnerMissionStep.SendChat && how != End.TIME) return
        host.say(report(m, how) + offer)
    }

    // ── Corrections ─────────────────────────────────────────────────────────────────────────────────────────

    /** The step [change] concerns, or -1 when this mission has none. */
    private fun target(m: Mission, change: OwnerMissionCorrection.Change): Int = when (change) {
        is OwnerMissionCorrection.Change.Page -> m.slots.indexOfLast { it.step is OwnerMissionStep.PageSave || it.step is OwnerMissionStep.PageSummary }
        is OwnerMissionCorrection.Change.Format -> {
            // The save that last ran or runs; before any save has started, the first one of the plan.
            val saves = m.slots.indices.filter { m.slots[it].step.let { s -> s is OwnerMissionStep.PageSave || s is OwnerMissionStep.Export } }
            saves.lastOrNull { m.slots[it].state != State.PENDING } ?: saves.firstOrNull() ?: -1
        }
        is OwnerMissionCorrection.Change.Recipient -> m.slots.indexOfLast { it.step is OwnerMissionStep.Mail || it.step is OwnerMissionStep.SendChat }
        is OwnerMissionCorrection.Change.File -> m.slots.indexOfFirst { it.step is OwnerMissionStep.OpenPdf }
    }

    private fun correct(m: Mission, request: String, change: OwnerMissionCorrection.Change): Boolean {
        val index = target(m, change)
        if (index < 0) return false
        val slot = m.slots[index]
        val old = slot.step
        val wanted: OwnerMissionStep = when (change) {
            is OwnerMissionCorrection.Change.Page -> when (old) {
                is OwnerMissionStep.PageSave -> old.copy(page = change.page)
                is OwnerMissionStep.PageSummary -> old.copy(page = change.page)
                else -> return false
            }
            is OwnerMissionCorrection.Change.Format -> when (old) {
                is OwnerMissionStep.PageSave -> old.copy(format = change.format)
                is OwnerMissionStep.Export -> old.copy(format = change.format)
                else -> return false
            }
            is OwnerMissionCorrection.Change.Recipient -> when (old) {
                is OwnerMissionStep.Mail -> old.copy(address = change.address, recipientName = change.name)
                // A message goes to a contact or a confirmed relation; the send lane resolves the new name as always.
                is OwnerMissionStep.SendChat -> old.copy(recipient = change.name ?: return false, corrected = true)
                else -> return false
            }
            is OwnerMissionCorrection.Change.File -> (old as OwnerMissionStep.OpenPdf).copy(query = change.query, open = true, folderHint = null)
        }
        val number = index + 1
        if (wanted.code == old.code) {
            host.acceptOwnerTurn(request)
            host.reply(request, if (slot.state == State.DONE) "Genau so habe ich Schritt $number gemacht: ${label(old)}. Was soll ich anders machen?"
                else "Genau so steht Schritt $number im Plan: ${label(old)}.")
            return true
        }
        val step = OwnerMissionPlanner.reworded(wanted, host.assistantName)
        if (step == null) {
            host.acceptOwnerTurn(request)
            host.reply(request, "Das kann ich in dieser Auftragsfolge nicht umstellen. Schritt $number bleibt: ${label(old)}.")
            return true
        }
        val what = difference(old, step)
        // The desk's own correction of a page order keeps its accepted summary when only the format changes.
        val deskOwn = old is OwnerMissionStep.PageSave && change !is OwnerMissionCorrection.Change.File
        val busy = m.slots.indexOfFirst { it.state == State.RUNNING }
        when {
            slot.state == State.RUNNING && deskOwn && host.deskIntercept(request) -> {
                // The desk took the turn: it ends its running job and starts the corrected order itself.
                slot.step = step
                m.corrected = true
                host.activity("AUFTRAGSFOLGE · Korrektur · Schritt $number: $what")
            }
            slot.state == State.PENDING && m.phase == Phase.ASKING && index == m.current -> {
                // The mission was asking whether to repeat this very step: the corrected order is the owner's answer.
                host.acceptOwnerTurn(request)
                slot.step = step
                slot.confirmFirst = false
                m.corrected = true
                m.phase = Phase.RUNNING
                host.reply(request, "Verstanden – $what. Ich mache Schritt $number damit.")
                host.later { advance(m) }
            }
            slot.state == State.PENDING -> {
                host.acceptOwnerTurn(request)
                slot.step = step
                m.corrected = true
                host.reply(request, "Verstanden – $what. Das gilt für Schritt $number; er ist noch nicht gelaufen.")
            }
            slot.state == State.WAITING -> {
                host.acceptOwnerTurn(request)
                slot.step = step
                slot.state = State.PENDING
                m.corrected = true
                m.phase = Phase.RUNNING
                host.reply(request, "Verstanden – $what. Ich mache Schritt $number damit neu.")
                host.later { advance(m) }
            }
            busy >= 0 -> {
                // Something runs right now: it is ended or awaited first, then the corrected step is done again.
                host.acceptOwnerTurn(request)
                m.queued = Queued(index, step, request, deskOwn && slot.state == State.DONE)
                val running = m.slots[busy].step
                // A send that runs is never cut off: once the control is pressed the message is out.
                val wait = running is OwnerMissionStep.Mail || running is OwnerMissionStep.Export || running is OwnerMissionStep.OpenSaved ||
                    running is OwnerMissionStep.SendChat
                host.reply(request, "Verstanden – $what. " + (if (busy == index) "Schritt $number läuft gerade" else
                    "Gerade läuft Schritt ${busy + 1}") + (if (wait) "; sobald er fertig ist, mache ich " else "; ich breche ihn ab und mache ") +
                    (if (busy == index) "ihn" else "Schritt $number") + " mit der Änderung neu.")
                if (!wait) host.cancelRunning()
            }
            else -> {
                host.acceptOwnerTurn(request)
                host.reply(request, "Verstanden – $what. Ich mache Schritt $number damit neu.")
                redo(m, Queued(index, step, request, deskOwn && slot.state == State.DONE))
            }
        }
        return true
    }

    /** [target] and every later step that works on its result, directly or through another one. */
    private fun affected(m: Mission, target: Int): Set<Int> {
        val again = sortedSetOf(target)
        m.slots.forEachIndexed { index, slot -> if (index > target && slot.step.source?.let { it in again } == true) again += index }
        return again
    }

    /** Does [queued]'s step again together with every later step that worked on its result. */
    private fun redo(m: Mission, queued: Queued) {
        if (mission !== m) return
        val again = affected(m, queued.target)
        // A step that was ended or left waiting for this correction is simply still to do.
        m.slots.forEach { if (it.state == State.STOPPED || it.state == State.WAITING) it.state = State.PENDING }
        again.forEach { index ->
            val slot = m.slots[index]
            slot.result?.let { slot.earlier += it }
            // A mail that already went out is not repeated on its own. A step that was really SENT cannot be taken back:
            // also when it is the corrected step itself, the owner is asked before it goes out a second time.
            val sent = (slot.result as? OwnerLaneResult.Delivered)?.state == OwnerLaneResult.Delivered.State.SENT
            slot.confirmOwn = index == queued.target && slot.state == State.DONE && sent
            slot.confirmFirst = slot.state == State.DONE && ((index != queued.target && slot.step is OwnerMissionStep.Mail) || slot.confirmOwn)
            slot.state = State.PENDING
            slot.result = null
            slot.problem = null
        }
        val slot = m.slots[queued.target]
        slot.step = queued.step
        slot.deskCorrection = queued.request.takeIf { queued.deskOwn }
        m.corrected = true
        m.stopping = false
        m.phase = Phase.RUNNING
        host.activity("AUFTRAGSFOLGE · Korrektur · Schritt ${queued.target + 1} wird neu gemacht" +
            (if (again.size > 1) " · danach " + again.drop(1).joinToString(", ") { "Schritt ${it + 1}" } else ""))
        host.later { advance(m) }
    }

    private fun difference(old: OwnerMissionStep, new: OwnerMissionStep): String = when {
        old is OwnerMissionStep.PageSave && new is OwnerMissionStep.PageSave && old.page != new.page ->
            "${new.pageName} statt " + if (old.page == at.heinz.deviceai.cognitive.OwnerPageReference.LAST) "der letzten Seite" else old.pageName
        old is OwnerMissionStep.PageSave && new is OwnerMissionStep.PageSave ->
            "als ${new.format?.label} statt " + (old.format?.let { "als ${it.label}" } ?: "ohne Format")
        old is OwnerMissionStep.PageSummary && new is OwnerMissionStep.PageSummary -> "${new.pageName} statt " +
            if (old.page == at.heinz.deviceai.cognitive.OwnerPageReference.LAST) "der letzten Seite" else old.pageName
        old is OwnerMissionStep.Export && new is OwnerMissionStep.Export -> "als ${new.format.label} statt als ${old.format.label}"
        old is OwnerMissionStep.Mail && new is OwnerMissionStep.Mail -> "an ${new.to} statt an ${old.to}"
        old is OwnerMissionStep.SendChat && new is OwnerMissionStep.SendChat -> "an ${new.recipient} statt an ${old.recipient ?: "den zuerst genannten Empfänger"}"
        old is OwnerMissionStep.OpenPdf && new is OwnerMissionStep.OpenPdf -> "das PDF „${new.query}“ statt „${old.query}“"
        else -> label(new)
    }

    // ── What the typed results say ──────────────────────────────────────────────────────────────────────────

    private fun lanes(step: OwnerMissionStep): Set<OwnerMissionLane> = when (step) {
        is OwnerMissionStep.OpenPdf, is OwnerMissionStep.PageSave, is OwnerMissionStep.PageSummary, is OwnerMissionStep.OpenFound ->
            setOf(OwnerMissionLane.DESK)
        is OwnerMissionStep.Mail -> setOf(OwnerMissionLane.WORK, OwnerMissionLane.SEND)
        is OwnerMissionStep.FindFile -> setOf(OwnerMissionLane.CHAIN)
        is OwnerMissionStep.Storage -> setOf(OwnerMissionLane.DEEPCLEAN)
        is OwnerMissionStep.SendChat -> setOf(OwnerMissionLane.SEND)
        is OwnerMissionStep.OpenApp -> setOf(OwnerMissionLane.APP)
        is OwnerMissionStep.Calendar -> setOf(OwnerMissionLane.BRIEFING)
        is OwnerMissionStep.Research -> setOf(OwnerMissionLane.RESEARCH)
        is OwnerMissionStep.MailSearch, is OwnerMissionStep.MailRead, is OwnerMissionStep.MailSummary -> setOf(OwnerMissionLane.MAIL)
        else -> setOf(OwnerMissionLane.WORK)
    }

    /** true = [result] is what [step] was to produce; anything else belongs to another order of that lane. */
    private fun fits(step: OwnerMissionStep, result: OwnerLaneResult): Boolean = when (step) {
        is OwnerMissionStep.OpenPdf -> if (!step.open) result is OwnerLaneResult.FilesFound else
            result is OwnerLaneResult.DocumentOpened && (result.query == null || result.query.equals(step.query, ignoreCase = true))
        is OwnerMissionStep.PageSave -> result is OwnerLaneResult.FileSaved && result.of.kind == OwnerDeliverable.Kind.PAGE_SUMMARY
        // Verified = the page's text was really read and the summary accepted (the desk reports nothing else as done).
        is OwnerMissionStep.PageSummary -> result is OwnerLaneResult.PageSummarised && result.coveredChars > 0 &&
            (step.page == at.heinz.deviceai.cognitive.OwnerPageReference.LAST || result.page == step.page)
        is OwnerMissionStep.News -> result is OwnerLaneResult.Briefing
        is OwnerMissionStep.Export -> result is OwnerLaneResult.FileSaved && result.file.mime == step.format.mime
        is OwnerMissionStep.Mail -> result is OwnerLaneResult.Delivered
        is OwnerMissionStep.FindFile -> result is OwnerLaneResult.FileFound
        is OwnerMissionStep.OpenFound -> result is OwnerLaneResult.DocumentOpened
        is OwnerMissionStep.OpenSaved -> result is OwnerLaneResult.FileOpened
        is OwnerMissionStep.Storage -> result is OwnerLaneResult.Storage && result.mode == step.mode
        // Sent, or a verified draft: the lane reports nothing else as done. The report says which of the two it was.
        is OwnerMissionStep.SendChat -> result is OwnerLaneResult.Delivered
        // Verified = the app lane itself saw the launched app.
        is OwnerMissionStep.OpenApp -> result is OwnerLaneResult.AppOpened
        is OwnerMissionStep.Calendar -> result is OwnerLaneResult.Agenda
        // Verified = at least one source was really read.
        is OwnerMissionStep.Research -> result is OwnerLaneResult.Researched && result.sources > 0
        // Verified = Gmail answered with message ids.
        is OwnerMissionStep.MailSearch -> result is OwnerLaneResult.MailsFound && result.ids.isNotEmpty()
        is OwnerMissionStep.MailRead -> result is OwnerLaneResult.MailRead
        is OwnerMissionStep.MailSummary -> result is OwnerLaneResult.MailSummarised && result.mails > 0
        else -> false
    }

    private fun deliverable(result: OwnerLaneResult?): OwnerDeliverable? = when (result) {
        is OwnerLaneResult.Briefing -> result.deliverable
        is OwnerLaneResult.FileSaved -> result.of
        is OwnerLaneResult.PageSummarised -> result.of
        is OwnerLaneResult.MailSummarised -> result.of
        is OwnerLaneResult.Researched -> result.of
        else -> null
    }

    private fun label(step: OwnerMissionStep): String = step.label.replaceFirstChar { it.uppercase(Locale.GERMAN) }

    /**
     * One step's real result in a few words – names and places exactly as the lane reported them. [named]: files the
     * same report has already named; an attachment among them is „diese Datei“ instead of its whole name again.
     */
    private fun sentence(result: OwnerLaneResult, named: Set<String> = emptySet()): String = when (result) {
        is OwnerLaneResult.DocumentOpened -> "„${result.name}“ geöffnet" + (result.folder?.let { " (Ordner $it)" } ?: "")
        is OwnerLaneResult.FilesFound -> "zu „${result.query}“ " + (if (result.names.size == 1) "„${result.names.single()}“" else
            "${result.names.size} PDFs") + " gefunden, nichts geöffnet"
        is OwnerLaneResult.FileFound -> "„${result.name}“ gefunden"
        is OwnerLaneResult.Briefing -> "Nachrichten zusammengefasst (" + (if (result.stories == 1) "1 Meldung" else "${result.stories} Meldungen") + ")"
        is OwnerLaneResult.FileSaved -> "„${result.file.displayName}“ gespeichert (" + (result.location ?: result.file.folder) + ")" +
            if (result.verified == false) " – zurücklesen konnte ich die Datei nicht" else ""
        is OwnerLaneResult.FileOpened -> "„${result.file.displayName}“ geöffnet"
        is OwnerLaneResult.PageSummarised -> "Seite ${result.page} von „${result.document}“ zusammengefasst und hier gezeigt – " +
            "gespeichert habe ich nichts"
        is OwnerLaneResult.Agenda -> "Kalender für ${result.span} gelesen (" + (if (result.events == 1) "1 Eintrag" else
            "${result.events} Einträge") + ")"
        is OwnerLaneResult.Storage -> storageSentence(result)
        is OwnerLaneResult.AppOpened -> "${result.appName} geöffnet"
        // Counts only: who wrote and what a mail says stays in the mail lane's own replies.
        is OwnerLaneResult.MailsFound -> if (result.ids.size == 1) "1 Mail gefunden" else "${result.ids.size} Mails gefunden"
        is OwnerLaneResult.MailRead -> "die Mail gelesen und hier gezeigt"
        is OwnerLaneResult.MailSummarised -> (if (result.mails == 1) "1 Mail" else "${result.mails} Mails") + " zusammengefasst und hier gezeigt (" +
            (if (result.cloud) "über das Cloud-Gehirn, mit deiner Freigabe für Mailinhalte" else "mit dem Modell auf dem Gerät") + ")"
        is OwnerLaneResult.Researched -> "Recherche zu „${result.topic}“ abgeschlossen (" + (if (result.sources == 1) "1 gelesene Quelle" else
            "${result.sources} gelesene Quellen") + ")"
        is OwnerLaneResult.FileKept -> "„${result.file.displayName}“ gespeichert (vor dem Neustart)"
        is OwnerLaneResult.Delivered -> if (result.channel == at.heinz.deviceai.sending.SendChannel.WHATSAPP.label) when (result.state) {
            OwnerLaneResult.Delivered.State.SENT -> "Nachricht an ${result.to} über WhatsApp gesendet"
            else -> "Entwurf an ${result.to} in WhatsApp vorbereitet – gesendet habe ich nichts"
        } else {
            val attached = result.attachment?.let { if (it.displayName in named) ", diese Datei hängt an" else ", Anhang „${it.displayName}“" }
                ?: ", ohne Anhang"
            when (result.state) {
                OwnerLaneResult.Delivered.State.SENT -> "E-Mail an ${result.to} über ${result.channel} gesendet$attached"
                OwnerLaneResult.Delivered.State.DRAFT -> "Entwurf an ${result.to} in ${result.channel} vorbereitet$attached – gesendet habe ich nichts"
                OwnerLaneResult.Delivered.State.HANDED_OVER -> "E-Mail an ${result.to} im Mailprogramm vorbereitet$attached – gesendet habe ich nichts"
            }
        }
    }

    /** The storage lane's measured figures in a few words – nothing it did not measure. */
    private fun storageSentence(result: OwnerLaneResult.Storage): String {
        val bytes = at.heinz.deviceai.deepclean.DeepCleanText::bytes
        val free = result.freeAfter ?: result.freeBefore
        return when (result.mode) {
            "SAFE_CLEAN" -> (if (result.removedFiles == 0) "nichts Entbehrliches gefunden, nichts gelöscht" else
                "${at.heinz.deviceai.deepclean.DeepCleanText.files(result.removedFiles)} entfernt (${bytes(result.removedBytes)} Dateigröße)") +
                (if (result.freeBefore != null && result.freeAfter != null) ", Android misst ${bytes(result.freeBefore)} → ${bytes(result.freeAfter)} frei" else "")
            "REPORT" -> "Platz gemeldet" + (free?.let { ": ${bytes(it)} frei" } ?: "")
            "OPTIMISE" -> "Last geprüft, nichts gelöscht"
            else -> "Speicher geprüft, nichts gelöscht" + (free?.let { " (${bytes(it)} frei)" } ?: "")
        }
    }

    /** The closing report: what was really done, what failed and why, what never began. */
    private fun report(m: Mission, how: End): String {
        fun numbered(states: Set<State>, text: (Slot) -> String): String = m.slots.withIndex().filter { it.value.state in states }
            .joinToString(" ") { (index, slot) -> "${index + 1}. ${text(slot).trimEnd('.', ' ')}." }
        val named = HashSet<String>()
        val done = numbered(setOf(State.DONE)) { slot ->
            slot.note?.let { return@numbered it }
            val result = slot.result ?: return@numbered label(slot.step)
            sentence(result, named).also { if (result is OwnerLaneResult.FileSaved) named += result.file.displayName }
        }
        val failed = numbered(setOf(State.FAILED)) { slot -> label(slot.step) + " – " + reason(slot.problem) }
        val unverified = numbered(setOf(State.UNVERIFIED)) { slot -> label(slot.step) + " – ob die Nachricht rausging, weiß ich nicht; " +
            "ich habe nicht noch einmal gedrückt. Bitte sieh selbst nach" }
        val stopped = numbered(setOf(State.STOPPED)) { slot -> label(slot.step) }
        val skipped = numbered(setOf(State.SKIPPED)) { slot -> label(slot.step) }
        val open = numbered(setOf(State.PENDING)) { slot -> label(slot.step) }
        val total = m.slots.size
        val head = when (how) {
            End.COMPLETED -> when {
                skipped.isNotEmpty() -> "Fertig bis auf das, was du nicht wiederholt haben wolltest."
                m.corrected -> "Fertig – mit deiner Korrektur sind ${all(total)} Schritte erledigt."
                else -> "Fertig – ${all(total)} Schritte sind erledigt."
            }
            End.FAILED -> "Die Auftragsfolge endet bei Schritt ${m.current + 1} von $total."
            End.STOPPED -> "Die Auftragsfolge ist gestoppt."
            End.TIME -> "Die Auftragsfolge endet hier: Ihre Grenze für Zeit und Wiederholungen ist erreicht."
            End.UNCONFIRMED -> "Die Auftragsfolge endet mit einem offenen Punkt."
        }
        val before = m.slots.flatMap { it.earlier }.distinct().filter { earlier -> m.slots.none { it.result == earlier } }
        return buildString {
            append(head)
            if (done.isNotEmpty()) append(if (how == End.COMPLETED) " " else " Erledigt: ").append(done)
            else if (how != End.COMPLETED) append(" Erledigt war noch nichts.")
            if (failed.isNotEmpty()) append(" Nicht geklappt: ").append(failed)
            if (unverified.isNotEmpty()) append(" Unklar: ").append(unverified)
            if (stopped.isNotEmpty()) append(" Abgebrochen: ").append(stopped)
            if (skipped.isNotEmpty()) append(" Nicht wiederholt: ").append(skipped)
            if (open.isNotEmpty()) append(" Nicht begonnen: ").append(open)
            if (before.isNotEmpty()) append(" Von vorher gibt es weiterhin: ").append(before.joinToString("; ") { earlier ->
                if (earlier is OwnerLaneResult.Delivered) sentence(earlier) + " (das lässt sich nicht zurücknehmen)" else sentence(earlier)
            }).append('.')
        }
    }

    private fun all(steps: Int): String = if (steps == 2) "beide" else "alle ${OwnerMissionPlanner.count(steps)}"

    /** The first sentence of a lane's reason; the lane has already said all of it. */
    private fun reason(problem: String?): String {
        val text = NewsBriefing.clean(problem ?: "ohne Angabe eines Grundes", 400)
        val end = Regex("[.!?](?=\\s|$)").find(text, startIndex = minOf(20, text.length))?.range?.first ?: text.length
        return text.substring(0, end).take(MAX_REASON)
    }

    /** The mission's steps as far as they were really handed over; a step that has not started is not shown. */
    private fun stage(m: Mission) {
        sync(m)
        val steps = m.slots.withIndex().filter { it.value.state != State.PENDING }.map { (index, slot) ->
            ProcessStep("mission:$index", "${index + 1}/${m.slots.size} ${label(slot.step)}".take(80), when (slot.state) {
                State.RUNNING -> StepState.ACTIVE
                State.WAITING -> StepState.WAITING
                State.DONE -> StepState.DONE
                State.FAILED -> StepState.FAILED
                State.UNVERIFIED -> StepState.UNVERIFIED
                else -> StepState.BLOCKED
            })
        }
        if (steps.isNotEmpty()) host.stage(CockpitAnimationEvent.ProcessTimeline(m.turnId, host.nowMillis(), steps))
        // The same overview with the exact outcome of every started step, for the order rail of the stage.
        if (steps.isNotEmpty()) host.stage(MissionOrdersEvent(m.turnId, host.nowMillis(), m.slots.size,
            m.slots.withIndex().mapNotNull { (index, slot) ->
                val state = when (slot.state) {
                    State.PENDING -> null
                    State.RUNNING -> MissionOrderState.RUNNING
                    State.WAITING -> MissionOrderState.WAITING
                    State.DONE -> MissionOrderState.DONE
                    State.FAILED -> MissionOrderState.FAILED
                    State.STOPPED -> MissionOrderState.STOPPED
                    State.SKIPPED -> MissionOrderState.SKIPPED
                    State.UNVERIFIED -> MissionOrderState.UNVERIFIED
                }
                state?.let { MissionOrder(index, label(slot.step), it) }
            }))
    }

    internal companion object {
        /** From the start of a mission to the start of its last step, pauses included. */
        const val MAX_TOTAL_MILLIS = 30 * 60_000L
        /** How long a mission waits for the owner before it is given up. */
        const val PAUSE_MILLIS = 10 * 60_000L
        /** Lane orders of one mission, repeated and resumed ones included. */
        const val MAX_RUNS = 2 * OwnerMissionPlanner.MAX_STEPS
        private const val MAX_REASON = 220
        private const val STOP_CHECK_MILLIS = 4_000L
        /** How long after its start a sequence from the journal is still offered. */
        const val RESUME_MILLIS = 24 * 60 * 60_000L
        /** Mails a search of a sequence lists; the summary takes as many as the mail lane summarises at once. */
        const val MAIL_LIST = 5
        const val OPEN_SAVED_ORDER = "Öffne die gespeicherte Datei"
    }
}
