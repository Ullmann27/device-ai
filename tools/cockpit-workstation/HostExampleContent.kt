package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box

/** Synthetic host pixels only. */
object HostExampleContent {
    private val ink = Fill.Solid(0xff14243a.toInt())
    private val muted = Fill.Solid(0xff516378.toInt())
    private val light = Fill.Solid(0xffe5edf6.toInt())
    private fun style(size: Float, weight: Int = 400) = TextStyle(FontFamily.TEXT, weight, size)
    private fun text(p: ContinuumPainter, value: String, x: Float, y: Float,
                     width: Float, size: Float, fill: Fill, weight: Int = 400) {
        val font = style(size, weight)
        p.drawText(JarvisShellRenderer.fit(p, value, font, width), x, y, font, fill)
    }

    fun document(p: ContinuumPainter, width: Float, height: Float) {
        p.fillRect(0f, 0f, width, height, Fill.Solid(0xfff1f4f8.toInt()))
        p.fillRect(0f, 0f, width, 21f, Fill.Solid(0xffdce7f0.toInt()))
        text(p, "VORSCHAU · BEISPIELINHALT", 9f, 14f, width - 18f, 10f, ink, 700)
        text(p, "Projektplan.pdf", 10f, 39f, width - 70f, 13f, ink, 600)
        text(p, "1 / 1", width - 37f, 38f, 30f, 10f, muted)
        val top = 47f
        p.fillRoundRect(8f, top, width - 8f, height - 7f, 3f, Fill.Solid(0xffffffff.toInt()))
        p.clipRect(8f, top, width - 8f, height - 7f)
        text(p, "Nächste Schritte", 18f, top + 19f, width - 36f, 13f, ink, 600)
        val lines = listOf("1. Unterlagen ordnen", "2. Inhalt prüfen", "3. Entwurf vorbereiten")
        val gap = minOf(18f, (height - top - 28f) / 3f).coerceAtLeast(12f)
        lines.forEachIndexed { i, value ->
            text(p, value, 18f, top + 36f + i * gap, width - 36f, 11f, muted)
        }
    }

    fun files(p: ContinuumPainter, box: Box) {
        val rows = listOf("Projektplan.pdf", "Notizen.txt", "Checkliste.md")
        p.save()
        p.clipRect(box.left + 7f, box.top + 32f, box.right - 7f, box.bottom - 7f)
        rows.forEachIndexed { i, value ->
            val top = box.top + 35f + i * 25f
            if (i == 0) p.fillRoundRect(box.left + 7f, top, box.right - 7f, top + 22f,
                3f, Fill.Solid(0xff174151.toInt()))
            text(p, value, box.left + 13f, top + 15f, box.width - 26f, 11f, light, if (i == 0) 600 else 400)
        }
        p.restore()
    }

    fun result(p: ContinuumPainter, box: Box) {
        p.save()
        p.clipRect(box.left + 8f, box.top + 32f, box.right - 8f, box.bottom - 7f)
        listOf("Unterlagen ordnen", "Inhalt prüfen", "Entwurf vorbereiten").forEachIndexed { i, value ->
            val y = box.top + 48f + i * 25f
            p.fillCircle(box.left + 12f, y - 4f, 2f, Fill.Solid(0xff85c7dd.toInt()))
            text(p, value, box.left + 21f, y, box.width - 31f, 10.5f, light)
        }
        p.restore()
    }
}
