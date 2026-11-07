package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Shared owner-reference light layers.
 * Decoration never represents system state.
 * Geometry stays separate from controls.
 */
object JarvisReferenceHud {
    enum class Interaction { NORMAL, PRESSED, SELECTED, DISABLED, LOADING }

    private val cyan = Fill.Solid(Colors.cyan)
    private val white = Fill.Solid(Colors.glowCore)
    private val ribbonGradient = Fill.Linear(0f, 0f, 1f, 0f,
        intArrayOf(Colors.violet, Colors.magenta, Colors.cyan, Colors.violet))
    private data class MaterialKey(val box: Box, val accent: Int, val end: Int,
                                   val selected: Boolean, val layer: Int)
    private data class CornerKey(val box: Box, val radius: Float, val accent: Int, val end: Int)
    private data class CornerMaterial(val upper: VectorPath, val lower: VectorPath,
                                      val upperLight: Fill, val lowerLight: Fill)
    private val corners = HashMap<CornerKey, CornerMaterial>()
    private val heroRibbons = List(12) { i ->
        val o = i * .012f
        VectorPath().moveTo(.145f, .267f + o)
            .cubicTo(.24f, .23f + o, .29f, .49f - o, .43f, .428f + o * .45f)
            .cubicTo(.57f, .30f + o, .58f, .49f + o * .3f, .68f, .45f + o * .3f)
            .cubicTo(.84f, .34f + o, .88f, .60f - o, 1.02f, .385f + o)
    }
    private val railRibbons = List(12) { i ->
        val o = i * .012f
        VectorPath().moveTo(-.02f, .70f + o)
            .cubicTo(.028f, .80f - o * .2f, .103f, .75f + o, .159f, .87f + o * .18f)
    }

    /** No global camera movement: control geometry and text stay exactly under their hit targets. */
    fun backdrop(p: ContinuumPainter, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        p.save()
        p.clipRect(0f, 0f, width, height)
        // 59509: saturated violet/cyan light behind the hero and a magenta glow along the lower-left edge.
        JarvisNeon.halo(p, width * .34f, height * .25f, width * .27f, Colors.violet, .22f)
        JarvisNeon.halo(p, width * .67f, height * .37f, width * .22f, Colors.cyan, .14f)
        JarvisNeon.halo(p, width * .04f, height * .86f, width * .16f, Colors.magenta, .16f)
        JarvisNeon.halo(p, width * .55f, height * .50f, width * .30f, Colors.violet, .08f)
        p.translate(0f, height * .5f)
        p.scale(width, height * .5f)
        p.setAdditive(true)
        (heroRibbons + railRibbons).forEachIndexed { i, path ->
            p.strokePath(path, .009f, ribbonGradient, .04f)
            p.strokePath(path, .0022f, ribbonGradient, .16f)
            p.strokePath(path, if (i % 4 == 0) .00085f else .00035f, ribbonGradient, if (i % 4 == 0) .92f else .48f)
        }
        p.setAdditive(false)
        p.restore()
    }

    /** Thin hot core, broad colored halo and stronger corner sources, all inside the available window. */
    fun frame(p: ContinuumPainter, width: Float, height: Float) {
        if (width < 32f || height < 32f) return
        val u = (width / 1536f).coerceIn(.45f, 1.5f)
        val inset = 7f * u
        val r = 27f * u
        val edge = Fill.Linear(0f, 0f, width, height,
            intArrayOf(Colors.violetRim, Colors.cyan, Colors.violet, Colors.cyan, Colors.violetRim),
            floatArrayOf(0f, .24f, .50f, .77f, 1f))
        p.save(); p.clipRect(0f, 0f, width, height)
        p.setAdditive(true)
        p.strokeRoundRect(inset, inset, width - inset, height - inset, r, 26f * u, edge, .055f)
        p.strokeRoundRect(inset, inset, width - inset, height - inset, r, 12f * u, edge, .12f)
        p.strokeRoundRect(inset, inset, width - inset, height - inset, r, 5f * u, edge, .30f)
        p.strokeRoundRect(inset, inset, width - inset, height - inset, r, 1.7f * u, edge, .98f)
        p.strokeRoundRect(inset + 4f * u, inset + 4f * u, width - inset - 4f * u,
            height - inset - 4f * u, r - 3f * u, .65f * u, white, .60f)
        p.setAdditive(false)
        for (x in floatArrayOf(inset + r * .5f, width - inset - r * .5f))
            for (y in floatArrayOf(inset + r * .5f, height - inset - r * .5f))
                JarvisNeon.halo(p, x, y, 34f * u, Colors.violetRim, .40f)
        p.restore()
    }

