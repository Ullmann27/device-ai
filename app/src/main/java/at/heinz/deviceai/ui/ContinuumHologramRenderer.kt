package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The living Continuum core: holographic glass sphere with luminous inner network and infinity mark, tilted orbits,
 * a projection pedestal and the brand line, drawn through [ContinuumPainter] (identical on the device and in the JVM
 * preview). Geometry is measured from docs/design_targets/continuum-2026-10-22/reference-phone.png: in the 412 x 307 dp
 * hero box the sphere has its centre at (206, 117) dp and a radius of 101 dp.
 *
 * The renderer is stateless per frame. [Frame] carries the animation phase, the crossfaded state weights and the real
 * speech energy supplied by [at.heinz.deviceai.ContinuumHologramView]. Nothing here invents activity: state visuals
 * are drawn only with the weight of the state the app reports, and the speech lines move only with real PCM energy.
 */
class ContinuumHologramRenderer {
    enum class Mode { READY, LISTENING, THINKING, SPEAKING, RESEARCH, LEARNING }

    /** [weights] has one entry per [Mode] (crossfaded), [energy] 0..1, [audio] 0..1 real speech envelope. */
    class Frame(val phase: Float, val energy: Float, val audio: Float, val weights: FloatArray) {
        init { require(weights.size == Mode.entries.size) }
        fun weight(mode: Mode) = weights[mode.ordinal]
        companion object {
            fun still(mode: Mode = Mode.READY, phase: Float = .4f): Frame =
                Frame(phase, energyOf(mode), 0f, FloatArray(Mode.entries.size).also { it[mode.ordinal] = 1f })
            fun energyOf(mode: Mode) = when (mode) {
                Mode.READY -> .18f
                Mode.LISTENING -> .42f
                Mode.THINKING -> .78f
                Mode.SPEAKING -> .12f
                Mode.RESEARCH -> .62f
                Mode.LEARNING -> .52f
            }
        }
    }

    /**
     * Vertical composition in units of the sphere radius, measured from the two references:
     * phone (412 x 307 dp box) and Fold (410 x 291 dp box, compressed pedestal, no page marks).
     */
    data class Geometry(val centerY: Float, val radiusOfWidth: Float, val radiusOfHeight: Float, val pad: Float,
                        val wall: Float, val brand: Float, val tagline: Float, val marks: Float?, val flat: Float = 1f) {
        companion object {
            val PHONE = Geometry(centerY = .381f, radiusOfWidth = .2455f, radiusOfHeight = .329f, pad = 1.11f,
                wall = .33f, brand = 1.545f, tagline = 1.685f, marks = 1.795f)
            val FOLD = Geometry(centerY = .495f, radiusOfWidth = .2366f, radiusOfHeight = .3334f, pad = .99f,
                wall = .1f, brand = 1.27f, tagline = 1.42f, marks = null, flat = .62f)
        }
    }

    var geometry: Geometry = Geometry.PHONE
        set(value) { field = value; w = 0f; h = 0f }

    /** Draw the "CONTINUUM / INTELLIGENCE LIVES ON" line and page marks below the pedestal. */
    var showBrand = true
    var pageCount = 3
    var pageIndex = 0

    private var w = 0f
    private var h = 0f
    private var cx = 0f
    private var cy = 0f
    private var r = 0f
    private var scale = 1f
    private val padY get() = cy + r * geometry.pad
    private val baseTopY get() = cy + r * (geometry.pad + .06f)
    private val flangeTopY get() = cy + r * (geometry.pad + .06f + geometry.wall)
    private val floorY get() = cy + r * (geometry.pad + .13f + geometry.wall)

    private lateinit var backdrop: Fill
    private lateinit var rays: Fill
    private lateinit var bloom: Fill
    private lateinit var glass: Fill
    private lateinit var glassCyan: Fill
    private lateinit var glassViolet: Fill
    private lateinit var rim: Fill
    private lateinit var innerGlass: Fill
    private lateinit var innerRim: Fill
    private lateinit var innerSpecular: Fill
    private lateinit var specular: Fill
    private lateinit var infinity: Fill
    private lateinit var infinityGlow: Fill
    private lateinit var beam: Fill
    private lateinit var padGlow: Fill
    private lateinit var padFace: Fill
    private lateinit var baseFace: Fill
    private lateinit var wall: Fill
    private lateinit var rimLight: Fill
    private lateinit var floorGlow: Fill
    private lateinit var padCore: Fill
    private lateinit var contactGlow: Fill
    private var orbitFills: List<Fill> = emptyList()
    private var floorFills: List<Fill> = emptyList()
    private lateinit var brandFill: Fill
    private lateinit var taglineFill: Fill

