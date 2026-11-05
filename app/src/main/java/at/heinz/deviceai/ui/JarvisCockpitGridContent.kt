package at.heinz.deviceai.ui

import at.heinz.deviceai.chief.TurnPhase
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Row
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Tone
import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisDesign.Type
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Shell
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One presentation for all window profiles. Decoration is separate from real status and native hit targets. */
object JarvisCockpitGridContent {
    const val NONE = "—"
    private val localModelCard = CockpitLocalModelCard(1f)
    /** The owner's interface is German regardless of the system language (e.g. "Mi., 29. Jan."). */
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val dateFormat = SimpleDateFormat("EEE, d. MMM", Locale.GERMANY)
    const val MODE_LABEL = "JARVIS-Modus"
    const val COMPANION = "Dein KI-Begleiter auf dem Gerät"
    const val COMPANION_SHORT = "Dein KI-Begleiter"

    val tileSubtitles: Map<String, List<String>> = mapOf(
        "chat" to listOf("Natürlich sprechen", "und nachfragen"),
        "memory" to listOf("Persönlicher Kontext", "und Erinnerungen"),
        "logic" to listOf("Überlegen, planen", "und Probleme lösen"),
        "research" to listOf("Recherchieren", "mit Quellen"),
        "agents" to listOf("Spezialisierte", "KI-Helfer"),
        "tasks" to listOf("Planen, verfolgen", "und erledigen"),
        "web" to listOf("Suchen und", "zusammenfassen"),
        "learning" to listOf("Anpassen, verbessern", "und dazulernen"),
        "device" to listOf("Android und Apps", "nach Freigabe steuern"),
        "system" to listOf("Zustand, Leistung", "und Modellstatus"))

    data class Module(val label: String, val icon: Icon, val value: String, val ok: Boolean)

    /** A loaded language model is not evidence that a separate reasoning service is initialized. */
    fun modules(s: ContinuumDashboardState): List<Module> = listOf(
        Module("Gedächtnis", Icon.DATABASE, if (s.memory != null) "aktiv" else NONE, s.memory != null),
        Module("Denken", Icon.ATOM, if (s.cockpit.turnPhase == TurnPhase.WORKING) "arbeitet" else NONE,
            s.cockpit.turnPhase == TurnPhase.WORKING),
        s.cockpit.workers.let { workers ->
            val running = workers?.count { it.running }
            Module("Assistenten", Icon.USERS, running?.let { "$it aktiv" } ?: NONE, running != null && running > 0)
        },
        Module("Lernen", Icon.BOOK, if (s.learning.live) "aktiv" else "ruhend", s.learning.live),
        Module("Lokales Modell", Icon.CPU, if (s.model.online) "online" else s.model.stateLabel, s.model.online))

    fun statusSegments(s: ContinuumDashboardState): List<Pair<String, Boolean>> =
        JarvisCockpitPanels.statusSegments(s).map { it.text to it.ok }

    fun title(slot: Slot, s: ContinuumDashboardState): String = when (slot) {
        Slot.ROW_AGENTS -> ContinuumCockpitPresentation.agentsTitle(s.cockpit)
        Slot.ROW_TASKS -> "Aktueller Ablauf"
        Slot.ROW_MEMORY -> JarvisMemoryActivityPanel.TITLE
        Slot.ROW_MODEL -> "Modell und Gerät"
        else -> ""
    }

    fun rows(slot: Slot, s: ContinuumDashboardState, now: Long): List<Row> = when (slot) {
        Slot.ROW_AGENTS -> ContinuumCockpitPresentation.agentRows(s.cockpit)
        Slot.ROW_TASKS -> ContinuumCockpitPresentation.processRows(s.cockpit)
        Slot.ROW_MEMORY -> ContinuumCockpitPresentation.memoryRows(s, now)
        Slot.ROW_MODEL -> ContinuumCockpitPresentation.brainRows(s)
        else -> emptyList()
    }

    private fun fit(p: ContinuumPainter, text: String, style: TextStyle, width: Float) =
        JarvisShellRenderer.fit(p, text, style, width)

