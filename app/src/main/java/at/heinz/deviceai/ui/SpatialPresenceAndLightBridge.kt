package at.heinz.deviceai.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs

/**
 * Opt-in game-rotation parallax; neither camera nor location permission is used.
 * Samples are bounded and delivered to the existing view clock, never a second timer.
 */
internal class SpatialPresenceAndLightBridge(context: Context, private val invalidate: () -> Unit) :
    SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var registered = false
    private var neutralPitch = Float.NaN
    private var neutralRoll = Float.NaN
    private var lastFrameAt = 0L

    var tiltX = 0f
        private set
    var tiltY = 0f
        private set

    fun start() {
        if (registered) return
        val service = manager ?: return
        val input = sensor ?: return
        neutralPitch = Float.NaN
        neutralRoll = Float.NaN
        registered = runCatching {
            service.registerListener(this, input, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
    }

    fun stop() {
        if (registered) manager?.unregisterListener(this)
        registered = false
        neutralPitch = Float.NaN
        neutralRoll = Float.NaN
        lastFrameAt = 0L
        tiltX = 0f
        tiltY = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!registered || event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR || event.values.size < 4) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val pitch = orientation[1]
        val roll = orientation[2]
        if (!pitch.isFinite() || !roll.isFinite()) return
        if (!neutralPitch.isFinite()) {
            neutralPitch = pitch
            neutralRoll = roll
            return
        }
        val targetX = normalizeAngle(roll - neutralRoll).div(.21f).coerceIn(-1f, 1f)
        val targetY = normalizeAngle(pitch - neutralPitch).div(.21f).coerceIn(-1f, 1f)
        val nextX = tiltX + (targetX - tiltX) * .14f
        val nextY = tiltY + (targetY - tiltY) * .14f
        val changed = abs(nextX - tiltX) + abs(nextY - tiltY) > .009f
        tiltX = nextX
        tiltY = nextY
        val now = SystemClock.uptimeMillis()
        if (changed && now - lastFrameAt >= 33L) {
            lastFrameAt = now
            invalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun normalizeAngle(value: Float): Float =
        kotlin.math.atan2(kotlin.math.sin(value), kotlin.math.cos(value))
}
