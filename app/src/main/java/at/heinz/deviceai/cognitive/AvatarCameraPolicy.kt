package at.heinz.deviceai.cognitive

import kotlin.math.abs
import kotlin.math.exp

/** Capture lifecycle only, not the cognitive task state machine. */
internal enum class CameraTrackingState {
    OFF, STARTING, TRACKING, NO_FACE, AMBIGUOUS, PERMISSION_REQUIRED,
    NOT_FOREGROUND, UNSUPPORTED, IN_USE, ERROR
}

/** Ephemeral hardware face-center observation, not eye tracking or identification. */
internal data class CameraGazeSample(val x: Float, val y: Float, val confidence: Int,
                                     val observedAtElapsedMillis: Long)
internal data class CameraFaceBox(val left: Int, val top: Int, val right: Int,
                                  val bottom: Int, val score: Int)
internal data class CameraFrameGeometry(val left: Int, val top: Int, val right: Int, val bottom: Int,
                                        val sensorOrientationDegrees: Int, val displayRotationDegrees: Int)

/** Front-camera sensor coordinates -> mirrored display coordinates, x right/y down.
 * Only advertised cardinal rotations and valid active-array bounds are accepted. */
internal object CameraCoordinateMapping {
    fun normalized(face: CameraFaceBox, geometry: CameraFrameGeometry): Pair<Float, Float>? {
        val width = geometry.right.toLong() - geometry.left
        val height = geometry.bottom.toLong() - geometry.top
        if (width !in 1..100_000 || height !in 1..100_000 ||
            geometry.sensorOrientationDegrees !in listOf(0, 90, 180, 270) ||
            geometry.displayRotationDegrees !in listOf(0, 90, 180, 270) ||
            face.score !in 60..100 || face.left >= face.right || face.top >= face.bottom ||
            face.left < geometry.left || face.top < geometry.top ||
            face.right > geometry.right || face.bottom > geometry.bottom) return null
        val x = ((face.left.toLong() + face.right) / 2.0 - geometry.left) / width * 2.0 - 1.0
        val y = ((face.top.toLong() + face.bottom) / 2.0 - geometry.top) / height * 2.0 - 1.0
        val rotated = when ((geometry.sensorOrientationDegrees + geometry.displayRotationDegrees) % 360) {
            0 -> x to y
            90 -> -y to x
            180 -> -x to -y
            else -> y to -x
        }
        return (-rotated.first).toFloat().coerceIn(-1f, 1f) to rotated.second.toFloat().coerceIn(-1f, 1f)
    }
}

/** No image or face identity is accepted or stored. A permission bit alone never
 * starts a session. The native adapter must close capture whenever a gate closes. */
internal class AvatarCameraPolicy(private val elapsedMillis: () -> Long) {
    @Volatile var state = CameraTrackingState.OFF; private set
    private var generation = 0L
    private var active: Long? = null
    private var lastFrameAt = 0L
    private var lastMotionAt: Long? = null
    private var sample: CameraGazeSample? = null
    private var x = 0f
    private var y = 0f

    @Synchronized fun start(ownerEnabled: Boolean, foreground: Boolean, permission: Boolean,
                            hardwareSupported: Boolean): Long? {
        stop()
        state = when {
            !ownerEnabled -> CameraTrackingState.OFF
            !foreground -> CameraTrackingState.NOT_FOREGROUND
            !permission -> CameraTrackingState.PERMISSION_REQUIRED
            !hardwareSupported -> CameraTrackingState.UNSUPPORTED
            else -> CameraTrackingState.STARTING
        }
        if (state != CameraTrackingState.STARTING) return null
        val now = elapsedMillis()
        if (now < 0) { stop(CameraTrackingState.ERROR); return null }
        lastFrameAt = now
        return generation.also { active = it }
    }

    @Synchronized fun stop(reason: CameraTrackingState = CameraTrackingState.OFF) {
        generation++; active = null; sample = null; x = 0f; y = 0f; lastMotionAt = null; state = reason
    }

    @Synchronized fun isCurrent(token: Long): Boolean = active == token

    @Synchronized fun updateGate(token: Long, ownerEnabled: Boolean, foreground: Boolean,
                                 permission: Boolean): Boolean {
        if (!isCurrent(token)) return false
        val reason = when {
            !ownerEnabled -> CameraTrackingState.OFF
            !foreground -> CameraTrackingState.NOT_FOREGROUND
            !permission -> CameraTrackingState.PERMISSION_REQUIRED
            else -> return true
        }
        stop(reason); return false
    }

    @Synchronized fun acceptFrame(token: Long, faces: List<CameraFaceBox>,
                                   geometry: CameraFrameGeometry): CameraGazeSample? {
        if (!isCurrent(token)) return null
        val now = elapsedMillis()
        if (now < lastFrameAt) { stop(CameraTrackingState.ERROR); return null }
        lastFrameAt = now
        if (faces.size != 1) {
            clearFace(if (faces.isEmpty()) CameraTrackingState.NO_FACE else CameraTrackingState.AMBIGUOUS)
            return null
        }
        val target = CameraCoordinateMapping.normalized(faces[0], geometry)
        if (target == null) { clearFace(CameraTrackingState.NO_FACE); return null }
        val previousAt = lastMotionAt
        // Duplicate or unexpectedly rapid metadata does not accelerate motion.
        if (previousAt != null && now - previousAt < MIN_MOTION_INTERVAL_MS) return sample
        val dt = if (previousAt == null) 60L else (now - previousAt).coerceIn(1, 250)
        val alpha = (1.0 - exp(-dt / 120.0)).toFloat()
        val maxDelta = 2f * dt / 1000f
        fun step(current: Float, targetValue: Float): Float {
            val difference = targetValue - current
            if (abs(difference) < 0.015f) return current
            return (current + (difference * alpha).coerceIn(-maxDelta, maxDelta)).coerceIn(-1f, 1f)
        }
        x = step(x, target.first); y = step(y, target.second); lastMotionAt = now
        state = CameraTrackingState.TRACKING
        return CameraGazeSample(x, y, faces[0].score, now).also { sample = it }
    }

    @Synchronized fun latest(): CameraGazeSample? {
        val current = sample ?: return null
        val age = elapsedMillis() - current.observedAtElapsedMillis
        if (age !in 0..GAZE_FRESHNESS_MS) { clearFace(CameraTrackingState.NO_FACE); return null }
        return current
    }

    @Synchronized fun expireHeartbeat(token: Long): Boolean {
        if (!isCurrent(token)) return false
        val age = elapsedMillis() - lastFrameAt
        if (age < 0 || age >= FRAME_HEARTBEAT_MS) { stop(CameraTrackingState.ERROR); return true }
        return false
    }

    private fun clearFace(reason: CameraTrackingState) {
        sample = null; x = 0f; y = 0f; lastMotionAt = null; state = reason
    }

    companion object {
        const val GAZE_FRESHNESS_MS = 750L
        const val FRAME_HEARTBEAT_MS = 5_000L
        const val MIN_MOTION_INTERVAL_MS = 50L
    }
}
