package at.heinz.deviceai

/** Local chat command decoder. No network, shell, Git or device tools involved. */
sealed class ContinuumWorkerChatCommand {
    data object Status : ContinuumWorkerChatCommand()
    data object Help : ContinuumWorkerChatCommand()
    data object Backlog : ContinuumWorkerChatCommand()
    data object DevelopmentCenter : ContinuumWorkerChatCommand()
    data object MobileBuilder : ContinuumWorkerChatCommand()
    data class Draft(val goal: String) : ContinuumWorkerChatCommand()
}

object ContinuumWorkerChatRouter {
    private val draftPattern = Regex(
        """^(?:worker|baumeister|coding[ -]?agent|programmierer)\s*:\s*(.{1,400})$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    // Voice recognition frequently omits the colon, so accept only an
    // explicitly addressed, development-specific imperative.
    private val spokenDraftPattern = Regex(
        """^(?:worker|baumeister|coding[ -]?agent|programmierer)[, ]+""" +
            """(?:bitte\s+)?(?:erstelle|baue|entwickle|programmiere)\s+(.{1,400})$""",
        RegexOption.IGNORE_CASE
    )
    fun parse(raw: String): ContinuumWorkerChatCommand? {
        val text = raw.trim().replace(Regex("""\s+"""), " ")
        return when (text.lowercase(java.util.Locale.ROOT).trimEnd('?', '.', '!')) {
            "worker", "baumeister", "coding agent", "coding-agent", "programmierer",
            "worker hilfe", "baumeister hilfe", "coding agent hilfe" ->
                ContinuumWorkerChatCommand.Help
            "worker status", "baumeister status", "coding agent status",
            "worker prüfen", "baumeister prüfen", "coding agent prüfen" ->
                ContinuumWorkerChatCommand.Status
            "worker aufgaben", "baumeister aufgaben", "coding agent aufgaben" ->
                ContinuumWorkerChatCommand.Backlog
            "worker entwickeln", "baumeister entwickeln", "worker werkstatt",
            "coding agent entwickeln", "coding agent werkstatt" ->
                ContinuumWorkerChatCommand.DevelopmentCenter
            "worker builder", "baumeister builder", "worker termux", "baumeister termux",
            "worker handy builder", "baumeister handy builder" ->
                ContinuumWorkerChatCommand.MobileBuilder
            else -> {
                val match = draftPattern.matchEntire(text) ?: spokenDraftPattern.matchEntire(text)
                    ?: return null
                val goal = match.groupValues[1].trim()
                if (goal.isBlank() || goal.any { it.code < 0x20 }) null
                else ContinuumWorkerChatCommand.Draft(goal)
            }
        }
    }
}
