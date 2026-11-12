package at.heinz.deviceai.avatar3d

import kotlin.math.sqrt

/** Monitor-relative light with upload caching. */
internal class CockpitMonitorLightTransform(
    private val setPosition: (Float, Float, Float) -> Unit,
    private val setFalloff: (Float) -> Unit
) {
    private var x = Float.NaN
    private var y = Float.NaN
    private var z = Float.NaN
    private var falloff = Float.NaN

    fun update(matrix: FloatArray) {
        require(matrix.size >= 16)
        for (index in 0..15) require(matrix[index].isFinite())
        fun length(column: Int) = sqrt(matrix[column] * matrix[column] +
            matrix[column + 1] * matrix[column + 1] + matrix[column + 2] * matrix[column + 2])
        val width = length(0)
        val height = length(4)
        val depth = length(8)
        require(width > 0f && height > 0f && depth > 0f)
        val reach = maxOf(width, height)
        val forward = reach * .22f / depth
        val nextX = matrix[12] - matrix[4] * .30f + matrix[8] * forward
        val nextY = matrix[13] - matrix[5] * .30f + matrix[9] * forward
        val nextZ = matrix[14] - matrix[6] * .30f + matrix[10] * forward
        if (x != nextX || y != nextY || z != nextZ) {
            x = Float.NaN; y = Float.NaN; z = Float.NaN
            setPosition(nextX, nextY, nextZ)
            x = nextX; y = nextY; z = nextZ
        }
        val nextFalloff = reach * 1.6f
        if (falloff != nextFalloff) {
            falloff = Float.NaN
            setFalloff(nextFalloff)
            falloff = nextFalloff
        }
    }

    fun clear() {
        x = Float.NaN; y = Float.NaN; z = Float.NaN; falloff = Float.NaN
    }
}
