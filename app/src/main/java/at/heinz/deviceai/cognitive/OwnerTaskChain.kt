package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolExecutionRecord
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState

/** What the stage may show of a chain: only steps that really waited, started or ended. */
interface OwnerTaskChainObserver {
    fun waitingForApproval(index: Int, step: ChainStep, total: Int) {}
    fun started(index: Int, step: ChainStep, total: Int) {}
    fun settled(index: Int, step: ChainStep, state: StepState) {}
    fun ended(completed: Boolean) {}
}

/**
 * Runs a [ChainPlan] strictly in order – as the model AND the evaluator of the existing [BoundedAgentOrchestrator].
 * It is not a second orchestrator: the orchestrator keeps its budget, the ToolGate its policy and receipts, the
 * approval provider its dialog, [ExternalContentActionGuard] its veto. The chain only decides which registered tool
 * comes next, checks every real observation against the step's success condition before the next step starts, hands
 * values forward explicitly and writes the closing report from nothing but those observations.
 *
 * One instance is one owner task. A paused chain (approval not given, a choice needed, interrupted) keeps its verified
 * steps and continues with [resume] in a fresh orchestrator run at the step where it stopped.
 */
class OwnerTaskChain(
    val plan: ChainPlan,
    /** The owner's own request: the task of every orchestrator run and the only source of provenance for the guard. */
    val ownerTask: String,
    /** The stage feed of the current run; a continued chain gets a fresh one per run. */
    @Volatile var observer: OwnerTaskChainObserver = object : OwnerTaskChainObserver {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxMillis: Long = DEFAULT_MAX_MILLIS
) : AgentModel, AgentEvaluator {
    enum class Pause { NEEDS_APPROVAL, NEEDS_CHOICE, INTERRUPTED }

    sealed interface Outcome {
        val report: String
        data class Completed(override val report: String) : Outcome
        data class Failed(val step: Int, val reason: String, override val report: String) : Outcome
        data class Paused(val step: Int, val pause: Pause, val options: List<String>, override val report: String) : Outcome
    }

    private class Done(val step: ChainStep, val output: String, val values: Map<String, String>)

    private val done = ArrayList<Done>()
    private var consumed = 0
    private var outcome: Outcome? = null
    private var approvalDenied = false
    private var dispatched = false
    private var startedAt = NOT_STARTED
    private var pendingChoice: ChainExtract.Result.Choice? = null
    private var pendingOutput: String? = null
    private var chosen: Map<String, String> = emptyMap()

    /** Null while the chain is still running. */
    @Synchronized fun outcome(): Outcome? = outcome
    @Synchronized fun completedSteps(): Int = done.size
    val stepCount: Int get() = plan.steps.size

    private val results = ChainResults { step, key -> done.firstOrNull { it.step.id == step }?.values?.get(key) }

    /** A value a verified step handed on („name“ of step „list“), for whoever continues on this chain's result. */
    @Synchronized fun value(step: String, key: String): String? = results.value(step, key)

    /** Wraps the caller's approval provider so the chain knows that a rejection was a missing owner yes. */
    fun approvals(delegate: AgentToolApprovalProvider): AgentToolApprovalProvider = AgentToolApprovalProvider { proposal, risk ->
        val index = synchronized(this) { done.size }
        plan.steps.getOrNull(index)?.let { runCatching { observer.waitingForApproval(index, it, plan.steps.size) } }
        val approval = delegate.requestApproval(proposal, risk)
        synchronized(this) { approvalDenied = !approval.userConfirmed }
        approval
    }

    /** Real dispatch only (BoundedAgentOrchestrator.onToolStarted). */
    fun toolStarted() {
        val index = synchronized(this) { dispatched = true; done.size }
        plan.steps.getOrNull(index)?.let { runCatching { observer.started(index, it, plan.steps.size) } }
    }

    @Synchronized override fun generate(task: String, memories: List<MemoryRecord>, reflection: AgentReflection?,
                                        toolObservations: List<AgentToolObservation>): AgentModelOutput {
        if (toolObservations.size < consumed) consumed = 0
        if (startedAt == NOT_STARTED) startedAt = clock()
        outcome?.let { return answer(it) }
        while (consumed < toolObservations.size) {
            val observation = toolObservations[consumed++]
            val index = done.size
            val step = plan.steps.getOrNull(index) ?: return answer(fail(index, "es kam ein Ergebnis ohne geplanten Schritt"))
            dispatched = false
            if (observation.toolName != step.tool) return answer(fail(index, "es kam das Ergebnis eines anderen Werkzeugs"))
            if (observation.status != ToolExecutionStatus.SUCCEEDED) {
                val needsYes = ToolRegistry.risk(step.tool) != Risk.READ_ONLY && approvalDenied
                approvalDenied = false
                if (needsYes) return answer(pause(index, Pause.NEEDS_APPROVAL, emptyList()))
                return answer(fail(index, failureReason(observation)))
            }
            approvalDenied = false
            val output = observation.output.orEmpty()
            step.success.problem(output, results::value)?.let { return answer(fail(index, "Erfolgsprüfung nicht bestanden: $it")) }
            when (val settled = settle(step, output, emptyMap())) {
                is Outcome -> return answer(settled)
                else -> Unit
            }
        }
        if (done.size == plan.steps.size) return answer(complete())
        if (clock() - startedAt > maxMillis) return answer(pause(done.size, Pause.INTERRUPTED, emptyList(),
            "Die Zeitgrenze für diesen Auftrag ist erreicht."))
        val index = done.size
        val step = plan.steps[index]
        val arguments = LinkedHashMap<String, Any?>()
        step.arguments.forEach { (key, argument) ->
            arguments[key] = when (argument) {
                is ChainArgument.Literal -> argument.value
                is ChainArgument.FromStep -> results.value(argument.step, argument.key)?.let { OwnerChainValues.part(it, argument.part) }
                    ?: return answer(fail(index, "der Wert „${argument.key}“ aus Schritt ${argument.step} fehlt"))
            }
        }
        val proposal = ToolProposal(step.tool, arguments)
        proposal.validate()?.let { return answer(fail(index, "das Werkzeug lehnt die Argumente ab ($it)")) }
        // Tool results are data. A resumed run starts with an empty observation list in the orchestrator, so the
        // chain applies the same provenance rule itself for every step that follows an earlier result.
        ExternalContentActionGuard.refusal(ownerTask, step.tool, ToolRegistry.risk(step.tool), arguments,
            untrustedObservationsSeen = done.isNotEmpty())?.let { return answer(fail(index, it, blocked = true)) }
        dispatched = false
        return AgentModelOutput(MODEL_ID, "", proposal)
    }

    /** Hands the values of [output] on; null when the step is done, an outcome when it failed or needs a choice. */
    private fun settle(step: ChainStep, output: String, picked: Map<String, String>): Outcome? {
        val index = done.size
        val values = LinkedHashMap<String, String>(picked)
        step.gives.forEach { extract ->
            when (val result = extract.extract(output, results::value)) {
                is ChainExtract.Result.Values -> result.values.forEach { (key, value) -> values.putIfAbsent(key, value) }
                is ChainExtract.Result.Missing -> return fail(index, result.reason)
                is ChainExtract.Result.Choice -> if (result.key !in values) {
                    pendingChoice = result
                    pendingOutput = output
                    return pause(index, Pause.NEEDS_CHOICE, result.options.map { it.first })
                }
            }
        }
        done += Done(step, output, values)
        runCatching { observer.settled(index, step, StepState.DONE) }
        return null
    }

    @Synchronized override fun evaluate(response: String, toolRecord: ToolExecutionRecord?): Evaluation {
        val current = outcome
        // A run that only had to take the owner's choice for the last step has no tool record of its own.
        val verified = current is Outcome.Completed && response == current.report && done.size == plan.steps.size &&
            (toolRecord == null || (toolRecord.toolName == plan.steps.last().tool && toolRecord.status == ToolExecutionStatus.SUCCEEDED))
        return Evaluation(verified, if (verified) 1.0 else 0.0,
            if (verified) "Alle Schritte der Aufgabenkette sind mit frischen Werkzeugergebnissen belegt."
            else "Aufgabenkette nicht abgeschlossen; kein Erfolg bestätigt.")
    }

    /**
     * The orchestrator's receipts decide: a chain counts as completed only when every step of this task has a fresh,
     * successful ToolGate call in [evidence] (the evidence of the run that finished it; earlier runs passed the same
     * check when they paused). False turns a completed chain into a failed one.
     */
    @Synchronized fun confirm(evidence: AgentTaskEvidence?, stepsBeforeRun: Int): Boolean {
        val current = outcome as? Outcome.Completed ?: return true
        val fresh = evidence?.calls.orEmpty().filter { it.status == ToolExecutionStatus.SUCCEEDED && it.freshOutput && !it.outcomeUncertain }
        val expected = plan.steps.drop(stepsBeforeRun).map { it.tool }
        if (fresh.map { it.toolName } == expected) return true
        outcome = Outcome.Failed(done.size, "Quittungen fehlen", "Nicht bestätigt: Für diesen Auftrag liegen nicht alle " +
            "Werkzeugquittungen vor. Ich melde ihn deshalb nicht als erledigt. " + doneLines(current.report.isNotEmpty()))
        return false
    }

    /** STOP or a lost run: the verified steps stay, the chain can be continued – unless a changing step was in flight. */
    @Synchronized fun interrupted(): Outcome {
        outcome?.let { if (it !is Outcome.Paused || it.pause != Pause.INTERRUPTED) return it }
        val index = done.size
        val step = plan.steps.getOrNull(index)
        return if (step != null && dispatched && ToolRegistry.risk(step.tool) != Risk.READ_ONLY)
            fail(index, "der Schritt war schon gestartet, als gestoppt wurde; ob er gewirkt hat, ist unbekannt. " +
                "Ich wiederhole ihn nicht von selbst")
        else pause(index, Pause.INTERRUPTED, emptyList(), "Angehalten.")
    }

    /**
     * The orchestrator ended the run without handing the chain a result to judge (uncertain tool outcome, unreadable
     * journal, exhausted budget). The chain fails at the open step with that reason; it never guesses an outcome.
     */
    @Synchronized fun aborted(reason: String): Outcome = outcome ?: fail(done.size, reason.ifBlank { "der Lauf wurde ohne Ergebnis beendet" })

    /**
     * Continue a paused chain: true when a new orchestrator run may start. [choice] is the 1-based pick for
     * [Pause.NEEDS_CHOICE]. A failed or completed chain cannot be resumed.
     */
    @Synchronized fun resume(choice: Int? = null): Boolean {
        val current = outcome as? Outcome.Paused ?: return false
        if (current.pause == Pause.NEEDS_CHOICE) {
            val open = pendingChoice ?: return false
            val option = open.options.getOrNull((choice ?: return false) - 1) ?: return false
            val step = plan.steps[done.size]
            val output = pendingOutput.orEmpty()
            pendingChoice = null
            pendingOutput = null
            outcome = null
            chosen = chosen + (open.key to option.second)
            settle(step, output, mapOf(open.key to option.second))?.let { outcome = it; return false }
        }
        outcome = null
        consumed = 0
        startedAt = NOT_STARTED
        approvalDenied = false
        dispatched = false
        return true
    }

    class Settled(val outcome: Outcome, val run: Result<AgentRunResult>)

    /**
     * One run of this chain in [orchestrator] (built by the caller with this chain as model and evaluator, [approvals]
     * as approval provider and [toolStarted] as dispatch callback). Always returns a settled outcome: completed and
     * receipt-confirmed, failed with its step and reason, or paused.
     */
    fun runWith(orchestrator: BoundedAgentOrchestrator): Settled {
        val before = completedSteps()
        val run = runCatching { orchestrator.run(ownerTask) }
        val settled = run.fold(
            onSuccess = { result ->
                confirm(result.taskEvidence, before)
                outcome() ?: aborted(result.blocker ?: result.response)
            },
            onFailure = { error ->
                if (error is java.util.concurrent.CancellationException) interrupted()
                else aborted("interner Fehler (${error.javaClass.simpleName})")
            })
        return Settled(settled, run)
    }

    private fun answer(result: Outcome): AgentModelOutput = AgentModelOutput(MODEL_ID, result.report.take(MAX_REPORT))

    private fun complete(): Outcome {
        val summary = runCatching { plan.summary(results) }.getOrDefault("").trim()
        val text = buildString {
            append(if (summary.isNotEmpty()) "Erledigt. $summary" else "Erledigt: ${plan.goal.trimEnd('.')}.")
            append("\n").append(doneLines(includeOutput = plan.origin == ChainPlan.Origin.MODEL || summary.isEmpty()))
        }.take(MAX_REPORT)
        runCatching { observer.ended(true) }
        return Outcome.Completed(text).also { outcome = it }
    }

    private fun fail(index: Int, reason: String, blocked: Boolean = false): Outcome {
        val step = plan.steps.getOrNull(index)
        step?.let { runCatching { observer.settled(index, it, if (blocked) StepState.BLOCKED else StepState.FAILED) } }
        val text = buildString {
            append("Nicht abgeschlossen: Schritt ${index + 1} von ${plan.steps.size}")
            step?.let { append(" („${it.label}“)") }
            append(" ist ").append(if (blocked) "nicht ausgeführt worden" else "fehlgeschlagen").append(" – ")
            append(reason.trim().trimEnd('.')).append('.')
            if (done.isNotEmpty()) append("\n").append(doneLines(includeOutput = false))
            append("\n").append(when {
                blocked -> "Wenn du das wirklich willst, gib es als eigenen, ausdrücklichen Auftrag."
                done.isEmpty() -> "Es wurde nichts verändert. Du kannst den Auftrag noch einmal geben oder genauer fassen."
                else -> "Die erledigten Schritte bleiben gültig; die folgenden habe ich nicht mehr ausgeführt."
            })
        }.take(MAX_REPORT)
        runCatching { observer.ended(false) }
        return Outcome.Failed(index, reason, text).also { outcome = it }
    }

    private fun pause(index: Int, pause: Pause, options: List<String>, lead: String = ""): Outcome {
        val step = plan.steps.getOrNull(index)
        val text = buildString {
            when (pause) {
                Pause.NEEDS_APPROVAL -> append("Angehalten bei Schritt ${index + 1} von ${plan.steps.size} („${step?.label}“): " +
                    "Dafür brauche ich deine Freigabe, ohne sie habe ich den Schritt nicht ausgeführt.")
                Pause.NEEDS_CHOICE -> {
                    append("Schritt ${index + 1} von ${plan.steps.size} („${step?.label}“) hat ${options.size} Treffer. Welchen meinst du?")
                    options.forEachIndexed { number, option -> append("\n${number + 1}. ").append(option.take(120)) }
                }
                Pause.INTERRUPTED -> append(lead.ifBlank { "Angehalten." }).append(" Offen ist Schritt ${index + 1} von ${plan.steps.size}")
                    .append(step?.let { " („${it.label}“)." } ?: ".")
            }
            if (done.isNotEmpty()) append("\n").append(doneLines(includeOutput = false))
            append("\n").append(when (pause) {
                Pause.NEEDS_CHOICE -> "Sag zum Beispiel „die zweite“ oder „weiter mit 2“."
                Pause.NEEDS_APPROVAL -> "Mit „weiter“ frage ich noch einmal; sonst bleibt es dabei."
                Pause.INTERRUPTED -> "Mit „weiter“ mache ich dort weiter."
            })
        }.take(MAX_REPORT)
        if (pause == Pause.NEEDS_APPROVAL) step?.let { runCatching { observer.settled(index, it, StepState.WAITING) } }
        runCatching { observer.ended(false) }
        return Outcome.Paused(index, pause, options, text).also { outcome = it }
    }

    private fun doneLines(includeOutput: Boolean): String = buildString {
        append("Erledigt und geprüft:")
        if (done.isEmpty()) append(" noch kein Schritt.")
        done.forEachIndexed { index, item ->
            append("\n${index + 1}. ").append(item.step.label)
            // A plan without its own closing sentence reports what each step handed on – the values the chain
            // itself read from the real output – and falls back to the first output lines.
            if (includeOutput) append(": ").append(
                (if (item.values.isNotEmpty()) item.values.entries.joinToString("; ") { "${it.key} = ${it.value.replace("\n", " · ").take(160)}" }
                else item.output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(" · ")).take(320))
        }
    }

    private fun failureReason(observation: AgentToolObservation): String {
        val detail = observation.output?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(200)
        return when (observation.status) {
            ToolExecutionStatus.REJECTED -> "das Werkzeug wurde nicht freigegeben" + (detail?.let { " ($it)" } ?: "")
            ToolExecutionStatus.TIMED_OUT -> "das Werkzeug hat nicht rechtzeitig geantwortet"
            ToolExecutionStatus.CANCELLED -> "das Werkzeug wurde abgebrochen"
            else -> "das Werkzeug meldet einen Fehler" + (detail?.let { " ($it)" } ?: "")
        }
    }

    companion object {
        const val MODEL_ID = "deterministic-owner-task-chain-v1"
        const val DEFAULT_MAX_MILLIS = 180_000L
        const val MAX_REPORT = 3_000
        private const val NOT_STARTED = Long.MIN_VALUE

        /** Orchestrator budget for one run of [plan]: every remaining step once, no retry. */
        fun limits(plan: ChainPlan): AgentLimits =
            AgentLimits(maxSteps = (8 + plan.steps.size * 6).coerceAtMost(64), maxToolCalls = plan.steps.size, maxRetries = 0)
    }
}

/**
 * Feeds the real steps of a chain to the existing cockpit bus, i.e. to [at.heinz.deviceai.ui.Command03ActivityProjection]
 * (the stage's step display). A step appears when it waits for approval or is dispatched, never earlier; its state
 * follows the verified result. No step, percentage or duration is invented.
 */
class OwnerTaskChainStage(
    private val turnId: String,
    private val emit: (CockpitAnimationEvent) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) : OwnerTaskChainObserver {
    private val steps = LinkedHashMap<Int, ProcessStep>()

    @Synchronized override fun waitingForApproval(index: Int, step: ChainStep, total: Int) =
        put(index, step, total, StepState.WAITING)

    @Synchronized override fun started(index: Int, step: ChainStep, total: Int) {
        put(index, step, total, StepState.ACTIVE)
        emit(CockpitAnimationEvent.ActionStarted(turnId, clock(),
            if (ToolRegistry.risk(step.tool) == Risk.READ_ONLY) CockpitAction.TOOL else CockpitAction.EXECUTE,
            "Schritt ${index + 1} von $total · ${step.label}", stepKey = key(index)))
    }

    @Synchronized override fun settled(index: Int, step: ChainStep, state: StepState) {
        val known = steps[index] ?: return
        steps[index] = known.copy(state = state)
        timeline()
    }

    @Synchronized override fun ended(completed: Boolean) {
        if (steps.isEmpty()) return
        emit(CockpitAnimationEvent.TurnEnded(turnId, clock(), completed))
    }

    private fun put(index: Int, step: ChainStep, total: Int, state: StepState) {
        steps[index] = ProcessStep(key(index), "${index + 1}/$total ${step.label}".take(80), state)
        timeline()
    }

    private fun timeline() = emit(CockpitAnimationEvent.ProcessTimeline(turnId, clock(), steps.values.toList()))
    private fun key(index: Int) = "chain:$index"
}
