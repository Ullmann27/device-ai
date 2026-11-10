package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box
import kotlin.math.PI
import kotlin.math.sin

/** Light transport on the existing screenplay. */
object JarvisFluidLight {
    data class Sample(val phase: Float, val energy: Float, val rebuilding: Boolean)
    val OFF = Sample(0f, 0f, false)
    private val cyan = Fill.Solid(JarvisDesign.Colors.cyan)
    private val violet = Fill.Solid(JarvisDesign.Colors.violet)
    private val glint = Fill.Solid(Argb.rgb(195, 246, 255))
    private val segments = FloatArray(4 * 18 * 4)

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

    /** Bounded, reusable batched geometry. */
    fun drawTransfer(p: ContinuumPainter, source: Box, sinkX: Float, sinkY: Float,
                     sample: Sample, budget: JarvisRenderQuality.Budget?) {
        val strands = strandCount(budget)
        if (sample.energy <= .001f || strands == 0 || source.width <= 0f || source.height <= 0f ||
            !sinkX.isFinite() || !sinkY.isFinite()) return
        var count = 0
        val phase = sample.phase
        repeat(strands) { strand ->
            val edge = if (strand % 2 == 0) source.left + 8f else source.right - 8f
            val x0 = edge + (strand / 2) * 3f
            val y0 = source.bottom - 6f
            var lastX = x0
            var lastY = y0
            for (step in 1..18) {
                val t = step / 18f
                val inverse = 1f - t
                val bend = sin(PI.toFloat() * t)
                val wave = sin(t * 9f + phase * 13f + strand * 1.7f) * bend * 7f
                val x = inverse * inverse * x0 + 2f * inverse * t * (x0 + (sinkX - x0) * .12f) +
                    t * t * sinkX + wave
                val y = y0 + (sinkY - y0) * t + bend * (10f + strand * 3f)
                val flow = phase
                val pulse = .5f + .5f * sin(t * 15f - flow * 22f + strand)
                if (pulse > .22f) {
                    segments[count++] = lastX; segments[count++] = lastY
                    segments[count++] = x; segments[count++] = y
                }
                lastX = x; lastY = y
            }
        }
        p.setAdditive(true)
        try {
            if (budget?.reducedBloom != true) p.strokeSegments(segments, count, 6f, violet, .11f * sample.energy)
            p.strokeSegments(segments, count, 2f, cyan, .65f * sample.energy)
            p.strokeSegments(segments, count, .65f, glint, .75f * sample.energy)
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
