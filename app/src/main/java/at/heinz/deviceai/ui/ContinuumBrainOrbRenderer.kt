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
    data class Signals(val pulse: Float = 0f, val recall: Float = 0f, val hypothesis: Boolean = false, val phase: Float = 0f)

    var label1 = "Continuum"
    var label2 = "Brain"

    /** Kugelmitte ([cx],[cy]) und Radius [r] in px; Sockel und Lichtbalken liegen unter der Kugel bis cy + 1.30 r. */
    fun draw(p: ContinuumPainter, cx: Float, cy: Float, r: Float, s: Signals = Signals()) {
        val C = JarvisDesign.Colors
        drawPedestal(p, cx, cy + r * 1.0f, r)
        drawOrbits(p, cx, cy, r, back = true)
        // Glaskörper
        p.fillCircle(cx, cy, r, Fill.Radial(cx, cy - r * .25f, r * 1.05f,
            intArrayOf(C.cyanDeep, C.backgroundHaze, C.pillSurface, Argb.withAlpha(C.pillSurface, .9f)), floatArrayOf(0f, .45f, .8f, 1f)), .92f)
        drawNet(p, cx, cy, r, s)
        drawBrain(p, cx, cy - r * .33f, r, s)
        // Rand: violett oben/rechts, cyan unten/links (ref5)
        p.setAdditive(true)
        p.strokeCircle(cx, cy, r, r * .07f, Fill.Sweep(cx, cy, intArrayOf(C.cyan, C.violet, C.violetSoft, C.cyan, C.cyanDeep, C.cyan)), .55f)
        p.strokeCircle(cx, cy, r * .995f, r * .014f, Fill.Sweep(cx, cy, intArrayOf(C.cyanSoft, C.violet, C.violet, C.cyan, C.cyanSoft)), .95f)
        p.fillCircle(cx, cy, r * 1.12f, Fill.Radial(cx, cy, r * 1.12f, intArrayOf(0, 0, Argb.withAlpha(C.violet, .22f), 0), floatArrayOf(0f, .86f, .9f, 1f)))
        // Glanzlicht oben links
        p.fillOval(cx - r * .62f, cy - r * .86f, cx - r * .05f, cy - r * .5f, Fill.Radial(cx - r * .35f, cy - r * .7f, r * .35f,
            intArrayOf(Argb.withAlpha(C.activeWhite, .22f), 0)), 1f)
        p.setAdditive(false)
        drawOrbits(p, cx, cy, r, back = false)
        drawLabel(p, cx, cy, r)
    }

    /** Zwei geneigte Orbitringe (ref5: links/unten aus der Kugel heraus), hinterer und vorderer Teil getrennt. */
    private fun drawOrbits(p: ContinuumPainter, cx: Float, cy: Float, r: Float, back: Boolean) {
        val C = JarvisDesign.Colors
        p.setAdditive(true)
        for ((tilt, k) in listOf(-14f to 1.32f, 9f to 1.18f)) {
            p.save(); p.rotate(tilt, cx, cy + r * .25f)
            val l = cx - r * k; val rr = cx + r * k; val t = cy + r * .25f - r * .3f; val b = cy + r * .25f + r * .3f
            val start = if (back) 180f else 0f
            p.strokeArc(l, t, rr, b, start, 180f, r * .03f, Fill.Solid(C.violet), .18f)
            p.strokeArc(l, t, rr, b, start, 180f, r * .01f, Fill.Linear(l, 0f, rr, 0f, intArrayOf(C.violetSoft, C.cyan, C.violetRim)), if (back) .45f else .8f)
            p.restore()
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
        val glow = .75f + .25f * s.pulse
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
            // Windungen
            for (g in 0 until 6) {
                val gy = by - hh * .7f + g * hh * .3f
                val path = VectorPath()
                val x0 = bx + side * r * .08f
                val x1 = bx + side * hw * (.82f - .08f * kotlin.math.abs(g - 2.5f) / 2.5f)
                path.moveTo(x0, gy)
                val segs = 4
                for (k in 1..segs) {
                    val xa = x0 + (x1 - x0) * (k - .5f) / segs; val xb = x0 + (x1 - x0) * k / segs
                    path.quadTo(xa, gy + (if ((k + g) % 2 == 0) -1 else 1) * hh * .16f, xb, gy)
                }
                p.strokePath(path, r * .011f, Fill.Solid(if (g % 2 == 0) C.glowCore else C.violetSoft), .7f * glow)
            }
        }
        // Mittelfurche (hellster Punkt in ref5)
        p.drawLine(bx, by - hh * 1.02f, bx, by + hh * .8f, r * .016f, Fill.Solid(C.activeWhite), .8f)
        p.fillCircle(bx, by - hh * .1f, r * .1f, Fill.Radial(bx, by - hh * .1f, r * .1f, intArrayOf(Argb.withAlpha(C.activeWhite, .7f), 0)), glow)
        p.setAdditive(false)
    }

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

    private fun drawPedestal(p: ContinuumPainter, cx: Float, top: Float, r: Float) {
        val C = JarvisDesign.Colors
        p.fillOval(cx - r * 1.45f, top - r * .05f, cx + r * 1.45f, top + r * .42f, Fill.Solid(C.glowHalo), .95f)
        p.setAdditive(true)
        val rings = floatArrayOf(1.42f, 1.22f, 1.0f, .78f, .58f)
        for ((i, k) in rings.withIndex()) {
            val ry = r * .19f * k
            val col = if (i % 2 == 0) C.cyan else C.violet
            p.strokeOval(cx - r * k, top + r * .17f - ry, cx + r * k, top + r * .17f + ry, r * (if (i == 2) .022f else .011f),
                Fill.Linear(cx - r * k, top, cx + r * k, top, intArrayOf(C.violet, col, C.cyan, col, C.violet)), if (i == 2) .95f else .6f)
        }
        p.fillOval(cx - r * .9f, top + r * .06f, cx + r * .9f, top + r * .28f, Fill.Radial(cx, top + r * .17f, r * .9f,
            intArrayOf(Argb.withAlpha(C.cyan, .35f), Argb.withAlpha(C.cyanDeep, .12f), 0)), 1f)
        // Lichtbalken unter dem Sockel
        val by = top + r * .44f; val bw = r * .16f; val gap = r * .06f
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
