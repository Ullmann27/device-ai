package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Row
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Tone
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import at.heinz.deviceai.ui.ContinuumIcons.Icon

/**
 * Drawn elements of the landscape Jarvis cockpit (ref1/ref3). Colours only from [JarvisDesign], glass and glow
 * only from [JarvisNeon] (PR #479). Every value comes from [ContinuumCockpitPresentation]; nothing is sampled.
 */
private val C = JarvisDesign.Colors

private fun toneColor(tone: Tone): Int = when (tone) {
    Tone.NEUTRAL -> C.textSecondary
    Tone.ACTIVE -> C.cyan
    Tone.OK -> C.ok
    Tone.WARN -> C.warn
    Tone.ERROR -> C.error
}

/** "∞ CONTINUUM · Device AI · Lokal" with the JARVIS line. */
class CockpitBrandElement(textScale: Float) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val cy = h / 2f
        JarvisNeon.halo(p, 24f, cy, 24f, C.violet, .25f)
        ContinuumIcons.logo(p, 24f, cy, 40f, 21f)
        val title = d(600, 17f, 2f)
        p.drawText(ContinuumSurfaces.fit(p, "CONTINUUM", title, w - 52f), 50f, cy - 1f, title,
            Fill.Linear(50f, 0f, w, 0f, C.brand))
        val sub = t(400, 10f)
        p.drawText(ContinuumSurfaces.fit(p, "Device AI · JARVIS · Lokal", sub, w - 52f), 50f, cy + 13f, sub, solid(C.textMuted))
    }
    override fun describe(s: ContinuumDashboardState) = "Continuum, Device AI, JARVIS. Zur Startseite."
}

/** One top navigation tab; opens the existing destination of its slot. */
class CockpitTabElement(private val slot: Slot, textScale: Float) : ContinuumElement(textScale) {
    val label: String get() = when (slot) {
        Slot.ACTION_MEMORY -> "Gedächtnis"
        Slot.ACTION_AGENTS -> "Agenten"
        Slot.ACTION_BOTS -> "Bots"
        Slot.ACTION_DEVICE -> "System"
        Slot.LABEL_CONNECTED -> "Netz"
        Slot.LABEL_EVOLVING -> "Lernstand"
        else -> error("not a cockpit tab: $slot")
    }
    private val icon: Icon get() = when (slot) {
        Slot.ACTION_MEMORY -> Icon.DATABASE
        Slot.ACTION_AGENTS -> Icon.USERS
        Slot.ACTION_BOTS -> Icon.CUBE
        Slot.ACTION_DEVICE -> Icon.CPU
        Slot.LABEL_CONNECTED -> Icon.WIFI
        else -> Icon.ATOM
    }
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        ContinuumSurfaces.iconGlow(p, icon, w / 2f, h * .36f, 17f)
        val style = t(500, 10.5f)
        p.drawText(ContinuumSurfaces.fit(p, label, style, w - 4f), w / 2f, h * .82f, style, solid(C.textSecondary), TextAlign.CENTER)
    }
    override fun describe(s: ContinuumDashboardState) = when (slot) {
        Slot.LABEL_CONNECTED -> "Netz: " + s.system.networkLabel + ". Interneteinstellungen öffnen"
        Slot.LABEL_EVOLVING -> "Lernstand: " + s.core.evolving + ". Lernübersicht öffnen"
        else -> "$label öffnen"
    }
}

/** Action bar button (Denken … Überprüfen). Each opens an existing owner-controlled path; nothing executes by itself. */
class CockpitActionElement(private val slot: Slot, textScale: Float) : ContinuumElement(textScale) {
    val label: String get() = when (slot) {
        Slot.LABEL_THINKING -> "Denken"
        Slot.LABEL_ANALYSING -> "Recherchieren"
        Slot.LABEL_LEARNING -> "Lernen"
        Slot.LABEL_DEVELOPING -> "Entwickeln"
        Slot.ACTION_TASK -> "Ausführen"
        Slot.ACTION_VERIFY -> "Überprüfen"
        else -> error("not a cockpit action: $slot")
    }
    private val icon: Icon get() = when (slot) {
        Slot.LABEL_THINKING -> Icon.BRAIN
        Slot.LABEL_ANALYSING -> Icon.SEARCH
        Slot.LABEL_LEARNING -> Icon.BOOK
        Slot.LABEL_DEVELOPING -> Icon.CODE
        Slot.ACTION_TASK -> Icon.BOLT
        else -> Icon.CHECK_CIRCLE
    }
    /** True while the real work this button stands for runs (pulsed by the animation layer). */
    var active = false
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        JarvisNeon.glassCard(p, 1f, 1f, w - 1f, h - 1f, JarvisDesign.Metrics.radiusControl,
            if (active) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.SUBTLE, active)
        ContinuumSurfaces.iconGlow(p, icon, w / 2f, h * .38f, 18f)
        val style = t(500, 10.5f)
        p.drawText(ContinuumSurfaces.fit(p, label, style, w - 6f), w / 2f, h * .82f, style,
            solid(if (active) C.cyan else C.text), TextAlign.CENTER)
    }
    override fun describe(s: ContinuumDashboardState) = when (slot) {
        Slot.LABEL_THINKING -> "Denken: Chat öffnen. " + s.core.thinking
        Slot.LABEL_ANALYSING -> "Recherchieren: Recherchemodus wählen. " + s.core.analysing
        Slot.LABEL_LEARNING -> "Lernen: Lernzentrum öffnen. " + s.core.learning
        Slot.LABEL_DEVELOPING -> "Entwickeln: Entwicklungszentrum öffnen. " + s.core.developing
        Slot.ACTION_TASK -> "Ausführen: Auftrag entwerfen; nichts startet ohne deine Bestätigung"
        else -> "Überprüfen: Diagnose und offene Fehlermeldungen öffnen. " + s.system.overallSpoken
    }
}

