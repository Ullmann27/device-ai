package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Owner scene spec 12.11.2026 (docs/design/continuum-live-computer-20261112): eight coloured, plastic glass buttons
 * under the mission field. Each button is a real destination with a routed target; colour names the destination,
 * never a live status. Fold7 open and other wide windows show one row of eight, normal phones two rows of four.
 *
 * Presentation and geometry only. Touch, focus and accessibility stay on the native views that
 * [ContinuumHomeView] creates from [JarvisTiles.bottomNavItemBoxes]; this renderer never dispatches input.
 */
object CockpitGlassButtons {
    data class Spec(val target: String, val label: String, val icon: Icon, val accent: Int, val colorName: String)

    /** Binding order and colours of the owner brief (Start … Geräte). */
    val buttons: List<Spec> = listOf(
        Spec("home", "Start", Icon.HOME, 0xFF22D8F2.toInt(), "Cyan"),
        Spec("chat", "Gespräch", Icon.CHAT, 0xFF1ED6B0.toInt(), "Türkis"),
        Spec("memory", "Gedächtnis", Icon.DATABASE, 0xFFA05CFF.toInt(), "Violett"),
        Spec("research", "Forschung", Icon.SEARCH, 0xFF5B5FFF.toInt(), "Indigo"),
        Spec("tasks", "Aufgaben", Icon.CHECK_CIRCLE, 0xFFFFA62B.toInt(), "Bernstein"),
        Spec("web", "Internet", Icon.GLOBE, 0xFF2F86FF.toInt(), "Blau"),
        Spec("learning", "Lernen", Icon.BOOK, 0xFF34D058.toInt(), "Grün"),
        Spec("device", "Geräte", Icon.CPU, 0xFFFF6E61.toInt(), "Koralle"),
    )

    /** Former dock destinations that now live in the header "Mehr" menu (still routed, never removed). */
    val secondaryTargets = listOf("logic", "agents", "system")

    /** Narrowest button that keeps icon plus readable one-line label; phones fall back to two rows of four. */
    const val MIN_BUTTON_WIDTH_DP = 72f
    /** Label size at 100 % system font; at larger font scales labels grow and never shrink below this. */
    const val LABEL_DP = 12.5f
    const val MIN_TOUCH_DP = 48f
    /** Space between neighbouring glass bodies inside their equal touch cells. */
    const val INSET_X_DP = 4f
    const val INSET_Y_DP = 5f

    fun spec(target: String): Spec? = buttons.firstOrNull { it.target == target }
    fun accent(target: String): Int = spec(target)?.accent ?: JarvisDesign.Colors.cyan

    /** One row when every button keeps [MIN_BUTTON_WIDTH_DP]; otherwise the fewest balanced rows (8 → 4 + 4). */
    fun columns(width: Float, count: Int = buttons.size): Int {
        if (count <= 0 || !width.isFinite() || width <= 0f) return 1
        val perRow = maxOf(1, (width / MIN_BUTTON_WIDTH_DP).toInt())
        if (perRow >= count) return count
        val rows = (count + perRow - 1) / perRow
        return (count + rows - 1) / rows
    }

    /** The drawn glass body inside its touch cell (the cell itself stays the native hit target). */
    fun body(cell: Box): Box = Box(cell.left + INSET_X_DP, cell.top + INSET_Y_DP,
        cell.right - INSET_X_DP, cell.bottom - INSET_Y_DP)

