package at.heinz.deviceai.voice

import at.heinz.deviceai.cognitive.ChatReplyFormatter

/**
 * Turns a visible reply into the text that is SPOKEN. The visible text is never changed; callers pass the result
 * only to the speech path. Deterministic, engine-independent, idempotent (shaping a shaped text changes nothing).
 *
 * Runs before [at.heinz.deviceai.cognitive.JarvisVoiceProfile.speakableChunks], which still removes code, residual
 * markup and control characters and splits into pieces at sentence ends.
 *
 * Pauses: no SSML. ElevenLabs documents `<break time="x.xs" />` but "Eleven v4 and Eleven v3 do not support SSML
 * break tags" and "too many break tags … can cause instability"; Android engines differ in SSML support, and the
 * lip-sync alignment of ElevenLabs maps characters of exactly the sent text. Pauses therefore come from
 * punctuation (sentence and clause ends that every engine honours) plus the existing pause between pieces.
 */
object SpeechTextShaper {
    fun shape(text: String): String {
        if (text.isBlank()) return ""
        val plain = ChatReplyFormatter.plain(text.replace("\r\n", "\n").replace('\r', '\n'))
        var inFence = false
        val lines = plain.lines().map { line ->
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                inFence = !inFence; return@map Line(line, code = true)
            }
            if (inFence) Line(line, code = true) else Line(shapeLine(line), code = false)
        }
        // A line break is a pause in the visible text; spoken text needs a sentence end for it.
        val out = StringBuilder()
        lines.forEachIndexed { index, line ->
            var value = line.text
            if (!line.code) {
                value = value.trim()
                val nextHasText = lines.drop(index + 1).firstOrNull { it.text.isNotBlank() } != null
                if (value.isNotEmpty() && nextHasText && value.last() !in TERMINAL) value += "."
            }
            if (out.isNotEmpty()) out.append('\n')
            out.append(value)
        }
        return out.toString().replace(blankLines, "\n\n").trim()
    }

    private class Line(val text: String, val code: Boolean)
    private const val TERMINAL = ".!?:;…,"
    private val blankLines = Regex("\\n{3,}")

    private val bulletLine = Regex("^\\s*[•▪◦·*+\\-–]\\s+(.*)$")
    private val numberedLine = Regex("^\\s*(\\d{1,2})[.)]\\s+(.*)$")
    private val ordinals = listOf("Erstens", "Zweitens", "Drittens", "Viertens", "Fünftens", "Sechstens",
        "Siebtens", "Achtens", "Neuntens", "Zehntens")

    private fun shapeLine(raw: String): String {
        var line = raw
        bulletLine.matchEntire(line)?.let { line = it.groupValues[1] }
        numberedLine.matchEntire(line)?.let { match ->
            val n = match.groupValues[1].toInt()
            line = (ordinals.getOrNull(n - 1)?.let { "$it, " } ?: "Punkt $n: ") + match.groupValues[2]
        }
        line = links(line)
        line = dates(line)
        line = times(line)
        line = abbreviations(line)
        line = numbers(line)
        line = units(line)
        line = symbols(line)
        return line.replace(spaces, " ").trim()
    }

    // ---- links ----
    private val markdownLink = Regex("\\[([^\\]]{1,100})\\]\\((?:https?://)?[^)\\s]*\\)")
    private val url = Regex("(?i)\\b(?:https?://|www\\.)((?:www\\.)?[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)(?::\\d+)?(?:[/?#][^\\s]*?)?(?=[.,;:!?)\\]]*(?:\\s|$))")

    private fun links(line: String): String = line.replace(markdownLink, "$1").replace(url) { match ->
        match.groupValues[1].lowercase().removePrefix("www.")
    }

    // ---- dates and times ----
    private val months = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
        "September", "Oktober", "November", "Dezember")
    private val date = Regex("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})(?!\\d)")
    private val time = Regex("\\b(\\d{1,2}):([0-5]\\d)(\\s*Uhr\\b)?")

    private fun dates(line: String) = line.replace(date) { m ->
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        if (day in 1..31 && month in 1..12) "$day. ${months[month - 1]} ${m.groupValues[3]}" else m.value
    }

    private fun times(line: String) = line.replace(time) { m ->
        val hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        val uhr = m.groupValues[3].isNotEmpty()
        // „9:30“ without „Uhr“ may be a ratio or a score; two-digit hours and anything with „Uhr“ are clock times.
        if (hour > 23 || (!uhr && m.groupValues[1].length < 2)) m.value
        else "$hour Uhr" + if (minute == 0) "" else " $minute"
    }

    // ---- abbreviations ----
    private class Abbreviation(pattern: String, val spoken: String, val canEndSentence: Boolean = false) {
        val regex = Regex("(?<![\\p{L}\\p{N}])$pattern")
    }
    private val abbreviations = listOf(
        Abbreviation("z\\.\\s?B\\.", "zum Beispiel"),
        Abbreviation("d\\.\\s?h\\.", "das heißt"),
        Abbreviation("u\\.\\s?a\\.", "unter anderem", true),
        Abbreviation("u\\.\\s?U\\.", "unter Umständen"),
        Abbreviation("o\\.\\s?ä\\.", "oder ähnlich", true),
        Abbreviation("usw\\.", "und so weiter", true),
        Abbreviation("etc\\.", "et cetera", true),
        Abbreviation("bzw\\.", "beziehungsweise"),
        Abbreviation("bzgl\\.", "bezüglich"),
        Abbreviation("ca\\.", "circa"),
        Abbreviation("evtl\\.", "eventuell"),
        Abbreviation("ggf\\.", "gegebenenfalls"),
        Abbreviation("inkl\\.", "inklusive"),
        Abbreviation("exkl\\.", "exklusive"),
        Abbreviation("insb\\.", "insbesondere"),
        Abbreviation("sog\\.", "sogenannte"),
        Abbreviation("vgl\\.", "vergleiche"),
        Abbreviation("max\\.", "maximal"),
        Abbreviation("Nr\\.", "Nummer"),
        Abbreviation("Dr\\.", "Doktor"),
        Abbreviation("Prof\\.", "Professor"),
        Abbreviation("Mio\\.", "Millionen"),
        Abbreviation("Mrd\\.", "Milliarden"),
        Abbreviation("Tel\\.", "Telefon")
    )
    /**
     * German nouns are capitalised, so a capital letter after „usw.“ does not prove a new sentence. Only these
     * typical sentence openings (or the end of the line) count as one.
     */
    private val sentenceContinues = Regex("^\\s+(?:Dann|Danach|Das|Der|Die|Dies|Diese|Ich|Es|Er|Sie|Wir|Du|Bitte|" +
        "Außerdem|Auch|Aber|Und|Damit|So|Jetzt|Heute|Morgen|Insgesamt|Kurz)\\b")

    private fun abbreviations(line: String): String {
        var out = line
        for (a in abbreviations) {
            out = a.regex.replace(out) { m ->
                val rest = out.substring(m.range.last + 1)
                // „… usw. Dann …“: the dot also ended the sentence; keep that pause.
                if (a.canEndSentence && (rest.isBlank() || sentenceContinues.containsMatchIn(rest))) a.spoken + "."
                else a.spoken
            }
        }
        return out
    }

    // ---- numbers and units ----
    private val decimal = Regex("(?<=\\d),(?=\\d)")
    private val thousands = Regex("(?<![\\d.,])(\\d{1,3}(?:\\.\\d{3})+)(?![\\d.])")
    private val range = Regex("(?<=\\d)\\s?–\\s?(?=\\d)")

    private fun numbers(line: String) = line
        .replace(thousands) { it.groupValues[1].replace(".", "") }
        .replace(decimal, " Komma ")
        .replace(range, " bis ")

    private class Unit(pattern: String, val spoken: String) {
        val regex = Regex("(?<=\\d)\\s?$pattern(?![\\p{L}\\p{N}])")
    }
    private val units = listOf(
        Unit("km/h", "Kilometer pro Stunde"), Unit("kWh", "Kilowattstunden"), Unit("mAh", "Milliamperestunden"),
        Unit("GHz", "Gigahertz"), Unit("MHz", "Megahertz"), Unit("TB", "Terabyte"), Unit("GB", "Gigabyte"),
        Unit("MB", "Megabyte"), Unit("[kK]B", "Kilobyte"), Unit("km", "Kilometer"), Unit("kg", "Kilogramm"),
        Unit("cm", "Zentimeter"), Unit("mm", "Millimeter"), Unit("ms", "Millisekunden"), Unit("°C", "Grad Celsius"),
        Unit("°", "Grad"), Unit("%", "Prozent"), Unit("€", "Euro"), Unit("[$]", "Dollar"), Unit("h", "Stunden"),
        Unit("min", "Minuten"), Unit("Std\\.", "Stunden"), Unit("W", "Watt")
    )
    private val euroBefore = Regex("€\\s?(?=\\d)")

    private fun units(line: String): String {
        var out = line
        // „€ 12“ → „12 Euro“ is reordering; keep it simple and say „Euro 12“ only when the sign comes first.
        out = out.replace(euroBefore, "Euro ")
        for (u in units) out = u.regex.replace(out, " " + u.spoken)
        return out
    }

    // ---- symbols, emoji, emphasis ----
    private val emoji = Regex("[\\p{So}\\p{Cs}\\x{FE0F}\\x{200D}]")
    private val repeatedBang = Regex("!{2,}")
    private val repeatedQuestion = Regex("\\?[!?]+")
    private val dots = Regex("\\.{3,}")
    private val spaces = Regex("[ \\t\\u00A0]+")

    private fun symbols(line: String) = line
        .replace("→", " auf ").replace("⇒", " also ").replace("&", " und ")
        .replace(emoji, " ")
        .replace(repeatedQuestion, "?").replace(repeatedBang, "!").replace(dots, "…")
}
