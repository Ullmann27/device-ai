package at.heinz.deviceai.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Jarvis motion API: fly-in of glass cards and staggered sequences, pure logic + a painter helper.
 * Pflicht-Abläufe (JARVIS-DESIGN-AUFTRAG.md): Einfliegen bei Befehlen, Gmail-Konto-Karte, PDF-Karte, Ablauf-Schritte.
 * ALLE Zeitwerte sind GESCHÄTZT (Videos in Google Drive "Quantum Backup/Videos" noch nicht ausgewertet) –
 * nach Video-Analyse nur die Konstanten in [Durations]/[Presets] anpassen.
 * Reduzierte Bewegung: [scale] = Settings.Global.ANIMATOR_DURATION_SCALE; 0 ⇒ sofort Endzustand.
 */
object JarvisMotion {
    /** Durations in ms (GESCHÄTZT). */
    object Durations {
        const val FLY_IN = 520L
        const val STAGGER = 70L
        const val EDGE_FLASH = 360L      // rim glow flare after landing
        const val ACCOUNT_CARD = 640L    // Gmail-Konto-Karte (GESCHÄTZT)
        const val DOCUMENT_CARD = 700L   // PDF-Karte (GESCHÄTZT)
        const val STEP = 420L            // Ablauf-Schritt
    }

    /** Easing curves, t in 0..1. */
    object Easing {
        val linear: (Float) -> Float = { it }
        val easeOutCubic: (Float) -> Float = { 1f - (1f - it).pow(3) }
        val easeInOutCubic: (Float) -> Float = { if (it < .5f) 4f * it * it * it else 1f - (-2f * it + 2f).pow(3) / 2f }
        /** Slight overshoot (~5 %) for the "landing" feel. */
        val easeOutBack: (Float) -> Float = { val c = 1.2f; val x = it - 1f; 1f + (c + 1f) * x * x * x + c * x * x }
    }

    enum class Direction(val dx: Float, val dy: Float) {
        LEFT(-1f, 0f), RIGHT(1f, 0f), TOP(0f, -1f), BOTTOM(0f, 1f), DEPTH(0f, 0f)
    }

    data class FlyIn(
        val direction: Direction = Direction.BOTTOM,
        val distanceDp: Float = 48f,
        val fromScale: Float = .92f,
        val fromAlpha: Float = 0f,
        val durationMs: Long = Durations.FLY_IN,
        val edgeFlashMs: Long = Durations.EDGE_FLASH,
        val easing: (Float) -> Float = Easing.easeOutBack,
    ) { val totalMs get() = durationMs + edgeFlashMs }

    /** Presets for the mandatory flows (GESCHÄTZT bis Video-Auswertung). */
    object Presets {
        val command = FlyIn(Direction.BOTTOM, 48f, .92f)
        val account = FlyIn(Direction.RIGHT, 120f, .85f, durationMs = Durations.ACCOUNT_CARD)
        val document = FlyIn(Direction.DEPTH, 0f, .6f, durationMs = Durations.DOCUMENT_CARD)
        val step = FlyIn(Direction.LEFT, 32f, .96f, durationMs = Durations.STEP, easing = Easing.easeOutCubic)
    }

    /** Frame state to apply to a card. */
    data class Frame(val dx: Float, val dy: Float, val scale: Float, val alpha: Float, val glow: Float, val done: Boolean)

    val REST = Frame(0f, 0f, 1f, 1f, 0f, true)

    /**
     * @param elapsedMs time since the card's own start (negative = not yet started)
     * @param animatorScale ANIMATOR_DURATION_SCALE; <= 0 ⇒ reduced motion ⇒ [REST]
     * glow: 0..1 rim intensity, peaks right after landing, then fades.
     */
    fun frame(spec: FlyIn, elapsedMs: Long, animatorScale: Float = 1f): Frame {
        if (!(animatorScale > 0f) || !animatorScale.isFinite()) return REST
        val dur = spec.durationMs * animatorScale
        val flash = spec.edgeFlashMs * animatorScale
        val t = elapsedMs.toFloat()
        if (t <= 0f) return Frame(spec.direction.dx * spec.distanceDp, spec.direction.dy * spec.distanceDp,
            spec.fromScale, spec.fromAlpha, 0f, false)
        val p = min(1f, t / max(dur, 1f))
        val e = spec.easing(p)
        val rest = 1f - e
        val alpha = (spec.fromAlpha + (1f - spec.fromAlpha) * Easing.easeOutCubic(p)).coerceIn(0f, 1f)
        val glow = when {
            p < 1f -> .6f * p
            flash <= 0f -> 0f
            else -> { val f = min(1f, (t - dur) / flash); (1f - f) * (1f - f).let { if (f < .15f) 1f else it } }
        }.coerceIn(0f, 1f)
        val done = t >= dur + flash
        return Frame(spec.direction.dx * spec.distanceDp * rest, spec.direction.dy * spec.distanceDp * rest,
            spec.fromScale + (1f - spec.fromScale) * e, alpha, if (done) 0f else glow, done)
    }

    /** Start offset of card [index] in a staggered group (stagger scales with animator scale). */
    fun staggerStart(index: Int, staggerMs: Long = Durations.STAGGER, animatorScale: Float = 1f): Long =
        if (!(animatorScale > 0f)) 0L else (index.coerceAtLeast(0) * staggerMs * animatorScale).toLong()

    /** Frames for a group of [count] cards at group time [elapsedMs]. */
    fun staggered(spec: FlyIn, count: Int, elapsedMs: Long, staggerMs: Long = Durations.STAGGER,
                  animatorScale: Float = 1f): List<Frame> =
        List(count) { frame(spec, elapsedMs - staggerStart(it, staggerMs, animatorScale), animatorScale) }

    fun groupDurationMs(spec: FlyIn, count: Int, staggerMs: Long = Durations.STAGGER, animatorScale: Float = 1f): Long =
        if (!(animatorScale > 0f) || count <= 0) 0L else staggerStart(count - 1, staggerMs, animatorScale) + (spec.totalMs * animatorScale).toLong()

    /** Draws a glass card in [frame] state; [content] draws inside the transformed card. */
    fun drawCard(p: ContinuumPainter, f: Frame, l: Float, t: Float, r: Float, b: Float, content: (ContinuumPainter) -> Unit = {}) {
        if (f.alpha <= 0f) return
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        p.save()
        p.translate(f.dx, f.dy)
        p.scale(f.scale, f.scale, cx, cy)
        val glow = when {
            f.glow > .66f -> JarvisDesign.Glow.STRONG
            f.glow > .33f -> JarvisDesign.Glow.NORMAL
            else -> JarvisDesign.Glow.SUBTLE
        }
        JarvisNeon.glassCard(p, l, t, r, b, glow = glow, active = f.glow > .5f)
        content(p)
        p.restore()
    }
}
