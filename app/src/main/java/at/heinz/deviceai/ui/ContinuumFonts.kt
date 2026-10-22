package at.heinz.deviceai.ui

/**
 * Bundled open-licence fonts (SIL OFL 1.1, licence texts next to the files in assets/fonts):
 * Montserrat for display type (brand, section titles), Inter for text and figures.
 * Device and JVM preview load the same files, so typography is identical in both.
 */
object ContinuumFonts {
    private val display = sortedMapOf(300 to "fonts/Montserrat-Light.ttf", 400 to "fonts/Montserrat-Regular.ttf",
        500 to "fonts/Montserrat-Medium.ttf", 600 to "fonts/Montserrat-SemiBold.ttf")
    private val text = sortedMapOf(300 to "fonts/Inter-Light.otf", 400 to "fonts/Inter-Regular.otf",
        500 to "fonts/Inter-Medium.otf", 600 to "fonts/Inter-SemiBold.otf", 700 to "fonts/Inter-Bold.otf")

    /** Asset path of the closest bundled weight. */
    fun assetPath(family: FontFamily, weight: Int): String {
        val table = if (family == FontFamily.DISPLAY) display else text
        return table.entries.minBy { kotlin.math.abs(it.key - weight) }.value
    }

    val allAssets: List<String> get() = (display.values + text.values).toList()
}
