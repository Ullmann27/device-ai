package at.heinz.deviceai.avatar3d

import kotlin.math.cos
import kotlin.math.sin

/** Shared native and GPU projection. */
data class CockpitStageFrame(
    val width: Float,
    val height: Float,
    val monitorLeft: Float,
    val monitorTop: Float,
    val monitorRight: Float,
    val monitorBottom: Float,
    val yawDegrees: Float = -3f,
    val pitchDegrees: Float = 0f,
    val depthOffset: Float = 0f
) {
    val monitorWidth get() = monitorRight - monitorLeft
    val monitorHeight get() = monitorBottom - monitorTop
    val distance get() = maxOf(width, height) * 2.4f
    fun valid() = listOf(width, height, monitorLeft, monitorTop, monitorRight, monitorBottom,
        yawDegrees, pitchDegrees, depthOffset)
        .all(Float::isFinite) && width > 0f && height > 0f && monitorWidth > 0f && monitorHeight > 0f &&
        monitorLeft >= 0f && monitorTop >= 0f && monitorRight <= width && monitorBottom <= height &&
        yawDegrees in -8f..8f && pitchDegrees in -12f..12f &&
        kotlin.math.abs(depthOffset) <= minOf(width, height) * .4f

    fun bufferSize(maxEdge: Int = 1280): Pair<Int, Int> {
        require(valid() && maxEdge in 64..2048)
        val scale = minOf(1f, maxEdge / maxOf(width, height))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    /** Clockwise, front plane coordinates. */
    fun quad(left: Float = CONTENT_LEFT, top: Float = CONTENT_TOP,
             right: Float = CONTENT_RIGHT, bottom: Float = CONTENT_BOTTOM): FloatArray {
        require(valid() && left <= right && top <= bottom)
        val angle = Math.toRadians(yawDegrees.toDouble())
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()
        val z = minOf(monitorWidth, monitorHeight) * CONTENT_DEPTH
        val cx = (monitorLeft + monitorRight - width) * .5f
        val cy = (height - monitorTop - monitorBottom) * .5f
        val result = FloatArray(8)
        val corners = arrayOf(left to top, right to top, right to bottom, left to bottom)
        corners.forEachIndexed { index, (u, v) ->
            val localX = (u - .5f) * monitorWidth
            val localY = (.5f - v) * monitorHeight
            val pitchedY = cp * localY - sp * z
            val pitchedZ = sp * localY + cp * z
            val projectedZ = depthOffset - s * localX + c * pitchedZ
            val scale = distance / (distance - projectedZ)
            result[index * 2] = width * .5f + (cx + c * localX + s * pitchedZ) * scale
            result[index * 2 + 1] = height * .5f - (cy + pitchedY) * scale
        }
        return result
    }

    companion object {
        const val CONTENT_LEFT = .035f
        const val CONTENT_TOP = .12f
        const val CONTENT_RIGHT = .965f
        const val CONTENT_BOTTOM = .945f
        const val CONTENT_DEPTH = .032f
    }
}
