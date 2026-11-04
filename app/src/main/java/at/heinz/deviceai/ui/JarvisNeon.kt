package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisDesign.ComponentStyle
import at.heinz.deviceai.ui.JarvisDesign.Styles
import at.heinz.deviceai.ui.JarvisDesign.Type
import at.heinz.deviceai.ui.JarvisDesign.Glow

/**
 * Reusable glow / neon primitives on the platform-neutral [ContinuumPainter]
 * (same drawing stack as PR #477 – Android Canvas on device, Java2D in JVM previews).
 * Glow = one soft light layer with falloff (3 additive strokes of growing width, decreasing alpha),
 * never offset copies of the shape or text, so it does not double at large font scales.
 */
object JarvisNeon {
    private fun edge(l: Float, t: Float, r: Float, b: Float) = Fill.Linear(l, t, r, b, Colors.neonEdge)
    private val falloff = floatArrayOf(1f, .55f, .25f)

    /** Soft halo along a rounded rect outline. */
    fun rimGlow(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, radius: Float, glow: Glow, boost: Float = 1f,
                fill: Fill = edge(l, t, r, b)) {
        if (glow == Glow.OFF) return
        p.setAdditive(true)
        falloff.forEachIndexed { i, k ->
            p.strokeRoundRect(l, t, r, b, radius, glow.haloWidth * (i + 1) / falloff.size * 2f, fill, glow.haloAlpha * k * boost / 2f)
        }
        p.setAdditive(false)
    }

    /**
     * Glass card (Review 1): #0A1128 @50 %, 1 px cyan rim @40 %, radius 14 dp.
     * [nativeGlass] = backdrop blur + rim glow already drawn by JarvisGlassRenderer (Android) → skip fallback glow.
     */
    fun glassCard(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float,
                  radius: Float = JarvisDesign.Metrics.radiusCard, glow: Glow = Glow.NORMAL,
                  active: Boolean = false, glowBoost: Float = 1f, nativeGlass: Boolean = false) {
        if (!nativeGlass) rimGlow(p, l, t, r, b, radius, glow, glowBoost * (if (active) 1.4f else .6f), Fill.Solid(Colors.cyan))
        p.fillRoundRect(l, t, r, b, radius, Fill.Linear(l, t, r, b, intArrayOf(Colors.surface, Colors.surfaceLow)))
        p.fillRoundRect(l + 1f, t + 1f, r - 1f, t + (b - t) * .35f, radius - 1f,
            Fill.Linear(0f, t, 0f, t + (b - t) * .35f, intArrayOf(Argb.of(18, 0, 255, 255), 0)))
        p.strokeRoundRect(l, t, r, b, radius, 1f, Fill.Solid(Colors.rim))
        if (active) {
            bloomRoundRect(p, l, t, r, b, radius, Colors.cyan, glowBoost)
            p.strokeRoundRect(l, t, r, b, radius, 1.4f, Fill.Solid(Colors.activeWhite), .5f)
        }
    }

    /** Neon line (light trail, laser connection, divider). */
    fun line(p: ContinuumPainter, x0: Float, y0: Float, x1: Float, y1: Float, color: Int = Colors.cyan,
             width: Float = 1f, glow: Glow = Glow.NORMAL) {
        if (glow != Glow.OFF) {
            p.setAdditive(true)
            falloff.forEachIndexed { i, k -> p.drawLine(x0, y0, x1, y1, width + glow.haloWidth * (i + 1) / 3f, Fill.Solid(color), glow.haloAlpha * k / 2f) }
            p.setAdditive(false)
        }
        p.drawLine(x0, y0, x1, y1, width, Fill.Solid(color))
    }

    /** Laser connection drawn from node to panel; [progress] 0..1 from JarvisMotion.laser(). */
    fun laser(p: ContinuumPainter, x0: Float, y0: Float, x1: Float, y1: Float, progress: Float, glow: Glow = Glow.NORMAL) {
        val k = progress.coerceIn(0f, 1f)
        if (k <= 0f) return
        line(p, x0, y0, x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, Colors.cyan, 1f, glow)
    }

