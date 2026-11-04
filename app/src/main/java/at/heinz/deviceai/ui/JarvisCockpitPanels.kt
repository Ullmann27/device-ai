package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Row
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Tone
import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisDesign.Type
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Integration (#486, Endspurt 10:11): ref5 boxes "Active Agents", "Current Task Flow" and the bottom status bar,
 * drawn inside Kern's grid boxes (JarvisCockpitGridContent delegates these three slots). Only #479 tokens.
 * Data only from real sources: ChiefWorkerPool snapshot / TurnLifecycle steps via CockpitStatus
 * (ContinuumCockpitPresentation rows) and SystemStatus / ModelStatus / StorageStatus; missing → "—" / "offline".
 * Layout measured in ref5 (px x 0.642 = dp): title row 8 dp + heading cap, rows ~18 dp, dot 2.5 dp, step ring 5.5 dp.
 */
object JarvisCockpitPanels {
    const val NONE = "—"

    private fun fit(p: ContinuumPainter, t: String, st: TextStyle, w: Float) = JarvisShellRenderer.fit(p, t, st, w)

    /** Title with icon left and chevron right (ref5 "Active Agents (4)  >"). Returns the title baseline. */
    private fun title(p: ContinuumPainter, b: Box, text: String, icon: (Float, Float) -> Unit): Float {
        val cap = p.capHeight(Type.heading)
        val y = b.top + 10f + cap
        icon(b.left + 16f, y - cap / 2f)
        p.drawText(fit(p, text, Type.heading, b.width - 52f), b.left + 30f, y, Type.heading, Fill.Solid(Colors.text))
        ContinuumIcons.draw(p, Icon.CHEVRON_RIGHT, b.right - 12f, y - cap / 2f, 9f, Fill.Solid(Colors.cyan), 1.4f)
        return y
    }

    private fun rowsTop(p: ContinuumPainter, titleBaseline: Float) = titleBaseline + 8f

    private fun pitch(p: ContinuumPainter, b: Box, top: Float, n: Int) =
        ((b.bottom - 6f - top) / maxOf(n, 1)).coerceIn(p.capHeight(Type.caption) + 6f, 18f)

    // ---- Active Agents --------------------------------------------------------------------------------------------

    fun agentsTitle(s: ContinuumDashboardState) = ContinuumCockpitPresentation.agentsTitle(s.cockpit)

    /** Real worker rows (ChiefWorkerPool); none → one "—" row, no invented agents. */
    fun agentRows(s: ContinuumDashboardState): List<Row> =
        if (s.cockpit.workers == null) listOf(Row(NONE, "offline", null)) else ContinuumCockpitPresentation.agentRows(s.cockpit)

    fun drawAgents(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        val y0 = title(p, b, agentsTitle(s)) { x, y -> JarvisRailIcons.draw(p, "logic", x - 7f, y - 7f, 14f, Colors.cyanSoft) }
        val rows = agentRows(s); val st = Type.caption; val cap = p.capHeight(st)
        val top = rowsTop(p, y0); val h = pitch(p, b, top, 4)
        val nameW = b.width * .36f
        rows.take(((b.bottom - 4f - top) / h).toInt().coerceAtLeast(0)).forEachIndexed { i, r ->
            val cy = top + h * i + h / 2f
            p.fillCircle(b.left + 14f, cy, 2.8f, Fill.Solid(tone(r.state)))
            p.drawText(fit(p, r.label, Type.tileLabel, nameW - 26f), b.left + 24f, cy + cap / 2f, Type.tileLabel, Fill.Solid(Colors.text))
            val ax = b.left + nameW
            if (i < rows.size - 1) p.drawLine(ax, cy + h / 2f, b.right - 8f, cy + h / 2f, 1f, Fill.Solid(Colors.rim), .5f)
            ContinuumIcons.draw(p, if (r.state == Tone.ACTIVE) Icon.GEAR else Icon.CLOCK, ax + 6f, cy, 10f, Fill.Solid(Colors.textSecondary), 1f)
            p.drawText(fit(p, r.value.ifEmpty { NONE }, st, b.right - 8f - (ax + 16f)), ax + 16f, cy + cap / 2f, st, Fill.Solid(Colors.textSecondary))
        }
    }

    // ---- Current Task Flow ----------------------------------------------------------------------------------------

    /** Real TurnLifecycle steps; none → "Kein Ablauf" in the same style. */
    fun taskRows(s: ContinuumDashboardState): List<Row> = ContinuumCockpitPresentation.processRows(s.cockpit)

    fun drawTaskFlow(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        val y0 = title(p, b, "Aktueller Ablauf") { x, y -> ContinuumIcons.draw(p, Icon.REFRESH, x, y, 13f, Fill.Solid(Colors.cyanSoft), 1.3f) }
        val rows = taskRows(s); val st = Type.caption; val cap = p.capHeight(st)
        val top = rowsTop(p, y0); val h = pitch(p, b, top, 5)
        val n = rows.take(((b.bottom - 4f - top) / h).toInt().coerceAtLeast(0))
        val x = b.left + 15f; val r = 5.5f
        n.forEachIndexed { i, row ->
            val cy = top + h * i + h / 2f
            if (i < n.size - 1) p.drawLine(x, cy + r, x, cy + h - r, 1f, Fill.Solid(Colors.rim))
            when (row.state) {
                Tone.OK -> { p.strokeCircle(x, cy, r, 1.4f, Fill.Solid(Colors.ok))
                    p.drawLine(x - 2.6f, cy, x - .6f, cy + 2.2f, 1.4f, Fill.Solid(Colors.ok)); p.drawLine(x - .6f, cy + 2.2f, x + 3.4f, cy - 2.6f, 1.4f, Fill.Solid(Colors.ok)) }
                Tone.ACTIVE -> { JarvisNeon.bloom(p, x, cy, r * 1.8f, Colors.cyan, .7f); p.strokeCircle(x, cy, r, 1.6f, Fill.Solid(Colors.glowCore)) }
                Tone.ERROR, Tone.WARN -> p.strokeCircle(x, cy, r, 1.4f, Fill.Solid(tone(row.state)))
                Tone.NEUTRAL -> p.strokeCircle(x, cy, r, 1.2f, Fill.Solid(Colors.rim))
            }
            val color = if (row.state == Tone.NEUTRAL) Colors.textSecondary else Colors.text
            p.drawText(fit(p, row.label, st, b.right - 8f - (x + 12f)), x + 12f, cy + cap / 2f, st, Fill.Solid(color))
        }
    }

    // ---- Status bar -----------------------------------------------------------------------------------------------

    enum class Seg { SYSTEM, DEVICE, LOCAL_AI, SYNC, PRIVACY, LOAD }
    data class Segment(val kind: Seg, val text: String, val ok: Boolean)

    /** ref5 order: System Status, Device, Local AI, Continuum Sync, Privacy, CPU | RAM | temperature. */
    fun statusSegments(s: ContinuumDashboardState): List<Segment> {
        val sys = s.system
        val net = when (sys.networkOnline) { true -> "online"; false -> "offline"; null -> NONE }
        fun pct(v: Int?) = v?.let { "$it %" } ?: NONE
        val temp = sys.temperatureC?.let { String.format(java.util.Locale.GERMANY, "%.0f °C", it) } ?: NONE
        return listOf(
            Segment(Seg.SYSTEM, "Systemstatus: ${sys.overall}", sys.openIssues == 0),
            Segment(Seg.DEVICE, "Gerät: $net", sys.networkOnline == true),
            Segment(Seg.LOCAL_AI, "Lokale KI: ${s.model.stateLabel}", s.model.online),
            Segment(Seg.SYNC, "Continuum-Sync: ${s.storage.syncLabel}", false),
            Segment(Seg.PRIVACY, "Privatsphäre: ${s.model.mode}", true),
            Segment(Seg.LOAD, "CPU ${pct(sys.appCpuPercent)} | RAM ${pct(sys.memoryPercent)} | $temp", false),
        )
    }

    fun drawStatusBar(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        JarvisNeon.glassCard(p, b.left, b.top, b.right, b.bottom, b.height / 2.5f, JarvisDesign.Glow.SUBTLE)
        val segs = statusSegments(s)
        // ref5 widths (px): 160 | 150 | 165 | 200 | 205 | 210 → proportional
        val weights = floatArrayOf(160f, 150f, 165f, 200f, 205f, 210f); val sum = weights.sum()
        val st = Type.status; val cap = p.capHeight(st); val cy = b.top + b.height / 2f
        var l = b.left
        segs.forEachIndexed { i, seg ->
            val w = b.width * weights[i] / sum
            if (i > 0) p.drawLine(l, b.top + 5f, l, b.bottom - 5f, 1f, Fill.Solid(Colors.rim))
            val ix = l + 12f
            when (seg.kind) {
                Seg.SYSTEM -> { if (seg.ok) JarvisNeon.bloom(p, ix, cy, 5f, Colors.ok, .6f); p.fillCircle(ix, cy, 3f, Fill.Solid(if (seg.ok) Colors.ok else Colors.textMuted)) }
                Seg.DEVICE -> JarvisRailIcons.draw(p, "device", ix - 6f, cy - 6f, 12f, Colors.cyanSoft)
                Seg.LOCAL_AI -> ContinuumIcons.draw(p, Icon.CPU, ix, cy, 12f, Fill.Solid(Colors.cyanSoft), 1f)
                Seg.SYNC -> ContinuumIcons.draw(p, Icon.REFRESH, ix, cy, 12f, Fill.Solid(Colors.cyanSoft), 1f)
                Seg.PRIVACY -> shield(p, ix, cy, 12f, Colors.cyanSoft)
                Seg.LOAD -> JarvisRailIcons.draw(p, "system", ix - 6f, cy - 6f, 12f, Colors.violetSoft)
            }
            val color = if (seg.kind == Seg.SYSTEM && seg.ok) Colors.ok else Colors.textSecondary
            p.drawText(fit(p, seg.text, st, l + w - 4f - (ix + 10f)), ix + 10f, cy + cap / 2f, st, Fill.Solid(color))
            l += w
        }
    }

    private fun shield(p: ContinuumPainter, cx: Float, cy: Float, size: Float, color: Int) {
        val k = size / 24f
        val path = VectorPath().moveTo(cx, cy - 10f * k).lineTo(cx + 8f * k, cy - 7f * k).lineTo(cx + 8f * k, cy)
            .quadTo(cx + 7f * k, cy + 7f * k, cx, cy + 10f * k).quadTo(cx - 7f * k, cy + 7f * k, cx - 8f * k, cy)
            .lineTo(cx - 8f * k, cy - 7f * k).close()
        p.strokePath(path, 1.2f, Fill.Solid(color))
        p.drawLine(cx - 3f * k, cy, cx - k, cy + 3f * k, 1.2f, Fill.Solid(color)); p.drawLine(cx - k, cy + 3f * k, cx + 4f * k, cy - 3f * k, 1.2f, Fill.Solid(color))
    }

    private fun tone(t: Tone) = when (t) {
        Tone.OK -> Colors.ok; Tone.WARN -> Colors.warn; Tone.ERROR -> Colors.error; Tone.ACTIVE -> Colors.ok
        Tone.NEUTRAL -> Colors.textMuted
    }
}
