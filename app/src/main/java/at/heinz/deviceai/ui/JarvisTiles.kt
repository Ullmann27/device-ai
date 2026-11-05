package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Pure presentation model of an action tile (look code only, NOT a data interface).
 * Real values reach the tile through Jarvis Kern's interfaces → [displayText]; how is Kern's decision.
 */
enum class JarvisControlState { NORMAL, PRESSED, SELECTED, DISABLED, LOADING }

data class JarvisTile(val target: String, val label: String, val icon: Icon, val displayText: String? = null,
                      /** ref5 grid (Kern): description lines under the title; empty = classic tile. */
                      val subtitle: List<String> = emptyList(),
                      val controlState: JarvisControlState = JarvisControlState.NORMAL)

object JarvisTiles {
    private val edgePalette = intArrayOf(Colors.cyan, Colors.cardRimEnd, Colors.violet, Colors.magenta)
    private val edgeEnergyColors = IntArray(64) { step ->
        val pos = step * edgePalette.size / 64f
        val i = pos.toInt()
        val t = pos - i
        val smooth = t * t * (3f - 2f * t)
        Argb.mix(edgePalette[i], edgePalette[(i + 1) % edgePalette.size], smooth)
    }
    private val edgeCoreFill = Fill.Solid(Colors.glowCore)
    /** ref5 uses two violet cards among the cyan cards. Colour describes the destination, never live status. */
    fun style(target: String): JarvisDesign.ComponentStyle = when (target) {
        "memory" -> JarvisDesign.Styles.memoryTile
        "agents" -> JarvisDesign.Styles.agentsTile
        "system" -> JarvisDesign.Styles.systemTile
        else -> JarvisDesign.Styles.tile
    }

    fun ambientPhase(target: String, timeMs: Long): Float {
        if (timeMs <= 0L) return 0f
        val position = (timeMs % 14_000L).toDouble() / 14_000.0
        val offset = (target.hashCode() and 7) * .36
        return (.5 + .5 * kotlin.math.sin(2.0 * kotlin.math.PI * position + offset)).toFloat()
    }

    fun drawSurface(p: ContinuumPainter, b: Box, tile: JarvisTile, highlighted: Boolean,
                    timeMs: Long = 0L) {
        val source = style(tile.target)
        val disabled = tile.controlState == JarvisControlState.DISABLED
        val st = if (disabled) source.copy(glow = JarvisDesign.Glow.OFF,
            rim = Argb.mix(source.rim, Colors.background, .65f)) else source
        val active = !disabled && (highlighted || tile.controlState == JarvisControlState.PRESSED ||
            tile.controlState == JarvisControlState.SELECTED)
        JarvisNeon.component(p, b.left, b.top, b.right, b.bottom, st, active = active)
        val radius = minOf(st.radius, b.height / 2f)
        p.fillRoundRect(b.left + 1f, b.top + 1f, b.right - 1f, b.top + b.height * .30f, radius,
            Fill.Linear(0f, b.top, 0f, b.top + b.height * .30f,
                intArrayOf(Argb.withAlpha(st.rim, .12f), 0)))
        JarvisNeon.halo(p, b.left + 16f, b.top + b.height / 2f, b.height * .42f, st.rim, .14f)
        if (!disabled && timeMs > 0L) {
            // Existing shell clock only; no decorative runtime-status inference.
            JarvisTileEnergy.sample(timeMs, b.width, b.height, tile.target, radius)?.let { bead ->
                val color = edgeEnergyColors[(bead.phase * edgeEnergyColors.size).toInt()
                    .coerceIn(0, edgeEnergyColors.lastIndex)]
                val x = b.left + bead.x
                val y = b.top + bead.y
                val tail = JarvisTileEnergy.sample(timeMs - 140L, b.width, b.height, tile.target, radius)
                p.save()
                p.clipRoundRect(b.left, b.top, b.right, b.bottom, radius)
                tail?.let {
                    JarvisSceneFills.halo(p, b.left + it.x, b.top + it.y, if (active) 7f else 5f,
                        color, if (active) .18f else .10f)
                }
                JarvisSceneFills.halo(p, x, y, if (active) 10f else 7f, color,
                    if (active) .32f else .20f)
                p.fillCircle(x, y, if (active) 1.5f else 1f, edgeCoreFill,
                    if (active) .9f else .67f)
                p.restore()
            }
        }
        // LOADING is explicit caller state. Never infer work from a destination's decorative animation.
        if (tile.controlState == JarvisControlState.LOADING) {
            p.drawText("…", b.right - 9f, b.top + 15f, JarvisDesign.Type.caption,
                Fill.Solid(Colors.cyanSoft), TextAlign.RIGHT)
        }
    }

