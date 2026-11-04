package at.heinz.deviceai.avatar

import at.heinz.deviceai.ui.Argb
import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.JarvisDesign
import at.heinz.deviceai.ui.JarvisMotion
import at.heinz.deviceai.ui.JarvisNeon
import at.heinz.deviceai.ui.VectorPath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Holographic bust: a procedural 3-D head surface drawn as dense flowing ref5 fibres (or ref1/ref4 wireframe)
 * contour lines with Fresnel rim light, glowing eyes and lips, neck/shoulders, tilted light orbits that pass
 * behind and in front of the head, and a projection platform.
 *
 * Everything is driven only by [AvatarPose] (existing AvatarContract) and the caller's clock:
 * gaze turns the head and the irises, the existing TTS envelope ([AvatarPose.jawOpen], [AvatarPose.speakEnergy])
 * opens the jaw (the mesh below the lips deforms), nods the head and makes the light orbits pulse; viseme cues
 * round or widen the lips. No downloaded portrait, camera image, audio engine or invented runtime state.
 * Per-frame work reuses preallocated segment buffers (no allocation in [draw] after the first frame).
 */
class ContinuumAvatarRenderer {
    private val cyan = Fill.Solid(0xff77efff.toInt())
    private val violet = Fill.Solid(0xffb293ff.toInt())
    private val white = Fill.Solid(0xffe6fdff.toInt())
    private val dark = Fill.Solid(0xff030915.toInt())
    private val eyeLight = Fill.Solid(0xff00e5ff.toInt())
    private val edge = Fill.Linear(-70f, 0f, 70f, 0f, intArrayOf(0xff55d9f5.toInt(), 0xff7f9bff.toInt(), 0xffa78bff.toInt()))
    private val ref5Rim = Fill.Linear(-72f, 0f, 72f, 0f,
        intArrayOf(0xffbd8cff.toInt(), 0xff668bff.toInt(), 0xff72f0ff.toInt()))
    private val glass = Fill.Radial(-12f, -30f, 125f,
        intArrayOf(0xc02a78c8.toInt(), 0xb0163e8c.toInt(), 0xa02a1a78.toInt()), floatArrayOf(0f, .55f, 1f))
    private val bodyGlass = Fill.Linear(0f, 80f, 0f, 175f, intArrayOf(0x90205a9c.toInt(), 0x50182a70, 0x00000000))
    // unit radial fills for volumetric light / shadow (placed with translate+scale, no per-frame allocation)
    private val softLight = Fill.Radial(0f, 0f, 1f, intArrayOf(0xff9fe8ff.toInt(), 0x553f8cff, 0x00000000), floatArrayOf(0f, .45f, 1f))
    private val softViolet = Fill.Radial(0f, 0f, 1f, intArrayOf(0xffb08cff.toInt(), 0x445a3cc8, 0x00000000), floatArrayOf(0f, .5f, 1f))
    private val softShadow = Fill.Radial(0f, 0f, 1f, intArrayOf(0xe0020612.toInt(), 0x80030a1c.toInt(), 0x00000000), floatArrayOf(0f, .55f, 1f))
    private val ear = VectorPath().moveTo(0f, -16f).cubicTo(9f, -22f, 13f, -6f, 9f, 6f).cubicTo(7f, 14f, 3f, 20f, -1f, 18f)
    private val earInner = VectorPath().moveTo(1f, -10f).cubicTo(7f, -13f, 8f, -2f, 5f, 6f)
    private val irisGlow = Fill.Radial(0f, 0f, 1f, intArrayOf(0xff9ff6ff.toInt(), 0x6640c8ff, 0x00000000), floatArrayOf(0f, .35f, 1f))

    // Static silhouettes (allocated once). Face units: crown y=-100, chin y=92, half width ~66.
    private val face = VectorPath().moveTo(0f, -104f)
        .cubicTo(-46f, -106f, -76f, -78f, -72f, -40f)
        .cubicTo(-70f, -12f, -64f, 22f, -54f, 44f)
        .cubicTo(-45f, 66f, -27f, 89f, 0f, 93f)
        .cubicTo(27f, 89f, 45f, 66f, 54f, 44f)
        .cubicTo(64f, 22f, 70f, -12f, 72f, -40f)
        .cubicTo(76f, -78f, 46f, -106f, 0f, -104f).close()
    private val body = VectorPath().moveTo(-22f, 76f).cubicTo(-23f, 96f, -24f, 112f, -30f, 124f)
        .cubicTo(-70f, 132f, -118f, 138f, -136f, 182f).lineTo(136f, 182f)
        .cubicTo(118f, 138f, 70f, 132f, 30f, 124f).cubicTo(24f, 112f, 23f, 96f, 22f, 76f).close()
    private val upperLip = VectorPath()
    private val lowerLip = VectorPath()
    private val bodyFibre = VectorPath()
    private val eyeAperture = VectorPath()
    private val upperLid = VectorPath()
    private val lowerLid = VectorPath()
    private val lidVolume = VectorPath()
    private val browContour = VectorPath()
    private val eyeDepth = Fill.Linear(0f, -6f, 0f, 6f,
        intArrayOf(0xff030b21.toInt(), 0xff102a59.toInt(), 0xff0b1535.toInt()))
    private val lidGlass = Fill.Linear(0f, -10f, 0f, 2f,
        intArrayOf(0x69688ee0, 0x443c4896, 0x0c0b183a))

    /** Cosmetic eyelid only. The animation never changes the app's work state. */
    internal fun eyeOpening(seconds: Float): Float {
        val cycle = ((seconds % 4.6f) + 4.6f) % 4.6f
        // VIDEO-SPEC: periodic blink of ~0.15 s
        return if (cycle < 4.45f) 1f else abs(cycle - 4.525f).div(.075f).coerceIn(.05f, 1f)
    }

    /**
     * Power-on sequence: eyes closed, then open over ~1.3 s (with a short flutter). Pass seconds since the avatar
     * was switched on; the result goes into [draw]'s `wake`. 1 = fully awake.
     */
    fun wakeOpening(secondsSinceStart: Float): Float {
        if (secondsSinceStart <= .35f) return 0f
        val t = ((secondsSinceStart - .35f) / 1.3f).coerceIn(0f, 1f)
        val flutter = if (t in .45f..0.62f) .55f else 1f
        return (t * t * (3f - 2f * t) * flutter).coerceIn(0f, 1f)
    }

