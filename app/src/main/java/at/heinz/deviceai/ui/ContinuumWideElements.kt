package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumDashboardState.Companion.UNKNOWN
import at.heinz.deviceai.ui.ContinuumDashboardState.ProjectState
import at.heinz.deviceai.ui.ContinuumIcons.Icon

/** Elements of the wide (unfolded Fold / tablet) reference layout; positions measured from reference-fold.png. */

/** Reference cockpit rail. Drawn rows and native touch targets share one bounded layout. */
class RailElement(private val items: List<Pair<Icon, String>>, var selected: Int = 0, textScale: Float = 1f) :
    ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        p.fillRect(0f, 0f, w, h, Fill.Linear(0f, 0f, w, 0f,
            intArrayOf(Argb.rgb(2, 7, 20), Argb.rgb(4, 15, 33))))
        p.drawLine(w - .5f, 0f, w - .5f, h, 1f, Fill.Linear(0f, 0f, 0f, h,
            intArrayOf(Argb.of(170, 45, 206, 248), Argb.of(70, 82, 76, 162), Argb.of(150, 126, 75, 232))))
        val cx = w / 2f
        ContinuumIcons.logo(p, cx, 29f, 40f, 22f)
        p.drawText("CONTINUUM", cx, 58f, fittedTitle(p, "CONTINUUM", 600, 10.5f, w - 12f),
            Fill.Solid(ContinuumTheme.TEXT), TextAlign.CENTER)
        val destinations = items + (Icon.DOTS_HORIZONTAL to "Mehr")
        val rows = targets(h)
        for ((i, item) in destinations.withIndex()) {
            val (top, bottom) = rows[i]
            val cy = (top + bottom) / 2f
            val horizontal = w >= 112f
            val iconX = if (horizontal) 22f else cx
            val iconY = if (horizontal) cy else cy - 8f
            val active = i == selected
            if (active) {
                p.fillRoundRect(6f, top + 3f, w - 6f, bottom - 3f, 10f,
                    Fill.Linear(6f, top, w, bottom, intArrayOf(Argb.rgb(9, 76, 102), Argb.rgb(8, 31, 72))))
                p.strokeRoundRect(6f, top + 3f, w - 6f, bottom - 3f, 10f, 1.2f,
                    Fill.Linear(6f, top, w, bottom, intArrayOf(ContinuumTheme.CYAN, ContinuumTheme.VIOLET)), .8f)
                p.fillRoundRect(4f, top + 12f, 6f, bottom - 12f, 1f, Fill.Solid(ContinuumTheme.CYAN))
                ContinuumSurfaces.iconGlow(p, item.first, iconX, iconY, 22f)
            } else {
                ContinuumIcons.draw(p, item.first, iconX, iconY, 20f,
                    Fill.Solid(ContinuumTheme.TEXT_SECONDARY), 1.5f)
            }
            val color = if (active) ContinuumTheme.CYAN_SOFT else ContinuumTheme.TEXT_SECONDARY
            val labelX = if (horizontal) 40f else cx
            val style = fittedTitle(p, item.second, if (active) 600 else 400, 11.3f,
                if (horizontal) w - labelX - 8f else w - 12f)
            p.drawText(item.second, labelX, if (horizontal) cy + p.capHeight(style) / 2f else cy + 18f,
                style, Fill.Solid(color), if (horizontal) TextAlign.LEFT else TextAlign.CENTER)
        }
    }

    override fun describe(s: ContinuumDashboardState) = "Navigation"

    /** Tap targets (top, bottom) per destination in rail coordinates, plus "Mehr" last. */
    fun targets(h: Float): List<Pair<Float, Float>> {
        val top = 80f
        val row = ((h - top - 12f) / (items.size + 1)).coerceIn(48f, 72f)
        return (0..items.size).map { top + it * row to top + (it + 1) * row }
    }
}

