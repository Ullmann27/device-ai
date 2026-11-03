package at.heinz.deviceai.cognitive

import java.time.ZonedDateTime

/** Device-clock grounding for time/date questions (stub = Code39 behaviour: none). */
internal object LocalClockIntent {
    fun isClockQuestion(message: String): Boolean = false
    fun answer(now: ZonedDateTime): String = ""
    fun promptAnchor(now: ZonedDateTime): String = ""
}
