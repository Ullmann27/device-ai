package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.JarvisDesign
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisMotion
import at.heinz.deviceai.ui.JarvisNeon
import at.heinz.deviceai.ui.TextAlign

/**
 * Calm level for ordinary errors and status alarms (Heinz: Jarvis must not overreact). No shake, no zoom, no warp,
 * no glow boost: the head stays visible with its normal idle float, one short message from the event (Kern's
 * CalmNotice.message, e.g. "Heinz, im System ist gerade ein Fehler aufgetreten") with the real source, and pills only
 * when the event carries options. Selections go back through [optionAt] to Kern's PillActionGate.
 */
class Video3CalmNotice(
    private val head: HeadDrawer = HeadDrawer.NONE,
    private val pills: PillPresenter = Video3Pills(),
) {
    private val sourceFill = Fill.Solid(Colors.textSecondary)
    private var bgW = -1f; private var bgH = -1f
    private var backdrop: Fill = Fill.Solid(Colors.background)
    private var cachedSource: String? = null
    private var sourceLine = ""

    fun render(p: ContinuumPainter, width: Float, height: Float, seconds: Float, info: Video3CalmNoticeInfo,
               opts: Video3Options = Video3Options()): Boolean {
        if (width <= 0f || height <= 0f) return false
        if (width != bgW || height != bgH) {
            bgW = width; bgH = height
            backdrop = Fill.Linear(0f, 0f, 0f, height, intArrayOf(Colors.background, Colors.backgroundBottom))
        }
        if (info.sourceLabel != cachedSource) {
            cachedSource = info.sourceLabel
            sourceLine = "Quelle: ${info.sourceLabel.ifBlank { Video3AlarmScene.SOURCE_MISSING }}"
        }
        val ms = if (opts.reducedMotion) SETTLED_MS else (seconds.coerceAtLeast(0f) * 1000f).toLong()
        p.fillRect(0f, 0f, width, height, backdrop)
        val cx = width / 2f
        val headH = height * HEAD_OF_HEIGHT
        val headW = headH * Video3AlarmScene.HEAD_ASPECT
        val headTop = height * HEAD_TOP + JarvisMotion.idleOffset(ms, animatorScale = opts.animatorScale)
        if (opts.glow) JarvisNeon.halo(p, cx, headTop + headH * .45f, headH * .7f, Colors.cyan, HALO_STRENGTH)
        head.draw(p, cx - headW / 2f, headTop, headW, headH, ms / 1000f, 1f)
        val msgY = height * HEAD_TOP + headH + height * .07f
        JarvisNeon.text(p, info.message, cx, msgY, JarvisDesign.Type.title, Colors.cyan, TextAlign.CENTER,
            if (opts.glow) JarvisDesign.Glow.SUBTLE else JarvisDesign.Glow.OFF)
        p.drawText(sourceLine, cx, msgY + LINE_GAP, JarvisDesign.Type.body, sourceFill, TextAlign.CENTER)
        pills.draw(p, width, cx, msgY + LINE_GAP * 2.2f, ms, info.options, opts)
        return true
    }

    /** Enabled option under (x, y) of the last frame, or null. The caller forwards it to PillActionGate.requestOption. */
    fun optionAt(x: Float, y: Float): Video3ActionOption? = pills.optionAt(x, y)

    companion object {
        const val HEAD_OF_HEIGHT = .5f
        const val HEAD_TOP = .08f
        const val LINE_GAP = 24f
        const val HALO_STRENGTH = .18f
        const val SETTLED_MS = 10_000L
    }
}
