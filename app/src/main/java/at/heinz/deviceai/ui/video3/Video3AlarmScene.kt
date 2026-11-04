package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.Argb
import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.FontFamily
import at.heinz.deviceai.ui.JarvisDesign
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisNeon
import at.heinz.deviceai.ui.TextAlign
import at.heinz.deviceai.ui.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Alarm H (Video 3, 10–12 s) for a REAL security finding (AlarmKind.SECURITY_FINDING) only.
 *
 * Adds what JarvisSequenceEffects (owner Jarvis Sicherheit) does not have: UI gone (own deep-space backdrop), hard
 * Z-zoom into the face (ease-in 0,5 s), jagged cyan waves behind the head, light trails from the head and a falling
 * cascade whose READABLE digits come only from the event (measured / threshold). Glow ×3 and the 2 px shake are taken
 * from [AlarmBaseEffects] (= JarvisSequenceEffects on the merged branch), not rebuilt.
 *
 * Pure renderer: [render] with the seconds since the event; no trigger, no action (nothing is fixed or run).
 */
class Video3AlarmScene(
    private val head: HeadDrawer = HeadDrawer.NONE,
    private val base: AlarmBaseEffects = Video3Curves.Fallback(),
    private val pills: PillPresenter = Video3Pills(),
) {
    private val waveSegs = FloatArray(WAVE_LINES * 2 * WAVE_POINTS * 4)
    private val streakSegs = FloatArray(CASCADE_COLUMNS * 4)
    private val cyan = Fill.Solid(Colors.cyan)
    private val cyanDeep = Fill.Solid(Colors.cyanDeep)
    private val white = Fill.Solid(Colors.activeWhite)
    private val digitFill = Fill.Solid(Colors.cyanSoft)
    private val textFill = Fill.Solid(Colors.text)
    private val text2Fill = Fill.Solid(Colors.textSecondary)
    private val mutedFill = Fill.Solid(Colors.textMuted)
    private val cascadeStyle = TextStyle(FontFamily.TEXT, 500, CASCADE_TEXT_SIZE, 1f)
    private val titleStyle = TextStyle(FontFamily.DISPLAY, 600, TITLE_SIZE, 1.6f)
    private val lineStyle = JarvisDesign.Type.body

    private var bgW = -1f; private var bgH = -1f
    private var backdrop: Fill = Fill.Solid(Colors.background)

    private var cachedInfo: Video3AlarmInfo? = null
    private var cascadeTexts: Array<String> = emptyArray()
    private var sourceLine = ""
    private var valueLine = ""

    /** True only for kinds that may start this scene (Kern: SECURITY_FINDING). Status kinds never play it. */
    fun plays(info: Video3AlarmInfo): Boolean = info.kind.playsAlarmScene

    /**
     * Draws the alarm at [seconds] after the event. Does nothing for kinds that do not play the scene.
     * Returns false when nothing was drawn.
     */
    fun render(p: ContinuumPainter, width: Float, height: Float, seconds: Float, info: Video3AlarmInfo,
               options: Video3Options = Video3Options()): Boolean {
        if (!plays(info) || width <= 0f || height <= 0f) return false
        prepare(width, height, info)
        val reduced = options.reducedMotion
        val t = if (reduced) DURATION_S else seconds.coerceAtLeast(0f)
        val ms = (t * 1000f).toLong()
        val glow = if (options.glow) base.glowBoost(ms) else 0f

        p.fillRect(0f, 0f, width, height, backdrop)
        p.save()
        if (!reduced) p.translate(base.shakeOffsetX(ms), base.shakeOffsetY(ms))

        val cx = width / 2f
        val headH = height * HEAD_OF_HEIGHT
        val headW = headH * HEAD_ASPECT
        val faceY = height * FACE_Y
        val zoom = if (reduced) 1f else 1f + (ZOOM_MAX - 1f) * Video3Curves.easeIn(t / Video3Curves.ALARM_ZOOM_S)

        drawCascade(p, width, height, t, reduced)
        p.save()
        p.scale(zoom, zoom, cx, faceY)
        drawWaves(p, width, cx, faceY, headW, headH, t, reduced, glow)
        drawTrails(p, cx, faceY, headH, t, glow)
        if (glow > 0f) JarvisNeon.bloom(p, cx, faceY, headH * .42f, Colors.cyan, BLOOM_PER_GLOW * glow)
        head.draw(p, cx - headW / 2f, faceY - headH * FACE_IN_HEAD, headW, headH, t, glow.coerceAtLeast(1f))
        p.restore()
        p.restore()
        drawPanel(p, width, height, glow, info, ms, options)
        return true
    }

    private fun prepare(w: Float, h: Float, info: Video3AlarmInfo) {
        if (w != bgW || h != bgH) {
            bgW = w; bgH = h
            backdrop = Fill.Linear(0f, 0f, 0f, h, intArrayOf(Colors.background, Colors.backgroundBottom))
        }
        if (info != cachedInfo) {
            cachedInfo = info
            val m = info.measured?.takeIf { it.isFinite() }?.let(::fmt)
            val th = info.threshold?.takeIf { it.isFinite() }?.let(::fmt)
            cascadeTexts = listOfNotNull(m, th).toTypedArray()
            sourceLine = "Quelle: ${info.sourceLabel.ifBlank { SOURCE_MISSING }}"
            valueLine = when {
                m != null && th != null -> "Messwert $m  ·  Schwelle $th"
                m != null -> "Messwert $m  ·  Schwelle $UNAVAILABLE"
                th != null -> "Messwert $UNAVAILABLE  ·  Schwelle $th"
                else -> "Messwert $UNAVAILABLE"
            }
        }
    }

    private fun fmt(v: Double) = String.format(Locale.GERMANY, "%.2f", v)

    /** Falling cascade: readable text only from the event; without values only light streaks (no glyphs). */
    private fun drawCascade(p: ContinuumPainter, w: Float, h: Float, t: Float, reduced: Boolean) {
        val colW = w / CASCADE_COLUMNS
        var n = 0
        for (c in 0 until CASCADE_COLUMNS) {
            val speed = CASCADE_SPEED_MIN + Video3Curves.unit(c * 31 + 7) * CASCADE_SPEED_RANGE
            val offset = Video3Curves.unit(c * 17 + 3) * (h + CASCADE_TAIL)
            val y = ((if (reduced) 0f else t * speed) + offset) % (h + CASCADE_TAIL)
            val x = colW * (c + .5f)
            val near = Video3Curves.unit(c * 13 + 1) > .55f
            if (cascadeTexts.isNotEmpty() && near) {
                val s = cascadeTexts[c % cascadeTexts.size]
                p.drawText(s, x, y, cascadeStyle, digitFill, TextAlign.CENTER, CASCADE_TEXT_ALPHA)
            } else {
                streakSegs[n++] = x; streakSegs[n++] = y - CASCADE_TAIL; streakSegs[n++] = x; streakSegs[n++] = y
            }
        }
        if (n > 0) p.strokeSegments(streakSegs, n, 1f, cyanDeep, CASCADE_STREAK_ALPHA)
    }

    /** Jagged cyan waves left and right of the head (Video 3, 10–11 s); jag pattern changes every [JAG_STEP_MS]. */
    private fun drawWaves(p: ContinuumPainter, w: Float, cx: Float, cy: Float, headW: Float, headH: Float, t: Float,
                          reduced: Boolean, glow: Float) {
        val bucket = if (reduced) 0 else (t * 1000f / JAG_STEP_MS).toInt()
        var n = 0
        for (side in 0..1) {
            val dir = if (side == 0) -1f else 1f
            val x0 = cx + dir * headW * .42f
            val x1 = if (side == 0) 0f else w
            for (l in 0 until WAVE_LINES) {
                val bandY = cy + (l - (WAVE_LINES - 1) / 2f) * headH * .06f
                val amp = headH * (.03f + .05f * Video3Curves.unit(l * 7 + side))
                var px = x0; var py = bandY
                for (k in 1..WAVE_POINTS) {
                    val f = k / WAVE_POINTS.toFloat()
                    val nx = x0 + (x1 - x0) * f
                    val jag = Video3Curves.unit(bucket * 131 + side * 977 + l * 53 + k) * 2f - 1f
                    val ny = bandY + jag * amp * (1f - .5f * f) + sin(f * 9f + t * 6f + l) * amp * .35f
                    waveSegs[n++] = px; waveSegs[n++] = py; waveSegs[n++] = nx; waveSegs[n++] = ny
                    px = nx; py = ny
                }
            }
        }
        if (glow > 0f) {
            p.setAdditive(true)
            p.strokeSegments(waveSegs, n, WAVE_GLOW_WIDTH, cyan, min(1f, WAVE_GLOW_ALPHA * glow))
            p.setAdditive(false)
        }
        p.strokeSegments(waveSegs, n, 1.2f, cyan, .9f)
    }

    /** Light trails shooting out of the head ("Blitz-Lichtbahnen"). */
    private fun drawTrails(p: ContinuumPainter, cx: Float, cy: Float, headH: Float, t: Float, glow: Float) {
        p.setAdditive(true)
        for (i in 0 until TRAILS) {
            val a = 2f * PI.toFloat() * (i + Video3Curves.unit(i)) / TRAILS
            val run = ((t * TRAIL_SPEED + Video3Curves.unit(i * 5 + 2)) % 1f)
            val r0 = headH * (.35f + run * 1.4f)
            val r1 = r0 + headH * (.18f + .2f * Video3Curves.unit(i * 3))
            val x0 = cx + cos(a) * r0; val y0 = cy + sin(a) * r0 * .7f
            val x1 = cx + cos(a) * r1; val y1 = cy + sin(a) * r1 * .7f
            val alpha = (1f - run) * .7f
            if (glow > 0f) p.drawLine(x0, y0, x1, y1, 5f, cyan, alpha * .25f * min(glow, 3f) / 3f)
            p.drawLine(x0, y0, x1, y1, 1.2f, white, alpha)
        }
        p.setAdditive(false)
    }

    /** Enabled option under (x, y) of the last frame (reporting only, the caller forwards it to Kern). */
    fun optionAt(x: Float, y: Float): Video3ActionOption? = pills.optionAt(x, y)

    private fun drawPanel(p: ContinuumPainter, w: Float, h: Float, glow: Float, info: Video3AlarmInfo, ms: Long,
                          options: Video3Options) {
        val pw = min(w * .62f, PANEL_MAX_W)
        val l = (w - pw) / 2f; val r = l + pw
        val b = h - h * .06f - (if (info.options.isEmpty()) 0f else PILL_ROW); val t = b - PANEL_H
        JarvisNeon.glassCard(p, l, t, r, b, glow = if (glow > 0f) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.OFF,
            active = glow > 0f, glowBoost = if (glow > 0f) 1f else 0f)
        val c = w / 2f
        JarvisNeon.text(p, TITLE, c, t + PANEL_H * .34f, titleStyle, Argb.mix(Colors.cyan, Colors.activeWhite, .4f),
            TextAlign.CENTER, if (glow > 0f) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.OFF, active = glow > 0f)
        p.drawText(sourceLine, c, t + PANEL_H * .60f, lineStyle, textFill, TextAlign.CENTER)
        p.drawText(valueLine, c, t + PANEL_H * .80f, lineStyle, text2Fill, TextAlign.CENTER)
        p.drawText(NO_ACTION, c, t + PANEL_H * .95f, JarvisDesign.Type.caption, mutedFill, TextAlign.CENTER)
        val pillMs = ms - (Video3Curves.ALARM_ZOOM_S * 1000f).toLong()
        pills.draw(p, w, w / 2f, b + 14f, if (options.reducedMotion) Long.MAX_VALUE / 4 else pillMs, info.options, options)
    }

    companion object {
        const val PILL_ROW = 64f
        const val DURATION_S = 2f
        const val TITLE = "Sicherheitsbefund erkannt"
        const val NO_ACTION = "Keine automatische Maßnahme – Entscheidung beim Besitzer"
        const val UNAVAILABLE = "nicht verfügbar"
        const val SOURCE_MISSING = "Quelle fehlt"

        const val ZOOM_MAX = 1.9f
        const val HEAD_OF_HEIGHT = .62f
        const val HEAD_ASPECT = 300f / 345f   // ContinuumAvatarRenderer reference box
        const val FACE_Y = .42f
        const val FACE_IN_HEAD = .42f
        const val BLOOM_PER_GLOW = .45f

        const val WAVE_LINES = 5
        const val WAVE_POINTS = 18
        const val WAVE_GLOW_WIDTH = 6f
        const val WAVE_GLOW_ALPHA = .12f
        const val JAG_STEP_MS = 70f

        const val TRAILS = 18
        const val TRAIL_SPEED = 1.6f

        const val CASCADE_COLUMNS = 26
        const val CASCADE_SPEED_MIN = 160f
        const val CASCADE_SPEED_RANGE = 220f
        const val CASCADE_TAIL = 90f
        const val CASCADE_TEXT_SIZE = 15f
        const val CASCADE_TEXT_ALPHA = .75f
        const val CASCADE_STREAK_ALPHA = .45f

        const val TITLE_SIZE = 22f
        const val PANEL_H = 118f
        const val PANEL_MAX_W = 560f
    }
}
