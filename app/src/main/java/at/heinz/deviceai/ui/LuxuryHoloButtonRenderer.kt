package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.PI

/** EXP-52-CINE-DEPTH: source-directed, restrained glass for the ten real cockpit tiles. */
object LuxuryHoloButtonRenderer {
    const val OBSIDIAN_TOP = 0xE0071020.toInt()
    const val OBSIDIAN_BOTTOM = 0xEA040812.toInt()
    const val MUTED_SLATE = 0xFF243B5E.toInt()
    const val ICE_CYAN = 0xFF7CE8FF.toInt()
    const val AMETHYST = 0xFF9D72FF.toInt()
    private const val INSET_SHADOW = 0xFF02050B.toInt()

    /** Stable colour law; no separate timer, phantom tool state or app action. */
    fun highlightProximity(box: Box, sourceX: Float, sourceY: Float): Float {
        if (!sourceX.isFinite() || !sourceY.isFinite() || box.width <= 0f || box.height <= 0f) return 0f
        val distance = hypot(sourceX - box.centerX, sourceY - box.centerY)
        return (1f - distance / 950f).coerceIn(.12f, .72f)
    }

    /** Native press, existing scene clock. */
    fun pressure(pressed: Boolean, disabled: Boolean, timeMs: Long): Float {
        if (!pressed || disabled) return 0f
        if (timeMs <= 0L) return .85f
        val phase = (timeMs % 1_200L) / 1_200f
        return .85f + .15f * sin(phase * 2f * PI.toFloat())
    }

    fun draw(p: ContinuumPainter, box: Box, sourceX: Float, sourceY: Float,
             active: Boolean, disabled: Boolean, pressed: Boolean,
             timeMs: Long = 0L, pressX: Float = .5f, pressY: Float = .5f) {
        if (box.width <= 0f || box.height <= 0f) return
        val radius = minOf(10f, box.height / 2f)
        val pressure = pressure(pressed, disabled, timeMs)
        val sink = if (pressure > 0f) 1.8f else 0f
        val l = box.left + .4f * sink
        val t = box.top + sink
        val r = box.right - .4f * sink
        val b = box.bottom + sink
        val alpha = if (disabled) .34f else 1f
        val glow = if (disabled) .22f else if (active) 1.25f else .85f
        val rim = Fill.Linear(l, t, r, b, intArrayOf(ICE_CYAN, AMETHYST, ICE_CYAN))
        p.setAdditive(true)
        p.strokeRoundRect(l, t, r, b, radius, 16f, rim, .11f * alpha * glow)
        p.strokeRoundRect(l, t, r, b, radius, 8f, rim, .20f * alpha * glow)
        p.strokeRoundRect(l, t, r, b, radius, 3.6f, rim, .36f * alpha * glow)
        p.setAdditive(false)
        if (pressure == 0f) p.fillRoundRect(l + 1.5f, t + 4.5f, r + 1.5f, b + 5.5f,
            radius, Fill.Solid(INSET_SHADOW), .60f * alpha)
        p.fillRoundRect(l, t, r, b, radius, Fill.Linear(l, t, l, b,
            intArrayOf(if (active) 0xE0122C4A.toInt() else OBSIDIAN_TOP, OBSIDIAN_BOTTOM)), .90f * alpha)
        p.fillRoundRect(l + 1.2f, t + 1.0f, r - 1.2f, t + (b - t) * .48f, (radius - 1f).coerceAtLeast(1f),
            Fill.Linear(l, t, l, t + (b - t) * .48f,
                intArrayOf(Argb.of(90, 180, 240, 255), Argb.of(0, 124, 232, 255))), alpha)
        val lip = JarvisVisualDepth.edgeThicknessDp().coerceAtMost((b - t) * .14f)
        p.fillRect(l + radius * .3f, b, r - radius * .15f, b + lip,
            Fill.Linear(l, b, l, b + lip, intArrayOf(Argb.withAlpha(ICE_CYAN, .40f),
                Argb.withAlpha(INSET_SHADOW, .80f))), .80f * alpha)
        p.drawLine(l + radius, b + lip, r - radius, b + lip,
            1.1f, Fill.Solid(ICE_CYAN), .45f * alpha)
        p.drawLine(l + radius, b - 1.2f, r - radius, b - 1.2f,
            .8f, Fill.Solid(INSET_SHADOW), .65f * alpha)

        val proximity = highlightProximity(box, sourceX, sourceY)
        val dx = (sourceX - box.centerX).takeIf(Float::isFinite) ?: 0f
        val dy = (sourceY - box.centerY).takeIf(Float::isFinite) ?: 0f
        val dist = hypot(dx, dy).coerceAtLeast(1f)
        val unitX = dx / dist
        val unitY = dy / dist
        val extentX = box.width * .53f
        val extentY = box.height * .53f
        val highlight = if (active) ICE_CYAN else Argb.mix(MUTED_SLATE, ICE_CYAN, proximity)
        val directional = Fill.Linear(
            box.centerX + unitX * extentX, box.centerY + unitY * extentY,
            box.centerX - unitX * extentX, box.centerY - unitY * extentY,
            intArrayOf(highlight, if (active) AMETHYST else MUTED_SLATE, MUTED_SLATE))
        p.setAdditive(true)
        p.strokeRoundRect(l, t, r, b, radius, if (active) 1.55f else 1.15f,
            directional, (if (active) .94f else .70f) * alpha)
        p.setAdditive(false)
        p.drawLine(l + radius * 1.2f, t + .6f,
            l + (r - l) * (if (active) .75f else .43f), t + .6f,
            .8f, Fill.Solid(ICE_CYAN), (if (active) .67f else .25f * proximity) * alpha)
        if (pressure > 0f) {
            val x = l + (r - l) * (pressX.takeIf(Float::isFinite) ?: .5f).coerceIn(0f, 1f)
            val y = t + (b - t) * (pressY.takeIf(Float::isFinite) ?: .5f).coerceIn(0f, 1f)
            val contact = Fill.Radial(x, y, maxOf(r - l, b - t).coerceAtLeast(1f),
                intArrayOf(ICE_CYAN, AMETHYST, Argb.withAlpha(ICE_CYAN, .08f)),
                floatArrayOf(0f, .55f, 1f))
            p.setAdditive(true)
            try {
                p.strokeRoundRect(l, t, r, b, radius, 6f, contact, .35f * pressure)
                p.strokeRoundRect(l, t, r, b, radius, 1.8f, contact, .90f * pressure)
            } finally { p.setAdditive(false) }
        }

    }

