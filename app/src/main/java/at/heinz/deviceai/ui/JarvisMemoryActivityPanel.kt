package at.heinz.deviceai.ui

import at.heinz.deviceai.learning.ContinuumMemoryEvent
import at.heinz.deviceai.learning.ContinuumMemoryEventListener
import at.heinz.deviceai.learning.ContinuumMemoryStats
import at.heinz.deviceai.learning.MemoryStored
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Daten der "Memory Activity"-Karte (ref5 unten). Nur echte Werte: null = nicht verbunden → "—".
 * [hourly] = 24 Stundenbalken heute (Anzahl echter Gedächtnis-Ereignisse), null = keine Ereignisquelle.
 */
data class MemoryActivitySnapshot(
    val newToday: Int? = null,
    val contextUpdates: Int? = null,
    val hypotheses: Int? = null,
    val total: Int? = null,
    val hourly: IntArray? = null,
) {
    val online: Boolean get() = newToday != null || contextUpdates != null || hypotheses != null || total != null || hourly != null

    /** Zeilen (Wert, Beschriftung) in ref5-Reihenfolge. */
    fun rows(): List<Pair<String, String>> = listOf(
        fmt(newToday, plus = true) to "New memories (today)",
        fmt(contextUpdates) to "Context updates",
        fmt(hypotheses) to "Hypotheses",
        fmt(total) to "Total memories",
    )

    override fun equals(other: Any?) = other is MemoryActivitySnapshot && newToday == other.newToday &&
        contextUpdates == other.contextUpdates && hypotheses == other.hypotheses && total == other.total &&
        (hourly ?: IntArray(0)).contentEquals(other.hourly ?: IntArray(0)) && (hourly == null) == (other.hourly == null)
    override fun hashCode() = listOf(newToday, contextUpdates, hypotheses, total, hourly?.contentHashCode()).hashCode()

    companion object {
        const val NONE = "—"
        val OFFLINE = MemoryActivitySnapshot()
        private fun fmt(v: Int?, plus: Boolean = false) = when { v == null -> NONE; plus && v > 0 -> "+$v"; else -> v.toString() }

        /** Aus [ContinuumMemoryStats] (Ledger) und optional dem [MemoryActivityTracker] (Ereignisse seit App-Start). */
        fun from(stats: ContinuumMemoryStats?, tracker: MemoryActivityTracker?): MemoryActivitySnapshot = MemoryActivitySnapshot(
            newToday = stats?.newToday, contextUpdates = tracker?.contextUpdatesToday(),
            hypotheses = stats?.hypothetical, total = stats?.totalEntries, hourly = tracker?.hourlyToday())
    }
}

/**
 * Hört auf ContinuumMemoryEvents (addListener) und zählt echte Ereignisse des heutigen Tages (Europe/Vienna):
 * Stundenbalken über alle Ereignisse, Kontext-Updates = MemoryStored. Vor Mitternacht verworfen.
 */
class MemoryActivityTracker(private val clock: Clock = Clock.system(ZoneId.of("Europe/Vienna"))) : ContinuumMemoryEventListener {
    private val lock = Any()
    private var day = -1L
    private val hours = IntArray(24)
    private var stored = 0

    override fun onMemoryEvent(event: ContinuumMemoryEvent) = synchronized(lock) {
        val at = Instant.ofEpochMilli(event.atEpochMs).atZone(clock.zone)
        roll()
        if (at.toLocalDate().toEpochDay() != day) return@synchronized
        hours[at.hour]++
        if (event is MemoryStored) stored++
    }

    fun hourlyToday(): IntArray = synchronized(lock) { roll(); hours.copyOf() }
    fun contextUpdatesToday(): Int = synchronized(lock) { roll(); stored }

    private fun roll() {
        val today = java.time.LocalDate.now(clock).toEpochDay()
        if (today != day) { day = today; hours.fill(0); stored = 0 }
    }
}

/**
 * "Memory Activity"-Karte wie ref5 (Glas, 1-px-Rand, Ebenen-Icon, Titel, Chevron, Balken, 4 Zeilen).
 * Eigenständig: Kern legt Slot/Grid fest und ruft [draw] mit den Slot-Grenzen in dp auf. Farben/Typo nur Tokens (#479).
 * Referenzgröße 160 x 105 dp (ref5 254 x 167 px × 0,642); skaliert proportional zur Slotbreite.
 */
object JarvisMemoryActivityPanel {
    const val TITLE = "Memory Activity"
    const val REF_W = 160f
    const val REF_H = 105f

    fun draw(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, snap: MemoryActivitySnapshot) {
        val c = JarvisDesign.Colors
        val type = JarvisDesign.Type
        val box = JarvisShellLayout.Box(l, t, r, b)
        JarvisReferenceHud.surface(p, box, 13f, c.cyan, c.violetRim)
        val titleY = t + 10f + p.capHeight(type.heading)
        ContinuumIcons.draw(p, ContinuumIcons.Icon.DATABASE, l + 18f, titleY - p.capHeight(type.heading) / 2f,
            17f, Fill.Solid(c.cyanSoft))
        p.drawText(JarvisShellRenderer.fit(p, TITLE, type.heading, box.width - 46f), l + 33f,
            titleY, type.heading, Fill.Solid(c.text))
        val barsTop = titleY + 10f
        val barHeight = minOf(24f, box.height * .17f)
        val baseline = barsTop + barHeight
        val bars = snap.hourly
        val n = bars?.size?.takeIf { it > 0 } ?: 24
        val peak = bars?.maxOrNull()?.coerceAtLeast(1) ?: 1
        val step = (box.width - 24f) / n
        for (i in 0 until n) {
            val value = bars?.getOrNull(i) ?: 0
            val x = l + 12f + step * (i + .2f)
            val h = if (value <= 0) 1.2f else barHeight * value / peak
            val color = if (value <= 0) c.rim else if (i < n / 2) c.cyan else c.violetRim
            p.fillRect(x, baseline - h, x + step * .6f, baseline, Fill.Solid(color), if (value <= 0) .4f else .95f)
        }
        // Bars count observed events since this Activity started, not a fabricated 24-hour history.
        val noteY = baseline + p.capHeight(type.status) + 4f
        p.drawText(if (bars == null) "Ereignisse: —" else "Ereignisse seit Start", l + 12f,
            noteY, type.status, Fill.Solid(c.textMuted))
        val rowsTop = noteY + 5f
        val pitch = ((b - 7f - rowsTop) / 4f).coerceAtLeast(p.capHeight(type.caption) + 3f)
        snap.rows().forEachIndexed { index, row ->
            val y = rowsTop + (index + .5f) * pitch + p.capHeight(type.caption) / 2f
            if (y > b - 4f) return@forEachIndexed
            p.drawText(row.first, l + 12f, y, type.caption, Fill.Solid(c.text))
            p.drawText(JarvisShellRenderer.fit(p, row.second, type.caption, box.width - 63f), l + 51f,
                y, type.caption, Fill.Solid(c.textSecondary))
        }
    }
}
