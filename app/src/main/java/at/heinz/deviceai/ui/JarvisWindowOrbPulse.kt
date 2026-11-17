package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.sin

/** One actual admitted work-window opening animates the existing original orb. */
internal object JarvisWindowOrbPulse {
    const val DURATION_MS = 650L

    fun energy(ageMillis: Long): Float {
        if (ageMillis < 0L || ageMillis >= DURATION_MS) return 0f
        val progress = ageMillis.toFloat() / DURATION_MS
        return (.72f * sin(PI.toFloat() * progress)).coerceIn(0f, .72f)
    }
}
