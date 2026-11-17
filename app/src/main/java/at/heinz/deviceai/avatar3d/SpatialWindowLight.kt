package at.heinz.deviceai.avatar3d

import kotlin.math.pow

/** Shares the existing avatar fill light; never creates a second GPU light. */
internal object SpatialWindowLight {
    data class Tint(
        val red: Float,
        val green: Float,
        val blue: Float,
        val lumens: Float,
        val positionX: Float = REST_X,
        val positionY: Float = REST_Y,
        val positionZ: Float = REST_Z,
        val side: Float = 0f,
        val roughness: Float = AUTHORED_SKIN_ROUGHNESS,
    )
    private const val BASE_RED = .18f
    private const val BASE_GREEN = .82f
    private const val BASE_BLUE = 1f
    private const val MAX_LUMENS = 36_000f
    /** Rest pose of the existing Filament core fill (viewer-right / +X). */
    const val REST_X = .32f
    const val REST_Y = .35f
    const val REST_Z = -.16f
    /** How far the fill may travel toward the window side, in the same units as the rest pose. */
    const val SIDE_SPAN = .38f
    const val HEIGHT_SPAN = .28f
    /** DeepIndigoSkin roughnessFactor in the packaged original GLB. */
    const val AUTHORED_SKIN_ROUGHNESS = .52f
    /** Slightly tighter highlight while a window is open; restored when it ends. */
    const val WINDOW_ROUGHNESS = .46f

    fun tint(accentArgb: Int?, energy: Float, open: Boolean, allowed: Boolean, gazeX: Float = 0f, gazeY: Float = 0f): Tint {
        val base = AvatarCoreLighting.intensity(energy, allowed)
        if (!allowed || !open) return Tint(
            BASE_RED, BASE_GREEN, BASE_BLUE, base,
            REST_X, REST_Y, REST_Z, 0f, AUTHORED_SKIN_ROUGHNESS)
        val side = if (gazeX.isFinite()) gazeX.coerceIn(-1f, 1f) else 0f
        val positionX = REST_X + side * SIDE_SPAN
        val elevation = if (gazeY.isFinite()) gazeY.coerceIn(-1f, 1f) else 0f
        val positionY = REST_Y + elevation * HEIGHT_SPAN
        if (accentArgb == null) return Tint(
            BASE_RED, BASE_GREEN, BASE_BLUE, base,
            positionX, positionY, REST_Z, side, WINDOW_ROUGHNESS)
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
            (base + 4_000f).coerceAtMost(MAX_LUMENS),
            positionX, positionY, REST_Z, side, WINDOW_ROUGHNESS
        )
    }
}
