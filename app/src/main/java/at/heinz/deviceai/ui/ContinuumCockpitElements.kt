package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumDashboardState.Companion.UNKNOWN
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import at.heinz.deviceai.ui.ContinuumIcons.Icon

/** Brand fitted to enlarged type without changing the reference header. */
class AdaptiveHeaderBrandElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val logoWidth = 44f
        ContinuumIcons.logo(p, 24f, h / 2f, logoWidth, 24f)
        val x = 56f
        val title = t(600, 19f)
        p.drawText(ContinuumSurfaces.fit(p, "CONTINUUM", title, w - x - 4f), x,
            h / 2f + 1f, title, solid(ContinuumTheme.TEXT))
        val subtitle = t(400, 10f)
        p.drawText(ContinuumSurfaces.fit(p, "Dein lokaler KI-Assistent", subtitle, w - x - 4f), x,
            h - 7f, subtitle, solid(ContinuumTheme.TEXT_SECONDARY))
    }
    override fun describe(s: ContinuumDashboardState) = "Continuum. Dein lokaler KI-Assistent. Cockpit öffnen."

}

/** Glass tiles consume the existing dashboard state; animation never manufactures activity. */
class CockpitActionTile(
    private val icon: Icon,
    private val label: String,
    private val status: (ContinuumDashboardState) -> String,
    textScale: Float
) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        cockpitGlass(p, w, h)
        ContinuumIcons.draw(p, icon, w / 2f, 23f, 23f, solid(ContinuumTheme.CYAN_SOFT), 1.5f)
        // Two bounded lines keep long German labels readable without reducing the chosen font size.
        val style = t(500, 14f)
        val lines = cockpitLines(p, label, style, w - 16f, 2)
        val labelBaseline = maxOf(52f, 39f + p.capHeight(style))
        lines.forEachIndexed { i, line ->
            p.drawText(line, w / 2f, labelBaseline + i * 17f * textScale, style, solid(ContinuumTheme.TEXT), TextAlign.CENTER)
        }
        val stateStyle = t(400, 11f)
        p.drawText(ContinuumSurfaces.fit(p, status(s), stateStyle, w - 16f), w / 2f, h - 13f,
            stateStyle, solid(ContinuumTheme.TEXT_SECONDARY), TextAlign.CENTER)
    }

    override fun describe(s: ContinuumDashboardState) = "$label: ${status(s)}"
}

/** One native, accessible card for each existing destination. No placeholder progress or agent counts. */
class CockpitMetricCard(private val slot: Slot, textScale: Float, private val wide: Boolean) : ContinuumElement(textScale) {
    private val title: String get() = when (slot) {
        Slot.MODEL -> "Gehirn & Modell"
        Slot.LEARNING -> "Lernen"
        Slot.STORAGE -> "Gedächtnis"
        Slot.DEVELOPMENT -> "Entwicklung"
        Slot.WORKERS -> "Aufträge"
        Slot.SYSTEM_STATUS -> "System & Gerät"
        Slot.SYSTEM_ACTIVITY -> "Diagnose"
        else -> error("Not a cockpit card: $slot")
    }
    private val icon: Icon get() = when (slot) {
        Slot.MODEL -> Icon.BRAIN
        Slot.LEARNING -> Icon.BOOK
        Slot.STORAGE -> Icon.DATABASE
        Slot.DEVELOPMENT -> Icon.CODE
        Slot.WORKERS -> Icon.USERS
        Slot.SYSTEM_STATUS -> Icon.CPU
        else -> Icon.PULSE
    }

    private fun facts(s: ContinuumDashboardState): List<Pair<String, String>> = when (slot) {
        Slot.MODEL -> listOf("Modell" to s.model.name, "Status" to s.model.stateLabel,
            "Antwortzeit" to ContinuumDashboardState.millis(s.model.latencyMs),
            "Prüffragen" to ContinuumDashboardState.percent(s.model.qualityPercent))
        Slot.LEARNING -> listOf("Ereignisse heute" to ContinuumDashboardState.count(s.learning.today),
            "Lernprozess" to s.core.learning, "Letzter Eintrag" to (s.learning.recent.firstOrNull()?.title ?: UNKNOWN))
        Slot.STORAGE -> listOf("Gerät belegt" to ContinuumDashboardState.percent(s.storage.usedPercent),
            "Aktive Quellen" to ContinuumDashboardState.count(s.storage.activeSources),
            "Synchronisation" to s.storage.syncLabel)
        Slot.DEVELOPMENT -> listOf("Projekte" to ContinuumDashboardState.count(s.development.activeProjects),
            "Aufträge laufen" to ContinuumDashboardState.count(s.development.runningTasks),
            "Fehlschläge" to ContinuumDashboardState.count(s.development.errors))
        Slot.WORKERS -> listOf("Aufträge laufen" to ContinuumDashboardState.count(s.development.runningTasks),
            "Letzter Auftrag" to (s.development.projects.firstOrNull()?.name ?: UNKNOWN),
            "Ergebnis" to (s.development.projects.firstOrNull()?.status ?: UNKNOWN))
        Slot.SYSTEM_STATUS -> listOf("App CPU" to ContinuumDashboardState.percent(s.system.appCpuPercent),
            "RAM belegt" to ContinuumDashboardState.percent(s.system.memoryPercent),
            "Akku" to ContinuumDashboardState.percent(s.system.batteryPercent))
        Slot.SYSTEM_ACTIVITY -> listOf("Netzwerk" to s.system.networkLabel,
            "Offene Hinweise" to ContinuumDashboardState.count(s.system.openIssues),
            "CPU-Messpunkte" to s.system.activity.size.toString())
        else -> emptyList()
    }

