package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisDesign.Type
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Design-Lead 17.11.2026: honest standby state for the three work windows.
 *
 * Owner photo: empty work windows read as black voids with a lone "—". ref-d asks for
 * glass with volume, light and depth. A vacant window therefore shows
 *  - an inner glass volume (cyan top sheen, warm amber floor light, specular rim),
 *  - a projected wireframe emblem of what *will* appear there (document / steps / draft),
 *    standing on a small projector ring with a light cone,
 *  - a short German caption saying what is missing and how to start it.
 *
 * No value is invented: the caption only names the absence and the real entry point
 * ("Dateien" in the dock, a spoken or typed order). As soon as real document, step or
 * draft data exists, [JarvisCockpitPanels] draws that instead and this is never called.
 * Everything is deterministic vector drawing, so host previews equal the device path
 * except for the GPU glass shader that may sit underneath.
 */
object JarvisVacantHologram {
    enum class Kind { DOCUMENT, PROCESS, DRAFT }

    data class Copy(val title: String, val hint: String, val shortHint: String)

    fun copy(kind: Kind): Copy = when (kind) {
        Kind.DOCUMENT -> Copy("Kein Dokument geöffnet",
            "Über „Dateien“ ein PDF oder Foto öffnen – oder den Auftrag einfach sagen.",
            "Über „Dateien“ öffnen.")
        Kind.PROCESS -> Copy("Keine laufenden Prozesse",
            "Bei einem Auftrag erscheinen hier die echten Arbeitsschritte.",
            "Schritte kommen mit dem Auftrag.")
        Kind.DRAFT -> Copy("Noch kein Entwurf",
            "Der Entwurf entsteht hier, sobald ein Dokument bearbeitet wird.",
            "Entsteht beim Bearbeiten.")
    }

    /** Below this emblem edge the window is too small for a readable hologram; text only. */
    const val MIN_EMBLEM_DP = 26f
    const val MAX_EMBLEM_DP = 92f

    data class Layout(val area: Box, val emblem: Float, val emblemCx: Float, val emblemCy: Float,
                      val projectorY: Float, val textTop: Float)

    /** Pure geometry (unit-tested): emblem, projector and caption never leave [area]. */
    fun layout(area: Box, titleCap: Float, hintLines: Int, hintLineH: Float): Layout {
        val textH = titleCap + if (hintLines > 0) 8f + hintLines * hintLineH else 0f
        val room = area.height - textH - 14f
        val raw = minOf(area.width * .40f, room * .62f, MAX_EMBLEM_DP)
        val emblem = if (raw >= MIN_EMBLEM_DP) raw else 0f
        val block = if (emblem > 0f) emblem * 1.32f + 12f + textH else textH
        val top = area.top + ((area.height - block) / 2f).coerceAtLeast(0f)
        val cy = top + emblem * .5f
        val projectorY = top + emblem * 1.12f
        val textTop = if (emblem > 0f) top + emblem * 1.32f + 12f else top
        return Layout(area, emblem, area.centerX, cy, projectorY, textTop)
    }

