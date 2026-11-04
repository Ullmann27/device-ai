package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Row
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Tone
import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisDesign.Type
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot

/**
 * Jarvis Kern: compact ref5 content for the Fold7 grid ([JarvisCockpitGrid]). Only #479 tokens (JarvisDesign.Colors /
 * Type / Styles, JarvisNeon.pill). Values only from real sources (CockpitStatus, MemoryEvents → state); where nothing
 * is wired a small "—" or "offline" in the same style. Tile subtitles are descriptions of the function, not data.
 */
object JarvisCockpitGridContent {
    const val NONE = "—"
    private val localModelCard = CockpitLocalModelCard(1f)

    /** ref5 tile subtitles (German), two short lines each. */
    val tileSubtitles: Map<String, List<String>> = mapOf(
        "chat" to listOf("Natürliches KI-Gespräch", "über mehrere Runden"),
        "memory" to listOf("Langzeit-Gedächtnis,", "persönlicher Kontext"),
        "logic" to listOf("Schlussfolgern, planen,", "Probleme lösen"),
        "research" to listOf("Tiefe Recherche", "mit Quellen"),
        "agents" to listOf("Spezialisierte", "KI-Teammitglieder"),
        "learning" to listOf("Anpassen, verbessern,", "neue Fähigkeiten"),
        "device" to listOf("Android-Gerät, Apps", "und System steuern"),
        "system" to listOf("Zustand, Leistung", "und Modellstatus"),
    )

    /** Module column right of the orb (ref5: Memory / Logic / Agents / Learning / Local Model) with status dot. */
    data class Module(val label: String, val icon: Icon, val value: String, val ok: Boolean)

    fun modules(s: ContinuumDashboardState): List<Module> = listOf(
        Module("Gedächtnis", Icon.DATABASE, if (s.memory != null) "aktiv" else "offline", s.memory != null),
        Module("Logik", Icon.ATOM, if (s.model.online) "bereit" else "offline", s.model.online),
        s.cockpit.workers.let { w -> Module("Agenten", Icon.USERS, w?.let { "${it.count { x -> x.running }} aktiv" } ?: NONE, w != null) },
        Module("Lernen", Icon.BOOK, if (s.learning.live) "aktiv" else NONE, s.learning.live),
        Module("Lokales Modell", Icon.CPU, if (s.model.online) "online" else "offline", s.model.online),
    )

    /** Status bar segments (ref5 order). Missing sources → "—". */
    fun statusSegments(s: ContinuumDashboardState): List<Pair<String, Boolean>> {
        val sys = s.system
        val net = when (sys.networkOnline) { true -> "online"; false -> "offline"; null -> NONE }
        fun pct(v: Int?) = v?.let { "$it %" } ?: NONE
        val temp = sys.temperatureC?.let { String.format(java.util.Locale.GERMANY, "%.0f °C", it) } ?: NONE
        return listOf(
            "Systemstatus: ${sys.overall}" to (sys.openIssues == 0),
            "Netz: $net" to (sys.networkOnline == true),
            "Lokale KI: ${s.model.stateLabel}" to s.model.online,
            "Sync: ${s.storage.syncLabel}" to false,
            "CPU ${pct(sys.appCpuPercent)} | RAM ${pct(sys.memoryPercent)} | $temp" to false,
        )
    }

    fun title(slot: Slot, s: ContinuumDashboardState): String = when (slot) {
        Slot.ROW_AGENTS -> ContinuumCockpitPresentation.agentsTitle(s.cockpit)
        Slot.ROW_TASKS -> "Aktueller Ablauf"
        Slot.ROW_MEMORY -> "Gedächtnis-Aktivität"
        Slot.ROW_MODEL -> "Lokales Modell"
        else -> ""
    }

    private fun icon(slot: Slot) = when (slot) {
        Slot.ROW_AGENTS -> Icon.USERS; Slot.ROW_TASKS -> Icon.CHECK_CIRCLE; Slot.ROW_MEMORY -> Icon.DATABASE; else -> Icon.CPU
    }

    fun rows(slot: Slot, s: ContinuumDashboardState, now: Long): List<Row> = when (slot) {
        Slot.ROW_AGENTS -> ContinuumCockpitPresentation.agentRows(s.cockpit)
        Slot.ROW_TASKS -> ContinuumCockpitPresentation.processRows(s.cockpit)
        Slot.ROW_MEMORY -> ContinuumCockpitPresentation.memoryRows(s, now)
        Slot.ROW_MODEL -> ContinuumCockpitPresentation.brainRows(s)
        else -> emptyList()
    }

