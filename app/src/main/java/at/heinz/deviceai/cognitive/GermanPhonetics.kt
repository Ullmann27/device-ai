package at.heinz.deviceai.cognitive

import java.text.Normalizer
import java.util.Locale

/**
 * Sound and spelling distance for German speech-recogniser output. Pure Kotlin, no dictionary, no I/O.
 *
 * Two independent views of one word are combined, because each alone is too permissive:
 *  - [soundKey]: Kölner Phonetik (the German Soundex) after dropping a vocalised r („Jarvis“ is heard as „Javis“,
 *    „Kalender“ as „Kalenda“). Vowels are not part of the key, so it tolerates every vowel confusion.
 *  - [fold]: a spelling normal form that removes the typical recogniser confusions (b/p, d/t, g/k, v/f/w, s/z,
 *    silent h, ie/i, doubled letters, ph/f, th/t, ck/k, x/ks, English sh). The weighted edit distance on that form
 *    keeps the vowels, so „Kammer“ and „Kamera“ stay apart although their consonant skeleton is close.
 *
 * [similarity] is 1.0 for identical words and falls with both views; the caller owns the thresholds.
 */
internal object GermanPhonetics {
    private const val VOWELS = "aeiouyäöü"

    private fun letters(word: String): String = Normalizer.normalize(word, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filter { it.isLetter() }

    private fun isVowel(c: Char) = c in VOWELS

    /** „r“ after a vowel and before a consonant or the word end is vocalised in Austrian and standard German. */
    private fun dropVocalisedR(word: String): String = buildString {
        for (i in word.indices) {
            val c = word[i]
            // A silent h between vowel and r („Uhr“, „Jahr“) does not stop the vocalisation.
            val before = word.substring(0, i).trimEnd('h').lastOrNull()
            if (c == 'r' && before != null && isVowel(before) && (i == word.lastIndex || !isVowel(word[i + 1]))) continue
            append(c)
        }
    }

    /** Kölner Phonetik of [word] after r-vocalisation; empty for a word without letters. */
    fun soundKey(word: String): String {
        val w = dropVocalisedR(letters(word).replace("ß", "ss").replace("sch", "s").replace("sh", "s"))
        if (w.isEmpty()) return ""
        val codes = StringBuilder()
        for (i in w.indices) {
            val c = w[i]
            val previous = w.getOrNull(i - 1)
            val next = w.getOrNull(i + 1)
            val code = when (c) {
                'a', 'e', 'i', 'j', 'o', 'u', 'y', 'ä', 'ö', 'ü' -> "0"
                'h' -> ""
                'b' -> "1"
                'p' -> if (next == 'h') "3" else "1"
                // „tc“ is an affricate only when that c is itself a sibilant; before a k-sounding c the t stays a t.
                'd', 't' -> if (next != null && (next in "sz" ||
                        (next == 'c' && w.getOrNull(i + 2)?.let { it in "ahkloqrux" } != true))) "8" else "2"
                'f', 'v', 'w' -> "3"
                'g', 'k', 'q' -> "4"
                'c' -> if (i == 0) { if (next != null && next in "ahkloqrux") "4" else "8" }
                    else if (previous != null && previous in "sz") "8"
                    // Deliberate deviation: „cl“/„cr“ inside a word is a k sound in the product names heard here („Nextcloud“).
                    else if (next != null && next in "ahkloqrux") "4" else "8"
                'x' -> if (previous != null && previous in "ckq") "8" else "48"
                'l' -> "5"
                'm', 'n' -> "6"
                'r' -> "7"
                's', 'z' -> "8"
                else -> ""
            }
            codes.append(code)
        }
        val collapsed = StringBuilder()
        for (c in codes) if (collapsed.isEmpty() || collapsed.last() != c) collapsed.append(c)
        return buildString {
            collapsed.forEachIndexed { index, c -> if (c != '0' || index == 0) append(c) }
        }
    }

    /** Spelling normal form without the typical recogniser confusions; keeps vowels. */
    fun fold(word: String): String {
        var w = letters(word).replace("ß", "ss")
        w = w.replace("sch", "$").replace("sh", "$")
            .replace(Regex("^ch(?=[rlao])"), "k").replace("ch", "#")
            .replace("ck", "k").replace("ph", "f").replace("th", "t").replace("qu", "kf")
            .replace("x", "ks").replace("tz", "s").replace("ts", "s").replace("dt", "t")
            .replace("ä", "e").replace("ö", "o").replace("ü", "u").replace("y", "i")
            .replace("ie", "i").replace(Regex("[ea][iy]"), "ai").replace(Regex("eu|oi"), "oi")
        w = dropVocalisedR(w)
        val out = StringBuilder()
        for (i in w.indices) {
            val c = when (val raw = w[i]) {
                'h' -> continue
                'c' -> if (w.getOrNull(i + 1)?.let { it in "eiä" } == true) 's' else 'k'
                'v', 'w' -> 'f'
                'b' -> 'p'
                'd' -> 't'
                'g', 'q' -> 'k'
                'z' -> 's'
                'j' -> 'i'
                else -> raw
            }
            if (out.isEmpty() || out.last() != c) out.append(c)
        }
        return out.toString()
    }

    /** Damerau-Levenshtein with cheaper vowel edits (a recogniser confuses vowels far more often than consonants). */
    fun weightedDistance(a: String, b: String): Double {
        if (a == b) return 0.0
        if (a.isEmpty()) return b.length.toDouble()
        if (b.isEmpty()) return a.length.toDouble()
        val d = Array(a.length + 1) { DoubleArray(b.length + 1) }
        fun single(c: Char) = if (c in "aeiou") 0.75 else 1.0
        for (i in 1..a.length) d[i][0] = d[i - 1][0] + single(a[i - 1])
        for (j in 1..b.length) d[0][j] = d[0][j - 1] + single(b[j - 1])
        for (i in 1..a.length) for (j in 1..b.length) {
            val x = a[i - 1]
            val y = b[j - 1]
            val substitute = when {
                x == y -> 0.0
                x in "aeiou" && y in "aeiou" -> 0.5
                else -> 1.0
            }
            var best = minOf(d[i - 1][j] + single(x), d[i][j - 1] + single(y), d[i - 1][j - 1] + substitute)
            if (i > 1 && j > 1 && x == b[j - 2] && a[i - 2] == y) best = minOf(best, d[i - 2][j - 2] + 1.0)
            d[i][j] = best
        }
        return d[a.length][b.length]
    }

    private fun plainDistance(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) current[j] = minOf(previous[j] + 1, current[j - 1] + 1,
                previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            val swap = previous; previous = current; current = swap
        }
        return previous[b.length]
    }

    /** True when both words have the same non-trivial sound key. */
    fun soundsAlike(a: String, b: String): Boolean {
        val key = soundKey(a)
        return key.length >= 2 && key == soundKey(b)
    }

    /**
     * 0.0 … 1.0: 0.55 × folded spelling similarity + 0.20 × raw spelling similarity + 0.25 × equal sound key.
     * Without an equal sound key the value cannot exceed 0.75, i.e. it stays below every repair threshold.
     */
    fun similarity(heard: String, known: String): Double {
        val h = letters(heard)
        val k = letters(known)
        if (h.isEmpty() || k.isEmpty()) return 0.0
        if (h == k) return 1.0
        val fh = fold(h)
        val fk = fold(k)
        val folded = 1.0 - weightedDistance(fh, fk) / maxOf(fh.length, fk.length, 1)
        val raw = 1.0 - plainDistance(h, k).toDouble() / maxOf(h.length, k.length)
        val sound = if (soundsAlike(h, k)) 1.0 else 0.0
        return (0.55 * folded.coerceAtLeast(0.0) + 0.20 * raw.coerceAtLeast(0.0) + 0.25 * sound).coerceIn(0.0, 1.0)
    }
}