/** "Continuum Ultra · Online" plaque at the top right; shows the real answering core and its state. */
class ModelPillElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h, 11f)
        val color = if (s.model.online) ContinuumTheme.GREEN else ContinuumTheme.AMBER
        p.fillCircle(19.5f, h / 2f, 11f, Fill.Radial(19.5f, h / 2f, 11f, intArrayOf(Argb.withAlpha(color, .55f), Argb.withAlpha(color, .05f))))
        p.strokeCircle(19.5f, h / 2f, 11f, 1f, Fill.Solid(Argb.withAlpha(color, .6f)))
        p.fillCircle(19.5f, h / 2f, 3.4f, Fill.Solid(color))
        p.drawText(ContinuumSurfaces.fit(p, s.model.name, t(500, 9.6f), w - 46f), 40.3f, 18.2f, t(500, 9.6f), Fill.Solid(ContinuumTheme.TEXT))
        p.fillCircle(43.5f, 28.6f, 2.6f, Fill.Solid(color))
        p.drawText(s.model.stateLabel, 49f, 31.8f, t(500, 8.6f), Fill.Solid(color))
    }
    override fun describe(s: ContinuumDashboardState) = "${s.model.name}, ${s.model.stateLabel}"
}

/** "Systemstatus" panel: app CPU share (bar), network state (no invented level), saved sources, context, response time. */
class SystemStatusElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h, 14f)
        p.drawText("Systemstatus", 11.7f, 26f, t(500, 11.6f), Fill.Solid(ContinuumTheme.TEXT))
        val overallColor = when (s.system.openIssues) { 0 -> ContinuumTheme.GREEN; null -> ContinuumTheme.AMBER; else -> ContinuumTheme.RED }
        ContinuumSurfaces.pill(p, w - 10f, 11.7f, 18.2f, s.system.overall, t(500, 8f), overallColor, alignRight = true)
        val rows = listOf(
            Triple(Icon.CPU, "App-CPU", ContinuumDashboardState.percent(s.system.appCpuPercent)),
            Triple(Icon.WIFI, "Netzwerk", s.system.networkLabel),
            Triple(Icon.DATABASE, "Quellen", s.storage.activeSources?.let { "$it gespeichert" } ?: UNKNOWN),
            Triple(Icon.DOCUMENT, "Kontextfenster", s.model.context ?: UNKNOWN),
            Triple(Icon.BOLT, "Reaktionszeit", ContinuumDashboardState.millis(s.model.latencyMs))
        )
        val baselines = floatArrayOf(60.4f, 84.4f, 110.4f, 137.7f, 167f)
        for ((i, row) in rows.withIndex()) {
            val y = baselines[i]
            ContinuumIcons.draw(p, row.first, 20.8f, y - 4f, 13f, Fill.Solid(Argb.rgb(196, 210, 235)), 1.2f)
            p.drawText(row.second, 35.7f, y, t(400, 8.4f), Fill.Solid(Argb.rgb(200, 210, 228)))
            val valueColor = if (i < 2) ContinuumTheme.CYAN_SOFT else Argb.rgb(150, 215, 255)
            p.drawText(row.third, w - 11.5f, y, t(500, 9.2f), Fill.Solid(valueColor), TextAlign.RIGHT)
            if (i == 0) {
                // Only the app CPU share is a measured level; connectivity is yes/no and gets no bar.
                val level = s.system.appCpuPercent
                val l = 97.4f; val r = 133f
                p.fillRoundRect(l, y - 7.2f, r, y - 2.4f, 2.4f, Fill.Solid(Argb.rgb(26, 40, 72)))
                if (level != null) {
                    val end = l + (r - l) * level.coerceIn(0, 100) / 100f
                    p.fillRoundRect(l, y - 7.2f, end, y - 2.4f, 2.4f, Fill.Linear(l, 0f, r, 0f,
                        intArrayOf(Argb.rgb(60, 200, 255), Argb.rgb(160, 120, 255), Argb.rgb(255, 120, 210))))
                }
            }
            if (i < rows.size - 1) p.drawLine(11f, y + 9f, w - 11f, y + 9f, .6f, Fill.Solid(ContinuumTheme.CARD_DIVIDER), .6f)
        }
    }
    override fun describe(s: ContinuumDashboardState) = "Systemstatus: ${s.system.overallSpoken}. CPU-Anteil der App " +
        "${ContinuumDashboardState.percent(s.system.appCpuPercent)}, Netzwerk ${s.system.networkLabel}, Reaktionszeit " +
        ContinuumDashboardState.millis(s.model.latencyMs)
}

