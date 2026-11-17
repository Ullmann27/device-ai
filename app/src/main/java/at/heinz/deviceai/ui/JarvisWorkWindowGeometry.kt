package at.heinz.deviceai.ui

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot

/** Free space in overlay pixels. */
object JarvisWorkWindowGeometry {
    /** Seed beyond the visible edge. */
    fun edgeOrigin(viewport: RectF, destination: RectF, density: Float,
                   side: JarvisActionScenes.EdgeSide): RectF? {
        if (!valid(viewport) || !valid(destination) || !density.isFinite() || density <= 0f) return null
        val width = destination.width() * .78f
        val height = destination.height() * .78f
        val gap = 12f * density
        val left = if (side == JarvisActionScenes.EdgeSide.LEFT) viewport.left - gap - width
            else viewport.right + gap
        val top = destination.centerY() - height * .5f
        return RectF(left, top, left + width, top + height)
    }

    fun destination(viewport: RectF, protectedBounds: List<RectF>, density: Float = 1f): RectF? {
        if (!valid(viewport) || !density.isFinite() || density <= 0f ||
            protectedBounds.any { !finite(it) }) return null
        val gap = 8f * density
        val area = RectF(viewport).apply { inset(gap, gap) }
        if (area.width() < 200f * density || area.height() < 160f * density) return null
        val blocked = protectedBounds.filter(::valid).map { RectF(it).apply { inset(-gap, -gap) } }
            .filter { it.intersect(area) }
        val xs = (listOf(area.left, area.right) + blocked.flatMap { listOf(it.left, it.right) }).distinct().sorted()
        val ys = (listOf(area.top, area.bottom) + blocked.flatMap { listOf(it.top, it.bottom) }).distinct().sorted()
        var best: RectF? = null
        var bestArea = 0f
        for (left in xs.indices) for (right in left + 1 until xs.size) {
            if (xs[right] - xs[left] < 200f * density) continue
            for (top in ys.indices) for (bottom in top + 1 until ys.size) {
                val candidate = RectF(xs[left], ys[top], xs[right], ys[bottom])
                if (candidate.height() < 160f * density || blocked.any { RectF.intersects(candidate, it) }) continue
                val width = minOf(candidate.width(), 640f * density)
                val score = width * candidate.height()
                if (score <= bestArea) continue
                val inset = (candidate.width() - width) * .5f
                candidate.inset(inset, 0f)
                best = candidate
                bestArea = score
            }
        }
        return best
    }

    internal fun finite(box: RectF) = box.left.isFinite() && box.top.isFinite() &&
        box.right.isFinite() && box.bottom.isFinite()

    internal fun valid(box: RectF) = finite(box) && box.width() > 0f && box.height() > 0f

    /** Shortest visible corner route. */
    internal fun route(origin: RectF, destination: RectF, viewport: RectF,
                       protectedBounds: List<RectF>, clearance: Float): List<PointF> {
        if (!valid(viewport) || !valid(origin) || !valid(destination) ||
            !clearance.isFinite() || clearance < 0f) return emptyList()
        val blocked = protectedBounds.filter(::valid).map { RectF(it).apply { inset(-clearance, -clearance) } }
        val start = PointF(origin.centerX().coerceIn(viewport.left, viewport.right),
            origin.centerY().coerceIn(viewport.top, viewport.bottom))
        val end = PointF(destination.centerX(), destination.centerY())
        fun allowed(p: PointF) = p.x in viewport.left..viewport.right && p.y in viewport.top..viewport.bottom &&
            blocked.none { inside(p, it) }
        if (!allowed(start) || !allowed(end)) return emptyList()
        val points = mutableListOf(start, end)
        blocked.forEach { b ->
            listOf(PointF(b.left, b.top), PointF(b.right, b.top),
                PointF(b.right, b.bottom), PointF(b.left, b.bottom)).filterTo(points, ::allowed)
        }
        val distances = FloatArray(points.size) { Float.POSITIVE_INFINITY }
        val previous = IntArray(points.size) { -1 }
        val visited = BooleanArray(points.size)
        distances[0] = 0f
        repeat(points.size) {
            val current = points.indices.filter { !visited[it] }.minByOrNull { distances[it] }
                ?: return@repeat
            if (!distances[current].isFinite()) return@repeat
            visited[current] = true
            for (next in points.indices) {
                if (visited[next] || blocked.any { crosses(points[current], points[next], it) }) continue
                val distance = distances[current] + hypot(points[next].x - points[current].x, points[next].y - points[current].y)
                if (distance < distances[next]) {
                    distances[next] = distance
                    previous[next] = current
                }
            }
        }
        if (!distances[1].isFinite()) return emptyList()
        val result = mutableListOf<PointF>()
        var index = 1
        while (index >= 0) {
            result.add(points[index])
            index = previous[index]
        }
        return result.asReversed()
    }

    private fun inside(p: PointF, b: RectF) =
        p.x > b.left + .01f && p.x < b.right - .01f && p.y > b.top + .01f && p.y < b.bottom - .01f

    private fun crosses(a: PointF, b: PointF, obstacle: RectF): Boolean {
        val rect = RectF(obstacle).apply { inset(.01f, .01f) }
        var enter = 0f
        var exit = 1f
        fun slab(position: Float, delta: Float, low: Float, high: Float): Boolean {
            if (abs(delta) < .0001f) return position in low..high
            val first = (low - position) / delta
            val last = (high - position) / delta
            enter = maxOf(enter, minOf(first, last))
            exit = minOf(exit, maxOf(first, last))
            return enter <= exit
        }
        return slab(a.x, b.x - a.x, rect.left, rect.right) &&
            slab(a.y, b.y - a.y, rect.top, rect.bottom)
    }
}