    fun draw(p: ContinuumPainter, slot: Slot, b: Box, grid: JarvisCockpitGrid.Grid?,
             s: ContinuumDashboardState, now: Long, shell: Shell? = null): Boolean {
        when (slot) {
            Slot.AVATAR -> Unit // the existing live View owns both render objects; never draw a stand-in behind it
            Slot.TOP_BAR -> header(p, b, grid, shell, s, now)
            Slot.CONTINUUM_HEADER -> brainHeader(p, b)
            Slot.CAMERA_STATUS -> cameraStatus(p, b, s)
            Slot.COCKPIT_SECONDARY -> modulesPanel(p, b, s)
            Slot.ROW_AGENTS -> JarvisCockpitPanels.drawAgents(p, b, s)
            Slot.ROW_TASKS -> JarvisCockpitPanels.drawTaskFlow(p, b, s)
            Slot.ROW_MEMORY -> JarvisMemoryActivityPanel.draw(p, b.left, b.top, b.right, b.bottom,
                s.memoryActivity ?: MemoryActivitySnapshot.from(s.memory, null))
            Slot.ROW_MODEL -> {
                p.save(); p.translate(b.left, b.top)
                localModelCard.draw(p, b.width, b.height, s)
                p.restore()
            }
            Slot.STATUS -> JarvisCockpitPanels.drawStatusBar(p, b, s)
            Slot.DIALOG -> dialog(p, b, s, shell)
            Slot.USER_CARD -> {
                if (at.heinz.deviceai.cockpit.JarvisUserCardSlot.renderer == null)
                    JarvisOwnerCardContent.draw(p, b, at.heinz.deviceai.cockpit.JarvisUserCardSlot.latest)
            }
            else -> return false
        }
        return true
    }

    private fun header(p: ContinuumPainter, b: Box, g: JarvisCockpitGrid.Grid?, shell: Shell?,
                       s: ContinuumDashboardState, now: Long) {
        val brand = g?.areas?.get(Area.BRAND) ?: Box(b.left + 9f, b.top + 4f, b.right - 104f, b.bottom - 4f)
        val logo = minOf(brand.height * .73f, 48f)
        JarvisReferenceHud.brandMark(p, brand.left, brand.top + (brand.height - logo) / 2f, logo)
        val tx = brand.left + logo + 8f
        val room = brand.right - tx
        val brandStyle = Type.brand
        val cap = p.capHeight(brandStyle)
        val caption = Type.caption
        val cy = brand.centerY
        val top = cy - (cap * 2f + p.capHeight(caption) + 9f) / 2f
        p.drawText(fit(p, "Device AI", brandStyle, room), tx, top + cap, brandStyle, Fill.Solid(Colors.text))
        p.drawText(fit(p, "Continuum", brandStyle, room), tx, top + 2f * cap + 3f, brandStyle, Fill.Solid(Colors.text))
        p.drawText(fit(p, "Deine KI-Steuerzentrale", caption, room), tx,
            top + 2f * cap + 9f + p.capHeight(caption), caption, Fill.Solid(Colors.cyanSoft))
        g?.areas?.get(Area.DEVICE_STATUS)?.let { clock ->
            JarvisReferenceHud.surface(p, clock, 14f, Colors.cyanDeep, Colors.cyanDeep)
            val style = Type.title.copy(size = 26f)
            val date = Date(now)
            val timeText: String
            val dateText: String
            synchronized(clockFormat) {
                // System timezone can change while the process remains alive.
                clockFormat.timeZone = java.util.TimeZone.getDefault()
                dateFormat.timeZone = clockFormat.timeZone
                timeText = clockFormat.format(date); dateText = dateFormat.format(date)
            }
            p.drawText(fit(p, timeText, style, clock.width - 18f), clock.left + 9f,
                clock.top + 8f + p.capHeight(style), style, Fill.Solid(Colors.text))
            p.drawText(fit(p, dateText, Type.caption, clock.width - 18f), clock.left + 9f,
                clock.bottom - 7f, Type.caption, Fill.Solid(Colors.textSecondary))
        }
        g?.areas?.get(Area.TAGLINE)?.let { tag ->
            p.drawLine(tag.left, tag.top + 3f, tag.left, tag.bottom - 5f, 1f, Fill.Solid(Colors.cyan), .7f)
            listOf("Deine persönliche KI", "auf deinem Gerät").forEachIndexed { i, line ->
                p.drawText(fit(p, line, Type.caption, tag.width - 18f), tag.left + 17f,
                    tag.top + 5f + p.capHeight(Type.caption) + i * (p.capHeight(Type.caption) + 3f),
                    Type.caption, Fill.Solid(Colors.text))
            }
        }
        g?.areas?.get(Area.MODE_PILL)?.let { mode ->
            val box = Box(mode.left, mode.centerY - 14f, mode.right, mode.centerY + 14f)
            JarvisReferenceHud.surface(p, box, 14f, Colors.cyan, Colors.violetRim)
            JarvisReferenceHud.crown(p, Box(box.left + 9f, box.centerY - 7f, box.left + 25f, box.centerY + 7f))
            val room = box.width - 35f
            val modeStyle = JarvisShellRenderer.shrinkToFit(p, MODE_LABEL, Type.heading, room)
            val mode = if (p.measureText(MODE_LABEL, modeStyle) <= room) MODE_LABEL else "JARVIS"
            p.drawText(fit(p, mode, modeStyle, room), box.left + 31f,
                box.centerY + p.capHeight(modeStyle) / 2f, modeStyle, Fill.Solid(Colors.text))
        }
        g?.areas?.get(Area.PRIVACY_PILL)?.let { priv ->
            val box = Box(priv.left, priv.centerY - 13f, priv.right, priv.centerY + 13f)
            JarvisReferenceHud.surface(p, box, 13f, Colors.cyanDeep, Colors.cyanDeep)
            val local = s.presentation.localOnly
            p.fillCircle(box.left + 11f, box.centerY, 4f, Fill.Solid(if (local == true) Colors.ok else Colors.textMuted))
            val text = when (local) { true -> "Lokal · Internet gesperrt"; false -> "Online nach Freigabe"; null -> "Freigaben: —" }
            p.drawText(fit(p, text, Type.caption, box.width - 27f), box.left + 22f,
                box.centerY + p.capHeight(Type.caption) / 2f, Type.caption, Fill.Solid(Colors.text))
        }
        val actions = shell?.headerActionBoxes().orEmpty()
        actions.forEach { (target, box) ->
            val radius = minOf(16f, box.width * .35f)
            p.strokeCircle(box.centerX, box.centerY, radius, 1f, Fill.Solid(Colors.rim))
            ContinuumIcons.draw(p, when (target) { "notifications" -> Icon.BELL; "settings" -> Icon.GEAR; else -> Icon.DOTS_HORIZONTAL },
                box.centerX, box.centerY, radius * 1.3f, Fill.Solid(Colors.cyanSoft))
            if (target == "notifications" && s.pendingNotifications > 0)
                p.fillCircle(box.centerX + radius * .65f, box.centerY - radius * .65f, 3f, Fill.Solid(Colors.magenta))
        }
    }