/** Wide "Modellstatus" card with mode, context, response time and last update. */
class WideModelCardElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val m = s.model
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h)
        ContinuumSurfaces.iconDisc(p, Icon.BRAIN, 30.5f, 27.3f, 40f, 23f)
        p.drawText("Modellstatus", 62.3f, 24f, t(500, 12.77f), Fill.Solid(ContinuumTheme.TEXT))
        p.drawText(ContinuumSurfaces.fit(p, m.name, t(400, 10.83f), w - 90f), 62.3f, 41.2f, t(400, 10.83f), Fill.Solid(ContinuumTheme.TEXT_SECONDARY))
        ContinuumSurfaces.pill(p, 61f, 50.3f, 19.5f, m.stateLabel, t(500, 8.88f), if (m.online) ContinuumTheme.GREEN else ContinuumTheme.AMBER, minWidth = 56f)
        ContinuumSurfaces.chevron(p, w - 19f, 20f, 14f)
        p.drawText(ModelStatusCardElement.QUALITY_LABEL, 12.3f, 90.3f, t(400, 9.09f), Fill.Solid(Argb.rgb(205, 214, 232)))
        ModelStatusCardElement.drawSegmentBar(p, 65f, 83.8f, 189.6f, 91.6f, m.qualityPercent, phase = motionPhase)
        p.drawText(ContinuumDashboardState.percent(m.qualityPercent), w - 16.2f, 86f, t(500, 17.71f), Fill.Solid(ContinuumTheme.CYAN_SOFT), TextAlign.RIGHT)
        val delta = m.qualityDelta24h
        p.drawText(ContinuumDashboardState.delta24h(delta), 65f, 100.5f, t(500, 7.6f), Fill.Solid(when {
            delta == null || delta == 0 -> ContinuumTheme.TEXT_MUTED; delta > 0 -> ContinuumTheme.GREEN; else -> ContinuumTheme.RED }))
        p.drawLine(11f, 104f, w - 11f, 104f, .7f, Fill.Solid(ContinuumTheme.CARD_DIVIDER))
        val rows = listOf(
            Triple(Icon.CPU, "Modus", m.mode),
            Triple(Icon.MODE, "Kontextfenster", m.context ?: UNKNOWN),
            Triple(Icon.CLOCK, "Reaktionszeit", ContinuumDashboardState.millis(m.latencyMs)),
            Triple(Icon.REFRESH, "Letztes Update", m.lastUpdate ?: UNKNOWN)
        )
        val ys = floatArrayOf(117.5f, 139f, 159f, 176.3f)
        for ((i, row) in rows.withIndex()) {
            ContinuumIcons.draw(p, row.first, 17.9f, ys[i] - 3.4f, 10.5f, Fill.Solid(Argb.rgb(180, 196, 225)), 1.05f)
            p.drawText(row.second, 32.5f, ys[i], t(400, 7.6f), Fill.Solid(Argb.rgb(200, 210, 228)))
            val color = when (i) { 0 -> ContinuumTheme.TEXT; 3 -> ContinuumTheme.TEXT_SECONDARY; else -> ContinuumTheme.CYAN_SOFT }
            val valueStyle = if (i == 0) t(400, 8.94f) else if (i == 3) t(400, 8.08f) else t(500, 9.27f)
            p.drawText(ContinuumSurfaces.fit(p, row.third, valueStyle, w - 120f), w - 16.2f, ys[i], valueStyle, Fill.Solid(color), TextAlign.RIGHT)
        }
    }
    override fun describe(s: ContinuumDashboardState) = ModelStatusCardElement().describe(s)
}