    private val path = VectorPath()
    private val stars = Array(70) { Star(it) }
    private val dust = Array(420) { Dust(it) }

    private class Star(i: Int) {
        val u = (i * 0.618034f + .137f) % 1f
        val v = (i * 0.754877f + .29f) % 1f
        val size = if (i % 9 == 0) 1.1f else if (i % 4 == 0) .7f else .42f
        val tint = i % 3
        val flare = i % 11 == 0
    }

    /** Particles inside the glass sphere (uniform on the disc, a warm cluster in the upper right as in the reference). */
    private class Dust(i: Int) {
        val radius: Float
        val angle: Float
        val warm = i % 6 == 0
        val size = if (i % 13 == 0) 1.15f else if (i % 3 == 0) .75f else .45f
        init {
            val t = (i * 0.618034f + .11f) % 1f
            radius = sqrt((i * 0.754877f + .07f) % 1f) * .93f
            angle = if (warm) (-.9f + t * 1.3f) else t * 2f * PI.toFloat()
        }
    }

    fun layout(width: Float, height: Float) {
        if (width == w && height == h) return
        w = width; h = height
        r = min(width * geometry.radiusOfWidth, height * geometry.radiusOfHeight)
        cx = width / 2f
        cy = height * geometry.centerY
        scale = r / 101f
        backdrop = Fill.Radial(cx, cy + r * .15f, r * 2.1f,
            intArrayOf(Argb.of(175, 24, 70, 175), Argb.of(110, 14, 46, 128), Argb.of(40, 8, 24, 70), 0),
            floatArrayOf(0f, .36f, .68f, 1f))
        rays = Fill.Linear(0f, cy - r * 1.25f, 0f, cy - r * .2f,
            intArrayOf(0, Argb.of(90, 80, 170, 255), Argb.of(0, 80, 170, 255)), floatArrayOf(0f, .55f, 1f))
        bloom = Fill.Radial(cx, cy, r * 1.34f,
            intArrayOf(0, 0, Argb.of(190, 70, 165, 255), Argb.of(95, 90, 120, 255), Argb.of(30, 100, 90, 255), 0),
            floatArrayOf(0f, .68f, .75f, .82f, .91f, 1f))
        glass = Fill.Radial(cx, cy + r * .08f, r,
            intArrayOf(Argb.of(245, 8, 22, 76), Argb.of(248, 5, 14, 52), Argb.of(248, 10, 30, 110), Argb.of(252, 40, 104, 250)),
            floatArrayOf(0f, .55f, .87f, 1f))
        glassCyan = Fill.Radial(cx - r * .66f, cy + r * .1f, r * .8f,
            intArrayOf(Argb.of(95, 40, 210, 255), Argb.of(30, 40, 180, 255), 0), floatArrayOf(0f, .55f, 1f))
        glassViolet = Fill.Radial(cx + r * .6f, cy - r * .1f, r * .85f,
            intArrayOf(Argb.of(120, 150, 60, 235), Argb.of(40, 120, 50, 220), 0), floatArrayOf(0f, .55f, 1f))
        rim = Fill.Sweep(cx, cy, intArrayOf(Argb.rgb(120, 150, 255), Argb.rgb(70, 170, 255), Argb.rgb(60, 220, 255),
            Argb.rgb(120, 240, 255), Argb.rgb(210, 245, 255), Argb.rgb(170, 130, 255), Argb.rgb(230, 90, 230), Argb.rgb(120, 150, 255)),
            floatArrayOf(0f, .14f, .38f, .55f, .72f, .83f, .92f, 1f))
        innerGlass = Fill.Radial(cx - r * .08f, cy - r * .1f, r * .44f,
            intArrayOf(Argb.of(160, 50, 44, 150), Argb.of(150, 28, 26, 104), Argb.of(175, 46, 66, 190), Argb.of(225, 100, 160, 255)),
            floatArrayOf(0f, .55f, .88f, 1f))
        innerRim = Fill.Sweep(cx, cy, intArrayOf(Argb.rgb(170, 110, 255), Argb.rgb(90, 160, 255), Argb.rgb(120, 225, 255),
            Argb.rgb(205, 235, 255), Argb.rgb(200, 110, 255), Argb.rgb(170, 110, 255)), floatArrayOf(0f, .25f, .5f, .7f, .85f, 1f))
        innerSpecular = Fill.Radial(cx - r * .14f, cy - r * .22f, r * .2f,
            intArrayOf(Argb.of(150, 210, 230, 255), Argb.of(30, 160, 190, 255), 0), floatArrayOf(0f, .5f, 1f))
        specular = Fill.Radial(cx - r * .45f, cy - r * .55f, r * .5f,
            intArrayOf(Argb.of(120, 200, 235, 255), Argb.of(30, 130, 200, 255), 0), floatArrayOf(0f, .45f, 1f))
        infinity = Fill.Linear(cx - r * .32f, cy, cx + r * .32f, cy,
            intArrayOf(Argb.rgb(120, 230, 255), Argb.rgb(150, 170, 255), Argb.rgb(200, 140, 255)))
        infinityGlow = Fill.Radial(cx, cy + r * .1f, r * .5f,
            intArrayOf(Argb.of(150, 110, 150, 255), Argb.of(60, 100, 110, 255), 0), floatArrayOf(0f, .5f, 1f))
        beam = Fill.Linear(0f, cy - r, 0f, padY + r * .02f,
            intArrayOf(Argb.of(0, 170, 235, 255), Argb.of(150, 170, 235, 255), Argb.of(255, 220, 250, 255)), floatArrayOf(0f, .55f, 1f))
        padGlow = Fill.Radial(cx, padY, r * .8f,
            intArrayOf(Argb.of(255, 230, 252, 255), Argb.of(245, 110, 220, 255), Argb.of(200, 30, 130, 245), Argb.of(0, 20, 80, 200)),
            floatArrayOf(0f, .18f, .55f, 1f))
        padFace = Fill.Radial(cx, padY + r * .02f, r * 1.0f,
            intArrayOf(Argb.rgb(40, 120, 230), Argb.rgb(14, 42, 104), Argb.rgb(10, 24, 62)), floatArrayOf(0f, .6f, 1f))
        baseFace = Fill.Linear(cx - r * 1.45f, 0f, cx + r * 1.45f, 0f,
            intArrayOf(Argb.rgb(14, 34, 80), Argb.rgb(6, 12, 34), Argb.rgb(7, 12, 36), Argb.rgb(30, 22, 74)))
        wall = Fill.Linear(cx - r * 1.45f, 0f, cx + r * 1.45f, 0f,
            intArrayOf(Argb.rgb(14, 34, 76), Argb.rgb(5, 10, 28), Argb.rgb(3, 7, 22), Argb.rgb(6, 9, 26), Argb.rgb(34, 20, 72)),
            floatArrayOf(0f, .25f, .5f, .75f, 1f))
        rimLight = Fill.Linear(cx - r * 1.55f, 0f, cx + r * 1.55f, 0f,
            intArrayOf(Argb.rgb(60, 205, 255), Argb.rgb(70, 160, 255), Argb.rgb(120, 140, 255), Argb.rgb(210, 100, 240)),
            floatArrayOf(0f, .35f, .65f, 1f))
        floorGlow = Fill.Radial(cx, flangeTopY + r * .02f, r * 2.2f,
            intArrayOf(Argb.of(90, 40, 120, 240), Argb.of(35, 30, 70, 180), 0), floatArrayOf(0f, .45f, 1f))
        padCore = Fill.Radial(cx, padY, r * .32f, intArrayOf(Argb.of(255, 250, 255, 255), Argb.of(0, 160, 230, 255)))
        contactGlow = Fill.Radial(cx, padY - r * .05f, r * .78f, intArrayOf(Argb.of(235, 225, 250, 255), Argb.of(150, 90, 200, 255),
            Argb.of(0, 40, 120, 255)), floatArrayOf(0f, .35f, 1f))
        orbitFills = orbits.map { o -> Fill.Linear(cx - r * o.a, cy, cx + r * o.a, cy, o.colors) }
        floorFills = (0..3).map { i ->
            val rx = r * (1.72f + i * .2f)
            val color = if (i % 2 == 0) Argb.rgb(60, 180, 255) else Argb.rgb(140, 110, 255)
            Fill.Linear(cx - rx, 0f, cx + rx, 0f, intArrayOf(color, Argb.rgb(40, 90, 200), Argb.rgb(190, 90, 240)))
        }
        brandFill = Fill.Solid(Argb.rgb(222, 232, 248))
        taglineFill = Fill.Solid(Argb.rgb(178, 194, 222))
    }

