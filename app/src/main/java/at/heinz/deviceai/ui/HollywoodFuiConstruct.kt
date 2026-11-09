package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Pure timing model for the canonical work-window visual layer.
 *
 * Owner sequence (#642/#643 C): light anchor → corner brackets → scan grid → glass depth layers → data release,
 * with the whole construct swinging in from a perspective tilt and settling flat once the glass stands. Closing runs the
 * choreography backwards: data fades first, the glass folds, the frame swings out and the anchor re-ignites once.
 * One clock, no second event bus; reduced motion settles instantly and never sweeps.
 */
object HollywoodFuiConstruct {
    const val ANCHOR_END_MS = 140L
    const val BRACKETS_END_MS = 320L
    const val GRID_END_MS = 560L
    const val GLASS_START_MS = 480L
    const val GLASS_END_MS = 760L
    const val DATA_START_MS = 640L
    const val DATA_END_MS = 900L
    const val CLOSE_END_MS = 420L
    const val SCAN_PERIOD_MS = 2_200L
    /** Entrance perspective of the construct (degrees around the vertical axis); settles to 0 at [GLASS_END_MS]. */
    const val ENTRANCE_TILT_DEGREES = 14f

    private const val CLOSE_DATA_END_MS = 160L
    private const val CLOSE_GLASS_START_MS = 80L
    private const val CLOSE_GLASS_END_MS = 300L
    private const val CLOSE_FRAME_START_MS = 160L

    /** Zero velocity at lift-off and landing; shared by the native panel and its construction layers. */
    fun flight(elapsedMs: Long): Float =
        easeInOutCubic(progress(elapsedMs, ANCHOR_END_MS, GLASS_END_MS))

    data class Frame(
        val anchor: Float,
        val brackets: Float,
        val grid: Float,
        val glass: Float,
        val data: Float,
        /** Overall close progress 1 → 0 (kept for callers that fade everything uniformly). */
        val closeFactor: Float,
        /** Close choreography per element, each 1 → 0: data first, then glass, then the frame with the brackets. */
        val closeData: Float,
        val closeGlass: Float,
        val closeFrame: Float,
        val scanPhase: Float,
        val scanEnabled: Boolean,
        /** Perspective tilt in degrees; 0 once settled, grows again while folding away. */
        val tiltDegrees: Float,
        /** Energy pulse 0..1 of the anchor/orb: peaks when the anchor stands, gone when the brackets stand. */
        val pulse: Float,
        /** All construction phases complete and the construct stands flat. */
        val settled: Boolean
    )

    fun frame(
        elapsedMs: Long,
        closingElapsedMs: Long = -1L,
        reducedMotion: Boolean = false
    ): Frame {
        val elapsed = max(0L, elapsedMs)
        val closing = closingElapsedMs >= 0L
        val closeFactor = if (!closing) 1f else 1f - progress(closingElapsedMs, 0L, CLOSE_END_MS)
        val closeData = if (!closing) 1f else 1f - progress(closingElapsedMs, 0L, CLOSE_DATA_END_MS)
        val closeGlass = if (!closing) 1f else 1f - easeInOutCubic(progress(closingElapsedMs, CLOSE_GLASS_START_MS, CLOSE_GLASS_END_MS))
        val closeFrame = if (!closing) 1f else 1f - easeInOutCubic(progress(closingElapsedMs, CLOSE_FRAME_START_MS, CLOSE_END_MS))

        if (reducedMotion) {
            return Frame(
                anchor = 1f, brackets = 1f, grid = 1f, glass = 1f, data = 1f,
                closeFactor = closeFactor, closeData = closeData, closeGlass = closeGlass, closeFrame = closeFrame,
                scanPhase = 0f, scanEnabled = false, tiltDegrees = 0f, pulse = 0f, settled = true
            )
        }

        val anchor = easeOutCubic(progress(elapsed, 0L, ANCHOR_END_MS))
        val brackets = easeOutCubic(progress(elapsed, ANCHOR_END_MS, BRACKETS_END_MS))
        val grid = easeOutCubic(progress(elapsed, BRACKETS_END_MS, GRID_END_MS))
        val glass = easeOutCubic(progress(elapsed, GLASS_START_MS, GLASS_END_MS))
        val data = easeOutCubic(progress(elapsed, DATA_START_MS, DATA_END_MS))
        val settleProgress = easeOutCubic(progress(elapsed, 0L, GLASS_END_MS))
        val openTilt = ENTRANCE_TILT_DEGREES * (1f - settleProgress)
        val closeTilt = if (closing) ENTRANCE_TILT_DEGREES * .6f * (1f - closeFrame) else 0f
        val openPulse = sin(PI.toFloat() * progress(elapsed, 0L, BRACKETS_END_MS)).coerceAtLeast(0f)
        val closePulse = if (closing) .6f * sin(PI.toFloat() * progress(closingElapsedMs, 0L, CLOSE_END_MS)) else 0f
        val scanElapsed = (elapsed - BRACKETS_END_MS).coerceAtLeast(0L)
        return Frame(
            anchor = anchor,
            brackets = brackets,
            grid = grid,
            glass = glass,
            data = data,
            closeFactor = closeFactor,
            closeData = closeData,
            closeGlass = closeGlass,
            closeFrame = closeFrame,
            scanPhase = (scanElapsed % SCAN_PERIOD_MS).toFloat() / SCAN_PERIOD_MS.toFloat(),
            scanEnabled = closeFactor > 0f && elapsed >= BRACKETS_END_MS,
            tiltDegrees = max(openTilt, closeTilt),
            pulse = max(openPulse, closePulse).coerceIn(0f, 1f),
            settled = elapsed >= DATA_END_MS && !closing
        )
    }

    /** One phase-derived angle shared by native panel and holographic glass. */
    fun orientedTilt(degrees: Float, side: Float, reducedMotion: Boolean): Float {
        if (reducedMotion || !degrees.isFinite() || !side.isFinite()) return 0f
        return degrees.coerceIn(0f, ENTRANCE_TILT_DEGREES) * side.coerceIn(-1f, 1f)
    }

    private fun progress(value: Long, start: Long, end: Long): Float {
        if (end <= start) return 1f
        return ((value - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    }

    internal fun easeOutCubic(value: Float): Float {
        val inverse = 1f - value.coerceIn(0f, 1f)
        return 1f - inverse * inverse * inverse
    }

    internal fun easeInOutCubic(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return if (t < .5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }
}
