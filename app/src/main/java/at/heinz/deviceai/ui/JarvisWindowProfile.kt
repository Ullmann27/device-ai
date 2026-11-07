package at.heinz.deviceai.ui

/** Available app-window dp, after system insets. No device model or physical display size is assumed. */
data class JarvisWindowProfile(
    val widthClass: WidthClass,
    val fontScale: Float,
    val tileColumns: Int,
    val informationColumns: Int,
    val railWidthDp: Float,
    val referenceComposition: Boolean
) {
    enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

    companion object {
        const val MEDIUM_MIN_DP = 600f
        const val EXPANDED_MIN_DP = 840f
        const val REFERENCE_MIN_HEIGHT_DP = 560f

        fun resolve(widthDp: Float, heightDp: Float, fontScale: Float = 1f,
                    rail: JarvisShellLayout.Rail = JarvisShellLayout.Rail.LABELLED): JarvisWindowProfile {
            require(widthDp.isFinite() && widthDp > 0f && heightDp.isFinite() && heightDp > 0f)
            val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 2f) else 1f
            val size = when {
                widthDp < MEDIUM_MIN_DP -> WidthClass.COMPACT
                widthDp < EXPANDED_MIN_DP -> WidthClass.MEDIUM
                else -> WidthClass.EXPANDED
            }
            val railWidth = when {
                size == WidthClass.COMPACT -> 0f
                rail == JarvisShellLayout.Rail.ICONS -> 64f
                else -> (widthDp * 243f / 1536f).coerceIn(112f, 244f) * maxOf(1f, font)
            }
            val bodyWidth = (widthDp - railWidth - 28f).coerceAtLeast(1f)
            val maxColumns = when (size) {
                WidthClass.COMPACT -> 2
                WidthClass.MEDIUM -> 5
                WidthClass.EXPANDED -> 5
            }
            val readableColumns = ((bodyWidth + 8f) / (104f * maxOf(1f, font) + 8f)).toInt()
                .coerceIn(1, maxColumns)
            val reference = size != WidthClass.COMPACT && heightDp >= REFERENCE_MIN_HEIGHT_DP &&
                font <= 1.3f && readableColumns == 5 && rail == JarvisShellLayout.Rail.LABELLED
            return JarvisWindowProfile(size, font, if (reference) 5 else readableColumns,
                if (bodyWidth >= 1000f * maxOf(1f, font)) 4
                else if (bodyWidth >= 352f * maxOf(1f, font)) 2 else 1,
                railWidth, reference)
        }
    }
}
