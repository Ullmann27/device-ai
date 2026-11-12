package at.heinz.deviceai.cognitive

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Device clock, no model inference. */
internal object OwnerDeviceClockReply {
    fun answer(request: String, now: Instant, zone: ZoneId): String? {
        val text = request.trim().trimEnd('.', '?', '!').trim().lowercase(Locale.GERMAN)
            .replace(Regex("\\s+"), " ").removePrefix("bitte ")
        val kind = when (text) {
            "wie spät ist es", "wie viel uhr ist es", "wieviel uhr ist es", "welche uhrzeit haben wir",
            "sag mir die uhrzeit", "uhrzeit" -> 1
            "welches datum haben wir", "welches datum ist heute", "was ist heute für ein tag",
            "welcher tag ist heute", "sag mir das datum", "datum" -> 2
            "datum und uhrzeit", "wie spät ist es und welches datum haben wir" -> 3
            else -> return null
        }
        val local = now.atZone(zone)
        val time = local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN))
        val date = local.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM uuuu", Locale.GERMAN))
        val value = when (kind) {
            1 -> "Es ist $time Uhr."
            2 -> "Heute ist $date."
            else -> "Heute ist $date, $time Uhr."
        }
        return "$value (Geräteuhr · ${zone.id})"
    }
}
