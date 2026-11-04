package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.JarvisDesign
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisNeon
import at.heinz.deviceai.ui.TextAlign
import at.heinz.deviceai.ui.VectorPath

/** Kern contract "User Recognized" (same names as on #481). Only real signal: device unlock (KeyguardManager). */
enum class OwnerRecognitionState { RECOGNIZED, NOT_RECOGNIZED, UNKNOWN }

data class OwnerRecognition(val state: OwnerRecognitionState, val method: String?, val sinceMillis: Long?) {
    companion object { val UNKNOWN = OwnerRecognition(OwnerRecognitionState.UNKNOWN, null, null) }
}

interface OwnerRecognitionSource {
    fun current(): OwnerRecognition
    fun addListener(l: (OwnerRecognition) -> Unit): AutoCloseable
}

fun interface UserCardRenderer {
    fun render(model: OwnerRecognition)
}

object JarvisUserCardSlot {
    @Volatile var renderer: UserCardRenderer? = null
        private set

    fun register(r: UserCardRenderer?) { renderer = r }
}

/**
 * ref5 owner-recognition card ("User Recognized", left of the head). Grid area "userCard" belongs to Kern; this class
 * only draws into the box it is given. Content is the real [OwnerRecognition]; presence / attention / gaze / emotion
 * have no source in the app and always show "—" in the same style.
 * Measured in ref5 (1448 × 1086 px): x 283–476, y 180–359 px ⇒ 193 × 179 px = 124 × 115 dp (1 px = 0,642 dp).
 */
class JarvisUserCard(private val onChanged: () -> Unit = {}) : UserCardRenderer {
    @Volatile var model: OwnerRecognition = OwnerRecognition.UNKNOWN
        private set

    private val path = VectorPath()
    private val titleFill = Fill.Solid(Colors.text)
    private val subFill = Fill.Solid(Colors.textSecondary)
    private val iconFill = Fill.Solid(Colors.cyanSoft)
    private val okFill = Fill.Solid(Colors.okSoft)
    private val dividerFill = Fill.Solid(Colors.rim)
    private val dimFill = Fill.Solid(Colors.textMuted)
    private val cardFill = Fill.Solid(Colors.surface)
    private val rimFill = Fill.Solid(Colors.panelRim)

    override fun render(model: OwnerRecognition) { this.model = model; onChanged() }

    fun title(m: OwnerRecognition = model) = when (m.state) {
        OwnerRecognitionState.RECOGNIZED -> "User Recognized"
        OwnerRecognitionState.NOT_RECOGNIZED -> "Not Recognized"
        OwnerRecognitionState.UNKNOWN -> NONE
    }

    fun subtitle(m: OwnerRecognition = model) = m.method?.takeIf { it.isNotBlank() } ?: NONE

    /** Draws the card into (l, t, r, b) in dp. Layout in ref5 pixels of the 193 × 179 px card, scaled to the box. */
    fun draw(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, glow: Boolean = true) {
        val k = (b - t) / REF_PX_H
        fun x(px: Float) = l + px * k
        fun y(px: Float) = t + px * k
        val radius = JarvisDesign.Styles.tile.radius * k / DP_PER_PX
        if (glow) JarvisNeon.rimGlow(p, l, t, r, b, radius, JarvisDesign.Glow.SUBTLE, 1f, rimFill)
        p.fillRoundRect(l, t, r, b, radius, cardFill)
        p.strokeRoundRect(l, t, r, b, radius, 1f, rimFill)
        val m = model
        drawUserIcon(p, x(15f), y(12f), 33f * k)
        val title = title(m)
        p.drawText(title, x(60f), y(29f), TITLE_STYLE, titleFill)
        if (m.state == OwnerRecognitionState.RECOGNIZED) {
            val cx = x(60f) + p.measureText(title, TITLE_STYLE) + 6f * k; val cy = y(24f)
            path.reset().moveTo(cx, cy).lineTo(cx + 4f * k, cy + 4f * k).lineTo(cx + 11f * k, cy - 5f * k)
            p.strokePath(path, 1.6f * k, okFill)
        }
        p.drawText(subtitle(m), x(60f), y(43f), ROW_STYLE, subFill)
        p.drawLine(x(10f), y(53f), r - 10f * k, y(53f), 1f, dividerFill, .8f)
        for (i in ROWS.indices) {
            val by = y(ROW_BASELINES[i])
            drawRowIcon(p, i, x(26f), by - 4f * k, k * 1.6f)
            val label = "${ROWS[i]}:"
            p.drawText(label, x(43f), by, ROW_STYLE, subFill)
            p.drawText(NONE, x(43f) + p.measureText(label, ROW_STYLE) + 6f * k, by, ROW_STYLE, dimFill)
        }
    }

