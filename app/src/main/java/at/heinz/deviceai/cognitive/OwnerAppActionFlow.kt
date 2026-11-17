package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolExecutionResult
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolRegistry
import org.json.JSONObject
import java.util.UUID

/** Selection never grants execution. */
internal class OwnerAppActionFlow(
    private val opening: Boolean,
    private val requireExactInstalledCandidate: Boolean = false
) {
    sealed interface Step {
        data class Run(val call: ToolCallRequest, val confirmed: Boolean = false) : Step
        data class Choose(val candidates: List<NativeAppCandidate>) : Step
        data class Confirm(val call: ToolCallRequest, val candidate: NativeAppCandidate) : Step
        data class Done(val message: String) : Step
    }

    private enum class Phase { NEW, LOOKUP, CHOOSE, CONFIRM, LAUNCH, DONE }
    private var phase = Phase.NEW
    private var expected: ToolCallRequest? = null
    private var selected: NativeAppCandidate? = null
    var candidates: List<NativeAppCandidate> = emptyList()
        private set
    var lookupVerified: Boolean = false
        private set
    var exactMatchVerified: Boolean = false
        private set
    var lookupFinishedWithoutInteraction: Boolean = false
        private set
    var launchedCandidate: NativeAppCandidate? = null
        private set
    val canRetainAppLookupContext: Boolean get() = lookupVerified && candidates.isNotEmpty() &&
        (!requireExactInstalledCandidate || exactMatchVerified)
    val launching: Boolean get() = phase == Phase.LAUNCH

    fun start(query: String): Step.Run {
        check(phase == Phase.NEW)
        phase = Phase.LOOKUP
        return Step.Run(ToolCallRequest("owner-app-" + UUID.randomUUID(), "find_app",
            mapOf("query" to query)).also { expected = it })
    }

    fun returned(result: ToolExecutionResult): Step? {
        val call = expected ?: return null
        if (result.record.callId != call.callId || result.record.toolName != call.name ||
            phase !in setOf(Phase.LOOKUP, Phase.LAUNCH)) return null
        if (phase == Phase.LAUNCH) return finishLaunch(result)
        if (!freshSuccess(result)) return done("Appsuche nicht bestätigt. Es wurde keine App gestartet.")
        val matches = runCatching {
            val obj = JSONObject(result.output ?: error("NO_APP_RESULT"))
            check(obj.optString("schema") == "CONTINUUM_APP_DISCOVERY_V1" && obj.optBoolean("readOnly"))
            val total = obj.getInt("totalMatches")
            val items = obj.getJSONArray("candidates")
            check(total >= items.length() && items.length() in 0..12)
            val supplied = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                NativeAppCandidate(item.getString("packageName"), item.getString("label"))
            }
            check(supplied.map { it.packageName }.distinct().size == supplied.size)
            val checked = NativeAppLookup.search(call.arguments.getValue("query") as String, supplied)
            check(checked.candidates.size == supplied.size)
            checked.copy(total = total)
        }.getOrElse { return done("Appsuche konnte nicht vollständig geprüft werden. Keine App gestartet.") }
        lookupVerified = true
        exactMatchVerified = matches.exactLabel
        candidates = matches.candidates
        if (candidates.isEmpty()) return done("Ich habe keine passende startbare App gefunden. " +
            "Das beweist nicht, dass sie nicht installiert ist. Welche App meinst du genau?")
        if (requireExactInstalledCandidate && !matches.exactLabel) return done(
            "Kein exakter installierter App-Treffer bestätigt. Keine App gestartet."
        )
        if (!opening) return done((if (candidates.size == 1) "Gefundene App: " else "Gefundene Apps: ") + candidates.joinToString("; ") {
            "${it.label} (${it.packageName})"
        } + ". Es wurde nichts geöffnet.")
        phase = Phase.CHOOSE
        // Code55 field: one exact, launchable hit (e.g. WhatsApp) must not ask „which one?“ with a single entry.
        // The concrete launch approval (Step.Confirm) still follows.
        return if (candidates.size == 1 && (matches.total == 1 || matches.exactLabel)) choose(candidates.single())
            else Step.Choose(candidates)
    }

    fun choose(candidate: NativeAppCandidate?): Step? {
        if (phase != Phase.CHOOSE) return null
        if (candidate == null) return done("App-Auswahl abgebrochen. Keine App gestartet.")
        if (candidate !in candidates) return null
        selected = candidate
        val call = ToolCallRequest("owner-app-" + UUID.randomUUID(), "launch_app",
            mapOf("packageOrName" to candidate.packageName))
        check(ToolRegistry.validate(call) == null)
        expected = call
        phase = Phase.CONFIRM
        return Step.Confirm(call, candidate)
    }

    fun confirm(approved: Boolean): Step.Run? {
        if (phase != Phase.CONFIRM) return null
        phase = Phase.LAUNCH
        return Step.Run(checkNotNull(expected), approved)
    }

    fun cancel() { phase = Phase.DONE; expected = null }

    private fun finishLaunch(result: ToolExecutionResult): Step.Done {
        val app = checkNotNull(selected)
        val verified = freshSuccess(result) && runCatching {
            val obj = JSONObject(result.output ?: error("NO_LAUNCH_RESULT"))
            obj.optString("schema") == "CONTINUUM_DEVICE_ACTION_V1" &&
                obj.optString("action") == "LAUNCH_APP" && obj.optString("package") == app.packageName &&
                obj.optString("status") == "VERIFIED_FOREGROUND" && obj.optLong("windowRevision") > 0L
        }.getOrDefault(false)
        if (verified) launchedCandidate = app
        return done(when {
            verified -> "${app.label} wurde in einem neuen aktiven Fenster beobachtet. " +
                "Der App-Inhalt wurde dabei nicht geprüft."
            result.record.outcomeUncertain -> "Das Öffnen von ${app.label} ist nicht bestätigt. " +
                "Eine bereits gestartete Aktion kann trotzdem gewirkt haben; bitte prüfe das Gerät."
            result.record.status == ToolExecutionStatus.REJECTED -> "${app.label} wurde nicht geöffnet: " +
                "Die konkrete Werkzeugfreigabe fehlt oder wurde abgelehnt."
            result.record.decisionReason.contains("OBSERVATION_NOT_GRANTED") ->
                "${app.label} wurde nicht bestätigt geöffnet. Für den vorhandenen Öffnen-und-Prüfen-Weg " +
                    "muss die Bildschirmbeobachtung in Android freigegeben sein."
            result.record.status == ToolExecutionStatus.CANCELLED ->
                "App-Auftrag abgebrochen. Es liegt keine bestätigte Öffnung vor."
            else -> "Das Öffnen von ${app.label} konnte nicht bestätigt werden. " +
                "Werkzeugstatus: ${result.record.status}."
        })
    }

    private fun freshSuccess(result: ToolExecutionResult): Boolean =
        result.record.status == ToolExecutionStatus.SUCCEEDED && !result.replayed &&
            !result.record.outcomeUncertain && !result.journalUnavailableBeforeDispatch

    private fun done(message: String): Step.Done {
        lookupFinishedWithoutInteraction = phase == Phase.LOOKUP
        phase = Phase.DONE
        expected = null
        return Step.Done(message)
    }
}
