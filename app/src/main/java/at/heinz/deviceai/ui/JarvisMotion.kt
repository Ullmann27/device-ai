package at.heinz.deviceai.ui

import at.heinz.deviceai.motion.DampedSpring
import at.heinz.deviceai.motion.Easings
import at.heinz.deviceai.motion.MotionPresets
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Jarvis motion API (pure logic + painter helper), tuned to /workspace/jarvis-design-referenz/VIDEO-SPEC.md
 * (Heinz' reference videos, 4.11.2026). Values marked "GESCHÄTZT" are not stated in the spec.
 * Reduced motion: pass Settings.Global.ANIMATOR_DURATION_SCALE as animatorScale; 0 ⇒ final state immediately.
 */
object JarvisMotion {
    /** Durations in ms. */
    object Durations {
        const val PANEL_FLY_IN = MotionPresets.FLY_IN_MS     // Muster B: 0,5–0,6 s cubic ease-out (Spec)
        const val STAGGER = MotionPresets.STAGGER_MS           // Muster B: 0,05–0,1 s (Spec-Bereich, Mittelwert gewählt)
        const val EDGE_FLASH = 300L       // Rand-Aufleuchten nach Landung – GESCHÄTZT
        const val PILL_FLY_IN = 450L      // Muster G: "schnell" – Dauer GESCHÄTZT
        const val WIDGET_GROW = 600L      // Widgets 50→100 % – Dauer wie Muster B angenommen (GESCHÄTZT)
        const val SCENE_Z = MotionPresets.SCENE_MS          // Muster A: 0,8 s exponential ease-in (Spec)
        const val LASER = MotionPresets.LASER_MS            // Muster D: 0,3 s (Spec)
        const val ENVELOPE_IN = MotionPresets.ENVELOPE_IN_MS      // Muster F: 0,5 s aus Tiefe (Spec)
        const val ENVELOPE_FLAP = MotionPresets.ENVELOPE_FLAP_MS    // Muster F: Klappe 0,3 s (Spec)
        const val STEP = 500L             // Ablauf-Schritt: Muster B unteres Ende angenommen (GESCHÄTZT)
        const val IDLE_PERIOD = MotionPresets.IDLE_PERIOD_MS     // Muster C: 4 s sine (Spec)
        const val BLINK = MotionPresets.BLINK_MS            // Avatar-Blinzeln 0,15 s (Spec)
    }

    /** Function-typed views of [Easings] (at.heinz.deviceai.motion), the single definition of every curve. */
    object Easing {
        val linear: (Float) -> Float = Easings.linear::at
        val easeInCubic: (Float) -> Float = Easings.easeInCubic::at
        val easeOutCubic: (Float) -> Float = Easings.easeOutCubic::at
        val easeInOutCubic: (Float) -> Float = Easings.easeInOutCubic::at
        /** Strong deceleration for question pills (Muster G). */
        val easeOutQuint: (Float) -> Float = Easings.easeOutQuint::at
        /** Scene change into depth (Muster A). */
        val easeInExpo: (Float) -> Float = Easings.easeInExpo::at
        val easeInOutSine: (Float) -> Float = Easings.easeInOutSine::at
        val easeOutBack: (Float) -> Float = Easings.easeOutBack::at
    }

    /** Widget fly-in spring (Bildbeschrieb 6: ~0,5 s, slight overshoot); value via valueAtMs(elapsedMs). */
    val widgetSpring: DampedSpring get() = MotionPresets.widgetSpring

    /** Spring with classic parameters (unit mass), 0 → 1; value via valueAtMs(elapsedMs). */
    fun spring(stiffness: Float, damping: Float): DampedSpring = DampedSpring.fromStiffness(stiffness, damping)

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
        if (t >= dur + flash) return REST
        return Frame(spec.direction.dx * spec.distanceDp * (1f - e), spec.direction.dy * spec.distanceDp * (1f - e),
            spec.fromScale + (1f - spec.fromScale) * e, alpha, glow.coerceIn(0f, 1f), false)
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
        if (off(animatorScale)) return SceneFrame(MotionPresets.SCENE_OLD_SCALE, 0f, 1f, 1f, true)
        val p = (elapsedMs / (Durations.SCENE_Z * animatorScale)).coerceIn(0f, 1f)
        val e = Easing.easeInExpo(p)
        return SceneFrame(MotionPresets.sceneOldScale(e), 1f - e, e, e, p >= 1f)
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
        if (off(animatorScale)) 0f else MotionPresets.idleFloat(timeMs, amplitudeDp)

    /**
     * Z-depth layers (VIDEO-ANALYSE Signatur 7 "UI schwebt in Z-Tiefe", 8 "Kamera nie statisch").
     * Pure logic: depth → scale / blur / tilt / drift. Amplitudes and periods are GESCHÄTZT (Videos nennen keine Zahlen).
     */
    object Depth {
        enum class Layer(val z: Int) { NEAR(0), MID(1), FAR(2) }

        /** Android View cameraDistance in dp (multiply by density). GESCHÄTZT. */
        const val CAMERA_DISTANCE_DP = 1600f
        /** Far panels slightly smaller. GESCHÄTZT. */
        const val SCALE_PER_LAYER = .035f
        /** Depth of field: far layers blurred, near sharp (Bildbeschrieb 1). Radius GESCHÄTZT, small for legibility. */
        const val BLUR_PER_LAYER_DP = .75f
        /** "ganz leichtes Neigen" (Bildbeschrieb 1): a few degrees. GESCHÄTZT. */
        const val MAX_TILT_DEG = 3f
        /** Camera drift ±1–2 px (Bildbeschrieb 1): 1.5 dp. */
        const val DRIFT_DP = 1.5f
        /** Drift loop period. GESCHÄTZT. */
        const val DRIFT_PERIOD_MS = 14_000L
        /** Dolly-in: linear scale 1.00 → 1.03 "über mehrere Sekunden" (Bildbeschrieb 1). */
        const val DOLLY_SCALE = MotionPresets.DOLLY_MAX_SCALE
        /** Duration of one dolly-in leg; the camera then glides back the same way (ping-pong). GESCHÄTZT. */
        const val DOLLY_PERIOD_MS = MotionPresets.DOLLY_PERIOD_MS

        data class State(val scale: Float, val blurDp: Float, val rotationX: Float, val rotationY: Float,
                         val dx: Float, val dy: Float)

        val FLAT = State(1f, 0f, 0f, 0f, 0f, 0f)

        fun scale(layer: Layer) = 1f - SCALE_PER_LAYER * layer.z
        fun blurDp(layer: Layer) = BLUR_PER_LAYER_DP * layer.z

        /** Tilt towards the screen centre: panels left of centre turn right (+Y) and vice versa. */
        fun tiltY(cardCenterX: Float, screenWidth: Float): Float {
            if (screenWidth <= 0f) return 0f
            val n = ((cardCenterX - screenWidth / 2f) / (screenWidth / 2f)).coerceIn(-1f, 1f)
            return -MAX_TILT_DEG * n
        }

        fun tiltX(cardCenterY: Float, screenHeight: Float): Float {
            if (screenHeight <= 0f) return 0f
            val n = ((cardCenterY - screenHeight / 2f) / (screenHeight / 2f)).coerceIn(-1f, 1f)
            return MAX_TILT_DEG * .5f * n
        }

        /** Continuous drift (dx, dy in dp) of ±[DRIFT_DP]; deeper layers move less (parallax). 0 for reduced motion. */
        fun drift(timeMs: Long, layer: Layer, animatorScale: Float = 1f): Pair<Float, Float> {
            if (off(animatorScale)) return 0f to 0f
            val ph = 2f * PI.toFloat() * (timeMs % DRIFT_PERIOD_MS) / DRIFT_PERIOD_MS
            val a = DRIFT_DP / (1 + layer.z)
            return a * sin(ph) to a * .6f * sin(2f * ph + 1f)
        }

        /** Linear dolly-in camera scale 1.00..1.03 (ping-pong). 1 for reduced motion. */
        fun dolly(timeMs: Long, animatorScale: Float = 1f): Float {
            if (off(animatorScale)) return 1f
            return MotionPresets.dollyScale(timeMs)
        }

        /** Full panel state. Reduced motion keeps the static depth composition (scale, blur, tilt) without drift. */
        fun state(layer: Layer, cardCenterX: Float, cardCenterY: Float, width: Float, height: Float, timeMs: Long,
                  animatorScale: Float = 1f): State {
            val (dx, dy) = drift(timeMs, layer, animatorScale)
            val wobble = if (off(animatorScale)) 0f else .4f * sin(2f * PI.toFloat() * (timeMs % DRIFT_PERIOD_MS) / DRIFT_PERIOD_MS)
            return State(scale(layer), blurDp(layer), tiltX(cardCenterY, height) + wobble, tiltY(cardCenterX, width) + wobble, dx, dy)
        }
    }

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