    fun draw(p: ContinuumPainter, f: Frame) {
        if (r <= 0f) return
        p.fillRect(0f, 0f, w, h, backdrop)
        drawRays(p, f)
        drawStars(p, f)
        p.fillOval(cx - r * 2.2f, flangeTopY - r * .3f, cx + r * 2.2f, flangeTopY + r * .35f, floorGlow)
        drawFloor(p, f)
        drawPedestal(p, f)
        drawOrbits(p, f, front = false)
        drawSphere(p, f)
        drawOrbits(p, f, front = true)
        val contactY = padY - r * .05f
        p.fillOval(cx - r * .78f, contactY - r * .14f, cx + r * .78f, contactY + r * .14f, contactGlow, .85f + f.energy * .15f)
        drawSparkles(p, f)
        if (showBrand) drawBrand(p)
    }

    private fun drawRays(p: ContinuumPainter, f: Frame) {
        for (i in 0 until 9) {
            val x = cx + (i - 4) * r * .19f + sin(i * 1.7f) * r * .05f
            val top = cy - r * (1.05f + (i % 3) * .12f)
            val alpha = (.18f + .1f * sin(f.phase * .4f + i)) * (if (i % 2 == 0) 1f else .6f)
            p.drawLine(x, top, x, cy - r * .35f, .55f * scale, rays, alpha)
        }
    }