    private fun brainHeader(p: ContinuumPainter, b: Box) {
        val radius = minOf(18f, b.height * .26f)
        val cx = b.left + radius + 5f
        val cy = b.top + radius + 3f
        JarvisNeon.halo(p, cx, cy, radius * 1.8f, Colors.cyan, .2f)
        p.strokeCircle(cx, cy, radius, 1.2f, Fill.Solid(Colors.cyanSoft))
        p.drawText("∞", cx, cy + p.capHeight(Type.title) / 2f, Type.title, Fill.Solid(Colors.cyan), TextAlign.CENTER)
        val x = cx + radius + 11f
        p.drawText(fit(p, "Continuum", Type.title, b.right - x), x, cy + 1f,
            Type.title, Fill.Solid(Colors.text))
        p.drawText(fit(p, "Ein Gedächtnis für alle deine Geräte", Type.caption, b.right - x),
            x, cy + p.capHeight(Type.caption) + 7f, Type.caption, Fill.Solid(Colors.textSecondary))
        val stripTop = maxOf(cy + 20f, b.bottom - 20f)
        val strip = Box(b.left, stripTop, b.right, b.bottom)
        if (strip.height >= 12f) {
            JarvisReferenceHud.surface(p, strip, 10f, Colors.cyan, Colors.cyanDeep)
            p.drawText(fit(p, "Reden · Denken · Forschen · Handeln · Lernen · Wachsen", Type.status, strip.width - 10f),
                strip.centerX, strip.centerY + p.capHeight(Type.status) / 2f,
                Type.status, Fill.Solid(Colors.text), TextAlign.CENTER)
        }
    }

