package at.heinz.deviceai.cognitive

/**
 * Deterministic paragraph segmentation of ONE page of really read PDF text (Code64 reference management).
 *
 * The reader returns plain text with line breaks. Two cases:
 *  - the page text contains blank lines: every block between blank lines is a paragraph ([Result.exact] = true);
 *  - it does not: paragraph ends are derived from the line structure (a clearly shorter line that ends a sentence,
 *    followed by a line that starts a new one). That is an estimate of the layout and is reported as such
 *    ([Result.exact] = false) wherever a paragraph number is shown or sent to the model.
 *
 * A heading (one short line without sentence punctuation in front of running text) is not counted as a paragraph:
 * „der zweite Absatz“ means the second block of running text, as a reader would count. Nothing is invented: the
 * returned paragraphs consist only of characters of the page text (line breaks inside a paragraph become one space).
 */
internal object DocumentParagraphs {
    data class Result(val paragraphs: List<String>, val exact: Boolean)

    private val blankLine = Regex("\\n[ \\t\\u00A0]*(?:\\n[ \\t\\u00A0]*)+")
    private val whitespace = Regex("\\s+")
    private val sentenceEnd = Regex("[.!?…:][\"”“’»)\\]]*$")
    private val headingEnd = Regex("[.!?…;,][\"”“’»)\\]]*$")
    private const val HEADING_CHARS = 80

    fun of(pageText: String): Result {
        val text = pageText.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return Result(emptyList(), true)
        val blocks = blankLine.split(text).map { it.trim() }.filter { it.isNotEmpty() }
        if (blocks.size >= 2) return Result(withoutHeadings(blocks.map { it to !it.contains('\n') }), true)
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return Result(listOf(flat(text)), true)
        return estimated(lines)
    }

    private fun flat(block: String) = whitespace.replace(block, " ").trim()

    /**
     * [blocks]: text to "is a single line". A heading-like block is dropped when running text follows it – directly
     * or after further heading-like blocks (title and subtitle). Short blocks with no running text after them (a
     * page of list entries, a closing „Ort, Datum“) are kept: there they are the content.
     */
    private fun withoutHeadings(blocks: List<Pair<String, Boolean>>): List<String> {
        val headingLike = blocks.map { (block, singleLine) -> singleLine && isHeading(block) }
        val lastRunningText = headingLike.lastIndexOf(false)
        return blocks.filterIndexed { index, _ -> !(headingLike[index] && index < lastRunningText) }
            .map { flat(it.first) }
    }

    private fun isHeading(line: String) = line.length <= HEADING_CHARS && line.any { it.isLetter() } &&
        !headingEnd.containsMatchIn(line)

    private fun startsNew(line: String): Boolean {
        val first = line.firstOrNull() ?: return false
        return first.isUpperCase() || first.isDigit() || first in "•-–—*„\"»("
    }

    private fun estimated(lines: List<String>): Result {
        val full = lines.maxOf { it.length }
        val blocks = mutableListOf<Pair<String, Boolean>>()
        var current = mutableListOf<String>()
        fun close(heading: Boolean = false) {
            if (current.isNotEmpty()) blocks += current.joinToString("\n") to (heading && current.size == 1)
            current = mutableListOf()
        }
        var breaks = 0
        for ((index, line) in lines.withIndex()) {
            current += line
            val next = lines.getOrNull(index + 1) ?: break
            val shortLine = line.length * 4 < full * 3
            val headingLine = current.size == 1 && line.length * 5 < full * 3 && isHeading(line) && startsNew(next)
            if (headingLine) { close(heading = true); breaks++ }
            else if (shortLine && sentenceEnd.containsMatchIn(line) && startsNew(next)) { close(); breaks++ }
        }
        close()
        if (breaks == 0) return Result(listOf(flat(lines.joinToString("\n"))), false)
        return Result(withoutHeadings(blocks), false)
    }
}
