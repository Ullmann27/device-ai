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

    /**
     * Tile and nav targets of #479 (JarvisTiles) → existing owner-controlled screens (ContinuumHomeLayout slot,
     * handled by AgentActivity.openHomeSlot). A target without a route is drawn disabled ("nicht verfügbar").
     * Nothing here starts work by itself.
     */
    val routes: Map<String, ContinuumHomeLayout.Slot> = mapOf(
        "home" to ContinuumHomeLayout.Slot.BRAND,
        "chat" to ContinuumHomeLayout.Slot.LABEL_THINKING,
        "memory" to ContinuumHomeLayout.Slot.ACTION_MEMORY,
        "logic" to ContinuumHomeLayout.Slot.MODEL,
        "research" to ContinuumHomeLayout.Slot.LABEL_ANALYSING,
        "agents" to ContinuumHomeLayout.Slot.ACTION_AGENTS,
        "tasks" to ContinuumHomeLayout.Slot.ACTION_TASK,
        "web" to ContinuumHomeLayout.Slot.LABEL_CONNECTED,
        "learning" to ContinuumHomeLayout.Slot.LABEL_LEARNING,
        "device" to ContinuumHomeLayout.Slot.ACTION_DEVICE,
        "system" to ContinuumHomeLayout.Slot.SYSTEM_STATUS,
    )

    /** Real value per tile target; never an invented number. */
    fun tileValue(target: String, s: ContinuumDashboardState, nowMillis: Long): String {
        val na = ContinuumCockpitPresentation.NOT_AVAILABLE
        if (target !in routes) return na
        return when (target) {
            "chat" -> ContinuumCockpitPresentation.phaseLabel(s.cockpit.turnPhase)
            "memory" -> s.memory?.let { "${it.totalEntries} Einträge" } ?: na
            "logic" -> s.model.name.ifBlank { na }
            "agents" -> s.cockpit.workers?.let { w -> "${w.count { it.running }} aktiv" + (s.cockpit.workerCapacity?.let { " / $it" } ?: "") } ?: na
            "device" -> s.system.batteryPercent?.let { "Akku $it %" } ?: na
            "system" -> s.system.memoryPercent?.let { "RAM $it %" } ?: na
            else -> na // research, learning: no live counter source yet
        }
    }

    fun tiles(s: ContinuumDashboardState, nowMillis: Long): List<JarvisTile> =
        JarvisTiles.reference.map { it.copy(displayText = tileValue(it.target, s, nowMillis)) }

    /**
     * Kern-side hit test (dp) for tiles, rail and bottom nav. #479 has no hit-test API yet, so the row/column
     * geometry of JarvisTiles.drawRail/drawBottomNav is mirrored here (reported interface gap).
     */
    fun hitTarget(shell: JarvisShellLayout.Shell, tiles: List<JarvisTile>, x: Float, y: Float): String? {
        shell.tileBoxes().forEachIndexed { i, b -> if (i < tiles.size && b.contains(x, y)) return tiles[i].target }
        shell[Slot.NAV_RAIL]?.takeIf { it.contains(x, y) }?.let { b ->
            val items = JarvisTiles.railNav
            val rowH = minOf(44f, (b.height - 16f) / items.size)
            val i = ((y - b.top - 8f) / rowH).toInt()
            return items.getOrNull(i)?.target
        }
        shell[Slot.BOTTOM_NAV]?.takeIf { it.contains(x, y) }?.let { b ->
            val items = JarvisTiles.bottomNav
            return items.getOrNull(((x - b.left) / (b.width / items.size)).toInt())?.target
        }
        return null
    }

    private fun JarvisShellLayout.Box.contains(x: Float, y: Float) = x in left..right && y in top..bottom

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
