package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "Continuum Brain"-Kugel wie ref5 (Fold7 offen): Glaskugel mit Längen-/Breiten-Knotennetz, leuchtendes Gehirn in der
 * oberen Hälfte, Cyan-∞ und zweizeilig "Continuum / Brain", Sockel aus konzentrischen Ellipsen mit Lichtbalken.
 * Zeichnet nur über [ContinuumPainter] (Gerät = JVM-Vorschau). Farben NUR aus [JarvisDesign.Colors] (ref5-Tokens #479, docs/JARVIS-REF5-FARBEN.md).
 *
 * Dynamik kommt ausschließlich aus echten Signalen ([Signals], befüllt aus JarvisSequenceEffects:
 * orbPulseLevel / recallBrightness / hypothesisVisible); ohne Signale ist die Kugel ruhig.
 */
class ContinuumBrainOrbRenderer {
    /** [pulse] 0..1 (MemoryStored), [recall] 0..1 (RecallHits), [hypothesis] Marker sichtbar, [phase] 0..1 Netzdrift. */
    data class Signals(val pulse: Float = 0f, val recall: Float = 0f, val hypothesis: Boolean = false, val phase: Float = 0f,
                       val processing: Boolean = false, val particles: Int = 70, val orbitSegments: Int = 96)

    var label1 = "Continuum"
    var label2 = "Gehirn"

    /** Kugelmitte ([cx],[cy]) und Radius [r] in px; Sockel und Lichtbalken liegen unter der Kugel bis cy + 1.30 r. */
    fun draw(p: ContinuumPainter, cx: Float, cy: Float, r: Float, s: Signals = Signals()) {
        // Keep invalid signals out of draw primitives.
        val pulse = if (s.pulse.isFinite()) s.pulse.coerceIn(0f, 1f) else 0f
        val recall = if (s.recall.isFinite()) s.recall.coerceIn(0f, 1f) else 0f
        val phase = if (s.phase.isFinite() && s.phase in -1_000_000f..1_000_000f) s.phase else 0f
        val stable = if (pulse == s.pulse && recall == s.recall && phase == s.phase) s
            else s.copy(pulse = pulse, recall = recall, phase = phase)
        val C = JarvisDesign.Colors
        drawPedestal(p, cx, cy + r * 1.0f, r)
        drawLightCone(p, cx, cy, r)
        drawOrbits(p, cx, cy, r, stable, back = true)
        // Hologram bloom toward ref-d: cyan volume plus a warm amber corona.
        // Extra fillCircles only — no fifth orbit plane (Fold7 thermal budget).
        p.setAdditive(true)
        p.fillCircle(cx, cy, r * 2.08f, Fill.Radial(cx, cy, r * 2.08f,
            intArrayOf(Argb.withAlpha(C.cyan, .10f), Argb.withAlpha(C.warn, .06f), 0),
            floatArrayOf(0f, .36f, 1f)))
        p.fillCircle(cx, cy, r * 1.74f, Fill.Radial(cx, cy, r * 1.74f,
            intArrayOf(Argb.withAlpha(C.cyan, .16f), Argb.withAlpha(C.violet, .08f), 0),
            floatArrayOf(0f, .40f, 1f)))
        p.fillCircle(cx, cy, r * 1.55f, Fill.Radial(cx, cy, r * 1.55f,
            intArrayOf(Argb.withAlpha(C.violet, .16f), Argb.withAlpha(C.cyan, .08f), 0),
            floatArrayOf(0f, .42f, 1f)))
        p.setAdditive(false)
        // Obsidian glass with volume: darker core, warm mid stop, brighter Fresnel rim.
        // Still a single fill at radius r (unique 0.78 r core stays in drawCore).
        p.fillCircle(cx, cy, r, Fill.Radial(cx, cy - r * .22f, r * 1.05f,
            intArrayOf(Argb.withAlpha(C.background, .64f), Argb.withAlpha(C.backgroundHaze, .44f),
                Argb.withAlpha(C.cyanDeep, .30f), Argb.withAlpha(C.warn, .18f), Argb.withAlpha(C.cyan, .62f)),
            floatArrayOf(0f, .36f, .66f, .86f, 1f)), .74f)
        p.setAdditive(true)
        p.fillCircle(cx, cy + r * .06f, r * .90f, Fill.Radial(cx, cy + r * .06f, r * .90f,
            intArrayOf(Argb.withAlpha(C.glowCore, .16f), Argb.withAlpha(C.cyan, .10f), 0),
            floatArrayOf(0f, .45f, 1f)))
        p.setAdditive(false)
        // Inner glass cavity — visible shell thickness. Not the unique 0.78 r core.
        p.fillCircle(cx, cy + r * .02f, r * .88f, Fill.Radial(cx, cy + r * .08f, r * .88f,
            intArrayOf(Argb.withAlpha(C.background, .55f), Argb.withAlpha(C.cyanDeep, .16f),
                Argb.withAlpha(C.cyan, .08f)),
            floatArrayOf(0f, .72f, 1f)), .55f)
        drawNet(p, cx, cy, r, stable)
        drawCore(p, cx, cy, r, stable)
        drawNeurons(p, cx, cy, r, stable)
        drawBrain(p, cx, cy - r * .33f, r, stable)
        // Unconfirmed memory stays visibly distinct.
        if (stable.hypothesis) {
            p.setAdditive(true)
            for (i in 0 until 12) {
                p.strokeArc(cx - r * .90f, cy - r * .90f, cx + r * .90f, cy + r * .90f,
                    i * 30f, 16f, r * .014f, Fill.Solid(C.violetRim), .65f)
            }
            p.setAdditive(false)
        }
        // Containment rings stay quiet; catchlights are angle-dependent (key light upper-left).
        p.setAdditive(true)
        val rimSweep = intArrayOf(C.cyan, C.violet, C.magenta, C.violetRim, C.magenta, C.cyan, C.violet, C.cyan)
        for ((w, a) in listOf(.36f to .08f, .22f to .14f, .12f to .26f, .05f to .48f))
            p.strokeCircle(cx, cy, r * (1f + w * .14f), r * w, Fill.Sweep(cx, cy, rimSweep), a)
        p.strokeCircle(cx, cy, r * .995f, r * .018f, Fill.Sweep(cx, cy, intArrayOf(C.glowCore, C.violetRim, C.violet, C.cyan, C.glowCore)), 1f)
        // Facing the key light: sharp cyan/white Fresnel. Opposite/rear: violet depth edge.
        p.strokeArc(cx - r * .97f, cy - r * .97f, cx + r * .97f, cy + r * .97f, 198f, 78f, r * .030f, Fill.Solid(C.cyan), .50f)
        p.strokeArc(cx - r * .97f, cy - r * .97f, cx + r * .97f, cy + r * .97f, 208f, 50f, r * .013f, Fill.Solid(C.glowCore), .92f)
        p.strokeArc(cx - r * .97f, cy - r * .97f, cx + r * .97f, cy + r * .97f, 18f, 72f, r * .024f, Fill.Solid(C.violet), .70f)
        p.strokeArc(cx - r * .97f, cy - r * .97f, cx + r * .97f, cy + r * .97f, 328f, 58f, r * .014f, Fill.Solid(C.violetRim), .78f)
        // Warm equatorial band (ref-d amber rings). strokeArc only — not a fifth projected orbit.
        p.strokeArc(cx - r * 1.02f, cy - r * .20f, cx + r * 1.02f, cy + r * .20f, 192f, 156f, r * .030f, Fill.Solid(C.warn), .40f)
        p.strokeArc(cx - r * 1.02f, cy - r * .20f, cx + r * 1.02f, cy + r * .20f, 200f, 140f, r * .011f, Fill.Solid(C.warn), .78f)
        p.strokeArc(cx - r * 1.05f, cy - r * .36f, cx + r * 1.05f, cy + r * .36f, 14f, 148f, r * .016f, Fill.Solid(C.warn), .30f)
        // Specular sickle + point (upper left, same key-light direction).
        p.strokeArc(cx - r * .86f, cy - r * .86f, cx + r * .86f, cy + r * .86f, 200f, 60f, r * .05f, Fill.Solid(C.activeWhite), .22f)
        p.strokeArc(cx - r * .86f, cy - r * .86f, cx + r * .86f, cy + r * .86f, 210f, 40f, r * .015f, Fill.Solid(C.activeWhite), .55f)
        p.fillCircle(cx - r * .55f, cy - r * .58f, r * .05f, Fill.Radial(cx - r * .55f, cy - r * .58f, r * .05f, intArrayOf(Argb.withAlpha(C.activeWhite, .8f), 0)))
        p.setAdditive(false)
        drawDust(p, cx, cy, r, s.particles)
        drawOrbits(p, cx, cy, r, stable, back = false)
        drawLabel(p, cx, cy, r)
    }

    /** Three independent 3D ring planes, projected with depth. Hidden segments are painted before the globe. */
    private fun drawOrbits(p: ContinuumPainter, cx: Float, cy: Float, r: Float, s: Signals, back: Boolean) {
        val c = JarvisDesign.Colors
        val segments = s.orbitSegments.coerceIn(32, 128)
        val inclinations = floatArrayOf(.24f, -.49f, .78f, -.31f)
        val azimuths = floatArrayOf(-.20f, .27f, -.42f, .55f)
        p.setAdditive(true)
        for (ring in 0 until 4) {
            val tilt = inclinations[ring]
            val yaw = azimuths[ring] + s.phase * (.07f + ring * .013f)
            val size = 1.25f + ring * .07f
            fun point(angle: Float, out: FloatArray) {
                val x = cos(angle) * size
                val y = sin(angle) * sin(tilt) * size
                val z = sin(angle) * cos(tilt) * size
                val xx = x * cos(yaw) - y * sin(yaw)
                val yy = x * sin(yaw) + y * cos(yaw)
                val perspective = 3.8f / (3.8f - z * .15f)
                out[0] = cx + xx * r * perspective
                out[1] = cy + (yy + .10f) * r * perspective
                out[2] = z
            }
            val first = FloatArray(3)
            val second = FloatArray(3)
            point(0f, first)
            var a = first
            var b = second
            for (i in 1..segments) {
                point((2.0 * PI * i / segments).toFloat(), b)
                val behind = (a[2] + b[2]) < 0f
                if (behind == back) {
                    val alpha = if (behind) .28f else .70f
                    val half = (i + ring * 13) % segments < segments / 2
                    val color = when (ring) {
                        0 -> if (half) c.magenta else c.violetRim
                        1 -> if (half) c.cyan else c.violet
                        2 -> if (half) c.violetRim else c.cyan
                        else -> if (half) c.warn else c.magenta
                    }
                    val warm = if (ring == 3) 1.20f else 1f
                    p.drawLine(a[0], a[1], b[0], b[1], r * .070f * warm, Fill.Solid(color), alpha * .16f * warm)
                    p.drawLine(a[0], a[1], b[0], b[1], r * .034f * warm, Fill.Solid(color), alpha * .28f * warm)
                    p.drawLine(a[0], a[1], b[0], b[1], r * .014f * warm, Fill.Solid(color), (alpha * warm).coerceAtMost(1f))
                    p.drawLine(a[0], a[1], b[0], b[1], r * .005f * warm, Fill.Solid(c.glowCore), (alpha * warm).coerceAtMost(1f))
                }
                val swap = a
                a = b
                b = swap
            }
            // An orbit light is decoration; event intensity is supplied separately by real memory/turn state.
            val bead = a
            point(s.phase * (.91f + ring * .11f) + ring * 2.1f, bead)
            if ((bead[2] < 0f) == back) {
                val color = when (ring) {
                    1 -> c.violetRim
                    3 -> c.warn
                    else -> c.cyan
                }
                JarvisNeon.halo(p, bead[0], bead[1], r * .09f, color, .35f)
                p.fillCircle(bead[0], bead[1], r * .014f, Fill.Solid(c.glowCore))
            }
        }
        p.setAdditive(false)
    }

    private fun drawNet(p: ContinuumPainter, cx: Float, cy: Float, r: Float, s: Signals) {
        val C = JarvisDesign.Colors
        val tilt = .32f; val rot = s.phase * 2f * PI.toFloat() / 12f
        fun proj(lat: Float, lon: Float, out: FloatArray) {
            val x = cos(lat) * sin(lon); val y = sin(lat); val z = cos(lat) * cos(lon)
            val y2 = y * cos(tilt) - z * sin(tilt); val z2 = y * sin(tilt) + z * cos(tilt)
            out[0] = cx + x * r * .97f
            out[1] = cy - y2 * r * .97f
            out[2] = z2
        }
        p.setAdditive(true)
        val lats = 7; val lons = 14; val steps = 40
        val a = .28f + .25f * s.recall
        val first = FloatArray(3)
        val second = FloatArray(3)
        for (i in 1 until lats) {
            val lat = (-PI / 2 + PI * i / lats).toFloat()
            var prev = first
            var q = second
            proj(lat, 0f + rot, prev)
            for (k in 1..steps) {
                proj(lat, (2 * PI * k / steps).toFloat() + rot, q)
                if (q[2] > -.15f && prev[2] > -.15f) p.drawLine(prev[0], prev[1], q[0], q[1], r * .006f, Fill.Solid(C.cyan), a * (.35f + .65f * q[2].coerceAtLeast(0f)))
                val swap = prev
                prev = q
                q = swap
            }
        }
        for (j in 0 until lons) {
            val lon = (2 * PI * j / lons).toFloat() + rot
            var prev = first
            var q = second
            proj((-PI / 2).toFloat(), lon, prev)
            for (k in 1..steps) {
                proj((-PI / 2 + PI * k / steps).toFloat(), lon, q)
                if (q[2] > -.15f && prev[2] > -.15f) p.drawLine(prev[0], prev[1], q[0], q[1], r * .006f, Fill.Solid(C.cyan), a * (.35f + .65f * q[2].coerceAtLeast(0f)))
                val swap = prev
                prev = q
                q = swap
            }
        }
        for (i in 1 until lats) for (j in 0 until lons) {
            proj((-PI / 2 + PI * i / lats).toFloat(), (2 * PI * j / lons).toFloat() + rot, first)
            val q = first
            if (q[2] < 0f) continue
            val col = if ((i + j) % 3 == 0) JarvisDesign.Colors.violetSoft else C.cyanSoft
            p.fillCircle(q[0], q[1], r * (.012f + .01f * q[2]), Fill.Solid(col), .5f + .5f * q[2])
        }
        p.setAdditive(false)
    }

    private val brainPath = VectorPath()

    /** Gehirn: zwei Hemisphären (Lappenkontur), Mittelfurche, Windungen; Mitte ([bx],[by]), Breite ≈ 1.40 r. */
    private fun drawBrain(p: ContinuumPainter, bx: Float, by: Float, r: Float, s: Signals) {
        val C = JarvisDesign.Colors
        val hw = r * .68f; val hh = r * .46f
        val glow = .65f + .25f * s.pulse.coerceIn(0f, 1f) + if (s.processing) .1f else 0f
        p.setAdditive(true)
        p.fillOval(bx - hw * 1.25f, by - hh * 1.4f, bx + hw * 1.25f, by + hh * 1.4f,
            Fill.Radial(bx, by, hw * 1.25f, intArrayOf(Argb.withAlpha(C.cyan, .22f * glow), Argb.withAlpha(C.violetSoft, .38f * glow), Argb.withAlpha(C.cyanDeep, .14f), 0)), 1f)
        for (side in intArrayOf(-1, 1)) {
            brainPath.reset()
            val n = 48
            for (k in 0..n) {
                val t = (PI * k / n).toFloat()            // 0..π: von der Mittelfurche oben über außen nach unten
                val ex = sin(t); val ey = -cos(t)
                val lobe = 1f + .045f * sin(t * 11f)        // Lappenwellen
                val flat = if (ey > 0f) .62f else 1.05f      // Unterseite flacher (Schläfenlappen)
                val x = bx + side * (r * .03f + ex * hw * .97f * lobe)
                val y = by + ey * hh * lobe * flat
                if (k == 0) brainPath.moveTo(x, y) else brainPath.lineTo(x, y)
            }
            brainPath.close()
            p.fillPath(brainPath, Fill.Linear(bx, by - hh, bx + side * hw, by + hh,
                intArrayOf(C.glowCore, C.cyanDeep, C.violetSoft)), .72f * glow)
            p.strokePath(brainPath, maxOf(1.15f, r * .016f), Fill.Linear(bx, by, bx + side * hw, by, intArrayOf(C.cyanSoft, C.violetSoft)), .55f)
            // Windungen: konturparallele Lappenlinien (eingerückte Kopien der Kontur) + Rippen aus der Mittelfurche
            for (inset in floatArrayOf(.82f, .64f, .46f)) {
                val g = VectorPath(); val n = 40
                for (q in 0..n) {
                    val t = (PI * q / n).toFloat(); val ex = sin(t); val ey = -cos(t)
                    val wob = 1f + .09f * sin(t * (14f + inset * 6f) + inset * 5f)
                    val x = bx + side * (r * .05f + ex * hw * inset * wob); val y = by + ey * hh * inset * wob * (if (ey > 0f) .62f else 1.05f)
                    if (q == 0) g.moveTo(x, y) else g.lineTo(x, y)
                }
                p.strokePath(g, r * .01f, Fill.Solid(if (inset > .7f) C.violetSoft else C.glowCore), .6f * glow)
            }
            for (q in 0 until 9) {
                val t = (PI * (q + .5f) / 9).toFloat()
                val ex = sin(t); val ey = -cos(t) * (if (cos(t) < 0f) .62f else 1.05f)
                val g = VectorPath().moveTo(bx + side * r * .02f, by + hh * .45f)
                val mx = bx + side * ex * hw * .45f; val my = by + ey * hh * .45f + hh * .1f * hash(q, side + 5)
                g.quadTo(mx + side * r * .04f, my - r * .04f, bx + side * ex * hw * .85f, by + ey * hh * .85f)
                p.strokePath(g, r * .009f, Fill.Solid(C.cyanSoft), .45f * glow)
            }
            // Glow nach innen: dunkler Rand → heller Kern
            p.fillPath(brainPath, Fill.Radial(bx + side * hw * .45f, by + hh * .1f, hw * .7f,
                intArrayOf(Argb.withAlpha(C.cyanSoft, .25f), 0)), glow)
        }
        // Mittelfurche (hellster Punkt in ref5)
        p.drawLine(bx, by - hh * 1.02f, bx, by + hh * .8f, r * .016f, Fill.Solid(C.activeWhite), .8f)
        p.fillOval(bx - r * .3f, by + hh * .25f, bx + r * .3f, by + hh * .75f, Fill.Radial(bx, by + hh * .5f, r * .3f, intArrayOf(Argb.withAlpha(C.activeWhite, .85f), Argb.withAlpha(C.glowCore, .35f), 0)), glow)
        p.setAdditive(false)
    }

    /** Funkelpartikel im Netz und um die Kugel: feste, deterministische Lage (keine Datenanzeige). */
    private fun drawDust(p: ContinuumPainter, cx: Float, cy: Float, r: Float, count: Int) {
        val C = JarvisDesign.Colors
        p.setAdditive(true)
        for (i in 0 until count.coerceIn(0, 70)) {
            val a = hash(i, 11) * 2f * PI.toFloat(); val d = r * (.2f + 1.35f * hash(i, 23))
            val x = cx + cos(a) * d; val y = cy + sin(a) * d * .9f
            if (kotlin.math.abs(x - cx) < r * .45f && y > cy && y < cy + r * .85f) continue
            val s = r * (.007f + .016f * hash(i, 37)); val col = if (i % 3 == 0) C.violetRim else C.glowCore
            p.fillCircle(x, y, s * 3.8f, Fill.Radial(x, y, s * 3.8f, intArrayOf(Argb.withAlpha(col, .62f), 0)))
            p.fillCircle(x, y, s, Fill.Solid(C.activeWhite), .9f)
            if (i % 9 == 0) { p.drawLine(x - s * 5, y, x + s * 5, y, s * .5f, Fill.Solid(col), .7f); p.drawLine(x, y - s * 5, x, y + s * 5, s * .5f, Fill.Solid(col), .7f) }
        }
        p.setAdditive(false)
    }

    private fun hash(i: Int, j: Int): Float { var h = i * 374761393 + j * 668265263; h = (h xor (h ushr 13)) * 1274126177; return ((h xor (h ushr 16)) and 0xFFFF) / 65535f }

    private val infinity = VectorPath()

    private fun drawLabel(p: ContinuumPainter, cx: Float, cy: Float, r: Float) {
        val C = JarvisDesign.Colors
        val iy = cy + r * .18f; val iw = r * .19f; val ih = r * .09f
        infinity.reset().moveTo(cx, iy)
            .cubicTo(cx + iw * .5f, iy - ih * 1.4f, cx + iw * 1.15f, iy - ih * 1.1f, cx + iw * 1.15f, iy)
            .cubicTo(cx + iw * 1.15f, iy + ih * 1.1f, cx + iw * .5f, iy + ih * 1.4f, cx, iy)
            .cubicTo(cx - iw * .5f, iy - ih * 1.4f, cx - iw * 1.15f, iy - ih * 1.1f, cx - iw * 1.15f, iy)
            .cubicTo(cx - iw * 1.15f, iy + ih * 1.1f, cx - iw * .5f, iy + ih * 1.4f, cx, iy)
        p.setAdditive(true); p.strokePath(infinity, r * .06f, Fill.Solid(C.cyan), .25f); p.setAdditive(false)
        p.strokePath(infinity, r * .03f, Fill.Solid(C.cyan), 1f)
        val st = TextStyle(FontFamily.DISPLAY, 500, maxOf(7f, r * .11f))
        p.drawText(label1, cx, cy + r * .52f, st, Fill.Solid(Argb.withAlpha(C.activeWhite, .62f)), TextAlign.CENTER)
        p.drawText(label2, cx, cy + r * .70f, st, Fill.Solid(Argb.withAlpha(C.cyanSoft, .55f)), TextAlign.CENTER)
    }

    /** Luminous inner source (59509): white-cyan centre, violet/magenta corona, slow sine breathing. */
    private fun drawCore(p: ContinuumPainter, cx: Float, cy: Float, r: Float, s: Signals) {
        val C = JarvisDesign.Colors
        val breath = .5f + .5f * sin(s.phase * 2f * PI.toFloat() * .35f)
        val k = .85f + .15f * breath + .25f * s.pulse.coerceIn(0f, 1f) + if (s.processing) .12f else 0f
        val y = cy + r * .10f
        p.setAdditive(true)
        // Fills depend on geometry only (cached); breathing changes the paint alpha, so no shader per frame.
        val g = listOf("core", cx.toInt(), cy.toInt(), r.toInt())
        // The cached colours carry the maximum gain; alpha k / CORE_GAIN_MAX keeps real pulse and processing visible.
        val corona = JarvisSceneFills.cached(g + 0) { Fill.Radial(cx, y, r * .78f,
            intArrayOf(Argb.withAlpha(C.magenta, .20f * CORE_GAIN_MAX), Argb.withAlpha(C.violet, .14f * CORE_GAIN_MAX), 0)) }
        val heart = JarvisSceneFills.cached(g + 1) { Fill.Radial(cx, y, r * .42f,
            intArrayOf(Argb.withAlpha(C.glowCore, (.75f * CORE_GAIN_MAX).coerceAtMost(1f)),
                Argb.withAlpha(C.cyan, .35f * CORE_GAIN_MAX), 0)) }
        val shell = JarvisSceneFills.cached(g + 2) { Fill.Sweep(cx, cy, intArrayOf(C.violetRim, C.cyan, C.magenta, C.violetRim)) }
        p.fillCircle(cx, y, r * .78f, corona, (k / CORE_GAIN_MAX).coerceIn(0f, 1f))
        p.fillCircle(cx, y, r * .42f, heart, (k / CORE_GAIN_MAX).coerceIn(0f, 1f))
        // Second, inner glass shell slightly offset in depth.
        p.strokeCircle(cx, cy, r * .84f, r * .012f, shell, .35f)
        // Light pillars from the pedestal into the sphere.
        for (i in -2..2) {
            val x = cx + i * r * .16f
            val a = (.22f - kotlin.math.abs(i) * .05f) * (.7f + .3f * sin(s.phase * 2f * PI.toFloat() * .5f + i))
            val pillar = JarvisSceneFills.cached(g + (10 + i)) {
                Fill.Linear(x, cy + r * 1.02f, x, cy + r * .1f, intArrayOf(C.cyan, Argb.withAlpha(C.cyan, 0f)))
            }
            p.fillRect(x - r * .02f, cy + r * .1f, x + r * .02f, cy + r * 1.02f, pillar, a)
        }
        p.setAdditive(false)
    }

    private fun drawPedestal(p: ContinuumPainter, cx: Float, top: Float, r: Float) {
        val C = JarvisDesign.Colors
        p.fillOval(cx - r * 1.45f, top - r * .05f, cx + r * 1.45f, top + r * .42f, Fill.Solid(C.glowHalo), .95f)
        p.setAdditive(true)
        // Tiefe: drei gestapelte Stufen, je Stufe Außen- und Innenring
        for (tier in 0 until 3) {
            val ty = top + r * (.10f + tier * .06f); val k0 = .95f + tier * .14f
            p.fillOval(cx - r * k0, ty - r * .2f * k0, cx + r * k0, ty + r * .2f * k0, Fill.Solid(C.glowHalo), .5f)
            for ((j, k) in floatArrayOf(k0, k0 * .86f, k0 * .7f).withIndex()) {
                val ry = r * .2f * k
                val col = if ((tier + j) % 2 == 0) C.cyan else C.violet
                p.strokeOval(cx - r * k, ty - ry, cx + r * k, ty + ry, r * .03f, Fill.Solid(col), .12f)
                p.strokeOval(cx - r * k, ty - ry, cx + r * k, ty + ry, r * (if (j == 0) .016f else .009f),
                    Fill.Linear(cx - r * k, ty, cx + r * k, ty, intArrayOf(C.violet, col, C.glowCore, col, C.violet)), if (j == 0) .95f else .6f)
            }
        }
        p.fillOval(cx - r * 1.15f, top - r * .02f, cx + r * 1.15f, top + r * .38f,
            Fill.Radial(cx, top + r * .16f, r * 1.15f,
                intArrayOf(Argb.withAlpha(C.glowCore, .55f), Argb.withAlpha(C.cyan, .30f),
                    Argb.withAlpha(C.warn, .10f), 0)), 1f)
        p.fillOval(cx - r * .9f, top + r * .06f, cx + r * .9f, top + r * .28f, Fill.Radial(cx, top + r * .17f, r * .9f,
            intArrayOf(Argb.withAlpha(C.glowCore, .45f), Argb.withAlpha(C.cyan, .30f), Argb.withAlpha(C.magenta, .10f), 0)), 1f)
        p.strokeOval(cx - r * 1.32f, top + r * .02f, cx + r * 1.32f, top + r * .40f, r * .012f,
            Fill.Linear(cx - r * 1.32f, top, cx + r * 1.32f, top, intArrayOf(C.magenta, C.violetRim, C.cyan, C.warn, C.magenta)), .85f)
        p.strokeOval(cx - r * 1.40f, top - r * .02f, cx + r * 1.40f, top + r * .46f, r * .020f,
            Fill.Solid(C.warn), .38f)
        p.strokeOval(cx - r * 1.40f, top - r * .02f, cx + r * 1.40f, top + r * .46f, r * .008f,
            Fill.Solid(C.warn), .70f)
        // Lichtbalken unter dem Sockel
        val by = top + r * .40f; val bw = r * .16f; val gap = r * .06f
        for (k in -1..1) {
            val x = cx + k * (bw + gap)
            p.fillRoundRect(x - bw / 2, by, x + bw / 2, by + r * .035f, r * .02f, Fill.Solid(C.cyan), .95f)
            p.fillRoundRect(x - bw / 2 - r * .02f, by - r * .02f, x + bw / 2 + r * .02f, by + r * .055f, r * .04f, Fill.Solid(C.cyan), .2f)
        }
        p.setAdditive(false)
    }

    /** Upward holographic cone from the pedestal into the sphere (ref B). */
    private fun drawLightCone(p: ContinuumPainter, cx: Float, cy: Float, r: Float) {
        val C = JarvisDesign.Colors
        val baseY = cy + r * 1.08f
        val topY = cy + r * .06f
        p.setAdditive(true)
        val steps = 9
        for (i in 0 until steps) {
            val t = i / (steps - 1f)
            val y = baseY + (topY - baseY) * t
            val w = r * (1.18f - t * .86f)
            val a = (1f - t) * .32f
            p.fillOval(cx - w, y - r * .045f, cx + w, y + r * .045f,
                Fill.Solid(if (i == 2 || i == 5) C.warn else if (i % 2 == 0) C.cyan else C.violetRim), a)
        }
        val beam = JarvisSceneFills.cached(listOf("cone", cx.toInt(), cy.toInt(), r.toInt())) {
            Fill.Linear(cx, baseY, cx, cy - r * .18f,
                intArrayOf(Argb.withAlpha(C.cyan, .38f), Argb.withAlpha(C.magenta, .10f), 0))
        }
        p.fillRect(cx - r * .24f, cy - r * .12f, cx + r * .24f, baseY, beam, .58f)
        p.setAdditive(false)
    }

    /**
     * Volumetric neuron plexus inside two lobes. Node count follows [Signals.particles]
     * so JarvisRenderQuality can thin the mesh on a warm device. Geometry is deterministic.
     */
    private fun drawNeurons(p: ContinuumPainter, cx: Float, cy: Float, r: Float, s: Signals) {
        val count = (18 + s.particles / 3).coerceIn(18, 36)
        val xs = FloatArray(count); val ys = FloatArray(count); val zs = FloatArray(count)
        val rot = s.phase * .11f
        val cr = cos(rot); val sr = sin(rot)
        for (i in 0 until count) {
            val u = hash(i, 3); val v = hash(i, 7); val w = hash(i, 13)
            val theta = u * PI.toFloat()
            val phi = v * 2f * PI.toFloat()
            // Fill the glass volume, with a mild two-lobe bias so it still reads as a brain.
            val lobe = if (i % 2 == 0) -1f else 1f
            val rad = .22f + .70f * w
            val x = sin(theta) * cos(phi) * r * .82f * rad + lobe * r * .08f
            val y = cos(theta) * r * .72f * rad - r * .10f
            val z = sin(theta) * sin(phi) * r * .82f * rad
            xs[i] = cx + x * cr - z * sr
            ys[i] = cy + y
            zs[i] = x * sr + z * cr
        }
        p.setAdditive(true)
        val maxDist2 = (r * .48f) * (r * .48f)
        val C = JarvisDesign.Colors
        val stroke = maxOf(1.15f, r * .022f)
        for (i in 0 until count) for (j in i + 1 until count) {
            val dx = xs[i] - xs[j]; val dy = ys[i] - ys[j]; val dz = zs[i] - zs[j]
            val d2 = dx * dx + dy * dy + dz * dz
            if (d2 > maxDist2) continue
            val z = ((zs[i] + zs[j]) * .5f / r).coerceIn(-1f, 1f)
            if (z < -.42f) continue
            val a = (.28f + .55f * ((z + 1f) * .5f)) * (1f - d2 / maxDist2)
            val col = when ((i + j) % 3) { 0 -> C.magenta; 1 -> C.violetRim; else -> C.cyan }
            p.drawLine(xs[i], ys[i], xs[j], ys[j], stroke, Fill.Solid(col), a)
        }
        for (i in 0 until count) {
            val z = (zs[i] / r).coerceIn(-1f, 1f)
            if (z < -.45f) continue
            val col = if (i % 3 == 0) C.magenta else C.cyan
            val sz = maxOf(1.15f, r * (.016f + .020f * ((z + 1f) * .5f)))
            JarvisSceneFills.halo(p, xs[i], ys[i], sz * 3.6f, col, .40f + .30f * ((z + 1f) * .5f))
            p.fillCircle(xs[i], ys[i], sz, Fill.Solid(C.glowCore), .92f)
        }
        p.setAdditive(false)
    }

    companion object {
        /** Largest core gain: breath 1 + MemoryStored pulse .25 + processing .12. */
        const val CORE_GAIN_MAX = 1.37f
        /**
         * Sphere + light pedestal height in radii (matches [draw] and the host preview stack).
         * Using this (not 2.9) lets the original orb fill the Ref-B hero column.
         */
        const val STACK_HEIGHT = 2.44f

        data class Placement(val cx: Float, val cy: Float, val radius: Float)

        /** Largest radius that still fits [slotW] × [slotH], including the pedestal. */
        fun radiusFor(slotW: Float, slotH: Float): Float {
            if (!slotW.isFinite() || !slotH.isFinite() || slotW <= 0f || slotH <= 0f) return 1f
            return minOf(slotW * .48f, slotH / STACK_HEIGHT).coerceAtLeast(1f)
        }

        /** Gap between the continuum title/slogan box and the top of the glass sphere. */
        const val HEADER_CLEARANCE_DP = 4f

        /**
         * Visual column for the original orb. Title and slogan in [header] stay readable
         * (ref-b: title above, sphere on its pedestal below). Layout boxes stay put.
         */
        fun columnFor(orb: JarvisShellLayout.Box, header: JarvisShellLayout.Box?): JarvisShellLayout.Box {
            if (header == null || header.width <= 0f || header.height <= 0f) return orb
            val aligned = kotlin.math.abs(header.left - orb.left) < 12f &&
                kotlin.math.abs(header.right - orb.right) < 12f
            if (!aligned) return orb
            val safeTop = maxOf(orb.top, header.bottom + HEADER_CLEARANCE_DP)
            if (safeTop >= orb.bottom) return orb
            return JarvisShellLayout.Box(orb.left, safeTop, orb.right, orb.bottom)
        }

        fun placementIn(column: JarvisShellLayout.Box): Placement {
            val r = radiusFor(column.width, column.height)
            val used = STACK_HEIGHT * r
            val top = column.top + (column.height - used).coerceAtLeast(0f) / 2f
            return Placement(column.centerX, top + r, r)
        }

        /** True when the glass sphere disk intersects the title/slogan box. */
        fun overlapsHeader(placement: Placement, header: JarvisShellLayout.Box): Boolean {
            if (header.width <= 0f || header.height <= 0f) return false
            if (!placement.cx.isFinite() || !placement.cy.isFinite() ||
                !placement.radius.isFinite() || placement.radius <= 0f) return true
            val nx = placement.cx.coerceIn(header.left, header.right)
            val ny = placement.cy.coerceIn(header.top, header.bottom)
            val dx = placement.cx - nx
            val dy = placement.cy - ny
            return dx * dx + dy * dy < placement.radius * placement.radius
        }

        @Suppress("unused") private fun len(x: Float, y: Float) = sqrt(x * x + y * y)
    }
}
