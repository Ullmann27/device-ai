package at.heinz.deviceai.ui

import at.heinz.deviceai.localmodel.LocalModelLoadAdmission
import at.heinz.deviceai.localmodel.LocalModelResourceSnapshot
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Row
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Tone

/**
 * "Local Model Status" panel (ref5, §11). Every value is mapped from an existing source; nothing is estimated:
 *  - Modell / Zustand / Antwortzeit  <- ContinuumDashboardState.model (runtime.loadedModel id·version, stateLabel, latencyMs)
 *  - RAM frei / Thermik / Ladefreigabe <- LocalModelResourceSnapshot + LocalModelLoadAdmission.refusal (measured at load)
 *  - Akku-Temperatur                 <- ContinuumDashboardState.system.temperatureC (dashboard collector)
 *  - Agenten                         <- CockpitStatus.workers/workerCapacity (ChiefWorkerPool.snapshot)
 * A missing source renders [NOT_AVAILABLE] / [SOURCE_MISSING]. Left out on purpose (no allowed source):
 * VRAM/GPU memory, SoC/CPU temperature, tokens/s, "Mode Local (Private)".
 * Placement in the grid is Kern's (#481).
 */
object LocalModelStatusPresentation {
    const val NOT_AVAILABLE = ContinuumCockpitPresentation.NOT_AVAILABLE
    const val SOURCE_MISSING = "Quelle fehlt"
    private val THERMAL = arrayOf("normal", "leicht", "mittel", "stark", "kritisch", "Notfall", "Abschaltung")

    /** [resources] = last load-time measurement; null = never measured in this session (no state field yet). */
    fun rows(s: ContinuumDashboardState, resources: LocalModelResourceSnapshot? = null,
             admissionRefusal: String? = null): List<Row> {
        val m = s.model
        val rows = mutableListOf(
            Row("Modell", if (m.online) m.name else NOT_AVAILABLE, null, if (m.online) Tone.OK else Tone.NEUTRAL),
            Row("Zustand", m.stateLabel, null),
            Row("Antwortzeit", m.latencyMs?.let { "$it ms" } ?: NOT_AVAILABLE, null)
        )
        if (resources == null) {
            rows += Row("RAM frei", SOURCE_MISSING, null)
            rows += Row("Thermik", SOURCE_MISSING, null)
            rows += Row("Ladefreigabe", SOURCE_MISSING, null)
        } else {
            val freeFrac = resources.availableRamBytes.toFloat() / resources.totalRamBytes
            rows += Row("RAM frei", "%.1f / %.1f GB".format(java.util.Locale.ROOT, gb(resources.availableRamBytes), gb(resources.totalRamBytes)),
                freeFrac.coerceIn(0f, 1f), if (resources.systemLowMemory) Tone.WARN else Tone.NEUTRAL)
            rows += Row("Thermik", resources.thermalStatus?.let { THERMAL[it] } ?: NOT_AVAILABLE, null,
                if ((resources.thermalStatus ?: 0) >= 3) Tone.WARN else Tone.NEUTRAL)
            rows += Row("Ladefreigabe", if (admissionRefusal == null) "frei" else LocalModelLoadAdmission.ownerMessage(admissionRefusal), null,
                if (admissionRefusal == null) Tone.OK else Tone.WARN)
        }
        rows += Row("Akku-Temp.", s.system.temperatureC?.let { "%.1f °C".format(java.util.Locale.ROOT, it) } ?: NOT_AVAILABLE, null)
        val w = s.cockpit.workers
        rows += Row("Agenten", if (w == null) NOT_AVAILABLE
            else "${w.count { it.running }} aktiv" + (s.cockpit.workerCapacity?.let { " / $it" } ?: ""), null)
        return rows
    }

    private fun gb(b: Long) = b / 1_073_741_824.0
}

/** Cockpit card wrapper; resources stay [LocalModelStatusPresentation.SOURCE_MISSING] until Kern adds a state field. */
class CockpitLocalModelCard(textScale: Float) : CockpitCard(textScale, ContinuumIcons.Icon.BRAIN) {
    override fun title(s: ContinuumDashboardState) = "Local Model Status"
    override fun rows(s: ContinuumDashboardState) = LocalModelStatusPresentation.rows(s)
}
