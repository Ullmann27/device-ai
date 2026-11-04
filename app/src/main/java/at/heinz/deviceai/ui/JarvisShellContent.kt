package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Row
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Tone
import at.heinz.deviceai.ui.JarvisShellLayout.Slot

/**
 * Real content for Welten-Optik's [JarvisShellView] slots (#479). Kern supplies only values (rows from
 * [ContinuumCockpitPresentation]); glass, motion and placement stay in JarvisShellRenderer. AVATAR, TILES and the
 * navigation slots get no content here (Avatar view / missing tile API, see docs/ARCHITEKTUR-JARVIS.md).
 */
object JarvisShellContent {
    val filled = listOf(Slot.TOP_BAR, Slot.DIALOG, Slot.INPUT, Slot.COCKPIT_PRIMARY, Slot.COCKPIT_SECONDARY,
        Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL, Slot.STATUS)

    fun title(slot: Slot, s: ContinuumDashboardState): String = when (slot) {
        Slot.TOP_BAR -> "CONTINUUM"
        Slot.DIALOG -> "JARVIS – Live-Dialog"
        Slot.INPUT -> "JARVIS, was soll ich tun?"
        Slot.COCKPIT_PRIMARY, Slot.ROW_MODEL -> "KI-Gehirn"
        Slot.COCKPIT_SECONDARY, Slot.STATUS -> "Systemstatus"
        Slot.ROW_AGENTS -> ContinuumCockpitPresentation.agentsTitle(s.cockpit)
        Slot.ROW_TASKS -> "Aktueller Ablauf"
        Slot.ROW_MEMORY -> "Gedächtnis"
        else -> ""
    }

    fun rows(slot: Slot, s: ContinuumDashboardState, nowMillis: Long): List<Row> = when (slot) {
        Slot.DIALOG -> ContinuumCockpitPresentation.dialogRows(s.cockpit)
        Slot.COCKPIT_PRIMARY, Slot.ROW_MODEL -> listOf(Row("Phase", ContinuumCockpitPresentation.phaseLabel(s.cockpit.turnPhase), null,
            Tone.ACTIVE)) + ContinuumCockpitPresentation.brainRows(s)
        Slot.COCKPIT_SECONDARY, Slot.STATUS -> ContinuumCockpitPresentation.systemRows(s)
        Slot.ROW_AGENTS -> ContinuumCockpitPresentation.agentRows(s.cockpit)
        Slot.ROW_TASKS -> ContinuumCockpitPresentation.processRows(s.cockpit)
        Slot.ROW_MEMORY -> ContinuumCockpitPresentation.memoryRows(s, nowMillis)
        else -> emptyList()
    }

    /** One SlotContent per filled slot; [state] is read at draw time so the map can be built once. */
    fun forState(state: () -> ContinuumDashboardState, clock: () -> Long = System::currentTimeMillis):
        Map<Slot, JarvisShellRenderer.SlotContent> = filled.associateWith { slot ->
        JarvisShellRenderer.SlotContent { p, box -> draw(p, slot, box, state(), clock()) }
    }

    private fun draw(p: ContinuumPainter, slot: Slot, b: JarvisShellLayout.Box, s: ContinuumDashboardState, now: Long) {
        val c = JarvisDesign.Colors
        val titleStyle = JarvisDesign.Type.label
        val body = JarvisDesign.Type.caption
        if (slot == Slot.TOP_BAR) {
            JarvisNeon.text(p, title(slot, s), b.left + 12f, b.top + b.height / 2f + p.capHeight(JarvisDesign.Type.brand) / 2f,
                JarvisDesign.Type.brand, c.cyan)
            return
        }
        if (slot == Slot.INPUT) {
            p.drawText(ContinuumSurfaces.fit(p, title(slot, s), body, b.width - 24f), b.left + 14f,
                b.top + b.height / 2f + p.capHeight(body) / 2f, body, Fill.Solid(c.textMuted))
            return
        }
        p.drawText(ContinuumSurfaces.fit(p, title(slot, s), titleStyle, b.width - 20f), b.left + 10f, b.top + 18f, titleStyle,
            Fill.Solid(c.text))
        val lineH = 15f
        val room = ((b.height - 26f) / lineH).toInt().coerceAtLeast(0)
        rows(slot, s, now).take(room).forEachIndexed { i, row ->
            val y = b.top + 34f + i * lineH
            val color = when (row.state) { Tone.OK -> c.ok; Tone.WARN -> c.warn; Tone.ERROR -> c.error; Tone.ACTIVE -> c.cyan
                Tone.NEUTRAL -> c.textSecondary }
            val valueWidth = if (row.value.isEmpty()) 0f else minOf(p.measureText(row.value, body), b.width * .55f)
            p.drawText(ContinuumSurfaces.fit(p, row.label, body, b.width - 24f - valueWidth), b.left + 10f, y, body, Fill.Solid(c.text))
            if (row.value.isNotEmpty()) p.drawText(ContinuumSurfaces.fit(p, row.value, body, b.width * .55f), b.right - 10f, y, body,
                Fill.Solid(color), TextAlign.RIGHT)
        }
    }
}
