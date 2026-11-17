package at.heinz.deviceai.systemintelligence

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Data-sparse Fold7 heat measurement by phase (owner order 12.11.2026, priority 4). Records only numbers the device
 * itself reports – Android thermal status 0..6, optional thermal headroom, battery temperature, model run time – and
 * never prompts, answers, file names or audio. It does not change any limit: the existing LocalModelThermalGuard
 * stays the only safety stop. A long inference phase is refused when Android already reports MODERATE (2) or worse,
 * so the measurement itself cannot push a warm phone further. Bounded: at most [MAX_SAMPLES] samples per session.
 */
class ThermalPhaseRecorder(private val clock: () -> Long) {
    enum class Phase { IDLE_APP_OPEN, MODEL_LOAD, SHORT_INFERENCE, LONG_INFERENCE, MAYA_SPEECH, CINEMA_ONLY, SPEECH_AND_CINEMA, AFTER_STOP_REST, DIAGNOSTIC_SNAPSHOT }

    data class Sample(val atMs: Long, val phase: Phase, val thermalStatus: Int?, val headroom: Float?,
                      val batteryTenthsC: Int?, val modelRunMs: Long?)

    sealed class Start {
        object Started : Start()
        data class Refused(val reason: String) : Start()
    }

    private val samples = ArrayList<Sample>()
    private val phaseStarts = LinkedHashMap<Phase, Long>()
    private var lastDiagnosisAtMillis: Long? = null
    var active: Phase? = null
        private set

    /** Starts [phase]. A long inference needs a known thermal status below MODERATE; nothing else is ever blocked here. */
    fun start(phase: Phase, thermalStatus: Int?): Start {
        if (phase == Phase.LONG_INFERENCE && (thermalStatus == null || thermalStatus >= 2))
            return Start.Refused(if (thermalStatus == null) "Thermalstatus unbekannt – lange Inferenz wird nicht gemessen."
                else "Android meldet Thermalstatus $thermalStatus – lange Inferenz erst nach Abkühlung.")
        active = phase
        phaseStarts[phase] = clock()
        return Start.Started
    }

    /** Adds one device-reported sample to the active phase; invalid or out-of-range values are stored as unknown. */
    fun sample(thermalStatus: Int?, headroom: Float?, batteryTenthsC: Int?, modelRunMs: Long? = null): Boolean {
        val phase = active ?: return false
        if (samples.size >= MAX_SAMPLES) return false
        samples += Sample(clock(), phase,
            thermalStatus?.takeIf { it in 0..6 },
            headroom?.takeIf { it.isFinite() && it >= 0f },
            batteryTenthsC?.takeIf { it in -200..800 },
            modelRunMs?.takeIf { it >= 0L })
        return true
    }

    /** Accepts fresh diagnosis samples only. */
    fun sampleDiagnosis(snapshot: SystemDiagnosis, requestedAtMillis: Long, completedAtMillis: Long): Boolean {
        if (active != Phase.DIAGNOSTIC_SNAPSHOT || requestedAtMillis < 0 ||
            completedAtMillis < requestedAtMillis || snapshot.observedAtMillis !in requestedAtMillis..completedAtMillis ||
            lastDiagnosisAtMillis?.let { snapshot.observedAtMillis <= it } == true) return false
        val battery = snapshot.batteryTemperatureC?.takeIf { it.isFinite() && it in -20.0..80.0 }
            ?.let { (it * 10).roundToInt() }
        val accepted = sample(snapshot.thermalStatus, null, battery)
        if (accepted) lastDiagnosisAtMillis = snapshot.observedAtMillis
        if (samples.size >= MAX_SAMPLES) stop()
        return accepted
    }

    fun diagnosticReportGerman(): String {
        val own = samples.filter { it.phase == Phase.DIAGNOSTIC_SNAPSHOT }
        val battery = own.mapNotNull { it.batteryTenthsC }
        val state = if (active == Phase.DIAGNOSTIC_SNAPSHOT) "aktiv" else "gestoppt"
        return "Manuelle Diagnose-Messreihe · $state\nMesspunkte: ${own.size}/$MAX_SAMPLES" +
            "\nThermalstatus (höchster): ${own.mapNotNull { it.thermalStatus }.maxOrNull() ?: "unbekannt"}" +
            "\nLetzte Akkutemperatur: ${own.lastOrNull()?.batteryTenthsC?.let { "${it / 10.0} °C" } ?: "unbekannt"}" +
            "\nAkkuänderung: ${if (battery.size >= 2) "${(battery.last() - battery.first()) / 10.0} °C" else "unbekannt"}" +
            "\nNur bei einer erneut angeforderten Diagnose; keine Hintergrundmessung." +
            "\nGPU, CPU-Temperatur, Headroom und Modellzeiten: nicht gemessen."
    }

    fun stop() { active = null }

    fun samples(): List<Sample> = samples.toList()

    /** Per phase: count, max thermal status, battery delta in tenths °C (last − first), max model run time. */
    fun summary(): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("note", "Nur gerätegemeldete Zahlen; keine Inhalte. Akkutemperatur ist keine CPU-/GPU-Temperatur.")
        val phases = JSONArray()
        for (phase in Phase.values()) {
            val own = samples.filter { it.phase == phase }
            if (own.isEmpty()) continue
            val battery = own.mapNotNull { it.batteryTenthsC }
            phases.put(JSONObject().apply {
                put("phase", phase.name)
                put("samples", own.size)
                put("maxThermalStatus", own.mapNotNull { it.thermalStatus }.maxOrNull() ?: JSONObject.NULL)
                put("minHeadroom", own.mapNotNull { it.headroom }.minOrNull()?.toDouble() ?: JSONObject.NULL)
                put("batteryDeltaTenthsC", if (battery.size >= 2) battery.last() - battery.first() else JSONObject.NULL)
                put("maxModelRunMs", own.mapNotNull { it.modelRunMs }.maxOrNull() ?: JSONObject.NULL)
            })
        }
        put("phases", phases)
        put("gpuMeasured", false)
    }

    companion object { const val MAX_SAMPLES = 2_000 }
}
