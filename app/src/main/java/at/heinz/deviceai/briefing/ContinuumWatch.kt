package at.heinz.deviceai.briefing

/**
 * Truthful warnings (owner goal 19.11.2026, „Achtung …“): real conditions become ONE calm, personal sentence each –
 * once per episode of the condition, never while a job, an approval or a mission is open. No alarm is invented: every
 * sentence states a value the device reported at that moment.
 *
 * An episode starts when a condition begins to hold and ends when it no longer holds; the same condition is not
 * announced again within [REPEAT_AFTER_MILLIS] even when it flickers. A warning that could not be said because
 * something was open is said afterwards – only if the condition still holds then.
 *
 * Two conditions are changes by their nature and are only announced when they happen while the app is in use: the
 * bridge that was connected and is lost, and the storage that had room and runs low. A storage that was already
 * full when the app was opened is the system check's matter – otherwise a full phone would be told so at every start.
 */
internal class ContinuumWatch {
    enum class Condition { HEAT, BATTERY, STORAGE, INTERNET, BRIDGE }

    /** [offer]: a next step that fits the warning and that the app can really do, or null. */
    data class Warning(val condition: Condition, val text: String, val offer: ContinuumBriefingBoard.OfferKind? = null)

    private class Episode(var said: Boolean = false, var lastSaidAt: Long? = null)

    private val episodes = Condition.entries.associateWith { Episode() }
    private var bridgeSeen = false
    private var storageRoomSeen = false
    private var onlineSeen = false
    private var lostDuringWebJob = false

    /**
     * One observation. [quiet]: something is open or someone speaks – nothing is said now. [loadReduced]: the app
     * really runs with reduced load because of the heat. [webJobRunning]: a job that uses the internet runs.
     * Returns at most one warning; a second condition is reported by a later observation.
     */
    fun observe(facts: ContinuumSystemFacts, ownerName: String?, nowMillis: Long, quiet: Boolean,
                loadReduced: Boolean = false, webJobRunning: Boolean = false): Warning? {
        if (facts.shizukuReady == true) bridgeSeen = true
        if (facts.networkOnline == true) { onlineSeen = true; lostDuringWebJob = false }
        else if (facts.networkOnline == false && onlineSeen && webJobRunning) lostDuringWebJob = true
        val checks = ContinuumSystemCheck.evaluate(facts).associateBy { it.key }
        fun troubled(key: String) = checks[key]?.state.let { it == ContinuumSystemCheck.State.WARN || it == ContinuumSystemCheck.State.FAIL }
        if (facts.storageFreeBytes != null && facts.storageTotalBytes != null && !troubled(ContinuumSystemCheck.STORAGE))
            storageRoomSeen = true
        val holding = mapOf(
            Condition.HEAT to ((facts.thermalStatus ?: 0) >= ContinuumSystemCheck.THERMAL_MODERATE),
            Condition.BATTERY to (facts.batteryPercent != null && troubled(ContinuumSystemCheck.BATTERY)),
            Condition.STORAGE to (storageRoomSeen && facts.storageFreeBytes != null && facts.storageTotalBytes != null &&
                troubled(ContinuumSystemCheck.STORAGE)),
            Condition.INTERNET to lostDuringWebJob,
            Condition.BRIDGE to (bridgeSeen && facts.shizukuReady == false))
        holding.forEach { (condition, holds) -> if (!holds) episodes.getValue(condition).said = false }
        if (quiet) return null
        val due = Condition.entries.firstOrNull { condition ->
            val episode = episodes.getValue(condition)
            holding.getValue(condition) && !episode.said &&
                (episode.lastSaidAt?.let { nowMillis - it >= REPEAT_AFTER_MILLIS } ?: true)
        } ?: return null
        episodes.getValue(due).apply { said = true; lastSaidAt = nowMillis }
        val address = ownerName?.trim()?.takeIf { it.isNotEmpty() }
        fun personal(sentence: String) = if (address != null) "$address, $sentence" else sentence.replaceFirstChar { it.uppercase() }
        return when (due) {
            Condition.HEAT -> Warning(due, personal(when {
                (facts.thermalStatus ?: 0) >= ContinuumSystemCheck.THERMAL_SEVERE -> "das Gerät ist stark erwärmt. Gönn ihm bitte eine Pause."
                loadReduced -> "das Gerät wird warm – ich schalte einen Gang zurück."
                else -> "das Gerät wird warm."
            }))
            Condition.BATTERY -> Warning(due, personal(
                if (facts.batteryPercent!! <= ContinuumSystemCheck.BATTERY_CRITICAL_PERCENT)
                    "der Akku ist fast leer – nur noch ${facts.batteryPercent} Prozent."
                else "der Akku ist bei ${facts.batteryPercent} Prozent und lädt nicht."))
            Condition.STORAGE -> Warning(due, personal(
                (if (checks.getValue(ContinuumSystemCheck.STORAGE).state == ContinuumSystemCheck.State.FAIL) "der Speicher ist fast voll"
                else "der Speicher wird knapp") + ": noch ${ContinuumSystemCheck.gigabytes(facts.storageFreeBytes!!)} Gigabyte frei."),
                offer = ContinuumBriefingBoard.OfferKind.DIAGNOSIS)
            Condition.INTERNET -> Warning(due, personal("während des Abrufs ist die Internetverbindung abgebrochen. Sie ist noch nicht wieder da."))
            Condition.BRIDGE -> Warning(due, personal("die Verbindung zu Shizuku ist abgebrochen."))
        }
    }

    companion object {
        const val REPEAT_AFTER_MILLIS = 30 * 60_000L
    }
}
