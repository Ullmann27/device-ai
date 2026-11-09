package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus

/**
 * One ephemeral, session-scoped battery reading backed by an actual successful,
 * non-replayed device tool observation. Never consults the model or device on recall.
 */
internal class PreviousBatteryObservation {
    private data class Reading(val session: String, val percent: Int)
    private var last: Reading? = null

    @Synchronized fun recordIfFresh(sessionId: String, result: AgentRunResult): Boolean {
        if (sessionId.isBlank() || result.blocker != null || !result.responseComplete) return false
        val proof = result.taskEvidence ?: return false
        if (!proof.evaluatorSucceeded || proof.calls.size != 1) return false
        val call = proof.calls.single()
        if (call.toolName != "get_battery_info" || call.status != ToolExecutionStatus.SUCCEEDED ||
            !call.freshOutput || call.outcomeUncertain || call.replayed || call.outputSha256 == null) return false
        val percent = parseObservedPercent(result.response) ?: return false
        last = Reading(sessionId, percent)
        return true
    }

    @Synchronized fun answer(sessionId: String): String {
        val observed = last?.takeIf { it.session == sessionId }
            ?: return "Für diese Sitzung liegt mir kein belegter früherer Akkustand vor; ich habe keine neue Messung gestartet."
        return "Bei der letzten erfolgreichen Akkuprüfung wurden ${observed.percent} % gemessen; ich habe keine neue Messung gestartet."
    }

    companion object {
        private val directPercent = Regex("""(?m)^Aktueller Akkustand: ([0-9]{1,3}) % \(Android-Batteriedienst\)\.[ \t]*$""")
        private val rawLevel = Regex("""(?m)^[ \t]*level: ([0-9]{1,3})[ \t]*$""")
        private val rawScale = Regex("""(?m)^[ \t]*scale: ([0-9]{1,3})[ \t]*$""")

        internal fun parseObservedPercent(text: String): Int? {
            if (!text.startsWith("Akku prüfen:\n")) return null
            val output = text.substringAfter("Akku prüfen:\n")
            directPercent.find(output)?.groupValues?.get(1)?.toIntOrNull()?.let { percent ->
                return percent.takeIf { it in 0..100 }
            }
            val level = rawLevel.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val scale = rawScale.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            if (scale <= 0 || level !in 0..scale) return null
            return (level.toLong() * 100 / scale).toInt()
        }
    }
}
