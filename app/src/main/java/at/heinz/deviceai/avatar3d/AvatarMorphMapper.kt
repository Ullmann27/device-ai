package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin

/** Presentation mapping only. No state derivation, speech clock, listener or retained text. */
class AvatarMorphMapper(targetNames: List<String>) {
    private val indices = targetNames.withIndex().associate { it.value to it.index }
    val weights = FloatArray(targetNames.size)
    var headYaw = 0f; private set
    var headPitch = 0f; private set
    var eyeYaw = 0f; private set
    var eyePitch = 0f; private set
    var jawPitch = 0f; private set

    init { require(targetNames.toSet().size == targetNames.size && indices.keys == AvatarGlbContract.TARGETS.toSet()) }

    fun update(pose: AvatarPose, seconds: Float, motion: Boolean) {
        weights.fill(0f)
        fun weight(name: String, value: Float) { weights[indices.getValue(name)] = finite(value, 0f, 1f) }
        val gx = finite(pose.gazeX, -1f, 1f)
        val gy = finite(pose.gazeY, -1f, 1f)
        // Right-handed Y-up +Z front: positive yaw goes viewer right, negative pitch looks up.
        headYaw = gx * 8f; headPitch = -gy * 5f
        eyeYaw = gx * 17f; eyePitch = -gy * 10f
        jawPitch = 0f
        when (pose.expression) {
            AvatarExpression.ATTENTIVE -> weight("BrowUp", .20f)
            AvatarExpression.CURIOUS -> weight("BrowUp", .32f)
            AvatarExpression.FOCUSED -> weight("BrowDown", .18f)
            AvatarExpression.CONCERNED -> weight("BrowDown", .28f)
            AvatarExpression.NEUTRAL -> Unit
        }
        if (!motion) return
        // Cosmetic idle motion, not observed gaze or invented speech/work. The existing view
        // owns the clock and stops it on pause/reduced motion. Actual task/observed gaze wins.
        if (pose.state == AvatarState.IDLE && !pose.gazeObserved) {
            // Integer harmonics keep the view's existing 460-second clock wrap continuous.
            val t = if (seconds.isFinite()) seconds.toDouble() % 460.0 else 0.0
            val phase = t * (2.0 * PI / 460.0)
            headYaw += (1.20 * sin(phase * 27) + .35 * sin(phase * 65)).toFloat()
            headPitch += (.62 * sin(phase * 34) + .20 * sin(phase * 67)).toFloat()
            eyeYaw += (2.10 * sin(phase * 52) + .55 * sin(phase * 83)).toFloat()
            eyePitch += (.95 * sin(phase * 39) + .30 * sin(phase * 78)).toFloat()
        }
        val jaw = finite(pose.jawOpen, 0f, 1f)
        val cueWeight = if (pose.viseme == Viseme.SIL) 0f else finite(pose.visemeWeight, 0f, 1f)
        val cueOpening = sharedOpeningRatio(pose.viseme) * cueWeight
        // The pinned CC0 recipes already include mouth-open inside each vowel target.
        // Compose that common basis once; retain the independent lip shape unchanged.
        weight("JawOpen", (jaw - cueOpening).coerceAtLeast(0f))
        jawPitch = maxOf(jaw, cueOpening) * 12f
        // The existing reducer expires stale cues/audio and resets interrupted utterances.
        if (pose.viseme != Viseme.SIL) weight(pose.viseme.name, cueWeight)
        val t = if (seconds.isFinite()) ((seconds % 4.6f) + 4.6f) % 4.6f else 0f
        val blink = if (t > 4.42f) (1f - abs(t - 4.51f) / .09f).coerceIn(0f, 1f) else 0f
        weight("BlinkLeft", blink); weight("BlinkRight", blink)
    }

    private fun finite(value: Float, min: Float, max: Float) = if (value.isFinite()) value.coerceIn(min, max) else 0f

    /** PR404 build_bust.py at 0862cfc8: authored mouth-open coefficient / JawOpen's .8.
     * Bound to the packaged asset family; a different morph recipe requires a new review. */
    private fun sharedOpeningRatio(viseme: Viseme): Float = when (viseme) {
        Viseme.SIL, Viseme.PP, Viseme.SS -> 0f
        Viseme.FF -> .06f / .8f
        Viseme.TH -> .18f / .8f
        Viseme.DD -> .20f / .8f
        Viseme.KK -> .30f / .8f
        Viseme.CH -> .15f / .8f
        Viseme.NN -> .11f / .8f
        Viseme.RR -> .22f / .8f
        Viseme.AA -> .65f / .8f
        Viseme.E -> .23f / .8f
        Viseme.I, Viseme.U -> .10f / .8f
        Viseme.O -> .36f / .8f
    }
}