/** Wide "Lernen & Forschung" card with the live marker and four real events. */
class WideLearningCardElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val l = s.learning
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h)
        ContinuumSurfaces.iconDisc(p, Icon.BOOK, 22.7f, 22.7f, 36f, 20f)
        p.drawText("Lernen & Forschung", 48.7f, 24.7f, t(500, 11.08f), Fill.Solid(ContinuumTheme.TEXT))
        // "Live" only while a learning job really runs; otherwise "Ruhe".
        val liveLabel = if (l.live) "Live" else "Ruhe"
        p.fillRoundRect(186.4f, 11f, 240.3f, 30.5f, 9.7f, Fill.Solid(Argb.rgb(12, 26, 56)))
        p.strokeRoundRect(186.4f, 11f, 240.3f, 30.5f, 9.7f, 1f, Fill.Solid(Argb.rgb(52, 80, 140)))
        p.drawText(liveLabel, 207.5f - p.measureText(liveLabel, t(500, 8.87f)) / 2f, 24.3f, t(500, 8.87f), Fill.Solid(if (l.live) ContinuumTheme.TEXT else ContinuumTheme.TEXT_MUTED))
        ContinuumIcons.draw(p, Icon.CHEVRON_DOWN, 229f, 21f, 9f, Fill.Solid(Argb.rgb(196, 210, 235)), 1.2f)
        ContinuumSurfaces.chevron(p, w - 17f, 21f, 13f)
        val figure = ContinuumDashboardState.count(l.today)
        val style = t(500, 22.13f)
        p.drawText(figure, 13.6f, 65f, style, ContinuumSurfaces.figureFill(48f, 65f))
        val lx = 13.6f + p.measureText(figure, style) + 7.5f
        p.drawText("Lernereignisse", lx, 53.6f, t(400, 9.03f), Fill.Solid(Argb.rgb(200, 210, 228)))
        p.drawText("heute", lx, 66.6f, t(400, 9.03f), Fill.Solid(Argb.rgb(200, 210, 228)))
        l.changePercent?.let { change ->
            val up = change >= 0
            val color = if (change > 0) ContinuumTheme.GREEN else if (change < 0) ContinuumTheme.RED else ContinuumTheme.TEXT_MUTED
            val label = (if (up) "+" else "") + change + "%"
            p.fillRoundRect(210f, 42f, 256f, 58.4f, 6f, Fill.Solid(Argb.withAlpha(color, .16f)))
            ContinuumIcons.draw(p, if (up) Icon.ARROW_UP else Icon.CHEVRON_DOWN, 219f, 50.2f, 8.5f, Fill.Solid(color), 1.3f)
            p.drawText(label, 226.5f, 53.6f, t(500, 9.07f), Fill.Solid(color))
        }
        WaveChart.draw(p, 13f, 60f, 256f, 107f, l.series, phase = motionPhase)
        val ys = floatArrayOf(122f, 138.3f, 155.2f, 172f)
        if (l.recent.isEmpty()) p.drawText("Heute noch keine Lernereignisse.", 13f, 124f, t(400, 8.04f), Fill.Solid(ContinuumTheme.TEXT_MUTED))
        for ((i, e) in l.recent.take(4).withIndex()) {
            EventDot.draw(p, 17.9f, ys[i] - 3f, e.kind)
            val timeStyle = t(400, 8.01f)
            val timeW = p.measureText(e.ago, timeStyle)
            p.drawText(e.ago, 255.8f, ys[i], timeStyle, Fill.Solid(ContinuumTheme.TEXT_MUTED), TextAlign.RIGHT)
            p.drawText(ContinuumSurfaces.fit(p, e.title, t(400, 8.04f), 255.8f - 29.2f - timeW - 10f), 29.2f, ys[i], t(400, 8.04f),
                Fill.Solid(Argb.rgb(205, 214, 232)))
        }
    }
    override fun describe(s: ContinuumDashboardState) = LearningCardElement().describe(s)
}