    // ---- procedural head surface -------------------------------------------------------------------------------
    private val profileY = floatArrayOf(-104f, -97f, -86f, -70f, -50f, -30f, -10f, 15f, 40f, 60f, 77f, 88f, 93f)
    private val profileW = floatArrayOf(2f, 30f, 52f, 66f, 72f, 71f, 66f, 58f, 49f, 39f, 26f, 13f, 2f)

    private fun halfWidth(y: Float): Float {
        if (y <= profileY[0]) return profileW[0]
        for (i in 1 until profileY.size) if (y <= profileY[i]) {
            val t = (y - profileY[i - 1]) / (profileY[i] - profileY[i - 1])
            val s = t * t * (3f - 2f * t)
            return profileW[i - 1] + (profileW[i] - profileW[i - 1]) * s
        }
        return profileW.last()
    }

    private fun g(x: Float, y: Float, cx: Float, cy: Float, sx: Float, sy: Float): Float {
        val dx = (x - cx) / sx; val dy = (y - cy) / sy
        return exp(-(dx * dx + dy * dy))
    }

    /** Forward relief of facial features at (x, y) on the front of the head, in face units. */
    private fun relief(x: Float, y: Float, jaw: Float): Float {
        val ax = abs(x)
        var z = 0f
        z += 15f * g(x, y, 0f, 16f, 7f, 15f) + 7f * g(x, y, 0f, -8f, 5f, 16f)          // nose ridge + tip
        z -= 8f * g(ax, y, 28f, -20f, 13f, 7f)                                          // eye sockets
        z += 4.5f * g(ax, y, 26f, -36f, 18f, 4.5f)                                      // brow ridge
        z += 3.5f * g(ax, y, 40f, 6f, 12f, 10f)                                         // cheekbones
        z += 4.5f * g(x, y, 0f, 48f, 15f, 4f) + 3.5f * g(x, y, 0f, 59f + jaw * 9f, 14f, 4.5f) // lips
        z -= 3f * g(x, y, 0f, 53f + jaw * 4f, 18f, 2.5f + jaw * 4f)                     // mouth line / opening
        z += 5f * g(x, y, 0f, 81f + jaw * 9f, 14f, 7f)                                  // chin
        return z
    }

    // projection state for one frame
    private var cosYaw = 1f; private var sinYaw = 0f; private var cosPitch = 1f; private var sinPitch = 0f
    private var px = 0f; private var py = 0f; private var pz = 0f; private var shade = 0f

    private fun project(x: Float, y: Float, z: Float) {
        val x1 = x * cosYaw + z * sinYaw
        val z1 = -x * sinYaw + z * cosYaw
        val y1 = y * cosPitch - z1 * sinPitch
        val z2 = y * sinPitch + z1 * cosPitch
        val k = 1f + z2 / 900f                     // mild perspective
        px = x1 * k; py = y1 * k; pz = z2
    }

    /** Surface point for a contour parameter: longitude [theta] (0 = front), height [y]; jaw drop below the lips. */
    private fun surface(theta: Float, y: Float, jaw: Float) {
        val drop = if (y > 50f) jaw * 10f * ((y - 50f) / 43f).coerceIn(0f, 1f).let { min(1f, it * 2.2f) } else 0f
        val yy = y + drop
        val w = halfWidth(y) * (if (y > 50f) 1f - jaw * .04f else 1f)
        val d = w * 1.12f
        val x = w * sin(theta)
        val c = cos(theta)
        val front = c > 0f
        val rel = if (front) relief(x, y, jaw) else 0f
        val z = d * c + rel * c * c
        // key light from upper left: slopes facing it brighten, recesses (eye sockets, mouth line) darken
        shade = if (front) ((relief(x - 3f, y - 3f, jaw) - relief(x + 3f, y + 3f, jaw)) * .085f +
            (if (rel < -2f) rel * .08f else 0f)) * c else 0f
        project(x, yy, z)
    }

    // Segment buckets: 3 hues (cyan/blue/violet by screen x) x 4 brightness levels.
    // ref5 (#479 tokens): cranium top / back-left violet, face blue (cyan<->violet mix), lit right side cyan
    private val hueColors = intArrayOf(JarvisDesign.Colors.violet,
        Argb.mix(JarvisDesign.Colors.cyan, JarvisDesign.Colors.violet, .55f), JarvisDesign.Colors.cyan)
    private val hueFills = arrayOf(Fill.Solid(Argb.mix(JarvisDesign.Colors.violet, JarvisDesign.Colors.glowCore, .25f)),
        Fill.Solid(Argb.mix(JarvisDesign.Colors.cyan, JarvisDesign.Colors.violet, .5f)),
        Fill.Solid(Argb.mix(JarvisDesign.Colors.cyan, JarvisDesign.Colors.glowCore, .3f)))
    private val levelAlpha = floatArrayOf(.3f, .5f, .72f, 1f)
    private val buckets = Array(12) { FloatArray(SEG_CAPACITY) }
    private val bucketCount = IntArray(12)

    private fun addSegment(x0: Float, y0: Float, x1: Float, y1: Float, bright: Float) {
        val mx = (x0 + x1) * .5f; val my = (y0 + y1) * .5f
        val hue = if (my < -42f + mx * .45f || mx < -44f) 0 else if (mx > 22f && my > -50f) 2 else 1
        val level = (bright * 3.99f).toInt().coerceIn(0, 3)
        val b = hue * 4 + level
        val n = bucketCount[b]
        if (n + 4 > SEG_CAPACITY) return
        val a = buckets[b]
        a[n] = x0; a[n + 1] = y0; a[n + 2] = x1; a[n + 3] = y1
        bucketCount[b] = n + 4
    }

    /** Hologram brightness: Fresnel rim (edges bright) plus a soft key light from the upper left. */
    private fun brightness(theta: Float, y: Float): Float {
        val facing = cos(theta + yawRad)
        if (facing < -.2f) return -1f
        val rim = (1f - facing.coerceIn(0f, 1f))
        val key = (.5f - theta * .25f - y / 300f).coerceIn(0f, 1f) * .35f
        return (.2f + rim * rim * .68f + key).coerceIn(0f, 1f)
    }
    private var yawRad = 0f
    private var bloom = .5f

