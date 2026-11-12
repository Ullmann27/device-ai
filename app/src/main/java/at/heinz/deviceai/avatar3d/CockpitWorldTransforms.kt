package at.heinz.deviceai.avatar3d

import kotlin.math.cos
import kotlin.math.sin

/** Column-major unified scene transforms. */
object CockpitWorldTransforms {
    fun avatar(frame: CockpitStageFrame, left: Float, top: Float, right: Float, bottom: Float,
               fitted: AvatarFraming.Window, focus: AvatarGlbContract.Focus,
               out: FloatArray = FloatArray(16)): FloatArray {
        require(frame.valid())
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            right > left && bottom > top)
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
        require(out.size >= 16)
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            right > left && bottom > top)
        frame.orbTransform(left, top, right, bottom).copyInto(out)
        return out
    }

    internal fun ringPose(index: Int, phase: Float, motion: Boolean): CockpitOrbMotion.Pose =
        CockpitOrbMotion.sample(index, if (motion) phase else .4f)

    /** rest × Rx(-pitch) × Ry(yaw). */
    fun ringTransform(rest: FloatArray, index: Int, phase: Float, motion: Boolean,
                      out: FloatArray = FloatArray(16)): FloatArray {
        require(rest.size >= 16 && out.size >= 16)
        val pose = ringPose(index, phase, motion)
        val yaw = Math.toRadians(pose.yawDegrees.toDouble())
        val pitch = Math.toRadians(-pose.pitchDegrees.toDouble())
        val cy = cos(yaw).toFloat(); val sy = sin(yaw).toFloat()
        val cx = cos(pitch).toFloat(); val sx = sin(pitch).toFloat()
        for (row in 0..3) {
            val x = rest[row]
            val y = rest[row + 4]
            val z = rest[row + 8]
            out[row] = x * cy + y * sx * sy - z * cx * sy
            out[row + 4] = y * cx + z * sx
            out[row + 8] = x * sy - y * sx * cy + z * cx * cy
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
