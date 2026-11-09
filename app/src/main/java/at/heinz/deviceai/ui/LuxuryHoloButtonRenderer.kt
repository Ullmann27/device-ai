package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box
import kotlin.math.hypot

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

    fun draw(p: ContinuumPainter, box: Box, sourceX: Float, sourceY: Float,
             active: Boolean, disabled: Boolean, pressed: Boolean) {
        if (box.width <= 0f || box.height <= 0f) return
        val radius = minOf(10f, box.height / 2f)
        val sink = if (pressed) 1.2f else 0f
        val l = box.left + .4f * sink
        val t = box.top + sink
        val r = box.right - .4f * sink
        val b = box.bottom + sink
        val alpha = if (disabled) .34f else 1f
        if (!pressed) p.fillRoundRect(l + 1.5f, t + 4.5f, r + 1.5f, b + 5.5f,
            radius, Fill.Solid(INSET_SHADOW), .60f * alpha)
        p.fillRoundRect(l, t, r, b, radius, Fill.Linear(l, t, l, b,
            intArrayOf(OBSIDIAN_TOP, OBSIDIAN_BOTTOM)), .92f * alpha)
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
        p.strokeRoundRect(l, t, r, b, radius, if (active) 1.25f else .85f,
            directional, (if (active) .82f else .58f) * alpha)
        p.drawLine(l + radius * 1.2f, t + .6f,
            l + (r - l) * (if (active) .75f else .43f), t + .6f,
            .8f, Fill.Solid(ICE_CYAN), (if (active) .67f else .25f * proximity) * alpha)
    }
}