    private fun drawUserIcon(p: ContinuumPainter, x: Float, y: Float, s: Float) {
        val c = s * .28f
        // corner brackets
        p.drawLine(x, y, x + c, y, 1.2f, iconFill); p.drawLine(x, y, x, y + c, 1.2f, iconFill)
        p.drawLine(x + s, y, x + s - c, y, 1.2f, iconFill); p.drawLine(x + s, y, x + s, y + c, 1.2f, iconFill)
        p.drawLine(x, y + s, x + c, y + s, 1.2f, iconFill); p.drawLine(x, y + s, x, y + s - c, 1.2f, iconFill)
        p.drawLine(x + s, y + s, x + s - c, y + s, 1.2f, iconFill); p.drawLine(x + s, y + s, x + s, y + s - c, 1.2f, iconFill)
        p.strokeCircle(x + s / 2f, y + s * .4f, s * .16f, 1.2f, iconFill)
        p.strokeArc(x + s * .24f, y + s * .62f, x + s * .76f, y + s * 1.05f, 180f, 180f, 1.2f, iconFill)
    }

    private fun drawRowIcon(p: ContinuumPainter, i: Int, cx: Float, cy: Float, u: Float) {
        val r = 4f * u
        when (i) {
            0 -> p.fillCircle(cx, cy, r * .7f, dimFill)   // presence: no source ⇒ muted, never the green 'active' dot
            1 -> {
                path.reset().moveTo(cx - r * 1.3f, cy).lineTo(cx - r * .5f, cy).lineTo(cx - r * .1f, cy - r)
                    .lineTo(cx + r * .4f, cy + r).lineTo(cx + r * .8f, cy).lineTo(cx + r * 1.3f, cy)
                p.strokePath(path, 1f, iconFill)
            }
            2 -> {
                p.strokeOval(cx - r * 1.3f, cy - r * .7f, cx + r * 1.3f, cy + r * .7f, 1f, iconFill)
                p.strokeCircle(cx, cy, r * .4f, 1f, iconFill)
            }
            else -> {
                p.strokeCircle(cx, cy, r, 1f, iconFill)
                p.strokeArc(cx - r * .55f, cy - r * .35f, cx + r * .55f, cy + r * .6f, 20f, 140f, 1f, iconFill)
            }
        }
    }

    companion object {
        const val NONE = "—"
        private val ROW_STYLE = JarvisDesign.Type.status
        val ROWS = arrayOf("Presence", "Attention", "Gaze", "Emotion")
        private val ROW_BASELINES = floatArrayOf(75f, 99f, 124f, 151f)
        private val TITLE_STYLE = JarvisDesign.Type.caption
        const val REF_PX_H = 179f
        /** ref5: 1 px = 0,642 dp (docs/JARVIS-REF5-FARBEN.md). */
        const val DP_PER_PX = .642f
        /** Card size in ref5 in dp (124 × 115); [draw] scales from this height. */
        const val REF_W = 124f
        const val REF_H = 115f
        /** Grid share agreed with Kern (of the cockpit area). */
        const val GRID_LEFT = .157f
        const val GRID_TOP = .123f
        const val GRID_WIDTH = .149f
        const val GRID_HEIGHT = .194f
    }
}