    private fun footer(s: ContinuumDashboardState): String = when (slot) {
        Slot.MODEL -> "Kontext: ${s.model.context ?: UNKNOWN}"
        Slot.LEARNING -> "Lernzentrum öffnen"
        Slot.STORAGE -> "Quellen öffnen"
        Slot.DEVELOPMENT -> if (wide) "Auftragsliste öffnen" else "Entwicklungszentrum öffnen"
        Slot.WORKERS -> "Auftragsliste öffnen"
        Slot.SYSTEM_STATUS -> "System öffnen"
        else -> "Diagnose öffnen"
    }

    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        cockpitGlass(p, w, h)
        val pad = 16f
        val heading = t(600, 15f)
        ContinuumIcons.draw(p, icon, 27f, 27f, 22f, solid(ContinuumTheme.CYAN_SOFT), 1.5f)
        p.drawText(ContinuumSurfaces.fit(p, title, heading, w - 68f), 47f, 32f,
            heading, solid(ContinuumTheme.TEXT))
        p.drawLine(pad, 47f, w - pad, 47f, .7f, solid(Argb.of(110, 33, 134, 171)))
        val labelStyle = t(400, 11f)
        val valueStyle = t(500, 14f)
        val rows = facts(s)
        val rowTop = 66f + (textScale - 1f).coerceAtLeast(0f) * 8f
        val rowBottom = h - 48f
        val step = (rowBottom - rowTop) / rows.size
        for ((index, pair) in rows.withIndex()) {
            val y = rowTop + index * step
            // At large type sizes labels and values remain distinct, never compressed with the whole window.
            p.drawText(ContinuumSurfaces.fit(p, pair.first, labelStyle, w - 2 * pad), pad, y,
                labelStyle, solid(ContinuumTheme.TEXT_SECONDARY))
            p.drawText(ContinuumSurfaces.fit(p, pair.second, valueStyle, w - 2 * pad), pad,
                y + 18f * textScale, valueStyle, solid(ContinuumTheme.TEXT))
        }
        val footerY = h - 16f
        p.drawLine(pad, h - 37f, w - pad, h - 37f, .7f, solid(Argb.of(90, 50, 123, 175)))
        val footerStyle = t(400, 11f)
        p.drawText(ContinuumSurfaces.fit(p, footer(s), footerStyle, w - 52f), pad, footerY,
            footerStyle, solid(ContinuumTheme.LABEL_CYAN))
        ContinuumSurfaces.chevron(p, w - 23f, footerY - 4f, 9f)
    }

    override fun describe(s: ContinuumDashboardState): String = buildString {
        append(title).append(". ")
        facts(s).forEach { (label, value) -> append(label).append(": ").append(value).append(". ") }
        if (slot == Slot.MODEL) {
            append("Kontext: ${s.model.context ?: UNKNOWN}. ")
            append("Prüffragen: ${ContinuumDashboardState.percent(s.model.qualityPercent)}. ")
        }
        append(footer(s))
    }
}

private fun cockpitGlass(p: ContinuumPainter, w: Float, h: Float) {
    ContinuumSurfaces.glassCard(p, .5f, .5f, w - .5f, h - .5f, radius = 15f)
    p.drawLine(17f, 1.1f, (w * .65f).coerceAtLeast(18f), 1.1f, 1f,
        Fill.Linear(17f, 0f, w * .65f, 0f, intArrayOf(Argb.of(215, 71, 222, 255), Argb.of(30, 153, 91, 255))))
}

/** Word wrapping with a bounded ellipsis. The full label remains available to TalkBack. */
internal fun cockpitLines(p: ContinuumPainter, value: String, style: TextStyle, width: Float, maxLines: Int): List<String> {
    if (p.measureText(value, style) <= width) return listOf(value)
    val words = value.split(' ')
    val result = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (current.isNotEmpty() && p.measureText(candidate, style) > width && result.size < maxLines - 1) {
            result += current
            current = word
        } else current = candidate
    }
    result += ContinuumSurfaces.fit(p, current, style, width)
    return result
}
