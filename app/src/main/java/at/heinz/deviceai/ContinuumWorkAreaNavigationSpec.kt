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
    LERNEN("KI-System", "✧", "Gedächtnis, Logik, Lernen, Modell und Gehirnkugel öffnen"),
    ENTWICKELN("Werkzeuge", "⌘", "Agenten, Aufgaben, Recherche, Web und Gerätesteuerung öffnen"),
    MEHR("Mehr", "⋯", "Systemstatus, Diagnose, Datenschutz und Einstellungen öffnen")
}

object ContinuumWorkAreaNavigationSpec {
    val destinations: List<ContinuumWorkArea> = listOf(
        ContinuumWorkArea.START,
        ContinuumWorkArea.CHAT,
        ContinuumWorkArea.LERNEN,
        ContinuumWorkArea.ENTWICKELN,
        ContinuumWorkArea.MEHR
    )

    /** Stable route keys keep compact categories connected to the same full dashboard destinations. */
    data class CategoryEntry(val title: String, val target: String)

    val systemEntries = listOf(
        CategoryEntry("Gedächtnis", "memory"), CategoryEntry("Logik", "logic"),
        CategoryEntry("Lernen", "learning"), CategoryEntry("Lokales Modell", "model-management"),
        CategoryEntry("Continuum Brain", "brain"))
    val toolEntries = listOf(
        CategoryEntry("Mein KI-Team", "agents"), CategoryEntry("Aufgaben", "tasks"),
        CategoryEntry("Recherche", "research"), CategoryEntry("Web", "web"),
        CategoryEntry("Gerätesteuerung", "device"))
    val moreEntries = listOf(
        CategoryEntry("Denken und Planen", "logic"), CategoryEntry("Mein KI-Team", "agents"),
        CategoryEntry("Systemstatus", "system"), CategoryEntry("Diagnose", "notifications"),
        CategoryEntry("Einstellungen", "settings"), CategoryEntry("Datenschutz & Freigaben", "privacy"),
        CategoryEntry("Kamera und Wahrnehmung", "camera-settings"),
        CategoryEntry("Modellverwaltung", "model-management"),
        CategoryEntry("Entwicklerwerkzeuge", "development"), CategoryEntry("Handy-Builder · Termux", "mobile-builder"))

    /** Readable quick actions on compact displays and at large system font scales. */
    fun quickActionColumns(usableWidthDp: Int, fontScale: Float): Int =
        if (usableWidthDp >= 320 && fontScale.isFinite() && fontScale in 0.5f..1.25f) 2 else 1

    /** "More" is a transient dialog, never a lasting screen selection. */
    fun selectionAfterTap(current: ContinuumWorkArea, tapped: ContinuumWorkArea): ContinuumWorkArea =
        if (tapped == ContinuumWorkArea.MEHR) current else tapped

    fun showBottomBar(wide: Boolean, keyboardVisible: Boolean, portraitFold: Boolean = false): Boolean =
        // Open Fold7 (portrait and landscape) always uses the labelled side rail; the bottom
        // bar is only for the cover display and phones. portraitFold is kept for callers.
        !wide && !keyboardVisible

    fun accessibilityLabel(destination: ContinuumWorkArea): String =
        destination.title + " – " + destination.explanation
}
