package at.heinz.deviceai.cognitive

/** Stub: domain-neutral ephemeral owner-statement timeline (not yet implemented). */
internal object OwnerStatementTimeline {
    data class Entry(val display: String, val unit: String?, val original: Long, val current: Long,
                     val future: Long?, val changed: Boolean)
    data class Projection(val entries: List<Entry>) {
        fun entry(display: String): Entry? = entries.firstOrNull { it.display == display }
        fun render(): String = ""
    }
    fun project(previousOwnerTurns: List<String>, currentTurn: String): Projection? = null
}
