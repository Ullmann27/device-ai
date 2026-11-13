package at.heinz.deviceai.avatar3d

import java.lang.reflect.InvocationTargetException
import kotlin.math.abs
import kotlin.math.sqrt

fun main() {
    val method = CockpitStageFrame::class.java.methods.singleOrNull {
        it.name == "monitorTransform" && it.parameterTypes.contentEquals(arrayOf(java.lang.Float.TYPE))
    }
    check(method != null) { "REGRESSION: no separate monitor-only presentation transform; renderer must not mutate stage yaw" }
    fun transform(frame: CockpitStageFrame, turn: Float): FloatArray =
        try { method.invoke(frame, turn) as FloatArray }
        catch (failure: InvocationTargetException) { throw failure.targetException }
    var samples = 0
    val layouts = listOf(
        CockpitStageFrame(852f, 480f, 270f, 180f, 590f, 390f),
        CockpitStageFrame(412f, 915f, 15f, 350f, 397f, 650f),
        CockpitStageFrame(1536f, 1024f, 400f, 400f, 1100f, 800f))
    for (base in layouts) for (yaw in listOf(-8f, -3f, 0f, 8f)) for (pitch in listOf(-12f, 0f, 12f)) {
        val frame = base.copy(yawDegrees = yaw, pitchDegrees = pitch)
        check(frame.valid())
        val snapshot = frame.copy()
        val rest = frame.monitorTransform()
        val quad = frame.quad()
        val orb = frame.orbTransform(20f, 20f, 140f, 140f)
        check(transform(frame, 0f).contentEquals(rest)) { "Rest transform changed" }
        for (step in 0..100) {
            val matrix = transform(frame, -100f + step)
            check(matrix.all(Float::isFinite))
            check(frame == snapshot && frame.valid()) { "Stage was changed by monitor presentation" }
            check(frame.quad().contentEquals(quad)) { "Rest hit geometry changed" }
            check(frame.orbTransform(20f, 20f, 140f, 140f).contentEquals(orb)) { "Orb moved" }
            for (i in 12..15) check(matrix[i].toBits() == rest[i].toBits()) { "Monitor pivot moved" }
            fun norm(m: FloatArray, c: Int) = sqrt((0..2).sumOf { m[c * 4 + it].toDouble() * m[c * 4 + it] })
            for (column in 0..2) check(abs(norm(matrix, column) - norm(rest, column)) < 1e-6) { "Nonuniform scale distorted by turn" }
            for ((a,b) in listOf(0 to 1, 1 to 2, 0 to 2)) {
                val dot = (0..2).sumOf { matrix[a * 4 + it].toDouble() * matrix[b * 4 + it] }
                check(abs(dot) < 1e-6) { "Monitor axes sheared" }
            }
            samples++
        }
        check(transform(frame, 0f).contentEquals(rest)) { "Final rest is not exact" }
    }
    val frame = layouts.first()
    var rejects = 0
    for (turn in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -181f, 181f)) {
        try { transform(frame, turn); error("Invalid turn admitted: $turn") }
        catch (expected: IllegalArgumentException) { rejects++ }
    }
    val invalid = frame.copy(yawDegrees = -103f)
    check(!invalid.valid()) { "The existing frame gate was weakened" }
    try { transform(invalid, 100f); error("Invalid stage admitted") }
    catch (expected: IllegalArgumentException) { rejects++ }
    check(!transform(frame, -100f).contentEquals(frame.monitorTransform())) { "Presentation was silently disabled" }
    println("PASS: $samples actual production transform samples; $rejects rejected invalid inputs; exact-rest/unchanged-frame/orb/quad/scale/orthogonality verified")
}
