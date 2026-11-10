package at.heinz.deviceai.avatar3d

/** Canonical projected window geometry. */
internal object CinemaWindowLightState {
    data class Snapshot(
        val accentArgb: Int? = null,
        val gazeX: Float = 0f,
        val gazeY: Float = 0f,
        val open: Boolean = false,
    )

    @Volatile var snapshot = Snapshot()
        private set
    val accentArgb: Int? get() = snapshot.accentArgb
    val gazeX: Float get() = snapshot.gazeX
    val gazeY: Float get() = snapshot.gazeY
    val open: Boolean get() = snapshot.open

    fun apply(accentArgb: Int?, gazeX: Float, open: Boolean, gazeY: Float = 0f) {
        snapshot = if (open) Snapshot(
            accentArgb,
            if (gazeX.isFinite()) gazeX.coerceIn(-1f, 1f) else 0f,
            if (gazeY.isFinite()) gazeY.coerceIn(-1f, 1f) else 0f,
            true,
        ) else Snapshot()
    }

    fun reset() = apply(null, 0f, false)
}
