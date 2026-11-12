package at.heinz.deviceai.avatar3d

import kotlin.math.cos
import kotlin.math.sin

/** Column-major unified scene transforms. */
object CockpitWorldTransforms {
    const val ORB_DEPTH = -.44f

    fun avatar(frame: CockpitStageFrame, left: Float, top: Float, right: Float, bottom: Float,
               fitted: AvatarFraming.Window, focus: AvatarGlbContract.Focus,
               out: FloatArray = FloatArray(16)): FloatArray {
        require(frame.valid())
        require(listOf(left, top, right, bottom).all(Float::isFinite) && right > left && bottom > top)
        identity(out)
        val unit = frame.worldScale
        val scale = ((bottom - top) * unit / (2 * fitted.halfHeight)).toFloat()
        val x = (left + (right - left) * fitted.anchorX - frame.width * .5).toFloat() * unit
        val y = (frame.height * .5f - (top + bottom) * .5f) * unit
        val angle = Math.toRadians(-focus.azimuthDegrees.toFloat().toDouble())
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        out[0] = scale * c
        out[2] = -scale * s
        out[5] = scale
        out[8] = scale * s
        out[10] = scale * c
        out[12] = -focus.centerX.toFloat() * out[0] - focus.centerZ.toFloat() * out[8] + x
        out[13] = -fitted.cameraCenterY.toFloat() * scale + y
        out[14] = -focus.centerX.toFloat() * out[2] - focus.centerZ.toFloat() * out[10]
        return out
    }

    fun room(frame: CockpitStageFrame, out: FloatArray = FloatArray(16)): FloatArray {
        require(frame.valid())
        identity(out)
        val compensation = frame.restCompensation
        out[0] = frame.width * frame.worldScale * compensation
        out[5] = frame.height * frame.worldScale * compensation
        out[10] = compensation
        out[14] = frame.restDepth * frame.worldScale
        return out
    }

    fun orb(frame: CockpitStageFrame, left: Float, top: Float, right: Float, bottom: Float,
            out: FloatArray = FloatArray(16)): FloatArray {
        require(frame.valid())
        require(listOf(left, top, right, bottom).all(Float::isFinite) && right > left && bottom > top)
        identity(out)
        val unit = frame.worldScale
        val cameraDistance = frame.distance * unit
        val perspectiveFit = (cameraDistance - ORB_DEPTH) / cameraDistance
        val radius = minOf(right - left, bottom - top) * .5f * unit * perspectiveFit
        out[0] = radius
        out[5] = radius
        out[10] = radius
        out[12] = ((left + right) * .5f - frame.width * .5f) * unit * perspectiveFit
        out[13] = (frame.height * .5f - (top + bottom) * .5f) * unit * perspectiveFit
        out[14] = ORB_DEPTH
        return out
    }

    fun ringAngleDegrees(index: Int, seconds: Float, motion: Boolean): Float {
        require(index in 0..3)
        val time = if (motion && seconds.isFinite()) seconds else 0f
        return time * (3.5f + index * 1.2f)
    }

    fun ringTransform(rest: FloatArray, index: Int, seconds: Float, motion: Boolean,
                      out: FloatArray = FloatArray(16)): FloatArray {
        require(rest.size >= 16 && out.size >= 16)
        val angle = Math.toRadians(ringAngleDegrees(index, seconds, motion).toDouble())
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        for (row in 0..3) {
            val x = rest[row]
            val y = rest[row + 4]
            out[row] = x * c + y * s
            out[row + 4] = -x * s + y * c
            out[row + 8] = rest[row + 8]
            out[row + 12] = rest[row + 12]
        }
        return out
    }

    private fun identity(out: FloatArray) {
        require(out.size >= 16)
        out.fill(0f, 0, 16)
        out[0] = 1f
        out[5] = 1f
        out[10] = 1f
        out[15] = 1f
    }
}