    fun baseLabelStyle(fontScale: Float): TextStyle {
        val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 2f) else 1f
        return ContinuumTheme.text(600, LABEL_DP * maxOf(1f, font), .2f)
    }

    /**
     * Label style that fits [room]: the scaled system size when it fits, else shrunk – but never below the 100 %
     * size [LABEL_DP]. Only if even that cannot fit (pathologically narrow window) the caller ellipsizes.
     */
    fun labelStyle(p: ContinuumPainter, label: String, room: Float, fontScale: Float): TextStyle {
        val base = baseLabelStyle(fontScale)
        val width = p.measureText(label, base)
        if (room <= 0f || width <= room || width <= 0f) return base
        return base.copy(size = maxOf(LABEL_DP, base.size * room / width))
    }

    /** Icon edge for a body: a clear symbol above the label, bounded by both body dimensions. */
    fun iconSize(body: Box, label: TextStyle): Float =
        minOf(26f, body.width * .34f, (body.height - label.size - 14f).coerceAtLeast(10f) * .62f)

    private class Material(val body: Fill, val gloss: Fill, val rim: Fill, val lip: Fill, val halo: Fill)
    private val materials = HashMap<String, Pair<Box, Material>>()

    /** Cached per button and geometry: steady frames reuse fills instead of building gradients again. */
    private fun material(spec: Spec, b: Box, active: Boolean): Material {
        val key = spec.target + if (active) ":on" else ":off"
        materials[key]?.let { (box, cached) -> if (box == b) return cached }
        val a = spec.accent
        val black = 0xFF04060C.toInt()
        val top = Argb.withAlpha(Argb.mix(a, black, if (active) .30f else .52f), .94f)
        val mid = Argb.withAlpha(Argb.mix(a, black, if (active) .52f else .70f), .95f)
        val bottom = Argb.withAlpha(Argb.mix(a, black, if (active) .66f else .82f), .97f)
        val m = Material(
            body = Fill.Linear(b.left, b.top, b.left, b.bottom, intArrayOf(top, mid, bottom), floatArrayOf(0f, .55f, 1f)),
            gloss = Fill.Linear(b.left, b.top, b.left, b.top + b.height * .5f,
                intArrayOf(Argb.of(if (active) 120 else 92, 255, 255, 255), Argb.of(0, 255, 255, 255))),
            rim = Fill.Linear(b.left, b.top, b.right, b.bottom,
                intArrayOf(Argb.mix(a, 0xFFFFFFFF.toInt(), .55f), a, Argb.mix(a, black, .45f))),
            lip = Fill.Linear(b.left, b.bottom, b.left, b.bottom + 5f,
                intArrayOf(Argb.withAlpha(a, .55f), Argb.withAlpha(black, .85f))),
            halo = Fill.Radial(b.centerX, b.bottom, maxOf(1f, b.width * .62f),
                intArrayOf(Argb.withAlpha(a, .55f), Argb.withAlpha(a, .14f), 0), floatArrayOf(0f, .55f, 1f)))
        if (materials.size > 48) materials.clear()
        materials[key] = b to m
        return m
    }

    private val whiteFill = Fill.Solid(0xFFFFFFFF.toInt())
    private val shadowFill = Fill.Solid(0xFF010309.toInt())

    /**
     * One plastic glass button: drop shadow, tinted glass body, top gloss, bevel highlight and lower lip, specular
     * streak, accent rim. Active: brighter glass, outer halo and an underline light bar. Pressed: body sinks and a
     * contact ring marks the touch. Disabled: muted, no glow.
     */
    fun draw(p: ContinuumPainter, b: Box, spec: Spec, active: Boolean, pressed: Boolean = false,
             disabled: Boolean = false, fontScale: Float = 1f, pressX: Float = .5f, pressY: Float = .5f) {
        if (b.width <= 4f || b.height <= 4f) return
        val sink = if (pressed && !disabled) 1.6f else 0f
        val l = b.left; val t = b.top + sink; val r = b.right; val bt = b.bottom + sink
        val sunk = Box(l, t, r, bt)
        val radius = minOf(14f, sunk.height * .28f)
        val m = material(spec, sunk, active && !disabled)
        val alpha = if (disabled) .38f else 1f
        val a = spec.accent
        if (!disabled) {
            p.setAdditive(true)
            p.fillOval(l + sunk.width * .08f, bt - 6f, r - sunk.width * .08f, bt + if (active) 16f else 9f,
                m.halo, if (active) .95f else .45f)
            p.strokeRoundRect(l, t, r, bt, radius, if (active) 12f else 7f, Fill.Solid(a), if (active) .16f else .07f)
            p.setAdditive(false)
        }
        if (sink == 0f) p.fillRoundRect(l + 1f, t + 4f, r + 1f, bt + 5f, radius, shadowFill, .62f * alpha)
        p.fillRoundRect(l, t, r, bt, radius, m.body, alpha)
        // Glossy upper half and bevel: light enters from the top edge, the bottom edge turns away.
        p.fillRoundRect(l + 1.2f, t + 1.2f, r - 1.2f, t + sunk.height * .5f, (radius - 1f).coerceAtLeast(1f), m.gloss, alpha)
        p.drawLine(l + radius * .8f, t + 1.1f, r - radius * .8f, t + 1.1f, 1f, whiteFill, (if (active) .62f else .42f) * alpha)
        val lip = minOf(4f, sunk.height * .07f)
        p.fillRect(l + radius * .5f, bt - .5f, r - radius * .5f, bt + lip, m.lip, .85f * alpha)
        p.drawLine(l + radius, bt - 1.3f, r - radius, bt - 1.3f, .9f, shadowFill, .55f * alpha)
        // Specular streak: a short, slanted reflection in the upper left; never crosses the label.
        p.save()
        p.clipRoundRect(l, t, r, bt, radius)
        p.setAdditive(true)
        val sx = l + sunk.width * .16f
        p.drawLine(sx, t + sunk.height * .36f, sx + sunk.width * .16f, t + 2f, 2.4f, whiteFill, .16f * alpha)
        p.drawLine(sx + 7f, t + sunk.height * .30f, sx + 7f + sunk.width * .09f, t + 2f, 1.1f, whiteFill, .10f * alpha)
        p.setAdditive(false)
        p.restore()
        p.strokeRoundRect(l, t, r, bt, radius, if (active) 1.8f else 1.25f, m.rim, (if (active) 1f else .78f) * alpha)
        if (active && !disabled) {
            // Visible active state independent of colour perception: underline light bar plus a brighter rim.
            val barW = sunk.width * .46f
            p.setAdditive(true)
            p.fillRoundRect(sunk.centerX - barW / 2f, bt - 3.6f, sunk.centerX + barW / 2f, bt - 1.2f, 1.2f,
                Fill.Solid(Argb.mix(a, 0xFFFFFFFF.toInt(), .45f)), .95f)
            p.setAdditive(false)
        }
        if (pressed && !disabled) {
            val x = l + sunk.width * (pressX.takeIf(Float::isFinite) ?: .5f).coerceIn(0f, 1f)
            val y = t + sunk.height * (pressY.takeIf(Float::isFinite) ?: .5f).coerceIn(0f, 1f)
            p.setAdditive(true)
            p.fillCircle(x, y, minOf(sunk.width, sunk.height) * .42f,
                Fill.Radial(x, y, maxOf(1f, minOf(sunk.width, sunk.height) * .42f),
                    intArrayOf(Argb.withAlpha(0xFFFFFFFF.toInt(), .32f), Argb.withAlpha(a, 0f))), 1f)
            p.setAdditive(false)
        }
        // Content: symbol above a readable label, both bright on the tinted glass.
        val room = sunk.width - 8f
        val style = labelStyle(p, spec.label, room, fontScale)
        val icon = iconSize(sunk, style)
        val iconColor = if (disabled) JarvisDesign.Colors.textMuted
            else Argb.mix(a, 0xFFFFFFFF.toInt(), if (active) .72f else .55f)
        val labelY = bt - minOf(10f, sunk.height * .16f)
        val iconCy = t + (labelY - style.size - t) / 2f + 2f
        if (!disabled) JarvisSceneFills.halo(p, sunk.centerX, iconCy, icon * .75f, a, if (active) .30f else .16f)
        if (!JarvisRailIcons.draw(p, spec.target, sunk.centerX - icon / 2f, iconCy - icon / 2f, icon, iconColor))
            ContinuumIcons.draw(p, spec.icon, sunk.centerX, iconCy, icon, Fill.Solid(iconColor))
        p.drawText(JarvisShellRenderer.fit(p, spec.label, style, room), sunk.centerX, labelY, style,
            Fill.Solid(if (disabled) JarvisDesign.Colors.textMuted else 0xFFF4F8FF.toInt()), TextAlign.CENTER)
    }
}