    fun draw(p: ContinuumPainter, panel: Box, contentTop: Float, kind: Kind) {
        if (panel.width < 40f || panel.height < 40f) return
        glassVolume(p, panel)
        val area = Box(panel.left + 10f, contentTop, panel.right - 10f, panel.bottom - 10f)
        if (area.height < 16f || area.width < 30f) return
        val text = copy(kind)
        val titleStyle = Type.tileLabel.copy(size = (area.width * .085f).coerceIn(12f, 15f))
        val hintStyle = Type.caption.copy(size = (area.width * .06f).coerceIn(10f, 11.5f))
        val titleLines = JarvisCockpitGridContent.wrap(p, text.title, titleStyle, area.width - 6f).take(2)
        val titleLineH = p.capHeight(titleStyle) + 5f
        val titleCap = titleLineH * titleLines.size - 5f
        val hintLineH = p.capHeight(hintStyle) + 5f
        // A hint is shown whole or not at all: never a sentence cut off mid-way.
        val candidates = listOf(text.hint, text.shortHint).map {
            JarvisCockpitGridContent.wrap(p, it, hintStyle, area.width - 8f)
        }.filter { it.size <= 3 } + listOf(emptyList())
        var hint = candidates.first()
        var l = layout(area, titleCap, hint.size, hintLineH)
        for (c in candidates) {
            hint = c; l = layout(area, titleCap, c.size, hintLineH)
            if (fits(l, area, titleCap, c.size, hintLineH)) break
        }
        if (l.emblem > 0f) {
            projector(p, l)
            when (kind) {
                Kind.DOCUMENT -> documentEmblem(p, l.emblemCx, l.emblemCy, l.emblem)
                Kind.PROCESS -> processEmblem(p, l.emblemCx, l.emblemCy, l.emblem)
                Kind.DRAFT -> draftEmblem(p, l.emblemCx, l.emblemCy, l.emblem)
            }
        }
        titleLines.forEachIndexed { i, line ->
            p.drawText(line, area.centerX, l.textTop + p.capHeight(titleStyle) + i * titleLineH, titleStyle,
                Fill.Solid(Colors.text), TextAlign.CENTER)
        }
        val ty = l.textTop + titleCap
        hint.forEachIndexed { i, line ->
            p.drawText(line, area.centerX, ty + 8f + (i + 1) * hintLineH - 5f, hintStyle,
                Fill.Solid(Colors.textSecondary), TextAlign.CENTER)
        }
    }

    /** Caption block (title + hint) ends inside the area. */
    fun fits(l: Layout, area: Box, titleCap: Float, hintLines: Int, hintLineH: Float): Boolean =
        l.textTop + titleCap + (if (hintLines > 0) 8f + hintLines * hintLineH else 0f) <= area.bottom + .5f

