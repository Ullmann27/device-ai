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
        Slot.COCKPIT_PRIMARY, Slot.COCKPIT_SECONDARY, Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL,
        Slot.WORK_DOCUMENT, Slot.WORK_PROCESS, Slot.WORK_DRAFT -> JarvisMotion.Depth.Layer.MID
        else -> JarvisMotion.Depth.Layer.FAR
    }

    private val foregroundSlots = setOf(Slot.TOP_BAR, Slot.CONTINUUM_HEADER, Slot.DIALOG,
        Slot.CAMERA_STATUS, Slot.COCKPIT_SECONDARY)
    private val bare = setOf(Slot.AVATAR) + foregroundSlots

    /** Drawn above the transparent native head. Keeps speech and status readable over the 3D silhouette. */
    fun drawForeground(p: ContinuumPainter, shell: Shell, content: Map<Slot, SlotContent>,
                       deckClearance: Float = 0f) {
        for (slot in foregroundSlots) {
            val box = shell[slot] ?: continue
            val clear = if (slot == Slot.COCKPIT_SECONDARY && deckClearance > 0f)
                JarvisDeckClearance.local(deckClearance, .65f) else 0f
            if (clear >= .999f) continue
            if (clear > 0f) p.saveAlpha(JarvisDeckClearance.alpha(clear)) else p.save()
            p.translate(JarvisDeckClearance.dx(clear, box.centerX, shell.width), JarvisDeckClearance.dy(clear))
            val k = JarvisDeckClearance.scale(clear)
            p.scale(k, k, box.centerX, box.centerY())
            if (slot != Slot.TOP_BAR && slot != Slot.CONTINUUM_HEADER) {
                JarvisReferenceHud.surface(p, box, 14f, Colors.cyan,
                    if (slot == Slot.COCKPIT_SECONDARY || slot == Slot.CAMERA_STATUS) Colors.violetRim else Colors.cyanDeep)
            }
            content[slot]?.draw(p, box)
            p.restore()
        }
    }

    fun draw(p: ContinuumPainter, shell: Shell, content: Map<Slot, SlotContent> = emptyMap(),
             elapsedMs: Long = Long.MAX_VALUE / 4, animatorScale: Float = 1f,
             tiles: List<JarvisTile> = JarvisTiles.reference, glass: GlassBackend? = null, timeMs: Long = 0L,
             deferForeground: Boolean = false, deckClearance: Float = 0f,
             missionClearance: Float = deckClearance, luxuryDepth: Boolean = false,
             depthBudget: JarvisRenderQuality.Budget? = null, enableWings: Boolean = luxuryDepth,
             exiting: Boolean = false, workElapsedMs: Long = elapsedMs,
             clearIdle: Boolean = false, resultForward: Boolean = false) {
        background(p, shell.width, shell.height)
        val backdropHeight = shell[Slot.INPUT]?.bottom ?: shell[Slot.AVATAR]?.bottom ?: shell.height
        JarvisReferenceHud.backdrop(p, shell.width, backdropHeight)
        val dolly = 1f
        p.save(); p.scale(dolly, dolly, shell.width / 2f, shell.height / 2f)
        val cardSlots = shell.boxes.keys.filter { it !in bare && it != Slot.TILES }
        val tileBoxes = shell.tileBoxes()
        val cards = cardSlots.map { shell[it]!! } + tileBoxes
        val cx = shell.width / 2f
        val ranks = JarvisMotion.centerFirstRanks(cards.map { it.centerX }, cx)
        val orbX = shell.orbBounds?.centerX ?: shell.width * .70f
        val orbY = shell.orbBounds?.centerY ?: shell.height * .30f
        shell[Slot.AVATAR]?.let { box ->
            val dy = 0f
            p.save(); p.translate(0f, dy)
            (content[Slot.AVATAR] ?: SlotContent(::avatarStandIn)).draw(p, box)
            p.restore()
        }
        cards.forEachIndexed { i, box ->
            val slot = cardSlots.getOrNull(i)
            val tileIndex = i - cardSlots.size
            val kind = JarvisCinemaEntrance.kindOf(slot)
            val cinemaElapsed = if (kind == JarvisCinemaEntrance.Kind.WORK_WINDOW) workElapsedMs else elapsedMs
            val side = if (box.centerX >= cx) 1f else -1f
            val rank = JarvisCinemaEntrance.rankOf(slot, ranks[i])
            val role = JarvisCinemaEntrance.stageRole(slot)
            val cinema = when {
                exiting && clearIdle && role == JarvisCinemaEntrance.StageRole.DEPART ->
                    JarvisCinemaEntrance.depart(rank, JarvisCinemaEntrance.EXIT_MS, side, depthBudget, animatorScale)
                exiting -> JarvisCinemaEntrance.pose(
                    kind, rank, cinemaElapsed, exiting = true,
                    side = side, budget = depthBudget, animatorScale = animatorScale)
                clearIdle && role == JarvisCinemaEntrance.StageRole.DEPART ->
                    JarvisCinemaEntrance.depart(rank, elapsedMs, side, depthBudget, animatorScale)
                clearIdle && role == JarvisCinemaEntrance.StageRole.STAY ->
                    JarvisCinemaEntrance.SETTLED
                resultForward && slot == Slot.WORK_DRAFT ->
                    JarvisCinemaEntrance.resultAdvance(workElapsedMs, depthBudget, animatorScale)
                else -> JarvisCinemaEntrance.pose(
                    kind, rank, cinemaElapsed, exiting = false,
                    side = side, budget = depthBudget, animatorScale = animatorScale)
            }
            val f = JarvisMotion.frame(JarvisMotion.panelSpecFor(box.centerX, cx),
                elapsedMs - JarvisMotion.staggerStart(ranks[i], animatorScale = animatorScale), animatorScale)
            if (cinema.hidden && cinema.rimAlpha < 0.01f) return@forEachIndexed
            if (f.scale <= 0f) return@forEachIndexed
            // Airbus deck: while a work window is open, tiles and lower rows make room instead of being covered.
            val sourceClearance = JarvisDeckClearance.clearance(slot, deckClearance, missionClearance)
            val clear = if (sourceClearance > 0f && JarvisDeckClearance.clears(slot))
                JarvisDeckClearance.local(sourceClearance, JarvisDeckClearance.order(slot, box.centerX, shell.width)) else 0f
            if (clear >= .999f) return@forEachIndexed
            val visible = maxOf(cinema.rimAlpha, cinema.glassAlpha, cinema.contentAlpha, f.alpha)
            val opacity = visible * if (clear > 0f) JarvisDeckClearance.alpha(clear) else 1f
            if (opacity < 0.01f) return@forEachIndexed
            if (opacity < 1f) p.saveAlpha(opacity) else p.save()
            p.translate(f.dx + cinema.dx + JarvisDeckClearance.dx(clear, box.centerX, shell.width),
                f.dy + cinema.dy + JarvisDeckClearance.dy(clear))
            val k = f.scale * cinema.scale * JarvisDeckClearance.scale(clear)
            p.scale(k, k, box.centerX, box.centerY())
            if (cinema.tiltY != 0f) p.rotate(cinema.tiltY, box.centerX, box.centerY())
            val depthState = when {
                cinema.tiltY != 0f || cinema.scale < 0.999f ->
                    JarvisMotion.Depth.State(cinema.scale, 0f, 0f, cinema.tiltY, cinema.dx, cinema.dy)
                enableWings && depthBudget != null ->
                    PerimeterWingChoreographer.depth(slot, box, shell.width, clear, true, depthBudget)
                else -> JarvisMotion.Depth.FLAT
            }
            val depthNative = depthState != JarvisMotion.Depth.FLAT &&
                glass?.beginDepth(box, depthState) == true
            val tile = if (slot == null) tiles.getOrNull(tileIndex) else null
            val ownsSurface = slot == Slot.BOTTOM_NAV || (content[slot] != null &&
                slot in setOf(Slot.ROW_MEMORY, Slot.ROW_MODEL, Slot.STATUS))
            val r = JarvisDesign.Metrics.radiusCard
            if (cinema.glassAlpha < 0.02f && cinema.rimAlpha > 0.02f) {
                p.strokeRoundRect(box.left, box.top, box.right, box.bottom, r, 1.6f, Fill.Solid(Colors.cyan), cinema.rimAlpha)
            } else if (tile != null) {
                if (cinema.glassAlpha < 1f) p.saveAlpha(cinema.glassAlpha)
                JarvisTiles.drawSurface(p, box, tile, f.glow > .5f,
                    if (animatorScale > 0f) timeMs else 0L,
                    luxuryDepth = luxuryDepth, lightSourceX = orbX, lightSourceY = orbY)
                if (cinema.glassAlpha < 1f) p.restore()
            } else if (!ownsSurface) {
                // Static HUD panels do not need repeated full-screen backdrop-blur copies.
                if (f.glow > .5f) glass?.bloom(JarvisNeon.BLOOM_RADIUS_DP, f.glow * cinema.glassAlpha) { q ->
                    q.strokeRoundRect(box.left, box.top, box.right, box.bottom, r, 2f, Fill.Solid(Colors.cyan))
                }
                if (cinema.glassAlpha < 1f) p.saveAlpha(cinema.glassAlpha)
                JarvisReferenceHud.surface(p, box, r, Colors.cyan,
                    if (slot == Slot.INPUT) Colors.violetRim else Colors.cyanDeep,
                    interaction = if (f.glow > .5f) JarvisReferenceHud.Interaction.SELECTED
                        else JarvisReferenceHud.Interaction.NORMAL)
                if (cinema.glassAlpha < 1f) p.restore()
            }
            if (cinema.contentAlpha > 0.01f) {
                if (cinema.contentAlpha < 1f) p.saveAlpha(cinema.contentAlpha)
                when {
                    slot != null && content[slot] != null -> content[slot]!!.draw(p, box)
                    slot == Slot.NAV_RAIL -> JarvisTiles.drawRail(p, box, labelled = shell.rail != JarvisShellLayout.Rail.ICONS)
                    slot == Slot.BOTTOM_NAV -> JarvisTiles.drawBottomNav(p, box,
                        timeMs = if (animatorScale > 0f) timeMs else 0L,
                        cinemaElapsedMs = cinemaElapsed, cinemaExiting = exiting,
                        cinemaBudget = depthBudget, animatorScale = animatorScale)
                    slot != null -> header(p, box, label(slot))
                    tileIndex < tiles.size -> JarvisTiles.draw(p, box, tiles[tileIndex])
                }
                if (cinema.contentAlpha < 1f) p.restore()
            }
            if (depthNative) glass?.endDepth()
            p.restore()
        }
        if (clearIdle && !exiting) {
            val reduced = animatorScale <= 0f || depthBudget?.animate == false
            val built = if (reduced) 1f else HollywoodFuiConstruct.flight(workElapsedMs)
            JarvisCinemaEntrance.drawStagePlatform(p, shell, built, reduced)
        }
        p.restore()
        JarvisReferenceHud.frame(p, shell.width, shell[Slot.STATUS]?.bottom?.plus(12f) ?: shell.height)
        if (!deferForeground) drawForeground(p, shell, content, deckClearance)
    }

    private fun Box.centerY() = (top + bottom) / 2f

    /** Portable ref5 background: near-black navy, faint lower-edge haze and deterministic dust. */
    fun background(p: ContinuumPainter, w: Float, h: Float) {
        p.fillRect(0f, 0f, w, h, Fill.Linear(0f, 0f, 0f, h, intArrayOf(Colors.background, Colors.backgroundBottom)))
        JarvisNeon.halo(p, w * .45f, h * .92f, maxOf(w, h) * .55f, Colors.backgroundHaze, .18f)
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
        Slot.ROW_MODEL -> "Lokales Modell"; Slot.STATUS -> "System- & Continuum-Status"
        Slot.WORK_DOCUMENT -> "Dokument"; Slot.WORK_PROCESS -> "Verarbeitung"
        Slot.WORK_DRAFT -> "Zusammenfassung – Entwurf"; else -> slot.name
    }

    /** Panel header (title only; values come from Kern via SlotContent). */
    fun header(p: ContinuumPainter, b: Box, text: String) {
        val style = JarvisDesign.Type.title
        val y = if (b.height < 70f) b.top + b.height / 2f + p.capHeight(style) / 2f else b.top + 14f + p.capHeight(style)
        p.drawText(fit(p, text, style, b.width - 24f), b.left + 12f, y, style, Fill.Solid(Colors.text))
    }

    /** Shrink before bounded ellipsis. */
    fun shrinkToFit(p: ContinuumPainter, text: String, style: TextStyle, maxWidth: Float,
                    minScale: Float = .82f): TextStyle {
        if (maxWidth <= 0f) return style
        val width = p.measureText(text, style)
        if (width <= maxWidth || width <= 0f) return style
        return style.copy(size = style.size * maxOf(minScale, maxWidth / width))
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
