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
    private val featureInk = Argb.mix(Colors.cyan, Colors.cyanSoft, .22f)
    private val featureDetail = Argb.mix(Colors.violetSoft, Colors.violetRim, .40f)
    private val featureFallback = Fill.Solid(featureInk)
    private val disabledFeatureFallback = Fill.Solid(Colors.textMuted)
    private val featureGround = Fill.Linear(-.70f, .56f, .70f, .56f,
        intArrayOf(Colors.cyan, Colors.cyanSoft, Colors.violetRim, Colors.magenta),
        floatArrayOf(0f, .30f, .72f, 1f))
    private val featureFloor = Fill.Linear(-.70f, .56f, .70f, .56f,
        intArrayOf(Argb.withAlpha(Colors.cyan, .20f), Argb.withAlpha(Colors.bloomHalo, .10f),
            Argb.withAlpha(Colors.magenta, .20f)))
    private val featureGlint = Fill.Linear(-.54f, .60f, .54f, .60f,
        intArrayOf(0, Argb.withAlpha(Colors.iconCore, .80f), Colors.violetRim, 0),
        floatArrayOf(0f, .28f, .72f, 1f))

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
                    timeMs: Long = 0L, luxuryDepth: Boolean = false,
                    lightSourceX: Float = b.centerX, lightSourceY: Float = b.top) {
        val source = style(tile.target)
        val disabled = tile.controlState == JarvisControlState.DISABLED
        val st = if (disabled) source.copy(glow = JarvisDesign.Glow.OFF,
            rim = Argb.mix(source.rim, Colors.background, .65f)) else source
        val active = !disabled && (highlighted || tile.controlState == JarvisControlState.PRESSED ||
            tile.controlState == JarvisControlState.SELECTED)
        val interaction = when {
            disabled -> JarvisReferenceHud.Interaction.DISABLED
            active -> JarvisReferenceHud.Interaction.SELECTED
            tile.controlState == JarvisControlState.LOADING -> JarvisReferenceHud.Interaction.LOADING
            else -> JarvisReferenceHud.Interaction.NORMAL
        }
        if (luxuryDepth) {
            LuxuryHoloButtonRenderer.draw(p, b, lightSourceX, lightSourceY, active,
                disabled, tile.controlState == JarvisControlState.PRESSED)
            if (tile.controlState == JarvisControlState.LOADING) {
                p.drawText("…", b.right - 9f, b.top + 15f, JarvisDesign.Type.caption,
                    Fill.Solid(Colors.cyanSoft), TextAlign.RIGHT)
            }
            return
        }
        JarvisReferenceHud.surface(p, b, st.radius, st.rim, st.rimEnd ?: st.rim,
            interaction, decorativeRibbons = true)
        val radius = minOf(st.radius, b.height / 2f)
        if (!disabled && timeMs > 0L) {
            // Existing clock, quiet edge energy.
            val wave = ambientPhase(tile.target, timeMs)
            val colour = if (wave <= .5f) Argb.mix(Colors.cyan, Colors.violet, wave * 2f)
                else Argb.mix(Colors.violet, Colors.magenta, (wave - .5f) * 2f)
            p.setAdditive(true)
            p.strokeRoundRect(b.left, b.top, b.right, b.bottom, radius,
                3f, Fill.Solid(colour), .025f + .020f * wave)
            p.strokeRoundRect(b.left, b.top, b.right, b.bottom, radius,
                .85f, Fill.Solid(colour), .065f + .055f * wave)
            p.setAdditive(false)
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

    /** Clean glyph, restrained local light. */
    fun drawFeatureIcon(p: ContinuumPainter, tile: JarvisTile, cx: Float, cy: Float, size: Float) {
        if (!cx.isFinite() || !cy.isFinite() || !size.isFinite() || size <= 0f) return
        val disabled = tile.controlState == JarvisControlState.DISABLED
        val opacity = if (disabled) .42f else 1f
        val color = if (disabled) Colors.textMuted else featureInk
        val detail = if (disabled) Colors.textMuted else featureDetail
        val ground = if (disabled) disabledFeatureFallback else featureGround
        val glint = if (disabled) disabledFeatureFallback else featureGlint
        p.save(); p.translate(cx, cy); p.scale(size, size)
        p.clipRect(-.78f, -.5f, .78f, .72f)
        if (!disabled) {
            JarvisSceneFills.halo(p, 0f, .02f, .52f, Colors.cyan, .14f)
            JarvisSceneFills.ellipseHalo(p, 0f, .56f, .77f, .15f, Colors.cyan, .13f)
        }
        if (!disabled) p.fillOval(-.70f, .45f, .70f, .65f, featureFloor)
        p.setAdditive(true)
        p.strokeOval(-.70f, .45f, .70f, .65f, .07f, ground, .12f * opacity)
        p.strokeOval(-.70f, .45f, .70f, .65f, .022f, ground, .82f * opacity)
        p.strokeArc(-.54f, .47f, .54f, .64f, 16f, 148f, .012f, glint, .70f * opacity)
        p.setAdditive(false)
        if (!JarvisRailIcons.drawFeature(p, tile.target, -.5f, -.5f, 1f, color, detail, opacity))
            ContinuumIcons.draw(p, tile.icon, 0f, 0f, 1f,
                if (disabled) disabledFeatureFallback else featureFallback, .0625f, opacity)
        p.restore()
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
        JarvisTile("agents", "Mein KI-Team", Icon.CUBE),
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
        NavItem("research", "Forschung", Icon.SEARCH), NavItem("agents", "Mein KI-Team", Icon.CUBE),
        NavItem("tasks", "Aufgaben", Icon.CHECK_CIRCLE), NavItem("web", "Internet", Icon.GLOBE),
        NavItem("learning", "Lernen", Icon.BOOK), NavItem("device", "Gerätesteuerung", Icon.CPU),
        NavItem("system", "Systemstatus", Icon.PULSE))
    /**
     * Holographic tool dock that replaces the retired left rail.
     * Same destinations and order as [railNav] so every previous rail target stays reachable.
     * Labels stay the real German owner terms; reference mock-up names such as "Dateien" or "PDF"
     * are not shown because those routes do not exist.
     */
    val bottomNav = railNav

    /** Shared by drawing, hit testing and the native, independently scrollable rail controls. */
    fun railItemBoxes(b: Box, count: Int = railNav.size, fontScale: Float = 1f): List<Box> {
        require(count >= 0)
        val scale = (b.width / 231f).coerceIn(.45f, 1.5f)
        val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 2f) else 1f
        val pitch = maxOf(48f * maxOf(1f, font), 56f * scale)
        val inset = 6f * scale
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
            val icon = minOf(22f * font, row.width * .21f, row.height * .48f)
            val ix = if (labelled) row.left + 5f + icon / 2f else row.centerX
            val color = if (it.target == "system") Colors.violetSoft else if (active) Colors.cyan else Colors.cyanSoft
            JarvisSceneFills.halo(p, ix, row.centerY, icon * .54f, color, if (active) .12f else .07f)
            if (!JarvisRailIcons.draw(p, it.target, ix - icon / 2f, row.centerY - icon / 2f, icon, color))
                ContinuumIcons.draw(p, it.icon, ix, row.centerY, icon, Fill.Solid(color))
            if (labelled) {
                val tx = ix + icon / 2f + 5f
                val room = row.right - tx - 3f
                val lines = railLines(p, it.label, style, room)
                val widest = lines.maxByOrNull { line -> p.measureText(line, style) }.orEmpty()
                val fitted = JarvisShellRenderer.shrinkToFit(p, widest, style, room - .5f, minScale = .88f)
                val lineH = maxOf(p.capHeight(fitted) + 4f, fitted.size * 1.1f)
                lines.forEachIndexed { j, line ->
                    val y = row.centerY + p.capHeight(fitted) / 2f + (j - (lines.size - 1) / 2f) * lineH
                    p.drawText(JarvisShellRenderer.fit(p, line, fitted, room), tx, y, fitted,
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

    private val dockShortLabels = mapOf(
        "logic" to "Denken", "device" to "Geräte", "system" to "System", "agents" to "Helfer",
        "research" to "Forschung", "learning" to "Lernen", "memory" to "Gedächtnis")

    /** Equal-width cells matching [ContinuumHomeView] hit targets and [JarvisShellContent.hitTarget]. */
    fun bottomNavItemBoxes(b: Box, items: List<NavItem> = bottomNav): List<Box> {
        if (items.isEmpty() || b.width <= 0f) return emptyList()
        val w = b.width / items.size
        return items.indices.map { i ->
            Box(b.left + i * w, b.top, b.left + (i + 1) * w, b.bottom)
        }
    }

    /**
     * Glass tiles with icon + label, using the existing animation clock via [timeMs].
     * [timeMs] 0 (reduced motion / still preview) skips the quiet edge energy.
     */
    fun drawBottomNav(p: ContinuumPainter, b: Box, items: List<NavItem> = bottomNav, selected: Int = 0,
                      timeMs: Long = 0L, pressed: Int = -1,
                      cinemaElapsedMs: Long? = null, cinemaExiting: Boolean = false,
                      cinemaBudget: JarvisRenderQuality.Budget? = null, animatorScale: Float = 1f) {
        if (items.isEmpty()) return
        val cells = bottomNavItemBoxes(b, items)
        val inset = 3f
        items.forEachIndexed { i, it ->
            val cell = cells[i]
            val tile = Box(cell.left + inset, b.top + 4f, cell.right - inset, b.bottom - 4f)
            if (tile.width <= 2f || tile.height <= 2f) return@forEachIndexed
            val cinema = if (cinemaElapsedMs != null) JarvisCinemaEntrance.pose(
                JarvisCinemaEntrance.Kind.DOCK, i, cinemaElapsedMs, cinemaExiting,
                side = if (i < items.size / 2) -1f else 1f, budget = cinemaBudget, animatorScale = animatorScale)
                else JarvisCinemaEntrance.SETTLED
            if (cinema.hidden && cinema.rimAlpha < 0.01f) return@forEachIndexed
            p.save()
            p.translate(cinema.dx, cinema.dy)
            p.scale(cinema.scale, cinema.scale, tile.centerX, tile.centerY)
            if (cinema.tiltY != 0f) p.rotate(cinema.tiltY * .25f, tile.centerX, tile.centerY)
            val active = i == selected || i == pressed
            val model = JarvisTile(it.target, it.label, it.icon,
                controlState = if (i == pressed) JarvisControlState.PRESSED
                    else if (i == selected) JarvisControlState.SELECTED
                    else JarvisControlState.NORMAL)
            if (cinema.glassAlpha < 0.02f && cinema.rimAlpha > 0.02f) {
                p.strokeRoundRect(tile.left, tile.top, tile.right, tile.bottom, 10f, 1.4f,
                    Fill.Solid(Colors.cyan), cinema.rimAlpha)
                p.restore()
                return@forEachIndexed
            }
            if (cinema.glassAlpha < 1f) p.saveAlpha(cinema.glassAlpha)
            drawSurface(p, tile, model, active, timeMs)
            if (cinema.glassAlpha < 1f) p.restore()
            if (cinema.contentAlpha < 0.01f) { p.restore(); return@forEachIndexed }
            if (cinema.contentAlpha < 1f) p.saveAlpha(cinema.contentAlpha)
            val iconSize = minOf(22f, tile.height * .38f, tile.width * .42f)
            val iy = tile.top + tile.height * .38f
            val color = if (it.target == "system") Colors.violetSoft else if (active) Colors.cyan else Colors.cyanSoft
            JarvisSceneFills.halo(p, tile.centerX, iy, iconSize * .7f, color, if (active) .16f else .08f)
            if (!JarvisRailIcons.draw(p, it.target, tile.centerX - iconSize / 2f, iy - iconSize / 2f, iconSize, color))
                ContinuumIcons.draw(p, it.icon, tile.centerX, iy, iconSize, Fill.Solid(color))
            val style = JarvisDesign.Type.caption
            val room = tile.width - 6f
            val short = dockShortLabels[it.target] ?: it.label
            val label = if (p.measureText(it.label, style) <= room) it.label else short
            p.drawText(JarvisShellRenderer.fit(p, label, style, room), tile.centerX, tile.bottom - 8f,
                style, Fill.Solid(if (active) Colors.text else Colors.textSecondary), TextAlign.CENTER)
            if (cinema.contentAlpha < 1f) p.restore()
            p.restore()
        }
    }
}
