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
        val C = JarvisDesign.Colors
        drawPedestal(p, cx, cy + r * 1.0f, r)
        drawOrbits(p, cx, cy, r, s, back = true)
        // Glaskörper
        p.fillCircle(cx, cy, r, Fill.Radial(cx, cy - r * .25f, r * 1.05f,
            intArrayOf(C.cyanDeep, C.backgroundHaze, C.pillSurface, Argb.withAlpha(C.pillSurface, .9f)), floatArrayOf(0f, .45f, .8f, 1f)), .92f)
        drawNet(p, cx, cy, r, s)
        drawCore(p, cx, cy, r, s)
        drawBrain(p, cx, cy - r * .33f, r, s)
        // Rand-Bloom: mehrere additive Ringe (violett oben/rechts, cyan unten/links), dann scharfe Kante.
        p.setAdditive(true)
        val rimSweep = intArrayOf(C.cyan, C.violet, C.violetRim, C.violet, C.violetRim, C.cyan, C.violet, C.cyan)
        for ((w, a) in listOf(.32f to .07f, .2f to .12f, .11f to .22f, .05f to .45f))
            p.strokeCircle(cx, cy, r * (1f + w * .15f), r * w, Fill.Sweep(cx, cy, rimSweep), a)
        p.strokeCircle(cx, cy, r * .995f, r * .016f, Fill.Sweep(cx, cy, intArrayOf(C.glowCore, C.violetRim, C.violet, C.cyan, C.glowCore)), 1f)
        p.strokeArc(cx - r * .97f, cy - r * .97f, cx + r * .97f, cy + r * .97f, 200f, 110f, r * .012f, Fill.Solid(C.violetRim), .9f)
        p.strokeArc(cx - r * .97f, cy - r * .97f, cx + r * .97f, cy + r * .97f, 330f, 70f, r * .012f, Fill.Solid(C.violetRim), .9f)
        // Glas-Glanz: Sichel oben links + kleiner Spiegelpunkt
        p.strokeArc(cx - r * .86f, cy - r * .86f, cx + r * .86f, cy + r * .86f, 200f, 60f, r * .05f, Fill.Solid(C.activeWhite), .16f)
        p.strokeArc(cx - r * .86f, cy - r * .86f, cx + r * .86f, cy + r * .86f, 210f, 40f, r * .015f, Fill.Solid(C.activeWhite), .45f)
        p.fillCircle(cx - r * .55f, cy - r * .58f, r * .05f, Fill.Radial(cx - r * .55f, cy - r * .58f, r * .05f, intArrayOf(Argb.withAlpha(C.activeWhite, .8f), 0)))
        p.setAdditive(false)
        drawDust(p, cx, cy, r, s.particles)
        drawOrbits(p, cx, cy, r, s, back = false)
        drawLabel(p, cx, cy, r)
    }

    /** Three independent 3D ring planes, projected with depth. Hidden segments are painted before the globe. */
    private fun drawOrbits(p: ContinuumPainter, cx: Float, cy: Float, r: Float, s: Signals, back: Boolean) {
        val c = JarvisDesign.Colors
        val segments = s.orbitSegments.coerceIn(32, 128)
        val inclinations = floatArrayOf(.24f, -.49f, .78f)
        val azimuths = floatArrayOf(-.20f, .27f, -.42f)
        p.setAdditive(true)
        for (ring in 0 until 3) {
            val tilt = inclinations[ring]
            val yaw = azimuths[ring] + s.phase * (.07f + ring * .013f)
            val size = 1.25f + ring * .07f
            fun point(angle: Float): FloatArray {
                val x = cos(angle) * size
                val y = sin(angle) * sin(tilt) * size
                val z = sin(angle) * cos(tilt) * size
                val xx = x * cos(yaw) - y * sin(yaw)
                val yy = x * sin(yaw) + y * cos(yaw)
                val perspective = 3.8f / (3.8f - z * .15f)
                return floatArrayOf(cx + xx * r * perspective, cy + (yy + .10f) * r * perspective, z)
            }
            var a = point(0f)
            for (i in 1..segments) {
                val b = point((2.0 * PI * i / segments).toFloat())
                val behind = (a[2] + b[2]) < 0f
                if (behind == back) {
                    val alpha = if (behind) .28f else .70f
                    val half = (i + ring * 13) % segments < segments / 2
                    val color = when (ring) { 0 -> if (half) c.magenta else c.violetRim; 1 -> if (half) c.cyan else c.violet
                        else -> if (half) c.violetRim else c.cyan }
                    p.drawLine(a[0], a[1], b[0], b[1], r * .060f, Fill.Solid(color), alpha * .10f)
                    p.drawLine(a[0], a[1], b[0], b[1], r * .030f, Fill.Solid(color), alpha * .18f)
                    p.drawLine(a[0], a[1], b[0], b[1], r * .012f, Fill.Solid(color), alpha)
                    p.drawLine(a[0], a[1], b[0], b[1], r * .004f, Fill.Solid(c.glowCore), alpha)
                }
                a = b
            }
            // An orbit light is decoration; event intensity is supplied separately by real memory/turn state.
            val bead = point(s.phase * (.91f + ring * .11f) + ring * 2.1f)
            if ((bead[2] < 0f) == back) {
                val color = if (ring == 1) c.violetRim else c.cyan
                JarvisNeon.halo(p, bead[0], bead[1], r * .09f, color, .35f)
                p.fillCircle(bead[0], bead[1], r * .014f, Fill.Solid(c.glowCore))
            }
        }
        p.setAdditive(false)
    }

    private fun drawNet(p: ContinuumPainter, cx: Float, cy: Float, r: Float, s: Signals) {
        val C = JarvisDesign.Colors
        val tilt = .32f; val rot = s.phase * 2f * PI.toFloat() / 12f
        fun proj(lat: Float, lon: Float): FloatArray {
            val x = cos(lat) * sin(lon); val y = sin(lat); val z = cos(lat) * cos(lon)
            val y2 = y * cos(tilt) - z * sin(tilt); val z2 = y * sin(tilt) + z * cos(tilt)
            return floatArrayOf(cx + x * r * .97f, cy - y2 * r * .97f, z2)
        }
        p.setAdditive(true)
        val lats = 7; val lons = 14; val steps = 40
        val a = .28f + .25f * s.recall
        for (i in 1 until lats) {
            val lat = (-PI / 2 + PI * i / lats).toFloat()
            var prev = proj(lat, 0f + rot)
            for (k in 1..steps) {
                val q = proj(lat, (2 * PI * k / steps).toFloat() + rot)
                if (q[2] > -.15f && prev[2] > -.15f) p.drawLine(prev[0], prev[1], q[0], q[1], r * .006f, Fill.Solid(C.cyan), a * (.35f + .65f * q[2].coerceAtLeast(0f)))
                prev = q
            }
        }
        for (j in 0 until lons) {
            val lon = (2 * PI * j / lons).toFloat() + rot
            var prev = proj((-PI / 2).toFloat(), lon)
            for (k in 1..steps) {
                val q = proj((-PI / 2 + PI * k / steps).toFloat(), lon)
                if (q[2] > -.15f && prev[2] > -.15f) p.drawLine(prev[0], prev[1], q[0], q[1], r * .006f, Fill.Solid(C.cyan), a * (.35f + .65f * q[2].coerceAtLeast(0f)))
                prev = q
            }
        }
        for (i in 1 until lats) for (j in 0 until lons) {
            val q = proj((-PI / 2 + PI * i / lats).toFloat(), (2 * PI * j / lons).toFloat() + rot)
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
            Fill.Radial(bx, by, hw * 1.25f, intArrayOf(Argb.withAlpha(C.violetSoft, .35f * glow), Argb.withAlpha(C.cyanDeep, .12f), 0)), 1f)
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
                intArrayOf(C.glowCore, C.cyanDeep, C.violetSoft)), .55f * glow)
            p.strokePath(brainPath, r * .02f, Fill.Linear(bx, by, bx + side * hw, by, intArrayOf(C.cyanSoft, C.violetSoft)), .9f)
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
            val s = r * (.006f + .014f * hash(i, 37)); val col = if (i % 3 == 0) C.violetRim else C.glowCore
            p.fillCircle(x, y, s * 3f, Fill.Radial(x, y, s * 3f, intArrayOf(Argb.withAlpha(col, .5f), 0)))
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
        val st = TextStyle(FontFamily.TEXT, 500, r * .165f)
        p.drawText(label1, cx, cy + r * .5f, st, Fill.Solid(C.activeWhite), TextAlign.CENTER)
        p.drawText(label2, cx, cy + r * .72f, st, Fill.Solid(C.activeWhite), TextAlign.CENTER)
    }

    /** Luminous inner source (59509): white-cyan centre, violet/magenta corona, slow sine breathing. */
    private fun drawCore(p: ContinuumPainter, cx: Float, cy: Float, r: Float, s: Signals) {
        val C = JarvisDesign.Colors
        val breath = .5f + .5f * sin(s.phase * 2f * PI.toFloat() * .35f)
        val k = .85f + .15f * breath + .25f * s.pulse.coerceIn(0f, 1f) + if (s.processing) .12f else 0f
        val y = cy + r * .10f
        p.setAdditive(true)
        p.fillCircle(cx, y, r * .78f, Fill.Radial(cx, y, r * .78f,
            intArrayOf(Argb.withAlpha(C.magenta, .20f * k), Argb.withAlpha(C.violet, .14f * k), 0)), 1f)
        p.fillCircle(cx, y, r * .42f, Fill.Radial(cx, y, r * .42f,
            intArrayOf(Argb.withAlpha(C.glowCore, .75f * k), Argb.withAlpha(C.cyan, .35f * k), 0)), 1f)
        // Second, inner glass shell slightly offset in depth.
        p.strokeCircle(cx, cy, r * .84f, r * .012f, Fill.Sweep(cx, cy, intArrayOf(C.violetRim, C.cyan, C.magenta, C.violetRim)), .35f)
        // Light pillars from the pedestal into the sphere.
        for (i in -2..2) {
            val x = cx + i * r * .16f
            val a = (.22f - kotlin.math.abs(i) * .05f) * (.7f + .3f * sin(s.phase * 2f * PI.toFloat() * .5f + i))
            p.fillRect(x - r * .02f, cy + r * .1f, x + r * .02f, cy + r * 1.02f, Fill.Linear(x, cy + r * 1.02f, x, cy + r * .1f,
                intArrayOf(Argb.withAlpha(C.cyan, a), 0)), 1f)
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
        p.fillOval(cx - r * .9f, top + r * .06f, cx + r * .9f, top + r * .28f, Fill.Radial(cx, top + r * .17f, r * .9f,
            intArrayOf(Argb.withAlpha(C.glowCore, .45f), Argb.withAlpha(C.cyan, .30f), Argb.withAlpha(C.magenta, .10f), 0)), 1f)
        p.strokeOval(cx - r * 1.32f, top + r * .02f, cx + r * 1.32f, top + r * .40f, r * .012f,
            Fill.Linear(cx - r * 1.32f, top, cx + r * 1.32f, top, intArrayOf(C.magenta, C.violetRim, C.cyan, C.violetRim, C.magenta)), .85f)
        // Lichtbalken unter dem Sockel
        val by = top + r * .40f; val bw = r * .16f; val gap = r * .06f
        for (k in -1..1) {
            val x = cx + k * (bw + gap)
            p.fillRoundRect(x - bw / 2, by, x + bw / 2, by + r * .035f, r * .02f, Fill.Solid(C.cyan), .95f)
            p.fillRoundRect(x - bw / 2 - r * .02f, by - r * .02f, x + bw / 2 + r * .02f, by + r * .055f, r * .04f, Fill.Solid(C.cyan), .2f)
        }
        p.setAdditive(false)
    }

    companion object {
        /** Ref5-Komposition (gemessen): Kugelradius ≈ 0.36 × Slotbreite, Mitte bei (0.63 w, 0.445 h) des Ausschnitts. */
        fun radiusFor(slotW: Float, slotH: Float) = minOf(slotW * .36f, slotH / 2.9f)
        @Suppress("unused") private fun len(x: Float, y: Float) = sqrt(x * x + y * y)
    }
}
