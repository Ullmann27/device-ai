package at.heinz.deviceai.cognitive

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * „Nein, ich meinte den letzten Entwurf.“ – the owner corrects what a task refers to (owner brief 06.11.2026). Only a
 * negation or correction marker together with „ich meinte / gemeint war“ and a work object (Entwurf, Zusammenfassung, PDF,
 * Dokument) counts; the reference is resolved against the [CockpitWorkJournal] – never guessed. Without a match the
 * caller asks one short question.
 */
internal object OwnerCorrectionIntent {
    enum class Target { DRAFT, DOCUMENT }
    enum class Which { LAST, PREVIOUS, YESTERDAY }
    data class Correction(val target: Target, val which: Which)

    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private val marker = Regex("(?iu)^\\s*(nein|nicht\\s+(das|der|die|den|so)|falsch|halt|stopp)$E")
    private val meant = Regex("(?iu)$S(ich\\s+meinte|ich\\s+meine|gemeint\\s+war|gemeint\\s+ist|ich\\s+wollte)$E")
    private val draft = Regex("(?iu)$S(entwurf|entwurfs|zusammenfassung)$E")
    private val document = Regex("(?iu)$S(pdf|pdf-datei|dokument)$E")
    private val previous = Regex("(?iu)$S(vorigen|vorige|vorletzten|vorletzte|davor|früheren|frühere)$E")
    private val yesterday = Regex("(?iu)$S(gestern|gestrigen|gestrige)$E")

    fun parse(utterance: String): Correction? {
        if (utterance.length !in 10..200 || utterance.any { it.isISOControl() }) return null
        if (!marker.containsMatchIn(utterance) || !meant.containsMatchIn(utterance)) return null
        val target = when {
            draft.containsMatchIn(utterance) -> Target.DRAFT
            document.containsMatchIn(utterance) -> Target.DOCUMENT
            else -> return null
        }
        val which = when {
            previous.containsMatchIn(utterance) -> Which.PREVIOUS
            yesterday.containsMatchIn(utterance) -> Which.YESTERDAY
            else -> Which.LAST
        }
        return Correction(target, which)
    }

    /**
     * The episode the owner means: for a draft one with a draft, for a document one with a re-openable document;
     * [current] (the task being corrected) is never its own answer. Null when the record holds no such episode.
     */
    fun resolve(episodes: List<CockpitWorkJournal.Episode>, correction: Correction, current: String?, now: Long,
                zone: TimeZone): CockpitWorkJournal.Episode? {
        val candidates = episodes.filter { it.id != current }.filter {
            when (correction.target) {
                Target.DRAFT -> !it.draft.isNullOrBlank()
                Target.DOCUMENT -> it.documentUri != null
            }
        }.sortedByDescending { it.startedAt }
        return when (correction.which) {
            Which.LAST -> candidates.firstOrNull()
            Which.PREVIOUS -> candidates.getOrNull(1)
            Which.YESTERDAY -> {
                val today = dayStart(now, zone)
                val yesterdayStart = dayStart(today - 1, zone)
                candidates.firstOrNull { it.startedAt in yesterdayStart until today }
            }
        }
    }

    private fun dayStart(at: Long, zone: TimeZone): Long = Calendar.getInstance(zone, Locale.GERMANY).apply {
        timeInMillis = at
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
