package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumDashboardState.Companion.UNKNOWN
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import at.heinz.deviceai.ui.ContinuumIcons.Icon

/** Native foreground surfaces for the owner's luminous avatar/core cockpit. */
class ReferenceBrandElement(textScale: Float) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val logoWidth = if (w < 220f) 34f else 44f
        ContinuumIcons.logo(p, logoWidth / 2f + 1f, h / 2f, logoWidth, logoWidth * .52f)
        val x = logoWidth + 10f
        val title = d(600, if (w < 220f) 16.5f else 20f, .5f)
        val detail = t(400, 10.5f)
        val firstY = h / 2f - 2f
        p.drawText(ContinuumSurfaces.fit(p, "CONTINUUM", title, w - x), x, firstY,
            title, solid(ContinuumTheme.TEXT))
        p.drawText(ContinuumSurfaces.fit(p, "Device AI · Dein KI-Begleiter", detail, w - x), x,
            firstY + 16f * textScale, detail, solid(ContinuumTheme.CYAN_SOFT))
    }
    override fun describe(s: ContinuumDashboardState) = "Continuum. Startseite. Dein KI-Begleiter."
}

/** This is an entrance to the real chat composer, deliberately exposed as a button, not an editable field. */
class ReferenceComposerElement(textScale: Float) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        ContinuumSurfaces.glassCard(p, 1f, 1f, w - 1f, h - 1f, minOf(28f, h / 2f))
        val cy = h / 2f
        val light = Fill.Radial(29f, cy, 26f,
            intArrayOf(Argb.of(95, 25, 220, 255), Argb.of(0, 25, 220, 255)))
        p.fillCircle(29f, cy, 26f, light)
        ContinuumIcons.draw(p, Icon.CHAT, 29f, cy, 25f, solid(ContinuumTheme.CYAN_SOFT), 1.7f)
        val title = t(500, 14f)
        val detail = t(400, 10.5f)
        p.drawText(ContinuumSurfaces.fit(p, "Nachricht an Continuum …", title, w - 108f),
            53f, cy - 2f, title, solid(ContinuumTheme.TEXT))
        p.drawText(ContinuumSurfaces.fit(p, "Chat öffnen", detail, w - 108f),
            53f, cy + 15f * textScale, detail, solid(ContinuumTheme.CYAN_SOFT))
        p.strokeCircle(w - 28f, cy, 17f, 1f, solid(ContinuumTheme.CYAN), .7f)
        ContinuumSurfaces.chevron(p, w - 28f, cy, 13f, ContinuumTheme.CYAN_SOFT)
    }
    override fun describe(s: ContinuumDashboardState) = "Nachricht an Continuum. Chat öffnen und Nachricht eingeben."
}

