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
    val worldScale get() = 1f / minOf(width, height)
    val restDepth get() = minOf(width, height) * .22f
    val restCompensation get() = (distance - restDepth) / distance
    val near get() = .01
    val far get() = (distance * worldScale + 8f).toDouble()
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

    /** Column-major; shared with Filament. */
    fun monitorTransform(): FloatArray {
        require(valid())
        val angle = Math.toRadians(yawDegrees.toDouble())
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()
        val sx = monitorWidth * restCompensation * worldScale
        val sy = monitorHeight * restCompensation * worldScale
        val sz = minOf(monitorWidth, monitorHeight) * restCompensation * worldScale
        return floatArrayOf(
            c * sx, 0f, -s * sx, 0f,
            s * sp * sy, cp * sy, c * sp * sy, 0f,
            s * cp * sz, -sp * sz, c * cp * sz, 0f,
            (monitorLeft + monitorRight - width) * .5f * restCompensation * worldScale,
            (height - monitorTop - monitorBottom) * .5f * restCompensation * worldScale,
            (restDepth + depthOffset) * worldScale, 1f)
    }

    /** Entire original orbit stays behind Jarvis. */
    fun orbTransform(left: Float, top: Float, right: Float, bottom: Float): FloatArray {
        require(valid() && right > left && bottom > top)
        val screenRadius = minOf(right - left, bottom - top) * .5f * worldScale
        val d = distance * worldScale
        val reach = screenRadius * ORB_MAX_RADIUS
        val depth = (ORB_FRONT_LIMIT - reach) / (1f - reach / d)
        val fit = (d - depth) / d
        val radius = screenRadius * fit
        val x = ((left + right) * .5f - width * .5f) * worldScale * fit
        val y = (height * .5f - (top + bottom) * .5f) * worldScale * fit
        return floatArrayOf(radius, 0f, 0f, 0f, 0f, radius, 0f, 0f,
            0f, 0f, radius, 0f, x, y, depth, 1f)
    }

    /** Same camera as the scene. */
    fun project(x: Float, y: Float, z: Float): Pair<Float, Float> {
        val d = distance * worldScale
        require(z < d)
        val perspective = d / (d - z) / worldScale
        return width * .5f + x * perspective to height * .5f - y * perspective
    }

    /** Clockwise front-plane coordinates. */
    fun quad(left: Float = CONTENT_LEFT, top: Float = CONTENT_TOP,
             right: Float = CONTENT_RIGHT, bottom: Float = CONTENT_BOTTOM): FloatArray {
        require(valid() && left <= right && top <= bottom)
        val matrix = monitorTransform()
        val result = FloatArray(8)
        val corners = arrayOf(left to top, right to top, right to bottom, left to bottom)
        corners.forEachIndexed { index, (u, v) ->
            val x = u - .5f
            val y = .5f - v
            val z = CONTENT_DEPTH
            val projected = project(matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12],
                matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13],
                matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14])
            result[index * 2] = projected.first
            result[index * 2 + 1] = projected.second
        }
        return result
    }

    companion object {
        const val ORB_MAX_RADIUS = 1.72f
        const val ORB_FRONT_LIMIT = -.4f
        const val CONTENT_LEFT = .035f
        const val CONTENT_TOP = .12f
        const val CONTENT_RIGHT = .965f
        const val CONTENT_BOTTOM = .945f
        const val CONTENT_DEPTH = .032f
    }
}
