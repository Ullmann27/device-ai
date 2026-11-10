package at.heinz.deviceai.avatar3d

import kotlin.math.pow

/** Shares the existing avatar fill light; never creates a second GPU light. */
internal object SpatialWindowLight {
    data class Tint(val red: Float, val green: Float, val blue: Float, val lumens: Float)
    private const val BASE_RED = .18f
    private const val BASE_GREEN = .82f
    private const val BASE_BLUE = 1f
    private const val MAX_LUMENS = 36_000f

    fun tint(accentArgb: Int?, energy: Float, open: Boolean, allowed: Boolean): Tint {
        val base = AvatarCoreLighting.intensity(energy, allowed)
        if (!allowed || !open || accentArgb == null) return Tint(
            BASE_RED, BASE_GREEN, BASE_BLUE, base)
        val a = .32f
        fun linear(channel: Int): Float {
            val value = (channel and 255) / 255f
            return if (value <= .04045f) value / 12.92f
                else ((value + .055f) / 1.055f).pow(2.4f)
        }
        val wr = linear(accentArgb ushr 16)
        val wg = linear(accentArgb ushr 8)
        val wb = linear(accentArgb)
        return Tint(
            BASE_RED + (wr - BASE_RED) * a,
            BASE_GREEN + (wg - BASE_GREEN) * a,
            BASE_BLUE + (wb - BASE_BLUE) * a,
            (base + 4_000f).coerceAtMost(MAX_LUMENS)
        )
    }
}
