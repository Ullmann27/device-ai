package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Light transport on the existing screenplay. */
object JarvisFluidLight {
    data class Sample(val phase: Float, val energy: Float, val rebuilding: Boolean)
    val OFF = Sample(0f, 0f, false)
    private val cyan = Fill.Solid(JarvisDesign.Colors.cyan)
    private val violet = Fill.Solid(JarvisDesign.Colors.violet)
    private val glint = Fill.Solid(Argb.rgb(195, 246, 255))
    private const val STEPS = 18
    private val segments = FloatArray(4 * STEPS * 4 * 3)
    private val highlights = FloatArray(4 * STEPS * 4)
    private val xPoints = FloatArray(STEPS + 1)
    private val yPoints = FloatArray(STEPS + 1)
    private val ribbonWidths = FloatArray(STEPS + 1)

    fun sample(elapsedMs: Long, rank: Int = 0,
               budget: JarvisRenderQuality.Budget? = null, animatorScale: Float = 1f): Sample {
        if (!animatorScale.isFinite() || animatorScale <= 0f || budget?.animate == false) return OFF
        val local = elapsedMs - JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.PANEL, rank)
        val start = HollywoodFuiConstruct.ANCHOR_END_MS
        val end = JarvisCinemaEntrance.DEPART_MS + HollywoodFuiConstruct.GLASS_END_MS
        if (local <= start || local >= end) return OFF
        val phase = (local - start).toFloat() / (end - start)
        return Sample(phase, sin(PI.toFloat() * phase).coerceIn(0f, 1f),
            local >= JarvisCinemaEntrance.DEPART_MS)
    }

    fun strandCount(budget: JarvisRenderQuality.Budget?): Int = when {
        budget?.animate == false -> 0
        budget == null || budget.tier == JarvisRenderQuality.Tier.HIGH -> 4
        else -> 2
    }

    /** Real work, shared window clock. */
    fun workingSample(elapsedMs: Long, active: Boolean, closing: Boolean,
                      budget: JarvisRenderQuality.Budget?, animatorScale: Float = 1f): Sample {
        if (!active || closing || elapsedMs < 0L || budget?.animate == false ||
            !animatorScale.isFinite() || animatorScale <= 0f) return OFF
        val cycle = (elapsedMs % 3_200L) / 3_200f
        val phase = .5f - .5f * cos(2f * PI.toFloat() * cycle)
        val arrival = (elapsedMs / HollywoodFuiConstruct.GLASS_END_MS.toFloat()).coerceIn(0f, 1f)
        return Sample(phase, arrival * (.65f + .15f * phase), false)
    }

    /** Connected ribbons; fixed reusable batches. */
    fun drawTransfer(p: ContinuumPainter, source: Box, sinkX: Float, sinkY: Float,
                     sample: Sample, budget: JarvisRenderQuality.Budget?) {
        val strands = strandCount(budget)
        if (!sample.energy.isFinite() || !sample.phase.isFinite() || sample.energy <= .001f ||
            strands == 0 || !source.left.isFinite() || !source.top.isFinite() ||
            !source.right.isFinite() || !source.bottom.isFinite() ||
            source.width <= 0f || source.height <= 0f || !sinkX.isFinite() || !sinkY.isFinite()) return
        var count = 0
        var highlightCount = 0
        val phase = sample.phase.coerceIn(0f, 1f)
        val energy = sample.energy.coerceIn(0f, 1f)
        repeat(strands) { strand ->
            val inset = minOf(source.width * .22f, 12f + (strand / 2) * 9f)
            val x0 = if (strand % 2 == 0) source.left + inset else source.right - inset
            val y0 = source.bottom
            for (step in 0..STEPS) {
                val t = step / STEPS.toFloat()
                val inverse = 1f - t
                val bend = sin(PI.toFloat() * t)
                val wave = sin(t * 7f - phase * 8f + strand * 1.7f) * bend
                xPoints[step] = inverse * inverse * x0 +
                    2f * inverse * t * (x0 + (sinkX - x0) * .12f) + t * t * sinkX + wave * 9f
                yPoints[step] = y0 + (sinkY - y0) * t + bend * (10f + strand * 3f)
                val swell = .5f + .5f * sin(t * 11f - phase * 18f + strand * 1.3f)
                ribbonWidths[step] = bend * (.55f + 1.7f * swell * swell)
            }
            for (step in 1..STEPS) {
                val dx = xPoints[step] - xPoints[step - 1]
                val dy = yPoints[step] - yPoints[step - 1]
                val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.001f)
                val nx = -dy / length
                val ny = dx / length
                for (lane in -1..1) {
                    val w0 = ribbonWidths[step - 1] * lane
                    val w1 = ribbonWidths[step] * lane
                    segments[count++] = xPoints[step - 1] + nx * w0
                    segments[count++] = yPoints[step - 1] + ny * w0
                    segments[count++] = xPoints[step] + nx * w1
                    segments[count++] = yPoints[step] + ny * w1
                }
                highlights[highlightCount++] = xPoints[step - 1] + nx * ribbonWidths[step - 1]
                highlights[highlightCount++] = yPoints[step - 1] + ny * ribbonWidths[step - 1]
                highlights[highlightCount++] = xPoints[step] + nx * ribbonWidths[step]
                highlights[highlightCount++] = yPoints[step] + ny * ribbonWidths[step]
            }
        }
        p.setAdditive(true)
        try {
            if (budget?.reducedBloom != true) p.strokeSegments(segments, count, 8f, violet, .035f * energy)
            p.strokeSegments(segments, count, 3.2f, cyan, .15f * energy)
            p.strokeSegments(highlights, highlightCount, 1.2f, cyan, .48f * energy)
            p.strokeSegments(highlights, highlightCount, .55f, glint, .48f * energy)
        } finally { p.setAdditive(false) }
    }

    /**
     * Cinema 2.0: the living band between the original orb and a window on which real work runs. Viscous strands flow
     * from ([x0], [y0]) to ([x1], [y1]); [seconds] comes from the window's own clock and enters only smooth functions,
     * so the band has no phase jump. [pulse] 0..1 sends one bright packet along it (a verified step), −1 none.
     * Reuses the batches of [drawTransfer]; nothing is allocated. No energy, a refused budget or a non-finite input
     * draws nothing.
     */
    fun drawBridge(p: ContinuumPainter, x0: Float, y0: Float, x1: Float, y1: Float, seconds: Float,
                   energy: Float, pulse: Float, budget: JarvisRenderQuality.Budget?) {
        val strands = strandCount(budget).coerceAtMost(3)
        if (strands == 0 || !energy.isFinite() || energy <= .001f || !seconds.isFinite() ||
            !x0.isFinite() || !y0.isFinite() || !x1.isFinite() || !y1.isFinite()) return
        val dxAll = x1 - x0
        val dyAll = y1 - y0
        val length = sqrt(dxAll * dxAll + dyAll * dyAll)
        if (length < 4f) return
        val nx = -dyAll / length
        val ny = dxAll / length
        val e = energy.coerceIn(0f, 1f)
        val sway = minOf(7f, length * .14f)
        val spread = minOf(9f, length * .2f)
        var count = 0
        var highlightCount = 0
        var packetX = Float.NaN
        var packetY = Float.NaN
        repeat(strands) { strand ->
            val lane = strand - (strands - 1) / 2f
            for (step in 0..STEPS) {
                val t = step / STEPS.toFloat()
                val bend = sin(PI.toFloat() * t)
                // Strands leave the orb side by side and gather where they enter the rim.
                val offset = lane * spread * (1f - t) * (1f - t) +
                    sin(t * 6.3f - seconds * 2.6f + strand * 2.1f) * sway * bend
                xPoints[step] = x0 + dxAll * t + nx * offset
                yPoints[step] = y0 + dyAll * t + ny * offset
                val swell = .5f + .5f * sin(t * 9f - seconds * 5.2f + strand * 1.3f)
                ribbonWidths[step] = bend * (.5f + 1.5f * swell * swell)
            }
            if (strand == 0 && pulse.isFinite() && pulse in 0f..1f) {
                val index = (pulse * STEPS).toInt().coerceIn(0, STEPS)
                packetX = xPoints[index]
                packetY = yPoints[index]
            }
            for (step in 1..STEPS) {
                for (side in -1..1) {
                    val w0 = ribbonWidths[step - 1] * side
                    val w1 = ribbonWidths[step] * side
                    segments[count++] = xPoints[step - 1] + nx * w0
                    segments[count++] = yPoints[step - 1] + ny * w0
                    segments[count++] = xPoints[step] + nx * w1
                    segments[count++] = yPoints[step] + ny * w1
                }
                highlights[highlightCount++] = xPoints[step - 1]
                highlights[highlightCount++] = yPoints[step - 1]
                highlights[highlightCount++] = xPoints[step]
                highlights[highlightCount++] = yPoints[step]
            }
        }
        p.setAdditive(true)
        try {
            if (budget?.reducedBloom != true) p.strokeSegments(segments, count, 7f, violet, .05f * e)
            p.strokeSegments(segments, count, 2.8f, cyan, .20f * e)
            p.strokeSegments(highlights, highlightCount, 1.1f, glint, .62f * e)
            if (packetX.isFinite() && packetY.isFinite()) {
                val fade = sin(PI.toFloat() * pulse.coerceIn(0f, 1f)).coerceIn(0f, 1f)
                p.fillOval(packetX - 8f, packetY - 8f, packetX + 8f, packetY + 8f, violet, .22f * e * fade)
                p.fillOval(packetX - 2.6f, packetY - 2.6f, packetX + 2.6f, packetY + 2.6f, glint, .95f * e * fade)
            }
        } finally { p.setAdditive(false) }
    }

    /** Portable edge fallback; centre stays clear. */
    fun drawEdge(p: ContinuumPainter, box: Box, sample: Sample) {
        if (sample.energy <= .001f) return
        p.setAdditive(true)
        try {
            repeat(3) { index ->
                val t = (sample.phase * 1.7f + index / 3f) % 1f
                val y = box.top + 14f + (box.height - 28f).coerceAtLeast(0f) * t
                val length = minOf(25f, box.height * .18f)
                repeat(2) { side ->
                    val x = if (side == 0) box.left + 2f else box.right - 2f
                    p.drawLine(x, y, x, minOf(box.bottom - 10f, y + length), 3f, cyan, .55f * sample.energy)
                    p.drawLine(x, y, x, minOf(box.bottom - 10f, y + length), .8f, glint, sample.energy)
                }
            }
        } finally { p.setAdditive(false) }
    }
}