/** Wide "Speicher & Quellen" card. */
class WideStorageCardElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val st = s.storage
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h)
        ContinuumSurfaces.iconDisc(p, Icon.DATABASE, 23.4f, 20.8f, 36f, 20f)
        p.drawText("Speicher & Quellen", 46f, 25.3f, t(500, 11.08f), Fill.Solid(ContinuumTheme.TEXT))
        ContinuumSurfaces.chevron(p, w - 19.5f, 20.8f, 13f)
        Donut.draw(p, 55.2f, 95.4f, 41f, 9f, st.usedPercent, st.segments)
        p.drawText(ContinuumDashboardState.percent(st.usedPercent), 55.2f, 99.5f, t(500, 17.4f), Fill.Solid(Argb.rgb(226, 238, 255)), TextAlign.CENTER)
        p.drawText("belegt", 55.2f, 111f, t(400, 8.2f), Fill.Solid(ContinuumTheme.TEXT_SECONDARY), TextAlign.CENTER)
        val ys = floatArrayOf(54.5f, 80.5f, 106.5f, 131.2f)
        for ((i, seg) in st.segments.take(4).withIndex()) {
            p.fillCircle(120f, ys[i] - 3.6f, 3.6f, Fill.Solid(seg.color))
            p.drawText(ContinuumSurfaces.fit(p, ContinuumDashboardState.compactStorageLabel(seg.label), t(400, 8.6f), w - 136f), 129.9f, ys[i], t(400, 8.6f), Fill.Solid(Argb.rgb(205, 214, 232)))
            p.drawText(ContinuumDashboardState.bytes(seg.bytes), 129.9f, ys[i] + 10.6f, t(400, 8.2f), Fill.Solid(Argb.rgb(150, 160, 190)))
        }
        p.drawLine(12f, 147.4f, w - 12f, 147.4f, .7f, Fill.Solid(ContinuumTheme.CARD_DIVIDER))
        ContinuumSurfaces.iconDisc(p, Icon.GLOBE, 24.7f, 165.6f, 24f, 14f, glow = false)
        val sources = st.activeSources?.let { "$it gespeicherte Quellen" } ?: "Quellen: $UNKNOWN"
        p.drawText(sources, 46.8f, 162.3f, t(500, 9.3f), Fill.Solid(ContinuumTheme.TEXT))
        p.drawText(ContinuumSurfaces.fit(p, st.syncLabel, t(400, 7.9f), w - 70f), 46.8f, 174f, t(400, 7.9f), Fill.Solid(ContinuumTheme.TEXT_MUTED))
        ContinuumSurfaces.chevron(p, w - 19.5f, 166f, 13f)
    }
    override fun describe(s: ContinuumDashboardState) = StorageCardElement().describe(s)
}