    /** Glass with volume: sheen from above, warm floor light, specular top rim, amber foot rim. */
    fun glassVolume(p: ContinuumPainter, b: Box) {
        val r = minOf(14f, minOf(b.width, b.height) * .25f)
        val inset = 3f
        p.fillRoundRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, r,
            Fill.Linear(0f, b.top, 0f, b.bottom, intArrayOf(
                Argb.withAlpha(Colors.cyan, .10f), Argb.withAlpha(Colors.cyanDeep, .04f),
                Argb.withAlpha(Argb.rgb(0, 2, 8), .0f), Argb.withAlpha(Colors.warn, .07f)),
                floatArrayOf(0f, .22f, .70f, 1f)))
        p.setAdditive(true)
        p.fillOval(b.left + b.width * .10f, b.bottom - b.height * .30f, b.right - b.width * .10f, b.bottom + b.height * .10f,
            Fill.Radial(b.centerX, b.bottom, b.width * .48f,
                intArrayOf(Argb.withAlpha(Colors.warn, .14f), Argb.withAlpha(Colors.magenta, .04f), 0)))
        p.drawLine(b.left + r, b.top + inset + .8f, b.right - r, b.top + inset + .8f, 1.1f,
            Fill.Linear(b.left, 0f, b.right, 0f, intArrayOf(0, Argb.withAlpha(Colors.glowCore, .55f), 0)))
        p.drawLine(b.left + r * 1.6f, b.bottom - inset - .8f, b.right - r * 1.6f, b.bottom - inset - .8f, 1.0f,
            Fill.Linear(b.left, 0f, b.right, 0f, intArrayOf(0, Argb.withAlpha(Colors.warn, .45f), 0)))
        p.setAdditive(false)
    }

    private fun projector(p: ContinuumPainter, l: Layout) {
        val s = l.emblem; val cx = l.emblemCx; val py = l.projectorY
        val rx = s * .62f; val ry = s * .13f
        p.setAdditive(true)
        // Light cone from the projector ring up through the emblem.
        val cone = VectorPath().moveTo(cx - rx * .92f, py).lineTo(cx - s * .30f, l.emblemCy - s * .52f)
            .lineTo(cx + s * .30f, l.emblemCy - s * .52f).lineTo(cx + rx * .92f, py).close()
        p.fillPath(cone, Fill.Linear(cx, py, cx, l.emblemCy - s * .52f,
            intArrayOf(Argb.withAlpha(Colors.cyan, .20f), Argb.withAlpha(Colors.violet, .06f), 0)))
        p.fillOval(cx - rx * 1.25f, py - ry * 1.6f, cx + rx * 1.25f, py + ry * 1.8f,
            Fill.Radial(cx, py, rx * 1.25f, intArrayOf(Argb.withAlpha(Colors.cyan, .26f), Argb.withAlpha(Colors.warn, .06f), 0)))
        p.strokeOval(cx - rx, py - ry, cx + rx, py + ry, 1.3f,
            Fill.Linear(cx - rx, py, cx + rx, py, intArrayOf(Colors.warn, Colors.cyan, Colors.glowCore, Colors.cyan, Colors.warn)), .85f)
        p.strokeOval(cx - rx * .62f, py - ry * .62f, cx + rx * .62f, py + ry * .62f, 1f, Fill.Solid(Colors.cyan), .55f)
        p.setAdditive(false)
    }

    private fun holo(p: ContinuumPainter, path: VectorPath, w: Float, color: Int = Colors.cyanSoft) {
        p.setAdditive(true)
        p.strokePath(path, w * 3.2f, Fill.Solid(color), .10f)
        p.strokePath(path, w, Fill.Solid(color), .92f)
        p.setAdditive(false)
    }

    private fun page(cx: Float, cy: Float, w: Float, h: Float, fold: Float) = VectorPath()
        .moveTo(cx - w / 2f, cy - h / 2f).lineTo(cx + w / 2f - fold, cy - h / 2f).lineTo(cx + w / 2f, cy - h / 2f + fold)
        .lineTo(cx + w / 2f, cy + h / 2f).lineTo(cx - w / 2f, cy + h / 2f).close()

    private fun documentEmblem(p: ContinuumPainter, cx: Float, cy: Float, s: Float) {
        val w = s * .56f; val h = s * .74f; val lw = (s / 52f).coerceIn(.9f, 1.6f)
        p.save(); p.rotate(-9f, cx, cy)
        holo(p, page(cx - s * .10f, cy + s * .02f, w, h, 0f), lw * .8f, Colors.violetRim)
        p.restore()
        p.save(); p.rotate(5f, cx, cy)
        p.fillPath(page(cx + s * .04f, cy - s * .02f, w, h, s * .14f),
            Fill.Linear(cx, cy - h / 2f, cx, cy + h / 2f, intArrayOf(Argb.withAlpha(Colors.cyan, .16f), Argb.withAlpha(Colors.cyanDeep, .05f))))
        holo(p, page(cx + s * .04f, cy - s * .02f, w, h, s * .14f), lw)
        val x0 = cx + s * .04f - w / 2f + w * .16f
        for (i in 0 until 4) {
            val y = cy - h * .20f + i * h * .15f
            val len = w * (if (i == 3) .42f else .68f)
            p.drawLine(x0, y, x0 + len, y, lw, Fill.Solid(Colors.cyanSoft), .70f)
        }
        p.restore()
        // Scan beam crossing the page: the place where reading will happen.
        p.setAdditive(true)
        p.drawLine(cx - s * .46f, cy + s * .06f, cx + s * .46f, cy + s * .06f, lw * 1.2f,
            Fill.Linear(cx - s * .46f, 0f, cx + s * .46f, 0f, intArrayOf(0, Argb.withAlpha(Colors.warn, .9f), 0)))
        p.setAdditive(false)
    }

    private fun processEmblem(p: ContinuumPainter, cx: Float, cy: Float, s: Float) {
        val lw = (s / 52f).coerceIn(.9f, 1.6f)
        p.setAdditive(true)
        for ((k, a) in listOf(.50f to .80f, .36f to .55f)) {
            p.strokeOval(cx - s * k, cy - s * k * .34f, cx + s * k, cy + s * k * .34f, lw, Fill.Solid(Colors.cyan), a)
        }
        p.strokeArc(cx - s * .50f, cy - s * .17f, cx + s * .50f, cy + s * .17f, 200f, 110f, lw * 2.4f, Fill.Solid(Colors.warn), .45f)
        p.setAdditive(false)
        val nodes = listOf(-.34f to .18f, 0f to -.30f, .34f to .10f)
        val path = VectorPath().moveTo(cx + nodes[0].first * s, cy + nodes[0].second * s)
            .quadTo(cx - s * .22f, cy - s * .34f, cx + nodes[1].first * s, cy + nodes[1].second * s)
            .quadTo(cx + s * .26f, cy - s * .30f, cx + nodes[2].first * s, cy + nodes[2].second * s)
        holo(p, path, lw)
        nodes.forEachIndexed { i, (nx, ny) ->
            val x = cx + nx * s; val y = cy + ny * s; val r = s * (if (i == 1) .085f else .065f)
            JarvisNeon.halo(p, x, y, r * 2.6f, if (i == 1) Colors.warn else Colors.cyan, .30f)
            p.fillCircle(x, y, r, Fill.Radial(x, y, r, intArrayOf(Colors.glowCore, Argb.withAlpha(Colors.cyan, .35f))))
            p.strokeCircle(x, y, r * 1.45f, lw * .8f, Fill.Solid(Colors.cyanSoft), .70f)
        }
    }

    private fun draftEmblem(p: ContinuumPainter, cx: Float, cy: Float, s: Float) {
        val w = s * .58f; val h = s * .74f; val lw = (s / 52f).coerceIn(.9f, 1.6f)
        val px = cx - s * .08f
        p.fillPath(page(px, cy, w, h, 0f),
            Fill.Linear(px, cy - h / 2f, px, cy + h / 2f, intArrayOf(Argb.withAlpha(Colors.violet, .16f), Argb.withAlpha(Colors.cyanDeep, .05f))))
        holo(p, page(px, cy, w, h, 0f), lw, Colors.violetRim)
        val x0 = px - w / 2f + w * .16f
        for (i in 0 until 3) {
            val y = cy - h * .22f + i * h * .17f
            p.drawLine(x0, y, x0 + w * (if (i == 2) .30f else .62f), y, lw, Fill.Solid(Colors.cyanSoft), .65f)
        }
        // Pen resting on the last line.
        val tipX = x0 + w * .34f; val tipY = cy - h * .22f + 2 * h * .17f + s * .02f
        val pen = VectorPath().moveTo(tipX, tipY).lineTo(tipX + s * .36f, tipY - s * .40f)
        holo(p, pen, lw * 1.8f, Colors.warn)
        p.fillCircle(tipX, tipY, lw * 1.6f, Fill.Solid(Colors.glowCore))
        // Spark: four-point star, warm accent toward ref-d.
        val sx = cx + s * .34f; val sy = cy - s * .32f; val sr = s * .09f
        val star = VectorPath().moveTo(sx, sy - sr).quadTo(sx, sy, sx + sr, sy).quadTo(sx, sy, sx, sy + sr)
            .quadTo(sx, sy, sx - sr, sy).quadTo(sx, sy, sx, sy - sr).close()
        JarvisNeon.halo(p, sx, sy, sr * 2.4f, Colors.warn, .30f)
        p.fillPath(star, Fill.Solid(Colors.glowCore), .95f)
    }
}
