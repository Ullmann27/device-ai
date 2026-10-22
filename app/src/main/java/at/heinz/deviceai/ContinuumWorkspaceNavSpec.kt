package at.heinz.deviceai

/**
 * Work-area navigation: every area of the app is at most two taps away from the start or chat screen.
 * Phones show a fixed bottom bar (plus "Mehr" for the rest); wide screens list the same areas in the sidebar.
 * Destinations open existing owner-controlled screens only, never shell commands, training runs or installations.
 */
internal enum class ContinuumWorkArea {
    START, CHAT, RESEARCH, LEARNING, MODELS, DEVELOPMENT, MEMORY, TASKS, BUILDER, SYSTEM, FIRST_STEPS
}

internal data class ContinuumWorkAreaItem(
    val area: ContinuumWorkArea,
    val symbol: String,
    /** Short label for the bottom bar and the home grid. */
    val label: String,
    val description: String
)

internal object ContinuumWorkspaceNavSpec {
    const val MORE_SYMBOL = "☰"
    const val MORE_LABEL = "Mehr"
    const val MAX_BAR_LABEL = 10

    val all = listOf(
        ContinuumWorkAreaItem(ContinuumWorkArea.START, "⌂", "Start", "Überblick, Status und alle Bereiche"),
        ContinuumWorkAreaItem(ContinuumWorkArea.CHAT, "●", "Chat", "Fragen, Ideen, Probleme lösen"),
        ContinuumWorkAreaItem(ContinuumWorkArea.RESEARCH, "⌕", "Forschung", "Recherchieren und Quellen prüfen"),
        ContinuumWorkAreaItem(ContinuumWorkArea.LEARNING, "▤", "Lernen", "Wissen aufbauen und verstehen"),
        ContinuumWorkAreaItem(ContinuumWorkArea.MODELS, "⬡", "Modelle", "Lokale KI-Modelle verwalten"),
        ContinuumWorkAreaItem(ContinuumWorkArea.DEVELOPMENT, "⌘", "Entwickeln", "Code und Aufgaben kontrollieren"),
        ContinuumWorkAreaItem(ContinuumWorkArea.MEMORY, "▱", "Speicher", "Erinnerungen und persönlicher Kontext"),
        ContinuumWorkAreaItem(ContinuumWorkArea.TASKS, "✓", "Aufgaben", "Entwicklungsaufträge und Ergebnisse verfolgen"),
        ContinuumWorkAreaItem(ContinuumWorkArea.BUILDER, "⚒", "Builder", "Apps auf dem Handy bauen (Termux)"),
        ContinuumWorkAreaItem(ContinuumWorkArea.SYSTEM, "⚙", "System", "Geräteinfos, Berechtigungen und Sicherheit"),
        ContinuumWorkAreaItem(ContinuumWorkArea.FIRST_STEPS, "★", "Hilfe", "Erste Schritte: deine KI einfach erklärt")
    )

    fun item(area: ContinuumWorkArea): ContinuumWorkAreaItem = all.first { it.area == area }

    /** Fixed phone bar, followed by the "Mehr" entry that lists [more]. */
    val bottomBar = listOf(ContinuumWorkArea.START, ContinuumWorkArea.CHAT,
        ContinuumWorkArea.LEARNING, ContinuumWorkArea.DEVELOPMENT)

    /** Second tap: everything that is not in the bar. */
    val more: List<ContinuumWorkArea> = all.map { it.area }.filter { it !in bottomBar }

    /** Home grid: the six work areas, directly visible (no longer hidden behind a toggle). */
    val homeGrid = listOf(ContinuumWorkArea.CHAT, ContinuumWorkArea.RESEARCH, ContinuumWorkArea.LEARNING,
        ContinuumWorkArea.MODELS, ContinuumWorkArea.DEVELOPMENT, ContinuumWorkArea.MEMORY)

    /** Three compact tiles per row on phones; fewer for narrow screens or enlarged fonts. */
    fun gridColumns(usableWidthDp: Int, fontScale: Float): Int = when {
        !fontScale.isFinite() || usableWidthDp < 240 -> 1
        fontScale > 1.3f || usableWidthDp < 330 -> 2
        else -> 3
    }

    /** Highlighted bar entry: the screen underneath any open area dialog. */
    fun selected(chatActive: Boolean): ContinuumWorkArea =
        if (chatActive) ContinuumWorkArea.CHAT else ContinuumWorkArea.START

    /** The bar gives way to the keyboard so the composer stays usable. */
    fun barVisible(keyboardVisible: Boolean): Boolean = !keyboardVisible
}