/** Shared glass card body: icon + title + optional pill, then rows with optional bars. */
abstract class CockpitCard(textScale: Float, private val icon: Icon) : ContinuumElement(textScale) {
    abstract fun title(s: ContinuumDashboardState): String
    abstract fun rows(s: ContinuumDashboardState): List<Row>
    open fun pill(s: ContinuumDashboardState): Pair<String, Int>? = null
    open val showBars = false
    /** Highlight set by the animation layer for a short pulse; never changes values. */
    var highlighted = false

    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        JarvisNeon.glassCard(p, 1f, 1f, w - 1f, h - 1f, glow = if (highlighted) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.NORMAL,
            active = highlighted)
        ContinuumSurfaces.iconGlow(p, icon, 22f, 22f, 16f)
        val titleStyle = d(600, 13f, .4f)
        var titleRoom = w - 48f
        pill(s)?.let { (text, color) ->
            val pw = ContinuumSurfaces.pill(p, w - 10f, 12f, 20f, text, t(500, 9.5f), color, alignRight = true)
            titleRoom -= pw + 6f
        }
        p.drawText(ContinuumSurfaces.fit(p, title(s), titleStyle, titleRoom), 38f, 27f, titleStyle, solid(C.text))
        drawBody(p, w, h, s, 44f)
    }

    protected open fun drawBody(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState, top: Float) {
        val rows = rows(s)
        val label = t(500, 10.5f)
        val value = t(400, 10.5f)
        val lineH = if (showBars) 28f else 20f
        val visible = ((h - top - 6f) / lineH).toInt().coerceAtLeast(1)
        rows.take(visible).forEachIndexed { i, row ->
            val y = top + i * lineH + 10f
            val color = toneColor(row.state)
            p.fillCircle(16f, y - 3.5f, 3f, solid(color))
            val valueWidth = if (row.value.isEmpty()) 0f else minOf(p.measureText(row.value, value), w * .55f)
            p.drawText(ContinuumSurfaces.fit(p, row.label, label, w - 40f - valueWidth), 26f, y, label, solid(C.text))
            if (row.value.isNotEmpty())
                p.drawText(ContinuumSurfaces.fit(p, row.value, value, w * .55f), w - 12f, y, value, solid(color), TextAlign.RIGHT)
            if (showBars) {
                val by = y + 6f
                p.fillRoundRect(26f, by, w - 12f, by + 4f, 2f, solid(Argb.withAlpha(C.cyan, .12f)))
                row.fraction?.let { f ->
                    p.fillRoundRect(26f, by, 26f + (w - 38f) * f, by + 4f, 2f, Fill.Linear(26f, 0f, w, 0f, C.neonEdge))
                }
            }
        }
    }

    override fun describe(s: ContinuumDashboardState): String =
        title(s) + ". " + rows(s).joinToString(". ") { if (it.value.isEmpty()) it.label else it.label + ": " + it.value }
}

