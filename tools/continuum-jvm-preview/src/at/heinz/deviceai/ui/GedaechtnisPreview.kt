package at.heinz.deviceai.ui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** JVM preview (Java2D, same drawing code as the device) of the Gedächtnis pieces at ref5 crop size. args: repoRoot outDir */
object GedaechtnisPreview {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).apply { mkdirs() }
        fun shot(name: String, w: Int, h: Int, draw: (ContinuumPainter) -> Unit) {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            draw(Java2DContinuumPainter(g, File(root, "app/src/main/assets")))
            g.dispose(); ImageIO.write(img, "png", File(out, "$name.png")); println("wrote $name.png")
        }
        shot("orb", 300, 315) { p ->
            p.fillRect(0f, 0f, 300f, 315f, Fill.Linear(0f, 0f, 0f, 315f, intArrayOf(JarvisDesign.Colors.background, JarvisDesign.Colors.backgroundBottom)))
            ContinuumBrainOrbRenderer().draw(p, 190f, 140f, 110f)
        }
        // Vorschau ohne Gedächtnis-Anbindung: so sieht die Karte auf einem frischen Gerät aus (keine erfundenen Zahlen).
        shot("mem", 254, 167) { p ->
            p.fillRect(0f, 0f, 254f, 167f, Fill.Solid(JarvisDesign.Colors.background))
            p.scale(254f / JarvisMemoryActivityPanel.REF_W, 254f / JarvisMemoryActivityPanel.REF_W)
            JarvisMemoryActivityPanel.draw(p, 1f, 1f, JarvisMemoryActivityPanel.REF_W - 1f, JarvisMemoryActivityPanel.REF_H - 1f, MemoryActivitySnapshot.OFFLINE)
        }
        // Testdaten: echtes Test-Gedächtnis in einem Temp-Verzeichnis, echte Ledger-Einträge und echte Ereignisse.
        val dir = java.nio.file.Files.createTempDirectory("ged-live").toFile()
        try {
            val tracker = MemoryActivityTracker()
            val sub = at.heinz.deviceai.learning.ContinuumMemoryEvents.forApp(dir).addListener(tracker)
            val now = System.currentTimeMillis(); val t = longArrayOf(now)
            val core = at.heinz.deviceai.learning.ContinuumLearningCore(dir) { t[0] }
            val facts = listOf("Ich wohne in Steyr.", "Mein Hund heißt Bello.", "Vielleicht ziehe ich nach Linz.",
                "Ich trinke Kaffee schwarz.", "Mein Auto ist blau.", "Vielleicht lerne ich Spanisch.", "Ich arbeite am Device-AI-Projekt.", "Merke dir: Wenn ich im Lotto gewinne, kaufe ich ein Haus.")
            facts.forEachIndexed { i, f -> t[0] = now - (facts.size - i) * 3_600_000L / 2; core.confirmOwnerFact(f) }
            val L = at.heinz.deviceai.learning.CharNgramHashRetriever(at.heinz.deviceai.learning.TemporalCue(java.time.ZoneId.of("Europe/Vienna")))
            val rr = at.heinz.deviceai.learning.EventEmittingRetriever(L, dir)
            rr.index(facts.mapIndexed { i, f -> at.heinz.deviceai.learning.MemoryDoc("f$i", f, now, true) })
            listOf("Wo wohne ich?", "Wie heißt mein Hund?", "Welche Farbe hat mein Auto?", "Wie trinke ich Kaffee?").forEach { rr.search(it, now, 3) }
            at.heinz.deviceai.learning.ContinuumMemoryEventHub.awaitIdleForTest(dir)
            val snap = MemoryActivitySnapshot.from(at.heinz.deviceai.learning.ContinuumMemoryStatsReader(dir).read(), tracker)
            println("live " + snap.rows() + " " + snap.hourly?.toList())
            shot("mem-live", 254, 167) { p ->
                p.fillRect(0f, 0f, 254f, 167f, Fill.Solid(JarvisDesign.Colors.background))
                p.save(); p.scale(254f / JarvisMemoryActivityPanel.REF_W, 254f / JarvisMemoryActivityPanel.REF_W)
                JarvisMemoryActivityPanel.draw(p, 1f, 1f, JarvisMemoryActivityPanel.REF_W - 1f, JarvisMemoryActivityPanel.REF_H - 1f, snap)
                p.restore()
                p.drawText("Testdaten", 248f, 164f, JarvisDesign.Type.status, Fill.Solid(JarvisDesign.Colors.warn), TextAlign.RIGHT)
            }
            sub.close()
        } finally { dir.deleteRecursively() }
    }
}
