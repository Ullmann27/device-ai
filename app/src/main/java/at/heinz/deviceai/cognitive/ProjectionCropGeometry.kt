package at.heinz.deviceai.cognitive

import kotlin.math.ceil
import kotlin.math.floor

/** Default-display capture only; every output pixel stays inside the target. */
internal object ProjectionCropGeometry {
    fun viewport(display: LivePixelRect, bufferWidth: Int, bufferHeight: Int,
                 target: LivePixelRect, crop: LivePixelRect): LiveTargetBounds? {
        if (!display.valid || !target.valid || !crop.valid || !display.contains(target) ||
            bufferWidth < 1 || bufferHeight < 1 || crop.left < 0 || crop.top < 0 ||
            crop.right > bufferWidth || crop.bottom > bufferHeight) return null
        val sx = (display.right - display.left).toDouble() / bufferWidth
        val sy = (display.bottom - display.top).toDouble() / bufferHeight
        val width = (target.right - target.left).toDouble()
        val height = (target.bottom - target.top).toDouble()
        val left = (display.left + crop.left * sx - target.left) / width
        val top = (display.top + crop.top * sy - target.top) / height
        val right = (display.left + crop.right * sx - target.left) / width
        val bottom = (display.top + crop.bottom * sy - target.top) / height
        if (left < -1e-6 || top < -1e-6 || right > 1.000001 || bottom > 1.000001 ||
            right <= left || bottom <= top) return null
        return LiveTargetBounds(left.coerceIn(0.0, 1.0).toFloat(), top.coerceIn(0.0, 1.0).toFloat(),
            right.coerceIn(0.0, 1.0).toFloat(), bottom.coerceIn(0.0, 1.0).toFloat())
    }

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
