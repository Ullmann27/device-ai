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
        """^(?:worker|baumeister)\s*:\s*(.{1,400})$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    fun parse(raw: String): ContinuumWorkerChatCommand? {
        val text = raw.trim().replace(Regex("""\s+"""), " ")
        return when (text.lowercase(java.util.Locale.ROOT).trimEnd('?', '.', '!')) {
            "worker", "baumeister", "worker hilfe", "baumeister hilfe" ->
                ContinuumWorkerChatCommand.Help
            "worker status", "baumeister status", "worker prüfen", "baumeister prüfen" ->
                ContinuumWorkerChatCommand.Status
            "worker aufgaben", "baumeister aufgaben" ->
                ContinuumWorkerChatCommand.Backlog
            "worker entwickeln", "baumeister entwickeln", "worker werkstatt" ->
                ContinuumWorkerChatCommand.DevelopmentCenter
            "worker builder", "baumeister builder", "worker termux", "baumeister termux",
            "worker handy builder", "baumeister handy builder" ->
                ContinuumWorkerChatCommand.MobileBuilder
            else -> {
                val match = draftPattern.matchEntire(text) ?: return null
                val goal = match.groupValues[1].trim()
                if (goal.isBlank() || goal.any { it.code < 0x20 }) null
                else ContinuumWorkerChatCommand.Draft(goal)
            }
        }
    }
}
