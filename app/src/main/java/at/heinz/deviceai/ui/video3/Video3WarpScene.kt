package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.Argb
import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisNeon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Warp C_WARP (Video 3, 12–15 s), played by Kern's hook after a real SECURITY_FINDING alarm only.
 * 1. bright bloom flash, 2. burst: cards 0 → >500 % through the camera (Muster A, exponential ease-in 0,8 s),
 * 3. fast LINEAR flight through a tunnel of document cards with motion-blur streaks.
 * Fixed element counts, all geometry precomputed at construction; [render] allocates nothing of its own.
 */
class Video3WarpScene(seed: Int = 12) {
    private val burstAngle = FloatArray(BURST_CARDS) { 2f * PI.toFloat() * (it + Video3Curves.unit(seed + it)) / BURST_CARDS }
    private val burstDist = FloatArray(BURST_CARDS) { .08f + .22f * Video3Curves.unit(seed * 7 + it) }
    private val tunnelAngle = FloatArray(TUNNEL_CARDS) { 2f * PI.toFloat() * Video3Curves.unit(seed * 13 + it) }
    private val tunnelZ0 = FloatArray(TUNNEL_CARDS) { Z_NEAR + (Z_FAR - Z_NEAR) * Video3Curves.unit(seed * 17 + it) }
    private val tunnelFolder = BooleanArray(TUNNEL_CARDS) { Video3Curves.unit(seed * 19 + it) < FOLDER_SHARE }
    private val streakAngle = FloatArray(STREAKS) { 2f * PI.toFloat() * Video3Curves.unit(seed * 23 + it) }
    private val streakZ0 = FloatArray(STREAKS) { Z_NEAR + (Z_FAR - Z_NEAR) * Video3Curves.unit(seed * 29 + it) }
    private val streakSegs = FloatArray(STREAKS * 4)
    private val docLineSegs = FloatArray(4 * 3)
    private val cyan = Fill.Solid(Colors.cyan)
    private val white = Fill.Solid(Colors.activeWhite)
    private val folderFill = Fill.Solid(Argb.withAlpha(Colors.warn, .55f))
    private val cardFill = Fill.Solid(Argb.withAlpha(Colors.backgroundBottom, .7f))
    private val rimFill = Fill.Solid(Colors.rim)
    private var bgW = -1f; private var bgH = -1f
    private var backdrop: Fill = Fill.Solid(Colors.background)

    fun render(p: ContinuumPainter, width: Float, height: Float, seconds: Float, options: Video3Options = Video3Options()): Boolean {
        if (width <= 0f || height <= 0f) return false
        if (width != bgW || height != bgH) {
            bgW = width; bgH = height
            backdrop = Fill.Radial(width / 2f, height / 2f, max(width, height) * .7f,
                intArrayOf(Colors.backgroundBottom, Colors.background))
        }
        p.fillRect(0f, 0f, width, height, backdrop)
        val cx = width / 2f; val cy = height / 2f
        val reach = max(width, height) * .5f
        if (options.reducedMotion) {
            drawTunnel(p, cx, cy, reach, REDUCED_FRAME_S, options.glow, still = true)
            return true
        }
        val t = seconds.coerceAtLeast(0f)
        if (t >= TUNNEL_START_S) drawTunnel(p, cx, cy, reach, t - TUNNEL_START_S, options.glow, still = false)
        if (t < BURST_START_S + Video3Curves.WARP_BURST_S) drawBurst(p, cx, cy, reach, t - BURST_START_S, options.glow)
        if (t < FLASH_S) drawFlash(p, width, height, cx, cy, t, options.glow)
        return true
    }

    private fun drawFlash(p: ContinuumPainter, w: Float, h: Float, cx: Float, cy: Float, t: Float, glow: Boolean) {
        val k = 1f - t / FLASH_S
        p.setAdditive(true)
        p.fillRect(0f, 0f, w, h, white, FLASH_ALPHA * k * k)
        p.setAdditive(false)
        if (glow) JarvisNeon.bloom(p, cx, cy, min(w, h) * .45f, Colors.activeWhite, 2.2f * k)
    }

    /** Muster A burst: scale 0 → BURST_MAX_SCALE (> 500 %), exponential ease-in over 0,8 s, fading near the camera. */
    private fun drawBurst(p: ContinuumPainter, cx: Float, cy: Float, reach: Float, t: Float, glow: Boolean) {
        if (t < 0f) return
        val e = Video3Curves.easeInExpo((t / Video3Curves.WARP_BURST_S).coerceIn(0f, 1f))
        val s = BURST_MAX_SCALE * e
        if (s <= .01f) return
        val alpha = (1f - e).coerceIn(0f, 1f).let { if (s < 1f) s else it * 1.6f }.coerceIn(0f, 1f)
        for (i in 0 until BURST_CARDS) {
            val d = reach * burstDist[i] * (1f + s * 1.5f)
            drawCard(p, cx + cos(burstAngle[i]) * d, cy + sin(burstAngle[i]) * d * .75f, CARD_W * s, CARD_H * s,
                alpha, i % 3 == 0, glow)
        }
    }

