package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.JarvisDesign
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisNeon
import at.heinz.deviceai.ui.TextAlign

/**
 * Input of the ref5 JARVIS greeting card. [hourOfDay] = real local hour (0..23) or null when unknown;
 * [statusText] = an optional true statement supplied by a real source (e.g. Kern); null ⇒ the neutral offer only.
 */
data class JarvisGreeting(val hourOfDay: Int?, val statusText: String? = null)

/**
 * ref5 greeting card under the head: header "JARVIS" with waveform icon and subtitle, below a glass speech bubble.
 * The greeting comes from the real clock only; nothing claims a sync or state without a source.
 * Measured in ref5 (1448 × 1086 px): x 290–565, y 390–555 px (275 × 165 px); layout below in those pixels.
 */
class JarvisGreetingCard {
    private val lines = ArrayList<String>(MAX_LINES)
    private var wrappedFor: String? = null
    private var wrappedWidth = -1f
    private val cyanFill = Fill.Solid(Colors.cyan)
    private val subFill = Fill.Solid(Colors.textSecondary)
    private val textFill = Fill.Solid(Colors.text)
    private val bubbleFill = Fill.Solid(Colors.surface)
    private val bubbleRim = Fill.Solid(Colors.navRimActive)
    private val iconFill = Fill.Solid(Colors.cyanSoft)

    fun greetingLine(g: JarvisGreeting): String = when (g.hourOfDay) {
        null -> NONE
        in 5..11 -> "Good morning."
        in 12..17 -> "Good afternoon."
        in 0..23 -> "Good evening."
        else -> NONE
    }

    fun bodyText(g: JarvisGreeting): String =
        g.statusText?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it $OFFER" } ?: OFFER

    fun draw(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, g: JarvisGreeting, glow: Boolean = true) {
        val k = (b - t) / REF_PX_H
        fun x(px: Float) = l + px * k
        fun y(px: Float) = t + px * k
        drawWave(p, x(15f), y(20f), 37f * k, 24f * k)
        JarvisNeon.text(p, TITLE, x(72f), y(25f), JarvisDesign.Type.heading, Colors.cyan, TextAlign.LEFT,
            if (glow) JarvisDesign.Glow.SUBTLE else JarvisDesign.Glow.OFF)
        p.drawText(SUBTITLE, x(72f), y(43f), JarvisDesign.Type.caption, subFill)
        val bt = y(52f); val radius = JarvisDesign.Styles.tile.radius * k / JarvisUserCard.DP_PER_PX
        if (glow) {
            JarvisNeon.rimGlow(p, l, bt, r, b, radius, JarvisDesign.Glow.NORMAL, 1f, bubbleRim)
            JarvisNeon.bloomRoundRect(p, l, bt, r, b, radius, Colors.navRimActive, .5f)
        }
        p.fillRoundRect(l, bt, r, b, radius, bubbleFill)
        p.strokeRoundRect(l, bt, r, b, radius, 1f, bubbleRim)
        val tx = x(30f); val maxW = r - 18f * k - tx
        p.drawText(greetingLine(g), tx, y(78f), BODY_STYLE, textFill)
        wrap(p, bodyText(g), maxW)
        for (i in lines.indices) p.drawText(lines[i], tx, y(94f + i * LINE_PX), BODY_STYLE, textFill)
    }

    /** Greedy word wrap into at most [MAX_LINES]; cached per text and width. */
    private fun wrap(p: ContinuumPainter, text: String, maxW: Float) {
        if (text == wrappedFor && maxW == wrappedWidth) return
        wrappedFor = text; wrappedWidth = maxW; lines.clear()
        val sb = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (sb.isEmpty()) word else "$sb $word"
            if (sb.isNotEmpty() && p.measureText(candidate, BODY_STYLE) > maxW) {
                lines += sb.toString(); sb.setLength(0); sb.append(word)
                if (lines.size == MAX_LINES) return
            } else { sb.setLength(0); sb.append(candidate) }
        }
        if (sb.isNotEmpty() && lines.size < MAX_LINES) lines += sb.toString()
    }

    private fun drawWave(p: ContinuumPainter, l: Float, cy: Float, w: Float, h: Float) {
        for (i in WAVE.indices) {
            val bx = l + w * i / (WAVE.size - 1)
            val bh = h * WAVE[i] / 2f
            p.drawLine(bx, cy - bh, bx, cy + bh, 1.6f, iconFill)
        }
    }

    companion object {
        const val TITLE = "JARVIS"
        const val SUBTITLE = "Your On-Device AI Companion"
        const val OFFER = "How can I help you create, research, automate, or optimize your device today?"
        const val NONE = "—"
        const val REF_PX_H = 165f
        const val LINE_PX = 14f
        const val MAX_LINES = 4
        private val BODY_STYLE = JarvisDesign.Type.caption
        private val WAVE = floatArrayOf(.2f, .45f, .8f, 1f, .6f, .9f, .5f, .3f, .15f)
        /** Grid share of the ref5 display area (x 80–1375, y 67–986 px). */
        const val GRID_LEFT = .162f
        const val GRID_TOP = .351f
        const val GRID_WIDTH = .212f
        const val GRID_HEIGHT = .180f
    }
}
