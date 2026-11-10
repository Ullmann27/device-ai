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
                                   val selected: Boolean, val layer: Int, val feature: Boolean)
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
        JarvisNeon.halo(p, width * .34f, height * .25f, width * .32f, Colors.violet, .34f)
        JarvisNeon.halo(p, width * .67f, height * .32f, width * .30f, Colors.cyan, .26f)
        JarvisNeon.halo(p, width * .08f, height * .72f, width * .22f, Colors.magenta, .22f)
        JarvisNeon.halo(p, width * .50f, height * .78f, width * .36f, Colors.cyan, .16f)
        JarvisNeon.halo(p, width * .55f, height * .50f, width * .30f, Colors.violet, .12f)
        p.translate(0f, height * .5f)
        p.scale(width, height * .5f)
        p.setAdditive(true)
        (heroRibbons + railRibbons).forEachIndexed { i, path ->
            p.strokePath(path, .014f, ribbonGradient, .07f)
            p.strokePath(path, .0040f, ribbonGradient, .22f)
            p.strokePath(path, if (i % 4 == 0) .0012f else .00055f, ribbonGradient, if (i % 4 == 0) .96f else .58f)
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

    /** Dark glass with localized light. */
    fun surface(p: ContinuumPainter, b: Box, radius: Float, accent: Int = Colors.cyan,
                endAccent: Int = Colors.violet, interaction: Interaction = Interaction.NORMAL,
                decorativeRibbons: Boolean = false) {
        if (b.width <= 2f || b.height <= 2f) return
        val enabled = interaction != Interaction.DISABLED
        val selected = interaction == Interaction.SELECTED || interaction == Interaction.PRESSED
        val opacity = if (enabled) 1f else .42f
        val glow = if (!enabled) .25f else if (selected) 1.25f else 1f
        val feature = decorativeRibbons
        val r = radius.coerceIn(1f, minOf(b.width, b.height) * .5f)
        val depth = minOf(if (feature) 22f else 14f, b.width * .16f, b.height * .21f)
        val inset = minOf(1.2f, b.width * .15f, b.height * .15f)
        val edge = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 0, feature)) {
            Fill.Linear(b.left, b.top, b.right, b.top,
                intArrayOf(accent, Argb.mix(accent, Colors.cyanDeep, .72f),
                    Argb.mix(endAccent, Colors.violet, .35f), endAccent),
                floatArrayOf(0f, .32f, .74f, 1f))
        }
        val body = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 1, feature)) {
            val top = if (feature) Colors.tileFillStart else Argb.rgb(6, 18, 36)
            val middle = if (feature) Colors.tileFillMiddle else Argb.rgb(3, 12, 28)
            val bottom = if (feature) Colors.tileFillEnd else Argb.rgb(6, 16, 34)
            Fill.Linear(b.left, b.top, b.left, b.bottom,
                intArrayOf(Argb.mix(top, accent, if (selected) .08f else .03f), middle,
                    middle, Argb.mix(bottom, endAccent, .04f)),
                floatArrayOf(0f, .28f, .74f, 1f))
        }
        val sheen = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 2, feature)) {
            Fill.Linear(b.left, b.top, b.left, b.top + depth,
                intArrayOf(Argb.withAlpha(Colors.glowCore, if (selected) .18f else .11f), 0))
        }
        val bevel = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 3, feature)) {
            Fill.Linear(b.left, b.top, b.right, b.bottom,
                intArrayOf(Argb.withAlpha(Colors.glowCore, .28f), 0,
                    Argb.withAlpha(endAccent, .22f)), floatArrayOf(0f, .35f, 1f))
        }
        val thickness = JarvisVisualDepth.edgeThicknessDp()
            .coerceAtMost(minOf(b.width, b.height) * .12f)
        val rearDx = thickness * .55f
        val rearDy = thickness
        val rearBody = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 7, feature)) {
            Fill.Linear(b.left + rearDx, b.top + rearDy, b.left + rearDx, b.bottom + rearDy,
                intArrayOf(Argb.rgb(2, 8, 20), Argb.rgb(1, 6, 16)))
        }
        val slabBottom = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 8, feature)) {
            Fill.Linear(b.left, b.bottom, b.left, b.bottom + rearDy,
                intArrayOf(Argb.withAlpha(accent, .28f), Argb.withAlpha(Argb.rgb(4, 14, 28), .85f),
                    Argb.withAlpha(endAccent, .18f)))
        }
        val slabRight = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 9, feature)) {
            Fill.Linear(b.right, b.top, b.right + rearDx, b.top,
                intArrayOf(Argb.withAlpha(accent, .16f), Argb.withAlpha(Argb.rgb(4, 14, 28), .80f),
                    Argb.withAlpha(endAccent, .22f)))
        }
        val innerBounce = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 10, feature)) {
            Fill.Linear(b.left, b.top, b.right, b.bottom,
                intArrayOf(Argb.withAlpha(Colors.glowCore, .14f), 0, Argb.withAlpha(endAccent, .10f)),
                floatArrayOf(0f, .45f, 1f))
        }
        // Contact shadow on the plane below (decoration — control bounds stay on [b]).
        p.fillRoundRect(b.left + rearDx * .4f, b.top + rearDy * 1.15f,
            b.right + rearDx * 1.05f, b.bottom + rearDy * 1.65f, r,
            Fill.Solid(0xB3010510.toInt()), .50f * opacity)
        // Rear glass plate (z = −24 dp, projected by the 14° cap).
        p.fillRoundRect(b.left + rearDx, b.top + rearDy, b.right + rearDx, b.bottom + rearDy, r,
            rearBody, .70f * opacity)
        p.fillRect(b.left + r * .25f, b.bottom, b.right - r * .08f, b.bottom + rearDy,
            slabBottom, .80f * opacity)
        p.fillRect(b.right, b.top + r * .25f, b.right + rearDx, b.bottom - r * .08f,
            slabRight, .75f * opacity)
        p.setAdditive(true)
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, r, 18f, edge, .09f * opacity * glow)
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, r, 10f, edge, .16f * opacity * glow)
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, r, 5f, edge, .30f * opacity * glow)
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, r, 2.6f, edge, .50f * opacity * glow)
        p.setAdditive(false)
        p.fillRoundRect(b.left, b.top, b.right, b.bottom, r, body, opacity)
        p.save(); p.clipRoundRect(b.left, b.top, b.right, b.bottom, r)
        p.fillRect(b.left, b.top, b.right, b.top + depth, sheen, opacity)
        p.fillRect(b.left, b.top, b.right, b.bottom, innerBounce, opacity)
        if (feature) {
            val side = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 4, true)) {
                Fill.Linear(b.left, b.top, b.left + depth, b.top,
                    intArrayOf(Argb.withAlpha(Colors.bloomHalo, .18f), 0))
            }
            val foot = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 5, true)) {
                val color = Argb.mix(Colors.bloomHalo, endAccent, .18f)
                Fill.Linear(b.left, b.bottom - depth, b.left, b.bottom,
                    intArrayOf(0, Argb.withAlpha(color, .24f), Argb.withAlpha(color, .45f)),
                    floatArrayOf(0f, .52f, 1f))
            }
            p.fillRect(b.left, b.top, b.left + depth, b.bottom, side, opacity)
            p.fillRect(b.left, b.bottom - depth, b.right, b.bottom, foot, opacity)
            JarvisSceneFills.ellipseHalo(p, b.left + depth * .65f, b.bottom - depth * .2f,
                depth * 2.2f, depth, Colors.cyan, .16f * opacity * glow)
        }
        JarvisSceneFills.ellipseHalo(p, b.left + r * .35f, b.top + r * .25f,
            depth * 1.5f, depth * 1.1f, accent, .20f * opacity * glow)
        JarvisSceneFills.ellipseHalo(p, b.right - r * .35f, b.bottom - r * .2f,
            depth * 1.7f, depth * 1.1f, endAccent, .20f * opacity * glow)
        p.restore()
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, r,
            if (selected) 1.45f else 1.2f, edge, (if (selected) .98f else .88f) * opacity)
        p.setAdditive(true)
        p.strokeRoundRect(b.left + inset + .6f, b.top + inset + .6f,
            b.right - inset - .6f, b.bottom - inset - .6f,
            (r - inset).coerceAtLeast(1f), 2.4f, edge, .24f * opacity * glow)
        p.setAdditive(false)
        p.strokeRoundRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset,
            (r - inset).coerceAtLeast(1f), .65f, bevel, opacity)
        val lift = JarvisVisualDepth.frontLiftDp().coerceAtMost(inset + 4f)
        // Front light plane (z = +20 dp) — sharp inner reflection, glow stays behind the rim.
        p.strokeRoundRect(b.left + inset + lift * .15f, b.top + inset + lift * .15f,
            b.right - inset - lift * .15f, b.bottom - inset - lift * .15f,
            (r - inset).coerceAtLeast(1f), .85f, Fill.Solid(Colors.glowCore), .22f * opacity)
        cornerLight(p, b, r, accent, endAccent, opacity, glow)
        if (feature) {
            val glint = JarvisSceneFills.cached(MaterialKey(b, accent, endAccent, selected, 6, true)) {
                Fill.Linear(b.left + r, b.top, b.left + minOf(b.width * .48f, r + 40f), b.top,
                    intArrayOf(0, Argb.withAlpha(Colors.glowCore, .78f), 0),
                    floatArrayOf(0f, .42f, 1f))
            }
            p.drawLine(b.left + r, b.top + .45f, b.left + minOf(b.width * .48f, r + 40f),
                b.top + .45f, .65f, glint, .72f * opacity)
        }
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
                    Argb.mix(color, Colors.activeWhite, .22f), color, Argb.withAlpha(color, 0f)),
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
        p.strokePath(material.upper, 11f, material.upperLight, .18f * opacity * glow)
        p.strokePath(material.lower, 11f, material.lowerLight, .18f * opacity * glow)
        p.strokePath(material.upper, 5f, material.upperLight, .32f * opacity * glow)
        p.strokePath(material.lower, 5f, material.lowerLight, .32f * opacity * glow)
        p.strokePath(material.upper, 1.25f, material.upperLight, .95f * opacity)
        p.strokePath(material.lower, 1.25f, material.lowerLight, .95f * opacity)
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
