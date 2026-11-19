package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.LocalModelGenerationPolicy

/**
 * Code64 – joins an answer that stopped at the local output limit with the segment the existing continuation path
 * („weiter“) produced for it, so that both read as one answer.
 *
 * What it changes, and nothing else:
 *  - an overlapping repeat at the seam is written once (the model often repeats the last clause or completes the
 *    word the limit cut);
 *  - an announcement of the continuation itself („Gerne, hier ist die Fortsetzung:“), a leading ellipsis and – inside
 *    an open code block – a re-opened fence line are not part of the answer and are left out;
 *  - a numbered list that the continuation starts again at 1 keeps counting.
 *
 * Every other character of both segments is kept in order. When the continuation starts the answer again from the
 * beginning, steps back in a numbered list or adds nothing new, the segments are NOT joined ([Stop]): the caller
 * keeps the answer so far and the manual „weiter“ hint. Pure text logic: no model, no facts, no tool authority.
 */
internal object AnswerContinuationJoiner {
    enum class Stop { NONE, EMPTY, RESTARTED, FORMAT_BREAK, TOO_LONG }

    /** [text]: the joined answer when [stop] is [Stop.NONE], otherwise the unchanged answer so far. */
    data class Joined(val text: String, val stop: Stop, val overlapCharacters: Int = 0, val renumberedItems: Int = 0,
                      val announcement: String = "")

    private const val SEAM_WINDOW = 600
    private const val RESTART_MIN_ANSWER_WORDS = 12
    private const val RESTART_MIN_COMMON_WORDS = 6
    private val token = Regex("\\S+")
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val fenceLine = Regex("(?m)^[ \\t]{0,3}```")
    private val reopenedFence = Regex("^\\s*```[\\p{L}\\p{N}+#.-]*[ \\t]*\\n")
    private val leadingEllipsis = Regex("^\\s*(?:…|\\.\\.\\.)[ \\t]*")
    private val announcement = Regex("(?iu)^\\s*(?:(?:gerne|gern|natürlich|klar|okay|ok|sicher|selbstverständlich)[,!.]?\\s+)?" +
        "(?:hier\\s+(?:ist|kommt|folgt)\\s+(?:die\\s+fortsetzung|der\\s+rest)|" +
        "ich\\s+(?:setze|fahre)\\s+(?:(?:die|meine)\\s+antwort\\s+)?(?:(?:genau|direkt|nahtlos)\\s+)?(?:(?:an\\s+dieser\\s+stelle|dort)\\s+)?fort|" +
        "(?:die\\s+)?fortsetzung|weiter\\s+geht(?:'s|\\s+es)|es\\s+geht\\s+weiter)[^\\n:]{0,40}:[ \\t]*\\n?")
    private val blockStart = Regex("^(?:\\d{1,3}[.)][ \\t]|[-*•][ \\t]|#{1,6}[ \\t]|\\||```)")
    private val numbered = Regex("^([ \\t]{0,3})(\\d{1,3})([.)])([ \\t]+\\S.*)$")
    private val listClosed = Regex("\\n[ \\t]*\\n(?![ \\t]{0,3}\\d{1,3}[.)][ \\t])[^\\s]")
    private const val NO_SPACE_BEFORE = ".,;:!?)]}»“”…%"
    private const val NO_SPACE_AFTER = "([{„«‚/"

    fun join(answer: String, continuation: String,
             maxCharacters: Int = LocalModelGenerationPolicy.MAX_RESPONSE_CHARACTERS): Joined {
        if (continuation.isBlank()) return Joined(answer, Stop.EMPTY)
        if (answer.isBlank()) return if (continuation.length > maxCharacters) Joined(answer, Stop.TOO_LONG)
            else Joined(continuation, Stop.NONE)
        val inCode = insideCodeFence(answer)
        var next = continuation
        var announced = ""
        if (inCode) reopenedFence.find(next)?.let { next = next.substring(it.range.last + 1) }
        else {
            // The line break after an announcement is its own layout, not a paragraph break of the answer.
            announcement.find(next)?.let { announced = it.value.trim(); next = next.substring(it.range.last + 1).trimStart() }
            next = leadingEllipsis.replaceFirst(next, "")
        }
        if (next.isBlank()) return Joined(answer, Stop.EMPTY)
        if (restarts(answer, next)) return Joined(answer, Stop.RESTARTED)

        val head = answer.trimEnd()
        val body = next.trimStart()
        val tailSpace = answer.substring(head.length)
        val leadSpace = next.substring(0, next.length - body.length)
        val seam = overlap(head, body)
        val base: String
        val addition: String
        if (seam != null) {
            base = head.substring(0, seam.first)
            addition = body.substring(seam.second)
            if (addition.isBlank()) return Joined(answer, Stop.EMPTY)
        } else {
            base = head
            addition = separator(head, body, tailSpace + leadSpace, inCode) + body
        }
        val (text, renumbered) = renumber(base, addition, inCode) ?: return Joined(answer, Stop.FORMAT_BREAK)
        val joined = base + text
        if (joined.length > maxCharacters) return Joined(answer, Stop.TOO_LONG)
        return Joined(joined, Stop.NONE, if (seam != null) head.length - seam.first + seam.second else 0, renumbered, announced)
    }

    fun insideCodeFence(text: String): Boolean = fenceLine.findAll(text).count() % 2 == 1

