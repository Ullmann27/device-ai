package at.heinz.deviceai.ui

/**
 * Bundled open-licence fonts (licence texts next to the files in assets/fonts):
 * Space Grotesk for display / titles and Manrope for text and figures, matching
 * the approved Kommando 03 design. Both families use SIL OFL 1.1.
 * Device and JVM preview load the same files, so typography is identical in both.
 */
object ContinuumFonts {
    private val display = sortedMapOf(400 to "fonts/SpaceGrotesk-Regular.ttf",
        500 to "fonts/SpaceGrotesk-Medium.ttf", 700 to "fonts/SpaceGrotesk-Bold.ttf")
    private val text = sortedMapOf(400 to "fonts/Manrope-Regular.ttf",
        500 to "fonts/Manrope-Medium.ttf", 600 to "fonts/Manrope-SemiBold.ttf",
        700 to "fonts/Manrope-Bold.ttf")

    /** Asset path of the closest bundled weight. */
    fun assetPath(family: FontFamily, weight: Int): String {
        val table = if (family == FontFamily.DISPLAY) display else text
        return table.entries.minWith(compareBy<Map.Entry<Int, String>> {
            kotlin.math.abs(it.key - weight)
        }.thenByDescending { it.key }).value
    }

    val allAssets: List<String> get() = (display.values + text.values).toList()
}
