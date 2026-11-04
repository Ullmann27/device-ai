package at.heinz.deviceai.avatar3d
fun main() {
    for (t in floatArrayOf(0.0f, -0.0f, -460.0f, -920.0f, -0.5f, 460.0f, 920.0f, Float.NaN, Float.POSITIVE_INFINITY)) {
        val motion = AvatarIdleMotion()
        try {
            motion.update(t)
            println("t=$t bits=${t.toRawBits()} OK head=${motion.headYaw},${motion.headPitch} eyes=${motion.eyeYaw},${motion.eyePitch} blink=${motion.blinkWeight}")
        } catch (error: Throwable) {
            println("t=$t bits=${t.toRawBits()} ERROR ${error.javaClass.name}: ${error.message}")
            error.stackTrace.take(2).forEach { println("  at $it") }
        }
    }
}