class ReferenceActionElement(private val slot: Slot, textScale: Float) : ContinuumElement(textScale) {
    private val title: String get() = when (slot) {
        Slot.LABEL_THINKING -> "Chat"
        Slot.ACTION_MEMORY -> "Gedächtnis"
        Slot.LABEL_ANALYSING -> "Forschung"
        Slot.LABEL_DEVELOPING -> "Entwicklung"
        Slot.ACTION_AGENTS -> "Agenten"
        Slot.LABEL_LEARNING -> "Lernen"
        Slot.ACTION_DEVICE -> "Gerät"
        Slot.LABEL_CONNECTED -> "Internet"
        else -> error("Not a cockpit action: $slot")
    }
    private val icon: Icon get() = when (slot) {
        Slot.LABEL_THINKING -> Icon.CHAT
        Slot.ACTION_MEMORY -> Icon.DATABASE
        Slot.LABEL_ANALYSING -> Icon.SEARCH
        Slot.LABEL_DEVELOPING -> Icon.CODE
        Slot.ACTION_AGENTS -> Icon.USERS
        Slot.LABEL_LEARNING -> Icon.BOOK
        Slot.ACTION_DEVICE -> Icon.GEAR
        else -> Icon.GLOBE
    }
    private fun detail(s: ContinuumDashboardState): String = when (slot) {
        Slot.LABEL_THINKING -> s.core.thinking
        Slot.ACTION_MEMORY -> "Wissen & Quellen"
        Slot.LABEL_ANALYSING -> s.core.analysing
        Slot.LABEL_DEVELOPING -> "Code & Aufträge"
        Slot.ACTION_AGENTS -> "Profile & Rollen"
        Slot.LABEL_LEARNING -> s.core.learning
        Slot.ACTION_DEVICE -> "System & Werkzeuge"
        Slot.LABEL_CONNECTED -> s.core.connected
        else -> UNKNOWN
    }
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        ContinuumSurfaces.glassCard(p, 1f, 1f, w - 1f, h - 1f, 13f)
        val violet = slot == Slot.ACTION_MEMORY || slot == Slot.ACTION_AGENTS
        val accent = if (violet) ContinuumTheme.VIOLET else ContinuumTheme.CYAN_SOFT
        p.fillCircle(25f, h / 2f, 23f, Fill.Radial(25f, h / 2f, 23f,
            intArrayOf(Argb.withAlpha(accent, .20f), Argb.withAlpha(accent, 0f))))
        ContinuumIcons.draw(p, icon, 25f, h / 2f, 27f, solid(accent), 1.55f)
        val titleStyle = t(600, 14f)
        val detailStyle = t(400, 11f)
        val textWidth = (w - 65f).coerceAtLeast(1f)
        val titles = cockpitLines(p, title, titleStyle, textWidth, 2)
        val details = cockpitLines(p, detail(s), detailStyle, textWidth, 2)
        val titleStep = 17f * textScale
        val detailStep = 14f * textScale
        val total = titles.size * titleStep + details.size * detailStep
        var y = (h - total) / 2f + p.capHeight(titleStyle)
        for (line in titles) {
            p.drawText(line, 49f, y, titleStyle, solid(ContinuumTheme.TEXT))
            y += titleStep
        }
        y += 2f
        for (line in details) {
            p.drawText(line, 49f, y, detailStyle, solid(ContinuumTheme.TEXT_SECONDARY))
            y += detailStep
        }
    }
    override fun describe(s: ContinuumDashboardState) = "$title. ${detail(s)}. Öffnen."
}

