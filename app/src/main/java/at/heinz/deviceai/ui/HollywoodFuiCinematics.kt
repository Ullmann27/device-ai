package at.heinz.deviceai.ui

import kotlin.math.PI
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

    // ---- Cinema 2.0: connections (pure) ----------------------------------------------------------------------------

    /** Duration of one verified receipt's way back from the window rim to the orb (same as the rim pulse). */
    const val RECEIPT_TRAVEL_MS = 380L

    /** Travel 0..1 of a verified receipt [ageMs] after it was presented; −1 before, after or for a bad age. */
    fun receiptTravel(ageMs: Long): Float =
        if (ageMs < 0L || ageMs > RECEIPT_TRAVEL_MS) -1f else ageMs / RECEIPT_TRAVEL_MS.toFloat()

    /**
     * The avatar's reading thread: from the side of the face that looks at the window to the window rim, clear of the
     * rounded corners. Null when face and window overlap or touch (no honest gap) or for bad geometry.
     */
    fun attentionLink(face: Box, window: Box, minGapDp: Float = 6f, cornerDp: Float = 18f): Beam? {
        val values = floatArrayOf(face.left, face.top, face.right, face.bottom,
            window.left, window.top, window.right, window.bottom)
        if (values.any { !it.isFinite() } || face.width <= 0f || face.height <= 0f ||
            window.width <= 2f * cornerDp || window.height <= 2f * cornerDp) return null
        // The eyes sit in the upper half of the face box; the thread leaves at eye height.
        val eyeY = face.top + face.height * .42f
        fun alongX(x: Float) = x.coerceIn(window.left + cornerDp, window.right - cornerDp)
        fun alongY(y: Float) = y.coerceIn(window.top + cornerDp, window.bottom - cornerDp)
        return when {
            window.left - face.right >= minGapDp -> Beam(face.right, eyeY, window.left, alongY(eyeY))
            face.left - window.right >= minGapDp -> Beam(face.left, eyeY, window.right, alongY(eyeY))
            window.top - face.bottom >= minGapDp -> Beam(face.centerX, face.bottom, alongX(face.centerX), window.top)
            face.top - window.bottom >= minGapDp -> Beam(face.centerX, face.top, alongX(face.centerX), window.bottom)
            else -> null
        }
    }

    // ---- Cinema 2.0: focus depth (pure) ------------------------------------------------

    /** Dim of the deck plane just below a constructed window's top edge and at the bottom of the deck. */
    const val FOCUS_DIM_NEAR = .30f
    const val FOCUS_DIM_FAR = .52f
    private const val FOCUS_FADE_IN_DP = 56f

    /**
     * Focus pull 0..1: once the glass of a work window exists and the window has arrived ([openness]), the deck plane
     * behind it steps back; it returns with the glass on close. Derived from the construct's own frame – no clock.
     */
    fun focusDepth(frame: HollywoodFuiConstruct.Frame, openness: Float): Float {
        if (!openness.isFinite() || openness <= 0f) return 0f
        val arrived = openness.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        return (frame.glass * frame.closeGlass * arrived).coerceIn(0f, 1f)
    }

    /**
     * Flight contact 0..1: the construct exists (brackets) but has not landed yet, so it is carried above the deck.
     * Rises with the brackets after lift-off, is gone at touch-down and returns while the window folds away. Derived
     * from the construct's own frame and the window's openness – no clock; reduced motion never flies.
     */
    fun flightContact(frame: HollywoodFuiConstruct.Frame, openness: Float): Float {
        if (!openness.isFinite()) return 0f
        val o = openness.coerceIn(0f, 1f)
        val airborne = 1f - o * o * (3f - 2f * o)
        return (frame.brackets * frame.closeFrame * airborne).coerceIn(0f, 1f)
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

        private var focusShade: Fill.Linear? = null
        private var focusKey: Box? = null
        private val deckShade = Fill.Solid(JarvisDesign.Colors.background)

        /**
         * Focus depth: the deck plane at and below the window steps back – softly from the window's top edge, deeper
         * towards the bottom – and the window casts a contact shadow, so the glass stands in front instead of lying
         * flat on half-covered labels. The row above the window (avatar, orb) is never shaded, so their protected
         * cut-outs leave no visible box. [strength] 0..1.
         */
        fun drawFocusDepth(p: ContinuumPainter, viewport: Box, window: Box, strength: Float, radius: Float,
                           contact: Box = window) {
            if (!strength.isFinite() || strength <= .01f || viewport.width <= 0f || viewport.height <= 0f ||
                window.width <= 0f || window.height <= 0f || window.top >= viewport.bottom - 1f) return
            val s = strength.coerceIn(0f, 1f)
            val shade = focusShade?.takeIf { focusKey == window } ?: run {
                val span = (viewport.bottom - window.top).coerceAtLeast(1f)
                Fill.Linear(0f, window.top, 0f, viewport.bottom,
                    intArrayOf(Argb.withAlpha(JarvisDesign.Colors.background, 0f),
                        Argb.withAlpha(JarvisDesign.Colors.background, FOCUS_DIM_NEAR),
                        Argb.withAlpha(JarvisDesign.Colors.background, FOCUS_DIM_FAR)),
                    floatArrayOf(0f, (FOCUS_FADE_IN_DP / span).coerceIn(.02f, .5f), 1f)).also { focusShade = it; focusKey = window }
            }
            p.fillRect(viewport.left, window.top, viewport.right, viewport.bottom, shade, s)
            // Contact shadow: three widening, fading rings slightly below the glass.
            val drop = 5f
            // They stand under the glass where it is in this frame ([contact]), not at a destination it has not reached.
            p.strokeRoundRect(contact.left, contact.top + drop, contact.right, contact.bottom + drop, radius + 6f, 26f, deckShade, .10f * s)
            p.strokeRoundRect(contact.left, contact.top + drop, contact.right, contact.bottom + drop, radius + 3f, 14f, deckShade, .16f * s)
            p.strokeRoundRect(contact.left, contact.top + drop * .5f, contact.right, contact.bottom + drop * .5f, radius, 6f, deckShade, .24f * s)
        }

        /**
         * Flight contact: while the construct is carried from its origin to its place it is not a bare wire frame
         * floating over the deck – its footprint ([ground], the pane's straight path without the arc lift) darkens the
         * deck as an umbra with a soft penumbra, and the hologram's own light lies as a fine accent line on the deck
         * under its lower edge. The arc lifts the glass off this footprint and sets it down on it. [strength] 0..1
         * from [flightContact]; nothing is drawn once the window has landed.
         */
        fun drawFlightContact(p: ContinuumPainter, ground: Box, strength: Float, radius: Float) {
            if (!strength.isFinite() || strength <= .01f || ground.width <= 2f || ground.height <= 2f) return
            val s = strength.coerceIn(0f, 1f)
            val r = min(radius, min(ground.width, ground.height) * .5f)
            val drop = 5f
            p.fillRoundRect(ground.left, ground.top + drop, ground.right, ground.bottom + drop, r, deckShade, .34f * s)
            p.strokeRoundRect(ground.left, ground.top + drop, ground.right, ground.bottom + drop, r + 6f, 26f, deckShade, .10f * s)
            p.strokeRoundRect(ground.left, ground.top + drop, ground.right, ground.bottom + drop, r + 3f, 14f, deckShade, .16f * s)
            val inset = min(r + 4f, ground.width * .2f)
            val y = ground.bottom + drop
            p.drawLine(ground.left + inset, y, ground.right - inset, y, 9f, accentFill, .12f * s)
            p.drawLine(ground.left + inset, y, ground.right - inset, y, 1.6f, accentFill, .6f * s)
        }

        /**
         * The holographic raster under the vacant part of the glass (below [vacantTop]): it stays at structural
         * strength there because no text stands on it, with a horizon line where the dense sheet ends.
         */
        fun drawVacantRaster(p: ContinuumPainter, box: Box, vacantTop: Float, strength: Float, bloom: Boolean) {
            if (!strength.isFinite() || strength <= .01f || !vacantTop.isFinite() ||
                vacantTop >= box.bottom - 8f || vacantTop <= box.top) return
            val s = strength.coerceIn(0f, 1f)
            val step = 24f
            var x = box.left + step
            while (x < box.right) {
                p.drawLine(x, vacantTop, x, box.bottom, .7f, accentFill, .20f * s)
                x += step
            }
            var y = box.top + step
            while (y < box.bottom) {
                if (y > vacantTop) p.drawLine(box.left, y, box.right, y, .7f, accentFill, .20f * s)
                y += step
            }
            if (bloom) p.drawLine(box.left + 10f, vacantTop, box.right - 10f, vacantTop, 4f, violet, .10f * s)
            p.drawLine(box.left + 10f, vacantTop, box.right - 10f, vacantTop, .9f, accentFill, .46f * s)
        }

        /**
         * The avatar's reading thread while real work runs: a hair-thin line from the face to the window with two
         * sparks travelling towards the window on the window's own clock ([seconds]); continuous, no phase jump.
         */
        fun drawAttentionLink(p: ContinuumPainter, beam: Beam, seconds: Float, strength: Float, bloom: Boolean) {
            if (!strength.isFinite() || strength <= .01f || !seconds.isFinite()) return
            val s = strength.coerceIn(0f, 1f)
            val dx = beam.x1 - beam.x0
            val dy = beam.y1 - beam.y0
            if (dx * dx + dy * dy < 16f) return
            if (bloom) p.setAdditive(true)
            try {
                if (bloom) p.drawLine(beam.x0, beam.y0, beam.x1, beam.y1, 3.2f, violet, .07f * s)
                p.drawLine(beam.x0, beam.y0, beam.x1, beam.y1, .7f, accentFill, .30f * s)
                repeat(2) { k ->
                    val t = ((seconds * .55f + k * .5f) % 1f + 1f) % 1f
                    val fade = sin(PI.toFloat() * t)
                    val x = beam.x0 + dx * t
                    val y = beam.y0 + dy * t
                    p.fillCircle(x, y, 2.4f, accentFill, .35f * s * fade)
                    p.fillCircle(x, y, 1.1f, glint, .9f * s * fade)
                }
                p.fillCircle(beam.x1, beam.y1, 1.8f, glint, .6f * s)
            } finally { if (bloom) p.setAdditive(false) }
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
            }
            visitMotes(box, timeMs, motes, seed) { index, x, y, radius, alpha ->
                val fill = when (index % 3) { 0 -> glint; 1 -> accentFill; else -> violet }
                if (bloom) p.fillCircle(x, y, radius * 2.6f, fill, alpha * .18f * visible)
                p.fillCircle(x, y, radius, fill, alpha * visible)
            }
            if (bloom) p.setAdditive(false)
        }

        /** Verified tool receipt: one bounded travelling spark, not a fake progress bar. */
        fun drawVerifiedStepPulse(p: ContinuumPainter, box: Box, frame: HollywoodFuiConstruct.Frame,
                                  progress: Float) {
            if (!progress.isFinite() || progress !in 0f..1f || frame.closeFrame <= 0f ||
                box.width <= 0f || box.height <= 0f) return
            val travel = progress * (2f * box.width + 2f * box.height)
            val x: Float
            val y: Float
            when {
                travel <= box.width -> { x = box.left + travel; y = box.top }
                travel <= box.width + box.height -> { x = box.right; y = box.top + travel - box.width }
                travel <= 2f * box.width + box.height -> {
                    x = box.right - (travel - box.width - box.height); y = box.bottom
                }
                else -> { x = box.left; y = box.bottom - (travel - 2f * box.width - box.height) }
            }
            val alpha = (1f - progress) * frame.closeFrame
            p.fillCircle(x, y, 7f, violet, .18f * alpha)
            p.fillCircle(x, y, 2.3f, glint, .82f * alpha)
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

        /** Keep the perimeter visible when the interior raster dims beneath real text. */
        fun drawGridAndScan(p: ContinuumPainter, box: Box, frame: HollywoodFuiConstruct.Frame,
                            bloom: Boolean, innerRasterAlpha: Float = frame.grid) {
            val rimAlpha = HollywoodFuiWorkActivity.structuralOpacity(frame.grid, frame.closeFrame)
            val rasterAlpha = HollywoodFuiWorkActivity.structuralOpacity(innerRasterAlpha, frame.closeFrame)
            if (rimAlpha <= .01f && rasterAlpha <= .01f) return
            val step = 24f
            var x = box.left + step
            while (x < box.right && rasterAlpha > .005f) {
                p.drawLine(x, box.top, x, box.bottom, .7f, accentFill, .16f * rasterAlpha)
                x += step
            }
            var y = box.top + step
            while (y < box.bottom && rasterAlpha > .005f) {
                p.drawLine(box.left, y, box.right, y, .7f, accentFill, .16f * rasterAlpha)
                y += step
            }
            p.drawLine(box.centerX, box.top, box.centerX, box.bottom, .7f, violet, .10f * rasterAlpha)
            p.drawLine(box.left, box.centerY, box.right, box.centerY, .7f, violet, .10f * rasterAlpha)
            if (gridBox != box) {
                val cut = min(14f, min(box.width, box.height) * .12f)
                val r = box.inset(1.2f)
                gridBox = box
                gridPath.reset().moveTo(r.left + cut, r.top).lineTo(r.right - cut, r.top).lineTo(r.right, r.top + cut)
                    .lineTo(r.right, r.bottom - cut).lineTo(r.right - cut, r.bottom).lineTo(r.left + cut, r.bottom)
                    .lineTo(r.left, r.bottom - cut).lineTo(r.left, r.top + cut).close()
            }
            p.strokePath(gridPath, 2.5f, violet, .26f * rimAlpha)
            p.strokePath(gridPath, 1.4f, accentFill, .84f * rimAlpha)
            if (bloom) p.strokePath(gridPath, 5.5f, accentFill, .14f * rimAlpha)
            if (frame.scanEnabled && rimAlpha > .01f) {
                val sy = box.top + box.height * frame.scanPhase
                val edge = HollywoodFuiWorkActivity.scanEnvelope(frame.scanPhase)
                if (bloom) {
                    p.drawLine(box.left + 6f, sy - 2f, box.right - 6f, sy - 2f,
                        4f, violet, .16f * rimAlpha * edge)
                    p.drawLine(box.left + 6f, sy, box.right - 6f, sy,
                        5f, accentFill, .10f * rimAlpha * edge)
                }
                p.drawLine(box.left + 6f, sy, box.right - 6f, sy,
                    1.2f, glint, .78f * rimAlpha * edge)
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