    /** Glass material for functional controls; glow can overlap visually without overlapping hitboxes. */
    fun surface(p: ContinuumPainter, b: Box, radius: Float, accent: Int = Colors.cyan,
                endAccent: Int = Colors.violet, interaction: Interaction = Interaction.NORMAL,
                decorativeRibbons: Boolean = false) {
        if (b.width <= 2f || b.height <= 2f) return
        val enabled = interaction != Interaction.DISABLED
        val selected = interaction == Interaction.SELECTED || interaction == Interaction.PRESSED
        val opacity = if (enabled) 1f else .42f
        // Dark glass, localized edge light.
        val glow = if (selected) 1.38f else 1f
        val r = radius.coerceIn(1f, minOf(b.width, b.height) * .5f)
        val edge = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 0)) {
            Fill.Linear(b.left, b.top, b.right, b.bottom,
                intArrayOf(Argb.mix(accent, Colors.activeWhite, .22f), accent,
                    Argb.mix(endAccent, Colors.activeWhite, .12f), endAccent),
                floatArrayOf(0f, .28f, .73f, 1f))
        }
        val body = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 1)) {
            Fill.Linear(b.left, b.top, b.right, b.bottom,
                intArrayOf(Argb.mix(Argb.rgb(6, 15, 31), accent, if (selected) .08f else .025f),
                    Argb.rgb(2, 6, 17), Argb.rgb(3, 8, 21),
                    Argb.mix(Argb.rgb(7, 12, 30), endAccent, if (selected) .07f else .025f)),
                floatArrayOf(0f, .34f, .72f, 1f))
        }
        val sheen = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 2)) {
            Fill.Linear(b.left, b.top, b.left, b.top + b.height * .16f,
                intArrayOf(Argb.withAlpha(Colors.glowCore, if (selected) .10f else .045f), 0))
        }
        p.setAdditive(true)
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, r, 10f, edge, .055f * opacity * glow)
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, r, 5f, edge, .17f * opacity * glow)
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, r, 3f, edge, .40f * opacity * glow)
        p.setAdditive(false)
        p.fillRoundRect(b.left, b.top, b.right, b.bottom, r, body, opacity)
        p.save(); p.clipRoundRect(b.left, b.top, b.right, b.bottom, r)
        p.fillRect(b.left, b.top, b.right, b.top + b.height * .16f, sheen, opacity)
        // Light stays inside the bevel.
        p.setAdditive(true)
        p.strokeRoundRect(b.left + 1.2f, b.top + 1.2f, b.right - 1.2f, b.bottom - 1.2f,
            (r - 1.2f).coerceAtLeast(1f), 3.2f, edge, .18f * opacity * glow)
        p.setAdditive(false)
        if (decorativeRibbons) {
            // Keep text centers free.
            p.setAdditive(true)
            p.drawLine(b.left + r, b.top + .8f, b.left + b.width * .42f, b.top + .8f,
                .8f, white, .68f * opacity)
            p.drawLine(b.right - b.width * .32f, b.bottom - .8f, b.right - r, b.bottom - .8f,
                .8f, white, .52f * opacity)
            p.setAdditive(false)
        }
        p.restore()
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, r, if (selected) 1.9f else 1.4f, edge, opacity)
        val inner = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 3)) {
            Fill.Linear(b.left, b.top, b.right, b.bottom,
                intArrayOf(Colors.glowCore, 0, Argb.withAlpha(endAccent, .65f)))
        }
        p.strokeRoundRect(b.left + 1.2f, b.top + 1.2f, b.right - 1.2f, b.bottom - 1.2f,
            (r - 1f).coerceAtLeast(1f), .75f, inner, .65f * opacity)
        cornerLight(p, b, r, accent, endAccent, opacity, glow)
    }

    private fun cornerLight(p: ContinuumPainter, b: Box, r: Float, accent: Int,
                            endAccent: Int, opacity: Float, glow: Float) {
        val key = CornerKey(b, r, accent, endAccent)
        val material = corners[key] ?: run {
            if (corners.size >= 128) corners.clear()
            val span = minOf(32f, b.width * .4f, b.height * .5f).coerceAtLeast(r)
            val k = r * .552285f
            fun light(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) =
                Fill.Linear(x0, y0, x1, y1, intArrayOf(Argb.withAlpha(color, 0f), color,
                    Argb.mix(color, Colors.activeWhite, .68f), color, Argb.withAlpha(color, 0f)),
                    floatArrayOf(0f, .28f, .5f, .72f, 1f))
            CornerMaterial(
                VectorPath().moveTo(b.left, b.top + span).lineTo(b.left, b.top + r)
                    .cubicTo(b.left, b.top + r - k, b.left + r - k, b.top, b.left + r, b.top)
                    .lineTo(b.left + span, b.top),
                VectorPath().moveTo(b.right - span, b.bottom).lineTo(b.right - r, b.bottom)
                    .cubicTo(b.right - r + k, b.bottom, b.right, b.bottom - r + k, b.right, b.bottom - r)
                    .lineTo(b.right, b.bottom - span),
                light(b.left + span, b.top, b.left, b.top + span, accent),
                light(b.right - span, b.bottom, b.right, b.bottom - span, endAccent)
            ).also { corners[key] = it }
        }
        p.setAdditive(true)
        p.strokePath(material.upper, 7f, material.upperLight, .10f * opacity * glow)
        p.strokePath(material.lower, 7f, material.lowerLight, .10f * opacity * glow)
        p.strokePath(material.upper, 4f, material.upperLight, .38f * opacity * glow)
        p.strokePath(material.lower, 4f, material.lowerLight, .38f * opacity * glow)
        p.strokePath(material.upper, 1.3f, material.upperLight, .95f * opacity)
        p.strokePath(material.lower, 1.3f, material.lowerLight, .95f * opacity)
        p.setAdditive(false)
    }

    private val logoLeft = VectorPath().moveTo(46f, 3f).lineTo(46f, 25f).lineTo(17f, 66f)
        .lineTo(3f, 55f).close()
    private val logoRight = VectorPath().moveTo(52f, 3f).lineTo(52f, 25f).lineTo(80f, 66f)
        .lineTo(94f, 55f).close()
    private val logoBase = VectorPath().moveTo(19f, 69f).lineTo(33f, 55f).lineTo(48f, 68f)
        .lineTo(60f, 57f).lineTo(74f, 70f).lineTo(52f, 90f).lineTo(34f, 90f).close()

    /** The three-part triangular Device AI mark in the supplied image, not the old generic MODE glyph. */
    fun brandMark(p: ContinuumPainter, left: Float, top: Float, size: Float) {
        if (size <= 0f) return
        if (p.drawRaster("jarvis-reference/device-ai-mark.png", left, top, left + size, top + size)) return
        JarvisNeon.halo(p, left + size * .5f, top + size * .5f, size * .72f, Colors.cyan, .18f)
        p.save(); p.translate(left, top); p.scale(size / 96f, size / 96f)
        p.fillPath(logoLeft, Fill.Linear(0f, 0f, 45f, 70f, intArrayOf(Colors.activeWhite, Colors.cyan, Colors.cyanDeep)))
        p.fillPath(logoRight, Fill.Linear(55f, 0f, 86f, 66f, intArrayOf(Colors.glowCore, Colors.cyan, Colors.cyanDeep)))
        p.fillPath(logoBase, Fill.Linear(18f, 58f, 75f, 85f, intArrayOf(Colors.cyan, Colors.violetRim, Colors.magenta)))
        p.restore()
    }

    fun crown(p: ContinuumPainter, b: Box) {
        val path = VectorPath().moveTo(b.left, b.top + b.height * .17f)
            .lineTo(b.left + b.width * .28f, b.top + b.height * .44f)
            .lineTo(b.centerX, b.top).lineTo(b.left + b.width * .72f, b.top + b.height * .44f)
            .lineTo(b.right, b.top + b.height * .17f).lineTo(b.right - b.width * .12f, b.bottom)
            .lineTo(b.left + b.width * .12f, b.bottom).close()
        p.fillPath(path, cyan)
    }
}