    private fun drawStars(p: ContinuumPainter, f: Frame) {
        for ((i, s) in stars.withIndex()) {
            val x = s.u * w
            val y = s.v * h * .86f
            val dx = (x - cx) / r; val dy = (y - cy) / r
            if (dx * dx + dy * dy < 1.15f) continue
            val twinkle = .55f + .45f * sin(f.phase * (.6f + (i % 5) * .13f) + i)
            val color = when (s.tint) { 0 -> Argb.rgb(150, 210, 255); 1 -> Argb.rgb(110, 160, 255); else -> Argb.rgb(205, 160, 255) }
            p.fillCircle(x, y, s.size * .6f, Fill.Solid(color), twinkle * .9f)
            if (s.flare) {
                glow(p, x, y, 2.8f, Argb.withAlpha(color, .5f), twinkle)
                p.drawLine(x - 3.6f, y, x + 3.6f, y, .35f, Fill.Solid(color), twinkle * .75f)
                p.drawLine(x, y - 3.6f, x, y + 3.6f, .35f, Fill.Solid(color), twinkle * .75f)
            }
        }
    }

    private fun drawFloor(p: ContinuumPainter, f: Frame) {
        val y = floorY
        for (i in 0..3) {
            val rx = r * (1.72f + i * .2f); val ry = r * (.25f + i * .045f) * geometry.flat
            val color = if (i % 2 == 0) Argb.rgb(60, 180, 255) else Argb.rgb(140, 110, 255)
            p.strokeOval(cx - rx, y - ry, cx + rx, y + ry, .7f * scale, floorFills[i], .5f - i * .1f)
        }
        val rx = r * 1.72f; val ry = r * .25f * geometry.flat
        p.strokeArc(cx - rx, y - ry, cx + rx, y + ry, 15f + f.phase * 12f, 60f, 1.1f * scale,
            Fill.Solid(Argb.rgb(150, 230, 255)), .55f)
    }

    private fun drawPedestal(p: ContinuumPainter, f: Frame) {
        val glow = .8f + f.energy * .2f
        // Flange (widest, bottom)
        drawTier(p, rx = r * 1.56f, ry = r * .2f * geometry.flat, topY = flangeTopY, height = r * .06f, face = baseFace, rimAlpha = .8f * glow)
        // Base cylinder
        drawTier(p, rx = r * 1.43f, ry = r * .17f * geometry.flat, topY = baseTopY, height = r * geometry.wall, face = baseFace, rimAlpha = .95f * glow)
        // Raised platform with the glowing projection surface
        drawTier(p, rx = r * .97f, ry = r * .125f * geometry.flat, topY = padY, height = r * .07f, face = padFace, rimAlpha = 1f * glow)
        val topY = padY
        p.fillOval(cx - r * .86f, topY - r * .118f, cx + r * .86f, topY + r * .118f, padGlow, 1f)
        p.fillOval(cx - r * .32f, topY - r * .05f, cx + r * .32f, topY + r * .05f,
            padCore, glow)
        for (i in 1..4) {
            val rx = r * (.9f - i * .16f); val ry = r * (.112f - i * .02f)
            p.strokeOval(cx - rx, topY - ry, cx + rx, topY + ry, .7f * scale, Fill.Solid(Argb.rgb(200, 248, 255)), (.75f - i * .12f) * glow)
        }
    }

    private fun drawTier(p: ContinuumPainter, rx: Float, ry: Float, topY: Float, height: Float, face: Fill, rimAlpha: Float) {
        val bottomY = topY + height
        // side wall: rectangle between the two ellipse centres plus the lower half ellipse
        p.fillRect(cx - rx, topY, cx + rx, bottomY, wall)
        p.fillOval(cx - rx, bottomY - ry, cx + rx, bottomY + ry, wall)
        p.strokeArc(cx - rx, bottomY - ry, cx + rx, bottomY + ry, 5f, 170f, 1.0f * scale, rimLight, rimAlpha * .75f)
        // top face
        p.fillOval(cx - rx, topY - ry, cx + rx, topY + ry, face)
        p.strokeOval(cx - rx, topY - ry, cx + rx, topY + ry, 2.4f * scale, rimLight, rimAlpha * .18f)
        p.strokeOval(cx - rx, topY - ry, cx + rx, topY + ry, .9f * scale, rimLight, rimAlpha)
        p.strokeArc(cx - rx, topY - ry, cx + rx, topY + ry, 20f, 140f, 1.4f * scale, Fill.Solid(Argb.rgb(170, 240, 255)), rimAlpha * .8f)
    }

