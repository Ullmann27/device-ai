package at.heinz.deviceai.cognitive

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One PDF found on the device: its real name, the folder relative to the storage root („Download“,
 * „Documents/Labor“, "" = the root itself), the absolute path, size and modification time as the file system
 * reports them. Nothing here is ever invented or completed.
 */
internal data class OwnerLibraryFile(val name: String, val folder: String, val path: String, val sizeBytes: Long,
                                     val modifiedMillis: Long) {
    /** „Download“, „Documents/Labor“ – or „Hauptordner“ for a file directly in the storage root. */
    val folderLabel: String get() = folder.ifEmpty { "Hauptordner" }
}

/**
 * Finds the PDF the owner named among the files that really exist („Öffne das PDF Argon4“). Names are compared in a
 * form that survives speech recognition: case, spaces, hyphens, underscores and „.pdf“ do not count, number words are
 * digits („Argon vier“ = „Argon4“), umlauts and ß equal their transcriptions („Müller“ = „Mueller“ = „Muller“).
 *
 * Rank: exact name > name starts with it > name contains it (or contains all its words) > near miss (small edit
 * distance, „Organ 4“). [decide] opens only what is clear – exactly one exact name, or exactly one real hit at all –
 * and otherwise asks. A near miss is never a hit: it is only offered as a question.
 */
internal object OwnerLibraryMatcher {
    enum class Tier { EXACT, PREFIX, CONTAINS, NEAR }

    data class Hit(val file: OwnerLibraryFile, val tier: Tier)

    sealed interface Decision {
        /** [hit] is clear; [others] are further real hits the owner may have meant instead (shown, never opened). */
        data class Open(val hit: Hit, val others: List<Hit>) : Decision
        /** Several real hits of equal standing: the owner chooses. */
        data class Ask(val hits: List<Hit>) : Decision
        /** No file of that name; these merely sound or look similar. */
        data class Near(val hits: List<Hit>) : Decision
        data object None : Decision
    }

    const val MAX_LISTED = 8
    private const val MAX_NEAR = 3

    private val numberWords = mapOf("null" to "0", "eins" to "1", "zwei" to "2", "zwo" to "2", "drei" to "3",
        "vier" to "4", "funf" to "5", "sechs" to "6", "sieben" to "7", "acht" to "8", "neun" to "9", "zehn" to "10",
        "elf" to "11", "zwolf" to "12")
    private val marks = Regex("\\p{M}+")
    private val pdfSuffix = Regex("(?i)\\.pdf$")

    /** The comparable words of a name or query: lower case, folded, number words as digits, letters and digits split. */
    fun tokens(text: String): List<String> {
        val folded = fold(text.trim().replace(pdfSuffix, ""))
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var digits = false
        fun flush() { if (current.isNotEmpty()) { parts += current.toString(); current.setLength(0) } }
        for (char in folded) {
            when {
                !char.isLetterOrDigit() -> flush()
                current.isNotEmpty() && char.isDigit() != digits -> { flush(); current.append(char) }
                else -> current.append(char)
            }
            digits = char.isDigit()
        }
        flush()
        return parts.map { numberWords[it] ?: it }
    }

    /** All [tokens] joined: „Argon 4“, „argon-4.pdf“ and „Argon vier“ give „argon4“. */
    fun key(text: String): String = tokens(text).joinToString("")

    /** ä/ae → a, ö/oe → o, ü/ue → u, ß → ss, other accents removed. Applied to both sides, so equality is what counts. */
    private fun fold(text: String): String {
        val lower = text.lowercase(Locale.GERMAN).replace("ß", "ss")
        val plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(marks, "")
        return plain.replace("ae", "a").replace("oe", "o").replace("ue", "u")
    }

    /** Every file that matches [query] with its tier, best first; near misses only when nothing really matches. */
    fun rank(query: String, files: List<OwnerLibraryFile>): List<Hit> {
        val wanted = tokens(query)
        val wantedKey = wanted.joinToString("")
        if (wantedKey.isEmpty()) return emptyList()
        val real = ArrayList<Hit>()
        val near = ArrayList<Pair<Hit, Int>>()
        val loose = ArrayList<Pair<Hit, Int>>()
        for (file in files) {
            val have = tokens(file.name)
            val haveKey = have.joinToString("")
            if (haveKey.isEmpty()) continue
            val tier = when {
                haveKey == wantedKey -> Tier.EXACT
                wantedKey.length >= 2 && haveKey.startsWith(wantedKey) -> Tier.PREFIX
                wantedKey.length >= 2 && (haveKey.contains(wantedKey) || have.containsAll(wanted)) -> Tier.CONTAINS
                else -> null
            }
            if (tier != null) real += Hit(file, tier)
            else nearDistance(wantedKey, haveKey)?.let { near += Hit(file, Tier.NEAR) to it }
                ?: sharedWords(wanted, have, wantedKey.length)?.let { loose += Hit(file, Tier.NEAR) to it }
        }
        val order = compareBy<Hit>({ it.tier.ordinal }, { -it.file.modifiedMillis }, { it.file.name.lowercase(Locale.GERMAN) },
            { it.file.path })
        if (real.isNotEmpty()) return real.sortedWith(order)
        if (near.isNotEmpty()) return near.sortedWith(compareBy<Pair<Hit, Int>> { it.second }.thenBy(order) { it.first }).map { it.first }
        return loose.sortedWith(compareBy<Pair<Hit, Int>> { -it.second }.thenBy(order) { it.first }).map { it.first }
    }

