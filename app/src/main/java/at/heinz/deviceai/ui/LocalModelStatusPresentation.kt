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
 * VRAM/GPU memory, SoC/CPU temperature, tokens/s; "Mode" is shown as [MISSING] (no routing-mode source).
 * Placement in the grid is Kern's (#481).
 */
object LocalModelStatusPresentation {
    /** ref5 placeholder for any value without a source (never an invented number). */
    const val MISSING = "—"
    private val THERMAL = arrayOf("Normal", "Leicht", "Mäßig", "Stark", "Kritisch", "Notfall", "Abschaltung")

    /** Online/Offline pill from the collector's ConnectivityManager state (ACCESS_NETWORK_STATE, already declared). */
    fun connectivity(s: ContinuumDashboardState): Pair<String, Tone> = when (s.system.networkOnline) {
        true -> "Online" to Tone.OK; false -> "Offline" to Tone.WARN; null -> MISSING to Tone.NEUTRAL
    }

    /** The model card reports its loaded local runtime, independently of internet connectivity. */
    fun modelAvailability(s: ContinuumDashboardState): Pair<String, Tone> =
        if (s.model.online) "Online" to Tone.OK else "Offline" to Tone.NEUTRAL

    /** [resources] = last load-time measurement; null = no state field yet -> [MISSING]. */
    fun rows(s: ContinuumDashboardState, resources: LocalModelResourceSnapshot? = null,
             admissionRefusal: String? = null): List<Row> {
        val m = s.model
        val rows = mutableListOf(
            Row("Modell", if (m.online) m.name else MISSING, null, if (m.online) Tone.OK else Tone.NEUTRAL),
            // ref5 "Mode: Local (Private)" has no runtime source (no routing-mode field) -> always the placeholder.
            Row("Modus", MISSING, null),
            Row("Status", m.stateLabel, null, if (m.online) Tone.OK else Tone.NEUTRAL),
            Row("Antwortzeit", m.latencyMs?.let { "$it ms" } ?: MISSING, null)
        )
        if (resources == null) {
            rows += Row("RAM", s.system.memoryPercent?.let { "$it % Gerät" } ?: MISSING,
                s.system.memoryPercent?.takeIf { it in 0..100 }?.div(100f))
            rows += Row("Wärme", s.system.thermalStatus?.let { THERMAL.getOrNull(it) } ?: MISSING, null)
            rows += Row("Ladefreigabe", MISSING, null)
        } else {
            rows += Row("RAM", "%.1f / %.1f GB".format(java.util.Locale.ROOT, gb(resources.totalRamBytes - resources.availableRamBytes), gb(resources.totalRamBytes)),
                (1f - resources.availableRamBytes.toFloat() / resources.totalRamBytes).coerceIn(0f, 1f),
                if (resources.systemLowMemory) Tone.WARN else Tone.NEUTRAL)
            rows += Row("Wärme", resources.thermalStatus?.let { THERMAL[it] } ?: MISSING, null,
                if ((resources.thermalStatus ?: 0) >= 3) Tone.WARN else Tone.NEUTRAL)
            rows += Row("Ladefreigabe", if (admissionRefusal == null) "Freigegeben" else LocalModelLoadAdmission.ownerMessage(admissionRefusal), null,
                if (admissionRefusal == null) Tone.OK else Tone.WARN)
        }
        rows += Row("Akku-Temp.", s.system.temperatureC?.let { "%.1f °C".format(java.util.Locale.ROOT, it) } ?: MISSING, null)
        val w = s.cockpit.workers
        rows += Row("Assistenten", if (w == null) MISSING else "${w.count { it.running }}" + (s.cockpit.workerCapacity?.let { " / $it" } ?: ""), null)
        return rows
    }

    private fun gb(b: Long) = b / 1_073_741_824.0
}