    /**
     * The ten destinations in the exact row-major order of the owner reference. Labels follow the owner's binding
     * German terminology (2026-11-05); stable [JarvisTile.target] IDs carry routing, never the visible text.
     */
    val reference = listOf(
        JarvisTile("chat", "Gespräch", Icon.CHAT),
        JarvisTile("memory", "Gedächtnis", Icon.DATABASE),
        JarvisTile("logic", "Denken und Planen", Icon.ATOM),
        JarvisTile("research", "Forschung", Icon.SEARCH),
        JarvisTile("agents", "Assistenten", Icon.CUBE),
        JarvisTile("tasks", "Aufgaben", Icon.CHECK_CIRCLE),
        JarvisTile("web", "Internet", Icon.GLOBE),
        JarvisTile("learning", "Lernen", Icon.BOOK),
        JarvisTile("device", "Gerätesteuerung", Icon.CPU),
        JarvisTile("system", "Systemstatus", Icon.PULSE),
    )

    /** Draws tile content inside an already drawn glass card. */
    fun draw(p: ContinuumPainter, b: Box, tile: JarvisTile) {
        if (tile.subtitle.isNotEmpty()) return JarvisCockpitGridContent.drawTile(p, b, tile, tile.subtitle)
        if (b.width < 140f) return drawCompact(p, b, tile)
        val iconSize = minOf(b.height * .5f, 30f)
        val ix = b.left + 10f + iconSize / 2f; val cy = b.top + b.height / 2f
        ContinuumIcons.draw(p, tile.icon, ix, cy, iconSize, Fill.Solid(Colors.cyan), iconSize / 13f * 2.6f, .18f)
        ContinuumIcons.draw(p, tile.icon, ix, cy, iconSize, Fill.Solid(Colors.cyan))
        val tx = ix + iconSize / 2f + 10f; val room = b.right - 10f - tx
        val title = JarvisDesign.Type.title; val sub = JarvisDesign.Type.caption
        val value = tile.displayText
        if (value == null) p.drawText(JarvisShellRenderer.fit(p, tile.label, title, room), tx, cy + p.capHeight(title) / 2f, title, Fill.Solid(Colors.text))
        else {
            p.drawText(JarvisShellRenderer.fit(p, tile.label, title, room), tx, cy - 2f, title, Fill.Solid(Colors.text))
            p.drawText(JarvisShellRenderer.fit(p, value, sub, room), tx, cy + p.capHeight(sub) + 5f, sub, Fill.Solid(Colors.cyanSoft))
        }
        ContinuumIcons.draw(p, Icon.CHEVRON_RIGHT, b.right - 10f, cy, 10f, Fill.Solid(Colors.textSecondary), 1.1f)
    }

    /** Narrow tiles (Fold7 centre column): icon above label, no subtitle. */
    private fun drawCompact(p: ContinuumPainter, b: Box, tile: JarvisTile) {
        val title = JarvisDesign.Type.label
        val iconSize = minOf(b.height * .42f, 26f)
        val cx = b.centerX; val iy = b.top + b.height * .40f
        ContinuumIcons.draw(p, tile.icon, cx, iy, iconSize, Fill.Solid(Colors.cyan), iconSize / 13f * 2.6f, .18f)
        ContinuumIcons.draw(p, tile.icon, cx, iy, iconSize, Fill.Solid(Colors.cyan))
        p.drawText(JarvisShellRenderer.fit(p, tile.label, title, b.width - 12f), cx, b.bottom - 9f, title, Fill.Solid(Colors.text), TextAlign.CENTER)
        tile.displayText?.let { p.drawText(JarvisShellRenderer.fit(p, it, JarvisDesign.Type.caption, b.width - 12f), cx, b.top + 12f, JarvisDesign.Type.caption, Fill.Solid(Colors.cyanSoft), TextAlign.CENTER) }
    }

    /** Navigation entries read from ref5 (rail) and ref4 (bottom nav), German owner terms. Presentation only. */
    data class NavItem(val target: String, val label: String, val icon: Icon)
    val railNav = listOf(NavItem("home", "Start", Icon.HOME), NavItem("chat", "Gespräch", Icon.CHAT),
        NavItem("memory", "Gedächtnis", Icon.DATABASE), NavItem("logic", "Denken und Planen", Icon.ATOM),
        NavItem("research", "Forschung", Icon.SEARCH), NavItem("agents", "Assistenten", Icon.CUBE),
        NavItem("tasks", "Aufgaben", Icon.CHECK_CIRCLE), NavItem("web", "Internet", Icon.GLOBE),
        NavItem("learning", "Lernen", Icon.BOOK), NavItem("device", "Gerätesteuerung", Icon.CPU),
        NavItem("system", "Systemstatus", Icon.PULSE))
    // The Activity supplies its grouped phone bar. Retained for standalone shell previews / embedding.
    val bottomNav = listOf(railNav[0], railNav[1], railNav[2], railNav[5], railNav[10])