/** Wide "Entwicklung & Code" card with the real project/task rows. */
class WideDevelopmentCardElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val dv = s.development
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h)
        ContinuumSurfaces.iconDisc(p, Icon.CODE, 26f, 17.5f + 2f, 36f, 20f)
        p.drawText("Entwicklung & Code", 50f, 23.4f, t(500, 11.3f), Fill.Solid(ContinuumTheme.TEXT))
        val pillColor = when (dv.healthy) { true -> ContinuumTheme.GREEN; false -> ContinuumTheme.RED; null -> ContinuumTheme.AMBER }
        ContinuumSurfaces.pill(p, w - 14f, 7.8f, 18.2f, dv.statusLabel, t(500, 8.2f), pillColor, alignRight = true)
        val metrics = listOf(ContinuumDashboardState.count(dv.activeProjects) to "offene Aufgaben",
            ContinuumDashboardState.count(dv.runningTasks) to "laufend", ContinuumDashboardState.count(dv.errors) to "fehlgeschlagen")
        val xs = floatArrayOf(43.5f, 117f, 183f)
        for ((i, m) in metrics.withIndex()) {
            p.drawText(m.first, xs[i], 54.5f, t(500, 19.6f), Fill.Solid(ContinuumTheme.CYAN_SOFT), TextAlign.CENTER)
            p.drawText(m.second, xs[i], 64.9f, t(400, 8f), Fill.Solid(Argb.rgb(196, 208, 228)), TextAlign.CENTER)
        }
        p.drawLine(81f, 36f, 81f, 68f, .7f, Fill.Solid(ContinuumTheme.CARD_DIVIDER))
        p.drawLine(154f, 36f, 154f, 68f, .7f, Fill.Solid(ContinuumTheme.CARD_DIVIDER))
        BarChart.draw(p, 245f, 34f, w - 10f, 69.5f, dv.series, grid = false)
        p.drawLine(11f, 74f, w - 11f, 74f, .7f, Fill.Solid(ContinuumTheme.CARD_DIVIDER), .7f)
        val ys = floatArrayOf(87f, 104.5f, 122f, 140f)
        if (dv.projects.isEmpty()) p.drawText("Keine Aufträge im Backlog.", 19.5f, 90f, t(400, 8.6f), Fill.Solid(ContinuumTheme.TEXT_MUTED))
        val colors = intArrayOf(Argb.rgb(30, 200, 140), Argb.rgb(60, 120, 255), Argb.rgb(255, 140, 60), Argb.rgb(150, 100, 255))
        for ((i, pr) in dv.projects.take(4).withIndex()) {
            val y = ys[i]
            val c = colors[i % colors.size]
            p.fillRoundRect(13.5f, y - 9.5f, 25.5f, y + 2.5f, 3f, Fill.Solid(Argb.withAlpha(c, .35f)))
            p.strokeRoundRect(13.5f, y - 9.5f, 25.5f, y + 2.5f, 3f, .9f, Fill.Solid(c))
            ContinuumIcons.draw(p, Icon.CUBE, 19.5f, y - 3.5f, 8f, Fill.Solid(Argb.rgb(230, 240, 255)), .9f)
            p.drawText(ContinuumSurfaces.fit(p, pr.name, t(500, 8.8f), 98f), 34.4f, y, t(500, 8.8f), Fill.Solid(Argb.rgb(214, 222, 238)))
            val statusColor = when (pr.state) {
                ProjectState.OK, ProjectState.READY -> ContinuumTheme.GREEN
                ProjectState.RUNNING -> Argb.rgb(180, 196, 225)
                ProjectState.WORKING -> Argb.rgb(150, 130, 255)
                ProjectState.FAILED -> ContinuumTheme.RED
                ProjectState.IDLE -> ContinuumTheme.TEXT_MUTED
            }
            val icon = when (pr.state) { ProjectState.RUNNING -> Icon.CLOCK; ProjectState.WORKING -> Icon.REFRESH; else -> Icon.CHECK_CIRCLE }
            ContinuumIcons.draw(p, icon, 138f, y - 3.6f, 10.5f, Fill.Solid(statusColor), 1.2f)
            p.drawText(ContinuumSurfaces.fit(p, pr.status, t(400, 8.6f), w - 149f - 70f), 149f, y, t(400, 8.6f), Fill.Solid(Argb.rgb(180, 192, 214)))
            p.drawText(pr.ago, w - 26f, y, t(400, 8.1f), Fill.Solid(ContinuumTheme.TEXT_MUTED), TextAlign.RIGHT)
            ContinuumSurfaces.chevron(p, w - 16f, y - 3.6f, 9.5f)
        }
    }
    override fun describe(s: ContinuumDashboardState) = DevelopmentCardElement().describe(s) +
        s.development.projects.take(4).joinToString("") { ". ${it.name}: ${it.status}, ${it.ago}" }
}

/**
 * "Systemaktivität": app CPU samples (every 5 s while visible) and four ring gauges: app CPU, GPU (not measurable on
 * Android without root: —), RAM and storage use. Connectivity has no measured level and therefore no gauge.
 */
class SystemActivityElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    private val gaugeTrack = Fill.Solid(Argb.rgb(30, 46, 88))
    private val gaugeGlows = listOf(Argb.rgb(40, 200, 255), Argb.rgb(150, 110, 255), Argb.rgb(255, 90, 150), Argb.rgb(40, 170, 255))
        .map { Fill.Solid(it) }

    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val sy = s.system
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h)
        ContinuumSurfaces.iconDisc(p, Icon.PULSE, 26f, 19.5f, 36f, 20f)
        p.drawText("Systemaktivität", 50f, 23.4f, t(500, 11.3f), Fill.Solid(ContinuumTheme.TEXT))
        ContinuumSurfaces.pill(p, w - 10f, 11f, 18f, "alle 5 s", t(500, 8.2f), ContinuumTheme.CYAN, alignRight = true)
        WaveChart.drawRidge(p, 9.7f, 34f, w - 10f, 73f, sy.activity)
        // Light traces the measured CPU sample curve, never manufactures readings.
        val phase = motionPhase?.takeIf { it.isFinite() }
        if (phase != null && sy.activity.size >= 2 && sy.activity.any { it > 0f }) {
            val maxSample = sy.activity.maxOrNull()?.coerceAtLeast(1e-6f) ?: 1e-6f
            val (mx, my) = WaveChart.markerOnCurve(9.7f, 34f, w - 10f, 73f,
                sy.activity, maxSample, phase)
            p.fillCircle(mx, my, 4f, Fill.Solid(Argb.rgb(100, 180, 255)), .2f)
            p.fillCircle(mx, my, 1.8f, Fill.Solid(Argb.rgb(220, 246, 255)), .9f)
        }
        val gauges = listOf(
            Triple("CPU", sy.appCpuPercent, intArrayOf(Argb.rgb(40, 200, 255), Argb.rgb(60, 140, 255))),
            Triple("GPU", null as Int?, intArrayOf(Argb.rgb(150, 110, 255), Argb.rgb(190, 120, 255))),
            Triple("RAM", sy.memoryPercent, intArrayOf(Argb.rgb(255, 90, 150), Argb.rgb(170, 90, 255))),
            Triple("Speicher", sy.storagePercent, intArrayOf(Argb.rgb(40, 170, 255), Argb.rgb(60, 220, 255)))
        )
        val xs = floatArrayOf(42.2f, 120.1f, 198f, 278.6f).map { it * w / 324.7f }
        for ((i, g) in gauges.withIndex()) {
            val cx = xs[i]; val cy = 112f; val rr = 21.5f
            p.strokeCircle(cx, cy, rr, 6.5f, gaugeTrack)
            g.second?.let { v ->
                // measured share only; the glow is additive light around the same arc
                val sweep = 360f * v.coerceIn(0, 100) / 100f
                p.setAdditive(true)
                p.strokeArc(cx - rr, cy - rr, cx + rr, cy + rr, -90f, sweep, 12f, gaugeGlows[i], .2f, roundCap = false)
                p.setAdditive(false)
                p.strokeArc(cx - rr, cy - rr, cx + rr, cy + rr, -90f, sweep, 6.5f,
                    Fill.Sweep(cx, cy, intArrayOf(g.third[1], g.third[0], g.third[1])), roundCap = false)
            }
            p.drawText(ContinuumDashboardState.percent(g.second), cx, cy + 4f, t(500, 11.2f), Fill.Solid(Argb.rgb(226, 238, 255)), TextAlign.CENTER)
            p.drawText(g.first, cx, 145.4f, t(400, 8.4f), Fill.Solid(Argb.rgb(200, 210, 228)), TextAlign.CENTER)
        }
    }
    override fun describe(s: ContinuumDashboardState) = "Systemaktivität, Messung alle 5 Sekunden: App-CPU ${ContinuumDashboardState.percent(s.system.appCpuPercent)}, " +
        "GPU nicht messbar, Arbeitsspeicher ${ContinuumDashboardState.percent(s.system.memoryPercent)}, " +
        "Gerätespeicher ${ContinuumDashboardState.percent(s.system.storagePercent)}"
}
