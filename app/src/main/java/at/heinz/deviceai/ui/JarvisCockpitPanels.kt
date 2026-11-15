package at.heinz.deviceai.ui

import at.heinz.deviceai.chief.TurnPhase
import at.heinz.deviceai.cockpit.StepState
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
        ((b.bottom - 6f - top) / maxOf(n, 1)).coerceAtLeast(p.capHeight(Type.caption) + 6f)

    // ---- Active Agents --------------------------------------------------------------------------------------------

    fun agentsTitle(s: ContinuumDashboardState) = ContinuumCockpitPresentation.agentsTitle(s.cockpit)

    /** Real worker rows (ChiefWorkerPool); no pool → one honest "Agenten-Pool · nicht gestartet" row, no invented agents. */
    fun agentRows(s: ContinuumDashboardState): List<Row> =
        if (s.cockpit.workers == null) listOf(Row("Agenten-Pool", "nicht gestartet", null, Tone.NEUTRAL))
        else ContinuumCockpitPresentation.agentRows(s.cockpit)

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
        drawStepList(p, b, "Aktueller Ablauf", taskRows(s))
    }

    fun workProcessRows(s: ContinuumDashboardState): List<Row> {
        val vis = DocumentBlueprintView.visible(s.document)
        if (vis != null && vis.steps.isNotEmpty()) {
            return vis.steps.map { Row(it.label, "", null, stepTone(it.state)) }
        }
        return taskRows(s)
    }

    fun drawWorkProcess(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        if (isProcessVacant(s)) JarvisVisualDepth.recedeVacantPanel(p, b)
        drawStepList(p, b, "Verarbeitung", workProcessRows(s))
    }

    fun isProcessVacant(s: ContinuumDashboardState): Boolean {
        val vis = DocumentBlueprintView.visible(s.document)
        if (vis != null && vis.steps.isNotEmpty()) return false
        return s.cockpit.steps.isEmpty() && s.cockpit.openWork.isEmpty()
    }

    fun drawDocumentPreview(p: ContinuumPainter, b: Box, s: ContinuumDashboardState, workstation: Boolean = false) {
        if (workstation && s.computer == null && DocumentBlueprintView.visible(s.document) == null) {
            drawComputerRows(p, b, "Projekte", Icon.CODE, ContinuumWorkspaceSummary.projects(s), true)
            return
        }
        s.computer?.let { computer ->
            val agents = s.cockpit.workers?.takeIf { it.any { worker -> worker.running } }
                ?.let { agentRows(s).take(2) }.orEmpty()
            drawComputerRows(p, b, "Auftrag", Icon.DOCUMENT, computer.planningRows() + agents, workstation)
            return
        }
        val y0 = if (workstation) CockpitWorkstationChrome.panelHeader(p, b, "Dokument", Icon.DOCUMENT)
        else title(p, b, "Dokument") { x, y ->
            ContinuumIcons.draw(p, Icon.DOCUMENT, x, y, 13f, Fill.Solid(Colors.cyanSoft), 1.3f)
        }
        val vis = DocumentBlueprintView.visible(s.document)
        val st = Type.caption
        val body = Type.body.copy(size = 14f)
        var y = rowsTop(p, y0)
        if (vis == null) {
            JarvisVisualDepth.recedeVacantPanel(p, b)
            p.drawText(NONE, b.left + 16f, y + p.capHeight(st), st, Fill.Solid(Colors.textSecondary))
            return
        }
        val nameStyle = Type.heading
        val nameRoom = b.width - 48f
        p.drawText(fit(p, vis.documentName, nameStyle, nameRoom), b.left + 16f,
            y + p.capHeight(nameStyle), nameStyle, Fill.Solid(Colors.text))
        vis.pageLabel?.let { page ->
            p.drawText(page, b.right - 12f, y + p.capHeight(st), st,
                Fill.Solid(Colors.textSecondary), TextAlign.RIGHT)
        }
        y += p.capHeight(nameStyle) + 10f
        vis.question?.takeIf { it.isNotBlank() }?.let { question ->
            p.drawText(fit(p, question, body, b.width - 32f), b.left + 16f,
                y + p.capHeight(body), body, Fill.Solid(Colors.text))
            y += p.capHeight(body) + 8f
        }
        vis.entriesLabel?.let { entries ->
            p.drawText(fit(p, entries, st, b.width - 32f), b.left + 16f,
                y + p.capHeight(st), st, Fill.Solid(Colors.textSecondary))
        }
    }

    fun drawDraftPreview(p: ContinuumPainter, b: Box, s: ContinuumDashboardState, workstation: Boolean = false) {
        if (workstation && s.computer == null && DocumentBlueprintView.visible(s.document) == null) {
            drawComputerRows(p, b, "Offene Arbeit", Icon.DOCUMENT, ContinuumWorkspaceSummary.work(s), true)
            return
        }
        s.computer?.let { computer ->
            drawComputerRows(p, b, "Ergebnis", Icon.DOCUMENT, computer.resultRows(), workstation)
            return
        }
        val y0 = if (workstation) CockpitWorkstationChrome.panelHeader(p, b, "Entwurf", Icon.CODE)
        else title(p, b, "Zusammenfassung – Entwurf") { x, y ->
            ContinuumIcons.draw(p, Icon.CODE, x, y, 13f, Fill.Solid(Colors.cyanSoft), 1.3f)
        }
        val vis = DocumentBlueprintView.visible(s.document)
        val st = Type.caption
        var y = rowsTop(p, y0)
        if (vis == null || !vis.showsDraft) {
            JarvisVisualDepth.recedeVacantPanel(p, b)
            p.drawText(NONE, b.left + 16f, y + p.capHeight(st), st, Fill.Solid(Colors.textSecondary))
            return
        }
        val lines = listOf(
            vis.draftGoal?.let { "Ziel: $it" },
            vis.draftApproach?.let { "Vorgehen: $it" },
            vis.draftOpenItems?.let { "Offene Punkte: $it" },
            vis.draftBody
        ).mapNotNull { it?.takeIf { text -> text.isNotBlank() } }
        val lineH = p.capHeight(st) + 6f
        lines.take(((b.bottom - 28f - y) / lineH).toInt().coerceAtLeast(0)).forEach { line ->
            p.drawText(fit(p, line, st, b.width - 32f), b.left + 16f, y + p.capHeight(st),
                st, Fill.Solid(Colors.text))
            y += lineH
        }
        if (vis.buttons.isEmpty()) return
        val buttonH = 22f
        val buttonY = (b.bottom - 10f - buttonH).coerceAtLeast(y + 4f)
        val gap = 6f
        val w = (b.width - 24f - (vis.buttons.size - 1) * gap) / vis.buttons.size
        vis.buttons.forEachIndexed { i, label ->
            val left = b.left + 12f + i * (w + gap)
            val box = Box(left, buttonY, left + w, buttonY + buttonH)
            JarvisReferenceHud.surface(p, box, 8f, Colors.cyan, Colors.cyanDeep)
            p.drawText(fit(p, label, st, box.width - 8f), box.centerX,
                box.centerY + p.capHeight(st) / 2f, st, Fill.Solid(Colors.text), TextAlign.CENTER)
        }
    }

    internal fun drawComputerRows(p: ContinuumPainter, b: Box, heading: String, icon: Icon,
                                 rows: List<Row>, workstation: Boolean) {
        val y0 = if (workstation) CockpitWorkstationChrome.panelHeader(p, b, heading, icon)
        else title(p, b, heading) { x, y ->
            ContinuumIcons.draw(p, icon, x, y, 13f, Fill.Solid(Colors.cyanSoft), 1.3f)
        }
        val style = Type.caption
        val cap = p.capHeight(style)
        val lineHeight = cap + 5f
        val width = (b.width - 28f).coerceAtLeast(0f)
        var y = rowsTop(p, y0) + cap
        for (row in rows) {
            if (y > b.bottom - 8f) break
            val words = (row.label + ": " + row.value).split(' ')
            var line = ""
            for (word in words) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && p.measureText(next, style) > width) {
                    p.drawText(fit(p, line, style, width), b.left + 14f, y, style, Fill.Solid(tone(row.state)))
                    y += lineHeight
                    line = word
                    if (y > b.bottom - 8f) break
                } else line = next
            }
            if (y > b.bottom - 8f) break
            p.drawText(fit(p, line, style, width), b.left + 14f, y, style, Fill.Solid(tone(row.state)))
            y += lineHeight + 4f
        }
    }

    private fun drawStepList(p: ContinuumPainter, b: Box, heading: String, rows: List<Row>) {
        val y0 = title(p, b, heading) { x, y -> ContinuumIcons.draw(p, Icon.REFRESH, x, y, 13f, Fill.Solid(Colors.cyanSoft), 1.3f) }
        val st = Type.caption; val cap = p.capHeight(st)
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

    private fun stepTone(state: StepState) = when (state) {
        StepState.DONE -> Tone.OK
        StepState.ACTIVE -> Tone.ACTIVE
        StepState.FAILED -> Tone.ERROR
        StepState.BLOCKED -> Tone.WARN
        StepState.WAITING, StepState.UNVERIFIED -> Tone.NEUTRAL
    }

    // ---- Active owner task ----------------------------------------------------------------------------------------

    /** A real turn, worker or process step is in progress. Idle conversation never counts. */
    fun isOwnerTaskRunning(s: ContinuumDashboardState): Boolean {
        val phase = s.cockpit.turnPhase
        if (phase == TurnPhase.ACKNOWLEDGED || phase == TurnPhase.WORKING) return true
        if (s.cockpit.steps.any { it.state == StepState.ACTIVE }) return true
        return s.cockpit.workers?.any { it.running } == true
    }

    fun activeTaskText(s: ContinuumDashboardState): String {
        s.cockpit.dialog.lastOrNull { it.fromOwner }?.text?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        s.cockpit.steps.lastOrNull { it.state == StepState.ACTIVE }?.label?.takeIf { it.isNotBlank() }?.let { return it }
        return NONE
    }

    /** Completed / total real process steps; null when there is no step list to measure. */
    fun activeTaskProgress(s: ContinuumDashboardState): Pair<Int, Int>? {
        val steps = s.cockpit.steps
        if (steps.isEmpty()) return null
        return steps.count { it.state == StepState.DONE } to steps.size
    }

    fun activeTaskElapsedMillis(s: ContinuumDashboardState): Long? =
        s.cockpit.workers?.filter { it.running }?.maxOfOrNull { it.elapsedMillis }

    fun formatElapsed(ms: Long?): String {
        if (ms == null || ms < 0L) return NONE
        val total = ms / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val sec = total % 60L
        return String.format(java.util.Locale.GERMANY, "%02d:%02d:%02d", h, m, sec)
    }

    /** Right-hand dialog/input control becomes STOP while a real task runs. */
    const val STOP_TARGET = "stop"

    // ---- Status bar -----------------------------------------------------------------------------------------------

    enum class Seg { SYSTEM, DEVICE, LOCAL_AI, SYNC, PRIVACY, LOAD }
    data class Segment(val kind: Seg, val text: String, val ok: Boolean)

    data class TelemetryTile(val id: String, val label: String, val value: String, val fraction: Float?)

    fun percentLabel(value: Int?): String = value?.let { "$it %" } ?: NONE

    fun temperatureLabel(celsius: Float?): String =
        celsius?.takeIf { it.isFinite() }?.let { String.format(java.util.Locale.GERMANY, "%.0f °C", it) } ?: NONE

    /**
     * Device thermal status from [android.os.PowerManager] (0 = none … 6 = shutdown).
     * Shown as "Leistung" because it is the only measured performance-throttle signal; missing → "—".
     */
    fun performanceLabel(thermalStatus: Int?): String = when (thermalStatus) {
        null -> NONE
        0 -> "Stabil"
        1 -> "Leicht"
        2 -> "Mäßig"
        else -> "Gedrosselt"
    }

    /** CPU, RAM, GPU (unmeasured), battery, temperature, thermal/Leistung. Never invents a GPU reading. */
    fun telemetryTiles(s: ContinuumDashboardState): List<TelemetryTile> {
        val sys = s.system
        fun frac(v: Int?) = v?.coerceIn(0, 100)?.div(100f)
        return listOf(
            TelemetryTile("cpu", "CPU", percentLabel(sys.appCpuPercent), frac(sys.appCpuPercent)),
            TelemetryTile("ram", "RAM", percentLabel(sys.memoryPercent), frac(sys.memoryPercent)),
            TelemetryTile("gpu", "GPU", NONE, null),
            TelemetryTile("battery", "Akku", percentLabel(sys.batteryPercent), frac(sys.batteryPercent)),
            TelemetryTile("temp", "Temperatur", temperatureLabel(sys.temperatureC),
                sys.temperatureC?.takeIf { it.isFinite() }?.let { (it / 50f).coerceIn(0f, 1f) }),
            TelemetryTile("perf", "Leistung", performanceLabel(sys.thermalStatus), null),
        )
    }

    /** ref5 / owner-reference order: System, Netz, Local AI, Continuum Sync, Sicherheit, CPU/RAM/Akku/Temp. */
    fun statusSegments(s: ContinuumDashboardState): List<Segment> {
        val sys = s.system
        val net = when (sys.networkOnline) { true -> "online"; false -> "offline"; null -> NONE }
        val temp = temperatureLabel(sys.temperatureC)
        val sync = s.storage.syncLabel.ifBlank { ContinuumDashboardState.UNKNOWN }
        return listOf(
            Segment(Seg.SYSTEM, "Systemstatus: ${sys.overall}", sys.openIssues == 0),
            Segment(Seg.DEVICE, "Netz: $net", sys.networkOnline == true),
            Segment(Seg.LOCAL_AI, "Lokale KI: ${s.model.stateLabel}", s.model.online),
            Segment(Seg.SYNC, "Continuum-Sync: $sync",
                sync != ContinuumDashboardState.UNKNOWN && !sync.equals(NONE, true)),
            Segment(Seg.PRIVACY, when (s.presentation.localOnly) {
                true -> "Sicherheit: Aktiv"; false -> "Sicherheit: Online nach Freigabe"; null -> "Sicherheit: —"
            }, s.presentation.localOnly == true),
            Segment(Seg.LOAD, "App-CPU ${percentLabel(sys.appCpuPercent)} · RAM ${percentLabel(sys.memoryPercent)} · Akku ${percentLabel(sys.batteryPercent)} · $temp", false),
        )
    }

    fun drawStatusBar(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        JarvisReferenceHud.surface(p, b, minOf(18f, b.height / 3f), Colors.violetRim, Colors.cyan)
        val telemetryH = if (b.height >= 96f) minOf(JarvisShellLayout.TELEMETRY_BAND_DP, b.height * .55f) else 0f
        if (telemetryH > 36f) {
            drawTelemetryRow(p, Box(b.left + 8f, b.top + 6f, b.right - 8f, b.top + telemetryH), s)
        }
        val bar = if (telemetryH > 36f) Box(b.left, b.top + telemetryH, b.right, b.bottom) else b
        drawStatusSegments(p, bar, s)
    }

    fun drawTelemetryRow(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        val tiles = telemetryTiles(s)
        if (tiles.isEmpty() || b.width <= 0f) return
        val gap = 6f
        val w = (b.width - (tiles.size - 1) * gap) / tiles.size
        val caption = Type.caption
        val valueStyle = Type.tileLabel
        tiles.forEachIndexed { i, tile ->
            val left = b.left + i * (w + gap)
            val box = Box(left, b.top, left + w, b.bottom)
            JarvisReferenceHud.surface(p, box, 10f, Colors.cyanDeep, Colors.violetRim)
            val cx = box.centerX
            p.drawText(JarvisShellRenderer.fit(p, tile.label, caption, box.width - 8f), cx,
                box.top + 4f + p.capHeight(caption), caption, Fill.Solid(Colors.textSecondary), TextAlign.CENTER)
            p.drawText(JarvisShellRenderer.fit(p, tile.value, valueStyle, box.width - 8f), cx,
                box.centerY + p.capHeight(valueStyle) / 2f + 4f, valueStyle,
                Fill.Solid(if (tile.value == NONE) Colors.textMuted else Colors.cyan), TextAlign.CENTER)
            tile.fraction?.let { frac ->
                val track = Box(box.left + 8f, box.bottom - 8f, box.right - 8f, box.bottom - 5f)
                p.fillRoundRect(track.left, track.top, track.right, track.bottom, 2f, Fill.Solid(Colors.rim), .45f)
                val fillTo = track.left + track.width * frac.coerceIn(0f, 1f)
                if (fillTo > track.left) p.fillRoundRect(track.left, track.top, fillTo, track.bottom, 2f, Fill.Solid(Colors.cyan), .85f)
            }
        }
    }

    private fun drawStatusSegments(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        val segments = statusSegments(s)
        val textStyle = Type.status.copy(size = (b.width / 55f).coerceIn(12f, 16f))
        val columns = when {
            b.width < 360f -> 1
            b.width < 720f -> 2
            else -> 3
        }
        val rows = (segments.size + columns - 1) / columns
        val rowHeight = b.height / rows
        segments.forEachIndexed { i, segment ->
            val column = i % columns; val row = i / columns
            val left = b.left + column * b.width / columns
            val width = b.width / columns
            val cy = b.top + (row + .5f) * rowHeight
            if (column > 0) p.drawLine(left, cy - rowHeight * .32f, left, cy + rowHeight * .32f,
                .8f, Fill.Solid(Colors.rim))
            val x = left + 19f
            when (segment.kind) {
                Seg.SYSTEM -> {
                    JarvisRailIcons.draw(p, "memory", x - 10f, cy - 10f, 20f, Colors.cyanSoft)
                }
                Seg.DEVICE -> JarvisRailIcons.draw(p, "device", x - 6f, cy - 6f, 12f, Colors.cyanSoft)
                Seg.LOCAL_AI -> ContinuumIcons.draw(p, Icon.CPU, x, cy, 20f, Fill.Solid(Colors.cyanSoft))
                Seg.SYNC -> ContinuumIcons.draw(p, Icon.REFRESH, x, cy, 14f, Fill.Solid(Colors.cyanSoft))
                Seg.PRIVACY -> shield(p, x, cy, 20f, Colors.cyanSoft)
                Seg.LOAD -> JarvisRailIcons.draw(p, "system", x - 6f, cy - 6f, 12f, Colors.cyanSoft)
            }
            if (segment.ok) JarvisNeon.halo(p, x + 21f, cy, 9f, Colors.ok, .25f)
            p.fillCircle(x + 21f, cy, 3.5f, Fill.Solid(if (segment.ok) Colors.ok else Colors.textMuted))
            val lines = JarvisCockpitGridContent.wrap(p, segment.text, textStyle, width - 62f)
            val lineHeight = p.capHeight(textStyle) + 3f
            val available = (rowHeight / lineHeight).toInt().coerceAtLeast(1)
            val visible = lines.take(available)
            visible.forEachIndexed { j, line ->
                p.drawText(line, left + 53f, cy - visible.size * lineHeight / 2f + p.capHeight(textStyle) + j * lineHeight,
                    textStyle, Fill.Solid(Colors.textSecondary))
            }
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