    private fun cameraStatus(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        val x = b.left + 22f; val cy = b.centerY
        cameraGlyph(p, x, cy, 19f, Colors.cyanSoft)
        p.drawText(fit(p, "Kamerawahrnehmung", Type.heading, b.width - 58f), b.left + 43f,
            cy - 2f, Type.heading, Fill.Solid(Colors.text))
        val status = when { s.presentation.tracking -> "Gesicht erkannt"; s.presentation.cameraSessionActive -> "Kamera aktiv"; else -> "Kamera aus" }
        p.fillCircle(b.left + 47f, cy + 10f, 3f, Fill.Solid(if (s.presentation.cameraSessionActive) Colors.ok else Colors.textMuted))
        p.drawText(fit(p, status, Type.caption, b.width - 62f), b.left + 55f,
            cy + 10f + p.capHeight(Type.caption) / 2f, Type.caption, Fill.Solid(Colors.textSecondary))
    }

    private fun modulesPanel(p: ContinuumPainter, b: Box, s: ContinuumDashboardState) {
        val list = modules(s); val rowH = (b.height - 8f) / list.size
        val style = Type.caption; val cap = p.capHeight(style)
        list.forEachIndexed { i, m ->
            val cy = b.top + 4f + rowH * (i + .5f)
            if (i > 0) p.drawLine(b.left + 12f, cy - rowH / 2f, b.right - 10f, cy - rowH / 2f, .6f, Fill.Solid(Colors.rim), .65f)
            ContinuumIcons.draw(p, m.icon, b.left + 17f, cy, 18f, Fill.Solid(Colors.cyanSoft))
            val valueWidth = minOf(p.measureText(m.value, style), b.width * .32f)
            val valueStart = b.right - 10f - valueWidth
            p.drawText(fit(p, m.label, style, valueStart - b.left - 47f), b.left + 34f, cy + cap / 2f,
                style, Fill.Solid(Colors.text))
            p.fillCircle(valueStart - 7f, cy, 2.5f, Fill.Solid(if (m.ok) Colors.ok else Colors.textMuted))
            p.drawText(fit(p, m.value, style, valueWidth), b.right - 9f, cy + cap / 2f, style,
                Fill.Solid(if (m.ok) Colors.okSoft else Colors.textSecondary), TextAlign.RIGHT)
        }
    }

    private fun dialog(p: ContinuumPainter, b: Box, s: ContinuumDashboardState, shell: Shell?) {
        val heading = Type.heading.copy(size = 16f)
        val cap = p.capHeight(heading)
        val titleY = b.top + 8f + cap
        ContinuumIcons.draw(p, Icon.PULSE, b.left + 21f, titleY - cap / 2f, 22f, Fill.Solid(Colors.cyan))
        p.drawText("JARVIS", b.left + 39f, titleY, heading, Fill.Solid(Colors.cyan))
        val live = s.presentation.live
        val badge = if (live) "Live" else "Ruhe"
        p.drawText(badge, b.right - 12f, titleY, Type.caption,
            Fill.Solid(if (live) Colors.ok else Colors.textMuted), TextAlign.RIGHT)
        val companionRoom = b.width - 45f
        val companion = if (p.measureText(COMPANION, Type.caption) * .88f <= companionRoom) COMPANION else COMPANION_SHORT
        val companionStyle = JarvisShellRenderer.shrinkToFit(p, companion, Type.caption, companionRoom, minScale = .88f)
        p.drawText(fit(p, companion, companionStyle, companionRoom), b.left + 39f,
            titleY + 4f + p.capHeight(Type.caption), companionStyle, Fill.Solid(Colors.textSecondary))
        val controls = shell?.dialogControlBoxes().orEmpty()
        val controlTop = controls.minOfOrNull { it.top } ?: (b.bottom - 6f)
        val bubble = Box(b.left + 10f, titleY + p.capHeight(Type.caption) + 12f, b.right - 10f, controlTop - 4f)
        if (bubble.height > 18f) {
            JarvisReferenceHud.surface(p, bubble, 10f, Colors.cyan, Colors.cyanDeep)
            val latest = s.cockpit.dialog.lastOrNull { !it.fromOwner }?.text
                ?: s.cockpit.dialog.lastOrNull()?.text ?: "Noch kein Gespräch."
            val lineHeight = p.capHeight(Type.caption) + 4f
            val count = ((bubble.height - 12f) / lineHeight).toInt().coerceAtLeast(1)
            wrap(p, latest, Type.caption, bubble.width - 18f).take(count).forEachIndexed { i, line ->
                p.drawText(line, bubble.left + 9f, bubble.top + 6f + p.capHeight(Type.caption) + i * lineHeight,
                    Type.caption, Fill.Solid(Colors.text))
            }
        }
        controls.forEachIndexed { i, box ->
            val active = (i == 0 && s.presentation.listening) || (i == 1 && s.presentation.cameraSessionActive)
            val color = if (active) Colors.ok else if (i == 2) Colors.violetRim else Colors.cyan
            val radius = minOf(15f, box.width * .36f)
            JarvisNeon.halo(p, box.centerX, box.centerY, radius * 1.7f, color, if (active) .30f else .14f)
            p.strokeCircle(box.centerX, box.centerY, radius, 1f, Fill.Solid(color))
            when (i) {
                0 -> microphoneGlyph(p, box.centerX, box.centerY, 17f, color)
                1 -> cameraGlyph(p, box.centerX, box.centerY, 17f, color)
                else -> ContinuumIcons.draw(p, Icon.ATOM, box.centerX, box.centerY, 19f, Fill.Solid(color))
            }
        }
    }