    /** Shared by drawing, hit testing and the native, independently scrollable rail controls. */
    fun railItemBoxes(b: Box, count: Int = railNav.size, fontScale: Float = 1f): List<Box> {
        require(count >= 0)
        val scale = (b.width / 231f).coerceIn(.45f, 1.5f)
        val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 2f) else 1f
        val pitch = maxOf(48f * maxOf(1f, font), 56f * scale)
        val inset = 12f * scale
        return List(count) { i ->
            val top = b.top + 20f * scale + i * pitch
            Box(b.left + inset, top, b.right - inset, top + pitch)
        }
    }

    fun railContentHeight(widthDp: Float, fontScale: Float = 1f, count: Int = railNav.size): Float =
        (railItemBoxes(Box(0f, 0f, widthDp, 1f), count, fontScale).lastOrNull()?.bottom ?: 0f) + 8f

    fun drawRail(p: ContinuumPainter, b: Box, items: List<NavItem> = railNav, selected: Int = 0,
                 labelled: Boolean = true, fontScale: Float = 1f, pressed: Int = -1) {
        val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 2f) else 1f
        val rows = railItemBoxes(b, items.size, font)
        val style = JarvisDesign.Type.nav.copy(size = JarvisDesign.Type.nav.size * font)
        p.save()
        p.clipRect(b.left, b.top, b.right, b.bottom)
        items.forEachIndexed { i, it ->
            val row = rows[i]
            val active = i == selected || i == pressed
            val surface = if (active) JarvisDesign.Styles.buttonActive else JarvisDesign.Styles.button
            JarvisNeon.component(p, row.left, row.top, row.right, row.bottom, surface, active, centerLift = active)
            val icon = minOf(24f * font, row.height * .52f)
            val ix = if (labelled) row.left + 9f + icon / 2f else row.centerX
            val color = if (it.target == "system") Colors.violetSoft else if (active) Colors.cyan else Colors.cyanSoft
            if (!JarvisRailIcons.draw(p, it.target, ix - icon / 2f, row.centerY - icon / 2f, icon, color))
                ContinuumIcons.draw(p, it.icon, ix, row.centerY, icon, Fill.Solid(color))
            if (labelled) {
                val tx = ix + icon / 2f + 9f
                val room = row.right - tx - 5f
                val lines = railLines(p, it.label, style, room)
                val lineH = maxOf(p.capHeight(style) + 4f, style.size * 1.1f)
                lines.forEachIndexed { j, line ->
                    val y = row.centerY + p.capHeight(style) / 2f + (j - (lines.size - 1) / 2f) * lineH
                    p.drawText(JarvisShellRenderer.fit(p, line, style, room), tx, y, style,
                        Fill.Solid(Colors.text))
                }
            }
        }
        p.restore()
    }

    /** German compounds that may break over two rail lines (hyphenated at the compound boundary). */
    val compoundBreaks: Map<String, Pair<String, String>> = mapOf(
        "Gerätesteuerung" to ("Geräte-" to "steuerung"),
        "Systemstatus" to ("System-" to "status"),
    )

    /** One line if it fits; else the last word moves down, or a known compound breaks at its boundary. */
    fun railLines(p: ContinuumPainter, label: String, style: TextStyle, room: Float): List<String> {
        if (p.measureText(label, style) <= room) return listOf(label)
        val words = label.split(' ')
        if (words.size > 1) return listOf(words.dropLast(1).joinToString(" "), words.last())
        return compoundBreaks[label]?.toList() ?: listOf(label)
    }

    fun drawBottomNav(p: ContinuumPainter, b: Box, items: List<NavItem> = bottomNav, selected: Int = 0) {
        val w = b.width / items.size; val style = JarvisDesign.Type.caption
        items.forEachIndexed { i, it ->
            val cx = b.left + w * i + w / 2f
            if (i == selected) p.fillRoundRect(cx - w / 2f + 4f, b.top + 6f, cx + w / 2f - 4f, b.bottom - 6f, 12f, Fill.Solid(Argb.of(60, 0, 255, 255)))
            val c = if (i == selected) Colors.cyan else Colors.textSecondary
            ContinuumIcons.draw(p, it.icon, cx, b.top + b.height * .4f, 20f, Fill.Solid(c))
            p.drawText(JarvisShellRenderer.fit(p, it.label, style, w - 6f), cx, b.bottom - 14f, style, Fill.Solid(c), TextAlign.CENTER)
        }
    }
}
