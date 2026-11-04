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
class MemoryActivityTracker(
    private val clock: Clock = Clock.system(ZoneId.of("Europe/Vienna")),
    /**
     * true only if a producer of learning.MemoryStored is wired. Stand d981fecd: nobody emits learning.MemoryStored
     * (ContinuumMemoryEventHub emits only RecallHits from EventEmittingRetriever), so "Context updates" shows "—"
     * instead of a false 0.
     */
    private val storedEventsWired: Boolean = false,
) : ContinuumMemoryEventListener {
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
    fun contextUpdatesToday(): Int? = if (!storedEventsWired) null else synchronized(lock) { roll(); stored }

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
        val C = JarvisDesign.Colors
        val k = (r - l) / REF_W
        val rad = 6f * k
        p.fillRoundRect(l, t, r, b, rad, Fill.Solid(C.surface))
        p.strokeRoundRect(l, t, r, b, rad, 1f, Fill.Solid(C.panelRim), .9f)
        // Ebenen-Icon
        val ix = l + 12f * k; val iy = t + 13f * k; val iw = 12f * k
        for (j in 0 until 3) {
            val y = iy + j * 3.2f * k
            val path = VectorPath().moveTo(ix, y).lineTo(ix + iw / 2, y - 2.6f * k).lineTo(ix + iw, y).lineTo(ix + iw / 2, y + 2.6f * k).close()
            p.strokePath(path, 1.1f * k, Fill.Solid(C.cyanSoft), if (j == 0) 1f else .8f)
        }
        val titleStyle = JarvisDesign.Type.heading.copy(size = JarvisDesign.Type.heading.size * k * .95f)
        p.drawText(TITLE, l + 33f * k, t + 18f * k, titleStyle, Fill.Solid(C.text))
        val cx = r - 12f * k; val cy = t + 14f * k
        p.drawLine(cx - 2.5f * k, cy - 3.5f * k, cx + 1f * k, cy, 1.2f * k, Fill.Solid(C.cyanSoft))
        p.drawLine(cx + 1f * k, cy, cx - 2.5f * k, cy + 3.5f * k, 1.2f * k, Fill.Solid(C.cyanSoft))
        // Balken
        val bl = l + 12f * k; val br = r - 12f * k; val base = t + 41f * k; val maxH = 14f * k
        val bars = snap.hourly
        // Ohne Ereignisquelle: 24 gedimmte leere Balken (kein Wert, keine "offline"-Zeile).
        val n = bars?.size?.takeIf { it > 0 } ?: 24
        val peak = bars?.maxOrNull()?.coerceAtLeast(1) ?: 1
        val step = (br - bl) / n; val bw = step * .55f
        for (i in 0 until n) {
            val v = bars?.getOrNull(i) ?: 0
            val x = bl + i * step + (step - bw) / 2
            if (bars == null || v <= 0) { p.fillRect(x, base - 1.5f * k, x + bw, base, Fill.Solid(C.rim), .45f); continue }
            val h = maxOf(2f * k, maxH * v / peak)
            val col = Fill.Linear(0f, base - maxH, 0f, base, intArrayOf(if (i % 3 == 0) C.cyan else C.violetRim, C.violet))
            p.fillRect(x, base - h, x + bw, base, col)
        }
        // Zeilen
        val rowStyle = JarvisDesign.Type.body.copy(size = 8.5f * k); val valStyle = rowStyle.copy(weight = 500)
        val top = t + 46f * k; val pitch = 14.2f * k
        for ((i, row) in snap.rows().withIndex()) {
            val y = top + i * pitch
            if (i > 0) p.drawLine(l + 8f * k, y, r - 8f * k, y, .8f, Fill.Solid(C.rim), .6f)
            val ty = y + pitch / 2 + p.capHeight(rowStyle) / 2
            p.drawText(row.first, l + 12f * k, ty, valStyle, Fill.Solid(C.text))
            p.drawText(row.second, l + 49f * k, ty, rowStyle, Fill.Solid(C.textSecondary))
        }
    }
}
