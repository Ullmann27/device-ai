package at.heinz.deviceai.ui

import java.awt.Color
import java.awt.image.BufferedImage

/** Synthetic checks for measurement semantics, not an acceptance test of the app's visual quality. */
object HollywoodFuiReferenceFidelityChecks {
    @JvmStatic fun main(args: Array<String>) {
        val tool = HollywoodFuiReferenceFidelity
        check(tool.hueBucket(Color.CYAN.rgb) == 2) { "Cyan bucket changed" }
        val cyan = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, 0x7f00ffff) }
        val rotated = tool.hueRotate(cyan, 180f).getRGB(0, 0)
        check(tool.hueBucket(rotated) == 5) { "180-degree cyan control must be off-palette" }
        check(rotated ushr 24 == 0x7f) { "Hue rotation must preserve alpha" }
        check(tool.hueRotate(cyan, 0f).getRGB(0, 0) == cyan.getRGB(0, 0)) { "Zero rotation changed pixels" }

        fun solid(gray: Int) = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics()
            g.color = Color(gray, gray, gray)
            g.fillRect(0, 0, 100, 100)
            g.color = Color.WHITE
            g.fillRect(35, 35, 30, 30)
            g.dispose()
        }
        val rect = HollywoodFuiReferenceFidelity.Rect(20, 20, 80, 80)
        check(tool.measure(solid(63), rect).litRimShare == 0.0) { "Value below 0.25 must be dark" }
        check(tool.measure(solid(64), rect).litRimShare == 1.0) { "Lit threshold must not depend on interior" }
        val white = tool.measure(solid(255), rect)
        check(white.neutral == 1.0 && white.offPalette == 0.0) { "Neutral pixels must not count as off-palette" }
        println("Reference fidelity synthetic checks: 7 PASS")
    }
}
