package at.heinz.deviceai.ui

import kotlin.math.max

/** Pure timing model for the canonical work-window visual layer. */
object HollywoodFuiConstruct {
    const val BRACKETS_END_MS = 120L
    const val GRID_END_MS = 250L
    const val DATA_END_MS = 450L
    const val CLOSE_END_MS = 320L
    const val SCAN_PERIOD_MS = 2_200L

    data class Frame(
        val brackets: Float,
        val grid: Float,
        val data: Float,
        val closeFactor: Float,
        val scanPhase: Float,
        val scanEnabled: Boolean
    )

    fun frame(
        elapsedMs: Long,
        closingElapsedMs: Long = -1L,
        reducedMotion: Boolean = false
    ): Frame {
        val elapsed = max(0L, elapsedMs)
        val closeFactor = if (closingElapsedMs < 0L) {
            1f
        } else {
            1f - progress(closingElapsedMs, 0L, CLOSE_END_MS)
        }

        if (reducedMotion) {
            return Frame(
                brackets = 1f,
                grid = 1f,
                data = 1f,
                closeFactor = closeFactor,
                scanPhase = 0f,
                scanEnabled = false
            )
        }

        val scanElapsed = (elapsed - BRACKETS_END_MS).coerceAtLeast(0L)
        return Frame(
            brackets = easeOutCubic(progress(elapsed, 0L, BRACKETS_END_MS)),
            grid = easeOutCubic(progress(elapsed, BRACKETS_END_MS, GRID_END_MS)),
            data = easeOutCubic(progress(elapsed, GRID_END_MS, DATA_END_MS)),
            closeFactor = closeFactor,
            scanPhase = (scanElapsed % SCAN_PERIOD_MS).toFloat() / SCAN_PERIOD_MS.toFloat(),
            scanEnabled = closeFactor > 0f && elapsed >= BRACKETS_END_MS
        )
    }

    private fun progress(value: Long, start: Long, end: Long): Float {
        if (end <= start) return 1f
        return ((value - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    }

    private fun easeOutCubic(value: Float): Float {
        val inverse = 1f - value.coerceIn(0f, 1f)
        return 1f - inverse * inverse * inverse
    }
}
