package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Shell
import at.heinz.deviceai.ui.JarvisShellLayout.Slot

/**
 * Draws a [Shell]: deep-space background (gradient, blurred cyan grid, bokeh dust), the avatar stage,
 * and glass panels per slot with the Muster-B fly-in (centre first, left/right by side).
 * Slot content comes from Jarvis Kern via [SlotContent]; tiles use the presentation model [JarvisTile].
 * Platform-neutral; Android glass (backdrop blur, AGSL glow/background) plugs in via [GlassBackend].
 */
object JarvisShellRenderer {
    fun interface SlotContent { fun draw(p: ContinuumPainter, box: Box) }

    /** Native glass hooks (Android: JarvisGlassRenderer). Null = portable fallback (JVM preview, old APIs). */
    interface GlassBackend {
        /** true if the backend drew the whole page background (e.g. AGSL shader). */
        fun drawBackground(width: Float, height: Float, timeMs: Long): Boolean
        /** Draws blurred backdrop + rim glow for one panel in the current transform; true if done natively. */
        fun drawPanelGlass(box: Box, radius: Float, glow: Float): Boolean
        /** Start a depth layer for one panel (Android: Camera rotationX/Y + RenderEffect depth blur). true if handled. */
        fun beginDepth(box: Box, depth: JarvisMotion.Depth.State): Boolean = false
        fun endDepth() {}
    }

    /** Depth layer per slot (Z-Ebenen). Tiles/dialog/input near, side panels mid, frame elements far. */
    fun layerOf(slot: Slot?): JarvisMotion.Depth.Layer = when (slot) {
        null, Slot.DIALOG, Slot.INPUT -> JarvisMotion.Depth.Layer.NEAR
        Slot.COCKPIT_PRIMARY, Slot.COCKPIT_SECONDARY, Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL -> JarvisMotion.Depth.Layer.MID
        else -> JarvisMotion.Depth.Layer.FAR
    }

    private val bare = setOf(Slot.AVATAR, Slot.TOP_BAR)

    fun draw(p: ContinuumPainter, shell: Shell, content: Map<Slot, SlotContent> = emptyMap(),
             elapsedMs: Long = Long.MAX_VALUE / 4, animatorScale: Float = 1f,
             tiles: List<JarvisTile> = JarvisTiles.reference, glass: GlassBackend? = null, timeMs: Long = 0L) {
        if (glass?.drawBackground(shell.width, shell.height, timeMs) != true) background(p, shell.width, shell.height)
        val cardSlots = shell.boxes.keys.filter { it !in bare && it != Slot.TILES }
        val tileBoxes = shell.tileBoxes()
        val cards = cardSlots.map { shell[it]!! } + tileBoxes
        val cx = shell.width / 2f
        val ranks = JarvisMotion.centerFirstRanks(cards.map { it.centerX }, cx)
        shell[Slot.AVATAR]?.let { box ->
            val dy = JarvisMotion.idleOffset(timeMs, animatorScale = animatorScale)
            p.save(); p.translate(0f, dy)
            (content[Slot.AVATAR] ?: SlotContent(::avatarStandIn)).draw(p, box)
            p.restore()
        }
        shell[Slot.TOP_BAR]?.let { (content[Slot.TOP_BAR] ?: SlotContent(::topBar)).draw(p, it) }
        cards.forEachIndexed { i, box ->
            val f = JarvisMotion.frame(JarvisMotion.panelSpecFor(box.centerX, cx),
                elapsedMs - JarvisMotion.staggerStart(ranks[i], animatorScale = animatorScale), animatorScale)
            val slot = cardSlots.getOrNull(i)
            val tileIndex = i - cardSlots.size
            if (f.alpha <= 0f || f.scale <= 0f) return@forEachIndexed
            val depth = JarvisMotion.Depth.state(layerOf(slot), box.centerX, box.centerY(), shell.width, shell.height, timeMs, animatorScale)
            p.save()
            p.translate(f.dx + depth.dx, f.dy + depth.dy)
            val k = f.scale * depth.scale
            p.scale(k, k, box.centerX, box.centerY())
            val depthNative = glass?.beginDepth(box, depth) == true
            val r = JarvisDesign.Metrics.radiusCard
            val native = glass?.drawPanelGlass(box, r, f.glow) == true
            JarvisNeon.glassCard(p, box.left, box.top, box.right, box.bottom, r,
                if (f.glow > .5f) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.NORMAL, f.glow > .5f, f.alpha, native)
            when {
                slot != null && content[slot] != null -> content[slot]!!.draw(p, box)
                slot == Slot.NAV_RAIL -> JarvisTiles.drawRail(p, box, labelled = shell.rail != JarvisShellLayout.Rail.ICONS)
                slot == Slot.BOTTOM_NAV -> JarvisTiles.drawBottomNav(p, box)
                slot != null -> header(p, box, label(slot))
                tileIndex < tiles.size -> JarvisTiles.draw(p, box, tiles[tileIndex])
            }
            if (depthNative) glass?.endDepth()
            p.restore()
        }
    }

    private fun Box.centerY() = (top + bottom) / 2f