    // Ref5's fibres follow the scalp, temples and mouth without a crossing mesh over the face. The contours
    // are static surface coordinates; gaze, jaw deformation and brightness still use the existing pose path.
    // Sample count stays below the old scan-line + diagonals + meridians total. No per-frame curve allocation.
    private val fibreTheta = FloatArray(FIBRE_COLS + 1) { -1.9f + it * (3.8f / FIBRE_COLS) }
    private val fibreHeight = FloatArray(FIBRE_ROWS * (FIBRE_COLS + 1)) { index ->
        val row = index / (FIBRE_COLS + 1)
        val theta = fibreTheta[index % (FIBRE_COLS + 1)]
        val t = row / (FIBRE_ROWS - 1f)
        val y = -103f + t * 195f
        fun falloff(center: Float, spread: Float): Float {
            val d = (y - center) / spread
            return exp(-d * d)
        }
        val scalp = 20f * sin(theta * 1.2f + .4f) * falloff(-68f, 45f)
        val temple = 7f * sin(theta * 2f - .25f) * falloff(-14f, 33f)
        val x = halfWidth(y) * sin(theta)
        val eyeDx = (abs(x) - 28f) / 17f
        val eyeDy = (y + 20f) / 18f
        val eyeFlow = 17f * eyeDy * exp(-eyeDx * eyeDx - eyeDy * eyeDy)
        val lipDx = x / 24f
        val lipDy = (y - 53f) / 14f
        val lipFlow = 11f * lipDy * exp(-lipDx * lipDx - lipDy * lipDy)
        val bridge = -7f * exp(-(x / 10f) * (x / 10f)) * falloff(0f, 28f)
        y + sin(t * PI.toFloat()) * (scalp + temple + eyeFlow + lipFlow + bridge)
    }

    private fun buildFibres(jaw: Float, flow: Float) {
        bucketCount.fill(0)
        for (row in 0 until FIBRE_ROWS) {
            var previousX = 0f; var previousY = 0f; var previousBrightness = -1f
            val offset = row * (FIBRE_COLS + 1)
            val strandLight = .86f + .14f * sin(row * 2.399f)
            for (column in 0..FIBRE_COLS) {
                val theta = fibreTheta[column]
                val y = fibreHeight[offset + column]
                surface(theta, y, jaw)
                val light = brightness(theta, y).let { if (it < 0f) it else (it + shade).coerceIn(.05f, 1f) }
                if (column > 0 && light >= 0f && previousBrightness >= 0f) {
                    val distance = (y + 100f - flow * 230f) / 18f
                    val wave = .16f * exp(-distance * distance)
                    addSegment(previousX, previousY, px, py,
                        (((light + previousBrightness) * .5f + wave) * strandLight).coerceIn(0f, 1f))
                }
                previousX = px; previousY = py; previousBrightness = light
            }
        }
    }

    private fun buildMesh(jaw: Float, flow: Float) {
        bucketCount.fill(0)
        // horizontal contour lines (the flowing "scan lines" of the references)
        val rows = 60
        val cols = 40
        for (r in 0 until rows) {
            val y = -103f + r * (195f / (rows - 1))
            var prevX = 0f; var prevY = 0f; var prevB = -1f
            for (c in 0..cols) {
                val theta = -1.9f + c * (3.8f / cols)
                surface(theta, y, jaw)
                val b = brightness(theta, y).let { if (it < 0f) it else (it + shade).coerceIn(.05f, 1f) }
                if (c > 0 && b >= 0f && prevB >= 0f) {
                    // a slow light wave travels down the face
                    val wave = .25f * exp(-((y + 100f - flow * 230f) / 18f).let { it * it })
                    addSegment(prevX, prevY, px, py, ((b + prevB) * .5f + wave).coerceIn(0f, 1f))
                }
                prevX = px; prevY = py; prevB = b
            }
        }
        // spun-glass flow lines in two diagonal directions (theta drifts with height)
        for (dir in intArrayOf(-1, 1)) for (m in 0..17) {
            val theta0 = -1.7f + m * (3.4f / 17)
            var prevX = 0f; var prevY = 0f; var prevB = -1f
            for (s in 0..30) {
                val y = -99f + s * (191f / 30)
                val theta = theta0 + dir * (s / 30f) * 1.1f
                if (theta < -1.9f || theta > 1.9f) { prevB = -1f; continue }
                surface(theta, y, jaw)
                val b = brightness(theta, y)
                if (s > 0 && b >= 0f && prevB >= 0f) addSegment(prevX, prevY, px, py, (b + prevB) * .24f)
                prevX = px; prevY = py; prevB = b
            }
        }
        // meridians (lighter, give the surface its curvature)
        val meridians = 16
        for (m in 0..meridians) {
            val theta = -1.75f + m * (3.5f / meridians)
            var prevX = 0f; var prevY = 0f; var prevB = -1f
            for (s in 0..36) {
                val y = -99f + s * (191f / 36)
                surface(theta, y, jaw)
                val b = brightness(theta, y)
                if (s > 0 && b >= 0f && prevB >= 0f) addSegment(prevX, prevY, px, py, (b + prevB) * .2f)
                prevX = px; prevY = py; prevB = b
            }
        }
    }

    // ---- light orbits ------------------------------------------------------------------------------------------
    private class Orbit(val cy: Float, val a: Float, val b: Float, val tilt: Float, val speed: Float, val color: Int, val width: Float)
    // Slanted orbit rings with bright nodes around the cranium. 3 rings confirmed (video 1).
    private val allOrbits = arrayOf(
        // three different axes (video 1, 0–3 s): temple ring, steep crossing ring, wide lower ring
        Orbit(-70f, 106f, 22f, 12f, .16f, 0xff00e5ff.toInt(), 1.5f),
        Orbit(-62f, 112f, 30f, -27f, -.13f, 0xff9a6bff.toInt(), 1.5f),
        Orbit(-28f, 124f, 26f, 6f, .10f, 0xff3aa8ff.toInt(), 1.2f)
    )
    /** Number of rings (1..3). */
    var ringCount = 3
        set(value) { field = value.coerceIn(1, allOrbits.size) }
    /** ref5 shows no orbit rings around the cranium: off by default, kept for video 1 (scene 1/2). */
    var showOrbits = false
    /** ref5 head composition: big HUD ring behind the head, face-tracking brackets, light/particle hair. */
    var ref5Hud = true
    private val orbits get() = allOrbits
    private val orbitFills = allOrbits.map { Fill.Solid(it.color) }
    private val orbitCore = Fill.Solid(0xffeafcff.toInt())
    private val speakCyan = Fill.Solid(0xff00ffff.toInt())

