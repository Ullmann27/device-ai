package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumIcons.Icon

/** Shared surfaces of the reference design: glass cards, icon discs, pills, chevrons and text fitting. */
object ContinuumSurfaces {
    /** Dark glass card with the cyan-to-violet edge light of the reference. */
    fun glassCard(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, radius: Float = ContinuumTheme.CARD_RADIUS) {
        val fill = Fill.Linear(l, t, r, b, intArrayOf(ContinuumTheme.CARD_FILL_TOP, ContinuumTheme.CARD_FILL_BOTTOM))
        p.fillRoundRect(l - 1.5f, t - 1.5f, r + 1.5f, b + 1.5f, radius + 1.5f,
            Fill.Linear(l, t, r, b, intArrayOf(Argb.of(60, 40, 150, 255), Argb.of(10, 40, 90, 200), Argb.of(50, 120, 80, 255))), .55f)
        p.fillRoundRect(l, t, r, b, radius, fill)
        // soft inner sheen along the top
        p.fillRoundRect(l + 1f, t + 1f, r - 1f, t + (b - t) * .45f, radius - 1f,
            Fill.Linear(0f, t, 0f, t + (b - t) * .45f, intArrayOf(Argb.of(28, 90, 160, 255), 0)))
        val edge = Fill.Linear(l, t, r, b, intArrayOf(ContinuumTheme.BORDER_LEFT, ContinuumTheme.BORDER_TOP,
            Argb.rgb(30, 50, 110), ContinuumTheme.BORDER_RIGHT), floatArrayOf(0f, .35f, .65f, 1f))
        p.strokeRoundRect(l, t, r, b, radius, 1f, edge, .95f)
        // bright corner light top-left and violet light bottom-right, as in the reference
        p.strokeArc(l, t, l + radius * 2f, t + radius * 2f, 180f, 90f, 1.2f, Fill.Solid(Argb.rgb(80, 200, 255)), .7f)
        p.strokeArc(r - radius * 2f, b - radius * 2f, r, b, 0f, 90f, 1.2f, Fill.Solid(Argb.rgb(150, 110, 255)), .55f)
    }


    /** Shallow extruded glass control: inset bevel, directional highlights and a restrained bottom rim.
     * Shared between left and right hologram controls so lighting does not imply a mismatched layout.
     */
    fun raisedControlCard(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, radius: Float = 12f) {
        glassCard(p, l, t, r, b, radius)
        val height = b - t
        p.fillRoundRect(l + 1.5f, t + 1.5f, r - 1.5f, t + height * .38f, radius - 1.5f,
            Fill.Linear(l, t, l, t + height * .38f,
                intArrayOf(Argb.of(39, 160, 215, 255), Argb.of(0, 160, 215, 255))))
        p.drawLine(l + radius * .75f, t + 1.7f, r - radius * .75f, t + 1.7f, 1f,
            Fill.Solid(Argb.of(120, 178, 232, 255)))
        p.drawLine(l + radius * .75f, b - 1.8f, r - radius * .75f, b - 1.8f, 1.35f,
            Fill.Solid(Argb.of(92, 84, 58, 168)))
    }

    /** Round icon disc with blue ring and glowing line icon. */
    fun iconDisc(p: ContinuumPainter, icon: Icon, cx: Float, cy: Float, diameter: Float, iconSize: Float = diameter * .54f,
                 glow: Boolean = true) {
        val rr = diameter / 2f
        if (glow) p.fillCircle(cx, cy, rr * 1.35f, Fill.Radial(cx, cy, rr * 1.35f,
            intArrayOf(Argb.of(70, 40, 140, 255), 0)))
        p.fillCircle(cx, cy, rr, Fill.Radial(cx - rr * .3f, cy - rr * .3f, rr * 1.2f,
            intArrayOf(Argb.rgb(18, 44, 100), Argb.rgb(8, 20, 52))))
        p.strokeCircle(cx, cy, rr, 1f, Fill.Linear(cx - rr, cy - rr, cx + rr, cy + rr,
            intArrayOf(Argb.rgb(70, 160, 255), Argb.rgb(40, 70, 160), Argb.rgb(110, 90, 230))), .9f)
        // Small optical highlight makes each button read as a raised 3D control.
        p.fillCircle(cx - rr * .28f, cy - rr * .32f, rr * .45f,
            Fill.Radial(cx - rr * .3f, cy - rr * .35f, rr * .45f,
                intArrayOf(Argb.of(115, 170, 230, 255), 0)))
        p.strokeArc(cx - rr + 1f, cy - rr + 1f, cx + rr - 1f, cy + rr - 1f,
            20f, 155f, .9f, Fill.Solid(Argb.of(160, 160, 220, 255)), .8f)
        iconGlow(p, icon, cx, cy, iconSize)
    }

    /** Icon drawn with the cyan-blue gradient and a faint glow. */
    fun iconGlow(p: ContinuumPainter, icon: Icon, cx: Float, cy: Float, size: Float, stroke: Float = size / 12f) {
        val gradient = Fill.Linear(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f,
            intArrayOf(Argb.rgb(110, 225, 255), Argb.rgb(70, 140, 255)))
        ContinuumIcons.draw(p, icon, cx, cy, size, gradient, stroke * 2.6f, .16f)
        ContinuumIcons.draw(p, icon, cx, cy, size, gradient, stroke)
    }

    fun chevron(p: ContinuumPainter, cx: Float, cy: Float, size: Float = 12f, color: Int = Argb.rgb(196, 210, 235)) =
        ContinuumIcons.draw(p, Icon.CHEVRON_RIGHT, cx, cy, size, Fill.Solid(color), size / 9.5f)

    /** Rounded status pill with leading dot, e.g. "● Online". Returns the pill width. */
    fun pill(p: ContinuumPainter, left: Float, top: Float, height: Float, text: String, style: TextStyle, color: Int,
             fillAlpha: Float = .16f, minWidth: Float = 0f, alignRight: Boolean = false): Float {
        val textWidth = p.measureText(text, style)
        val dot = height * .18f
        val width = maxOf(minWidth, height * .55f + dot * 2f + height * .32f + textWidth + height * .55f)
        val l = if (alignRight) left - width else left
        val r = l + width
        p.fillRoundRect(l, top, r, top + height, height / 2f, Fill.Solid(Argb.withAlpha(color, fillAlpha)))
        p.strokeRoundRect(l, top, r, top + height, height / 2f, 1f, Fill.Solid(Argb.withAlpha(color, .55f)))
        val dotX = l + height * .55f + dot
        val cy = top + height / 2f
        p.fillCircle(dotX, cy, dot * 2.2f, Fill.Radial(dotX, cy, dot * 2.2f, intArrayOf(Argb.withAlpha(color, .5f), 0)))
        p.fillCircle(dotX, cy, dot, Fill.Solid(color))
        p.drawText(text, dotX + dot + height * .32f, cy + p.capHeight(style) / 2f, style, Fill.Solid(color))
        return width
    }

    /** Shortens [text] with an ellipsis until it fits [maxWidth]. */
    fun fit(p: ContinuumPainter, text: String, style: TextStyle, maxWidth: Float): String {
        if (maxWidth <= 0f) return ""
        if (p.measureText(text, style) <= maxWidth) return text
        var end = text.length
        while (end > 1 && p.measureText(text.substring(0, end) + "…", style) > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    /** Text with a vertical gradient (used for the large figures). */
    fun figureFill(top: Float, bottom: Float): Fill =
        Fill.Linear(0f, top, 0f, bottom, intArrayOf(Argb.rgb(232, 244, 255), Argb.rgb(140, 200, 255)))
}
