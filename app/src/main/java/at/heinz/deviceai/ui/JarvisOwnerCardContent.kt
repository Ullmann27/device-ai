package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.OwnerRecognition
import at.heinz.deviceai.cockpit.OwnerRecognitionState
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/** Reference card content using only the published recognition signal. Other sensors are not connected. */
object JarvisOwnerCardContent {
    fun draw(p: ContinuumPainter, box: Box, owner: OwnerRecognition) {
        val c = JarvisDesign.Colors
        val scale = minOf(box.width / 132f, box.height / 145f).coerceIn(.65f, 1.4f)
        val pad = 9f * scale
        val icon = 22f * scale
        val left = box.left + pad
        val top = box.top + pad
        recognitionIcon(p, left, top, icon)
        val textLeft = left + icon + 6f * scale
        val title = JarvisDesign.Type.heading.copy(size = 9.5f * scale)
        val body = JarvisDesign.Type.caption.copy(size = 8.5f * scale)
        val recognized = owner.state == OwnerRecognitionState.RECOGNIZED
        val label = when (owner.state) {
            OwnerRecognitionState.RECOGNIZED -> "Nutzer erkannt"
            OwnerRecognitionState.NOT_RECOGNIZED -> "Nicht erkannt"
            OwnerRecognitionState.UNKNOWN -> "—"
        }
        val titleRoom = box.right - pad - textLeft - if (recognized) 9f * scale else 0f
        p.drawText(JarvisShellRenderer.fit(p, label, title, titleRoom), textLeft, top + p.capHeight(title),
            title, Fill.Solid(c.text))
        val method = owner.method?.takeIf { owner.state != OwnerRecognitionState.UNKNOWN && it.isNotBlank() } ?: "—"
        p.drawText(JarvisShellRenderer.fit(p, method, body, box.right - pad - textLeft), textLeft,
            top + icon, body, Fill.Solid(c.textSecondary))
        if (recognized) {
            val x = box.right - pad - 5f * scale
            val y = top + 5f * scale
            val path = VectorPath().moveTo(x - 3f * scale, y).lineTo(x, y + 3f * scale)
                .lineTo(x + 5f * scale, y - 4f * scale)
            p.strokePath(path, 1.4f * scale, Fill.Solid(c.okSoft))
        }
        val divider = top + icon + 9f * scale
        p.drawLine(left, divider, box.right - pad, divider, .6f, Fill.Solid(c.cardRimStart), .65f)
        val rowTop = divider + 5f * scale
        val rowHeight = (box.bottom - pad - rowTop) / 4f
        listOf("Anwesenheit", "Aufmerksamkeit", "Blick", "Stimmung").forEachIndexed { i, name ->
            val cy = rowTop + rowHeight * (i + .5f)
            val ix = left + 5f * scale
            val r = 4f * scale
            when (i) {
                0 -> p.strokeCircle(ix, cy, r * .7f, 1f * scale, Fill.Solid(c.textMuted))
                1 -> ContinuumIcons.draw(p, ContinuumIcons.Icon.PULSE, ix, cy, 11f * scale, Fill.Solid(c.cyanSoft))
                2 -> {
                    p.strokeOval(ix - r, cy - r * .55f, ix + r, cy + r * .55f, 1f * scale, Fill.Solid(c.cyanSoft))
                    p.strokeCircle(ix, cy, r * .38f, 1f * scale, Fill.Solid(c.cyanSoft))
                }
                3 -> {
                    p.strokeCircle(ix, cy, r, 1f * scale, Fill.Solid(c.cyanSoft))
                    p.strokeArc(ix - r * .55f, cy - r * .55f, ix + r * .55f, cy + r * .55f,
                        15f, 150f, .8f * scale, Fill.Solid(c.cyanSoft))
                }
            }
            val x = left + 16f * scale
            val labelRoom = box.right - pad - x - 9f * scale
            p.drawText(JarvisShellRenderer.fit(p, "$name:", body, labelRoom), x,
                cy + p.capHeight(body) / 2f, body, Fill.Solid(c.textSecondary))
            p.drawText("—", box.right - pad, cy + p.capHeight(body) / 2f,
                body, Fill.Solid(c.textMuted), TextAlign.RIGHT)
        }
    }

    private fun recognitionIcon(p: ContinuumPainter, l: Float, t: Float, size: Float) {
        val fill = Fill.Solid(JarvisDesign.Colors.cyanSoft)
        val w = size / 20f
        val arm = size * .25f
        val path = VectorPath()
        for ((x, sx) in listOf(l to 1f, l + size to -1f)) {
            for ((y, sy) in listOf(t to 1f, t + size to -1f)) {
                path.reset().moveTo(x + sx * arm, y).lineTo(x, y).lineTo(x, y + sy * arm)
                p.strokePath(path, w, fill)
            }
        }
        p.strokeCircle(l + size / 2f, t + size * .36f, size * .16f, w, fill)
        p.strokeArc(l + size * .22f, t + size * .57f, l + size * .78f, t + size * .95f,
            185f, 170f, w, fill)
    }
}