    /**
     * A looser near miss for a name that speech recognition broke apart (field finding 19.11.2026: „a und 4
     * Experimente“ for „Argon4 Experimente“): the file shares at least two whole words with what was said, one of them
     * a real word of five letters or more, and these words are most of what was said. The weight is the number of
     * shared characters. Like every near miss it is never a hit – it is only offered as a question.
     */
    private fun sharedWords(wanted: List<String>, have: List<String>, wantedLength: Int): Int? {
        val shared = wanted.toSet().filter { it in have }
        if (shared.size < 2 || shared.none { it.length >= 5 && it.all(Char::isLetter) }) return null
        val weight = shared.sumOf { it.length }
        return weight.takeIf { it * 5 >= wantedLength * 3 }
    }

    /**
     * What to do with [query]. [folderHint] („aus dem Download-Ordner“) narrows real hits to that folder when some lie
     * there; it never turns a miss into a hit.
     */
    fun decide(query: String, files: List<OwnerLibraryFile>, folderHint: String? = null): Decision {
        val ranked = rank(query, files)
        if (ranked.isEmpty()) return Decision.None
        if (ranked.first().tier == Tier.NEAR) return Decision.Near(ranked.take(MAX_NEAR))
        val hinted = folderHint?.let { hint -> ranked.filter { inFolder(it.file, hint) } }.orEmpty()
        val pool = hinted.ifEmpty { ranked }
        val exact = pool.filter { it.tier == Tier.EXACT }
        return when {
            exact.size == 1 -> Decision.Open(exact.single(), pool - exact.single())
            exact.isEmpty() && pool.size == 1 -> Decision.Open(pool.single(), emptyList())
            else -> Decision.Ask(pool)
        }
    }

    /** The hits of [files] that [fragment] („das Messprotokoll“, „Argon4 Datenblatt“) singles out by name or folder. */
    fun pick(fragment: String, files: List<OwnerLibraryFile>): List<OwnerLibraryFile> {
        val wanted = tokens(fragment)
        val wantedKey = wanted.joinToString("")
        if (wantedKey.length < 2) return emptyList()
        val byName = files.filter { file ->
            val have = tokens(file.name)
            have.containsAll(wanted) || (wantedKey.length >= 3 && have.joinToString("").contains(wantedKey))
        }
        if (byName.isNotEmpty()) return byName
        return files.filter { file -> wantedKey.length >= 3 && tokens(file.folder).containsAll(wanted) }
    }

    private fun inFolder(file: OwnerLibraryFile, hint: String): Boolean {
        val top = file.folder.substringBefore('/').lowercase(Locale.ROOT)
        return when (hint) {
            FOLDER_DOWNLOAD -> top == "download" || top == "downloads"
            FOLDER_DOCUMENTS -> top == "documents" || top == "dokumente"
            else -> false
        }
    }

    const val FOLDER_DOWNLOAD = "Download"
    const val FOLDER_DOCUMENTS = "Documents"

    /**
     * A near miss: the query differs from the name – or from the name's beginning – by one edit (short names) or two.
     * Names under four characters are too short to call anything „similar“.
     */
    private fun nearDistance(wanted: String, have: String): Int? {
        if (wanted.length < 4) return null
        val allowed = if (wanted.length <= 5) 1 else 2
        val from = (wanted.length - allowed).coerceAtLeast(1)
        if (have.length < from) return null
        var best = Int.MAX_VALUE
        for (length in from..(wanted.length + allowed)) {
            if (length > have.length) break
            best = minOf(best, editDistance(wanted, have.substring(0, length), allowed))
        }
        return best.takeIf { it <= allowed }
    }

    /** Optimal string alignment distance (insert, delete, replace, swap of neighbours), cut off above [limit]. */
    internal fun editDistance(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var beforePrevious = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowBest = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1])
                    value = minOf(value, beforePrevious[j - 2] + 1)
                current[j] = value
                rowBest = minOf(rowBest, value)
            }
            if (rowBest > limit) return limit + 1
            val recycled = beforePrevious
            beforePrevious = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }

    // ── Wording of real file facts ──────────────────────────────────────────────────────────────────────────

    private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

    /** „1,2 MB“, „310 KB“, „512 Byte“ – decimal comma, powers of 1024 like Android's file manager. */
    fun sizeText(bytes: Long): String = when {
        bytes < 0 -> "Größe unbekannt"
        bytes < 1024 -> "$bytes Byte"
        bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.GERMAN, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.GERMAN, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun dateText(millis: Long, zone: ZoneId): String? =
        if (millis <= 0L) null else shortDate.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** „Argon4.pdf“ – Ordner Download · 1,2 MB · geändert am 17.11.2026 */
    fun describe(file: OwnerLibraryFile, zone: ZoneId): String = "„${file.name}“ – " +
        listOfNotNull(if (file.folder.isEmpty()) "Hauptordner des Speichers" else "Ordner ${file.folder}", sizeText(file.sizeBytes),
            dateText(file.modifiedMillis, zone)?.let { "geändert am $it" }).joinToString(" · ")
}
