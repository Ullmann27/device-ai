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

    /** Readable quick actions on compact displays and at large system font scales. */
    fun quickActionColumns(usableWidthDp: Int, fontScale: Float): Int =
        if (usableWidthDp >= 320 && fontScale.isFinite() && fontScale in 0.5f..1.25f) 2 else 1

    /** "More" is a transient dialog, never a lasting screen selection. */
    fun selectionAfterTap(current: ContinuumWorkArea, tapped: ContinuumWorkArea): ContinuumWorkArea =
        if (tapped == ContinuumWorkArea.MEHR) current else tapped

    fun showBottomBar(wide: Boolean, keyboardVisible: Boolean, portraitFold: Boolean = false): Boolean =
        (!wide || portraitFold) && !keyboardVisible

    fun accessibilityLabel(destination: ContinuumWorkArea): String =
        destination.title + " – " + destination.explanation
}