    private fun drawOrbits(p: ContinuumPainter, front: Boolean, seconds: Float, pulse: Float, motion: Boolean) {
        p.setAdditive(true)
        for (i in 0 until ringCount) {
            val o = allOrbits[i]
            p.save(); p.rotate(o.tilt, 0f, o.cy)
            val l = -o.a; val t = o.cy - o.b; val r = o.a; val b = o.cy + o.b
            // screen-space ellipse: angles 0..180 are the lower (front) half
            val start = if (front) 0f else 180f
            val k = if (front) 1f else .45f
            val fill = orbitFills[i]
            p.strokeArc(l, t, r, b, start, 180f, o.width * 5f * pulse, fill, .06f * k * pulse)
            p.strokeArc(l, t, r, b, start, 180f, o.width * 2.2f, fill, .22f * k * pulse)
            p.strokeArc(l, t, r, b, start, 180f, o.width * .7f, fill, .75f * k)
            for (fbr in 1..4) {   // spun fibres around the ring (ref1: bundles, not single lines)
                val d = (if (fbr % 2 == 0) 1f else -1f) * (1.6f + fbr * 1.3f)
                p.strokeArc(l - d, t - d * .35f, r + d, b + d * .35f, start, 180f, .45f, fill, (.32f - fbr * .05f) * k)
            }
            // travelling comet of light (phase from the caller's clock only)
            val head = (((if (motion) seconds else 0f) * o.speed * 360f + i * 71f) % 360f + 360f) % 360f
            val inHalf = if (front) head in 0f..180f else head >= 180f
            if (inHalf) {
                p.strokeArc(l, t, r, b, head - 38f, 38f, o.width * 3.4f, fill, .35f * pulse)
                p.strokeArc(l, t, r, b, head - 14f, 14f, o.width * 1.4f, orbitCore, .9f * k)
            }
            // nodes riding on the ring (4 per ring, evenly spaced, moving with the comet)
            for (n in 0 until 4) {
                val ang = ((head + n * 90f) % 360f) * (PI.toFloat() / 180f)
                val front2 = sin(ang) >= 0f
                if (front2 != front) continue
                val nx = cos(ang) * o.a; val ny = o.cy + sin(ang) * o.b
                JarvisNeon.bloom(p, nx, ny, 4f * pulse, o.color, k * bloom)
                p.fillCircle(nx, ny, 4.5f * pulse, fill, .22f * k)
                p.fillCircle(nx, ny, 1.9f, orbitCore, .95f * k)
            }
            p.restore()
        }
        p.setAdditive(false)
    }

    // ---- scene 2 (video 1, 3–5 s): glowing 3-D file/folder icons burst out of the head towards camera + right ----
    private var burstAt = Float.NaN
    /**
     * Starts the icon burst at [seconds] (same clock as [draw]'s `seconds`). Trigger is Kern's (cockpit fly-in);
     * cosmetic only, never a work state. ~1.5 s ease-out, then gone.
     */
    fun headIconBurst(seconds: Float) { burstAt = seconds }
    fun isBursting(seconds: Float) = burstAt.isFinite() && seconds - burstAt in 0f..BURST_S + BURST_STAGGER * BURST_ICONS
    // unit icons, centred, ~1 unit tall (allocated once)
    private val fileIcon = VectorPath().moveTo(-.38f, -.5f).lineTo(.18f, -.5f).lineTo(.38f, -.3f).lineTo(.38f, .5f)
        .lineTo(-.38f, .5f).close()
    private val fileFold = VectorPath().moveTo(.18f, -.5f).lineTo(.18f, -.3f).lineTo(.38f, -.3f)
    private val folderIcon = VectorPath().moveTo(-.5f, -.32f).lineTo(-.12f, -.32f).lineTo(-.02f, -.2f).lineTo(.5f, -.2f)
        .lineTo(.5f, .36f).lineTo(-.5f, .36f).close()
    private val iconGlass = Fill.Solid(0xff0a2a5c.toInt())
    private val iconBack = Fill.Solid(0xff0088ff.toInt())

    private fun drawBurst(p: ContinuumPainter, seconds: Float) {
        if (!isBursting(seconds)) return
        for (i in 0 until BURST_ICONS) {
            val t = ((seconds - burstAt - i * BURST_STAGGER) / BURST_S)
            if (t <= 0f || t >= 1f) continue
            val e = 1f - (1f - t) * (1f - t) * (1f - t)   // cubic ease-out
            // deterministic fan towards the right and the camera
            val ang = (-.95f + 1.9f * ((i * 7) % BURST_ICONS) / (BURST_ICONS - 1f))
            val dist = 150f + (i * 37 % 70)
            val x = 6f + cos(ang) * dist * e
            val y = -40f + sin(ang) * dist * .85f * e
            val size = 8f + 42f * e * e                      // grows as it nears the camera
            val a = (if (t < .12f) t / .12f else 1f) * (1f - ((t - .7f) / .3f).coerceIn(0f, 1f))
            val folder = i % 3 == 0
            val col = if (i % 2 == 0) JarvisDesign.Colors.cyan else JarvisDesign.Colors.violet
            JarvisNeon.bloom(p, x, y, size * .22f, col, a * (.25f + .2f * bloom))
            p.save(); p.translate(x, y); p.rotate(ang * 18f + (1f - e) * 25f); p.scale(size, size)
            val path = if (folder) folderIcon else fileIcon
            // 3-D: a darker back face offset towards the vanishing point, then the glowing front face
            p.save(); p.translate(-.07f, -.05f); p.fillPath(path, iconBack, .25f * a); p.restore()
            p.fillPath(path, iconGlass, .45f * a)
            p.setAdditive(true); p.fillPath(path, iconBack, .18f * a); p.setAdditive(false)
            p.setAdditive(true)
            p.strokePath(path, .1f, if (i % 2 == 0) speakCyan else violet, .25f * a)
            p.strokePath(path, .035f, orbitCore, .95f * a)
            if (!folder) p.strokePath(fileFold, .035f, orbitCore, .8f * a)
            p.setAdditive(false)
            p.restore()
        }
    }