    /** Shared holographic shelf under the tool dock. Decoration only; does not move hit targets. */
    fun drawDockPlatform(p: ContinuumPainter, box: Box) {
        if (box.width <= 2f || box.height <= 2f) return
        val radius = minOf(18f, box.height * .35f)
        p.setAdditive(true)
        p.fillOval(box.left + box.width * .06f, box.bottom - 8f,
            box.right - box.width * .06f, box.bottom + 14f,
            Fill.Radial(box.centerX, box.bottom, box.width * .48f,
                intArrayOf(Argb.withAlpha(ICE_CYAN, .35f), Argb.withAlpha(AMETHYST, .12f), 0)), .85f)
        p.fillRoundRect(box.left - 4f, box.top + 2f, box.right + 4f, box.bottom + 6f, radius,
            Fill.Linear(box.left, box.top, box.right, box.bottom,
                intArrayOf(Argb.withAlpha(ICE_CYAN, .10f), Argb.withAlpha(AMETHYST, .08f), 0)), .70f)
        val edge = Fill.Linear(box.left, box.top, box.right, box.top, intArrayOf(ICE_CYAN, AMETHYST, ICE_CYAN))
        p.strokeRoundRect(box.left - 2f, box.top, box.right + 2f, box.bottom + 4f, radius, 10f, edge, .18f)
        p.strokeRoundRect(box.left - 2f, box.top, box.right + 2f, box.bottom + 4f, radius, 2.4f, edge, .55f)
        p.strokeOval(box.left + box.width * .18f, box.bottom - 6f,
            box.right - box.width * .18f, box.bottom + 16f, 1.4f, edge, .50f)
        p.setAdditive(false)
    }
}