    /** The continuation begins with the beginning of the answer: a new start, not a continuation. */
    private fun restarts(answer: String, continuation: String): Boolean {
        val first = word.findAll(answer).take(RESTART_MIN_ANSWER_WORDS).map { it.value.lowercase() }.toList()
        if (first.size < RESTART_MIN_ANSWER_WORDS) return false
        val again = word.findAll(continuation).take(RESTART_MIN_ANSWER_WORDS).map { it.value.lowercase() }.toList()
        if (again.size < RESTART_MIN_COMMON_WORDS) return false
        var common = 0
        while (common < again.size && first[common] == again[common]) common++
        return common >= RESTART_MIN_COMMON_WORDS
    }

    /**
     * The longest run of whole words that ends [head] and starts [body]. The last word of [head] may be the cut
     * beginning of the word [body] writes in full. Returns (characters of [head] to keep, characters of [body] to
     * skip) or null. A single character or a punctuation mark alone is not taken as a repeat.
     */
    private fun overlap(head: String, body: String): Pair<Int, Int>? {
        val offset = (head.length - SEAM_WINDOW).coerceAtLeast(0)
        val start = if (offset > 0 && head[offset].isLowSurrogate()) offset + 1 else offset
        // A word the window cut in half is not a word of the answer.
        val before = token.findAll(head.substring(start)).toList()
            .let { if (start > 0 && !head[start - 1].isWhitespace()) it.drop(1) else it }
        val after = token.findAll(body.take(SEAM_WINDOW)).toList()
        for (count in minOf(before.size, after.size) downTo 1) {
            var matches = true
            var cutWord = false
            for (index in 0 until count) {
                val left = before[before.size - count + index].value
                val right = after[index].value
                if (left == right || index == 0 && left.equals(right, ignoreCase = true)) continue
                val last = index == count - 1
                if (last && left.length >= (if (count == 1) 6 else 3) && left.last().isLetterOrDigit() &&
                    right.length > left.length && right.startsWith(left)) { cutWord = true; continue }
                matches = false
                break
            }
            if (!matches) continue
            if (count == 1 && !cutWord) {
                val only = before.last().value
                if (only.length < 2 || !only.all { it.isLetterOrDigit() || it == '-' }) continue
            }
            return if (cutWord) (start + before.last().range.first) to after[count - 1].range.first
                else head.length to after[count - 1].range.last + 1
        }
        return null
    }

    private fun separator(head: String, body: String, between: String, inCode: Boolean): String {
        // Whitespace inside a code block is the model's own; nothing is added or removed there.
        if (inCode) return between
        val breaks = between.count { it == '\n' }
        // The indentation after the last line break belongs to the continued line (a nested list item).
        val indent = if (breaks > 0) between.substringAfterLast('\n') else ""
        return when {
            head.last().isHighSurrogate() && body.first().isLowSurrogate() -> ""
            breaks >= 2 -> "\n\n" + indent
            breaks == 1 -> "\n" + indent
            blockStart.containsMatchIn(body) -> "\n"
            body.first() in NO_SPACE_BEFORE || head.last() in NO_SPACE_AFTER -> ""
            head.length >= 2 && head.last() == '-' && head[head.length - 2].isLetterOrDigit() && body.first().isLetter() -> ""
            else -> " "
        }
    }

    /**
     * [addition] with a numbered list that restarts at 1 counted on from the last item of [base]; null when the
     * list steps back in any other way (the continuation contradicts the format of the answer).
     */
    private fun renumber(base: String, addition: String, inCode: Boolean): Pair<String, Int>? {
        var fenced = false
        var last: MatchResult? = null
        var lastEnd = 0
        var position = 0
        for (line in base.split('\n')) {
            if (fenceLine.containsMatchIn(line)) fenced = !fenced
            else if (!fenced) numbered.matchEntire(line)?.let { last = it; lastEnd = position + line.length }
            position += line.length + 1
        }
        val item = last ?: return addition to 0
        if (listClosed.containsMatchIn(base.substring(lastEnd.coerceAtMost(base.length)))) return addition to 0
        val indent = item.groupValues[1]
        val delimiter = item.groupValues[3]
        val lastNumber = item.groupValues[2].toInt()
        val lines = addition.split('\n').toMutableList()
        fenced = inCode
        var expected = 0
        var changed = 0
        var blank = false
        for (index in lines.indices) {
            // The first piece continues the line the answer ended on unless the seam put it on a new line.
            if (index == 0 && addition.isNotEmpty() && addition[0] != '\n' && base.isNotEmpty() && base.last() != '\n') continue
            val line = lines[index]
            if (fenceLine.containsMatchIn(line)) { fenced = !fenced; continue }
            if (fenced) continue
            val match = numbered.matchEntire(line)
            if (match == null) {
                // A new paragraph before any item: the list of the answer is over, a later „1.“ starts another one.
                if (line.isBlank()) blank = blank || index > 0
                else if (expected == 0 && blank && !line.first().isWhitespace()) return addition to 0
                continue
            }
            if (match.groupValues[1] != indent || match.groupValues[3] != delimiter) continue
            val number = match.groupValues[2].toInt()
            if (expected == 0) {
                when {
                    number == lastNumber + 1 -> return addition to 0
                    number > lastNumber + 1 -> return addition to 0
                    number == 1 && lastNumber >= 2 -> expected = 1
                    else -> return null
                }
            }
            if (number != expected) break
            lines[index] = indent + (lastNumber + number) + delimiter + match.groupValues[4]
            expected++
            changed++
        }
        return lines.joinToString("\n") to changed
    }
}
