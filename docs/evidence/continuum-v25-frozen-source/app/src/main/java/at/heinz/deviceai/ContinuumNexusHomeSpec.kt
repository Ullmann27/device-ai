package at.heinz.deviceai

/**
 * Stable beginner navigation for the existing native Nexus dashboard.
 * The destinations are wired to existing owner-controlled Android screens,
 * never to direct shell commands, training operations or APK installation.
 */
internal enum class ContinuumNexusDestination {
    CHAT, CREATE_APP, KNOWLEDGE, PERSONALIZE, TASKS
}

internal data class ContinuumNexusAction(
    val destination: ContinuumNexusDestination,
    val symbol: String,
    val title: String,
    val description: String
)

internal object ContinuumNexusHomeSpec {
    val voice = ContinuumNexusAction(
        ContinuumNexusDestination.CHAT,
        "◉",
        "Mit meiner KI sprechen",
        "Fragen stellen oder einen Wunsch einfach aussprechen"
    )

    val essentials = listOf(
        ContinuumNexusAction(
            ContinuumNexusDestination.CREATE_APP,
            "⬡",
            "App erstellen",
            "Beschreibe deine Idee. Die KI bereitet einen überprüfbaren Entwurf vor."
        ),
        ContinuumNexusAction(
            ContinuumNexusDestination.KNOWLEDGE,
            "▤",
            "Meine Dateien & Wissen",
            "Gespeicherte Fakten und Quellen ansehen und kontrollieren."
        ),
        ContinuumNexusAction(
            ContinuumNexusDestination.PERSONALIZE,
            "✧",
            "KI personalisieren",
            "Lernen, Erinnerungen und lokale Modelle selbst bestimmen."
        ),
        ContinuumNexusAction(
            ContinuumNexusDestination.TASKS,
            "✓",
            "Meine Aufgaben",
            "Entwicklungsaufträge, Prüfungen und Ergebnisse verfolgen."
        )
    )

    /** Preserve readable touch targets when narrow screens or enlarged fonts need a single column. */
    fun columns(usableWidthDp: Int, fontScale: Float): Int =
        if (usableWidthDp >= 320 && fontScale.isFinite() && fontScale <= 1.25f) 2 else 1
}