    /** Linear flight: every card moves towards the camera at constant speed; perspective scale = Z_NEAR / z. */
    private fun drawTunnel(p: ContinuumPainter, cx: Float, cy: Float, reach: Float, t: Float, glow: Boolean, still: Boolean) {
        val travel = t * TUNNEL_SPEED
        val span = Z_FAR - Z_NEAR
        var n = 0
        for (i in 0 until STREAKS) {
            val z = Z_NEAR + ((streakZ0[i] - Z_NEAR - travel) % span + span) % span
            val r0 = reach * TUNNEL_RADIUS / z
            val r1 = reach * TUNNEL_RADIUS / max(Z_NEAR * .5f, z - (if (still) STILL_STREAK else STREAK_LEN))
            val a = streakAngle[i]
            streakSegs[n++] = cx + cos(a) * r0; streakSegs[n++] = cy + sin(a) * r0
            streakSegs[n++] = cx + cos(a) * r1; streakSegs[n++] = cy + sin(a) * r1
        }
        p.setAdditive(true)
        if (glow) p.strokeSegments(streakSegs, n, 4f, cyan, .10f)
        p.strokeSegments(streakSegs, n, 1f, cyan, .55f)
        p.setAdditive(false)
        // far cards first
        for (pass in 0..1) for (i in 0 until TUNNEL_CARDS) {
            val z = Z_NEAR + ((tunnelZ0[i] - Z_NEAR - travel) % span + span) % span
            if ((z > (Z_NEAR + Z_FAR) / 2f) != (pass == 0)) continue
            val k = Z_NEAR / z
            val r = reach * TUNNEL_RADIUS / z
            val x = cx + cos(tunnelAngle[i]) * r; val y = cy + sin(tunnelAngle[i]) * r * .8f
            val fadeIn = ((Z_FAR - z) / (span * .25f)).coerceIn(0f, 1f)
            if (!still && glow) {
                val rb = reach * TUNNEL_RADIUS / (z + STREAK_LEN * .6f)
                p.setAdditive(true)
                p.drawLine(cx + cos(tunnelAngle[i]) * rb, cy + sin(tunnelAngle[i]) * rb * .8f, x, y,
                    CARD_W * k * .5f, cyan, .06f * fadeIn)
                p.setAdditive(false)
            }
            drawCard(p, x, y, CARD_W * k, CARD_H * k, fadeIn, tunnelFolder[i], glow)
        }
    }

    /** Glass document card (or amber folder) centred at (x, y). No text – pure texture. */
    private fun drawCard(p: ContinuumPainter, x: Float, y: Float, w: Float, h: Float, alpha: Float, folder: Boolean,
                         glow: Boolean) {
        if (alpha <= 0f || w < .5f) return
        val l = x - w / 2f; val t = y - h / 2f; val r = x + w / 2f; val b = y + h / 2f
        val rad = min(w, h) * .08f
        if (folder) {
            p.fillRoundRect(l, t + h * .12f, r, b, rad, folderFill, alpha)
            p.fillRoundRect(l, t, l + w * .45f, t + h * .2f, rad, folderFill, alpha)
        } else {
            p.fillRoundRect(l, t, r, b, rad, cardFill, alpha)
            var n = 0
            for (k in 0 until 3) {
                val ly = t + h * (.3f + .18f * k)
                docLineSegs[n++] = l + w * .16f; docLineSegs[n++] = ly
                docLineSegs[n++] = r - w * (if (k == 2) .4f else .16f); docLineSegs[n++] = ly
            }
            p.strokeSegments(docLineSegs, n, max(1f, w * .025f), cyan, .6f * alpha)
        }
        p.strokeRoundRect(l, t, r, b, rad, 1f, rimFill, alpha)
        if (glow) JarvisNeon.bloomRoundRect(p, l, t, r, b, rad, Colors.cyan, .5f * alpha)
    }

    companion object {
        const val DURATION_S = 3f
        const val FLASH_S = .3f
        const val FLASH_ALPHA = .85f
        const val BURST_START_S = .1f
        const val BURST_MAX_SCALE = 6f       // > 500 % (VIDEO-SPEC)
        const val TUNNEL_START_S = .8f
        const val TUNNEL_SPEED = 2.6f        // depth units per second, linear
        const val TUNNEL_RADIUS = .55f
        const val Z_NEAR = .35f
        const val Z_FAR = 4f
        const val STREAK_LEN = .5f
        const val STILL_STREAK = .08f
        const val REDUCED_FRAME_S = 1f
        const val FOLDER_SHARE = .3f
        const val CARD_W = 44f
        const val CARD_H = 56f
        const val BURST_CARDS = 20
        const val TUNNEL_CARDS = 44
        const val STREAKS = 72
    }
}
