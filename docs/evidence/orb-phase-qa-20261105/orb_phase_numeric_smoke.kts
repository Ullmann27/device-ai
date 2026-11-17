import kotlin.math.PI
import kotlin.math.sin

// Formula probe, not full Android compilation.
val inputs = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
    Float.MAX_VALUE, -Float.MAX_VALUE, -9f, -20f, 0f, 3f, 1_000_000f)
fun before(v: Float) = if (v.isFinite()) v else 0f
fun after(v: Float) = if (v.isFinite() && v in -1_000_000f..1_000_000f) v else 0f
fun alpha(phase: Float, pulse: Float = 0f, processing: Boolean = false): Float {
    val breath = .5f + .5f * sin(phase * 2f * PI.toFloat() * .35f)
    val k = .85f + .15f * breath + .25f * pulse.coerceIn(0f, 1f) +
        (if (processing) .12f else 0f)
    return (k / 1.37f).coerceIn(0f, 1f)
}
val red = inputs.filter { !alpha(before(it)).isFinite() }
check(red.toSet() == setOf(Float.MAX_VALUE, -Float.MAX_VALUE))
var total = 0
for (phase in inputs) for (pulse in listOf(0f, .5f, 1f)) for (busy in listOf(false, true)) {
    val actual = alpha(after(phase), pulse, busy)
    check(actual.isFinite() && actual in 0f..1f)
    total++
}
val idle = listOf(0f, .7f, 1.4f, 3f).map { alpha(it) }
val active = alpha(0f, 1f, true)
check(idle.max() - idle.min() > .02f)
check(idle.all { it < active - .15f })
println("RED old finite guard: ${red.size} overflows")
println("GREEN updated formula: $total/$total cases")
println("GREEN idle vs active: ${idle.size} phases")
