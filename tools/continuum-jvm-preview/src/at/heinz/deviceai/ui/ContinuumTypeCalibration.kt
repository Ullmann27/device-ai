package at.heinz.deviceai.ui

import java.awt.image.BufferedImage
import java.io.File

/**
 * Derives text sizes from the reference image: for every text, the size whose rendered width equals the width
 * measured in reference-phone.png (px / 1.7306 = dp). Run: java -cp <jar> at.heinz.deviceai.ui.ContinuumTypeCalibration <repo>
 */
object ContinuumTypeCalibration {
    @JvmStatic fun main(args: Array<String>) {
        val assets = File(File(args.getOrElse(0) { "." }), "app/src/main/assets")
        val g = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB).createGraphics()
        val p = Java2DContinuumPainter(g, assets)
        val px = ContinuumTheme.REFERENCE_DENSITY
        // text, family, weight, letterSpacingEm, measured reference width in px
        val items = listOf(
            listOf("Continuum", "D", 500, 0f, 217), listOf("Dein KI-Universum. Ohne Grenzen.", "T", 400, 0f, 229),
            listOf("VERBUNDEN", "T", 700, 0f, 79), listOf("Globale Ressourcen", "T", 400, 0f, 102),
            listOf("ANALYSIEREN", "T", 700, 0f, 89), listOf("Mehrdimensional", "T", 400, 0f, 88),
            listOf("Modellstatus", "T", 500, 0f, 136), listOf("Continuum Ultra", "T", 400, 0f, 126), listOf("Online", "T", 500, 0f, 48),
            listOf("Leistung", "T", 400, 0f, 53), listOf("98%", "T", 500, 0f, 40), listOf("Reaktionszeit", "T", 400, 0f, 86),
            listOf("320 ms", "T", 500, 0f, 64), listOf("Kontextfenster", "T", 400, 0f, 96), listOf("1M Tokens", "T", 500, 0f, 92),
            listOf("Lernen & Forschung", "T", 500, 0f, 174), listOf("247", "T", 500, 0f, 56), listOf("neue Erkenntnisse", "T", 400, 0f, 117),
            listOf("+12%", "T", 500, 0f, 35), listOf("Neueste Forschungspapiere analysiert", "T", 400, 0f, 207), listOf("vor 6 Min.", "T", 400, 0f, 51),
            listOf("Speicher & Quellen", "T", 500, 0f, 161), listOf("78%", "T", 500, 0f, 53), listOf("belegt", "T", 400, 0f, 36),
            listOf("Eigene Daten", "T", 400, 0f, 77), listOf("432 GB", "T", 500, 0f, 45), listOf("12 aktive Quellen", "T", 500, 0f, 108),
            listOf("Live-Synchronisation", "T", 400, 0f, 110), listOf("Entwicklung & Code", "T", 500, 0f, 177),
            listOf("Alle Systeme nominal", "T", 500, 0f, 121), listOf("aktive Projekte", "T", 400, 0f, 83),
            listOf("Start", "T", 500, 0f, 36), listOf("Entwickeln", "T", 500, 0f, 75),
            listOf("CONTINUUM", "D", 400, .456f, 218), listOf("INTELLIGENCE  LIVES  ON", "D", 500, .476f, 323)
        )
        // Fold reference (reference-fold.png, 1.54 px/dp): widths measured in dp directly
        val fold = listOf(
            listOf("Modellstatus", "T", 500, 0f, 74.7f), listOf("Continuum Ultra", "T", 400, 0f, 77.9f), listOf("Online", "T", 500, 0f, 25.3f),
            listOf("Leistung", "T", 400, 0f, 34.7f), listOf("98%", "T", 500, 0f, 33.1f), listOf("Modus", "T", 400, 0f, 23.1f),
            listOf("Intelligenter Assistent", "T", 400, 0f, 86.4f), listOf("Kontextfenster", "T", 400, 0f, 50.3f), listOf("1M Tokens", "T", 500, 0f, 45.1f),
            listOf("Heute, 14:12", "T", 400, 0f, 45.1f), listOf("Lernen & Forschung", "T", 500, 0f, 99f), listOf("Live", "T", 500, 0f, 16.2f),
            listOf("247", "T", 500, 0f, 37.7f), listOf("neue Erkenntnisse", "T", 400, 0f, 73.4f), listOf("+12%", "T", 500, 0f, 22f),
            listOf("Neueste Forschungspapiere analysiert", "T", 400, 0f, 137f), listOf("vor 6 Min.", "T", 400, 0f, 35f)
        )
        for (item in fold) {
            val text = item[0] as String
            val family = if (item[1] == "D") FontFamily.DISPLAY else FontFamily.TEXT
            var size = 10f
            repeat(30) { size *= (item[4] as Float) / p.measureText(text, TextStyle(family, item[2] as Int, size)) }
            println(String.format("FOLD %-33s %s%d  %.2f sp", text, item[1], item[2], size))
        }
        for (item in items) {
            val text = item[0] as String
            val family = if (item[1] == "D") FontFamily.DISPLAY else FontFamily.TEXT
            val weight = item[2] as Int
            val spacing = item[3] as Float
            val target = (item[4] as Int) / px
            var size = 10f
            repeat(30) {
                val w = p.measureText(text, TextStyle(family, weight, size, size * spacing))
                size *= target / w
            }
            println(String.format("%-38s %s%d  %.2f sp  (target %.1f dp)", text, item[1], weight, size, target))
        }
    }
}