    private fun microphoneGlyph(p: ContinuumPainter, x: Float, y: Float, size: Float, color: Int) {
        val k = size / 18f; val fill = Fill.Solid(color)
        p.strokeRoundRect(x - 3f*k, y - 8f*k, x + 3f*k, y + 3f*k, 3f*k, 1.3f, fill)
        p.strokeArc(x - 6f*k, y - 3f*k, x + 6f*k, y + 7f*k, 0f, 180f, 1.3f, fill)
        p.drawLine(x, y + 7f*k, x, y + 10f*k, 1.3f, fill)
        p.drawLine(x - 4f*k, y + 10f*k, x + 4f*k, y + 10f*k, 1.3f, fill)
    }

    private fun cameraGlyph(p: ContinuumPainter, x: Float, y: Float, size: Float, color: Int) {
        val k = size / 20f; val fill = Fill.Solid(color)
        p.strokeRoundRect(x - 10f*k, y - 7f*k, x + 10f*k, y + 7f*k, 4f*k, 1.2f, fill)
        p.strokeCircle(x, y, 4f*k, 1.2f, fill)
        p.fillCircle(x, y, 1.7f*k, fill)
    }

    fun wrap(p: ContinuumPainter, text: String, st: TextStyle, w: Float): List<String> {
        val out = mutableListOf<String>(); var line = ""
        for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next, st) <= w || line.isEmpty()) line = next else { out += line; line = word }
        }
        if (line.isNotEmpty()) out += line
        return out.map { fit(p, it, st, w) }
    }

    fun drawTile(p: ContinuumPainter, b: Box, tile: JarvisTile, subtitle: List<String>) {
        val iconSize = minOf(26f, b.height * .5f)
        val ix = b.left + 10f + iconSize / 2f; val cy = b.centerY
        val accent = if (tile.target in setOf("memory", "agents")) Colors.violetRim else Colors.cyan
        JarvisNeon.halo(p, ix, cy, iconSize * .85f, accent, .20f)
        if (!JarvisRailIcons.draw(p, tile.target, ix - iconSize / 2f, cy - iconSize / 2f, iconSize, Colors.iconCore))
            ContinuumIcons.draw(p, tile.icon, ix, cy, iconSize, Fill.Solid(Colors.iconCore))
        val x = ix + iconSize / 2f + 10f; val room = b.right - 7f - x
        val lines = subtitle.take(2)
        val cap = p.capHeight(Type.tileLabel)
        val pitch = p.capHeight(Type.subtitle) + 3f
        var y = cy - (cap + lines.size * pitch) / 2f + cap
        val labelStyle = JarvisShellRenderer.shrinkToFit(p, tile.label, Type.tileLabel, room)
        p.drawText(fit(p, tile.label, labelStyle, room), x, y, labelStyle, Fill.Solid(Colors.text))
        lines.forEach {
            y += pitch
            val style = JarvisShellRenderer.shrinkToFit(p, it, Type.subtitle, room, minScale = .88f)
            p.drawText(fit(p, it, style, room), x, y, style, Fill.Solid(Colors.textSecondary))
        }
    }
}
