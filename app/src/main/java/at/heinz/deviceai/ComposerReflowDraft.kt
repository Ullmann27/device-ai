package at.heinz.deviceai

/** Immutable snapshot of the composer while the Fold changes its window width.
 * No Android runtime dependency: selection restoration can be tested on the JVM.
 */
data class ComposerReflowDraft(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val hadFocus: Boolean,
    val imeWasVisible: Boolean
) {
    /** Android Editable.setSelection(start, end) requires ordered in-range offsets. */
    fun safeSelection(): Pair<Int, Int> {
        val start = selectionStart.takeIf { it >= 0 }?.coerceAtMost(text.length) ?: text.length
        val end = selectionEnd.takeIf { it >= 0 }?.coerceAtMost(text.length) ?: start
        return minOf(start, end) to maxOf(start, end)
    }
}