/** Model availability and measured telemetry. */
class CockpitLocalModelCard(textScale: Float) : CockpitCard(textScale, ContinuumIcons.Icon.CPU) {
    override fun title(s: ContinuumDashboardState) = "Modell und Gerät"
    override fun rows(s: ContinuumDashboardState) = LocalModelStatusPresentation.rows(s)

    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val c = JarvisDesign.Colors
        val type = JarvisDesign.Type
        JarvisNeon.glassCard(p, 1f, 1f, w - 1f, h - 1f, glow = if (highlighted) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.SUBTLE, active = highlighted)
        val pad = JarvisDesign.Styles.tile.padH * .75f
        // Title row: CPU icon, title, chevron.
        val iconSize = JarvisDesign.Styles.pill.height * .7f
        val titleMid = pad + iconSize / 2f
        ContinuumIcons.draw(p, ContinuumIcons.Icon.CPU, pad + iconSize / 2f, titleMid, iconSize, Fill.Solid(c.cyanSoft), iconSize / 12f)
        p.drawText(ContinuumSurfaces.fit(p, title(s), type.heading, w - pad * 3 - iconSize), pad + iconSize + JarvisDesign.Styles.pill.iconGap, titleMid + p.capHeight(type.heading) / 2f, type.heading, Fill.Solid(c.text))
        ContinuumSurfaces.chevron(p, w - pad - iconSize * .25f, titleMid, iconSize * .55f, c.cyan)
        // Headline + connectivity pill.
        val (txt, tone) = LocalModelStatusPresentation.modelAvailability(s)
        val ok = tone == ContinuumCockpitPresentation.Tone.OK
        val st = JarvisDesign.Styles.pill.copy(rim = if (ok) c.ok else JarvisDesign.Styles.pill.rim)
        val ph = st.height * .8f
        val pw = p.measureText(txt, type.status) + 2f * st.padH
        val pillTop = titleMid + iconSize / 2f + pad * .5f
        JarvisNeon.component(p, w - pad - pw, pillTop, w - pad, pillTop + ph, st)
        p.drawText(txt, w - pad - pw / 2f, pillTop + (ph + p.capHeight(type.status)) / 2f, type.status,
            Fill.Solid(if (ok) c.ok else c.textSecondary), TextAlign.CENTER)
        p.drawText(ContinuumSurfaces.fit(p, s.model.name, type.tileLabel, w - 3f * pad - pw), pad,
            pillTop + (ph + p.capHeight(type.tileLabel)) / 2f, type.tileLabel, Fill.Solid(c.text))
        if (h >= 180f) {
            drawTelemetry(p, w, h, s, pillTop + ph + pad * .6f, pad)
            return
        }
        // Body: emblem left, label/value rows right, RAM bar below.
        val all = rows(s)
        val shown = listOf("Modell", "Modus", "Status", "RAM").mapNotNull { l -> all.firstOrNull { it.label == l } }
        val bodyTop = pillTop + ph + pad * .6f
        val barH = 4f
        val barTop = h - pad - barH
        val colX = w * .30f
        val lineH = ((barTop - pad * .5f - bodyTop) / shown.size).coerceAtLeast(p.capHeight(type.caption) * 1.4f)
        drawEmblem(p, pad, bodyTop, colX - pad * 1.5f, barTop + barH - bodyTop)
        val labelW = shown.maxOf { p.measureText(it.label + ":", type.caption) } + JarvisDesign.Styles.pill.iconGap
        shown.forEachIndexed { i, r ->
            val y = bodyTop + i * lineH + (lineH + p.capHeight(type.caption)) / 2f
            if (y > barTop) return@forEachIndexed
            p.drawText(r.label + ":", colX, y, type.caption, Fill.Solid(c.textSecondary))
            val col = when (r.state) {
                ContinuumCockpitPresentation.Tone.WARN -> c.warn
                else -> if (r.value == LocalModelStatusPresentation.MISSING) c.textMuted else c.text
            }
            p.drawText(ContinuumSurfaces.fit(p, r.value, type.caption, w - pad - colX - labelW), colX + labelW, y, type.caption, Fill.Solid(col))
        }
        // RAM usage bar: track always, fill only with a measured fraction.
        p.fillRoundRect(colX, barTop, w - pad, barTop + barH, barH / 2f, Fill.Solid(c.pillSurface))
        p.strokeRoundRect(colX, barTop, w - pad, barTop + barH, barH / 2f, 1f, Fill.Solid(c.cyanDeep), .6f)
        val frac = shown.firstOrNull { it.label == "RAM" }?.fraction
        if (frac != null && frac > 0f) {
            val r = colX + (w - pad - colX) * frac
            JarvisNeon.bloomRoundRect(p, colX, barTop, r, barTop + barH, barH / 2f, c.cyan, .3f)
            p.fillRoundRect(colX, barTop, r, barTop + barH, barH / 2f, Fill.Linear(colX, 0f, r, 0f, intArrayOf(c.cyanDeep, c.cyan)))
        }
    }

    private fun drawTelemetry(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState,
                              top: Float, pad: Float) {
        val c = JarvisDesign.Colors
        val type = JarvisDesign.Type
        val caption = type.caption
        val cap = p.capHeight(caption)
        val metricTop = h - pad - 3f * cap - 23f
        val chartTop = top + cap + 6f
        val chartBottom = maxOf(chartTop + 18f, metricTop - 12f)
        fun percent(v: Int?) = v?.takeIf { it in 0..100 }?.let { "$it %" }
            ?: LocalModelStatusPresentation.MISSING
        p.drawText("App-CPU", pad, top + cap, caption, Fill.Solid(c.textSecondary))
        p.drawText(percent(s.system.appCpuPercent), w - pad, top + cap, caption,
            Fill.Solid(c.cyanSoft), TextAlign.RIGHT)
        WaveChart.draw(p, pad, chartTop, w - pad, chartBottom, s.system.activity, layers = 1)
        if (s.system.activity.size < 2) {
            p.drawText(ContinuumSurfaces.fit(p, "Verlauf noch nicht verfügbar", type.status, w - 2f * pad), w / 2f,
                chartTop + (chartBottom - chartTop + p.capHeight(type.status)) / 2f,
                type.status, Fill.Solid(c.textMuted), TextAlign.CENTER)
        }
        val ramY = metricTop + cap
        p.drawText("Geräte-RAM", pad, ramY, caption, Fill.Solid(c.textSecondary))
        p.drawText(percent(s.system.memoryPercent), w - pad, ramY, caption,
            Fill.Solid(c.text), TextAlign.RIGHT)
        val barTop = ramY + 5f
        p.fillRoundRect(pad, barTop, w - pad, barTop + 4f, 2f, Fill.Solid(c.pillSurface))
        p.strokeRoundRect(pad, barTop, w - pad, barTop + 4f, 2f, .7f, Fill.Solid(c.cyanDeep), .65f)
        s.system.memoryPercent?.takeIf { it in 1..100 }?.let { value ->
            val right = pad + (w - 2f * pad) * value / 100f
            p.fillRoundRect(pad, barTop, right, barTop + 4f, 2f, Fill.Solid(c.cyan), .8f)
        }
        val temp = s.system.temperatureC?.takeIf { it.isFinite() }
            ?.let { String.format(java.util.Locale.GERMANY, "%.1f °C", it) }
            ?: LocalModelStatusPresentation.MISSING
        val bottom = h - pad
        p.drawText("Akku-Temperatur", pad, bottom, caption, Fill.Solid(c.textSecondary))
        p.drawText(temp, w - pad, bottom, caption, Fill.Solid(c.text), TextAlign.RIGHT)
    }

    /** Isometric chip emblem of ref5: stacked glowing plates with a bright core, cyan to violet edge light. */
    private fun drawEmblem(p: ContinuumPainter, l: Float, t: Float, w: Float, h: Float) {
        val c = JarvisDesign.Colors
        val cx = l + w / 2f
        val rx = minOf(w / 2f, h * .95f) * .9f
        val ry = rx * .5f
        val cy = t + h / 2f
        JarvisNeon.halo(p, cx, cy, rx * 1.1f, c.cyan, .25f)
        val path = VectorPath()
        fun plate(dy: Float, k: Float, fill: Fill, rim: Int, rimAlpha: Float) {
            val x = rx * k; val y = ry * k
            path.reset().moveTo(cx, cy + dy - y).lineTo(cx + x, cy + dy).lineTo(cx, cy + dy + y).lineTo(cx - x, cy + dy).close()
            p.fillPath(path, fill)
            p.strokePath(path, 1f, Fill.Solid(rim), rimAlpha)
        }
        plate(ry * .45f, 1f, Fill.Solid(c.pillSurface), c.violet, .9f)
        plate(ry * .15f, .92f, Fill.Linear(cx - rx, cy, cx + rx, cy, intArrayOf(c.cyanDeep, c.violet)), c.cyan, .8f)
        plate(-ry * .2f, .78f, Fill.Solid(c.surfaceLow), c.cyan, 1f)
        plate(-ry * .2f, .4f, Fill.Solid(c.cyanDeep), c.glowCore, .9f)
        JarvisNeon.bloom(p, cx, cy - ry * .2f, rx * .35f, c.cyan, .8f)
        p.fillCircle(cx, cy - ry * .2f, rx * .08f, Fill.Solid(c.glowCore))
    }
}
