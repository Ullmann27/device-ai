package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Slot

/**
 * Airbus flight-deck principle (owner brief 06.11.2026): while a work window is open the cockpit makes room instead
 * of being covered by it. The quick-access tiles and the lower status rows slide down and slightly outward, shrink
 * and fade – staggered from the centre outward, rows after tiles – and return in reverse once the window has folded
 * back. Avatar, orb, dialog, rail, header and status bar stay. Pure geometry: no allocation, no clock of its own;
 * the shell's frame clock drives [progress]. A fully cleared card is not drawn at all (less GPU work while working).
 */
object JarvisDeckClearance {
    /** Vertical travel of a fully cleared card, dp. */
    const val TRAVEL_DP = 180f
    /** Outward drift as a fraction of the card's distance from the centre. */
    const val SPREAD = .12f
    const val SHRINK = .18f
    /** Share of the motion used for the centre-outward stagger. */
    const val STAGGER = .35f
    const val DURATION_MS = 360L

    private val rows = setOf(Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL,
        Slot.WORK_DOCUMENT, Slot.WORK_PROCESS, Slot.WORK_DRAFT)

    /** Tiles have no slot of their own (null); the secondary panel and lower rows may clear as well. */
    fun clears(slot: Slot?): Boolean = slot == null || slot == Slot.COCKPIT_SECONDARY || slot in rows

    fun clearance(slot: Slot?, tileProgress: Float, missionProgress: Float): Float = when {
        slot == null || slot == Slot.COCKPIT_SECONDARY -> tileProgress.coerceIn(0f, 1f)
        slot in rows -> missionProgress.coerceIn(0f, 1f)
        else -> 0f
    }

    /** 0 = leaves first. Centre tiles first, outer tiles later, rows after the tiles. */
    fun order(slot: Slot?, centerX: Float, width: Float): Float {
        val half = (width / 2f).takeIf { it > 0f } ?: return 0f
        val distance = (kotlin.math.abs(centerX - half) / half).coerceIn(0f, 1f)
        return if (slot == null) distance * .5f else .5f + distance * .5f
    }

    /** Eased clearance of one card for the deck [progress] (0 = in place, 1 = gone). */
    fun local(progress: Float, order: Float): Float {
        if (!progress.isFinite() || progress <= 0f) return 0f
        if (progress >= 1f) return 1f
        val t = (progress * (1f + STAGGER) - order.coerceIn(0f, 1f) * STAGGER).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun alpha(local: Float) = 1f - local.coerceIn(0f, 1f)
    fun scale(local: Float) = 1f - SHRINK * local.coerceIn(0f, 1f)
    fun dy(local: Float) = TRAVEL_DP * local.coerceIn(0f, 1f)
    fun dx(local: Float, centerX: Float, width: Float) = (centerX - width / 2f) * SPREAD * local.coerceIn(0f, 1f)

    /** Next deck progress for a frame of [elapsedMs] towards [target]; reduced motion snaps. */
    fun step(progress: Float, target: Float, elapsedMs: Long, motion: Boolean): Float {
        val goal = if (target.isFinite()) target.coerceIn(0f, 1f) else 0f
        if (!motion || !progress.isFinite()) return goal
        val delta = elapsedMs.coerceIn(0L, 100L).toFloat() / DURATION_MS
        return if (progress < goal) minOf(goal, progress + delta) else maxOf(goal, progress - delta)
    }

    fun step(progress: Float, cleared: Boolean, elapsedMs: Long, motion: Boolean): Float =
        step(progress, if (cleared) 1f else 0f, elapsedMs, motion)
}
