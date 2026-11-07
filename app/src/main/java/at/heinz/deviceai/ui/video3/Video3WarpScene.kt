package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.motion.MotionPresets
import at.heinz.deviceai.ui.Argb
import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.JarvisDesign.Colors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * PR487@85a0562d document-flight geometry.
 * Transparent, bounded, caller-clocked overlay.
 */
internal class Video3WarpScene(seed: Int = 12) {
    private val burstAngle = FloatArray(BURST_CARDS) { 2f * PI.toFloat() * (it + unit(seed + it)) / BURST_CARDS }
    private val burstX = FloatArray(BURST_CARDS) { cos(burstAngle[it]) }
    private val burstY = FloatArray(BURST_CARDS) { sin(burstAngle[it]) }
    private val burstDist = FloatArray(BURST_CARDS) { .08f + .22f * unit(seed * 7 + it) }
    private val tunnelAngle = FloatArray(TUNNEL_CARDS) { 2f * PI.toFloat() * unit(seed * 13 + it) }
    private val tunnelX = FloatArray(TUNNEL_CARDS) { cos(tunnelAngle[it]) }
    private val tunnelY = FloatArray(TUNNEL_CARDS) { sin(tunnelAngle[it]) }
    private val tunnelZ0 = FloatArray(TUNNEL_CARDS) { Z_NEAR + (Z_FAR - Z_NEAR) * unit(seed * 17 + it) }
    private val tunnelFolder = BooleanArray(TUNNEL_CARDS) { unit(seed * 19 + it) < FOLDER_SHARE }
    private val streakAngle = FloatArray(STREAKS) { 2f * PI.toFloat() * unit(seed * 23 + it) }
    private val streakX = FloatArray(STREAKS) { cos(streakAngle[it]) }
    private val streakY = FloatArray(STREAKS) { sin(streakAngle[it]) }
    private val streakZ0 = FloatArray(STREAKS) { Z_NEAR + (Z_FAR - Z_NEAR) * unit(seed * 29 + it) }
    private val streakSegs = FloatArray(STREAKS * 4)
    private val docLineSegs = FloatArray(12)
    private val cyan = Fill.Solid(Colors.cyan)
    private val folderFill = Fill.Solid(Argb.withAlpha(Colors.warn, .55f))
    private val cardFill = Fill.Solid(Argb.withAlpha(Colors.backgroundBottom, .7f))
    private val rimFill = Fill.Solid(Colors.rim)

    fun render(p: ContinuumPainter, width: Float, height: Float, seconds: Float, glow: Boolean): Boolean {
        if (!validDimension(width) || !validDimension(height) || !seconds.isFinite() ||
            seconds < 0f || seconds >= DURATION_S
        ) return false
        val alpha = min(seconds / FADE_IN_S, (DURATION_S - seconds) / FADE_OUT_S).coerceIn(0f, 1f)
        if (alpha <= 0f) return true
        val cx = width / 2f
        val cy = height / 2f
        val reach = max(width, height) * .5f
        p.save()
        try {
            p.clipRect(0f, 0f, width, height)
            if (seconds >= TUNNEL_START_S) {
                drawTunnel(p, width, height, cx, cy, reach, seconds - TUNNEL_START_S, alpha, glow)
            }
            if (seconds < BURST_START_S + MotionPresets.WARP_MS / 1000f) {
                drawBurst(p, width, height, cx, cy, reach, seconds - BURST_START_S, alpha, glow)
            }
        } finally {
            p.setAdditive(false)
            p.restore()
        }
        return true
    }

    private fun drawBurst(p: ContinuumPainter, width: Float, height: Float, cx: Float, cy: Float,
                          reach: Float, t: Float, opacity: Float, glow: Boolean) {
        if (t < 0f) return
        val scale = MotionPresets.warpScale((t * 1000f).toLong())
        if (scale <= .01f) return
        val e = scale / MotionPresets.WARP_MAX_SCALE
        val alpha = (if (scale < 1f) scale else (1f - e) * 1.6f).coerceIn(0f, 1f) * opacity
        for (i in 0 until BURST_CARDS) {
            val d = reach * burstDist[i] * (1f + scale * 1.5f)
            drawCard(p, width, height, cx + burstX[i] * d, cy + burstY[i] * d * .75f,
                CARD_W * scale, CARD_H * scale, alpha, i % 3 == 0, glow)
        }
    }