    private fun tone(t: Tone) = when (t) {
        Tone.OK -> Colors.ok; Tone.WARN -> Colors.warn; Tone.ERROR -> Colors.error; Tone.ACTIVE -> Colors.cyan
        Tone.NEUTRAL -> Colors.textSecondary
    }

    private fun fit(p: ContinuumPainter, t: String, st: TextStyle, w: Float) = JarvisShellRenderer.fit(p, t, st, w)

    /** Draws [slot] in the ref5 grid; false → caller falls back to its own drawing. */
    fun draw(p: ContinuumPainter, slot: Slot, b: Box, grid: JarvisCockpitGrid.Grid, s: ContinuumDashboardState, now: Long): Boolean {
        when (slot) {
            Slot.TOP_BAR -> header(p, grid, s)
            Slot.COCKPIT_SECONDARY -> modulesPanel(p, b, s)
            // Integration (#486): ref5 Active Agents / Current Task Flow / status bar.
            Slot.ROW_AGENTS -> JarvisCockpitPanels.drawAgents(p, b, s)
            Slot.ROW_TASKS -> JarvisCockpitPanels.drawTaskFlow(p, b, s)
            Slot.ROW_MEMORY -> JarvisMemoryActivityPanel.draw(p, b.left, b.top, b.right, b.bottom,
                MemoryActivitySnapshot.from(s.memory, null))
            Slot.ROW_MODEL -> {
                p.save()
                p.translate(b.left, b.top)
                localModelCard.draw(p, b.width, b.height, s)
                p.restore()
            }
            Slot.STATUS -> JarvisCockpitPanels.drawStatusBar(p, b, s)
            Slot.DIALOG -> dialog(p, b, s)
            Slot.USER_CARD -> userCard(p, b)
            else -> return false
        }
        return true
    }

    private fun header(p: ContinuumPainter, g: JarvisCockpitGrid.Grid, s: ContinuumDashboardState) {
        val brand = g[Area.BRAND]
        val logo = minOf(brand.height * .62f, 26f)
        val lx = brand.left + logo / 2f; val cy = brand.top + brand.height / 2f
        ContinuumIcons.draw(p, Icon.MODE, lx, cy, logo, Fill.Solid(Colors.cyan))
        val tx = brand.left + logo + 8f; val room = brand.right - tx
        val capB = p.capHeight(Type.brand); val capC = p.capHeight(Type.caption)
        val top = cy - (capB * 2 + 6f + capC + 5f) / 2f
        JarvisNeon.text(p, fit(p, "Device AI", Type.brand, room), tx, top + capB, Type.brand, Colors.text)
        JarvisNeon.text(p, fit(p, "Continuum", Type.brand, room), tx, top + capB * 2 + 6f, Type.brand, Colors.text)
        p.drawText(fit(p, "Android-KI-Kontrollzentrum", Type.caption, room), tx, top + capB * 2 + 11f + capC, Type.caption,
            Fill.Solid(Colors.cyanSoft))
        val pillH = JarvisDesign.Styles.pill.height
        val mode = g[Area.MODE_PILL]
        JarvisNeon.pill(p, mode.left, mode.top + (mode.height - pillH) / 2f, "JARVIS-Modus", rim = Colors.violetRim)
        val priv = g[Area.PRIVACY_PILL]
        JarvisNeon.pill(p, priv.left, priv.top + (priv.height - pillH) / 2f, "${s.model.mode} · Privat · On-Device",
            rim = if (s.model.online) Colors.ok else Colors.cyanDeep)
        val icons = g[Area.ICONS]
        val d = minOf(icons.height, (icons.width - 8f) / 3f)
        listOf(Icon.BELL, Icon.GEAR, Icon.DOTS_HORIZONTAL).forEachIndexed { i, ic ->
            val icy = icons.top + icons.height / 2f
            val x = icons.right - d / 2f - (2 - i) * (d + 4f)
            p.strokeCircle(x, icy, d / 2f, 1f, Fill.Solid(Colors.rim))
            ContinuumIcons.draw(p, ic, x, icy, d * .55f, Fill.Solid(Colors.cyanSoft))
            if (ic == Icon.BELL && s.pendingNotifications > 0) p.fillCircle(x + d * .3f, icy - d * .3f, 2.5f, Fill.Solid(Colors.magenta))
        }
        brainHeader(p, g)
    }

