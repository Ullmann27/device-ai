package at.heinz.deviceai.cognitive

enum class DevelopmentUiPhase { IDLE, RUNNING, DONE, FAILED, CANCELLED }

data class DevelopmentUiState(
    val phase: DevelopmentUiPhase = DevelopmentUiPhase.IDLE,
    val goal: String = "",
    val sources: List<SourceSnapshot> = emptyList(),
    val status: String = "Idle. Select source text and describe one development task.",
    val preview: String? = null,
    val error: String? = null
) {
    val canEdit: Boolean get() = phase != DevelopmentUiPhase.RUNNING
    val canStop: Boolean get() = phase == DevelopmentUiPhase.RUNNING
    val canExport: Boolean get() = phase == DevelopmentUiPhase.DONE && preview != null
}

/** UI-thread-confined controller; no I/O and no execution capabilities. */
class DevelopmentUiController(private val newTaskId: () -> String) {
    var state = DevelopmentUiState()
        private set

    private fun edited() = state.copy(phase = DevelopmentUiPhase.IDLE, preview = null, error = null,
        status = "Input changed. Start a new review; previous proposal is invalidated.")

    fun setGoal(goal: String) {
        if (state.canEdit && goal != state.goal) state = edited().copy(goal = goal)
    }

    fun addSource(path: String, content: String): Boolean {
        if (!state.canEdit) return false
        return try {
            val budget = DevelopmentLimits.DEFAULT_SOURCE_BYTES
            require(utf8Bytes(content) <= budget) { "$path exceeds the $budget-byte source budget; select a smaller file." }
            require(state.sources.sumOf { utf8Bytes(it.content) } <= budget - utf8Bytes(content)) {
                "Selected sources exceed $budget UTF-8 bytes; select a smaller source set."
            }
            val snapshot = SourceSnapshot.of(path, content)
            require(state.sources.none { it.path == path }) { "$path is already selected." }
            require(state.sources.size < DevelopmentLimits.MAX_SOURCES) { "At most ${DevelopmentLimits.MAX_SOURCES} source files." }
            state = edited().copy(sources = state.sources + snapshot)
            true
        } catch (e: IllegalArgumentException) {
            state = state.copy(error = e.message ?: "Source rejected.")
            false
        }
    }

    fun clearSources() {
        if (state.canEdit) state = edited().copy(sources = emptyList())
    }

    fun start(modelReady: Boolean): DevelopmentTask? {
        if (!state.canEdit) return null
        if (!modelReady) {
            state = state.copy(phase = DevelopmentUiPhase.FAILED, preview = null,
                error = "No local model loaded. Load a local model first; there is no cloud fallback.",
                status = "Failed: no local model.")
            return null
        }
        val task = try {
            DevelopmentTask(newTaskId(), state.goal.trim(), state.sources.toList())
        } catch (e: IllegalArgumentException) {
            state = state.copy(phase = DevelopmentUiPhase.FAILED, preview = null, error = e.message ?: "Invalid task.",
                status = "Failed: invalid task.")
            return null
        }
        state = state.copy(phase = DevelopmentUiPhase.RUNNING, preview = null, error = null, status = "Pending…")
        return task
    }

    fun onJobState(job: DevelopmentJobState) {
        if (state.phase != DevelopmentUiPhase.RUNNING) return
        state = state.copy(status = when (job) {
            DevelopmentJobState.PENDING -> "Pending…"
            DevelopmentJobState.RUNNING_DEVELOPER -> "1/4 Developer drafting proposal…"
            DevelopmentJobState.RUNNING_CRITIC -> "2/4 Critic reviewing…"
            DevelopmentJobState.RUNNING_TEST_DESIGNER -> "3/4 Test designer suggesting tests…"
            DevelopmentJobState.RUNNING_JUDGE -> "4/4 Judge deciding…"
            else -> state.status
        })
    }

    fun stop() {
        if (state.canStop) state = state.copy(status = "Stopping…")
    }

    fun onOutcome(outcome: DevelopmentOutcome) {
        if (state.phase != DevelopmentUiPhase.RUNNING) return
        state = when (outcome.state) {
            DevelopmentJobState.COMPLETED -> state.copy(phase = DevelopmentUiPhase.DONE,
                preview = outcome.proposal!!.render(), error = null,
                status = "Proposal ready for review. Nothing was applied, compiled or tested.")
            DevelopmentJobState.CANCELLED -> state.copy(phase = DevelopmentUiPhase.CANCELLED,
                preview = null, error = null, status = "Stopped by user.")
            else -> state.copy(phase = DevelopmentUiPhase.FAILED, preview = null,
                error = outcome.failure?.let { "${it.kind}: ${it.message}" } ?: outcome.state.name,
                status = if (outcome.state == DevelopmentJobState.TIMED_OUT) "Timed out." else "Failed.")
        }
    }

    fun exportText(): String? = if (state.canExport) state.preview else null
}
