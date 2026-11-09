package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Cinematic layers of the work-window construct, drawn through the platform-neutral [ContinuumPainter] in dp:
 * the light anchor and its beam, the glass depth layers behind the front frame, the dust motes along the rim and the
 * energy rim while the task really runs. Pure geometry is exposed for tests and for the Java2D preview; nothing here
 * reads a clock. The live draw path reuses its materials, paths and particle storage.
 */
object HollywoodFuiCinematics {
    const val MAX_MOTES = 24
    const val GLASS_LAYERS = 3
    /** Settled offset of one glass layer towards the depth (dp): up and towards the far side. */
    const val LAYER_STEP_DP = 10f
    /** Where a glass layer starts before it slides in (dp beyond its settled place). */
    const val LAYER_TRAVEL_DP = 34f
    private const val LAYER_SCALE_STEP = .018f

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
        fun inset(d: Float) = Box(left + d, top + d, right - d, bottom - d)
    }

    /** Presentation budget of this layer, derived from the single cockpit budget (never a second quality model). */
    data class Budget(val motes: Int, val bloom: Boolean, val frameDelayMs: Long, val animate: Boolean) {
        val glassLayers: Int get() = if (!animate) 1 else if (bloom) GLASS_LAYERS else 2
        fun needsLiveFrames(working: Boolean, reducedMotion: Boolean, visible: Boolean,
                            enabled: Boolean, closing: Boolean): Boolean =
            animate && working && !reducedMotion && visible && enabled && !closing
        companion object {
            val FULL = Budget(MAX_MOTES, bloom = true, frameDelayMs = 16L, animate = true)

            fun from(budget: JarvisRenderQuality.Budget): Budget = Budget(
                motes = when (budget.tier) {
                    JarvisRenderQuality.Tier.HIGH -> MAX_MOTES
                    JarvisRenderQuality.Tier.MEDIUM -> (budget.particleCount / 2).coerceIn(4, MAX_MOTES - 1)
                    JarvisRenderQuality.Tier.LOW, JarvisRenderQuality.Tier.SUSPENDED -> 0
                },
                bloom = !budget.reducedBloom,
                frameDelayMs = budget.frameDelayMillis.coerceAtLeast(16L),
                animate = budget.animate
            )
        }
    }

    data class GlassLayer(val box: Box, val alpha: Float, val scale: Float)

    /**
     * Glass layer [index] (1 = directly behind the front, [layers] = deepest) at construction progress [glass] 0..1.
     * Layers come out of the depth: further away, smaller and transparent, sliding to their settled parallax step.
     */
    fun glassLayer(box: Box, index: Int, glass: Float, layers: Int = GLASS_LAYERS): GlassLayer {
        val depth = index.coerceIn(1, layers)
        val stagger = ((depth - 1).toFloat() / layers.toFloat()) * .35f
        val own = ((glass - stagger) / (1f - stagger)).coerceIn(0f, 1f)
        val eased = HollywoodFuiConstruct.easeOutCubic(own)
        val settledOffset = LAYER_STEP_DP * depth
        val travel = LAYER_TRAVEL_DP * (1f - eased)
        val dx = settledOffset + travel
        val dy = -(settledOffset + travel)
        val scale = 1f - LAYER_SCALE_STEP * depth - .06f * (1f - eased)
        val halfW = box.width * scale / 2f
        val halfH = box.height * scale / 2f
        val cx = box.centerX + dx
        val cy = box.centerY + dy
        val alpha = eased * (.62f - .14f * (depth - 1)).coerceAtLeast(.18f)
        return GlassLayer(Box(cx - halfW, cy - halfH, cx + halfW, cy + halfH), alpha, scale)
    }

    data class Mote(val x: Float, val y: Float, val radius: Float, val alpha: Float)

    /** Deterministic dust motes drifting along the rim; same [seed] and [timeMs] give the same frame. */
    fun motes(box: Box, timeMs: Long, count: Int, seed: Int): List<Mote> {
        val result = ArrayList<Mote>(count.coerceIn(0, MAX_MOTES))
        visitMotes(box, timeMs, count, seed) { _, x, y, radius, alpha -> result += Mote(x, y, radius, alpha) }
        return result
    }

    private inline fun visitMotes(box: Box, timeMs: Long, count: Int, seed: Int,
                                 draw: (Int, Float, Float, Float, Float) -> Unit) {
        if (count <= 0 || box.width <= 0f || box.height <= 0f) return
        val perimeter = 2f * (box.width + box.height)
        val seconds = timeMs.coerceAtLeast(0L) / 1000.0
        for (index in 0 until count.coerceAtMost(MAX_MOTES)) {
            val h = hash(seed * 7919 + index * 104_729)
            val speed = .35f + (h and 0xFF) / 255f * .65f
            val offset = ((h ushr 8) and 0xFFFF) / 65535f
            val phase = ((h ushr 24) and 0xFF) / 255f
            val distance = (((seconds * speed * .08 + offset) % 1.0) * perimeter).toFloat()
            val wobble = sin(2.0 * PI * (seconds * .5 + phase)).toFloat() * 4f
            val x: Float
            val y: Float
            when {
                distance < box.width -> { x = box.left + distance; y = box.top - wobble }
                distance < box.width + box.height -> { x = box.right + wobble; y = box.top + distance - box.width }
                distance < 2f * box.width + box.height -> {
                    x = box.right - (distance - box.width - box.height); y = box.bottom + wobble
                }
                else -> { x = box.left - wobble; y = box.bottom - (distance - 2f * box.width - box.height) }
            }
            val twinkle = .45f + .55f * (.5f + .5f * sin(2.0 * PI * (seconds * (.6f + speed) + phase)).toFloat())
            draw(index, x, y, 1.1f + ((h ushr 16) and 0xFF) / 255f * 1.7f, twinkle.coerceIn(0f, 1f))
        }
    }

    data class Beam(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
        val length: Float get() = hypot(x1 - x0, y1 - y0)
    }

    /** The light beam from the anchor (origin) to the nearest edge point of the construct, grown to [progress]. */
    fun anchorBeam(originX: Float, originY: Float, box: Box, progress: Float): Beam {
        val tx = originX.coerceIn(box.left, box.right)
        val ty = originY.coerceIn(box.top, box.bottom)
        val inside = tx == originX && ty == originY
        val endX = if (inside) originX else tx
        val endY = if (inside) originY else ty
        val p = progress.coerceIn(0f, 1f)
        return Beam(originX, originY, originX + (endX - originX) * p, originY + (endY - originY) * p)
    }

    // ---- drawing -------------------------------------------------------------------------------------------------

    class Painterly(private val accent: Int) {
        private class GlassMaterial(val width: Float, val height: Float, accent: Int) {
            val fill = Fill.Linear(0f, 0f, width * .25f, height,
                intArrayOf(Argb.of(180, 0x10, 0x2C, 0x46), Argb.of(220, 0x03, 0x08, 0x18)))
            val sheen = Fill.Linear(0f, 0f, width, height * .35f,
                intArrayOf(Argb.withAlpha(accent, .20f), Argb.withAlpha(JarvisDesign.Colors.violet, .06f), 0),
                floatArrayOf(0f, .42f, 1f))
            val rim = Fill.Linear(0f, 0f, width, height,
                intArrayOf(accent, JarvisDesign.Colors.cyanSoft, JarvisDesign.Colors.violet, JarvisDesign.Colors.magenta),
                floatArrayOf(0f, .22f, .72f, 1f))
            val reflection = VectorPath().moveTo(0f, 0f).lineTo(width * .68f, 0f)
                .lineTo(width * .28f, height * .42f).lineTo(0f, height * .55f).close()
        }
        private var material: GlassMaterial? = null
        private val gridPath = VectorPath()
        private var gridBox: Box? = null
        private val anchorHalo = Fill.Radial(0f, 0f, 1f,
            intArrayOf(Argb.withAlpha(accent, .55f), Argb.withAlpha(accent, 0f)))
        private val glint = Fill.Solid(JarvisDesign.Colors.glowCore)
        private val accentFill = Fill.Solid(accent)
        private val violet = Fill.Solid(JarvisDesign.Colors.violet)

        /** Anchor halo, expanding ring and the beam towards the construct; fades as the brackets take over. */
        fun drawAnchor(p: ContinuumPainter, originX: Float, originY: Float, box: Box,
                       frame: HollywoodFuiConstruct.Frame, bloom: Boolean) {
            val strength = frame.anchor * (1f - frame.brackets) * frame.closeFrame
            val pulse = frame.pulse
            val glow = max(strength, pulse * frame.closeFrame)
            if (glow <= .01f) return
            val haloRadius = 10f + 26f * frame.anchor + 14f * pulse
            if (bloom) p.setAdditive(true)
            if (bloom) drawHalo(p, originX, originY, haloRadius, (.35f + .45f * pulse) * glow)
            p.strokeCircle(originX, originY, 6f + 34f * frame.anchor, 1.2f, accentFill, .6f * glow)
            p.fillCircle(originX, originY, 2.4f + 1.6f * pulse, glint, (.6f + .4f * pulse) * glow)
            val beam = anchorBeam(originX, originY, box, frame.anchor)
            if (beam.length > 1f) {
                if (bloom) p.drawLine(beam.x0, beam.y0, beam.x1, beam.y1, 7f, accentFill, .16f * glow)
                p.drawLine(beam.x0, beam.y0, beam.x1, beam.y1, 1.6f, glint, .9f * glow)
                p.fillCircle(beam.x1, beam.y1, 3.2f, glint, .95f * glow)
                if (bloom) drawHalo(p, beam.x1, beam.y1, 9f, glow)
            }
            if (bloom) p.setAdditive(false)
        }

        private fun drawHalo(p: ContinuumPainter, x: Float, y: Float, radius: Float, alpha: Float) {
            p.save()
            p.translate(x, y)
            p.scale(radius, radius)
            p.fillCircle(0f, 0f, 1f, anchorHalo, alpha)
            p.restore()
        }

        /** The glass sheets behind the front frame: dark glass, cyan→violet Fresnel rim, glint on the top edge. */
        fun drawGlassLayers(p: ContinuumPainter, box: Box, frame: HollywoodFuiConstruct.Frame, radius: Float,
                            layers: Int = GLASS_LAYERS) {
            val alphaScale = frame.closeGlass
            if (frame.glass <= 0f || alphaScale <= .01f || box.width <= 0f || box.height <= 0f) return
            val m = material?.takeIf { it.width == box.width && it.height == box.height }
                ?: GlassMaterial(box.width, box.height, accent).also { material = it }
            for (index in layers.coerceIn(0, GLASS_LAYERS) downTo 1) {
                val layer = glassLayer(box, index, frame.glass)
                val a = layer.alpha * alphaScale
                if (a <= .01f) continue
                val b = layer.box
                p.save()
                p.translate(b.left, b.top)
                p.scale(layer.scale, layer.scale)
                p.fillRoundRect(0f, 0f, m.width, m.height, radius, m.fill, a)
                // A clipped, oblique reflection, not a second translucent rectangle across the text.
                p.save()
                p.clipRoundRect(0f, 0f, m.width, m.height, radius)
                p.fillPath(m.reflection, m.sheen, a)
                p.restore()
                p.strokeRoundRect(0f, 0f, m.width, m.height, radius, 3.5f, m.rim, .14f * a)
                p.strokeRoundRect(0f, 0f, m.width, m.height, radius, 1f, m.rim, a)
                p.drawLine(radius, 0f, m.width * .42f, 0f, 1.2f, glint, .65f * a)
                p.drawLine(m.width, m.height * .62f, m.width, m.height - radius, 1f, violet, .5f * a)
                p.restore()
            }
        }

        /** Dust along the rim and, while the task really works, a breathing energy rim. */
        fun drawMotesAndEnergy(p: ContinuumPainter, box: Box, frame: HollywoodFuiConstruct.Frame, timeMs: Long,
                               motes: Int, seed: Int, working: Boolean, bloom: Boolean, radius: Float) {
            val visible = frame.grid * frame.closeFrame
            if (visible <= .01f) return
            if (bloom) p.setAdditive(true)
            if (working) {
                val breath = .5f + .5f * sin(2f * PI.toFloat() * ((timeMs % 2_400L) / 2_400f))
                p.strokeRoundRect(box.left, box.top, box.right, box.bottom, radius, 6f, accentFill, (.12f + .14f * breath) * visible)
                p.strokeRoundRect(box.left, box.top, box.right, box.bottom, radius, 1.6f, glint, (.45f + .45f * breath) * visible)
                // Narrow reflections remain outside the content plane; opposite travel separates the two bevels.
                val length = min(48f, box.width * .18f)
                val travel = (box.width - 2f * radius - length).coerceAtLeast(0f)
                val phase = .5f - .5f * cos(2f * PI.toFloat() * (timeMs % 4_800L) / 4_800f)
                for (edge in 0..1) {
                    val x = box.left + radius + travel * if (edge == 0) phase else 1f - phase
                    val y = if (edge == 0) box.top - 2f else box.bottom + 2f
                    if (bloom) p.drawLine(x, y, x + length, y, 4f, accentFill, .10f * visible)
                    p.drawLine(x, y, x + length, y, 1f, glint, .65f * visible)
                }
            }
            visitMotes(box, timeMs, motes, seed) { index, x, y, radius, alpha ->
                val fill = when (index % 3) { 0 -> glint; 1 -> accentFill; else -> violet }
                if (bloom) p.fillCircle(x, y, radius * 2.6f, fill, alpha * .18f * visible)
                p.fillCircle(x, y, radius, fill, alpha * visible)
            }
            if (bloom) p.setAdditive(false)
        }

        /** Four corner brackets of [box] with a soft bloom; [strength] 0..1. */
        fun drawBrackets(p: ContinuumPainter, box: Box, strength: Float, bloom: Boolean) {
            if (strength <= .01f) return
            val length = min(20f, min(box.width, box.height) * .18f)
            fun corner(x: Float, y: Float, sx: Float, sy: Float) {
                if (bloom) {
                    p.drawLine(x, y, x + length * sx, y, 5f, accentFill, .14f * strength, roundCap = false)
                    p.drawLine(x, y, x, y + length * sy, 5f, accentFill, .14f * strength, roundCap = false)
                }
                p.drawLine(x, y, x + length * sx, y, 2.2f, glint, .9f * strength, roundCap = false)
                p.drawLine(x, y, x, y + length * sy, 2.2f, glint, .9f * strength, roundCap = false)
            }
            corner(box.left, box.top, 1f, 1f)
            corner(box.right, box.top, -1f, 1f)
            corner(box.left, box.bottom, 1f, -1f)
            corner(box.right, box.bottom, -1f, -1f)
        }

        /** Sparse scan raster, chamfered rim and the moving scan line, all inside [box]. */
        fun drawGridAndScan(p: ContinuumPainter, box: Box, frame: HollywoodFuiConstruct.Frame, bloom: Boolean) {
            val a = frame.grid * frame.closeFrame
            if (a <= .01f) return
            val step = 24f
            var x = box.left + step
            while (x < box.right) { p.drawLine(x, box.top, x, box.bottom, .7f, accentFill, .16f * a); x += step }
            var y = box.top + step
            while (y < box.bottom) { p.drawLine(box.left, y, box.right, y, .7f, accentFill, .16f * a); y += step }
            p.drawLine(box.centerX, box.top, box.centerX, box.bottom, .7f, violet, .10f * a)
            p.drawLine(box.left, box.centerY, box.right, box.centerY, .7f, violet, .10f * a)
            if (gridBox != box) {
                val cut = min(14f, min(box.width, box.height) * .12f)
                val r = box.inset(1.2f)
                gridBox = box
                gridPath.reset().moveTo(r.left + cut, r.top).lineTo(r.right - cut, r.top).lineTo(r.right, r.top + cut)
                    .lineTo(r.right, r.bottom - cut).lineTo(r.right - cut, r.bottom).lineTo(r.left + cut, r.bottom)
                    .lineTo(r.left, r.bottom - cut).lineTo(r.left, r.top + cut).close()
            }
            p.strokePath(gridPath, 1.3f, accentFill, .80f * a)
            if (bloom) p.strokePath(gridPath, 5f, accentFill, .12f * a)
            if (frame.scanEnabled) {
                val sy = box.top + box.height * frame.scanPhase
                val edge = HollywoodFuiWorkActivity.scanEnvelope(frame.scanPhase)
                if (bloom) p.drawLine(box.left + 6f, sy, box.right - 6f, sy, 5f, accentFill, .07f * a * edge)
                p.drawLine(box.left + 6f, sy, box.right - 6f, sy, 1.2f, glint, .75f * a * edge)
            }
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private fun hash(value: Int): Int {
        var h = value * -0x61c88647
        h = h xor (h ushr 15); h *= -0x7a143595; h = h xor (h ushr 13); h *= -0x3d4d51cb; h = h xor (h ushr 16)
        return h
    }

}