    // ---- ref5 light/particle hair: violet filaments streaming from the back-left of the cranium -------------------
    private val hairY0 = FloatArray(HAIR_N) { i -> -92f + i * (118f / (HAIR_N - 1)) }
    private val hairLen = FloatArray(HAIR_N) { i -> 4.8f + (i * 7 % 11) * .1f }
    private val hairBuf = FloatArray(HAIR_N * HAIR_PTS * 4)
    private val hairFill = Fill.Solid(Argb.mix(JarvisDesign.Colors.violet, JarvisDesign.Colors.glowCore, .2f))
    private val hairCore = Fill.Solid(JarvisDesign.Colors.glowCore)
    private val hudFill = Fill.Solid(JarvisDesign.Colors.cyan)
    private val hudMagenta = Fill.Solid(JarvisDesign.Colors.violetRim)

    private fun drawHair(p: ContinuumPainter, seconds: Float, motion: Boolean, speak: Float,
                         halfStage: Float = Float.POSITIVE_INFINITY) {
        var n = 0
        val t = if (motion) seconds else 0f
        for (i in 0 until HAIR_N) {
            val y0 = hairY0[i]
            val x0 = -halfWidth(y0) * .96f + sinYaw * 6f
            val endX = maxOf(-halfStage + 8f, -110f + (i % 6) * 1.4f)
            val endY = 122f + (i % 5) * 2.2f
            val controlX = maxOf(-halfStage + 5f, endX - hairLen[i] * 7f)
            var x = x0; var y = y0
            for (k in 1..HAIR_PTS) {
                val u = k / HAIR_PTS.toFloat(); val v = 1f - u
                val nx = v * v * v * x0 + 3f * v * v * u * (x0 - 24f) +
                    3f * v * u * u * controlX + u * u * u * endX
                val ny = v * v * v * y0 + 3f * v * v * u * (y0 + 12f) +
                    3f * v * u * u * (endY - 22f) + u * u * u * endY +
                    sin(t * 1.3f + i * .9f + u * 3f) * 1.6f * sin(u * PI.toFloat())
                if (n + 4 <= hairBuf.size) { hairBuf[n] = x; hairBuf[n + 1] = y; hairBuf[n + 2] = nx; hairBuf[n + 3] = ny; n += 4 }
                x = nx; y = ny
            }
        }
        p.setAdditive(true)
        JarvisNeon.bloomSegments(p, hairBuf, n, .5f, JarvisDesign.Colors.violet, .6f + speak * .4f)
        p.strokeSegments(hairBuf, n, .45f, hairFill, .55f)
        // particles riding along the filaments
        for (i in 0 until HAIR_N step 2) {
            val k = (((if (motion) seconds * .6f else .3f) + i * .37f) % 1f * (HAIR_PTS - 1)).toInt()
            val o = (i * HAIR_PTS + k) * 4
            p.fillCircle(hairBuf[o + 2], hairBuf[o + 3], 1.1f, hairCore, .8f)
        }
        p.setAdditive(false)
    }

    /** ref5: thin HUD circle behind the head with segmented cyan ticks on the right and a violet arc lower left. */
    private fun drawHudRing(p: ContinuumPainter, seconds: Float, motion: Boolean, radius: Float = 150f) {
        val r = radius; val cy = -10f
        p.setAdditive(true)
        p.strokeOval(-r, cy - r, r, cy + r, .7f, hudFill, .22f)
        p.strokeOval(-r - 6f, cy - r - 6f, r + 6f, cy + r + 6f, 3f, hudFill, .04f)
        val drift = if (motion) seconds * 2f else 0f
        for (k in 0 until 9) {    // right-side segments
            val a = -48f + k * 11f + drift % 11f
            p.strokeArc(-r, cy - r, r, cy + r, a, 6f, 3.2f, hudFill, if (k % 3 == 1) .85f else .45f)
        }
        p.strokeArc(-r, cy - r, r, cy + r, 150f, 22f, 3f, hudFill, .55f)
        p.strokeArc(-r, cy - r, r, cy + r, 128f, 9f, 3f, hudMagenta, .7f)
        p.setAdditive(false)
    }

    /** ref5 face-tracking brackets (User Recognized): four cyan corners framing eyes to mouth. */
    private fun drawBrackets(p: ContinuumPainter) {
        val l = -52f + sinYaw * 30f; val r = 66f + sinYaw * 30f; val t = -46f; val b = 62f; val k = 14f
        p.setAdditive(true)
        for (pass in 0..1) {
            val w = if (pass == 0) 4.5f else 1.6f; val a = if (pass == 0) .18f else .95f
            p.drawLine(l, t, l + k, t, w, hudFill, a); p.drawLine(l, t, l, t + k, w, hudFill, a)
            p.drawLine(r, t, r - k, t, w, hudFill, a); p.drawLine(r, t, r, t + k, w, hudFill, a)
            p.drawLine(l, b, l + k, b, w, hudFill, a); p.drawLine(l, b, l, b - k, w, hudFill, a)
            p.drawLine(r, b, r - k, b, w, hudFill, a); p.drawLine(r, b, r, b - k, w, hudFill, a)
        }
        p.setAdditive(false)
    }

    /** Muster C breathing: 0..1 over a 4.5 s cycle (cosmetic). */
    internal fun breath(seconds: Float): Float = .5f - .5f * cos(seconds * (2f * PI.toFloat() / 4.5f))

    // fixed sparkle field around the bust (no random per frame)
    private val sparkX = FloatArray(36) { i -> sin(i * 2.399f) * (95f + (i * 37 % 50)) }
    private val sparkY = FloatArray(36) { i -> -40f + cos(i * 2.399f) * (85f + (i * 53 % 40)) }

    /** Curved fibres continue from the neck over the shoulders, using one reusable path. */
    private fun drawBodyFibres(p: ContinuumPainter, pulse: Float) {
        for (i in 0 until 30) {
            val t = -1f + i * (2f / 29f)
            val spread = abs(t)
            bodyFibre.reset().moveTo(t * 21f, 80f)
                .cubicTo(t * 20f, 101f, t * 23f, 118f, t * (34f + spread * 28f), 138f)
                .cubicTo(t * (64f + spread * 32f), 153f, t * 119f, 164f, t * 134f, 181f)
            p.strokePath(bodyFibre, 1.7f, ref5Rim, .07f * pulse)
            p.strokePath(bodyFibre, .38f, ref5Rim, (.48f - spread * .16f) * pulse.coerceAtMost(1.3f))
        }
        for (i in 0 until 9) {
            val y = 131f + i * 5.6f
            val w = 46f + i * 10f
            p.strokeArc(-w, y - 10f - i * .7f, w, y + 10f + i * .7f,
                196f, 148f, .35f, ref5Rim, (.3f - i * .025f) * pulse.coerceAtMost(1.3f))
        }
    }