    /** Static identity above the original brain sphere; no model availability or invented activity. */
    private fun brainHeader(p: ContinuumPainter, g: JarvisCockpitGrid.Grid) {
        val b = g[Area.ORB]
        val y = b.top + 14f * g.unit
        val cx = b.left + 16f
        JarvisNeon.halo(p, cx, y + 5f, 22f, Colors.cyan, .15f)
        p.strokeCircle(cx, y + 5f, 14f, 1.2f, Fill.Solid(Colors.cyanSoft))
        p.drawText("∞", cx, y + 5f + p.capHeight(Type.title) / 2f,
            Type.title, Fill.Solid(Colors.cyan), TextAlign.CENTER)
        val x = b.left + 39f
        val room = b.right - x
        p.drawText(fit(p, "Continuum", Type.title, room), x, y + p.capHeight(Type.title),
            Type.title, Fill.Solid(Colors.text))
        p.drawText(fit(p, "Dein persönlicher KI-Begleiter", Type.caption, room), x,
            y + p.capHeight(Type.title) + 5f + p.capHeight(Type.caption), Type.caption,
            Fill.Solid(Colors.cyanSoft))
        val strip = y + p.capHeight(Type.title) + 13f + p.capHeight(Type.caption)
        p.fillRoundRect(b.left, strip, b.right, strip + 17f, 8.5f, Fill.Solid(Colors.pillSurface))
        p.strokeRoundRect(b.left, strip, b.right, strip + 17f, 8.5f, .6f, Fill.Solid(Colors.cyanDeep))
        p.drawText(fit(p, "Chat · Denken · Recherche · Handeln · Lernen", Type.status, b.width - 14f),
            b.centerX, strip + (17f + p.capHeight(Type.status)) / 2f,
            Type.status, Fill.Solid(Colors.textSecondary), TextAlign.CENTER)
    }

    private fun panelTitle(p: ContinuumPainter, b: Box, text: String, icon: Icon?): Float {
        val cap = p.capHeight(Type.heading)
        val y = b.top + 8f + cap
        var x = b.left + 9f
        if (icon != null) { ContinuumIcons.draw(p, icon, x + 6f, y - cap / 2f, 12f, Fill.Solid(Colors.cyanSoft)); x += 17f }
        p.drawText(fit(p, text, Type.heading, b.right - 9f - x), x, y, Type.heading, Fill.Solid(Colors.text))
        return y
    }

    private fun modulesPanel(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        val list = modules(s)
        val rowH = (b.height - 8f) / list.size
        val st = Type.caption; val cap = p.capHeight(st)
        list.forEachIndexed { i, m ->
            val cy = b.top + 4f + rowH * i + rowH / 2f
            ContinuumIcons.draw(p, m.icon, b.left + 12f, cy, 12f, Fill.Solid(Colors.cyanSoft))
            val valueW = p.measureText(m.value, st)
            val dotX = b.right - 9f - valueW - 7f
            p.drawText(fit(p, m.label, st, dotX - 6f - (b.left + 22f)), b.left + 22f, cy + cap / 2f, st, Fill.Solid(Colors.text))
            p.fillCircle(dotX, cy, 2.5f, Fill.Solid(if (m.ok) Colors.ok else Colors.textMuted))
            p.drawText(m.value, b.right - 9f, cy + cap / 2f, st, Fill.Solid(if (m.ok) Colors.okSoft else Colors.textSecondary), TextAlign.RIGHT)
        }
    }

    private fun rowPanel(p: ContinuumPainter, slot: Slot, b: Box, s: ContinuumDashboardState, now: Long) {
        val y0 = panelTitle(p, b, title(slot, s), icon(slot))
        if (slot == Slot.ROW_MODEL) {
            val label = if (s.model.online) "Online" else "Offline"
            val w = JarvisNeon.pillWidth(p, label, false)
            if (w < b.width * .4f) JarvisNeon.pill(p, b.right - 8f - w, b.top + 5f, label, rim = if (s.model.online) Colors.ok else Colors.cyanDeep)
        }
        val st = Type.caption; val cap = p.capHeight(st)
        val pitch = cap + 6f
        val room = ((b.bottom - 6f - (y0 + 8f)) / pitch).toInt().coerceAtLeast(0)
        rows(slot, s, now).take(room).forEachIndexed { i, row ->
            val y = y0 + 8f + pitch * (i + 1) - 2f
            p.fillCircle(b.left + 12f, y - cap / 2f, 2.2f, Fill.Solid(tone(row.state)))
            val valueW = if (row.value.isEmpty()) 0f else minOf(p.measureText(row.value, st), b.width * .5f)
            p.drawText(fit(p, row.label, st, b.width - 30f - valueW), b.left + 19f, y, st, Fill.Solid(Colors.text))
            if (row.value.isNotEmpty()) p.drawText(fit(p, row.value, st, b.width * .5f), b.right - 9f, y, st,
                Fill.Solid(tone(row.state)), TextAlign.RIGHT)
        }
    }

