package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Ephemeral discourse state, not durable memory.
 *
 * It stores only bounded topic/focus metadata for the current conversation and never
 * grants fact or tool authority. The existing LocalConversationContext remains the
 * owner of actual same-session turns.
 */
internal class ConversationSituationModel(
    private val maxThreads: Int = 8,
    private val maxCuesPerThread: Int = 16,
    private val normalization: CueNormalization = CueNormalization.DEFAULT
) {
    enum class Lifecycle { ACTIVE, PAUSED, DORMANT, RESOLVED }

    /**
     * Shared H4-B1 normalization keeps complete bounded words. Only common inflection
     * suffixes may extend a stem of at least [prefixMin] characters; arbitrary compounds
     * such as Arbeit/Arbeitsamt must not bind. No fact, memory or tool authority.
     */
    data class CueNormalization(val foldUmlauts: Boolean, val maxLength: Int, val prefixMin: Int?) {
        init {
            require(maxLength in 4..80)
            require(prefixMin == null || prefixMin >= 3)
        }

        fun normalize(word: String): String? {
            val lower = word.lowercase(Locale.GERMAN)
            val folded = if (foldUmlauts) {
                lower.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            } else lower
            return folded.takeIf { it.length <= maxLength }
        }

        fun matches(query: String, cue: String): Boolean {
            if (query == cue) return true
            val minimum = prefixMin ?: return false
            val (short, long) = if (query.length <= cue.length) query to cue else cue to query
            return short.length >= minimum && long.startsWith(short) &&
                long.substring(short.length) in INFLECTION_SUFFIXES
        }

        companion object {
            private val INFLECTION_SUFFIXES = setOf("e", "en", "er", "es", "n", "s")
            val DEFAULT = CueNormalization(foldUmlauts = true, maxLength = 80, prefixMin = 6)
        }
    }

    data class ThreadState(
        val id: String,
        val label: String,
        val lifecycle: Lifecycle,
        val openedAtTurn: Int,
        val lastTouchedTurn: Int,
        val cues: Set<String>,
        val openQuestions: List<String> = emptyList(),
        val recentCorrections: List<String> = emptyList()
    )

    data class Situation(
        val turn: Int,
        val currentThreadId: String?,
        val currentGoal: String?,
        val currentProject: String?,
        val currentTask: String?,
        val unresolvedReference: String?,
        val threads: List<ThreadState>
    )

    sealed interface Resolution {
        data class Resolved(val thread: ThreadState, val score: Float): Resolution
        data class Ambiguous(val candidates: List<ThreadState>): Resolution
        data object NoMatch: Resolution
    }

    private val threads = LinkedHashMap<String, ThreadState>()
    private var turn = 0
    private var activeId: String? = null
    private var currentGoal: String? = null
    private var currentProject: String? = null
    private var currentTask: String? = null
    private var unresolvedReference: String? = null
    private var workingTask: WorkingTaskSnapshot? = null
    private var workingTaskGeneration = 0L
    private var workingTaskResolutionTurn = -1
    private val workingTaskContextNonce = java.util.UUID.randomUUID().toString()

    fun bindWorkingTask(seed: WorkingTaskSeed): Boolean {
        val previous = workingTask
        if (previous != null && previous.seed.taskId == seed.taskId && seed.revision < previous.seed.revision)
            return false
        if (previous?.blockedReason != null && previous.seed.taskId == seed.taskId && seed.revision <= previous.seed.revision)
            return false
        if (!WorkingTaskContext.valid(seed) || previous != null && previous.seed.taskId == seed.taskId &&
            seed.revision == previous.seed.revision && seed != previous.seed) {
            workingTask = previous?.copy(currentPlan = null, blockedReason = "CONFLICTING_TASK_BINDING",
                admissionGeneration = nextWorkingTaskGeneration())
            return false
        }
        workingTask = WorkingTaskSnapshot(WorkingTaskContext.copy(seed), seed.selectedReferenceId,
            admissionGeneration = nextWorkingTaskGeneration(), contextNonce = workingTaskContextNonce)
        workingTaskResolutionTurn = -1
        setExplicitScope(task = seed.taskId, goal = seed.goal)
        return true
    }

    fun workingTaskSnapshot(): WorkingTaskSnapshot? = workingTask?.let(WorkingTaskContext::copy)

    fun resolveWorkingTask(request: String): WorkingTaskDecision {
        val old = workingTask ?: return WorkingTaskDecision.NoMatch
        if (WorkingTaskContext.isStop(request)) {
            suspendWorkingTask()
            return WorkingTaskDecision.NoMatch
        }
        val sameAdmission = old.currentPlan?.originalRequest == request && workingTaskResolutionTurn == turn
        val admitted = if (sameAdmission) old else old.copy(admissionGeneration = nextWorkingTaskGeneration())
        val decision = WorkingTaskContext.resolve(admitted, request)
        workingTask = when (decision) {
            is WorkingTaskDecision.Resolved -> admitted.copy(currentPlan = WorkingTaskContext.copy(decision.plan),
                suspended = false, unresolvedReference = null)
            is WorkingTaskDecision.Clarify -> admitted.copy(currentPlan = null, selectedReferenceId = null,
                unresolvedReference = request.take(160), blockedReason =
                    if (decision.reason == "CONSTRAINT_CHANGE_REQUIRES_REBIND") decision.reason else admitted.blockedReason)
            else -> admitted.copy(currentPlan = null)
        }
        workingTaskResolutionTurn = turn
        if (decision is WorkingTaskDecision.Resolved) setExplicitScope(task = old.seed.taskId, goal = old.seed.goal)
        return decision
    }

    fun suspendWorkingTask() {
        workingTask = workingTask?.copy(suspended = true, currentPlan = null, unresolvedReference = null,
            admissionGeneration = nextWorkingTaskGeneration())
    }

    fun invalidateWorkingTaskReference(referenceId: String): Boolean {
        val old = workingTask ?: return false
        if (old.seed.references.none { it.id == referenceId }) return false
        val seed = old.seed.copy(references = old.seed.references.map {
            if (it.id == referenceId) it.copy(revoked = true) else it
        }, selectedReferenceId = old.seed.selectedReferenceId?.takeUnless { it == referenceId })
        workingTask = old.copy(seed = seed,
            selectedReferenceId = old.selectedReferenceId?.takeUnless { it == referenceId }, currentPlan = null,
            admissionGeneration = nextWorkingTaskGeneration())
        return true
    }

    private fun nextWorkingTaskGeneration(): Long = Math.addExact(workingTaskGeneration, 1L).also {
        workingTaskGeneration = it
    }

    fun isWorkingTaskPlanCurrent(plan: WorkingTaskPlan): Boolean {
        val current = workingTask ?: return false
        return !current.suspended && current.blockedReason == null && current.currentPlan == plan &&
            current.seed.taskId == plan.taskId && current.seed.revision == plan.revision &&
            current.seed.references.any { it == plan.reference && !it.revoked }
    }

    fun nextTurn(): Int = ++turn

    fun setExplicitScope(project: String? = currentProject, task: String? = currentTask, goal: String? = currentGoal) {
        currentProject = cleanScope(project)
        currentTask = cleanScope(task)
        currentGoal = cleanScope(goal)
    }

    fun openThread(id: String, label: String, cues: Collection<String>, atTurn: Int = turn): ThreadState {
        require(ID.matches(id))
        require(label.isNotBlank() && label.length <= 120)
        require(atTurn >= 0)
        val normalizedCues = cues.asSequence().flatMap { tokens(it).asSequence() }
            .distinct().take(maxCuesPerThread).toSet()
        require(normalizedCues.isNotEmpty())

        activeId?.takeIf { it != id }?.let { oldId ->
            threads[oldId]?.let { old ->
                threads[oldId] = old.copy(lifecycle = Lifecycle.PAUSED)
            }
        }
        val previous = threads[id]
        val state = ThreadState(
            id = id,
            label = label.trim(),
            lifecycle = Lifecycle.ACTIVE,
            openedAtTurn = previous?.openedAtTurn ?: atTurn,
            lastTouchedTurn = atTurn,
            cues = (previous?.cues.orEmpty() + normalizedCues).take(maxCuesPerThread).toSet(),
            openQuestions = previous?.openQuestions.orEmpty(),
            recentCorrections = previous?.recentCorrections.orEmpty()
        )
        threads[id] = state
        activeId = id
        trimThreads()
        return state
    }

    fun pauseActive(dormant: Boolean = false) {
        val id = activeId ?: return
        threads[id]?.let {
            threads[id] = it.copy(lifecycle = if (dormant) Lifecycle.DORMANT else Lifecycle.PAUSED)
        }
        activeId = null
    }

    fun resolveThread(id: String) {
        threads[id]?.let { threads[id] = it.copy(lifecycle = Lifecycle.RESOLVED) }
        if (activeId == id) activeId = null
    }

    fun addOpenQuestion(threadId: String, question: String) {
        val old = threads[threadId] ?: return
        val q = question.trim().take(180)
        if (q.isBlank()) return
        threads[threadId] = old.copy(openQuestions = (old.openQuestions + q).distinct().takeLast(4))
    }

    fun recordCorrection(threadId: String, correction: String) {
        val old = threads[threadId] ?: return
        val c = correction.trim().take(180)
        if (c.isBlank()) return
        threads[threadId] = old.copy(recentCorrections = (old.recentCorrections + c).distinct().takeLast(4))
    }

    /**
     * Resolves an explicit return cue against paused/dormant threads.
     * It never auto-binds when the top two candidates are too close.
     */
    fun resolveReturn(text: String, atTurn: Int = turn): Resolution {
        val query = tokens(text)
        val genericReturn = GENERIC_RETURN.containsMatchIn(text)
        if (query.isEmpty() && !genericReturn) return Resolution.NoMatch
        val candidates = threads.values.filter { it.lifecycle in setOf(Lifecycle.PAUSED, Lifecycle.DORMANT) }
            .mapNotNull { state ->
                val overlap = query.count { token -> state.cues.any { cue -> normalization.matches(token, cue) } }
                if (overlap == 0 && !genericReturn) return@mapNotNull null
                val openBoost = if (state.openQuestions.isNotEmpty()) 0.20f else 0f
                val correctionBoost = if (state.recentCorrections.isNotEmpty()) 0.12f else 0f
                val recency = 1f / (1f + .08f * (atTurn - state.lastTouchedTurn).coerceAtLeast(0))
                val score = overlap * .45f + openBoost + correctionBoost + recency * .25f
                state to score
            }
            .filter { it.second >= MIN_RETURN_SCORE }
            .sortedWith(compareByDescending<Pair<ThreadState, Float>> { it.second }
                .thenByDescending { it.first.lastTouchedTurn }
                .thenBy { it.first.id })

        if (candidates.isEmpty()) return Resolution.NoMatch
        val best = candidates[0]
        val second = candidates.getOrNull(1)
        if (second != null && best.second - second.second < MIN_MARGIN) {
            unresolvedReference = text.take(160)
            return Resolution.Ambiguous(candidates.take(2).map { it.first })
        }
        unresolvedReference = null
        activate(best.first.id, atTurn)
        return Resolution.Resolved(threads.getValue(best.first.id), best.second)
    }

    fun markUnresolved(reference: String?) {
        unresolvedReference = reference?.trim()?.take(160)?.takeIf(String::isNotBlank)
    }

    fun snapshot(): Situation = Situation(
        turn = turn,
        currentThreadId = activeId,
        currentGoal = currentGoal,
        currentProject = currentProject,
        currentTask = currentTask,
        unresolvedReference = unresolvedReference,
        threads = threads.values.sortedWith(compareByDescending<ThreadState> { it.lifecycle == Lifecycle.ACTIVE }
            .thenByDescending { it.lastTouchedTurn }.thenBy { it.id })
    )

    fun reset(preserveWorkingTask: Boolean = false) {
        threads.clear()
        turn = 0
        activeId = null
        currentGoal = null
        currentProject = null
        currentTask = null
        unresolvedReference = null
        if (preserveWorkingTask) suspendWorkingTask() else workingTask = null
    }

    private fun activate(id: String, atTurn: Int) {
        activeId?.takeIf { it != id }?.let { previous ->
            threads[previous]?.let { threads[previous] = it.copy(lifecycle = Lifecycle.PAUSED) }
        }
        val state = threads[id] ?: return
        threads[id] = state.copy(lifecycle = Lifecycle.ACTIVE, lastTouchedTurn = atTurn)
        activeId = id
    }

    private fun trimThreads() {
        while (threads.size > maxThreads) {
            val victim = threads.values
                .filter { it.id != activeId }
                .minWithOrNull(compareBy<ThreadState> { retentionRank(it.lifecycle) }
                    .thenBy { it.lastTouchedTurn }.thenBy { it.id })
                ?: break
            threads.remove(victim.id)
        }
    }

    private fun retentionRank(lifecycle: Lifecycle): Int = when (lifecycle) {
        Lifecycle.RESOLVED -> 0
        Lifecycle.DORMANT -> 1
        Lifecycle.PAUSED -> 2
        Lifecycle.ACTIVE -> 3
    }

    private fun cleanScope(value: String?): String? =
        value?.trim()?.take(160)?.takeIf { it.isNotBlank() && !it.any(Char::isISOControl) }

    private fun cue(value: String): String =
        value.lowercase(Locale.GERMAN).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().take(80)

    private fun tokens(value: String): Set<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(value.lowercase(Locale.GERMAN))
            .map { it.value }.filter { it.length >= 3 && it !in STOP }.take(64)
            .mapNotNull(normalization::normalize).toSet()

    companion object {
        private val ID = Regex("[A-Za-z0-9._:-]{1,96}")
        private const val MIN_RETURN_SCORE = .25f
        private const val MIN_MARGIN = .18f
        private val GENERIC_RETURN = Regex("(?iu)\\b(?:vorhin|davor|weiter|fortsetzen|dort\\s+weiter|zurück\\s+zu)\\b")
        private val STOP = setOf(
            "und","aber","das","die","der","den","dem","von","vorhin","davor","weiter",
            "bitte","mach","mache","wir","ich","du","jetzt","noch","mal","zurück"
        )
    }
}