/** Shows recorded states and measured percentages; unknown values never become a green success indicator. */
class ReferenceMetricCard(private val slot: Slot, textScale: Float) : ContinuumElement(textScale) {
    private val title: String get() = when (slot) {
        Slot.MODEL -> "Gehirn & Modell"
        Slot.STORAGE -> "Gedächtnis & Quellen"
        Slot.LEARNING -> "Lernprotokoll"
        Slot.DEVELOPMENT -> "Entwicklung & Code"
        Slot.WORKERS -> "Aufträge"
        Slot.SYSTEM_STATUS -> "System & Gerät"
        Slot.SYSTEM_ACTIVITY -> "Systemaktivität"
        else -> error("Not a cockpit metric: $slot")
    }
    private val icon: Icon get() = when (slot) {
        Slot.MODEL -> Icon.BRAIN
        Slot.STORAGE -> Icon.DATABASE
        Slot.LEARNING -> Icon.BOOK
        Slot.DEVELOPMENT -> Icon.CODE
        Slot.WORKERS -> Icon.USERS
        Slot.SYSTEM_STATUS -> Icon.CPU
        else -> Icon.PULSE
    }
    private fun facts(s: ContinuumDashboardState): List<Pair<String, String>> = when (slot) {
        Slot.MODEL -> listOf("Modell" to s.model.name, "Status" to s.model.stateLabel,
            "Kontext" to (s.model.context ?: UNKNOWN),
            "Lernkern-Prüffragen" to ContinuumDashboardState.percent(s.model.qualityPercent))
        Slot.STORAGE -> listOf("Gerät belegt" to ContinuumDashboardState.percent(s.storage.usedPercent),
            "Archiv-Einträge" to ContinuumDashboardState.count(s.storage.activeSources),
            "Eigene Dateien" to ContinuumDashboardState.bytes(s.storage.segments.firstOrNull {
                it.label == "App: eigene Dateien" }?.bytes), "Quellenstatus" to s.storage.syncLabel)
        Slot.LEARNING -> listOf("Protokollereignisse heute" to ContinuumDashboardState.count(s.learning.today),
            "Lernprozess" to s.core.learning,
            "Letzter Eintrag" to (s.learning.recent.firstOrNull()?.title ?: UNKNOWN))
        Slot.DEVELOPMENT, Slot.WORKERS -> listOf("Aufträge offen" to ContinuumDashboardState.count(s.development.activeProjects),
            "Laufend" to ContinuumDashboardState.count(s.development.runningTasks),
            "Fehlgeschlagen" to ContinuumDashboardState.count(s.development.errors))
        Slot.SYSTEM_STATUS -> listOf("App-CPU" to ContinuumDashboardState.percent(s.system.appCpuPercent),
            "RAM belegt" to ContinuumDashboardState.percent(s.system.memoryPercent),
            "Akku" to ContinuumDashboardState.percent(s.system.batteryPercent))
        Slot.SYSTEM_ACTIVITY -> listOf("Netzwerk" to s.system.networkLabel,
            "Offene Hinweise" to ContinuumDashboardState.count(s.system.openIssues),
            "CPU-Messpunkte" to s.system.activity.size.toString())
        else -> emptyList()
    }

    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        ContinuumSurfaces.glassCard(p, 1f, 1f, w - 1f, h - 1f, 14f)
        val header = 44f + (textScale - 1f).coerceAtLeast(0f) * 28f
        val titleStyle = t(600, 13f)
        ContinuumIcons.draw(p, icon, 24f, header / 2f + 1f, 21f,
            solid(ContinuumTheme.CYAN_SOFT), 1.4f)
        p.drawText(ContinuumSurfaces.fit(p, title, titleStyle, w - 65f), 43f,
            header / 2f + p.capHeight(titleStyle) / 2f, titleStyle, solid(ContinuumTheme.TEXT))
        ContinuumSurfaces.chevron(p, w - 14f, header / 2f, 9f)
        p.drawLine(14f, header, w - 14f, header, .7f, solid(ContinuumTheme.CARD_DIVIDER))
        when (slot) {
            Slot.SYSTEM_STATUS -> system(p, w, h, header, s)
            Slot.WORKERS, Slot.DEVELOPMENT -> tasks(p, w, h, header, s)
            Slot.LEARNING -> learning(p, w, h, header, s)
            Slot.SYSTEM_ACTIVITY -> activity(p, w, h, header, s)
            else -> rows(p, w, h, header, facts(s))
        }
    }

    private fun rows(p: ContinuumPainter, w: Float, h: Float, header: Float, rows: List<Pair<String, String>>) {
        val label = t(400, 10.5f)
        val value = t(500, 12f)
        val step = (h - header - 22f) / rows.size
        for ((index, pair) in rows.withIndex()) {
            val y = header + 15f * textScale + index * step
            p.drawText(ContinuumSurfaces.fit(p, pair.first, label, w - 28f), 14f, y,
                label, solid(ContinuumTheme.TEXT_SECONDARY))
            p.drawText(ContinuumSurfaces.fit(p, pair.second, value, w - 28f), 14f,
                y + 15f * textScale, value, solid(ContinuumTheme.CYAN_SOFT))
        }
    }

    private fun system(p: ContinuumPainter, w: Float, h: Float, header: Float, s: ContinuumDashboardState) {
        val measurements = listOf("App-CPU" to s.system.appCpuPercent,
            "RAM belegt" to s.system.memoryPercent, "Akku" to s.system.batteryPercent)
        val style = t(400, 11.5f)
        val value = t(600, 12f)
        val step = (h - header - 22f) / measurements.size
        for ((index, pair) in measurements.withIndex()) {
            val y = header + 18f * textScale + index * step
            p.drawText(pair.first, 14f, y, style, solid(ContinuumTheme.TEXT_SECONDARY))
            p.drawText(ContinuumDashboardState.percent(pair.second), w - 14f, y,
                value, solid(ContinuumTheme.CYAN_SOFT), TextAlign.RIGHT)
            val top = y + 6f
            p.fillRoundRect(14f, top, w - 14f, top + 4f, 2f, solid(Argb.rgb(15, 41, 65)))
            pair.second?.coerceIn(0, 100)?.takeIf { it > 0 }?.let { percent ->
                p.fillRoundRect(14f, top, 14f + (w - 28f) * percent / 100f, top + 4f, 2f,
                    Fill.Linear(14f, top, w - 14f, top, intArrayOf(ContinuumTheme.CYAN, ContinuumTheme.VIOLET)))
            }
        }
    }

    private fun tasks(p: ContinuumPainter, w: Float, h: Float, header: Float, s: ContinuumDashboardState) {
        if (w < 300f && textScale > 1.3f) {
            rows(p, w, h, header, facts(s))
            return
        }
        val labels = listOf("offen", "laufend", "Fehler")
        val values = listOf(s.development.activeProjects, s.development.runningTasks, s.development.errors)
        val body = h - header
        val figureY = header + minOf(body * .44f, 60f * textScale)
        for (i in values.indices) {
            val x = 14f + (w - 28f) * (i + .5f) / 3f
            p.drawText(ContinuumDashboardState.count(values[i]), x, figureY,
                t(600, 24f), solid(if (i == 2 && (values[i] ?: 0) > 0) ContinuumTheme.RED else ContinuumTheme.CYAN_SOFT), TextAlign.CENTER)
            p.drawText(labels[i], x, figureY + 20f * textScale,
                t(400, 10.5f), solid(ContinuumTheme.TEXT_SECONDARY), TextAlign.CENTER)
        }
        if (body >= 130f) {
            val last = s.development.projects.firstOrNull()
            val detail = last?.let { "${it.name} · ${it.status}" } ?: "Keine Auftragseinträge vorhanden"
            p.drawText(ContinuumSurfaces.fit(p, detail, t(400, 10.5f), w - 28f), 14f, h - 18f,
                t(400, 10.5f), solid(ContinuumTheme.TEXT_SECONDARY))
        }
    }

    private fun learning(p: ContinuumPainter, w: Float, h: Float, header: Float, s: ContinuumDashboardState) {
        val countY = header + 37f * textScale
        p.drawText(ContinuumDashboardState.count(s.learning.today), 14f, countY,
            t(600, 29f), solid(ContinuumTheme.CYAN_SOFT))
        val style = t(400, 10.5f)
        val caption = cockpitLines(p, "Protokollereignisse heute", style, w - 28f, 2)
        for ((index, line) in caption.withIndex()) {
            p.drawText(line, 14f, countY + (16f + index * 13f) * textScale,
                style, solid(ContinuumTheme.TEXT_SECONDARY))
        }
        val chartTop = countY + (24f + caption.size * 13f) * textScale
        val chartBottom = h - 43f * textScale
        if (chartBottom > chartTop + 8f) WaveChart.draw(p, 14f, chartTop, w - 14f, chartBottom,
            s.learning.series, phase = motionPhase)
        val recent = s.learning.recent.firstOrNull()?.title ?: "Noch keine protokollierten Ereignisse"
        p.drawText(ContinuumSurfaces.fit(p, recent, style, w - 28f), 14f, h - 17f,
            style, solid(ContinuumTheme.TEXT_SECONDARY))
    }

    private fun activity(p: ContinuumPainter, w: Float, h: Float, header: Float, s: ContinuumDashboardState) {
        val style = t(400, 11f)
        p.drawText(ContinuumSurfaces.fit(p, s.system.networkLabel, style, w - 28f), 14f,
            header + 21f * textScale, style, solid(ContinuumTheme.TEXT_SECONDARY))
        val top = header + 38f * textScale
        val bottom = h - 45f * textScale
        if (bottom > top + 8f) WaveChart.draw(p, 14f, top, w - 14f, bottom,
            s.system.activity, phase = motionPhase)
        p.drawText(ContinuumSurfaces.fit(p, "App-CPU · ${s.system.activity.size} Messpunkte", style, w - 28f),
            14f, h - 19f, style, solid(ContinuumTheme.CYAN_SOFT))
    }

    override fun describe(s: ContinuumDashboardState): String = buildString {
        append(title).append(". ")
        facts(s).forEach { (label, value) -> append(label).append(": ").append(value).append(". ") }
        if (slot == Slot.MODEL) append("Prüffragen des Lernkerns, kein Gesamtfortschritt. ")
        if (slot == Slot.STORAGE) append("Gerätebelegung, keine Gedächtniskapazität der KI. ")
        append("Details öffnen.")
    }
}

/** Separate, reachable learning-status destination, not an isolated ninth action-grid tile. */
class ReferenceLearningStatusElement(textScale: Float) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        ContinuumSurfaces.glassCard(p, 1f, 1f, w - 1f, h - 1f, 14f)
        ContinuumIcons.draw(p, Icon.ATOM, 28f, h / 2f, 27f, solid(ContinuumTheme.VIOLET), 1.5f)
        val title = t(600, 13f)
        val detail = t(400, 11.5f)
        p.drawText("Lernstatus", 52f, h / 2f - 3f, title, solid(ContinuumTheme.TEXT))
        p.drawText(ContinuumSurfaces.fit(p, s.core.evolving, detail, w - 80f),
            52f, h / 2f + 16f * textScale, detail, solid(ContinuumTheme.CYAN_SOFT))
        ContinuumSurfaces.chevron(p, w - 19f, h / 2f, 11f)
    }
    override fun describe(s: ContinuumDashboardState) = "Lernstatus. ${s.core.evolving}. Lernübersicht öffnen."
}