    private fun drawTunnel(p: ContinuumPainter, width: Float, height: Float, cx: Float, cy: Float,
                           reach: Float, t: Float, opacity: Float, glow: Boolean) {
        val travel = t * TUNNEL_SPEED
        val span = Z_FAR - Z_NEAR
        var n = 0
        for (i in 0 until STREAKS) {
            val z = Z_NEAR + ((streakZ0[i] - Z_NEAR - travel) % span + span) % span
            val r0 = reach * TUNNEL_RADIUS / z
            val r1 = reach * TUNNEL_RADIUS / max(Z_NEAR * .5f, z - STREAK_LEN)
            streakSegs[n++] = cx + streakX[i] * r0
            streakSegs[n++] = cy + streakY[i] * r0
            streakSegs[n++] = cx + streakX[i] * r1
            streakSegs[n++] = cy + streakY[i] * r1
        }
        p.setAdditive(true)
        if (glow) p.strokeSegments(streakSegs, n, 4f, cyan, .10f * opacity)
        p.strokeSegments(streakSegs, n, 1f, cyan, .55f * opacity)
        p.setAdditive(false)
        // Far cards first.
        for (pass in 0..1) for (i in 0 until TUNNEL_CARDS) {
            val z = Z_NEAR + ((tunnelZ0[i] - Z_NEAR - travel) % span + span) % span
            if ((z > (Z_NEAR + Z_FAR) / 2f) != (pass == 0)) continue
            val k = Z_NEAR / z
            val r = reach * TUNNEL_RADIUS / z
            val x = cx + tunnelX[i] * r
            val y = cy + tunnelY[i] * r * .8f
            val fadeIn = ((Z_FAR - z) / (span * .25f)).coerceIn(0f, 1f) * opacity
            if (glow) {
                val rb = reach * TUNNEL_RADIUS / (z + STREAK_LEN * .6f)
                p.setAdditive(true)
                p.drawLine(cx + tunnelX[i] * rb, cy + tunnelY[i] * rb * .8f, x, y,
                    CARD_W * k * .5f, cyan, .06f * fadeIn)
                p.setAdditive(false)
            }
            drawCard(p, width, height, x, y, CARD_W * k, CARD_H * k, fadeIn, tunnelFolder[i], glow)
        }
    }

    private fun drawCard(p: ContinuumPainter, width: Float, height: Float, x: Float, y: Float,
                         w: Float, h: Float, alpha: Float, folder: Boolean, glow: Boolean) {
        if (alpha <= 0f || w < .5f) return
        val l = x - w / 2f
        val t = y - h / 2f
        val r = x + w / 2f
        val b = y + h / 2f
        if (r < -9f || b < -9f || l > width + 9f || t > height + 9f) return
        val radius = min(w, h) * .08f
        if (folder) {
            p.fillRoundRect(l, t + h * .12f, r, b, radius, folderFill, alpha)
            p.fillRoundRect(l, t, l + w * .45f, t + h * .2f, radius, folderFill, alpha)
        } else {
            p.fillRoundRect(l, t, r, b, radius, cardFill, alpha)
            var n = 0
            for (k in 0 until 3) {
                val ly = t + h * (.3f + .18f * k)
                docLineSegs[n++] = l + w * .16f
                docLineSegs[n++] = ly
                docLineSegs[n++] = r - w * (if (k == 2) .4f else .16f)
                docLineSegs[n++] = ly
            }
            p.strokeSegments(docLineSegs, n, max(1f, w * .025f), cyan, .6f * alpha)
        }
        if (glow) {
            p.setAdditive(true)
            p.strokeRoundRect(l, t, r, b, radius, 5f, cyan, .08f * alpha)
            p.setAdditive(false)
        }
        p.strokeRoundRect(l, t, r, b, radius, 1f, rimFill, alpha)
    }

    private fun validDimension(value: Float) = value.isFinite() && value > 0f && value <= 100_000f

    private fun unit(seed: Int): Float {
        var h = seed * -0x61c88647
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        h *= 0x297a2d39
        h = h xor (h ushr 15)
        return (h and 0xffff) / 65535f
    }

    companion object {
        const val DURATION_S = 3f
        private const val FADE_IN_S = .18f
        private const val FADE_OUT_S = .55f
        private const val BURST_START_S = .1f
        private const val TUNNEL_START_S = .8f
        private const val TUNNEL_SPEED = 2.6f
        private const val TUNNEL_RADIUS = .55f
        private const val Z_NEAR = .35f
        private const val Z_FAR = 4f
        private const val STREAK_LEN = .5f
        private const val FOLDER_SHARE = .3f
        private const val CARD_W = 44f
        private const val CARD_H = 56f
        private const val BURST_CARDS = 20
        private const val TUNNEL_CARDS = 44
        private const val STREAKS = 72
    }
}
