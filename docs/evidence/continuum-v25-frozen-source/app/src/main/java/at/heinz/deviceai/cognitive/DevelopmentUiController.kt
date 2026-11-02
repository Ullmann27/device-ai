package at.heinz.deviceai.cognitive

enum class DevelopmentUiPhase { IDLE, RUNNING, DONE, FAILED, CANCELLED }

data class DevelopmentUiState(
    val phase: DevelopmentUiPhase = DevelopmentUiPhase.IDLE,
    val goal: String = "",
    val sources: List<SourceSnapshot> = emptyList(),
    val status: String = "Bereit. Beschreibe deine Idee und wähle die passenden Dateien aus.",
    val preview: String? = null,
    val error: String? = null,
    val sourceImportPending: Boolean = false
) {
    val canEdit: Boolean get() = phase != DevelopmentUiPhase.RUNNING
    val canStart: Boolean get() = canEdit && !sourceImportPending
    val canStop: Boolean get() = phase == DevelopmentUiPhase.RUNNING
    val canExport: Boolean get() = phase == DevelopmentUiPhase.DONE && preview != null && !sourceImportPending
}

/** UI-thread-confined controller; no I/O and no execution capabilities. */
class DevelopmentUiController(private val newTaskId: () -> String) {
    var state = DevelopmentUiState()
        private set

    private fun edited() = state.copy(phase = DevelopmentUiPhase.IDLE, preview = null, error = null,
        status = "Eingaben geändert. Der alte Vorschlag ist nicht mehr gültig. Starte die Prüfung erneut.")

    fun setGoal(goal: String) {
        if (state.canEdit && goal != state.goal) state = edited().copy(goal = goal)
    }


    /** Revoke an old proposal immediately while SAF reads are still queued off the UI thread. */
    fun beginSourceImport(): Boolean {
        if (!state.canStart) return false
        state = edited().copy(sourceImportPending = true, status = "Dateien werden sicher eingelesen …")
        return true
    }

    fun finishSourceImport() {
        if (!state.sourceImportPending) return
        state = state.copy(sourceImportPending = false,
            status = "Dateien ausgewählt. Bitte prüfe die Auswahl vor dem Start.")
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
        if (state.canStart) state = edited().copy(sources = emptyList())
    }

    fun start(modelReady: Boolean): DevelopmentTask? {
        if (!state.canStart) return null
        if (!modelReady) {
            state = state.copy(phase = DevelopmentUiPhase.FAILED, preview = null,
                error = "Kein KI-Sprachmodell geladen. Öffne „Sprachmodell einrichten“, importiere eine kompatible LiteRT-LM-Datei und lade sie. Es gibt keinen Cloud-Ersatz.",
                status = "Noch nicht bereit: Sprachmodell fehlt.")
            return null
        }
        val task = try {
            DevelopmentTask(newTaskId(), state.goal.trim(), state.sources.toList())
        } catch (e: IllegalArgumentException) {
            state = state.copy(phase = DevelopmentUiPhase.FAILED, preview = null, error = e.message ?: "Invalid task.",
                status = "Start nicht möglich: Bitte Entwicklungsziel und Quelldateien prüfen.")
            return null
        }
        state = state.copy(phase = DevelopmentUiPhase.RUNNING, preview = null, error = null, status = "Auftrag vorbereitet …")
        return task
    }

    fun onJobState(job: DevelopmentJobState) {
        if (state.phase != DevelopmentUiPhase.RUNNING) return
        state = state.copy(status = when (job) {
            DevelopmentJobState.PENDING -> "Auftrag vorbereitet …"
            DevelopmentJobState.RUNNING_DEVELOPER -> "1/4 · Änderungsvorschlag wird erstellt …"
            DevelopmentJobState.RUNNING_CRITIC -> "2/4 · Vorschlag wird auf Fehler geprüft …"
            DevelopmentJobState.RUNNING_TEST_DESIGNER -> "3/4 · Passende Tests werden vorgeschlagen …"
            DevelopmentJobState.RUNNING_JUDGE -> "4/4 · Ergebnis wird abschließend geprüft …"
            else -> state.status
        })
    }

    fun stop() {
        if (state.canStop) state = state.copy(status = "Wird beendet …")
    }

    fun onOutcome(outcome: DevelopmentOutcome) {
        if (state.phase != DevelopmentUiPhase.RUNNING) return
        state = when (outcome.state) {
            DevelopmentJobState.COMPLETED -> state.copy(phase = DevelopmentUiPhase.DONE,
                preview = outcome.proposal!!.render(), error = null,
                status = "Vorschlag fertig. Es wurde nichts geändert, kompiliert oder getestet.")
            DevelopmentJobState.CANCELLED -> state.copy(phase = DevelopmentUiPhase.CANCELLED,
                preview = null, error = null, status = "Von dir beendet.")
            else -> state.copy(phase = DevelopmentUiPhase.FAILED, preview = null,
                error = outcome.failure?.let { "${it.kind}: ${it.message}" } ?: outcome.state.name,
                status = if (outcome.state == DevelopmentJobState.TIMED_OUT) "Zeitlimit erreicht." else "Fehlgeschlagen.")
        }
    }

    fun exportText(): String? = if (state.canExport) state.preview else null
}
