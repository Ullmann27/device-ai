package x.ui
// Kommentar: "CPU 18%" in a comment is not shown.
/** Example: "Kachel 1" in KDoc is documentation only. */
object GoodCard {
    const val NOT_AVAILABLE = "nicht verfügbar"
    val dash = "—"
    val missing = "Quelle fehlt"
    val margin = 16f            // dimension constant
    val alpha = .65f
    const val DURATION_MS = 600L
    fun cpu(v: Int?) = v?.let { "CPU $it %" } ?: NOT_AVAILABLE
    fun temp(t: Float) = String.format("%.1f °C", t)
    fun ram(used: Long) = "RAM ${used} GB"
    val title = "Systemstatus"
}
