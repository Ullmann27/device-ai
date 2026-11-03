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
    class Frame(var phase: Float, var energy: Float, var audio: Float, val weights: FloatArray) {
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
     * Vertical composition in units of the composition radius, measured from the two references:
     * phone (412 x 307 dp box) and Fold (410 x 291 dp box, compressed pedestal, no page marks).
     * [sphere] is the glass sphere's radius over the composition radius (rim measured at the brightest point per ray:
     * phone 99.3 dp of 100.9, Fold 100.7 dp of 97.0); [haloNodes], [aura] and [bloom] set the network and glow
     * around it (radial colour profiles of the references: the Fold glows further out than the phone).
     */
    data class Geometry(val centerY: Float, val radiusOfWidth: Float, val radiusOfHeight: Float, val pad: Float,
                        val wall: Float, val brand: Float, val tagline: Float, val marks: Float?, val flat: Float = 1f,
                        val sphere: Float = 1f, val haloNodes: Int = 28, val aura: Float = .25f, val bloom: Float = .6f) {
        companion object {
            val PHONE = Geometry(centerY = .381f, radiusOfWidth = .2455f, radiusOfHeight = .329f, pad = 1.11f,
                wall = .33f, brand = 1.545f, tagline = 1.685f, marks = 1.795f)
            val FOLD = Geometry(centerY = .495f, radiusOfWidth = .2366f, radiusOfHeight = .3334f, pad = .99f,
                wall = .1f, brand = 1.27f, tagline = 1.42f, marks = null, flat = .62f, sphere = 1.04f, haloNodes = 72, aura = .35f, bloom = 1f)
            // Tall unfolded inner screen: reference orb ~274 design dp diameter,
            // drawn behind the enlarged action buttons in its own, wider stage.
            // Landscape and cover-display geometry remain byte-for-byte unchanged.
            val FOLD_PORTRAIT = Geometry(centerY = .42f, radiusOfWidth = .375f, radiusOfHeight = .37f,
                pad = .99f, wall = .1f, brand = 1.27f, tagline = 1.42f, marks = null,
                flat = .62f, sphere = 1.04f, haloNodes = 72, aura = .35f, bloom = 1f)
        }
    }

    var geometry: Geometry = Geometry.PHONE
        set(value) {
            if (value.haloNodes != field.haloNodes) plexus = ContinuumHologramPlexus(value.haloNodes)
            field = value; w = 0f; h = 0f
        }
    private var plexus = ContinuumHologramPlexus(Geometry.PHONE.haloNodes)

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
    /** Radius of the glass sphere and its stroke scale (composition radius [r] places pedestal and text). */
    private var rs = 0f
    private var sphereScale = 1f
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
    private lateinit var coreHalo: Fill
    private lateinit var aura: Fill
    private lateinit var auraMagenta: Fill

    private val path = VectorPath()
    private val stars = Array(70) { Star(it) }

    /** Network colours by depth bucket: violet at the back, blue in the middle, cyan-white at the front. */
    private val depthFills = Array(DEPTH_BUCKETS) { i ->
        val t = i / (DEPTH_BUCKETS - 1f)
        Fill.Solid(if (t < .5f) Argb.mix(Argb.rgb(96, 78, 214), Argb.rgb(58, 138, 255), t * 2f)
        else Argb.mix(Argb.rgb(58, 138, 255), Argb.rgb(178, 236, 255), t * 2f - 1f))
    }
    private val nodeCool = Fill.Solid(Argb.rgb(196, 242, 255))
    private val nodeViolet = Fill.Solid(Argb.rgb(184, 150, 255))
    private val nodePink = Fill.Solid(Argb.rgb(255, 140, 220))
    private val dustFills = arrayOf(Fill.Solid(Argb.rgb(150, 220, 255)), Fill.Solid(Argb.rgb(170, 140, 255)),
        Fill.Solid(Argb.rgb(255, 120, 170)), Fill.Solid(Argb.rgb(255, 160, 96)))
    private val pulseFill = Fill.Solid(Argb.rgb(236, 250, 255))
    private val haloFill = Fill.Solid(Argb.rgb(110, 170, 255))
    private val accentFill = Fill.Solid(Argb.rgb(236, 112, 226))
    private val orbitCore = Fill.Solid(Argb.rgb(232, 248, 255))
    private val orbitSpark = Fill.Solid(Argb.rgb(235, 250, 255))

    private class Star(i: Int) {
        val u = (i * 0.618034f + .137f) % 1f
        val v = (i * 0.754877f + .29f) % 1f
        val size = if (i % 9 == 0) 1.1f else if (i % 4 == 0) .7f else .42f
        val tint = i % 3
        val flare = i % 11 == 0
    }

    fun layout(width: Float, height: Float) {
        if (width == w && height == h) return
        w = width; h = height
        r = min(width * geometry.radiusOfWidth, height * geometry.radiusOfHeight)
        cx = width / 2f
        cy = height * geometry.centerY
        scale = r / 101f
        rs = r * geometry.sphere
        sphereScale = rs / 101f
        backdrop = Fill.Radial(cx, cy + r * .15f, r * 2.1f,
            intArrayOf(Argb.of(175, 24, 70, 175), Argb.of(110, 14, 46, 128), Argb.of(40, 8, 24, 70), 0),
            floatArrayOf(0f, .36f, .68f, 1f))
        rays = Fill.Linear(0f, cy - r * 1.25f, 0f, cy - r * .2f,
            intArrayOf(0, Argb.of(90, 80, 170, 255), Argb.of(0, 80, 170, 255)), floatArrayOf(0f, .55f, 1f))
        // Sphere colours follow the radial profile of the references (saturated blue, little red): a bright cyan-blue
        // shell around the core at 0.4-0.6 r, a darker band at 0.6-0.8 r, a violet-white rim and a blue aura outside.
        bloom = Fill.Radial(cx, cy, rs * 1.34f,
            intArrayOf(0, 0, Argb.of(190, 70, 150, 255), Argb.of(105, 80, 115, 255), Argb.of(36, 90, 90, 255), 0),
            floatArrayOf(0f, .68f, .75f, .82f, .91f, 1f))
        // two-sided aura as in the references: teal-cyan light on the left, magenta on the right
        aura = Fill.Radial(cx - rs * .3f, cy, rs * 1.7f,
            intArrayOf(0, 0, Argb.of(150, 18, 120, 225), Argb.of(80, 16, 92, 190), 0), floatArrayOf(0f, .45f, .55f, .75f, 1f))
        auraMagenta = Fill.Radial(cx + rs * .35f, cy - rs * .05f, rs * 1.5f,
            intArrayOf(0, 0, Argb.of(120, 130, 50, 215), Argb.of(50, 100, 40, 175), 0), floatArrayOf(0f, .5f, .6f, .78f, 1f))
        glass = Fill.Radial(cx, cy + rs * .08f, rs,
            intArrayOf(Argb.of(244, 12, 26, 104), Argb.of(246, 8, 20, 84), Argb.of(248, 10, 28, 102), Argb.of(250, 28, 62, 180),
                Argb.of(252, 58, 96, 240)),
            floatArrayOf(0f, .5f, .78f, .92f, 1f))
        glassCyan = Fill.Radial(cx - rs * .66f, cy + rs * .1f, rs * .8f,
            intArrayOf(Argb.of(80, 30, 190, 255), Argb.of(26, 30, 160, 255), 0), floatArrayOf(0f, .55f, 1f))
        glassViolet = Fill.Radial(cx + rs * .6f, cy - rs * .1f, rs * .85f,
            intArrayOf(Argb.of(110, 140, 60, 240), Argb.of(36, 110, 50, 225), 0), floatArrayOf(0f, .55f, 1f))
        coreHalo = Fill.Radial(cx, cy, rs * .68f,
            intArrayOf(0, 0, Argb.of(205, 40, 132, 255), Argb.of(110, 40, 100, 245), 0), floatArrayOf(0f, .55f, .68f, .82f, 1f))
        rim = Fill.Sweep(cx, cy, intArrayOf(Argb.rgb(250, 96, 222), Argb.rgb(150, 112, 255), Argb.rgb(70, 170, 255),
            Argb.rgb(60, 222, 255), Argb.rgb(150, 236, 255), Argb.rgb(226, 242, 255), Argb.rgb(200, 150, 255), Argb.rgb(250, 96, 222)),
            floatArrayOf(0f, .16f, .32f, .46f, .62f, .74f, .87f, 1f))
        innerGlass = Fill.Radial(cx - rs * .08f, cy - rs * .1f, rs * .44f,
            intArrayOf(Argb.of(205, 34, 48, 176), Argb.of(195, 22, 30, 124), Argb.of(200, 36, 62, 198), Argb.of(230, 86, 146, 255)),
            floatArrayOf(0f, .55f, .88f, 1f))
        innerRim = Fill.Sweep(cx, cy, intArrayOf(Argb.rgb(170, 110, 255), Argb.rgb(90, 160, 255), Argb.rgb(120, 225, 255),
            Argb.rgb(205, 235, 255), Argb.rgb(200, 110, 255), Argb.rgb(170, 110, 255)), floatArrayOf(0f, .25f, .5f, .7f, .85f, 1f))
        innerSpecular = Fill.Radial(cx - rs * .14f, cy - rs * .22f, rs * .2f,
            intArrayOf(Argb.of(130, 200, 225, 255), Argb.of(26, 150, 185, 255), 0), floatArrayOf(0f, .5f, 1f))
        specular = Fill.Radial(cx - rs * .45f, cy - rs * .55f, rs * .5f,
            intArrayOf(Argb.of(110, 190, 230, 255), Argb.of(26, 120, 190, 255), 0), floatArrayOf(0f, .45f, 1f))
        infinity = Fill.Linear(cx - rs * .32f, cy, cx + rs * .32f, cy,
            intArrayOf(Argb.rgb(70, 222, 255), Argb.rgb(120, 146, 255), Argb.rgb(214, 100, 255)))
        infinityGlow = Fill.Radial(cx, cy + rs * .1f, rs * .5f,
            intArrayOf(Argb.of(120, 80, 120, 255), Argb.of(50, 80, 90, 245), 0), floatArrayOf(0f, .5f, 1f))
        beam = Fill.Linear(0f, cy - rs, 0f, padY + r * .02f,
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
        orbitFills = orbits.map { o -> Fill.Linear(cx - rs * o.a, cy, cx + rs * o.a, cy, o.colors) }
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
        if (geometry == Geometry.FOLD_PORTRAIT) {
            // HomeView permits portrait decoration beyond the narrow hero slot.
            // Paint the complete radial support so its alpha reaches zero before
            // the edge; the old slot-sized rectangle left a visible hard seam.
            val reach = r * 2.1f
            val center = cy + r * .15f
            p.fillRect(cx - reach, center - reach, cx + reach, center + reach, backdrop)
        } else p.fillRect(0f, 0f, w, h, backdrop)
        drawRays(p, f)
        drawStars(p, f)
        p.fillCircle(cx - rs * .3f, cy, rs * 1.7f, aura, geometry.aura)
        p.fillCircle(cx + rs * .35f, cy - rs * .05f, rs * 1.5f, auraMagenta, geometry.aura)
        p.fillOval(cx - r * 2.2f, flangeTopY - r * .3f, cx + r * 2.2f, flangeTopY + r * .35f, floorGlow)
        drawFloor(p, f)
        drawPedestal(p, f)
        val lift = ContinuumOrbitalRing.lift(f.phase, rs)
        p.save()
        p.translate(0f, lift)
        // One rotation for the whole network: slow turn plus a gentle nod, both from the animation phase.
        // A visible but unhurried, physically consistent rotation of the *same* 3-D nodes;
        // no new samples, faux device load or randomized per-frame particle locations.
        plexus.project(
            .4f * .16f + (f.phase - .4f) * .52f,
            .3f + .05f * sin(.4f * .21f) +
                .08f * (sin(f.phase * .28f) - sin(.4f * .28f)),
            cx, cy, rs, f.phase
        )
        // Light layers add up (crossings and dense regions glow); glass, core and text cover.
        p.setAdditive(true)
        drawHalo(p, f)
        drawOrbits(p, f, front = false)
        p.setAdditive(false)
        drawSphere(p, f)
        p.setAdditive(true)
        drawOrbits(p, f, front = true)
        drawSparkles(p, f)
        p.restore()
        val contactY = padY - r * .05f
        val contactBreath = .4f + f.energy * .1f + .065f * (sin(f.phase * .8f) - sin(.4f * .8f))
        p.fillOval(cx - r * .78f, contactY - r * .14f, cx + r * .78f, contactY + r * .14f, contactGlow, (contactBreath + lift / rs * 1.8f).coerceIn(.2f, .65f))
        p.setAdditive(false)
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

    // Ring extents measured on the references: the widest ring reaches about 1.4 sphere radii beside the sphere.
    private val orbits = listOf(
        Orbit(-11f, 1.42f, .34f, 1f, intArrayOf(Argb.rgb(80, 225, 255), Argb.rgb(120, 170, 255), Argb.rgb(210, 110, 255)), 1.5f),
        Orbit(22f, 1.32f, .48f, -.8f, intArrayOf(Argb.rgb(120, 140, 255), Argb.rgb(190, 110, 255), Argb.rgb(255, 120, 210)), 1.25f),
        Orbit(-36f, 1.22f, .42f, .65f, intArrayOf(Argb.rgb(230, 100, 220), Argb.rgb(150, 120, 255), Argb.rgb(80, 210, 255)), 1.05f),
        Orbit(74f, 1.14f, .3f, -.55f, intArrayOf(Argb.rgb(80, 215, 255), Argb.rgb(150, 150, 255), Argb.rgb(90, 225, 255)), .95f),
        Orbit(7f, 1.18f, .6f, .45f, intArrayOf(Argb.rgb(90, 230, 255), Argb.rgb(150, 160, 255), Argb.rgb(240, 110, 220)), 1.1f)
    )

    private val orbitalGeometry = orbits.map { ContinuumOrbitalRing(it.a, it.b, it.tilt) }

    private fun drawOrbits(p: ContinuumPainter, f: Frame, front: Boolean) {
        for ((i, o) in orbits.withIndex()) {
            val ring = orbitalGeometry[i]
            if (!front) ring.project(f.phase, o.speed, cx, cy, rs)
            val points = if (front) ring.front else ring.back
            val count = if (front) ring.frontSize else ring.backSize
            val k = if (front) 1f else .42f
            val wd = o.width * sphereScale
            val fill = orbitFills[i]
            p.strokeSegments(points, count, wd * 6.5f, fill, .05f * k)
            p.strokeSegments(points, count, wd * 3.2f, fill, .12f * k)
            p.strokeSegments(points, count, wd * 1.5f, fill, .5f * k)
            p.strokeSegments(points, count, wd * .6f, orbitCore, .5f * k)
        }
    }

    private fun drawSphere(p: ContinuumPainter, f: Frame) {
        val breath = 1f + sin(f.phase * 1.4f) * (.006f + f.energy * .008f) + f.audio * .02f
        p.save()
        p.scale(breath, breath, cx, cy)
        p.fillCircle(cx, cy, rs * 1.3f, bloom, geometry.bloom)
        p.fillCircle(cx, cy, rs, glass)
        p.fillCircle(cx, cy, rs, glassCyan)
        p.fillCircle(cx, cy, rs, glassViolet)
        p.setAdditive(true)
        p.fillCircle(cx, cy, rs * .68f, coreHalo, .85f + f.energy * .15f)
        drawPlexus(p, f, front = false)
        // fan of light rays rising from the projection pad
        for (i in 0 until 15) {
            val t = (i - 7) / 7f
            val x0 = cx + t * r * .5f
            val x1 = cx + t * rs * .16f
            val alpha = (.22f - abs(t) * .1f) * (.8f + .2f * sin(f.phase * .9f + i))
            p.drawLine(x0, padY - r * .05f - ContinuumOrbitalRing.lift(f.phase, rs), x1, cy + rs * .12f, (if (i % 3 == 0) 1.1f else .6f) * scale, beam, alpha)
        }
        p.drawLine(cx, cy - rs * .98f, cx, padY - ContinuumOrbitalRing.lift(f.phase, rs), 4.5f * scale, beam, .15f + f.energy * .08f)
        p.drawLine(cx, cy - rs * .98f, cx, padY - ContinuumOrbitalRing.lift(f.phase, rs), 1.1f * scale, beam, .5f)
        drawStateEffects(p, f)
        p.setAdditive(false)
        // inner sphere with the infinity mark; it hides the back half of the network
        p.fillCircle(cx, cy + rs * .05f, rs * .62f, infinityGlow, .32f + f.energy * .2f)
        p.fillCircle(cx, cy, rs * .4f, innerGlass)
        p.strokeCircle(cx, cy, rs * .4f, rs * .06f, innerRim, .18f)
        p.strokeCircle(cx, cy, rs * .4f, rs * .02f, innerRim, .95f)
        // The central glass has its own parallax reflection; the brand mark remains legible.
        p.save()
        p.translate(sin(f.phase * .83f) * rs * .08f, cos(f.phase * .61f) * rs * .035f)
        p.fillOval(cx - rs * .3f, cy - rs * .36f, cx + rs * .02f, cy - rs * .12f, innerSpecular)
        p.restore()
        drawEnergyCore(p, f, front = false)
        drawInfinity(p, f)
        drawEnergyCore(p, f, front = true)
        p.setAdditive(true)
        drawPlexus(p, f, front = true)
        // rim: bloom ring, crisp edge, highlight arc and specular
        p.strokeCircle(cx, cy, rs * 1.01f, rs * .16f, rim, .05f)
        p.strokeCircle(cx, cy, rs, rs * .08f, rim, .1f)
        p.strokeCircle(cx, cy, rs, rs * .036f, rim, .3f)
        p.strokeCircle(cx, cy, rs * .993f, rs * .01f, rimEdge, .18f)
        p.strokeArc(cx - rs * .955f, cy - rs * .955f, cx + rs * .955f, cy + rs * .955f, 196f, 72f, rs * .022f, rimHighlight, .4f)
        p.fillOval(cx - rs * .82f, cy - rs * .9f, cx - rs * .05f, cy - rs * .32f, specular)
        p.setAdditive(false)
        p.restore()
    }

    // Reuses the same bounded 3-D orbit buffers as the outer rings; no particle worker or random samples.
    private val coreOrbits = arrayOf(
        ContinuumOrbitalRing(.35f, .17f, -28f),
        ContinuumOrbitalRing(.32f, .12f, 62f)
    )

    private fun drawEnergyCore(p: ContinuumPainter, f: Frame, front: Boolean) {
        val pulse = .78f + .12f * sin(f.phase * 2.1f) + f.energy * .1f
        p.setAdditive(true)
        for (i in coreOrbits.indices) {
            val orbit = coreOrbits[i]
            if (!front) orbit.project(f.phase, if (i == 0) 1.35f else -.95f, cx, cy, rs)
            val points = if (front) orbit.front else orbit.back
            val count = if (front) orbit.frontSize else orbit.backSize
            val opacity = (if (front) .62f else .2f) * pulse
            val fill = if (i == 0) stateCyan else stateViolet
            p.strokeSegments(points, count, 3.2f * sphereScale, fill, opacity * .16f)
            p.strokeSegments(points, count, .75f * sphereScale, fill, opacity)
            // Three ordered packets on each visible half; no telemetry or progress claim.
            for (packet in 0..2) {
                if (count < 4) continue
                val position = (f.phase * (if (i == 0) .22f else .16f) + packet / 3f) % 1f
                val offset = ((count / 4 - 1) * position).toInt() * 4
                p.fillCircle(points[offset], points[offset + 1], 1.15f * sphereScale, orbitSpark, opacity)
            }
        }
        p.setAdditive(false)
    }

    private val rimEdge = Fill.Solid(Argb.rgb(215, 248, 255))
    private val rimHighlight = Fill.Solid(Argb.rgb(230, 250, 255))

    private fun depthFill(t: Float) = depthFills[(t * (DEPTH_BUCKETS - 1) + .5f).toInt().coerceIn(0, DEPTH_BUCKETS - 1)]

    /**
     * One half of the spatial network inside the glass ([front] = facing the viewer). Back links are thin, dim and
     * violet; front links sharp, bright and cyan-white. Over the core the front half is faint, so the infinity
     * mark stays clearly readable. Light pulses travel along a fixed subset of front links.
     */
    private fun drawPlexus(p: ContinuumPainter, f: Frame, front: Boolean) {
        val px = plexus
        val intensity = .82f + f.weight(Mode.THINKING) * .25f + f.weight(Mode.LEARNING) * .18f
        drawArcs(p, front, intensity)
        val core = rs * .42f
        for (e in 0 until px.edgeCount) {
            val a = px.edgeA[e]; val b = px.edgeB[e]
            if (px.layer[a] == ContinuumHologramPlexus.Layer.HALO) continue
            val d = (px.depth[a] + px.depth[b]) * .5f
            if ((d >= 0f) != front) continue
            val t = (d + 1f) * .5f
            var alpha = (.08f + .8f * t * t) * intensity
            if (front) {
                val mx = (px.sx[a] + px.sx[b]) * .5f - cx; val my = (px.sy[a] + px.sy[b]) * .5f - cy
                if (mx * mx + my * my < core * core) alpha *= .3f
            }
            p.drawLine(px.sx[a], px.sy[a], px.sx[b], px.sy[b], (.36f + .74f * t) * sphereScale,
                if (e % 6 == 0) accentFill else depthFill(t), alpha)
        }
        if (front) for (e in 0 until px.edgeCount step 7) {
            val a = px.edgeA[e]; val b = px.edgeB[e]
            if (px.layer[a] == ContinuumHologramPlexus.Layer.HALO || px.depth[a] + px.depth[b] < 0f) continue
            val t = (f.phase * .3f + e * .618034f) % 1f
            val t1 = min(1f, t + .16f)
            val x0 = px.sx[a] + (px.sx[b] - px.sx[a]) * t; val y0 = px.sy[a] + (px.sy[b] - px.sy[a]) * t
            val x1 = px.sx[a] + (px.sx[b] - px.sx[a]) * t1; val y1 = px.sy[a] + (px.sy[b] - px.sy[a]) * t1
            p.drawLine(x0, y0, x1, y1, .9f * sphereScale, pulseFill, .75f * intensity * sin(t * PI.toFloat()))
        }
        for (i in 0 until px.nodeCount) {
            val layer = px.layer[i]
            if (layer == ContinuumHologramPlexus.Layer.HALO) continue
            val d = px.depth[i]
            if ((d >= 0f) != front) continue
            val t = (d + 1f) * .5f
            val fill = if (i % 11 == 0) nodePink else if (layer == ContinuumHologramPlexus.Layer.INNER) nodeViolet else nodeCool
            p.fillCircle(px.sx[i], px.sy[i], (.4f + 1.05f * t) * sphereScale, fill, (.12f + .85f * t) * intensity)
            if (front && t > .78f && i % 3 == 0) {
                glow(p, px.sx[i], px.sy[i], 3.6f * sphereScale, Argb.withAlpha((fill as Fill.Solid).argb, .6f), intensity)
            }
        }
        for (i in 0 until px.dustCount) {
            val d = px.dustDepth[i]
            if ((d >= 0f) != front) continue
            val t = (d + 1f) * .5f
            val twinkle = .5f + .5f * sin(f.phase * (.9f + (i % 7) * .11f) + i * 1.3f)
            val fill = when (px.dustTint[i]) { 2 -> dustFills[if (i % 2 == 0) 2 else 3]; 1 -> dustFills[1]; else -> dustFills[0] }
            p.fillCircle(px.dustX[i], px.dustY[i], px.dustSize[i] * (.75f + .55f * t) * sphereScale, fill, (.35f + .65f * t) * (.5f + .5f * twinkle))
            if (front && i % 17 == 0) glow(p, px.dustX[i], px.dustY[i], 3.4f * sphereScale, Argb.withAlpha(fill.argb, .5f), twinkle)
        }
    }

    /** The part of every light arc on the [front] or back half (circle angles 0..180 are in front). */
    private fun drawArcs(p: ContinuumPainter, front: Boolean, intensity: Float) {
        val px = plexus
        for (i in 0 until px.arcCount) {
            val from = px.arcFrom[i]; val to = from + px.arcSweepNow[i]
            var window = if (front) 0f else 180f
            while (window < to) {
                val a = max(from, window); val b = min(to, window + 180f)
                if (b > a) drawArc(p, i, a, b, front, intensity)
                window += 360f
            }
        }
    }

    private fun drawArc(p: ContinuumPainter, i: Int, from: Float, to: Float, front: Boolean, intensity: Float) {
        val px = plexus
        val a = rs * px.arcRadius[i]; val b = a * px.arcRatio[i]
        val start = if (px.arcMirror[i]) -to else from
        val fill = arcFills[i % arcFills.size]
        val k = (if (front) 1f else .32f) * intensity
        p.save(); p.rotate(px.arcAngle[i], cx, cy)
        p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, to - from, 3.2f * sphereScale, fill, .12f * k)
        p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, to - from, 1.2f * sphereScale, fill, .45f * k)
        if (front) p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, to - from, .5f * sphereScale, arcCores[i % arcCores.size], .9f * k)
        p.restore()
    }

    private val arcColors = intArrayOf(Argb.rgb(120, 225, 255), Argb.rgb(90, 150, 255), Argb.rgb(180, 130, 255), Argb.rgb(240, 120, 215))
    private val arcFills = Array(arcColors.size) { Fill.Solid(arcColors[it]) }
    private val arcCores = Array(arcColors.size) { Fill.Solid(Argb.mix(arcColors[it], Argb.rgb(255, 255, 255), .55f)) }

    /** The sparse network around the sphere (behind the glass, so only its outer part shows). */
    private fun drawHalo(p: ContinuumPainter, f: Frame) {
        val px = plexus
        val k = geometry.aura
        for (e in 0 until px.edgeCount) {
            val a = px.edgeA[e]; val b = px.edgeB[e]
            if (px.layer[a] != ContinuumHologramPlexus.Layer.HALO) continue
            val t = ((px.depth[a] + px.depth[b]) * .5f + 1f) * .5f
            p.drawLine(px.sx[a], px.sy[a], px.sx[b], px.sy[b], (.4f + .45f * t) * sphereScale, haloFill, (.12f + .35f * t) * k)
        }
        for (i in 0 until px.nodeCount) {
            if (px.layer[i] != ContinuumHologramPlexus.Layer.HALO) continue
            val t = (px.depth[i] + 1f) * .5f
            val twinkle = .6f + .4f * sin(f.phase * (.8f + (i % 5) * .15f) + i)
            p.fillCircle(px.sx[i], px.sy[i], (.5f + .7f * t) * sphereScale, if (i % 4 == 0) nodePink else nodeCool, (.3f + .6f * t) * twinkle * k)
            if (i % 5 == 0) glow(p, px.sx[i], px.sy[i], 3f * sphereScale, Argb.of(150, 150, 210, 255), twinkle * k)
        }
    }

    private fun drawInfinity(p: ContinuumPainter, f: Frame) {
        val a = rs * .29f; val b = rs * .135f
        val oy = cy + rs * .02f
        path.reset()
        for (i in 0..96) {
            val t = i * (2f * PI.toFloat() / 96f)
            val x = cx + a * sin(t)
            val y = oy + b * sin(t) * cos(t) * 2f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val pulse = .85f + f.energy * .15f + f.audio * .2f
        p.setAdditive(true)
        p.strokePath(path, rs * .17f, infinity, .22f * pulse)
        p.setAdditive(false)
        p.strokePath(path, rs * .085f, infinity, 1f)
        p.strokePath(path, rs * .032f, infinityCore, .55f)
        p.strokePath(path, rs * .011f, infinityHighlight, .6f * pulse)
    }

    private val infinityCore = Fill.Solid(Argb.rgb(70, 80, 190))
    private val infinityHighlight = Fill.Solid(Argb.rgb(240, 252, 255))

    private fun drawStateEffects(p: ContinuumPainter, f: Frame) {
        val listening = f.weight(Mode.LISTENING)
        if (listening > .02f) for (i in 0..2) {
            val progress = (f.phase * .5f + i / 3f) % 1f
            val radius = rs * (.45f + (1f - progress) * .5f)
            p.strokeCircle(cx, cy, radius, 1.1f * sphereScale, stateCyan, listening * progress * .7f)
        }
        val thinking = f.weight(Mode.THINKING)
        if (thinking > .02f) {
            p.save(); p.rotate(f.phase * 44f, cx, cy)
            for (i in 0..2) {
                val radius = rs * (.5f + i * .12f)
                p.strokeArc(cx - radius, cy - radius, cx + radius, cy + radius, i * 110f, 78f, 1.7f * sphereScale,
                    if (i == 1) stateCyan else stateViolet, thinking * .85f)
            }
            p.restore()
        }
        val speaking = f.weight(Mode.SPEAKING)
        if (speaking > .02f && f.audio > 0f) {
            // Only real PCM energy moves these lines; without audio data they stay hidden.
            for (line in 0..2) {
                path.reset()
                for (i in 0..64) {
                    val x = cx - rs * .62f + i * (rs * 1.24f / 64f)
                    val taper = sin(i * PI.toFloat() / 64f)
                    val wave = sin(i * .27f - f.phase * 8f + line * .8f) * taper
                    val y = cy + rs * .58f + (line - 1) * 4f * sphereScale + wave * f.audio * rs * (.12f - line * .02f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                p.strokePath(path, .9f * sphereScale, if (line == 1) speechCore else stateCyan, speaking * (.4f + f.audio * .6f))
            }
        }
        val research = f.weight(Mode.RESEARCH)
        if (research > .02f) {
            p.save(); p.rotate(f.phase * 34f, cx, cy)
            val rr = rs * .78f
            p.strokeArc(cx - rr, cy - rr, cx + rr, cy + rr, -65f, 105f, 1.8f * sphereScale, stateCyan, research * .85f)
            p.drawLine(cx, cy, cx + rr * .7f, cy - rr * .7f, .8f * sphereScale, stateCyan, research * .5f)
            p.restore()
        }
        val learning = f.weight(Mode.LEARNING)
        if (learning > .02f) for (i in 0 until 12) {
            val progress = (f.phase * .24f + i / 12f) % 1f
            val x = cx + sin(i * 2.4f + progress * 3f) * rs * (.55f - progress * .35f)
            val y = cy + rs * 1.05f - progress * rs * 1.05f
            p.fillCircle(x, y, (1.3f + progress * .6f) * sphereScale, if (i % 2 == 0) stateViolet else stateCyan,
                learning * sin(progress * PI.toFloat()) * .9f)
        }
    }

    private fun drawSparkles(p: ContinuumPainter, f: Frame) {
        val spots = floatArrayOf(-0.93f, -0.3f, 0.97f, -0.2f, -0.55f, 0.84f, 0.62f, 0.8f, 0.02f, -1.0f, 0.38f, -0.93f)
        for (i in 0 until spots.size / 2) {
            val x = cx + spots[i * 2] * rs; val y = cy + spots[i * 2 + 1] * rs
            val pulse = .6f + .4f * sin(f.phase * (1.1f + i * .2f) + i * 1.3f)
            val size = rs * (.03f + (i % 2) * .018f) * (1f + f.energy * .4f)
            glow(p, x, y, size * 3.6f, Argb.of(170, 175, 232, 255), pulse)
            p.drawLine(x - size * 3.2f, y, x + size * 3.2f, y, max(.4f, size * .16f), sparkLine, pulse * .8f)
            p.drawLine(x, y - size * 3.2f, x, y + size * 3.2f, max(.4f, size * .16f), sparkLine, pulse * .8f)
            p.fillCircle(x, y, size * .6f, sparkCore, pulse)
        }
    }

    private val stateCyan = Fill.Solid(Argb.rgb(90, 225, 255))
    private val stateViolet = Fill.Solid(Argb.rgb(160, 120, 255))
    private val speechCore = Fill.Solid(Argb.rgb(204, 248, 255))
    private val sparkLine = Fill.Solid(Argb.rgb(235, 250, 255))
    private val sparkCore = Fill.Solid(Argb.rgb(248, 253, 255))

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
    val sphereRadius: Float get() = rs

    private companion object {
        const val DEPTH_BUCKETS = 9
    }
}
