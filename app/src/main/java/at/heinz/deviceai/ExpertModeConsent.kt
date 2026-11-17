package at.heinz.deviceai

/**
 * Confirmation copy for the expert-mode shell planner ([MainActivity]).
 * Destructive wording matches [at.heinz.deviceai.AgentActivity] showStructuredToolConfirmation
 * so both owner paths ask the same two questions before a destructive command runs.
 * The exact shell command is always part of the dialog text.
 */
internal object ExpertModeConsent {
    const val DESTRUCTIVE_TITLE = "Kritische Aktion bestätigen"
    const val DESTRUCTIVE_CONTINUE = "Weiter prüfen"
    const val STRONG_TITLE = "Starke Bestätigung erforderlich"
    const val STRONG_PREFIX = "Diese Aktion kann Daten verändern oder löschen."
    const val STRONG_CONFIRM = "Destruktiv ausführen"

    fun stepBlock(step: CommandStep): String = "• ${step.label}\n${step.shellCommand}"

    fun plannedActions(plan: CommandPlan): String =
        plan.steps.joinToString("\n\n", transform = ::stepBlock)

    fun confirmMessage(plan: CommandPlan): String =
        "Geplante Aktionen:\n\n${plannedActions(plan)}\n\nAusführen?"

    fun destructiveFirstMessage(plan: CommandPlan): String =
        "Dieser Auftrag enthält eine destruktive Aktion:\n\n${plannedActions(plan)}\n\n" +
            "Nur fortfahren, wenn du das wirklich willst."

    fun destructiveStrongMessage(plan: CommandPlan): String =
        "$STRONG_PREFIX\n\n${destructiveFirstMessage(plan)}"
}
