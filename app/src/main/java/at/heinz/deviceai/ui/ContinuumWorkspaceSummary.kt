package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Row

/** Real home workspace values. */
object ContinuumWorkspaceSummary {
    fun projects(state: ContinuumDashboardState): List<Row> =
        state.development.projects.map { Row(it.name, it.status, null) }
            .ifEmpty { listOf(Row("Projekte", "Keine Projektdaten verfügbar", null)) }

    fun work(state: ContinuumDashboardState): List<Row> =
        state.cockpit.openWork.map { Row("Offen", it, null) }
            .ifEmpty { ContinuumCockpitPresentation.processRows(state.cockpit) }

    fun lines(state: ContinuumDashboardState): List<String> {
        val document = DocumentBlueprintView.visible(state.document)
        return listOf(
            document?.let { "Dokument: ${it.documentName}" } ?: "Kein Dokument geöffnet",
            projects(state).first().let { "${it.label}: ${it.value}" },
            "Modell: ${state.model.name} · ${state.model.stateLabel}",
            "CPU ${ContinuumDashboardState.percent(state.system.appCpuPercent)}  ·  " +
                "RAM ${ContinuumDashboardState.percent(state.system.memoryPercent)}"
        )
    }
}