    private class Orbit(val tilt: Float, val a: Float, val b: Float, val speed: Float, val colors: IntArray, val width: Float)

    private val orbits = listOf(
        Orbit(-11f, 1.78f, .36f, 1f, intArrayOf(Argb.rgb(80, 225, 255), Argb.rgb(120, 170, 255), Argb.rgb(210, 110, 255)), 1.5f),
        Orbit(22f, 1.5f, .5f, -.8f, intArrayOf(Argb.rgb(120, 140, 255), Argb.rgb(190, 110, 255), Argb.rgb(255, 120, 210)), 1.25f),
        Orbit(-38f, 1.34f, .44f, .65f, intArrayOf(Argb.rgb(230, 100, 220), Argb.rgb(150, 120, 255), Argb.rgb(80, 210, 255)), 1.05f),
        Orbit(74f, 1.2f, .31f, -.55f, intArrayOf(Argb.rgb(80, 215, 255), Argb.rgb(150, 150, 255), Argb.rgb(90, 225, 255)), .95f),
        Orbit(7f, 1.28f, .62f, .45f, intArrayOf(Argb.rgb(90, 230, 255), Argb.rgb(150, 160, 255), Argb.rgb(240, 110, 220)), 1.1f)
    )

    private fun drawOrbits(p: ContinuumPainter, f: Frame, front: Boolean) {
        for ((i, o) in orbits.withIndex()) {
            val a = r * o.a; val b = r * o.b
            val wobble = sin(f.phase * .35f + i * 1.7f) * 2f
            p.save()
            p.rotate(o.tilt + wobble, cx, cy)
            val fill = orbitFills[i]
            val start = if (front) 0f else 180f
            val k = if (front) 1f else .55f
            val wd = o.width * scale
            p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, 180f, wd * 6.5f, fill, .08f * k)
            p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, 180f, wd * 3.2f, fill, .2f * k)
            p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, 180f, wd * 1.5f, fill, .85f * k)
            p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, 180f, wd * .6f, Fill.Solid(Argb.rgb(232, 248, 255)), .85f * k)
            val travel = ((f.phase * 24f * o.speed + i * 77f) % 360f + 360f) % 360f
            val local = if (front) travel % 180f else 180f + travel % 180f
            p.strokeArc(cx - a, cy - b, cx + a, cy + b, local, 24f, wd * 1.6f, Fill.Solid(Argb.rgb(235, 250, 255)), .9f * k)
            p.restore()
        }
    }

    private fun drawSphere(p: ContinuumPainter, f: Frame) {
        val breath = 1f + sin(f.phase * 1.4f) * (.006f + f.energy * .008f) + f.audio * .02f
        p.save()
        p.scale(breath, breath, cx, cy)
        p.fillCircle(cx, cy, r * 1.3f, bloom)
        p.fillCircle(cx, cy, r, glass)
        p.fillCircle(cx, cy, r, glassCyan)
        p.fillCircle(cx, cy, r, glassViolet)
        drawDust(p, f)
        drawNetwork(p, f)
        drawInnerOrbits(p, f)
        // centre beam from the pad through the core
        // fan of light rays rising from the projection pad
        for (i in 0 until 15) {
            val t = (i - 7) / 7f
            val x0 = cx + t * r * .5f
            val x1 = cx + t * r * .16f
            val alpha = (.22f - abs(t) * .1f) * (.8f + .2f * sin(f.phase * .9f + i))
            p.drawLine(x0, padY - r * .05f, x1, cy + r * .12f, (if (i % 3 == 0) 1.1f else .6f) * scale, beam, alpha)
        }
        p.drawLine(cx, cy - r * .98f, cx, padY, 4.5f * scale, beam, .25f + f.energy * .1f)
        p.drawLine(cx, cy - r * .98f, cx, padY, 1.1f * scale, beam, .95f)
        drawStateEffects(p, f)
        // inner sphere with the infinity mark
        p.fillCircle(cx, cy + r * .05f, r * .62f, infinityGlow, .5f + f.energy * .3f)
        p.fillCircle(cx, cy, r * .4f, innerGlass)
        p.strokeCircle(cx, cy, r * .4f, r * .06f, innerRim, .18f)
        p.strokeCircle(cx, cy, r * .4f, r * .02f, innerRim, .95f)
        p.fillOval(cx - r * .3f, cy - r * .36f, cx + r * .02f, cy - r * .12f, innerSpecular)
        drawInfinity(p, f)
        // rim: bloom ring, crisp edge, highlight arc and specular
        p.strokeCircle(cx, cy, r * 1.01f, r * .16f, rim, .13f)
        p.strokeCircle(cx, cy, r, r * .08f, rim, .35f)
        p.strokeCircle(cx, cy, r, r * .036f, rim, .95f)
        p.strokeCircle(cx, cy, r * .993f, r * .01f, Fill.Solid(Argb.rgb(215, 248, 255)), .8f)
        p.strokeArc(cx - r * .955f, cy - r * .955f, cx + r * .955f, cy + r * .955f, 196f, 72f, r * .022f,
            Fill.Solid(Argb.rgb(230, 250, 255)), .75f)
        p.fillOval(cx - r * .82f, cy - r * .9f, cx - r * .05f, cy - r * .32f, specular)
        p.restore()
    }

    private fun drawDust(p: ContinuumPainter, f: Frame) {
        val turn = f.phase * .05f
        for ((i, d) in dust.withIndex()) {
            val angle = d.angle + turn * (if (i % 2 == 0) 1f else -.6f)
            val x = cx + cos(angle) * d.radius * r
            val y = cy + sin(angle) * d.radius * r
            val twinkle = .5f + .5f * sin(f.phase * (.9f + (i % 7) * .11f) + i * 1.3f)
            val color = when {
                d.warm -> if (i % 12 == 0) Argb.rgb(255, 150, 90) else Argb.rgb(255, 110, 170)
                i % 5 == 0 -> Argb.rgb(170, 140, 255)
                else -> Argb.rgb(150, 220, 255)
            }
            p.fillCircle(x, y, d.size * scale, Fill.Solid(color), .5f + twinkle * .5f)
            if (i % 17 == 0) {
                val g = 3.4f * scale
                glow(p, x, y, g, Argb.withAlpha(color, .5f), twinkle)
            }
        }
    }

    /** Great circles of a slowly turning sphere: the luminous interior lattice. */
    private fun drawNetwork(p: ContinuumPainter, f: Frame) {
        val turn = f.phase * .16f
        val density = .7f + f.weight(Mode.THINKING) * .3f + f.weight(Mode.LEARNING) * .2f
        val circles = 18
        for (k in 0 until circles) {
            val inclination = (k * 31f + 9f) * (PI.toFloat() / 180f)
            val node = k * 2.39996f + turn
            val nx = sin(inclination) * cos(node); val ny = cos(inclination); val nz = sin(inclination) * sin(node)
            var ux = -nz; val uy = 0f; var uz = nx
            var len = sqrt(ux * ux + uz * uz)
            if (len < 1e-3f) { ux = 1f; uz = 0f; len = 1f }
            ux /= len; uz /= len
            val vx = ny * uz - nz * uy; val vy = nz * ux - nx * uz; val vz = nx * uy - ny * ux
            val color = when (k % 5) { 0, 3 -> Argb.rgb(120, 225, 255); 1 -> Argb.rgb(90, 150, 255); 2 -> Argb.rgb(180, 130, 255); else -> Argb.rgb(240, 120, 215) }
            for (half in 0..1) {
                path.reset()
                var started = false
                var done = false
                for (step in 0..48) {
                    if (done) break
                    val t = step * (PI.toFloat() * 2f / 48f)
                    val x = cos(t) * ux + sin(t) * vx; val y = cos(t) * uy + sin(t) * vy; val z = cos(t) * uz + sin(t) * vz
                    val inHalf = (z >= 0f) == (half == 0)
                    if (inHalf) {
                        val sx = cx + x * r * .965f; val sy = cy + y * r * .965f
                        if (!started) { path.moveTo(sx, sy); started = true } else path.lineTo(sx, sy)
                    } else if (started) done = true
                }
                if (path.isEmpty) continue
                if (half == 0) {
                    p.strokePath(path, 3.4f * scale, Fill.Solid(color), .13f * density)
                    p.strokePath(path, 1.3f * scale, Fill.Solid(color), .45f * density)
                    p.strokePath(path, .55f * scale, Fill.Solid(Argb.mix(color, Argb.rgb(255, 255, 255), .55f)), .95f * density)
                } else {
                    p.strokePath(path, .6f * scale, Fill.Solid(color), .38f * density)
                }
            }
        }
        // Glowing nodes on a Fibonacci sphere
        val n = 30
        for (i in 0 until n) {
            val y = 1f - (i + .5f) * 2f / n
            val radius = sqrt(1f - y * y)
            val angle = i * 2.39996f + turn
            val x = cos(angle) * radius; val z = sin(angle) * radius
            val sx = cx + x * r * .9f; val sy = cy + y * r * .9f
            val depth = (z + 1f) * .5f
            val color = if (i % 5 == 0) Argb.rgb(255, 140, 220) else Argb.rgb(190, 240, 255)
            p.fillCircle(sx, sy, (.5f + depth * .8f) * scale, Fill.Solid(color), (.2f + depth * .8f) * density)
            if (depth > .65f && i % 2 == 0) {
                val g = 3.6f * scale
                glow(p, sx, sy, g, Argb.withAlpha(color, .6f), density)
            }
        }
    }

    private val innerOrbits = floatArrayOf(-28f, .86f, .34f, 34f, .8f, .4f, 72f, .74f, .26f, -64f, .7f, .3f, 8f, .9f, .22f)

    /** Bright elliptical light paths inside the glass, as in the reference interior. */
    private fun drawInnerOrbits(p: ContinuumPainter, f: Frame) {
        for (i in 0 until innerOrbits.size / 3) {
            val tilt = innerOrbits[i * 3] + sin(f.phase * .25f + i) * 3f
            val a = r * innerOrbits[i * 3 + 1]; val b = r * innerOrbits[i * 3 + 2]
            val color = if (i % 2 == 0) Argb.rgb(110, 225, 255) else Argb.rgb(190, 130, 255)
            p.save(); p.rotate(tilt, cx, cy)
            p.strokeOval(cx - a, cy - b, cx + a, cy + b, 3.4f * scale, Fill.Solid(color), .12f)
            p.strokeOval(cx - a, cy - b, cx + a, cy + b, .9f * scale, Fill.Solid(color), .6f)
            p.strokeArc(cx - a, cy - b, cx + a, cy + b, 10f + i * 40f + f.phase * 10f, 60f, .7f * scale,
                Fill.Solid(Argb.rgb(235, 250, 255)), .85f)
            p.restore()
        }
    }

    private fun drawInfinity(p: ContinuumPainter, f: Frame) {
        val a = r * .29f; val b = r * .135f
        val oy = cy + r * .02f
        path.reset()
        for (i in 0..96) {
            val t = i * (2f * PI.toFloat() / 96f)
            val x = cx + a * sin(t)
            val y = oy + b * sin(t) * cos(t) * 2f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val pulse = .85f + f.energy * .15f + f.audio * .2f
        p.strokePath(path, r * .16f, infinity, .16f * pulse)
        p.strokePath(path, r * .075f, infinity, 1f)
        p.strokePath(path, r * .032f, Fill.Solid(Argb.rgb(70, 80, 190)), .55f)
        p.strokePath(path, r * .013f, Fill.Solid(Argb.rgb(240, 252, 255)), .8f * pulse)
    }

    private fun drawStateEffects(p: ContinuumPainter, f: Frame) {
        val cyan = Argb.rgb(90, 225, 255)
        val violet = Argb.rgb(160, 120, 255)
        val listening = f.weight(Mode.LISTENING)
        if (listening > .02f) for (i in 0..2) {
            val progress = (f.phase * .5f + i / 3f) % 1f
            val radius = r * (.45f + (1f - progress) * .5f)
            p.strokeCircle(cx, cy, radius, 1.1f * scale, Fill.Solid(cyan), listening * progress * .7f)
        }
        val thinking = f.weight(Mode.THINKING)
        if (thinking > .02f) {
            p.save(); p.rotate(f.phase * 44f, cx, cy)
            for (i in 0..2) {
                val radius = r * (.5f + i * .12f)
                p.strokeArc(cx - radius, cy - radius, cx + radius, cy + radius, i * 110f, 78f, 1.7f * scale,
                    Fill.Solid(if (i == 1) cyan else violet), thinking * .85f)
            }
            p.restore()
        }
        val speaking = f.weight(Mode.SPEAKING)
        if (speaking > .02f && f.audio > 0f) {
            // Only real PCM energy moves these lines; without audio data they stay hidden.
            for (line in 0..2) {
                path.reset()
                for (i in 0..64) {
                    val x = cx - r * .62f + i * (r * 1.24f / 64f)
                    val taper = sin(i * PI.toFloat() / 64f)
                    val wave = sin(i * .27f - f.phase * 8f + line * .8f) * taper
                    val y = cy + r * .58f + (line - 1) * 4f * scale + wave * f.audio * r * (.12f - line * .02f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                p.strokePath(path, .9f * scale, Fill.Solid(if (line == 1) Argb.rgb(204, 248, 255) else cyan), speaking * (.4f + f.audio * .6f))
            }
        }
        val research = f.weight(Mode.RESEARCH)
        if (research > .02f) {
            p.save(); p.rotate(f.phase * 34f, cx, cy)
            val rr = r * .78f
            p.strokeArc(cx - rr, cy - rr, cx + rr, cy + rr, -65f, 105f, 1.8f * scale, Fill.Solid(cyan), research * .85f)
            p.drawLine(cx, cy, cx + rr * .7f, cy - rr * .7f, .8f * scale, Fill.Solid(cyan), research * .5f)
            p.restore()
        }
        val learning = f.weight(Mode.LEARNING)
        if (learning > .02f) for (i in 0 until 12) {
            val progress = (f.phase * .24f + i / 12f) % 1f
            val x = cx + sin(i * 2.4f + progress * 3f) * r * (.55f - progress * .35f)
            val y = cy + r * 1.05f - progress * r * 1.05f
            p.fillCircle(x, y, (1.3f + progress * .6f) * scale, Fill.Solid(if (i % 2 == 0) violet else cyan),
                learning * sin(progress * PI.toFloat()) * .9f)
        }
    }

    private fun drawSparkles(p: ContinuumPainter, f: Frame) {
        val spots = floatArrayOf(-0.93f, -0.3f, 0.97f, -0.2f, -0.55f, 0.84f, 0.62f, 0.8f, 0.02f, -1.0f, 0.38f, -0.93f)
        for (i in 0 until spots.size / 2) {
            val x = cx + spots[i * 2] * r; val y = cy + spots[i * 2 + 1] * r
            val pulse = .6f + .4f * sin(f.phase * (1.1f + i * .2f) + i * 1.3f)
            val size = r * (.03f + (i % 2) * .018f) * (1f + f.energy * .4f)
            glow(p, x, y, size * 3.6f, Argb.of(170, 175, 232, 255), pulse)
            p.drawLine(x - size * 3.2f, y, x + size * 3.2f, y, max(.4f, size * .16f), Fill.Solid(Argb.rgb(235, 250, 255)), pulse * .8f)
            p.drawLine(x, y - size * 3.2f, x, y + size * 3.2f, max(.4f, size * .16f), Fill.Solid(Argb.rgb(235, 250, 255)), pulse * .8f)
            p.fillCircle(x, y, size * .6f, Fill.Solid(Argb.rgb(248, 253, 255)), pulse)
        }
    }

    private fun drawBrand(p: ContinuumPainter) {
        val brand = ContinuumTheme.display(400, 11.83f * scale, 11.83f * .456f * scale)
        val tagline = ContinuumTheme.display(500, 7.76f * scale, 7.76f * .476f * scale)
        // letter spacing also trails the last glyph; shift by half a space to keep the word optically centred
        p.drawText("CONTINUUM", cx + brand.letterSpacing / 2f, cy + r * geometry.brand, brand, brandFill, TextAlign.CENTER)
        p.drawText("INTELLIGENCE  LIVES  ON", cx + tagline.letterSpacing / 2f, cy + r * geometry.tagline, tagline, taglineFill, TextAlign.CENTER)
        val marks = geometry.marks
        if (pageCount > 1 && marks != null) {
            val y = cy + r * marks
            val active = 25f * scale; val idle = 10f * scale; val gap = 4.5f * scale; val thick = 2.3f * scale
            val total = active + (pageCount - 1) * idle + (pageCount - 1) * gap
            var x = cx - total / 2f
            for (i in 0 until pageCount) {
                val len = if (i == pageIndex) active else idle
                p.fillRoundRect(x, y - thick / 2f, x + len, y + thick / 2f, thick / 2f,
                    Fill.Solid(if (i == pageIndex) Argb.rgb(150, 220, 255) else Argb.rgb(60, 80, 120)))
                x += len + gap
            }
        }
    }

    private val unitGlows = HashMap<Int, Fill>()

    /** Soft round glow: one cached unit-radius gradient per colour, positioned by transform (no per-frame allocation). */
    private fun glow(p: ContinuumPainter, x: Float, y: Float, radius: Float, color: Int, alpha: Float) {
        if (alpha <= 0f || radius <= 0f) return
        val fill = unitGlows.getOrPut(color) { Fill.Radial(0f, 0f, 1f, intArrayOf(color, Argb.withAlpha(color, 0f))) }
        p.save(); p.translate(x, y); p.scale(radius, radius)
        p.fillCircle(0f, 0f, 1f, fill, alpha)
        p.restore()
    }

    val sphereCenterY: Float get() = cy
    val sphereRadius: Float get() = r
}
