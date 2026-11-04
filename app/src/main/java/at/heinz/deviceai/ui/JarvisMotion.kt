package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Jarvis motion API (pure logic + painter helper), tuned to /workspace/jarvis-design-referenz/VIDEO-SPEC.md
 * (Heinz' reference videos, 4.11.2026). Values marked "GESCHÄTZT" are not stated in the spec.
 * Reduced motion: pass Settings.Global.ANIMATOR_DURATION_SCALE as animatorScale; 0 ⇒ final state immediately.
 */
object JarvisMotion {
    /** Durations in ms. */
    object Durations {
        const val PANEL_FLY_IN = 600L     // Muster B: 0,5–0,6 s cubic ease-out (Spec)
        const val STAGGER = 80L           // Muster B: 0,05–0,1 s (Spec-Bereich, Mittelwert gewählt)
        const val EDGE_FLASH = 300L       // Rand-Aufleuchten nach Landung – GESCHÄTZT
        const val PILL_FLY_IN = 450L      // Muster G: "schnell" – Dauer GESCHÄTZT
        const val WIDGET_GROW = 600L      // Widgets 50→100 % – Dauer wie Muster B angenommen (GESCHÄTZT)
        const val SCENE_Z = 800L          // Muster A: 0,8 s exponential ease-in (Spec)
        const val LASER = 300L            // Muster D: 0,3 s (Spec)
        const val ENVELOPE_IN = 500L      // Muster F: 0,5 s aus Tiefe (Spec)
        const val ENVELOPE_FLAP = 300L    // Muster F: Klappe 0,3 s (Spec)
        const val STEP = 500L             // Ablauf-Schritt: Muster B unteres Ende angenommen (GESCHÄTZT)
        const val IDLE_PERIOD = 4000L     // Muster C: 4 s sine (Spec)
        const val BLINK = 150L            // Avatar-Blinzeln 0,15 s (Spec)
    }

    object Easing {
        val linear: (Float) -> Float = { it }
        val easeOutCubic: (Float) -> Float = { 1f - (1f - it).pow(3) }
        val easeInOutCubic: (Float) -> Float = { if (it < .5f) 4f * it * it * it else 1f - (-2f * it + 2f).pow(3) / 2f }
        /** Strong deceleration for question pills (Muster G). */
        val easeOutQuint: (Float) -> Float = { 1f - (1f - it).pow(5) }
        /** Scene change into depth (Muster A). */
        val easeInExpo: (Float) -> Float = { if (it <= 0f) 0f else 2f.pow(10f * it - 10f) }
        val easeInOutSine: (Float) -> Float = { -(kotlin.math.cos(PI.toFloat() * it) - 1f) / 2f }
        val easeOutBack: (Float) -> Float = { val c = 1.2f; val x = it - 1f; 1f + (c + 1f) * x * x * x + c * x * x }
    }

    enum class Direction(val dx: Float, val dy: Float) {
        LEFT(-1f, 0f), RIGHT(1f, 0f), TOP(0f, -1f), BOTTOM(0f, 1f), TOP_LEFT(-.7f, -.7f), TOP_RIGHT(.7f, -.7f), DEPTH(0f, 0f)
    }

    data class FlyIn(
        val direction: Direction = Direction.LEFT,
        val distanceDp: Float = 64f,
        val fromScale: Float = .8f,
        val fromAlpha: Float = 0f,
        val durationMs: Long = Durations.PANEL_FLY_IN,
        val edgeFlashMs: Long = Durations.EDGE_FLASH,
        val easing: (Float) -> Float = Easing.easeOutCubic,
    ) { val totalMs get() = durationMs + edgeFlashMs }

    object Presets {
        /** Muster B: panels from left/right, 80→100 %, alpha 0→1, 0,6 s cubic ease-out. Distance GESCHÄTZT. */
        val panelLeft = FlyIn(Direction.LEFT, 64f, .8f)
        val panelRight = FlyIn(Direction.RIGHT, 64f, .8f)
        /** Muster G: question pills from the edges, NO fade, strong deceleration. Distance GESCHÄTZT. */
        fun pill(from: Direction) = FlyIn(from, 160f, 1f, fromAlpha = 1f, durationMs = Durations.PILL_FLY_IN,
            edgeFlashMs = 0L, easing = Easing.easeOutQuint)
        /** Widgets (weather, calendar, CPU, RAM) from top-left/right, 50→100 %. Distance GESCHÄTZT. */
        fun widget(from: Direction) = FlyIn(from, 96f, .5f, durationMs = Durations.WIDGET_GROW)
        /** Muster F: Gmail envelope out of depth 0→100 % in 0,5 s (flap: [envelopeFlap]). */
        val envelope = FlyIn(Direction.DEPTH, 0f, .0f, durationMs = Durations.ENVELOPE_IN, edgeFlashMs = 0L)
        /** PDF document: same depth entrance as the envelope content – GESCHÄTZT. */
        val document = FlyIn(Direction.DEPTH, 0f, .6f, durationMs = Durations.ENVELOPE_IN)
        /** Process step (live flow): GESCHÄTZT, derived from Muster B. */
        val step = FlyIn(Direction.LEFT, 32f, .9f, durationMs = Durations.STEP)
        /** Back-compat alias: command → panels (Muster B). */
        val command = panelLeft
    }

    data class Frame(val dx: Float, val dy: Float, val scale: Float, val alpha: Float, val glow: Float, val done: Boolean)

    val REST = Frame(0f, 0f, 1f, 1f, 0f, true)

    private fun off(s: Float) = !(s > 0f) || !s.isFinite()

    fun frame(spec: FlyIn, elapsedMs: Long, animatorScale: Float = 1f): Frame {
        if (off(animatorScale)) return REST
        val dur = spec.durationMs * animatorScale
        val flash = spec.edgeFlashMs * animatorScale
        val t = elapsedMs.toFloat()
        if (t <= 0f) return Frame(spec.direction.dx * spec.distanceDp, spec.direction.dy * spec.distanceDp,
            spec.fromScale, spec.fromAlpha, 0f, false)
        val p = min(1f, t / max(dur, 1f))
        val e = spec.easing(p)
        val alpha = (spec.fromAlpha + (1f - spec.fromAlpha) * Easing.easeOutCubic(p)).coerceIn(0f, 1f)
        val glow = when {
            p < 1f -> .6f * p
            flash <= 0f -> 0f
            else -> { val f = min(1f, (t - dur) / flash); 1f - f * f }
        }
        val done = t >= dur + flash
        return Frame(spec.direction.dx * spec.distanceDp * (1f - e), spec.direction.dy * spec.distanceDp * (1f - e),
            spec.fromScale + (1f - spec.fromScale) * e, alpha, if (done) 0f else glow.coerceIn(0f, 1f), done)
    }

    /** "Mitte zuerst": stagger rank by distance of each card centre from [centerX] (ties keep input order). */
    fun centerFirstRanks(centersX: List<Float>, centerX: Float): IntArray {
        val order = centersX.indices.sortedBy { abs(centersX[it] - centerX) }
        val ranks = IntArray(centersX.size); order.forEachIndexed { rank, i -> ranks[i] = rank }; return ranks
    }

    /** Panels left of centre fly in from the left, right of centre from the right (Muster B). */
    fun panelSpecFor(cardCenterX: Float, screenCenterX: Float) =
        if (cardCenterX < screenCenterX) Presets.panelLeft else Presets.panelRight

    fun staggerStart(rank: Int, staggerMs: Long = Durations.STAGGER, animatorScale: Float = 1f): Long =
        if (off(animatorScale)) 0L else (rank.coerceAtLeast(0) * staggerMs * animatorScale).toLong()

    fun staggered(spec: FlyIn, count: Int, elapsedMs: Long, staggerMs: Long = Durations.STAGGER,
                  animatorScale: Float = 1f): List<Frame> =
        List(count) { frame(spec, elapsedMs - staggerStart(it, staggerMs, animatorScale), animatorScale) }

    fun groupDurationMs(spec: FlyIn, count: Int, staggerMs: Long = Durations.STAGGER, animatorScale: Float = 1f): Long =
        if (off(animatorScale) || count <= 0) 0L else staggerStart(count - 1, staggerMs, animatorScale) + (spec.totalMs * animatorScale).toLong()

    /** Muster A: Z scene change. Old UI → >300 % + alpha 0, new UI 0→100 % from centre; 0,8 s exponential ease-in. */
    data class SceneFrame(val oldScale: Float, val oldAlpha: Float, val newScale: Float, val newAlpha: Float, val done: Boolean)

    fun sceneZ(elapsedMs: Long, animatorScale: Float = 1f): SceneFrame {
        if (off(animatorScale)) return SceneFrame(3.2f, 0f, 1f, 1f, true)
        val p = (elapsedMs / (Durations.SCENE_Z * animatorScale)).coerceIn(0f, 1f)
        val e = Easing.easeInExpo(p)
        return SceneFrame(1f + 2.2f * e, 1f - e, e, e, p >= 1f)
    }

    /** Muster D: laser line progress 0..1 over 0,3 s (linear draw-on). */
    fun laser(elapsedMs: Long, animatorScale: Float = 1f): Float =
        if (off(animatorScale)) 1f else (elapsedMs / (Durations.LASER * animatorScale)).coerceIn(0f, 1f)

    /** Muster F: envelope flap opening angle 0..1 after the envelope has arrived (X-rotation). */
    fun envelopeFlap(elapsedMs: Long, animatorScale: Float = 1f): Float {
        if (off(animatorScale)) return 1f
        val t = elapsedMs - Durations.ENVELOPE_IN * animatorScale
        return Easing.easeInOutCubic((t / (Durations.ENVELOPE_FLAP * animatorScale)).coerceIn(0f, 1f))
    }

    /** Muster C: avatar idle float, Y ±[amplitudeDp] (spec 2–3 px), 4 s sine loop. 0 when reduced motion. */
    fun idleOffset(timeMs: Long, amplitudeDp: Float = 2.5f, animatorScale: Float = 1f): Float =
        if (off(animatorScale)) 0f else amplitudeDp * sin(2f * PI.toFloat() * (timeMs % Durations.IDLE_PERIOD) / Durations.IDLE_PERIOD)

    /** Draws a glass card in [f] state; [content] draws inside the transformed card. */
    fun drawCard(p: ContinuumPainter, f: Frame, l: Float, t: Float, r: Float, b: Float, content: (ContinuumPainter) -> Unit = {}) {
        if (f.alpha <= 0f || f.scale <= 0f) return
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        p.save()
        p.translate(f.dx, f.dy)
        p.scale(f.scale, f.scale, cx, cy)
        val glow = if (f.glow > .5f) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.NORMAL
        JarvisNeon.glassCard(p, l, t, r, b, glow = glow, active = f.glow > .5f, glowBoost = f.alpha)
        content(p)
        p.restore()
    }
}
