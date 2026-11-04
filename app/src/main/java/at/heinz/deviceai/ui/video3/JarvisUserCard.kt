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
        OwnerRecognitionState.RECOGNIZED -> "Nutzer erkannt"
        OwnerRecognitionState.NOT_RECOGNIZED -> "Nicht erkannt"
        OwnerRecognitionState.UNKNOWN -> NONE
    }

    fun subtitle(m: OwnerRecognition = model) = m.method?.takeIf { it.isNotBlank() } ?: NONE

    /** Draws the card into (l, t, r, b) in dp; all sizes follow the card height (ref5 proportions). */
    fun draw(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, glow: Boolean = true) {
        val h = b - t
        val u = h / REF_H
        val radius = JarvisDesign.Styles.tile.radius * u
        if (glow) JarvisNeon.rimGlow(p, l, t, r, b, radius, JarvisDesign.Glow.SUBTLE, 1f, Fill.Solid(Colors.panelRim))
        p.fillRoundRect(l, t, r, b, radius, cardFill)
        p.strokeRoundRect(l, t, r, b, radius, 1f, rimFill)
        val m = model
        // head row: user icon in a corner frame, title (+ check when recognized), method
        val ix = l + 9f * u; val iy = t + 9f * u; val isz = 25f * u
        drawUserIcon(p, ix, iy, isz)
        val tx = ix + isz + 9f * u
        val title = title(m)
        val titleStyle = JarvisDesign.Type.pill
        p.drawText(title, tx, t + 22f * u, titleStyle, titleFill)
        if (m.state == OwnerRecognitionState.RECOGNIZED) {
            val cx = tx + p.measureText(title, titleStyle) + 5f * u; val cy = t + 18f * u
            path.reset().moveTo(cx, cy).lineTo(cx + 3f * u, cy + 3f * u).lineTo(cx + 8f * u, cy - 3.5f * u)
            p.strokePath(path, 1.4f * u, okFill)
        }
        p.drawText(subtitle(m), tx, t + 34f * u, JarvisDesign.Type.caption, subFill)
        p.drawLine(l + 8f * u, t + 45f * u, r - 8f * u, t + 45f * u, 1f, dividerFill, .8f)
        // four rows without a source: always "—"
        val rowX = l + 30f * u
        for (i in ROWS.indices) {
            val y = t + (62f + i * 15.5f) * u
            drawRowIcon(p, i, l + 16f * u, y - 3.5f * u, u)
            p.drawText("${ROWS[i]}:", rowX, y, ROW_STYLE, subFill)
            p.drawText(NONE, rowX + p.measureText("${ROWS[i]}:", ROW_STYLE) + 5f * u, y,
                ROW_STYLE, dimFill)
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
            0 -> p.strokeCircle(cx, cy, r * .8f, 1f, dimFill)   // presence: no source ⇒ hollow, not green
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
        val ROWS = arrayOf("Anwesenheit", "Aufmerksamkeit", "Blick", "Stimmung")
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
