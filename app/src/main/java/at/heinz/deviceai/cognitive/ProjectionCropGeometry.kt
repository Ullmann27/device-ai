package at.heinz.deviceai.cognitive

import kotlin.math.ceil
import kotlin.math.floor

/** Default-display capture only; every output pixel stays inside the target. */
internal object ProjectionCropGeometry {
    fun crop(display: LivePixelRect, sourceWidth: Int, sourceHeight: Int,
             bufferWidth: Int, bufferHeight: Int, selection: LiveWindowSafety.Selection,
             own: LiveWindowRecord): LivePixelRect? {
        if (!display.valid || sourceWidth != display.right - display.left || sourceHeight != display.bottom - display.top)
            return null
        if (bufferWidth < 1 || bufferHeight < 1 || own.kind != LiveWindowKind.APPLICATION ||
            own.id == selection.target.id || !display.contains(own.bounds) ||
            !display.contains(selection.target.bounds) || own.bounds.intersects(selection.target.bounds)) return null
        var rectangles = listOf(selection.target.bounds)
        for (obstacle in selection.obstructions) rectangles = rectangles.flatMap { subtract(it, obstacle.bounds) }
        val visible = rectangles.maxByOrNull { (it.right - it.left).toLong() * (it.bottom - it.top) } ?: return null
        val sx = bufferWidth.toDouble() / sourceWidth
        val sy = bufferHeight.toDouble() / sourceHeight
        val cropped = LivePixelRect(ceil((visible.left - display.left) * sx).toInt(),
            ceil((visible.top - display.top) * sy).toInt(), floor((visible.right - display.left) * sx).toInt(),
            floor((visible.bottom - display.top) * sy).toInt())
        return cropped.takeIf { it.valid && it.left >= 0 && it.top >= 0 && it.right <= bufferWidth && it.bottom <= bufferHeight }
    }

    private fun subtract(box: LivePixelRect, obstruction: LivePixelRect): List<LivePixelRect> {
        if (!box.intersects(obstruction)) return listOf(box)
        return listOf(LivePixelRect(box.left, box.top, box.right, minOf(box.bottom, obstruction.top)),
            LivePixelRect(box.left, maxOf(box.top, obstruction.bottom), box.right, box.bottom),
            LivePixelRect(box.left, maxOf(box.top, obstruction.top), minOf(box.right, obstruction.left), minOf(box.bottom, obstruction.bottom)),
            LivePixelRect(maxOf(box.left, obstruction.right), maxOf(box.top, obstruction.top), box.right, minOf(box.bottom, obstruction.bottom)))
            .filter { it.valid }
    }
}
