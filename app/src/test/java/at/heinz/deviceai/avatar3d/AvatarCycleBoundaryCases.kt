package at.heinz.deviceai.avatar3d

/** Shared by JUnit and the no-Android host runner; invokes the actual production sampler. */
internal object AvatarCycleBoundaryCases {
    private fun sample(seconds: Float): FloatArray {
        val motion = AvatarIdleMotion()
        motion.update(seconds)
        return floatArrayOf(motion.headYaw, motion.headPitch, motion.eyeYaw, motion.eyePitch, motion.blinkWeight)
    }

    private fun same(expected: FloatArray, actual: FloatArray) {
        check(expected.size == actual.size)
        expected.indices.forEach { index ->
            check(actual[index].isFinite() && kotlin.math.abs(expected[index] - actual[index]) <= .00001f) {
                "component=$index expected=${expected[index]} actual=${actual[index]}"
            }
        }
    }

    fun positiveCycleBoundaries() {
        val zero = sample(0f)
        for (t in listOf(460f, 920f, 4_600f)) same(zero, sample(t))
    }

    fun negativeZero() {
        same(sample(0f), sample(Float.fromBits(Int.MIN_VALUE)))
    }

    fun negativeCycleBoundaries() {
        val zero = sample(0f)
        for (t in listOf(-460f, -920f, -4_600f)) same(zero, sample(t))
    }

    fun nearZeroValuesRemainFiniteAndBounded() {
        for (t in listOf(-Float.MIN_VALUE, -1e-12f, -1e-6f, 1e-6f, Float.MIN_VALUE)) {
            val pose = sample(t)
            check(pose.all { it.isFinite() })
            check(kotlin.math.abs(pose[0]) <= 1.55f && kotlin.math.abs(pose[1]) <= .82f)
            check(kotlin.math.abs(pose[2]) <= 3.5f && kotlin.math.abs(pose[3]) <= 1.4f)
            check(pose[4] in 0f..1f)
        }
    }

    fun boundariesAfterEarlierFramesRemainDeterministic() {
        val motion = AvatarIdleMotion()
        for (t in listOf(13.425f, 0f, 459.99f, 460f, 84.22f)) {
            motion.update(t)
            same(sample(t), floatArrayOf(motion.headYaw, motion.headPitch, motion.eyeYaw, motion.eyePitch, motion.blinkWeight))
        }
    }
}

/** A non-JUnit runner for these same five cases; it does not replace Android or asset validation. */
fun main() {
    val cases = linkedMapOf<String, () -> Unit>(
        "positive cycle boundaries" to AvatarCycleBoundaryCases::positiveCycleBoundaries,
        "negative zero" to AvatarCycleBoundaryCases::negativeZero,
        "negative cycle boundaries" to AvatarCycleBoundaryCases::negativeCycleBoundaries,
        "near-zero finite bounds" to AvatarCycleBoundaryCases::nearZeroValuesRemainFiniteAndBounded,
        "deterministic boundary sampling" to AvatarCycleBoundaryCases::boundariesAfterEarlierFramesRemainDeterministic
    )
    var failures = 0
    for ((name, checkCase) in cases) {
        try { checkCase(); println("PASS $name") }
        catch (failure: Exception) {
            failures++
            println("FAIL $name: ${failure.javaClass.simpleName}: ${failure.message}")
        }
    }
    println("RESULT passed=${cases.size - failures} failed=$failures total=${cases.size}")
    check(failures == 0) { "Cycle-boundary acceptance failed" }
}
