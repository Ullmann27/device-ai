package at.heinz.deviceai.avatar3d

/** Canonical projected window geometry. */
internal object CinemaWindowLightState {
    data class Snapshot(
        val accentArgb: Int? = null,
        val gazeX: Float = 0f,
        val gazeY: Float = 0f,
        val open: Boolean = false,
    ) {
        fun accentOr(fallback: Int?): Int? = if (open) accentArgb ?: fallback else fallback
    }

    @Volatile var snapshot = Snapshot()
        private set
    val accentArgb: Int? get() = snapshot.accentArgb
    val gazeX: Float get() = snapshot.gazeX
    val gazeY: Float get() = snapshot.gazeY
    val open: Boolean get() = snapshot.open

    /** Gate every projected light source. */
    fun resolve(enabled: Boolean, accentArgb: Int?, open: Boolean,
                gazeX: Float = 0f, gazeY: Float = 0f): Snapshot {
        if (!enabled) return Snapshot()
        val projected = snapshot
        if (projected.open) return projected.copy(accentArgb = projected.accentOr(accentArgb))
        if (!open) return Snapshot()
        return Snapshot(accentArgb,
            if (gazeX.isFinite()) gazeX.coerceIn(-1f, 1f) else 0f,
            if (gazeY.isFinite()) gazeY.coerceIn(-1f, 1f) else 0f,
            true)
    }

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
