package at.heinz.deviceai.ui

/**
 * Bundled open-licence fonts (licence texts next to the files in assets/fonts):
 * Exo 2 (SIL OFL 1.1) for display / titles, matching the technical HUD of the owner
 * reference plates, and Roboto (Apache 2.0) for body text and figures.
 * Device and JVM preview load the same files, so typography is identical in both.
 */
object ContinuumFonts {
    private val display = sortedMapOf(300 to "fonts/Exo2-Light.ttf", 400 to "fonts/Exo2-Regular.ttf",
        500 to "fonts/Exo2-Medium.ttf", 600 to "fonts/Exo2-SemiBold.ttf")
    private val text = sortedMapOf(300 to "fonts/Roboto-Light.ttf", 400 to "fonts/Roboto-Regular.ttf",
        500 to "fonts/Roboto-Medium.ttf", 700 to "fonts/Roboto-Bold.ttf")

    /** Asset path of the closest bundled weight. */
    fun assetPath(family: FontFamily, weight: Int): String {
        val table = if (family == FontFamily.DISPLAY) display else text
        return table.entries.minBy { kotlin.math.abs(it.key - weight) }.value
    }

    val allAssets: List<String> get() = (display.values + text.values).toList()
}
