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
    /** ref5 placeholder for any value without a source (never an invented number). */
    const val MISSING = "—"
    private val THERMAL = arrayOf("Normal", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown")

    /** Online/Offline pill from the collector's ConnectivityManager state (ACCESS_NETWORK_STATE, already declared). */
    fun connectivity(s: ContinuumDashboardState): Pair<String, Tone> = when (s.system.networkOnline) {
        true -> "Online" to Tone.OK; false -> "Offline" to Tone.WARN; null -> MISSING to Tone.NEUTRAL
    }

    /** [resources] = last load-time measurement; null = no state field yet -> [MISSING]. */
    fun rows(s: ContinuumDashboardState, resources: LocalModelResourceSnapshot? = null,
             admissionRefusal: String? = null): List<Row> {
        val m = s.model
        val rows = mutableListOf(
            Row("Model", if (m.online) m.name else MISSING, null, if (m.online) Tone.OK else Tone.NEUTRAL),
            Row("Status", m.stateLabel, null, if (m.online) Tone.OK else Tone.NEUTRAL),
            Row("Latency", m.latencyMs?.let { "$it ms" } ?: MISSING, null)
        )
        if (resources == null) {
            rows += Row("RAM", MISSING, null); rows += Row("Thermal", MISSING, null); rows += Row("Load", MISSING, null)
        } else {
            rows += Row("RAM", "%.1f / %.1f GB".format(java.util.Locale.ROOT, gb(resources.totalRamBytes - resources.availableRamBytes), gb(resources.totalRamBytes)),
                (1f - resources.availableRamBytes.toFloat() / resources.totalRamBytes).coerceIn(0f, 1f),
                if (resources.systemLowMemory) Tone.WARN else Tone.NEUTRAL)
            rows += Row("Thermal", resources.thermalStatus?.let { THERMAL[it] } ?: MISSING, null,
                if ((resources.thermalStatus ?: 0) >= 3) Tone.WARN else Tone.NEUTRAL)
            rows += Row("Load", if (admissionRefusal == null) "Admitted" else LocalModelLoadAdmission.ownerMessage(admissionRefusal), null,
                if (admissionRefusal == null) Tone.OK else Tone.WARN)
        }
        rows += Row("Battery", s.system.temperatureC?.let { "%.1f °C".format(java.util.Locale.ROOT, it) } ?: MISSING, null)
        val w = s.cockpit.workers
        rows += Row("Agents", if (w == null) MISSING else "${w.count { it.running }}" + (s.cockpit.workerCapacity?.let { " / $it" } ?: ""), null)
        return rows
    }

    private fun gb(b: Long) = b / 1_073_741_824.0
}

/**
 * ref5 "Local Model Status" tile: same slot size as Memory Activity / Task Flow (Kern's grid), title Type.heading,
 * Online/Offline pill top right, two-column label/value rows in Type.status/caption, no bullets.
 */
class CockpitLocalModelCard(textScale: Float) : CockpitCard(textScale, ContinuumIcons.Icon.BRAIN) {
    override fun title(s: ContinuumDashboardState) = "Local Model Status"
    override fun rows(s: ContinuumDashboardState) = LocalModelStatusPresentation.rows(s)

    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        JarvisNeon.glassCard(p, 1f, 1f, w - 1f, h - 1f, glow = if (highlighted) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.SUBTLE, active = highlighted)
        val pad = JarvisDesign.Styles.tile.padH
        p.drawText(title(s), pad, pad + p.capHeight(JarvisDesign.Type.heading), JarvisDesign.Type.heading, Fill.Solid(JarvisDesign.Colors.text))
        val (txt, tone) = LocalModelStatusPresentation.connectivity(s)
        val st = JarvisDesign.Styles.pill.copy(rim = if (tone == ContinuumCockpitPresentation.Tone.OK) JarvisDesign.Colors.ok else JarvisDesign.Styles.pill.rim)
        val pw = p.measureText(txt, JarvisDesign.Type.status) + 2f * st.padH * .6f
        val ph = st.height * .8f
        JarvisNeon.component(p, w - pad - pw, pad - 2f, w - pad, pad - 2f + ph, st)
        p.drawText(txt, w - pad - pw / 2f, pad - 2f + (ph + p.capHeight(JarvisDesign.Type.status)) / 2f, JarvisDesign.Type.status,
            Fill.Solid(if (tone == ContinuumCockpitPresentation.Tone.OK) JarvisDesign.Colors.ok else JarvisDesign.Colors.textSecondary), TextAlign.CENTER)
        val top = pad + ph + 8f
        val lineH = p.capHeight(JarvisDesign.Type.caption) * 2.1f
        val rs = rows(s); val visible = ((h - top - pad / 2f) / lineH).toInt().coerceIn(0, rs.size)
        rs.take(visible).forEachIndexed { i, r ->
            val y = top + i * lineH + p.capHeight(JarvisDesign.Type.caption)
            p.drawText(r.label, pad, y, JarvisDesign.Type.status, Fill.Solid(JarvisDesign.Colors.textSecondary))
            val c = when (r.state) { ContinuumCockpitPresentation.Tone.OK -> JarvisDesign.Colors.ok; ContinuumCockpitPresentation.Tone.WARN -> JarvisDesign.Colors.warn
                else -> if (r.value == LocalModelStatusPresentation.MISSING) JarvisDesign.Colors.textMuted else JarvisDesign.Colors.text }
            p.drawText(ContinuumSurfaces.fit(p, r.value, JarvisDesign.Type.caption, w * .58f), w - pad, y, JarvisDesign.Type.caption, Fill.Solid(c), TextAlign.RIGHT)
        }
    }
}
