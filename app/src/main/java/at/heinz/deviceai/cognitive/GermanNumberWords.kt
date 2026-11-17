package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Spoken German cardinals 0..999 ("zwölf", "siebzehn", "einundzwanzig", "hundertdrei") and digit
 * strings. ASR on the Fold7 writes counts as words; a digit-only parser silently misses every
 * spoken correction (#758). The bare articles "ein/eine/einen/…" are deliberately NOT numbers.
 */
internal object GermanNumberWords {
    private val units = mapOf("eins" to 1, "zwei" to 2, "zwo" to 2, "drei" to 3, "vier" to 4, "fünf" to 5,
        "fuenf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9)
    // Prefix forms inside compounds ("einundzwanzig", "hundertein…").
    private val compoundUnits = units + mapOf("ein" to 1)
    private val teens = mapOf("zehn" to 10, "elf" to 11, "zwölf" to 12, "zwoelf" to 12, "dreizehn" to 13,
        "vierzehn" to 14, "fünfzehn" to 15, "fuenfzehn" to 15, "sechzehn" to 16, "siebzehn" to 17,
        "achtzehn" to 18, "neunzehn" to 19)
    private val tens = mapOf("zwanzig" to 20, "dreißig" to 30, "dreissig" to 30, "vierzig" to 40,
        "fünfzig" to 50, "fuenfzig" to 50, "sechzig" to 60, "siebzig" to 70, "achtzig" to 80, "neunzig" to 90)

    fun parse(word: String): Int? {
        val w = word.lowercase(Locale.GERMAN)
        if (w.isEmpty() || w.length > 40) return null
        if (w.all(Char::isDigit)) return if (w.length <= 6) w.toInt() else null
        if (w == "null") return 0
        val hundred = w.indexOf("hundert")
        if (hundred < 0) return below100(w)
        val head = w.substring(0, hundred)
        val multiplier = if (head.isEmpty()) 1 else compoundUnits[head] ?: return null
        val rest = w.substring(hundred + "hundert".length).removePrefix("und")
        if (rest.isEmpty()) return multiplier * 100
        val tail = below100(rest) ?: (if (rest == "ein") 1 else return null)
        return multiplier * 100 + tail
    }

    private fun below100(w: String): Int? {
        units[w]?.let { return it }
        teens[w]?.let { return it }
        tens[w]?.let { return it }
        val und = w.indexOf("und")
        if (und <= 0) return null
        val unit = compoundUnits[w.substring(0, und)] ?: return null
        val ten = tens[w.substring(und + 3)] ?: return null
        return ten + unit
    }
}