class CockpitBrainCard(textScale: Float) : CockpitCard(textScale, Icon.BRAIN) {
    override fun title(s: ContinuumDashboardState) = "KI-Gehirn"
    override fun pill(s: ContinuumDashboardState) = s.model.stateLabel to (if (s.model.online) C.ok else C.warn)
    override fun rows(s: ContinuumDashboardState) = ContinuumCockpitPresentation.brainRows(s)
    override fun drawBody(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState, top: Float) {
        // Real TurnLifecycle phase rail: Annehmen → Arbeiten → Ergebnis
        val reached = ContinuumCockpitPresentation.phaseIndex(s.cockpit.turnPhase)
        val labels = ContinuumCockpitPresentation.phaseRail
        val step = (w - 40f) / (labels.size - 1)
        val y = top + 6f
        for (i in labels.indices) {
            val x = 20f + i * step
            if (i > 0) JarvisNeon.line(p, x - step + 6f, y, x - 6f, y, if (i <= reached) C.cyan else C.textMuted, 1.2f,
                if (i <= reached) JarvisDesign.Glow.SUBTLE else JarvisDesign.Glow.OFF)
            if (i <= reached) p.fillCircle(x, y, 4.5f, solid(C.cyan)) else p.strokeCircle(x, y, 4.5f, 1.2f, solid(C.textMuted))
            val style = t(400, 9f)
            p.drawText(labels[i], x, y + 16f, style, solid(if (i == reached) C.cyan else C.textMuted),
                when (i) { 0 -> TextAlign.LEFT; labels.size - 1 -> TextAlign.RIGHT; else -> TextAlign.CENTER }.let {
                    if (i == 0) TextAlign.LEFT else it })
        }
        val phase = t(500, 10.5f)
        p.drawText(ContinuumSurfaces.fit(p, ContinuumCockpitPresentation.phaseLabel(s.cockpit.turnPhase), phase, w - 30f),
            16f, y + 34f, phase, solid(C.cyanSoft))
        super.drawBody(p, w, h, s, y + 40f)
    }
    override fun describe(s: ContinuumDashboardState) =
        super.describe(s) + ". Auftragsphase: " + ContinuumCockpitPresentation.phaseLabel(s.cockpit.turnPhase)
}

class CockpitProcessesCard(textScale: Float) : CockpitCard(textScale, Icon.PULSE) {
    override fun title(s: ContinuumDashboardState) = "Aktuelle Prozesse"
    override fun rows(s: ContinuumDashboardState) = ContinuumCockpitPresentation.processRows(s.cockpit)
}

class CockpitDialogCard(textScale: Float) : CockpitCard(textScale, Icon.CHAT) {
    override fun title(s: ContinuumDashboardState) = "Live-Dialog"
    override fun rows(s: ContinuumDashboardState) = ContinuumCockpitPresentation.dialogRows(s.cockpit)
    override fun drawBody(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState, top: Float) {
        val who = t(600, 10f)
        val body = t(400, 10.5f)
        var y = top + 8f
        for (row in rows(s)) {
            if (y > h - 10f) break
            if (row.value.isEmpty()) { p.drawText(row.label, 16f, y, body, solid(C.textMuted)); y += 18f; continue }
            p.drawText(row.label + ":", 16f, y, who, solid(toneColor(row.state)))
            val indent = 16f + p.measureText(row.label + ": ", who)
            p.drawText(ContinuumSurfaces.fit(p, row.value, body, w - indent - 12f), indent, y, body, solid(C.text))
            y += 18f
        }
    }
}

class CockpitSystemCard(textScale: Float) : CockpitCard(textScale, Icon.CPU) {
    override val showBars = true
    override fun title(s: ContinuumDashboardState) = "Systemstatus"
    override fun pill(s: ContinuumDashboardState) = s.system.overall to (if (s.system.openIssues == 0) C.ok else C.warn)
    override fun rows(s: ContinuumDashboardState) = ContinuumCockpitPresentation.systemRows(s)
}

class CockpitAgentsCard(textScale: Float) : CockpitCard(textScale, Icon.USERS) {
    override val showBars = true
    override fun title(s: ContinuumDashboardState) = ContinuumCockpitPresentation.agentsTitle(s.cockpit)
    override fun rows(s: ContinuumDashboardState) = ContinuumCockpitPresentation.agentRows(s.cockpit)
}

/** Composer entrance of the landscape cockpit: opens the real chat editor. */
class CockpitComposerElement(textScale: Float) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        JarvisNeon.glassCard(p, 1f, 1f, w - 1f, h - 1f, h / 2f, JarvisDesign.Glow.SUBTLE)
        ContinuumSurfaces.iconGlow(p, Icon.PULSE, 24f, h / 2f, 16f)
        val style = t(400, 12f)
        p.drawText(ContinuumSurfaces.fit(p, "JARVIS, was soll ich tun?", style, w - 90f), 44f, h / 2f + 4f, style, solid(C.textMuted))
        ContinuumSurfaces.iconGlow(p, Icon.ARROW_UP, w - 24f, h / 2f, 16f)
    }
    override fun describe(s: ContinuumDashboardState) = "Nachricht an JARVIS schreiben oder sprechen. Öffnet den Chat."
}
