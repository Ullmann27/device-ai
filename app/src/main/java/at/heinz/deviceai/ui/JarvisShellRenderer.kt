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
        /** Real bloom (blurred additive light) around [emissive] drawing; false → portable JarvisNeon fallback. */
        fun bloom(radiusDp: Float, intensity: Float, emissive: (ContinuumPainter) -> Unit): Boolean = false
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
        JarvisReferenceHud.backdrop(p, shell.width, shell.height)
        val dolly = if (shell.ref5Grid) 1f else JarvisMotion.Depth.dolly(timeMs, animatorScale)
        p.save(); p.scale(dolly, dolly, shell.width / 2f, shell.height / 2f)
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
            val depth = if (shell.ref5Grid) JarvisMotion.Depth.FLAT else
                JarvisMotion.Depth.state(layerOf(slot), box.centerX, box.centerY(), shell.width, shell.height, timeMs, animatorScale)
            p.save()
            p.translate(f.dx + depth.dx, f.dy + depth.dy)
            val k = f.scale * depth.scale
            p.scale(k, k, box.centerX, box.centerY())
            val depthNative = !shell.ref5Grid && glass?.beginDepth(box, depth) == true
            val tile = if (slot == null) tiles.getOrNull(tileIndex) else null
            val ownsSurface = shell.ref5Grid && content[slot] != null &&
                slot in setOf(Slot.ROW_MEMORY, Slot.ROW_MODEL, Slot.STATUS)
            if (tile != null) {
                JarvisTiles.drawSurface(p, box, tile, f.glow > .5f)
            } else if (!ownsSurface) {
                val r = JarvisDesign.Metrics.radiusCard
                // Static HUD panels do not need repeated full-screen backdrop-blur copies.
                val native = !shell.ref5Grid && glass?.drawPanelGlass(box, r, f.glow) == true
                if (f.glow > .5f) glass?.bloom(JarvisNeon.BLOOM_RADIUS_DP, f.glow) { q ->
                    q.strokeRoundRect(box.left, box.top, box.right, box.bottom, r, 2f, Fill.Solid(Colors.cyan))
                }
                JarvisNeon.glassCard(p, box.left, box.top, box.right, box.bottom, r,
                    if (f.glow > .5f) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.NORMAL,
                    f.glow > .5f, f.alpha, native,
                    rimEnd = if (slot == Slot.COCKPIT_SECONDARY) Colors.cardRimEndViolet else Colors.cardRimEnd)
            }
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
        p.restore()
        JarvisReferenceHud.frame(p, shell.width, shell.height)
    }

    private fun Box.centerY() = (top + bottom) / 2f

    /** Portable ref5 background: near-black navy, faint lower-edge haze and deterministic dust. */
    fun background(p: ContinuumPainter, w: Float, h: Float) {
        p.fillRect(0f, 0f, w, h, Fill.Linear(0f, 0f, 0f, h, intArrayOf(Colors.background, Colors.backgroundBottom)))
        JarvisNeon.halo(p, w * .45f, h * .92f, maxOf(w, h) * .70f, Colors.backgroundHaze, .65f)
        val step = 48f
        p.setAdditive(true)
        var x = 0f; while (x <= w) { p.drawLine(x, 0f, x, h, 3f, Fill.Solid(Colors.cyan), .004f); p.drawLine(x, 0f, x, h, .6f, Fill.Solid(Colors.cyan), .008f); x += step }
        var y = 0f; while (y <= h) { p.drawLine(0f, y, w, y, 3f, Fill.Solid(Colors.cyan), .004f); p.drawLine(0f, y, w, y, .6f, Fill.Solid(Colors.cyan), .008f); y += step }
        p.setAdditive(false)
        bokeh(p, w, h)
    }

    /** Upper bounds for the portable particle layers (fallback path; the AGSL path is per-pixel, fixed cost). */
    const val BOKEH_MAX = 60
    const val DUST_MAX = 240

    /**
     * ref5 (docs/JARVIS-REF5-FARBEN.md): the clean background varies only +11 (G) / +21 (B) at p95, so bokeh may add
     * at most ~5 % cyan; dust = 2 motes of ~2 x 2 px (r 0.65 dp) in 5940 px² → one per ~1200 dp² (small sample).
     */
    const val BOKEH_ALPHA = .05f
    const val DUST_AREA_DP2 = 1200f
    const val DUST_RADIUS_DP = .65f

    /** Bokeh discs and fine dust (deterministic positions, Signatur 15 "Staub"); counts capped. */
    fun bokeh(p: ContinuumPainter, w: Float, h: Float) {
        p.setAdditive(true)
        var seed = 0x5EED
        repeat(((w * h) / 12000f).toInt().coerceIn(16, BOKEH_MAX)) {
            seed = seed * 1103515245 + 12345
            val bx = ((seed ushr 8) % 10000) / 10000f * w; seed = seed * 1103515245 + 12345
            val by = ((seed ushr 8) % 10000) / 10000f * h; seed = seed * 1103515245 + 12345
            val br = 1f + ((seed ushr 8) % 100) / 100f * 5f
            val c = if (it % 3 == 0) Colors.violet else Colors.cyan
            p.fillCircle(bx, by, br * 2.2f, Fill.Radial(bx, by, br * 2.2f, intArrayOf(Argb.withAlpha(c, BOKEH_ALPHA), 0)))
        }
        repeat(((w * h) / DUST_AREA_DP2).toInt().coerceIn(20, DUST_MAX)) {
            seed = seed * 1103515245 + 12345
            val dx = ((seed ushr 8) % 10000) / 10000f * w; seed = seed * 1103515245 + 12345
            val dy = ((seed ushr 8) % 10000) / 10000f * h
            p.fillCircle(dx, dy, DUST_RADIUS_DP, Fill.Solid(Colors.dust))
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
        JarvisNeon.text(p, "CONTINUUM", b.left + b.width * .17f, b.top + b.height / 2f + p.capHeight(s) / 2f, s, Colors.cyan, TextAlign.LEFT)
        // ref5: chips start at 52.7 % of the width, vertically centred in the header slot.
        JarvisNeon.headerChips(p, b.left + b.width * .527f, b.top + (b.height - JarvisDesign.Styles.pill.height) / 2f)
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
