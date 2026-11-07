package at.heinz.deviceai.motion

import kotlin.math.abs

/** [Stagger] implementations. All are pure and allocation-free. */
object Staggers {
    /** VIDEO-SPEC Muster B: 0,05–0,1 s. */
    const val MIN_STEP_SEC = .05f
    const val MAX_STEP_SEC = .1f

    /**
     * "Mitte zuerst" in symmetric rings: the centre item (two centre items for an even count) start at 0,
     * each ring further out [stepSec] later; left and right partners of a ring start together.
     * count 5 → rings 2,1,0,1,2; count 4 → 1,0,0,1.
     */
    fun centerFirst(stepSec: Float = MotionPresets.STAGGER_SEC): Stagger {
        require(stepSec >= 0f && stepSec.isFinite())
        return Stagger { index, count -> ring(index, count) * stepSec }
    }

    /** "Mitte zuerst" with a distinct slot per item: centre, then left, right, next-left, ... (ties: left first). */
    fun centerFirstSequential(stepSec: Float = MotionPresets.STAGGER_SEC): Stagger {
        require(stepSec >= 0f && stepSec.isFinite())
        return Stagger { index, count -> rank(index, count) * stepSec }
    }

    /** Plain left-to-right stagger. */
    fun linear(stepSec: Float = MotionPresets.STAGGER_SEC): Stagger {
        require(stepSec >= 0f && stepSec.isFinite())
        return Stagger { index, count -> if (count <= 0 || index < 0) 0f else index.coerceAtMost(count - 1) * stepSec }
    }

    /** Ring number (0 = centre). Out-of-range index is clamped. */
    fun ring(index: Int, count: Int): Int {
        if (count <= 1) return 0
        val i = index.coerceIn(0, count - 1)
        // Twice the distance from the centre, integer: |2i - (n-1)|; ring = that / 2 (rounded down).
        return abs(2 * i - (count - 1)) / 2
    }

    /** Distinct rank 0..count-1 by distance from the centre; equal distance keeps the left item first. */
    fun rank(index: Int, count: Int): Int {
        if (count <= 1) return 0
        val i = index.coerceIn(0, count - 1)
        val di = abs(2 * i - (count - 1))
        var r = 0
        for (j in 0 until count) {
            val dj = abs(2 * j - (count - 1))
            if (dj < di || (dj == di && j < i)) r++
        }
        return r
    }
}