    fun draw(p: ContinuumPainter, width: Float, height: Float, pose: AvatarPose, seconds: Float, motion: Boolean,
             wake: Float = 1f) {
        if (width <= 0f || height <= 0f) return
        // The ref5 portrait sits beside the user card. It must not reserve the older scene's 300-unit orbits.
        val portrait = ref5Hud && !showOrbits
        val scale = if (portrait) min(width / 215f, height / 330f) else min(width / 300f, height / 345f)
        val halfStage = width / (2f * scale)
        val speak = if (motion) pose.speakEnergy.coerceIn(0f, 1f) else 0f
        val jaw = if (motion) pose.jawOpen.coerceIn(0f, 1f) else 0f
        val pulse = 1f + speak * .9f
        bloom = (.45f + speak * .55f) * (if (motion) 1f else .6f)
        p.save()
        // VIDEO-SPEC C: idle float, 4 s sine loop, Y +-2.5 px (screen units)
        val float = if (motion) JarvisMotion.idleOffset((seconds * 1000f).toLong()) else 0f
        val originY = if (portrait) height * .5f - 40f * scale else height * .385f
        p.translate(width / 2f, originY + float)
        p.scale(scale, scale)

        val breathe = if (motion) breath(seconds) else .5f
        if (ref5Hud) drawHudRing(p, seconds, motion, if (portrait) min(150f, halfStage - 14f) else 150f)
        // background light pools (JarvisNeon halo, design-system colours)
        JarvisNeon.halo(p, 0f, -10f, if (portrait) min(150f, halfStage - 2f) else 150f,
            JarvisDesign.Colors.cyan, .20f + speak * .08f)
        val violetX = if (portrait) min(40f, halfStage * .2f) else 40f
        JarvisNeon.halo(p, violetX, -30f, if (portrait) min(120f, halfStage - violetX - 4f) else 120f,
            JarvisDesign.Colors.violet, .16f)
        // projection platform under the bust
        val bodyWidthScale = if (portrait) min(1f, (halfStage - 8f) / 136f) else 1f
        JarvisNeon.ring(p, 0f, 182f, 136f * bodyWidthScale, 16f, 1.2f, glow = JarvisDesign.Glow.NORMAL)
        JarvisNeon.ring(p, 0f, 182f, 104f * bodyWidthScale, 11f, .8f, glow = JarvisDesign.Glow.SUBTLE)
        if (showOrbits) drawOrbits(p, front = false, seconds, pulse, motion)

        // neck and shoulders rise slightly with the breath
        p.save(); p.translate(0f, -breathe * 1.4f); p.scale((1f + breathe * .012f) * bodyWidthScale, 1f)
        // neck and shoulders: translucent glass with flowing contour lines
        p.fillPath(body, bodyGlass)
        p.setAdditive(true)
        if (ref5Hud) drawBodyFibres(p, pulse) else {
            for (i in 0 until 7) {   // legacy neck rings
                val y = 84f + i * 6.5f
                p.strokeArc(-22f, y - 4f, 22f, y + 4f, 10f, 160f, .55f, edge, .45f)
            }
            for (i in 0 until 10) {
                val y = 128f + i * 5.6f
                val w = 40f + i * 10f
                p.strokeArc(-w, y - 10f - i * .7f, w, y + 10f + i * .7f, 196f, 148f, .6f, edge, (.6f - i * .05f) * pulse.coerceAtMost(1.3f))
            }
            for (i in -6..6) {
                val x = i * 4f
                p.drawLine(x * .9f, 80f, x * (2.2f + abs(i) * .55f), 178f, .45f, edge, .26f - abs(i) * .02f)
            }
        }
        p.strokePath(body, .9f, edge, .55f)
        p.setAdditive(false)
        p.restore()

        // head pose: gaze turns the head, speech adds a small nod/turn, idle a slow breath
        val idle = if (motion) sin(seconds * .7f) else 0f
        val nod = if (motion) speak * (sin(seconds * 5.1f) * 2.6f + sin(seconds * 8.3f) * 1.1f) else 0f
        // ref5: head turned slightly to the viewer's right (+9 deg rest yaw)
        yawRad = (((if (ref5Hud) 9f else 0f) + pose.gazeX.coerceIn(-1f, 1f) * 12f + idle * 1.2f + speak * sin(seconds * 2.3f) * 2f) * PI / 180f).toFloat()
        val pitchRad = ((-pose.gazeY.coerceIn(-1f, 1f) * 7f + nod) * PI / 180f).toFloat()
        cosYaw = cos(yawRad); sinYaw = sin(yawRad); cosPitch = cos(pitchRad); sinPitch = sin(pitchRad)
        p.save()
        p.translate(0f, -breathe * .8f)
        p.rotate(idle * .6f, 0f, 85f)
        if (ref5Hud) drawHair(p, seconds, motion, speak, if (portrait) halfStage else Float.POSITIVE_INFINITY)

        // glass head volume and glow aura, shifted with the turn
        p.save(); p.translate(sinYaw * 10f, -sinPitch * 12f)
        p.setAdditive(true)
        val rim = if (ref5Hud) ref5Rim else edge
        p.strokePath(face, 16f, rim, .045f * pulse)
        p.strokePath(face, 6f, rim, .09f * pulse)
        if (ref5Hud) p.strokePath(face, .7f, rim, .58f)
        p.setAdditive(false)
        p.fillPath(face, glass)
        p.restore()
        drawEars(p, pulse)

        // Ref5 fibre surface; the earlier wireframe remains available for its original video scenes.
        val flow = if (motion) (seconds * .18f) % 1f else .35f
        if (ref5Hud) buildFibres(jaw, flow) else buildMesh(jaw, flow)
        for (b in 0 until 12) if (b % 4 >= 2) JarvisNeon.bloomSegments(p, buckets[b], bucketCount[b],
            if (ref5Hud) .46f else .6f, hueColors[b / 4], bloom)
        p.setAdditive(true)
        for (b in 0 until 12) {
            val n = bucketCount[b]
            if (n == 0) continue
            val level = b % 4
            val strokeWidth = if (ref5Hud) .25f + level * .10f else .42f + level * .16f
            p.strokeSegments(buckets[b], n, strokeWidth, hueFills[b / 4], (levelAlpha[level] * (.82f + speak * .45f)).coerceAtMost(1f))
            if (speak > .02f && level >= 2) p.strokeSegments(buckets[b], n, .5f + level * .2f, speakCyan, speak * .35f)
        }
        drawVolume(p, speak)
        // inner neural light (the mind behind the face), pulses with the voice
        val mind = .18f + speak * .25f
        surface(0f, -62f, 0f)
        JarvisNeon.halo(p, px, py, 42f, JarvisDesign.Colors.violet, mind * .6f)

        // features
        val eyelid = (if (motion) eyeOpening(seconds) else 1f) * wake.coerceIn(0f, 1f)
        drawEye(p, -28f, eyelid, pose)
        drawEye(p, 28f, eyelid, pose)
        drawBrows(p, pose)
        drawNose(p)
        drawLips(p, pose, jaw, speak)
        p.setAdditive(false)
        if (ref5Hud) drawBrackets(p)
        p.restore()

        if (showOrbits) drawOrbits(p, front = true, seconds, pulse, motion)
        if (motion) drawBurst(p, seconds)
        // sparkles
        p.setAdditive(true)
        for (i in sparkX.indices) {
            if (portrait && abs(sparkX[i]) > halfStage - 3f) continue
            val tw = if (motion) .5f + .5f * sin(seconds * (1.1f + (i % 5) * .23f) + i) else .7f
            p.fillCircle(sparkX[i], sparkY[i], if (i % 7 == 0) 1.3f else .7f, if (i % 3 == 0) violet else cyan, tw * .8f)
        }
        p.setAdditive(false)
        p.restore()
    }

