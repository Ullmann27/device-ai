package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.sin

/**
 * Motion of the menu navigation (Jarvis Integration): pure timing / spec math on top of JarvisMotion (#479).
 * Curves and durations come ONLY from JarvisMotion; the Android player is CockpitAnimationPresenter (Kern's one
 * fly-in logic) and [JarvisCameraDrift]. Reduced motion (animatorScale 0) ⇒ final state immediately.
 */
object JarvisMenuMotion {
    /** Muster B, one panel: start delay (Mitte zuerst, 0,05–0,1 s staggered), side and fly-in spec. */
    data class PanelStep(val index: Int, val rank: Int, val fromLeft: Boolean, val delayMs: Long, val spec: JarvisMotion.FlyIn)

    /** Muster B for panels with the given centre X: centre first, left of centre from the left, right from the right. */
    fun patternB(centersX: List<Float>, screenCenterX: Float, animatorScale: Float = 1f): List<PanelStep> {
        val ranks = JarvisMotion.centerFirstRanks(centersX, screenCenterX)
        return centersX.mapIndexed { i, x ->
            val spec = JarvisMotion.panelSpecFor(x, screenCenterX)
            PanelStep(i, ranks[i], spec.direction == JarvisMotion.Direction.LEFT,
                JarvisMotion.staggerStart(ranks[i], animatorScale = animatorScale), spec)
        }
    }

    /** Last panel lands (without the edge flash) – the visible length of a B group. */
    fun patternBMs(count: Int, animatorScale: Float = 1f): Long =
        if (count <= 0 || !(animatorScale > 0f)) 0L
        else JarvisMotion.staggerStart(count - 1, animatorScale = animatorScale) + (JarvisMotion.Durations.PANEL_FLY_IN * animatorScale).toLong()

    /** Muster A: 0,8 s, old UI → >300 % + alpha 0, new UI 0→100 % from the centre (JarvisMotion.sceneZ). */
    fun patternA(elapsedMs: Long, animatorScale: Float = 1f) = JarvisMotion.sceneZ(elapsedMs, animatorScale)

    /**
     * Camera drift (Signaturen 7, 8, 13; Korrektur Kreativdirektor): mostly a slow LINEAR dolly-in into depth,
     * scale 1,00 → 1,03 over [DOLLY_MS], then an ease-in-out return over [RETURN_MS] so it can loop forever, plus a
     * ±1–2 px float and a very slight tilt. Periods are GESCHÄTZT ("mehrere Sekunden").
     */
    object Camera {
        const val SCALE_FROM = 1f
        const val SCALE_TO = 1.03f
        const val DOLLY_MS = 9_000L
        const val RETURN_MS = 3_000L
        const val PERIOD_MS = DOLLY_MS + RETURN_MS
        const val FLOAT_DP = 1.5f    // ±1–2 px
        const val TILT_DEG = .5f     // "ganz leichtes Neigen"

        data class State(val scale: Float, val dxDp: Float, val dyDp: Float, val rotationX: Float, val rotationY: Float)
        val REST = State(1f, 0f, 0f, 0f, 0f)

        fun dollyScale(timeMs: Long, animatorScale: Float = 1f): Float {
            if (!(animatorScale > 0f) || !animatorScale.isFinite() || timeMs < 0) return SCALE_FROM
            val t = timeMs % PERIOD_MS
            val k = if (t < DOLLY_MS) JarvisMotion.Easing.linear(t.toFloat() / DOLLY_MS)
            else 1f - JarvisMotion.Easing.easeInOutSine((t - DOLLY_MS).toFloat() / RETURN_MS)
            return SCALE_FROM + (SCALE_TO - SCALE_FROM) * k
        }

        fun at(timeMs: Long, animatorScale: Float = 1f): State {
            if (!(animatorScale > 0f) || !animatorScale.isFinite() || timeMs < 0) return REST
            val ph = 2f * PI.toFloat() * (timeMs % JarvisMotion.Depth.DRIFT_PERIOD_MS) / JarvisMotion.Depth.DRIFT_PERIOD_MS
            return State(dollyScale(timeMs, animatorScale), FLOAT_DP * sin(ph), FLOAT_DP * .6f * sin(2f * ph + 1f),
                TILT_DEG * sin(ph + .7f), TILT_DEG * sin(ph))
        }
    }

    /**
     * Szene 3b (Video 1, 6–8 s; ref4): the portrait phone screen scales up from the centre, floats and tilts slightly
     * in 3D (ease-in-out, ~2 s); the head floats to its right – drawn ONLY through Kern's JarvisAvatarSlot.
     * Tilt amplitude GESCHÄTZT.
     */
    object PhoneScene {
        const val SCALE_IN_MS = JarvisMotion.Durations.PANEL_FLY_IN
        const val TILT_MS = 2_000L
        const val TILT_Y_DEG = -12f
        const val TILT_X_DEG = 4f
        const val PHONE_ASPECT = 9f / 19.5f   // ref4 portrait phone
        const val PHONE_HEIGHT_FRACTION = .82f

        data class Frame(val scale: Float, val alpha: Float, val rotationX: Float, val rotationY: Float, val floatDy: Float,
                         val done: Boolean)

        fun frame(elapsedMs: Long, animatorScale: Float = 1f): Frame {
            if (!(animatorScale > 0f) || !animatorScale.isFinite()) return Frame(1f, 1f, TILT_X_DEG, TILT_Y_DEG, 0f, true)
            val t = elapsedMs.coerceAtLeast(0).toFloat()
            val s = JarvisMotion.Easing.easeOutCubic((t / (SCALE_IN_MS * animatorScale)).coerceIn(0f, 1f))
            val k = JarvisMotion.Easing.easeInOutCubic((t / (TILT_MS * animatorScale)).coerceIn(0f, 1f))
            return Frame(s, s, TILT_X_DEG * k, TILT_Y_DEG * k, JarvisMotion.idleOffset(elapsedMs, animatorScale = animatorScale),
                t >= TILT_MS * animatorScale)
        }

        /** Phone box centred slightly left, head box (JarvisAvatarSlot) to its right; dp. */
        fun layout(widthDp: Float, heightDp: Float): Pair<JarvisShellLayout.Box, JarvisShellLayout.Box> {
            val h = heightDp * PHONE_HEIGHT_FRACTION
            val w = h * PHONE_ASPECT
            val cx = widthDp * .42f; val top = (heightDp - h) / 2f
            val phone = JarvisShellLayout.Box(cx - w / 2f, top, cx + w / 2f, top + h)
            val head = minOf(widthDp - phone.right - 16f, h * .55f).coerceAtLeast(0f)
            val hl = phone.right + 16f; val ht = heightDp / 2f - head / 2f
            return phone to JarvisShellLayout.Box(hl, ht, hl + head, ht + head)
        }
    }
}
