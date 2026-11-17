package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumDashboardState.Event
import at.heinz.deviceai.ui.ContinuumDashboardState.Project
import at.heinz.deviceai.ui.ContinuumDashboardState.ProjectState
import at.heinz.deviceai.ui.ContinuumDashboardState.Segment

/**
 * TEST/PREVIEW ONLY: the example values printed in the owner's reference images, used solely to compare the
 * drawn composition pixel-for-pixel with those images. The app never uses this object; it shows real values.
 */
object ReferencePreviewData {
    val state = ContinuumDashboardState(
        model = ContinuumDashboardState.ModelStatus(name = "Continuum Ultra", online = true, stateLabel = "Online", qualityPercent = 98,
            latencyMs = 320, context = "1M Tokens", mode = "Intelligenter Assistent", lastUpdate = "Heute, 14:12"),
        learning = ContinuumDashboardState.LearningStatus(today = 247, changePercent = 12, live = true,
            series = listOf(1f, 1.6f, 1.2f, 2.4f, 1.9f, 3.2f, 2.2f, 2.8f, 2.1f, 3.6f, 2.6f, 3.0f, 2.4f, 4.0f, 3.1f, 3.6f),
            recent = listOf(Event("Neueste Forschungspapiere analysiert", "vor 6 Min.", 0), Event("Wissensgraph erweitert", "vor 18 Min.", 1),
                Event("Neue Zusammenhänge entdeckt", "vor 37 Min.", 2), Event("Model verbessert sich selbst", "laufend", 3))),
        storage = ContinuumDashboardState.StorageStatus(usedPercent = 78, activeSources = 12, totalSources = 13, syncLabel = "Live-Synchronisation",
            segments = listOf(Segment("Eigene Daten", 432_000_000_000, Argb.rgb(70, 110, 255)), Segment("Wissensquellen", 287_000_000_000, Argb.rgb(40, 220, 200)),
                Segment("Projekte", 164_000_000_000, Argb.rgb(190, 90, 255)), Segment("System", 117_000_000_000, Argb.rgb(60, 190, 255)))),
        development = ContinuumDashboardState.DevelopmentStatus(activeProjects = 8, runningTasks = 3, errors = 0,
            series = listOf(2f, 3f, 2.4f, 4f, 3f, 5f, 3.6f, 5.4f, 4f, 4.6f, 3.4f, 5f, 6f, 3.8f, 3.4f, 4.4f),
            projects = listOf(Project("Continuum Core", "Build erfolgreich", ProjectState.OK, "vor 2 Min."),
                Project("Research Pipeline", "Tests laufen", ProjectState.RUNNING, "vor 14 Min."),
                Project("Knowledge Graph", "Wird optimiert", ProjectState.WORKING, "vor 28 Min."),
                Project("UI Framework", "Deployment bereit", ProjectState.READY, "vor 1 Std."))),
        system = ContinuumDashboardState.SystemStatus(appCpuPercent = 98, memoryPercent = 51, storagePercent = 78, networkOnline = true,
            networkLabel = "Verbunden", openIssues = 0,
            activity = listOf(1f, 2f, 1.4f, 2.8f, 2f, 3.4f, 2.4f, 4.2f, 3f, 3.8f, 2.6f, 3.2f, 2.2f)),
        core = ContinuumDashboardState.CoreStatus(connected = "Globale Ressourcen", thinking = "Mehrdimensional", learning = "Echtzeit",
            analysing = "In Echtzeit", developing = "Aktiv", evolving = "Kontinuierlich"),
        pendingNotifications = 1
    )
}
