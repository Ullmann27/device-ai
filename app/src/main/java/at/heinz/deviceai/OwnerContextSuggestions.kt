package at.heinz.deviceai

import at.heinz.deviceai.cognitive.OwnerSharedWorkRecord
import at.heinz.deviceai.cognitive.OwnerSuggestion
import at.heinz.deviceai.cognitive.OwnerSuggestionKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The context suggestions the app can really make today, built from data it really read (owner wish 19.11.2026:
 * „unaufdringliche proaktive Unterstützung“ – suggest, never act). Each function returns one sentence or null; whether
 * it may be said is [at.heinz.deviceai.cognitive.OwnerInitiative]'s decision, not this object's.
 *
 * Supported: an appointment of today that starts within the next hours – from the device calendar, read with the
 * owner's permission. Not supported, because the app has no such data yet (the policy has the slots, nothing fills
 * them): a deadline in a mail → a calendar entry ([OwnerSuggestionKind.CALENDAR_ENTRY]; no lane reads mail bodies for
 * dates and no lane writes the calendar), an address in a message → the maps app ([OwnerSuggestionKind.OPEN_MAPS];
 * the incoming-message inbox keeps no address recognition and there is no approved maps hand-over).
 */
internal object OwnerContextSuggestions {
    /** How far ahead an appointment is mentioned. */
    const val APPOINTMENT_WINDOW_MILLIS = 3 * 60 * 60_000L
    /** A calendar read older than this is no basis for a sentence any more. */
    const val FRESH_MILLIS = 15 * 60_000L
    private val clock = DateTimeFormatter.ofPattern("H:mm", Locale.GERMAN)

    /**
     * „Übrigens: Um 15:30 Uhr steht „Zahnarzt“ in deinem Kalender.“ – for an appointment that starts today within
     * [APPOINTMENT_WINDOW_MILLIS] after [nowMillis]. [readAtMillis]: when the calendar was read; a stale read gives
     * nothing. A statement, not a question: the app has no reminder it could offer to set.
     */
    fun appointment(title: String, startMillis: Long, nowMillis: Long, zone: ZoneId, readAtMillis: Long = nowMillis): OwnerSuggestion? {
        if (startMillis <= nowMillis || startMillis - nowMillis > APPOINTMENT_WINDOW_MILLIS) return null
        if (nowMillis - readAtMillis !in 0..FRESH_MILLIS) return null
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        if (start.toLocalDate() != Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()) return null
        val name = OwnerSharedWorkRecord.clean(title, 60)
        val what = if (name.isEmpty()) "ein Termin" else "„$name“"
        return OwnerSuggestion(OwnerSuggestionKind.APPOINTMENT, "Übrigens: Um ${clock.format(start)} Uhr steht $what in deinem Kalender.",
            order = null, subject = "$startMillis:${name.hashCode()}")
    }
}
