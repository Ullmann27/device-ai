package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.JarvisDesign
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisMotion
import at.heinz.deviceai.ui.JarvisNeon
import at.heinz.deviceai.ui.TextAlign
import kotlin.math.min

/**
 * Default [PillPresenter]: Muster G with JarvisMotion.Presets.pill (from the edges, no fade, strong deceleration) and
 * JarvisNeon glass. Disabled options are dimmed and show their reason below. Hit testing only reports the option.
 */
class Video3Pills : PillPresenter {
    private val rects = FloatArray(MAX_PILLS * 4)
    private val shown = arrayOfNulls<Video3ActionOption>(MAX_PILLS)
    private var count = 0
    private val labelFill = Fill.Solid(Colors.cyanSoft)
    private val dimFill = Fill.Solid(Colors.textMuted)
    private val pillFill = Fill.Solid(STYLE.fill)
    private val rimFill = Fill.Solid(STYLE.rim)

    override fun draw(p: ContinuumPainter, width: Float, centerX: Float, top: Float, elapsedMs: Long,
                      options: List<Video3ActionOption>, opts: Video3Options) {
        count = min(options.size, MAX_PILLS)
        if (count == 0) return
        val style = JarvisDesign.Type.pill
        var total = PILL_GAP * (count - 1)
        for (i in 0 until count) total += p.measureText(options[i].label, style) + PILL_PAD * 2f
        var x = centerX - total / 2f
        for (i in 0 until count) {
            val o = options[i]; shown[i] = o
            val pw = p.measureText(o.label, style) + PILL_PAD * 2f
            val k = i * 4
            rects[k] = x; rects[k + 1] = top; rects[k + 2] = x + pw; rects[k + 3] = top + PILL_H
            val from = if (x + pw / 2f < centerX) JarvisMotion.Direction.LEFT else JarvisMotion.Direction.RIGHT
            val f = JarvisMotion.frame(JarvisMotion.Presets.pill(from),
                elapsedMs - JarvisMotion.staggerStart(i, animatorScale = opts.animatorScale), opts.animatorScale)
            p.save(); p.translate(f.dx * PILL_TRAVEL, f.dy)
            val glow = if (opts.glow && o.enabled) JarvisDesign.Glow.SUBTLE else JarvisDesign.Glow.OFF
            p.fillRoundRect(x, top, x + pw, top + PILL_H, PILL_H / 2f, pillFill, if (o.enabled) 1f else DISABLED_ALPHA)
            if (glow != JarvisDesign.Glow.OFF) JarvisNeon.rimGlow(p, x, top, x + pw, top + PILL_H, PILL_H / 2f, glow)
            p.strokeRoundRect(x, top, x + pw, top + PILL_H, PILL_H / 2f, STYLE.rimWidth, rimFill, if (o.enabled) 1f else DISABLED_ALPHA)
            p.drawText(o.label, x + pw / 2f, top + PILL_H * .64f, style, if (o.enabled) labelFill else dimFill,
                TextAlign.CENTER, if (o.enabled) 1f else DISABLED_ALPHA)
            val reason = o.disabledReason
            if (!o.enabled && !reason.isNullOrBlank())
                p.drawText(reason, x + pw / 2f, top + PILL_H + 15f, JarvisDesign.Type.caption, dimFill, TextAlign.CENTER)
            p.restore()
            x += pw + PILL_GAP
        }
    }

    override fun optionAt(x: Float, y: Float): Video3ActionOption? {
        for (i in 0 until count) {
            val k = i * 4
            if (x >= rects[k] && x <= rects[k + 2] && y >= rects[k + 1] && y <= rects[k + 3])
                return shown[i]?.takeIf { it.enabled }
        }
        return null
    }

    companion object {
        const val MAX_PILLS = 4
        /** ref5 pill style from #479 (JarvisDesign.Styles.pill), scaled up for a touch target. */
        val STYLE = JarvisDesign.Styles.pill
        val PILL_H = STYLE.height * 1.45f
        val PILL_PAD = STYLE.padH * 1.4f
        const val PILL_GAP = 14f
        const val DISABLED_ALPHA = .5f
        /** Edge travel on top of JarvisMotion's pill distance. */
        const val PILL_TRAVEL = 2.5f
    }
}
