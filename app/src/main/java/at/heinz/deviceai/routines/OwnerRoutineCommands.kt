package at.heinz.deviceai.routines

import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.cognitive.ReadOnlyToolPlan
import java.util.Locale

internal enum class OwnerRoutineTarget(val goal: String, val title: String, private val tool: String) {
    BATTERY("Prüfe Akku", "Akku prüfen", "get_battery_info"),
    LOW_BATTERY("Prüfe Akku und melde nur bei einem Akkustand unter 20 %", "Akku unter 20 %", "get_battery_info"),
    STORAGE("Prüfe Speicher", "Freien App-Speicher prüfen", "get_storage_info"),
    NETWORK("Prüfe Netzwerk", "Netzwerkstatus prüfen", "get_network_summary"),
    DEVICE("Prüfe Geräteinformationen", "Geräteinformationen prüfen", "get_device_info"),
    APPS("Prüfe installierte Nutzer-Apps", "Installierte Nutzer-Apps prüfen", "list_installed_apps"),
    DOWNLOADS("Prüfe Downloads", "Downloads auflisten", "list_files"),
    STORAGE_DOWNLOADS("Prüfe Speicher und Downloads", "Speicher und Downloads prüfen", "list_files");

    val requiresShizuku: Boolean get() = tool == "list_files"
    fun plan(): ReadOnlyToolPlan = ReadOnlyToolPlan.forRequest(goal, ToolProposal(tool,
        if (requiresShizuku) mapOf("path" to "/sdcard/Download") else emptyMap()))
    val allowedTools: Set<String> get() = plan().plannedToolNames.toSet()

    fun digest(result: String): String {
        if (this != STORAGE && this != STORAGE_DOWNLOADS) return result
        val timestamp = Regex("^Zeitpunkt: [0-9]+$")
        val lines = result.lines()
        require(lines.count(timestamp::matches) == 1) { "Storage observation timestamp missing." }
        return lines.filterNot(timestamp::matches).joinToString("\n")
    }

    fun reports(result: String): Boolean {
        if (this != LOW_BATTERY) return true
        val readings = Regex("(?m)^Aktueller Akkustand: ([0-9]{1,3}) % \\(Android-Batteriedienst\\)\\.$")
            .findAll(result).toList()
        require(readings.size == 1) { "Verified battery percentage required." }
        val percent = readings.single().groupValues[1].toInt()
        require(percent in 0..100) { "Battery percentage out of range." }
        return percent < 20
    }

    companion object {
        fun from(routine: Routine): OwnerRoutineTarget? = entries.singleOrNull {
            it.goal == routine.goal && it.allowedTools == routine.allowedTools && when (val trigger = routine.trigger) {
                is RoutineTrigger.Every -> trigger.periodMillis >= RoutineBook.MIN_PERIOD_MILLIS && trigger.firstAtMillis >= 0
                is RoutineTrigger.OnEvent -> trigger.name == OwnerRoutineDraft.APP_START
            }
        }

        fun parse(text: String): OwnerRoutineTarget? {
            val request = normalize(text)
            entries.firstOrNull { normalize(it.goal) == request || normalize(it.title) == request }?.let { return it }
            return when (request) {
                "akku", "prüfe den akku" -> BATTERY
                "akku < 20 %", "akku <20%", "akku unter20%", "melde nur wenn akku unter20%",
                "melde nur wenn akku unter 20 %" -> LOW_BATTERY
                "speicher", "speicher prüfen", "prüfe den freien speicher" -> STORAGE
                "netzwerk", "netzwerk prüfen" -> NETWORK
                "gerät", "geräteinformationen" -> DEVICE
                "apps", "prüfe apps", "apps prüfen" -> APPS
                "downloads", "downloads prüfen" -> DOWNLOADS
                "speicher und downloads" -> STORAGE_DOWNLOADS
                else -> null
            }
        }

        private fun normalize(text: String): String =
            text.trim().removeSuffix(".").lowercase(Locale.GERMAN).replace(Regex("\\s+"), " ")
    }
}

internal data class OwnerRoutineDraft(val target: OwnerRoutineTarget, val periodMillis: Long?) {
    init { require(periodMillis == null || periodMillis >= RoutineBook.MIN_PERIOD_MILLIS) }
    fun routine(id: String, nowMillis: Long): Routine {
        require(nowMillis >= 0)
        return Routine(id, target.goal, periodMillis?.let { RoutineTrigger.Every(it, nowMillis) }
            ?: RoutineTrigger.OnEvent(APP_START), target.allowedTools)
    }
    companion object { const val APP_START = "owner-app-start" }
}

internal sealed interface OwnerRoutineCommand {
    data object ListRoutines : OwnerRoutineCommand
    data object Choose : OwnerRoutineCommand
    data object StopAll : OwnerRoutineCommand
    data class Stop(val id: String) : OwnerRoutineCommand
    data class Create(val draft: OwnerRoutineDraft) : OwnerRoutineCommand
    data class Unsupported(val reason: String) : OwnerRoutineCommand
}

internal object OwnerRoutineCommands {
    fun parse(text: String): OwnerRoutineCommand? {
        val input = text.trim()
        if (input.lowercase(Locale.GERMAN) in setOf("routinen", "routinen anzeigen", "zeige routinen"))
            return OwnerRoutineCommand.ListRoutines
        if (!Regex("(?iu)^routinen?\\b").containsMatchIn(input) &&
            !input.equals("alle Routinen stoppen", true)) return null
        if (input.equals("Routine einrichten", true)) return OwnerRoutineCommand.Choose
        if (input.equals("alle Routinen stoppen", true) || input.equals("Routinen stoppen", true))
            return OwnerRoutineCommand.StopAll
        Regex("(?iu)^routine stoppen ([a-z0-9][a-z0-9._-]{0,63})$").matchEntire(input)?.let {
            return OwnerRoutineCommand.Stop(it.groupValues[1].lowercase(Locale.ROOT))
        }
        val timed = Regex("(?iu)^routine alle ([0-9]{1,4}) (minuten|stunden):\\s*(.+)$").matchEntire(input)
        val start = Regex("(?iu)^routine beim appstart:\\s*(.+)$").matchEntire(input)
        val request = timed?.groupValues?.get(3) ?: start?.groupValues?.get(1)
            ?: return OwnerRoutineCommand.Unsupported("Nutze ‚Routine einrichten‘, ‚Routinen‘ oder ‚Routine stoppen <ID>‘.")
        val target = OwnerRoutineTarget.parse(request) ?: return OwnerRoutineCommand.Unsupported(
            "Dieses Routineziel oder diese Bedingung ist nicht unterstützt. Es wurde nichts eingerichtet.")
        val period = timed?.let {
            it.groupValues[1].toLong() * if (it.groupValues[2].equals("Stunden", true)) 3_600_000L else 60_000L
        }
        if (period != null && period < RoutineBook.MIN_PERIOD_MILLIS) return OwnerRoutineCommand.Unsupported(
            "Android-Routinen benötigen mindestens 15 Minuten Abstand. Es wurde nichts eingerichtet.")
        return OwnerRoutineCommand.Create(OwnerRoutineDraft(target, period))
    }
}
