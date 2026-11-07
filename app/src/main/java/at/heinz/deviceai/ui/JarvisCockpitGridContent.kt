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
    const val MODE_LABEL = "JARVIS"
    const val COMPANION = "Dein KI-Begleiter auf dem Gerät"
    const val COMPANION_SHORT = "Dein KI-Begleiter"

    val tileSubtitles: Map<String, List<String>> = mapOf(
        "chat" to listOf("Natürlich", "sprechen und", "nachfragen"),
        "memory" to listOf("Persönlicher", "Kontext und", "Erinnerungen"),
        "logic" to listOf("Überlegen,", "planen und", "Probleme lösen"),
        "research" to listOf("Recherchieren", "und Wissen", "vernetzen"),
        "agents" to listOf("Spezialisierte", "KI-Helfer", "nutzen"),
        "tasks" to listOf("Planen,", "verfolgen", "und erledigen"),
        "web" to listOf("Suchen und", "Informationen", "zusammenfassen"),
        "learning" to listOf("Anpassen,", "verbessern", "und dazulernen"),
        "device" to listOf("Android und Apps", "nach Freigabe", "steuern"),
        "system" to listOf("Zustand, Leistung", "und Modellstatus", "überwachen"))

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

    private val CLOCK_STYLE = Type.title.copy(size = 26f)
    private val sendPath = VectorPath().moveTo(2f, 10f).lineTo(22f, 2f)
        .lineTo(14f, 22f).lineTo(10f, 14f).close()
        .moveTo(10f, 14f).lineTo(22f, 2f)

    /** [style] scaled down (to at most 55 %) so [text] fits [width] in full; unchanged when it already fits. */
    private fun fitting(p: ContinuumPainter, text: String, style: TextStyle, width: Float): TextStyle {
        val natural = p.measureText(text, style)
        if (natural <= width || natural <= 0f || width <= 0f) return style
        return style.copy(size = style.size * (width / natural).coerceAtLeast(.55f))
    }

    /** Breathing room of the brand block inside its box (dp), above and below. */
    const val BRAND_BREATHING_DP = 6f

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
            Slot.INPUT -> input(p, b, s, shell)
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
        val brandStyle = Type.brand.copy(size = if (g != null) 24f else 18f)
        val cap = p.capHeight(brandStyle)
        val cy = brand.centerY
        // Reference uses two brand lines.
        val block = cap * 2f + 3f
        val fitScale = ((brand.height - 2f * BRAND_BREATHING_DP) / block).coerceIn(.6f, 1f)
        p.save(); p.scale(fitScale, fitScale, brand.left, cy)
        val logo = minOf(brand.height * .73f, 48f)
        JarvisReferenceHud.brandMark(p, brand.left, cy - logo / 2f, logo)
        val tx = brand.left + logo + 8f
        // Local coordinates: the box's right edge lies at brand.left + width / fitScale (scaled around brand.left).
        val room = brand.left + (brand.right - brand.left) / fitScale - tx
        val top = cy - block / 2f
        p.drawText(fit(p, "Device AI", brandStyle, room), tx, top + cap, brandStyle, Fill.Solid(Colors.text))
        p.drawText(fit(p, "Continuum", brandStyle, room), tx, top + 2f * cap + 3f, brandStyle, Fill.Solid(Colors.text))
        p.restore()
        g?.areas?.get(Area.DEVICE_STATUS)?.let { clock ->
            JarvisReferenceHud.surface(p, clock, 14f, Colors.cyanDeep, Colors.cyanDeep)
            val date = Date(now)
            val timeText: String
            val dateText: String
            synchronized(clockFormat) {
                // System timezone can change while the process remains alive.
                clockFormat.timeZone = java.util.TimeZone.getDefault()
                dateFormat.timeZone = clockFormat.timeZone
                timeText = clockFormat.format(date); dateText = dateFormat.format(date)
            }
            // Host capture 06.11.: proportional digits made „07:44“ wider than the box and it read „07:…“. Time and
            // date are never shortened – the type shrinks to fit; the baseline stays where the full size puts it.
            val room = clock.width - 18f
            p.drawText(timeText, clock.left + 9f, clock.top + 8f + p.capHeight(CLOCK_STYLE),
                fitting(p, timeText, CLOCK_STYLE, room), Fill.Solid(Colors.text))
            p.drawText(dateText, clock.left + 9f, clock.bottom - 7f,
                fitting(p, dateText, Type.caption, room), Fill.Solid(Colors.textSecondary))
        }
        g?.areas?.get(Area.TAGLINE)?.let { tag ->
            p.drawLine(tag.left, tag.top + 3f, tag.left, tag.bottom - 5f, 1f, Fill.Solid(Colors.cyan), .7f)
            val caption = Type.caption.copy(size = 13f)
            val lineHeight = p.capHeight(caption) + 3f
            val textTop = tag.centerY - lineHeight + 1.5f
            listOf("Deine persönliche KI", "auf deinem Gerät").forEachIndexed { i, line ->
                p.drawText(fit(p, line, caption, tag.width - 18f), tag.left + 14f,
                    textTop + p.capHeight(caption) + i * lineHeight, caption, Fill.Solid(Colors.text))
            }
        }
        g?.areas?.get(Area.MODE_PILL)?.let { mode ->
            val box = Box(mode.left, mode.centerY - 17f, mode.right, mode.centerY + 17f)
            JarvisReferenceHud.surface(p, box, 17f, Colors.cyan, Colors.violetRim)
            JarvisReferenceHud.crown(p, Box(box.left + 9f, box.centerY - 7f, box.left + 25f, box.centerY + 7f))
            val room = box.width - 35f
            val modeStyle = JarvisShellRenderer.shrinkToFit(p, MODE_LABEL, Type.heading.copy(size = 18f), room)
            p.drawText(fit(p, MODE_LABEL, modeStyle, room), box.left + 31f,
                box.centerY + p.capHeight(modeStyle) / 2f, modeStyle, Fill.Solid(Colors.text))
        }
        g?.areas?.get(Area.PRIVACY_PILL)?.let { priv ->
            val box = Box(priv.left, priv.centerY - 16f, priv.right, priv.centerY + 16f)
            JarvisReferenceHud.surface(p, box, 16f, Colors.cyanDeep, Colors.cyanDeep)
            val local = s.presentation.localOnly
            p.fillCircle(box.left + 11f, box.centerY, 4f, Fill.Solid(if (local == true) Colors.ok else Colors.textMuted))
            val text = when (local) { true -> "Lokal · Internet gesperrt"; false -> "Online nach Freigabe"; null -> "Freigaben: —" }
            val caption = JarvisShellRenderer.shrinkToFit(p, text, Type.caption.copy(size = 13f), box.width - 27f)
            p.drawText(fit(p, text, caption, box.width - 27f), box.left + 22f,
                box.centerY + p.capHeight(caption) / 2f, caption, Fill.Solid(Colors.text))
        }
        val actions = shell?.headerActionBoxes().orEmpty()
        actions.forEach { (target, box) ->
            val radius = minOf(20f, box.width * .42f)
            p.strokeCircle(box.centerX, box.centerY, radius, 1f, Fill.Solid(Colors.rim))
            ContinuumIcons.draw(p, when (target) { "notifications" -> Icon.BELL; "settings" -> Icon.GEAR; else -> Icon.DOTS_HORIZONTAL },
                box.centerX, box.centerY, radius * 1.3f, Fill.Solid(Colors.cyanSoft))
            if (target == "notifications" && s.pendingNotifications > 0)
                p.fillCircle(box.centerX + radius * .65f, box.centerY - radius * .65f, 3f, Fill.Solid(Colors.magenta))
        }
    }

    private fun brainHeader(p: ContinuumPainter, b: Box) {
        val radius = minOf(19f, b.height * .25f)
        val cx = b.left + radius + 6f
        val cy = b.top + radius + 10f
        JarvisNeon.halo(p, cx, cy, radius * 1.8f, Colors.cyan, .2f)
        p.strokeCircle(cx, cy, radius, 1.2f, Fill.Solid(Colors.cyanSoft))
        p.drawText("∞", cx, cy + p.capHeight(Type.title) / 2f, Type.title, Fill.Solid(Colors.cyan), TextAlign.CENTER)
        val x = cx + radius + 11f
        val titleStyle = JarvisShellRenderer.shrinkToFit(p, "Continuum",
            Type.title.copy(size = (b.width * .10f).coerceIn(24f, 36f)), b.right - x)
        val titleBaseline = b.top + 6f + p.capHeight(titleStyle)
        p.drawText("Continuum", x, titleBaseline, titleStyle, Fill.Solid(Colors.text))
        val caption = Type.caption.copy(size = 12f)
        p.drawText(fit(p, "Ein Gedächtnis für alle deine Geräte", caption, b.right - x),
            x, titleBaseline + p.capHeight(caption) + 5f, caption, Fill.Solid(Colors.textSecondary))
        p.drawLine(b.left, b.top + 9f, b.left, b.top + 1f, 1f, Fill.Solid(Colors.cyan), .7f)
        p.drawLine(b.left, b.top + 1f, b.left + 12f, b.top + 1f, 1f, Fill.Solid(Colors.cyan), .7f)
        p.drawLine(b.right, b.bottom - 9f, b.right, b.bottom - 1f, 1f, Fill.Solid(Colors.cyan), .7f)
        p.drawLine(b.right, b.bottom - 1f, b.right - 12f, b.bottom - 1f, 1f, Fill.Solid(Colors.cyan), .7f)
        val stripTop = maxOf(cy + 20f, b.bottom - 20f)
        val strip = Box(b.left, stripTop, b.right, b.bottom)
        if (strip.height >= 12f) {
            JarvisReferenceHud.surface(p, strip, 10f, Colors.cyan, Colors.cyanDeep)
            val stripStyle = Type.status.copy(size = 11f)
            p.drawText(fit(p, "Reden · Denken · Forschen · Handeln · Lernen · Wachsen", stripStyle, strip.width - 10f),
                strip.centerX, strip.centerY + p.capHeight(stripStyle) / 2f,
                stripStyle, Fill.Solid(Colors.text), TextAlign.CENTER)
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
        val title = Type.heading.copy(size = (b.width * .095f).coerceIn(16f, 22f))
        val detail = Type.caption.copy(size = 14f)
        val badge = when {
            s.presentation.listening -> "Hört zu"
            s.presentation.speaking -> "Spricht"
            s.presentation.processing -> "Denkt nach"
            s.model.online -> "Bereit"
            else -> s.model.stateLabel
        }
        val height = p.capHeight(title) + p.capHeight(detail) + 7f
        val top = b.centerY - height / 2f
        val x = b.left + 42f
        val room = b.right - x - 23f
        ContinuumIcons.draw(p, Icon.PULSE, b.left + 23f, b.centerY, 23f, Fill.Solid(Colors.cyan))
        p.drawText(fit(p, "JARVIS", title, room), x, top + p.capHeight(title), title, Fill.Solid(Colors.cyan))
        p.drawText(fit(p, badge, detail, room), x, top + height, detail, Fill.Solid(Colors.textSecondary))
        p.fillCircle(b.right - 14f, b.centerY + 8f, 3f,
            Fill.Solid(if (s.presentation.live || s.model.online) Colors.ok else Colors.textMuted))
    }

    private fun input(p: ContinuumPainter, b: Box, s: ContinuumDashboardState, shell: Shell?) {
        val textBox = shell?.inputConversationBox() ?: Box(b.left + 12f, b.top, b.right - 12f, b.bottom)
        val controls = shell?.dialogControlBoxes().orEmpty()
        controls.forEachIndexed { i, box ->
            val active = i == 0 && s.presentation.listening
            val color = if (active) Colors.ok else Colors.cyan
            val radius = minOf(15f, box.width * .36f)
            JarvisNeon.halo(p, box.centerX, box.centerY, radius * 1.5f, color, if (active) .20f else .07f)
            if (i == 0) {
                p.strokeCircle(box.centerX, box.centerY, radius, 1f, Fill.Solid(color))
                microphoneGlyph(p, box.centerX, box.centerY, 17f, color)
            } else {
                p.save(); p.translate(box.centerX - 14f, box.centerY - 14f)
                p.scale(28f / 24f, 28f / 24f)
                p.strokePath(sendPath, 1.5f, Fill.Solid(color))
                p.restore()
            }
        }
        p.drawLine(textBox.left, b.centerY - 14f, textBox.left, b.centerY + 14f,
            .8f, Fill.Solid(Colors.cyanSoft), .55f)
        val room = textBox.width - 18f
        val latest = s.cockpit.dialog.lastOrNull { !it.fromOwner }?.text
            ?.takeIf { it.isNotBlank() && (s.presentation.processing || s.presentation.speaking) }
        val prompt = when {
            room >= 235f -> "Was soll ich für dich erledigen?"
            room >= 140f -> "Was möchtest du tun?"
            else -> "Gespräch öffnen"
        }
        val style = if (latest != null) Type.body else Type.body.copy(size = (b.width * .027f).coerceIn(15f, 23f))
        val lineHeight = p.capHeight(style) + 4f
        val count = ((b.height - 16f) / lineHeight).toInt().coerceIn(1, 2)
        val lines = wrap(p, latest ?: prompt, style, room)
        val visible = lines.take(count).toMutableList()
        if (lines.size > count && visible.isNotEmpty())
            visible[visible.lastIndex] = fit(p, visible.last() + " …", style, room)
        val top = b.centerY - (visible.size * lineHeight - 4f) / 2f
        visible.forEachIndexed { i, line ->
            p.drawText(line, textBox.left + 10f, top + p.capHeight(style) + i * lineHeight,
                style, Fill.Solid(Colors.text))
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
        val room = b.width - 16f
        val titleBase = Type.tileLabel.copy(size = (b.width * .13f).coerceIn(15f, 20f))
        val labelText = if (p.measureText(tile.label, titleBase) > room)
            JarvisTiles.compoundBreaks[tile.label]?.toList()?.joinToString(" ") ?: tile.label else tile.label
        fun fitted(parts: List<String>, style: TextStyle, minimum: Float): TextStyle {
            val widest = parts.maxByOrNull { p.measureText(it, style) }.orEmpty()
            var result = JarvisShellRenderer.shrinkToFit(p, widest, style, room - 1f,
                minScale = minimum * .88f / style.size)
            repeat(6) {
                val measured = p.measureText(widest, result)
                if (measured <= room - 1f) return result
                val size = maxOf(minimum * .88f, result.size * (room - 2f) / measured)
                if (size >= result.size) return result
                result = result.copy(size = size)
            }
            return result
        }
        val labelStyle = fitted(labelText.split(' '), titleBase, 15f)
        val subtitleStyle = fitted(subtitle, Type.subtitle.copy(size = (b.width * .09f).coerceIn(12f, 14f)), 12f)
        val labels = wrap(p, labelText, labelStyle, room)
        val lines = subtitle.flatMap { wrap(p, it, subtitleStyle, room) }
        val labelCap = p.capHeight(labelStyle)
        val subtitleCap = p.capHeight(subtitleStyle)
        val labelDescent = labelCap * .35f
        val subtitleDescent = subtitleCap * .35f
        val labelPitch = labelCap + labelDescent + maxOf(2f, labelCap * .15f)
        val subtitlePitch = subtitleCap + subtitleDescent + maxOf(2f, subtitleCap * .15f)
        val labelHeight = labelCap + (labels.size - 1).coerceAtLeast(0) * labelPitch + labelDescent
        val subtitleHeight = subtitleCap + (lines.size - 1).coerceAtLeast(0) * subtitlePitch + subtitleDescent
        val gap = maxOf(8f, subtitleCap * .4f)
        val titleTop = minOf(b.top + b.height * .46f,
            b.bottom - 12f - labelHeight - gap - subtitleHeight)
        val descriptionTop = maxOf(minOf(b.top + b.height * .63f,
            b.bottom - 12f - subtitleHeight), titleTop + labelHeight + gap)
        val iconGap = maxOf(8f, labelCap * .25f)
        val iconSize = minOf(72f, b.width * .42f, b.height * .28f,
            (titleTop - b.top - 12f - iconGap) / 1.22f).coerceAtLeast(1f)
        val iconY = maxOf(b.top + 12f + iconSize * .5f,
            minOf(b.top + b.height * .22f, titleTop - iconGap - iconSize * .72f))
        JarvisTiles.drawFeatureIcon(p, tile, b.centerX, iconY, iconSize)
        var y = titleTop + labelCap
        labels.forEach { line ->
            p.drawText(line, b.centerX, y, labelStyle, Fill.Solid(Colors.text), TextAlign.CENTER)
            y += labelPitch
        }
        y = descriptionTop + subtitleCap
        lines.forEach { line ->
            p.drawText(line, b.centerX, y, subtitleStyle, Fill.Solid(Colors.textSecondary), TextAlign.CENTER)
            y += subtitlePitch
        }
    }
}
