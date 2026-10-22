package at.heinz.deviceai

/**
 * Pure Kotlin, owner-visible work-area navigation contract.
 * Presentation has no permissions, shell execution or model mutations.
 */
enum class ContinuumWorkArea(
    val title: String,
    val symbol: String,
    val explanation: String
) {
    START("Start", "⌂", "Startseite und Übersicht öffnen"),
    CHAT("Chat", "◉", "Mit der lokalen KI sprechen"),
    LERNEN("Lernen", "✧", "Lernzentrum und Trainingsstand öffnen"),
    ENTWICKELN("Entwickeln", "⌘", "Sichere Entwicklungswerkzeuge öffnen"),
    MEHR("Mehr", "⋯", "Forschung, Modelle, Speicher und System öffnen")
}

object ContinuumWorkAreaNavigationSpec {
    val destinations: List<ContinuumWorkArea> = listOf(
        ContinuumWorkArea.START,
        ContinuumWorkArea.CHAT,
        ContinuumWorkArea.LERNEN,
        ContinuumWorkArea.ENTWICKELN,
        ContinuumWorkArea.MEHR
    )

    fun showBottomBar(wide: Boolean, keyboardVisible: Boolean): Boolean =
        !wide && !keyboardVisible

    fun accessibilityLabel(destination: ContinuumWorkArea): String =
        destination.title + " – " + destination.explanation
}