    /** Soft blob at a head-surface point; [lit] additive light, otherwise a recess shadow. */
    private fun blob(p: ContinuumPainter, theta: Float, y: Float, rx: Float, ry: Float, fill: Fill, alpha: Float) {
        surface(theta, y, 0f)
        p.save(); p.translate(px, py); p.scale(rx * (.55f + .45f * cos(theta + yawRad).coerceAtLeast(0f)), ry)
        p.fillCircle(0f, 0f, 1f, fill, alpha); p.restore()
    }

    // Volumetric face (ref1): lit forehead, nose ridge, cheekbones, chin; dark eye sockets and recesses.
    private fun drawVolume(p: ContinuumPainter, speak: Float) {
        p.setAdditive(false)
        blob(p, -.47f, -20f, 21f, 12f, softShadow, .9f); blob(p, .47f, -20f, 21f, 12f, softShadow, .9f)
        blob(p, -.7f, 40f, 13f, 20f, softShadow, .35f); blob(p, .7f, 40f, 13f, 20f, softShadow, .35f)
        blob(p, 0f, 31f, 13f, 4.5f, softShadow, .45f)
        blob(p, 0f, 68f, 14f, 4f, softShadow, .4f)
        p.setAdditive(true)
        val k = .8f + speak * .4f
        blob(p, -.15f, -66f, 44f, 30f, softLight, .32f * k)
        blob(p, .45f, -60f, 30f, 26f, softViolet, .22f * k)
        if (ref5Hud) {   // ref5: violet cranium glow and the bright magenta-violet light on the left temple
            blob(p, -.5f, -70f, 56f, 38f, softViolet, .6f * k)
            blob(p, -.85f, -22f, 16f, 10f, softViolet, 1f * k)
            blob(p, -.85f, -22f, 6f, 4f, softLight, .5f * k)
        }
        blob(p, 0f, 2f, 6.5f, 25f, softLight, .45f * k)
        blob(p, -.62f, 5f, 17f, 9f, softLight, .3f * k); blob(p, .62f, 5f, 17f, 9f, softViolet, .28f * k)
        blob(p, 0f, 82f, 15f, 8f, softLight, .3f * k)
        blob(p, 0f, 45f, 14f, 4f, softLight, .22f * k)
    }

    private fun drawEars(p: ContinuumPainter, pulse: Float) {
        for (side in intArrayOf(-1, 1)) {
            val theta = side * 1.5f
            val facing = cos(theta + yawRad)
            if (facing < -.35f) continue
            surface(theta, -8f, 0f)
            p.save(); p.translate(px + side * 2f, py); p.scale(side.toFloat() * (.55f + .45f * (1f - abs(sin(theta + yawRad)) * .4f)), 1f)
            p.fillPath(ear, glass, .8f)
            p.setAdditive(true)
            p.strokePath(ear, 4f, edge, .08f * pulse)
            p.strokePath(ear, .8f, edge, .7f)
            p.strokePath(earInner, .6f, edge, .5f)
            p.setAdditive(false)
            p.restore()
        }
    }

    private fun drawEye(p: ContinuumPainter, x: Float, opening: Float, pose: AvatarPose) {
        surface((x / 60f), -20f, 0f)
        val ex = px; val ey = py
        val w = 12f * cosYaw.coerceAtLeast(.6f)
        val h = 4.6f * opening
        if (ref5Hud) {
            drawRef5Eye(p, ex, ey, w, h, opening, pose, x < 0f)
            return
        }
        p.fillOval(ex - w, ey - h, ex + w, ey + h, dark, .85f)
        p.strokeArc(ex - w, ey - 5.6f, ex + w, ey + 5.6f, 195f, 150f, .9f, if (x < 0) cyan else violet, .9f) // lid crease
        if (h > .6f) {
            p.strokeOval(ex - w, ey - h, ex + w, ey + h, .8f, edge, .85f)
            val ix = ex + pose.gazeX.coerceIn(-1f, 1f) * 5.5f
            val iy = ey - pose.gazeY.coerceIn(-1f, 1f) * 2.2f
            p.save(); p.translate(ix, iy); p.scale(7f, 7f * opening)
            p.fillCircle(0f, 0f, 1f, irisGlow, if (ref5Hud) .35f else .7f)
            p.restore()
            // VIDEO-SPEC: eyes without pupils, cyan/white light; the light centre still follows the gaze
            p.fillOval(ex - w * .85f, ey - h * .8f, ex + w * .85f, ey + h * .8f, eyeLight, if (ref5Hud) .22f else .6f)
            JarvisNeon.bloom(p, ix, iy, 6f, 0xff00ffff.toInt(), bloom * .5f)
        } else p.drawLine(ex - w, ey, ex + w, ey, 1.1f, edge, .9f)
    }