    private fun statusBar(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        val segs = statusSegments(s)
        val w = b.width / segs.size; val st = Type.status; val cy = b.top + b.height / 2f
        segs.forEachIndexed { i, (text, ok) ->
            val l = b.left + i * w
            if (i > 0) p.drawLine(l, b.top + 6f, l, b.bottom - 6f, 1f, Fill.Solid(Colors.rim))
            var x = l + 10f
            if (i == 0) { p.fillCircle(x + 2f, cy, 3f, Fill.Solid(if (ok) Colors.ok else Colors.textMuted)); x += 10f }
            p.drawText(fit(p, text, st, l + w - 6f - x), x, cy + p.capHeight(st) / 2f, st,
                Fill.Solid(if (i == 0 && ok) Colors.okSoft else Colors.textSecondary))
        }
    }

    private fun dialog(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        val capT = p.capHeight(Type.heading)
        ContinuumIcons.draw(p, Icon.PULSE, b.left + 15f, b.top + 9f + capT / 2f, 16f, Fill.Solid(Colors.cyan))
        JarvisNeon.text(p, "JARVIS", b.left + 28f, b.top + 9f + capT, Type.heading, Colors.cyan)
        p.drawText(fit(p, "Dein On-Device-KI-Begleiter", Type.caption, b.width - 36f), b.left + 28f, b.top + 9f + capT + 5f + p.capHeight(Type.caption),
            Type.caption, Fill.Solid(Colors.textSecondary))
        val st = Type.caption; val cap = p.capHeight(st); val pitch = cap + 6f
        var y = b.top + 9f + capT + 5f + p.capHeight(Type.caption) + 12f
        for (row in ContinuumCockpitPresentation.dialogRows(s.cockpit)) {
            val text = if (row.value.isEmpty()) row.label else row.value
            val color = if (row.state == Tone.ACTIVE) Colors.text else Colors.textSecondary
            for (line in wrap(p, text, st, b.width - 20f)) {
                if (y + cap > b.bottom - 6f) return
                y += pitch; p.drawText(line, b.left + 10f, y, st, Fill.Solid(color))
            }
        }
    }

    private fun userCard(p: ContinuumPainter, b: Box) {
        if (at.heinz.deviceai.cockpit.JarvisUserCardSlot.renderer != null) return // the registered view draws itself
        JarvisOwnerCardContent.draw(p, b, at.heinz.deviceai.cockpit.JarvisUserCardSlot.latest)
    }

    /** Greedy word wrap. */
    fun wrap(p: ContinuumPainter, text: String, st: TextStyle, w: Float): List<String> {
        val out = mutableListOf<String>(); var line = ""
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next, st) <= w || line.isEmpty()) line = next else { out += line; line = word }
        }
        if (line.isNotEmpty()) out += line
        return out.map { fit(p, it, st, w) }
    }

    /** Tile content inside the already drawn tile card (ref5: icon left, title, two subtitle lines, chevron). */
    fun drawTile(p: ContinuumPainter, b: Box, tile: JarvisTile, subtitle: List<String>) {
        val st = JarvisDesign.Styles.tile
        val iconSize = minOf(st.iconSize, b.height * .5f)
        val ix = b.left + 12f + iconSize / 2f; val cy = b.top + b.height / 2f
        val accent = if (tile.target in setOf("memory", "agents")) Colors.violetRim else Colors.cyan
        JarvisNeon.halo(p, ix, cy, iconSize * .85f, accent, .20f)
        if (!JarvisRailIcons.draw(p, tile.target, ix - iconSize / 2f, cy - iconSize / 2f, iconSize, Colors.iconCore))
            ContinuumIcons.draw(p, tile.icon, ix, cy, iconSize, Fill.Solid(Colors.iconCore))
        val x = ix + iconSize * .5f + 10f; val room = b.right - 18f - x
        val lines = subtitle.take(2)
        val capT = p.capHeight(Type.tileLabel)
        val lh = minOf(Type.subtitleLineHeight, (b.height - capT - 8f) / maxOf(1, lines.size))
        var y = cy - (capT + lines.size * lh) / 2f + capT
        p.drawText(fit(p, tile.label, Type.tileLabel, room), x, y, Type.tileLabel, Fill.Solid(Colors.text))
        lines.forEach { y += lh; p.drawText(fit(p, it, Type.subtitle, room), x, y, Type.subtitle, Fill.Solid(Colors.textSecondary)) }
        ContinuumIcons.draw(p, Icon.CHEVRON_RIGHT, b.right - 9f, b.top + 11f, 8f, Fill.Solid(Colors.textSecondary), 1f)
    }
}
