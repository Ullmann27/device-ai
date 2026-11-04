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
 * Holographic wireframe bust (reference ref1/ref4 look): a procedural 3-D head surface drawn as dense flowing
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
    private val glass = Fill.Radial(-12f, -30f, 125f,
        intArrayOf(0xc02a78c8.toInt(), 0xb0163e8c.toInt(), 0xa02a1a78.toInt()), floatArrayOf(0f, .55f, 1f))
    private val bodyGlass = Fill.Linear(0f, 80f, 0f, 175f, intArrayOf(0x90205a9c.toInt(), 0x50182a70, 0x00000000))
    private val irisGlow = Fill.Radial(0f, 0f, 1f, intArrayOf(0xff9ff6ff.toInt(), 0x6640c8ff, 0x00000000), floatArrayOf(0f, .35f, 1f))

    // Static silhouettes (allocated once). Face units: crown y=-100, chin y=92, half width ~66.
    private val face = VectorPath().moveTo(0f, -102f)
        .cubicTo(-50f, -104f, -72f, -74f, -70f, -34f)
        .cubicTo(-69f, -8f, -64f, 22f, -54f, 44f)
        .cubicTo(-45f, 66f, -27f, 89f, 0f, 93f)
        .cubicTo(27f, 89f, 45f, 66f, 54f, 44f)
        .cubicTo(64f, 22f, 69f, -8f, 70f, -34f)
        .cubicTo(72f, -74f, 50f, -104f, 0f, -102f).close()
    private val body = VectorPath().moveTo(-22f, 76f).cubicTo(-23f, 96f, -24f, 112f, -30f, 124f)
        .cubicTo(-70f, 132f, -118f, 138f, -136f, 182f).lineTo(136f, 182f)
        .cubicTo(118f, 138f, 70f, 132f, 30f, 124f).cubicTo(24f, 112f, 23f, 96f, 22f, 76f).close()
    private val upperLip = VectorPath()
    private val lowerLip = VectorPath()

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
    private val profileY = floatArrayOf(-101f, -92f, -75f, -55f, -35f, -15f, 5f, 25f, 45f, 62f, 78f, 88f, 93f)
    private val profileW = floatArrayOf(4f, 36f, 57f, 67f, 70f, 68f, 64f, 59f, 52f, 42f, 29f, 15f, 2f)

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
        shade = if (front) ((relief(x - 3f, y - 3f, jaw) - relief(x + 3f, y + 3f, jaw)) * .05f +
            (if (rel < -2f) rel * .05f else 0f)) * c else 0f
        project(x, yy, z)
    }

    // Segment buckets: 3 hues (cyan/blue/violet by screen x) x 4 brightness levels.
    private val hueColors = intArrayOf(0xff00ffff.toInt(), 0xff0088ff.toInt(), 0xff9a6bff.toInt())
    private val hueFills = arrayOf(Fill.Solid(0xff6fe9ff.toInt()), Fill.Solid(0xff7aa6ff.toInt()), Fill.Solid(0xffb48cff.toInt()))
    private val levelAlpha = floatArrayOf(.3f, .5f, .72f, 1f)
    private val buckets = Array(12) { FloatArray(SEG_CAPACITY) }
    private val bucketCount = IntArray(12)

    private fun addSegment(x0: Float, y0: Float, x1: Float, y1: Float, bright: Float) {
        val mx = (x0 + x1) * .5f
        val hue = if (mx < -18f) 0 else if (mx > 18f) 2 else 1
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

    private fun buildMesh(jaw: Float, flow: Float) {
        bucketCount.fill(0)
        // horizontal contour lines (the flowing "scan lines" of the references)
        val rows = 60
        val cols = 40
        for (r in 0 until rows) {
            val y = -99f + r * (191f / (rows - 1))
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
    // Slanted orbit rings with bright nodes around the cranium. Creative review: 3 (default); VIDEO-SPEC video 1 shows 2.
    private val allOrbits = arrayOf(
        Orbit(-58f, 108f, 24f, 13f, .16f, 0xff00e5ff.toInt(), 1.5f),
        Orbit(-58f, 108f, 24f, -13f, -.13f, 0xff9a6bff.toInt(), 1.5f),
        Orbit(-30f, 122f, 30f, 24f, .11f, 0xff3aa8ff.toInt(), 1.2f)
    )
    /** Number of rings (1..3). */
    var ringCount = 3
        set(value) { field = value.coerceIn(1, allOrbits.size) }
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
                JarvisNeon.bloomPoint(p, nx, ny, 9f * pulse, o.color, k * bloom)
                p.fillCircle(nx, ny, 4.5f * pulse, fill, .22f * k)
                p.fillCircle(nx, ny, 1.9f, orbitCore, .95f * k)
            }
            p.restore()
        }
        p.setAdditive(false)
    }

    // fixed sparkle field around the bust (no random per frame)
    private val sparkX = FloatArray(36) { i -> sin(i * 2.399f) * (95f + (i * 37 % 50)) }
    private val sparkY = FloatArray(36) { i -> -40f + cos(i * 2.399f) * (85f + (i * 53 % 40)) }

    fun draw(p: ContinuumPainter, width: Float, height: Float, pose: AvatarPose, seconds: Float, motion: Boolean,
             wake: Float = 1f) {
        if (width <= 0f || height <= 0f) return
        val scale = min(width / 300f, height / 345f)
        val speak = if (motion) pose.speakEnergy.coerceIn(0f, 1f) else 0f
        val jaw = if (motion) pose.jawOpen.coerceIn(0f, 1f) else 0f
        val pulse = 1f + speak * .9f
        bloom = (.45f + speak * .55f) * (if (motion) 1f else .6f)
        p.save()
        // VIDEO-SPEC C: idle float, 4 s sine loop, Y +-2.5 px (screen units)
        val float = if (motion) JarvisMotion.idleOffset((seconds * 1000f).toLong()) else 0f
        p.translate(width / 2f, height * .385f + float)
        p.scale(scale, scale)

        // background light pools (JarvisNeon halo, design-system colours)
        JarvisNeon.halo(p, 0f, -10f, 150f, JarvisDesign.Colors.cyan, .20f + speak * .08f)
        JarvisNeon.halo(p, 40f, -30f, 120f, JarvisDesign.Colors.violet, .16f)
        // projection platform under the bust
        JarvisNeon.ring(p, 0f, 182f, 136f, 16f, 1.2f, glow = JarvisDesign.Glow.NORMAL)
        JarvisNeon.ring(p, 0f, 182f, 104f, 11f, .8f, glow = JarvisDesign.Glow.SUBTLE)
        drawOrbits(p, front = false, seconds, pulse, motion)

        // neck and shoulders: translucent glass with flowing contour lines
        p.fillPath(body, bodyGlass)
        p.setAdditive(true)
        for (i in 0 until 7) {   // neck rings
            val y = 84f + i * 6.5f
            p.strokeArc(-22f, y - 4f, 22f, y + 4f, 10f, 160f, .55f, edge, .45f)
        }
        for (i in 0 until 10) {  // shoulder / chest contours
            val y = 128f + i * 5.6f
            val w = 40f + i * 10f
            p.strokeArc(-w, y - 10f - i * .7f, w, y + 10f + i * .7f, 196f, 148f, .6f, edge, (.6f - i * .05f) * pulse.coerceAtMost(1.3f))
        }
        for (i in -6..6) {       // flow lines down the torso
            val x = i * 4f
            p.drawLine(x * .9f, 80f, x * (2.2f + abs(i) * .55f), 178f, .45f, edge, .26f - abs(i) * .02f)
        }
        p.strokePath(body, .9f, edge, .55f)
        p.setAdditive(false)

        // head pose: gaze turns the head, speech adds a small nod/turn, idle a slow breath
        val idle = if (motion) sin(seconds * .7f) else 0f
        val nod = if (motion) speak * (sin(seconds * 5.1f) * 2.6f + sin(seconds * 8.3f) * 1.1f) else 0f
        yawRad = ((pose.gazeX.coerceIn(-1f, 1f) * 12f + idle * 1.2f + speak * sin(seconds * 2.3f) * 2f) * PI / 180f).toFloat()
        val pitchRad = ((-pose.gazeY.coerceIn(-1f, 1f) * 7f + nod) * PI / 180f).toFloat()
        cosYaw = cos(yawRad); sinYaw = sin(yawRad); cosPitch = cos(pitchRad); sinPitch = sin(pitchRad)
        p.save()
        p.rotate(idle * .6f, 0f, 85f)

        // glass head volume and glow aura, shifted with the turn
        p.save(); p.translate(sinYaw * 10f, -sinPitch * 12f)
        p.setAdditive(true)
        p.strokePath(face, 16f, edge, .045f * pulse)
        p.strokePath(face, 6f, edge, .09f * pulse)
        p.setAdditive(false)
        p.fillPath(face, glass)
        p.restore()

        // wireframe surface
        val flow = if (motion) (seconds * .18f) % 1f else .35f
        buildMesh(jaw, flow)
        for (b in 0 until 12) if (b % 4 >= 2) JarvisNeon.bloomSegments(p, buckets[b], bucketCount[b], .6f, hueColors[b / 4], bloom)
        p.setAdditive(true)
        for (b in 0 until 12) {
            val n = bucketCount[b]
            if (n == 0) continue
            val level = b % 4
            p.strokeSegments(buckets[b], n, .42f + level * .16f, hueFills[b / 4], (levelAlpha[level] * (.82f + speak * .45f)).coerceAtMost(1f))
            if (speak > .02f && level >= 2) p.strokeSegments(buckets[b], n, .5f + level * .2f, speakCyan, speak * .35f)
        }
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
        p.restore()

        drawOrbits(p, front = true, seconds, pulse, motion)
        // sparkles
        p.setAdditive(true)
        for (i in sparkX.indices) {
            val tw = if (motion) .5f + .5f * sin(seconds * (1.1f + (i % 5) * .23f) + i) else .7f
            p.fillCircle(sparkX[i], sparkY[i], if (i % 7 == 0) 1.3f else .7f, if (i % 3 == 0) violet else cyan, tw * .8f)
        }
        p.setAdditive(false)
        p.restore()
    }

    private fun drawEye(p: ContinuumPainter, x: Float, opening: Float, pose: AvatarPose) {
        surface((x / 60f), -20f, 0f)
        val ex = px; val ey = py
        val w = 15f * cosYaw.coerceAtLeast(.6f)
        val h = 6.2f * opening
        p.fillOval(ex - w, ey - h, ex + w, ey + h, dark, .85f)
        p.strokeArc(ex - w, ey - 6.2f, ex + w, ey + 6.2f, 195f, 150f, .9f, if (x < 0) cyan else violet, .9f) // lid crease
        if (h > .6f) {
            p.strokeOval(ex - w, ey - h, ex + w, ey + h, .8f, edge, .85f)
            val ix = ex + pose.gazeX.coerceIn(-1f, 1f) * 5.5f
            val iy = ey - pose.gazeY.coerceIn(-1f, 1f) * 2.2f
            p.save(); p.translate(ix, iy); p.scale(9f, 9f * opening)
            p.fillCircle(0f, 0f, 1f, irisGlow, .7f)
            p.restore()
            // VIDEO-SPEC: eyes without pupils, cyan/white light; the light centre still follows the gaze
            p.fillOval(ex - w * .85f, ey - h * .8f, ex + w * .85f, ey + h * .8f, eyeLight, .75f)
            JarvisNeon.bloomPoint(p, ix, iy, 11f, 0xff00ffff.toInt(), bloom * .8f)
        } else p.drawLine(ex - w, ey, ex + w, ey, 1.1f, edge, .9f)
    }

    private fun drawBrows(p: ContinuumPainter, pose: AvatarPose) {
        val lift = when (pose.expression) {
            AvatarExpression.ATTENTIVE -> -3f
            AvatarExpression.CONCERNED -> 3f
            AvatarExpression.FOCUSED -> 1.5f
            AvatarExpression.CURIOUS -> -2f
            AvatarExpression.NEUTRAL -> 0f
        }
        for (side in intArrayOf(-1, 1)) {
            surface(side * .2f, -37f, 0f); val x0 = px; val y0 = py
            surface(side * .75f, -35f + lift * .5f, 0f); val x1 = px; val y1 = py + lift
            p.drawLine(x0, y0 + lift * .3f, x1, y1, 3.5f, if (side < 0) cyan else violet, .12f)
            p.drawLine(x0, y0 + lift * .3f, x1, y1, 1.2f, if (side < 0) cyan else violet, .8f)
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
    }
}
