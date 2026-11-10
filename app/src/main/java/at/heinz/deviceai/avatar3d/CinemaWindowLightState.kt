package at.heinz.deviceai.avatar3d

/**
 * Latest fly-in window light published by [at.heinz.deviceai.avatar.CinemaWindowGazeBridge].
 * The Filament fill light reads this when the work-window host has not yet marked the
 * window open. Parameters are restored by [reset] / a CLOSED sample.
 */
internal object CinemaWindowLightState {
    @Volatile var accentArgb: Int? = null
        private set
    @Volatile var gazeX: Float = 0f
        private set
    @Volatile var open: Boolean = false
        private set

    fun apply(accentArgb: Int?, gazeX: Float, open: Boolean) {
        this.accentArgb = if (open) accentArgb else null
        this.gazeX = if (open && gazeX.isFinite()) gazeX.coerceIn(-1f, 1f) else 0f
        this.open = open
    }

    fun reset() = apply(null, 0f, false)
}