    /** Neon ring / orbit (avatar halo, brain-orb pedestal, gauges). */
    fun ring(p: ContinuumPainter, cx: Float, cy: Float, rx: Float, ry: Float = rx, width: Float = 1.5f,
             colors: IntArray = Colors.neonEdge, glow: Glow = Glow.NORMAL) {
        val fill = Fill.Linear(cx - rx, cy, cx + rx, cy, colors)
        if (glow != Glow.OFF) {
            p.setAdditive(true)
            falloff.forEachIndexed { i, k -> p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width + glow.haloWidth * (i + 1) / 3f, fill, glow.haloAlpha * k / 2f) }
            p.setAdditive(false)
        }
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width, fill)
    }

    /**
     * Blur radius of JarvisBloomPass, from ref5: the halo of the active Home button falls off with an e-fold of
     * ~3.5 px and is gone after 12 px (= 3 sigma ≈ 2.6 dp); RenderEffect radius r gives sigma ≈ 0.57 r + 0.5 px → r ≈ 4 dp.
     */
    const val BLOOM_RADIUS_DP = 4f

    /**
     * Bloom: shared emissive light (VIDEO-ANALYSE Signatur 4 "starkes Bloom"), also for Jarvis Avatar.
     * Three additive radial layers: white-hot core, colour body, wide violet veil. [intensity] 0..1 (>1 allowed for alarms).
     */
    fun bloom(p: ContinuumPainter, cx: Float, cy: Float, radius: Float, color: Int = Colors.cyan, intensity: Float = 1f) {
        if (intensity <= 0f || radius <= 0f) return
        p.setAdditive(true)
        p.fillCircle(cx, cy, radius * 2.4f, Fill.Radial(cx, cy, radius * 2.4f, intArrayOf(Argb.withAlpha(Colors.violet, .10f * intensity), 0)))
        // ref5 core→halo: bright inner stop near glowCore, outer bloomHalo (stop positions estimated, not measured).
        p.fillCircle(cx, cy, radius * 1.3f, Fill.Radial(cx, cy, radius * 1.3f,
            intArrayOf(Argb.withAlpha(Colors.glowCore, .42f * intensity), Argb.withAlpha(Colors.bloomHalo, .30f * intensity), 0),
            floatArrayOf(0f, .45f, 1f)))
        p.fillCircle(cx, cy, radius * .45f, Fill.Radial(cx, cy, radius * .45f, intArrayOf(Argb.withAlpha(Colors.activeWhite, .38f * intensity), 0)))
        p.setAdditive(false)
    }

    /** Bloom around a rounded rect (active panel / button). */
    fun bloomRoundRect(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int = Colors.cyan,
                       intensity: Float = 1f) {
        if (intensity <= 0f) return
        p.setAdditive(true)
        floatArrayOf(14f, 8f, 3f).forEachIndexed { i, s ->
            p.strokeRoundRect(l, t, r, b, radius, s, Fill.Solid(color), (.05f + .07f * i) * intensity)
        }
        p.setAdditive(false)
    }

    /** Radial light pool behind hero content (head, brain orb). */
    fun halo(p: ContinuumPainter, cx: Float, cy: Float, radius: Float, color: Int = Colors.cyan, strength: Float = .35f) =
        p.fillCircle(cx, cy, radius, Fill.Radial(cx, cy, radius, intArrayOf(Argb.withAlpha(color, strength), 0)))

    /**
     * Neon text: one soft light pool behind the text bounds (radius scales with the font size), then crisp text.
     * No offset copies, so large system fonts stay single and sharp.
     */
    fun text(p: ContinuumPainter, s: String, x: Float, y: Float, style: TextStyle, color: Int = Colors.cyan,
             align: TextAlign = TextAlign.LEFT, glow: Glow = Glow.SUBTLE, active: Boolean = false) {
        if (glow != Glow.OFF && s.isNotEmpty()) {
            val w = p.measureText(s, style); val h = p.capHeight(style)
            val l = when (align) { TextAlign.LEFT -> x; TextAlign.CENTER -> x - w / 2f; TextAlign.RIGHT -> x - w }
            val pad = style.size * .45f
            p.setAdditive(true)
            p.fillRoundRect(l - pad, y - h - pad, l + w + pad, y + pad, pad * 2f,
                Fill.Linear(0f, y - h - pad, 0f, y + pad, intArrayOf(0, Argb.withAlpha(color, glow.haloAlpha * .45f), 0)))
            p.setAdditive(false)
        }
        if (active && s.isNotEmpty()) {
            val w = p.measureText(s, style)
            val cx = when (align) { TextAlign.LEFT -> x + w / 2f; TextAlign.CENTER -> x; TextAlign.RIGHT -> x - w / 2f }
            bloom(p, cx, y - p.capHeight(style) / 2f, maxOf(w * .45f, style.size), color, .6f)
        }
        p.drawText(s, x, y, style, Fill.Solid(if (active) Argb.mix(color, Colors.activeWhite, .35f) else color), align)
    }

    /** Draws an icon into the square (left, top, size); supplied by the caller. */
    fun interface IconPainter { fun draw(p: ContinuumPainter, left: Float, top: Float, size: Float, color: Int) }

    /** Shared body of tile, button and pill: fill, optional glow, 1 dp rim (ref5 measurements in [style]). */
    fun component(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, style: ComponentStyle, active: Boolean = false,
                  centerLift: Boolean = false) {
        val radius = minOf(style.radius, (b - t) / 2f)
        val rim = style.rimEnd?.let { Fill.Linear(l, t, r, t, intArrayOf(style.rim, it)) } ?: Fill.Solid(style.rim)
        if (style.glow != Glow.OFF) rimGlow(p, l, t, r, b, radius, style.glow, if (active) 1.4f else 1f,
            if (active) Fill.Solid(Colors.bloomHalo) else rim)
        val fill = when {
            // ref5 active button: brighter toward the middle (navSurface edges → lifted navSurfaceActive centre; lift estimated).
            centerLift -> Fill.Linear(l, t, r, t, intArrayOf(Colors.navSurface, Argb.mix(style.fill, Colors.cyan, .12f), Colors.navSurface),
                floatArrayOf(0f, .5f, 1f))
            style.fillEnd != null -> Fill.Linear(l, t, r, t, intArrayOf(style.fill, style.fillEnd))
            else -> Fill.Solid(style.fill)
        }
        p.fillRoundRect(l, t, r, b, radius, fill)
        p.strokeRoundRect(l, t, r, b, radius, style.rimWidth, rim)
        if (active) p.strokeRoundRect(l, t, r, b, radius, style.rimWidth, Fill.Solid(Colors.glowCore), .6f)
    }

    /**
     * ref5 tile: icon left, title (white) and up to two subtitle lines (secondary). [subtitle] only with real data.
     * Height follows [Styles.tile] unless the caller's bounds are larger.
     */
    fun tile(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, title: String, subtitle: List<String> = emptyList(),
             icon: IconPainter? = null, iconColor: Int = Colors.cyanSoft, active: Boolean = false, chevron: Boolean = true) {
        val st = Styles.tile
        component(p, l, t, r, b, st, active)
        val iconTop = t + (b - t - st.iconSize) / 2f
        icon?.draw(p, l + st.padH, iconTop, st.iconSize, iconColor)
        val x = l + st.padH + st.iconSize + st.iconGap
        val lines = subtitle.take(2)
        val block = p.capHeight(Type.tileLabel) + lines.size * Type.subtitleLineHeight
        var y = t + (b - t - block) / 2f + p.capHeight(Type.tileLabel)
        p.drawText(title, x, y, Type.tileLabel, Fill.Solid(Colors.text))
        // ref5: chevron on title height, not vertically centred.
        if (chevron) ContinuumIcons.draw(p, ContinuumIcons.Icon.CHEVRON_RIGHT, r - Styles.chevronInset, y - p.capHeight(Type.tileLabel) / 2f,
            Styles.chevronSize, Fill.Solid(Colors.chevron))
        lines.forEach { y += Type.subtitleLineHeight; p.drawText(it, x, y, Type.subtitle, Fill.Solid(Colors.textSecondary)) }
    }

    /** ref5 navigation button: icon + label, active = brighter fill, cyan rim and glow. */
    fun button(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, label: String, icon: IconPainter? = null,
               active: Boolean = false) {
        val st = if (active) Styles.buttonActive else Styles.button
        component(p, l, t, r, b, st, active, centerLift = active)
        icon?.draw(p, l + st.padH, t + (b - t - st.iconSize) / 2f, st.iconSize, if (active) Colors.cyan else Colors.cyanSoft)
        val x = l + st.padH + (if (icon != null) st.iconSize + st.iconGap else 0f)
        p.drawText(label, x, t + (b - t + p.capHeight(Type.nav)) / 2f, Type.nav, Fill.Solid(Colors.text))
    }

    /** Measured width of a ref5 pill for [label]. */
    fun pillWidth(p: ContinuumPainter, label: String, hasIcon: Boolean): Float {
        val st = Styles.pill
        return 2 * st.padH + p.measureText(label, Type.pill) + (if (hasIcon) st.iconSize + st.iconGap else 0f)
    }

    /** ref5 pill ("JARVIS Mode", "Local · Private", "Online"); [rim] e.g. [Colors.violetRim] or [Colors.ok]. */
    fun pill(p: ContinuumPainter, l: Float, t: Float, label: String, icon: IconPainter? = null, rim: Int = Styles.pill.rim,
             iconColor: Int = Colors.cyanSoft): Float {
        val st = Styles.pill.copy(rim = rim)
        val r = l + pillWidth(p, label, icon != null)
        component(p, l, t, r, t + st.height, st)
        icon?.draw(p, l + st.padH, t + (st.height - st.iconSize) / 2f, st.iconSize, iconColor)
        val x = l + st.padH + (if (icon != null) st.iconSize + st.iconGap else 0f)
        p.drawText(label, x, t + (st.height + p.capHeight(Type.pill)) / 2f, Type.pill, Fill.Solid(Colors.text))
        return r
    }

    /** ref5 status dot icon (green "Local · Private" dot, measured #03F374 ≈ Colors.ok). */
    val dotIcon = IconPainter { p, l, t, size, color ->
        val c = size / 2f
        halo(p, l + c, t + c, size * .9f, color, .45f)
        p.fillCircle(l + c, t + c, size * .28f, Fill.Solid(color))
    }

    /**
     * ref5 header chips (measured on ref5-fold7-offen.png 1448 px: JARVIS Mode x 763–945, Local chip x 945–1180, y 107–139,
     * JARVIS rim cyan #43B9DF → violet, Local rim teal #0C5F65 + green dot). Returns right edge.
     */
    fun headerChips(p: ContinuumPainter, l: Float, t: Float, gap: Float = 8f, jarvisIcon: IconPainter? = null): Float {
        val r = pill(p, l, t, "JARVIS Mode", jarvisIcon)
        return pill(p, r + gap, t, "Local · Private · On-Device First", dotIcon, rim = Colors.ok, iconColor = Colors.ok)
    }
}
