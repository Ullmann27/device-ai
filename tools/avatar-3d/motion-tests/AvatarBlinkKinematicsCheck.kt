package at.heinz.deviceai.avatar3d

import kotlin.math.abs

/** Standalone, deterministic host probe for the *production* AvatarIdleMotion class.
 * Compile it together with the exact repository source; no Android, JUnit, GLB or GPU required.
 */
fun main() {
    fun sample(t: Float): AvatarIdleMotion = AvatarIdleMotion().also { it.update(t) }
    fun blink(t: Float): Float = sample(t).blinkWeight
    fun near(a: Float, b: Float, error: Float = 1e-4f) {
        check(abs(a - b) <= error) { "expected $a ~= $b (+/- $error)" }
    }
    fun smoothAt(t: Float, tolerance: Float = 1.0f) {
        val delta = .001f
        val left = (blink(t) - blink(t - delta)) / delta
        val right = (blink(t + delta) - blink(t)) / delta
        check(abs(left - right) < tolerance) { "blink velocity jumps at $t: before=$left after=$right" }
    }
    // RED before correction: triangular lid closure introduces a sharp slope at its start.
    smoothAt(4.45f)
    smoothAt(4.505f)
    smoothAt(4.6f)
    // The pre-existing 150-ms blink interval and extra closures must remain bounded.
    listOf(4.449f, 4.601f, 4.149f + 9.2f, 4.301f + 9.2f).forEach { near(blink(it), 0f, .001f) }
    check(blink(4.505f) > .99f) { "primary closure is no longer complete" }
    check(blink(13.405f) > .99f) { "fifth-group secondary closure is no longer complete" }
    check(blink(8.825f) < .001f) { "secondary closure appears outside its designated group" }
    // The single 460-second source clock is stable on exact negative/positive wraps.
    for (wrap in listOf(0f, -0.0f, 460f, -460f, -920f)) {
        val s = sample(wrap)
        near(s.blinkWeight, 0f); near(s.eyeYaw, 0f); near(s.eyePitch, 0f)
        near(s.headYaw, 0f); near(s.headPitch, 0f)
    }
    val previous = sample(17.25f)
    val repeated = sample(17.25f)
    near(previous.headYaw, repeated.headYaw)
    near(previous.headPitch, repeated.headPitch)
    near(previous.eyeYaw, repeated.eyeYaw)
    near(previous.eyePitch, repeated.eyePitch)
    near(previous.blinkWeight, repeated.blinkWeight)
    for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
        val a = sample(invalid)
        near(a.blinkWeight, 0f); near(a.headYaw, 0f); near(a.eyeYaw, 0f)
    }
    for (i in 0..4600) {
        val w = blink(i / 10f)
        check(w.isFinite() && w in 0f..1f)
    }
    println("PASS: smooth primary closure/reopen and cycle seam; 150-ms profile, extra closure, bounded deterministic motion")
}