    /** The same tracked eye and blink opening, with an almond aperture and shaded upper lid instead of a flat oval. */
    private fun drawRef5Eye(p: ContinuumPainter, ex: Float, ey: Float, w: Float, h: Float,
                           opening: Float, pose: AvatarPose, left: Boolean) {
        val rim = if (left) violet else cyan
        p.save(); p.translate(ex, ey)
        upperLid.reset().moveTo(-w, h * .12f)
            .cubicTo(-w * .45f, -h * 1.3f, w * .55f, -h * 1.2f, w, -h * .15f)
        lowerLid.reset().moveTo(-w, h * .12f)
            .cubicTo(-w * .4f, h * .85f, w * .55f, h * .9f, w, -h * .15f)
        lidVolume.reset().moveTo(-w, h * .12f)
            .cubicTo(-w * .55f, -9f, w * .55f, -9f, w, -2f)
            .cubicTo(w * .55f, -h * 1.2f, -w * .45f, -h * 1.3f, -w, h * .12f).close()
        p.setAdditive(false)
        p.fillPath(lidVolume, lidGlass)
        if (h > .6f) {
            eyeAperture.reset().moveTo(-w, h * .12f)
                .cubicTo(-w * .45f, -h * 1.3f, w * .55f, -h * 1.2f, w, -h * .15f)
                .cubicTo(w * .55f, h * .9f, -w * .4f, h * .85f, -w, h * .12f).close()
            p.fillPath(eyeAperture, eyeDepth, .94f)
            p.setAdditive(true)
            p.save(); p.translate(pose.gazeX.coerceIn(-1f, 1f) * 5.5f, -pose.gazeY.coerceIn(-1f, 1f) * 2.2f)
            p.scale(5f, 5.5f * opening)
            p.fillCircle(0f, 0f, 1f, irisGlow, .3f)
            p.restore()
            p.strokePath(upperLid, 3.2f, rim, .13f)
            p.strokePath(upperLid, .7f, rim, .78f)
            p.strokePath(lowerLid, .55f, rim, .4f)
        } else {
            p.setAdditive(true)
            p.drawLine(-w, 0f, w, 0f, .8f, rim, .8f)
        }
        p.strokeArc(-w * 1.08f, -8f, w * 1.08f, 4f, 202f, 132f, .6f, rim, .32f)
        p.restore()
    }

    private fun drawBrows(p: ContinuumPainter, pose: AvatarPose) {
        val lift = when (pose.expression) {
            AvatarExpression.ATTENTIVE -> -3f
            AvatarExpression.CONCERNED -> 3f
            AvatarExpression.FOCUSED -> 1.5f
            AvatarExpression.CURIOUS -> -2f
            AvatarExpression.NEUTRAL, AvatarExpression.WARM -> 0f
        }
        for (side in intArrayOf(-1, 1)) {
            surface(side * .2f, -37f, 0f); val x0 = px; val y0 = py
            surface(side * .75f, -35f + lift * .5f, 0f); val x1 = px; val y1 = py + lift
            if (ref5Hud) {
                val rim = if (side < 0) violet else cyan
                browContour.reset().moveTo(x0, y0 + lift * .3f)
                    .quadTo((x0 + x1) * .5f, (y0 + y1) * .5f - 3f, x1, y1)
                p.strokePath(browContour, 5f, rim, .09f)
                p.strokePath(browContour, 1f, rim, .64f)
            } else {
                p.drawLine(x0, y0 + lift * .3f, x1, y1, 3.5f, if (side < 0) cyan else violet, .12f)
                p.drawLine(x0, y0 + lift * .3f, x1, y1, 1.2f, if (side < 0) cyan else violet, .8f)
            }
        }
    }

    private fun drawNose(p: ContinuumPainter) {
        surface(0f, -8f, 0f); val x0 = px; val y0 = py
        surface(0f, 18f, 0f); val x1 = px; val y1 = py
        p.drawLine(x0, y0, x1, y1, .8f, cyan, .45f)
        for (side in intArrayOf(-1, 1)) {
            surface(side * .2f, 24f, 0f)
            p.drawLine(x1, y1 + 2f, px, py, .9f, edge, .6f)
        }
    }

    private fun drawLips(p: ContinuumPainter, pose: AvatarPose, jaw: Float, speak: Float) {
        val rounded = pose.visemeWeight > 0f && (pose.viseme == Viseme.O || pose.viseme == Viseme.U || pose.viseme == Viseme.CH)
        val wide = pose.visemeWeight > 0f && (pose.viseme == Viseme.E || pose.viseme == Viseme.I || pose.viseme == Viseme.SS)
        val closed = pose.visemeWeight > 0f && (pose.viseme == Viseme.PP)
        val half = if (rounded) 11f else if (wide) 22f else 18f
        val open = if (closed) 0f else jaw * 11f
        surface(0f, 52f, jaw); val mx = px; val my = py
        // mouth opening
        if (open > .4f) {
            p.setAdditive(false)
            p.fillOval(mx - half * .8f, my - open * .35f, mx + half * .8f, my + open, dark, .95f)
            p.setAdditive(true)
        }
        upperLip.reset().moveTo(mx - half, my).quadTo(mx - half * .5f, my - 5f, mx - 2.5f, my - 3.2f)
            .quadTo(mx, my - 1.8f, mx + 2.5f, my - 3.2f).quadTo(mx + half * .5f, my - 5f, mx + half, my)
            .quadTo(mx, my - open * .35f + .6f, mx - half, my)
        lowerLip.reset().moveTo(mx - half, my).quadTo(mx, my + open + 1f, mx + half, my)
            .quadTo(mx, my + open + 7.5f, mx - half, my)
        if (speak > .02f) { p.strokePath(upperLip, 7f, speakCyan, .18f * speak); p.strokePath(lowerLip, 7f, speakCyan, .18f * speak) }
        p.strokePath(upperLip, 3.5f, edge, .14f)
        p.strokePath(upperLip, 1.05f, edge, .92f)
        p.strokePath(lowerLip, 3.5f, edge, .14f)
        p.strokePath(lowerLip, 1.05f, edge, .85f)
    }

    private companion object {
        const val SEG_CAPACITY = 4 * 2400
        const val FIBRE_ROWS = 88
        const val FIBRE_COLS = 44
        const val HAIR_N = 28
        const val HAIR_PTS = 14
        const val BURST_ICONS = 9
        const val BURST_S = 1.5f
        const val BURST_STAGGER = .05f
    }
}