    /** Portable background: #050814→#0A1128, soft cyan grid, bokeh dust (deterministic). */
    fun background(p: ContinuumPainter, w: Float, h: Float) {
        p.fillRect(0f, 0f, w, h, Fill.Linear(0f, 0f, 0f, h, intArrayOf(Colors.background, Colors.backgroundBottom)))
        JarvisNeon.halo(p, w / 2f, h * .35f, maxOf(w, h) * .55f, Colors.violet, .10f)
        val step = 48f
        p.setAdditive(true)
        var x = 0f; while (x <= w) { p.drawLine(x, 0f, x, h, 3f, Fill.Solid(Colors.cyan), .018f); p.drawLine(x, 0f, x, h, .6f, Fill.Solid(Colors.cyan), .035f); x += step }
        var y = 0f; while (y <= h) { p.drawLine(0f, y, w, y, 3f, Fill.Solid(Colors.cyan), .018f); p.drawLine(0f, y, w, y, .6f, Fill.Solid(Colors.cyan), .035f); y += step }
        p.setAdditive(false)
        bokeh(p, w, h)
    }

    /** Bokeh dust (deterministic positions), also drawn on top of the AGSL background. */
    fun bokeh(p: ContinuumPainter, w: Float, h: Float) {
        p.setAdditive(true)
        var seed = 0x5EED
        repeat(((w * h) / 9000f).toInt().coerceIn(20, 160)) {
            seed = seed * 1103515245 + 12345
            val bx = ((seed ushr 8) % 10000) / 10000f * w; seed = seed * 1103515245 + 12345
            val by = ((seed ushr 8) % 10000) / 10000f * h; seed = seed * 1103515245 + 12345
            val br = 1f + ((seed ushr 8) % 100) / 100f * 5f
            val c = if (it % 3 == 0) Colors.violet else Colors.cyan
            p.fillCircle(bx, by, br * 2.2f, Fill.Radial(bx, by, br * 2.2f, intArrayOf(Argb.withAlpha(c, .22f), 0)))
        }
        p.setAdditive(false)
    }

    fun label(slot: Slot) = when (slot) {
        Slot.NAV_RAIL, Slot.BOTTOM_NAV -> "Navigation"; Slot.DIALOG -> "JARVIS"
        Slot.INPUT -> "Nachricht an Continuum …"; Slot.COCKPIT_PRIMARY -> "Kern-Status"; Slot.COCKPIT_SECONDARY -> "System-Status"
        Slot.ROW_AGENTS -> "Aktive Agenten"; Slot.ROW_TASKS -> "Aktueller Ablauf"; Slot.ROW_MEMORY -> "Gedächtnis-Aktivität"
        Slot.ROW_MODEL -> "Lokales Modell"; Slot.STATUS -> "System- & Continuum-Status"; else -> slot.name
    }

    /** Panel header (title only; values come from Kern via SlotContent). */
    fun header(p: ContinuumPainter, b: Box, text: String) {
        val style = JarvisDesign.Type.title
        val y = if (b.height < 70f) b.top + b.height / 2f + p.capHeight(style) / 2f else b.top + 14f + p.capHeight(style)
        p.drawText(fit(p, text, style, b.width - 24f), b.left + 12f, y, style, Fill.Solid(Colors.text))
    }

    fun fit(p: ContinuumPainter, text: String, style: TextStyle, maxWidth: Float): String {
        if (maxWidth <= 0f) return ""
        if (p.measureText(text, style) <= maxWidth) return text
        var end = text.length
        while (end > 1 && p.measureText(text.substring(0, end) + "\u2026", style) > maxWidth) end--
        return text.substring(0, end).trimEnd() + "\u2026"
    }

    private fun topBar(p: ContinuumPainter, b: Box) {
        val s = JarvisDesign.Type.brand
        JarvisNeon.text(p, "CONTINUUM", b.centerX, b.top + b.height / 2f + p.capHeight(s) / 2f, s, Colors.cyan, TextAlign.CENTER)
    }

    /** Stand-in until Jarvis Avatar draws AVATAR: tilted atom rings, head halo, brain orb (no fake data). */
    private fun avatarStandIn(p: ContinuumPainter, b: Box) {
        val headX = b.left + b.width * .4f; val headY = b.top + b.height * .42f
        val r = minOf(b.width, b.height) * .3f
        JarvisNeon.halo(p, headX, headY, r * 1.7f, Colors.cyan, .2f)
        p.fillOval(headX - r * .5f, headY - r * .78f, headX + r * .5f, headY + r * .78f,
            Fill.Radial(headX, headY - r * .3f, r, intArrayOf(Argb.of(110, 0, 200, 255), Argb.of(25, 0x8A, 0x2B, 0xE2))))
        for ((k, deg) in listOf(1.15f to -18f, 1f to 14f, .85f to 38f)) {
            p.save(); p.rotate(deg, headX, headY); JarvisNeon.ring(p, headX, headY, r * k, r * k * .26f, 1.1f); p.restore()
        }
        val orbX = b.left + b.width * .76f; val orbY = b.top + b.height * .62f; val o = r * .6f
        JarvisNeon.halo(p, orbX, orbY, o * 1.9f, Argb.rgb(0x4D, 0, 0xFF), .32f)
        p.fillCircle(orbX, orbY, o, Fill.Radial(orbX, orbY, o, intArrayOf(Argb.of(200, 200, 255, 255), Argb.of(150, 0, 200, 255), Argb.of(120, 0x4D, 0, 0xFF))))
        JarvisNeon.ring(p, orbX, orbY + o * 1.05f, o * 1.25f, o * .26f, 1.5f)
    }
}
